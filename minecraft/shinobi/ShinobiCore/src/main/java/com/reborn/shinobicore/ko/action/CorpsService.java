package com.reborn.shinobicore.ko.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.AutoMe;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.KoJournal;
import com.reborn.shinobicore.ko.KoState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
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
 * Actions sur un corps à terre, KO ou mort — et sur un personnage <b>en douleur</b> — depuis le <b>menu
 * d'interaction</b> du mod (touche R, clic sur la personne).
 *
 * <ul>
 *   <li>Canal {@code reborn:corps} (S2C, JSON) : chaque demi-seconde, si quelque chose a changé —
 *       {@code b} les corps proches ({@code {"u","ph","rpk"}} — ph 1 à terre, 2 inconscient, 3 mort ; rpk 0 rien,
 *       1 demande en attente du staff, 2 RPK accordé à ce joueur), {@code dl} les personnages en douleur proches
 *       ({@code {"u"}}), {@code ex} ses exécutions, {@code cp} les corps au sol, et {@code me} son propre état
 *       ({@code d} en douleur, {@code c} porté, {@code s} jauge de lutte 0..1, {@code x} exécution en cours :
 *       {@code r} 0 exécuteur / 1 victime, {@code e} ticks écoulés, {@code d} durée, {@code k} instant du coup,
 *       {@code l} 1 = mort RP). Un mod plus ancien ignore {@code dl} et {@code me}. La scène de mort passe aussi par
 *       ce canal ({@link DeathScene}).</li>
 *   <li>{@code /corps <action> <joueur> [exécution]} : porter, gorge, fouiller, rpk, executer (et achever = exécuter
 *       sans animation) ; {@code /corps annuler} : la touche Exécuter du mod a été relâchée — toujours revérifié ici (distance, état, RPK accordé, exécution possédée).</li>
 *   <li><b>Attraper à la gorge</b> (corps KO) : tenu debout devant soi, sans limite de durée, on avance lentement ;
 *       accroupi pour lâcher.</li>
 *   <li><b>Exécuter</b> — une seule action, dont l'effet dépend de la cible :
 *     <ul>
 *       <li>cible <b>en douleur</b> : mise hors de combat, <b>sans RPK</b> — la cible tombe <b>inconsciente</b>
 *           (sans agonie ni Volonté) ; ce n'est pas une mort ;</li>
 *       <li>cible <b>KO</b> avec une <b>RPK validé</b> pour l'exécuteur : <b>mort RP</b>
 *           ({@link KillRequestManager#kill}).</li>
 *     </ul>
 *     Canalisation : la victime est tenue face à l'exécuteur, les deux sont figés, l'animation de l'exécution
 *     choisie joue (ou « sans animation » : {@code ko.execution.canalisation-secondes}, 3 s), le coup tombe à
 *     l'instant prévu. <b>Interrompue</b> si l'exécuteur est frappé, s'accroupit, tombe en douleur ou KO, si la
 *     victime en douleur est soignée, ou si l'un des deux se déconnecte.</li>
 * </ul>
 */
public final class CorpsService implements Listener, TabExecutor {

    public static final String CHANNEL = "reborn:corps";
    private static final double REACH = 3.5;
    private static final double SCAN = 16;
    /** Identifiant de l'exécution sans animation (toujours disponible). */
    public static final String SOBRE = "sobre";
    /** Lenteurs d'état en modificateurs nommés (plus de potion infinie qui survit au crash ni de retrait par type). */
    private static final NamespacedKey LENTEUR_GORGE = new NamespacedKey("shinobicore", "tient_gorge");
    private static final NamespacedKey LENTEUR_EXECUTION = new NamespacedKey("shinobicore", "execute");

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
    /** Porter / fouiller une personne consciente avec son accord (GAMEPLAN §14, 24/10). */
    private final DemandeService demandes;

    /** {@code lethal} : mort RP (cible KO + RPK) ; sinon mise hors de combat d'une cible en douleur. */
    private record Run(UUID victim, Executions.Def def, Location killerAt, long startTick, boolean lethal) {}

    public CorpsService(ShinobiCore plugin) {
        this.plugin = plugin;
        this.executions = new Executions(plugin);
        this.demandes = new DemandeService(plugin, this);
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public Executions executions() { return executions; }

    public void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L); }

    public void stop() {
        if (task != null) task.cancel();
        for (UUID k : new ArrayList<>(running.keySet())) {
            Player kp = Bukkit.getPlayer(k);
            if (kp != null) abort(kp, "");
        }
        for (UUID v : new ArrayList<>(held.keySet())) release(v);
    }

    /** Corps tenu debout (gorge, exécution) : le KO ne lui remet pas sa pose couchée. */
    public boolean isHeld(UUID victim) { return held.containsKey(victim); }

    /** Celui qui tient cette victime (gorge ou exécution), ou null. */
    public UUID holderOf(UUID victim) { return victim == null ? null : held.get(victim); }

    /** Exécute quelqu'un en ce moment (touche F maintenue). */
    public boolean isExecuting(UUID killer) { return killer != null && running.containsKey(killer); }

    /** Tient quelqu'un à la gorge (hors exécution). */
    public boolean holdsThroat(UUID holder) {
        if (holder == null || running.containsKey(holder)) return false;
        return held.containsValue(holder);
    }

    /** Tient quelqu'un (gorge) ou exécute en ce moment. */
    public boolean isHolding(UUID holder) {
        return holder != null && (running.containsKey(holder) || held.containsValue(holder));
    }

    public void send(Player p, JsonObject o) {
        p.sendPluginMessage(plugin, CHANNEL, o.toString().getBytes(StandardCharsets.UTF_8));
    }

    private boolean pain(UUID id) {
        return plugin.douleur() != null && plugin.douleur().isInPain(id);
    }

    /** Exécution sans animation : canalisation seule, le coup tombe à la fin. */
    private Executions.Def sobre() {
        int t = (int) Math.round(Math.max(0.5, plugin.getConfig().getDouble("ko.execution.canalisation-secondes", 3)) * 20);
        return new Executions.Def(SOBRE, "Sans animation", "", "", t, t, true);
    }

    /* ================================================================ ticker */

    private void tick() {
        ticks++;
        // corps tenus : devant celui qui tient
        for (Map.Entry<UUID, UUID> e : held.entrySet()) {
            Player v = Bukkit.getPlayer(e.getKey()), h = Bukkit.getPlayer(e.getValue());
            Run run = h != null ? running.get(h.getUniqueId()) : null;
            boolean grab = run == null;
            boolean valid = v != null && (plugin.ko().isKo(v.getUniqueId())
                    || (run != null && !run.lethal() && pain(v.getUniqueId())));
            if (v == null || h == null || !valid || plugin.ko().isKo(h.getUniqueId())
                    || !v.getWorld().equals(h.getWorld())) {
                if (run != null && h != null) abort(h, v != null && !valid && !run.lethal()
                        ? "Il s'est repris : l'exécution échoue." : "La scène est interrompue.");
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
        // exécutions : tueur figé, le coup tombe à l'instant prévu, fin de scène
        for (Map.Entry<UUID, Run> e : running.entrySet()) {
            Player k = Bukkit.getPlayer(e.getKey());
            Run run = e.getValue();
            Player v = Bukkit.getPlayer(run.victim());
            if (k == null) { running.remove(e.getKey()); release(run.victim()); continue; }
            long t = ticks - run.startTick();
            if (k.getLocation().distanceSquared(run.killerAt()) > 0.01) k.teleport(run.killerAt());
            if (t == run.def().deathTick() && v != null) strike(k, v, run);
            if (t >= run.def().durationTicks()) finish(k);
        }
        if (ticks % 10 == 0) broadcastBodies();
        if (ticks % 20 == 0) demandes.tick();
    }

    /** L'instant du coup : mort RP, ou mise hors de combat. */
    private void strike(Player k, Player v, Run run) {
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
        v.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, v.getLocation().add(0, 1.2, 0), 8, 0.2, 0.3, 0.2, 0.1);
        String how = run.def().id().equals(SOBRE) ? (run.lethal() ? "achevé" : "sans animation")
                : "exécution « " + run.def().name() + " »";
        if (run.lethal()) {
            release(run.victim());
            plugin.killRequests().kill(k, v, how);
            return;
        }
        if (plugin.ko().executeToKo(k, v)) {
            KoJournal.log(plugin, k, "Exécution", "met K.O. " + KoJournal.who(plugin, v) + " (" + how + ")");
        }
    }

    /* ======================================================= état des corps */

    private void broadcastBodies() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player viewer : online) {
            if (!viewer.getListeningPluginChannels().contains(CHANNEL)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("t", "bodies");
            JsonArray b = new JsonArray();
            JsonArray dl = new JsonArray();
            for (Player p : online) {
                if (p == viewer || !p.getWorld().equals(viewer.getWorld())
                        || p.getLocation().distanceSquared(viewer.getLocation()) > SCAN * SCAN) continue;
                int ph = 0;
                if (plugin.deathScene() != null && plugin.deathScene().isDead(p.getUniqueId())) ph = 3;
                else {
                    KoState st = plugin.ko().getKo(p.getUniqueId());
                    if (st != null) ph = st.isDowned() ? 1 : 2;
                }
                if (ph == 0) {
                    if (pain(p.getUniqueId())) {
                        JsonObject e = new JsonObject();
                        e.addProperty("u", p.getUniqueId().toString());
                        dl.add(e);
                    }
                    continue;
                }
                JsonObject e = new JsonObject();
                e.addProperty("u", p.getUniqueId().toString());
                e.addProperty("ph", ph);
                e.addProperty("rpk", ph == 3 ? 0 : plugin.killRequests().statusFor(viewer.getUniqueId(), p.getUniqueId()));
                b.add(e);
            }
            o.add("b", b);
            o.add("dl", dl);
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
            o.add("me", me(viewer));
            String json = o.toString();
            if (json.equals(lastSent.get(viewer.getUniqueId()))) continue;
            lastSent.put(viewer.getUniqueId(), json);
            viewer.sendPluginMessage(plugin, CHANNEL, json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Son propre état (douleur, porté, lutte, exécution en cours) pour le HUD discret du mod. */
    private JsonObject me(Player viewer) {
        UUID id = viewer.getUniqueId();
        JsonObject me = new JsonObject();
        if (pain(id)) me.addProperty("d", 1);
        if (plugin.porter().carrierOf(id) != null) {
            me.addProperty("c", 1);
            if (plugin.porter().consenti(id)) me.addProperty("v", 1);   // de plein gré : « Espace · descendre »
            double s = plugin.porter().struggleOf(id);
            if (s >= 0) me.addProperty("s", Math.round(s * 100) / 100.0);
        }
        Run run = running.get(id);
        int role = 0;
        if (run == null) {
            for (Run r : running.values()) if (r.victim().equals(id)) { run = r; role = 1; break; }
        }
        if (run != null) {
            JsonObject x = new JsonObject();
            x.addProperty("r", role);
            x.addProperty("e", ticks - run.startTick());
            x.addProperty("d", run.def().durationTicks());
            x.addProperty("k", run.def().deathTick());
            x.addProperty("l", run.lethal() ? 1 : 0);
            me.add("x", x);
        }
        return me;
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
        // touche Exécuter relâchée avant la fin de la canalisation (mod) : on renonce
        if (act.equals("annuler")) {
            if (running.containsKey(p.getUniqueId())) abort(p, "Tu renonces.");
            return true;
        }
        // Demandes d'accord (14 · 24/10) : Entrée / Retour côté mod.
        if (act.equals("accepter")) { demandes.accepter(p); return true; }
        if (act.equals("refuser")) { demandes.refuser(p); return true; }
        if (act.equals("demander")) {
            if (args.length >= 3) demandes.demander(p, args[1].toLowerCase(Locale.ROOT), args[2]);
            return true;
        }
        if (args.length < 2) return true;
        // Monnaie (15/10) : un corps laissé au sol (mannequin) se fouille même si le joueur est parti.
        if (act.equals("fouiller") && args[1].startsWith("corps:")) {
            if (fouilleImpossible(p)) return true;
            if (com.reborn.shinobicore.etat.Verrous.gele(p.getUniqueId())
                    || com.reborn.shinobicore.etat.Verrous.cinematique(p.getUniqueId())) {
                p.sendActionBar(Component.text("Tu ne peux pas maintenant.", NamedTextColor.GRAY));
                return true;
            }
            String r = com.reborn.shinobicore.etat.Verrous.refus(p.getUniqueId(), com.reborn.shinobicore.api.etat.Capacite.FOUILLER);
            if (r != null) { p.sendActionBar(Component.text(r, NamedTextColor.GRAY)); return true; }
            plugin.monnaie().fouille().commencerCorps(p, args[1].substring("corps:".length()));
            return true;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null || t == p) { p.sendMessage(Component.text("Personne ici.", NamedTextColor.GRAY)); return true; }
        if (plugin.ko().isKo(p.getUniqueId()) || (plugin.deathScene() != null && plugin.deathScene().isDead(p.getUniqueId()))) return true;
        if (pain(p.getUniqueId())) {
            p.sendActionBar(Component.text("À bout de forces : tu ne peux pas.", NamedTextColor.GRAY));
            return true;
        }
        if (plugin.porter().carrierOf(p.getUniqueId()) != null) return true;
        // Correctifs urgents (ETATS_JOUEUR §9 étape 0) : un gelé n'agit pas (ses commandes restaient libres)…
        if (com.reborn.shinobicore.etat.Verrous.gele(p.getUniqueId())
                || com.reborn.shinobicore.etat.Verrous.cinematique(p.getUniqueId())) {
            p.sendActionBar(Component.text("Tu ne peux pas maintenant.", NamedTextColor.GRAY));
            return true;
        }
        // … et on ne porte, ne tient, ne fouille ni n'exécute un gelé, un staff en mode staff ou un invisible.
        if (com.reborn.shinobicore.etat.Verrous.horsJeu(t.getUniqueId())
                || (plugin.vanish() != null && plugin.vanish().isVanished(t.getUniqueId()))) {
            p.sendMessage(Component.text("Personne ici.", NamedTextColor.GRAY));
            return true;
        }
        if (com.reborn.shinobicore.etat.Verrous.gele(t.getUniqueId())) {
            p.sendActionBar(Component.text("Gelé par le staff : on n'y touche pas.", NamedTextColor.GRAY));
            return true;
        }
        if (!t.getWorld().equals(p.getWorld()) || t.getLocation().distance(p.getLocation()) > REACH) {
            p.sendActionBar(Component.text("Trop loin.", NamedTextColor.GRAY));
            return true;
        }
        boolean dead = plugin.deathScene() != null && plugin.deathScene().isDead(t.getUniqueId());
        boolean ko = plugin.ko().isKo(t.getUniqueId());
        boolean pain = !ko && !dead && pain(t.getUniqueId());
        boolean allowed = ko
                || (dead && (act.equals("porter") || act.equals("fouiller")))
                || (pain && (act.equals("porter") || act.equals("executer")));
        if (!allowed) {
            p.sendActionBar(Component.text(dead ? "Ce corps ne respire plus." : pain ? "Il tient encore debout."
                    : "Cette personne n'est pas à terre.", NamedTextColor.GRAY));
            return true;
        }
        if (running.containsKey(p.getUniqueId())) return true;
        // Étape 2 d'ETATS_JOUEUR : la matrice tranche aussi (menotté, entravé, occupé, prison… ; cible en duel, tenue
        // par l'exécution d'un autre — un seul détenteur de la couche Contrôle, §5.2 règle 3).
        com.reborn.shinobicore.api.etat.Capacite capActeur = switch (act) {
            case "porter", "gorge" -> com.reborn.shinobicore.api.etat.Capacite.PORTER;
            case "executer", "achever" -> com.reborn.shinobicore.api.etat.Capacite.EXECUTER;
            case "fouiller" -> com.reborn.shinobicore.api.etat.Capacite.FOUILLER;
            default -> null;
        };
        com.reborn.shinobicore.api.etat.Capacite capCible = switch (act) {
            case "porter", "gorge" -> com.reborn.shinobicore.api.etat.Capacite.ETRE_PORTE;
            case "executer", "achever" -> com.reborn.shinobicore.api.etat.Capacite.ETRE_EXECUTE;
            case "fouiller" -> com.reborn.shinobicore.api.etat.Capacite.ETRE_FOUILLE;
            default -> null;
        };
        if (capActeur != null) {
            String r = com.reborn.shinobicore.etat.Verrous.refus(p.getUniqueId(), capActeur);
            if (r != null) { p.sendActionBar(Component.text(r, NamedTextColor.GRAY)); return true; }
            r = com.reborn.shinobicore.etat.Verrous.refusSur(p.getUniqueId(), t.getUniqueId(), capCible);
            if (r != null) { p.sendActionBar(Component.text("Impossible : " + r + ".", NamedTextColor.GRAY)); return true; }
        }
        String tName = name(t);
        switch (act) {
            case "porter" -> {
                release(t.getUniqueId());
                if (plugin.porter().carry(p, t)) {
                    if (pain) AutoMe.broadcast(plugin, p, "soulève " + tName + " de force.");
                } else {
                    p.sendMessage(Component.text("Impossible de porter cette personne maintenant.", NamedTextColor.RED));
                }
            }
            case "gorge" -> grab(p, t, tName);
            case "fouiller" -> {
                // Monnaie (15/10) : 3 s immobile, puis la sacoche du corps (barre, sac, bourse) en mode fouille.
                // 18/10 : toujours la sacoche RP — plus de repli sur l'ancien coffre vanilla (FouillerScreen).
                if (fouilleImpossible(p)) return true;
                plugin.monnaie().fouille().commencer(p, t);
                KoJournal.log(plugin, p, "Fouille", KoJournal.who(plugin, t));
            }
            case "rpk" -> plugin.killRequests().request(p, t);
            case "achever" -> execute(p, t, tName, SOBRE);
            case "executer" -> execute(p, t, tName, args.length > 2 ? args[2] : SOBRE);
            default -> { }
        }
        return true;
    }

    /**
     * La fouille se fait dans la sacoche RP du corps (écran du mod, canal {@code reborn:monnaie}) et nulle part
     * ailleurs (18/10) : sans le module monnaie ou sans le canal, on refuse avec un message plutôt que d'ouvrir un
     * coffre vanilla — même règle que l'ancien menu KO au clic droit, retiré au profit du menu R.
     */
    private boolean fouilleImpossible(Player p) {
        if (plugin.monnaie() == null) {
            p.sendActionBar(Component.text("La fouille n'est pas disponible ici.", NamedTextColor.GRAY));
            return true;
        }
        if (!plugin.monnaie().canal().modde(p)) {
            p.sendActionBar(Component.text("La fouille demande le mod Reborn à jour.", NamedTextColor.GRAY));
            return true;
        }
        return false;
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
        // comme une lenteur III, sans potion infinie — étape 5 : ligne mod.tient.GORGE de l'applicateur unique
        if (!com.reborn.shinobicore.etat.Applicateur.actif()) com.reborn.shinobicore.util.Effets.lenteur(p, LENTEUR_GORGE, 3);
        else com.reborn.shinobicore.etat.Applicateur.reappliquer(plugin, p);
        play(p, "attraper_gorge");
        AutoMe.broadcast(plugin, p, "attrape " + tName + " à la gorge.");
        KoJournal.log(plugin, p, "Prise à la gorge", KoJournal.who(plugin, t));
        p.sendActionBar(Component.text("Tu le tiens à la gorge · accroupis-toi pour lâcher.", NamedTextColor.GRAY));
    }

    /**
     * Exécuter : en douleur → mise hors de combat (sans RPK) ; KO + RPK accordé → mort RP. {@code id} = une
     * exécution possédée, ou {@link #SOBRE}.
     */
    private void execute(Player p, Player t, String tName, String id) {
        boolean lethal = plugin.ko().isKo(t.getUniqueId());
        if (lethal && plugin.killRequests().validatedFor(p.getUniqueId(), t.getUniqueId()) == null) {
            p.sendMessage(Component.text("Il faut un RPK validé par le staff pour exécuter quelqu'un à terre (mort RP).",
                    NamedTextColor.RED));
            return;
        }
        if (!lethal && !pain(t.getUniqueId())) {
            p.sendMessage(Component.text("Il tient encore debout.", NamedTextColor.GRAY));
            return;
        }
        if (plugin.porter().isCarrying(p.getUniqueId())) {
            p.sendActionBar(Component.text("Dépose d'abord la personne que tu portes.", NamedTextColor.GRAY));
            return;
        }
        Executions.Def d = id == null || id.isBlank() || id.equals(SOBRE) ? sobre() : executions.get(id);
        if (d == null || !(d.id().equals(SOBRE) || executions.owns(p, d))) {
            p.sendMessage(Component.text("Exécution inconnue ou non possédée.", NamedTextColor.RED));
            return;
        }
        for (Run r : running.values()) {
            if (r.victim().equals(t.getUniqueId())) {
                p.sendActionBar(Component.text("Quelqu'un s'en charge déjà.", NamedTextColor.GRAY));
                return;
            }
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
        running.put(p.getUniqueId(), new Run(t.getUniqueId(), d, at, ticks, lethal));
        // figé le temps de l'exécution — étape 5 : ligne mod.tient.EXECUTION de l'applicateur unique
        if (!com.reborn.shinobicore.etat.Applicateur.actif()) com.reborn.shinobicore.util.Effets.lenteur(p, LENTEUR_EXECUTION, 7);
        else com.reborn.shinobicore.etat.Applicateur.reappliquer(plugin, p);
        play(p, d.killerEmote());
        play(t, d.victimEmote());
        AutoMe.broadcast(plugin, p, (lethal ? (d.id().equals(SOBRE) ? "achève " : "exécute ")
                : "met " ) + tName + (lethal ? "." : " hors de combat."));
        KoJournal.log(plugin, p, "Exécution (début)", KoJournal.who(plugin, t) + " · "
                + (lethal ? "mort RP" : "mise hors de combat (douleur)") + " · " + d.name());
        if (plugin.koHud() == null || !plugin.koHud().modded(t)) {
            t.sendActionBar(Component.text(lethal ? "On t'exécute…" : "On te met hors de combat…", NamedTextColor.RED));
        }
    }

    private void finish(Player k) {
        Run run = running.remove(k.getUniqueId());
        if (run == null) return;
        com.reborn.shinobicore.util.Effets.retirer(k, Attribute.MOVEMENT_SPEED, LENTEUR_EXECUTION);
        if (plugin.emotes() != null) plugin.emotes().stop(k);
        release(run.victim());
    }

    private void abort(Player k, String why) {
        Run run = running.remove(k.getUniqueId());
        if (run == null) return;
        com.reborn.shinobicore.util.Effets.retirer(k, Attribute.MOVEMENT_SPEED, LENTEUR_EXECUTION);
        Player v = Bukkit.getPlayer(run.victim());
        if (plugin.emotes() != null) {
            plugin.emotes().stop(k);
            if (v != null) plugin.emotes().stop(v);
        }
        release(run.victim());
        if (!why.isEmpty()) k.sendActionBar(Component.text(why, NamedTextColor.GRAY));
        KoJournal.log(plugin, k, "Exécution interrompue", (v != null ? KoJournal.who(plugin, v) : run.victim().toString())
                + (why.isEmpty() ? "" : " · " + why));
    }

    /** L'acteur n'a plus la force (douleur) : exécution annulée, prise à la gorge lâchée. */
    public void onActorDisabled(Player p, String why) {
        if (p == null) return;
        if (running.containsKey(p.getUniqueId())) abort(p, why);
        for (Map.Entry<UUID, UUID> e : held.entrySet()) if (e.getValue().equals(p.getUniqueId())) release(e.getKey());
    }

    /** Staff : arrête toute exécution où ce joueur est exécuteur ou victime. */
    public boolean abortInvolving(UUID id) {
        boolean any = false;
        for (Map.Entry<UUID, Run> e : new ArrayList<>(running.entrySet())) {
            if (!e.getKey().equals(id) && !e.getValue().victim().equals(id)) continue;
            Player k = Bukkit.getPlayer(e.getKey());
            if (k != null) abort(k, "Le staff a interrompu la scène.");
            else running.remove(e.getKey());
            any = true;
        }
        return any;
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
        if (holder != null) com.reborn.shinobicore.util.Effets.retirer(holder, Attribute.MOVEMENT_SPEED, LENTEUR_GORGE);
        KoState st = plugin.ko().getKo(victim);
        Player v = Bukkit.getPlayer(victim);
        // tenu 0,3 bloc au-dessus du sol : on le repose au sol avant de l'épingler, sinon il reste en l'air
        Location spot = v != null ? PorterManager.dropSpot(v.getLocation(), 0) : null;
        if (v != null) {
            v.teleport(spot);
            v.setFallDistance(0f);
            if (st == null && v.getPose() != Pose.STANDING) v.setPose(Pose.STANDING, false);
        }
        if (st != null) {
            st.setCarrierPlayerId(null);
            if (spot != null) st.setLockLocation(spot.clone());
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent ev) {
        if (!ev.isSneaking()) return;
        UUID me = ev.getPlayer().getUniqueId();
        if (running.containsKey(me)) { abort(ev.getPlayer(), "Tu renonces."); return; }
        for (Map.Entry<UUID, UUID> e : held.entrySet()) if (e.getValue().equals(me)) release(e.getKey());
    }

    /** Un exécuteur frappé est interrompu. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKillerHit(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof Player k && running.containsKey(k.getUniqueId())) {
            abort(k, "Interrompu : tu as été frappé.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        UUID me = ev.getPlayer().getUniqueId();
        lastSent.remove(me);
        if (running.containsKey(me)) abort(ev.getPlayer(), "");
        for (Map.Entry<UUID, Run> e : new ArrayList<>(running.entrySet())) {
            if (!e.getValue().victim().equals(me)) continue;
            Player k = Bukkit.getPlayer(e.getKey());
            if (k != null) abort(k, "La cible est partie.");
        }
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
