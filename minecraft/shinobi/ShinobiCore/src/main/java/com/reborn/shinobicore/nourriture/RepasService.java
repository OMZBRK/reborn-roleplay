package com.reborn.shinobicore.nourriture;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.modificateur.ModificateurRegistry;
import com.reborn.shinobicore.modificateur.TypeModificateur;
import com.reborn.shinobicore.staff.outils.ModeStaff;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Manger</b> (docs/gameplay/NOURRITURE.md §6) : clic droit avec un plat (ou une ration) en main. Le repas dure 30 s à
 * 1 min selon le plat, deux fois plus vite assis ; la satiété monte peu à peu ; un coup fige le repas 30 s ; ranger le
 * plat (changer d'objet en main) le laisse <b>entamé</b> (la part mangée est gardée sur l'objet) ; pas de progrès en
 * courant ; effets à la dernière bouchée, réduits de moitié si le plat était froid. Le bol vide reste dans la main.
 */
public final class RepasService implements ModificateurRegistry.Contributeur {

    private static final class Repas {
        final NourritureConfig.Plat plat;
        final boolean ration, chaud;
        final int slot;
        final ItemStack modele;
        double fait;          // secondes mangées
        double gagne;         // satiété déjà gagnée
        long pauseJusqua;
        int vitesse = 1;
        boolean trot;

        Repas(NourritureConfig.Plat plat, boolean ration, boolean chaud, int slot, ItemStack modele) {
            this.plat = plat;
            this.ration = ration;
            this.chaud = chaud;
            this.slot = slot;
            this.modele = modele;
        }
    }

    private final ShinobiCore plugin;
    private final NourritureModule module;
    private final Map<UUID, Repas> enCours = new HashMap<>();

    RepasService(ShinobiCore plugin, NourritureModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public boolean mange(UUID compte) { return enCours.containsKey(compte); }

    /** Satiété encore à venir du repas en cours (pour l'or pâle de la barre). */
    double aVenir(Player p, double sat) {
        Repas r = enCours.get(p.getUniqueId());
        if (r == null) return 0;
        double total = r.plat.satiete() * (1 - NourritureItems.entame(r.modele));
        double reste = Math.max(0, total - r.gagne);
        return Math.max(0, Math.min(r.plat.plafond(), sat + reste) - sat);
    }

    @Override
    public String raison(UUID compte, String capacite) {
        return "SPRINTER".equals(capacite) && enCours.containsKey(compte) ? "en train de manger" : null;
    }

    /* ================================================================ début */

    /** Clic droit avec un plat ou une ration ; renvoie vrai si l'événement doit être annulé. */
    boolean commencer(Player p, ItemStack main, String genre) {
        NourritureConfig k = module.cfg();
        UUID id = p.getUniqueId();
        ShinobiCharacter c = plugin.characters().getActive(id);
        if (c == null) return false;
        if (ModeStaff.horsJeu(p)) { module.refus(p, "Mode staff", "aucune mécanique RP"); return true; }
        if (enCours.containsKey(id)) return true;
        if (plugin.ko() != null && plugin.ko().isKo(id)) return true;
        if (module.cuisine() != null && module.cuisine().prepare(id)) {
            module.refus(p, "Occupé", "tu es au fourneau");
            return true;
        }
        NourritureConfig.Plat plat;
        boolean ration = NourritureItems.RATION.equals(genre);
        if (ration) plat = k.ration;
        else {
            plat = k.plats.get(NourritureItems.recette(main));
            if (plat == null) { module.refus(p, "Plat inconnu", "recette retirée de la configuration"); return true; }
        }
        double sat = module.satiete().satiete(p);
        if (sat >= plat.plafond() - 0.5) {
            module.refus(p, ration ? "Plus faim pour une ration" : "Rassasié",
                    ration ? "elle ne monte pas au-dessus de " + (int) plat.plafond() + " %" : "garde-le pour plus tard");
            return true;
        }
        long chaud = NourritureItems.chaud(main);
        boolean estChaud = !ration && chaud > System.currentTimeMillis();
        ItemStack modele = main.clone();
        modele.setAmount(1);
        Repas r = new Repas(plat, ration, estChaud, p.getInventory().getHeldItemSlot(), modele);
        r.fait = plat.dureeS() * NourritureItems.entame(main);
        enCours.put(id, r);
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.4f, 1.0f);
        envoyer(p, r);
        return true;
    }

    /* ================================================================ tick (0,5 s) */

