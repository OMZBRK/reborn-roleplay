package com.reborn.shinobicore.mudra;

import com.reborn.shinobicore.staff.panel.StaffGrades;
import com.reborn.shinobicore.technique.Mudra;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Règles pures des mudras au clavier et des portes scellées (demande du 14/11) : minuterie de composition, lecture d'une
 * suite saisie par le staff, comparaison des suites, boîte d'une dalle, calendrier de la levée, droits par grade, refus
 * d'état. Aucune dépendance à Bukkit : testées par {@code MudraReglesTest}.
 *
 * <p>Ordinaux : 0 à 11 = ordre de l'enum {@link Mudra} (RAT … BOAR) = ordre de la barre du mod.
 */
public final class MudraRegles {

    /** Nombre de mudras de la barre. */
    public static final int NB_MUDRAS = 12;
    /** Suite d'une porte : de 2 à 12 mudras. */
    public static final int SUITE_MIN = 2, SUITE_MAX = 12;
    public static final int NOM_MAX = 32, TITRE_MAX = 48;
    /** Dalle : chaque côté de 1 à 9, épaisseur (le plus petit côté horizontal) de 3 au plus, 162 blocs au plus. */
    public static final int COTE_MAX = 9, EPAISSEUR_MAX = 3, VOLUME_MAX = 162;
    /** Tolérance de la minuterie, en faveur du joueur (latence). */
    public static final long TOLERANCE_MS = 500;
    /**
     * Cadence des signes : avance tolérée sur le plafond d'un signe par {@code signe-min-ms} (tick long, retransmission,
     * paquets arrivés groupés). Voir {@link #signeRecevable}.
     */
    public static final long GIGUE_MS = 500;
    /** Demandes {@code ouvrir} par seconde et par joueur. */
    public static final int OUVRIR_PAR_S = 2;
    /** Taille maximale d'un message reçu du mod (octets). */
    public static final int RECU_MAX = 256;
    /** Visée du staff pour poser un coin (blocs) ; {@code /porte info} sans id : la plus proche à ce rayon. */
    public static final int VISEE_STAFF = 8;
    public static final double INFO_RAYON = 16;
    /** Délai par signe d'une barre guidée (ms) : bornes de ce que l'appelant peut demander. */
    public static final long GUIDE_MIN_MS = 500, GUIDE_MAX_MS = 60_000;
    /** Outil « Portes » du Poste de garde : messages {@code porte*} par seconde et par staff. */
    public static final int OUTIL_PAR_S = 10;
    /** Outil « Portes » : rayon des portes « proches » (défaut, bornes) et nombre de portes d'une liste. */
    public static final int OUTIL_RAYON = 64, OUTIL_RAYON_MIN = 4, OUTIL_RAYON_MAX = 256, OUTIL_LISTE_MAX = 200;

    private MudraRegles() {}

    /* ================================================================ réglages */

