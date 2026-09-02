package com.reborn.shinobicore.ko.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Inspect the player you're looking at. Opens their {@link EtatGui}
 * silhouette so you can see all currently-tracked injuries — types,
 * origins, severities — without having to KO them first.
 *
 * <p>Uses the same FOV ray-trace as {@code /amitier} (a 10-block
 * cylinder along the viewer's look direction). The targeting rule
 * means "the person literally in front of you" — no command-line
 * argument is supported, by design (RP first).
 *
 * <p>Permission gate: {@code shinobicore.osculter}. Granted to
 * Iryō players + staff in {@code permissions.yml}; the rank is
 * intentionally not auto-granted so the medic role stays meaningful.
 */
public final class OsculterCommand implements CommandExecutor {

    /** Same range as {@code /amitier} — keep the targeting feel
     *  consistent across the RP commands. */
    private static final double RANGE  = 10.0;
    private static final double RADIUS = 0.25;

    private final ShinobiCore plugin;

    public OsculterCommand(ShinobiCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text(
                    "Cette commande nécessite un joueur.", NamedTextColor.RED));
            return true;
        }
        if (!viewer.hasPermission("shinobicore.osculter")) {
            viewer.sendMessage(Component.text(
                    "Tu n'as pas l'art médical pour ausculter quelqu'un.",
                    NamedTextColor.RED));
            return true;
        }

        Player target = rayTracePlayer(viewer, RANGE);
        if (target == null) {
            viewer.sendMessage(Component.text(
                    "Personne en face de toi à portée d'auscultation.",
                    NamedTextColor.GRAY));
            return true;
        }

        ShinobiCharacter c = plugin.characters().getActive(target.getUniqueId());
        plugin.coreGui().openEtat(viewer, target.getUniqueId(), c);
        // Auto-narration so onlookers see the medic stop and study
        // the patient. Target name is the character name when known,
        // falling back to the player username if no character is
        // active on the target's account.
        String targetName = c != null ? c.name() : target.getName();
        com.reborn.shinobicore.character.AutoMe.broadcast(plugin, viewer,
                "examine attentivement la silhouette de " + targetName + ".");
        return true;
    }

    /* ----------------------------------------------------- FOV ray-trace */

    private Player rayTracePlayer(Player viewer, double maxDist) {
        Location eye = viewer.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult trace = viewer.getWorld().rayTraceEntities(
                eye, dir, maxDist, RADIUS,
                e -> e instanceof Player other
                        && !other.getUniqueId().equals(viewer.getUniqueId()));
        if (trace == null) return null;
        Entity hit = trace.getHitEntity();
        return hit instanceof Player p ? p : null;
    }
}
