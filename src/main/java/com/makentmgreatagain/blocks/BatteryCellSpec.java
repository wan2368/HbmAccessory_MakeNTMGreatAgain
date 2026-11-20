package com.makentmgreatagain.blocks;

/**
 * 附属版「弃用单方块电池 / 电容」的数值表 —— 纯数据，不依赖 Minecraft，可独立单元测试。
 *
 * <h2>这些方块是干什么用的</h2>
 * <p>HBM 原版有一批标了 {@code @Deprecated} 但没删除的单方块储能件
 * （5 种电池 + 5 种电容）。附属把它们各复制一份，<b>但不做 UI、不存电、不参与电网</b>：
 * 它们只作为大型多方块电池组的「元件」，成型时数一数里面放了几块、都是什么，
 * 据此算出整组的总容量与放电速率。所以数值必须准确、且运行时可查。</p>
 *
 * <h2>原版的两个坑（附属版都修了）</h2>
 * <ol>
 *   <li><b>电容数值被构造函数丢弃</b>：原版 {@code MachineCapacitor} 的签名是
 *       {@code (Material mat, long power, String name)}，但方法体只存了 {@code name}，
 *       {@code power} 直接扔掉；而且它是纯装饰方块、没有 TE。于是电容的数值只活在
 *       {@code ModBlocks} 注册行的字面量里，运行时<b>根本查不到</b>。本类把它固化下来。</li>
 *   <li><b>渲染器硬强转</b>：原版 {@code RenderCapacitor} 里是
 *       {@code (MachineCapacitor) block}，还访问它的 5 个图标字段。附属电容方块
 *       <b>必须继承 {@code MachineCapacitor}</b> 才能复用那个 OBJ 模型渲染，
 *       否则放下去就 ClassCastException。见 {@code BlockAddonCapacitor}。</li>
 * </ol>
 *
 * <h2>速率口径</h2>
 * <p>电池的充放速率沿用原版 {@code TileEntityMachineBattery} 的公式：
 * 充电 {@code capacity / 200}、放电 {@code capacity / 600}。
 * 原版 {@code MachineBattery.addInformation} 给玩家看的也是这两个数，口径一致。</p>
 *
 * <h2>为什么电容没有速率</h2>
 * <p>原版电容本来就没有速率概念（连 TE 都没有）。多方块那边如果要用电容调速，
 * 由多方块自己定义公式（例如按电容数值之和取比例），本类只负责提供数值。</p>
 */
public final class BatteryCellSpec {

    /** 种类：电池还是电容。 */
    public enum Kind { BATTERY, CAPACITOR }

    /** 内部名（与方块注册名、贴图前缀一致），运行时按它反查。 */
    public final String name;
    public final Kind kind;
    /** 容量（HE）。电容这里是原版注册行里那个被丢弃的 power 值；总线为 0。 */
    public final long capacity;
    /** 充电速率（HE/t）= 容量 / 200（快档）。电容为 0。 */
    public final long chargeSpeed;
    /** 放电速率（HE/t）= 容量 / 600（慢档）。电容为 0。 */
    public final long dischargeSpeed;

    /**
     * 电容的<b>充电</b>提速倍率（电池恒为 1）。用户给定的四档：
     * 金 1.1、铌 1.4、钽 1.8、Sa酸铁 2.5。
     */
    public final double chargeMult;
    /**
     * 电容的<b>放电</b>提速倍率（电池恒为 1），<b>始终高于充电</b>：
     * 金 1.3、铌 1.8、钽 2.5、Sa酸铁 5.0。
     */
    public final double dischargeMult;

    /**
     * 电池的三面贴图名（相对 {@code assets/hbm/textures/blocks/}，直接复用原版贴图）。
     *
     * <p><b>不能从内部名推导</b>：原版 {@code machine_battery} 用的是
     * {@code battery_front_alt / battery_top / battery_side_alt}，不遵循
     * 「battery_材质_front」的规律；其余四种才遵循。所以三张图名显式存。</p>
     *
     * <p>电容用 OBJ 模型渲染（原版 {@code RenderCapacitor}），走的是
     * {@code capacitor_材质_{top,side,bottom,inner_top,inner_side}} 五张图，
     * 由方块类按材质名拼，不在这里存。</p>
     */
    public final String texFront;
    public final String texTop;
    public final String texSide;

