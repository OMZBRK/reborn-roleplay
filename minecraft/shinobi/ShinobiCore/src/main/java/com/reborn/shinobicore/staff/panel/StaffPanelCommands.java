package com.reborn.shinobicore.staff.panel;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Commandes du Poste de garde :
 * <ul>
 *   <li>{@code /garde} — staff : ouvrir le panel (aussi touche du mod) ;</li>
 *   <li>{@code /staffchat <message>} — staff : canal staff (aussi {@code #message} dans le chat) ;</li>
 *   <li>{@code /signaler <joueur> <motif>} — tout joueur : alerte le staff.</li>
 * </ul>
 */
public final class StaffPanelCommands implements CommandExecutor {

    private final StaffPanel panel;

    public StaffPanelCommands(StaffPanel panel) {
        this.panel = panel;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cEn jeu uniquement."); return true; }
        switch (command.getName().toLowerCase()) {
            case "garde" -> panel.openFor(p);
            case "staffchat" -> {
                if (StaffGrades.of(p) == StaffGrades.NONE) { p.sendMessage("§cRéservé au staff."); return true; }
                if (args.length == 0) { p.sendMessage("§7Usage : /" + label + " <message> (ou #message dans le chat)"); return true; }
                panel.staffChat(p, String.join(" ", args));
            }
            case "signaler" -> {
                if (args.length < 2) { p.sendMessage("§7Usage : /" + label + " <joueur> <motif>"); return true; }
                Player t = Bukkit.getPlayerExact(args[0]);
                if (t == null || t.equals(p)) { p.sendMessage("§cJoueur introuvable."); return true; }
                String motif = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                panel.onReport(p, t, motif.length() > 80 ? motif.substring(0, 80) : motif);
                p.sendMessage("§aTon signalement a été transmis au staff. Merci.");
            }
            default -> { }
        }
        return true;
    }
}
