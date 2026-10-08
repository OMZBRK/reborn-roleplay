"""bbforge.render — rasteriseur logiciel (z-buffer) pour previsualiser un modele
Blockbench anime, sans Blockbench ni GPU.

L'eclairage imite celui de Minecraft : la luminosite d'une face depend de son
orientation dans le monde (dessus 1.0, nord/sud 0.8, est/ouest 0.6, dessous 0.5),
pas d'un modele de reflexion. Pas de speculaire, pas de rim light : ce sont eux
qui donnaient l'aspect plastique. Juste une cle directionnelle tres faible pour
garder du volume, et l'echantillonnage de texture reste en nearest.
"""
from __future__ import annotations

import math
import numpy as np
from PIL import Image, ImageDraw

from forge.bb import FACES, face_corners

# ------------------------------------------------------------------ algebre


def ident():
    return np.eye(4, dtype=np.float64)


def mtrans(v):
    m = ident()
    m[:3, 3] = v
    return m


def mscale(s):
    m = ident()
    m[0, 0], m[1, 1], m[2, 2] = s
    return m


def mrot(deg):
    rx, ry, rz = [math.radians(a) for a in deg]
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]], np.float64)
    my = np.array([[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]], np.float64)
    mz = np.array([[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], np.float64)
    return mx @ my @ mz


def normalize(v):
    n = float(np.linalg.norm(v))
    return v / n if n > 1e-9 else v


def look_at(eye, target, up=(0, 1, 0)):
    eye = np.array(eye, np.float64)
    f = normalize(np.array(target, np.float64) - eye)
    s = normalize(np.cross(f, np.array(up, np.float64)))
    u = np.cross(s, f)
    m = ident()
    m[0, :3], m[1, :3], m[2, :3] = s, u, -f
    m[:3, 3] = [-np.dot(s, eye), -np.dot(u, eye), np.dot(f, eye)]
    return m


def perspective(fov_deg, aspect, near=1.0, far=4000.0):
    f = 1.0 / math.tan(math.radians(fov_deg) / 2)
    m = np.zeros((4, 4), np.float64)
    m[0, 0] = f / aspect
    m[1, 1] = f
    m[2, 2] = (far + near) / (near - far)
    m[2, 3] = 2 * far * near / (near - far)
    m[3, 2] = -1.0
    return m


# ------------------------------------------------------------------ geometrie

class Face:
    __slots__ = ("pts", "uv", "rect", "n", "center", "tid")

    def __init__(self, pts, uv, rect, n, center, tid):
        self.pts = pts
        self.uv = uv
        self.rect = rect
        self.n = n
        self.center = center
        self.tid = tid


def collect(model, pose, extra_cubes=(), root_m=None):
    """Aplati l'arbre d'os pose en quads monde + renvoie les pivots monde.

    `root_m` est une matrice appliquee au-dessus de la racine : c'est ce qui
    permet de rendre un modele en unites Model Engine (1/16 bloc) dans une
    scene dont la camera et le decor raisonnent a une autre echelle."""
    faces = []
    pivots = {}

    def rec(bone, pm):
        p = pose.get(bone.name) or {}
        pos = p.get("position", (0.0, 0.0, 0.0))
        rot = p.get("rotation", (0.0, 0.0, 0.0))
        sc = p.get("scale", (1.0, 1.0, 1.0))
        rr = [bone.rot[i] + rot[i] for i in range(3)]
        piv = np.array(bone.pivot, np.float64)
        m = (pm @ mtrans(piv) @ mtrans(pos) @ mrot(rr) @ mscale(sc) @ mtrans(-piv))
        pivots[bone.name] = (m @ np.append(piv, 1.0))[:3]
        if min(abs(v) for v in sc) > 0.02:
            for c in bone.cubes:
                cm = m
                if any(abs(v) > 1e-9 for v in c.rot):
                    o = np.array(c.origin, np.float64)
                    cm = m @ mtrans(o) @ mrot(c.rot) @ mtrans(-o)
                emit_cube(faces, c, cm, 0)
        for sub in bone.children:
            rec(sub, m)

    rec(model.root, ident() if root_m is None else root_m)
    for item in extra_cubes:
        c, cm = item[0], item[1]
        emit_cube(faces, c, cm, item[2] if len(item) > 2 else 1)
    return faces, pivots


def emit_cube(faces, cube, m, tid=0):
    # centre du cube dans le repere monde : la normale de chaque face est
    # orientee vers l'exterieur a partir de lui (l'ordre des coins ne suffit
    # pas a garantir le sens, et une normale rentrante fait cacher les faces
    # visibles et eclairer le dessous a la place du dessus)
    c0 = [(a + b) / 2.0 for a, b in zip(cube.f, cube.t)]
    cc = (m @ np.array([c0[0], c0[1], c0[2], 1.0]))[:3]
    for fname in FACES:
        pts, uv = face_corners(cube.f, cube.t, fname)
        w = np.array([(m @ np.array([p[0], p[1], p[2], 1.0]))[:3] for p in pts])
        n = normalize(np.cross(w[1] - w[0], w[2] - w[0]))
        center = w.mean(0)
        if float(np.dot(n, center - cc)) < 0.0:
            n = -n
        faces.append(Face(w, uv, cube.uv[fname], n, center, tid))


# ------------------------------------------------------------------ rasteriseur

class Target:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.color = np.zeros((h, w, 3), np.float32)
        self.emis = np.zeros((h, w), np.float32)
        self.depth = np.full((h, w), 1e18, np.float32)


LIGHT = normalize(np.array([-0.40, 0.72, 0.56]))
# lampe de position (pas a l'infini) : la direction vers elle change d'un sommet
# a l'autre, ce qui donne un degrade lisse SUR la face au lieu d'un aplat. C'est
# la difference de finition la plus visible avec la reference.
PLIGHT = np.array([-170.0, 250.0, 210.0])
GLOW = np.array([0.55, 0.85, 1.00], np.float32)
TINT = np.array([0.97, 0.99, 1.06], np.float32)   # ambiance froide, tres legere

# luminosite par orientation, comme le moteur de Minecraft
SH_UP, SH_DOWN, SH_NS, SH_EW = 0.96, 0.48, 0.78, 0.60


def mc_shade(n):
    ax, ay, az = abs(float(n[0])), abs(float(n[1])), abs(float(n[2]))
    s = ax + ay + az
    if s < 1e-9:
        return 0.8
    up = max(float(n[1]), 0.0)
    dn = max(-float(n[1]), 0.0)
    return (SH_UP * up + SH_DOWN * dn + SH_NS * az + SH_EW * ax) / s


def draw(target, faces, view, proj, eye, textures, energy):
    W, H = target.w, target.h
    vp = proj @ view
    for f in faces:
        if float(np.dot(f.n, eye - f.center)) <= 0.0:
            continue
        clip = (vp @ np.concatenate([f.pts, np.ones((4, 1))], 1).T).T
        if np.any(clip[:, 3] < 0.6):
            continue
        invw = 1.0 / clip[:, 3]
        ndc = clip[:, :3] * invw[:, None]
        sx = (ndc[:, 0] * 0.5 + 0.5) * W
        sy = (1.0 - (ndc[:, 1] * 0.5 + 0.5)) * H
        if sx.max() < 0 or sx.min() > W or sy.max() < 0 or sy.min() > H:
            continue
        ent = textures[f.tid]
        tex, etex = ent[0], ent[1]
        rimk = ent[2] if len(ent) > 2 else 0.0
        flat = ent[3] if len(ent) > 3 else 0.0
        # un corps d'energie n'est pas eclaire par un soleil : on aplatit son
        # ombrage et on lui rend un contour lumineux, comme dans l'anime
        v = mc_shade(f.n) * (1.0 - flat) + 0.95 * flat
        lam = max(0.0, float(np.dot(f.n, LIGHT)))
        shade = (TINT * (v * (0.88 + 0.24 * lam))).astype(np.float32)
        rim = 0.0
        if rimk > 0.0:
            vd = normalize(eye - f.center)
            rim = (1.0 - max(0.0, float(np.dot(f.n, vd)))) ** 2.2 * rimk
        th, tw = etex.shape
        x1, y1, x2, y2 = f.rect
        tu = np.array([x1 + a * (x2 - x1) for a, _ in f.uv])
        tv = np.array([y1 + b * (y2 - y1) for _, b in f.uv])
        gv = np.empty(4, np.float32)
        for k in range(4):
            gv[k] = 0.80 + 0.38 * max(0.0, float(np.dot(f.n, normalize(PLIGHT - f.pts[k]))))
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            _tri(target, sx[[a, b, c]], sy[[a, b, c]], invw[[a, b, c]],
                 tu[[a, b, c]], tv[[a, b, c]], shade, energy, tex, etex, tw, th, rim,
                 gv[[a, b, c]])


def _tri(t, sx, sy, iw, tu, tv, shade, emit, tex, etex, tw, th, rim=0.0, g=None):
    W, H = t.w, t.h
    x0 = max(int(math.floor(sx.min())), 0)
    x1 = min(int(math.ceil(sx.max())) + 1, W)
    y0 = max(int(math.floor(sy.min())), 0)
    y1 = min(int(math.ceil(sy.max())) + 1, H)
    if x1 <= x0 or y1 <= y0:
        return
    area = ((sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]))
    if abs(area) < 1e-7:
        return
    xs = np.arange(x0, x1, dtype=np.float32) + 0.5
    ys = np.arange(y0, y1, dtype=np.float32) + 0.5
    X = xs[None, :]
    Y = ys[:, None]
    w0 = ((sx[1] - X) * (sy[2] - Y) - (sx[2] - X) * (sy[1] - Y)) / area
    w1 = ((sx[2] - X) * (sy[0] - Y) - (sx[0] - X) * (sy[2] - Y)) / area
    w2 = 1.0 - w0 - w1
    mask = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
    if not mask.any():
        return
    iwp = w0 * iw[0] + w1 * iw[1] + w2 * iw[2]
    np.maximum(iwp, 1e-9, out=iwp)
    depth = (1.0 / iwp).astype(np.float32)
    sub = t.depth[y0:y1, x0:x1]
    mask &= depth < sub
    if not mask.any():
        return
    u = (w0 * tu[0] * iw[0] + w1 * tu[1] * iw[1] + w2 * tu[2] * iw[2]) / iwp
    v = (w0 * tv[0] * iw[0] + w1 * tv[1] * iw[1] + w2 * tv[2] * iw[2]) / iwp
    ui = np.clip(np.nan_to_num(u).astype(np.int32), 0, tw - 1)
    vi = np.clip(np.nan_to_num(v).astype(np.int32), 0, th - 1)
    texel = tex[vi, ui]
    e = etex[vi, ui] * emit + rim * 0.55
    rgb = texel * shade[None, None, :]
    if g is not None:                      # degrade de Gouraud sur la face
        rgb = rgb * (w0 * g[0] + w1 * g[1] + w2 * g[2])[:, :, None]
    # l'emission prend la couleur du texel : un oeil d'or brille or, une crete
    # cyan brille cyan. Une lueur globale cyan delavait tout accent chaud.
    rgb = rgb + texel * e[:, :, None] * 0.75 + GLOW[None, None, :] * e[:, :, None] * 0.10
    sub[mask] = depth[mask]
    t.color[y0:y1, x0:x1][mask] = rgb[mask]
    t.emis[y0:y1, x0:x1][mask] = e[mask]


