package com.reborn.shinobicombat.combat;

import com.reborn.shinobicombat.net.CombatChannel;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.KoService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cœur du taïjutsu — <b>incrément 2</b> : combos M1 (4 coups + finition
 * « pyramid ») et garde M2 (blocage). Le serveur reste autoritaire ; les dégâts
 * passent par le pipeline ShinobiCore (armure, KO, sync HP) via
 * l'{@link EntityDamageByEntityEvent} vanilla.
 *
 * <p><b>M1</b> (chaque coup mêlée d'un joueur ayant un personnage actif) :
 * <ol>
 *   <li>Refuse si KO ; porte de stamina (anti-spam).</li>
 *   <li>Combo 0→1→2→3 dans une fenêtre de {@link #COMBO_WINDOW_MS} ; dégâts
 *       {@code 3/4/6/8}. Sur la finition (index 3, « pyramid ») : projection
 *       + hitstun.</li>
 *   <li>Broadcast anim (1/2/3/4) aux clients proches, damage indicator aux deux
 *       joueurs, stamina à l'attaquant.</li>
 * </ol>
 *
 * <p><b>M2 = garde</b> (kind=2 BLOCK ON / kind=3 BLOCK OFF sur
 * {@code reborn:combatin}) : tant qu'un joueur bloque, les coups reçus sont
 * atténués de {@link #BLOCK_REDUCTION} et drainent {@link #GUARD_DRAIN} de
 * stamina. Stamina à zéro → <b>guard break</b> (stun + fin de garde). Anims
 * 5 (block on) / 6 (block off) diffusées aux clients proches.
 */
public final class CombatListener implements Listener, PluginMessageListener {

    // --- M1 (combo) ---------------------------------------------------------
    /** Coût stamina d'un M1 — anti-spam. */
    private static final double M1_STAMINA_COST = 18.0;
    /** Dégâts par palier de combo (index 0→3 ; index 3 = finition « pyramid »). */
    private static final double[] M1_COMBO_DAMAGE = {3.0, 4.0, 6.0, 8.0};
    /** Longueur du combo (paliers). */
    private static final int COMBO_LENGTH = M1_COMBO_DAMAGE.length;
    /** Index du coup finisher (dernier palier). */
    private static final int FINISHER_INDEX = COMBO_LENGTH - 1;
    /** Fenêtre max entre deux coups pour enchaîner le combo (ms). */
    private static final long COMBO_WINDOW_MS = 1200L;
    /** Poussée horizontale de la finition M1. */
    private static final double FINISHER_KB_H = 0.85;
    /** Composante verticale de la poussée de finition M1. */
    private static final double FINISHER_KB_Y = 0.35;
    /** Durée du hitstun (SLOWNESS) de la finition M1, en ticks. */
    private static final int FINISHER_HITSTUN_TICKS = 8;

    // --- M2 (garde / blocage) ----------------------------------------------
    /** Fraction de dégâts absorbée par la garde (0.7 → la victime encaisse 30 %). */
    private static final double BLOCK_REDUCTION = 0.7;
    /** Stamina drainée à chaque coup bloqué. */
    private static final double GUARD_DRAIN = 25.0;
    /** Durée du stun de guard break, en ticks. */
    private static final int GUARD_BREAK_STUN_TICKS = 20;

    // --- sons de combat (joués côté serveur à la position de la victime) ----
    /** Son d'impact normal (M1 non-finisher, non bloqué). */
    private static final String SOUND_HIT = "reborn:combat.hit";
    /** Son d'impact « fort » — garde touchée ou finition du combo. */
    private static final String SOUND_HIT_STRONG = "reborn:combat.hit_strong";
    /** Pitch du finisher (index 3). */
    private static final float FINISHER_PITCH = 0.95f;

    // --- dash (déplacement directionnel) -----------------------------------
    /** Cooldown entre deux dashs, par joueur (ms). */
    private static final long DASH_COOLDOWN_MS = 1500L;
    /** Coût stamina d'un dash. */
    private static final double DASH_COST = 20.0;
    /** Vitesse horizontale du dash. */
    private static final double DASH_SPEED = 0.9;
    /** Composante verticale (petit décollage) du dash. */
    private static final double DASH_UP = 0.15;

    // --- saut chakra (leap vertical/directionnel) --------------------------
    /** Cooldown entre deux sauts chakra, par joueur (ms) — long, sans coût stamina. */
    private static final long CHAKRA_JUMP_COOLDOWN_MS = 35000L;
    /** Magnitude horizontale max (anti-cheat) — {@code sqrt(vx²+vz²)} clampé à ça. */
    private static final double CHAKRA_JUMP_MAX_HORIZONTAL = 1.6;
    /** Composante verticale min (anti-cheat). */
    private static final double CHAKRA_JUMP_MIN_VY = -0.2;
    /** Composante verticale max (anti-cheat). */
    private static final double CHAKRA_JUMP_MAX_VY = 1.2;

    // --- commun -------------------------------------------------------------
    /** Amplificateur SLOWNESS commun aux hitstun / guard break. */
    private static final int SLOWNESS_AMP = 6;
    /** Rayon de diffusion des anims aux clients proches (blocs). */
    private static final double ANIM_BROADCAST_RANGE = 32.0;

    private final Plugin plugin;
    private final CharacterService characters;
    private final KoService ko; // peut être null si le service n'est pas exposé
    private final StaminaManager stamina;

    // État de combo par attaquant.
    private final Map<UUID, Integer> comboIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHitMs = new ConcurrentHashMap<>();
    // Joueurs en garde (M2 maintenu).
    private final Set<UUID> blocking = ConcurrentHashMap.newKeySet();
    // Dernier dash par joueur (cooldown).
    private final Map<UUID, Long> lastDashMs = new ConcurrentHashMap<>();
    // Dernier saut chakra par joueur (cooldown).
    private final Map<UUID, Long> lastChakraJumpMs = new ConcurrentHashMap<>();

    public CombatListener(Plugin plugin, CharacterService characters, KoService ko, StaminaManager stamina) {
        this.plugin = plugin;
        this.characters = characters;
        this.ko = ko;
        this.stamina = stamina;
    }

    // ------------------------------------------------------------------ M1 --

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
            CombatChannel.sendStamina(plugin, attacker, stamina.get(attacker.getUniqueId()), stamina.max());
            return;
        }

        // Avance l'état de combo.
        int index = advanceCombo(attacker.getUniqueId());
        double damage = M1_COMBO_DAMAGE[index];

        // Dégâts de base Reborn — le reste du pipeline ShinobiCore s'applique.
        e.setDamage(damage);

        // Finition (« pyramid ») : projection + hitstun.
        if (index == FINISHER_INDEX) {
            applyKnockback(attacker, victim, FINISHER_KB_H, FINISHER_KB_Y);
            applyHitstun(victim, FINISHER_HITSTUN_TICKS);
        }

        // La victime bloque-t-elle ce coup ? (capturé avant maybeBlock, qui peut
        // rompre la garde sur guard break).
        boolean guarded = victim instanceof Player pvGuard
                && (ko == null || !ko.isKo(pvGuard.getUniqueId()))
                && blocking.contains(pvGuard.getUniqueId());

        // Garde de la victime : atténuation + drain, éventuel guard break.
        double dealt = maybeBlock(victim, damage, e);

        // Son d'impact — joué à la position de la victime pour les joueurs proches.
        playHitSound(victim, guarded, index == FINISHER_INDEX);

        // Anim de coup (1/2/3/4) aux clients proches, attaquant inclus.
        CombatChannel.sendAnim(plugin, nearbyPlayers(attacker),
                attacker.getEntityId(), (byte) (index + 1));

        // Damage indicator : attaquant + victime (si joueur) — reflète les dégâts réels.
        CombatChannel.sendHit(plugin, attacker, victim.getEntityId(), dealt);
        if (victim instanceof Player pv) {
            CombatChannel.sendHit(plugin, pv, victim.getEntityId(), dealt);
        }
        // Stamina de l'attaquant.
        CombatChannel.sendStamina(plugin, attacker, stamina.get(attacker.getUniqueId()), stamina.max());
    }

    /**
     * Si {@code victim} est un joueur en garde : atténue les dégâts de l'event,
     * draine sa stamina et déclenche un guard break si elle tombe à 0. Retourne
     * les dégâts effectivement encaissés (pour le damage indicator).
     */
    private double maybeBlock(LivingEntity victim, double incoming, EntityDamageByEntityEvent e) {
        if (!(victim instanceof Player pv)) return incoming;
        UUID vid = pv.getUniqueId();
        // Un joueur KO ne garde pas.
        if (ko != null && ko.isKo(vid)) {
            stopBlocking(pv, false);
            return incoming;
        }
        if (!blocking.contains(vid)) return incoming;

        double reduced = incoming * (1.0 - BLOCK_REDUCTION);
        e.setDamage(reduced);

        double remaining = stamina.drain(vid, GUARD_DRAIN);
        CombatChannel.sendStamina(plugin, pv, remaining, stamina.max());

        if (remaining <= 0.0) {
            // Guard break : fin de garde forcée + stun.
            stopBlocking(pv, true);
            applyHitstun(pv, GUARD_BREAK_STUN_TICKS);
        }
        return reduced;
    }

    /** Avance le compteur de combo (fenêtre {@link #COMBO_WINDOW_MS}) et renvoie l'index courant. */
    private int advanceCombo(UUID id) {
        long now = System.currentTimeMillis();
        Long last = lastHitMs.get(id);
        int index;
        if (last != null && now - last <= COMBO_WINDOW_MS) {
            index = (comboIndex.getOrDefault(id, 0) + 1) % COMBO_LENGTH;
        } else {
            index = 0;
        }
        comboIndex.put(id, index);
        lastHitMs.put(id, now);
        return index;
    }

    // ------------------------------------------------------------- M2 garde --

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CombatChannel.CHANNEL_IN.equals(channel)) return;
        byte kind;
        float dx = 0f;
        float dz = 0f;
        float vx = 0f;
        float vy = 0f;
        float vz = 0f;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            kind = in.readByte();
            if (kind == CombatChannel.KIND_DASH) {
                // Corps du dash : direction horizontale monde (déjà normalisée).
                dx = in.readFloat();
                dz = in.readFloat();
            } else if (kind == CombatChannel.KIND_CHAKRA_JUMP) {
                // Corps du saut chakra : vélocité monde (clampée serveur).
                vx = in.readFloat();
                vy = in.readFloat();
                vz = in.readFloat();
            }
        } catch (IOException ignored) {
            return; // paquet malformé — on ignore
        }
        if (kind == CombatChannel.KIND_BLOCK_ON) {
            startBlocking(player);
        } else if (kind == CombatChannel.KIND_BLOCK_OFF) {
            stopBlocking(player, false);
        } else if (kind == CombatChannel.KIND_DASH) {
            handleDash(player, dx, dz);
        } else if (kind == CombatChannel.KIND_CHAKRA_JUMP) {
            handleChakraJump(player, vx, vy, vz);
        }
    }

    /**
     * Dash directionnel : personnage actif requis, non KO, cooldown écoulé et
     * stamina suffisante. Consomme {@link #DASH_COST}, arme le cooldown et
     * applique la vélocité. Le mouvement est observable par les autres clients —
     * aucune anim à diffuser.
     */
    private void handleDash(Player player, float dx, float dz) {
        UUID id = player.getUniqueId();
        if (characters.getActive(id) == null) return;
        if (ko != null && ko.isKo(id)) return;

        long now = System.currentTimeMillis();
        Long last = lastDashMs.get(id);
        if (last != null && now - last < DASH_COOLDOWN_MS) return;

        if (!stamina.tryConsume(id, DASH_COST)) {
            player.sendActionBar(Component.text("Stamina épuisée", NamedTextColor.RED));
            CombatChannel.sendStamina(plugin, player, stamina.get(id), stamina.max());
            return;
        }
        lastDashMs.put(id, now);

        player.setVelocity(new Vector(dx * DASH_SPEED, DASH_UP, dz * DASH_SPEED));
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, org.bukkit.SoundCategory.PLAYERS, 0.7f, 1.5f);
        CombatChannel.sendStamina(plugin, player, stamina.get(id), stamina.max());
    }

    /**
     * Saut chakra : bond puissant dont la vélocité est calculée côté client. Gardes
     * comme le dash (personnage actif, non KO, cooldown), mais <b>sans coût
     * stamina</b> — la contrainte est le long cooldown ({@link #CHAKRA_JUMP_COOLDOWN_MS}).
     * Un <b>clamp anti-cheat</b> borne la magnitude horizontale à
     * {@link #CHAKRA_JUMP_MAX_HORIZONTAL} (mise à l'échelle proportionnelle) et la
     * composante verticale à {@code [CHAKRA_JUMP_MIN_VY, CHAKRA_JUMP_MAX_VY]}. Arme le
     * cooldown. Le mouvement est observable par les autres clients — aucune anim à diffuser.
     */
    private void handleChakraJump(Player player, float vx, float vy, float vz) {
        UUID id = player.getUniqueId();
        if (characters.getActive(id) == null) return;
        if (ko != null && ko.isKo(id)) return;

        long now = System.currentTimeMillis();
        Long last = lastChakraJumpMs.get(id);
        if (last != null && now - last < CHAKRA_JUMP_COOLDOWN_MS) return;

        lastChakraJumpMs.put(id, now);

        // Clamp anti-cheat : magnitude horizontale.
        double cvx = vx;
        double cvz = vz;
        double horizontal = Math.sqrt(cvx * cvx + cvz * cvz);
        if (horizontal > CHAKRA_JUMP_MAX_HORIZONTAL) {
            double scale = CHAKRA_JUMP_MAX_HORIZONTAL / horizontal;
            cvx *= scale;
            cvz *= scale;
        }
        // Clamp anti-cheat : composante verticale.
        double cvy = Math.max(CHAKRA_JUMP_MIN_VY, Math.min(CHAKRA_JUMP_MAX_VY, vy));

        player.setVelocity(new Vector(cvx, cvy, cvz));
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_FLAP, org.bukkit.SoundCategory.PLAYERS, 0.7f, 1.4f);
    }

    /** Début de garde : personnage actif requis, non KO. Diffuse l'anim block-on. */
    private void startBlocking(Player player) {
        UUID id = player.getUniqueId();
        if (characters.getActive(id) == null) return;
        if (ko != null && ko.isKo(id)) return;
        if (!blocking.add(id)) return; // déjà en garde
        CombatChannel.sendAnim(plugin, nearbyPlayers(player),
                player.getEntityId(), CombatChannel.ANIM_BLOCK_ON);
    }

    /** Fin de garde. Diffuse l'anim block-off si le joueur bloquait effectivement. */
    private void stopBlocking(Player player, boolean guardBroken) {
        if (!blocking.remove(player.getUniqueId()) && !guardBroken) return;
        CombatChannel.sendAnim(plugin, nearbyPlayers(player),
                player.getEntityId(), CombatChannel.ANIM_BLOCK_OFF);
    }

    // -------------------------------------------------------------- helpers --

    /** Pousse {@code victim} horizontalement à l'opposé de {@code attacker}, plus une composante Y. */
    private void applyKnockback(Player attacker, LivingEntity victim, double horizontal, double vertical) {
        Vector dir = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-6) {
            // Attaquant et victime superposés : pousse dans le regard de l'attaquant.
            dir = attacker.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1.0e-6) dir = new Vector(0, 0, 1);
        }
        dir.normalize().multiply(horizontal);
        dir.setY(vertical);
        victim.setVelocity(dir);
    }

    /**
     * Joue le son d'impact à la position de la victime (catégorie PLAYERS) pour
     * les joueurs proches. Garde touchée ou finisher → son « fort », sinon son
     * normal avec une légère variance de pitch.
     */
    private void playHitSound(LivingEntity victim, boolean guarded, boolean finisher) {
        String key;
        float pitch;
        if (guarded) {
            key = SOUND_HIT_STRONG;
            pitch = 1.0f;
        } else if (finisher) {
            key = SOUND_HIT_STRONG;
            pitch = FINISHER_PITCH;
        } else {
            key = SOUND_HIT;
            pitch = 1.0f + (float) (Math.random() * 0.08 - 0.04); // ±0.04
        }
        victim.getWorld().playSound(victim.getLocation(), key, SoundCategory.PLAYERS, 1.0f, pitch);
    }

    /** Applique un ralentissement fort, sans particules/ambiance. */
    private void applyHitstun(LivingEntity victim, int ticks) {
        victim.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, ticks, SLOWNESS_AMP, false, false, false));
    }

    /** Joueurs du monde de {@code center} dans {@link #ANIM_BROADCAST_RANGE} (center inclus). */
    private List<Player> nearbyPlayers(Player center) {
        double r2 = ANIM_BROADCAST_RANGE * ANIM_BROADCAST_RANGE;
        List<Player> out = new ArrayList<>();
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center.getLocation()) <= r2) out.add(p);
        }
        return out;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        forget(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        forget(e.getPlayer().getUniqueId());
    }

    /**
     * Réinitialise les cooldowns d'aptitudes ({@code dash}, saut chakra) d'un joueur
     * côté serveur — outil de test dev exposé par la commande staff {@code /resetcd}.
     * Ne touche ni la stamina ni l'état de combo/garde. Le client est notifié
     * séparément (S2C {@link CombatChannel#TYPE_COOLDOWN_RESET}).
     */
    public void resetCooldowns(UUID id) {
        lastDashMs.remove(id);
        lastChakraJumpMs.remove(id);
    }

    /** Nettoie l'état combat d'un joueur (mort, déconnexion). */
    private void forget(UUID id) {
        stamina.clear(id);
        comboIndex.remove(id);
        lastHitMs.remove(id);
        blocking.remove(id);
        lastDashMs.remove(id);
        lastChakraJumpMs.remove(id);
    }
}
