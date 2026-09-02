# ShinobiAbilities

Jutsu, mobilité et apprentissage pour le serveur Reborn. Module compagnon de
ShinobiCore (soft-depend : core démarre seul, ShinobiAbilities se désactive
sans lui).

## Compiler

Depuis la racine `ShinobiReborn` (le pom agrégateur compile core puis abilities
dans le bon ordre) :

```
mvn clean package
```

Jars produits : `ShinobiCore/target/ShinobiCore-0.1.0-SNAPSHOT.jar` et
`ShinobiAbilities/target/ShinobiAbilities-0.1.0-SNAPSHOT.jar`. Les DEUX vont
dans `plugins/` (ShinobiCore a été patché : 2 events + registre itemgive
public — l'ancien jar core n'expose pas ces hooks).

## Ce que contient le plugin

### Jutsu
- 7 JutsuItems (`/sc itemgive Jutsu_…`) : Rouleau, Pupilles, Katana, Kunai,
  Shuriken, Fūma, Poing. Objets-clés universels, aucun marquage propriétaire —
  les liaisons vivent sur le personnage (`bindings.yml`).
- **F** : ouvre/ferme le sélecteur (hotbar swap, snapshot restauré à la
  fermeture, à la déconnexion, au KO, au changement de personnage, à la mort).
- **Accroupi + F** : GUI d'édition des 5 slots (catalogue filtré par canal).
- **Clic gauche, sélecteur fermé** : quick-cast du slot 1 — instantané, sans
  incantation (« bouton panique »).
- Tir depuis le sélecteur : incantation d'abord — mais la **vérification
  (personnage / cooldown / chakra) passe AVANT** la moindre bossbar ou son.
  Cadence 0,25 s/étape, mudras E/D=2, C/B=4, A/Hiden=6 (la liste `mudras:`
  explicite prime), pitch 1.0→1.4, bossbar bleue avec mudra doré + nom aqua.
- Méthodes : LEFT_CLICK (déf.), RIGHT_CLICK, MUDRA, HOLD_SNEAK (≥1 s),
  CLICK_SEQUENCE (3 clics/2 s). F_PRESS déprécié → réécrit LEFT_CLICK + warn.
- Effets : 11 façonnés (Gōkakyū, Teppōdama, Kamaitachi, Doryūheki, Shiden,
  Shōsen, Kasumi, Konoha Senpū, Iai, Shunshin, Kawarimi) + ~30 génériques par
  feuille de catégorie, dégâts ~0,30-0,45 × coût en chakra.
- `jutsu.require-learned` (config, défaut **false**) : limite liaison + cast
  aux techniques connues ; bypass `shinobiabilities.admin`.

### Mobilité (les 7 fixes historiques sont préservés)
- Course Shinobi (tap accroupi), Double Saut / Saut Mural (Espace, jeton
  d'air + dwell au sol continu), Onde de Choc (accroupi 3 s en l'air), Dash
  (accroupi maintenu au sol), Escalade (accroupi contre un mur, 2 charges),
  Pistes (F près du premier point, F pour lâcher — éditeur staff `/trail`).
- `/toggle` : GUI on/off par personnage (stockage `toggles.yml`, plus rien
  dans ShinobiCore).
- Dégâts de chute désactivés pour les joueurs « ground game ».
- Modificateur de vitesse par NamespacedKey (jamais par nom), nettoyé au
  join/quit/switch/KO/disable.

### Apprentissage
- Parchemins (`/sa parchemin <id|random> [joueur]`, `/sc itemgive
  Parchemin_Random`) + Étagères (3 ou 9 emplacements, `/sc itemgive
  Block_LearningShelf[Large]`), contenu persistant `shelves.yml`.
- Minijeux : MUDRA (titres + clic gauche, clic droit = raté, timeout par
  difficulté), PUSHUP (reps accroupi par rang, timer par rep), SUIVI
  (validation staff `/sa valider|refuser <joueur>`), NONE.
- Succès → `LearnedState` du personnage (`learned:`, persisté par ShinobiCore),
  parchemin consommé. `/techniques` liste les acquis ; « Recueil des
  Techniques » (livre) via itemgive.

### HUD
Deux sections enregistrées dans le CooldownHud de core : **Jutsu** (recharges
en cours, max 5 lignes) et **Mobilité** (Course ON, charges d'Escalade,
recharges Dash/Onde).

