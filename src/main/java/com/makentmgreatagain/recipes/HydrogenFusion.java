package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.FusionRecipe;
import com.hbm.inventory.recipes.FusionRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.items.ModItems;
import com.makentmgreatagain.init.ModFluids;

import net.minecraft.item.ItemStack;

/**
 * 本模组的聚变配方总表：整条链的数值都收在这里，加热器与仿星器各取一半。
 *
 * <h3>三条配方</h3>
 * <ul>
 *   <li><b>氢 → 氦-3</b>、<b>氢 → 氦-4</b>：加热器吃 100 mB 液氢 → 10 mB 氢等离子体；
 *       仿星器每 tick 烧 20 mB，产 15 mB 氦；</li>
 *   <li><b>氢-硼 → 氦-4</b>：加热器吃 100 mB 氢 + 100 mB 液态硼 → 20 mB 氢-硼等离子体；
 *       仿星器每 tick 烧 20 mB，产 30 mB 氦-4 + 1 电离粒子。p-B11 路线几乎不出中子，
 *       所以通量只有 1 /t，但约束力量是 35M、启动读条 600 tick。</li>
 * </ul>
 *
 * <p>加热器的进料按配方走 {@link #heaterFeed(String)}：一条逻辑输入（氢-氢）会平摊到左右
 * 两个输入罐，两条逻辑输入（氢-硼）则是左罐氢、右罐硼。仿星器只认 {@link #isOurRecipe(String)}
 * 里的配方名，消耗量直接读配方自己的 {@code inputFluid}。</p>
 *
 * <p>等离子体用本模组自己的 {@code PLASMA_H}（粉紫）和 {@code PLASMA_HB}（青绿）。
 * HBM 的聚变表是加热器与仿星器共用的配方源，{@link #addRecipe()} 每次都会<b>先清空整张表</b>
 * 再只注册这三条。副作用是宿主聚变堆（{@code BlockFusionTorus}）的配方表也会只剩这三条。</p>
 *
 * <p>配方名带 {@code makentmgreatagain.} 前缀，交给 {@link RecipeRegistrar} 做幂等注册：
 * HBM 的聚变表在 init 阶段可能按 json 重建，所以既挂监听器分支，也挂 postInit 的兜底注入，
 * 机器每 tick 还会检查一次 {@link #isRegistered()} 自愈。</p>
 */
public final class HydrogenFusion {

    private HydrogenFusion() { }

    /** 氢等离子体 → 氦-3；内部名同时也是语言键。 */
    public static final String RECIPE_HE3 = "makentmgreatagain.fus.hh3";

    /** 氢等离子体 → 氦-4。 */
    public static final String RECIPE_HE4 = "makentmgreatagain.fus.hh4";

    /** 氢-硼等离子体 → 氦-4。 */
    public static final String RECIPE_HB = "makentmgreatagain.fus.hb";

    /** 反物质等离子体 → 反Sa326。 */
    public static final String RECIPE_AMAT = "makentmgreatagain.fus.amat";

    /** 约束态反物质等离子体 → 无输出（纯能量/约束）。 */
    public static final String RECIPE_PAMAT = "makentmgreatagain.fus.pamat";

    /** 反Sa326等离子体 → 星流浆。 */
    public static final String RECIPE_ASCHRAB = "makentmgreatagain.fus.aschrab";

    /* ==================== 氢-氢：共用数值 ==================== */

    /** 氢-氢配方每 tick 的能量消耗（HE/t）：与加热器造这份等离子体的耗电相同 */
    public static final long POWER = 100_000L;

    /** 加热器在氢-氢配方上的耗电（HE/t） */
    public static final long HEATER_POWER = 100_000L;

    /** 点火门槛：不用速调管，有电就能点着 */
    public static final long IGNITION = 0L;

    /* ==================== 加热器侧：液氢 → 氢等离子体 ==================== */

    /** 加热器每 tick 吃的液氢总量（mB）。两侧输入罐各吃一半，合计就是它 */
    public static final int HEATER_HYDROGEN_IN = 100;

