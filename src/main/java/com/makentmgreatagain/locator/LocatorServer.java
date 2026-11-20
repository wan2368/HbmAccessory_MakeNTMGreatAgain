package com.makentmgreatagain.locator;

import com.makentmgreatagain.locator.LocatorNetwork.Position;
import com.makentmgreatagain.locator.LocatorNetwork.Query;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread work shared across all players and dimensions. */
public final class LocatorServer {
    static final LocatorServer INSTANCE = new LocatorServer();
    private static final int PROBES_PER_TICK = 8192;
    private static final int QUANTUM = 1024;
    private static final int MAX_ACTIVE = 16;
    private static final int MAX_PENDING = 128;
    private static final int ADMISSIONS_PER_TICK = 8;
    /** 每次搜索冷却 5 秒（用户要求）。 */
    private static final int COOLDOWN_TICKS = 100;
    private static final int MAX_ORE_ENTRIES = 4096;

    // The only state shared with Netty. One pending packet per connection; bounded global admission.
    private final Map<NetHandlerPlayServer, Query> pending = new LinkedHashMap<>();
    private final ArrayDeque<Scan> scans = new ArrayDeque<>();
    private final Map<UUID, Scan> active = new HashMap<>();
    // Insertion order is expiration order. Retain cooldown across dimension transfers and quick relogs.
    private final Map<UUID, Long> cooldowns = new LinkedHashMap<>();
    private long tick;

    private LocatorServer() { }

    void enqueue(NetHandlerPlayServer connection, Query query) {
        if (connection == null) return;
        synchronized (pending) {
            if (pending.size() < MAX_PENDING && !pending.containsKey(connection)) pending.put(connection, query);
        }
    }

    private Map.Entry<NetHandlerPlayServer, Query> takePending() {
        synchronized (pending) {
            Iterator<Map.Entry<NetHandlerPlayServer, Query>> it = pending.entrySet().iterator();
            if (!it.hasNext()) return null;
            Map.Entry<NetHandlerPlayServer, Query> entry = it.next();
            it.remove();
            return entry;
        }
    }

    void clear() {
        synchronized (pending) { pending.clear(); }
        scans.clear();
        active.clear();
        cooldowns.clear();
        tick = 0;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        Iterator<Long> expires = cooldowns.values().iterator();
        while (expires.hasNext() && expires.next() <= tick) expires.remove();
        for (int i = 0; i < ADMISSIONS_PER_TICK; i++) {
            Map.Entry<NetHandlerPlayServer, Query> entry = takePending();
            if (entry == null) break;
            admit(entry.getKey(), entry.getValue());
        }

        int budget = PROBES_PER_TICK;
        while (budget > LocatorNetwork.MAX_RESULTS && !scans.isEmpty()) {
            Scan scan = scans.removeFirst();
            EntityPlayerMP player = scan.player;
            if (!isCurrent(scan.connection, player)) {
                active.remove(scan.owner);
                continue;
            }
            if (player.worldObj != scan.world || player.dimension != scan.dimension || !scan.nearOrigin()) {
                finish(scan, LocatorNetwork.CANCELLED);
                continue;
            }
            if (!LocatorNetwork.canUse(player)) {
                finish(scan, LocatorNetwork.FORBIDDEN);
                continue;
            }
            budget -= scan.step(Math.min(budget, QUANTUM));
            if (scan.done()) {
                finish(scan, scan.found == 0 ? LocatorNetwork.NO_MATCH : LocatorNetwork.OK);
            } else {
                scans.addLast(scan);
            }
        }
    }

    private void admit(NetHandlerPlayServer connection, Query query) {
        EntityPlayerMP player = connection.playerEntity;
        if (!isCurrent(connection, player)) return;
        UUID owner = player.getUniqueID();
        int dimension = player.dimension;
        int status;
        if (cooldowns.containsKey(owner)) {
            status = LocatorNetwork.COOLDOWN;
        } else {
            cooldowns.put(owner, tick + COOLDOWN_TICKS);
            if (!LocatorNetwork.canUse(player)) {
                status = LocatorNetwork.FORBIDDEN;
            } else if (active.containsKey(owner) || scans.size() >= MAX_ACTIVE) {
                status = LocatorNetwork.BUSY;
            } else {
                WorldServer world = player.getServerForPlayer();
                int x = MathHelper.floor_double(player.posX), y = MathHelper.floor_double(player.posY);
                int z = MathHelper.floor_double(player.posZ);
                if (world == null || !LocatorNetwork.validXZ(x) || !LocatorNetwork.validXZ(z)) {
                    status = LocatorNetwork.CANCELLED;
                } else {
                    List<Target> targets = new ArrayList<>(query.targets.size());
                    for (LocatorNetwork.TargetSpec spec : query.targets) {
                        Target t = Target.from(spec);
                        if (t != null) targets.add(t);
                    }
                    if (!targets.isEmpty()) {
                        Scan scan = new Scan(player, connection, owner, world, x, y, z, query.sequence, targets);
                        active.put(owner, scan);
                        scans.addLast(scan);
                        return;
                    }
                    status = LocatorNetwork.INVALID_TARGET;
                }
            }
        }
        LocatorNetwork.send(player, query.sequence, dimension, status, Collections.emptyList());
    }

