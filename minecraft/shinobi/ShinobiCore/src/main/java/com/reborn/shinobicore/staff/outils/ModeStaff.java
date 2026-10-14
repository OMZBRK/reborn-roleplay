package com.reborn.shinobicore.staff.outils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.ko.KoState;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import com.reborn.shinobicore.vanish.VanishManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Mode staff</b> ({@code /modestaff}, état « Mode staff » du Game Plan §1.5) : invisible aux joueurs et au staff hors
 * mode staff (les staffs en mode staff se voient entre eux), vol, invulnérable, et <b>aucune mécanique RP</b> (décision
 * du 14/10 : « le mode staff empêche toute mécanique RP d'être faite — sauf animateur / MJ, mais c'est différent » ;
 * l'animateur a son propre mode, {@link ModeAnimation}).
 *
 * <p>Bloqué pour le staff en mode staff : frapper et être frappé, techniques ({@code KoService#techniqueDenial} /
 * {@code isOutOfPlay}, lus par ShinobiAbilities et ShinobiCombat), menu d'interaction R et toutes les commandes RP
 * ({@code /corps} : porter, fouiller, exécuter, gorge, RPK ; {@code /yeux}, {@code /bandage}, {@code /rencontrer},
 * {@code /me}, {@code /duel}, {@code /soigner}, {@code /paume}, postures, emotes sur soi…), ramassage (touche F et
 * vanilla), lâcher, objets de la sacoche (utiliser, jeter, détruire), manger / pilules, interactions de blocs et de
 * meubles (bibliothèques, coffres, portes, chaises), monture, tchat RP (ses messages vont au chat staff), radio.
 * <b>Réciproquement</b>, personne ne peut le cibler : coups, regard des commandes RP (rencontrer, ausculter, soigner,
 * amitié, paume), commandes RP qui le nomment, entités. Les outils staff (Poste de garde, commandes staff, voirsac,
 * choix des lits de l'Infirmerie) restent permis.
 *
 * <p>Chaque seconde, les <b>plaques staff</b> des joueurs proches partent sur {@code reborn:plaques}
 * (PLUGINS_REFERENCE §4 bis.5). Le mode tombe à la déconnexion. Entrée et sortie journalisées.
 */
public final class ModeStaff implements Listener {

    public static final String CANAL_PLAQUES = "reborn:plaques";
    private static final double RAYON = 64.0;

    /** Commandes RP (nom de base et alias, sans « / » ni espace de noms) refusées en mode staff. */
    static final Set<String> COMMANDES_RP = Set.of(
            "me", "action", "act", "rencontrer", "meet", "introduce", "amitier", "friend", "amis",
            "osculter", "ausculter", "auscult", "examine", "soigner", "heal", "treat", "paume", "shosen", "soin",
            "aide", "help-rp", "meditation", "meditate", "med", "corps", "duel", "defi", "assoir", "asseoir", "sit",
            "allonger", "coucher", "lay", "ramper", "crawl", "bandage", "yeux", "trade", "echange",
            "playemote", "emote", "pe", "testfeuille", "feuille", "leaftest", "hopital", "hospital", "radio",
            "lookdown");
    /** Sous-commandes staff permises même si la commande est RP. */
    private static final Map<String, Set<String>> EXCEPTIONS = Map.of(
            "testfeuille", Set.of("give"), "feuille", Set.of("give"), "leaftest", Set.of("give"),
            "hopital", Set.of("set", "suppr", "liste"), "hospital", Set.of("set", "suppr", "liste"),
            "radio", Set.of("ecouter", "stop", "couper", "retirer", "info", "donner", "reparer"));

    private record Avant(boolean vol, boolean enVol, boolean invulnerable, boolean collision, boolean vanish) { }

    private final ShinobiCore plugin;
    private final Map<UUID, Avant> actifs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dernierAvis = new ConcurrentHashMap<>();
    private BukkitTask tache;

    public ModeStaff(ShinobiCore plugin) {
        this.plugin = plugin;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CANAL_PLAQUES)) m.registerOutgoingPluginChannel(plugin, CANAL_PLAQUES);
    }

    public void demarrer() {
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::plaques, 20L, 20L);
    }

    public void arreter() {
        if (tache != null) tache.cancel();
        for (UUID id : List.copyOf(actifs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) sortir(p, false);
        }
    }

    public boolean actif(UUID id) { return id != null && actifs.containsKey(id); }

    public Set<UUID> actifs() { return Set.copyOf(actifs.keySet()); }

    /** Hors jeu : en mode staff (le mode animation, lui, laisse jouer). */
    public boolean horsJeu(UUID id) { return actif(id); }

    /** Raccourci statique pour les autres paquets de ShinobiCore. */
    public static boolean horsJeu(Player p) {
        ShinobiCore c = ShinobiCore.get();
        return p != null && c != null && c.outilsStaff() != null && c.outilsStaff().mode().horsJeu(p.getUniqueId());
    }

    /** Bascule ; renvoie le nouvel état. */
    public boolean basculer(Player p) {
        if (actif(p.getUniqueId())) { sortir(p, true); return false; }
        // Décision du 14/10 (ETATS_JOUEUR §11 n° 6) : pas de mode staff sur un personnage KO (ni en prison, quand elle
        // existera) — c'était une sortie du KO (vol, invisibilité, invulnérable). Idem mort en scène, porté, tenu.
        String refus = refusEntree(p);
        if (refus != null) {
            p.sendMessage(Component.text("Mode staff refusé : " + refus + " Sors d'abord de cet état (ou passe par un autre staff).",
                    NamedTextColor.RED));
            return false;
        }
        entrer(p);
        return true;
    }

    /** Pourquoi ce staff ne peut pas entrer en mode staff maintenant (null = permis). */
    public String refusEntree(Player p) {
        UUID id = p.getUniqueId();
        if (plugin.ko() != null && plugin.ko().isKo(id)) return "ton personnage est à terre (KO).";
        if (plugin.deathScene() != null && plugin.deathScene().isDead(id)) return "ton personnage est mort (scène en cours).";
        if (plugin.porter() != null && plugin.porter().carrierOf(id) != null) return "on te porte.";
        if (plugin.corps() != null && plugin.corps().isHeld(id)) return "on te tient.";
        return null;
    }

    private void entrer(Player p) {
        ModeAnimation anim = plugin.outilsStaff() != null ? plugin.outilsStaff().animation() : null;
        if (anim != null && anim.actif(p.getUniqueId())) anim.sortir(p, true);
        VanishManager v = plugin.vanish();
        boolean dejaVanish = v != null && v.isVanished(p.getUniqueId());
        actifs.put(p.getUniqueId(), new Avant(p.getAllowFlight(), p.isFlying(), p.isInvulnerable(), p.isCollidable(), dejaVanish));
        if (v != null) {
            v.setStaffMode(p.getUniqueId(), true);
            v.enable(p, VanishManager.Mode.HIDE_ALL, Set.of());
            v.applyForViewer(p);
        }
        // en selle ou porté : on descend (aucune monture en mode staff)
        if (p.getVehicle() != null) p.leaveVehicle();
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setInvulnerable(true);
        p.setCollidable(false);
        p.sendActionBar(Component.text("Mode staff : invisible, aucune mécanique RP.", NamedTextColor.GOLD));
        Journal.entree(Categorie.STAFF, "MODE_STAFF").acteur(p).detail("entrée").lieu(p.getLocation()).ecrire();
        if (plugin.staffPanel() != null) plugin.staffPanel().envoyerMode(p);
    }

    private void sortir(Player p, boolean journal) {
        Avant a = actifs.remove(p.getUniqueId());
        if (a == null) return;
        VanishManager v = plugin.vanish();
        if (v != null) {
            v.setStaffMode(p.getUniqueId(), false);
            if (a.vanish()) v.enable(p, VanishManager.Mode.HIDE_ALL, Set.of()); else v.disable(p);
            v.applyForViewer(p);
        }
        p.setFlying(a.enVol() && a.vol());
        p.setAllowFlight(a.vol() || p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR);
        p.setInvulnerable(a.invulnerable());
        p.setCollidable(a.collision());
        if (p.getListeningPluginChannels().contains(CANAL_PLAQUES)) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "plaques");
            o.addProperty("on", false);
            p.sendPluginMessage(plugin, CANAL_PLAQUES, o.toString().getBytes(StandardCharsets.UTF_8));
        }
        p.sendActionBar(Component.text("Mode staff terminé.", NamedTextColor.GRAY));
        if (journal) Journal.entree(Categorie.STAFF, "MODE_STAFF").acteur(p).detail("sortie").lieu(p.getLocation()).ecrire();
        if (plugin.staffPanel() != null && p.isOnline()) plugin.staffPanel().envoyerMode(p);
    }

    /* ================================================================ plaques */

    private void plaques() {
        if (actifs.isEmpty()) return;
        for (UUID id : actifs.keySet()) {
            Player staff = Bukkit.getPlayer(id);
            if (staff == null || !staff.getListeningPluginChannels().contains(CANAL_PLAQUES)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("t", "plaques");
            o.addProperty("on", true);
            JsonArray arr = new JsonArray();
            for (Player p : staff.getWorld().getPlayers()) {
                if (p.equals(staff) || p.getLocation().distanceSquared(staff.getLocation()) > RAYON * RAYON) continue;
                arr.add(plaque(p));
            }
            o.add("joueurs", arr);
            staff.sendPluginMessage(plugin, CANAL_PLAQUES, o.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private JsonObject plaque(Player p) {
        JsonObject j = new JsonObject();
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        j.addProperty("uuid", p.getUniqueId().toString());
        j.addProperty("pid", c != null && c.hasPlayerId() ? PlayerIdRegistry.format(c.playerId()) : "");
        j.addProperty("perso", c != null ? c.name() : "");
        j.addProperty("pseudo", p.getName());
        j.addProperty("etat", etat(p, c));
        j.addProperty("pv", Math.round(p.getHealth()));
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        j.addProperty("pvMax", Math.round(max != null ? max.getValue() : 20));
        j.addProperty("staff", StaffGrades.of(p) > StaffGrades.NONE);
        OutilsStaff o = plugin.outilsStaff();
        j.addProperty("gele", o != null && o.gels().gele(p.getUniqueId()));
        j.addProperty("modeStaff", actif(p.getUniqueId()));
        j.addProperty("modeAnim", o != null && o.animation().actif(p.getUniqueId()));
        return j;
    }

    /** État court (KO, ATA, duel, gelé, écran noir, animation) — sert aussi au Poste de garde. */
    public String etat(Player p, ShinobiCharacter c) {
        List<String> parts = new ArrayList<>();
        KoState ko = plugin.ko() != null ? plugin.ko().getKo(p.getUniqueId()) : null;
        if (ko != null) parts.add(ko.isDowned() ? "À terre" : "Inconscient");
        if (c != null && plugin.ata() != null && plugin.ata().view(c.id()) != null) parts.add("ATA");
        if (plugin.duels() != null && plugin.duels().inDuel(p.getUniqueId())) parts.add("Duel");
        OutilsStaff o = plugin.outilsStaff();
        if (o != null && o.gels().gele(p.getUniqueId())) parts.add("Gelé");
        if (o != null && o.effets().enNoir(p.getUniqueId())) parts.add("Écran noir");
        if (o != null && o.animation().actif(p.getUniqueId())) parts.add("Animation");
        if (c == null) parts.add("Sans personnage");
        return String.join(" · ", parts);
    }

    /* ================================================================ aucune mécanique RP */

    /** Avis discret (barre d'action, au plus toutes les 2 s). */
    private void avis(Player p, String m) {
        long now = System.currentTimeMillis();
        Long last = dernierAvis.get(p.getUniqueId());
        if (last != null && now - last < 2000) return;
        dernierAvis.put(p.getUniqueId(), now);
        p.sendActionBar(Component.text(m, NamedTextColor.GOLD));
    }

    private static Player auteur(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent ev) {
        Player d = auteur(ev.getDamager());
        if (d != null && horsJeu(d.getUniqueId())) { ev.setCancelled(true); avis(d, "Mode staff : tu ne peux pas frapper."); return; }
        if (ev.getEntity() instanceof Player v && horsJeu(v.getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof Player p && horsJeu(p.getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent ev) {
        if (ev.getEntity() instanceof Player p && horsJeu(p.getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent ev) {
        if (horsJeu(ev.getPlayer().getUniqueId())) { ev.setCancelled(true); avis(ev.getPlayer(), "Mode staff : rien à lâcher."); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent ev) {
        if (ev.getTarget() instanceof Player p && horsJeu(p.getUniqueId())) ev.setCancelled(true);
    }

    /** Blocs et objets en main : ni coffre, ni porte, ni bibliothèque, ni meuble, ni objet utilisé (clic gauche libre). */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent ev) {
        Player p = ev.getPlayer();
        if (!horsJeu(p.getUniqueId())) return;
        if (ev.getAction() == Action.LEFT_CLICK_BLOCK || ev.getAction() == Action.LEFT_CLICK_AIR) return;
        if (choixDeLits(p)) return;
        ev.setUseInteractedBlock(Event.Result.DENY);
        ev.setUseItemInHand(Event.Result.DENY);
        if (ev.getAction() == Action.RIGHT_CLICK_BLOCK) avis(p, "Mode staff : aucune interaction RP.");
    }

    /** Entités : ni joueur, ni monture, ni meuble ; et personne n'interagit avec un staff en mode staff. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent ev) {
        Player p = ev.getPlayer();
        if (horsJeu(p.getUniqueId()) && !choixDeLits(p)) {
            ev.setCancelled(true);
            avis(p, "Mode staff : aucune interaction RP.");
            return;
        }
        if (ev.getRightClicked() instanceof Player cible && horsJeu(cible.getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent ev) {
        if (horsJeu(ev.getPlayer().getUniqueId())) { ev.setCancelled(true); avis(ev.getPlayer(), "Mode staff : ni manger ni pilule."); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMount(EntityMountEvent ev) {
        if (ev.getEntity() instanceof Player p && horsJeu(p.getUniqueId())) { ev.setCancelled(true); avis(p, "Mode staff : pas de monture."); }
        else if (ev.getMount() instanceof Player m && horsJeu(m.getUniqueId())) ev.setCancelled(true);
    }

    /** Tchat RP : en mode staff, les messages partent au chat staff (le « # » du chat staff reste géré ailleurs). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent ev) {
        Player p = ev.getPlayer();
        if (!horsJeu(p.getUniqueId())) return;
        String text = PlainTextComponentSerializer.plainText().serialize(ev.message());
        if (text.startsWith("#")) return;
        ev.setCancelled(true);
        String m = text.trim();
        if (m.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.staffPanel() != null) plugin.staffPanel().staffChat(p, m);
            p.sendMessage(Component.text("[Mode staff] Envoyé au chat staff (pas de tchat RP en mode staff).", NamedTextColor.GRAY));
        });
    }

    /**
     * Commandes RP : refusées au staff en mode staff ; et refusées à tous quand elles nomment un staff en mode staff
     * (pseudo ou nom RP) — personne ne peut le cibler.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent ev) {
        String msg = ev.getMessage();
        if (msg.length() < 2) return;
        String[] t = msg.substring(1).trim().split("\\s+");
        String cmd = t[0].toLowerCase(Locale.ROOT);
        int ns = cmd.indexOf(':');
        if (ns >= 0) cmd = cmd.substring(ns + 1);
        if (!COMMANDES_RP.contains(cmd)) return;
        String sous = t.length > 1 ? t[1].toLowerCase(Locale.ROOT) : "";
        Set<String> exc = EXCEPTIONS.get(cmd);
        if (exc != null && exc.contains(sous)) return;
        Player p = ev.getPlayer();
        if (horsJeu(p.getUniqueId())) {
            // emote jouée sur quelqu'un d'autre par le staff (/playemote <emote> <joueur>) : outil staff, permis
            if ((cmd.equals("playemote") || cmd.equals("emote") || cmd.equals("pe")) && t.length >= 3) return;
            ev.setCancelled(true);
            p.sendMessage(Component.text("Mode staff : aucune mécanique RP (/" + cmd + "). Coupe le mode staff, ou passe en mode animation pour un event.",
                    NamedTextColor.GOLD));
            return;
        }
        if (actifs.isEmpty() || t.length < 2) return;
        String args = " " + String.join(" ", java.util.Arrays.copyOfRange(t, 1, t.length)).toLowerCase(Locale.ROOT) + " ";
        for (UUID id : actifs.keySet()) {
            Player s = Bukkit.getPlayer(id);
            if (s == null || s.equals(p)) continue;
            ShinobiCharacter c = plugin.characters().getActive(id);
            boolean nomme = args.contains(" " + s.getName().toLowerCase(Locale.ROOT) + " ")
                    || c != null && !c.name().isBlank() && args.contains(" " + c.name().toLowerCase(Locale.ROOT));
            if (nomme) {
                ev.setCancelled(true);
                p.sendMessage(Component.text("Personne de ce nom ici.", NamedTextColor.GRAY));
                return;
            }
        }
    }

    /** Le staff choisit-il des lits d'hôpital (outil de l'Infirmerie, qui passe par le clic droit) ? */
    private boolean choixDeLits(Player p) {
        return plugin.staffPanel() != null && plugin.staffPanel().infirmerie().choixDeLits(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent ev) {
        dernierAvis.remove(ev.getPlayer().getUniqueId());
        if (actif(ev.getPlayer().getUniqueId())) sortir(ev.getPlayer(), true);
    }
}
