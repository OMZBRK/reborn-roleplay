package com.reborn.shinobicore.mudra;

import com.reborn.shinobicore.technique.Mudra;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /porte} (alias {@code /portescellee}) : les portes scellées et l'interrupteur des mudras (demande du 14/11).
 * Permission {@code reborn.staff.portes} ; les droits réels sont relus dans le code par grade, comme {@code /plaque}.
 *
 * <pre>
 * /porte coin 1|2                      le bloc visé (8 blocs) devient un coin de la dalle
 * /porte creer &lt;nom&gt; &lt;signes…&gt;         la dalle entre les deux coins devient une porte (nom en un mot, _ = espace)
 * /porte signes &lt;id&gt; &lt;signes…&gt; · nom &lt;id&gt; &lt;nom…&gt; · leve &lt;id&gt; &lt;n&gt;
 * /porte liste · info [id] · tp &lt;id&gt;
 * /porte ouvrir &lt;id&gt; · fermer &lt;id&gt;    essai du staff, sans suite
 * /porte retirer &lt;id&gt;                  porte fermée seulement ; les blocs restent en place
 * /porte systeme on|off                allume ou éteint les mudras pour tout le serveur
 * </pre>
 * Signes : noms français ou anglais ({@code tigre}, {@code snake}…), ou nombres de 1 à 12 = place sur la barre.
 *
 * <p>Droits : liste et téléport, Helper et plus ; suite, création, retouches, retrait, ouverture, fermeture, Modérateur
 * et plus ; {@code systeme}, Admin et plus. <b>La suite n'est jamais écrite dans le tchat public</b> : elle ne paraît que
 * dans la réponse privée de {@code /porte info} à un Modérateur. Elle voyage pourtant dans les arguments de
 * {@code creer} et {@code signes} : la ligne de commande s'écrit au journal {@code STAFF} (lu des Helpers) avec la suite
 * masquée ({@link MudraRegles#masquerCommande}, appelée par {@code JournalEcouteur}).
 */
public final class PorteCommand implements TabExecutor {

    private static final List<String> SOUS = List.of("coin", "creer", "signes", "nom", "leve", "liste", "info", "tp", "ouvrir",
            "fermer", "retirer", "systeme");
    /** Sous-commandes dont le deuxième mot est un identifiant de porte. */
    private static final List<String> AVEC_ID = List.of("signes", "nom", "leve", "info", "tp", "ouvrir", "fermer", "retirer");
    private final PorteService svc;

    public PorteCommand(PorteService svc) {
        this.svc = svc;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        int grade = PorteService.grade(s);
        if (!MudraRegles.peutLister(grade)) { s.sendMessage("§cRéservé au staff."); return true; }
        if (args.length == 0) { aide(s, grade); return true; }
        String op = args[0].toLowerCase(Locale.ROOT);
        List<String> a = Arrays.asList(args).subList(1, args.length);
        Player p = s instanceof Player x ? x : null;
        switch (op) {
            case "coin" -> {
                if (p == null) { err(s, "En jeu seulement."); return true; }
                int n = a.isEmpty() ? 0 : a.get(0).equals("1") ? 1 : a.get(0).equals("2") ? 2 : 0;
                if (n == 0) { err(s, "/porte coin 1|2 (vise un bloc)"); return true; }
                String e = svc.coin(p, n);
                if (e != null) { err(s, e); return true; }
                MudraRegles.Boite b = svc.boiteDesCoins(p);
                if (b == null) { s.sendMessage("§6[Portes] §fCoin " + n + " posé. Pose l'autre : §e/porte coin " + (3 - n) + "§f."); return true; }
                String v = MudraRegles.validerBoite(b.dx(), b.dy(), b.dz());
                s.sendMessage("§6[Portes] §fCoin " + n + " posé · dalle de §e" + b.dx() + "×" + b.dy() + "×" + b.dz() + "§f ("
                        + b.volume() + " blocs)" + (v == null ? " · §e/porte creer <nom> <signes…>" : " · §c" + v));
            }
            case "creer", "créer" -> {
                if (p == null) { err(s, "En jeu seulement."); return true; }
                if (a.size() < 2) { err(s, "/porte creer <nom> <signes…> (nom en un mot, _ = espace ; signes : noms ou 1 à 12)"); return true; }
                MudraRegles.Suite su = MudraRegles.lireSuite(a.subList(1, a.size()));
                if (!su.ok()) { err(s, su.erreur()); return true; }
                PorteService.Resultat r = svc.creer(p, a.get(0), su.signes());
                fin(s, r, r.ok() ? "créée · dalle de " + r.porte().boite.dx() + "×" + r.porte().boite.dy() + "×" + r.porte().boite.dz()
                        + " · " + r.porte().signes.size() + " signes · levée " + r.porte().leve : "");
            }
            case "signes" -> {
                if (a.size() < 2) { err(s, "/porte signes <id> <signes…>"); return true; }
                MudraRegles.Suite su = MudraRegles.lireSuite(a.subList(1, a.size()));
                if (!su.ok()) { err(s, su.erreur()); return true; }
                fin(s, svc.signes(s, a.get(0), su.signes()), "suite enregistrée (" + su.signes().size() + " signes)");
            }
            case "nom" -> {
                if (a.size() < 2) { err(s, "/porte nom <id> <nom…>"); return true; }
                fin(s, svc.nom(s, a.get(0), String.join(" ", a.subList(1, a.size()))), "renommée");
            }
            case "leve", "levée", "levee" -> {
                if (a.size() < 2) { err(s, "/porte leve <id> <blocs>"); return true; }
                try { fin(s, svc.leve(s, a.get(0), Integer.parseInt(a.get(1))), "levée enregistrée"); }
                catch (NumberFormatException e) { err(s, "Levée : un nombre de blocs."); }
            }
            case "liste" -> {
                List<PorteScellee> l = svc.liste(p);
                s.sendMessage("§6[Portes] §fSystème " + (svc.actif() ? "§aallumé" : "§céteint") + "§f · " + l.size() + " porte(s).");
                for (PorteScellee d : l) s.sendMessage(ligne(d, p));
            }
            case "info" -> {
                PorteScellee d;
                if (a.isEmpty()) {
                    if (p == null) { err(s, "/porte info <id>"); return true; }
                    d = svc.plusProche(p, MudraRegles.INFO_RAYON, false);
                    if (d == null) { err(s, "Aucune porte à " + (int) MudraRegles.INFO_RAYON + " blocs."); return true; }
                } else {
                    d = svc.get(a.get(0));
                    if (d == null) { err(s, "Porte introuvable : " + a.get(0) + "."); return true; }
                }
                s.sendMessage(ligne(d, p));
                s.sendMessage("§7" + d.boite.libelle(d.monde) + " · levée " + d.leve + " · posée par " + d.creePseudo
                        + (d.modifPseudo.isEmpty() ? "" : " · modifiée par " + d.modifPseudo));
                // la suite : au Modérateur seulement, et à lui seul
                if (MudraRegles.peutGerer(grade)) s.sendMessage("§7Suite (" + d.signes.size() + ") : §f" + MudraRegles.libelle(d.signes));
            }
            case "tp" -> {
                if (p == null) { err(s, "En jeu seulement."); return true; }
                if (a.isEmpty()) { err(s, "/porte tp <id>"); return true; }
                PorteService.Resultat r = svc.teleporter(p, a.get(0));
                if (!r.ok()) err(s, r.erreur());
            }
            case "ouvrir" -> {
                if (a.isEmpty()) { err(s, "/porte ouvrir <id>"); return true; }
                fin(s, svc.essaiOuvrir(s, a.get(0)), "se lève");
            }
            case "fermer" -> {
                if (a.isEmpty()) { err(s, "/porte fermer <id>"); return true; }
                fin(s, svc.essaiFermer(s, a.get(0)), "se referme");
            }
            case "retirer", "supprimer" -> {
                if (a.isEmpty()) { err(s, "/porte retirer <id>"); return true; }
                fin(s, svc.retirer(s, a.get(0)), "retirée (les blocs restent en place)");
            }
            case "systeme", "système" -> {
                String v = a.isEmpty() ? "" : a.get(0).toLowerCase(Locale.ROOT);
                if (!v.equals("on") && !v.equals("off")) {
                    s.sendMessage("§6[Portes] §fSystème " + (svc.actif() ? "§aallumé" : "§céteint") + "§f · §e/porte systeme on|off");
                    return true;
                }
                String e = svc.systeme(s, v.equals("on"));
                if (e != null) { err(s, e); return true; }
                s.sendMessage("§6[Portes] §fMudras " + (v.equals("on") ? "§aallumés" : "§céteints") + "§f pour tout le serveur.");
            }
            default -> aide(s, grade);
        }
        return true;
    }

    /** Une ligne de la liste : jamais la suite. */
    private String ligne(PorteScellee d, Player p) {
        String loin = "";
        if (p != null) {
            double dist = PorteService.distance(d, p);
            loin = Double.isInfinite(dist) ? " · " + d.monde : " · à " + (int) Math.round(dist) + " blocs";
        }
        return "§e" + d.id + " §f" + d.nom + " §7· " + svc.etat(d) + loin + " · " + d.signes.size() + " signes";
    }

    private static void fin(CommandSender s, PorteService.Resultat r, String ok) {
        if (!r.ok()) { err(s, r.erreur()); return; }
        s.sendMessage("§6[Portes] §fPorte §e" + r.porte().id + "§f " + ok + ".");
    }

    private static void err(CommandSender s, String m) { s.sendMessage("§c" + m); }

    private static void aide(CommandSender s, int grade) {
        s.sendMessage("§6/porte §fliste §7· §finfo [id] §7(sans id : la plus proche à 16 blocs) · §ftp <id>");
        if (!MudraRegles.peutGerer(grade)) return;
        s.sendMessage("§6/porte §fcoin 1|2 §7(vise un bloc) · §fcreer <nom> <signes…> §7(nom en un mot, _ = espace)");
        s.sendMessage("§6/porte §fsignes <id> <signes…> §7· §fnom <id> <nom…> §7· §fleve <id> <blocs> §7· §fouvrir|fermer|retirer <id>");
        s.sendMessage("§7Signes : noms (tigre, serpent, dog…) ou places sur la barre, de 1 à 12. Dalle : 9 blocs par côté, 3 d'épaisseur, 162 blocs au plus.");
        if (MudraRegles.peutSysteme(grade)) s.sendMessage("§6/porte §fsysteme on|off §7(allume ou éteint les mudras pour tout le serveur)");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        int grade = PorteService.grade(s);
        if (!MudraRegles.peutLister(grade) || args.length == 0) return out;
        String op = args[0].toLowerCase(Locale.ROOT);
        String dernier = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String x : SOUS) {
                boolean permis = switch (x) {
                    case "liste", "info", "tp" -> true;
                    case "systeme" -> MudraRegles.peutSysteme(grade);
                    default -> MudraRegles.peutGerer(grade);
                };
                if (permis && x.startsWith(dernier)) out.add(x);
            }
        } else if (args.length == 2 && op.equals("coin")) {
            for (String x : List.of("1", "2")) if (x.startsWith(dernier)) out.add(x);
        } else if (args.length == 2 && op.equals("systeme")) {
            for (String x : List.of("on", "off")) if (x.startsWith(dernier)) out.add(x);
        } else if (args.length == 2 && AVEC_ID.contains(op)) {
            for (PorteScellee d : svc.portes().values()) if (d.id.toLowerCase(Locale.ROOT).startsWith(dernier)) out.add(d.id);
        } else if (MudraRegles.peutGerer(grade) && (op.equals("signes") && args.length >= 3 || op.equals("creer") && args.length >= 3)) {
            // les noms des mudras : la saisie ne révèle rien, ce sont les douze signes de la barre
            for (Mudra m : Mudra.values()) {
                String nom = m.display().toLowerCase(Locale.ROOT);
                if (nom.startsWith(dernier)) out.add(nom);
            }
        }
        return out;
    }
}
