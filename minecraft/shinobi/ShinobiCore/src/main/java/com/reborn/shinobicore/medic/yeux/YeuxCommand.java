package com.reborn.shinobicore.medic.yeux;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.AutoMe;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.KoState;
import com.reborn.shinobicore.ko.action.DeathScene;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /yeux} — la chirurgie des yeux, côté serveur (autorité unique : le mod ne fait que jouer le geste).
 *
 * <ul>
 *   <li>{@code /yeux prelever <joueur|corps:<uuidMannequin>> <gauche|droit>} — sur un joueur en scène de mort,
 *       un corps au sol, ou un corps KO sur lequel l'opérateur a une RPK validée. Ouvre l'écran (mode
 *       {@code prelevement}) avec un jeton à usage unique.</li>
 *   <li>{@code /yeux greffer <joueur> <gauche|droit>} — médic expert, fiole en main, orbite vide. Patient
 *       inconscient : écran direct ; sinon il doit accepter ({@code /yeux consentir <médic>}).</li>
 *   <li>{@code /yeux resultat <prelevement|greffe> <ok|echec> <score> <jeton>} — envoyé par le mod
 *       ({@code ChirurgieScreen}) ; tout est revérifié ici avant d'appliquer.</li>
 *   <li>{@code /yeux etat [joueur]}, et pour le staff {@code definir} / {@code donner}.</li>
 * </ul>
 * Tout tourne sur le thread principal (commandes Bukkit).
 */
public final class YeuxCommand implements TabExecutor, Listener {

    public static final String PERM_ADMIN = "shinobicore.yeux.admin";
    public static final String PERM_MEDIC = "shinobicore.medic";
    public static final String PERM_EXPERT = "shinobicore.medic.expert";
    public static final String PERM_STAFF = "shinobicore.staff";

    private enum Mode {
        PRELEVEMENT("prelevement"), GREFFE("greffe");
        final String id;
        Mode(String id) { this.id = id; }
        static Mode parse(String s) {
            for (Mode m : values()) if (m.id.equalsIgnoreCase(s)) return m;
            return null;
        }
    }

    /**
     * Sur qui on opère. {@code mannequin != null} : un corps au sol (le joueur propriétaire peut être hors ligne) ;
     * sinon un joueur en ligne (mort en scène, KO, ou patient d'une greffe).
     */
    private record Cible(UUID player, UUID charId, UUID mannequin, String nom) {
        boolean corps() { return mannequin != null; }
    }

    private record Jeton(String id, UUID operateur, Mode mode, Cible cible, Cote cote, String type,
                         int integrite, long expire, UUID fiole) {}

    private record Consent(UUID medic, String medicName, Cote cote, UUID fiole, long expire) {}

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";

    private final ShinobiCore plugin;
    private final YeuxService yeux;
    private final SecureRandom rnd = new SecureRandom();
    private final Map<String, Jeton> jetons = new HashMap<>();
    /** patient → proposition de greffe en attente. */
    private final Map<UUID, Consent> consents = new HashMap<>();

    public YeuxCommand(ShinobiCore plugin, YeuxService yeux) {
        this.plugin = plugin;
        this.yeux = yeux;
    }

