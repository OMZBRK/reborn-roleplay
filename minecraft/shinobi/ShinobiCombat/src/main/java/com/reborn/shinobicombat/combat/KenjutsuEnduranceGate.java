package com.reborn.shinobicombat.combat;

import com.reborn.shinobicombat.net.CombatChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Gate d'ENDURANCE pour le M1 <b>kenjutsu</b> — une technique MagicSpells liée à
 * l'item d'arme Reborn ({@code epee_kenjutsu} = paper), donc HORS du moteur mêlée
 * vanilla de ShinobiCombat. On écoute le {@code SpellCastEvent} de MagicSpells
 * <b>par réflexion</b> (aucune dépendance de compilation sur MagicSpells) : au
 * moment où le sort {@code MS_KENJTSU_M1} va partir, on débite {@link #COST}
 * d'endurance au lanceur ({@link StaminaManager#tryConsume}). Si l'endurance est
 * insuffisante → on <b>annule le sort</b> ({@code setCancelled}) : plus moyen de
 * taper une fois la barre vidée, exactement comme le taïjutsu mains nues.
 *
 * <p>Source de vérité unique = {@link StaminaManager} (la même barre que le HUD /
 * le taïjutsu). Tout est gardé en {@code try/catch} → no-op propre si MagicSpells
 * est absent ou si son API évolue (le jeu n'est jamais bloqué).
 */
public final class KenjutsuEnduranceGate implements Listener {

    /** Nom interne du sort MagicSpells (clé YAML dans {@code spells-m1.yml}). */
    private static final String SPELL = "MS_KENJTSU_M1";
    /** Endurance débitée au lanceur par coup (choix user : 15 ; réglable). */
    private static final double COST = 15.0;

    private final Plugin plugin;
    private final StaminaManager stamina;
    private final CombatListener combat;

    private Method mGetSpell, mInternalName, mGetState, mGetCaster, mSetCancelled;

    public KenjutsuEnduranceGate(Plugin plugin, StaminaManager stamina, CombatListener combat) {
        this.plugin = plugin;
        this.stamina = stamina;
        this.combat = combat;
    }

    /** Installe le listener réflectif si MagicSpells est présent. */
    @SuppressWarnings("unchecked")
    public void register() {
        if (Bukkit.getPluginManager().getPlugin("MagicSpells") == null) {
            plugin.getLogger().info("MagicSpells absent — gate endurance kenjutsu inactif.");
            return;
        }
        try {
            Class<? extends Event> ev = (Class<? extends Event>)
                    Class.forName("com.nisovin.magicspells.events.SpellCastEvent");
            mGetSpell = ev.getMethod("getSpell");
            mGetState = ev.getMethod("getSpellCastState");
            mGetCaster = ev.getMethod("getCaster");
            mSetCancelled = ev.getMethod("setCancelled", boolean.class);
            mInternalName = Class.forName("com.nisovin.magicspells.Spell").getMethod("getInternalName");
            EventExecutor exec = (listener, event) -> handle(event);
            Bukkit.getPluginManager().registerEvent(ev, this, EventPriority.HIGH, exec, plugin);
            plugin.getLogger().info("Gate endurance kenjutsu actif (SpellCastEvent " + SPELL
                    + ", coût " + COST + ").");
        } catch (Throwable t) {
            plugin.getLogger().warning("Gate endurance kenjutsu NON installé (" + t + ").");
        }
    }

    private void handle(Event event) {
        try {
            Object spell = mGetSpell.invoke(event);
            if (spell == null) return;
            // Ne gate/débite qu'à l'état NORMAL (le cast va réellement partir).
            if (!"NORMAL".equals(String.valueOf(mGetState.invoke(event)))) return;
            if (!(mGetCaster.invoke(event) instanceof Player p)) return;

            // EN PARADE ON NE LANCE PAS DE JUTSU : tant que le lanceur garde (touche C),
            // on annule TOUT sort. La parade = uniquement bloquer, pas attaquer.
            if (combat.isGuarding(p.getUniqueId())) {
                mSetCancelled.invoke(event, true);
                return;
            }

            // Coût d'endurance spécifique au M1 kenjutsu.
            if (!SPELL.equals(mInternalName.invoke(spell))) return;

            if (stamina.tryConsume(p.getUniqueId(), COST)) {
                CombatChannel.sendStamina(plugin, p, stamina.get(p.getUniqueId()), stamina.max());
            } else {
                mSetCancelled.invoke(event, true);
                p.sendActionBar(Component.text("Endurance épuisée", NamedTextColor.RED));
                CombatChannel.sendStamina(plugin, p, stamina.get(p.getUniqueId()), stamina.max());
            }
        } catch (Throwable ignored) {
            // API MagicSpells différente / erreur réflexion → ne bloque jamais le jeu.
        }
    }
}
