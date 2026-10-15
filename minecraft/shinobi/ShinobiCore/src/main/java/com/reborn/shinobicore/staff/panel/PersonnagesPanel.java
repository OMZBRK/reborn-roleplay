package com.reborn.shinobicore.staff.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.StatsService;
import com.reborn.shinobicore.character.Affinity;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.CharacterManager;
import com.reborn.shinobicore.character.Clan;
import com.reborn.shinobicore.character.NinjaArt;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.select.CharacterSelectManager;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.stats.StatFormulas;
import com.reborn.shinobicore.stats.StatsServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Poste de garde › fiche d'un joueur › <b>Personnages</b> (15/10) : tous les personnages d'un compte, en ligne ou hors
 * ligne, et leur gestion staff. Remplace les écrans-coffres {@code /character create|edit} (CharacterEditScreen,
 * ClanPickerScreen) et les commandes {@code /persoreset}, {@code /persosuppr}.
 *
 * <p>Canal {@value StaffPanel#CHANNEL}, requêtes (C → S) :
 * <ul>
 *   <li>{@code persos} ({@code t} ou {@code pid}) → réponse {@code persos} ;</li>
 *   <li>{@code perso_act} ({@code t}, {@code c} = UUID du personnage ou {@code pid}, {@code op}, champs, {@code motif})
 *       → {@code toast} + {@code persos} + {@code profile} ;</li>
 *   <li>{@code perso_slots} ({@code t}, {@code delta} ±1, {@code motif}) et {@code perso_creer} ({@code t},
 *       {@code nom}, {@code motif}).</li>
 * </ul>
 * <b>Tout est revérifié ici</b> (grade, cible, valeurs) et <b>journalisé</b> (catégorie {@code STAFF}, détail avant →
 * après et motif). Grades : voir {@link #DROITS}.
 */
public final class PersonnagesPanel {

    /** Grade minimal par famille d'action (renvoyé au client dans {@code droits}). */
    static final int G_VOIR = StaffGrades.HELPER, G_MODIFIER = StaffGrades.MODO, G_ACTIF = StaffGrades.MODO,
            G_PROGRESSION = StaffGrades.ADMIN, G_MORT = StaffGrades.ADMIN, G_EMPLACEMENTS = StaffGrades.ADMIN,
            G_CREER = StaffGrades.ADMIN, G_RESET = StaffGrades.ADMIN, G_SUPPR = StaffGrades.ADMIN;
    static final Object[][] DROITS = {{"voir", G_VOIR}, {"modifier", G_MODIFIER}, {"actif", G_ACTIF},
            {"progression", G_PROGRESSION}, {"mort", G_MORT}, {"emplacements", G_EMPLACEMENTS}, {"creer", G_CREER},
            {"reset", G_RESET}, {"suppr", G_SUPPR}};

    /** Villages proposés (valeur stockée, libellé court) — mêmes valeurs que le créateur du mod. */
    static final String[][] VILLAGES = {{"Konohagakure", "Konoha"}, {"Sunagakure", "Suna"}, {"Kirigakure", "Kiri"},
            {"Kumogakure", "Kumo"}, {"Iwagakure", "Iwa"}, {"Amegakure", "Ame"}, {"Déserteur", "Déserteur"}, {"", "Aucun"}};

    private final ShinobiCore plugin;
    private final StaffPanel panel;

    PersonnagesPanel(ShinobiCore plugin, StaffPanel panel) {
        this.plugin = plugin;
        this.panel = panel;
    }

    void handle(Player p, int grade, String a, JsonObject in) {
        if (grade < G_VOIR) return;
        UUID owner = owner(in);
        if (owner == null) { panel.toast(p, "Joueur introuvable."); return; }
        switch (a) {
            case "persos" -> envoyer(p, owner);
            case "perso_act" -> { act(p, grade, owner, in); apres(p, owner); }
            case "perso_slots" -> { emplacements(p, grade, owner, in); apres(p, owner); }
            case "perso_creer" -> { creer(p, grade, owner, in); apres(p, owner); }
            default -> { }
        }
    }

    /** Compte visé : {@code t}, sinon le propriétaire du N° {@code pid}. */
    private UUID owner(JsonObject in) {
        UUID t = StaffPanel.uuid(in, "t");
        if (t != null) return t;
        int n = pid(in);
        var e = n >= 0 && plugin.playerIds() != null ? plugin.playerIds().get(n) : null;
        return e == null ? null : e.ownerId();
    }

    private static int pid(JsonObject in) {
        String s = StaffPanel.str(in, "pid").trim();
        return s.isEmpty() ? -1 : PlayerIdRegistry.parse(s.startsWith("#") ? s : "#" + s);
    }

    /** Personnage visé : {@code c} (UUID) dans le roster du compte, sinon par N°. */
    private ShinobiCharacter perso(UUID owner, JsonObject in) {
        CharacterManager mgr = plugin.characters();
        UUID c = StaffPanel.uuid(in, "c");
        if (c != null) return mgr.findById(owner, c).orElse(null);
        int n = pid(in);
        if (n < 0) return null;
        for (ShinobiCharacter x : mgr.getAll(owner)) if (x.hasPlayerId() && x.playerId() == n) return x;
        return null;
    }

    /** Après une action : la liste à jour, la fiche, et le roster d'un compte hors ligne relâché (déjà sauvé). */
    private void apres(Player viewer, UUID owner) {
        envoyer(viewer, owner);
        panel.profil(viewer, owner);
    }

    /* ================================================================ lecture */

    void envoyer(Player viewer, UUID owner) {
        CharacterManager mgr = plugin.characters();
        Player en = Bukkit.getPlayer(owner);
        List<ShinobiCharacter> roster = mgr.getAll(owner);
        ShinobiCharacter actif = en != null ? mgr.getActive(owner) : null;
        JsonObject o = new JsonObject();
        o.addProperty("t", "persos");
        o.addProperty("uuid", owner.toString());
        o.addProperty("pseudo", en != null ? en.getName() : panel.store().nameOf(owner));
        o.addProperty("online", en != null);
        CharacterSelectManager sel = plugin.characterSelect();
        o.addProperty("selection", en != null && sel != null && sel.isSelecting(owner));
        int bonus = sel != null ? sel.emplacements().of(owner) : 0;
        JsonObject slots = new JsonObject();
        slots.addProperty("used", roster.size());
        slots.addProperty("bonus", bonus);
        slots.addProperty("base", en != null ? CharacterSelectManager.slotBase(en) : -1);
        slots.addProperty("total", en != null && sel != null ? sel.slotLimit(en) : -1);
        o.add("slots", slots);
        if (en != null) {
            try { o.addProperty("playtime", en.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60); }
            catch (RuntimeException ignored) { }
        }
        int g = StaffGrades.of(viewer);
        o.addProperty("grade", g);
        JsonObject droits = new JsonObject();
        for (Object[] d : DROITS) droits.addProperty((String) d[0], (int) d[1]);
        o.add("droits", droits);
        JsonArray rangs = new JsonArray();
        for (Rank r : Rank.values()) rangs.add(paire(r.name(), r.displayName()));
        o.add("rangs", rangs);
        JsonArray villages = new JsonArray();
        for (String[] v : VILLAGES) villages.add(paire(v[0], v[1]));
        o.add("villages", villages);
        JsonArray arr = new JsonArray();
        for (ShinobiCharacter c : roster) arr.add(json(c, actif != null && actif.id().equals(c.id())));
        o.add("persos", arr);
        panel.send(viewer, o);
        if (en == null) mgr.unload(owner);   // hors ligne : sauvé, puis relâché (pas de roster fantôme en mémoire)
    }

    private static JsonObject paire(String id, String label) {
        JsonObject j = new JsonObject();
        j.addProperty("id", id);
        j.addProperty("label", label);
        return j;
    }

    private JsonObject json(ShinobiCharacter c, boolean actif) {
        JsonObject j = new JsonObject();
        j.addProperty("id", c.id().toString());
        j.addProperty("pid", c.hasPlayerId() ? PlayerIds.format(c.playerId()) : "");
        j.addProperty("nom", c.name());
        String clan = c.clan() == null || "None".equalsIgnoreCase(c.clan()) ? "" : c.clan();
        j.addProperty("clan", clan);
        j.addProperty("village", c.village());
        j.addProperty("villageCourt", villageCourt(c.village()));
        j.addProperty("rang", c.rank().displayName());
        j.addProperty("rangId", c.rank().name());
        j.addProperty("age", c.age());
        j.addProperty("taille", (int) Math.round(c.size() * 180));
        j.addProperty("sexe", c.sexe());
        j.addProperty("art", c.ninjaArt() == null ? "" : c.ninjaArt().name());
        j.addProperty("affinite", c.affinity() == null ? "" : c.affinity().name());
        j.addProperty("mort", c.dead());
        j.addProperty("actif", actif);
        j.addProperty("cree", creeLe(c));
        j.addProperty("niveau", c.level());
        j.addProperty("pvMax", Math.round(c.maxHp()));
        j.addProperty("chakraMax", Math.round(c.maxChakra()));
        j.addProperty("points", StatFormulas.unspent(c.rank(), c.stats()));
        j.addProperty("bonus", c.stats().bonusPoints());
        j.addProperty("techniques", c.knownAbilities().size());
        j.addProperty("blessures", c.injuries().size());
        j.addProperty("ryo", c.ryo());
        JsonArray nat = new JsonArray();
        for (ChakraAffinity a : c.chakraAffinities()) nat.add(nature(a));
        j.add("natures", nat);
        JsonArray st = new JsonArray();
        for (StatsService.Stat s : StatsService.Stat.values()) {
            JsonObject x = new JsonObject();
            x.addProperty("n", s.displayName());
            x.addProperty("v", c.stats().get(s));
            st.add(x);
        }
        j.add("stats", st);
        return j;
    }

    /** Date de l'enregistrement aux états de service (« dd/MM/yyyy »), vide si inconnue (personnage antérieur). */
    private String creeLe(ShinobiCharacter c) {
        var sr = plugin.serviceRecords();
        if (sr == null) return "";
        try {
            for (var e : sr.store().of(c.id()).all()) {
                if (e.type() == com.reborn.shinobicore.api.ServiceRecordService.Type.ENREGISTREMENT && e.at() > 0)
                    return new SimpleDateFormat("dd/MM/yyyy").format(new Date(e.at()));
            }
        } catch (RuntimeException ignored) { }
        return "";
    }

    static String villageCourt(String v) {
        if (v == null || v.isBlank()) return "";
        for (String[] x : VILLAGES) if (!x[0].isEmpty() && (x[0].equalsIgnoreCase(v) || x[1].equalsIgnoreCase(v))) return x[1];
        return v;
    }

    private static String nature(ChakraAffinity a) {
        return switch (a) {
            case KATON -> "Katon"; case SUITON -> "Suiton"; case FUTON -> "Fūton"; case DOTON -> "Doton";
            case RAITON -> "Raiton"; default -> a.displayName();
        };
    }

    static String affinite(Affinity a) {
        return a == null ? "" : switch (a) { case STRENGTH -> "Force"; case INTELLIGENCE -> "Intelligence"; case AGILITY -> "Agilité"; };
    }

    /* ================================================================ actions sur un personnage */

    private boolean droit(Player staff, int grade, int min) {
        if (grade >= min) return true;
        panel.toast(staff, "Ton grade ne permet pas cette action (" + StaffGrades.name(min) + " requis).");
        return false;
    }

    private static String motif(JsonObject in) {
        String m = StaffPanel.str(in, "motif").trim();
        return m.length() > 160 ? m.substring(0, 160) : m;
    }

    private void act(Player staff, int grade, UUID owner, JsonObject in) {
        String op = StaffPanel.str(in, "op");
        ShinobiCharacter c = perso(owner, in);
        if (c == null) { panel.toast(staff, "Personnage introuvable (supprimé ?)."); return; }
        String qui = PlayerIds.prefix(c) + c.name();
        String motif = motif(in);
        switch (op) {
            case "identite" -> { if (droit(staff, grade, G_MODIFIER)) identite(staff, owner, c, in, motif); }
            case "origine" -> { if (droit(staff, grade, G_MODIFIER)) origine(staff, owner, c, in, motif); }
            case "art" -> { if (droit(staff, grade, G_MODIFIER)) art(staff, owner, c, in, motif); }
            case "progression" -> { if (droit(staff, grade, G_PROGRESSION)) progression(staff, owner, c, in, motif); }
            case "actif" -> { if (droit(staff, grade, G_ACTIF)) forcerActif(staff, owner, c, motif); }
            case "mort", "ressusciter" -> {
                if (!droit(staff, grade, G_MORT)) return;
                if (motif.isEmpty()) { panel.toast(staff, "Motif obligatoire."); return; }
                mort(staff, owner, c, op.equals("mort"), motif);
            }
            case "reset", "suppr" -> {
                if (!droit(staff, grade, op.equals("reset") ? G_RESET : G_SUPPR)) return;
                if (motif.isEmpty()) { panel.toast(staff, "Motif obligatoire."); return; }
                if (plugin.outilsStaff() == null) { panel.toast(staff, "Outils staff indisponibles."); return; }
                String dossier = plugin.outilsStaff().perso().executer(staff, op, owner, c, motif);
                if (dossier == null) { panel.toast(staff, "Sauvegarde impossible : rien n'a été fait (voir la console)."); return; }
                panel.toast(staff, qui + (op.equals("reset") ? " remis à zéro." : " supprimé.") + " Sauvegarde faite.");
                // personnage joué : la suppression attend la déconnexion (5 ticks) ; on renvoie la liste ensuite
                if (op.equals("suppr")) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (staff.isOnline()) envoyer(staff, owner); }, 12L);
            }
            default -> { }
        }
    }

    /** Écrit l'action au journal universel (STAFF) et au journal du Poste de garde. */
    private void journal(Player staff, UUID owner, ShinobiCharacter c, String action, String libelle, String detail) {
        Journal.entree(Categorie.STAFF, action).acteur(staff).ciblePersonnage(owner, c.id()).detail(detail).ecrire();
        panel.log(staff, "staff", libelle, PlayerIds.prefix(c) + c.name() + " · " + detail);
    }

    private static String avecMotif(String detail, String motif) {
        return motif.isEmpty() ? detail : detail + " · motif : " + motif;
    }

    /** Joueur en ligne qui joue CE personnage, sinon null. */
    private Player joueur(UUID owner, ShinobiCharacter c) {
        Player p = Bukkit.getPlayer(owner);
        ShinobiCharacter a = p != null ? plugin.characters().getActive(owner) : null;
        return a != null && a.id().equals(c.id()) ? p : null;
    }

    private void identite(Player staff, UUID owner, ShinobiCharacter c, JsonObject in, String motif) {
        CharacterManager mgr = plugin.characters();
        List<String> ch = new ArrayList<>();
        String nom = StaffPanel.str(in, "nom").trim().replaceAll("\\s+", " ");
        if (!nom.isEmpty() && !nom.equals(c.name())) {
            if (nom.length() < 2 || nom.length() > 32) { panel.toast(staff, "Nom : 2 à 32 caractères."); return; }
            var autre = mgr.findByName(owner, nom);
            if (autre.isPresent() && !autre.get().id().equals(c.id())) { panel.toast(staff, "Ce compte a déjà un personnage nommé « " + nom + " »."); return; }
            ch.add("nom " + c.name() + " → " + nom);
        }
        Integer age = null;
        String sa = StaffPanel.str(in, "age").trim();
        if (!sa.isEmpty()) {
            try { age = Integer.parseInt(sa); } catch (NumberFormatException e) { panel.toast(staff, "Âge : un nombre entier."); return; }
            if (age < 0 || age > 1000) { panel.toast(staff, "Âge : entre 0 et 1000."); return; }
            if (age == c.age()) age = null; else ch.add("âge " + c.age() + " → " + age);
        }
        Double taille = null;
        String st = StaffPanel.str(in, "taille").trim().replace(',', '.');
        if (!st.isEmpty()) {
            double cm;
            try { cm = Double.parseDouble(st); } catch (NumberFormatException e) { panel.toast(staff, "Taille : un nombre en cm."); return; }
            if (cm < 45 || cm > 720) { panel.toast(staff, "Taille : entre 45 et 720 cm."); return; }
            if (Math.round(cm) != Math.round(c.size() * 180)) { taille = cm / 180.0; ch.add("taille " + Math.round(c.size() * 180) + " → " + Math.round(cm) + " cm"); }
        }
        String sexe = StaffPanel.str(in, "sexe").trim();
        if (!sexe.isEmpty() && !sexe.equals("Homme") && !sexe.equals("Femme")) sexe = "";
        if (!sexe.isEmpty() && !sexe.equals(c.sexe())) ch.add("sexe " + c.sexe() + " → " + sexe); else sexe = "";
        if (ch.isEmpty()) { panel.toast(staff, "Rien à changer."); return; }
        if (!nom.isEmpty() && !nom.equals(c.name())) c.setName(nom);
        if (age != null) c.setAge(age);
        if (taille != null) c.setSize(taille);
        if (!sexe.isEmpty()) c.setSexe(sexe);
        mgr.save(c);
        Player p = joueur(owner, c);
        if (p != null) {
            if (taille != null) mgr.reapplyStatsIfActive(c);
            CharacterDisplay.apply(p, c);
            if (!sexe.isEmpty() && plugin.characterSelect() != null) plugin.characterSelect().broadcastActive(p);
            p.sendMessage(Component.text("Le staff a modifié ton personnage : " + String.join(", ", ch) + ".", NamedTextColor.GOLD));
        }
        String d = avecMotif(String.join(" · ", ch), motif);
        journal(staff, owner, c, "PERSO_IDENTITE", "Personnage modifié", d);
        panel.toast(staff, "Identité mise à jour.");
    }

    private void origine(Player staff, UUID owner, ShinobiCharacter c, JsonObject in, String motif) {
        List<String> ch = new ArrayList<>();
        String clan = StaffPanel.str(in, "clan").trim();
        if (clan.equalsIgnoreCase("aucun") || clan.equals("-")) clan = "None";
        if (!clan.isEmpty()) {
            Clan canon = Clan.forName(clan);
            if (canon != null) clan = canon.displayName();
            if (clan.length() > 24) { panel.toast(staff, "Clan : 24 caractères au plus."); return; }
            if (!clan.equalsIgnoreCase(c.clan())) ch.add("clan " + affiche(c.clan()) + " → " + affiche(clan)); else clan = "";
        }
        String village = null;
        if (in.has("village")) {
            String v = StaffPanel.str(in, "village").trim();
            boolean connu = false;
            for (String[] x : VILLAGES) if (x[0].equals(v)) connu = true;
            if (!connu) { panel.toast(staff, "Village inconnu."); return; }
            if (!v.equalsIgnoreCase(c.village())) { village = v; ch.add("village " + affiche(villageCourt(c.village())) + " → " + affiche(villageCourt(v))); }
        }
        if (ch.isEmpty()) { panel.toast(staff, "Rien à changer."); return; }
        if (!clan.isEmpty()) c.setClan(clan);
        if (village != null) c.setVillage(village);
        plugin.characters().save(c);
        Player p = joueur(owner, c);
        if (p != null) {
            CharacterDisplay.apply(p, c);
            p.sendMessage(Component.text("Le staff a modifié ton personnage : " + String.join(", ", ch) + ".", NamedTextColor.GOLD));
        }
        journal(staff, owner, c, "PERSO_ORIGINE", "Personnage modifié", avecMotif(String.join(" · ", ch), motif));
        panel.toast(staff, "Clan et village mis à jour.");
    }

    private static String affiche(String s) { return s == null || s.isBlank() || "None".equalsIgnoreCase(s) ? "aucun" : s; }

    private void art(Player staff, UUID owner, ShinobiCharacter c, JsonObject in, String motif) {
        List<String> ch = new ArrayList<>();
        NinjaArt art = null;
        try { if (!StaffPanel.str(in, "art").isEmpty()) art = NinjaArt.valueOf(StaffPanel.str(in, "art")); }
        catch (IllegalArgumentException e) { panel.toast(staff, "Art inconnu."); return; }
        Affinity af = null;
        try { if (!StaffPanel.str(in, "affinite").isEmpty()) af = Affinity.valueOf(StaffPanel.str(in, "affinite")); }
        catch (IllegalArgumentException e) { panel.toast(staff, "Affinité inconnue."); return; }
        if (art != null && art != c.ninjaArt()) ch.add("art " + c.ninjaArt().displayName() + " → " + art.displayName()); else art = null;
        if (af != null && af != c.affinity()) ch.add("affinité " + affinite(c.affinity()) + " → " + affinite(af)); else af = null;
        if (ch.isEmpty()) { panel.toast(staff, "Rien à changer."); return; }
        if (art != null) c.setNinjaArt(art);
        if (af != null) c.setAffinity(af);
        plugin.characters().save(c);
        plugin.characters().reapplyStatsIfActive(c);
        journal(staff, owner, c, "PERSO_ART", "Personnage modifié", avecMotif(String.join(" · ", ch), motif));
        panel.toast(staff, "Art et affinité mis à jour.");
    }

    private void progression(Player staff, UUID owner, ShinobiCharacter c, JsonObject in, String motif) {
        List<String> ch = new ArrayList<>();
        Rank to = null;
        String r = StaffPanel.str(in, "rang");
        if (!r.isEmpty()) {
            try { to = Rank.valueOf(r); } catch (IllegalArgumentException e) { panel.toast(staff, "Grade inconnu."); return; }
            if (to == c.rank()) to = null; else ch.add((to.ordinal() > c.rank().ordinal() ? "promu " : "rétrogradé ") + c.rank().displayName() + " → " + to.displayName());
        }
        Integer bonus = null;
        String b = StaffPanel.str(in, "bonus").trim();
        if (!b.isEmpty()) {
            try { bonus = Integer.parseInt(b.replace("+", "")); } catch (NumberFormatException e) { panel.toast(staff, "Points bonus : un nombre entier."); return; }
            if (bonus < 0 || bonus > 500) { panel.toast(staff, "Points bonus : entre 0 et 500."); return; }
            if (bonus == c.stats().bonusPoints()) bonus = null; else ch.add("points bonus " + c.stats().bonusPoints() + " → " + bonus);
        }
        if (ch.isEmpty()) { panel.toast(staff, "Rien à changer."); return; }
        if (bonus != null) c.stats().setBonusPoints(bonus);
        if (to != null) {
            Rank from = c.rank();
            c.setRank(to);
            if (plugin.serviceRecords() != null) plugin.serviceRecords().promotion(c, from, to, false, staff.getName() + " (staff)");
            if (plugin.stats() != null) plugin.stats().onRankChanged(c);
        }
        plugin.characters().save(c);
        StatsServiceImpl.pushIfOnline(c);
        Player p = joueur(owner, c);
        if (p != null && to != null) p.sendMessage(Component.text("Le staff a changé ton grade : " + to.displayName() + ".", NamedTextColor.GOLD));
        journal(staff, owner, c, "PERSO_PROGRESSION", to != null ? "Grade changé" : "Points bonus", avecMotif(String.join(" · ", ch), motif));
        panel.toast(staff, "Progression mise à jour.");
    }

    private void forcerActif(Player staff, UUID owner, ShinobiCharacter c, String motif) {
        if (motif.isEmpty()) { panel.toast(staff, "Motif obligatoire."); return; }
        Player p = Bukkit.getPlayer(owner);
        if (p == null) { panel.toast(staff, "Le joueur doit être en ligne."); return; }
        if (c.dead()) { panel.toast(staff, c.name() + " est mort RP : ressuscite-le d'abord."); return; }
        ShinobiCharacter actuel = plugin.characters().getActive(owner);
        if (actuel != null && actuel.id().equals(c.id())) { panel.toast(staff, c.name() + " est déjà joué."); return; }
        CharacterSelectManager sel = plugin.characterSelect();
        if (sel != null && sel.isSelecting(owner)) { panel.toast(staff, "Le joueur est à l'écran de sélection : il choisit lui-même."); return; }
        String refus = actuel != null ? com.reborn.shinobicore.etat.Verrous.changementPerso(owner) : null;
        if (refus != null) { panel.toast(staff, "Impossible maintenant : " + refus); return; }
        if (!plugin.characters().setActive(p, c)) { panel.toast(staff, "Changement refusé (voir l'état du joueur)."); return; }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (sel != null) sel.broadcastActive(p);
            if (plugin.rpInventory() != null) plugin.rpInventory().broadcastCosmetics(p);
        }, 10L);
        p.sendMessage(Component.text("Le staff t'a fait passer sur " + c.name() + ".", NamedTextColor.GOLD));
        journal(staff, owner, c, "PERSO_ACTIF_FORCE", "Personnage imposé",
                avecMotif((actuel != null ? PlayerIds.prefix(actuel) + actuel.name() + " → " : "") + c.name(), motif));
        panel.toast(staff, "Le joueur joue maintenant " + c.name() + ".");
    }

    private void mort(Player staff, UUID owner, ShinobiCharacter c, boolean mort, String motif) {
        if (c.dead() == mort) { panel.toast(staff, c.name() + (mort ? " est déjà mort RP." : " n'est pas mort RP.")); return; }
        Player p = joueur(owner, c);
        c.setDead(mort);
        plugin.characters().save(c);
        if (plugin.ko() != null && p != null) plugin.ko().forceClear(owner);
        Player en = Bukkit.getPlayer(owner);
        if (en != null) en.sendMessage(Component.text(mort ? "Le staff a déclaré " + c.name() + " mort (RP)." : c.name() + " est de nouveau jouable (décision du staff).",
                mort ? NamedTextColor.RED : NamedTextColor.GOLD));
        if (mort && p != null && plugin.characterSelect() != null && plugin.characterSelect().isEnabled()) plugin.characterSelect().beginSelection(p);
        journal(staff, owner, c, mort ? "PERSO_MORT_RP" : "PERSO_RESSUSCITE", mort ? "Mort RP déclarée" : "Personnage ressuscité", avecMotif(mort ? "mort RP" : "ressuscité", motif));
        panel.toast(staff, c.name() + (mort ? " est mort RP." : " est ressuscité."));
    }

    /* ================================================================ compte : emplacements, création */

    private void emplacements(Player staff, int grade, UUID owner, JsonObject in) {
        if (!droit(staff, grade, G_EMPLACEMENTS)) return;
        String motif = motif(in);
        if (motif.isEmpty()) { panel.toast(staff, "Motif obligatoire."); return; }
        CharacterSelectManager sel = plugin.characterSelect();
        if (sel == null) { panel.toast(staff, "Sélection des personnages indisponible."); return; }
        int delta = StaffPanel.intDe(in, "delta", 0) >= 0 ? 1 : -1;
        int avant = sel.emplacements().of(owner);
        int apres = avant + delta;
        Player en = Bukkit.getPlayer(owner);
        int base = en != null ? CharacterSelectManager.slotBase(en) : 2;
        if (apres > 20) { panel.toast(staff, "20 emplacements bonus au plus."); return; }
        if (base + apres < 1) { panel.toast(staff, "Il reste au moins un emplacement."); return; }
        sel.emplacements().set(owner, apres);
        String d = avecMotif("bonus " + signe(avant) + " → " + signe(apres), motif);
        Journal.entree(Categorie.STAFF, delta > 0 ? "EMPLACEMENT_AJOUTE" : "EMPLACEMENT_RETIRE").acteur(staff).cible(owner).detail(d).ecrire();
        panel.log(staff, "staff", delta > 0 ? "Emplacement débloqué" : "Emplacement retiré", panel.store().nameOf(owner) + " · " + d);
        panel.toast(staff, delta > 0 ? "Emplacement débloqué." : "Emplacement retiré.");
    }

    private static String signe(int n) { return n > 0 ? "+" + n : String.valueOf(n); }

    private void creer(Player staff, int grade, UUID owner, JsonObject in) {
        if (!droit(staff, grade, G_CREER)) return;
        String motif = motif(in);
        String nom = StaffPanel.str(in, "nom").trim().replaceAll("\\s+", " ");
        if (motif.isEmpty()) { panel.toast(staff, "Motif obligatoire."); return; }
        if (nom.length() < 2 || nom.length() > 32) { panel.toast(staff, "Nom : 2 à 32 caractères."); return; }
        CharacterManager mgr = plugin.characters();
        if (mgr.findByName(owner, nom).isPresent()) { panel.toast(staff, "Ce compte a déjà un personnage nommé « " + nom + " »."); return; }
        ShinobiCharacter c = mgr.create(owner, nom);
        mgr.save(c);
        if (plugin.serviceRecords() != null) plugin.serviceRecords().enregistrement(c);
        Player en = Bukkit.getPlayer(owner);
        if (en != null) en.sendMessage(Component.text("Le staff t'a créé un personnage : " + nom + ".", NamedTextColor.GOLD));
        journal(staff, owner, c, "PERSO_CREE", "Personnage créé", avecMotif("créé pour " + panel.store().nameOf(owner), motif));
        panel.toast(staff, "Personnage « " + nom + " » créé" + (c.hasPlayerId() ? " (N° " + PlayerIds.format(c.playerId()) + ")" : "") + ".");
    }
}
