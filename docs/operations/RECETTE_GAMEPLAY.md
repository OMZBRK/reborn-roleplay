# Recette gameplay — tout tester de A à Z

> Liste complète des tests en jeu après la publication **`reborn-hud` 0.4.178 / launcher 0.3.46** (14/10/2026).
> Chaque ligne : **action → résultat attendu**. Cocher au fur et à mesure ; noter le numéro de la ligne dans le
> retour (« 7.4 KO : le nom n'est pas teinté »). Prévoir **deux comptes** (A et B), un compte **staff** et, pour
> quelques tests, un troisième joueur (C).
>
> Avant de commencer : redémarrage du serveur fait, `/nexo reload all` fait, console sans `NoClassDefFoundError`
> ni `Invalid plugin.yml` (`Enabling ShinobiCore` puis chaque addon).

---

## 1. Launcher

- [ ] 1.1 Se déconnecter du launcher → écran « Bienvenue sur Reborn », fond flou de Konoha (réel, pas pixel art).
- [ ] 1.2 Connexion Microsoft → coche, tête et pseudo MC ; le bouton devient « Lier mon compte Discord ».
- [ ] 1.3 Lier Discord → attente avec « Rouvrir la page / Annuler » ; après autorisation → « Tout est prêt » puis
  whitelist (joueur) ou accueil.
- [ ] 1.4 Compte sans Discord → bloqué à l'étape 2 (Discord obligatoire).
- [ ] 1.5 Comptes enregistrés en puces sous la carte : clic = reconnexion, croix = oublier.
- [ ] 1.6 Paramètres → délier Discord → retour à l'étape 2.
- [ ] 1.7 Lancer le jeu → la mise à jour du mod 0.4.178 se fait, le jeu démarre.

## 2. Personnage, identité, N°

- [ ] 2.1 Sélection de personnage : lac, lucioles, ambiances sonores (pas de musique, pas de voix).
- [ ] 2.2 Création : vrais blasons de clan, 35 coiffures, accessoire bandeau de Konoha, pipette de couleur libre,
  bouton hasard, taille au cm, nombre d'étapes inchangé.
- [ ] 2.3 Nouveau personnage → `/pid <pseudo>` (staff) donne un N° à 5 chiffres (premier = 00000, puis +1).
- [ ] 2.4 Supprimer un perso (staff) → son N° n'est jamais réattribué au suivant.
- [ ] 2.5 **K** (fiche shinobi) : pastille « N° 000xx » à côté du nom ; clic → « Copié » ; coller au chat = le N°.
- [ ] 2.6 Fiche : la ligne Natures affiche les **symboles octogonaux** canoniques (plus de kanji seul).
- [ ] 2.7 Un joueur non staff ne voit jamais le N° des autres (chat, plaques, tablist, journal de bibliothèque).
- [ ] 2.8 Changer de personnage en combat (< 15 s après un coup), KO, gelé, porté, en duel → refusé.

## 3. Test de la Feuille

- [ ] 3.1 `/testfeuille` en terrain dégagé → confirmation centrée (feuille sur son plateau), « Confirmer / Refuser ».
- [ ] 3.2 Signes de mains visibles (vue de profil), puis concentration (lueur de chakra).
- [ ] 3.3 La feuille réagit selon la nature : Katon brûle, Fūton se fend, Raiton se froisse, Doton s'effrite,
  Suiton se détrempe.
- [ ] 3.4 Révélation : symbole canonique, nom, phrase, « Valider ». La nature apparaît ensuite dans la fiche.
- [ ] 3.5 Second test (niveau 6+) → double nature : feuille en deux bandes, « Ton chakra porte aussi le … ».
- [ ] 3.6 Après le test : HUD revenu, caméra revenue à la normale.

## 4. HUD et écrans

- [ ] 4.1 Vitals : PV, chakra, **barre or = satiété** avec repère à 90 %, icônes voix.
- [ ] 4.2 **H** (éditeur de HUD) : déplacer, redimensionner, preset, aide ; l'élément « Recharges » est en haut à
  gauche sous les vitals.
- [ ] 4.3 **J** (viseur), **O** (caméra), **P** (photo : filtre, cadre), **G** (galerie : favori, détail,
  retouche, partage), **.** (démarches/emotes), Échap (Signaler, Se déconnecter), cosmétiques (Repositionner).
- [ ] 4.4 Chat + réglages du chat, tablist, carte (**M**), échoppe, réglages : tout s'ouvre, rien ne déborde.
- [ ] 4.5 FPS dans chaque écran proches des FPS en jeu (noter les valeurs si chute).
- [ ] 4.6 Recharges : en combat puis en course chakraïque → icônes **nettes** (taille exacte) en 1080p et 1366×768.

## 5. Déplacements

- [ ] 5.1 Avancer sans rien toucher → **marche** lente, aucune barre, la vue ne zoome pas.
- [ ] 5.2 Maintenir Ctrl → **trot** ; relâcher → retour à la marche.
- [ ] 5.3 Ctrl, Ctrl (le second maintenu) → **sprint** ~5 s, barre fine au-dessus de la barre d'action, traînée de
  fumée vue par les autres.
- [ ] 5.4 Barre vide → retour au trot, plus de sprint avant 30 %.
- [ ] 5.5 Course ninja (**L**) pendant un sprint → le sprint s'arrête, la course garde sa vitesse.
- [ ] 5.6 Dash (**V**), double saut, saut de chakra (Shift + Espace), grappin, tyrolienne : fonctionnent hors KO.
- [ ] 5.7 En FAIM (satiété 0 %) → pas de sprint ni de course ninja.

## 6. Inventaire, ramassage

- [ ] 6.1 **E** : sacoche ; onglet Équipement : plaque Arme (étui) + plaque Tête (renvoie aux cosmétiques).
- [ ] 6.2 Arme à l'étui → visible à la ceinture (aussi pour B) ; **Z** dégaine / rengaine.
- [ ] 6.3 Technique de Kenjutsu sans arme → refusée.
- [ ] 6.4 Se charger à 105 % → plus de sprint ; 120 % → ni saut, ni dash, ni course aux murs ; 135 % → immobile.
  Une notification par palier, indicateur sous les vitals, plus de message rouge.
- [ ] 6.5 Jauge de poids graduée jusqu'à 130 % dans la sacoche.
- [ ] 6.6 Glisser un objet hors de la fenêtre → lâché au sol, « −1 … · lâché ».
- [ ] 6.7 Marcher sur un objet → **pas** ramassé ; le viser → « [F] Ramasser … » ; F → « +n … » en bas à droite.
- [ ] 6.8 Aura au sol selon la rareté (5 couleurs) ; option « Auras de rareté » dans Réglages → Interface.
- [ ] 6.9 Pile trop grosse → ramassage partiel ; sans place → « Plus assez de place » ; > 130 % → « Trop lourd ».
- [ ] 6.10 F sans objet visé → garde son rôle (techniques, tyrolienne, grappin).
- [ ] 6.11 Objet au sol 10 min → disparaît ; lâché par un KO → 15 min ; parchemin → jamais.
- [ ] 6.12 `/sc itemgive` barre pleine → l'objet arrive dans le sac, pas dans des cases cachées.
- [ ] 6.13 En KO / porté / étourdi → sacoche en lecture seule, Q et F refusés.

## 7. Interaction (R)

- [ ] 7.1 R + clic sur un joueur : Saluer, Se présenter, Message privé, Ami, Ausculter, Tendre, Donner des ryo,
  Proposer un duel. Plus d'entrée « Échanger ».
- [ ] 7.2 **Tendre** un objet à B → B voit « A te tend … — F prendre · Retour refuser » ; F → l'objet passe, notif
  des deux côtés. Sacoche de B pleine → refus « plus de place ».
- [ ] 7.3 **Donner des ryo** (10 à 500 ou montant libre) → soldes mis à jour ; Retour → refusé.

## 8. Duel

- [ ] 8.1 R → Proposer un duel → petite ligne chez B (Entrée / Retour) ; bandeau « proposé à… » chez A.
- [ ] 8.2 Accepter → 3-2-1 (aucun coup ni technique), indicateur fin en haut (nom, temps, abandon).
- [ ] 8.3 0 PV ou 0 chakra → VICTOIRE / DEFAITE, pas de KO ; PV et chakra d'avant rendus.
- [ ] 8.4 S'éloigner à 30 blocs, tiers qui frappe, téléport > 16 blocs → annulé ; 5 min → nul.
- [ ] 8.5 Refus en ATA ou sur un terrain d'entraînement ; réessayer aussitôt → délai.
- [ ] 8.6 Annonce grise reçue à moins de 24 blocs.

## 9. Combat et états

- [ ] 9.1 Finition de combo sur B (sans garde) → B **immobile 1 s** (ni marche, saut, coup, garde, dash,
  technique) ; un Kai allié ne le libère pas.
- [ ] 9.2 Parade subie → attaquant figé 1,5 s ; garde brisée → 2 s.
- [ ] 9.3 B en garde (**C**) → immobile, sans technique.
- [ ] 9.4 B en ATA frappe avec un katana → refusé ; à mains nues → dégâts ÷2, appliqués **avant** le KO.
- [ ] 9.5 Se déconnecter < 15 s après un coup → alerte « Déco en combat » au Poste de garde.
- [ ] 9.6 Épuiser son chakra en duel → défaite, pas de KO.

## 10. Douleur, KO, mort

- [ ] 10.1 `/execution douleur B` (ou B sous 5 % PV) → chez B : **aucune ligne en haut**, bords sombres qui battent,
  vision qui se trouble par à-coups, cœur et souffle, sons étouffés ; marche lente, ni course, ni technique, ni coup.
- [ ] 10.2 « Effets de distorsion » à 0 % → plus de vacillement ni de dédoublement.
- [ ] 10.3 Chez A : le **nom RP de B est teinté** avec un petit 痛 accolé ; rien ne s'empile au-dessus.
- [ ] 10.4 A vise B → « [F] Exécuter » ; **maintenir F** → anneau d'or autour du viseur ; B tombe **inconscient**.
  Relâcher avant la fin → annulé ; s'accroupir ou être frappé → interrompu.
- [ ] 10.5 A porte B en douleur (R → Porter) → chez B : « Espace · se débattre » + fil fin ; 8 appuis → libéré,
  6 s sans pouvoir être repris. Porté en KO → **rien** à l'écran.
- [ ] 10.6 C met A KO pendant qu'il porte B → B est déposé au sol aussitôt.
- [ ] 10.7 B à 0 PV → **agonie** : il rampe 45 s, **aucun HUD**, pas de contour de bloc, aucune mention de « /aide ».
- [ ] 10.8 Bandage pendant l'agonie → relevé en douleur + ATA allégée.
- [ ] 10.9 **Inconscient** : aucun HUD sauf le tchat et le bloc KO ; « /hopital » apparaît **seulement après 5 min**.
- [ ] 10.10 B KO : tente de frapper, lancer un projectile, grappin, changer de personnage → tout refusé.
- [ ] 10.11 B KO se déconnecte 2 min puis revient → le compte à rebours reprend où il était ; RPK en attente conservé.
- [ ] 10.12 A sur B KO sans RPK → menu R « **Demander un RPK** », pas d'invite F.
- [ ] 10.13 RPK validé par le staff → F « Confirmer · mort RP définitive » (4 s), second appui maintenu → scène de
  mort (10 s).
- [ ] 10.14 Corps d'un mort portable ; dépôt au sol (jamais en l'air) ; prise à la gorge.
- [ ] 10.15 Ancien client : « Achever » fonctionne toujours (3 s).

