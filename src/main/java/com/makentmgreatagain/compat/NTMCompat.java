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
import com.makentmgreatagain.recipes.RecipeRegistrar;
import com.makentmgreatagain.init.ModFluids;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * 接入 HBM 机器配方的兼容层，只能在 postInit 调用（preInit 时 HBM 的配方表还是 null）。
 *
 * <p>常用 API（源码已核实）：</p>
 * <pre>
 *   RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, GenericRecipe)    化工厂（已判重）
 *   RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, GenericRecipe)  装配机（已判重）
 *   CrystallizerRecipes.registerRecipe(AStack, CrystallizerRecipe, FluidStack)  结晶器
 *   PressRecipes.makeRecipe(ItemStamp.StampType.FLAT, AStack, ItemStack)        压机
 *   GameRegistry.addShapedRecipe(输出, "图案", 键值对...)                      工作台
 * </pre>
 * <p>GenericRecipe 链：.setup(时长, HE/t) .inputItems(AStack...) .inputFluids(FluidStack...)
 * .outputItems(ItemStack...) .outputFluids(FluidStack...)，需要自定义名字时加 .setupNamed()。</p>
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

    /** 甲胺、苯基丙酮、甲基苯丙胺粗品、甲基苯丙胺晶体、药片 */
    private static void addMethChainRecipes() {

        ItemStack impure = new ItemStack(com.makentmgreatagain.init.ModItems.meth_impure, 2);
        ItemStack crystal = new ItemStack(com.makentmgreatagain.init.ModItems.meth_crystal);
        ItemStack pill = new ItemStack(com.makentmgreatagain.init.ModItems.meth_pill, 4);

        // 甲胺、水
        RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.methylamine")
                .setup(100, 500L)
                .setIcon(ModItems.fluid_icon, ModFluids.METHYLAMINE.type.getID())
                .inputFluids(new FluidStack[] {
                        ModFluids.METHANOL.stack(2000),
                        ModFluids.AMMONIA.stack(2000)
                })
                .inputItems(new RecipesCommon.AStack[] {
                        new RecipesCommon.OreDictStack(OreDictManager.AL.dust(), 1)
                })
                .outputFluids(new FluidStack[] {
                        ModFluids.METHYLAMINE.stack(3500),
                        new FluidStack(Fluids.WATER, 500)
                })
        );

        // 苯基丙酮、水
        RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.phenylacetone")
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

        // 甲基苯丙胺粗品、水
        RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.meth_impure")
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

        // 甲基苯丙胺晶体
        CrystallizerRecipes.registerRecipe(
                new RecipesCommon.ComparableStack(com.makentmgreatagain.init.ModItems.meth_impure),
                new CrystallizerRecipe(crystal, 200).setReq(1),
                new FluidStack(Fluids.SOLVENT, 500)
        );

        // 药片
        RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.meth_pill")
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
    /** CMB 电极 */
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

    /** 钻石 */
    private static void addGraphiteDiamondRecipe() {
        PressRecipes.makeRecipe(
                ItemStamp.StampType.FLAT,
                new RecipesCommon.ComparableStack(ModItems.ingot_graphite),
                new ItemStack(Items.diamond)
        );

        MakeNTMGreatAgain.LOG.info("Added press recipe: graphite ingot -> diamond");
    }

    /** 硅晶圆：circuit 物品 + meta，meta 必须用 EnumCircuitType.ordinal()，不要填数字。 */
    public static ItemStack getPrintedSiliconWafer() {
        return new ItemStack(ModItems.circuit, 1, ItemCircuit.EnumCircuitType.SILICON.ordinal());
    }

    /** 化工厂通用封装（当前无调用方，留作备用）。 */
    private static void registerChemplantRecipe(String recipeName, ItemStack dust, ItemStack output) {
        if (dust == null || output == null) {
            MakeNTMGreatAgain.LOG.warn("Skipped chemical plant recipe (null input/output): {}", recipeName);
            return;
        }

        RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe(recipeName)
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
