package com.makentmgreatagain.recipes;

import net.minecraft.item.ItemStack;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.ChemicalPlantRecipes;
import com.hbm.inventory.recipes.PyroOvenRecipes;
import com.hbm.inventory.recipes.ShredderRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.init.ModFluids;
import com.makentmgreatagain.init.ModItems;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 白钨矿湿法链的全部配方（L0–L4 + 浸出渣三级处理）。
 *
 * <h3>这条链和 HBM 原版钨矿的根本区别</h3>
 * <p>原版钨矿 {@code SmeltingRecipes:38} 一行就完事：矿丢熔炉，出一个钨锭，零加工链。
 * 白钨矿<b>故意没有熔炼配方</b>——丢进熔炉什么都不发生。它必须先粉碎成粉，
 * 再走碱浸、酸化、焙烧、氢还原四道工序。挖出来不能烧，是这条链的设计前提，
 * 也是"难处理钨矿"这四个字的落点。</p>
 *
 * <h3>为什么是批量配方而不是概率产出</h3>
 * <p>目标是「1 方块矿 = 1.6 锭当量」。Minecraft 出不了 0.6 个锭，
 * 与其用 60% 概率糊过去（玩家会觉得产出不稳定、也没法算账），
 * 不如做成 <b>5 份料进、4 份产品出</b> 的批量配方。化工厂的
 * {@code GenericRecipe.inputItems/inputFluids} 本身就是变长参数，
 * 批量配方在 HBM 里也很常见（2000 mB 这种就是），操作上不别扭。</p>
 *
 * <table border="1">
 *   <tr><th>步</th><th>机器</th><th>进</th><th>出</th></tr>
 *   <tr><td>L0</td><td>粉碎机</td><td>1 白钨矿</td><td>2 白钨矿粉</td></tr>
 *   <tr><td>L1</td><td>化工厂</td><td>1 粉 + 500 碱液 + 500 水</td><td>500 钨酸钠液 + 1 浸出渣</td></tr>
 *   <tr><td>L1′</td><td>化工厂</td><td>1 粉 + 500 碱液 + 500 废酸</td><td>500 钨酸钠液 + 1 浸出渣</td></tr>
 *   <tr><td>L2</td><td>化工厂</td><td>2500 液 + 750 硫酸</td><td>4 钨酸 + 2500 废酸</td></tr>
 *   <tr><td>L3</td><td>热解炉</td><td>4 钨酸</td><td>4 三氧化钨 + 2000 蒸汽</td></tr>
 *   <tr><td>L4</td><td>热解炉</td><td>4 三氧化钨 + 4000 氢</td><td>4 钨粉 + 4000 蒸汽</td></tr>
 *   <tr><td>L5</td><td>熔炉</td><td>1 钨粉</td><td>1 钨锭（HBM 自带，不重复注册）</td></tr>
 * </table>
 *
 * <h3>废酸闭环（这条链能不能持续跑的关键）</h3>
 * <pre>
 *   L1 做 5 次：5 粉 + 2500 碱液 + 2500 水 → 2500 钨酸钠液 + 5 浸出渣
 *   L2 做 1 次：2500 液 + 750 硫酸        → 4 钨酸 + 2500 废酸
 *   2500 废酸正好顶回上面 5 次 L1 的 2500 水 —— 净投入只有硫酸和碱液，没有废液外排。
 * </pre>
 * <p>因此 L1 必须<b>同时</b>存在"水版"和"废酸版"两条配方。只给废酸版，
 * 第一次玩的人没有废酸就永远起不了步；只给水版，废酸会越积越多最后堵死。</p>
 *
 * <h3>浸出渣的三级处理</h3>
 * <p>同一堆渣，科技进度不同能拿走的东西也不同：</p>
 * <ul>
 *   <li>R1 基础水洗（无门槛）：32 渣 → 1 铍精矿 + 31 砷渣</li>
 *   <li>R2 轻质芳烃萃取（重整机 + 分馏塔，三期）：16 渣 → 1 铍 + 1 锆 + 15 砷渣</li>
 *   <li>R3 氧化焙烧（热解炉）：32 砷渣 + 4000 氧 → 1 白砷</li>
 * </ul>
 * <p>R2 完全替代 R1，不是叠加。砷全程锁在砷渣里，绝不跟着精矿走——
 * 否则玩家把含砷精矿丢熔炉，砷就凭空消失了，"二期前拿不到砷"的设计会被直接绕过。</p>
 */