## 11. Médical

- [ ] 11.1 Bandage, soins, Infirmerie (lits, zones, hôpitaux).
- [ ] 11.2 Chirurgie des yeux (prélèvement / greffe) jusqu'au bout → résultat appliqué ; fiole d'œil Nexo.
- [ ] 11.3 Cécité d'un œil / des deux, bandage qui adoucit.

## 12. Nourriture et entreprises

- [ ] 12.1 Staff : `/entreprise creer restaurant "Ichiraku Ramen" A` → A prévenu, ligne au journal.
- [ ] 12.2 Viser un feu de camp → `/entreprise poste ajouter ichiraku_ramen`.
- [ ] 12.3 Échoppe → onglet **Vivres** : A voit ration + paniers ; B ne voit que la ration.
- [ ] 12.4 A, panier en main, clic droit sur le fourneau → ligne + filet 15 s → « +2 Ramen shōyu — chaud ».
  S'éloigner > 4 blocs ou être frappé → interrompu, panier gardé.
- [ ] 12.5 B (non cuisinier) sur le fourneau → « Pas ton fourneau ».
- [ ] 12.6 B mange le ramen (clic droit) → ~45 s (×2 plus vite assis), or pâle dans la barre ; un coup → pause 30 s.
- [ ] 12.7 Fin → bol vide + **Régénération ×2** sous les vitals ; plat froid (> 10 min) → effet réduit.
- [ ] 12.8 Satiété ≥ 90 % et PV bas → les PV remontent ; un coup → icône grise, 30 s de pause ; en ATA → plafond 30 %.
- [ ] 12.9 `/nourriture satiete B 0` → FAIM : pas de sprint, endurance −50 %, **jamais de dégâts** ; 1-19 % = Affamé.
- [ ] 12.10 Satiété figée en KO, porté, AFK 5 min, mode staff (`/nourriture etat`) ; au KO les bonus partent,
  la Fatigue reste.
