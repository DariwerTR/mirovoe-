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
N = 32
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
    """Периодический value-noise 32x32 с ячейками cx x cy, значения 0..1."""
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


def fbm(rng, cx, cy, octaves=3):
    total = np.zeros((N, N))
    amp = 1.0
    norm = 0.0
    for o in range(octaves):
        total += amp * tile_noise(rng, cx * 2 ** o, cy * 2 ** o)
        norm += amp
        amp *= 0.5
    return total / norm


def ramp(v, colors):
    """Раскрашивает массив 0..1 по палитре с интерполяцией."""
    v = np.clip(v, 0, 0.9999) * (len(colors) - 1)
    i = np.floor(v).astype(int)
    t = (v - i)[..., None]
    c = np.array(colors, dtype=float)
    return c[i] * (1 - t) + c[i + 1] * t


def to_img(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def speckle(rng, arr, amount):
    return arr + (rng.random((N, N, 1)) - 0.5) * amount


# --------------------------------------------------------------- кора

def bark_oak(seed):
    rng = np.random.default_rng(seed)
    n1 = fbm(rng, 8, 2, 3)
    n2 = fbm(rng, 5, 3, 2)
    ridge = 1 - np.abs(2 * n1 - 1)
    v = 0.55 * ridge + 0.45 * n2
    pal = [(28, 21, 15), (48, 36, 26), (70, 54, 38), (92, 72, 52), (112, 90, 66)]
    img = ramp(v, pal)
    # тёмные трещины
    crack = fbm(rng, 10, 3, 2)
    img[crack < 0.26] *= 0.55
    return to_img(speckle(rng, img, 10))


def bark_birch(seed):
    rng = np.random.default_rng(seed)
    n = fbm(rng, 4, 4, 3)
    pal = [(196, 192, 180), (214, 211, 200), (230, 228, 218), (238, 236, 228)]
    img = ramp(n, pal)
    py = random.Random(seed)
    for _ in range(11):
        y = py.randrange(N)
        x = py.randrange(N)
        length = py.randint(5, 13)
        th = py.choice([1, 1, 2])
        shade = py.choice([(30, 28, 27), (44, 41, 39), (62, 58, 54)])
        for dx in range(length):
            for dy in range(th):
                taper = 0.0 if (dx in (0, length - 1) and th == 2 and dy == 1) else 1.0
                if taper:
                    img[(y + dy) % N, (x + dx) % N] = shade
    # серые пятна вокруг отметин
    img = img * (0.93 + 0.07 * fbm(rng, 6, 6, 2)[..., None])
    return to_img(speckle(rng, img, 8))


def bark_spruce(seed):
    """Чешуйчатая кора: вытянутые по вертикали пластины (ячейки Вороного) с тёмными щелями."""
    rng = np.random.default_rng(seed)
    pts = rng.random((16, 2)) * N
    base = [(92, 62, 46), (108, 76, 56), (78, 52, 40), (122, 88, 66), (98, 68, 50), (84, 58, 44)]
    cols = [np.array(base[i % len(base)], dtype=float) for i in range(len(pts))]
    ys, xs = np.mgrid[0:N, 0:N]
    best = np.full((N, N), 1e9)
    second = np.full((N, N), 1e9)
    idx = np.zeros((N, N), dtype=int)
    for i, (px, py) in enumerate(pts):
        dx = np.minimum(np.abs(xs - px), N - np.abs(xs - px))
        dy = np.minimum(np.abs(ys - py), N - np.abs(ys - py)) * 0.42
        d = np.hypot(dx, dy)
        mask = d < best
        second = np.where(mask, best, np.minimum(second, d))
        best = np.where(mask, d, best)
        idx = np.where(mask, i, idx)
    img = np.zeros((N, N, 3))
    for i in range(len(pts)):
        img[idx == i] = cols[i]
    edge = (second - best) < 0.9
    img[edge] *= 0.5
    img = img * (0.85 + 0.3 * fbm(rng, 6, 6, 2)[..., None])
    return to_img(speckle(rng, img, 12))


# ------------------------------------------------------------ торец

def log_top(bark_img, wood, seed):
    rng = np.random.default_rng(seed)
    warp = fbm(rng, 3, 3, 2)
    ys, xs = np.mgrid[0:N, 0:N]
    d = np.hypot(xs - 15.5, ys - 15.5) + (warp - 0.5) * 3.0
    rings = 0.5 + 0.5 * np.sin(d * 1.15)
    dark, light = np.array(wood[0], float), np.array(wood[1], float)
    img = dark * (1 - rings[..., None]) + light * rings[..., None]
    img = img * (0.92 + 0.16 * fbm(rng, 5, 5, 2)[..., None])
    bark = np.array(bark_img, dtype=float)
    edge = np.minimum.reduce([xs, ys, N - 1 - xs, N - 1 - ys]) < 3
    img[edge] = bark[edge]
    return to_img(speckle(rng, img, 8))


# ------------------------------------------------------------ листва

def leaves_broad(seed, bg, palette, count, length, width):
    """Листья-эллипсы на тёмном фоне. Бесшовно: каждый рисуется со сдвигами на период."""
    r = random.Random(seed)
    img = Image.new("RGB", (N, N), bg)
    dr = ImageDraw.Draw(img)
    for _ in range(count):
        cx, cy = r.uniform(0, N), r.uniform(0, N)
        ang = r.uniform(0, math.pi * 2)
        ln = length * r.uniform(0.75, 1.25)
        wd = width * r.uniform(0.8, 1.2)
        col = r.choice(palette)
        pts = []
        for k in range(10):
            t = k / 9
            px = (t - 0.5) * ln
            py = math.sin(t * math.pi) * wd * 0.5
            pts.append((px, py))
        for k in range(9, -1, -1):
            t = k / 9
            px = (t - 0.5) * ln
            py = -math.sin(t * math.pi) * wd * 0.5
            pts.append((px, py))
        ca, sa = math.cos(ang), math.sin(ang)
        for ox in (-N, 0, N):
            for oy in (-N, 0, N):
                poly = [(cx + ox + px * ca - py * sa, cy + oy + px * sa + py * ca) for px, py in pts]
                dr.polygon(poly, fill=col)
                # жилка
                dr.line([poly[0], poly[9]], fill=tuple(int(c * 0.7) for c in col))
    return img


def leaves_needles(seed, bg, palette, count, length):
    r = random.Random(seed)
    img = Image.new("RGB", (N, N), bg)
    dr = ImageDraw.Draw(img)
    for _ in range(count):
        cx, cy = r.uniform(0, N), r.uniform(0, N)
        base = r.uniform(0, math.pi * 2)
        col = r.choice(palette)
        for k in range(r.randint(5, 9)):
            a = base + (k - 3) * 0.35 + r.uniform(-0.1, 0.1)
            ln = length * r.uniform(0.7, 1.2)
            for ox in (-N, 0, N):
                for oy in (-N, 0, N):
                    dr.line([(cx + ox, cy + oy), (cx + ox + math.cos(a) * ln, cy + oy + math.sin(a) * ln)], fill=col)
    return img


# ------------------------------------------------------------- модели

def face(uv, tex="#bark", cull=None):
    f = {"uv": uv, "texture": tex}
    if cull:
        f["cullface"] = cull
    return f


def core_model(sp, t):
    a, b = 8 - t, 8 + t
    return {
        "parent": "minecraft:block/block",
        "textures": {"bark": f"wildlands:block/{sp}_bark", "particle": "#bark"},
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
        "textures": {"bark": f"wildlands:block/{sp}_bark", "particle": "#bark"},
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

    barks = {
        "oak": bark_oak(11), "birch": bark_birch(23), "spruce": bark_spruce(37),
    }
    woods = {
        "oak": ((150, 112, 66), (190, 152, 98)),
        "birch": ((196, 176, 122), (226, 210, 160)),
        "spruce": ((112, 82, 48), (150, 112, 68)),
    }
    leaves = {
        "oak": leaves_broad(5, (14, 40, 14), [(36, 96, 30), (48, 118, 38), (62, 138, 44), (30, 80, 28), (78, 154, 52)], 34, 9.0, 5.0),
        "birch": leaves_broad(8, (30, 60, 20), [(96, 150, 44), (118, 172, 52), (138, 188, 62), (84, 134, 40), (160, 196, 70)], 44, 7.0, 4.0),
        "spruce": leaves_needles(9, (10, 30, 20), [(24, 72, 44), (32, 90, 54), (20, 58, 38), (44, 108, 62)], 20, 6.5),
    }
    for sp in SPECIES:
        barks[sp].save(os.path.join(tex, f"{sp}_bark.png"))
        log_top(barks[sp], woods[sp], 90 + len(sp)).save(os.path.join(tex, f"{sp}_log_top.png"))
        leaves[sp].save(os.path.join(tex, f"{sp}_leaves.png"))

    lang_en, lang_ru = {}, {}
    for sp in SPECIES:
        # модели
        w(os.path.join(A, "models", "block", f"{sp}_log.json"), {
            "parent": "minecraft:block/cube_column",
            "textures": {"end": f"wildlands:block/{sp}_log_top", "side": f"wildlands:block/{sp}_bark"},
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

    # контактный лист текстур для проверки
    sheet = Image.new("RGB", (N * 3 * 4 + 16, N * 4 * 3 + 8), (200, 220, 235))
    for i, sp in enumerate(SPECIES):
        for j, name in enumerate((f"{sp}_bark", f"{sp}_log_top", f"{sp}_leaves")):
            im = Image.open(os.path.join(tex, name + ".png")).resize((N * 3, N * 3), Image.NEAREST)
            sheet.paste(im, (j * (N * 3 + 4), i * (N * 3 + 4)))
    out = os.path.join(ROOT, "build", "preview", "png")
    os.makedirs(out, exist_ok=True)
    sheet.save(os.path.join(out, "textures.png"))
    print("готово: текстуры, модели, blockstates, items, loot, теги, lang")


if __name__ == "__main__":
    main()
