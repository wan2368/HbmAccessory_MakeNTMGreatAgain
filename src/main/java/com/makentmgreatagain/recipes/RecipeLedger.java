package com.makentmgreatagain.recipes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.main.MainRegistry;
import com.makentmgreatagain.MakeNTMGreatAgain;

/**
 * 附属配方账本：让「更新附属」和「玩家改配方」不再互相打架。
 *
 * <h2>要解决的两个真实问题</h2>
 *
 * <p><b>问题一：玩家的 json 会把附属旧配方冻住。</b>
 * HBM 的 {@code SerializableRecipe.initialize()} 对每张表先 {@code deleteRecipes()}，
 * 然后「配置文件存在就读文件，不存在才 registerDefaults + 通知监听器」。
 * 而 {@code writeTemplateFile()} 是在 registerDefaults 与监听器执行之后写的，
 * 所以 {@code config/hbmRecipes/_xxx.nt} 模板里**含有当时的附属配方**。
 * 玩家把模板改名启用后，附属配方就以 json 形式固化在硬盘上了；
 * 之后附属升级、某条配方数值变了，玩家那份 json 仍然是旧数值 ——
 * postInit 的兜底注入按名字判重，只会跳过，永远升不上去。</p>
 *
 * <p><b>问题二：反过来，更新不该踩玩家的改动。</b>
 * 玩家可能主动改过某条附属配方（调数值、换产物）。如果升级时无脑「全删重建」，
 * 玩家改的东西就没了。</p>
 *
 * <h2>做法：名字前缀 + 内容指纹 + 逐表对账</h2>
 *
 * <p>本模组的配方名统一是 {@code makentmgreatagain.*}（唯一例外 {@code crucible.heliumalloy}，
 * 见 {@link #EXTRA_OWNED}），所以「哪些配方是我的」是确定的，不靠猜。</p>
 *
 * <p>每次启动分三步：</p>
 * <ol>
 *   <li><b>对账（{@link #begin()}）</b>：扫所有 {@link GenericRecipes} 表，<b>逐条</b>检查自有配方：
 *       <ul>
 *         <li>账本有记录且指纹一致 → 这条自从上次启动后没被动过 → 摘除，
 *             等下由 postInit 重建为当前版本；</li>
 *         <li>账本无记录、登记表不符或指纹不符 → 玩家改过 → <b>这一条留在原地</b>
 *             （玩家改动优先），并且从此账本不再认领它；其余没被动的条目照常升级。
 *             同一张表里可以既有升级又有保留，互不影响。</li>
 *       </ul>
 *       <b>一个已知边界</b>：{@code ModRecipes} 里有
 *       {@code if(!hasGenericRecipe(A)) { 注册A; 注册B; 注册C; }} 这种批量判重块——
 *       块内任何一条被玩家保护时，块条件不成立，同块被摘除的兄弟条目就回不来，
 *       {@link #end()} 会把它们回滚成旧版（宁旧勿丢，下次启动再试）。
 *       整块都干净时，块内所有条目都能正常升级。崩不了：所有注册都走
 *       {@link RecipeRegistrar#add}，已存在的名字直接跳过。</li>
 *   <li><b>重建</b>：{@link com.makentmgreatagain.init.ModRecipes#injectAllFallback()}
 *       照常跑，被摘掉的名字判重通过 → 注册成当前版本；被玩家保护的条目
 *       {@link RecipeRegistrar#add} 判重跳过，玩家的版本原样留在表里。</li>
 *   <li><b>收尾（{@link #end()}）</b>：
 *       <ul>
 *         <li>摘掉但在重建中没能回来的（依赖缺失 / 代码抛异常）→ <b>回滚旧对象</b>，
 *             宁可停在旧版也不让玩家丢配方。</li>
 *         <li>执行 {@code options.disabled} 里的禁用名单。</li>
 *         <li>把当前所有自有配方的指纹写回账本。</li>
 *       </ul></li>
 * </ol>
 *
 * <h2>不含在这套机制里的表</h2>
 *
 * <p>固化机 / 液化机 / 金属电解机这类老式表（{@code SolidificationRecipes.recipes} 等
 * {@code Map} 形式的）不是 {@link GenericRecipes}，配方没有名字，无法逐条指纹。
 * 它们本来就只做「键存在就跳过」，天然只补不覆盖，所以不需要也不应该进账本。</p>
 *
 * <h2>玩家能用它做什么</h2>
 *
 * <p>账本在 {@code config/MNGA/recipe_ledger.json}，可手改：</p>
 * <pre>
 * {
 *   "ledgerVersion": 1,
 *   "addonVersion": "1.0.0-beta.6",
 *   "options": {
 *     "forceUpgrade": false,          // true = 无视玩家改动，强制刷新成附属当前版本
 *     "disabled": []                  // 填配方全名可永久禁用某条（替代「删掉再被补回」的无效操作）
 *   },
 *   "recipes": { "makentmgreatagain.xxx": { "table": "...", "hash": "...", "version": "..." } }
 * }
 * </pre>
 *
 * <p><b>首次启动只建基线，不重建。</b>没有历史指纹时无法区分「代码生成的」和「玩家改的」，
 * 此时任何摘除都可能误伤。所以第一次只把当前表内容记成基线，从第二次启动起才有升级语义。
 * 整合包如果预置了改过的 json，第一次启动记录的就会是那份内容，升级自然绕开它 ——
 * 想强制刷新就把 {@code forceUpgrade} 改成 true。</p>
 *
 * <p>线程模型：只在 {@code FMLPostInitializationEvent} 单线程执行一次，无并发访问。</p>
 */
