package com.reborn.shinobicore.douane;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /douane} et {@code /modeexaminateur} (CANDIDATURE_ET_DOUANE.md §3.6 à §3.8). Les mêmes actions passent par
 * l'onglet Douane du Poste de garde (canal {@code reborn:douane}), avec les mêmes contrôles.
 *
 * <pre>
 * /douane                                   statut (candidat) ou résumé (staff)
 * /douane aller | quitter                   au hall sans passer par la file / retour
 * /douane file                              la file, dans l'ordre de passage
 * /douane appeler [suivant|joueur]          au bureau (mode examinateur)
 * /douane renvoyer                          le candidat du bureau retourne au hall, en tête
 * /douane retirer joueur motif…             expulsion de la douane, sans décision
 * /douane dossier [joueur]                  fiche résumée (API, inscrite à l'audit)
 * /douane question [montrer|masquer]        tirage au hasard / montrer au candidat
 * /douane notes texte… | -                  notes privées jointes à la décision
 * /douane decision accepter|revoir|refuser [motif…]
 * </pre>
 */
final class DouaneCommand implements TabExecutor {

    private static final List<String> SOUS = List.of("aller", "quitter", "file", "appeler", "renvoyer", "retirer",
            "dossier", "question", "notes", "decision");

    private final DouaneManager douane;

    DouaneCommand(DouaneManager douane) {
        this.douane = douane;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (cmd.getName().equalsIgnoreCase("modeexaminateur")) {
            if (s instanceof Player p) douane.mode().basculer(p);
            else s.sendMessage(Component.text("En jeu seulement.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { douane.statut(s); return true; }
        String sous = args[0].toLowerCase(Locale.ROOT);
        if (sous.equals("file")) { douane.listerFile(s); return true; }
        if (!(s instanceof Player p)) { s.sendMessage(Component.text("En jeu seulement.", NamedTextColor.RED)); return true; }
        // Un candidat n'a que son statut.
        if (douane.restreint(p.getUniqueId())) { douane.statut(p); return true; }
        switch (sous) {
            case "aller" -> douane.aller(p);
            case "quitter" -> douane.quitterDouane(p);
            case "appeler" -> douane.appeler(p, args.length > 1 ? args[1] : null);
            case "renvoyer" -> douane.renvoyer(p);
            case "retirer" -> {
                if (args.length < 3) { p.sendMessage(Component.text("/douane retirer <joueur> <motif…>", NamedTextColor.RED)); return true; }
                douane.retirer(p, args[1], reste(args, 2));
            }
            case "dossier" -> douane.dossier(p, args.length > 1 ? args[1] : null);
            case "question" -> {
                String q = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (q.equals("montrer")) douane.montrer(p, true);
                else if (q.equals("masquer")) douane.montrer(p, false);
                else douane.question(p);
            }
            case "notes" -> douane.notes(p, args.length > 1 ? reste(args, 1) : "-");
            case "decision" -> {
                String r = args.length > 1 ? ReglesDouane.resultat(args[1]) : null;
                if (r == null) {
                    p.sendMessage(Component.text("/douane decision <accepter|revoir|refuser> [motif…]", NamedTextColor.RED));
                    return true;
                }
                douane.decider(p, r, args.length > 2 ? reste(args, 2) : null, null);
            }
            default -> p.sendMessage(Component.text("/douane [" + String.join("|", SOUS) + "]", NamedTextColor.RED));
        }
        return true;
    }

    private static String reste(String[] args, int depuis) {
        return String.join(" ", Arrays.copyOfRange(args, depuis, args.length)).strip();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label,
                                      @NotNull String[] args) {
        if (cmd.getName().equalsIgnoreCase("modeexaminateur")) return List.of();
        if (s instanceof Player p && (douane.restreint(p.getUniqueId())
                || (!DouaneManager.peutExaminer(p) && com.reborn.shinobicore.staff.panel.StaffGrades.of(p) == 0))) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(SOUS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "appeler" -> { out.add("suivant"); out.addAll(douane.pseudosFile()); }
                case "retirer", "dossier" -> out.addAll(douane.pseudosCandidats());
                case "question" -> out.addAll(List.of("montrer", "masquer"));
                case "decision" -> out.addAll(List.of("accepter", "revoir", "refuser"));
                default -> { }
            }
        }
        String debut = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(debut));
        return out;
    }
}
