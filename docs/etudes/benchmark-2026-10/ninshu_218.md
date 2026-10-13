# Ninshu Origins 2.1.8 : différentiel et systèmes RP approfondis

> Étude complémentaire de `docs/archives/etudes/ETUDE_NINSHU_ORIGINS.md` (2.1.7). Inspiration de concepts
> uniquement : le mod est « All Rights Reserved » (`mods.toml`), on ne reprend ni code ni assets.
> Sources : décompilation `net.saberart.ninshuorigins` (2 666 classes, 1 734 `.java`), ressources du jar
> (`ninshu/res/`), dont le **guide en jeu** `assets/ninshuorigins/guide/en_us.json` (200 Ko, 78 pages), très
> utile parce qu'il donne les chiffres réels côté joueur, recoupés ici avec le code.
> Date : 2026-10-12.

---

## Résumé

1. La 2.1.8 change surtout la **lisibilité et l'encadrement** du mod : un livre-guide en jeu de 78 pages
   (statuts « Working / Partly Done / Unfinished / Creative / Admin »), une vraie config serveur éditable en
   jeu, une table d'armure (213 recettes de tenues), et des rencontres de PNJ nommés (Jiraiya, Orochimaru, Zabuza).
2. Le **combat au corps à corps** est bien plus profond qu'en 2.1.7 : garde haute/basse, parade parfaite de
   6 ticks, guard break qui étourdit, clash de coups identiques, 4 types de prise selon le clan, finisher par
   derrière, 4 sous-stats (Puissance, Forme, Jeu de jambes, Condition), Huit Portes avec blessures persistantes.
3. Les systèmes **sociaux** existent mais restent légers : escouades de 4 persistées avec missions partagées et
   friendly fire réglable, contrats à caution (100 ryo), réputation par village (-1000 à +1000, bannissement),
   diplomatie entre villages (guerre, alliance), embauche de PNJ par rang.
4. L'**économie** tient en un service unique (`CurrencyService`) : pièces et billets de 100 à 5 000 ryo, banque
   (ATM), portefeuille, carte bancaire, bonus quotidiens d'activité (5 000 puis 10 000 ryo).
5. Le **KO** de Ninshu (« Downed », 40 s, réanimation médicale) est plus simple que celui de Reborn (KO-1 à KO-7) ;
   deux détails valent d'être repris : la mort par hémorragie est créditée à celui qui a mis à terre, et toute
   réanimation interrompue plus de 3 ticks repart de zéro.
6. Pour Reborn, les meilleures idées à coût raisonnable : **garde haute/basse + clash** dans ShinobiCombat,
   **caution de mission** et **missions d'escouade partagées**, **réputation de village**, **guide en jeu
   data-driven à statuts**, **clan principal / ultime technique réservée**, **blessures persistantes des Portes**.
7. Côté 3D : 351 geo GeckoLib (111 tenues avec os de pans animés, 20 coiffures, 23 invocations, 9 bijū, 76 armes),
   147 animations joueur au format Bedrock. Utile comme **référence de découpage et de catalogue**, pas comme source.

---

## 0. Méthode et limites

- **Pas de jar 2.1.7 disponible.** Le différentiel s'appuie sur trois points : les affirmations de l'étude 2.1.7,
  le jar 2.1.8 (identique à `Downloads/ninshuorigins-2.1.8-1.20.1.jar`, même MD5) et un jar **2.1.4d** trouvé
  dans le profil Modrinth `Naruto Ninshu Origins Anime` (lu en liste seulement). « Nouveau » veut donc dire
  « absent de 2.1.4d et non cité par l'étude 2.1.7 », ce qui est très probablement 2.1.8 sans certitude absolue.
- Les dates internes du jar ne servent à rien (toutes au 2026-10-05).
- Les chiffres viennent du code quand c'est indiqué par une classe, sinon du guide en jeu (signalé « guide »).

---

## 1. Différentiel 2.1.7 → 2.1.8

### 1.1 Volumes

| Élément | 2.1.4d | 2.1.7 (étude) | 2.1.8 | Remarque |
|---|---|---|---|---|
| Classes | 1 397 | 2 239 | **2 666** | +427 |
| Fichiers `.java` décompilés | — | 1 523 | 1 734 | |
| Clés de lang `en_us` | 489 | 913 | **995** | +82 |
| geo | — | 316 | **351** | +35, surtout les 20 coiffures |
| animations (GeckoLib) | — | 271 | 285 | |
| player_animation | — | 141 | 147 | |
| models JSON | — | 704 | 887 | |
| Recettes | — | 40 | **283** | dont 213 `armor_table` |
| Guide en jeu | non | non | **oui** | `assets/ninshuorigins/guide/` |

### 1.2 Nouveautés non couvertes par l'étude 2.1.7

