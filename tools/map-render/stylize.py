"""Stylise un rendu brut (render.py) en carte pixel-art façon « Carte du hub ».

Usage :
    python stylize.py <raw.png> <sortie.png> --center X Z --radius R [--size 1024] [--colors 40]

- relief ombré (lumière nord-ouest) + courbes de niveau discrètes ;
- forêts en canopées texturées avec ombre portée ;
- herbe tachetée, contours sombres sous les bâtiments ;
- bord de carte dithéré qui se fond dans un fond parchemin ;
- palette réduite pour le rendu pixel-art (affichage en nearest côté client).

Les noms de zones ne sont PAS incrustés : le mod les dessine depuis places.yml.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

PARCHMENT = np.array([236, 224, 190], dtype=np.float32)
PARCHMENT_DARK = np.array([214, 198, 158], dtype=np.float32)


def arg(args, name, default=None, n=1, cast=float):
    if name not in args:
        return default
    i = args.index(name)
    vals = [cast(v) for v in args[i + 1 : i + 1 + n]]
    return vals if n > 1 else vals[0]


def box_down(a: np.ndarray, f: int) -> np.ndarray:
    h, w = a.shape[:2]
    h2, w2 = h // f, w // f
    a = a[: h2 * f, : w2 * f]
    return a.reshape(h2, f, w2, f, *a.shape[2:]).mean(axis=(1, 3))


def mode_down(ids: np.ndarray, f: int) -> np.ndarray:
    """Bloc majoritaire par cellule f×f (garde les routes fines lisibles)."""
    h, w = ids.shape
    h2, w2 = h // f, w // f
    cells = ids[: h2 * f, : w2 * f].reshape(h2, f, w2, f).transpose(0, 2, 1, 3).reshape(h2, w2, f * f)
    out = np.empty((h2, w2), dtype=ids.dtype)
    for i in range(h2):
        row = cells[i]
        for j in range(w2):
            v, c = np.unique(row[j], return_counts=True)
            out[i, j] = v[c.argmax()]
    return out


def main() -> None:
    args = sys.argv[1:]
    raw_path, out_path = Path(args[0]), Path(args[1])
    cx, cz = arg(args, "--center", n=2)
    radius = arg(args, "--radius")
    size = int(arg(args, "--size", 1024))
    ncolors = int(arg(args, "--colors", 40))

    d = np.load(raw_path.with_suffix(".npz"))
    names = [str(n) for n in d["names"]]
    ids, height, water = d["ids"], d["height"].astype(np.float32), d["water"].astype(np.float32)
    ox, oz = (int(v) for v in d["origin"])
    pal = d["palette"].astype(np.float32)
    H, W = ids.shape

    f = max(1, round(W / size))
    rng = np.random.default_rng(7)

    def is_(pred):
        lut = np.array([pred(n.split(":")[-1]) for n in names])
        return lut[ids]

    leaves = is_(lambda b: b.endswith("_leaves") or b in ("vine", "moss_block", "azalea"))
    wat = is_(lambda b: b in ("water", "seagrass", "kelp", "kelp_plant", "tall_seagrass"))
    grass = is_(lambda b: b in ("grass_block", "short_grass", "tall_grass", "fern", "moss_carpet"))
    natural = is_(lambda b: b in ("grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol", "stone", "andesite",
                                  "diorite", "granite", "gravel", "sand", "packed_mud", "mud", "terracotta",
                                  "brown_mushroom_block", "mushroom_stem", "moss_block", "tuff", "calcite")) | leaves | wat
    road_like = is_(lambda b: "wool" in b and b.startswith(("light_gray", "white", "gray")) or b in ("dirt_path",))
    built = ~natural & ~grass & ~road_like

    # --- relief ombré, calculé en pleine résolution puis réduit
    hs = height.copy()
    hs[hs < -1000] = np.median(hs[hs > -1000])
    k = np.ones(5, dtype=np.float32) / 5  # flou boîte séparable (Pillow ne floute pas le mode F)
    hs = np.apply_along_axis(lambda r: np.convolve(r, k, mode="same"), 1, hs)
    hs = np.apply_along_axis(lambda c: np.convolve(c, k, mode="same"), 0, hs)
    gx = np.zeros_like(hs); gz = np.zeros_like(hs)
    gx[:, 1:-1] = (hs[:, 2:] - hs[:, :-2]) / 2
    gz[1:-1, :] = (hs[2:, :] - hs[:-2, :]) / 2
    shade = np.clip(-(gx + gz) * 0.18, -0.45, 0.45)  # lumière venant du nord-ouest

    rgb = pal[ids].copy()
    # couleurs « carte » : herbe chaude, forêts profondes, eau plus saturée
    rgb[grass] = [118, 168, 74]
    rgb[wat] = np.stack([60 - water[wat] * 0.6, 120 - water[wat], 196 - water[wat] * 0.8], axis=-1)
    roads = road_like
    rgb[roads] = [208, 190, 150]

    small = box_down(rgb, f)
    s_shade = box_down(shade, f)
    s_leaves = box_down(leaves.astype(np.float32), f) > 0.45
    s_built = box_down(built.astype(np.float32), f) > 0.35
    s_roads = box_down(roads.astype(np.float32), f) > 0.3
    s_grass = box_down(grass.astype(np.float32), f) > 0.6
    s_h = box_down(hs, f)
    h2, w2 = small.shape[:2]

    # herbe tachetée (2 tons, motif pixel)
    speck = rng.random((h2, w2))
    small[s_grass & (speck < 0.10)] *= 0.86
    small[s_grass & (speck > 0.94)] *= 1.10

    # forêts : canopées en blobs (bruit basse fréquence) + ombre portée sud-est
    blob = np.asarray(Image.fromarray((rng.random((h2 // 3 + 1, w2 // 3 + 1)) * 255).astype(np.uint8))
                      .resize((w2, h2), Image.BICUBIC), dtype=np.float32) / 255
    canopy = s_leaves
    small[canopy] = np.array([62, 112, 48]) * (0.82 + 0.36 * blob[canopy, None])
    shadow = np.zeros_like(canopy)
    shadow[2:, 2:] = canopy[:-2, :-2]
    shadow &= ~canopy
    small[shadow] *= 0.72

    # routes : bord sombre
    edge_r = np.zeros_like(s_roads)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge_r |= np.roll(np.roll(s_roads, dy, 0), dx, 1)
    edge_r &= ~s_roads & ~s_built
    small[edge_r] = small[edge_r] * 0.55 + np.array([120, 96, 60]) * 0.45

    # bâtiments : contour sombre + ombre portée
    edge_b = np.zeros_like(s_built)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge_b |= np.roll(np.roll(s_built, dy, 0), dx, 1)
    edge_b &= ~s_built
    small[edge_b] *= 0.45
    bshadow = np.zeros_like(s_built)
    bshadow[2:, 2:] = s_built[:-2, :-2]
    bshadow &= ~s_built & ~edge_b
    small[bshadow] *= 0.75

    # relief + courbes de niveau tous les 10 blocs
    small *= (1 + s_shade)[..., None]
    lvl = np.floor(s_h / 10)
    contour = (lvl != np.roll(lvl, 1, 0)) | (lvl != np.roll(lvl, 1, 1))
    small[contour & ~s_built & ~s_roads] *= 0.88

    # bord de carte : disque dithéré qui se fond dans le parchemin
    yy, xx = np.mgrid[0:h2, 0:w2]
    wx = ox + (xx + 0.5) * f
    wz = oz + (yy + 0.5) * f
    dist = np.hypot(wx - cx, wz - cz)
    edge_noise = np.asarray(Image.fromarray((rng.random((h2 // 4 + 1, w2 // 4 + 1)) * 255).astype(np.uint8))
                            .resize((w2, h2), Image.NEAREST), dtype=np.float32) / 255
    fade = (dist - (radius + 40)) / 140 + (edge_noise - 0.5) * 0.9
    outside = fade > 0
    band = (fade > -0.25) & ~outside
    small[band] = small[band] * 0.55 + PARCHMENT_DARK * 0.45
    paper = PARCHMENT * (0.97 + 0.05 * edge_noise[..., None])
    small[outside] = paper[outside]

    img = Image.fromarray(np.clip(small, 0, 255).astype(np.uint8))
    img = img.quantize(colors=ncolors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    img.save(out_path)
    meta = out_path.with_suffix(".json")
    meta.write_text(
        '{"originX": %d, "originZ": %d, "blocksPerPixel": %d, "width": %d, "height": %d}\n'
        % (ox, oz, f, img.width, img.height),
        encoding="utf-8",
    )
    print(f"-> {out_path} {img.size}, {f} blocs/pixel, origine ({ox}, {oz})")


if __name__ == "__main__":
    main()
