package com.reborn.shinobicombat.net;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;

/**
 * Canaux plugin-message combat.
 *
 * <p><b>{@link #CHANNEL} — {@code reborn:combat} (serveur→client)</b> : alimente
 * le HUD combat du mod-hud (damage indicators, anneau de stamina, anims). Octets
 * bruts (le custom payload MC porte déjà la taille), même convention que
 * {@code reborn:run}/{@code reborn:anim}.
 *
 * <p>Format S2C : 1 octet de type, puis le corps.
 * <ul>
 *   <li>{@link #TYPE_HIT} : {@code int victimEntityId}, {@code float damage} —
 *       nombre flottant au-dessus de la cible + cumul de combo côté client.</li>
 *   <li>{@link #TYPE_STAMINA} : {@code float current}, {@code float max} —
 *       anneau de stamina autour du curseur.</li>
 *   <li>{@link #TYPE_ANIM} : {@code int attackerEntityId}, {@code byte animId} —
 *       joue une anim sur l'acteur chez les clients proches (1=strike1, 2=strike2,
 *       3=strike3, 4=pyramid/finisher, 5=block-on, 6=block-off).</li>
 *   <li>{@link #TYPE_COOLDOWN_RESET} : aucun corps — le client vide ses cooldowns
 *       (dash, saut chakra) et leurs gates HUD. Émis par {@code /resetcd} (staff).</li>
 * </ul>
 *
 * <p><b>{@link #CHANNEL_IN} — {@code reborn:combatin} (client→serveur)</b> :
 * intentions du client. Format : {@code byte kind}, puis un corps optionnel
 * selon le type.
 * {@link #KIND_BLOCK_ON} = début de garde (M2 maintenu, aucun corps),
 * {@link #KIND_BLOCK_OFF} = fin de garde (aucun corps),
 * {@link #KIND_DASH} = dash directionnel (corps : {@code float dx}, {@code float dz}
 * — direction horizontale monde normalisée),
 * {@link #KIND_CHAKRA_JUMP} = saut chakra (corps : {@code float vx}, {@code float vy},
 * {@code float vz} — vélocité monde calculée côté client, clampée serveur),
 * {@link #KIND_KERIOX_DASH} = keriox dash orienté pitch (corps : {@code float vx},
 * {@code float vy}, {@code float vz} — vélocité monde, clampée serveur),
 * {@link #KIND_DODGE} = esquive (corps : {@code float vx}, {@code float vy},
 * {@code float vz} — vélocité monde, clampée serveur).
 */
public final class CombatChannel {

    public static final String CHANNEL = "reborn:combat";
    public static final String CHANNEL_IN = "reborn:combatin";

    public static final byte TYPE_HIT = 1;
    public static final byte TYPE_STAMINA = 2;
    public static final byte TYPE_ANIM = 3;
    /** Reset des cooldowns côté client (aucun corps) — dev/staff via {@code /resetcd}. */
    public static final byte TYPE_COOLDOWN_RESET = 4;

    /** Anim ids portés par {@link #TYPE_ANIM}. */
    public static final byte ANIM_STRIKE_1 = 1;
    public static final byte ANIM_STRIKE_2 = 2;
    public static final byte ANIM_STRIKE_3 = 3;
    public static final byte ANIM_PYRAMID = 4;
    public static final byte ANIM_BLOCK_ON = 5;
    public static final byte ANIM_BLOCK_OFF = 6;

    /** Intentions C2S sur {@link #CHANNEL_IN}. */
    public static final byte KIND_BLOCK_ON = 2;
    public static final byte KIND_BLOCK_OFF = 3;
    /** Dash directionnel — suivi de {@code float dx}, {@code float dz}. */
    public static final byte KIND_DASH = 4;
    /** Saut chakra — suivi de {@code float vx}, {@code float vy}, {@code float vz}. */
    public static final byte KIND_CHAKRA_JUMP = 5;
    /** Keriox dash (avancée orientée pitch) — suivi de {@code float vx}, {@code float vy}, {@code float vz}. */
    public static final byte KIND_KERIOX_DASH = 6;
    /** Esquive latérale/arrière — suivi de {@code float vx}, {@code float vy}, {@code float vz}. */
    public static final byte KIND_DODGE = 7;

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

    /**
     * Ordonne à {@code target} de vider ses cooldowns d'aptitudes (dash, saut chakra)
     * et les gates HUD associées. Corps vide — un seul octet {@link #TYPE_COOLDOWN_RESET}.
     * Utilisé par la commande staff {@code /resetcd} (test dev).
     */
    public static void sendCooldownReset(Plugin plugin, Player target) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(TYPE_COOLDOWN_RESET);
        } catch (IOException ignored) {
            return;
        }
        target.sendPluginMessage(plugin, CHANNEL, b.toByteArray());
    }

    /**
     * Joue une anim de coup de {@code attackerEntityId} chez chaque destinataire
     * ({@code animId} : 1=strike1, 2=strike2, 3=strike3, 4=heavy/M2).
     */
    public static void sendAnim(Plugin plugin, Collection<Player> recipients, int attackerEntityId, byte animId) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(TYPE_ANIM);
            out.writeInt(attackerEntityId);
            out.writeByte(animId);
        } catch (IOException ignored) {
            return;
        }
        byte[] payload = b.toByteArray();
        for (Player r : recipients) {
            r.sendPluginMessage(plugin, CHANNEL, payload);
        }
    }
}
