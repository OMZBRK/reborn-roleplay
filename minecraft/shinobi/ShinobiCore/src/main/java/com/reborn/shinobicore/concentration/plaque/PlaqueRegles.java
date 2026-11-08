package com.reborn.shinobicore.concentration.plaque;

import com.reborn.shinobicore.staff.panel.StaffGrades;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Règles des plaques (lot 4, étape B), sans dépendance au serveur : portées, cône de vue, droits, saisie, limites de
 * débit. Tout ce qui décide si le texte d'une plaque part chez un client passe par ici (testé par
 * {@code PlaqueReglesTest}).
 *
 * <p>Décisions du 07/11 : seules les plaques posées par le staff se lisent ; texte envoyé au seul joueur concentré
 * devant la plaque (portée 6 par défaut, réglable de 2 à 16, tolérance serveur + 1,5 bloc, ligne de vue) ; ancres
 * envoyées à 32 blocs sans le texte ; droits : Helper voit (liste, aperçu, téléport), Modérateur crée / modifie / déplace
 * / supprime, Admin restaure (corbeille 30 jours), animateur en mode animation : plaques temporaires de 24 h au plus,
 * les siennes seulement.
 */
public final class PlaqueRegles {

    public static final int TEXTE_MAX = 700, TEXTE_AVERTI = 450;
    public static final int PORTEE_MIN = 2, PORTEE_MAX = 16, PORTEE_DEFAUT = 6;
    /** Le serveur accepte jusqu'à portée + 1,5 bloc (latence, caméra à l'épaule). */
    public static final double TOLERANCE = 1.5;
    /** Ancres envoyées aux joueurs concentrés (blocs). */
    public static final double ANCRES_PORTEE = 32;
    /** Visée du staff pour poser ou déplacer une plaque (blocs depuis les yeux), + 1 bloc de tolérance au serveur. */
    public static final double VISEE_STAFF = 8;
    /** Taille maximale d'une boîte (stèle de plusieurs blocs), par côté. */
    public static final int BOITE_MAX = 32;
    /**
     * Cône de vue côté serveur (degrés, autour du regard de la tête) : le client choisit à 25° autour de la caméra ; la
     * caméra à l'épaule et la latence demandent de la marge. Garde-fou contre un client qui fouillerait tout autour de
     * lui ; la portée et la ligne de vue restent les vrais verrous.
     */
    public static final double CONE_SERVEUR = 60;
    public static final int ANIM_EXPIRE_MAX_MIN = 1440;
    public static final int CORBEILLE_JOURS = 30;
    /** Bascules on / off par seconde, demandes de texte par seconde (par joueur). */
    public static final int BASCULES_S = 4, LECTURES_S = 10;
    /** Liste du staff : rayon par défaut et maximal. */
    public static final int LISTE_RAYON = 64, LISTE_RAYON_MAX = 256;

    private PlaqueRegles() {}

    /* ================================================================ géométrie */

