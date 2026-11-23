# Mudras au clavier et portes scellées

> Fiche du système. Demande du propriétaire du 14/11 : « je veux aussi que tu fasses le principe d'ouverture de porte
> […] et aussi le principe de mudra (que je veux avoir implémenté que ce soit pour ouvrir les portes ou encore pour les
> apprentissages de techniques) ». Remplace le rejet du 31/10 (« c'est trop pour ouvrir une porte ») : cela ne concerne
> que les **portes scellées** posées par le staff, pas les portes ordinaires.
>
> **État au 15/11 : publié (0.4.203) et essayé en jeu, portes validées ; livré éteint.** Mod : `reborn-hud` 0.4.203 (paquet `mudra/`), vérifié au banc
> de capture. Serveur : ShinobiCore (paquet `mudra/`), 133 tests verts, **jamais lancé sur un vrai serveur** (l'animation
> de la dalle en particulier). Détail technique : [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §1 sexies et
> [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md).

## 1. Le principe

- **Douze signes**, dans l'ordre du zodiaque : Rat, Buffle, Tigre, Lièvre, Dragon, Serpent, Cheval, Bélier, Singe, Coq,
  Chien, Sanglier.
- Une touche (**U** par défaut) ouvre la **barre de mudras** en bas de l'écran : douze cases laquées bordées d'or, dans
  le style des menus Reborn, le dessin du signe dans chacune, le nom au-dessus, la touche dessous. Les signes se composent sur la **rangée des chiffres** : 1 à 9, 0, et les deux touches à droite du zéro.
  Tant que la barre est ouverte, ces touches ne changent plus l'objet tenu.
- **Le temps est compté** : une jauge dorée se vide sous les cases (5 s) ; chaque signe en rend un peu (0,7 s).
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
- **Essais en série** (16/11) : au cinquième essai manqué devant une même porte en une minute, la porte **ne répond
  plus à ce joueur pendant deux minutes**, même à la bonne suite. Rien ne le lui dit (aucun indice) ; le staff le
  lit au journal. Se déconnecter ne lève pas le blocage ; un redémarrage du serveur ou un `/sc reload`, si. Devant
  deux portes proches (un sas), l'essai compte pour les deux. Celui qui pose la bonne suite sans que la porte
  s'ouvre (un pas trop loin, dalle encore en mouvement) n'est pas compté.
- La dalle ne se casse pas, ne se pousse pas, n'explose pas.

**Côté staff** — l'outil **Portes scellées** du Poste de garde (F7 › Outils › Monde, depuis la 0.4.206), ou la
commande `/porte` (Modo et plus pour créer ; Helper pour lister).

Avec l'outil, tout se fait à la souris :

- **Liste** : les portes proches, leur état (fermée, levée, « redescend dans 5 s »), le nombre de joueurs bloqués ;
  elles sont cernées dans le monde, pour toi seul. L'interrupteur du système est en tête (Admin).
- **Nouvelle porte** : clic sur un bloc pour le premier coin, Maj + clic pour le second ; l'outil dit tout de suite
  si la dalle est valable ou pourquoi elle est refusée. Puis un nom, la suite (on clique les dessins des signes,
  dans l'ordre), la hauteur de levée, « Sceller la porte ». Il faut se tenir à 8 blocs de la dalle.
- **Fiche** : ouvrir pour essai, fermer, changer la suite, renommer, changer la levée, aller devant, retirer le
  sceau. La ligne **Blocages** nomme les joueurs que la porte ne reconnaît plus et le temps qui reste ; « Lever »
  rend la porte à un joueur sans toucher aux autres.
- Un **Helper** voit les portes, leur état, le nombre de signes et les blocages ; jamais la suite, et il ne modifie
  rien (il peut seulement « Aller devant »).

La commande reste disponible :

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
voient avec `/porte info`. Le blocage des essais en série (au 5ᵉ essai manqué en une minute, 2 minutes de silence :
`mudras.portes.essais-max`, `essais-fenetre-s`, `blocage-s`) ne freine que celui qui enchaîne. Un joueur qui s'en
tient à 4 essais par minute n'est jamais bloqué : une porte à 2 signes (144 suites) lui cède en une demi-heure
environ (36 minutes au pire), avec seulement les `MUDRA_ESSAI` au journal. **Donner au moins 4 signes à une porte
qui compte** (20 736 suites).

## 3. L'apprentissage d'une technique

Quand une séance d'apprentissage arrive aux mudras, la barre s'ouvre **en mode guidé** : le nom de la technique
s'écrit au-dessus avec l'avancement (« Boule de feu · 2 / 4 »). **Rien ne dit quel signe est attendu** (décision du
16/11 : « le joueur doit les connaître depuis le parchemin ou via professeur ») : la suite se compose **de mémoire**.
Le joueur la lit sur son parchemin avant de s'entraîner, ou la tient d'un maître qui la lui montre ou la lui dit en
RP (celui qui a appris une technique relit sa suite entière sur sa fiche, onglet Techniques). Le serveur ne donne
même plus la suite au mod, seulement le nombre de signes : un client modifié n'y gagne rien. Chaque signe doit
arriver dans le délai de la difficulté (plus 1,5 s). Un mauvais signe ou un délai dépassé fait échouer la séance.

Sans le système (éteint, ou joueur sans le mod à jour), l'ancien mini-jeu reste : titres à l'écran et clic gauche.
Système allumé mais joueur empêché de signer par son état (blessé, en garde, en prison…) : la séance s'arrête sans
compter, l'ancien mini-jeu ne prend pas le relais (il nommerait chaque signe).

**Chaque technique a sa suite, toujours la même** (15/11). Huit techniques la déclarent dans `abilities.yml`
(`mudras:`). Pour les autres, la suite est **tirée de l'identifiant de la technique** : fixe, la même à chaque séance,
à chaque redémarrage (2 signes aux rangs E et D, 4 aux rangs C et B, 6 aux rangs A et S). Avant, elle était tirée au
hasard à chaque séance : on n'apprenait rien. Pour les arts à signes (ninjutsu, kekkei genkai de nature, genjutsu,
kinjutsu, juinjutsu, senjutsu : 133 techniques), cette suite appartient à la technique : le parchemin et la fiche la
montrent aussi. Pour les autres arts dont la séance passe par les mudras (armes hors kenjutsu, marionnettes, dōjutsu :
48 techniques), elle ne sert qu'à la séance : **le parchemin la montre** (16/11, sans quoi elle ne s'apprendrait
nulle part), pas la fiche ni l'incantation. Écrire `mudras:` sur une technique remplace sa suite tirée.

**Le lancer en combat n'utilise pas la barre** (précision du propriétaire, 15/11 : « quand je te parle des mudras
c'est pour le système d'apprentissage »). L'incantation reste la barre bleue qui avance seule ; elle affiche
maintenant le nom des signes de la technique.