    /** 加热器每 tick 产出的氢等离子体（mB） */
    public static final int HEATER_PLASMA_OUT = 10;

    /** 单侧输入罐每 tick 扣的量（两罐合计 = {@link #HEATER_HYDROGEN_IN}） */
    public static final int HEATER_UNITS_PER_TICK = HEATER_HYDROGEN_IN / 2;

    /* ==================== 配方 A：氢等离子体 → 氦-3 ==================== */

    /** 输入：氢等离子体 mB/t */
    public static final int HE3_PLASMA_IN = 20;
    /** 输出流体（null = 这条配方不出流体，只出物品） */
    public static final FluidType HE3_FLUID_OUT = Fluids.HELIUM3;
    /** 输出流体 mB/t */
    public static final int HE3_FLUID_OUT_AMOUNT = 15;
    /** 同时产出的物品（null = 不出物品；要出就写 new ItemStack(ModItems.xxx, 1)） */
    public static final ItemStack HE3_ITEM_OUT = null;

    /* ==================== 配方 B：氢等离子体 → 氦-4 ==================== */

    /** 输入：氢等离子体 mB/t */
    public static final int HE4_PLASMA_IN = 20;
    /** 输出流体（null = 只出物品） */
    public static final FluidType HE4_FLUID_OUT = Fluids.HELIUM4;
    /** 输出流体 mB/t */
    public static final int HE4_FLUID_OUT_AMOUNT = 15;
    /** 同时产出的物品（null = 不出物品） */
    public static final ItemStack HE4_ITEM_OUT = null;

    /* ==================== 配方 C：氢-硼等离子体 → 氦-4 ==================== */

    /** 输入：氢-硼等离子体 mB/t */
    public static final int HB_PLASMA_IN = 20;
    /** 输出流体 */
    public static final FluidType HB_FLUID_OUT = Fluids.HELIUM4;
    /** 输出流体 mB/t */
    public static final int HB_FLUID_OUT_AMOUNT = 30;
    /** 加热器进料：氢 / 液态硼，各 100 mB/t（左罐氢、右罐硼） */
    public static final int HB_HYDROGEN_IN = 100;
    public static final int HB_BORON_IN = 100;
    /** 加热器每 tick 产出的氢-硼等离子体 */
    public static final int HB_PLASMA_OUT = 20;
    /** 能量消耗（HE/t） */
    public static final long HB_POWER = 300_000L;
    /** 等离子体能量输出（TU/t） */
    public static final long HB_HEAT = 75_000L;
    /** 中子通量（flux/t）：p-B11 几乎无中子 */
    public static final double HB_FLUX = 1D;
    /** 约束力量（HE/t）：仿星器储电上限 = ×100 = 3.5G */
    public static final long HB_CONFINEMENT = 35_000_000L;
    /** 启动读条 600 tick（30 s） */
    public static final int HB_STARTUP_TICKS = 600;
    /** 物品产物间隔：每 60 tick 出一个电离粒子 */
    public static final int HB_ITEM_INTERVAL = 60;
    /** 氢-硼等离子体的颜色（青绿） */
    public static final int HB_COLOR = 0x8CF0A8;

    /* ==================== 配方 D：反物质等离子体 → 反Sa326 ==================== */

    /** 输入：反物质等离子体 mB/t */
    public static final int AMAT_PLASMA_IN = 8;
    /** 输出流体：反Sa326 */
    public static final FluidType AMAT_FLUID_OUT = Fluids.ASCHRAB;
    /** 输出流体 mB/t */
    public static final int AMAT_FLUID_OUT_AMOUNT = 2;
    /** 加热器进料：反物质，单侧/双侧合计 8 mB/t（各 4 mB/t） */
    public static final int AMAT_FEED_IN = 8;
    /** 加热器每 tick 产出的反物质等离子体 */
    public static final int AMAT_PLASMA_OUT = 8;
    /** 能量消耗（HE/t） */
    public static final long AMAT_POWER = 800_000L;
    /** 等离子体能量输出（TU/t） */
    public static final long AMAT_HEAT = 120_000L;
    /** 中子通量（flux/t） */
    public static final double AMAT_FLUX = 0D;
    /** 约束力量（HE/t）：仿星器储电上限 = ×100 = 5G */
    public static final long AMAT_CONFINEMENT = 50_000_000L;
    /** 启动读条 300 tick（15 s） */
    public static final int AMAT_STARTUP_TICKS = 300;
    /** 反物质等离子体的颜色（深邃虚空紫/深靛蓝） */
    public static final int AMAT_COLOR = 0x651C9C;

