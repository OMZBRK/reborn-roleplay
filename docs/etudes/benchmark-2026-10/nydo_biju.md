# NydoMod 5.5 : le système de bijū, et ce que Reborn peut en tirer

> Étude des concepts uniquement. NydoMod est « All Rights Reserved » : on ne reprend ni code ni assets.
> Sources : `net/nydomod/biju/**`, `client/biju/**`, `combat/kurama/**`, `entity/EntitySealing*.java`, `res/assets/nydomod/{lang,models/entity/biju,vfx/nydobiju}`.
> Côté Reborn : `minecraft/shinobi/ShinobiTail` (survol complet), `ShinobiAbilities/.../fuinjutsu/SealingLink.java`, `docs/technique/PLUGINS_REFERENCE.md`. `CHANTIERS.md` et `DECISIONS.md` ne contiennent aucune entrée sur les bijū.

## Résumé

1. Nydo réécrit les bijū autour d'un **« Bijū Core » serveur autoritaire**. La logique pure (records immuables `domain/*Rules`) est séparée des services (`*Service`), avec 13 paquets réseau dédiés (`BijuCoreNetwork`).
2. Seul **Kurama** est vraiment jouable en jinchūriki. Il est coupé en deux moitiés **Yin / Yang** (`KuramaHalf`), soit deux hôtes possibles. Les 8 autres bijū n'ont qu'une personnalité et 5 répliques génériques chacun.
3. Le **lien** est une jauge de confiance de −1000 à 1000, avec en plus fatigue, tension, mémoires et pactes. Elle passe par 6 paliers (Méfiance → Loyauté). Elle monte quand on **tient ses engagements** et chute quand on **force** le bijū.
4. Les **conversations** ne sont pas un arbre de dialogue. Ce sont des actions (parler, demander du chakra, forcer, réparer, poser une question, conclure un pacte…). La réplique de Kurama est choisie par l'état du lien : 207 répliques, 4 variantes chacune.
5. Le **scellement** est un rituel physique : bijū à 10 % de PV ou moins, entravé par des chaînes, table de scellement avec 1 réceptacle et 2 à 4 sceaux, 60 s. Il existe une variante en auto-scellement. L'**extraction** se fait sur un hôte entravé, en 4 à 18 s, et libère Kurama avec un tempérament tiré au sort.
6. La **possession** se déclenche dans quatre cas : PV à 25 % ou moins, coup mortel, surcharge après avoir forcé, ou contrôle raté en activant un manteau. L'IA serveur pilote alors le corps environ 2 min. Côté joueur : écran noir, paupières, chute de caméra, battement de cœur.
7. Les **manteaux 1 à 3 queues** sont un calque de rendu procédural : coque de chakra, queues, oreilles, veines. Ils donnent vitesse, dégâts, régénération de chakra et soins, mais consomment la réserve du bijū et fatiguent le lien.
8. Le **manteau 3 queues** ajoute des « bras étendus » : frappes à 5,8 blocs, prise, projection au sol, puis lancer.
9. La **forme 4 queues** est un véhicule invisible que le joueur chevauche. Son rendu natif repose sur un rig de 78 os, avec des queues physiques à ressorts, une caméra dédiée, de la prédiction client, des griffes et une **Bijūdama**.
10. Reborn a déjà **ShinobiTail**, qui tient le versant « RP piloté par MJ » : relation à 4 jauges, rage, monde intérieur, Union. Il lui manque tout le rendu client, le lien jouable au quotidien, le rituel de scellement / extraction et la possession pilotée par IA.

---

## 1. Le Bijū Core (architecture)

**Ce que voit le joueur.** Rien directement. C'est le socle de tout le reste.

**Comment ça marche.**
- `biju/core/state/BijuWorldData` : `SavedData` serveur (`nydomod_biju_core_v1`, schéma 7). Il contient le propriétaire de chaque moitié de Kurama, la **réserve de chakra du bijū**, les relations (`BondRecord`, 256 au maximum) et l'état du sceau.
- La réserve est un **budget partagé** pour manteaux, prêts et attaques. Elle va de 10 000 à 50 000 selon l'XP (`BondRules.reserveCapacity = 10000 + 2·min(xp, 20000)`) et se régénère de 8 points par seconde (`RESERVE_PER_SECOND`).
- Les règles sont des fonctions pures sur des records immuables (`BondRules`, `BondGrowthRules`, `BijuControlRules`, `BijuMantleRules`, `SealRules`…). Elles renvoient `Result(record, reason, xp)`, et la `reason` sert ensuite de clé de dialogue ou de message.
- Réseau (`BijuCoreNetwork`, 13 paquets) :
  - C→S : `BijuBondRequest`, `BijuCompanionRequest`, `InnerVisitRequest`, `BijuBeastInput`.
  - S→C : snapshots `Bond`, `Companion`, `Consciousness`, `Manifest`, `Form`, `MantleLeap`, `BeastAction`, `InnerVisit`.
  - Les requêtes sont limitées par portes temporelles (`Gate` : 5 ticks par action, 20 ticks par synchro).
