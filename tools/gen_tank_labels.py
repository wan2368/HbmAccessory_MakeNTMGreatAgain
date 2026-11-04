# -*- coding: utf-8 -*-
"""白钨矿链条的储罐标签生成器。

HBM 的罐体贴图是 122×140 的展开图：左侧是罐身色块，右上有一个菱形图标，
底部一行 5px 高的白色字母写着流体名。附属这边一直用 tank_SALT_WATER.png
当形状模板，只换主色和文字 —— 这样形状、条纹、图标位置全都和已有流体一致，
不需要重新画一遍罐体。

用法：改下面 LABELS 里的一行，重跑即可。
"""
import os
from PIL import Image

ROOT = r'F:\HBM-Source'
TANK = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'makentmgreatagain', 'textures', 'tank')
TEMPLATE = os.path.join(TANK, 'tank_SALT_WATER.png')

# 模板里罐身的主色
TEMPLATE_BODY = (48, 140, 180)

# 文字区（由模板实测得出）：y 110..114，旧文字从 x=19 开始
TEXT_Y = 110
TEXT_X = 13          # 比旧文字再靠左一点，给长名字腾地方
CLEAR_BOX = (16, 108, 78, 116)   # 清掉旧文字的区域 x0,y0,x1,y1

# 5×5 点阵字库，只收这条链要用的字母
FONT = {
    'A': ('.###.', '#...#', '#####', '#...#', '#...#'),
    'C': ('.###.', '#...#', '#....', '#...#', '.###.'),
    'D': ('####.', '#...#', '#...#', '#...#', '####.'),
    'E': ('#####', '#....', '####.', '#....', '#####'),
    'G': ('.###.', '#....', '#..##', '#...#', '.###.'),
    'I': ('#####', '..#..', '..#..', '..#..', '#####'),
    'M': ('#...#', '##.##', '#.#.#', '#...#', '#...#'),
    'N': ('#...#', '##..#', '#.#.#', '#..##', '#...#'),
    'O': ('.###.', '#...#', '#...#', '#...#', '.###.'),
    'S': ('.####', '#....', '.###.', '....#', '####.'),
    'T': ('#####', '..#..', '..#..', '..#..', '..#..'),
    'U': ('#...#', '#...#', '#...#', '#...#', '.###.'),
    'W': ('#...#', '#...#', '#.#.#', '##.##', '#...#'),
    '_': ('.....', '.....', '.....', '.....', '#####'),
}

# (输出文件名, 罐身颜色, 显示文字)
# 文字长度上限约 11 字符：模板右侧 x≈68 起是图标和条纹区，再长就会压上去。
# 所以钨酸钠液这里写 TUNGSTATE 而不是 SODIUM_TUNGSTATE ——
# 罐贴图只是装饰，完整名字在 NEI、储罐界面和语言文件里都能看到。
LABELS = [
    ('tank_SODIUM_TUNGSTATE', (0xC6, 0xD2, 0xA2), 'TUNGSTATE'),
    ('tank_WASTE_ACID',       (0x8E, 0x7B, 0x39), 'WASTE_ACID'),
]


def draw_text(im, text, x0, y0, col=(255, 255, 255, 255)):
    x = x0
    for ch in text:
        glyph = FONT.get(ch)
        if glyph is None:
            x += 6
            continue
        for ry, row in enumerate(glyph):
            for rx, c in enumerate(row):
                if c == '#':
                    im.putpixel((x + rx, y0 + ry), col)
        x += 6   # 5 宽 + 1 间距


def build(body, text):
    im = Image.open(TEMPLATE).convert('RGBA').copy()

    # 1) 罐身主色整体换色
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if (r, g, b) == TEMPLATE_BODY:
                px[x, y] = (body[0], body[1], body[2], a)

    # 2) 抹掉旧文字：把文字区里的白色像素涂回罐身色
    x0, y0, x1, y1 = CLEAR_BOX
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b, a = px[x, y]
            if (r, g, b) == (255, 255, 255):
                px[x, y] = (body[0], body[1], body[2], 255)

    # 3) 写新文字
    draw_text(im, text, TEXT_X, TEXT_Y)
    return im


def main():
    for name, body, text in LABELS:
        im = build(body, text)
        p = os.path.join(TANK, name + '.png')
        im.save(p)
        print('%-28s %s  %s' % (name, text, os.path.getsize(p)))


if __name__ == '__main__':
    main()
