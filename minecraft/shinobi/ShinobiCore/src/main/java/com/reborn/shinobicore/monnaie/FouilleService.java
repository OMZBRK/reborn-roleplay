package com.reborn.shinobicore.monnaie;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.AutoMe;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.inventory.Notifs;
import com.reborn.shinobicore.inventory.RareteItems;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.ko.action.DeathScene;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le vol sur un corps (MONNAIE_ET_BANQUE.md §8, décision 3 du 15/10) : « Fouiller » (menu R) sur un corps à terre,
 * inconscient ou mort, <b>3 s immobile</b> (interrompu si l'on bouge ou si l'on est frappé ; la victime voit « On te
 * fouille »), puis la <b>sacoche du corps</b> en mode fouille : barre, sac et bourse. On prend, on ne pose rien.
 *
 * <ul>
 *   <li>Bourse : « Prendre combien ? », <b>30 % au plus</b> de ce qu'elle contenait à la première fouille de la scène
 *       ({@code monnaie.vol.pourcent}), tous voleurs confondus ; à la <b>mort RP</b>, sans plafond
 *       ({@code monnaie.vol.sans-plafond-mort-rp}) ;</li>
 *   <li>corps <b>fouillable même si le joueur est parti</b> : le corps laissé au sol (mannequin de la scène de mort,
 *       15 min) se fouille sur les affaires enregistrées du personnage ;</li>
 *   <li>vol ≥ 1 000 ryo : alerte {@code vol} au Poste de garde ; journal {@code ECONOMIE VOL} (voleur, victime, montant,
 *       lieu) et {@code OBJET VOL} pour les objets, marqués « volés » ({@code reborn:vole_a}) : refusés au coffre.</li>
 * </ul>
 * Le compte et le coffre ne se volent jamais.
 */
public final class FouilleService implements Listener {

    public static final NamespacedKey VOLE_A = new NamespacedKey("reborn", "vole_a");
    private static final long SCENE_MS = 30 * 60_000L;

    private final ShinobiCore plugin;
    private final MonnaieModule module;

    /** Fouille en cours ou ouverte, par voleur. */
    private final Map<UUID, Fouille> fouilles = new ConcurrentHashMap<>();
    /** Plafond par victime (personnage) et par scène. */
    private final Map<UUID, Scene> scenes = new ConcurrentHashMap<>();

    private static final class Fouille {
        UUID looter, cibleJoueur, mannequin, perso, owner;
        String nom, etat;
        Location depart;
        long debut;
        boolean ouverte, mort;
    }

    private static final class Scene {
        long initial, pris, maj;
    }

