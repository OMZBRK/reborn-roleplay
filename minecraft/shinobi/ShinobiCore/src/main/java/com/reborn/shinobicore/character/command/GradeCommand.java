package com.reborn.shinobicore.character.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.stats.GradeRules;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /grade} — passage de grade RP (docs/GRADES.md).
 * <ul>
 *   <li>{@code /grade} : son grade, ses bonus, ce qu'on peut donner.</li>
 *   <li>{@code /grade voir <joueur>} : le grade d'un autre personnage.</li>
 *   <li>{@code /grade <joueur> <grade> [merite|examen]} : promouvoir ou rétrograder. Un joueur gradé ne peut donner
 *       un grade que dans les limites de {@link GradeRules} (au mérite, ou sur examen réussi), dans son propre
 *       village ; le staff ({@code shinobicore.grade.staff}) n'a pas de limite.</li>
 * </ul>
 */
public final class GradeCommand implements TabExecutor {

    private static final String STAFF = "shinobicore.grade.staff";

    private final ShinobiCore plugin;

    public GradeCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("§cUsage : /grade <joueur> <grade> [merite|examen]"); return true; }
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            if (c == null) { p.sendMessage("§cAucun personnage actif."); return true; }
            describe(p, c, true);
            return true;
        }
        if (args[0].equalsIgnoreCase("voir")) {
            if (args.length < 2) { sender.sendMessage("§cUsage : /grade voir <joueur>"); return true; }
            Player t = Bukkit.getPlayerExact(args[1]);
            ShinobiCharacter c = t == null ? null : plugin.characters().getActive(t.getUniqueId());
            if (c == null) { sender.sendMessage("§cJoueur hors ligne ou sans personnage actif."); return true; }
            describe(sender, c, false);
            return true;
        }
        if (args.length < 2) { sender.sendMessage("§cUsage : /grade <joueur> <grade> [merite|examen]"); return true; }

        Player target = Bukkit.getPlayerExact(args[0]);
        ShinobiCharacter tc = target == null ? null : plugin.characters().getActive(target.getUniqueId());
        if (tc == null) { sender.sendMessage("§cJoueur hors ligne ou sans personnage actif."); return true; }
        Rank to = parse(args[1]);
        if (to == null) { sender.sendMessage("§cGrade inconnu. Grades : " + names()); return true; }
        boolean exam = args.length >= 3 && args[2].toLowerCase(Locale.ROOT).startsWith("exam");
        Rank from = tc.rank();
        if (to == from) { sender.sendMessage("§e" + tc.name() + " est déjà " + to.displayName() + "."); return true; }

        boolean staff = sender.hasPermission(STAFF) || !(sender instanceof Player);
        String giver = sender.getName();
        if (!staff) {
            Player p = (Player) sender;
            ShinobiCharacter gc = plugin.characters().getActive(p.getUniqueId());
            if (gc == null) { p.sendMessage("§cAucun personnage actif."); return true; }
            if (p.getUniqueId().equals(target.getUniqueId())) { p.sendMessage("§cOn ne se donne pas un grade soi-même."); return true; }
            String refused = refusal(gc, tc, to, exam);
            if (refused != null) { p.sendMessage("§c" + refused); return true; }
            giver = gc.name();
        }

        tc.setRank(to);
        plugin.stats().onRankChanged(tc);

        boolean up = to.ordinal() > from.ordinal();
        String how = !up ? "rétrogradé" : exam ? "promu (examen réussi)" : "promu au mérite";
        String line = tc.name() + " : " + from.displayName() + " → " + to.displayName() + " · " + how + " par " + giver;
        target.sendMessage("§6⛩ §e" + (up ? "Tu es désormais " : "Tu redeviens ") + "§f" + to.displayName()
                + "§e" + (up ? (exam ? " — examen réussi." : " — au mérite.") : ".") + " §7(" + giver + ")");
        if (sender != target) sender.sendMessage("§a" + line);
        if (plugin.staffPanel() != null && sender instanceof Player sp) plugin.staffPanel().log(sp, "grade", up ? "Promotion" : "Rétrogradation", line);
        plugin.getLogger().info("[grade] " + line);
        return true;
    }

    /** Raison du refus pour un joueur gradé, ou {@code null} si la passation est permise. */
    private static String refusal(ShinobiCharacter giver, ShinobiCharacter target, Rank to, boolean exam) {
        Rank g = giver.rank();
        String gv = giver.village(), tv = target.village();
        if (!gv.isBlank() && !tv.isBlank() && !gv.equalsIgnoreCase(tv)) return "Ce shinobi n'est pas de ton village.";
        if (to.ordinal() < target.rank().ordinal()) {
            Rank min = GradeRules.demoteMin();
            if (min == null || g.ordinal() < min.ordinal()) return "Seul le staff" + (min == null ? "" : " ou un " + min.displayName()) + " peut rétrograder.";
            if (g.ordinal() <= target.rank().ordinal()) return "Tu ne peux pas rétrograder un grade égal ou supérieur au tien.";
            return null;
        }
        if (g.ordinal() <= to.ordinal()) return "Tu ne peux donner qu'un grade inférieur au tien.";
        Rank min = exam ? GradeRules.examMin(to) : GradeRules.meritMin(to);
        if (min == null) {
            if (exam) return "Le grade " + to.displayName() + " ne s'obtient pas par examen.";
            Rank e = GradeRules.examMin(to);
            return "Le grade " + to.displayName() + (e != null ? " s'obtient par examen (ajoute « examen »)." : " ne se donne que par le staff.");
        }
        if (g.ordinal() < min.ordinal()) {
            return "Il faut être " + min.displayName() + " ou plus pour donner " + to.displayName()
                    + (exam ? " sur examen." : " au mérite.");
        }
        return null;
    }

    private void describe(CommandSender to, ShinobiCharacter c, boolean self) {
        GradeRules.Bonus b = GradeRules.bonus(c.rank());
        to.sendMessage("§6⛩ §f" + c.name() + " §7— §e" + c.rank().displayName());
        to.sendMessage("§7Bonus de grade : §f+" + (int) b.hp() + " PV§7, §f+" + (int) b.chakra() + " chakra§7, §f+"
                + (int) b.stamina() + " endurance§7, §f+" + b.stats() + "§7 à chaque stat.");
        if (!self) return;
        List<String> can = new ArrayList<>();
        for (Rank r : Rank.values()) {
            if (r.ordinal() >= c.rank().ordinal()) break;
            Rank m = GradeRules.meritMin(r), e = GradeRules.examMin(r);
            if (m != null && c.rank().ordinal() >= m.ordinal()) can.add(r.displayName());
            else if (e != null && c.rank().ordinal() >= e.ordinal()) can.add(r.displayName() + " (examen)");
        }
        to.sendMessage(can.isEmpty() ? "§7Tu ne peux pas encore donner de grade."
                : "§7Tu peux donner : §f" + String.join("§7, §f", can));
    }

    private static Rank parse(String s) {
        String n = s.toUpperCase(Locale.ROOT).replace('-', '_').replace('É', 'E').replace('Ō', 'O').replace('Ū', 'U');
        for (Rank r : Rank.values()) if (r.name().equals(n)) return r;
        return null;
    }

    private static String names() {
        List<String> out = new ArrayList<>();
        for (Rank r : Rank.values()) out.add(r.name().toLowerCase(Locale.ROOT));
        return String.join(", ", out);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String cur = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            out.add("voir");
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("voir")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 2) {
            for (Rank r : Rank.values()) out.add(r.name().toLowerCase(Locale.ROOT));
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("voir")) {
            out.add("merite");
            out.add("examen");
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
