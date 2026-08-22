# Claude Code hooks — notif Discord "Claude a fini"

`notify-discord.mjs` est un hook Claude Code qui envoie une notification Discord
(via le bot Reborn existant) quand une session Claude **termine son tour**
(`Stop`) ou **attend une action de ta part** (`Notification`). Tu peux ainsi
laisser le PC allumé, partir, et être pingé sur Discord quand c'est prêt.

## Chaîne

```
Claude Code (hook Stop/Notification)
   └─ node notify-discord.mjs   (lit l'event sur stdin, signe HMAC)
        └─ POST http://localhost:3001/webhooks/claude-notify   (bot Reborn)
             └─ DM Discord (ou salon dédié)
```

Même secret HMAC que les autres webhooks du bot (`REBORN_WEBHOOK_SECRET`), donc
rien de nouveau à provisionner côté sécurité. Le script **ne bloque jamais**
Claude : toute erreur (bot éteint, réseau) est avalée, sortie code 0.

## Prérequis

1. **Le bot doit tourner** (`pnpm bot:dev`) avec une build qui contient
   l'endpoint `/webhooks/claude-notify` (ajouté dans `apps/bot/src/webhook-server.ts`).
2. **Cible Discord** dans le `.env` racine — renseigne l'un des deux :
   ```dotenv
   DISCORD_CLAUDE_NOTIFY_USER_ID=<ton user id>      # DM (recommandé)
   # ou
   DISCORD_CLAUDE_NOTIFY_CHANNEL_ID=<id salon dédié>
   ```
   Trouver ton user ID : Discord → Paramètres → Avancé → **Mode développeur**,
   puis clic droit sur ton profil → **Copier l'identifiant**.
   > Sans cible configurée, le hook fonctionne mais le bot n'envoie rien
   > (pas de fallback sur le salon staff, pour ne pas le spammer).

## Activation (settings Claude Code)

Ajoute ce bloc dans `.claude/settings.local.json` (perso, non commité) **à la
racine du repo** — remplace le chemin par le chemin absolu réel du script :

```json
{
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "node \"D:\\\\Téléchargement - ALL\\\\RB - GESTION\\\\reborn-roleplay\\\\tools\\\\claude-hooks\\\\notify-discord.mjs\""
          }
        ]
      }
    ],
    "Notification": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "node \"D:\\\\Téléchargement - ALL\\\\RB - GESTION\\\\reborn-roleplay\\\\tools\\\\claude-hooks\\\\notify-discord.mjs\""
          }
        ]
      }
    ]
  }
}
```

- **`Stop`** fire à **chaque fin de tour** de Claude. En session interactive
  multi-messages c'est bavard ; en job de fond (tu lances puis tu pars) c'est
  exactement le signal « c'est fini ». Retire le bloc `Stop` si trop bruyant.
- **`Notification`** fire quand Claude attend une permission / une réponse.
- Après édition, lance `/hooks` dans Claude Code pour vérifier l'enregistrement.

## Test manuel (sans Claude)

Bot lancé + cible configurée, puis :

```pwsh
echo '{"hook_event_name":"Stop","cwd":"D:/.../reborn-roleplay","session_id":"test123"}' | node tools/claude-hooks/notify-discord.mjs
```

Tu dois recevoir le DM / le message dans le salon. Sinon, vérifie les logs du bot
(`[webhook] claude-notify ...`).
