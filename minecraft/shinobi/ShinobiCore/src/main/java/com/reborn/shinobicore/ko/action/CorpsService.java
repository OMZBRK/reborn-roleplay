package com.reborn.shinobicore.ko.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.AutoMe;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.KoState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Actions sur un corps à terre ou KO, depuis le <b>menu d'interaction</b> du mod (touche R, clic sur le corps).
 *
 * <ul>
 *   <li>Canal {@code reborn:corps} (S2C, JSON) : chaque demi-seconde, les corps proches du joueur
 *       ({@code {"t":"bodies","b":[{"u","ph","rpk"}],"ex":[{"id","n"}]}} — ph 1 à terre, 2 inconscient, 3 mort ;
 *       rpk 0 rien, 1 demande en attente du staff, 2 RPK accordée à ce joueur ; ex = ses exécutions). Le mod en
 *       déduit le menu. La scène de mort passe aussi par ce canal ({@link DeathScene}).</li>
 *   <li>{@code /corps <action> <joueur> [exécution]} : porter, gorge, fouiller, rpk, achever, executer — toujours
 *       revérifié ici (distance, état du corps, RPK accordée, exécution possédée).</li>
 *   <li><b>Attraper à la gorge</b> : le corps est tenu debout devant soi, sans limite de durée, on avance
 *       lentement ; accroupi pour lâcher.</li>
 *   <li><b>Exécution</b> : après une RPK validée, la victime est placée face au tueur, les deux jouent leur
 *       animation, et la mort tombe à l'instant prévu ({@link KillRequestManager#kill}).</li>
 * </ul>
 */
public final class CorpsService implements Listener, TabExecutor {

    public static final String CHANNEL = "reborn:corps";
    private static final double REACH = 3.5;
    private static final double SCAN = 16;

    private final ShinobiCore plugin;
    private final Executions executions;
    /** victime → celui qui la tient (gorge ou exécution). */
    private final Map<UUID, UUID> held = new ConcurrentHashMap<>();
    private final Map<UUID, Long> heldSince = new ConcurrentHashMap<>();
    /** tueur → exécution en cours. */
    private final Map<UUID, Run> running = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastSent = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long ticks;

    private record Run(UUID victim, Executions.Def def, Location killerAt, long startTick) {}

