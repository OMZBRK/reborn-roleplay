package com.reborn.shinobicore.banque;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.entreprise.Entreprise;
import com.reborn.shinobicore.entreprise.EntrepriseService;
import com.reborn.shinobicore.monnaie.MonnaieConfig;
import com.reborn.shinobicore.monnaie.MonnaieModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Les guichets (MONNAIE_ET_BANQUE.md §4.1, §5 ; décision 6 : PNJ / bloc ET guichetier joueur coexistent) : un bloc,
 * un meuble Nexo (son entité) ou un PNJ marqué par le staff ({@code /banque guichet ajouter <village> ["libellé"]
 * [entreprise]}). Clic droit = ouvrir son compte (libre-service). Un <b>guichetier joueur</b> = un membre d'une
 * entreprise de type {@code BANQUE} (celle du guichet si elle est indiquée) à {@code banque.guichet.portee-guichetier}
 * blocs : il est nommé à l'écran (« Au guichet : Hana Kurenai »), il peut ouvrir le compte d'un client (menu R) et il
 * <b>scelle</b> les échanges garantis ; il ne voit jamais le solde.
 */
public final class Guichets implements Listener {

    /** Un guichet ouvert pour un joueur : où, quel village, qui tient le guichet. */
    public record Ouverture(String id, String village, String libelle, String banque, String guichetier, UUID guichetierId,
                            Location at) {}

    private final ShinobiCore plugin;
    private final MonnaieModule module;
    private final BanqueStore store;

    public Guichets(ShinobiCore plugin, MonnaieModule module, BanqueStore store) {
        this.plugin = plugin;
        this.module = module;
        this.store = store;
    }

    public List<BanqueStore.Guichet> liste() { return new ArrayList<>(store.guichets.values()); }

    public BanqueStore.Guichet duBloc(Block b) {
        if (b == null) return null;
        for (BanqueStore.Guichet g : store.guichets.values()) {
            if ("bloc".equals(g.type()) && g.monde().equals(b.getWorld().getName()) && g.x() == b.getX() && g.y() == b.getY()
                    && g.z() == b.getZ()) return g;
        }
        return null;
    }

    public BanqueStore.Guichet deEntite(Entity e) {
        if (e == null) return null;
        for (BanqueStore.Guichet g : store.guichets.values()) {
            if (e.getUniqueId().equals(g.entite())) return g;
            // meuble Nexo : l'entité d'interaction est à un bloc près du point enregistré
            if ("entite".equals(g.type()) && g.entite() == null && g.monde().equals(e.getWorld().getName())
                    && Math.abs(e.getLocation().getBlockX() - g.x()) <= 1 && Math.abs(e.getLocation().getBlockY() - g.y()) <= 1
                    && Math.abs(e.getLocation().getBlockZ() - g.z()) <= 1) return g;
        }
        return null;
    }

