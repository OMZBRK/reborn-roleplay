package com.reborn.shinobicore.ko.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.ko.HospitalRegistry;
import com.reborn.shinobicore.ko.KoState;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /hopital} — joueur inconscient : « se laisser emmener » à l'hôpital
 * (proposé après {@code ko.hopital-propose-apres} secondes).
 * <br>{@code /hopital set <village|defaut>}, {@code suppr <village>}, {@code liste} —
 * staff ({@code shinobicore.hopital}) : placer le lit de réveil de chaque village.
 */
public final class HopitalCommand implements TabExecutor {

    private final ShinobiCore plugin;

    public HopitalCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        HospitalRegistry reg = plugin.hospitals();
        if (sub.equals("set") || sub.equals("suppr") || sub.equals("liste")) {
            if (!sender.hasPermission("shinobicore.hopital")) {
                sender.sendMessage("§cPermission refusée.");
                return true;
            }
            switch (sub) {
                case "set" -> {
                    if (!(sender instanceof Player p)) { sender.sendMessage("§cEn jeu uniquement."); return true; }
                    String village = args.length > 1 ? args[1] : HospitalRegistry.DEFAULT;
                    reg.set(village, p.getLocation());
                    p.sendMessage("§aLit d'hôpital « " + HospitalRegistry.key(village) + " » placé ici.");
                    if (plugin.staffPanel() != null) plugin.staffPanel().log(p, "monde", "Lit d'hôpital", HospitalRegistry.key(village));
                }
                case "suppr" -> {
                    if (args.length < 2) { sender.sendMessage("§cUsage : /" + label + " suppr <village>"); return true; }
                    sender.sendMessage(reg.remove(args[1]) ? "§aLit supprimé." : "§cAucun lit pour « " + args[1] + " ».");
                }
                default -> {
                    if (reg.all().isEmpty()) { sender.sendMessage("§7Aucun lit d'hôpital (réveil au spawn)."); return true; }
                    sender.sendMessage("§6Lits d'hôpital :");
                    for (Map.Entry<String, Location> e : reg.all().entrySet()) {
                        Location l = e.getValue();
                        sender.sendMessage(" §e- §f" + e.getKey() + " §7(" + l.getWorld().getName() + " "
                                + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ")");
                    }
                }
            }
            return true;
        }

        if (!(sender instanceof Player p)) { sender.sendMessage("§cEn jeu uniquement."); return true; }
        KoState st = plugin.ko().getKo(p.getUniqueId());
        if (st == null || st.isDowned() || st.cause() != KoState.Cause.HP) {
            p.sendMessage("§7Seul un shinobi inconscient peut être emmené à l'hôpital.");
            return true;
        }
        if (!st.hospitalOffered()) {
            p.sendMessage("§7Trop tôt : quelqu'un peut encore venir te secourir.");
            return true;
        }
        plugin.ko().hospitalize(p.getUniqueId());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("shinobicore.hopital")) {
            for (String s : List.of("set", "suppr", "liste")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("suppr")) {
            for (String k : plugin.hospitals().all().keySet()) if (k.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(k);
        }
        return out;
    }
}
