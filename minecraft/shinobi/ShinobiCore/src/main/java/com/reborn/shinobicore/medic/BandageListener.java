package com.reborn.shinobicore.medic;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bandages (lot KO-4) : n'importe qui peut panser un blessé avec une
 * {@link Medicine#BANDE}. Clic droit sur le joueur (ou accroupi + clic droit
 * dans le vide pour soi). Rend {@code medic.bandage.pv-pct} des PV max, sans
 * dépasser le plancher ({@code ko.plancher-pct}) : au-delà, il faut un médic.
 * Ne lève pas l'ATA. Sur un corps KO, un bandage peut suffire à le relever.
 *
 * <p>Passe avant le menu d'actions sur un corps KO (priorité LOWEST + annulation).
 */
public final class BandageListener implements Listener {

    private static final long COOLDOWN_MILLIS = 10_000L;

    private final ShinobiCore plugin;
    private final Map<UUID, Long> lastOn = new ConcurrentHashMap<>();   // patient →

    public BandageListener(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBandageOther(PlayerInteractEntityEvent ev) {
        if (ev.getHand() != EquipmentSlot.HAND) return;
        if (!(ev.getRightClicked() instanceof Player patient)) return;
        Player healer = ev.getPlayer();
        if (!isBandage(healer.getInventory().getItemInMainHand())) return;
        ev.setCancelled(true);
        apply(healer, patient);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBandageSelf(PlayerInteractEvent ev) {
        if (ev.getHand() != EquipmentSlot.HAND) return;
        if (ev.getAction() != Action.RIGHT_CLICK_AIR && ev.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = ev.getPlayer();
        if (!p.isSneaking() || !isBandage(p.getInventory().getItemInMainHand())) return;
        ev.setCancelled(true);
        apply(p, p);
    }

    private boolean isBandage(ItemStack it) {
        return it != null && !it.getType().isAir() && MedicineItem.typeOf(plugin, it) == Medicine.BANDE;
    }

    private void apply(Player healer, Player patient) {
        if (plugin.ko().isKo(healer.getUniqueId())) return;
        ShinobiCharacter pc = plugin.characters().getActive(patient.getUniqueId());
        if (pc == null) return;
        long now = System.currentTimeMillis();
        Long last = lastOn.get(patient.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            healer.sendActionBar(Component.text("Le pansement vient d'être posé, laisse-le tenir.", NamedTextColor.GRAY));
            return;
        }
        double max = patient.getMaxHealth();
        double floor = max * plugin.ko().floorPct();
        double hp = patient.getHealth();
        if (hp >= floor && !plugin.ko().isKo(patient.getUniqueId())) {
            healer.sendActionBar(Component.text("Un bandage ne suffit plus : il faut un médic.", NamedTextColor.GRAY));
            return;
        }
        lastOn.put(patient.getUniqueId(), now);
        ItemStack hand = healer.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);

        double gain = max * plugin.getConfig().getDouble("medic.bandage.pv-pct", 0.05);
        hp = Math.min(Math.max(hp, Math.min(floor, hp + gain)), max);
        patient.setHealth(hp);
        pc.setCurrentHp(hp);
        if (plugin.ko().isKo(patient.getUniqueId())) plugin.ko().checkHealRevive(patient);

        patient.getWorld().playSound(patient.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.2f);
        String who = healer.equals(patient) ? "Tu te poses un bandage." : "Bandage posé.";
        healer.sendActionBar(Component.text(who + " (" + Math.round(hp / max * 100) + " % des PV)", NamedTextColor.GREEN));
        if (!healer.equals(patient)) {
            patient.sendActionBar(Component.text("On te pose un bandage.", NamedTextColor.GREEN));
        }
    }
}
