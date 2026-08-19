package com.reborn.shinobicombat.combat;

import com.reborn.shinobicombat.net.CombatChannel;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.KoService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Cœur du taïjutsu — <b>incrément 1</b> : le M1 (attaque mêlée vanilla) est
 * l'intention de coup ; le serveur est autoritaire.
 *
 * <p>Sur chaque coup mêlée d'un joueur ayant un personnage actif :
 * <ol>
 *   <li>Refuse si le joueur est KO.</li>
 *   <li><b>Porte de stamina</b> (anti-spam) : coup annulé si stamina insuffisante.</li>
 *   <li>Fixe les dégâts de base Reborn (combos/finitions contextuelles = incréments
 *       suivants) — la suite passe par le pipeline de dégâts ShinobiCore (armure,
 *       classification, KO, sync HP).</li>
 *   <li>Émet {@code reborn:combat} vers le client de l'attaquant : damage indicator
 *       sur la cible + stamina courante (anneau curseur).</li>
 * </ol>
 *
 * <p>Note d'ordre : on tourne en {@link EventPriority#LOW} pour poser nos dégâts
 * avant les lecteurs par défaut (dont le mannequin {@code dummy}). Le combo, les
 * finitions (aérien→projection, sol→coups rapides) et le M2 (blocage/parry)
 * arriveront aux incréments 3-4 via des intentions client sur {@code reborn:combat}.
 */
public final class CombatListener implements Listener {

    /** Dégâts de base d'un M1 taïjutsu (réglable). */
    private static final double M1_BASE_DAMAGE = 4.0;
    /** Coût stamina d'un M1 (réglable) — anti-spam. Avec la régén gelée 1,2 s
     *  après un coup, ~5-6 coups enchaînés vident la réserve. */
    private static final double M1_STAMINA_COST = 18.0;

    private final Plugin plugin;
    private final CharacterService characters;
    private final KoService ko; // peut être null si le service n'est pas exposé
    private final StaminaManager stamina;

    public CombatListener(Plugin plugin, CharacterService characters, KoService ko, StaminaManager stamina) {
        this.plugin = plugin;
        this.characters = characters;
        this.ko = ko;
        this.stamina = stamina;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        // Uniquement les coups mêlée directs (pas projectiles/jutsu).
        DamageCause cause = e.getCause();
        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        // Combat RP uniquement : l'attaquant doit avoir un personnage actif.
        if (characters.getActive(attacker.getUniqueId()) == null) return;

        // Un joueur KO ne frappe pas.
        if (ko != null && ko.isKo(attacker.getUniqueId())) {
            e.setCancelled(true);
            return;
        }

        // Porte de stamina — anti-spam.
        if (!stamina.tryConsume(attacker.getUniqueId(), M1_STAMINA_COST)) {
            e.setCancelled(true);
            attacker.sendActionBar(Component.text("Stamina épuisée", NamedTextColor.RED));
            // Renvoie la stamina réelle (le client corrige sa prédiction).
            CombatChannel.sendStamina(plugin, attacker, stamina.get(attacker.getUniqueId()), stamina.max());
            return;
        }

        // Dégâts de base Reborn — le reste du pipeline ShinobiCore s'applique.
        e.setDamage(M1_BASE_DAMAGE);

        // Feedback client : damage indicator + stamina.
        CombatChannel.sendHit(plugin, attacker, victim.getEntityId(), M1_BASE_DAMAGE);
        CombatChannel.sendStamina(plugin, attacker, stamina.get(attacker.getUniqueId()), stamina.max());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stamina.clear(e.getPlayer().getUniqueId());
    }
}
