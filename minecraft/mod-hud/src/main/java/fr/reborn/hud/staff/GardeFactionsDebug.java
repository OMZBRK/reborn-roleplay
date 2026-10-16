package fr.reborn.hud.staff;

import net.minecraft.client.Minecraft;

/**
 * Pont du banc Faction ({@code faction/FactionDebug}, REBORN_FACTION_DEBUG) vers le Poste de garde : ouvre F7 ›
 * Outils › Factions sur le serveur simulé ({@link StaffDemo} pour le canal staff, {@code FactionDemo} pour le canal
 * faction). Inactif hors banc.
 */
public final class GardeFactionsDebug {

    private GardeFactionsDebug() {}

    /** {@code cas} : « liste », « fiche » (Konohagakure) ou « creer » (formulaire Créer une faction). */
    public static void ouvrir(String cas) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String me = mc.player.getUUID().toString();
        StaffClient.demo = true;
        StaffClient.debugReceive(StaffDebug.snap(me, false, "outils"));
        GardeScreen g = new GardeScreen("alerts");
        mc.setScreenAndShow(g);
        g.switchTab("outils");
        g.outil = "factions";
        g.outilsVue.factionsVue.ouvrir();
        switch (cas) {
            case "fiche" -> g.outilsVue.factionsVue.debugFiche("KON");
            case "creer" -> g.outilsVue.factionsVue.debugCreer();
            default -> { }
        }
    }

    public static void fermer() {
        StaffClient.demo = false;
        StaffClient.debugEffacerToasts();
    }
}
