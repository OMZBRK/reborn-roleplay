package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import fr.reborn.hud.recit.Recit;
import fr.reborn.hud.ui.TexteRaster;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Zone d'écriture de l'outil « Narration » du volet (lot 2) : texte sur plusieurs lignes, retour à la ligne
 * automatique, <b>une couleur par caractère</b> parmi celles de la narration (blanc, rouge, or, ombre, gris : les clés du
 * contrat, PLUGINS_REFERENCE §4 bis.3 bis), sélection au clavier (Maj + flèches, Ctrl+A) et à la souris (clic, Maj + clic,
 * glisser), copier / couper / coller. Une pastille colore la sélection, ou la suite de la frappe sans sélection. Même
 * mécanique que la zone de la cloche ({@code communication/Editeur}), peinte au pinceau du volet (Inter 14).
 *
 * <p>Le texte part en <b>segments</b> {@code [texte, clé]} ({@link #segments}, {@link #json}), jamais en balises : espaces
 * réduites à une, aucune autour d'un retour à la ligne ni aux bouts, trois retours à la ligne ou plus ramenés à deux
 * (paragraphe), comme le nettoyage du serveur ; deux segments voisins n'ont jamais la même couleur. La mise en page n'est
 * refaite que quand le texte ou la largeur change ; les morceaux peints sont gardés d'une image à l'autre.
 */
final class GardeEditeur {

    /** Clés de couleur du contrat, dans l'ordre des pastilles ; libellés des pastilles. */
    static final String[] CLES = {"", "rouge", "or", "ombre", "gris"};
    static final String[] NOMS = {"Blanc", "Rouge", "Or", "Ombre", "Gris"};

    /** Fond de la sélection (gris chaud, lisible sous le blanc, l'or et le rouge). */
    private static final int SELECTION = 0xFF4A3D42;

    /** Taille du texte (px de maquette à l'échelle 2) et pas des lignes (GUI). */
    static final int TAILLE = 14, PAS = 10;

    private final StringBuilder txt = new StringBuilder();
    private final StringBuilder col = new StringBuilder();   // un chiffre par caractère : indice dans CLES
    final int max;
    private int cur, ancre = -1, attente = -1;
    private long bouge = System.currentTimeMillis();
    /** Change à chaque modification (texte ou couleur) : segments et mise en page recalculés à la demande. */
    private int version;

    // mise en page (refaite quand le texte, la largeur ou l'origine changent)
    private final List<int[]> lignes = new ArrayList<>();
    private final List<String> morceaux = new ArrayList<>();
    private final List<float[]> poses = new ArrayList<>();   // {x, ligne, indice de couleur}
    /** Position GUI de chaque indice depuis le début de sa ligne (mise en page). */
    private float[] xs = new float[64];
    private GardeArt.Pinceau pz;
    private float x0, y0, largeur = -1;
    private int versionPage = -1;
    // segments du texte nettoyé, gardés jusqu'à la prochaine modification (l'aperçu les compare par référence)
    private List<Recit.Segment> segs = List.of();
    private int versionSegs = -1;

    GardeEditeur(int max) {
        this.max = max;
    }

    /* ================================================================ contenu */

    int longueur() { return txt.length(); }

    boolean vide() { return segments().isEmpty(); }

    int version() { return version; }

    void effacer() {
        txt.setLength(0);
        col.setLength(0);
        cur = 0;
        ancre = -1;
        attente = -1;
        version++;
    }

    /** Remplace le texte par ces segments (banc, reprise d'un texte). */
    void poser(List<Recit.Segment> l) {
        effacer();
        for (Recit.Segment s : l) {
            int c = indice(s.couleur());
            for (int i = 0; i < s.texte().length() && txt.length() < max; i++) {
                char ch = s.texte().charAt(i);
                if (ch == '\r') continue;
                txt.append(ch);
                col.append((char) ('0' + c));
            }
        }
        cur = txt.length();
        version++;
    }

    /** Banc : sélection [a, b) (curseur en b). */
    void debugSelection(int a, int b) {
        ancre = Math.max(0, Math.min(txt.length(), a));
        cur = Math.max(0, Math.min(txt.length(), b));
        attente = -1;
    }

    static int indice(String cle) {
        for (int i = 0; i < CLES.length; i++) if (CLES[i].equals(cle)) return i;
        return 0;
    }

    /** Couleur d'affichage d'un indice (blanc = la crème du volet, les autres : les teintes vues par les joueurs). */
    static int teinte(int i) { return i == 0 ? CREME : 0xFF000000 | TexteRaster.couleur(CLES[i]); }

    /**
     * Segments {@code [texte, clé]} du texte nettoyé comme par le serveur (voir la classe) ; la même liste tant que rien ne
     * change, vide si le texte est blanc.
     */
    List<Recit.Segment> segments() {
        if (versionSegs == version) return segs;
        versionSegs = version;
        StringBuilder t = new StringBuilder(txt.length());
        StringBuilder c = new StringBuilder(txt.length());
        for (int i = 0; i < txt.length(); i++) {
            char ch = txt.charAt(i);
            char cc = col.charAt(i);
            if (Character.isSpaceChar(ch) && ch != ' ' && ch != ' ' && ch != ' ' && ch != ' ') ch = ' ';
            int n = t.length();
            char prec = n == 0 ? '\n' : t.charAt(n - 1);
            if (ch == ' ' && (prec == ' ' || prec == '\n')) continue;          // espaces réduites, aucune en début de ligne
            if (ch == '\n') {
                while (t.length() > 0 && t.charAt(t.length() - 1) == ' ') { t.setLength(t.length() - 1); c.setLength(c.length() - 1); }
                n = t.length();
                if (n == 0) continue;                                           // aucun retour à la ligne en tête
                if (n >= 2 && t.charAt(n - 1) == '\n' && t.charAt(n - 2) == '\n') continue;   // deux au plus
            }
            t.append(ch);
            c.append(cc);
        }
        while (t.length() > 0 && Character.isWhitespace(t.charAt(t.length() - 1))) { t.setLength(t.length() - 1); c.setLength(c.length() - 1); }
        List<Recit.Segment> l = new ArrayList<>();
        int i = 0;
        while (i < t.length()) {
            int j = i;
            while (j < t.length() && c.charAt(j) == c.charAt(i)) j++;
            l.add(new Recit.Segment(t.substring(i, j), CLES[c.charAt(i) - '0']));
            i = j;
        }
        segs = l.isEmpty() ? List.of() : List.copyOf(l);
        return segs;
    }

    /** Segments au format du canal : {@code [[texte, clé]…]}. */
    JsonArray json() {
        JsonArray a = new JsonArray();
        for (Recit.Segment s : segments()) {
            JsonArray x = new JsonArray();
            x.add(s.texte());
            x.add(s.couleur());
            a.add(x);
        }
        return a;
    }

    /** Points de code du texte tel qu'il partira (compteur, durée automatique). */
    int caracteres() {
        int n = 0;
        for (Recit.Segment s : segments()) n += s.texte().codePointCount(0, s.texte().length());
        return n;
    }

    /** Texte à plat, « / » pour un retour à la ligne (résumé de la confirmation). */
    String aPlat() {
        StringBuilder b = new StringBuilder();
        for (Recit.Segment s : segments()) b.append(s.texte());
        return b.toString().replace("\n\n", " / ").replace('\n', ' ');
    }

    /* ================================================================ couleur */

    private int couleurCourante() {
        if (attente >= 0) return attente;
        if (cur > 0) return col.charAt(cur - 1) - '0';
        return col.length() > 0 ? col.charAt(0) - '0' : 0;
    }

    /** Pastille allumée : couleur de toute la sélection (-1 si mêlée), sinon celle de la frappe. */
    int couleurActive() {
        if (!selection()) return couleurCourante();
        int a = Math.min(cur, ancre), b = Math.max(cur, ancre);
        int c = col.charAt(a) - '0';
        for (int i = a + 1; i < b; i++) if (col.charAt(i) - '0' != c) return -1;
        return c;
    }

    /** Colore la sélection, ou la suite de la frappe. */
    void colorer(int c) {
        if (selection()) {
            int a = Math.min(cur, ancre), b = Math.max(cur, ancre);
            for (int i = a; i < b; i++) col.setCharAt(i, (char) ('0' + c));
            version++;
        } else attente = c;
        bouge = System.currentTimeMillis();
    }

    /* ================================================================ édition */

    boolean selection() { return ancre >= 0 && ancre != cur; }

    private void supprimerSelection() {
        if (!selection()) { ancre = -1; return; }
        int a = Math.min(cur, ancre), b = Math.max(cur, ancre);
        txt.delete(a, b);
        col.delete(a, b);
        cur = a;
        ancre = -1;
        version++;
    }

    void inserer(String s) {
        int c = couleurCourante();
        supprimerSelection();
        StringBuilder ok = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\r') continue;
            if (ch == '\t') ch = ' ';
            if (ch != '\n' && (Character.isISOControl(ch) || ch == '§' || ch == TexteRaster.INFERIEUR_LITTERAL)) continue;
            ok.append(ch);
        }
        int place = Math.max(0, max - txt.length());
        String t = ok.length() > place ? ok.substring(0, place) : ok.toString();
        // pas de demi-paire de substitution au bout d'un collage coupé
        if (!t.isEmpty() && Character.isHighSurrogate(t.charAt(t.length() - 1))) t = t.substring(0, t.length() - 1);
        if (t.isEmpty()) return;
        txt.insert(cur, t);
        col.insert(cur, String.valueOf((char) ('0' + c)).repeat(t.length()));
        cur += t.length();
        attente = -1;
        bouge = System.currentTimeMillis();
        version++;
    }

    private void aller(int i, boolean etendre) {
        if (etendre) { if (ancre < 0) ancre = cur; } else ancre = -1;
        cur = Math.max(0, Math.min(txt.length(), i));
        attente = -1;
        bouge = System.currentTimeMillis();
    }

    /** Touche du clavier : true si l'éditeur l'a prise. */
    boolean touche(int key, boolean maj, boolean ctrl) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> { if (!maj && selection()) aller(Math.min(cur, ancre), false); else aller(ctrl ? motPrecedent() : cur - 1, maj); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { if (!maj && selection()) aller(Math.max(cur, ancre), false); else aller(ctrl ? motSuivant() : cur + 1, maj); return true; }
            case GLFW.GLFW_KEY_UP -> { aller(vertical(-1), maj); return true; }
            case GLFW.GLFW_KEY_DOWN -> { aller(vertical(1), maj); return true; }
            case GLFW.GLFW_KEY_HOME -> { aller(ctrl ? 0 : debutLigne(), maj); return true; }
            case GLFW.GLFW_KEY_END -> { aller(ctrl ? txt.length() : finLigne(), maj); return true; }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (selection()) supprimerSelection();
                else if (cur > 0) { int a = ctrl ? motPrecedent() : cur - 1; txt.delete(a, cur); col.delete(a, cur); cur = a; version++; }
                attente = -1;
                bouge = System.currentTimeMillis();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (selection()) supprimerSelection();
                else if (cur < txt.length()) { int b = ctrl ? motSuivant() : cur + 1; txt.delete(cur, b); col.delete(cur, b); version++; }
                bouge = System.currentTimeMillis();
                return true;
            }
            default -> { }
        }
        if (!ctrl) return false;
        switch (key) {
            case GLFW.GLFW_KEY_A -> { ancre = 0; cur = txt.length(); return true; }
            case GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_X -> {
                if (selection()) {
                    int a = Math.min(cur, ancre), b = Math.max(cur, ancre);
                    Minecraft.getInstance().keyboardHandler.setClipboard(txt.substring(a, b));
                    if (key == GLFW.GLFW_KEY_X) supprimerSelection();
                }
                return true;
            }
            case GLFW.GLFW_KEY_V -> { inserer(Minecraft.getInstance().keyboardHandler.getClipboard()); return true; }
            default -> { return false; }
        }
    }

    private int motPrecedent() {
        int i = cur;
        while (i > 0 && Character.isWhitespace(txt.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(txt.charAt(i - 1))) i--;
        return i;
    }

    private int motSuivant() {
        int i = cur, n = txt.length();
        while (i < n && !Character.isWhitespace(txt.charAt(i))) i++;
        while (i < n && Character.isWhitespace(txt.charAt(i))) i++;
        return i;
    }

    /* ================================================================ mise en page */

    /** Largeur GUI d'un morceau de texte en Inter 14 (la police du volet : avances et crénage de la chaîne entière). */
    private float mesure(int a, int b) { return a >= b ? 0f : pz.largeur(txt.substring(a, b), TAILLE, false); }

    /**
     * Coupe le texte en lignes de {@code larg} GUI, première ligne au milieu en {@code yMil}, pas {@link #PAS}. Ne refait
     * rien si ni le texte ni la place n'ont changé (le corps défile : l'origine bouge sans recouper). Les positions sont
     * mesurées sur le début de la ligne entière (pas en additionnant les lettres une à une : la police du volet donne à
     * une lettre seule une avance un peu plus grande, les morceaux de couleur se décalaient).
     */
    void mettreEnPage(GardeArt.Pinceau p, float x, float yMil, float larg) {
        this.pz = p;
        boolean recouper = versionPage != version || larg != largeur;
        float dx = x - x0, dy = yMil - y0;
        x0 = x;
        y0 = yMil;
        if (!recouper) {
            if (dx != 0 || dy != 0) for (float[] q : poses) q[0] += dx;
            return;
        }
        versionPage = version;
        largeur = larg;
        lignes.clear();
        int n = txt.length(), deb = 0;
        if (xs.length < n + 1) xs = new float[n + 65];
        while (true) {
            int i = deb, coupe = -1;
            xs[deb] = 0f;
            while (i < n) {
                char c = txt.charAt(i);
                if (c == '\n') break;
                float w = mesure(deb, i + 1);
                if (w > larg && i > deb) break;
                if (c == ' ') coupe = i;
                xs[i + 1] = w;
                i++;
            }
            int fin = i, saut = 0;
            if (i < n) {
                if (txt.charAt(i) == '\n') saut = 1;
                else if (coupe > deb) { fin = coupe; saut = 1; }
            }
            lignes.add(new int[]{deb, fin});
            if (fin >= n) break;
            deb = fin + saut;
            if (deb >= n) { lignes.add(new int[]{n, n}); xs[n] = 0f; break; }
        }
        // morceaux de même couleur, gardés jusqu'à la prochaine modification
        morceaux.clear();
        poses.clear();
        for (int j = 0; j < lignes.size(); j++) {
            int[] li = lignes.get(j);
            int i = li[0];
            while (i < li[1]) {
                int k = i;
                while (k < li[1] && col.charAt(k) == col.charAt(i)) k++;
                morceaux.add(txt.substring(i, k));
                poses.add(new float[]{x0 + xs[i], j, col.charAt(i) - '0'});
                i = k;
            }
        }
    }

    int nbLignes() { return Math.max(1, lignes.size()); }

    private int ligneDe(int idx) {
        int r = 0;
        for (int j = 0; j < lignes.size(); j++) if (lignes.get(j)[0] <= idx && idx <= lignes.get(j)[1]) r = j;
        return r;
    }

    /** Largeur GUI de [a, b) sur une même ligne (positions de la mise en page). */
    private float largeur(int a, int b) {
        return b <= a ? 0f : xs[Math.min(b, xs.length - 1)] - xs[a];
    }

    private float xDe(int idx) {
        if (lignes.isEmpty()) return x0;
        int[] li = lignes.get(ligneDe(idx));
        return x0 + largeur(li[0], Math.min(idx, li[1]));
    }

    private int indexA(int ligne, float x) {
        if (lignes.isEmpty()) return 0;
        int[] li = lignes.get(Math.max(0, Math.min(lignes.size() - 1, ligne)));
        for (int i = li[0]; i < li[1]; i++) {
            if (x < x0 + (xs[i] + xs[i + 1]) / 2) return i;
        }
        return li[1];
    }

    private int vertical(int d) {
        if (lignes.isEmpty()) return cur;
        int lg = ligneDe(cur) + d;
        if (lg < 0) return 0;
        if (lg >= lignes.size()) return txt.length();
        return indexA(lg, xDe(cur));
    }

    private int debutLigne() { return lignes.isEmpty() ? 0 : lignes.get(ligneDe(cur))[0]; }

    private int finLigne() { return lignes.isEmpty() ? txt.length() : lignes.get(ligneDe(cur))[1]; }

    /** Clic (ou glisser, {@code etendre}) : place le curseur. */
    void cliquer(double mx, double my, boolean etendre) {
        if (pz == null) return;
        int lg = (int) Math.floor((my - (y0 - PAS / 2f)) / PAS);
        aller(indexA(lg, (float) mx), etendre);
    }

    /* ================================================================ rendu */

    /** Sélection, morceaux colorés et curseur (qui clignote si {@code focus}). */
    void peindre(boolean focus) {
        if (pz == null) return;
        if (selection()) {
            int a = Math.min(cur, ancre), b = Math.max(cur, ancre);
            for (int j = 0; j < lignes.size(); j++) {
                int[] li = lignes.get(j);
                int sa = Math.max(a, li[0]), sb = Math.min(b, li[1]);
                boolean saut = b > li[1] && a <= li[1] && j < lignes.size() - 1;   // le retour à la ligne choisi
                if (sb <= sa && !saut) continue;
                float xa = x0 + largeur(li[0], sa), xb = x0 + largeur(li[0], Math.max(sa, sb)) + (saut ? 2 : 0);
                float yc = y0 + j * PAS;
                pz.px(xa, yc - 4.5f, xb, yc + 4.5f, SELECTION);
            }
        }
        for (int i = 0; i < morceaux.size(); i++) {
            float[] q = poses.get(i);
            pz.texte(morceaux.get(i), q[0], y0 + q[1] * PAS, TAILLE, teinte((int) q[2]), false);
        }
        if (focus && ((System.currentTimeMillis() - bouge) / 530) % 2 == 0) {
            float x = xDe(cur), yc = y0 + ligneDe(cur) * PAS;
            int c = couleurCourante();   // le curseur prend la couleur de la frappe (or pour le blanc, comme les champs)
            pz.px(x, yc - 4.5f, x + 1f / GardeArt.echelle(), yc + 4.5f, c == 0 ? OR : teinte(c));
        }
    }
}
