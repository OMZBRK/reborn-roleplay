package fr.reborn.ost.plugin.broadcast;

import fr.reborn.ost.plugin.network.OstChannel;
import fr.reborn.ost.plugin.network.OstPacket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Logique de broadcast OST. Sépare le filtrage des joueurs cibles de
 * l'envoi du packet réseau pour faciliter les tests + la réutilisation
 * depuis les commandes.
 *
 * <p>Depuis Phase 2 : maintient un {@link OstZoneRegistry} pour permettre
 * aux joueurs qui rejoignent une zone de broadcast déjà active de recevoir
 * la track au timestamp courant. {@link #tick()} scanne les joueurs vs
 * zones actives et envoie un PLAY aux nouvelles subscriptions.
 */
public final class OstBroadcaster {

    private final Plugin plugin;
    private final OstZoneRegistry registry;

    public OstBroadcaster(Plugin plugin) {
        this(plugin, new OstZoneRegistry());
    }

    /** Pour injecter un registry custom dans les tests. */
    OstBroadcaster(Plugin plugin, OstZoneRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public OstZoneRegistry registry() { return registry; }

    /**
     * Crée une zone broadcast active et envoie le packet PLAY (offset = 0)
     * aux joueurs déjà dans le rayon. Les joueurs qui arrivent plus tard
     * sont notifiés par {@link #tick()}.
     *
     * @return nombre de joueurs notifiés initialement
     */
    public int playAtPosition(Location target, float radius, String trackId, float volume) {
        return playAtPosition(target, radius, trackId, volume, null);
    }

    /**
     * Variante avec {@code owner} (broadcast déclenché par un joueur via le
     * menu). Le propriétaire n'est PAS renvoyé le packet (il joue déjà sa
     * piste en local) mais est marqué subscribed pour que le late-join tick ne
     * la lui repousse pas. Seul lui pourra stop/pause la zone.
     */
    public int playAtPosition(Location target, float radius, String trackId, float volume, UUID owner) {
        if (target == null || target.getWorld() == null) return 0;
        OstZoneRegistry.ZoneRecord zone = registry.addZone(
            target.getWorld().getUID(), target.getX(), target.getY(), target.getZ(),
            radius, trackId, volume, owner);

        byte[] packet = OstPacket.playAtPosition(
            target.getX(), target.getY(), target.getZ(), radius, trackId, volume, 0f);

        List<Player> recipients = playersInRange(target, radius);
        for (Player p : recipients) {
            if (owner != null && owner.equals(p.getUniqueId())) {
                registry.markSubscribed(p.getUniqueId(), zone.id()); // joue en local
                continue;
            }
            p.sendPluginMessage(plugin, OstChannel.NAME, packet);
            registry.markSubscribed(p.getUniqueId(), zone.id());
        }
        Logger log = plugin.getLogger();
        log.info(String.format("Zone enregistree id=%s track=%s r=%.1f at %s(%.1f,%.1f,%.1f), %d joueur(s) subscribed",
            zone.id(), trackId, radius, target.getWorld().getName(),
            target.getX(), target.getY(), target.getZ(), recipients.size()));
        return recipients.size();
    }

    /** Broadcast global sans distance — tous les joueurs en ligne. */
    public int playGlobal(String trackId, float volume) {
        byte[] packet = OstPacket.playGlobal(trackId, volume);
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPluginMessage(plugin, OstChannel.NAME, packet);
            count++;
        }
        return count;
    }

    /**
     * Stoppe le son broadcast actuel pour les joueurs dans le radius
     * autour de {@code target}. Si {@code target == null} ou
     * {@code radius < 0}, stop pour TOUS les joueurs en ligne et purge
     * toutes les zones actives.
     *
     * <p>Note : on supprime aussi les zones correspondantes du registry —
     * sinon le tick suivant ré-enverrait un PLAY à n'importe quel joueur
     * encore dans le rayon. Le "stop" est définitif.
     */
    public int stop(Location target, float radius) {
        byte[] packet = OstPacket.stopBroadcast();
        List<Player> recipients;
        if (target == null || radius < 0) {
            recipients = new ArrayList<>(Bukkit.getOnlinePlayers());
            registry.clearAll();
        } else {
            recipients = playersInRange(target, radius);
            if (target.getWorld() != null) {
                registry.removeZonesNear(target.getWorld().getUID(), target.getX(),
                    target.getY(), target.getZ(), radius);
            }
        }
        for (Player p : recipients) {
            p.sendPluginMessage(plugin, OstChannel.NAME, packet);
        }
        return recipients.size();
    }

    /** Stoppe une zone précise (broadcast d'un joueur) : envoie StopBroadcast à
     *  ses auditeurs et retire la zone du registry. */
    public int stopZone(OstZoneRegistry.ZoneRecord zone) {
        return stopZone(zone, null);
    }

    /**
     * Variante avec {@code exclude} : ne pas envoyer StopBroadcast à ce joueur.
     * Utilisé quand un joueur REMPLACE son propre broadcast — il joue déjà la
     * nouvelle piste en local, un StopBroadcast le couperait à tort (et son
     * client enchaînerait sur la piste suivante).
     */
    public int stopZone(OstZoneRegistry.ZoneRecord zone, UUID exclude) {
        int n = 0;
        World world = Bukkit.getWorld(zone.worldId());
        if (world != null) {
            Location loc = new Location(world, zone.x(), zone.y(), zone.z());
            byte[] packet = OstPacket.stopBroadcast();
            for (Player p : playersInRange(loc, zone.radius())) {
                if (exclude != null && exclude.equals(p.getUniqueId())) continue;
                p.sendPluginMessage(plugin, OstChannel.NAME, packet);
                n++;
            }
        }
        registry.remove(zone.id());
        return n;
    }

    /** Met en pause / reprend une zone précise pour ses auditeurs. */
    public int pauseZone(OstZoneRegistry.ZoneRecord zone, boolean paused) {
        int n = 0;
        World world = Bukkit.getWorld(zone.worldId());
        if (world != null) {
            Location loc = new Location(world, zone.x(), zone.y(), zone.z());
            byte[] packet = OstPacket.pauseBroadcast(paused);
            for (Player p : playersInRange(loc, zone.radius())) {
                p.sendPluginMessage(plugin, OstChannel.NAME, packet);
                n++;
            }
        }
        return n;
    }

    /**
     * Scan O(zones × joueurs en ligne). Pour chaque (zone, joueur) :
     * <ul>
     *   <li>si même monde + distance ≤ radius + pas encore subscribed →
     *       envoie un PLAY avec {@code secOffset = elapsed since zone start}</li>
     *   <li>sinon : no-op</li>
     * </ul>
     * À appeler depuis {@link org.bukkit.scheduler.BukkitScheduler} toutes
     * les 20 ticks (1 s). À cette fréquence, un joueur qui sprint à 6 m/s
     * couvre 6 blocs entre deux ticks — encore largement dedans le rayon
     * typique (16-64).
     *
     * @return nombre de subscriptions ajoutées (pour métriques / logs)
     */
    public int tick() {
        int added = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            added += scanPlayer(p);
        }
        return added;
    }

    /**
     * Scan zones pour UN seul joueur. Utilisé depuis {@link #tick()} (boucle)
     * et depuis le listener {@code PlayerJoinEvent} avec un delay
     * ({@code runTaskLater}) parce qu'à la reconnexion le canal client n'est
     * pas immédiatement prêt — le tick périodique peut envoyer le packet
     * dans le vide pendant les ~3 premières secondes. Avec le scan ciblé
     * après delay, on garantit que le late-join arrive bien quand le client
     * est ready.
     *
     * @return nombre de subscriptions ajoutées pour ce joueur
     */
    public int scanPlayer(Player p) {
        int added = 0;
        long now = System.currentTimeMillis();
        Logger log = plugin.getLogger();
        Location loc = p.getLocation();
        UUID worldId = loc.getWorld() != null ? loc.getWorld().getUID() : null;
        for (OstZoneRegistry.ZoneRecord zone : registry.allZones()) {
            if (registry.isSubscribed(p.getUniqueId(), zone.id())) continue;
            double r2 = (double) zone.radius() * (double) zone.radius();
            double d2 = zone.distanceSquaredFrom(worldId, loc.getX(), loc.getY(), loc.getZ());
            if (d2 > r2) continue;

            float secOffset = (now - zone.startedAtMs()) / 1000f;
            byte[] packet = OstPacket.playAtPosition(
                zone.x(), zone.y(), zone.z(), zone.radius(),
                zone.trackId(), zone.volume(), secOffset);
            p.sendPluginMessage(plugin, OstChannel.NAME, packet);
            registry.markSubscribed(p.getUniqueId(), zone.id());
            added++;
            log.info(String.format("Late-join: %s -> zone %s (track=%s, seek=%.1fs)",
                p.getName(), zone.id(), zone.trackId(), secOffset));
        }
        return added;
    }

    /**
     * @return joueurs en ligne dans le même monde que {@code target},
     *         à distance ≤ {@code radius} blocks (distance euclidienne).
     */
    private static List<Player> playersInRange(Location target, float radius) {
        World world = target.getWorld();
        double rSquared = (double) radius * (double) radius;
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(world)) continue;
            if (p.getLocation().distanceSquared(target) <= rSquared) {
                out.add(p);
            }
        }
        return out;
    }
}
