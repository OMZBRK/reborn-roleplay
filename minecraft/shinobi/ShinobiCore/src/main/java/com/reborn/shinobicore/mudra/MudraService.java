package com.reborn.shinobicore.mudra;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.etat.Verdict;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.api.event.EtatChangedEvent;
import com.reborn.shinobicore.concentration.plaque.PlaqueRegles;
import com.reborn.shinobicore.etat.Verrous;
import com.reborn.shinobicore.event.KoEnterEvent;
import com.reborn.shinobicore.technique.Mudra;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * <b>Mudras au clavier</b> (demande du 14/11) : la barre de 12 mudras du mod, composée en temps compté. Le serveur ouvre
 * les sessions, tient la minuterie, reçoit chaque signe et décide de la fin ; le mod ({@code reborn-hud}) dessine la barre
 * et ne sait jamais si un signe est juste ni s'il y a une porte devant lui.
 *
 * <p>Canal {@value #CANAL}, JSON UTF-8 brut, champ {@code v} = {@value #VERSION}, champs inconnus ignorés
 * (PLUGINS_REFERENCE §4 terdecies) :
 * <table>
 *   <tr><th>Sens</th><th>Message</th><th>Effet</th></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"regles","actif":bool,"max":12}}</td><td>40 ticks après la connexion, au
 *       changement de personnage, à {@code /sc reload}, à {@code /porte systeme} : l'interrupteur du système seulement</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"ouvre","n":3,"mode":"libre","temps":5000,"gain":700,"max":12}}</td>
 *       <td>session ouverte ; en mode {@code "guide"} en plus {@code "total":3} (nombre de signes) et
 *       {@code "titre":"…"} ; <b>jamais la suite</b> (16/11 : elle se sait par le parchemin ou par un professeur)</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"fin","n":3,"res":"ok|rate|temps|coupe|ferme","m":"…"}}</td><td>la session
 *       {@code n} est finie ({@code m} facultatif)</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"refus","m":"…"}}</td><td>{@code ouvrir} refusé : toujours une réponse</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"geste","e":4182,"s":2}}</td><td>un proche a posé un signe (jamais envoyé au
 *       signataire)</td></tr>
 *   <tr><td>C → S</td><td>{@code {"v":1,"a":"ouvrir"}}</td><td>demande une session libre ; une session déjà ouverte est
 *       refermée ({@code fin} {@code ferme}) puis {@code refus} sans message</td></tr>
 *   <tr><td>C → S</td><td>{@code {"v":1,"a":"signe","n":3,"s":2}}</td><td>un signe (0 à 11) ; ignoré en silence hors
 *       session, hors bornes ou au-delà de la cadence (un signe par {@code signe-min-ms}, 500 ms de marge)</td></tr>
 *   <tr><td>C → S</td><td>{@code {"v":1,"a":"fermer","n":3}}</td><td>referme la session ({@code fin} {@code ferme})</td></tr>
 * </table>
 *
 * <p><b>Aucun indice</b> : en mode libre, ni la présence d'une porte ni la justesse d'un signe ne partent chez le client ;
 * hors de portée de toute porte, la barre s'ouvre et se joue pareil. En mode guide non plus : le mod ne reçoit que le
 * nombre de signes, la suite se compose de mémoire.
 *
 * <p><b>Essais en série</b> : {@code portes.essais-max} compositions manquées devant une même porte en
 * {@code portes.essais-fenetre-s} secondes, et la porte ne répond plus à ce joueur pendant {@code portes.blocage-s}
 * secondes (même la bonne suite). Rien ne lui est dit ; le journal du staff reçoit {@code MUDRA_BLOCAGE}. L'essai
 * compte pour chaque porte fermée vue à portée pendant la session ; la bonne suite d'une porte, posée sans qu'elle
 * s'ouvre, n'est ni comptée ni écrite en clair au journal.
 */
