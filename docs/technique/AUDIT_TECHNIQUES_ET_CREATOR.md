# Audit du système de techniques & spécification du Technique Creator

> Audit réalisé le **2026-09-08** sur `chore/portabilite-multi-postes` (621197f).
> Périmètre : `minecraft/shinobi/` (6 plugins), `minecraft/server-config/magicspells/`,
> le pont fichiers `apps/api/src/files/` + `PanelBridge`, et le client 26.2 déobfusqué
> pour l'inventaire des particules.
>
> Objet : (1) ce que le système de techniques fait réellement aujourd'hui, (2) ce qui est
> optimisable, (3) la spécification de l'outil de création de techniques par graphe de
> nœuds dont les maquettes `creator.png` / `creator2.png` / `creator3.png` fixent la
> direction.

---

## 1. État réel — les chiffres

| Mesure | Valeur | Source |
|---|---:|---|
| Abilities déclarées | **215** | `ShinobiAbilities/src/main/resources/abilities.yml` |
| dont façonnées à la main (effet Java dédié) | 11 | `JutsuEffectRegistry.registerHandCrafted()` |
| dont entrées d'essai génériques (34 catégories × 6 rangs) | 204 | en-tête du même fichier |
| Catégories distinctes | 35 | `category:` uniques |
| Taille du YAML | 87 Ko, un seul fichier | — |
| Abilities utilisant MagicSpells | **0** | aucun `magicspell:` ni `commands:` |
| Effets Java enregistrés | 11 façonnés + ~24 génériques + 1 fallback | `JutsuEffectRegistry` |
| Types de particules 26.2 disponibles | **125** | `net.minecraft.core.particles.ParticleTypes` (client 26.2) |
| Types réellement utilisés | **27** | `grep Particle.<CONST>` sur les 309 `.java` |

### Le pont MagicSpells est complet — et mort

Toute la plomberie existe :

- `JutsuMeta.commands` + `runAsPlayer` (`ShinobiCore/technique/JutsuMeta.java`)
- `JutsuExecutionManager.dispatchCommands` avec 7 placeholders (`%player%`, `%uuid%`,
  `%character%`, `%world%`, `%x/y/z%`)
- `jutsu.magicspells.command-template: "cast forcecast %player% %spell%"` dans
  `ShinobiAbilities/config.yml`
- `MagicSpellsHook` — bridge par réflexion pure, aucune dépendance compile-time, lit
  `MagicSpells.spells()` avec fallback `getSpells()`
- `/sa importspells [catégorie] [rang]` — génère un stub `abilities.yml` pour chaque sort
  MS chargé, en append-only, puis recharge le registre
- `plugins/MagicSpells` est un scope d'écriture du grade `DEVELOPPEUR` dans
  `apps/api/src/files/files.service.ts`, avec `ms reload` en cible de reload
- `PanelBridge` sait déclencher `ms reload` sur le serveur live sans RCON ni port entrant

…et **aucune des 215 abilities ne s'en sert**. `minecraft/server-config/magicspells/spells-emotes.yml`
(51 lignes) est le seul exemple existant, et il est explicitement marqué « référence, pas
déployé automatiquement ».

**Conclusion 1 : le chaînon manquant n'est pas technique. C'est du contenu, et un outil
pour le produire.**

---

## 2. Les cinq trous structurels

### 2.1 Aucune notion de prérequis — c'est le vrai sujet

`Ability` n'a ni `requires`, ni `prerequisites`, ni `unlocked-by`. `knownAbilities` est un
`Set<String>` plat. Le gate de cast (`JutsuExecutionManager.gateOrWarn`) ne vérifie que :
personnage actif, pas KO, `knowsAbility(id)`, cooldown, chakra.

Donc il est **impossible aujourd'hui d'exprimer** :

> « Rasengan exige : connaître Contrôle du Chakra à maîtrise ≥ 60, avoir validé l'examen
> Chūnin, être du village de Konoha, et avoir été enseigné par un mentor de rang Jōnin. »

