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
  (motif, durée…) ; remettre à zéro / supprimer demandent de **retaper le N°**. Sections repliées : **Notes**,
  **Sanctions** (casier, messages récents, choix de l'infraction puis preuves à cocher), **Journal du joueur**, **Radio**
  (écoute : prête, active quand la radio existera), **Comptes liés**.
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
- **Personnages** : `/persoreset <#N°>`, `/persosuppr <#N°>` (Admin) — demande, puis la même commande + `confirmer`
  sous 60 s ; une sauvegarde est faite avant (`sauvegardes/perso/`).
- **Suivi** : `/note <#N°> ajouter [etiquette:<mot>] <texte>`, `/note <#N°>` pour lire (supprimer : l'auteur ou un
  Admin) ; `/journal [#N°] [catégorie] [h:24] [texte]` ; `/modestaff` (invisible aux joueurs, aucune action de jeu).
- Le **vocal n'est jamais journalisé**.

## 4. Les médics

- Rang 1 : permission `shinobicore.medic` ; rang 2 : `shinobicore.medic.expert`. À donner au grade « Corps
  médical » quand la faction existera.
- Outils : `/paume` (soin tenu), bandes de gaze, `/soigner` (blessures), armoires à médicaments.
  Détails : [`../gameplay/KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §5.

## 5. Ce qui n'existe pas encore

- Casier et journal dans la base du panel web (module `incidents` vide).
- Synchronisation des grades staff panel → jeu.
- Commande `/sanction` hors du Poste de garde.
- La radio (l'écoute staff est prête dans la fiche, grisée « Bientôt » tant que `/radio` n'existe pas).
- Carte des zones pour un autre village que Konoha.
