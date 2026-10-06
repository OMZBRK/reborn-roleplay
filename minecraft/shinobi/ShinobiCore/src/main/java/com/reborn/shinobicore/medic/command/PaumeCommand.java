package com.reborn.shinobicore.medic.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.medic.PalmHealing;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /paume [joueur]} — médic : tendre la paume de soin vers le joueur visé
 * (ou nommé). {@code /paume stop} l'interrompt. {@code /paume accepter} — patient :
 * accepter la demande reçue (lien cliquable dans le chat).
 */
public final class PaumeCommand implements TabExecutor {

    private final ShinobiCore plugin;

    public PaumeCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cEn jeu uniquement."); return true; }
        PalmHealing palm = plugin.palm();
        String a = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (a.equals("accepter") || a.equals("accept")) { palm.accept(p); return true; }
        if (a.equals("stop")) {
            if (palm.isHealing(p.getUniqueId())) palm.stop(p.getUniqueId(), "Tu interromps le soin.");
            else p.sendMessage("§7Tu ne soignes personne.");
            return true;
        }
        Player target = null;
        if (!a.isEmpty()) {
            target = Bukkit.getPlayerExact(args[0]);
        } else {
            Entity looked = p.getTargetEntity(5);
            if (looked instanceof Player lp) target = lp;
        }
        if (target == null) {
            p.sendMessage("§7Regarde le blessé (ou nomme-le) : /" + label + " [joueur].");
            return true;
        }
        palm.request(p, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) return out;
        String q = args[0].toLowerCase(Locale.ROOT);
        for (String s : List.of("stop", "accepter")) if (s.startsWith(q)) out.add(s);
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(q)) out.add(p.getName());
        return out;
    }
}
