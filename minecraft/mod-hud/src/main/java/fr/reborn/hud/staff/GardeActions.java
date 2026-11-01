package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Catalogue des actions du Poste de garde sur un joueur (fiche, icônes au survol d'une ligne, menu « Plus » et clic
 * droit, recherche universelle) : libellé, icône au trait, raccourci d'une lettre (dans la fiche), grade minimal, mots
 * de recherche, et lancement — direct, par formulaire dans le volet ({@link GardeFormulaire}), ou par confirmation à
 * N° retapé. Tout est revérifié par le serveur (PLUGINS_REFERENCE §4 bis.2).
 */
final class GardeActions {

    /** Une action : id, libellé, icône, touche (0 = aucune), grade minimal, mots-clés de la recherche. */
    record Action(String id, String label, String icone, char touche, int grade, String mots) {
        String libelle(JsonObject p) {
            return switch (id) {
                case "freeze" -> gele(p) ? "Dégeler" : label;
                case "blind" -> StaffScreen.bool(p, "noir") ? "Lever l'écran noir" : label;
                case "watch" -> StaffScreen.bool(p, "watching") ? "Fin d'observation" : label;
                case "voix" -> voixCoupee(p) ? "Rétablir la voix" : label;
                default -> label;
            };
        }
    }

    static final Action ALLER = new Action("tp", "Aller à", "aller", 'A', 1, "aller tp teleporter rejoindre");
    static final Action RAMENER = new Action("bring", "Ramener", "ramener", 'R', 2, "ramener bring");
    static final Action OBSERVER = new Action("watch", "Observer", "observer", 'O', 2, "observer spectateur surveiller");
    static final Action GELER = new Action("freeze", "Geler", "geler", 'G', 2, "geler freeze degeler bloquer");
    static final Action SACOCHE = new Action("invsee", "Sacoche", "sac", 'S', 2, "sacoche inventaire sac invsee voirsac");
    static final Action NOIR = new Action("blind", "Écran noir", "noir", 'N', 2, "ecran noir blind aveugler");
    static final Action NOTER = new Action("note", "Noter", "noter", 'T', 1, "noter note etiquette tag");
    static final Action RADIO = new Action("radio", "Écouter la radio…", "radio", 'E', 1, "ecouter radio escouade");
    /** Couper la voix (31/10, mute de Plasmo Voice : proximité et radio) ; bascule en « Rétablir la voix ». */
    static final Action VOIX = new Action("voix", "Couper la voix…", "micro_off", 'V', 2, "voix vocal micro muet couper retablir rendre vmute");
    static final Action ANNONCE = new Action("announce", "Annonce perso…", "annonce", 'M', 1, "annonce message convoquer");
    static final Action EMOTE = new Action("emote", "Forcer une emote…", "emote", (char) 0, 2, "emote forcer animation");
    static final Action TENUE = new Action("outfit", "Imposer une tenue…", "tenue", (char) 0, 2, "tenue habit imposer");
    static final Action REVEIL = new Action("revive", "Réveiller", "reveiller", (char) 0, 2, "reveiller relever ko");
    static final Action ATA = new Action("lift_ata", "Lever l'ATA", "coeur", (char) 0, 2, "ata lever soigner");
    static final Action REVENIR = new Action("back", "Revenir", "revenir", 'B', 1, "revenir retour back");
    static final Action RENVOYER = new Action("return", "Le renvoyer", "renvoyer", (char) 0, 2, "renvoyer return");
    static final Action SANCTION = new Action("sanction", "Sanctionner…", "sanction", 'X', 1, "sanctionner sanction ban mute avertir");
    static final Action SEISME = new Action("shake", "Séisme…", "seisme", (char) 0, 3, "seisme secouer trembler");
    static final Action RESET = new Action("perso_reset", "Remettre à zéro…", "zero", (char) 0, 3, "remettre zero reset");
    static final Action SUPPR = new Action("perso_suppr", "Supprimer le perso…", "suppr", (char) 0, 3, "supprimer perso");
    static final Action FICHE = new Action("fiche", "Ouvrir la fiche", "joueurs", (char) 0, 1, "fiche ouvrir profil");

    /** Les huit actions éclair de la fiche (grille 4 × 2, la huitième case est « Plus »). */
    static final Action[] GRILLE = {ALLER, RAMENER, OBSERVER, GELER, SACOCHE, NOIR, NOTER};
    /** Menu « Plus » (null = séparateur). */
    static final Action[] PLUS = {RADIO, VOIX, ANNONCE, EMOTE, TENUE, REVEIL, ATA, REVENIR, RENVOYER, null, SANCTION, null,
            SEISME, RESET, SUPPR};
    /** Icônes au survol d'une ligne de la liste (de gauche à droite ; « Plus » ajouté à la fin). */
    static final Action[] SURVOL = {ALLER, RAMENER, GELER};

