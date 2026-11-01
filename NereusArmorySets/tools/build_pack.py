#!/usr/bin/env python3
"""
Membuat resource pack NereusArmorySets dari aset mod Armory (RPG Series).

  python3 tools/build_pack.py --mod "<folder Armory++ by Naok1>" --config src/main/resources/config.yml \
          --out ../resourcepacks

Hasil:
  Nereus-ArmorySets-Java.zip         (Java 1.21.1: ikon item + model 3D untuk ItemDisplay)
  Nereus-ArmorySets-Bedrock.mcpack   (Bedrock: attachable armor dari geo mod, dipakai langsung)
  nereus_armorysets_geyser.json      (Geyser custom item mapping)

Model Bedrock = geo asli mod (dipecah per piece). Model Java = konversi geo ke elemen blok:
Java hanya mendukung rotasi satu sumbu (-45..45, kelipatan 22.5), jadi bagian yang miring
dibulatkan -> hasil Java mendekati, tidak identik.
"""
import argparse
import json
import math
import os
import shutil
import uuid
import zipfile

import yaml

SLOTS = ["head", "chest", "legs", "feet"]

# set -> (geo, texture). Set astral/scarlet/glacier memakai geo tirisfal, smouldering/rimeweave memakai geo tempest
# (dibaca dari CustomArmorRenderer mod).
GEO = {
    "astral": "tirisfal_robe", "scarlet": "tirisfal_robe", "glacier": "tirisfal_robe",
    "tempest": "tempest_robe", "smouldering": "tempest_robe", "rimeweave": "tempest_robe",
    "avatar": "avatar_robe", "absolution": "absolution_robe",
    "justicar": "justicar_armor", "destroyer": "destroyer_armor", "lightbringer": "lightbringer_armor",
    "onslaught": "onslaught_armor", "deathmantle": "deathmantle_armor", "strider": "strider_armor",
    "slayer": "slayer_armor", "riftstalker": "riftstalker_armor",
}
TEX = {
    "astral": "astral_robe", "scarlet": "scarlet_robe", "glacier": "glacier_robe", "avatar": "avatar_robe",
    "justicar": "justicar_armor", "destroyer": "destroyer_armor", "deathmantle": "deathmantle_armor",
    "strider": "strider_armor", "tempest": "tempest_robe", "smouldering": "smouldering_robe",
    "rimeweave": "rimeweave_robe", "absolution": "absolution_robe", "lightbringer": "lightbringer_armor",
    "onslaught": "onslaught_armor", "slayer": "slayer_armor", "riftstalker": "riftstalker_armor",
}
VANILLA = {"head": "leather_helmet", "chest": "leather_chestplate", "legs": "leather_leggings",
           "feet": "leather_boots"}

ROOTS = {"bipedHead": "head", "bipedBody": "body", "bipedLeftArm": "leftArm", "bipedRightArm": "rightArm",
         "bipedLeftLeg": "leftLeg", "bipedRightLeg": "rightLeg"}


# ------------------------------------------------------------------ geo helpers

def descendants(bones, root):
    out = {root}
    changed = True
    while changed:
        changed = False
        for b in bones:
            if b.get("parent") in out and b["name"] not in out:
                out.add(b["name"])
                changed = True
    return out


def slot_bones(bones, slot):
    if slot == "head":
        keep = descendants(bones, "bipedHead")
    elif slot == "chest":
        keep = descendants(bones, "bipedBody") | descendants(bones, "bipedLeftArm") | descendants(bones, "bipedRightArm")
    elif slot == "legs":
        keep = {"bipedLeftLeg", "bipedRightLeg"} | descendants(bones, "armorLeftLeg") | descendants(bones, "armorRightLeg")
    else:
        keep = {"bipedLeftLeg", "bipedRightLeg"} | descendants(bones, "armorLeftBoot") | descendants(bones, "armorRightBoot")
    return keep


HEAD_OFFSET_Y = 8.0   # helm Bedrock terlihat turun ~4 satuan model dari kepala; geser ke atas (ubah via --head-offset-y)


def shift_y(bone, dy):
    """Geser semua kubus (dan pivot) satu bone ke atas sebesar dy satuan model."""
    if "pivot" in bone:
        bone["pivot"] = [bone["pivot"][0], bone["pivot"][1] + dy, bone["pivot"][2]]
    cubes = []
    for c in bone.get("cubes", []):
        c = dict(c)
        c["origin"] = [c["origin"][0], c["origin"][1] + dy, c["origin"][2]]
        if "pivot" in c:
            c["pivot"] = [c["pivot"][0], c["pivot"][1] + dy, c["pivot"][2]]
        cubes.append(c)
    if cubes:
        bone["cubes"] = cubes


