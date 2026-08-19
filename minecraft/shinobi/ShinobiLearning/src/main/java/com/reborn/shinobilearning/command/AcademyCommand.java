package com.reborn.shinobilearning.command;

import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobilearning.ShinobiLearning;
import com.reborn.shinobilearning.academy.AcademyData;
import com.reborn.shinobilearning.academy.AcademyStore;
import com.reborn.shinobilearning.academy.Lesson;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * {@code /academy} — the student's status view plus the instructor toolbox
 * (enrol, validate lessons, graduate). Instructor actions need
 * {@code shinobilearning.instructor}.
 */
public final class AcademyCommand implements CommandExecutor, TabCompleter {

    private final ShinobiLearning plugin;

    public AcademyCommand(ShinobiLearning plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) return status(sender);
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("help".equals(sub)) return help(sender);

        if (!sender.hasPermission("shinobilearning.instructor")) return noPerm(sender);
        return switch (sub) {
            case "enroll", "inscrire" -> enroll(sender, args);
            case "lesson", "lecon", "leçon" -> lesson(sender, args);
            case "graduate", "diplome", "diplôme" -> graduate(sender, args);
            case "info" -> info(sender, args);
            default -> {
                sender.sendMessage(err("Sous-commande inconnue. Essaie /academy help"));
                yield true;
            }
        };
    }

    /* ------------------------------------------------------------- player */

    private boolean status(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(err("Réservé aux joueurs."));
            return true;
        }
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) { p.sendMessage(err("Aucun personnage actif.")); return true; }
        AcademyData d = plugin.academy().of(c.id());
        if (!d.enrolled() && !d.graduated()) {
            p.sendMessage(Component.text("Tu n'es pas inscrit à l'Académie.", NamedTextColor.GRAY));
            return true;
        }
        p.sendMessage(Component.text("— Académie · " + c.name() + " —", NamedTextColor.GOLD));
        if (d.graduated()) {
            p.sendMessage(Component.text("Diplômé — Genin.", NamedTextColor.GREEN));
        }
        for (Lesson l : Lesson.values()) {
            boolean done = d.hasCompleted(l);
            p.sendMessage(Component.text((done ? "✔ " : "✘ ") + l.displayName(),
                    done ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean help(CommandSender sender) {
        sender.sendMessage(Component.text("Commandes Académie :", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /academy — ton cursus", NamedTextColor.YELLOW));
        if (sender.hasPermission("shinobilearning.instructor")) {
            sender.sendMessage(Component.text("Instructeur :", NamedTextColor.LIGHT_PURPLE));
            sender.sendMessage(Component.text("  /academy enroll <personnage>", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /academy lesson <personnage> <leçon>", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /academy graduate <personnage> [force]", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /academy info <personnage>", NamedTextColor.GRAY));
        }
        return true;
    }

    /* --------------------------------------------------------- instructor */

    private boolean enroll(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(err("Usage : /academy enroll <personnage>")); return true; }
        AcademyStore.ResolvedCharacter r = plugin.academy().resolveCharacter(a[1]);
        if (r == null) return unknown(s, a[1]);
        plugin.academyManager().enroll(r.character());
        s.sendMessage(ok(r.character().name() + " est inscrit à l'Académie (rang Académie)."));
        return true;
    }

    private boolean lesson(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(err("Usage : /academy lesson <personnage> <leçon>")); return true; }
        AcademyStore.ResolvedCharacter r = plugin.academy().resolveCharacter(a[1]);
        if (r == null) return unknown(s, a[1]);
        Lesson l = Lesson.from(a[2]);
        if (l == null) { s.sendMessage(err("Leçon inconnue : " + a[2] + " (voir tab)")); return true; }
        boolean added = plugin.academyManager().completeLesson(r.character(), l);
        s.sendMessage(ok(r.character().name() + (added ? " a validé : " : " avait déjà : ") + l.displayName()));
        if (plugin.academyManager().canGraduate(r.character())) {
            s.sendMessage(Component.text(r.character().name()
                    + " a terminé le cursus — prêt pour /academy graduate.", NamedTextColor.AQUA));
        }
        return true;
    }

    private boolean graduate(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(err("Usage : /academy graduate <personnage> [force]")); return true; }
        AcademyStore.ResolvedCharacter r = plugin.academy().resolveCharacter(a[1]);
        if (r == null) return unknown(s, a[1]);
        boolean force = a.length >= 3 && "force".equalsIgnoreCase(a[2]);
        if (!force && !plugin.academyManager().canGraduate(r.character())) {
            s.sendMessage(err(r.character().name()
                    + " n'a pas terminé le cursus (ajoute 'force' pour passer outre)."));
            return true;
        }
        plugin.academyManager().graduate(r.character());
        s.sendMessage(ok(r.character().name() + " est diplômé — Genin."));
        return true;
    }

    private boolean info(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage(err("Usage : /academy info <personnage>")); return true; }
        AcademyStore.ResolvedCharacter r = plugin.academy().resolveCharacter(a[1]);
        if (r == null) return unknown(s, a[1]);
        ShinobiCharacter c = r.character();
        AcademyData d = plugin.academy().of(c.id());
        s.sendMessage(Component.text("— Académie · " + c.name()
                + " (" + c.rank().displayName() + ") —", NamedTextColor.GOLD));
        s.sendMessage(Component.text("Inscrit : " + d.enrolled()
                + " · Diplômé : " + d.graduated(), NamedTextColor.GRAY));
        for (Lesson l : Lesson.values()) {
            boolean done = d.hasCompleted(l);
            s.sendMessage(Component.text((done ? "✔ " : "✘ ") + l.displayName(),
                    done ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        }
        return true;
    }

    /* ------------------------------------------------------------ helpers */

    private boolean unknown(CommandSender s, String name) {
        s.sendMessage(err("Aucun personnage trouvé pour : " + name
                + " (le joueur doit être passé en ligne récemment)"));
        return true;
    }

    private boolean noPerm(CommandSender s) {
        s.sendMessage(err("Permission insuffisante."));
        return true;
    }

    private static Component ok(String t)  { return Component.text(t, NamedTextColor.GREEN); }
    private static Component err(String t) { return Component.text(t, NamedTextColor.RED); }

    /* ----------------------------------------------------- tab completion */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        boolean staff = sender.hasPermission("shinobilearning.instructor");
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("status", "help"));
            if (staff) subs.addAll(List.of("enroll", "lesson", "graduate", "info"));
            return prefix(subs, args[0]);
        }
        if (!staff) return List.of();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "enroll", "inscrire", "lesson", "lecon", "leçon",
                     "graduate", "diplome", "diplôme", "info" -> prefix(characterNames(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && (sub.equals("lesson") || sub.equals("lecon") || sub.equals("leçon"))) {
            List<String> ls = new ArrayList<>();
            for (Lesson l : Lesson.values()) ls.add(l.name());
            return prefix(ls, args[2]);
        }
        if (args.length == 3 && (sub.equals("graduate") || sub.equals("diplome") || sub.equals("diplôme"))) {
            return prefix(List.of("force"), args[2]);
        }
        return List.of();
    }

    private List<String> characterNames() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (plugin.characters() != null) {
            for (var roster : plugin.characters().rosterView().values()) {
                for (ShinobiCharacter c : roster) names.add(c.name());
            }
        }
        return new ArrayList<>(names);
    }

    private static List<String> prefix(List<String> options, String arg) {
        String low = arg == null ? "" : arg.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(low)) out.add(o);
        }
        return out;
    }
}
