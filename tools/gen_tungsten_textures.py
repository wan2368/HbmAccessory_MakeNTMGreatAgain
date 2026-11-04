# -*- coding: utf-8 -*-
"""白钨矿链条最终贴图生成器。

配色统一走"暖米白"：白钨矿 CaWO4 在现实里就是白色到浅黄白，
三氧化钨才是标志性的亮黄绿，两者色差正好把"精矿 -> 提纯"的过程讲清楚。

唯一例外是矿粉 powder_scheelite：暖米白和 HBM 的白砷 arsenic_trioxide
几乎同色（同一套粉堆模板，色阶只差 10 来个亮度），NEI 里并排分不出来，
所以矿粉单独走土黄 P_OCHRE。角标方案实测过：16x16 上 1px 折角在 GUI
尺寸完全看不见，2px 以上又像贴了块胶带，不如直接用颜色区分，
角标版只作为备选输出到 宣传素材，不进资源包。
"""
import os
import random
from PIL import Image

ROOT = r'F:\HBM-Source'
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'makentmgreatagain', 'textures')
PREVIEW = os.path.join(ROOT, '宣传素材', 'tungsten')

TEMPLATE_POWDER = os.path.join(TEX, 'items', 'powder_sodium_sulfate.png')

# 暖米白：矿石 / 精矿通用
P_WARM = [(0x4E, 0x48, 0x37), (0x64, 0x5D, 0x48), (0x7B, 0x73, 0x5A), (0x93, 0x8B, 0x6E),
          (0xAD, 0xA4, 0x83), (0xC9, 0xBF, 0x9A), (0xE1, 0xD7, 0xB1), (0xF1, 0xE8, 0xC3)]

# 土黄：矿粉专用。比暖米白更黄，和白砷（冷灰白）拉开距离
P_OCHRE = [(0x4E, 0x45, 0x2B), (0x66, 0x5A, 0x38), (0x7E, 0x70, 0x45), (0x98, 0x88, 0x53),
           (0xB3, 0xA1, 0x63), (0xCF, 0xBB, 0x75), (0xE7, 0xD4, 0x8E), (0xF7, 0xE7, 0xAB)]

# 纯白：钨酸 H2WO4，沉淀出来的白色粉末
P_ACID = [(0x6B, 0x6B, 0x66), (0x7E, 0x7E, 0x78), (0x96, 0x96, 0x8F), (0xAE, 0xAE, 0xA6),
          (0xC8, 0xC8, 0xBE), (0xE2, 0xE2, 0xD8), (0xF5, 0xF5, 0xEC), (0xFF, 0xFF, 0xF9)]

# 亮黄绿：三氧化钨 WO3
P_TRIOXIDE = [(0x3F, 0x46, 0x12), (0x52, 0x5A, 0x18), (0x6C, 0x76, 0x1E), (0x87, 0x92, 0x26),
              (0xA3, 0xAE, 0x30), (0xBF, 0xCA, 0x42), (0xD8, 0xE2, 0x5E), (0xEC, 0xF4, 0x8C)]

GOLD_D = (0xB4, 0x8C, 0x3B)
GOLD_L = (0xDD, 0xB7, 0x60)

# 灰褐：白钨浸出渣。不是"废土色"，而是湿渣那种发暗的褐，带烧过的调子
P_SLAG = [(0x27, 0x24, 0x1F), (0x34, 0x30, 0x29), (0x43, 0x3D, 0x33), (0x55, 0x4D, 0x40),
          (0x69, 0x5F, 0x4E), (0x7F, 0x73, 0x5E), (0x96, 0x89, 0x71), (0xAD, 0x9F, 0x85)]

# 灰绿：砷渣。偏冷、偏脏，和浸出渣的暖褐一眼能分开
P_ARSENIC_CAKE = [(0x25, 0x2B, 0x24), (0x31, 0x39, 0x30), (0x3F, 0x49, 0x3D), (0x50, 0x5C, 0x4D),
                  (0x63, 0x71, 0x5F), (0x78, 0x88, 0x73), (0x8E, 0x9F, 0x87), (0xA5, 0xB6, 0x9D)]

# 浅灰绿：绿柱石（铍精矿）。绿色是绿柱石的本色，但要压得很淡，避免像铜矿
P_BERYL = [(0x3A, 0x45, 0x3C), (0x4B, 0x58, 0x4C), (0x5E, 0x6D, 0x5E), (0x73, 0x84, 0x71),
           (0x8A, 0x9C, 0x86), (0xA3, 0xB4, 0x9C), (0xBD, 0xCB, 0xB4), (0xD6, 0xE0, 0xCB)]

