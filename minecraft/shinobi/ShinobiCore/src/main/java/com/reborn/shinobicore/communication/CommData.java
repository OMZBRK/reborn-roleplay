package com.reborn.shinobicore.communication;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Données de la cloche du village et des volières (docs/gameplay/CLOCHE_ET_MISSIVES.md) : points posés par le staff
 * (cloche, volière) et missives (une par copie). Conteneurs simples, persistés par {@link CommStore} ; la logique est
 * dans {@link CommService}, les règles pures dans {@link CommRegles}.
 */
public final class CommData {

    private CommData() {}

    /** Point d'interaction posé par le staff sur un bloc ou un meuble Nexo (son entité). */
    public abstract static class Point {
        public String id;
        public String faction = "";        // tag du village (KON…)
        public String monde = "world";
        public int x, y, z;
        public UUID entite;                // meuble Nexo visé (entité d'interaction), sinon null
        public long pose;
        public UUID posePar;

        /** Le bloc ou l'entité (x, y, z) est-il ce point ? (un meuble occupe parfois 2 blocs de haut) */
        public boolean est(String w, int bx, int by, int bz) {
            return monde.equals(w) && Math.abs(bx - x) <= 1 && by >= y - 1 && by <= y + 2 && Math.abs(bz - z) <= 1;
        }
    }

    /** La cloche d'un village : zone (rayon), anti-spam. */
    public static final class Cloche extends Point {
        public String lieu = "";           // « Place centrale de Konoha »
        public int rayon = 250;
        public long derniere;              // dernière annonce (ms)
        public long taireJusqua;           // staff : /cloche taire
    }

    public enum TypeVoliere {
        PETITE("Petite volière", 12), GRANDE("Grande volière", 40), TOUR("Tour de volière", 80),
        PALAIS("Volière du palais", 40);

        public final String libelle;
        public final int capacite;

        TypeVoliere(String libelle, int capacite) { this.libelle = libelle; this.capacite = capacite; }

        public static TypeVoliere lire(String s) {
            if (s == null) return null;
            String k = java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toUpperCase(Locale.ROOT);
            for (TypeVoliere t : values()) if (t.name().equals(k)) return t;
            return null;
        }
    }

    /** Une volière : un perchoir posé par le staff, nommé, typé, rattaché à un village. */
    public static final class Voliere extends Point {
        public String nom = "";            // « Grande volière de Konoha »
        public TypeVoliere type = TypeVoliere.GRANDE;
    }

    public enum Etat { VOL, ATTENTE, PRISE, BRULEE }

    /** Un destinataire écrit sur la missive : nom affiché (lisible de tous), personnage si c'est une personne connue. */
    public record Dest(String nom, UUID perso) {}

    /** Une copie de missive (une par destinataire). */
    public static final class Missive {
        public String id;                  // « M7K2QF »
        public String groupe = "";         // les copies d'un même envoi
        public UUID auteur;                // personnage
        public UUID auteurCompte;
        public String auteurNom = "";      // vrai nom (staff, journal) ; affiché seulement si signée
        public boolean signee;
        public final List<Dest> dests = new ArrayList<>();
        public int index;                  // la copie de dests.get(index)
        public String texte = "";          // balises (Riche)
        public String depart = "", arrivee = "";
        public long envoye, arrive;
        public Etat etat = Etat.VOL;
        public boolean scellee = true;
        public UUID briseePar;             // personnage qui a brisé le cachet
        public long briseeLe;
        public UUID preneur;               // personnage qui l'a prise à la volière
        public long priseLe;
        public boolean prevenu;            // destinataire prévenu de l'arrivée
        public boolean transferee;

        public Dest pour() { return index >= 0 && index < dests.size() ? dests.get(index) : new Dest("?", null); }
    }
}
