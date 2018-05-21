package es.eltrueno.npc.nms;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import es.eltrueno.npc.TruenoNPC;
import es.eltrueno.npc.event.TruenoNPCSpawnEvent;
import es.eltrueno.npc.skin.SkinData;
import es.eltrueno.npc.skin.SkinDataReply;
import es.eltrueno.npc.skin.TruenoNPCSkin;
import es.eltrueno.npc.wrapper.WrapperPlayServerPlayerInfo;
import es.eltrueno.npc.wrapper.WrapperPlayServerScoreboardTeam;
import net.minecraft.server.v1_12_R1.MathHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class TruenoNPC_v1_12_r1 implements TruenoNPC {
    private static List<TruenoNPC_v1_12_r1> npcs;
    private static int id;
    private static boolean taskstarted;
    private static Plugin plugin;

    static {
        TruenoNPC_v1_12_r1.npcs = new ArrayList<TruenoNPC_v1_12_r1>();
        TruenoNPC_v1_12_r1.id = 0;
        TruenoNPC_v1_12_r1.taskstarted = false;
    }

    private PacketContainer scbpacket;
    private boolean deleted;
    private int npcid;
    private int entityID;
    private Location location;
    private GameProfile gameprofile;
    private TruenoNPCSkin skin;
    private List<Player> rendered;
    private JsonObject jsonElement;

    public TruenoNPC_v1_12_r1(final Location location, final TruenoNPCSkin skin) {
        this.deleted = false;
        this.rendered = new ArrayList<Player>();
        this.entityID = (int) Math.ceil(Math.random() * 1000.0) + 2000;
        this.npcid = TruenoNPC_v1_12_r1.id++;
        this.skin = skin;
        this.location = location;
        if (!TruenoNPC_v1_12_r1.npcs.contains(this)) {
            TruenoNPC_v1_12_r1.npcs.add(this);
        }
    }

    public static void startTask(Plugin plugin) {
        if (!taskstarted) {
            taskstarted = true;
            TruenoNPC_v1_12_r1.plugin = plugin;
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
                @Override
                public void run() {
                    for (TruenoNPC_v1_12_r1 nmsnpc : npcs) {
                        for (Player pl : Bukkit.getOnlinePlayers()) {
                            if (nmsnpc.location.getWorld().equals(pl.getWorld())) {
                                if (nmsnpc.location.distance(pl.getLocation()) > 60 && nmsnpc.rendered.contains(pl)) {
                                    nmsnpc.destroyV2(pl);
                                } else if (nmsnpc.location.distance(pl.getLocation()) < 60 && !nmsnpc.rendered.contains(pl)) {
                                    nmsnpc.spawn(pl);
                                }
                            } else {
                                nmsnpc.destroyV2(pl);
                            }
                        }
                    }
                }
            }, 0, 30);
        }
    }

    public static void removeCacheFile() {
        File file = new File(plugin.getDataFolder().getPath() + "/truenonpcdata.json");
        if (file.exists()) {
            file.delete();
        }
    }

    @Override
    public Location getLocation() {
        return this.location;
    }

    @Override
    public int getEntityID() {
        return this.entityID;
    }

    @Override
    public boolean isDeleted() {
        return this.deleted;
    }

    @Override
    public int getNpcID() {
        return this.npcid;
    }

    private JsonObject getChacheFile(Plugin plugin) {
        File file = new File(plugin.getDataFolder().getPath() + "/truenonpcdata.json");
        if (file.exists()) {
            try {
                JsonParser parser = new JsonParser();
                JsonElement jsonElement = parser.parse(new FileReader(file));
                this.jsonElement = jsonElement.getAsJsonObject();
                return jsonElement.getAsJsonObject();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return jsonElement;
            }
        } else return jsonElement;
    }

    private SkinData getCachedSkin() {
        JsonObject jsonFile = getChacheFile(plugin);
        JsonArray oldskindata = jsonFile.getAsJsonArray("skindata");
        Iterator it = oldskindata.iterator();
        SkinData skin = null;
        while (it.hasNext()) {
            JsonElement element = (JsonElement) it.next();
            if (element.getAsJsonObject().get("id").getAsInt() == this.npcid) {
                String value = element.getAsJsonObject().get("value").getAsString();
                String signature = element.getAsJsonObject().get("signature").getAsString();
                skin = new SkinData(value, signature);
            }
        }
        return skin;
    }

    private void cacheSkin(SkinData skindata) {
        JsonObject jsonFile = getChacheFile(plugin);
        JsonArray newskindata = new JsonArray();
        if (jsonFile != null) {
            JsonArray oldskindata = jsonFile.getAsJsonArray("skindata");
            Iterator it = oldskindata.iterator();
            while (it.hasNext()) {
                JsonElement element = (JsonElement) it.next();
                if (element.getAsJsonObject().get("id").getAsInt() == this.npcid) {
                    element.getAsJsonObject().remove("value");
                    element.getAsJsonObject().remove("signature");
                    element.getAsJsonObject().addProperty("value", skindata.getValue());
                    element.getAsJsonObject().addProperty("signature", skindata.getSignature());
                } else {
                    newskindata.add(element);
                }
            }
        }
        JsonObject skin = new JsonObject();
        skin.addProperty("id", this.npcid);
        skin.addProperty("value", skindata.getValue());
        skin.addProperty("signature", skindata.getSignature());
        newskindata.add(skin);

        JsonObject obj = new JsonObject();
        obj.add("skindata", newskindata);
        try {
            plugin.getDataFolder().mkdir();
            File file = new File(plugin.getDataFolder().getPath() + "/truenonpcdata.json");
            file.createNewFile();
            FileWriter writer = new FileWriter(file);
            writer.write(obj.toString());
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private GameProfile getGameProfile(String profilename, SkinData skindata) {
        if (skindata != null) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), profilename);
            profile.getProperties().put("textures", new Property("textures", skindata.getValue(), skindata.getSignature()));
            return profile;
        } else {
            GameProfile profile = new GameProfile(UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7"), profilename);
            profile.getProperties().put("textures", new Property("textures", "eyJ0aW1lc3RhbXAiOjE1MTUzMzczNTExMjk" +
                    "sInByb2ZpbGVJZCI6Ijg2NjdiYTcxYjg1YTQwMDRhZjU0NDU3YTk3MzRlZWQ3IiwicHJvZmlsZU5hbWUiOiJTdGV2ZSIsInNpZ2" +
                    "5hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQub" +
                    "mV0L3RleHR1cmUvNDU2ZWVjMWMyMTY5YzhjNjBhN2FlNDM2YWJjZDJkYzU0MTdkNTZmOGFkZWY4NGYxMTM0M2RjMTE4OGZlMTM4" +
                    "In0sIkNBUEUiOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iNzY3ZDQ4MzI1ZWE1MzI0NTY" +
                    "xNDA2YjhjODJhYmJkNGUyNzU1ZjExMTUzY2Q4NWFiMDU0NWNjMiJ9fX0", "oQHxJ9U7oi/JOeC5C9wtLcoqQ/Uj5j8mfSL" +
                    "aPo/zMQ1GP/IjB+pFmfy5JOOaX94Ia98QmLLd+AYacnja60DhO9ljrTtL/tM7TbXdWMWW7A2hJkEKNH/wnBkSIm0EH8WhH+m9+8" +
                    "2pkTB3h+iDGHyc+Qb9tFXWLiE8wvdSrgDHPHuQAOgGw6BfuhdSZmv2PGWXUG02Uvk6iQ7ncOIMRWFlWCsprpOw32yzWLSD8UeUU" +
                    "io6SlUyuBIO+nJKmTRWHnHJgTLqgmEqBRg0B3GdML0BncMlMHq/qe9x6gTlDCJATLTFJg4kDEF+kUa4+P0BDdPFrgApFUeK4Bz1" +
                    "w7Qxls4zKQQJNJw58nhvKk/2yQnFOOUqfRx/DeIDLCGSTEJr4VjKIVThnvkocUDsH8DLk4/Xt9qKWh3ZxXtxoKPDvFP5iyxIOfZ" +
                    "dkZu/H0qlgRTqF8RP8AnXf2lgnarfty8G7q7/4KQwWC1CIn9MmaMwv3MdFDlwdAjHhvpyBYYTnL11YDBSUg3b6+QmrWWm1DXcHr" +
                    "wkcS0HI82VHYdg8uixzN57B3DGRSlh2qBWHJTb0zF8uryveCZppHl/ULa/2vAt6XRXURniWU4cTQKQAGqjByhWSbUM0XHFgcuKj" +
                    "GFVlJ4HEzBiXgY3PtRF6NzfsUZ2gQI9o12x332USZiluYrf+OLhCa8="));
            return profile;
        }
    }

    @Override
    public TruenoNPCSkin getSkin() {
        return this.skin;
    }

    @Override
    public void delete() {
        TruenoNPC_v1_12_r1.npcs.remove(this);
        for (final Player p : Bukkit.getOnlinePlayers()) {
            this.destroyV2(p);
        }
        this.deleted = true;
    }

    private void setGameProfile(final GameProfile profile) {
        this.gameprofile = profile;
    }

    private void spawn(final Player p) {
        this.skin.getSkinDataAsync(new SkinDataReply() {
            @Override
            public void done(final SkinData skinData) {
                final GameProfile profile = TruenoNPC_v1_12_r1.this.getGameProfile(randomString(4), skinData);
                TruenoNPC_v1_12_r1.this.setGameProfile(profile);
                TruenoNPC_v1_12_r1.this.cacheSkin(skinData);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            TruenoNPC_v1_12_r1.this.spawnEntityV2(p, profile, skinData);
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }.runTaskLater(plugin, 20);
            }
        });
    }

    private void spawnEntityV2(final Player p, final GameProfile gameprofile, final SkinData skindata) throws InvocationTargetException {
        GameProfile profile = gameprofile;
        if (skindata == null && this.gameprofile != null) {
            profile = this.gameprofile;
        } else if (this.getCachedSkin() != null) {
            profile = this.getGameProfile(randomString(4), this.getCachedSkin());
            this.setGameProfile(profile);
        }
        final PacketContainer packet = new PacketContainer(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        StructureModifier<Object> spawnPacketModifier = packet.getModifier();

        spawnPacketModifier.write(0, this.entityID);
        spawnPacketModifier.write(1, profile.getId());
        spawnPacketModifier.write(2, MathHelper.floor(this.location.getX() * 32.0));
        spawnPacketModifier.write(3, MathHelper.floor(this.location.getY() * 32.0));
        spawnPacketModifier.write(4, MathHelper.floor(this.location.getZ() * 32.0));
        spawnPacketModifier.write(5, (byte) (this.location.getYaw() * 256.0f / 360.0f));
        spawnPacketModifier.write(6, (byte) (this.location.getPitch() * 256.0f / 360.0f));

        WrappedDataWatcher w = new WrappedDataWatcher();
        w.setObject(10, (Object) (byte) 127);
        packet.getDataWatcherModifier().write(0, w);
        try {
            this.scbpacket = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);
            WrapperPlayServerScoreboardTeam scoreMods = new WrapperPlayServerScoreboardTeam(this.scbpacket);
            scoreMods.setMode(WrapperPlayServerScoreboardTeam.Mode.TEAM_CREATED);
            scoreMods.setName(randomString(8));
            scoreMods.setDisplayName(randomString(8));
            scoreMods.setPrefix(profile.getName());
            scoreMods.setSuffix(profile.getName());
            scoreMods.setNameTagVisibility("never");
            List<String> list = new ArrayList<>();
            list.add(profile.getName());
            scoreMods.setPlayers(list);
            ProtocolLibrary.getProtocolManager().sendServerPacket(p, this.scbpacket, false);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        addToTablistV2(p);
        ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
        final PacketContainer rotationpacket = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        StructureModifier<Object> rotationModifier = rotationpacket.getModifier();
        rotationModifier.write(0, this.entityID);
        rotationModifier.write(1, (byte) (this.location.getYaw() * 256.0f / 360.0f));
        ProtocolLibrary.getProtocolManager().sendServerPacket(p, rotationpacket, false);
        Bukkit.getScheduler().runTaskLater(TruenoNPC_v1_12_r1.plugin, (Runnable) new Runnable() {
            @Override
            public void run() {
                TruenoNPC_v1_12_r1.this.rmvFromTablistV2(p);
            }
        }, 25L);
        this.rendered.add(p);
        final TruenoNPCSpawnEvent event = new TruenoNPCSpawnEvent(p, this);
        Bukkit.getPluginManager().callEvent((Event) event);
    }

    private void destroyV2(final Player p) {
        final PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        StructureModifier<Object> spawnPacketModifier = packet.getModifier();
        spawnPacketModifier.write(0, this.entityID);
        this.rmvFromTablistV2(p);
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private final void addToTablistV2(Player p) {
        GameProfile profile2 = gameprofile;

        final PacketContainer packet = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
        WrapperPlayServerPlayerInfo packet_editor = new WrapperPlayServerPlayerInfo(packet);

        WrappedGameProfile profile = WrappedGameProfile.fromHandle(profile2);

        PlayerInfoData newData = new PlayerInfoData(profile, 1, EnumWrappers.NativeGameMode.NOT_SET, WrappedChatComponent.fromText("§dNPC§e " + gameprofile.getName()));

        List<PlayerInfoData> players = (List<PlayerInfoData>) packet.getPlayerInfoDataLists().read(0);
        players.add(newData);

        packet_editor.setAction(EnumWrappers.PlayerInfoAction.ADD_PLAYER);
        packet_editor.setData(players);

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public String randomString(int length) {
        String[] randomChars = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "N", "M", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
        int[] randomInteger = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0};

        String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        SecureRandom rnd = new SecureRandom();

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            sb.append(AB.charAt(rnd.nextInt(AB.length())));
        return sb.toString();
    }


    private final void rmvFromTablistV2(Player p) {
        GameProfile profile2 = gameprofile;
        final PacketContainer packet = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
        WrapperPlayServerPlayerInfo packet_editor = new WrapperPlayServerPlayerInfo(packet);

        WrappedGameProfile profile = WrappedGameProfile.fromHandle(profile2);
        PlayerInfoData newData = new PlayerInfoData(profile, 1, EnumWrappers.NativeGameMode.NOT_SET, WrappedChatComponent.fromText("§dNPC§e " + gameprofile.getName()));

        List<PlayerInfoData> players = (List<PlayerInfoData>) packet.getPlayerInfoDataLists().read(0);
        players.add(newData);

        packet_editor.setAction(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
        packet_editor.setData(players);

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet, false);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
