# Mudras au clavier et portes scellées

> Fiche du système. Demande du propriétaire du 14/11 : « je veux aussi que tu fasses le principe d'ouverture de porte
> […] et aussi le principe de mudra (que je veux avoir implémenté que ce soit pour ouvrir les portes ou encore pour les
> apprentissages de techniques) ». Remplace le rejet du 31/10 (« c'est trop pour ouvrir une porte ») : cela ne concerne
> que les **portes scellées** posées par le staff, pas les portes ordinaires.
>
> **État au 14/11 : codé, non publié, livré éteint.** Mod : `reborn-hud` 0.4.203 (paquet `mudra/`), vérifié au banc
> de capture. Serveur : ShinobiCore (paquet `mudra/`), 133 tests verts, **jamais lancé sur un vrai serveur** (l'animation
> de la dalle en particulier). Détail technique : [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §1 sexies et
> [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md).

## 1. Le principe

- **Douze signes**, dans l'ordre du zodiaque : Rat, Buffle, Tigre, Lièvre, Dragon, Serpent, Cheval, Bélier, Singe, Coq,
  Chien, Sanglier.
- Une touche (**U** par défaut) ouvre la **barre de mudras** en bas de l'écran : douze médaillons, le nom au-dessus, la
  touche dessous. Les signes se composent sur la **rangée des chiffres** : 1 à 9, 0, et les deux touches à droite du zéro.
  Tant que la barre est ouverte, ces touches ne changent plus l'objet tenu.
- **Le temps est compté** : un filet doré raccourcit sous les médaillons (5 s) ; chaque signe en rend un peu (0,7 s).
  À zéro, la barre se referme. Un second appui sur U, ou Échap, la referme aussi.
- **Le personnage fait les signes** : il joint les mains, on entend le claquement. Les joueurs proches (24 blocs) le
  voient et l'entendent. Une pose pour les douze signes aujourd'hui (provisoire).
- On ne peut pas composer KO, mort, entravé, en cinématique, en douane (mêmes règles qu'une technique à mudras).

## 2. Les portes scellées

- Une porte scellée est **une dalle de blocs** posée par le staff, avec **une suite de 2 à 12 signes**. Rien ne la
  désigne : pas de poignée, pas de texte, pas d'invite. **Il faut connaître la suite** : elle se transmet en RP.
- Devant la dalle (6 blocs), le joueur ouvre la barre et compose. Si la suite est la bonne, **la dalle coulisse vers
  le haut** en 3 s, avec son bruit de pierre, puis un choc sourd. Elle redescend 8 s plus tard, dès que le passage est
  libre.
- **Aucun indice** : la barre s'ouvre partout, de la même façon. Le jeu ne dit jamais qu'une porte est proche, ni
  qu'un signe est juste. Une mauvaise suite se dissipe en silence.
- Les essais ratés devant une porte fermée sont notés au journal du staff (qui, où, quelle suite) : forcer une porte
  en essayant des suites se voit.
- La dalle ne se casse pas, ne se pousse pas, n'explose pas.

**Côté staff** — commande `/porte` (Modo et plus pour créer ; Helper pour lister) :

| Commande | Effet |
|---|---|
| `/porte coin 1` puis `/porte coin 2` | Vise un bloc : les deux coins opposés de la dalle |
| `/porte creer <nom> <signes…>` | Crée la porte ; nom en un mot (`_` = espace) ; signes par leur nom (`tigre serpent rat`) ou leur rang sur la barre (`3 6 1`) |
| `/porte signes <id> <signes…>`, `nom`, `leve` | Change la suite, le nom, la hauteur de levée |
| `/porte liste`, `info [id]`, `tp <id>` | Consulter |
| `/porte ouvrir <id>`, `fermer <id>` | Essai sans composer |
| `/porte retirer <id>` | Retire le sceau (les blocs restent) |
| `/porte systeme on\|off` | Allume ou éteint tout le système (Admin) |

Limites d'une dalle : 9 blocs par côté au plus, 3 d'épaisseur, blocs simples seulement (ni coffre, ni panneau, ni
bannière, ni cadre ou tableau accroché : la création est refusée). On ne peut rien poser ni accrocher sur une dalle.

La suite d'une porte n'est jamais écrite au journal du staff (elle y apparaît masquée) ; seuls les Modos et plus la
voient avec `/porte info`. **Pas de blocage contre les essais en série** : une porte à 2 signes se force en quelques
minutes (144 suites), les essais sont seulement tracés. Donner au moins 4 signes à une porte qui compte.

## 3. L'apprentissage d'une technique

Quand une séance d'apprentissage arrive aux mudras, la barre s'ouvre **en mode guidé** : le nom de la technique
s'écrit au-dessus avec l'avancement (« Boule de feu · 2 / 4 »), le signe attendu reste clair et souligné d'or, les autres
pâlissent. Il faut presser la bonne touche dans le délai de la difficulté (plus 1,5 s pour trouver la touche). Un
mauvais signe ou un délai dépassé fait échouer la séance, comme avant.

Sans le système (éteint, ou joueur sans le mod à jour), l'ancien mini-jeu reste : titres à l'écran et clic gauche.

**Pas encore fait** : composer les mudras pour **lancer** une technique en combat. L'incantation actuelle (une barre
qui avance seule) est inchangée ; à décider après l'essai des portes et de l'apprentissage.

## 4. Ce qui est provisoire

- Les douze **médaillons** ne sont plus provisoires : ce sont les vrais dessins des signes (15/11), de Schlum
  (Wikimedia Commons, licence CC BY-SA 4.0), fournis par le propriétaire. Le crédit de l'auteur est dans le mod
  (`gui/mudras/CREDITS.txt`) et doit y rester : c'est la condition de la licence.
- La **pose** du personnage : une seule pour les douze signes (celle du Test de la Feuille).
- Les **sons** : synthèse (`tools/ui-art/gen_mudra_sfx.py`), jamais écoutés par le propriétaire.
- L'**outil du Poste de garde** (F7) pour poser les portes à la souris n'existe pas encore : commande seulement.

## 5. Allumer le système pour l'essayer

Le système arrive éteint. En jeu, un Admin tape `/porte systeme on` : la touche U ouvre alors la barre pour tout le
monde. `/porte systeme off` l'éteint. L'état est gardé au redémarrage (`plugins/ShinobiCore/mudras.yml`).
