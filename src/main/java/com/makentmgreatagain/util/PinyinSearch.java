package com.makentmgreatagain.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.makentmgreatagain.MakeNTMGreatAgain;

/**
 * 拼音搜索：原版 {@code contains} 优先，否则把中文名转拼音再匹配，
 * 支持全拼、首字母与混拼（冷却塔 → lengqueta / lqt / lengqt / lqueta）。
 *
 * <p><b>谁在用</b>：{@code core/PinyinSearchTransformer} 把
 * {@code com.hbm.inventory.gui.*}、{@code container.*} 与 {@code GenericRecipe} 里的
 * {@code String.contains} 换成这里。<b>NEI 不在覆盖范围</b>（它走
 * {@code Pattern.matcher().find()}），所以 NEI 里搜不到拼音是预期行为。</p>
 *
 * <p><b>字典</b>：{@code assets/makentmgreatagain/pinyin.tsv}，GB2312 全集 6765 字。
 * 缺字由 {@link #SKIP} 占位兜底（不断段、可跳过）。新增物品必须先有 zh_CN.lang 中文名，
 * 再跑 {@code python tools/pinyin_audit.py} 补字，否则那个字永远匹配不上。</p>
 *
 * <p>只处理纯字母查询；含数字/符号的查询原样交回原版 {@code contains}。</p>
 */
public final class PinyinSearch {

    private static final Map<Character, String[]> DICT = new HashMap<Character, String[]>();
    private static final Map<String, List<String[][]>> CACHE = new HashMap<String, List<String[][]>>();
    private static boolean loaded = false;

    /** 字典里没有收录的汉字占位：保留在 run 里但不消耗查询字符，避免一个字断掉整段匹配 */
    private static final String[] SKIP = new String[] { "" };

    private PinyinSearch() {
    }

    public static boolean contains(String haystack, CharSequence needle) {
        if(haystack == null || needle == null) return false;
        if(haystack.contains(needle)) return true;

        String query = needle.toString().toLowerCase(Locale.ROOT).trim();
        if(query.isEmpty()) return false;

        // 只处理纯字母查询；其它字符交给原版 contains
        for(int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if(c < 'a' || c > 'z') return false;
        }

        if(!hasChinese(haystack)) return false;
        ensureLoaded();

        for(String[][] run : tokenize(haystack)) {
            if(matchRun(run, query)) return true;
        }
        return false;
    }

    private static boolean hasChinese(String s) {
        for(int i = 0; i < s.length(); i++) {
            if(isChinese(s.charAt(i))) return true;
        }
        return false;
    }

    private static boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private static boolean isAsciiWordChar(char c) {
        // 只把英文字母当作读音链的断点；数字和标点透明（Mk4 / 氟化铀-235 / 中华牌"香烟）
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static List<String[][]> tokenize(String s) {
        List<String[][]> cached = CACHE.get(s);
        if(cached != null) return cached;

        List<String[][]> runs = new ArrayList<String[][]>();
        List<String[]> current = new ArrayList<String[]>();

        for(int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String[] options = DICT.get(c);

            if(options != null) {
                current.add(options);
            } else if(isChinese(c)) {
                // 生僻字漏收时不断段，仅作可跳过占位，保证前后汉字仍能连起来搜
                current.add(SKIP);
            } else if(isAsciiWordChar(c)) {
                // 英文单词是真的分隔（PO环氧丙烷），数字与标点不切段
                if(!current.isEmpty()) {
                    runs.add(current.toArray(new String[0][]));
                    current = new ArrayList<String[]>();
                }
            }
            // 其余标点、空格、全角符号都当透明，不切断读音串（"中华牌"香烟 / （粗品））
        }
        if(!current.isEmpty()) runs.add(current.toArray(new String[0][]));

        if(CACHE.size() > 8192) CACHE.clear();
        CACHE.put(s, runs);
        return runs;
    }

    private static boolean matchRun(String[][] run, String query) {
        for(int start = 0; start < run.length; start++) {
            if(matchFrom(run, start, query)) return true;
        }
        return false;
    }

    private static boolean matchFrom(String[][] run, int start, String query) {
        boolean[][] dp = new boolean[run.length + 1][query.length() + 1];
        dp[start][0] = true;

        for(int i = start; i < run.length; i++) {
            for(int pos = 0; pos <= query.length(); pos++) {
                if(!dp[i][pos]) continue;

                for(String option : run[i]) {
                    if(option.isEmpty()) {
                        // 未知字：允许跨过它继续匹配后面的读音
                        dp[i + 1][pos] = true;
                        continue;
                    }
                    if(query.startsWith(option, pos)) {
                        dp[i + 1][pos + option.length()] = true;
                    }
                }
            }

            // 查询已经完整匹配到某个前缀，视为 contains 成功
            if(dp[i + 1][query.length()]) return true;
        }

        return false;
    }

    private static synchronized void ensureLoaded() {
        if(loaded) return;

        InputStream in = null;
        try {
            in = PinyinSearch.class.getResourceAsStream("/assets/makentmgreatagain/pinyin.tsv");
            if(in == null) {
                // 资源缺失是确定性的，标记已加载，避免每次查询都白找一遍
                loaded = true;
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            try {
                String line;
                while((line = reader.readLine()) != null) {
                    if(line.isEmpty()) continue;
                    int tab = line.indexOf('\t');
                    if(tab <= 0) continue;

                    char ch = line.charAt(0);
                    String[] readings = line.substring(tab + 1).split(",");
                    List<String> options = new ArrayList<String>();

                    for(String reading : readings) {
                        reading = reading.trim().toLowerCase(Locale.ROOT);
                        if(reading.isEmpty()) continue;
                        options.add(reading);
                        String initial = reading.substring(0, 1);
                        if(!options.contains(initial)) options.add(initial);
                    }

                    DICT.put(ch, options.toArray(new String[0]));
                }
            } finally {
                // 无论解析是否出错都要关掉流，否则文件句柄泄漏
                reader.close();
            }

            // 只有载入完整成功才置位；中途出错下次查询还能重试
            loaded = true;

            try {
                MakeNTMGreatAgain.LOG.info("[拼音] 搜索字典载入完成：" + DICT.size() + " 字");
            } catch(Throwable ignored) {
            }
        } catch(Throwable ignored) {
        }
    }
}
