package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Résolution commune des cibles des commandes staff, avec la syntaxe <b>{@code #N°}</b> du Player ID
 * ({@code #00042} ou {@code #42}) en plus du pseudo Minecraft et du nom RP. Utilisable depuis les add-ons
 * (ShinobiAbilities…) : tout passe par {@link ShinobiCore#get()}.
 *
 * <ul>
 *   <li>{@link #online(CommandSender, String)} : un joueur <b>en ligne</b> — {@code #N°} = le joueur qui joue <em>ce</em>
 *       personnage ; sinon pseudo exact ; sinon nom RP du personnage actif ;</li>
 *   <li>{@link #character(CommandSender, String)} : un <b>personnage</b>, même hors ligne pour {@code #N°} ;</li>
 *   <li>{@link #tag(ShinobiCharacter)} : {@code "#00042"} pour les journaux (vide si pas de N°).</li>
 * </ul>
 * Le {@code #N°} n'est honoré que pour la console et le staff ({@link #mayUseRef}) ; le N° n'est jamais montré aux
 * autres joueurs : à n'utiliser que dans des retours staff et des journaux.
 */
public final class PlayerIds {

    private PlayerIds() {}

    /** {@code "#00042"} → 42 ; sinon -1. */
    public static int parse(String arg) { return PlayerIdRegistry.parse(arg); }

    /** L'argument est-il un {@code #N°} ? */
    public static boolean isRef(String arg) { return PlayerIdRegistry.parse(arg) >= 0; }

    /** {@code 42} → {@code "00042"}. */
    public static String format(int n) { return PlayerIdRegistry.format(n); }

    /** {@code "#00042"}, ou {@code ""} si le personnage n'a pas (encore) de N°. */
    public static String tag(ShinobiCharacter c) {
        return c == null || !c.hasPlayerId() ? "" : "#" + PlayerIdRegistry.format(c.playerId());
    }

    /** {@code "#00042 "} (avec espace) devant un nom, ou {@code ""}. */
    public static String prefix(ShinobiCharacter c) {
        String t = tag(c);
        return t.isEmpty() ? "" : t + " ";
    }

    /** Tag du personnage actif d'un joueur en ligne ({@code ""} sinon). */
    public static String tag(Player p) {
        ShinobiCore core = ShinobiCore.get();
        if (p == null || core == null || core.characters() == null) return "";
        return tag(core.characters().getActive(p.getUniqueId()));
    }

    /** Le joueur en ligne dont le personnage <b>actif</b> porte ce {@code #N°}, sinon null. */
    public static Player onlineByRef(String arg) {
        int n = parse(arg);
        ShinobiCore core = ShinobiCore.get();
        if (n < 0 || core == null || core.characters() == null) return null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter c = core.characters().getActive(p.getUniqueId());
            if (c != null && c.playerId() == n) return p;
        }
        return null;
    }

    /**
     * Le {@code #N°} est-il accepté pour cet émetteur ? Console, staff du Poste de garde ({@code reborn.staff.*}) ou
     * {@code shinobicore.staff} seulement : un joueur ne doit pas pouvoir sonder les N° pour retrouver des noms
     * (le système « rencontrer » garde les noms secrets).
     */
    public static boolean mayUseRef(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        return p.isOp() || StaffGrades.of(p) > StaffGrades.NONE || p.hasPermission("shinobicore.staff");
    }

    /**
     * Joueur en ligne : {@code #N°} (staff seulement, le joueur qui joue ce personnage), sinon pseudo exact, sinon
     * nom RP du personnage actif ; null sinon.
     */
    public static Player online(CommandSender s, String arg) {
        if (arg == null || arg.isBlank()) return null;
        if (isRef(arg)) return mayUseRef(s) ? onlineByRef(arg) : null;
        Player p = Bukkit.getPlayerExact(arg);
        if (p != null) return p;
        ShinobiCore core = ShinobiCore.get();
        return core == null || core.characters() == null ? null : core.characters().findOnlinePlayerByCharacter(arg);
    }

    /**
     * Personnage : {@code #N°} (staff seulement, même hors ligne via l'index), sinon personnage actif du pseudo en
     * ligne, sinon nom RP parmi les rosters chargés ; null sinon.
     */
    public static CharacterService.ResolvedCharacter character(CommandSender s, String arg) {
        ShinobiCore core = ShinobiCore.get();
        if (arg == null || arg.isBlank() || core == null || core.characters() == null) return null;
        int n = parse(arg);
        if (n >= 0) return mayUseRef(s) ? core.characters().findByPlayerId(n) : null;
        Player p = Bukkit.getPlayerExact(arg);
        if (p != null) {
            ShinobiCharacter c = core.characters().getActive(p.getUniqueId());
            if (c != null) return new CharacterService.ResolvedCharacter(p.getUniqueId(), c);
        }
        return core.characters().resolveOwned(arg);
    }

    /** UUID du compte visé : {@code #N°} (staff) → propriétaire du personnage, sinon null (au code appelant le reste). */
    public static java.util.UUID ownerByRef(CommandSender s, String arg) {
        if (!isRef(arg) || !mayUseRef(s)) return null;
        ShinobiCore core = ShinobiCore.get();
        if (core == null || core.playerIds() == null) return null;
        PlayerIdRegistry.Entry e = core.playerIds().get(parse(arg));
        return e == null || e.deleted() ? null : e.ownerId();
    }
}