    /**
     * Réglages lus dans {@code mudras} (config.yml de ShinobiCore) ; les défauts vivent ici, une section absente du
     * {@code config.yml} du serveur les prend.
     *
     * @param tempsMs      composition libre : durée de la ligne de temps
     * @param gainMs       temps rendu par signe accepté (sans dépasser {@code tempsMs})
     * @param maxSignes    signes au plus dans une composition libre
     * @param signeMinMs   écart minimal entre deux signes (plafond moyen, {@link #GIGUE_MS} de marge : {@link #signeRecevable})
     * @param rechargeMs   délai avant de rouvrir la barre après une fin
     * @param gesteRayon   blocs : les proches voient et entendent les signes (0 = jamais)
     * @param guideMargeMs apprentissage : temps ajouté au délai par signe de la difficulté
     * @param portee       blocs entre les pieds du joueur et la dalle
     * @param dureeTicks   durée de la levée (et de la descente)
     * @param ouverteS     secondes avant que la dalle redescende, dès que le passage est libre
     * @param essaisMax    essais manqués devant une même porte, dans la fenêtre, avant qu'elle ne réponde plus à ce
     *                     joueur (0 = jamais de blocage)
     * @param essaisFenetreS fenêtre de ce compte, en secondes
     * @param blocageS     durée du blocage, en secondes
     */
    public record Reglages(long tempsMs, long gainMs, int maxSignes, long signeMinMs, long rechargeMs, double gesteRayon,
                           long guideMargeMs, double portee, int dureeTicks, int ouverteS, int essaisMax,
                           int essaisFenetreS, int blocageS) {

        public static final Reglages DEFAUT = new Reglages(5000, 700, 12, 120, 1500, 24.0, 1500, 6.0, 60, 8, 5, 60, 120);

        public Reglages {
            tempsMs = Math.max(1000, Math.min(60_000, tempsMs));
            gainMs = Math.max(0, Math.min(tempsMs, gainMs));
            maxSignes = Math.max(SUITE_MIN, Math.min(SUITE_MAX, maxSignes));
            signeMinMs = Math.max(0, Math.min(2000, signeMinMs));
            rechargeMs = Math.max(0, Math.min(60_000, rechargeMs));
            gesteRayon = Double.isNaN(gesteRayon) ? 0.0 : Math.max(0.0, Math.min(64.0, gesteRayon));
            guideMargeMs = Math.max(0, Math.min(10_000, guideMargeMs));
            portee = Double.isNaN(portee) ? 6.0 : Math.max(1.0, Math.min(16.0, portee));
            dureeTicks = Math.max(10, Math.min(400, dureeTicks));
            ouverteS = Math.max(1, Math.min(600, ouverteS));
            essaisMax = Math.max(0, Math.min(50, essaisMax));
            essaisFenetreS = Math.max(5, Math.min(3600, essaisFenetreS));
            blocageS = Math.max(5, Math.min(86_400, blocageS));
        }
    }

    /* ================================================================ minuterie */

    /** Échéance d'une session qui part maintenant. */
    public static long echeance(long now, long tempsMs) { return now + tempsMs; }

    /** Temps qui reste sur la ligne (jamais négatif). */
    public static long reste(long echeance, long now) { return Math.max(0, echeance - now); }

    /** La minuterie est écoulée : l'échéance est passée de plus de {@link #TOLERANCE_MS} (en faveur du joueur). */
    public static boolean echue(long echeance, long now) { return now - echeance > TOLERANCE_MS; }

    /**
     * Nouvelle échéance après un signe accepté : le temps qui reste gagne {@code gainMs}, sans dépasser {@code tempsMs}.
     * En mode guide {@code gainMs} = {@code tempsMs} : la ligne repart pleine à chaque bon signe.
     */
    public static long apresSigne(long echeance, long now, long tempsMs, long gainMs) {
        return now + Math.min(tempsMs, reste(echeance, now) + Math.max(0, gainMs));
    }

    /**
     * Un signe reçu est-il recevable : ordinal de 0 à 11, et pas en avance de plus de {@link #GIGUE_MS} sur la cadence
     * ({@code auPlusTot} = {@link #signeSuivant} du signe précédent ; 0 avant le premier signe).
     *
     * <p>La cadence est un <b>plafond moyen</b> (un signe par {@code signe-min-ms}), pas un écart mesuré entre deux
     * arrivées : le serveur ne voit pas l'instant de l'appui, seulement celui où le paquet lui parvient, et un tick long,
     * une retransmission ou un simple battement de 50 ms tassent deux signes posés à bonne distance. Un écart lu trop
     * court jetait donc des signes justes, en silence (le signe suivant était alors jugé au mauvais rang). Ici un signe
     * n'est jeté que s'il dépasse le plafond de plus de {@link #GIGUE_MS} : un mod qui respecte l'écart ne perd jamais de
     * signe, une rafale reste bornée.
     */
    public static boolean signeRecevable(int s, long auPlusTot, long recu) {
        if (s < 0 || s >= NB_MUDRAS) return false;
        return recu >= auPlusTot - GIGUE_MS;
    }

    /** Après un signe accepté : l'instant où le suivant est attendu au plus tôt (à passer à {@link #signeRecevable}). */
    public static long signeSuivant(long auPlusTot, long recu, long signeMinMs) {
        return Math.max(auPlusTot, recu) + Math.max(0, signeMinMs);
    }

