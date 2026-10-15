# Guide staff en jeu

> **Pour le staff du serveur.** Ce qu'il faut savoir pour modérer et animer en jeu : le Poste de garde, les
> grades staff, et les commandes rangées par situation. À jour au 2026-10-14 (Poste de garde v3, non publié). L'annuaire complet des commandes est dans
> [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md).
>
> Le panel web (`panel.reborn-rp.com` : whitelist, tickets, fichiers…) est un autre outil ; ajouter un modèle 3D
> depuis le panel : [`NEXO_STAFF_GUIDE.md`](./NEXO_STAFF_GUIDE.md).

---

## 1. Les grades staff

| Grade | Permission | Peut |
|---|---|---|
| **Helper** | `reborn.staff.helper` | Voir les alertes, se téléporter, avertir, chat staff |
| **Modérateur** | `reborn.staff.modo` | + geler, observer, réveiller, lever une ATA, mute et ban jusqu'à 1 jour, **valider ou refuser une RPK**, agir sur les blessés |
| **Admin** | `reborn.staff.admin` | + bans longs ou définitifs, zones, lits, réglages de l'Infirmerie |
| **Owner** | `reborn.staff.owner` (ou OP) | Tout |

Les grades sont cumulatifs. ⚠️ Ils sont aujourd'hui donnés **par permission sur le serveur**, pas encore
synchronisés avec les rôles du panel web.

## 2. Le Poste de garde (F7 ou `/garde`)

Panel staff en jeu (mod Reborn requis ; sans mod, `/staff` ouvre l'ancien panneau). **Version 3 (14/10) : un volet
étroit à droite**, le jeu continue et reste visible à gauche — on voit la scène pendant qu'on agit. F7 ou Échap pour
fermer. En haut : la puce **Mode staff** (verte quand il est actif, clic = bascule ; violette « Animation » en mode
animation) et quatre onglets.

- **Alertes** : combats hors terrain d'entraînement (score), appels à l'aide, signalements, **demandes de mise à mort
  RP**, prélèvements d'œil, vols de parchemin. Chaque alerte a ses actions en mots : **Y aller** (te téléporte),
  Valider… (raison obligatoire) / Refuser pour une mise à mort, Répondre (une ligne sur son écran) pour un appel à
  l'aide, Voir le chat pour un signalement, Observer, Fiche, Ignorer. Sous-onglets **En attente · Traitées · Les
  miennes** (qui a fait quoi). La cloche coupe le petit son des nouvelles alertes.
- **Joueurs** : la liste (visage, nom RP, N° · pseudo · grade, état à droite), filtres **Tous · À terre · Gelés ·
  Notés**, tri N° / nom / état. **Au survol** d'une ligne : 4 icônes en un clic (Aller à, Ramener, Geler, toutes les
  actions). **Clic** = fiche, **clic droit** = toutes les actions.
- **Recherche universelle** (le champ en haut de Joueurs) : nom RP, pseudo, `#00042` — **joueurs hors ligne compris**
  — et **actions** : taper « gel » propose « Geler… » sur le joueur choisi, « gel kazu » cherche Kazu et propose Geler.
  Flèches pour choisir, Entrée pour ouvrir la fiche ou lancer l'action.
