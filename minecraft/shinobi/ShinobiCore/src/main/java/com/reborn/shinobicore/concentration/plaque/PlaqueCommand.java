package com.reborn.shinobicore.concentration.plaque;

import com.reborn.shinobicore.staff.outils.Narrations;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /plaque} (alias {@code /stele}) : les actions de l'outil « Plaques » au tchat, pour un staff sans mod ou pressé
 * (lot 4, étape B). Grade relu dans le code (pas de permission de commande), comme {@code /cloche} ; mêmes règles et
 * même journal que le Poste de garde ({@link PlaqueService}).
 *
 * <pre>
 * /plaque poser [p:6] [+1h|+24h|+30m] &lt;texte…&gt;      vise un meuble (6 blocs) ou un bloc (8 blocs)
 * /plaque objet [p:6] [+…] &lt;texte…&gt;                 l'objet en main porte la plaque
 * /plaque texte &lt;id&gt; &lt;texte…&gt; · portee &lt;id&gt; &lt;2-16&gt; · deplacer &lt;id&gt; · retirer &lt;id&gt; &lt;motif…&gt;
 * /plaque restaurer &lt;id&gt; · liste [rayon] · corbeille · info &lt;id&gt; · voir &lt;id&gt; · tp &lt;id&gt;
 * </pre>
 * Texte : balises de la narration ({@code <rouge>…</>}, {@code <or>}, {@code <ombre>}, {@code <gris>}, {@code \n}). La
 * portée s'écrit {@code p:6} (ou {@code p=6}) : un nombre seul en tête du texte reste du texte (« 3 guerriers… »).
 *
 * <p>Droits : Helper et plus voient tout ({@code liste}, {@code info}, {@code voir}, {@code corbeille}, {@code tp}) ; un
 * animateur en mode animation sans grade staff ne voit et ne touche que <b>ses</b> plaques temporaires (ni corbeille, ni
 * téléport, ni le texte des autres).
 */
public final class PlaqueCommand implements TabExecutor {

    private static final List<String> SOUS = List.of("poser", "objet", "texte", "portee", "deplacer", "retirer", "restaurer",
            "liste", "corbeille", "info", "voir", "tp");
    private final PlaqueService svc;
    private final Runnable relire;

