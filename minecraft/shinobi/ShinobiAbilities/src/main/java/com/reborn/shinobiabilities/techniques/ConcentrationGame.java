package com.reborn.shinobiabilities.techniques;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Concentration du chakra : première étape de tout entraînement qui demande du chakra (Ninjutsu, Kenjutsu — pas le
 * Taïjutsu). Mini-jeu « serveur » : rien ne bloque le joueur, il reste libre de ses mouvements.
 *
 * <ul>
 *   <li>Une jauge dans la barre d'action : une zone d'or et un trait de chakra qui dérive tout seul (fluctuations
 *       lentes, à-coups aux rangs élevés, zone qui se déplace en A/S).</li>
 *   <li>Le joueur corrige en tournant la tête (gauche / droite) : le trait suit la rotation, avec l'inertie de la
 *       dérive à anticiper.</li>
 *   <li>Le corps doit rester immobile : marcher vide la stabilité, courir la vide presque d'un coup, un saut ou un
 *       coup encaissé en arrache un gros morceau.</li>
 *   <li>Il faut cumuler N secondes dans la zone avant que la stabilité tombe à zéro.</li>
 * </ul>
 * Avec le mod Reborn : l'état part à chaque tick sur le canal {@code reborn:parchemin} ({@code {"t":"conc"}}) et le
 * mod dessine la plaque dans la DA. Sans le mod : barre d'action, deux barres de boss et titres vanilla. Dans tous
 * les cas : aura de particules visible des autres joueurs, sons.
 */
public final class ConcentrationGame implements Listener {

    /** Réglages d'un rang. {@code zone} = demi-largeur de la zone sur une jauge de -1 à 1. */
    public record Tier(double zone, double seconds, double drift, double tolerance, boolean gusts, boolean moving) {}

    private static final int CELLS = 25;
    private static final String CHANNEL = "reborn:parchemin";
    private static final double DT = 0.05;
    private static final double SENS = 2.0 / 70.0;          // 70° de rotation = toute la jauge
    private static final double GRACE = 1.5;                // secondes de calme au départ
    private static final TextColor GOLD = TextColor.color(0xD9A95E);
    private static final TextColor DIM = TextColor.color(0x3A302A);
    private static final TextColor CHAKRA = TextColor.color(0x6FD3FF);
    private static final TextColor LOST = TextColor.color(0xE0505A);

    private final JavaPlugin plugin;
    private final Map<UUID, Run> runs = new HashMap<>();
    private final Map<Character, Tier> tiers = new HashMap<>();
    private double masterBonus = 1.25;
    private final Random rnd = new Random();

    private final class Run {
        final Player p;
        final Tier t;
        final Consumer<Boolean> then;
        final boolean mod;
        final boolean helped;
        final char rank;
        final double[] phase = {rnd.nextDouble() * 6.3, rnd.nextDouble() * 6.3, rnd.nextDouble() * 6.3};
        double zone, pos, vel, center, held, stability = 1, time, nextGust;
        boolean airborne, jolt;
        float lastYaw;
        Location last;
        BossBar focus, calm;
        BukkitTask task;

        Run(Player p, Tier t, double zone, Consumer<Boolean> then, boolean helped, char rank) {
            this.p = p;
            this.mod = p.getListeningPluginChannels().contains(CHANNEL);
            this.helped = helped;
            this.rank = rank;
            this.t = t;
            this.zone = zone;
            this.then = then;
            this.lastYaw = p.getLocation().getYaw();
            this.last = p.getLocation();
            this.nextGust = GRACE + 2 + rnd.nextDouble() * 3;
        }
    }

    public ConcentrationGame(JavaPlugin plugin) {
        this.plugin = plugin;
        tiers.put('D', new Tier(0.40, 8, 0.95, 3.0, false, false));
        tiers.put('C', new Tier(0.30, 12, 1.10, 2.5, false, false));
        tiers.put('B', new Tier(0.22, 14, 1.20, 2.2, true, false));
        tiers.put('A', new Tier(0.16, 16, 1.25, 2.0, true, true));
        tiers.put('S', new Tier(0.12, 18, 1.35, 2.0, true, true));
    }

