package com.reborn.shinobicore.emote;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /playemote <nom> [joueur]} et {@code /stopemote} — joue/arrête une emote RP
 * (EmoteCraft) via {@link EmoteManager}. La lecture est visible par tous les joueurs
 * proches (le serveur diffuse le nom, chaque client la rejoue).
 *
 * <ul>
 *   <li>{@code /playemote <nom>} — joue sur soi.</li>
 *   <li>{@code /playemote <nom> <joueur>} — joue sur un autre (perm
 *       {@code shinobicore.emote.others}, staff / MagicSpells console).</li>
 *   <li>{@code /playemote reload} — recharge {@code emotes.yml}
 *       (perm {@code shinobicore.emote.admin}).</li>
 *   <li>{@code /stopemote [joueur]} — arrête l'emote en cours.</li>
 * </ul>
 *
 * <p>Pensé pour être appelé aussi bien par un joueur, que par la console (MagicSpells
 * {@code commandspell} : {@code playemote %a% <nom>} avec {@code run-as CONSOLE}).
 */
public final class PlayEmoteCommand implements CommandExecutor, TabCompleter {

    private final ShinobiCore plugin;

    public PlayEmoteCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        EmoteManager mgr = plugin.emotes();
        if (mgr == null) {
            sender.sendMessage(Component.text("Emotes indisponibles.", NamedTextColor.RED));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("stopemote")) {
            Player target = resolveTarget(sender, args, 0);
            if (target == null) return true;
            mgr.stop(target);
            return true;
        }

        // /playemote
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage : /playemote <nom> [joueur]", NamedTextColor.GRAY));
            return true;
        }

        // Sous-commande admin : reload.
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("shinobicore.emote.admin")) {
                sender.sendMessage(Component.text("Permission refusée.", NamedTextColor.RED));
                return true;
            }
            mgr.reload();
            sender.sendMessage(Component.text("emotes.yml rechargé.", NamedTextColor.GREEN));
            return true;
        }

        // Cible : args[1] si fourni (nécessite la perm), sinon l'émetteur.
        Player actor;
        if (args.length >= 2) {
            if (!sender.hasPermission("shinobicore.emote.others")) {
                sender.sendMessage(Component.text("Tu ne peux pas jouer une emote sur autrui.", NamedTextColor.RED));
                return true;
            }
            actor = Bukkit.getPlayerExact(args[1]);
            if (actor == null) {
                sender.sendMessage(Component.text("Joueur introuvable : " + args[1], NamedTextColor.RED));
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Depuis la console : /playemote <nom> <joueur>", NamedTextColor.RED));
                return true;
            }
            actor = p;
        }

        EmoteManager.Entry entry = mgr.resolve(args[0]);
        if (entry == null) {
            sender.sendMessage(Component.text("Emote inconnue : " + args[0], NamedTextColor.RED));
            return true;
        }
        // Permission spécifique à l'emote (le cas échéant), vérifiée sur l'émetteur.
        if (entry.permission() != null && !entry.permission().isBlank()
                && !sender.hasPermission(entry.permission())) {
            sender.sendMessage(Component.text("Cette emote t'est verrouillée.", NamedTextColor.RED));
            return true;
        }

        mgr.play(actor, entry);
        return true;
    }

    /** Résout la cible d'un stop : arg optionnel (perm others) sinon l'émetteur. */
    private Player resolveTarget(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) {
            if (!sender.hasPermission("shinobicore.emote.others")) {
                sender.sendMessage(Component.text("Permission refusée.", NamedTextColor.RED));
                return null;
            }
            Player t = Bukkit.getPlayerExact(args[idx]);
            if (t == null) sender.sendMessage(Component.text("Joueur introuvable.", NamedTextColor.RED));
            return t;
        }
        if (sender instanceof Player p) return p;
        sender.sendMessage(Component.text("Précise un joueur.", NamedTextColor.RED));
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("stopemote")) {
            if (args.length == 1 && sender.hasPermission("shinobicore.emote.others")) {
                addOnlinePlayers(out, args[0]);
            }
            return out;
        }
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            EmoteManager mgr = plugin.emotes();
            if (mgr != null) {
                for (String k : mgr.declaredKeys()) if (k.startsWith(pre)) out.add(k);
            }
            if ("reload".startsWith(pre) && sender.hasPermission("shinobicore.emote.admin")) {
                out.add("reload");
            }
        } else if (args.length == 2 && sender.hasPermission("shinobicore.emote.others")) {
            addOnlinePlayers(out, args[1]);
        }
        return out;
    }

    private void addOnlinePlayers(List<String> out, String prefix) {
        String pre = prefix.toLowerCase(Locale.ROOT);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(p.getName());
        }
    }
}
