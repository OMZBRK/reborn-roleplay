package com.reborn.shinobiabilities.command;

import com.reborn.shinobiabilities.mobility.training.Parkour;
import com.reborn.shinobiabilities.mobility.training.ParkourEditorSession;
import com.reborn.shinobiabilities.mobility.training.ParkourManager;
import com.reborn.shinobiabilities.mobility.training.ParkourRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /trainingground} — the training-ground parkour suite.
 *
 * <pre>
 * /trainingground run &lt;nom&gt;                  (joueur — tente le parcours)
 * /trainingground list
 * /trainingground create &lt;nom&gt;               (admin — ouvre l'éditeur)
 * /trainingground delete &lt;nom&gt; | tp &lt;nom&gt;    (admin)
 * </pre>
 */
public final class ParkourCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "shinobiabilities.admin";

    private final ParkourManager manager;
    private final ParkourRunner runner;

    public ParkourCommand(ParkourManager manager, ParkourRunner runner) {
        this.manager = manager;
        this.runner = runner;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Commande joueur uniquement.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { help(p); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!admin(p)) return true;
                if (args.length < 2) { usage(p, "/trainingground create <nom>"); return true; }
                if (manager.isEditing(p.getUniqueId())) {
                    p.sendMessage(Component.text("Termine d'abord l'édition en cours.",
                            NamedTextColor.RED));
                    return true;
                }
                if (manager.byName(args[1]) != null) {
                    p.sendMessage(Component.text("Un parcours porte déjà ce nom "
                            + "(supprime-le d'abord).", NamedTextColor.RED));
                    return true;
                }
                Parkour working = new Parkour(args[1], p.getWorld().getName());
                ParkourEditorSession s = new ParkourEditorSession(manager, p.getUniqueId(), working);
                manager.putEditor(p.getUniqueId(), s);
                s.enter(p);
            }
            case "edit" -> {
                if (!admin(p)) return true;
                if (args.length < 2) { usage(p, "/trainingground edit <nom>"); return true; }
                if (manager.isEditing(p.getUniqueId())) {
                    p.sendMessage(Component.text("Termine d'abord l'édition en cours.",
                            NamedTextColor.RED));
                    return true;
                }
                Parkour existing = manager.byName(args[1]);
                if (existing == null) {
                    p.sendMessage(Component.text("Parcours inconnu : " + args[1], NamedTextColor.RED));
                    return true;
                }
                Parkour working = existing.copy();   // edit a copy; commit overwrites on finish
                ParkourEditorSession s = new ParkourEditorSession(manager, p.getUniqueId(), working);
                manager.putEditor(p.getUniqueId(), s);
                s.enter(p);
                p.sendMessage(Component.text("Édition de « " + existing.name() + " » — "
                        + existing.anchors().size() + " ancres (utilise /tg tp pour t'y rendre).",
                        NamedTextColor.AQUA));
            }
            case "delete" -> {
                if (!admin(p)) return true;
                if (args.length < 2) { usage(p, "/trainingground delete <nom>"); return true; }
                p.sendMessage(manager.delete(args[1])
                        ? Component.text("Parcours supprimé.", NamedTextColor.GREEN)
                        : Component.text("Parcours inconnu.", NamedTextColor.RED));
            }
            case "list" -> {
                List<String> names = manager.names();
                p.sendMessage(Component.text("Parcours (" + names.size() + ") : "
                        + (names.isEmpty() ? "(aucun)" : String.join(", ", names)),
                        NamedTextColor.GOLD));
            }
            case "tp" -> {
                if (!admin(p)) return true;
                if (args.length < 2) { usage(p, "/trainingground tp <nom>"); return true; }
                Parkour pk = manager.byName(args[1]);
                World w = pk == null ? null : p.getServer().getWorld(pk.world());
                if (pk == null || w == null || pk.anchors().isEmpty()) {
                    p.sendMessage(Component.text("Parcours inconnu (ou monde déchargé).",
                            NamedTextColor.RED));
                    return true;
                }
                p.teleport(pk.anchors().get(0).standLocation(w));
                p.sendMessage(Component.text("Téléporté au départ de « " + pk.name() + " ».",
                        NamedTextColor.GREEN));
            }
            case "run", "start" -> {
                if (args.length < 2) { usage(p, "/trainingground run <nom>"); return true; }
                Parkour pk = manager.byName(args[1]);
                if (pk == null) {
                    p.sendMessage(Component.text("Parcours inconnu.", NamedTextColor.RED));
                    return true;
                }
                if (!runner.start(p, pk)) {
                    p.sendMessage(Component.text("Impossible de lancer ce parcours "
                            + "(déjà en cours, monde déchargé, ou moins de 2 ancres).",
                            NamedTextColor.RED));
                }
            }
            default -> help(p);
        }
        return true;
    }

    private boolean admin(Player p) {
        if (p.hasPermission(ADMIN)) return true;
        p.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
        return false;
    }

    private void help(Player p) {
        p.sendMessage(Component.text("— /trainingground —", NamedTextColor.GOLD));
        p.sendMessage(Component.text("/trainingground run <nom> — tenter le parcours",
                NamedTextColor.YELLOW));
        if (p.hasPermission(ADMIN)) {
            p.sendMessage(Component.text("/trainingground create <nom> — éditeur (barre d'outils)",
                    NamedTextColor.YELLOW));
            p.sendMessage(Component.text("/trainingground edit <nom> — rouvrir l'éditeur",
                    NamedTextColor.YELLOW));
            p.sendMessage(Component.text("/trainingground delete <nom> · list · tp <nom>",
                    NamedTextColor.YELLOW));
        } else {
            p.sendMessage(Component.text("/trainingground list", NamedTextColor.YELLOW));
        }
    }

    private void usage(Player p, String u) {
        p.sendMessage(Component.text("Usage : " + u, NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("run", "list"));
            if (sender.hasPermission(ADMIN)) subs.addAll(List.of("create", "edit", "delete", "tp"));
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String s = args[0].toLowerCase(Locale.ROOT);
            if (s.equals("run") || s.equals("start") || s.equals("edit")
                    || s.equals("delete") || s.equals("tp")) {
                return filter(manager.names(), args[1]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(low)) out.add(o);
        return out;
    }
}