C'est exactement ce que le nœud `Gate` / `Requirements` de la maquette veut résoudre.
→ **L'apport principal du créateur n'est pas l'interface, c'est le modèle de données.**
Sans `requires:`, l'éditeur graphique n'aurait rien à compiler.

### 2.2 Aucune variante ni embranchement

Une ability = un id = un effet. Le commentaire de `config.yml` promet pourtant le modèle
« 0-90 efficacité / 90 %+ version améliorée » — les consommateurs de ce modèle n'ont
jamais atterri (`mastery-per-cast` accumule, personne ne lit le seuil). Pas de variation
par affinité, par clan, par état (KO, transformation `ShinobiTail`).

### 2.3 Le fichier unique ne passe pas l'échelle

87 Ko / 215 entrées dans un seul YAML. Un `id` en double → « la première gagne », warning
console uniquement. **Zéro validation de schéma** : une `category` non routée vers un
`JutsuItemType`, un `effect:` inexistant, un `mudra` inconnu — tout dégrade silencieusement
vers `generic_<leaf>` puis `generic_fallback`. Un designer qui ajoute 40 techniques cassera
quelque chose sans le voir.

### 2.4 Aucune boucle d'itération

Cycle actuel pour tester une modification : éditer le YAML → SFTP ou panel → `/sa reload`
→ se connecter → apprendre la technique → la lier à un JutsuItem → la lancer. Pas d'aperçu,
pas de test à sec, pas de « joue-moi juste l'effet ».

### 2.5 Le plafond de créativité d'un non-dev est très bas

Les 11 effets façonnés sont du Java compilé. Un designer ne peut créer aucun effet visuel :
il recycle un des ~24 génériques, ou il passe par MagicSpells — que personne n'utilise et
qui n'est documenté que par un fichier d'exemple non déployé.

---

## 3. Particules 26.2 — 98 types inexploités

Inventaire extrait du client déobfusqué (`versions/26.2/26.2.jar` →
`net/minecraft/core/particles/ParticleTypes.class`) : **125 types**, dont **27 utilisés**.

Reproduire l'inventaire :

```pwsh
# depuis n'importe quel dossier de travail
unzip -o -q "$env:APPDATA\RebornRoleplay\versions\26.2\26.2.jar" `
  "net/minecraft/core/particles/ParticleTypes.class" -d .
