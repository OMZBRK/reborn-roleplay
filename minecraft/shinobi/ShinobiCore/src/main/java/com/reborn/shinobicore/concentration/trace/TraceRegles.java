package com.reborn.shinobicore.concentration.trace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Règles des traces de pas (lot 4, étape C, planche c7, décision du 08/11), sans dépendance au serveur : qui marque, durée
 * de vie selon le sol, la météo et l'accroupi, espacement des pas, gabarit du pied, clé de piste anonyme. Testé par
 * {@code TraceReglesTest}.
 */
public final class TraceRegles {

    /**
     * Durée de vie de base (secondes) : 2 h, réglable ({@code concentration.traces.vie-base-secondes}). « Une trace de pas
     * reste » (08/11) : elle sert à savoir si quelqu'un est passé.
     */
    public static final int VIE_BASE_S = 7200;
    /**
     * Anneau d'un tronçon et plafond global (mémoire seule, perdues au redémarrage) ; le plafond : voir {@code TraceStore}. Ces
     * pas vivent des heures : l'anneau tient bien plus que le va-et-vient d'un joueur (3,6 pas/s en marche : 1024 pas = 4 min 40
     * sur place, 78 traversées de 13 pas), et le plafond tient 3 h de 50 joueurs (45 pas/s à 25 % de marche).
     */
    public static final int MAX_TRONCON = 1024, MAX_GLOBAL = 500_000;
    /** Pas examinés au plus, chaque seconde, pour oublier les expirés (un tour complet de 500 000 pas : 100 s). */
    public static final int BALAYAGE_PAR_ENVOI = 5_000;
    /** Relevé toutes les 4 ticks, envoi aux concentrés chaque seconde. */
    public static final long RELEVE_TICKS = 4L, ENVOI_TICKS = 20L;
    /** Rayon d'envoi : au-delà du fondu client (24 blocs) pour qu'un pas ne surgisse pas entre deux envois. */
    public static final double RAYON_ENVOI = 28.0;
    /** Pas envoyés à un même joueur au plus (le mod en dessine 256, les plus proches). */
    public static final int MAX_ENVOI = 320;

    /** Espacement des pas (blocs) à la taille de référence. */
    public static final double PAS_MARCHE = 1.2, PAS_SPRINT = 2.0, PAS_ACCROUPI = 0.8, PAS_CHARGE = 1.0;
    /** Écart latéral d'un pied à l'axe de marche (blocs, taille de référence), pointe tournée vers l'extérieur (degrés). */
    public static final double DECAL = 0.12, OUVERTURE = 6.0;
    /** Échelle du pied bornée (attribut d'échelle du personnage). */
    public static final double K_MIN = 0.6, K_MAX = 1.6;
    /**
     * Au-delà de cette vitesse (blocs/s), le joueur sprinte : le drapeau de sprint Minecraft veut dire trot <b>ou</b> sprint
     * (allures du 14/10 : trot × 1,15, soit 5 blocs/s ; sprint × 1,55, soit 6,7), même seuil que la satiété. Réglable
     * ({@code concentration.traces.vitesse-sprint}). Un saut en course va plus vite que le sol (× 1,27 en vanilla).
     */
    public static final double VITESSE_SPRINT = 5.8, FACTEUR_SAUT = 1.27;
    /** Sous ce ratio de vie, un blessé saigne (option {@code sang}, coupée au départ). */
    public static final double SEUIL_SANG = 0.5;

    /** Drapeaux d'un pas, sur le fil. */
    public static final int F_DROIT = 1, F_SPRINT = 2, F_ACCROUPI = 4, F_CHARGE = 8, F_SANG = 16;

    private TraceRegles() {}

    /* ================================================================ durée de vie */

    /** Le sol sous le pied : × 2 sable, neige, boue ; × 1 herbe, terre ; × 0,4 pierre, bois, chemin. */
    public enum Sol {
        MOU(2.0), NORMAL(1.0), DUR(0.4);

        public final double facteur;

        Sol(double f) { facteur = f; }
    }

    /** La météo à l'endroit du pas, celle de tout le monde (jamais une météo individuelle) : × 0,5 sous la pluie, × 0,3 en tempête. */
    public enum Meteo {
        CLAIR(1.0), PLUIE(0.5), TEMPETE(0.3);

