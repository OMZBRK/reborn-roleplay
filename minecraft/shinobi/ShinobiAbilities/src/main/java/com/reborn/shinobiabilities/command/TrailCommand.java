package com.reborn.shinobiabilities.command;

import com.reborn.shinobiabilities.mobility.TrailEditorSession;
import com.reborn.shinobiabilities.mobility.TrailManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
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
 * {@code /trail} — staff editor for the invisible anchor trails.
 * create &lt;nom&gt; → add (à chaque point) → finish. delete/list/tp pour
 * la gestion.
 */
public final class TrailCommand implements CommandExecutor, TabCompleter {

    private final TrailManager trails;

    public TrailCommand(TrailManager trails) {
        this.trails = trails;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Commande joueur uniquement.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            help(p);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 2) { usage(p, "/trail create <nom>"); return true; }
                if (trails.isEditing(p)) {
                    p.sendMessage(Component.text("Termine d'abord la piste en cours.",
                            NamedTextColor.RED));
                    return true;
                }
                if (!trails.editCreate(p, args[1])) {
                    p.sendMessage(Component.text("Une piste porte déjà ce nom.",
                            NamedTextColor.RED));
                    return true;
                }
                // Open the temporary-hotbar editor (replaces the old
                // /trail add|finish|cancel command chain).
                TrailEditorSession s = new TrailEditorSession(trails, p.getUniqueId(), args[1]);
                trails.putEditor(p.getUniqueId(), s);
                s.enter(p);
            }
            case "delete" -> {
                if (args.length < 2) { usage(p, "/trail delete <nom>"); return true; }
                p.sendMessage(trails.delete(args[1])
                        ? Component.text("Piste supprimée.", NamedTextColor.GREEN)
                        : Component.text("Piste inconnue.", NamedTextColor.RED));
            }
            case "list" -> {
                List<String> names = trails.names();
                p.sendMessage(Component.text("Pistes (" + names.size() + ") : "
                        + String.join(", ", names), NamedTextColor.GOLD));
            }
            case "tp" -> {
                if (args.length < 2) { usage(p, "/trail tp <nom>"); return true; }
                TrailManager.Trail t = trails.byName(args[1]);
                Location first = t == null ? null : t.pointLocation(0);
                if (first == null) {
                    p.sendMessage(Component.text("Piste inconnue (ou monde déchargé).",
                            NamedTextColor.RED));
                    return true;
                }
                p.teleport(first);
                p.sendMessage(Component.text("Téléporté au départ de « "
                        + t.name() + " ».", NamedTextColor.GREEN));
            }
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(Component.text("— /trail (staff) —", NamedTextColor.GOLD));
        p.sendMessage(Component.text("/trail create <nom> — ouvre l'éditeur (barre d'outils)",
                NamedTextColor.YELLOW));
        p.sendMessage(Component.text("/trail delete <nom> · list · tp <nom>",
                NamedTextColor.YELLOW));
        p.sendMessage(Component.text("Les joueurs montent une piste avec F près "
                + "du premier point.", NamedTextColor.GRAY));
    }

    private void usage(Player p, String usage) {
        p.sendMessage(Component.text("Usage : " + usage, NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "delete", "list", "tp"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete")
                || args[0].equalsIgnoreCase("tp"))) {
            return filter(trails.names(), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(low)) out.add(o);
        }
        return out;
    }
}
