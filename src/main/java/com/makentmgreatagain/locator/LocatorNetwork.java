package com.makentmgreatagain.locator;

import com.hbm.extprop.HbmPlayerProps;
import com.hbm.handler.ArmorModHandler;
import com.makentmgreatagain.init.ModItems;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Pattern;

/** Common-side transport; neither packet handler accesses a world or an inventory. */
public final class LocatorNetwork {
    public static final int CHUNK_RADIUS = 1;
    /** 一次扫描最多 3 个样本 × 每样本 8 条 = 24 条结果。 */
    public static final int MAX_SAMPLES = 3;
    public static final int MAX_RESULTS = 24;
    public static final int OK = 0, NO_MATCH = 1, COOLDOWN = 2, FORBIDDEN = 3,
            INVALID_TARGET = 4, BUSY = 5, CANCELLED = 6;

    private static final int MAX_KEY = 64;
    private static final int WORLD_LIMIT = 30000000;
    private static final Pattern ORE_KEY = Pattern.compile("ore[A-Z][A-Za-z0-9_]*");
    private static final ArrayBlockingQueue<Result> INBOX = new ArrayBlockingQueue<>(16);
    private static SimpleNetworkWrapper channel;

    private LocatorNetwork() { }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("MNTGA|locator");
        channel.registerMessage(ServerHandler.class, Query.class, 0, Side.SERVER);
        channel.registerMessage(ClientHandler.class, Reply.class, 1, Side.CLIENT);
        FMLCommonHandler.instance().bus().register(LocatorServer.INSTANCE);
        FMLCommonHandler.instance().bus().register(new ConnectionEvents());
    }

    public static void serverStopped() {
        LocatorServer.INSTANCE.clear();
    }

    /** 一个扫描目标：精确方块（oreKey 空、meta -1 表示任意）或矿族。 */
    public static final class TargetSpec {
        public final int blockId, meta;
        public final String oreKey;

        public TargetSpec(int blockId, int meta, String oreKey) {
            this.blockId = blockId;
            this.meta = meta;
            this.oreKey = oreKey == null ? "" : oreKey;
        }
    }

    /** Called on the client thread. 1~3 targets scanned together in one request (one cooldown). */
    public static void request(int sequence, List<TargetSpec> targets) {
        if (targets == null || targets.isEmpty()) return;
        List<TargetSpec> bounded = targets.size() > MAX_SAMPLES ? targets.subList(0, MAX_SAMPLES) : targets;
        List<TargetSpec> clean = new ArrayList<>(bounded.size());
        for (TargetSpec t : bounded) {
            String key = t.oreKey;
            // A bounded, invalid key lets the server return INVALID_TARGET rather than silently dropping the request.
            if (!key.isEmpty() && !validOreKey(key)) key = "?";
            clean.add(new TargetSpec(t.blockId, t.meta, key));
        }
        channel.sendToServer(new Query(sequence, clean));
    }

    /** Nonblocking client inbox; callers must match both the sequence and the current dimension. */
    public static Result poll() {
        return INBOX.poll();
    }

    /** Shared, read-only check. HBM's pryMod removes invalid slots, so do not call it here. */
    public static boolean canUse(EntityPlayer player) {
        if (player == null || !player.isEntityAlive() || ModItems.neutrino_goggles == null) return false;
        HbmPlayerProps props = (HbmPlayerProps) player.getExtendedProperties(HbmPlayerProps.key);
        if (props == null || !props.enableHUD) return false;
        ItemStack helmet = player.getCurrentArmor(3);
        if (helmet == null || !helmet.hasTagCompound()) return false;
        NBTTagCompound mod = helmet.getTagCompound().getCompoundTag(ArmorModHandler.MOD_COMPOUND_KEY)
                .getCompoundTag(ArmorModHandler.MOD_SLOT_KEY + ArmorModHandler.helmet_only);
        return mod.hasKey("id", 2) && mod.getByte("Count") > 0
                && (mod.getShort("id") & 0xFFFF) == Item.getIdFromItem(ModItems.neutrino_goggles);
    }

    static boolean validOreKey(String key) {
        return key.length() <= MAX_KEY && ORE_KEY.matcher(key).matches();
    }

    static boolean validXZ(int coordinate) {
        return coordinate >= -WORLD_LIMIT && coordinate < WORLD_LIMIT;
    }

    static boolean validBlockId(int blockId) {
        return blockId > 0 && blockId < 4096;
    }

    static void send(EntityPlayerMP player, int sequence, int dimension, int status, List<Position> positions) {
        channel.sendTo(new Reply(new Result(sequence, dimension, status, positions)), player);
    }

    public static final class Result {
        public final int sequence, dimension, status;
        public final List<Position> positions;

        private Result(int sequence, int dimension, int status, List<Position> positions) {
            this.sequence = sequence;
            this.dimension = dimension;
            this.status = status;
            this.positions = Collections.unmodifiableList(new ArrayList<>(positions));
        }
    }

    public static final class Position {
        public final int x, y, z, blockId, meta, targetIndex;

        Position(int x, int y, int z, int blockId, int meta, int targetIndex) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            this.meta = meta;
            this.targetIndex = targetIndex;
        }
    }

    public static final class Query implements IMessage {
        int sequence;
        List<TargetSpec> targets = new ArrayList<>();
        boolean framed;

        public Query() { }

        private Query(int sequence, List<TargetSpec> targets) {
            this.sequence = sequence;
            this.targets = targets;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            framed = false;
            targets = new ArrayList<>();
            if (buf.readableBytes() < 5) return;
            sequence = buf.readInt();
            int count = buf.readUnsignedByte();
            if (count < 1 || count > MAX_SAMPLES) return;
            for (int i = 0; i < count; i++) {
                if (buf.readableBytes() < 9) return;
                int blockId = buf.readInt(), meta = buf.readInt();
                int n = buf.readUnsignedByte();
                if (n > MAX_KEY || buf.readableBytes() < n) return;
                char[] key = new char[n];
                for (int k = 0; k < n; k++) key[k] = (char) buf.readUnsignedByte();
                targets.add(new TargetSpec(blockId, meta, new String(key)));
            }
            framed = true;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(sequence).writeByte(targets.size());
            for (TargetSpec t : targets) {
                buf.writeInt(t.blockId).writeInt(t.meta).writeByte(t.oreKey.length());
                for (int i = 0; i < t.oreKey.length(); i++) buf.writeByte(t.oreKey.charAt(i));
            }
        }
    }

    public static final class Reply implements IMessage {
        private Result result;

        public Reply() { }
        private Reply(Result result) { this.result = result; }

        @Override
        public void fromBytes(ByteBuf buf) {
            result = null;
            if (buf.readableBytes() < 10) return;
            int sequence = buf.readInt(), dimension = buf.readInt();
            int status = buf.readUnsignedByte(), count = buf.readUnsignedByte();
            if (status > CANCELLED || count > MAX_RESULTS || (status == OK) != (count > 0)
                    || buf.readableBytes() != count * 13) return;
            List<Position> positions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int x = buf.readInt(), z = buf.readInt(), y = buf.readUnsignedByte();
                int blockId = buf.readUnsignedShort(), meta = buf.readUnsignedByte();
                int targetIndex = buf.readUnsignedByte();
                if (!validXZ(x) || !validXZ(z) || !validBlockId(blockId) || meta > 15
                        || targetIndex >= MAX_SAMPLES) return;
                positions.add(new Position(x, y, z, blockId, meta, targetIndex));
            }
            result = new Result(sequence, dimension, status, positions);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(result.sequence).writeInt(result.dimension)
                    .writeByte(result.status).writeByte(result.positions.size());
            for (Position p : result.positions) {
                buf.writeInt(p.x).writeInt(p.z).writeByte(p.y).writeShort(p.blockId).writeByte(p.meta)
                        .writeByte(p.targetIndex);
            }
        }
    }

    public static final class ServerHandler implements IMessageHandler<Query, IMessage> {
        @Override
        public IMessage onMessage(Query msg, MessageContext ctx) {
            if (ctx.side == Side.SERVER && msg.framed) {
                LocatorServer.INSTANCE.enqueue(ctx.getServerHandler(), msg);
            }
            return null;
        }
    }

    public static final class ClientHandler implements IMessageHandler<Reply, IMessage> {
        @Override
        public IMessage onMessage(Reply msg, MessageContext ctx) {
            if (ctx.side == Side.CLIENT && msg.result != null) INBOX.offer(msg.result);
            return null;
        }
    }

    public static final class ConnectionEvents {
        @SubscribeEvent
        public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) { INBOX.clear(); }

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { INBOX.clear(); }
    }
}
