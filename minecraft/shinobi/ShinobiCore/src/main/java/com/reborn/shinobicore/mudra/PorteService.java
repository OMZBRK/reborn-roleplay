package com.reborn.shinobicore.mudra;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import com.reborn.shinobicore.technique.Mudra;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Portes scellées (demande du 14/11) : registre, création et retouches du staff, levée et descente animées de la dalle,
 * remise en place après un arrêt. Fil principal seulement.
 *
 * <p><b>Levée</b> ({@link #lever}) : la liste des blocs est écrite dans {@code mudras.yml} <b>avant</b> de toucher au
 * monde ; chaque bloc de la dalle devient un {@code BlockDisplay} non persistant et le bloc réel une barrière ; les
 * affichages glissent vers le haut par interpolation ; les rangées de barrières se libèrent de bas en haut au rythme de
 * {@link MudraRegles#tickLiberation}. <b>Descente</b> : le mouvement inverse, puis les blocs d'origine. Une porte restée
 * {@code ouverte: true} (arrêt brutal) est remise au démarrage, sans animation.
 *
 * <p>Au repos rien ne tourne : les seules tâches sont celles d'une porte en mouvement ou levée. La protection des dalles
 * (casse, pose, explosion, piston, feu) fait une recherche par événement dans un registre indexé par monde puis tronçon.
 */
public final class PorteService implements Listener {

    private static final String BLOQUE = "mudras.yml n'a pas pu être lu : modifications bloquées (voir la console, puis /sc reload).";
    private static final String ECRITURE = "mudras.yml ne s'écrit pas : rien n'est changé (voir la console).";
    /**
     * Réponse de {@link #essaiFermer} quand quelqu'un se tient dans le passage : rendue comme une erreur, mais la descente
     * est bien planifiée. L'outil du Poste de garde la montre comme une information.
     */
    static final String PASSAGE_OCCUPE = "Le passage est occupé : la dalle redescendra dès qu'il sera libre.";
    private static final String TROP_LOIN = "Trop loin : approche-toi à " + MudraRegles.VISEE_STAFF + " blocs.";
    private static final String SON_DALLE = "reborn:mudra.dalle", SON_CHOC = "reborn:mudra.choc";

    /** Un bloc de la dalle rendu par un affichage : l'entité et la place d'origine du bloc. */
    private record Piece(BlockDisplay aff, int x, int y, int z) { }

    /** Une dalle en mouvement (levée ou descente). */
    private static final class Mouvement {
        final PorteScellee porte;
        final boolean levee;
        final List<Piece> pieces;
        final int duree, leve;
        /** Descente : les blocs d'origine, dans l'ordre de {@link MudraRegles.Boite#indice}. */
        final BlockData[] origine;
        /** Ticks écoulés depuis le départ de l'interpolation. */
        int tick;
        int tache = -1;

        Mouvement(PorteScellee porte, boolean levee, List<Piece> pieces, int duree, int leve, BlockData[] origine) {
            this.porte = porte;
            this.levee = levee;
            this.pieces = pieces;
            this.duree = duree;
            this.leve = leve;
            this.origine = origine;
        }
    }

    /** Les deux coins posés par un staff ({@code /porte coin 1|2}), gardés en mémoire jusqu'à la création. */
    private static final class Coins {
        String monde;
        int[] a, b;
    }

    private final ShinobiCore plugin;
    private final PorteStore store;
    private MudraRegles.Reglages reglages = MudraRegles.Reglages.DEFAUT;
    private long seq;
    private BlockData barriere;
    /** Registre des dalles : monde, puis tronçon, puis portes qui le touchent. */
    private final Map<String, Map<Long, List<PorteScellee>>> index = new HashMap<>();
    /** Portes en mouvement : jamais deux animations sur la même. */
    private final Map<String, Mouvement> mouvements = new HashMap<>();
    /** Portes levées : les affichages restés en haut. */
    private final Map<String, List<Piece>> levees = new HashMap<>();
    /** Portes levées : tâche de la prochaine tentative de descente. */
    private final Map<String, Integer> fermetures = new HashMap<>();
    /**
     * Portes levées : instant (epoch ms) de la redescente prévue à la fin du maintien. Absent quand la dalle attend que
     * le passage se libère (réessai chaque seconde, échéance inconnue).
     */
    private final Map<String, Long> descenteLe = new HashMap<>();
    /** Portes {@code ouverte: true} dont la dalle n'a pas pu être remise (monde absent) : réessai au chargement du monde. */
    private final Set<String> aRemettre = new HashSet<>();
    private final Map<UUID, Coins> coins = new HashMap<>();
    /** Appelé après {@code /porte systeme} : le canal renvoie les règles et coupe les sessions. */
    private Runnable surBascule = () -> { };

    public PorteService(ShinobiCore plugin) {
        this.plugin = plugin;
        this.store = new PorteStore(plugin.getLogger(), new File(plugin.getDataFolder(), "mudras.yml"));
    }

    /* ================================================================ cycle de vie */

    public void demarrer(MudraRegles.Reglages r) {
        reglages = r;
        barriere = Material.BARRIER.createBlockData();
        store.charger();
        int remises = remettreOuvertes();
        reindexer();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Mudras : système " + (store.actif() ? "allumé" : "éteint") + ", " + store.portes.size()
                + " porte(s) scellée(s)" + (remises > 0 ? ", " + remises + " dalle(s) remise(s) en place" : "")
                + (store.bloque() ? " — FICHIER BLOQUÉ" : "") + ".");
    }

    /** Arrêt : toute porte en mouvement ou levée est refermée sans animation, affichages retirés. */
    public void arreter() {
        if (fermerTout() > 0) sauver();
        coins.clear();
    }

    /**
     * {@code /sc reload} : les portes en mouvement ou levées sont d'abord refermées sans animation (le fichier relu
     * remplace les objets en mémoire), puis {@code mudras.yml} est relu sous le verrou du store, comme les plaques.
     */
    public void recharger(MudraRegles.Reglages r) {
        reglages = r;
        fermerTout();
        store.recharger(store.serialiser(), ++seq);
        aRemettre.clear();
        remettreOuvertes();
        reindexer();
        plugin.getLogger().info("Mudras : système " + (store.actif() ? "allumé" : "éteint") + ", " + store.portes.size()
                + " porte(s) rechargée(s)" + (store.bloque() ? " — FICHIER BLOQUÉ" : "") + ".");
    }

    void surBascule(Runnable r) { surBascule = r == null ? () -> { } : r; }

    /** Écriture immédiate (les portes changent rarement, et la levée exige ses blocs sur le disque d'abord). */
    private boolean sauver() { return store.ecrire(store.serialiser(), ++seq); }

    /** L'interrupteur du système ({@code actif} de mudras.yml) : livré éteint. */
    public boolean actif() { return store.actif(); }

    /**
     * {@code mudras.yml} n'a pas pu être lu : créations, retouches, retraits et ouvertures échouent tant qu'il n'est pas
     * corrigé puis rechargé ({@code /sc reload}). L'outil du Poste de garde l'annonce d'avance.
     */
    public boolean bloque() { return store.bloque(); }

    public MudraRegles.Reglages reglages() { return reglages; }

    public Map<String, PorteScellee> portes() { return store.portes; }

    public PorteScellee get(String id) { return id == null ? null : store.portes.get(id.trim().toUpperCase(Locale.ROOT)); }

    /* ================================================================ registre */

    private static long cle(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    private void reindexer() {
        index.clear();
        for (PorteScellee d : store.portes.values()) {
            Map<Long, List<PorteScellee>> m = index.computeIfAbsent(d.monde, k -> new HashMap<>());
            MudraRegles.Boite b = d.boite;
            for (int cx = b.x() >> 4; cx <= (b.x() + b.dx() - 1) >> 4; cx++)
                for (int cz = b.z() >> 4; cz <= (b.z() + b.dz() - 1) >> 4; cz++)
                    m.computeIfAbsent(cle(cx, cz), k -> new ArrayList<>(1)).add(d);
        }
    }

    /** La porte dont la dalle contient ce bloc, ou null : une recherche par tronçon. */
    public PorteScellee porteEn(Block b) {
        if (index.isEmpty()) return null;
        Map<Long, List<PorteScellee>> m = index.get(b.getWorld().getName());
        if (m == null) return null;
        List<PorteScellee> l = m.get(cle(b.getX() >> 4, b.getZ() >> 4));
        if (l == null) return null;
        for (PorteScellee d : l) if (d.boite.contient(b.getX(), b.getY(), b.getZ())) return d;
        return null;
    }

    /** Fermée et immobile : la seule porte qu'une suite peut ouvrir. */
    public boolean fermee(PorteScellee d) { return !d.ouverte && !mouvements.containsKey(d.id); }

    /** État en clair pour le staff. */
    public String etat(PorteScellee d) { return etatDe(d).texte(); }

    /** État de la porte ({@link MudraRegles.Etat#code} part vers l'outil du Poste de garde). */
    public MudraRegles.Etat etatDe(PorteScellee d) {
        Mouvement m = mouvements.get(d.id);
        return MudraRegles.etat(m == null ? null : m.levee, aRemettre.contains(d.id), d.ouverte);
    }

    /**
     * Secondes avant la redescente d'une porte levée ; -1 si elle n'est pas levée, si la dalle attend que le passage
     * se libère (elle redescendra dès qu'il sera libre), ou si l'échéance est passée sans que la redescente soit partie
     * (elle est planifiée en ticks : un serveur sous 20 TPS la fait plus tard que l'horloge).
     */
    public int resteAvantDescente(PorteScellee d) {
        if (etatDe(d) != MudraRegles.Etat.LEVEE) return -1;
        long le = descenteLe.getOrDefault(d.id, 0L), now = System.currentTimeMillis();
        return le <= now ? -1 : MudraRegles.resteS(le, now);
    }

    public static Location lieu(PorteScellee d) {
        World w = Bukkit.getWorld(d.monde);
        if (w == null) return null;
        double[] c = d.boite.centre();
        return new Location(w, c[0], c[1], c[2]);
    }

    /** Distance des pieds du joueur à la dalle ; infinie dans un autre monde. */
    public static double distance(PorteScellee d, Player p) {
        if (!d.monde.equals(p.getWorld().getName())) return Double.POSITIVE_INFINITY;
        return d.boite.distance(p.getX(), p.getY(), p.getZ());
    }

    /** La porte la plus proche du joueur à {@code rayon} blocs au plus ({@code fermees} : fermées et immobiles seulement). */
    public PorteScellee plusProche(Player p, double rayon, boolean fermees) {
        PorteScellee mieux = null;
        double dm = rayon;
        for (PorteScellee d : store.portes.values()) {
            if (fermees && !fermee(d)) continue;
            double dist = distance(d, p);
            if (dist <= dm) { dm = dist; mieux = d; }
        }
        return mieux;
    }

    /**
     * La porte fermée et immobile du monde du joueur, à portée ({@code portes.portee}), dont la suite est <b>exactement</b>
     * celle composée ; la plus proche s'il y en a plusieurs. null sinon (et rien n'en est dit au client).
     */
    public PorteScellee correspondante(Player p, List<Integer> composee) {
        PorteScellee mieux = null;
        double dm = reglages.portee();
        for (PorteScellee d : store.portes.values()) {
            if (!fermee(d)) continue;
            double dist = distance(d, p);
            if (dist > dm || !MudraRegles.memeSuite(composee, MudraRegles.ordinaux(d.signes))) continue;
            dm = dist;
            mieux = d;
        }
        return mieux;
    }

    /** Toutes les portes, celles du monde du staff d'abord, de la plus proche à la plus lointaine. */
    public List<PorteScellee> liste(Player p) {
        List<PorteScellee> l = new ArrayList<>(store.portes.values());
        if (p != null) l.sort(Comparator.comparingDouble(d -> distance(d, p)));
        return l;
    }

    /* ================================================================ levée */

    /**
     * Lève la dalle. null si le mouvement est parti, sinon pourquoi il ne part pas. L'ordre compte : relevé des blocs,
     * écriture de {@code ouverte: true} + {@code blocs}, et seulement ensuite le monde.
     */
    private String lever(PorteScellee d) {
        if (store.bloque()) return BLOQUE;
        if (d.ouverte || mouvements.containsKey(d.id)) return "La porte " + d.id + " est déjà " + etat(d) + ".";
        World w = Bukkit.getWorld(d.monde);
        if (w == null) return "Monde absent : " + d.monde + ".";
        MudraRegles.Boite b = d.boite;
        int n = b.volume();
        List<String> blocs = new ArrayList<>(n);
        int nonVides = 0, etats = 0;
        for (int i = 0; i < n; i++) {
            Block bl = bloc(w, b, i);
            blocs.add(bl.getBlockData().getAsString());
            if (!bl.getType().isAir()) nonVides++;
            if (bl.getState(false) instanceof TileState) etats++;
        }
        // la dalle a pu changer depuis la pose (éditeur de monde) : jamais de coffre emporté, jamais une dalle d'air,
        // jamais un cadre qui tomberait en objet une fois son support parti
        String e = MudraRegles.validerContenu(nonVides, etats, accrochees(w, b));
        if (e != null) return "La dalle de " + d.id + " a changé : " + e;
        d.ouverte = true;
        d.blocs = List.copyOf(blocs);
        if (!sauver()) {
            d.ouverte = false;
            d.blocs = List.of();
            return "mudras.yml ne s'écrit pas : la porte " + d.id + " reste fermée (voir la console).";
        }
        List<Piece> pieces = new ArrayList<>(nonVides);
        for (int i = 0; i < n; i++) {
            Block bl = bloc(w, b, i);
            if (bl.getType().isAir()) continue;
            pieces.add(afficher(w, bl.getX(), bl.getY(), bl.getZ(), bl.getBlockData(), 0));
            bl.setBlockData(barriere, false);
        }
        partir(new Mouvement(d, true, pieces, reglages.dureeTicks(), d.leve, null), w);
        return null;
    }

    /** Redescend la dalle (porte levée, passage libre). */
    private String descendre(PorteScellee d, World w) {
        BlockData[] origine = lire(d);
        if (origine == null) return "Les blocs de " + d.id + " ne se relisent pas (voir la console).";
        List<Piece> hauts = levees.remove(d.id);
        if (hauts != null) retirer(hauts);
        MudraRegles.Boite b = d.boite;
        List<Piece> pieces = new ArrayList<>();
        for (int i = 0; i < origine.length; i++) {
            if (origine[i].getMaterial().isAir()) continue;
            int[] p = b.position(i);
            pieces.add(afficher(w, p[0], p[1], p[2], origine[i], d.leve));
        }
        partir(new Mouvement(d, false, pieces, reglages.dureeTicks(), d.leve, origine), w);
        return null;
    }

    private void partir(Mouvement m, World w) {
        mouvements.put(m.porte.id, m);
        son(w, m.porte, SON_DALLE);
        // un tick plus tard : les clients ont reçu les affichages, l'interpolation peut partir
        m.tache = Bukkit.getScheduler().runTaskTimer(plugin, () -> avancer(m), 1L, 1L).getTaskId();
    }

    /** Un affichage du bloc à sa place d'origine, décalé de {@code haut} blocs vers le haut ; jamais sauvé avec le monde. */
    private static Piece afficher(World w, int x, int y, int z, BlockData data, int haut) {
        BlockDisplay aff = w.spawn(new Location(w, x, y, z), BlockDisplay.class, bd -> {
            bd.setBlock(data);
            bd.setPersistent(false);
            if (haut != 0) decaler(bd, haut);
        });
        return new Piece(aff, x, y, z);
    }

    private static void decaler(BlockDisplay bd, int haut) {
        Transformation t = bd.getTransformation();
        bd.setTransformation(new Transformation(new Vector3f(0f, haut, 0f), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
    }

    private void avancer(Mouvement m) {
        PorteScellee d = m.porte;
        World w = Bukkit.getWorld(d.monde);
        if (w == null) {
            // monde déchargé en plein mouvement : rien à remettre maintenant, la dalle le sera au retour du monde
            Bukkit.getScheduler().cancelTask(m.tache);
            mouvements.remove(d.id);
            retirer(m.pieces);
            aRemettre.add(d.id);
            plugin.getLogger().warning("Porte " + d.id + " : monde « " + d.monde + " » absent en plein mouvement, dalle à remettre.");
            return;
        }
        int t = m.tick++;
        if (t == 0) {
            int vers = m.levee ? m.leve : 0;
            for (Piece p : m.pieces) {
                if (!p.aff.isValid()) continue;
                p.aff.setInterpolationDelay(0);
                p.aff.setInterpolationDuration(m.duree);
                decaler(p.aff, vers);
            }
        }
        MudraRegles.Boite b = d.boite;
        for (int r = 0; r < m.leve && r < b.dy(); r++) {
            int quand = m.levee ? MudraRegles.tickLiberation(r, m.duree, m.leve) : MudraRegles.tickPose(r, m.duree, m.leve);
            if (quand != t) continue;
            for (int x = b.x(); x < b.x() + b.dx(); x++) {
                for (int z = b.z(); z < b.z() + b.dz(); z++) {
                    Block bl = w.getBlockAt(x, b.y() + r, z);
                    if (m.levee) {
                        // le bas de la dalle a dépassé la rangée : le passage se libère
                        if (bl.getType() == Material.BARRIER) bl.setType(Material.AIR, false);
                    } else if (!m.origine[b.indice(x, b.y() + r, z)].getMaterial().isAir()) {
                        // la dalle va entrer dans la rangée : barrière d'abord, le bloc d'origine à la fin
                        bl.setBlockData(barriere, false);
                    }
                }
            }
        }
        if (t < m.duree) return;
        Bukkit.getScheduler().cancelTask(m.tache);
        mouvements.remove(d.id);
        if (m.levee) finLevee(m, w);
        else finDescente(m, w);
    }

    private void finLevee(Mouvement m, World w) {
        PorteScellee d = m.porte;
        List<Piece> hauts = new ArrayList<>();
        for (Piece p : m.pieces) {
            int y = p.y + m.leve;
            // arrivé dans un bloc plein (le linteau) : l'affichage ne se verrait pas, il part ; les autres restent en haut
            boolean cache = y >= w.getMinHeight() && y < w.getMaxHeight() && w.getBlockAt(p.x, y, p.z).getType().isOccluding();
            if (cache) { if (p.aff.isValid()) p.aff.remove(); }
            else hauts.add(p);
        }
        levees.put(d.id, hauts);
        son(w, d, SON_CHOC);
        planifierDescente(d, reglages.ouverteS() * 20L, true);
    }

    private void finDescente(Mouvement m, World w) {
        PorteScellee d = m.porte;
        // les blocs d'origine d'abord, les affichages ensuite : aucun trou à l'écran
        if (remettre(d)) {
            d.ouverte = false;
            d.blocs = List.of();
            sauver();
        } else {
            aRemettre.add(d.id);
        }
        retirer(m.pieces);
        son(w, d, SON_CHOC);
    }

    /**
     * {@code maintien} : la fin du maintien normal, dont l'échéance se lit ({@link #resteAvantDescente}) ; faux pour un
     * réessai, passage occupé.
     */
    private void planifierDescente(PorteScellee d, long delai, boolean maintien) {
        annulerDescente(d.id);
        if (maintien) descenteLe.put(d.id, System.currentTimeMillis() + delai * 50L);
        fermetures.put(d.id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            fermetures.remove(d.id);
            descenteLe.remove(d.id);
            tenterDescente(d);
        }, delai).getTaskId());
    }

    private void annulerDescente(String id) {
        descenteLe.remove(id);
        Integer t = fermetures.remove(id);
        if (t != null) Bukkit.getScheduler().cancelTask(t);
    }

    /**
     * Essaie de redescendre une porte levée : null si le mouvement part. Tant qu'un joueur ou une entité vivante recoupe
     * la boîte de la dalle, réessai chaque seconde.
     */
    private String tenterDescente(PorteScellee d) {
        if (store.portes.get(d.id) != d || !d.ouverte || mouvements.containsKey(d.id) || aRemettre.contains(d.id))
            return "La porte " + d.id + " n'est pas levée.";
        World w = Bukkit.getWorld(d.monde);
        if (w == null) {
            List<Piece> hauts = levees.remove(d.id);
            if (hauts != null) retirer(hauts);
            aRemettre.add(d.id);
            plugin.getLogger().warning("Porte " + d.id + " : monde « " + d.monde + " » absent, dalle à remettre à son retour.");
            return "Monde absent : " + d.monde + ".";
        }
        if (occupee(d, w)) {
            planifierDescente(d, 20L, false);
            return PASSAGE_OCCUPE;
        }
        annulerDescente(d.id);
        return descendre(d, w);
    }

    /** Un joueur ou une entité vivante recoupe la boîte de la dalle (spectateurs et supports d'armure ignorés). */
    private static boolean occupee(PorteScellee d, World w) {
        for (Entity e : w.getNearbyEntities(volume(d.boite))) {
            if (!(e instanceof LivingEntity l) || e instanceof ArmorStand || l.isDead()) continue;
            if (e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
            return true;
        }
        return false;
    }

    private static Block bloc(World w, MudraRegles.Boite b, int i) {
        int[] p = b.position(i);
        return w.getBlockAt(p[0], p[1], p[2]);
    }

    private static BoundingBox volume(MudraRegles.Boite b) {
        return new BoundingBox(b.x(), b.y(), b.z(), b.x() + b.dx(), b.y() + b.dy(), b.z() + b.dz());
    }

    /**
     * Entités accrochées à un bloc de la dalle : cadres, tableaux, nœuds de laisse. Elles ne sont pas des blocs (aucun
     * {@code TileState}) mais tiennent à leur support : dès que la rangée passe à l'air, elles se décrochent et tombent en
     * objet, avec leur contenu. Une dalle qui en porte n'est donc ni créée ni levée.
     */
    private static int accrochees(World w, MudraRegles.Boite b) {
        BoundingBox dalle = volume(b);
        int n = 0;
        // un tableau fait jusqu'à 4 blocs de large : on cherche un bloc autour, puis on regarde le support de chacun
        for (Entity e : w.getNearbyEntities(dalle.clone().expand(1.0), x -> x instanceof Hanging))
            if (supportDans((Hanging) e, dalle)) n++;
        return n;
    }

    /**
     * Le support de l'entité accrochée est-il dans la dalle ? Un cadre ou un tableau est une plaque fine posée contre la
     * face de son support : décalée d'un demi-bloc vers lui, elle est tout entière dedans. Un nœud de laisse est dans son
     * support (face {@code SELF}, décalage nul).
     */
    private static boolean supportDans(Hanging h, BoundingBox dalle) {
        BlockFace f = h.getAttachedFace();
        return h.getBoundingBox().shift(f.getModX() * 0.5, f.getModY() * 0.5, f.getModZ() * 0.5).overlaps(dalle);
    }

    private static void retirer(List<Piece> pieces) {
        for (Piece p : pieces) if (p.aff != null && p.aff.isValid()) p.aff.remove();
    }

    private static void son(World w, PorteScellee d, String cle) {
        double[] c = d.boite.centre();
        w.playSound(new Location(w, c[0], c[1], c[2]), cle, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /* ================================================================ remise en place */

    /** Les blocs d'origine de la porte ({@code blocs}), ou null s'ils ne se relisent pas (journal de la console). */
    private BlockData[] lire(PorteScellee d) {
        int n = d.boite.volume();
        if (d.blocs.size() != n) {
            plugin.getLogger().severe("Porte " + d.id + " : " + d.blocs.size() + " bloc(s) notés pour une dalle de " + n
                    + " : dalle non remise, à corriger dans mudras.yml.");
            return null;
        }
        BlockData[] data = new BlockData[n];
        try {
            for (int i = 0; i < n; i++) data[i] = Bukkit.createBlockData(d.blocs.get(i));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Porte " + d.id + " : bloc illisible dans mudras.yml (" + e.getMessage() + ") : dalle non remise.");
            return null;
        }
        return data;
    }

    /**
     * Remet les blocs d'origine de la dalle, sans physique. Faux si rien n'a pu être remis (monde absent, liste
     * illisible) : la porte garde {@code ouverte: true} pour un nouvel essai.
     */
    private boolean remettre(PorteScellee d) {
        World w = Bukkit.getWorld(d.monde);
        if (w == null) {
            plugin.getLogger().warning("Porte " + d.id + " : monde « " + d.monde + " » absent, dalle non remise (nouvel essai au"
                    + " chargement du monde ou au prochain démarrage).");
            return false;
        }
        BlockData[] data = lire(d);
        if (data == null) return false;
        for (int i = 0; i < data.length; i++) bloc(w, d.boite, i).setBlockData(data[i], false);
        return true;
    }

    /** Démarrage, rechargement : toute porte {@code ouverte: true} est remise tout de suite. Rend le nombre de dalles remises. */
    private int remettreOuvertes() {
        int n = 0;
        for (PorteScellee d : store.portes.values()) {
            if (!d.ouverte) continue;
            if (remettre(d)) {
                d.ouverte = false;
                d.blocs = List.of();
                n++;
            } else {
                aRemettre.add(d.id);
            }
        }
        if (n > 0) sauver();
        return n;
    }

    /** Referme sans animation une porte en mouvement ou levée ; vrai si son état a changé. */
    private boolean fermerNet(PorteScellee d) {
        Mouvement m = mouvements.remove(d.id);
        if (m != null) {
            Bukkit.getScheduler().cancelTask(m.tache);
            retirer(m.pieces);
        }
        annulerDescente(d.id);
        List<Piece> hauts = levees.remove(d.id);
        if (hauts != null) retirer(hauts);
        if (!d.ouverte) return false;
        if (!remettre(d)) {
            aRemettre.add(d.id);
            return false;
        }
        d.ouverte = false;
        d.blocs = List.of();
        aRemettre.remove(d.id);
        return true;
    }

    /** Referme sans animation toutes les portes en mouvement ou levées ; rend le nombre de dalles remises en place. */
    private int fermerTout() {
        int n = 0;
        for (PorteScellee d : new ArrayList<>(store.portes.values())) if (fermerNet(d)) n++;
        return n;
    }

    /** Un monde chargé après le démarrage : les dalles qui l'attendaient sont remises. */
    @EventHandler
    public void mondeCharge(WorldLoadEvent e) {
        if (aRemettre.isEmpty()) return;
        String monde = e.getWorld().getName();
        boolean change = false;
        for (PorteScellee d : store.portes.values()) {
            if (!d.monde.equals(monde) || !aRemettre.contains(d.id)) continue;
            if (remettre(d)) {
                d.ouverte = false;
                d.blocs = List.of();
                aRemettre.remove(d.id);
                change = true;
            }
        }
        if (change) sauver();
    }

    /* ================================================================ joueurs */

    /**
     * Une suite reconnue : la porte se lève pour le joueur ({@code INTERACTION} / {@code PORTE_OUVERTE}). Faux si elle ne
     * part pas (fichier bloqué, dalle changée) : dit à la console, jamais au joueur.
     */
    public boolean ouvrirPar(Player p, PorteScellee d) {
        String e = lever(d);
        if (e != null) {
            plugin.getLogger().warning("Porte " + d.id + " : suite reconnue de " + p.getName() + " mais la dalle ne part pas (" + e + ")");
            return false;
        }
        journal(JournalService.Categorie.INTERACTION, "PORTE_OUVERTE", p, d, d.libelle());
        return true;
    }

    /* ================================================================ staff */

    /** Résultat d'une action du staff : la porte, ou l'erreur à lui montrer. */
    public record Resultat(PorteScellee porte, String erreur) {
        static Resultat ko(String e) { return new Resultat(null, e); }

        public boolean ok() { return erreur == null; }
    }

    /** Grade de l'auteur d'une commande : la console vaut Owner. */
    public static int grade(CommandSender s) {
        if (s instanceof Player p) return StaffGrades.of(p);
        return s instanceof ConsoleCommandSender ? StaffGrades.OWNER : StaffGrades.NONE;
    }

    /** {@code /porte coin 1|2} : le bloc visé (8 blocs) devient un coin de la future dalle. */
    public String coin(Player s, int n) {
        if (!MudraRegles.peutGerer(StaffGrades.of(s))) return refus(s, "coin", "Réservé aux modérateurs.").erreur();
        Block b = s.getTargetBlockExact(MudraRegles.VISEE_STAFF);
        if (b == null) return "Vise un bloc (" + MudraRegles.VISEE_STAFF + " blocs au plus).";
        Coins c = coins.computeIfAbsent(s.getUniqueId(), k -> new Coins());
        String monde = b.getWorld().getName();
        if (!monde.equals(c.monde)) { c.monde = monde; c.a = null; c.b = null; }
        int[] pos = {b.getX(), b.getY(), b.getZ()};
        if (n == 1) c.a = pos;
        else c.b = pos;
        return null;
    }

    /** La boîte entre les deux coins du staff, ou null s'il en manque un. */
    public MudraRegles.Boite boiteDesCoins(Player s) {
        Coins c = coins.get(s.getUniqueId());
        if (c == null || c.a == null || c.b == null) return null;
        return MudraRegles.Boite.entre(c.a[0], c.a[1], c.a[2], c.b[0], c.b[1], c.b[2]);
    }

    /** {@code /porte creer} : la dalle entre les deux coins devient une porte scellée. */
    public Resultat creer(Player s, String nom, List<Mudra> signes) {
        if (store.bloque()) return Resultat.ko(BLOQUE);
        if (!MudraRegles.peutGerer(StaffGrades.of(s))) return refus(s, "créer", "Réservé aux modérateurs.");
        Coins c = coins.get(s.getUniqueId());
        if (c == null || c.a == null || c.b == null)
            return Resultat.ko("Pose d'abord les deux coins de la dalle : /porte coin 1, puis /porte coin 2 (vise un bloc).");
        World w = s.getWorld();
        if (!w.getName().equals(c.monde)) return Resultat.ko("Les deux coins sont dans un autre monde : repose-les ici.");
        MudraRegles.Boite b = MudraRegles.Boite.entre(c.a[0], c.a[1], c.a[2], c.b[0], c.b[1], c.b[2]);
        String e = dalleRefusee(w, b, null);
        if (e != null) return Resultat.ko(e);
        Resultat r = sceller(s, w, b, nom, signes, 0);
        if (r.ok()) coins.remove(s.getUniqueId());
        return r;
    }

    /**
     * Outil du Poste de garde : la dalle choisie à la souris devient une porte scellée. La boîte vient du client, tout est
     * donc revérifié ici : elle est dans le monde du staff, à {@link MudraRegles#VISEE_STAFF} + 1 blocs au plus de ses
     * yeux, puis les mêmes contrôles que {@code /porte creer}. {@code leve} : 0 = toute la hauteur de la dalle.
     */
    public Resultat creer(Player s, MudraRegles.Boite b, String nom, List<Mudra> signes, int leve) {
        if (store.bloque()) return Resultat.ko(BLOQUE);
        if (!MudraRegles.peutGerer(StaffGrades.of(s))) return refus(s, "créer", "Réservé aux modérateurs.");
        if (b == null) return Resultat.ko("Choisis d'abord la dalle.");
        String e = dalleRefusee(s.getWorld(), b, s.getEyeLocation());
        if (e != null) return Resultat.ko(e);
        if (MudraRegles.leveDemandee(leve, b.dy()) < 0)
            return Resultat.ko("Levée : de 1 à " + b.dy() + " blocs (la hauteur de la dalle).");
        return sceller(s, s.getWorld(), b, nom, signes == null ? List.of() : signes, leve);
    }

    /**
     * Outil du Poste de garde : cette dalle pourrait-elle devenir une porte ? null si oui, sinon la raison (taille,
     * distance, contenu, recoupe). Rien n'est créé ni journalisé : l'outil le demande à chaque dalle dessinée.
     */
    public String verifier(Player s, MudraRegles.Boite b) {
        if (!MudraRegles.peutGerer(StaffGrades.of(s))) return "Réservé aux modérateurs.";
        if (b == null) return "Choisis d'abord la dalle.";
        return dalleRefusee(s.getWorld(), b, s.getEyeLocation());
    }

    /**
     * Pourquoi cette boîte ne peut pas devenir une dalle, null si elle le peut : taille, distance aux yeux du staff
     * ({@code yeux} non null : la boîte vient de l'outil), place dans le monde, contenu, recoupe d'une autre porte. La
     * taille et la distance passent avant toute lecture de bloc.
     */
    private String dalleRefusee(World w, MudraRegles.Boite b, Location yeux) {
        String e = MudraRegles.validerBoite(b.dx(), b.dy(), b.dz());
        if (e != null) return e;
        if (yeux != null && MudraRegles.tropLoin(b, yeux.getX(), yeux.getY(), yeux.getZ())) return TROP_LOIN;
        if (b.y() < w.getMinHeight() || b.y() + b.dy() > w.getMaxHeight()) return "Dalle hors du monde.";
        int nonVides = 0, etats = 0;
        for (int i = 0; i < b.volume(); i++) {
            Block bl = bloc(w, b, i);
            if (!bl.getType().isAir()) nonVides++;
            if (bl.getState(false) instanceof TileState) etats++;
        }
        e = MudraRegles.validerContenu(nonVides, etats, accrochees(w, b));
        if (e != null) return e;
        for (PorteScellee o : store.portes.values())
            if (o.monde.equals(w.getName()) && o.boite.recoupe(b)) return "Cette dalle recoupe la porte " + o.id + ".";
        return null;
    }

    /**
     * La dalle, déjà vérifiée ({@link #dalleRefusee}), devient une porte : nom, suite, écriture, journal. {@code leve} :
     * 0 = toute la hauteur.
     */
    private Resultat sceller(Player s, World w, MudraRegles.Boite b, String nom, List<Mudra> signes, int leve) {
        String n = MudraRegles.nom(nom);
        if (n.isEmpty()) return Resultat.ko("Donne un nom à la porte.");
        String e = MudraRegles.validerSuite(signes.size());
        if (e != null) return Resultat.ko(e);
        long now = System.currentTimeMillis();
        PorteScellee d = new PorteScellee(store.nouvelId());
        d.nom = n;
        d.monde = w.getName();
        d.boite = b;
        d.signes = List.copyOf(signes);
        d.leve = MudraRegles.bornerLeve(leve, b.dy());
        d.creeLe = now;
        d.creePar = s.getUniqueId();
        d.creePseudo = s.getName();
        store.portes.put(d.id, d);
        reindexer();
        if (!sauver()) {
            // jamais une porte « créée » qui disparaîtrait au redémarrage : rien n'est gardé, les coins restent posés
            store.portes.remove(d.id);
            reindexer();
            return Resultat.ko(ECRITURE);
        }
        // la suite elle-même ne va ni au tchat public ni au journal (lu des Helpers) : son nombre de signes seulement.
        // La ligne de commande, journalisée à part (JournalEcouteur), y passe masquée : MudraRegles.masquerCommande.
        journal(JournalService.Categorie.STAFF, "PORTE_POSEE", s, d, d.libelle() + " · " + b.libelle(d.monde) + " · "
                + d.signes.size() + " signes · levée " + d.leve);
        return new Resultat(d, null);
    }

    public Resultat signes(CommandSender s, String id, List<Mudra> signes) {
        Resultat r = modifiable(s, id, "changer la suite de");
        if (!r.ok()) return r;
        PorteScellee d = r.porte();
        String e = MudraRegles.validerSuite(signes.size());
        if (e != null) return Resultat.ko(e);
        List<Mudra> avant = d.signes;
        d.signes = List.copyOf(signes);
        return modifiee(s, r, "suite changée (" + avant.size() + " → " + d.signes.size() + " signes)", () -> d.signes = avant);
    }

    public Resultat nom(CommandSender s, String id, String nom) {
        Resultat r = modifiable(s, id, "renommer");
        if (!r.ok()) return r;
        PorteScellee d = r.porte();
        String n = MudraRegles.nom(nom);
        if (n.isEmpty()) return Resultat.ko("Donne un nom à la porte.");
        String avant = d.nom;
        d.nom = n;
        return modifiee(s, r, "nom « " + avant + " » → « " + n + " »", () -> d.nom = avant);
    }

    public Resultat leve(CommandSender s, String id, int n) {
        Resultat r = modifiable(s, id, "changer la levée de");
        if (!r.ok()) return r;
        PorteScellee d = r.porte();
        if (!fermee(d)) return Resultat.ko("La porte " + d.id + " doit être fermée (elle " + etat(d) + ").");
        if (n < 1 || n > d.boite.dy()) return Resultat.ko("Levée : de 1 à " + d.boite.dy() + " blocs (la hauteur de la dalle).");
        int avant = d.leve;
        d.leve = n;
        return modifiee(s, r, "levée " + avant + " → " + n, () -> d.leve = avant);
    }

    private Resultat modifiable(CommandSender s, String id, String quoi) {
        if (store.bloque()) return Resultat.ko(BLOQUE);
        PorteScellee d = get(id);
        if (d == null) return Resultat.ko("Porte introuvable : " + id + ".");
        if (!MudraRegles.peutGerer(grade(s))) return refus(s, quoi + " " + d.id, "Réservé aux modérateurs.");
        return new Resultat(d, null);
    }

    /**
     * Une retouche déjà posée en mémoire : écrite tout de suite, puis journalisée. Si {@code mudras.yml} ne s'écrit pas,
     * {@code annuler} la défait (l'ancienne valeur reviendrait de toute façon au redémarrage) et le staff lit l'erreur :
     * jamais un « enregistré » pour un changement qui ne tient pas.
     */
    private Resultat modifiee(CommandSender s, Resultat r, String quoi, Runnable annuler) {
        PorteScellee d = r.porte();
        long le = d.modifLe;
        UUID par = d.modifPar;
        String pseudo = d.modifPseudo;
        d.changer(s instanceof Player p ? p.getUniqueId() : null, s.getName(), System.currentTimeMillis());
        if (!sauver()) {
            annuler.run();
            d.modifLe = le;
            d.modifPar = par;
            d.modifPseudo = pseudo;
            return Resultat.ko(ECRITURE);
        }
        journal(JournalService.Categorie.STAFF, "PORTE_MODIFIEE", s, d, d.libelle() + " · " + quoi);
        return r;
    }

    /** {@code /porte retirer} : porte fermée seulement ; les blocs restent en place. */
    public Resultat retirer(CommandSender s, String id) {
        Resultat r = modifiable(s, id, "retirer");
        if (!r.ok()) return r;
        PorteScellee d = r.porte();
        if (!fermee(d)) return Resultat.ko("La porte " + d.id + " doit être fermée (elle " + etat(d) + ") : /porte fermer " + d.id + ".");
        Map<String, PorteScellee> avant = new LinkedHashMap<>(store.portes);
        store.portes.remove(d.id);
        reindexer();
        if (!sauver()) {
            // la porte reviendrait du fichier au redémarrage, avec sa suite et sa protection : elle reste, à sa place
            store.portes.clear();
            store.portes.putAll(avant);
            reindexer();
            return Resultat.ko(ECRITURE);
        }
        journal(JournalService.Categorie.STAFF, "PORTE_RETIREE", s, d, d.libelle() + " · " + d.boite.libelle(d.monde)
                + " · les blocs restent en place");
        return r;
    }

    /** {@code /porte ouvrir} : essai du staff, sans suite ({@code STAFF} / {@code PORTE_ESSAI}). */
    public Resultat essaiOuvrir(CommandSender s, String id) {
        PorteScellee d = get(id);
        if (d == null) return Resultat.ko("Porte introuvable : " + id + ".");
        if (!MudraRegles.peutGerer(grade(s))) return refus(s, "ouvrir " + d.id, "Réservé aux modérateurs.");
        if (!store.actif()) return Resultat.ko("Système éteint : les portes restent fermées (/porte systeme on).");
        String e = lever(d);
        if (e != null) return Resultat.ko(e);
        journal(JournalService.Categorie.STAFF, "PORTE_ESSAI", s, d, d.libelle() + " · ouverte par le staff");
        return new Resultat(d, null);
    }

    /** {@code /porte fermer} : essai du staff ; passage occupé = la dalle attend qu'il se libère. */
    public Resultat essaiFermer(CommandSender s, String id) {
        PorteScellee d = get(id);
        if (d == null) return Resultat.ko("Porte introuvable : " + id + ".");
        if (!MudraRegles.peutGerer(grade(s))) return refus(s, "fermer " + d.id, "Réservé aux modérateurs.");
        if (aRemettre.contains(d.id)) {
            // dalle restée en l'air faute de monde : nouvel essai de remise, sans animation
            if (!remettre(d)) return Resultat.ko("La dalle de " + d.id + " ne peut pas être remise (voir la console).");
            d.ouverte = false;
            d.blocs = List.of();
            aRemettre.remove(d.id);
            sauver();
        } else {
            if (mouvements.containsKey(d.id)) return Resultat.ko("La porte " + d.id + " " + etat(d) + " : attends la fin du mouvement.");
            if (!d.ouverte) return Resultat.ko("La porte " + d.id + " est déjà fermée.");
            String e = tenterDescente(d);
            if (e != null) return Resultat.ko(e);
        }
        journal(JournalService.Categorie.STAFF, "PORTE_ESSAI", s, d, d.libelle() + " · refermée par le staff");
        return new Resultat(d, null);
    }

    /** {@code /porte systeme on|off} : l'interrupteur, écrit tout de suite, journalisé, renvoyé à tous les joueurs. */
    public String systeme(CommandSender s, boolean on) {
        if (!MudraRegles.peutSysteme(grade(s))) return refus(s, "système " + (on ? "on" : "off"), "Réservé aux admins.").erreur();
        if (store.bloque()) return BLOQUE;
        if (store.actif == on) return "Le système est déjà " + (on ? "allumé" : "éteint") + ".";
        store.actif = on;
        if (!sauver()) {
            store.actif = !on;
            return ECRITURE;
        }
        // éteint, les portes restent fermées : celles qui montent ou sont levées se referment tout de suite, sans
        // animation, comme à l'arrêt (sinon une dalle finirait sa levée et resterait ouverte, système éteint)
        int refermees = on ? 0 : fermerTout();
        if (refermees > 0) sauver();
        if (plugin.journal() != null) {
            plugin.journal().entree(JournalService.Categorie.STAFF, "MUDRAS_SYSTEME").acteur(s).cibleTexte("mudras")
                    .detail(on ? "système allumé" : "système éteint" + (refermees > 0 ? " · " + refermees + " porte(s) refermée(s)" : ""))
                    .ecrire();
        }
        surBascule.run();
        return null;
    }

    /**
     * Téléporte le staff devant la dalle, regard sur elle, sur une place sûre (pieds et tête libres, sol plein dessous) ;
     * inscrit au journal {@code STAFF} ({@code PORTE_TP}). Helper et plus.
     */
    public Resultat teleporter(Player s, String id) {
        PorteScellee d = get(id);
        if (d == null) return Resultat.ko("Porte introuvable : " + id + ".");
        if (!MudraRegles.peutLister(StaffGrades.of(s))) return refus(s, "téléport " + d.id, "Réservé au staff (Helper et plus).");
        World w = Bukkit.getWorld(d.monde);
        if (w == null) return Resultat.ko("Monde absent : " + d.monde + ".");
        MudraRegles.Boite b = d.boite;
        double[] c = b.centre();
        // devant la dalle : le long de son côté le plus fin
        Vector dir = s.getWorld().equals(w) ? new Vector(s.getX() - c[0], 0, s.getZ() - c[2]) : new Vector(0, 0, 0);
        if (b.dx() < b.dz()) dir = new Vector(dir.getX() < 0 ? -1 : 1, 0, 0);
        else if (b.dz() < b.dx()) dir = new Vector(0, 0, dir.getZ() < 0 ? -1 : 1);
        else if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        dir.normalize();
        Location to = placeSure(w, c, b, dir, Math.min(b.dx(), b.dz()) / 2.0 + 2.5);
        if (to == null) return Resultat.ko("Aucune place libre devant la porte " + d.id + " : /porte info " + d.id + " pour ses coordonnées.");
        to.setDirection(new Vector(c[0] - to.getX(), c[1] - (to.getY() + 1.6), c[2] - to.getZ()));
        if (plugin.outilsStaff() != null) plugin.outilsStaff().memoriser(s);
        s.teleport(to);
        if (plugin.journal() != null) {
            plugin.journal().entree(JournalService.Categorie.STAFF, "PORTE_TP").acteur(s).cibleTexte("porte " + d.id)
                    .detail(d.libelle() + " · " + b.libelle(d.monde)).lieu(to).ecrire();
        }
        return new Resultat(d, null);
    }

    /**
     * Place où poser les pieds à {@code recul} blocs du centre dans la direction {@code dir} (puis en tournant autour de
     * la dalle par pas de 45°) : bloc des pieds et de la tête passables, bloc dessous plein ; null si rien de sûr.
     */
    private static Location placeSure(World w, double[] c, MudraRegles.Boite b, Vector dir, double recul) {
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(45.0 * ((k + 1) / 2) * (k % 2 == 0 ? 1 : -1));
            double dx = dir.getX() * Math.cos(a) - dir.getZ() * Math.sin(a), dz = dir.getX() * Math.sin(a) + dir.getZ() * Math.cos(a);
            int bx = (int) Math.floor(c[0] + dx * recul), bz = (int) Math.floor(c[2] + dz * recul);
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, 5, 6}) {
                int y = b.y() + dy;
                if (y <= w.getMinHeight() || y + 1 >= w.getMaxHeight()) continue;
                Block pieds = w.getBlockAt(bx, y, bz), tete = w.getBlockAt(bx, y + 1, bz), sol = w.getBlockAt(bx, y - 1, bz);
                if (pieds.isPassable() && !pieds.isLiquid() && tete.isPassable() && !tete.isLiquid() && sol.getType().isSolid())
                    return new Location(w, bx + 0.5, y, bz + 0.5);
            }
        }
        return null;
    }

    /** Refus de grade : {@code STAFF} / {@code PORTE_REFUS}, puis le message rendu au staff. */
    Resultat refus(CommandSender s, String quoi, String msg) {
        if (plugin.journal() != null) {
            JournalService.Entree e = plugin.journal().entree(JournalService.Categorie.STAFF, "PORTE_REFUS").acteur(s)
                    .detail(quoi + " · grade " + StaffGrades.name(grade(s)));
            if (s instanceof Player p) e.lieu(p.getLocation());
            e.ecrire();
        }
        return Resultat.ko(msg);
    }

    private void journal(JournalService.Categorie cat, String action, CommandSender s, PorteScellee d, String detail) {
        if (plugin.journal() == null) return;
        JournalService.Entree e = plugin.journal().entree(cat, action).acteur(s).cibleTexte("porte " + d.id).detail(detail);
        Location l = lieu(d);
        if (l != null) e.lieu(l);
        e.ecrire();
    }

    /* ================================================================ protection des dalles */

    private boolean protege(Block b) { return porteEn(b) != null; }

    /** Le staff apprend pourquoi son bloc ne part pas ; un joueur ne reçoit rien (aucun indice). */
    private static void prevenir(Player p, PorteScellee d) {
        if (StaffGrades.of(p) < StaffGrades.HELPER) return;
        p.sendActionBar(Component.text("Dalle de la porte scellée " + d.id + " : /porte retirer " + d.id + " pour la défaire.",
                NamedTextColor.GRAY));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void casse(BlockBreakEvent e) {
        PorteScellee d = porteEn(e.getBlock());
        if (d == null) return;
        e.setCancelled(true);
        prevenir(e.getPlayer(), d);
    }

    /** Rien ne se pose dans le passage d'une porte levée : le bloc serait écrasé par la dalle à la descente. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void pose(BlockPlaceEvent e) {
        PorteScellee d = porteEn(e.getBlock());
        if (d == null) return;
        e.setCancelled(true);
        prevenir(e.getPlayer(), d);
    }

    /** Rien ne s'accroche à une dalle (cadre, tableau, nœud de laisse) : l'entité tomberait à la première levée. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void accroche(HangingPlaceEvent e) {
        PorteScellee d = porteEn(e.getBlock());
        if (d == null) return;
        e.setCancelled(true);
        if (e.getPlayer() != null) prevenir(e.getPlayer(), d);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) {
        if (!index.isEmpty()) e.blockList().removeIf(this::protege);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void explosion(BlockExplodeEvent e) {
        if (!index.isEmpty()) e.blockList().removeIf(this::protege);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) {
        if (index.isEmpty()) return;
        if (protege(e.getBlock().getRelative(e.getDirection())) || pistonTouche(e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent e) {
        if (index.isEmpty()) return;
        if (pistonTouche(e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    /** Un bloc poussé ou tiré sort d'une dalle, ou y entre. */
    private boolean pistonTouche(List<Block> blocs, BlockFace dir) {
        for (Block b : blocs) if (protege(b) || protege(b.getRelative(dir))) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void feu(BlockBurnEvent e) {
        if (protege(e.getBlock())) e.setCancelled(true);
    }

    /** Sable qui tombe, enderman, piétinement : rien ne change un bloc d'une dalle. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void entite(EntityChangeBlockEvent e) {
        if (protege(e.getBlock())) e.setCancelled(true);
    }

    /** L'eau et la lave ne coulent pas dans le passage d'une porte levée. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void coulee(BlockFromToEvent e) {
        if (index.isEmpty()) return;
        if (protege(e.getToBlock())) e.setCancelled(true);
    }

    @EventHandler
    public void quitter(PlayerQuitEvent e) {
        coins.remove(e.getPlayer().getUniqueId());
    }
}