    public BanqueStore.Guichet ajouter(Block b, Entity e, String village, String libelle, String entreprise) {
        String v = MonnaieConfig.village(village);
        Location l = e != null ? e.getLocation() : b.getLocation();
        String id = v + "_" + (store.guichets.size() + 1);
        while (store.guichets.containsKey(id)) id = id + "b";
        BanqueStore.Guichet g = new BanqueStore.Guichet(id, e != null ? "entite" : "bloc", l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), e != null ? e.getUniqueId() : null, v,
                libelle == null || libelle.isBlank() ? "Banque de " + titre(v) : libelle,
                entreprise == null ? "" : entreprise);
        store.guichets.put(id, g);
        store.marquer();
        return g;
    }

    public BanqueStore.Guichet retirer(Block b, Entity e) {
        BanqueStore.Guichet g = e != null ? deEntite(e) : duBloc(b);
        if (g != null) { store.guichets.remove(g.id()); store.marquer(); }
        return g;
    }

    static String titre(String v) {
        if (v == null || v.isEmpty()) return "?";
        return Character.toUpperCase(v.charAt(0)) + v.substring(1).replace('_', ' ');
    }

    public static Location centre(BanqueStore.Guichet g) {
        World w = Bukkit.getWorld(g.monde());
        return w == null ? null : new Location(w, g.x() + 0.5, g.y() + 0.5, g.z() + 0.5);
    }

    /** Le guichet le plus proche à {@code portee} blocs, ou null. */
    public BanqueStore.Guichet proche(Location l, double portee) {
        BanqueStore.Guichet best = null;
        double d = portee * portee;
        for (BanqueStore.Guichet g : store.guichets.values()) {
            Location c = centre(g);
            if (c == null || !c.getWorld().equals(l.getWorld())) continue;
            double x = c.distanceSquared(l);
            if (x <= d) { d = x; best = g; }
        }
        return best;
    }

    /** Le guichetier joueur en service près du guichet (membre d'une entreprise BANQUE), sauf {@code sauf}. */
    public Player guichetier(BanqueStore.Guichet g, UUID sauf) {
        EntrepriseService es = plugin.nourriture() == null ? null : plugin.nourriture().entreprises();
        Location c = centre(g);
        if (es == null || c == null) return null;
        double r = MonnaieConfig.get().porteeGuichetier;
        for (Player o : c.getWorld().getPlayers()) {
            if (o.getUniqueId().equals(sauf) || o.getLocation().distanceSquared(c) > r * r) continue;
            if (com.reborn.shinobicore.staff.outils.ModeStaff.horsJeu(o)) continue;
            ShinobiCharacter oc = plugin.characters().getActive(o.getUniqueId());
            if (oc == null) continue;
            for (Entreprise en : es.de(oc.id())) {
                if (en.type() != Entreprise.Type.BANQUE) continue;
                if (g.entreprise() == null || g.entreprise().isEmpty() || g.entreprise().equalsIgnoreCase(en.id())) return o;
            }
        }
        return null;
    }

    /** Est-il guichetier en service (BANQUE, près d'un guichet) ? Renvoie le guichet, ou null. */
    public BanqueStore.Guichet enService(Player p) {
        BanqueStore.Guichet g = proche(p.getLocation(), MonnaieConfig.get().porteeGuichetier);
        if (g == null) return null;
        Player gu = guichetier(g, null);
        return gu != null && gu.getUniqueId().equals(p.getUniqueId()) ? g : null;
    }

    public Ouverture ouverture(BanqueStore.Guichet g, Player client) {
        Player gu = guichetier(g, client.getUniqueId());
        ShinobiCharacter gc = gu == null ? null : plugin.characters().getActive(gu.getUniqueId());
        String banque = g.libelle();
        if (g.entreprise() != null && !g.entreprise().isEmpty() && plugin.nourriture() != null) {
            Entreprise en = plugin.nourriture().entreprises().trouver(g.entreprise());
            if (en != null) banque = en.nom();
        }
        return new Ouverture(g.id(), g.village(), g.libelle(), banque, gc == null ? null : gc.name(),
                gu == null ? null : gu.getUniqueId(), centre(g));
    }

    /* ================================================================ clic droit */

    @EventHandler(priority = EventPriority.HIGH)
    public void onBloc(PlayerInteractEvent ev) {
        if (ev.getAction() != Action.RIGHT_CLICK_BLOCK || ev.getHand() != EquipmentSlot.HAND) return;
        BanqueStore.Guichet g = duBloc(ev.getClickedBlock());
        if (g == null) return;
        ev.setCancelled(true);
        module.guichetCanal().ouvrir(ev.getPlayer(), g, 0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntite(PlayerInteractEntityEvent ev) {
        if (ev.getHand() != EquipmentSlot.HAND) return;
        BanqueStore.Guichet g = deEntite(ev.getRightClicked());
        if (g == null) return;
        ev.setCancelled(true);
        module.guichetCanal().ouvrir(ev.getPlayer(), g, 0);
    }

    public static String nomVillage(String v) { return titre(v == null ? "" : v.toLowerCase(Locale.ROOT)); }
}
