package com.reborn.shinobiabilities.parchemin;

import com.google.gson.JsonObject;
import com.reborn.shinobiabilities.techniques.ParcheminItems;
import com.reborn.shinobicore.technique.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vol dans une bibliothèque (docs/gameplay/APPRENTISSAGE.md §3.7). Un non-membre d'une bibliothèque « membres » dont
 * le vol est permis peut fouiller une case : canal de quelques secondes, <b>immobile devant le meuble</b> (durée de
 * base de la bibliothèque × facteur du rang du rouleau, {@code parchemins.vol.facteurs}). La fouille s'interrompt si
 * le voleur bouge, s'il est frappé, s'il ferme l'écran ou se déconnecte ; un bruit de pages tournées trahit la fouille
 * aux alentours. À la réussite : le rouleau part dans la sacoche, marqué « volé à &lt;bibliothèque&gt; », journal
 * {@code vol} (avec le personnage voleur), message discret aux membres connectés (nominatif si la bibliothèque le
 * règle), alerte {@code vol} du Poste de garde (ShinobiCore) et journal staff.
 */
public final class VolService implements Listener {

    /** Une fouille en cours. */
    private record Vol(String libId, boolean fonds, int slot, String techId, String scrollId, Library.Deposit deposit,
                       long start, long ms, Location from) {}

    private final JavaPlugin plugin;
    private final LibraryService libs;
    private final ScrollCatalog catalog;
    private final Map<UUID, Vol> active = new HashMap<>();
    /** Facteur de durée par rang, indices D C B A S. */
    private final double[] factors = {1.0, 1.5, 2.0, 3.0, 4.0};
    /** Distance (blocs) au-delà de laquelle un déplacement interrompt la fouille. */
    private double moveTolerance = 0.45;
    private int tick;

    public VolService(JavaPlugin plugin, LibraryService libs, ScrollCatalog catalog) {
        this.plugin = plugin;
        this.libs = libs;
        this.catalog = catalog;
    }

