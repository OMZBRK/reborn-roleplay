package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.etat.Capacites;
import com.reborn.shinobicore.api.etat.Vital;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * <b>La matrice des états</b> (docs/technique/ETATS_JOUEUR.md §4.5-4.6) : une fonction pure, sans Bukkit, qui calcule
 * à partir des <b>lignes actives</b> (une par valeur de couche, par modificateur, par contexte) le niveau de chaque
 * {@link Capacite}, sa raison, et les effets physiques agrégés. Testable hors serveur (banc de recette).
 *
 * <p>Règle de calcul (§4.5) :
 * <ol>
 *   <li>on part de « tout permis » pour les capacités ordinaires, « rien » pour les spéciales ;</li>
 *   <li>chaque ligne applique ses plafonds ({@code refuse} = 0, {@code limite} = 1) : on garde le plus bas ;</li>
 *   <li>le <b>refus l'emporte toujours</b>, sauf une exception écrite ({@code force}) ;</li>
 *   <li>chaque limite / refus porte la raison de la <b>première</b> ligne qui l'a posé — les lignes arrivent dans
 *       l'ordre de priorité (celui de l'état principal) ;</li>
 *   <li>les spéciales {@code accorde} sont accordées à la fin, sauf si une ligne les refuse explicitement.</li>
 * </ol>
 * Vitesse : produit des multiplicateurs, avec un plancher ({@code vitesse-plancher}, 0,25), sauf une ligne à 0
 * (immobile) qui donne 0. Saut : produit, 0 si une ligne vaut 0.
 *
 * <p>Les lignes viennent de {@code etats.yml} ({@link #lire}) ; le code ne contient que les règles générales.
 */
public final class Matrice {

    /** Une ligne de la matrice. */
    public record Ligne(String id, String raison, Map<Capacite, Integer> plafonds, Map<Capacite, String> raisons,
                        Set<Capacite> accorde, Set<Capacite> force, Set<Vital> siVital,
                        double vitesse, double saut, boolean faimTenue) {

        /** Ligne minimale : refuse ces capacités avec cette raison (types du registre des modificateurs). */
        public static Ligne simple(String id, String raison, Collection<Capacite> refuse) {
            Map<Capacite, Integer> p = new EnumMap<>(Capacite.class);
            for (Capacite c : refuse) p.put(c, 0);
            return new Ligne(id, raison, p, Map.of(), Set.of(), Set.of(), Set.of(), 1.0, 1.0, false);
        }

        /** Copie avec d'autres multiplicateurs (surcharge interpolée, facteurs des add-ons). */
        public Ligne avecVitesse(double v, double s) {
            return new Ligne(id, raison, plafonds, raisons, accorde, force, siVital, v, s, faimTenue);
        }
    }

    /** Une ligne active, avec son paramètre ({@code {x}} dans la raison : secondes de combat restantes…). */
    public record Activation(Ligne ligne, String param) {
        public static Activation de(Ligne l) { return new Activation(l, null); }
    }

    private final Map<String, Ligne> lignes;
    private final double plancher;

    public Matrice(Map<String, Ligne> lignes, double plancher) {
        this.lignes = Collections.unmodifiableMap(new LinkedHashMap<>(lignes));
        this.plancher = plancher;
    }

    public Ligne ligne(String id) { return lignes.get(id); }

    public Map<String, Ligne> lignes() { return lignes; }

    public double plancher() { return plancher; }

    /* ================================================================ calcul */

    /** Le calcul (§4.5). {@code vital} sert aux lignes conditionnelles ({@code si-vital}). */
    public Capacites calculer(Vital vital, List<Activation> actives) {
        Capacite[] toutes = Capacite.values();
        byte[] n = new byte[toutes.length];
        String[] r = new String[toutes.length];
        String[] l = new String[toutes.length];
        boolean[] bloque = new boolean[toutes.length];
        for (Capacite c : toutes) n[c.ordinal()] = (byte) (c.speciale() ? 0 : 2);
        double vit = 1.0, saut = 1.0;
        boolean vitZero = false, sautZero = false, faim = false;
        List<Activation> grants = new ArrayList<>();
        for (Activation a : actives) {
            Ligne ln = a.ligne();
            if (ln == null) continue;
            if (!ln.siVital().isEmpty() && (vital == null || !ln.siVital().contains(vital))) continue;
            for (Map.Entry<Capacite, Integer> e : ln.plafonds().entrySet()) {
                int i = e.getKey().ordinal();
                int plaf = Math.max(0, Math.min(2, e.getValue()));
                if (plaf == 0) bloque[i] = true;
                if (plaf < n[i]) {
                    n[i] = (byte) plaf;
                    String txt = ln.raisons().getOrDefault(e.getKey(), ln.raison());
                    r[i] = param(txt, a.param());
                    l[i] = ln.id();
                }
            }
            if (!ln.accorde().isEmpty() || !ln.force().isEmpty()) grants.add(a);
            if (ln.vitesse() <= 0) vitZero = true; else vit *= ln.vitesse();
            if (ln.saut() <= 0) sautZero = true; else saut *= ln.saut();
            faim |= ln.faimTenue();
        }
        for (Activation a : grants) {
            Ligne ln = a.ligne();
            for (Capacite c : ln.accorde()) {
                int i = c.ordinal();
                if (!bloque[i] && n[i] < 2) { n[i] = 2; l[i] = ln.id(); r[i] = null; }
            }
            for (Capacite c : ln.force()) {
                int i = c.ordinal();
                n[i] = 2;
                l[i] = ln.id();
                r[i] = null;
            }
        }
        double v = vitZero ? 0.0 : (vit < plancher ? plancher : vit);
        double s = sautZero ? 0.0 : saut;
        return new Capacites(n, r, l, v, s, faim);
    }

    /**
     * Les lignes actives ({@code id → paramètre}) remises dans l'<b>ordre du fichier</b> (= la priorité), avec leurs
     * multiplicateurs remplacés si {@code vitesses} en donne ({@code id → {vitesse, saut}}). Inconnues ignorées.
     */
    public List<Activation> ordonner(Map<String, String> actives, Map<String, double[]> vitesses) {
        List<Activation> out = new ArrayList<>();
        for (Map.Entry<String, Ligne> e : lignes.entrySet()) {
            if (!actives.containsKey(e.getKey())) continue;
            Ligne ln = e.getValue();
            double[] vs = vitesses == null ? null : vitesses.get(e.getKey());
            if (vs != null) ln = ln.avecVitesse(vs[0], vs[1]);
            out.add(new Activation(ln, actives.get(e.getKey())));
        }
        return out;
    }

    /** Raccourci : calcul à partir d'identifiants de lignes, remis dans l'ordre du fichier (banc de tests). */
    public Capacites calculer(Vital vital, String... ids) {
        Map<String, String> a = new LinkedHashMap<>();
        for (String id : ids) {
            int i = id.indexOf('=');
            if (i > 0) a.put(id.substring(0, i), id.substring(i + 1)); else a.put(id, null);
        }
        return calculer(vital, ordonner(a, null));
    }

    private static String param(String txt, String p) {
        if (txt == null) return "";
        return p == null ? txt.replace("{x}", "") : txt.replace("{x}", p);
    }

    /* ================================================================ lecture des données */

    /**
     * Lit les lignes depuis la structure YAML ({@code lignes:} de {@code etats.yml}, déjà convertie en maps).
     * Clés d'une ligne : {@code raison}, {@code refuse} (liste), {@code sauf} (retirées de {@code refuse}), {@code limite} (capacité → niveau), {@code raisons}
     * (capacité → raison propre), {@code accorde}, {@code force}, {@code si-vital}, {@code vitesse}, {@code saut},
     * {@code faim-tenue}. Groupes admis dans les listes : {@code TOUT} (capacités ordinaires), {@code FRAPPER},
     * {@code DEPLACEMENT}, {@code SACOCHE}.
     *
     * @param erreurs reçoit une ligne par capacité ou valeur inconnue (affichées au démarrage, jamais fatales)
     */
    public static Matrice lire(Map<?, ?> racine, List<String> erreurs) {
        Map<String, Ligne> out = new LinkedHashMap<>();
        double plancher = num(racine.get("vitesse-plancher"), 0.25);
        Object lo = racine.get("lignes");
        if (lo instanceof Map<?, ?> lm) {
            for (Map.Entry<?, ?> e : lm.entrySet()) {
                String id = String.valueOf(e.getKey());
                if (!(e.getValue() instanceof Map<?, ?> m)) { erreurs.add(id + " : pas une section"); continue; }
                out.put(id, ligne(id, m, erreurs));
            }
        }
        return new Matrice(out, plancher);
    }

    static Ligne ligne(String id, Map<?, ?> m, List<String> erreurs) {
        String raison = m.get("raison") == null ? id : String.valueOf(m.get("raison"));
        Map<Capacite, Integer> plaf = new EnumMap<>(Capacite.class);
        Set<Capacite> refuse = caps(m.get("refuse"), id, erreurs);
        refuse.removeAll(caps(m.get("sauf"), id, erreurs));
        for (Capacite c : refuse) plaf.put(c, 0);
        if (m.get("limite") instanceof Map<?, ?> lim) {
            for (Map.Entry<?, ?> e : lim.entrySet()) {
                int niv = (int) num(e.getValue(), 1);
                for (Capacite c : groupe(String.valueOf(e.getKey()), id, erreurs)) plaf.merge(c, niv, Math::min);
            }
        }
        Map<Capacite, String> rs = new EnumMap<>(Capacite.class);
        if (m.get("raisons") instanceof Map<?, ?> rm) {
            for (Map.Entry<?, ?> e : rm.entrySet()) {
                for (Capacite c : groupe(String.valueOf(e.getKey()), id, erreurs)) rs.put(c, String.valueOf(e.getValue()));
            }
        }
        Set<Vital> siVital = EnumSet.noneOf(Vital.class);
        if (m.get("si-vital") instanceof List<?> sv) {
            for (Object o : sv) {
                try {
                    siVital.add(Vital.valueOf(String.valueOf(o).trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    erreurs.add(id + " : vital inconnu « " + o + " »");
                }
            }
        }
        return new Ligne(id, raison, plaf, rs, caps(m.get("accorde"), id, erreurs), caps(m.get("force"), id, erreurs),
                siVital, num(m.get("vitesse"), 1.0), num(m.get("saut"), 1.0),
                Boolean.TRUE.equals(m.get("faim-tenue")) || "true".equalsIgnoreCase(String.valueOf(m.get("faim-tenue"))));
    }

    private static Set<Capacite> caps(Object o, String id, List<String> erreurs) {
        Set<Capacite> s = EnumSet.noneOf(Capacite.class);
        if (o instanceof List<?> l) for (Object x : l) s.addAll(groupe(String.valueOf(x), id, erreurs));
        else if (o != null) s.addAll(groupe(String.valueOf(o), id, erreurs));
        return s;
    }

    /** Un nom de capacité ou de groupe → capacités. */
    static Set<Capacite> groupe(String nom, String id, List<String> erreurs) {
        String k = nom.trim().toUpperCase(Locale.ROOT);
        Set<Capacite> s = EnumSet.noneOf(Capacite.class);
        switch (k) {
            case "TOUT" -> { for (Capacite c : Capacite.values()) if (!c.speciale()) s.add(c); }
            case "FRAPPER" -> s.addAll(EnumSet.of(Capacite.FRAPPER_MAINS, Capacite.FRAPPER_ARME, Capacite.FRAPPER_PROJECTILE));
            case "DEPLACEMENT" -> s.addAll(EnumSet.of(Capacite.BOUGER, Capacite.TROTTER, Capacite.SPRINTER, Capacite.SAUTER,
                    Capacite.MOBILITE));
            case "SACOCHE" -> s.addAll(EnumSet.of(Capacite.OUVRIR_SACOCHE, Capacite.MODIFIER_SACOCHE));
            default -> {
                Capacite c = Capacite.lire(k);
                if (c != null) s.add(c);
                else if (erreurs != null) erreurs.add(id + " : capacité inconnue « " + nom + " »");
            }
        }
        return s;
    }

    private static double num(Object o, double def) {
        if (o instanceof Number x) return x.doubleValue();
        if (o == null) return def;
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
