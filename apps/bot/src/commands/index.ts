import type {
  ChatInputCommandInteraction,
  SlashCommandBuilder,
  SlashCommandOptionsOnlyBuilder,
  SlashCommandSubcommandsOnlyBuilder,
} from "discord.js";
import * as ping from "./ping.js";
import * as whitelist from "./whitelist.js";
import * as ticket from "./ticket.js";
import * as role from "./role.js";

export interface SlashCommand {
  data:
    | SlashCommandBuilder
    | SlashCommandOptionsOnlyBuilder
    | SlashCommandSubcommandsOnlyBuilder;
  execute(interaction: ChatInputCommandInteraction): Promise<void>;
}

export const commands: SlashCommand[] = [ping, whitelist, ticket, role];
