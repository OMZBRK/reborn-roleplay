package com.reborn.shinobicombat;

import com.reborn.shinobicombat.combat.CombatListener;
import com.reborn.shinobicombat.combat.StaminaManager;
import com.reborn.shinobicombat.command.ResetCooldownsCommand;
import com.reborn.shinobicombat.net.CombatChannel;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.KoService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * ShinobiCombat — PvP taïjutsu (M1 combos, M2 blocage/parry, stamina) pour le
 * serveur RP Reborn.
 *
 * <p>Soft-depend sur ShinobiCore : sans lui (personnages, pipeline de dégâts,
 * KO), le plugin se désactive. Les services sont résolus via le seam
 * {@code ServicesManager} de ShinobiCore, comme ShinobiAbilities.
 *
 * <p><b>Incrément 1</b> : cœur serveur autoritaire — le M1 porte des dégâts de
 * base sous porte de stamina, et pousse {@code reborn:combat} (damage indicator
 * + stamina) au client. Combos/finitions/M2 et le client (anims, anneau curseur,
 * indicators) suivent aux incréments 2-4.
 */
public final class ShinobiCombat extends JavaPlugin {

    private static final long REGEN_PERIOD_TICKS = 5L; // 0,25 s
    private static final double REGEN_DT_SECONDS = REGEN_PERIOD_TICKS / 20.0;

    private StaminaManager stamina;
    private BukkitTask regenTask;

    @Override
    public void onEnable() {
        // Lien ShinobiCore via le seam ServicesManager (pas la classe concrète).
        ServicesManager services = getServer().getServicesManager();
        CharacterService characters = services.load(CharacterService.class);
        if (characters == null) {
            getLogger().severe("ShinobiCore introuvable/désactivé — ShinobiCombat "
                    + "ne peut pas démarrer sans les personnages.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        KoService ko = services.load(KoService.class); // optionnel

        this.stamina = new StaminaManager();

        // Canal serveur→client pour le HUD combat (damage indicators + stamina + anims).
        getServer().getMessenger().registerOutgoingPluginChannel(this, CombatChannel.CHANNEL);

        CombatListener combat = new CombatListener(this, characters, ko, stamina);
        getServer().getPluginManager().registerEvents(combat, this);

        // Le taïjutsu (M1) passe par l'event de dégât mêlée vanilla, qui ne NAÎT
        // que si le monde autorise le PvP. Sur ce serveur le PvP est supprimé au
        // niveau du monde malgré server.properties (diagnostic confirmé). On force
        // donc le flag ON au runtime sur tous les mondes déjà chargés — aucun autre
        // plugin ne touche ce flag, ça reste stable.
        for (org.bukkit.World w : getServer().getWorlds()) {
            if (!w.getPVP()) {
                w.setPVP(true);
                getLogger().info("PvP forcé ON sur le monde '" + w.getName() + "'.");
            }
        }

        // Canal client→serveur pour les intentions combat (M2).
        getServer().getMessenger().registerIncomingPluginChannel(this, CombatChannel.CHANNEL_IN, combat);

        // Commande staff : reset des cooldowns d'aptitudes (test dev).
        getCommand("resetcd").setExecutor(new ResetCooldownsCommand(this, combat));

        // Pont endurance pour les techniques MagicSpells (ex. M1 kenjutsu = item paper,
        // hors moteur mêlée vanilla) : /scendurance débite l'endurance d'un joueur
        // (commande générique, réutilisable par d'autres techniques MS).
        getCommand("scendurance").setExecutor(
                new com.reborn.shinobicombat.command.EnduranceCommand(this, stamina));

        // Gate SpellCastEvent (réflexion) : (1) débite + bloque le M1 kenjutsu si
        // l'endurance est vide ; (2) annule TOUT jutsu tant que le lanceur est en parade.
        new com.reborn.shinobicombat.combat.KenjutsuEnduranceGate(this, stamina, combat).register();

        // Régénération de stamina + sync client léger (uniquement ceux qui régénèrent).
        this.regenTask = getServer().getScheduler().runTaskTimer(this, () -> {
            stamina.tickRegen(REGEN_DT_SECONDS);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (characters.getActive(p.getUniqueId()) == null) continue;
                double cur = stamina.get(p.getUniqueId());
                if (cur < stamina.max()) {
                    CombatChannel.sendStamina(this, p, cur, stamina.max());
                }
            }
        }, REGEN_PERIOD_TICKS, REGEN_PERIOD_TICKS);

        getLogger().info("ShinobiCombat activé — taïjutsu incrément 2 (combos M1 + M2 lourd).");
    }

    @Override
    public void onDisable() {
        if (regenTask != null) regenTask.cancel();
        if (getServer().getMessenger().isOutgoingChannelRegistered(this, CombatChannel.CHANNEL)) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, CombatChannel.CHANNEL);
        }
        if (getServer().getMessenger().isIncomingChannelRegistered(this, CombatChannel.CHANNEL_IN)) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, CombatChannel.CHANNEL_IN);
        }
    }
}
