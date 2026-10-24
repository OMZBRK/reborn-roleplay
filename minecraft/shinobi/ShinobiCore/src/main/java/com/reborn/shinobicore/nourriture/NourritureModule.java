package com.reborn.shinobicore.nourriture;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.ModificateurService;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.entreprise.EntrepriseCommand;
import com.reborn.shinobicore.entreprise.EntrepriseService;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.inventory.Notifs;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.journal.RyoJournal;
import com.reborn.shinobicore.modificateur.ModificateurRegistry;
import com.reborn.shinobicore.modificateur.TypeModificateur;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Nourriture</b> (GAMEPLAN §12, §27 ; docs/gameplay/NOURRITURE.md v3, validée le 14/10) — assemble le registre des
 * modificateurs, les entreprises, la satiété, la cuisine, les repas et le geste « tendre ». Un seul point d'entrée
 * pour {@code ShinobiCore} ({@link #start()} / {@link #stop()} / {@link #reload()}).
 *
 * <p>Ce module tient aussi : le clic droit (panier sur un fourneau, plat ou ration en main), l'état « plat en main »
 * (chaud / froid) pour le HUD, l'onglet <b>Vivres</b> de l'échoppe (rations pour tous, paniers pour les cuisiniers) et
 * la pilule du soldat.
 */
public final class NourritureModule implements Listener {

    private final ShinobiCore plugin;
    private NourritureConfig cfg;
    private final ModificateurRegistry modifs;
    private final EntrepriseService entreprises;
    private SatieteService satiete;
    private RepasService repas;
    private CuisineService cuisine;
    private TendreService tendre;
    private BukkitTask tache;
    private int tick;
    /** Dernier état « plat en main » envoyé : clé (recette|chaud) ou "". */
    private final Map<UUID, String> mainEnvoyee = new HashMap<>();

    public NourritureModule(ShinobiCore plugin) {
        this.plugin = plugin;
        this.modifs = new ModificateurRegistry(plugin);
        this.entreprises = new EntrepriseService(plugin);
    }

    /* ================================================================ cycle */

    public void start() {
        cfg = NourritureConfig.lire(plugin.getConfig());
        modifs.start();
        entreprises.charger();
        NourritureCanal.start(plugin);
        satiete = new SatieteService(plugin, this);
        satiete.charger();
        repas = new RepasService(plugin, this);
        cuisine = new CuisineService(plugin, this);
        tendre = new TendreService(plugin, this);
        modifs.ajouterContributeur(satiete);
        modifs.ajouterContributeur(repas);
        modifs.pauseSi(satiete::enPause);
        Bukkit.getPluginManager().registerEvents(satiete, plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getServicesManager().register(ModificateurService.class, modifs, plugin,
                org.bukkit.plugin.ServicePriority.Normal);

        PluginCommand ent = plugin.getCommand("entreprise");
        if (ent != null) {
            EntrepriseCommand ex = new EntrepriseCommand(plugin, entreprises);
            ent.setExecutor(ex);
            ent.setTabCompleter(ex);
        } else plugin.getLogger().warning("Command 'entreprise' is not declared in plugin.yml.");
        PluginCommand td = plugin.getCommand("tendre");
        if (td != null) { td.setExecutor(tendre); td.setTabCompleter(tendre); }
        else plugin.getLogger().warning("Command 'tendre' is not declared in plugin.yml.");
        PluginCommand nc = plugin.getCommand("nourriture");
        if (nc != null) {
            NourritureCommand ex = new NourritureCommand(plugin, this);
            nc.setExecutor(ex);
            nc.setTabCompleter(ex);
        } else plugin.getLogger().warning("Command 'nourriture' is not declared in plugin.yml.");

        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void stop() {
        if (tache != null) { tache.cancel(); tache = null; }
        if (satiete != null) satiete.sauver();
        modifs.stop();
    }

    public void reload() {
        cfg = NourritureConfig.lire(plugin.getConfig());
        modifs.reload();
    }

    /** Toutes les 0,5 s : repas, cuisine ; toutes les secondes : satiété, offres, plat en main. */
    private void tick() {
        if (!cfg.actif) return;
        tick++;
        repas.tick();
        cuisine.tick();
        if (tick % 2 == 0) {
            satiete.tick();
            tendre.tick();
            platEnMain();
        }
    }

    /* ================================================================ accès */

    NourritureConfig cfg() { return cfg; }

    public ModificateurRegistry modifs() { return modifs; }

    public EntrepriseService entreprises() { return entreprises; }

    public SatieteService satiete() { return satiete; }

    RepasService repas() { return repas; }

    CuisineService cuisine() { return cuisine; }

    /** Champs ajoutés à {@code reborn:vitals} pour un client qui écoute {@code reborn:nourriture}, sinon null. */
    public int[] vitalsEtendus(Player p) {
        if (satiete == null || !cfg.actif || !NourritureCanal.ecoute(p)) return null;
        return satiete.vitalsEtendus(p);
    }

    /* ================================================================ objets */

    /** Un plat de la recette, chaud jusqu'à {@code chaud}, avec ses effets en clair dans la description. */
    ItemStack plat(NourritureConfig.Plat p, long chaud, int n) {
        List<String> eff = new ArrayList<>();
        for (NourritureConfig.Effet e : p.effets()) {
            TypeModificateur t = modifs.type(e.type());
            if (t == null) continue;
            String v = t.valeurAffichee(e.intensite());
            eff.add(t.nom() + (v.isEmpty() ? "" : " " + v) + " · " + e.dureeMs() / 60_000 + " min");
        }
        return NourritureItems.plat(p, chaud, n, eff);
    }

    /** Donne sans la notification ni la ligne de journal de la sacoche (le système appelant fait les siennes). */
    void donnerSansBruit(Player p, ItemStack s) {
        InventoryManager inv = plugin.rpInventory();
        int reste = inv == null ? 0 : inv.recevoir(p, s);
        if (inv == null) p.getInventory().addItem(s).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
        if (reste > 0) {
            ItemStack r = s.clone();
            r.setAmount(reste);
            p.getWorld().dropItemNaturally(p.getLocation(), r);
            refus(p, "Plus de place", reste + " au sol");
        }
    }

    /* ================================================================ messages */

    void refus(Player p, String gras, String texte) {
        NourritureCanal.notif(p, "refus", null, gras, texte);
        if (!Notifs.aLeMod(p)) p.sendActionBar(net.kyori.adventure.text.Component.text(gras + (texte == null ? "" : " — " + texte),
                net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    void info(Player p, String ic, String gras, String texte) {
        NourritureCanal.notif(p, "info", ic, gras, texte);
        if (!Notifs.aLeMod(p)) p.sendActionBar(net.kyori.adventure.text.Component.text(gras + (texte == null ? "" : " — " + texte),
                net.kyori.adventure.text.format.NamedTextColor.GOLD));
    }

    void passageAffame(Player p) { NourritureCanal.notif(p, "palier", "affame", "Affamé", "va manger"); }

    void passageFaim(Player p) { NourritureCanal.notif(p, "refus", "faim", "Faim", "ni sprint ni dash"); }

    /** Un coup reçu : repas figé, préparation interrompue. */
    void coupRecu(Player p) {
        repas.coup(p);
        cuisine.coup(p);
    }

    /* ================================================================ clic droit */

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent ev) {
        if (!cfg.actif || ev.getHand() != EquipmentSlot.HAND) return;
        if (ev.getAction() != Action.RIGHT_CLICK_AIR && ev.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = ev.getPlayer();
        ItemStack main = ev.getItem();
        String genre = NourritureItems.genre(main);
        if (genre == null) return;
        boolean annuler = false;
        if (NourritureItems.PANIER.equals(genre)) {
            if (ev.getClickedBlock() != null) annuler = cuisine.commencer(p, main, ev.getClickedBlock().getLocation(), 0);
            if (!annuler) {
                annuler = true;
                info(p, "panier", "Un panier d'ingrédients", "clic droit sur le fourneau d'un restaurant");
            }
        } else if (NourritureItems.PLAT.equals(genre) || NourritureItems.RATION.equals(genre)) {
            annuler = repas.commencer(p, main, genre);
        } else if (NourritureItems.BOL_VIDE.equals(genre)) {
            annuler = true;
        }
        if (annuler) ev.setCancelled(true);
    }

    /** Meuble Nexo (entité d'interaction / d'affichage) marqué comme fourneau. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent ev) {
        if (!cfg.actif || ev.getHand() != EquipmentSlot.HAND || ev.getRightClicked() instanceof Player) return;
        Player p = ev.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        if (!NourritureItems.PANIER.equals(NourritureItems.genre(main))) return;
        if (cuisine.commencer(p, main, ev.getRightClicked().getLocation(), 1)) ev.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        UUID id = ev.getPlayer().getUniqueId();
        repas.oublier(id);
        cuisine.oublier(id);
        tendre.oublier(id);
        mainEnvoyee.remove(id);
        ShinobiCharacter c = plugin.characters().getActive(id);
        if (c != null) modifs.quitter(c.id());
    }

    /* ================================================================ plat en main (HUD) */

    private void platEnMain() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!NourritureCanal.ecoute(p)) continue;
            ItemStack m = p.getInventory().getItemInMainHand();
            String cle = "";
            NourritureCanal.J j = null;
            if (NourritureItems.PLAT.equals(NourritureItems.genre(m))) {
                NourritureConfig.Plat pl = cfg.plats.get(NourritureItems.recette(m));
                long reste = Math.max(0, NourritureItems.chaud(m) - now) / 1000;
                cle = NourritureItems.recette(m) + "|" + (reste > 0 ? reste / 30 : -1);
                j = new NourritureCanal.J().s("t", "main").s("nom", pl == null ? "Plat" : pl.nom())
                        .s("ic", pl == null ? "bol" : pl.icone()).n("chaud", reste);
            }
            String avant = mainEnvoyee.getOrDefault(p.getUniqueId(), "");
            if (cle.equals(avant)) continue;
            mainEnvoyee.put(p.getUniqueId(), cle);
            NourritureCanal.envoyer(p, j != null ? j : new NourritureCanal.J().s("t", "main"));
        }
    }

    /* ================================================================ échoppe : onglet Vivres */

    /** {@code "vivres":[…]} pour l'état de l'échoppe : rations pour tous, paniers pour les cuisiniers. */
    public String vivresJson(Player p, ShinobiCharacter c) {
        StringBuilder b = new StringBuilder("[");
        boolean cuisinier = entreprises.cuisinier(c);
        for (NourritureConfig.Vivre v : cfg.vivres.values()) {
            if (v.cuisinierSeulement() && !cuisinier) continue;
            if (b.length() > 1) b.append(',');
            b.append("{\"id\":\"").append(NourritureCanal.esc(v.id())).append("\",\"name\":\"")
                    .append(NourritureCanal.esc(v.nom() + (v.quantite() > 1 ? " ×" + v.quantite() : ""))).append("\",\"price\":")
                    .append(v.prix()).append('}');
        }
        return b.append(']').toString();
    }

    /** Achat d'un vivre ; renvoie le message de l'échoppe. */
    public String acheterVivre(Player p, ShinobiCharacter c, String id) {
        NourritureConfig.Vivre v = cfg.vivres.get(id);
        if (v == null) return "§cCe vivre n'est pas en vente.";
        if (v.cuisinierSeulement() && !entreprises.cuisinier(c)) return "§cRéservé aux cuisiniers d'un restaurant.";
        ItemStack it;
        if (v.recette() != null) {
            NourritureConfig.Plat pl = cfg.plats.get(v.recette());
            if (pl == null) return "§cRecette inconnue.";
            it = NourritureItems.panier(pl, v.quantite());
        } else it = NourritureItems.ration(cfg.ration, v.quantite());
        String refus = plugin.rpInventory() == null ? null : plugin.rpInventory().refusReception(p, it);
        if (refus != null) return "§c" + Character.toUpperCase(refus.charAt(0)) + refus.substring(1) + ".";
        // Monnaie (15/10) : payé avec la bourse.
        String pb;
        if (plugin.monnaie() != null) pb = plugin.monnaie().bourses().payer(p, v.prix(), "achat « " + v.nom() + " »");
        else {
            RyoJournal.contexte("achat « " + v.nom() + " »", p);
            pb = c.trySpendRyo(v.prix()) ? null : "Pas assez de ryo (" + v.prix() + ").";
            RyoJournal.fin();
            plugin.characters().save(c);
        }
        if (pb != null) return "§c" + pb;
        donnerSansBruit(p, it);
        Notifs.objet(p, true, it, v.quantite(), v.prix() + " ryo");
        Journal.entree(Categorie.OBJET, "ACHAT").acteur(p).detail(v.quantite() + " × " + v.nom() + " · " + v.prix() + " ryo").ecrire();
        return "§a" + v.nom() + " acheté" + (v.quantite() > 1 ? "s" : "") + " ! §7(-" + v.prix() + " ryo)";
    }

    /* ================================================================ pilule du soldat */

    /** Pourquoi la pilule ne peut pas être prise maintenant, ou null. */
    public String refusPilule(Player p) {
        if (com.reborn.shinobicore.staff.outils.ModeStaff.horsJeu(p)) return "mode staff";
        long r = modifs.reste(p.getUniqueId(), "pilule_recharge");
        if (r > 0) return "encore " + Math.max(1, (r + 59_999) / 60_000) + " min avant la suivante";
        if (satiete.depuisCoup(p.getUniqueId()) < cfg.piluleRefusCoupMs) return "pas juste après un coup";
        return null;
    }

    /** Effets de la pilule (v3) : satiété, endurance (puis Fatigue en contrecoup), délai de 10 min. */
    public void pilulePrise(Player p) {
        satiete.ajouter(p, cfg.piluleSatiete, 100);
        modifs.poser(p, "pilule_soldat", cfg.piluleIntensite, cfg.piluleDureeMs, "Pilule du soldat");
        modifs.poser(p, "pilule_recharge", 1, cfg.piluleRechargeMs, "Pilule du soldat");
        TypeModificateur t = modifs.type("pilule_soldat");
        info(p, "pilule", "Pilule du soldat", t == null ? null : t.valeurAffichee(cfg.piluleIntensite) + " · "
                + cfg.piluleDureeMs / 60_000 + " min, puis Fatigue");
        Journal.entree(Categorie.OBJET, "PILULE").acteur(p).detail("Pilule du soldat").lieu(p.getLocation()).ecrire();
    }
}
