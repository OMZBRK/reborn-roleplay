package com.reborn.shinobicore.duel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /duel proposer|accepter|refuser|abandonner [joueur]} — cf. {@link DuelService}. Le menu d'interaction du mod
 * et les boutons de l'invitation passent par ces mêmes commandes ; le serveur revérifie tout.
 */
public final class DuelCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("proposer", "accepter", "refuser", "abandonner");

    private final DuelService duels;

    public DuelCommand(DuelService duels) { this.duels = duels; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Commande joueur."); return true; }
        if (args.length == 0) { usage(p); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
        if (args.length >= 2 && target == null && !sub.startsWith("aband")) {
            p.sendMessage(Component.text("⚔ Joueur introuvable : " + args[1], NamedTextColor.RED));
            return true;
        }
        switch (sub) {
            case "proposer", "defier", "défier", "propose" -> {
                if (target == null) { usage(p); return true; }
                duels.propose(p, target);
            }
            case "accepter", "accept", "oui" -> duels.accept(p, target);
            case "refuser", "refuse", "non" -> duels.refuse(p, target);
            case "abandonner", "abandon", "forfait" -> duels.forfeit(p);
            default -> usage(p);
        }
        return true;
    }

    private static void usage(Player p) {
        p.sendMessage(Component.text("⚔ /duel proposer <joueur> · accepter [joueur] · refuser [joueur] · abandonner",
                NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && !args[0].toLowerCase(Locale.ROOT).startsWith("aband")) {
            String pre = args[1].toLowerCase(Locale.ROOT);
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o.equals(sender)) continue;
                if (o.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(o.getName());
            }
        }
        return out;
    }
}
