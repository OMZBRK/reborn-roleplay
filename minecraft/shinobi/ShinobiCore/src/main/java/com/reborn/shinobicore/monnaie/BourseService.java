package com.reborn.shinobicore.monnaie;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.inventory.Notifs;
import com.reborn.shinobicore.journal.Journal;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La bourse portée (MONNAIE_ET_BANQUE.md §3) : trouver, créditer, débiter (fil principal, atomique), fusionner (une
 * seule bourse sur soi), poser au sol (« Combien veux-tu poser ? », 30 min), ramasser (touche F, via
 * {@code Ramassage}), ranger dans un coffre posé (« Combien veux-tu ranger ? ») et reprendre (un clic : tout prendre),
 * payer l'échoppe. Chaque mouvement écrit une ligne {@code ECONOMIE} au journal universel.
 */
public final class BourseService implements Listener {

    /** Clé d'ObjetsAuSol (disparition des objets) : 0 = jamais ; la bourse gère son propre délai. */
    private static final NamespacedKey SOL_FIN = new NamespacedKey("reborn", "sol_fin");

    /** Un ensemble de cases où peut se trouver une bourse (barre H0..H8 puis sac B0..Bn). */
    public interface Emplacements {
        List<String> refs();
        ItemStack lire(String ref);
        void ecrire(String ref, ItemStack s);
    }

    private final ShinobiCore plugin;
    private final MonnaieModule module;
    private final Map<UUID, Long> dernierEnvoye = new ConcurrentHashMap<>();
    /** Coffre visé par une demande « Combien veux-tu ranger ? ». */
    private final Map<UUID, Inventory> rangement = new ConcurrentHashMap<>();