    private static boolean isCurrent(NetHandlerPlayServer connection, EntityPlayerMP player) {
        return player != null && connection.playerEntity == player && player.playerNetServerHandler == connection
                && connection.netManager.isChannelOpen();
    }

    private void finish(Scan scan, int status) {
        active.remove(scan.owner);
        LocatorNetwork.send(scan.player, scan.sequence, scan.dimension, status,
                status == LocatorNetwork.OK ? scan.results() : Collections.emptyList());
    }

    private static final class Target {
        private final Block exact;
        private final int exactMask;
        private final Map<Block, Integer> family;

        private Target(Block exact, int exactMask, Map<Block, Integer> family) {
            this.exact = exact;
            this.exactMask = exactMask;
            this.family = family;
        }

        static Target from(LocatorNetwork.TargetSpec spec) {
            if (spec.oreKey.isEmpty()) {
                if (!LocatorNetwork.validBlockId(spec.blockId) || spec.meta < -1 || spec.meta > 15) return null;
                Block block = Block.getBlockById(spec.blockId);
                return block != null && Block.getIdFromBlock(block) == spec.blockId
                        ? new Target(block, spec.meta == -1 ? 0xFFFF : 1 << spec.meta, null) : null;
            }
            if (!LocatorNetwork.validOreKey(spec.oreKey)) return null;
            // false = do not intern unknown dictionary names supplied by clients.
            List<ItemStack> entries = OreDictionary.getOres(spec.oreKey, false);
            if (entries.size() > MAX_ORE_ENTRIES) return null;
            Map<Block, Integer> family = new HashMap<>();
            for (ItemStack stack : entries) {
                if (stack == null || !(stack.getItem() instanceof ItemBlock)) continue;
                Block block = Block.getBlockFromItem(stack.getItem());
                if (block == null || !LocatorNetwork.validBlockId(Block.getIdFromBlock(block))) continue;
                int damage = stack.getItemDamage();
                int mask;
                if (damage == OreDictionary.WILDCARD_VALUE) mask = 0xFFFF;
                else {
                    int placementMeta = stack.getItem().getMetadata(damage);
                    if (placementMeta < 0 || placementMeta > 15) continue;
                    mask = 1 << placementMeta;
                }
                Integer old = family.get(block);
                family.put(block, old == null ? mask : old | mask);
            }
            return family.isEmpty() ? null : new Target(null, 0, family);
        }

        int mask(Block block) {
            if (family == null) return exact == block ? exactMask : 0;
            Integer mask = family.get(block);
            return mask == null ? 0 : mask;
        }
    }

    private static final class Scan {
        private static final int WIDTH = LocatorNetwork.CHUNK_RADIUS * 2 + 1;
        private static final int AREA = WIDTH * WIDTH;
        private static final int HEIGHT = 16;
        private static final int BLOCKS_PER_CHUNK = 16 * 16 * 256;

        private final EntityPlayerMP player;
        private final NetHandlerPlayServer connection;
        private final UUID owner;
        private final WorldServer world;
        private final int x, y, z, dimension, sequence;
        private final int minChunkX, minChunkZ, minBlockX, minBlockZ;
        private final List<Target> targets;
        private final Position[] positions = new Position[LocatorNetwork.MAX_RESULTS];
        private final double[] distances = new double[LocatorNetwork.MAX_RESULTS];
        private final Chunk[] chunks = new Chunk[AREA];
        private final boolean[] checkedChunks = new boolean[AREA];
        private int cursor;
        private int found;
        private double farthest = Double.POSITIVE_INFINITY;
        private int cachedSectionIndex = -1;
        private ExtendedBlockStorage cachedSection;

