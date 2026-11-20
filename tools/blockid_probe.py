# -*- coding: utf-8 -*-
"""从存档反查「方块注册名 ↔ 数字 ID」，给 1.7.10 创世神（WorldEdit）用。

为什么需要：1.7.10 的 WE 只吃数字 ID（`//replace air 1953`），
而方块 ID 是 FML 在运行时按注册顺序分配的，装多少模组就会变多少个号，
所以不能把号写死在教程里，只能现场从数据里反查。

原理：区块的 `TileEntities` 存的是 TE 的**注册名**（`tileentity_xxx`），
同一区块的 `Sections.Blocks` 存的是**数字 ID**。用 TE 的 x/y/z 去 Blocks
里取那一格，就得到这个方块的 ID。前提是世界里至少摆过一个该方块。

用法：
    # 列出世界里出现过的所有「TE 名 → 方块 ID」，按 ID 排序
    python tools/blockid_probe.py "F:/.../.minecraft/versions/xxx/saves/新的世界--"

    # 只看名字里含 wand 的
    python tools/blockid_probe.py "<存档目录>" --find wand

    # 统计某段 ID 各出现多少次（验证推测：真的摆过就是这个号）
    python tools/blockid_probe.py "<存档目录>" --count 1950-1962

没有任何第三方依赖。
"""
import io
import os
import struct
import sys
import zlib


# ------------------------------------------------------------------ NBT
class Tag:
    __slots__ = ("t", "v")

    def __init__(self, t, v):
        self.t = t
        self.v = v

    def __repr__(self):
        return "Tag(%d,%r)" % (self.t, self.v)


def _utf(f):
    n = struct.unpack(">H", f.read(2))[0]
    return f.read(n).decode("utf-8", "replace")


def _payload(f, tid):
    if tid == 1:
        return Tag(1, struct.unpack(">b", f.read(1))[0])
    if tid == 2:
        return Tag(2, struct.unpack(">h", f.read(2))[0])
    if tid == 3:
        return Tag(3, struct.unpack(">i", f.read(4))[0])
    if tid == 4:
        return Tag(4, struct.unpack(">q", f.read(8))[0])
    if tid == 5:
        return Tag(5, struct.unpack(">f", f.read(4))[0])
    if tid == 6:
        return Tag(6, struct.unpack(">d", f.read(8))[0])
    if tid == 7:
        n = struct.unpack(">i", f.read(4))[0]
        return Tag(7, list(f.read(n)))
    if tid == 8:
        return Tag(8, _utf(f))
    if tid == 9:
        et = struct.unpack(">b", f.read(1))[0]
        n = struct.unpack(">i", f.read(4))[0]
        return Tag(9, [(_payload(f, et) if et else Tag(0, None)) for _ in range(n)])
    if tid == 10:
        d = {}
        while True:
            t = struct.unpack(">b", f.read(1))[0]
            if t == 0:
                return Tag(10, d)
            # 注意：必须先把名字读出来再读 payload。
            # 写成 d[_utf(f)] = _payload(f, t) 会先算右边的 payload，键名读晚一步，整个流串位。
            key = _utf(f)
            d[key] = _payload(f, t)
    if tid == 11:
        n = struct.unpack(">i", f.read(4))[0]
        return Tag(11, list(struct.unpack(">%di" % n, f.read(4 * n))))
    raise ValueError("bad tag id %d" % tid)


def parse_nbt(f):
    t = struct.unpack(">b", f.read(1))[0]
    if t != 10:
        raise ValueError("root is not a compound")
    _utf(f)
    return _payload(f, 10).v


# ------------------------------------------------------------------ 区域文件
def iter_chunks(path):
    """逐个产出 (chunkX, chunkZ, rootCompound)。坏块直接跳过，不中断扫描。"""
    with open(path, "rb") as fh:
        hdr = fh.read(8192)
        if len(hdr) < 8192:
            return
        for i in range(1024):
            off = struct.unpack(">I", b"\x00" + hdr[i * 4:i * 4 + 3])[0]
            if off == 0:
                continue
            fh.seek(off << 12)
            ln = struct.unpack(">I", fh.read(4))[0]
            ctype = struct.unpack(">B", fh.read(1))[0]
            data = fh.read(ln - 1)
            try:
                if ctype == 2:
                    data = zlib.decompress(data)
                elif ctype == 1:
                    import gzip
                    data = gzip.decompress(data)
                else:
                    continue
                yield i % 32, i // 32, parse_nbt(io.BytesIO(data))
            except Exception:
                continue


