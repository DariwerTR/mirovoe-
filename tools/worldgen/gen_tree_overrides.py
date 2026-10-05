#!/usr/bin/env python3
"""Заменяет ванильные деревья (дуб, берёза, ель и их варианты) на деревья Wildlands во всех биомах и саженцах.

Принцип: файлы data/minecraft/worldgen/configured_feature/<имя>.json переопределяются нашим признаком wildlands:wild_tree.
Все ванильные биомы и саженцы ссылаются на эти имена, поэтому заменяется всё сразу.
Заодно уменьшается плотность деревьев (они крупнее ванильных): счётчики placed_feature trees_* умножаются на DENSITY.

Запуск из корня репозитория:
  python3 tools/worldgen/gen_tree_overrides.py PATH/TO/vanilla/data/minecraft/worldgen
"""
import glob
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "minecraft", "worldgen")
DENSITY = 0.5

LEAVES = {"oak_leaves": "oak", "birch_leaves": "birch", "spruce_leaves": "spruce"}
LOGS = {"oak_log": "oak", "birch_log": "birch", "spruce_log": "spruce"}


def heights(name, species):
    if "mega" in name:
        return 28, 40
    if name.startswith("swamp"):
        return 8, 13
    if name.startswith("fancy_oak"):
        return 16, 26
    if name.startswith("super_birch"):
        return 16, 24
    if name.startswith("pine"):
        return 14, 24
    return {"oak": (11, 18), "birch": (10, 17), "spruce": (16, 28)}[species]


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def scale_count(n):
    return max(1, round(n * DENSITY)) if n >= 4 else n


def scale_placement(placement):
    changed = False
    for mod in placement:
        if mod.get("type") != "minecraft:count":
            continue
        c = mod["count"]
        if isinstance(c, int):
            nc = scale_count(c)
            changed |= nc != c
            mod["count"] = nc
        elif isinstance(c, dict) and c.get("type") == "minecraft:weighted_list":
            for e in c["distribution"]:
                if isinstance(e["data"], int):
                    nc = scale_count(e["data"])
                    changed |= nc != e["data"]
                    e["data"] = nc
        elif isinstance(c, dict) and c.get("type") == "minecraft:uniform":
            lo, hi = c.get("min_inclusive"), c.get("max_inclusive")
            if isinstance(lo, int) and isinstance(hi, int):
                c["min_inclusive"], c["max_inclusive"] = scale_count(lo), scale_count(hi)
                changed = True
    return changed


def main():
    van = sys.argv[1]
    done = []
    for f in sorted(glob.glob(os.path.join(van, "configured_feature", "*.json"))):
        d = json.load(open(f))
        if d["type"] != "minecraft:tree":
            continue
        c = d["config"]
        log = c["trunk_provider"].get("state", {}).get("Name", "").replace("minecraft:", "")
        leaf = c["foliage_provider"].get("state", {}).get("Name", "").replace("minecraft:", "")
        if log not in LOGS or leaf not in LEAVES or LOGS[log] != LEAVES[leaf]:
            continue
        sp = LOGS[log]
        name = os.path.basename(f)[:-5]
        lo, hi = heights(name, sp)
        write(os.path.join(OUT, "configured_feature", name + ".json"), {
            "type": "wildlands:wild_tree",
            "config": {"species": sp, "min_height": lo, "max_height": hi},
        })
        done.append(name)
    scaled = []
    skip = ("trees_jungle", "trees_cherry", "trees_mangrove")
    for f in sorted(glob.glob(os.path.join(van, "placed_feature", "trees_*.json"))):
        if os.path.basename(f)[:-5] in skip:
            continue
        d = json.load(open(f))
        if scale_placement(d.get("placement", [])):
            name = os.path.basename(f)
            write(os.path.join(OUT, "placed_feature", name), d)
            scaled.append(name)
    print("деревьев заменено:", len(done), "->", ", ".join(done))
    print("плотность уменьшена в:", len(scaled), "placed_feature")


if __name__ == "__main__":
    main()
