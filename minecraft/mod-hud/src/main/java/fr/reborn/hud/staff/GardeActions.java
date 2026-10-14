package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    static final Action[] PLUS = {RADIO, ANNONCE, EMOTE, TENUE, REVEIL, ATA, REVENIR, RENVOYER, null, SANCTION, null,
            SEISME, RESET, SUPPR};
    /** Icônes au survol d'une ligne de la liste (de gauche à droite ; « Plus » ajouté à la fin). */
    static final Action[] SURVOL = {ALLER, RAMENER, GELER};

    static final Action[] TOUTES = {ALLER, RAMENER, OBSERVER, GELER, SACOCHE, NOIR, NOTER, RADIO, ANNONCE, EMOTE, TENUE,
            REVEIL, ATA, REVENIR, RENVOYER, SANCTION, SEISME, RESET, SUPPR};

    private GardeActions() {}

    static boolean gele(JsonObject p) { return StaffScreen.bool(p, "gele") || StaffScreen.bool(p, "frozen"); }

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
            case "perso_reset", "perso_suppr" -> {
                String pid = StaffScreen.str(p, "pid");
                boolean suppr = "perso_suppr".equals(a.id());
                s.ficheFormulaire(p, f -> f.ouvrir(suppr ? "suppr" : "zero", (suppr ? "Supprimer " : "Remettre à zéro ") + nom + " ?",
                        suppr ? "Supprime le personnage. Son N° ne sera jamais réutilisé. Irréversible."
                                : "Stats, techniques, inventaire, ryo, grade. Une sauvegarde est faite avant. Irréversible.",
                        suppr ? "Supprimer" : "Remettre à zéro", true, pid.isEmpty() ? StaffScreen.str(p, "name") : pid, List.of(),
                        List.of("Inscrit au journal, visible des autres staffs."), v -> commandePerso(a.id(), p)));
            }
            case "radio" -> s.ficheFormulaire(p, f -> GardeFormulaire.ecouteRadio(f, p));
            case "sanction" -> s.ficheSanctions(uuid);
            default -> { }
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
            if ("s".equals(e.getKey()) || "v".equals(e.getKey()) && e.getValue().chars().allMatch(Character::isDigit)) {
                try { o.addProperty(e.getKey(), Integer.parseInt(e.getValue())); continue; } catch (NumberFormatException ignored) { }
            }
            o.addProperty(e.getKey(), e.getValue());
        }
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    /**
     * Remise à zéro / suppression : commandes {@code /persoreset} et {@code /persosuppr} (Admin), qui demandent
     * « confirmer » sous 60 s ; la confirmation a été faite dans le volet (N° retapé).
     */
    private static void commandePerso(String op, JsonObject p) {
        String pid = StaffScreen.str(p, "pid");
        String ref = pid.isEmpty() ? StaffScreen.str(p, "name") : "#" + pid;
        String cmd = ("perso_reset".equals(op) ? "persoreset " : "persosuppr ") + ref;
        if (StaffClient.demo) {
            JsonObject o = new JsonObject();
            o.addProperty("a", "act");
            o.addProperty("op", op);
            StaffClient.send(o);
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.connection.sendCommand(cmd);
        mc.player.connection.sendCommand(cmd + " confirmer");
        RebornSounds.confirm();
    }
}
