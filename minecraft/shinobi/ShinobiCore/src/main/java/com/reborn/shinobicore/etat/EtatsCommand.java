package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.etat.Controle;
import com.reborn.shinobicore.api.etat.Couche;
import com.reborn.shinobicore.api.etat.Etat;
import com.reborn.shinobicore.api.etat.Lien;
import com.reborn.shinobicore.api.etat.Modificateur;
import com.reborn.shinobicore.api.etat.Resultat;
import com.reborn.shinobicore.api.etat.Source;
import com.reborn.shinobicore.journal.Journal;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /etats} (staff, docs/technique/ETATS_JOUEUR.md §8) — {@code /etat} reste le ressenti du joueur.
 * <ul>
 *   <li>{@code /etats <joueur>} : état principal, couches, lien, modificateurs, lignes de la matrice, refus, vitesse ;</li>
 *   <li>{@code /etats poser <joueur> <id> [secondes] [intensité]} : un type du registre ({@code menottes},
 *       {@code hydratation}…) ou une valeur de contrôle ({@code etourdi}, {@code entrave}, {@code controle},
 *       {@code corps_vide}) — test et arbitrage, journal {@code STAFF} ;</li>
 *   <li>{@code /etats lever <joueur> <id|controle|prison|occupe>} ;</li>
 *   <li>{@code /etats prison <joueur> <minutes> [zone] [motif…]} ;</li>
 *   <li>{@code /etats recharger} : relit {@code etats.yml}.</li>
 * </ul>
 */
public final class EtatsCommand implements TabExecutor {

    private final ShinobiCore plugin;
    private final EtatServiceImpl service;

    EtatsCommand(ShinobiCore plugin, EtatServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        if (!s.hasPermission("shinobicore.staff")) { s.sendMessage("§cRéservé au staff."); return true; }
        if (a.length == 0) {
            s.sendMessage("§6/" + label + " <joueur> §7· poser <joueur> <id> [s] [n] · lever <joueur> <id|controle|prison|occupe>"
                    + " · prison <joueur> <minutes> [zone] [motif] · recharger");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "recharger", "reload" -> {
                service.charger();
                s.sendMessage("§aetats.yml relu : " + service.matrice().lignes().size() + " lignes"
                        + (service.erreurs().isEmpty() ? "." : ", §e" + service.erreurs().size() + " avertissement(s) (console)."));
            }
            case "poser" -> poser(s, a);
            case "lever" -> lever(s, a);
            case "prison" -> prison(s, a);
            default -> voir(s, a[0]);
        }
        return true;
    }

    private Player cible(CommandSender s, String nom) {
        Player p = Bukkit.getPlayerExact(nom);
        if (p == null) s.sendMessage("§cJoueur introuvable (en ligne) : " + nom);
        return p;
    }

    private void voir(CommandSender s, String nom) {
        Player p = cible(s, nom);
        if (p == null) return;
        Etat e = service.calculer(p.getUniqueId());
        s.sendMessage("§6États · §f" + p.getName() + " §8— §e" + e.principal().libelle());
        s.sendMessage("§7Vital §f" + e.vital() + " §8· §7Contrôle §f" + e.controle()
                + (e.lien() != null ? " §8(" + e.lien().type() + " " + e.lien().nom() + ")" : "")
                + " §8· §7Cadre §f" + e.cadre());
        StringBuilder m = new StringBuilder();
        for (Modificateur x : e.modificateurs()) {
            if (m.length() > 0) m.append("§8, ");
            m.append("§f").append(x.nom());
            if (x.resteMs() > 0) m.append(" §8").append(x.resteMs() / 1000).append(" s");
            if (!x.derive()) m.append(" §8[").append(x.source()).append("]");
        }
        s.sendMessage("§7Modificateurs : " + (m.length() == 0 ? "§8aucun" : m));
        s.sendMessage("§7Lignes : §8" + String.join(", ", service.lignesActives(p.getUniqueId())));
        StringBuilder r = new StringBuilder();
        for (Map.Entry<Capacite, String> x : e.capacites().refus().entrySet()) {
            if (r.length() > 0) r.append("§8 · ");
            r.append("§c").append(x.getKey().name().toLowerCase(Locale.ROOT)).append(" §7").append(x.getValue());
        }
        s.sendMessage("§7Refus : " + (r.length() == 0 ? "§aaucun" : r));
        if (!e.capacites().accordees().isEmpty()) {
            s.sendMessage("§7Accordées : §a" + String.join(", ", e.capacites().accordees().keySet().stream()
                    .map(c -> c.name().toLowerCase(Locale.ROOT)).toList()));
        }
        s.sendMessage(String.format(Locale.ROOT, "§7Vitesse ×%.2f · saut ×%.2f%s", e.capacites().vitesse(),
                e.capacites().saut(), e.capacites().faimTenue() ? " · faim tenue" : ""));
        var f = service.facteurs(p.getUniqueId());
        if (!f.isEmpty()) {
            List<String> l = new ArrayList<>();
            f.forEach((k, v) -> l.add(String.format(Locale.ROOT, "%s ×%.2f/×%.2f", k, v[0], v[1])));
            s.sendMessage("§7Facteurs : §f" + String.join(", ", l));
        }
        var pe = service.peine(p.getUniqueId());
        if (pe != null) s.sendMessage("§7Prison : §f" + (pe.resteMs() / 60_000 + 1) + " min" + (pe.zone() != null ? " · zone " + pe.zone() : "")
                + (pe.motif() != null ? " · " + pe.motif() : "") + " §8(par " + pe.par() + ")");
    }

