# Passation — Audit du système de techniques & spécification du Technique Creator

> Session Claude Code du **2026-09-08**, branche `chore/portabilite-multi-postes` (621197f).
> Session sœur : « repository documentation audit » (cohérence docs/Trello/Miro, branches,
> portabilité, Creator Tools Blockbench).

---

## 1. Sujet

Audit complet du système de techniques (les 6 plugins Shinobi + le pont MagicSpells + les
particules 26.2 + la perf des effets), et rédaction de la spécification de l'outil de
création de techniques par graphe de nœuds dont les maquettes
`reborn-design-prep/reference-screen/creator{,2,3}.png` fixent la direction.

**Aucune ligne de code n'a été écrite.** Le livrable est un document d'analyse et de
conception, plus un artefact web partageable pour les contributeurs que le user veut
associer à la conception de l'outil.

---

## 2. Ce qui est fait

### Un seul fichier créé : `docs/AUDIT_TECHNIQUES_ET_CREATOR.md`

Document de ~450 lignes, en français, structuré en 8 sections. Voici son contenu réel et
le **pourquoi** de chaque constat — c'est cette partie qui a de la valeur, pas le fichier.

**§1 — État réel, chiffré.** 215 abilities dans `abilities.yml` (87 Ko), dont **11
seulement** sont de vraies techniques avec un effet Java dédié ; les 204 autres sont des
stubs générés mécaniquement (34 catégories × 6 rangs), tous à 50 chakra / 5 s / clic gauche.
35 catégories distinctes. **0 ability n'utilise MagicSpells** — aucun `magicspell:`, aucun
`commands:`.

**§1 bis — le pont MagicSpells est complet et mort.** Toute la plomberie existe et est
correcte (`JutsuMeta.commands` + `runAsPlayer`, `JutsuExecutionManager.dispatchCommands`
avec 7 placeholders, `jutsu.magicspells.command-template`, `MagicSpellsHook` en réflexion
pure, `/sa importspells`, `plugins/MagicSpells` dans les scopes du panel, `ms reload` dans
`PanelBridge`). Elle n'a simplement jamais servi. **Pourquoi c'est important** : quiconque
reprend le sujet va croire que la brique manque et la réécrire. Elle ne manque pas.

**§2 — les cinq trous structurels.** Le plus important, et de loin :
**`Ability` n'a aucune notion de prérequis.** Pas de `requires`, pas de `prerequisites`,
pas de `unlocked-by` ; `knownAbilities` est un `Set<String>` plat et `gateOrWarn` ne teste
que `knowsAbility(id)`. Conséquence concrète : « Rasengan exige maîtrise ≥ 60 en Contrôle du
Chakra + examen Chūnin + mentor Jōnin » est **inexprimable aujourd'hui**. C'est ce que le
nœud `Gate` de la maquette veut résoudre — donc **l'apport principal du créateur n'est pas
l'interface, c'est le modèle de données** ; sans `requires:` côté runtime l'éditeur n'aurait
rien à compiler. Les quatre autres : pas de variantes/embranchements (le modèle « 0-90
efficacité / 90 %+ version améliorée » est promis en commentaire de config, `mastery-per-cast`
accumule, mais **personne ne lit jamais le seuil**) ; le YAML unique sans validation de
schéma (une catégorie non routée, un `effect:` inexistant ou un mudra inconnu dégradent
silencieusement vers `generic_fallback` — un designer casse sans le voir) ; aucune boucle
d'itération ; et un plafond de créativité très bas pour un non-développeur.

**§3 — inventaire des particules 26.2.** Extrait du **client déobfusqué**
(`%APPDATA%\RebornRoleplay\versions\26.2\26.2.jar` →
`net/minecraft/core/particles/ParticleTypes.class`, chaînes du constant pool) puis croisé
avec un `grep Particle.<CONST>` sur les 309 fichiers Java de `minecraft/shinobi`.
**125 types existent, 27 sont utilisés, 98 sont inexploités** — dont toute la famille
`geyser` que le user citait, plus `sulfur_*`/`noxious_gas` (poisons, Kugutsu),
`copper_fire_flame`, `block_crumble`, `dust_pillar`, `sculk_soul`, `shriek`/`sonic_boom`,
`vault_connection`, `firefly`. Les deux plus utiles stratégiquement :
`dust_color_transition` (une palette par affinité avec un seul type) et la famille
`item`/`block` (particules paramétrées par un item Nexo).