| Domaine | Ce qui apparaît | Classes / fichiers |
|---|---|---|
| **Guide en jeu** | Livre « Ninja Guide » donné au premier login, craftable, 14 chapitres / 78 pages, blocs typés, statut par page | `client/gui/guide/GuideBookScreen` + `GuideContent`, `S2COpenGuideBook`, `guide/en_us.json` |
| **Table d'armure** | Atelier de couture 5×5 + outils réutilisables, aperçu 3D rotatif, livre de patrons | `common/recipe/ArmorTableRecipe`, `common/gui/armortable`, `ArmorTableBlockEntity`, `client/compat/ArmorTableRecipeCategory` (JEI) |
| **Chaîne textile** | Bobine de fil, tissu, tissu rembourré, bourre, teintures, bandes de cuir, doublure d'acier | `FabricCrafting/Dyeing`, `PaddedFabric*`, `YarnDyeing`, `FluffDyeing` |
| **Config serveur réelle** | Sections progression, lit médical, entraînement, vitesse, développé-couché, endurance, Lame de vent, PNJ ninja ; écran de config en jeu (op niveau 2), synchronisée au client | `common/config/NinshuServerConfig`, `EditableConfigService`, `NamedNpcSpawnConfig` (`ninshuorigins-named-npcs.toml`), `client/config/NinshuConfigScreen`, `S2CServerConfigSyncPacket` |
| **PNJ nommés en monde ouvert** | Jiraiya (jour, près des villages), Orochimaru (nuit, clairières), Zabuza (rumeur donnée par un PNJ) ; messages d'arrivée, espacement, cooldown de région | `common/data/quests/ZabuzaEncounterQuest`, `FrogSageTraining`, `NamedNpcSpawnConfig` |
| **Mentors** | Rasengan (Jiraiya, 3 leçons de 60 s), Chidori (Kakashi, 180 s), Sage crapaud (3 méditations) | `common/helper/SpecialJutsuTraining` |
| **Combat** | Garde haute/basse, parade parfaite kenjutsu, clash, prises par clan, finisher arrière, sous-stats | `TaijutsuAttackPacket` (2 803 lignes), `TaijutsuGrabHandler`, `CombatProficiency(Effects)`, `CSKenjutsuAttack`, `ClientKenjutsuInput` |
| **Huit Portes** | 8 paliers liés au niveau de taïjutsu, blessures persistantes, Porte de la Mort mortelle | `common/entity/player/EightGatesHandler`, `client/fx/EightGatesClientEffects` |
| **Marque maudite** | Morsure d'Orochimaru, rituel des Quatre Brumes Noires (5 min, prison gardée), maîtrise | `CurseMarkHandler`, `FourBlackFogsBlockEntity`, `S2CFourBlackFogsConfinement`, mixins `FourBlackFogs*` |
| **Entraînement physique** | Banc de développé-couché, entraîneur de vitesse à rythme (fenêtres parfait/bon), mannequin, râtelier de poids, bottes lestées réglables | `common/block/benchpress`, `taijutsuspeedtraining`, `dummy`, `weightrack`, `WeightedBootsEvents` |
| **Armes de jet** | Rattraper au vol, collisions entre projectiles par poids, Fuma pliable, harpon | `ThrownWeaponProjectile`, `ThrownWeaponCollisions`, `CollidingThrownWeapon`, `FumaShurikenEntity`, `WaterHarpoonEntity` |
| **Village et diplomatie** | Réputation par village, exil, relations entre villages (guerre, alliance) avec valeurs « ère Naruto » | `world/PlayerVillageData`, `world/VillageWorldData` |
| **Escouades** | Persistées (`SavedData`), `/squad` complet, friendly fire, propriété du feu | `common/data/squad/*`, `SquadCommand` |
| **Clan principal** | Choix du clan principal parmi l'ADN possédé ; dernière technique d'un clan réservée au clan principal | `C2SSetMainClanPacket`, `Release_Base.isClanCapstone` |
| **Clans jouables** | Nara (ombre guidée + étranglement), Inuzuka (Gatsūga avec Akamaru), Morino (Vierge de fer) | `entity/jutsu/nara/NaraShadowEntity`, `inuzuka/PassingFangEntity`, `geo/summons/IronMaidenEntity` |
| **Coiffures 3D** | 20 coiffures GeckoLib (perso Shippuden), touche pour les masquer | `client/entity/hair`, `CSToggleHairPacket`, `geo/entity/hair/` |
| **Sac à dos** | Accessoire porté, 18 cases, contenu lié à l'objet | `common/backpack`, `CSPacketOpenBackpack` |
| **Profil RP** | `/profile` : classification (ANBU, Médical-nin, Déserteur, Sannin…) et métier (longue liste) | `PlayerProfile`, `ProfileSpecifications`, `ProfileCommand` |
| **Cooldowns lisibles** | Cooldowns de jutsu synchronisés au HUD, `/jutsu cooldown clear` | `JutsuCooldowns`, `ClientJutsuCooldowns`, `S2CClearJutsuCooldownsPacket` |
| **Saisonnier** | Quêtes Halloween (citrouilles) et Noël (seulement en décembre, horloge serveur) | `QuestRegistry.registerHalloweenCarvingQuests`, `isDecember()` |
| **Compatibilité** | Iris / Photon / shaders, JEI | `client/compat/*`, 14 mixins client |

Les **+82 clés de lang** correspondent à ces ajouts : `gui.ninshuorigins.guide.*`,
`container.ninshuorigins.armor_table.*` (dont `tool_reusable`, `tool_damage`, `tool_consumed`),
`*.main_clan.*` / `secondary_clan.capstone`, `wrist_smoke_launcher.*`, `gaaras_gourd.*`, `predator_wand.*`, etc.