public final class RecipeLedger {

    private static final Logger LOG = LogManager.getLogger("MNGA-Recipes");

    /** 附属配方名统一前缀。改这个名字等于把所有权声明扔掉，别动。 */
    private static final String PREFIX = "makentmgreatagain.";

    /** 不带前缀但确实属于本模组的配方名（历史原因，改名会让老存档 json 里留下孤儿条目）。 */
    private static final Set<String> EXTRA_OWNED = new LinkedHashSet<String>();
    static {
        EXTRA_OWNED.add("crucible.heliumalloy");
    }

    private static final int LEDGER_VERSION = 1;
    private static final String FILE_NAME = "recipe_ledger.json";

    private static final RecipeLedger INSTANCE = new RecipeLedger();

    public static RecipeLedger get() {
        return INSTANCE;
    }

    /** 账本里一条配方的记录。 */
    private static final class Entry {
        final String name;
        final String table;
        final String hash;
        final String version;

        Entry(String name, String table, String hash, String version) {
            this.name = name;
            this.table = table;
            this.hash = hash;
            this.version = version;
        }
    }

    private File file;
    /** 账本是否成功读到（文件存在且解析没炸）。false 时等同于首次运行。 */
    private boolean haveBaseline = false;
    /** 本次是「建立基线」的那一次（日志措辞与 end() 的统计口径都依赖它）。 */
    private boolean baselineOnly = false;

    private boolean forceUpgrade = false;
    private final Set<String> disabled = new LinkedHashSet<String>();
    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();

    /**
     * 本次对账判定为「玩家资产」的条目（账本无记录、登记表不符或指纹不符）。
     * 这些条目留在原地不动，并且 {@link #doEnd()} 写回时不再认领——
     * 一旦玩家改过某条，所有权就归玩家，附属后续升级不再碰它。
     */
    private final Set<String> playerOwned = new LinkedHashSet<String>();

    /** 本次被摘除的旧对象，end() 时若没能重建回来就回滚。 */
    private final Map<String, GenericRecipe> detachedRecipes = new LinkedHashMap<String, GenericRecipe>();
    private final Map<String, GenericRecipes> detachedTables = new LinkedHashMap<String, GenericRecipes>();

    private int detachCount = 0;
    private int statNew = 0;
    private int statProtected = 0;
    private int statRolledBack = 0;
    private int statDisabled = 0;

    private RecipeLedger() {
    }

