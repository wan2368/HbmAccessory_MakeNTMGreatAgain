package com.makentmgreatagain.init;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.recipes.ArcWelderRecipes;
import com.hbm.inventory.recipes.ArcWelderRecipes.ArcWelderRecipe;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.PrecAssRecipes;
import com.hbm.inventory.recipes.SolderingRecipes;
import com.hbm.inventory.recipes.SolderingRecipes.SolderingRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipes.ChanceOutput;
import com.hbm.inventory.recipes.loader.GenericRecipes.ChanceOutputMulti;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * 「铂细线替代」配方：把 HBM 里用到金细线 / 铅细线的元件配方各复制一份，
 * 换线、翻倍产出，<b>原配方原样保留</b>。
 *
 * <h2>做什么</h2>
 * <p>对每条含 {@code wireFineGold} 或 {@code wireFineLead} 的元件配方：</p>
 * <ol>
 *   <li>把那一处线换成 <b>{@code wireFinePlatinum}</b>（铂细线），数量不变；</li>
 *   <li>产物数量 <b>×2</b>（带碎品的概率配方除外，见下一节）；</li>
 *   <li>时间、能耗、流体、其余材料、机器、蓝图池、成品率 / 回收率<b>全部照抄原配方</b>。</li>
 * </ol>
 * <p>另外电弧焊机单独加一条：1 致密金线 + 2 铂板 → 4 马达（原配方 2 钢板 + 2 军工级钢致密线 → 2 马达）。</p>
 *
 * <h2>例外：概率配方（528 的成品率 + 碎品）只有成品翻倍</h2>
 * <p>精密装配机那 7 条走 {@link PrecAssRecipes#registerPair}，输出是「成品率 % 出成品、剩下出碎品」的
 * 二选一池子。{@code registerPair} 只收一个 Stack，<b>碎品的数量是从成品 Stack 里抄出来的</b> ——
 * 直接传 ×2，碎品就跟着变成一次掉 <b>2 个</b>，而碎品是 NBT 一致的通用物品（只有 itemID + meta），
 * 原版那条 {@code precass.*.recycle} 每次只吃 1 个：</p>
 * <pre>
 *   一次失败掉 2 个碎品 → 跑两次原版回收 → 拿回两套原料 × 回收率（晶体电路板那条是 100%）
 *   → 一次失败反而赚，材料凭空变多
 * </pre>
 * <p>所以这 7 条的做法是<b>先按 ×1 注册、再把成品那一项单独 ×2</b>（{@link #doubleGoodOutput}）：
 * 成品 2 个、碎品 1 个、回收吃 1 个，<b>成品率与回收率都不动</b>。保底出产的那 13 条（焊接台 9、
 * 装配机 4）本来就是 ×2，没有碎品，不受影响。</p>
 *
 * <h2>为什么不硬编码 21 条数值</h2>
 * <p>这里全部<b>从现存配方派生</b>：遍历 HBM 的表，找到带金/铅细线的那几条，复制 + 改线 + 翻倍。
 * 好处有三个：</p>
 * <ul>
 *   <li>「其他不变」是结构上保证的 —— 没有第二个地方抄数值，就不会抄错；</li>
 *   <li>HBM 改了数值（换版本 / 玩家 json 里改过）时，铂版自动跟随；</li>
 *   <li>玩家在 {@code config/hbmRecipes} 里删掉某条原配方后，对应的铂版也不会凭空出现。</li>
 * </ul>
 *
 * <h2>幂等</h2>
 * <p>三个注入方法都可以重复调用：焊接台 / 电弧焊机按「产物 + 铂线」比对，装配机 / 精密装配机按
 * 配方名（{@code makentmgreatagain.pt.*}）比对，已经存在就直接跳过。所以既能在
 * {@code additionalListeners}（无 json 时 HBM 每次重建默认表）里调，也能在
 * {@code ModRecipes.injectAllFallback()}（有 json 时的兜底注入）里调。</p>
 *
 * <p>配方名统一 {@code makentmgreatagain.pt.<原内部名>}，前缀是
 * {@link com.makentmgreatagain.recipes.RecipeLedger} 认领自有配方的依据，不要改。</p>
 */
public final class ModPlatinumRecipes {

    /** 被替换的两种线。只有这两种。 */
    private static final String WIRE_GOLD = "wireFineGold";
    private static final String WIRE_LEAD = "wireFineLead";
    /** 换成这个 —— 本模组 {@code MNGAMetals.registerOreDict()} 里 registerOne("Platinum", ...) 注册的矿辞。 */
    private static final String WIRE_PLATINUM = "wireFinePlatinum";

    /** 自有配方名前缀。 */
    private static final String PREFIX = "makentmgreatagain.pt.";
    /** 精密装配机的废品回收配方后缀，这类是派生出来的，不要再复制一遍。 */
    private static final String RECYCLE = ".recycle";
    /** 已经提示过的「矿辞未就绪」项，避免监听器 + 兜底各刷一遍日志。 */
    private static final Set<String> LOGGED = new HashSet<String>();

    private ModPlatinumRecipes() { }

    /** 四个注入点的总入口（postInit 兜底用）。 */
    public static void inject() {
        injectSoldering();
        injectAssembly();
        injectPrecAss();
        injectMotor();
    }

    /**
     * 四个对外的注入方法都套一层「矿辞就绪 + 异常兜底」。
     *
     * <p>两个原因，都是实战教训：</p>
     * <ul>
     *   <li><b>矿辞未就绪</b>：{@code PrecAssRecipes.registerPair} 会拿输入做
     *       {@code extractForNEI().get(0)} 来生成回收配方，铂细线还没登记时这里会
     *       {@code IndexOutOfBoundsException}；</li>
     *   <li><b>不能被我们炸掉</b>：{@link #inject()} 之外的三个方法还会被挂到 HBM 的
     *       {@code additionalListeners} 上，跑在 HBM 的配方初始化循环里，抛出去会连累整张表。</li>
     * </ul>
     * <p>矿辞没就绪时直接跳过，等 postInit 那次再补 —— 两条调用路径本身就是互为兜底的。</p>
     */
    private static void guarded(String what, Runnable action) {
        if(OreDictionary.getOres(WIRE_PLATINUM).isEmpty()) {
            if(LOGGED.add(what)) {
                com.makentmgreatagain.MakeNTMGreatAgain.LOG.warn(
                        "铂细线替代：" + what + " 本次跳过 —— wireFinePlatinum 尚未注册（矿辞未就绪）");
            }
            return;
        }
        try {
            action.run();
        } catch(Throwable ex) {
            com.makentmgreatagain.MakeNTMGreatAgain.LOG.warn("铂细线替代：" + what + " 注入失败：" + ex);
        }
    }

    // ------------------------------------------------------------------ 焊接台

    /**
     * 焊接台：9 条电路件全是 {@code PB.wireFine()} 焊锡，逐个复制成铂版。
     *
     * <p>只认 {@code solder}（第三个槽），{@code toppings} / {@code pcb} 里的东西不动 ——
     * 那两槽放的是电路件与基材，本来就没有线。</p>
     */
    public static void injectSoldering() { guarded("soldering", ModPlatinumRecipes::doInjectSoldering); }

    private static void doInjectSoldering() {
        List<SolderingRecipe> snapshot = new ArrayList<SolderingRecipe>(SolderingRecipes.recipes);

        for(SolderingRecipe recipe : snapshot) {
            if(!hasSwappable(recipe.solder)) continue;

            ItemStack output = doubled(recipe.output);
            if(output == null || hasPlatinumOutput(SolderingRecipes.recipes, output)) continue;

            SolderingRecipes.recipes.add(new SolderingRecipe(output, recipe.duration, recipe.consumption,
                    recipe.fluid, swapWires(recipe.toppings), swapWires(recipe.pcb), swapWires(recipe.solder)));
        }
    }

    /** 列表里是否已有「同一个产物 + 铂线」的配方（判重，也是重复调用的保险）。 */
    private static boolean hasPlatinumOutput(List<SolderingRecipe> recipes, ItemStack output) {
        for(SolderingRecipe recipe : recipes) {
            if(!hasPlatinum(recipe.solder)) continue;
            ItemStack existing = recipe.output;
            if(existing == null) continue;
            if(existing.getItem() == output.getItem()
                    && existing.getItemDamage() == output.getItemDamage()
                    && existing.stackSize == output.stackSize) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 装配机

    /**
     * 装配机：4 条（微芯片、多功能集成电路、固态量子处理器、热电元件-硅路线），全是金细线。
     *
     * <p>输出翻倍后重新 {@code outputItems(...)}，所以原本的 {@code ChanceOutput} 包装会被
     * 换成一个新的 {@code ChanceOutput}（chance = 1）—— 这 4 条本来就是必出，没有概率。</p>
     */
    public static void injectAssembly() { guarded("assembly", ModPlatinumRecipes::doInjectAssembly); }

    private static void doInjectAssembly() {
        GenericRecipes<GenericRecipe> table = AssemblyMachineRecipes.INSTANCE;

        for(GenericRecipe recipe : new ArrayList<GenericRecipe>(table.recipeOrderedList)) {
            if(!hasSwappable(recipe.inputItem)) continue;

            String name = PREFIX + recipe.getInternalName();
            if(table.recipeNameMap.containsKey(name)) continue;

            ItemStack output = firstOutput(recipe);
            if(output == null) continue;

            GenericRecipe clone = new GenericRecipe(name)
                    .setup(recipe.duration, recipe.power)
                    .inputItems(swapWires(recipe.inputItem))
                    .outputItems(doubled(output));

            cloneRecipeShell(clone, recipe);
            table.register(clone);
        }
    }

    // ------------------------------------------------------------- 精密装配机

    /**
     * 精密装配机：7 条（晶体电路板、微芯片、多功能集成电路、固态量子处理器 + 三条控制单元）。
     *
     * <p>走 {@link PrecAssRecipes#registerPair}，因为那 7 条都是「成品率 + 碎品」的双输出，
     * 直接 {@code register} 会丢掉碎品与自动生成的回收配方。成品率从原配方的
     * {@code ChanceOutputMulti} 权重反推，回收率从原回收配方（{@code <名>.recycle}）的概率反推，
     * 这样 {@code enableExpensiveMode} 的差异不需要在这里再判断一次。</p>
     *
     * <p><b>这 7 条成品 ×2、碎品 ×1</b>：先 {@code registerPair(clone, output, ...)} 按 ×1 注册
     * （碎品 1 个、回收吃 1 个、成品率照旧），注册完再 {@link #doubleGoodOutput} 把成品那一项翻倍。
     * 理由见类注释 —— 碎品跟着翻倍会变成「失败一次退回两套料」。</p>
     */
    public static void injectPrecAss() { guarded("precass", ModPlatinumRecipes::doInjectPrecAss); }

    private static void doInjectPrecAss() {
        PrecAssRecipes table = PrecAssRecipes.INSTANCE;

        for(GenericRecipe recipe : new ArrayList<GenericRecipe>(table.recipeOrderedList)) {
            String id = recipe.getInternalName();
            if(id.endsWith(RECYCLE)) continue;                  // 回收配方是派生物，不复制
            if(!hasSwappable(recipe.inputItem)) continue;

            String name = PREFIX + id;
            if(table.recipeNameMap.containsKey(name)) continue;

            ItemStack output = pairedOutput(recipe);
            int chance = chanceOf(recipe);
            int reclaim = reclaimOf(table, id);
            if(output == null || chance <= 0 || reclaim <= 0) continue;   // 结构不认识就跳过，不猜

            GenericRecipe clone = new GenericRecipe(name)
                    .setup(recipe.duration, recipe.power)
                    .inputItems(swapWires(recipe.inputItem));

            cloneRecipeShell(clone, recipe);

            if(hasFaultyOutput(recipe)) {
                // 成对配方：registerPair 用同一个 Stack 同时造成品与碎品，所以先按 ×1 注册，
                // 再单独把成品那一项翻倍 —— 碎品与回收配方保持在 1 个的量级。
                table.registerPair(clone, output, chance, reclaim);
                doubleGoodOutput(table.recipeNameMap.get(name));
            } else {
                table.registerPair(clone, doubled(output), chance, reclaim);
            }
        }
    }

    /**
     * 成品率（百分数）：从「成品」那一项的权重里读。
     *
     * <p>{@code registerPair} 用 {@code new ChanceOutput(output, chance)} 建池子，命中的是
     * {@code (ItemStack, int weight)} 重载 —— 百分数落在 {@code itemWeight} 上（成品 + 碎品 = 100），
     * {@code chance} 字段反而恒为 1。所以这里读 weight，不读 chance。</p>
     */
    private static int chanceOf(GenericRecipe recipe) {
        IOutput output = recipe.outputItem == null || recipe.outputItem.length == 0 ? null : recipe.outputItem[0];
        if(output instanceof ChanceOutputMulti) {
            List<ChanceOutput> pool = ((ChanceOutputMulti) output).pool;
            for(ChanceOutput entry : pool) {
                if(!isBroken(entry)) return entry.itemWeight;
            }
            return pool.isEmpty() ? 0 : pool.get(0).itemWeight;
        }
        if(output instanceof ChanceOutput) return Math.round(((ChanceOutput) output).chance * 100F);
        return 0;
    }

    /** 回收率（百分数）：从原配方自动生成的 {@code <名>.recycle} 里读第一项的 chance。 */
    private static int reclaimOf(PrecAssRecipes table, String id) {
        GenericRecipe recycle = table.recipeNameMap.get(id + RECYCLE);
        if(recycle == null || recycle.outputItem == null || recycle.outputItem.length == 0) return 0;
        IOutput output = recycle.outputItem[0];
        if(output instanceof ChanceOutput) return Math.round(((ChanceOutput) output).chance * 100F);
        return 0;
    }

    // ------------------------------------------------------------ 电弧焊机马达

    /**
     * 电弧焊机：新增一条马达配方 —— <b>1 致密金线 + 2 铂板 → 4 马达</b>。
     *
     * <p>原配方是 2 钢板 + 2 军工级钢致密线 → 2 马达（100 t / 400 HE/t）。这条是手写的，
     * 不走派生，因为它的输入结构与原配方没有任何对应关系（材料全换了）。
     * 时间与能耗照抄原配方，所以单位马达的成本正好减半。</p>
     */
    public static void injectMotor() { guarded("motor", ModPlatinumRecipes::doInjectMotor); }

    private static void doInjectMotor() {
        for(ArcWelderRecipe recipe : ArcWelderRecipes.recipes) {
            ItemStack output = recipe.output;
            if(output != null && output.getItem() == com.hbm.items.ModItems.motor
                    && output.stackSize == 4 && output.getItemDamage() == 0) {
                return;
            }
        }

        ArcWelderRecipes.recipes.add(new ArcWelderRecipe(
                new ItemStack(com.hbm.items.ModItems.motor, 4), 100, 400L,
                new OreDictStack("wireDenseGold", 1), new OreDictStack("platePlatinum", 2)));
    }

    // -------------------------------------------------------------------- 工具

    /** 复制姓名包装 / 图标 / 流体 / 蓝图池这些「附加值」字段。 */
    private static void cloneRecipeShell(GenericRecipe clone, GenericRecipe source) {
        clone.nameWrapper = source.nameWrapper;
        if(source.inputFluid != null) clone.inputFluids(source.inputFluid);
        if(source.outputFluid != null) clone.outputFluids(source.outputFluid);
        // 用 Allow528：克隆时原配方已经通过了那条检查，这里再抛一次只会把整条链打断
        if(source.getPools() != null) clone.setPoolsAllow528(source.getPools());
        // 刻意不复制 autoSwitchGroup：自动切换组是按第一项固体输入的「同名实体」做替换的，
        // 同一个产物挂两条（原版 + 铂版）会让 GUI 在两者之间来回跳。
    }

    /** 这条输入里有没有金细线 / 铅细线。 */
    private static boolean hasSwappable(AStack[] stacks) {
        if(stacks == null) return false;
        for(AStack stack : stacks) if(isSwappable(stack)) return true;
        return false;
    }

    private static boolean isSwappable(Object stack) {
        if(!(stack instanceof OreDictStack)) return false;
        String name = ((OreDictStack) stack).name;
        return WIRE_GOLD.equals(name) || WIRE_LEAD.equals(name);
    }

    /** 这条输入里有没有铂细线（用于焊接台判重）。 */
    private static boolean hasPlatinum(AStack[] stacks) {
        if(stacks == null) return false;
        for(AStack stack : stacks) {
            if(stack instanceof OreDictStack && WIRE_PLATINUM.equals(((OreDictStack) stack).name)) return true;
        }
        return false;
    }

    /**
     * 复制整组输入，只把金/铅细线换成铂细线（数量不变）。
     *
     * <p>其它 stack 走 {@code copy()} 而不是直接引用：两套配方共用同一个 AStack 对象虽然当前
     * 不会被改，但以后任何一处（NEI 计数、配方编辑 GUI）动它都会同时污染原配方。</p>
     */
    private static AStack[] swapWires(AStack[] stacks) {
        if(stacks == null) return null;
        AStack[] out = new AStack[stacks.length];
        for(int i = 0; i < stacks.length; i++) {
            AStack stack = stacks[i];
            out[i] = isSwappable(stack) ? new OreDictStack(WIRE_PLATINUM, stack.stacksize) : stack.copy();
        }
        return out;
    }

    /** 产物翻倍（保留 NBT 与 meta）。 */
    private static ItemStack doubled(ItemStack stack) {
        if(stack == null) return null;
        ItemStack copy = stack.copy();
        copy.stackSize *= 2;
        return copy;
    }

    /** 取唯一那项产物的原样副本；多输出（getSingle 返回 null）时放弃。 */
    private static ItemStack firstOutput(GenericRecipe recipe) {
        if(recipe.outputItem == null || recipe.outputItem.length == 0) return null;
        return recipe.outputItem[0].getSingle();
    }

    /**
     * 取「成品率 + 碎品」池子里的<b>成品</b>那一项。
     *
     * <p>不能直接用 {@link #firstOutput}：成对配方的输出是 {@code ChanceOutputMulti}（成品一条、
     * 碎品一条），而 {@code ChanceOutputMulti.getSingle()} 在池子大于 1 时<b>恒返回 null</b> ——
     * 照着写会让这 7 条一条都注册不上（看起来像「没生效」，实际是提前 continue 了）。</p>
     *
     * <p>顺序不能假设：找第一项<b>不是</b>碎品的。</p>
     */
    private static ItemStack pairedOutput(GenericRecipe recipe) {
        if(recipe.outputItem == null || recipe.outputItem.length == 0) return null;
        IOutput output = recipe.outputItem[0];

        if(output instanceof ChanceOutputMulti) {
            for(ChanceOutput entry : ((ChanceOutputMulti) output).pool) {
                if(!isBroken(entry)) return entry.getSingle();
            }
            return null;
        }
        return output.getSingle();
    }

    /** 这条配方是不是「成品率 + 碎品」的概率配方（{@code registerPair} 造出来的那种）。 */
    private static boolean hasFaultyOutput(GenericRecipe recipe) {
        if(recipe.outputItem == null) return false;
        for(IOutput output : recipe.outputItem) {
            if(!(output instanceof ChanceOutputMulti)) continue;
            for(ChanceOutput entry : ((ChanceOutputMulti) output).pool) {
                if(isBroken(entry)) return true;
            }
        }
        return false;
    }

    /** {@code BrokenItem.make(...)} 造出来的碎品。 */
    private static boolean isBroken(ChanceOutput entry) {
        return entry.stack != null && entry.stack.getItem() == com.hbm.items.ModItems.broken_item;
    }

    /**
     * 把成对配方里<b>成品</b>那一项翻倍，碎品、回收配方、成品率、回收率全部不动。
     *
     * <p>为什么不在 {@code registerPair} 之前就把 Stack 乘 2：那个方法会用同一个 Stack 派生碎品
     * （{@code BrokenItem.make(output)} 抄 {@code stackSize}），成品 ×2 会连带碎品 ×2，而碎品与原版
     * 回收配方是同一件物品，一次失败就能回收两次 → 白拿原料。</p>
     *
     * <p>注册完之后改是安全的：{@code ChanceOutput.getSingle()} 每次都返回 {@code stack.copy()}，
     * 机器、NEI、json 导出都读这一份，没有别处缓存数量。</p>
     */
    private static void doubleGoodOutput(GenericRecipe recipe) {
        if(recipe == null || recipe.outputItem == null) return;
        for(IOutput output : recipe.outputItem) {
            if(!(output instanceof ChanceOutputMulti)) continue;
            for(ChanceOutput entry : ((ChanceOutputMulti) output).pool) {
                if(entry.stack == null || isBroken(entry)) continue;
                entry.stack.stackSize *= 2;
                return;
            }
        }
    }
}
