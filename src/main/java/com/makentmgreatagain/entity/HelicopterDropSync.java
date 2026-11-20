package com.makentmgreatagain.entity;

import java.nio.ByteBuffer;

/**
 * 耐摔合金掉落物「生成包同步」的编解码 —— 纯逻辑，不依赖 Minecraft / Netty，可独立单元测试。
 *
 * <h2>为什么必须同步这三个量</h2>
 * <p>轨迹是 {@code ticksExisted} 的纯函数，理论上两端各算各的就该一致。但 FML 客户端
 * 建实体的顺序有个坑（{@code EntitySpawnHandler.spawnEntityIntoClientWorld}）：</p>
 * <ol>
 *   <li>{@code cls.getConstructor(World.class).newInstance(wc)} —— 此时 {@code posY = 0}，
 *       构造器把 {@code spawnY} 也设成了 <b>0</b>；</li>
 *   <li>之后才 {@code setLocationAndAngles(...)} 设真实位置 —— 但 {@code spawnY} 不会跟着变；</li>
 *   <li>最后回调 {@code readSpawnData}。</li>
 * </ol>
 *
 * <p>于是客户端的 {@code spawnY = 0}、服务端是真实落点高度。上升段的
 * {@code remaining = (spawnY + 24) - posY} 两边算出的目标高度不同
 * （客户端 24、服务端 落点+24），客户端先朝错误目标走一遍，再被服务端位置包
 * 硬拽回正确轨迹 —— 表现就是「飞上去后卡顿着从下往上重走一遍，然后才坠落」。</p>
 *
 * <p>所以这三个量必须随包传：{@code spawnY}（轨迹基准）、{@code liftEnabled}
 * （护甲关演出，客户端也要知道，否则仍按飞行渲染）、{@code ticksExisted}
 * （接着服务端已走过的 tick 继续，不从 0 重放）。</p>
 *
 * <h2>格式</h2>
 * <p>定长 {@link #SIZE} 字节：{@code double spawnY} + {@code byte liftEnabled}
 * + {@code int ticksExisted}，大端。定长的好处是读的时候不需要先读长度，
 * 也不会因为版本不一致而错位读到后面的字段。</p>
 */
public final class HelicopterDropSync {

    /** 同步载荷的字节数：8(double) + 1(boolean) + 4(int)。 */
    public static final int SIZE = 13;

    /** 解出来的同步状态。 */
    public static final class State {
        public final double spawnY;
        public final boolean liftEnabled;
        public final int ticksExisted;

        State(double spawnY, boolean liftEnabled, int ticksExisted) {
            this.spawnY = spawnY;
            this.liftEnabled = liftEnabled;
            this.ticksExisted = ticksExisted;
        }
    }

    private HelicopterDropSync() {
    }

    /** 服务端：把三个量编成定长载荷。 */
    public static byte[] write(double spawnY, boolean liftEnabled, int ticksExisted) {
        ByteBuffer buf = ByteBuffer.allocate(SIZE);
        buf.putDouble(spawnY);
        buf.put(liftEnabled ? (byte) 1 : (byte) 0);
        buf.putInt(ticksExisted);
        return buf.array();
    }

    /**
     * 客户端：解出同步状态。
     *
     * <p>长度不足时退回「起点 0 / 演出开 / 年龄 0」而不是抛异常 ——
     * 生成包解析失败不该让整个客户端崩掉，最坏情况只是这一个掉落物轨迹不对。</p>
     */
    public static State read(byte[] data) {
        if (data == null || data.length < SIZE) {
            return new State(0.0D, true, 0);
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        double spawnY = buf.getDouble();
        boolean liftEnabled = buf.get() != 0;
        int ticksExisted = buf.getInt();
        return new State(spawnY, liftEnabled, ticksExisted);
    }

    /**
     * 决定实际采用的起点高度：<b>一律用同步值</b>。
     *
     * <p>构造器给的那个值（客户端是 0）不可信，见类注释。保留这个参数是为了
     * 把「覆盖构造器值」这件事显式写出来，也方便单元测试锁住它。</p>
     *
     * @param constructorValue 构造器里算出的 spawnY（客户端为 0，不可信）
     * @param state            从生成包解出的状态
     */
    public static double resolveSpawnY(double constructorValue, State state) {
        return state.spawnY;
    }
}