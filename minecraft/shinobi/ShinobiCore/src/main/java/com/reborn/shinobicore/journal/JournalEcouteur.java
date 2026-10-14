package com.reborn.shinobicore.journal;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Branche les « trous » du journal sur les événements Bukkit / Reborn : connexions, personnages, dégâts, morts,
 * <b>toutes les commandes</b> (joueurs, staff, vanilla comprises, console, RCON), tchat, mode de jeu,
 * téléportations par commande, expulsions, coffres et inventaires d'autrui.
 *
 * <p>Réglages : section {@code journal} du config.yml (cf. PLUGINS_REFERENCE §5.1).
 */
public final class JournalEcouteur implements Listener {

    /** Commandes RP dont l'entrée va en {@code INTERACTION} plutôt qu'en {@code COMMANDE}. */
    private static final Set<String> INTERACTIONS = Set.of("corps", "soigner", "heal", "treat", "paume", "shosen",
            "soin", "rencontrer", "meet", "introduce", "amitier", "friend", "amis", "osculter", "ausculter", "auscult",
            "examine", "duel", "defi", "aide", "help-rp", "signaler", "report", "assoir", "sit", "allonger", "lay",
            "ramper", "crawl", "playemote", "emote", "pe", "stopemote", "se");
    /** Commandes de parole RP : déjà au tchat, en {@code CHAT}. */
    private static final Set<String> PAROLE = Set.of("me", "action", "act", "staffchat", "schat", "msg", "tell", "w",
            "r", "reply", "whisper");

    private final ShinobiCore plugin;
    private final Map<UUID, Long> connexion = new ConcurrentHashMap<>();

    public JournalEcouteur(ShinobiCore plugin) { this.plugin = plugin; }

