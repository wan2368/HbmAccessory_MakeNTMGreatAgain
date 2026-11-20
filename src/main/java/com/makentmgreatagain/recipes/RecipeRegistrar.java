package com.makentmgreatagain.recipes;

import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;

/**
 * 向 HBM 的 {@link GenericRecipes} 表注册配方的唯一入口。
 *
 * <p>为什么不直接调 {@code table.register(recipe)}：HBM 的实现是这样的——</p>
 *
 * <pre>
 * public void register(T recipe) {
 *     this.recipeOrderedList.add(recipe);          // ← 先入列表
 *     if(recipeNameMap.containsKey(recipe.name))
 *         throw new IllegalStateException("... duplicate ID!");   // ← 再查重，抛异常
 *     this.recipeNameMap.put(recipe.name, recipe);
 * }
 * </pre>
 *
 * <p>也就是说<b>重名时列表已经被污染了</b>：对象进了 {@code recipeOrderedList}，却没进
 * {@code recipeNameMap}。如果这个异常还被上层吃掉（例如 {@code ModRecipes.step()} 的
 * “单条失败不影响后续”），表里就永久留着两条同名配方，之后任何按列表遍历重建的地方
 * （{@code LargeChemicalFactoryRecipes.rebuild()}、NEI 扫描、配方选择器）都会在第二条上
 * 再抛一次重名异常——而这次没人接，直接崩游戏。</p>
 *
 * <p>本模组有两条注册通路：HBM 载入配方时的 {@code IRecipeRegisterListener}（只在
 * “没有 json 模板”时触发），以及 postInit 的 {@code ModRecipes.injectAllFallback()} 兜底。
 * 两条都会跑的时候，未做判重的配方就会撞名。{@link com.makentmgreatagain.recipes.RecipeLedger}
 * 只在「账本基线存在且内容未变」时才会先摘除再重建，首次启动 / 玩家改过 json 时
 * 整张表会被保护（不摘除），于是撞名必然发生。</p>
 *
 * <p>所以这里统一改成「已存在就跳过」，把幂等性收在一个地方，而不是指望 121 个调用点
 * 各自记得判重。</p>
 */
public final class RecipeRegistrar {

    private RecipeRegistrar() { }

    /**
     * 幂等注册：表里已有同名配方就跳过。
     *
     * @return {@code true} 表示本次真的注册进去了，{@code false} 表示已存在（跳过）
     */
    public static <T extends GenericRecipe> boolean add(GenericRecipes<T> table, T recipe) {

        if(table == null || recipe == null) return false;

        String name = recipe.getInternalName();
        if(name == null) return false;

        // 判重必须在 register() 之前：register 内部是先 add 后查重，撞名会留下幽灵条目
        if(table.recipeNameMap.containsKey(name)) return false;

        table.register(recipe);
        return true;
    }
}