**§4 — optimisations, avec les chiffres.** La priorité 1 est
`ShinobiCore/util/Effects.java:49` : `forwardCone` boucle sur
`world.spawnParticle(type, loc, 1, 0,0,0,0)`, soit **un paquet réseau par particule**,
diffusé à tous les joueurs dans la view distance. Katon Gōkakyū = **156 paquets par cast**
(140 FLAME + 16 LAVA) × chaque spectateur, plus 480 `Math.random()`. Grouper par tranche de
cône avec les `offset` donne ~12 paquets pour un rendu identique. Puis : aucun LOD par
distance ; `damageCone` scanne une boîte de 22³ ≈ 10 648 blocs³ pour un cône qui en occupe
~1/6 ; `normalize()` sur un vecteur nul si une entité est pile à la position de l'œil ;
les 204 stubs chargés en prod ; pas d'index par catégorie ; `CooldownTracker` en mémoire
seule (un cooldown HIDEN de 30 min est effacé par un restart).

**§5 — spécification du créateur.** Le principe qui rend le projet réaliste :
**le graphe n'est pas le runtime**, il compile vers des artefacts que le serveur sait déjà
lire (`graph.json` = source de vérité, `abilities.yml` v2 et `spells-*.yml` = artefacts de
build). Aucun interpréteur de graphe côté Paper. Le transport existe déjà de bout en bout :
`FilesService.write` (SFTP + `.bak` + audit) puis `PanelBridge` pour le reload sans RCON.
**Trouvaille exploitable immédiatement** : `PanelBridge.ALLOWED_PREFIXES` autorise `nexo`,
`ms`, `mm`, `meg`, `creator` et `playemote` — **mais pas `sa reload` ni `sc reload`**. Le
panel peut donc recharger MagicSpells mais pas le registre de techniques. Deux lignes, et
c'est le prérequis de tout déploiement automatisé depuis l'éditeur. Le document contient
aussi le catalogue de nœuds complet (avec les 6 nœuds d'effet absents de la maquette, dont
`Sequence`/`Delay` — le timing c'est 80 % du ressenti d'une technique), le schéma
`requires:`/`variants:` cible, et un plan en 4 phases dont la phase 0 (2-3 j) a de la valeur
sans aucun outil.

**§7 — MagicSpells : analyse, pertinence, moteurs concurrents.** Ajoutée après une relance
du user. Le fait qui retourne la question : **l'inventaire SFTP vérifié du 2026-08-05**
(`docs/MIGRATION_26.1.md §0`) liste les plugins réellement présents — build (7002) : Axiom,
Chunky, EssentialsX, FAWE, HeadDatabase, Multiverse, **MythicMobs**, ShinobiCore/Abilities,
ProtocolLib, spark ; dev (7012) : ShinobiCore/Abilities/Learning/Sense/Tail, spark, bStats.
**Ni MagicSpells, ni ModelEngine, ni Nexo n'y figurent. MythicMobs, si.** Donc la raison
pour laquelle le pont n'a jamais servi est plus radicale que « personne n'a écrit de sort » :
il n'y a probablement pas de MagicSpells à l'autre bout — ce qui explique cohéremment le
`softdepend: MagicSpells`, le hook en réflexion pure et le message d'erreur prévu par
`importspells`. La section pose ensuite : ce que MagicSpells est vraiment (un moteur de jeu
concurrent — mana, cooldowns, spellbook, variables, `modifiers:` — dont **six fonctions sur
sept sont des doublons de ShinobiCore**) ; la règle d'usage saine (**ShinobiCore décide,
MagicSpells dessine** : jamais de `mana:`, `cooldown:`, `modifiers:` de gating ni
`cast-item:` dans un sort MS) ; les manquements 26.2 (compat inconnue et bloquante ; le vrai
test de compat n'est pas « le plugin démarre » mais les **particules paramétrées** —
`dust_color_transition` → `DustTransition`, `block`/`falling_dust`/`block_crumble` →
`BlockData`, `item` → `ItemStack`, `trail`) ; l'argument perf en faveur de MS
(`visible-range` par effet = le LOD manquant, EffectLib, `MultiSpell` avec délais au tick) ;
et la cumulabilité MythicMobs/ModelEngine — **les quatre moteurs passent par le même tuyau
`dispatchCommands`**, donc `Effect · MagicSpell` / `Effect · MythicMob` / `Effect · Raw` sont
le même nœud avec un template différent (généralisation d'une ligne dans `JutsuMeta` :
`external: { provider, ref }`), l'exception étant ModelEngine qui n'est pas appelable par
commande (piloté par MythicMobs) et doit donc être une **propriété** d'un effet MythicMobs,
pas un nœud.

