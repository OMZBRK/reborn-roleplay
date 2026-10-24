package com.reborn.shinobicore.ko.action;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.ko.KoJournal;
import com.reborn.shinobicore.ko.KoState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carry mechanic — a non-KO player can put a KO body on top of them
 * and walk around with it.
 *
 * <p>Mechanics:
 * <ul>
 *   <li>Pickup is initiated by « Porter » in the mod's interaction menu ({@code /corps porter}); we then call
 *       {@link #carry(Player, Player)} which mounts the target on the carrier as a passenger ("on the
 *       shoulders").</li>
 *   <li>The carrier moves at slowness II while a body is on them, to
 *       balance the perk vs the price.</li>
 *   <li>Drop is triggered by sneak (hold shift). Disconnect / death of either party also drops the body, on the
 *       ground in front of the carrier ({@link #dropSpot}).</li>
 *   <li>The KO state's {@code lockLocation} is updated to the
 *       carrier's location every tick, so when the body is dropped it
 *       stays where it landed instead of teleporting back.</li>
 * </ul>
 *
 * <p><b>Douleur (Pain RP)</b> — un personnage en douleur ({@link com.reborn.shinobicore.ko.DouleurManager}) se porte
 * aussi, <b>sans son accord</b>. Conscient, il peut <b>se débattre avec Espace</b> : chaque appui remplit une jauge
 * ({@code ko.douleur.lutte-appuis}, 8 par défaut) qui perd un appui toutes les {@code ko.douleur.lutte-oubli-secondes}
 * (1,5 s) ; pleine, il se dégage et ne peut pas être repris avant {@code ko.douleur.lutte-protection-secondes} (6 s).
 * S'il reprend des forces (PV au-dessus du seuil), il est reposé. Un corps porté ne descend plus seul en
 * s'accroupissant : seul le porteur dépose.
 */
public final class PorterManager implements Listener {

    /** Lenteur du porteur (comme une lenteur II) en modificateur nommé : ni potion infinie qui survit au crash, ni
     *  retrait par type qui effaçait l'étourdi ou la lenteur d'ATA du porteur (ETATS_JOUEUR §9 étape 0). */
    private static final NamespacedKey LENTEUR_PORTEUR = new NamespacedKey("shinobicore", "porteur");

    private final ShinobiCore plugin;

    /** carrierId → carriedBodyId. We don't allow stacking — one carry
     *  per player at a time. */
    private final Map<UUID, UUID> byCarrier = new HashMap<>();

    /** Reverse index for quick lookup: carriedBodyId → carrierId. */
    private final Map<UUID, UUID> byCarried = new HashMap<>();

    /** porté conscient (douleur) → jauge de lutte 0..1. */
    private final Map<UUID, Double> struggle = new HashMap<>();
    private final Map<UUID, Long> lastStruggle = new HashMap<>();
    private final Map<UUID, Boolean> jumpHeld = new HashMap<>();
    /** Portés <b>de plein gré</b> (demande acceptée, GAMEPLAN §14, 24/10) : Espace les fait descendre. */
    private final java.util.Set<UUID> consentis = new java.util.HashSet<>();
    /** dégagé → instant jusqu'auquel on ne peut pas le reprendre. */
    private final Map<UUID, Long> noCarryUntil = new HashMap<>();
    private final Map<UUID, Long> lastWarn = new HashMap<>();
    /** Vrai pendant nos propres descentes de monture : l'anti-descente laisse passer. */
    private boolean releasing;

    private BukkitTask ticker;

    public PorterManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        // Drop everyone on shutdown so we don't leak passenger state.
        for (UUID carrierId : new HashMap<>(byCarrier).keySet()) {
            release(carrierId);
        }
    }

    /* ----------------------------------------------- public surface */

    /** Begin carrying {@code target} — KO, mort (scène de mort) ou <b>en douleur</b> (sans son accord).
     *  {@code carrier} can't already be carrying someone else. Returns true on success. */
    public boolean carry(Player carrier, Player target) {
        if (carrier == null || target == null) return false;
        if (byCarrier.containsKey(carrier.getUniqueId())) return false;
        boolean dead = plugin.deathScene() != null && plugin.deathScene().isDead(target.getUniqueId());
        boolean pain = isPain(target.getUniqueId());
        if (!plugin.ko().isKo(target.getUniqueId()) && !dead && !pain) return false;
        // Can't carry yourself; can't carry someone already on a mount.
        if (carrier.getUniqueId().equals(target.getUniqueId())) return false;
        if (target.getVehicle() != null) return false;
        if (plugin.ko().isKo(carrier.getUniqueId()) || isPain(carrier.getUniqueId())
                || carrier.getVehicle() != null) return false;
        // Correctifs urgents (ETATS_JOUEUR §9 étape 0) : ni porter un gelé, un staff en mode staff ou un invisible,
        // ni porter quand on est soi-même gelé, en mode staff ou en cinématique.
        if (refuse(carrier, target)) return false;
        Long protect = noCarryUntil.get(target.getUniqueId());
        if (protect != null && protect > System.currentTimeMillis()) {
            carrier.sendActionBar(Component.text("Il vient de se dégager : attends un instant.", NamedTextColor.GRAY));
            return false;
        }

        // Mark the KO state as carried so the KoManager ticker stops
        // pinning the body to its old lock location.
        KoState st = plugin.ko().getKo(target.getUniqueId());
        if (st != null) st.setCarrierPlayerId(carrier.getUniqueId());

        carrier.addPassenger(target);
        // Étape 5 d'ETATS_JOUEUR : la lenteur du porteur est la ligne mod.porteur (applicateur unique) ; repli sinon.
        if (!com.reborn.shinobicore.etat.Applicateur.actif()) com.reborn.shinobicore.util.Effets.lenteur(carrier, LENTEUR_PORTEUR, 2);
        else com.reborn.shinobicore.etat.Applicateur.reappliquer(plugin, carrier);

        byCarrier.put(carrier.getUniqueId(), target.getUniqueId());
        byCarried.put(target.getUniqueId(), carrier.getUniqueId());

        carrier.sendMessage(Component.text(
                "Tu portes la personne. Accroupis-toi pour la déposer.",
                NamedTextColor.GRAY));
        if (pain && st == null) {
            struggle.put(target.getUniqueId(), 0.0);
            lastStruggle.put(target.getUniqueId(), System.currentTimeMillis());
            if (plugin.koHud() == null || !plugin.koHud().modded(target)) {
                target.sendMessage(Component.text("On te soulève de force. Espace pour te débattre.", NamedTextColor.GRAY));
            }
        }
        KoJournal.log(plugin, carrier, "Porte", KoJournal.who(plugin, target)
                + (dead ? " (mort)" : st != null ? " (KO)" : " (en douleur, sans accord)"));
        return true;
    }

    /**
     * Porter une personne <b>consciente qui a accepté</b> ({@link DemandeService}) : blessé qui ne peut plus marcher,
     * scène RP. Mêmes garde-fous que {@link #carry} ; elle descend quand elle veut (Espace).
     */
    public boolean carryConsenti(Player carrier, Player target) {
        if (carrier == null || target == null || carrier.getUniqueId().equals(target.getUniqueId())) return false;
        if (byCarrier.containsKey(carrier.getUniqueId()) || byCarried.containsKey(target.getUniqueId())) return false;
        if (target.getVehicle() != null || carrier.getVehicle() != null) return false;
        if (plugin.ko().isKo(carrier.getUniqueId()) || isPain(carrier.getUniqueId())) return false;
        if (plugin.ko().isKo(target.getUniqueId())
                || (plugin.deathScene() != null && plugin.deathScene().isDead(target.getUniqueId()))) return false;
        if (refuse(carrier, target)) return false;
        carrier.addPassenger(target);
        if (!com.reborn.shinobicore.etat.Applicateur.actif()) com.reborn.shinobicore.util.Effets.lenteur(carrier, LENTEUR_PORTEUR, 2);
        else com.reborn.shinobicore.etat.Applicateur.reappliquer(plugin, carrier);
        byCarrier.put(carrier.getUniqueId(), target.getUniqueId());
        byCarried.put(target.getUniqueId(), carrier.getUniqueId());
        consentis.add(target.getUniqueId());
        carrier.sendActionBar(Component.text("Tu la portes. Accroupis-toi pour la déposer.", NamedTextColor.GRAY));
        if (plugin.koHud() == null || !plugin.koHud().modded(target)) {
            target.sendMessage(Component.text("On te porte. Espace pour descendre.", NamedTextColor.GRAY));
        }
        KoJournal.log(plugin, carrier, "Porte", KoJournal.who(plugin, target) + " (avec son accord)");
        return true;
    }

    /** Porté de plein gré (demande acceptée) ? */
    public boolean consenti(UUID bodyId) { return bodyId != null && consentis.contains(bodyId); }

    private boolean isPain(UUID id) {
        return plugin.douleur() != null && plugin.douleur().isInPain(id);
    }

    /** Gel, mode staff, invisibilité, cinématique : personne ne porte ni n'est porté. */
    private boolean refuse(Player carrier, Player target) {
        UUID c = carrier.getUniqueId(), t = target.getUniqueId();
        if (com.reborn.shinobicore.etat.Verrous.gele(c) || com.reborn.shinobicore.etat.Verrous.horsJeu(c)
                || com.reborn.shinobicore.etat.Verrous.cinematique(c)) return true;
        if (com.reborn.shinobicore.etat.Verrous.gele(t)) {
            carrier.sendActionBar(Component.text("Gelé par le staff : on n'y touche pas.", NamedTextColor.GRAY));
            return true;
        }
        return com.reborn.shinobicore.etat.Verrous.horsJeu(t)
                || (plugin.vanish() != null && plugin.vanish().isVanished(t));
    }

    /** Le porteur tombe KO d'un coup (sans passer par la douleur) : il lâche ce qu'il porte ou tient. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onKoEnter(com.reborn.shinobicore.event.KoEnterEvent ev) {
        UUID id = ev.player().getUniqueId();
        if (byCarrier.containsKey(id)) release(id);
        if (plugin.corps() != null) plugin.corps().onActorDisabled(ev.player(), "Tu perds connaissance.");
    }

    /** Porteur de ce corps, ou null. */
    public UUID carrierOf(UUID bodyId) { return byCarried.get(bodyId); }

    /** Jauge de lutte d'un porté conscient (0..1), ou -1 s'il n'est pas porté en douleur. */
    public double struggleOf(UUID bodyId) {
        Double v = struggle.get(bodyId);
        return v == null ? -1 : v;
    }

    /** Release whatever {@code carrier} is holding. No-op when not
     *  carrying. */
    public void release(UUID carrierId) {
        UUID bodyId = byCarrier.remove(carrierId);
        if (bodyId == null) return;
        byCarried.remove(bodyId);
        struggle.remove(bodyId);
        consentis.remove(bodyId);

        Player carrier = Bukkit.getPlayer(carrierId);
        Player body    = Bukkit.getPlayer(bodyId);
        if (carrier != null) {
            com.reborn.shinobicore.util.Effets.retirer(carrier, Attribute.MOVEMENT_SPEED, LENTEUR_PORTEUR);
        }
        if (body != null && body.getVehicle() != null) {
            releasing = true;
            try { body.leaveVehicle(); } finally { releasing = false; }
        }
        // Le corps se pose au sol devant le porteur : à l'instant du dépôt il est encore sur ses épaules, et
        // l'épingler là le laissait flotter en l'air.
        Location spot = null;
        if (body != null) {
            spot = dropSpot(carrier != null ? carrier.getLocation() : body.getLocation(), 0.8);
            body.teleport(spot);
            body.setFallDistance(0f);
        }
        KoState st = plugin.ko().getKo(bodyId);
        if (st != null) {
            st.setCarrierPlayerId(null);
            if (spot != null) st.setLockLocation(spot.clone());
        }
        if (body != null && plugin.deathScene() != null) plugin.deathScene().moveBody(body.getUniqueId(), spot);
        if (carrier != null) {
            carrier.sendMessage(Component.text(
                    "Tu déposes la personne.", NamedTextColor.GRAY));
            if (body != null) KoJournal.log(plugin, carrier, "Dépose", KoJournal.who(plugin, body));
        }
    }

    /** Drop a carry that involves {@code playerId} — works whether
     *  they're the carrier or the carried. */
    public void releaseAllInvolving(UUID playerId) {
        if (byCarrier.containsKey(playerId)) {
            release(playerId);
        }
        UUID carrier = byCarried.remove(playerId);
        if (carrier != null) release(carrier);
    }

    public boolean isCarrying(UUID carrierId) { return byCarrier.containsKey(carrierId); }

    /**
     * Point au sol à {@code ahead} blocs devant {@code from} (horizontalement), posé sur le premier bloc solide en
     * dessous (6 blocs au plus), sinon {@code from}. Sert à déposer un corps porté ou tenu.
     */
    public static Location dropSpot(Location from, double ahead) {
        Vector dir = from.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        Location at = from.clone().add(dir.normalize().multiply(ahead));
        Location ground = ground(at);
        if (ground == null) ground = ground(from.clone());
        if (ground == null) return from.clone();
        ground.setYaw(from.getYaw());
        ground.setPitch(0f);
        return ground;
    }

    private static Location ground(Location at) {
        World w = at.getWorld();
        int x = at.getBlockX(), z = at.getBlockZ();
        int top = (int) Math.floor(at.getY() + 1.0);
        for (int y = top; y >= top - 7 && y > w.getMinHeight(); y--) {
            Block below = w.getBlockAt(x, y - 1, z);
            Block feet = w.getBlockAt(x, y, z);
            Block head = w.getBlockAt(x, y + 1, z);
            if (below.isSolid() && feet.isPassable() && head.isPassable()) {
                double surface = y - 1 + below.getBoundingBox().getHeight();
                return new Location(w, at.getX(), Math.max(surface, y - 1 + 0.01), at.getZ());
            }
        }
        return null;
    }
    public UUID    carriedBy(UUID bodyId)     { return byCarried.get(bodyId); }

    /* ----------------------------------------------- listeners */

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent ev) {
        if (!ev.isSneaking()) return;
        // le porté qui s'accroupit ne descend pas (voir onDismount) ; seul le porteur dépose
        if (byCarried.containsKey(ev.getPlayer().getUniqueId())) return;
        dropEverything(ev.getPlayer());
    }

    /** Drop whatever {@code carrier} is hauling — registered KO carry,
     *  raw passenger (e.g. a dummy mount that bypassed the registry),
     *  or both. Public so {@code /sc drop} can call it. */
    public void dropEverything(Player carrier) {
        if (carrier == null) return;
        if (byCarrier.containsKey(carrier.getUniqueId())) {
            release(carrier.getUniqueId());
        }
        // Defensive: if anything else is riding the carrier (a dummy
        // attached via direct addPassenger, or a stale leftover from
        // a previous session), pop it off.
        releasing = true;
        try {
            for (org.bukkit.entity.Entity p :
                    new java.util.ArrayList<>(carrier.getPassengers())) {
                carrier.removePassenger(p);
            }
        } finally { releasing = false; }
    }

    /**
     * Un corps porté ne descend pas seul (s'accroupir sur les épaules du porteur le faisait tomber) : seul le
     * porteur dépose, ou le porté conscient se dégage en se débattant. Le client a déjà quitté la monture de son
     * côté : on la lui remet au tick suivant.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent ev) {
        if (releasing || !(ev.getEntity() instanceof Player body)) return;
        UUID carrierId = byCarried.get(body.getUniqueId());
        if (carrierId == null || !ev.getDismounted().getUniqueId().equals(carrierId)) return;
        Player carrier = Bukkit.getPlayer(carrierId);
        if (carrier == null || !carrier.isOnline() || !body.isOnline()
                || !carrier.getWorld().equals(body.getWorld())) return;
        ev.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!carrierId.equals(byCarried.get(body.getUniqueId()))) return;
            releasing = true;
            try {
                carrier.removePassenger(body);
                carrier.addPassenger(body);
            } finally { releasing = false; }
        });
    }

    /** Porté en douleur : Espace pour se débattre (front montant de la touche Saut). */
    @EventHandler
    public void onInput(PlayerInputEvent ev) {
        Player body = ev.getPlayer();
        UUID id = body.getUniqueId();
        boolean jump = ev.getInput().isJump();
        boolean was = Boolean.TRUE.equals(jumpHeld.put(id, jump));
        if (!jump || was) return;
        UUID carrierId = byCarried.get(id);
        if (carrierId != null && consentis.contains(id) && !plugin.ko().isKo(id)) {   // porté de plein gré : il descend
            release(carrierId);
            Player c = Bukkit.getPlayer(carrierId);
            if (c != null) c.sendActionBar(Component.text("Il descend.", NamedTextColor.GRAY));
            KoJournal.log(plugin, body, "Descend", "porté avec son accord");
            return;
        }
        if (carrierId == null || !struggle.containsKey(id) || !isPain(id)) return;
        long now = System.currentTimeMillis();
        double v = decayed(id, now) + 1.0 / presses();
        lastStruggle.put(id, now);
        Player carrier = Bukkit.getPlayer(carrierId);
        if (v >= 0.999) {
            struggle.remove(id);
            long protect = Math.max(0, plugin.getConfig().getLong("ko.douleur.lutte-protection-secondes", 6)) * 1000L;
            noCarryUntil.put(id, now + protect);
            release(carrierId);
            body.sendActionBar(Component.text("Tu te dégages !", NamedTextColor.GOLD));
            if (carrier != null) {
                carrier.sendActionBar(Component.text("Il s'est dégagé.", NamedTextColor.RED));
                KoJournal.log(plugin, body, "Se dégage", "porté par " + KoJournal.who(plugin, carrier));
            }
            return;
        }
        struggle.put(id, v);
        if (carrier != null) {
            Long lw = lastWarn.get(carrierId);
            if (lw == null || now - lw > 1000) {
                lastWarn.put(carrierId, now);
                carrier.sendActionBar(Component.text("Il se débat…", NamedTextColor.GRAY));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        UUID id = ev.getPlayer().getUniqueId();
        jumpHeld.remove(id);
        lastWarn.remove(id);
        noCarryUntil.remove(id);
    }

    private int presses() { return Math.max(2, plugin.getConfig().getInt("ko.douleur.lutte-appuis", 8)); }

    /** Jauge de lutte après l'oubli (un appui perdu toutes les {@code lutte-oubli-secondes}). */
    private double decayed(UUID id, long now) {
        Double v = struggle.get(id);
        if (v == null) return 0;
        Long last = lastStruggle.get(id);
        if (last == null) return v;
        double forget = Math.max(0.2, plugin.getConfig().getDouble("ko.douleur.lutte-oubli-secondes", 1.5));
        return Math.max(0, v - (now - last) / 1000.0 / forget / presses());
    }

    /* ----------------------------------------------- ticker */

    private void tick() {
        // Keep the KO lock location in sync with the carrier so the
        // body doesn't snap back when dropped.
        for (Map.Entry<UUID, UUID> e : new HashMap<>(byCarrier).entrySet()) {
            UUID carrierId = e.getKey();
            UUID bodyId    = e.getValue();
            Player carrier = Bukkit.getPlayer(carrierId);
            Player body    = Bukkit.getPlayer(bodyId);
            if (carrier == null || body == null) {
                releaseAllInvolving(carrierId);
                continue;
            }
            // Porteur hors d'état (KO, douleur, mort, gel) : il dépose — filet de sécurité derrière onKoEnter.
            if (plugin.ko().isKo(carrierId) || isPain(carrierId) || com.reborn.shinobicore.etat.Verrous.gele(carrierId)
                    || (plugin.deathScene() != null && plugin.deathScene().isDead(carrierId))) {
                release(carrierId);
                continue;
            }
            // If anything broke the passenger relationship (e.g.
            // teleport, vehicle eviction), drop cleanly.
            if (body.getVehicle() == null
                    || !body.getVehicle().getUniqueId().equals(carrier.getUniqueId())) {
                release(carrierId);
                continue;
            }
            KoState st = plugin.ko().getKo(bodyId);
            if (st != null) {
                st.setLockLocation(carrier.getLocation().clone());
                st.setCarrierPlayerId(carrierId);   // tombé KO sur les épaules : le corps reste porté
                struggle.remove(bodyId);            // inconscient : il ne se débat plus
                continue;
            }
            boolean dead = plugin.deathScene() != null && plugin.deathScene().isDead(bodyId);
            if (consentis.contains(bodyId)) continue;   // porté de plein gré : rien à vérifier de plus
            if (!dead && !isPain(bodyId)) {
                // il a repris des forces (soin) : on ne porte pas un conscient sans son accord
                release(carrierId);
                body.sendActionBar(Component.text("Tu reprends des forces : on te repose.", NamedTextColor.GRAY));
                continue;
            }
            if (struggle.containsKey(bodyId)) {
                long now = System.currentTimeMillis();
                struggle.put(bodyId, decayed(bodyId, now));
                lastStruggle.put(bodyId, now);
            }
        }
    }
}
