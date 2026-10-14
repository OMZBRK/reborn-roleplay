package fr.reborn.hud.nourriture;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Icônes d'effets et d'état du HUD : un fichier par icône, nom stable,
 * {@code assets/reborn/textures/gui/effets/<nom>.png} ({@code tools/gen_nourriture_textures.py}). <b>Remplaçables sans
 * toucher au code</b> : la taille réelle de chaque fichier est lue (10, 16, 32 px…) ; une bande verticale d'images
 * carrées (hauteur = n × largeur, comme {@code gui/ability/dash.png}) est jouée comme une animation. L'icône est posée
 * au pixel écran, à un multiple entier (ou une fraction 1/n) de sa taille, centrée dans la case demandée.
 *
 * <p>Noms utilisés : regeneration, pause, satiete_prolongee, endurance, meditation, chakra, pilule, fatigue, poison,
 * faim, affame, chaud, froid, repas, ramen, dango, the, bento, ration, panier, ryo, bol_vide. Un nom inconnu retombe
 * sur {@code repas}.
 */
public final class EffetsIcones {

    private record Info(Identifier id, int w, int h, int images) {}

    private static final Map<String, Info> CACHE = new HashMap<>();

    private EffetsIcones() {}

    private static Info info(String nom) {
        String n = nom == null || nom.isBlank() ? "repas" : nom.toLowerCase(Locale.ROOT);
        Info i = CACHE.get(n);
        if (i != null) return i;
        Identifier id = DaKit.tex("effets", n);
        int[] wh = taille(id);
        if (wh == null && !"repas".equals(n)) {
            i = info("repas");
        } else {
            if (wh == null) wh = new int[] {10, 10};
            int images = wh[1] > wh[0] && wh[1] % wh[0] == 0 ? wh[1] / wh[0] : 1;
            i = new Info(id, wh[0], wh[1], images);
        }
        CACHE.put(n, i);
        return i;
    }

    /** Largeur et hauteur d'un PNG (en-tête IHDR), ou null s'il n'existe pas. */
    private static int[] taille(Identifier id) {
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) return null;
            try (InputStream in = res.get().open()) {
                byte[] h = in.readNBytes(24);
                if (h.length < 24) return null;
                int w = ((h[16] & 0xFF) << 24) | ((h[17] & 0xFF) << 16) | ((h[18] & 0xFF) << 8) | (h[19] & 0xFF);
                int hh = ((h[20] & 0xFF) << 24) | ((h[21] & 0xFF) << 16) | ((h[22] & 0xFF) << 8) | (h[23] & 0xFF);
                return w > 0 && hh > 0 ? new int[] {w, hh} : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Oublie les tailles lues (rechargement des ressources). */
    public static void vider() { CACHE.clear(); }

    /**
     * Pose l'icône {@code nom} centrée dans la case carrée ({@code x}, {@code y}, {@code cote}) en unités GUI courantes.
     *
     * @param argb teinte (blanc = telle quelle ; l'alpha module l'opacité)
     */
    public static void poser(GuiGraphicsExtractor g, String nom, float x, float y, float cote, int argb) {
        Info i = info(nom);
        int k = DaKit.echelleGui();
        float voulu = cote * k;                        // pixels écran visés
        int cadre = i.w;                               // côté d'une image (carrée si bande)
        int ch = i.images > 1 ? i.w : i.h;
        float s;
        float r = voulu / cadre;
        if (r >= 1) s = (float) Math.floor(r);
        else s = 1f / (float) Math.ceil(1 / r);
        float wPx = cadre * s, hPx = ch * s;
        int image = i.images > 1 ? (int) ((System.currentTimeMillis() / 160) % i.images) : 0;
        DaKit.debutPx(g);
        float px0 = Math.round(x * k + (voulu - wPx) / 2f), py0 = Math.round(y * k + (voulu - hPx) / 2f);
        g.pose().pushMatrix();
        g.pose().translate(px0, py0);
        g.pose().scale(s, s);
        DaKit.blit(g, i.id, 0, 0, 0, image * ch, cadre, ch, i.w, i.h, argb);
        g.pose().popMatrix();
        DaKit.finPx(g);
    }
}
