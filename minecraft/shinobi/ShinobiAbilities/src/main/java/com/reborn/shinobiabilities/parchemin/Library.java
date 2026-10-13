package com.reborn.shinobiabilities.parchemin;

import org.bukkit.Location;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Une bibliothèque (docs/gameplay/APPRENTISSAGE.md §3, bibliothèques v2) : où elle est, son type, son propriétaire,
 * son accès, ses deux étagères — le <b>fonds</b> (rouleaux déposés, permanents) et les <b>arrivages</b> (tirage
 * partagé, renouvelé) — et son journal.
 */
public final class Library {

    /** Modèles de pourcentages internes, indices D C B A S (docs/gameplay/APPRENTISSAGE.md §3). */
    public static final double[] PUBLIQUE = {35, 12, 3, 0.3, 0.02};
    public static final double[] RESERVEE = {30, 18, 7, 1.0, 0.08};
    public static final double[] PRIVEE = {20, 22, 12, 3.0, 0.3};
    /** Pourcentage par défaut d'une technique de la réserve de secrets (D C B A S) ; réglable dans la config. */
    public static final double[] SECRETS = {30, 20, 10, 3.0, 0.5};

    /** Cases du fonds (un meuble ; extensible plus tard). */
    public static final int FONDS = 9;
    /** Entrées gardées dans le journal. */
    public static final int JOURNAL_MAX = 120;

    public enum Access { LIBRE, MEMBRES }

    /**
     * Type de bibliothèque : qui y prend, qui y dépose, ses arrivages. {@code model} = modèle de pourcentages de
     * départ (0 Publique, 1 Réservée, 2 Privée) ; {@code ownerKind} = ce que désigne le propriétaire.
     */
    public enum Type {
        VILLAGE("Village", "Bibliothèque de village", 0, true, Access.LIBRE, "village"),
        ACADEMIE("Académie", "Bibliothèque de l'Académie", 0, true, Access.MEMBRES, "village"),
        CLAN("Clan", "Bibliothèque de clan", 0, true, Access.MEMBRES, "clan"),
        ANBU("ANBU", "Bibliothèque ANBU", 2, true, Access.MEMBRES, "village"),
        PALAIS("Palais du Kage", "Réserve du palais", 2, true, Access.MEMBRES, "village"),
        HOPITAL("Hôpital", "Bibliothèque de l'hôpital", 0, true, Access.MEMBRES, "village"),
        PERSONNELLE("Personnelle", "Bibliothèque personnelle", 0, false, Access.MEMBRES, "personnage");

        public final String label, title, ownerKind;
        public final int model;
        public final boolean drawsByDefault;
        public final Access defaultAccess;

        Type(String label, String title, int model, boolean drawsByDefault, Access defaultAccess, String ownerKind) {
            this.label = label;
            this.title = title;
            this.model = model;
            this.drawsByDefault = drawsByDefault;
            this.defaultAccess = defaultAccess;
            this.ownerKind = ownerKind;
        }

        /** Tirage commun (catalogue hors secrets) actif par défaut : tous sauf Clan (sa réserve seulement). */
        public boolean commonsByDefault() { return this != CLAN; }

        /** Lecture sur place possible (avec un maître présent) : Clan, Académie, Palais. */
        public boolean onsiteReading() { return this == CLAN || this == ACADEMIE || this == PALAIS; }

        /** Lecture tolérante : « palais_du_kage », « Hôpital », « perso »… null si inconnu. */
        public static Type parse(String s) {
            if (s == null) return null;
            String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            return switch (n) {
                case "VILLAGE", "PUBLIQUE" -> VILLAGE;
                case "ACADEMIE", "ACADEMY", "RESERVEE" -> ACADEMIE;
                case "CLAN" -> CLAN;
                case "ANBU" -> ANBU;
                case "PALAIS", "PALAIS_DU_KAGE", "KAGE", "PRIVEE" -> PALAIS;
                case "HOPITAL", "HOSPITAL", "MEDICAL" -> HOPITAL;
                case "PERSONNELLE", "PERSO", "PERSONNEL" -> PERSONNELLE;
                default -> null;
            };
        }

        /** Migration v1 → v2 : Publique → Village, Réservée → Académie, Privée → Palais. */
        public static Type fromModel(int model) {
            return model == 1 ? ACADEMIE : model == 2 ? PALAIS : VILLAGE;
        }
    }

