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

/**
 * 拼音搜索辅助：
 * - 原版 contains 仍然优先；
 * - 否则把中文名转成拼音，支持全拼、首字母以及两者混拼：
 *   冷却塔 -> lengqueta / lqt / lengqt / lqueta。
 */
public final class PinyinSearch {

    private static final Map<Character, String[]> DICT = new HashMap<Character, String[]>();
    private static final Map<String, List<String[][]>> CACHE = new HashMap<String, List<String[][]>>();
    private static boolean loaded = false;

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
            char c = s.charAt(i);
            if(c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
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
            } else if(!current.isEmpty()) {
                runs.add(current.toArray(new String[0][]));
                current = new ArrayList<String[]>();
            }
        }
        if(!current.isEmpty()) runs.add(current.toArray(new String[0][]));

        if(CACHE.size() > 2048) CACHE.clear();
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
                    if(option.isEmpty()) continue;
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
        loaded = true;

        try {
            InputStream in = PinyinSearch.class.getResourceAsStream("/assets/makentmgreatagain/pinyin.tsv");
            if(in == null) return;

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
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
            reader.close();
        } catch(Throwable ignored) {
        }
    }
}