# ------------------------------------------------------------------ 2D helpers

class FX:
    """Couche 2D float, dessinee avec PIL puis versee dans color/emis."""

    def __init__(self, w, h):
        self.im = Image.new("F", (w, h), 0.0)
        self.d = ImageDraw.Draw(self.im)

    def line(self, pts, val, width=1):
        if len(pts) >= 2:
            self.d.line(pts, fill=float(val), width=int(width), joint="curve")

    def dot(self, x, y, r, val):
        self.d.ellipse([x - r, y - r, x + r, y + r], fill=float(val))

    def arr(self):
        return np.asarray(self.im, np.float32)


def project(vp, W, H, p):
    q = vp @ np.array([p[0], p[1], p[2], 1.0])
    if q[3] < 0.5:
        return None
    return ((q[0] / q[3] * 0.5 + 0.5) * W, (1.0 - (q[1] / q[3] * 0.5 + 0.5)) * H)


def bolt_path(p0, p1, rng, jag=0.16, depth=5):
    """Subdivision aleatoire : chemin d'eclair entre deux points ecran."""
    pts = [np.array(p0, np.float64), np.array(p1, np.float64)]
    for _ in range(depth):
        out = [pts[0]]
        for i in range(len(pts) - 1):
            a, b = pts[i], pts[i + 1]
            mid = (a + b) / 2
            d = b - a
            nrm = np.array([-d[1], d[0]])
            ln = float(np.linalg.norm(nrm))
            if ln > 1e-6:
                mid = mid + nrm / ln * rng.normal(0, jag) * float(np.linalg.norm(d))
            out.append(mid)
            out.append(b)
        pts = out
        jag *= 0.62
    return [(float(p[0]), float(p[1])) for p in pts]


def _box1(a, r, axis):
    n = a.shape[axis]
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r, r)
    p = np.pad(a, pad, mode="edge")
    c = np.cumsum(p, axis=axis)
    zs = list(c.shape)
    zs[axis] = 1
    c = np.concatenate([np.zeros(zs, c.dtype), c], axis=axis)
    hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
    lo = np.take(c, np.arange(0, n), axis=axis)
    return ((hi - lo) / (2 * r + 1)).astype(np.float32)


def blur(a, sigma):
    """Gaussienne approchee par trois box blurs separables (PIL ne filtre pas le mode F)."""
    r = max(1, int(round(sigma * 0.95)))
    out = a.astype(np.float32)
    for _ in range(3):
        out = _box1(_box1(out, r, 0), r, 1)
    return out
