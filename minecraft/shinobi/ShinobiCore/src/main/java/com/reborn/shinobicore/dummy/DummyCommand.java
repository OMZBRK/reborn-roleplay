package com.reborn.shinobicore.dummy;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /dummy} — staff-only training-dummy management.
 *
 * <pre>
 *   /dummy create &lt;name&gt;        spawn at your feet
 *   /dummy edit   &lt;name&gt;        chat-prompt: maxHp / maxChakra
 *   /dummy heal   &lt;name&gt;        full HP + chakra, clear injuries
 *   /dummy delete &lt;name&gt;        remove entity + record
 *   /dummy list                       list all dummies
 *   /dummy tp     &lt;name&gt;        teleport to the dummy
 * </pre>
 *
 * <p>Permission gate: {@code shinobicore.dummy} (op default). The
 * dummies themselves take damage via {@link DummyListener} and show
 * up to {@code /osculter} as if they were real characters.
 */
public final class DummyCommand implements TabExecutor {

    private final ShinobiCore plugin;

    public DummyCommand(ShinobiCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shinobicore.dummy")) {
            sender.sendMessage(Component.text(
                    "Permission insuffisante.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "edit"   -> handleEdit(sender, args);
            case "heal"   -> handleHeal(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "list"   -> handleList(sender);
            case "tp"     -> handleTeleport(sender, args);
            default       -> help(sender);
        }
        return true;
    }

    /* ----------------------------------------------------------- create */

    private void handleCreate(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Component.text(
                    "Doit être lancé en jeu (la dummy spawn à tes pieds).",
                    NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            s.sendMessage(Component.text(
                    "Usage : /dummy create <nom>", NamedTextColor.GRAY));
            return;
        }
        String name = args[1];
        Dummy d = plugin.dummies().create(name, p.getLocation());
        if (d == null) {
            p.sendMessage(Component.text(
                    "Une dummy nommée " + name + " existe déjà.",
                    NamedTextColor.RED));
            return;
        }
        plugin.dummies().refreshVisual(d);
        p.sendMessage(Component.text(
                "Dummy " + name + " créée (PV " + (int) d.maxHp()
                        + ", Chakra " + (int) d.maxChakra() + ").",
                NamedTextColor.GREEN));
    }

    /* ------------------------------------------------------------ edit */

    private void handleEdit(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        if (args.length < 2) {
            s.sendMessage(Component.text(
                    "Usage : /dummy edit <nom>", NamedTextColor.GRAY));
            return;
        }
        Dummy d = plugin.dummies().byName(args[1]);
        if (d == null) {
            s.sendMessage(Component.text(
                    "Aucune dummy nommée " + args[1] + ".",
                    NamedTextColor.RED));
            return;
        }
        // Chat-prompt flow: ask for maxHp, then maxChakra. The
        // existing ChatInputManager handles the cancel / 60-s timeout
        // boilerplate.
        p.sendMessage(Component.text(
                "Édition de " + d.name() + " — PV actuels : "
                        + (int) d.maxHp(), NamedTextColor.GOLD));
        plugin.chatInputs().prompt(p,
                "Nouveau PV max (ou 'cancel' pour garder " + (int) d.maxHp() + ") :",
                raw -> {
                    String trimmed = raw == null ? "" : raw.trim();
                    if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("cancel")) {
                        try {
                            d.setMaxHp(Double.parseDouble(trimmed));
                            d.setCurrentHp(d.maxHp());
                        } catch (NumberFormatException ex) {
                            p.sendMessage(Component.text(
                                    "Valeur PV invalide ; conservée.",
                                    NamedTextColor.RED));
                        }
                    }
                    plugin.chatInputs().prompt(p,
                            "Nouveau Chakra max (ou 'cancel' pour garder "
                                    + (int) d.maxChakra() + ") :",
                            raw2 -> {
                                String t2 = raw2 == null ? "" : raw2.trim();
                                if (!t2.isEmpty() && !t2.equalsIgnoreCase("cancel")) {
                                    try {
                                        d.setMaxChakra(Double.parseDouble(t2));
                                        d.setCurrentChakra(d.maxChakra());
                                    } catch (NumberFormatException ex) {
                                        p.sendMessage(Component.text(
                                                "Valeur Chakra invalide ; conservée.",
                                                NamedTextColor.RED));
                                    }
                                }
                                plugin.dummies().refreshVisual(d);
                                plugin.dummies().save();
                                p.sendMessage(Component.text(
                                        d.name() + " mis à jour : PV "
                                                + (int) d.maxHp() + ", Chakra "
                                                + (int) d.maxChakra() + ".",
                                        NamedTextColor.GREEN));
                            }, null);
                }, null);
    }

    /* ------------------------------------------------------------ heal */

    private void handleHeal(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(Component.text(
                    "Usage : /dummy heal <nom>", NamedTextColor.GRAY));
            return;
        }
        if (!plugin.dummies().heal(args[1])) {
            s.sendMessage(Component.text(
                    "Aucune dummy nommée " + args[1] + ".",
                    NamedTextColor.RED));
            return;
        }
        s.sendMessage(Component.text(
                args[1] + " rétabli (PV + Chakra pleins, blessures effacées).",
                NamedTextColor.GREEN));
    }

