# Panel staff v2 — conception

> **Validé le 25/10 par l'utilisateur** (« ok pour le panel »), avec une exigence : **les développeurs et modélisateurs
> gardent tous leurs accès de création** — gestion des fichiers du serveur (Nexo, creator-assets, emotes…), ajout de
> modèles, **Techniques / spells** (Technique Creator, aperçu, déploiement avec récapitulatif) et le reste de la section
> Contenu. La matrice des capacités ne doit rien leur retirer de ce qu'ils ont aujourd'hui ; seuls les accès hors de
> leur métier (données joueurs, IP, tickets) sont revus. Correction à faire au code : aucune candidature de première
> whitelist n'affiche de clan (tous les clans passent par une session).

> **Statut : proposition du 25/10, rien n'est codé.** Écrite à partir de trois audits du panel faits en lecture seule
> le 25/10 : structure et usage, visuel et composants, données disponibles pour les indicateurs. **21 maquettes HTML**
> produites le 25/10 puis relues le jour même (relecture critique : écarts corrigés dans cette fiche et dans les
> maquettes, voir §13).
>
> **Demande du 25/10** : « revoir l'entièreté du panel staff pour qu'il soit plus esthétique et épuré, facile à
> utiliser… vraiment simplifier », et un avis sur le tableau de statistiques vu dans une vidéo de Fuze III (rachat
> d'un serveur Minecraft).
>
> **Chantier en cours à respecter** : la candidature v2 et la douane
> ([`../gameplay/CANDIDATURE_ET_DOUANE.md`](../gameplay/CANDIDATURE_ET_DOUANE.md) §3.8, §3.11, §6.3 P1 à P3)
> modifient sans commit :
> - côté panel : `app/(panel)/layout.tsx` (le menu), `app/globals.css`, `lib/api.ts`, `lib/types.ts`, les pages
>   `whitelist/*` et `oral-slots`, et les nouveaux `douane/`, `components/candidature/`, `lib/candidature.ts`,
>   `lib/douane.ts` ;
> - côté API : `prisma/schema.prisma`, `admin.*`, `staff.*`, `whitelist.*`, `game.*`, `play.*`, `app.module.ts`.
>
> Aucun lot de cette fiche ne touche ces fichiers avant que ce chantier soit commité (§12).
>
> **Ce que la fiche ne change pas**
> - **Les adresses des pages.** Le bot renvoie vers `/tickets/<id>` (`apps/bot/src/interactions/ticket.ts`), le jeu
>   vers `/whitelist/<id>` (« Ouvrir dans le panel », `game-douane.vues.ts`). Les sections sont des groupes de menu
>   posés sur les adresses actuelles.
> - Le Poste de garde en jeu : c'est la référence de langage, pas un objet de cette fiche.
>
> **Fiche sœur** : [`../technique/STATISTIQUES.md`](../technique/STATISTIQUES.md) (proposition parallèle du 25/10)
> définit les indicateurs, les données et leurs lots (0, A à G). Elle prime pour tout ce qui est chiffre ; cette fiche
> fixe l'écran (§8).
>
> **Vocabulaire** : **grade** = rôle du panel (`Role` : HELPER, MODERATOR…) ; **serveur** = un serveur Minecraft du
> network (`serverId`) ; **file** = liste de ce qui attend une action du staff.

---

## 0. En dix lignes

1. **Six sections au lieu de quatorze entrées** : À traiter · Joueurs · Candidatures · Modération · Contenu ·
   Pilotage, plus Réglages en pied de barre. Le reste devient des onglets.
2. **L'accueil devient « À traiter »** : une seule file de tout ce qui attend une action (candidatures, tickets,
   alertes), en retard d'abord, « à moi » en premier ; la séance de douane du jour dans une bande au-dessus. Le tableau
   de bord et « Mes prises » fusionnent.
3. **Une page = un travail.** Les tâches fréquentes tiennent en un ou deux clics, et se font aussi au clavier.
4. **Le panel ne montre que ce que le grade permet.** Plus d'entrée de menu qui mène à un 403 brut, plus de bouton
   qui échoue après deux clics.
5. **Une fiche joueur unique** : Aperçu · Personnages · Candidature · Tickets · Sanctions · Historique · Sécurité.
   On trouve un joueur partout avec **Ctrl K**, y compris par le nom de son personnage.
6. **Visuel : le launcher v3 en version outil.** Neutres chauds, ivoire, **or = seule couleur d'interaction** (jamais
   un statut ni un retard : un retard s'écrit en rouge), cramoisi réservé à la marque. Plus de halo, de titre en
   dégradé ni d'animation d'entrée. Les polices sont réellement chargées, les listes deviennent des tableaux denses.
7. **Huit composants de base** remplacent environ 1 500 classes arbitraires, dix pastilles de statut différentes et
   quatre styles de fenêtre.
