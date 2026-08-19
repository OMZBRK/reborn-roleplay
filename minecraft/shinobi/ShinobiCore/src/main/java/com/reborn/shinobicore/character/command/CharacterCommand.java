package com.reborn.shinobicore.character.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.CharacterManager;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.CharacterListScreen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplified {@code /character} command. All editing happens in the GUI now;
 * the command's job is to get admins / players *into* the GUI.
 *
 * <pre>
 *   /character create &lt;player&gt;   — open a "pick a name in chat" flow, then editor
 *   /character edit   &lt;player&gt;   — open the character-picker for that owner
 *   /character select             — self only, opens the select GUI
 *   /character list   [player]
 *   /character leaftest           — self only
 * </pre>
 *
 * Deletes happen inside the editor GUI (shift-click the barrier). Value edits
 * happen inside the editor GUI (each slot uses a GUI-first → chat-input flow).
 *
 * Admin permission: {@code shinobicore.character.admin}.
 */
public class CharacterCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERM = "shinobicore.character.admin";

    private final ShinobiCore plugin;

    public CharacterCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* ----------------------------------------------------------- execute */

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { help(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "create"   -> handleCreate(sender, args);
            case "edit"     -> handleEdit(sender, args);
            case "select"   -> handleSelect(sender);
            case "list"     -> handleList(sender, args);
            case "leaftest" -> handleLeafTest(sender);
            case "cinematic" -> handleCinematic(sender, args);
            default -> help(sender);
        }
        return true;
    }

    /* ----------------------------------------------------------- helpers */

    private void help(CommandSender s) {
        // Players see only the entries that operate on their own
        // characters (select, list, leaftest). Staff additionally see
        // the create/edit subcommands which require ADMIN_PERM.
        s.sendMessage(Component.text("Commandes de personnage :", NamedTextColor.GOLD));
        s.sendMessage(Component.text("  /character select",   NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /character list",     NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /character leaftest", NamedTextColor.YELLOW));
        if (s.hasPermission(ADMIN_PERM)) {
            s.sendMessage(Component.text("Admin :", NamedTextColor.LIGHT_PURPLE));
            s.sendMessage(Component.text("  /character create <joueur>", NamedTextColor.GRAY));
            s.sendMessage(Component.text("  /character edit   <joueur>", NamedTextColor.GRAY));
            s.sendMessage(Component.text("  /character list   [joueur]", NamedTextColor.GRAY));
            s.sendMessage(Component.text("  /character cinematic <nom> — désigne l'intro", NamedTextColor.GRAY));
        }
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        @SuppressWarnings("deprecation")
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off.hasPlayedBefore() ? off : null;
    }

    /* ------------------------------------------------------------ create */

    private void handleCreate(CommandSender s, String[] args) {
        if (!s.hasPermission(ADMIN_PERM)) {
            s.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
            return;
        }
        if (!(s instanceof Player admin)) {
            s.sendMessage(Component.text("Exécute /character create en jeu.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            s.sendMessage(Component.text("Usage : /character create <joueur>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            s.sendMessage(Component.text("Joueur inconnu ou jamais connecté : " + args[1], NamedTextColor.RED));
            return;
        }

        String targetName = target.getName() != null ? target.getName() : args[1];
        plugin.chatInputs().prompt(admin,
                "Tape le nom du nouveau personnage pour " + targetName + " dans le chat :",
                raw -> {
                    String name = raw.trim();
                    if (name.isEmpty()) {
                        admin.sendMessage(Component.text("Le nom ne peut pas être vide. Annulé.", NamedTextColor.RED));
                        return;
                    }
                    CharacterManager mgr = plugin.characters();
                    if (mgr.findByName(target.getUniqueId(), name).isPresent()) {
                        admin.sendMessage(Component.text(
                                "Ce joueur a déjà un personnage portant ce nom.",
                                NamedTextColor.RED));
                        return;
                    }
                    ShinobiCharacter c = mgr.create(target.getUniqueId(), name);
                    admin.sendMessage(Component.text(
                            "Personnage '" + name + "' créé pour " + targetName + ". Ouverture de l'éditeur...",
                            NamedTextColor.GREEN));
                    plugin.coreGui().openCharacterEdit(admin, c);
                },
                null);
    }

    /* ------------------------------------------------------------- edit */

    private void handleEdit(CommandSender s, String[] args) {
        if (!s.hasPermission(ADMIN_PERM)) {
            s.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
            return;
        }
        if (!(s instanceof Player admin)) {
            s.sendMessage(Component.text("Exécute /character edit en jeu.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            s.sendMessage(Component.text("Usage : /character edit <joueur>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            s.sendMessage(Component.text("Joueur inconnu ou jamais connecté : " + args[1], NamedTextColor.RED));
            return;
        }

        List<ShinobiCharacter> chars = plugin.characters().getAll(target.getUniqueId());
        if (chars.isEmpty()) {
            admin.sendMessage(Component.text(
                    target.getName() + " n'a aucun personnage. Utilise /character create " + target.getName(),
                    NamedTextColor.RED));
            return;
        }
        plugin.coreGui().openCharacterList(admin, target.getUniqueId(), CharacterListScreen.Action.EDIT);
    }

    /* ----------------------------------------------------------- select */

    private void handleSelect(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Component.text("Réservé aux joueurs.", NamedTextColor.RED));
            return;
        }
        List<ShinobiCharacter> mine = plugin.characters().getAll(p.getUniqueId());
        if (mine.isEmpty()) {
            p.sendMessage(Component.text(
                    "Tu n'as aucun personnage. Demande à un administrateur d'en créer un.",
                    NamedTextColor.RED));
            return;
        }
        plugin.coreGui().openCharacterList(p, p.getUniqueId(), CharacterListScreen.Action.SELECT);
    }

    /* ------------------------------------------------------------- list */

    private void handleList(CommandSender s, String[] args) {
        java.util.UUID ownerId;
        String ownerName;
        if (args.length >= 2) {
            if (!s.hasPermission(ADMIN_PERM)) {
                s.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
                return;
            }
            OfflinePlayer t = resolvePlayer(args[1]);
            if (t == null) {
                s.sendMessage(Component.text("Joueur inconnu ou jamais connecté.", NamedTextColor.RED));
                return;
            }
            ownerId = t.getUniqueId();
            ownerName = t.getName();
        } else if (s instanceof Player p) {
            ownerId = p.getUniqueId();
            ownerName = p.getName();
        } else {
            s.sendMessage(Component.text("Usage : /character list <joueur>", NamedTextColor.RED));
            return;
        }

        List<ShinobiCharacter> chars = plugin.characters().getAll(ownerId);
        s.sendMessage(Component.text("Personnages de " + ownerName + " (" + chars.size() + ") :", NamedTextColor.GOLD));
        for (ShinobiCharacter c : chars) {
            s.sendMessage(Component.text("  - " + c.name() + "  [Niv " + c.level() + ", "
                    + c.clan() + ", " + c.affinity().displayName() + "/"
                    + joinAffinities(c) + "]", NamedTextColor.YELLOW));
        }
    }

    /** Joined display names of a character's chakra affinities, or "None". */
    private static String joinAffinities(ShinobiCharacter c) {
        if (c.chakraAffinities().isEmpty()) return "Aucune";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ChakraAffinity a : c.chakraAffinities()) {
            if (!first) sb.append('+');
            sb.append(a.displayName());
            first = false;
        }
        return sb.toString();
    }

    /* --------------------------------------------------------- leaftest */

    private void handleLeafTest(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Component.text("Réservé aux joueurs.", NamedTextColor.RED));
            return;
        }
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) {
            p.sendMessage(Component.text("Select a character first: /character select", NamedTextColor.RED));
            return;
        }
        // The Leaf Test is now a gambling-machine GUI — open it directly.
        // The legacy fixed-5-rolls `rollLeafTest()` on ShinobiCharacter is
        // preserved for save-file compatibility but no longer drives the
        // player-facing flow.
        plugin.coreGui().openLeafTest(p, c);
    }

    /* --------------------------------------------------------- cinematic */

    /** {@code /character cinematic [<nom>|none]} — designate (or show/clear)
     *  the first-play intro cinematic. Staff only. */
    private void handleCinematic(CommandSender s, String[] args) {
        if (!s.hasPermission(ADMIN_PERM)) {
            s.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
            return;
        }
        var cm = plugin.cinematics();
        if (args.length < 2) {
            String cur = cm.introCinematicName();
            s.sendMessage(Component.text("Cinématique d'intro : "
                    + (cur != null ? cur : "(aucune)"), NamedTextColor.GOLD));
            s.sendMessage(Component.text("Usage : /character cinematic <nom|none>", NamedTextColor.GRAY));
            return;
        }
        String name = args[1];
        if (name.equalsIgnoreCase("none") || name.equalsIgnoreCase("clear")) {
            cm.setIntroCinematicName(null);
            s.sendMessage(Component.text("Cinématique d'intro retirée.", NamedTextColor.GREEN));
            return;
        }
        if (cm.get(name) == null) {
            s.sendMessage(Component.text("Cinématique introuvable : " + name
                    + " (crée-la avec /cinematic create " + name + ")", NamedTextColor.RED));
            return;
        }
        cm.setIntroCinematicName(name);
        s.sendMessage(Component.text("Cinématique d'intro définie : " + name, NamedTextColor.GREEN));
    }

    /* ---------------------------------------------------------- tab-complete */

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "edit", "select", "list", "leaftest", "cinematic"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("create")
                || args[0].equalsIgnoreCase("edit")
                || args[0].equalsIgnoreCase("list"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return filter(names, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cinematic")) {
            List<String> opts = new ArrayList<>(plugin.cinematics().names());
            opts.add("none");
            return filter(opts, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> base, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : base) if (s.toLowerCase().startsWith(lower)) out.add(s);
        return out;
    }
}
