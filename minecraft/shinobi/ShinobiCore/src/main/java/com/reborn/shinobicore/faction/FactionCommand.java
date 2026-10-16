package com.reborn.shinobicore.faction;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.faction.FactionData.Faction;
import com.reborn.shinobicore.faction.FactionData.Membre;
import com.reborn.shinobicore.faction.FactionData.Section;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /faction} — le volet J en secours et les gestes hors volet :
 * <ul>
 *   <li>joueur : {@code /faction} (ouvre le volet), {@code info}, {@code inviter <joueur>} (face à face, 3 blocs ;
 *       c'est ce qu'envoie le menu R), {@code accepter}, {@code refuser}, {@code quitter},
 *       {@code annoncer [section] <texte>}, au guichet : {@code verser <montant> [motif]}, {@code prime <#N°|nom> <montant> <motif>},
 *       {@code doter <section> <montant> [motif]} ;</li>
 *   <li>staff ({@code /faction admin …}, grades du Poste de garde) : {@code liste}, {@code info <tag>},
 *       {@code creer <tag> <village|organisation> <motif…> nom=<nom>} (Admin), {@code chef <tag> <#N°> <motif…>} (Modo),
 *       {@code section <tag> <modèle|libre> <#N° chef> <motif…> nom=<nom>} (Modo), {@code cacher <section> <oui|non> <motif…>},
 *       {@code dissoudre <section> <motif…>} (Admin), {@code caisse <section> <ajouter|retirer|fixer> <n> <motif…>} (Admin).
 *       Toute action staff est motivée et journalisée (catégorie FACTION).</li>
 * </ul>
 */
final class FactionCommand implements TabExecutor {

    private final ShinobiCore plugin;
    private final FactionService svc;

    FactionCommand(ShinobiCore plugin, FactionService svc) {
        this.plugin = plugin;
        this.svc = svc;
    }

    private static String reste(String[] a, int from) {
        return from >= a.length ? "" : String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    private static long montant(String s) {
        try { return Long.parseLong(s.replace(" ", "").replace("_", "")); } catch (NumberFormatException e) { return -1; }
    }

    private static void rep(CommandSender s, String r, String ok) {
        if (r != null) s.sendMessage("§c[Faction] §f" + r);
        else if (ok != null) s.sendMessage("§6[Faction] §f" + ok);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "ouvrir" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) return admin(s, a);
        if (!(s instanceof Player p)) { s.sendMessage("En jeu seulement (ou /faction admin)."); return true; }
        switch (sub) {
            case "ouvrir", "open" -> svc.canal().ouvrir(p);
            case "info" -> info(p);
            case "inviter" -> {
                Player q = a.length > 1 ? cible(p, a[1]) : null;
                rep(p, q == null ? "Personne de ce nom à côté de toi." : svc.inviter(p, q, false), q == null ? null : "Invitation envoyée (10 s).");
            }
            case "accepter" -> rep(p, svc.repondre(p, true), null);
            case "refuser" -> rep(p, svc.repondre(p, false), "Invitation refusée.");
            case "quitter" -> rep(p, svc.quitter(p), "Tu as quitté ta section.");
            case "annoncer" -> {
                boolean sec = a.length > 1 && a[1].equalsIgnoreCase("section");
                rep(p, svc.annoncer(p, sec, reste(a, sec ? 2 : 1)), "Annonce publiée.");
            }
            case "verser" -> rep(p, a.length < 2 ? "/faction verser <montant> [motif]" : svc.verser(p, montant(a[1]), reste(a, 2)), "Versé à la caisse de ta section.");
            case "prime" -> rep(p, a.length < 4 ? "/faction prime <#N°|nom> <montant> <motif>"
                    : svc.prime(p, svc.resoudre(a[1]), montant(a[2]), reste(a, 3)), "Prime versée.");
            case "doter" -> rep(p, a.length < 3 ? "/faction doter <section> <montant> [motif]"
                    : svc.doter(p, sectionPar(p, a[1]), montant(a[2]), reste(a, 3)), "Section dotée.");
            default -> p.sendMessage("§6[Faction] §f/faction [info|inviter|accepter|refuser|quitter|annoncer|verser|prime|doter]");
        }
        return true;
    }

    /** Joueur à côté (pseudo Minecraft, ou nom RP connu). */
    private Player cible(Player p, String nom) {
        Player q = Bukkit.getPlayerExact(nom);
        if (q != null) return q;
        Membre m = svc.resoudre(nom);
        return svc.enLigne(m);
    }

    private Section sectionPar(Player p, String ref) {
        Membre m = svc.membre(p);
        Faction f = m == null ? null : svc.faction(m.faction);
        if (f == null) return null;
        Section s = svc.section(ref);
        if (s != null && s.faction.equals(f.tag)) return s;
        for (Section x : svc.sections(f)) if (FactionService.cle(x.nom).equals(FactionService.cle(ref))) return x;
        return null;
    }

    private void info(Player p) {
        Membre m = svc.membre(p);
        Faction f = m == null ? null : svc.faction(m.faction);
        if (f == null) { p.sendMessage("§6[Faction] §fAucune faction."); return; }
        Section s = svc.section(m.section);
        p.sendMessage("§6[Faction] §f" + f.nom + " §7[" + f.tag + "] §f· " + m.rang.displayName()
                + (s == null ? " · sans section" : " · " + s.nom + " (" + svc.effectif(s) + "/" + s.places + ")"));
    }

    /* ================================================================ staff */

    private boolean admin(CommandSender s, String[] a) {
        int g = s instanceof Player p ? StaffGrades.of(p) : StaffGrades.OWNER;
        if (g < StaffGrades.HELPER) { s.sendMessage("§cRéservé au staff."); return true; }
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "liste";
        // « nom=… » : nom à espaces en fin de commande
        String nom = "";
        List<String> args = new ArrayList<>();
        for (int i = 2; i < a.length; i++) {
            if (a[i].startsWith("nom=")) { nom = (a[i].substring(4) + " " + reste(a, i + 1)).trim(); break; }
            args.add(a[i]);
        }
        switch (sub) {
            case "liste" -> {
                for (Faction f : svc.factions()) {
                    Membre ch = svc.membre(f.chef);
                    s.sendMessage("§6" + f.tag + " §f" + f.nom + " §7· " + (f.village() ? "village" : "organisation") + " · "
                            + svc.membres(f).size() + " membres · chef : " + (ch == null ? "à nommer" : ch.nom) + " · " + svc.sections(f).size() + " section(s)");
                }
            }
            case "info" -> {
                Faction f = args.isEmpty() ? null : svc.faction(args.get(0));
                if (f == null) { s.sendMessage("§c/faction admin info <tag>"); return true; }
                for (Section x : svc.sections(f)) {
                    Membre ch = svc.membre(x.chef);
                    s.sendMessage("§e" + x.id + " §f" + x.nom + (x.cachee ? " §8(cachée)" : "") + " §7· chef " + (ch == null ? "?" : ch.nom)
                            + " · " + svc.effectif(x) + "/" + x.places + " · caisse " + x.caisse);
                }
            }
            case "creer" -> {
                if (g < StaffGrades.ADMIN) { s.sendMessage("§cAdmin et plus."); return true; }
                if (args.size() < 3 || nom.isEmpty()) { s.sendMessage("§c/faction admin creer <tag> <village|organisation> <motif…> nom=<nom>"); return true; }
                rep(s, svc.creerFaction(s, args.get(0), nom, args.get(1).startsWith("orga"), args.get(1).startsWith("orga") ? "lune" : "",
                        null, String.join(" ", args.subList(2, args.size()))), "Faction créée.");
            }
            case "chef" -> {
                if (g < StaffGrades.MODO) { s.sendMessage("§cModérateur et plus."); return true; }
                if (args.size() < 3) { s.sendMessage("§c/faction admin chef <tag> <#N°|aucun> <motif…>"); return true; }
                Membre m = args.get(1).equalsIgnoreCase("aucun") ? null : svc.resoudre(args.get(1));
                if (m == null && !args.get(1).equalsIgnoreCase("aucun")) { s.sendMessage("§cPersonnage introuvable."); return true; }
                rep(s, svc.nommerChefFaction(s, svc.faction(args.get(0)), m == null ? null : m.perso, String.join(" ", args.subList(2, args.size()))), "Chef nommé.");
            }
            case "section" -> {
                if (g < StaffGrades.MODO) { s.sendMessage("§cModérateur et plus."); return true; }
                if (args.size() < 4) { s.sendMessage("§c/faction admin section <tag> <anbu|police|medical|academie|missions|garde|libre> <#N° chef> <motif…> [nom=<nom>]"); return true; }
                Membre ch = svc.resoudre(args.get(2));
                Droits.Modele mo = Droits.modele(args.get(1));
                rep(s, ch == null ? "Chef introuvable." : svc.creerSection(s, null, svc.faction(args.get(0)), args.get(1),
                        nom.isEmpty() && mo != null ? mo.nom() : nom, null, ch.perso, 0, mo != null && mo.cachee(), true), "Section créée.");
            }
            case "cacher" -> {
                if (g < StaffGrades.MODO) { s.sendMessage("§cModérateur et plus."); return true; }
                if (args.size() < 3) { s.sendMessage("§c/faction admin cacher <section> <oui|non> <motif…>"); return true; }
                rep(s, svc.cacher(s, null, svc.section(args.get(0)), args.get(1).startsWith("o"), true), "Fait.");
            }
            case "dissoudre" -> {
                if (g < StaffGrades.ADMIN) { s.sendMessage("§cAdmin et plus."); return true; }
                if (args.size() < 2) { s.sendMessage("§c/faction admin dissoudre <section> <motif…>"); return true; }
                rep(s, svc.dissoudre(s, null, svc.section(args.get(0)), String.join(" ", args.subList(1, args.size())), true), "Section dissoute.");
            }
            case "caisse" -> {
                if (g < StaffGrades.ADMIN) { s.sendMessage("§cAdmin et plus."); return true; }
                if (args.size() < 4) { s.sendMessage("§c/faction admin caisse <section> <ajouter|retirer|fixer> <n> <motif…>"); return true; }
                rep(s, svc.caisseStaff(s, svc.section(args.get(0)), args.get(1), montant(args.get(2)), String.join(" ", args.subList(3, args.size()))), "Caisse corrigée.");
            }
            default -> s.sendMessage("§6/faction admin §f[liste|info|creer|chef|section|cacher|dissoudre|caisse]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : new String[]{"info", "inviter", "accepter", "refuser", "quitter", "annoncer", "verser", "prime", "doter", "admin"})
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin")) {
            for (String x : new String[]{"liste", "info", "creer", "chef", "section", "cacher", "dissoudre", "caisse"})
                if (x.startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("inviter")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }
}
