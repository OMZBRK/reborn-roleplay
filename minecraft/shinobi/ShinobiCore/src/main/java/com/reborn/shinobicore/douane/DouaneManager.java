package com.reborn.shinobicore.douane;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.listener.CharacterLifecycleListener;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.staff.outils.ModeStaff;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La douane en jeu (CANDIDATURE_ET_DOUANE.md §3.3 à §3.10) : aiguillage à la connexion, hall, file, bureaux, mode
 * examinateur, questions, décision. Protocole vers le mod : {@link DouaneCanal} (PLUGINS_REFERENCE §4 decies).
 *
 * <p><b>Aiguillage</b> ({@code douane.enabled: true}) : à l'arrivée, état d'attente neutre (écran opaque, immobile,
 * rien d'interactif) jusqu'à une preuve — la réponse de {@code GET /v1/game/acces} lue au pré-login, ou la marque
 * {@code reborn.scope} de RebornGuardian. {@code play} → sélection ; {@code douane} → hall ; sans preuve au bout de
 * {@code douane.delais.preuve-secondes} → expulsion. <b>Refus par défaut</b> : la sélection n'ouvre que sur une preuve
 * positive de {@code play} ({@link #selectionPermise}). Un op ou un membre du staff n'est jamais envoyé au hall.
 *
 * <p>Douane <b>fermée</b> ({@code douane.enabled: true} mais voix du hall fermé non réglée) : l'aiguillage reste actif
 * (refus par défaut, {@code play} → sélection), mais aucune admission au hall : une preuve {@code douane} expulse.
 *
 * <p>Douane coupée ({@code douane.enabled: false}) : aiguillage d'avant (sélection directe), mais une marque
 * {@code douane} de Guardian expulse, et le choix d'un personnage attend la fin du contrôle tardif
 * ({@link #choixPermis}) : un candidat n'arrive jamais en jeu normal.
 *
 * <p>Fil principal partout, sauf le pré-login et le tchat (asynchrones) : les ensembles qu'ils lisent sont
 * concurrents.
 */
public final class DouaneManager {

    /** Permission des examinateurs (avec un grade {@code reborn.staff.*} Helper au minimum). */
    public static final String PERMISSION = "reborn.douane.examinateur";
    /** Marque posée par RebornGuardian après une attestation valide. */
    public static final String META_PORTEE = "reborn.scope";
    public static final String GUARDIAN = "RebornGuardian";

    /** Un candidat admis à la douane. */
    static final class Candidat {
        final UUID id;
        final String pseudo;
        DouaneClient.Acces acces;
        long arriveeAt;
        /** null = au hall ; sinon l'identifiant du bureau. */
        String bureau;
        /** Questions déjà tirées pour lui, par leur texte (une banque rechargée ne les décale pas). */
        final Set<String> questions = new HashSet<>();
        boolean ecranFerme;
        boolean alerteFerme;
        long dernierRafraichissement;
        String derniereLigne = "";
        long ligneAt;

        Candidat(UUID id, String pseudo) {
            this.id = id;
            this.pseudo = pseudo;
        }

        String reservationId() {
            return acces != null && acces.douane() != null ? acces.douane().reservationId() : null;
        }

        long convocationAt() {
            return acces != null && acces.douane() != null ? acces.douane().convocationAt() : 0L;
        }
    }

    /** Un bureau : un examinateur + un candidat. */
    static final class Bureau {
        final DouaneConfig.BureauConf conf;
        UUID examinateur;
        UUID candidat;
        /** Candidat déconnecté au bureau : il y revient s'il se reconnecte à temps. */
        UUID absent;
        long absentDepuis;
        long depuis;
        /** Question tirée (son texte), null sans tirage. */
        String question;
        boolean montree;
        JsonObject dossier;
        String notes = "";

        Bureau(DouaneConfig.BureauConf conf) {
            this.conf = conf;
        }

        boolean libre() {
            return candidat == null && absent == null && examinateur == null;
        }

        void vider() {
            candidat = null;
            absent = null;
            question = null;
            montree = false;
            dossier = null;
            notes = "";
        }
    }

    private record PreuveApi(DouaneClient.Acces acces, long at) {}
    /** Départ après une coupure : de quoi reprendre la place (rang en tête, questions déjà tirées). */
    private record Depart(long at, long arriveeAt, long teteAt, Set<String> questions) {}
    /** Refus local après un retrait ou un report : pour cette place, jusqu'à {@code jusquA}. */
    private record Refus(long jusquA, String reservationId, String message) {}

    private final ShinobiCore plugin;
    private final DouaneClient client;
    private DouaneConfig conf;
    private boolean actif;
    /**
     * Douane activée mais fermée depuis le démarrage (raison), ou null. L'aiguillage reste actif (refus par défaut) ;
     * le monde de la douane n'est pas chargé et aucune preuve {@code douane} n'est admise au hall.
     */
    private String horsService;
    private World monde;

    private final Map<UUID, PreuveApi> preuves = new ConcurrentHashMap<>();
    private final Map<UUID, DouaneClient.Acces> derniersAcces = new ConcurrentHashMap<>();
    private final Map<UUID, Long> attentes = new HashMap<>();
    private final Set<UUID> enAttente = ConcurrentHashMap.newKeySet();
    /** Contrôle tardif : aiguillé vers le jeu normal sans la marque de Guardian, jusqu'à (ms epoch). */
    private final Map<UUID, Long> surveillances = new ConcurrentHashMap<>();
    /** Choix de personnage (sélection, création) retenu pendant le contrôle tardif : rejoué à sa fin. */
    private final Map<UUID, Runnable> choixDifferes = new ConcurrentHashMap<>();
    /** Connectés, pas encore aiguillés (du JOIN au tick suivant) : figés, comme l'attente neutre. */
    private final Set<UUID> arrivants = ConcurrentHashMap.newKeySet();
    /** Retirés ou reportés : la même place ne rouvre pas la douane (lu au pré-login, asynchrone). */
    private final Map<UUID, Refus> refuses = new ConcurrentHashMap<>();
    /** Joueur → bureau où il passe l'oral (candidat ou examinateur) ; lu par le tchat, asynchrone. */
    private final Map<UUID, String> salles = new ConcurrentHashMap<>();
    private final Set<UUID> admisJeu = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Candidat> candidats = new ConcurrentHashMap<>();
    private final Set<UUID> transitions = ConcurrentHashMap.newKeySet();
    private final FileDouane file = new FileDouane();
    private final Map<String, Bureau> bureaux = new LinkedHashMap<>();
    private final Map<UUID, Depart> departs = new HashMap<>();
    private final Map<UUID, String> ecrans = new HashMap<>();
    private final Set<UUID> ecransMod = new HashSet<>();
    private final Set<UUID> muets = new HashSet<>();
    private final Set<UUID> teleportsInternes = new HashSet<>();
    private final Map<UUID, Location> retours = new HashMap<>();
    private volatile boolean ouvert;
    /** Hall fermé sans interruption depuis (ms epoch) ; 0 = ouvert. */
    private long fermeDepuis;
    private TirageQuestions tirage = new TirageQuestions(List.of());
    private final Random hasard = new Random();

    private ModeExaminateur mode;
    private DouaneCanal canal;
    private EnvoisDecision envois;
    private BukkitTask tacheRapide, tacheLente;
    private boolean etatSale = true;
    private long dernierEtat;
    private int compteurLent;
    private String dernieresPlaques = "";
    private long plaquesAt;

    public DouaneManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.client = new DouaneClient(plugin);
    }

    /* ================================================================ cycle de vie */

    public void demarrer() {
        conf = DouaneConfig.lire(plugin.getConfig());
        actif = conf.enabled;
        horsService = null;
        if (actif && !conf.voixValide()) {
            // §3.4 : hall fermé = voix coupée (joueurs dès 15 ans, sans encadrant). Sans les deux commandes, la douane
            // ne s'ouvre pas : pas de hall avec la proximité Plasmo ouverte. L'aiguillage, lui, reste actif (refus par
            // défaut) : retomber en sélection directe laisserait un candidat atteindre le jeu normal avant le contrôle.
            horsService = "voix du hall fermé non réglée";
            plugin.getLogger().severe("[douane] douane.enabled est vrai, mais douane.voix.couper ou douane.voix.rendre"
                    + " est vide : la voix du hall fermé ne serait pas coupée. Douane FERMÉE : aiguillage actif, accès"
                    + " « play » vers la sélection, accès « douane » expulsés. Remplir les deux commandes (ou poser"
                    + " douane.voix.non-coupee-acceptee: true), puis redémarrer le serveur.");
        }
        canal = new DouaneCanal(plugin, this);
        canal.enregistrer();
        mode = new ModeExaminateur(plugin, this);
        envois = new EnvoisDecision(plugin, client, new SuiteDecision());
        envois.regler(conf.renvoiMs, conf.abandonMs);
        Bukkit.getPluginManager().registerEvents(new DouaneEcouteur(this), plugin);
        DouaneCommand cmd = new DouaneCommand(this);
        PluginCommand pc = plugin.getCommand("douane");
        if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
        else plugin.getLogger().warning("Command 'douane' is not declared in plugin.yml.");
        PluginCommand pm = plugin.getCommand("modeexaminateur");
        if (pm != null) pm.setExecutor(cmd);
        else plugin.getLogger().warning("Command 'modeexaminateur' is not declared in plugin.yml.");

        if (actif) {
            if (horsService == null) {
                monde = MondeDouane.charger(plugin, conf);
                reconstruireBureaux();
                tirage = new TirageQuestions(conf.questions);
                fermeDepuis = System.currentTimeMillis();   // le hall démarre fermé
            }
            // Décisions restées sur disque : renvoyées même douane fermée (le candidat absent est géré).
            envois.charger();
            // Joueurs déjà en jeu (rechargement à chaud) : refus par défaut (§3.12). Seuls le staff et les porteurs de
            // la marque « play » de Guardian restent ; les autres (candidats, joueurs en attente : la file est perdue)
            // se reconnectent et repassent par l'aiguillage.
            for (Player p : List.copyOf(Bukkit.getOnlinePlayers())) {
                if (staffEnJeu(p) || marqueGuardian(p) == Aiguillage.Portee.PLAY) admisJeu.add(p.getUniqueId());
                else expulser(p, ReglesDouane.EXPULSION_REVERIFIER, "rechargement à chaud sans preuve play");
            }
            String routes = client.nonPret();
            String etatRoutes = routes == null ? "prêtes (serveur " + client.serveur() + ")" : routes;
            if (horsService == null) {
                plugin.getLogger().info("[douane] active — monde « " + conf.monde + " »" + (monde == null ? " INDISPONIBLE" : "")
                        + ", " + bureaux.size() + " bureau(x), " + tirage.taille() + " question(s), routes du jeu : "
                        + etatRoutes + ".");
            } else {
                plugin.getLogger().warning("[douane] FERMÉE (" + horsService + ") — aiguillage actif, aucune admission au"
                        + " hall, routes du jeu : " + etatRoutes + ".");
            }
            if (routes != null) {
                plugin.getLogger().warning("[douane] sans routes du jeu, seule l'attestation Guardian aiguille les joueurs"
                        + " et aucune décision ne peut partir.");
            }
            if (horsService == null && !conf.voixCoupable()) {
                plugin.getLogger().warning("[douane] voix du hall fermé NON coupée (douane.voix.non-coupee-acceptee: true).");
            }
        } else {
            plugin.getLogger().info("[douane] coupée (douane.enabled: false) : sélection directe à la connexion.");
        }
        tacheRapide = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRapide, 5L, 5L);
        tacheLente = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLent, 20L, 20L);
    }

    /** Relit la config ({@code /sc reload}) : positions, bureaux libres, questions, délais. Pas {@code enabled}. */
    public void recharger() {
        DouaneConfig neuve = DouaneConfig.lire(plugin.getConfig());
        if (neuve.enabled != actif || !neuve.monde.equals(conf.monde)) {
            plugin.getLogger().warning("[douane] douane.enabled et douane.monde ne changent qu'au redémarrage.");
        }
        if (horsService != null) {
            // Fermée depuis le démarrage : délais relus (preuve, HTTP, envois), mais le hall n'ouvre qu'au redémarrage.
            conf = neuve;
            envois.regler(conf.renvoiMs, conf.abandonMs);
            plugin.getLogger().warning("[douane] fermée depuis le démarrage (" + horsService + ") : elle ne s'ouvre"
                    + " qu'au prochain redémarrage, douane.voix.* remplies.");
            return;
        }
        if (actif && !neuve.voixValide()) {
            plugin.getLogger().severe("[douane] rechargement refusé : douane.voix.couper ou douane.voix.rendre est vide"
                    + " (la voix du hall fermé ne serait plus coupée). Réglages de la douane inchangés.");
            return;
        }
        // actif et monde restent ceux du démarrage (champs à part) ; le reste est relu. Les questions en cours sont
        // gardées par leur texte : la nouvelle banque ne décale ni la question affichée ni les exclusions.
        conf = neuve;
        TirageQuestions neuf = new TirageQuestions(conf.questions);
        neuf.reprendre(tirage);
        tirage = neuf;
        envois.regler(conf.renvoiMs, conf.abandonMs);
        reconstruireBureaux();
        etatSale = true;
    }

    /**
     * Arrêt du serveur ou du plugin : examinateurs ramenés, voix rendues, file perdue (les décisions en attente restent
     * sur disque). Candidats et joueurs figés sont expulsés : sans personnage, rien à sauvegarder, et après un
     * rechargement ils repassent par l'aiguillage (refus par défaut).
     */
    public void arreter() {
        if (tacheRapide != null) tacheRapide.cancel();
        if (tacheLente != null) tacheLente.cancel();
        if (mode != null) mode.arreter();
        for (UUID id : List.copyOf(muets)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) rendreVoix(p);
        }
        List<Player> aExpulser = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Location l = retours.remove(id);
            if (l != null && dansMonde(p)) p.teleport(l);
            // Candidat, ou ancien candidat entre la décision et la sortie : l'Aventure de la douane ne reste pas.
            if (candidats.containsKey(id) || transitions.contains(id)) p.setGameMode(Bukkit.getDefaultGameMode());
            if (restreint(id)) {
                retirerEcran(p);   // Cécité et Obscurité infinies : jamais laissées dans les données du joueur
                aExpulser.add(p);
            }
        }
        // État vidé AVANT l'expulsion : la déconnexion ne planifie rien (le plugin est déjà désactivé).
        candidats.clear();
        attentes.clear();
        enAttente.clear();
        transitions.clear();
        arrivants.clear();
        salles.clear();
        surveillances.clear();
        choixDifferes.clear();
        for (Player p : aExpulser) {
            if (p.isOnline()) p.kick(Component.text(ReglesDouane.EXPULSION_REDEMARRAGE));
        }
    }

    /** Bureaux selon la config ; l'état des bureaux qui gardent leur identifiant est conservé. */
    private void reconstruireBureaux() {
        Map<String, Bureau> avant = new LinkedHashMap<>(bureaux);
        bureaux.clear();
        for (DouaneConfig.BureauConf b : conf.bureaux) {
            Bureau neuf = new Bureau(b);
            Bureau vieux = avant.remove(b.id());
            if (vieux != null) {
                neuf.examinateur = vieux.examinateur;
                neuf.candidat = vieux.candidat;
                neuf.absent = vieux.absent;
                neuf.absentDepuis = vieux.absentDepuis;
                neuf.depuis = vieux.depuis;
                neuf.question = vieux.question;
                neuf.montree = vieux.montree;
                neuf.dossier = vieux.dossier;
                neuf.notes = vieux.notes;
            }
            bureaux.put(b.id(), neuf);
        }
        // Bureaux retirés de la config : leur candidat revient au hall.
        for (Bureau b : avant.values()) {
            if (b.candidat != null) {
                Candidat c = candidats.get(b.candidat);
                if (c != null) renvoyerAuHall(c, true);
            }
        }
        synchroniserSalles();
    }

    /**
     * Salles du tchat : le candidat d'un bureau et son examinateur (aussi quand le candidat est déconnecté et attendu).
     * Fil principal ; la table est lue par le tchat, asynchrone : on retire et on ajoute sans jamais la vider.
     */
    private void synchroniserSalles() {
        Map<UUID, String> neuf = new HashMap<>();
        for (Bureau b : bureaux.values()) {
            if (b.candidat == null && b.absent == null) continue;
            if (b.candidat != null) neuf.put(b.candidat, b.conf.id());
            if (b.examinateur != null) neuf.put(b.examinateur, b.conf.id());
        }
        salles.keySet().retainAll(neuf.keySet());
        salles.putAll(neuf);
    }

    /* ================================================================ lecture (raccords) */

    /** Aiguillage actif ({@code douane.enabled} au démarrage). */
    public boolean actif() { return actif; }

    World monde() { return monde; }

    DouaneConfig conf() { return conf; }

    DouaneClient client() { return client; }

    ModeExaminateur mode() { return mode; }

    DouaneCanal canal() { return canal; }

    boolean ouvert() { return ouvert; }

    FileDouane file() { return file; }

    Map<String, Bureau> bureaux() { return bureaux; }

    Map<UUID, Candidat> candidats() { return candidats; }

    int envoisEnAttente() { return envois == null ? 0 : envois.taille(); }

    boolean envoiEnCours(UUID candidat) { return envois != null && envois.pour(candidat) != null; }

    TirageQuestions tirage() { return tirage; }

    public boolean candidat(UUID id) { return id != null && candidats.containsKey(id); }

    /**
     * En attente d'une preuve d'accès, connecté mais pas encore aiguillé (du JOIN au tick suivant), ou entre la décision
     * et la sortie : figé, rien d'interactif (ni commande, ni tchat, ni téléportation, ni interaction).
     */
    public boolean fige(UUID id) {
        return id != null && (enAttente.contains(id) || transitions.contains(id) || arrivants.contains(id));
    }

    boolean enAttenteDePreuve(UUID id) { return id != null && enAttente.contains(id); }

    /** Candidat ou joueur figé : confiné, aucune commande sauf {@code /douane}, aucune interaction. */
    public boolean restreint(UUID id) { return candidat(id) || fige(id); }

    /** JOIN (priorité la plus basse) : figé jusqu'à l'aiguillage du tick suivant. Douane active seulement. */
    void arrive(UUID id) {
        if (actif && id != null) arrivants.add(id);
    }

    /** Bureau où ce joueur passe l'oral (candidat ou examinateur), ou null (hall). Lisible hors du fil principal. */
    String salle(UUID id) { return id == null ? null : salles.get(id); }

    /**
     * Cadre {@code HORS_JEU.DOUANE} (EtatServiceImpl) : candidat admis, examinateur en mode examinateur, et tout autre
     * occupant du monde de la douane hors mode staff (staff venu par {@code /douane aller} avec un personnage actif :
     * aucune mécanique RP au milieu des candidats, §3.5). Sauf le mode construction, pour bâtir le décor.
     */
    public boolean cadreDouane(UUID id) {
        if (id == null) return false;
        if (candidats.containsKey(id) || (mode != null && mode.actif(id))) return true;
        if (monde == null) return false;
        Player p = Bukkit.getPlayer(id);
        return p != null && dansMonde(p) && !ModeStaff.horsJeu(p) && !plugin.isStaffBuilding(id);
    }

    /**
     * La sélection de personnage peut-elle s'ouvrir pour ce joueur ? Douane coupée : oui. Active : seulement après une
     * preuve positive de {@code play} (ou l'acceptation à la douane). Refus par défaut.
     */
    public boolean selectionPermise(Player p) {
        return !actif || (p != null && admisJeu.contains(p.getUniqueId()));
    }

    /**
     * Le choix d'un personnage (sélection, création) peut-il s'appliquer maintenant ? Faux pendant le contrôle tardif
     * (aiguillé vers le jeu normal sans la marque de Guardian) tant que cette marque n'est pas {@code play} : un
     * client qui retarde son attestation n'entre pas dans le monde RP avant le contrôle. Au plus
     * {@code douane.delais.preuve-secondes} ; avec Guardian 0.2.0, la marque arrive en moins d'une seconde.
     */
    public boolean choixPermis(Player p) {
        if (p == null) return false;
        Long fin = surveillances.get(p.getUniqueId());
        if (fin == null) return true;
        Aiguillage.Portee m = marqueGuardian(p);
        if (m == Aiguillage.Portee.DOUANE) return false;   // expulsion au prochain passage du contrôle
        return m == Aiguillage.Portee.PLAY || System.currentTimeMillis() > fin;
    }

    /**
     * Retient un choix refusé par {@link #choixPermis} : rejoué sur le fil principal dès la fin du contrôle tardif
     * (marque {@code play} ou délai écoulé), oublié si le joueur est expulsé ou part. Le dernier remplace le précédent.
     */
    public void differerChoix(Player p, Runnable rejouer) {
        if (p == null || rejouer == null) return;
        choixDifferes.put(p.getUniqueId(), rejouer);
        plugin.getLogger().info("[douane] " + p.getName() + " : choix de personnage retenu jusqu'à la fin du contrôle d'accès.");
    }

    /** Fin du contrôle tardif sans marque restrictive : le choix retenu, s'il y en a un, s'applique. */
    private void finSurveillance(Player p) {
        UUID id = p.getUniqueId();
        surveillances.remove(id);
        Runnable r = choixDifferes.remove(id);
        if (r != null && p.isOnline()) r.run();
    }

    boolean dansMonde(Player p) {
        return p != null && monde != null && p.getWorld().equals(monde);
    }

    /** Peut examiner : grade staff Helper au minimum ET la permission dédiée (Poste de garde : les deux). */
    public static boolean peutExaminer(Player p) {
        return p != null && StaffGrades.of(p) >= StaffGrades.HELPER && p.hasPermission(PERMISSION);
    }

    /** Membre du staff en jeu : jamais envoyé au hall. */
    static boolean staffEnJeu(Player p) {
        return StaffGrades.of(p) > StaffGrades.NONE || p.hasPermission("shinobicore.staff");
    }

    /**
     * Encadrant présent dans le monde de la douane : le hall n'ouvre qu'avec lui. Droits revérifiés à chaque passage :
     * un examinateur rétrogradé pendant son mode n'encadre plus (le mode tombe aussi, {@link ModeExaminateur#verifier}).
     */
    boolean encadrant(Player q) {
        UUID id = q.getUniqueId();
        if (candidats.containsKey(id) || fige(id)) return false;
        return (mode != null && mode.actif(id) && peutExaminer(q)) || StaffGrades.of(q) >= StaffGrades.HELPER;
    }

    /**
     * Marque {@code reborn.scope} posée par l'instance <b>chargée et active</b> de RebornGuardian, comparée par
     * identité : un autre propriétaire, même nommé « RebornGuardian », est ignoré. Guardian absent ou désactivé : null.
     */
    @SuppressWarnings("deprecation")
    static Aiguillage.Portee marqueGuardian(Player p) {
        Plugin g = Bukkit.getPluginManager().getPlugin(GUARDIAN);
        if (g == null || !g.isEnabled()) return null;
        for (MetadataValue v : p.getMetadata(META_PORTEE)) {
            if (v.getOwningPlugin() == g) {
                Aiguillage.Portee portee = Aiguillage.Portee.lire(v.asString());
                return portee == Aiguillage.Portee.AUCUNE ? null : portee;
            }
        }
        return null;
    }

    /** Efface la marque « douane » de Guardian après l'acceptation : un nouveau contrôle ne renverrait plus au hall. */
    @SuppressWarnings("deprecation")
    private static void effacerMarqueGuardian(Player p) {
        Plugin g = Bukkit.getPluginManager().getPlugin(GUARDIAN);
        if (g != null) p.removeMetadata(META_PORTEE, g);
    }

    /**
     * Préfixe du tchat d'un joueur dans le monde de la douane (CharacterNicknameListener) : le pseudo Minecraft, avec
     * « Examinateur » devant pour un examinateur en mode ; null hors de la douane. Appelé hors du fil principal.
     */
    public static Component prefixeTchat(Player sender) {
        ShinobiCore core = ShinobiCore.get();
        DouaneManager d = core == null ? null : core.douane();
        if (d == null || !d.actif || sender == null) return null;
        UUID id = sender.getUniqueId();
        boolean exam = d.mode != null && d.mode.actif(id);
        if (!exam && !d.candidats.containsKey(id) && !d.dansMonde(sender)) return null;
        Component pseudo = Component.text(sender.getName(), NamedTextColor.GRAY).decoration(TextDecoration.BOLD, true);
        if (!exam) return pseudo;
        return Component.text("Examinateur ", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, false).append(pseudo);
    }

    /* ================================================================ pré-login et arrivée */

    /** Pré-login (asynchrone) : interroge l'API, délai court. Un refus explicite coupe la connexion tout de suite. */
    void preLogin(UUID id, String nom, java.util.function.Consumer<Component> refuser) {
        if (!actif) return;
        DouaneClient.Acces a = client.acces(id, conf.httpMs);
        if (a == null) {
            plugin.getLogger().info("[douane] " + nom + " : pas de réponse de l'API au pré-login, attente de l'attestation.");
            return;
        }
        preuves.put(id, new PreuveApi(a, System.currentTimeMillis()));
        derniersAcces.put(id, a);
        if (a.portee() == Aiguillage.Portee.AUCUNE && !Bukkit.getOfflinePlayer(id).isOp()) {
            preuves.remove(id);
            String msg = a.message() == null || a.message().isBlank() ? "Accès refusé." : a.message();
            plugin.getLogger().info("[douane] " + nom + " refusé au pré-login (" + a.raison() + ").");
            refuser.accept(Component.text(msg));
            return;
        }
        if (a.portee() == Aiguillage.Portee.DOUANE && !Bukkit.getOfflinePlayer(id).isOp()) {
            String refus = refusLocal(id, a);
            if (refus != null) {
                preuves.remove(id);
                plugin.getLogger().info("[douane] " + nom + " refusé au pré-login (retiré ou reporté de cette séance).");
                refuser.accept(Component.text(refus));
            }
        }
    }

    /**
     * Refus local encore valable pour cette arrivée à la douane (retrait, séance reportée), ou null. Une autre
     * réservation passe : la séance suivante reste possible. Lisible hors du fil principal.
     */
    private String refusLocal(UUID id, DouaneClient.Acces a) {
        Refus r = refuses.get(id);
        if (r == null) return null;
        if (System.currentTimeMillis() > r.jusquA()) { refuses.remove(id, r); return null; }
        String place = a != null && a.douane() != null ? a.douane().reservationId() : null;
        return ReglesDouane.refusApplicable(r.reservationId(), place) ? r.message() : null;
    }

    /** Note un refus local pour la place en cours de ce candidat (jusqu'à la fin de sa fenêtre d'entrée). */
    private void noterRefus(Candidat c, String message) {
        DouaneClient.Acces a = c.acces != null ? c.acces : derniersAcces.get(c.id);
        DouaneClient.Place pl = a == null ? null : a.douane();
        long t = System.currentTimeMillis();
        refuses.put(c.id, new Refus(ReglesDouane.finRefus(pl == null ? 0L : pl.entreeFermeAt(), t, conf.repriseMs),
                pl == null ? null : pl.reservationId(), message));
    }

    /**
     * À l'arrivée (un tick après le JOIN, avant toute sélection). Renvoie vrai si la douane prend la main : l'appelant
     * n'ouvre alors pas la sélection lui-même.
     */
    public boolean aiguiller(Player p) {
        UUID id = p.getUniqueId();
        arrivants.remove(id);
        long t = System.currentTimeMillis();
        if (!actif) {
            if (!staffEnJeu(p)) {
                // Marque « douane » déjà posée : jamais le jeu normal, pas même la sélection.
                if (marqueGuardian(p) == Aiguillage.Portee.DOUANE) {
                    expulser(p, ReglesDouane.EXPULSION_DOUANE_INACTIVE, "marque douane, douane coupée sur ce serveur");
                    return true;
                }
                // Marque « douane » tardive de Guardian (jusqu'à 8 s, preuve-secondes ≥ 10) : contrôle tardif, et le
                // choix d'un personnage attend sa fin (choixPermis).
                surveillances.put(id, t + conf.preuveMs);
            }
            return false;
        }
        PreuveApi pa = preuves.remove(id);
        DouaneClient.Acces acces = pa != null && t - pa.at() <= 60_000L ? pa.acces() : null;
        Aiguillage.Decision d = Aiguillage.decider(staffEnJeu(p), acces == null ? null : acces.portee(),
                marqueGuardian(p), 0L, conf.preuveMs);
        if (d.issue() == Aiguillage.Issue.ATTENDRE) {
            entrerAttente(p, t);
            return true;
        }
        appliquer(p, d, acces, t);
        return true;
    }

    private void appliquer(Player p, Aiguillage.Decision d, DouaneClient.Acces acces, long arriveeAt) {
        UUID id = p.getUniqueId();
        switch (d.issue()) {
            case SELECTION -> {
                sortirAttente(p);
                admisJeu.add(id);
                // Décidé sans Guardian : sa marque, si elle arrive, ne doit pas être plus restrictive.
                if (!d.source().contains("guardian") && !"staff".equals(d.source())) {
                    surveillances.put(id, arriveeAt + conf.preuveMs);
                }
                if ("guardian".equals(d.source())) {
                    Journal.entree(Categorie.JOUEUR, "ACCES_GUARDIAN").acteur(p)
                            .detail("jeu normal sur l'attestation Guardian (API muette au pré-login)").ecrire();
                }
                if (acces != null && acces.candidature() != null && plugin.characterSelect() != null) {
                    plugin.characterSelect().definirCandidature(id, acces.candidature());
                }
                CharacterLifecycleListener.ouvrirSelection(plugin, p);
            }
            case DOUANE -> {
                sortirAttente(p);
                DouaneClient.Acces a = acces != null ? acces : derniersAcces.get(id);
                // Retiré ou reporté de cette séance : la même place ne rouvre pas le hall (API muette comprise).
                String refus = refusLocal(id, a);
                if (refus != null) {
                    expulser(p, refus, "retiré ou reporté de cette séance");
                    return;
                }
                if (horsService != null || monde == null) {
                    expulser(p, ReglesDouane.EXPULSION_DOUANE_FERMEE, horsService != null
                            ? "douane fermée (" + horsService + ")" : "monde de la douane indisponible");
                    return;
                }
                // Oral déjà passé, décision en attente d'envoi : il ne revient pas au hall, le résultat suit au launcher.
                if (envoiEnCours(id)) {
                    expulser(p, ReglesDouane.TEXTE_RESULTAT_DIFFERE, "décision de la douane en attente d'envoi");
                    return;
                }
                admettre(p, a);
            }
            case EXPULSER -> {
                String msg = "api".equals(d.source()) && acces != null && acces.message() != null
                        ? acces.message() : ReglesDouane.EXPULSION_SANS_PREUVE;
                expulser(p, msg, d.source());
            }
            default -> { }
        }
    }

    private void expulser(Player p, String message, String raison) {
        sortirAttente(p);
        Journal.entree(Categorie.JOUEUR, "ACCES_REFUSE").acteur(p).detail(raison + " · « " + message + " »").ecrire();
        plugin.getLogger().info("[douane] " + p.getName() + " expulsé : " + raison + ".");
        p.kick(Component.text(message));
    }

    /* ================================================================ attente neutre */

    private void entrerAttente(Player p, long debut) {
        UUID id = p.getUniqueId();
        attentes.put(id, debut);
        enAttente.add(id);
        cacher(p);
        // Non vérifié, peut-être près des joueurs en sélection ou en plein monde RP : voix coupée aussi (§3.4 ; Plasmo
        // peut ignorer hideEntity, §3.2).
        couperVoix(p);
        appliquerEcran(p, ReglesDouane.TEXTE_VERIFICATION);
        plugin.getLogger().info("[douane] " + p.getName() + " : aucune preuve d'accès, attente de l'attestation.");
    }

    private void sortirAttente(Player p) {
        UUID id = p.getUniqueId();
        if (attentes.remove(id) == null) return;
        enAttente.remove(id);
        retirerEcran(p);
        rendreVoix(p);   // admettre la recoupe si le hall est fermé
        montrer(p);
    }

    /** JOIN (MONITOR) : le nouvel arrivant et les joueurs en attente neutre ne se voient pas (cacher() ne voit que les présents). */
    void cacherArrivant(Player nouveau) {
        for (UUID id : enAttente) {
            Player o = Bukkit.getPlayer(id);
            if (o == null || o.equals(nouveau)) continue;
            o.hideEntity(plugin, nouveau);
            nouveau.hideEntity(plugin, o);
        }
    }

    /** Cache le joueur en attente (il est là où il s'est déconnecté, peut-être en plein monde RP), et les autres de lui. */
    private void cacher(Player p) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            p.hideEntity(plugin, o);
            o.hideEntity(plugin, p);
        }
    }

    private void montrer(Player p) {
        var sel = plugin.characterSelect();
        for (Player o : Bukkit.getOnlinePlayers()) {
            UUID oid = o.getUniqueId();
            // Un arrivant pas encore aiguillé n'est pas caché : on le montre (il sera caché s'il passe en attente).
            if (o.equals(p) || enAttente.contains(oid) || transitions.contains(oid) || (sel != null && sel.isSelecting(oid))) continue;
            p.showEntity(plugin, o);
            o.showEntity(plugin, p);
        }
        if (plugin.vanish() != null) {
            plugin.vanish().applyForViewer(p);
            plugin.vanish().apply(p);
        }
    }

    /** Écran opaque : effets (marchent sans le mod, avant même que ses canaux soient déclarés), puis écran du mod. */
    private void appliquerEcran(Player p, String texte) {
        UUID id = p.getUniqueId();
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        ecrans.put(id, texte);
        ecransMod.remove(id);
        envoyerEcran(p, texte);
    }

    private void envoyerEcran(Player p, String texte) {
        UUID id = p.getUniqueId();
        if (canal.ecoute(p)) {
            canal.attente(p, true, texte);
            ecransMod.add(id);
        } else if (p.getListeningPluginChannels().contains(DouaneCanal.CANAL_ECRAN)) {
            canal.noir(p, true, texte);
            ecransMod.add(id);
        } else {
            p.showTitle(net.kyori.adventure.title.Title.title(Component.empty(), Component.text(texte, NamedTextColor.GRAY),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(4),
                            java.time.Duration.ofMillis(400))));
        }
    }

    private void retirerEcran(Player p) {
        UUID id = p.getUniqueId();
        if (ecrans.remove(id) == null) return;
        com.reborn.shinobicore.util.Effets.retirerInfini(p, PotionEffectType.BLINDNESS);
        com.reborn.shinobicore.util.Effets.retirerInfini(p, PotionEffectType.DARKNESS);
        if (ecransMod.remove(id)) {
            if (canal.ecoute(p)) canal.attente(p, false, "");
            if (p.getListeningPluginChannels().contains(DouaneCanal.CANAL_ECRAN)) canal.noir(p, false, "");
        }
        p.clearTitle();
    }

    /* ================================================================ minuteries */

    private void tickRapide() {
        long t = System.currentTimeMillis();
        // Attente neutre : la marque de Guardian aiguille dès qu'elle arrive ; sans preuve au bout du délai, expulsion.
        for (Map.Entry<UUID, Long> e : List.copyOf(attentes.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) { attentes.remove(e.getKey()); enAttente.remove(e.getKey()); continue; }
            Aiguillage.Decision d = Aiguillage.decider(staffEnJeu(p), null, marqueGuardian(p), t - e.getValue(), conf.preuveMs);
            if (d.issue() != Aiguillage.Issue.ATTENDRE) appliquer(p, d, null, e.getValue());
        }
        // Contrôle tardif : une marque « douane » après un aiguillage vers le jeu normal. Le choix d'un personnage est
        // retenu pendant ce temps (choixPermis) et rejoué à la fin du contrôle.
        for (Map.Entry<UUID, Long> e : List.copyOf(surveillances.entrySet())) {
            UUID id = e.getKey();
            Player p = Bukkit.getPlayer(id);
            if (p == null) { surveillances.remove(id); choixDifferes.remove(id); continue; }
            // Staff : jamais la douane (op ou staff porteur d'une marque douane : pas d'expulsion).
            if (candidats.containsKey(id)) { surveillances.remove(id); choixDifferes.remove(id); continue; }
            if (staffEnJeu(p)) { finSurveillance(p); continue; }
            Aiguillage.Portee m = marqueGuardian(p);
            if (Aiguillage.marqueTardiveRestrictive(m)) {
                surveillances.remove(id);
                choixDifferes.remove(id);
                if (!actif) expulser(p, ReglesDouane.EXPULSION_DOUANE_INACTIVE, "marque douane, douane coupée sur ce serveur");
                else {
                    admisJeu.remove(id);
                    expulser(p, ReglesDouane.EXPULSION_REVERIFIER, "marque douane de Guardian après un accès play de l'API");
                }
                continue;
            }
            // Marque « play » (Guardian n'atteste qu'une fois par connexion : elle ne changera plus) ou délai écoulé.
            if (m == Aiguillage.Portee.PLAY || t > e.getValue()) finSurveillance(p);
        }
        // Écrans envoyés avant que le mod ait déclaré ses canaux : on les renvoie dès qu'il écoute.
        for (Map.Entry<UUID, String> e : List.copyOf(ecrans.entrySet())) {
            if (ecransMod.contains(e.getKey())) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) { ecrans.remove(e.getKey()); continue; }
            if (canal.ecoute(p) || p.getListeningPluginChannels().contains(DouaneCanal.CANAL_ECRAN)) envoyerEcran(p, e.getValue());
        }
    }

    private void tickLent() {
        compteurLent++;
        long t = System.currentTimeMillis();
        if (envois != null) envois.tick();
        if (mode != null) mode.verifier();
        if (!actif || monde == null) return;

        // Hall ouvert seulement avec un encadrant (examinateur ou staff) dans le monde.
        boolean o = false;
        for (Player q : monde.getPlayers()) {
            if (encadrant(q)) { o = true; break; }
        }
        if (o != ouvert) basculerHall(o);

        // Plaques : qui est examinateur dans le monde (à chaque changement, et toutes les 10 s).
        List<String> exams = new ArrayList<>();
        for (UUID id : mode.actifs()) {
            Player q = Bukkit.getPlayer(id);
            if (q != null && dansMonde(q)) exams.add(id.toString());
        }
        java.util.Collections.sort(exams);
        String cle = String.join(",", exams);
        if (!cle.equals(dernieresPlaques) || t - plaquesAt > 10_000L) {
            dernieresPlaques = cle;
            plaquesAt = t;
            for (Player q : monde.getPlayers()) if (canal.ecoute(q)) canal.plaques(q, exams);
        }

        // Examinateur sorti du monde ou parti : son candidat revient au hall, en tête.
        for (Bureau b : bureaux.values()) {
            if (b.examinateur == null) continue;
            Player ex = Bukkit.getPlayer(b.examinateur);
            if (ex == null || !dansMonde(ex) || !mode.actif(b.examinateur)) examinateurParti(b.examinateur);
        }
        for (Bureau b : bureaux.values()) {
            if (b.absent != null && !ReglesDouane.reprisePossible(b.absentDepuis, t, conf.repriseMs)) b.absent = null;
        }
        departs.values().removeIf(d -> !ReglesDouane.reprisePossible(d.at(), t, conf.repriseMs));
        refuses.values().removeIf(r -> t > r.jusquA());
        synchroniserSalles();   // filet de sécurité (chaque changement de bureau resynchronise déjà)

        for (Candidat c : List.copyOf(candidats.values())) {
            Player p = Bukkit.getPlayer(c.id);
            if (p == null) continue;
            // Confinement de secours (les déplacements sont déjà contrôlés à chaque pas).
            if (!dansMonde(p) || !zoneDe(c).contient(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ())) {
                teleporterInterne(p, pointDe(c));
            }
            // Hall fermé sans interruption depuis le délai de grâce, et convocation + report-minutes passée : séance
            // reportée. Jamais pour un candidat déjà appelé ou remis en tête (renvoyé, examinateur parti).
            if (!ouvert && c.bureau == null) {
                if (!c.alerteFerme) {
                    c.alerteFerme = true;
                    if (plugin.staffPanel() != null) {
                        plugin.staffPanel().system("Douane : " + c.pseudo + " attend au hall, aucun examinateur en jeu.");
                    }
                }
                FileDouane.Entree fe = file.get(c.id);
                boolean dejaAppele = (fe != null && fe.teteAt() > 0) || !c.questions.isEmpty();
                if (!dejaAppele && ReglesDouane.seanceReportee(c.convocationAt(), fermeDepuis, t, conf.reportMs,
                        conf.reportGraceMs)) {
                    noterRefus(c, ReglesDouane.EXPULSION_REPORT);
                    retirerCandidat(p, c, "séance reportée faute d'examinateur", "report", null, null);
                    Journal.entree(Categorie.JOUEUR, "DOUANE_REPORT").acteur(p).detail("hall fermé, aucun examinateur").ecrire();
                    p.kick(Component.text(ReglesDouane.EXPULSION_REPORT));
                    continue;
                }
            }
            // Place inconnue (aiguillé par Guardian seul) : on relit l'accès toutes les 30 s.
            if ((c.acces == null || c.acces.douane() == null) && t - c.dernierRafraichissement > 30_000L) {
                rafraichirAcces(c);
            }
            envoyerLigne(p, c, compteurLent % 2 == 0);
        }

        if (etatSale ? t - dernierEtat >= 500L : t - dernierEtat >= 5000L) {
            etatSale = false;
            dernierEtat = t;
            canal.etatPourTous();
        }
    }

    /* ================================================================ hall */

    private void basculerHall(boolean o) {
        ouvert = o;
        fermeDepuis = o ? 0L : System.currentTimeMillis();
        for (Candidat c : candidats.values()) {
            Player p = Bukkit.getPlayer(c.id);
            if (p == null || c.bureau != null) continue;
            if (o) ouvrirPour(p, c); else fermerPour(p, c);
        }
        if (plugin.staffPanel() != null) {
            plugin.staffPanel().system(o ? "Douane : hall ouvert (" + file.taille() + " en attente)."
                    : "Douane : hall fermé, plus aucun examinateur ni staff dans le monde de la douane.");
        }
        Journal.entree(Categorie.SYSTEME, o ? "DOUANE_OUVERTE" : "DOUANE_FERMEE").detail(file.taille() + " en attente").ecrire();
        etatSale = true;
    }

    private void fermerPour(Player p, Candidat c) {
        c.ecranFerme = true;
        appliquerEcran(p, ReglesDouane.LIGNE_FERME);
        couperVoix(p);
        envoyerLigne(p, c, true);
    }

    private void ouvrirPour(Player p, Candidat c) {
        c.ecranFerme = false;
        c.alerteFerme = false;
        retirerEcran(p);
        rendreVoix(p);
        envoyerLigne(p, c, true);
    }

    /** Ligne du HUD : au mod quand elle change (et toutes les 10 s) ; sans le mod, barre d'action toutes les 2 s. */
    private void envoyerLigne(Player p, Candidat c, boolean repliDu) {
        String etat, texte;
        int rang = 0;
        if (c.bureau != null) { etat = "bureau"; texte = ReglesDouane.LIGNE_BUREAU; }
        else if (!ouvert) { etat = "ferme"; texte = ReglesDouane.LIGNE_FERME; }
        else { etat = "attente"; rang = file.rang(c.id); texte = ReglesDouane.ligneAttente(true, rang); }
        long t = System.currentTimeMillis();
        if (canal.ecoute(p)) {
            if (!texte.equals(c.derniereLigne) || t - c.ligneAt > 10_000L) {
                canal.ligne(p, etat, texte, rang, file.taille());
                c.derniereLigne = texte;
                c.ligneAt = t;
            }
        } else if (repliDu) {
            // Sans le mod : barre d'action ; hall fermé sans écran noir du mod, le titre est renouvelé.
            if (!c.ecranFerme) p.sendActionBar(Component.text(texte, NamedTextColor.GRAY));
            else if (!ecransMod.contains(c.id)) envoyerEcran(p, texte);
        }
    }

    private void envoyerLignes() {
        for (Candidat c : candidats.values()) {
            Player p = Bukkit.getPlayer(c.id);
            if (p != null) envoyerLigne(p, c, false);
        }
    }

    private Zone zoneDe(Candidat c) {
        Bureau b = c.bureau == null ? null : bureaux.get(c.bureau);
        return b == null ? conf.zoneHall : b.conf.zone();
    }

    private Location pointDe(Candidat c) {
        Bureau b = c.bureau == null ? null : bureaux.get(c.bureau);
        return (b == null ? conf.hall : b.conf.candidat()).en(monde);
    }

    /** Point de retour du confinement pour un pas hors zone, ou null si le pas est permis. */
    Location confiner(Player p, Location vers) {
        Candidat c = candidats.get(p.getUniqueId());
        if (c == null || monde == null) return null;
        if (vers.getWorld() == null || !vers.getWorld().equals(monde)) return pointDe(c);
        return zoneDe(c).contient(vers.getX(), vers.getY(), vers.getZ()) ? null : pointDe(c);
    }

    /** Immobile : attente d'une preuve, transition, ou candidat au hall fermé. */
    boolean immobile(UUID id) {
        if (fige(id)) return true;
        Candidat c = candidats.get(id);
        return c != null && c.bureau == null && c.ecranFerme;
    }

    /** Hall fermé pour ce candidat : tchat coupé. */
    boolean tchatCoupe(UUID id) {
        if (fige(id)) return true;
        Candidat c = candidats.get(id);
        return c != null && c.bureau == null && !ouvert;
    }

    boolean teleportInterne(UUID id) { return teleportsInternes.contains(id); }

    void teleporterInterne(Player p, Location l) {
        teleportsInternes.add(p.getUniqueId());
        try { p.teleport(l); } finally { teleportsInternes.remove(p.getUniqueId()); }
    }

    Location pointHall() { return monde == null ? null : conf.hall.en(monde); }

    /* ================================================================ voix (Plasmo, par commandes console) */

    private void couperVoix(Player p) {
        // Jamais de coupure sans la commande qui rend la voix (douane fermée avec une seule des deux commandes, ou
        // non-coupee-acceptee) : le joueur resterait muet dans Plasmo.
        if (!conf.voixCoupable() || !muets.add(p.getUniqueId())) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), conf.voixCouper.replace("{joueur}", p.getName()));
    }

    private void rendreVoix(Player p) {
        if (!muets.remove(p.getUniqueId()) || conf.voixRendre.isEmpty()) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), conf.voixRendre.replace("{joueur}", p.getName()));
    }

    /* ================================================================ admission */

    private void admettre(Player p, DouaneClient.Acces acces) {
        UUID id = p.getUniqueId();
        long t = System.currentTimeMillis();
        Candidat c = new Candidat(id, p.getName());
        c.acces = acces;
        Depart dep = departs.remove(id);
        boolean reprise = dep != null && ReglesDouane.reprisePossible(dep.at(), t, conf.repriseMs);
        c.arriveeAt = reprise ? dep.arriveeAt() : t;
        // Reprise : les questions déjà tirées ne retombent pas (§3.7).
        if (reprise) c.questions.addAll(dep.questions());
        candidats.put(id, c);

        // Sans personnage : aventure, aucun dégât (porte des dégâts + écouteur), faim figée, inventaire vanilla vide,
        // skin Minecraft et pseudo sur la plaque.
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().clear();
        p.setFoodLevel(20);
        p.setSaturation(5f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) p.setHealth(max.getValue());
        CharacterDisplay.reset(p);

        Bureau retour = null;
        for (Bureau b : bureaux.values()) {
            if (id.equals(b.absent) && b.examinateur != null && Bukkit.getPlayer(b.examinateur) != null
                    && ReglesDouane.reprisePossible(b.absentDepuis, t, conf.repriseMs)) { retour = b; break; }
        }
        if (retour != null) {
            retour.absent = null;
            retour.candidat = id;
            c.bureau = retour.conf.id();
            teleporterInterne(p, retour.conf.candidat().en(monde));
            Player ex = Bukkit.getPlayer(retour.examinateur);
            if (ex != null) dire(ex, p.getName() + " est de retour au bureau.", false);
        } else {
            // Bureau perdu (examinateur parti) : il ne reste pas réservé à son nom.
            for (Bureau b : bureaux.values()) if (id.equals(b.absent)) b.vider();
            // Reprise d'un candidat remis en tête : il retrouve la tête, pas son rang de convocation.
            file.ajouter(entree(c, reprise ? dep.teteAt() : 0L));
            teleporterInterne(p, conf.hall.en(monde));
            if (ouvert) ouvrirPour(p, c); else fermerPour(p, c);
            if (plugin.staffPanel() != null) {
                plugin.staffPanel().onAlerteDouane(id, "Candidat arrivé à la douane · " + p.getName(), conf.hall.en(monde));
            }
        }
        synchroniserSalles();
        presence(id, "arrivee", null);
        Journal.entree(Categorie.JOUEUR, "DOUANE_ARRIVEE").acteur(p).lieu(p.getLocation())
                .detail((reprise ? "retour après une coupure · " : "") + (c.reservationId() == null ? "place inconnue" : "place " + c.reservationId()))
                .ecrire();
        if (c.acces == null || c.acces.douane() == null) rafraichirAcces(c);
        plaquesAt = 0L;   // les plaques partent au prochain passage (le mod du nouvel arrivé écoute après quelques ticks)
        etatSale = true;
        envoyerLignes();
    }

    private FileDouane.Entree entree(Candidat c, long teteAt) {
        DouaneClient.Place pl = c.acces == null ? null : c.acces.douane();
        return new FileDouane.Entree(c.id, c.pseudo, pl == null ? 0L : pl.convocationAt(), c.arriveeAt, teteAt,
                pl != null && pl.deuxiemePassage(), pl == null || pl.village() == null ? "" : pl.village());
    }

    /** Relit l'accès (place, heure de convocation) en arrière-plan. */
    private void rafraichirAcces(Candidat c) {
        c.dernierRafraichissement = System.currentTimeMillis();
        UUID id = c.id;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DouaneClient.Acces a = client.acces(id, 3000L);
            if (a == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                derniersAcces.put(id, a);
                Candidat cc = candidats.get(id);
                if (cc == null || a.douane() == null) return;
                cc.acces = a;
                FileDouane.Entree e = file.get(id);
                if (e != null) file.ajouter(entree(cc, e.teteAt()));
                etatSale = true;
                envoyerLignes();
            });
        });
    }

    /** Présence (facultative côté API) : arrivée, appel, départ. Sans suite en cas d'échec. */
    private void presence(UUID id, String evenement, UUID examinateur) {
        Candidat c = candidats.get(id);
        DouaneClient.Acces a = c != null ? c.acces : derniersAcces.get(id);
        String reservation = a != null && a.douane() != null ? a.douane().reservationId() : null;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DouaneClient.Reponse r = client.presence(id, evenement, reservation, examinateur);
            if (!r.ok() && r.code() != 0) {
                plugin.getLogger().fine("[douane] présence " + evenement + " " + id + " → " + r.code() + " " + r.message());
            }
        });
    }

    /* ================================================================ départs */

    /** Déconnexion (priorité basse : avant la sauvegarde du personnage et de la position). */
    void quitter(Player p) {
        UUID id = p.getUniqueId();
        long t = System.currentTimeMillis();
        preuves.remove(id);
        arrivants.remove(id);
        attentes.remove(id);
        enAttente.remove(id);
        surveillances.remove(id);
        choixDifferes.remove(id);
        admisJeu.remove(id);
        // Parti entre la décision (ou le retrait) et la sortie, écran Bienvenue compris : l'Aventure de la douane ne
        // reste pas dans ses données (aucun code de personnage ne remet le mode de jeu).
        if (transitions.remove(id)) p.setGameMode(Bukkit.getDefaultGameMode());
        if (ecrans.remove(id) != null) {
            // Écran d'attente : Cécité et Obscurité infinies ne restent pas dans les données du joueur.
            com.reborn.shinobicore.util.Effets.retirerInfini(p, PotionEffectType.BLINDNESS);
            com.reborn.shinobicore.util.Effets.retirerInfini(p, PotionEffectType.DARKNESS);
        }
        ecransMod.remove(id);
        // Voix rendue à tout joueur coupé (candidat, mais aussi attente neutre) : personne ne reste muet.
        rendreVoix(p);
        if (mode != null && mode.actif(id)) mode.sortir(p, true, true);
        Location r = retours.remove(id);
        if (r != null && dansMonde(p)) p.teleport(r);
        Candidat c = candidats.remove(id);
        if (c != null) {
            p.setGameMode(Bukkit.getDefaultGameMode());
            com.reborn.shinobicore.util.Effets.retirerInfini(p, PotionEffectType.BLINDNESS);
            com.reborn.shinobicore.util.Effets.retirerInfini(p, PotionEffectType.DARKNESS);
            Bureau b = bureauAvec(id);
            long teteAt = 0L;
            if (b != null) {
                b.candidat = null;
                b.absent = id;
                b.absentDepuis = t;
                Player ex = b.examinateur == null ? null : Bukkit.getPlayer(b.examinateur);
                if (ex != null) dire(ex, c.pseudo + " s'est déconnecté (retour possible pendant "
                        + conf.repriseMs / 60_000L + " min).", true);
            } else {
                FileDouane.Entree fe = file.retirer(id);
                if (fe != null) teteAt = fe.teteAt();
            }
            departs.put(id, new Depart(t, c.arriveeAt, teteAt, Set.copyOf(c.questions)));
            if (!envoiEnCours(id)) presenceDepart(id, c);
            if (plugin.staffPanel() != null) plugin.staffPanel().finAlerteDouane(id);
            Journal.entree(Categorie.JOUEUR, "DOUANE_DEPART").acteur(p).detail(b != null ? "déconnecté au bureau " + b.conf.id()
                    : "déconnecté au hall").ecrire();
            etatSale = true;
            envoyerLignes();
        }
        synchroniserSalles();
        derniersAcces.remove(id);
    }

    private void presenceDepart(UUID id, Candidat c) {
        String reservation = c.reservationId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> client.presence(id, "depart", reservation, null));
    }

    /**
     * Départ sans décision (§3.10) : {@code depart}, puis l'événement précis ({@code retrait} avec motif et
     * examinateur, {@code report}), dans cet ordre, pour que l'API annule la place avec son motif. Une API qui ne
     * connaît pas encore cet événement le refuse (400, sans suite) : la place reste alors BOOKED et le refus local
     * (noterRefus) tient la porte.
     */
    private void presenceSortie(UUID id, Candidat c, String evenement, String motif, UUID examinateur) {
        String reservation = c.reservationId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            client.presence(id, "depart", reservation, null);
            DouaneClient.Reponse r = client.presence(id, evenement, reservation, examinateur, motif);
            if (!r.ok() && r.code() != 0) {
                plugin.getLogger().fine("[douane] présence " + evenement + " " + id + " → " + r.code() + " " + r.message());
            }
        });
    }

    /**
     * Retire un candidat de la douane sans décision (report, retrait par l'examinateur) ; l'appelant l'expulse tout
     * de suite. {@code evenement} : {@code retrait} (motif, examinateur) ou {@code report}.
     */
    private void retirerCandidat(Player p, Candidat c, String raison, String evenement, String motif, UUID examinateur) {
        UUID id = c.id;
        candidats.remove(id);
        file.retirer(id);
        Bureau b = bureauAvec(id);
        if (b != null) b.vider();
        departs.remove(id);
        transitions.add(id);
        rendreVoix(p);
        p.setGameMode(Bukkit.getDefaultGameMode());   // l'Aventure de la douane ne reste pas sur le compte
        presenceSortie(id, c, evenement, motif, examinateur);
        if (plugin.staffPanel() != null) plugin.staffPanel().finAlerteDouane(id);
        plugin.getLogger().info("[douane] " + c.pseudo + " retiré : " + raison + ".");
        synchroniserSalles();
        etatSale = true;
        envoyerLignes();
    }

    /** Un examinateur quitte son bureau (déconnexion, sortie du mode ou du monde) : son candidat revient en tête. */
    void examinateurParti(UUID ex) {
        for (Bureau b : bureaux.values()) {
            if (!ex.equals(b.examinateur)) continue;
            b.examinateur = null;
            if (b.candidat != null) {
                Candidat c = candidats.get(b.candidat);
                if (c != null) {
                    EnvoisDecision.Envoi en = envois.pour(c.id);
                    if (en != null && en.tentatives() == 0) {
                        // Premier essai en vol (3 s au plus) : le candidat attend la réponse au bureau. Succès : promu
                        // ou expulsé ; refus : renvoyé au hall (echec) ; pas de réponse : résultat différé (report).
                        etatSale = true;
                        continue;
                    }
                    if (en != null) {
                        resultatDiffere(c.id);
                    } else {
                        renvoyerAuHall(c, true);
                        Player p = Bukkit.getPlayer(c.id);
                        if (p != null) dire(p, "L'examinateur a dû partir : tu reprends la tête de la file.", false);
                    }
                }
            }
            b.vider();
            etatSale = true;
        }
        synchroniserSalles();
    }

    private void renvoyerAuHall(Candidat c, boolean tete) {
        Bureau b = bureauAvec(c.id);
        if (b != null) { b.candidat = null; b.question = null; b.montree = false; b.dossier = null; b.notes = ""; }
        c.bureau = null;
        if (tete) file.mettreEnTete(entree(c, 0L), System.currentTimeMillis());
        else file.ajouter(entree(c, 0L));
        synchroniserSalles();
        Player p = Bukkit.getPlayer(c.id);
        if (p != null) {
            canal.question(p, "");
            teleporterInterne(p, conf.hall.en(monde));
            if (ouvert) ouvrirPour(p, c); else fermerPour(p, c);
        }
        etatSale = true;
        envoyerLignes();
    }

    Bureau bureauAvec(UUID candidat) {
        for (Bureau b : bureaux.values()) if (candidat.equals(b.candidat) || candidat.equals(b.absent)) return b;
        return null;
    }

    Bureau bureauDe(UUID examinateur) {
        for (Bureau b : bureaux.values()) if (examinateur.equals(b.examinateur)) return b;
        return null;
    }

    private Bureau bureauOu(Player ex) {
        Location l = ex.getLocation();
        for (Bureau b : bureaux.values()) {
            if (dansMonde(ex) && b.conf.zone().contient(l.getX(), l.getY(), l.getZ())) return b;
        }
        return null;
    }

    /* ================================================================ actions de l'examinateur */

    /** Message à un staff : notification du mod s'il écoute le canal, sinon tchat. */
    void dire(Player p, String msg, boolean erreur) {
        if (canal.ecoute(p)) canal.toast(p, msg, erreur);
        else p.sendMessage(Component.text(msg, erreur ? NamedTextColor.RED : NamedTextColor.GOLD));
    }

    /** Droits d'une action d'examinateur ; {@code enMode} exige le mode examinateur. Message si refusé. */
    private boolean exiger(Player ex, boolean enMode) {
        if (!actif || monde == null) { dire(ex, "La douane n'est pas active sur ce serveur.", true); return false; }
        if (!peutExaminer(ex)) {
            dire(ex, "Réservé aux examinateurs (grade Helper au minimum et permission " + PERMISSION + ").", true);
            return false;
        }
        if (enMode && !mode.actif(ex.getUniqueId())) { dire(ex, "Passe d'abord en mode examinateur (/modeexaminateur).", true); return false; }
        return true;
    }

    /**
     * État bloquant qui interdit d'entrer à la douane (mode examinateur, {@code /douane aller}) : la téléportation au
     * hall serait une sortie de scène, comme celle déjà fermée pour le mode staff (ETATS_JOUEUR §11 n° 6). Reprend le
     * refus du mode staff (KO, mort, porté, tenu, matrice {@code MODE_STAFF} : prison, exécution) et le gel. null =
     * permis.
     */
    String refusEtat(Player p) {
        UUID id = p.getUniqueId();
        var outils = plugin.outilsStaff();
        if (outils != null) {
            String refus = outils.mode().refusEntree(p);
            if (refus == null && outils.gels().gele(id)) refus = "tu es gelé par le staff.";
            return refus;
        }
        if ((plugin.ko() != null && plugin.ko().isKo(id)) || (plugin.deathScene() != null && plugin.deathScene().isDead(id))
                || (plugin.porter() != null && plugin.porter().carrierOf(id) != null)) {
            return "ton personnage est à terre, mort ou porté.";
        }
        return null;
    }

    /** {@code /douane aller} : au hall sans passer par la file (examinateurs et staff). */
    void aller(Player p) {
        if (!actif || monde == null) { dire(p, "La douane n'est pas active sur ce serveur.", true); return; }
        if (!peutExaminer(p) && StaffGrades.of(p) < StaffGrades.HELPER) { dire(p, "Réservé aux examinateurs et au staff.", true); return; }
        String refus = refusEtat(p);
        if (refus != null) { dire(p, "Accès à la douane refusé : " + refus, true); return; }
        if (!dansMonde(p)) retours.put(p.getUniqueId(), p.getLocation().clone());
        p.teleport(conf.hall.en(monde));
        Journal.entree(Categorie.STAFF, "DOUANE_ALLER").acteur(p).lieu(p.getLocation()).ecrire();
    }

    /** {@code /douane quitter} : retour au point d'avant {@code /douane aller}. */
    void quitterDouane(Player p) {
        if (mode.actif(p.getUniqueId())) { mode.sortir(p, true, true); return; }
        Location l = retours.remove(p.getUniqueId());
        if (l == null) { dire(p, "Aucun point de retour mémorisé.", true); return; }
        p.teleport(l);
    }

    /** Mémorise le point de retour d'un staff qui entre dans le monde de la douane (mode examinateur). */
    Location retourDe(UUID id) { return retours.get(id); }

    void oublierRetour(UUID id) { retours.remove(id); }

    /** Appelle le suivant, ou un candidat précis (pseudo ou UUID). */
    void appeler(Player ex, String cible) {
        if (!exiger(ex, true)) return;
        UUID eid = ex.getUniqueId();
        Bureau mien = bureauDe(eid);
        if (mien != null && mien.candidat != null) {
            Candidat c = candidats.get(mien.candidat);
            EnvoisDecision.Envoi en = envois.pour(mien.candidat);
            if (en == null) {
                dire(ex, "Termine d'abord avec " + (c == null ? "ton candidat" : c.pseudo) + " (décision ou /douane renvoyer).", true);
                return;
            }
            if (en.tentatives() == 0) {
                // Premier essai en vol (3 s au plus) : la réponse décide de la suite du candidat.
                dire(ex, "Décision en cours d'envoi pour " + (c == null ? "ton candidat" : c.pseudo) + " : patiente quelques secondes.", true);
                return;
            }
            // Décision en attente d'envoi : le candidat n'attend pas l'API au bureau, qui se libère.
            resultatDiffere(mien.candidat);
        }
        FileDouane.Entree e;
        if (cible == null || cible.isBlank() || cible.equalsIgnoreCase("suivant")) {
            e = file.premier();
        } else {
            UUID cid = resoudre(cible);
            e = cid == null ? null : file.get(cid);
            if (e == null) { dire(ex, "« " + cible + " » n'attend pas au hall.", true); return; }
        }
        if (e == null) { dire(ex, "Personne n'attend au hall.", true); return; }
        Candidat c = candidats.get(e.id());
        Player p = Bukkit.getPlayer(e.id());
        if (c == null || p == null) { file.retirer(e.id()); dire(ex, "Ce candidat n'est plus là.", true); return; }
        // Bureau (§3.6) : celui où il se trouve (libre, ou déjà le sien), sinon le sien, sinon le premier libre.
        Bureau ici = bureauOu(ex);
        Bureau b = ici != null && (ici == mien || ici.libre()) ? ici : mien;
        if (b == null) for (Bureau x : bureaux.values()) if (x.libre()) { b = x; break; }
        if (b == null) { dire(ex, "Aucun bureau libre.", true); return; }
        if (mien != null && mien != b) {
            // Il change de bureau : l'ancien se libère (un candidat déconnecté qui y était attendu repassera au hall).
            mien.examinateur = null;
        }
        if (b.absent != null) {
            // Le candidat déconnecté perd son bureau : à son retour, il repasse en fin de file (arrivée remise à
            // maintenant), mais ses questions déjà tirées restent écartées.
            Depart d = departs.get(b.absent);
            if (d != null) departs.put(b.absent, new Depart(d.at(), System.currentTimeMillis(), 0L, d.questions()));
            b.absent = null;
        }
        file.retirer(c.id);
        b.vider();
        b.examinateur = eid;
        b.candidat = c.id;
        b.depuis = System.currentTimeMillis();
        c.bureau = b.conf.id();
        synchroniserSalles();
        rendreVoix(p);
        retirerEcran(p);
        canal.appel(p);
        final Bureau fb = b;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !c.id.equals(fb.candidat)) return;
            teleporterInterne(p, fb.conf.candidat().en(monde));
            envoyerLigne(p, c, true);
        }, 10L);
        if (!fb.conf.zone().contient(ex.getLocation().getX(), ex.getLocation().getY(), ex.getLocation().getZ())) {
            ex.teleport(fb.conf.examinateur().en(monde));
        }
        presence(c.id, "appel", eid);
        if (plugin.staffPanel() != null) plugin.staffPanel().finAlerteDouane(c.id);
        Journal.entree(Categorie.STAFF, "DOUANE_APPEL").acteur(ex).cible(p).detail("bureau " + fb.conf.id()
                + (e.deuxiemePassage() ? " · 2ᵉ passage" : "")).lieu(fb.conf.candidat().en(monde)).ecrire();
        dire(ex, "Tu appelles " + c.pseudo + " au bureau " + fb.conf.id() + ".", false);
        chargerDossier(ex, c.id);
        etatSale = true;
        envoyerLignes();
    }

    /** Renvoie le candidat du bureau au hall, en tête de file. */
    void renvoyer(Player ex) {
        if (!exiger(ex, true)) return;
        Bureau b = bureauDe(ex.getUniqueId());
        if (b == null || b.candidat == null) { dire(ex, "Aucun candidat à ton bureau.", true); return; }
        if (envoiEnCours(b.candidat)) { dire(ex, "Une décision est en cours d'envoi pour ce candidat.", true); return; }
        Candidat c = candidats.get(b.candidat);
        if (c == null) { b.vider(); return; }
        renvoyerAuHall(c, true);
        Player p = Bukkit.getPlayer(c.id);
        if (p != null) dire(p, "Tu retournes au hall, en tête de la file.", false);
        Journal.entree(Categorie.STAFF, "DOUANE_RENVOI").acteur(ex).cible(c.id).detail("bureau " + b.conf.id()).ecrire();
        dire(ex, c.pseudo + " retourne au hall, en tête de file.", false);
    }

    /**
     * « Retirer de la douane » : expulsion avec motif, sans décision. Refus local pour cette place jusqu'à la fin de sa
     * fenêtre d'entrée : il ne revient pas au hall dans la minute ; une autre réservation passe.
     */
    void retirer(Player staff, String cible, String motif) {
        if (!exiger(staff, false)) return;
        if (motif == null || motif.isBlank()) { dire(staff, "Motif obligatoire : /douane retirer <joueur> <motif>.", true); return; }
        UUID id = resoudre(cible);
        Candidat c = id == null ? null : candidats.get(id);
        Player p = id == null ? null : Bukkit.getPlayer(id);
        if (c == null || p == null) { dire(staff, "« " + cible + " » n'est pas à la douane.", true); return; }
        if (envoiEnCours(id)) { dire(staff, "Une décision est en cours d'envoi pour ce candidat.", true); return; }
        String m = motif.strip();
        if (m.length() > ReglesDouane.MOTIF_MAX) m = m.substring(0, ReglesDouane.MOTIF_MAX);
        String texte = ReglesDouane.PREFIXE_RETRAIT + m;
        noterRefus(c, texte);
        retirerCandidat(p, c, "retiré par " + staff.getName(), "retrait", m, staff.getUniqueId());
        Journal.entree(Categorie.STAFF, "DOUANE_RETRAIT").acteur(staff).cible(p).detail(m).ecrire();
        if (plugin.staffPanel() != null) plugin.staffPanel().system("Douane : " + c.pseudo + " retiré par " + staff.getName() + " (" + m + ").");
        p.kick(Component.text(texte));
    }

    /** Dossier résumé d'un candidat (celui du bureau, ou un candidat de la file). */
    void dossier(Player ex, String cible) {
        if (!exiger(ex, false)) return;
        UUID id;
        if (cible == null || cible.isBlank()) {
            Bureau b = bureauDe(ex.getUniqueId());
            id = b == null ? null : (b.candidat != null ? b.candidat : b.absent);
            if (id == null) { dire(ex, "Aucun candidat à ton bureau : /douane dossier <joueur>.", true); return; }
        } else {
            id = resoudre(cible);
            if (id == null || !candidats.containsKey(id)) { dire(ex, "« " + cible + " » n'est pas à la douane.", true); return; }
        }
        chargerDossier(ex, id);
    }

    private void chargerDossier(Player ex, UUID candidat) {
        UUID eid = ex.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DouaneClient.Reponse r = client.dossier(candidat, eid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player e = Bukkit.getPlayer(eid);
                if (r.ok()) {
                    Bureau b = bureauAvec(candidat);
                    if (b != null && eid.equals(b.examinateur)) b.dossier = r.corps();
                    Journal.entree(Categorie.STAFF, "DOUANE_DOSSIER").acteur(eid).cible(candidat).detail("dossier lu en jeu").ecrire();
                    if (e != null) canal.dossier(e, candidat, r.corps(), null);
                } else if (e != null) {
                    String msg = r.code() == 0 ? "API injoignable : dossier indisponible." : "Dossier refusé : " + r.message();
                    canal.dossier(e, candidat, null, msg);
                }
            });
        });
    }

    /** Tire une question pour le candidat du bureau (montrée à l'examinateur seul). */
    void question(Player ex) {
        if (!exiger(ex, true)) return;
        Bureau b = bureauDe(ex.getUniqueId());
        Candidat c = b == null || b.candidat == null ? null : candidats.get(b.candidat);
        if (c == null) { dire(ex, "Aucun candidat à ton bureau.", true); return; }
        String q = tirage.tirer(c.questions, hasard);
        if (q == null) { dire(ex, "La banque de questions est vide (douane.questions).", true); return; }
        c.questions.add(q);
        b.question = q;
        b.montree = false;
        canal.tirage(ex, q, false, tirage.reste(c.questions));
        Journal.entree(Categorie.STAFF, "DOUANE_QUESTION").acteur(ex).cible(c.id).detail(q).ecrire();
    }

    /** Montre la question tirée au candidat (une ligne en haut de son écran). */
    void montrer(Player ex, boolean afficher) {
        if (!exiger(ex, true)) return;
        Bureau b = bureauDe(ex.getUniqueId());
        Player p = b == null || b.candidat == null ? null : Bukkit.getPlayer(b.candidat);
        if (p == null) { dire(ex, "Aucun candidat à ton bureau.", true); return; }
        if (!afficher) { canal.question(p, ""); b.montree = false; return; }
        if (b.question == null) { dire(ex, "Tire d'abord une question (/douane question).", true); return; }
        String q = b.question;
        canal.question(p, q);
        b.montree = true;
        canal.tirage(ex, q, true, -1);
        Journal.entree(Categorie.STAFF, "DOUANE_QUESTION_MONTREE").acteur(ex).cible(p).detail(q).ecrire();
    }

    /** Notes privées jointes à la prochaine décision ({@code -} efface). */
    void notes(Player ex, String texte) {
        if (!exiger(ex, true)) return;
        Bureau b = bureauDe(ex.getUniqueId());
        if (b == null || (b.candidat == null && b.absent == null)) { dire(ex, "Aucun candidat à ton bureau.", true); return; }
        String t = texte == null || texte.strip().equals("-") ? "" : texte.strip();
        if (t.length() > ReglesDouane.NOTES_MAX) { dire(ex, "Notes trop longues (" + ReglesDouane.NOTES_MAX + " caractères au plus).", true); return; }
        b.notes = t;
        dire(ex, t.isEmpty() ? "Notes privées effacées." : "Notes privées enregistrées (" + t.length() + " caractères), jamais montrées au candidat.", false);
    }

    /** Décision : envoyée à l'API (file locale si elle ne répond pas). Le joueur n'est promu que sur un 200. */
    void decider(Player ex, String resultat, String motif, String notes) {
        if (!exiger(ex, true)) return;
        Bureau b = bureauDe(ex.getUniqueId());
        UUID cid = b == null ? null : (b.candidat != null ? b.candidat : b.absent);
        if (cid == null) { dire(ex, "Aucun candidat à ton bureau.", true); return; }
        if (envoiEnCours(cid)) { dire(ex, "Une décision est déjà en cours d'envoi pour ce candidat.", true); return; }
        String n = notes != null ? notes : b.notes;
        String erreur = ReglesDouane.verifierDecision(resultat, motif, n);
        if (erreur != null) { dire(ex, erreur, true); return; }
        Candidat c = candidats.get(cid);
        DouaneClient.Acces a = c != null ? c.acces : derniersAcces.get(cid);
        String reservation = a != null && a.douane() != null ? a.douane().reservationId() : null;
        if ((reservation == null || reservation.isBlank()) && b.dossier != null && b.dossier.has("passage")
                && b.dossier.get("passage").isJsonObject()) {
            reservation = DouaneClient.str(b.dossier.getAsJsonObject("passage"), "reservationId");
        }
        if (reservation == null || reservation.isBlank()) {
            dire(ex, "Place en cours inconnue : le dossier se recharge, recommence dans un instant.", true);
            chargerDossier(ex, cid);
            return;
        }
        JsonObject corps = new JsonObject();
        corps.addProperty("mcUuidCandidat", cid.toString());
        corps.addProperty("mcUuidExaminateur", ex.getUniqueId().toString());
        corps.addProperty("reservationId", reservation);
        corps.addProperty("resultat", resultat);
        if (motif != null && !motif.isBlank()) corps.addProperty("motif", motif.strip());
        if (n != null && !n.isBlank()) corps.addProperty("notesPrivees", n.strip());
        String pseudo = c != null ? c.pseudo : Bukkit.getOfflinePlayer(cid).getName();
        envois.ajouter(cid, pseudo == null ? cid.toString() : pseudo, ex.getUniqueId(), resultat, reservation, corps.toString());
        canal.envoi(ex, "en_cours", resultat, "Décision « " + ReglesDouane.libelle(resultat) + " » envoyée…");
        Journal.entree(Categorie.STAFF, "DOUANE_DECISION").acteur(ex).cible(cid)
                .detail(ReglesDouane.libelle(resultat) + (motif == null || motif.isBlank() ? "" : " · motif : " + motif.strip())
                        + (n == null || n.isBlank() ? "" : " · notes privées jointes")).ecrire();
        etatSale = true;
    }

    /** Pseudo d'un joueur en ligne, ou UUID. */
    private static UUID resoudre(String cible) {
        if (cible == null || cible.isBlank()) return null;
        Player p = Bukkit.getPlayerExact(cible.strip());
        if (p != null) return p.getUniqueId();
        try { return UUID.fromString(cible.strip()); } catch (IllegalArgumentException e) { return null; }
    }

    /* ================================================================ issue d'une décision */

    private final class SuiteDecision implements EnvoisDecision.Suite {
        @Override
        public void succes(EnvoisDecision.Envoi e, JsonObject r) {
            boolean deja = DouaneClient.bool(r, "dejaTraitee");
            String phase = DouaneClient.str(r, "phase");
            String lib = ReglesDouane.libelle(e.resultat());
            Journal.entree(Categorie.STAFF, "DOUANE_DECISION_OK").acteur(e.examinateur()).cible(e.candidat())
                    .detail(lib + (phase == null ? "" : " · phase " + phase) + (deja ? " · déjà enregistrée" : "")).ecrire();
            Player ex = Bukkit.getPlayer(e.examinateur());
            if (ex != null) canal.envoi(ex, "ok", e.resultat(), "Décision enregistrée : " + lib + " (" + e.pseudoCandidat() + ").");
            if (plugin.staffPanel() != null) {
                String exNom = ex != null ? ex.getName() : String.valueOf(Bukkit.getOfflinePlayer(e.examinateur()).getName());
                plugin.staffPanel().system("Douane : " + e.pseudoCandidat() + " · " + lib + " (par " + exNom + ").");
            }
            Bureau b = bureauAvec(e.candidat());
            if (b != null) b.vider();
            synchroniserSalles();
            departs.remove(e.candidat());
            Player p = Bukkit.getPlayer(e.candidat());
            if (p != null && candidats.containsKey(e.candidat())) {
                if (ReglesDouane.APPROUVE.equals(e.resultat())) accepter(p);
                else negatif(p, e.resultat());
            } else {
                file.retirer(e.candidat());
            }
            etatSale = true;
            envoyerLignes();
        }

        @Override
        public void echec(EnvoisDecision.Envoi e, int code, String message) {
            Journal.entree(Categorie.STAFF, "DOUANE_DECISION_REFUSEE").acteur(e.examinateur()).cible(e.candidat())
                    .detail(ReglesDouane.libelle(e.resultat()) + " · " + (code == 0 ? "" : "HTTP " + code + " · ") + message).ecrire();
            Player ex = Bukkit.getPlayer(e.examinateur());
            if (ex != null) canal.envoi(ex, "erreur", e.resultat(), "Décision refusée : " + message);
            if (plugin.staffPanel() != null) {
                plugin.staffPanel().system("Douane : décision pour " + e.pseudoCandidat() + " refusée par l'API (" + message + ").");
            }
            // L'examinateur est parti pendant l'envoi : le candidat ne reste pas seul au bureau.
            Bureau b = bureauAvec(e.candidat());
            Candidat c = candidats.get(e.candidat());
            if (b != null && b.examinateur == null && c != null && e.candidat().equals(b.candidat)) renvoyerAuHall(c, true);
            etatSale = true;
        }

        @Override
        public void report(EnvoisDecision.Envoi e, String message) {
            Player ex = Bukkit.getPlayer(e.examinateur());
            if (ex != null) canal.envoi(ex, "attente", e.resultat(), "Décision en attente d'envoi (" + message
                    + "). Nouvel essai dans " + Math.max(1, (e.prochain() - System.currentTimeMillis()) / 1000L) + " s.");
            // Dès le premier report, le candidat n'attend pas l'API au bureau (§3.9, seconde branche) : il sort avec
            // « Résultat sous peu dans ton launcher » et le bureau se libère. La file d'envoi garde la décision.
            if (candidats.containsKey(e.candidat()) || bureauAvec(e.candidat()) != null) {
                resultatDiffere(e.candidat());
                if (ex != null) dire(ex, e.pseudoCandidat() + " quitte la douane : le résultat lui parviendra dans son"
                        + " launcher dès l'envoi. Ton bureau est libre.", false);
            }
            etatSale = true;
        }
    }

    /**
     * Décision en attente d'envoi (API muette) : le candidat quitte la douane avec « Résultat sous peu dans ton
     * launcher » (§3.9) et son bureau se libère, sans toucher à la file d'envoi ({@link SuiteDecision#succes} gère le
     * candidat parti). Sans ça, le bureau resterait pris jusqu'à {@code abandon-heures}. S'il revient avant l'envoi,
     * l'aiguillage le refuse avec le même texte.
     */
    private void resultatDiffere(UUID id) {
        Bureau b = bureauAvec(id);
        if (b != null) {
            if (id.equals(b.candidat)) b.candidat = null;
            if (id.equals(b.absent)) b.absent = null;
            b.question = null;
            b.montree = false;
            b.dossier = null;
            b.notes = "";
        }
        file.retirer(id);
        departs.remove(id);
        Candidat c = candidats.remove(id);
        synchroniserSalles();
        etatSale = true;
        if (c == null) return;
        if (plugin.staffPanel() != null) plugin.staffPanel().finAlerteDouane(id);
        envoyerLignes();
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        transitions.add(id);
        retirerEcran(p);
        rendreVoix(p);
        canal.question(p, "");
        canal.resultat(p, ReglesDouane.RESULTAT_DIFFERE, ReglesDouane.TEXTE_RESULTAT_DIFFERE, conf.expulsionTicks / 20);
        Journal.entree(Categorie.JOUEUR, "DOUANE_RESULTAT_DIFFERE").acteur(p).detail("décision en attente d'envoi").ecrire();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            transitions.remove(id);
            if (!p.isOnline()) return;
            p.setGameMode(Bukkit.getDefaultGameMode());
            p.kick(Component.text(ReglesDouane.TEXTE_RESULTAT_DIFFERE));
        }, Math.max(60L, conf.expulsionTicks));
    }

    /** Acceptée : « Bienvenue sur Reborn », marque douane effacée, puis sélection et création normales. */
    private void accepter(Player p) {
        UUID id = p.getUniqueId();
        candidats.remove(id);
        file.retirer(id);
        transitions.add(id);
        retirerEcran(p);
        rendreVoix(p);
        effacerMarqueGuardian(p);
        admisJeu.add(id);
        if (plugin.staffPanel() != null) plugin.staffPanel().finAlerteDouane(id);
        canal.bienvenue(p, conf.bienvenueTicks / 20);
        Journal.entree(Categorie.JOUEUR, "DOUANE_ACCEPTE").acteur(p).ecrire();
        // La candidature en cache date d'avant l'oral : on la relit avant d'ouvrir la création.
        if (plugin.characterSelect() != null) plugin.characterSelect().oublierCandidature(id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DouaneClient.Acces a = client.acces(id, 3000L);
            if (a == null || a.candidature() == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.characterSelect() != null && Bukkit.getPlayer(id) != null) {
                    plugin.characterSelect().definirCandidature(id, a.candidature());
                }
            });
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            transitions.remove(id);
            if (!p.isOnline()) return;
            canal.fin(p);
            p.setGameMode(Bukkit.getDefaultGameMode());
            CharacterDisplay.reset(p);
            CharacterLifecycleListener.ouvrirSelection(plugin, p);
        }, Math.max(20L, conf.bienvenueTicks));
    }

    /** À revoir ou refusé : écran court, puis expulsion avec le même texte. */
    private void negatif(Player p, String resultat) {
        UUID id = p.getUniqueId();
        candidats.remove(id);
        file.retirer(id);
        transitions.add(id);
        retirerEcran(p);
        rendreVoix(p);
        if (plugin.staffPanel() != null) plugin.staffPanel().finAlerteDouane(id);
        canal.resultat(p, resultat, ReglesDouane.TEXTE_NEGATIF, conf.expulsionTicks / 20);
        Journal.entree(Categorie.JOUEUR, ReglesDouane.REFUSE.equals(resultat) ? "DOUANE_REFUSE" : "DOUANE_A_REVOIR").acteur(p).ecrire();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            transitions.remove(id);
            if (!p.isOnline()) return;
            p.setGameMode(Bukkit.getDefaultGameMode());   // l'Aventure de la douane ne reste pas sur le compte
            p.kick(Component.text(ReglesDouane.TEXTE_NEGATIF));
        }, Math.max(60L, conf.expulsionTicks));
    }

    /* ================================================================ textes (commande, repli sans mod) */

    String heure(long epochMs) {
        if (epochMs <= 0) return "—";
        return DateTimeFormatter.ofPattern("HH:mm").withZone(conf.fuseau).format(Instant.ofEpochMilli(epochMs));
    }

    /** {@code /douane} : statut du candidat, ou résumé pour le staff. */
    void statut(CommandSender s) {
        if (!(s instanceof Player p)) { resume(s); return; }
        Candidat c = candidats.get(p.getUniqueId());
        if (c != null) {
            String l = c.bureau != null ? ReglesDouane.LIGNE_BUREAU : ReglesDouane.ligneAttente(ouvert, file.rang(c.id));
            p.sendMessage(Component.text(l, NamedTextColor.GOLD));
            if (c.convocationAt() > 0) p.sendMessage(Component.text("Convocation : " + heure(c.convocationAt()), NamedTextColor.GRAY));
            return;
        }
        if (peutExaminer(p) || StaffGrades.of(p) >= StaffGrades.HELPER) { resume(s); return; }
        p.sendMessage(Component.text("Tu n'es pas à la douane.", NamedTextColor.GRAY));
    }

    private void resume(CommandSender s) {
        if (!actif) { s.sendMessage(Component.text("Douane coupée (douane.enabled: false).", NamedTextColor.GRAY)); return; }
        if (horsService != null) {
            s.sendMessage(Component.text("Douane fermée (" + horsService + ") : accès « douane » refusés, redémarrage"
                    + " requis une fois douane.voix.* remplies · " + envoisEnAttente() + " décision(s) en attente d'envoi",
                    NamedTextColor.RED));
            return;
        }
        s.sendMessage(Component.text("Douane · hall " + (ouvert ? "ouvert" : "fermé") + " · " + file.taille() + " en attente · "
                + envoisEnAttente() + " décision(s) en attente d'envoi", NamedTextColor.GOLD));
        for (Bureau b : bureaux.values()) {
            String ex = b.examinateur == null ? "libre" : String.valueOf(Bukkit.getOfflinePlayer(b.examinateur).getName());
            String ca = b.candidat != null ? String.valueOf(Bukkit.getOfflinePlayer(b.candidat).getName())
                    : b.absent != null ? Bukkit.getOfflinePlayer(b.absent).getName() + " (déconnecté)" : "—";
            s.sendMessage(Component.text("  Bureau " + b.conf.id() + " : " + ex + " · " + ca, NamedTextColor.GRAY));
        }
    }

    /** {@code /douane file}. */
    void listerFile(CommandSender s) {
        if (s instanceof Player p && !peutExaminer(p) && StaffGrades.of(p) < StaffGrades.HELPER) {
            s.sendMessage(Component.text("Réservé aux examinateurs et au staff.", NamedTextColor.RED));
            return;
        }
        List<FileDouane.Entree> l = file.ordre();
        if (l.isEmpty()) { s.sendMessage(Component.text("Personne n'attend au hall.", NamedTextColor.GRAY)); return; }
        long t = System.currentTimeMillis();
        int r = 1;
        for (FileDouane.Entree e : l) {
            s.sendMessage(Component.text(ReglesDouane.rang(r++) + " · " + e.pseudo() + " · attend depuis "
                    + Math.max(0, (t - e.arriveeAt()) / 60_000L) + " min · convocation " + heure(e.convocationAt())
                    + (e.village().isEmpty() ? "" : " · " + e.village()) + (e.deuxiemePassage() ? " · 2ᵉ passage" : "")
                    + (e.teteAt() > 0 ? " · en tête" : ""), NamedTextColor.GRAY));
        }
    }

    /** Pseudos des candidats de la file (complétion). */
    List<String> pseudosFile() {
        List<String> l = new ArrayList<>();
        for (FileDouane.Entree e : file.ordre()) l.add(e.pseudo());
        return l;
    }

    List<String> pseudosCandidats() {
        List<String> l = new ArrayList<>();
        for (Candidat c : candidats.values()) l.add(c.pseudo);
        return l;
    }

    /** Personnage actif d'un joueur (plaque restaurée à la sortie du mode examinateur). */
    ShinobiCharacter actifDe(UUID id) {
        return plugin.characters() == null ? null : plugin.characters().getActive(id);
    }

    void marquerSale() { etatSale = true; }
}
