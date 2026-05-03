import { REST, Routes } from "discord.js";
import { config } from "../config.js";
import { commands } from "../commands/index.js";

/**
 * Enregistre les slash commands du bot sur le guild Reborn (deploy local
 * instantane, pas de delai global de 1h). A relancer apres chaque ajout
 * de commande.
 */

const rest = new REST({ version: "10" }).setToken(config.botToken);

const body = commands.map((c) => c.data.toJSON());

console.log(
  `Deploiement de ${body.length} commande(s) sur guild ${config.guildId}...`,
);

const result = await rest.put(
  Routes.applicationGuildCommands(config.clientId, config.guildId),
  { body },
);

console.log(`OK — ${(result as unknown[]).length} commande(s) actives.`);