public final class ScheeliteRecipes {

    /**
     * 化工厂功率基准（HE/tick）。
     *
     * <p>湿法段刻意做得比碳化钨那种高温配方便宜一个数量级：
     * 碳化钨是 100,000 HE/t，这里 500~1200。理由是白钨矿的难点在"工序多"
     * 而不是"单步耗电"，能耗压力应该体现在建四台不同的机器上，
     * 而不是卡在某一步电费上让玩家去堆核电。</p>
     */
    private static final long POWER_LEACH   = 500L;    // 碱浸：常压、60~90°C
    private static final long POWER_ACID    = 800L;    // 酸化沉钨：要控 pH，稍贵
    private static final long POWER_WASH    = 400L;    // 渣水洗：最便宜的一道
    private static final long POWER_EXTRACT = 1_200L;  // 芳烃萃取：三期工艺，最贵

    private ScheeliteRecipes() {
    }

    // =====================================================================================
    //  L0　粉碎
    // =====================================================================================

    /**
     * 1 白钨矿 → 2 白钨矿粉。
     *
     * <p>为什么要 2 份：一是补偿后面五道工序的折腾，二是"矿石解离"这件事本身
     * 在现实里就是把一块矿磨成粉、让钨矿物从脉石里单体解离出来，产出量本来就大于进料块数。
     * 三是让 1 方块 = 1.6 锭这个折算能算得整齐（2 粉 → 1.6 锭）。</p>
     */
    public static void addShredderRecipes() {
        ShredderRecipes.setRecipe(ModBlocks.ore_scheelite, new ItemStack(ModItems.powder_scheelite, 2));
        MakeNTMGreatAgain.LOG.info("[白钨矿] 粉碎机：1 白钨矿 → 2 白钨矿粉");
    }

    // =====================================================================================
    //  L1–L2　碱浸与酸化（化工厂）
    // =====================================================================================