def bedrock_geometry(geo, set_id, slot):
    g = geo["minecraft:geometry"][0]
    keep = slot_bones(g["bones"], slot)
    out = []
    for b in g["bones"]:
        if b["name"] not in keep:
            continue
        nb = dict(b)
        nb["name"] = ROOTS.get(b["name"], b["name"])
        if "parent" in nb:
            nb["parent"] = ROOTS.get(nb["parent"], nb["parent"])
        # bone akar "head" tetap di pivot kepala; hanya isinya yang digeser
        if slot == "head" and b["name"] != "bipedHead" and HEAD_OFFSET_Y:
            shift_y(nb, HEAD_OFFSET_Y)
        out.append(nb)
    desc = dict(g["description"])
    desc["identifier"] = f"geometry.nereusset.{set_id}_{slot}"
    desc.setdefault("visible_bounds_width", 4)
    desc.setdefault("visible_bounds_height", 4)
    desc.setdefault("visible_bounds_offset", [0, 1, 0])
    return {"format_version": "1.12.0", "minecraft:geometry": [{"description": desc, "bones": out}]}


# ------------------------------------------------------------------ geo -> Java element

def rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def rot_matrix(deg):
    """Rotasi geo -> ruang Java: (-x, -y, +z), urutan Z*Y*X (sama dengan GeckoLib)."""
    a, b, c = (math.radians(-deg[0]), math.radians(-deg[1]), math.radians(deg[2]))
    return mm(rz(c), mm(ry(b), rx(a)))


IDENT = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
STATS = {"clamped": 0, "multi_axis": 0, "rotated": 0}


def single_axis(m):
    """Ambil satu sumbu dominan dari matriks rotasi (R = Rz*Ry*Rx); bulatkan ke 22.5 dan batasi +-45."""
    angles = {
        "x": math.degrees(math.atan2(m[2][1], m[2][2])),
        "y": math.degrees(math.asin(max(-1.0, min(1.0, -m[2][0])))),
        "z": math.degrees(math.atan2(m[1][0], m[0][0])),
    }
    nz = [k for k, v in angles.items() if abs(v) > 1.0]
    if not nz:
        return None, 0.0
    if len(nz) > 1:
        STATS["multi_axis"] += 1
    axis = max(nz, key=lambda k: abs(angles[k]))
    snapped = round(angles[axis] / 22.5) * 22.5
    if abs(snapped) > 45:
        STATS["clamped"] += 1
        snapped = 45.0 if snapped > 0 else -45.0
    if snapped == 0:
        return None, 0.0
    return axis, snapped


def java_elements(geo, slot, tw, th):
    g = geo["minecraft:geometry"][0]
    bones = {b["name"]: b for b in g["bones"]}
    keep = slot_bones(g["bones"], slot)
    elements = []
    for b in g["bones"]:
        if b["name"] not in keep:
            continue
        # rantai bone dari bone ini ke root
        chain = []
        cur = b
        while cur is not None:
            chain.append(cur)
            cur = bones.get(cur.get("parent"))
        bone_pivot = b.get("pivot", [0, 0, 0])
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            inf = c.get("inflate", b.get("inflate", 0)) or 0
            u, v = c["uv"]
            mirror = bool(c.get("mirror", b.get("mirror", False)))

            # pusat kubus & kotak (ruang geo, x belum dibalik)
            center = [o[0] + s[0] / 2, o[1] + s[1] / 2, o[2] + s[2] / 2]
            half = [s[0] / 2 + inf, s[1] / 2 + inf, s[2] / 2 + inf]

            # ke ruang Java (x dibalik)
            cj = [-center[0], center[1], center[2]]
            total = IDENT
            if c.get("rotation") and any(c["rotation"]):
                pv = c.get("pivot", bone_pivot)
                pj = [-pv[0], pv[1], pv[2]]
                r = rot_matrix(c["rotation"])
                rel = [cj[i] - pj[i] for i in range(3)]
                cj = [pj[i] + mv(r, rel)[i] for i in range(3)]
                total = mm(r, total)
            for bone in chain:
                if bone.get("rotation") and any(bone["rotation"]):
                    pv = bone.get("pivot", [0, 0, 0])
                    pj = [-pv[0], pv[1], pv[2]]
                    r = rot_matrix(bone["rotation"])
                    rel = [cj[i] - pj[i] for i in range(3)]
                    cj = [pj[i] + mv(r, rel)[i] for i in range(3)]
                    total = mm(r, total)
            axis, angle = single_axis(total)
            if axis:
                STATS["rotated"] += 1

            hx, hy, hz = half
            # cj sudah di ruang Java (x dibalik); 1 unit geo = 0.5 unit model Java, pinggang (y=16) di tengah
            ctr = [8 + 0.5 * cj[0], 8 + 0.5 * (cj[1] - 16), 8 + 0.5 * cj[2]]
            frm = [ctr[0] - 0.5 * hx, ctr[1] - 0.5 * hy, ctr[2] - 0.5 * hz]
            to = [ctr[0] + 0.5 * hx, ctr[1] + 0.5 * hy, ctr[2] + 0.5 * hz]

            sx, sy, sz = s
            def px(a, b2, c2, d2):
                if mirror:
                    a, c2 = c2, a
                return [round(a * 16 / tw, 4), round(b2 * 16 / th, 4), round(c2 * 16 / tw, 4), round(d2 * 16 / th, 4)]

            north = px(u + sz, v + sz, u + sz + sx, v + sz + sy)
            south = px(u + sz + sx + sz, v + sz, u + sz + sx + sz + sx, v + sz + sy)
            geo_east = px(u, v + sz, u + sz, v + sz + sy)          # sisi +x geo
            geo_west = px(u + sz + sx, v + sz, u + sz + sx + sz, v + sz + sy)
            up = px(u + sz, v, u + sz + sx, v + sz)
            down = px(u + sz + sx, v + sz, u + sz + sx + sx, v)
            # x dibalik: sisi +x geo menjadi sisi barat Java. Mirror menukar kedua sisi.
            west, east = (geo_east, geo_west) if not mirror else (geo_west, geo_east)
            el = {
                "from": [round(x, 4) for x in frm],
                "to": [round(x, 4) for x in to],
                "faces": {
                    "north": {"uv": north, "texture": "#0"},
                    "east": {"uv": east, "texture": "#0"},
                    "south": {"uv": south, "texture": "#0"},
                    "west": {"uv": west, "texture": "#0"},
                    "up": {"uv": up, "texture": "#0"},
                    "down": {"uv": down, "texture": "#0"},
                },
            }
            if axis:
                el["rotation"] = {"angle": angle, "axis": axis, "origin": [round(x, 4) for x in ctr]}
            elements.append(el)
    return elements


