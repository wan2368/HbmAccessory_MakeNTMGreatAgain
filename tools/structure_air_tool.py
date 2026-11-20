#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HBM NBT 结构文件（.nbt）空气填充 / 体检工具。

为什么需要它
------------
HBM 的 `NBTStructure.saveArea()` 导出时把「空气」加进黑名单直接跳过，
所以 .nbt 里默认**没有任何空气条目**；而世界生成时遇到「没有记录的格子」是
`continue`（保留原地形），不会挖空。结果就是：埋在地下的遗迹被土填满。

要让某个格子变成空气，必须显式记录一条 `minecraft:air`。
本工具就是把结构外接盒里所有「没记录」的格子补成 air 条目。

⚠️ 它是「整盒填充」：盒子范围内所有空格都会被强制挖成空气。
   对规整的方形建筑，外接盒 ≈ 建筑外壳，正好等于「挖空内部」；
   对 L 形 / 不规则建筑，盒子四角会在地形上多挖出缺口。
   需要精确控制时，用创世神只选内部空腔做 `//replace air <wand_air 的ID>`，
   本工具用 `--info` / `--dry-run` 复核即可。

用法
----
    python tools/structure_air_tool.py structures/ruin_outpost.nbt --info
    python tools/structure_air_tool.py structures/ruin_outpost.nbt --dry-run
    python tools/structure_air_tool.py structures/ruin_outpost.nbt
    python tools/structure_air_tool.py structures/ruin_outpost.nbt -o fixed.nbt