    /* ==================== 配方 E：约束态反物质等离子体 → 无输出 ==================== */

    /** 输入：约束态反物质等离子体 mB/t */
    public static final int PAMAT_PLASMA_IN = 10;
    /** 加热器进料：离子凝胶 100 mB/t + 反物质 10 mB/t（左罐离子凝胶、右罐反物质） */
    public static final int PAMAT_IONGEL_IN = 100;
    public static final int PAMAT_AMAT_IN = 10;
    /** 加热器每 tick 产出的约束态反物质等离子体 */
    public static final int PAMAT_PLASMA_OUT = 10;
    /** 能量消耗（HE/t）：1.5m */
    public static final long PAMAT_POWER = 1_500_000L;
    /** 等离子体能量输出（TU/t）：450.0k */
    public static final long PAMAT_HEAT = 450_000L;
    /** 中子通量（flux/t）：200.0 */
    public static final double PAMAT_FLUX = 200D;
    /** 约束力量（HE/t）：100.0M，储电上限 = ×100 = 10G */
    public static final long PAMAT_CONFINEMENT = 100_000_000L;
    /** 启动读条 900 tick */
    public static final int PAMAT_STARTUP_TICKS = 900;
    /** 约束态反物质等离子体的颜色（品红紫） */
    public static final int PAMAT_COLOR = 0xB432C8;

    /* ==================== 配方 F：反Sa326等离子体 → 星流浆 ==================== */

    /** 输入：反Sa326等离子体 mB/t */
    public static final int ASCHRAB_PLASMA_IN = 10;
    /** 输出流体：星流浆 */
    public static final FluidType ASCHRAB_FLUID_OUT = Fluids.STELLAR_FLUX;
    /** 输出流体 mB/t */
    public static final int ASCHRAB_FLUID_OUT_AMOUNT = 5;
    /** 加热器进料：反Sa326，合计 100 mB/t（左右罐各 50 mB/t） */
    public static final int ASCHRAB_FEED_IN = 100;
    /** 加热器每 tick 产出的反Sa326等离子体 */
    public static final int ASCHRAB_PLASMA_OUT = 20;
    /** 能量消耗（HE/t）：1.0M */
    public static final long ASCHRAB_POWER = 1_000_000L;
    /** 等离子体能量输出（TU/t）：240.0k */
    public static final long ASCHRAB_HEAT = 240_000L;
    /** 中子通量（flux/t）：0.0 */
    public static final double ASCHRAB_FLUX = 0D;
    /** 约束力量（HE/t）：150.0M，储电上限 = ×100 = 15G */
    public static final long ASCHRAB_CONFINEMENT = 150_000_000L;
    /** 启动读条 600 tick */
    public static final int ASCHRAB_STARTUP_TICKS = 600;
    /** 反Sa326等离子体的颜色（高能幽蓝青） */
    public static final int ASCHRAB_COLOR = 0x28C8F0;

    /* ==================== 三条配方共用 ==================== */

    /** 氢-氢的启动读条时长：10 s */
    public static final int STARTUP_TICKS = 200;

    /** 条件不足时读条收回时长：5 s */
    public static final int SHUTDOWN_TICKS = 100;

    /** 氢-氢的输出热量（TU/t） */
    public static final long HEAT = 100_000L;

    /** 氢-氢的约束力量（HE/t）：储电上限 = ×100 = 1G */
    public static final long CONFINEMENT = 10_000_000L;

    /** 氢-氢的中子通量（flux/t） */
    public static final double FLUX = 100D;

    /** 配方的 duration 字段（宿主结构需要），界面不再显示 */
    public static final int DURATION = 100;