# 浅砂黄：锆英石（锆精矿）。比白钨的暖米白更灰、更"砂"
P_ZIRCON = [(0x46, 0x3F, 0x33), (0x58, 0x50, 0x41), (0x6C, 0x63, 0x51), (0x82, 0x78, 0x63),
            (0x99, 0x8E, 0x77), (0xB1, 0xA5, 0x8C), (0xC9, 0xBD, 0xA2), (0xDF, 0xD4, 0xBB)]


# ------------------------------------------------------------------ 通用工具
def smooth(t):
    return t * t * (3 - 2 * t)


def value_noise(cells, seed):
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * 16 for _ in range(16)]
    for y in range(16):
        gy = y * cells / 16.0
        y0, ty = int(gy) % cells, smooth(gy - int(gy))
        y1 = (y0 + 1) % cells
        for x in range(16):
            gx = x * cells / 16.0
            x0, tx = int(gx) % cells, smooth(gx - int(gx))
            x1 = (x0 + 1) % cells
            a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def wander(rnd, length):
    x, y = rnd.randrange(16), rnd.randrange(16)
    dx, dy = rnd.choice(((1, 1), (1, -1), (-1, 1), (-1, -1), (1, 0), (0, 1)))
    pts = []
    for _ in range(length):
        pts.append((x % 16, y % 16))
        if rnd.random() < 0.4:
            dx, dy = rnd.choice(((1, 1), (1, -1), (-1, 1), (-1, -1),
                                 (1, 0), (0, 1), (-1, 0), (0, -1)))
        x += dx
        y += dy
    return pts


# ------------------------------------------------------------------ 方块：白钨矿石
def make_ore(pal, seed=17, base=5.2, spread=4.0, grain=0.5, specks=3, veins=3):
    rnd = random.Random(seed)
    lo, hi = value_noise(4, seed), value_noise(8, seed + 91)
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            n = lo[y][x] * 0.6 + hi[y][x] * 0.4
            # 量化成 3 档，让色块边界变硬，摆脱砂岩的糊感
            q = -1 if n < 0.36 else (0 if n < 0.64 else 1)
            v = base + q * spread * 0.5 + rnd.uniform(-grain, grain)
            im.putpixel((x, y), pal[max(1, min(7, int(round(v))))] + (255,))

    for _ in range(veins):
        for (x, y) in wander(rnd, rnd.randint(5, 10)):
            im.putpixel((x, y), pal[2 if rnd.random() < 0.65 else 3] + (255,))

    for _ in range(specks):
        sx, sy = rnd.randrange(1, 15), rnd.randrange(1, 15)
        im.putpixel((sx, sy), pal[2] + (255,))
        if rnd.random() < 0.55:
            im.putpixel(((sx + 1) % 16, sy), pal[3] + (255,))

    for cx, cy, w, h in ((2, 1, 2, 2), (9, 3, 3, 2), (12, 12, 2, 2), (5, 9, 2, 3)):
        for yy in range(cy, min(16, cy + h)):
            for xx in range(cx, min(16, cx + w)):
                if rnd.random() < 0.6:
                    im.putpixel((xx, yy), pal[7] + (255,))
    return im


# ------------------------------------------------------------------ 方块：白钨矿石（HBM 布局）
MASK_SRC = os.path.join(TEX, 'blocks', 'ore_refractory_gold.png')
BG_COLORS = {(0x7F, 0x7F, 0x7F), (0x74, 0x74, 0x74), (0x8F, 0x8F, 0x8F), (0x68, 0x68, 0x68)}


