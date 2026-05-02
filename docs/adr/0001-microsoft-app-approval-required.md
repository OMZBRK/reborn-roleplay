# ADR 0001 — Approbation Microsoft du Client ID pour Minecraft Services

**Statut** : Accepte — 2026-05-03
**Contexte de la decision** : test live de la Semaine 2 (auth MS).

## Contexte

Pour authentifier un compte Microsoft contre `api.minecraftservices.com`, il
ne suffit pas d'avoir une App Registration Azure valide. Depuis 2024,
Microsoft impose qu'un Client ID soit **explicitement approuve** pour
appeler les Minecraft Services. Sans approbation, l'endpoint
`/authentication/login_with_xbox` retourne `403 Forbidden` avec le message
`Invalid app registration`.

Concretement, ca veut dire que la chaine OAuth → XBL → XSTS marche tout de
suite avec une simple App Registration, mais la derniere etape echoue tant
que Microsoft n'a pas valide le Client ID.

## Decisions

1. **On garde notre Client ID** (`affd0327-8cb3-479c-80aa-a8be73b8ba4d`)
   dans `.env` et on lance la procedure d'approbation. Pas de bidouille
   avec un Client ID emprunte (Prism, Modrinth, MultiMC) : le redirect URI
   `http://localhost:53682/callback` n'est de toute facon pas enregistre
   sur leurs apps, donc OAuth refuse direct.

2. **Procedure d'approbation** :
   - Formulaire Microsoft : <https://aka.ms/mce-reviewappid>
   - Documentation officielle :
     <https://help.minecraft.net/hc/en-us/articles/16254801392141>
   - Delai annonce communaute : quelques jours a 2 semaines.
   - Pre-requis : profil developpeur Microsoft + nom de produit + lien
     vers le site/Discord du launcher.

3. **Pendant l'attente** : on n'est pas bloque sur le reste du
   developpement.
   - Semaine 3 (manifest signe, telechargement) ne touche pas a Mojang.
   - Semaine 4 (lancement Minecraft) a besoin de l'auth, mais on peut
     coder le spawn JVM et le FS watcher sans token reel et plug le
     vrai token quand l'approbation tombe.
   - Le code de la Semaine 2 reste intact : des que Microsoft approuve,
     on retente le bouton et tout marche sans modification.

## Consequences

- ✅ Le Client ID Reborn ne change pas, donc tous les utilisateurs en prod
  utiliseront le bon ID des le premier jour.
- ✅ Pas de path de fallback "mock" qui pourrait fuiter en prod.
- ⚠️ Le test end-to-end de l'auth MS est differe de quelques jours.
- ⚠️ Bien penser a re-tester `pnpm tauri dev` + bouton MS apres
  l'approbation pour valider que rien d'autre ne casse entretemps.

## A faire

- [ ] Soumettre le Client ID `affd0327-...` via le formulaire Microsoft
- [ ] Mettre l'email de notification a jour dans le profil developpeur MS
- [ ] Ajouter une checklist "post-approbation" dans la doc de release v1.0
