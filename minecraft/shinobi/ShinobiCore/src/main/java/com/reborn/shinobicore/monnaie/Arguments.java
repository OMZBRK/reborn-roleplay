package com.reborn.shinobicore.monnaie;

import java.util.ArrayList;
import java.util.List;

/** Arguments de commande avec les textes entre guillemets (« "remboursement du ramen" ») gardés d'un bloc. */
public final class Arguments {

    private Arguments() {}

    public static List<String> lire(String[] args) {
        String brut = String.join(" ", args);
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean guill = false, vu = false;
        for (int i = 0; i < brut.length(); i++) {
            char ch = brut.charAt(i);
            if (ch == '"' || ch == '«' || ch == '»' || ch == '“' || ch == '”') {
                guill = !guill;
                vu = true;
                continue;
            }
            if (ch == ' ' && !guill) {
                if (cur.length() > 0 || vu) out.add(cur.toString().trim());
                cur.setLength(0);
                vu = false;
                continue;
            }
            cur.append(ch);
        }
        if (cur.length() > 0 || vu) out.add(cur.toString().trim());
        return out;
    }

    /** Ce qui suit l'indice {@code de} (texte libre sans guillemets accepté). */
    public static String reste(List<String> a, int de) {
        if (de >= a.size()) return "";
        return String.join(" ", a.subList(de, a.size())).trim();
    }
}