    /** 加热器侧进料表缓存（{@link #addRecipe()} 会清空） */
    private static final Map<String, HeaterFeed> FEEDS = new HashMap<String, HeaterFeed>();

    /**
     * 加热器侧的一条配方：逻辑输入（简介显示）+ 物理罐位（左罐/右罐）+ 输出的等离子体。
     *
     * <p>氢-氢是「一条逻辑输入 100 mB 氢」，物理上左右罐各吃 50 mB；
     * 氢-硼是「100 mB 氢 + 100 mB 液态硼」，物理上就是左罐氢、右罐硼。</p>
     */
    public static final class HeaterFeed {

        /** 逻辑输入，简介“加热效率”那一行按它显示 */
        public final FluidType[] inFluid;
        public final int[] inAmount;

        /** 物理罐位：[0] = 左罐，[1] = 右罐 */
        public final FluidType[] tankFluid;
        public final int[] tankAmount;

        /** 输出的等离子体 */
        public final FluidType outFluid;
        public final int outAmount;

        private HeaterFeed(FluidType[] inFluid, int[] inAmount,
                FluidType[] tankFluid, int[] tankAmount, FluidType outFluid, int outAmount) {
            this.inFluid = inFluid;
            this.inAmount = inAmount;
            this.tankFluid = tankFluid;
            this.tankAmount = tankAmount;
            this.outFluid = outFluid;
            this.outAmount = outAmount;
        }
    }

    /** 是不是本模组的等离子聚变配方 */
    public static boolean isOurRecipe(String recipeName) {
        return RECIPE_HE3.equals(recipeName) || RECIPE_HE4.equals(recipeName) || RECIPE_HB.equals(recipeName) || RECIPE_AMAT.equals(recipeName) || RECIPE_PAMAT.equals(recipeName) || RECIPE_ASCHRAB.equals(recipeName);
    }

    /** 兼容旧调用名 */
    public static boolean isHydrogen(String recipeName) {
        return isOurRecipe(recipeName);
    }

    /**
     * 三条配方是否都在表里、且都还是本模组的 {@link HydrogenFusionRecipe}。
     *
     * <p>宿主会把聚变表写成 json 再读回来（/ntmreload、客户端同步），读回来的是普通
     * {@code FusionRecipe}，{@code confinementPower} 字段会丢。所以这里必须连类型一起查：
     * 只要有一条退化成普通配方，就整表重注册。</p>
     */
    public static boolean isRegistered() {
        FusionRecipes table = FusionRecipes.INSTANCE;
        return table.recipeNameMap.get(RECIPE_HE3) instanceof HydrogenFusionRecipe
                && table.recipeNameMap.get(RECIPE_HE4) instanceof HydrogenFusionRecipe
                && table.recipeNameMap.get(RECIPE_HB) instanceof HydrogenFusionRecipe
                && table.recipeNameMap.get(RECIPE_AMAT) instanceof HydrogenFusionRecipe
                && table.recipeNameMap.get(RECIPE_PAMAT) instanceof HydrogenFusionRecipe
                && table.recipeNameMap.get(RECIPE_ASCHRAB) instanceof HydrogenFusionRecipe;
    }

    /** 氢等离子体类型：粉紫色的 {@code PLASMA_H}（注册完成前返回 {@code NONE}） */
    public static FluidType plasmaType() {
        FluidType type = ModFluids.PLASMA_H.type;
        return type == null ? Fluids.NONE : type;
    }

    /** 氢-硼等离子体类型：青绿的 {@code PLASMA_HB}（注册完成前返回 {@code NONE}） */
    public static FluidType hbPlasmaType() {
        FluidType type = ModFluids.PLASMA_HB.type;
        return type == null ? Fluids.NONE : type;
    }

    /** 反物质等离子体类型：深邃虚空紫/深靛蓝的 {@code PLASMA_AMAT}（注册完成前返回 {@code NONE}） */
    public static FluidType amatPlasmaType() {
        FluidType type = ModFluids.PLASMA_AMAT.type;
        return type == null ? Fluids.NONE : type;
    }