    // =====================================================================================
    //  生命周期
    // =====================================================================================

    /**
     * 在 {@code ModRecipes.injectAllFallback()} <b>之前</b>调用：对账并摘除可安全重建的旧版本。
     *
     * <p>任何异常都在这里吃掉并降级为「什么都不做」—— 配方账本坏掉不能连带把整个模组的
     * 配方注入搞崩。</p>
     */
    public void begin() {
        try {
            doBegin();
        } catch (Throwable t) {
            LOG.warn("配方账本对账失败，本次按「全部跳过」处理（配方保持现有内容）：" + t, t);
            entries.clear();
            detachedRecipes.clear();
            detachedTables.clear();
            haveBaseline = false;
        }
    }

    /**
     * 在 {@code ModRecipes.injectAllFallback()} <b>之后</b>调用：回滚、执行禁用名单、写回账本。
     */
    public void end() {
        try {
            doEnd();
        } catch (Throwable t) {
            LOG.warn("配方账本收尾失败（配方本身已生效，只是下次启动会重新对账）：" + t, t);
        }
    }

    private void doBegin() {
        File dir = new File(MainRegistry.configDir, "MNGA");
        if (!dir.exists() && !dir.mkdirs()) {
            LOG.warn("无法创建配置目录 " + dir.getAbsolutePath() + "，配方账本停用");
            return;
        }
        file = new File(dir, FILE_NAME);
        haveBaseline = read();

        if (!haveBaseline) {
            // 首次：只建基线。end() 会把当前内容写进账本。
            baselineOnly = true;
            LOG.info("配方账本首次建立基线（" + countOwned() + " 条附属配方）。"
                    + "本次不改动任何配方；从下次启动起才会比对内容并升级。");
            return;
        }

        // 逐条对账
        playerOwned.clear();
        for (SerializableRecipe handler : SerializableRecipe.recipeHandlers) {
            if (!(handler instanceof GenericRecipes)) continue;
            GenericRecipes table = (GenericRecipes) handler;
            String tableName = handler.getFileName();

            List<String> own = new ArrayList<String>();
            for (Object o : new ArrayList<Object>(table.recipeNameMap.keySet())) {
                String name = (String) o;
                if (owns(name)) own.add(name);
            }
            if (own.isEmpty()) continue;

            int detachHere = 0;
            List<String> protectedHere = new ArrayList<String>();

            for (String name : own) {
                GenericRecipe recipe = (GenericRecipe) table.recipeNameMap.get(name);

                // 表里没有这条（玩家在 json 里删了，或本版本才新增）：
                // 没有可摘的对象，重建阶段按各自的代码路径照常走。
                if (recipe == null) continue;

                Entry e = entries.get(name);
                String reason = null;
                if (e == null) {
                    reason = "账本无记录（不是本模组上次写进去的）";
                } else if (!tableName.equals(e.table)) {
                    reason = "登记在另一张表 [" + e.table + "]";
                } else if (!fingerprint(handler, recipe).equals(e.hash)) {
                    reason = "内容与账本不一致（被改过）";
                }

                if (reason != null && !forceUpgrade) {
                    // 玩家改过的条目优先：留在原地不动，从此账本也不再认领它。
                    playerOwned.add(name);
                    statProtected++;
                    protectedHere.add(name + "（" + reason + "）");
                    continue;
                }

                detachedRecipes.put(name, recipe);
                detachedTables.put(name, table);
                detach(table, name);
                detachCount++;
                detachHere++;
            }

            if (detachHere > 0) {
                LOG.info("配方表 [" + tableName + "] 摘除 " + detachHere
                        + " 条旧版附属配方，等待重建为当前版本。");
            }
            if (!protectedHere.isEmpty()) {
                LOG.warn("配方表 [" + tableName + "] 保留 " + protectedHere.size()
                        + " 条玩家改过的附属配方，本次不升级：" + protectedHere);
                LOG.warn("  → 想强制刷成附属当前版本：把 " + FILE_NAME
                        + " 里的 options.forceUpgrade 改成 true。");
            }
        }

        if (statProtected == 0 && detachCount == 0) {
            LOG.info("配方账本：" + countOwned() + " 条附属配方全部与基线一致，无需升级。");
        }
    }

