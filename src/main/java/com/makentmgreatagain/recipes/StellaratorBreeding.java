package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;

import net.minecraft.item.ItemStack;

/**
 * 仿星器的增殖配方表（中子通量驱动）。
 *
 * <h3>为什么不用原版的表</h3>
 * <p>原版两条路各有一张<b>通用</b>表，和聚变没关系：</p>
 * <ul>
 *   <li>液相走 {@code FluidBreederRecipes}，里面只有 {@code GAS → SYNGAS}、
 *       {@code LIGHTOIL / LIGHTOIL_CRACK → REFORMGAS} 三条炼油厂配方；</li>
 *   <li>固相走 {@code OutgasserRecipes}，那是照射室（Outgasser）的表，内容从煤炭制合成气
 *       到蘑菇变异，同样跟聚变不搭。</li>
 * </ul>
 * <p>聚变堆里真正会被中子嬗变的只有氢同位素、锂和少数金属，所以这里单开一张表：
 * 仿星器的增殖只认本表，液相、固相都读这里，产物的流体也直接决定「增值后输出」罐里显示什么。</p>
 *
 * <h3>通量门槛</h3>
 * <p>全表通量统一在 <b>50~4000</b> 区间（{@link #clamp} 钳制）：聚变配方每 tick 给
 * {@code neutronFlux}，攒够配方要求的通量才完成一次转化。氢配方 100 /t 时，
 * 2,000 档 20 tick 转化一次，比旧版 10,000 档快 5 倍。</p>
 * <p>增殖棒链对照原版 {@code BreederRecipes} 补齐（单/双/四联，通量 ×1/×2/×3 后钳进区间）。</p>
 *
 * <h3>加配方</h3>
 * <p>在静态块里{@code fluid(...)} / {@code item(...)} 各加一行即可，不用动
 * {@code TileEntityStellarator}。物品输入用矿辞，别的模组加的同类材料也能吃。</p>
 *
 * <h3>增殖配方模板（复制改数值即可）</h3>
 * <pre>
 * // ① 液相：inputFluid 的 inputAmount mB → outputFluid 的 outputAmount mB
 * //    fluid(输入流体, 每轮用量mB, 产物流体, 每轮产量mB, 需要的中子通量);
 * //    例：
 * fluid(Fluids.DEUTERIUM, 1_000, Fluids.TRITIUM, 500, BASE_FLUX);
 *
 * // ② 固相：一件物品 → 物品和/或流体（两者可以同时给，也可以给 null 只出其中一种）
 * //    item(输入AStack, 产物ItemStack或null, 产物FluidStack或null, 需要的中子通量);
 * //    例：
 * item(new OreDictStack("dustLithium"), null, new FluidStack(Fluids.TRITIUM, 1_000), BASE_FLUX / 2);
 * item(new OreDictStack("ingotGold"), new ItemStack(ModItems.ingot_au198), null, BASE_FLUX / 2);
 * item(new OreDictStack("ingotXxx"), new ItemStack(ModItems.ingot_xxx), new FluidStack(Fluids.TRITIUM, 100), BASE_FLUX);
 *
 * // ③ 通量：仿星器运行中每 tick 收到聚变配方的 neutronFlux（本模组氢配方 = 100/t），
 * //    攒够该条配方要求的 flux 才转化一次（一次扣一轮用量、出一轮产物）；
 * //    槽 2 没有可增殖物品、罐 1 没有可增殖流体时不会积攒；
 * //    产物装不下时只暂停积攒，不清零。
 * //    想要“10 秒一次”就把 flux 填成 100 × 20 × 10 = 20_000。
 * </pre>
 */
public final class StellaratorBreeding {

    private StellaratorBreeding() { }

    /** 通量基准，与原版增殖器容量一致 */
    public static final double BASE_FLUX = 10_000D;

    /** 液相增殖配方：{@code inputAmount} mB 原料 → {@code outputAmount} mB 产物 */
    public static final class FluidRecipe {

        public final FluidType input;
        public final int inputAmount;
        public final FluidType output;
        public final int outputAmount;
        public final double flux;

        private FluidRecipe(FluidType input, int inputAmount, FluidType output, int outputAmount, double flux) {
            this.input = input;
            this.inputAmount = inputAmount;
            this.output = output;
            this.outputAmount = outputAmount;
            this.flux = flux;
        }
    }

