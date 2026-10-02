"""Rendu vue du dessus d'un monde Minecraft 26.x (format Anvil) → PNG 1 px = 1 bloc.

Usage :
    python render.py <dossier region> <client.jar> <sortie.png> [--bounds x0 z0 x1 z1]

Sorties : <sortie>.png (couleurs brutes) et <sortie>.npz (hauteurs + ids de blocs),
consommés ensuite par stylize.py pour le rendu pixel-art de la carte du hub.

Aucune dépendance hors numpy + Pillow : le parseur NBT est maison.
"""

from __future__ import annotations

import io
import math
import re
import struct
import sys
import zipfile
import zlib
import gzip
from pathlib import Path

import numpy as np
from PIL import Image

# --------------------------------------------------------------------------- NBT


def _read_nbt(buf: memoryview, pos: int, tag: int):
    if tag == 1:
        return struct.unpack_from(">b", buf, pos)[0], pos + 1
    if tag == 2:
        return struct.unpack_from(">h", buf, pos)[0], pos + 2
    if tag == 3:
        return struct.unpack_from(">i", buf, pos)[0], pos + 4
    if tag == 4:
        return struct.unpack_from(">q", buf, pos)[0], pos + 8
    if tag == 5:
        return struct.unpack_from(">f", buf, pos)[0], pos + 4
    if tag == 6:
        return struct.unpack_from(">d", buf, pos)[0], pos + 8
    if tag == 7:
        n = struct.unpack_from(">i", buf, pos)[0]
        return None, pos + 4 + n  # byte arrays inutiles ici
    if tag == 8:
        n = struct.unpack_from(">H", buf, pos)[0]
        return bytes(buf[pos + 2 : pos + 2 + n]).decode("utf-8", "replace"), pos + 2 + n
    if tag == 9:
        sub = buf[pos]
        n = struct.unpack_from(">i", buf, pos + 1)[0]
        pos += 5
        out = []
        for _ in range(n):
            v, pos = _read_nbt(buf, pos, sub)
            out.append(v)
        return out, pos
    if tag == 10:
        out = {}
        while True:
            t = buf[pos]
            pos += 1
            if t == 0:
                return out, pos
            n = struct.unpack_from(">H", buf, pos)[0]
            name = bytes(buf[pos + 2 : pos + 2 + n]).decode("utf-8", "replace")
            pos += 2 + n
            out[name], pos = _read_nbt(buf, pos, t)
    if tag == 11:
        n = struct.unpack_from(">i", buf, pos)[0]
        return np.frombuffer(buf[pos + 4 : pos + 4 + 4 * n], ">i4"), pos + 4 + 4 * n
    if tag == 12:
        n = struct.unpack_from(">i", buf, pos)[0]
        return np.frombuffer(buf[pos + 4 : pos + 4 + 8 * n], ">i8").astype(np.int64), pos + 4 + 8 * n
    raise ValueError(f"tag NBT inconnu {tag} @ {pos}")


def parse_nbt(data: bytes) -> dict:
    buf = memoryview(data)
    assert buf[0] == 10, "racine NBT non compound"
    n = struct.unpack_from(">H", buf, 1)[0]
    root, _ = _read_nbt(buf, 3 + n, 10)
    return root


# --------------------------------------------------------------------------- Anvil


def iter_chunks(region_path: Path):
    raw = region_path.read_bytes()
    if len(raw) < 8192:
        return
    for i in range(1024):
        off = int.from_bytes(raw[i * 4 : i * 4 + 3], "big")
        if off == 0:
            continue
        p = off * 4096
        length = int.from_bytes(raw[p : p + 4], "big")
        comp = raw[p + 4]
        payload = raw[p + 5 : p + 4 + length]
        if comp == 2:
            data = zlib.decompress(payload)
        elif comp == 1:
            data = gzip.decompress(payload)
        elif comp == 3:
            data = payload
        else:
            # 4 = LZ4, >=128 = chunk externe .mcc : non géré, on saute
            continue
        yield parse_nbt(data)