    /**
     * 电容的材质名（{@code gold / niobium / tantalium / schrabidate}），用于拼贴图。
     *
     * <p><b>必须从 {@link #name} 去掉 {@code capacitor_} 前缀</b>：原版父类
     * {@code MachineCapacitor.registerBlockIcons} 按 {@code capacitor_<name>_top} 拼图标名，
     * 若把完整的 {@code capacitor_gold} 当 name 传进去，会拼成
     * {@code capacitor_capacitor_gold_top} —— 这张图不存在，方块就显示紫黑块。</p>
     *
     * <p>电池与总线不走那套拼接，材质名对它们没有实际用途，原样返回 {@link #name}。</p>
     */
    public final String materialName;

    /* ---------------- 电池（4 种：普通 / 锂 / 钠离子 / Sa326） ---------------- */

    /** 电池 machine_battery —— 贴图是 _alt 特例，不遵循材质命名规律 */
    public static final BatteryCellSpec BATTERY =
            new BatteryCellSpec("battery", Kind.BATTERY, 1_000_000L,
                    "battery_front_alt", "battery_top", "battery_side_alt");
    /** 锂电池 machine_lithium_battery（充放电速率砍半） */
    public static final BatteryCellSpec LITHIUM_BATTERY =
            new BatteryCellSpec("battery_lithium", Kind.BATTERY, 50_000_000L, 0.5D,
                    "battery_lithium_front", "battery_lithium_top", "battery_lithium_side");
    /** 钠离子电池（附属新增，比锂高级：容量 500M、充放电满速率，贴图自绘钠焰色金黄） */
    public static final BatteryCellSpec SODIUM_BATTERY =
            new BatteryCellSpec("battery_sodium", Kind.BATTERY, 500_000_000L,
                    "battery_sodium_front", "battery_sodium_top", "battery_sodium_side");
    /** 钍电池 machine_schrabidium_battery：速率由用户显式指定（充 6.25M/t、放 2.1M/t，充电快于放电） */
    public static final BatteryCellSpec SCHRABIDIUM_BATTERY =
            new BatteryCellSpec("battery_schrabidium", Kind.BATTERY, 25_000_000_000L,
                    6_250_000L, 2_100_000L,
                    "battery_schrabidium_front", "battery_schrabidium_top", "battery_schrabidium_side");
    /**
     * 量子电池（附属新增，最高级）：容量 75G，速率与 Sa326 同口径对调
     * （充 25,000,000 / 放 8,333,333），因此在容量与速率上都严格强于 Sa326。
     * 样式参照原版量子电池：深灰外壳 + 紫色核心 + 品红节点。
     */
    public static final BatteryCellSpec QUANTUM_BATTERY =
            new BatteryCellSpec("battery_quantum", Kind.BATTERY, 75_000_000_000L,
                    25_000_000L, 8_333_333L,
                    "battery_quantum_front", "battery_quantum_top", "battery_quantum_side");

    /* ---------------- 电容（5 种，含无数值的总线） ---------------- */

    /** 金电容 capacitor_gold：充 1.1× / 放 1.3× */
    public static final BatteryCellSpec GOLD_CAPACITOR =
            new BatteryCellSpec("capacitor_gold", Kind.CAPACITOR, 5_000_000L, 1.1D, 1.3D);
    /** 铌电容 capacitor_niobium：充 1.4× / 放 1.8× */
    public static final BatteryCellSpec NIOBIUM_CAPACITOR =
            new BatteryCellSpec("capacitor_niobium", Kind.CAPACITOR, 25_000_000L, 1.4D, 1.8D);
    /** 钽电容 capacitor_tantalium：充 1.8× / 放 2.5× */
    public static final BatteryCellSpec TANTALIUM_CAPACITOR =
            new BatteryCellSpec("capacitor_tantalium", Kind.CAPACITOR, 150_000_000L, 1.8D, 2.5D);
    /** 钍酸电容 capacitor_schrabidate（15G）：充 2.5× / 放 5× */
    public static final BatteryCellSpec SCHRABIDATE_CAPACITOR =
            new BatteryCellSpec("capacitor_schrabidate", Kind.CAPACITOR, 15_000_000_000L, 2.5D, 5.0D);

    private static final BatteryCellSpec[] BATTERIES = {
            BATTERY, LITHIUM_BATTERY, SODIUM_BATTERY, SCHRABIDIUM_BATTERY, QUANTUM_BATTERY
    };

    private static final BatteryCellSpec[] CAPACITORS = {
            GOLD_CAPACITOR, NIOBIUM_CAPACITOR, TANTALIUM_CAPACITOR, SCHRABIDATE_CAPACITOR
    };

