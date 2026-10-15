package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Formulaire <b>dans le volet même</b> (maquette A_02b : « l'action choisie s'ouvre dans le volet, pas de fenêtre en
 * plus ») : icône et titre, une ligne d'explication, champs (texte libre ou choix en mots soulignés), notes, puis
 * « Annuler » en mot et l'action en plaque laquée fine « … · Entrée ». Sert aux actions à paramètre (gel, écran noir,
 * annonce, note, emote, tenue, séisme, écoute radio), aux confirmations (annonce globale, séisme, suppression d'une
 * note, validation d'une mise à mort) et aux actions destructives, qui restent éteintes tant que le N° n'est pas
 * retapé.
 */
final class GardeFormulaire {

    /**
     * Un champ : texte libre ({@code choix == null}, {@code valeur} = texte déjà saisi à l'ouverture) ou choix en mots
     * ({id, libellé}…, {@code defaut} = indice).
     */
    record Champ(String cle, String label, String hint, String[][] choix, boolean requis, int defaut, String valeur) {
        static Champ texte(String cle, String label, String hint, boolean requis) { return new Champ(cle, label, hint, null, requis, 0, ""); }
        /** Texte pré-rempli (valeur actuelle à modifier). */
        static Champ texte(String cle, String label, String hint, boolean requis, String valeur) { return new Champ(cle, label, hint, null, requis, 0, valeur == null ? "" : valeur); }
        static Champ choix(String cle, String label, String[][] choix) { return new Champ(cle, label, null, choix, false, 0, ""); }
        static Champ choix(String cle, String label, String[][] choix, int defaut) { return new Champ(cle, label, null, choix, false, defaut, ""); }
    }

    private final StaffScreen s;
    private final EditBox[] boites = new EditBox[4];
    private EditBox exigerBox;
    private boolean actif;
    private String icone = "noter", titre = "", texte = "", bouton = "Valider", exiger, eteint;
    private boolean danger;
    private List<Champ> liste = List.of();
    private List<String> notes = List.of();
    private final Map<String, String> valeurs = new HashMap<>();
    private Consumer<Map<String, String>> action;

    GardeFormulaire(StaffScreen s) { this.s = s; }

    void init() {
        for (int i = 0; i < boites.length; i++) boites[i] = s.box(boites[i], "", 200);
        exigerBox = s.box(exigerBox, "N° du personnage", 12);
    }

    boolean actif() { return actif; }

    void fermer() {
        actif = false;
        for (EditBox b : boites) if (b != null) b.setFocused(false);
    }

    /**
     * Ouvre le formulaire. {@code exiger} : N° à retaper pour confirmer (comparé sans # ni zéros de tête) ou null ;
     * {@code danger} : action irréversible.
     */
    void ouvrir(String icone, String titre, String texte, String bouton, boolean danger, String exiger, List<Champ> champs,
                List<String> notes, Consumer<Map<String, String>> action) {
        this.icone = icone;
        this.titre = titre;
        this.texte = texte == null ? "" : texte;
        this.bouton = bouton;
        this.danger = danger;
        this.exiger = exiger;
        this.eteint = null;
        this.liste = new ArrayList<>(champs);
        this.notes = notes == null ? List.of() : notes;
        this.action = action;
        valeurs.clear();
        int k = 0;
        EditBox premier = null;
        for (Champ c : liste) {
            if (c.choix() != null) valeurs.put(c.cle(), c.choix()[Math.min(c.defaut(), c.choix().length - 1)][0]);
            else if (k < boites.length) {
                boites[k].setValue(c.valeur());
                s.indice(boites[k], c.hint());
                if (premier == null) premier = boites[k];
                k++;
            }
        }
        exigerBox.setValue("");
        if (premier == null && exiger != null) premier = exigerBox;
        s.menu = null;
        actif = true;
        if (premier != null) { s.setFocused(premier); premier.setFocused(true); }
        RebornSounds.uiClick();
    }

    /** Écoute radio d'un joueur (A_02b) : prête côté mod ; éteinte tant que le serveur n'a pas {@code /radio}. */
    static void ecouteRadio(GardeFormulaire f, JsonObject p) {
        boolean dispo = radioDisponible();
        String nom = StaffScreen.name(p);
        String pid = StaffScreen.str(p, "pid");
        String ref = pid.isEmpty() ? StaffScreen.str(p, "name") : "#" + pid;
        f.ouvrir("radio", "Écouter l'escouade de " + nom,
                dispo ? "Tu entends l'escouade comme un membre, sans pouvoir parler." : "La radio n'est pas encore sur ce serveur : l'écoute s'activera avec elle.",
                "Écouter", false, null, List.of(
                        GardeFormulaire.Champ.texte("m", "Motif (obligatoire)", "Plainte n° 212 : harcèlement radio", true),
                        GardeFormulaire.Champ.choix("d", "Durée", new String[][]{{"5", "5"}, {"10", "10"}, {"15", "15"}, {"30", "30 min"}}, 1)),
                List.of("Inscrit au journal, visible des autres staffs.", "Le joueur n'est pas prévenu. Rien n'est enregistré."),
                v -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null && dispo)
                        mc.player.connection.sendCommand("radio ecouter " + ref + " " + v.get("d") + " " + v.get("m"));
                });
        if (!dispo) f.eteint = "Bientôt";
    }

    /** Le serveur connaît-il la commande {@code /radio} (docs/gameplay/RADIO.md §8) ? */
    static boolean radioDisponible() {
        if (fr.reborn.hud.radio.RadioClient.serveur()) return true;   // ShinobiRadio a parlé sur reborn:radio
        var c = Minecraft.getInstance().getConnection();
        try { return c != null && c.getCommands().getRoot().getChild("radio") != null; }
        catch (RuntimeException e) { return false; }
    }

    boolean possede(EditBox b) {
        if (b == exigerBox) return true;
        for (EditBox x : boites) if (x == b) return true;
        return false;
    }

    void debugValeur(int i, String v) { if (i < boites.length) boites[i].setValue(v); }

    void debugExiger(String v) { exigerBox.setValue(v); }

    private boolean valide() {
        if (eteint != null) return false;
        int k = 0;
        for (Champ c : liste) {
            if (c.choix() != null) continue;
            if (k < boites.length && c.requis() && boites[k].getValue().isBlank()) return false;
            k++;
        }
        if (exiger != null) {
            String a = StaffClient.normPid(exigerBox.getValue()), b = StaffClient.normPid(exiger);
            return !a.isEmpty() && a.equalsIgnoreCase(b);
        }
        return true;
    }

    boolean entree() {
        if (!actif) return false;
        if (!valide()) { RebornSounds.deny(); return true; }
        valider();
        return true;
    }

    private void valider() {
        Map<String, String> v = new HashMap<>(valeurs);
        int k = 0;
        for (Champ c : liste) if (c.choix() == null && k < boites.length) v.put(c.cle(), boites[k++].getValue().trim());
        actif = false;
        if (action != null) action.accept(v);
        RebornSounds.confirm();
    }

    /** Dessine le formulaire à partir de y (GUI) dans la largeur w ; renvoie le bas. */
    int dessiner(GardeArt.Pinceau pz, int x, int y, int w) {
        pz.icone(icone, x, y + 1, 8, danger ? ROUGE : OR);
        s.txt(titre, x + 14, y + 5, w - 14, 15, CREME, true);
        y += 12;
        if (!texte.isEmpty()) {
            List<String> l = s.wrap(texte, w - 14, 12);
            for (String li : l) { pz.texte(li, x + 14, y + 3, 12, danger ? ROUGE : GRIS_2, false); y += 9; }
            y += 4;
        } else y += 2;
        int k = 0;
        for (Champ c : liste) {
            if (c.choix() == null) {
                pz.texte(c.label(), x, y + 3, 12, GRIS, false);
                y += 8;
                if (k < boites.length) s.champ(boites[k++], x, y, w, null);
                y += 23;
            } else {
                float lw = pz.largeur(c.label(), 12, false);
                pz.texte(c.label(), x, y + 3, 12, GRIS, false);
                float ox = x + Math.max(30, lw + 9);
                for (String[] ch : c.choix()) {
                    float cw = pz.largeur(ch[1], 13, true);
                    if (ox + cw > x + w) { ox = x + Math.max(30, lw + 9); y += 12; }
                    boolean on = ch[0].equals(valeurs.get(c.cle()));
                    ox += s.mot(ch[1], ox, y + 3, 13, on, () -> valeurs.put(c.cle(), ch[0])) + 9;
                }
                y += 15;
            }
        }
        if (exiger != null) {
            pz.texte("Pour confirmer, retape le N° « " + exiger + " »", x, y + 3, 12, ROUGE, false);
            y += 8;
            s.champ(exigerBox, x, y, Math.min(110, w), null);
            y += 23;
        }
        for (String n : notes) {
            for (String li : s.wrap(n, w, 12)) { pz.texte(li, x, y + 3, 12, GRIS_2, false); y += 9; }
        }
        y += 7;
        boolean ok = valide();
        String lab = eteint != null ? eteint : bouton + "  ·  Entrée";
        int bw = s.largeurBouton(lab);
        int bx = x + w - bw;
        s.refusVerrou = eteint != null ? "La radio n'existe pas encore sur ce serveur."
                : exiger != null ? "Retape le N° du personnage pour confirmer." : "Remplis les champs obligatoires.";
        s.bouton(bx, y, bw, lab, true, this::valider, !ok);
        s.refusVerrou = null;
        s.motCouleur("Annuler", bx - 10 - pz.largeur("Annuler", 13, false), y + 8.5f, 13, GRIS, this::fermer);
        return y + 17 + 6;
    }
}
