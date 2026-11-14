package com.reborn.shinobicombat.combat;

import com.reborn.shinobicombat.net.CombatChannel;
import com.reborn.shinobicombat.saut.SautChargeLoi;
import com.reborn.shinobicombat.saut.SautChargeLoi.Regles;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.KoService;
import com.reborn.shinobicore.api.ResourcePool;
import com.reborn.shinobicore.api.ResourceService;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * <b>Saut chargé</b> décidé par le serveur (lot 5, étape A, 09/11 ; loi dans {@link SautChargeLoi}).
 *
 * <p>Protocole sur {@code reborn:combatin} : {@link CombatChannel#KIND_SAUT_DEBUT} (le serveur note <b>son</b> heure et
 * vérifie les gardes), {@link CombatChannel#KIND_SAUT_RELACHE} (lacet, tangage ; la charge vient des deux heures du
 * serveur, la visée est ramenée dans un cône de 25° autour de son regard, le chakra est débité, la recharge posée) et
 * {@link CombatChannel#KIND_SAUT_ANNULE}. Réponse {@code GO} ou {@code NON} sur {@code reborn:combat} : rien ne part sans
 * réponse, donc plus de recharge fantôme côté mod. Le serveur ne pose pas la vitesse : le mod la joue (vol à frottement
 * × 0,98, sans poussée), le serveur surveille la distance parcourue.
 *
 * <p><b>Chute</b> : de {@code GO} à la première réception (10 s au plus) puis une seconde de grâce, les dégâts de chute
 * sont annulés pour tous, quelle que soit la voie. La métadonnée {@value #META_BOND} dit aux autres plugins (ShinobiAbilities)
 * que le corps est pris : ni double saut, ni escalade, ni onde au sol, ni pilotage de la Voie du Flux pendant le bond.
 *
 * <p><b>Le bond doit être pris</b> (critique du 09/11) : sans décollage vu par le serveur dans
 * {@code 10 ticks + 2 × ping} (3 s au plus), le bond tombe et il est remboursé (chakra et recharge) ; le mod le dit aussi
 * quand il n'a pas joué un {@code GO} tardif ({@link CombatChannel#KIND_SAUT_GO_IGNORE}). Huit ticks après le décollage, le
 * corps doit avoir suivi l'arc ({@link SautChargeLoi#decollageConforme}) : sinon (chute d'une falaise à la place du bond,
 * mur heurté d'entrée) la protection tombe, sans remboursement. Un téléport ou un changement de monde finit le bond.
 *
 * <p><b>Sol</b> : collision sous la boîte du joueur, jamais le {@code onGround} déclaré par le client.
 *
 * <p><b>Ancien saut</b> (kind 5, mod d'avant le lot 5, gardé une version) : refusé pendant un bond, et dès que le client a
 * montré le nouveau protocole (kind 8 reçu, ou canal {@code reborn:dojutsu} écouté = mod 0.4.202 et plus) ; recharge
 * partagée avec le saut chargé ; jamais un bond écrasé.
 */
public final class SautCharge implements Listener {

    /** Métadonnée posée pendant le bond (lue par ShinobiAbilities, {@code MobilityListener.coreSessionActive}). */
    public static final String META_BOND = "reborn_bond";

    private static final long GRACE_MS = 1000L;
    /** Une relâche juste sous le seuil (gigue du réseau) est arrondie au seuil. */
    private static final long GIGUE_SEUIL_MS = 60L;
    /** Une charge oubliée (paquet d'annulation perdu) expire. */
    private static final long CHARGE_MAX_MS = 30_000L;
    /** Marge de la surveillance du vol : latence (ticks) et distance (blocs). */
    private static final int MARGE_TICKS = 12;
    private static final double MARGE_BLOCS = 3.0;
    /** Délai de décollage : base et plafond (ticks), plus deux fois le ping. */
    private static final int DECOLLAGE_BASE_TICKS = 10, DECOLLAGE_MAX_TICKS = 60;
    /** Contrôle de conformité, en ticks après le décollage. */
    private static final int CONTROLE_TICKS = 8;
    /** Canal du mod 0.4.202 et plus (Byakugan, ShinobiCore) : ce client connaît le saut chargé. */
    private static final String CANAL_MOD_LOT5 = "reborn:dojutsu";

    private final Plugin plugin;
    private final CombatListener combat;
    private final CharacterService characters;
    private final KoService ko;
    private ResourceService ressources;
    private volatile Regles regles = Regles.DEFAUT;
    private long toleranceSolMs = 200L;
    private long protectionMaxMs = 10_000L;

    /** Début de charge (heure du serveur). */
    private final Map<UUID, Long> debut = new ConcurrentHashMap<>();
    /** Dernière fois vu au sol pendant la charge. */
    private final Map<UUID, Long> dernierSol = new ConcurrentHashMap<>();
    /** Fin de la recharge. */
    private final Map<UUID, Long> rechargeJusqua = new ConcurrentHashMap<>();
    /** Bonds en cours. */
    private final Map<UUID, Bond> bonds = new ConcurrentHashMap<>();
    /** Fin de la protection contre la chute (bond fini, grâce). */
    private final Map<UUID, Long> protegeJusqua = new ConcurrentHashMap<>();
    /** Joueurs dont le client a montré le nouveau protocole (kind 8) : l'ancien saut leur est fermé. */
    private final Set<UUID> protocoleLot5 = ConcurrentHashMap.newKeySet();
    private BukkitTask tache;
    private long ticks;

    private static final class Bond {
        final UUID monde;
        final double x0, y0, z0, vh, vy;
        final long tick0, finMs;
        final int delaiDecollage;
        /** Chakra payé et recharge d'avant : rendus si le bond n'est jamais pris. */
        final double coutPaye;
        final Long rechargeAvant;
        long tickDecollage = -1;
        double yMax;
        boolean controle;
        Bond(Location l, double vh, double vy, long tick0, long finMs, int delaiDecollage, double coutPaye,
             Long rechargeAvant) {
            this.monde = l.getWorld().getUID();
            this.x0 = l.getX(); this.y0 = l.getY(); this.z0 = l.getZ(); this.vh = vh; this.vy = vy;
            this.tick0 = tick0; this.finMs = finMs; this.delaiDecollage = delaiDecollage;
            this.coutPaye = coutPaye; this.rechargeAvant = rechargeAvant; this.yMax = l.getY();
        }
    }

    public SautCharge(Plugin plugin, CombatListener combat, CharacterService characters, KoService ko) {
        this.plugin = plugin;
        this.combat = combat;
        this.characters = characters;
        this.ko = ko;
    }

    /* ================================================================ réglages */

    /** Lit {@code mobility.saut-charge} (valeurs absentes = défauts de {@link Regles#DEFAUT}). */
    public void charger(ConfigurationSection s) {
        Regles d = Regles.DEFAUT;
        if (s == null) { regles = d; return; }
        regles = new Regles(
                Math.max(100L, s.getLong("charge-ms", d.chargeMs())),
                clamp(s.getDouble("seuil", d.seuil()), 0, 1),
                Math.max(0, s.getDouble("vitesse-min", d.vitesseMin())),
                s.getDouble("vitesse-pente", d.vitessePente()),
                s.getDouble("elan-min", d.elanMin()),
                s.getDouble("elan-pente", d.elanPente()),
                clamp(s.getDouble("vy-max", d.vyMax()), 0, 6),
                clamp(s.getDouble("trainee", d.trainee()), 0.5, 1),
                Math.max(0, s.getDouble("cout-min", d.coutMinPct())),
                Math.max(0, s.getDouble("cout-max", d.coutMaxPct())),
                Math.round(Math.max(0, s.getDouble("recharge-min-s", d.rechargeMinMs() / 1000.0)) * 1000),
                Math.round(Math.max(0, s.getDouble("recharge-max-s", d.rechargeMaxMs() / 1000.0)) * 1000),
                clamp(s.getDouble("portee-max", d.porteeMax()), 8, 200),
                clamp(s.getDouble("visee-max", d.viseeMaxDeg()), 0, 180));
        toleranceSolMs = Math.max(0, s.getLong("tolerance-sol-ms", 200L));
        protectionMaxMs = Math.max(1000L, Math.round(s.getDouble("protection-chute-max-s", 10) * 1000));
    }

    public Regles regles() { return regles; }

    public void demarrer() {
        tache = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        for (Player p : plugin.getServer().getOnlinePlayers()) CombatChannel.sendSautRegles(plugin, p, regles);
    }

    public void arreter() {
        if (tache != null) tache.cancel();
        for (Player p : plugin.getServer().getOnlinePlayers()) p.removeMetadata(META_BOND, plugin);
        bonds.clear();
    }

    /* ================================================================ protocole */

    /** Début de charge : gardes, heure du serveur. Refus immédiat si la charge ne pourra pas partir. */
    void debut(Player p) {
        UUID id = p.getUniqueId();
        protocoleLot5.add(id);
        long now = System.currentTimeMillis();
        byte r = refus(p, now, true);
        if (r != 0) {
            debut.remove(id);
            CombatChannel.sendSautNon(plugin, p, r, r == CombatChannel.SAUT_R_RECHARGE ? reste(id, now) : 0);
            return;
        }
        debut.put(id, now);
        dernierSol.put(id, now);
    }

    void annule(Player p) {
        debut.remove(p.getUniqueId());
        dernierSol.remove(p.getUniqueId());
    }

    /** Le mod n'a pas joué un {@code GO} arrivé trop tard : bond pas encore pris = remboursé. */
    void goIgnore(Player p) {
        Bond b = bonds.get(p.getUniqueId());
        if (b != null && b.tickDecollage < 0) fauxDepart(p.getUniqueId(), p, b);
    }

    /** Relâche : charge calculée ici, gardes relues, visée bornée, chakra débité, réponse GO ou NON. */
    void relache(Player p, float lacetCli, float tangageCli) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long t0 = debut.remove(id);
        Long sol = dernierSol.remove(id);
        Regles rg = regles;
        if (t0 == null || now - t0 > CHARGE_MAX_MS) {
            CombatChannel.sendSautNon(plugin, p, CombatChannel.SAUT_R_SANS_DEBUT, 0);
            return;
        }
        long tenu = now - t0;
        if (tenu < rg.seuilMs() - GIGUE_SEUIL_MS) {
            CombatChannel.sendSautNon(plugin, p, CombatChannel.SAUT_R_SEUIL, 0);
            return;
        }
        tenu = Math.max(tenu, rg.seuilMs());
        byte r = refus(p, now, false);
        if (r == 0 && !auSol(p) && (sol == null || now - sol > toleranceSolMs)) r = CombatChannel.SAUT_R_SOL;
        if (r != 0) {
            CombatChannel.sendSautNon(plugin, p, r, r == CombatChannel.SAUT_R_RECHARGE ? reste(id, now) : 0);
            return;
        }
        double c = SautChargeLoi.charge(tenu, rg);
        byte drapeaux = 0;
        // chakra : la charge est ramenée à ce que la réserve peut payer
        ResourcePool pool = pool(p);
        double dispoPct = pool == null || pool.max() <= 0 ? 100.0 : 100.0 * pool.current() / pool.max();
        if (SautChargeLoi.coutPct(c, rg) > dispoPct) {
            double payable = SautChargeLoi.chargePayable(dispoPct, rg);
            if (!SautChargeLoi.suffisante(payable, rg)) {
                CombatChannel.sendSautNon(plugin, p, CombatChannel.SAUT_R_CHAKRA, 0);
                return;
            }
            c = payable;
            drapeaux |= CombatChannel.SAUT_BRIDE_CHAKRA;
        }
        // visée : dans le cône du regard connu du serveur (le mod vise à la caméra et aligne le corps pendant la charge)
        Location loc = p.getLocation();
        double[] visee = SautChargeLoi.viseeBornee(lacetCli, tangageCli, loc.getYaw(), loc.getPitch(), rg.viseeMaxDeg());
        double[] v = SautChargeLoi.vitesseDepart(c, visee[0], visee[1], rg);
        // l'arrivée réelle (relief compris) reste dans un chunk chargé chez le client
        double borne = SautChargeLoi.porteeVue(distanceVue(p), rg);
        if (SautChargeLoi.bornerArrivee(v, borne, rg, sol(loc))) drapeaux |= CombatChannel.SAUT_BRIDE_VUE;
        double cout = 0;
        if (pool != null && pool.max() > 0) {
            cout = SautChargeLoi.coutPct(c, rg) / 100.0 * pool.max();
            if (!pool.consume(cout)) {
                CombatChannel.sendSautNon(plugin, p, CombatChannel.SAUT_R_CHAKRA, 0);
                return;
            }
        }
        long recharge = SautChargeLoi.rechargeMs(c, rg);
        Long avant = rechargeJusqua.put(id, now + recharge);
        bonds.put(id, new Bond(loc, Math.hypot(v[0], v[2]), v[1], ticks, now + protectionMaxMs, delaiDecollage(p),
                cout, avant));
        protegeJusqua.put(id, now + protectionMaxMs + GRACE_MS);
        p.setMetadata(META_BOND, new FixedMetadataValue(plugin, true));
        p.setFallDistance(0f);
        CombatChannel.sendSautGo(plugin, p, v, c, recharge, drapeaux, borne);
        p.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_BREEZE_JUMP, org.bukkit.SoundCategory.PLAYERS,
                0.55f, 0.75f + 0.25f * (float) c);
    }

    /**
     * Ancien saut (kind 5, mod d'avant le lot 5, gardé une version) : permis seulement à un client qui n'a jamais montré
     * le nouveau protocole, au sol (collision), sans bond en cours ni recharge (partagée avec le saut chargé).
     */
    boolean ancienPermis(Player p) {
        UUID id = p.getUniqueId();
        if (protocoleLot5.contains(id) || p.getListeningPluginChannels().contains(CANAL_MOD_LOT5)) return false;
        if (bonds.containsKey(id) || debut.containsKey(id)) return false;
        Long fin = rechargeJusqua.get(id);
        if (fin != null && fin > System.currentTimeMillis()) return false;
        return auSol(p) && !surEau(p);
    }

    /** Ancien saut parti (vitesse posée par le serveur) : recharge partagée, chute couverte, vol surveillé comme un bond. */
    void ancienParti(Player p, double vx, double vy, double vz, long rechargeMs) {
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        Long avant = rechargeJusqua.put(id, now + rechargeMs);
        bonds.put(id, new Bond(p.getLocation(), Math.hypot(vx, vz), vy, ticks, now + protectionMaxMs, delaiDecollage(p),
                0, avant));
        protegeJusqua.put(id, now + protectionMaxMs + GRACE_MS);
        p.setMetadata(META_BOND, new FixedMetadataValue(plugin, true));
        p.setFallDistance(0f);
    }

    /** Raison de refuser (0 = permis). {@code auDebut} : le sol est exigé tout de suite. */
    private byte refus(Player p, long now, boolean auDebut) {
        UUID id = p.getUniqueId();
        if (characters.getActive(id) == null) return CombatChannel.SAUT_R_PERSO;
        if (ko != null && ko.isKo(id)) return CombatChannel.SAUT_R_KO;
        if (combat.isGuarding(id)) return CombatChannel.SAUT_R_GARDE;
        if (combat.isStunned(id) || combat.sautRefuse(id)) return CombatChannel.SAUT_R_ETAT;
        if (combat.surchargeSaut(id)) return CombatChannel.SAUT_R_SURCHARGE;
        if (surEau(p)) return CombatChannel.SAUT_R_EAU;
        if (bonds.containsKey(id)) return CombatChannel.SAUT_R_SOL;
        Long fin = rechargeJusqua.get(id);
        if (fin != null && fin > now) return CombatChannel.SAUT_R_RECHARGE;
        if (auDebut && !auSol(p)) return CombatChannel.SAUT_R_SOL;
        ResourcePool pool = pool(p);
        if (pool != null && pool.max() > 0
                && 100.0 * pool.current() / pool.max() < SautChargeLoi.coutPct(regles.seuil(), regles)) {
            return CombatChannel.SAUT_R_CHAKRA;
        }
        return 0;
    }

    private long reste(UUID id, long now) {
        Long fin = rechargeJusqua.get(id);
        return fin == null ? 0 : Math.max(0, fin - now);
    }

    /**
     * Au sol : une collision sous les pieds (boîte de 0,25 sous la boîte du joueur). Jamais le {@code onGround} déclaré par
     * le client, qu'un client modifié peut poser en l'air.
     */
    private static boolean auSol(Player p) {
        BoundingBox b = p.getBoundingBox();
        BoundingBox dessous = new BoundingBox(b.getMinX() + 0.01, b.getMinY() - 0.25, b.getMinZ() + 0.01,
                b.getMaxX() - 0.01, b.getMinY() - 0.01, b.getMaxZ() - 0.01);
        return p.getWorld().hasCollisionsIn(dessous);
    }

    /** Dans l'eau ou debout dessus (marche sur l'eau) : pas de charge. */
    private static boolean surEau(Player p) {
        if (p.isInWater()) return true;
        Location l = p.getLocation();
        Material sous = l.clone().subtract(0, 0.2, 0).getBlock().getType();
        return sous == Material.WATER || l.getBlock().getType() == Material.WATER;
    }

    /** Délai pour voir le décollage : 10 ticks plus deux fois le ping (aller du GO, retour du mouvement), 3 s au plus. */
    private static int delaiDecollage(Player p) {
        int ping;
        try { ping = Math.max(0, p.getPing()); } catch (Throwable t) { ping = 0; }
        return Math.min(DECOLLAGE_MAX_TICKS, DECOLLAGE_BASE_TICKS + (int) Math.ceil(2.0 * ping / 50.0));
    }

    /**
     * Relief sous l'arc, relatif aux pieds au départ : la carte des hauteurs {@code MOTION_BLOCKING} du serveur (chunks
     * chargés seulement ; ailleurs, sol inconnu, donc le vol continue et la borne joue).
     */
    private static SautChargeLoi.Sol sol(Location depart) {
        World w = depart.getWorld();
        double x0 = depart.getX(), y0 = depart.getY(), z0 = depart.getZ();
        return (dx, dz) -> {
            int bx = (int) Math.floor(x0 + dx), bz = (int) Math.floor(z0 + dz);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return Double.NEGATIVE_INFINITY;
            return w.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING) + 1 - y0;
        };
    }

    /** Distance de vue réellement servie : la plus petite du serveur et du client. */
    private static int distanceVue(Player p) {
        int srv = p.getViewDistance();
        int cli;
        try { cli = p.getClientViewDistance(); } catch (Throwable t) { cli = srv; }
        if (cli <= 0) cli = srv;
        return Math.min(srv, cli);
    }

    private ResourcePool pool(Player p) {
        if (ressources == null) {
            ressources = plugin.getServer().getServicesManager().load(ResourceService.class);
            if (ressources == null) return null;
        }
        try {
            return ressources.pool(p);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /* ================================================================ vol et chute */

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : debut.entrySet()) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p == null || now - e.getValue() > CHARGE_MAX_MS) { debut.remove(e.getKey()); continue; }
            if (auSol(p)) dernierSol.put(e.getKey(), now);
        }
        for (Map.Entry<UUID, Bond> e : bonds.entrySet()) {
            UUID id = e.getKey();
            Bond b = e.getValue();
            Player p = plugin.getServer().getPlayer(id);
            if (p == null) { finir(id, null, now); continue; }
            Location l = p.getLocation();
            if (!l.getWorld().getUID().equals(b.monde)) { finir(id, p, now); continue; }
            long k = ticks - b.tick0;
            p.setFallDistance(0f);
            boolean sol = auSol(p);
            if (b.tickDecollage < 0) {
                if (!sol || l.getY() > b.y0 + 0.4) {
                    b.tickDecollage = ticks;
                } else {
                    if (k > b.delaiDecollage) fauxDepart(id, p, b);
                    continue;
                }
            }
            b.yMax = Math.max(b.yMax, l.getY());
            long kd = ticks - b.tickDecollage;
            if ((sol && kd >= 3) || p.isInWater() || now > b.finMs) {
                finir(id, p, now);
                continue;
            }
            double d = Math.hypot(l.getX() - b.x0, l.getZ() - b.z0);
            if (!b.controle && kd >= CONTROLE_TICKS) {
                b.controle = true;
                if (!SautChargeLoi.decollageConforme(b.vh, b.vy, (int) kd, d, b.yMax - b.y0, regles)) {
                    // le corps n'a pas pris l'arc (chute d'une falaise, saut vanilla, mur heurté) : plus de protection
                    plugin.getLogger().fine("Saut chargé : " + p.getName() + " n'a pas suivi son bond, protection retirée.");
                    abandonner(id, p);
                    continue;
                }
            }
            if (k % 5 == 0) {
                double max = SautChargeLoi.distanceAuTick(b.vh, (int) k + MARGE_TICKS, regles) + MARGE_BLOCS;
                if (d > max) {
                    plugin.getLogger().log(Level.WARNING, "Saut chargé : " + p.getName() + " a parcouru "
                            + String.format(java.util.Locale.ROOT, "%.1f", d) + " blocs en " + k + " ticks (au plus "
                            + String.format(java.util.Locale.ROOT, "%.1f", max) + ") : bond arrêté.");
                    p.setVelocity(new org.bukkit.util.Vector(0, Math.min(0, p.getVelocity().getY()), 0));
                    abandonner(id, p);
                }
            }
        }
        protegeJusqua.entrySet().removeIf(e -> e.getValue() < now && !bonds.containsKey(e.getKey()));
    }

    private void finir(UUID id, Player p, long now) {
        bonds.remove(id);
        protegeJusqua.put(id, now + GRACE_MS);
        if (p != null) {
            p.setFallDistance(0f);
            p.removeMetadata(META_BOND, plugin);
        }
    }

    /** Bond tombé sans protection ni remboursement (non conforme, trop loin). */
    private void abandonner(UUID id, Player p) {
        bonds.remove(id);
        protegeJusqua.remove(id);
        p.removeMetadata(META_BOND, plugin);
    }

    /** Bond jamais pris : rien n'a eu lieu, chakra et recharge rendus, aucune protection. */
    private void fauxDepart(UUID id, Player p, Bond b) {
        abandonner(id, p);
        if (b.coutPaye > 0) {
            ResourcePool pool = pool(p);
            if (pool != null) pool.regen(b.coutPaye);
        }
        if (b.rechargeAvant == null) rechargeJusqua.remove(id); else rechargeJusqua.put(id, b.rechargeAvant);
    }

    /** Téléport (staff, cinématique, douane, hôpital) ou changement de monde : le bond finit proprement, avec la grâce. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (bonds.containsKey(id)) finir(id, e.getPlayer(), System.currentTimeMillis());
    }

    @EventHandler
    public void onMonde(PlayerChangedWorldEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (bonds.containsKey(id)) finir(id, e.getPlayer(), System.currentTimeMillis());
    }

    /** Aucun dégât de chute pendant le bond et la seconde qui suit la réception, pour tous. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChute(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        Long fin = protegeJusqua.get(id);
        if (bonds.containsKey(id) || (fin != null && fin >= System.currentTimeMillis())) {
            e.setCancelled(true);
            p.setFallDistance(0f);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // après l'enregistrement des canaux du client
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) CombatChannel.sendSautRegles(plugin, p, regles);
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { oublier(e.getPlayer().getUniqueId(), e.getPlayer()); }

    /** KO : la charge tombe ; un bond en cours reste protégé jusqu'au sol. */
    void surKo(Player p) {
        debut.remove(p.getUniqueId());
        dernierSol.remove(p.getUniqueId());
    }

    /** {@code /resetcd} : recharge effacée côté serveur. */
    void reinitialiser(UUID id) { rechargeJusqua.remove(id); }

    void oublier(UUID id, Player p) {
        debut.remove(id);
        dernierSol.remove(id);
        bonds.remove(id);
        protegeJusqua.remove(id);
        protocoleLot5.remove(id);
        // la recharge survit à une reconnexion (pas de saut gratuit en se reconnectant)
        if (p != null) p.removeMetadata(META_BOND, plugin);
    }

    private static double clamp(double v, double a, double b) { return v < a ? a : v > b ? b : v; }
}
