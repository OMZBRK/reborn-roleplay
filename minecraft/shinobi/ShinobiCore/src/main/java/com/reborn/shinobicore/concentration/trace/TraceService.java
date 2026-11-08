package com.reborn.shinobicore.concentration.trace;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.concentration.ConcentrationCanal;
import com.reborn.shinobicore.etat.Verrous;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Traces de pas</b> (lot 4, étape C, planche c7, décision du 08/11) : le serveur est le seul à savoir qui est passé.
 * Toutes les 4 ticks, un point tous les 1,2 bloc au sol (sprint 2, accroupi 0,8, chargé 1) pour chaque joueur qui marque
 * ({@link TraceRegles#marque}) ; en mémoire seulement, anneaux par tronçon et plafond global ({@link TraceStore}). Un saut
 * ne coupe pas la marche : un pas à l'atterrissage, guetté à chaque tick pour les seuls joueurs en l'air (courir en sautant
 * ne touche le sol que 1 à 2 ticks sur 13, le relevé le raterait). Le sprint se lit à la vitesse réelle, pas au drapeau de
 * sprint Minecraft (trot ou sprint).
 *
 * <p>Envoyées <b>aux seuls joueurs concentrés</b> : complètes à l'entrée puis en différentiel chaque seconde
 * ({@link TraceProtocole}). Un joueur voit les traces des autres, pas les siennes ; aucun coût, aucun nom, une clé de piste
 * par (marcheur, spectateur, jour). Personne ne sait qu'un joueur les regarde. Sang des blessés : option serveur, coupée
 * au départ.
 *
 * <p>La durée de vie est fixée à la pose (sol, météo à cet endroit, accroupi) : une pluie qui arrive après n'efface pas les
 * vieilles traces plus vite. {@code concentration.traces.actif: false} (rechargé) arrête tout dans la seconde : relevé, envoi,
 * mémoire vidée, et les traces disparaissent de l'écran des concentrés.
 */
public final class TraceService implements Listener {

    /** Une piste pour un spectateur : un marcheur, le jour de naissance du pas. */
    private record Piste(UUID marcheur, long jour) { }

    /** Ce qu'un joueur concentré a déjà reçu. */
    private static final class Vue {
        final String monde;
        final Set<Integer> connus = new HashSet<>();
        final Map<Piste, Integer> cles = new HashMap<>();

        Vue(String monde) { this.monde = monde; }
    }

    private final ShinobiCore plugin;
    private final ConcentrationCanal canal;
    private final long sel = new SecureRandom().nextLong();
    private final Map<UUID, TraceMarche> marches = new HashMap<>();
    private final Map<UUID, Vue> vues = new HashMap<>();
    /** Les joueurs en l'air au dernier relevé : leur atterrissage est guetté à chaque tick. */
    private final Set<UUID> enLair = new HashSet<>();
    private final List<Player> atterris = new ArrayList<>();
    private final double[] points = new double[3 * TraceMarche.MAX_PAR_RELEVE], pied = new double[3];
    private final List<Trace> selection = new ArrayList<>();
    private TraceStore store = new TraceStore(TraceRegles.MAX_TRONCON, TraceRegles.MAX_GLOBAL);
    private int tacheReleve = -1, tacheEnvoi = -1, tacheGuet = -1;
    /** Le réglage {@code actif} coupé a déjà été appliqué. */
    private boolean coupe;
    /** Surface trouvée par {@link #dessus} (hauteur, matériau). */
    private double surfaceY;
    private String surfaceMat;

    public TraceService(ShinobiCore plugin, ConcentrationCanal canal) {
        this.plugin = plugin;
        this.canal = canal;
    }

    public void demarrer() {
        store = nouveauStore();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tacheReleve = Bukkit.getScheduler().runTaskTimer(plugin, this::relever, TraceRegles.RELEVE_TICKS, TraceRegles.RELEVE_TICKS).getTaskId();
        tacheEnvoi = Bukkit.getScheduler().runTaskTimer(plugin, this::diffuser, TraceRegles.ENVOI_TICKS, TraceRegles.ENVOI_TICKS).getTaskId();
        tacheGuet = Bukkit.getScheduler().runTaskTimer(plugin, this::guetter, 1L, 1L).getTaskId();
    }

    private TraceStore nouveauStore() {
        var c = plugin.getConfig();
        return new TraceStore(c.getInt("concentration.traces.max-troncon", TraceRegles.MAX_TRONCON),
            c.getInt("concentration.traces.max-global", TraceRegles.MAX_GLOBAL));
    }

    private boolean actif() { return plugin.getConfig().getBoolean("concentration.traces.actif", true); }

    public void arreter() {
        if (tacheReleve >= 0) Bukkit.getScheduler().cancelTask(tacheReleve);
        if (tacheEnvoi >= 0) Bukkit.getScheduler().cancelTask(tacheEnvoi);
        if (tacheGuet >= 0) Bukkit.getScheduler().cancelTask(tacheGuet);
        marches.clear();
        enLair.clear();
        vues.clear();
    }

    /** Nombre de pas en mémoire (diagnostic). */
    public int taille() { return store.taille(); }

    /* ================================================================ relevé */

    private void relever() {
        if (!actif()) return;
        for (Player p : Bukkit.getOnlinePlayers()) releverJoueur(p);
    }

    /** À chaque tick, pour les seuls joueurs en l'air : l'atterrissage, que le relevé toutes les 4 ticks raterait. */
    private void guetter() {
        if (enLair.isEmpty()) return;
        atterris.clear();
        for (var it = enLair.iterator(); it.hasNext(); ) {
            Player p = Bukkit.getPlayer(it.next());
            if (p == null) {
                it.remove();
            } else if (p.isOnGround()) {
                it.remove();
                atterris.add(p);
            }
        }
        for (int i = 0; i < atterris.size(); i++) releverJoueur(atterris.get(i));
    }

    private void releverJoueur(Player p) {
        UUID id = p.getUniqueId();
        TraceMarche m = marches.computeIfAbsent(id, k -> new TraceMarche());
        TraceRegles.Etat e = etat(p);
        if (TraceRegles.exclu(e)) {
            m.couper();
            enLair.remove(id);
            return;
        }
        Location l = p.getLocation();
        m.mesurer(Bukkit.getCurrentTick(), l.getX(), l.getZ(), e.auSol());
        if (!e.auSol()) {
            m.enLair();
            enLair.add(id);
            return;
        }
        enLair.remove(id);
        var c = plugin.getConfig();
        double seuil = c.getDouble("concentration.traces.vitesse-sprint", TraceRegles.VITESSE_SPRINT);
        // le drapeau de sprint Minecraft veut dire trot ou sprint : c'est la vitesse qui tranche (décision des allures, 14/10)
        boolean sprint = p.isSprinting() && TraceRegles.sprinte(m.vitesse(), seuil, m.mesureEnSaut());
        boolean accroupi = p.isSneaking(), charge = Verrous.porteur(id);
        double k = TraceRegles.borneK(echelle(p));
        int n = m.avancer(l.getWorld().getName(), l.getX(), l.getZ(), TraceRegles.espacement(sprint, accroupi, charge, k), points);
        if (n == 0) return;
        int base = Math.max(1, c.getInt("concentration.traces.vie-base-secondes", TraceRegles.VIE_BASE_S));
        boolean sang = c.getBoolean("concentration.traces.sang", false);
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            boolean droit = m.piedSuivant();
            TraceRegles.pied(points[i * 3], points[i * 3 + 1], points[i * 3 + 2], droit, k, m.rang(), pied);
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            boolean blesse = sang && max != null && p.getHealth() < max.getValue() * TraceRegles.SEUIL_SANG;
            poser(p, l, pied[0], pied[1], pied[2], TraceRegles.drapeaux(droit, sprint, accroupi, charge, blesse), k, base, now);
        }
    }

    private void poser(Player p, Location l, double x, double z, double angle, int drapeaux, double k, int base, long now) {
        World w = l.getWorld();
        if (!dessus(w, x, z, l.getY())) return;
        TraceRegles.Sol sol = TraceRegles.sol(surfaceMat);
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        boolean abri = w.getHighestBlockYAt(bx, bz) > surfaceY + 1;
        TraceRegles.Meteo meteo = meteoIci(w, x, z, bx, bz, abri);
        int vie = TraceRegles.vieSecondes(base, sol, meteo, (drapeaux & TraceRegles.F_ACCROUPI) != 0);
        store.ajouter(p.getUniqueId(), w.getName(), x, surfaceY, z, Math.floorMod((int) Math.round(angle), 360), drapeaux,
            (int) Math.round(k * 100), now, vie);
    }

    /** La météo qui tombe sur ce pas : celle du lieu, pour tout le monde (ni le biome d'à côté, ni une météo individuelle). */
    private TraceRegles.Meteo meteoIci(World w, double x, double z, int bx, int bz, boolean abri) {
        if (abri) return TraceRegles.Meteo.CLAIR;
        var mc = plugin.meteo();
        int type = mc != null ? mc.typeLieu(new Location(w, x, surfaceY, z)) : 0;
        boolean pluie = w.hasStorm() && !TraceRegles.biomeSec(w.getBiome(bx, (int) Math.floor(surfaceY), bz).getKey().getKey());
        return TraceRegles.meteo(type, pluie, pluie && w.isThundering(), false);
    }

    private TraceRegles.Etat etat(Player p) {
        UUID id = p.getUniqueId();
        var o = plugin.outilsStaff();
        var vanish = plugin.vanish();
        boolean horsJeu = p.isDead() || p.getHealth() <= 0
            || plugin.douane() != null && plugin.douane().cadreDouane(id)
            || plugin.characterSelect() != null && plugin.characterSelect().isSelecting(id)
            || plugin.characters() != null && plugin.characters().getActive(id) == null;
        return new TraceRegles.Etat(p.isOnGround(), o != null && o.mode().horsJeu(id), vanish != null && vanish.isVanished(id),
            p.getGameMode() == GameMode.SPECTATOR, p.isFlying() || p.isGliding(),
            p.isInWater() || p.getLocation().getBlock().isLiquid() || p.isSwimming(), p.isInsideVehicle(),
            Verrous.porte(id) || Verrous.tenu(id), Verrous.cinematique(id), horsJeu);
    }

    /** L'attribut d'échelle du personnage (1 = taille de référence). */
    private static double echelle(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.SCALE);
        return a != null ? a.getValue() : 1.0;
    }

    /**
     * Le dessus sur lequel le pied se pose : forme de collision du bloc à cet endroit, en cherchant de la hauteur des pieds
     * vers le bas. Faux au-dessus du vide ou de l'eau (pas de trace). Exceptions de {@link #dessusVisible} : neige en couches,
     * boue, sable des âmes, dont la collision passe sous la surface qu'on voit (le pas y serait caché).
     */
    private boolean dessus(World w, double x, double z, double pieds) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), haut = (int) Math.floor(pieds + 0.6);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return false;
        double fx = x - bx, fz = z - bz;
        for (int y = haut; y >= haut - 2; y--) {
            Block b = w.getBlockAt(bx, y, bz);
            Material m = b.getType();
            if (m.isAir()) continue;
            if (b.isLiquid() || m == Material.LILY_PAD) return false;
            double top = dessusVisible(b, m);
            if (top < 0) {
                for (BoundingBox bb : b.getCollisionShape().getBoundingBoxes()) {
                    if (fx >= bb.getMinX() && fx <= bb.getMaxX() && fz >= bb.getMinZ() && fz <= bb.getMaxZ()) {
                        top = Math.max(top, bb.getMaxY());
                    }
                }
            }
            if (top < 0) continue;   // pas de collision ici (herbe haute, fleur, marche voisine)
            double surface = y + top;
            if (surface > pieds + 0.6 || surface < pieds - 1.3) return false;
            surfaceY = surface;
            surfaceMat = m.name();
            return true;
        }
        return false;
    }

    private static double dessusVisible(Block b, Material m) {
        if (m == Material.SNOW) return b.getBoundingBox().getHeight();
        if (m == Material.MUD || m == Material.SOUL_SAND) return 1.0;
        return -1;
    }

    /* ================================================================ envoi */

    /** Entrée en concentration (appelé par {@link ConcentrationCanal}) : la liste complète. */
    public void entree(Player p) {
        if (!actif()) return;
        Location l = p.getLocation();
        Vue v = new Vue(l.getWorld().getName());
        vues.put(p.getUniqueId(), v);
        envoyer(p, l, v, true, System.currentTimeMillis());
    }

    private void diffuser() {
        long now = System.currentTimeMillis();
        if (!actif()) {
            if (!coupe) couper(now);
            return;
        }
        coupe = false;
        store.balayer(now, TraceRegles.BALAYAGE_PAR_ENVOI);
        vues.keySet().retainAll(canal.concentres());
        if (canal.concentres().isEmpty()) return;
        for (UUID id : List.copyOf(canal.concentres())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            // relu avant chaque envoi, comme une demande de texte : à terre, cinématique, douane… rien ne part
            if (!canal.maintenir(p)) {
                vues.remove(id);
                continue;
            }
            Location l = p.getLocation();
            Vue v = vues.get(id);
            boolean plein = v == null || !v.monde.equals(l.getWorld().getName());
            if (plein) {
                v = new Vue(l.getWorld().getName());
                vues.put(id, v);
            }
            envoyer(p, l, v, plein, now);
        }
    }

    /** Réglage coupé : plus rien en mémoire, et les concentrés oublient ce qu'ils ont reçu (liste complète vide). */
    private void couper(long now) {
        coupe = true;
        store = nouveauStore();
        marches.clear();
        enLair.clear();
        selection.clear();
        for (UUID id : vues.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) canal.envoyerBrut(p, TraceProtocole.message(ConcentrationCanal.VERSION, true, 0, 0, 0, List.of(), List.of(), now, t -> 0));
        }
        vues.clear();
    }

    private void envoyer(Player p, Location l, Vue v, boolean plein, long now) {
        store.autour(v.monde, l.getX(), l.getZ(), TraceRegles.RAYON_ENVOI, p.getUniqueId(), now, TraceRegles.MAX_ENVOI, selection);
        List<Trace> ajouts = new ArrayList<>();
        Set<Integer> vus = new HashSet<>(Math.max(16, selection.size() * 2));
        for (Trace t : selection) {
            vus.add(t.id);
            if (!v.connus.contains(t.id)) ajouts.add(t);
        }
        List<Integer> retraits = new ArrayList<>();
        for (int id : v.connus) if (!vus.contains(id)) retraits.add(id);
        if (!plein && ajouts.isEmpty() && retraits.isEmpty()) return;
        v.connus.clear();
        v.connus.addAll(vus);
        UUID spectateur = p.getUniqueId();
        // le jour de naissance du pas, pas le jour courant : un pas renvoyé après minuit garde sa clé
        String msg = TraceProtocole.message(ConcentrationCanal.VERSION, plein, l.getBlockX(), l.getBlockY(), l.getBlockZ(), ajouts,
            retraits, now, t -> v.cles.computeIfAbsent(new Piste(t.marcheur, TraceRegles.jour(t.nee)),
                c -> TraceRegles.cle(c.marcheur(), spectateur, c.jour(), sel)));
        canal.envoyerBrut(p, msg);
    }

    /* ================================================================ fins */

    @EventHandler
    public void quitter(PlayerQuitEvent e) {
        marches.remove(e.getPlayer().getUniqueId());
        enLair.remove(e.getPlayer().getUniqueId());
        vues.remove(e.getPlayer().getUniqueId());
    }
}
