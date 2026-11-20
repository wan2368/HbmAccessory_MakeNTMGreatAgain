# -*- coding: utf-8 -*-
"""查存档里「地心实验室」到底生成了没有、在哪。

为什么需要：这座遗迹在 121 区块里只出一次，而且底下埋在 y=3~15 的实心地狱岩里，
在游戏里靠飞是找不到的——「走了 50 个区块没看见」很可能是真的没走到落点，
也可能是生成了但位置不对（比如超平坦存档把层位算歪了）。

这个脚本直接读 .mca，用**钢箱的方块实体**（`tileentity_crate_steel`，
一座实验室里固定 10 个）当指纹，把已经写进存档的实验室全揪出来；
再加 `--predict` 按世界种子反推「网格公式说哪里该有」，方便去没探索过的地方蹲。

用法:
    # 列出存档（下界）里所有已生成的实验室
    python tools/structure_find.py "F:/.../eclipse/saves/新的世界"

    # 只看某个维度（默认 -1 = 下界）
    python tools/structure_find.py "<存档目录>" --dim 0

    # 按种子预测最近的 10 个落点（区块坐标），去那儿一定有一个
    python tools/structure_find.py "<存档目录>" --predict 10

    # 换一个网格间距去预测（要和 config/MNGA/worldgen.cfg 里的 structureGridChunks 一致）
    python tools/structure_find.py "<存档目录>" --predict 10 --grid 7

没有任何第三方依赖。
"""
import argparse
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


class Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def payload(self, t):
        d, i = self.d, self.i
        if t == 1:
            self.i = i + 1
            return struct.unpack_from(">b", d, i)[0]
        if t == 2:
            self.i = i + 2
            return struct.unpack_from(">h", d, i)[0]
        if t == 3:
            self.i = i + 4
            return struct.unpack_from(">i", d, i)[0]
        if t == 4:
            self.i = i + 8
            return struct.unpack_from(">q", d, i)[0]
        if t == 5:
            self.i = i + 4
            return struct.unpack_from(">f", d, i)[0]
        if t == 6:
            self.i = i + 8
            return struct.unpack_from(">d", d, i)[0]
        if t == 7:
            n = struct.unpack_from(">i", d, i)[0]
            self.i = i + 4 + n
            return d[i + 4:i + 4 + n]
        if t == 8:
            n = struct.unpack_from(">H", d, i)[0]
            self.i = i + 2 + n
            return d[i + 2:i + 2 + n].decode("utf-8", "replace")
        if t == 9:
            et = d[i]
            n = struct.unpack_from(">i", d, i + 1)[0]
            self.i = i + 5
            return [self.payload(et) for _ in range(n)]
        if t == 10:
            out = {}
            self.i = i
            while True:
                tt = d[self.i]
                self.i += 1
                if tt == 0:
                    return out
                n = struct.unpack_from(">H", d, self.i)[0]
                self.i += 2
                key = d[self.i:self.i + n].decode("utf-8", "replace")
                self.i += n
                out[key] = self.payload(tt)
        if t == 11:
            n = struct.unpack_from(">i", d, i)[0]
            self.i = i + 4 + 4 * n
            return struct.unpack_from(">%di" % n, d, i + 4)
        raise ValueError("未知 NBT 类型 %d" % t)

    def root(self):
        t = self.d[self.i]
        self.i += 1
        n = struct.unpack_from(">H", self.d, self.i)[0]
        self.i += 2 + n
        return Tag(t, self.payload(t))


def read_nbt(path):
    raw = open(path, "rb").read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip_read(raw)
    return Reader(raw).root().v


def gzip_read(raw):
    import gzip
    return gzip.GzipFile(fileobj=io.BytesIO(raw)).read()


