# Guide staff en jeu

> **Pour le staff du serveur.** Ce qu'il faut savoir pour modérer et animer en jeu : le Poste de garde, les
> grades staff, et les commandes rangées par situation. À jour au 2026-10-12 (`reborn-hud` 0.4.169, ShinobiCore
> du serveur dev). L'annuaire complet des commandes est dans
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

Panel staff en jeu (mod Reborn requis ; sans mod, `/staff` ouvre l'ancien panneau). Colonne de navigation à
gauche :

**番 GARDE**
- **Alertes** : combats hors terrain d'entraînement (à partir de 2 par heure, avec un score), appels `/aide`,
  signalements `/signaler`, **demandes de mise à mort RP**. Boutons d'action sur chaque alerte (Observer, Y aller,
  Ignorer, Valider la RPK, Refuser…).
- **Joueurs** : liste et **fiche joueur** (personnage, PV, chakra, ryo, temps de jeu, ping, état KO / ATA, comptes
  liés par IP, casier). Actions : téléporter, ramener, geler, observer, réveiller, lever l'ATA.
- **Chat staff** : canal privé (aussi `/staffchat <message>`, ou `#message` dans le chat).
- **Journal** : tout ce que fait le staff (sanctions, téléportations, `/ata`, `/zonerp`, `/hopital`, soins,
  grades, RPK), filtrable (ex. « KO »).
- **Commandes** : aide-mémoire des commandes avec le grade minimum, recherche, bouton « Préparer ».

**医 INFIRMERIE**
- **Blessés** : joueurs à terre, inconscients ou en ATA, en carrousel au-delà de 5.
- **Zones** : tracer, déplacer, redimensionner les terrains d'entraînement, zones de repos et hôpitaux **sur la
  carte de Konoha** (molette = zoom, clic droit = déplacer la vue).
- **Hôpitaux** : choisir les lits (meubles Nexo « lit », « bed », « futon »), nombre illimité.
- **Réglages** : durées du KO et de l'ATA (appliqués à chaud ; un `/sc reload` les annule jusqu'au redémarrage).

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

## 4. Les médics

- Rang 1 : permission `shinobicore.medic` ; rang 2 : `shinobicore.medic.expert`. À donner au grade « Corps
  médical » quand la faction existera.
- Outils : `/paume` (soin tenu), bandes de gaze, `/soigner` (blessures), armoires à médicaments.
  Détails : [`../gameplay/KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §5.

## 5. Ce qui n'existe pas encore

- Casier et journal dans la base du panel web (module `incidents` vide).
- Synchronisation des grades staff panel → jeu.
- Commandes `/geler` et `/sanction` hors du Poste de garde (« à venir » dans l'aide-mémoire).
- Carte des zones pour un autre village que Konoha.