    private void doEnd() {
        // ① 回滚：摘掉了却没重建回来（依赖缺失、代码异常、或该方法本次没跑）
        for (Map.Entry<String, GenericRecipe> e : detachedRecipes.entrySet()) {
            String name = e.getKey();
            GenericRecipes table = detachedTables.get(name);
            if (table == null || table.recipeNameMap.containsKey(name)) continue;
            registerRaw(table, e.getValue());
            statRolledBack++;
            LOG.warn("附属配方 " + name + " 本次未能重建，已回滚为升级前的版本"
                    + "（通常是它的前置物品/流体在当前环境不存在）。");
        }
        detachedRecipes.clear();
        detachedTables.clear();

        // ② 禁用名单：显式声明比「玩家删掉再补回」可靠。
        //    遍历所有表而不是先查索引：禁用名单可以提前写进账本，哪怕这条配方是本版本才新增的。
        if (!disabled.isEmpty()) {
            for (String name : disabled) {
                for (SerializableRecipe handler : SerializableRecipe.recipeHandlers) {
                    if (!(handler instanceof GenericRecipes)) continue;
                    if (detach((GenericRecipes) handler, name)) {
                        statDisabled++;
                        LOG.info("按禁用名单移除附属配方 " + name);
                    }
                }
            }
        }

        // ③ 写回：只记自有名字。新增的会在这一步入账；被作者删掉的自然消失；
        //    玩家改过的（playerOwned）不再认领——所有权已经归玩家，之后升级也不碰。
        Map<String, Entry> next = new LinkedHashMap<String, Entry>();
        for (SerializableRecipe handler : SerializableRecipe.recipeHandlers) {
            if (!(handler instanceof GenericRecipes)) continue;
            GenericRecipes table = (GenericRecipes) handler;
            String tableName = handler.getFileName();
            for (Object o : new ArrayList<Object>(table.recipeNameMap.keySet())) {
                String name = (String) o;
                if (!owns(name)) continue;
                if (disabled.contains(name)) continue;
                if (playerOwned.contains(name)) continue;
                GenericRecipe recipe = (GenericRecipe) table.recipeNameMap.get(name);
                Entry old = entries.get(name);
                if (old == null) statNew++;
                next.put(name, new Entry(name, tableName, fingerprint(handler, recipe), MakeNTMGreatAgain.VERSION));
            }
        }
        entries.clear();
        entries.putAll(next);
        write();
        playerOwned.clear();

        // ④ 自检：列表里的同名幽灵条目。
        //    HBM 的 register() 是「先入 recipeOrderedList，再查 recipeNameMap」，
        //    撞名抛出的异常一旦被上层吃掉（ModRecipes.step），表里就留下两条同名配方，
        //    之后任何按列表重建的地方（大型催化化工厂、NEI 扫描）都会再抛一次而崩游戏。
        //    这里只报警不改表：真正的修法是在注册处走 RecipeRegistrar.add()，别在这里动别人的表。
        checkGhostEntries();

        if (baselineOnly) {
            LOG.info("配方账本已建立基线：" + entries.size() + " 条附属配方入库，禁用 "
                    + statDisabled + " 条。");
        } else {
            LOG.info("配方账本：摘除旧版 " + detachCount + " 条、新增 " + statNew
                    + " 条、保护（玩家改动）" + statProtected + " 条、禁用 " + statDisabled
                    + " 条、回滚 " + statRolledBack + " 条；账本现有 " + entries.size() + " 条。");
        }
    }

    // =====================================================================================
    //  所有权与指纹
    // =====================================================================================