8. **Pilotage, en deux temps** (chiffres définis dans STATISTIQUES.md), vue **Essentiel** par défaut, **Détail** sur
   demande.
   - Sans toucher au jeu : Maintenant, Recrutement (l'entonnoir de candidature), Staff.
   - Après la remontée des sessions de jeu : activité, rétention, cohortes, économie.
   - Du panel de Fuze III, on garde l'esprit (rétention, cohortes, nouveaux joueurs, durée de session). On écarte ce
     qui sert un serveur qui vend en volume : ARPPDAU, acquisition par nom d'hôte, entonnoir d'achat (§8.1).
9. **Network** : un sélecteur de serveur, invisible tant qu'il n'y a qu'un serveur. Toute donnée venue du jeu porte
   un `serverId`.
10. **Migration en onze lots** (L0 à L9, L7 coupé en deux), chacun déployable seul, sans changer d'adresse.
    **21 maquettes produites** (16 des 31 écrans prévus en entier, 4 en partie), dont les six à valider d'abord
    (§13).

---

## 1. Le constat en bref

Détails dans les trois audits (non versionnés, résumés ici).

| Problème | Chiffre ou exemple |
|---|---|
| Menu plat, non filtré par grade | 14 entrées (plus Paramètres, hors menu), sans groupes. Un HELPER voit 8 entrées qui répondent « Rôle insuffisant : HELPER < WHITELIST_REVIEWER. » |
| Ce qui attend une action est éparpillé | Tableau de bord, Mes prises, Whitelist, Douane, Tickets : cinq pages pour savoir quoi faire |
| Décisions lentes | Accepter un écrit : 5 clics après ouverture (prendre, HRP, confirmer, RP, confirmer), sans « suivante » |
| Joueur introuvable par son personnage | La recherche Joueurs ne couvre ni le nom de personnage ni le N° ; 30 résultats au plus |
| Accès incohérents | WHITELIST_REVIEWER et MODERATOR peuvent **déployer les techniques en production**, en un clic, sans confirmation. L'IP d'un joueur est visible dès HELPER |
| Identité perdue | Accent bleu `#3b5bdb` (le launcher est passé en laque et or). Polices jamais chargées : les titres sortent en Impact |
| Effets qui se concurrencent | 18 en-têtes recopiés (surtitre, h1 de 48 à 60 px, barre lumineuse), 28 halos, 97 `uppercase`, 6 listes animées |
| Aucun composant commun | 19 + 59 variantes de boutons, 20 styles de champ, 10 pastilles de statut, 13 `window.confirm` / `prompt` |
| Lisibilité | Texte bleu à 3,4:1 (203 emplois), gris à 3,9:1 (267 emplois), aucun focus visible au clavier |
| Chiffres faux ou morts | « Joueurs whitelistés » oublie 5 grades. Deux cartes « à brancher », une colonne « Temps de jeu » vide |

**À garder** : Tailwind v4, React Query, toasts sonner, squelettes de chargement, le kit de la candidature v2
(`components/candidature/ui.tsx`), la grille partagée de la liste whitelist, la feuille `.wiki-prose`.

---

## 2. Principes

1. **Une page = un travail.** Une page répond à une question (« qu'est-ce qui m'attend ? », « qui est ce
   joueur ? ») ou mène une tâche (relire, répondre). Ce qui n'y sert pas va dans un onglet, un tiroir ou disparaît.
2. **Ce qui attend une action est sur l'accueil, et seulement ça.** Les comptages sans action vont dans Pilotage.
3. **Un ou deux clics pour les tâches fréquentes**, avec un raccourci clavier pour chacune. Une décision s'enchaîne
   avec la suivante.
4. **Une seule action principale par écran, en or.** Le reste est en **mots** (comme au Poste de garde v3, DA
   §4.2). Un geste destructif n'est jamais à une touche ; une suppression définitive demande de retaper le nom.
5. **Densité d'outil** : tableaux à lignes de 44 px, en-tête de page de 72 px environ, une seule largeur de page.
6. **Le grade décide de ce qu'on voit.** Une entrée, un onglet ou un bouton interdit n'est pas affiché. Si une
   action est refusée quand même, le message dit pourquoi en français (« Réservé aux administrateurs »).
7. **Rien d'« à brancher ».** Une page, une colonne ou une tuile n'apparaît que lorsque sa donnée est collectée.
   Collecte en service mais fenêtre pas encore complète : la tuile dit « Premier chiffre le 9/01 », jamais un zéro.
8. **Même langue que le Poste de garde et le launcher** : icônes au trait (Lucide), mots plutôt que boutons,
   petites capitales réservées aux en-têtes de section, état vide = une phrase + une aide. Aucun kanji, aucun emoji.
9. **Français du staff, jamais de code brut.** Un dictionnaire central traduit statuts, catégories, grades et
   actions d'audit.
10. **Vie privée par défaut** : IP masquées derrière un clic journalisé ; statistiques en agrégats seulement.
11. **Pensé network** : chaque vue sait si elle est globale ou propre à un serveur (§7).

---

## 3. Nouvelle architecture

### 3.1 Les sections

| Section | Ce qu'on y fait | Onglets | Adresses (inchangées, + nouvelles) |
|---|---|---|---|
| **À traiter** | Voir et prendre ce qui attend une action | Filtres : Tout · À moi · Non pris | `/dashboard` (libellé seul changé) ; `/inbox` redirige vers `/dashboard?filtre=moi` |
| **Joueurs** | Retrouver un joueur, tout savoir de lui | Liste ; fiche : Aperçu · Personnages · Candidature · Tickets · Sanctions · Historique · Sécurité | `/players`, `/players/[id]?onglet=…` |
| **Candidatures** | Relire les écrits, organiser la douane | Dossiers · Séances de douane (· Anciens créneaux jusqu'à la fin de la transition) | `/whitelist`, `/whitelist/[id]`, `/douane`, `/douane/[id]`, `/oral-slots` |
| **Modération** | Tickets, casier, sécurité, journal | Tickets · Sanctions · Sécurité · Journal | `/tickets`, `/tickets/[id]`, `/anomalies`, `/audit`, + `/sanctions` (lot L8) |
| **Contenu** | Ce que le staff produit | Wiki · Idées · Annonces Discord · Fichiers · Techniques | `/wiki`, `/wiki/[slug]` (nouveau), `/wiki/ideas`, `/discord`, `/files`, `/abilities` |
| **Pilotage** | Indicateurs du serveur et du network | Une page en blocs : Maintenant · Activité · Recrutement · Staff · Économie | `/pilotage` (nouveau) |
| **Réglages** (pied de barre) | Mon compte, préférences, équipe, modèles de réponses | Compte · Préférences · Équipe · Modèles | `/settings`, `/settings/[onglet]` |

Les onglets sont des **liens vers les adresses existantes** : passer de Tickets à Journal reste une navigation
normale, et aucun fichier de page n'a besoin d'être déplacé.

### 3.2 La barre latérale

```
 REBORN  Panel staff               [ Reborn · Production v ]   ← seulement s'il y a plus d'un serveur
 ─────────────────────────────
  (o) À traiter                 12   ← « à moi » + non pris, selon le grade
  (o) Joueurs
  (o) Candidatures               5   ← écrits à relire
  (o) Modération                 3   ← tickets non pris
  (o) Contenu
  (o) Pilotage
 ─────────────────────────────
  Rechercher…               Ctrl K
  Réglages
  [visage] Omar · Administrateur    Se déconnecter
```

- **Collante**, 232 px, sur toute la hauteur : l'identité et la déconnexion restent visibles.
- Entrée active : texte ivoire en gras, fond `surface-2`, **liseré cramoisi de 2 px** à gauche (le seul emploi du
  cramoisi hors logo).
- **Compteurs** en chiffres sobres (`fg-2`), en rouge (`danger-text`) seulement quand un élément dépasse le seuil de
  retard (§4.1).
  Rafraîchis toutes les 30 s.
- Une section sans aucun onglet autorisé n'apparaît pas.
- Page d'arrivée après connexion : À traiter pour les grades qui ont une file. Les grades techniques (modélisateur,
  développeur) arrivent sur Contenu.

### 3.3 Qui voit quoi (proposition)

La hiérarchie linéaire des grades (`roles.guard.ts`) donne aujourd'hui aux grades de modération des outils de
développeur. La v2 raisonne en **capacités** : l'API renvoie la liste des capacités du compte (`GET /v1/auth/me`,
champ `capacites`) et le panel ne lit que ça (`lib/droits.ts`). Les copies des rangs et des libellés côté panel
(`AssignmentBlock`, `RoleBadge`, `players/[id]`, `settings`) disparaissent.

| Section / onglet | Helper | Modélisateur | Développeur | Recruteur (WL_REVIEWER) | Modérateur | Admin | Owner |
|---|---|---|---|---|---|---|---|
| À traiter | tickets | — | tickets « Bug » (Q3) | candidatures, douane, tickets | idem + signalements | tout + alertes de sécurité | tout |
| Joueurs (sans Sécurité) | oui | non (Q3) | non (Q3) | oui | oui | oui | oui |
| Joueurs › Sécurité (IP, comptes liés) | non | non | non | non | non (Q4) | oui | oui |
| Candidatures | non | non | non | oui | oui | oui + remise à zéro | oui |
| Modération › Tickets | oui | non | « Bug » seulement (Q3) | oui | oui | oui | oui |
| Modération › Sanctions (L8) | lecture | non | non | lecture | oui | oui | oui |
| Modération › Sécurité, Journal | non | non | non | non | non | oui | oui |
| Contenu › Wiki, Idées | oui | oui | oui | oui | oui (suppression) | oui | oui |
| Contenu › Annonces Discord | non | non | non | non | non | oui | oui |
| Contenu › Fichiers | non | Nexo, emotes, ressources du créateur | élargi | non | non | + `plugins/` | + racine |
| Contenu › Techniques | non | non | oui | **non** (aujourd'hui oui) | **non** (aujourd'hui oui) | oui | oui |
| Pilotage › Maintenant, Staff | oui | oui | oui | oui | oui | oui | oui |
| Pilotage › Activité, Recrutement, Économie | non | non | non | non | oui | oui | oui (+ revenus) |
| Pilotage › Démographie | non | non | non | non | non | oui | oui |
| Réglages › Équipe | non | non | non | non | non | oui | oui |

**Libellés des grades**, définis à un seul endroit (`lib/libelles.ts`) : Joueur, Whitelisté, Helper, Modélisateur,
Développeur, Recruteur (`WHITELIST_REVIEWER`), Modérateur, Administrateur, Propriétaire. Un grade s'affiche en
**mot**, sans couleur propre.

La carte des droits de Fichiers (`files.service.ts`) reste la source pour les dossiers ; elle est seulement exposée
en capacités pour que le panel n'affiche plus Fichiers à un grade qui n'y a rien.

### 3.4 Ancien → nouveau

| Aujourd'hui | Devient | Pourquoi |
|---|---|---|
| Dashboard (4 compteurs, 2 cartes « à brancher », 2 panneaux « Répartition ») | **À traiter** (file) ; les comptages vont dans Pilotage › Maintenant | Une page = un travail ; doublons et emplacements morts retirés |
| Mes prises (`/inbox`) | Filtre « À moi » d'À traiter (redirection) | Doublon du bandeau du tableau de bord ; les séances où je suis examinateur apparaissent enfin (bande « Aujourd'hui ») |
| Whitelist | **Candidatures › Dossiers** | Nom du travail, pas de l'outil |
| Séances de douane (entrée de menu) | Onglet de Candidatures | Même travail que les dossiers |
| Anciens créneaux | Onglet temporaire, **supprimé** à la mise en service de la douane | La carte « Oral » de la fiche reste en secours (CANDIDATURE Q9, tranchée le 24/10) |
| Tickets | **Modération › Tickets** | — |
| Audit | **Modération › Journal** | Mot français ; détail mis en forme au lieu du JSON coupé |
| Anomalies | **Modération › Sécurité** (« Connexions suspectes ») | Avec lien vers la fiche, IP masquée |
| — | **Modération › Sanctions** (lot L8) | Le casier remonte du jeu ; pas d'onglet avant |
| Joueurs + fiche (6 cartes, sélecteur de grade immédiat) | **Fiche unique à onglets** ; grade changé dans une fenêtre avec motif | Une fiche = tout le joueur ; plus de changement de grade par erreur |
| Wiki + Idées (deux entrées) | Contenu › Wiki, Idées en onglet | Sous-page du wiki |
| Discord | Contenu › **Annonces Discord** | Ce qu'on y fait |
| Fichiers, Techniques | Contenu › Fichiers, Techniques (filtrés par grade, serveur cible affiché) | Outils de production : le serveur visé doit se voir |
| Paramètres (hors menu) | **Réglages** en pied de barre | Introuvable aujourd'hui |
| Colonne « Temps de jeu » | Retirée ; revient avec les sessions de jeu (L8) | Principe 7 |
| Deux boutons « Prendre en charge » sur un ticket | Un seul « Prendre » (assigne et passe en cours) | Doublon trompeur |
| Deux moteurs Markdown dans le wiki | Un seul (react-markdown) | — |
| 13 `confirm` / `prompt` du navigateur | Fenêtres du panel (`useConfirm`) | Cohérence, accessibilité |