    public CorpsService(ShinobiCore plugin) {
        this.plugin = plugin;
        this.executions = new Executions(plugin);
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public Executions executions() { return executions; }

    public void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L); }

    public void stop() {
        if (task != null) task.cancel();
        for (UUID v : new ArrayList<>(held.keySet())) release(v);
    }

    /** Corps tenu debout (gorge, exécution) : le KO ne lui remet pas sa pose couchée. */
    public boolean isHeld(UUID victim) { return held.containsKey(victim); }

    public void send(Player p, JsonObject o) {
        p.sendPluginMessage(plugin, CHANNEL, o.toString().getBytes(StandardCharsets.UTF_8));
    }

    /* ================================================================ ticker */

    private void tick() {
        ticks++;
        // corps tenus : devant celui qui tient
        for (Map.Entry<UUID, UUID> e : held.entrySet()) {
            Player v = Bukkit.getPlayer(e.getKey()), h = Bukkit.getPlayer(e.getValue());
            Run run = h != null ? running.get(h.getUniqueId()) : null;
            boolean grab = run == null;
            if (v == null || h == null || !plugin.ko().isKo(v.getUniqueId()) || plugin.ko().isKo(h.getUniqueId())
                    || !v.getWorld().equals(h.getWorld())) {
                if (run != null && h != null) abort(h, "La scène est interrompue.");
                release(e.getKey());
                continue;
            }
            Location base = grab ? h.getLocation() : run.killerAt();
            Vector dir = base.getDirection().setY(0);
            if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
            dir.normalize();
            Location at = base.clone().add(dir.multiply(grab ? 0.75 : 1.1));
            at.setY(base.getY() + (grab ? 0.3 : 0));
            at.setYaw(base.getYaw() + 180f);
            at.setPitch(0f);
            if (v.getLocation().distanceSquared(at) > 0.004 || Math.abs(v.getLocation().getYaw() - at.getYaw()) > 2) {
                v.teleport(at);
            }
            if (v.getPose() != Pose.STANDING) v.setPose(Pose.STANDING, true);
        }
        // exécutions : tueur figé, mort à l'instant prévu, fin de scène
        for (Map.Entry<UUID, Run> e : running.entrySet()) {
            Player k = Bukkit.getPlayer(e.getKey());
            Run run = e.getValue();
            Player v = Bukkit.getPlayer(run.victim());
            if (k == null) { running.remove(e.getKey()); release(run.victim()); continue; }
            long t = ticks - run.startTick();
            if (k.getLocation().distanceSquared(run.killerAt()) > 0.01) k.teleport(run.killerAt());
            if (t == run.def().deathTick()) {
                if (v != null) {
                    v.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
                    v.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, v.getLocation().add(0, 1.2, 0), 8, 0.2, 0.3, 0.2, 0.1);
                    release(run.victim());
                    plugin.killRequests().kill(k, v, "exécution « " + run.def().name() + " »");
                }
            }
            if (t >= run.def().durationTicks()) finish(k);
        }
        if (ticks % 10 == 0) broadcastBodies();
    }

    /* ======================================================= état des corps */

    private void broadcastBodies() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player viewer : online) {
            if (!viewer.getListeningPluginChannels().contains(CHANNEL)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("t", "bodies");
            JsonArray b = new JsonArray();
            for (Player p : online) {
                if (p == viewer || !p.getWorld().equals(viewer.getWorld())
                        || p.getLocation().distanceSquared(viewer.getLocation()) > SCAN * SCAN) continue;
                int ph = 0;
                if (plugin.deathScene() != null && plugin.deathScene().isDead(p.getUniqueId())) ph = 3;
                else {
                    KoState st = plugin.ko().getKo(p.getUniqueId());
                    if (st != null) ph = st.isDowned() ? 1 : 2;
                }
                if (ph == 0) continue;
                JsonObject e = new JsonObject();
                e.addProperty("u", p.getUniqueId().toString());
                e.addProperty("ph", ph);
                e.addProperty("rpk", ph == 3 ? 0 : plugin.killRequests().statusFor(viewer.getUniqueId(), p.getUniqueId()));
                b.add(e);
            }
            o.add("b", b);
            JsonArray ex = new JsonArray();
            for (Executions.Def d : executions.ownedBy(viewer)) {
                JsonObject e = new JsonObject();
                e.addProperty("id", d.id());
                e.addProperty("n", d.name());
                ex.add(e);
            }
            o.add("ex", ex);
            JsonArray cp = new JsonArray();
            if (plugin.deathScene() != null) {
                plugin.deathScene().corpsesNear(viewer.getLocation(), 48).forEach((id, look) -> {
                    JsonObject e = new JsonObject();
                    e.addProperty("m", id.toString());
                    e.addProperty("a", look);
                    cp.add(e);
                });
            }
            o.add("cp", cp);
            String json = o.toString();
            if (json.equals(lastSent.get(viewer.getUniqueId()))) continue;
            lastSent.put(viewer.getUniqueId(), json);
            viewer.sendPluginMessage(plugin, CHANNEL, json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /* ============================================================== actions */

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(s instanceof Player p) || args.length < 1) return true;
        String act = args[0].toLowerCase(Locale.ROOT);
        if (act.equals("quitter")) {
            if (plugin.deathScene() != null) plugin.deathScene().leave(p);
            return true;
        }
        if (args.length < 2) return true;
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null || t == p) { p.sendMessage(Component.text("Personne ici.", NamedTextColor.GRAY)); return true; }
        if (plugin.ko().isKo(p.getUniqueId()) || (plugin.deathScene() != null && plugin.deathScene().isDead(p.getUniqueId()))) return true;
        if (!t.getWorld().equals(p.getWorld()) || t.getLocation().distance(p.getLocation()) > REACH) {
            p.sendActionBar(Component.text("Trop loin.", NamedTextColor.GRAY));
            return true;
        }
        if (!plugin.ko().isKo(t.getUniqueId())) {
            p.sendActionBar(Component.text("Cette personne n'est pas à terre.", NamedTextColor.GRAY));
            return true;
        }
        if (running.containsKey(p.getUniqueId())) return true;
        String tName = name(t);
        switch (act) {
            case "porter" -> {
                release(t.getUniqueId());
                if (!plugin.porter().carry(p, t)) p.sendMessage(Component.text("Impossible de porter cette personne maintenant.", NamedTextColor.RED));
            }
            case "gorge" -> grab(p, t, tName);
            case "fouiller" -> {
                if (plugin.coreGui() != null) plugin.coreGui().openFouiller(p, t.getUniqueId());
                AutoMe.broadcast(plugin, p, "fouille les affaires de " + tName + ".");
            }
            case "rpk" -> plugin.killRequests().request(p, t);
            case "achever" -> {
                if (plugin.killRequests().validatedFor(p.getUniqueId(), t.getUniqueId()) == null) {
                    p.sendMessage(Component.text("Il faut une RPK validée par le staff.", NamedTextColor.RED));
                    return true;
                }
                release(t.getUniqueId());
                plugin.porter().releaseAllInvolving(t.getUniqueId());
                AutoMe.broadcast(plugin, p, "achève " + tName + ".");
                t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
                plugin.killRequests().kill(p, t, "achevé");
            }
            case "executer" -> execute(p, t, tName, args.length > 2 ? args[2] : "");
            default -> { }
        }
        return true;
    }

    private void grab(Player p, Player t, String tName) {
        UUID cur = held.get(t.getUniqueId());
        if (cur != null) {
            if (cur.equals(p.getUniqueId())) release(t.getUniqueId());
            return;
        }
        plugin.porter().releaseAllInvolving(t.getUniqueId());
        KoState st = plugin.ko().getKo(t.getUniqueId());
        if (st != null) st.setCarrierPlayerId(p.getUniqueId());
        held.put(t.getUniqueId(), p.getUniqueId());
        heldSince.put(t.getUniqueId(), System.currentTimeMillis());
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 2, true, false, false));
        play(p, "attraper_gorge");
        AutoMe.broadcast(plugin, p, "attrape " + tName + " à la gorge.");
        p.sendActionBar(Component.text("Tu le tiens à la gorge · accroupis-toi pour lâcher.", NamedTextColor.GRAY));
    }

    private void execute(Player p, Player t, String tName, String id) {
        if (plugin.killRequests().validatedFor(p.getUniqueId(), t.getUniqueId()) == null) {
            p.sendMessage(Component.text("Il faut une RPK validée par le staff pour exécuter.", NamedTextColor.RED));
            return;
        }
        Executions.Def d = executions.get(id);
        if (d == null || !executions.owns(p, d)) {
            p.sendMessage(Component.text("Exécution inconnue ou non possédée.", NamedTextColor.RED));
            return;
        }
        plugin.porter().releaseAllInvolving(t.getUniqueId());
        plugin.porter().releaseAllInvolving(p.getUniqueId());
        release(t.getUniqueId());
        Location at = p.getLocation().clone();
        at.setPitch(0f);
        KoState st = plugin.ko().getKo(t.getUniqueId());
        if (st != null) st.setCarrierPlayerId(p.getUniqueId());
        held.put(t.getUniqueId(), p.getUniqueId());
        heldSince.put(t.getUniqueId(), System.currentTimeMillis());
        running.put(p.getUniqueId(), new Run(t.getUniqueId(), d, at, ticks));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, d.durationTicks() + 10, 6, true, false, false));
        play(p, d.killerEmote());
        play(t, d.victimEmote());
        AutoMe.broadcast(plugin, p, "exécute " + tName + ".");
    }

    private void finish(Player k) {
        Run run = running.remove(k.getUniqueId());
        if (run == null) return;
        k.removePotionEffect(PotionEffectType.SLOWNESS);
        if (plugin.emotes() != null) plugin.emotes().stop(k);
        release(run.victim());
    }

    private void abort(Player k, String why) {
        Run run = running.remove(k.getUniqueId());
        if (run == null) return;
        k.removePotionEffect(PotionEffectType.SLOWNESS);
        if (plugin.emotes() != null) {
            plugin.emotes().stop(k);
            Player v = Bukkit.getPlayer(run.victim());
            if (v != null) plugin.emotes().stop(v);
        }
        k.sendActionBar(Component.text(why, NamedTextColor.GRAY));
    }

    private void play(Player p, String emote) {
        if (emote == null || emote.isBlank() || plugin.emotes() == null) return;
        var e = plugin.emotes().resolve(emote);
        if (e != null) plugin.emotes().play(p, e);
    }

    /** Lâche le corps tenu : il retombe là où il est. */
    public void release(UUID victim) {
        UUID h = held.remove(victim);
        heldSince.remove(victim);
        if (h == null) return;
        Player holder = Bukkit.getPlayer(h);
        if (holder != null && !running.containsKey(h)) holder.removePotionEffect(PotionEffectType.SLOWNESS);
        KoState st = plugin.ko().getKo(victim);
        Player v = Bukkit.getPlayer(victim);
        if (st != null) {
            st.setCarrierPlayerId(null);
            if (v != null) st.setLockLocation(v.getLocation().clone());
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent ev) {
        if (!ev.isSneaking()) return;
        UUID me = ev.getPlayer().getUniqueId();
        if (running.containsKey(me)) return;
        for (Map.Entry<UUID, UUID> e : held.entrySet()) if (e.getValue().equals(me)) release(e.getKey());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        UUID me = ev.getPlayer().getUniqueId();
        lastSent.remove(me);
        if (running.containsKey(me)) abort(ev.getPlayer(), "");
        release(me);
        for (Map.Entry<UUID, UUID> e : held.entrySet()) if (e.getValue().equals(me)) release(e.getKey());
    }

    private String name(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        return c != null ? c.name() : p.getName();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