- [ ] 12.11 Pilule du soldat → +20 % satiété, endurance +40 % 3 min, puis Fatigue 5 min ; 2ᵉ prise refusée.
- [ ] 12.12 Ration sèche → +25 % ; au-dessus de 60 % → refus.
- [ ] 12.13 Après 30 min de jeu : la faim vanilla ne descend plus, le trot n'est jamais coupé.
- [ ] 12.14 Poste de garde → fiche de A : « Entreprise : Ichiraku Ramen (gérant) · Satiété … % ».

## 13. Apprentissage, bibliothèques

- [ ] 13.1 Parchemins = rouleaux SNR (Nexo) ; lecture d'un rouleau → symbole de nature dans le coin.
- [ ] 13.2 Bibliothèque v2 : types (Village, Académie, Clan, ANBU, Palais, Hôpital, Personnelle), onglet Fonds,
  **Déposer** un rouleau, journal de la bibliothèque.
- [ ] 13.3 Lecture sur place (Clan / Académie / Palais) avec un maître présent.
- [ ] 13.4 **Vol** dans une bibliothèque « membres » : fouille immobile, interrompue si on bouge ou si on est frappé ;
  rouleau marqué « volé à … » ; alerte aux membres et au Poste de garde.
- [ ] 13.5 Secrets : rang S, kekkei, kinjutsu… jamais dans le tirage commun ; seulement dans la réserve concernée.
- [ ] 13.6 Séance d'apprentissage, concentration (ensō).

