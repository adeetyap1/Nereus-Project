#!/usr/bin/env python3
"""Buat konten ItemsAdder UI NereusRpg (pixel art orisinal) + pratinjau.
Pakai: python3 make_pack.py <folder keluaran, mis. itemsadder/nereus_ui> [pratinjau.png]
Butuh Pillow. Semua angka posisi ada di bagian KONFIG supaya mudah disetel."""
import os, sys, zipfile
from PIL import Image, ImageDraw

OUT = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

# ---------------- KONFIG ----------------
HUD_W, HUD_H = 82, 11
HUD_Y = -1               # y_position HUD di ItemsAdder (naik/turun). Setel di game.
GAP_PX = 16              # jarak antara bar HP dan mana (sisakan area tengah hotbar)
GUI_W, GUI_H = 176, 166
GUI_Y = 13               # y_position latar menu di ItemsAdder
STEPS = 10
NS = "nereus_ui"
# ---------------- palet ----------------
INK = (20, 15, 28, 255)
PLUM_D = (34, 27, 46, 255)
PLUM = (48, 38, 64, 255)
PLUM_L = (70, 57, 92, 255)
BRONZE_D = (96, 62, 30, 255)
BRONZE = (158, 104, 46, 255)
GOLD = (222, 172, 70, 255)
GOLD_L = (255, 224, 140, 255)
TEAL = (84, 214, 200, 255)
TEAL_D = (36, 120, 120, 255)
STEEL = (200, 210, 226, 255)
STEEL_D = (120, 130, 150, 255)
LEATHER = (110, 70, 44, 255)
CLEAR = (0, 0, 0, 0)


def rect(d, x0, y0, x1, y1, c):
    d.rectangle([x0, y0, x1, y1], fill=c)


def bevel(d, x0, y0, x1, y1, hi, lo, fill=None):
    if fill:
        rect(d, x0, y0, x1, y1, fill)
    d.line([x0, y0, x1, y0], fill=hi)
    d.line([x0, y0, x0, y1], fill=hi)
    d.line([x0, y1, x1, y1], fill=lo)
    d.line([x1, y0, x1, y1], fill=lo)