    /* ============================================================ dispatch */

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        String sub = a.length == 0 ? "etat" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "prelever" -> { if (s instanceof Player p) prelever(p, a); }
            case "greffer" -> { if (s instanceof Player p) greffer(p, a); }
            case "consentir" -> { if (s instanceof Player p) consentir(p, a); }
            case "resultat" -> { if (s instanceof Player p) resultat(p, a); }
            case "etat" -> etat(s, a);
            case "definir" -> definir(s, a);
            case "donner" -> donner(s, a);
            default -> usage(s);
        }
        return true;
    }

    private void usage(CommandSender s) {
        s.sendMessage(Component.text("/yeux etat [joueur] · prelever <joueur|corps:id> <gauche|droit> · "
                + "greffer <joueur> <gauche|droit> · consentir <médic>", NamedTextColor.GRAY));
        if (s.hasPermission(PERM_ADMIN)) {
            s.sendMessage(Component.text("Staff : /yeux definir <joueur> <gauche|droit> <type|manquant|defaut> [intégrité]"
                    + " · donner <joueur> <type> <gauche|droit> [intégrité]", NamedTextColor.GRAY));
        }
    }

    /* =========================================================== prélèvement */

    private void prelever(Player op, String[] a) {
        if (a.length < 3) { msg(op, "Usage : /yeux prelever <joueur|corps:id> <gauche|droit>"); return; }
        Cote cote = Cote.parse(a[2]);
        if (cote == null) { msg(op, "Côté : gauche ou droit."); return; }
        if (busy(op)) { bar(op, "Pas dans ton état."); return; }
        Cible c = resolvePrelevement(op, a[1]);
        if (c == null) return;                                   // message déjà envoyé
        String why = ineligible(op, c, yeux.portee());
        if (why != null) { bar(op, why); return; }
        ShinobiCharacter ch = character(c);
        if (ch == null) { msg(op, "Ce corps n'a plus d'identité connue."); return; }
        Oeil o = yeux.oeil(ch, cote);
        if (!o.present()) { bar(op, "L'orbite " + cote.nom + " est déjà vide."); return; }
        if (!yeux.modded(op)) {
            msg(op, "Il faut le mod Reborn à jour pour opérer.");
            return;
        }
        Jeton j = issue(op, Mode.PRELEVEMENT, c, cote, o.type(), o.integrite(), null);
        open(op, j);
        AutoMe.broadcast(plugin, op, "se penche sur le visage de " + c.nom() + ", un scalpel à la main.");
    }

    /** {@code corps:<uuid>} → corps au sol ; sinon un joueur en ligne (mort en scène ou KO avec RPK). */
    private Cible resolvePrelevement(Player op, String arg) {
        DeathScene ds = plugin.deathScene();
        if (arg.toLowerCase(Locale.ROOT).startsWith("corps:")) {
            UUID m;
            try { m = UUID.fromString(arg.substring(6)); } catch (IllegalArgumentException ex) { m = null; }
            DeathScene.CorpseOwner co = ds != null && m != null ? ds.corpseOwner(m) : null;
            if (co == null || co.charId() == null) { bar(op, "Ce corps n'est plus là."); return null; }
            return new Cible(co.player(), co.charId(), m, co.name());
        }
        Player t = player(arg);
        if (t == null || t == op) { bar(op, "Personne ici."); return null; }
        UUID charId = null;
        if (ds != null && ds.isDead(t.getUniqueId())) charId = ds.deadCharacter(t.getUniqueId());
        if (charId == null) {
            ShinobiCharacter act = plugin.characters().getActive(t.getUniqueId());
            charId = act != null ? act.id() : null;
        }
        if (charId == null) { bar(op, "Cette personne n'a pas de personnage."); return null; }
        ShinobiCharacter ch = plugin.characters().findById(t.getUniqueId(), charId).orElse(null);
        return new Cible(t.getUniqueId(), charId, null, ch != null ? ch.name() : t.getName());
    }

    /**
     * Raison pour laquelle on ne peut pas (ou plus) prélever sur cette cible, ou {@code null}. Joueur : en scène de
     * mort, ou KO avec une RPK validée pour l'opérateur, et toujours le même personnage. Corps : toujours au sol.
     */
    private String ineligible(Player op, Cible c, double reach) {
        DeathScene ds = plugin.deathScene();
        Location at;
        if (c.corps()) {
            DeathScene.CorpseOwner co = ds != null ? ds.corpseOwner(c.mannequin()) : null;
            if (co == null) return "Ce corps n'est plus là.";
            at = co.at();
        } else {
            Player t = Bukkit.getPlayer(c.player());
            if (t == null) return "Cette personne n'est plus là.";
            boolean dead = ds != null && ds.isDead(t.getUniqueId());
            if (dead) {
                UUID dc = ds.deadCharacter(t.getUniqueId());
                if (dc != null && !dc.equals(c.charId())) return "Ce n'est plus le même corps.";
            } else {
                if (!plugin.ko().isKo(t.getUniqueId())) return "Cette personne n'est pas à terre.";
                if (plugin.killRequests().validatedFor(op.getUniqueId(), t.getUniqueId()) == null) {
                    return "Il faut une RPK validée par le staff sur cette personne.";
                }
                ShinobiCharacter act = plugin.characters().getActive(t.getUniqueId());
                if (act == null || !act.id().equals(c.charId())) return "Ce n'est plus la même personne.";
            }
            at = t.getLocation();
        }
        if (!near(op, at, reach)) return "Trop loin.";
        return null;
    }

    /* =============================================================== greffe */

    private void greffer(Player medic, String[] a) {
        if (!medic.hasPermission(PERM_EXPERT)) { msg(medic, "Seul un médic expert peut greffer un œil."); return; }
        if (a.length < 3) { msg(medic, "Usage : /yeux greffer <joueur> <gauche|droit>"); return; }
        Cote cote = Cote.parse(a[2]);
        if (cote == null) { msg(medic, "Côté : gauche ou droit."); return; }
        Player patient = player(a[1]);
        if (patient == null) { bar(medic, "Personne ici."); return; }
        if (patient == medic) { msg(medic, "Tu ne peux pas te greffer toi-même."); return; }
        FioleOeil.Fiole f = FioleOeil.read(medic.getInventory().getItemInMainHand());
        if (f == null) { msg(medic, "Prends la fiole d'œil en main."); return; }
        String why = greffeCheck(medic, patient, cote, f);
        if (why != null) { bar(medic, why); return; }
        ShinobiCharacter pc = plugin.characters().getActive(patient.getUniqueId());
        KoState st = plugin.ko().getKo(patient.getUniqueId());
        if (st != null && !st.isDowned()) {                       // inconscient : pas de consentement possible
            openGreffe(medic, patient, pc, cote, f);
            return;
        }
        consents.put(patient.getUniqueId(), new Consent(medic.getUniqueId(), medic.getName(), cote, f.id(),
                System.currentTimeMillis() + yeux.consentementMillis()));
        long sec = yeux.consentementMillis() / 1000L;
        patient.sendMessage(Component.text(name(medic) + " propose de te greffer un œil (" + Oeil.label(f.type())
                        + ", intégrité " + f.integrite() + " %) dans l'orbite " + cote.nom + ". ", NamedTextColor.GRAY)
                .append(Component.text("[Accepter]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/yeux consentir " + medic.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Accepter l'opération (" + sec + " s)")))));
        msg(medic, "Tu attends l'accord de " + pc.name() + ".");
    }

    private void consentir(Player patient, String[] a) {
        Consent c = consents.get(patient.getUniqueId());
        if (c == null || c.expire() < System.currentTimeMillis()) {
            consents.remove(patient.getUniqueId());
            msg(patient, "Aucune proposition de greffe en attente.");
            return;
        }
        Player medic = Bukkit.getPlayer(c.medic());
        if (a.length >= 2 && medic != null && !a[1].equalsIgnoreCase(c.medicName()) && !a[1].equalsIgnoreCase(name(medic))) {
            msg(patient, "Aucune proposition de greffe de cette personne.");
            return;
        }
        consents.remove(patient.getUniqueId());
        if (medic == null) { msg(patient, "Le médic n'est plus là."); return; }
        FioleOeil.Fiole f = findFiole(medic, c.fiole());
        String why = !medic.hasPermission(PERM_EXPERT) ? "Ce médic ne peut plus opérer."
                : f == null ? "Le médic n'a plus la fiole."
                : greffeCheck(medic, patient, c.cote(), f);
        if (why != null) {
            msg(patient, "L'opération ne peut pas avoir lieu : " + lower(why));
            msg(medic, "L'opération ne peut pas avoir lieu : " + lower(why));
            return;
        }
        msg(patient, "Tu acceptes l'opération.");
        openGreffe(medic, patient, plugin.characters().getActive(patient.getUniqueId()), c.cote(), f);
    }

    /** Conditions d'une greffe (hors permission et consentement) ; {@code null} si tout va. */
    private String greffeCheck(Player medic, Player patient, Cote cote, FioleOeil.Fiole f) {
        if (busy(medic)) return "Pas dans ton état.";
        if (plugin.deathScene() != null && plugin.deathScene().isDead(patient.getUniqueId())) return "Il est trop tard pour cette personne.";
        if (!near(medic, patient.getLocation(), yeux.portee())) return "Trop loin.";
        if (f == null || f.integrite() <= 0) return "Cette fiole ne contient plus rien d'utilisable.";
        ShinobiCharacter pc = plugin.characters().getActive(patient.getUniqueId());
        if (pc == null) return "Cette personne n'a pas de personnage.";
        if (yeux.oeil(pc, cote).present()) return "L'orbite " + cote.nom + " n'est pas vide.";
        return null;
    }

    private void openGreffe(Player medic, Player patient, ShinobiCharacter pc, Cote cote, FioleOeil.Fiole f) {
        if (pc == null) return;
        if (!yeux.modded(medic)) { msg(medic, "Il faut le mod Reborn à jour pour opérer."); return; }
        Jeton j = issue(medic, Mode.GREFFE, new Cible(patient.getUniqueId(), pc.id(), null, pc.name()), cote,
                f.type(), f.integrite(), f.id());
        open(medic, j);
        AutoMe.broadcast(plugin, medic, "ouvre une fiole et prépare une greffe d'œil sur " + pc.name() + ".");
    }

    /* ============================================================= résultat */

    private void resultat(Player op, String[] a) {
        // /yeux resultat <mode> <ok|echec> <score> <jeton>
        Jeton j = a.length >= 5 ? jetons.remove(a[4].toLowerCase(Locale.ROOT)) : null;
        Mode mode = a.length >= 2 ? Mode.parse(a[1]) : null;
        if (j == null || !j.operateur().equals(op.getUniqueId()) || j.mode() != mode) {
            if (j != null && !j.operateur().equals(op.getUniqueId())) jetons.put(j.id(), j);   // pas le sien : on le laisse
            plugin.getLogger().info("[yeux] résultat ignoré de " + op.getName() + " : " + String.join(" ", a));
            op.sendActionBar(Component.text("Aucune opération en cours.", NamedTextColor.DARK_GRAY));
            return;
        }
        if (j.expire() < System.currentTimeMillis()) {
            plugin.getLogger().info("[yeux] jeton expiré pour " + op.getName() + " (" + j.mode().id + ").");
            msg(op, "Trop tard : l'opération a été abandonnée.");
            return;
        }
        boolean ok = a[2].equalsIgnoreCase("ok");
        int score;
        try { score = Math.max(0, Math.min(100, Integer.parseInt(a[3]))); } catch (NumberFormatException ex) { score = 0; }
        if (j.mode() == Mode.PRELEVEMENT) finishPrelevement(op, j, ok, score);
        else finishGreffe(op, j, ok, score);
    }

    private void finishPrelevement(Player op, Jeton j, boolean ok, int score) {
        Cible c = j.cible();
        String why = busy(op) ? "Pas dans ton état." : ineligible(op, c, yeux.portee() + 1);
        ShinobiCharacter ch = why == null ? character(c) : null;
        if (why == null && ch == null) why = "Ce corps n'a plus d'identité connue.";
        Oeil o = ch != null ? yeux.oeil(ch, j.cote()) : null;
        if (why == null && !o.present()) why = "L'orbite est déjà vide.";
        if (why != null) {
            msg(op, "Opération interrompue : " + lower(why));
            return;
        }
        yeux.setOeil(ch, j.cote(), o.retire());
        Location at = location(c, op);
        int integ = Math.max(1, Math.min(100, Math.round(score * o.integrite() / 100f)));
        if (ok) {
            ItemStack fiole = FioleOeil.create(o.type(), j.cote(), integ, c.nom(), name(op));
            give(op, fiole);
            msg(op, "L'œil " + j.cote().nom + " de " + c.nom() + " est dans la fiole (intégrité " + integ + " %).");
            AutoMe.broadcast(plugin, op, "retire l'œil " + j.cote().nom + " de " + c.nom() + " et le scelle dans une fiole.");
            op.playSound(op.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1.4f);
        } else {
            msg(op, "L'œil n'a pas survécu.");
            AutoMe.broadcast(plugin, op, "arrache l'œil " + j.cote().nom + " de " + c.nom() + " ; il n'a pas survécu.");
        }
        Player victim = Bukkit.getPlayer(c.player());
        if (victim != null && !c.corps()) {
            victim.sendMessage(Component.text("On t'a pris l'œil " + j.cote().nom + ".", NamedTextColor.DARK_RED));
        }
        String source = c.corps() ? "corps au sol"
                : plugin.deathScene() != null && plugin.deathScene().isDead(c.player()) ? "mort, en scène" : "KO, RPK validée";
        alert(op, c, j, ok, integ, source, at);
    }

    private void finishGreffe(Player medic, Jeton j, boolean ok, int score) {
        Cible c = j.cible();
        Player patient = Bukkit.getPlayer(c.player());
        String why = null;
        ShinobiCharacter pc = null;
        if (!medic.hasPermission(PERM_EXPERT)) why = "Tu ne peux plus opérer.";
        else if (busy(medic)) why = "Pas dans ton état.";
        else if (patient == null) why = "Le patient n'est plus là.";
        else if (plugin.deathScene() != null && plugin.deathScene().isDead(patient.getUniqueId())) why = "Il est trop tard pour le patient.";
        else if (!near(medic, patient.getLocation(), yeux.portee() + 1)) why = "Le patient est trop loin.";
        else {
            pc = plugin.characters().getActive(patient.getUniqueId());
            if (pc == null || !pc.id().equals(c.charId())) why = "Ce n'est plus le même patient.";
            else if (yeux.oeil(pc, j.cote()).present()) why = "L'orbite n'est plus vide.";
        }
        FioleOeil.Fiole f = why == null ? findFiole(medic, j.fiole()) : null;
        if (why == null && f == null) why = "La fiole n'est plus sur toi.";
        if (why != null) {
            msg(medic, "Opération interrompue : " + lower(why));
            return;
        }
        consumeFiole(medic, f.id());
        String pn = pc.name();
        if (ok) {
            int integ = Math.max(1, Math.min(100, Math.round(f.integrite() * (0.5f + score / 200f))));
            yeux.setOeil(pc, j.cote(), new Oeil(f.type(), Oeil.Etat.NORMAL, integ));
            msg(medic, "Greffe réussie : " + Oeil.label(f.type()) + " dans l'orbite " + j.cote().nom + " de " + pn
                    + " (intégrité " + integ + " %).");
            patient.sendMessage(Component.text("Un œil a été greffé dans ton orbite " + j.cote().nom + ".", NamedTextColor.GREEN));
            AutoMe.broadcast(plugin, medic, "achève la greffe et referme les paupières de " + pn + ".");
        } else {
            yeux.pushCecite(patient, false);
            msg(medic, "La greffe a échoué : l'œil est perdu.");
            patient.sendMessage(Component.text("La greffe a échoué. Ton orbite " + j.cote().nom + " reste vide.", NamedTextColor.GRAY));
            AutoMe.broadcast(plugin, medic, "échoue la greffe ; l'œil est perdu.");
        }
        plugin.getLogger().info("[yeux] greffe " + (ok ? "réussie" : "échouée") + " par " + medic.getName() + " sur "
                + pn + " (" + patient.getName() + ") · " + j.cote().nom + " " + f.type() + " · fiole " + f.id()
                + " (" + f.integrite() + " %, de " + f.provenance() + ") · score " + score);
        if (plugin.staffPanel() != null) {
            plugin.staffPanel().log(medic, "soin", "Greffe d'œil " + (ok ? "réussie" : "échouée"),
                    pn + " · " + j.cote().nom + " · " + f.type() + " (fiole de " + f.provenance() + ")");
        }
    }

    /* ============================================================ état, staff */

    private void etat(CommandSender s, String[] a) {
        Player t;
        if (a.length >= 2) {
            if (!s.hasPermission(PERM_MEDIC) && !s.hasPermission(PERM_STAFF)) {
                msg(s, "Seul un médic peut examiner les yeux de quelqu'un.");
                return;
            }
            t = player(a[1]);
            if (t == null) { msg(s, "Joueur introuvable."); return; }
        } else if (s instanceof Player p) {
            t = p;
        } else { msg(s, "/yeux etat <joueur>"); return; }
        ShinobiCharacter c = plugin.characters().getActive(t.getUniqueId());
        if (c == null) { msg(s, "Pas de personnage actif."); return; }
        YeuxService.Yeux y = yeux.eyes(c);
        s.sendMessage(Component.text((t == s ? "Tes yeux" : "Yeux de " + c.name()) + " :", NamedTextColor.GOLD));
        for (Cote cote : Cote.values()) {
            Oeil o = y.get(cote);
            String txt = o.present()
                    ? Oeil.label(o.type()) + " · intégrité " + o.integrite() + " %"
                        + (o.aveugle(yeux.seuilCecite()) ? " · ne voit plus" : "")
                    : "orbite vide";
            s.sendMessage(Component.text("  " + cap(cote.nom) + " : " + txt,
                    o.present() && !o.aveugle(yeux.seuilCecite()) ? NamedTextColor.GRAY : NamedTextColor.RED));
        }
        if (y.bandage()) s.sendMessage(Component.text("  Un bandage couvre les orbites.", NamedTextColor.GRAY));
    }

    private void definir(CommandSender s, String[] a) {
        if (!s.hasPermission(PERM_ADMIN)) { msg(s, "Réservé au staff."); return; }
        if (a.length < 4) { msg(s, "/yeux definir <joueur> <gauche|droit> <type|manquant|defaut> [intégrité]"); return; }
        Player t = player(a[1]);
        Cote cote = Cote.parse(a[2]);
        if (t == null || cote == null) { msg(s, "Joueur ou côté invalide."); return; }
        ShinobiCharacter c = plugin.characters().getActive(t.getUniqueId());
        if (c == null) { msg(s, "Ce joueur n'a pas de personnage actif."); return; }
        String type = a[3].toLowerCase(Locale.ROOT);
        Oeil o;
        if (type.equals("defaut") || type.equals("reset")) o = null;
        else if (type.equals("manquant")) o = yeux.oeil(c, cote).retire();
        else o = new Oeil(type, Oeil.Etat.NORMAL, a.length >= 5 ? parseInt(a[4], 100) : 100);
        yeux.setOeil(c, cote, o);
        Oeil now = yeux.oeil(c, cote);
        msg(s, "Œil " + cote.nom + " de " + c.name() + " : " + (now.present() ? Oeil.label(now.type()) + " " + now.integrite() + " %" : "orbite vide") + ".");
        plugin.getLogger().info("[yeux] " + s.getName() + " définit l'œil " + cote.nom + " de " + c.name() + " : " + String.join(" ", a));
        if (s instanceof Player sp && plugin.staffPanel() != null) {
            plugin.staffPanel().log(sp, "yeux", "Œil défini", c.name() + " · " + cote.nom + " · " + type);
        }
    }

    private void donner(CommandSender s, String[] a) {
        if (!s.hasPermission(PERM_ADMIN)) { msg(s, "Réservé au staff."); return; }
        if (a.length < 4) { msg(s, "/yeux donner <joueur> <type> <gauche|droit> [intégrité]"); return; }
        Player t = player(a[1]);
        Cote cote = Cote.parse(a[3]);
        if (t == null || cote == null) { msg(s, "Joueur ou côté invalide."); return; }
        int integ = a.length >= 5 ? parseInt(a[4], 100) : 100;
        give(t, FioleOeil.create(a[2], cote, integ, "Staff", s.getName()));
        msg(s, "Fiole d'œil " + Oeil.label(a[2]) + " (" + cote.nom + ", " + Math.max(1, Math.min(100, integ)) + " %) donnée à " + t.getName() + ".");
        plugin.getLogger().info("[yeux] " + s.getName() + " donne une fiole : " + String.join(" ", a));
        if (s instanceof Player sp && plugin.staffPanel() != null) {
            plugin.staffPanel().log(sp, "yeux", "Fiole d'œil donnée", t.getName() + " · " + a[2] + " · " + cote.nom);
        }
    }

    /* ============================================================== outils */

    private Jeton issue(Player op, Mode mode, Cible c, Cote cote, String type, int integ, UUID fiole) {
        long now = System.currentTimeMillis();
        jetons.values().removeIf(j -> j.operateur().equals(op.getUniqueId()) || j.expire() < now);
        String id;
        do {
            StringBuilder sb = new StringBuilder(10);
            for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
            id = sb.toString();
        } while (jetons.containsKey(id));
        Jeton j = new Jeton(id, op.getUniqueId(), mode, c, cote, type, integ, now + yeux.jetonMillis(), fiole);
        jetons.put(id, j);
        return j;
    }

    private void open(Player op, Jeton j) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "ouvrir");
        o.addProperty("mode", j.mode().id);
        o.addProperty("patient", j.cible().nom());
        o.addProperty("cote", j.cote().code);
        o.addProperty("type", j.type());
        o.addProperty("jeton", j.id());
        yeux.send(op, o);
    }

    private void alert(Player op, Cible c, Jeton j, boolean ok, int integ, String source, Location at) {
        String type = Oeil.label(j.type());
        plugin.getLogger().info("[yeux] prélèvement " + (ok ? "réussi" : "raté") + " par " + op.getName() + " sur "
                + c.nom() + " (" + c.player() + ", " + source + ") · " + j.cote().nom + " " + j.type()
                + (ok ? " · fiole " + integ + " %" : "") + " · " + place(at));
        if (plugin.staffPanel() != null) {
            plugin.staffPanel().onPrelevementOeil(op, c.player(), c.nom(), j.cote().nom, type, ok, integ, source, at);
        }
        // staff marqué par shinobicore.staff mais sans grade du Poste de garde : message direct
        Component line = Component.text("[Staff] ", NamedTextColor.GOLD).append(Component.text(
                "Prélèvement d'œil : " + name(op) + " sur " + c.nom() + " (" + source + ") · œil " + j.cote().nom + " "
                        + type + " · " + (ok ? "prélevé" : "perdu") + " · " + place(at), NamedTextColor.RED));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(PERM_STAFF) && (plugin.staffPanel() == null || StaffGrades.of(p) <= 0)) p.sendMessage(line);
        }
    }

    private ShinobiCharacter character(Cible c) {
        return plugin.characters().findById(c.player(), c.charId()).orElse(null);
    }

    private Location location(Cible c, Player fallback) {
        if (c.corps()) {
            DeathScene.CorpseOwner co = plugin.deathScene() != null ? plugin.deathScene().corpseOwner(c.mannequin()) : null;
            if (co != null) return co.at();
        } else {
            Player t = Bukkit.getPlayer(c.player());
            if (t != null) return t.getLocation();
        }
        return fallback.getLocation();
    }

    /** Opérateur KO ou mort : il ne peut rien faire. */
    private boolean busy(Player p) {
        return plugin.ko().isKo(p.getUniqueId())
                || (plugin.deathScene() != null && plugin.deathScene().isDead(p.getUniqueId()));
    }

    private static boolean near(Player p, Location at, double reach) {
        return at != null && at.getWorld() != null && at.getWorld().equals(p.getWorld())
                && at.distanceSquared(p.getLocation()) <= reach * reach;
    }

    private Player player(String arg) {
        Player t = Bukkit.getPlayerExact(arg);
        return t != null ? t : plugin.characters().findOnlinePlayerByCharacter(arg);
    }

    private FioleOeil.Fiole findFiole(Player p, UUID id) {
        if (id == null) return null;
        for (ItemStack it : p.getInventory().getContents()) {
            FioleOeil.Fiole f = FioleOeil.read(it);
            if (f != null && f.id().equals(id)) return f;
        }
        return null;
    }

    private void consumeFiole(Player p, UUID id) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] content = inv.getContents();
        for (int i = 0; i < content.length; i++) {
            FioleOeil.Fiole f = FioleOeil.read(content[i]);
            if (f == null || !f.id().equals(id)) continue;
            if (content[i].getAmount() > 1) content[i].setAmount(content[i].getAmount() - 1);
            else inv.setItem(i, null);
            return;
        }
    }

    private static void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    private String name(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        return c != null ? c.name() : p.getName();
    }

    private static String place(Location l) {
        if (l == null || l.getWorld() == null) return "?";
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return def; }
    }

    private static String lower(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void msg(CommandSender s, String text) { s.sendMessage(Component.text(text, NamedTextColor.GRAY)); }

    private static void bar(Player p, String text) { p.sendActionBar(Component.text(text, NamedTextColor.GRAY)); }

    /* ========================================================== événements */

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        UUID me = ev.getPlayer().getUniqueId();
        jetons.values().removeIf(j -> j.operateur().equals(me));
        consents.remove(me);
        consents.values().removeIf(c -> c.medic().equals(me));
    }

    /* ======================================================== tab-complétion */

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            List<String> subs = new ArrayList<>(List.of("etat", "prelever", "consentir"));
            if (s.hasPermission(PERM_EXPERT)) subs.add("greffer");
            if (s.hasPermission(PERM_ADMIN)) { subs.add("definir"); subs.add("donner"); }
            return filter(subs, a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("resultat")) return out;
        if (a.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, a[1]);
        }
        List<String> cotes = List.of("gauche", "droit");
        List<String> types = List.of("normal", "sharingan", "byakugan", "rinnegan");
        if (a.length == 3) {
            if (sub.equals("prelever") || sub.equals("greffer") || sub.equals("definir")) return filter(cotes, a[2]);
            if (sub.equals("donner")) return filter(types, a[2]);
        }
        if (a.length == 4) {
            if (sub.equals("definir")) {
                List<String> l = new ArrayList<>(types);
                l.add("manquant");
                l.add("defaut");
                return filter(l, a[3]);
            }
            if (sub.equals("donner")) return filter(cotes, a[3]);
        }
        return out;
    }

    private static List<String> filter(List<String> l, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : l) if (s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        return out;
    }
}
