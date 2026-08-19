package com.reborn.shinobicombat.net;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Canal serveur→client {@code reborn:combat} — alimente le HUD combat du
 * mod-hud (damage indicators, anneau de stamina). Octets bruts (le custom
 * payload MC porte déjà la taille), même convention que {@code reborn:run}
 * /{@code reborn:anim}.
 *
 * <p>Format : 1 octet de type, puis le corps.
 * <ul>
 *   <li>{@link #TYPE_HIT} : {@code int victimEntityId}, {@code float damage} —
 *       nombre flottant au-dessus de la cible + cumul de combo côté client.</li>
 *   <li>{@link #TYPE_STAMINA} : {@code float current}, {@code float max} —
 *       anneau de stamina autour du curseur.</li>
 * </ul>
 */
public final class CombatChannel {

    public static final String CHANNEL = "reborn:combat";

    public static final byte TYPE_HIT = 1;
    public static final byte TYPE_STAMINA = 2;

    private CombatChannel() {}

    /** Notifie {@code viewer} d'un coup porté à {@code victimEntityId} (dégâts). */
    public static void sendHit(Plugin plugin, Player viewer, int victimEntityId, double damage) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(TYPE_HIT);
            out.writeInt(victimEntityId);
            out.writeFloat((float) damage);
        } catch (IOException ignored) {
            return; // écriture en mémoire — ne peut échouer en pratique
        }
        viewer.sendPluginMessage(plugin, CHANNEL, b.toByteArray());
    }

    /** Pousse la stamina courante du joueur (anneau curseur). */
    public static void sendStamina(Plugin plugin, Player viewer, double current, double max) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(TYPE_STAMINA);
            out.writeFloat((float) current);
            out.writeFloat((float) max);
        } catch (IOException ignored) {
            return;
        }
        viewer.sendPluginMessage(plugin, CHANNEL, b.toByteArray());
    }
}
