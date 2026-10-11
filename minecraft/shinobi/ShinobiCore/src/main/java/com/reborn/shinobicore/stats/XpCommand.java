package com.reborn.shinobicore.stats;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /xp} — niveau et expérience d'un personnage, <b>staff uniquement</b> (le joueur ne voit ni l'un ni l'autre) :
 * {@code /xp voir <joueur>}, {@code /xp donner|retirer <joueur> <n> [raison]}, {@code /xp niveau <joueur> <n>}.
 */
public final class XpCommand implements TabExecutor {

    private final ShinobiCore plugin;

    public XpCommand(ShinobiCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!s.hasPermission("shinobicore.staff")) { s.sendMessage("§cRéservé au staff."); return true; }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.isEmpty()) {
            if (s instanceof Player p) show(s, p);
            return true;
        }
        Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        if (t == null) { s.sendMessage("§cJoueur hors ligne."); return true; }
        switch (sub) {
            case "voir" -> show(s, t);
            case "donner", "retirer" -> {
                if (args.length < 3) { s.sendMessage("§cUsage : /xp " + sub + " <joueur> <n> [raison]"); return true; }
                double n;
                try { n = Double.parseDouble(args[2]); } catch (NumberFormatException e) { s.sendMessage("§cNombre invalide."); return true; }
                if (sub.equals("retirer")) n = -n;
                String why = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "staff";
                int lvl = plugin.progression().grant(t, n, why);
                if (lvl < 0) { s.sendMessage("§cAucun personnage actif."); return true; }
                s.sendMessage("§a" + (n >= 0 ? "+" : "") + (int) n + " XP à " + t.getName() + " (" + why + ") · niveau " + lvl + ".");
            }
            case "niveau" -> {
                if (args.length < 3) { s.sendMessage("§cUsage : /xp niveau <joueur> <n>"); return true; }
                ShinobiCharacter c = plugin.characters().getActive(t.getUniqueId());
                if (c == null) { s.sendMessage("§cAucun personnage actif."); return true; }
                try { c.setLevel(Integer.parseInt(args[2])); } catch (NumberFormatException e) { s.sendMessage("§cNombre invalide."); return true; }
                c.setExperience(0);
                plugin.characterRepository().save(c);
                s.sendMessage("§a" + t.getName() + " passe niveau " + c.level() + ".");
            }
            default -> s.sendMessage("§cUsage : /xp [voir|donner|retirer|niveau] …");
        }
        return true;
    }

    private void show(CommandSender to, Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) { to.sendMessage("§cAucun personnage actif."); return; }
        double need = Progression.need(c.level());
        boolean max = c.level() >= Progression.config().maxLevel();
        to.sendMessage("§6" + c.name() + " §7· niveau §f" + c.level()
                + (max ? " §7(maximum)" : " §7· " + (int) c.experience() + " / " + (int) need + " XP"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("shinobicore.staff")) return out;
        if (args.length == 1) out.addAll(List.of("voir", "donner", "retirer", "niveau"));
        else if (args.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        String cur = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
