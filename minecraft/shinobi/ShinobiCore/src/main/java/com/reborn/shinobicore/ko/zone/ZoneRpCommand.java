package com.reborn.shinobicore.ko.zone;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /zonerp pos1|pos2|creer <entrainement|repos> <id>|suppr <id>|liste|ici} — outil staff
 * pour délimiter les terrains d'entraînement et les zones de repos (restaurant, onsen,
 * auberge, hôpital). pos1 / pos2 prennent le bloc sous les pieds.
 */
public final class ZoneRpCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("pos1", "pos2", "creer", "suppr", "liste", "ici");

    private final TrainingZones zones;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public ZoneRpCommand(TrainingZones zones) {
        this.zones = zones;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "pos1", "pos2" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cEn jeu uniquement."); return true; }
                Location[] sel = selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
                Location loc = p.getLocation().getBlock().getLocation();
                sel[sub.equals("pos1") ? 0 : 1] = loc;
                p.sendMessage("§a" + sub + " §7= " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            }
            case "creer" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cEn jeu uniquement."); return true; }
                TrainingZone.Kind kind = args.length >= 3 ? TrainingZone.Kind.parse(args[1]) : null;
                if (kind == null) { p.sendMessage("§cUsage : /" + label + " creer <entrainement|repos> <id>"); return true; }
                Location[] sel = selections.get(p.getUniqueId());
                if (sel == null || sel[0] == null || sel[1] == null) {
                    p.sendMessage("§cDéfinis d'abord pos1 et pos2.");
                    return true;
                }
                if (!sel[0].getWorld().equals(sel[1].getWorld())) {
                    p.sendMessage("§cpos1 et pos2 doivent être dans le même monde.");
                    return true;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                TrainingZone z = TrainingZone.of(id, kind, sel[0], sel[1]);
                zones.put(z);
                selections.remove(p.getUniqueId());
                p.sendMessage("§aZone enregistrée : §f" + z.describe());
                var core = org.bukkit.plugin.java.JavaPlugin.getPlugin(com.reborn.shinobicore.ShinobiCore.class);
                if (core.staffPanel() != null) core.staffPanel().log(p, "monde", "Zone créée", kind.label() + " « " + id + " »");
            }
            case "suppr" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage : /" + label + " suppr <id>"); return true; }
                sender.sendMessage(zones.remove(args[1].toLowerCase(Locale.ROOT))
                        ? "§aZone supprimée." : "§cAucune zone « " + args[1] + " ».");
            }
            case "liste" -> {
                if (zones.all().isEmpty()) { sender.sendMessage("§7Aucune zone RP."); return true; }
                sender.sendMessage("§6Zones RP :");
                for (TrainingZone z : zones.all()) sender.sendMessage(" §e- §f" + z.describe());
            }
            case "ici" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cEn jeu uniquement."); return true; }
                TrainingZone z = zones.anyZoneAt(p.getLocation());
                p.sendMessage(z == null ? "§7Tu n'es dans aucune zone RP."
                        : "§aTu es dans §f" + z.describe()
                          + (zones.isTraining(p) ? " §7(entraînement en cours)" : ""));
            }
            default -> sender.sendMessage("§cUsage : /" + label
                    + " <pos1|pos2|creer <entrainement|repos> <id>|suppr <id>|liste|ici>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("creer")) {
            for (TrainingZone.Kind k : TrainingZone.Kind.values())
                if (k.label().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(k.label());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("suppr")) {
            for (TrainingZone z : zones.all()) if (z.id().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(z.id());
        }
        return out;
    }
}
