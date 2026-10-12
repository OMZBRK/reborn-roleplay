# Missions et entraînements — idées et formats d'animation

> **Ce que contient cette fiche :** les idées de missions et d'entraînements à animer en RP, plus les formats de
> rendu déjà utilisés (rapports, affiches, vidéos). Il n'y a **pas encore de système de missions en jeu** : tout ceci
> est animé à la main par le staff ou les joueurs. Ces idées sont la matière première d'un futur système.
> Sources : deux conversations du 08 au 10/10/2026, consolidées ici le 2026-10-12.

---

## 1. Observations venues du terrain

- **Zones de ressources.** Une zone contrôlée par un autre village empêche la récolte : le plugin bloque, et en RP
  ce sont les marchands qui refusent au nom du village qui tient la zone. C'est une bonne accroche narrative : le
  blocage technique s'habille très bien en RP. À garder si un système de missions arrive (missions de récolte,
  de négociation ou de reprise de zone).
- **Missions de rang C jouées** : par exemple, un ravitaillement Konoha → Ryukon (recensement des besoins, collecte
  en deux équipes nourriture / médical, livraison par la route marchande, tête et fermeture de colonne). Le schéma
  « reconnaissance → collecte → convoi → retour » se reproduit facilement.
- **Le personnage du joueur** (Yoru Uchi) écorche volontairement les noms des autres. Dans ses rapports, on garde les
  noms tels qu'il les écrit, fautes comprises.

## 2. Entraînements originaux

| Idée | Principe |
|---|---|
| Le sensei menteur | Le sensei est un clone qui donne des consignes de plus en plus absurdes ; trouver l'original. |
| Un seul jutsu | Chacun tire un jutsu au sort et doit gagner un duel avec lui seul. |
| Le banquet empoisonné | Un plat sur cinq a un poison léger (voix aiguë, vision floue) ; le trouver sans goûter. |
| Genjutsu en boucle | La même matinée se répète ; repérer le détail qui change pour casser l'illusion. |
| Seul contre le village | Prime sur la tête de l'élève jusqu'au coucher du soleil ; tout le village le traque. |
| Échange de corps raté | Un Yamanaka rate son jutsu ; l'entraînement se fait dans le corps d'un coéquipier. |
| Joute politique (joué le 10/10) | Trois joueurs négocient deux à deux pendant 30 s (le troisième observe), un dé fixe le nombre de techniques de chacun (forts et faibles), puis combat. On travaille la trahison et la loyauté. |

Classiques simples, en complément : grimper aux arbres sans les mains, marcher sur l'eau, les grelots, parcours
de kunai, cache-cache avec Henge et Kawarimi, duel taijutsu seul, papier à chakra.

## 3. Missions

**Rang D, simples :** retrouver le chat Tora, livrer un colis avec des embûches, garder une boutique, promener les
chiens Inuzuka, désherber un champ qui cache quelque chose.

**Rang C/B, originales :**
- *Le procès du chat Tora* : défense contre accusation. Le coupable est un chat ninja invoqué.
- *Le fantôme de l'Académie* : un jutsu scellé par un ninja mort s'active quand on prononce un mot précis.
- *Le mariage arrangé* : la mariée a elle-même engagé les assassins. Il faut choisir son camp.
- *Le village où personne ne ment* : un genjutsu de vérité fait sortir les secrets RP de chacun.
- *L'invocation perdue* : un crapaud cherche son invocateur, mort depuis 20 ans.
- *Le déserteur qui veut rentrer* : l'escorter alors que tout le monde veut sa peau. Est-il sincère ?
- *Le tournoi clandestin* : celui qui perd se fait sceller un jutsu (lien possible avec les slots de rang S).
- *Le rouleau qui parle* : le parchemin à livrer supplie qu'on ne le livre pas.

**Dilemmes :**
- *Le choix de l'Hokage* : deux villages appellent à l'aide, on n'en sauve qu'un.
- *Le témoin gênant* : l'enfant sauvé a vu un secret du village.

**Astuces d'animation :** un rebondissement par mission, une récompense RP (ryō, objet, réputation), des dés pour
les moments clés.

## 4. Formats de rendu déjà produits

| Format | Contenu type |
|---|---|
| **Rapport de mission** (PNG 1080 px de large, parchemin aux couleurs de Konoha) | En-tête avec sceau et informations, résumé, chronologie par étapes, fiches des individus rencontrés (sans inventer de noms), renseignements, bilan, signature et tampon (« Transmis à… », « Mission réussie », « Entraînement clos »). Le HRP (plugin, crash) est toujours traduit en RP (marchands, genjutsu). |
| **Affiche d'entraînement** | Parchemin minimal qui ne dit **pas** en quoi consiste l'entraînement : symbole de Konoha, tampon rouge, organisateur, lieu et heure. |
| **Vidéo « rapport d'entraînement »** | Intro façon Ketto (Sharingan, logo au pinceau), titres par épreuve, ralentis, fin avec tampon « VALIDÉ », musique originale composée (taiko, guitare, koto). Faite avec FFmpeg. Prévoir une version compressée pour Discord. Une capture OBS donne un bien meilleur rendu qu'une vidéo filmée à la webcam. |

Les fichiers produits (PNG, MP4) étaient dans les dossiers temporaires de ces conversations : ils ne sont **pas**
dans le dépôt.

## 5. Pistes pour un futur système en jeu

Pas planifié dans la roadmap beta. Si ça arrive : tableau de missions par rang, missions de zone de ressources
liées au contrôle territorial, rapport de mission généré automatiquement (même mise en page que §4), récompense en
XP via `XpService.reward` (cf. [`STATS_ET_PROGRESSION.md`](./STATS_ET_PROGRESSION.md)).