    /**
     * 自检：每张 {@link GenericRecipes} 表的 {@code recipeOrderedList} 里是否有同名条目。
     *
     * <p>只报警，不改表 —— 动别人的表只会让状态更难推断。真正的修法在注册处：</p>
     *
     * <pre>
     * // 错：撞名时 HBM 已经把对象塞进列表了，异常被吃掉就留下幽灵条目
     * ChemicalPlantRecipes.INSTANCE.register(recipe);
     * // 对：先判重，已存在就跳过
     * RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, recipe);
     * </pre>
     */
    private static void checkGhostEntries() {
        for (SerializableRecipe handler : SerializableRecipe.recipeHandlers) {
            if (!(handler instanceof GenericRecipes)) continue;
            GenericRecipes table = (GenericRecipes) handler;

            Map<String, Integer> seen = new LinkedHashMap<String, Integer>();
            for (Object o : table.recipeOrderedList) {
                if (!(o instanceof GenericRecipe)) continue;
                String n = ((GenericRecipe) o).getInternalName();
                if (n == null) continue;
                Integer c = seen.get(n);
                seen.put(n, c == null ? Integer.valueOf(1) : Integer.valueOf(c.intValue() + 1));
            }

            for (Map.Entry<String, Integer> e : seen.entrySet()) {
                if (e.getValue().intValue() <= 1) continue;
                LOG.warn("配方表 [" + handler.getFileName() + "] 里有 " + e.getValue()
                        + " 条同名配方 " + e.getKey() + "（幽灵条目）：注册处没判重，"
                        + "异常被 step() 吃掉后列表就被污染了。请把那处 register 换成 RecipeRegistrar.add()。");
            }
        }
    }

    /** 这条配方名是否属于本模组。 */
    public boolean owns(String name) {
        if (name == null) return false;
        return name.startsWith(PREFIX) || EXTRA_OWNED.contains(name);
    }

    /**
     * 内容指纹：用 HBM 自己的序列化器把配方写成一个紧凑 json 串再取 SHA-1。
     *
     * <p>为什么不用「代码生成时的参数」当指纹：玩家 json 读回来的对象是反序列化的产物，
     * 与代码里 new 出来的对象在「怎么序列化」这一层是对称的（HBM 靠它做服务端→客户端
     * 配方同步，必须对称），直接比字段反而要处理 AStack / IOutput / FluidStack 各种分支。
     * 用官方序列化器等于借用这份对称性。</p>
     *
     * <p>返回 {@code "ERR"} 表示序列化失败（例如某个物品已在注册表里查不到），
     * 这种条目会被当成「与基线不一致」而受到保护，不会被误删。</p>
     */
    private static String fingerprint(SerializableRecipe handler, GenericRecipe recipe) {
        if (handler == null || recipe == null) return "ERR";
        try {
            StringWriter sw = new StringWriter();
            JsonWriter w = new JsonWriter(sw);
            w.setIndent("");
            w.beginObject();
            handler.writeRecipe(recipe, w);
            w.endObject();
            w.close();
            return sha1(sw.toString());
        } catch (Throwable t) {
            return "ERR";
        }
    }

