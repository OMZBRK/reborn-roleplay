package fr.reborn.hud.cosmetic;

/**
 * Point d'enregistrement des {@link net.minecraft.client.model.geom.ModelLayerLocation}
 * de cosmétiques 3D à mesh custom (bake vanilla).
 *
 * <p>Les cosmétiques actuels sont rendus directement à partir de leur <b>modèle
 * d'item Nexo</b> ({@link CosmeticFeatureRenderer}), sans mesh baké — il n'y a donc
 * plus de layer à enregistrer. La classe est conservée pour accueillir un futur
 * cosmétique à géométrie vanilla dédiée : ajouter un {@code ModelLayerLocation} +
 * son {@code registerModelLayer(...)} ici.
 */
public final class CosmeticModelLayers {

    private CosmeticModelLayers() {}

    /** À appeler dans {@code onInitializeClient()} — no-op tant qu'aucun mesh baké n'est requis. */
    public static void register() {
        // Aucun LayerDefinition à enregistrer : les cosmétiques utilisent leur modèle d'item.
    }
}
