# ADR 0006 — Deux monnaies : Ryo et RBCoins (retrait de « ZK Coin »)

**Statut** : **Proposé** — 2026-09-08, en attente de validation par la direction
**Documents liés** : [`AUDIT_COHERENCE.md §4`](../AUDIT_COHERENCE.md),
[`PLAN_CONCEPTION_LAUNCHER.md §11`](../../PLAN_CONCEPTION_LAUNCHER.md)

## Contexte

Trois noms de monnaie circulent dans les documents et sur le Trello public, pour deux
concepts seulement :

| Nom | Où | Rôle supposé |
|---|---|---|
| **Ryo** | in-game (boutique de tenues, carte Trello « Drop rates Ryo ») | gagnée en jeu |
| **RBCoins 💎** | Trello, carte *Bug Bounty* | récompense hors-jeu |
| **ZK Coin** | Trello *v1.1 Boutique launcher*, `PLAN` §11 | achetée en euros (Stripe) |

« ZK » vient de **Zenkai**, la référence visuelle qui a servi de modèle au launcher
jusqu'en juin 2026, avant le pivot vers l'identité **Akatsuki**. Le nom d'une marque
tierce abandonnée ne peut pas rester sur le produit payant.

Contrainte externe : l'**EULA Mojang** interdit de vendre des avantages de gameplay.
La monnaie achetée est donc strictement cosmétique — ce que la séparation des deux
monnaies rend lisible plutôt que d'avoir à l'expliquer.

## Décision

**Deux monnaies, pas trois.**

| | **Ryo** | **RBCoins 💎** |
|---|---|---|
| Obtention | gagnée en jeu : RP, combat, quêtes | achetée en euros (Stripe, v1.1) **ou** gagnée via Bug Bounty |
| Dépense | boutique in-game : tenues, consommables | cosmétiques premium |
| Portée | serveur Minecraft | launcher (network Reborn) |
| Conversion | **aucune, dans aucun sens** | — |

**« ZK Coin » est retiré** de toute la documentation, du Trello et du code à venir.

L'absence de conversion est délibérée : elle empêche toute lecture « pay-to-win » et
supprime le besoin d'équilibrer une économie face à un moyen de paiement.

## Conséquences

- ✅ Un joueur comprend en une phrase : *ce que je joue* vs *ce que j'achète*.
- ✅ La conformité EULA est structurelle, pas déclarative.
- ✅ Le Bug Bounty récompense en RBCoins — la valeur perçue est réelle sans toucher au
  gameplay.
- ⚠️ Le barème du Bug Bounty est encore en `X à X RBCoins` sur le Trello. Il doit être
  chiffré avant l'ouverture publique de la beta.
- ⚠️ Renommage à faire quand la boutique launcher sera implémentée (v1.1) : aucun code
  n'est encore concerné, seulement de la documentation et des cartes Trello.

## À valider

Si la direction préfère un autre nom pour la monnaie premium, il se substitue à
« RBCoins » partout — mais **la structure à deux monnaies non convertibles reste**,
c'est elle qui porte la décision, pas le nom.