    /**
     * 固相增殖配方：一件原料 → 一个物品和/或一罐流体。
     * 两个产出可以同时存在（例如锂既出氚气又不出固体，金只出固体）。
     */
    public static final class ItemRecipe {

        public final AStack input;
        public final ItemStack output;
        public final FluidStack fluidOutput;
        public final double flux;

        private ItemRecipe(AStack input, ItemStack output, FluidStack fluidOutput, double flux) {
            this.input = input;
            this.output = output;
            this.fluidOutput = fluidOutput;
            this.flux = flux;
        }
    }

    public static final List<FluidRecipe> FLUID = new ArrayList<FluidRecipe>();
    public static final List<ItemRecipe> ITEM = new ArrayList<ItemRecipe>();

    static {

        /* ---------- 液相：氢同位素链 ---------- */

        // H + n → D + γ。慢，而且要先有氢；但这是把普通氢变成聚变燃料的唯一入口
        fluid(Fluids.HYDROGEN, 1_000, Fluids.DEUTERIUM, 200, 2_000);

        // D + n → T + p。聚变堆最经典的增殖反应，dt 配方缺氚就靠它
        fluid(Fluids.DEUTERIUM, 1_000, Fluids.TRITIUM, 500, 2_000);

        // He3 + n → T + p。氦-3 配方（h3）自带氚产出，这里再补一条增殖路线
        fluid(Fluids.HELIUM3, 1_000, Fluids.TRITIUM, 750, 2_000);

        // He4 + n → He3 + 2n。需要快中子，通量门槛翻倍，产量也低
        fluid(Fluids.HELIUM4, 1_000, Fluids.HELIUM3, 250, 4_000);

        /* ---------- 固相：锂 → 氚 ---------- */

        item(new OreDictStack("dustLithium"), null, new FluidStack(Fluids.TRITIUM, 1_000), 1_000);
        item(new OreDictStack("dustTinyLithium"), null, new FluidStack(Fluids.TRITIUM, 100), 100);
        item(new OreDictStack("ingotLithium"), null, new FluidStack(Fluids.TRITIUM, 1_000), 1_000);
        item(new OreDictStack("blockLithium"), null, new FluidStack(Fluids.TRITIUM, 9_000), 4_000);

        /* ---------- 固相：金属嬗变 ---------- */

        // 金 → 金-198（Au-197 俘获中子）
        item(new OreDictStack("ingotGold"), new ItemStack(ModItems.ingot_au198), null, 1_000);
        item(new OreDictStack("nuggetGold"), new ItemStack(ModItems.nugget_au198), null, 1_000);
        item(new OreDictStack("dustGold"), new ItemStack(ModItems.powder_au198), null, 1_000);

        /* ---------- 固相：增殖棒链（对照原版 BreederRecipes 补齐） ---------- */

        // 锂棒→氚棒与上面的锂粉/锭路线并存：原版增殖器有的，仿星器也要有
        rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType.LITHIUM, com.hbm.items.machine.ItemBreedingRod.BreedingRodType.TRITIUM, 400);
        rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType.CO, com.hbm.items.machine.ItemBreedingRod.BreedingRodType.CO60, 200);
        rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType.RA226, com.hbm.items.machine.ItemBreedingRod.BreedingRodType.AC227, 600);
        rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType.TH232, com.hbm.items.machine.ItemBreedingRod.BreedingRodType.THF, 1_000);
        rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType.U235, com.hbm.items.machine.ItemBreedingRod.BreedingRodType.NP237, 600);
        rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType.NP237, com.hbm.items.machine.ItemBreedingRod.BreedingRodType.PU238, 400);
        rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType.PU238, com.hbm.items.machine.ItemBreedingRod.BreedingRodType.PU239, 2_000);
        rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType.U238, com.hbm.items.machine.ItemBreedingRod.BreedingRodType.RGP, 600);
        rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType.URANIUM, com.hbm.items.machine.ItemBreedingRod.BreedingRodType.RGP, 400);
        rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType.RGP, com.hbm.items.machine.ItemBreedingRod.BreedingRodType.WASTE, 400);

        // 陨石剑照射（原版增殖器彩蛋配方，仿星器同样补上）
        item(new com.hbm.inventory.RecipesCommon.ComparableStack(ModItems.meteorite_sword_etched),
                new ItemStack(ModItems.meteorite_sword_bred), null, 2_000);

        /* ---------- 半科幻：稳定岛 / 顶级材料（跳过昂贵粒子链） ---------- */

        // 铀-238 → Sa326：lore「在 Sa326 嬗变器中由铀制成」，中子直产跳过 higgs 粒子链
        item(new OreDictStack(com.hbm.inventory.OreDictManager.U238.ingot(), 1),
                new ItemStack(ModItems.ingot_schrabidium), null, 3_000);

        // 钚 → Ep：中子直产跳过 dark 粒子两级链
        item(new OreDictStack(com.hbm.inventory.OreDictManager.PU.ingot(), 1),
                new ItemStack(ModItems.ingot_euphemium), null, 3_500);

        // Sa酸锭 → 双聚中子态素：中子俘获聚合，跳过 sparkticle 五级粒子链
        item(new OreDictStack(com.hbm.inventory.OreDictManager.SBD.ingot(), 1),
                new ItemStack(ModItems.ingot_dineutronium), null, 3_000);

        // 反Sa326 → 星流浆：液相，ICF 独产之外的中子凝聚补充
        fluid(Fluids.ASCHRAB, 1_000, Fluids.STELLAR_FLUX, 250, 2_500);
    }

    /** 增殖棒单/双/四联注册，通量按原版 ×1/×2/×3 缩放后钳进 50~4000 */
    private static void rod(com.hbm.items.machine.ItemBreedingRod.BreedingRodType in,
            com.hbm.items.machine.ItemBreedingRod.BreedingRodType out, double flux) {
        item(new com.hbm.inventory.RecipesCommon.ComparableStack(new ItemStack(ModItems.rod, 1, in.ordinal())),
                new ItemStack(ModItems.rod, 1, out.ordinal()), null, clamp(flux));
        item(new com.hbm.inventory.RecipesCommon.ComparableStack(new ItemStack(ModItems.rod_dual, 1, in.ordinal())),
                new ItemStack(ModItems.rod_dual, 1, out.ordinal()), null, clamp(flux * 2));
        item(new com.hbm.inventory.RecipesCommon.ComparableStack(new ItemStack(ModItems.rod_quad, 1, in.ordinal())),
                new ItemStack(ModItems.rod_quad, 1, out.ordinal()), null, clamp(flux * 3));
    }

    /** 全表通量统一钳在 50~4000 */
    private static double clamp(double flux) {
        return Math.max(50D, Math.min(4_000D, flux));
    }

    private static void fluid(FluidType input, int inputAmount, FluidType output, int outputAmount, double flux) {
        FLUID.add(new FluidRecipe(input, inputAmount, output, outputAmount, flux));
    }

    private static void item(AStack input, ItemStack output, FluidStack fluidOutput, double flux) {
        ITEM.add(new ItemRecipe(input, output, fluidOutput, flux));
    }

    /** 按输入流体找液相配方；{@code null} / {@code NONE} 一律返回 {@code null} */
    public static FluidRecipe getFluidRecipe(FluidType input) {

        if(input == null || input == Fluids.NONE) return null;

        for(FluidRecipe recipe : FLUID) {
            if(recipe.input == input) return recipe;
        }

        return null;
    }

    /** 按输入流体找液相产物类型，供「增值后输出」罐换型用 */
    public static FluidType getFluidProduct(FluidType input) {
        FluidRecipe recipe = getFluidRecipe(input);
        return recipe == null ? null : recipe.output;
    }

    /** 按物品找固相配方；同一件物品只会命中第一条 */
    public static ItemRecipe getItemRecipe(ItemStack input) {

        if(input == null) return null;

        for(ItemRecipe recipe : ITEM) {
            if(recipe.input.matchesRecipe(input, true)) return recipe;
        }

        return null;
    }

    /** 该物品能不能塞进增殖槽（管道 / 容器判准入用） */
    public static boolean isBreedable(ItemStack input) {
        return getItemRecipe(input) != null;
    }
}
