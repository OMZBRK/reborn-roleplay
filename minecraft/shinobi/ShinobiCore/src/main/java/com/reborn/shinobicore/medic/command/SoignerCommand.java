package com.reborn.shinobicore.medic.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.dummy.Dummy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /soigner} — open the heal silhouette on whoever is in front
 * of you.
 *
 * <p>Targets:
 * <ul>
 *   <li>A real player (KO or not) — opens with their active
 *       character's injuries.</li>
 *   <li>A training dummy — opens with the dummy's injuries.</li>
 * </ul>
 *
 * <p>Ray-trace identical to {@code /amitier} / {@code /osculter} —
 * 10 blocks, 0.25 cylinder radius. With no target in the cone, you
 * get a French miss message instead of an empty GUI.
 */
public final class SoignerCommand implements CommandExecutor {

    private static final double RANGE  = 10.0;
    private static final double RADIUS = 0.25;

    private final ShinobiCore plugin;

    public SoignerCommand(ShinobiCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text(
                    "Cette commande nécessite un joueur.", NamedTextColor.RED));
            return true;
        }

        Entity hit = rayTrace(viewer);
        if (hit == null) {
            viewer.sendMessage(Component.text(
                    "Personne en face de toi à portée de soin.",
                    NamedTextColor.GRAY));
            return true;
        }

        // Dummy first (Villager), then Player.
        if (hit instanceof Villager v) {
            Dummy d = plugin.dummies().byEntity(v.getUniqueId());
            if (d != null) {
                plugin.coreGui().openSoignerForDummy(viewer, d);
                return true;
            }
        }
        if (hit instanceof Player target) {
            ShinobiCharacter c =
                    plugin.characters().getActive(target.getUniqueId());
            if (c == null) {
                viewer.sendMessage(Component.text(
                        "La cible n'a pas de personnage actif.",
                        NamedTextColor.RED));
                return true;
            }
            plugin.coreGui().openSoignerForPlayer(viewer, target.getUniqueId(), c);
            return true;
        }
        viewer.sendMessage(Component.text(
                "Cette cible ne peut pas être soignée.",
                NamedTextColor.GRAY));
        return true;
    }

    private static Entity rayTrace(Player viewer) {
        Location eye = viewer.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult trace = viewer.getWorld().rayTraceEntities(
                eye, dir, RANGE, RADIUS,
                e -> (e instanceof Player p && !p.getUniqueId().equals(viewer.getUniqueId()))
                        || e instanceof Villager);
        return trace == null ? null : trace.getHitEntity();
    }
}
