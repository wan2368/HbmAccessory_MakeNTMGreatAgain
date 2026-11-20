# -*- coding: utf-8 -*-
"""
附属内容自检：拼音字典 + 中英 lang 键 + 贴图文件。

用途：每次新增物品 / 方块 / 流体 / 配方 / 成就之后跑一次。
  1) 拼音：所有中文名里的汉字都能在 pinyin.tsv 查到，否则拼音搜索会漏；
  2) lang：每个 setUnlocalizedName / setBlockName 都有 item./tile. 的 zh_CN 与 en_US 键，
     漏了游戏里就显示 `item.xxx.name`；
  3) 贴图：setTextureName / setBlockTextureName 指向的 png 真的存在，
     漏了游戏里就是紫黑格。

用法：
    python tools/pinyin_audit.py              # 只查拼音（老行为，退出码 1 = 有缺字）
    python tools/pinyin_audit.py --all        # 拼音 + lang + 贴图，全部检查
    python tools/pinyin_audit.py --assets     # 只查 lang + 贴图
    python tools/pinyin_audit.py --fix        # 用 pypinyin 自动补拼音字典
    python tools/pinyin_audit.py --verbose    # 打印每个问题的出处

依赖：--fix 需要 pypinyin（pip install pypinyin），其余无第三方依赖。
"""
from __future__ import print_function

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/java/com/makentmgreatagain")
LANG_DIR = os.path.join(ROOT, "src/main/resources/assets/makentmgreatagain/lang")
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/makentmgreatagain/textures")
TSV = os.path.join(ROOT, "src/main/resources/assets/makentmgreatagain/pinyin.tsv")
LANG_FILES = [
    os.path.join(LANG_DIR, "zh_CN.lang"),
    os.path.join(ROOT, "src/main/resources/assets/hbm/lang/zh_CN.lang"),
]
HAN = re.compile(u"[\u4e00-\u9fff]")

UNLOC = re.compile(r'\.\s*(?:setUnlocalizedName|setBlockName)\s*\(\s*"([^"]+)"')
TEXTURE = re.compile(r'\.\s*(?:setTextureName|setBlockTextureName)\s*\(\s*"(?:makentmgreatagain:)?([^"]+)"')


def strip_comments(code):
    """去掉 // 与 /* */ 注释，保留字符串内容。

    必须剥：ModItems / ModBlocks 的类 javadoc 里躺着 `powder_xxx`、`xxx` 这类模板示例，
    不剥会被当成真物品去查 lang，永远报缺。
    """
    out = []
    i, n = 0, len(code)
    while i < n:
        c = code[i]
        if c == "/" and i + 1 < n and code[i + 1] == "/":
            while i < n and code[i] != "\n":
                i += 1
        elif c == "/" and i + 1 < n and code[i + 1] == "*":
            i += 2
            while i + 1 < n and not (code[i] == "*" and code[i + 1] == "/"):
                i += 1
            i += 2
        elif c in ('"', "'"):
            quote = c
            out.append(c)
            i += 1
            while i < n:
                out.append(code[i])
                if code[i] == "\\":
                    i += 1
                    if i < n:
                        out.append(code[i])
                        i += 1
                    continue
                if code[i] == quote:
                    i += 1
                    break
                i += 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def java_sources():
    for dirpath, _dirnames, filenames in os.walk(SRC):
        for name in filenames:
            if name.endswith(".java"):
                path = os.path.join(dirpath, name)
                with io.open(path, encoding="utf-8", errors="replace") as f:
                    yield os.path.relpath(path, ROOT).replace("\\", "/"), strip_comments(f.read())


def read_tsv(path):
    order, table = [], {}
    if not os.path.exists(path):
        return order, table
    with io.open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            tab = line.find("\t")
            if tab <= 0:
                continue
            ch = line[0]
            readings = [r.strip().lower() for r in line[tab + 1:].split(",") if r.strip()]
            if ch not in table:
                order.append(ch)
                table[ch] = []
            for r in readings:
                if r not in table[ch]:
                    table[ch].append(r)
    return order, table


def collect_chars(paths):
    """返回 {汉字: [(文件, 行号, 该行内容)]}，只收汉字。"""
    found = {}
    for path in paths:
        if not os.path.exists(path):
            continue
        tag = os.path.basename(os.path.dirname(os.path.dirname(path)))
        with io.open(path, encoding="utf-8") as f:
            for ln, line in enumerate(f, 1):
                line = line.rstrip("\n")
                if not line or line.startswith("#") or "=" not in line:
                    continue
                for ch in HAN.findall(line.split("=", 1)[1]):
                    found.setdefault(ch, []).append((tag, ln, line.strip()[:100]))
    return found


