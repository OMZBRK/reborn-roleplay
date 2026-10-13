# KO, blessures, soins et mort RP

> **Fiche système, à jour au 2026-10-12** (ShinobiCore et ShinobiAbilities sur le serveur dev, `reborn-hud`
> 0.4.169). Elle décrit **ce qui est codé**, pas une intention. La réflexion de départ (références GTA RP,
> GMod, Arma…) et les variantes écartées sont dans
> [`archives/conception/PROPOSITION_KO_PAINRP_2026-10-06.md`](../archives/conception/PROPOSITION_KO_PAINRP_2026-10-06.md).
>
> Code : `ShinobiCore/…/ko/` (KO, ATA, zones, corps, mort RP), `ShinobiCore/…/medic/` (soins),
> `mod-hud/…/ko/` (habillage, QTE, scène de mort).

---

## 1. Le principe en une phrase

**Se battre hors d'un terrain d'entraînement coûte quelque chose, et cette conséquence oblige à jouer une
scène** (médic, repos, hôpital) au lieu de repartir au combat. C'est la réponse au « combat d'arène » que
l'équipe veut éviter.

| Où | Ce qui se passe à 0 PV | Conséquence |
|---|---|---|
| **Terrain d'entraînement** (zone staff) | « Défaite » : on se relève aussitôt | **Aucune** : PV remis au maximum, pas de KO, pas de blessure |
| **Partout ailleurs** | KO (à terre → inconscient) | PV perdus **persistants**, blessures, ATA au réveil |

---

## 2. Le cycle complet

```
combat hors zone ──► PV à 0
                      │
                      ▼
              ① À TERRE (45 s) ──[Volonté, si le perso la connaît]──► SECOND SOUFFLE (debout, 5 % PV)
                      │ coup de plus, temps écoulé, échec            │ retombe à 0 PV → KO direct
                      ▼                                                ▼
              ② INCONSCIENT (5 min) ──── soigné sur place ────► ④ ATA allégée
                      │ les 5 min passent
                      ▼
              ③ TOUJOURS INCONSCIENT + « /hopital » proposé ──► réveil à l'hôpital ──► ④ ATA pleine
                                                                                        │ médic ou repos
                                                                                        ▼
                                                                                     rétabli

   Sur un corps à terre ou inconscient, les autres peuvent : ausculter, porter, attraper à la gorge,
   fouiller, demander une mise à mort RP (§6).
```

### ① À terre — 45 s (`ko.a-terre-secondes`)

- Le joueur **rampe** lentement : pas de saut, pas de technique, pas d'attaque.
- Il **chuchote** (portée 4 blocs, `ko.chuchotement-blocs`) et peut faire des `/me`.
- `/aide` : fumée rouge visible et alerte aux médics (`shinobicore.medic`) et au Poste de garde.
- Un coup de plus, ou la fin du compte à rebours, le fait passer **inconscient**.
- `ko.a-terre: false` désactive cette phase (KO direct, comme avant).

### La Volonté (QTE de relève) — **facultative**

- **N'apparaît que si le personnage connaît la technique `volonte`** (`ko.volonte.technique`), ou pour tout
  le monde si `ko.volonte.pour-tous: true`. Décision du 2026-10-10 : la Volonté est une compétence, pas un
  droit.
- ⚠️ **La technique `volonte` n'existe pas encore dans le catalogue** (`abilities.yml`) : aujourd'hui,
  personne ne l'a. À ajouter, ou passer `pour-tous` à `true` pour tester.
- Fonctionnement : à terre, **Espace** lance une suite de 4 à 7 sceaux (子丑寅卯辰巳午). Un cercle se referme sur
  chaque sceau ; il faut frapper la touche affichée (Espace, clic gauche ou clic droit) au moment où il se pose.
- **Difficulté** = relèves déjà faites + blessures graves − Vigueur / 4 (de 0 à 6). Fenêtre de 250 ms au plus
  facile, 120 ms au plus dur.
- **Anti-triche** : le serveur tire la séquence et vérifie chaque appui (bonne touche, dans la fenêtre + 90 ms de
  marge réseau, durée réelle plausible).
