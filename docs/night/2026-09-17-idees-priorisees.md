# Idées priorisées — Technique Creator & serveur (nuit 2026-09-17, sujet D)

> Priorisation impact / effort. Objectif directeur : **rendre atteignables les 57
> techniques dues (9 oct → 3 déc)** et exploiter l'edge « éditeur graphe visuel +
> MagicSpells-natif + preview + gate ». S'appuie sur
> `2026-09-17-concurrents-idees.md` et `AUDIT_TECHNIQUES_ET_CREATOR.md`.

## 🟢 Quick wins (fort impact / faible effort) — à faire en premier

1. **Préfixe anti-collision à l'import** *(≈½ j)* — renommer les sorts importés
   (`imported_<slug>_<nom>`) pour pouvoir importer PUIS déployer sans écraser les
   sorts serveur existants. ⚠️ rewrite aussi les refs dans les options
   (`land-spell`, `spell-on-hit-entity`…) → à faire supervisé (correctness).
2. **Bibliothèque de templates de nœuds** *(≈1 j)* — patrons prêts : « projectile
   ninjutsu », « combo taïjutsu (MultiSpell + emote) », « nova de zone », « buff
   mobilité », « soin ». Créer une technique = partir d'un patron. **Multiplie la
   vitesse de production** vers les 57 techniques.
3. **Palette teintée par nature** *(≈½ j)* — bouton « Katon/Suiton/… » sur un nœud
   particules → auto-`dust_color_transition` cohérent. VFX pro en un clic.
4. **Sélecteur de fichier serveur à l'import** *(≈½ j)* — lister
   `plugins/MagicSpells/*.yml` (via FilesService.list) dans le modal au lieu de
   taper le chemin. Rend l'import « voir mes sorts » immédiat.

## 🟡 Moyen terme (fort impact / effort moyen)

5. **Éditeur de keyframes `entity` complet** *(≈2 j)* — timeline visuelle des
   `delayed-entity-data` (scale/rotation/translation par frame) + aperçu. C'est
   80 % du ressenti des sorts (les item_display animés dominent le serveur).
6. **Vue « bibliothèque de sorts »** *(≈2 j)* — importer un fichier entier et voir
   TOUS ses sorts en graphe (pas seulement une racine). Pour auditer/éditer les
   77 Ko de Shukaku visuellement.
7. **Nœud « Signes de main »** *(≈2-3 j)* — `CLICK_SEQUENCE` → combos de signes,
   mini-éditeur de séquence, branché sur le minigame mudra existant. Très canon.
8. **Validation live enrichie** *(≈1 j)* — au-delà des particules : vérifier que
   les refs de sorts externes (MythicMobs/emotes) existent, avertir sur les
   `PassiveSpell tick` coûteux, plafonner `iterations×period`.

## 🟠 Ambitieux (fort impact / gros effort)

9. **Kawarimi (substitution)** *(≈2 j, contenu)* — mécanique défensive
   identitaire (bûche item_display + Blink/Shadowstep). Excellent sort de démo +
   feature de gameplay. Absente du kit actuel.
10. **Compilation incrémentale + déploiement 1-clic depuis l'éditeur** *(≈3 j)* —
    « Déployer » écrit + reload seulement les sorts modifiés, avec diff visible
    avant envoi. Boucle designer complète.
11. **Suite mouvement shinobi** *(≈1 sem)* — wall-run / water-walk / substitution,
    au-delà du Naruto Run. Gros chantier gameplay (plutôt post-beta).

## 🔴 Dette / hygiène (à planifier)

- **Sortir les 204 stubs** de `abilities.yml` live → `abilities-debug.yml`
  (mécanisme déjà en place, reste l'opération data — supervisée).
- **Localiser + corriger le crash BlockData** serveur (cf.
  `2026-09-17-correctifs-prepares.md`).
- **Unifier le tronc `main`** (consolidation des deux branches de déploiement) —
  session dédiée + vérifiée.

## Ordre conseillé pour la roadmap 57 techniques
**Templates (2) → palette nature (3) → sélecteur fichier (4) → keyframes entity (5)**
puis produire les techniques par lots (taïjutsu → kenjutsu → ninjutsu → clanique)
en partant des patrons. Le préfixe anti-collision (1) dès qu'on veut réutiliser
des sorts existants comme base.
