package com.reborn.shinobicore.staff.outils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Narration</b> (lot 2 « Reprises Ketto », décision du 01/11 « oui pour /narration, en plus des plaques ») : un texte
 * RP au centre de l'écran des joueurs pendant un event, mots colorés, sans fond. Canal {@value #CANAL} (S → C, JSON
 * UTF-8 brut) ; protocole : PLUGINS_REFERENCE §4 bis.3 bis. Le mod met en page, tient la file d'attente et le
 * minuteur ; le serveur choisit les destinataires, journalise, se replie sur le tchat pour un client qui n'écoute pas
 * le canal, et rattrape le joueur qui se connecte pendant une narration en cours.
 *
 * <p>Émetteurs : {@code /narration} et l'outil du Poste de garde (droits : {@link #refus} ; garde-fou sous Modérateur :
 * {@link #refusEnvoi}), l'API {@link #envoyer} pour les autres systèmes (cinématiques, douane, quêtes). Thread principal
 * seulement. L'auteur n'est <b>jamais</b> envoyé aux joueurs (il est au journal, {@code STAFF NARRATION}).
 */
public final class Narrations implements Listener {

    public static final String CANAL = "reborn:narration";
    /** Version du protocole (champ {@code v}) : ne change que pour une rupture. */
    public static final int VERSION = 1;
    public static final int MAX_CARACTERES = 1000;
    public static final int RAYON_DEFAUT = 30, RAYON_MAX = 64;
    /** Durées ({@code s}) : 0 = automatique, -1 = tenue jusqu'à la fin, sinon de 2 à 180 secondes. */
    public static final int AUTO = 0, TENIR = -1, DUREE_MIN = 2, DUREE_MAX = 180;
    /** Clés de couleur du protocole ; {@code ""} = blanc. */
    public static final List<String> COULEURS = List.of("", "rouge", "or", "ombre", "gris");

    /** Pause entre deux narrations de la file, comme le mod (ms). */
    static final long PAUSE_MS = 400L;
    /** Fondu de sortie de la narration chez le mod (ms) : une narration arrêtée reste ce temps-là à l'écran. */
    static final long SORTIE_MS = 700L;
    /** Fondu de page de la narration chez le mod (ms, de chaque côté) : 1,2 s de plus par page au-delà de la première. */
    static final long FONDU_PAGE_MS = 600L;
    /** Narrations en attente chez le mod au plus : une 9e fait sortir la plus ancienne en attente. */
    static final int FILE_MAX = 8;
    /** Estimation des pages : caractères d'une ligne pleine (Philosopher Bold, 1080p) et lignes par page, comme le mod. */
    static final int LIGNE_CARACTERES = 70, LIGNES_PAGE = 3;
    /** Une narration tenue s'efface d'elle-même au bout de 10 min chez le mod. */
    static final long TENIR_MAX_MS = 600_000L;
    /** Rattrapage à la connexion seulement s'il reste au moins 5 s. */
    static final long RATTRAPAGE_MIN_MS = 5_000L;
    /** Oubliée 40 min après l'envoi : file de 8 × 180 s plus une tenue de 10 min, au pire. */
    static final long OUBLI_MS = 40 * 60_000L;
    /**
     * Au-delà de {@code MEMOIRE_MAX}, on oublie les plus anciennes déjà finies, sans dépasser la première qui passe encore
     * (la file des suivantes en dépend) ; au-delà de {@code MEMOIRE_DURE} (garde-fou mémoire), les plus anciennes quoi
     * qu'il arrive.
     */
    static final int MEMOIRE_MAX = 64, MEMOIRE_DURE = 256;
    /** Une narration finie depuis moins de 30 s selon l'estimation du serveur peut encore être arrêtée par le staff. */
    static final long MARGE_ARRET_MS = 30_000L;
    /** « tous » tapé en jeu : la confirmation attend 60 s. */
    static final long CONFIRMATION_MS = 60_000L;
    /** Garde-fou sous Modérateur (Helper, animateur) : 3 s entre deux narrations, 5 joueurs différents par minute. */
    static final long DELAI_MIN_MS = 3_000L;
    static final int CIBLES_PAR_MINUTE = 5;
    private static final long MINUTE_MS = 60_000L;
    /** {@link #lireDuree} : une durée, mais hors de 2-180 s. */
    static final int DUREE_INVALIDE = Integer.MIN_VALUE;
    /** Texte brut reçu (balises comprises) au-delà duquel on refuse sans l'analyser. */
    static final int MAX_BRUT = 4 * MAX_CARACTERES;
    /** Texte de l'entrée {@code NARRATION} ; au-delà, le texte complet suit en {@code NARRATION_TEXTE}. */
    private static final int TEXTE_JOURNAL = 300;
    /** Morceaux de {@code NARRATION_TEXTE} : le journal coupe un détail à {@value #DETAIL_JOURNAL} unités UTF-16. */
    static final int MORCEAU_JOURNAL = 550, DETAIL_JOURNAL = 600;
    static final String AIDE_COULEURS = "<rouge>…</>, <or>, <ombre>, <gris> (ou &c, &6, &9, &7 ; &r = blanc)";

    /** Jeton qui ressemble à une balise : {@code <nom>}, {@code </nom>}, {@code </>}, {@code <#ff0000>}… */
    private static final Pattern BALISE = Pattern.compile("/|/?[A-Za-z#][^<>\\s]{0,30}");
    /** Une balise fait 32 caractères au plus entre les chevrons : le {@code >} n'est pas cherché plus loin. */
    private static final int BALISE_MAX = 33;
    private static final Pattern DUREE = Pattern.compile("(\\d{1,4})(s|sec|m|min)");
    private static final String CODES = "0123456789abcdefklmnorx";

    /* ================================================================ modèle */

    /** Un segment du texte : texte (retours à la ligne {@code \n} compris) et clé de couleur ({@link #COULEURS}). */
    public record Segment(String texte, String couleur) {
        public Segment {
            texte = texte == null ? "" : texte;
            couleur = couleur != null && COULEURS.contains(couleur) ? couleur : "";
        }

        public static Segment blanc(String texte) { return new Segment(texte, ""); }
    }

    /** Texte analysé : les segments, ou l'erreur à montrer au staff (rien n'est envoyé). */
    public record Analyse(List<Segment> segments, String erreur) {
        public boolean ok() { return erreur == null; }

        static Analyse erreur(String m) { return new Analyse(List.of(), m); }
    }

    /**
     * {@code file} : après celle à l'écran ; {@code remplace} : coupe l'écran et vide la file (Modérateur, console, API ;
     * sous Modérateur, le serveur arrête seulement les narrations de l'émetteur encore en cours chez l'un des
     * destinataires, puis envoie la nouvelle en {@code file}).
     */
    public enum Mode {
        FILE, REMPLACE;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        /** {@code "remplace"} → REMPLACE ; tout le reste → FILE. */
        public static Mode lire(String s) { return s != null && s.trim().equalsIgnoreCase("remplace") ? REMPLACE : FILE; }
    }

    public enum Type {
        TOUS, MONDE, ICI, JOUEUR, LISTE;

        /** Portée tapée ou reçue du Poste de garde : tous, monde, ici, joueur ; null sinon ({@code LISTE} = API seule). */
        public static Type lire(String s) {
            if (s == null) return null;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "tous", "all", "global", "partout" -> TOUS;
                case "monde", "world" -> MONDE;
                case "ici", "zone" -> ICI;
                case "joueur", "perso", "player" -> JOUEUR;
                default -> null;
            };
        }
    }

    /**
     * Portée d'une narration : qui la reçoit à l'envoi ({@link #couvre}), et qui la rattrape en se connectant pendant
     * qu'elle passe. {@code libelle} : pour le staff et le journal seulement.
     */
    public record Portee(Type type, UUID monde, Location centre, int rayon, UUID joueur, String libelle) {

        public static Portee tous() { return new Portee(Type.TOUS, null, null, 0, null, "tout le serveur"); }

        public static Portee monde(World w) { return new Portee(Type.MONDE, w.getUID(), null, 0, null, "monde " + w.getName()); }

        public static Portee ici(Location c, int rayon) {
            return new Portee(Type.ICI, c.getWorld().getUID(), c.clone(), rayon, null, "ici · " + rayon + " blocs");
        }

        public static Portee joueur(Player p) {
            return new Portee(Type.JOUEUR, null, null, 0, p.getUniqueId(), "joueur " + p.getName());
        }

        /** API : liste de joueurs fournie ; personne de plus ne la rattrape. */
        public static Portee liste(String origine) { return new Portee(Type.LISTE, null, null, 0, null, origine); }

        /** Joueur dans la portée (envoi, rattrapage). Distance 3D à partir du centre, comme {@code /seisme}. */
        public boolean couvre(Player p) {
            return switch (type) {
                case TOUS -> true;
                case MONDE -> p.getWorld().getUID().equals(monde);
                case ICI -> {
                    Location l = p.getLocation();
                    if (l.getWorld() == null || !l.getWorld().getUID().equals(monde)) yield false;
                    double dx = l.getX() - centre.getX(), dy = l.getY() - centre.getY(), dz = l.getZ() - centre.getZ();
                    yield dx * dx + dy * dy + dz * dz <= (double) rayon * rayon;
                }
                case JOUEUR -> p.getUniqueId().equals(joueur);
                case LISTE -> false;
            };
        }
    }

    /**
     * Résultat d'un envoi : identifiant, destinataires, dont sans le mod (repli tchat), durée en clair. {@code attente} :
     * secondes avant l'affichage chez le destinataire le plus en retard (0 = tout de suite ; -1 = derrière une narration
     * tenue, qui passera à son arrêt) ; {@code derriere} : la narration qu'elle attend ; {@code remplacees} : narrations
     * de l'émetteur arrêtées par {@code +remplace} sous Modérateur.
     */
    public record Envoi(String id, int joueurs, int sansMod, String duree, int attente, String derriere, int remplacees) {

        /** Pour le staff : « en file derrière n42 (tenue) : … », « en file derrière n41 : à l'écran dans ~25 s », ou vide. */
        public String file() {
            if (attente < 0) return "en file derrière " + derriere + " (tenue) : elle passera à son arrêt, 10 min au plus";
            if (attente >= 2) return "en file derrière " + derriere + " : à l'écran dans ~" + attente + " s";
            return "";
        }
    }

    /** Résultat d'un arrêt demandé par le staff : narrations arrêtées, joueurs prévenus, ou l'erreur. */
    public record Arret(int narrations, int joueurs, String erreur) { }

    /** Une narration envoyée (en cours, ou finie depuis peu : elle compte encore dans les files d'attente). */
    static final class EnCours {
        final String id;
        final List<Segment> segs;
        /** Tel qu'envoyé : {@link #AUTO}, {@link #TENIR} ou 2-180. */
        final int secondes;
        final int caracteres;
        /** Durée automatique estimée (ms), pages comprises ({@link #dureeAutoMs(List)}). */
        final long autoMs;
        final Mode mode;
        final Integer pos;
        final boolean tchat;
        final Portee portee;
        final long envoi;
        /** Compte de l'auteur ; null = console ou API. */
        final UUID auteur;
        /** Pseudo de l'auteur, CONSOLE ou origine de l'API (staff et journal seulement). */
        final String par;
        final Set<UUID> destinataires = new HashSet<>();
        /** Joueurs qui ont déjà reçu les lignes de tchat (repli ou {@code +tchat}) : jamais deux fois. */
        final Set<UUID> tchatRecu = new HashSet<>();
        /** Arrêtée à (epoch ms) ; 0 = non. */
        long finA;

        EnCours(String id, List<Segment> segs, int secondes, Mode mode, Integer pos, boolean tchat, Portee portee,
                long envoi, UUID auteur, String par) {
            this.id = id;
            this.segs = List.copyOf(segs);
            this.secondes = secondes;
            this.caracteres = caracteres(segs);
            this.autoMs = dureeAutoMs(segs);
            this.mode = mode == null ? Mode.FILE : mode;
            this.pos = pos;
            this.tchat = tchat;
            this.portee = portee;
            this.envoi = envoi;
            this.auteur = auteur;
            this.par = par;
        }

        boolean tenue() { return secondes == TENIR; }

        long dureeMs() { return secondes == TENIR ? TENIR_MAX_MS : secondes == AUTO ? autoMs : secondes * 1000L; }
    }

    /** Passage d'une narration à l'écran d'un joueur, de {@code debut} à {@code fin} (epoch ms). */
    record Passage(EnCours n, long debut, long fin) { }

    /** Envoi « tous » tapé en jeu, en attente de {@code /narration confirmer <jeton>}. */
    private record Attente(Portee portee, List<Segment> segs, int secondes, Mode mode, boolean tchat, long expire, int jeton) { }

    /** Garde-fou d'un émetteur sous Modérateur : son dernier envoi, et les joueurs visés en « joueur » (→ quand). */
    private static final class Debit {
        long dernier;
        final Map<UUID, Long> cibles = new HashMap<>();
    }

    private final ShinobiCore plugin;
    private final ModeAnimation animation;
    /** Par ordre d'envoi ; thread principal seulement. */
    private final List<EnCours> narrations = new ArrayList<>();
    private final Map<UUID, Attente> attentes = new HashMap<>();
    /**
     * Joueurs connectés depuis moins de 40 ticks (→ heure d'arrivée) : rien ne leur part en direct, le rattrapage leur
     * livre tout dans l'ordre (ses canaux ne sont pas encore déclarés, et une narration en direct doublerait la file).
     */
    private final Map<UUID, Long> enRattrapage = new HashMap<>();
    private final Map<UUID, Debit> debits = new HashMap<>();
    private int compteur, jetons;

    public Narrations(ShinobiCore plugin, ModeAnimation animation) {
        this.plugin = plugin;
        this.animation = animation;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CANAL)) m.registerOutgoingPluginChannel(plugin, CANAL);
    }

    /* ================================================================ texte */

    /**
     * Texte tapé par le staff → segments. Balises {@code <rouge>…</>}, {@code <or>}, {@code <ombre>}, {@code <gris>}
     * (fermées par {@code </>} ou {@code </rouge>} ; imbriquées, on revient à la couleur d'avant), alias {@code &c}
     * rouge, {@code &6} or, {@code &9} ombre, {@code &7} gris, {@code &r} ou {@code &f} retour au blanc, {@code &&} =
     * « & », {@code \n} = retour à la ligne. Toute autre balise ou mise en forme ({@code <b>}, {@code &l}, {@code §}…)
     * est refusée avec un message clair : rien de brut ne part aux joueurs.
     */
    public static Analyse analyser(String brut) {
        String s = brut == null ? "" : brut;
        if (s.length() > MAX_BRUT) return tropLong();
        List<Segment> segs = new ArrayList<>();
        Deque<String> pile = new ArrayDeque<>();
        String couleur = "";
        StringBuilder cur = new StringBuilder();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            char suiv = i + 1 < n ? s.charAt(i + 1) : 0;
            if (c == '\\' && (suiv == 'n' || suiv == 'N')) { cur.append('\n'); i += 2; continue; }
            if (c == '§') return Analyse.erreur("Le caractère § n'est pas permis. Couleurs : " + AIDE_COULEURS + ".");
            if (c == '&' && suiv != 0) {
                if (suiv == '&') { cur.append('&'); i += 2; continue; }
                String alias = alias(suiv);
                if (alias != null) {
                    vider(segs, cur, couleur);
                    couleur = alias;
                    i += 2;
                    continue;
                }
                if (CODES.indexOf(Character.toLowerCase(suiv)) >= 0)
                    return Analyse.erreur("Mise en forme non prise en charge : &" + suiv + ". Couleurs : " + AIDE_COULEURS
                            + " ; && pour écrire « & ».");
            }
            if (c == '<') {
                int fin = chevronFermant(s, i);
                String jeton = fin > i ? s.substring(i + 1, fin) : null;
                // « <<Konoha>> » (guillemets tapés au clavier) reste du texte
                boolean chevrons = i > 0 && s.charAt(i - 1) == '<' || fin > i && fin + 1 < n && s.charAt(fin + 1) == '>';
                if (jeton != null && !chevrons && BALISE.matcher(jeton).matches()) {
                    boolean ferme = jeton.startsWith("/");
                    String nom = (ferme ? jeton.substring(1) : jeton).toLowerCase(Locale.ROOT);
                    boolean connue = !nom.isEmpty() && COULEURS.contains(nom);
                    if (ferme && (nom.isEmpty() || connue)) {
                        vider(segs, cur, couleur);
                        couleur = pile.isEmpty() ? "" : pile.pop();
                    } else if (!ferme && connue) {
                        vider(segs, cur, couleur);
                        if (pile.size() < 16) pile.push(couleur);
                        couleur = nom;
                    } else {
                        return Analyse.erreur("Balise inconnue : <" + jeton + ">. Couleurs : " + AIDE_COULEURS + ".");
                    }
                    i = fin + 1;
                    continue;
                }
            }
            cur.append(c);
            i++;
        }
        vider(segs, cur, couleur);
        return verifier(normaliser(segs));
    }

    /** Le {@code >} d'une balise ouverte en {@code i}, à {@value #BALISE_MAX} caractères au plus (jamais tout le texte) ; -1 sinon. */
    private static int chevronFermant(String s, int i) {
        int lim = Math.min(s.length(), i + BALISE_MAX + 1);
        for (int k = i + 1; k < lim; k++) if (s.charAt(k) == '>') return k;
        return -1;
    }

    /**
     * Segments reçus du Poste de garde ({@code segs} : {@code [[texte, couleur], …]}, ou une simple chaîne) : texte
     * littéral (aucune balise lue), couleur inconnue = blanc, {@code §} et le caractère qui suit retirés, même nettoyage et
     * mêmes limites que le texte tapé ; plus de {@value #MAX_CARACTERES} segments ou de {@value #MAX_BRUT} caractères
     * bruts : refusé d'emblée.
     */
    public static Analyse depuisJson(JsonElement e) {
        List<Segment> l = new ArrayList<>();
        if (e != null && e.isJsonPrimitive()) {
            String t = e.getAsString();
            if (t.length() > MAX_BRUT) return tropLong();
            l.add(Segment.blanc(sansCodes(t)));
        } else if (e != null && e.isJsonArray()) {
            JsonArray arr = e.getAsJsonArray();
            if (arr.size() > MAX_CARACTERES) return Analyse.erreur("Trop de segments (" + MAX_CARACTERES + " au plus).");
            int brut = 0;
            for (JsonElement x : arr) {
                if (!x.isJsonArray() || x.getAsJsonArray().isEmpty()) continue;
                JsonArray a = x.getAsJsonArray();
                try {
                    String t = a.get(0).isJsonPrimitive() ? a.get(0).getAsString() : "";
                    brut += t.length();
                    if (brut > MAX_BRUT) return tropLong();
                    String c = a.size() > 1 && a.get(1).isJsonPrimitive() ? a.get(1).getAsString().trim().toLowerCase(Locale.ROOT) : "";
                    l.add(new Segment(sansCodes(t), c));
                } catch (RuntimeException ignored) { }
            }
        }
        return verifier(normaliser(l));
    }

    private static Analyse tropLong() {
        return Analyse.erreur("Texte trop long (" + MAX_CARACTERES + " caractères au plus).");
    }

    private static String alias(char c) {
        return switch (Character.toLowerCase(c)) {
            case 'c' -> "rouge";
            case '6' -> "or";
            case '9' -> "ombre";
            case '7' -> "gris";
            case 'r', 'f' -> "";
            default -> null;
        };
    }

    private static void vider(List<Segment> segs, StringBuilder cur, String couleur) {
        if (cur.isEmpty()) return;
        segs.add(new Segment(cur.toString(), couleur));
        cur.setLength(0);
    }

    private static Analyse verifier(List<Segment> segs) {
        int n = caracteres(segs);
        if (n == 0) return Analyse.erreur("Texte vide.");
        if (n > MAX_CARACTERES) return Analyse.erreur("Texte trop long : " + n + " caractères (" + MAX_CARACTERES + " au plus).");
        return new Analyse(List.copyOf(segs), null);
    }

    /** Retire chaque {@code §} et le caractère qui le suit (le tchat vanilla y lirait un code de mise en forme). */
    static String sansCodes(String t) {
        if (t == null || t.indexOf('§') < 0) return t == null ? "" : t;
        StringBuilder b = new StringBuilder(t.length());
        int i = 0;
        while (i < t.length()) {
            int c = t.codePointAt(i);
            i += Character.charCount(c);
            if (c == '§') {
                if (i < t.length()) i += Character.charCount(t.codePointAt(i));
                continue;
            }
            b.appendCodePoint(c);
        }
        return b.toString();
    }

    /**
     * Nettoie et regroupe, point de code par point de code : caractères de contrôle et de format retirés (sauf {@code \n}
     * et le liant U+200D des émojis composés), séparateurs Unicode de ligne (U+2028) et de paragraphe (U+2029) changés en
     * {@code \n} et {@code \n\n}, tabulation et espaces Unicode changées en espace (sauf les insécables U+00A0, U+2007,
     * U+202F), espaces réduites à une, aucune espace en début ou fin de ligne, deux retours à la ligne au plus (un
     * paragraphe), rien au début ni à la fin ; segments voisins de même couleur fusionnés, segments vides retirés.
     */
    static List<Segment> normaliser(List<Segment> entree) {
        StringBuilder t = new StringBuilder();
        List<String> cs = new ArrayList<>();   // couleur de chaque unité UTF-16 de t
        for (Segment s : entree) {
            String x = s.texte();
            int i = 0;
            while (i < x.length()) {
                int c = x.codePointAt(i);
                i += Character.charCount(c);
                if (c == '\r') {
                    if (i < x.length() && x.charAt(i) == '\n') continue;
                    c = '\n';
                }
                if (c == 0x2029) ajouter(t, cs, '\n', s.couleur());
                if (c == 0x2028 || c == 0x2029) c = '\n';
                ajouter(t, cs, c, s.couleur());
            }
        }
        while (!t.isEmpty() && (t.charAt(t.length() - 1) == ' ' || t.charAt(t.length() - 1) == '\n')) {
            t.setLength(t.length() - 1);
            cs.remove(cs.size() - 1);
        }
        List<Segment> out = new ArrayList<>();
        int debut = 0;
        for (int i = 1; i <= t.length(); i++) {
            if (i == t.length() || !cs.get(i).equals(cs.get(debut))) {
                out.add(new Segment(t.substring(debut, i), cs.get(debut)));
                debut = i;
            }
        }
        return out;
    }

    /** Ajoute un point de code au texte normalisé (règles de {@link #normaliser}). */
    private static void ajouter(StringBuilder t, List<String> cs, int c, String couleur) {
        if (c == '\t' || Character.getType(c) == Character.SPACE_SEPARATOR && c != 0x00A0 && c != 0x2007 && c != 0x202F) c = ' ';
        if (c != '\n' && invisible(c)) return;
        int k = t.length();
        if (c == ' ' && (k == 0 || t.charAt(k - 1) == ' ' || t.charAt(k - 1) == '\n')) return;
        if (c == '\n') {
            while (!t.isEmpty() && t.charAt(t.length() - 1) == ' ') { t.setLength(t.length() - 1); cs.remove(cs.size() - 1); }
            k = t.length();
            if (k == 0 || k >= 2 && t.charAt(k - 1) == '\n' && t.charAt(k - 2) == '\n') return;
        }
        t.appendCodePoint(c);
        for (int j = Character.charCount(c); j > 0; j--) cs.add(couleur);
    }

    /** Contrôle, format (étiquettes invisibles U+E0000… comprises), demi-paire UTF-16 isolée ; le liant U+200D reste. */
    private static boolean invisible(int c) {
        int type = Character.getType(c);
        return Character.isISOControl(c) || type == Character.FORMAT && c != 0x200D || type == Character.SURROGATE;
    }

    /** Coupe à {@code max} caractères, « … » à la fin (API : jamais de refus pour un texte trop long). */
    static List<Segment> couper(List<Segment> segs, int max) {
        if (caracteres(segs) <= max) return segs;
        List<Segment> out = new ArrayList<>();
        int reste = max - 1;
        for (Segment s : segs) {
            int n = s.texte().codePointCount(0, s.texte().length());
            if (n <= reste) { out.add(s); reste -= n; continue; }
            String debut = s.texte().substring(0, s.texte().offsetByCodePoints(0, reste)).stripTrailing();
            out.add(new Segment(debut + "…", s.couleur()));
            break;
        }
        return normaliser(out);
    }

    /** Nombre de caractères (points de code, retours à la ligne compris) : limite et durée automatique. */
    public static int caracteres(List<Segment> segs) {
        int n = 0;
        for (Segment s : segs) n += s.texte().codePointCount(0, s.texte().length());
        return n;
    }

    /** Texte à plat pour le journal et le staff : retours à la ligne → « / », {@code max} caractères au plus. */
    public static String aPlat(List<Segment> segs, int max) {
        StringBuilder b = new StringBuilder();
        for (Segment s : segs) b.append(s.texte());
        String t = b.toString().replaceAll("\\s*\\n+\\s*", " / ").trim();
        if (t.codePointCount(0, t.length()) <= max) return t;
        return t.substring(0, t.offsetByCodePoints(0, Math.max(0, max - 1))).stripTrailing() + "…";
    }

    /** Découpe un texte en morceaux de {@code max} unités UTF-16 au plus, sans couper une paire (journal). */
    static List<String> morceaux(String t, int max) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < t.length()) {
            int fin = Math.min(t.length(), i + max);
            if (fin < t.length() && Character.isHighSurrogate(t.charAt(fin - 1))) fin--;
            out.add(t.substring(i, fin));
            i = fin;
        }
        return out;
    }

    /* ================================================================ durées */

    /**
     * Durée automatique, la même que le mod ({@code recit/Recit.dureeAuto}) : {@code 1300 + n × 1000 / 30} ms (division
     * entière), bornée à 4 000 - 60 000, avec {@code n} = {@link #caracteres}. Règle du 02/11, calée sur les vidéos de
     * Ketto : 3 lignes pleines (≈ 210 caractères) tenues ≈ 7 s, plus les fondus d'entrée (0,6 s) et de sortie (0,7 s).
     */
    public static long dureeAutoMs(int caracteres) {
        return Math.max(4_000L, Math.min(60_000L, 1_300L + caracteres * 1_000L / 30));
    }

    /**
     * Durée automatique d'un texte, pages comprises : {@link #dureeAutoMs(int)} plus 1,2 s (deux fondus de page) par page
     * au-delà de la première, comme le mod ({@code Recit.fondusDePages}). Le mod compte les vraies pages (coupes de la
     * police, 3 lignes par page, un paragraphe par page) ; ici, une <b>estimation</b> ({@link #pagesEstimees}) : file,
     * rattrapage et « à l'écran dans ~N s » peuvent s'écarter d'une seconde ou deux du client sur un long texte.
     */
    public static long dureeAutoMs(List<Segment> segs) {
        return dureeAutoMs(caracteres(segs)) + (pagesEstimees(segs) - 1) * 2L * FONDU_PAGE_MS;
    }

    /**
     * Pages estimées d'une narration : texte entier de 3 lignes au plus (ligne vide comprise) = 1 page ; sinon chaque
     * paragraphe ({@code \n\n}) en pages de 3 lignes, une ligne pleine ≈ {@value #LIGNE_CARACTERES} caractères.
     */
    static int pagesEstimees(List<Segment> segs) {
        StringBuilder t = new StringBuilder();
        for (Segment x : segs) t.append(x.texte());
        String[] lignes = t.toString().split("\n", -1);
        int total = 0;
        for (String l : lignes) total += lignesDe(l);
        if (total <= LIGNES_PAGE) return 1;
        int pages = 0, par = 0;
        for (String l : lignes) {
            if (l.isBlank()) {
                pages += (par + LIGNES_PAGE - 1) / LIGNES_PAGE;
                par = 0;
            } else par += lignesDe(l);
        }
        pages += (par + LIGNES_PAGE - 1) / LIGNES_PAGE;
        return Math.max(1, pages);
    }

    private static int lignesDe(String l) {
        int n = l.codePointCount(0, l.length());
        return Math.max(1, (n + LIGNE_CARACTERES - 1) / LIGNE_CARACTERES);
    }

    public static boolean dureeValide(int s) { return s == AUTO || s == TENIR || s >= DUREE_MIN && s <= DUREE_MAX; }

    /** API : ramène toute valeur dans le protocole (négatif = tenue, 1 → 2, au-delà de 180 → 180). */
    static int borner(int s) {
        if (s == AUTO) return AUTO;
        if (s < 0) return TENIR;
        return Math.max(DUREE_MIN, Math.min(DUREE_MAX, s));
    }

    /**
     * Option de durée de la commande : {@code +auto}, {@code +tenir} (ou {@code +tenue}), {@code 30s}, {@code 45sec},
     * {@code 2m}, {@code 3min}. Null : ce n'est pas une durée, le texte commence (« Tenue correcte exigée… », « 3 jours
     * plus tard… » restent du texte). {@link #DUREE_INVALIDE} : une durée hors de 2-180 s.
     */
    public static Integer lireDuree(String jeton) {
        if (jeton == null) return null;
        String b = jeton.trim().toLowerCase(Locale.ROOT);
        if (b.equals("+auto")) return AUTO;
        if (b.equals("+tenir") || b.equals("+tenue")) return TENIR;
        Matcher m = DUREE.matcher(b);
        if (!m.matches()) return null;
        long v = Long.parseLong(m.group(1)) * (m.group(2).startsWith("m") ? 60 : 1);
        return v >= DUREE_MIN && v <= DUREE_MAX ? (int) v : DUREE_INVALIDE;
    }

    /** Mot d'option de la commande, avant le texte : durée, {@code +remplace}, {@code +tchat}. */
    public static boolean estOption(String jeton) {
        String b = jeton == null ? "" : jeton.trim().toLowerCase(Locale.ROOT);
        return b.equals("+remplace") || b.equals("+tchat") || lireDuree(b) != null;
    }

    /** « auto (14 s) », « 30 s », « tenue ». */
    public static String libelleDuree(int secondes, List<Segment> segs) {
        if (secondes == TENIR) return "tenue";
        if (secondes == AUTO) return "auto (" + Math.round(dureeAutoMs(segs) / 1000.0) + " s)";
        return secondes + " s";
    }

    /* ================================================================ droits */

    /**
     * Droit d'envoyer une narration de cette portée. {@code grade} : {@link StaffGrades} (console = Owner, comme
     * {@link OutilsStaff#grade}) ; {@code animation} : en mode animation ({@link #enAnimation}). Null = permis, sinon le
     * motif du refus. Helper : un joueur ; Modérateur : ici (64 blocs au plus), ce monde, tout le serveur ; animateur en
     * mode animation : un joueur, ou ici.
     */
    public static String refus(int grade, boolean animation, Type type, int rayon) {
        return switch (type) {
            case JOUEUR -> grade >= OutilsStaff.G_NARRATION_JOUEUR || animation ? null
                    : "Réservé au staff (Helper et plus) ou à un animateur en mode animation.";
            case ICI -> rayon < 1 || rayon > RAYON_MAX ? "Rayon de 1 à " + RAYON_MAX + " blocs."
                    : grade >= OutilsStaff.G_NARRATION || animation ? null
                    : grade >= OutilsStaff.G_NARRATION_JOUEUR
                    ? "Autour de toi : Modérateur et plus, ou en mode animation. Un Helper vise un joueur : /narration joueur <cible>."
                    : "Réservé au staff ou à un animateur en mode animation.";
            case MONDE, TOUS -> grade >= OutilsStaff.G_NARRATION ? null
                    : animation ? "En mode animation : un joueur, ou autour de toi (" + RAYON_MAX + " blocs au plus)."
                    : "Réservé aux modérateurs et plus.";
            case LISTE -> "Portée réservée aux systèmes du serveur.";
        };
    }

    /**
     * Garde-fou des émetteurs sous Modérateur (Helper, animateur), après {@link #refus} : pas de narration tenue,
     * {@value #DELAI_MIN_MS} ms au moins entre deux narrations, {@value #CIBLES_PAR_MINUTE} joueurs différents par minute
     * au plus en « joueur » ; un animateur sans grade staff ne vise qu'un joueur de son monde à {@value #RAYON_MAX} blocs
     * au plus, comme son « ici ». Le Modérateur, la console et l'API n'ont pas de limite. Null = permis, sinon le motif.
     * Le compte est tenu à l'envoi ({@link #diffuser}) : un refus ne compte pas.
     */
    public String refusEnvoi(CommandSender staff, int grade, Type type, Player cible, int secondes) {
        if (grade >= OutilsStaff.G_NARRATION || !(staff instanceof Player p)) return null;
        if (secondes == TENIR)
            return "Tenir une narration jusqu'à son arrêt : Modérateur et plus. Sinon une durée jusqu'à 3 min (2m, 180s).";
        if (grade < OutilsStaff.G_NARRATION_JOUEUR && type == Type.JOUEUR && cible != null && !aPortee(p, cible))
            return "En mode animation : un joueur de ton monde, à " + RAYON_MAX + " blocs au plus de toi.";
        Debit d = debits.get(p.getUniqueId());
        if (d == null) return null;
        long now = System.currentTimeMillis();
        long reste = d.dernier + DELAI_MIN_MS - now;
        if (reste > 0) return "Une narration toutes les " + DELAI_MIN_MS / 1000 + " s au plus : réessaie dans " + (reste + 999) / 1000 + " s.";
        if (type == Type.JOUEUR && cible != null) {
            d.cibles.values().removeIf(t -> now - t >= MINUTE_MS);
            if (!d.cibles.containsKey(cible.getUniqueId()) && d.cibles.size() >= CIBLES_PAR_MINUTE) {
                long r = Collections.min(d.cibles.values()) + MINUTE_MS - now;
                return CIBLES_PAR_MINUTE + " joueurs différents par minute au plus : réessaie dans " + (r + 999) / 1000 + " s.";
            }
        }
        return null;
    }

    /** Même monde, à {@value #RAYON_MAX} blocs au plus (distance 3D, comme {@link Portee#couvre}). */
    static boolean aPortee(Player a, Player b) {
        Location la = a.getLocation(), lb = b.getLocation();
        if (la.getWorld() == null || lb.getWorld() == null || !la.getWorld().getUID().equals(lb.getWorld().getUID())) return false;
        double dx = la.getX() - lb.getX(), dy = la.getY() - lb.getY(), dz = la.getZ() - lb.getZ();
        return dx * dx + dy * dy + dz * dz <= (double) RAYON_MAX * RAYON_MAX;
    }

    /** En mode animation, permission comprise (un animateur n'a pas besoin d'un grade staff). */
    public boolean enAnimation(CommandSender s) {
        return s instanceof Player p && animation != null && animation.actif(p.getUniqueId()) && ModeAnimation.permis(p);
    }

    /** Refus de droits ou de garde-fou au Poste de garde : tracé (une commande refusée l'est déjà par le journal des commandes). */
    public static void journalRefus(CommandSender staff, String demande, String motif) {
        Journal.entree(Categorie.STAFF, "NARRATION_REFUS").acteur(staff).detail(demande + " · " + motif).ecrire();
    }

    /* ================================================================ envoi */

    /**
     * Narration du staff ({@code /narration}, Poste de garde) : <b>les droits sont vérifiés par l'appelant</b>
     * ({@link #refus}, {@link #refusEnvoi}, confirmation de « tous »). Destinataires = joueurs en ligne dans la portée,
     * staff compris. Sous Modérateur, {@code REMPLACE} arrête les narrations de l'émetteur encore en cours chez l'un des
     * destinataires, puis envoie celle-ci en file.
     */
    public Envoi diffuser(CommandSender staff, Portee portee, List<Segment> segs, int secondes, Mode mode, Integer pos,
                          boolean tchat) {
        verifierThread();
        List<Player> joueurs = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (portee.couvre(p)) joueurs.add(p);
        return publier(staff, true, portee, joueurs, segs, secondes, mode, pos, tchat);
    }

    /**
     * API des autres émetteurs (cinématiques, douane, quêtes…) : affiche {@code segments} à ces joueurs. {@code secondes} :
     * {@link #AUTO}, {@link #TENIR} ou 2-180 (borné) ; texte nettoyé comme celui du staff, coupé à 1 000 caractères.
     * Journalisé ({@code STAFF NARRATION}, origine « API ») ; rattrapée si l'un d'eux se reconnecte pendant qu'elle passe.
     * Pas de garde-fou ; {@code REMPLACE} coupe l'écran et vide la file. Thread principal. Null si le texte est vide.
     */
    public Envoi envoyer(Collection<? extends Player> joueurs, List<Segment> segments, int secondes, Mode mode) {
        return envoyer(joueurs, segments, secondes, mode, null, false, null, null);
    }

    /**
     * Idem, avec {@code pos} (centre du bloc en % de la hauteur, 0-100, null = défaut du mod), copie au tchat, acteur du
     * journal (null = aucun) et origine (ex. « cinématique intro », libellé du journal et du staff).
     */
    public Envoi envoyer(Collection<? extends Player> joueurs, List<Segment> segments, int secondes, Mode mode,
                         Integer pos, boolean tchat, CommandSender acteur, String origine) {
        verifierThread();
        if (joueurs == null || segments == null) return null;
        List<Segment> nets = new ArrayList<>();
        for (Segment s : segments) if (s != null) nets.add(new Segment(sansCodes(s.texte()), s.couleur()));
        List<Segment> segs = couper(normaliser(nets), MAX_CARACTERES);
        if (segs.isEmpty()) return null;
        String o = origine == null || origine.isBlank() ? "API" : origine.trim();
        return publier(acteur, false, Portee.liste(o), joueurs, segs, borner(secondes), mode, pos, tchat);
    }

    private Envoi publier(CommandSender acteur, boolean staff, Portee portee, Collection<? extends Player> joueurs,
                          List<Segment> segs, int secondes, Mode mode, Integer pos, boolean tchat) {
        oublier();
        long now = System.currentTimeMillis();
        UUID auteur = acteur instanceof Player p ? p.getUniqueId() : null;
        String par = acteur != null ? acteur.getName() : portee.libelle();
        boolean sousModo = staff && auteur != null && OutilsStaff.grade(acteur) < OutilsStaff.G_NARRATION;
        String id = "n" + (++compteur);
        List<Player> vises = new ArrayList<>();
        Set<UUID> uuids = new HashSet<>();
        for (Player p : joueurs) if (p != null && p.isOnline() && uuids.add(p.getUniqueId())) vises.add(p);
        Mode m = mode == null ? Mode.FILE : mode;
        String libelleMode = m == Mode.REMPLACE ? " · remplace" : "";
        // Sous Modérateur, « remplace » ne touche que ses propres narrations encore en cours chez l'un de ces joueurs :
        // elles s'arrêtent, la nouvelle passe ensuite en file (derrière celles des autres, qui restent).
        List<EnCours> remplacees = new ArrayList<>();
        if (sousModo && m == Mode.REMPLACE) {
            for (EnCours x : siennes(auteur, now)) if (!Collections.disjoint(x.destinataires, uuids)) remplacees.add(x);
            for (EnCours x : remplacees) arreterUne(x, now);
            if (!remplacees.isEmpty()) journalFin(acteur, String.join(", ", ids(remplacees)) + " (remplacée(s) par " + id + ")");
            m = Mode.FILE;
            libelleMode = " · remplace (les siennes)";
        }
        EnCours n = new EnCours(id, segs, secondes, m, pos == null ? null : Math.max(0, Math.min(100, pos)), tchat,
                portee, now, auteur, par);
        int sansMod = 0;
        for (Player p : vises) {
            n.destinataires.add(p.getUniqueId());
            if (enRattrapage.containsKey(p.getUniqueId())) continue;   // le rattrapage la livrera, à sa place dans la file
            if (!livrer(p, n, n.secondes, n.mode, 0L)) sansMod++;
        }
        narrations.add(n);
        if (sousModo) {
            Debit d = debits.computeIfAbsent(auteur, k -> new Debit());
            d.dernier = now;
            if (portee.type() == Type.JOUEUR && portee.joueur() != null) d.cibles.put(portee.joueur(), now);
        }
        String duree = libelleDuree(n.secondes, n.segs);
        String plat = aPlat(segs, Integer.MAX_VALUE);
        String detail = portee.libelle() + " · " + n.destinataires.size() + " joueur(s) · " + duree + libelleMode
                + (tchat ? " · + tchat" : "") + " · « " + aPlat(segs, TEXTE_JOURNAL) + " » · " + n.id;
        entreeJournal("NARRATION", acteur, portee).detail(detail).ecrire();
        // Texte complet au journal : ce que les joueurs ont lu doit pouvoir être vérifié (1 000 caractères au plus ; le
        // journal coupe un détail à 600 unités UTF-16, d'où aussi le second test pour un texte chargé d'émojis).
        if (plat.codePointCount(0, plat.length()) > TEXTE_JOURNAL || detail.length() > DETAIL_JOURNAL) {
            List<String> ms = morceaux(plat, MORCEAU_JOURNAL);
            for (int i = 0; i < ms.size(); i++)
                entreeJournal("NARRATION_TEXTE", acteur, portee).detail(n.id + " · " + (i + 1) + "/" + ms.size() + " · « " + ms.get(i) + " »").ecrire();
        }
        int attente = 0;
        String derriere = "";
        for (UUID u : n.destinataires) {
            Place pl = place(n, u, now);
            if (pl == null) continue;
            if (pl.secondes() < 0) { attente = -1; derriere = pl.derriere().id; break; }
            if (pl.secondes() > attente) { attente = pl.secondes(); derriere = pl.derriere().id; }
        }
        return new Envoi(n.id, n.destinataires.size(), sansMod, duree, attente, derriere, remplacees.size());
    }

    private JournalService.Entree entreeJournal(String action, CommandSender acteur, Portee portee) {
        var e = Journal.entree(Categorie.STAFF, action);
        if (acteur != null) e.acteur(acteur);
        if (portee.type() == Type.JOUEUR && portee.joueur() != null) e.cible(portee.joueur());
        Location lieu = portee.centre() != null ? portee.centre() : acteur instanceof Player p ? p.getLocation() : null;
        if (lieu != null) e.lieu(lieu);
        return e;
    }

    /** Attente d'une narration chez un joueur : secondes avant l'affichage (-1 = derrière une tenue), et ce qu'elle attend. */
    private record Place(int secondes, EnCours derriere) { }

    /** Place de {@code n} dans la file du joueur {@code u} ; null si elle passe tout de suite (à 1 s près). */
    private Place place(EnCours n, UUID u, long now) {
        List<EnCours> l = new ArrayList<>();
        for (EnCours x : narrations) if (x.destinataires.contains(u)) l.add(x);
        List<Passage> ps = chronologie(l);
        for (int i = 0; i < ps.size(); i++) {
            if (ps.get(i).n() != n) continue;
            if (i == 0 || ps.get(i).debut() <= now + 1_000L) return null;
            // une tenue encore à l'écran ou en file devant elle : elle passera à son arrêt (ou au bout de ses 10 min)
            for (int k = 0; k < i; k++) {
                EnCours x = ps.get(k).n();
                if (x.tenue() && x.finA == 0 && ps.get(k).fin() > now) return new Place(-1, x);
            }
            return new Place((int) ((ps.get(i).debut() - now + 999) / 1000), ps.get(i - 1).n());
        }
        return null;
    }

    /**
     * À un joueur : canal du mod ; sinon (et avec {@code tchat}) des lignes de tchat en italique. {@code ecoule} : ms
     * déjà passées à l'écran (rattrapage), 0 sinon. True = par le mod.
     */
    private boolean livrer(Player p, EnCours n, int secondes, Mode mode, long ecoule) {
        boolean mod = ecoute(p);
        if (mod) envoyer(p, json(n.id, n.segs, secondes, mode, n.pos, ecoule));
        // lignes de tchat (repli sans le mod, ou +tchat) : une fois par joueur, pas à chaque reconnexion (rattrapage)
        if ((!mod || n.tchat) && n.tchatRecu.add(p.getUniqueId())) for (Component c : lignesTchat(n.segs)) p.sendMessage(c);
        return mod;
    }

    private static boolean ecoute(Player p) { return p.getListeningPluginChannels().contains(CANAL); }

    private void envoyer(Player p, JsonObject o) {
        // un plugin désactivé ne peut plus envoyer (« Plugin must be enabled ») : un émetteur appelé pendant l'arrêt
        // (onDisable) ne doit pas faire sauter la suite ; le mod efface tout à la déconnexion, qui suit
        if (!plugin.isEnabled()) return;
        p.sendPluginMessage(plugin, CANAL, o.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** {@code {"v":1,"t":"narration","id","segs":[[texte,couleur]…],"s","mode"[,"pos"][,"ecoule"]}}. */
    static JsonObject json(String id, List<Segment> segs, int secondes, Mode mode, Integer pos, long ecoule) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("t", "narration");
        o.addProperty("id", id);
        JsonArray a = new JsonArray();
        for (Segment s : segs) {
            JsonArray x = new JsonArray();
            x.add(s.texte());
            x.add(s.couleur());
            a.add(x);
        }
        o.add("segs", a);
        o.addProperty("s", secondes);
        o.addProperty("mode", (mode == null ? Mode.FILE : mode).id());
        if (pos != null) o.addProperty("pos", pos);
        if (ecoule > 0) o.addProperty("ecoule", ecoule);
        return o;
    }

    /** {@code {"v":1,"t":"narration_fin","id"}} ; id vide = tout effacer (écran et file). */
    static JsonObject jsonFin(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("t", "narration_fin");
        o.addProperty("id", id == null ? "" : id);
        return o;
    }

    /** Repli : le texte en lignes de tchat italiques, aux couleurs du mod ; un paragraphe = une ligne vide. */
    static List<Component> lignesTchat(List<Segment> segs) {
        List<Component> out = new ArrayList<>();
        Component ligne = Component.empty();
        for (Segment s : segs) {
            String[] morceaux = s.texte().split("\n", -1);
            for (int i = 0; i < morceaux.length; i++) {
                if (i > 0) {
                    out.add(ligne.decoration(TextDecoration.ITALIC, true));
                    ligne = Component.empty();
                }
                if (!morceaux[i].isEmpty()) ligne = ligne.append(Component.text(morceaux[i], TextColor.color(rgb(s.couleur()))));
            }
        }
        out.add(ligne.decoration(TextDecoration.ITALIC, true));
        return out;
    }

    /**
     * Couleurs du mod (brief du lot 2 §1.2 ; maquettes v2 du 01/11 : {@code ombre} en gris neutre, mesuré chez Ketto) :
     * blanc, rouge vermillon, or clair, ombre gris neutre, gris doux.
     */
    static int rgb(String couleur) {
        return switch (couleur == null ? "" : couleur) {
            case "rouge" -> 0xE5483C;
            case "or" -> 0xF2D08A;
            case "ombre" -> 0x8F8B8E;
            case "gris" -> 0xC2B59A;
            default -> 0xFFFFFF;
        };
    }

    /* ================================================================ fin */

    /**
     * {@code /narration fin [id]} et le Poste de garde. Avec id : la sienne, ou n'importe laquelle à partir de Modérateur,
     * tant que le serveur s'en souvient et qu'elle n'est pas déjà arrêtée (aucune estimation exigée : un client qui l'a
     * finie ignore l'arrêt). Sans id : Modérateur et plus (et la console) arrêtent tout, écrans nettoyés même si le
     * serveur n'en connaît plus aucune ; les autres, les leurs encore à l'écran ou en file (à 30 s près).
     */
    public Arret finir(CommandSender staff, String id) {
        verifierThread();
        oublier();
        long now = System.currentTimeMillis();
        boolean tout = OutilsStaff.grade(staff) >= OutilsStaff.G_NARRATION;
        UUID moi = staff instanceof Player p ? p.getUniqueId() : null;
        if (id != null && !id.isBlank()) {
            EnCours n = trouver(id.trim());
            if (n == null) return new Arret(0, 0, "Aucune narration récente sous ce nom : " + id.trim() + ".");
            if (n.finA > 0) return new Arret(0, 0, n.id + " est déjà arrêtée.");
            if (!tout && !Objects.equals(n.auteur, moi)) return new Arret(0, 0, "Seul son auteur ou un Modérateur peut arrêter " + n.id + ".");
            int k = arreterUne(n, now);
            journalFin(staff, n.id + " (" + n.portee.libelle() + ", posée par " + n.par + ")");
            return new Arret(1, k, null);
        }
        if (tout) {
            List<EnCours> encore = actives(now, MARGE_ARRET_MS);
            int k = arreterTout(now);
            journalFin(staff, encore.isEmpty() ? "tout · aucune en cours (écrans nettoyés)"
                    : "tout · " + encore.size() + " narration(s) · " + String.join(", ", ids(encore)));
            return new Arret(encore.size(), k, null);
        }
        List<EnCours> miennes = siennes(moi, now);
        if (miennes.isEmpty()) return new Arret(0, 0, "Aucune narration en cours de ta part.");
        int k = 0;
        for (EnCours n : miennes) k += arreterUne(n, now);
        journalFin(staff, String.join(", ", ids(miennes)) + " (les siennes)");
        return new Arret(miennes.size(), k, null);
    }

    /** API : arrête la narration {@code id} chez ses destinataires (à l'écran ou en file). False si inconnue ou déjà arrêtée. */
    public boolean fin(String id) {
        verifierThread();
        EnCours n = id == null ? null : trouver(id.trim());
        if (n == null || n.finA > 0) return false;
        arreterUne(n, System.currentTimeMillis());
        journalFin(null, n.id + " (" + n.portee.libelle() + ", par son émetteur)");
        return true;
    }

    /**
     * API : efface <b>tout</b>, narrations du staff comprises ({@code narration_fin} à id vide pour chaque joueur du mod).
     * Un émetteur arrête les siennes avec {@link #fin}. Renvoie le nombre de narrations qui passaient encore.
     */
    public int toutFinir() {
        verifierThread();
        long now = System.currentTimeMillis();
        List<EnCours> encore = actives(now, 0L);
        arreterTout(now);
        journalFin(null, "tout · " + encore.size() + " narration(s) (API)");
        return encore.size();
    }

    private int arreterUne(EnCours n, long now) {
        n.finA = now;
        JsonObject o = jsonFin(n.id);
        int k = 0;
        for (UUID u : n.destinataires) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && ecoute(p)) { envoyer(p, o); k++; }
        }
        return k;
    }

    /** Tout le monde (joueurs du mod), même ceux que le serveur ne croit pas concernés : l'arrêt doit être sûr. */
    private int arreterTout(long now) {
        for (EnCours n : narrations) if (n.finA == 0) n.finA = now;
        JsonObject o = jsonFin("");
        int k = 0;
        for (Player p : Bukkit.getOnlinePlayers()) if (ecoute(p)) { envoyer(p, o); k++; }
        return k;
    }

    private static void journalFin(CommandSender staff, String detail) {
        var e = Journal.entree(Categorie.STAFF, "NARRATION_FIN");
        if (staff != null) e.acteur(staff);
        e.detail(detail).ecrire();
    }

    private static List<String> ids(List<EnCours> l) {
        List<String> out = new ArrayList<>();
        for (EnCours n : l) out.add(n.id);
        return out;
    }

    private EnCours trouver(String id) {
        for (EnCours n : narrations) if (n.id.equalsIgnoreCase(id)) return n;
        return null;
    }

    /* ================================================================ « tous » : confirmation */

    /**
     * « tous » tapé en jeu : rien ne part avant {@code /narration confirmer <jeton>} (60 s). Une nouvelle attente remplace
     * la précédente ; le jeton empêche un ancien bouton [Confirmer] d'envoyer la nouvelle. Renvoie le jeton.
     */
    public int mettreEnAttente(Player staff, Portee portee, List<Segment> segs, int secondes, Mode mode, boolean tchat) {
        int j = ++jetons;
        attentes.put(staff.getUniqueId(), new Attente(portee, List.copyOf(segs), secondes, mode, tchat,
                System.currentTimeMillis() + CONFIRMATION_MS, j));
        return j;
    }

    /** Jeton de la narration en attente de ce staff ; null si aucune ou expirée. */
    public Integer enAttente(Player staff) {
        Attente a = attentes.get(staff.getUniqueId());
        return a == null || a.expire() < System.currentTimeMillis() ? null : a.jeton();
    }

    /** Envoie la narration en attente (null si aucune ou expirée). Le grade et le jeton sont revérifiés par l'appelant. */
    public Envoi confirmer(Player staff) {
        Attente a = attentes.remove(staff.getUniqueId());
        if (a == null || a.expire() < System.currentTimeMillis()) return null;
        return diffuser(staff, a.portee(), a.segs(), a.secondes(), a.mode(), null, a.tchat());
    }

    public boolean annuler(Player staff) { return attentes.remove(staff.getUniqueId()) != null; }

    /* ================================================================ file d'attente, rattrapage */

    /**
     * File d'attente d'un joueur, refaite comme le mod la tient : {@code file} commence quand l'écran est libre (400 ms
     * après la précédente), {@code remplace} coupe l'écran et vide la file, un arrêt coupe la narration (ou la retire de
     * la file avant qu'elle passe). {@code liste} : par ordre d'envoi.
     */
    static List<Passage> chronologie(List<EnCours> liste) {
        List<Passage> out = new ArrayList<>();
        long libre = Long.MIN_VALUE;
        for (EnCours n : liste) {
            long debut;
            if (n.mode == Mode.REMPLACE) {
                for (int i = out.size() - 1; i >= 0; i--) {
                    Passage p = out.get(i);
                    if (p.fin() <= n.envoi) continue;
                    if (p.debut() >= n.envoi) out.remove(i);
                    else out.set(i, new Passage(p.n(), p.debut(), n.envoi));
                }
                if (libre > n.envoi) libre = n.envoi;
                debut = n.envoi;
            } else {
                // file pleine chez le mod (8 en attente) : la plus ancienne en attente sort, les suivantes avancent
                int attente = 0, premiere = -1;
                for (int i = 0; i < out.size(); i++) {
                    if (out.get(i).debut() > n.envoi) {
                        attente++;
                        if (premiere < 0) premiere = i;
                    }
                }
                if (attente >= FILE_MAX) {
                    out.remove(premiere);
                    long l = premiere > 0 ? out.get(premiere - 1).fin() : Long.MIN_VALUE;
                    for (int i = premiere; i < out.size(); i++) {
                        Passage q = out.get(i);
                        long d = l == Long.MIN_VALUE ? q.n().envoi : Math.max(q.n().envoi, l + PAUSE_MS);
                        out.set(i, new Passage(q.n(), d, finDe(q.n(), d)));
                        l = out.get(i).fin();
                    }
                    libre = out.isEmpty() ? Long.MIN_VALUE : out.get(out.size() - 1).fin();
                }
                debut = libre == Long.MIN_VALUE ? n.envoi : Math.max(n.envoi, libre + PAUSE_MS);
            }
            if (n.finA > 0 && n.finA <= debut) continue;   // arrêtée avant de passer : retirée de la file
            long fin = finDe(n, debut);
            out.add(new Passage(n, debut, fin));
            libre = fin;
        }
        return out;
    }

    /** Fin d'une narration partie en {@code debut} : sa durée, ou son arrêt plus le fondu de sortie du mod (700 ms). */
    private static long finDe(EnCours n, long debut) {
        long fin = debut + n.dureeMs();
        return n.finA > 0 ? Math.min(fin, n.finA + SORTIE_MS) : fin;
    }

    /** Fin estimée de chaque narration : la plus tardive chez ses destinataires ; sans destinataire, seule à l'écran. */
    private Map<EnCours, Long> finsEstimees() {
        Map<UUID, List<EnCours>> parJoueur = new HashMap<>();
        for (EnCours n : narrations) for (UUID u : n.destinataires) parJoueur.computeIfAbsent(u, k -> new ArrayList<>()).add(n);
        Map<EnCours, Long> fins = new HashMap<>();
        for (List<EnCours> l : parJoueur.values()) for (Passage ps : chronologie(l)) fins.merge(ps.n(), ps.fin(), Math::max);
        for (EnCours n : narrations) {
            if (fins.containsKey(n)) continue;
            long seule = n.envoi + n.dureeMs();
            fins.put(n, n.finA > 0 ? Math.min(seule, n.finA) : seule);
        }
        return fins;
    }

    /**
     * Narrations non arrêtées encore à l'écran ou en file chez quelqu'un, par ordre d'envoi ; {@code marge} : finies depuis
     * moins de {@code marge} ms selon l'estimation comprises (les arrêts tolèrent un écart avec le mod).
     */
    private List<EnCours> actives(long now, long marge) {
        Map<EnCours, Long> fins = finsEstimees();
        List<EnCours> out = new ArrayList<>();
        for (EnCours n : narrations) if (n.finA == 0 && fins.getOrDefault(n, 0L) + marge > now) out.add(n);
        return out;
    }

    /** Les narrations de cet émetteur qu'il peut encore arrêter (à 30 s près). */
    private List<EnCours> siennes(UUID auteur, long now) {
        List<EnCours> out = new ArrayList<>();
        if (auteur == null) return out;
        for (EnCours n : actives(now, MARGE_ARRET_MS)) if (auteur.equals(n.auteur)) out.add(n);
        return out;
    }

    /** Identifiants que cet émetteur peut arrêter (complétion de {@code /narration fin}) : toutes à partir de Modérateur. */
    public List<String> idsArretables(CommandSender s) {
        long now = System.currentTimeMillis();
        if (OutilsStaff.grade(s) >= OutilsStaff.G_NARRATION) return ids(actives(now, MARGE_ARRET_MS));
        return s instanceof Player p ? ids(siennes(p.getUniqueId(), now)) : List.of();
    }

    /** Cet émetteur a-t-il une narration en cours (il peut l'arrêter, même sorti du mode animation) ? */
    public boolean aDesNarrations(CommandSender s) {
        return s instanceof Player p && !siennes(p.getUniqueId(), System.currentTimeMillis()).isEmpty();
    }

    /**
     * Mémoire bornée : 40 min après l'envoi ; au-delà de {@value #MEMOIRE_MAX}, les plus anciennes déjà finies, sans
     * dépasser la première qui passe encore (la file des suivantes en dépend) ; au-delà de {@value #MEMOIRE_DURE}, les plus
     * anciennes quoi qu'il arrive.
     */
    private void oublier() {
        long now = System.currentTimeMillis();
        narrations.removeIf(n -> now - n.envoi > OUBLI_MS);
        if (narrations.size() > MEMOIRE_MAX) {
            Map<EnCours, Long> fins = finsEstimees();
            while (narrations.size() > MEMOIRE_MAX && fins.getOrDefault(narrations.get(0), 0L) + MARGE_ARRET_MS <= now)
                narrations.remove(0);
            while (narrations.size() > MEMOIRE_DURE) narrations.remove(0);
        }
        attentes.values().removeIf(a -> a.expire() < now);
        debits.values().removeIf(d -> now - d.dernier >= MINUTE_MS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent ev) {
        UUID u = ev.getPlayer().getUniqueId();
        long arrivee = System.currentTimeMillis();
        enRattrapage.put(u, arrivee);
        // Le mod n'a déclaré ses canaux qu'après quelques ticks (comme l'écran noir). Un ancien objet Player reste
        // « en ligne » si le joueur s'est reconnecté entre-temps : on vérifie que c'est la même arrivée.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long a = enRattrapage.get(u);
            Player p = Bukkit.getPlayer(u);
            if (a != null && a == arrivee && p != null) rattraper(p, arrivee);
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent ev) {
        UUID u = ev.getPlayer().getUniqueId();
        attentes.remove(u);
        enRattrapage.remove(u);
    }

    /**
     * Rattrapage : les narrations qu'il a reçues ou dont il était destinataire pendant ses 40 premiers ticks, et celles
     * envoyées avant son arrivée dont il est dans la portée, refaites dans sa file. Celle qui passe encore part avec sa
     * durée d'origine et {@code ecoule} (ms déjà passées à l'écran ; seulement s'il reste au moins 5 s), les suivantes
     * telles qu'envoyées, toutes en {@code file}.
     */
    private void rattraper(Player p, long arrivee) {
        UUID u = p.getUniqueId();
        enRattrapage.remove(u);
        oublier();
        long now = System.currentTimeMillis();
        List<EnCours> liste = new ArrayList<>();
        for (EnCours n : narrations) {
            if (n.destinataires.contains(u) || n.envoi <= arrivee && n.portee.couvre(p)) liste.add(n);
        }
        for (Passage ps : chronologie(liste)) {
            if (ps.fin() <= now) continue;
            EnCours n = ps.n();
            long ecoule = 0;
            if (ps.debut() <= now) {
                if (ps.fin() - now < RATTRAPAGE_MIN_MS) continue;
                ecoule = now - ps.debut();
            }
            n.destinataires.add(u);
            livrer(p, n, n.secondes, Mode.FILE, ecoule);
        }
    }

    /* ================================================================ Poste de garde, arrêt */

    /**
     * État pour l'outil du Poste de garde ({@code t: narrations}) : narrations encore à l'écran ou en file, la plus récente
     * d'abord (10 au plus). {@code dernier} : celle que la requête vient de créer.
     */
    public JsonObject etat(Player viewer, String dernier) {
        oublier();
        long now = System.currentTimeMillis();
        boolean tout = StaffGrades.of(viewer) >= OutilsStaff.G_NARRATION;
        Map<EnCours, Long> fins = finsEstimees();
        JsonObject o = new JsonObject();
        o.addProperty("t", "narrations");
        o.addProperty("dernier", dernier == null ? "" : dernier);
        JsonArray arr = new JsonArray();
        for (int i = narrations.size() - 1; i >= 0 && arr.size() < 10; i--) {
            EnCours n = narrations.get(i);
            long fin = fins.getOrDefault(n, 0L);
            if (n.finA > 0 || fin <= now) continue;
            boolean moi = viewer.getUniqueId().equals(n.auteur);
            JsonObject j = new JsonObject();
            j.addProperty("id", n.id);
            j.addProperty("portee", n.portee.libelle());
            j.addProperty("joueurs", n.destinataires.size());
            j.addProperty("s", n.secondes);
            j.addProperty("reste", n.tenue() ? -1 : (int) ((fin - now + 999) / 1000));
            j.addProperty("mode", n.mode.id());
            j.addProperty("texte", aPlat(n.segs, 80));
            j.addProperty("par", n.par);
            j.addProperty("moi", moi);
            j.addProperty("arreter", moi || tout);
            arr.add(j);
        }
        o.add("actives", arr);
        return o;
    }

    /**
     * Arrêt du plugin : on oublie tout, <b>sans rien envoyer</b>. Paper désactive le plugin avant d'appeler
     * {@code onDisable} (un envoi lèverait « Plugin must be enabled » et ferait sauter la suite de l'arrêt, sauvegarde des
     * personnages comprise) ; le mod efface ses narrations à la déconnexion, qui suit.
     */
    public void arreter() {
        narrations.clear();
        attentes.clear();
        enRattrapage.clear();
        debits.clear();
    }

    private static void verifierThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Narrations : à appeler sur le thread principal.");
    }
}
