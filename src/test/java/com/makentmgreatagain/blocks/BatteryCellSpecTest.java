package com.makentmgreatagain.blocks;

/**
 * 附属版「弃用单方块电池 / 电容」数值表的独立单元测试（不依赖 Minecraft / Forge）。
 *
 * <p>用 {@code javac + java} 直接跑，main 里逐条断言，失败抛 AssertionError。
 * TDD 的 RED 阶段：先写期望行为，再实现 {@link BatteryCellSpec} 让它变绿。</p>
 *
 * <h2>为什么这张表值得单测</h2>
 * <p>这些数值抄自 HBM 原版 {@code ModBlocks} 的注册行，后续要用来算大型多方块
 * 电池组的总容量与放电速率 —— 抄错一个零，容量就差三个数量级，而且不报错。</p>
 *
 * <h2>原版电容的一个坑（必须修正）</h2>
 * <p>原版 {@code MachineCapacitor} 的构造函数是
 * {@code public MachineCapacitor(Material mat, long power, String name)}，
 * 但方法体里<b>只存了 name，把 power 直接丢弃了</b>，而且它是纯装饰方块、没有 TE。
 * 所以电容的数值只活在注册行的字面量里，运行时根本查不到。
 * 附属版必须把 power 真正存下来，否则「用电容决定放电速率」无从实现。</p>
 *
 * <h2>速率口径</h2>
 * <p>电池的充放速率沿用原版 {@code TileEntityMachineBattery} 的公式：
 * 充电 {@code maxPower / 200}、放电 {@code maxPower / 600}
 * （原版 {@code addInformation} 里给玩家看的也是这两个数）。</p>
 */
public final class BatteryCellSpecTest {

    private static int passed = 0;

    public static void main(String[] args) {
        testBatteryCapacities();
        testBatteryChargeSpeed();
        testBatteryDischargeSpeed();
        testChargeFasterThanDischarge();
        testQuantumBeatsSchrabidium();
        testCapacitorValues();
        testCapacitorBusHasNoValue();
        testCount();
        testLookupByName();
        testLookupByUnknown();
        testBatteryTextureNames();
        testCapacitorMaterialName();
        System.out.println("ALL " + passed + " TESTS PASSED");
    }

    /** 五种电池的容量（土豆、Spark 已删；新增钠离子与量子）。 */
    private static void testBatteryCapacities() {
        check(BatteryCellSpec.BATTERY.capacity == 1_000_000L, "普通电池容量");
        check(BatteryCellSpec.LITHIUM_BATTERY.capacity == 50_000_000L, "锂电池容量");
        check(BatteryCellSpec.SODIUM_BATTERY.capacity == 500_000_000L, "钠离子电池容量");
        check(BatteryCellSpec.SCHRABIDIUM_BATTERY.capacity == 25_000_000_000L, "钍电池容量");
        check(BatteryCellSpec.QUANTUM_BATTERY.capacity == 75_000_000_000L, "量子电池容量 75G");
        passed++;
    }

    /**
     * 充电速率 = 容量 / 200 × 速率倍率（快的那一档）。
     * 锂 ×0.5（砍一半）、Sa326 ×0.2（减 80%），其余 ×1。
     */
    private static void testBatteryChargeSpeed() {
        check(BatteryCellSpec.BATTERY.chargeSpeed == 5_000L, "普通电池充电速率");
        // 50M/200=250000 ×0.5=125000
        check(BatteryCellSpec.LITHIUM_BATTERY.chargeSpeed == 125_000L, "锂电池充电速率（砍半）");
        // 500M/200=2500000（满速）
        check(BatteryCellSpec.SODIUM_BATTERY.chargeSpeed == 2_500_000L, "钠离子充电速率（满速）");
        // 25G 显式速率：充 6.25M/t（与放电对调后）
        check(BatteryCellSpec.SCHRABIDIUM_BATTERY.chargeSpeed == 6_250_000L, "Sa326充电速率（显式 6.25M）");
        // 量子对调后：充 25M/t
        check(BatteryCellSpec.QUANTUM_BATTERY.chargeSpeed == 25_000_000L, "量子充电速率（对调后 25M）");
        passed++;
    }

