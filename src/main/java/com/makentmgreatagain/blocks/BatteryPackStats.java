package com.makentmgreatagain.blocks;

/**
 * 大型多方块电池组的「容量 / 充放电速率」聚合 —— 纯逻辑，不依赖 Minecraft，可独立单测。
 * <ol>
 *   <li><b>容量</b>：所有电池容量<b>相加</b>。堆叠电池加容量。</li>
 *   <li><b>基础充/放速率</b>：取组内<b>最低</b>那块电池的充/放速率。
 *       堆叠电池<b>不</b>提速 —— 整组被最弱的那块电池限速。</li>
 *   <li><b>电容提速</b>：电容不存电、不加容量，但<b>大幅</b>提升整组充放电速率，
 *       且<b>可堆叠</b>。倍率 = {@code 1 + 电容值之和 / CAP_HE_PER_MULT}，
 *       电容越多倍率越高，线性叠加。</li>
 * </ol>
 *
 * <h2>最终速率</h2>
 * <p>{@code 最终充/放 = 基础充/放 × 倍率}。没有电池时容量与速率都为 0
 * （电容 alone 不构成储能）；没有电容时倍率为 1（不提速）。</p>
 *
 * <h2>提速强度是可调假设</h2>
 * <p>{@link #} 默认 1,000,000：每 1M 电容值 +1× 倍率。
 * 金电容(5M)→×6，Sa酸铁(15G)→×15001，体现「大幅」。用户未给具体公式，
 * 这是集中可调的默认值，改这一个常量即可整体调强/调弱。</p>
 */
public final class BatteryPackStats {

    public final long capacity;
    public final long baseCharge;
    public final long baseDischarge;
    public final long capacitorTotal;
    /** 充电总倍率（1 = 无电容）。 */
    public final double chargeMult;
    /** 放电总倍率，始终 ≥ 充电倍率。 */
    public final double dischargeMult;
    public final long chargeSpeed;
    public final long dischargeSpeed;

    private BatteryPackStats(long capacity, long baseCharge, long baseDischarge,
                             long capacitorTotal, double chargeMult, double dischargeMult) {
        this.capacity = capacity;
        this.baseCharge = baseCharge;
        this.baseDischarge = baseDischarge;
        this.capacitorTotal = capacitorTotal;
        this.chargeMult = chargeMult;
        this.dischargeMult = dischargeMult;
        this.chargeSpeed = (long) (baseCharge * chargeMult);
        this.dischargeSpeed = (long) (baseDischarge * dischargeMult);
    }

    /**
     * 按结构内元件列表聚合。
     *
     * <p>电容倍率<b>按等级查表</b>（金 1.1/1.3、铌 1.4/1.8、钽 1.8/2.5、Sa酸铁 2.5/5），
     * <b>可堆叠</b>：每块电容把自己的「超出 1 的部分」累加进总倍率，
     * 所以两块金 = 充 1.2× / 放 1.6×，金+铌 = 充 1.5× / 放 2.1×。</p>
     *
     * <p>{@code cells} 里混入非电池非电容（如外壳/端口）或 null 都忽略。</p>
     */
    public static BatteryPackStats compute(BatteryCellSpec[] cells) {
        long capacity = 0;
        long minCharge = Long.MAX_VALUE;
        long minDischarge = Long.MAX_VALUE;
        long capTotal = 0;
        long minCapCapacity = Long.MAX_VALUE;
        double chargeMult = 1.0D;
        double dischargeMult = 1.0D;
        boolean hasBattery = false;
        boolean hasCapacitor = false;

        if (cells != null) {
            for (BatteryCellSpec c : cells) {
                if (c == null) continue;
                if (c.isBattery()) {
                    hasBattery = true;
                    capacity += c.capacity;
                    if (c.chargeSpeed < minCharge) minCharge = c.chargeSpeed;
                    if (c.dischargeSpeed < minDischarge) minDischarge = c.dischargeSpeed;
                } else if (c.isCapacitor()) {
                    // 电容也储能：电容值计入总容量，否则全电容结构 maxPower=0，
                    // 电力网算出的可接收量恒为 0，永远充不进电。
                    capacity += c.capacity;
                    if (c.capacity > 0) {
                        hasCapacitor = true;
                        if (c.capacity < minCapCapacity) minCapCapacity = c.capacity;
                    }
                    capTotal += c.capacity;
                    // 只累加「超出 1 的增益」，保证无电容时倍率为 1、堆叠线性相加
                    chargeMult += Math.max(0.0D, c.chargeMult - 1.0D);
                    dischargeMult += Math.max(0.0D, c.dischargeMult - 1.0D);
                }
            }
        }

        // 没有任何元件：空结构
        if (!hasBattery && !hasCapacitor) {
            return new BatteryPackStats(0, 0, 0, capTotal, chargeMult, dischargeMult);
        }

        // 全电容（无电池）：按电容值折算基础速率，口径与电池一致
        // （充电 /200 快、放电 /600 慢）。电容放电档倍率更高，
        // 叠高后放电可能反超充电，属正常设计。
        if (!hasBattery) {
            minCharge = minCapCapacity / 200L;
            minDischarge = minCapCapacity / 600L;
        }

        return new BatteryPackStats(capacity, minCharge, minDischarge, capTotal, chargeMult, dischargeMult);
    }
}
