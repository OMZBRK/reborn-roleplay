package com.reborn.shinobisense;

import com.reborn.shinobicore.character.ShinobiCharacter;
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

/**
 * {@code /sense} — the perception hub. Bare {@code /sense} fires a fuzzy chakra
 * pulse; subcommands cover the rest of the Perception Web:
 * <ul>
 *   <li>{@code /sense suppress} — mask your own chakra</li>
 *   <li>{@code /sense dojutsu}  — toggle your dōjutsu (sharper, but visible)</li>
 *   <li>{@code /sense genjutsu <perso> [s]} — trap a target in an illusion</li>
 *   <li>{@code /sense kai} — break a genjutsu you're under</li>
 * </ul>
 */
public final class SenseCommand implements CommandExecutor, TabCompleter {

    private final ShinobiSense plugin;

    public SenseCommand(ShinobiSense plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Réservé aux joueurs.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "suppress", "masque", "masquer" -> {
                    boolean now = plugin.sense().toggleSuppress(p.getUniqueId());
                    p.sendActionBar(Component.text(now
                            ? "Tu masques ton chakra."
                            : "Tu relâches ton chakra.", NamedTextColor.DARK_AQUA));
                    return true;
                }
                case "dojutsu", "doujutsu", "yeux" -> { plugin.dojutsu().toggle(p); return true; }
                case "kai", "libere", "liberer" -> { plugin.genjutsu().kai(p); return true; }
                case "genjutsu", "illusion" -> { return castGenjutsu(p, args); }
                default -> { /* fall through to a pulse */ }
            }
        }
        plugin.sense().pulse(p);
        return true;
    }

    private boolean castGenjutsu(Player caster, String[] args) {
        if (args.length < 2) {
            caster.sendMessage(err("Usage : /sense genjutsu <personnage> [secondes]"));
            return true;
        }
        Player target = plugin.sense().resolveOnlinePlayerByCharacter(args[1]);
        if (target == null) {
            caster.sendMessage(err("Cible introuvable ou hors-ligne : " + args[1]));
            return true;
        }
        if (target.equals(caster)) {
            caster.sendMessage(err("Tu ne peux pas t'illusionner toi-même."));
            return true;
        }
        double range = plugin.getConfig().getDouble("genjutsu.cast-range", 15.0);
        if (!target.getWorld().equals(caster.getWorld())
                || target.getLocation().distanceSquared(caster.getLocation()) > range * range) {
            caster.sendMessage(err(args[1] + " est trop loin (portée " + (int) range + "m)."));
            return true;
        }
        ShinobiCharacter c = plugin.characters() != null
                ? plugin.characters().getActive(caster.getUniqueId()) : null;
        double cost = plugin.getConfig().getDouble("genjutsu.chakra-cost", 25.0);
        if (c != null && cost > 0) c.chakra().overdraw(cost);
        int secs = plugin.getConfig().getInt("genjutsu.duration-seconds", 12);
        if (args.length >= 3) {
            try {
                secs = Math.max(3, Math.min(60, Integer.parseInt(args[2].trim())));
            } catch (NumberFormatException ignore) { /* keep default */ }
        }
        plugin.genjutsu().cast(caster, target, secs);
        return true;
    }

    private static Component err(String t) { return Component.text(t, NamedTextColor.RED); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String low = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String o : List.of("suppress", "dojutsu", "genjutsu", "kai")) {
                if (o.startsWith(low)) out.add(o);
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("genjutsu") || args[0].equalsIgnoreCase("illusion"))) {
            String low = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if (plugin.characters() != null) {
                for (var roster : plugin.characters().rosterView().values()) {
                    for (ShinobiCharacter c : roster) {
                        if (c.name().toLowerCase(Locale.ROOT).startsWith(low)) out.add(c.name());
                    }
                }
            }
            return out;
        }
        return List.of();
    }
}