# puis extraire les chaînes lowercase_snake du constant pool
```

### Les familles qui manquent, mappées aux disciplines

| Particules inexploitées | Usage Reborn évident |
|---|---|
| `geyser`, `geyser_base`, `geyser_plume`, `geyser_poof` | **Suiton** (jets sous pression), **Futton** (vapeur), éruption **Doton** |
| `sulfur_bubbles`, `sulfur_cube_goo`, `noxious_gas`, `noxious_gas_cloud` | poisons, **Kugutsu** (Sasori), Futton corrosif |
| `copper_fire_flame` | **Katon** à teinte distincte du `flame` vanilla — signature de clan |
| `dust_color_transition` | **la primitive la plus utile qui manque** : dégradé A→B, une palette par affinité (Katon rouge→orange, Raiton bleu→blanc) avec un seul type |
| `item`, `item_slime`, `item_snowball`, `item_cobweb`, `block`, `block_marker`, `falling_dust` | particules **paramétrées par un item/bloc** — un designer peut faire pleuvoir des kunai Nexo. Aucune utilisée |
| `block_crumble` | **Doton** : murs qui s'effritent, impacts |
| `dust_pillar`, `dust_plume` | colonnes de poussière du `FloorShockwave` (aujourd'hui : un anneau de `LARGE_SMOKE`) |
| `shriek`, `sonic_boom` | cris de bijū, **ShinobiTail** |
| `sculk_soul`, `sculk_charge`, `sculk_charge_pop` | Edo Tensei, **juinjutsu**, **fūinjutsu** |
| `vault_connection`, `trail` | rendu du lien de `SealingLink` — qui n'a aucun visuel de liaison aujourd'hui |
| `ominous_spawning`, `raid_omen`, `trial_omen`, `trial_spawner_detection` | **Kuchiyose**, invocations |
| `firefly` | chakra ambiant, **Byakugan** / détection sensorielle |
| `tinted_leaves`, `pale_oak_leaves`, `cherry_leaves`, `falling_nectar`, `spore_blossom_air` | **Mokuton** |
| `entity_effect`, `instant_effect`, `effect` | nuages colorés paramétrables (RGB) |
| `glow_squid_ink`, `squid_ink`, `dragon_breath`, `elder_guardian`, `nautilus` | **Genjutsu**, dōjutsu |

Liste complète des 98 : voir l'annexe de l'artefact partagé, ou régénérer avec la commande
ci-dessus.

> ⚠️ Les constantes Bukkit sont l'uppercase des clés de registre, mais rien ne garantit que
> Purpur 26.2 expose 1:1 les tout derniers ajouts. Vérification en 10 secondes côté serveur :
> `Arrays.stream(Particle.values()).map(Enum::name)` dans un `/sa` de diagnostic.

---

## 4. Optimisations mesurables

### 4.1 `Effects.forwardCone` envoie un paquet réseau par particule — priorité 1

`ShinobiCore/util/Effects.java:49` boucle `count` fois sur
`l.getWorld().spawnParticle(particle, l, 1, 0,0,0,0)`. Chaque appel produit un
`ClientboundLevelParticlesPacket` diffusé à **tous** les joueurs dans la view distance.

Katon Gōkakyū seul : **156 paquets par cast** (140 `FLAME` + 16 `LAVA`), multipliés par
chaque spectateur. En combat à 8 joueurs c'est le premier poste de bande passante du
serveur.

Correctifs, du moins cher au plus :

1. **Regrouper** — un `spawnParticle(type, loc, count, offX, offY, offZ, extra)` par tranche
   du cône au lieu d'un appel par particule. 156 → ~12 paquets, rendu visuellement identique
   (l'offset fait le jitter que fait déjà `Math.random()`).
2. **LOD par joueur** — `Player#spawnParticle` avec atténuation par distance : au-delà de
   ~24 blocs, count / 2 ; au-delà de 48, rien. Aujourd'hui aucun LOD.
3. `ThreadLocalRandom.current()` au lieu de `Math.random()` — 3 appels par particule, soit
   **480 par cast** de Gōkakyū.

### 4.2 `damageCone` scanne une boîte cubique

`Effects.java:81` — `getNearbyEntities(origin, length, length, length)` avec `length = 11`
donne une boîte de 22³ ≈ 10 648 blocs³ à filtrer, alors que le cône réel en occupe ~1/6.
`World#getNearbyLivingEntities(loc, x, y, z, predicate)` filtre côté serveur avant
d'allouer la collection.

### 4.3 `normalize()` sur un vecteur potentiellement nul

`damageCone` fait `toEntity.normalize().dot(dir)` après avoir mesuré `dist`. Si une entité
est exactement à la position de l'œil (monture, entité montée), `normalize()` produit NaN,
`dot < 0.6` est faux, et l'entité est touchée. Rare mais réel — garder `if (dist < 1e-4) continue;`.

### 4.4 Les 204 entrées d'essai sont chargées en production

Elles saturent le `CatalogueGui`, faussent `/sa importspells` (qui teste
`registry.byId(id) != null`), et rendent tout diff de `abilities.yml` illisible. Elles
devraient vivre dans un `abilities-debug.yml` chargé conditionnellement — `ShinobiCore`
a déjà un `mode: dev` en tête de `config.yml`.

### 4.5 Pas d'index par catégorie

`AbilityRegistry` n'indexe que `byId` (`LinkedHashMap`). Le `CatalogueGui` refiltre en O(n)
à chaque ouverture. Indolore à 215 entrées ; ça ne l'est plus à l'échelle que vise l'outil.

### 4.6 Les cooldowns ne survivent pas à un redémarrage

`CooldownTracker` est un `ConcurrentHashMap` en mémoire, sans persistance. Un cooldown
HIDEN de 30 minutes est effacé par un `restart` ou un reload de plugin. Acceptable
aujourd'hui, exploitable dès que les cooldowns longs deviennent un levier d'équilibrage.

### 4.7 `sa reload` / `sc reload` manquent dans le pont panel

