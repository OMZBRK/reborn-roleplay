# Bibliothèques v2 — proposition (à valider)

> **Statut : proposition du 2026-10-12**, en réponse à la demande : « il faut le principe de bibliothèque clan,
> ANBU, palais du Kage… (aujourd'hui publique, réservée, privée) » et « mettre les parchemins que l'on possède dans
> la bibliothèque, pour qu'ils y restent : chacun peut faire sa propre bibliothèque avec ses rouleaux ». Rien
> n'est codé. Le système actuel est décrit dans [`APPRENTISSAGE.md`](./APPRENTISSAGE.md) §3.

---

## 1. Le principe

Une bibliothèque a désormais **deux étagères** :

| Étagère | Ce qu'il y a dessus | D'où ça vient | Durée |
|---|---|---|---|
| **Le fonds** | Des rouleaux **déposés** par des joueurs ou par le staff | Ce que les gens y posent | **Permanent** : le rouleau reste tant que personne ne le prend |
| **Les arrivages** | Des rouleaux **tirés au sort** (le tirage actuel) | Le serveur, selon le type de bibliothèque | Renouvelé toutes les 3 h, comme aujourd'hui |

Une bibliothèque de joueur (personnelle, de clan) n'a **que le fonds** : elle se remplit avec ce que ses membres
y déposent. Une bibliothèque d'institution (Académie, palais, ANBU…) a **les deux**.

Le parchemin reste **un vrai objet unique** : le déposer le retire de l'inventaire, le prendre le retire de
l'étagère. Un rouleau S déposé garde son slot.

## 2. Les types de bibliothèque (remplacent Publique / Réservée / Privée)

| Type | Qui peut y prendre | Qui peut y déposer | Arrivages tirés au sort | Exemple |
|---|---|---|---|---|
| **Village** | Tout le monde | Tout le monde | Oui, modèle « Publique » (beaucoup de D) | Bibliothèque de Konoha |
| **Académie** | Académiciens et Genin du village | Instructeurs (grade Chūnin et plus) | Oui, D et C surtout | Salle des rouleaux de l'Académie |
| **Clan** | Membres du clan | Membres du clan | Non, sauf réglage staff | Archives Uchiha |
| **ANBU** | Fonction ANBU | Fonction ANBU | Oui, modèle « Privée » (B, A plus fréquents) | Quartier ANBU |
| **Palais du Kage** | Kage, conseil, bras droit | Kage | Oui, modèle « Privée » (A et S possibles) | Réserve du palais |
| **Hôpital** | Médics (rang médical) | Médics | Oui, catégorie Médical seulement | Bibliothèque de l'hôpital |
| **Personnelle** | Le propriétaire et ceux qu'il autorise | Le propriétaire | Non | Étagère chez soi |

- **Accès** : chaque type a un réglage `acces: libre | membres`.
  - En `membres`, un non-membre voit les rouleaux mais **ne peut pas les prendre** ; le staff peut ouvrir l'accès
    le temps d'un vol RP.
  - En `libre`, comme aujourd'hui : le contrôle est RP. La décision du 08/10 (accès libre) reste valable pour le
    type Village.
- **Les pourcentages de tirage** gardent les modèles actuels (Publique, Réservée, Privée), mais ils deviennent un
  réglage *interne* du type, plus le nom de la bibliothèque.

## 3. Déposer et prendre

- **Déposer** : dans l'écran de la bibliothèque, onglet « Fonds », glisser un rouleau de sa sacoche vers une case
  vide (ou bouton « Déposer » rouleau en main). Le rouleau garde son identité (son `parchemin_id`), ce qui préserve
  le slot S et le journal.
- **Prendre** : comme aujourd'hui (limite de poids). Chaque prise et chaque dépôt est **journalisé** (qui, quand,
  quel rouleau), visible par le propriétaire de la bibliothèque et par le staff.
- **Capacité** : 9 cases par meuble (le meuble actuel), plusieurs meubles = plusieurs bibliothèques reliées
  (option : une bibliothèque de clan qui s'étend sur trois étagères).

## 4. Idée à trancher : la lecture sur place

Aujourd'hui, une séance demande **le rouleau en main**. Proposition : on peut aussi faire une séance **devant la
bibliothèque**, sans emporter le rouleau, s'il est dans son fonds.

- **Pour** : la bibliothèque de clan devient un vrai lieu d'enseignement ; le rouleau ne se perd pas, ne se vole
  pas en route ; les maîtres enseignent sur place.
- **Contre** : moins de circulation d'objets, donc moins de vols RP.
- **Compromis** : lecture sur place **réservée aux bibliothèques Clan, Académie et Palais**, avec un maître
  présent obligatoire.

## 5. Ce que ça change dans le code (estimation)

| Partie | Changement | Effort |
|---|---|---|
| `Library` | `type`, `owner` (UUID ou clan ou fonction), `acces`, `fonds[]` (identifiants de rouleaux déposés), journal | Faible |
| `LibraryService` | Règles d'accès par type, dépôt/retrait, persistance du fonds | Moyen |
| `/bibliotheque creer <nom> <type> [propriétaire]` | Nouveau paramètre ; la bibliothèque personnelle se crée par le joueur sur un meuble qu'il possède | Faible |
| Mod : `BibliothequeScreen` | Deux étagères (Fonds / Arrivages), bouton Déposer, journal | Moyen |
| Mod : `BibliothequeStaffScreen` | Le choix de modèle devient un choix de type | Faible |

## 6. Questions pour l'utilisateur

1. La liste des types te va-t-elle ? (Village, Académie, Clan, ANBU, Palais du Kage, Hôpital, Personnelle)
2. En `membres`, un non-membre **voit** les rouleaux sans pouvoir les prendre, ou ne voit rien ?
3. Lecture sur place : oui, non, ou le compromis (§4) ?
4. Bibliothèque personnelle : le joueur la crée lui-même (sur un meuble Nexo acheté), ou c'est le staff ?