    /**
     * 放电速率 = 容量 / 600 × 速率倍率（慢的那一档，整除截断）。
     * 锂 ×0.5、Sa326 ×0.2，其余 ×1。
     */
    private static void testBatteryDischargeSpeed() {
        check(BatteryCellSpec.BATTERY.dischargeSpeed == 1_666L, "普通电池放电速率");
        check(BatteryCellSpec.LITHIUM_BATTERY.dischargeSpeed == 41_666L, "锂电池放电速率（砍半）");
        check(BatteryCellSpec.SODIUM_BATTERY.dischargeSpeed == 833_333L, "钠离子放电速率（满速）");
        check(BatteryCellSpec.SCHRABIDIUM_BATTERY.dischargeSpeed == 2_100_000L, "Sa326放电速率（显式 2.1M）");
        check(BatteryCellSpec.QUANTUM_BATTERY.dischargeSpeed == 8_333_333L, "量子放电速率（对调后 8.33M）");
        passed++;
    }

    /** 量子是最高级：容量与充放电速率都必须严格强于 Sa326。 */
    private static void testQuantumBeatsSchrabidium() {
        BatteryCellSpec q = BatteryCellSpec.QUANTUM_BATTERY;
        BatteryCellSpec s = BatteryCellSpec.SCHRABIDIUM_BATTERY;
        check(q.capacity > s.capacity, "量子容量应高于 Sa326");
        check(q.chargeSpeed > s.chargeSpeed, "量子充电应快于 Sa326");
        check(q.dischargeSpeed > s.dischargeSpeed, "量子放电应快于 Sa326");
        passed++;
    }

    /** 每种电池都必须「充电快于放电」（用户最新口径）。 */
    private static void testChargeFasterThanDischarge() {
        for (BatteryCellSpec b : BatteryCellSpec.batteries()) {
            check(b.chargeSpeed > b.dischargeSpeed, "电池必须充电快于放电: " + b.name);
        }
        passed++;
    }

    /**
     * 五种弃用电容的数值 —— 这些数在原版是被构造函数丢弃的，附属版必须真正保留。
     * 对照 HBM 原版注册行的字面量。
     *
     * <p>注意 copper 不在列：原版 {@code capacitor_copper} 没有 {@code @Deprecated}
     * 标记（注释写的是 "neat for structures"，仍在用），不属于「弃用未删除」。</p>
     */
    private static void testCapacitorValues() {
        check(BatteryCellSpec.GOLD_CAPACITOR.capacity == 5_000_000L, "金电容数值");
        check(BatteryCellSpec.NIOBIUM_CAPACITOR.capacity == 25_000_000L, "铌电容数值");
        check(BatteryCellSpec.TANTALIUM_CAPACITOR.capacity == 150_000_000L, "钽电容数值");
        check(BatteryCellSpec.SCHRABIDATE_CAPACITOR.capacity == 15_000_000_000L, "钍酸电容数值（15G）");
        passed++;
    }

    /** 电容总线已按需求删除，不再存在。 */
    private static void testCapacitorBusHasNoValue() {
        check(BatteryCellSpec.byName("capacitor_bus") == null, "电容总线应已删除");
        passed++;
    }

    /** 电池 5 种 + 电容 4 种（总线已删，不含仍在用的铜）= 9 种。 */
    private static void testCount() {
        check(BatteryCellSpec.batteries().length == 5, "电池应有 5 种，实际 " + BatteryCellSpec.batteries().length);
        check(BatteryCellSpec.capacitors().length == 4, "电容应有 4 种，实际 " + BatteryCellSpec.capacitors().length);
        passed++;
    }

    /** 按内部名查得到自己（多方块成型后要靠原方块反查数值）。 */
    private static void testLookupByName() {
        check(BatteryCellSpec.byName("battery_lithium") == BatteryCellSpec.LITHIUM_BATTERY, "按名查锂电池");
        check(BatteryCellSpec.byName("capacitor_gold") == BatteryCellSpec.GOLD_CAPACITOR, "按名查金电容");
        passed++;
    }

