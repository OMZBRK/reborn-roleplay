package com.reborn.shinobiabilities.command;

import com.reborn.shinobiabilities.ShinobiAbilities;
import com.reborn.shinobiabilities.mobility.MobilityModule;
import com.reborn.shinobiabilities.mobility.MobilityPaths;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.api.CharacterService;
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
 * {@code /mobility path <personnage> <none|one|two>} — staff command to set a
 * character's learned mobility path (interim, until parkour teaches it). Targets
 * a CHARACTER by name (not a player). Switching cleanly resets the owner's live
 * mobility state if they're online.
 */
public final class MobilityPathCommand implements CommandExecutor, TabCompleter {

    private final ShinobiAbilities plugin;
    private final CoreServices core;
    private final MobilityPaths paths;

    public MobilityPathCommand(ShinobiAbilities plugin, CoreServices core, MobilityPaths paths) {
        this.plugin = plugin;
        this.core = core;
        this.paths = paths;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("shinobicore.admin") && !sender.hasPermission("shinobicore.staff")) {
            sender.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("path")) {
            sender.sendMessage(Component.text("Usage : /mobility path <personnage> <none|one|two>", NamedTextColor.GRAY));
            return true;
        }
        MobilityPaths.Path path = parse(args[2]);
        if (path == null) {
            sender.sendMessage(Component.text("Voie invalide : " + args[2] + " (none|one|two).", NamedTextColor.RED));
            return true;
        }
        CharacterService.ResolvedCharacter rc = core.characters().resolveOwned(args[1]);
        if (rc == null || rc.character() == null) {
            sender.sendMessage(Component.text("Personnage introuvable : " + args[1], NamedTextColor.RED));
            return true;
        }
        paths.set(rc.character().id(), path);

        Player owner = Bukkit.getPlayer(rc.ownerId());
        if (owner != null && owner.isOnline()) {
            resetMobility(owner);
            owner.sendActionBar(Component.text("Voie de déplacement : " + path.display(), NamedTextColor.AQUA));
        }
        sender.sendMessage(Component.text(
                "Voie de « " + rc.character().name() + " » → " + path.display() + ".", NamedTextColor.GREEN));
        return true;
    }

    /** Clear live Path-One mobility state so a switch doesn't leak speed/cooldowns. */
    private void resetMobility(Player p) {
        MobilityModule m = plugin.mobility();
        if (m == null) return;
        if (m.narutoRun() != null) { m.narutoRun().stop(p, false); m.narutoRun().removeModifier(p); }
        if (m.dash() != null) m.dash().clear(p);
        if (m.climb() != null) m.climb().clear(p);
        if (m.shockwave() != null) m.shockwave().clear(p);
        if (m.trails() != null) m.trails().stopRide(p, false);
        if (plugin.pathTwo() != null) plugin.pathTwo().reset(p);   // strip Voie du Flux attrs
    }

    private static MobilityPaths.Path parse(String s) {
        switch (s.toLowerCase(Locale.ROOT)) {
            case "none", "aucune", "0" -> { return MobilityPaths.Path.NONE; }
            case "one", "1", "chakra" -> { return MobilityPaths.Path.ONE; }
            case "two", "2", "flux" -> { return MobilityPaths.Path.TWO; }
            default -> { return null; }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return prefix(List.of("path"), args[0]);
        if (args.length == 2) return prefix(core.characters().allCharacterNames(), args[1]);
        if (args.length == 3) return prefix(List.of("none", "one", "two"), args[2]);
        return List.of();
    }

    private static List<String> prefix(List<String> opts, String pre) {
        String low = pre == null ? "" : pre.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : opts) if (o.toLowerCase(Locale.ROOT).startsWith(low)) out.add(o);
        return out;
    }
}