- **Réussite** : « Second souffle » — debout à 5 % des PV, en ATA allégée, et la pire blessure s'aggrave d'un cran.
- **Échec** : inconscient.
- **2 relèves maximum** par cycle de blessure ; le compteur ne revient à zéro qu'après une paume de soin tenue
  30 s. Relèves épuisées → retomber = KO direct, sans phase à terre.
- Canal `reborn:volonte` (`avail`, `start`, `result`, `ok`, `fail`, `deny`). Code : `ko/VolonteQte.java`, mod
  `ko/VolonteHud.java`.

### ② Inconscient — 5 min (`ko.inconscient-secondes`)

- Corps immobile, vision assombrie ; le joueur voit, chuchote et fait des `/me`. Commandes autorisées :
  `/me`, `/aide`, `/hopital`.
- **Pendant les 5 minutes, `/hopital` n'est PAS proposé.**
- **Après les 5 minutes, il reste inconscient** et `/hopital` apparaît (« Se laisser emmener à l'hôpital »). Il
  n'y a plus de rapatriement automatique (décision du 2026-10-10).
- Cause chakra (chakra à 0) : épuisement, teinte bleue, réveil automatique à la fin du délai.

### ③ L'hôpital

- `/hopital` : réveil sur le **premier lit libre** de l'hôpital du village du personnage, à **30 % des PV**
  (le plancher, `ko.plancher-pct`), en **ATA pleine**.
- Ordre de recherche : lit libre d'une zone hôpital du village → entrée de la zone → point `/hopital set`
  du village → spawn du monde.
- Lits : meubles Nexo dont l'identifiant contient `lit`, `bed` ou `futon` (`ko.lits-nexo`), ou lit vanilla.
  Un lit reste occupé 15 min tant que son occupant est à côté (`ko.lit-occupe-minutes`). Les lits se
  posent depuis l'Infirmerie du Poste de garde.
- Les frais d'hôpital se règlent **en RP** (aucune mécanique, décision du 2026-10-06).

### ④ L'ATA — « blessé, en attente de soin »

Posée sur le **personnage** à chaque réveil d'un KO par les PV : **pleine** après l'hôpital, **allégée** si
quelqu'un l'a soigné sur place.

| Interdit pendant l'ATA | Autorisé |
|---|---|
| Courir, course de chakra, dash, double saut, saut de chakra | Marcher |
| **Toute technique** (ninjutsu, taïjutsu, kenjutsu…) | **Coups à mains nues (M1)**, divisés par deux |
| Frapper avec une arme ou un projectile | — |

- **La peur (FearRP) a été retirée** le 2026-10-10 (« inutile »). Le NLR reste à la charge du joueur (simple
  rappel).
- **On en sort** : par un médic (paume de soin tenue 30 s), ou par le **repos RP** cumulé — méditer n'importe
  où, ou être assis / allongé dans une zone de repos (restaurant, onsen, auberge, hôpital). 20 min pour une
  ATA pleine, 10 min pour une allégée ; manger dans une zone de repos ajoute 2 min. Le repos n'avance que
  si le joueur est actif (anti-AFK, 120 s).
- Retomber KO pendant une ATA la remet à zéro, en pleine.
- Staff : `/ata voir|lever|appliquer <joueur> [pleine|allegee]`.
- Code : `ko/ata/AtaManager.java` ; blocages côté Abilities via `KoService.isImpaired`
  (`JutsuExecutionManager`, `MobilityListener`, `NarutoRun`).

---

## 3. Les PV persistants et le plancher

- **Hors terrain d'entraînement, les PV ne remontent pas tout seuls** (la saturation permanente du serveur
  régénérait la vie ; c'est coupé). `ko.pv-persistants: false` réactive la régénération.
- Le **repos** rend des PV, mais **jusqu'à 30 % seulement**. Au-delà, il faut un médic. Ainsi, personne ne
  reste bloqué à 5 % s'il n'y a pas de médic connecté, mais revenir à pleine forme demande une scène.
- Les mobs (MythicMobs uniquement, pas de faune vanilla) suivent la même règle.

---

## 4. Terrains d'entraînement et zones RP

- Zones posées par le staff : `/zonerp pos1`, `pos2`, `creer <entrainement|repos> <id>`, `suppr`, `liste`,
  `ici` (stockées dans `training-zones.yml`). Les zones **hôpital** se créent depuis l'Infirmerie.
- **Dans un terrain d'entraînement** : PV et chakra mémorisés à l'entrée, combat libre, **défaite à 0 PV
  = PV remis au maximum** (ou chakra plein si c'est le chakra qui a lâché), sans KO ni blessure
  (`ko.defaite-pct: 1.0`). À la sortie, retour aux valeurs d'entrée : on ne peut ni s'y blesser ni s'y soigner.
- **Hors zone**, chaque combat est journalisé (`aggression-log.txt` : qui a frappé en premier, où). Le Poste de
  garde lève une alerte quand un joueur enchaîne les combats hors zone.

---

## 5. Blessures et soins

### 5.1 Les blessures

- Blessures **par partie du corps**, avec une gravité (de Légère à Urgente). Chaque nouveau KO **aggrave d'un
  cran** les blessures existantes.
- **Seul `/soigner` les retire** (le temps ne les guérit pas) : silhouette du patient, recette de médicaments,
  quelques secondes d'immobilité par palier. Armoires à médicaments posables (`medic-armoirs.yml`).
- `/osculter` (ou « Ausculter » dans le menu d'interaction) : voir l'état d'une personne.

### 5.2 La paume de soin (`/paume`)

- Le médic vise le patient ; un patient **conscient** accepte d'un clic, un patient à terre ou KO est soigné
  directement.
- Soin **tenu dans le temps** : PV par seconde = 1,5 % des PV max × (1 + 0,10 × Contrôle du médic) × bonus de
  rang. Coût en chakra du médic par seconde. Interrompu si le médic bouge de plus de 1,5 bloc, est frappé ou
  n'a plus de chakra.

| Rang médical | Permission | Soigne jusqu'à | Lève l'ATA |
|---|---|---|---|
| 1 | `shinobicore.medic` | 60 % des PV | après 30 s, s'il n'y a pas de blessure Importante ou Urgente |
| 2 | `shinobicore.medic.expert` | 100 % | toute ATA |

- Un corps KO soigné se relève sur place, en ATA allégée.
- Bandes de gaze (clic droit, accroupi pour soi) : +5 % des PV, jamais au-delà de 30 %, une fois toutes les
  10 s par blessé ; ne lève pas l'ATA.
- Réglages : `medic.paume.*`, `medic.bandage.pv-pct`.

### 5.3 Lien entre KO et médical — **léger, volontairement**

Aujourd'hui, les seuls points de contact sont : la paume ou le bandage qui réveillent un KO au-dessus du
plancher ; la paume tenue 30 s qui lève l'ATA et remet les relèves de Volonté à zéro ; l'aggravation des
blessures à chaque KO. L'encyclopédie médicale, les armoires et les traitements par blessure vivent à part.

---

## 6. Agir sur un corps — le menu d'interaction

**Touche R, puis clic sur un corps à terre ou inconscient.** L'ancien menu en coffre (clic droit) a été
**retiré** le 2026-10-11 : un clic droit sur un corps rappelle seulement d'utiliser R.

| Action | Effet |
|---|---|
| **Ausculter (état)** | Voir les blessures et l'état |
| **Attraper à la gorge** | Le corps est tenu **debout** devant soi, **sans limite de durée** ; on avance lentement ; s'accroupir le lâche |
| **Porter** | Le corps passe sur les épaules ; s'accroupir le dépose |
| **Fouiller** | Ouvrir les affaires du corps |
| **Demander une RPK** | Envoie la demande de mise à mort RP au staff (§7) |
| **Achever** | *Seulement après une RPK validée* : tue le personnage |
| **Exécution ▸** | *Seulement après une RPK validée* : liste des exécutions possédées (§8) |

- Le mod sait quels corps sont proches et où en est la RPK grâce au canal `reborn:corps` (mis à jour toutes
  les ½ s). Les actions passent par `/corps <action> <joueur>`, toujours revérifiées par le serveur
  (distance 3,5 blocs, corps bien KO, RPK accordée, exécution possédée).
- Code : `ko/action/CorpsService.java`, mod `interaction/InteractionMenus.forBody`, `ko/CorpsClient.java`.

---

## 7. La mise à mort RP (RPK)

**La victime n'a rien à valider. Le staff tranche, puis c'est le demandeur qui joue la mort.**

1. **Demande** — « Demander une RPK » sur un corps à terre ou KO. Tout le staff en ligne reçoit au tchat :
   `☠ [Staff] RPK : Kazuki Uchiha (Raiden_FR) veut mettre à mort Ren Hyūga (Mika_) · world 640 70 -120
   [Valider] [Refuser] [Y aller]`, et une alerte « mort » apparaît au Poste de garde (Valider la RPK / Refuser /
   Y aller, Modérateur et plus). Sans staff en ligne, la demande attend au Poste de garde.
2. **Validation** — le staff donne une raison (au tchat, ou depuis le Poste de garde). **Le personnage ne
   meurt pas** : la RPK est **accordée** au demandeur, qui est prévenu. Le staff voit « RPK validée … — X doit
   maintenant l'exécuter ».
3. **La dernière action** — le demandeur joue sa scène, puis choisit « Achever » ou une exécution. **C'est
   cette action qui tue.** Le personnage est marqué mort et sauvegardé, `CharacterDeathEvent` est émis (les
   addons libèrent ce qu'il détenait : slots du rang S, cf.
   [`APPRENTISSAGE.md`](./APPRENTISSAGE.md) §6), tout est journalisé, et la scène de mort commence (§9).
- **La demande tombe** si la cible n'est plus KO (relevée, soignée, hôpital) ou se déconnecte ; le staff et le
  demandeur sont prévenus. Le refus est annoncé à tout le staff.
- Journaux : `kill-log.txt` (DEMANDE, VALIDE, REFUSE, EXPIRE, MORT) et journal du Poste de garde.
- Commandes : `/sckillvote approve|refuse|goto <id>` (boutons du tchat). Code :
  `ko/action/KillRequestManager.java`.

---

## 8. Les exécutions

- **Mises à mort animées**, jouées après une RPK validée. Ce sont des **cosmétiques vendables** (« animation
  stylée »).
- Catalogue `executions.yml` (dossier de ShinobiCore, créé au premier démarrage) : pour chaque exécution, un
  nom, l'animation du tueur et celle de la victime (clés d'emotes EmoteCraft), la durée et l'instant où la mort
  tombe. Rechargé par `/execution recharger`.
- Au lancement : la victime est placée debout face au tueur, les deux sont figés, les animations jouent, la
  mort tombe à l'instant prévu.
- Possession **par compte** (`executions-joueurs.yml`) : `/execution donner|retirer <joueur> <id>`, ou la
  permission `shinobicore.execution.<id>`. Une entrée `gratuite: true` appartient à tout le monde.
- ⚠️ **Aucune animation n'existe encore.** Les 3 exemples (Coup de grâce, Lame dans le dos, Étranglement)
  pointent vers des emotes à créer (`execution_coup_de_grace`, `…_victime`…). Sans fichier, la scène se joue
  sans animation. **La vente en boutique n'est pas branchée.**

---

> **Porter un corps (correctif du 13/10)** : au dépôt (accroupi), le corps est posé **au sol devant le porteur**
> (`PorterManager.dropSpot`) ; avant, il restait figé à la hauteur des épaules, en l'air. Même correctif pour la
> prise à la gorge.

## 9. La scène de mort

Le joueur dont le personnage meurt **reste sur place**, allongé, et **ne peut plus rien faire** : ni bouger,
ni parler, ni lancer de commande, ni ouvrir d'inventaire, ni être blessé.

1. Ses yeux battent puis **se ferment** ; écran noir.
2. **10 s après la mort** (`mort-rp.scene-min-secondes`, 60 avant le 13/10) : « **Assister à votre réelle fin ?** » Oui / Non.
3. **Non** → il part : le corps reste au sol et il arrive à la **sélection de personnage**.
4. **Oui** → ses yeux se rouvrent sur une **vue grisée, sans aucun HUD, en bandes de cinéma** ; il regarde la
   suite (glisser la souris tourne la vue autour du corps) et part quand il veut avec **Échap**.
   **Tant qu'il regarde, le corps, c'est lui** : les autres peuvent le **porter, le déposer, le fouiller**
   (menu d'interaction : Constater la mort, Porter, Fouiller). S'il part, le mannequin apparaît là où le corps a
   été laissé.
- En partant (ou en se déconnectant), son **corps reste au sol 15 min** (`mort-rp.corps-minutes`) : un
  mannequin allongé, **habillé de l'apparence Reborn** du personnage (le mod reçoit l'apparence par
  `reborn:corps` et la compose).
- Code : `ko/action/DeathScene.java`, mod `ko/DeathScreen.java`, `ko/CorpsClient.java`,
  `mixin/ClientMannequinSkinMixin.java`.

---

## 10. Habillage visuel et sonore (KO-6)

- Filtre plein écran `ko.fsh` (mod) : **à terre** désaturation + vignette rouge au rythme du cœur ;
  **inconscient** noir et blanc, flou, grain, paupières ; **ATA** pulsation légère ; **cause chakra** teinte bleu
  glacé. Priorité des filtres : KO > Byakugan > Météo.
- Cartes de titre (倒 À terre, 気絶 K.O., 病院 Hôpital, 回復 Rétabli, 敗 Défaite, 志 Second souffle), plaque ATA
  痛 avec 10 crans de repos, sons de cœur et de souffle.
- Le HUD du KO évite le chat et les jauges. Les titres vanilla ne s'affichent que pour un client sans mod.
- Canaux `reborn:ko` (état) et `reborn:ko_event` (cartes). Code : `ko/KoHudSync.java`, mod `ko/KoHud.java`.
- Défaut connu : la carte « K.O. » se superpose à « INCONSCIENT » pendant environ 1 s.

---

## 11. Réglages (`ShinobiCore/config.yml`)

| Clé | Défaut | Rôle |
|---|---|---|
| `ko.pv-persistants` | `true` | Pas de régénération naturelle hors terrain |
| `ko.defaite-pct` | `1.0` | Fraction rendue après une défaite à l'entraînement |
| `ko.journal-agressions.*` | activé, 5 min | Journal des combats hors zone |
| `ko.a-terre`, `ko.a-terre-secondes` | `true`, `45` | Phase à terre |
| `ko.inconscient-secondes` | `300` | Durée avant que `/hopital` soit proposé |
| `ko.volonte.technique`, `ko.volonte.pour-tous` | `volonte`, `false` | Qui a la Volonté |
| `ko.plancher-pct` | `0.30` | PV max rendus par le repos, PV au réveil à l'hôpital |
| `ko.chuchotement-blocs`, `ko.aide-portee` | `4`, `48` | Portées |
| `ko.lits-nexo`, `ko.lit-occupe-minutes` | `[lit, bed, futon]`, `15` | Lits d'hôpital |
| `ko.ata.repos-minutes-pleine` / `-allegee` | `20` / `10` | Repos nécessaire |
| `ko.ata.anti-afk-secondes`, `ko.ata.repos-pv-pct-par-10s` | `120`, `0.01` | Repos |
| `mort-rp.scene-min-secondes`, `mort-rp.corps-minutes` | `10`, `15` | Scène de mort |
| `medic.paume.*`, `medic.bandage.pv-pct` | cf. config | Soins |

> Une clé absente du `config.yml` **du serveur** prend la valeur par défaut du code. Les réglages de
> l'Infirmerie faits en jeu vont dans `ko-reglages.yml` (perdus par `/sc reload` jusqu'au redémarrage).

---

## 12. Reste à faire

| Sujet | État |
|---|---|
| Tester la 0.4.169 à **deux comptes** (RPK, exécution, scène de mort, corps) | à faire |
| Créer la technique `volonte` (ou décider `pour-tous`) | à faire |
| Animations EmoteCraft des exécutions | à créer (staff animation) |
| Vente des exécutions en boutique | non branchée |
| Coma au 3ᵉ KO pendant une ATA (réveil par médic rang 2) | compteur prêt, effet non branché |
| Saignement pendant la phase à terre | non fait |
| `MedicalService` (antidotes, coma) | non fait — chantier Médical (Sprint 3) |
| Animations de chute / reptation / relève | non faites |
| Carte « K.O. » superposée à « INCONSCIENT » | défaut connu |
