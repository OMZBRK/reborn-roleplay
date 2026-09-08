# ADR 0004 — Abandon de MCEF pour le fond du menu principal

**Statut** : Accepté — 2026-08-08
**Documents liés** : [`MIGRATION_26.1.md`](../MIGRATION_26.1.md)

## Contexte

Le menu principal custom de `mod-hud` devait afficher un fond animé riche (scène 3D du
joueur, effets) rendu par **Chromium embarqué via MCEF** (`mcef-modern` en 26.1). Ça
marchait en 1.21.1.

En 26.1 sous **JDK 25**, le binding `jcef` lève une exception sur le thread
`AWT-EventQueue-0` immédiatement après `createBrowser`. Conséquence : `onPaint` n'est
jamais appelé, la texture GPU n'est jamais alimentée, le browser reste transparent et
le panorama vanilla passe au travers.

Diagnostic mené jusqu'au bout : un probe HTML minimal (page au fond rouge uni) reste
invisible, y compris depuis un chemin de fichiers strictement ASCII. Ce n'est donc ni
un problème de contenu, ni un problème de chemin — c'est le binding natif sous JDK 25.

## Décision

**Abandonner MCEF.** `mcef-modern` est retiré du modpack et des dépendances de
`mod-hud`. Le fond du menu est remplacé par un **dégradé sombre → crimson brandé**,
rendu procéduralement (`DynamicPlayerBackground` réécrit).

## Conséquences

- ✅ Plus de dépendance à un binding natif Chromium fragile, ni des ~250 Ko de jar +
  runtime associé.
- ✅ Le fond procédural est cohérent avec la règle « chrome procédural, PNG seulement
  pour l'art » de [`MOD_HUD_REDESIGN.md`](../MOD_HUD_REDESIGN.md) — net à tous les GUI
  Scale, recolorable au thème.
- ❌ On perd la possibilité d'afficher du contenu web (patch notes riches, vidéo,
  carousel) directement dans le menu in-game.
- ➡️ **Si le besoin revient** : le passer par le **launcher** (qui est déjà une WebView
  Tauri, donc le fait nativement) plutôt que par le client Minecraft. Ne pas retenter
  MCEF sans preuve qu'un build fonctionne sous JDK 25.