    /** 查不到返回 null，不能抛异常（多方块里可能混进别的模组的方块）。 */
    private static void testLookupByUnknown() {
        check(BatteryCellSpec.byName("not_a_thing") == null, "未知名称应返回 null");
        check(BatteryCellSpec.byName(null) == null, "null 应返回 null");
        check(BatteryCellSpec.byName("") == null, "空串应返回 null");
        passed++;
    }

    /**
     * 电池贴图名必须与 HBM 原版逐一对上（贴图直接复用原版文件，名字错了就是紫黑块）。
     *
     * <p>特例：原版 {@code machine_battery} 用的是 {@code battery_*_alt} 三张，
     * <b>不</b>遵循「battery_材质_front」的规律；其余四种才遵循规律。
     * 所以贴图名只能显式存，不能从内部名推导。</p>
     */
    private static void testBatteryTextureNames() {
        // 特例：普通电池走 _alt 后缀
        check("battery_front_alt".equals(BatteryCellSpec.BATTERY.texFront), "普通电池正面贴图");
        check("battery_top".equals(BatteryCellSpec.BATTERY.texTop), "普通电池顶面贴图");
        check("battery_side_alt".equals(BatteryCellSpec.BATTERY.texSide), "普通电池侧面贴图");

        // 规律命名：battery_<材质>_{front,top,side}
        check("battery_sodium_front".equals(BatteryCellSpec.SODIUM_BATTERY.texFront), "钠离子正面");
        check("battery_sodium_top".equals(BatteryCellSpec.SODIUM_BATTERY.texTop), "钠离子顶面");
        check("battery_sodium_side".equals(BatteryCellSpec.SODIUM_BATTERY.texSide), "钠离子侧面");

        check("battery_lithium_front".equals(BatteryCellSpec.LITHIUM_BATTERY.texFront), "锂电池正面");
        check("battery_lithium_top".equals(BatteryCellSpec.LITHIUM_BATTERY.texTop), "锂电池顶面");
        check("battery_lithium_side".equals(BatteryCellSpec.LITHIUM_BATTERY.texSide), "锂电池侧面");

        check("battery_schrabidium_front".equals(BatteryCellSpec.SCHRABIDIUM_BATTERY.texFront), "钍电池正面");
        check("battery_schrabidium_top".equals(BatteryCellSpec.SCHRABIDIUM_BATTERY.texTop), "钍电池顶面");
        check("battery_schrabidium_side".equals(BatteryCellSpec.SCHRABIDIUM_BATTERY.texSide), "钍电池侧面");

        passed++;
    }

    /**
     * 电容材质名必须去掉 "capacitor_" 前缀 —— 原版父类按
     * {@code capacitor_<name>_top} 拼贴图名，若把 "capacitor_gold" 整个当 name
     * 传进去，会拼成 "capacitor_capacitor_gold_top"，贴图找不到（紫黑块）。
     *
     * <p>总线是普通方块、不走父类那套拼接，材质名对它无意义，返回空串即可。</p>
     */
    private static void testCapacitorMaterialName() {
        check("gold".equals(BatteryCellSpec.GOLD_CAPACITOR.materialName), "金电容材质名");
        check("niobium".equals(BatteryCellSpec.NIOBIUM_CAPACITOR.materialName), "铌电容材质名");
        check("tantalium".equals(BatteryCellSpec.TANTALIUM_CAPACITOR.materialName), "钽电容材质名");
        check("schrabidate".equals(BatteryCellSpec.SCHRABIDATE_CAPACITOR.materialName), "钍酸电容材质名");
        // 电池没有 capacitor_ 前缀，材质名原样返回（电池不走父类拼接，用不到）
        check("battery".equals(BatteryCellSpec.BATTERY.materialName), "电池材质名原样");
        passed++;
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private BatteryCellSpecTest() {
    }
}