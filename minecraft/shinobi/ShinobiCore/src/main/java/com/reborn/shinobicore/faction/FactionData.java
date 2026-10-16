package com.reborn.shinobicore.faction;

import com.reborn.shinobicore.character.Rank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Données du système Faction (docs/gameplay/FACTION.md) : faction, section, grade de section, charge, membre (registre),
 * annonce et ligne (caisse ou journal). Simples conteneurs mutables, persistés par {@link FactionStore} ; toute la
 * logique et les droits sont dans {@link FactionService}.
 */
public final class FactionData {

    private FactionData() {}

    public enum Type { VILLAGE, ORGANISATION }

    /** Une faction : un village (les six existent d'office) ou une organisation créée par le staff. */
    public static final class Faction {
        public String tag;                 // 3 lettres, unique : KON, SUN…
        public String nom;                 // Konohagakure
        public Type type = Type.VILLAGE;
        public String embleme = "";        // village : konoha… ; organisation : lune…
        /** Valeur du champ village des personnages (village seulement) : « Konohagakure ». */
        public String village = "";
        public UUID chef;                  // personnage, null = à nommer
        public String serveur = "shinobi";
        public int taux = -1;              // taxe des sections en % ; -1 = défaut de la config
        public final Map<String, Rank> seuils = Droits.seuilsParDefaut();
        public final Map<String, Charge> charges = new LinkedHashMap<>();
        public final List<Annonce> annonces = new ArrayList<>();
        public final List<Ligne> journal = new ArrayList<>();
        /** Taxe perçue, par semaine ISO (« 2026-W42 »). */
        public final Map<String, Long> taxeSemaine = new LinkedHashMap<>();
        public long cree;

        public boolean village() { return type == Type.VILLAGE; }
    }

    /** Une section d'une faction (ANBU, Police…) ; pour une organisation, sa section racine porte le même id que le tag. */
    public static final class Section {
        public String id;
        public String faction;             // tag
        public String nom;
        public String insigne = "missions";
        public UUID chef;                  // personnage
        public int places = 8;
        public boolean cachee;
        public String tenue = "";          // id d'une tenue du catalogue (calque du skin), "" = aucune
        public String tenueNom = "";
        public String accessoire = "";     // texte libre (objet Nexo remis par le coffre)
        public long caisse;
        public final List<Ligne> mouvements = new ArrayList<>();
        public final List<GradeSection> grades = new ArrayList<>();
        public final List<String> lieux = new ArrayList<>();   // posés par le staff (lot suivant) : « Hôpital|porte »
        public final List<Ligne> journal = new ArrayList<>();
        public long cree;
        public UUID creePar;

        public GradeSection grade(String id) {
            for (GradeSection g : grades) if (g.id.equals(id)) return g;
            return null;
        }

        public int rang(String gradeId) {
            for (int i = 0; i < grades.size(); i++) if (grades.get(i).id.equals(gradeId)) return i;
            return grades.size();
        }

        public GradeSection dernier() { return grades.isEmpty() ? null : grades.get(grades.size() - 1); }
    }

    /** Grade de section : l'ordre dans la liste est l'autorité (0 = celui du chef, verrouillé). */
    public static final class GradeSection {
        public String id;
        public String nom;
        public final Set<String> droits = new LinkedHashSet<>();
    }

    /** Charge du village (Conseiller, Intendant…) : droits en cases, titulaires. */
    public static final class Charge {
        public String id;
        public String nom;
        public boolean publique;
        public final Set<String> droits = new LinkedHashSet<>();
        public final Set<UUID> titulaires = new LinkedHashSet<>();
    }

    /** Un personnage dans le registre (tenu à jour à chaque connexion ; les hors-ligne restent comptés). */
    public static final class Membre {
        public UUID perso;
        public UUID owner;
        public String nom = "";            // « Prénom Clan »
        public String sexe = "Homme";
        public Rank rang = Rank.GENIN;
        public String village = "";
        public String faction = "";        // tag, "" = aucune
        public String section = "";        // id, "" = aucune
        public String grade = "";          // id du grade de section
        public long depuis;
        public long vu;
        public int pid = -1;
        public boolean mort;

        public boolean feminin() { return sexe != null && sexe.toLowerCase(java.util.Locale.ROOT).startsWith("f"); }
    }

    /** Annonce publiée (village ou section) : la cloche s'y branchera (CLOCHE_ET_MISSIVES.md). */
    public static final class Annonce {
        public long le;
        public UUID auteur;                // personnage
        public String titre = "";          // « Hokage », « Commissaire de la Police »
        public String section = "";        // "" = tout le village
        public String texte = "";
    }

    /** Ligne de caisse (montant signé) ou de journal (montant 0). */
    public record Ligne(long le, String texte, long montant) {}
}
