# -*- coding: utf-8 -*-
"""
gen_garde_icones.py — atlas des icônes au trait du Poste de garde v3 (direction A, « le volet », 14/10).

Icônes simples au trait façon Lucide (licence ISC), redessinées ici : aucune n'est un kanji ni un sceau (DA §3).
Blanches sur transparent, teintées au rendu. Pour rester nettes à toutes les échelles d'interface sans filtrage
(DA §6, règle 3), chaque icône est rendue à **chaque taille en pixels écran** utilisée par le volet : taille GUI
(5, 6, 7, 8, 10) × échelle d'interface (1 à 4). Le code (GardeArt.icone) dessine en pixels écran (1 texel = 1 px)
la rangée dont la taille vaut « taille GUI × échelle », et n'étire que si l'échelle dépasse 4.

Usage (depuis minecraft/mod-hud) :  python tools/gen_garde_icones.py

Contrat — textures/gui/garde/icones.png :
- rangées par taille p (px) dans l'ordre de TAILLES ; rangée de hauteur p + 2, empilées depuis y = 0 ;
- dans une rangée, l'icône d'indice i (ordre de NOMS) est en x = i × (p + 2), carré p × p ;
- NOMS et TAILLES sont recopiés dans GardeArt.java (même ordre) : ne pas réordonner sans mettre le code à jour.
"""
from __future__ import annotations

import os

from PIL import Image, ImageDraw

ICI = os.path.dirname(os.path.abspath(__file__))
SORTIE = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui", "garde",
                      "icones.png")

NOMS = ["chercher", "cloche", "joueurs", "journal", "outils", "croix", "gauche", "droite", "bas", "haut",
        "aller", "ramener", "revenir", "observer", "geler", "sac", "noir", "noter", "sanction", "radio",
        "plus", "reveiller", "annonce", "chat", "commandes", "infirmerie", "zones", "verrou", "filtre", "emote",
        "tenue", "seisme", "zero", "suppr", "lien", "horloge", "point", "epee", "alerte", "crane",
        "drapeau", "aide", "renvoyer", "hopital", "reglages", "masque", "bouclier", "copier", "tri", "coeur",
        "cercle"]
TAILLES = [5, 6, 7, 8, 10, 12, 14, 15, 16, 18, 20, 21, 24, 28, 30, 32, 40]