    /** Section {@code techniques.concentration} : un sous-bloc par rang (zone, secondes, derive, tolerance). */
    public void loadConfig(ConfigurationSection sec) {
        if (sec == null) return;
        masterBonus = sec.getDouble("bonus-maitre", masterBonus);
        for (char r : new char[]{'D', 'C', 'B', 'A', 'S'}) {
            ConfigurationSection s = sec.getConfigurationSection(String.valueOf(r));
            if (s == null) continue;
            Tier d = tiers.get(r);
            tiers.put(r, new Tier(s.getDouble("zone", d.zone()), s.getDouble("secondes", d.seconds()),
                    s.getDouble("derive", d.drift()), s.getDouble("tolerance", d.tolerance()),
                    s.getBoolean("rafales", d.gusts()), s.getBoolean("zone-mobile", d.moving())));
        }
    }

    public boolean isActive(Player p) { return runs.containsKey(p.getUniqueId()); }

    /**
     * Lance la concentration. {@code helped} : un maître est là — la zone s'élargit (l'astuce du maître, comme la
     * marque de Jiraiya dans la paume de Naruto). {@code then} reçoit {@code true} si réussi.
     */
    public void start(Player p, char rank, boolean helped, Consumer<Boolean> then) {
        if (isActive(p)) return;
        Tier t = tiers.getOrDefault(rank, tiers.get('D'));
        char rk = tiers.containsKey(rank) ? rank : 'D';
        Run r = new Run(p, t, t.zone() * (helped ? masterBonus : 1), then, helped, rk);
        r.focus = BossBar.bossBar(Component.text("Concentration"), 0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        r.calm = BossBar.bossBar(Component.text("Stabilité"), 1f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        if (!r.mod) {
            p.showBossBar(r.focus);
            p.showBossBar(r.calm);
        }
        if (!r.mod) p.showTitle(Title.title(
                Component.text("集中", GOLD, TextDecoration.BOLD),
                Component.text("Reste immobile, tourne la tête pour garder ton chakra dans l'or", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(400))));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        if (helped) p.sendMessage(Component.text("Ton maître t'indique où poser ton chakra : la zone est plus large.", GOLD));
        runs.put(p.getUniqueId(), r);
        r.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(r), 1L, 1L);
    }

    /* ------------------------------------------------------------------ boucle */

    private void tick(Run r) {
        Player p = r.p;
        if (!p.isOnline() || runs.get(p.getUniqueId()) != r) { stop(r); return; }
        r.time += DT;
        boolean calmPhase = r.time < GRACE;

        // correction du joueur : rotation de la tête depuis le tick précédent
        Location now = p.getLocation();
        float yaw = now.getYaw();
        double dyaw = wrap(yaw - r.lastYaw);
        r.lastYaw = yaw;
        double control = Math.max(-0.35, Math.min(0.35, dyaw * SENS));

        // dérive : somme de sinus lents (fluctuations du chakra), plus forte avec le rang
        double s = r.time;
        double drift = r.t.drift() * (0.6 * Math.sin(s * 0.9 + r.phase[0]) + 0.3 * Math.sin(s * 2.3 + r.phase[1])
                + 0.5 * Math.sin(s * 0.37 + r.phase[2]));
        if (calmPhase) drift *= r.time / GRACE;
        // le corps doit rester immobile : marcher, courir ou sauter brise la concentration
        double moved = Math.hypot(now.getX() - r.last.getX(), now.getZ() - r.last.getZ()) / DT;   // blocs/s
        if (moved > 4.6) {                                                   // course : quasi sûr d'échouer
            r.vel += (rnd.nextDouble() - 0.5) * 0.9;
            r.stability -= DT * 2.4;
            r.jolt = true;
        } else if (moved > 0.8) {                                            // marche
            r.vel += (rnd.nextDouble() - 0.5) * 0.45;
            r.stability -= DT * 0.9;
            r.jolt = true;
        }
        boolean rising = now.getY() - r.last.getY() > 0.1;
        if (rising && !r.airborne) {                                         // saut : un gros à-coup
            r.airborne = true;
            r.vel += (rnd.nextBoolean() ? 1 : -1) * 1.0;
            r.stability -= 0.3;
            r.jolt = true;
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 0.7f);
        }
        if (((org.bukkit.entity.Entity) p).isOnGround()) r.airborne = false;
        r.last = now;
        // à-coups (rang B et plus)
        if (r.t.gusts() && r.time >= r.nextGust) {
            r.vel += (rnd.nextBoolean() ? 1 : -1) * (0.3 + rnd.nextDouble() * 0.25) * r.t.drift();
            r.nextGust = r.time + 2 + rnd.nextDouble() * 3;
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.7f, 0.6f);
        }
        r.vel = (r.vel + drift * DT) * 0.92;
        r.pos = Math.max(-1, Math.min(1, r.pos + r.vel * DT + control));
        if (Math.abs(r.pos) >= 1) r.vel *= -0.3;
        // zone mobile (A, S)
        if (r.t.moving()) r.center = 0.5 * Math.sin(r.time * 0.35 + r.phase[1]) * Math.min(1, r.time / 4);

