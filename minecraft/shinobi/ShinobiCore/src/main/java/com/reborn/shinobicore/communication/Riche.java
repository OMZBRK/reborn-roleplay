package com.reborn.shinobicore.communication;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Texte riche des annonces et des missives (CLOCHE_ET_MISSIVES.md §2.3, §3.2) : <b>gras</b> et <i>italique</i>
 * seulement. Sur le canal : un tableau de segments {@code [["texte", ""|"g"|"i"|"gi"], …]} ; sur disque : une chaîne à
 * balises {@code <g>…</g>}, {@code <i>…</i>} ({@code <} et {@code &} échappés). Le serveur normalise tout ce qui arrive
 * (styles inconnus retirés, caractères de contrôle enlevés sauf le retour à la ligne, longueur bornée).
 */
public final class Riche {

    /** Un segment : texte et style ({@code ""}, {@code "g"}, {@code "i"}, {@code "gi"}). */
    public record Seg(String t, String st) {}

    private Riche() {}

    static String style(String s) {
        if (s == null) return "";
        boolean g = s.indexOf('g') >= 0, i = s.indexOf('i') >= 0;
        return (g ? "g" : "") + (i ? "i" : "");
    }

    static String nettoyer(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\r') continue;
            if (c == '\n' || c == '\t') { b.append(c == '\t' ? ' ' : '\n'); continue; }
            if (Character.isISOControl(c) || c == '§') continue;
            b.append(c);
        }
        return b.toString();
    }

    /** Lit le tableau du canal, normalise, borne à {@code max} caractères visibles et fusionne les segments voisins. */
    public static List<Seg> lire(JsonElement e, int max) {
        List<Seg> out = new ArrayList<>();
        if (e == null) return out;
        int n = 0;
        if (e.isJsonPrimitive()) {
            String t = nettoyer(e.getAsString());
            if (t.length() > max) t = t.substring(0, max);
            if (!t.isEmpty()) out.add(new Seg(t, ""));
            return out;
        }
        if (!e.isJsonArray()) return out;
        for (JsonElement x : e.getAsJsonArray()) {
            if (!x.isJsonArray() || x.getAsJsonArray().isEmpty()) continue;
            JsonArray a = x.getAsJsonArray();
            String t = nettoyer(a.get(0).isJsonPrimitive() ? a.get(0).getAsString() : "");
            String st = a.size() > 1 && a.get(1).isJsonPrimitive() ? style(a.get(1).getAsString()) : "";
            if (t.isEmpty()) continue;
            if (n + t.length() > max) t = t.substring(0, Math.max(0, max - n));
            if (t.isEmpty()) break;
            n += t.length();
            ajouter(out, t, st);
        }
        // pas de blancs en tête ni en queue, pas plus de deux retours à la ligne de suite
        return rogner(out);
    }

    private static void ajouter(List<Seg> out, String t, String st) {
        if (!out.isEmpty() && out.get(out.size() - 1).st().equals(st)) {
            Seg d = out.remove(out.size() - 1);
            out.add(new Seg(d.t() + t, st));
        } else out.add(new Seg(t, st));
    }

    private static List<Seg> rogner(List<Seg> l) {
        String plat = plat(l);
        int deb = 0, fin = plat.length();
        while (deb < fin && Character.isWhitespace(plat.charAt(deb))) deb++;
        while (fin > deb && Character.isWhitespace(plat.charAt(fin - 1))) fin--;
        List<Seg> out = new ArrayList<>();
        int pos = 0;
        for (Seg s : l) {
            int a = Math.max(deb, pos), b = Math.min(fin, pos + s.t().length());
            if (b > a) ajouter(out, s.t().substring(a - pos, b - pos).replaceAll("\n{3,}", "\n\n"), s.st());
            pos += s.t().length();
        }
        return out;
    }

    public static String plat(List<Seg> l) {
        StringBuilder b = new StringBuilder();
        for (Seg s : l) b.append(s.t());
        return b.toString();
    }

    public static int longueur(List<Seg> l) { return plat(l).length(); }

    public static JsonArray json(List<Seg> l) {
        JsonArray a = new JsonArray();
        for (Seg s : l) {
            JsonArray x = new JsonArray();
            x.add(s.t());
            x.add(s.st());
            a.add(x);
        }
        return a;
    }

    /* ================================================================ disque */

    public static String balises(List<Seg> l) {
        StringBuilder b = new StringBuilder();
        for (Seg s : l) {
            boolean g = s.st().contains("g"), i = s.st().contains("i");
            if (g) b.append("<g>");
            if (i) b.append("<i>");
            b.append(s.t().replace("&", "&amp;").replace("<", "&lt;"));
            if (i) b.append("</i>");
            if (g) b.append("</g>");
        }
        return b.toString();
    }

    public static List<Seg> deBalises(String s) {
        List<Seg> out = new ArrayList<>();
        if (s == null) return out;
        boolean g = false, i = false;
        StringBuilder cur = new StringBuilder();
        for (int k = 0; k < s.length(); ) {
            if (s.startsWith("<g>", k) || s.startsWith("</g>", k) || s.startsWith("<i>", k) || s.startsWith("</i>", k)) {
                if (cur.length() > 0) { ajouter(out, cur.toString(), (g ? "g" : "") + (i ? "i" : "")); cur.setLength(0); }
                boolean ferme = s.charAt(k + 1) == '/';
                char quoi = s.charAt(k + (ferme ? 2 : 1));
                if (quoi == 'g') g = !ferme; else i = !ferme;
                k += ferme ? 4 : 3;
                continue;
            }
            if (s.startsWith("&lt;", k)) { cur.append('<'); k += 4; continue; }
            if (s.startsWith("&amp;", k)) { cur.append('&'); k += 5; continue; }
            cur.append(s.charAt(k++));
        }
        if (cur.length() > 0) ajouter(out, cur.toString(), (g ? "g" : "") + (i ? "i" : ""));
        return out;
    }
}