    public PlaqueCommand(PlaqueService svc, Runnable relire) {
        this.svc = svc;
        this.relire = relire;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("§cEn jeu seulement."); return true; }
        int grade = StaffGrades.of(p);
        boolean anim = svc.animation(p);
        if (!PlaqueRegles.peutVoir(grade, anim)) { p.sendMessage("§cRéservé au staff."); return true; }
        if (args.length == 0) { aide(p); return true; }
        String op = args[0].toLowerCase(Locale.ROOT);
        List<String> a = Arrays.asList(args).subList(1, args.length);
        boolean tout = PlaqueRegles.voirTout(grade);
        if (!tout && List.of("corbeille", "tp", "restaurer").contains(op)) { p.sendMessage("§cRéservé au staff (Helper et plus)."); return true; }
        if (!tout && List.of("info", "voir").contains(op)) {
            Plaque pl = a.isEmpty() ? null : svc.get(a.get(0));
            if (pl != null && !p.getUniqueId().equals(pl.creePar)) { p.sendMessage("§cEn mode animation : tes plaques seulement."); return true; }
        }
        switch (op) {
            case "poser", "objet" -> poser(p, op.equals("objet"), a);
            case "texte" -> {
                if (a.size() < 2) { err(p, "/plaque texte <id> <texte…>"); return true; }
                Narrations.Analyse t = Narrations.analyser(String.join(" ", a.subList(1, a.size())));
                if (!t.ok()) { err(p, t.erreur()); return true; }
                fin(p, svc.modifier(p, a.get(0), t.segments(), null, null, null, null), "texte enregistré");
            }
            case "portee", "portée" -> {
                if (a.size() < 2) { err(p, "/plaque portee <id> <2-16>"); return true; }
                try { fin(p, svc.modifier(p, a.get(0), null, null, Integer.parseInt(a.get(1)), null, null), "portée enregistrée"); }
                catch (NumberFormatException e) { err(p, "Portée : un nombre de 2 à 16."); }
            }
            case "deplacer", "déplacer" -> {
                if (a.isEmpty()) { err(p, "/plaque deplacer <id> (vise le nouvel endroit)"); return true; }
                Ancre an = svc.ancreVisee(p);
                if (an == null) { err(p, "Vise un bloc (8 blocs) ou un meuble (6 blocs)."); return true; }
                fin(p, svc.deplacer(p, a.get(0), an), "déplacée · " + an.libelle());
            }
            case "retirer", "supprimer" -> {
                if (a.size() < 2) { err(p, "/plaque retirer <id> <motif…>"); return true; }
                fin(p, svc.supprimer(p, a.get(0), String.join(" ", a.subList(1, a.size()))), "à la corbeille (30 jours)");
            }
            case "restaurer" -> {
                if (a.isEmpty()) { err(p, "/plaque restaurer <id>"); return true; }
                fin(p, svc.restaurer(p, a.get(0)), "restaurée");
            }
            case "liste" -> {
                int r = PlaqueRegles.LISTE_RAYON;
                if (!a.isEmpty()) try { r = Math.max(4, Math.min(PlaqueRegles.LISTE_RAYON_MAX, Integer.parseInt(a.get(0)))); } catch (NumberFormatException ignored) { }
                List<Plaque> l = svc.autour(p, r, "");
                if (l.isEmpty()) { p.sendMessage("§7Aucune plaque à " + r + " blocs."); return true; }
                for (Plaque pl : l) p.sendMessage("§e" + pl.id + " §7" + pl.ancre.libelle() + " · portée " + pl.portee
                        + (pl.titre.isEmpty() ? "" : " · §f" + pl.titre) + " §7· lue " + pl.lectures + " fois"
                        + (pl.temporaire ? " · §6temporaire" : "") + (pl.actif ? "" : " · §cinactive"));
            }
            case "corbeille" -> {
                List<Plaque> l = svc.corbeille();
                if (l.isEmpty()) { p.sendMessage("§7Corbeille vide."); return true; }
                for (Plaque pl : l) p.sendMessage("§e" + pl.id + " §7" + pl.supprimee.pseudo() + " · « " + pl.supprimee.motif() + " » §f"
                        + pl.aPlat(60));
            }
            case "info" -> {
                Plaque pl = a.isEmpty() ? null : svc.get(a.get(0));
                if (pl == null) { err(p, "Plaque introuvable."); return true; }
                p.sendMessage("§e" + pl.id + " §7" + pl.ancre.libelle() + " · portée " + pl.portee + " · rév. " + pl.rev
                        + " · lue " + pl.lectures + " fois · posée par " + pl.creePseudo
                        + (pl.modifPseudo.isEmpty() ? "" : " · modifiée par " + pl.modifPseudo)
                        + (pl.dansCorbeille() ? " · §ccorbeille (" + pl.supprimee.motif() + ")" : ""));
            }
            case "voir" -> {
                Plaque pl = a.isEmpty() ? null : svc.get(a.get(0));
                if (pl == null) { err(p, "Plaque introuvable."); return true; }
                p.sendMessage("§e" + pl.id + " §7· " + pl.balise());
            }
            case "tp" -> {
                if (a.isEmpty()) { err(p, "/plaque tp <id>"); return true; }
                PlaqueService.Resultat r = svc.teleporter(p, a.get(0));
                if (!r.ok()) err(p, r.erreur());
            }
            default -> aide(p);
        }
        return true;
    }

    private void poser(Player p, boolean objet, List<String> a) {
        // portée explicite seulement (« p:6 ») : « 3 guerriers reposent ici » garde son 3
        PlaqueRegles.ArgsPoser ap = PlaqueRegles.argsPoser(a);
        int portee = ap.portee(), expire = ap.expire(), i = ap.debut();
        if (i >= a.size()) { err(p, "/plaque " + (objet ? "objet" : "poser") + " [p:6] [+24h] <texte…>"); return; }
        Narrations.Analyse t = Narrations.analyser(String.join(" ", a.subList(i, a.size())));
        if (!t.ok()) { err(p, t.erreur()); return; }
        Ancre an = objet ? Ancre.objet() : svc.ancreVisee(p);
        if (an == null) { err(p, "Vise un bloc (8 blocs) ou un meuble (6 blocs)."); return; }
        PlaqueService.Resultat r = svc.creer(p, an, t.segments(), "", portee, expire);
        fin(p, r, r.ok() ? "posée · " + r.plaque().ancre.libelle() + " · portée " + r.plaque().portee : "");
    }

    private void fin(Player p, PlaqueService.Resultat r, String ok) {
        if (!r.ok()) { err(p, r.erreur()); return; }
        relire.run();
        p.sendMessage("§6[Plaques] §fPlaque §e" + r.plaque().id + "§f " + ok + ".");
    }

    private static void err(Player p, String m) { p.sendMessage("§c" + m); }

    private static void aide(Player p) {
        p.sendMessage("§6/plaque §fposer [p:6] [+24h] <texte> §7(vise un bloc ou un meuble ; portée 2 à 16) · §fobjet §7(objet en main)");
        p.sendMessage("§6/plaque §ftexte|portee|deplacer|retirer|restaurer|info|voir|tp <id> §7· §fliste [rayon] §7· §fcorbeille");
        p.sendMessage("§7Couleurs : <rouge>…</>, <or>, <ombre>, <gris> ; \\n = à la ligne. Poste de garde (F7) › Outils › Plaques.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String x : SOUS) if (x.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (args.length == 2 && !List.of("poser", "objet", "liste", "corbeille").contains(args[0].toLowerCase(Locale.ROOT))
                && s instanceof Player p) {
            boolean tout = PlaqueRegles.voirTout(StaffGrades.of(p));
            if (!tout && !svc.animation(p)) return out;
            for (Plaque pl : svc.plaques().values())
                if (pl.id.startsWith(args[1].toUpperCase(Locale.ROOT)) && (tout || p.getUniqueId().equals(pl.creePar))) out.add(pl.id);
        }
        return out;
    }
}
