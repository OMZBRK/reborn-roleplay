# Journal des décisions

> **Toutes les décisions de design et d'organisation prises par l'utilisateur**, datées, une ligne chacune, avec
> le lien vers la fiche qui l'applique. Sert à ne pas rouvrir une question tranchée et à savoir **quelle règle a
> remplacé laquelle**. Les décisions techniques structurantes ont en plus un ADR dans [`../adr/`](../adr/).
>
> Une décision remplacée est barrée et renvoie à celle qui la remplace. Consolidé le 2026-10-12 à partir de
> toutes les sessions de travail.

---

## Organisation et méthode de travail

| Date | Décision |
|---|---|
| 2026-09-08 | Hiérarchie des sources de vérité : le **code** fait foi, puis `ETAT_DES_LIEUX.md` (présent), `ROADMAP_BETA_2026.md` (futur) ; les boards suivent la roadmap |
| 2026-09-08 | **Publier implique commiter** : ce qui tourne en prod ou sur le dev revient sur `origin/main` dans la foulée |
| 2026-09-08 | **Deux trains de release jamais mélangés** dans un commit : jeu (mods + plugins) / web (API, panel, bot) — [ADR 0003](../adr/0003-shinobi-dans-le-monorepo-deux-trains-de-release.md) |
| 2026-09-17 | Dépôt « le plus clear et clean possible », transmissible entre PC : `main` = tronc unique, branches courtes, **pas de backups** dans le gestionnaire de fichiers staff |
| 2026-09-17 | Le Trello est **public** : jamais d'IP, de secret ni de chemin du VPS dessus |
| 2026-10-03 | Des **maquettes avant le code**, plusieurs séries si besoin |
| 2026-10-06 | **Claude ne pousse jamais** sur GitHub : le push part de la session terminal de l'utilisateur, avec son token, sur son instruction |
| 2026-10-06 | Accès SFTP : jamais écrits en clair ; mot de passe du dev rangé dans le gestionnaire d'identification Windows (`reborn-sftp-dev`) ; **le redémarrage du serveur est toujours fait par l'utilisateur** |
| 2026-10-06 | Un commit `feat` + un commit `docs(etat)` séparé pour l'état des lieux ; ne jamais toucher aux `config.yml` du serveur sans demande |
| 2026-10-10 | **Montrer les visuels avant chaque prompt de publication** (captures du client de dev) |
| 2026-10-10 | **Finir le Sprint 1 avant d'ouvrir le Sprint 2** |
| 2026-10-11 | Valider `plugin.yml` / `config.yml` avec un vrai parseur YAML avant toute publication de plugin (incident ShinobiCore) |
| 2026-10-12 | Documentation restructurée pour qu'une session reprenne seule : [`../REPRISE_DE_SESSION.md`](../REPRISE_DE_SESSION.md) |

## Planning

| Date | Décision |
|---|---|
| 2026-09-08 | Beta jouable le **31 décembre 2026**, 4 sprints mensuels, 16 chantiers — [`../ROADMAP_BETA_2026.md`](../ROADMAP_BETA_2026.md) |
| 2026-09-08 | Périmètre combat beta : **39 techniques** (12 Tai, 12 Ken, 15 Nin rangs D-C-B) ; rangs A et S en cut list |
| 2026-09-17 | **18 sorts claniques** (9 clans × 2 ; ni Neutre ni Autre) — question close |
| 2026-09-17 | Semaine S1 dérivée (Technique Creator) : on **absorbe**, le ship reste au 31/12 ; ordre de coupe Nin B → Nin C → sorts claniques |

## Identité visuelle