无外部依赖，只用标准库。
"""

import argparse
import gzip
import os
import shutil
import struct
import sys

# ---------------------------------------------------------------- NBT 基础类型
TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG = 0, 1, 2, 3, 4
TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING = 5, 6, 7, 8
TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY = 9, 10, 11

TAG_NAMES = {
    0: "End", 1: "Byte", 2: "Short", 3: "Int", 4: "Long", 5: "Float",
    6: "Double", 7: "ByteArray", 8: "String", 9: "List", 10: "Compound",
    11: "IntArray",
}


class Tag:
    """一个 NBT 标签：类型 + 值。List 的值是 (元素类型, [Tag...])。"""

    __slots__ = ("tid", "value")

    def __init__(self, tid, value):
        self.tid = tid
        self.value = value

    def __repr__(self):
        return "Tag(%s)" % TAG_NAMES.get(self.tid, self.tid)


def read_utf(f):
    n = struct.unpack(">H", f.read(2))[0]
    return f.read(n).decode("utf-8", "replace")


def write_utf(f, s):
    b = s.encode("utf-8")
    f.write(struct.pack(">H", len(b)))
    f.write(b)


def read_payload(f, tid):
    if tid == TAG_BYTE:
        return struct.unpack(">b", f.read(1))[0]
    if tid == TAG_SHORT:
        return struct.unpack(">h", f.read(2))[0]
    if tid == TAG_INT:
        return struct.unpack(">i", f.read(4))[0]
    if tid == TAG_LONG:
        return struct.unpack(">q", f.read(8))[0]
    if tid == TAG_FLOAT:
        return struct.unpack(">f", f.read(4))[0]
    if tid == TAG_DOUBLE:
        return struct.unpack(">d", f.read(8))[0]
    if tid == TAG_BYTE_ARRAY:
        n = struct.unpack(">i", f.read(4))[0]
        return f.read(n)
    if tid == TAG_STRING:
        return read_utf(f)
    if tid == TAG_LIST:
        elem = struct.unpack(">b", f.read(1))[0]
        n = struct.unpack(">i", f.read(4))[0]
        return (elem, [Tag(elem, read_payload(f, elem)) for _ in range(n)])
    if tid == TAG_COMPOUND:
        d = {}
        while True:
            ct = struct.unpack(">b", f.read(1))[0]
            if ct == TAG_END:
                break
            name = read_utf(f)
            d[name] = Tag(ct, read_payload(f, ct))
        return d
    if tid == TAG_INT_ARRAY:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(">%di" % n, f.read(4 * n)))
    raise ValueError("未知的 NBT 标签类型 %d" % tid)


def write_payload(f, tag):
    tid, v = tag.tid, tag.value
    if tid == TAG_BYTE:
        f.write(struct.pack(">b", v))
    elif tid == TAG_SHORT:
        f.write(struct.pack(">h", v))
    elif tid == TAG_INT:
        f.write(struct.pack(">i", v))
    elif tid == TAG_LONG:
        f.write(struct.pack(">q", v))
    elif tid == TAG_FLOAT:
        f.write(struct.pack(">f", v))
    elif tid == TAG_DOUBLE:
        f.write(struct.pack(">d", v))
    elif tid == TAG_BYTE_ARRAY:
        f.write(struct.pack(">i", len(v)))
        f.write(v)
    elif tid == TAG_STRING:
        write_utf(f, v)
    elif tid == TAG_LIST:
        elem, items = v
        f.write(struct.pack(">b", elem))
        f.write(struct.pack(">i", len(items)))
        for it in items:
            write_payload(f, it)
    elif tid == TAG_COMPOUND:
        for name, sub in v.items():
            f.write(struct.pack(">b", sub.tid))
            write_utf(f, name)
            write_payload(f, sub)
        f.write(struct.pack(">b", TAG_END))
    elif tid == TAG_INT_ARRAY:
        f.write(struct.pack(">i", len(v)))
        for i in v:
            f.write(struct.pack(">i", i))
    else:
        raise ValueError("未知的 NBT 标签类型 %d" % tid)


def load_nbt(path):
    """读 gzip 压缩的 NBT，返回 (根名字, 根 Tag)。"""
    with open(path, "rb") as raw:
        magic = raw.read(2)
        raw.seek(0)
        f = gzip.GzipFile(fileobj=raw) if magic == b"\x1f\x8b" else raw
        tid = struct.unpack(">b", f.read(1))[0]
        if tid != TAG_COMPOUND:
            raise ValueError("根标签不是 Compound，这个文件不是 HBM 结构 nbt")
        name = read_utf(f)
        return name, Tag(TAG_COMPOUND, read_payload(f, TAG_COMPOUND))


def save_nbt(path, name, root):
    with open(path, "wb") as raw:
        gz = gzip.GzipFile(fileobj=raw, mode="wb", mtime=0)
        gz.write(struct.pack(">b", TAG_COMPOUND))
        write_utf(gz, name)
        write_payload(gz, root)
        gz.close()


# ---------------------------------------------------------------- 结构文件解析
def get_int_list(tag):
    return list(tag.value[1]) if tag.tid == TAG_LIST else []


def parse_structure(root):
    """提取 (size, palette_names, block_entries, air_indices)。"""

    if "size" not in root.value:
        raise ValueError("缺少 size 字段")
    size = [t.value for t in get_int_list(root.value["size"])]
    if len(size) != 3:
        raise ValueError("size 字段不是 3 个整数")

    palette = []
    for entry in get_int_list(root.value["palette"]):
        name = entry.value.get("Name")
        meta = 0
        props = entry.value.get("Properties")
        if props is not None:
            meta_tag = props.value.get("meta")
            if meta_tag is not None:
                try:
                    meta = int(meta_tag.value)
                except (TypeError, ValueError):
                    meta = 0
        palette.append((name.value if name else "?", meta))

    blocks = get_int_list(root.value["blocks"])
    air_ids = {i for i, (n, _m) in enumerate(palette) if n == "minecraft:air"}
    return size, palette, blocks, air_ids


def block_pos(entry):
    pos = entry.value.get("pos")
    if pos is None:
        return None
    items = pos.value[1]
    if len(items) != 3:
        return None
    return items[0].value, items[1].value, items[2].value


def ensure_air_palette(root, palette, air_meta=0):
    """返回 air 在调色板里的下标，没有就追加一条 minecraft:air。"""
    for i, (n, m) in enumerate(palette):
        if n == "minecraft:air" and m == air_meta:
            return i

    props = Tag(TAG_COMPOUND, {"meta": Tag(TAG_STRING, str(air_meta))})
    entry = Tag(TAG_COMPOUND, {
        "Name": Tag(TAG_STRING, "minecraft:air"),
        "Properties": props,
    })

    pal_tag = root.value["palette"]
    if pal_tag.tid == TAG_LIST:
        elem, items = pal_tag.value
        if elem == TAG_END:
            pal_tag.value = (TAG_COMPOUND, [entry])
        else:
            items.append(entry)
    else:
        raise ValueError("palette 不是 List")

    palette.append(("minecraft:air", air_meta))
    return len(palette) - 1


def append_air_blocks(root, cells, air_id):
    """把一批坐标作为 air 条目追加到 blocks 列表。"""
    blocks_tag = root.value["blocks"]
    if blocks_tag.tid != TAG_LIST:
        raise ValueError("blocks 不是 List")

    elem, items = blocks_tag.value
    if elem == TAG_END:
        elem = TAG_COMPOUND
    for (x, y, z) in cells:
        items.append(Tag(TAG_COMPOUND, {
            "pos": Tag(TAG_LIST, (TAG_INT, [Tag(TAG_INT, x), Tag(TAG_INT, y), Tag(TAG_INT, z)])),
            "state": Tag(TAG_INT, air_id),
        }))
    blocks_tag.value = (elem, items)
    return len(items)


# ---------------------------------------------------------------- 主流程
def print_info(path, size, palette, blocks, air_ids):
    voxels = size[0] * size[1] * size[2]
    recorded = len(blocks)
    air_entries = sum(1 for b in blocks if b.value.get("state") is not None
                      and b.value["state"].value in air_ids)
    solid = recorded - air_entries
    missing = voxels - recorded

    print("文件      : %s (%.1f KB)" % (path, os.path.getsize(path) / 1024.0))
    print("尺寸      : %d x %d x %d = %d 格" % (size[0], size[1], size[2], voxels))
    print("调色板    : %d 项，其中 air 下标 %s" % (len(palette), sorted(air_ids) or "无"))
    print("已记录    : %d 条（实体 %d / 空气 %d）" % (recorded, solid, air_entries))
    print("未记录    : %d 格 —— 世界生成时保留原地形" % missing)
    print("带 TE     : %d 条" % sum(1 for b in blocks if "nbt" in b.value))

    if air_entries == 0 and missing > 0:
        print("\n⚠️ 文件里一条空气都没有：整盒都会保留原地形，"
              "埋在地下的房间会被土填满。")
    elif air_entries > 0:
        print("\n✔ 已显式标记了 %d 格空气。" % air_entries)


def main(argv=None):
    ap = argparse.ArgumentParser(
        description="HBM NBT 结构：整盒填充缺失格为空气 / 体检",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="示例：\n"
               "  python tools/structure_air_tool.py structures/ruin.nbt --info\n"
               "  python tools/structure_air_tool.py structures/ruin.nbt\n")
    ap.add_argument("file", help="结构 .nbt 文件路径")
    ap.add_argument("--info", action="store_true", help="只打印体检信息，不改文件")
    ap.add_argument("--dry-run", action="store_true", help="只统计会填多少格，不改文件")
    ap.add_argument("-o", "--output", help="输出到新文件（默认原地覆盖）")
    ap.add_argument("--no-backup", action="store_true", help="原地覆盖时不生成 .bak 备份")
    args = ap.parse_args(argv)

    path = args.file
    if not os.path.isfile(path):
        print("找不到文件：%s" % path, file=sys.stderr)
        return 1

    name, root = load_nbt(path)
    size, palette, blocks, air_ids = parse_structure(root)

    print_info(path, size, palette, blocks, air_ids)

    if args.info:
        return 0

    present = set()
    for b in blocks:
        p = block_pos(b)
        if p is not None:
            present.add(p)

    sx, sy, sz = size
    cells = [(x, y, z)
             for x in range(sx)
             for y in range(sy)
             for z in range(sz)
             if (x, y, z) not in present]

    print("\n待填空气  : %d 格" % len(cells))

    if not cells:
        print("没有缺失格，文件无需改动。")
        return 0

    if args.dry_run or args.info:
        print("（--dry-run，未写入）")
        return 0

    air_id = ensure_air_palette(root, palette)
    total = append_air_blocks(root, cells, air_id)

    out = args.output or path
    if out == path and not args.no_backup:
        bak = path + ".bak"
        shutil.copy2(path, bak)
        print("已备份     : %s" % bak)

    save_nbt(out, name, root)
    print("已写入     : %s（blocks %d 条，新增空气 %d 格）"
          % (out, total, len(cells)))
    print("\n下一步：把文件放回 assets/makentmgreatagain/structures/ 并重新打包。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
