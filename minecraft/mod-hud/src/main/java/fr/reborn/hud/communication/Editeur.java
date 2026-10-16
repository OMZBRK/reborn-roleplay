package fr.reborn.hud.communication;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La zone d'écriture encastrée de la cloche et de la volière (maquettes v3c 01, 03) : texte multiligne, retour à la
 * ligne automatique, <b>gras</b> et <i>italique</i> seulement (G, I ou Ctrl+G / Ctrl+B, Ctrl+I), sélection au clavier
 * (Maj + flèches, Ctrl+A), copier / couper / coller, curseur à la souris. Un style par caractère ; le texte part en
 * segments ({@link CommArt.Seg}). Le retour à la ligne se tape Maj + Entrée (Entrée envoie, comme sur la maquette).
 */
final class Editeur {

    static final int G = 1, I = 2;

    private final StringBuilder txt = new StringBuilder();
    private final StringBuilder stl = new StringBuilder();   // un chiffre par caractère : bit 1 gras, bit 2 italique
    int max;
    int cur, ancre = -1, attente = -1;
    private long bouge = System.currentTimeMillis();

    // mise en page (recalculée à chaque image)
    private final List<int[]> lignes = new ArrayList<>();
    private final Map<Integer, Float> largeurs = new HashMap<>();
    private Font font;
    private float k = 1, x0, y0, pas;

    Editeur(int max) { this.max = max; }

    /* ================================================================ contenu */

    int longueur() { return txt.length(); }

    boolean vide() { return txt.toString().isBlank(); }

    String plat() { return txt.toString(); }

    void effacer() { txt.setLength(0); stl.setLength(0); cur = 0; ancre = -1; attente = -1; }

    List<CommArt.Seg> segs() { return segs(0, txt.length()); }

    private List<CommArt.Seg> segs(int a, int b) {
        List<CommArt.Seg> l = new ArrayList<>();
        int i = a;
        while (i < b) {
            int j = i;
            while (j < b && stl.charAt(j) == stl.charAt(i)) j++;
            int s = stl.charAt(i) - '0';
            l.add(new CommArt.Seg(txt.substring(i, j), ((s & G) != 0 ? "g" : "") + ((s & I) != 0 ? "i" : "")));
            i = j;
        }
        return l;
    }

    void poser(List<CommArt.Seg> l) {
        effacer();
        for (CommArt.Seg s : l) {
            int st = (s.st().contains("g") ? G : 0) | (s.st().contains("i") ? I : 0);
            for (int i = 0; i < s.t().length() && txt.length() < max; i++) { txt.append(s.t().charAt(i)); stl.append((char) ('0' + st)); }
        }
        cur = txt.length();
    }

    /* ================================================================ style */

    private int styleCourant() {
        if (attente >= 0) return attente;
        if (cur > 0) return stl.charAt(cur - 1) - '0';
        return stl.length() > 0 ? stl.charAt(0) - '0' : 0;
    }

    /** G ou I est-il actif (sélection entière, ou style de frappe) ? */
    boolean actif(int bit) {
        if (selection()) {
            int a = Math.min(cur, ancre), b = Math.max(cur, ancre);
            for (int i = a; i < b; i++) if (((stl.charAt(i) - '0') & bit) == 0) return false;
            return true;
        }
        return (styleCourant() & bit) != 0;
    }

    void basculer(int bit) {
        if (selection()) {
            boolean tous = actif(bit);
            int a = Math.min(cur, ancre), b = Math.max(cur, ancre);
            for (int i = a; i < b; i++) {
                int s = stl.charAt(i) - '0';
                stl.setCharAt(i, (char) ('0' + (tous ? s & ~bit : s | bit)));
            }
        } else attente = styleCourant() ^ bit;
        bouge = System.currentTimeMillis();
    }

    /* ================================================================ édition */

    boolean selection() { return ancre >= 0 && ancre != cur; }

    private void supprimerSelection() {
        if (!selection()) { ancre = -1; return; }
        int a = Math.min(cur, ancre), b = Math.max(cur, ancre);
        txt.delete(a, b);
        stl.delete(a, b);
        cur = a;
        ancre = -1;
    }