    /** Le délai de recharge après une fin est-il passé ({@code finLe} = 0 : jamais de fin) ? */
    public static boolean rechargeFinie(long finLe, long now, long rechargeMs) {
        return finLe <= 0 || now - finLe >= rechargeMs;
    }

    /** Délai par signe d'une barre guidée, borné. */
    public static long bornerGuide(long tempsParSigneMs) {
        return Math.max(GUIDE_MIN_MS, Math.min(GUIDE_MAX_MS, tempsParSigneMs));
    }

    /* ================================================================ suites */

    /** Ordinaux (0 à 11) d'une suite de mudras. */
    public static List<Integer> ordinaux(List<Mudra> suite) {
        List<Integer> l = new ArrayList<>(suite.size());
        for (Mudra m : suite) l.add(m.ordinal());
        return l;
    }

    /** La suite composée est-elle <b>exactement</b> la suite attendue (même longueur, mêmes signes, même ordre) ? */
    public static boolean memeSuite(List<Integer> composee, List<Integer> attendue) {
        return composee != null && attendue != null && !attendue.isEmpty() && composee.equals(attendue);
    }

    /** Ce que vaut un signe en mode guide. */
    public enum Pas { BON, DERNIER, MAUVAIS }

    /** Le signe {@code s} posé à la place {@code index} d'une suite guidée : bon, bon et dernier, ou mauvais. */
    public static Pas pasGuide(List<Integer> attendue, int index, int s) {
        if (attendue == null || index < 0 || index >= attendue.size() || attendue.get(index) != s) return Pas.MAUVAIS;
        return index == attendue.size() - 1 ? Pas.DERNIER : Pas.BON;
    }

    /** La composition libre est-elle pleine ({@code max-signes} signes sans rien) ? */
    public static boolean pleine(int composes, int maxSignes) { return composes >= maxSignes; }

    /**
     * Essais en série devant une porte : note l'essai manqué de {@code now} dans {@code instants} (ceux sortis de la
     * fenêtre sont retirés) et dit si le plafond est atteint. Vrai : la liste est vidée, à l'appelant de bloquer la
     * porte pour ce joueur. {@code max} à 0 ou moins : jamais de blocage, rien n'est noté.
     */
    public static boolean essaiDeTrop(Deque<Long> instants, long now, int max, long fenetreMs) {
        if (max <= 0) return false;
        while (!instants.isEmpty() && now - instants.peekFirst() > fenetreMs) instants.pollFirst();
        instants.addLast(now);
        if (instants.size() < max) return false;
        instants.clear();
        return true;
    }

    /**
     * Essais manqués d'un joueur devant une porte, et la fin de son blocage. L'horloge est passée par l'appelant.
     */
    public static final class Essais {
        private final ArrayDeque<Long> instants = new ArrayDeque<>();
        private long bloqueJusqua;

        /** La porte ne répond-elle plus à ce joueur ? */
        public boolean bloquee(long now) { return now < bloqueJusqua; }

        /**
         * Note un essai manqué ; vrai s'il déclenche le blocage ({@code blocageMs}). Pendant un blocage rien n'est
         * compté : il ne se prolonge pas.
         */
        public boolean compter(long now, int max, long fenetreMs, long blocageMs) {
            if (max <= 0 || bloquee(now)) return false;
            if (!essaiDeTrop(instants, now, max, fenetreMs)) return false;
            bloqueJusqua = now + Math.max(0, blocageMs);
            return true;
        }

        /** Plus rien à retenir : blocage fini et aucun essai encore dans la fenêtre. */
        public boolean eteint(long now, long fenetreMs) {
            return !bloquee(now) && (instants.isEmpty() || now - instants.peekLast() > fenetreMs);
        }

        /** Millisecondes avant la fin du blocage ; 0 hors blocage. */
        public long resteMs(long now) { return Math.max(0, bloqueJusqua - now); }
    }

    /** Un blocage en cours devant une porte : le joueur et ce qui reste (ms). */
    public record Blocage<K>(K joueur, long resteMs) { }

