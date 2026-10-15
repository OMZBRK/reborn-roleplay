package com.reborn.shinobicore.character.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.ShinobiCharacter;
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
 * {@code /character} — côté joueur seulement, plus un raccourci staff de lecture.
 *
 * <pre>
 *   /character select             — ses personnages (repli de la sélection du mod)
 *   /character list   [joueur]    — liste texte ; [joueur] = staff, utilisable en console
 *   /character cinematic &lt;nom|none&gt; — staff : cinématique d'intro
 * </pre>
 *
 * La gestion staff des personnages (création, édition, clan, grade, mort RP, emplacements, remise à zéro,
 * suppression) se fait au Poste de garde (F7 › fiche du joueur › Personnages) depuis le 15/10 ; les anciens
 * {@code /character create|edit} et leurs écrans-coffres ont été retirés.
 *
 * Permission staff : {@code shinobicore.character.admin}.
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
            case "create", "edit" -> sender.sendMessage(Component.text(
                    "La gestion des personnages se fait au Poste de garde : F7 › fiche du joueur › Personnages.",
                    NamedTextColor.GRAY));
            case "select"   -> handleSelect(sender);
            case "list"     -> handleList(sender, args);
            case "cinematic" -> handleCinematic(sender, args);
            default -> help(sender);
        }
        return true;
    }

    /* ----------------------------------------------------------- helpers */

    private void help(CommandSender s) {
        // Players see only the entries that operate on their own
        // characters (select, list). Staff additionally see the
        // read-only list of another player and the intro cinematic.
        s.sendMessage(Component.text("Commandes de personnage :", NamedTextColor.GOLD));
        s.sendMessage(Component.text("  /character select",   NamedTextColor.YELLOW));
        s.sendMessage(Component.text("  /character list",     NamedTextColor.YELLOW));
        if (s.hasPermission(ADMIN_PERM)) {
            s.sendMessage(Component.text("Admin :", NamedTextColor.LIGHT_PURPLE));
            s.sendMessage(Component.text("  /character list   [joueur]", NamedTextColor.GRAY));
            s.sendMessage(Component.text("  Gérer : Poste de garde (F7) › fiche › Personnages", NamedTextColor.GRAY));
            s.sendMessage(Component.text("  /character cinematic <nom> — désigne l'intro", NamedTextColor.GRAY));
        }
    }

    private OfflinePlayer resolvePlayer(CommandSender s, String name) {
        java.util.UUID byPid = com.reborn.shinobicore.character.PlayerIds.ownerByRef(s, name);   // #N° (staff) → compte du personnage
        if (byPid != null) return Bukkit.getOfflinePlayer(byPid);
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        @SuppressWarnings("deprecation")
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off.hasPlayedBefore() ? off : null;
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
        plugin.coreGui().openCharacterList(p);
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
            OfflinePlayer t = resolvePlayer(s, args[1]);
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
            s.sendMessage(Component.text("  - " + c.name() + "  ["
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
            return filter(s.hasPermission(ADMIN_PERM) ? List.of("select", "list", "cinematic") : List.of("select", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list") && s.hasPermission(ADMIN_PERM)) {
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
