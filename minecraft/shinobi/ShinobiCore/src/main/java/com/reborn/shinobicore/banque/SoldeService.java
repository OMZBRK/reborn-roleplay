package com.reborn.shinobicore.banque;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.entreprise.Entreprise;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.monnaie.Bourse;
import com.reborn.shinobicore.monnaie.MonnaieConfig;
import com.reborn.shinobicore.monnaie.MonnaieModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La solde hebdomadaire (MONNAIE_ET_BANQUE.md §7.2) : versée le <b>dimanche</b> ({@code banque.solde.jour},
 * {@code heure}) sur le <b>compte</b>, depuis le <b>trésor du village</b> (alimenté par le staff : {@code /banque tresor},
 * et plus tard par les missions), selon le <b>grade</b> (barème {@code banque.solde.bareme}) + une solde de fonction
 * pour les guichetiers (entreprise BANQUE). Condition : {@code presence-heures} de présence RP dans la semaine (comptée
 * ici, minute par minute, personnage actif hors mode staff). Si le trésor ne suffit pas : <b>au prorata</b>. Une seule
 * fois par compte et par semaine (idempotent).
 */
public final class SoldeService {

    private final ShinobiCore plugin;
    private final MonnaieModule module;

    public SoldeService(ShinobiCore plugin, MonnaieModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void start() { Bukkit.getScheduler().runTaskTimer(plugin, this::minute, 1200L, 1200L); }

    private BanqueService banque() { return module.banque(); }

    static ZonedDateTime maintenant() {
        return ZonedDateTime.now(java.time.ZoneId.of(MonnaieConfig.get().fuseau));
    }

    public static String semaine(ZonedDateTime t) {
        return t.get(IsoFields.WEEK_BASED_YEAR) + "-S" + String.format("%02d", t.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private void minute() {
        String sem = semaine(maintenant());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (com.reborn.shinobicore.staff.outils.ModeStaff.horsJeu(p)) continue;
            Compte a = banque().compte(p);
            if (a == null) continue;
            if (!sem.equals(a.presenceSemaine)) { a.presenceSemaine = sem; a.presenceMinutes = 0; }
            a.presenceMinutes++;
            banque().store.marquer();
        }
        ZonedDateTime t = maintenant();
        MonnaieConfig k = MonnaieConfig.get();
        if (t.getDayOfWeek() == k.soldeJour && t.getHour() >= k.soldeHeure && !sem.equals(banque().store.semainePayee)) {
            payer(null, null);
            banque().store.semainePayee = sem;
            banque().store.marquer();
        }
    }

    /** Montant dû à ce personnage (grade + fonction), sans condition de présence. */
    public long du(ShinobiCharacter c) {
        MonnaieConfig k = MonnaieConfig.get();
        long n = k.bareme.getOrDefault(c.rank(), 0L);
        var es = plugin.nourriture() == null ? null : plugin.nourriture().entreprises();
        if (es != null && es.membreDuType(c.id(), Entreprise.Type.BANQUE)) n += k.fonctionBanque;
        return n;
    }

    /** Verse la solde de la semaine (un village, ou tous) ; renvoie un résumé. Idempotent par compte. */
    public String payer(CommandSender staff, String village) {
        String sem = semaine(maintenant());
        MonnaieConfig k = MonnaieConfig.get();
        long presence = k.presenceHeures * 60L;
        Map<String, List<Object[]>> parVillage = new HashMap<>();
        for (Compte a : new ArrayList<>(banque().store.comptes.values())) {
            if (a.mortLe > 0 || a.gele() || a.village.isEmpty() || k.villagesRefuses.contains(a.village)) continue;
            if (village != null && !village.equals(a.village)) continue;
            if (sem.equals(a.soldeSemaine)) continue;
            if (!sem.equals(a.presenceSemaine) || a.presenceMinutes < presence) continue;
            ShinobiCharacter c = a.owner == null ? null : plugin.characters().findById(a.owner, a.perso).orElse(null);
            if (c == null || c.dead()) continue;
            long du = du(c);
            if (du <= 0) continue;
            parVillage.computeIfAbsent(a.village, v -> new ArrayList<>()).add(new Object[] {a, du});
        }
        StringBuilder res = new StringBuilder();
        for (var e : parVillage.entrySet()) {
            String v = e.getKey();
            long total = 0;
            for (Object[] x : e.getValue()) total += (long) x[1];
            long tresor = banque().tresor(v);
            double ratio = total <= 0 ? 0 : Math.min(1.0, tresor / (double) total);
            long verse = 0;
            for (Object[] x : e.getValue()) {
                Compte a = (Compte) x[0];
                long montant = (long) Math.floor((long) x[1] * ratio);
                a.soldeSemaine = sem;
                a.soldeMontant = montant;
                if (montant <= 0) continue;
                a.solde += montant;
                a.noter(montant, "solde de la semaine" + (ratio < 1 ? " (prorata " + Math.round(ratio * 100) + " %)" : ""));
                verse += montant;
                long op = banque().store.nouvelleOp("SOLDE", null, a.perso, montant, 0, "solde", sem);
                Journal.entree(Categorie.ECONOMIE, "SOLDE").ciblePersonnage(a.owner, a.perso)
                        .detail("+" + Bourse.fmt(montant) + " ryo · solde " + sem + (ratio < 1 ? " · prorata " + Math.round(ratio * 100) + " %" : "")
                                + " · op " + op).ecrire();
                Player p = a.owner == null ? null : Bukkit.getPlayer(a.owner);
                if (p != null) com.reborn.shinobicore.inventory.Notifs.msg(p, "info", null, "Solde de la semaine : +" + Bourse.fmt(montant) + " ryo",
                        "sur ton compte, au guichet");
            }
            banque().store.tresors.put(v, Math.max(0, tresor - verse));
            var j = Journal.entree(Categorie.ECONOMIE, "SOLDE");
            if (staff != null) j.acteur(staff);
            j.cibleTexte("trésor de " + v).detail(e.getValue().size() + " personnage(s) · " + Bourse.fmt(verse) + " ryo versés · dû "
                    + Bourse.fmt(total) + (ratio < 1 ? " · prorata " + Math.round(ratio * 100) + " % (trésor à sec)" : "")
                    + " · trésor " + Bourse.fmt(tresor) + " → " + Bourse.fmt(tresor - verse)).ecrire();
            res.append(v).append(" : ").append(e.getValue().size()).append(" perso(s), ").append(Bourse.fmt(verse)).append(" ryo")
                    .append(ratio < 1 ? " (prorata " + Math.round(ratio * 100) + " %)" : "").append(". ");
        }
        banque().store.marquer();
        return res.length() == 0 ? "Personne à payer (présence " + k.presenceHeures + " h, déjà payés, ou barème à 0)." : res.toString().trim();
    }
}
