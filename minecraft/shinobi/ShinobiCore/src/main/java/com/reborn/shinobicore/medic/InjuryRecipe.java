package com.reborn.shinobicore.medic;

import com.reborn.shinobicore.ko.injury.InjuryType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Maps each {@link InjuryType} to the medicines required to treat it.
 *
 * <p>The mapping is intentionally short and prescriptive — one to
 * three items per injury — so the in-game flow stays brisk and so
 * the encyclopedia book reads cleanly:
 *
 * <ul>
 *   <li>{@link InjuryType#HEMATOME}  → Gel d'Arnica</li>
 *   <li>{@link InjuryType#BRULURE}   → Biafine</li>
 *   <li>{@link InjuryType#OS_CASSE}  → Plâtre + Antalgique</li>
 *   <li>{@link InjuryType#INFECTION} → Amoxicilline + Bétadine</li>
 *   <li>{@link InjuryType#PLAIE}     → Compresse stérile + Bétadine + Bande</li>
 * </ul>
 *
 * <p>Returns an unmodifiable view so the recipe tables can't be
 * mutated by accident from listener code. To rebalance, edit the
 * static initialiser in this class.
 */
public final class InjuryRecipe {

    private static final Map<InjuryType, List<Medicine>> RECIPES =
            new EnumMap<>(InjuryType.class);

    static {
        RECIPES.put(InjuryType.HEMATOME, List.of(Medicine.ARNICA_GEL));
        RECIPES.put(InjuryType.BRULURE,  List.of(Medicine.BIAFINE));
        RECIPES.put(InjuryType.OS_CASSE, List.of(Medicine.PLATRE, Medicine.ANTALGIQUE));
        RECIPES.put(InjuryType.INFECTION,
                List.of(Medicine.AMOXICILLINE, Medicine.BETADINE));
        RECIPES.put(InjuryType.PLAIE,
                List.of(Medicine.COMPRESSE_STERILE, Medicine.BETADINE, Medicine.BANDE));
    }

    private InjuryRecipe() {}

    /** Read-only list of medicines required for {@code type}, in
     *  application order (e.g. désinfecter → compresser → bander). */
    public static List<Medicine> forType(InjuryType type) {
        return RECIPES.getOrDefault(type, List.of());
    }

    /** True iff {@code type} has a recipe defined. (All five do
     *  today, but future infection sub-types could be added without
     *  recipes — caller can fall back to a generic message.) */
    public static boolean hasRecipe(InjuryType type) {
        return RECIPES.containsKey(type);
    }
}