Bilan : **14 entrées → 6 sections + Réglages**. Deux pages disparaissent (Mes prises, Anciens créneaux), deux
arrivent quand leurs données existeront (Sanctions, Pilotage). Nouveautés limitées à ce qui fait gagner du temps : file unifiée, Ctrl K, compteurs, modèles de réponses, joueur visé sur un signalement, notes staff sur le
joueur, enchaînement des décisions, sélecteur de serveur, Pilotage.

---

## 4. Les écrans

### 4.1 À traiter (accueil)

**En-tête** : « À traiter », filtres en segments **Tout · À moi · Non pris**, puces de type avec leur compteur
(Candidatures 5 · Tickets 5 · Alertes 1).

**Bande « Aujourd'hui »**, une ligne, seulement si elle a du contenu :
- « Séance de douane 21 h · 12 / 16 inscrits · tu es examinateur · Voir » ;
- « Reborn · 23 en ligne » (et chaque serveur du network).

La séance n'est que là, pas dans la file : c'est un rendez-vous, pas une tâche (la montrer aux deux endroits la
comptait deux fois).

**La file**, un tableau : ce qui dépasse le seuil de retard d'abord, puis du plus ancien au plus récent :

| Colonne | Contenu |
|---|---|
| Type | icône + mot : Écrit à relire, Ticket « Bug », Signalement, Connexion suspecte |
| Sujet | nom du personnage, titre du ticket, pays de la connexion |
| Joueur | visage + pseudo, lien vers la fiche |
| Attente | « 2 j 6 h », une seule écriture (jamais « 26 h » à côté de « 1 j 20 h »), en rouge au-delà du seuil de retard (Q10 : 48 h pour un écrit, 24 h pour un ticket sans réponse, réglables) |
| Pris par | visage du staff ou « — » |
| Action | un mot : **Prendre** ou **Ouvrir** |

- Ordre des groupes : **À moi**, puis **Non pris**, puis **Pris par d'autres** (replié).
- Ce qui attend le joueur (écrit « à revoir », douane non réservée) n'est pas dans la file : ce n'est pas au staff
  d'agir.
- **État vide** : « Rien à traiter. » + « Les nouvelles candidatures et les tickets arrivent ici. »
- **Données** : au lot L3, la page assemble les routes existantes (liste whitelist, tickets, inbox, séances du
  jour, anomalies). Au lot L4, une route `GET /v1/admin/a-traiter` et une route légère `GET /v1/admin/compteurs`
  (qui sert aussi aux compteurs de la barre) la remplacent. Le comptage des whitelistés est corrigé avant, au lot 0 de
  STATISTIQUES.md.

### 4.2 Joueurs

