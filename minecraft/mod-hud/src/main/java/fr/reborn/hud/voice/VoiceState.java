package fr.reborn.hud.voice;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.audio.source.ClientAudioSource;
import su.plo.voice.proto.data.audio.line.VoiceSourceLine;
import su.plo.voice.proto.data.audio.source.EntitySourceInfo;
import su.plo.voice.proto.data.audio.source.PlayerSourceInfo;
import su.plo.voice.proto.data.audio.source.SourceInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pont vers PlasmoVoice côté client : qui parle (bulle), état voix perso (micro/casque du panneau vitals) et
 * <b>portée de la voix</b> (31/10 : les portées du serveur Plasmo Voice, pas de système Reborn ; la couleur du micro
 * dit la portée, la touche « Portée de la voix » les fait tourner).
 *
 * <p>On récupère le client via l'<b>instance statique</b>
 * {@code ModVoiceClient.INSTANCE} plutôt que l'injection {@code @InjectPlasmoVoice}
 * (qui n'alimente pas un champ Java de façon fiable). Tout est défensif : sans
 * PlasmoVoice (classe absente / non initialisé) chaque accès retombe sur une
 * valeur neutre, jamais de crash. Tout ce qui nomme une classe de Plasmo Voice est dans {@link Pv}, chargée au
 * premier appel seulement : le vérificateur de la JVM charge les types de l'API qu'une méthode retourne ou passe, et
 * l'ancien {@code client()} (ModVoiceClient rendu en PlasmoVoiceClient) empêchait VoiceState elle-même de se charger
 * sans Plasmo Voice (vérifié le 31/10 sur un classpath sans le jar).
 *
 * <p>L'état est relu <b>une fois par tick</b> ({@link #tick}, appelé par {@code HudKeybinds}) dans des champs et un
 * ensemble réutilisés : le HUD le lit à chaque image sans rien allouer ni rappeler Plasmo Voice.
 */
public final class VoiceState {

    private VoiceState() {}

    /** Classes de Plasmo Voice introuvables (pas dans le modpack) : plus aucune tentative. */
    private static boolean absent;

    public static boolean available() {
        if (absent) return false;
        try {
            return Pv.pret();
        } catch (NoClassDefFoundError e) {
            absent = true;
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    // ── Portée de la voix (31/10) ──────────────────────────────────
    /** Rang de la portée : chuchoter (la plus petite, vert), parler (jaune), crier (la plus grande, rouge). */
    public static final int CHUCHOTE = 0, NORMALE = 1, CRIEE = 2;
    /**
     * Retours de {@link #changerPortee()} quand la portée n'a pas changé ; {@link #REFUS_COUPEE} : voix refusée par
     * l'état (capacité {@code voc} = 0 : mort RP, corps contrôlé ou vide, cinématique), raison dans
     * {@code EtatClient.raison("voc")}.
     */
    public static final int REFUS_A_TERRE = -1, REFUS_INDISPONIBLE = -2, REFUS_PORTEE_FIXE = -3, REFUS_COUPEE = -4;

    /** Banc : rang imposé (0, 1 ou 2 ; null = celui de Plasmo Voice). Le chuchoté imposé (à terre) passe devant. */
    public static volatile Integer DEBUG_PORTEE;
    /** Banc : ids d'entités qui « parlent » (mannequins), ajoutés à {@link #speakingEntityIds()}. */
    public static final Set<Integer> DEBUG_PARLE = ConcurrentHashMap.newKeySet();
    /** Banc : le joueur local « parle ». */
    public static volatile boolean DEBUG_SOI_PARLE;
    /** Banc : micro coupé / assourdi imposés (aucune écriture dans la config de Plasmo Voice). */
    public static volatile boolean DEBUG_MUET, DEBUG_ASSOURDI;

    // relus à chaque tick (thread client) ; lus par le HUD à chaque image
    private static boolean parle, muet, assourdi, impose, coupee, relie;
    private static int rang = NORMALE, distance = -1, nombre;

    /**
     * Rang de la portée choisie : {@link #CHUCHOTE} (la plus petite portée du serveur), {@link #CRIEE} (la plus grande),
     * {@link #NORMALE} sinon, et aussi sans Plasmo Voice, hors serveur vocal, portée inconnue ou unique. À terre ou
     * inconscient (capacité {@code voc} limitée, ou phase KO 1 / 2) : chuchoté imposé.
     */
    public static int portee() {
        if (impose) return CHUCHOTE;
        Integer d = DEBUG_PORTEE;
        if (d != null) return Math.max(CHUCHOTE, Math.min(CRIEE, d));
        return rang;
    }

    /** Chuchoté imposé (à terre, inconscient) : la touche est refusée. */
    public static boolean porteeImposee() { return impose; }

    /** Portée choisie dans Plasmo Voice (blocs), -1 inconnue. */
    public static int distance() { return distance; }

    /** Nombre de portées que le serveur autorise (3 en mode plage {@code [-1, N]}), 0 inconnu. */
    public static int nombrePortees() { return nombre; }

    /** Thread client, chaque tick (HudKeybinds) : relit tout ce que le HUD affiche. */
    public static void tick(Minecraft mc) {
        impose = chuchoteImpose();
        coupee = coupeeParEtat();
        int lus = 0;
        boolean ok = false;
        if (!absent) {
            try {
                lus = Pv.tick(mc);
                ok = true;
            } catch (NoClassDefFoundError e) {
                absent = true;
            } catch (Throwable ignored) {
                // API indisponible / en cours d'init → valeurs neutres ce tick
            }
        }
        if (!ok) {
            parle = muet = assourdi = relie = false;
            distance = -1;
            nombre = 0;
            rang = NORMALE;
        }
        // Voix refusée par l'état (VOCAL = 0) : le serveur jette chaque trame (ShinobiRadio VoixATerre) — micro éteint,
        // aucune bulle sur soi, comme si le micro était coupé.
        if (coupee) parle = false;
        majParlent(lus);
    }

    /**
     * Capacité {@code voc} refusée par le serveur (mort RP, corps contrôlé ou vide, cinématique). {@code niveau} vaut 2
     * tant qu'aucun état n'est reçu : sans effet avec un serveur qui n'envoie pas {@code reborn:etat}.
     */
    private static boolean coupeeParEtat() {
        return fr.reborn.hud.etat.EtatClient.niveau("voc") == 0;
    }

    /** À terre ou inconscient : chuchoter seulement (capacité {@code voc} = 1, ou phase KO 1 / 2). */
    private static boolean chuchoteImpose() {
        return fr.reborn.hud.etat.EtatClient.niveau("voc") == 1 || fr.reborn.hud.ko.KoMasqueHud.masque();
    }

    /** Mode plage de Plasmo Voice : {@code [-1, N]} autorise toute portée de 1 à N. */
    private static boolean plage(List<Integer> ds) {
        if (ds == null || ds.size() != 2) return false;
        Integer premier = ds.get(0);
        return premier != null && premier == -1;
    }

    /**
     * Rang de la portée {@code d} parmi celles du serveur (Plasmo Voice les trie, on ne s'y fie pas) : la plus petite = 0,
     * la plus grande = 2, entre les deux = 1 ; hors liste (activation de repli, liste vide) ou portée unique = 1. Mode
     * plage : par tiers de N. Pure et sans allocation (appelée aussi depuis le fil réseau de Plasmo Voice).
     */
    static int rangDans(List<Integer> ds, int d) {
        if (ds == null || ds.isEmpty()) return NORMALE;
        if (plage(ds)) {
            Integer n = ds.get(1);
            int max = n == null ? 0 : n;
            if (d <= 0 || max <= 0) return NORMALE;
            float f = d / (float) max;
            return f <= 1f / 3f ? CHUCHOTE : f > 2f / 3f ? CRIEE : NORMALE;
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        boolean dedans = false;
        for (int i = 0, k = ds.size(); i < k; i++) {
            Integer v = ds.get(i);
            if (v == null) continue;
            int x = v;
            if (x < min) min = x;
            if (x > max) max = x;
            if (x == d) dedans = true;
        }
        if (!dedans || min >= max) return NORMALE;
        return d == min ? CHUCHOTE : d == max ? CRIEE : NORMALE;
    }

    /**
     * Portée qui suit {@code d} à l'appui : un cran <b>plus bas</b> — la plus grande sous {@code d}, sinon la plus grande
     * de toutes (on tourne : normale → chuchotée → criée → normale). Chuchoter tient en un appui depuis la portée par
     * défaut, et on ne passe par « criée » qu'en remontant depuis le chuchoté : avant le 31/10 au soir, baisser la voix
     * passait d'abord par la plus grande portée (et l'émettait si l'on parlait entre deux appuis). Mode plage : tout N →
     * la moitié → un quart → tout N. 0 s'il n'y a pas de choix.
     */
    static int suivante(List<Integer> ds, int d) {
        if (ds == null || ds.isEmpty()) return 0;
        if (plage(ds)) {
            Integer n = ds.get(1);
            int max = n == null ? 0 : n;
            if (max <= 1) return 0;
            int quart = Math.max(1, Math.round(max / 4f)), moitie = Math.max(1, Math.round(max / 2f));
            return d > moitie ? moitie : d > quart ? quart : max;
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, avant = Integer.MIN_VALUE;
        for (int i = 0, k = ds.size(); i < k; i++) {
            Integer v = ds.get(i);
            if (v == null || v <= 0) continue;
            int x = v;
            if (x < min) min = x;
            if (x > max) max = x;
            if (x < d && x > avant) avant = x;
        }
        if (min >= max) return 0;
        return avant != Integer.MIN_VALUE ? avant : max;
    }

    /** Rang d'une portée quelconque (cercle de portée de Plasmo Voice, fil réseau) ; 1 sans Plasmo Voice. */
    public static int rangDe(int d) {
        if (absent) return NORMALE;
        try {
            return Pv.rangDe(d);
        } catch (NoClassDefFoundError e) {
            absent = true;
            return NORMALE;
        } catch (Throwable t) {
            return NORMALE;
        }
    }

    /**
     * Touche « Portée de la voix » : un cran plus bas parmi les portées autorisées par le serveur ({@link #suivante} :
     * normale → chuchotée → criée → normale). On écrit la portée de proximité dans la config de Plasmo Voice (hors API :
     * {@code getServers().getById(serveur).getActivationDistance(PROXIMITY_ID)}, la même entrée que l'activation, dont
     * l'écouteur prévient le serveur, qui la revalide). Renvoie la nouvelle portée (blocs), ou {@link #REFUS_COUPEE}
     * (voix refusée par l'état), {@link #REFUS_A_TERRE} (chuchoté imposé), {@link #REFUS_PORTEE_FIXE} (une seule
     * portée), {@link #REFUS_INDISPONIBLE} (pas de Plasmo Voice, pas de serveur vocal, écriture impossible).
     */
    public static int changerPortee() {
        if (coupeeParEtat()) return REFUS_COUPEE;
        if (chuchoteImpose()) return REFUS_A_TERRE;
        if (absent) return REFUS_INDISPONIBLE;
        try {
            return Pv.changerPortee();
        } catch (NoClassDefFoundError e) {
            absent = true;
            return REFUS_INDISPONIBLE;
        } catch (Throwable t) {
            return REFUS_INDISPONIBLE;
        }
    }

    // ── État voix perso (testable en solo) ─────────────────────────
    /** true = le joueur local <b>transmet</b> en ce moment (activation de proximité active), ou le banc le dit. */
    public static boolean selfSpeaking() { return parle || DEBUG_SOI_PARLE; }

    /**
     * true = micro coupé (bouton mute local), mute serveur (staff) ou voix refusée par l'état (VOCAL = 0 : mort RP,
     * corps contrôlé ou vide, cinématique — le serveur jette tout, le micro ne doit pas dire « je parle »).
     */
    public static boolean selfMuted() { return muet || coupee || DEBUG_MUET; }

    /**
     * Liaison vocale établie : Plasmo Voice présent, serveur vocal annoncé, UDP connecté (le critère de Plasmo pour ses
     * propres icônes). Faux : personne ne nous entend, le micro passe au gris (« indisponible »). Le banc, qui impose
     * portée, parole ou micro coupé, la suppose établie.
     */
    public static boolean relie() {
        return relie || DEBUG_PORTEE != null || DEBUG_SOI_PARLE || DEBUG_MUET || DEBUG_ASSOURDI;
    }

    /** true = voix désactivée / assourdi (casque coupé). */
    public static boolean selfDeafened() { return assourdi || DEBUG_ASSOURDI; }

    // ── Volume d'un joueur (menu R, GAMEPLAN §14, 24/10) ───────────
    /** Volume de la voix d'un joueur chez moi seulement : 1 = normal ; on baisse, on ne coupe jamais. */
    public static double playerVolume(UUID id) {
        Double d = DEBUG_VOLUMES.get(id);
        if (d != null) return d;
        if (absent) return 1.0;
        try {
            return Pv.playerVolume(id);
        } catch (NoClassDefFoundError e) {
            absent = true;
            return 1.0;
        } catch (Throwable t) { return 1.0; }
    }

    /** Règle le volume (borné à 25 %… 100 %, jamais 0) et enregistre la config de Plasmo Voice. */
    public static void setPlayerVolume(UUID id, double volume) {
        double v = Math.max(0.25, Math.min(1.0, volume));
        if (absent) return;
        try {
            Pv.setPlayerVolume(id, v);
        } catch (NoClassDefFoundError e) {
            absent = true;
        } catch (Throwable ignored) { }
    }

    /** Banc d'essai : volumes imposés (aucune écriture dans la config de Plasmo Voice). */
    public static final java.util.Map<UUID, Double> DEBUG_VOLUMES = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Micro d'un autre joueur (onglet Douane du Poste de garde, 25/10) ─
    public static final int MICRO_INCONNU = -1, MICRO_SANS_VOIX = 0, MICRO_ACTIF = 1, MICRO_COUPE = 2, MICRO_MUET = 3;

    /**
     * État du micro d'un joueur, tel que le serveur Plasmo Voice le diffuse : {@link #MICRO_ACTIF}, {@link #MICRO_COUPE}
     * (il a coupé son micro), {@link #MICRO_MUET} (rendu muet par le staff), {@link #MICRO_SANS_VOIX} (pas de Plasmo
     * Voice, ou voix désactivée) ; {@link #MICRO_INCONNU} sans Plasmo Voice chez soi ou hors serveur vocal.
     */
    public static int micro(UUID id) {
        Integer d = DEBUG_MICROS.get(id);
        if (d != null) return d;
        if (absent || id == null) return MICRO_INCONNU;
        try {
            return Pv.micro(id);
        } catch (NoClassDefFoundError e) {
            absent = true;
            return MICRO_INCONNU;
        } catch (Throwable t) { return MICRO_INCONNU; }
    }

    /** Banc d'essai : états de micro imposés. */
    public static final java.util.Map<UUID, Integer> DEBUG_MICROS = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Qui parle autour (bulle de parole) ─────────────────────────
    /** Qui parle : ensemble réutilisé (retouché seulement quand il change), vue non modifiable rendue au HUD. */
    private static final HashSet<Integer> PARLENT = new HashSet<>();
    private static final Set<Integer> PARLENT_VUE = Collections.unmodifiableSet(PARLENT);
    /** Ids lus ce tick (sans doublon) et contenu actuel de {@link #PARLENT}, en entiers : comparés sans rien allouer. */
    private static int[] lus = new int[16], membres = new int[16];
    /** Pour chaque id lu / membre : sa voix vient de la ligne de proximité de Plasmo Voice (pas de la radio). */
    private static boolean[] lusProche = new boolean[16], membresProche = new boolean[16];
    private static int nMembres;

    /**
     * Entity ids des joueurs/entités dont une source audio est active (= parlent), plus {@link #DEBUG_PARLE}. Vide si
     * PlasmoVoice absent. Ne contient jamais le joueur local (on ne s'entend pas soi-même → pas de source). Relu une
     * fois par tick : la même vue non modifiable à chaque appel, rien n'est alloué.
     */
    public static Set<Integer> speakingEntityIds() {
        return PARLENT_VUE;
    }

    /**
     * La voix de {@code id} (parmi {@link #speakingEntityIds()}) vient-elle de la ligne de <b>proximité</b> de Plasmo
     * Voice ? Vrai : la bulle porte jusqu'à 64 blocs (une source de proximité activée est forcément audible : Plasmo
     * n'envoie la voix qu'aux auditeurs à portée de l'orateur) ; faux (radio, autre ligne) : la bulle reste à 8 blocs.
     * Les mannequins du banc ({@link #DEBUG_PARLE}) parlent en proximité.
     */
    public static boolean voixDeProximite(int id) {
        for (int i = 0; i < nMembres; i++) if (membres[i] == id) return membresProche[i];
        return false;
    }

    private static int ajouterId(int n, int id, boolean proche) {
        for (int i = 0; i < n; i++) {
            if (lus[i] == id) {
                lusProche[i] |= proche;
                return n;
            }
        }
        if (n == lus.length) {
            lus = Arrays.copyOf(lus, n * 2);
            lusProche = Arrays.copyOf(lusProche, n * 2);
        }
        lus[n] = id;
        lusProche[n] = proche;
        return n + 1;
    }

    /** Remet {@link #PARLENT} à jour (avec le banc) seulement si son contenu change ; la ligne de chaque voix suit. */
    private static void majParlent(int n) {
        if (!DEBUG_PARLE.isEmpty()) {
            for (Integer id : DEBUG_PARLE) if (id != null) n = ajouterId(n, id, true);
        }
        if (n == nMembres) {
            boolean pareil = true;
            for (int i = 0; i < n && pareil; i++) {
                boolean trouve = false;
                for (int j = 0; j < nMembres; j++) {
                    if (membres[j] == lus[i]) {
                        membresProche[j] = lusProche[i];
                        trouve = true;
                        break;
                    }
                }
                pareil = trouve;
            }
            if (pareil) return;
        }
        PARLENT.clear();
        if (membres.length < n) {
            membres = new int[lus.length];
            membresProche = new boolean[lus.length];
        }
        for (int i = 0; i < n; i++) {
            PARLENT.add(lus[i]);
            membres[i] = lus[i];
            membresProche[i] = lusProche[i];
        }
        nMembres = n;
    }

    /**
     * Tout ce qui nomme une classe de Plasmo Voice. Chargée et vérifiée au premier appel seulement : sans Plasmo Voice,
     * elle échoue (NoClassDefFoundError, rattrapé par l'appelant, qui note {@link #absent}) sans emporter VoiceState.
     */
    private static final class Pv {

        private Pv() {}

        private static PlasmoVoiceClient client() {
            return su.plo.voice.client.ModVoiceClient.INSTANCE;
        }

        static boolean pret() { return client() != null; }

        /** Relit l'état voix perso et la portée dans VoiceState ; renvoie le nombre d'ids lus dans {@link #lus}. */
        static int tick(Minecraft mc) {
            PlasmoVoiceClient c = client();
            boolean p = false, m = false, a = false, l = false;
            int d = -1, n = 0, r = NORMALE;
            if (c != null) {
                // liaison : serveur vocal annoncé et UDP connecté (Plasmo juge ainsi ses icônes, EntityIconStateExtractor)
                l = c.getServerInfo().isPresent() && c.getUdpClientManager().isConnected();
                ClientActivation act = c.getActivationManager().getParentActivation().orElse(null);
                if (act != null) {
                    // l'activation, PAS AudioCapture.isActive() qui veut juste dire « micro ouvert » (toujours vrai)
                    p = act.isActive() && !act.isDisabled();
                    d = act.getDistance();
                    List<Integer> ds = act.getDistances();
                    n = plage(ds) ? 3 : ds == null ? 0 : ds.size();
                    r = rangDans(ds, d);
                }
                m = c.getAudioCapture().isServerMuted()
                    || Boolean.TRUE.equals(c.getConfig().getVoice().getMicrophoneDisabled().value());
                a = Boolean.TRUE.equals(c.getConfig().getVoice().getDisabled().value());
            }
            // micro coupé, rendu muet ou assourdi : Plasmo Voice n'émet plus rien (VoiceAudioCapture)
            parle = p && !m && !a && l;
            muet = m;
            assourdi = a;
            relie = l;
            distance = d;
            nombre = n;
            rang = r;
            return c != null && mc != null && mc.level != null ? lireSources(c, mc) : 0;
        }

        /**
         * Sources de Plasmo Voice actives → ids d'entités (dans {@link #lus}), avec leur ligne (proximité ou non : la
         * radio a la sienne ; son chuchoté autour de l'orateur, lui, est en proximité) ; renvoie leur nombre.
         */
        private static int lireSources(PlasmoVoiceClient c, Minecraft mc) {
            int n = 0;
            try {
                for (ClientAudioSource<?> source : c.getSourceManager().getSources()) {
                    if (source == null || !source.isActivated()) continue;
                    SourceInfo info = source.getSourceInfo();
                    boolean proche = info != null && VoiceSourceLine.PROXIMITY_ID.equals(info.getLineId());
                    if (info instanceof EntitySourceInfo e) {
                        n = ajouterId(n, e.getEntityId(), proche);
                    } else if (info instanceof PlayerSourceInfo p && p.getPlayerInfo() != null) {
                        UUID uuid = p.getPlayerInfo().getPlayerId();
                        Player pl = uuid != null ? mc.level.getPlayerByUUID(uuid) : null;
                        if (pl != null) n = ajouterId(n, pl.getId(), proche);
                    }
                }
            } catch (RuntimeException ignored) {
                // API indisponible / en cours d'init → personne ce tick
            }
            return n;
        }

        static int rangDe(int d) {
            PlasmoVoiceClient c = client();
            if (c == null) return NORMALE;
            ClientActivation a = c.getActivationManager().getParentActivation().orElse(null);
            return a == null ? NORMALE : rangDans(a.getDistances(), d);
        }

        static int changerPortee() {
            PlasmoVoiceClient c = client();
            if (c == null) return REFUS_INDISPONIBLE;
            ClientActivation a = c.getActivationManager().getParentActivation().orElse(null);
            if (a == null) return REFUS_INDISPONIBLE;
            List<Integer> ds = a.getDistances();
            int cible = suivante(ds, a.getDistance());
            if (cible <= 0) return ds == null || ds.isEmpty() ? REFUS_INDISPONIBLE : REFUS_PORTEE_FIXE;
            if (!ecrirePortee(c, cible) || a.getDistance() != cible) return REFUS_INDISPONIBLE;
            // couleur et notification à jour tout de suite, sans attendre le tick
            distance = cible;
            rang = rangDans(ds, cible);
            return cible;
        }

        /** Écrit la portée de proximité (config de Plasmo Voice, hors API) et l'enregistre (écriture asynchrone). */
        private static boolean ecrirePortee(PlasmoVoiceClient c, int cible) {
            java.util.Optional<su.plo.voice.api.client.connection.ServerInfo> info = c.getServerInfo();
            if (info.isEmpty()) return false;
            su.plo.voice.client.config.VoiceClientConfig cfg = su.plo.voice.client.ModVoiceClient.INSTANCE.getConfig();
            java.util.Optional<su.plo.voice.client.config.VoiceClientConfig.Server> serveur =
                cfg.getServers().getById(info.get().getServerId());
            if (serveur.isEmpty()) return false;
            java.util.Optional<su.plo.config.entry.IntConfigEntry> entree =
                serveur.get().getActivationDistance(su.plo.voice.proto.data.audio.capture.VoiceActivation.PROXIMITY_ID);
            if (entree.isEmpty()) return false;
            entree.get().set(Integer.valueOf(cible));
            cfg.save(true);
            return true;
        }

        static double playerVolume(UUID id) {
            PlasmoVoiceClient c = client();
            if (c == null) return 1.0;
            Object v = c.getConfig().getVoice().getVolumes().getPlayerVolume(id).value();
            return v instanceof Number n ? n.doubleValue() : 1.0;
        }

        static void setPlayerVolume(UUID id, double v) throws ReflectiveOperationException {
            PlasmoVoiceClient c = client();
            if (c == null) return;
            c.getConfig().getVoice().getVolumes().setPlayerVolume(id, v);
            Object cfg = c.getConfig();
            cfg.getClass().getMethod("save", boolean.class).invoke(cfg, true);   // hors API, comme RadioVoix
        }

        static int micro(UUID id) {
            PlasmoVoiceClient c = client();
            if (c == null) return MICRO_INCONNU;
            var conn = c.getServerConnection();
            if (conn.isEmpty()) return MICRO_INCONNU;
            var info = conn.get().getPlayerById(id);
            if (info.isEmpty() || info.get().isVoiceDisabled()) return MICRO_SANS_VOIX;
            if (info.get().isMuted()) return MICRO_MUET;
            return info.get().isMicrophoneMuted() ? MICRO_COUPE : MICRO_ACTIF;
        }
    }
}