    /** 约束态反物质等离子体类型：品红紫的 {@code PLASMA_POSITRON_AMAT}（注册完成前返回 {@code NONE}） */
    public static FluidType pamatPlasmaType() {
        FluidType type = ModFluids.PLASMA_POSITRON_AMAT.type;
        return type == null ? Fluids.NONE : type;
    }

    /** 反Sa326等离子体类型：高能幽蓝青的 {@code PLASMA_ASCHRAB}（注册完成前返回 {@code NONE}） */
    public static FluidType aschrabPlasmaType() {
        FluidType type = ModFluids.PLASMA_ASCHRAB.type;
        return type == null ? Fluids.NONE : type;
    }

    /** 按配方名取该配方对应的等离子体类型 */
    public static FluidType plasmaType(String recipeName) {
        if(RECIPE_AMAT.equals(recipeName)) return amatPlasmaType();
        if(RECIPE_PAMAT.equals(recipeName)) return pamatPlasmaType();
        if(RECIPE_ASCHRAB.equals(recipeName)) return aschrabPlasmaType();
        return RECIPE_HB.equals(recipeName) ? hbPlasmaType() : plasmaType();
    }

    /** 按配方名取启动读条 tick 数（氢-硼 / 反Sa326 600，约束态反物质 900，反物质 300，其余 200） */
    public static int startupTicks(String recipeName) {
        if(RECIPE_AMAT.equals(recipeName)) return AMAT_STARTUP_TICKS;
        if(RECIPE_PAMAT.equals(recipeName)) return PAMAT_STARTUP_TICKS;
        if(RECIPE_ASCHRAB.equals(recipeName)) return ASCHRAB_STARTUP_TICKS;
        return RECIPE_HB.equals(recipeName) ? HB_STARTUP_TICKS : STARTUP_TICKS;
    }

    /** 按配方名取物品产物间隔（tick）：氢-硼 60 才出一个电离粒子，其余配方每 tick 都给 */
    public static int itemInterval(String recipeName) {
        return RECIPE_HB.equals(recipeName) ? HB_ITEM_INTERVAL : 1;
    }

    /**
     * 加热器侧进料表：不是本模组的配方返回 null。
     *
     * <p>第一次取用时按当时的流体类型构建并缓存（{@link ModFluids} 的 {@code type} 在 preInit
     * 才赋值，不能在类初始化时抓），{@link #addRecipe()} 会清缓存。</p>
     */
    public static HeaterFeed heaterFeed(String recipeName) {

        if(recipeName == null) return null;

        HeaterFeed cached = FEEDS.get(recipeName);
        if(cached != null) return cached;

        HeaterFeed feed = buildFeed(recipeName);
        if(feed != null) FEEDS.put(recipeName, feed);
        return feed;
    }

    private static HeaterFeed buildFeed(String recipeName) {

        FluidType hydrogen = Fluids.HYDROGEN;

        if(RECIPE_HE3.equals(recipeName) || RECIPE_HE4.equals(recipeName)) {
            return new HeaterFeed(
                    new FluidType[] { hydrogen }, new int[] { HEATER_HYDROGEN_IN },
                    new FluidType[] { hydrogen, hydrogen }, new int[] { HEATER_UNITS_PER_TICK, HEATER_UNITS_PER_TICK },
                    plasmaType(), HEATER_PLASMA_OUT);
        }

        if(RECIPE_HB.equals(recipeName)) {
            FluidType boron = ModFluids.LIQUID_BORON.type == null ? Fluids.NONE : ModFluids.LIQUID_BORON.type;
            return new HeaterFeed(
                    new FluidType[] { hydrogen, boron }, new int[] { HB_HYDROGEN_IN, HB_BORON_IN },
                    new FluidType[] { hydrogen, boron }, new int[] { HB_HYDROGEN_IN, HB_BORON_IN },
                    hbPlasmaType(), HB_PLASMA_OUT);
        }

        if(RECIPE_AMAT.equals(recipeName)) {
            FluidType amat = Fluids.AMAT;
            int half = AMAT_FEED_IN / 2;
            return new HeaterFeed(
                    new FluidType[] { amat }, new int[] { AMAT_FEED_IN },
                    new FluidType[] { amat, amat }, new int[] { half, half },
                    amatPlasmaType(), AMAT_PLASMA_OUT);
        }

        if(RECIPE_PAMAT.equals(recipeName)) {
            FluidType iongel = Fluids.IONGEL;
            FluidType amat = Fluids.AMAT;
            return new HeaterFeed(
                    new FluidType[] { iongel, amat }, new int[] { PAMAT_IONGEL_IN, PAMAT_AMAT_IN },
                    new FluidType[] { iongel, amat }, new int[] { PAMAT_IONGEL_IN, PAMAT_AMAT_IN },
                    pamatPlasmaType(), PAMAT_PLASMA_OUT);
        }

        if(RECIPE_ASCHRAB.equals(recipeName)) {
            FluidType aschrab = Fluids.ASCHRAB;
            int half = ASCHRAB_FEED_IN / 2;
            return new HeaterFeed(
                    new FluidType[] { aschrab }, new int[] { ASCHRAB_FEED_IN },
                    new FluidType[] { aschrab, aschrab }, new int[] { half, half },
                    aschrabPlasmaType(), ASCHRAB_PLASMA_OUT);
        }

        return null;
    }