**Liste**
- Un seul champ : pseudo, Discord, UUID, **nom de personnage**, **N°** (l'API ajoute ces deux derniers).
- Puces de filtre : Grade · Statut (whitelisté, en candidature, banni) · En ligne · Serveur.
- Tableau paginé côté API : visage, pseudo, personnage(s), grade (un mot), statut, dernière connexion, serveur
  actuel.

**Fiche**
- **Carte d'identité fixe** en tête : visage, pseudo, grade, « whitelisté depuis le 12/10 », « en ligne sur
  Reborn » ; une ligne d'alerte si une sanction est active.
- Actions de la carte, en mots : Ajouter une note · Changer de grade (Admin, fenêtre avec motif) · Plus (remise à
  zéro de la candidature, Admin, retaper le pseudo).
- **Onglets** (proches des sous-onglets de la fiche du Poste de garde, pour parler la même langue) :

| Onglet | Contenu | Disponible |
|---|---|---|
| Aperçu | Une ligne par onglet (« 2 personnages, 1 en vie », « Candidature acceptée le 12/10 », « 3 tickets, 0 ouvert », « Aucune sanction »), **notes staff** | L4 (notes : nouvelle table) |
| Personnages | Cartes : nom, N°, village, clan, rang, en vie / mort RP, date de création | Après la synchro des personnages (CANDIDATURE §4.5) |
| Candidature | Résumé + lien vers la fiche candidature | L4 |
| Tickets | Tableau des tickets du joueur, et ceux où il est **visé** | L4 |
| Sanctions | Casier network : type, motif, portée, durée, auteur, preuves | L8 (casier remonté du jeu) |
| Historique | Frise : compte, candidature, décisions, tickets, sanctions, changements de grade, issue de l'audit | L4 |
| Sécurité | Pays, connexions suspectes, versions du launcher, IP **masquée** (« Afficher » journalisé), comptes liés | L4, Admin |

### 4.3 Candidatures

**À coordonner avec la tâche candidature v2.** Cette fiche ne redessine pas le contenu des maquettes P1 à P3 de
CANDIDATURE §6.3. Elle fournit la coquille et le kit. Reco : faire valider la planche du kit (PV-00) **avant** que
P1 à P3 soient dessinées, pour ne les dessiner qu'une fois.

**Dossiers (liste)**
- Onglets : À relire · Douane · À revoir (chez le joueur) · Décidées (Acceptées / Refusées en segments) · Toutes.
- Ligne : **nom du personnage** (principal), pseudo, village, envoyée il y a, phase (**la seule pastille de la
  ligne**), trois petits symboles Écrit HRP · Écrit RP · Oral, visage de qui l'a prise.
- La pastille de phase n'est teintée que si le staff doit agir (« Écrit en attente », info) ou si c'est tranché
  (acceptée, refusée). « À revoir », « Douane à réserver » et « Douane réservée » restent neutres : l'action est chez
  le candidat.
- Les symboles de partie ont chacun **sa forme** : coche (validé), horloge (en attente), flèche de reprise (à revoir),
  croix (refusé), tiret (pas encore). La maquette d'origine avait quatre points de couleur, dont « en attente » (ambre)
  et « à revoir » (orange) à ΔE 7 : indiscernables, et une couleur seule.

**Fiche**
- **À gauche, le dossier** : ancres HRP · Personnage · Histoire · Voix (touches 1 à 4), lecteur de la voix
  (Espace), texte en `.candidature-prose`. Le statut d'une partie s'affiche **une seule fois**, dans le panneau de
  décision.
- **À droite, le panneau de décision**, collant, 360 px :
  - deux lignes **Écrit HRP · Écrit RP**, chacune avec trois mots **Valider · À revoir · Refuser** (la décision est
    par partie côté API : `PATCH /admin/whitelist/:id/decision` avec `part`) ;
  - une ligne **Oral en lecture seule** : il se décide au Poste de garde ; la carte « Oral hors jeu » de secours est
    dans le bloc Douane (§3.11 de CANDIDATURE) ;
  - **« Valider l'écrit »** (seule action en or) valide HRP et RP d'un coup quand les deux attendent. Pas de boutons
    « À revoir » ou « Refuser » globaux en plus des mots : ils doublaient les commandes et ne disaient pas quelle
    partie ;
  - **motif** : il n'apparaît que sous la ligne choisie, après « À revoir » ou « Refuser » (jamais un champ vide
    affiché en permanence) ; liste de **modèles** (« Histoire trop courte », « Incohérence avec le lore »…, gérés
    dans Réglages › Modèles), modifiables avant envoi ;
  - note privée repliée (une ligne d'aperçu).
- **Échanges** (le chat avec le candidat) dans un **tiroir** à droite, pleine hauteur, avec le nombre de messages non
  lus. Fini le bloc figé à 560 px.
- **Prise en charge** : explicite (« Prendre », touche P) ou automatique à la première décision.
- **Présence légère** : « Ouvert par Alex il y a 2 min » pour éviter deux relecteurs sur le même dossier.
- **Après une décision** : confirmation en place (résumé + Entrée), puis bandeau « Décision enregistrée ·
  Suivante (4) », et la plus ancienne du même onglet s'ouvre si « Enchaîner » est actif (Réglages › Préférences).
- Remise à zéro : Admin seulement, dans « Plus », en retapant le pseudo.

**Séances de douane**
- Liste par semaine ; la séance du jour en tête, dans une carte, et pas répétée dans le tableau.
- « **Planifier une séance** » dans un **tiroir**, prérempli avec les heures et les examinateurs de la dernière
  séance. La capacité est proposée selon la règle de CANDIDATURE Q5 (examinateurs × heures × 4).
- **Un verbe par sens** : *planifier* (créer une séance), *voir* (aller sur sa fiche), *hall ouvert* (l'ouverture des
  portes avant la première vague). Les maquettes d'origine disaient « Ouvrir » pour les trois. Le panel actuel dit « Ouvrir une
  séance » pour créer : à aligner avec la tâche candidature.
- Fiche de séance : vagues, présence en direct, absents, retraits avec motif, clôture avec notes. Les décisions se
  prennent au Poste de garde ; le panel les affiche.

### 4.4 Modération

**Tickets**
- Tableau paginé **côté API**, recherche côté API.
- Filtres : Ouverts (par défaut : ouverts + en cours) · Catégorie (Bug, Signalement, Contestation de whitelist,
  Achat, Autre) · Pris par (moi, personne, tous).
- Rafraîchi toutes les 30 s.

**Fiche ticket** : deux zones.
- Conversation pleine hauteur, zone de réponse en bas.
- Panneau latéral :
  - le joueur (lien vers sa fiche) ;
  - le **joueur visé** pour un signalement (nouveau champ, avec son nombre de sanctions) ;
  - la catégorie ;
  - le statut en trois mots : En cours · Résolu · Fermé ;
  - pris par.
- **Réponses types** : taper `/` en début de réponse ouvre la liste.
- Envoi : Ctrl + Entrée envoie ; Ctrl + Maj + Entrée **envoie et résout**. La première réponse prend le ticket.

**Sanctions** (lot L8) : casier network, filtres par type, portée, auteur et période. Lecture seule au panel au
départ : les sanctions se posent au Poste de garde.

**Sécurité** (Admin) : connexions suspectes avec lien vers la fiche, pays, IP masquée, « Vu » en mot (sélection
multiple possible). Plus tard, les expulsions de Guardian (`IntegrityIncident`, aujourd'hui jamais écrit).

**Journal** (Admin)
- Filtres : personne, famille d'action, période. La recherche attend 300 ms après la dernière touche (une requête
  par touche aujourd'hui).
- Libellés français des actions (`whitelist.approve` → « Candidature acceptée »).
- Liens vers les entités.
- Détail mis en forme dans un tiroir, à la place de la colonne « Meta ».

### 4.5 Contenu

- **Wiki** : liste + lecture ; chaque article a son adresse (`/wiki/[slug]`) pour le partager ; un seul moteur
  Markdown ; l'édition reste dans le tiroir.
- **Idées** : onglet du wiki, kanban gardé tel quel (Q5).
- **Annonces Discord** : seul l'habillage change ; l'aperçu garde les couleurs de Discord.
- **Fichiers**
  - Fenêtres du panel à la place des `prompt` / `confirm`. Une suppression définitive demande de retaper le nom.
  - Le **serveur cible** est affiché dans l'en-tête.
  - Le générateur « item animé Nexo » passe dans son propre onglet.
- **Techniques**
  - Outil de développeur : il garde son style compact, mais prend les tokens et les composants.
  - « Déployer » ouvre une fenêtre : techniques modifiées, serveur et environnement visés (« Déployer sur Reborn ·
    Production »).
- **Plus tard** : patch notes, règlement et lore du launcher, dès que l'API aura des routes d'écriture (aujourd'hui
  seulement par le script d'initialisation).

### 4.6 Pilotage

Voir §8.

### 4.7 Réglages

- **Compte** : profil, double authentification.
- **Préférences** : densité (compacte / confortable), raccourcis actifs, enchaîner après une décision, serveur par
  défaut.
- **Équipe** (Admin) : liste du staff, grade, dernière activité, changer de grade (même fenêtre que la fiche).
- **Modèles** (Modérateur et plus) : motifs de candidature et réponses types de tickets, partagés par tout le staff.

### 4.8 Connexion

Même kit : logo, « Se connecter avec Discord », une phrase « Réservé au staff de Reborn », saisie du code de double
authentification en six cases. Le grade minimum n'est plus écrit en anglais.

---

## 5. Design system minimal

### 5.1 Direction

- **Le launcher v3 en version outil** : mêmes neutres chauds, même ivoire.
- **L'or est la seule couleur d'interaction** : action principale, onglet actif, focus, liens, filtre actif. Jamais
  un statut, un retard, une pastille ni un graphique (seule exception : la rampe du tableau de cohortes, sans texte
  or). Mesure faite : l'ambre `warning` est à ΔE 6,4 de l'or (validateur OKLab, seuil de lisibilité 15) ; il ne
  sert donc qu'en point ou en icône à côté d'un mot, jamais en texte ni en fond de pastille. Un retard s'écrit en
  `danger-text`.
- **Le cramoisi est la marque** (logo, liseré de l'entrée active). Jamais sur un bouton : pour un staff, rouge =
  refus ou danger.
- Le violet de la marque (DA §7) reste aux visuels Discord et réseaux. Seuls ses accents de salon servent, et
  seulement comme **couleurs de données** (§5.2).
- Sobriété : une seule ombre (ce qui passe au-dessus de la page), pas de halo, pas de dégradé de texte, pas
  d'animation d'entrée sur les listes. Bebas Neue seulement pour le titre de page et les chiffres clés.
- Thème sombre seul, comme le launcher.

### 5.2 Tokens (`@theme` de `globals.css`)

| Rôle | Token | Valeur | Note |
|---|---|---|---|
| Fond | `background` | `#0a0608` | launcher |
| Surface | `surface` | `#17090c` | cartes, barre latérale |
| Surface 2 | `surface-2` | `#231115` | survol, en-têtes de tableau, champs |
| Surface 3 | `surface-3` | `#30181d` | sélection |
| Bordure | `border` / `border-strong` | `#3a2127` / `#5a2c34` | |
| Texte | `fg` / `fg-2` / `fg-3` | `#f7ecd6` / `#c9bda2` / `#9a8d78` | `fg-3` éclairci : 6,0:1 sur surface (le `#877a66` du launcher tombe à 4,3:1) |
| Interaction | `gold` / `gold-hover` / `gold-soft` | `#e0b06a` / `#f4cd86` / `#3a2c14` | texte sur or `#1a0a0d` (9,7:1) ; anneau de focus |
| Marque | `brand` | `#b01c32` | jamais en texte (2,8:1) |
| Succès | `success` (+ `-soft`) | `#4ade80` | |
| Attention | `warning` | `#f59e0b` | point ou icône à côté d'un mot seulement (trop proche de l'or) |
| Danger | `danger-text` / `danger` (+ `-soft`) | `#f87171` / `#ef4444` | texte 7,0:1 ; fond plein seulement à la confirmation |
| Info | `info` (+ `-soft`) | `#9db4f0` | pervenche de la marque |
| Données | `data-1` à `data-6` | `#728fe3` `#d77361` `#16a8a9` `#a67ed4` `#23ac81` `#b78d19` | pervenche, corail, turquoise, glycine, menthe, or : teintes des accents de salon (DA §7) ramenées à OKLCH L 0,665 / C 0,13, une couleur par serveur ou par série, dans cet ordre. Les pastels d'origine échouent au validateur sur fond sombre (trop clairs, trop peu saturés, or pâle et menthe à ΔE 12) ; ceux-ci passent (ΔE ≥ 16,7 entre voisines, contraste ≥ 3:1). Glycine et turquoise à 6,7 en deutéranopie : légende et étiquettes directes obligatoires |
| Cohortes | rampe | de `surface-2` à `gold` | tableau de cohortes, carte de chaleur |

### 5.3 Typographie, espacement, formes

- **Polices auto-hébergées** : copier `apps/launcher/public/fonts/` (Inter Variable, Bebas Neue) dans
  `apps/admin/public/fonts/` et les déclarer avec `next/font/local`.
- **Six tailles** : 12 (métadonnées, en-têtes de colonne), 13 (tableaux), 14 (texte), 16 (titres de carte),
  28 (titre de page, Bebas), 40 (chiffres clés, Bebas). Graisses 400, 500, 600. Chiffres tabulaires dans les
  tableaux.
- **Espacement** (base 4) : marge de page 32, entre sections 24, intérieur de carte 20, cellule 12 × 16.
- **Rayons** : 6 (petits éléments), 8 (boutons, champs, cartes), 12 (fenêtres), rond (visages).
- **Ombre** : une seule, `shadow-overlay`.
- **Mouvement** : 150 ms, sortie douce ; rien si `prefers-reduced-motion`.
- **Largeur** : contenu jusqu'à 1 440 px, texte long (wiki, candidature) à 72 caractères par ligne.

### 5.4 Composants (`apps/admin/components/ui/`)

| Composant | Rôle | Remplace |
|---|---|---|
| `PageHeader` | Titre (Bebas 28), une ligne de sous-titre facultative, actions à droite, fil d'Ariane, onglets dessous | 18 en-têtes recopiés |
| `Button` | primary (or, un par écran), secondary (bordure), ghost (un mot), danger (texte rouge) ; tailles sm / md ; état « en cours » ; raccourci affiché | 19 + 59 variantes |
| `Field` + `Input` / `Select` / `Textarea` | Libellé, aide, erreur, compteur ; hauteur 36 ; focus or | 5 `Field`, `Champ`, 20 styles |
| `Tabs` / `Segmented` | Onglets soulignés d'or avec compteur ; segments pour 2 ou 3 choix | Pilules lumineuses de 7 pages |
| `Badge` | Tons neutre, info, succès, attention, danger (pas d'or) ; par défaut « point + mot » sans fond ; teinté = une information par ligne au plus, et seulement quand le staff doit agir (info) ou que c'est tranché (succès, danger) | 10 pastilles, `RoleBadge`, `AssigneeBadge` |
| `Card` / `Section` | Surface, bordure, rayon 8, en-tête (titre 16 + action) | 62 cartes à 5 rayons |
| `DataTable` | En-têtes collants, lignes de 44 px cliquables, colonnes déclarées, tri, sélection, pagination, chargement et état vide intégrés, navigation au clavier | Listes en cartes et tableaux faits main |
| `Dialog` + `Drawer` + `useConfirm()` | Un voile, rayon 12, Échap, focus gardé, `role="dialog"` ; suppression à retaper | 4 fenêtres, 13 boîtes natives |

En complément : `EmptyState`, `ErrorState`, `Skeleton` (existant), `Kbd` (une touche), `CommandPalette` (Ctrl K),
`ServerPicker`, et pour Pilotage `StatTile` (chiffre, libellé, écart à la période précédente, mini-courbe) et
`ChartCard` (titre, période, serveur, légende, état « pas encore de données », « Comment c'est calculé »).

Dictionnaires centraux : `lib/libelles.ts` (statuts, catégories, grades, actions d'audit, sources) et
`lib/statuts.ts` (libellé + ton de `Badge` par statut).

### 5.5 Rédaction

- Tutoiement partout, français accentué, majuscule en début de phrase seulement. Les petites capitales ne servent
  qu'aux en-têtes de section.
- Boutons = verbes précis (« Valider l'écrit », « Envoyer et résoudre »), jamais « OK ».
- Dates relatives (« il y a 3 h ») avec la date exacte en info-bulle, heure de Paris. Nombres avec espace fine
  (1 234).
- Une erreur dit quoi faire (« Réservé aux administrateurs », « Connexion perdue · Réessayer »).
- Aucun code brut, aucun emoji, aucun kanji.

### 5.6 Garde-fous

- Un contrôle automatique (recherche dans le code, au build) refuse `[var(--color`, les `#hex` dans `app/`, et
  `window.confirm` / `window.prompt`. Il s'applique d'abord aux fichiers déjà migrés ; la liste des exceptions
  rétrécit à chaque lot.
- Rafraîchissement unifié (React Query) :
  - listes et compteurs : 30 s ;
  - fiche ouverte : 10 s ;
  - séance en cours : 5 s ;
  - rien quand l'onglet du navigateur est caché.

---

## 6. Les cinq parcours les plus fréquents

### 6.1 Commencer son service (tout le staff)

- **Aujourd'hui** : tableau de bord → bandeau « N éléments m'attendent » → Mes prises → Whitelist → Douane →
  Tickets. Cinq pages, et Mes prises ignore les séances où l'on est examinateur.
- **Demain** : connexion → **À traiter**. Tout est dans une file, « à moi » en premier, avec la séance du jour dans
  la bande. **0 clic.**

### 6.2 Relire une candidature écrite (recruteur)

- **Aujourd'hui** : Whitelist → onglet → ligne → fiche (8 cartes et 6 blocs) → Prendre en charge → Valider HRP →
  Confirmer → Valider RP → Confirmer → retour à la liste → suivante. **Environ 9 clics**, plus le défilement.
- **Demain** :
  1. À traiter (ou Candidatures › À relire) → **Entrée** sur la plus ancienne.
  2. Lecture : touches 1 à 4 pour sauter de partie, Espace pour la voix.
  3. **V** « Valider l'écrit » → **Entrée** pour confirmer.
  4. La suivante s'ouvre.

  **1 clic + 2 touches** (ou 3 clics à la souris : ouvrir, « Valider l'écrit », confirmer). Pour « À revoir » :
  **R** (partie en cours de lecture, modifiable) → choisir un modèle de motif → l'ajuster → Entrée.

### 6.3 Répondre à un ticket, traiter un signalement (helper, modérateur)

- **Aujourd'hui** : Tickets (50 au plus, filtre sur ces 50) → fiche → Prendre en charge, et l'autre « Prendre en
  charge » → écrire → Envoyer → Résolu. **5 à 6 clics.** La catégorie est en code brut. Aucun lien vers le joueur
  signalé.
- **Demain** : À traiter → ticket → **R** → `/` + modèle → **Ctrl + Maj + Entrée** (envoie, prend et résout).
  **1 clic + 3 touches.** Pour un signalement, le panneau latéral affiche le **joueur visé** (« 1 sanction · Ouvrir
  la fiche »).

### 6.4 Retrouver un joueur (tout le staff)

- **Aujourd'hui** : Joueurs → recherche par pseudo, Discord ou UUID (pas le nom de personnage, cherchable seulement
  dans Whitelist) → 30 résultats au plus → fiche.
- **Demain** : **Ctrl K** → « itachi », un pseudo ou un N° → **Entrée** → fiche, Aperçu. **2 gestes**, depuis
  n'importe quelle page. Partout où un joueur est nommé (journal, connexions suspectes, tickets, file), son nom mène à
  sa fiche.

### 6.5 Organiser et tenir une séance de douane (recruteur)

- **Aujourd'hui** : Douane → formulaire toujours déplié → jour, début, fin, capacité, examinateurs → Créer. Le jour
  J : page de la séance (rafraîchie toutes les 10 à 15 s).
- **Demain** :
  1. Candidatures › Séances → **Planifier une séance** : le tiroir est prérempli (mêmes heures et examinateurs que
     la dernière, capacité proposée). Ajuster le jour → **Entrée**.
  2. Le jour J, la bande d'À traiter affiche « Séance 21 h · 12 / 16 » → Voir. La présence s'affiche en direct,
     les décisions prises au Poste de garde arrivent seules.
  3. Clôture avec notes.

  Tant que la douane en jeu n'est pas en service, la carte « Oral » de la fiche sert de secours.

**En plus, pour l'administration** : le point de la semaine se fait dans Pilotage, en une page, sur le serveur choisi
dans la barre latérale.

### 6.6 Bilan en gestes, et ce qui en demande plus, volontairement

| Tâche | Aujourd'hui | Demain |
|---|---|---|
| Savoir quoi faire en arrivant | 5 pages | 0 clic |
| Valider un écrit (depuis la liste) | ≈ 9 clics + défilement | 1 clic + 2 touches |
| Répondre à un ticket et le résoudre | 5 à 6 clics | 1 clic + 3 touches |
| Retrouver un joueur par son personnage | impossible depuis Joueurs | Ctrl K + nom + Entrée |
| Créer une séance de douane | 5 champs + Créer | le jour + Entrée |
| **Changer un grade** | 1 menu déroulant, appliqué tout de suite | 3 gestes : « Changer de grade », choix, motif + Entrée |
| **Supprimer un fichier** | `confirm` du navigateur | retaper le nom |
| **Déployer les techniques** | 1 clic, sans confirmation | fenêtre récapitulative (serveur, environnement, techniques modifiées) |
| **Voir l'IP d'un joueur** | visible dès Helper | Admin seulement, « Afficher » journalisé |

Les quatre dernières lignes ralentissent exprès : ce sont des gestes à risque ou des données personnelles.

---

## 7. Network : plusieurs serveurs

**Ce qui est global, ce qui est propre à un serveur**

| Global (network) | Propre à un serveur | Les deux |
|---|---|---|
| Comptes, grades staff, candidatures (Q7), tickets (serveur facultatif), journal d'audit, annonces Discord, wiki | Joueurs en ligne, fichiers, techniques, journal du jeu, sessions, économie, vie RP | Sanctions : portée choisie (Q8) |

Les noms de serveurs de cette fiche (« Reborn · Production », « Build ») sont des exemples.

**Le sélecteur**
- En haut de la barre latérale, **invisible tant qu'il n'y a qu'un serveur**. Valeurs : « Tout le network » et
  chaque serveur, avec son nom et son environnement (Production, Développement, Build).
- Retenu par personne (navigateur) et repris dans l'adresse (`?serveur=reborn`) pour partager un lien.
- Une page globale filtre si un serveur est choisi. Une page propre à un serveur **en exige un** : avec « Tout le
  network », elle demande de choisir, sans défaut silencieux.
- Une action à risque **répète le serveur et l'environnement** dans son libellé (« Déployer sur Reborn ·
  Production », « Recharger les plugins de Build »).
- **Un seul sélecteur.** Pilotage n'a pas le sien : il suit celui de la barre. Seul un bloc propre à un serveur
  (Économie) en affiche un, et seulement quand « Tout le network » est choisi.
- Le serveur `build` reste dans le sélecteur (Fichiers, Techniques) mais il est **exclu des statistiques**
  (STATISTIQUES §3.1) : il s'affiche à part, hors des totaux.

**Les données**
- Liste des serveurs : `GET /v1/admin/serveurs` (identifiant, nom, environnement, en ligne), tirée de la
  configuration de l'API (`REBORN_SERVER_IDS`). **Aucune adresse de serveur dans le panel.**
- L'état des serveurs passe d'un seul hôte à une liste.
- Toute mesure venue du jeu porte un `serverId`. L'identité network est le `userId` Reborn (le `sub` du jeton de
  jeu), pas l'UUID Minecraft.
- Dans Pilotage, chaque serveur garde sa couleur de données d'un graphe à l'autre.

---

## 8. Pilotage : le tableau façon Fuze III, adapté à Reborn

> **Source pour les chiffres** : [`../technique/STATISTIQUES.md`](../technique/STATISTIQUES.md), proposition
> parallèle du 25/10. On y trouve la définition exacte des douze indicateurs, les événements, les agrégats (schéma
> `stats`), Metabase, le cadre RGPD et ses lots 0 et A à G. En cas d'écart sur une formule, une donnée ou un droit,
> **c'est elle qui prime**. Cette section ne fixe que l'écran et sa place dans le panel.

### 8.1 Mon avis sur le panel de Fuze III

**Intéressant, oui. Nécessaire tel quel, non.** La moitié de ses indicateurs servent un serveur à fort volume qui
vend (mini-jeux, boutique). Pour la beta de Reborn, quatre questions comptent :
- les candidats arrivent-ils au bout (entonnoir) ?
- le staff suit-il (charge) ?
- les joueurs reviennent-ils (rétention à J+7, indicateur déjà fixé dans COMMUNICATION.md) ?
- l'économie tient-elle (cible de +5 % de ryo par semaine, MONNAIE_ET_BANQUE §7.3) ?

| Indicateur de Fuze III | Verdict | Version Reborn |
|---|---|---|
| Revenus, revenus mensuels | Écarté pendant la beta | Plus tard : registre des RBCoins, revenus mensuels, Owner seulement (STATISTIQUES lot G) |
| Taux d'achat, ARPPDAU | **Écarté** | Rapport de deux petits nombres à notre échelle. Au plus, plus tard : payeurs du mois et revenu moyen par payeur |
| DAU | Adapté | **Pic du soir** (joueurs simultanés) et **actifs sur 7 jours** ; le DAU reste secondaire |
| Rétention J1 / J7 | Gardé | Comptée depuis la **première session en jeu** (l'écrit et la douane prennent des jours), J7 en fenêtre |
| Nouveaux joueurs | Gardé, précisé | Première session en jeu, pas création de compte |
| Durée de session | Gardé | Médiane et écart interquartile, reconnexions fusionnées |
| Utilisateurs actifs quotidiens (graphe) | Adapté | Courbe des joueurs simultanés par heure, et **carte de chaleur jour × heure** |
| Acquisition par nom d'hôte | **Écarté** | Sans objet : le launcher se connecte seul. À la place : source de découverte, invitations Discord par canal, lien de téléchargement suivi |
| Tableau de cohortes | Gardé | Par semaine d'entrée en jeu ou par vague de whitelist, effectifs affichés, case de moins de 5 masquée |
| Entonnoir connexion → achat → rachat | **Remplacé** | Entonnoir de recrutement : arrivée sur le Discord → compte → candidature → écrit accepté → douane → première session → retour à J7 |
| Progression des joueurs | Adapté | Rangs RP, techniques apprises, par village |
| Démographie | Adapté | Tranches d'âge déclaré, pays ; agrégats, seuil de 5, Admin et plus |

### 8.2 Ce qui manque chez Fuze et compte pour Reborn

- **Charge du staff** : écrits en attente et âge du plus ancien, tickets répondus sous 24 h, remplissage des séances
  et présence à la douane. On montre la charge, **jamais un classement** des membres du staff.
- **Carte de chaleur des joueurs simultanés** : pour placer les séances de douane et les events au bon moment.
- **Économie RP** : masse de ryo par village face à la bande cible, flux par motif, Gini, ryo créé par le staff.
- **Santé technique** : versions du launcher dès aujourd'hui ; plus tard, lancements aboutis, plantages, FPS au
  5ᵉ centile (la cible est un PC à 4 Go de RAM).
- **Annotations** sur toutes les courbes : chaque publication y laisse une fine ligne verticale. Sans elles, une
  courbe ne s'explique pas.

### 8.3 L'écran

Une seule page, de haut en bas du plus urgent au plus analytique (STATISTIQUES §3.4). Une barre d'ancres en segments
permet de sauter d'un bloc à l'autre sans changer de page.

| Bloc | Contenu | Visible par (STATISTIQUES Q6) | Arrive avec |
|---|---|---|---|
| En-tête | Bascule **Essentiel · Détail** ; le serveur est celui de la barre latérale. **Pas de période globale** : chaque tuile porte sa fenêtre dans son titre (« · 7 jours », « · 14 jours »), chaque courbe a sa plage fixe. La maquette d'origine avait un sélecteur 7 j / 30 j / 90 j qui ne changeait aucune tuile | — | Lot B |
| **Maintenant** | En ligne par serveur, écrits en attente, douane du jour, tickets ouverts ; rafraîchi toutes les 30 s | Tout le staff | Lot B |
| **Activité** | Tuiles pic du soir, actifs 7 j, nouveaux joueurs, rétention J1 et J7, durée de session ; courbe horaire, carte de chaleur jour × heure, tableau de cohortes (sans la colonne J0–J6, 100 % par construction), courbe des actifs 7 j | Modérateur et plus | Lots C et D (substitut « launcher actif » avant) |
| **Recrutement** | Entonnoir en barres horizontales (effectif, taux, intervalle), découpe par source, village ou type ; candidatures, conversion, présence | Modérateur et plus | Lot B |
| **Staff** | Écrits relus sous 48 h, tickets répondus sous 24 h, remplissage des séances ; tickets par catégorie, séances des 14 derniers jours. Le stock d'écrits en attente est dans Maintenant, pas deux fois | Tout le staff | Lot B |
| **Économie** | Masse empilée par village, variation hebdomadaire face à la bande cible, Gini, ryo créé par le staff | Modérateur et plus | Lot D |
| Démographie | Tranches d'âge, pays | Admin et plus | Lot B |
| *Vue Détail* | Courbe de survie, durée des sessions, sanctions par type et par semaine, invitations Discord par canal et téléchargements suivis, santé technique (lot F), indicateurs secondaires de STATISTIQUES §3.3 | Mêmes droits que le bloc | Avec la donnée |

- **Composants** : `StatTile` (chiffre en Bebas 40, écart avec la période précédente, petite courbe) et `ChartCard`
  (titre, légende, « Comment c'est calculé », « Premier chiffre le JJ/MM » tant que la fenêtre n'est pas complète).
  Un bloc dont la collecte n'est pas en service n'est **pas affiché** (principe 7) : la maquette d'origine montrait
  une tuile « Plantages et FPS · Pas encore de données », retirée. Ils font partie du kit (lot L1) et sont le
  prérequis de la page.
- **Graphes** : Recharts, chargé seulement sur cette page ; petites courbes, cohortes et entonnoir sans bibliothèque
  (STATISTIQUES §9.2).
- **Couleurs** : une par serveur, prises dans les couleurs de données du §5.2 ; rampe de la surface vers l'or pour
  les cohortes ; une série seule garde `data-1` ; la bande cible de l'économie est neutre (pas d'or).
- **Lecture des petits effectifs** : effectif toujours affiché, intervalle de Wilson, Kaplan-Meier pour la
  rétention, médianes plutôt que moyennes, comparaison au même jour de la semaine précédente (STATISTIQUES §12).

### 8.4 Pour tes analyses (IA / data science)

- **Metabase auto-hébergé**, joignable seulement par tunnel SSH, avec un rôle Postgres en lecture seule sur le
  schéma `stats`. Le même accès sert un carnet Jupyter. Il arrive quand il y a des données à explorer (STATISTIQUES
  §9, lot E). La page Pilotage garde les douze indicateurs du quotidien pour tout le staff.
- **Définitions versionnées** dans le code (`def_version`) : une courbe ne mélange jamais deux définitions.
- **Pistes IA, plus tard**, toujours en aide à la décision, jamais en décision automatique :
  - **similarité entre histoires de candidature** (MinHash ou embeddings calculés en local) pour repérer les textes
    recopiés ;
  - **regroupement des tickets similaires** (même bug signalé dix fois) ;
  - **anomalies économiques** par règles d'abord, puis un score robuste (STATISTIQUES §12).

  Aucun texte de joueur, mineur ou non, envoyé à un service externe sans base légale. Pas de modèle prédictif du
  départ des joueurs : trop peu de joueurs, et ce serait du profilage de mineurs.

---

## 9. Écran étroit

| Largeur | Barre latérale | Pages |
|---|---|---|
| 1 280 px et plus | 232 px, textes | Fiche candidature et ticket en deux zones |
| 1 024 à 1 279 | Rail d'icônes de 64 px, libellés en info-bulle | Panneau de décision plus étroit (320 px) |
| 768 à 1 023 (tablette) | Tiroir ouvert par un bouton menu | Panneau de décision → barre « Décider » collée en bas, qui ouvre une feuille |
| Moins de 768 (téléphone) | Tiroir ; barre du bas : À traiter · Rechercher · Moi | Lecture et actions rapides : À traiter, répondre à un ticket, lire et trancher une candidature, retrouver un joueur. Les tableaux deviennent des listes sur deux lignes ; marges de 16 px, aucun défilement horizontal |

Fichiers, Techniques et l'éditeur d'annonces restent réservés à l'ordinateur. Sur téléphone, ils affichent « À faire
depuis un ordinateur » (Q9).

---

## 10. Accessibilité

- Contraste AA, soit 4,5:1 pour tout texte. Les tokens du §5.2 sont choisis pour ça ; l'or et le cramoisi ne sont
  jamais du texte sur fond or ou cramoisi.
- **Focus visible partout** : anneau or de 2 px, décalé de 2 px. Plus aucun `outline-none` sans remplacement.
- Tout se fait au clavier, y compris les tableaux (flèches, Entrée) et les tiroirs.
- Lien « Aller au contenu » en premier élément.
- Fenêtres : `role="dialog"`, `aria-modal`, focus gardé dans la fenêtre puis rendu à l'élément d'origine, Échap
  ferme.
- Une couleur ne porte jamais seule une information : chaque pastille a son mot, chaque symbole de partie sa forme.
- Icônes seules = `aria-label`. Toasts annoncés aux lecteurs d'écran (`aria-live`). Tableaux avec en-têtes déclarés.
  Page en `lang="fr"`.
- Cibles de 32 px au moins à la souris, 44 px au toucher.
- `prefers-reduced-motion` respecté.
- Vérification à chaque lot : un passage au clavier seul et un contrôle automatique (axe) sur les pages modifiées.

---

## 11. Raccourcis clavier

Affichés dans les info-bulles (« Valider · V »), comme au Poste de garde (« Geler G »). Inactifs dans un champ de
saisie, sauf les combinaisons avec Ctrl. Désactivables dans Réglages › Préférences. La touche **?** ouvre l'aide.

| Où | Touche | Effet |
|---|---|---|
| Partout | Ctrl K | Palette : joueurs, personnages, candidatures, tickets, pages, actions |
| | / | Rechercher dans la page |
| | G puis A, J, C, M, W, P | Aller à À traiter, Joueurs, Candidatures, Modération, Contenu (wiki), Pilotage |
| | Échap | Fermer la fenêtre ou le tiroir, quitter un champ |
| Listes | ↓ ↑ | Ligne suivante, précédente |
| | Entrée | Ouvrir |
| | P | Prendre / relâcher |
| | X | Sélectionner (actions groupées) |
| Fiche candidature | 1 à 4 | HRP, Personnage, Histoire, Voix |
| | Espace | Lire / mettre en pause la voix |
| | V | Valider (« Valider l'écrit » si HRP et RP attendent) |
| | R | À revoir (ouvre le motif de la partie en cours de lecture) |
| | N, Maj + N | Suivante, précédente |
| | E | Échanges (tiroir du chat) |
| | O | Note privée |
| Fiche ticket | R | Répondre |
| | / en début de réponse | Réponses types |
| | Ctrl + Entrée | Envoyer |
| | Ctrl + Maj + Entrée | Envoyer et résoudre |
| Fenêtre | Entrée / Échap | Confirmer / annuler (sauf suppression à retaper) |

**Refuser n'a pas de raccourci à une touche** : geste destructif, au clic ou par la palette.

---

## 12. Plan de migration par lots

**Règles communes**
- **Train web**, déployé par l'utilisateur depuis `origin/main` (Docker). Seul L8 passe aussi par le train jeu,
  dans des commits séparés : la route de l'API d'abord, le plugin ensuite. Aucun push par Claude.
- **Un lot = un déploiement autonome.** On le vérifie en local (panel + API), avec des captures en 1 440 × 900,
  1 024 et 390 de large, **montrées avant le prompt de publication**.
- Migrations Prisma **additives seulement** (aucune suppression de colonne) : revenir à l'image précédente reste
  possible.
- Fichiers stagés un par un.
- **Les adresses ne changent pas.**
- Liste « À tester » à chaque lot.

| Lot | Contenu | Touche les fichiers de la candidature ? | Dépend de |
|---|---|---|---|
| **L0 · Correctifs immédiats** | Retirer les 2 cartes « à brancher » et la colonne « Temps de jeu » ; libellés français des catégories et statuts de ticket ; confirmation avant « Déployer » (techniques) et avant un changement de grade ; API : techniques réservées à Développeur, Admin et Owner | Non (`dashboard`, `tickets`, `players`, `abilities`, `abilities.controller.ts` : absents du `git status` le 25/10) | Réponse à la Q2 pour la partie API |
| **L1 · Fondations** | Tokens v2, polices chargées, focus global, `components/ui/*`, `lib/libelles.ts`, `lib/statuts.ts`, garde-fou. Re-peinture d'ensemble en un passage : l'accent bleu devient or, les halos et dégradés tombent. Les briques de `components/candidature/ui.tsx` passent dans `ui/`, avec des réexports pour ne rien casser | Oui (`globals.css`) | Commit de la candidature v2 |
| **L2 · Coquille** | Barre en 6 sections, filtrage par grade (d'abord déduit du grade, côté panel), barre collante, compteurs, Ctrl K (pages + recherche joueurs existante), rail et tiroir en écran étroit, sélecteur de serveur (caché) | Oui (`layout.tsx`) | L1 |
| **L3 · À traiter** | Nouvelle page `/dashboard` assemblée à partir des routes existantes ; `/inbox` redirige ; bande « Aujourd'hui » | Non | L2 |
| **L4 · Joueurs et Modération** | Fiche à onglets, notes staff, Ctrl K étendu (personnages, N°) ; tickets paginés côté API, un seul « Prendre », réponses types, joueur visé ; Journal et Sécurité refaits ; IP réservée à Admin ; capacités (`GET /v1/auth/me`) ; routes `a-traiter` et `compteurs` | Oui côté API (`schema.prisma`, `admin.*`) | Commit de la candidature v2, L2 |
| **L5 · Candidatures** | P1 à P3 au kit v2, panneau de décision, « Valider l'écrit », enchaînement, modèles de motifs, tiroir des séances ; suppression des Anciens créneaux à la mise en service de la douane | Oui (c'est leur cœur) | Candidature v2 stabilisée, **avec la tâche qui la porte** |
| **L6 · Contenu** | Wiki (adresse par article, un moteur), Idées en onglet, Annonces, Fichiers (fenêtres, serveur cible, onglet Nexo), Techniques (tokens, fenêtre de déploiement) | Non | L1 |
| **L7a · Collecte qui ne se rattrape pas** | = lot A1 de STATISTIQUES.md : arrivées et départs Discord avec l'invitation (bot, nouveau fichier ; journal local tant que la route de l'API n'est pas déployée), lien de téléchargement suivi, échantillons de joueurs en ligne | Une ligne : l'import du module `stats` dans `app.module.ts` (modifié par la candidature) | Rien d'autre. **Avant le 14/11**, même si la candidature v2 n'est pas commitée |
| **L7b · Pilotage v1** | = lots 0, A2 et B de STATISTIQUES.md : corrections (comptage des whitelistés, géolocalisation locale, purge des sessions **après** la reprise qui les lit), émission depuis les services existants, reprise historique, puis page Pilotage (Maintenant, Recrutement, Staff) | Oui côté API | Commit de la candidature v2 ; L1 pour la page (`StatTile`, `ChartCard`). **Page avant le 28/11** |
| **L8 · Données du jeu** | = lots C et D de STATISTIQUES.md, puis le casier en base : train jeu (sessions, battement, instantanés, sanctions envoyés par ShinobiCore), puis train web (blocs Activité et Économie, onglet Sanctions, colonne Temps de jeu) | Non | L7b ; publication des plugins par l'utilisateur. Sessions en service au plus tard le 2/01 |
| **L9 · Network** | Liste des serveurs, `serverId` partout, sélecteur visible, couleurs par serveur | — | Arrivée d'un 2ᵉ serveur |

**Ordre conseillé**
1. L0 et L7a tout de suite : ils ne dépendent pas de la candidature v2. L7a protège ce qui ne se rattrape pas
   (arrivées sur le Discord du 14/11, téléchargements, joueurs en ligne) ; le reste se reprend depuis l'audit.
2. Dès que la candidature v2 est commitée : la collecte de L7b, et L1 → L2 → L3 en parallèle.
3. La page Pilotage de L7b avant le 28/11, sur le kit de L1. L6 se glisse quand il y a de la place.
4. Puis L4, L5 quand la candidature est stabilisée, et L8 avec le train jeu.

Risque à surveiller : si la candidature v2 n'est pas commitée début novembre, L1, L2 et L7b glissent ensemble, et la
page Pilotage du 28/11 avec eux. L0 et L7a n'en dépendent pas.

---

## 13. Maquettes

**Méthode**
- Montages HTML aux tokens v2 (§5.2), en 1 440 × 900, plus la page entière quand elle est plus haute.
- **Données fictives** : pseudos et personnages inventés, aucune IP (toujours masquée), aucun vrai joueur. Scène :
  mardi 16 mars 2027 pendant la beta, sessions de jeu remontées (après L8).
- Rangées dans `Maquettes/panel_v2/` (hors dépôt) : captures à la racine, pages dans `html/`, générateur dans
  `_sources/` (`node build.mjs [filtre]`, puis `shoot.sh [filtre]` avec un profil Edge dans `EDGE_PROFILE`).
- Une maquette validée est refaite en capture réelle du panel en local avant toute publication (DA §5).
- **À valider en premier**, pour fixer la direction : le kit, la coquille, À traiter, la fiche candidature, la fiche
  ticket, Pilotage. Le kit avant que la tâche candidature ne dessine P1 à P3.

**Produites le 25/10 (21)**

| Fichier | Prévu | Contenu |
|---|---|---|
| `00_kit` | PV-00 | Planche du kit : couleurs, typographie, boutons, champs, onglets, pastilles, tableau, états, tuile, suppression à retaper |
| `01_a_traiter` | PV-01 A, PV-03 A | Coquille (barre texte de 232 px, visible sur toutes les pages), bande « Aujourd'hui », file unique |
| `02_joueurs` | PV-06 | Liste, champ unique, puces de filtre |
| `03_fiche_joueur` | PV-07 | Carte d'identité, alerte de sanction, Aperçu, notes staff |
| `04_candidatures` | PV-10 | Dossiers, segments, symboles de partie |
| `05a`, `05b`, `05c` | PV-11 | Fiche candidature : écrit à relire, douane réservée, échanges en tiroir |
| `06a`, `06b` | PV-13 | Séances de douane, tiroir « Planifier une séance » prérempli |
| `07_seance_en_cours` | (en plus) | Fiche de séance en direct : vagues, présence, retraits, notes de clôture |
| `08_tickets`, `09_ticket` | PV-14, PV-15 | Liste, fiche avec joueur visé et réponses types |
| `10_sanctions` | PV-17 | Casier network (état futur, après L8) |
| `11_securite` | PV-16 (moitié) | Connexions suspectes, IP masquée |
| `12_pilotage` | PV-20 à PV-24 | Page entière, vue Essentiel : Maintenant, Activité, Recrutement, Staff, Économie |
| `13a` à `13d` | PV-09, PV-25 (3 sur 4) | Préférences, Équipe, changer de grade, Modèles |
| `14_palette_ctrl_k` | PV-05 | Palette « ita » |

**Corrigé à la relecture du 25/10** (maquettes recapturées) : séance du jour en double sur À traiter ; tri de la file
(« 26 h » rangé avant « 1 j 20 h ») ; retards, statuts, pastilles et bande cible en or ; points de couleur des
candidatures ; boutons « À revoir » / « Refuser » en double dans le panneau de décision et motif affiché à vide ;
« Ouvrir » pour trois sens ; Pilotage (sélecteur de période sans effet, second sélecteur de serveur, `build` compté
dans les joueurs en ligne, 7 écrits en attente au lieu de 5, rétention J7 à 41 % contre environ 60 % dans le tableau
de cohortes, présence et remplissage faux par rapport au graphe des séances, pourcentages sur 5 et 9 personnes,
colonne J0–J6, tuile « à brancher ») ; données fictives incohérentes (candidats à l'écrit non relu présents à la
douane, sanctionnés avant d'avoir joué, « Canada, habituellement Canada »).

**Restent à faire** (11 écrans entiers, 4 en partie) :
- en partie : PV-01 B et PV-03 B (les variantes à comparer pour la Q1), PV-16 Journal, PV-25 Compte ;
- entiers : PV-02 coquille par grade, PV-04 états d'À traiter, PV-08 autres onglets de la fiche joueur, PV-12
  « À revoir » avec motif et bandeau « Suivante », PV-18 Wiki et Idées, PV-19 Fichiers et Techniques (fenêtre de
  déploiement), PV-26 fenêtres et refus de droit, PV-27 écran étroit, PV-28 aide des raccourcis, PV-29 connexion,
  PV-30 avant / après.

La section **Contenu** n'a donc encore aucune maquette, ni l'écran étroit.

---

## 14. Questions ouvertes

| # | Question | Recommandation |
|---|---|---|
| Q1 | Barre latérale et accueil : quelles variantes ? | Seules les variantes A (barre texte, file unique) sont maquettées et la fiche les suppose. Les garder, sauf si tu veux voir les B (rail d'icônes, trois colonnes) |
| Q2 | Qui peut déployer les techniques en production ? | Développeur, Admin, Owner. Les grades de modération (Recruteur, Modérateur) perdent cet accès |
| Q3 | Les grades techniques voient-ils les joueurs et les tickets ? | Non, sauf les tickets « Bug » pour le Développeur |
| Q4 | Qui voit l'IP d'un joueur ? | Admin et Owner seulement, derrière « Afficher », chaque affichage journalisé |
| Q5 | Kanban Idées ou Trello ? | Garder l'onglet tant que le staff s'en sert ; sinon le retirer au profit du Trello |
| Q6 | Qui voit Pilotage ? | Voir STATISTIQUES Q6 : Maintenant et Staff pour tout le staff ; Activité, Recrutement et Économie à partir de Modérateur ; démographie Admin et plus ; revenus Owner |
| Q7 | Candidature par serveur ou par network ? | Une whitelist pour tout le network de l'univers Naruto ; une candidature par univers si un serveur d'un autre univers arrive |
| Q8 | Portée des sanctions ? | Choisie à la sanction : un ban vaut pour tout le network par défaut ; un avertissement ou un mute, pour le serveur |
| Q9 | Faut-il une version téléphone ? | Oui, en lecture et actions rapides seulement (§9) |
| Q10 | Seuils de retard ? | 48 h pour un écrit, 24 h pour un ticket sans première réponse ; réglables par un Admin |
| Q11 | Télémétrie du launcher et du mod (plantages, FPS), avec accord préalable ? | Voir STATISTIQUES Q5 et lot F : après la beta, sur accord, désactivée par défaut |
| Q12 | Nom de la section des statistiques ? | « Pilotage », comme STATISTIQUES Q1 (« Stats » désigne déjà les statistiques des personnages en jeu) |
| Q13 | Grades staff : pour tout le network ou par serveur ? | Network pour ce qui est network (Admin, Owner, Recruteur : la candidature est commune). Par serveur pour la modération en jeu (Helper, Modérateur) dès le 2ᵉ serveur : une capacité porte alors un `serverId`. Rien à coder avant L9, mais `lib/droits.ts` doit le permettre |
| Q14 | Couleur d'un retard ? | Rouge (`danger-text`), comme dans les maquettes recapturées. L'or est pris par l'interaction, l'ambre en est indiscernable |
