package com.makentmgreatagain.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;

/**
 * 世界生成的诊断计数（排查"火星找不到矿"这类问题用）。
 *
 * <p>光看游戏里有没有矿，分不清是「生成器没被调用」「维度 ID 没对上」
 * 「宿主岩没查到」还是「概率太低」。这里按矿种记两个数：
 * 扫过的 Duna 区块数、实际落下的矿石格数，每 {@value #EVERY} 个区块
 * 往日志打一行。看到 chunks>0 说明代码跑到了，placed>0 说明真落了矿，
 * host=null 说明 hbm 的 duna_rock 没查到。</p>
 *
 * <p>只在 Duna 分支计数，主世界不打印，免得刷屏。</p>
 */
final class DunaDiag {

    private static final int EVERY = 64;

    private static final Map<String, long[]> counters = new ConcurrentHashMap<String, long[]>();

    private DunaDiag() {
    }

    /** 一个 Duna 区块走完生成后调用，累加本区块落下的矿石格数。 */
    static void tick(String ore, int placedThisChunk, Block host) {
        long[] c = counters.get(ore);
        if(c == null) {
            long[] fresh = new long[2];
            c = counters.putIfAbsent(ore, fresh);
            if(c == null) c = fresh;
        }
        synchronized(c) {
            c[0]++;
            c[1] += placedThisChunk;
            if(c[0] % EVERY == 0) {
                System.out.println("[MNGA worldgen] Duna " + ore
                        + ": chunks=" + c[0] + ", orePlaced=" + c[1]
                        + ", hostRock=" + host);
            }
        }
    }
}
