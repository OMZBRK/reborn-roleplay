package fr.reborn.hud.menu.tablist;

import java.util.ArrayList;
import java.util.List;

/**
 * Données de démonstration pour le tablist v1 (le temps de valider le visuel
 * façon Zenkai). Remplacé par le flux serveur {@code reborn:tablist} en v2.
 */
public final class MockTablist {

    private MockTablist() {}

    /**
     * Date RP (calendrier lore) — mockée en v1. En v2 elle viendra du serveur
     * (ShinobiCore {@code LoreCalendar}, ex. « An 495 »).
     */
    public static String rpDate() {
        return "AN 495 · PRINTEMPS";
    }

    // Quelques couleurs de clan pour la démo (ARGB).
    private static final int UCHIHA = 0xFFE0514E;   // rouge
    private static final int HYUGA  = 0xFFB98BE0;   // violet
    private static final int SENJU  = 0xFF5EC26A;   // vert
    private static final int NARA   = 0xFF6FA8DC;   // bleu

    public static List<TabEntry> build(String selfName) {
        List<TabEntry> list = new ArrayList<>();

        // Toi.
        list.add(new TabEntry(selfName, TabEntry.Relation.SOI, false,
            "Genin", "Uchiha", UCHIHA, 12, 7, 16, "Agilité"));

        // Staff.
        list.add(new TabEntry("Izura Uchiha", TabEntry.Relation.CONNAISSANCE, true,
            "Jonin", "Uchiha", UCHIHA, 25, 34, 27, "Intelligence"));
        list.add(new TabEntry("Hokage Senju", TabEntry.Relation.AMI, true,
            "Kage", "Senju", SENJU, 8, 60, 48, "Force"));

        // Amis.
        list.add(new TabEntry("Khan Uchiha", TabEntry.Relation.AMI, false,
            "Chunin", "Uchiha", UCHIHA, 19, 15, 19, "Force"));
        list.add(new TabEntry("Sena Hyuga", TabEntry.Relation.AMI, false,
            "Genin", "Hyuga", HYUGA, 12, 9, 17, "Intelligence"));

        // Connaissances.
        list.add(new TabEntry("Ryo Nara", TabEntry.Relation.CONNAISSANCE, false,
            "Chunin", "Nara", NARA, 41, 18, 22, "Intelligence"));
        list.add(new TabEntry("Mei Senju", TabEntry.Relation.CONNAISSANCE, false,
            "Genin", "Senju", SENJU, 17, 6, 15, "Agilité"));
        list.add(new TabEntry("Daichi", TabEntry.Relation.CONNAISSANCE, false,
            "Étudiant Académie", null, 0, 30, 2, 13, "Force"));

        // Inconnus (Pas RP) — grade ???.
        for (int i = 0; i < 6; i++) {
            int ping = new int[]{222, 11, 41, 17, 23, 17}[i];
            list.add(new TabEntry("Inconnu (Pas RP)", TabEntry.Relation.INCONNU, false,
                "???", null, 0, ping, 0, 0, "???"));
        }

        return list;
    }
}