    static final Action[] TOUTES = {ALLER, RAMENER, OBSERVER, GELER, SACOCHE, NOIR, NOTER, RADIO, VOIX, ANNONCE, EMOTE,
            TENUE, REVEIL, ATA, REVENIR, RENVOYER, SANCTION, SEISME, RESET, SUPPR};

    private GardeActions() {}

    static boolean gele(JsonObject p) { return StaffScreen.bool(p, "gele") || StaffScreen.bool(p, "frozen"); }

    /* ================================================================ voix coupée (31/10, PLUGINS_REFERENCE §4 quater.1) */

    /** Voix coupée par le staff (mute de Plasmo Voice, champ {@code voixCoupee} de la fiche et de la liste). */
    static boolean voixCoupee(JsonObject p) { return StaffScreen.bool(p, "voixCoupee"); }

    /**
     * « Voix coupée — jusqu'à 21:45 — motif » à l'heure du poste du staff (« jusqu'à demain 21:45 », « jusqu'au 02/11
     * à 21:45 » ; « sans fin » pour un {@code /vmute} permanent, {@code voixFin} = -1) ; null si la voix est libre.
     */
    static String voixEtat(JsonObject p) {
        if (!voixCoupee(p)) return null;
        long fin = StaffScreen.lng(p, "voixFin");
        StringBuilder b = new StringBuilder("Voix coupée");
        if (fin < 0) b.append(" — sans fin");
        else if (fin > 0) b.append(" — ").append(jusqua(fin));
        String motif = StaffScreen.str(p, "voixMotif").trim();
        if (!motif.isEmpty()) b.append(" — ").append(motif);
        return b.toString();
    }

    private static String jusqua(long fin) {
        ZonedDateTime f = Instant.ofEpochMilli(fin).atZone(ZoneId.systemDefault());
        long jours = ChronoUnit.DAYS.between(LocalDate.now(f.getZone()), f.toLocalDate());
        String hm = String.format(Locale.ROOT, "%02d:%02d", f.getHour(), f.getMinute());
        if (jours <= 0) return "jusqu'à " + hm;
        if (jours == 1) return "jusqu'à demain " + hm;
        return String.format(Locale.ROOT, "jusqu'au %02d/%02d à %s", f.getDayOfMonth(), f.getMonthValue(), hm);
    }

    /** Morceau « Voix coupée · 12 min » / « · sans fin » que le serveur ajoute à {@code state} (EtatTexte). */
    private static final Pattern VOIX_ETAT = Pattern.compile("(^| · )Voix coupée( · (sans fin|\\d+ min))?");

    /** {@code state} sans la voix coupée : la fiche lui donne sa propre ligne, sous l'état. */
    static String sansVoix(String st) {
        if (st == null) return "";
        if (!st.contains("Voix coupée")) return st;
        String r = VOIX_ETAT.matcher(st).replaceAll("");
        return r.startsWith(" · ") ? r.substring(3) : r;
    }

    /** Menu du clic droit sur une ligne : la fiche, les actions éclair, puis celles du menu « Plus ». */
    static List<Action> menuLigne() {
        List<Action> l = new ArrayList<>();
        l.add(FICHE);
        l.add(null);
        for (Action a : GRILLE) l.add(a);
        l.add(null);
        for (Action a : PLUS) l.add(a);
        return l;
    }

    /** Raison pour laquelle l'action est indisponible pour ce grade / ce joueur (null = disponible). */
    static String refus(Action a, int grade, JsonObject p) {
        if (grade < a.grade()) return "Réservé au grade " + StaffScreen.gradeName(a.grade()) + " et plus.";
        if (a == REVENIR && p != null && p.has("back") && !StaffScreen.bool(p, "back")) return "Rien à annuler : tu n'as pas bougé.";
        if (a == RENVOYER && p != null && !StaffScreen.bool(p, "retour")) return "Ce joueur n'a pas été ramené.";
        if ((a == RESET || a == SUPPR || a == NOTER) && StaffScreen.str(p, "pid").isEmpty() && a != NOTER)
            return "Pas de personnage actif.";
        if (a == NOTER && StaffScreen.str(p, "pid").isEmpty()) return "Pas de N° : aucun personnage actif.";
        // rétablir marche hors ligne (le mute de Plasmo est rangé par UUID) ; couper exige le joueur en jeu
        if (a == VOIX && p != null && !voixCoupee(p) && p.has("online") && !StaffScreen.bool(p, "online"))
            return "Hors ligne : la voix ne se coupe qu'en jeu.";
        return null;
    }

