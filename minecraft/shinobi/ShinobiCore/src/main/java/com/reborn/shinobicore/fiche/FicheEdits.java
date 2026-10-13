package com.reborn.shinobicore.fiche;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.ServiceRecordService.Type;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.stats.StatsChannel;
import com.reborn.shinobicore.stats.StatsServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sous-commandes de {@code /fiche} (alias de {@code /stats}) pour la fiche v2.
 *
 * <pre>
 * Joueur, sur son personnage actif :
 *   /fiche caractere &lt;trait&gt; &lt;trait&gt; &lt;trait&gt;     3 mots de 2 à 14 lettres
 *   /fiche citation [texte…]                     110 caractères au plus ; sans texte = effacée
 * Staff (shinobicore.fiche.staff) :
 *   /fiche edit &lt;joueur&gt; caractere &lt;t1&gt; &lt;t2&gt; &lt;t3&gt;
 *   /fiche edit &lt;joueur&gt; citation [texte…]
 *   /fiche edit &lt;joueur&gt; dojutsu &lt;stade&gt;
 *   /fiche service &lt;joueur&gt;                                   liste numérotée
 *   /fiche service &lt;joueur&gt; mission &lt;D|C|B|A|S&gt; [texte…] [--date jj/mm[/aaaa]]
 *   /fiche service &lt;joueur&gt; ajouter &lt;type&gt; &lt;texte…&gt; [--date …]
 *   /fiche service &lt;joueur&gt; masquer &lt;n&gt;
 *   /fiche json &lt;joueur&gt;                                      JSON envoyé au mod, en console
 * </pre>
 * Chaque modification est journalisée au Poste de garde ({@code staffPanel().log}).
 */
public final class FicheEdits {

    public static final String STAFF = "shinobicore.fiche.staff";
    public static final List<String> SUBS = List.of("caractere", "citation", "edit", "service", "json");
    public static final int CITATION_MAX = 110;

    /** Un trait : une lettre, puis lettres ou tirets ; 2 à 14 caractères. */
    private static final Pattern TRAIT = Pattern.compile("^\\p{L}[\\p{L}-]{1,13}$");

    private final ShinobiCore plugin;
    private final StatsChannel channel;

    public FicheEdits(ShinobiCore plugin, StatsChannel channel) {
        this.plugin = plugin;
        this.channel = channel;
    }

    /** Vrai si {@code sub} relève de la fiche v2 (et a été traité). */
    public boolean handle(CommandSender s, String sub, String[] args) {
        switch (sub) {
            case "caractere", "caractère" -> {
                if (!(s instanceof Player p)) { err(s, "Commande réservée aux joueurs."); return true; }
                ShinobiCharacter c = active(p);
                if (c != null) setTraits(s, c, tail(args, 1), false);
            }
            case "citation" -> {
                if (!(s instanceof Player p)) { err(s, "Commande réservée aux joueurs."); return true; }
                ShinobiCharacter c = active(p);
                if (c != null) setCitation(s, c, String.join(" ", tail(args, 1)), false);
            }
            case "edit" -> edit(s, args);
            case "service" -> service(s, args);
            case "json" -> json(s, args);
            default -> { return false; }
        }
        return true;
    }

    /* ---------------------------------------------------------- caractère */

    private void setTraits(CommandSender s, ShinobiCharacter c, String[] words, boolean staff) {
        if (words.length != 3) {
            err(s, "Il faut exactement 3 traits : /fiche caractere Calme Obstiné Loyal");
            return;
        }
        List<String> out = new ArrayList<>();
        for (String w : words) {
            String t = w.trim();
            if (!TRAIT.matcher(t).matches()) {
                err(s, "« " + t + " » : un trait est un seul mot de 2 à 14 lettres (accents et tiret permis).");
                return;
            }
            out.add(t.substring(0, 1).toUpperCase(Locale.FRENCH) + t.substring(1).toLowerCase(Locale.FRENCH));
        }
        c.setTraits(out);
        saveAndPush(c);
        ok(s, "Caractère de " + c.name() + " : " + String.join(", ", out) + ".");
        journal(s, staff ? "Caractère (staff)" : "Caractère", c.name() + " : " + String.join(", ", out));
    }

    private void setCitation(CommandSender s, ShinobiCharacter c, String raw, boolean staff) {
        String t = cleanCitation(raw);
        if (t.length() > CITATION_MAX) {
            err(s, "Citation trop longue (" + CITATION_MAX + " caractères au plus).");
            return;
        }
        c.setCitation(t);
        saveAndPush(c);
        if (t.isEmpty()) ok(s, "Citation de " + c.name() + " effacée.");
        else ok(s, "Citation de " + c.name() + " : « " + t + " »");
        journal(s, staff ? "Citation (staff)" : "Citation", c.name() + " : " + (t.isEmpty() ? "(effacée)" : t));
    }

