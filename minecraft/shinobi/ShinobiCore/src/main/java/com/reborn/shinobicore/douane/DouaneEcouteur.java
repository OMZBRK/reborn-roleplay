package com.reborn.shinobicore.douane;

import com.reborn.shinobicore.staff.outils.ModeStaff;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * Écouteurs de la douane (CANDIDATURE_ET_DOUANE.md §3.3, §3.4, §3.10) :
 * <ul>
 *   <li>pré-login : {@code GET /v1/game/acces} (délai court) ; JOIN : figé jusqu'à l'aiguillage du tick suivant ;</li>
 *   <li>confinement : un candidat qui sort de sa zone revient à son point ; aucune téléportation qui ne vienne pas de
 *       la douane ; joueur figé (attente d'une preuve, hall fermé) immobile ;</li>
 *   <li>commandes : un candidat n'a que {@code /douane} ; rien pour un joueur figé ; pas de commande RP en mode
 *       examinateur ;</li>
 *   <li>tchat : celui de la douane reste dans la douane (et au staff en mode staff), par salle (hall, chaque bureau),
 *       celui du monde n'y entre pas ; coupé hall fermé ;</li>
 *   <li>aucune interaction, aucun dégât, faim figée pour les candidats, les joueurs figés, les examinateurs en mode et
 *       tout autre occupant du monde de la douane hors mode staff.</li>
 * </ul>
 */
final class DouaneEcouteur implements Listener {

    private final DouaneManager douane;

    DouaneEcouteur(DouaneManager douane) {
        this.douane = douane;
    }

    /**
     * Protégé (aucune mécanique RP) : joueur figé, et cadre douane — candidat, examinateur en mode, tout autre occupant
     * du monde de la douane hors mode staff et hors mode construction.
     */
    private boolean protege(UUID id) {
        return douane.fige(id) || douane.cadreDouane(id);
    }

    /* ================================================================ connexion */

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        douane.preLogin(e.getUniqueId(), e.getName(),
                msg -> e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, msg));
    }

    /**
     * Avant tout le reste : figé jusqu'à l'aiguillage du tick suivant. Sans ça, un client modifié pourrait écrire au
     * tchat du monde ou lancer des commandes entre le JOIN et l'aiguillage.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        douane.arrive(e.getPlayer().getUniqueId());
    }

    /** Après tout le reste : le nouvel arrivant et les joueurs en attente neutre ne se voient pas. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinVisibilite(PlayerJoinEvent e) {
        douane.cacherArrivant(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        douane.quitter(e.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (douane.mode().actif(p.getUniqueId()) && !douane.dansMonde(p)) douane.mode().sortir(p, true, false);
        // Sortie du monde de la douane : « fin » au mod (plaque « Examinateur » et pseudos à la place d'« Inconnu »).
        if (douane.monde() != null && douane.monde().equals(e.getFrom())) douane.canal().sortie(p);
        douane.marquerSale();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (douane.candidat(e.getPlayer().getUniqueId()) && douane.pointHall() != null) e.setRespawnLocation(douane.pointHall());
    }

    /* ================================================================ confinement */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!douane.restreint(id)) return;
        Location de = e.getFrom(), vers = e.getTo();
        boolean pas = de.getX() != vers.getX() || de.getY() != vers.getY() || de.getZ() != vers.getZ();
        if (!pas) return;
        if (douane.immobile(id)) {
            Location reste = de.clone();
            reste.setYaw(vers.getYaw());
            reste.setPitch(vers.getPitch());
            e.setTo(reste);
            return;
        }
        Location retour = douane.confiner(p, vers);
        if (retour != null) e.setTo(retour);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (douane.restreint(id) && !douane.teleportInterne(id)) e.setCancelled(true);
    }

    /* ================================================================ commandes */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        String base = base(e.getMessage());
        if (douane.fige(id)) {
            e.setCancelled(true);
            return;
        }
        if (douane.candidat(id)) {
            if (!base.equals("douane")) {
                e.setCancelled(true);
                p.sendMessage(Component.text("Commande indisponible à la douane (seule /douane répond).", NamedTextColor.GRAY));
            }
            return;
        }
        if (douane.mode().actif(id) && ModeStaff.COMMANDES_RP.contains(base)) {
            e.setCancelled(true);
            p.sendMessage(Component.text("Mode examinateur : aucune commande RP.", NamedTextColor.RED));
        }
    }

    /** Nom de base d'une commande tapée, sans « / », sans espace de noms, en minuscules. */
    static String base(String message) {
        String m = message.startsWith("/") ? message.substring(1) : message;
        int sp = m.indexOf(' ');
        if (sp >= 0) m = m.substring(0, sp);
        int dp = m.indexOf(':');
        if (dp >= 0) m = m.substring(dp + 1);
        return m.toLowerCase(Locale.ROOT);
    }

    /* ================================================================ tchat */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!douane.actif()) return;
        Player s = e.getPlayer();
        UUID id = s.getUniqueId();
        if (douane.tchatCoupe(id)) {
            e.setCancelled(true);
            s.sendMessage(Component.text(douane.enAttenteDePreuve(id) || douane.fige(id)
                    ? "Patiente un instant." : "Le hall est fermé : le tchat ouvre avec l'arrivée d'un examinateur.",
                    NamedTextColor.GRAY));
            return;
        }
        boolean dedans = douane.candidat(id) || douane.mode().actif(id) || douane.dansMonde(s);
        if (dedans) {
            // Tchat de la douane, par salle (§3.5, §3.7) : un bureau n'est lu que de son candidat et de son examinateur,
            // le hall que des occupants qui ne passent pas l'oral ; le staff en mode staff surveille tout. Rien ne
            // fuit du bureau vers le hall, ni du hall vers le bureau (réponses soufflées).
            String salle = douane.salle(id);
            e.viewers().removeIf(a -> {
                if (!(a instanceof Player v)) return false;
                if (ModeStaff.horsJeu(v)) return false;
                UUID vid = v.getUniqueId();
                if (!douane.dansMonde(v) && !douane.candidat(vid)) return true;
                return !java.util.Objects.equals(salle, douane.salle(vid));
            });
        } else {
            // Tchat du monde : jamais lu à la douane (candidats, examinateurs en mode, staff venu par /douane aller hors
            // mode staff), ni par un joueur en attente. Le staff en mode staff continue de tout lire.
            e.viewers().removeIf(a -> a instanceof Player v && (douane.restreint(v.getUniqueId())
                    || douane.mode().actif(v.getUniqueId()) || (douane.dansMonde(v) && !ModeStaff.horsJeu(v))));
        }
    }

    /* ================================================================ aucune interaction */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && protege(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        Player auteur = d instanceof Player p ? p : d instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
        if (auteur != null && protege(auteur.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFaim(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && protege(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (!protege(e.getPlayer().getUniqueId())) return;
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (protege(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (protege(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (protege(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && protege(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (protege(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (protege(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && douane.restreint(p.getUniqueId())
                && e.getInventory().getType() != InventoryType.CRAFTING && e.getInventory().getType() != InventoryType.PLAYER) {
            e.setCancelled(true);
        }
    }
}
