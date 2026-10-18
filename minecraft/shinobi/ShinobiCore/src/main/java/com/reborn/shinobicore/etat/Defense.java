package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.api.etat.Etiquette;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Une <b>défense</b> (docs/technique/ETATS_JOUEUR.md §6) : portée par un type de modificateur ({@code defense:} sous
 * {@code modificateurs.types.<id>}). Elle s'applique à une attaque dont une étiquette est dans {@code contre}, sauf si
 * l'attaque porte une étiquette de {@code sauf} — elle est alors <b>contournée</b> et l'attaque prend
 * {@code faiblesse[étiquette]}. Fonction pure ({@link #evaluer}), testable hors serveur.
 *
 * @param type       {@code INTANGIBLE} (0 dégât), {@code REDUCTION} (× (1 − valeur)), {@code IMMUNITE} (annule),
 *                   {@code ESQUIVE} (annule), {@code ABSORPTION} et {@code RENVOI} (réservés : traités comme
 *                   {@code REDUCTION} / {@code INTANGIBLE} en attendant leur réserve et leur renvoi)
 * @param conditions {@code CHAKRA_POSITIF}, {@code PAS_MENOTTE}, {@code PAS_DESHYDRATE} : fausses = défense coupée
 */
public record Defense(Type type, Set<Etiquette> contre, Set<Etiquette> sauf, Map<Etiquette, Double> faiblesse,
                      Set<String> conditions, double valeur) {

    public enum Type { INTANGIBLE, REDUCTION, ABSORPTION, RENVOI, ESQUIVE, IMMUNITE }

    /** Issue d'une défense sur une attaque. */
    public enum Issue { IGNOREE, COUPEE, APPLIQUEE, CONTOURNEE }

    /** Résultat : l'issue et le multiplicateur de dégâts (0 = annulé). */
    public record Effet(Issue issue, double multiplicateur, Etiquette par) {}

    /**
     * Confronte l'attaque à la défense (§6.3).
     *
     * @param conditionsVraies les conditions vraies chez la cible en ce moment
     */
    public Effet evaluer(Set<Etiquette> attaque, Set<String> conditionsVraies) {
        for (String c : conditions) if (!conditionsVraies.contains(c)) return new Effet(Issue.COUPEE, 1.0, null);
        boolean touche = false;
        for (Etiquette e : attaque) if (contre.contains(e)) { touche = true; break; }
        if (!touche) return new Effet(Issue.IGNOREE, 1.0, null);
        for (Etiquette e : attaque) {
            if (sauf.contains(e)) return new Effet(Issue.CONTOURNEE, faiblesse.getOrDefault(e, 1.0), e);
        }
        double m = switch (type) {
            case INTANGIBLE, IMMUNITE, ESQUIVE, RENVOI -> 0.0;
            case REDUCTION, ABSORPTION -> Math.max(0.0, 1.0 - valeur);
        };
        return new Effet(Issue.APPLIQUEE, m, null);
    }

    /** Lecture depuis la section {@code defense:} (déjà en maps). Null si absente ou invalide. */
    @SuppressWarnings("unchecked")
    public static Defense lire(Map<?, ?> m) {
        if (m == null) return null;
        Type t;
        try {
            t = Type.valueOf(String.valueOf(m.get("type")).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Set<Etiquette> contre = Etiquette.lireTout(m.get("contre") instanceof List<?> l ? l : List.of());
        Set<Etiquette> sauf = Etiquette.lireTout(m.get("sauf") instanceof List<?> l ? l : List.of());
        Map<Etiquette, Double> faib = new EnumMap<>(Etiquette.class);
        if (m.get("faiblesse") instanceof Map<?, ?> fm) {
            for (Map.Entry<?, ?> e : fm.entrySet()) {
                Etiquette et = Etiquette.lire(String.valueOf(e.getKey()));
                if (et == null) continue;
                try {
                    faib.put(et, Double.parseDouble(String.valueOf(e.getValue())));
                } catch (NumberFormatException ignored) { }
            }
        }
        Set<String> cond = new LinkedHashSet<>();
        if (m.get("conditions") instanceof List<?> cl) for (Object o : cl) cond.add(String.valueOf(o).trim().toUpperCase(Locale.ROOT));
        double val = 0;
        try {
            if (m.get("valeur") != null) val = Double.parseDouble(String.valueOf(m.get("valeur")));
        } catch (NumberFormatException ignored) { }
        return new Defense(t, contre.isEmpty() ? EnumSet.noneOf(Etiquette.class) : contre, sauf, faib, cond, val);
    }
}