def load_lang(name):
    path = os.path.join(LANG_DIR, name)
    table = {}
    if not os.path.exists(path):
        return table
    with io.open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            table[key] = value
    return table


def check_assets(verbose):
    """lang 键与贴图。返回 (问题数, 明细行列表)。"""
    problems = []
    names = []          # (unlocalizedName, 出处文件)
    textures = []       # (贴图名, 出处文件)
    for rel, code in java_sources():
        for m in UNLOC.finditer(code):
            names.append((m.group(1), rel))
        for m in TEXTURE.finditer(code):
            textures.append((m.group(1), rel))

    zh = load_lang("zh_CN.lang")
    en = load_lang("en_US.lang")

    for tag, table in (("zh", zh), ("en", en)):
        missing = []
        for name, rel in names:
            if "item." + name + ".name" in table or "tile." + name + ".name" in table:
                continue
            missing.append((name, rel))
        if missing:
            problems.append(u"[lang/%s] %d 个物品/方块没有名字（游戏里会显示键名）" % (tag, len(missing)))
            for name, rel in sorted(set(missing)):
                problems.append(u"    %s  <- %s" % (name, rel))

    have = set()
    if os.path.isdir(TEX_DIR):
        for dirpath, _dirnames, filenames in os.walk(TEX_DIR):
            for f in filenames:
                if f.endswith(".png"):
                    rel = os.path.relpath(os.path.join(dirpath, f), TEX_DIR).replace("\\", "/")
                    have.add(os.path.splitext(rel)[0])

    missing_tex = []
    for tex, rel in textures:
        # 只检查本模组的贴图；minecraft: / hbm: 前缀已在正则里排除不到，这里再兜一层
        if ":" in tex:
            continue
        if tex in have or "blocks/" + tex in have or "items/" + tex in have:
            continue
        missing_tex.append((tex, rel))
    if missing_tex:
        problems.append(u"[texture] %d 个贴图引用找不到 png" % len(missing_tex))
        for tex, rel in sorted(set(missing_tex)):
            problems.append(u"    %s  <- %s" % (tex, rel))

    print(u"物品/方块名: %d   贴图引用: %d" % (len(set(n for n, _ in names)), len(set(t for t, _ in textures))))
    return len(problems), problems


def main(argv):
    verbose = "--verbose" in argv or "-v" in argv
    fix = "--fix" in argv
    check_all = "--all" in argv
    assets_only = "--assets" in argv
    do_pinyin = not assets_only

    if hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8")
        except Exception:
            pass

    exit_code = 0
    missing = []

    if do_pinyin:
        order, table = read_tsv(TSV)
        found = collect_chars(LANG_FILES)
        missing = sorted(ch for ch in found if ch not in table)

        print(u"[拼音] 字典字数: %d" % len(table))
        print(u"[拼音] lang 汉字数: %d" % len(found))
        print(u"[拼音] 缺字: %d" % len(missing))

        if missing:
            exit_code = 1
            print("-" * 60)
            for ch in missing:
                hits = found[ch]
                print(u"%s  出现 %d 次" % (ch, len(hits)))
                if verbose:
                    for tag, ln, text in hits[:3]:
                        print(u"    [%s:%d] %s" % (tag, ln, text))

        if fix and missing:
            try:
                from pypinyin import pinyin, Style
            except ImportError:
                print(u"\n需要 pypinyin 才能自动补字: pip install pypinyin")
                return 2

            added = 0
            for ch in missing:
                try:
                    rs = pinyin(ch, style=Style.NORMAL, heteronym=True)[0]
                except Exception:
                    rs = []
                rs = [r.strip().lower() for r in rs]
                rs = [r for r in rs if r and r.isalpha() and r.isascii()]
                if not rs:
                    print(u"  跳过（无读音）: %s" % ch)
                    continue
                order.append(ch)
                table[ch] = rs
                added += 1
                print(u"  补入: %s -> %s" % (ch, ",".join(rs)))

            lines = []
            for ch in order:
                rs = table.get(ch)
                if not rs:
                    continue
                lines.append(u"%s\t%s" % (ch, ",".join(rs)))
            with io.open(TSV, "w", encoding="utf-8", newline="\n") as f:
                f.write(u"\n".join(lines) + u"\n")
            print(u"\n已写入 %s，新增 %d 字，总计 %d 字" % (TSV, added, len(lines)))
            return 0

    if check_all or assets_only:
        if do_pinyin:
            print("")
        count, problems = check_assets(verbose)
        if problems:
            exit_code = 1
            print("-" * 60)
            for line in problems:
                print(line)
        else:
            print(u"[资源] lang 键与贴图全部齐全")

    return exit_code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
