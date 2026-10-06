package com.reborn.shinobicore.ko.ata;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /ata} — ton état (ATA, repos, peur).
 * <br>Staff ({@code shinobicore.ata}) : {@code /ata voir|lever|appliquer <joueur> [pleine|allegee]}.
 * « lever » tient lieu de soin médical en attendant le soin maintenu (lot KO-4).
 */
public final class AtaCommand implements TabExecutor {

    private final ShinobiCore plugin;

    public AtaCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("§cUsage : /" + label + " voir <joueur>"); return true; }
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            p.sendMessage(c == null ? "§7Aucun personnage actif." : plugin.ata().describe(c.id()));
            return true;
        }
        if (!sender.hasPermission("shinobicore.ata")) { sender.sendMessage("§cPermission refusée."); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 2) { sender.sendMessage("§cUsage : /" + label + " <voir|lever|appliquer> <joueur> [pleine|allegee]"); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        ShinobiCharacter c = target == null ? null : plugin.characters().getActive(target.getUniqueId());
        if (c == null) { sender.sendMessage("§cJoueur introuvable ou sans personnage actif."); return true; }
        switch (sub) {
            case "voir" -> sender.sendMessage("§f" + c.name() + " : " + plugin.ata().describe(c.id()));
            case "lever" -> sender.sendMessage(plugin.ata().lift(c.id(), "Un médic s'est occupé de toi.")
                    ? "§aATA levée pour " + c.name() + "." : "§7" + c.name() + " n'a pas d'ATA.");
            case "appliquer" -> {
                AtaManager.Level lvl = args.length > 2 && args[2].equalsIgnoreCase("allegee")
                        ? AtaManager.Level.ALLEGEE : AtaManager.Level.PLEINE;
                plugin.ata().apply(c.id(), target.getUniqueId(), Set.of(), lvl);
                sender.sendMessage("§aATA " + lvl.name().toLowerCase(Locale.ROOT) + " appliquée à " + c.name() + ".");
            }
            default -> sender.sendMessage("§cUsage : /" + label + " <voir|lever|appliquer> <joueur> [pleine|allegee]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("shinobicore.ata")) return out;
        if (args.length == 1) {
            for (String s : List.of("voir", "lever", "appliquer")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("appliquer")) {
            out.add("pleine");
            out.add("allegee");
        }
        return out;
    }
}