- Garde-fous : flag `quarantined` (données d'un ancien format en attente de revue, `LegacyBijuArchive`), mode `preview`, et exclusions mutuelles systématiques (pas de manteau pendant une étreinte, un clash, le monde intérieur…).
- Bijū sauvages : `BijuWildEncounterService` les fait apparaître à 56–88 blocs d'un joueur, hors d'un rayon de 256 autour du spawn, avec 24 000 ticks de repos avant réapparition. Un objet `ItemBijuMap` les localise.
- Admin : `/biju` (désigner un jinchūriki, libérer, localiser, XP, entrer dans le mindscape…).

**Intérêt RP.** Fort. La **réserve du bijū** comme ressource commune donne du poids à chaque appel de chakra. Et des règles pures, testables, sont exactement ce que Reborn fait déjà en plugins.

**Faisabilité Reborn.** Facile à moyen. C'est de la logique serveur pure, à brancher sur `CharacterData` comme `JinchurikiData` (par personnage, pas par compte).
- Le bijū sauvage en monde ouvert est **inutile** chez Reborn : c'est le staff qui attribue.
- La coupe Yin / Yang est une idée intéressante pour Kurama (deux hôtes RP).

---

## 2. Lien et amitié (Bijū Bond)

**Ce que voit le joueur.**
- Un écran « Lien » avec un **portrait 3D animé de Kurama** : `KuramaPortraitData`, 272 triangles en 4 parties, mâchoire animée par `BijuPortraitSpeech`.
- Le palier du lien, la confiance, la fatigue et la tension.
- Les souvenirs marquants (`BondMemoryLayout`).
- L'état du sceau (`BijuSealArt`).
- Des boutons d'action, désactivés avec la raison du refus (`BijuBondActionPolicy`).
- Une file de répliques temporisées (`TimedSpeechQueue`).

**Comment ça marche.** `BondRules`, `BondGrowthRules`, `BondMind`, `BondJourney`, `BijuBondProfile`.

**Les données.** `BondRecord(playerId, trust, seal, mind)`. Le `BondMind` regroupe :
- `tension` et `fatigue` (0–100) ;
- les compteurs `kept` / `broken` ;
- `repairPledge`, une promesse de réparer en cours ;
- jusqu'à **24 mémoires typées** : `MET`, `FORCED`, `REPAIR_KEPT`, `PACT_KEPT`, `BODY_TAKEN`… En cas de purge, les mémoires décisives `FORCED` / `REPAIR_KEPT` sont gardées ;
- un `BondLoan` (prêt de chakra en cours) ;
- une `BondReflection` (limite mutuelle : OFFERED → AGREED → KEPT, ou BROKEN / DISTANCE) ;
- un `BondJourney` : pactes, dette de réparation (0–3), préférence d'aide `AUTOMATIC` ou `WARN_FIRST`, moments partagés.

**Les paliers** (`BondGrowthRules.Stage`).

| Palier | Condition |
|---|---|
| DISTRUST | confiance < 0, ou dette de réparation > 0 |
| CAUTIOUS | confiance < 80 |
| RESPECT | 80 à 249, ou limite mutuelle pas encore tenue |
| COOPERATION | ≥ 250 et limite mutuelle tenue |
| FRIENDSHIP | ≥ 650, expériences **variées** (au moins 3 sortes de pactes tenus, ou 2 sortes de moments partagés et 6 moments au moins), et au moins 8 expériences au total |
| LOYALTY | ≥ 850 et au moins 16 expériences |

**Ce qui fait monter la confiance.**
- **Prêt tenu.** On demande du chakra, on reçoit au moins 3 impulsions, puis on **contient** volontairement le flux avant la fin, après 60 ticks au moins. Gain : +20, avec un plafond de 180. Sous pression (prêt pris en plein danger), +40 la première fois, plafond 300. Récompense au plus toutes les 12 000 ticks (10 min).
- **Limite mutuelle tenue** : +25 une fois, plafond 300.
- **Réparation tenue** après une trahison : +35, plafond 250. Chaque réparation réduit la dette de 1.
- **Pactes** (`BondPact.Kind`) :

  | Pacte | Ce qu'il demande | Gain |
  |---|---|---|
  | `REGULATE` | contenir le chakra | +26 |
  | `KEEP_LIMIT` | respecter le prêt | +24 |
  | `TRAIN_TOGETHER` | pratiquer dans le monde intérieur | +32 |
  | `STAND_ALONE` | se battre seul, constaté par 2 témoins distincts (`BijuCombatEvidence`) | +34 |

  Le gain est divisé par 2 si on répète le même pacte deux fois de suite, et tombe à 0 à la troisième. Il y a 2 400 ticks entre deux reconnaissances. **Plafond de 350 tant qu'on n'a pas tenu 3 sortes de pactes, puis 1000** : la variété est obligatoire.
- **Envies du bijū** (`BijuDesire`, `BijuCompanionService`). Kurama propose une demande que le joueur accepte ou refuse : manger (ramen, pomme, repas), chasser (mouton, zombie, squelette) ou miner (diamant, fer, or). Le poids de chaque catégorie dépend de la personnalité (`BijuPersonality` : ramen 4, chasse 4, exploration 2 pour Kurama). Gain : +12, +18 pour un diamant, mais seulement +4 si c'est la même catégorie que la fois précédente. Un refus repousse l'envie suivante de 6 000 ticks.

**Ce qui la fait baisser.**
- **Forcer le prêt** (`force`). C'est possible après 40 ticks de prêt, pour 800 de réserve. Effets :
  - −45 de confiance, ou −70 si une promesse de réparation était en cours ;
  - tension +30 et fatigue +30 ;
  - dette de réparation +1 (ou +2 en cas de promesse), au maximum 3 ;
  - le pacte en cours et la limite mutuelle sont rompus ;
  - mémoire `FORCED` ;
  - plus de demande possible pendant 2 400 ticks.
- En dessous de −80, Kurama **refuse** tout prêt, sauf si le joueur a promis de réparer.
- La possession ajoute fatigue +20, puis +10 au retour. Porter le manteau ajoute de la fatigue toutes les 200 ticks (`mantleStrain`).
- Récupération : tension −2 et fatigue −1 toutes les 200 ticks.

**Le prêt de chakra** (`BondRules.request`).
- Part prêtée : 8 % du chakra max en cas de méfiance, 12 % en temps normal, 18 % à partir du palier 3 (confiance ≥ 250).
- Bonus : +2 % en mode régulé, plus jusqu'à +4 % selon la maîtrise (`BijuLearning.loanBonus`).
- Étalé sur 12 impulsions (16 en mode régulé).
- Coût : 600 de réserve. Délai entre deux demandes : 1 200 ticks. Fatigue +16.
- Le prêt **ouvre le sceau** (`SealRules`) : ouverture 2 000 pour un prêt normal, 4 500 pour un prêt forcé, 9 000 en possession, 2 500 + 1 000 × nombre de queues avec un manteau.

**Maîtrise (XP).** `BijuLearning` donne de l'XP selon le type d'expérience, avec un plafond de 320 XP par fenêtre de 10 min :

| Expérience | XP |
|---|---|
| CHANNELING | 10 |
| REGULATION | 25 |
| UNDER_PRESSURE | 40 |
| INNER_PRACTICE | 35 |
| SELF_RELIANCE | 30 |

Seuils de rang : 120, 400, 1 200, 3 200, 7 000 et 12 000 XP.

**Coordination** (`BijuControlRules.coordination`). Elle va de 0 à 1 :
0,1 + 0,32 × pratique + 0,36 × (confiance / 850) + jusqu'à 0,12 en moments partagés + 0,1 si la limite mutuelle est tenue − fatigue × 0,0018 − tension × 0,0015, plus un bonus « amitié » qui dépend de la réputation.
Elle règle tout le reste : contrôle des formes, vitesse en forme bijū, durée des manteaux.

**Intérêt RP (★★★).** C'est la pièce maîtresse :
- Une relation qui **se mérite par des engagements tenus** et se casse par la contrainte, c'est exactement le fantasme Naruto / Kurama.
- La **dette de réparation** et la **mémoire des trahisons** sont de l'or pour le RP : le bijū « se souvient ».
- Les plafonds et la variété obligatoire empêchent le farm.
- Pour un serveur encadré, le staff garderait la main via des commandes (`/tail set trust`…), et on pourrait **masquer les chiffres** au joueur. ShinobiTail le fait déjà avec `show-relationship-to-player: false`.

**Reborn a déjà (partiel).** `JinchurikiData` contient trust, anger, cooperation et influence (0–100, **réglés par le MJ**), plus une maîtrise par palier. Rien ne fait évoluer ces jauges par le jeu lui-même : pas de prêt, pas de pacte, pas d'envie, pas de mémoire.

**Faisabilité.** Moyen.
- La logique serveur (prêt, pactes, envies, mémoires) est facile en Paper : ce sont des machines à états.
- L'écran Lien se ferait dans `reborn-hud` comme les autres menus. Le kit nuit / laque / or existe ; il faudrait un motif signature propre au bijū.
- Le portrait 3D animé est optionnel. Une illustration 2D avec bouche animée suffit au début.

---

## 3. Conversations avec Kurama

**Ce que voit le joueur.** Une bulle de dialogue dans l'écran Lien et en jeu (`OverlayBijuDialogue`). Kurama parle quand on clique sur une action, et **de lui-même** sur certains événements : début de possession, retour, avertissement avant une aide d'urgence, manteau qui s'épuise…

**Comment ça marche.**
- `BondRules.Action` liste 22 actions : `TALK`, `REQUEST`, `CONTAIN`, `FORCE`, `REPAIR`, `RECALL`, `ASK_TRUST`, `ASK_CONTROL`, `ASK_SEAL`, `ASK_INTENT`, `REFLECT`, `AGREE_LIMIT`, `KEEP_DISTANCE`, 4 pactes, l'annulation de pacte, `AFTERMATH` (debrief de la dernière possession), `HELP_AUTO` et `HELP_WARN`.
- Chaque action renvoie une `reason`. Exemples : `greeting`, `forced`, `distrust_0..2`, `warming_0..2`, `close_companion`, `loyal_companion`, `recall_<type de mémoire>`, `debrief_death`…
- `BondDialogueCatalog` associe à chaque `reason` un **pool** de clés de langue (4 variantes). `dialogue.nydomod.bond.*` compte 207 lignes en_us. Le choix tourne avec `dialogueCursor` et évite la ligne précédente.
- `talk()` produit une réplique **contextuelle**, par priorité :
  1. présentation, au premier contact ;
  2. avertissement si un prêt est en cours ;
  3. rappel de la promesse de réparer ;
  4. « fatigué » si la fatigue est à 60 ou plus ;
  5. méfiance ;
  6. rappel du pacte en cours ;
  7. puis selon le palier : loyal, ami, distance respectée, confiance mutuelle, ou chaleureux / prudent.
- `RECALL` fait ressortir un **souvenir réel** de la liste des mémoires.
- Délai de 160 ticks entre deux répliques.
- Les autres bijū n'ont que 5 répliques génériques par personnalité (`dialogue.nydomod.personality.*`, 50 lignes). Les tempéraments viennent de `BijuPersonality` (orgueilleux, posé, prudent, honorable…), avec une volonté de 0,48 à 0,95 (0,95 pour Kurama).

**Intérêt RP (★★).**
- Sur un serveur RP, la **voix du bijū doit pouvoir être tenue par un staff**. ShinobiTail le permet déjà avec `/tail speak`.
- Le modèle Nydo de répliques choisies par l'état serait un **complément automatique** : il donne vie au bijū quand aucun MJ n'est présent.
- Les questions « que veux-tu ? » ou « explique le sceau » servent de tutoriel en jeu.

**Reborn a déjà.**
- `/tail speak <perso> <message>` : le MJ parle au nom du bijū, avec le préfixe `messages.beast-prefix`.
- `whispers` et `confront-lines` par bête dans `beasts.yml`.
- Les **paliers de corruption** (30 / 55 / 80 de rage, messages et sons).

**Faisabilité.** Facile.
- Un catalogue YAML `raison → répliques` par bijū, écrit en français par l'équipe.
- Un affichage dans le chat RP custom ou dans une bulle `reborn-hud`.
- Pas d'arbre de dialogue à construire.

---

## 4. Monde intérieur (mindscape)

**Ce que voit le joueur.**
- Après le scellement, une **première rencontre** automatique (400 ticks).
- Ensuite, on « s'arme » depuis l'écran Lien puis on **dort dans un lit** pour entrer dans une dimension dédiée, `nydomod:bijuu_mindscape`, avec une arène construite par `InnerRealmStructure`.
- On y fait une **épreuve** (`InnerTrial`) :
  - Kurama charge pendant 80 ticks et marque une zone au sol à l'emplacement du joueur.
  - Le joueur a 40 ticks pour **sortir d'un rayon de 2,25 blocs**.
  - Il doit ensuite **contenir** (appuyer sur l'action) dans les 80 ticks.
  - Il faut réussir 3 manches avec au plus 3 erreurs, en 2 400 ticks maximum.
- Réussir valide le fait `INNER_PRACTICE`, qui donne de l'XP et remplit le pacte `TRAIN_TOGETHER`.

**Intérêt RP (★★★).** C'est la scène emblématique : rencontrer le bijū derrière les barreaux. Une petite épreuve d'esquive et de timing est jouable sans MJ.

**Reborn a déjà (et va plus loin côté RP).** `InnerWorldManager` fonctionne ainsi :
- **gel de 3 s** du joueur, avec aura ;
- un **faux corps** (`FakeBody` : armor stand, tête et équipement) reste sur place ;
- téléportation vers le lieu configuré par `/tail setinnerworld` ;
- GUI `ConfrontGui` avec deux choix : **céder** (palier suivant) ou **résister**, sur un jet basé sur maîtrise, confiance, coopération, influence et rage ;
- une troisième option dorée, **Union**, si confiance et coopération sont à 100.

Il manque l'**épreuve d'action** et la visite **volontaire** (méditation) hors transformation.

**Faisabilité.** Facile à moyen.
- L'épreuve se fait en plugin : marqueur au sol avec des particules ou un block display, vérification de la position côté serveur, clic ou touche via un paquet `reborn-hud`.
- La dimension dédiée n'est pas nécessaire : le lieu MJ existant suffit.

---

## 5. Scellement et extraction

**Ce que voit le joueur.**

**Scellement en rituel** (`EntitySealing.EC`, jutsu `ItemYoton.FUUIN`, rang S, 100 de chakra).
- Une **table de scellement** apparaît, avec des places.
- Le **réceptacle** s'y installe, et de 1 à 4 **sceaux** autour (`ROLE_VESSEL`, `ROLE_SEALER_0..3`). En multijoueur, il faut au moins 2 sceaux.
- Le bijū doit être **affaibli à 10 % de PV ou moins** (`BijuSealService.weakened`), entravé par des **chaînes de scellement** (`SEALINGCHAIN`, rang A, 50 de chakra), et se trouver à 6,8 blocs au plus.
- Le rituel dure **1 200 ticks (60 s)**. Chaque sceau paie 10 de chakra par seconde. Le rituel est annulé après 240 ticks sans progression.
- Barre de progression : « Fūin X % ».

**Auto-scellement** (`BijuSelfSeal`). Le futur hôte retient lui-même le bijū avec ses chaînes et canalise 400 ticks à 10 de chakra par seconde. Il ne doit pas bouger de plus de 1,5 bloc.

**Conditions sur l'hôte** (`hostEligible`). Un joueur vivant, hors créatif, pas déjà jinchūriki. Une moitié de Kurama ne peut être scellée que si elle est **libre et sauvage**.

**À la fin** (`acquired`) : création du lien, synchronisation compagnon, puis première rencontre dans le monde intérieur.

**Extraction** (`BijuSealExtraction`).
- **L'hôte doit être entravé** : au moins 3 chaînes d'un même contrôleur, ou une **imitation d'ombre** tenue par un tiers (`BijuSealRestraints.find`).
- L'extracteur s'accroupit à 4 blocs au plus, en vue directe, PvP autorisé entre eux (`hostileContact`). Il paie 25 de chakra par seconde.
- Durée : 80 + 160 × intégrité + 120 × solidarité ticks, soit environ 4 à 18 s. Ironie voulue : plus l'hôte est **lié** au bijū, plus l'extraction est longue.
- Phases : lecture (40 ticks), dénouage (8 ancrages se ferment un à un), brèche (40 ticks).
- Résultat :
  - Kurama réapparaît en entité, avec 25 à 100 % de PV selon la réserve restante.
  - Son **tempérament** est tiré au sort (`SealExtractionRules.temperament`) : DÉCHAÎNÉ, MÉFIANT ou PROTECTEUR. Les chances de déchaînement vont de 0,03 à 0,97 selon la solidarité du lien, la volonté du bijū et la tension. Un Kurama protecteur **défend son ancien hôte** et attaque l'extracteur (`BijuReleasedBehavior`).
  - **L'hôte survit** : PV ramenés à 20 %, −75 % de chakra, Faiblesse II pendant 30 s, Lenteur II pendant 10 s, Nausée pendant 6 s.
- Mode **réparation** : si l'extracteur est l'hôte lui-même, le canal **renforce** le sceau (160 ticks).

**Intégrité du sceau.** `SealState` va de 0 à 10 000 (intégrité), avec une ouverture variable.

**Intérêt RP (★★★).**
- Le scellement et l'extraction sont des **événements de serveur** : rituel à plusieurs, chaînes, scellés.
- Le rituel coopératif à places est idéal pour un clan Uzumaki ou une équipe de scelleurs.
- Le tempérament post-extraction qui dépend du **lien** récompense le RP fait en amont.
- Sur Reborn, il faudrait **brancher l'issue sur la mort RP**, dans l'esprit du canon où l'hôte meurt de l'extraction : passage par `CorpsService` ou une RPK validée par un modérateur. Il faudrait aussi verrouiller ces actions derrière un **événement validé par le staff** (permission temporaire, ou drapeau d'event posé par un MJ).

**Reborn a déjà.**
- `/tail bind` / `unbind` : attribution par le MJ.
- `ShinobiAbilities…/fuinjutsu/SealingLink` (« Sceau de Contention ») : s'accroupir et cliquer droit sur un jinchūriki **transformé** pour drainer sa rage jusqu'à **refermer la transformation**, contre du chakra du sceau. C'est l'équivalent de `BijuSealRestraints` combiné à « sceller la forme », **pas** un transfert de bijū.
- Le `StopReason.SEALED` de `TransformationManager`.

Il manque le rituel de transfert, l'extraction et l'intégrité du sceau.

**Faisabilité.** Moyen.
- Tout se fait côté serveur Paper : places autour d'un bloc ou d'une entité « table », chakra via `ResourceService`, barre de progression, chaînes en block / item displays ou en modèle Model Engine.
- Le passage du bijū d'un perso à un autre réutilise `JinchurikiStore`.

---

## 6. Possession du corps (instinct / « Bijū body possession »)

**Ce que voit le joueur.**
- **Début** (160 ticks, `ConsciousnessTimeline.ONSET`) : flou progressif, deux clignements de paupières (à 34 et 80 ticks), puis fermeture totale. La caméra **s'effondre** au sol (`ConsciousnessFall`), avec un battement de cœur discret.
- **Déchaînement** : écran noir, HUD masqué, entrées de déplacement et d'interaction annulées (`BijuConsciousnessClient.input`/`interact`), menus fermés. Le son du monde reste audible.
- **Réveil** (120 ticks) : les yeux se rouvrent avec un clignement.
- Si la perte de contrôle vient d'une **activation volontaire ratée**, l'écran reste **visible** (`visible = cause voluntary_*`). Le joueur **regarde son corps agir sans pouvoir rien faire**.
- Options d'accessibilité côté client : flou, mouvement réduit, battement de cœur, chute de caméra et sons sont désactivables (`BijuInstinctConfig.CLIENT`).

**Comment ça marche** (`BijuInstinctService`, `InstinctSession`, `BijuInstinctCombat` / `Navigation` / `Motor`).

**Déclencheurs :**

| Cause | Condition |
|---|---|
| `emergency` | PV à 25 % ou moins après un coup hostile (`critical`), avec une seule évaluation par recharge. Ou **coup mortel** : `dying()` ramène les PV à 15 % et lance la possession, le bijū sauve son hôte |
| `overload` | après un prêt forcé, si fatigue ≥ 60, tension ≥ 40 et confiance < 125 |
| `voluntary_N` | contrôle raté en activant le manteau N |

**Chance de garder le contrôle** (`BijuControlRules.keepControl`) :
0,3 + 0,78 × coordination − 0,07 × (queues − 1) − 0,13 × volonté du bijū − 0,08 en urgence, borné entre 0,04 et 0,98.
En **urgence réussie**, Kurama prête un **manteau de secours contrôlé** (au moins 2 400 ticks) au lieu de prendre le corps. Si le lien est loyal et que le joueur a choisi « prévenir d'abord », Kurama **avertit** avant d'intervenir (`help_warning`).

**Durée.** 2 400 ticks par défaut (2 min, réglable de 400 à 4 800). Elle est réduite de 1/8 si l'hôte a appris, et de 1/8 de plus si la confiance est à 100 ou plus. Un sauvetage dure au moins 2 400 ticks. La recharge est de 12 000 ticks, et jamais moins que l'épisode plus 2 400.

**L'IA sur le corps du joueur.** Elle choisit parmi : chercher, poursuivre, encercler, contourner, rôder, charger, coups au corps à corps. Elle peut casser jusqu'à **8 blocs « mous »** par épisode, en respectant le mobGriefing et les protections. Elle respecte les règles PvP et d'équipe, et **épargne les alliés**, les créatures nommées et les animaux apprivoisés.
- Dégâts : 26 pour Kurama, 20 pour les autres. Vitesse : 0,88.
- Un **réservoir de vitalité** absorbe les coups. Un réflexe défensif réduit les dégâts de 12 %, plafonné.

**Fin de la possession :**
- `quiet` : plus de cible ;
- `exhausted` : réserve épuisée ;
- `vitality_depleted` ;
- `elapsed` ;
- `restrained` : **entravé par des chaînes de scellement**, c'est ainsi que les autres arrêtent un hôte déchaîné ;
- `death`, `logout`, `seal_extracted`.

**Après coup.** Mémoires `BODY_TAKEN` / `BODY_RETURNED`. L'action `AFTERMATH` donne un **debrief** dans l'écran Lien : test, mort, contrainte, calme, danger.

**Intérêt RP (★★★).**
- La perte de contrôle est **le** moment fort du jinchūriki.
- Le « le bijū te sauve quand tu tombes » donne du sens au lien.
- L'écran noir est très fort en immersion, **mais** sur un serveur RP, laisser une IA tuer des joueurs est dangereux : RPK non consenties, griefing.
- Reborn devrait plutôt : bloquer le contrôle, prévenir le staff (Poste de garde, webhook Discord), et confier soit à une IA bridée (pas de mise à mort, dégâts plafonnés, KO plutôt que mort), soit à un **MJ qui « joue » le bijū**.

**Reborn a déjà (sans IA).**
- **Déclencheurs** (`TriggerListener`) :
  - jets à 50, 30 et 15 % de PV ;
  - 0,01 % de chance par coup reçu ;
  - épuisement du chakra ;
  - **au KO, le bijū sauve son hôte** (+15 points à la chance d'aide, soin de 35 %, réveil transformé).
- **Deux jets distincts** :
  - « aide » : le bijū prête sa force parce qu'il le veut, nourri par confiance et coopération ;
  - « prise de contrôle » : il force le passage, nourri par colère et influence.
- **Rage croissante** et **corruption** par paliers.
- **Fenêtres de contrôle** toutes les 180 s ± 60 s, qui renvoient au monde intérieur.
- **Point de non-retour** au dernier palier : le personnage est **mort RP** à la fin de la transformation.

C'est conceptuellement plus « RP serveur » que Nydo. Mais le joueur garde toujours le contrôle de son corps : il n'existe pas de vraie dépossession.

**Faisabilité.**
- Écran noir, flou, paupières et blocage des entrées dans `reborn-hud` : **moyen**. Il y a déjà des overlays KO et une scène de mort ; l'état passerait par un canal type `reborn:ko`.
- Corps piloté par une IA côté serveur Paper : **lourd**. Un `Player` ne se pathfinde pas. Il faudrait soit un PNJ ou un modèle Model Engine qui prend le relais pendant que le joueur est caché ou spectateur (en réutilisant l'idée du `FakeBody` dans l'autre sens), soit mouvoir le joueur à coups de vélocités serveur.
- Recommandation : version « écran noir + MJ ou IA bridée ».

---

## 7. Manteaux 1 à 3 queues (cloaks)

**Ce que voit le joueur.** Une coque de chakra rouge-orangé qui épouse le modèle du joueur, avec 1 à 3 queues, des oreilles ou cornes et des veines. Elle a une animation d'arrivée, et ondule, se penche et se balance avec la course.
Le joueur gagne :
- vitesse : +20 % à 1 queue, +32 % à 2, +44 % à 3 ;
- dégâts : +1, +2 ou +3 ;
- poise (résistance au recul) : 0,08 × queues ;
- régénération de chakra à chaque impulsion (20 ticks) ;
- soins toutes les 40 ticks si aucun coup reçu depuis 60 ticks ;
- chakra max multiplié par (1 + 0,5 × queues).

Un saut de manteau existe aussi (`BijuMantleLeap`). Un message prévient quand le manteau faiblit, 200 ticks avant la fin.

**Comment ça marche.**

**Accès.** `BijuControlRules.requiredXp` : 301, 600, 900 et 1 200 XP pour 1 à 4 queues. Le catalogue `BijuManifestationCatalog` va de 1 à 9 queues, plus Golden, Sage, Six Paths et Avatar (seuils XP et niveau ninja de 10 à 150), mais **seules 1 à 4 sont implémentées**. `BijuFormAccessRules` ajoute des raisons de refus : coopération requise (confiance ≥ 250 et limite mutuelle tenue pour les formes avancées), mode ermite, Six Chemins…

**Coût** (`BijuMantleRules`) :
- 600 de réserve à l'activation, avec au moins 1 600 disponibles ;
- recharge de 600 ticks ;
- changement de palier : 250 de réserve, 80 ticks entre deux changements ;
- entretien : 8 × queues de réserve par seconde, qui grimpe jusqu'à ×9 au bout de 8 × 30 s ;
- fatigue de 2 + queues toutes les 200 ticks ;
- repos après coup : 200 à 2 400 ticks.

**Durée.** 600 à 6 000 ticks (30 s à 5 min) selon maîtrise, amitié (moments partagés), confiance et fatigue. Elle est multipliée par 0,7 à partir de 4 queues.

**Jet de contrôle à chaque activation.** S'il rate, c'est la possession (§6).

**Rendu** (`client/biju/manifest`).
- `BijuMantleLayer` est un calque de rendu joueur actif pour 1 à 3 queues.
- `ChakraShellConsumer` est un `VertexConsumer` intermédiaire : il **re-dessine le modèle du joueur gonflé le long des normales**, avec un scintillement, ce qui donne la coque.
- `BijuMantleMesh` construit les queues **procédurales** : 8 segments avec largeur dégressive, et les cornes.
- `BijuVeinMesh` trace des veines ramifiées sur 4 niveaux d'épaisseur.
- `BijuMantleAura`, `BijuMantleArrival` (apparition), `BijuMantlePose` (balancement, levée, inclinaison).
- Matériaux émissifs, avec une variante compatible shaders (`NydoVfxShaderCompatibility`).

**Intérêt RP (★★★).** C'est la signature visuelle d'un jinchūriki. Coût et durée liés au lien : un hôte en froid avec son bijū ne tient pas longtemps.

**Reborn a déjà (côté serveur).** `TransformationManager` applique des effets par palier selon une courbe de puissance : `power(stage) = stage × (9 / queues)^0,732`. Par point de puissance : dégâts +30 %, vitesse +9 %, résistance +4 %, échelle +0,02, régénération de PV et de chakra, aura. Il y a aussi une boss bar et une aura en particules (hélices, anneau, colonne). **Aucun rendu de manteau** côté client.

**Faisabilité.**
- Effets et coûts : facile, c'est déjà en place.
- Rendu dans `reborn-hud` : **lourd**. MC 26.2 n'a plus `DrawContext` et le rendu d'entité passe par des render states (cf. `MIGRATION_26.2.md`). Une coque « modèle gonflé » plus des queues procédurales demandent des feature renderers custom et des render types émissifs.
- Variante **moyenne** : queues en modèles Model Engine ou item displays attachés au joueur, plus les particules existantes.

---

## 8. Manteau 3 queues : taïjutsu spécial (bras étendus)

**Ce que voit le joueur.** En manteau, les coups de poing **projettent un bras de chakra** :
- portée de 3,05 à 5,8 blocs (`KuramaCombatCatalog.REACH_*`) ;
- jusqu'à 3 cibles, 9,5 de dégâts ;
- coût : 8 de chakra et 12 d'endurance.

Le **coup chargé** est une **prise** :
- capture en 8 ticks, puis montée (tick 30), écrasement au sol (tick 44), projection (tick 66) ;
- 16 de dégâts : 20 % à la capture, 80 % au slam ;
- recharge de 140 ticks.

La victime est animée (`KuramaGrabVictimAnimation`). Des **clones de chakra** viennent aussi prendre les cibles en tenaille (`KuramaCloneAssist` / `KuramaCloneCatalog`). Autres coups du style : Breakwater, Predator Cross, Crimson Drop, Fox Echo.

**Comment ça marche.**
- `ThreeTailCombatManager.route` détourne les entrées du système de combat générique vers le catalogue Kurama.
- `KuramaReachSelection` choisit les cibles.
- Le rendu combine `KuramaStrikeMesh` / `Renderer`, une main déformable (`KuramaBendyHand`) et un cache de collision de rendu.
- Le terrain est marqué à l'impact (`ThreeTailTerrainImpact`).

**Intérêt RP (★★).** Très spectaculaire en combat, mais le combat est secondaire au RP. Sert surtout les combats d'événement.

**Reborn.** `ShinobiCombat` (M1 kenjutsu / taïjutsu, parade, endurance) offre la base : une **variante de style « manteau »** avec allonge accrue et une prise serait une extension.

**Faisabilité.** Moyen côté serveur : portée, hitbox, prise avec téléportations successives. Lourd pour le **bras rendu** côté client : mesh étiré entre l'épaule et la cible. Alternative : beam de particules ou block display étiré.

---

## 9. Forme 4 queues (forme native)

**Ce que voit le joueur.**
- Après une **activation** de 24 ticks, le joueur devient un **petit bijū noir-rouge à 4 queues**, avec une caméra à la troisième personne qui recule selon la taille du corps.
- Déplacement fluide :
  - marche 0,4, course 0,78, **shunshin** jusqu'à 2,4 ;
  - **3 sauts aériens**, course sur les murs (80 ticks), saut de mur ;
  - **slam** en course.
- Les queues ondulent physiquement selon l'élan, les virages et les sauts.
- Combat à griffes : droite et gauche à 5 de dégâts, combo 3 coups à 9, slam à 11. Cônes de 2,8 blocs / 106°, ou 3,4 blocs / 150° pour le slam, en mode « cleave ».
- Esquives dans les 4 directions.
- Rayon et **Bijūdama** (§10).
- La maniabilité dépend de la **coordination** avec Kurama.
- Il faut de la place pour se transformer (`no_space`).

**Comment ça marche.**

**Serveur.**
- `BijuBeastMount` est une `Entity` **invisible que le joueur chevauche**.
- Les entrées passent par le paquet `BijuBeastInput`. `BijuBeastMotor` / `BijuBeastMotion` donnent vitesse, accélération, freinage et virage, tous dépendant du contrôle.
- Les réglages fins sont dans `FourTailTuning` : 3 sauts aériens, mur 80 ticks, montée de marche 1,05…
- Pendant la forme, le combat humain est suspendu (`NYCombatManager.suspendForBiju`).
- Hitbox du corps : `BijuBodyGeometry` (sphères, collision par balayage).

**Client.**
- `BijuBeastPrediction` fait de la prédiction avec réconciliation (au plus 16 ticks d'écart).
- `FourTailsLocomotionSelector`, `FourTailsGroundContact`, `FourTailsAirControl`, `FourTailsLook`, `FourTailsTimeline` et `FourTailsAnimationState` (705 lignes, mélange de clips) pilotent l'animation.

**Rendu natif** (`FourTailsNativeRenderer`). Le modèle `models/entity/biju/four_tails_native.json` (9,3 Mo) est un **format maison** :
- `format`, `units_per_block`, `source_sha256` ;
- **78 os** : squelette complet jusqu'aux doigts, et 4 queues de 10 segments ;
- 251 cubes ;
- **37 clips** : idle, walk, run, sprint, shunshin, roar, claw…, bijuudama_charge / release, dodge ×4, wall_run, air_jump 1 à 3…
- des **locators** comme `socket_bijuudama`, `socket_gaze` ou `contact_hand_L`.

Le rendu se fait en deux passes, albedo et **émission**, plus une aura, avec des render types distincts quand un shader pack est actif.

**Dynamique des queues** (`FourTailsTailDynamics`). 4 × 10 segments, soit 40 os, avec un **ressort amorti par os**. Simulation à pas fixe de 1/120 s, 30 sous-pas au maximum. La raideur et l'amortissement dépendent du segment. Les queues réagissent à la vitesse du corps, à sa vitesse angulaire, à l'accélération et au poids en l'air. Vitesse limitée à 2,4, accélération à 24, rotation à 240°/s.

**Caméra** (`BijuBeastCamera`, `BijuBeastCameraRig`, `BijuBeastCameraFeedback`, `BijuBeastAimCamera`).
- Recul calculé selon la hauteur et la largeur du modèle (profil `FOUR_TAILS` : recul minimum 3,2, au plus 24 blocs).
- Point d'ancrage lissé, qui suit la tête animée.
- Collision avec le terrain, pour que la caméra ne traverse pas les murs.
- Retour de foulée : jusqu'à 7° de tangage.
- **Visée épaule** en douceur pendant la charge de Bijūdama.
- Intégration du verrouillage de cible.

**Intérêt RP (★).** Superbe techniquement, mais une forme 4 queues en ville RP est rarissime : c'est un moment d'événement. Le rapport valeur / coût est faible pour Reborn.

**Reborn.** Rien côté rendu. ShinobiTail se contente d'augmenter l'échelle du joueur et l'aura.

**Faisabilité.** **Lourd** en natif : mount, rig, prédiction, caméra et 37 animations dans `reborn-hud`. Variante **moyenne** : modèle **Model Engine** (pipeline `bbforge` déjà en place, cf. Son Gokū 6 animations), monté par le joueur grâce au système de mount de Model Engine, joueur invisible, caméra vanilla en troisième personne avec un recul forcé côté mod. Les queues physiques seraient remplacées par des animations.

---

## 10. Bijūdama et rayon (Tailed Beast Ball)

**Ce que voit le joueur.** En forme 4 queues, il charge une boule ou un rayon : visée épaule, effets `vfx/nydobiju/charge|ball|beam|impact`. La boule part et explose en laissant un **cratère limité**.

**Comment ça marche** (`BijuArtilleryAccess`, `artillery/BijuArtillery`, `BijuArtilleryProfile`).

| Attaque | Chakra | Réserve | Recharge | Préparation |
|---|---|---|---|---|
| BEAM | 600 | 900 | 160 ticks | 18 ticks |
| BALL | 1 000 | 1 400 | 240 ticks | 24 ticks |

Profil `FOUR_TAILS` : boule de rayon 0,8 lancée à 2,4, explosion de rayon 6, **34 de dégâts** (−65 % au bord), rayon de 9 dégâts par impulsion, portée de 64, cratère de rayon 5 et 144 blocs au plus.
Profil `KURAMA` (bijū complet) : 280 de dégâts, explosion de rayon 10.

**Conditions.** 1 200 XP, forme contrôlée (pas possédée), sceau intègre, mains vides.

**Charge serveur maîtrisée.** 1,5 ms par tick au maximum, 8 attaques simultanées, 768 blocs de collision, 16 rayons et 8 tentatives de dégâts par tick.

**Intérêt RP (★★).** L'attaque iconique. À réserver aux événements, avec une destruction de terrain **temporaire** ou désactivée en zone RP.

**Reborn.** Rien de dédié. `ShinobiAbilities` et MagicSpells peuvent lancer des projectiles.

**Faisabilité.** Moyen :
- projectile serveur, en item ou block display de sphère, ou modèle Model Engine ;
- particules ;
- dégâts de zone avec décroissance ;
- cratère « cosmétique » restauré après X secondes.

Le **budget par tick** de Nydo est une bonne pratique à reprendre.

---

## 11. Tableau final

| Idée | Ce que fait Nydo | Reborn a déjà ? | Valeur RP | Effort |
|---|---|---|---|---|
| Jauge de lien qui évolue par le jeu | Confiance de −1000 à 1000, 6 paliers, fatigue / tension, plafonds et variété obligatoire (`BondGrowthRules`) | Partiel : 4 jauges 0–100 **réglées par le MJ** (`JinchurikiData`) | ★★★ | Moyen |
| Prêt de chakra « demander / contenir / forcer » | 8 à 18 % du chakra en 12 à 16 impulsions. Contenir fait gagner de la confiance, forcer en fait perdre 45 à 70 et crée une dette de réparation | Non (seulement le jet « aide » automatique) | ★★★ | Facile |
| Mémoire des trahisons + réparation | 24 mémoires typées, `repairDebt` 0–3, promesse de réparer | Non | ★★★ | Facile |
| Pactes (contenir, limite, entraînement, se battre seul) | Objectifs RP avec gains dégressifs ; témoins requis pour « seul » | Non | ★★ | Moyen |
| Envies du bijū (ramen, chasse, minage) | Demandes pondérées par personnalité, +4 à +18 de confiance | Non | ★★ | Facile |
| Répliques contextuelles du bijū | `reason → pool` de 4 variantes, rappel de souvenirs, questions (sceau, contrôle, intention) | Partiel : `/tail speak` (MJ), murmures, paliers de corruption | ★★ | Facile |
| Écran Lien avec portrait | Portrait 3D animé, actions avec raison de refus, souvenirs, état du sceau | Non (`AdminGui` côté MJ seulement) | ★★ | Moyen |
| Monde intérieur + épreuve | Dimension dédiée, entrée par le lit, épreuve esquive / contenir en 3 manches | Oui, version RP : faux corps, GUI céder / résister, Union | ★★★ | Facile (ajouter l'épreuve) |
| Rituel de scellement coopératif | Table, 1 réceptacle + 2 à 4 sceaux, 60 s, bijū ≤ 10 % PV et enchaîné | Non (bind MJ) | ★★★ | Moyen |
| Extraction + tempérament du bijū libéré | Hôte entravé, 4 à 18 s selon le lien, tempérament déchaîné / méfiant / protecteur | Non | ★★★ | Moyen |
| Intégrité / ouverture du sceau | 0–10 000, s'ouvre selon prêt, manteau ou possession | Non (`SealingLink` referme la transformation, sans intégrité) | ★★ | Facile |
| Chaînes qui stoppent un hôte déchaîné | La possession se termine sur `restrained` | Oui en esprit : `SealingLink` draine la rage | ★★★ | — |
| Possession : écran noir, paupières, chute de caméra | Début 8 s, entrées bloquées, réveil 6 s, mode « spectateur impuissant » si contrôle raté | Non (le joueur garde la main) | ★★★ | Moyen |
| Possession : IA aux commandes | Poursuite, charge, mêlée, 8 blocs cassés max, épargne les alliés, ~2 min | Non | ★★ (risqué en RP) | Lourd |
| Sauvetage par le bijū au KO / coup mortel | PV à 15 % et possession, ou manteau de secours contrôlé si lien loyal | **Oui** (`on-ko` : soin de 35 %, réveil transformé) | ★★★ | — |
| Jet de contrôle à l'activation | `keepControl` selon coordination, nombre de queues, volonté du bijū | Partiel (jets aide / prise de contrôle / résistance) | ★★ | Facile |
| Manteau : coût en réserve du bijū + durée liée au lien | 30 s à 5 min, entretien croissant, repos | Partiel (rage et fenêtres de contrôle, pas de réserve) | ★★ | Facile |
| Rendu du manteau 1 à 3 queues | Coque de modèle gonflé, queues procédurales, veines, émissif | Non (aura en particules) | ★★★ | Lourd (moyen via Model Engine) |
| Bras étendus 3 queues | Allonge 5,8 blocs, prise, slam, projection, clones | Non | ★★ | Moyen (serveur) / lourd (rendu) |
| Forme 4 queues jouable | Mount invisible, rig de 78 os, 37 clips, queues à ressorts, prédiction | Non (échelle + aura) | ★ | Lourd (moyen via Model Engine) |
| Caméra de bête | Recul selon la taille du modèle, collision, retour de foulée, visée épaule | Non | ★ | Moyen (`reborn-hud`) |
| Bijūdama / rayon | 1 000 de chakra + 1 400 de réserve, explosion de rayon 6, cratère plafonné, budget 1,5 ms par tick | Non | ★★ | Moyen |
| Kurama Yin / Yang (2 hôtes) | `KuramaHalf`, données séparées par moitié | Non | ★★ | Facile (données) |
| Bijū sauvages + carte de localisation | Apparition 56–88 blocs, `ItemBijuMap` | Non, et non souhaitable (attribution par le staff) | ★ | — |

## 12. Recommandation d'ordre (pour discussion)

1. **Lien jouable côté ShinobiTail**, en logique serveur uniquement : prêt demander / contenir / forcer, mémoires et dette de réparation, envies, répliques contextuelles en YAML. Les chiffres resteraient cachés au joueur, avec la main du MJ intacte (`/tail set`). C'est le plus fort rapport valeur RP / effort.
2. **Écran Lien dans `reborn-hud`**, avec un motif signature bijū, branché sur un canal `reborn:tail`.
3. **Possession « écran noir » côté mod** : paupières, flou, entrées bloquées. L'issue est confiée au MJ, ou à une IA bridée qui ne fait pas de mort RP, avec une alerte au Poste de garde.
4. **Rituel de scellement et d'extraction** dans le cadre d'un événement validé par le staff, en réutilisant `SealingLink` pour l'entrave et `CorpsService` pour une éventuelle mort RP.
5. Visuels (manteau, Bijūdama, 4 queues) via **Model Engine / bbforge** avant tout rendu natif.