## 14. Staff — Poste de garde (F7)

- [ ] 14.1 F7 → volet à droite, le jeu reste visible ; onglets Alertes · Joueurs · Journal · Outils ; F7 / Échap ferme.
- [ ] 14.2 Joueurs : survol → 4 icônes (aller à, ramener, geler, plus) ; clic droit → menu complet.
- [ ] 14.3 Recherche : « uchi », « #42 », « gel kazu » + Entrée ; joueurs hors ligne inclus.
- [ ] 14.4 Fiche : A, R, O, G, S, N, T, X ; Tab → menu Plus (actions Admin grisées avec cadenas) ;
  « Écouter la radio » grisé « Bientôt ».
- [ ] 14.5 Geler (motif) → pastille « gelé » ; dégeler. Supprimer → bouton grisé tant que le N° n'est pas retapé.
- [ ] 14.6 Notes (étiquette + texte), sanctions, comptes liés.
- [ ] 14.7 Journal : jetons (#N°, catégorie, 24 h), groupé par jour, détail au clic, « Y aller ».
- [ ] 14.8 Alertes : Y aller / Valider / Observer / Ignorer ; « Traitées récemment ».
- [ ] 14.9 Outils : annonces (globale avec confirmation, perso), séisme (Admin), chat staff, commandes, Infirmerie.
- [ ] 14.10 Rien ne déborde en 1366×768.

## 15. Staff — commandes et modes

- [ ] 15.1 `/annonce`, `/annoncejoueur` → ligne fine en haut de l'écran des joueurs.
- [ ] 15.2 `/aller`, `/retour`, `/ramener #N°` ; `/tp #N°`, `/kick #N°`, `/give #N°` (staff seulement).
- [ ] 15.3 `/geler #N° test` → reste gelé après déco et redémarrage ; un gelé ne peut être porté ni agir ; `/degeler`.
- [ ] 15.4 `/voirsac` (Modo : lecture seule ; Admin : prendre → `OBJET PRISE_STAFF`).
- [ ] 15.5 `/noir #N° 10 texte` → fondu au noir, tchat lisible, levé après 10 s.
- [ ] 15.6 `/seisme` → secousse légère (coupée si « Effets de distorsion » à 0 %).
- [ ] 15.7 `/tenue #N° main` puis `retirer` ; `/emoteforcee`.
- [ ] 15.8 `/persoreset #N°` puis `confirmer` → sauvegarde dans `sauvegardes/perso/`.
- [ ] 15.9 `/note #N° ajouter etiquette:surveillé …` puis `/note #N°`.
- [ ] 15.10 `/modestaff` : invisible, impossible de frapper / porter / ramasser / manger / ouvrir un coffre ou une
  bibliothèque ; tchat → chat staff ; B ne peut ni le frapper ni lui proposer un duel ; refusé si le perso est KO.
- [ ] 15.11 `/modeanim` (permission `reborn.staff.animation`) : visible, mécaniques RP permises, indicateur en haut
  à droite, tout dans `/journal staff`.
- [ ] 15.12 `/pid <joueur|nom RP|#N°>` : N°, nom, compte, état, en ligne, autres N° du compte.
- [ ] 15.13 Un joueur sans grade staff : `#00042` dans une commande ne résout rien.

## 16. Journal universel

- [ ] 16.1 Démarrage → `logs/systeme-<date>.log` contient `DEMARRAGE`.
- [ ] 16.2 `/tp`, `/gamemode`, `/give` → visibles dans `/journal staff h:1` avec N° et grade.
- [ ] 16.3 Coup entre joueurs → `/journal #N° combat` ; ryo / achat → `/journal #N° economie` (solde avant → après).
- [ ] 16.4 KO, exécution, portage → catégorie KO avec #N° ; dons (`DON`), repas, préparations, pilules.
- [ ] 16.5 Reconnexion après crash → `NETTOYAGE_CONNEXION` (plus d'invulnérabilité, de potions infinies, de créatif).

---

**Retours** : un message par bloc, avec les numéros (ex. « 10.3 nom pas teinté »), une capture si visuel, et les
FPS si lenteur. Ce qui casse le jeu d'abord ; les détails visuels ensuite.
