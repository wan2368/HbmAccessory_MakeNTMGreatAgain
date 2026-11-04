package com.makentmgreatagain.recipes;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import com.hbm.blocks.machine.NTMAnvil;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.anvil.AnvilRecipes;
import com.hbm.inventory.recipes.anvil.AnvilRecipes.AnvilConstructionRecipe;
import com.hbm.inventory.recipes.anvil.AnvilSmithingRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.items.machine.ItemBlueprints;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.init.ModItems;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 把 HBM 砧的一键配方转换成装配机配方，并生成“空模板 + 砧 → 等级模板”的砧配方。
 *
 * <p>过滤规则（和砧内保留的一致）：模具/锻模、板、线、管、壳、流体管道、
 * 警报声轨、稀土分解不进入装配机。</p>
 *
 * <p>等级模板的配方池是累计的：tierN 模板包含所有 tierLower <= N 的配方；
 * 暗黑砧模板包含全部配方。</p>
 */
public final class AnvilAssemblyTemplates {

    public static final String POOL_PREFIX = "mnga.anvil.";
    public static final String POOL_MURKY = POOL_PREFIX + "murky";

    private static final Set<String> REGISTERED_TEMPLATE_KEYS = new HashSet<String>();

    private AnvilAssemblyTemplates() {
    }

    public static void register() {
        if(AnvilRecipes.getConstruction() == null || AnvilRecipes.getConstruction().isEmpty()) {
            MakeNTMGreatAgain.LOG.warn("[MNGA] AnvilAssemblyTemplates: construction recipe list is empty, skipped.");
            return;
        }

        int added = 0;
        int skipped = 0;

        for(int i = 0; i < AnvilRecipes.getConstruction().size(); i++) {
            AnvilConstructionRecipe recipe = AnvilRecipes.getConstruction().get(i);
            if(recipe == null) continue;

            String name = POOL_PREFIX + "recipe." + i;
            if(AssemblyMachineRecipes.INSTANCE.recipeNameMap.containsKey(name)) continue;

            try {
                if(!eligible(recipe)) { skipped++; continue; }

                AStack[] inputs = expand(recipe.input.toArray(new AStack[0]));

                List<ItemStack> stacks = new ArrayList<ItemStack>();
                List<Float> chances = new ArrayList<Float>();
                readOutputs(recipe, stacks, chances);

                if(stacks.isEmpty()) { skipped++; continue; }

                int level = levelOf(recipe.tierLower);
                int duration = 100 + (level - 1) * 100;       // 5s + 5s/级
                long power = 100L << (level - 1);              // 100 HE/t 起，每级翻倍
                GenericRecipe converted = new GenericRecipe(name).setup(duration, power);
                converted.inputItems(inputs);

                GenericRecipes.IOutput[] outputs = new GenericRecipes.IOutput[stacks.size()];
                for(int k = 0; k < stacks.size(); k++) {
                    outputs[k] = new GenericRecipes.ChanceOutput(stacks.get(k), chances.get(k));
                }
                converted.outputItems(outputs);
                converted.setPoolsAllow528(poolsFor(recipe.tierLower));

                AssemblyMachineRecipes.INSTANCE.register(converted);
                added++;

            } catch(Throwable t) {
                MakeNTMGreatAgain.LOG.warn("[MNGA] AnvilAssemblyTemplates: failed converting recipe " + i, t);
            }
        }

        addSmithingRecipes();

        MakeNTMGreatAgain.LOG.info("[MNGA] AnvilAssemblyTemplates: " + added
                + " assembly recipes added, " + skipped + " skipped.");
    }

    /** 读取 AnvilOutput 的 stack/chance。AnvilOutput 是包私有类，只能反射。 */
    private static void readOutputs(AnvilConstructionRecipe recipe, List<ItemStack> stacks, List<Float> chances) throws Exception {
        Field f = recipe.getClass().getField("output");
        List<?> outs = (List<?>) f.get(recipe);
        if(outs == null) return;

        for(Object o : outs) {
            if(o == null) continue;
            Field sf = o.getClass().getDeclaredField("stack");
            sf.setAccessible(true);
            ItemStack stack = (ItemStack) sf.get(o);
            if(stack == null) continue;

            float chance = 1F;
            try {
                Field cf = o.getClass().getDeclaredField("chance");
                cf.setAccessible(true);
                chance = cf.getFloat(o);
            } catch(Throwable ignored) {
            }

            stacks.add(stack.copy());
            chances.add(chance);
        }
    }

