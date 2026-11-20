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
 * 白钨矿湿法链：粉碎 -> 碱浸 -> 酸化 -> 焙烧 -> 氢还原，1 方块矿 = 1.6 锭。
 *
 * <p>白钨矿故意没有正常熔炼配方（丢熔炉出「没有东西」）。废酸闭环：L2 出 2500 废酸，
 * 正好顶 5 次 L1 用的 2500 水，所以碱浸必须同时有「水版」和「废酸版」。</p>
 *
 * <p>配方顺序：addShredderRecipes / addChemicalPlantRecipes / addPyroOvenRecipes / addFurnaceRecipes，
 * 前三者在 postInit 兜底里各有一行 step()，熔炉那条在 preInit 直接调。</p>
 */
public final class ScheeliteRecipes {

    /** 化工厂功率（HE/tick）。 */
    private static final long POWER_LEACH   = 500L;    // 碱浸
    private static final long POWER_ACID    = 800L;    // 酸化
    private static final long POWER_WASH    = 400L;    // 渣水洗
    private static final long POWER_EXTRACT = 1_200L;  // 芳烃萃取

    private ScheeliteRecipes() {
    }

    /* ---------------------------- L0　粉碎 ---------------------------- */

    /** 白钨矿粉 */
    public static void addShredderRecipes() {
        ShredderRecipes.setRecipe(ModBlocks.ore_scheelite, new ItemStack(ModItems.powder_scheelite, 2));
        // Duna 变体：同一份产物，精确方块匹配要单独登记
        if(ModBlocks.ore_scheelite_duna != null) {
            ShredderRecipes.setRecipe(ModBlocks.ore_scheelite_duna, new ItemStack(ModItems.powder_scheelite, 2));
        }
        MakeNTMGreatAgain.LOG.info("[白钨矿] 粉碎机：1 白钨矿 → 2 白钨矿粉（含 Duna 变体）");
    }

    /* ---------------------------- L1–L2　碱浸与酸化（化工厂） ---------------------------- */

