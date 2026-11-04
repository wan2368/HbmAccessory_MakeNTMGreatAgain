#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
附属版本号 / 本体版本号 统一修改工具。

为什么需要它：
  附属版本号散落在 4 个文件里，手改必漏（之前 build.gradle 写 beta.3、
  bat 里却还写着 beta.1）。这个脚本一次改四处，并做交叉校验。

用法：
  python tools/bump_version.py                     只看当前版本 + 一致性检查
  python tools/bump_version.py 1.0.0-beta.4        把附属版本改成 1.0.0-beta.4
  python tools/bump_version.py --hbm 1.0.28 --build 5900
                                                   改本体版本（gradle.properties）

退出码 0 = 成功，1 = 有错。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# (文件, 正则) —— 正则第 1 组是前缀，第 3 组是后缀，中间是被替换的版本号。
# 用 "modid" 锚点定位，保证不会把 mcmod.info 里 hbm 那条的 ${version} 改掉。
VER_TARGETS = [
    (
        "build.gradle",
        r'(ext\.addon_version\s*=\s*")[^"]*(")',
        "ext.addon_version",
    ),
    (
        "src/main/java/com/makentmgreatagain/MakeNTMGreatAgain.java",
        r'(String\s+VERSION\s*=\s*")[^"]*(")',
        "MakeNTMGreatAgain.VERSION",
    ),
    (
        "src/addon/resources/mcmod.info",
        r'("modid"\s*:\s*"makentmgreatagain"\s*,[\s\S]*?"version"\s*:\s*")[^"]*(")',
        "src/addon/resources/mcmod.info",
    ),
    (
        "src/main/resources/mcmod.info",
        r'("modid"\s*:\s*"makentmgreatagain"\s*,[\s\S]*?"version"\s*:\s*")[^"]*(")',
        "src/main/resources/mcmod.info (第 2 条)",
    ),
]


def read(path: Path) -> str:
    # newline='' 保留原文件的 LF，避免整份文件变成 CRLF 产生巨大 diff
    with open(path, "r", encoding="utf-8", newline="") as fh:
        return fh.read()


def write(path: Path, text: str) -> None:
    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)


def current_versions() -> list:
    """[(label, rel, version or None, 命中次数)]"""
    result = []
    for rel, pattern, label in VER_TARGETS:
        path = ROOT / rel
        if not path.exists():
            result.append((label, rel, None, 0))
            continue
        text = read(path)
        ms = list(re.finditer(pattern, text))
        if not ms:
            result.append((label, rel, None, 0))
            continue
        m = ms[0]
        # 被替换区 = 第 1 组结束 ~ 第 2 组开始
        prefix_end = m.end(1)
        suffix_start = m.start(2)
        result.append((label, rel, text[prefix_end:suffix_start], len(ms)))
    return result


def check_json(rel: str) -> str | None:
    path = ROOT / rel
    try:
        raw = path.read_bytes()
        if any(b > 127 for b in raw):
            return "%s 含非 ASCII 字符（编码会被 expand 弄坏）" % rel
        json.loads(raw.decode("ascii"))
    except Exception as exc:  # noqa: BLE001
        return "%s 不是合法 JSON: %s" % (rel, exc)
    return None


def report() -> int:
    rows = current_versions()
    print("=" * 62)
    print("附属版本号现状（必须四处一致）")
    print("=" * 62)
    values = set()
    bad = False
    for label, rel, ver, hits in rows:
        mark = "  " if hits == 1 else "!!"
        if hits != 1:
            bad = True
        print("%s %-38s %s" % (mark, label, ver if ver else "<找不到>"))
        if ver:
            values.add(ver)
    if len(values) > 1:
        bad = True
        print("\n!! 四处版本号不一致：%s" % ", ".join(sorted(values)))
    for rel in ("src/main/resources/mcmod.info", "src/addon/resources/mcmod.info"):
        err = check_json(rel)
        if err:
            bad = True
            print("!! " + err)

    props = ROOT / "gradle.properties"
    if props.exists():
        text = read(props)
        mv = re.search(r"^mod_version=(.*)$", text, re.M)
        mb = re.search(r"^mod_build_number=(.*)$", text, re.M)
        print("\n本体版本（gradle.properties）")
        print("   mod_version      = %s" % (mv.group(1).strip() if mv else "?"))
        print("   mod_build_number = %s" % (mb.group(1).strip() if mb else "?"))
        print("   -> 产物名 HBM-NTM-[<mod_version>_X<build>].jar" if (mb and mb.group(1).strip())
              else "   -> 产物名 HBM-NTM-<mod_version>.jar")
    print()
    if bad:
        print("结果：有问题，见上面的 !! 行")
        return 1
    print("结果：四处一致，OK")
    return 0


def bump(new_ver: str) -> int:
    if not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z._+-]*", new_ver):
        print("版本号不合法：%r（只允许字母数字和 . _ + -）" % new_ver)
        return 1

    changed = 0
    for rel, pattern, label in VER_TARGETS:
        path = ROOT / rel
        if not path.exists():
            print("!! 缺文件：%s" % rel)
            return 1
        text = read(path)
        new_text, n = re.subn(pattern, lambda m: m.group(1) + new_ver + m.group(2), text)
        if n == 0:
            print("!! %s 里没找到版本字段（正则失配，可能文件被改过）" % rel)
            return 1
        if n > 1:
            print("!! %s 命中 %d 次，拒绝修改（正则太宽）" % (rel, n))
            return 1
        if new_text != text:
            write(path, new_text)
            changed += 1
        print("   %-44s -> %s" % (label, new_ver))

    print("\n已改 %d 个文件。" % changed)
    rc = report()
    if rc == 0:
        print("提示：改完要重新构建（客户端读的是 build/resources 里的副本）：")
        print("      .\\gradlew.bat build      （或直接跑 一键打包附属.bat）")
    return rc


def bump_hbm(mod_ver: str, build_no: str) -> int:
    if not re.fullmatch(r"[0-9][0-9A-Za-z._-]*", mod_ver):
        print("本体版本号不合法：%r" % mod_ver)
        return 1
    if build_no and not re.fullmatch(r"[0-9A-Za-z]+", build_no):
        print("build 号不合法：%r" % build_no)
        return 1

    path = ROOT / "gradle.properties"
    text = read(path)
    new_text, n1 = re.subn(r"^mod_version=.*$", "mod_version=" + mod_ver, text, flags=re.M)
    if not n1:
        print("!! gradle.properties 里没有 mod_version=")
        return 1
    if build_no:
        new_text, n2 = re.subn(r"^mod_build_number=.*$", "mod_build_number=" + build_no,
                               new_text, flags=re.M)
        if not n2:
            print("!! gradle.properties 里没有 mod_build_number=")
            return 1
    write(path, new_text)
    print("gradle.properties 已更新")
    return report()


def main(argv: list) -> int:
    args = argv[1:]
    if not args:
        return report()
    if args[0] in ("-h", "--help"):
        print(__doc__)
        return 0
    if args[0] == "--hbm":
        mod_ver = args[1] if len(args) > 1 else ""
        build_no = ""
        if "--build" in args:
            i = args.index("--build")
            build_no = args[i + 1] if len(args) > i + 1 else ""
        if not mod_ver or mod_ver.startswith("--"):
            print("用法: python tools/bump_version.py --hbm 1.0.28 --build 5900")
            return 1
        return bump_hbm(mod_ver, build_no)
    return bump(args[0])


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(errors="replace")
    except Exception:  # noqa: BLE001
        pass
    sys.exit(main(sys.argv))
