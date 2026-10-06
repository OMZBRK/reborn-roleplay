package fr.reborn.hud.immersion;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cadres du mode photo, appliqués au fichier PNG <b>après</b> la capture (en arrière-plan) :
 * <ul>
 *   <li>1 — <b>Makimono</b> : baguettes de bois en haut et en bas, bords de laque vermillon,
 *       filet d'or, sceau rouge dans le coin.</li>
 *   <li>2 — <b>Polaroïd</b> : marge crème, plus large en bas.</li>
 * </ul>
 */
public final class PhotoFrames {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/photo-frames");

    private PhotoFrames() {}

    public static void applyAsync(Path file, int frame) {
        if (frame <= 0) return;
        Util.backgroundExecutor().execute(() -> {
            try {
                NativeImage src;
                try (InputStream in = Files.newInputStream(file)) { src = NativeImage.read(in); }
                NativeImage out = frame == 1 ? makimono(src) : polaroid(src);
                out.writeToFile(file);
                if (out != src) out.close();
                src.close();
            } catch (Exception e) {
                LOG.warn("cadre sur {} échec : {}", file.getFileName(), e.getMessage());
            }
        });
    }

    private static NativeImage makimono(NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int bar = Math.max(6, h * 4 / 100), side = Math.max(4, w * 12 / 1000);
        for (int y = 0; y < bar; y++) {
            int shade = 0xFF000000 | woodColor(y, bar);
            for (int x = 0; x < w; x++) { img.setPixel(x, y, shade); img.setPixel(x, h - 1 - y, shade); }
        }
        for (int y = bar; y < h - bar; y++)
            for (int x = 0; x < side; x++) { img.setPixel(x, y, 0xFF8E1B22); img.setPixel(w - 1 - x, y, 0xFF8E1B22); }
        int g = Math.max(1, h / 540);
        for (int t = 0; t < g; t++) {
            for (int x = side; x < w - side; x++) { img.setPixel(x, bar + t, 0xFFD9B26A); img.setPixel(x, h - bar - 1 - t, 0xFFD9B26A); }
            for (int y = bar; y < h - bar; y++) { img.setPixel(side + t, y, 0xFFD9B26A); img.setPixel(w - side - 1 - t, y, 0xFFD9B26A); }
        }
        // sceau
        int s = Math.max(14, h * 6 / 100), sx = w - side - s - s / 2, sy = h - bar - s - s / 2;
        img.fillRect(sx, sy, s, s, 0xFFB0221E);
        int b = Math.max(1, s / 12);
        for (int k = 0; k < b; k++) {
            for (int i = 0; i < s; i++) {
                img.setPixel(sx + i, sy + k, 0xFFF4E6CC); img.setPixel(sx + i, sy + s - 1 - k, 0xFFF4E6CC);
                img.setPixel(sx + k, sy + i, 0xFFF4E6CC); img.setPixel(sx + s - 1 - k, sy + i, 0xFFF4E6CC);
            }
        }
        int c = s / 2, r = s / 5;
        img.fillRect(sx + c - r, sy + c - b, 2 * r, 2 * b, 0xFFF4E6CC);
        img.fillRect(sx + c - b, sy + c - r, 2 * b, 2 * r, 0xFFF4E6CC);
        return img;
    }

    private static int woodColor(int y, int bar) {
        float t = y / (float) Math.max(1, bar - 1);
        float l = 0.75f + 0.35f * (float) Math.sin(t * Math.PI);
        int r = Math.min(255, (int) (96 * l)), gg = Math.min(255, (int) (62 * l)), b = Math.min(255, (int) (36 * l));
        return (r << 16) | (gg << 8) | b;
    }

    private static NativeImage polaroid(NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int m = Math.max(8, w * 3 / 100), bottom = Math.max(24, h * 14 / 100);
        NativeImage out = new NativeImage(w + 2 * m, h + m + bottom, false);
        out.fillRect(0, 0, out.getWidth(), out.getHeight(), 0xFFF4EEDC);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) out.setPixel(m + x, m + y, img.getPixel(x, y));
        return out;
    }
}