    /** 化工厂段：碱浸（水版 / 废酸版）+ 酸化沉钨。 */
    public static void addChemicalPlantRecipes() {

        // ---- L1 碱浸（水版）：起步用 ----
        // 白钨矿在碱里被分解成可溶的钨酸钠，钙变成碳酸钙进渣。
        // 这一步是整条链的分水岭：原版钨矿到这一步之前就该出锭了，白钨矿才刚开头。
        if(!has(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.scheelite.leachWater")) {
            ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.scheelite.leachWater")
                .setup(200, POWER_LEACH)
                .inputItems(new ComparableStack(ModItems.powder_scheelite, 1))
                .inputFluids(new FluidStack(Fluids.LYE, 500), new FluidStack(Fluids.WATER, 500))
                .outputItems(new ItemStack(ModItems.scheelite_leach_residue, 1))
                .outputFluids(new FluidStack(ModFluids.SODIUM_TUNGSTATE.type, 500)));
        }

        // ---- L1′ 碱浸（废酸版）：闭环用 ----
        // 与上面完全同构，只是把 500 水换成 500 废酸。废酸里剩下的游离酸
        // 会被碱中和掉，顺便省一点碱液的实际消耗感（数值上不体现，靠玩家自己体会）。
        // 【必须两条都在】只留这条，第一次玩的人没有废酸就永远起不了步。
        if(!has(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.scheelite.leachWasteAcid")) {
            ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.scheelite.leachWasteAcid")
                .setup(200, POWER_LEACH)
                .inputItems(new ComparableStack(ModItems.powder_scheelite, 1))
                .inputFluids(new FluidStack(Fluids.LYE, 500), new FluidStack(ModFluids.WASTE_ACID.type, 500))
                .outputItems(new ItemStack(ModItems.scheelite_leach_residue, 1))
                .outputFluids(new FluidStack(ModFluids.SODIUM_TUNGSTATE.type, 500)));
        }

        // ---- L2 酸化沉钨：2500 液 + 750 硫酸 → 4 钨酸 + 2500 废酸 ----
        // 5:4 的批量比例就是"1 方块 = 1.6 锭"的实现方式：
        // 2500 液 = 5 次 L1 = 5 份粉，出 4 份钨酸 → 4 锭。5 份粉 = 2.5 方块矿 → 4 锭 = 1.6 锭/方块。
        // 产出的 2500 废酸正好补回那 5 次 L1 用的 2500 水，体积严格守恒。
        if(!has(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.scheelite.precipitate")) {
            ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.scheelite.precipitate")
                .setup(240, POWER_ACID)
                .inputFluids(new FluidStack(ModFluids.SODIUM_TUNGSTATE.type, 2_500),
                             new FluidStack(Fluids.SULFURIC_ACID, 750))
                .outputItems(new ItemStack(ModItems.tungstic_acid, 4))
                .outputFluids(new FluidStack(ModFluids.WASTE_ACID.type, 2_500)));
        }

        // ---- R1 浸出渣基础水洗（无科技门槛）----
        // 32 渣才换 1 个铍精矿，比例刻意做得很差：这是"只能挑出一点东西"的阶段。
        // 顺带把渣里残余的钨酸钠洗出来（500 mB），让玩家不至于觉得这一步纯亏。
        if(!has(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.scheelite.residueWash")) {
            ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.scheelite.residueWash")
                .setup(600, POWER_WASH)
                .inputItems(new ComparableStack(ModItems.scheelite_leach_residue, 32))
                .inputFluids(new FluidStack(Fluids.LYE, 250), new FluidStack(Fluids.WATER, 500))
                .outputItems(new ItemStack(ModItems.beryllium_concentrate, 1),
                             new ItemStack(ModItems.arsenic_cake, 31))
                .outputFluids(new FluidStack(ModFluids.SODIUM_TUNGSTATE.type, 500)));
        }

        // ---- R2 浸出渣轻质芳烃萃取（三期：重整机 + 分馏塔）----
        // 锆英石极其稳定，普通酸碱都不动它，只有强碱熔融（L1 已经做了）加上
        // 有机相萃取才能把锆从溶液里分离出来 —— 现实里用的就是 TBP + 芳烃稀释剂，
        // 对应的就是轻质芳烃（BTX，Fluids.XYLENE）。铍的收率同时翻倍（32→16 渣/个）。
        // 1000 进 600 出：损耗 40%。它同时是光气和 CFT 的原料，这个损耗是有意做的取舍点。
        if(!has(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.scheelite.residueExtract")) {
            ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.scheelite.residueExtract")
                .setup(400, POWER_EXTRACT)
                .inputItems(new ComparableStack(ModItems.scheelite_leach_residue, 16))
                .inputFluids(new FluidStack(Fluids.XYLENE, 1_000))
                .outputItems(new ItemStack(ModItems.beryllium_concentrate, 1),
                             new ItemStack(ModItems.zirconium_concentrate, 1),
                             new ItemStack(ModItems.arsenic_cake, 15))
                .outputFluids(new FluidStack(ModFluids.SODIUM_TUNGSTATE.type, 1_000),
                              new FluidStack(Fluids.XYLENE, 600)));
        }

        MakeNTMGreatAgain.LOG.info("[白钨矿] 化工厂：碱浸 ×2（水/废酸）、酸化沉钨、渣水洗、渣萃取");
    }

    // =====================================================================================
    //  L3–L4 + R3　焙烧与还原（热解炉）
    // =====================================================================================

