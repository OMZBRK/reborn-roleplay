package fr.reborn.hud.menu;

import net.minecraft.SharedConstants;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * La fenêtre du jeu aux couleurs Reborn (docs/technique/MOD_HUD.md) : titre « 26.2* Reborn » et logo du launcher en
 * icône. Appelé par {@code mixin.menu.FenetreTitreMixin} et {@code mixin.menu.FenetreIconeMixin} (un mixin ne peut pas
 * se référencer lui-même à l'exécution, d'où cette classe).
 */
public final class FenetreReborn {

    /** Tailles livrées dans {@code assets/reborn/icons/window/icon_<n>x<n>.png} (logo du launcher). */
    private static final int[] TAILLES = {16, 32, 48, 128, 256};

    private FenetreReborn() {}

    /** « 26.2* Reborn » : version lue du jeu, l'astérisque des clients moddés gardée. */
    public static String titre() {
        return SharedConstants.getCurrentVersion().name() + "* Reborn";
    }

    /** Les icônes Reborn, de la plus petite à la plus grande ; {@code null} si une manque (on laisse alors vanilla). */
    public static List<IoSupplier<InputStream>> icones() {
        List<IoSupplier<InputStream>> l = new ArrayList<>();
        for (int n : TAILLES) {
            String chemin = "/assets/reborn/icons/window/icon_" + n + "x" + n + ".png";
            if (FenetreReborn.class.getResource(chemin) == null) return null;
            l.add(() -> {
                InputStream in = FenetreReborn.class.getResourceAsStream(chemin);
                if (in == null) throw new IOException("Icône Reborn absente : " + chemin);
                return in;
            });
        }
        return l;
    }
}
