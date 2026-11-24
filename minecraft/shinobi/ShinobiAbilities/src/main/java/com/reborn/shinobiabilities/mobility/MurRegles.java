package com.reborn.shinobiabilities.mobility;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Règles pures de l'essai de la marche sur les murs (lot 5, étape C0, {@link MurTest}) : aucune classe du serveur, tout
 * se teste sans lui ({@code MurReglesTest}).
 *
 * <ul>
 *   <li><b>Protocole</b> {@value #CANAL} (JSON UTF-8 brut, champ {@code v} = {@value #VERSION}, champs inconnus
 *       ignorés) : lecture des demandes du mod ({@link #lire}), écriture des trois messages du serveur
 *       ({@link #permis}, {@link #etat}, {@link #refus}) ;</li>
 *   <li><b>Faces</b> ({@link Face}) : la normale {@code n} de la paroi, cardinale, qui pointe du mur vers l'air ;</li>
 *   <li><b>Contrôle de paroi</b> ({@link #murDerriere}) : un bloc dont la face tournée vers le joueur est pleine se
 *       trouve à portée derrière lui, dans la direction {@code −n} ;</li>
 *   <li><b>Paquets refusés</b> ({@link CompteurRefus}) : un paquet de déplacement refusé compte une fois, même quand
 *       le serveur lui donne deux raisons ;</li>
 *   <li><b>Bilan</b> ({@link Compteurs}) : les chiffres de l'essai, écrits pour quelqu'un qui n'est pas développeur.</li>
 * </ul>
 */
public final class MurRegles {

    public static final String CANAL = "reborn:mur";
    public static final int VERSION = 1;
    /** Face envoyée quand l'entité n'est plus au mur. */
    public static final String SANS_FACE = "-";

    /** Accroche : la surface du mur est à cette distance au plus derrière {@code P} (debout, {@code P} en est à 0,3). */
    public static final double PORTEE_ACCROCHE = 1.3;
    /** Marche : la surface du mur est à cette distance au plus derrière {@code P} (couché, {@code P} en est à 0,9). */
    public static final double PORTEE_MARCHE = 1.5;
    /** Hauteur lue au-dessus de {@code P} : le milieu du cube du serveur (0,6 de haut), donc les pieds à l'accroche. */
    public static final double HAUTEUR_CUBE = 0.3;
    /** Hauteur du torse d'un joueur debout : là où le cube se trouve une fois la bascule finie. */
    public static final double HAUTEUR_TORSE = 0.9;
    /** Le serveur relit la paroi et l'état du joueur à cette période (ticks). */
    public static final int PERIODE_CONTROLE = 5;
    /** Un paquet refusé juste après le lâcher (le cube redevient debout) est encore compté au mur (ticks). */
    public static final int SORTIE_TICKS = 40;
    /** Un message du mod plus long que cela n'est pas lu (octets). */
    public static final int TAILLE_MAX = 256;
    /** Plafond du nombre de corrections qu'un mod peut annoncer pour une marche. */
    public static final int CORRECTIONS_MAX = 100_000;

    private static final double EPS = 1.0e-6;

    private MurRegles() {}

    /* ================================================================ faces */

    /**
     * Face de la paroi : la direction de sa normale {@code n}, du mur vers l'air. {@code E} : le mur est à l'ouest du
     * joueur, son corps part vers l'est.
     */
    public enum Face {
        N(0, -1), S(0, 1), E(1, 0), W(-1, 0);

        /** Composantes de la normale (une seule n'est pas nulle). */
        public final int nx, nz;

        Face(int nx, int nz) {
            this.nx = nx;
            this.nz = nz;
        }

        /** La face de ce code ({@code N}, {@code S}, {@code E}, {@code W}), ou null. */
        public static Face de(String code) {
            if (code == null) return null;
            return switch (code) {
                case "N" -> N;
                case "S" -> S;
                case "E" -> E;
                case "W" -> W;
                default -> null;
            };
        }
    }

    /* ================================================================ protocole, C → S */

    public enum Action { ACCROCHE, FACE, LACHE }

    /**
     * Une demande du mod. {@code face} : pour {@link Action#ACCROCHE} et {@link Action#FACE} ; {@code corrections} :
     * pour {@link Action#LACHE}, le nombre de corrections de position que le mod a reçues pendant la marche.
     */
    public record Demande(Action action, Face face, int corrections) {}

    /** Lit un message du mod ; null s'il est trop long, mal formé, d'une autre version ou sans face valable. */
    public static Demande lire(byte[] octets) {
        if (octets == null || octets.length == 0 || octets.length > TAILLE_MAX) return null;
        return lire(new String(octets, StandardCharsets.UTF_8));
    }

    /** Lit un message du mod (texte JSON) ; null s'il n'est pas valable. */
    public static Demande lire(String brut) {
        if (brut == null) return null;
        try {
            JsonElement racine = JsonParser.parseString(brut);
            if (!racine.isJsonObject()) return null;
            JsonObject o = racine.getAsJsonObject();
            if (!entier(o, "v") || o.get("v").getAsInt() != VERSION) return null;
            String a = texte(o, "a");
            if (a == null) return null;
            return switch (a) {
                case "accroche" -> avecFace(Action.ACCROCHE, o);
                case "face" -> avecFace(Action.FACE, o);
                case "lache" -> new Demande(Action.LACHE, null,
                        entier(o, "c") ? Math.max(0, Math.min(CORRECTIONS_MAX, o.get("c").getAsInt())) : 0);
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Demande avecFace(Action action, JsonObject o) {
        Face f = Face.de(texte(o, "f"));
        return f == null ? null : new Demande(action, f, 0);
    }

    private static boolean entier(JsonObject o, String champ) {
        JsonElement e = o.get(champ);
        if (e == null || !e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber()) return false;
        double d = e.getAsDouble();
        return d == Math.rint(d) && Math.abs(d) <= Integer.MAX_VALUE;
    }

    private static String texte(JsonObject o, String champ) {
        JsonElement e = o.get(champ);
        return e != null && e.isJsonPrimitive() && ((JsonPrimitive) e).isString() ? e.getAsString() : null;
    }

    /* ================================================================ protocole, S → C */

    /** {@code {"v":1,"t":"permis","on":true}} : le joueur peut s'accrocher ; {@code false} : interdit, il lâche. */
    public static byte[] permis(boolean on) {
        JsonObject o = base("permis");
        o.addProperty("on", on);
        return octets(o);
    }

    /** {@code {"v":1,"t":"etat","e":4182,"f":"E"}} : l'entité marche sur un mur de cette face ; null : elle n'y est plus. */
    public static byte[] etat(int entite, Face face) {
        JsonObject o = base("etat");
        o.addProperty("e", entite);
        o.addProperty("f", face == null ? SANS_FACE : face.name());
        return octets(o);
    }

    /** {@code {"v":1,"t":"refus","m":"…"}} : accroche ou changement de face refusé. */
    public static byte[] refus(String message) {
        JsonObject o = base("refus");
        o.addProperty("m", message == null ? "" : message);
        return octets(o);
    }

    private static JsonObject base(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("t", type);
        return o;
    }

    private static byte[] octets(JsonObject o) {
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    /* ================================================================ contrôle de paroi */

    /** Lecture du monde par le contrôle de paroi : rien d'autre n'est demandé au serveur. */
    @FunctionalInterface
    public interface LecteurBlocs {
        /**
         * Les boîtes de collision du bloc, dans son repère (0 à 1 sur chaque axe), chacune sous la forme
         * {@code {minX, minY, minZ, maxX, maxY, maxZ}} ; vide ou null : pas de collision.
         */
        Collection<double[]> boites(int x, int y, int z);
    }

    /**
     * Un bloc plein est-il derrière le joueur ? On part de {@code (px, py, pz)} (la hauteur {@code py} est celle qu'on
     * veut lire : {@code P.y} + {@link #HAUTEUR_CUBE} ou {@link #HAUTEUR_TORSE}) et on recule le long de {@code −n} :
     * vrai dès qu'un bloc dont la face tournée vers {@code n} est pleine ({@link #facePleine}) a cette face à
     * {@code portee} au plus.
     */
    public static boolean murDerriere(LecteurBlocs blocs, double px, double py, double pz, Face face, double portee) {
        int y = (int) Math.floor(py);
        for (int[] b : blocsDerriere(px, pz, face, portee)) {
            if (facePleine(blocs.boites(b[0], y, b[1]), face)) return true;
        }
        return false;
    }

    /**
     * Les colonnes {@code {x, z}} dont la face tournée vers {@code n} est entre 0 et {@code portee} derrière le point,
     * de la plus proche à la plus lointaine. La colonne où se tient le point n'en fait pas partie : sa face est devant lui.
     */
    public static List<int[]> blocsDerriere(double px, double pz, Face face, double portee) {
        boolean surX = face.nx != 0;
        double a = surX ? px : pz;
        int autre = (int) Math.floor(surX ? pz : px);
        List<int[]> out = new ArrayList<>(2);
        if (face.nx + face.nz > 0) {
            // le mur est du côté des coordonnées qui baissent : la face du bloc b est à b + 1, à (a − b − 1) du point
            for (int b = (int) Math.floor(a - 1 + EPS); a - (b + 1) <= portee + EPS; b--) out.add(colonne(surX, b, autre));
        } else {
            // le mur est du côté des coordonnées qui montent : la face du bloc b est à b, à (b − a) du point
            for (int b = (int) Math.ceil(a - EPS); b - a <= portee + EPS; b++) out.add(colonne(surX, b, autre));
        }
        return out;
    }

    private static int[] colonne(boolean surX, int b, int autre) {
        return surX ? new int[] {b, autre} : new int[] {autre, b};
    }

    /**
     * La face du bloc tournée vers {@code n} est-elle pleine ? Les boîtes de collision qui touchent ce bord du bloc
     * doivent, ensemble, couvrir tout le carré (un cube plein, deux dalles empilées, le dos d'un escalier : oui ; une
     * dalle, une barrière, un bloc en retrait : non).
     */
    public static boolean facePleine(Collection<double[]> boites, Face face) {
        if (boites == null || boites.isEmpty()) return false;
        boolean surX = face.nx != 0;
        boolean coteMax = face.nx + face.nz > 0;
        int axe = surX ? 0 : 2, travers = surX ? 2 : 0;
        List<double[]> rects = new ArrayList<>(boites.size());
        TreeSet<Double> us = new TreeSet<>(List.of(0.0, 1.0)), vs = new TreeSet<>(List.of(0.0, 1.0));
        for (double[] b : boites) {
            if (b == null || b.length < 6) continue;
            boolean touche = coteMax ? b[axe + 3] >= 1 - EPS : b[axe] <= EPS;
            if (!touche) continue;
            double u0 = borne(b[travers]), u1 = borne(b[travers + 3]), v0 = borne(b[1]), v1 = borne(b[4]);
            if (u1 - u0 <= EPS || v1 - v0 <= EPS) continue;
            rects.add(new double[] {u0, v0, u1, v1});
            us.add(u0);
            us.add(u1);
            vs.add(v0);
            vs.add(v1);
        }
        if (rects.isEmpty()) return false;
        Double[] u = us.toArray(new Double[0]), v = vs.toArray(new Double[0]);
        for (int i = 0; i + 1 < u.length; i++) {
            if (u[i + 1] - u[i] <= EPS) continue;
            double um = (u[i] + u[i + 1]) / 2;
            for (int j = 0; j + 1 < v.length; j++) {
                if (v[j + 1] - v[j] <= EPS) continue;
                double vm = (v[j] + v[j + 1]) / 2;
                boolean couvert = false;
                for (double[] r : rects) {
                    if (um > r[0] && um < r[2] && vm > r[1] && vm < r[3]) {
                        couvert = true;
                        break;
                    }
                }
                if (!couvert) return false;
            }
        }
        return true;
    }

    private static double borne(double d) { return d < 0 ? 0 : d > 1 ? 1 : d; }

    /* ================================================================ paquets refusés */

    /** Les quatre raisons d'un refus de déplacement ({@code PlayerFailMoveEvent.FailReason}). */
    public enum Raison {
        ECART("écart de position"),
        BLOC("traversée de bloc"),
        VITESSE("trop rapide"),
        CHUNK("zone non chargée");

        private final String libelle;

        Raison(String libelle) { this.libelle = libelle; }

        public String libelle() { return libelle; }

        /** La raison de ce nom du serveur ; null pour un nom inconnu (une cinquième raison ajoutée plus tard). */
        public static Raison de(String nomServeur) {
            if (nomServeur == null) return null;
            return switch (nomServeur) {
                case "MOVED_WRONGLY" -> ECART;
                case "CLIPPED_INTO_BLOCK" -> BLOC;
                case "MOVED_TOO_QUICKLY" -> VITESSE;
                case "MOVED_INTO_UNLOADED_CHUNK" -> CHUNK;
                default -> null;
            };
        }
    }

    /**
     * Compte les <b>paquets</b> de déplacement refusés, pas les raisons. Sur Paper 26.2, un paquet refusé pour
     * {@link Raison#ECART} est aussitôt refusé une seconde fois pour {@link Raison#BLOC} : deux événements, un seul
     * paquet. Deux refus appartiennent au même paquet quand ils tombent au même tick du serveur pour la même position
     * visée (après un refus, le serveur n'écoute plus de déplacement avant d'avoir replacé le joueur).
     */
    public static final class CompteurRefus {
        private int paquets;
        private final Map<Raison, Integer> parRaison = new EnumMap<>(Raison.class);
        /** Traversées de bloc tombées seules : le vrai juge, sans écart de position refusé dans le même paquet. */
        private int blocSeul;
        private boolean vu;
        private long tick;
        private double x, y, z;
        private boolean ecartDansLePaquet;

        /**
         * Note un refus de déplacement que rien n'a accepté. {@code tick} : le tick du serveur ; {@code (x, y, z)} : la
         * position visée par le paquet.
         */
        public void refuse(long tick, double x, double y, double z, Raison raison) {
            if (raison == null) return;
            boolean memePaquet = vu && this.tick == tick && this.x == x && this.y == y && this.z == z;
            if (!memePaquet) {
                paquets++;
                ecartDansLePaquet = false;
            }
            parRaison.merge(raison, 1, Integer::sum);
            if (raison == Raison.ECART) ecartDansLePaquet = true;
            if (raison == Raison.BLOC && !ecartDansLePaquet) blocSeul++;
            vu = true;
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int paquets() { return paquets; }

        public int raison(Raison r) { return parRaison.getOrDefault(r, 0); }

        public int blocSeul() { return blocSeul; }
    }

    /* ================================================================ cadence des messages */

    /** Au plus {@code max} messages par fenêtre glissante : au-delà, le message n'est pas lu. */
    public static final class Cadence {
        private final int max;
        private final long fenetreMs;
        private final ArrayDeque<Long> heures = new ArrayDeque<>();

        public Cadence(int max, long fenetreMs) {
            this.max = max;
            this.fenetreMs = fenetreMs;
        }

        public boolean passe(long maintenantMs) {
            while (!heures.isEmpty() && maintenantMs - heures.peekFirst() >= fenetreMs) heures.pollFirst();
            if (heures.size() >= max) return false;
            heures.addLast(maintenantMs);
            return true;
        }
    }

    /* ================================================================ bilan */

    /**
     * Les chiffres de l'essai d'un membre du staff, de {@code /murtest raz} (ou de sa première commande) jusqu'au bilan.
     * Ils survivent à sa déconnexion : une expulsion se lit après s'être reconnecté.
     */
    public static final class Compteurs {
        public int accroches, accrochesRefusees;
        /** Dernière raison d'un refus d'accroche ou de changement de face. */
        public String dernierRefus;
        /** Lâchers décidés par le serveur (plus de mur, état refusé, KO, téléportation…), et la dernière raison. */
        public int lachersServeur;
        public String dernierLacher;
        /** Expulsions du serveur (« kick ») reçues au mur, et la dernière cause. */
        public int expulsions;
        public String derniereExpulsion;
        /** Paquets refusés au mur (et pendant {@link #SORTIE_TICKS} après), puis hors du mur : le témoin. */
        public final CompteurRefus refusMur = new CompteurRefus(), refusHorsMur = new CompteurRefus();
        /** Écarts de position acceptés par le filet. */
        public int filet;
        /** Corrections de position annoncées par le mod dans ses {@code lache}. */
        public int corrections;
        /** Lâchers du serveur dont le mod n'a pas renvoyé le compte de corrections. */
        public int sansCompte;
        /** Le serveur a lâché : le {@code lache} du mod qui suit ne vaut que pour son compte de corrections. */
        public boolean attendCompte;
        public int coupsMelee, projectiles, degats;
        /** Clics d'attaque reçus par le testeur hors du mur (tombé en pleine série) : à part, hors de l'essai 5. */
        public int coupsHorsMur;
        /** Pose de nage reposée par le serveur (quelque chose l'avait enlevée). */
        public int poseReposee;
        /** Ticks où le vol était autorisé au mur : à zéro, sinon l'essai 1 ne prouve rien. */
        public int volAutorise;

        private long tempsMurMs;
        private boolean enMarche;
        private long debutMarcheMs;
        private long finMarcheTick = Long.MIN_VALUE / 2;

        public void debutMarche(long maintenantMs) {
            accroches++;
            reprendreMarche(maintenantMs);
        }

        public void finMarche(long maintenantMs, long tick) {
            tempsMurMs = tempsMurMs(maintenantMs);
            enMarche = false;
            finMarcheTick = tick;
        }

        /** Une remise à zéro en pleine marche : le temps repart de maintenant, la marche continue. */
        public void reprendreMarche(long maintenantMs) {
            enMarche = true;
            debutMarcheMs = maintenantMs;
        }

        /** Temps passé au mur, marche en cours comprise. */
        public long tempsMurMs(long maintenantMs) {
            return tempsMurMs + (enMarche ? Math.max(0, maintenantMs - debutMarcheMs) : 0);
        }

        /** Le compteur d'un refus tombé à ce tick : au mur (et juste après le lâcher), ou hors du mur. */
        public CompteurRefus refus(boolean auMur, long tick) {
            return auMur || tick - finMarcheTick <= SORTIE_TICKS ? refusMur : refusHorsMur;
        }

        /**
         * Le bilan, une phrase courte et un chiffre par ligne. {@code face} : la face du mur où le joueur se tient en ce
         * moment, null s'il n'y est pas.
         */
        public List<String> lignes(boolean permis, boolean filetAllume, Face face, long maintenantMs) {
            long temps = tempsMurMs(maintenantMs);
            List<String> l = new ArrayList<>(32);
            l.add("Permis : " + (permis ? "allumé" : "éteint"));
            l.add("Filet : " + (filetAllume ? "allumé" : "éteint"));
            l.add("Au mur en ce moment : " + (face == null ? "non" : "oui, face " + face.name()));
            l.add("Accroches acceptées : " + accroches);
            l.add("Accroches refusées : " + accrochesRefusees);
            if (dernierRefus != null) l.add("Dernier refus : " + dernierRefus);
            l.add("Temps passé au mur : " + duree(temps));
            l.add("Expulsions du serveur pendant le mur : " + expulsions);
            if (derniereExpulsion != null) l.add("Dernière expulsion : " + derniereExpulsion);
            l.add("Lâchers décidés par le serveur : " + lachersServeur);
            if (dernierLacher != null) l.add("Dernier lâcher du serveur : " + dernierLacher);
            l.add("Paquets de déplacement refusés au mur : " + refusMur.paquets());
            for (Raison r : Raison.values()) l.add("  raison « " + r.libelle() + " » : " + refusMur.raison(r));
            l.add("  " + Raison.BLOC.libelle() + " seule, sans écart : " + refusMur.blocSeul());
            l.add("  (un même paquet peut porter deux raisons)");
            l.add("  (le mur compte jusqu'à 2 s après le lâcher)");
            l.add("Paquets acceptés par le filet : " + filet);
            l.add("Paquets refusés hors du mur, pour comparer : " + refusHorsMur.paquets());
            l.add("Corrections de position vues par le mod : " + corrections);
            if (enMarche) l.add("  (marche en cours non comptée, lâche le mur pour l'avoir)");
            l.add("Corrections par minute de mur : " + parMinute(corrections, enMarche ? tempsMurMs : temps));
            l.add("Lâchers du serveur sans compte du mod : " + sansCompte);
            l.add("Coups au corps à corps comptés par le serveur : " + coupsMelee);
            l.add("Essai 5, part sur 30 clics : " + Math.round(coupsMelee * 100.0 / 30.0) + " %");
            l.add("Clics reçus hors du mur, non comptés à l'essai 5 : " + coupsHorsMur);
            l.add("Projectiles qui ont touché : " + projectiles);
            l.add("Dégâts subis au mur, techniques comprises : " + degats);
            l.add("Pose de nage reposée par le serveur : " + poseReposee);
            l.add("Ticks avec le vol autorisé au mur : " + volAutorise);
            l.add("  (ces deux derniers chiffres doivent rester à zéro)");
            return l;
        }
    }

    /** {@code 0 s}, {@code 47 s}, {@code 2 min 04 s}. */
    public static String duree(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s < 60 ? s + " s" : String.format(Locale.ROOT, "%d min %02d s", s / 60, s % 60);
    }

    /** Un nombre d'événements ramené à la minute de mur, une décimale, virgule française ; pas de taux avant 10 s de mur. */
    public static String parMinute(int nombre, long tempsMs) {
        if (tempsMs < 10_000) return "trop tôt pour le dire";
        return String.format(Locale.FRENCH, "%.1f", nombre * 60_000.0 / tempsMs);
    }
}
