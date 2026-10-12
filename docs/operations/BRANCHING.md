# Modèle de branches & workflow multi-machines

> Source de vérité pour **où vit quoi** et **comment travailler depuis
> plusieurs postes** (ce PC, un autre Windows, un Mac). Objectif : tout est sur
> `origin`, rien de critique en local-only, des pushs simples.

## Règle d'or

**Rien d'important ne reste en local.** Sur un nouveau poste, tu ne vois que les
branches **distantes** (`git branch -r`). Donc : on commit tôt, on push souvent,
et on ne laisse jamais du travail sur une branche locale non poussée.

## Les branches qui comptent (sur `origin`)

| Branche | Rôle | Déploiement |
|---|---|---|
| **`main`** | Tronc public / releases / docs. Tout converge ici à terme (via PR). | — |
| **`feature/emote-system`** | **Intégration JEU** : mods Fabric (`minecraft/mod-*`) + plugins Paper (`minecraft/shinobi/*`, `minecraft/plugin-*`). | par **SFTP + manifest signé** (les joueurs auto-update ; les plugins par SFTP + restart). |
| **`feature/migrate-26.2`** | **Intégration API/PANEL** : `apps/api`, `apps/admin`, `packages/*`. C'est ce que **le VPS déploie**. | `cd /opt/reborn && git pull && docker compose -f infra/docker-compose.prod.yml --env-file .env.prod up -d --build api admin` |
| `feature/*`, `fix/*` | Travail court, branché sur l'intégration concernée, mergé quand vert. | — |
| `archive/*` | Congelé, ne pas merger. | — |

### Pourquoi DEUX branches d'intégration ?

Les deux moitiés du projet se déploient par des **mécanismes différents** :
le jeu par **SFTP/manifest** (pas de `git` côté serveur de jeu), l'API/panel par
**`git pull` + Docker** sur le VPS. `apps/api/src/files` a **divergé** entre `main`
et `feature/migrate-26.2` (structure de scopes différente) → une modif panel/API
destinée à la prod se met sur **`feature/migrate-26.2`**, pas seulement sur `main`.

> **Objectif à terme : tronc unique `main`.** Cela demande une consolidation
> soignée (résoudre la divergence `files.service.ts`, éviter de ressusciter les
> fichiers audio supprimés) — à faire en session dédiée + vérifiée, pas à chaud.

## Travailler depuis un nouveau poste (Mac / autre Windows)

```bash
git clone https://github.com/OMZBRK/reborn-roleplay.git
cd reborn-roleplay
pnpm install

# Travail JEU (mods / plugins) :
git switch feature/emote-system

# Travail API / PANEL :
git switch feature/migrate-26.2
```

Prérequis outillage (voir `CLAUDE.md`) : Node 20+/pnpm 10+, JDK 25 pour les
mods/plugins, Maven pour les plugins Shinobi, Docker pour Postgres/Redis.
Sur Mac : les `gradlew` sont exécutables (`.gitattributes` gère le bit +x).

## Reprise rapide (changer de poste au quotidien)

Sur une machine **déjà configurée** (elle a déjà `.env` + l'outillage d'une
session précédente), reprendre le travail se résume à :

```bash
git fetch --all --prune
git switch feature/emote-system     # JEU (mods/plugins)   — OU
git switch feature/migrate-26.2     # API / PANEL / éditeur
git pull
pnpm install                        # nouvelles deps éventuelles
```

⚠️ **`git pull` ne met à jour que la branche courante.** Si tu changes de sujet
(jeu ↔ api/panel), fais `git fetch` puis `git switch` sur la bonne branche avant
le `pull`.

### Ce que `git pull` ne rapporte PAS (jamais dans git — à avoir une fois par machine)

| Élément | Pourquoi absent | Quoi faire |
|---|---|---|
| `.env`, `.env.prod`, `secrets/*.pem` | gitignorés (secrets) — seul `.env.example` est versionné | les copier une fois par un canal sûr (gestionnaire de mots de passe / clé USB), **pas** git |
| `node_modules/` | jamais versionné | `pnpm install` à chaque machine (et après ajout de deps) |
| JDK 25 / Maven / Docker | outillage local | installer une fois (cf. `CLAUDE.md`) |
| Données Postgres locales (techniques créées en test) | contenu runtime, pas du code | non synchronisé ; les **migrations** Prisma s'appliquent seules |

Les **worktrees** locaux (`.claude/worktrees/`) sont propres à un poste : sur une
autre machine, pas besoin — on `git switch` directement la branche d'intégration.

## Cycle de travail

```bash
git switch <branche-d-intégration>
git pull --ff-only
git switch -c fix/mon-sujet            # branche courte
# … commits …
git push -u origin fix/mon-sujet
gh pr create --base <branche-d-intégration>   # ou merge direct si tu es seul
```

- **Commits** : Conventional Commits (`feat(scope):`, `fix(scope):`…).
- **Un seul commiteur à la fois** sur un même working tree (ne pas faire
  `git add -A` à plusieurs — stager fichier par fichier).

## PR ouvertes

| PR | Branche | Sujet |
|---|---|---|
| #11 | `feature/modrinth-sync` | Suivi auto des MAJ de mods (API/bot) |
| #13 | `feat/blockbench-creator-tools` | Creator Tools Blockbench |

## Nettoyage effectué (2026-09-16)

- Restauré `feature/migrate-26.2` sur `origin` (branche de déploiement api/admin,
  supprimée par erreur lors de l'archivage) — le VPS peut de nouveau `git pull`.
- Poussé tout le travail local-only (moteur de techniques : jeu sur
  `feature/emote-system`, api/panel sur `feature/migrate-26.2`).
- Supprimé les branches distantes redondantes (contenu absorbé dans les branches
  d'intégration) : `feat/technique-engine`, `feat/ability-compiler`,
  `fix/ig-staff-on-emote`, `fix/retours-test-staff` (PR #14 fermée, déjà en prod).
