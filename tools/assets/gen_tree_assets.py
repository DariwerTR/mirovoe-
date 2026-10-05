#!/usr/bin/env python3
"""Генератор ресурсов деревьев v2: текстуры 32x32, модели, blockstate, items, loot tables, теги, lang.

Запуск из корня репозитория:  python3 tools/assets/gen_tree_assets.py [--vanilla-loot PATH_TO_LOOT_DIR]
Зависимости: numpy, pillow.
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
A = os.path.join(RES, "assets", "wildlands")
D = os.path.join(RES, "data", "wildlands")
DM = os.path.join(RES, "data", "minecraft")
N = 128
K = N / 64.0  # масштаб размеров в пикселях относительно базовых 64
SPECIES = ["oak", "birch", "spruce"]
NAMES_EN = {"oak": "Oak", "birch": "Birch", "spruce": "Spruce"}
NAMES_RU = {"oak": "Дуб", "birch": "Берёза", "spruce": "Ель"}


def w(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


# ---------------------------------------------------------------- шум

def tile_noise(rng, cx, cy):
    """Периодический value-noise NxN с ячейками cx x cy, значения 0..1."""
    lat = rng.random((cy, cx))
    ys, xs = np.mgrid[0:N, 0:N]
    fx = xs / N * cx
    fy = ys / N * cy
    x0 = np.floor(fx).astype(int)
    y0 = np.floor(fy).astype(int)
    tx = fx - x0
    ty = fy - y0
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    x1 = (x0 + 1) % cx
    y1 = (y0 + 1) % cy
    x0 %= cx
    y0 %= cy
    a = lat[y0, x0] * (1 - tx) + lat[y0, x1] * tx
    b = lat[y1, x0] * (1 - tx) + lat[y1, x1] * tx
    return a * (1 - ty) + b * ty


def fbm(rng, cx, cy, octaves=4, gain=0.5):
    total = np.zeros((N, N))
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        total += amp * tile_noise(rng, cx * 2 ** o, cy * 2 ** o)
        norm += amp
        amp *= gain
    return total / norm


def blur(a, passes=1):
    for _ in range(passes):
        a = (a * 4 + np.roll(a, 1, 0) * 2 + np.roll(a, -1, 0) * 2 + np.roll(a, 1, 1) * 2 + np.roll(a, -1, 1) * 2
             + np.roll(np.roll(a, 1, 0), 1, 1) + np.roll(np.roll(a, 1, 0), -1, 1)
             + np.roll(np.roll(a, -1, 0), 1, 1) + np.roll(np.roll(a, -1, 0), -1, 1)) / 16.0
    return a


def warp(h, rng, amp, cx=3, cy=3):
    wx = (fbm(rng, cx, cy, 3) - 0.5) * amp
    wy = (fbm(rng, cx, cy, 3) - 0.5) * amp
    ys, xs = np.mgrid[0:N, 0:N]
    return h[(ys + wy.round().astype(int)) % N, (xs + wx.round().astype(int)) % N]


def lit(h, strength=5.0, light=(-0.55, -0.8)):
    """Освещение по карте высот: светлее на склонах, обращённых к свету."""
    gx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) * 0.5
    gy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) * 0.5
    return np.clip(1.0 + (gx * light[0] + gy * light[1]) * strength * K, 0.35, 1.7)


def ramp(v, colors):
    v = np.clip(v, 0, 0.9999) * (len(colors) - 1)
    i = np.floor(v).astype(int)
    t = (v - i)[..., None]
    c = np.array(colors, dtype=float)
    return c[i] * (1 - t) + c[i + 1] * t


def to_img(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def grain(rng, arr, amount, cx=48, cy=6):
    g = fbm(rng, cx, cy, 2) - 0.5
    return arr * (1 + g[..., None] * amount) + (rng.random((N, N, 1)) - 0.5) * amount * 14


def smooth(x, e0, e1):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


# --------------------------------------------------------------- кора

def bark_oak(seed):
    """Дуб: вилкообразные продольные борозды, плоские пластины с мелкой фактурой, лишайник и мох."""
    rng = np.random.default_rng(seed)
    n = fbm(rng, 22, 2, 3, gain=0.35)
    n = warp(n, rng, 2.5 * K, 4, 4)
    n = blur(n, 1)
    furrow = np.abs(n - 0.5)
    h = smooth(furrow, 0.004, 0.05)
    # ромбовидные разрывы пластин: поперечная модуляция
    h = h * (0.55 + 0.45 * smooth(fbm(rng, 6, 12, 2), 0.3, 0.6))
    # продольные чешуйки и мелкая фактура на пластинах
    h = h * (0.8 + 0.2 * fbm(rng, 30, 6, 3)) + 0.05 * (fbm(rng, 64, 20, 2) - 0.5)
    h = blur(h, 1)
    shade = lit(h, 3.2)
    pal = [(12, 10, 8), (36, 30, 25), (70, 60, 51), (102, 90, 77), (128, 115, 100)]
    img = ramp(np.clip(h, 0, 1), pal) * shade[..., None]
    tone = fbm(rng, 4, 6, 3) - 0.5
    img = img * (1 + tone[..., None] * 0.3) + np.array([9, 4, -4]) * tone[..., None] * 2
    lich = smooth(fbm(rng, 5, 5, 4), 0.66, 0.84) * smooth(h, 0.6, 0.9)
    moss = smooth(fbm(rng, 3, 4, 3), 0.55, 0.78) * (1 - smooth(h, 0.2, 0.7)) * 0.55
    img = img * (1 - 0.5 * lich[..., None]) + np.array([150, 158, 128]) * 0.5 * lich[..., None] * shade[..., None]
    img = img * (1 - moss[..., None]) + np.array([46, 62, 28]) * (0.5 + 0.7 * shade[..., None]) * moss[..., None]
    img = grain(rng, img, 0.2, 96, 10)
    return to_img(img)


def bark_birch(seed):
    """Берёза: белая бумажная кора, тёмные горизонтальные чечевички, серые разводы."""
    rng = np.random.default_rng(seed)
    base = fbm(rng, 5, 5, 4)
    paper = ramp(0.35 + 0.65 * base, [(176, 172, 160), (206, 203, 192), (226, 224, 214), (240, 238, 230)])
    # шелушение: тонкие горизонтальные слои
    layers = fbm(rng, 3, 24, 2)
    paper *= (0.92 + 0.14 * layers)[..., None]
    mask = np.zeros((N, N))
    py = random.Random(seed)
    ys, xs = np.mgrid[0:N, 0:N]
    for _ in range(14):
        cy, cx = py.uniform(0, N), py.uniform(0, N)
        ln, th = py.uniform(7, 22) * K, py.uniform(0.9, 2.4) * K
        dx = np.minimum(np.abs(xs - cx), N - np.abs(xs - cx))
        dy = np.minimum(np.abs(ys - cy), N - np.abs(ys - cy))
        d = (dx / ln) ** 2 + (dy / th) ** 2
        mask = np.maximum(mask, smooth(1.0 - d, 0.0, 0.35))
    # рваные края: обрезаем метки горизонтальным шумом, добавляем тонкие трещинки
    rag = fbm(rng, 10, 56, 3)
    mask = mask * smooth(rag, 0.38, 0.55)
    fine = smooth(fbm(rng, 4, 70, 2), 0.7, 0.78) * smooth(fbm(rng, 5, 3, 2), 0.5, 0.7)
    mask = np.clip(np.maximum(mask, fine * 0.8), 0, 1)
    dark = np.array([26, 24, 23]) * (0.8 + 0.6 * fbm(rng, 20, 20, 2))[..., None]
    img = paper * (1 - mask[..., None]) + dark * mask[..., None]
    # серо-коричневый налёт у основания меток
    halo = blur(mask, 2) * (1 - mask)
    img = img * (1 - 0.35 * halo[..., None]) + np.array([120, 110, 98]) * 0.35 * halo[..., None]
    return to_img(grain(rng, img, 0.12, 40, 40))


def bark_spruce(seed):
    """Ель: чешуйчатые пластины (ячейки Вороного, вытянутые вдоль ствола), красновато-бурые."""
    rng = np.random.default_rng(seed)
    pts = rng.random((70, 2)) * N
    ys, xs = np.mgrid[0:N, 0:N]
    best = np.full((N, N), 1e9)
    second = np.full((N, N), 1e9)
    idx = np.zeros((N, N), dtype=int)
    for i, (px, py) in enumerate(pts):
        dx = np.minimum(np.abs(xs - px), N - np.abs(xs - px))
        dy = np.minimum(np.abs(ys - py), N - np.abs(ys - py)) * 0.38
        d = np.hypot(dx, dy) / K
        mask = d < best
        second = np.where(mask, best, np.minimum(second, d))
        best = np.where(mask, d, best)
        idx = np.where(mask, i, idx)
    tone = rng.random(len(pts))[idx]
    gap = smooth((second - best), 0.0, 1.6)
    h = gap * (0.65 + 0.35 * tone) + 0.08 * (fbm(rng, 40, 14, 3) - 0.5)
    h = warp(h, rng, 3 * K, 6, 6)
    h = blur(h, 1)
    shade = lit(h, 2.4)
    pal = [(26, 21, 19), (62, 52, 46), (96, 80, 70), (122, 104, 92), (144, 126, 112)]
    img = ramp(h * 0.9 + 0.1 * tone, pal) * shade[..., None]
    img = grain(rng, img, 0.2, 48, 8)
    return to_img(img)


# ------------------------------------------------------------ торец

def log_top(bark_img, wood, seed):
    rng = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:N, 0:N]
    c = (N - 1) / 2.0
    warpn = fbm(rng, 3, 3, 3)
    d = np.hypot(xs - c, ys - c) + (warpn - 0.5) * 5.0 * K
    ring = np.abs(np.sin(d * 0.62 / K + 0.6 * fbm(rng, 4, 4, 2)))
    ring = ring ** 0.55
    dark, light = np.array(wood[0], float), np.array(wood[1], float)
    img = dark * (1 - ring[..., None]) + light * ring[..., None]
    # сердцевина темнее, заболонь светлее
    core = smooth(1 - d / (c * 0.9), 0.55, 1.0)[..., None]
    img = img * (1 - 0.35 * core)
    sap = smooth(d / c, 0.62, 0.95)[..., None]
    img = img * (1 - sap * 0.25) + light * 1.08 * sap * 0.25
    # радиальные трещины
    ang = np.arctan2(ys - c, xs - c)
    crack = smooth(0.09 - np.abs(np.sin(ang * 2.5 + 3 * fbm(rng, 3, 3, 2))), 0.0, 0.07)
    crack *= smooth(d - 5 * K, 0.0, 6 * K) * smooth(1 - d / (c * 1.1), 0.0, 0.3)
    img = img * (1 - 0.45 * crack[..., None])
    img = img * (0.9 + 0.2 * fbm(rng, 20, 20, 2))[..., None]
    img = grain(rng, img, 0.12, 40, 40)
    bark = np.array(bark_img, dtype=float)
    edge = np.minimum.reduce([xs, ys, N - 1 - xs, N - 1 - ys])
    t = smooth(5.5 * K - edge, 0.0, 1.5 * K)[..., None]
    img = img * (1 - t) + bark * t
    return to_img(img)


# ------------------------------------------------------------ листва

SS = 4  # суперсэмплинг: рисуем в 4x и уменьшаем, чтобы края были мягкими


def _poly(pts, cx, cy, ang, ox, oy):
    ca, sa = math.cos(ang), math.sin(ang)
    return [((cx + ox + px * ca - py * sa) * SS, (cy + oy + px * sa + py * ca) * SS) for px, py in pts]


def _shade(col, f):
    return tuple(max(0, min(255, int(c * f))) for c in col)


def leaves_broad(seed, bg, palette, counts, length, width, lobes=0.0, tooth=0.0):
    """Многослойная листва: задний слой темнее, передний ярче; тени под листьями, жилки, мягкие края."""
    r = random.Random(seed)
    big = Image.new("RGBA", (N * SS, N * SS), bg + (255,))
    for layer, (count, bright) in enumerate(zip(counts, (0.5, 0.78, 1.0))):
        dr = ImageDraw.Draw(big, "RGBA")
        for _ in range(count):
            cx, cy = r.uniform(0, N), r.uniform(0, N)
            ang = r.uniform(0, math.pi * 2)
            ln = length * r.uniform(0.75, 1.25) * (0.8 + 0.2 * layer)
            wd = width * r.uniform(0.8, 1.2)
            col = _shade(r.choice(palette), bright * r.uniform(0.88, 1.12))
            side, other = [], []
            for k in range(16):
                t = k / 15
                prof = math.sin(math.pi * t ** 0.85) ** 0.9
                prof *= 1 + lobes * math.sin(t * math.pi * 6) + tooth * math.sin(t * math.pi * 16)
                px = (t - 0.5) * ln
                side.append((px, prof * wd * 0.5))
                other.append((px, -prof * wd * 0.5))
            pts = side + other[::-1]
            for ox in (-N, 0, N):
                for oy in (-N, 0, N):
                    if abs(cx + ox - N / 2) > N * 0.9 or abs(cy + oy - N / 2) > N * 0.9:
                        continue
                    dr.polygon(_poly(pts, cx, cy, ang, ox + 0.7 * K, oy + 1.0 * K), fill=(0, 0, 0, 90))
                    dr.polygon(_poly(pts, cx, cy, ang, ox, oy), fill=_shade(col, 0.82) + (255,))
                    inner = [(px * 0.78, py * 0.55 - wd * 0.05) for px, py in pts]
                    dr.polygon(_poly(inner, cx, cy, ang, ox, oy), fill=_shade(col, 1.12) + (255,))
                    rib = [(-ln * 0.5, 0), (ln * 0.5, 0)]
                    dr.line(_poly(rib, cx, cy, ang, ox, oy), fill=_shade(col, 0.62) + (255,), width=max(1, int(SS * K) // 3))
                    for vt in (0.2, 0.4, 0.6, 0.8):
                        px = (vt - 0.5) * ln
                        for sgn in (1, -1):
                            v = [(px, 0), (px + ln * 0.12, sgn * wd * 0.32)]
                            dr.line(_poly(v, cx, cy, ang, ox, oy), fill=_shade(col, 0.72) + (200,), width=1)
    return big.resize((N, N), Image.LANCZOS).convert("RGB")


def leaves_needles(seed, bg, palette, counts, length):
    """Хвоя: веточки с парными иглами под углом, три слоя глубины."""
    r = random.Random(seed)
    big = Image.new("RGBA", (N * SS, N * SS), bg + (255,))
    for layer, (count, bright) in enumerate(zip(counts, (0.5, 0.78, 1.0))):
        dr = ImageDraw.Draw(big, "RGBA")
        for _ in range(count):
            cx, cy = r.uniform(0, N), r.uniform(0, N)
            ang = r.uniform(0, math.pi * 2)
            ln = length * r.uniform(1.4, 2.2)
            col = _shade(r.choice(palette), bright * r.uniform(0.85, 1.15))
            bend = r.uniform(-0.25, 0.25)
            for ox in (-N, 0, N):
                for oy in (-N, 0, N):
                    pts = []
                    for k in range(9):
                        t = k / 8
                        a = ang + bend * t
                        pts.append((cx + ox + math.cos(a) * ln * t, cy + oy + math.sin(a) * ln * t))
                    dr.line([(x * SS + 2 * K, y * SS + 3 * K) for x, y in pts], fill=(0, 0, 0, 80), width=int(SS * K))
                    dr.line([(x * SS, y * SS) for x, y in pts], fill=(58, 42, 30, 255), width=max(1, int(SS * K) // 2))
                    for k in range(1, 9):
                        px, py = pts[k]
                        for sgn in (1, -1):
                            a = ang + bend * k / 8 + sgn * 0.95
                            nl = length * r.uniform(0.55, 0.85) * (1 - k / 14)
                            e = (px + math.cos(a) * nl, py + math.sin(a) * nl)
                            dr.line([(px * SS, py * SS), (e[0] * SS, e[1] * SS)],
                                    fill=_shade(col, r.uniform(0.85, 1.2)) + (255,), width=max(2, int(SS * K) // 2 + 1))
    return big.resize((N, N), Image.LANCZOS).convert("RGB")


# ------------------------------------------------------------- модели

CUSTOM = "--custom-textures" in sys.argv


def bark_ref(sp):
    return f"wildlands:block/{sp}_bark" if CUSTOM else f"minecraft:block/{sp}_log"


def top_ref(sp):
    return f"wildlands:block/{sp}_log_top" if CUSTOM else f"minecraft:block/{sp}_log_top"


LEAF_TINT = {"oak": (0x48, 0xB5, 0x18), "birch": (0x80, 0xA7, 0x55), "spruce": (0x61, 0x99, 0x61)}


def vanilla_leaves(sp, jar):
    """Ванильная текстура листвы, окрашенная в стандартный цвет породы; прозрачные места заполнены тенью (непрозрачно)."""
    import io
    import zipfile
    with zipfile.ZipFile(jar) as z:
        im = Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/block/{sp}_leaves.png"))).convert("RGBA")
    a = np.array(im, dtype=float)
    tint = np.array(LEAF_TINT[sp], dtype=float) / 255.0
    rgb = a[..., :3] * tint
    hole = a[..., 3] < 128
    rgb[hole] = np.array(LEAF_TINT[sp], dtype=float) * 0.28
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB")


def face(uv, tex="#bark", cull=None):
    f = {"uv": uv, "texture": tex}
    if cull:
        f["cullface"] = cull
    return f


def core_model(sp, t):
    a, b = 8 - t, 8 + t
    return {
        "parent": "minecraft:block/block",
        "textures": {"bark": bark_ref(sp), "particle": "#bark"},
        "elements": [{
            "from": [a, a, a], "to": [b, b, b],
            "faces": {k: face([a, a, b, b]) for k in ("north", "south", "east", "west", "up", "down")},
        }],
    }


def arm_model(sp, t):
    """Рукав на север: от грани блока до ядра."""
    a, b = 8 - t, 8 + t
    return {
        "parent": "minecraft:block/block",
        "textures": {"bark": bark_ref(sp), "particle": "#bark"},
        "elements": [{
            "from": [a, a, 0], "to": [b, b, a],
            "faces": {
                "north": face([a, a, b, b], cull="north"),
                "up": face([a, 0, b, a]),
                "down": face([a, 0, b, a]),
                "east": face([0, a, a, b]),
                "west": face([0, a, a, b]),
            },
        }],
    }


ARMS = [("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}),
        ("up", {"x": 270}), ("down", {"x": 90})]


def branch_state(sp):
    parts = []
    for t in range(1, 8):
        parts.append({"when": {"thickness": str(t)}, "apply": {"model": f"wildlands:block/{sp}_branch_core_{t}"}})
    for t in range(1, 8):
        for d, rot in ARMS:
            apply = {"model": f"wildlands:block/{sp}_branch_arm_{t}"}
            apply.update(rot)
            parts.append({"when": {"AND": [{"thickness": str(t)}, {d: "true"}]}, "apply": apply})
    return {"multipart": parts}


# ------------------------------------------------------------ loot/теги

def loot_self(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "bonus_rolls": 0.0, "rolls": 1.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"wildlands:{name}"}],
        }],
        "random_sequence": f"wildlands:blocks/{name}",
    }


def loot_branch(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "bonus_rolls": 0.0, "rolls": 1.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{
                "type": "minecraft:item", "name": "minecraft:stick",
                "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 3.0}}],
            }],
        }],
        "random_sequence": f"wildlands:blocks/{name}",
    }


def loot_leaves(sp, vanilla_dir):
    path = os.path.join(vanilla_dir, f"{sp}_leaves.json") if vanilla_dir else None
    if path and os.path.exists(path):
        text = open(path, encoding="utf-8").read()
        text = text.replace(f'"minecraft:{sp}_leaves"', f'"wildlands:{sp}_leaves"', 1)
        text = text.replace(f'"minecraft:blocks/{sp}_leaves"', f'"wildlands:blocks/{sp}_leaves"')
        return json.loads(text)
    silk = {"condition": "minecraft:any_of", "terms": [
        {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
        {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}
    return {
        "type": "minecraft:block",
        "pools": [
            {"bonus_rolls": 0.0, "rolls": 1.0, "entries": [{
                "type": "minecraft:item", "name": f"wildlands:{sp}_leaves", "conditions": [silk]}]},
            {"bonus_rolls": 0.0, "rolls": 1.0,
             "conditions": [{"condition": "minecraft:inverted", "term": silk},
                            {"condition": "minecraft:survives_explosion"}],
             "entries": [
                 {"type": "minecraft:item", "name": f"minecraft:{sp}_sapling",
                  "conditions": [{"condition": "minecraft:random_chance", "chance": 0.05}]},
                 {"type": "minecraft:item", "name": "minecraft:stick",
                  "conditions": [{"condition": "minecraft:random_chance", "chance": 0.1}]}]},
        ],
        "random_sequence": f"wildlands:blocks/{sp}_leaves",
    }


def tag(values):
    return {"replace": False, "values": values}


def main():
    vanilla = None
    if "--vanilla-loot" in sys.argv:
        vanilla = sys.argv[sys.argv.index("--vanilla-loot") + 1]
    tex = os.path.join(A, "textures", "block")
    os.makedirs(tex, exist_ok=True)

    if CUSTOM:
        barks = {
            "oak": bark_oak(11), "birch": bark_birch(23), "spruce": bark_spruce(37),
        }
        woods = {
            "oak": ((120, 88, 52), (196, 158, 104)),
            "birch": ((176, 154, 104), (230, 214, 168)),
            "spruce": ((96, 68, 38), (160, 120, 74)),
        }
        leaves = {
            "oak": leaves_broad(5, (6, 20, 6), [(44, 104, 34), (58, 124, 40), (72, 142, 46), (36, 88, 30), (92, 160, 56), (110, 150, 50)],
                                tuple(int(c * K * K) for c in (46, 40, 30)), 15.0 * K, 8.0 * K, lobes=0.16),
            "birch": leaves_broad(8, (14, 30, 8), [(104, 150, 44), (126, 172, 54), (146, 186, 62), (92, 134, 40), (170, 196, 76)],
                                  tuple(int(c * K * K) for c in (60, 52, 40)), 11.0 * K, 6.5 * K, tooth=0.07),
            "spruce": leaves_needles(9, (8, 26, 16), [(44, 104, 66), (56, 126, 78), (36, 88, 58), (74, 142, 88), (60, 112, 96)],
                                     tuple(int(c * K * K) for c in (30, 28, 22)), 8.5 * K),
        }
        for sp in SPECIES:
            barks[sp].save(os.path.join(tex, f"{sp}_bark.png"))
            log_top(barks[sp], woods[sp], 90 + len(sp)).save(os.path.join(tex, f"{sp}_log_top.png"))
            leaves[sp].save(os.path.join(tex, f"{sp}_leaves.png"))
    
    else:
        jar = sys.argv[sys.argv.index("--vanilla-jar") + 1] if "--vanilla-jar" in sys.argv else None
        for sp in SPECIES:
            for old in ("bark", "log_top"):
                f = os.path.join(tex, f"{sp}_{old}.png")
                if os.path.exists(f):
                    os.remove(f)
            if jar:
                vanilla_leaves(sp, jar).save(os.path.join(tex, f"{sp}_leaves.png"))

    lang_en, lang_ru = {}, {}
    for sp in SPECIES:
        # модели
        w(os.path.join(A, "models", "block", f"{sp}_log.json"), {
            "parent": "minecraft:block/cube_column",
            "textures": {"end": top_ref(sp), "side": bark_ref(sp)},
        })
        w(os.path.join(A, "models", "block", f"{sp}_leaves.json"), {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"wildlands:block/{sp}_leaves"},
        })
        for t in range(1, 8):
            w(os.path.join(A, "models", "block", f"{sp}_branch_core_{t}.json"), core_model(sp, t))
            w(os.path.join(A, "models", "block", f"{sp}_branch_arm_{t}.json"), arm_model(sp, t))
        # blockstate
        w(os.path.join(A, "blockstates", f"{sp}_log.json"), {"variants": {
            "axis=y": {"model": f"wildlands:block/{sp}_log"},
            "axis=z": {"model": f"wildlands:block/{sp}_log", "x": 90},
            "axis=x": {"model": f"wildlands:block/{sp}_log", "x": 90, "y": 90},
        }})
        w(os.path.join(A, "blockstates", f"{sp}_leaves.json"), {"variants": {
            "persistent=true": {"model": f"wildlands:block/{sp}_leaves"},
            "persistent=false": {"model": f"wildlands:block/{sp}_leaves"},
        }})
        w(os.path.join(A, "blockstates", f"{sp}_branch.json"), branch_state(sp))
        # предметы
        w(os.path.join(A, "items", f"{sp}_log.json"), {"model": {"type": "minecraft:model", "model": f"wildlands:block/{sp}_log"}})
        w(os.path.join(A, "items", f"{sp}_leaves.json"), {"model": {"type": "minecraft:model", "model": f"wildlands:block/{sp}_leaves"}})
        w(os.path.join(A, "items", f"{sp}_branch.json"), {"model": {"type": "minecraft:model", "model": f"wildlands:block/{sp}_branch_core_5"}})
        # loot
        w(os.path.join(D, "loot_table", "blocks", f"{sp}_log.json"), loot_self(f"{sp}_log"))
        w(os.path.join(D, "loot_table", "blocks", f"{sp}_branch.json"), loot_branch(f"{sp}_branch"))
        w(os.path.join(D, "loot_table", "blocks", f"{sp}_leaves.json"), loot_leaves(sp, vanilla))
        # lang
        for kind, en, ru in (("log", "Log", "Бревно"), ("branch", "Branch", "Ветвь"), ("leaves", "Leaves", "Листва")):
            for pre in ("block", "item"):
                lang_en[f"{pre}.wildlands.{sp}_{kind}"] = f"Wild {NAMES_EN[sp]} {en}"
                lang_ru[f"{pre}.wildlands.{sp}_{kind}"] = f"{ru}: {NAMES_RU[sp].lower()} (дикая)" if False else f"Дикая {NAMES_RU[sp].lower()}: {ru.lower()}"

    # теги
    logs = [f"wildlands:{sp}_log" for sp in SPECIES]
    branches = [f"wildlands:{sp}_branch" for sp in SPECIES]
    leaf = [f"wildlands:{sp}_leaves" for sp in SPECIES]
    w(os.path.join(DM, "tags", "block", "logs.json"), tag(logs + branches))
    w(os.path.join(DM, "tags", "block", "logs_that_burn.json"), tag(logs + branches))
    w(os.path.join(DM, "tags", "block", "mineable", "axe.json"), tag(logs + branches))
    w(os.path.join(DM, "tags", "block", "mineable", "hoe.json"), tag(leaf))
    w(os.path.join(DM, "tags", "block", "leaves.json"), tag(leaf))
    w(os.path.join(DM, "tags", "block", "overworld_natural_logs.json"), tag(logs))
    w(os.path.join(DM, "tags", "item", "logs.json"), tag(logs))
    w(os.path.join(DM, "tags", "item", "logs_that_burn.json"), tag(logs))
    w(os.path.join(DM, "tags", "item", "leaves.json"), tag(leaf))
    for sp in SPECIES:
        w(os.path.join(DM, "tags", "block", f"{sp}_logs.json"), tag([f"wildlands:{sp}_log", f"wildlands:{sp}_branch"]))
        w(os.path.join(DM, "tags", "item", f"{sp}_logs.json"), tag([f"wildlands:{sp}_log"]))

    # lang (дописываем к существующим)
    for code, add in (("en_us", lang_en), ("ru_ru", lang_ru)):
        p = os.path.join(A, "lang", f"{code}.json")
        cur = json.load(open(p, encoding="utf-8")) if os.path.exists(p) else {}
        cur.update(add)
        w(p, cur)

    if CUSTOM:
        # контактный лист текстур для проверки
        sheet = Image.new("RGB", ((N * 2 + 4) * 3, (N * 2 + 4) * 3), (200, 220, 235))
        for i, sp in enumerate(SPECIES):
            for j, name in enumerate((f"{sp}_bark", f"{sp}_log_top", f"{sp}_leaves")):
                im = Image.open(os.path.join(tex, name + ".png")).resize((N * 2, N * 2), Image.NEAREST)
                sheet.paste(im, (j * (N * 2 + 4), i * (N * 2 + 4)))
        out = os.path.join(ROOT, "build", "preview", "png")
        os.makedirs(out, exist_ok=True)
        sheet.save(os.path.join(out, "textures.png"))
    print("готово: текстуры, модели, blockstates, items, loot, теги, lang")


if __name__ == "__main__":
    main()
