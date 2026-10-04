package fr.reborn.hud.screenshot;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Textures GL des screenshots.
 *
 * <ul>
 *   <li>{@link #thumb} — vignette <b>réduite</b> (≤ 320×180), décodée en <b>arrière-plan</b> :
 *       la galerie et l'aperçu de capture n'ont plus de gel à l'ouverture.</li>
 *   <li>{@link #full} — image complète, aussi chargée en arrière-plan, cache limité aux
 *       {@value #FULL_KEEP} dernières (vue détail).</li>
 *   <li>{@link #get} — chargement complet <b>synchrone</b> (éditeur / partage, qui en ont besoin
 *       tout de suite).</li>
 * </ul>
 * Cache indexé par chemin + date de modification (un fichier réécrit est rechargé). Un échec
 * (fichier encore en cours d'écriture…) est retenté après un court délai au lieu de rester vide.
 * Les uploads GL se font toujours sur le render thread.
 */
public final class ScreenshotTextures {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/shots-tex");
    private static final int THUMB_W = 320, THUMB_H = 180, FULL_KEEP = 3;
    private static final long RETRY_MS = 1200;

    public record Tex(Identifier id, int w, int h) {}

    private record Key(String path, long mod, int kind) {}   // kind : 0 sync, 1 vignette, 2 complète

    private static final Map<Key, Tex> READY = new HashMap<>();
    private static final LinkedHashMap<Key, Tex> FULL_LRU = new LinkedHashMap<>(8, .75f, true);
    private static final Set<Key> LOADING = new HashSet<>();
    private static final Map<Key, Long> FAILED = new HashMap<>();

    private ScreenshotTextures() {}

    private static Key key(Path p, int kind) {
        long mod;
        try { mod = Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { mod = 0; }
        return new Key(p.toAbsolutePath().toString(), mod, kind);
    }

    /** Vignette réduite, ou {@code null} tant qu'elle se charge. */
    public static Tex thumb(Path path) { return async(path, 1); }

    /** Image complète, ou {@code null} tant qu'elle se charge. */
    public static Tex full(Path path) { return async(path, 2); }

    private static Tex async(Path path, int kind) {
        Key k = key(path, kind);
        Tex t = kind == 2 ? FULL_LRU.get(k) : READY.get(k);
        if (t != null || LOADING.contains(k)) return t;
        Long failedAt = FAILED.get(k);
        if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_MS) return null;
        LOADING.add(k);
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> decode(path, kind == 1), Util.backgroundExecutor())
            .whenCompleteAsync((img, err) -> {
                LOADING.remove(k);
                if (err != null || img == null) {
                    FAILED.put(k, System.currentTimeMillis());
                    return;
                }
                FAILED.remove(k);
                Tex tex = upload(path, img, kind);
                if (kind == 2) {
                    FULL_LRU.put(k, tex);
                    while (FULL_LRU.size() > FULL_KEEP) {
                        var it = FULL_LRU.entrySet().iterator();
                        mc.getTextureManager().release(it.next().getValue().id());
                        it.remove();
                    }
                } else {
                    READY.put(k, tex);
                }
            }, mc);
        return null;
    }

    /** Chargement complet synchrone (render thread). */
    public static Tex get(Path path) {
        Key k = key(path, 0);
        if (READY.containsKey(k)) return READY.get(k);
        NativeImage img = decode(path, false);
        Tex t = img == null ? null : upload(path, img, 0);
        if (t != null) READY.put(k, t);
        return t;
    }

    private static NativeImage decode(Path path, boolean small) {
        try (InputStream in = Files.newInputStream(path)) {
            NativeImage img = NativeImage.read(in);
            if (!small || (img.getWidth() <= THUMB_W && img.getHeight() <= THUMB_H)) return img;
            float s = Math.min(THUMB_W / (float) img.getWidth(), THUMB_H / (float) img.getHeight());
            int w = Math.max(1, Math.round(img.getWidth() * s)), h = Math.max(1, Math.round(img.getHeight() * s));
            NativeImage out = new NativeImage(w, h, false);
            // réduction par moyenne de blocs (box filter) : rapide et propre pour une vignette
            float fx = img.getWidth() / (float) w, fy = img.getHeight() / (float) h;
            for (int y = 0; y < h; y++) {
                int y0 = (int) (y * fy), y1 = Math.max(y0 + 1, (int) ((y + 1) * fy));
                for (int x = 0; x < w; x++) {
                    int x0 = (int) (x * fx), x1 = Math.max(x0 + 1, (int) ((x + 1) * fx));
                    long a = 0, r = 0, g = 0, b = 0; int n = 0;
                    for (int yy = y0; yy < y1; yy += 2) for (int xx = x0; xx < x1; xx += 2) {
                        int c = img.getPixel(xx, yy);
                        a += (c >>> 24) & 255; r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
                    }
                    out.setPixel(x, y, (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
                }
            }
            img.close();
            return out;
        } catch (Exception e) {
            LOGGER.debug("lecture {} échec : {}", path.getFileName(), e.getMessage());
            return null;
        }
    }

    private static Tex upload(Path path, NativeImage img, int kind) {
        String name = path.getFileName().toString();
        Identifier id = Identifier.fromNamespaceAndPath("reborn-shots",
            (kind == 1 ? "t/" : kind == 2 ? "f/" : "g/") + sanitize(name));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "reborn-shot", img));
        return new Tex(id, img.getWidth(), img.getHeight());
    }

    /** Libère les images complètes (en sortant de la vue détail). */
    public static void releaseFull() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (Tex t : FULL_LRU.values()) tm.release(t.id());
        FULL_LRU.clear();
    }

    /** Libère tout (fermeture de la galerie). */
    public static void clear() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (Tex t : READY.values()) if (t != null) tm.release(t.id());
        READY.clear();
        releaseFull();
        FAILED.clear();
    }

    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }
}
