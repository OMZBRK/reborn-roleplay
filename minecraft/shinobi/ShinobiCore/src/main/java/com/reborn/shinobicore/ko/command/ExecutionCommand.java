package com.reborn.shinobicore.ko.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.ko.action.Executions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /execution} — ses exécutions (animations de mise à mort). Staff : {@code donner|retirer <joueur> <id>},
 * {@code liste [joueur]}, {@code recharger} (relit {@code executions.yml}), et pour arbitrer une scène :
 * {@code forcer <joueur>} (le met K.O. inconscient, comme une exécution en douleur), {@code douleur <joueur>} (PV au
 * seuil de douleur), {@code annuler <joueur>} (arrête une exécution en cours).
 */
public final class ExecutionCommand implements TabExecutor {

    private final ShinobiCore plugin;

    public ExecutionCommand(ShinobiCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        Executions ex = plugin.corps().executions();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.isEmpty() && s instanceof Player p) {
            List<String> names = new ArrayList<>();
            for (Executions.Def d : ex.ownedBy(p)) names.add(d.name());
            s.sendMessage("§6Tes exécutions : §f" + (names.isEmpty() ? "aucune" : String.join(", ", names)));
            return true;
        }
        if (!s.hasPermission("shinobicore.staff")) { s.sendMessage("§cRéservé au staff."); return true; }
        switch (sub) {
            case "recharger" -> { ex.reload(); s.sendMessage("§aexecutions.yml rechargé (" + ex.all().size() + ")."); }
            case "liste" -> {
                Player t = args.length > 1 ? com.reborn.shinobicore.character.PlayerIds.online(s, args[1]) : null;
                for (Executions.Def d : ex.all()) {
                    String own = t == null ? "" : (ex.owns(t, d) ? " §a✔" : " §8✘");
                    s.sendMessage("§7- §f" + d.id() + " §7« " + d.name() + " » §8(" + d.durationTicks() / 20.0 + " s)" + own);
                }
            }
            case "forcer", "douleur", "annuler" -> {
                if (args.length < 2) { s.sendMessage("§cUsage : /execution " + sub + " <joueur|#N°>"); return true; }
                Player t = com.reborn.shinobicore.character.PlayerIds.online(s, args[1]);
                if (t == null) { s.sendMessage("§cJoueur introuvable (en ligne)."); return true; }
                String who = com.reborn.shinobicore.ko.KoJournal.who(plugin, t);
                switch (sub) {
                    case "forcer" -> {
                        if (!plugin.ko().executeToKo(null, t)) { s.sendMessage("§cDéjà K.O. (ou sans personnage)."); return true; }
                        s.sendMessage("§a" + who + " est mis K.O. (inconscient).");
                    }
                    case "douleur" -> {
                        if (plugin.ko().isKo(t.getUniqueId()) || plugin.douleur() == null) { s.sendMessage("§cImpossible (K.O.)."); return true; }
                        double hp = Math.max(0.5, plugin.douleur().thresholdHp(t));
                        t.setHealth(Math.min(t.getMaxHealth(), hp));
                        var c = plugin.characters().getActive(t.getUniqueId());
                        if (c != null) c.setCurrentHp(t.getHealth());
                        plugin.douleur().check(t);
                        s.sendMessage("§a" + who + " : PV au seuil de douleur.");
                    }
                    default -> {
                        if (!plugin.corps().abortInvolving(t.getUniqueId())) { s.sendMessage("§7Aucune exécution en cours."); return true; }
                        s.sendMessage("§aExécution interrompue.");
                    }
                }
                if (s instanceof Player sp && plugin.staffPanel() != null) {
                    plugin.staffPanel().log(sp, "ko", "Exécution " + sub, who);
                }
                com.reborn.shinobicore.ko.KoJournal.log(plugin, t, "Staff : exécution " + sub, "par " + s.getName());
            }
            case "donner", "retirer" -> {
                if (args.length < 3) { s.sendMessage("§cUsage : /execution " + sub + " <joueur> <id>"); return true; }
                java.util.UUID byPid = com.reborn.shinobicore.character.PlayerIds.ownerByRef(s, args[1]);
                OfflinePlayer t = byPid != null ? Bukkit.getOfflinePlayer(byPid) : Bukkit.getOfflinePlayer(args[1]);
                if (!ex.give(t.getUniqueId(), args[2], sub.equals("donner"))) { s.sendMessage("§cExécution inconnue."); return true; }
                s.sendMessage("§a" + (sub.equals("donner") ? "Donnée à " : "Retirée à ") + args[1] + " : " + args[2] + ".");
            }
            default -> s.sendMessage("§cUsage : /execution [donner|retirer|liste|recharger|forcer|douleur|annuler]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("shinobicore.staff")) return out;
        if (args.length == 1) out.addAll(List.of("donner", "retirer", "liste", "recharger", "forcer", "douleur", "annuler"));
        else if (args.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (args.length == 3) plugin.corps().executions().all().forEach(d -> out.add(d.id()));
        String cur = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