def masque(nom: str, s: int) -> Image.Image:
    """Icône 24×24 (repère Lucide) rendue à 4× puis réduite à s px (anticrénelée)."""
    S = 4
    N = s * S
    m = Image.new("L", (N, N), 0)
    d = ImageDraw.Draw(m)
    k = N / 24.0
    # trait : 2 unités Lucide, jamais moins d'un pixel final
    w = max(S, int(round(2.0 * k)))

    def P(*pts):
        return [(x * k, y * k) for x, y in pts]

    def L(*pts):
        d.line(P(*pts), fill=255, width=w, joint="curve")
        for x, y in pts:
            r = w / 2
            d.ellipse([x * k - r, y * k - r, x * k + r, y * k + r], fill=255)

    def C(cx, cy, r, plein=False):
        bb = [(cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k]
        if plein:
            d.ellipse(bb, fill=255)
        else:
            d.ellipse(bb, outline=255, width=w)

    def A(x0, y0, x1, y1, a0, a1):
        d.arc([x0 * k, y0 * k, x1 * k, y1 * k], a0, a1, fill=255, width=w)

    def R(x0, y0, x1, y1, r=2):
        d.rounded_rectangle([x0 * k, y0 * k, x1 * k, y1 * k], radius=r * k, outline=255, width=w)

    if nom == "chercher":
        C(11, 11, 7); L((16.2, 16.2), (21, 21))
    elif nom == "cloche":
        A(6, 3, 18, 15, 180, 360); L((6, 9), (6, 15), (4, 17), (20, 17), (18, 15), (18, 9)); A(9.5, 17, 14.5, 22, 0, 180)
    elif nom == "joueurs":
        C(9, 8, 4); A(2, 15, 16, 29, 180, 360); A(14, 4, 20, 12, 270, 90); A(15, 15, 23, 29, 270, 360)
    elif nom == "journal":
        for y in (6, 12, 18):
            C(4, y, 0.9, True); L((8.5, y), (20, y))
    elif nom == "outils":
        R(3, 3, 10, 10, 1.5); R(14, 3, 21, 10, 1.5); R(3, 14, 10, 21, 1.5); R(14, 14, 21, 21, 1.5)
    elif nom == "croix":
        L((6, 6), (18, 18)); L((18, 6), (6, 18))
    elif nom == "gauche":
        L((15, 5), (8, 12), (15, 19))
    elif nom == "droite":
        L((9, 5), (16, 12), (9, 19))
    elif nom == "bas":
        L((6, 9), (12, 15), (18, 9))
    elif nom == "haut":
        L((6, 15), (12, 9), (18, 15))
    elif nom == "aller":                       # map-pin
        A(5, 2, 19, 16, 150, 390); L((5.9, 12.5), (12, 22), (18.1, 12.5)); C(12, 9, 2.6)
    elif nom == "ramener":                     # arrow-down-to-line
        L((12, 3), (12, 16)); L((6.5, 10.5), (12, 16), (17.5, 10.5)); L((5, 21), (19, 21))
    elif nom == "renvoyer":                    # arrow-up-from-line
        L((12, 21), (12, 8)); L((6.5, 13.5), (12, 8), (17.5, 13.5)); L((5, 3), (19, 3))
    elif nom == "revenir":                     # undo
        L((9, 14), (4, 9), (9, 4)); A(4, 9, 20, 25, 270, 360); L((4, 9), (12, 9)); L((20, 17), (20, 20))
    elif nom == "observer":                    # eye
        A(1.5, 5, 22.5, 25, 200, 340); A(1.5, -1, 22.5, 19, 20, 160); C(12, 12, 3.2)
    elif nom == "geler":                       # snowflake
        L((12, 2), (12, 22)); L((3.3, 7), (20.7, 17)); L((3.3, 17), (20.7, 7))
        L((9.5, 3.5), (12, 6), (14.5, 3.5)); L((9.5, 20.5), (12, 18), (14.5, 20.5))
    elif nom == "sac":                         # backpack
        R(4, 7, 20, 22, 3); A(8, 2, 16, 10, 180, 360); L((8, 6), (8, 7)); L((16, 6), (16, 7))
        L((8, 14), (16, 14)); L((8, 14), (8, 18)); L((16, 14), (16, 18))
    elif nom == "noir":                        # eye-off
        A(1.5, 5, 22.5, 25, 200, 340); A(1.5, -1, 22.5, 19, 20, 160); L((3, 3), (21, 21))
    elif nom == "noter":                       # notebook-pen
        R(4, 3, 17, 21, 2); L((8, 8), (13, 8)); L((8, 12), (11, 12))
        L((20.5, 9.5), (13.5, 16.5), (12.5, 19.5), (15.5, 18.5), (22, 12))
    elif nom == "sanction":                    # gavel
        d.line(P((13.5, 3.5), (20.5, 10.5)), fill=255, width=int(w * 2.4))
        L((10, 7), (17, 14)); L((12.5, 11.5), (3.5, 20.5))
    elif nom == "radio":                       # headphones
        A(3, 3, 21, 21, 180, 360); L((3, 12), (3, 17)); L((21, 12), (21, 17))
        R(3, 13, 7.5, 20.5, 1.5); R(16.5, 13, 21, 20.5, 1.5)
    elif nom == "plus":                        # ellipsis
        for x in (5, 12, 19):
            C(x, 12, 1.6, True)
    elif nom == "reveiller":                   # activity
        L((2, 12), (6, 12), (9, 4), (15, 20), (18, 12), (22, 12))
    elif nom == "annonce":                     # megaphone
        L((3, 10), (3, 14), (7, 14), (18, 19), (18, 5), (7, 10), (3, 10)); L((7, 14), (9, 20), (11, 20), (10, 15.5))
        A(18, 9, 22, 15, 270, 90)
    elif nom == "chat":
        R(3, 4, 21, 17, 3); L((7, 17), (7, 21), (11.5, 17))
    elif nom == "commandes":                   # terminal
        L((5, 7), (10, 12), (5, 17)); L((12, 18), (19, 18))
    elif nom == "infirmerie":                  # square-plus
        L((12, 7), (12, 17)); L((7, 12), (17, 12)); R(2.5, 2.5, 21.5, 21.5, 4)
    elif nom == "zones":                       # map
        L((3, 6), (9, 3), (15, 6), (21, 3), (21, 18), (15, 21), (9, 18), (3, 21), (3, 6)); L((9, 3), (9, 18))
        L((15, 6), (15, 21))
    elif nom == "verrou":
        R(5, 11, 19, 21, 2); A(8, 4, 16, 14, 180, 360); L((8, 9), (8, 11)); L((16, 9), (16, 11))
    elif nom == "filtre":
        L((3, 4), (21, 4), (14, 12.5), (14, 20), (10, 18), (10, 12.5), (3, 4))
    elif nom == "emote":
        C(12, 12, 9); A(7.5, 9, 16.5, 17, 20, 160); C(9, 9.5, 0.9, True); C(15, 9.5, 0.9, True)
    elif nom == "tenue":                       # shirt
        L((8, 3), (3, 6), (5, 11), (7, 10), (7, 21), (17, 21), (17, 10), (19, 11), (21, 6), (16, 3))
        A(8, 0.5, 16, 7, 0, 180)
    elif nom == "seisme":
        L((2, 9), (6, 5), (10, 9), (14, 5), (18, 9), (22, 5)); L((2, 17), (6, 13), (10, 17), (14, 13), (18, 17), (22, 13))
    elif nom == "zero":                        # rotate-ccw
        A(3, 3, 21, 21, 200, 520); L((3, 3), (3, 9), (9, 9))
    elif nom == "suppr":
        L((3, 6), (21, 6)); L((8, 6), (8, 3), (16, 3), (16, 6)); L((5, 6), (6, 21), (18, 21), (19, 6))
        L((10, 11), (10, 17)); L((14, 11), (14, 17))
    elif nom == "lien":
        R(2, 8, 13, 16, 4); R(11, 8, 22, 16, 4)
    elif nom == "horloge":
        C(12, 12, 9); L((12, 7), (12, 12), (15.5, 14))
    elif nom == "point":
        C(12, 12, 6, True)
    elif nom == "epee":                        # swords
        L((14.5, 17.5), (3, 6), (3, 3), (6, 3), (17.5, 14.5)); L((13, 19), (19, 13)); L((16, 16), (20, 20))
        L((19, 21), (21, 19))
    elif nom == "alerte":                      # triangle-alert
        L((12, 3), (22, 20), (2, 20), (12, 3)); L((12, 9), (12, 13)); C(12, 16.6, 0.9, True)
    elif nom == "crane":                       # skull simplifié (mise à mort)
        A(4, 2, 20, 18, 180, 360); L((4, 10), (4, 14), (8, 17), (8, 21), (16, 21), (16, 17), (20, 14), (20, 10))
        C(9, 11, 1.6, True); C(15, 11, 1.6, True)
    elif nom == "drapeau":
        L((4, 22), (4, 3)); L((4, 4), (8, 3), (12, 5), (16, 4), (20, 4), (20, 14), (16, 14), (12, 15), (8, 13), (4, 14))
    elif nom == "aide":                        # circle-help
        C(12, 12, 9); A(8.5, 6, 15.5, 13, 200, 400); L((12, 13), (12, 14)); C(12, 17.2, 0.9, True)
    elif nom == "hopital":                     # bed
        L((3, 5), (3, 20)); L((3, 15), (21, 15)); L((21, 20), (21, 11), (11, 11), (11, 15)); C(7, 11, 2)
    elif nom == "reglages":                    # sliders-horizontal
        L((3, 6), (21, 6)); L((3, 12), (21, 12)); L((3, 18), (21, 18))
        C(15, 6, 2.2, True); C(8, 12, 2.2, True); C(16, 18, 2.2, True)
    elif nom == "masque":                      # drama : masque de théâtre (animation / MJ)
        A(3, 3, 21, 21, 0, 360); C(8.5, 10, 1.3, True); C(15.5, 10, 1.3, True); A(7.5, 11, 16.5, 18, 20, 160)
    elif nom == "bouclier":                    # shield
        L((12, 2.5), (20, 5.5), (20, 12), (12, 21.5), (4, 12), (4, 5.5), (12, 2.5))
    elif nom == "copier":
        R(8, 8, 21, 21, 2); L((4, 16), (3, 16), (3, 3), (16, 3), (16, 4))
    elif nom == "tri":                         # arrow-up-down
        L((7, 20), (7, 4)); L((3, 8), (7, 4), (11, 8)); L((17, 4), (17, 20)); L((13, 16), (17, 20), (21, 16))
    elif nom == "coeur":                       # heart (lever l'ATA)
        L((12, 20), (4, 12)); L((12, 20), (20, 12)); A(2.5, 4, 12.5, 14, 135, 360); A(11.5, 4, 21.5, 14, 180, 405)
    elif nom == "cercle":
        C(12, 12, 8)
    else:
        raise KeyError(nom)
    return m.resize((s, s), Image.LANCZOS)


def main():
    h = sum(p + 2 for p in TAILLES)
    w = max(len(NOMS) * (p + 2) for p in TAILLES)
    atlas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    y = 0
    for p in TAILLES:
        for i, nom in enumerate(NOMS):
            m = masque(nom, p)
            blanc = Image.new("RGBA", (p, p), (255, 255, 255, 255))
            atlas.paste(blanc, (i * (p + 2), y), m)
        y += p + 2
    os.makedirs(os.path.dirname(SORTIE), exist_ok=True)
    atlas.save(SORTIE, optimize=True)
    print("ok", os.path.normpath(SORTIE), atlas.size, os.path.getsize(SORTIE), "octets")


if __name__ == "__main__":
    main()