### Framework GUI partagé (pour TOUTES les futures interfaces)
Le framework vit désormais dans le moteur (ShinobiCore,
`gui/framework`, derrière l'api `ScreenRouter`) ; ce plugin fournit son
routeur (`GuiRouter`) et ses écrans. Chaque écran est une sous-classe
de `Screen` : on
implémente `title`, `rows`, `render` (boutons fabriqués via `Ui.*`,
porteurs d'une action PDC) et `onAction`. Tout le reste est mutualisé
dans `ScreenManager`, l'unique listener GUI du plugin : annulation des
clics/drags, bouton Fermer, bouton Retour (`onBack`), pagination
standard (`pages()` + flèches automatiques du `Ui.footer`), sons,
fermeture forcée au changement de personnage / KO. Les écrans qui
manipulent de vrais items (étagère) surchargent `onRawClick`.
Créer un nouveau GUI = 1 classe, 0 listener, 0 gestion de slots de
navigation. `AbilityText` centralise lore + détails chat des techniques.

### Pont MagicSpells — jutsu 100 % YAML
Un jutsu se crée entièrement dans `abilities.yml` (modèle documenté en
tête de fichier, rechargeable via `/sa reload`). Trois formes d'effet,
cumulables : `effect:` (registre interne), `magicspell: <sort>` (exécute
le modèle console `cast forcecast %player% %sort%`, configurable sous
`jutsu.magicspells.command-template`), ou `commands:` brutes avec
`run-as: CONSOLE|PLAYER`. Placeholders : `%player% %uuid% %character%
%world% %x% %y% %z%`. L'incantation s'adapte au type d'art : `mudras:`
(signes typés) pour le ninjutsu, `steps:`/`mouvements:` (libellés
libres) pour le reste — affichés dans la bossbar et repris par le
minijeu MUDRA. Sans liste : décompte par rang (E/D 2, C/B 4, A/Hiden 6).
`softdepend: MagicSpells` garantit l'ordre de chargement.

**Tout MagicSpells dans le yml** : `/sa importspells [catégorie] [rang]`
scanne les sorts chargés par MS et AJOUTE en fin d'abilities.yml une
entrée `ms_<sort>` pour chaque sort encore absent (catégorie
`autres/divers` + rang C par défaut), sans toucher aux lignes
existantes, puis recharge le registre. Relancer la commande n'importe
quand : seuls les nouveaux sorts sont ajoutés. Il ne reste qu'à affiner
catégorie/rang/coûts/mudras dans le fichier.

### Interface — GUI d'abord
`/menu` (alias `/hub`) ouvre le **Menu Shinobi**, construit avec le
framework d'écrans et le thème (`GuiTheme`) de ShinobiCore (même
langage visuel que le GUI personnage) :

- **Mes Techniques** — acquis du personnage, paginé, détails au clic.
- **Catalogue** — toutes les voies (Ninjutsu, Bukijutsu, Taijutsu,
  Kekkei Genkai, Dōjutsu, Senjutsu, Autres) avec marqueur « ✔ Connue ».
- **Mes Liaisons** — choix du canal puis éditeur 5-slots restylé.
- **Mobilité** — les interrupteurs /toggle, restylés.
- **Encyclopédie** — le Recueil feuilleté directement (openBook).
- **Validations (Suivi)** — staff : têtes des demandeurs, clic gauche
  valide / clic droit refuse.
- **Administration** — admin : choix d'un personnage (têtes) →
  apprendre/retirer au clic dans le catalogue, liaisons, tout
  apprendre / tout oublier (double-clic), reset cooldowns.

Tous les écrans se ferment automatiquement au changement de personnage
et au KO (GuiCloseListener).

### Commandes
GUI d'abord : `/menu`. Raccourcis : `/toggle`, `/techniques`.
Replis texte (console/macros) : `/sa` reload · learn/forget/learnall ·
bind · cooldowns clear · abilities list/info (admin) · parchemin ·
valider/refuser (staff) · toggles · tokens — `/trail` (staff).

## Patchs apportés à ShinobiCore
- `event/CharacterSwitchEvent` — tiré au DÉBUT de `setActive` (avant la
  capture d'inventaire) pour que le sélecteur restaure la hotbar à temps.
- `event/KoEnterEvent` — tiré dans `enterKo` avant les effets.
- `ItemGiveRegistry.register(...)` public + accesseur `itemGive()` — les
  tokens Jutsu_/Block_/Parchemin_/Book_ apparaissent dans `/sc itemgive`.

## Fichiers de données (plugins/ShinobiAbilities/)
`config.yml` (rechargeable via `/sa reload`), `abilities.yml` (215 entrées :
11 réelles + 204 d'essai), `bindings.yml`, `toggles.yml`, `shelves.yml`,
`trails.yml` — écrits en asynchrone, flush au shutdown.