def block_id_at(level, x, y, z):
    for sec in level.get("Sections", Tag(9, [])).v:
        s = sec.v
        if "Blocks" not in s:
            continue
        yy = s.get("Y", Tag(1, 0)).v
        if not (yy * 16 <= y < yy * 16 + 16):
            continue
        idx = (y & 15) * 256 + (z & 15) * 16 + (x & 15)
        bid = s["Blocks"].v[idx] & 0xFF
        add = s.get("Add")
        if add is not None:
            nib = add.v[idx >> 1]
            bid |= ((nib >> 4) if (idx & 1) else (nib & 0x0F)) << 8
        meta = s["Data"].v[idx >> 1]
        meta = (meta >> 4) if (idx & 1) else (meta & 0x0F)
        return bid, meta
    return None


def region_dir_of(path):
    p = path
    if os.path.isdir(os.path.join(p, "region")):
        p = os.path.join(p, "region")
    return p


def te_table(rd, needle=None):
    """返回 {TE 名: (blockID, meta, 首个坐标)}"""
    out = {}
    for name in sorted(os.listdir(rd)):
        if not name.endswith(".mca"):
            continue
        for _cx, _cz, root in iter_chunks(os.path.join(rd, name)):
            lv = root.get("Level")
            if lv is None:
                continue
            level = lv.v
            for te in level.get("TileEntities", Tag(9, [])).v:
                d = te.v
                tid = d.get("id", Tag(8, "?")).v
                if needle and needle not in tid:
                    continue
                if tid in out:
                    continue
                x, y, z = d["x"].v, d["y"].v, d["z"].v
                r = block_id_at(level, x, y, z)
                if r:
                    out[tid] = (r[0], r[1], (x, y, z))
    return out


def id_count(rd, lo, hi):
    cnt = {}
    for name in sorted(os.listdir(rd)):
        if not name.endswith(".mca"):
            continue
        for _cx, _cz, root in iter_chunks(os.path.join(rd, name)):
            lv = root.get("Level")
            if lv is None:
                continue
            for sec in lv.v.get("Sections", Tag(9, [])).v:
                s = sec.v
                if "Blocks" not in s:
                    continue
                blocks = s["Blocks"].v
                add = s.get("Add")
                if add is None:
                    for b in blocks:
                        b &= 0xFF
                        if lo <= b <= hi:
                            cnt[b] = cnt.get(b, 0) + 1
                else:
                    for i in range(4096):
                        b = blocks[i] & 0xFF
                        nib = add.v[i >> 1]
                        b |= ((nib >> 4) if (i & 1) else (nib & 0x0F)) << 8
                        if lo <= b <= hi:
                            cnt[b] = cnt.get(b, 0) + 1
    return cnt


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 1
    rd = region_dir_of(argv[1])
    if not os.path.isdir(rd):
        print("不是存档目录：%s" % rd)
        return 1

    if "--count" in argv:
        rng = argv[argv.index("--count") + 1]
        lo, _, hi = rng.partition("-")
        lo, hi = int(lo), int(hi or lo)
        for k, v in sorted(id_count(rd, lo, hi).items()):
            print("ID %-6d %d 次" % (k, v))
        return 0

    needle = None
    if "--find" in argv:
        needle = argv[argv.index("--find") + 1]

    tab = te_table(rd, needle)
    if not tab:
        print("没找到匹配的 TileEntity（世界里没摆过，或者这个方块没有 TE）")
        return 0
    for tid, (bid, meta, pos) in sorted(tab.items(), key=lambda kv: kv[1][0]):
        print("ID %-6d meta %-2d  %-34s  首个坐标 %s" % (bid, meta, tid, pos))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