## 4. Ce qui est provisoire

- Les **dessins des signes** ne sont plus provisoires : ce sont les vrais (15/11), de Schlum (Wikimedia Commons,
  licence CC BY-SA 4.0), fournis par le propriétaire. Le crédit de l'auteur est dans le mod (`gui/mudras/credits.txt`)
  et doit y rester : c'est la condition de la licence. Le **visuel de la barre** est celui des menus Reborn depuis le
  16/11 (maquette R2).
- La **pose** du personnage : une seule pour les douze signes (celle du Test de la Feuille).
- Les **sons** : synthèse (`tools/ui-art/gen_mudra_sfx.py`), adoucis le 16/11 à la demande du propriétaire (« un son
  plus subtil ») ; à juger en jeu.
- L'**outil du Poste de garde** (F7 › Outils › Portes scellées) existe depuis la 0.4.206 (maquettes validées le
  16/11 : « 3A et on garde les blocages »). Il ne déplace pas la dalle d'une porte existante : on la retire et on la
  repose.

## 5. Allumer le système pour l'essayer

Le système arrive éteint. En jeu, un Admin tape `/porte systeme on` : la touche U ouvre alors la barre pour tout le
monde. `/porte systeme off` l'éteint. L'état est gardé au redémarrage (`plugins/ShinobiCore/mudras.yml`).
