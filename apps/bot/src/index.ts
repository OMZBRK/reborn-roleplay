import { Client, Events, GatewayIntentBits } from "discord.js";
import { config } from "./config.js";
import { commands } from "./commands/index.js";
import { startWebhookServer } from "./webhook-server.js";

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMembers,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
  ],
});

client.once(Events.ClientReady, (ready) => {
  console.log(`Bot connecte en tant que ${ready.user.tag}`);
  console.log(
    `Commandes disponibles : ${commands.map((c) => c.data.name).join(", ") || "(aucune)"}`,
  );
  startWebhookServer(ready);
});

client.on(Events.InteractionCreate, async (interaction) => {
  if (!interaction.isChatInputCommand()) return;
  const cmd = commands.find((c) => c.data.name === interaction.commandName);
  if (!cmd) {
    await interaction.reply({
      content: `Commande inconnue : ${interaction.commandName}`,
      ephemeral: true,
    });
    return;
  }
  try {
    await cmd.execute(interaction);
  } catch (err) {
    console.error(`Echec commande ${interaction.commandName}`, err);
    const message = "Erreur interne pendant l'execution de la commande.";
    if (interaction.deferred || interaction.replied) {
      await interaction.followUp({ content: message, ephemeral: true });
    } else {
      await interaction.reply({ content: message, ephemeral: true });
    }
  }
});

await client.login(config.botToken);