    /**
     * Les blocages en cours, rangés par porte, lus dans la table des essais (joueur → identifiant de porte → essais).
     * Dans chaque porte : le blocage le plus long d'abord, puis l'ordre des joueurs (un ordre qui ne bouge pas d'une
     * lecture à l'autre). Les comptes éteints sont retirés de la table au passage, les joueurs vidés aussi.
     */
    public static <K extends Comparable<K>> Map<String, List<Blocage<K>>> blocages(Map<K, Map<String, Essais>> table, long now,
                                                                                long fenetreMs) {
        Map<String, List<Blocage<K>>> out = new LinkedHashMap<>();
        for (Iterator<Map.Entry<K, Map<String, Essais>>> it = table.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<K, Map<String, Essais>> j = it.next();
            j.getValue().values().removeIf(e -> e.eteint(now, fenetreMs));
            if (j.getValue().isEmpty()) { it.remove(); continue; }
            for (Map.Entry<String, Essais> e : j.getValue().entrySet()) {
                if (!e.getValue().bloquee(now)) continue;
                out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Blocage<>(j.getKey(), e.getValue().resteMs(now)));
            }
        }
        Comparator<Blocage<K>> ordre = Comparator.<Blocage<K>>comparingLong(Blocage::resteMs).reversed()
                .thenComparing(Blocage::joueur);
        for (List<Blocage<K>> l : out.values()) l.sort(ordre);
        return out;
    }

    /**
     * Le staff lève le blocage d'un joueur devant une porte : son compte d'essais devant elle est oublié (il repart de
     * zéro). Rend les millisecondes qui restaient ; 0 s'il n'était pas bloqué (rien n'est alors touché).
     */
    public static <K> long leverBlocage(Map<K, Map<String, Essais>> table, K joueur, String porte, long now) {
        Map<String, Essais> m = joueur == null ? null : table.get(joueur);
        Essais e = m == null ? null : m.get(porte);
        if (e == null || !e.bloquee(now)) return 0;
        long reste = e.resteMs(now);
        m.remove(porte);
        if (m.isEmpty()) table.remove(joueur);
        return reste;
    }

    /**
     * La composition commence-t-elle par cette suite ? Sert à reconnaître la <b>bonne</b> suite d'une porte posée sans
     * qu'elle s'ouvre (blocage, hors de portée, dalle en mouvement) : elle ne s'écrit pas en clair au journal et ne
     * compte pas comme un essai manqué.
     */
    public static boolean commencePar(List<Integer> composee, List<Integer> suite) {
        if (composee == null || suite == null || suite.isEmpty() || composee.size() < suite.size()) return false;
        return composee.subList(0, suite.size()).equals(suite);
    }

    /** Suite lue d'une saisie du staff, ou l'erreur à lui montrer. */
    public record Suite(List<Mudra> signes, String erreur) {
        public boolean ok() { return erreur == null; }
    }

    /**
     * Un signe saisi par le staff : nom français ou anglais ({@link Mudra#from}), ou un nombre de 1 à 12 = sa place sur
     * la barre. null si inconnu.
     */
    public static Mudra lireSigne(String mot) {
        if (mot == null) return null;
        String t = mot.strip();
        if (t.isEmpty()) return null;
        if (t.matches("\\d{1,2}")) {
            int n = Integer.parseInt(t);
            return n >= 1 && n <= NB_MUDRAS ? Mudra.values()[n - 1] : null;
        }
        return Mudra.from(t);
    }

    /**
     * Lit la suite d'une porte ({@code /porte creer}, {@code /porte signes}) : mots séparés par des espaces ou des
     * virgules, de {@link #SUITE_MIN} à {@link #SUITE_MAX} signes. Un même signe peut se répéter.
     */
    public static Suite lireSuite(List<String> mots) {
        List<Mudra> out = new ArrayList<>();
        if (mots != null) {
            for (String brut : mots) {
                if (brut == null) continue;
                for (String m : brut.split("[,;]")) {
                    if (m.isBlank()) continue;
                    Mudra x = lireSigne(m);
                    if (x == null) return new Suite(List.of(), "Signe inconnu : « " + m.strip()
                            + " » (nom du mudra, ou sa place sur la barre de 1 à " + NB_MUDRAS + ").");
                    out.add(x);
                }
            }
        }
        String e = validerSuite(out.size());
        return e != null ? new Suite(List.of(), e) : new Suite(List.copyOf(out), null);
    }

    /**
     * Suite reçue de l'outil du Poste de garde : ordinaux de 0 à 11 (les cases de la grille), de {@link #SUITE_MIN} à
     * {@link #SUITE_MAX} signes. Un même signe peut se répéter.
     */
    public static Suite suiteDepuisOrdinaux(List<Integer> ordinaux) {
        int n = ordinaux == null ? 0 : ordinaux.size();
        String e = validerSuite(n);
        if (e != null) return new Suite(List.of(), e);
        List<Mudra> out = new ArrayList<>(n);
        for (Integer o : ordinaux) {
            if (o == null || o < 0 || o >= NB_MUDRAS) return new Suite(List.of(), "Signe inconnu.");
            out.add(Mudra.values()[o]);
        }
        return new Suite(List.copyOf(out), null);
    }

    /** Longueur d'une suite de porte : null si acceptée, sinon l'erreur. */
    public static String validerSuite(int n) {
        if (n < SUITE_MIN) return "Il faut au moins " + SUITE_MIN + " signes.";
        if (n > SUITE_MAX) return "Pas plus de " + SUITE_MAX + " signes.";
        return null;
    }

    /** Ce qui remplace la suite d'une porte dans une ligne de commande journalisée. */
    public static final String SUITE_MASQUEE = "[suite masquée]";

    /**
     * Ligne de commande telle qu'elle peut être journalisée : la suite d'une porte voyage dans les arguments de
     * {@code /porte creer <nom> <signes…>} et {@code /porte signes <id> <signes…>}, et le journal {@code STAFF} est lu
     * des Helpers, qui n'ont pas le droit de la voir. Tout ce qui suit le nom ou l'identifiant est remplacé par
     * {@link #SUITE_MASQUEE} (alias, espace de noms {@code plugin:porte} et casse compris) ; un identifiant oublié, lu
     * comme un signe, est masqué lui aussi. Toute autre ligne est rendue telle quelle.
     */
    public static String masquerCommande(String ligne) {
        if (ligne == null) return null;
        String[] m = ligne.strip().split("\\s+");
        if (m.length < 3) return ligne;
        String racine = (m[0].startsWith("/") ? m[0].substring(1) : m[0]).toLowerCase(Locale.ROOT);
        int ns = racine.indexOf(':');
        if (ns >= 0) racine = racine.substring(ns + 1);
        if (!racine.equals("porte") && !racine.equals("portescellee")) return ligne;
        String op = m[1].toLowerCase(Locale.ROOT);
        boolean creer = op.equals("creer") || op.equals("créer");
        if (!creer && !op.equals("signes")) return ligne;
        // « /porte signes tigre serpent » (identifiant oublié) : le premier mot est déjà un signe
        if (!creer && contientSigne(m[2])) return m[0] + " " + m[1] + " " + SUITE_MASQUEE;
        if (m.length < 4) return ligne;
        return m[0] + " " + m[1] + " " + m[2] + " " + SUITE_MASQUEE;
    }

    /** Le mot (ou l'un de ses morceaux séparés par une virgule) se lit-il comme un signe ? */
    private static boolean contientSigne(String mot) {
        for (String x : mot.split("[,;]")) if (lireSigne(x) != null) return true;
        return false;
    }

    /** Suite en clair pour le staff et le journal : « Tigre · Serpent · Chien ». */
    public static String libelle(List<Mudra> suite) {
        StringBuilder b = new StringBuilder();
        for (Mudra m : suite) {
            if (b.length() > 0) b.append(" · ");
            b.append(m.display());
        }
        return b.toString();
    }

    /** Suite composée (ordinaux) en clair ; un ordinal hors bornes s'écrit « ? ». */
    public static String libelleOrdinaux(List<Integer> suite) {
        StringBuilder b = new StringBuilder();
        for (int s : suite) {
            if (b.length() > 0) b.append(" · ");
            b.append(s >= 0 && s < NB_MUDRAS ? Mudra.values()[s].display() : "?");
        }
        return b.toString();
    }

    /* ================================================================ nom, identifiant */

    /** Nom d'une porte : espaces autour retirés, {@code _} = espace (un seul mot à la commande), 32 caractères au plus. */
    public static String nom(String s) {
        String t = s == null ? "" : s.replace('_', ' ').strip().replaceAll("\\s+", " ");
        return t.length() > NOM_MAX ? t.substring(0, NOM_MAX).strip() : t;
    }

    /** Titre d'une barre guidée : 48 caractères au plus. */
    public static String titre(String s) {
        String t = s == null ? "" : s.strip();
        return t.length() > TITRE_MAX ? t.substring(0, TITRE_MAX) : t;
    }

    /** Identifiant court : « D » + compteur en base 36 (D1, D2… DZZ). */
    public static String id(int n) { return "D" + Integer.toString(Math.max(1, n), 36).toUpperCase(Locale.ROOT); }

    /* ================================================================ boîte de la dalle */

    /**
     * Boîte de la dalle : coin bas ({@code x, y, z}) et taille ({@code dx, dy, dz}) en blocs.
     */
    public record Boite(int x, int y, int z, int dx, int dy, int dz) {

        /** La boîte tendue entre deux blocs (les deux coins posés par le staff, dans n'importe quel ordre). */
        public static Boite entre(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Boite(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.abs(x1 - x2) + 1,
                    Math.abs(y1 - y2) + 1, Math.abs(z1 - z2) + 1);
        }

        public int volume() { return dx * dy * dz; }

        /** Épaisseur : le plus petit côté horizontal. */
        public int epaisseur() { return Math.min(dx, dz); }

        public boolean contient(int bx, int by, int bz) {
            return bx >= x && bx < x + dx && by >= y && by < y + dy && bz >= z && bz < z + dz;
        }

        public boolean recoupe(Boite o) {
            return x < o.x + o.dx && o.x < x + dx && y < o.y + o.dy && o.y < y + dy && z < o.z + o.dz && o.z < z + dz;
        }

        /** Distance d'un point (les pieds du joueur) à la boîte ; 0 dedans. */
        public double distance(double px, double py, double pz) {
            double ex = Math.max(Math.max(x - px, 0), px - (x + dx));
            double ey = Math.max(Math.max(y - py, 0), py - (y + dy));
            double ez = Math.max(Math.max(z - pz, 0), pz - (z + dz));
            return Math.sqrt(ex * ex + ey * ey + ez * ez);
        }

        public double[] centre() { return new double[]{x + dx / 2.0, y + dy / 2.0, z + dz / 2.0}; }

        /**
         * Rang d'un bloc dans la liste {@code blocs} : <b>ordre fixe</b>, rangée par rangée du bas vers le haut, puis x,
         * puis z. -1 hors de la boîte.
         */
        public int indice(int bx, int by, int bz) {
            if (!contient(bx, by, bz)) return -1;
            return ((by - y) * dx + (bx - x)) * dz + (bz - z);
        }

        /** Le bloc de rang {@code i} ({@link #indice}) : {@code {x, y, z}}. */
        public int[] position(int i) {
            int rz = i % dz, rx = (i / dz) % dx, ry = i / (dz * dx);
            return new int[]{x + rx, y + ry, z + rz};
        }

        public String libelle(String monde) {
            return monde + " " + x + " " + y + " " + z + " (" + dx + "×" + dy + "×" + dz + ")";
        }
    }

    /** Taille d'une dalle : null si acceptée, sinon l'erreur à montrer au staff. */
    public static String validerBoite(int dx, int dy, int dz) {
        if (dx < 1 || dy < 1 || dz < 1) return "Boîte vide.";
        if (dx > COTE_MAX || dy > COTE_MAX || dz > COTE_MAX)
            return "Dalle trop grande : " + COTE_MAX + " blocs par côté au plus (" + dx + "×" + dy + "×" + dz + ").";
        if (Math.min(dx, dz) > EPAISSEUR_MAX)
            return "Dalle trop épaisse : " + EPAISSEUR_MAX + " blocs d'épaisseur au plus (" + Math.min(dx, dz) + ").";
        if (dx * dy * dz > VOLUME_MAX) return "Dalle trop grosse : " + VOLUME_MAX + " blocs au plus (" + dx * dy * dz + ").";
        return null;
    }

    /**
     * Contenu d'une dalle : null si accepté. Refusé si elle n'est que de l'air, si un bloc porte un état propre (coffre,
     * panneau, bannière… : son contenu serait perdu à la levée), ou si une entité est accrochée à l'un de ses blocs
     * (cadre, tableau, nœud de laisse : elle se décrocherait et tomberait en objet dès que la dalle a quitté sa place).
     */
    public static String validerContenu(int nonVides, int etatsPropres, int accrochees) {
        if (nonVides <= 0) return "Il n'y a que de l'air entre les deux coins.";
        if (etatsPropres > 0)
            return "La dalle contient " + etatsPropres + " bloc(s) à contenu (coffre, panneau, bannière…) : retire-les.";
        if (accrochees > 0)
            return "La dalle porte " + accrochees + " cadre(s), tableau(x) ou nœud(s) de laisse : retire-les (ils tomberaient"
                    + " à la levée).";
        return null;
    }

    /**
     * La dalle est-elle hors d'atteinte du staff qui la choisit à la souris : à plus de {@link #VISEE_STAFF} + 1 blocs de
     * ses yeux (la règle de l'ancre d'une plaque) ? Un client modifié ne pose pas une porte au bout du monde.
     */
    public static boolean tropLoin(Boite b, double ox, double oy, double oz) {
        return b.distance(ox, oy, oz) > VISEE_STAFF + 1;
    }

    /**
     * Levée demandée à la création par l'outil : 0 = toute la hauteur de la dalle, sinon de 1 à {@code dy}. -1 hors
     * bornes (refusée, comme {@code /porte leve}).
     */
    public static int leveDemandee(int leve, int dy) {
        if (leve == 0) return Math.max(1, dy);
        return leve < 0 || leve > dy ? -1 : leve;
    }

    /** Blocs de levée : de 1 à la hauteur de la dalle ; 0 ou moins = toute la hauteur. */
    public static int bornerLeve(int leve, int dy) {
        if (leve <= 0) return Math.max(1, dy);
        return Math.max(1, Math.min(Math.max(1, dy), leve));
    }

    /* ================================================================ calendrier de la levée */

    /**
     * Levée : tick (compté du départ du mouvement) où la rangée {@code r} de barrières (comptée du bas, à partir de 0) se
     * libère, quand le bas de la dalle l'a dépassée : {@code ceil((r + 1) × duree / leve)}. -1 : la rangée ne se libère
     * pas (au-dessus de {@code leve}, la dalle levée l'occupe encore).
     */
    public static int tickLiberation(int r, int duree, int leve) {
        if (r < 0 || leve <= 0 || r >= leve || duree <= 0) return -1;
        return (int) (((long) (r + 1) * duree + leve - 1) / leve);
    }

    /**
     * Descente (mouvement inverse) : tick où la rangée {@code r} reprend ses barrières, de haut en bas, juste avant que le
     * bas de la dalle n'y entre : {@code duree - tickLiberation(r)}. -1 : rangée restée en barrière.
     */
    public static int tickPose(int r, int duree, int leve) {
        int t = tickLiberation(r, duree, leve);
        return t < 0 ? -1 : duree - t;
    }

    /* ================================================================ état d'une porte */

    /** État d'une porte : le code envoyé à l'outil du Poste de garde et le texte en clair des réponses de {@code /porte}. */
    public enum Etat {
        FERMEE("fermee", "fermée"),
        LEVE("leve", "se lève"),
        LEVEE("levee", "levée"),
        REDESCEND("redescend", "redescend"),
        REMETTRE("remettre", "dalle à remettre (monde absent)");

        private final String code, texte;

        Etat(String code, String texte) {
            this.code = code;
            this.texte = texte;
        }

        public String code() { return code; }

        public String texte() { return texte; }
    }

    /**
     * L'état d'une porte. {@code mouvement} : null = immobile, vrai = la dalle monte, faux = elle descend ; le mouvement
     * passe avant la dalle à remettre, qui passe avant {@code ouverte}.
     */
    public static Etat etat(Boolean mouvement, boolean aRemettre, boolean ouverte) {
        if (mouvement != null) return mouvement ? Etat.LEVE : Etat.REDESCEND;
        if (aRemettre) return Etat.REMETTRE;
        return ouverte ? Etat.LEVEE : Etat.FERMEE;
    }

    /** Secondes (arrondies au-dessus) avant une échéance ; -1 si elle est inconnue ({@code echeance} à 0 ou moins). */
    public static int resteS(long echeance, long now) {
        if (echeance <= 0) return -1;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, (echeance - now + 999) / 1000));
    }

    /* ================================================================ outil du Poste de garde */

    /** Rayon des portes « proches » demandé par l'outil, borné. */
    public static int bornerRayon(int r) { return Math.max(OUTIL_RAYON_MIN, Math.min(OUTIL_RAYON_MAX, r)); }

    private static final Pattern RENVOI_PARENTHESE = Pattern.compile("\\s*\\(/porte [^)]*\\)");
    private static final Pattern RENVOI_FINAL = Pattern.compile("\\s*:\\s*/porte [^.]*\\.$");

    /**
     * Une erreur du service redite dans l'outil du Poste de garde : les renvois à une commande ({@code (/porte systeme
     * on)}, {@code : /porte fermer D3.}) n'ont pas de sens dans un volet à la souris, ils sont retirés. Le reste de la
     * phrase est gardé tel quel.
     */
    public static String pourOutil(String message) {
        if (message == null) return null;
        return RENVOI_FINAL.matcher(RENVOI_PARENTHESE.matcher(message).replaceAll("")).replaceAll(".");
    }

    /* ================================================================ droits */

    /** Voir la liste des portes et s'y téléporter : Helper et plus. */
    public static boolean peutLister(int grade) { return grade >= StaffGrades.HELPER; }

    /** Voir la suite, créer, modifier, retirer, ouvrir, fermer : Modérateur et plus. */
    public static boolean peutGerer(int grade) { return grade >= StaffGrades.MODO; }

    /** Allumer ou éteindre le système : Admin et plus. */
    public static boolean peutSysteme(int grade) { return grade >= StaffGrades.ADMIN; }

    /* ================================================================ droit d'ouvrir la barre */

    /**
     * Ce que le serveur sait du joueur quand il relit le droit de composer (ouverture, toutes les 10 ticks, changement
     * d'état).
     *
     * @param eteint    le système est éteint ({@code actif: false} dans mudras.yml)
     * @param etatRefus raison de la matrice d'états pour {@code TECHNIQUE} <b>avec mudras</b> (entravé compris), null si
     *                  permise
     */
    public record Contexte(boolean eteint, boolean mortRp, boolean aTerre, boolean inconscient, boolean spectateur,
                           boolean cinematique, boolean douane, boolean horsJeu, String etatRefus) {
    }

    /**
     * Pourquoi la barre est refusée (ou coupée), null si elle est permise. Les cas durs (mort, KO, cinématique, douane,
     * hors jeu) sont relus chez leurs détenteurs avant la matrice : un {@code etats.yml} du disque plus ancien gagne sur
     * celui du jar. Phrases courtes au tutoiement, comme la matrice.
     */
    public static String refus(Contexte c) {
        if (c.eteint()) return "Indisponible.";
        if (c.mortRp()) return "Ton personnage est mort.";
        if (c.aTerre()) return "Tu es à terre.";
        if (c.inconscient()) return "Tu es inconscient.";
        if (c.spectateur()) return "Impossible en spectateur.";
        if (c.cinematique()) return "Cinématique en cours.";
        if (c.douane()) return "Douane : aucune mécanique RP.";
        if (c.horsJeu()) return "Hors jeu.";
        if (c.etatRefus() != null && !c.etatRefus().isBlank()) return c.etatRefus();
        return null;
    }
}
