# Concurrents & idées — nuit 2026-09-17 (sujet B)

> Veille légère : serveurs/mods Naruto MC + outils d'édition de sorts. Objectif :
> repérer ce qui marche ailleurs et en tirer des pistes pour Reborn.

## Concurrents (serveurs / mods Naruto)

- **AHZNB's Naruto ShinobiCraft** (mod 1.12.2) — chakra via `ninjaxp` (2 ninjaxp = 1 chakra, plafond 50 000), **parchemins de jutsu** trouvés en coffres (donjon/end/mineshaft/nether) → clic droit « learn », **combinaisons de signes** → « milliers » de techniques, et une **suite de mouvement shinobi** riche : sauts ninja directionnels, sprint au chakra, wall-run, wall-jump, marche sur l'eau, **substitution (kawarimi)**, **détection de chakra**, camouflage. [1][3]
- **Naruto: Ultimate Roleplay** — les **signes de main** manifestent les techniques ; grand espace combinatoire. [2]
- **Naruto Daikage** (RPG/PVP) — jutsu + clans. [4]
- Serveurs « custom jutsu » — modèle **3 jutsu par clan + techniques communes** à tous les shinobi. [6]

## Outils d'édition de sorts (tooling)

- **MythicMobs** — scripting YAML (conditions / targeters / triggers). C'est la cible que notre compilateur sait déjà générer. [wiki MythicCraft]
- **MMGUI**, **MythicMobsCreator** (appli desktop), **extension VS Code MythicMobs** — édition *assistée* (formulaires / autocomplétion), mais **pas de graphe visuel de chaînage**. [7][9]
- **SynthEdit** — référence d'UX node-graph (modularité par nœuds). [8]

**→ Notre différenciateur** : l'éditeur Reborn est **graphe-visuel** (chaînage MultiSpell montré à l'écran), **MagicSpells-natif**, avec **preview en jeu** + intégration **gate/coût/cooldown ShinobiCore**. Aucun des outils ci-dessus ne combine les trois. C'est un vrai edge à garder.

## Pistes pour Reborn (idées, non priorisées ici — voir sujet D)

1. **Nœud « Signes de main »** : mapper `CLICK_SEQUENCE` → combos de signes (Tigre/Serpent…), avec un mini-éditeur de séquence. Colle au canon Naruto et au minigame mudra existant.
2. **Kawarimi (substitution)** : mécanique défensive (bûche + téléport court) — très identitaire, absente de notre kit. Faisable en MagicSpells (Blink/Shadowstep + entity bûche) → **excellent futur spell de démo**.
3. **Détection de chakra / sensing** : buff révélant les joueurs proches (Glow ciblé) — buff MagicSpells.
4. **Palette teintée par nature** : auto-`dust_color_transition` par affinité (Katon rouge→orange, Suiton bleu…) dans l'éditeur — un clic, VFX cohérents.
5. **Bibliothèque de templates de nœuds** : « projectile », « combo taïjutsu », « nova de zone », « buff mobilité » — pour créer une technique en partant d'un patron (accélère la roadmap des 57 techniques).
6. **Jutsu par clan** : on a déjà `requires: clan` ; formaliser le modèle « N par clan + communs » dans l'UI (filtre par clan).
7. **Suite mouvement shinobi** : wall-run / water-walk / substitution — étend le Naruto Run existant.

## Sources
- [1] https://www.curseforge.com/minecraft/mc-mods/ahznbs-naruto-mod
- [2] https://n-u-rp.fandom.com/wiki/Jutsu
- [3] https://filmora.wondershare.com/minecraft/top-best-minecraft-naruto-servers.html
- [4] https://www.planetminecraft.com/server/naruto-daikage-a-smp-experience-based-on-naruto-with-jutsu-and-more/
- [6] https://www.minecraftforum.net/forums/servers-java-edition/pc-servers/765682-naruto-rpg-server-24-7-custom-jutsu-clans
- [7] https://polymart.org/resource/mythicmobsgui-mmgui.2641
- [8] https://en.wikipedia.org/wiki/SynthEdit
- [9] https://www.spigotmc.org/resources/mythicmobscreator-increase-productivity-with-user-friendly-editor.36245/
- wiki MythicMobs : https://wiki.mythiccraft.io/mythicmobs
