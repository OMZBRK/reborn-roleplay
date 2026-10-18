#!/usr/bin/env python3
"""Bras fins (carrure Alex, 3 px) pour les calques du personnage composé — 18/10.

Depuis le 18/10 tous les personnages Reborn (Homme et Femme) sont en carrure fine : le modèle Alex lit des bras de
3 px de large. Un calque peint pour la carrure large (Steve, bras de 4 px) est converti ici : sur chaque bras (bras
droit 40,16 et gauche 32,48, manches 40,32 et 48,48) on retire UNE colonne de pixels des faces avant, dessus, dessous et
arrière (la même colonne vue de face, donc la colonne miroir à l'arrière), et on recolle les faces au gabarit fin :

    large : dessus 4..7 | dessous 8..11 | côté ext. 0..3 | avant 4..7 | côté int. 8..11 | arrière 12..15
    fin   : dessus 4..6 | dessous 7..9  | côté ext. 0..3 | avant 4..6 | côté int. 7..10 | arrière 11..13

Les côtés gardent leurs 4 colonnes (seule la profondeur reste 4 px). La colonne retirée est une colonne du milieu
(1 ou 2 de la face avant) : celle qui fait le moins perdre — la plus proche de sa voisine, sur toutes les faces du bras
et de sa manche à la fois (bras et manche restent alignés), donc les bordures, liserés et motifs des bords sont gardés.
À égalité : la colonne côté corps. Le masque RGBA `<id>_Mask.png` éventuel reçoit exactement la même coupe.

Détection : un calque est « large » quand les colonnes 14-15 d'un bras (arrière d'un bras de 4 px, vides en carrure
fine) portent au moins 8 pixels et autant que les colonnes 12-13 — sauf si cette zone (avec les colonnes 10-11 du haut)
est bouchée d'une seule couleur unie : c'est alors un dessin fin « élargi » à la peau (cas de Complet_Ceremonie2), gardé
tel quel. Un calque déjà fin n'est pas touché (le script est idempotent). Le compositeur du mod (`fr.reborn.hud.skin.BrasFins`) applique la même règle à la volée : un asset live
du serveur peint en 4 px est converti au chargement.

Usage (depuis minecraft/mod-hud) :
    python tools/bras_fins.py                 # tout le dossier assets/reborn/textures/character
    python tools/bras_fins.py --dry-run       # liste seulement
    python tools/bras_fins.py chemin.png dossier/ ...
Les originaux sont copiés dans tools/bras_fins_originaux/<chemin relatif> avant écriture (jamais écrasés).
"""
import argparse
import os
import shutil
import sys

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
CHARACTER = os.path.normpath(os.path.join(ICI, '..', 'src', 'main', 'resources', 'assets', 'reborn', 'textures',
                                          'character'))
ORIGINAUX = os.path.join(ICI, 'bras_fins_originaux')

# (bras, manche) : droit puis gauche ; « interieur » = colonne retirée à égalité (côté corps)
PAIRES = (((40, 16), (40, 32), 2), ((32, 48), (48, 48), 1))


def opaque(px, x, y):
    return px[x, y][3] != 0


def remplissage(px, bx, by):
    """Calque peint en 3 px dont la zone inutilisée (colonnes 14-15, et 10-11 en haut) est bouchée d'UNE seule couleur
    (souvent la peau, habitude des skins Alex « élargis ») : c'est un dessin fin, il ne faut pas le couper."""
    zone = [px[bx + x, by + y] for x in (14, 15) for y in range(4, 16)] +            [px[bx + x, by + y] for x in (10, 11) for y in range(4)]
    return all(p[3] != 0 for p in zone) and len(set(zone)) == 1


def est_large(px):
    for paire in PAIRES:
        for bx, by in paire[:2]:
            a = sum(1 for x in (14, 15) for y in range(4, 16) if opaque(px, bx + x, by + y))
            b = sum(1 for x in (12, 13) for y in range(4, 16) if opaque(px, bx + x, by + y))
            if a >= 8 and a * 5 >= b * 3 and not remplissage(px, bx, by):
                return True
    return False


def ecart(p, q):
    if p[3] == 0 and q[3] == 0:
        return 0
    if p[3] == 0 or q[3] == 0:
        return 1000
    return sum(abs(p[i] - q[i]) for i in range(4))


