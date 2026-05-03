package fr.reborn.guardian;

import fr.reborn.guardian.listener.PlayerJoinListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reborn Guardian — point d'entree du plugin Paper.
 *
 * <p>Role (cf PLAN_CONCEPTION_LAUNCHER.md §9.5) :
 * <ul>
 *   <li>Valider que le joueur qui se connecte presente un token Reborn signe
 *       (preuve que le launcher officiel l'a lance et qu'il n'a pas modifie
 *       l'integrite du game directory).</li>
 *   <li>Refuser les joueurs sans token valide avec un message explicite.</li>
 *   <li>Synchroniser les actions staff vers Discord (kicks, bans, mutes)
 *       via webhook.</li>
 *   <li>Exposer une API HTTP interne (Bukkit scheduler + jetty embedded)
 *       que l'API Reborn appelle pour pousser des sanctions live.</li>
 * </ul>
 *
 * <p>Pour le scaffold initial, on enregistre un listener bidon qui log les
 * joins. Le wiring complet du systeme de validation arrive avec le mod
 * Reborn Integrity (cf §9.4) qui pousse le token au join.
 */
public final class RebornGuardian extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Reborn Guardian " + getPluginMeta().getVersion() + " demarre.");
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("Reborn Guardian arrete.");
    }
}
