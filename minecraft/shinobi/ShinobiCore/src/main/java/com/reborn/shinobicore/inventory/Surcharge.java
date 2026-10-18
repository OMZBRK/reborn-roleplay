package com.reborn.shinobicore.inventory;

import com.reborn.shinobicore.api.InventoryService;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Surcharge par <b>paliers</b> (décision du 2026-10-13), appliquée par <b>modificateurs d'attributs</b> — plus de
 * potions (ni icône, ni particules, ni fatigue) :
 * <ul>
 *   <li>≤ 100 % : normal ;</li>
 *   <li>100-115 % <b>Chargé</b> : vitesse 90 → 70 %, plus de sprint (le bonus de sprint est compensé) ;</li>
 *   <li>115-130 % <b>Surchargé</b> : vitesse 70 → 35 %, plus de saut ; la mobilité ninja est coupée
 *       ({@link InventoryService#mobiliteBloquee}) ;</li>
 *   <li>au-delà de 130 % <b>Immobile</b> : vitesse 0 ; on peut encore lâcher, ranger, parler.</li>
 * </ul>
 * Seuils et vitesses : {@code inventaire.surcharge.*}. Une notification seulement au <b>changement de palier</b>.
 */
final class Surcharge {

    private static final NamespacedKey CLE = new NamespacedKey("reborn", "surcharge");
    /** Bonus de vitesse du sprint vanilla (modificateur ADD_MULTIPLIED_TOTAL +0,3). */
    private static final double SPRINT = 1.3;

    private final Map<UUID, Integer> paliers = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> appliques = new ConcurrentHashMap<>();

    int palier(UUID id) {
        return paliers.getOrDefault(id, InventoryService.NORMAL);
    }

    /** Palier pour un ratio poids / poids max. */
    static int palierPour(double r, InventaireConfig c) {
        if (r <= c.seuilCharge) return InventoryService.NORMAL;
        if (r <= c.seuilSurcharge) return InventoryService.CHARGE;
        if (r <= c.seuilImmobile + 1e-9) return InventoryService.SURCHARGE;
        return InventoryService.IMMOBILE;
    }

    /**
     * Recalcule le palier et applique vitesse / saut. Renvoie l'ancien palier (pour la notification), ou -1 si
     * inchangé.
     */
    int appliquer(Player p, double cur, double max, InventaireConfig c, boolean actif) {
        UUID id = p.getUniqueId();
        int nouveau = !actif || max <= 0 ? InventoryService.NORMAL : palierPour(cur / max, c);
        double vit = 1.0, saut = 1.0;
        if (actif && max > 0) {
            double r = cur / max;
            switch (nouveau) {
                case InventoryService.CHARGE -> {
                    vit = lerp(c.vitChargeDe, c.vitChargeA, t(r, c.seuilCharge, c.seuilSurcharge));
                    saut = c.sautCharge;
                }
                case InventoryService.SURCHARGE -> {
                    vit = lerp(c.vitSurchargeDe, c.vitSurchargeA, t(r, c.seuilSurcharge, c.seuilImmobile));
                    saut = c.sautSurcharge;
                }
                case InventoryService.IMMOBILE -> {
                    vit = c.vitImmobile;
                    saut = c.sautImmobile;
                }
                default -> { }
            }
            // Pas de sprint dès « Chargé » : le bonus de sprint est annulé (le mod coupe aussi le sprint côté client).
            if (nouveau >= InventoryService.CHARGE && !(nouveau == InventoryService.CHARGE && c.sprintCharge)
                && p.isSprinting()) {
                vit /= SPRINT;
            }
        }
        poser(p, vit, saut);
        Integer avant = paliers.put(id, nouveau);
        int a = avant == null ? InventoryService.NORMAL : avant;
        return a == nouveau ? -1 : a;
    }

    /** Retire les modificateurs (déconnexion, mode créatif, extinction). */
    void retirer(Player p) {
        boolean avait = paliers.remove(p.getUniqueId()) != null | appliques.remove(p.getUniqueId()) != null;
        if (!avait) return;
        enlever(p.getAttribute(Attribute.MOVEMENT_SPEED));
        enlever(p.getAttribute(Attribute.JUMP_STRENGTH));
    }

    /** Derniers facteurs calculés ({vitesse, saut}), ou null. */
    double[] facteurs(UUID id) {
        double[] f = appliques.get(id);
        return f == null ? null : f.clone();
    }

    private void poser(Player p, double vit, double saut) {
        double[] prec = appliques.get(p.getUniqueId());
        if (prec != null && Math.abs(prec[0] - vit) < 0.005 && Math.abs(prec[1] - saut) < 0.005) return;
        appliques.put(p.getUniqueId(), new double[] {vit, saut});
        // Étape 5 d'ETATS_JOUEUR : l'applicateur unique pose vitesse et saut (reborn:etat_*) ; on ne fait que calculer.
        if (com.reborn.shinobicore.etat.Applicateur.actif()) {
            enlever(p.getAttribute(Attribute.MOVEMENT_SPEED));
            enlever(p.getAttribute(Attribute.JUMP_STRENGTH));
            return;
        }
        modifier(p.getAttribute(Attribute.MOVEMENT_SPEED), vit);
        modifier(p.getAttribute(Attribute.JUMP_STRENGTH), saut);
    }

    private static void modifier(AttributeInstance a, double facteur) {
        if (a == null) return;
        enlever(a);
        if (Math.abs(facteur - 1.0) < 1e-4) return;
        a.addTransientModifier(new AttributeModifier(CLE, Math.max(-1.0, facteur - 1.0),
            AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private static void enlever(AttributeInstance a) {
        if (a == null) return;
        try {
            a.removeModifier(CLE);
        } catch (Throwable ignored) { }
    }

    private static double t(double r, double a, double b) {
        return b <= a ? 1.0 : Math.max(0, Math.min(1, (r - a) / (b - a)));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Notification au changement de palier (une seule ligne, en bas à droite). */
    static void annoncer(Player p, int avant, int apres) {
        switch (apres) {
            case InventoryService.CHARGE -> {
                if (avant > apres) Notifs.msg(p, "info", "軽", "Moins chargé", "tu peux de nouveau sauter");
                else Notifs.msg(p, "palier", "軽", "Tu es chargé", "plus de course");
            }
            case InventoryService.SURCHARGE -> {
                if (avant > apres) Notifs.msg(p, "palier", "重", "Tu es surchargé", "tu peux de nouveau avancer");
                else Notifs.msg(p, "palier", "重", "Tu es surchargé", "tu ralentis");
            }
            case InventoryService.IMMOBILE -> Notifs.msg(p, "refus", "止", "Trop lourd", "tu ne peux plus avancer");
            default -> Notifs.msg(p, "info", null, "Tu peux de nouveau courir", null);
        }
    }
}