    void inserer(String s) {
        int st = styleCourant();
        supprimerSelection();
        StringBuilder ok = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r') continue;
            if (c == '\t') c = ' ';
            if (c != '\n' && (Character.isISOControl(c) || c == '§')) continue;
            ok.append(c);
        }
        int place = Math.max(0, max - txt.length());
        String t = ok.length() > place ? ok.substring(0, place) : ok.toString();
        if (t.isEmpty()) return;
        txt.insert(cur, t);
        stl.insert(cur, String.valueOf((char) ('0' + st)).repeat(t.length()));
        cur += t.length();
        attente = st == styleCourant() ? -1 : attente;
        bouge = System.currentTimeMillis();
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
                else if (cur > 0) { int a = ctrl ? motPrecedent() : cur - 1; txt.delete(a, cur); stl.delete(a, cur); cur = a; }
                attente = -1;
                bouge = System.currentTimeMillis();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (selection()) supprimerSelection();
                else if (cur < txt.length()) { int b = ctrl ? motSuivant() : cur + 1; txt.delete(cur, b); stl.delete(cur, b); }
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
            case GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_G -> { basculer(G); return true; }
            case GLFW.GLFW_KEY_I -> { basculer(I); return true; }
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

    private float l(int i) {
        char c = txt.charAt(i);
        int s = stl.charAt(i) - '0';
        int cle = (c << 2) | s;
        Float w = largeurs.get(cle);
        if (w == null) {
            w = font.getSplitter().stringWidth(Component.literal(String.valueOf(c)).setStyle(CommArt.style(((s & G) != 0 ? "g" : "") + ((s & I) != 0 ? "i" : ""))));
            largeurs.put(cle, w);
        }
        return w * k;
    }

    /** Coupe le texte en lignes de {@code largeur} GUI ; première ligne au milieu en {@code yMil}, interligne {@code pas}. */
    void mettreEnPage(Font f, float x, float yMil, float largeur, int taille, float pas) {
        this.font = f;
        this.k = taille / 18f;
        this.x0 = x;
        this.y0 = yMil;
        this.pas = pas;
        lignes.clear();
        int n = txt.length(), deb = 0;
        while (true) {
            float w = 0;
            int i = deb, coupe = -1;
            while (i < n) {
                char c = txt.charAt(i);
                if (c == '\n') break;
                float cw = l(i);
                if (w + cw > largeur && i > deb) break;
                if (c == ' ') coupe = i;
                w += cw;
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
            if (deb >= n) { lignes.add(new int[]{n, n}); break; }
        }
    }

    int nbLignes() { return lignes.size(); }

    private int ligneDe(int idx) {
        int r = 0;
        for (int j = 0; j < lignes.size(); j++) if (lignes.get(j)[0] <= idx && idx <= lignes.get(j)[1]) r = j;
        return r;
    }

    /** Largeur rendue de [a, b) (même composant que le texte peint : le curseur tombe juste). */
    private float largeur(int a, int b) {
        if (b <= a) return 0;
        return font.getSplitter().stringWidth(CommArt.riche(segs(a, b))) * k;
    }

    private float xDe(int idx) {
        int[] li = lignes.get(ligneDe(idx));
        return x0 + largeur(li[0], Math.min(idx, li[1]));
    }

    private int indexA(int ligne, float x) {
        int[] li = lignes.get(Math.max(0, Math.min(lignes.size() - 1, ligne)));
        for (int i = li[0]; i < li[1]; i++) {
            float a = x0 + largeur(li[0], i), b = x0 + largeur(li[0], i + 1);
            if (x < (a + b) / 2) return i;
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

    /** Clic : place le curseur (Maj : étend la sélection). */
    void cliquer(double mx, double my, boolean maj) {
        if (lignes.isEmpty()) return;
        int lg = (int) Math.floor((my - (y0 - pas / 2)) / pas);
        aller(indexA(lg, (float) mx), maj);
    }

    /* ================================================================ rendu */

    /** Lignes, sélection et curseur (qui clignote) ; renvoie le nombre de lignes. */
    int peindre(CommArt.Plume pz, int taille, int col, boolean focus) {
        if (selection()) {
            int a = Math.min(cur, ancre), b = Math.max(cur, ancre);
            for (int j = 0; j < lignes.size(); j++) {
                int[] li = lignes.get(j);
                int sa = Math.max(a, li[0]), sb = Math.min(b, li[1]);
                if (sb <= sa) continue;
                float xa = x0 + largeur(li[0], sa), xb = x0 + largeur(li[0], sb);
                float yc = y0 + j * pas;
                pz.fond(xa, yc - 6 * k * 1.1f, xb, yc + 6 * k * 1.1f, 0xFF4A2E1E);
            }
        }
        for (int j = 0; j < lignes.size(); j++) {
            int[] li = lignes.get(j);
            if (li[1] > li[0]) pz.riche(segs(li[0], li[1]), x0, y0 + j * pas, taille, col);
        }
        if (focus && ((System.currentTimeMillis() - bouge) / 530) % 2 == 0) {
            float x = xDe(cur), yc = y0 + ligneDe(cur) * pas;
            pz.px(x + 0.5f, yc - 10 * k / 1.0f * 0.55f, x + 1.0f, yc + 10 * k * 0.55f, CommArt.OR_CLAIR);
        }
        return lignes.size();
    }
}
