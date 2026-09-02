package com.reborn.shinobicore.character.listener;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.CharacterManager;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/**
 * Runs join/quit flows for the character system:
 *
 * <ul>
 *   <li><b>Join (no characters):</b> apply {@code NoCharacterLockdown} until a
 *       character is created for them.</li>
 *   <li><b>Join (has characters):</b> open the character list screen one
 *       tick later, regardless of whether they own 1 or many — there is no
 *       auto-select any more so position-per-character stays predictable.</li>
 *   <li><b>Quit:</b> persist the current HP / last position on the active
 *       character, then unload the player's roster from memory.</li>
 * </ul>
 */
public class CharacterLifecycleListener implements Listener {

    private final ShinobiCore plugin;

    public CharacterLifecycleListener(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        CharacterManager mgr = plugin.characters();
        List<ShinobiCharacter> roster = mgr.getAll(p.getUniqueId());

        // Sélection pilotée par le mod client (build + overlay Zenkai). Ouverte
        // MÊME à 0 perso (l'écran présente la tuile « Créer ») → un nouveau joueur
        // peut créer son 1er perso sans staff. Différé d'un tick pour laisser le
        // client prêt. Si la feature est désactivée, on retombe sur l'ancien
        // comportement (hub coffre si roster, lockdown+staff si vide).
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.characterSelect() != null && plugin.characterSelect().beginSelection(p)) {
                return;
            }
            if (roster.isEmpty()) {
                if (plugin.noCharacterLockdown() != null) {
                    plugin.noCharacterLockdown().apply(p);
                }
                p.sendMessage(Component.text(
                        "Vous devez contacter un Staff pour créer votre personnage.",
                        NamedTextColor.RED));
            } else {
                plugin.coreGui().openHub(p);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        CharacterManager mgr = plugin.characters();
        ShinobiCharacter c = mgr.getActive(p.getUniqueId());
        if (c != null) {
            c.setCurrentHp(p.getHealth());
            c.setLastLocation(p.getLocation());
            // Inventory too — previously only the 60s autosave and the
            // switch path captured it, so a quit right after picking
            // something up could lose up to a minute of inventory.
            c.captureInventoryFrom(p);
        }
        // Revert chat / tab / nameplate to the vanilla Minecraft username so
        // the player rejoins cleanly if they reconnect before we re-apply.
        CharacterDisplay.reset(p);
        mgr.unload(p.getUniqueId());
        if (plugin.noCharacterLockdown() != null) {
            plugin.noCharacterLockdown().release(p);
        }
    }

}
