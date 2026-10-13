package com.reborn.shinobicore.character.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.KoState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /pid <joueur|nom RP|#N°>} — staff (Helper+) : la carte d'identité d'un personnage par son Player ID. Affiche
 * le N°, le nom RP, le compte propriétaire, l'état (vivant, mort RP, supprimé ; KO / ATA si en ligne), en ligne ou
 * non, et les autres N° du même compte. Fonctionne hors ligne avec {@code #N°} (index player-ids.yml). Sans argument
 * en jeu : le personnage actif de l'appelant.
 */
public final class PidCommand implements TabExecutor {

    public static final String PERM = "reborn.staff.helper";

    private final ShinobiCore plugin;

    public PidCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!s.hasPermission(PERM)) {
            s.sendMessage(Component.text("Réservé au staff.", NamedTextColor.RED));
            return true;
        }
        PlayerIdRegistry ids = plugin.playerIds();
        if (ids == null) {
            s.sendMessage(Component.text("Index des N° indisponible.", NamedTextColor.RED));
            return true;
        }
        String arg = args.length > 0 ? String.join(" ", args).trim() : (s instanceof Player p ? p.getName() : "");
        if (arg.isEmpty()) {
            s.sendMessage(Component.text("Usage : /" + label + " <joueur|nom RP|#N°>", NamedTextColor.GRAY));
            return true;
        }

        // 1. Un N° : l'index répond même pour un personnage supprimé.
        int n = PlayerIds.parse(arg);
        PlayerIdRegistry.Entry entry = n >= 0 ? ids.get(n) : null;
        if (n >= 0 && entry == null) {
            s.sendMessage(Component.text("Aucun personnage n'a le N° " + PlayerIds.format(n) + " (prochain : "
                    + PlayerIds.format(ids.next()) + ").", NamedTextColor.RED));
            return true;
        }
        CharacterService.ResolvedCharacter r = entry != null && entry.deleted() ? null : PlayerIds.character(s, arg);
        if (r == null && entry == null) {
            s.sendMessage(Component.text("Joueur ou personnage introuvable : " + arg, NamedTextColor.RED));
            return true;
        }
        ShinobiCharacter c = r == null ? null : r.character();
        if (entry == null && c != null && c.hasPlayerId()) entry = ids.get(c.playerId());

        int number = c != null && c.hasPlayerId() ? c.playerId() : entry != null ? entry.number() : -1;
        String name = c != null ? c.name() : entry.name();
        UUID owner = r != null ? r.ownerId() : entry.ownerId();

        s.sendMessage(Component.text("N° " + (number >= 0 ? PlayerIds.format(number) : "—") + "  ", NamedTextColor.GOLD)
                .append(Component.text(name, NamedTextColor.WHITE)));
        s.sendMessage(line("Compte", ownerName(owner) + (owner != null ? "  (" + owner + ")" : "")));
        s.sendMessage(line("État", state(c, entry, owner)));
        s.sendMessage(line("En ligne", online(c, owner)));

        // Autres N° du même compte (l'index suffit, y compris les supprimés).
        List<String> others = new ArrayList<>();
        if (owner != null) {
            for (PlayerIdRegistry.Entry e : ids.entries()) {
                if (!owner.equals(e.ownerId()) || e.number() == number) continue;
                others.add("#" + e.formatted() + " " + e.name() + (e.deleted() ? " (supprimé)" : ""));
            }
        }
        if (!others.isEmpty()) s.sendMessage(line("Même compte", String.join(" · ", others)));
        return true;
    }

    private String state(ShinobiCharacter c, PlayerIdRegistry.Entry entry, UUID owner) {
        if (c == null) return entry != null && entry.deleted() ? "supprimé" : "inconnu";
        List<String> parts = new ArrayList<>();
        parts.add(c.dead() ? "mort RP" : "vivant");
        Player p = owner == null ? null : Bukkit.getPlayer(owner);
        ShinobiCharacter act = p == null ? null : plugin.characters().getActive(p.getUniqueId());
        if (act != null && act.id().equals(c.id())) {
            KoState ko = plugin.ko() == null ? null : plugin.ko().getKo(p.getUniqueId());
            if (ko != null) parts.add(ko.isDowned() ? "à terre" : "inconscient");
            if (plugin.ata() != null && plugin.ata().view(c.id()) != null) parts.add("ATA");
        }
        parts.add(c.rank().displayName() + (c.village().isBlank() ? "" : " · " + c.village()));
        return String.join(" · ", parts);
    }

    private String online(ShinobiCharacter c, UUID owner) {
        Player p = owner == null ? null : Bukkit.getPlayer(owner);
        if (p == null) return "non";
        ShinobiCharacter act = plugin.characters().getActive(p.getUniqueId());
        if (c != null && act != null && act.id().equals(c.id())) return "oui (" + p.getName() + ")";
        return "compte en ligne sur un autre personnage" + (act != null ? " (" + PlayerIds.prefix(act) + act.name() + ")" : "");
    }

    private static String ownerName(UUID owner) {
        if (owner == null) return "?";
        OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        return op.getName() != null ? op.getName() : "?";
    }

    private static Component line(String k, String v) {
        return Component.text("  " + k + " : ", NamedTextColor.GRAY).append(Component.text(v, NamedTextColor.WHITE));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1 || !s.hasPermission(PERM)) return out;
        String pre = args[0].toLowerCase(Locale.ROOT);
        if (pre.startsWith("#")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String t = PlayerIds.tag(p);
                if (!t.isEmpty() && t.startsWith(pre)) out.add(t);
            }
            return out;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(p.getName());
        }
        return out;
    }
}