**§7.7 — la pression calendaire.** `ROADMAP_BETA_2026.md` demande **57 techniques entre le
9 octobre et le 3 décembre** (12 taïjutsu S1 + 12 kenjutsu S2 + 15 ninjutsu + 18 sorts
claniques). Il y en a 11, façonnées en Java. **Le créateur n'est donc pas un confort : c'est
ce qui rend la roadmap atteignable**, et le moteur d'effet doit être tranché **avant le
sprint du 9 octobre**.

### Artefact web publié (hors dépôt)

<https://claude.ai/code/artifact/d1f0d34c-dbd8-4289-ad0f-0111eb2d878a> — même contenu, mis
en page pour être partagé aux contributeurs que le user veut associer à la conception de
l'outil. Contient la grille visuelle des 125 particules (allumées = utilisées) et le
catalogue de nœuds rendu sous forme de vraies cartes de nœuds. **Le dépôt reste la source
de vérité** ; si le `.md` évolue, l'artefact diverge et devra être republié.

---

## 3. État de compilation / test

**Franchement : rien n'a été compilé ni testé, et c'était impossible sur ce poste.**

- **Aucun changement de code.** Zéro fichier `.java`, `.ts`, `.rs`, `.yml` modifié. Le seul
  fichier produit est un `.md`. Il n'y a donc rien à compiler et **rien ne peut casser** si
  on lance le projet tel quel.
- **La toolchain Java est absente de cette machine** : `java` = OpenJDK **17.0.18** (il faut
  25), `mvn` **absent**, JDK portable `D:\dev-cache\jdk25` **absent**. Les 6 plugins Shinobi
  et les mods Fabric **ne sont pas compilables ici** — donc aucune vérification en jeu n'a
  pu être faite, ni par moi ni par qui que ce soit sur ce poste.

### Ce qui est vérifié vs. ce qui ne l'est pas — à lire avant d'agir sur l'audit

**Vérifié, mécaniquement, sur le code du dépôt** (fiable) :
- les 215 / 11 / 204 / 35 abilities, les 0 usages de MagicSpells → comptés par `grep` sur
  `abilities.yml` ;
- les 125 types de particules → extraits du constant pool du jar client 26.2 réellement
  présent sur le poste ; les 27 utilisés → `grep` sur les 309 `.java` ;
- l'absence de `requires`/`prerequisites` dans `Ability`/`AbilityRegistry`/`TechniquesService` ;
- le contenu de `PanelBridge.ALLOWED_PREFIXES` (lu ligne à ligne) ;
- le comptage de paquets de `forwardCone` (lu ligne à ligne) ;
- `CooldownTracker` sans persistance (lu ligne à ligne) ;
- l'existence de `StaminaManager` / `KenjutsuEnduranceGate` dans ShinobiCombat.

**NON vérifié — à confirmer en jeu avant toute décision** :
- **quels plugins tournent réellement.** L'inventaire cité date du 2026-08-05 et couvrait la
  migration 26.1 ; il est peut-être partiel (Nexo n'y figure pas non plus alors que
  `server-config/nexo/` est très fourni). → **`/plugins` sur prod, dev et build.** C'est
  l'action n°1 de tout le dossier : elle conditionne la moitié des recommandations.
- **que les constantes Bukkit `Particle.GEYSER` & co. existent dans Purpur 26.2.** Les noms
  Bukkit sont l'uppercase des clés de registre, mais rien ne garantit que Purpur expose 1:1
  les derniers ajouts. → `Arrays.stream(Particle.values()).map(Enum::name)`.
- **qu'un build MagicSpells pour 26.2 existe.** Rien dans le dépôt ne pin de version.
- **la syntaxe exacte de `mm skills cast`** sur la version de MythicMobs installée, et
  quelles mechanics sont premium.

---

## 4. Ce qui n'est PAS fini