    /**
     * 仿星器每 tick 烧掉的等离子体；0 表示不是本模组的配方。
     * 直接读配方自己的输入量，所以以后新加配方只要填 inputFluids 就能用。
     */
    public static int plasmaPerTick(FusionRecipe r) {
        if(r == null || !isOurRecipe(r.getInternalName())) return 0;
        if(r.inputFluid != null && r.inputFluid.length > 0) return r.inputFluid[0].fill;
        return 0;
    }

    /**
     * 清空整张聚变表，再只注册三条配方（氦-3 / 氦-4 / 氢-硼）。
     *
     * <p>HBM 的 {@code FusionRecipes} 在初始化时会先 {@code deleteRecipes()} 再按 json / 默认值
     * 填表，所以这里不能只在表尾追加，必须自己也清一遍。{@code nameToRecipeGlobal} 是跨表的
     * 静态查表，残留的旧名字会给蓝图池留下指向已删除配方的条目，所以按清空前的名字逐个摘掉。</p>
     *
     * <h3>新配方模板（复制这段，改数值即可）</h3>
     * <pre>
     * // ① 仿星器侧：每 tick 烧掉的等离子体（mB/t）
     * int plasmaIn = 20;
     * // ② 输出流体（不需要就写 null / 0）；③ 输出物品（不需要就写 null）
     * FluidType outFluid = Fluids.HELIUM3;  int outAmount = 15;  ItemStack outItem = null;
     * // ④ 其余参数：能量消耗、热量、中子通量、约束力量、启动 tick、颜色、图标
     * register("makentmgreatagain.fus.xxx", plasmaIn, outFluid, outAmount, outItem,
     *          100_000L, 100_000L, 100D, 10_000_000L, 200, 0xF2B4F6, icon);
     * // ⑤ 新名字要加进 isOurRecipe()
     * // ⑥ 加热器进料在 buildFeed() 里加一支：逻辑输入 + 左/右罐映射 + 输出的等离子体
     * //    氢-氢：1 条逻辑输入 100 mB 氢，物理左右罐各 50
     * //    氢-硼：2 条逻辑输入 100 mB 氢 + 100 mB 液态硼，物理左罐氢、右罐硼
     * </pre>
     */
    public static void addRecipe() {

        resetTable();
        FEEDS.clear();

        register(RECIPE_HE3, HE3_PLASMA_IN, HE3_FLUID_OUT, HE3_FLUID_OUT_AMOUNT, HE3_ITEM_OUT,
                POWER, HEAT, FLUX, CONFINEMENT, STARTUP_TICKS, 0xF2B4F6, hydrogenIcon());

        register(RECIPE_HE4, HE4_PLASMA_IN, HE4_FLUID_OUT, HE4_FLUID_OUT_AMOUNT, HE4_ITEM_OUT,
                POWER, HEAT, FLUX, CONFINEMENT, STARTUP_TICKS, 0xF2B4F6, hydrogenIcon());

        register(RECIPE_HB, HB_PLASMA_IN, HB_FLUID_OUT, HB_FLUID_OUT_AMOUNT,
                new ItemStack(ModItems.pellet_charged, 1),
                HB_POWER, HB_HEAT, HB_FLUX, HB_CONFINEMENT, HB_STARTUP_TICKS, HB_COLOR,
                new ItemStack(ModItems.pellet_charged, 1));

        register(RECIPE_AMAT, AMAT_PLASMA_IN, AMAT_FLUID_OUT, AMAT_FLUID_OUT_AMOUNT, null,
                AMAT_POWER, AMAT_HEAT, AMAT_FLUX, AMAT_CONFINEMENT, AMAT_STARTUP_TICKS, AMAT_COLOR,
                new ItemStack(ModItems.cell_antimatter, 1));

        register(RECIPE_PAMAT, PAMAT_PLASMA_IN, null, 0, null,
                PAMAT_POWER, PAMAT_HEAT, PAMAT_FLUX, PAMAT_CONFINEMENT, PAMAT_STARTUP_TICKS, PAMAT_COLOR,
                new ItemStack(ModItems.fluid_icon, 1, Fluids.IONGEL.getID()));

        register(RECIPE_ASCHRAB, ASCHRAB_PLASMA_IN, ASCHRAB_FLUID_OUT, ASCHRAB_FLUID_OUT_AMOUNT, null,
                ASCHRAB_POWER, ASCHRAB_HEAT, ASCHRAB_FLUX, ASCHRAB_CONFINEMENT, ASCHRAB_STARTUP_TICKS, ASCHRAB_COLOR,
                new ItemStack(ModItems.fluid_icon, 1, Fluids.ASCHRAB.getID()));
    }

