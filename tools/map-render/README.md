# map-render — texture de la carte du monde (touche M)

Produit `minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/map/<id>.png`
et son géoréférencement `assets/reborn/maps/<id>.json`, lus par `WorldMapScreen`.
Les noms de zones et marqueurs ne sont **pas** dans l'image : ils viennent de
`ShinobiCore/places.yml` (éditable en jeu avec `/carte set|del`).

Dépendances : Python 3 + numpy + Pillow. Le parseur NBT/Anvil est maison.

## Regénérer Konoha

1. Télécharger les régions du monde `PaysDuFeu06` (serveur de build, site WinSCP
   `RB - BUILD`, dossier `/world/dimensions/minecraft/paysdufeu06/region/`).
   Konoha = `r.27..34.15..22.mca` (enceinte : centre 15900, 9720 ; rayon ≈ 1790).
2. Rendu brut (1 px = 1 bloc) — couleurs moyennes des textures vanilla :

   ```bash
   python render.py <dossier region> ~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar raw.png --bounds 14000 7820 17800 11620
   ```

3. Stylisation pixel-art (relief, forêts, bord dithéré sur parchemin, palette réduite) :

   ```bash
   python stylize.py raw.png konoha.png --center 15900 9720 --radius 1790 --size 950
   ```

4. Copier `konoha.png` → `assets/reborn/textures/gui/map/konoha.png` et
   `konoha.json` → `assets/reborn/maps/konoha.json`, puis publier le mod (train **Game**).

Une nouvelle carte (autre village) = nouveau couple PNG/JSON + une entrée `maps.<id>`
dans `places.yml` liée au monde Bukkit.