    /** 普通等级 1~8；暗黑砧按第 9 级处理。 */
    private static int levelOf(int tierLower) {
        if(tierLower >= 1916169) return 9;
        if(tierLower < 1) return 1;
        if(tierLower > 8) return 8;
        return tierLower;
    }

    private static String[] poolsFor(int tierLower) {
        List<String> pools = new ArrayList<String>();
        for(int n = 1; n <= 8; n++) {
            if(tierLower <= n) pools.add(POOL_PREFIX + "tier" + n);
        }
        pools.add(POOL_MURKY);
        return pools.toArray(new String[0]);
    }

    private static boolean eligible(AnvilConstructionRecipe recipe) {
        for(AStack input : recipe.input) {
            if(bannedInput(input)) return false;
        }

        try {
            Field f = recipe.getClass().getField("output");
            List<?> outs = (List<?>) f.get(recipe);
            for(Object o : outs) {
                Field sf = o.getClass().getDeclaredField("stack");
                sf.setAccessible(true);
                ItemStack stack = (ItemStack) sf.get(o);
                if(stack != null && bannedOutput(stack.getItem())) return false;
            }
        } catch(Throwable t) {
            return false;
        }

        return true;
    }

    private static boolean bannedInput(AStack input) {
        if(!(input instanceof ComparableStack)) return false;
        ComparableStack comp = (ComparableStack) input;
        if(comp.item == null) return false;

        // 模具/锻模和稀土分解的输入不进装配机；板/线/管/壳作为材料仍可使用
        if(isMoldOrStamp(comp.item)) return true;
        if(comp.item == com.hbm.items.ModItems.chunk_ore) return true;
        return false;
    }

    private static boolean bannedOutput(Item item) {
        if(item == null) return true;

        if(isMoldOrStamp(item)) return true;
        if(item == com.hbm.items.ModItems.wire_fine) return true;
        if(item == com.hbm.items.ModItems.pipe) return true;
        if(item == com.hbm.items.ModItems.shell) return true;
        if(item == com.hbm.items.ModItems.siren_track) return true;

        String name = item.getUnlocalizedName();
        if(name == null) name = "";
        if(name.startsWith("tile.fluid_duct")) return true;

        // 板：保留装甲板
        if(name.startsWith("item.plate_") && !name.startsWith("item.plate_armor")) return true;

        return false;
    }

    private static boolean isMoldOrStamp(Item item) {
        String name = item.getUnlocalizedName();
        if(name == null) return false;
        return name.contains("stamp") || name.contains("mold");
    }

    /** 超过 64 的输入拆成多个 64 的 AStack，让装配机用两个格子装 128 个。 */
    private static AStack[] expand(AStack[] inputs) {
        List<AStack> out = new ArrayList<AStack>();
        for(AStack input : inputs) {
            int left = input.stacksize;
            if(left <= 0) left = 1;
            while(left > 0) {
                int part = Math.min(left, 64);
                out.add(input.copy(part));
                left -= part;
            }
        }
        return out.toArray(new AStack[0]);
    }

    /** 空模板 + 对应等级砧 → 该等级装配模板。任意砧都能敲（tier 0）。 */
    private static void addSmithingRecipes() {
        if(AnvilRecipes.getSmithing() == null) return;

        for(Map.Entry<Integer, List<NTMAnvil>> entry : NTMAnvil.tierMap.entrySet()) {
            int tier = entry.getKey();
            String pool = tier == 1916169 ? POOL_MURKY : POOL_PREFIX + "tier" + tier;

            for(NTMAnvil anvil : entry.getValue()) {
                if(anvil == null) continue;

                ItemStack output = ItemBlueprints.make(pool);
                AnvilSmithingRecipe recipe = new AnvilSmithingRecipe(
                        0,
                        output,
                        new ComparableStack(ModItems.blank_template),
                        new ComparableStack(anvil));

                String key = tier + ":" + anvil.getUnlocalizedName();
                if(REGISTERED_TEMPLATE_KEYS.add(key)) {
                    AnvilRecipes.getSmithing().add(recipe);
                }
            }
        }
    }
}