    private static String sha1(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                int v = b & 0xFF;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            return "ERR";
        }
    }

    // =====================================================================================
    //  表操作
    // =====================================================================================

    /**
     * 从表里摘掉一条配方，**连同它在所有副索引里的痕迹**。
     *
     * <p>漏掉任何一处都会留下残影：{@code nameToRecipeGlobal} 会让 NEI/合成查找拿到
     * 已失效的对象，{@code autoSwitchGroups} 会让机器在自动切换时切到幽灵配方，
     * {@code blueprintPools} 里留着名字则会让蓝图出现重复条目。</p>
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static boolean detach(GenericRecipes table, String name) {
        Object recipe = table.recipeNameMap.remove(name);
        if (recipe == null) return false;
        table.recipeOrderedList.remove(recipe);
        com.makentmgreatagain.compat.HBMVersionCompat.removeGlobalIndex(name);
        for (Object listObj : table.autoSwitchGroups.values()) {
            ((List) listObj).remove(recipe);
        }
        for (Object listObj : GenericRecipes.blueprintPools.values()) {
            ((List) listObj).remove(name);
        }
        return true;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void registerRaw(GenericRecipes table, GenericRecipe recipe) {
        try {
            table.register(recipe);
        } catch (Throwable t) {
            LOG.warn("回滚 " + recipe.getInternalName() + " 失败：" + t);
        }
    }

    private int countOwned() {
        int n = 0;
        for (SerializableRecipe handler : SerializableRecipe.recipeHandlers) {
            if (!(handler instanceof GenericRecipes)) continue;
            GenericRecipes table = (GenericRecipes) handler;
            for (Object o : table.recipeNameMap.keySet()) {
                if (owns((String) o)) n++;
            }
        }
        return n;
    }

    // =====================================================================================
    //  账本读写
    // =====================================================================================

    private boolean read() {
        if (file == null || !file.isFile()) return false;
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            JsonElement rootEl = new JsonParser().parse(reader);
            if (rootEl == null || !rootEl.isJsonObject()) return false;
            JsonObject root = rootEl.getAsJsonObject();

            if (root.has("options") && root.get("options").isJsonObject()) {
                JsonObject opt = root.getAsJsonObject("options");
                if (opt.has("forceUpgrade")) {
                    forceUpgrade = opt.get("forceUpgrade").getAsBoolean();
                }
                if (opt.has("disabled") && opt.get("disabled").isJsonArray()) {
                    for (JsonElement el : opt.getAsJsonArray("disabled")) {
                        if (el != null && !el.isJsonNull()) disabled.add(el.getAsString());
                    }
                }
            }

            if (!root.has("recipes") || !root.getAsJsonObject("recipes").isJsonObject()) {
                return false;
            }
            JsonObject recs = root.getAsJsonObject("recipes");
            for (Map.Entry<String, JsonElement> e : recs.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject obj = e.getValue().getAsJsonObject();
                String table = obj.has("table") ? obj.get("table").getAsString() : "?";
                String hash = obj.has("hash") ? obj.get("hash").getAsString() : "ERR";
                String version = obj.has("version") ? obj.get("version").getAsString() : "?";
                entries.put(e.getKey(), new Entry(e.getKey(), table, hash, version));
            }
            return !entries.isEmpty();
        } catch (Throwable t) {
            LOG.warn("配方账本 " + file.getName() + " 读取失败，本次按首次运行处理：" + t);
            return false;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) { }
            }
        }
    }

    private void write() {
        if (file == null) return;
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            JsonWriter w = new JsonWriter(writer);
            w.setIndent("  ");
            w.beginObject();
            w.name("ledgerVersion").value(LEDGER_VERSION);
            w.name("addonVersion").value(MakeNTMGreatAgain.VERSION);
            w.name("_comment").value("附属配方账本。删掉本文件等于把下次启动当成首次运行（只建基线，不升级）。"
                    + "options.forceUpgrade=true 可强制刷新附属配方；options.disabled 可永久禁用指定配方名。");

            w.name("options").beginObject();
            w.name("forceUpgrade").value(forceUpgrade);
            w.name("disabled").beginArray();
            for (String d : disabled) w.value(d);
            w.endArray();
            w.endObject();

            w.name("recipes").beginObject();
            for (Entry e : entries.values()) {
                w.name(e.name).beginObject();
                w.name("table").value(e.table);
                w.name("hash").value(e.hash);
                w.name("version").value(e.version);
                w.endObject();
            }
            w.endObject();

            w.endObject();
            w.close();
        } catch (Throwable t) {
            LOG.warn("配方账本写入失败 " + file.getAbsolutePath() + "：" + t);
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (Exception ignored) { }
            }
        }
    }

    // =====================================================================================
    //  给外部查询用的小接口
    // =====================================================================================

    /** 某条配方是否被玩家列进了禁用名单（addXxx 里可选地用一下，效果同 end() 阶段摘除）。 */
    public boolean isDisabled(String name) {
        return disabled.contains(name);
    }

    /** 账本里是否已有这条配方的基线（调试 / 日志用）。 */
    public boolean hasBaseline(String name) {
        return entries.containsKey(name);
    }
}