public final class MudraService implements PluginMessageListener, Listener {

    public static final String CANAL = "reborn:mudra";
    public static final int VERSION = 1;
    /** Relecture des sessions : minuterie à chaque passage (2 ticks), droits tous les 5 passages (10 ticks). */
    private static final long PERIODE_TICKS = 2L;
    private static final int PASSAGES_DROITS = 5;
    private static final String SON_SIGNE = "reborn:mudra.signe";

    /** Fin d'une session, telle qu'elle part dans {@code fin.res}. */
    public enum Fin {
        OK("ok"), RATE("rate"), TEMPS("temps"), COUPE("coupe"), FERME("ferme");

        private final String code;

        Fin(String code) { this.code = code; }

        public String code() { return code; }
    }

    /** Une session de composition en cours (une par joueur au plus). */
    private static final class Session {
        final int n;
        /** Mode guide : la suite attendue (ordinaux) ; null en mode libre. */
        final List<Integer> attendue;
        final long tempsMs, gainMs;
        /** Rappel de l'appelant (mode guide) ; null en mode libre, ou après {@link MudraService#annuler}. */
        Consumer<Fin> fin;
        final List<Integer> composee = new ArrayList<>(MudraRegles.SUITE_MAX);
        long echeance;
        /** Cadence des signes : instant où le suivant est attendu au plus tôt ({@link MudraRegles#signeSuivant}). */
        long auPlusTot;
        /**
         * Mode libre : la dernière porte fermée à portée <b>au moment d'un signe</b>, et où se tenait alors le joueur. La
         * trace {@code MUDRA_ESSAI} se décide là, pas à la fin de la session : reculer d'un pas avant de refermer ne
         * l'efface pas.
         */
        PorteScellee devant;
        Location lieuEssai;
        /**
         * Mode libre : toutes les portes fermées vues à portée au moment d'un signe (identifiant → porte). L'essai
         * manqué compte pour chacune : deux portes proches (sas, double sceau) ne se couvrent pas l'une l'autre.
         */
        final Map<String, PorteScellee> vues = new LinkedHashMap<>();

        Session(int n, List<Integer> attendue, long tempsMs, long gainMs, Consumer<Fin> fin, long now) {
            this.n = n;
            this.attendue = attendue;
            this.tempsMs = tempsMs;
            this.gainMs = gainMs;
            this.fin = fin;
            this.echeance = MudraRegles.echeance(now, tempsMs);
        }

        boolean guide() { return attendue != null; }
    }

    private final ShinobiCore plugin;
    private final PorteService portes;
    private MudraRegles.Reglages reglages = MudraRegles.Reglages.DEFAUT;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** Dernier numéro de session de chaque joueur (croissant jusqu'à sa déconnexion). */
    private final Map<UUID, Integer> numeros = new HashMap<>();
    /** Instant de la dernière fin de session de chaque joueur (délai de recharge). */
    private final Map<UUID, Long> finLe = new HashMap<>();
    private final PlaqueRegles.Limiteur<UUID> ouvertures = new PlaqueRegles.Limiteur<>(MudraRegles.OUVRIR_PAR_S, 1000);

    /**
     * Joueur → identifiant de porte → essais. Gardé à la déconnexion (se reconnecter ne lève pas un blocage) ; vidé à
     * l'arrêt et à {@code /sc reload} (qui lève tous les blocages d'un coup). L'outil du Poste de garde les liste
     * ({@link #blocages}) et en lève un seul ({@link #debloquer}).
     */
    private final Map<UUID, Map<String, MudraRegles.Essais>> essais = new HashMap<>();
    private int tache = -1;
    private int passages;

    public MudraService(ShinobiCore plugin, PorteService portes) {
        this.plugin = plugin;
        this.portes = portes;
    }

    /* ================================================================ cycle de vie */

    public void demarrer(MudraRegles.Reglages r) {
        reglages = r;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CANAL)) m.registerOutgoingPluginChannel(plugin, CANAL);
        if (!m.isIncomingChannelRegistered(plugin, CANAL)) m.registerIncomingPluginChannel(plugin, CANAL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Arrêt du module : toutes les sessions finissent ({@code coupe}), tables vidées. */
    public void arreter() {
        couperTout(null);
        desarmer();
        numeros.clear();
        finLe.clear();
        essais.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CANAL, this);
    }

