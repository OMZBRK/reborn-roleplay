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
    /** Parade timée (Sekiro) : corps {@code byte role}. Flash + son côté client. */
    public static final byte TYPE_PARRY = 5;
    /** Regard vers le sol (aucun corps) — en vue épaule le client incline sa caméra
     *  orbite plein bas (la 1ère personne/vanilla suit déjà le pitch de l'entité).
     *  Émis par la commande {@code /lookdown}. */
    public static final byte TYPE_LOOK_DOWN = 6;
    /** Saut chargé (lot 5, étape A) : réponse à la relâche ou refus. Corps : {@code byte statut} ({@link #SAUT_GO} ou
     *  {@link #SAUT_NON}). GO : {@code float vx, vy, vz, float charge, int rechargeMs, byte drapeaux
     *  ({@link #SAUT_BRIDE_VUE}, {@link #SAUT_BRIDE_CHAKRA}), float porteeBorne}. NON : {@code byte raison
     *  (SAUT_R_*), int resteMs} (recharge restante, sinon 0). Un mod plus ancien l'ignore. */
    public static final byte TYPE_SAUT = 7;
    /** Réglages du saut chargé, poussés à la connexion : le mod calcule le même cercle que le serveur. Corps :
     *  {@code byte version (1), int chargeMs, float seuil, vitesseMin, vitessePente, elanMin, elanPente, vyMax,
     *  trainee, coutMinPct, coutMaxPct, int rechargeMinMs, int rechargeMaxMs, float porteeMax, viseeMaxDeg}. */
    public static final byte TYPE_SAUT_REGLES = 8;

    public static final byte SAUT_NON = 0, SAUT_GO = 1;
    public static final byte SAUT_BRIDE_VUE = 1, SAUT_BRIDE_CHAKRA = 2;
    /** Raisons d'un refus du saut chargé (le mod les traduit). */
    public static final byte SAUT_R_ETAT = 1, SAUT_R_RECHARGE = 2, SAUT_R_CHAKRA = 3, SAUT_R_SOL = 4,
            SAUT_R_SEUIL = 5, SAUT_R_EAU = 6, SAUT_R_SURCHARGE = 7, SAUT_R_KO = 8, SAUT_R_PERSO = 9,
            SAUT_R_SANS_DEBUT = 10, SAUT_R_GARDE = 11;

    /** Rôle porté par {@link #TYPE_PARRY} : la victime a paré avec succès (deflect). */
    public static final byte PARRY_ROLE_SUCCESS = 0;
    /** Rôle porté par {@link #TYPE_PARRY} : l'attaquant s'est fait parer (stun/ouverture). */
    public static final byte PARRY_ROLE_BROKEN = 1;

    /** Anim ids portés par {@link #TYPE_ANIM}. */
    public static final byte ANIM_STRIKE_1 = 1;
    public static final byte ANIM_STRIKE_2 = 2;
    public static final byte ANIM_STRIKE_3 = 3;
    public static final byte ANIM_PYRAMID = 4;
    public static final byte ANIM_BLOCK_ON = 5;
    public static final byte ANIM_BLOCK_OFF = 6;
    /** Parade réussie (18/10) : coup de garde du défenseur ({@code block_parry.json} côté mod). Un client plus ancien
     *  l'ignore (anim inconnue = no-op). */
    public static final byte ANIM_PARRY = 7;

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
    /** Saut chargé, début de charge (aucun corps) : le serveur note SON heure et vérifie les gardes. */
    public static final byte KIND_SAUT_DEBUT = 8;
    /** Saut chargé, relâche : {@code float lacet}, {@code float tangage} de la visée (caméra). Le serveur calcule la
     *  charge avec ses deux heures et répond {@link #TYPE_SAUT}. */
    public static final byte KIND_SAUT_RELACHE = 9;
    /** Saut chargé, annulation (quitté le sol, garde, écran ouvert ; aucun corps). */
    public static final byte KIND_SAUT_ANNULE = 10;
    /** Saut chargé : le mod n'a pas joué un {@code GO} arrivé trop tard (aucun corps). Bond pas encore décollé : remboursé. */
    public static final byte KIND_SAUT_GO_IGNORE = 11;

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
     * Notifie {@code viewer} d'un évènement de parade timée ({@code role} :
     * {@link #PARRY_ROLE_SUCCESS} = a paré / {@link #PARRY_ROLE_BROKEN} = s'est fait parer).
     * Le client déclenche le flash + son de deflect.
     */
    public static void sendParry(Plugin plugin, Player viewer, byte role) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(TYPE_PARRY);
            out.writeByte(role);
        } catch (IOException ignored) {
            return;
        }
        viewer.sendPluginMessage(plugin, CHANNEL, b.toByteArray());
    }

    /**
     * Ordonne à {@code target} d'incliner sa caméra épaule plein bas (regard sol).
     * Corps vide — un seul octet {@link #TYPE_LOOK_DOWN}. Sans effet en 1ère personne
     * / vue vanilla (elles suivent déjà le pitch de l'entité posé par le téléport).
     * Utilisé par la commande {@code /lookdown}.
     */
    public static void sendLookDown(Plugin plugin, Player target) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(TYPE_LOOK_DOWN);
        } catch (IOException ignored) {
            return;
        }
        target.sendPluginMessage(plugin, CHANNEL, b.toByteArray());
    }

    /** Saut chargé accepté : vitesse de départ, charge retenue, recharge, drapeaux et portée bornée. */
    public static void sendSautGo(Plugin plugin, Player target, double[] v, double charge, long rechargeMs,
                                  byte drapeaux, double porteeBorne) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(TYPE_SAUT);
            out.writeByte(SAUT_GO);
            out.writeFloat((float) v[0]);
            out.writeFloat((float) v[1]);
            out.writeFloat((float) v[2]);
            out.writeFloat((float) charge);
            out.writeInt((int) Math.min(Integer.MAX_VALUE, rechargeMs));
            out.writeByte(drapeaux);
            out.writeFloat((float) porteeBorne);
        } catch (IOException ignored) {
            return;
        }
        target.sendPluginMessage(plugin, CHANNEL, b.toByteArray());
    }

    /** Saut chargé refusé : raison ({@code SAUT_R_*}) et recharge restante (0 sinon). */
    public static void sendSautNon(Plugin plugin, Player target, byte raison, long resteMs) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(TYPE_SAUT);
            out.writeByte(SAUT_NON);
            out.writeByte(raison);
            out.writeInt((int) Math.max(0, Math.min(Integer.MAX_VALUE, resteMs)));
        } catch (IOException ignored) {
            return;
        }
        target.sendPluginMessage(plugin, CHANNEL, b.toByteArray());
    }

    /** Réglages du saut chargé (voir {@link #TYPE_SAUT_REGLES}). */
    public static void sendSautRegles(Plugin plugin, Player target, com.reborn.shinobicombat.saut.SautChargeLoi.Regles r) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(TYPE_SAUT_REGLES);
            out.writeByte(1);
            out.writeInt((int) r.chargeMs());
            out.writeFloat((float) r.seuil());
            out.writeFloat((float) r.vitesseMin());
            out.writeFloat((float) r.vitessePente());
            out.writeFloat((float) r.elanMin());
            out.writeFloat((float) r.elanPente());
            out.writeFloat((float) r.vyMax());
            out.writeFloat((float) r.trainee());
            out.writeFloat((float) r.coutMinPct());
            out.writeFloat((float) r.coutMaxPct());
            out.writeInt((int) r.rechargeMinMs());
            out.writeInt((int) r.rechargeMaxMs());
            out.writeFloat((float) r.porteeMax());
            out.writeFloat((float) r.viseeMaxDeg());
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