        boolean in = Math.abs(r.pos - r.center) <= r.zone;
        if (!calmPhase) {
            if (in) {
                r.held += DT;
                r.stability = Math.min(1, r.stability + DT * 0.12);
            } else {
                r.stability -= DT / r.t.tolerance();
            }
        }

        render(r, in);
        if (r.held >= r.t.seconds()) { end(r, true); return; }
        if (r.stability <= 0) { end(r, false); return; }
        if (r.time > r.t.seconds() * 3 + GRACE) end(r, false);
    }

    /** Coup encaissé : la concentration vacille. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Run r = runs.get(p.getUniqueId());
        if (r == null) return;
        r.vel += (rnd.nextBoolean() ? 1 : -1) * Math.min(1.4, 0.8 + e.getFinalDamage() * 0.1);
        r.stability -= 0.35;
        r.jolt = true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Run r = runs.get(e.getPlayer().getUniqueId());
        if (r != null) stop(r);
    }

    /* ------------------------------------------------------------------ rendu */

    private void render(Run r, boolean in) {
        Player p = r.p;
        renderAura(r, in);
        if (r.mod) { send(r, "run", in); return; }
        // jauge crantée : [ ███████ ] — glyphe plein de la police bitmap vanilla (net à toute échelle)
        int needle = (int) Math.round((r.pos + 1) / 2 * (CELLS - 1));
        TextComponent.Builder bar = Component.text().append(Component.text("[ ", GOLD));
        for (int i = 0; i < CELLS; i++) {
            double x = -1 + 2.0 * i / (CELLS - 1);
            boolean zone = Math.abs(x - r.center) <= r.zone;
            if (i == needle) bar.append(Component.text("█", in ? CHAKRA : LOST));
            else bar.append(Component.text("█", zone ? GOLD : DIM));
        }
        bar.append(Component.text(" ]", GOLD));
        p.sendActionBar(bar.build());

        float prog = (float) Math.min(1, r.held / r.t.seconds());
        r.focus.progress(prog);
        r.focus.color(in ? BossBar.Color.BLUE : BossBar.Color.RED);
        r.focus.name(Component.text("Concentration du chakra  ·  "
                + String.format(Locale.FRANCE, "%.1f / %.0f s", r.held, r.t.seconds()), in ? CHAKRA : LOST));
        float st = (float) Math.max(0, r.stability);
        r.calm.progress(st);
        r.calm.color(st > 0.6 ? BossBar.Color.GREEN : st > 0.3 ? BossBar.Color.YELLOW : BossBar.Color.RED);
        r.calm.name(Component.text(st > 0.3 ? "Stabilité" : "Stabilité — ton chakra se disperse !",
                st > 0.3 ? NamedTextColor.GRAY : NamedTextColor.RED));
    }

    /** État pour le mod : {@code {"t":"conc","st":"run|ok|ko|stop", ...}}. */
    private void send(Run r, String st, boolean in) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "conc");
        o.addProperty("st", st);
        o.addProperty("rank", String.valueOf(r.rank));
        o.addProperty("helped", r.helped);
        o.addProperty("zone", Math.round(r.zone * 1000) / 1000.0);
        o.addProperty("c", Math.round(r.center * 1000) / 1000.0);
        o.addProperty("pos", Math.round(r.pos * 1000) / 1000.0);
        o.addProperty("held", Math.round(r.held * 100) / 100.0);
        o.addProperty("secs", r.t.seconds());
        o.addProperty("stab", Math.round(Math.max(0, r.stability) * 100) / 100.0);
        o.addProperty("in", in);
        if (r.jolt) o.addProperty("jolt", true);
        r.jolt = false;
        try {
            r.p.sendPluginMessage(plugin, CHANNEL, o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
            // canal fermé entre-temps
        }
    }

    private void renderAura(Run r, boolean in) {
        Player p = r.p;
        // aura autour du joueur, visible de tous : bleue et calme dans la zone, rouge et heurtée en dehors
        if ((int) (r.time / DT) % 2 == 0) {
            Location c = p.getLocation().add(0, 1.0, 0);
            double a = r.time * 4;
            for (int k = 0; k < 3; k++) {
                double ang = a + k * Math.PI * 2 / 3;
                Location at = c.clone().add(Math.cos(ang) * 0.7, Math.sin(r.time * 3 + k) * 0.25, Math.sin(ang) * 0.7);
                p.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(in ? Color.fromRGB(0x6FD3FF) : Color.fromRGB(0xE0505A), in ? 0.8f : 1.1f));
            }
            if (!in) p.getWorld().spawnParticle(Particle.SMOKE, c, 2, 0.3, 0.3, 0.3, 0.01);
        }
        if (in && (int) (r.time / DT) % 20 == 0 && r.held > 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 0.8f + (float) (r.held / r.t.seconds()));
        }
    }

    private void end(Run r, boolean ok) {
        Player p = r.p;
        stop(r);
        if (r.mod) send(r, ok ? "ok" : "ko", ok);
        Location c = p.getLocation().add(0, 1, 0);
        if (ok) {
            p.getWorld().spawnParticle(Particle.END_ROD, c, 30, 0.4, 0.6, 0.4, 0.05);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);
            if (!r.mod) p.showTitle(Title.title(Component.text("集", GOLD, TextDecoration.BOLD),
                    Component.text("Chakra concentré", CHAKRA),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(300))));
        } else {
            p.getWorld().spawnParticle(Particle.CLOUD, c, 20, 0.4, 0.5, 0.4, 0.03);
            p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.9f);
            if (!r.mod) p.sendMessage(Component.text("Ton chakra s'est dispersé : la concentration a échoué.", NamedTextColor.RED));
        }
        if (r.then != null) r.then.accept(ok);
    }

    /** Arrêt sans verdict (déconnexion, entraînement annulé). */
    public void abort(Player p) {
        Run r = runs.get(p.getUniqueId());
        if (r == null) return;
        stop(r);
        if (r.mod) send(r, "stop", false);
    }

    public void abortAll() {
        for (Run r : runs.values().toArray(new Run[0])) stop(r);
    }

    private void stop(Run r) {
        runs.remove(r.p.getUniqueId(), r);
        if (r.task != null) r.task.cancel();
        r.p.hideBossBar(r.focus);
        r.p.hideBossBar(r.calm);
        if (!r.mod) r.p.sendActionBar(Component.empty());
    }

    private static double wrap(double d) {
        d %= 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }
}