**Retraits constatés** : `CSJumpSusanno` (cité par l'étude 2.1.7) n'existe plus ; Susanoo passe désormais par la
roue de transformation et `common/utils/SusanooManager`. `Config.java` (boilerplate) a disparu au profit de
`common/config/`.

### 1.3 Points ambigus et corrections de l'étude 2.1.7

| Affirmation 2.1.7 | Vérification 2.1.8 |
|---|---|
| PlayerAnimator embarqué ou transitif, version inconnue | **Non embarqué** : aucune classe `dev/kosmx` dans le jar, rien dans `mods.toml`. C'est une dépendance externe implicite (`PlayerAnimationRegistry` utilisé par `DashHandler`, `DoubleJumpHandler`, `PlayerAnimator`). Les 147 fichiers `player_animation` sont au **format Bedrock/GeckoLib** (`format_version 1.8.0`, os `right_arm`…, interpolation `catmullrom`), pas au format EmoteCraft natif. |
| Coûts et cooldowns non détaillés | Exemples réels : Substitution 10 chakra / 200 ticks ; Rasengan 75 ; Chidori 65 ; Vierge de fer 100 chakra / 45 s ; Gatsūga 50 + 10/s, cooldown 25 s ; invocation 40 + coût du sceau, cooldown 20 s ; Susanoo 50 puis 4/s (cage) ou 100 puis 8/s (squelette). |
| Conditions de Jōgan, Tenseigan, etc. | **Toujours aucune voie en survie** (guide, page `dojutsu`) : objets de map ou de créatif. |
| `Config.java` boilerplate, réglages en dur | Faux en 2.1.8 : config serveur complète (cf. §1.2). Les gamerules `resetCapOnDeath` et `squadFriendlyFire` restent. |
| Garde : `isBlocking`, `BlockTicks`, `GuardBreakCooldown` | `GuardBreakCooldown` est **déclaré mais jamais lu**. L'ancienne garde (`PlayerEvents`, ~l. 586) lit `BlockTicks` en `Long` alors qu'il est déclaré `Integer`, et sa branche « parade parfaite » est du code mort. Le guide l'admet (« type-cast and timing problems »). La vraie garde 2.1.8 est dans `TaijutsuAttackPacket` (cf. §2.8). |
| Seuils Sharingan « 80 genjutsu » | C'est la **valeur de la stat Genjutsu**, pas un nombre de genjutsu lancés (`DojutsuUtils.tryUpgradeSharinganProgress`). Les minutes de Sharingan s'accumulent dès qu'il est débloqué, même éteint (guide). |
| `NinjaRank` dans `entity/ninja` | Déplacé dans `common/data/NinjaRank`. Le rang **affiché du joueur** dépend uniquement de la stat Ninjutsu (E < 25, D 25, C 50, B 75, A 100, S 250, SS 500, Z 1000). |
| Bonus PV « au respawn » via Médical | Immédiat : PV max + Médical / 2, plafonné à +1 000. |
| Points SP/JP/MP/KP | KP est l'**ancien nom de MP**, migration inachevée ; les jetons « KP » donnent du JP (bug documenté). |
| 53 sous-classes `NinshuJutsu` | 89 classes étendent `NinshuJutsu` (59 fichiers). Beaucoup sont des **aperçus inactifs** : Bois, Glace, Cristal, Poussière, Brûlure, et une trentaine de clans pointent sur « Grande Boule de Feu » en attendant. |
| Dimension `pocket` « Kamui probablement » | `PocketRegionSeeder` y copie des régions pré-générées ; usage non tranché. `mount_myoboku` sert à l'invocation inversée, désactivée en 2.1.8. |

---

## 2. Systèmes utiles en RP, approfondis

Faisabilité Reborn : plugins Paper/Purpur (ShinobiCore et addons, serveur autoritaire) + mod client Fabric
`reborn-hud`. **Facile** = un service ou une commande dans un plugin existant ; **moyen** = plugin + écran
`reborn-hud` + canal réseau ; **lourd** = nouveau sous-système, animations et équilibrage.

### 2.1 Quêtes et contrats

- **Côté joueur** : on parle à un ninja PNJ de son village, rang C ou plus (75 % d'entre eux donnent des
  quêtes). Clic simple = contrat, Maj+clic = commerce, clic avec de l'argent = embauche. Le **Parchemin de quête**
  ouvre le journal. Une quête terminée ne paie pas toute seule : il faut cliquer sur « réclamer ».
- **Mécanique** : `QuestRegistry` (25 quêtes codées en dur : 15 contrats permanents, 4 Halloween, 4 Noël
  réservées à décembre, Sage crapaud, Zabuza), `Quests` (id, type `STORY/SEASONAL/KILL/COLLECT/INTERACT/SAGE/TAIJUTSU/BREAK`,
  objectif, objets, XP d'activité, récompense personnalisée). `QuestUtil` : **caution de 100 ryo**
  (`CONTRACT_DEPOSIT`), payée d'abord en liquide puis en banque, rendue à la réclamation, perdue à l'abandon.
  4 quêtes actives au maximum, pas de doublon. Cooldown d'offre par PNJ : 600 ticks (config `questOfferCooldownTicks`).
- **Catalogue** (guide) : tuer 15 zombies (400 ryo), 5 ninjas de la Feuille (1 000 ryo, 100 XP), 50 hostiles
  (800 ryo) ; 100 coups de shuriken ou de sabre ; marcher 100 / 300 / 1 000 / 5 000 blocs ; méditer 300 s ;
  frapper du bois 1 000 fois ; 100 dashs. Bug connu : « déserteurs » compte tout ninja PNJ.
- **Zabuza** : la quête donne des coordonnées ; à moins de 48 blocs, il apparaît ; battre le vrai (pas un clone)
  rapporte 250 ryo et 350 XP ; cooldown de région de deux jours MC s'il meurt.
- **Intérêt RP** : la caution est une très bonne idée (engagement, pas de prise de quête gratuite). Les quêtes de
  Ninshu sont du grind (tuer X), pas du RP ; ce qui se transpose, c'est **le cadre** : tableau de contrats par
  rang, journal, réclamation manuelle, caution, rumeur qui mène à une rencontre scénarisée.
- **Reborn** : rien en jeu ; idées animées par le staff dans `docs/gameplay/MISSIONS.md`.
- **Faisabilité** : moyen. Un `MissionService` (ShinobiCore) avec contrats data-driven en YAML écrits par le staff,
  caution en ryo, journal dans `reborn-hud`, validation par un staff ou par objectif simple. Le RP reste joué par
  le staff ; le système porte la caution, le suivi et la récompense (`XpService.reward`).

### 2.2 Escouades

- **Côté joueur** : `/squad create|invite|accept|decline|invitations|leave|kick|transfer|disband|info|missions|abandon|friendlyfire`.
  Une quête prise en escouade devient **mission partagée** : la progression de chacun s'additionne.
- **Mécanique** : `SquadSavedData` (persisté dans le monde, `ninshuorigins_squads`) : 4 membres max, 4 missions
  actives max, invitation valable 6 000 ticks (5 min). À la création d'une mission, la liste des membres
  **éligibles est figée** ; seul le chef peut abandonner ; chaque éligible réclame sa part ; la caution revient à
  celui qui l'a payée. Les quêtes personnelles (Zabuza, Sage) restent hors escouade (`QuestUtil.isPersonalQuest`).
- **Friendly fire** : `SquadCombatRules.canDamage` remonte au « contrôleur » de toute source de dégâts
  (projectile, invocation, clone, partie d'entité, tag NBT `NinshuSquadController`, profondeur 8) et bloque les
  dégâts entre coéquipiers sauf gamerule `squadFriendlyFire`. Le **feu posé** garde son propriétaire bloc par bloc
  (`SquadFireSavedData`), donc un Katon ne brûle pas l'équipe. Mixins `SquadEffectOwnerMixin`,
  `AreaEffectCloudOwnerMixin`, `EvokerFangsOwnerMixin`, `ProjectileMixin`.
- **Intérêt RP** : fort pour les équipes Genin et les missions encadrées. La remontée « qui contrôle cette
  source de dégâts » est la vraie pépite : sans elle, invocations, clones et zones tuent les alliés.
- **Reborn** : `ShinobiLearning /squad` (équipe Genin + sensei), **en mémoire seulement** (`SquadManager`, perdu au
  redémarrage), pas de mission partagée ni de règle de friendly fire connue.
- **Faisabilité** : facile pour persister les squads et ajouter un friendly fire réglable (Paper :
  `EntityDamageByEntityEvent`, propriétaire via métadonnées sur projectiles et entités de technique) ; moyen pour
  les missions partagées (dépend de 2.1).

### 2.3 Villages : réputation et diplomatie

- **Côté joueur** : réputation par village de -1 000 à +1 000 : Hostile ≤ -300, Méfiant -299 à -101, Neutre -100
  à 199, Amical 200-599 (-10 % au commerce), Honoré ≥ 600 (-25 %). À -600 avec son propre village : **exil**
  (réputation forcée à -1 000). Un gracié peut être réintégré (`PlayerVillageData.pardon`).
- **Barème** (victime / alliés / ennemis) : attaquer -20 / -10 / +5 ; tuer -300 / -150 / +75 ; arracher un œil
  -600 / -300 / +150 ; déserter -400 / -100 / +25. Cooldown de 100 ticks sur les pénalités d'attaque.
- **Entre villages** (`VillageWorldData`) : relation de -100 à +100, hostile ≤ -50, allié ≥ 50, guerres déclarées
  à part (`declareWar`, `makePeace`, `formAlliance`, `endWar`). Valeurs initiales « ère Naruto » : Feuille-Sable
  +60, Feuille-Cascade +55, Feuille-Son -75, Nuage-Pierre -55, etc. Synchronisé aux clients. Aucune commande ne
  l'appelle encore : c'est une API prête.
- **Intérêt RP** : très fort pour Reborn (villages, déserteurs, ANBU, Faction). La réputation mesurable donne
  une conséquence aux actes hors scène ; la matrice diplomatique est exactement ce qu'un staff veut piloter.
- **Reborn** : rien ; le design **Faction Konoha** est en attente (CHANTIERS §1, ❓). À mettre sur la table de
  cette discussion.
- **Faisabilité** : facile côté serveur (deux tables, un barème en config, commandes staff `/village relation`,
  `/village exil|grace`) ; moyen si on l'affiche dans le Poste de garde et la fiche perso. ⚠️ En RP encadré, les
  pénalités automatiques doivent rester désactivables par zone (terrains d'entraînement, scènes validées).

### 2.4 À terre, réanimation, hémorragie, lit médical

- **Côté joueur** : le premier coup mortel est annulé, on tombe à 1 PV, état « Downed » 800 ticks (40 s) ; on
  rampe à 1/4 de vitesse, sans sprint, attaque, taïjutsu, dash ni double saut. Un deuxième coup mortel tue.
  Maintenir « Abandonner » 40 ticks pour mourir tout de suite.
- **Mécanique** (`PlayerEvents`) : `DOWNED_TOTAL_TICKS = 800` ; `recordDowner` mémorise qui a mis à terre ;
  `bleedoutKill` inflige la mort `bled_out` **au nom de cet attaquant** (le kill lui revient). Soins
  (`MedicalReleaseItem`, guide) : auto-réanimation au-dessus de Médical 25 (canal 7 s, 50 chakra, retour à 4 PV),
  réanimer un allié au-dessus de 50 (canal 5 s à 4,5 blocs, 10 chakra, 4 PV), soin de groupe au-dessus de 100
  (40 chakra, 2 PV à 5 blocs). Canal interrompu plus de ~3 ticks : progression perdue.
- **Lit médical** (`MedicalBedRestHandler`) : se coucher **réanime** un joueur à terre (`revivePlayer`), soigne
  `healAmount` (1 PV) toutes les `healIntervalTicks` (20) ; accélère les blessures des Portes (×4), le blocage
  de chakra du Poing souple, la cicatrisation d'œil et le saignement Inuzuka. Refusé si une Porte est ouverte.
  Lit unique par joueur, gratuit, ne change ni le spawn ni l'heure.
- **Intérêt RP** : Reborn est déjà plus riche (à terre 45 s, inconscient 5 min, hôpital, ATA, RPK). À reprendre :
  l'**attribution de la mort** à celui qui a mis à terre (journal staff, RPK), la **réinitialisation sur
  interruption** des soins (oblige à sécuriser la zone), et le lit qui **accélère** des blessures persistantes.
- **Reborn** : `docs/gameplay/KO_ET_MORT_RP.md` (KO-1 à KO-7), lits et hôpitaux ✅.
- **Faisabilité** : facile pour ces trois détails.

### 2.5 Économie : ryo, ATM, activité quotidienne, embauche

- **Côté joueur** : pièces et billets de 100, 500, 1 000, 2 000 et 5 000 ryo ; un ATM (craftable) pour déposer
  et retirer par multiples de 100 ; un portefeuille grenouille (12 cases visibles) ; une carte bancaire pour
  payer depuis la banque. Les paiements rendent la monnaie exacte, les centimes restent en banque.
- **Mécanique** : `common/economy/CurrencyService` (`BANK_BALANCE_KEY = "Ryo"`, `SMALLEST_CASH_VALUE = 100`,
  `tryCharge` avec source `WALLET_THEN_BANK`, `makeChange`), `ATMBlockEntity`, `ATMActionPacket`.
- **Activité quotidienne** : `ActivityXP` plafonnée à 100, alimentée par les quêtes ; paliers réclamables à 55
  (5 000 ryo en banque) et 100 (10 000 ryo) dans `C2SClaimActivityRewardPacket`. Remise à zéro **au login** si la
  date locale du serveur a changé (`PlayerEvents.onPlayerLoggedIn`, `LastActivityReset`) : un joueur connecté
  à minuit garde sa journée.
- **Embauche** : PNJ engagé un jour MC (`hireDurationTicks` 24 000) ; prix de base E 100, D 250, C 600,
  B 1 500, A 3 500, S 7 500, SS 15 000, Z 30 000, majorés selon clan, natures, jutsu, lignée, dōjutsu, arrondis
  à la centaine. Commerce avec PNJ de son village si réputation ≥ Neutre (+5 de réputation par échange).
- **Intérêt RP** : la banque séparée de l'argent liquide est utile (vol, fouille d'un corps à terre, trésor de
  faction). L'activité quotidienne est un levier de rétention, mais 15 000 ryo/jour casse toute économie RP : à
  ne pas copier tel quel.
- **Reborn** : ryo (gagné en jeu) + RBCoins (achat), sans conversion (ADR 0006) ; `/ryo` (solde, staff
  give/take/set) ; trésor commun prévu dans Faction. Pas de banque ni d'argent physique.
- **Faisabilité** : moyen. Distinguer « bourse » (objet, fouillable, volable) et « compte » (banque de village,
  guichet PNJ ou bloc) dans ShinobiCore ; le trésor de faction devient un compte comme un autre.

### 2.6 Guide en jeu

- **Côté joueur** : un livre double page avec sommaire, liens internes, retour arrière, navigation au clavier,
  objets survolables (vraie infobulle), touches affichées selon les **raccourcis actuels** du joueur, et un
  **statut par page** (Working, Partly Done, Unfinished, Creative/Map, Admin Only).
- **Mécanique** : `guide/en_us.json` = `chapters[]` (14) + `pages[]` (78), chaque page = liste de blocs
  `paragraph` (74), `bullets` (80), `callout` avec ton info/tip/warning/danger/wip (72), `heading`, `links`,
  `items`, `keys`, `toc`. Statuts : 40 « available », 24 « partial », 7 « wip », 4 « creative_only »,
  2 « admin_only ». Rendu par `GuideBookScreen` (749 lignes) et `GuideContent` ; ouvert par
  `S2COpenGuideBook`.
- **Intérêt RP** : excellent pour l'onboarding d'un serveur encadré. Le statut par page évite les tickets
  « ça marche pas » et dit clairement ce qui est staff-only. Les pages « problèmes connus » sont honnêtes.
- **Reborn** : pas de guide en jeu ; wiki web (module `wiki` de l'API, `apps/admin` wiki) ; livre d'encyclopédie
  médicale RP (`ShinobiCore/medic/Encyclopedia`).
- **Faisabilité** : moyen. Un écran `reborn-hud` qui lit un JSON servi par le serveur (ou par l'API wiki), mêmes
  blocs typés, DA Reborn. Bonus : générer les pages depuis le wiki pour une seule source.

### 2.7 Table d'armure (fabrication des tenues)

- **Côté joueur** : un atelier de 3×2 blocs. Grille de **5×5 matériaux** (cases 0 à 24), emplacements
  d'**outils** (ciseaux, cisailles, pinceau, pioche en fer), aperçu 3D de la pièce sur son personnage
  (rotation, déplacement, zoom), livre de patrons qui ne cache pas l'inventaire.
- **Mécanique** : recettes JSON `ninshuorigins:armor_table` : `group`, `materials[]` (`slot`, `ingredient`,
  `count`, **`color`**), `tools[]` (réutilisable, perd X de durabilité, ou consommé), `result`. 213 recettes :
  vêtements 94, renforcés 47, masques 21, plaques 14, chapeaux 10, bandeaux 10, robes 9, capuches 5, lunettes 2.
  Couleurs les plus demandées : noir, blanc, gris, rouge, bleu. Chaîne amont : fil (ficelle + bâton), tissu
  (2×2 bobines de même couleur), tissu rembourré (laine), bandes de cuir (1 cuir → 4), doublure d'acier (1 lingot
  → 3), tous teignables. Matières totales sur les 213 recettes : tissu 963, rembourré 453, cuir 409, fil 184.
- **Intérêt RP** : fort. Fabriquer une tenue de village ou d'ANBU devient un **métier** (couturier, armurier) et
  un échange entre joueurs, au lieu d'un clic en boutique. Les tenues de Kage restent attribuées par le staff.
- **Reborn** : tenues via la sacoche RP et la création de perso (`INVENTAIRE_ET_COSMETIQUES.md`, Nexo) ;
  aucune fabrication.
- **Faisabilité** : moyen. Serveur : recettes YAML avec couleur par case et outils à usure (Paper, inventaire
  custom). Client : l'aperçu 3D sur le joueur existe déjà dans l'esprit (création de perso en scène).

### 2.8 Taïjutsu et kenjutsu : chiffres réels

**Coups de base** (`TaijutsuAttackPacket`, `PlayerEvents`) :

| Coup | Entrée | Dégâts de base | Endurance | Cooldown de base | Hauteur |
|---|---|---|---|---|---|
| Poing | clic gauche | 2,0 | 4 | 20 ticks | haute (basse si accroupi) |
| Uppercut | clic gauche accroupi | 2,0 (lourd) | 7 | 20 ticks | haute |
| Coup de pied | clic droit | 4,0 (lourd) | 9 | 45 ticks | **basse** |

Multiplicateurs : Puissance (+10 %/niveau en taïjutsu ; +0,4 %/niveau plafonné à 300 en kenjutsu, plafond ×28),
état de bijū (×1,2 à ×1,85), Portes (×1,15 à ×5), Sage (×1,6 à ×2,5), Poing souple, mode Ébullition, Poing
rapide (cooldown ×0,65). En kenjutsu, la base est le dégât de l'arme, ×1,2 pour un coup lourd.

**Sous-stats** (`CombatProficiency`, par discipline Taï / Ken) : **Puissance** (dégâts), **Forme** (cooldown
-0,05 tick/niveau, plancher 3 ticks), **Jeu de jambes** (rapprochement vers la cible +0,1 %/niveau, plafond 500,
ne rallonge pas l'allonge), **Condition** (coûts d'endurance / (1 + 0,5 %/niveau)). Entraînement : frapper des
blocs selon leur dureté (dégâts aux mains si trop dur : kenjutsu < 20, < 90, < 350 selon le bloc),
mannequin, développé-couché (fenêtres de rythme), entraîneur de vitesse (cycle 32 ticks, parfait 3, bon 7).

**Garde et parade** :
- Garde = touche maintenue, draine 1 endurance / 12 ticks (allongé par Condition).
- Garde **haute ou basse** : debout = haute, accroupi = basse ; le coup de pied frappe bas. Mauvaise hauteur =
  garde ratée (« Crouch to block low »).
- **Parade parfaite kenjutsu** : arme en main, bonne hauteur, garde levée depuis **≤ 6 ticks**. Effet :
  attaquant étourdi 8 ticks et repoussé, défenseur +2 XP kenjutsu.
- **Garde brisée** (mauvais timing, mauvaise hauteur ou pas d'arme) : défenseur étourdi 45 ticks (70 sur coup
  lourd) + Lenteur II et Faiblesse.
- **Clash** : deux joueurs qui lancent le même type de coup (même animation) à moins de 8 ticks et 4,25 blocs
  s'entrechoquent et se figent 20 ticks.

**Prises** (`TaijutsuGrabHandler`) : portée 3,25, cooldown 80 ticks, 8 endurance. Le type dépend du **clan** :
projection (24 ticks, 6 dégâts, défaut), écrasement au sol (Akimichi, 34 ticks, 10), chokeslam (Haruno,
42 ticks, 8, casse le sol au-delà de taïjutsu 250), drain de chakra (Akado, 60 ticks). **Contre-prise** : si
les deux saisissent à ±3 ticks, la prise est cassée et les deux sont repoussés.

**Finisher** : par **derrière** (produit scalaire ≤ -0,55), à 3 blocs ; à mains nues si la cible est à ≤ 35 %
de PV ; avec arme si son kenjutsu dépasse celui de la cible. Charge 15 ticks, animation couplée
attaquant / victime de 40 ticks, cooldown 240 ticks.

**Huit Portes** (`EightGatesHandler`) : porte N accessible au taïjutsu 2 / 4 / 7 / 10 / 15 / 22 / 30 / 40 ;
charge 1 s à 1/4 de vitesse ; +20 % à +260 % de vitesse, ×1,15 à ×5 dégâts, drain 0,5 % à 5 % d'endurance
max par seconde. Fermer après les Portes 3 à 7 laisse une **blessure persistante** (-10 % à -40 % de vitesse,
2 à 16 min, survit à la déconnexion, ×4 au lit médical). Porte 8 : 60 s puis **mort certaine**, irréversible.

- **Intérêt RP** : le duel devient lisible et technique (lire la hauteur, timer, punir), ce que Reborn vise
  avec son combat « fluide ». Le finisher par derrière et la prise par clan donnent une identité de clan au
  corps à corps. Les blessures des Portes sont du RP pur : un prix visible et durable.
- **Reborn** : ShinobiCombat a un combo M1 de 4 coups (3 / 4 / 6 / 8), garde -70 % avec drain 25 d'endurance,
  guard break à endurance nulle (stun 20 ticks), parade timée 320 ms façon Sekiro. **Manquent** : hauteur de
  garde, clash, prises, finisher arrière, sous-stats.
- **Faisabilité** : garde haute/basse + clash = **facile** (deux comparaisons dans `CombatListener`, le client
  envoie déjà l'état accroupi) ; prises et finisher = **lourd** (animations couplées, verrouillage de position,
  caméra) ; sous-stats = moyen (s'articule mal avec les 6 stats Reborn : à traiter comme maîtrise cachée).

### 2.9 Kawarimi (Substitution)

- **Côté joueur** : touche dédiée, indépendante de la Release choisie ; téléportation jusqu'à ~20 blocs dans la
  direction regardée, bûche animée et fumée à l'endroit quitté ; icône de recharge au HUD.
- **Mécanique** (`CSPacketSubJutsuPressed`) : déblocage à Ninjutsu 10 pour 5 JP ; 10 chakra ; cooldown 200 ticks ;
  point d'arrivée sûr calculé par le serveur (raccourci si mur) ; bloqué si piégé par l'ombre Nara, Gatsūga ou
  le rituel. Pas de garde-fou « à terre » visible dans ce handler, contrairement à ce que laisse entendre le guide.
- **Intérêt RP** : moyen. C'est une esquive universelle bon marché ; en RP encadré, elle doit coûter plus et
  laisser une trace (bûche ramassable comme indice).
- **Reborn** : `kawarimi` existe dans `ShinobiAbilities/JutsuEffectRegistry` (recul + leurre) ; listé P1 dans
  `ANIMATIONS_BASE.md`.
- **Faisabilité** : facile (ajuster distance, coût, cooldown, interdiction à terre).

### 2.10 ADN de clan

- **Côté joueur** : le clan principal donne stats de départ et affinités. Un ADN implanté (seringue, 0,7 s) donne
  la Release d'un autre clan sans ses stats. **3 ADN de clan maximum** (le plus récent est remplacé). On peut
  choisir son **clan principal** parmi ceux possédés ; la **dernière technique** d'un clan n'est utilisable que
  s'il est principal.
- **Mécanique** : `DNA_Base.MAX_CLAN_DNA = 3`, `C2SSetMainClanPacket`, `Release_Base.getClanJutsuAccess` /
  `isClanCapstone`. Source d'ADN : cellules lâchées par les ninjas PNJ (20 % en rang E à 60 % en Z), Éprouvette
  en 3 étapes (~10 s chacune, combustible), extraction **à risque** : 12 % de réussite pour un œil, 20 % pour un
  clan ou une Release, améliorée par la stat Médical ; un échec efface l'échantillon. ADN de nature : refuse les
  doublons.
- **Intérêt RP** : fort, à condition de le **réserver au staff** (expériences d'Orochimaru, Kabuto, Danzō). Le
  principe « lignée principale + techniques ultimes verrouillées » est sain : il empêche le cumul des meilleures
  techniques de plusieurs clans.
- **Reborn** : clans fixés à la création (11 entrées, verrous village / sexe, candidature) ; pas d'ADN.
- **Faisabilité** : moyen (un champ `lignees` sur le personnage, commande staff d'implant, verrou des
  techniques de rang S au clan principal dans le catalogue de techniques).

### 2.11 Dōjutsu

- **Œil par œil** : `Left_Eye` / `Right_Eye`, implant via écran de choix d'orbite (annuler rend l'œil). Drain
  par œil et par seconde (guide) : Sharingan 6 / 4 / 2 selon tomoe, Mangekyō 20, EMS 8, Rinnegan 10, Byakugan 5,
  Tenseigan 12, Shion 3, Yome 3, Ketsuryūgan 5 ; deux yeux = double coût.
- **Éveil naturel** : Uchiha, 5 MP pour le 1er tomoe ; évolution vérifiée **au kill suivant** si kills, stat
  Genjutsu et minutes atteintes (Uchiha 8/80/30, 75/300/75, 150/750/150 ; non-Uchiha 10/100/35, 100/450/90,
  400/1000/300). EMS : deux Mangekyō **d'un même autre joueur** (UUID du donneur stocké dans l'œil). Hyūga :
  Byakugan pour 30 MP (le message dit JP, bug). Chinoike : 30 JP.
- **Arracher un œil** : viser la tête à ≤ 4,5 blocs, maintenir 12 ticks, puis duel de pressions répétées
  (Médical de l'attaquant contre résistance du défenseur) ; la victime prend Cécité 20 s. Bandage oculaire pour
  cicatriser. Réputation -600 avec le village de la victime.
- **Susanoo** : cage (Ninjutsu 150, Genjutsu 750) ou squelette (300 / 1000, motif de Sasuke seulement) ;
  incompatible avec les Portes (`TransformationConflictPolicy`).
- **Intérêt RP** : le vol d'œil est un énorme moteur de scènes (trahison, chasse, greffe clandestine), mais il
  doit passer par une validation staff, comme la RPK chez Reborn. La progression par métriques est trop
  « farm de kills » pour un RP encadré.
- **Reborn** : Byakugan « comme dans l'anime » (ShinobiSense) ✅ ; iris Sharingan / Byakugan / Rinnegan en
  assets wave 2.
- **Faisabilité** : yeux séparés + greffe validée par le staff = moyen ; arrachage en QTE = lourd (animation
  couplée, à rapprocher du menu « agir sur un corps » de KO-6).

### 2.12 Modes et transformations

- **Roue de transformation** (`common/gui/wheel`, `WheelCodeExecutor`) : pages Portes, Susanoo, marque maudite,
  formes de bijū, sac à dos. `TransformationConflictPolicy` empêche les cumuls (Portes + Susanoo, ombre Nara
  active + autre jutsu).
- **Mode ermite** : énergie naturelle séparée (5 × Senjutsu, recharge 0,5 % du max par seconde en méditant),
  drain 2/s ; Crapaud : +65 % d'attaque, +20 PV, taïjutsu ×2,15 ; Serpent ×1,6 ; Bois ×2,0.
- **Marque maudite** : morsure d'Orochimaru (variante aléatoire, niveau 1 forcé 20 s), maîtrise (niveau × 25
  + 25 XP), niveau 1 contrôlé dès maîtrise 5 (30 + 5 × maîtrise secondes, 5 chakra/s), niveau 2 après le
  **rituel des Quatre Brumes Noires** : 5 minutes enfermé, écran noir, Orochimaru garde le réceptacle
  (PV et attaque ×2), d'autres joueurs peuvent briser le réceptacle pour interrompre. Éveil de secours
  automatique sous 3 PV, cooldown 1 min et moitié des PV.
- **Bijū** : 9 boss persistants, uniques, apparus à 1 200-3 000 blocs d'un joueur ; scellement Uzumaki (50 JP,
  canal 10 s) ; maîtrise de forme 1 à 8 par seuils.
- **Intérêt RP** : le **rituel de 5 minutes interruptible par d'autres joueurs** est un modèle de scène PvP
  parfait (gardes, intrus, enjeu). Les blessures persistantes des Portes aussi (§2.8).
- **Reborn** : pas de transformations codées ; techniques via le Technique Creator.
- **Faisabilité** : moyen pour un « mode actif » générique (état serveur + modificateurs + FX + garde-fou de
  conflits) ; lourd pour les formes visuelles (Model Engine).

### 2.13 PNJ nommés et rencontres

- `NamedNpcSpawnConfig` : tentative toutes les 600 ticks par joueur éligible ; Jiraiya 25 % de jour près d'un
  village habité, Orochimaru 17,5 % la nuit en forêt ; 32-96 blocs du joueur ; espacement 200 blocs entre
  doublons ; cooldown de région 48 000 ticks après leur mort ; message d'ambiance à 200 blocs ; tout est
  réglable et désactivable.
- **Intérêt RP** : pour Reborn, ces PNJ doivent rester joués par le staff. L'idée utile est le **message
  d'ambiance** (« une présence se fait sentir ») et le **cooldown de région** pour des événements staff.

---

## 3. Modèles et animations

### 3.1 Inventaire

| Catégorie | Nombre | Détail | Format |
|---|---|---|---|
| Tenues et armures | 111 geo | Kage (Hokage, Kazekage, Mizukage, Raikage, Tsuchikage, Amekage, Hoshikage, Yukikage), jōnin par village (Nuage, Brume, Pluie, Sable, Neige, Étoile, Pierre), ANBU (+ masques), Akatsuki, persos (Kakashi, Jiraiya, Zabuza, Haku, Kimimaro, Mifune, Gaï, Tsunade…), formes de mode (Baryon Kurama / Shukaku / Chōmei / Saiken, papillon Akimichi, marques maudites niveau 2), costumes d'événement | GeckoLib armure, texture 64×64, os `armorBody` / `armorLeftArm`… + **pans de tissu animés** (ex. robe Akatsuki : 11 os dont 5 `backflap` et `collar`) |
| Animations de tenues | ~104 | essentiellement `idle`, quelques `walk`, `run`, `idlecrouch` | GeckoLib `.animation.json` |
| Coiffures | 20 geo | Naruto, Sasuke, Sakura, Kakashi, Itachi, Madara, Minato, Obito, Tsunade, Temari, Tenten, Ino, Shikamaru, Kiba, Chōji, Mei, Hashirama, Indra, Asura | GeckoLib, calque masquable |
| Armes | 76 geo, 54 anims | 7 sabres de la Brume, Kusanagi, éventails, bâtons des Six Voies, faux, kunai, shuriken (géant, Fuma, Kamui), lance rétractable, lanceur de fumigènes | GeckoLib item |
| Objets 3D | 24 + 5 monnaies | ramen, dango, onigiri, parchemins (invocation, ermite), seringue, livres de Kakashi, clochettes, portefeuille grenouille, billets | GeckoLib item |
| Invocations | 23 geo, 22 anims | famille crapaud (Gamabunta, Gamakichi, Gamahiro, Gamaken…), limace, salamandre, Akamaru, corbeau, trois serpents, Vierge de fer, oiseau d'encre, bûche de substitution | GeckoLib entité ; anims `idle`, `walk`, `death`, `spit`, `slam`… |
| Bijū | 9 geo, 9 anims | Shukaku → Kurama | GeckoLib entité |
| Susanoo | 3 | générique, Sasuke | GeckoLib entité |
| Entités de jutsu | 24 geo, 13 anims | dragons, os Kaguya, sceaux, orbe de vérité | GeckoLib |
| PNJ nommés | 13 | Danzō, Itachi, Jiraiya, Kakashi, Killer Bee, Naruto, Orochimaru, Pain, Tsunade, Obito, Zetsu, Zabuza, Nuiba | GeckoLib |
| Décors et blocs | 29 geo, 18 anims | ATM, lit médical, table d'armure, banc de musculation, entraîneur de vitesse, râteliers de katana, cuisine, mini-citrouilles, plant de riz, réceptacle des Quatre Brumes | GeckoLib bloc |
| Animations joueur | 147 | locomotion (dashs, double saut, course Naruto, ramper, nager), combat (poings, pieds, uppercut, garde, garde à l'envers, sabre, Kubikiribōchō, Poing souple, Crapaud, Inuzuka, os), finishers couplés (`finisher_aggressor` / `finisher_victim`, `weapon_finisher(_victim)`), jutsu, soins, implant d'ADN, arrachage d'œil, entraînement (pompes, tractions, abdos, développé-couché), 7 postures `ninjastance` | **Bedrock / GeckoLib** `format_version 1.8.0`, lu par PlayerAnimator (kosmx) |
| Modèles JSON | 887 | 701 items 2D, 186 blocs | vanilla |
| Textures | 2 059 | dont 282 icônes de Release, 72 dōjutsu, 30 marques, 154 armures | PNG |

⚠️ Les animations de prise (`taijutsu_grab_throw`, `_slam`, `_chokeslam`, `_drain`, `_break`) sont envoyées par
le serveur mais **aucun fichier** de ce nom n'existe dans `player_animation/` : elles ne jouent probablement rien.

### 3.2 Ce qui peut servir de référence à Reborn

- **Découpage des tenues pour Model Engine / bbforge** : une tenue = os du corps + **os de pans séparés**
  (devant, derrière, col) animés en `idle` / `walk` / `run`. C'est la bonne granularité pour les capes et robes
  Reborn (Uchiha, Kage, ANBU), et le passage 64×64 reste lisible.
- **Coiffures en calque séparé**, masquable par une touche ou par un couvre-chef : utile pour la wave 2
  (« 10 coupes de cheveux ») et pour éviter les conflits coiffure / bandeau / capuche.
- **Paires d'animations couplées** attaquant / victime avec verrouillage de position : modèle pour les
  **exécutions** encore à créer (KO-7) et pour une future prise de taïjutsu.
- **Catalogue d'animations joueur** : la liste ci-dessus peut servir de grille de contrôle face à
  `docs/gameplay/ANIMATIONS_BASE.md` (gardes basses, garde à l'envers, ramper, Poing souple, postures).
  Reborn joue ses emotes via EmoteCraft, qui s'appuie sur la même bibliothèque kosmx : le format Bedrock est
  compatible, mais on recrée nos animations (droits).
- **Invocations et bijū** : conventions de nommage d'animations (`idle`, `walk`, `death`, `spit`, `slam`, `summon`)
  et nombre d'os pour une créature lisible : comparer avec Son Gokū (bbforge, 29 os).
- **Mobilier fonctionnel** (lit médical, ATM, table d'armure, banc) : idée de blocs 3D à usage RP pour Konoha
  (hôpital, banque, atelier de couture, dojo).

---

## 4. Tableau de synthèse

| Idée | Ce que fait Ninshu | Reborn a déjà ? | Valeur RP | Effort |
|---|---|---|---|---|
| Garde haute / basse | Accroupi = garde basse ; mauvaise hauteur = garde brisée | Non (garde unique + parade 320 ms) | ★★★ | Facile |
| Clash de coups identiques | Même coup à ≤ 8 ticks et 4,25 blocs → choc et gel 20 ticks | Non | ★★ | Facile |
| Garde brisée qui punit | Stun 45 / 70 ticks + Lenteur + Faiblesse | Partiel (stun 20 ticks à endurance nulle) | ★★ | Facile |
| Prise selon le clan | Projection, écrasement Akimichi, chokeslam Haruno, drain Akado ; contre-prise à ±3 ticks | Non | ★★★ | Lourd |
| Finisher par derrière | Dos tourné, ≤ 35 % PV ou kenjutsu supérieur, anim couplée, cooldown 240 ticks | Non (exécutions KO-7 à animer) | ★★ | Lourd |
| Sous-stats de maîtrise | Puissance / Forme / Jeu de jambes / Condition entraînées en frappant, mannequin, banc | Non (6 stats + XP invisible) | ★★ | Moyen |
| Huit Portes à blessures persistantes | Paliers par niveau de taïjutsu, blessure 2-16 min, Porte 8 mortelle | Non | ★★★ | Moyen |
| Missions à caution | 100 ryo bloqués, rendus à la réclamation, perdus à l'abandon ; 4 actives | Non (missions animées à la main) | ★★★ | Moyen |
| Missions d'escouade partagées | Progression commune, éligibles figés, chef seul abandonne | Non | ★★ | Moyen |
| Escouades persistées + friendly fire | `SavedData`, FF par gamerule, propriétaire remonté sur toute source | Partiel (`/squad` en mémoire) | ★★ | Facile |
| Réputation de village et exil | -1 000 à +1 000, paliers, barème attaque / meurtre / œil / désertion | Non | ★★★ | Facile à moyen |
| Diplomatie entre villages | Matrice de relations, guerre, alliance, valeurs canon | Non (design Faction ❓) | ★★★ | Facile |
| Bourse et banque séparées | Liquide physique + compte (ATM, carte), monnaie exacte | Partiel (`/ryo`, sans banque) | ★★ | Moyen |
| Bonus d'activité quotidienne | Paliers 55 / 100 → 5 000 / 10 000 ryo | Non | ★ | Facile |
| Embauche de PNJ | Prix par rang, 1 jour MC | Non | ★ | Moyen |
| Guide en jeu data-driven à statuts | 78 pages, blocs typés, touches réelles, statut par page | Non (wiki web) | ★★★ | Moyen |
| Table de couture | 5×5 + outils + couleurs, 213 patrons, aperçu 3D | Non (tenues en sacoche / création) | ★★★ | Moyen |
| Clan principal + technique ultime réservée | 3 ADN max, capstone verrouillé hors clan principal | Non | ★★ | Moyen |
| Greffe et vol d'œil | Yeux G/D séparés, implant transactionnel, arrachage en QTE | Non | ★★★ | Moyen à lourd |
| Mort créditée à qui a mis à terre | `recordDowner` → mort `bled_out` attribuée | Partiel (KO riche) | ★★ | Facile |
| Soin interrompu = reset | Canal perdu après ~3 ticks d'interruption | À vérifier | ★★ | Facile |
| Lit qui accélère les blessures | ×4 sur Portes, œil, saignement | Partiel (lits ✅) | ★★ | Facile |
| Rituel interruptible | 5 min enfermé, gardé, brisable par d'autres joueurs | Non | ★★★ | Moyen |
| Kawarimi | 10 chakra, 200 ticks, ~20 blocs, bûche | Oui (`kawarimi` dans ShinobiAbilities) | ★★ | Facile (réglages) |
| Message d'ambiance et cooldown de région | Présence annoncée à 200 blocs, 2 jours MC entre rencontres | Non | ★★ | Facile |
| Profil RP (classification, métier) | `/profile` avec listes canoniques | Partiel (fiche perso) | ★ | Facile |
| Tenues à pans animés | 111 geo, os de pans + idle / walk / run | Partiel (bbforge, Model Engine non intégré) | ★★★ | Moyen |
| Coiffures en calque | 20 geo, touche de masquage | Non (wave 2 prévue) | ★★ | Moyen |
| Entraînement à rythme | Fenêtres parfait 3 / bon 7 ticks sur cycle 32 | Non | ★★ | Moyen |

**Ordre conseillé** : garde haute/basse + clash (facile, gros gain de lisibilité) ; réputation et diplomatie à
apporter à la discussion Faction ; caution + journal de missions ; guide en jeu ; table de couture ; puis prises
et finishers une fois les animations d'exécution produites.