    /**
     * Rouleau déposé : identité complète de l'objet (technique, parchemin_id, objet sérialisé), qui, quand, et la
     * bibliothèque d'où il a été volé (vide si le rouleau n'a jamais été volé).
     */
    public record Deposit(String techId, String scrollId, String item, String by, long at, String stolenFrom) {}

    /** Ligne du journal : {@code act} = depot | prise | vol ; {@code shelf} = fonds | arrivages. */
    public record LogEntry(long at, String who, String act, String shelf, String techId, String techName, char rank) {}

    public final String id;
    public String name;
    public String world;
    public int x, y, z;

    public Type type = Type.VILLAGE;
    /** Village, nom de clan, ou UUID du personnage (personnelle). Vide = sans restriction de village. */
    public String owner = "";
    /** Nom affiché du propriétaire (personnage d'une bibliothèque personnelle). */
    public String ownerName = "";
    public Access access = Access.LIBRE;
    /** Arrivages tirés au sort actifs (désactivés par défaut pour Clan et Personnelle). */
    public boolean draws = true;
    /** Personnages autorisés d'une bibliothèque personnelle : UUID → nom. */
    public final Map<String, String> allowed = new LinkedHashMap<>();
    /** Ouverture temporaire par le staff (ms epoch, 0 = fermée). */
    public long openUntil = 0L;

    // ─── vol (non-membres d'une bibliothèque « membres ») ───
    /** Vol possible (canal immobile devant le meuble). */
    public boolean theft = true;
    /** Durée de base du vol, en secondes (multipliée selon le rang du rouleau). */
    public int theftSeconds = 6;
    /** Alerte aux membres : nomme le voleur (sinon « quelqu'un »). */
    public boolean theftNamed = false;

    // ─── secrets ───
    /** Tirage commun (catalogue hors secrets) en plus de la réserve. */
    public boolean commons = true;
    /** Réserve réglée par le staff ({@link #reserve}) ; sinon réserve par défaut du type (calculée). */
    public boolean reserveCustom = false;
    /** Réserve de techniques exclusives : identifiant → pourcentage de tirage propre. */
    public final Map<String, Double> reserve = new LinkedHashMap<>();

    public final Deposit[] fonds = new Deposit[FONDS];
    public final List<LogEntry> journal = new ArrayList<>();

    // ─── arrivages (tirage) ───
    public int count = 6;
    public int delayHours = 3;
    public int model = 0;
    public double[] byRank = PUBLIQUE.clone();
    public final Map<String, Double> overrides = new HashMap<>();
    /** Tirage en cours : identifiant de technique par case (null = vide), identifiant du rouleau à créer. */
    public String[] cells = new String[9];
    public String[] scrollIds = new String[9];
    public long nextRoll = 0L;

    public Library(String id) {
        this.id = id;
    }

    /** Applique les réglages par défaut d'un type (accès, arrivages, modèle de pourcentages). */
    public void applyTypeDefaults(Type t) {
        type = t;
        access = t.defaultAccess;
        draws = t.drawsByDefault;
        commons = t.commonsByDefault();
        model = t.model;
        byRank = model(t.model);
    }

    public boolean openNow() { return openUntil > System.currentTimeMillis(); }

    public int freeFondsCell() {
        for (int i = 0; i < FONDS; i++) if (fonds[i] == null) return i;
        return -1;
    }

    public int fondsCount() {
        int n = 0;
        for (Deposit d : fonds) if (d != null) n++;
        return n;
    }

    public void log(String who, String act, String shelf, String techId, String techName, char rank) {
        journal.add(new LogEntry(System.currentTimeMillis(), who, act, shelf, techId, techName, rank));
        while (journal.size() > JOURNAL_MAX) journal.remove(0);
    }

    public boolean at(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(world)
                && l.getBlockX() == x && l.getBlockY() == y && l.getBlockZ() == z;
    }

    public double distanceSq(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return Double.MAX_VALUE;
        double dx = l.getX() - (x + 0.5), dy = l.getY() - (y + 0.5), dz = l.getZ() - (z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    public static double[] model(int m) {
        return (m == 1 ? RESERVEE : m == 2 ? PRIVEE : PUBLIQUE).clone();
    }
}