    public void loadConfig(ConfigurationSection sec) {
        if (sec == null) return;
        List<Double> f = sec.getDoubleList("facteurs");
        if (f.size() == 5) for (int i = 0; i < 5; i++) factors[i] = Math.max(0.1, Math.min(20, f.get(i)));
        moveTolerance = Math.max(0.1, Math.min(3, sec.getDouble("tolerance-mouvement", 0.45)));
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    public double[] factors() { return factors.clone(); }

    /** Durée de la fouille d'un rouleau de rang {@code r} dans cette bibliothèque (ms). */
    public long durationMs(Library l, char r) {
        return Math.round(l.theftSeconds * factors[ScrollCatalog.bucket(r)] * 1000.0);
    }


    // ─────────────────────────────────────────────────────────────── début

    public void start(Player p, Library l, String shelf, int slot) {
        if (slot < 0 || slot >= 9 || l.distanceSq(p.getLocation()) > LibraryService.REACH_SQ) return;
        if (active.containsKey(p.getUniqueId())) { libs.toast(p, "Tu fouilles déjà."); return; }
        if (libs.canTake(p, l)) { libs.take(p, l, shelf, slot); return; }     // rien à voler : prise normale
        if (!l.theft) {
            libs.toast(p, "Impossible de voler ici : les archives sont trop bien gardées.");
            libs.refresh(p, l);
            return;
        }
        if (!libs.canSteal(p, l)) { libs.toast(p, "Il faut un personnage actif."); return; }
        // États (ETATS_JOUEUR §2.2 f, étape 2) : ni à terre, ni étourdi, ni porté, ni gelé, ni entravé (INTERAGIR).
        var etats = com.reborn.shinobicore.api.EtatService.get();
        String refusEtat = etats == null ? null : etats.refus(p.getUniqueId(), com.reborn.shinobicore.api.etat.Capacite.INTERAGIR);
        if (refusEtat != null) { libs.toast(p, refusEtat); return; }
        boolean fonds = "fonds".equals(shelf);
        String techId, scrollId = null;
        Library.Deposit dep = null;
        if (fonds) {
            dep = l.fonds[slot];
            if (dep == null) { libs.toast(p, "Ce rouleau a déjà disparu."); libs.refresh(p, l); return; }
            techId = dep.techId();
        } else {
            if (l.cells[slot] == null) { libs.toast(p, "Ce rouleau a déjà disparu."); libs.refresh(p, l); return; }
            techId = l.cells[slot];
            scrollId = l.scrollIds[slot];
        }
        Ability a = catalog.byId(techId);
        char r = a == null ? 'D' : ScrollCatalog.rank(a);
        var inv = libs.inventory();
        if (inv != null && inv.weight(p) + ScrollCatalog.weight(r) > inv.maxWeight(p) + 1e-6) {
            libs.toast(p, "Ta sacoche est trop lourde.");
            return;
        }
        long ms = durationMs(l, r);
        active.put(p.getUniqueId(), new Vol(l.id, fonds, slot, techId, scrollId, dep, System.currentTimeMillis(), ms,
                p.getLocation().clone()));
        JsonObject o = new JsonObject();
        o.addProperty("t", "steal");
        o.addProperty("state", "start");
        o.addProperty("id", l.id);
        o.addProperty("shelf", fonds ? "fonds" : "arrivages");
        o.addProperty("slot", slot);
        o.addProperty("ms", ms);
        libs.send(p, o);
        sound(l, Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.8f);
    }

    // ─────────────────────────────────────────────────────────────── suivi

    private void tick() {
        if (active.isEmpty()) return;
        tick++;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Vol>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Vol> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Vol v = e.getValue();
            Library l = libs.byId(v.libId());
            if (p == null || !p.isOnline()) { it.remove(); continue; }
            if (l == null) { it.remove(); fail(p, "La bibliothèque a disparu."); continue; }
            // L'état a changé (KO, étourdi, porté, gelé…) : la fouille s'arrête.
            var etats = com.reborn.shinobicore.api.EtatService.get();
            if (etats != null && !etats.peut(p.getUniqueId(), com.reborn.shinobicore.api.etat.Capacite.INTERAGIR)) {
                it.remove();
                fail(p, "La fouille est interrompue.");
                continue;
            }
            Location at = p.getLocation();
            if (at.getWorld() != v.from().getWorld() || at.distanceSquared(v.from()) > moveTolerance * moveTolerance
                    || l.distanceSq(at) > LibraryService.REACH_SQ) {
                it.remove();
                fail(p, "Tu as bougé : la fouille est interrompue.");
                continue;
            }
            if (now - v.start() >= v.ms()) {
                it.remove();
                complete(p, l, v);
                continue;
            }
            if (tick % 12 == 0) sound(l, Sound.ITEM_BOOK_PAGE_TURN, 0.55f, 0.7f + (tick % 24 == 0 ? 0.15f : 0f));
        }
    }

    /** Frappé (même si un système de combat annule les dégâts vanilla), ou blessé : la fouille s'arrête. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent e) {
        if (e.isCancelled() && !(e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent)) return;
        if (e.getEntity() instanceof Player p && active.remove(p.getUniqueId()) != null) {
            fail(p, "Tu as été frappé : la fouille est interrompue.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { active.remove(e.getPlayer().getUniqueId()); }

    /** Abandon (écran fermé) : rien ne part. */
    public void abort(Player p, String msg) {
        if (active.remove(p.getUniqueId()) != null) fail(p, msg);
    }