        public final double facteur;

        Meteo(double f) { facteur = f; }
    }

    /** Classe un matériau ({@code Material#name()}) ; la pierre passe avant le sable : {@code SANDSTONE} est dur. */
    public static Sol sol(String m) {
        if (m == null) return Sol.NORMAL;
        if (m.equals("DIRT_PATH") || m.contains("STONE") || m.contains("BRICK") || m.contains("COBBLE") || m.contains("DEEPSLATE")
            || m.contains("PLANKS") || m.endsWith("_LOG") || m.endsWith("_WOOD") || m.endsWith("_STEM") || m.endsWith("_HYPHAE")
            || m.contains("ANDESITE") || m.contains("DIORITE") || m.contains("GRANITE") || m.contains("TUFF")
            || m.contains("BASALT") || m.contains("CONCRETE") || m.contains("TERRACOTTA") || m.contains("QUARTZ")
            || m.contains("PURPUR") || m.contains("PRISMARINE") || m.contains("OBSIDIAN") || m.contains("COPPER")
            || m.contains("IRON") || m.contains("BAMBOO") || m.endsWith("_SLAB") || m.endsWith("_STAIRS")
            || m.endsWith("_FENCE") || m.endsWith("_FENCE_GATE") || m.endsWith("_TRAPDOOR") || m.endsWith("_DOOR")) {
            return Sol.DUR;
        }
        if (m.contains("SAND") || m.contains("SNOW") || m.contains("MUD")) return Sol.MOU;
        return Sol.NORMAL;
    }

    /**
     * La météo qui compte pour ce pas. {@code type} : météo Reborn à cet endroit ({@code MeteoCommand#typeLieu}, 0 clair,
     * 1 pluie, 2 sable, 3 brume, 4 blizzard, 5 tempête de sable). {@code pluieIci}, {@code orageIci} : pluie et orage du
     * monde, là où il pleut vraiment ({@link #biomeSec}). À l'abri d'un toit, rien ne l'efface.
     */
    public static Meteo meteo(int type, boolean pluieIci, boolean orageIci, boolean abri) {
        if (abri) return Meteo.CLAIR;
        if (type == 4 || type == 5 || orageIci) return Meteo.TEMPETE;
        if (type == 1 || type == 2 || pluieIci) return Meteo.PLUIE;
        return Meteo.CLAIR;
    }

    /**
     * Un biome où la pluie du monde ne tombe jamais ({@code Biome#getKey} : désert, savanes, badlands, Nether, End, vide),
     * d'après les {@code has_precipitation: false} de vanilla ; l'API du serveur n'expose pas ce champ.
     */
    public static boolean biomeSec(String cle) {
        if (cle == null) return false;
        return cle.contains("desert") || cle.contains("savanna") || cle.contains("badlands") || cle.equals("the_void")
            || cle.equals("nether_wastes") || cle.equals("soul_sand_valley") || cle.equals("crimson_forest")
            || cle.equals("warped_forest") || cle.equals("basalt_deltas") || cle.equals("the_end")
            || cle.startsWith("end_") || cle.equals("small_end_islands");
    }

    /** Durée de vie (secondes, au moins une) : base × sol × météo × 0,5 accroupi. */
    public static int vieSecondes(int base, Sol sol, Meteo meteo, boolean accroupi) {
        double v = base * sol.facteur * meteo.facteur * (accroupi ? 0.5 : 1.0);
        return Math.max(1, (int) Math.round(v));
    }

    /* ================================================================ qui marque */

    /** Ce qui décide si un joueur laisse une trace maintenant. */
    public record Etat(boolean auSol, boolean modeStaff, boolean vanish, boolean spectateur, boolean vol, boolean eau,
                       boolean monture, boolean porte, boolean cinematique, boolean horsJeu) {
    }

    /**
     * Pas de trace : mode staff, vanish, spectateur, en vol, sur ou dans l'eau, monture, porté ou tenu (c'est le porteur qui
     * marque, drapeau « chargé »), cinématique, hors jeu (sélection, douane, sans personnage), pieds pas au sol.
     */
    public static boolean marque(Etat e) {
        return e.auSol() && !exclu(e);
    }

