package com.reborn.shinobicore.medic;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.injury.BodyPart;
import com.reborn.shinobicore.ko.injury.DamageOrigin;
import com.reborn.shinobicore.ko.injury.Injury;
import com.reborn.shinobicore.ko.injury.InjuryMerger;
import com.reborn.shinobicore.ko.injury.InjuryType;
import com.reborn.shinobicore.ko.injury.Severity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Right-clicking a {@code Pilule du Soldat} item consumes one:
 * <ul>
 *   <li>+100 chakra (clamped to the active character's pool max)</li>
 *   <li>Speed II for 3 minutes (3600 ticks)</li>
 *   <li>5 fresh Faible Hématome on {@link BodyPart#BUSTE_DROIT}</li>
 * </ul>
 *
 * <p>The five hematomas trigger {@link InjuryMerger} immediately —
 * if the character already has 2+ Faible bruises on the right
 * torso, the new five push the part across the 7-threshold and the
 * whole bucket consolidates into a Moyen. Repeated pill abuse has
 * the obvious medical consequence.
 *
 * <p>Cancels the underlying {@link PlayerInteractEvent} so the
 * vanilla "consume" animation doesn't fire — Pilules are sugar
 * items, vanilla treats them as inedible by default, but cancelling
 * keeps the right-click from doing anything else (e.g. block place
 * if facing a block).
 */
public final class PiluleConsumeListener implements Listener {

    private static final int  CHAKRA_RESTORE = 100;
    private static final int  SPEED_DURATION_TICKS = 20 * 60 * 3;
    private static final int  HEMATOME_COUNT = 5;

    private final ShinobiCore plugin;

    public PiluleConsumeListener(ShinobiCore plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent ev) {
        if (ev.getAction() != Action.RIGHT_CLICK_AIR
                && ev.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (ev.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = ev.getItem();
        if (item == null) return;
        Medicine kind = MedicineItem.typeOf(plugin, item);
        if (kind != Medicine.PILULE_SOLDAT) return;
        ev.setCancelled(true);

        Player p = ev.getPlayer();
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());

        // Chakra refill on the active character's pool.
        if (c != null) {
            double next = Math.min(
                    c.chakra().current() + CHAKRA_RESTORE,
                    c.chakra().max());
            c.chakra().setCurrent(next);
        }

        // Speed II for 3 minutes. Particles off, ambient on so it
        // reads as an in-character buff rather than a vanilla potion.
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                SPEED_DURATION_TICKS, 1, true, true, true));

        // 5 Faible hématomes on the right torso. Origin AUTRE because
        // the body's reaction to chakra burst doesn't fit the
        // attack-source taxonomy.
        if (c != null) {
            for (int i = 0; i < HEMATOME_COUNT; i++) {
                c.addInjury(Injury.create(BodyPart.BUSTE_DROIT,
                        InjuryType.HEMATOME, DamageOrigin.AUTRE,
                        Severity.FAIBLE));
            }
            InjuryMerger.mergeAll(c.injuries());
            plugin.characterRepository().save(c);
        }

        // Consume one item from the stack.
        item.setAmount(item.getAmount() - 1);

        p.playSound(p.getLocation(),
                Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.4f);
        p.sendMessage(Component.text(
                "Tu avales la Pilule du Soldat. Le chakra remonte, le corps proteste.",
                NamedTextColor.GRAY));
    }
}