    /**
     * 热解炉段：钨酸焙烧、三氧化钨氢还原、砷渣氧化焙烧。
     *
     * <p>为什么是热解炉而不是回转炉：{@code RotaryFurnaceRecipe.output} 的类型是
     * {@code MaterialStack}，只能出熔融金属，做不了"固体进、固体出"的焙烧。
     * 热解炉的 {@code PyroOvenRecipe} 是 物品+流体 进、物品+流体 出，
     * HBM 自己就用它焙烧基岩矿（bedrock ore → ROASTED），语义完全对得上。</p>
     *
     * <p>它的槽位硬约束：{@code inputFluid / inputItem / outputFluid / outputItem}
     * 各一个，任意一侧可以留空。所以下面三条都不能出现"两个物品进"或"两个物品出"。</p>
     */
    public static void addPyroOvenRecipes() {

        // ---- L3 钨酸焙烧：4 钨酸 → 4 三氧化钨 + 2000 蒸汽 ----
        // H2WO4 → WO3 + H2O。水以蒸汽形式排出，不是凭空消失。
        // 这一步之后产物颜色从土黄跳到亮黄绿，是整条链视觉上最明显的一次变化。
        PyroOvenRecipes.recipes.add(new PyroOvenRecipes.PyroOvenRecipe(200)
            .in(new ComparableStack(ModItems.tungstic_acid, 4))
            .out(new ItemStack(ModItems.tungsten_trioxide, 4))
            .out(new FluidStack(Fluids.STEAM, 2_000)));

        // ---- L4 氢还原：4 三氧化钨 + 4000 氢 → 4 钨粉 + 4000 蒸汽 ----
        // WO3 + 3H2 → W + 3H2O（配平按 1:3，但这里给 1:4，
        // 留一份过量氢当作流化/保护气，玩家不至于因为差一点点氢就卡住）。
        // 产物直接用 HBM 的 powder_tungsten —— 于是 SmeltingRecipes:82 那条
        // "钨粉 → 钨锭"自动就对白钨矿生效了，L5 不需要再注册任何东西。
        PyroOvenRecipes.recipes.add(new PyroOvenRecipes.PyroOvenRecipe(300)
            .in(new ComparableStack(ModItems.tungsten_trioxide, 4))
            .in(new FluidStack(Fluids.HYDROGEN, 4_000))
            .out(new ItemStack(com.hbm.items.ModItems.powder_tungsten, 4))
            .out(new FluidStack(Fluids.STEAM, 4_000)));

        // ---- R3 砷渣氧化焙烧：32 砷渣 + 4000 氧 → 1 白砷 ----
        // 要的是三氧化二砷（白砷），所以必须氧化焙烧；用碳还原只会得到单质砷。
        // 这既符合化学，也刚好绕开热解炉"不能两个物品输入"的槽位限制。
        // 产量刻意压得很低：一条 500 块的矿脉总共也就换三十来个白砷，
        // 它是有毒的副产物，不该变成主要收益。
        PyroOvenRecipes.recipes.add(new PyroOvenRecipes.PyroOvenRecipe(300)
            .in(new ComparableStack(ModItems.arsenic_cake, 32))
            .in(new FluidStack(Fluids.OXYGEN, 4_000))
            .out(new ItemStack(ModItems.arsenic_trioxide, 1)));

        MakeNTMGreatAgain.LOG.info("[白钨矿] 热解炉：钨酸焙烧、WO3 氢还原、砷渣氧化焙烧");
    }

    // =====================================================================================
    //  熔炉：精矿 → 锭
    // =====================================================================================

    /**
     * 铍精矿 / 锆精矿 一比一出锭。
     *
     * <p>直接对齐 HBM 自己的铍矿、锆矿处理方式（矿石进熔炉出一个锭），
     * 不再给精矿设计额外的电解或铝热还原——那会把铍和锆这两条线的成本
     * 结构搞乱，而它们本来只是白钨矿的副产品，不该比主产钨还麻烦。</p>
     *
     * <p>这个方法要在 preInit 阶段调一次（{@code GameRegistry.addSmelting}
     * 不依赖配方表加载顺序）。</p>
     */
    public static void addFurnaceRecipes() {
        GameRegistry.addSmelting(ModItems.beryllium_concentrate,
                new ItemStack(com.hbm.items.ModItems.ingot_beryllium, 1), 1.0F);
        GameRegistry.addSmelting(ModItems.zirconium_concentrate,
                new ItemStack(com.hbm.items.ModItems.ingot_zirconium, 1), 1.0F);

        // ---- 白钨矿：烧成「没有东西」----
        // 这条配方存在的意义不是产出，是「解释」。白钨矿本来没有熔炼配方，
        // 玩家丢进熔炉会看到矿石原封不动地退回来——那看起来像 bug。
        // 所以给它一条模具配方，让熔炉真的把它消耗掉，吐出一个 HBM 的 nothing，
        // 玩家看到「没有东西」这四个字就明白了：这矿确实烧不出东西。
        // 顺带解锁「滚木？」那条成就（AchHandler 扫到 nothing 即解锁）。
        if (com.hbm.items.ModItems.nothing != null) {
            GameRegistry.addSmelting(ModBlocks.ore_scheelite,
                    new ItemStack(com.hbm.items.ModItems.nothing, 1), 0.0F);
        }

        MakeNTMGreatAgain.LOG.info("[白钨矿] 熔炉：铍精矿/锆精矿 → 铍锭/锆锭（1:1）；白钨矿 → 没有东西");
    }

    /** 与 {@code ModRecipes.hasGenericRecipe} 同一套判断，配方表里已有同名的就跳过。 */
    private static boolean has(GenericRecipes<?> holder, String name) {
        return holder.recipeNameMap.containsKey(name);
    }
}