    /**
     * {@code /sc reload} : nouveaux réglages, règles renvoyées à tous. Les sessions en cours gardent leur minuterie ;
     * les comptes d'essais et les blocages de portes sont levés.
     */
    public void recharger(MudraRegles.Reglages r) {
        reglages = r;
        essais.clear();
        diffuserRegles();
    }

    /**
     * {@code /porte systeme on|off} : règles renvoyées à tous ; éteint, toute session est coupée (les dalles en mouvement
     * ou levées ont déjà été refermées par {@link PorteService#systeme}).
     */
    void systemeChange() {
        if (!portes.actif()) couperTout("Indisponible.");
        diffuserRegles();
    }

    private void diffuserRegles() {
        for (Player p : Bukkit.getOnlinePlayers()) regles(p);
    }

    private void couperTout(String m) {
        for (UUID id : List.copyOf(sessions.keySet())) finir(id, Fin.COUPE, m);
    }

    public MudraRegles.Reglages reglages() { return reglages; }

    /* ================================================================ droit de composer */

    /**
     * Pourquoi la barre est refusée maintenant (null = permise) : relu à l'ouverture, à chaque signe, toutes les 10 ticks
     * pendant la session et à chaque {@code EtatChangedEvent}. Les cas durs sont relus chez leurs détenteurs (mort, KO,
     * cinématique, douane, sélection, mode staff, sans personnage), comme {@code ByakuganService.refus} : un
     * {@code etats.yml} du disque plus ancien gagne sur celui du jar. Puis la matrice : {@code TECHNIQUE} <b>avec
     * mudras</b> (un entravé ne signe pas).
     */
    private String refus(Player p) {
        UUID id = p.getUniqueId();
        var ko = plugin.ko() != null ? plugin.ko().getKo(id) : null;
        var o = plugin.outilsStaff();
        boolean modeStaff = o != null && o.mode().horsJeu(id);
        boolean selection = plugin.characterSelect() != null && plugin.characterSelect().isSelecting(id);
        boolean sansPerso = plugin.characters() != null && plugin.characters().getActive(id) == null;
        String etat = null;
        var etats = plugin.etats();
        if (etats != null) {
            Verdict v = etats.verifier(id, Capacite.TECHNIQUE, Boolean.TRUE);
            if (!v.ok()) etat = v.raison() == null || v.raison().isBlank() ? "Impossible maintenant." : v.raison();
        }
        return MudraRegles.refus(new MudraRegles.Contexte(
            !portes.actif(),
            Verrous.mort(id),
            p.isDead() || p.getHealth() <= 0 || (ko != null && ko.isDowned()),
            ko != null && !ko.isDowned(),
            p.getGameMode() == GameMode.SPECTATOR,
            Verrous.cinematique(id),
            plugin.douane() != null && plugin.douane().cadreDouane(id),
            selection || modeStaff || sansPerso,
            etat));
    }