        Scan(EntityPlayerMP player, NetHandlerPlayServer connection, UUID owner, WorldServer world,
             int x, int y, int z, int sequence, List<Target> targets) {
            this.player = player;
            this.connection = connection;
            this.owner = owner;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = player.dimension;
            this.sequence = sequence;
            this.targets = targets;
            this.minChunkX = (x >> 4) - LocatorNetwork.CHUNK_RADIUS;
            this.minChunkZ = (z >> 4) - LocatorNetwork.CHUNK_RADIUS;
            this.minBlockX = minChunkX << 4;
            this.minBlockZ = minChunkZ << 4;
        }

        boolean nearOrigin() {
            return (player.posX >= minBlockX && player.posX < minBlockX + WIDTH * 16
                    && player.posZ >= minBlockZ && player.posZ < minBlockZ + WIDTH * 16);
        }

        boolean done() {
            return cursor >= AREA * BLOCKS_PER_CHUNK;
        }

        int step(int budget) {
            cachedSectionIndex = -1;
            cachedSection = null;
            Arrays.fill(checkedChunks, false);
            Arrays.fill(chunks, null);
            int work = 0;
            ChunkProviderServer provider = world.theChunkProviderServer;
            while (work < budget && !done()) {
                int chunkIndex = cursor / BLOCKS_PER_CHUNK;
                int local = cursor % BLOCKS_PER_CHUNK;
                int sectionY = local / 4096;
                int blockIndex = local & 4095;
                cursor++;
                work++;
                if (!checkedChunks[chunkIndex]) {
                    int cx = minChunkX + chunkIndex / WIDTH;
                    int cz = minChunkZ + chunkIndex % WIDTH;
                    chunks[chunkIndex] = (Chunk) provider.loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(cx, cz));
                    checkedChunks[chunkIndex] = true;
                }
                Chunk chunk = chunks[chunkIndex];
                if (chunk == null) {
                    cursor = (chunkIndex + 1) * BLOCKS_PER_CHUNK;
                    continue;
                }
                int sectionIndex = chunkIndex * HEIGHT + sectionY;
                if (cachedSectionIndex != sectionIndex) {
                    cachedSectionIndex = sectionIndex;
                    cachedSection = chunk.getBlockStorageArray()[sectionY];
                }
                ExtendedBlockStorage section = cachedSection;
                if (section == null || section.isEmpty()) {
                    cursor = (sectionIndex + 1) * 4096;
                    continue;
                }
                int lx = blockIndex & 15;
                int ly = (blockIndex >> 8) & 15;
                int lz = (blockIndex >> 4) & 15;
                int bx = (minChunkX + chunkIndex / WIDTH) * 16 + lx;
                int by = sectionY * 16 + ly;
                int bz = (minChunkZ + chunkIndex % WIDTH) * 16 + lz;
                if (!LocatorNetwork.validXZ(bx) || !LocatorNetwork.validXZ(bz)) continue;
                Block block = section.getBlockByExtId(lx, ly, lz);
                if (block != null && Block.getIdFromBlock(block) != 0) record(block, section, bx, by, bz);
            }
            return work;
        }

        private void record(Block block, ExtendedBlockStorage section, int x, int y, int z) {
            int meta = section.getExtBlockMetadata(x & 15, y & 15, z & 15);
            int targetIndex = -1;
            for (int i = 0; i < targets.size(); i++) {
                if ((targets.get(i).mask(block) & (1 << meta)) != 0) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex < 0) return;
            double dx = x + 0.5 - (this.x + 0.5);
            double dy = y + 0.5 - (this.y + 0.5);
            double dz = z + 0.5 - (this.z + 0.5);
            double distance = dx * dx + dy * dy + dz * dz;
            if (found == positions.length && distance >= farthest) return;
            int slot = found < positions.length ? found++ : positions.length - 1;
            while (slot > 0 && distance < distances[slot - 1]) {
                if (slot < positions.length) {
                    positions[slot] = positions[slot - 1];
                    distances[slot] = distances[slot - 1];
                }
                slot--;
            }
            positions[slot] = new Position(x, y, z, Block.getIdFromBlock(block), meta, targetIndex);
            distances[slot] = distance;
            if (found == positions.length) farthest = distances[found - 1];
        }

        List<Position> results() {
            List<Position> result = new ArrayList<>(found);
            for (int i = 0; i < found; i++) result.add(positions[i]);
            return result;
        }
    }
}
