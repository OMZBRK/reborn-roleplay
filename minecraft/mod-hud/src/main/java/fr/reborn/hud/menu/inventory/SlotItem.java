package fr.reborn.hud.menu.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item <b>rendu</b> dans la Sacoche, reconstruit depuis le JSON serveur. Depuis
 * le passage « base = hotbar vanilla », le contenu du sac est fait de vrais
 * items MC/Nexo : le serveur n'envoie que ce qu'il faut pour reconstruire un
 * {@link ItemStack} affichable ({@code mat} = matériau vanilla de base,
 * {@code model} = {@code item_model} custom Nexo si présent, {@code count},
 * {@code name}), et le client rend le vrai modèle via le resource pack.
 */
public final class SlotItem {

    public final String mat;      // "minecraft:paper", "minecraft:diamond_sword"…
    public final String model;    // "nexo:sac_sacoche" ou null
    public final int count;
    public final String name;     // nom affiché ou null
    public final double weight;
    public final String desc;     // description (lore), \n = retours ligne, ou null
    public final String rarity;   // "Rare"… ou null
    public final String actionId; // action contextuelle ("tirage"…) ou null
    public final String actionLabel; // libellé du bouton d'action, ou null

    private ItemStack cached;

    public SlotItem(String mat, String model, int count, String name, double weight,
                    String desc, String rarity, String actionId, String actionLabel) {
        this.mat = mat;
        this.model = empty(model);
        this.count = Math.max(1, count);
        this.name = empty(name);
        this.weight = weight;
        this.desc = empty(desc);
        this.rarity = empty(rarity);
        this.actionId = empty(actionId);
        this.actionLabel = empty(actionLabel);
    }

    /** Raccourci sans métadonnées (test solo). */
    public SlotItem(String mat, String model, int count, String name, double weight) {
        this(mat, model, count, name, weight, null, null, null, null);
    }

    private static String empty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    public boolean hasAction() {
        return actionId != null;
    }

    /** Reconstruit (et met en cache) l'ItemStack affichable. */
    public ItemStack toStack() {
        if (cached != null) return cached;
        Item base;
        if (model != null) {
            base = Items.PAPER; // les items Nexo sont des PAPER habillés par item_model
        } else {
            Identifier mid = safeId(mat);
            base = mid != null ? BuiltInRegistries.ITEM.getOptional(mid).orElse(Items.PAPER) : Items.PAPER;
        }
        ItemStack s = new ItemStack(base, count);
        if (model != null) {
            Identifier mm = safeId(model);
            if (mm != null) s.set(DataComponents.ITEM_MODEL, mm);
        }
        if (name != null) s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        cached = s;
        return s;
    }

    /** Nom du tier de sac si cet item est un objet-sac équipable, sinon null. */
    public String bagTierName() {
        if (model == null) return null;
        BagTiers t = BagTiers.fromModel(model);
        return t != null ? t.name() : null;
    }

    public boolean isBag() {
        return bagTierName() != null;
    }

    private static Identifier safeId(String s) {
        try {
            return Identifier.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
