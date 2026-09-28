package com.reborn.shinobicombat.combat;

import com.reborn.shinobicombat.net.CombatChannel;
import com.reborn.shinobicore.api.EnduranceService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * {@link EnduranceService} over {@link StaminaManager} — publié dans le
 * ServicesManager pour que ShinobiAbilities fasse payer les techniques de
 * taïjutsu / bukijutsu en endurance (même barre que le M1, même anneau HUD).
 */
public final class EnduranceBridge implements EnduranceService {

    private final Plugin plugin;
    private final StaminaManager stamina;

    public EnduranceBridge(Plugin plugin, StaminaManager stamina) {
        this.plugin = plugin;
        this.stamina = stamina;
    }

    @Override
    public double current(Player player) { return stamina.get(player.getUniqueId()); }

    @Override
    public double max(Player player) { return stamina.max(player.getUniqueId()); }

    @Override
    public boolean tryConsume(Player player, double amount) {
        boolean ok = stamina.tryConsume(player.getUniqueId(), amount);
        CombatChannel.sendStamina(plugin, player, stamina.get(player.getUniqueId()),
                stamina.max(player.getUniqueId()));
        return ok;
    }
}