    void tick() {
        NourritureConfig k = module.cfg();
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(enCours.keySet())) {
            Repas r = enCours.get(id);
            Player p = plugin.getServer().getPlayer(id);
            if (p == null || !p.isOnline()) { enCours.remove(id); continue; }
            ItemStack main = p.getInventory().getItem(r.slot);
            boolean meme = p.getInventory().getHeldItemSlot() == r.slot && main != null && main.isSimilar(r.modele);
            boolean ko = plugin.ko() != null && plugin.ko().isKo(id);
            if (!meme || ko || ModeStaff.horsJeu(p)) { ranger(p, r, main); continue; }
            if (now < r.pauseJusqua) continue;
            boolean assis = p.isInsideVehicle() || (plugin.posture() != null && plugin.posture().isPosed(p));
            int v = assis ? (int) Math.max(1, Math.round(k.assisMult)) : 1;
            boolean trot = p.isSprinting();
            if (v != r.vitesse || trot != r.trot) { r.vitesse = v; r.trot = trot; envoyer(p, r); }
            if (trot) continue;   // on mange en marchant lentement, pas en courant
            double pas = 0.5 * v;
            double part = Math.min(pas, r.plat.dureeS() - r.fait);
            r.fait += part;
            double gain = r.plat.satiete() * part / r.plat.dureeS();
            r.gagne += module.satiete().ajouter(p, gain, r.plat.plafond());
            if (r.fait >= r.plat.dureeS() - 1e-6) finir(p, r, main);
            else if (((int) (r.fait * 2)) % 6 == 0) envoyer(p, r);
        }
    }

    /** Un coup reçu : le repas se fige (rien n'est perdu). */
    void coup(Player p) {
        Repas r = enCours.get(p.getUniqueId());
        if (r == null) return;
        r.pauseJusqua = System.currentTimeMillis() + module.cfg().pauseRepasMs;
        envoyer(p, r);
    }

    /** Le plat a quitté la main (ou KO, mode staff) : il reste entamé, sans effet. */
    private void ranger(Player p, Repas r, ItemStack actuel) {
        enCours.remove(p.getUniqueId());
        double part = Math.min(0.99, r.fait / r.plat.dureeS());
        // retrouver la pile d'origine (même emplacement) pour y noter la part mangée
        ItemStack slot = p.getInventory().getItem(r.slot);
        if (part > 0.01 && slot != null && slot.isSimilar(r.modele)) {
            ItemStack entame = NourritureItems.avecEntame(r.modele, part);
            if (slot.getAmount() == 1) p.getInventory().setItem(r.slot, entame);
            else {
                slot.setAmount(slot.getAmount() - 1);
                p.getInventory().setItem(r.slot, slot);
                module.donnerSansBruit(p, entame);
            }
        }
        NourritureCanal.envoyer(p, new NourritureCanal.J().s("t", "repas_fin").b("ok", false));
        if (part > 0.01) module.info(p, "bol", "Repas rangé", "entamé, à finir plus tard");
    }

    private void finir(Player p, Repas r, ItemStack main) {
        enCours.remove(p.getUniqueId());
        NourritureConfig k = module.cfg();
        // l'objet part ; le bol vide (s'il y en a un) reste dans la main
        if (main.getAmount() <= 1) {
            p.getInventory().setItem(r.slot, r.plat.reste() != null && r.plat.reste().equals("bol_vide")
                    ? NourritureItems.bolVide() : null);
        } else {
            main.setAmount(main.getAmount() - 1);
            p.getInventory().setItem(r.slot, main);
            if ("bol_vide".equals(r.plat.reste())) module.donnerSansBruit(p, NourritureItems.bolVide());
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.35f, 1.1f);
        List<String> faits = new ArrayList<>();
        for (NourritureConfig.Effet e : r.plat.effets()) {
            TypeModificateur t = module.modifs().type(e.type());
            if (t == null) continue;
            double x = e.intensite();
            if (!r.chaud) x = t.neutre() + (x - t.neutre()) * k.effetsFroid;
            if (module.modifs().poser(p, e.type(), x, e.dureeMs(), r.plat.nom())) {
                String v = t.valeurAffichee(x);
                module.info(p, t.icone(), t.nom() + (v.isEmpty() ? "" : " " + v), e.dureeMs() / 60_000 + " min");
                faits.add(t.nom() + " " + v + " " + e.dureeMs() / 1000 + " s");
            }
        }
        double sat = module.satiete().satiete(p);
        if (sat >= 99.5) module.info(p, "satiete_prolongee", "Rassasié", "100 %");
        NourritureCanal.envoyer(p, new NourritureCanal.J().s("t", "repas_fin").b("ok", true));
        Journal.entree(Categorie.OBJET, "REPAS").acteur(p)
                .detail(r.plat.nom() + (r.ration ? "" : r.chaud ? " · chaud" : " · froid") + " · +"
                        + Math.round(r.gagne) + " % → " + Math.round(sat) + " %"
                        + (faits.isEmpty() ? "" : " · " + String.join(", ", faits)))
                .lieu(p.getLocation()).ecrire();
    }

    void envoyer(Player p, Repas r) {
        long pause = Math.max(0, r.pauseJusqua - System.currentTimeMillis());
        NourritureCanal.envoyer(p, new NourritureCanal.J().s("t", "repas").s("nom", r.plat.nom())
                .s("ic", r.plat.icone()).n("dur", r.plat.dureeS()).n("fait", r.fait).n("vit", r.trot ? 0 : r.vitesse)
                .n("pause", Math.ceil(pause / 1000.0)).b("froid", !r.ration && !r.chaud));
    }

    void oublier(UUID id) { enCours.remove(id); }
}