    FouilleService(ShinobiCore plugin, MonnaieModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    private InventoryManager inv() { return plugin.rpInventory(); }

    /* ================================================================ début */

    /** {@code /corps fouiller <joueur>} : la cible est un joueur à terre, inconscient ou en scène de mort. */
    public void commencer(Player p, Player t) {
        ShinobiCharacter c = plugin.characters().getActive(t.getUniqueId());
        if (c == null) { p.sendActionBar(net.kyori.adventure.text.Component.text("Rien à fouiller.")); return; }
        boolean mort = plugin.deathScene() != null && plugin.deathScene().isDead(t.getUniqueId());
        Fouille f = new Fouille();
        f.looter = p.getUniqueId();
        f.cibleJoueur = t.getUniqueId();
        f.perso = c.id();
        f.owner = t.getUniqueId();
        f.nom = c.name();
        f.mort = mort;
        f.etat = mort ? "mort" : plugin.ko().isKo(t.getUniqueId()) ? "à terre" : "inconscient";
        lancer(p, f);
        Notifs.msg(t, "etat", null, "On te fouille", null);
        AutoMe.broadcast(plugin, p, "fouille les affaires de " + f.nom + ".");
    }

    /** {@code /corps fouiller corps:<uuid>} : corps laissé au sol (mannequin), joueur parti. */
    public void commencerCorps(Player p, String uuid) {
        DeathScene ds = plugin.deathScene();
        UUID m;
        try { m = UUID.fromString(uuid.trim()); } catch (IllegalArgumentException e) { return; }
        DeathScene.CorpseOwner co = ds == null ? null : ds.corpseOwner(m);
        if (co == null || co.charId() == null) { p.sendActionBar(net.kyori.adventure.text.Component.text("Ce corps n'a plus rien.")); return; }
        if (!co.at().getWorld().equals(p.getWorld()) || co.at().distance(p.getLocation()) > MonnaieConfig.get().fouilleDistance) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("Trop loin."));
            return;
        }
        String v = module.bourses().verrou(p);
        if (v != null || plugin.ko().isKo(p.getUniqueId())) { p.sendActionBar(net.kyori.adventure.text.Component.text("Tu ne peux pas maintenant.")); return; }
        Fouille f = new Fouille();
        f.looter = p.getUniqueId();
        f.mannequin = m;
        f.perso = co.charId();
        f.owner = co.player();
        f.nom = co.name();
        f.mort = true;
        f.etat = "mort";
        lancer(p, f);
        AutoMe.broadcast(plugin, p, "fouille le corps de " + f.nom + ".");
    }

    private void lancer(Player p, Fouille f) {
        fermer(p, null);
        f.depart = p.getLocation().clone();
        f.debut = System.currentTimeMillis();
        fouilles.put(p.getUniqueId(), f);
        int s = MonnaieConfig.get().fouilleSecondes;
        if (s <= 0) { ouvrir(p, f); return; }
        module.canal().envoyer(p, new MonnaieCanal.J().s("t", "fouille_prog").s("nom", f.nom).n("s", s));
    }

    /* ================================================================ boucle */

    private void tick() {
        long now = System.currentTimeMillis();
        for (Fouille f : new ArrayList<>(fouilles.values())) {
            Player p = Bukkit.getPlayer(f.looter);
            if (p == null) { fouilles.remove(f.looter); continue; }
            String pb = probleme(p, f);
            if (pb != null) { fermer(p, pb); continue; }
            if (!f.ouverte) {
                Location l = p.getLocation();
                if (!l.getWorld().equals(f.depart.getWorld()) || l.distanceSquared(f.depart) > 0.12) {
                    fermer(p, "Fouille interrompue : tu as bougé.");
                    continue;
                }
                if (now - f.debut >= MonnaieConfig.get().fouilleSecondes * 1000L) ouvrir(p, f);
            }
        }
        scenes.entrySet().removeIf(e -> now - e.getValue().maj > SCENE_MS);
    }

    /** Ce qui empêche de continuer (cible relevée, partie, trop loin…), ou null. */
    private String probleme(Player p, Fouille f) {
        Location at;
        if (f.mannequin != null) {
            DeathScene.CorpseOwner co = plugin.deathScene() == null ? null : plugin.deathScene().corpseOwner(f.mannequin);
            if (co == null) return "Le corps n'est plus là.";
            at = co.at();
        } else {
            Player t = Bukkit.getPlayer(f.cibleJoueur);
            if (t == null) return "La cible est partie.";
            boolean ok = plugin.ko().isKo(t.getUniqueId())
                    || (plugin.deathScene() != null && plugin.deathScene().isDead(t.getUniqueId()));
            ShinobiCharacter c = plugin.characters().getActive(t.getUniqueId());
            if (!ok || c == null || !c.id().equals(f.perso)) return "La cible s'est relevée.";
            at = t.getLocation();
        }
        if (!at.getWorld().equals(p.getWorld()) || at.distance(p.getLocation()) > MonnaieConfig.get().fouilleDistance + 1)
            return "Trop loin du corps.";
        if (plugin.ko().isKo(p.getUniqueId())) return "Tu es à terre.";
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player p)) return;
        Fouille f = fouilles.get(p.getUniqueId());
        if (f != null && !f.ouverte) fermer(p, "Fouille interrompue : on t'a frappé.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent ev) {
        fouilles.remove(ev.getPlayer().getUniqueId());
        for (Fouille f : new ArrayList<>(fouilles.values())) {
            if (ev.getPlayer().getUniqueId().equals(f.cibleJoueur)) {
                Player p = Bukkit.getPlayer(f.looter);
                if (p != null) fermer(p, "La cible est partie.");
            }
        }
    }

    /** Ferme la fouille (et l'écran du voleur) ; {@code raison} affichée si non nulle. */
    public void fermer(Player p, String raison) {
        Fouille f = fouilles.remove(p.getUniqueId());
        if (f == null) return;
        module.canal().envoyer(p, new MonnaieCanal.J().s("t", f.ouverte ? "fouille_ferme" : "fouille_prog_fin"));
        if (raison != null) p.sendActionBar(net.kyori.adventure.text.Component.text(raison,
                net.kyori.adventure.text.format.NamedTextColor.GRAY));
    }

    /* ================================================================ sacoche du corps */

    private void ouvrir(Player p, Fouille f) {
        f.ouverte = true;
        envoyerEtat(p, f);
        p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 0.4f, 1.2f);
    }

    /** Les affaires de la victime : le joueur en ligne s'il joue ce personnage, sinon les affaires enregistrées. */
    private BourseService.Emplacements source(Fouille f) {
        Player t = f.cibleJoueur != null ? Bukkit.getPlayer(f.cibleJoueur) : null;
        if (t == null && f.owner != null) {
            Player o = Bukkit.getPlayer(f.owner);
            ShinobiCharacter act = o == null ? null : plugin.characters().getActive(o.getUniqueId());
            if (act != null && act.id().equals(f.perso)) t = o;
        }
        if (t != null) return module.bourses().sur(t);
        ShinobiCharacter c = plugin.characters().findById(f.owner, f.perso).orElse(null);
        InventoryManager im = inv();
        List<ItemStack> sac = im == null ? new ArrayList<>() : im.sacDuPersonnage(f.perso);
        ItemStack[] snap = c == null ? null : c.inventorySnapshot();
        return new BourseService.Emplacements() {
            @Override public List<String> refs() {
                List<String> l = new ArrayList<>();
                for (int i = 0; i < 9; i++) l.add("H" + i);
                for (int i = 0; i < sac.size(); i++) l.add("B" + i);
                return l;
            }
            @Override public ItemStack lire(String ref) {
                int i = BourseService.idx(ref);
                if (ref.charAt(0) == 'H') return snap != null && i >= 0 && i < Math.min(9, snap.length) ? snap[i] : null;
                return i >= 0 && i < sac.size() ? sac.get(i) : null;
            }
            @Override public void ecrire(String ref, ItemStack s) {
                int i = BourseService.idx(ref);
                if (ref.charAt(0) == 'H') {
                    if (snap != null && i >= 0 && i < Math.min(9, snap.length)) {
                        snap[i] = s;
                        if (c != null) plugin.characters().save(c);
                    }
                    return;
                }
                if (i < 0 || i >= sac.size()) return;
                sac.set(i, s);
                if (im != null) im.poserDansSacDe(f.perso, i, s);
            }
        };
    }

    private Scene scene(Fouille f, long bourse) {
        Scene s = scenes.computeIfAbsent(f.perso, k -> {
            Scene n = new Scene();
            n.initial = bourse;
            return n;
        });
        s.maj = System.currentTimeMillis();
        return s;
    }

    /** Ce que le voleur peut encore prendre dans la bourse. */
    private long reste(Fouille f, long bourse) {
        if (f.mort && MonnaieConfig.get().volSansPlafondMort) return bourse;
        Scene s = scene(f, bourse);
        long plafond = s.initial * MonnaieConfig.get().volPourcent / 100;
        return Math.max(0, Math.min(bourse, plafond - s.pris));
    }

    private void envoyerEtat(Player p, Fouille f) {
        BourseService.Emplacements e = source(f);
        long bourse = BourseService.total(e);
        long reste = reste(f, bourse);
        InventoryManager im = inv();
        JsonArray hot = new JsonArray(), bag = new JsonArray();
        for (String r : e.refs()) {
            String js = im == null ? "null" : im.jsonObjet(e.lire(r));
            (r.charAt(0) == 'H' ? hot : bag).add(JsonParser.parseString(js));
        }
        boolean plafond = !(f.mort && MonnaieConfig.get().volSansPlafondMort);
        module.canal().envoyer(p, new MonnaieCanal.J().s("t", "fouille").s("cible", f.nom).s("etat", f.etat)
                .s("uuid", f.cibleJoueur != null ? f.cibleJoueur.toString() : f.mannequin.toString())
                .n("bourse", bourse).n("reste", reste).b("plafond", plafond).n("pourcent", MonnaieConfig.get().volPourcent)
                .n("extraSlots", bag.size()).a("hotbar", hot).a("bag", bag));
    }

    /* ================================================================ prendre */

    /** Prendre l'objet d'une case du corps (pas la bourse : « Prendre combien ? »). */
    public void prendre(Player p, String ref) {
        Fouille f = fouilles.get(p.getUniqueId());
        if (f == null || !f.ouverte || ref == null || ref.length() < 2) return;
        BourseService.Emplacements e = source(f);
        ItemStack s = e.lire(ref);
        if (s == null || s.getType().isAir()) return;
        if (Bourse.est(s)) return;
        InventoryManager im = inv();
        if (im == null) return;
        ItemStack pris = s.clone();
        marquerVole(pris, f.nom);
        String refus = im.refusReception(p, pris);
        if (refus != null) {
            Notifs.msg(p, "refus", null, Character.toUpperCase(refus.charAt(0)) + refus.substring(1), null);
            return;
        }
        e.ecrire(ref, null);
        int reste = im.recevoir(p, pris);
        if (reste > 0) {   // ne devrait pas arriver (vérifié) : on rend au corps
            ItemStack r = s.clone();
            r.setAmount(reste);
            e.ecrire(ref, r);
        }
        int n = pris.getAmount() - reste;
        Notifs.objet(p, true, pris, n, "de " + f.nom);
        Journal.entree(Categorie.OBJET, "VOL").acteur(p).ciblePersonnage(f.owner, f.perso)
                .detail(n + " × " + RareteItems.nom(s) + " · fouille (" + f.etat + ")").lieu(p.getLocation()).ecrire();
        apresVictime(f);
        envoyerEtat(p, f);
    }

    /** « Prendre combien ? » dans la bourse du corps. */
    public void voler(Player p, long n) {
        Fouille f = fouilles.get(p.getUniqueId());
        if (f == null || !f.ouverte || n <= 0) return;
        BourseService.Emplacements e = source(f);
        long bourse = BourseService.total(e);
        long reste = reste(f, bourse);
        if (n > reste) {
            Notifs.msg(p, "refus", null, "Pas plus de " + Bourse.fmt(reste) + " ryo", "dans cette scène");
            n = reste;
        }
        if (n <= 0) return;
        // place pour la bourse du voleur, vérifiée avant de toucher au corps
        if (module.bourses().montant(p) <= 0 && inv() != null && inv().refusReception(p, Bourse.creer(n, false)) != null) {
            Notifs.msg(p, "refus", null, "Plus de place pour ta bourse", "libère une case");
            return;
        }
        BourseService.retirer(e, n);
        if (!module.bourses().crediter(p, n, "VOL", "pris sur le corps de " + f.nom + " (" + f.etat + ")", p)) {
            // rendu au corps
            List<String> b = BourseService.bourses(e);
            if (!b.isEmpty()) e.ecrire(b.get(0), Bourse.avecMontant(e.lire(b.get(0)), Bourse.montant(e.lire(b.get(0))) + n, false));
            else for (String r : e.refs()) if (e.lire(r) == null) { e.ecrire(r, Bourse.creer(n, false)); break; }
            return;
        }
        Scene sc = scene(f, bourse);
        sc.pris += n;
        Journal.entree(Categorie.ECONOMIE, "VOL").acteur(p).ciblePersonnage(f.owner, f.perso)
                .detail("−" + Bourse.fmt(n) + " ryo · bourse " + Bourse.fmt(bourse) + " → " + Bourse.fmt(bourse - n)
                        + " · fouille (" + f.etat + ")")
                .lieu(p.getLocation()).ecrire();
        if (n >= MonnaieConfig.get().volAlerte && plugin.staffPanel() != null) {
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            plugin.staffPanel().onAlerteEconomie(p.getUniqueId(), "Vol de " + Bourse.fmt(n) + " ryo sur " + f.nom + " ("
                    + f.etat + ") par " + (c != null ? PlayerIds.prefix(c) + c.name() : p.getName()), p.getLocation(),
                    n >= 5 * MonnaieConfig.get().volAlerte ? 40 : 20);
        }
        apresVictime(f);
        envoyerEtat(p, f);
    }

    private void apresVictime(Fouille f) {
        Player t = f.cibleJoueur != null ? Bukkit.getPlayer(f.cibleJoueur) : null;
        if (t == null) return;
        t.updateInventory();
        if (inv() != null) { inv().push(t, false); inv().recalc(t); }
        module.bourses().envoyer(t, module.bourses().montant(t));
    }

    static void marquerVole(ItemStack s, String victime) {
        ItemMeta m = s.getItemMeta();
        if (m == null) return;
        m.getPersistentDataContainer().set(VOLE_A, PersistentDataType.STRING,
                victime + " · " + new java.text.SimpleDateFormat("dd/MM/yyyy").format(new java.util.Date()));
        s.setItemMeta(m);
    }

    /** Entité vue par le mod comme cible (joueur ou mannequin) — sert au menu R. */
    public static boolean estCorps(Entity e) {
        ShinobiCore core = ShinobiCore.get();
        return core != null && core.deathScene() != null && core.deathScene().corpseOwner(e.getUniqueId()) != null;
    }
}