| Date | Décision |
|---|---|
| 2026-06-21 | Identité **Akatsuki** (crimson `#A0182B` + or `#D9A95E`) — remplace le « bleu v2 » |
| 2026-10-01 | Retirer toute référence à **Zenkai** ; colonne `zkCoinBalance` → `rbCoinBalance` |
| 2026-10-03 | Kit commun nuit / laque / or, **une signature (objet) par écran**, jamais celle d'un autre ; sons dédiés — [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md) |
| 2026-10-03 | Écrans en jeu : le jeu visible derrière un voile ; création de perso : jamais le monde du jeu ; pixel art détaillé, pas de fonds animés lourds |
| 2026-10-08 | HUD vitals de base **inchangé** ; HUD combat en version discrète ; pas d'effets « RPG » |
| 2026-10-10 | Menus Reborn à la **taille de l'échelle 2 en 1080p** quelle que soit l'échelle du joueur — [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §3 |
| 2026-10-12 | On **reste en pixel art** (pas le rendu « web » HD de Zenkai / GMod), mais **léger et optimisé** : décors et cadres en textures pré-dessinées, code seulement pour ce qui bouge — [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md) §6 |
| 2026-10-13 | **Fiche shinobi** (remplace l'écran de stats) : design retenu « **Le portrait en scène** » v2 (personnage réel au centre, panneaux laqués suspendus, lanternes en arc, onglets Identité / Attributs / Techniques / État) — « très très beau… vraiment bien travaillé visuellement » ; maquettes hors dépôt dans `Maquettes/fiche-shinobi/v2/portrait/` |
| 2026-10-13 | **HUD en jeu subtil** : KO, duel, alertes et overlays en jeu = petits, fins, épurés, jamais de grosses plaques (les plaques laquées restent aux menus plein écran) — [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md) §6 règle 9 |

## Monnaies

| Date | Décision |
|---|---|
| 2026-09-08 | **Ryo** (gagné en jeu) + **RBCoins** (acheté ou Bug Bounty), sans conversion — [ADR 0006](../adr/0006-monnaies-ryo-et-rbcoins.md) |
| 2026-10-03 | Les récompenses d'activation (boost Discord, pseudo, bio, stream…) rapportent des **RBCoins**, pas des ryo |

## Stats et progression — [`../gameplay/STATS_ET_PROGRESSION.md`](../gameplay/STATS_ET_PROGRESSION.md)

| Date | Décision |
|---|---|
| ~~2026-09-17~~ | ~~Voie A : 5 stats du plan, chakra plafonné à 200~~ → remplacée le 23/09 |
| 2026-09-23 | **Option B : 6 stats** (Taïjutsu, Kenjutsu, Ninjutsu, Contrôle, Vigueur, Chakra) ; genjutsu hors beta |
| 2026-09-23 | **Aucune stat ne ferme une porte RP** ; **apprentissage des techniques libre** (pas de `requires:` sur une stat) |
| 2026-09-23 | **Grande échelle de chakra** : ~100 000 au maximum, un rang S se lance 2 à 3 fois |
| 2026-09-29 | Infobulles **sans chiffres exacts** ; réinitialisation par **jetons liés au compte** |
| ~~2026-09-23~~ | ~~3 points de stats par passage de grade~~ → remplacée le 10/10 |
| 2026-10-08 | **9 grades** : Académicien, Genin, Genin confirmé, Chūnin, **Konin**, Tokubetsu Jōnin, Jōnin, Commandant Jōnin, Kage ; **on ne monte pas de grade à l'XP** ; passage **RP** (`/grade`, droits selon son grade) |
| 2026-10-08 | ANBU, bras droit, conseil… sont des **fonctions RP**, pas des grades (avantages, pas de stats) |
| 2026-10-10 | Progression à l'**XP** en dehors des grades : **1 niveau = 1 point de stats** |
| 2026-10-10 | Points de grade **séparés** des stats et renommés **Mérite** (usage gameplay à venir) |
| 2026-10-10 | **Niveau et XP invisibles** pour le joueur (ni affichage, ni annonce) ; `/xp` réservé au staff |

## KO, médical et mort RP — [`../gameplay/KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md)

| Date | Décision |
|---|---|
| 2026-10-06 | But : **tuer le combat d'arène** ; combat libre seulement sur les terrains d'entraînement ; ailleurs, PV perdus persistants, sortie par médic ou repos RP |
| 2026-10-06 | Plancher sans médic **30 %** ; frais d'hôpital **en RP** ; mobs = MythicMobs uniquement ; **NLR laissé au joueur** |
| 2026-10-06 | Soin médical **tenu dans le temps**, plus rapide selon le Contrôle et le rang |
| 2026-10-07 | Hôpitaux = zones, lits = **meubles Nexo**, nombre illimité ; pas d'onglet Historique (le journal suffit) |
| ~~2026-10-06~~ | ~~Défaite à l'entraînement = relève à 30 %~~ → **PV pleins** (10/10) |
| ~~2026-10-06~~ | ~~`/hopital` proposé après 2 min, rapatriement automatique à 5 min~~ → **5 min d'inconscience, puis il reste au sol et `/hopital` est proposé** (10/10) |
| ~~2026-10-06~~ | ~~ATA : techniques rang D maximum ; FearRP 20 min~~ → **aucune technique, seulement le M1 à mains nues ; FearRP retirée** (10/10) |
| 2026-10-10 | La **Volonté** (QTE de relève) devient **facultative** : une compétence / technique, pas un droit |
| ~~2026-10-10 (14 h)~~ | ~~Mort RP avec consentement de la victime~~ → **pas de validation joueur** (10/10, 15 h) |
| 2026-10-10 | Le RPK part au **tchat du staff** et au Poste de garde |
| 2026-10-10 | Menu d'interaction sur un corps : **exécution** (animations stylées, **vendables**), attraper à la gorge, porter, RPK |
| 2026-10-10 | **Valider un RPK ne tue pas** : la mort tombe à la dernière action du demandeur (achever / exécuter) |
| 2026-10-10 | Le mort reste sur place, écran noir, ne peut plus rien faire |
| 2026-10-11 | Ancien menu (coffre) sur un corps **retiré** ; prise à la gorge **sans limite de durée** ; corps à l'**apparence Reborn** |
| 2026-10-11 | Au bout de 60 s : « **Assister à votre réelle fin ?** » — Non → sélection ; Oui → vue grisée, sans HUD, bandes de cinéma |
| 2026-10-14 | **Pain system sous 5 % des PV « comme décrit sur le doc » RedLine** : état **Douleur** avant le KO (marche lente, ni course ni mobilité ninja, ni technique ni coup), seuil réglable (`ko.douleur.seuil-pct`) — fiche §1 bis |
| ~~2026-10-14~~ | ~~Exécution par une entrée « Exécuter » au menu R~~ → **à la touche F** (plus bas) |
| 2026-10-14 | **Carry while in Pain RP** : porter une personne en douleur **sans son accord** ; elle **se débat avec Espace** (jauge, protection 6 s) ; un corps porté ne descend plus seul — fiche §6 bis |
| 2026-10-14 | **Agonie = notre « à terre »** (on rampe, 45 s gardés) ; un soin ou la Volonté relèvent **en douleur** (5 %) comme chez RedLine — fiche §2 |
| 2026-10-14 | KO / ATA : **plus de voile rouge sur les bords** (DA §3) → bords assombris, désaturation — fiche §10 ; ~~étiquettes d'état au-dessus des têtes~~ → état **intégré au nom RP** (ligne v2) |
| 2026-10-14 | **Exécution à la touche F maintenue** (« comme pour ramasser », touche Ramasser) : invite « [F] Exécuter » en visant, **maintenir** = canalisation (anneau autour du viseur), **relâcher = annuler** ; douleur → inconscient sans RPK ; KO → mort RP seulement avec un **RPK validé**, **en deux temps** (un appui arme « Confirmer · mort RP définitive », le second maintenu exécute) ; priorité de F : objet visé > joueur visé > rôle normal ; le menu R ne garde que le choix de l'animation (si possédée) — fiche §8 |
| 2026-10-14 | **Style unique pour toute la chaîne KO / mort**, dérivé de l'écran « inconscient » (seul jugé bien) : titre pixel centré, filet d'or, minuterie, sous-texte gris ; « visuellement pour les menus c'est pas ça » pour le reste — fiche §10 |
| 2026-10-14 | **À terre et inconscient : plus de HUD de jeu** (vitals, cœurs, barre d'action, viseur, HUD Reborn) ; en douleur : HUD normal — fiche §2, §10 |
| 2026-10-14 | **Douleur : pas de ligne de HUD** (« moche au-dessus ») mais un **ressenti** : bords noirs au rythme du cœur, vision troublée et dédoublée par à-coups, désaturation, vacillement de caméra, cœur et souffle lourd, sons étouffés ; coupable par « Effets de distorsion » — fiche §1 bis |
| 2026-10-14 | **État au nom RP** (glyphe 痛 / 倒 / 気絶 / 屍 accolé, nom teinté) au lieu d'une étiquette séparée ; « R · porter, exécuter » supprimé — fiche §1 bis, §10 |
| 2026-10-14 | **Porté de force** : seulement « Espace · se débattre » et un filet fin, **uniquement si le porté est en douleur** (pas en KO / mort) — fiche §6 bis |
| 2026-10-14 | **`/aide` retiré des textes du jeu** (« la commande n'existe pas ») ; **`/hopital` n'apparaît qu'après les 5 minutes** ; vocabulaire : **« un RPK »** (masculin) partout — fiche §2, §7 |

## Combat — duel

| Date | Décision |
|---|---|
| 2026-10-13 | **Duel consenti n'importe où** : menu d'interaction → « Proposer un duel » → la cible accepte ou refuse → combat ; assouplit la règle « combat libre seulement sur les terrains d'entraînement » |
| 2026-10-13 | **Duel : règles et visuel** — sans séquelles (défaite à 0 PV ou 0 chakra, PV et chakra d'avant rendus), annulé si écart > 30 blocs, tiers qui frappe ou téléport > 16 blocs, nul à 5 min ; invitation 30 s (Entrée / Retour). **Visuel discret** exigé (« c'est gros, faut que ce soit subtil ») : lignes fines, pas de plaques ; titres en police pixel **sans accents** (« met pas d'accents et c'est tout ») — [`../gameplay/KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §4.1 |

## Apprentissage — [`../gameplay/APPRENTISSAGE.md`](../gameplay/APPRENTISSAGE.md)

| Date | Décision |
|---|---|
| 2026-10-08 | Parchemins pour Taï / Ken / Nin ; techniques de clan apprises **dans le clan** ; ANBU via la fonction ANBU |
| 2026-10-08 | Le parchemin est un **vrai objet** (volable, échangeable, brûlable), **unique** (pas de recopie) |
| 2026-10-08 | Bibliothèques : tirage **partagé** toutes les **3 h**, 1 à 9 rouleaux, tout emportable (limite = poids), **accès libre** (restrictions RP) |
| 2026-10-08 | Rang S à **slots limités** par technique ; mort du détenteur = slot libéré, héritage seulement si le rouleau est porté |
| 2026-10-08 | Séances (D 1 … S 20), une par jour ; échec = séance perdue ; easter egg de perte du rouleau ; **maître = maîtrise ≥ 80 et connue depuis 14 jours** ; progression liée au personnage, rouleau en main requis ; fin d'un S validée par le staff |
| 2026-10-09 | Concentration du chakra : en jeu, avant tout ce qui demande du chakra **sauf le Taïjutsu** ; **un mouvement ou un saut doit fausser le test** |
| 2026-10-12 | Les parchemins prennent les **rouleaux SNR de l'équipe** (les modèles générés par bbforge sont jugés moches) : D vert, C jaune, B rouge, A violet, S rouleau des techniques interdites ; les écrans affichent le même modèle 3D ; **les objets 3D vivent dans le pack Nexo, pas dans le mod** |
| 2026-10-13 | **Bibliothèques v2** (choix par défaut, l'utilisateur a dit « continue » sans trancher — révisables) : 7 types Village / Académie / Clan / ANBU / Palais du Kage / Hôpital / Personnelle ; deux étagères **Fonds** (rouleaux déposés, permanents) et **Arrivages** (tirage) ; non-membre = voit sans prendre ; lecture sur place seulement Clan / Académie / Palais avec maître présent ; bibliothèque personnelle créée par le joueur, une par personnage — [`../gameplay/BIBLIOTHEQUES_V2_PROPOSITION.md`](../gameplay/BIBLIOTHEQUES_V2_PROPOSITION.md) · ~~non-membre = voit sans prendre~~ → vols possibles (ligne suivante) |
| 2026-10-13 | **Vols possibles** (remplace « non-membre = voit sans prendre ») : un non-membre peut **voler** un rouleau d'une bibliothèque « membres » — fouille immobile de quelques secondes devant le meuble (durée de base réglable × rang), interrompue si on bouge ou qu'on est frappé ; rouleau marqué « volé à … », journal `vol`, alerte discrète aux membres connectés (anonyme sauf réglage) et alerte `vol` au Poste de garde. Réglable par bibliothèque : vol permis / interdit, durée, alerte nominative. **Secrets paramétrables** : rang S (Hiden), kekkei, jūken, kinjutsu, juinjutsu, techniques de clan / ANBU et toute technique `secret: true` ne sortent **jamais** du tirage commun (Village, Académie, Hôpital) mais seulement des bibliothèques dont la **réserve** les contient (défauts : Clan = son clan, ANBU = kinjutsu / ANBU, Palais = rangs A et S ; onglet staff « Secrets »). Une bibliothèque de **Clan** tire désormais ses arrivages dans sa réserve seulement (arrivages actifs par défaut, tirage commun coupé). Dépôt rendu visible : bouton « Déposer » sur toutes les bibliothèques, grisé avec la raison — [`../gameplay/APPRENTISSAGE.md`](../gameplay/APPRENTISSAGE.md) §3.7, §3.8 |

## Personnages — [`../gameplay/PERSONNAGES.md`](../gameplay/PERSONNAGES.md)

| Date | Décision |
|---|---|
| 2026-10-10 | Sélection et création **en scène**, façon Ketto RP, dans un **vide sombre** ; âge et taille changent la stature |
| 2026-10-10 | **Déserteur retiré** des villages ; emblème d'Ame ajouté ; braises de chakra à la couleur du village ; sons immersifs |
| 2026-10-10 | Garder la **police des menus de base** |
| 2026-10-10 | Apparence façon Ketto dans la DA Reborn ; **palettes par clan** à prévoir |
| 2026-10-13 | Refonte « au bord de l'étang » : esthétique validée (« c'est vraiment ça ») ; bouton « Au hasard » gardé |
| 2026-10-13 | Décor : **plus de silhouette de village** derrière le lac (« juste les roches avec les persos ») ; le village ne teinte plus que le ciel, l'eau et les braises |
| 2026-10-13 | ~~Voix off des étapes~~ **désactivée** (voix générée jugée laide ; mécanisme gardé pour un vrai enregistrement, `-Dreborn.persoVoix=true`) ; ~~musique~~ → **ambiances de nature nocturne** (eau, insectes, vent, grenouilles) |
| 2026-10-13 | Cheveux et yeux : **couleur libre** (pipette) en plus des palettes de clan ; les personnages voisins rendus comme le principal |

## Launcher

| Date | Décision |
|---|---|
| 2026-09-28 | Pas de refonte de l'accueil en tableau de bord |
| 2026-10-07 | « Plus tard » mémorisé 24 h, « Ignorer cette version » (0.3.45) |
| 2026-10-13 | **Onboarding du launcher façon Ketto** : carte sombre « Bienvenue sur Reborn — Comptes Microsoft et Discord requis », étape 1 Microsoft, étape 2 Discord ; on ne joue pas sans les deux — [`../GAMEPLAN.md`](../GAMEPLAN.md) §1.8 |

## Game Plan (modèle RedLine) — [`../GAMEPLAN.md`](../GAMEPLAN.md)

| Date | Décision |
|---|---|
| 2026-10-13 | **Suivre le Game Plan RedLine point par point**, dans l'ordre de leur document : chaque système de Reborn est repris (aujourd'hui, RedLine, écart, cible) et tranché à son tour (§54) |
| 2026-10-13 | **Player ID** : un **compte Reborn** (lié Microsoft + Discord, via le launcher) et un **numéro unique par personnage** (ex. N° 82545) ; **états du personnage** : Libre, Combat, KO, Étourdi, Garde, Porté, Prison, Mode staff — §1 |
| 2026-10-13 | **Inventaire** : on garde la **sacoche** et ses sacs à paliers ; emplacements **Tête** et **Arme** ; ~~**dépasser la limite de poids n'empêche pas de ramasser**~~ → on ramasse jusqu'à **130 %** (ligne « Inventaire : réglages ») ; ça **ralentit progressivement** jusqu'à l'immobilité au-delà d'un seuil ; notifications **en bas à droite**, épurées et simples — §2 |
| 2026-10-13 | **Ramassage** : plus de ramassage automatique, il faut **ramasser l'objet** (touche) ; chaque objet au sol a une **aura de couleur selon sa rareté** ; disparition après X min — §3 |
| 2026-10-13 | **Player ID : N° à 5 chiffres à partir de 00000** (« N° ok… on commence par 00000 au cas où ») — remplace la proposition « départ 10 000 » ; jamais réutilisé (supprimé = gardé dans l'index) ; visible du joueur (sa fiche) et du staff seulement, jamais des autres joueurs ; commandes staff avec `#00042`, commande `/pid` — §1.4, [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md) §2.1 bis |
| 2026-10-13 | **Discord obligatoire dans le launcher** : on ne joue pas sans compte Discord lié au compte Reborn (étape 2 de l'onboarding) — §1.8 |
| 2026-10-13 | **Touche F pour ramasser** un objet au sol (ramassage manuel, §3) |
| 2026-10-13 | **Inventaire : Tête** = on **réutilise** les emplacements Chapeau / Masque / Bandeau de l'onglet Cosmétique (pas de nouvel emplacement), simplement rappelés comme « Tête » dans l'onglet Équipement — [`../gameplay/INVENTAIRE_ET_COSMETIQUES.md`](../gameplay/INVENTAIRE_ET_COSMETIQUES.md) §3 |
| 2026-10-13 | **Inventaire : Arme = étui** : l'emplacement Arme de l'onglet Équipement garde l'arme **visible à la ceinture** (diffusée à tous, ancrée au bassin) ; on la **dégaine** dans la barre et on la **rengaine** (touche **Z**, rebindable) ; API `weapon(p)` pour les techniques d'arme (le Kenjutsu l'exige) — fiche §3 |
| 2026-10-13 | **Inventaire : réglages** — ramassage au-delà de la limite **jusqu'à 130 %** (refusé au-delà, pile ramassée en partie possible) ; paliers Chargé 100-115 % (plus de sprint), Surchargé 115-130 % (ni saut, ni dash, ni course aux murs), Immobile au-delà ; **touche sacoche = E** (inchangée) ; **capacité de base = la nôtre** (barre 1-9 + sacs, 12 kg), pas celle de RedLine — fiche §2, §4, §5 |
| 2026-10-13 | **Objets au sol** : disparition **10 min**, **15 min** pour un objet lâché par un joueur KO, **jamais** pour un rouleau unique ou un objet de quête — fiche §6 |
| 2026-10-13 | **Rareté à 5 niveaux** : Commun, Peu commun, Rare, Épique, **Légendaire (or)**, clé persistante `reborn:rarete` ; résolution clé posée → table `rarete.par-id` de la config → rang du parchemin (D → S) → rareté vanilla → Commun — fiche §7 |
| 2026-10-13 | **Maquettes de l'inventaire validées** (`Maquettes/gameplan/02_inventaire/` : sacoche, surpoids, notifications, ramassage) ; codées le jour même, non publiées — fiche |
| 2026-10-14 | **Déplacements : trois allures façon RedLine** (« marcher, trottiner, sprint, comme c'est décrit ») — **marche** par défaut (lente, RP), **trot** = touche de sprint Minecraft **maintenue** (proposé : Ctrl, rebindable ; Shift reste l'accroupi), **sprint** = **double appui** (endurance du combat, ~5 s, essoufflé jusqu'à 30 %) ; barre fine **seulement** en sprint / récupération, traînée blanche ; vitesses réglables (`mobility.allure`) ; coupé en Chargé, ATA, étourdi, en portant ; neutre en KO, porté, course ninja — [`../gameplay/DEPLACEMENTS.md`](../gameplay/DEPLACEMENTS.md). En attente : course ninja seulement depuis le sprint ? seuil de faim RedLine (0 %) ? |
| 2026-10-14 | **Staff et journaux : « il faut que TOUT soit référencé et traçable, quel que soit le grade »** (du joueur à l'Owner) — journal universel unique (catégories, acteur avec N° et grade, cible, lieu ; fichiers journaliers par catégorie + index mémoire), **toutes les commandes** de tout le monde journalisées (vanilla et console comprises), aucun mouvement de ryo ni d'objet silencieux ; commandes staff à la RedLine (annonces, aller / ramener / retour, gel **persistant**, voir la sacoche, écran noir, séisme, tenue et emote forcées, reset / suppression de perso avec confirmation et sauvegarde, notes par N°, mode staff) — [`../GAMEPLAN.md`](../GAMEPLAN.md) §5.5 et §6.4, [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md) §4 bis *(grades par outil et rétention 90 j choisis par l'agent, à valider)* |
