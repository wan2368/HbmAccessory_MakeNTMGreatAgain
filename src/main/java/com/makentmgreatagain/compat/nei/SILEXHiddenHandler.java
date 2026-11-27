package com.makentmgreatagain.compat.nei;

import java.util.Map;

import com.hbm.handler.nei.SILEXRecipeHandler;
import com.hbm.inventory.recipes.SILEXRecipes;
import com.hbm.inventory.recipes.SILEXRecipes.SILEXRecipe;
import com.hbm.util.WeightedRandomObject;
import com.makentmgreatagain.init.ModItems;

import codechicken.nei.NEIServerUtils;
import net.minecraft.item.ItemStack;

/**
 * SILEX NEI handler 的附属替换版：把「乱码X晶体」从所有 NEI 配方页里滤掉，
 * 但机器（TileEntitySILEX）读的是真实配方表，分离照常产出乱码晶体。
 *
 * <p>实现：加载时把含乱码输出的配方临时换成"去掉乱码输出"的副本喂给父类，
 * 加载完立即恢复原表，保证机器侧不受影响。父类 RecipeSet 在构造时即快照输出，
 * 所以恢复后已缓存的 NEI 页仍不含乱码晶体。</p>
 */
public class SILEXHiddenHandler extends SILEXRecipeHandler {

    /** 把 NEIRegistry 里的 HBM SILEX handler 换成本隐藏版（幂等） */
    public static void install() {
        java.util.List<codechicken.nei.recipe.TemplateRecipeHandler> handlers = com.hbm.main.NEIRegistry.listAllHandlers();
        for(int i = 0; i < handlers.size(); i++) {
            if(handlers.get(i) instanceof SILEXRecipeHandler && !(handlers.get(i) instanceof SILEXHiddenHandler)) {
                handlers.set(i, new SILEXHiddenHandler());
                return;
            }
        }
    }

    private static boolean isGlitch(ItemStack s) {
        return s != null && s.getItem() == ModItems.crystal_xen_glitch;
    }

    @SuppressWarnings("unchecked")
    private Map<Object, SILEXRecipe> table() {
        try {
            java.lang.reflect.Field f = SILEXRecipes.class.getDeclaredField("recipes");
            f.setAccessible(true);
            return (Map<Object, SILEXRecipe>) f.get(null);
        } catch(Exception e) {
            return null;
        }
    }

    /** 临时把含乱码输出的配方替换为去掉乱码的副本，返回原配方以便恢复 */
    private java.util.HashMap<Object, SILEXRecipe> strip() {
        java.util.HashMap<Object, SILEXRecipe> orig = new java.util.HashMap<Object, SILEXRecipe>();
        Map<Object, SILEXRecipe> map = table();
        if(map == null) return orig;
        for(Map.Entry<Object, SILEXRecipe> e : map.entrySet()) {
            SILEXRecipe r = e.getValue();
            boolean has = false;
            for(WeightedRandomObject o : r.outputs) if(isGlitch(o.asStack())) { has = true; break; }
            if(!has) continue;
            orig.put(e.getKey(), r);
            SILEXRecipe filtered = new SILEXRecipe(r.fluidProduced, r.fluidConsumed, r.laserStrength);
            for(WeightedRandomObject o : r.outputs) if(!isGlitch(o.asStack())) filtered.addOut(o);
            e.setValue(filtered);
        }
        return orig;
    }

    private void restore(java.util.HashMap<Object, SILEXRecipe> orig) {
        Map<Object, SILEXRecipe> map = table();
        if(map == null) return;
        for(Map.Entry<Object, SILEXRecipe> e : orig.entrySet()) map.put(e.getKey(), e.getValue());
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        java.util.HashMap<Object, SILEXRecipe> orig = strip();
        super.loadCraftingRecipes(outputId, results);
        restore(orig);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if(isGlitch(result)) return; // 乱码晶体不反查出任何配方
        java.util.HashMap<Object, SILEXRecipe> orig = strip();
        super.loadCraftingRecipes(result);
        restore(orig);
    }

    @Override
    public void loadUsageRecipes(String inputId, Object... ingredients) {
        java.util.HashMap<Object, SILEXRecipe> orig = strip();
        super.loadUsageRecipes(inputId, ingredients);
        restore(orig);
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        java.util.HashMap<Object, SILEXRecipe> orig = strip();
        super.loadUsageRecipes(ingredient);
        restore(orig);
    }
}