def make_ore_layout(pal, seed=23, dark_tone=3, light_tone=6, grain=0.5):
    """沿用 HBM 矿石的矿斑布局，把"石底"换成矿体亮色、"矿斑"换成矿体暗色，
    整块都是白钨矿，但保留了原版那种成团的矿物分布。"""
    rnd = random.Random(seed)
    src = Image.open(MASK_SRC).convert('RGBA')

    base = [[0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[:3] not in BG_COLORS:
                base[y][x] = 1

    # 膨胀一次，让矿斑连成团
    m = [[0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if base[y][x]:
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        m[(y + dy) % 16][(x + dx) % 16] = 1

    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            v = dark_tone if m[y][x] else light_tone
            v += rnd.uniform(-grain, grain)
            im.putpixel((x, y), pal[max(1, min(7, int(round(v))))] + (255,))

    # 矿斑里点几处更深的核心，做出结晶感
    cores = [(x, y) for y in range(16) for x in range(16) if base[y][x]]
    rnd.shuffle(cores)
    for cx, cy in cores[:10]:
        im.putpixel((cx, cy), pal[max(1, dark_tone - 2)] + (255,))

    # 亮色区散几处高光
    for hx, hy in ((3, 1), (12, 2), (7, 9), (2, 12), (14, 8), (10, 14)):
        if not m[hy][hx]:
            im.putpixel((hx, hy), pal[7] + (255,))
    return im


def make_ore_blob(pal, seed=41, base=5.3, spread=3.8, grain=0.5, blobs=3):
    """噪声底 + 成团暗色矿物：平铺无网格，又有明显的矿团。"""
    rnd = random.Random(seed)
    lo, hi = value_noise(4, seed), value_noise(8, seed + 53)
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            n = lo[y][x] * 0.5 + hi[y][x] * 0.5
            v = base + (n - 0.5) * spread + rnd.uniform(-grain, grain)
            im.putpixel((x, y), pal[max(1, min(7, int(round(v))))] + (255,))

    # 暗色矿团：环形半径 + 抖动，边缘不规则
    for _ in range(blobs):
        cx, cy = rnd.uniform(0, 16), rnd.uniform(0, 16)
        r = rnd.uniform(1.5, 2.8)
        for y in range(16):
            for x in range(16):
                dx = min(abs(x - cx), 16 - abs(x - cx))
                dy = min(abs(y - cy), 16 - abs(y - cy))
                d = (dx * dx + dy * dy) ** 0.5 + rnd.uniform(-0.45, 0.45)
                if d < r * 0.55:
                    im.putpixel((x, y), pal[2] + (255,))
                elif d < r:
                    im.putpixel((x, y), pal[3] + (255,))

    # 亮色区少量高光
    for hx, hy in ((3, 1), (12, 2), (7, 9), (2, 12), (14, 8)):
        if im.getpixel((hx, hy))[0] > pal[4][0]:
            im.putpixel((hx, hy), pal[7] + (255,))
    return im


# ------------------------------------------------------------------ 物品：粉堆模板
def powder_level_map():
    im = Image.open(TEMPLATE_POWDER).convert('RGBA')
    cols = sorted({im.getpixel((x, y))[:3] for y in range(16) for x in range(16)
                   if im.getpixel((x, y))[3] > 0}, key=lambda c: sum(c))
    rank = {c: i for i, c in enumerate(cols)}
    lv = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            r, g, b, a = im.getpixel((x, y))
            if a > 0:
                lv[y][x] = rank[(r, g, b)]
    return lv


LEVEL = None


def make_powder(pal):
    """细腻粉末：直接套模板明暗。"""
    global LEVEL
    if LEVEL is None:
        LEVEL = powder_level_map()
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            v = LEVEL[y][x]
            if v is not None:
                im.putpixel((x, y), pal[v] + (255,))
    return im


def make_granular(pal, seed=7):
    """粗颗粒（精矿）：按 2x2 归组同色，组界压暗，做出砂粒感。"""
    global LEVEL
    if LEVEL is None:
        LEVEL = powder_level_map()
    rnd = random.Random(seed)
    cell = {}
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            v = LEVEL[y][x]
            if v is None:
                continue
            key = (x // 3, y // 2)
            if key not in cell:
                cell[key] = rnd.randint(-1, 1)
            k = max(1, min(6, v + cell[key]))
            if x % 3 == 2 or y % 2 == 1:
                k = 0 if rnd.random() < 0.55 else max(0, k - 2)
            im.putpixel((x, y), pal[k] + (255,))
    return im


def corner_mark(im, col=(0x3D, 0x4F, 0x5C), w=2, n=6):
    """右上角折角标记。实验结论：16x16 上低于 2px 宽在 GUI 里看不见，
    2px 以上又像贴了块胶带，所以只作为备选方案输出到预览目录。"""
    im = im.copy()
    for i in range(w):
        for x in range(16 - n, 16):
            im.putpixel((x, i), col + (255,))
        for y in range(0, n):
            im.putpixel((15 - i, y), col + (255,))
    return im


# ------------------------------------------------------------------ 物品：钨酸滤饼
CAKE = [
    '................',
    '................',
    '....####.####...',
    '..###########...',
    '..############..',
    '.#############..',
    '.###############',
    '.###############',
    '.###############',
    '..#############.',
    '..############..',
    '...##########...',
    '....########....',
    '......####......',
    '................',
    '................',
]


def make_cake(pal, seed=13):
    """压滤后的钨酸饼：不规则实心块，顶部亮、底部有阴影。"""
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    rows = [y for y in range(16) if '#' in CAKE[y]]
    top, bot = min(rows), max(rows)
    for y in range(16):
        for x in range(16):
            if CAKE[y][x] != '#':
                continue
            t = (y - top) / float(bot - top)
            v = 6.4 - t * 3.6
            if CAKE[y][max(0, x - 1)] == '.' or CAKE[y][min(15, x + 1)] == '.':
                v -= 1.5
            if CAKE[max(0, y - 1)][x] == '.':
                v -= 0.8
            v += rnd.uniform(-0.45, 0.45)
            im.putpixel((x, y), pal[max(1, min(7, int(round(v))))] + (255,))

    # 顶面几道压痕，交代"这是压出来的饼"
    for cx, cy, n in ((5, 5, 3), (9, 8, 2), (6, 10, 3)):
        for i in range(n):
            x, y = cx + i, cy + (i // 2)
            if 0 <= x < 16 and CAKE[y][x] == '#':
                im.putpixel((x, y), pal[2] + (255,))
    return im


def make_slag(pal, seed=17, specks=3):
    """渣：粉堆轮廓 + 粗颗粒跳变 + 散落颗粒。

    轮廓必须和粉末、精矿共用同一套模板——HBM 里所有"一堆东西"都是这个形状，
    自己另起一套反而出戏。渣和粉的差别放在质地上：
    粉是细腻渐变，精矿是 3x2 归组的砂粒，渣是 2x2 随机跳档（颗粒粗、成分杂），
    再加几粒散落在堆外，就是"倒出来的时候撒了一点"的感觉。
    参照物是同模组的 arsenical_residue（含砷金矿的浸出渣），它也是粉堆 + 散粒。
    """
    global LEVEL
    if LEVEL is None:
        LEVEL = powder_level_map()
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))

    cell = {}
    for y in range(16):
        for x in range(16):
            v = LEVEL[y][x]
            if v is None:
                continue
            key = (x // 2, y // 2)
            if key not in cell:
                cell[key] = rnd.choice((-1, 0, 0, 1))
            k = max(1, min(6, v + cell[key]))
            im.putpixel((x, y), pal[k] + (255,))

    # 异相颗粒：亮的像石英，暗的像没反应完的矿
    cells = [(x, y) for y in range(16) for x in range(16) if LEVEL[y][x] is not None]
    rnd.shuffle(cells)
    for i, (x, y) in enumerate(cells):
        if i % 8 == 0:
            im.putpixel((x, y), pal[7] + (255,))
        elif i % 11 == 0:
            im.putpixel((x, y), pal[2] + (255,))

    # 散落颗粒：只放在堆底以下的空白处，不破坏堆形
    for _ in range(specks):
        sx, sy = rnd.randrange(1, 15), rnd.randrange(13, 16)
        if LEVEL[sy][sx] is None:
            im.putpixel((sx, sy), pal[4] + (255,))
            if LEVEL[sy][(sx + 1) % 16] is None:
                im.putpixel(((sx + 1) % 16, sy), pal[2] + (255,))
    return im


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    out = []

    def save(im, rel):
        p = os.path.join(TEX, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)
        out.append((rel, p))

    # 两个备选方案，仅输出到预览目录，不进资源包：
    #   alt_ore_noise  纯噪声斑驳（无网格，但缺矿团，像砂岩）
    #   alt_ore_layout 复用 HBM 矿斑布局（矿团明显，但平铺有网格）
    make_ore(P_WARM, base=5.4, spread=4.6, grain=0.45, specks=4, veins=4).save(
        os.path.join(PREVIEW, 'alt_ore_noise.png'))
    make_ore_layout(P_WARM, dark_tone=3, light_tone=6, grain=0.55).save(
        os.path.join(PREVIEW, 'alt_ore_layout.png'))
    save(make_ore_blob(P_WARM), 'blocks/ore_scheelite.png')
    save(make_granular(P_WARM), 'items/scheelite_concentrate.png')
    save(make_powder(P_OCHRE), 'items/powder_scheelite.png')
    # 角标备选，仅预览
    corner_mark(make_powder(P_OCHRE)).save(
        os.path.join(PREVIEW, 'powder_scheelite_marked.png'))
    save(make_cake(P_ACID), 'items/tungstic_acid.png')
    save(make_powder(P_TRIOXIDE), 'items/tungsten_trioxide.png')
    # 浸出渣 / 砷渣：两块渣饼，靠色调分开（暖褐 vs 冷绿）
    save(make_slag(P_SLAG, seed=17), 'items/scheelite_leach_residue.png')
    save(make_slag(P_ARSENIC_CAKE, seed=29), 'items/arsenic_cake.png')
    # 铍精矿 / 锆精矿：粗颗粒砂感，和精矿同一套形状、只换色板
    save(make_granular(P_BERYL, seed=11), 'items/beryllium_concentrate.png')
    save(make_granular(P_ZIRCON, seed=19), 'items/zirconium_concentrate.png')
    # 流体 GUI 图标：整张纯色（HBM 的做法）
    save(Image.new('RGBA', (16, 16), (0xC6, 0xD2, 0xA2, 255)), 'gui/fluids/sodium_tungstate.png')
    save(Image.new('RGBA', (16, 16), (0x8E, 0x7B, 0x39, 255)), 'gui/fluids/waste_acid.png')

    for rel, p in out:
        print('%-40s %s' % (rel, os.path.getsize(p)))


if __name__ == '__main__':
    main()
