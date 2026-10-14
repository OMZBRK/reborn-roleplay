package com.reborn.shinobiradio;

import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /radio} — joueurs (menu R et écran N du mod, ou tapé) et staff (Poste de garde, ou tapé).
 *
 * <pre>
 * creer &lt;joueur&gt;            ajouter &lt;joueur&gt;          accepter | refuser
 * quitter                     renommer &lt;nom…&gt;            dissoudre confirmer
 * exclure &lt;membre&gt;           exclure-oreillette &lt;membre&gt;
 * oreillette couper|prendre &lt;joueur&gt;   (joueur à terre, à côté)
 * staff : info &lt;joueur&gt; (Helper) · ecouter &lt;joueur&gt; [minutes] &lt;motif…&gt; (Helper) · stop
 *         couper &lt;joueur&gt; [minutes] [motif…] (Modérateur) · retirer &lt;joueur&gt; [motif…] (Modérateur)
 *         donner &lt;joueur&gt; (Admin) · reparer [joueur] (Modérateur ou shinobiradio.artisan)
 * </pre>
 * {@code <joueur>} : pseudo, nom RP du personnage actif, ou {@code #N°} pour le staff. Tout est revérifié ici.
 */
final class RadioCommande implements CommandExecutor, TabCompleter {

    private static final List<String> JOUEUR = List.of("creer", "ajouter", "accepter", "refuser", "quitter", "renommer",
            "exclure", "exclure-oreillette", "dissoudre", "oreillette");
    private static final List<String> STAFF = List.of("info", "ecouter", "stop", "couper", "retirer", "donner", "reparer");

    private final Radio radio;
    private final RadioConfig cfg;

    RadioCommande(Radio radio, RadioConfig cfg) {
        this.radio = radio;
        this.cfg = cfg;
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (a.length == 0) { aide(s); return true; }
        String sc = a[0].toLowerCase(Locale.ROOT);
        Player p = s instanceof Player pl ? pl : null;
        int grade = p == null ? StaffGrades.OWNER : StaffGrades.of(p);
        switch (sc) {
            case "creer", "créer", "ajouter" -> {
                if (p == null || a.length < 2) return usage(s, "/radio " + sc + " <joueur>");
                Player b = Bukkit.getPlayerExact(a[1]);
                if (sc.equals("ajouter")) radio.ajouter(p, b); else radio.creer(p, b);
            }
            case "accepter" -> { if (p != null) radio.accepter(p); }
            case "refuser" -> { if (p != null) radio.refuser(p); }
            case "quitter" -> { if (p != null) radio.quitter(p); }
            case "renommer" -> {
                if (p == null || a.length < 2) return usage(s, "/radio renommer <nom>");
                radio.renommer(p, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            case "exclure" -> {
                if (p == null || a.length < 2) return usage(s, "/radio exclure <membre>");
                radio.exclure(p, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            case "exclure-oreillette" -> {
                if (p == null || a.length < 2) return usage(s, "/radio exclure-oreillette <membre>");
                radio.exclureOreillette(p, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            case "dissoudre" -> {
                if (p == null) return true;
                if (a.length < 2 || !a[1].equalsIgnoreCase("confirmer")) return usage(s, "/radio dissoudre confirmer");
                radio.dissoudre(p);
            }
            case "oreillette" -> {
                if (p == null || a.length < 3) return usage(s, "/radio oreillette <couper|prendre> <joueur>");
                Player b = Bukkit.getPlayerExact(a[2]);
                if (a[1].equalsIgnoreCase("couper")) radio.couperOreillette(p, b);
                else if (a[1].equalsIgnoreCase("prendre")) radio.prendreOreillette(p, b);
                else return usage(s, "/radio oreillette <couper|prendre> <joueur>");
            }
            case "info" -> {
                if (grade < StaffGrades.HELPER) return refus(s);
                if (a.length < 2) return usage(s, "/radio info <joueur|#N°>");
                Player c = cible(s, a[1]);
                if (c == null) return true;
                s.sendMessage(ChatColor.GOLD + "[Radio] " + ChatColor.WHITE + c.getName() + ChatColor.GRAY + "\n" + radio.info(c));
            }
            case "ecouter", "écouter" -> {
                if (p == null) return usage(s, "réservé à un staff en jeu");
                if (grade < StaffGrades.HELPER) return refus(s);
                if (a.length < 3) return usage(s, "/radio ecouter <joueur|#N°> [minutes] <motif>");
                Player c = cible(s, a[1]);
                if (c == null) return true;
                int minutes = cfg.ecouteDefaut;
                int debut = 2;
                Integer m = entier(a[2]);
                if (m != null) { minutes = m; debut = 3; }
                String motif = debut < a.length ? String.join(" ", Arrays.copyOfRange(a, debut, a.length)).trim() : "";
                if (motif.isEmpty()) return usage(s, "le motif est obligatoire : /radio ecouter <joueur> [minutes] <motif>");
                radio.ecouter(p, c, Math.max(1, Math.min(cfg.ecouteMax, minutes)), motif);
            }
            case "stop" -> {
                if (p == null) return true;
                if (grade < StaffGrades.HELPER) return refus(s);
                radio.stop(p);
            }
            case "couper" -> {
                if (grade < StaffGrades.MODO) return refus(s);
                if (a.length < 2) return usage(s, "/radio couper <joueur|#N°> [minutes] [motif]");
                Player c = cible(s, a[1]);
                if (c == null) return true;
                int minutes = cfg.coupureDefaut;
                int debut = 2;
                if (a.length > 2) {
                    Integer m = entier(a[2]);
                    if (m != null) { minutes = m; debut = 3; }
                }
                String motif = debut < a.length ? String.join(" ", Arrays.copyOfRange(a, debut, a.length)) : "";
                radio.couperStaff(s, c, Math.max(1, minutes), motif);
            }
            case "retirer" -> {
                if (grade < StaffGrades.MODO) return refus(s);
                if (a.length < 2) return usage(s, "/radio retirer <joueur|#N°> [motif]");
                Player c = cible(s, a[1]);
                if (c == null) return true;
                radio.retirerStaff(s, c, a.length > 2 ? String.join(" ", Arrays.copyOfRange(a, 2, a.length)) : "");
            }
            case "donner" -> {
                if (grade < StaffGrades.ADMIN) return refus(s);
                if (a.length < 2 && p == null) return usage(s, "/radio donner <joueur|#N°>");
                Player c = a.length < 2 ? p : cible(s, a[1]);
                if (c == null) return true;
                radio.donner(s, c);
            }
            case "reparer", "réparer" -> {
                boolean artisan = s.hasPermission("shinobiradio.artisan");
                if (grade < StaffGrades.MODO && !artisan) return refus(s);
                Player c = a.length < 2 ? p : cible(s, a[1]);
                if (c == null) return usage(s, "/radio reparer [joueur]");
                radio.reparer(s, c);
            }
            default -> aide(s);
        }
        return true;
    }

    private Player cible(CommandSender s, String arg) {
        Player c = PlayerIds.online(s, arg);
        if (c == null) s.sendMessage(ChatColor.RED + "[Radio] Joueur introuvable (en ligne) : " + arg);
        return c;
    }

    private static Integer entier(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static boolean usage(CommandSender s, String u) {
        s.sendMessage(ChatColor.GRAY + "[Radio] " + u);
        return true;
    }

    private static boolean refus(CommandSender s) {
        s.sendMessage(ChatColor.RED + "[Radio] Réservé au staff.");
        return true;
    }

    private void aide(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "[Radio] " + ChatColor.WHITE + "Escouade : menu R sur un joueur à côté (Radio), "
                + "écran N, B pour parler. " + ChatColor.GRAY + "/radio creer|ajouter <joueur>, accepter, refuser, quitter, "
                + "renommer <nom>, exclure <membre>, dissoudre confirmer.");
        if (s instanceof Player p && StaffGrades.of(p) >= StaffGrades.HELPER || !(s instanceof Player)) {
            s.sendMessage(ChatColor.GRAY + "Staff : /radio info <joueur>, ecouter <joueur> [minutes] <motif>, stop, "
                    + "couper <joueur> [minutes] [motif], retirer <joueur> [motif], donner <joueur>, reparer [joueur].");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        boolean staff = !(s instanceof Player p) || StaffGrades.of(p) >= StaffGrades.HELPER;
        if (a.length == 1) {
            for (String x : JOUEUR) if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
            if (staff) for (String x : STAFF) if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
            return out;
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("oreillette")) return List.of("couper", "prendre");
        if (a.length == 2 && a[0].equalsIgnoreCase("dissoudre")) return List.of("confirmer");
        if (a.length == 2 || (a.length == 3 && a[0].equalsIgnoreCase("oreillette"))) {
            String d = a[a.length - 1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(d)) out.add(p.getName());
        }
        if (a.length == 3 && (a[0].equalsIgnoreCase("ecouter") || a[0].equalsIgnoreCase("couper"))) return List.of("5", "10", "15", "30");
        return out;
    }
}
