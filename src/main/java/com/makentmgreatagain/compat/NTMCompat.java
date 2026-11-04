package com.makentmgreatagain.compat;

import com.hbm.inventory.recipes.BlastFurnaceRecipes;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.OreDictManager;
import com.hbm.inventory.RecipesCommon;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.ChemicalPlantRecipes;
import com.hbm.inventory.recipes.CrystallizerRecipes;
import com.hbm.inventory.recipes.CrystallizerRecipes.CrystallizerRecipe;
import com.hbm.inventory.recipes.PressRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCircuit;
import com.hbm.items.machine.ItemStamp;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.init.ModFluids;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * 接入 HBM 机器配方的兼容层。
 *
 * 这个类必须在 postInit 阶段调用，原因：HBM 的配方表是在它自己的加载流程里
 * 逐步建立的，postInit 时它才完整。如果在 preInit 就调用，会拿到 null 对象。
 *
 * 已核实的 HBM API（都在 F:\HBM-Source 源码里查证过）：
 *   PressRecipes.makeRecipe(StampType, AStack, ItemStack)   压机（冲压机）配方
 *   ItemStamp.StampType.FLAT                                平冲压模具
 *   ModItems.ingot_graphite                                 HBM 的石墨锭
 *   ChemicalPlantRecipes.INSTANCE.register(GenericRecipe)   化工厂配方
 *   Fluids.WATER                                            HBM 的水流体
 */
public final class NTMCompat {

    /** 工具类，不需要被实例化。 */
    private NTMCompat() {
    }

    /** 由 CommonProxy.postInit 调用，注册全部与 HBM 相关的配方。 */
    public static void init() {
        addGraphiteDiamondRecipe();
        addCmbElectrodeRecipe();
        addMethChainRecipes();

        MakeNTMGreatAgain.LOG.info("NTMCompat: HBM-related recipes registered.");
    }

    /**
     * 甲基苯丙胺产线（5 道工序）：
     *
     *   1. 化工厂      甲醇 + 氨 + 铝催化      → 甲胺
     *   2. 化工厂      芳烃 + 不饱和烃 + 氧 + 钴催化 → 苯基丙酮
     *   3. 化工厂      苯基丙酮 + 甲胺 + 氢 + 钴催化 → 甲基苯丙胺粗品
     *   4. 结晶器      粗品 + 溶剂               → 甲基苯丙胺晶体
     *   5. 装配机      晶体 + 糖                 → (2R)-N-甲基-1-苯基丙-2-胺药片
     */
    private static void addMethChainRecipes() {

        ItemStack impure = new ItemStack(com.makentmgreatagain.init.ModItems.meth_impure, 2);
        ItemStack crystal = new ItemStack(com.makentmgreatagain.init.ModItems.meth_crystal);
        ItemStack pill = new ItemStack(com.makentmgreatagain.init.ModItems.meth_pill, 4);

        // 1) 甲胺：甲醇氨化
        ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.methylamine")
                .setup(100, 500L)
                .setIcon(ModItems.fluid_icon, ModFluids.METHYLAMINE.type.getID())
                .inputFluids(new FluidStack[] {
                        ModFluids.METHANOL.stack(1000),
                        ModFluids.AMMONIA.stack(1000)
                })
                .inputItems(new RecipesCommon.AStack[] {
                        new RecipesCommon.OreDictStack(OreDictManager.AL.dust(), 1)
                })
                .outputFluids(new FluidStack[] {
                        ModFluids.METHYLAMINE.stack(1000),
                        new FluidStack(Fluids.WATER, 500)
                })
        );

