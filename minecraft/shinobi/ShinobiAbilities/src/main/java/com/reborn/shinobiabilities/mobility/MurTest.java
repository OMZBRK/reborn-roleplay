package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobiabilities.mobility.MurRegles.Action;
import com.reborn.shinobiabilities.mobility.MurRegles.Cadence;
import com.reborn.shinobiabilities.mobility.MurRegles.Compteurs;
import com.reborn.shinobiabilities.mobility.MurRegles.Demande;
import com.reborn.shinobiabilities.mobility.MurRegles.Face;
import com.reborn.shinobiabilities.mobility.MurRegles.LecteurBlocs;
import com.reborn.shinobiabilities.mobility.MurRegles.Raison;
import com.reborn.shinobiabilities.mobility.MurRegles.Tenue;
import com.reborn.shinobiabilities.mobility.MurRegles.Virage;
import com.reborn.shinobicore.api.EtatService;
import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.etat.Verdict;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.api.event.EtatChangedEvent;
import com.reborn.shinobicore.event.KoEnterEvent;
import com.reborn.shinobicore.journal.Journal;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * <b>Essai technique de la marche sur les murs</b> (lot 5 « Mobilité », étapes C0 et C1a, Game Plan n° 17). Ce n'est
 * pas la fonctionnalité : c'est de quoi prouver l'approche retenue (pieds sur la paroi, corps à 90°, boîte du corps
 * couchée chez tous les clients, cube de 0,6 côté serveur) sur le vrai serveur, à deux comptes, avant d'écrire les murs
 * pour de bon. L'étape C1a ajoute le passage de mur en mur : le serveur valide les changements de face aux coins.
 *
 * <h2>Inerte tant qu'il est éteint</h2>
 * Rien ne se passe tant qu'un membre du staff n'a pas tapé {@code /murtest on} pour lui-même, et aucun joueur ne peut
 * l'allumer. Éteint, ce module n'a <b>ni écouteur ni tâche</b> : seuls la commande et le canal entrant existent (un
 * message d'un joueur sans permis est jeté à la première ligne). Les écouteurs sont posés au premier permis et retirés au
 * dernier ; la tâche et l'écouteur du suivi des entités ne vivent que tant qu'un joueur est au mur.
 *
 * <h2>Commande {@code /murtest on|off|filet on|off|bilan|raz}</h2>
 * Permission {@value #PERMISSION}, comme les autres commandes de diagnostic du plugin.
 * <ul>
 *   <li>{@code on} / {@code off} : le permis, pour soi ;</li>
 *   <li>{@code filet on|off} : allumé, un déplacement refusé pour écart de position ({@code MOVED_WRONGLY}) est accepté
 *       quand le joueur est au mur. Une traversée de bloc ({@code CLIPPED_INTO_BLOCK}) n'est <b>jamais</b> acceptée.
 *       Éteint par défaut : l'essai 1 se fait d'abord sans filet ;</li>
 *   <li>{@code bilan} : les chiffres de l'essai ({@link Compteurs#lignes}) ; {@code raz} : remise à zéro.</li>
 * </ul>
 *
 * <h2>Protocole {@value #CANAL}</h2>
 * JSON UTF-8 brut, champ {@code v} = 1, champs inconnus ignorés ({@link MurRegles}). Le serveur fait foi : la boîte d'un
 * joueur chez les autres dépend de l'état que le serveur diffuse, jamais d'un message d'un autre client.
 * <table>
 *   <tr><th>Sens</th><th>Message</th><th>Effet</th></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"permis","on":true}}</td><td>le joueur peut s'accrocher ; {@code false} :
 *       interdit, il lâche</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"etat","e":4182,"f":"E","n":12}}</td><td>l'entité marche sur un mur de cette
 *       face ; {@code "-"} : elle n'y est plus. Au joueur (sa confirmation), à ceux qui le voient, et à qui se met à le
 *       voir. {@code n} : le numéro de la demande qui a produit cet état, dans l'exemplaire du joueur lui-même
 *       seulement ; absent chez les voisins et quand le serveur parle de lui-même</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"refus","m":"…","a":"face","n":13}}</td><td>demande refusée ; {@code a} :
 *       l'action refusée ({@code accroche} ou {@code face}), {@code n} : son numéro</td></tr>
 *   <tr><td>C → S</td><td>{@code {"v":1,"a":"accroche","f":"E","n":12}}</td><td>demande d'accroche ; {@code n} : numéro
 *       de demande, facultatif</td></tr>
 *   <tr><td>C → S</td><td>{@code {"v":1,"a":"face","f":"N","n":13}}</td><td>changement de face à un coin, vers une face
 *       voisine ; redemander la face que l'on vient de quitter annule</td></tr>
 *   <tr><td>C → S</td><td>{@code {"v":1,"a":"lache","c":3}}</td><td>le joueur lâche ; {@code c} : corrections de position
 *       que le mod a reçues pendant la marche</td></tr>
 * </table>
 *
 * <h2>Au mur</h2>
 * Pose de nage fixée (le cube de 0,6), reposée si quelque chose l'enlève ; modificateur transitoire {@code GRAVITY} à
 * zéro (plus d'expulsion pour vol) ; distance de chute et compteur de flottement remis à zéro à chaque tick ; vol
 * interdit ; métadonnée {@value #META_MUR} qui fait se retirer {@link MobilityListener} et
 * {@link MobilityPathTwoModule}. Toutes les {@value MurRegles#PERIODE_CONTROLE} ticks le serveur relit qu'un bloc plein
 * est derrière le joueur et que son état permet la mobilité : sinon il lâche.
 *
 * <h2>Coins</h2>
 * Un changement de face est accepté vers une face voisine (jamais la face opposée) dans deux cas : <b>coin rentrant</b>,
 * le mur de la nouvelle face est déjà derrière le joueur (même contrôle que la marche) ; <b>coin sortant</b>, le mur
 * s'arrête devant lui et la face voisine du bloc d'angle est pleine ({@link MurRegles#coinSortant}). Un changement
 * refusé <b>ne fait pas tomber</b> : le joueur reçoit {@code refus} avec {@code a} = {@code face} et reste sur sa face.
 * Demander la face que l'on tient déjà n'est qu'un accusé de réception. Pendant les {@value MurRegles#TICKS_VIRAGE}
 * ticks qui suivent un changement accepté (la fenêtre de virage), le contrôle de paroi accepte le mur de l'ancienne
 * face, celui de la nouvelle, ou le bloc d'angle ({@link MurRegles#murEnVirage}) ; ensuite la nouvelle face seule.
 *
 * <h2>Retrait</h2>
 * Déconnexion, mort, KO, changement de monde, téléportation, changement de personnage, changement de mode de jeu, arrêt
 * du plugin : pose, gravité et métadonnée sont retirées, l'état {@code "-"} part aux voisins. <b>Au KO</b>, on lâche à
 * {@link KoEnterEvent} (priorité la plus basse), donc avant que le KO ne pose sa propre pose de nage ; passé ce point la
 * pose appartient au KO et on n'y touche plus ({@link #liberer}).
 */
public final class MurTest implements Listener, CommandExecutor, TabCompleter, PluginMessageListener {

    public static final String CANAL = MurRegles.CANAL;
    /** Métadonnée posée pendant la marche : le corps est pris (même rôle que {@code reborn_bond} du saut chargé). */
    public static final String META_MUR = "reborn_mur";
    private static final String PERMISSION = "shinobiabilities.staff";
    /** Messages lus par seconde et par joueur ; au-delà ils sont jetés. */
    private static final int CADENCE_MAX = 20;
    /** Ticks pendant lesquels la bascule du mod peut ne pas être encore arrivée au serveur (bascule + latence). */
    private static final int TICKS_BASCULE = 40;

    /** Joueurs au mur en ce moment : tant qu'il n'y en a aucun, {@link #auMur(Player)} ne lit même pas la métadonnée. */
    private static int marcheurs;

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final MobilityModule mobility;
    private final NamespacedKey cleGravite;

    /** Staff qui a tapé {@code /murtest on} ; vidé à la déconnexion. */
    private final Set<UUID> permis = new HashSet<>();
    private final Set<UUID> filets = new HashSet<>();
    private final Map<UUID, Marche> marches = new HashMap<>();
    /** Gardés après la déconnexion : une expulsion se lit au retour. */
    private final Map<UUID, Compteurs> compteurs = new HashMap<>();
    private final Map<UUID, Cadence> cadences = new HashMap<>();
    private final Listener voisins = new Voisins();
    /** Les écouteurs de cette classe sont posés (au moins un permis). */
    private boolean arme;
    private BukkitTask tache;

    private static final class Marche {
        /** La face tenue et la fenêtre de virage : ce que le contrôle de paroi doit trouver derrière le joueur. */
        final Tenue tenue;
        int ticks;
        /** L'écart de position que le filet vient d'accepter, pour le compter une fois le sort de l'événement connu. */
        PlayerFailMoveEvent filetPose;

        Marche(Face face) { this.tenue = new Tenue(face); }

        Face face() { return tenue.face(); }
    }

    /** Qui a mis fin à la marche. */
    private enum Fin { JOUEUR, SERVEUR, COMMANDE, DEPART, ARRET }

    public MurTest(JavaPlugin plugin, CoreServices core, MobilityModule mobility) {
        this.plugin = plugin;
        this.core = core;
        this.mobility = mobility;
        this.cleGravite = new NamespacedKey(plugin, "mur_gravite");
    }

    /**
     * Le joueur marche-t-il sur un mur ? Lu par {@link MobilityListener} et {@link MobilityPathTwoModule} pour se
     * retirer. Sans personne au mur, c'est la lecture d'un entier.
     */
    public static boolean auMur(Player p) {
        return marcheurs > 0 && p.hasMetadata(META_MUR);
    }

    /* ================================================================ cycle de vie */

    public void demarrer() {
        var m = plugin.getServer().getMessenger();
        m.registerOutgoingPluginChannel(plugin, CANAL);
        m.registerIncomingPluginChannel(plugin, CANAL, this);
    }

    /** Arrêt du plugin : tout le monde descend du mur, plus aucun permis. */
    public void arreter() {
        for (UUID id : List.copyOf(marches.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) lacher(p, Fin.ARRET, null);
        }
        for (UUID id : permis) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) envoyer(p, MurRegles.permis(false));
        }
        marches.clear();
        marcheurs = 0;
        permis.clear();
        filets.clear();
        cadences.clear();
        finSuivi();
        desarmerSiInutile();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CANAL, this);
    }

    private void armer() {
        if (arme) return;
        arme = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void desarmerSiInutile() {
        if (!arme || !permis.isEmpty() || !marches.isEmpty()) return;
        arme = false;
        HandlerList.unregisterAll(this);
    }

    /** Premier joueur au mur : la tâche de tenue et l'écouteur du suivi des entités démarrent. */
    private void debutSuivi() {
        if (tache != null) return;
        tache = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getServer().getPluginManager().registerEvents(voisins, plugin);
    }

    private void finSuivi() {
        if (tache == null) return;
        tache.cancel();
        tache = null;
        HandlerList.unregisterAll(voisins);
    }

    private Compteurs compteurs(UUID id) {
        return compteurs.computeIfAbsent(id, k -> new Compteurs());
    }

    /* ================================================================ commande */

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Commande réservée à un joueur en jeu.", NamedTextColor.RED));
            return true;
        }
        String sous = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String option = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sous) {
            case "on" -> allumer(p);
            case "off" -> eteindre(p);
            case "filet" -> {
                if (option.equals("on") || option.equals("off")) filet(p, option.equals("on"));
                else usage(p, label);
            }
            case "bilan" -> bilan(p);
            case "raz" -> raz(p);
            default -> usage(p, label);
        }
        return true;
    }

    private static void usage(Player p, String label) {
        p.sendMessage(Component.text("Usage : /" + label + " on|off|filet on|off|bilan|raz", NamedTextColor.GRAY));
    }

    private void allumer(Player p) {
        UUID id = p.getUniqueId();
        if (!p.getListeningPluginChannels().contains(CANAL)) {
            p.sendMessage(Component.text("Ton jeu n'écoute pas le canal " + CANAL
                    + " : il faut le mod à jour, lancé par le launcher.", NamedTextColor.RED));
            return;
        }
        boolean neuf = permis.add(id);
        armer();
        envoyer(p, MurRegles.permis(true));
        if (neuf) journal("MURTEST_ON", p);
        p.sendMessage(Component.text("Essai du mur allumé pour toi. Pousse contre un mur plein pour t'y accrocher.",
                NamedTextColor.GREEN));
        String gene = refusEtat(p, marches.containsKey(id));
        if (gene != null) {
            p.sendMessage(Component.text("Attention, l'accroche sera refusée pour l'instant : " + gene,
                    NamedTextColor.YELLOW));
        }
    }

    private void eteindre(Player p) {
        UUID id = p.getUniqueId();
        boolean avait = permis.remove(id);
        // le filet tombe avec le permis : au dernier permis rendu, quitter() n'est plus posé pour le vider
        boolean filetCoupe = filets.remove(id);
        envoyer(p, MurRegles.permis(false));
        lacher(p, Fin.COMMANDE, null);
        if (avait) journal("MURTEST_OFF", p);
        desarmerSiInutile();
        p.sendMessage(Component.text(filetCoupe ? "Essai du mur éteint pour toi, filet éteint aussi."
                : "Essai du mur éteint pour toi.", NamedTextColor.GRAY));
    }

    private void filet(Player p, boolean on) {
        if (on && !permis.contains(p.getUniqueId())) {
            p.sendMessage(Component.text("Fais d'abord /murtest on.", NamedTextColor.RED));
            return;
        }
        boolean change = on ? filets.add(p.getUniqueId()) : filets.remove(p.getUniqueId());
        if (change) journal(on ? "MURTEST_FILET_ON" : "MURTEST_FILET_OFF", p);
        p.sendMessage(on
                ? Component.text("Filet allumé pour toi : au mur, un écart de position n'est plus refusé. "
                        + "Une traversée de bloc reste toujours refusée.", NamedTextColor.GREEN)
                : Component.text("Filet éteint pour toi.", NamedTextColor.GRAY));
    }

    private void bilan(Player p) {
        UUID id = p.getUniqueId();
        Compteurs c = compteurs.get(id);
        if (c == null) c = new Compteurs();
        Marche m = marches.get(id);
        p.sendMessage(Component.text("Essai du mur, bilan de " + p.getName(), NamedTextColor.GOLD));
        for (String ligne : c.lignes(permis.contains(id), filets.contains(id), m == null ? null : m.face(),
                System.currentTimeMillis())) {
            int coupe = ligne.indexOf(" : ");
            p.sendMessage(coupe < 0
                    ? Component.text(ligne, NamedTextColor.DARK_GRAY)
                    : Component.text(ligne.substring(0, coupe + 3), NamedTextColor.GRAY)
                            .append(Component.text(ligne.substring(coupe + 3), NamedTextColor.WHITE)));
        }
    }

    private void raz(Player p) {
        UUID id = p.getUniqueId();
        if (marches.containsKey(id)) {
            Compteurs neuf = new Compteurs();
            neuf.reprendreMarche(System.currentTimeMillis());
            compteurs.put(id, neuf);
            p.sendMessage(Component.text("Tu es au mur : les corrections de cette marche comptent depuis l'accroche. "
                    + "Lâche puis raccroche pour une mesure propre.", NamedTextColor.YELLOW));
        } else {
            compteurs.remove(id);
        }
        p.sendMessage(Component.text("Compteurs de l'essai du mur remis à zéro.", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) return prefixe(List.of("on", "off", "filet", "bilan", "raz"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("filet")) return prefixe(List.of("on", "off"), args[1]);
        return List.of();
    }

    private static List<String> prefixe(List<String> choix, String debut) {
        String bas = debut.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(choix.size());
        for (String c : choix) if (c.startsWith(bas)) out.add(c);
        return out;
    }

    /** Aucune action du staff n'est silencieuse (journal {@code STAFF} de ShinobiCore). */
    private static void journal(String action, Player p) {
        Journal.entree(JournalService.Categorie.STAFF, action).acteur(p).lieu(p.getLocation()).ecrire();
    }

    /* ================================================================ réception */

    @Override
    public void onPluginMessageReceived(@NotNull String canal, @NotNull Player p, byte[] octets) {
        if (!CANAL.equals(canal)) return;
        if (Bukkit.isPrimaryThread()) recevoir(p, octets);
        else plugin.getServer().getScheduler().runTask(plugin, () -> recevoir(p, octets));
    }

    private void recevoir(Player p, byte[] octets) {
        UUID id = p.getUniqueId();
        boolean aPermis = permis.contains(id);
        Compteurs c = compteurs.get(id);
        // sans permis, seul compte le « lache » qui suit un lâcher du serveur (il porte les corrections du mod)
        if (!aPermis && (c == null || !c.attendCompte)) return;
        if (!p.isOnline()) return;
        Demande d = MurRegles.lire(octets);
        if (d == null) return;
        Marche m = marches.get(id);
        if (MurRegles.soumisACadence(d, m == null ? null : m.face(), m == null ? null : m.tenue.faceAvant())
                && !cadences.computeIfAbsent(id, k -> new Cadence(CADENCE_MAX, 1000L)).passe(System.currentTimeMillis())) {
            // une face nouvelle hors budget : le mod reste au bord au lieu d'attendre une seconde sa réponse
            if (d.action() == Action.FACE && aPermis) envoyer(p, MurRegles.refus("Trop de demandes.", Action.FACE, d.n()));
            return;
        }
        switch (d.action()) {
            case LACHE -> lacheDuMod(p, d.corrections());
            case ACCROCHE -> {
                if (aPermis) demander(p, d.face(), d.n());
            }
            case FACE -> {
                if (aPermis) changerDeFace(p, d.face(), d.n());
            }
        }
    }

    private void lacheDuMod(Player p, int corrections) {
        UUID id = p.getUniqueId();
        Compteurs c = compteurs(id);
        if (marches.containsKey(id)) {
            c.corrections += corrections;
            lacher(p, Fin.JOUEUR, null);
        } else if (c.attendCompte) {
            c.corrections += corrections;
            c.attendCompte = false;
        }
    }

    /**
     * Accroche ({@code n} : le numéro de la demande, null s'il n'y en a pas). Un joueur déjà au mur qui redemande une
     * accroche est jugé comme à l'étape C0 : même contrôle que la marche, et un refus le fait redescendre (le mod aussi).
     */
    private void demander(Player p, Face face, Integer n) {
        UUID id = p.getUniqueId();
        Compteurs c = compteurs(id);
        Marche m = marches.get(id);
        String refus = refusEtat(p, m != null);
        if (refus == null && !paroi(p, face, m != null, m != null && m.ticks <= TICKS_BASCULE)) refus = "Pas de mur plein derrière toi.";
        if (refus != null) {
            c.accrochesRefusees++;
            c.dernierRefus = refus;
            envoyer(p, MurRegles.refus(refus, Action.ACCROCHE, n));
            lacher(p, Fin.SERVEUR, refus);
            return;
        }
        if (m != null) {
            m.tenue.poser(face);
            diffuser(p, face, n);
            return;
        }
        accrocher(p, face, c, n);
    }

    /**
     * Changement de face à un coin, jugé par {@link MurRegles#jugerFace} : accepté vers une face voisine si le mur
     * s'arrête devant le joueur sur un bloc d'angle dont cette face est pleine (coin sortant) ou si le mur de cette
     * face est déjà derrière lui (coin rentrant). Un refus <b>ne fait pas tomber</b> : le joueur reste sur sa face, le
     * mod reste au bord. Si quelque chose interdit vraiment le mur (KO, monture, état), c'est la tenue du tick qui
     * lâche, avec sa raison. Redemander la face quittée après un accord est un changement comme un autre : le mur de
     * cette face est encore derrière le joueur, le serveur y revient.
     */
    private void changerDeFace(Player p, Face face, Integer n) {
        UUID id = p.getUniqueId();
        Compteurs c = compteurs(id);
        Marche m = marches.get(id);
        if (m != null && m.face() == face) {
            // la face demandée est déjà la sienne : le joueur annule un virage que le serveur avait refusé
            envoyer(p, MurRegles.etat(p.getEntityId(), face, n));
            return;
        }
        String refus = m == null ? "Tu n'es pas au mur." : refusEtat(p, true);
        Virage virage = null;
        if (refus == null) {
            Location l = p.getLocation();
            virage = MurRegles.jugerFace(lecteur(l.getWorld()), l.getX(), l.getY(), l.getZ(), m.face(), face,
                    m.ticks <= TICKS_BASCULE);
            refus = virage.jugement().refus;
        }
        if (refus != null) {
            c.facesRefusees++;
            c.dernierRefusFace = refus;
            envoyer(p, MurRegles.refus(refus, Action.FACE, n));
            return;
        }
        c.faces++;
        m.tenue.tourner(face, virage.arete(), m.ticks);
        diffuser(p, face, n);
    }

    /* ================================================================ validation */

    /** Pourquoi le joueur ne peut pas être au mur maintenant, hors paroi (null = rien ne s'y oppose). */
    private String refusEtat(Player p, boolean dejaAuMur) {
        String r = refusCorps(p);
        if (r != null) return r;
        // une pose fixée qui n'est pas la nôtre : /ramper, corps tenu, scène de mort
        if (!dejaAuMur && p.hasFixedPose()) return "Une autre posture tient déjà ton corps.";
        return refusSysteme(p);
    }

    /** Lu à chaque tick au mur : rien que des lectures simples. */
    private String refusCorps(Player p) {
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return "Passe en survie ou en aventure.";
        if (p.isDead() || p.getHealth() <= 0) return "Tu es mort.";
        if (core.ko() != null && core.ko().isKo(p.getUniqueId())) return "Tu es au KO.";
        if (p.isInsideVehicle()) return "Impossible sur une monture.";
        if (p.isGliding() || p.isSleeping()) return "Impossible en planant ou en dormant.";
        return null;
    }

    /**
     * Lu toutes les {@value MurRegles#PERIODE_CONTROLE} ticks au mur : un autre système a pris le corps, ou l'état du
     * joueur refuse la mobilité.
     */
    private String refusSysteme(Player p) {
        if (corpsPris(p)) return "Un autre déplacement tient déjà ton corps.";
        return refusMobilite(p.getUniqueId());
    }

    /** Saut chargé en vol, parcours, piste, grappin, tyrolienne : les mêmes que {@code MobilityListener.coreSessionActive}. */
    private boolean corpsPris(Player p) {
        if (p.hasMetadata("reborn_bond")) return true;
        if (mobility.training() != null && mobility.training().isRunning(p)) return true;
        if (mobility.trails() != null && mobility.trails().isRiding(p)) return true;
        var module = core.mobility();
        if (module == null) return false;
        UUID id = p.getUniqueId();
        return (module.grapple() != null && module.grapple().isGrappling(id))
                || (module.zipline() != null && module.zipline().isTraveling(id));
    }

    /** Capacité {@code MOBILITE} de la matrice des états (étourdi, porté, douleur, mode staff…), repli sur l'ancien test. */
    private String refusMobilite(UUID id) {
        EtatService etats = EtatService.get();
        if (etats != null) {
            Verdict v = etats.verifier(id, Capacite.MOBILITE);
            if (v == null || v.ok()) return null;
            return v.refus() != null ? v.refus() : "Ton état ne permet pas la mobilité.";
        }
        return core.ko() != null ? core.ko().mobilityDenial(id) : null;
    }

    /**
     * Un bloc plein est-il derrière le joueur, dans la direction {@code −n} ? À l'accroche (joueur debout) : à
     * {@value MurRegles#PORTEE_ACCROCHE} au plus, à hauteur des pieds ou du torse. Au mur : à
     * {@value MurRegles#PORTEE_MARCHE} au plus, à la hauteur du cube ; pendant les {@value #TICKS_BASCULE} premiers
     * ticks d'une marche ({@code bascule}), la hauteur du torse est acceptée aussi : le serveur ne voit la bascule
     * du mod qu'un aller-retour après l'avoir acceptée.
     */
    private boolean paroi(Player p, Face face, boolean auMur, boolean bascule) {
        Location l = p.getLocation();
        LecteurBlocs blocs = lecteur(l.getWorld());
        double x = l.getX(), y = l.getY(), z = l.getZ();
        if (auMur) return MurRegles.murDeMarche(blocs, x, y, z, face, bascule);
        return MurRegles.murDerriere(blocs, x, y + MurRegles.HAUTEUR_CUBE, z, face, MurRegles.PORTEE_ACCROCHE)
                || MurRegles.murDerriere(blocs, x, y + MurRegles.HAUTEUR_TORSE, z, face, MurRegles.PORTEE_ACCROCHE);
    }

    /**
     * Le contrôle de paroi de la marche, toutes les {@value MurRegles#PERIODE_CONTROLE} ticks ({@link Tenue#tient}).
     * Pendant la fenêtre de virage qui suit un changement de face accepté : le mur de l'ancienne face, celui de la
     * nouvelle, ou le bloc d'angle. Hors de la fenêtre : la face courante seule.
     */
    private boolean tientLaParoi(Player p, Marche m) {
        Location l = p.getLocation();
        return m.tenue.tient(lecteur(l.getWorld()), l.getX(), l.getY(), l.getZ(), m.ticks, m.ticks <= TICKS_BASCULE);
    }

    /** Formes de collision du monde, sans jamais charger un chunk. */
    private static LecteurBlocs lecteur(World w) {
        return (x, y, z) -> {
            if (y < w.getMinHeight() || y >= w.getMaxHeight() || !w.isChunkLoaded(x >> 4, z >> 4)) return List.of();
            Collection<BoundingBox> boites = w.getBlockAt(x, y, z).getCollisionShape().getBoundingBoxes();
            List<double[]> out = new ArrayList<>(boites.size());
            for (BoundingBox b : boites) {
                out.add(new double[] {b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ()});
            }
            return out;
        };
    }

    /* ================================================================ au mur */

    private void accrocher(Player p, Face face, Compteurs c, Integer n) {
        UUID id = p.getUniqueId();
        // ce qui tenait encore le corps dans la Voie du Chakra s'efface : agrippement, onde au sol, dash
        if (mobility.climb().isClinging(p)) mobility.climb().clear(p);
        mobility.shockwave().clear(p);
        mobility.dash().clear(p);
        marches.put(id, new Marche(face));
        marcheurs = marches.size();
        p.setMetadata(META_MUR, new FixedMetadataValue(plugin, true));
        if (c.attendCompte) {
            c.sansCompte++;
            c.attendCompte = false;
        }
        c.debutMarche(System.currentTimeMillis());
        try {
            tenir(p, null);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "[murtest] accroche de " + p.getName() + " impossible", e);
            envoyer(p, MurRegles.refus("Erreur du serveur.", Action.ACCROCHE, n));
            lacher(p, Fin.SERVEUR, "Erreur du serveur.");
            return;
        }
        debutSuivi();
        // la pose et la gravité sont posées : le mod peut lancer sa bascule, les voisins coucher la boîte
        diffuser(p, face, n);
    }

    /**
     * Tient le corps au mur, à chaque tick. {@code c} null : c'est l'accroche, rien n'est compté ; sinon une pose à
     * reposer ou un vol réarmé sont notés au bilan (les deux doivent rester à zéro).
     */
    private void tenir(Player p, Compteurs c) {
        if (p.getPose() != Pose.SWIMMING || !p.hasFixedPose()) {
            p.setPose(Pose.SWIMMING, true);
            if (c != null) c.poseReposee++;
        }
        AttributeInstance gravite = p.getAttribute(Attribute.GRAVITY);
        if (gravite != null && gravite.getModifier(cleGravite) == null) {
            gravite.addTransientModifier(new AttributeModifier(cleGravite, -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
        p.setFallDistance(0f);
        p.resetFlyingTicks();
        // un joueur à qui le vol est permis n'est jamais expulsé pour flottement : l'essai 1 ne prouverait rien
        if (p.getAllowFlight()) {
            p.setAllowFlight(false);
            if (c != null) c.volAutorise++;
        }
    }

    private void tick() {
        for (UUID id : List.copyOf(marches.keySet())) {
            Marche m = marches.get(id);
            if (m == null) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                // la déconnexion fait descendre avant d'arriver ici : simple garde pour ne jamais tourner à vide
                marches.remove(id);
                continue;
            }
            m.ticks++;
            String fin = refusCorps(p);
            if (fin == null && m.ticks % MurRegles.PERIODE_CONTROLE == 0) {
                fin = refusSysteme(p);
                if (fin == null && !tientLaParoi(p, m)) fin = "Plus de mur plein derrière toi.";
            }
            if (fin != null) {
                lacher(p, Fin.SERVEUR, fin);
                continue;
            }
            try {
                tenir(p, compteurs(id));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "[murtest] tenue de " + p.getName() + " impossible", e);
                lacher(p, Fin.SERVEUR, "Erreur du serveur.");
            }
        }
        marcheurs = marches.size();
        if (marches.isEmpty()) finSuivi();
    }

    /**
     * Fait descendre le joueur du mur (sans effet s'il n'y est pas) : gravité et pose rendues, métadonnée retirée quoi
     * qu'il arrive, état {@code "-"} au joueur et aux voisins.
     */
    private void lacher(Player p, Fin fin, String raison) {
        UUID id = p.getUniqueId();
        if (marches.remove(id) == null) return;
        marcheurs = marches.size();
        Compteurs c = compteurs(id);
        c.finMarche(System.currentTimeMillis(), Bukkit.getCurrentTick());
        // le mod sort de lui-même à la réception de l'état : son « lache » qui suit porte encore ses corrections
        c.attendCompte = fin == Fin.SERVEUR || fin == Fin.COMMANDE;
        if (fin == Fin.SERVEUR) {
            c.lachersServeur++;
            c.dernierLacher = raison;
            plugin.getLogger().info("[murtest] " + p.getName() + " lâché par le serveur : " + raison);
        }
        try {
            liberer(p);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "[murtest] retrait de " + p.getName() + " incomplet", e);
        } finally {
            // sans cela toute la mobilité du joueur resterait coupée jusqu'à sa déconnexion
            p.removeMetadata(META_MUR, plugin);
        }
        diffuser(p, null, null);
        if (marches.isEmpty()) finSuivi();
    }

    /**
     * Rend la gravité et la pose. La pose n'est retirée que si elle est encore la nôtre : un joueur déjà au KO porte la
     * pose de nage (à terre) ou de sommeil que {@code KoManager} vient de fixer, un corps tenu ou une scène de mort ont
     * la leur ; on n'y touche pas.
     */
    private void liberer(Player p) {
        AttributeInstance gravite = p.getAttribute(Attribute.GRAVITY);
        if (gravite != null) gravite.removeModifier(cleGravite);
        // descendre un mur n'accumule rien ; la chute qui suit le lâcher, elle, est une chute ordinaire
        p.setFallDistance(0f);
        boolean ko = core.ko() != null && core.ko().isKo(p.getUniqueId());
        if (!ko && p.getPose() == Pose.SWIMMING && p.hasFixedPose()) p.unsetFixedPose();
    }

    /* ================================================================ envoi */

    /**
     * L'état du joueur, à lui-même (sa confirmation) et à tous ceux qui le voient. {@code face} null : il n'y est plus.
     * {@code n} : le numéro de la demande qui a produit cet état, recopié dans l'exemplaire du joueur seulement (null :
     * le serveur parle de lui-même). Les voisins ne le reçoivent jamais : pour eux, un nouvel état est un virage à jouer.
     */
    private void diffuser(Player p, Face face, Integer n) {
        envoyer(p, MurRegles.etat(p.getEntityId(), face, n));
        Collection<Player> voisins = p.getTrackedBy();
        if (voisins.isEmpty()) return;
        byte[] message = MurRegles.etat(p.getEntityId(), face);
        for (Player voisin : voisins) envoyer(voisin, message);
    }

    private void envoyer(Player p, byte[] message) {
        // plugin arrêté : Bukkit refuse l'envoi (le client remet tout à zéro à sa déconnexion)
        if (!plugin.isEnabled() || !p.isOnline() || !p.getListeningPluginChannels().contains(CANAL)) return;
        try {
            p.sendPluginMessage(plugin, CANAL, message);
        } catch (RuntimeException ignored) {
            // canal retiré entre-temps (arrêt en cours)
        }
    }

    /**
     * Posé seulement tant qu'un joueur est au mur : dès qu'il a un écouteur, le serveur crée cet événement pour chaque
     * entité que chaque joueur se met à voir.
     */
    private final class Voisins implements Listener {

        /**
         * Un joueur se met à voir un marcheur : il reçoit son état. L'événement part avant le paquet d'apparition de
         * l'entité ; l'état est envoyé au tick suivant, donc après lui.
         */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void suivi(PlayerTrackEntityEvent e) {
            if (!(e.getEntity() instanceof Player cible) || !marches.containsKey(cible.getUniqueId())) return;
            Player voisin = e.getPlayer();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Marche m = marches.get(cible.getUniqueId());
                if (m != null && cible.isOnline()) envoyer(voisin, MurRegles.etat(cible.getEntityId(), m.face()));
            });
        }

        /**
         * Un joueur qui se connecte n'annonce ses canaux qu'après avoir traité son paquet de connexion : s'il s'est mis
         * à voir un marcheur avant, l'état de {@link #suivi} a été jeté par {@code envoyer}. Il repart à l'annonce.
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void canal(PlayerRegisterChannelEvent e) {
            if (!CANAL.equals(e.getChannel())) return;
            Player voisin = e.getPlayer();
            for (Map.Entry<UUID, Marche> m : marches.entrySet()) {
                Player cible = Bukkit.getPlayer(m.getKey());
                if (cible == null || cible.equals(voisin) || !cible.getTrackedBy().contains(voisin)) continue;
                envoyer(voisin, MurRegles.etat(cible.getEntityId(), m.getValue().face()));
            }
        }
    }

    /* ================================================================ retraits */

    @EventHandler(priority = EventPriority.LOWEST)
    public void quitter(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        lacher(p, Fin.DEPART, null);
        permis.remove(id);
        filets.remove(id);
        cadences.remove(id);
        Compteurs c = compteurs.get(id);
        if (c != null) c.attendCompte = false;
        desarmerSiInutile();
    }

    /** Avant les effets du KO ({@link KoEnterEvent} part avant eux) : notre pose est retirée, le KO pose la sienne. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void ko(KoEnterEvent e) {
        lacher(e.player(), Fin.SERVEUR, "KO.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mort(PlayerDeathEvent e) {
        lacher(e.getPlayer(), Fin.SERVEUR, "Mort.");
    }

    /** Étourdi, porté, douleur, mode staff… : relu au tick même du changement d'état. */
    @EventHandler
    public void etatChange(EtatChangedEvent e) {
        Player p = e.player();
        if (!marches.containsKey(p.getUniqueId())) return;
        String refus = refusMobilite(p.getUniqueId());
        if (refus != null) lacher(p, Fin.SERVEUR, refus);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void personnage(CharacterSwitchEvent e) {
        lacher(e.player(), Fin.SERVEUR, "Changement de personnage.");
    }

    /**
     * Une vraie téléportation fait descendre. La cause {@code UNKNOWN} est celle des replacements internes du serveur :
     * les ignorer laisse l'essai mesurer une correction de position au lieu d'y mettre fin (un vrai départ est vu par le
     * contrôle de paroi qui suit).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleportation(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;
        lacher(e.getPlayer(), Fin.SERVEUR, "Téléportation.");
    }

    @EventHandler
    public void monde(PlayerChangedWorldEvent e) {
        lacher(e.getPlayer(), Fin.SERVEUR, "Changement de monde.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void modeDeJeu(PlayerGameModeChangeEvent e) {
        lacher(e.getPlayer(), Fin.SERVEUR, "Changement de mode de jeu.");
    }

    /* ================================================================ mesures */

    /** Le filet : au mur, un écart de position est accepté. Jamais une traversée de bloc. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void filetDeplacement(PlayerFailMoveEvent e) {
        if (e.getFailReason() != PlayerFailMoveEvent.FailReason.MOVED_WRONGLY || e.isAllowed()) return;
        UUID id = e.getPlayer().getUniqueId();
        Marche m = marches.get(id);
        if (m == null || !filets.contains(id)) return;
        e.setAllowed(true);
        m.filetPose = e;
    }

    /** Le sort final de chaque déplacement refusé d'un joueur qui a le permis (l'événement n'est pas annulable). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void deplacementRefuse(PlayerFailMoveEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!permis.contains(id)) return;
        Compteurs c = compteurs(id);
        Marche m = marches.get(id);
        boolean parLeFilet = m != null && m.filetPose == e;
        if (m != null) m.filetPose = null;
        if (e.isAllowed()) {
            if (parLeFilet) c.filet++;
            return;
        }
        Location vers = e.getTo();
        int tick = Bukkit.getCurrentTick();
        c.refus(m != null, tick).refuse(tick, vers.getX(), vers.getY(), vers.getZ(), Raison.de(e.getFailReason().name()));
    }

    /** Expulsion du serveur pendant la marche (flottement, le plus souvent) : le chiffre que l'essai 1 veut à zéro. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void expulsion(PlayerKickEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!marches.containsKey(id)) return;
        Compteurs c = compteurs(id);
        c.expulsions++;
        c.derniereExpulsion = e.getCause() == PlayerKickEvent.Cause.FLYING_PLAYER
                ? "flottement (vol interdit)"
                : e.getCause().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Un clic d'attaque sur un marcheur, accepté par le serveur (portée validée) : le coup compte, essai 5. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void coup(PrePlayerAttackEntityEvent e) {
        if (!e.willAttack() || !(e.getAttacked() instanceof Player cible)) return;
        UUID id = cible.getUniqueId();
        if (marches.containsKey(id)) compteurs(id).coupsMelee++;
        // le marcheur est tombé (étourdi par un combo, douleur) : ces clics ne sont pas un échec de l'essai 5
        else if (permis.contains(id)) compteurs(id).coupsHorsMur++;
    }

    /** Un projectile que le serveur fait toucher le marcheur (son cube de 0,6) : essai 6. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void projectile(ProjectileHitEvent e) {
        if (marches.isEmpty()) return;
        if (e.getHitEntity() instanceof Player cible && marches.containsKey(cible.getUniqueId())) {
            compteurs(cible.getUniqueId()).projectiles++;
        }
    }

    /** Tout dégât réellement appliqué au marcheur, techniques comprises (la chute n'en est pas un) : essai 6. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void degat(EntityDamageEvent e) {
        if (marches.isEmpty() || e.getCause() == EntityDamageEvent.DamageCause.FALL) return;
        if (e.getEntity() instanceof Player cible && marches.containsKey(cible.getUniqueId())) {
            compteurs(cible.getUniqueId()).degats++;
        }
    }
}