def dither(d, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if (x + y) % 2 == 0:
                d.point((x, y), fill=c)


def panel(d, x0, y0, x1, y1, fill=PLUM_D, trim=BRONZE):
    rect(d, x0, y0, x1, y1, INK)
    bevel(d, x0 + 1, y0 + 1, x1 - 1, y1 - 1, GOLD, BRONZE_D, trim)
    bevel(d, x0 + 2, y0 + 2, x1 - 2, y1 - 2, BRONZE_D, GOLD, None)
    rect(d, x0 + 3, y0 + 3, x1 - 3, y1 - 3, fill)
    dither(d, x0 + 3, y0 + 3, x1 - 3, y1 - 3, PLUM)


def gem(d, cx, cy, c, hi):
    for dx, dy in [(0, -2), (-1, -1), (0, -1), (1, -1), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (-1, 1), (0, 1), (1, 1), (0, 2)]:
        d.point((cx + dx, cy + dy), fill=c)
    d.point((cx - 1, cy - 1), fill=hi)
    d.point((cx, cy - 1), fill=hi)


def line(d, pts, c):
    d.line(pts, fill=c)


def icon_sword(d, ox, oy):
    line(d, [ox + 12, oy + 2, ox + 4, oy + 10], STEEL)
    line(d, [ox + 13, oy + 3, ox + 5, oy + 11], STEEL_D)
    line(d, [ox + 11, oy + 2, ox + 3, oy + 10], (240, 246, 255, 255))
    line(d, [ox + 3, oy + 9, ox + 7, oy + 13], GOLD)
    line(d, [ox + 2, oy + 14, ox + 4, oy + 12], LEATHER)
    d.point((ox + 1, oy + 15), fill=GOLD_L)


def icon_dagger(d, ox, oy):
    line(d, [ox + 11, oy + 3, ox + 6, oy + 8], STEEL)
    line(d, [ox + 12, oy + 4, ox + 7, oy + 9], STEEL_D)
    line(d, [ox + 5, oy + 8, ox + 8, oy + 11], (150, 80, 200, 255))
    line(d, [ox + 4, oy + 12, ox + 6, oy + 10], LEATHER)
    line(d, [ox + 3, oy + 13, ox + 4, oy + 12], GOLD)
    for i in range(3):
        d.point((ox + 2 + i, oy + 2 + i), fill=TEAL_D)


def icon_bow(d, ox, oy):
    pts = [(ox + 4, oy + 1), (ox + 8, oy + 2), (ox + 11, oy + 5), (ox + 12, oy + 8), (ox + 11, oy + 11), (ox + 8, oy + 14), (ox + 4, oy + 15)]
    d.line(pts, fill=LEATHER, width=2)
    d.line(pts, fill=BRONZE)
    line(d, [ox + 4, oy + 1, ox + 4, oy + 15], (230, 230, 240, 255))
    line(d, [ox + 2, oy + 8, ox + 12, oy + 8], STEEL)
    d.point((ox + 1, oy + 8), fill=GOLD_L)
    line(d, [ox + 11, oy + 8, ox + 13, oy + 6], STEEL)
    line(d, [ox + 11, oy + 8, ox + 13, oy + 10], STEEL)


def slot(d, x, y):
    rect(d, x, y, x + 17, y + 17, (74, 64, 92, 255))
    d.line([x, y, x + 16, y], fill=INK)
    d.line([x, y, x, y + 16], fill=INK)
    d.line([x + 1, y + 17, x + 17, y + 17], fill=PLUM_L)
    d.line([x + 17, y + 1, x + 17, y + 17], fill=PLUM_L)
    rect(d, x + 1, y + 1, x + 16, y + 16, (60, 50, 78, 255))


def gui_class():
    img = Image.new("RGBA", (GUI_W, GUI_H), PLUM_D)
    d = ImageDraw.Draw(img)
    panel(d, 0, 0, GUI_W - 1, 81)
    panel(d, 0, 82, GUI_W - 1, GUI_H - 1, fill=PLUM_D)
    # header ornament
    rect(d, 40, 5, 135, 14, INK)
    bevel(d, 41, 6, 134, 13, BRONZE_D, GOLD, PLUM)
    gem(d, 88, 10, TEAL, (200, 255, 250, 255))
    for i in range(5):
        d.point((50 + i * 4, 10), fill=GOLD)
        d.point((126 - i * 4, 10), fill=GOLD)
    # kartu class (slot 10, 13, 16 -> pusat x 34, 88, 142; slot y=36)
    for cx, icon in ((34, icon_sword), (88, icon_dagger), (142, icon_bow)):
        panel(d, cx - 22, 20, cx + 22, 78, fill=PLUM)
        rect(d, cx - 12, 33, cx + 12, 55, INK)
        bevel(d, cx - 11, 34, cx + 11, 54, BRONZE_D, GOLD, PLUM_D)
        rect(d, cx - 9, 35, cx + 8, 52, INK)
        slot(d, cx - 9, 35)
        gem(d, cx, 25, TEAL_D, TEAL)
        for k in range(3):
            gem(d, cx - 10 + k * 10, 66, (150, 110, 50, 255), GOLD_L)
    # tombol kembali (slot 8: x 152..168, y 18..34)
    rect(d, 151, 17, 168, 34, INK)
    bevel(d, 152, 18, 167, 33, GOLD, BRONZE_D, BRONZE)
    # slot inventori pemain (posisi vanilla)
    for r in range(3):
        for c in range(9):
            slot(d, 7 + 18 * c, 83 + 18 * r)
    for c in range(9):
        slot(d, 7 + 18 * c, 141)
    return img


def bar(kind, step, mirror):
    img = Image.new("RGBA", (HUD_W, HUD_H), CLEAR)
    d = ImageDraw.Draw(img)
    rect(d, 0, 1, HUD_W - 1, HUD_H - 2, INK)
    bevel(d, 1, 2, HUD_W - 2, HUD_H - 3, GOLD, BRONZE_D, BRONZE)
    ix0, ix1, iy0, iy1 = 8, HUD_W - 5, 4, HUD_H - 5
    rect(d, ix0 - 1, iy0 - 1, ix1 + 1, iy1 + 1, INK)
    rect(d, ix0, iy0, ix1, iy1, (28, 20, 38, 255))
    if kind == "hp":
        top, bot, hi = (236, 70, 96, 255), (150, 22, 52, 255), (255, 170, 184, 255)
        g, gh = (210, 50, 80, 255), (255, 170, 184, 255)
    else:
        top, bot, hi = (92, 160, 255, 255), (36, 82, 190, 255), (190, 224, 255, 255)
        g, gh = (70, 140, 240, 255), (190, 224, 255, 255)
    w = ix1 - ix0 + 1
    fw = round(w * step / STEPS)
    for x in range(ix0, ix0 + fw):
        d.point((x, iy0), fill=hi)
        for y in range(iy0 + 1, iy1 + 1):
            d.point((x, y), fill=top if y < (iy0 + iy1 + 1) // 2 + 1 else bot)
        if (x - ix0) % 6 == 5:
            for y in range(iy0, iy1 + 1):
                d.point((x, y), fill=INK)
    gem(d, 4, HUD_H // 2, g, gh)
    if mirror:
        img = img.transpose(Image.FLIP_LEFT_RIGHT)
    return img


def pair(h, m):
    w = HUD_W * 2 + GAP_PX
    img = Image.new("RGBA", (w, HUD_H), CLEAR)
    a, b = bar("hp", h, False), bar("mana", m, True)
    img.paste(a, (0, 0), a)
    img.paste(b, (HUD_W + GAP_PX, 0), b)
    return img


def main():
    tex = os.path.join(OUT, "textures/ui")
    os.makedirs(tex, exist_ok=True)
    gui = gui_class()
    gui.save(os.path.join(tex, "class_select.png"))
    lines = ["info:", f"  namespace: {NS}", "", "font_images:",
             "  class_select:", '    path: "ui/class_select.png"', f"    scale_ratio: {GUI_H}",
             f"    y_position: {GUI_Y}", "    show_in_gui: true"]
    for h in range(STEPS + 1):
        for m in range(STEPS + 1):
            pair(h, m).save(os.path.join(tex, f"hud_{h}_{m}.png"))
            lines += [f"  hud_{h}_{m}:", f'    path: "ui/hud_{h}_{m}.png"', f"    scale_ratio: {HUD_H}",
                      f"    y_position: {HUD_Y}", "    show_in_gui: true"]
    os.makedirs(os.path.join(OUT, "configs"), exist_ok=True)
    with open(os.path.join(OUT, "configs/nereus_ui.yml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    if PREVIEW:
        canvas = Image.new("RGBA", (760, 330), (22, 18, 30, 255))
        canvas.paste(gui.resize((GUI_W * 2, GUI_H * 2), Image.NEAREST), (10, 10))
        d = ImageDraw.Draw(canvas)
        for i in range(9):
            d.rectangle([380 + i * 36, 120, 380 + i * 36 + 33, 153], fill=(70, 60, 86, 255), outline=(150, 130, 170, 255))
        for k, (h, m) in enumerate(((7, 4), (10, 10), (2, 8))):
            im = pair(h, m)
            im = im.resize((im.width * 2, im.height * 2), Image.NEAREST)
            canvas.paste(im, (380, 40 + k * 0 if k == 0 else 190 + (k - 1) * 40), im)
        canvas.save(PREVIEW)


main()
zp = OUT.rstrip("/") + ".zip"
with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(OUT):
        for fn in files:
            full = os.path.join(root, fn)
            z.write(full, os.path.join(NS, os.path.relpath(full, OUT)))
print("zip:", zp)