`PanelBridge.ALLOWED_PREFIXES` autorise `nexo reload`, `ms reload`, `mm reload`,
`meg reload`, `creator reload`, `playemote reload` — **mais pas** `sa reload` ni
`sc reload`. Le panel peut donc recharger MagicSpells mais pas le registre de techniques.
Correctif : deux lignes. C'est le prérequis de tout déploiement automatisé depuis l'éditeur.

---

## 5. Le Technique Creator — spécification

### 5.1 Le principe qui rend le projet réaliste

**Le graphe n'est pas le runtime.** Le graphe est une source ; il *compile* vers des
artefacts que le serveur sait déjà lire. Aucun interpréteur de graphe côté Paper, aucun
risque de perf en jeu, aucune réécriture des 6 plugins.

Trois cibles de compilation :

| Cible | Rôle |
|---|---|
| `abilities.yml` **schéma v2** (avec `requires:`) | lu par `AbilityRegistry` — identité, coût, cooldown, gate, effet |
| `spells-<pack>.yml` MagicSpells | les compositions que MS fait mieux : chaînage, projectiles, zones, buffs |
| `graph.json` | **la source de vérité**. Le YAML devient un artefact de build, plus un fichier édité à la main |

### 5.2 Le transport existe déjà

```
Éditeur (apps/admin)
  → POST /v1/abilities/graphs        [à créer, ~1 contrôleur]
  → compilation (packages/ability-compiler)   [à créer]
  → FilesService.write (SFTP)        [EXISTE]
  → PanelBridge → "sa reload" + "ms reload"   [EXISTE, + 2 lignes de whitelist]
```

Les rôles `DEVELOPPEUR` / `ADMIN` / `OWNER` ont déjà les scopes d'écriture nécessaires,
l'audit des écritures fichiers est déjà branché, et `FilesService` fait déjà une sauvegarde
`.bak` avant chaque `put`.

### 5.3 Catalogue de nœuds

Repris de la maquette, complété par ce que le runtime sait réellement faire.

**ENTRY / PATH**

| Nœud | Contenu |
|---|---|
| `Learn Path` *(Event)* | méthode d'apprentissage : Mentor / Parchemin (étagère) / Examen / Squad teach / Auto |
| `Path Root` *(Context)* | id de l'ability, nom affiché, état partagé de la branche |

**BRANCH SPINE**