    /** 白钨浸出渣、钨酸钠液、钨酸、废酸、铍精矿、砷渣、锆精矿 */
    public static void addChemicalPlantRecipes() {

        // 白钨浸出渣、钨酸钠液
        if(!has(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.scheelite.leachWater")) {
            RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.scheelite.leachWater")
                .setup(200, POWER_LEACH)
                .inputItems(new ComparableStack(ModItems.powder_scheelite, 1))
                .inputFluids(new FluidStack(Fluids.LYE, 500), new FluidStack(Fluids.WATER, 500))
                .outputItems(new ItemStack(ModItems.scheelite_leach_residue, 1))
                .outputFluids(new FluidStack(ModFluids.SODIUM_TUNGSTATE.type, 500)));
        }

        // 白钨浸出渣、钨酸钠液
        if(!has(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.scheelite.leachWasteAcid")) {
            RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.scheelite.leachWasteAcid")
                .setup(200, POWER_LEACH)
                .inputItems(new ComparableStack(ModItems.powder_scheelite, 1))
                .inputFluids(new FluidStack(Fluids.LYE, 500), new FluidStack(ModFluids.WASTE_ACID.type, 500))
                .outputItems(new ItemStack(ModItems.scheelite_leach_residue, 1))
                .outputFluids(new FluidStack(ModFluids.SODIUM_TUNGSTATE.type, 500)));
        }

        // 钨酸、废酸
        if(!has(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.scheelite.precipitate")) {
            RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.scheelite.precipitate")
                .setup(240, POWER_ACID)
                .inputFluids(new FluidStack(ModFluids.SODIUM_TUNGSTATE.type, 2_500),
                             new FluidStack(Fluids.SULFURIC_ACID, 750))
                .outputItems(new ItemStack(ModItems.tungstic_acid, 4))
                .outputFluids(new FluidStack(ModFluids.WASTE_ACID.type, 2_500)));
        }

        // 铍精矿、砷渣、钨酸钠液
        if(!has(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.scheelite.residueWash")) {
            RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.scheelite.residueWash")
                .setup(600, POWER_WASH)
                .inputItems(new ComparableStack(ModItems.scheelite_leach_residue, 32))
                .inputFluids(new FluidStack(Fluids.LYE, 250), new FluidStack(Fluids.WATER, 500))
                .outputItems(new ItemStack(ModItems.beryllium_concentrate, 1),
                             new ItemStack(ModItems.arsenic_cake, 31))
                .outputFluids(new FluidStack(ModFluids.SODIUM_TUNGSTATE.type, 500)));
        }

        // 铍精矿、锆精矿、砷渣、钨酸钠液、轻质芳烃
        if(!has(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.scheelite.residueExtract")) {
            RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.scheelite.residueExtract")
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

    /* ---------------------------- L3–L4 + R3　焙烧与还原（热解炉） ---------------------------- */

    /**
     * 热解炉段：钨酸焙烧、WO3 氢还原、砷渣氧化焙烧。
     * 槽位硬约束：inputFluid / inputItem / outputFluid / outputItem 各一个，
     * 不能出现「两个物品进」或「两个物品出」。
     */
    public static void addPyroOvenRecipes() {

        // 热解炉配方表是 List 不是 Map，postInit 兜底注入会再调一次，必须自己判重
        // in() 走兼容层：旧版 HBM 的 in() 返回 void，不能链式调用
        if(!hasPyroInput(ModItems.tungstic_acid)) {
            // 三氧化钨、蒸汽
            PyroOvenRecipes.PyroOvenRecipe recipe = new PyroOvenRecipes.PyroOvenRecipe(200);
            com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new ComparableStack(ModItems.tungstic_acid, 4));
            recipe.out(new ItemStack(ModItems.tungsten_trioxide, 4))
                .out(new FluidStack(Fluids.STEAM, 2_000));
            PyroOvenRecipes.recipes.add(recipe);
        }

        if(!hasPyroInput(ModItems.tungsten_trioxide)) {
            // 钨粉、蒸汽
            PyroOvenRecipes.PyroOvenRecipe recipe = new PyroOvenRecipes.PyroOvenRecipe(300);
            com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new ComparableStack(ModItems.tungsten_trioxide, 4));
            com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new FluidStack(Fluids.HYDROGEN, 4_000));
            recipe.out(new ItemStack(com.hbm.items.ModItems.powder_tungsten, 4))
                .out(new FluidStack(Fluids.STEAM, 4_000));
            PyroOvenRecipes.recipes.add(recipe);
        }

        if(!hasPyroInput(ModItems.arsenic_cake)) {
            // 白砷
            PyroOvenRecipes.PyroOvenRecipe recipe = new PyroOvenRecipes.PyroOvenRecipe(300);
            com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new ComparableStack(ModItems.arsenic_cake, 32));
            com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new FluidStack(Fluids.OXYGEN, 4_000));
            recipe.out(new ItemStack(ModItems.arsenic_trioxide, 1));
            PyroOvenRecipes.recipes.add(recipe);
        }

        MakeNTMGreatAgain.LOG.info("[白钨矿] 热解炉：钨酸焙烧、WO3 氢还原、砷渣氧化焙烧");
    }

    /** 热解炉里是否已有吃这个物品的配方 */
    private static boolean hasPyroInput(net.minecraft.item.Item item) {

        if(item == null) return false;

        for(PyroOvenRecipes.PyroOvenRecipe rec : PyroOvenRecipes.recipes) {
            if(rec.inputItem instanceof ComparableStack && ((ComparableStack) rec.inputItem).item == item) return true;
        }

        return false;
    }

    /* ---------------------------- 熔炉：精矿 → 锭 ---------------------------- */

    /** 铍锭、锆锭、没有东西 */
    public static void addFurnaceRecipes() {
        GameRegistry.addSmelting(ModItems.beryllium_concentrate,
                new ItemStack(com.hbm.items.ModItems.ingot_beryllium, 1), 1.0F);
        GameRegistry.addSmelting(ModItems.zirconium_concentrate,
                new ItemStack(com.hbm.items.ModItems.ingot_zirconium, 1), 1.0F);

        // 没有东西
        if (com.hbm.items.ModItems.nothing != null) {
            GameRegistry.addSmelting(ModBlocks.ore_scheelite,
                    new ItemStack(com.hbm.items.ModItems.nothing, 1), 0.0F);
            if(ModBlocks.ore_scheelite_duna != null) {
                GameRegistry.addSmelting(ModBlocks.ore_scheelite_duna,
                        new ItemStack(com.hbm.items.ModItems.nothing, 1), 0.0F);
            }
        }

        MakeNTMGreatAgain.LOG.info("[白钨矿] 熔炉：铍精矿/锆精矿 → 铍锭/锆锭（1:1）；白钨矿 → 没有东西");
    }

    /** recipeNameMap 按内部名判重。 */
    private static boolean has(GenericRecipes<?> holder, String name) {
        return holder.recipeNameMap.containsKey(name);
    }
}