def validate(elements, name):
    for e in elements:
        for k in ("from", "to"):
            for x in e[k]:
                if not -16 <= x <= 32:
                    raise SystemExit(f"{name}: koordinat {x} di luar -16..32")
        for f in e["faces"].values():
            for x in f["uv"]:
                if not -16 <= x <= 32:
                    raise SystemExit(f"{name}: uv {x} di luar -16..32")
        r = e.get("rotation")
        if r and (abs(r["angle"]) > 45 or (r["angle"] / 22.5) % 1 != 0):
            raise SystemExit(f"{name}: rotasi {r['angle']} tidak valid")


# ------------------------------------------------------------------ main

def main():
    global HEAD_OFFSET_Y
    ap = argparse.ArgumentParser()
    ap.add_argument("--mod", required=True)
    ap.add_argument("--config", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--head-offset-y", type=float, default=HEAD_OFFSET_Y)
    ap.add_argument("--bedrock-version", default="1,0,1", help="versi manifest Bedrock; naikkan tiap pack berubah")
    args = ap.parse_args()
    HEAD_OFFSET_Y = args.head_offset_y
    bver = [int(x) for x in args.bedrock_version.split(",")]

    cfg = yaml.safe_load(open(args.config))
    base = cfg.get("custom-model-data-base", 9000)
    sets = list(cfg["sets"].keys())
    assets = os.path.join(args.mod, "assets", "armory_rpgs")
    os.makedirs(args.out, exist_ok=True)

    java_files = {}     # path -> bytes
    bed_files = {}
    geyser_items = {}
    java_icon_overrides = {s: [] for s in SLOTS}
    carrier_overrides = []
    item_tex_data = {}

    def add_json(store, path, data):
        store[path] = json.dumps(data, indent=1).encode()

    def add_file(store, path, src):
        with open(src, "rb") as f:
            store[path] = f.read()

    for idx, sid in enumerate(sets):
        geo = json.load(open(os.path.join(assets, "geo", GEO[sid] + ".geo.json")))
        tex = TEX[sid]
        tex_png = os.path.join(assets, "textures", "armor", tex + ".png")
        tw = geo["minecraft:geometry"][0]["description"]["texture_width"]
        th = geo["minecraft:geometry"][0]["description"]["texture_height"]
        add_file(java_files, f"assets/nereus_armorysets/textures/armor/{tex}.png", tex_png)
        add_file(bed_files, f"textures/nereus_sets/{tex}.png", tex_png)

        display_names = cfg["sets"][sid]["pieces"]
        for si, slot in enumerate(SLOTS):
            icon_cmd = base + idx * 4 + si
            disp_cmd = icon_cmd + 100
            key = f"{sid}_{slot}"

            # ikon 2D dari mod
            icon_src = os.path.join(assets, "textures", "item", f"{tex}_{slot}.png")
            add_file(java_files, f"assets/nereus_armorysets/textures/item/{key}.png", icon_src)
            add_file(bed_files, f"textures/items/nereus_set_{key}.png", icon_src)
            add_json(java_files, f"assets/nereus_armorysets/models/icon/{key}.json", {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"nereus_armorysets:item/{key}"},
            })
            java_icon_overrides[slot].append({"predicate": {"custom_model_data": icon_cmd},
                                              "model": f"nereus_armorysets:icon/{key}"})

            # model 3D Java
            els = java_elements(geo, slot, tw, th)
            validate(els, key)
            add_json(java_files, f"assets/nereus_armorysets/models/piece/{key}.json", {
                "textures": {"0": f"nereus_armorysets:armor/{tex}", "particle": f"nereus_armorysets:armor/{tex}"},
                "elements": els,
            })
            carrier_overrides.append({"predicate": {"custom_model_data": disp_cmd},
                                      "model": f"nereus_armorysets:piece/{key}"})

            # Bedrock
            add_json(bed_files, f"models/entity/{key}.geo.json", bedrock_geometry(geo, sid, slot))
            add_json(bed_files, f"attachables/{key}.json", {
                "format_version": "1.10.0",
                "minecraft:attachable": {"description": {
                    "identifier": f"nereusset:{key}",
                    "materials": {"default": "armor", "enchanted": "armor_enchanted"},
                    "textures": {"default": f"textures/nereus_sets/{tex}",
                                 "enchanted": "textures/misc/enchanted_item_glint"},
                    "geometry": {"default": f"geometry.nereusset.{key}"},
                    "render_controllers": ["controller.render.armor"]}}})
            item_tex_data[f"nereus_set_{key}"] = {"textures": f"textures/items/nereus_set_{key}"}
            geyser_items.setdefault(f"minecraft:{VANILLA[slot]}", []).append({
                "type": "legacy",
                "custom_model_data": float(icon_cmd),
                "bedrock_identifier": f"nereusset:{key}",
                "display_name": display_names[slot],
                "bedrock_options": {"icon": f"nereus_set_{key}", "allow_offhand": True},
            })

    # base item Java: ikon armor (leather) + pembawa model 3D
    for slot in SLOTS:
        van = VANILLA[slot]
        add_json(java_files, f"assets/minecraft/models/item/{van}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"minecraft:item/{van}", "layer1": f"minecraft:item/{van}_overlay"},
            "overrides": java_icon_overrides[slot],
        })
    add_json(java_files, "assets/minecraft/models/item/armadillo_scute.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "minecraft:item/armadillo_scute"},
        "overrides": carrier_overrides,
    })
    add_json(java_files, "pack.mcmeta", {"pack": {"pack_format": 34,
                                                  "description": "Nereus Armory Sets - model 3D armor"}})
    icon = os.path.join(args.mod, "icon.png")
    add_file(java_files, "pack.png", icon)

    # Bedrock manifest + texture list
    ns = uuid.UUID("6f1c3b0e-2d7a-4a55-9c1e-0a7b1f2c3d4e")
    add_json(bed_files, "manifest.json", {
        "format_version": 2,
        "header": {"name": "Nereus Armory Sets", "description": "Armor 3D Armory (RPG Series)",
                   "uuid": str(uuid.uuid5(ns, "header")), "version": bver, "min_engine_version": [1, 21, 0]},
        "modules": [{"type": "resources", "uuid": str(uuid.uuid5(ns, "module")), "version": bver}],
    })
    add_file(bed_files, "pack_icon.png", icon)
    add_json(bed_files, "textures/item_texture.json", {
        "resource_pack_name": "nereus_armorysets", "texture_name": "atlas.items", "texture_data": item_tex_data})

    def write_zip(path, files):
        if os.path.exists(path):
            os.remove(path)
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
            for name in sorted(files):
                z.writestr(name, files[name])

    write_zip(os.path.join(args.out, "Nereus-ArmorySets-Java.zip"), java_files)
    write_zip(os.path.join(args.out, "Nereus-ArmorySets-Bedrock.mcpack"), bed_files)
    with open(os.path.join(args.out, "nereus_armorysets_geyser.json"), "w") as f:
        json.dump({"format_version": 2, "items": geyser_items}, f, indent=1)

    print(f"{len(sets)} set x 4 piece. Kubus berotasi di model Java: {STATS['rotated']}, "
          f"multi-sumbu (disederhanakan): {STATS['multi_axis']}, sudut >45 (dipotong): {STATS['clamped']}")


if __name__ == "__main__":
    main()