    /** Actions dont un mot-clé commence par ce terme (≥ 2 lettres, sans accents). */
    static List<Action> correspondant(String terme) {
        List<Action> out = new ArrayList<>();
        String t = sans(terme);
        if (t.length() < 2) return out;
        for (Action a : TOUTES) {
            for (String m : a.mots().split(" ")) if (m.startsWith(t)) { out.add(a); break; }
        }
        return out;
    }

    static String sans(String s) {
        return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    /* ================================================================ lancement */

    /**
     * Lance l'action sur le joueur {@code p} (ligne de la liste, fiche ou résultat de recherche). Les actions à
     * paramètre ouvrent la fiche puis le formulaire dans le volet.
     */
    static void lancer(StaffScreen s, Action a, JsonObject p) {
        if (p == null) return;
        String uuid = StaffScreen.str(p, "uuid");
        String refus = refus(a, StaffScreen.grade(StaffClient.snapshot()), p);
        if (refus != null) { StaffClient.toast(refus); RebornSounds.deny(); return; }
        String nom = StaffScreen.name(p);
        switch (a.id()) {
            case "fiche" -> s.openProfile(uuid);
            case "tp", "bring", "watch", "invsee", "revive", "lift_ata", "back", "return" -> envoyer(s, a.id(), p, Map.of());
            case "freeze" -> {
                if (gele(p)) { envoyer(s, "unfreeze", p, Map.of()); return; }
                s.ficheFormulaire(p, f -> f.ouvrir("geler", "Geler " + nom, "Ne bouge plus, même après une déconnexion ou un redémarrage.",
                        "Geler", false, null, List.of(GardeFormulaire.Champ.texte("m", "Motif (facultatif, vu du staff)", "Refus d'obtempérer…", false)),
                        List.of("Inscrit au journal, visible des autres staffs."), v -> envoyer(s, "freeze", p, v)));
            }
            case "blind" -> {
                if (StaffScreen.bool(p, "noir")) { envoyer(s, "blind", p, Map.of()); return; }
                s.ficheFormulaire(p, f -> f.ouvrir("noir", "Écran noir — " + nom, "Fondu au noir sur son écran. Le tchat reste lisible.",
                        "Plonger dans le noir", false, null, List.of(
                                GardeFormulaire.Champ.texte("m", "Texte (facultatif)", "Vous perdez connaissance…", false),
                                GardeFormulaire.Champ.choix("s", "Durée", new String[][]{{"0", "Jusqu'à levée"}, {"10", "10 s"}, {"30", "30 s"}, {"120", "2 min"}})),
                        List.of(), v -> envoyer(s, "blind", p, v)));
            }
            case "note" -> s.ficheFormulaire(p, f -> f.ouvrir("noter", "Noter " + nom, "Vue du staff seulement ; suit le N° même après un changement de nom.",
                    "Ajouter la note", false, null, List.of(
                            GardeFormulaire.Champ.choix("etiquette", "Étiquette", new String[][]{{"", "Aucune"}, {"surveillé", "Surveillé"},
                                    {"avertissement", "Avertissement"}, {"event", "Event"}, {"comportement", "Comportement"}}),
                            GardeFormulaire.Champ.texte("texte", "Note", "Averti pour HRP au marché…", true)),
                    List.of(), v -> s.fiche.ajouterNote(StaffScreen.str(p, "pid"), v.get("texte"), v.get("etiquette"))));
            case "announce" -> s.ficheFormulaire(p, f -> f.ouvrir("annonce", "Annonce à " + nom, "Une ligne discrète en haut de son écran. Ton nom n'est pas montré.",
                    "Envoyer", false, null, List.of(
                            GardeFormulaire.Champ.texte("m", "Message", "Le Hokage te convoque.", true),
                            GardeFormulaire.Champ.choix("s", "Durée", new String[][]{{"8", "8 s"}, {"5", "5 s"}, {"15", "15 s"}, {"30", "30 s"}})),
                    List.of(), v -> envoyer(s, "announce", p, v)));
            case "emote" -> s.ficheFormulaire(p, f -> f.ouvrir("emote", "Forcer une emote — " + nom, null, "Jouer", false, null,
                    List.of(GardeFormulaire.Champ.texte("v", "Emote", "Id de l'emote, ou « stop »", true)), List.of(),
                    v -> envoyer(s, "emote", p, v)));
            case "outfit" -> s.ficheFormulaire(p, f -> f.ouvrir("tenue", "Imposer une tenue — " + nom, null, "Habiller", false, null,
                    List.of(GardeFormulaire.Champ.texte("v", "Tenue", "Id Nexo, « main » ou « retirer »", true)), List.of(),
                    v -> envoyer(s, "outfit", p, v)));
            case "shake" -> s.ficheFormulaire(p, f -> f.ouvrir("seisme", "Séisme sur " + nom, "Secoue sa caméra seulement (le son vient du serveur).",
                    "Secouer", false, null, List.of(
                            GardeFormulaire.Champ.choix("v", "Force", new String[][]{{"5", "Moyenne"}, {"3", "Légère"}, {"8", "Forte"}}),
                            GardeFormulaire.Champ.choix("s", "Durée", new String[][]{{"4", "4 s"}, {"2", "2 s"}, {"8", "8 s"}})),
                    List.of(), v -> envoyer(s, "shake", p, v)));
            // personnage joué, visé par son N° ; même formulaire que la section Personnages (motif + N° retapé)
            case "perso_reset", "perso_suppr" -> s.ficheFormulaire(p, f -> s.fiche.persos.lourdParPid(p,
                    "perso_suppr".equals(a.id()) ? "suppr" : "reset"));
            case "radio" -> s.ficheFormulaire(p, f -> GardeFormulaire.ecouteRadio(f, p));
            // mute de Plasmo Voice (31/10) : rien n'est enregistré, seul le motif va au journal
            case "voix" -> {
                if (voixCoupee(p)) { envoyer(s, "voix_retablir", p, Map.of()); return; }
                s.ficheFormulaire(p, f -> f.ouvrir("micro_off", "Couper la voix — " + nom, "Il n'est plus entendu (proximité et radio). Le tchat reste ouvert.",
                        "Couper la voix", false, null, List.of(
                                GardeFormulaire.Champ.texte("m", "Motif (obligatoire)", "Cris et insultes en vocal…", true),
                                GardeFormulaire.Champ.choix("d", "Durée", new String[][]{{"5", "5 min"}, {"15", "15 min"}, {"30", "30 min"},
                                        {"60", "1 h"}, {"1440", "1 jour"}}, 1)),
                        List.of("Inscrit au journal, visible des autres staffs.", "Rien n'est enregistré de sa voix."),
                        v -> envoyer(s, "voix_couper", p, v)));
            }
            case "sanction" -> s.ficheSanctions(uuid);
            default -> { }
        }
    }

    /**
     * Action demandée hors du Poste de garde (menu R › Staff), lancée par {@link StaffClient} à l'arrivée de la fiche :
     * comme {@link #lancer}, sans bascule. Le menu R suit Plasmo, qui peut avoir un temps de retard sur la fiche :
     * « Couper la voix… » sur une voix déjà coupée ne la rétablit pas, la fiche montre la coupure et un mot le dit.
     */
    static void lancerDemandee(StaffScreen s, String id, JsonObject p) {
        for (Action a : TOUTES) {
            if (!a.id().equals(id)) continue;
            if (a == VOIX && voixCoupee(p)) { StaffClient.toast("Sa voix est déjà coupée."); return; }
            lancer(s, a, p);
            return;
        }
    }

    /** Envoie {@code act} au serveur (cible par UUID et N°). */
    static void envoyer(StaffScreen s, String op, JsonObject p, Map<String, String> v) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "act");
        o.addProperty("op", op);
        o.addProperty("t", StaffScreen.str(p, "uuid"));
        if (!StaffScreen.str(p, "pid").isEmpty()) o.addProperty("pid", StaffScreen.str(p, "pid"));
        if (p.has("_alerte")) o.addProperty("alerte", StaffScreen.str(p, "_alerte"));
        for (Map.Entry<String, String> e : v.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            // s (secondes) et d (minutes, voix_couper) en entiers ; v seulement s'il est numérique
            if ("s".equals(e.getKey()) || "d".equals(e.getKey()) || "v".equals(e.getKey()) && e.getValue().chars().allMatch(Character::isDigit)) {
                try { o.addProperty(e.getKey(), Integer.parseInt(e.getValue())); continue; } catch (NumberFormatException ignored) { }
            }
            o.addProperty(e.getKey(), e.getValue());
        }
        StaffClient.send(o);
        RebornSounds.uiClick();
    }
}