- **Tout, côté code.** Aucune des dix actions de la checklist §6 du document n'a été
  entreprise. En particulier les deux quick wins : les deux lignes de
  `PanelBridge.ALLOWED_PREFIXES` et le batching des `spawnParticle`.
- **Le schéma `requires:`** est spécifié dans le document mais pas implémenté ; c'est le
  bloqueur de tout le reste du créateur.
- **`packages/ability-compiler`** n'existe pas — c'est la phase 1 de la spec.
- **La page `apps/admin/app/(panel)/abilities/`** n'existe pas — phase 2.
- **Le catalogue de nœuds n'est pas figé** : il a été déduit du runtime existant et de la
  maquette, pas du contenu réel. Le document dit explicitement qu'il faut d'abord obtenir
  des contributeurs **dix techniques réelles écrites en français avec leurs conditions
  d'accès** — c'est ça qui détermine quels nœuds `Gate` et `Effect` existent, pas l'inverse.
- **L'artefact web et le `.md` sont deux copies** du même contenu ; ils divergeront à la
  première modification. Personne n'a tranché lequel fait foi à long terme (le `.md` par
  convention `CLAUDE.md`, mais l'artefact est le support de partage).

Rien n'est « cassé si on lance tel quel » — il n'y a pas de code.

---

## 5. Décisions ouvertes (arbitrage user)

1. **Quel moteur d'effet ?** C'est la décision structurante et elle a une date limite : le
   **sprint du 9 octobre** (12 sorts taïjutsu). Si MagicSpells n'a pas de build 26.2, la
   recommandation de l'audit est de **ne pas l'installer** et de s'appuyer sur MythicMobs,
   déjà présent, qui couvre l'essentiel du besoin VFX + mechanics et sert de toute façon aux
   invocations et aux boss — une dépendance de moins. Sinon : MagicSpells **uniquement comme
   renderer**, avec la discipline du §7.3.
2. **Va-t-on jusqu'à l'éditeur graphique, ou s'arrête-t-on à la phase 0/1 ?** La phase 0
   (2-3 j) et la phase 1 (compilateur CLI, ~1 sem) ont de la valeur seules et rendent déjà
   le YAML nettement plus expressif. Les phases 2-3 (éditeur + boucle d'essai, 3-5 sem)
   sont un investissement à peser contre les 57 techniques à produire sur la même période.
3. **Les 204 stubs d'essai** : les sortir dans `abilities-debug.yml` (recommandé) ou les
   supprimer purement ? Ils faussent `/sa importspells` et rendent les diffs illisibles.
4. **Qui sont les contributeurs** que le user veut associer, et sous quelle forme leur
   demander les dix techniques de référence ?
5. **Faute de frappe figée** : `impact_kenjtusu` (au lieu de *kenjutsu*) dans l'id Nexo, le
   modèle et la texture de `server-config/nexo/`. À corriger tant que rien n'en dépend —
   les deux entrées de `reborn_effets.yml` sont inutilisées aujourd'hui.

---

## 6. Fichiers touchés

### Créés

| Chemin | Nature |
|---|---|
| `docs/AUDIT_TECHNIQUES_ET_CREATOR.md` | document d'audit + spécification (~450 lignes) |
| `docs/handoff/2026-09-08/session-audit-techniques-magicspells.md` | ce fichier |

### Modifiés

**Aucun.**

### Ce qui n'est PAS à moi

Tout le reste du working tree appartient à d'autres sessions et **ne doit pas être attribué
à celle-ci** — notamment les modifications sur `minecraft/mod-hud/` (`NarutoRun`,
`NarutoRunPayload`, `RebornHudClient`, `RebornCamera`, `CinemaBars`, `HudKeybinds`,
`HudElementBounds`, `KeyboardInputMixin`, `LocalPlayerBodyMixin`, `KeyboardCinemaMixin`,
`HudEditScreen`, `HudEditSidePanel`, `reborn-hud.mixins.json`, `HudElementBoundsTest`) et
sur `minecraft/shinobi/ShinobiAbilities/` (`ShinobiAbilities.java`, `MobilityPathCommand`,
`MobilityListener`, `NarutoRun`, `RunChannelListener`, `config.yml`). Cette session n'a
ouvert aucun de ces fichiers en écriture.

> Note : un dossier `mcjar/` a été temporairement extrait à la racine du dépôt pour lire
> `ParticleTypes.class` du jar client. **Il a été supprimé** ; `git status` le confirme.
