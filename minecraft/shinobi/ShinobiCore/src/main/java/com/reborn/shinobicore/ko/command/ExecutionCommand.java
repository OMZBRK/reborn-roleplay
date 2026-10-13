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
 * {@code liste [joueur]}, {@code recharger} (relit {@code executions.yml}).
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
            case "donner", "retirer" -> {
                if (args.length < 3) { s.sendMessage("§cUsage : /execution " + sub + " <joueur> <id>"); return true; }
                java.util.UUID byPid = com.reborn.shinobicore.character.PlayerIds.ownerByRef(s, args[1]);
                OfflinePlayer t = byPid != null ? Bukkit.getOfflinePlayer(byPid) : Bukkit.getOfflinePlayer(args[1]);
                if (!ex.give(t.getUniqueId(), args[2], sub.equals("donner"))) { s.sendMessage("§cExécution inconnue."); return true; }
                s.sendMessage("§a" + (sub.equals("donner") ? "Donnée à " : "Retirée à ") + args[1] + " : " + args[2] + ".");
            }
            default -> s.sendMessage("§cUsage : /execution [donner|retirer|liste|recharger]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("shinobicore.staff")) return out;
        if (args.length == 1) out.addAll(List.of("donner", "retirer", "liste", "recharger"));
        else if (args.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (args.length == 3) plugin.corps().executions().all().forEach(d -> out.add(d.id()));
        String cur = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
