package com.reborn.shinobicore.skill;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Bukkit front-end for the skill / roll system: the GM {@code /sc roll}
 * command and the {@code /sc competences} sheet (view, self-allocate, and
 * staff point-grant). The resolution itself lives in {@link RollService};
 * the data lives on {@link ShinobiCharacter}.
 */
public final class SkillCommands {

    private SkillCommands() {}

    /** Synergy bonus the matching body affinity grants its skill. */
    public static final int SYNERGY_BONUS = 3;

    /** Effective rating used in a roll = invested points + affinity synergy. */
    public static int effectiveSkill(ShinobiCharacter c, Skill skill) {
        int base = c.skill(skill);
        if (skill.synergyAffinity() != null && c.affinity() == skill.synergyAffinity()) {
            base += SYNERGY_BONUS;
        }
        return base;
    }

    /* ------------------------------------------------------------ /sc roll */

    public static boolean handleRoll(ShinobiCore plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("shinobicore.staff")) {
            sender.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage : /sc roll <personnage> <compétence> [difficulté] <narration…>",
                    NamedTextColor.GRAY));
            return true;
        }
        ShinobiCharacter target = findCharacter(plugin, args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Aucun personnage trouvé pour : " + args[1],
                    NamedTextColor.RED));
            return true;
        }
        Skill skill = Skill.from(args[2]);
        if (skill == null) {
            sender.sendMessage(Component.text("Compétence inconnue : " + args[2]
                    + " (Force, Agilité, Endurance, Intelligence, Perception, Contrôle, Présence)",
                    NamedTextColor.RED));
            return true;
        }
        // Optional difficulty at args[3]. If it doesn't parse as a DC/preset,
        // everything from args[3] on is treated as narration (open mode).
        Integer dc = (args.length >= 4) ? RollService.parseDc(args[3]) : null;
        int narrationStart = (dc != null) ? 4 : 3;
        String narration = (args.length > narrationStart)
                ? String.join(" ", Arrays.copyOfRange(args, narrationStart, args.length))
                : "";

        int eff = effectiveSkill(target, skill);
        RollResult r = RollService.resolve(eff, dc);
        broadcastCard(plugin, sender, target, skill, r, narration);
        return true;
    }

    private static void broadcastCard(ShinobiCore plugin, CommandSender sender,
                                      ShinobiCharacter target, Skill skill,
                                      RollResult r, String narration) {
        Component story = narration.isEmpty() ? null
                : Component.text(narration, NamedTextColor.WHITE);

        NamedTextColor col = colorFor(r.outcome());
        Component line = switch (r.outcome()) {
            case AUTO_SUCCESS -> Component.text("✦ Routine pour " + target.name()
                    + " — réussite automatique (" + skill.displayName() + " " + r.skill() + ")", col);
            case AUTO_FAIL -> Component.text("✘ Hors de portée — échec ("
                    + skill.displayName() + " " + r.skill() + " vs DD " + r.dc() + ")", col);
            case OPEN -> Component.text("🎲 " + skill.displayName() + " " + r.skill()
                    + " + dé " + r.die() + " = " + r.total(), col);
            default -> Component.text("🎲 " + skill.displayName() + " " + r.skill()
                    + " + dé " + r.die() + " = " + r.total()
                    + " · DD " + r.dc() + " → " + label(r.outcome()), col);
        };

        Component header = Component.text("— ", NamedTextColor.DARK_GRAY)
                .append(Component.text(target.name(), NamedTextColor.YELLOW))
                .append(Component.text(" · " + skill.displayName() + " —", NamedTextColor.GRAY));

        Set<Player> audience = sceneAudience(plugin, sender, target);
        for (Player p : audience) {
            p.sendMessage(header);
            if (story != null) p.sendMessage(story);
            p.sendMessage(line);
        }
        // A console GM still wants the result echoed.
        if (!(sender instanceof Player)) {
            if (story != null) sender.sendMessage(story);
            sender.sendMessage(line);
        }
    }

    /** The GM plus everyone in the scene (radius around the GM) plus the
     *  target's online player. */
    private static Set<Player> sceneAudience(ShinobiCore plugin, CommandSender sender,
                                             ShinobiCharacter target) {
        Set<Player> out = new HashSet<>();
        double radius = plugin.getConfig().getDouble("rolls.scene-radius", 30.0);
        if (sender instanceof Player gm) {
            out.add(gm);
            Location at = gm.getLocation();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(at.getWorld())
                        && p.getLocation().distanceSquared(at) <= radius * radius) {
                    out.add(p);
                }
            }
        }
        Player owner = Bukkit.getPlayer(target.ownerId());
        if (owner != null && owner.isOnline()) out.add(owner);
        return out;
    }

    private static String label(RollResult.Outcome o) {
        return switch (o) {
            case CRITICAL -> "Réussite éclatante";
            case SUCCESS -> "Réussite";
            case PARTIAL -> "Réussite partielle";
            case FAILURE -> "Échec";
            case AUTO_SUCCESS -> "Réussite automatique";
            case AUTO_FAIL -> "Échec";
            case OPEN -> "";
        };
    }

    private static NamedTextColor colorFor(RollResult.Outcome o) {
        return switch (o) {
            case CRITICAL -> NamedTextColor.GOLD;
            case SUCCESS, AUTO_SUCCESS -> NamedTextColor.GREEN;
            case PARTIAL -> NamedTextColor.YELLOW;
            case FAILURE, AUTO_FAIL -> NamedTextColor.RED;
            case OPEN -> NamedTextColor.AQUA;
        };
    }

    /* ------------------------------------------------------ /sc competences */

    public static boolean handleSkills(ShinobiCore plugin, Player player, String[] args) {
        // Staff point-grant: /sc competences give <personnage> <points>
        if (args.length >= 2 && "give".equalsIgnoreCase(args[1])) {
            if (!player.hasPermission("shinobicore.staff")) {
                player.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 4) {
                player.sendMessage(Component.text(
                        "Usage : /sc competences give <personnage> <points>", NamedTextColor.GRAY));
                return true;
            }
            ShinobiCharacter target = findCharacter(plugin, args[2]);
            if (target == null) {
                player.sendMessage(Component.text("Aucun personnage trouvé pour : " + args[2],
                        NamedTextColor.RED));
                return true;
            }
            int n = parseInt(args[3]);
            if (n == Integer.MIN_VALUE) {
                player.sendMessage(Component.text("Nombre invalide : " + args[3], NamedTextColor.RED));
                return true;
            }
            target.addSkillPoints(n);
            plugin.characters().save(target);
            player.sendMessage(Component.text(target.name() + " a maintenant "
                    + target.skillPoints() + " point(s) à répartir.", NamedTextColor.GREEN));
            return true;
        }

        ShinobiCharacter c = Players.activeOrWarn(plugin, player);
        if (c == null) return true;

        // Self-allocate: /sc competences <compétence> <points>
        if (args.length >= 3) {
            Skill skill = Skill.from(args[1]);
            if (skill == null) {
                player.sendMessage(Component.text("Compétence inconnue : " + args[1], NamedTextColor.RED));
                return true;
            }
            int n = parseInt(args[2]);
            if (n == Integer.MIN_VALUE || n <= 0) {
                player.sendMessage(Component.text("Indique un nombre de points positif.", NamedTextColor.RED));
                return true;
            }
            if (n > c.skillPoints()) {
                player.sendMessage(Component.text("Points insuffisants ("
                        + c.skillPoints() + " disponibles).", NamedTextColor.RED));
                return true;
            }
            int cap = c.skillCap();
            if (c.skill(skill) + n > cap) {
                player.sendMessage(Component.text(skill.displayName()
                        + " est plafonné à " + cap + " à ton niveau.", NamedTextColor.RED));
                return true;
            }
            c.setSkill(skill, c.skill(skill) + n);
            c.setSkillPoints(c.skillPoints() - n);
            plugin.characters().save(c);
            player.sendMessage(Component.text(skill.displayName() + " → " + c.skill(skill)
                    + "  (" + c.skillPoints() + " points restants)", NamedTextColor.GREEN));
            return true;
        }

        // View
        player.sendMessage(Component.text("— Compétences de " + c.name() + " —", NamedTextColor.GOLD));
        for (Skill sk : Skill.values()) {
            int base = c.skill(sk);
            int eff = effectiveSkill(c, sk);
            String syn = (eff > base) ? " §7(+" + (eff - base) + " affinité)" : "";
            player.sendMessage(Component.text("  " + sk.displayName() + " : " + base, NamedTextColor.YELLOW)
                    .append(Component.text(syn.replace("§7", ""), NamedTextColor.DARK_GRAY)));
        }
        player.sendMessage(Component.text("Points à répartir : " + c.skillPoints(), NamedTextColor.AQUA));
        player.sendMessage(Component.text("→ /sc competences <compétence> <points>", NamedTextColor.DARK_GRAY));
        return true;
    }

    /* --------------------------------------------------------------- helpers */

    private static ShinobiCharacter findCharacter(ShinobiCore plugin, String name) {
        return plugin.characters().resolveByName(name);
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException ex) { return Integer.MIN_VALUE; }
    }
}
