# -*- coding: utf-8 -*-
"""
gen_concentration_textures.py — textures de la concentration du chakra (parchemin/ConcentrationHud.java),
piste « ensō A » : un cercle de pinceau tracé AU PIXEL RÉEL de l'écran (pas à la grille GUI : la DA a
rejeté les cercles pixelisés), traversé d'un trait de pinceau horizontal qui porte la zone d'or et la
perle de chakra. L'ensō se referme en or à mesure que la concentration tient.

Tout ce qui est fixe est ici, une fois par échelle k (pixels écran par unité menu, 1 à 4) :
- enso_k.png : 40 images de progression (0 → 1) en grille 8×5, cellule F×F, F = 36·k ; centre de
  l'ensō au centre de la cellule. Encre sumi à liseré clair (lisible sur ciel clair comme sur sol
  sombre) ; la part parcourue est en or.
- trait_k.png : 144·k × 10·k, trait d'encre horizontal (attaque, ventre, queue sèche), axe à 5·k.
- perle_k.png : 2 cellules 11·k × 11·k (chakra dans l'or : bleu ; hors de l'or : laque rouge).
- goutte_k.png : 3 cellules 3·k × 4·k (goutte d'or, goutte de laque, goutte éteinte).
Et à la résolution des unités menu (1 texel = 1 unité) :
- sceaux.png 80×16 : sceaux de rang 14×14 (+ombre) D C B A S en (16·i, 0).

Usage (depuis minecraft/mod-hud) :  python tools/gen_concentration_textures.py
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("concentration", budget=400_000)

R_U = 13.0            # rayon de l'ensō (unités menu)
F_U = 36              # cellule
N = 40                # images de progression
TRAIT_U = 144
SS = 4                # sur-échantillonnage (bords lisses au pixel réel)

ENCRE = np.array([62, 46, 46], float)   # encre sumi éclaircie : lisible sur sol sombre comme sur ciel
LISERE = np.array([236, 222, 196], float)
OR_SOMBRE = np.array([176, 132, 66], float)
OR_ = np.array([217, 169, 94], float)
OR_VIF = np.array([242, 213, 154], float)

START, SWEEP = 18.0, 316.0


def _h(a, b):
    """Bruit déterministe (fibres du pinceau)."""
    x = np.sin(a * 12.9898 + b * 78.233) * 43758.5453
    return x - np.floor(x)


def enso_champs(k):
    """Pour chaque sous-pixel : u (avancée le long du trait, NaN hors trait), t (position dans
    l'épaisseur, −1..1), puis réduction en couverture par pixel."""
    F = F_U * k
    n = F * SS
    c = n / 2.0
    R = R_U * k * SS
    yy, xx = np.mgrid[0:n, 0:n].astype(float)
    dx, dy = xx + 0.5 - c, yy + 0.5 - c
    ang = np.degrees(np.arctan2(dx, -dy)) % 360.0
    rel = (ang - START) % 360.0
    u = rel / SWEEP
    dedans = rel <= SWEEP
    base = max(2.2 * SS, R * 0.17)
    press = np.power(np.sin(np.pi * np.clip(u * 1.06 + 0.04, 0, 1)), 0.6) * (1 - 0.42 * u)
    ep = base * (0.38 + 0.9 * press) * (1 + 0.07 * np.sin(u * 23))
    rr = R - u * R * 0.06
    d = np.sqrt(dx * dx + dy * dy) - rr
    t = d / np.maximum(ep / 2, 1e-6)
    on = dedans & (np.abs(t) <= 1)
    # queue sèche : stries le long du trait
    sec = u > 0.70
    strie = np.floor((t + 1) * 3.2 + 0.3 * np.sin(u * 40))
    trou = _h(strie, np.floor(u * 55)) < (u - 0.70) * 2.6
    on &= ~(sec & trou)
    return u, t, on, dedans, ep


def enso_frames(k):
    F = F_U * k
    u, t, on, dedans, ep = enso_champs(k)
    # couvertures par pixel
    def reduire(m):
        return m.reshape(F, SS, F, SS).mean(axis=(1, 3))
    cov = reduire(on.astype(float))
    # liseré clair : anneau d'un pixel autour du trait (dilatation de la couverture)
    halo = np.zeros_like(cov)
    for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        halo = np.maximum(halo, np.roll(np.roll(cov, a, 0), b, 1))
    halo = np.clip(halo - cov, 0, 1) * 0.55
    u_px = np.where(on, u, np.nan).reshape(F, SS, F, SS)
    with np.errstate(all="ignore"):
        u_moy = np.nanmean(u_px, axis=(1, 3))
    t_px = np.where(on, np.abs(t), np.nan).reshape(F, SS, F, SS)
    with np.errstate(all="ignore"):
        t_moy = np.nanmean(t_px, axis=(1, 3))
    t_moy = np.nan_to_num(t_moy, nan=1.0)
    u_moy = np.nan_to_num(u_moy, nan=2.0)
    cols = 8
    atlas = np.zeros((F * 5, F * cols, 4), np.uint8)
    for i in range(N):
        p = i / (N - 1)
        img = np.zeros((F, F, 4), float)
        # liseré clair sous tout
        img[..., :3] = LISERE
        img[..., 3] = halo * 255
        dore = u_moy <= p + 1e-6
        # encre : léger dégradé dans l'épaisseur (bord plus dense)
        enc = ENCRE * (0.85 + 0.25 * (1 - t_moy))[..., None]
        coeur = np.clip(1 - t_moy, 0, 1)[..., None]
        orc = OR_SOMBRE + (OR_VIF - OR_SOMBRE) * np.clip(coeur * 1.3, 0, 1)
        col = np.where(dore[..., None], orc, enc)
        a_trait = cov * np.where(dore, 1.0, 0.86)
        # composition « par-dessus » du trait sur le liseré
        a0 = img[..., 3] / 255
        a = a_trait + a0 * (1 - a_trait)
        rgb = (col * a_trait[..., None] + img[..., :3] * (a0 * (1 - a_trait))[..., None]) / np.maximum(a, 1e-6)[..., None]
        img[..., :3] = rgb
        img[..., 3] = a * 255
        cx, cy = (i % cols) * F, (i // cols) * F
        atlas[cy:cy + F, cx:cx + F] = np.clip(img, 0, 255).astype(np.uint8)
    return Image.fromarray(atlas, "RGBA")


def trait(k):
    W, H = TRAIT_U * k, 10 * k
    n_w, n_h = W * SS, H * SS
    yy, xx = np.mgrid[0:n_h, 0:n_w].astype(float)
    u = (xx + 0.5) / n_w
    cy = n_h / 2 + np.sin(u * 7.0) * 0.18 * k * SS
    # attaque franche à gauche, ventre, queue effilée et sèche à droite
    prof = np.where(u < 0.06, np.sqrt(np.clip(u / 0.06, 0, 1)) * 1.05, 1.0)
    prof = prof * (1 - 0.55 * np.clip((u - 0.55) / 0.45, 0, 1) ** 1.4)
    prof *= np.where(u > 0.97, np.clip((1 - u) / 0.03, 0, 1), 1)
    ep = (2.6 * k * SS) * prof * (1 + 0.06 * np.sin(u * 31))
    t = (yy + 0.5 - cy) / np.maximum(ep / 2, 1e-6)
    on = np.abs(t) <= 1
    strie = np.floor((t + 1) * 2.6)
    on &= ~((u > 0.62) & (_h(strie, np.floor(u * 90)) < (u - 0.62) * 1.9))
    cov = on.reshape(H, SS, W, SS).mean(axis=(1, 3))
    halo = np.zeros_like(cov)
    for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        halo = np.maximum(halo, np.roll(np.roll(cov, a, 0), b, 1))
    halo = np.clip(halo - cov, 0, 1) * 0.55
    a = cov * 0.82 + halo * (1 - cov * 0.82)
    rgb = (ENCRE * (cov * 0.82)[..., None] + LISERE * (halo * (1 - cov * 0.82))[..., None]) / np.maximum(a, 1e-6)[..., None]
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = np.clip(rgb, 0, 255)
    img[..., 3] = np.clip(a * 255, 0, 255)
    return Image.fromarray(img, "RGBA")


def perle(k):
    C = 11 * k
    out = np.zeros((C, 2 * C, 4), np.uint8)
    for j, (coeur, teinte) in enumerate((((236, 250, 255), (111, 211, 255)), ((255, 228, 222), (224, 80, 90)))):
        yy, xx = np.mgrid[0:C * SS, 0:C * SS].astype(float)
        c = C * SS / 2
        d = np.sqrt((xx + 0.5 - c) ** 2 + (yy + 0.5 - c) ** 2) / SS / k     # en unités
        noyau = (d <= 1.6).astype(float)
        bord = ((d > 1.6) & (d <= 2.4)).astype(float)
        noy = noyau.reshape(C, SS, C, SS).mean(axis=(1, 3))
        brd = bord.reshape(C, SS, C, SS).mean(axis=(1, 3))
        dd = d.reshape(C, SS, C, SS).mean(axis=(1, 3))
        glow = np.clip(1 - dd / 5.2, 0, 1) ** 2 * 0.55
        rgb = np.zeros((C, C, 3))
        a = np.zeros((C, C))
        # halo
        rgb[:] = teinte
        a = glow
        # anneau coloré puis cœur clair
        rgb = rgb * (1 - brd[..., None]) + np.array(teinte) * brd[..., None]
        a = np.maximum(a, brd)
        rgb = rgb * (1 - noy[..., None]) + np.array(coeur) * noy[..., None]
        a = np.maximum(a, noy)
        out[:, j * C:(j + 1) * C, :3] = np.clip(rgb, 0, 255)
        out[:, j * C:(j + 1) * C, 3] = np.clip(a * 255, 0, 255)
    return Image.fromarray(out, "RGBA")


def goutte(k):
    W, H = 3 * k, 4 * k
    out = np.zeros((H, 3 * W, 4), np.uint8)
    for j, (c, a_) in enumerate((((242, 213, 154), 1.0), ((226, 100, 116), 1.0), ((22, 13, 16), 0.45))):
        yy, xx = np.mgrid[0:H * SS, 0:W * SS].astype(float)
        X = (xx + 0.5) / SS / k - 1.5
        Y = (yy + 0.5) / SS / k
        # goutte : pointe en haut, ventre rond en bas
        r = np.where(Y < 1.6, (Y / 1.6) * 1.15, 1.15)
        m = (np.abs(X) <= r) & (((Y - 2.6) ** 2 + X ** 2 <= 1.25) | (Y < 2.6))
        cov = m.astype(float).reshape(H, SS, W, SS).mean(axis=(1, 3))
        out[:, j * W:(j + 1) * W, :3] = c
        out[:, j * W:(j + 1) * W, 3] = np.clip(cov * 255 * a_, 0, 255)
    return Image.fromarray(out, "RGBA")


def sceaux():
    im = nouveau(80, 16)
    for i, r in enumerate(ea.RANGS):
        gf.sceau_rang4(im, 16 * i + 7, 7, r, 14)
    S.sauver("sceaux.png", im, (80, 16))


if __name__ == "__main__":
    for k in (1, 2, 3, 4):
        F = F_U * k
        S.sauver(f"enso_{k}.png", enso_frames(k), (F * 8, F * 5))
        S.sauver(f"trait_{k}.png", trait(k), (TRAIT_U * k, 10 * k))
        S.sauver(f"perle_{k}.png", perle(k), (22 * k, 11 * k))
        S.sauver(f"goutte_{k}.png", goutte(k), (9 * k, 4 * k))
    sceaux()
    S.bilan()