        // 2) 苯基丙酮：苯 + 丙烯的烷基化/氧化（简化为一步）
        ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.phenylacetone")
                .setup(200, 800L)
                .setIcon(ModItems.fluid_icon, ModFluids.PHENYLACETONE.type.getID())
                .inputFluids(new FluidStack[] {
                        new FluidStack(Fluids.AROMATICS, 1000),
                        new FluidStack(Fluids.UNSATURATEDS, 1000),
                        new FluidStack(Fluids.OXYGEN, 500)
                })
                .inputItems(new RecipesCommon.AStack[] {
                        new RecipesCommon.OreDictStack(OreDictManager.CO.dust(), 1)
                })
                .outputFluids(new FluidStack[] {
                        ModFluids.PHENYLACETONE.stack(1000),
                        new FluidStack(Fluids.WATER, 500)
                })
        );

        // 3) 还原胺化：得到未提纯的晶体
        ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.meth_impure")
                .setup(300, 1200L)
                .setIcon(new ItemStack(com.makentmgreatagain.init.ModItems.meth_impure))
                .inputFluids(new FluidStack[] {
                        ModFluids.PHENYLACETONE.stack(1000),
                        ModFluids.METHYLAMINE.stack(1000),
                        new FluidStack(Fluids.HYDROGEN, 1000)
                })
                .inputItems(new RecipesCommon.AStack[] {
                        new RecipesCommon.OreDictStack(OreDictManager.CO.dust(), 1)
                })
                .outputItems(new ItemStack[] { impure })
                .outputFluids(new FluidStack[] {
                        new FluidStack(Fluids.WATER, 500)
                })
        );

        // 4) 溶剂重结晶
        CrystallizerRecipes.registerRecipe(
                new RecipesCommon.ComparableStack(com.makentmgreatagain.init.ModItems.meth_impure),
                new CrystallizerRecipe(crystal, 200).setReq(1),
                new FluidStack(Fluids.SOLVENT, 500)
        );

        // 5) 加糖压片
        AssemblyMachineRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.meth_pill")
                .setup(100, 500L)
                .setIcon(pill)
                .inputItems(new RecipesCommon.AStack[] {
                        new RecipesCommon.ComparableStack(com.makentmgreatagain.init.ModItems.meth_crystal, 2),
                        new RecipesCommon.ComparableStack(Items.sugar, 1)
                })
                .outputItems(new ItemStack[] { pill })
        );

        MakeNTMGreatAgain.LOG.info("Added methamphetamine production chain (5 steps).");
    }
    private static void addCmbElectrodeRecipe() {
        GameRegistry.addShapedRecipe(
                new ItemStack(com.makentmgreatagain.init.ModItems.cmb_electrode, 1, 0),
                "S",
                "D",
                "S",
                'S', new ItemStack(ModItems.ingot_combine_steel),
                'D', new ItemStack(ModItems.ingot_dineutronium)
        );

        MakeNTMGreatAgain.LOG.info("Added shaped recipe: CMB Electrode");
    }

    /**
     * 压机配方：石墨锭 --(FLAT 冲压)--> 钻石。
     *
     * 三个参数依次是：使用哪种模具、输入什么、产出什么。
     * ComparableStack 是 HBM 对 ItemStack 的包装，用来做配方匹配。
     */
    private static void addGraphiteDiamondRecipe() {
        PressRecipes.makeRecipe(
                ItemStamp.StampType.FLAT,
                new RecipesCommon.ComparableStack(ModItems.ingot_graphite),
                new ItemStack(Items.diamond)
        );

        MakeNTMGreatAgain.LOG.info("Added press recipe: graphite ingot -> diamond");
    }

    /**
     * 取 HBM 的「硅晶圆」物品。
     *
     * HBM 用一个 circuit 物品 + 元数据表示多种电路，元数据值来自
     * ItemCircuit.EnumCircuitType 枚举的序号（ordinal）。
     * 这样写比直接填数字更安全：HBM 调整枚举顺序时这里不会静默出错。
     */
    public static ItemStack getPrintedSiliconWafer() {
        return new ItemStack(ModItems.circuit, 1, ItemCircuit.EnumCircuitType.SILICON.ordinal());
    }

    /**
     * 化工厂配方注册的通用封装（当前未被调用，作为备用工具方法保留）。
     */
    private static void registerChemplantRecipe(String recipeName, ItemStack dust, ItemStack output) {
        if (dust == null || output == null) {
            MakeNTMGreatAgain.LOG.warn("Skipped chemical plant recipe (null input/output): {}", recipeName);
            return;
        }

        ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe(recipeName)
                .setupNamed(300, 100L)
                .inputItems(new RecipesCommon.AStack[] {
                        new RecipesCommon.ComparableStack(dust),
                        new RecipesCommon.ComparableStack((Block) Blocks.sand)
                })
                .inputFluids(new FluidStack[] {
                        new FluidStack(Fluids.WATER, 1000)
                })
                .outputItems(new ItemStack[] { output })
        );

        MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: {}", recipeName);
    }
}