    /** Sans guillemets ni codes couleur, espaces resserrés. */
    static String cleanCitation(String raw) {
        if (raw == null) return "";
        String t = raw.replace('§', ' ').replaceAll("\\s+", " ").trim();
        while (!t.isEmpty() && "«»\"“”'".indexOf(t.charAt(0)) >= 0) t = t.substring(1).trim();
        while (!t.isEmpty() && "«»\"“”".indexOf(t.charAt(t.length() - 1)) >= 0) t = t.substring(0, t.length() - 1).trim();
        return t;
    }

    /* --------------------------------------------------------------- edit */

    private void edit(CommandSender s, String[] args) {
        if (!staff(s)) return;
        if (args.length < 3) { err(s, "/fiche edit <joueur> caractere|citation|dojutsu <…>"); return; }
        ShinobiCharacter c = target(s, args[1]);
        if (c == null) return;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "caractere", "caractère" -> setTraits(s, c, tail(args, 3), true);
            case "citation" -> setCitation(s, c, String.join(" ", tail(args, 3)), true);
            case "dojutsu", "dōjutsu" -> {
                if (args.length < 4) { err(s, "/fiche edit <joueur> dojutsu <stade> (0 = dormant)"); return; }
                int st;
                try { st = Integer.parseInt(args[3]); } catch (NumberFormatException e) { err(s, "Stade invalide."); return; }
                if (st < 0 || st > 9) { err(s, "Stade entre 0 et 9."); return; }
                String doj = FichePhrases.dojutsuOf(c.clan());
                c.setDojutsuStage(st);
                saveAndPush(c);
                ok(s, c.name() + " : stade du dōjutsu " + st
                        + (doj == null ? " (attention : son clan n'a pas de dōjutsu connu)." : " (" + doj + ")."));
                journal(s, "Dōjutsu", c.name() + " : stade " + st);
            }
            default -> err(s, "/fiche edit <joueur> caractere|citation|dojutsu <…>");
        }
    }

    /* ------------------------------------------------------------ service */

    private void service(CommandSender s, String[] args) {
        if (!staff(s)) return;
        ServiceRecords sr = plugin.serviceRecords();
        if (sr == null) { err(s, "Journal des états de service indisponible."); return; }
        if (args.length < 2) { err(s, "/fiche service <joueur> [mission|ajouter|masquer …]"); return; }
        ShinobiCharacter c = target(s, args[1]);
        if (c == null) return;
        if (args.length == 2) { list(s, sr, c); return; }

        // --date jj/mm[/aaaa] n'importe où après l'action.
        List<String> rest = new ArrayList<>(Arrays.asList(tail(args, 3)));
        long at = System.currentTimeMillis();
        int di = rest.indexOf("--date");
        if (di >= 0) {
            if (di + 1 >= rest.size()) { err(s, "--date jj/mm ou jj/mm/aaaa"); return; }
            at = ServiceRecords.parseDate(rest.get(di + 1));
            if (at <= 0) { err(s, "Date illisible : " + rest.get(di + 1) + " (jj/mm ou jj/mm/aaaa)"); return; }
            rest.remove(di + 1);
            rest.remove(di);
        }
        String by = s.getName();
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "mission" -> {
                if (rest.isEmpty()) { err(s, "/fiche service <joueur> mission <D|C|B|A|S> [texte…] [--date jj/mm]"); return; }
                String rang = rest.remove(0).toUpperCase(Locale.ROOT);
                if (!List.of("D", "C", "B", "A", "S").contains(rang)) { err(s, "Rang de mission : D, C, B, A ou S."); return; }
                String texte = String.join(" ", rest).trim();
                sr.mission(c, rang, texte, by, at);
                pushIfOnline(c);
                ok(s, c.name() + " : mission de rang " + rang + " inscrite.");
                journal(s, "Mission", c.name() + " : rang " + rang + (texte.isEmpty() ? "" : " — " + texte));
            }
            case "ajouter", "add" -> {
                if (rest.size() < 2) { err(s, "/fiche service <joueur> ajouter <enregistrement|promotion|distinction|autre> <texte…> [--date …]"); return; }
                Type type = ServiceEntry.parseType(rest.remove(0));
                if (type == null) { err(s, "Type : enregistrement, promotion, retrogradation, mission, distinction ou autre."); return; }
                String texte = String.join(" ", rest).trim();
                sr.add(c, type, texte, null, by, at);
                pushIfOnline(c);
                ok(s, c.name() + " : « " + texte + " » inscrit aux états de service.");
                journal(s, "État de service", c.name() + " : " + type.name() + " — " + texte);
            }
            case "masquer", "hide" -> {
                if (rest.isEmpty()) { err(s, "/fiche service <joueur> masquer <n>"); return; }
                int n;
                try { n = Integer.parseInt(rest.get(0)); } catch (NumberFormatException e) { err(s, "Numéro invalide."); return; }
                if (!sr.hide(c, n, by)) { err(s, "Entrée n° " + n + " introuvable ou déjà masquée."); return; }
                pushIfOnline(c);
                ok(s, c.name() + " : entrée n° " + n + " masquée (gardée au journal).");
                journal(s, "État de service masqué", c.name() + " : n° " + n);
            }
            default -> err(s, "/fiche service <joueur> [mission|ajouter|masquer …]");
        }
    }

    private void list(CommandSender s, ServiceRecords sr, ShinobiCharacter c) {
        sr.recent(c, 0); // rattrapage d'un ancien personnage avant affichage
        List<ServiceEntry> all = sr.log(c).all();
        s.sendMessage(Component.text("━━ États de service de " + c.name() + " ━━", NamedTextColor.GOLD));
        for (int i = 0; i < all.size(); i++) {
            ServiceEntry e = all.get(i);
            String date = ServiceRecords.formatDate(e.at());
            String line = (i + 1) + ". " + (date.isEmpty() ? "(sans date)" : date) + " · " + e.type().name()
                    + (e.rang() != null ? " " + e.rang() : "") + " — " + e.text()
                    + (e.by() != null ? " (par " + e.by() + ")" : "")
                    + (e.hidden() ? " [masquée" + (e.hiddenBy() != null ? " par " + e.hiddenBy() : "") + "]" : "");
            s.sendMessage(Component.text(line, e.hidden() ? NamedTextColor.DARK_GRAY : NamedTextColor.GRAY));
        }
        if (all.isEmpty()) s.sendMessage(Component.text("(vide)", NamedTextColor.DARK_GRAY));
    }

    /* --------------------------------------------------------------- json */

    private void json(CommandSender s, String[] args) {
        if (!staff(s)) return;
        Player t = args.length >= 2 ? com.reborn.shinobicore.character.PlayerIds.online(s, args[1]) : (s instanceof Player p ? p : null);
        if (t == null) { err(s, "Joueur hors ligne."); return; }
        String js = channel.debugJson(t);
        if (js == null) { err(s, "Aucun personnage actif."); return; }
        plugin.getLogger().info("[fiche] JSON reborn:stats de " + t.getName() + " (" + js.length() + " car.) : " + js);
        ok(s, "JSON de " + t.getName() + " écrit dans la console (" + js.length() + " caractères).");
    }

    /* ------------------------------------------------------------ complétion */

    public List<String> complete(CommandSender s, String[] args) {
        List<String> out = new ArrayList<>();
        boolean staff = s.hasPermission(STAFF);
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && staff && List.of("edit", "service", "json").contains(sub)) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            if (!sub.equals("json")) out.addAll(plugin.characters().allCharacterNames());
        } else if (args.length == 3 && staff && sub.equals("edit")) {
            out.addAll(List.of("caractere", "citation", "dojutsu"));
        } else if (args.length == 3 && staff && sub.equals("service")) {
            out.addAll(List.of("mission", "ajouter", "masquer"));
        } else if (args.length == 4 && staff && sub.equals("service")) {
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "mission" -> out.addAll(List.of("D", "C", "B", "A", "S"));
                case "ajouter" -> out.addAll(List.of("enregistrement", "promotion", "retrogradation",
                        "mission", "distinction", "autre"));
                default -> { }
            }
        } else if (args.length == 4 && staff && sub.equals("edit") && args[2].equalsIgnoreCase("dojutsu")) {
            out.addAll(List.of("0", "1", "2", "3"));
        } else if (args.length >= 4 && staff && sub.equals("service") && !args[2].equalsIgnoreCase("masquer")) {
            out.add("--date");
        }
        return out;
    }

    /* ------------------------------------------------------------- outils */

    private ShinobiCharacter active(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) err(p, "Aucun personnage actif.");
        return c;
    }

    /** Joueur en ligne (son personnage actif), sinon nom de personnage. */
    private ShinobiCharacter target(CommandSender s, String name) {
        if (com.reborn.shinobicore.character.PlayerIds.isRef(name)) {                      // #N° (staff), même hors ligne
            CharacterService.ResolvedCharacter r = com.reborn.shinobicore.character.PlayerIds.character(s, name);
            if (r == null) { err(s, "Aucun personnage n°" + name.substring(1) + "."); return null; }
            return r.character();
        }
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) {
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            if (c != null) return c;
        }
        CharacterService.ResolvedCharacter r = plugin.characters().resolveOwned(name);
        if (r == null) { err(s, "Joueur ou personnage introuvable : " + name); return null; }
        return r.character();
    }

    private boolean staff(CommandSender s) {
        if (s.hasPermission(STAFF) || !(s instanceof Player)) return true;
        err(s, "Réservé au staff.");
        return false;
    }

    private void saveAndPush(ShinobiCharacter c) {
        plugin.characters().save(c);
        pushIfOnline(c);
    }

    private static void pushIfOnline(ShinobiCharacter c) {
        StatsServiceImpl.pushIfOnline(c);
    }

    private void journal(CommandSender s, String type, String detail) {
        plugin.getLogger().info("[fiche] " + s.getName() + " · " + type + " · " + detail);
        if (plugin.staffPanel() != null && s instanceof Player p) plugin.staffPanel().log(p, "fiche", type, detail);
    }

    private static String[] tail(String[] a, int from) {
        if (from >= a.length) return new String[0];
        return Arrays.copyOfRange(a, from, a.length);
    }

    private static void ok(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.GREEN)); }

    private static void err(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.RED)); }
}