    private static ItemStack hydrogenIcon() {
        return new ItemStack(ModItems.gas_full, 1, Fluids.HYDROGEN.getID());
    }

    /**
     * 注册一条配方：输入等离子体 {@code plasmaIn} mB/t → 输出流体 {@code outFluid}（可 null）
     * 和/或物品 {@code outItem}（可 null）。
     */
    private static void register(String name, int plasmaIn, FluidType outFluid, int outAmount, ItemStack outItem,
            long power, long heat, double flux, long confinement, int startupTicks, int color, ItemStack icon) {

        HydrogenFusionRecipe recipe = new HydrogenFusionRecipe(name)
                .setConfinementPower(confinement)
                .setStartupTicks(startupTicks);

        RecipeRegistrar.add(FusionRecipes.INSTANCE, (FusionRecipe) recipe
                .setInputEnergy(IGNITION)   // 不用速调管
                .setOutputEnergy(heat)      // TU/t，仿星器读它算蒸汽
                .setOutputFlux(flux)        // flux/t
                .setRGB(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F)
                .setNamed()
                .setIcon(icon)
                .setPower(power)            // HE/t
                .setDuration(DURATION)
                .inputFluids(new FluidStack(plasmaType(name), plasmaIn))
                .outputFluids(outFluid == null ? new FluidStack[0]
                        : new FluidStack[] { new FluidStack(outFluid, outAmount) })
                .outputItems(outItem == null ? new ItemStack[0]
                        : new ItemStack[] { outItem }));
    }

    /** 清空聚变表（含跨表静态查表里的旧条目）。 */
    private static void resetTable() {

        FusionRecipes table = FusionRecipes.INSTANCE;

        for(String name : new ArrayList<String>(table.recipeNameMap.keySet())) {
            GenericRecipes.nameToRecipeGlobal.remove(name);
        }

        table.deleteRecipes();

        // 万一 json 里还有表外的 fus.* 残留，一并清掉
        Iterator<Map.Entry<String, GenericRecipe>> it = GenericRecipes.nameToRecipeGlobal.entrySet().iterator();
        while(it.hasNext()) {
            String name = it.next().getKey();
            if(name != null && name.startsWith("fus.")) it.remove();
        }
    }
}