def _unpack(longs: np.ndarray, bits: int, count: int) -> np.ndarray:
    per = 64 // bits
    mask = (1 << bits) - 1
    u = longs.view(np.uint64)
    shifts = (np.arange(per, dtype=np.uint64) * np.uint64(bits))
    vals = (u[:, None] >> shifts[None, :]) & np.uint64(mask)
    return vals.reshape(-1)[:count].astype(np.int32)


def _heightmap(longs: np.ndarray) -> np.ndarray:
    for bits in range(9, 13):
        if math.ceil(256 / (64 // bits)) == len(longs):
            return _unpack(longs, bits, 256).reshape(16, 16)  # [z][x]
    raise ValueError("heightmap de taille inattendue")


# --------------------------------------------------------------------------- Couleurs

TINT_GRASS = np.array([124, 172, 76])
TINT_FOLIAGE = np.array([82, 132, 46])
TINT_WATER = np.array([52, 98, 168])


def load_block_colors(client_jar: Path) -> dict[str, tuple[int, int, int]]:
    """Couleur moyenne de la texture du dessus de chaque bloc vanilla."""
    colors: dict[str, tuple[int, int, int]] = {}
    with zipfile.ZipFile(client_jar) as z:
        names = {n for n in z.namelist() if n.startswith("assets/minecraft/textures/block/") and n.endswith(".png")}
        for n in names:
            stem = n.rsplit("/", 1)[1][:-4]
            try:
                img = Image.open(io.BytesIO(z.read(n))).convert("RGBA")
            except Exception:
                continue
            a = np.asarray(img, dtype=np.float32)[:16, :16]
            m = a[..., 3] > 16
            if not m.any():
                continue
            colors[stem] = tuple(int(c) for c in a[..., :3][m].mean(axis=0))
    return colors


def block_color(name: str, tex: dict) -> tuple[int, int, int]:
    b = name.split(":", 1)[-1].removeprefix("waxed_")
    b = {"smooth_quartz": "quartz_block_bottom", "smooth_stone_slab": "smooth_stone"}.get(b, b)
    b = re.sub(r"_bars$", "", b)
    if b in ("water", "bubble_column", "kelp", "kelp_plant", "seagrass", "tall_seagrass"):
        return tuple(TINT_WATER)
    if b == "grass_block":
        return tuple(TINT_GRASS)
    if b.endswith("_leaves"):
        if b in ("cherry_leaves", "azalea_leaves", "flowering_azalea_leaves", "pale_oak_leaves"):
            return tex.get(b, (90, 120, 60))
        g = np.array(tex.get(b, (120, 120, 120)), dtype=np.float32) / 255.0
        return tuple(int(c) for c in (g * TINT_FOLIAGE * 1.35).clip(0, 255))
    if b in ("vine", "lily_pad", "fern", "large_fern", "short_grass", "tall_grass"):
        return tuple(TINT_FOLIAGE)
    for cand in (f"{b}_top", b, re.sub(r"_(stairs|slab|wall|fence|fence_gate|pressure_plate|button|door|trapdoor|sign|wall_sign|hanging_sign|carpet|pane)$", "", b)):
        if cand in tex:
            return tex[cand]
        base = cand.replace("_wood", "_log").replace("stripped_", "stripped_")
        if f"{base}_top" in tex:
            return tex[f"{base}_top"]
        if base in tex:
            return tex[base]
        if cand.endswith("s") and cand[:-1] in tex:  # bricks → brick, …
            return tex[cand[:-1]]
        if f"{cand}s" in tex:  # brick → bricks
            return tex[f"{cand}s"]
        if f"{cand}_planks" in tex:
            return tex[f"{cand}_planks"]
    return (255, 0, 255)  # magenta = bloc inconnu (custom / moddé)


# --------------------------------------------------------------------------- Rendu


def main() -> None:
    args = sys.argv[1:]
    region_dir, client_jar, out = Path(args[0]), Path(args[1]), Path(args[2])
    bounds = None
    if "--bounds" in args:
        i = args.index("--bounds")
        bounds = tuple(int(v) for v in args[i + 1 : i + 5])

    files = sorted(region_dir.glob("r.*.*.mca"))
    coords = [tuple(int(v) for v in f.stem.split(".")[1:3]) for f in files]
    rx0, rx1 = min(c[0] for c in coords), max(c[0] for c in coords)
    rz0, rz1 = min(c[1] for c in coords), max(c[1] for c in coords)
    ox, oz = rx0 * 512, rz0 * 512
    W, H = (rx1 - rx0 + 1) * 512, (rz1 - rz0 + 1) * 512

    height = np.full((H, W), np.iinfo(np.int16).min, dtype=np.int16)
    ids = np.zeros((H, W), dtype=np.uint16)
    water_depth = np.zeros((H, W), dtype=np.uint8)
    names: list[str] = ["minecraft:void"]
    index: dict[str, int] = {names[0]: 0}

    def nid(n: str) -> int:
        if n not in index:
            index[n] = len(names)
            names.append(n)
        return index[n]

    for f in files:
        nchunks = 0
        for c in iter_chunks(f):
            hm = c.get("Heightmaps", {}).get("MOTION_BLOCKING")
            secs = c.get("sections") or []
            if hm is None or not secs:
                continue
            ymin_sec = c.get("yPos", min(s.get("Y", 0) for s in secs))
            ymin = ymin_sec * 16
            hmap = _heightmap(hm) + ymin - 1  # y du bloc du dessus
            by_y = {}
            for s in secs:
                bs = s.get("block_states")
                if not bs or "palette" not in bs:
                    continue
                pal = [p["Name"] for p in bs["palette"]]
                if "data" in bs and len(pal) > 1:
                    bits = max(4, math.ceil(math.log2(len(pal))))
                    arr = _unpack(bs["data"], bits, 4096)
                else:
                    arr = np.zeros(4096, dtype=np.int32)
                by_y[s["Y"]] = (pal, arr)

            def block_at(x: int, y: int, z: int) -> str:
                s = by_y.get(y >> 4)
                if s is None:
                    return "minecraft:air"
                pal, arr = s
                return pal[arr[((y & 15) << 8) | (z << 4) | x]]

            cx, cz = c["xPos"] * 16 - ox, c["zPos"] * 16 - oz
            if not (0 <= cx < W and 0 <= cz < H):
                continue
            for z in range(16):
                for x in range(16):
                    y = int(hmap[z, x])
                    if y < ymin:
                        continue
                    n = block_at(x, y, z)
                    d = 0
                    if n.endswith(":water"):
                        yy = y
                        while d < 40 and block_at(x, yy - 1, z).endswith(":water"):
                            yy -= 1
                            d += 1
                    height[cz + z, cx + x] = y
                    ids[cz + z, cx + x] = nid(n)
                    water_depth[cz + z, cx + x] = d
            nchunks += 1
        print(f"{f.name}: {nchunks} chunks", flush=True)

    tex = load_block_colors(client_jar)
    pal = np.array([block_color(n, tex) if i else (0, 0, 0) for i, n in enumerate(names)], dtype=np.uint8)
    rgb = pal[ids]

    unknown = sorted({names[i] for i in np.unique(ids) if i and tuple(pal[i]) == (255, 0, 255)})
    if unknown:
        print("Blocs sans couleur (magenta) :", ", ".join(unknown))

    if bounds:
        x0, z0, x1, z1 = bounds
        sl = (slice(z0 - oz, z1 - oz), slice(x0 - ox, x1 - ox))
        rgb, height, ids, water_depth = rgb[sl], height[sl], ids[sl], water_depth[sl]
        ox, oz = x0, z0

    Image.fromarray(rgb).save(out)
    np.savez_compressed(
        out.with_suffix(".npz"), height=height, ids=ids, water=water_depth,
        names=np.array(names), origin=np.array([ox, oz]), palette=pal,
    )
    print(f"-> {out} ({rgb.shape[1]}x{rgb.shape[0]}), origine monde ({ox}, {oz})")


if __name__ == "__main__":
    main()
