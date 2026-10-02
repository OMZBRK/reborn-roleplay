package com.reborn.shinobicore.map;

import com.reborn.shinobicore.map.PlaceRegistry.MapDef;
import com.reborn.shinobicore.map.PlaceRegistry.Place;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:map} (bidirectionnel) — la <b>carte du monde</b> du mod client.
 * Même forme que {@code reborn:stats} : octets UTF-8 bruts.
 *
 * <p>S2C = un JSON : carte du monde courant du joueur, ses lieux, et {@code canTp}.
 * La position du joueur n'est PAS envoyée : le client connaît la sienne, et
 * aucune autre position de joueur ne transite (PLAN : pas de mini-map qui révèle
 * les joueurs).
 *
 * <p>C2S : {@code open} · {@code tp:<placeId>} (staff uniquement, revérifié ici).
 *
 * <p>Ouvrir la carte demande de <b>tenir l'objet carte</b> ({@link MapItem}) en main —
 * touche M ({@code open}) ou clic droit avec l'objet. La carte affichée est celle de
 * l'objet. Le staff ({@code shinobicore.map.admin}) peut ouvrir sans objet : carte
 * du monde courant.
 */
public final class MapChannel implements PluginMessageListener, Listener {

    public static final String CHANNEL = "reborn:map";
    public static final String PERM_TP = "shinobicore.map.tp";
    public static final String PERM_ADMIN = "shinobicore.map.admin";

    private final JavaPlugin plugin;
    private final PlaceRegistry places;

    public MapChannel(JavaPlugin plugin, PlaceRegistry places) {
        this.plugin = plugin;
        this.places = places;
    }

    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL)) m.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    /* ------------------------------------------------------------ inbound */

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        String msg = new String(message, StandardCharsets.UTF_8).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handle(p, msg));
    }

    private void handle(Player p, String msg) {
        if (msg.equals("open")) {
            open(p);
        } else if (msg.startsWith("tp:")) {
            teleport(p, msg.substring(3));
        }
    }

    /** Ouvre la carte tenue en main ; refus (barre d'action) si le joueur n'en tient pas. */
    public void open(Player p) {
        String held = MapItem.held(plugin, p);
        if (held != null) {
            MapDef def = places.map(held);
            if (def == null) {
                p.sendActionBar(Component.text("Cette carte est illisible (région inconnue).", NamedTextColor.RED));
                return;
            }
            push(p, def, true);
        } else if (p.hasPermission(PERM_ADMIN)) {
            push(p, true);
        } else {
            p.sendActionBar(Component.text("Vous n'avez pas de carte en main.", NamedTextColor.RED));
        }
    }

    /** Clic droit avec une carte en main = l'ouvrir. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (MapItem.mapId(plugin, e.getItem()) == null) return;
        e.setCancelled(true);
        open(e.getPlayer());
    }

    /** Téléporte vers un lieu. Retourne false (avec message) si refusé. */
    public boolean teleport(Player p, String placeId) {
        if (!p.hasPermission(PERM_TP)) {
            p.sendActionBar(Component.text("La téléportation par la carte est réservée au staff.", NamedTextColor.RED));
            return false;
        }
        Place pl = places.place(placeId);
        if (pl == null) {
            p.sendActionBar(Component.text("Lieu inconnu : " + placeId, NamedTextColor.RED));
            return false;
        }
        Location to = pl.location(Bukkit.getServer());
        if (to == null) {
            p.sendActionBar(Component.text("Monde « " + pl.world() + " » non chargé.", NamedTextColor.RED));
            return false;
        }
        p.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
        p.sendActionBar(Component.text("Téléporté : " + pl.name(), NamedTextColor.GOLD));
        return true;
    }

    /* ----------------------------------------------------------- outbound */

    /** Envoie la carte du monde courant. {@code open} demande au client d'ouvrir l'écran. */
    public void push(Player p, boolean open) {
        MapDef def = places.mapFor(p.getWorld());
        if (def == null) {
            p.sendActionBar(Component.text("Aucune carte n'est configurée.", NamedTextColor.RED));
            return;
        }
        push(p, def, open);
    }

    /** Envoie une carte précise. */
    public void push(Player p, MapDef def, boolean open) {
        byte[] bytes = buildJson(def, p, open).getBytes(StandardCharsets.UTF_8);
        try {
            p.sendPluginMessage(plugin, CHANNEL, bytes);
        } catch (Exception ignored) {
            // pas de mod-hud → /carte liste les lieux en chat.
        }
    }

    String buildJson(MapDef def, Player p, boolean open) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"open\":").append(open)
          .append(",\"map\":").append(str(def.id()))
          .append(",\"title\":").append(str(def.title()))
          .append(",\"here\":").append(def.world().equalsIgnoreCase(p.getWorld().getName()))
          .append(",\"canTp\":").append(p.hasPermission(PERM_TP))
          .append(",\"places\":[");
        boolean first = true;
        for (Place pl : places.placesOf(def.id())) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":").append(str(pl.id()))
              .append(",\"name\":").append(str(pl.name()))
              .append(",\"type\":").append(str(pl.type().key()))
              .append(",\"x\":").append(Math.round(pl.x()))
              .append(",\"z\":").append(Math.round(pl.z()))
              .append('}');
        }
        return sb.append("]}").toString();
    }

    private static String str(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
