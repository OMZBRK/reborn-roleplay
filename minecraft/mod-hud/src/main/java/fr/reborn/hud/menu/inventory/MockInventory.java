package fr.reborn.hud.menu.inventory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Données de démonstration pour tester l'écran <b>en solo</b> (touche Sacoche)
 * sans serveur. Modèle « base = hotbar vanilla » : la rangée du bas lit le vrai
 * hotbar du joueur (le perso dev a des items vanilla) ; ce mock ne fournit que
 * le <b>sac</b> — ici une Sacoche équipée avec quelques items vanilla visibles
 * (les modèles Nexo ne se rendent pas sans le pack, donc on prend des matériaux
 * vanilla pour le test visuel).
 */
public final class MockInventory {

    private MockInventory() {}

    public static InventoryData.Snapshot build() {
        SlotItem[] bag = new SlotItem[9]; // tier SACOCHE = +9 cases
        bag[0] = new SlotItem("minecraft:iron_sword", null, 1, "Tantō", 1.5);
        bag[1] = new SlotItem("minecraft:arrow", null, 20, "Shuriken", 0.05);
        bag[2] = new SlotItem("minecraft:bread", null, 4, "Ration", 0.4);
        bag[3] = new SlotItem("minecraft:paper", null, 1, "Parchemin — Katon", 0.3);
        bag[4] = new SlotItem("minecraft:leather", null, 1, "Sac ninja", 2.0); // à « équiper » (mock)

        Map<CosmeticSlot, SlotItem> equipped = new EnumMap<>(CosmeticSlot.class);
        equipped.put(CosmeticSlot.BANDEAU, new SlotItem("minecraft:iron_ingot", null, 1, "Bandeau de Konoha", 0.1));

        SlotItem bagItem = new SlotItem("minecraft:leather", null, 1, "Sacoche", 0.6);

        // Tier SACOCHE : 9 base (hotbar) + 9 sac, 20 kg. curWeight indicatif.
        return new InventoryData.Snapshot("SACOCHE", 9, 9, 20.0, 8.4, bagItem, bag, equipped);
    }
}
