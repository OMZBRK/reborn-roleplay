package fr.reborn.hud.menu;

/**
 * Constantes de versioning Reborn — centralisées pour qu'un bump
 * (Minecraft, Fabric Loader, mod) ne nécessite de toucher qu'à un
 * seul fichier. Affiché dans le coin "credits" du main menu et
 * dans la ligne secondaire du ServerInfoMini.
 */
public final class RebornVersion {

    private RebornVersion() {}

    public static final String MC_VERSION = "26.1.2";
    public static final String FABRIC_LOADER = "0.19.3";
    public static final String MOD_VERSION = "0.1.0-dev";

    public static final String COPYRIGHT = "Reborn Roleplay © 2026 Reborn Studios";
    public static final String DISCLAIMER = "Not affiliated with Mojang AB or Microsoft Corporation";

    /** Lignes de crédits du bas — affichées sur le splash ET le menu (bas-droite).
     *  Majuscules ASCII sans accents (compat police pixel ArcadePix). */
    public static final String SPLASH_CREDIT_1 = "REBORN 2025 - 2026 - TOUS DROITS RESERVES";
    public static final String SPLASH_CREDIT_2 =
        "COPYRIGHT MOJANG AB. DO NOT REDISTRIBUTE! THANKS TO FABRIC FOR THE LOADER";

    /** Ligne courte type "Minecraft 26.1.2 · Fabric Loader 0.19.3". */
    public static String shortVersion() {
        return "Minecraft " + MC_VERSION + " · Fabric Loader " + FABRIC_LOADER;
    }
}