    /* ---------------------------------------------------------- delete */

    private void handleDelete(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(Component.text(
                    "Usage : /dummy delete <nom>", NamedTextColor.GRAY));
            return;
        }
        if (!plugin.dummies().delete(args[1])) {
            s.sendMessage(Component.text(
                    "Aucune dummy nommée " + args[1] + ".",
                    NamedTextColor.RED));
            return;
        }
        s.sendMessage(Component.text(
                "Dummy " + args[1] + " supprimée.", NamedTextColor.GRAY));
    }

    /* ------------------------------------------------------------ list */

    private void handleList(CommandSender s) {
        if (plugin.dummies().all().isEmpty()) {
            s.sendMessage(Component.text("Aucune dummy.", NamedTextColor.GRAY));
            return;
        }
        s.sendMessage(Component.text("Dummies :", NamedTextColor.GOLD));
        for (Dummy d : plugin.dummies().all()) {
            s.sendMessage(Component.text(
                    "  - " + d.name() + "  PV " + (int) d.currentHp()
                            + "/" + (int) d.maxHp() + "  Chakra "
                            + (int) d.currentChakra() + "/"
                            + (int) d.maxChakra()
                            + (d.ko() ? "  [KO]" : ""),
                    NamedTextColor.YELLOW));
        }
    }

    /* -------------------------------------------------------------- tp */

    private void handleTeleport(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        if (args.length < 2) {
            s.sendMessage(Component.text(
                    "Usage : /dummy tp <nom>", NamedTextColor.GRAY));
            return;
        }
        Dummy d = plugin.dummies().byName(args[1]);
        if (d == null || d.entityId() == null) {
            s.sendMessage(Component.text(
                    "Aucune dummy nommée " + args[1] + ".",
                    NamedTextColor.RED));
            return;
        }
        var ent = org.bukkit.Bukkit.getEntity(d.entityId());
        if (ent == null) {
            s.sendMessage(Component.text(
                    "L'entité de cette dummy n'est pas chargée.",
                    NamedTextColor.RED));
            return;
        }
        p.teleport(ent.getLocation());
    }

    /* ------------------------------------------------------------ help */

    private void help(CommandSender s) {
        s.sendMessage(Component.text("Commandes /dummy :", NamedTextColor.GOLD));
        s.sendMessage(Component.text("  /dummy create <nom>",  NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /dummy edit   <nom>",  NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /dummy heal   <nom>",  NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /dummy delete <nom>",  NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /dummy list",          NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /dummy tp     <nom>",  NamedTextColor.YELLOW));
    }

    /* -------------------------------------------------- tab completion */

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "edit", "heal", "delete",
                    "list", "tp"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("create")
                && !args[0].equalsIgnoreCase("list")) {
            List<String> names = new ArrayList<>();
            for (Dummy d : plugin.dummies().all()) names.add(d.name());
            return filter(names, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> all, String prefix) {
        String low = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : all) {
            if (s.toLowerCase().startsWith(low)) out.add(s);
        }
        return out;
    }
}