| Nœud | Mappe vers |
|---|---|
| `Trigger` *(Input)* | `ExecutionMethod` : LEFT_CLICK, RIGHT_CLICK, HOLD_SNEAK, CLICK_SEQUENCE (+ DOUBLE_TAP côté mobilité) |
| `Gate` *(Pre-cast)* | **le nœud à créer côté runtime** : `knows(ability)`, `mastery(id) ≥ n`, `rank ≥ X`, `skill(id) ≥ n`, `exam(id) validé`, `clan`, `affinité`, `événement joué`, `!KO`, item en main |
| `Cost` *(Resource)* | Chakra (`ShinobiCore`), **Endurance** (`ShinobiCombat.StaminaManager` — déjà utilisée par `KenjutsuEnduranceGate`), Rage (`ShinobiTail`) |
| `Cooldown` *(Gate)* | ms + option « partagé avec le groupe *G* » (n'existe pas encore) |
| `Mastery` *(Progression)* | gain par cast, seuil de bascule vers la variante |

**EFFECTS** — la maquette en propose 4 ; il en manque 6 sans lesquelles un designer ne peut
rien faire seul :

| Nœud | État |
|---|---|
| `Effect · Mobility` | ✅ primitives existantes (dash, wall-jump, shockwave, climb…) |
| `Effect · MagicSpell` | ✅ liste live fournie par `MagicSpellsHook.listSpells()` |
| `Effect · MythicMob` | ✅ via `dispatchCommands` |
| `Effect · Custom` | ✅ clé du `JutsuEffectRegistry` |
| **`Effect · Particles`** | ❌ à créer — type (les 125), forme (cône / anneau / spirale / ligne / sphère), count, portée, **LOD** |
| **`Effect · Sound`** | ❌ à créer — son, volume, pitch, portée |
| **`Effect · Damage`** | ❌ à créer — cône / anneau / cible, dégâts, knockback |
| **`Effect · Status`** | ❌ à créer — potion, stun, ignite, glow |
| **`Effect · Block`** | ❌ à créer — mur temporaire (`placeTemporaryWall` existe déjà) |
| **`Sequence` / `Delay`** | ❌ à créer — **le timing, c'est 80 % du ressenti d'une technique.** Sans ça tout part sur la même frame |

**ANNOTATION** : `Sticky note`, groupe `Comment` (déjà dans la maquette, à garder).

### 5.4 Gestion des dépendances — ce que le graphe débloque

Schéma cible :

```yaml
- id: rasengan
  name: "Rasengan"
  category: "ninjutsu/pur"
  rank: A
  requires:
    - { type: ability,  id: controle_chakra }
    - { type: mastery,  id: controle_chakra, min: 60 }
    - { type: exam,     id: chunin }
    - { type: skill,    id: controle_chakra, min: 3 }
    - { type: mentor,   rank: JONIN }
  variants:
    - when: { type: mastery, id: rasengan, min: 90 }
      id: odama_rasengan
```

Le compilateur **valide le graphe orienté acyclique** : cycle interdit, référence à une
ability inexistante = **erreur de compilation**, plus un fallback silencieux. Et l'éditeur
peut afficher l'arbre inverse — « qui dépend de Shunshin ? » — question impossible à poser
aujourd'hui.

### 5.5 Livraison en 4 phases

| Phase | Contenu | Charge | Livrable utile seul |
|---|---|---|---|
| **0 — Débloquer le runtime** | `requires:` dans le schéma `Ability` + le gate ; `sa reload` / `sc reload` dans `PanelBridge` ; sortir les 204 stubs dans `abilities-debug.yml` ; batching + LOD des particules | 2-3 j | ✅ le YAML seul devient bien plus expressif, sans aucun outil |
| **1 — Le compilateur** | `packages/ability-compiler` (TS) : schéma Zod du graphe, compile → `abilities.yml` + `spells-*.yml`, valide le DAG, CLI `pnpm ability build` | ~1 sem | ✅ testable et versionnable sans interface |
| **2 — L'éditeur** | `apps/admin/app/(panel)/abilities/` : le browser de `creator.png`/`creator3.png`, le graphe de `creator2.png` (React Flow), Save → API → compile → SFTP → reload | 2-3 sem | ✅ un designer crée une technique sans toucher un fichier |
| **3 — La boucle d'essai** | `/sa preview <id>` (joue l'effet sans coût, cooldown ni apprentissage) + bouton « Tester sur moi » dans l'éditeur via `PanelBridge` | 1-2 sem | ✅ **c'est ce qui transforme l'outil en atelier** |

Chaque phase a de la valeur isolément : si le projet s'arrête après la 0, le système de
techniques est déjà nettement meilleur.

### 5.6 Ce qu'il faut demander aux contributeurs intéressés

Le catalogue de nœuds doit sortir du contenu réel, pas l'inverse. Trois demandes :

1. **10 techniques réelles** qu'ils veulent créer, écrites en français, avec leurs
   conditions d'accès. C'est ce qui détermine quels nœuds `Gate` et `Effect` existent.
2. **Les conditions d'accès qu'ils utilisent déjà en RP** — examens, mentors, clans,
   événements, quêtes — avec leur vrai vocabulaire.
3. **Ce qui les bloque aujourd'hui** dans la création d'une technique.

---

## 6. Checklist actionnable

Par ordre de rapport valeur / effort.

- [ ] Ajouter `"sa reload"` et `"sc reload"` à `PanelBridge.ALLOWED_PREFIXES`
      (`ShinobiCore/panel/PanelBridge.java`) — 2 lignes, débloque tout le reste
- [ ] Grouper les appels `spawnParticle` dans `Effects.forwardCone` / `spawnRing`
      (156 → ~12 paquets par cast) et passer à `ThreadLocalRandom`
