package fr.reborn.ost.plugin.commands;

import fr.reborn.ost.plugin.broadcast.OstBroadcaster;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Exécuteur de {@code /ost <subcommand> ...}. Sous-commandes :
 *
 * <ul>
 *   <li>{@code /ost play <trackId> <radius> [volume]} — broadcast positionnel
 *       depuis la position du sender (joueur ou spawn si console).</li>
 *   <li>{@code /ost playat <world> <x> <y> <z> <trackId> <radius> [volume]}
 *       — broadcast positionnel à des coordonnées explicites.</li>
 *   <li>{@code /ost playglobal <trackId> [volume]} — broadcast global.</li>
 *   <li>{@code /ost stop [radius]} — stop pour les joueurs dans radius,
 *       ou pour tous si radius absent / -1.</li>
 * </ul>
 *
 * <p>Permission {@code reborn.ost.broadcast} déjà déclarée dans
 * {@code paper-plugin.yml}. Volume optionnel : 1.0f par défaut.
 */
public final class OstCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "reborn.ost.broadcast";
    private static final List<String> SUBS =
        List.of("play", "playat", "playglobal", "stop");

    private final OstBroadcaster broadcaster;

    public OstCommand(OstBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("Permission refusée.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "play"        -> handlePlay(sender, args);
                case "playat"      -> handlePlayAt(sender, args);
                case "playglobal"  -> handlePlayGlobal(sender, args);
                case "stop"        -> handleStop(sender, args);
                default -> { usage(sender); yield true; }
            };
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Nombre invalide : " + e.getMessage(),
                NamedTextColor.RED));
            return true;
        }
    }

    private boolean handlePlay(CommandSender sender, String[] args) {
        // /ost play <trackId> <radius> [volume]
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                "Usage: /ost play <trackId> <radius> [volume]", NamedTextColor.YELLOW));
            return true;
        }
        String trackId = args[1];
        float radius = Float.parseFloat(args[2]);
        float volume = args.length >= 4 ? Float.parseFloat(args[3]) : 1.0f;

        Location origin = senderLocation(sender);
        if (origin == null) {
            sender.sendMessage(Component.text(
                "Pas de position de référence (sender sans monde ?).", NamedTextColor.RED));
            return true;
        }
        int n = broadcaster.playAtPosition(origin, radius, trackId, volume);
        sender.sendMessage(Component.text(
            "OST '" + trackId + "' broadcast à " + n + " joueur(s) (rayon " + radius + ").",
            NamedTextColor.GREEN));
        return true;
    }

    private boolean handlePlayAt(CommandSender sender, String[] args) {
        // /ost playat <world> <x> <y> <z> <trackId> <radius> [volume]
        if (args.length < 7) {
            sender.sendMessage(Component.text(
                "Usage: /ost playat <world> <x> <y> <z> <trackId> <radius> [volume]",
                NamedTextColor.YELLOW));
            return true;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(Component.text("Monde introuvable : " + args[1],
                NamedTextColor.RED));
            return true;
        }
        double x = Double.parseDouble(args[2]);
        double y = Double.parseDouble(args[3]);
        double z = Double.parseDouble(args[4]);
        String trackId = args[5];
        float radius = Float.parseFloat(args[6]);
        float volume = args.length >= 8 ? Float.parseFloat(args[7]) : 1.0f;

        Location target = new Location(world, x, y, z);
        int n = broadcaster.playAtPosition(target, radius, trackId, volume);
        sender.sendMessage(Component.text(
            "OST '" + trackId + "' broadcast à " + n + " joueur(s) (" + world.getName()
                + " " + x + "," + y + "," + z + " r=" + radius + ").",
            NamedTextColor.GREEN));
        return true;
    }

    private boolean handlePlayGlobal(CommandSender sender, String[] args) {
        // /ost playglobal <trackId> [volume]
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /ost playglobal <trackId> [volume]",
                NamedTextColor.YELLOW));
            return true;
        }
        String trackId = args[1];
        float volume = args.length >= 3 ? Float.parseFloat(args[2]) : 1.0f;
        int n = broadcaster.playGlobal(trackId, volume);
        sender.sendMessage(Component.text(
            "OST '" + trackId + "' broadcast global à " + n + " joueur(s).",
            NamedTextColor.GREEN));
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        // /ost stop [radius]
        Location origin = senderLocation(sender);
        float radius = -1f;
        if (args.length >= 2) {
            radius = Float.parseFloat(args[1]);
        }
        int n = broadcaster.stop(origin, radius);
        sender.sendMessage(Component.text(
            "Stop OST envoyé à " + n + " joueur(s)" + (radius < 0 ? " (TOUS)." : "."),
            NamedTextColor.GREEN));
        return true;
    }

    /**
     * Location du sender, ou spawn du monde principal si console. Null
     * uniquement si aucun monde n'est chargé.
     */
    private static Location senderLocation(CommandSender sender) {
        if (sender instanceof Player p) return p.getLocation();
        if (sender instanceof ConsoleCommandSender) {
            World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            return w == null ? null : w.getSpawnLocation();
        }
        return null;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text("--- /ost ---", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/ost play <trackId> <radius> [volume]",
            NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/ost playat <world> <x> <y> <z> <trackId> <radius> [volume]",
            NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/ost playglobal <trackId> [volume]",
            NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/ost stop [radius]",
            NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
