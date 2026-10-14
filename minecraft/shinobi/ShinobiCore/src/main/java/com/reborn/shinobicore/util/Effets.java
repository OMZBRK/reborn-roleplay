package com.reborn.shinobicore.util;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Effets physiques posés <b>par source</b> (lot de correctifs urgents, docs/technique/ETATS_JOUEUR.md §9 étape 0).
 *
 * <ul>
 *   <li><b>Retirer seulement ce qu'on a posé</b> : un {@code removePotionEffect(type)} effaçait aussi les effets des
 *       autres systèmes (le Kai levait l'étourdi de combat, un changement de personnage levait l'écran noir du staff).
 *       {@link #retirerSiSien} ne retire l'effet que s'il a la signature de celui qu'on a posé.</li>
 *   <li><b>Lenteurs d'état en modificateurs nommés</b> ({@link #lenteur}) : un modificateur <i>transitoire</i> n'est
 *       jamais écrit dans le NBT du joueur (rien ne reste après un crash), se retire par sa clé sans toucher aux
 *       autres, et se compose avec l'allure ({@code reborn:allure}) au lieu de l'écraser.</li>
 * </ul>
 * L'applicateur unique de l'étape 5 remplacera ces appels ; d'ici là, tout nouvel effet d'état passe par ici.
 */
public final class Effets {

    private Effets() { }

    /**
     * Retire {@code type} seulement si l'effet actif ressemble à celui qu'on a posé : même amplificateur, durée finie
     * et au plus {@code maxTicks}. Un effet plus fort, plus long ou infini posé ailleurs reste en place.
     */
    public static void retirerSiSien(LivingEntity e, PotionEffectType type, int amplifier, int maxTicks) {
        if (e == null || type == null) return;
        PotionEffect pe = e.getPotionEffect(type);
        if (pe == null || pe.isInfinite() || pe.getAmplifier() != amplifier || pe.getDuration() > maxTicks) return;
        e.removePotionEffect(type);
    }

    /** Retire {@code type} seulement s'il est infini (effet tenu par un état en mémoire, levé à la fin de l'état). */
    public static void retirerInfini(LivingEntity e, PotionEffectType type) {
        if (e == null || type == null) return;
        PotionEffect pe = e.getPotionEffect(type);
        if (pe != null && pe.isInfinite()) e.removePotionEffect(type);
    }

    /**
     * Facteur de vitesse d'une lenteur vanilla de niveau {@code niveau} (I = 1) : −15 % par niveau, 0 dès le
     * niveau 7. Fonction pure (testable sans serveur).
     */
    public static double facteurLenteur(int niveau) {
        return Math.max(0.0, 1.0 - 0.15 * Math.max(0, niveau));
    }

    /**
     * Lenteur d'état en modificateur nommé sur {@code MOVEMENT_SPEED} : {@code niveau} comme une potion (I = −15 %,
     * VII et plus = immobile). Idempotent : remplace le modificateur de même clé.
     */
    public static void lenteur(LivingEntity e, NamespacedKey cle, int niveau) {
        poser(e, Attribute.MOVEMENT_SPEED, cle, facteurLenteur(niveau) - 1.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    /** Pose (ou remplace) un modificateur transitoire nommé. {@code montant} 0 = le retirer. */
    public static void poser(LivingEntity e, Attribute attribut, NamespacedKey cle, double montant,
                             AttributeModifier.Operation op) {
        if (e == null) return;
        AttributeInstance a = e.getAttribute(attribut);
        if (a == null) return;
        enlever(a, cle);
        if (montant == 0.0) return;
        a.addTransientModifier(new AttributeModifier(cle, montant, op, EquipmentSlotGroup.ANY));
    }

    /** Retire le modificateur de clé {@code cle} (et lui seul). */
    public static void retirer(LivingEntity e, Attribute attribut, NamespacedKey cle) {
        if (e == null) return;
        enlever(e.getAttribute(attribut), cle);
    }

    private static void enlever(AttributeInstance a, NamespacedKey cle) {
        if (a == null) return;
        for (AttributeModifier m : a.getModifiers().toArray(new AttributeModifier[0])) {
            if (cle.equals(m.getKey())) a.removeModifier(m);
        }
    }
}
