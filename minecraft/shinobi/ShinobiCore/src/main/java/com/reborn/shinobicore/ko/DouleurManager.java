package com.reborn.shinobicore.ko;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * État « Douleur » (Pain RP du Game Plan RedLine) : <b>sous {@code ko.douleur.seuil-pct} des PV (5 %)</b>, avant le
 * KO, le personnage est à bout de forces.
 *
 * <ul>
 *   <li>Démarche ralentie ({@code ko.douleur.vitesse}, ×0,6), <b>ni course</b>, ni course de chakra, dash, double
 *       saut ou saut de chakra, <b>aucune technique</b> (via {@code KoService#isImpaired}, comme l'ATA), <b>aucun
 *       coup</b> (ni mains nues, ni arme, ni projectile).</li>
 *   <li>Les autres peuvent le <b>porter sans son accord</b> (il peut tenter de se débattre avec Espace,
 *       {@link com.reborn.shinobicore.ko.action.PorterManager}) ou l'<b>exécuter</b> (menu d'interaction R →
 *       « Exécuter » : canalisation, puis K.O. direct, {@link com.reborn.shinobicore.ko.action.CorpsService}).</li>
 *   <li>On en sort en remontant au-dessus du seuil (soin, bandage, repos) ; à 0 PV, on tombe à terre (agonie).</li>
 *   <li>État <b>dérivé des PV</b> (persistants) : rien à sauvegarder, recalculé à chaque quart de seconde et à la
 *       reconnexion. Jamais sur un terrain d'entraînement, en duel, en mode construction, KO ou mort.</li>
 * </ul>
 */
public final class DouleurManager implements Listener {

    private final ShinobiCore plugin;
    private final NamespacedKey slowKey;
    private final Set<UUID> inPain = ConcurrentHashMap.newKeySet();
    /** Joueurs dont on a coupé la faim (pas de sprint côté client) : à rendre à la sortie. */
    private final Set<UUID> starved = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastDeny = new ConcurrentHashMap<>();
    private BukkitTask task;

    private boolean enabled = true;
    private double threshold = 0.05;
    private double speed = 0.6;

    public DouleurManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.slowKey = new NamespacedKey(plugin, "ko_douleur");
    }

    public void start() {
        reloadConfig();
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        for (UUID id : Set.copyOf(inPain)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) clearEffects(p);
        }
        inPain.clear();
    }

    public void reloadConfig() {
        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("ko.douleur.actif", true);
        threshold = Math.max(0.005, Math.min(0.5, cfg.getDouble("ko.douleur.seuil-pct", 0.05)));
        speed = Math.max(0.1, Math.min(1.0, cfg.getDouble("ko.douleur.vitesse", 0.6)));
    }

    /* ================================================================ requêtes */

    public boolean isInPain(UUID playerId) { return playerId != null && inPain.contains(playerId); }

    /** Multiplicateur de démarche en douleur ({@code ko.douleur.vitesse}, ×0,6), lu par la matrice des états. */
    public double vitesse() { return speed; }

    /** Fraction des PV max sous laquelle on entre en douleur. */
    public double threshold() { return threshold; }

    /** PV (absolus) du seuil pour ce joueur. */
    public double thresholdHp(Player p) { return p.getMaxHealth() * threshold; }

    /* ================================================================ cycle */

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) check(p);
    }

    /** Recalcule l'état d'un joueur (appelé au tick et juste après un coup ou un soin). */
    public void check(Player p) {
        if (p == null || !p.isOnline()) return;
        UUID id = p.getUniqueId();
        boolean eligible = eligible(p);
        boolean low = eligible && p.getHealth() <= thresholdHp(p) + 1e-6;
        if (low && !inPain.contains(id)) enter(p);
        else if (!low && inPain.contains(id)) exit(p, eligible);
        else if (low) apply(p);
    }

    private boolean eligible(Player p) {
        if (!enabled) return false;
        UUID id = p.getUniqueId();
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return false;
        ShinobiCharacter c = plugin.characters().getActive(id);
        if (c == null) return false;
        if (plugin.ko().isKo(id)) return false;
        if (plugin.deathScene() != null && plugin.deathScene().isDead(id)) return false;
        if (plugin.isStaffBuilding(id)) return false;
        if (plugin.trainingZones() != null && plugin.trainingZones().isTraining(p)) return false;
        return plugin.duels() == null || !plugin.duels().inDuel(id);
    }

    private void enter(Player p) {
        inPain.add(p.getUniqueId());
        apply(p);
        // à bout de forces : on lâche ce qu'on portait ou tenait, une exécution en cours s'arrête
        if (plugin.porter() != null && plugin.porter().isCarrying(p.getUniqueId())) plugin.porter().release(p.getUniqueId());
        if (plugin.corps() != null) plugin.corps().onActorDisabled(p, "Tu n'as plus la force.");
        boolean modded = plugin.koHud() != null && plugin.koHud().event(p, KoHudSync.EV_DOULEUR);
        if (!modded) {
            p.showTitle(Title.title(Component.empty(),
                    Component.text("Douleur — à bout de forces", NamedTextColor.RED),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(700))));
            p.sendMessage(Component.text("Douleur : ni course ni technique, plus aucun coup. Un soin au-dessus de "
                    + Math.round(threshold * 100) + " % des PV t'en sort.", NamedTextColor.GRAY));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.7f, 0.7f);
        KoJournal.log(plugin, p, "Douleur", String.format("PV %.0f/%.0f (seuil %d %%)",
                p.getHealth(), p.getMaxHealth(), Math.round(threshold * 100)));
    }

    /** {@code eligible} faux = sortie silencieuse (KO, terrain, duel…) ; vrai = soigné au-dessus du seuil. */
    private void exit(Player p, boolean eligible) {
        inPain.remove(p.getUniqueId());
        clearEffects(p);
        if (eligible) {
            KoJournal.log(plugin, p, "Fin de douleur", String.format("PV %.0f/%.0f", p.getHealth(), p.getMaxHealth()));
            if (plugin.koHud() == null || !plugin.koHud().modded(p)) {
                p.sendActionBar(Component.text("Tu reprends un peu de forces.", NamedTextColor.GRAY));
            }
        }
    }

    /** Sortie immédiate sans trace (entrée en KO : le KO journalise lui-même). */
    public void clear(Player p) {
        if (p == null || !inPain.remove(p.getUniqueId())) return;
        clearEffects(p);
    }

    private void apply(Player p) {
        // Étape 5 d'ETATS_JOUEUR : l'applicateur des états pose la démarche (×0,6) et tient la faim (une seule règle).
        if (com.reborn.shinobicore.etat.Applicateur.actif()) {
            AttributeInstance ms0 = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (ms0 != null) ms0.removeModifier(slowKey);
            if (p.isSprinting()) p.setSprinting(false);
            return;
        }
        AttributeInstance ms = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (ms != null && ms.getModifier(slowKey) == null) {
            ms.addTransientModifier(new AttributeModifier(slowKey, speed - 1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
        // pas de sprint côté client : faim maintenue à 6 (comme l'ATA)
        starved.add(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.SATURATION);
        if (p.getFoodLevel() > 6) p.setFoodLevel(6);
        p.setSaturation(0f);
        if (p.isSprinting()) p.setSprinting(false);
    }

    private void clearEffects(Player p) {
        AttributeInstance ms = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (ms != null) ms.removeModifier(slowKey);
        if (starved.remove(p.getUniqueId()) && !com.reborn.shinobicore.etat.Applicateur.actif()
                && (plugin.ata() == null || !plugin.ata().isImpaired(p.getUniqueId()))) {
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }
    }

    /* ================================================================ événements */

    @EventHandler(ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent ev) {
        if (ev.isSprinting() && isInPain(ev.getPlayer().getUniqueId())) {
            ev.setCancelled(true);
            ev.getPlayer().setSprinting(false);
        }
    }

    /** En douleur, on ne frappe plus (mains nues, arme, projectile). Tôt, pour que les plugins de combat le voient. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent ev) {
        Player attacker = attackerOf(ev.getDamager());
        if (attacker == null || !isInPain(attacker.getUniqueId())) return;
        ev.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastDeny.get(attacker.getUniqueId());
        if (last == null || now - last > 1500) {
            lastDeny.put(attacker.getUniqueId(), now);
            attacker.sendActionBar(Component.text("À bout de forces : tu ne peux plus frapper.", NamedTextColor.RED));
        }
    }

    /** Entrée immédiate après un coup (sans attendre le prochain quart de seconde). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof Player p) Bukkit.getScheduler().runTask(plugin, () -> check(p));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent ev) {
        if (ev.getEntity() instanceof Player p) Bukkit.getScheduler().runTask(plugin, () -> check(p));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> check(p), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        if (inPain.remove(p.getUniqueId())) {
            AttributeInstance ms = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (ms != null) ms.removeModifier(slowKey);
        }
        starved.remove(p.getUniqueId());
        lastDeny.remove(p.getUniqueId());
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