    /**
     * Les vraies exclusions, sans {@code auSol} : elles coupent la marche. Un saut ne la coupe pas : les pieds en l'air ne
     * marquent pas, mais l'atterrissage pose un pas ({@code TraceMarche}), sinon courir en sautant ne laisserait rien.
     */
    public static boolean exclu(Etat e) {
        return e.modeStaff() || e.vanish() || e.spectateur() || e.vol() || e.eau() || e.monture() || e.porte()
            || e.cinematique() || e.horsJeu();
    }

    /** Sprint d'après la vitesse réelle (blocs/s) : le trot reste une marche sur le sol. {@code enSaut} : mesurée sur un saut. */
    public static boolean sprinte(double vitesse, double seuil, boolean enSaut) {
        return vitesse >= (enSaut ? seuil * FACTEUR_SAUT : seuil);
    }

    /* ================================================================ gabarit du pas */

    public static double borneK(double k) { return Math.max(K_MIN, Math.min(K_MAX, k)); }

    /** Espacement réel (blocs) : accroupi 0,8, sprint 2, chargé 1, marche 1,2, selon la racine de la taille. */
    public static double espacement(boolean sprint, boolean accroupi, boolean charge, double k) {
        double pas = accroupi ? PAS_ACCROUPI : sprint ? PAS_SPRINT : charge ? PAS_CHARGE : PAS_MARCHE;
        return pas * Math.sqrt(borneK(k));
    }

    public static int drapeaux(boolean droit, boolean sprint, boolean accroupi, boolean charge, boolean sang) {
        return (droit ? F_DROIT : 0) | (sprint ? F_SPRINT : 0) | (accroupi ? F_ACCROUPI : 0) | (charge ? F_CHARGE : 0)
            | (sang ? F_SANG : 0);
    }

    /**
     * Le pied posé depuis le point d'axe {@code (x, z)}, cap {@code cap} (degrés, 0 = vers +z, 90 = vers +x) : écart latéral
     * selon le côté et la taille, pointe tournée vers l'extérieur, un rien de variation selon le rang du pas.
     *
     * @param sortie {x, z, angle}
     */
    public static void pied(double x, double z, double cap, boolean droit, double k, int rang, double[] sortie) {
        double a = Math.toRadians(cap);
        // droite du marcheur (monde Minecraft, x à l'est, z au sud) : vers -x quand il marche vers +z
        double rx = -Math.cos(a), rz = Math.sin(a);
        double lat = (droit ? DECAL : -DECAL) * borneK(k);
        sortie[0] = x + rx * lat;
        sortie[1] = z + rz * lat;
        double jitter = (Math.floorMod(rang * 37, 7) - 3) * 0.8;
        sortie[2] = cap + (droit ? -OUVERTURE : OUVERTURE) + jitter;
    }

    /* ================================================================ anonymat */

    /** Le jour (UTC) d'un instant : celui de la naissance du pas pour sa clé de piste, jamais le jour courant. */
    public static long jour(long millis) { return Math.floorDiv(millis, 86_400_000L); }

    /**
     * Clé de piste (0 à 65535), une par (marcheur, spectateur, jour de naissance du pas) : relie les pas d'une piste pour
     * <b>ce</b> spectateur. Un pas garde sa clé toute sa vie : le renvoyer après minuit ne relie pas deux jours. Elle ne se
     * recalcule pas sans le sel du serveur (tiré au démarrage, jamais envoyé), et diffère d'un spectateur à l'autre ; deux
     * clients qui comparent ce qu'ils reçoivent peuvent tout de même relier leurs clés (même {@code id}, même position) :
     * cela dit « c'est la même piste », jamais à qui elle est. Jamais de nom ni d'UUID sur le fil.
     */
    public static int cle(UUID marcheur, UUID spectateur, long jour, long sel) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update((marcheur + "|" + spectateur + "|" + jour + "|" + sel).getBytes(StandardCharsets.UTF_8));
            byte[] h = d.digest();
            return (h[0] & 0xFF) << 8 | (h[1] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-256 est garanti par la JVM
        }
    }
}