    private void fail(Player p, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "steal");
        o.addProperty("state", "fail");
        o.addProperty("msg", msg);
        libs.send(p, o);
        libs.toast(p, msg);
    }

    // ─────────────────────────────────────────────────────────────── réussite

    private void complete(Player p, Library l, Vol v) {
        ItemStack it;
        Ability a = catalog.byId(v.techId());
        if (v.fonds()) {
            Library.Deposit d = l.fonds[v.slot()];
            if (d == null || d != v.deposit()) { fail(p, "Quelqu'un a été plus rapide : le rouleau a disparu."); libs.refresh(p, l); return; }
            it = libs.itemOf(d);
            l.fonds[v.slot()] = null;
            if (it == null) { libs.save(); libs.refresh(p, l); return; }
        } else {
            if (!v.techId().equals(l.cells[v.slot()]) || (v.scrollId() != null && !v.scrollId().equals(l.scrollIds[v.slot()]))) {
                fail(p, "Quelqu'un a été plus rapide : le rouleau a disparu.");
                libs.refresh(p, l);
                return;
            }
            if (a == null) { l.cells[v.slot()] = null; libs.save(); libs.refresh(p, l); return; }
            it = ParcheminItems.create(a, l.scrollIds[v.slot()]);
            l.cells[v.slot()] = null;
            l.scrollIds[v.slot()] = null;
        }
        var inv = libs.inventory();
        char r = a == null ? 'D' : ScrollCatalog.rank(a);
        String techName = a == null ? v.techId() : a.name();
        boolean fits = inv == null || inv.weight(p) + ScrollCatalog.weight(r) <= inv.maxWeight(p) + 1e-6;
        ParcheminItems.markStolen(it, l.name);
        String thief = libs.who(p);
        l.log(thief, "vol", v.fonds() ? "fonds" : "arrivages", a == null ? v.techId() : a.id(), techName, r);
        com.reborn.shinobicore.journal.Journal.entree(com.reborn.shinobicore.api.JournalService.Categorie.BIBLIOTHEQUE, "VOL").acteur(p).cibleTexte(l.name)
                .detail(techName + " (rang " + r + ") · " + (v.fonds() ? "fonds" : "arrivages")).lieu(p.getLocation()).ecrire();
        libs.save();
        if (fits) {
            libs.give(p, it);
        } else {                                   // sacoche pleine au dernier moment : le rouleau tombe à ses pieds
            p.getWorld().dropItemNaturally(p.getLocation(), it);
        }
        sound(l, Sound.ITEM_BOOK_PUT, 0.6f, 1.2f);

        JsonObject o = new JsonObject();
        o.addProperty("t", "steal");
        o.addProperty("state", "done");
        o.addProperty("msg", "Rouleau volé : " + techName);
        libs.send(p, o);
        libs.toast(p, "Rouleau volé : " + techName);
        libs.refresh(p, l);

        // alerte discrète aux membres connectés (pas au voleur ; le staff a le Poste de garde)
        Component msg = Component.text(l.theftNamed
                        ? thief + " a fouillé les archives de « " + l.name + " » : un rouleau de rang " + r + " a disparu."
                        : "Quelqu'un fouille les archives de « " + l.name + " »… un rouleau de rang " + r + " a disparu.",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true);
        for (Player m : Bukkit.getOnlinePlayers()) {
            if (m.equals(p) || !libs.isMemberRp(m, l)) continue;
            m.sendMessage(msg);
            m.playSound(m.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.5f, 0.6f);
        }
        String detail = techName + " (rang " + r + ") · " + l.name + " · par " + thief;
        libs.logStaff(p, "Vol de parchemin", detail);
        if (Bukkit.getPluginManager().getPlugin("ShinobiCore") instanceof com.reborn.shinobicore.ShinobiCore sc
                && sc.staffPanel() != null) {
            try {
                sc.staffPanel().onVolParchemin(p, l.name, techName, r, new Location(Bukkit.getWorld(l.world), l.x, l.y, l.z));
            } catch (LinkageError ignored) {
                // ShinobiCore sans l'alerte « vol » : le journal staff suffit
            }
        }
    }

    private void sound(Library l, Sound s, float vol, float pitch) {
        World w = Bukkit.getWorld(l.world);
        if (w != null) w.playSound(new Location(w, l.x + 0.5, l.y + 0.5, l.z + 0.5), s, SoundCategory.BLOCKS, vol, pitch);
    }
}