- [ ] Ajouter un LOD par distance sur les particules (`Player#spawnParticle`)
- [ ] `if (dist < 1e-4) continue;` dans `Effects.damageCone` avant `normalize()`
- [ ] Déplacer les 204 entrées d'essai dans `abilities-debug.yml`, chargé si `mode: dev`
- [ ] `getNearbyLivingEntities(..., predicate)` dans `damageCone` / `damageRing`
- [ ] Écrire **une** technique de bout en bout via MagicSpells pour valider le pont
      (`magicspell:` dans `abilities.yml` + le sort dans `plugins/MagicSpells/`) — le pont
      n'a jamais tourné en conditions réelles
- [ ] Vérifier côté serveur que `Particle.GEYSER` & co. existent dans Purpur 26.2
      (`Arrays.stream(Particle.values())`)
- [ ] Ajouter `requires:` au schéma `Ability` + au gate `gateOrWarn`
- [ ] Persister `CooldownTracker` (au minimum les cooldowns > 5 min)

---

## 7. MagicSpells — analyse, pertinence, et les moteurs concurrents

### 7.1 Le fait qui change tout : MagicSpells n'est installé nulle part

L'inventaire SFTP vérifié le **2026-08-05** (`MIGRATION_26.1.md §0 — État des lieux
constaté`) liste les plugins réellement présents :

| Serveur | Plugins présents |
|---|---|
| **build** (7002) | Axiom, Chunky, EssentialsX, FAWE, HeadDatabase, Multiverse, **MythicMobs**, ShinobiCore/Abilities, ProtocolLib\*, spark |
| **dev** (7012) | ShinobiCore/Abilities/Learning/Sense/Tail, spark, bStats |

**Ni MagicSpells, ni ModelEngine, ni Nexo** n'y figurent. **MythicMobs, si.**

Donc la raison pour laquelle « le pont MagicSpells n'a jamais traversé » est plus radicale
que « personne n'a écrit de sort » : **il n'y a probablement pas de MagicSpells à l'autre
bout.** Ça explique cohéremment `MagicSpellsHook` en réflexion pure, le `softdepend:
MagicSpells` du `plugin.yml`, et le message d'erreur prévu par `/sa importspells`
(« MagicSpells est introuvable »).

> **Vérification préalable à toute décision** : `/plugins` sur prod, dev et build. Cinq
> secondes, et ça tranche tout le reste de cette section. L'inventaire ci-dessus date d'un
> mois et couvrait la migration 26.1 — il peut être partiel (Nexo est très fourni dans
> `server-config/` alors qu'il n'y apparaît pas non plus).

### 7.2 Ce que MagicSpells est réellement

Pas une bibliothèque d'effets : un **moteur de jeu complet et concurrent**. Il apporte son
propre mana avec régénération, ses propres cooldowns (par sort, globaux, partagés), son
propre spellbook / apprentissage / permissions par sort, ses propres variables
persistantes, son propre système de `modifiers:` (conditions sur variable, mana,
permission, spellcount, niveau, monde, heure…), ~200 `spell-class`, et une couche
`effects:` très riche.

Reborn possède **déjà** : chakra avec overdraw et dette, `CooldownTracker`,
`knownAbilities`, maîtrise 0-100, compétences, rangs, personnages multiples.
**Six des sept fonctions de MagicSpells sont des doublons de ShinobiCore.**

### 7.3 L'avis : MagicSpells n'est pas l'interface de création, c'est une cible de compilation

La règle, en une phrase : **ShinobiCore décide, MagicSpells dessine.**

Ce qui doit être interdit dans tout sort MS généré ou écrit à la main :

- pas de `mana:` — le chakra vit dans ShinobiCore
- pas de `cooldown:` (hors debounce anti-spam < 200 ms) — `CooldownTracker` fait autorité
- pas de `modifiers:` de gating — c'est le rôle du nœud `Gate`
- pas de `cast-item:` ni `cast-with-left-click:` sur un sort de technique — l'entrée, c'est
  le JutsuItem de ShinobiCore
- MS ne reçoit que `cast forcecast <player> <spell>`, c'est-à-dire : « joue ce VFX et cette
  hitbox »

Sans cette discipline : deux sources de vérité par ressource, et des joueurs qui ont du
chakra mais pas de mana.

**Corollaire** : le nœud `Effect · MagicSpell` de la maquette est **au bon endroit** — une
feuille, en aval de `Gate` / `Cost` / `Cooldown`. La topologie du graphe est juste.

### 7.4 Manquements vis-à-vis de la 26.2

**a) Compatibilité — inconnue et bloquante.** Rien dans le dépôt ne fixe une version de
MagicSpells. 26.x est le plus gros saut d'API depuis longtemps. Vérifier qu'un build MS
pour Paper/Purpur 26.2 existe **avant** de miser dessus.

**b) Les nouvelles particules à données.** Les particules « simples » (`geyser`, `firefly`,
`copper_fire_flame`, `noxious_gas`, `sulfur_bubbles`, `shriek`) passent par un
`Particle.valueOf(nom)` — un MS à jour les prend sans rien changer. Mais les nouvelles
particules **paramétrées** échouent si MS ne connaît pas leur type d'options :

| Particule | Options requises |
|---|---|
| `dust_color_transition` | `DustTransition` — couleur départ → arrivée + taille |
| `block`, `block_marker`, `falling_dust`, `block_crumble` | `BlockData` |
| `item`, `item_cobweb` | `ItemStack` |
| `trail` | cible + couleur + durée |

Ce sont précisément les plus utiles pour Reborn (palette par affinité, kunai Nexo en
particules). **C'est le vrai test de compatibilité** — pas « le plugin démarre ».

**c) `itemdisplay` + composant `item_model`.** Le pipeline documenté dans
`NEXO_STAFF_GUIDE.md §4` (afficher un item Nexo animé à l'impact) exige un MS capable de
poser le composant `item_model` (1.21.4+). C'est ce qui fait vivre `reborn_effets.yml` —
dont les deux entrées (`impact_kenjtusu`, `slashkenjutsu1`) sont aujourd'hui inutilisées.
*(Au passage : `impact_kenjtusu` porte une faute de frappe figée dans l'id Nexo, le modèle
et la texture. À corriger tant que rien n'en dépend.)*

**d) `ms reload` est un re-parse complet.** Sur un gros pack de sorts c'est un hitch
visible. Pour une boucle d'itération de designer, garder un fichier généré séparé et petit.

### 7.5 Optimisation — c'est l'argument le plus fort en faveur de MagicSpells

Sur le point précis où `Effects.java` est faible, MS est nettement meilleur :

- **`visible-range` par effet** = le LOD par distance que `forwardCone` n'a pas
- `position: line` / `trail` interpole côté MS au lieu de N `spawnParticle`
- EffectLib fournit cône, hélice, sphère, anneau, atome, tornade, ligne animée — là où
  `Effects.java` fait 166 lignes et deux formes
- `MultiSpell` avec délais au tick = le nœud `Sequence` / `Delay`, gratuit

Les pièges perf côté MS, à **interdire dans le compilateur** :

- `PassiveSpell` sur trigger `tick` — listener global, coût constant même hors combat
- `iterations` × `period` mal réglés (`period: 1` sur 200 itérations = 200 ticks de
  particules)
- effets `position: trail` sur projectile longue portée sans `visible-range`

### 7.6 MythicMobs et ModelEngine : cumulables, et probablement prioritaires

Oui, cumulables — et **pas concurrents** : chacun couvre une couche différente. Surtout,
**MythicMobs est déjà installé et MagicSpells ne l'est pas.**

| Couche | Outil | Statut serveur (2026-08-05) |
|---|---|---|
| Autorité : ressources, gating, progression, persistance | **ShinobiCore** | ✅ installé |
| Mechanics scriptées, entités, invocations (kuchiyose), boss, projectiles | **MythicMobs** | ✅ **installé (build)** |
| Modèles 3D animés (clones, bijū, invocations, armures) | **ModelEngine** | ❌ absent de l'inventaire |
| Composition VFX, géométrie, chaînage | **MagicSpells** | ❌ absent de l'inventaire |
| Items, modèles, textures animées 2D | **Nexo** | ❌ absent de l'inventaire (mais `server-config/nexo` est très fourni → à reconfirmer) |

**Le point clé pour l'éditeur : les quatre passent par le même tuyau.**
`JutsuExecutionManager.dispatchCommands` exécute une commande console. Donc
`Effect · MagicSpell`, `Effect · MythicMob` et `Effect · RawCommand` sont **le même nœud
avec un template différent** :

```
MagicSpells   cast forcecast %player% %spell%
MythicMobs    mm skills cast %skill% %player%     (syntaxe à confirmer sur la version installée)
Brut          n'importe quelle commande console
```

→ Une généralisation d'une ligne dans `JutsuMeta` : remplacer le raccourci `magicspell:`
par `external: { provider: MAGICSPELLS | MYTHICMOBS | RAW, ref: <nom> }`, avec un template
par provider en config. Le nœud d'éditeur devient `Effect · External` + menu déroulant.

**ModelEngine est un cas à part** : il ne s'appelle pas par commande, il est piloté par
MythicMobs (mechanic `model`) ou par API. Dans le graphe ce n'est donc **pas un nœud
d'effet**, mais une **propriété d'un effet MythicMobs**. Le distinguer évite un nœud mort
dans le catalogue.

### 7.7 Ce que ça change pour le calendrier

`ROADMAP_BETA_2026.md` demande **57 techniques entre le 9 octobre et le 3 décembre 2026** :
12 taïjutsu (S1), 12 kenjutsu (S2), 15 ninjutsu (S3 + sprint 3), 18 sorts claniques.
À la cadence actuelle — **11 techniques façonnées en Java** — c'est hors d'atteinte.

**Le créateur n'est donc pas un confort : c'est ce qui rend la roadmap atteignable.** Et le
moteur d'effet doit être tranché **avant le sprint du 9 octobre**, sinon les 12 sorts
taïjutsu partent en Java et tout le reste suit le même chemin.

### 7.8 Recommandation, dans l'ordre

1. **`/plugins` sur prod, dev et build.** Cinq secondes. Ça tranche tout.
2. **Si MagicSpells n'a pas de build 26.2 : ne pas l'installer.** MythicMobs couvre
   l'essentiel du besoin VFX + mechanics (`particleline`, `particlesphere`,
   `particleorbital`, `particletornado`, `delay`, `projectile`, `damage`, `potion`,
   `summon`), il est déjà là, et il sert de toute façon pour les invocations et les boss.
   Une dépendance de moins. *(Vérifier quelles mechanics sont premium sur la version
   installée.)*
3. **Si MagicSpells a un build 26.2 :** l'installer **uniquement comme renderer**, avec la
   discipline du §7.3, et faire le test des particules paramétrées (`dust_color_transition`,
   `block`, `item`) **avant** de bâtir dessus.
4. **Dans les deux cas** : le compilateur du graphe émet un fichier généré
   (`spells-reborn-generated.yml` côté MS, ou `Reborn_generated.yml` côté MythicMobs). **Le
   designer n'écrit jamais de YAML de sort** — il branche des nœuds `Particles` / `Sound` /
   `Damage` / `Delay`, et le compilateur choisit la cible.

Ce dernier point est le vrai gain stratégique : **il rend le choix du moteur réversible.**
Changer de moteur revient à changer le backend du compilateur, pas à réécrire les 57
techniques.

---

## 8. Références

- Maquettes : `reborn-design-prep/reference-screen/creator.png`, `creator2.png`, `creator3.png`
- Modèle de données actuel : `ShinobiCore/technique/{Ability,JutsuMeta,AbilityRegistry}.java`
- Exécution : `ShinobiAbilities/jutsu/JutsuExecutionManager.java`
- Effets : `ShinobiCore/util/Effects.java`, `ShinobiAbilities/jutsu/JutsuEffectRegistry.java`
- Pont MagicSpells : `ShinobiAbilities/util/MagicSpellsHook.java`, `command/SaCommand.java`
  (`importspells`)
- Transport fichiers : `apps/api/src/files/files.service.ts`, `ShinobiCore/panel/PanelBridge.java`
- Exemple MS : `minecraft/server-config/magicspells/spells-emotes.yml`