def faces(bx, by):
    """(x0 de la face, rangées, miroir) des quatre faces de 4 px de large d'un bras large."""
    return ((bx + 4, range(by, by + 4), False),        # dessus
            (bx + 8, range(by, by + 4), False),        # dessous
            (bx + 4, range(by + 4, by + 16), False),   # avant
            (bx + 12, range(by + 4, by + 16), True))   # arrière (vu de face : colonnes inversées)


def perte(px, blocs, c):
    tot = 0
    for bx, by in blocs:
        for x0, rangees, miroir in faces(bx, by):
            k = 3 - c if miroir else c
            for y in rangees:
                p = px[x0 + k, y]
                tot += min(ecart(p, px[x0 + k - 1, y]), ecart(p, px[x0 + k + 1, y]))
    return tot


def plan(px):
    """Colonne retirée (1 ou 2, vue de face) pour le bras droit et le bras gauche."""
    out = []
    for bras, manche, interieur in PAIRES:
        pertes = {c: perte(px, (bras, manche), c) for c in (1, 2)}
        if pertes[1] == pertes[2]:
            out.append(interieur)
        else:
            out.append(min(pertes, key=pertes.get))
    return out


def convertir(im, cols):
    src = im.copy()
    s = src.load()
    d = im.load()
    for (bras, manche, _), c in zip(PAIRES, cols):
        garde = [k for k in range(4) if k != c]
        garde_dos = [k for k in range(4) if k != 3 - c]
        for bx, by in (bras, manche):
            for y in range(16):
                for x in range(16):
                    d[bx + x, by + y] = (0, 0, 0, 0)
            for y in range(4):
                for j, k in enumerate(garde):
                    d[bx + 4 + j, by + y] = s[bx + 4 + k, by + y]      # dessus
                    d[bx + 7 + j, by + y] = s[bx + 8 + k, by + y]      # dessous
            for y in range(4, 16):
                for x in range(4):
                    d[bx + x, by + y] = s[bx + x, by + y]              # côté extérieur
                    d[bx + 7 + x, by + y] = s[bx + 8 + x, by + y]      # côté intérieur
                for j, k in enumerate(garde):
                    d[bx + 4 + j, by + y] = s[bx + 4 + k, by + y]      # avant
                for j, k in enumerate(garde_dos):
                    d[bx + 11 + j, by + y] = s[bx + 12 + k, by + y]    # arrière
    return im


def sauver_original(chemin):
    try:
        rel = os.path.relpath(chemin, CHARACTER)
    except ValueError:
        rel = os.path.basename(chemin)
    if rel.startswith('..'):
        rel = os.path.basename(chemin)
    dest = os.path.join(ORIGINAUX, rel)
    if not os.path.exists(dest):
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        shutil.copy2(chemin, dest)
    return dest


def traiter(chemin, essai):
    im = Image.open(chemin)
    mode = im.mode
    rgba = im.convert('RGBA')
    if rgba.size != (64, 64):
        return None
    px = rgba.load()
    if not est_large(px):
        return None
    cols = plan(px)
    if not essai:
        sauver_original(chemin)
        convertir(rgba, cols).save(chemin)
        masque = chemin[:-4] + '_Mask.png'
        if os.path.isfile(masque):
            sauver_original(masque)
            m = Image.open(masque).convert('RGBA')
            convertir(m, cols).save(masque)
    return cols


def fichiers(cibles):
    for c in cibles:
        if os.path.isdir(c):
            for dp, _, fn in os.walk(c):
                if os.path.basename(dp) == 'ui':
                    continue
                for f in sorted(fn):
                    if f.lower().endswith('.png') and not f.endswith('_Mask.png'):
                        yield os.path.join(dp, f)
        elif c.lower().endswith('.png'):
            yield c


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('cibles', nargs='*', default=[CHARACTER])
    ap.add_argument('--dry-run', action='store_true')
    a = ap.parse_args()
    n = 0
    for f in fichiers(a.cibles):
        cols = traiter(f, a.dry_run)
        if cols is not None:
            n += 1
            print('%s %s (colonne retirée : bras droit %d, bras gauche %d)'
                  % ('[essai]' if a.dry_run else 'converti', f, cols[0], cols[1]))
    print('%d calque(s) %s' % (n, 'à convertir' if a.dry_run else 'converti(s)'))


if __name__ == '__main__':
    sys.exit(main())