    BourseService(ShinobiCore plugin, MonnaieModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::balayerSol, 200L, 400L);
    }

    private InventoryManager inv() { return plugin.rpInventory(); }

    /* ================================================================ emplacements */

    /** Barre d'action + sac porté du personnage actif d'un joueur en ligne. */
    public Emplacements sur(Player p) {
        InventoryManager im = inv();
        List<ItemStack> sac = im == null ? new ArrayList<>() : im.bagStorage(p);   // copie lue une fois
        return new Emplacements() {
            @Override public List<String> refs() {
                List<String> l = new ArrayList<>();
                for (int i = 0; i < 9; i++) l.add("H" + i);
                for (int i = 0; i < sac.size(); i++) l.add("B" + i);
                return l;
            }
            @Override public ItemStack lire(String ref) {
                int i = idx(ref);
                if (ref.charAt(0) == 'H') return i >= 0 && i < 9 ? p.getInventory().getItem(i) : null;
                return i >= 0 && i < sac.size() ? sac.get(i) : null;
            }
            @Override public void ecrire(String ref, ItemStack s) {
                int i = idx(ref);
                if (ref.charAt(0) == 'H') { if (i >= 0 && i < 9) p.getInventory().setItem(i, s); return; }
                if (i < 0 || i >= sac.size()) return;
                sac.set(i, s == null ? null : s.clone());
                if (im != null) im.poserDansSac(p, i, s);
            }
        };
    }

    static int idx(String ref) {
        try { return Integer.parseInt(ref.substring(1)); } catch (RuntimeException e) { return -1; }
    }

    /** Cases qui portent une bourse, dans l'ordre (barre d'abord). */
    public static List<String> bourses(Emplacements e) {
        List<String> out = new ArrayList<>();
        for (String r : e.refs()) if (Bourse.est(e.lire(r))) out.add(r);
        return out;
    }

    public static long total(Emplacements e) {
        long t = 0;
        for (String r : e.refs()) t += Bourse.montant(e.lire(r));
        return t;
    }

    /** Ce que le joueur porte sur lui (0 sans personnage). */
    public long montant(Player p) {
        if (p == null || plugin.characters().getActive(p.getUniqueId()) == null) return 0L;
        return total(sur(p));
    }

    /* ================================================================ crédit / débit */

    /**
     * Ajoute {@code n} ryo à la bourse du joueur (fusion ; crée une bourse dans la barre, puis le sac). {@code false} si
     * aucune place (rien n'a bougé).
     */
    public boolean crediter(Player p, long n, String action, String motif, CommandSender acteur) {
        if (p == null || n <= 0) return n == 0;
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return false;
        Emplacements e = sur(p);
        fusionner(p, e);
        List<String> b = bourses(e);
        long avant = total(e);
        if (!b.isEmpty()) {
            String r = b.get(0);
            e.ecrire(r, Bourse.avecMontant(e.lire(r), Bourse.montant(e.lire(r)) + n, false));
        } else {
            InventoryManager im = inv();
            ItemStack neuve = Bourse.creer(n, false);
            if (im == null) {
                if (!p.getInventory().addItem(neuve).isEmpty()) return false;
            } else {
                if (im.refusReception(p, neuve) != null) return false;
                if (im.recevoir(p, neuve) > 0) return false;
            }
        }
        apres(p, c, avant, avant + n, action, motif, acteur);
        return true;
    }

    /** Retire {@code n} ryo de la bourse ({@code false} si elle ne les contient pas : rien n'a bougé). */
    public boolean debiter(Player p, long n, String action, String motif, CommandSender acteur) {
        if (p == null || n <= 0) return n == 0;
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return false;
        Emplacements e = sur(p);
        fusionner(p, e);
        long avant = total(e);
        if (avant < n) return false;
        retirer(e, n);
        apres(p, c, avant, avant - n, action, motif, acteur);
        return true;
    }

    /** Retire {@code n} des bourses de {@code e} (la plus pleine d'abord) ; une bourse vide disparaît. */
    static void retirer(Emplacements e, long n) {
        long reste = n;
        for (String r : bourses(e)) {
            if (reste <= 0) break;
            ItemStack s = e.lire(r);
            long m = Bourse.montant(s);
            long pris = Math.min(m, reste);
            reste -= pris;
            e.ecrire(r, m - pris <= 0 ? null : Bourse.avecMontant(s, m - pris, false));
        }
    }

    /** Payer depuis la bourse (échoppe) : null si payé, sinon la raison. */
    public String payer(Player p, long prix, String motif) {
        if (prix <= 0) return null;
        if (montant(p) < prix) return "Pas assez dans ta bourse (" + Bourse.fmt(prix) + " ryo) — passe au guichet.";
        return debiter(p, prix, "DEPENSE", motif, p) ? null : "Paiement impossible.";
    }

    private void apres(Player p, ShinobiCharacter c, long avant, long apres, String action, String motif, CommandSender acteur) {
        InventoryManager im = inv();
        p.updateInventory();
        if (im != null) { im.push(p, false); im.recalc(p); }
        long delta = apres - avant;
        var j = Journal.entree(Categorie.ECONOMIE, action);
        if (acteur != null) j.acteur(acteur); else j.acteur(p);
        j.ciblePersonnage(p.getUniqueId(), c.id())
                .detail((delta >= 0 ? "+" : "") + Bourse.fmt(delta) + " ryo · bourse " + Bourse.fmt(avant) + " → "
                        + Bourse.fmt(apres) + " · " + (motif == null || motif.isBlank() ? "—" : motif))
                .lieu(p.getLocation()).ecrire();
        envoyer(p, apres);
        if (module.banque() != null) module.banque().noterBourse(c.id(), apres);
    }

    /** Pousse le montant au mod ({@code {"t":"bourse","n":…}}) s'il a changé. */
    public void envoyer(Player p, long n) {
        Long d = dernierEnvoye.put(p.getUniqueId(), n);
        if (d != null && d == n) return;
        module.canal().envoyer(p, new MonnaieCanal.J().s("t", "bourse").n("n", n));
    }

    /* ================================================================ une seule bourse */

    /** Fusionne toutes les bourses portées dans la première ; deux bourses au même identifiant = alerte de doublon. */
    void fusionner(Player p, Emplacements e) {
        List<String> b = bourses(e);
        if (b.size() < 2) return;
        String garde = b.get(0);
        ItemStack s0 = e.lire(garde);
        String id0 = Bourse.id(s0);
        long total = 0;
        boolean doublon = false;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (String r : b) {
            ItemStack s = e.lire(r);
            total += Bourse.montant(s);
            String id = Bourse.id(s);
            if (id != null && !ids.add(id)) doublon = true;
            if (!r.equals(garde)) e.ecrire(r, null);
        }
        e.ecrire(garde, Bourse.avecMontant(s0, total, false));
        if (doublon) {
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            Journal.entree(Categorie.ECONOMIE, "DOUBLON").acteur(p)
                    .detail("deux bourses au même identifiant (" + id0 + ") · total " + Bourse.fmt(total) + " ryo")
                    .lieu(p.getLocation()).ecrire();
            if (plugin.staffPanel() != null)
                plugin.staffPanel().onAlerteEconomie(p.getUniqueId(), "Bourse en double · " + Bourse.fmt(total) + " ryo"
                        + (c != null ? " · " + PlayerIds.prefix(c) + c.name() : ""), p.getLocation(), 40);
        }
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.characters().getActive(p.getUniqueId()) == null) continue;
            Emplacements e = sur(p);
            if (bourses(e).size() > 1) {
                fusionner(p, e);
                p.updateInventory();
                if (inv() != null) inv().push(p, false);
            }
            envoyer(p, total(e));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent ev) {
        dernierEnvoye.remove(ev.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        dernierEnvoye.remove(p.getUniqueId());
        rangement.remove(p.getUniqueId());
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c != null && module.banque() != null) module.banque().noterBourse(c.id(), total(sur(p)));
    }

    /* ================================================================ au sol */

    /** Pourquoi le joueur ne peut pas manipuler sa bourse maintenant (KO, porté, mode staff…), ou null. */
    public String verrou(Player p) {
        if (com.reborn.shinobicore.staff.outils.ModeStaff.horsJeu(p)) return "mode staff";
        String v = inv() == null ? null : inv().lockReason(p);
        return v;
    }

    /** « Combien veux-tu poser ? » → une bourse de {@code n} ryo à ses pieds (30 min). */
    public boolean poser(Player p, long n) {
        String v = verrou(p);
        if (v != null) { Notifs.msg(p, "refus", null, "Impossible", v); return false; }
        if (n <= 0) return false;
        long m = montant(p);
        if (m < n) { Notifs.msg(p, "refus", null, "Pas autant dans ta bourse", Bourse.fmt(m) + " ryo sur toi"); return false; }
        if (!debiter(p, n, "POSE_SOL", "bourse posée au sol", p)) return false;
        Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(0.7)).add(0, 0.25, 0);
        Item it = p.getWorld().dropItem(at, Bourse.creer(n, true));
        it.setVelocity(new org.bukkit.util.Vector(0, 0.05, 0));
        it.setPickupDelay(20);
        it.setThrower(p.getUniqueId());
        marquerSol(it, p.getUniqueId());
        Notifs.msg(p, "info", null, "−" + Bourse.fmt(n) + " ryo", "posés au sol");
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 0.5f, 1.4f);
        return true;
    }

    private void marquerSol(Item it, UUID par) {
        var pdc = it.getPersistentDataContainer();
        if (par != null) pdc.set(Bourse.POSEE_PAR, PersistentDataType.STRING, par.toString());
        if (!pdc.has(Bourse.FIN_SOL, PersistentDataType.LONG))
            pdc.set(Bourse.FIN_SOL, PersistentDataType.LONG, System.currentTimeMillis() + MonnaieConfig.get().solMs);
        pdc.set(SOL_FIN, PersistentDataType.LONG, 0L);           // ObjetsAuSol n'y touche plus
        it.setUnlimitedLifetime(true);
        ItemStack s = it.getItemStack();
        it.setItemStack(Bourse.avecMontant(s, Bourse.montant(s), true));
    }

    /** Toute bourse qui apparaît au sol (touche Q, mort, plugin) prend le délai de 30 min, après ObjetsAuSol. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent ev) {
        Item it = ev.getEntity();
        if (!Bourse.est(it.getItemStack())) return;
        UUID par = it.getThrower();
        Bukkit.getScheduler().runTask(plugin, () -> { if (it.isValid()) marquerSol(it, par); });
    }

    /** Touche Q sur la bourse (barre) : toute la bourse tombe — journalisé. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent ev) {
        ItemStack s = ev.getItemDrop().getItemStack();
        if (!Bourse.est(s)) return;
        Player p = ev.getPlayer();
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        long n = Bourse.montant(s);
        Journal.entree(Categorie.ECONOMIE, "POSE_SOL").acteur(p)
                .ciblePersonnage(p.getUniqueId(), c != null ? c.id() : null)
                .detail("−" + Bourse.fmt(n) + " ryo · bourse entière lâchée (touche Q)").lieu(p.getLocation()).ecrire();
        Bukkit.getScheduler().runTask(plugin, () -> envoyer(p, montant(p)));
    }

    /** Appelé par {@code Ramassage} (touche F) quand l'objet visé est une bourse : elle rejoint la bourse du joueur. */
    public boolean ramasser(Player p, Item it) {
        ItemStack s = it.getItemStack();
        long n = Bourse.montant(s);
        if (n <= 0) { it.remove(); return false; }
        String parS = it.getPersistentDataContainer().get(Bourse.POSEE_PAR, PersistentDataType.STRING);
        String par = null;
        UUID parId = null;
        try { parId = parS == null ? null : UUID.fromString(parS); } catch (IllegalArgumentException ignored) { }
        if (parId != null) {
            Player o = Bukkit.getPlayer(parId);
            ShinobiCharacter oc = o != null ? plugin.characters().getActive(parId) : null;
            par = oc != null ? PlayerIds.prefix(oc) + oc.name() : o != null ? o.getName() : parId.toString().substring(0, 8);
        }
        boolean autre = parId != null && !parId.equals(p.getUniqueId());
        Location lieu = it.getLocation();
        if (!crediter(p, n, "RAMASSE", "bourse ramassée au sol" + (par != null ? " (posée par " + par + ")" : ""), p)) {
            Notifs.msg(p, "refus", null, "Plus de place pour ta bourse", "libère une case");
            return false;
        }
        p.playPickupItemAnimation(it, 1);
        it.remove();
        Notifs.msg(p, "info", null, "+" + Bourse.fmt(n) + " ryo", "ramassés");
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS, 0.5f, 1.6f);
        if (autre && n >= MonnaieConfig.get().alerteRamasse && plugin.staffPanel() != null) {
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            plugin.staffPanel().onAlerteEconomie(p.getUniqueId(), "Bourse de " + Bourse.fmt(n) + " ryo ramassée par "
                    + (c != null ? PlayerIds.prefix(c) + c.name() : p.getName()) + " · posée par " + par, lieu, 20);
        }
        return true;
    }

    /** Les bourses au sol depuis plus de 30 min sont perdues (journal PERTE). */
    private void balayerSol() {
        long now = System.currentTimeMillis();
        for (World w : Bukkit.getWorlds()) {
            for (Item it : w.getEntitiesByClass(Item.class)) {
                ItemStack s = it.getItemStack();
                if (!Bourse.est(s) || !it.isValid()) continue;
                Long fin = it.getPersistentDataContainer().get(Bourse.FIN_SOL, PersistentDataType.LONG);
                if (fin == null) { marquerSol(it, it.getThrower()); continue; }
                if (fin > now) continue;
                Journal.entree(Categorie.ECONOMIE, "PERTE")
                        .detail("−" + Bourse.fmt(Bourse.montant(s)) + " ryo · bourse restée au sol "
                                + (MonnaieConfig.get().solMs / 60_000L) + " min")
                        .lieu(it.getLocation()).ecrire();
                it.remove();
            }
        }
    }

    /* ================================================================ coffres posés */

    private static boolean coffre(Inventory top) {
        if (top == null) return false;
        InventoryType t = top.getType();
        if (t == InventoryType.ENDER_CHEST) return true;
        if (!(t == InventoryType.CHEST || t == InventoryType.BARREL || t == InventoryType.SHULKER_BOX
                || t == InventoryType.HOPPER || t == InventoryType.DISPENSER || t == InventoryType.DROPPER)) return false;
        var h = top.getHolder(false);
        return h instanceof Container || h instanceof DoubleChest;
    }

    /**
     * Coffre posé ouvert : un clic sur la bourse <b>du coffre</b> la reprend entière (double-clic compris) ; un clic sur
     * <b>sa</b> bourse (ou la touche 1-9 qui la vise) demande « Combien veux-tu ranger ? ». La bourse ne passe jamais
     * par le curseur (pas de copie possible).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent ev) {
        if (!(ev.getWhoClicked() instanceof Player p)) return;
        Inventory top = ev.getView().getTopInventory();
        if (!coffre(top)) return;
        ItemStack cur = ev.getCurrentItem(), curseur = ev.getCursor();
        ItemStack touche = ev.getClick() == ClickType.NUMBER_KEY && ev.getHotbarButton() >= 0
                ? p.getInventory().getItem(ev.getHotbarButton()) : null;
        boolean dansCoffre = ev.getClickedInventory() == top;
        if (dansCoffre && Bourse.est(cur)) {
            ev.setCancelled(true);
            prendreDuCoffre(p, top, ev.getSlot());
            return;
        }
        if (Bourse.est(curseur) || Bourse.est(touche) || (!dansCoffre && Bourse.est(cur))) {
            ev.setCancelled(true);
            if (plugin.characters().getActive(p.getUniqueId()) == null) return;
            demanderRangement(p, top);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent ev) {
        if (Bourse.est(ev.getOldCursor()) && coffre(ev.getView().getTopInventory())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent ev) {
        Inventory r = rangement.get(ev.getPlayer().getUniqueId());
        if (r != null && r.equals(ev.getInventory())) rangement.remove(ev.getPlayer().getUniqueId());
    }

    private void demanderRangement(Player p, Inventory top) {
        String v = verrou(p);
        if (v != null) { Notifs.msg(p, "refus", null, "Impossible", v); return; }
        long m = montant(p);
        if (m <= 0) return;
        rangement.put(p.getUniqueId(), top);
        module.canal().envoyer(p, new MonnaieCanal.J().s("t", "combien").s("mode", "ranger").s("titre", "Ranger dans le coffre")
                .s("question", "Combien veux-tu ranger ?").n("max", m).s("action", "Ranger"));
    }

    /** Réponse au « Combien veux-tu ranger ? ». */
    public void ranger(Player p, long n) {
        Inventory top = rangement.remove(p.getUniqueId());
        if (top == null || n <= 0) return;
        if (!top.equals(p.getOpenInventory().getTopInventory())) {
            Notifs.msg(p, "refus", null, "Le coffre est fermé", null);
            return;
        }
        int slot = -1;
        for (int i = 0; i < top.getSize(); i++) if (Bourse.est(top.getItem(i))) { slot = i; break; }
        if (slot < 0) slot = top.firstEmpty();
        if (slot < 0) { Notifs.msg(p, "refus", null, "Coffre plein", "aucune case libre"); return; }
        Location lieu = top.getLocation() != null ? top.getLocation() : p.getLocation();
        if (!debiter(p, n, "RANGE", "rangé dans un coffre (" + court(lieu) + ")", p)) {
            Notifs.msg(p, "refus", null, "Pas autant dans ta bourse", null);
            return;
        }
        ItemStack deja = top.getItem(slot);
        top.setItem(slot, Bourse.est(deja) ? Bourse.avecMontant(deja, Bourse.montant(deja) + n, true) : Bourse.creer(n, true));
        Notifs.msg(p, "info", null, "−" + Bourse.fmt(n) + " ryo", "rangés dans le coffre");
    }

    private void prendreDuCoffre(Player p, Inventory top, int slot) {
        String v = verrou(p);
        if (v != null) { Notifs.msg(p, "refus", null, "Impossible", v); return; }
        if (plugin.characters().getActive(p.getUniqueId()) == null) return;
        ItemStack s = top.getItem(slot);
        long n = Bourse.montant(s);
        if (n <= 0) return;
        top.setItem(slot, null);
        Location lieu = top.getLocation() != null ? top.getLocation() : p.getLocation();
        if (!crediter(p, n, "PRIS", "pris dans un coffre (" + court(lieu) + ")", p)) {
            top.setItem(slot, s);
            Notifs.msg(p, "refus", null, "Plus de place pour ta bourse", "libère une case");
            return;
        }
        Notifs.msg(p, "info", null, "+" + Bourse.fmt(n) + " ryo", "pris dans le coffre");
    }

    static String court(Location l) {
        return l == null ? "?" : l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    /** Montants par joueur en ligne (pour /banque masse). */
    public Map<UUID, Long> enLigneMontants() {
        Map<UUID, Long> out = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            if (c != null) out.put(c.id(), total(sur(p)));
        }
        return out;
    }
}