    /** 电容用（不提速，如总线）。 */
    private BatteryCellSpec(String name, Kind kind, long capacity) {
        this(name, kind, capacity, 1.0D, 1.0D, 1.0D, null, null, null);
    }

    /** 电容用（指定充/放倍率）。 */
    private BatteryCellSpec(String name, Kind kind, long capacity, double chargeMult, double dischargeMult) {
        this(name, kind, capacity, 1.0D, chargeMult, dischargeMult, null, null, null);
    }

    /** 电池用（默认速率倍率 1）：三面贴图名显式给，原版有 _alt 特例不能推导。 */
    private BatteryCellSpec(String name, Kind kind, long capacity,
                            String texFront, String texTop, String texSide) {
        this(name, kind, capacity, 1.0D, 1.0D, 1.0D, texFront, texTop, texSide);
    }

    /**
     * 电池用（指定速率倍率）：{@code speedMultiplier} 只乘在充放电速率上，<b>不影响容量</b>。
     * 锂 ×0.5（砍半）、Sa326 ×0.2（减 80%）。
     */
    private BatteryCellSpec(String name, Kind kind, long capacity, double speedMultiplier,
                            String texFront, String texTop, String texSide) {
        this(name, kind, capacity, speedMultiplier, 1.0D, 1.0D, texFront, texTop, texSide);
    }

    /**
     * 电池用（<b>显式指定充放电速率</b>）：速率不再由容量除数推导。
     *
     * <p>Sa326 与量子电池的速率是用户直接给定的数值，与容量不成固定比例，
     * 所以必须走这个入口，不能套 /600 与 /200 的公式。</p>
     */
    private BatteryCellSpec(String name, Kind kind, long capacity,
                            long chargeSpeed, long dischargeSpeed,
                            String texFront, String texTop, String texSide) {
        this.name = name;
        this.kind = kind;
        this.capacity = capacity;
        this.texFront = texFront;
        this.texTop = texTop;
        this.texSide = texSide;
        this.chargeMult = 1.0D;
        this.dischargeMult = 1.0D;
        this.materialName = name;
        this.chargeSpeed = chargeSpeed;
        this.dischargeSpeed = dischargeSpeed;
    }

    private BatteryCellSpec(String name, Kind kind, long capacity, double speedMultiplier,
                            double chargeMult, double dischargeMult,
                            String texFront, String texTop, String texSide) {
        this.name = name;
        this.kind = kind;
        this.capacity = capacity;
        this.texFront = texFront;
        this.texTop = texTop;
        this.texSide = texSide;
        this.chargeMult = chargeMult;
        this.dischargeMult = dischargeMult;

        // 电容贴图名要去掉 capacitor_ 前缀（见 materialName 字段注释）；电池/总线原样
        this.materialName = (kind == Kind.CAPACITOR && name.startsWith("capacitor_"))
                ? name.substring("capacitor_".length())
                : name;

        // 速率只对电池有意义（原版电容连 TE 都没有）；倍率只调速、不调容量
        // 充电用 /200（快），放电用 /600（慢）—— 用户最新口径：充电比放电快。
        if (kind == Kind.BATTERY) {
            this.chargeSpeed = (long) (capacity / 200L * speedMultiplier);
            this.dischargeSpeed = (long) (capacity / 600L * speedMultiplier);
        } else {
            this.chargeSpeed = 0L;
            this.dischargeSpeed = 0L;
        }
    }

    /** 全部电池（返回副本，防止外部改内部表）。 */
    public static BatteryCellSpec[] batteries() {
        return BATTERIES.clone();
    }

    /** 全部电容（返回副本）。 */
    public static BatteryCellSpec[] capacitors() {
        return CAPACITORS.clone();
    }

    /**
     * 按内部名查规格。查不到返回 null，不抛异常 ——
     * 多方块成型时里面可能混进别的模组的方块，容错比崩溃重要。
     */
    public static BatteryCellSpec byName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (BatteryCellSpec spec : BATTERIES) {
            if (spec.name.equals(name)) return spec;
        }
        for (BatteryCellSpec spec : CAPACITORS) {
            if (spec.name.equals(name)) return spec;
        }
        return null;
    }

    /** 是不是电池（多方块统计容量时用）。 */
    public boolean isBattery() {
        return kind == Kind.BATTERY;
    }

    /** 是不是电容（多方块统计放电速率时用）。 */
    public boolean isCapacitor() {
        return kind == Kind.CAPACITOR;
    }
}