    /** Distance d'un point à une boîte {minX, minY, minZ, maxX, maxY, maxZ} (0 dedans). */
    public static double distanceBoite(double px, double py, double pz, double[] b) {
        double dx = Math.max(Math.max(b[0] - px, 0), px - b[3]);
        double dy = Math.max(Math.max(b[1] - py, 0), py - b[4]);
        double dz = Math.max(Math.max(b[2] - pz, 0), pz - b[5]);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Point de la boîte le plus proche de (px, py, pz), rentré de {@code retrait} vers le centre (rayon de vue). */
    public static double[] pointProche(double px, double py, double pz, double[] b, double retrait) {
        double[] p = new double[3];
        double[] c = centre(b);
        double[] q = {px, py, pz};
        for (int i = 0; i < 3; i++) {
            double v = Math.max(b[i], Math.min(b[i + 3], q[i]));
            // rentré vers le centre : le rayon ne s'arrête pas sur l'arête voisine
            p[i] = v + Math.signum(c[i] - v) * Math.min(retrait, Math.abs(c[i] - v));
        }
        return p;
    }

    public static double[] centre(double[] b) {
        return new double[]{(b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2};
    }

    /** Angle (degrés) entre la direction {@code (dx, dy, dz)} et le vecteur œil → cible. */
    public static double angle(double ox, double oy, double oz, double dx, double dy, double dz, double tx, double ty, double tz) {
        double vx = tx - ox, vy = ty - oy, vz = tz - oz;
        double nv = Math.sqrt(vx * vx + vy * vy + vz * vz), nd = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (nv < 1e-6 || nd < 1e-6) return 0;
        double c = (vx * dx + vy * dy + vz * dz) / (nv * nd);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
    }

    /** L'œil est à portée de lecture : distance à la boîte ≤ portée + tolérance. */
    public static boolean aPortee(double ox, double oy, double oz, double[] b, int portee) {
        return distanceBoite(ox, oy, oz, b) <= bornerPortee(portee) + TOLERANCE;
    }

    /**
     * Dans le cône du serveur : le centre de la boîte, ou son point le plus proche (grande stèle vue de près), à moins de
     * {@link #CONE_SERVEUR}° du regard.
     */
    public static boolean dansCone(double ox, double oy, double oz, double dx, double dy, double dz, double[] b) {
        double[] c = centre(b);
        if (angle(ox, oy, oz, dx, dy, dz, c[0], c[1], c[2]) <= CONE_SERVEUR) return true;
        double[] p = pointProche(ox, oy, oz, b, 0.05);
        return angle(ox, oy, oz, dx, dy, dz, p[0], p[1], p[2]) <= CONE_SERVEUR;
    }

    /**
     * La face {@code face} de la boîte {@code b} est tournée vers l'œil : l'œil est du côté extérieur du plan de cette face
     * (vide = tous côtés). Une plaque posée sur la face intérieure d'un mur de tombeau ne se lit pas de dehors, face à
     * la face extérieure du même bloc : le mur qui doit bloquer est l'ancre elle-même (revue du 07/11).
     */
    public static boolean faceVisible(String face, double ex, double ey, double ez, double[] b) {
        return switch (Ancre.face(face)) {
            case "north" -> ez < b[2];
            case "south" -> ez > b[5];
            case "west" -> ex < b[0];
            case "east" -> ex > b[3];
            case "down" -> ey < b[1];
            case "up" -> ey > b[4];
            default -> true;
        };
    }

    /** Un point (de l'impact d'un rayon) est dans la boîte agrandie de {@code marge}. */
    public static boolean dans(double[] b, double x, double y, double z, double marge) {
        return x >= b[0] - marge && x <= b[3] + marge && y >= b[1] - marge && y <= b[4] + marge && z >= b[2] - marge
                && z <= b[5] + marge;
    }

    /* ================================================================ ligne de vue */

    /** Premier bloc plein heurté par un rayon : point d'impact, bloc, et s'il s'agit d'une barrière (hitbox Nexo). */
    public record Impact(double x, double y, double z, int bx, int by, int bz, boolean barriere) { }

    /**
     * Lance un rayon depuis ({@code ox, oy, oz}) dans la direction unitaire ({@code dx, dy, dz}) sur {@code dist} blocs ;
     * renvoie le premier bloc plein heurté (blocs passables ignorés), ou null. Le serveur passe {@code World#rayTraceBlocks},
     * les tests une grille de blocs.
     */
    @FunctionalInterface
    public interface Traceur {
        Impact tracer(double ox, double oy, double oz, double dx, double dy, double dz, double dist);
    }

    /**
     * Ligne de vue des yeux à la boîte {@code b} : vers son point le plus proche (rentré de 5 cm) ou vers son centre. Le
     * rayon doit atteindre la boîte elle-même (impact dans la boîte, 2 cm de marge) sans avoir touché de bloc plein avant.
     * Seule exception, pour une entité ({@code entite}) : les <b>barrières</b> collées à sa boîte (hitbox d'un meuble Nexo)
     * sont traversées — jamais un mur, même collé à l'objet (revue du 07/11 : l'ancienne marge d'un bloc laissait passer
     * le texte d'un cadre ou d'un meuble à travers un mur d'un bloc).
     */
    public static boolean enVue(Traceur t, double ex, double ey, double ez, double[] b, boolean entite) {
        if (rayonLibre(t, ex, ey, ez, pointProche(ex, ey, ez, b, 0.05), b, entite)) return true;
        return rayonLibre(t, ex, ey, ez, centre(b), b, entite);
    }

    static boolean rayonLibre(Traceur t, double ox, double oy, double oz, double[] c, double[] b, boolean entite) {
        for (int essai = 0; essai < 4; essai++) {
            double dx = c[0] - ox, dy = c[1] - oy, dz = c[2] - oz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 0.05) return true;
            dx /= dist;
            dy /= dist;
            dz /= dist;
            Impact i = t.tracer(ox, oy, oz, dx, dy, dz, dist);
            if (i == null) return true;
            if (dans(b, i.x(), i.y(), i.z(), 0.02)) return true;
            boolean barriere = entite && i.barriere() && dans(b, i.bx() + 0.5, i.by() + 0.5, i.bz() + 0.5, 1.0);
            if (!barriere) return false;
            // hitbox du meuble : on repart juste derrière la barrière, vers la même cible
            double px = i.x() + dx * 0.02, py = i.y() + dy * 0.02, pz = i.z() + dz * 0.02;
            for (int k = 0; k < 40 && dansBloc(i.bx(), i.by(), i.bz(), px, py, pz); k++) {
                px += dx * 0.05;
                py += dy * 0.05;
                pz += dz * 0.05;
            }
            ox = px;
            oy = py;
            oz = pz;
        }
        return false;
    }

    private static boolean dansBloc(int bx, int by, int bz, double x, double y, double z) {
        return x >= bx && x <= bx + 1 && y >= by && y <= by + 1 && z >= bz && z <= bz + 1;
    }

    /* ================================================================ /plaque poser : arguments */

    /** Arguments de {@code /plaque poser} : portée (0 = défaut), expiration (minutes), indice du premier mot du texte. */
    public record ArgsPoser(int portee, int expire, int debut) { }

    /**
     * Lit les options en tête de {@code /plaque poser|objet} : portée <b>explicite</b> seulement ({@code p:6} ou
     * {@code p=6}), expiration ({@code +30m}, {@code +24h}). Un nombre seul en tête reste du texte : « 3 guerriers reposent
     * ici » garde son 3 (revue du 07/11).
     */
    public static ArgsPoser argsPoser(java.util.List<String> a) {
        int portee = 0, expire = 0, i = 0;
        for (; i < a.size(); i++) {
            String t = a.get(i);
            if (portee == 0 && t.matches("(?i)p[:=]\\d{1,2}")) { portee = Integer.parseInt(t.substring(2)); continue; }
            if (t.matches("\\+\\d{1,4}[mh]")) {
                int v = Integer.parseInt(t.substring(1, t.length() - 1));
                expire = t.endsWith("h") ? v * 60 : v;
                continue;
            }
            break;
        }
        return new ArgsPoser(portee, expire, i);
    }

    /* ================================================================ droits */

    /**
     * Accès à l'outil (liste, {@code /plaque}) : Helper, ou un animateur en mode animation — qui ne voit alors que <b>ses</b>
     * plaques ({@link #voirTout}).
     */
    public static boolean peutVoir(int grade, boolean animation) { return grade >= StaffGrades.HELPER || animation; }

    /**
     * Lire le texte de n'importe quelle plaque hors concentration, la liste de toutes, la corbeille, le téléport : Helper
     * et plus seulement (décision du 07/11). Un animateur sans grade n'a que ses plaques temporaires.
     */
    public static boolean voirTout(int grade) { return grade >= StaffGrades.HELPER; }

    public static boolean peutCreer(int grade, boolean animation) { return grade >= StaffGrades.MODO || animation; }

    /** Modifier, déplacer, supprimer : Modérateur ; l'animateur en mode animation, ses plaques temporaires seulement. */
    public static boolean peutModifier(int grade, boolean animation, boolean auteur, boolean temporaire) {
        return grade >= StaffGrades.MODO || animation && auteur && temporaire;
    }

    public static boolean peutRestaurer(int grade) { return grade >= StaffGrades.ADMIN; }

    /** Plaque créée en temporaire : un animateur sans grade Modérateur. */
    public static boolean temporaire(int grade, boolean animation) { return grade < StaffGrades.MODO && animation; }

    /* ================================================================ saisie */

    public static int bornerPortee(int p) { return Math.max(PORTEE_MIN, Math.min(PORTEE_MAX, p)); }

    /** Texte : null si accepté, sinon l'erreur à montrer au staff. */
    public static String validerTexte(int caracteres) {
        if (caracteres <= 0) return "Le texte est vide.";
        if (caracteres > TEXTE_MAX) return "Texte trop long (" + caracteres + " / " + TEXTE_MAX + " caractères).";
        return null;
    }

    /**
     * Expiration demandée en minutes (0 = jamais) : null si acceptée, sinon l'erreur. Une plaque temporaire (animateur)
     * expire forcément, dans 1 440 minutes au plus.
     */
    public static String validerExpire(boolean temporaire, int minutes) {
        if (minutes < 0) return "Expiration invalide.";
        if (temporaire && (minutes == 0 || minutes > ANIM_EXPIRE_MAX_MIN))
            return "En mode animation, une plaque est temporaire : 24 h au plus.";
        if (minutes > 60 * 24 * 365) return "Expiration trop lointaine (un an au plus).";
        return null;
    }

    /** Nouvelle expiration d'une plaque modifiée : instant ({@code 0} = jamais), drapeau temporaire, ou l'erreur. */
    public record Expiration(long expire, boolean temporaire, String erreur) { }

    /**
     * Expiration demandée à la modification ({@code minutes} depuis maintenant, 0 = jamais). Un <b>Modérateur</b> fixe ce
     * qu'il veut (un an au plus) ; « jamais » rend permanente une plaque temporaire d'animateur (elle n'est plus
     * temporaire). Un <b>animateur</b> (sa plaque temporaire) choisit de 1 à 1 440 minutes, mais la plaque meurt au plus
     * tard <b>24 h après sa création</b> : la modifier chaque jour ne la prolonge pas (revue du 07/11).
     */
    public static Expiration expireModif(boolean modo, boolean temporaire, long creeLe, long now, int minutes) {
        if (minutes < 0) return new Expiration(0, temporaire, "Expiration invalide.");
        if (modo) {
            String e = validerExpire(false, minutes);
            if (e != null) return new Expiration(0, temporaire, e);
            return new Expiration(minutes > 0 ? now + minutes * 60_000L : 0, temporaire && minutes > 0, null);
        }
        String e = validerExpire(true, minutes);
        if (e != null) return new Expiration(0, true, e);
        long plafond = (creeLe > 0 ? creeLe : now) + ANIM_EXPIRE_MAX_MIN * 60_000L;
        long exp = Math.min(now + minutes * 60_000L, plafond);
        if (exp <= now) return new Expiration(0, true, "Cette plaque temporaire a fait ses 24 h : elle ne se prolonge pas.");
        return new Expiration(exp, true, null);
    }

    /* ================================================================ halo (planche c6) */

    /** Lueur du halo par défaut (ivoire-or H1) : choisie, elle n'est pas stockée (absent = ivoire-or). */
    public static final String HALO_DEFAUT = "FFCC7A";
    public static final String HALO_AUCUN = "aucun";

    /** Saisie du halo : valeur à stocker (null = ivoire-or par défaut, {@code "aucun"}, {@code "RRGGBB"}) ou l'erreur. */
    public record Halo(String valeur, String erreur) {
        public boolean ok() { return erreur == null; }
    }

    /**
     * Halo saisi par le staff (volet, commande, fichier) : vide, {@code ivoire}, {@code ivoire-or}, {@code defaut} ou la
     * lueur par défaut {@code FFCC7A} → null (ivoire-or, rien de stocké) ; {@code aucun} → {@code "aucun"} ; sinon six
     * chiffres hexadécimaux, {@code #} facultatif, rendus en majuscules ({@code ^(aucun|[0-9A-Fa-f]{6})$}). Le reste est
     * refusé.
     */
    public static Halo lireHalo(String s) {
        String t = s == null ? "" : s.strip();
        if (t.startsWith("#")) {
            t = t.substring(1);
            if (t.isEmpty()) return new Halo(null, "Halo : « aucun » ou une couleur RRGGBB (ex. 6FB4FF).");
        }
        String b = t.toLowerCase(java.util.Locale.ROOT);
        if (b.isEmpty() || b.equals("ivoire") || b.equals("ivoire-or") || b.equals("defaut") || b.equals("défaut"))
            return new Halo(null, null);
        if (b.equals(HALO_AUCUN) || b.equals("sans") || b.equals("aucun-halo")) return new Halo(HALO_AUCUN, null);
        if (!t.matches("[0-9A-Fa-f]{6}")) return new Halo(null, "Halo : « aucun » ou une couleur RRGGBB (ex. 6FB4FF).");
        String v = t.toUpperCase(java.util.Locale.ROOT);
        return new Halo(v.equals(HALO_DEFAUT) ? null : v, null);
    }

    /** Libellé du halo pour le journal et la commande : « #FFCC7A (ivoire-or) », « aucun », « #6FB4FF ». */
    public static String libelleHalo(String h) {
        if (h == null) return "#" + HALO_DEFAUT + " (ivoire-or)";
        return HALO_AUCUN.equals(h) ? "aucun" : "#" + h;
    }

    /** Boîte : chaque côté de 1 à {@link #BOITE_MAX}. */
    public static boolean boiteValide(int dx, int dy, int dz) {
        return dx >= 1 && dy >= 1 && dz >= 1 && dx <= BOITE_MAX && dy <= BOITE_MAX && dz <= BOITE_MAX;
    }

    /** Identifiant court : « P » + compteur en base 36 (P1, P2… PZZ). */
    public static String id(int n) { return "P" + Integer.toString(Math.max(1, n), 36).toUpperCase(java.util.Locale.ROOT); }

    /** Motif de suppression : au moins 3 caractères. */
    public static boolean motifValide(String m) { return m != null && m.trim().length() >= 3; }

    /* ================================================================ débit */

    /**
     * Fenêtre glissante : au plus {@code max} événements par {@code fenetreMs} et par clé. Fil principal (pas de verrou) ;
     * {@link #oublier} à la déconnexion.
     */
    public static final class Limiteur<K> {
        private final int max;
        private final long fenetreMs;
        private final Map<K, ArrayDeque<Long>> par = new HashMap<>();

        public Limiteur(int max, long fenetreMs) {
            this.max = max;
            this.fenetreMs = fenetreMs;
        }

        public boolean essayer(K k, long now) {
            ArrayDeque<Long> q = par.computeIfAbsent(k, x -> new ArrayDeque<>());
            while (!q.isEmpty() && now - q.peekFirst() >= fenetreMs) q.pollFirst();
            if (q.size() >= max) return false;
            q.addLast(now);
            return true;
        }

        public void oublier(K k) { par.remove(k); }
    }
}
