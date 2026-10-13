package com.reborn.shinobicore.inventory;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ramassage <b>volontaire</b> (GAMEPLAN §3.3, décision du 2026-10-13) : marcher sur un objet ne le ramasse plus ; le
 * joueur vise l'objet et appuie sur la touche Ramasser du mod (F par défaut), qui envoie {@code pick:<uuid>} sur
 * {@code reborn:inventory}. Vérifié ici :
 * <ul>
 *   <li>l'objet existe, est dans le même monde, à 3 blocs au plus des yeux, <b>en vue</b> ;</li>
 *   <li>son délai de ramassage est écoulé, il n'est pas réservé à un autre joueur ;</li>
 *   <li>le joueur n'est ni KO, ni porté, ni étourdi, ni en scène de mort ;</li>
 *   <li>cadence ≤ 4 par seconde ;</li>
 *   <li>place (avec fusion des piles) dans la barre 1-9 puis le sac ;</li>
 *   <li>poids : on peut dépasser la limite jusqu'à <b>130 %</b>, pas au-delà ; une pile est ramassée en partie si elle
 *       ne rentre pas toute (« +2 Kunai — 1 reste au sol »).</li>
 * </ul>
 * Les flèches (et tridents) gardent le ramassage de Minecraft. Créatif / spectateur : inchangé.
 */
final class Ramassage implements Listener {

    private final ShinobiCore plugin;
    private final InventoryManager inv;
    private final Map<UUID, Long> dernier = new ConcurrentHashMap<>();

    Ramassage(ShinobiCore plugin, InventoryManager inv) {
        this.plugin = plugin;
        this.inv = inv;
    }

    private static boolean survie(Player p) {
        GameMode g = p.getGameMode();
        return g == GameMode.SURVIVAL || g == GameMode.ADVENTURE;
    }

    /* ─────────── plus de ramassage automatique ─────────── */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttempt(PlayerAttemptPickupItemEvent e) {
        if (!survie(e.getPlayer())) return;
        e.setFlyAtPlayer(false);
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && survie(p)) e.setCancelled(true);
    }

    void oublier(UUID id) {
        dernier.remove(id);
    }

    /* ─────────── pick:<uuid> ─────────── */

    /** Traite une demande de ramassage ; renvoie true si l'inventaire a changé. */
    boolean ramasser(Player p, RpBag b, String arg) {
        long now = System.currentTimeMillis();
        InventaireConfig cfg = InventaireConfig.get(plugin);
        Long d = dernier.get(p.getUniqueId());
        if (d != null && now - d < cfg.cadenceMs) return false;
        dernier.put(p.getUniqueId(), now);

        String verrou = inv.lockReason(p);
        if (verrou != null) {
            Notifs.msg(p, "etat", "止", "Impossible " + verrou(verrou), null);
            return false;
        }
        if (!survie(p)) return false;
        Item it = trouver(p, arg);
        if (it == null) return false;
        Location oeil = p.getEyeLocation();
        Location centre = it.getLocation().add(0, 0.25, 0);
        if (centre.getWorld() != oeil.getWorld() || centre.distance(oeil) > cfg.distance + 0.6) return false;
        if (!p.hasLineOfSight(it)) return false;
        int delai = it.getPickupDelay();
        if (delai >= Short.MAX_VALUE || !it.canPlayerPickup()) return false;
        if (delai > 0) return false;
        UUID owner = it.getOwner();
        if (owner != null && !owner.equals(p.getUniqueId())) {
            Notifs.msg(p, "refus", "印", "Pas à toi", "cet objet est réservé");
            return false;
        }

        ItemStack sol = it.getItemStack();
        if (sol == null || sol.getType().isAir()) return false;
        ItemStack unite = RareteItems.normaliser(sol.clone());
        unite.setAmount(1);
        int total = sol.getAmount();

        // place (fusion des piles comprise)
        int place = inv.placePour(p, b, unite, total);
        if (place <= 0) {
            Notifs.msg(p, "refus", "袋", "Plus assez de place", "libère une case");
            son(p, false);
            return false;
        }
        // poids : jusqu'à 130 % de la limite
        double unitPoids = RpWeights.of(unite);
        double max = b.maxWeight();
        double cur = inv.weight(p);
        int parPoids = total;
        if (unitPoids > 0 && max > 0) {
            double marge = max * cfg.poidsMaxRatio - cur;
            parPoids = (int) Math.floor((marge + 1e-6) / unitPoids);
        }
        if (parPoids <= 0) {
            double trop = cur + unitPoids - max * cfg.poidsMaxRatio;
            Notifs.msg(p, "refus", "重", "Trop lourd", String.format(java.util.Locale.FRANCE, "%.1f kg de trop", Math.max(0.1, trop)));
            son(p, false);
            return false;
        }
        int n = Math.min(total, Math.min(place, parPoids));
        ItemStack pris = unite.clone();
        pris.setAmount(n);
        int reste = inv.ranger(p, b, pris);
        int ramasse = n - reste;
        if (ramasse <= 0) return false;

        p.playPickupItemAnimation(it, ramasse);
        if (ramasse >= total) {
            it.remove();
        } else {
            ItemStack r = sol.clone();
            r.setAmount(total - ramasse);
            it.setItemStack(r);
        }
        son(p, true);
        int resteSol = total - ramasse;
        String suite = resteSol > 0 ? resteSol + " reste" + (resteSol > 1 ? "nt" : "") + " au sol" : null;
        ItemStack trace = unite.clone();
        trace.setAmount(ramasse);
        Notifs.objet(p, true, trace, ramasse, suite);
        JournalObjets.noter(plugin, p, "RAMASSE", trace, ramasse, it.getLocation(), suite);
        return true;
    }

    private Item trouver(Player p, String arg) {
        if (arg == null || arg.isBlank()) return null;
        Entity e = null;
        try {
            e = Bukkit.getEntity(UUID.fromString(arg.trim()));
        } catch (IllegalArgumentException ignored) {
            // identifiant illisible
        }
        if (!(e instanceof Item it) || !it.isValid() || it.getWorld() != p.getWorld()) return null;
        return it;
    }

    private static void son(Player p, boolean ok) {
        if (ok) p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.35f, 1.1f);
        else p.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 0.5f, 0.7f);
    }

    static String verrou(String raison) {
        return switch (raison) {
            case "KO" -> "en KO";
            case "porté" -> "en étant porté";
            case "étourdi" -> "étourdi";
            case "mort" -> "pendant la scène de mort";
            default -> raison;
        };
    }
}
