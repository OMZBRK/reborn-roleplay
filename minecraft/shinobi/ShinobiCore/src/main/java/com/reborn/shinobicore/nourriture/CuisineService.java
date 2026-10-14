package com.reborn.shinobicore.nourriture;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.entreprise.Entreprise;
import com.reborn.shinobicore.inventory.Notifs;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.staff.outils.ModeStaff;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Cuisiner</b> (docs/gameplay/NOURRITURE.md §4) : un cuisinier (membre d'une entreprise RESTAURANT) tient un panier
 * d'ingrédients et fait clic droit sur un fourneau de son restaurant (poste marqué par {@code /entreprise poste
 * ajouter}) ; 15 s pendant lesquelles il parle et bouge dans sa cuisine ; s'éloigner de plus de 4 blocs, être frappé,
 * tomber KO interrompt (le panier n'est pas perdu). À la fin : un panier en moins, deux plats chauds.
 */
public final class CuisineService {

    private record Prep(NourritureConfig.Plat plat, Location poste, String entreprise, long debut, long finMs) {}

    private final ShinobiCore plugin;
    private final NourritureModule module;
    private final Map<UUID, Prep> enCours = new HashMap<>();

    CuisineService(ShinobiCore plugin, NourritureModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public boolean prepare(UUID compte) { return enCours.containsKey(compte); }

    /** Clic droit avec un panier sur {@code cible} ; renvoie vrai si l'événement doit être annulé. */
    boolean commencer(Player p, ItemStack panier, Location cible, int tolerance) {
        NourritureConfig k = module.cfg();
        var trouve = module.entreprises().posteA(cible, tolerance);
        if (trouve == null) return false;                       // pas un fourneau : clic normal
        Entreprise e = trouve.getKey();
        if (e.type() != Entreprise.Type.RESTAURANT) return false;
        UUID id = p.getUniqueId();
        ShinobiCharacter c = plugin.characters().getActive(id);
        if (c == null) return true;
        if (ModeStaff.horsJeu(p)) { module.refus(p, "Mode staff", "aucune mécanique RP"); return true; }
        if (enCours.containsKey(id)) return true;
        if (module.repas() != null && module.repas().mange(id)) { module.refus(p, "Occupé", "tu manges"); return true; }
        if ((plugin.ko() != null && plugin.ko().isKo(id)) || (plugin.douleur() != null && plugin.douleur().isInPain(id))) {
            module.refus(p, "Trop mal en point", "pour cuisiner");
            return true;
        }
        boolean autorise = k.posteDeSonEntreprise ? e.membre(c.id()) != null : module.entreprises().cuisinier(c);
        if (!autorise) {
            module.refus(p, "Pas ton fourneau", k.posteDeSonEntreprise ? "réservé à « " + e.nom() + " »" : "réservé aux cuisiniers");
            return true;
        }
        NourritureConfig.Plat plat = k.plats.get(NourritureItems.recette(panier));
        if (plat == null) { module.refus(p, "Recette inconnue", "panier d'une recette retirée"); return true; }
        long now = System.currentTimeMillis();
        Location centre = trouve.getValue().centre(cible.getWorld());
        enCours.put(id, new Prep(plat, centre, e.nom(), now, now + (long) (k.prepS * 1000)));
        p.playSound(centre, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 0.6f, 1.2f);
        NourritureCanal.envoyer(p, new NourritureCanal.J().s("t", "prep").s("nom", plat.nom()).s("ic", "vapeur")
                .n("dur", k.prepS).n("fait", 0));
        return true;
    }

    void tick() {
        NourritureConfig k = module.cfg();
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(enCours.keySet())) {
            Prep pr = enCours.get(id);
            Player p = plugin.getServer().getPlayer(id);
            if (p == null || !p.isOnline()) { enCours.remove(id); continue; }
            if (p.getWorld() != pr.poste().getWorld() || p.getLocation().distance(pr.poste()) > k.distanceCuisine) {
                interrompre(p, "trop loin du fourneau");
                continue;
            }
            if ((plugin.ko() != null && plugin.ko().isKo(id)) || ModeStaff.horsJeu(p)) { interrompre(p, null); continue; }
            int slot = trouverPanier(p, pr.plat().id());
            if (slot < 0) { interrompre(p, "plus de panier"); continue; }
            if (now % 3000 < 500) {
                pr.poste().getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, pr.poste().clone().add(0, 0.6, 0),
                        1, 0.1, 0.05, 0.1, 0.01);
            }
            if (now >= pr.finMs()) finir(p, pr, slot);
        }
    }

    void coup(Player p) {
        if (enCours.containsKey(p.getUniqueId())) interrompre(p, "frappé");
    }

    private void interrompre(Player p, String raison) {
        enCours.remove(p.getUniqueId());
        NourritureCanal.envoyer(p, new NourritureCanal.J().s("t", "prep_fin").b("ok", false));
        if (raison != null) module.refus(p, "Préparation interrompue", raison + " — le panier est gardé");
    }

    private void finir(Player p, Prep pr, int slot) {
        enCours.remove(p.getUniqueId());
        NourritureConfig k = module.cfg();
        ItemStack panier = p.getInventory().getItem(slot);
        if (panier == null) return;
        if (panier.getAmount() <= 1) p.getInventory().setItem(slot, null);
        else { panier.setAmount(panier.getAmount() - 1); p.getInventory().setItem(slot, panier); }
        ItemStack plats = module.plat(pr.plat(), System.currentTimeMillis() + k.chaudMs, k.platsParPanier);
        module.donnerSansBruit(p, plats);
        p.playSound(p.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.BLOCKS, 0.8f, 1.0f);
        Notifs.objet(p, true, plats, k.platsParPanier, "chaud");
        NourritureCanal.envoyer(p, new NourritureCanal.J().s("t", "prep_fin").b("ok", true));
        Journal.entree(Categorie.OBJET, "PREPARATION").acteur(p)
                .detail(k.platsParPanier + " × " + pr.plat().nom() + " · « " + pr.entreprise() + " »").lieu(pr.poste()).ecrire();
    }

    /** Emplacement (0-8) d'un panier de cette recette : la main d'abord. */
    private static int trouverPanier(Player p, String recette) {
        int main = p.getInventory().getHeldItemSlot();
        if (estPanier(p.getInventory().getItem(main), recette)) return main;
        for (int i = 0; i < 9; i++) if (estPanier(p.getInventory().getItem(i), recette)) return i;
        return -1;
    }

    private static boolean estPanier(ItemStack it, String recette) {
        return NourritureItems.PANIER.equals(NourritureItems.genre(it)) && recette.equals(NourritureItems.recette(it));
    }

    void oublier(UUID id) { enCours.remove(id); }
}
