package com.reborn.shinobicore.cinematic;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
 * {@code /cinematic create|edit|list|delete|play|stop} — the GM authoring +
 * playback command (perm {@code shinobicore.cinematic}).
 */
public final class CinematicCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "shinobicore.cinematic";
    private static final List<String> SUBS =
            List.of("create", "edit", "list", "delete", "play", "stop");

    private final ShinobiCore plugin;

    public CinematicCommand(ShinobiCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { help(sender); return true; }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create", "edit" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Commande réservée aux joueurs.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) { usage(sender, "/cinematic " + args[0].toLowerCase(Locale.ROOT) + " <nom>"); return true; }
                CinematicEditorSession openEdit = plugin.cinematics().editor(p.getUniqueId());
                if (openEdit != null) openEdit.abort();   // restore real inv if mid-edit
                new CinematicGui(plugin, args[1]).open(p);
            }
            case "list" -> {
                List<String> names = plugin.cinematics().names();
                if (names.isEmpty()) {
                    sender.sendMessage(Component.text("Aucune cinématique.", NamedTextColor.GRAY));
                    return true;
                }
                String intro = plugin.cinematics().introCinematicName();
                sender.sendMessage(Component.text("— Cinématiques (" + names.size() + ") —", NamedTextColor.GOLD));
                for (String n : names) {
                    boolean isIntro = n.equalsIgnoreCase(intro);
                    sender.sendMessage(Component.text(" • " + n + (isIntro ? "  (intro)" : ""),
                            isIntro ? NamedTextColor.AQUA : NamedTextColor.YELLOW));
                }
            }
            case "delete" -> {
                if (args.length < 2) { usage(sender, "/cinematic delete <nom>"); return true; }
                boolean ok = plugin.cinematics().delete(args[1]);
                sender.sendMessage(Component.text(ok ? "Cinématique supprimée." : "Introuvable : " + args[1],
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case "play" -> {
                if (args.length < 2) { usage(sender, "/cinematic play <nom> [joueur]"); return true; }
                Cinematic cine = plugin.cinematics().get(args[1]);
                if (cine == null) {
                    sender.sendMessage(Component.text("Introuvable : " + args[1], NamedTextColor.RED));
                    return true;
                }
                Player target = resolveTarget(sender, args, 2);
                if (target == null) {
                    sender.sendMessage(Component.text("Joueur introuvable.", NamedTextColor.RED));
                    return true;
                }
                plugin.cinematics().play(target, cine);
                sender.sendMessage(Component.text("Lecture de « " + cine.name() + " » pour "
                        + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "stop" -> {
                Player target = resolveTarget(sender, args, 1);
                if (target == null) {
                    sender.sendMessage(Component.text("Joueur introuvable.", NamedTextColor.RED));
                    return true;
                }
                plugin.cinematics().stop(target);
                sender.sendMessage(Component.text("Cinématique arrêtée pour "
                        + target.getName() + ".", NamedTextColor.GREEN));
            }
            default -> help(sender);
        }
        return true;
    }

    private Player resolveTarget(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) return Bukkit.getPlayerExact(args[idx]);
        return sender instanceof Player p ? p : null;
    }

    private void usage(CommandSender s, String u) {
        s.sendMessage(Component.text("Usage : " + u, NamedTextColor.GRAY));
    }

    private void help(CommandSender s) {
        s.sendMessage(Component.text("— /cinematic —", NamedTextColor.GOLD));
        s.sendMessage(Component.text(" create|edit <nom> — ouvre l'éditeur", NamedTextColor.YELLOW));
        s.sendMessage(Component.text(" list — liste les cinématiques", NamedTextColor.YELLOW));
        s.sendMessage(Component.text(" delete <nom> — supprime", NamedTextColor.YELLOW));
        s.sendMessage(Component.text(" play <nom> [joueur] — joue", NamedTextColor.YELLOW));
        s.sendMessage(Component.text(" stop [joueur] — arrête", NamedTextColor.YELLOW));
        s.sendMessage(Component.text("Désigner l'intro : /character cinematic <nom>", NamedTextColor.DARK_GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERM)) return List.of();
        if (args.length == 1) return filter(SUBS, args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("edit") || sub.equals("delete") || sub.equals("play")) {
                return filter(plugin.cinematics().names(), args[1]);
            }
            if (sub.equals("stop")) return filter(onlineNames(), args[1]);
        }
        if (args.length == 3 && sub.equals("play")) {
            return filter(onlineNames(), args[2]);
        }
        return List.of();
    }

    private List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lp = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(lp)) out.add(o);
        return out;
    }
}
