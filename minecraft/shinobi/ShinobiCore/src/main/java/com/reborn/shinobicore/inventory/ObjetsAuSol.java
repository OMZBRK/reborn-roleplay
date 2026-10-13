package com.reborn.shinobicore.inventory;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Objets au sol (GAMEPLAN §3.3, décision du 2026-10-13) :
 * <ul>
 *   <li>la <b>rareté</b> résolue est posée sur l'objet à son apparition ({@code reborn:rarete}) : le mod la lit pour
 *       l'aura au sol ;</li>
 *   <li>la <b>disparition</b> est gérée ici, plus par Minecraft (5 min) : <b>10 min</b>, <b>15 min</b> pour un objet
 *       lâché par (ou près d')un joueur KO, <b>jamais</b> pour un rouleau unique / un objet de quête (rendus
 *       invulnérables au feu, à la lave, aux explosions) ;</li>
 *   <li>une disparition d'objet Rare et plus (ou unique) est journalisée.</li>
 * </ul>
 * Horloge murale (clé d'entité {@code reborn:sol_fin}, ms epoch, 0 = jamais) : un objet d'un tronçon déchargé expire
 * au rechargement.
 */
final class ObjetsAuSol implements Listener {

    static final NamespacedKey FIN = new NamespacedKey("reborn", "sol_fin");

    private final ShinobiCore plugin;

    ObjetsAuSol(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    void start() {
        long periode = InventaireConfig.get(plugin).balayageTicks;
        Bukkit.getScheduler().runTaskTimer(plugin, this::balayer, periode, periode);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent e) {
        Item it = e.getEntity();
        ItemStack s = it.getItemStack();
        if (s == null || s.getType().isAir()) return;
        InventaireConfig cfg = InventaireConfig.get(plugin);
        RareteItems.poser(s);
        it.setItemStack(s);
        it.setUnlimitedLifetime(true);
        long fin;
        if (RareteItems.unique(s)) {
            fin = 0L;
            it.setInvulnerable(true);
        } else {
            fin = System.currentTimeMillis() + (lacheParUnKo(it) ? cfg.disparitionKoMs : cfg.disparitionMs);
        }
        it.getPersistentDataContainer().set(FIN, PersistentDataType.LONG, fin);
    }

    /** Lâché par un joueur KO (lanceur), ou apparu tout près d'un joueur KO / en scène de mort sans lanceur. */
    private boolean lacheParUnKo(Item it) {
        if (plugin.ko() == null) return false;
        UUID t = it.getThrower();
        if (t != null) return plugin.ko().isKo(t) || (plugin.deathScene() != null && plugin.deathScene().isDead(t));
        for (Player p : it.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(it.getLocation()) > 2.25) continue;
            if (plugin.ko().isKo(p.getUniqueId())) return true;
            if (plugin.deathScene() != null && plugin.deathScene().isDead(p.getUniqueId())) return true;
        }
        return false;
    }

    private void balayer() {
        long now = System.currentTimeMillis();
        for (World w : Bukkit.getWorlds()) {
            for (Item it : w.getEntitiesByClass(Item.class)) {
                Long fin = it.getPersistentDataContainer().get(FIN, PersistentDataType.LONG);
                if (fin == null || fin == 0L || fin > now || !it.isValid()) continue;
                // Objet de décor posé par un plugin (jamais ramassable) : on n'y touche pas.
                if (!it.canPlayerPickup() || it.getPickupDelay() >= Short.MAX_VALUE) continue;
                ItemStack s = it.getItemStack();
                RpRarity r = RareteItems.of(s);
                if (r.auMoins(RpRarity.RARE)) {
                    JournalObjets.noter(plugin, null, "DISPARU", s, s.getAmount(), it.getLocation(), "délai au sol écoulé");
                }
                it.remove();
            }
        }
    }
}