# ------------------------------------------------------------------ 区域文件
def region_chunks(path):
    """返回 {(chunkX, chunkZ): 解压后的 NBT 字节}。"""
    raw = open(path, "rb").read()
    out = {}
    for i in range(1024):
        off = struct.unpack_from(">I", raw, i * 4)[0]
        sec = off >> 8
        if not sec:
            continue
        ln = struct.unpack_from(">I", raw, sec * 4096)[0]
        if ln <= 1:
            continue
        try:
            data = zlib.decompress(raw[sec * 4096 + 5: sec * 4096 + 4 + ln])
        except Exception:
            continue
        out[(i % 32, i // 32)] = data
    return out


def load_dim(save, dim):
    folder = os.path.join(save, "region") if dim == 0 else os.path.join(save, "DIM%d" % dim, "region")
    chunks = {}
    if not os.path.isdir(folder):
        return None, chunks
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".mca"):
            continue
        try:
            rx, rz = [int(t) for t in name[:-4].split(".")[1:3]]
        except ValueError:
            continue
        for (cx, cz), data in region_chunks(os.path.join(folder, name)).items():
            chunks[(rx * 32 + cx, rz * 32 + cz)] = data
    return folder, chunks


# ------------------------------------------------------------------ 指纹
CRATE = "tileentity_crate_steel"


def crates_of(chunk_nbt):
    """一个区块里所有钢箱 TE 的 (x, y, z)。"""
    out = []
    level = chunk_nbt.get("Level", chunk_nbt)
    for te in level.get("TileEntities") or []:
        if te.get("id") == CRATE:
            out.append((te.get("x"), te.get("y"), te.get("z")))
    return out


def cluster(chunk_set):
    """把相邻（切比雪夫距离 ≤ 2 区块）的区块并成一座实验室。"""
    left = set(chunk_set)
    groups = []
    while left:
        seed = left.pop()
        group = [seed]
        stack = [seed]
        while stack:
            cx, cz = stack.pop()
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    n = (cx + dx, cz + dz)
                    if n in left:
                        left.discard(n)
                        group.append(n)
                        stack.append(n)
        groups.append(group)
    return groups


# ------------------------------------------------------------------ 落点预测
MASK = (1 << 64) - 1
C1 = 0x9E3779B97F4A7C15
C2 = 0xC2B2AE3D27D4EB4F
C3 = 0xD6E8FEB86659FD93
C4 = 0xFF51AFD7ED558CCD
C5 = 0xC4CEB9FE1A85EC53


def s64(v):
    v &= MASK
    return v - (1 << 64) if v >> 63 else v


def mul(a, b):
    return (s64(a) * s64(b)) & MASK


def spot_of(seed, gx, gz, dim, spacing):
    h = seed & MASK
    h ^= mul(gx, C1)
    h ^= mul(gz, C2)
    h ^= mul(dim, C3)
    h ^= h >> 33
    h = mul(h, C4)
    h ^= h >> 33
    h = mul(h, C5)
    h ^= h >> 33
    span = max(1, spacing - 4)
    base = max(1, (spacing - span) // 2)
    ox = base + (h % span)
    oz = base + ((h >> 32) % span)
    return gx * spacing + ox, gz * spacing + oz


def main():
    ap = argparse.ArgumentParser(description="定位存档里已生成的地心实验室（以及理论落点）")
    ap.add_argument("save", help="存档目录（含 level.dat 的那一层）")
    ap.add_argument("--dim", type=int, default=-1, help="维度 ID，默认 -1（下界）")
    ap.add_argument("--predict", type=int, default=0, help="额外预测最近的 N 个落点（区块）")
    ap.add_argument("--grid", type=int, default=11, help="structureGridChunks，默认 11")
    args = ap.parse_args()

    level = os.path.join(args.save, "level.dat")
    seed = None
    if os.path.exists(level):
        try:
            seed = read_nbt(level)["Data"]["RandomSeed"]
        except Exception:
            seed = None

    folder, chunks = load_dim(args.save, args.dim)
    if not chunks:
        print("这个存档里找不到 DIM%d 的 region 文件：%s" % (args.dim, folder or "(目录不存在)"))
    else:
        hits = {}
        for key, data in chunks.items():
            try:
                nbt = read_nbt_bytes(data)
            except Exception:
                continue
            c = crates_of(nbt)
            if len(c) >= 2:
                hits[key] = c

        print("维度 DIM%d：已生成 %d 个区块，其中 %d 个区块带钢箱指纹"
              % (args.dim, len(chunks), len(hits)))
        if seed is not None:
            print("世界种子：%d" % seed)

        groups = cluster(hits.keys())
        groups.sort(key=lambda g: (min(g)[1], min(g)[0]))
        print("判定为「地心实验室」的簇：%d 座" % len(groups))
        for i, group in enumerate(groups, 1):
            pts = [p for k in group for p in hits[k]]
            xs = [p[0] for p in pts]
            zs = [p[2] for p in pts]
            ys = sorted({p[1] for p in pts})
            cx = (min(xs) + max(xs)) // 2
            cz = (min(zs) + max(zs)) // 2
            print("  [%2d] 区块 %s ~ %s   钢箱 %2d 个   y=%s"
                  % (i, (min(k[0] for k in group), min(k[1] for k in group)),
                     (max(k[0] for k in group), max(k[1] for k in group)),
                     len(pts), "/".join(str(y) for y in ys)))
            print("       底座大约 y=%d，平面中心 (%d, %d) → /tp @p %d %d %d"
                  % (min(ys) - 6, cx, cz, cx + 0.5, min(ys) - 6, cz + 0.5))

    if args.predict:
        if seed is None:
            print("\n读不到 level.dat 的种子，没法预测落点")
            return
        print("\n按种子 + 网格 %d 预测的落点（每个 %dx%d 区块格一个）：" % (args.grid, args.grid, args.grid))
        spots = []
        r = max(2, int((args.predict * 2) ** 0.5) + 1)
        for gx in range(-r, r + 1):
            for gz in range(-r, r + 1):
                cx, cz = spot_of(seed, gx, gz, args.dim, args.grid)
                spots.append((abs(cx) + abs(cz), cx, cz))
        spots.sort()
        for _, cx, cz in spots[:args.predict]:
            print("  区块 (%4d, %4d)  方块中心 → /tp @p %d ~ %d"
                  % (cx, cz, cx * 16 + 8, cz * 16 + 8))
        print("  提示：只有「已经生成过的区块」才会有楼；去没走过的区块，飞过去就会生成。")


def read_nbt_bytes(raw):
    if raw[:2] == b"\x1f\x8b":
        raw = gzip_read(raw)
    return Reader(raw).root().v


if __name__ == "__main__":
    sys.exit(main())
