package com.reborn.shinobicore.monnaie;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.banque.Compte;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /ryo} (MONNAIE_ET_BANQUE.md §11) :
 * <ul>
 *   <li>joueur : « Sur toi : 1 240 ryo » (la bourse ; le compte ne se voit qu'au guichet) ;</li>
 *   <li>staff (Admin, ou {@code shinobicore.ryo.admin}) : {@code /ryo donner|retirer|fixer <cible> <montant>
 *       [bourse|compte] "<motif>"} — cible = pseudo, nom RP ou {@code #N°} (le compte même hors ligne ; la bourse
 *       seulement en ligne). <b>Motif obligatoire.</b> {@code give|take|set} restent des alias.</li>
 * </ul>
 */
public final class RyoCommand implements TabExecutor {

    private final ShinobiCore plugin;
    private final MonnaieModule module;

    RyoCommand(ShinobiCore plugin, MonnaieModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    static boolean admin(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        return p.hasPermission("shinobicore.ryo.admin") || StaffGrades.of(p) >= StaffGrades.ADMIN;
    }

    private static void msg(CommandSender s, String t, NamedTextColor c) { s.sendMessage(Component.text(t, c)); }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] raw) {
        List<String> a = Arguments.lire(raw);
        if (a.isEmpty()) {
            if (!(s instanceof Player p)) { msg(s, "/ryo donner|retirer|fixer <cible> <montant> [bourse|compte] \"<motif>\"", NamedTextColor.YELLOW); return true; }
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            if (c == null) { msg(p, "Aucun personnage actif.", NamedTextColor.GRAY); return true; }
            msg(p, "Sur toi : " + Bourse.fmt(module.bourses().montant(p)) + " ryo · ton compte se consulte au guichet.", NamedTextColor.GOLD);
            return true;
        }
        String op = switch (a.get(0).toLowerCase(Locale.ROOT)) {
            case "donner", "give", "ajouter" -> "donner";
            case "retirer", "take", "enlever" -> "retirer";
            case "fixer", "set" -> "fixer";
            default -> null;
        };
        if (op == null) { msg(s, "Usage : /ryo donner|retirer|fixer <cible> <montant> [bourse|compte] \"<motif>\"", NamedTextColor.YELLOW); return true; }
        if (!admin(s)) { msg(s, "Réservé aux admins.", NamedTextColor.RED); return true; }
        if (a.size() < 3) { msg(s, "/ryo " + op + " <cible> <montant> [bourse|compte] \"<motif>\"", NamedTextColor.YELLOW); return true; }
        long n = MonnaieCanal.lire(a.get(2));
        if (n < 0) { msg(s, "Montant invalide.", NamedTextColor.RED); return true; }
        int i = 3;
        String ou = "compte";
        if (a.size() > 3 && (a.get(3).equalsIgnoreCase("bourse") || a.get(3).equalsIgnoreCase("compte"))) { ou = a.get(3).toLowerCase(Locale.ROOT); i = 4; }
        String motif = Arguments.reste(a, i);
        if (motif.isBlank()) { msg(s, "Le motif est obligatoire : /ryo " + op + " " + a.get(1) + " " + n + " " + ou + " \"<motif>\"", NamedTextColor.RED); return true; }
        CharacterService.ResolvedCharacter rc = PlayerIds.character(s, a.get(1));
        if (rc == null) { msg(s, "Personnage introuvable : " + a.get(1), NamedTextColor.RED); return true; }
        ShinobiCharacter c = rc.character();
        if (ou.equals("compte")) {
            Compte cp = module.banque().compte(c);
            long apres = module.banque().staffCompte(s, cp, op, n, motif);
            msg(s, PlayerIds.prefix(c) + c.name() + " · compte → " + Bourse.fmt(apres) + " ryo.", NamedTextColor.GREEN);
            return true;
        }
        Player p = Bukkit.getPlayer(rc.ownerId());
        ShinobiCharacter act = p == null ? null : plugin.characters().getActive(p.getUniqueId());
        if (act == null || !act.id().equals(c.id())) { msg(s, "La bourse ne se règle que sur un personnage joué en ligne (sinon : compte).", NamedTextColor.RED); return true; }
        long cur = module.bourses().montant(p);
        String m = "staff /ryo " + op + " · « " + motif + " »";
        boolean ok = switch (op) {
            case "donner" -> module.bourses().crediter(p, n, "STAFF", m, s);
            case "retirer" -> module.bourses().debiter(p, Math.min(n, cur), "STAFF", m, s);
            default -> n >= cur ? module.bourses().crediter(p, n - cur, "STAFF", m, s) : module.bourses().debiter(p, cur - n, "STAFF", m, s);
        };
        if (!ok) { msg(s, "Impossible (plus de place dans sa sacoche ?).", NamedTextColor.RED); return true; }
        msg(s, PlayerIds.prefix(c) + c.name() + " · bourse → " + Bourse.fmt(module.bourses().montant(p)) + " ryo.", NamedTextColor.GREEN);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String al, @NotNull String[] a) {
        List<String> r = new ArrayList<>();
        if (!admin(s)) return r;
        if (a.length == 1) r.addAll(List.of("donner", "retirer", "fixer"));
        else if (a.length == 2) for (Player p : Bukkit.getOnlinePlayers()) r.add(p.getName());
        else if (a.length == 3) r.addAll(List.of("100", "500", "1000"));
        else if (a.length == 4) r.addAll(List.of("compte", "bourse"));
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return r;
    }
}
