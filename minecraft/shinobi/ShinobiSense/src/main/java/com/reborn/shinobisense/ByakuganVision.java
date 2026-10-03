package com.reborn.shinobisense;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vision du Byakugan : le porteur voit le réseau de chakra (et les tenketsu) des joueurs et entités autour de lui,
 * <b>à travers les murs</b>, et lui seul.
 *
 * <p>Pour chaque cible à portée, un {@link ItemDisplay} portant le modèle {@code reborn:byakugan_reseau}
 * (silhouette de chakra translucide) suit la cible. Il est invisible par défaut et montré uniquement au porteur
 * ({@link Player#showEntity}), et il est en surbrillance ({@code setGlowing} + couleur forcée), ce qui dessine son
 * contour à travers les blocs. Aucune librairie de paquets n'est nécessaire.
 *
 * <p>Tant que la vision est active, un léger voile de veines reste affiché sur les bords de l'écran : c'est une barre de
 * boss rose dont le nom est un « a » de couleur {@code #FE0004}, que le shader de texte du pack étire en plein écran
 * (les sprites de la barre rose sont transparents dans le pack). Elle tient sans être renvoyée et ne prend pas la
 * place des titres.
 *
 * <p>La portée, la couleur et le son sont réglables (l'équilibrage décide de la portée).
 */
public final class ByakuganVision {

    private final Plugin plugin;
    private final NamespacedKey marker;
    private final Map<UUID, Map<UUID, ItemDisplay>> doubles = new HashMap<>();   // porteur -> cible -> double de chakra
    private final Map<UUID, Double> ranges = new HashMap<>();
    private final Map<UUID, BossBar> voiles = new HashMap<>();
    /** Nom de la barre de boss : un « a » de la couleur marqueur 4 (voile Byakugan), sans ombre. */
    private static final Component VOILE = Component.text("a").color(TextColor.color(0xFE0004)).shadowColor(ShadowColor.none());
    private BukkitTask task;

    /** Modèle d'objet (assets/reborn/items/byakugan_reseau.json dans le pack). */
    private NamespacedKey model = new NamespacedKey("reborn", "byakugan_reseau");
    private Color glow = Color.fromRGB(0x6EC8FF);
    private int period = 2;                      // mise à jour toutes les 2 ticks

    public ByakuganVision(Plugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "byakugan_double");
    }

    public ByakuganVision model(NamespacedKey key) { this.model = key; return this; }

    public ByakuganVision glowColor(Color color) { this.glow = color; return this; }

    public void start() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
        // nettoyage d'éventuels doubles restés après un crash (ils ne sont pas persistants, mais par sécurité)
        for (var world : Bukkit.getWorlds()) {
            for (ItemDisplay d : world.getEntitiesByClass(ItemDisplay.class)) {
                if (d.getPersistentDataContainer().has(marker)) d.remove();
            }
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (UUID viewer : Set.copyOf(doubles.keySet())) clear(viewer);
    }

    public boolean isActive(Player viewer) { return doubles.containsKey(viewer.getUniqueId()); }

    /** Active la vision. {@code range} = portée en blocs (fixée par l'équilibrage). */
    public void enable(Player viewer, double range) {
        doubles.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
        ranges.put(viewer.getUniqueId(), range);
        BossBar voile = voiles.computeIfAbsent(viewer.getUniqueId(),
                k -> BossBar.bossBar(VOILE, 0f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS));
        viewer.showBossBar(voile);
        playActivation(viewer);
        tick();
    }

    public void disable(Player viewer) {
        clear(viewer.getUniqueId());
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.6f, 1.6f);
    }

    /** Bruit d'activation (pas d'yeux à l'écran). Remplaçable par un son du pack, ex. « reborn:byakugan.activation ». */
    private void playActivation(Player p) {
        Location l = p.getLocation();
        p.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.9f);
        p.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.6f);
        p.playSound(l, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.PLAYERS, 0.5f, 1.4f);
    }

    private void clear(UUID viewer) {
        Map<UUID, ItemDisplay> m = doubles.remove(viewer);
        ranges.remove(viewer);
        BossBar voile = voiles.remove(viewer);
        Player p = Bukkit.getPlayer(viewer);
        if (voile != null && p != null) p.hideBossBar(voile);
        if (m != null) m.values().forEach(Entity::remove);
    }

    private void tick() {
        for (UUID viewerId : Set.copyOf(doubles.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) { clear(viewerId); continue; }
            Map<UUID, ItemDisplay> mine = doubles.get(viewerId);
            double range = ranges.getOrDefault(viewerId, 20.0);

            Set<UUID> seen = new HashSet<>();
            for (Entity e : viewer.getNearbyEntities(range, range, range)) {
                if (!(e instanceof LivingEntity target) || e instanceof ArmorStand || target.isDead()) continue;
                if (e.getLocation().distanceSquared(viewer.getLocation()) > range * range) continue;
                seen.add(e.getUniqueId());
                ItemDisplay d = mine.get(e.getUniqueId());
                if (d == null || !d.isValid()) {
                    d = spawnDouble(viewer, target);
                    mine.put(e.getUniqueId(), d);
                } else {
                    follow(d, target);
                }
            }
            // cibles sorties de la portée, mortes ou déconnectées
            for (Iterator<Map.Entry<UUID, ItemDisplay>> it = mine.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, ItemDisplay> en = it.next();
                if (!seen.contains(en.getKey())) { en.getValue().remove(); it.remove(); }
            }
        }
    }

    private ItemDisplay spawnDouble(Player viewer, LivingEntity target) {
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(model);
        item.setItemMeta(meta);
        Location at = target.getLocation();
        at.setPitch(0);
        ItemDisplay d = target.getWorld().spawn(at, ItemDisplay.class, disp -> {
            disp.setVisibleByDefault(false);
            disp.setPersistent(false);
            disp.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
            disp.setItemStack(item);
            disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            disp.setBrightness(new Display.Brightness(15, 15));
            disp.setGlowing(true);
            disp.setGlowColorOverride(glow);
            disp.setViewRange(4f);
            disp.setTeleportDuration(period);
            disp.setTransformation(scaleFor(target));
        });
        viewer.showEntity(plugin, d);
        return d;
    }

    private void follow(ItemDisplay d, LivingEntity target) {
        Location at = target.getLocation();
        at.setPitch(0);
        d.teleport(at);
        Transformation t = scaleFor(target);
        if (!t.getScale().equals(d.getTransformation().getScale())) d.setTransformation(t);
    }

    /** Le modèle fait 1,8 bloc de haut (taille d'un joueur) : on l'adapte à la hauteur de la cible. */
    private static Transformation scaleFor(LivingEntity target) {
        float s = (float) Math.max(0.3, target.getHeight() / 1.8);
        float w = (float) Math.max(0.3, target.getWidth() / 0.6);
        float sx = Math.min(w, s * 2.5f);
        // modèle centré en (8, ?, 8) avec pieds à y = 0 ; le rendu « NONE » décale de -0,5 bloc : on compense
        return new Transformation(new Vector3f(0f, 0.5f * s, 0f), new AxisAngle4f(), new Vector3f(sx, s, sx), new AxisAngle4f());
    }
}