- **Fiche** (compacte) : identité, étiquettes, état, distance, PV, chakra, ping, temps de jeu, ryo ; **8 actions** :
  Aller à, Ramener, Observer, Geler, Sacoche, Écran noir, Noter, **Plus** (Écouter la radio, Annonce perso, Forcer une
  emote, Imposer une tenue, Réveiller, Lever l'ATA, Revenir, Le renvoyer, Sanctionner, puis Séisme, Remettre à zéro,
  Supprimer — grisés avec un cadenas si ton grade ne suffit pas). Une action à paramètre s'ouvre **dans le volet**
  (motif, durée…) ; remettre à zéro / supprimer demandent un **motif** et de **retaper le N°**. Sections repliées :
  **Personnages** (voir ci-dessous), **Notes**,
  **Sanctions** (casier, messages récents, choix de l'infraction puis preuves à cocher), **Journal du joueur**, **Radio**
  (écoute de son escouade : motif, durée), **Comptes liés**.
- **Personnages** (section de la fiche, 15/10) : tous les personnages du compte, **même hors ligne** (trouve-le par la
  recherche). Une ligne par personnage (N°, nom, clan · village · grade · âge, « joué » / « mort RP »), les
  emplacements du compte (**Ajouter… / Retirer…**, Admin) et **+ Créer un personnage…** (Admin). Clic sur une ligne =
  le détail : identité, date de création, natures, techniques, ryo, blessures, **fiche shinobi** (stats, PV, chakra,
  points) ; puis **Identité…**, **Clan et village…**, **Art et affinité…** (Modo), **Jouer ce personnage…** (Modo,
  joueur en ligne, motif), **Grade et points…** (Admin), **Déclarer mort RP… / Ressusciter…**, **Remettre à zéro…**,
  **Supprimer…** (Admin, motif obligatoire ; N° retapé pour les deux derniers, sauvegarde faite avant). Échap = retour
  à la liste. Tout est journalisé. Remplace `/character create|edit` et les anciennes commandes `/persoreset`,
  `/persosuppr`. Détail : [`../gameplay/PERSONNAGES.md`](../gameplay/PERSONNAGES.md) §1 bis.
- **Raccourcis dans la fiche** (aucun champ actif) : **A** aller, **R** ramener, **O** observer, **G** geler, **S**
  sacoche, **N** écran noir, **T** noter, **E** écouter la radio, **M** annonce perso, **B** revenir, **X** sanctionner,
  **Tab** = Plus.
- **Journal** : le journal universel (tout ce que fait tout le monde). Texte libre, **jetons** de filtre (#N°,
  catégories, période) avec leur croix, « + Filtre » ; entrées groupées par jour ; clic = détail (lieu, Y aller, fiche,
  Copier) ; **Ctrl + clic sur un N°** = filtrer ce joueur.
- **Outils** : Annonces (aperçu), Chat staff, Séisme (Admin) ; **Infirmerie** : Blessés (réveiller, KO, hôpital, TP,
  lever l'ATA), Zones et Hôpitaux (la carte s'ouvre dans le volet élargi : glisser pour tracer, molette = zoom, clic
  droit = déplacer), Réglages du KO ; **Modes** (mode staff, mode animation) ; Commandes (aide-mémoire, clic = copier).

### Mode staff et mode animation

- **Mode staff** (`/modestaff`, puce du Poste de garde) : invisible aux joueurs, vol, invulnérable, plaques « N° · nom ·
  état » au-dessus des têtes. **Aucune mécanique RP** : ni coup, ni technique, ni menu R (porter, fouiller, exécuter…),
  ni duel, ni ramassage, ni objet, ni bloc RP (coffre, bibliothèque), ni monture, ni tchat RP — **tes messages partent au
  chat staff**. **Personne ne peut te cibler.** Tous les outils staff restent permis.
- **Mode animation** (`/modeanim`, permission `reborn.staff.animation`) : pour un animateur ou un MJ qui **joue** un
  personnage pendant un event. Visible, mécaniques RP permises, mais **tout est journalisé** (commandes, tchat, coups).
  Les autres staffs te voient « Animation » ; une petite ligne en haut à droite te le rappelle. Exclusif avec le mode
  staff.

**Sanctions** : 9 infractions RP avec escalade par récidive sur 90 jours ; la confirmation joint les derniers
messages du joueur comme preuves et une note ; le sceau applique avertissement, mute ou ban (`/pardon` pour
annuler). Stockage local au serveur (`sanctions.yml`, `staff-journal.yml`) — **pas encore dans la base du panel
web**.

## 3. Situations courantes

### Un joueur appelle à l'aide / est à terre
- L'alerte arrive au Poste de garde (et aux médics). « Y aller » pour se téléporter.
- Réveiller ou lever une ATA si la situation l'exige (Modérateur) : `/ata lever <joueur>`.
- Mettre une ATA : `/ata appliquer <joueur> [pleine|allegee]` ; voir : `/ata voir <joueur>`.

### Une demande de mise à mort RP (RPK)
1. Le message arrive au tchat : `☠ [Staff] RPK : <tueur> (<pseudo>) veut mettre à mort <victime> (<pseudo>) ·
   <lieu> [Valider] [Refuser] [Y aller]`, et l'alerte « mort » au Poste de garde.
2. **Aller voir la scène** si besoin ([Y aller]).
3. **Valider** demande une **raison**. La victime **ne meurt pas tout de suite** : le tueur reçoit le droit
   d'« Achever » ou d'exécuter ; c'est son action qui tue.
4. **Refuser** : la victime reste en vie, tout le staff est prévenu.
5. La demande tombe toute seule si la victime se relève ou se déconnecte.
Règles complètes : [`../gameplay/KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §7.

### Préparer un lieu
- Terrain d'entraînement / zone de repos : `/zonerp pos1`, `/zonerp pos2`, `/zonerp creer <entrainement|repos> <id>`
  (ou depuis l'Infirmerie).
- Hôpital d'un village : zone hôpital + lits depuis l'Infirmerie ; repli `/hopital set <village|defaut>`.
- Bibliothèque de parchemins : viser le meuble, `/bibliotheque creer <nom>`, puis `/bibliotheque regler`.

### Faire progresser un personnage
- **Grade** (décision RP) : `/grade <joueur> <grade> [merite|examen]`.
- **XP / niveau** (invisible pour le joueur) : `/xp voir <joueur>`, `/xp donner <joueur> <n> [raison]` (missions,
  examens, events), `/xp niveau <joueur> <n>`.
- **Points de stats bonus** : `/stats give <personnage> <n>`. Jeton de réinitialisation : `/stats jeton give
  <joueur> <n>`.
- **Techniques** : `/parchemin donner <joueur> <technique>` (un rouleau), `/parchemin seance <joueur> <technique>
  [n]` (séances), `/parchemin valider|refuser <joueur> <technique>` (fin d'un rang S, liste avec
  `/parchemin attente`).
- **Exécutions** (cosmétiques) : `/execution donner <joueur> <id>`, `/execution liste [joueur]`.

### Ambiance
- Météo visuelle : `/meteo <pluie|sable|brume|clair> [leger|moyen|fort|tempete|0-100] [joueur|tous]`.
- Musiques : `/ost play|playat|playglobal|stop`.
- Cinématiques : `/cinematic`. Invisibilité : `/vanish`.

### Outils staff (14/10, pas encore publiés)
Cible : pseudo, nom RP ou `#N°`. **Tout est journalisé** : la commande tapée et son effet (`/journal`).
- **Joueurs** : `/aller <cible>` puis `/retour` ; `/ramener <cible>` puis `/retour <cible>` (Modo) ;
  `/geler <cible> [motif]` / `/degeler` (Modo, **persistant** : survit à la déconnexion) ; `/voirsac <cible>` (Modo,
  prendre un objet : Admin) ; `/noir <cible> [secondes|off] [message]` (Modo) ; `/emoteforcee <cible> <emote|stop>`,
  `/tenue <cible> <id Nexo|main|retirer>` (Modo).
- **Messages** : `/annoncejoueur <cible> <message>` (convocation, scène) ; `/annonce <message>` à tous (Modo).
- **Monde** : `/seisme [rayon|tous] [force] [secondes] [message]` (Admin).
- **Personnages** : au Poste de garde, fiche › **Personnages** (§2) ; `/persoreset` et `/persosuppr` ont été retirés le
  15/10 (la remise à zéro et la suppression demandent un motif et le N° retapé, une sauvegarde est faite avant dans
  `sauvegardes/perso/`).
- **Suivi** : `/note <#N°> ajouter [etiquette:<mot>] <texte>`, `/note <#N°>` pour lire (supprimer : l'auteur ou un
  Admin) ; `/journal [#N°] [catégorie] [h:24] [texte]` ; `/modestaff` (invisible aux joueurs, aucune action de jeu).
- Le **vocal n'est jamais journalisé**.

### Radio (escouades, 14/10, pas encore publiée)
Cible : pseudo, nom RP ou `#N°`. Le contenu vocal n'est **jamais** enregistré ; les faits le sont (catégorie `RADIO` du
journal, et `STAFF` pour vos actions).
- **Écouter une escouade** : fiche › Plus › « Écouter la radio… » (touche **E**), ou `/radio ecouter <cible> [minutes]
  <motif>` (Helper ; 10 min par défaut, 60 au plus ; **motif obligatoire**). Vous entendez l'escouade comme un membre,
  **sans pouvoir parler** ; une petite ligne « écoute · Kagerō · 9:41 » s'affiche au-dessus de l'élément Escouade. Le
  joueur **n'est pas prévenu** ; l'écoute est au journal, visible des autres staffs. `/radio stop` pour arrêter.
- **Voir** : `/radio info <cible>` (oreillette, escouade, membres, oreillettes exclues).
- **Sanctionner** : `/radio couper <cible> [minutes] [motif]` (Modo, interdit d'émettre, 30 min par défaut ; perdu au
  redémarrage) ; `/radio retirer <cible> [motif]` (Modo, le retire de son escouade).
- **Objets** : `/radio donner <cible>` (Admin, oreillette neuve) ; `/radio reparer [cible]` (Modo ou permission
  `shinobiradio.artisan` : répare l'oreillette coupée portée ou en main).
- En **mode staff** : vous n'émettez ni n'entendez par une oreillette (aucune mécanique RP) ; seules les commandes
  ci-dessus marchent.

### Monnaie et banque (15/10, pas encore publiées)
Cible : pseudo, nom RP ou `#N°`. **Motif obligatoire** pour tout ce qui change un montant ou un état ; tout est au
journal (catégorie `ECONOMIE`), consultation comprise. Le ryo **porté** est un objet (la bourse) ; le ryo **à l'abri**
est sur le **compte**, au guichet du village. Détails : [`../gameplay/MONNAIE_ET_BANQUE.md`](../gameplay/MONNAIE_ET_BANQUE.md).
- **Fiche joueur** : section **Économie** (bourse, compte et palier, coffre, solde, dernier mouvement) avec
  *Donner…* · *Retirer…* · *Voir le coffre* · *Geler le compte* · *Journal économie ›* ; les formulaires s'ouvrent
  dans le volet.
- **Donner / retirer** : `/ryo donner|retirer|fixer <cible> <montant> [bourse|compte] "<motif>"` (Admin ; compte par
  défaut, même hors ligne ; la bourse seulement si le personnage est joué). Exemple :
  `/ryo donner #00042 500 compte "récompense mission C (Tora)"`.
- **Consulter** : `/banque voir <cible>` (Modo) ; `/banque coffre <cible>` (Admin, ouvre le coffre du compte, ce que
  vous retirez est journalisé) ; `/banque masse [village]` (bourses + comptes + trésor).
- **Geler** : `/banque geler|degeler <cible> "<motif>"` (plus aucune opération au guichet) ; `/banque palier <cible>
  <1-4> "<motif>"`.
- **Corriger** : `/banque contrepasser <n° d'opération> "<raison>"` (virement, staff, solde, héritage : ligne inverse,
  l'originale reste ; le N° est dans le journal, « op 123 »).
- **Mettre en place une banque** : viser le comptoir (bloc, meuble ou PNJ), `/banque guichet ajouter konoha "Banque de
  Konoha"` ; pour un guichetier joueur : `/entreprise creer banque "Banque de Konoha" <responsable>`, puis `/entreprise
  membre ajouter …` ; `/banque guichet liste|retirer`.
- **Solde du dimanche** : payée par le **trésor du village** — le remplir : `/banque tresor konoha ajouter 10000
  "subvention de lancement"` ; barème : `/banque solde bareme` ; versement forcé : `/banque solde payer [village]`
  (une fois par compte et par semaine, 3 h de présence RP requises).
- **Testament** : un joueur le dépose au guichet (`/testament deposer …`) ; une fois la scène vue en RP :
  `/testament valider <cible>` (ou `/testament enregistrer <cible> <héritier>[:part] … "texte"`). À la mort RP, compte et
  coffre restent gelés 7 jours (annuler la mort les dégèle), puis vont aux héritiers d'un testament validé, sinon au
  trésor du village.
- **Alertes « economie »** au Poste de garde : virement ou échange ≥ 10 000, vol sur un corps ≥ 1 000, trois virements
  vers le même compte en 24 h, virement entre comptes liés (même IP), grosse bourse ramassée par un autre, bourse en
  double.

### Boutiques : tailleur et bazar (15/10, pas encore publiées)
Les tenues ne s'achètent plus depuis l'Échap (sa carte **BOUTIQUE** ouvre un écran vide, en attendant ses visuels) :
elles se vendent chez un **tailleur**, un lieu du village ; vivres et cartes au **bazar**. Les bâtisseurs construisent la
boutique ; vous ne faites que **marquer** les comptoirs et les estrades. Tout est journalisé (`BOUTIQUE_*`), chaque achat
aussi (`ECONOMIE ACHAT`). Détails : [`../gameplay/BOUTIQUES.md`](../gameplay/BOUTIQUES.md) §13.
- **Créer** : `/boutique creer tailleur "Tailleur Hōsen" konoha [entreprise]` (ou `bazar "Bazar de Konoha" konoha`).
- **Comptoirs** (un par rayon) : visez le bloc, le meuble ou le PNJ, puis `/boutique comptoir ajouter tailleur_hosen
  hauts bas` ; un autre `… masques`, un autre `… coiffes accessoires`. Catégories : `tenues`, `hauts`, `bas`,
  `manteaux`, `coiffes`, `masques`, `accessoires` (tailleur), `vivres`, `cartes` (bazar). Sans catégorie : toutes
  celles du genre. Changer (en visant) : `/boutique comptoir categories masques` ; liste précise :
  `/boutique comptoir articles masque_hannya bandeau_konoha` (`tous` pour revenir) ; libellé : `/boutique comptoir nom
  "Rayon des masques"` ; retirer : `/boutique comptoir retirer`.
- **Estrade d'essayage** (tailleur) : tenez-vous où le client se tiendra, **regardez vers où la caméra sera** (face au
  miroir), `/boutique estrade ajouter tailleur_hosen` : la ligne de visée de la caméra s'affiche 10 s (fumée = un bloc
  gêne). Ajuster : `/boutique camera tailleur_hosen 2,7 1,0` (distance, hauteur). Sans estrade, le personnage est
  dessiné sur un voile.
- **Prix et articles** : catalogue commun `plugins/ShinobiCore/boutique-catalogue.yml` (prix, catégories, conditions ;
  `/boutique recharger` après modification) ; propre à une boutique : `/boutique article prix tailleur_hosen tenueh12
  300` (`catalogue` pour revenir), `/boutique article retirer|remettre tailleur_hosen <id>`.
- **Gérer** : `/boutique fermer|ouvrir <b>` (« Fermé — revenez plus tard »), `/boutique info <b>`, `/boutique liste
  [village]`, `/boutique voir <b> [n° de comptoir]` (ouvre l'écran à distance pour tester), `/boutique supprimer <b>
  confirmer`.
- **Garde-robe** (tenues liées au personnage) : `/boutique donner|reprendre <cible> <tenue> "<motif>"`.
- **Fonction** (masque ANBU…) : donnez la permission `reborn.fonction.anbu` en attendant le menu Faction.

## 4. Les médics

- Rang 1 : permission `shinobicore.medic` ; rang 2 : `shinobicore.medic.expert`. À donner au grade « Corps
  médical » quand la faction existera.
- Outils : `/paume` (soin tenu), bandes de gaze, `/soigner` (blessures), armoires à médicaments.
  Détails : [`../gameplay/KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §5.

## 5. Ce qui n'existe pas encore

- Casier et journal dans la base du panel web (module `incidents` vide).
- Synchronisation des grades staff panel → jeu.
- Commande `/sanction` hors du Poste de garde.
- La radio est **codée, pas encore publiée** (plugin ShinobiRadio + Plasmo Voice serveur) : tant qu'elle n'est pas sur le
  serveur, l'écoute de la fiche reste grisée « Bientôt ».
- Carte des zones pour un autre village que Konoha.
