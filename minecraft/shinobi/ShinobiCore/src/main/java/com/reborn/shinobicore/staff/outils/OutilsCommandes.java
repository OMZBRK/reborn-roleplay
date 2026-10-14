package com.reborn.shinobicore.staff.outils;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.emote.EmoteManager;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.journal.JournalServiceImpl;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Commandes des outils staff (PLUGINS_REFERENCE §2.1 et §4 bis.6). Cible : pseudo, nom RP ou {@code #N°}.
 * Chaque exécution est journalisée deux fois : la commande tapée ({@code STAFF COMMANDE}, par le journal des
 * commandes) et son effet ({@code STAFF <ACTION>}).
 */
public final class OutilsCommandes implements TabExecutor {

    static final String[] COMMANDES = {"journal", "note", "annonce", "annoncejoueur", "aller", "ramener", "retour",
            "geler", "degeler", "voirsac", "noir", "seisme", "tenue", "emoteforcee", "persoreset", "persosuppr",
            "modestaff"};

    private final ShinobiCore plugin;
    private final OutilsStaff o;

    OutilsCommandes(ShinobiCore plugin, OutilsStaff outils) {
        this.plugin = plugin;
        this.o = outils;
    }

    private static void err(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.RED)); }
    private static void ok(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.GREEN)); }
    private static void info(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.GRAY)); }

    private Player enLigne(CommandSender s, String ref) {
        Player p = PlayerIds.online(s, ref);
        if (p == null) err(s, "Joueur introuvable ou hors ligne : " + ref);
        return p;
    }

    private static String reste(String[] a, int from) {
        return from >= a.length ? "" : String.join(" ", Arrays.copyOfRange(a, from, a.length)).trim();
    }

    private static String nom(Player p) {
        var c = ShinobiCore.get().characters().getActive(p.getUniqueId());
        return c != null ? PlayerIds.prefix(c) + c.name() + " (" + p.getName() + ")" : p.getName();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        if (OutilsStaff.grade(s) == StaffGrades.NONE) { err(s, "Réservé au staff."); return true; }
        switch (c) {
            case "journal" -> journal(s, a);
            case "note" -> note(s, a);
            case "annonce" -> {
                if (!OutilsStaff.exige(s, OutilsStaff.G_ANNONCE)) return true;
                if (a.length == 0) { info(s, "/annonce <message>  ·  /annonce joueur <cible> <message>"); return true; }
                if (a[0].equalsIgnoreCase("joueur") && a.length >= 3) { annoncePerso(s, a[1], reste(a, 2)); return true; }
                String m = reste(a, 0);
                o.effets().annonceGlobale(s, null, m, 8);
                ok(s, "Annonce envoyée à tous.");
            }
            case "annoncejoueur" -> {
                if (a.length < 2) { info(s, "/annoncejoueur <cible> <message>"); return true; }
                annoncePerso(s, a[0], reste(a, 1));
            }
            case "aller" -> {
                if (!(s instanceof Player sp)) { err(s, "En jeu seulement."); return true; }
                if (!OutilsStaff.exige(s, OutilsStaff.G_ALLER) || a.length < 1) { if (a.length < 1) info(s, "/aller <cible>"); return true; }
                Player t = enLigne(s, a[0]);
                if (t == null) return true;
                o.aller(sp, t);
                ok(s, "Téléporté vers " + nom(t) + ". /retour pour revenir.");
            }
            case "ramener" -> {
                if (!(s instanceof Player sp)) { err(s, "En jeu seulement."); return true; }
                if (!OutilsStaff.exige(s, OutilsStaff.G_RAMENER)) return true;
                if (a.length < 1) { info(s, "/ramener <cible>"); return true; }
                Player t = enLigne(s, a[0]);
                if (t == null) return true;
                o.ramener(sp, t);
                ok(s, nom(t) + " ramené. /retour " + a[0] + " pour le renvoyer.");
            }
            case "retour" -> {
                if (a.length == 0) {
                    if (!(s instanceof Player sp)) { err(s, "/retour <cible>"); return true; }
                    if (!o.retour(sp)) err(s, "Aucune position de retour.");
                    else ok(s, "Retour effectué.");
                    return true;
                }
                if (!OutilsStaff.exige(s, OutilsStaff.G_RAMENER)) return true;
                Player t = enLigne(s, a[0]);
                if (t == null) return true;
                if (!o.renvoyer(s, t)) err(s, nom(t) + " n'a pas été ramené.");
                else ok(s, nom(t) + " renvoyé à sa position.");
            }
            case "geler" -> {
                if (!OutilsStaff.exige(s, OutilsStaff.G_GEL)) return true;
                if (a.length < 1) { info(s, "/geler <cible> [motif]"); return true; }
                Player t = enLigne(s, a[0]);
                if (t == null) return true;
                boolean neuf = o.gels().geler(s, t.getUniqueId(), t.getName(), reste(a, 1));
                ok(s, nom(t) + (neuf ? " est gelé (persistant). /degeler pour libérer." : " : motif du gel mis à jour."));
            }
            case "degeler" -> {
                if (!OutilsStaff.exige(s, OutilsStaff.G_GEL)) return true;
                if (a.length < 1) { info(s, "/degeler <cible>"); return true; }
                java.util.UUID id = compte(s, a[0]);
                if (id == null) { err(s, "Introuvable : " + a[0]); return true; }
                if (!o.gels().degeler(s, id)) err(s, a[0] + " n'est pas gelé.");
                else ok(s, a[0] + " est dégelé.");
            }
            case "voirsac" -> {
                if (!(s instanceof Player sp)) { err(s, "En jeu seulement."); return true; }
                if (!OutilsStaff.exige(s, OutilsStaff.G_VOIR_SAC)) return true;
                if (a.length < 1) { info(s, "/voirsac <cible>"); return true; }
                Player t = enLigne(s, a[0]);
                if (t == null) return true;
                o.voirSac().ouvrir(sp, t);
            }
            case "noir" -> noir(s, a);
            case "seisme" -> seisme(s, a);
            case "tenue" -> {
                if (!OutilsStaff.exige(s, OutilsStaff.G_TENUE)) return true;
                if (a.length < 2) { info(s, "/tenue <cible> <id Nexo|main|retirer>"); return true; }
                Player t = enLigne(s, a[0]);
                if (t == null) return true;
                if (a[1].equalsIgnoreCase("retirer") || a[1].equalsIgnoreCase("off")) {
                    if (o.tenues().lever(s, t)) ok(s, "Tenue rendue à " + nom(t) + ".");
                    else err(s, nom(t) + " n'a pas de tenue imposée.");
                    return true;
                }
                ItemStack it = o.tenues().fabriquer(s, a[1]);
                if (it == null) { err(s, "Tenue introuvable : " + a[1] + " (id Nexo, ou « main » pour l'objet en main)."); return true; }
                if (o.tenues().imposer(s, t, it, a[1])) ok(s, "Tenue imposée à " + nom(t) + ". /tenue " + a[0] + " retirer pour la rendre.");
                else err(s, nom(t) + " n'a pas de personnage actif.");
            }
            case "emoteforcee" -> {
                if (!OutilsStaff.exige(s, OutilsStaff.G_EMOTE)) return true;
                if (a.length < 2) { info(s, "/emoteforcee <cible> <emote|stop>"); return true; }
                Player t = enLigne(s, a[0]);
                if (t == null) return true;
                emote(s, t, a[1]);
            }
            case "persoreset", "persosuppr" -> {
                if (!OutilsStaff.exige(s, OutilsStaff.G_PERSO)) return true;
                if (a.length < 1) { info(s, "/" + c + " <cible|#N°> [confirmer]"); return true; }
                o.perso().demander(s, c.equals("persoreset") ? "reset" : "suppr", a[0],
                        a.length >= 2 && a[1].equalsIgnoreCase("confirmer"));
            }
            case "modestaff" -> {
                if (!(s instanceof Player sp)) { err(s, "En jeu seulement."); return true; }
                if (!OutilsStaff.exige(s, OutilsStaff.G_MODE_STAFF)) return true;
                boolean on = o.mode().basculer(sp);
                ok(s, on ? "Mode staff activé : invisible aux joueurs, aucune action de jeu." : "Mode staff désactivé.");
            }
            default -> { return false; }
        }
        return true;
    }

    /** Compte visé, en ligne ou non (pseudo, nom RP, #N°). */
    private java.util.UUID compte(CommandSender s, String ref) {
        Player p = PlayerIds.online(s, ref);
        if (p != null) return p.getUniqueId();
        java.util.UUID byRef = PlayerIds.ownerByRef(s, ref);
        if (byRef != null) return byRef;
        CharacterService.ResolvedCharacter rc = PlayerIds.character(s, ref);
        if (rc != null) return rc.ownerId();
        @SuppressWarnings("deprecation") var off = Bukkit.getOfflinePlayerIfCached(ref);
        return off != null ? off.getUniqueId() : null;
    }

    /* ================================================================ sous-commandes */

    private void annoncePerso(CommandSender s, String ref, String m) {
        if (!OutilsStaff.exige(s, OutilsStaff.G_ANNONCE_PERSO)) return;
        Player t = enLigne(s, ref);
        if (t == null || m.isBlank()) return;
        o.effets().annoncePerso(s, t, m, 8);
        ok(s, "Message affiché à " + nom(t) + ".");
    }

    void emote(CommandSender s, Player t, String id) {
        EmoteManager em = plugin.emotes();
        if (em == null) { err(s, "Emotes indisponibles."); return; }
        if (id.equalsIgnoreCase("stop")) {
            em.stop(t);
            Journal.entree(Categorie.STAFF, "EMOTE_FORCEE").acteur(s).cible(t).detail("arrêt").ecrire();
            ok(s, "Emote arrêtée pour " + nom(t) + ".");
            return;
        }
        EmoteManager.Entry e = em.resolve(id);
        if (e == null) { err(s, "Emote inconnue : " + id); return; }
        em.play(t, e);
        Journal.entree(Categorie.STAFF, "EMOTE_FORCEE").acteur(s).cible(t).detail(e.key()).ecrire();
        ok(s, "Emote « " + e.key() + " » jouée par " + nom(t) + ".");
    }

    private void noir(CommandSender s, String[] a) {
        if (!OutilsStaff.exige(s, OutilsStaff.G_NOIR)) return;
        if (a.length < 1) { info(s, "/noir <cible|tous> [secondes|off] [message]"); return; }
        List<Player> cibles = new ArrayList<>();
        if (a[0].equalsIgnoreCase("tous")) {
            if (!OutilsStaff.exige(s, StaffGrades.ADMIN)) return;
            for (Player p : Bukkit.getOnlinePlayers()) if (StaffGrades.of(p) == StaffGrades.NONE) cibles.add(p);
        } else {
            Player t = enLigne(s, a[0]);
            if (t == null) return;
            cibles.add(t);
        }
        boolean off = a.length >= 2 && (a[1].equalsIgnoreCase("off") || a[1].equalsIgnoreCase("fin"));
        if (off || (a.length == 1 && cibles.size() == 1 && o.effets().enNoir(cibles.get(0).getUniqueId()))) {
            int n = 0;
            for (Player p : cibles) if (o.effets().finNoir(s, p.getUniqueId())) n++;
            ok(s, "Écran noir levé (" + n + ").");
            return;
        }
        int sec = 0;
        int from = 1;
        if (a.length >= 2) {
            try { sec = Math.max(0, Integer.parseInt(a[1])); from = 2; } catch (NumberFormatException ignored) { }
        }
        String m = reste(a, from);
        for (Player p : cibles) o.effets().noir(s, p, m, sec);
        ok(s, "Écran noir sur " + (cibles.size() == 1 ? nom(cibles.get(0)) : cibles.size() + " joueurs")
                + (sec > 0 ? " pendant " + sec + " s." : ". /noir " + a[0] + " off pour lever."));
    }

    private void seisme(CommandSender s, String[] a) {
        if (!OutilsStaff.exige(s, OutilsStaff.G_SEISME)) return;
        // /seisme [rayon|tous] [force 1-10] [secondes] [message…]
        int rayon = 0, force = 5, sec = 4, i = 0;
        if (a.length > i) {
            if (a[i].equalsIgnoreCase("tous")) { rayon = 0; i++; }
            else try { rayon = Math.max(0, Integer.parseInt(a[i])); i++; } catch (NumberFormatException ignored) { }
        }
        if (a.length > i) try { force = Integer.parseInt(a[i]); i++; } catch (NumberFormatException ignored) { }
        if (a.length > i) try { sec = Integer.parseInt(a[i]); i++; } catch (NumberFormatException ignored) { }
        if (rayon > 0 && !(s instanceof Player)) { err(s, "Un rayon demande d'être en jeu ; « tous » depuis la console."); return; }
        var centre = s instanceof Player p ? p.getLocation() : null;
        int n = o.effets().seisme(s, centre, rayon, force, sec, reste(a, i));
        ok(s, "Séisme déclenché (" + n + " joueur(s)).");
    }

    private void note(CommandSender s, String[] a) {
        if (!OutilsStaff.exige(s, OutilsStaff.G_NOTE)) return;
        if (a.length < 1) { info(s, "/note <cible|#N°> [ajouter [etiquette:<mot>] <texte> | suppr <id>]"); return; }
        CharacterService.ResolvedCharacter rc = PlayerIds.character(s, a[0]);
        if (rc == null || !rc.character().hasPlayerId()) { err(s, "Personnage introuvable (ou sans N°) : " + a[0]); return; }
        int pid = rc.character().playerId();
        String qui = PlayerIds.prefix(rc.character()) + rc.character().name();
        if (a.length >= 3 && (a[1].equalsIgnoreCase("ajouter") || a[1].equalsIgnoreCase("add"))) {
            String etiquette = "";
            int from = 2;
            if (a[2].toLowerCase(Locale.ROOT).startsWith("etiquette:") || a[2].toLowerCase(Locale.ROOT).startsWith("tag:")) {
                etiquette = a[2].substring(a[2].indexOf(':') + 1);
                from = 3;
            }
            if (o.notes().ajouter(s, pid, etiquette, reste(a, from)) != null) ok(s, "Note ajoutée à " + qui + ".");
            else err(s, "Note vide.");
            return;
        }
        if (a.length >= 3 && (a[1].equalsIgnoreCase("suppr") || a[1].equalsIgnoreCase("del"))) {
            int r = o.notes().supprimer(s, pid, a[2]);
            if (r > 0) ok(s, "Note supprimée.");
            else if (r < 0) err(s, "Seul l'auteur ou un Admin peut supprimer cette note.");
            else err(s, "Note introuvable : " + a[2]);
            return;
        }
        List<NotesStaff.Note> l = o.notes().de(pid);
        s.sendMessage(Component.text("Notes staff · " + qui + " (" + l.size() + ")", NamedTextColor.GOLD));
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yy HH:mm");
        for (NotesStaff.Note n : l) {
            Component line = Component.text(f.format(new Date(n.at())) + " ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(n.auteur() + " ", NamedTextColor.YELLOW))
                    .append(Component.text(n.etiquette().isEmpty() ? "" : "[" + n.etiquette() + "] ", NamedTextColor.AQUA))
                    .append(Component.text(n.texte(), NamedTextColor.WHITE));
            if (NotesStaff.peutSupprimer(s, n)) {
                line = line.append(Component.text("  ✕", NamedTextColor.RED)
                        .clickEvent(ClickEvent.suggestCommand("/note " + a[0] + " suppr " + n.id()))
                        .hoverEvent(HoverEvent.showText(Component.text("Supprimer (id " + n.id() + ")"))));
            }
            s.sendMessage(line);
        }
        if (l.isEmpty()) info(s, "Aucune note. /note " + a[0] + " ajouter <texte>");
    }

    /* ================================================================ /journal */

    private void journal(CommandSender s, String[] a) {
        if (!OutilsStaff.exige(s, OutilsStaff.G_JOURNAL)) return;
        JournalServiceImpl j = Journal.impl();
        if (j == null) { err(s, "Journal indisponible."); return; }
        Set<Categorie> cats = EnumSet.noneOf(Categorie.class);
        int pid = -1, page = 0;
        long du = 0;
        String joueur = "";
        List<String> texte = new ArrayList<>();
        for (String t : a) {
            String b = t.toLowerCase(Locale.ROOT);
            if (t.startsWith("#") && PlayerIdRegistry.parse(t) >= 0) pid = PlayerIdRegistry.parse(t);
            else if (b.startsWith("cat:") && Categorie.parse(t.substring(4)) != null) cats.add(Categorie.parse(t.substring(4)));
            else if (b.startsWith("j:") || b.startsWith("joueur:")) joueur = t.substring(t.indexOf(':') + 1);
            else if (b.matches("h:\\d+")) du = System.currentTimeMillis() - Long.parseLong(b.substring(2)) * 3_600_000L;
            else if (b.matches("d:\\d+")) du = System.currentTimeMillis() - Long.parseLong(b.substring(2)) * 86_400_000L;
            else if (b.matches("p:\\d+")) page = Math.max(0, Integer.parseInt(b.substring(2)) - 1);
            else if (Categorie.parse(t) != null && texte.isEmpty()) cats.add(Categorie.parse(t));
            else texte.add(t);
        }
        if (a.length == 0) {
            info(s, "/journal [#N°] [catégorie…] [j:<pseudo|nom RP>] [h:<heures>|d:<jours>] [p:<page>] [texte]");
            info(s, "Catégories : " + String.join(", ", Arrays.stream(Categorie.values()).map(c -> c.name().toLowerCase(Locale.ROOT)).toList()));
        }
        var req = new JournalServiceImpl.Requete(cats, pid, joueur, du, 0, String.join(" ", texte), page, 12);
        Journal.entree(Categorie.STAFF, "JOURNAL_CONSULTE").acteur(s).detail(String.join(" ", a)).ecrire();
        j.chercher(req, res -> {
            s.sendMessage(Component.text("Journal · " + res.total() + " entrée(s) · page " + (res.page() + 1) + "/" + res.pages()
                    + (res.source().equals("disque") ? " (fichiers)" : ""), NamedTextColor.GOLD));
            SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm:ss");
            for (JournalServiceImpl.Ligne l : res.lignes()) {
                Component line = Component.text(f.format(new Date(l.at())) + " ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("[" + l.cat().label() + "] ", NamedTextColor.AQUA))
                        .append(Component.text(l.action() + " ", NamedTextColor.YELLOW))
                        .append(Component.text(l.acteur() == null ? "—" : l.acteur().texte(), NamedTextColor.WHITE));
                if (l.cible() != null) line = line.append(Component.text(" → " + l.cible().texte(), NamedTextColor.WHITE));
                if (!l.detail().isEmpty()) line = line.append(Component.text(" · " + l.detail(), NamedTextColor.GRAY));
                if (!l.lieu().isEmpty()) line = line.hoverEvent(HoverEvent.showText(Component.text(l.lieu())));
                s.sendMessage(line);
            }
            if (res.page() + 1 < res.pages()) info(s, "Page suivante : ajoute p:" + (res.page() + 2));
        });
    }

    /* ================================================================ complétion */

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] a) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (c.equals("journal")) {
            for (Categorie k : Categorie.values()) out.add(k.name().toLowerCase(Locale.ROOT));
            out.addAll(List.of("h:24", "d:7", "p:2", "j:"));
        } else if (a.length == 1 && !c.equals("modestaff") && !c.equals("seisme")) {
            if (c.equals("noir")) out.add("tous");
            if (c.equals("annonce")) out.add("joueur");
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 1 && c.equals("seisme")) {
            out.addAll(List.of("tous", "50", "100"));
        } else if (a.length == 2) {
            switch (c) {
                case "noir" -> out.addAll(List.of("off", "10", "30", "60"));
                case "tenue" -> out.addAll(List.of("main", "retirer"));
                case "note" -> out.addAll(List.of("ajouter", "suppr"));
                case "persoreset", "persosuppr" -> out.add("confirmer");
                case "emoteforcee" -> { if (plugin.emotes() != null) { out.add("stop"); out.addAll(plugin.emotes().declaredKeys()); } }
                default -> { }
            }
        }
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