    private ConfigurationSection cfg() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("journal");
        return s != null ? s : plugin.getConfig().createSection("journal");
    }

    private static boolean staff(org.bukkit.command.CommandSender s) {
        if (!(s instanceof Player p)) return true;
        return p.isOp() || StaffGrades.of(p) > StaffGrades.NONE || p.hasPermission("shinobicore.staff");
    }

    /* ================================================================ joueur */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        connexion.put(p.getUniqueId(), System.currentTimeMillis());
        Journal.entree(Categorie.JOUEUR, "CONNEXION").acteur(p).lieu(p.getLocation())
                .detail(p.hasPlayedBefore() ? "" : "première connexion").ecrire();
    }

    /** LOWEST : avant que le personnage actif ne soit déchargé. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        Long t0 = connexion.remove(p.getUniqueId());
        StringBuilder d = new StringBuilder();
        if (t0 != null) d.append("session ").append(duree(System.currentTimeMillis() - t0));
        if (plugin.ko() != null && plugin.ko().isKo(p.getUniqueId())) d.append(" · déconnecté en KO");
        if (plugin.duels() != null && plugin.duels().inDuel(p.getUniqueId())) d.append(" · déconnecté en duel");
        Journal.entree(Categorie.JOUEUR, "DECONNEXION").acteur(p).lieu(p.getLocation()).detail(d.toString()).ecrire();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent ev) {
        String raison = PlainTextComponentSerializer.plainText().serialize(ev.reason());
        Journal.entree(Categorie.JOUEUR, "EXPULSE").acteur(ev.getPlayer()).detail(raison).ecrire();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitch(CharacterSwitchEvent ev) {
        ShinobiCharacter prev = ev.previous(), next = ev.next();
        if (prev != null && prev.id().equals(next.id())) return;
        Journal.entree(Categorie.JOUEUR, "CHOIX_PERSO").acteur(ev.player())
                .ciblePersonnage(next.ownerId(), next.id())
                .detail((prev != null ? com.reborn.shinobicore.character.PlayerIds.prefix(prev) + prev.name() + " → " : "")
                        + com.reborn.shinobicore.character.PlayerIds.prefix(next) + next.name())
                .ecrire();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent ev) {
        Player p = ev.getPlayer();
        Journal.entree(staff(p) ? Categorie.STAFF : Categorie.JOUEUR, "MODE_DE_JEU").acteur(p)
                .detail(p.getGameMode().name().toLowerCase(Locale.ROOT) + " → "
                        + ev.getNewGameMode().name().toLowerCase(Locale.ROOT)
                        + " (" + ev.getCause().name().toLowerCase(Locale.ROOT) + ")")
                .lieu(p.getLocation()).ecrire();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent ev) {
        if (ev.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND
                && ev.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) return;
        Player p = ev.getPlayer();
        Journal.entree(staff(p) ? Categorie.STAFF : Categorie.JOUEUR, "TELEPORTATION").acteur(p)
                .detail("depuis " + lieu(ev.getFrom()) + " (" + ev.getCause().name().toLowerCase(Locale.ROOT) + ")")
                .lieu(ev.getTo()).ecrire();
    }

    /* ================================================================ combat */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent ev) {
        if (!(ev.getEntity() instanceof Player victim)) return;
        Player attacker = attaquant(ev.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (!cfg().getBoolean("combat.degats", true)) return;
        double d = ev.getFinalDamage();
        if (d < cfg().getDouble("combat.seuil-pv", 0.0)) return;
        StringBuilder det = new StringBuilder(String.format(Locale.FRANCE, "%.1f PV", d));
        det.append(" · PV restants ").append(String.format(Locale.FRANCE, "%.1f", Math.max(0, victim.getHealth() - d)));
        ItemStack arme = attacker.getInventory().getItemInMainHand();
        if (ev.getDamager() instanceof Projectile pr) det.append(" · projectile ").append(pr.getType().name().toLowerCase(Locale.ROOT));
        else if (!arme.getType().isAir()) det.append(" · ").append(nomObjet(arme));
        det.append(" · ").append(ev.getCause().name().toLowerCase(Locale.ROOT));
        if (plugin.duels() != null && plugin.duels().together(victim.getUniqueId(), attacker.getUniqueId())) det.append(" · duel");
        else if (plugin.trainingZones() != null && plugin.trainingZones().isTraining(victim)) det.append(" · entraînement");
        Journal.entree(Categorie.COMBAT, "DEGATS").acteur(attacker).cible(victim).detail(det.toString())
                .lieu(victim.getLocation()).ecrire();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent ev) {
        Player v = ev.getEntity();
        Player k = v.getKiller();
        var e = Journal.entree(Categorie.COMBAT, "MORT");
        if (k != null) e.acteur(k).cible(v); else e.acteur(v);
        String cause = v.getLastDamageCause() != null ? v.getLastDamageCause().getCause().name().toLowerCase(Locale.ROOT) : "?";
        e.detail("mort vanilla · " + cause + (ev.getKeepInventory() ? "" : " · inventaire lâché")).lieu(v.getLocation()).ecrire();
    }

    // KO, agonie, réveil, hôpital, portage, exécution : ko/KoJournal (catégorie KO) ; RPK et mort RP :
    // KillRequestManager (catégorie KO). Pas d'écouteur ici pour ne pas doubler les entrées.

    /* ================================================================ commandes */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent ev) {
        Player p = ev.getPlayer();
        String msg = ev.getMessage();
        if (ignoree(msg)) return;
        String racine = racine(msg);
        boolean st = staff(p);
        if (!st && !cfg().getBoolean("commandes.tous", true)) return;
        Categorie cat = st ? Categorie.STAFF
                : INTERACTIONS.contains(racine) ? Categorie.INTERACTION
                : PAROLE.contains(racine) ? Categorie.CHAT
                : Categorie.COMMANDE;
        var e = Journal.entree(cat, "COMMANDE").acteur(p)
                .detail(msg + (ev.isCancelled() ? " (refusée)" : "")).lieu(p.getLocation());
        Player cible = cibleDans(msg, p);
        if (cible != null) e.cible(cible);
        e.ecrire();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsole(ServerCommandEvent ev) {
        if (ignoree("/" + ev.getCommand())) return;
        var e = Journal.entree(Categorie.STAFF, "COMMANDE").acteur(ev.getSender())
                .detail("/" + ev.getCommand() + (ev.isCancelled() ? " (refusée)" : ""));
        Player cible = cibleDans("/" + ev.getCommand(), null);
        if (cible != null) e.cible(cible);
        e.ecrire();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRcon(RemoteServerCommandEvent ev) {
        Journal.entree(Categorie.STAFF, "COMMANDE").acteur(ev.getSender()).detail("/" + ev.getCommand() + " (RCON)").ecrire();
    }

    private boolean ignoree(String msg) {
        String bas = msg.toLowerCase(Locale.ROOT);
        List<String> ign = cfg().getStringList("commandes.ignorer");
        for (String i : ign) if (!i.isBlank() && bas.startsWith(i.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    static String racine(String msg) {
        String s = msg.startsWith("/") ? msg.substring(1) : msg;
        int sp = s.indexOf(' ');
        String r = (sp < 0 ? s : s.substring(0, sp)).toLowerCase(Locale.ROOT);
        int colon = r.indexOf(':');
        return colon >= 0 ? r.substring(colon + 1) : r;
    }

    /** Premier argument qui est un joueur en ligne (autre que l'émetteur) : la cible la plus probable. */
    private static Player cibleDans(String msg, Player soi) {
        String[] a = msg.split(" ");
        for (int i = 1; i < a.length && i < 6; i++) {
            if (a[i].length() < 3 || a[i].length() > 16) continue;
            Player t = Bukkit.getPlayerExact(a[i]);
            if (t != null && !t.equals(soi)) return t;
        }
        return null;
    }

    /* ================================================================ tchat */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent ev) {
        if (!cfg().getBoolean("chat", true)) return;
        String text = PlainTextComponentSerializer.plainText().serialize(ev.message());
        Journal.entree(Categorie.CHAT, "MESSAGE").acteur(ev.getPlayer()).detail(text).ecrire();
    }

    /* ================================================================ coffres, inventaires */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent ev) {
        if (!(ev.getPlayer() instanceof Player p)) return;
        InventoryHolder h = ev.getInventory().getHolder(false);
        if (h instanceof Player other && !other.equals(p)) {
            Journal.entree(staff(p) ? Categorie.STAFF : Categorie.INTERACTION, "INVENTAIRE_AUTRUI").acteur(p).cible(other)
                    .lieu(p.getLocation()).ecrire();
        } else if (h instanceof BlockInventoryHolder b && cfg().getBoolean("coffres", true)) {
            Journal.entree(Categorie.INTERACTION, "COFFRE").acteur(p)
                    .cibleTexte(b.getBlock().getType().name().toLowerCase(Locale.ROOT))
                    .lieu(b.getBlock().getLocation()).ecrire();
        }
    }

    /* ================================================================ utilitaires */

    private static Player attaquant(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    static String nomObjet(ItemStack s) {
        if (s == null) return "";
        var meta = s.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        return s.getType().name().toLowerCase(Locale.ROOT);
    }

    static String lieu(Location l) {
        if (l == null || l.getWorld() == null) return "";
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    static String duree(long ms) {
        long m = ms / 60_000L;
        return m < 60 ? m + " min" : (m / 60) + " h " + (m % 60) + " min";
    }
}