    private void poser(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage("§c/etats poser <joueur> <id> [secondes] [intensité]"); return; }
        Player p = cible(s, a[1]);
        if (p == null) return;
        String id = a[2].toLowerCase(Locale.ROOT);
        long ms = a.length > 3 ? (long) (num(a[3], 0) * 1000) : 0;
        double n = a.length > 4 ? num(a[4], 1) : 1;
        Source src = Source.staff(s.getName(), s instanceof Player sp ? sp.getUniqueId() : null);
        String ctrl = switch (id) {
            case "etourdi", "étourdi" -> "ETOURDI";
            case "entrave", "entravé" -> "ENTRAVE";
            case "controle", "contrôle" -> "CONTROLE";
            case "corps_vide", "corpsvide" -> "CORPS_VIDE";
            default -> null;
        };
        String res;
        if (ctrl != null) {
            Lien lien = s instanceof Player sp && !sp.equals(p) ? new Lien("STAFF", sp.getUniqueId(), s.getName(), 0) : null;
            Resultat r = service.transition(p, Couche.CONTROLE, ctrl, src, lien, ms > 0 ? ms : 10_000L);
            res = ctrl + " → " + r;
        } else {
            boolean ok = service.registre() != null && service.registre().poser(p, id, n, ms, "staff " + s.getName());
            res = id + (ok ? " posé" : " refusé (type inconnu, plus faible, immunité ou pas de personnage)");
        }
        s.sendMessage("§7" + p.getName() + " : §f" + res);
        Journal.entree(Categorie.STAFF, "ETATS_POSER").acteur(s).cible(p).detail(res).ecrire();
    }

    private void lever(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage("§c/etats lever <joueur> <id|controle|prison|occupe>"); return; }
        Player p = cible(s, a[1]);
        if (p == null) return;
        String id = a[2].toLowerCase(Locale.ROOT);
        Source src = Source.staff(s.getName(), s instanceof Player sp ? sp.getUniqueId() : null);
        String res;
        switch (id) {
            case "controle", "contrôle" -> res = "contrôle → " + service.transition(p, Couche.CONTROLE, Controle.LIBRE.name(), src, null);
            case "prison" -> res = service.liberer(p.getUniqueId(), src) ? "libéré" : "pas en prison";
            case "occupe", "occupation" -> {
                service.occupations().interrompre(p.getUniqueId(), "Le staff a interrompu l'action.");
                res = "action interrompue";
            }
            default -> {
                if (service.registre() != null) service.registre().retirer(p, id, "staff " + s.getName());
                res = id + " retiré";
            }
        }
        s.sendMessage("§7" + p.getName() + " : §f" + res);
        Journal.entree(Categorie.STAFF, "ETATS_LEVER").acteur(s).cible(p).detail(res).ecrire();
    }

    private void prison(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage("§c/etats prison <joueur> <minutes> [zone] [motif…]"); return; }
        Player p = cible(s, a[1]);
        if (p == null) return;
        double min = num(a[2], 0);
        if (min <= 0) { s.sendMessage("§cDurée en minutes > 0."); return; }
        String zone = a.length > 3 && !a[3].equals("-") ? a[3].toLowerCase(Locale.ROOT) : null;
        String motif = a.length > 4 ? String.join(" ", java.util.Arrays.copyOfRange(a, 4, a.length)) : null;
        if (zone != null && (plugin.trainingZones() == null || plugin.trainingZones().get(zone) == null)) {
            s.sendMessage("§eZone « " + zone + " » inconnue : peine posée sans zone (crée-la avec /zonerp creer prison " + zone + ").");
            zone = null;
        }
        boolean ok = service.emprisonner(p, (long) (min * 60_000), zone, motif,
                Source.staff(s.getName(), s instanceof Player sp ? sp.getUniqueId() : null));
        s.sendMessage(ok ? "§a" + p.getName() + " en prison pour " + (long) min + " min." : "§cRefusé (pas de personnage, ou veto).");
        if (ok) {
            p.sendMessage(net.kyori.adventure.text.Component.text("Tu es en prison (" + (long) min + " min, temps réel).",
                    net.kyori.adventure.text.format.NamedTextColor.GOLD));
            Journal.entree(Categorie.STAFF, "ETATS_PRISON").acteur(s).cible(p)
                    .detail((long) min + " min" + (zone != null ? " · " + zone : "") + (motif != null ? " · " + motif : "")).ecrire();
        }
    }

    private static double num(String s, double def) {
        try { return Double.parseDouble(s.replace(',', '.')); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("shinobicore.staff")) return out;
        if (a.length == 1) {
            out.addAll(List.of("poser", "lever", "prison", "recharger"));
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("recharger")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("poser")) {
            out.addAll(List.of("etourdi", "entrave", "controle", "corps_vide"));
            if (service.registre() != null) for (var t : service.registre().types()) out.add(t.id());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("lever")) {
            out.addAll(List.of("controle", "prison", "occupe"));
            if (service.registre() != null) for (var t : service.registre().types()) out.add(t.id());
        } else if (a.length == 4 && a[0].equalsIgnoreCase("prison") && plugin.trainingZones() != null) {
            for (var z : plugin.trainingZones().all()) if (z.kind() == com.reborn.shinobicore.ko.zone.TrainingZone.Kind.PRISON) out.add(z.id());
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
