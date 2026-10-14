# Entreprises — restaurant, forge, échoppe

> **Codé le 14/10, non publié** (ShinobiCore, package `entreprise/`). Décision du 14/10 : « Qui nomme les cuisiniers ?
> Ce sera comme pour le menu Faction / section : un truc **entreprise**, créée et gérée par le staff dans un premier
> temps pour exister. » Premier usage : la nourriture ([`NOURRITURE.md`](./NOURRITURE.md)) — **un cuisinier est un
> membre d'une entreprise de type RESTAURANT**. Game Plan : [`../GAMEPLAN.md`](../GAMEPLAN.md) §21, §27, §37.

---

## 1. Le principe

Une **entreprise** est un lieu de travail tenu par des personnages : un restaurant de ramen, une forge, une échoppe.
Elle a :

| Champ | Contenu |
|---|---|
| **Nom** | libre, unique (« Ichiraku Ramen ») ; identifiant dérivé (`ichiraku_ramen`) |
| **Type** | `RESTAURANT` (codé), `FORGE`, `ECHOPPE` (prévus : le type dit ce que l'entreprise permet) |
| **Responsable** | le premier gérant ; le staff peut en nommer d'autres |
| **Membres** | des **personnages** (pas des comptes), chacun avec un **rôle** : `gérant` ou `employé` |
| **Postes de travail** | des blocs ou des meubles Nexo marqués par le staff (fourneaux d'un restaurant, enclume d'une forge…) |

Un personnage peut appartenir à plusieurs entreprises. Ce que le type permet :

| Type | Permet | Postes |
|---|---|---|
| `RESTAURANT` | **cuisiner** : acheter des paniers d'ingrédients à l'échoppe (onglet Vivres), préparer au fourneau de **son** restaurant (15 s, deux plats) | fourneau |
| `FORGE` | (à venir : réparer, forger — GAMEPLAN §25) | enclume |
| `ECHOPPE` | (à venir : vendre au comptoir) | comptoir |

## 2. Ce qui est codé (14/10)

- Données : `plugins/ShinobiCore/entreprises.yml`, écrit à chaque changement (rares, commandes staff).
- **Cuisinier** = membre (gérant ou employé) d'une entreprise `RESTAURANT` ; il ne cuisine qu'aux postes de **son**
  restaurant (`nourriture.cuisine.poste-de-son-entreprise: true`).
- **Poste** : le staff le vise (bloc ou meuble Nexo à 6 blocs) avec `/entreprise poste ajouter <entreprise>` ; le clic
  droit d'un cuisinier avec un panier sur ce bloc (ou sur l'entité du meuble, tolérance 1 bloc) lance la préparation.
- **Poste de garde** : la fiche du joueur affiche une ligne « Entreprise : Ichiraku Ramen (employé) · Satiété 72 % ».
  Pas d'écran dédié.
- **Journal universel** (catégorie Staff) : `ENTREPRISE_CREER`, `ENTREPRISE_SUPPRIMER`, `ENTREPRISE_RENOMMER`,
  `ENTREPRISE_MEMBRE_AJOUTER`, `ENTREPRISE_MEMBRE_RETIRER`, `ENTREPRISE_ROLE`, `ENTREPRISE_POSTE_AJOUTER`,
  `ENTREPRISE_POSTE_RETIRER` ; la préparation est en catégorie Objet (`PREPARATION`).

## 3. Commandes (staff : `shinobicore.entreprise`, opérateur, ou Modérateur du Poste de garde et au-dessus)

Noms à plusieurs mots **entre guillemets** ; cible = pseudo, nom RP ou `#N°`.

| Commande | Effet |
|---|---|
| `/entreprise creer <restaurant\|forge\|echoppe> "<nom>" [responsable]` | Crée ; le responsable éventuel devient gérant |
| `/entreprise supprimer <entreprise> confirmer` | Supprime (sans `confirmer` : rappel de ce qui sera perdu) |
| `/entreprise renommer <entreprise> "<nouveau nom>"` | Renomme |
| `/entreprise membre ajouter <entreprise> <cible> [gerant\|employe]` | Ajoute (employé par défaut) ; le joueur est prévenu |
| `/entreprise membre retirer <entreprise> <cible>` | Retire (un autre gérant devient responsable s'il y en a un) |
| `/entreprise membre role <entreprise> <cible> <gerant\|employe>` | Change le rôle |
| `/entreprise poste ajouter <entreprise> [libellé]` | Marque le bloc / meuble **visé** comme poste |
| `/entreprise poste retirer` | Retire le poste **visé** |
| `/entreprise info <entreprise>` · `/entreprise liste [type]` | Consulter |
| `/entreprise` (joueur) | Ses entreprises et son rôle |

Exemple pour Konoha : `/entreprise creer restaurant "Ichiraku Ramen" #00042`, puis viser le fourneau :
`/entreprise poste ajouter ichiraku_ramen "fourneau gauche"`, puis `/entreprise membre ajouter ichiraku_ramen Ayame`.

## 4. Plus tard : le menu Faction / entreprise géré par les joueurs (conçu, **non codé**)

Même logique que les sections de la Faction ([`FACTION_PROPOSITION.md`](./FACTION_PROPOSITION.md) §2, §5) : le staff
crée l'entreprise et nomme le responsable ; ensuite **le gérant la fait vivre** depuis un onglet « Entreprise » du menu
Faction (touche J) :

- **membres** : le gérant recrute et renvoie ses employés (demande acceptée par le joueur, journalisée) ; le staff garde
  la main (contre-passer, retirer un gérant) ;
- **postes** : le gérant ne crée pas de postes (règle RedLine des zones de faction : le staff pose les lieux), il peut
  seulement les **nommer** ;
- **caisse d'entreprise** (après le trésor de la Faction, F4) : recettes déclarées, salaires, achats d'ingrédients
  groupés ; journal en ajout seul ;
- **accès** : portes et coffres de l'entreprise (même mécanique que les coffres de section, F5) ;
- **rattachement** : une entreprise peut dépendre d'une section du village (la forge de l'armurerie) ou être privée.

Ce qu'il faudra : un paquet `reborn:faction` étendu (onglet Entreprise filtré par droits), des commandes joueur
`/entreprise recruter|renvoyer|role` réservées aux gérants, et l'écran dans `fr.reborn.hud.menu.faction`.