    /* ================================================================ réception */

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!CANAL.equals(channel) || bytes.length > MudraRegles.RECU_MAX) return;
        String raw = new String(bytes, StandardCharsets.UTF_8);
        // l'instant d'arrivée se lit ici : après le report sur le fil principal il serait arrondi au tick (50 ms)
        long recu = System.currentTimeMillis();
        Bukkit.getScheduler().runTask(plugin, () -> recevoir(p, raw, recu));
    }

    private void recevoir(Player p, String raw, long recu) {
        if (!p.isOnline()) return;
        String a;
        int n = -1, s = -1;
        try {
            JsonObject in = JsonParser.parseString(raw).getAsJsonObject();
            if (!in.has("v") || in.get("v").getAsInt() != VERSION) return;
            a = in.has("a") ? in.get("a").getAsString() : "";
            if (in.has("n")) n = in.get("n").getAsInt();
            if (in.has("s")) s = in.get("s").getAsInt();
        } catch (RuntimeException e) { return; }
        switch (a) {
            case "ouvrir" -> ouvrir(p);
            case "signe" -> signe(p, n, s, recu);
            case "fermer" -> {
                Session se = sessions.get(p.getUniqueId());
                if (se != null && se.n == n) finir(p.getUniqueId(), Fin.FERME, null);
            }
            default -> { }
        }
    }

    /** {@code ouvrir} : une session libre, ou un refus. Toujours une réponse. */
    private void ouvrir(Player p) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Session deja = sessions.get(id);
        if (deja != null) {
            // déjà ouverte (client désynchronisé) : une barre libre est refermée, jamais deux barres pour une session.
            // Une barre guidée reste : la demande a croisé son ouverture (touche pressée au moment où la séance
            // l'ouvrait), la refermer ferait échouer l'apprentissage (relecture du 15/11).
            if (!deja.guide()) finir(id, Fin.FERME, null);
            refuser(p, null);
            return;
        }
        if (!ouvertures.essayer(id, now)) { refuser(p, "Trop vite."); return; }
        String r = refus(p);
        if (r != null) { refuser(p, r); return; }
        if (!MudraRegles.rechargeFinie(finLe.getOrDefault(id, 0L), now, reglages.rechargeMs())) { refuser(p, "Trop vite."); return; }
        Session s = new Session(numero(id), null, reglages.tempsMs(), reglages.gainMs(), null, now);
        sessions.put(id, s);
        armer();
        JsonObject o = base("ouvre");
        o.addProperty("n", s.n);
        o.addProperty("mode", "libre");
        o.addProperty("temps", s.tempsMs);
        o.addProperty("gain", s.gainMs);
        o.addProperty("max", reglages.maxSignes());
        envoyer(p, o);
    }

    private int numero(UUID id) { return numeros.merge(id, 1, Integer::sum); }

    /**
     * {@code signe} : ignoré en silence hors session, hors bornes ou au-delà de la cadence ; sinon compté, montré aux
     * proches, jugé. {@code recu} est l'instant d'arrivée du paquet : la minuterie et la cadence se jugent dessus, pas sur
     * l'instant du traitement (un tick long ne mange ni le temps ni un signe du joueur).
     */
    private void signe(Player p, int n, int s, long recu) {
        UUID id = p.getUniqueId();
        Session se = sessions.get(id);
        if (se == null || se.n != n) return;
        if (!MudraRegles.signeRecevable(s, se.auPlusTot, recu)) return;
        if (MudraRegles.echue(se.echeance, recu)) { finir(id, Fin.TEMPS, null); return; }
        String r = refus(p);
        if (r != null) { finir(id, Fin.COUPE, r); return; }
        se.composee.add(s);
        se.auPlusTot = MudraRegles.signeSuivant(se.auPlusTot, recu, reglages.signeMinMs());
        se.echeance = MudraRegles.apresSigne(se.echeance, recu, se.tempsMs, se.gainMs);
        geste(p, s);
        if (se.guide()) {
            switch (MudraRegles.pasGuide(se.attendue, se.composee.size() - 1, s)) {
                case DERNIER -> finir(id, Fin.OK, null);
                case MAUVAIS -> finir(id, Fin.RATE, null);
                default -> { }
            }
            return;
        }
        // la porte devant laquelle ce signe est posé : c'est elle que nommera la trace d'un essai manqué
        PorteScellee pres = portes.plusProche(p, reglages.portee(), true);
        if (pres != null) {
            se.devant = pres;
            se.lieuEssai = p.getLocation();
            for (PorteScellee v : portes.portes().values()) {
                if (portes.fermee(v) && PorteService.distance(v, p) <= reglages.portee()) se.vues.put(v.id, v);
            }
        }
        PorteScellee d = portes.correspondante(p, se.composee);
        // une porte bloquée pour ce joueur (essais en série) ne répond pas, même à la bonne suite
        if (d != null && !bloquee(id, d, recu) && portes.ouvrirPar(p, d)) { finir(id, Fin.OK, null); return; }
        if (MudraRegles.pleine(se.composee.size(), reglages.maxSignes())) finir(id, Fin.RATE, null);
    }

    /**
     * Le signe vu et entendu des proches : même monde, {@code geste-rayon} blocs, joueurs qui écoutent le canal. Jamais au
     * signataire (il entend son propre son côté mod). Rien si le signataire leur est invisible (vanish, mode staff,
     * spectateur : la règle des traces de pas).
     */
    private void geste(Player p, int s) {
        double r = reglages.gesteRayon();
        if (r <= 0) return;
        UUID id = p.getUniqueId();
        var vanish = plugin.vanish();
        var o = plugin.outilsStaff();
        if (p.getGameMode() == GameMode.SPECTATOR || (vanish != null && vanish.isVanished(id)) || (o != null && o.mode().horsJeu(id))) return;
        Location ici = p.getLocation();
        double r2 = r * r;
        JsonObject g = null;
        for (Player autre : p.getWorld().getPlayers()) {
            if (autre == p || !autre.getListeningPluginChannels().contains(CANAL) || !autre.canSee(p)) continue;
            if (autre.getLocation().distanceSquared(ici) > r2) continue;
            if (g == null) {
                g = base("geste");
                g.addProperty("e", p.getEntityId());
                g.addProperty("s", s);
            }
            envoyer(autre, g);
            autre.playSound(ici, SON_SIGNE, SoundCategory.PLAYERS, 0.9f, 1.0f);
        }
    }

    /* ================================================================ fin d'une session */

    /**
     * Finit la session du joueur : retirée d'abord (un rappel qui rouvrirait une barre ne la retrouve pas), {@code fin}
     * envoyé au mod, essai manqué devant une porte journalisé, puis le rappel de l'appelant (mode guide), une seule fois.
     */
    private void finir(UUID id, Fin res, String m) {
        Session s = sessions.remove(id);
        if (s == null) return;
        if (sessions.isEmpty()) desarmer();
        finLe.put(id, System.currentTimeMillis());
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            JsonObject o = base("fin");
            o.addProperty("n", s.n);
            o.addProperty("res", res.code());
            if (m != null && !m.isBlank()) o.addProperty("m", m);
            envoyer(p, o);
        }
        if (!s.guide() && res != Fin.OK && !s.composee.isEmpty()) essaiManque(id, p, s, res);
        if (s.fin != null) {
            try { s.fin.accept(res); }
            catch (RuntimeException e) { plugin.getLogger().log(Level.WARNING, "Mudras : rappel de fin de barre guidée en erreur", e); }
        }
    }

    /**
     * Une composition libre finie sans succès à portée d'une porte fermée : {@code INTERACTION} / {@code MUDRA_ESSAI}.
     * La porte est celle devant laquelle un signe a été posé ({@link Session#devant}, relevée à chaque signe) : reculer,
     * changer de monde ou se déconnecter avant la fin ne retire pas la trace. À défaut (aucun signe posé à portée), la
     * porte la plus proche à la fin de la session, comme le dit le cahier.
     */
    private void essaiManque(UUID id, Player p, Session s, Fin res) {
        PorteScellee d = s.devant;
        Location lieu = s.lieuEssai;
        if (d == null && p != null) {
            d = portes.plusProche(p, reglages.portee(), true);
            lieu = p.getLocation();
        }
        if (d == null) return;
        s.vues.putIfAbsent(d.id, d);
        // L'essai compte pour chaque porte vue à portée, sauf celle dont la bonne suite a été posée sans qu'elle s'ouvre
        // (blocage, dernier signe hors de portée, dalle en mouvement) : celui qui la connaît n'est pas en train de forcer.
        long now = System.currentTimeMillis();
        boolean bonne = false;
        List<PorteScellee> bloquees = new ArrayList<>();
        for (PorteScellee v : s.vues.values()) {
            if (MudraRegles.commencePar(s.composee, MudraRegles.ordinaux(v.signes))) bonne = true;
            else if (compterEssai(id, v, now)) bloquees.add(v);
        }
        if (plugin.journal() == null) return;
        JournalService.Entree e = plugin.journal().entree(JournalService.Categorie.INTERACTION, "MUDRA_ESSAI");
        if (p != null) e.acteur(p);
        else e.acteur(id);
        e.cibleTexte("porte " + d.id)
                // la bonne suite d'une porte ne s'écrit jamais : le journal est lu des Helpers, qui ne la voient pas
                .detail(d.libelle() + " · composé : " + (bonne ? MudraRegles.SUITE_MASQUEE + " (bonne suite, porte restée fermée)"
                        : MudraRegles.libelleOrdinaux(s.composee)) + " · " + res.code());
        if (lieu != null) e.lieu(lieu);
        e.ecrire();
        for (PorteScellee v : bloquees) {
            JournalService.Entree b = plugin.journal().entree(JournalService.Categorie.INTERACTION, "MUDRA_BLOCAGE");
            if (p != null) b.acteur(p);
            else b.acteur(id);
            b.cibleTexte("porte " + v.id)
                    .detail(v.libelle() + " · " + reglages.essaisMax() + " essais manqués en " + reglages.essaisFenetreS()
                            + " s · la porte ne lui répond plus pendant " + reglages.blocageS() + " s");
            if (lieu != null) b.lieu(lieu);
            b.ecrire();
        }
    }

    /* ================================================================ essais en série */

    /** La porte est-elle bloquée pour ce joueur (trop d'essais manqués devant elle) ? */
    private boolean bloquee(UUID id, PorteScellee d, long now) {
        Map<String, MudraRegles.Essais> m = essais.get(id);
        MudraRegles.Essais e = m == null ? null : m.get(d.id);
        return e != null && e.bloquee(now);
    }

    /**
     * Note un essai manqué devant la porte ; vrai si cet essai déclenche le blocage. Pendant un blocage rien n'est
     * compté (il ne se prolonge pas). Les comptes éteints du joueur sont retirés au passage.
     */
    private boolean compterEssai(UUID id, PorteScellee d, long now) {
        if (reglages.essaisMax() <= 0) return false;
        long fenetre = reglages.essaisFenetreS() * 1000L;
        Map<String, MudraRegles.Essais> m = essais.computeIfAbsent(id, k -> new HashMap<>());
        m.values().removeIf(e -> e.eteint(now, fenetre));
        return m.computeIfAbsent(d.id, k -> new MudraRegles.Essais())
                .compter(now, reglages.essaisMax(), fenetre, reglages.blocageS() * 1000L);
    }

    /**
     * Outil du Poste de garde : les blocages en cours, par identifiant de porte (le plus long d'abord). Les comptes
     * éteints de la table sont purgés au passage (ils ne l'étaient qu'au prochain essai du même joueur).
     */
    public Map<String, List<MudraRegles.Blocage<UUID>>> blocages() {
        return MudraRegles.blocages(essais, System.currentTimeMillis(), reglages.essaisFenetreS() * 1000L);
    }

    /**
     * Outil du Poste de garde : lève le blocage d'un joueur devant une porte (Modérateur et plus), sans toucher à ceux
     * des autres ni aux autres portes ; son compte d'essais devant elle repart de zéro. {@code STAFF} /
     * {@code PORTE_DEBLOCAGE}, cible = le joueur. Rien ne lui est dit : il ne savait pas qu'il était bloqué.
     */
    public PorteService.Resultat debloquer(CommandSender s, String id, UUID joueur) {
        PorteScellee d = portes.get(id);
        if (d == null) return PorteService.Resultat.ko("Porte introuvable : " + id + ".");
        if (!MudraRegles.peutGerer(PorteService.grade(s))) return portes.refus(s, "débloquer " + d.id, "Réservé aux modérateurs.");
        if (joueur == null) return PorteService.Resultat.ko("Joueur inconnu.");
        long reste = MudraRegles.leverBlocage(essais, joueur, d.id, System.currentTimeMillis());
        if (reste <= 0) return PorteService.Resultat.ko("Ce joueur n'est pas bloqué devant la porte " + d.id + ".");
        if (plugin.journal() != null) {
            JournalService.Entree e = plugin.journal().entree(JournalService.Categorie.STAFF, "PORTE_DEBLOCAGE").acteur(s)
                    .cible(joueur).detail(d.libelle() + " · blocage levé (il restait " + (reste + 999) / 1000 + " s)");
            Location l = PorteService.lieu(d);
            if (l != null) e.lieu(l);
            e.ecrire();
        }
        return new PorteService.Resultat(d, null);
    }

    /* ================================================================ tâche des sessions */

    /** La tâche ne tourne que tant qu'une session est ouverte : rien par tick au repos. */
    private void armer() {
        if (tache >= 0) return;
        passages = 0;
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIODE_TICKS, PERIODE_TICKS).getTaskId();
    }

    private void desarmer() {
        if (tache >= 0) Bukkit.getScheduler().cancelTask(tache);
        tache = -1;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean droits = ++passages % PASSAGES_DROITS == 0;
        for (UUID id : List.copyOf(sessions.keySet())) {
            Session s = sessions.get(id);
            if (s == null) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null) { finir(id, Fin.COUPE, null); continue; }
            if (MudraRegles.echue(s.echeance, now)) { finir(id, Fin.TEMPS, null); continue; }
            if (droits) {
                String r = refus(p);
                if (r != null) finir(id, Fin.COUPE, r);
            }
        }
        if (sessions.isEmpty()) desarmer();
    }

    /* ================================================================ API pour les autres plugins */

    /**
     * Ouvre une barre guidée : le joueur compose la suite <b>de mémoire</b> (16/11 : le mod ne reçoit que le nombre de
     * signes, jamais la suite ; elle se sait par le parchemin ou par un professeur) ; chaque signe doit arriver en moins de
     * {@code tempsParSigneMs}. Faux si le système est éteint, si le joueur n'écoute pas {@value #CANAL}, n'a pas le droit,
     * ou a déjà une session. {@code fin} reçoit la raison de la fin, une seule fois, sur le fil principal.
     */
    public boolean guider(Player p, List<Mudra> suite, String titre, long tempsParSigneMs, Consumer<Fin> fin) {
        if (p == null || suite == null || suite.isEmpty() || suite.size() > MudraRegles.SUITE_MAX || !plugin.isEnabled()) return false;
        UUID id = p.getUniqueId();
        if (!p.isOnline() || !p.getListeningPluginChannels().contains(CANAL)) return false;
        Session deja = sessions.get(id);
        if (deja != null && deja.guide()) return false;
        if (refus(p) != null) return false;
        // une barre libre ouverte cède à la séance (le joueur composait au moment où l'apprentissage arrive aux mudras)
        if (deja != null) finir(id, Fin.FERME, null);
        long temps = MudraRegles.bornerGuide(tempsParSigneMs);
        List<Integer> attendue = List.copyOf(MudraRegles.ordinaux(suite));
        Session s = new Session(numero(id), attendue, temps, temps, fin, System.currentTimeMillis());
        sessions.put(id, s);
        armer();
        JsonObject o = base("ouvre");
        o.addProperty("n", s.n);
        o.addProperty("mode", "guide");
        o.addProperty("temps", temps);
        o.addProperty("gain", temps);
        o.addProperty("max", Math.max(reglages.maxSignes(), attendue.size()));
        o.addProperty("total", attendue.size());
        o.addProperty("titre", MudraRegles.titre(titre));
        envoyer(p, o);
        return true;
    }

    /** Finit sans appeler son rappel la session guidée du joueur (abandon par l'appelant) ; le mod reçoit {@code coupe}. */
    public void annuler(Player p) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        Session s = sessions.get(id);
        if (s == null || !s.guide()) return;
        s.fin = null;
        finir(id, Fin.COUPE, null);
    }

    public boolean enCours(Player p) { return p != null && sessions.containsKey(p.getUniqueId()); }

    /** Banc et diagnostic : nombre de sessions ouvertes. */
    public int nombreSessions() { return sessions.size(); }

    /* ================================================================ envoi */

    private void regles(Player p) {
        JsonObject o = base("regles");
        o.addProperty("actif", portes.actif());
        o.addProperty("max", reglages.maxSignes());
        envoyer(p, o);
    }

    /** Réponse à un {@code ouvrir} refusé ; {@code m} null : sans message. */
    private void refuser(Player p, String m) {
        JsonObject o = base("refus");
        if (m != null && !m.isBlank()) o.addProperty("m", m);
        envoyer(p, o);
    }

    private static JsonObject base(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("t", t);
        return o;
    }

    private void envoyer(Player p, JsonObject o) {
        if (!plugin.isEnabled() || !p.isOnline() || !p.getListeningPluginChannels().contains(CANAL)) return;
        p.sendPluginMessage(plugin, CANAL, o.toString().getBytes(StandardCharsets.UTF_8));
    }

    /* ================================================================ événements */

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // le client annonce ses canaux juste après la connexion : on attend qu'il écoute celui-ci
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) regles(p); }, 40L);
    }

    @EventHandler
    public void quitter(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        finir(id, Fin.COUPE, null);
        numeros.remove(id);
        finLe.remove(id);
        ouvertures.oublier(id);
    }

    /**
     * Changement de personnage : la session ne passe pas à l'autre personnage. L'événement part aussi quand le joueur
     * reprend le personnage qu'il joue déjà ({@code CharacterManager.setActive}) : rien n'a changé, la session continue
     * (comme {@code LearningMinigame.onSwitch}) ; les règles sont renvoyées dans les deux cas.
     */
    @EventHandler
    public void personnage(CharacterSwitchEvent e) {
        Player p = e.player();
        boolean meme = e.previous() != null && e.previous().id().equals(e.next().id());
        if (!meme) finir(p.getUniqueId(), Fin.COUPE, "Changement de personnage.");
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) regles(p); });
    }

    /** KO, cinématique, entrave… : relu au tick même du changement d'état, la barre se coupe net. */
    @EventHandler
    public void etatChange(EtatChangedEvent e) {
        Player p = e.player();
        if (!sessions.containsKey(p.getUniqueId())) return;
        String r = refus(p);
        if (r != null) finir(p.getUniqueId(), Fin.COUPE, r);
    }

    @EventHandler
    public void ko(KoEnterEvent e) {
        finir(e.player().getUniqueId(), Fin.COUPE, "Tu es à terre.");
    }

    @EventHandler
    public void mort(PlayerDeathEvent e) {
        finir(e.getPlayer().getUniqueId(), Fin.COUPE, "Tu es à terre.");
    }

    @EventHandler
    public void monde(PlayerChangedWorldEvent e) {
        finir(e.getPlayer().getUniqueId(), Fin.COUPE, null);
    }
}
