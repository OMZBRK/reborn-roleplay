package com.reborn.shinobicore.inventory;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Sac RP d'un personnage — <b>modèle « base = hotbar vanilla »</b>. Les 9 cases
 * de base ne sont PAS stockées ici : ce sont les 9 slots réels de la barre
 * d'action du joueur ({@link #HOTBAR_SLOTS}). Ce que ce sac stocke, c'est
 * <b>l'espace supplémentaire</b> débloqué par un {@link BagTier} équipé
 * ({@link #storage}, taille = {@code bagTier.extraSlots}), plus le sac porté
 * ({@link #wornBag}) et les cosmétiques équipés.
 *
 * <p>Contrairement à l'ancien modèle data-driven, le contenu est fait de
 * <b>vrais {@link ItemStack}</b> (déplaçables entre la barre et le sac), ce qui
 * permet la cohérence avec l'inventaire IG + les items Nexo (fouille INRP).
 */
public final class RpBag {

    /** Cases « sur soi » = la barre d'action vanilla (jamais stockées ici). */
    public static final int HOTBAR_SLOTS = 9;
    /** Limite de poids de base (kg), avant tout sac. */
    public static final double BASE_WEIGHT = 12.0;

    private BagTier bagTier;
    private ItemStack[] storage;                 // espace débloqué par le sac
    private ItemStack wornBag;                   // l'objet-sac équipé (à rendre), ou null
    private final EnumMap<CosmeticSlot, ItemStack> equipped = new EnumMap<>(CosmeticSlot.class);
    /**
     * Placement 3D appliqué par cosmétique, indexé par id de MODÈLE Nexo
     * (ex. {@code nexo:masque_x}) → chaîne compacte {@code ANCHOR,px,..,scale}
     * (opaque côté serveur, (dé)sérialisée par le client). Diffusé aux autres
     * joueurs pour qu'ils voient le placement choisi par le porteur.
     */
    private final Map<String, String> cosTransforms = new java.util.HashMap<>();

    public RpBag() { this(BagTier.NONE); }

    public RpBag(BagTier tier) {
        this.bagTier = tier != null ? tier : BagTier.NONE;
        this.storage = new ItemStack[this.bagTier.extraSlots];
    }

    // ─────────── Capacité ───────────
    public BagTier bagTier() { return bagTier; }
    public int extraSlots() { return bagTier.extraSlots; }
    public double maxWeight() { return BASE_WEIGHT + bagTier.extraWeight; }
    public String bagName() { return bagTier.displayName; }

    public ItemStack wornBag() { return wornBag; }
    public ItemStack[] storage() { return storage; }
    public Map<CosmeticSlot, ItemStack> equipped() { return equipped; }
    public Map<String, String> cosTransforms() { return cosTransforms; }

    public ItemStack getStorage(int i) {
        return (i >= 0 && i < storage.length) ? storage[i] : null;
    }

    public void setStorage(int i, ItemStack s) {
        if (i < 0 || i >= storage.length) return;
        storage[i] = (s == null || s.getType().isAir()) ? null : s;
    }

    public void setWornBag(ItemStack s) {
        this.wornBag = (s == null || s.getType().isAir()) ? null : s;
    }

    // ─────────── Poids (côté sac ; le hotbar est ajouté par InventoryManager) ───────────
    public double storageWeight() {
        double w = 0;
        for (ItemStack s : storage) w += RpWeights.of(s);
        for (ItemStack s : equipped.values()) w += RpWeights.of(s);
        return w;
    }

    /**
     * Change le tier et redimensionne le stockage en préservant les items.
     * Renvoie les items qui ne tiennent plus (débordement — cas d'un tier plus
     * petit) pour que l'appelant les rende au joueur.
     */
    public List<ItemStack> setTier(BagTier tier) {
        BagTier nt = tier != null ? tier : BagTier.NONE;
        ItemStack[] old = storage;
        ItemStack[] ns = new ItemStack[nt.extraSlots];
        List<ItemStack> overflow = new ArrayList<>();
        int j = 0;
        for (ItemStack s : old) {
            if (s == null) continue;
            if (j < ns.length) ns[j++] = s;
            else overflow.add(s);
        }
        this.bagTier = nt;
        this.storage = ns;
        return overflow;
    }
}
