package fr.reborn.hud.skin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Réception client des assets du character creator poussés par le serveur (canal
 * {@code reborn:creatorpack}). Réassemble les chunks par asset puis injecte le tout
 * dans {@link CharacterCatalog} au runtime → les nouvelles tenues/cheveux/yeux…
 * apparaissent dans le creator SANS mise à jour du mod.
 *
 * <p>Corps d'un asset réassemblé : {@code UTF folder, UTF id, UTF metaJson,
 * int pngLen, png, int maskLen, mask}.
 */
public final class CreatorAssets {

    public static final CreatorAssets INSTANCE = new CreatorAssets();
    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/creator");

    /** Réassemblage en cours : clé « catégorie/id » → morceaux reçus. */
    private final Map<String, byte[][]> pending = new ConcurrentHashMap<>();

    private CreatorAssets() {}

    /**
     * Reçoit un chunk et, une fois tous les morceaux présents, réassemble puis
     * enregistre l'asset. Un chunk avec {@code total<=0} (requête) est ignoré côté client.
     */
    public void registerChunk(String name, int idx, int total, byte[] data) {
        if (name == null || name.isBlank() || total <= 0 || idx < 0 || idx >= total) return;
        try {
            byte[][] parts = pending.computeIfAbsent(name, k -> new byte[total][]);
            if (parts.length != total) { // taille changée (reload) → repart à neuf
                parts = new byte[total][];
                pending.put(name, parts);
            }
            parts[idx] = data != null ? data : new byte[0];
            for (byte[] p : parts) if (p == null) return; // pas encore complet

            int len = 0;
            for (byte[] p : parts) len += p.length;
            byte[] full = new byte[len];
            int off = 0;
            for (byte[] p : parts) { System.arraycopy(p, 0, full, off, p.length); off += p.length; }
            pending.remove(name);
            registerAsset(full);
        } catch (Throwable t) {
            LOG.debug("réassemblage asset creator '{}' échoué ({})", name, t.toString());
            pending.remove(name);
        }
    }

    /** Décode le corps réassemblé d'un asset et l'injecte dans le catalogue. */
    private void registerAsset(byte[] full) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(full))) {
            String folder = in.readUTF();
            String id = in.readUTF();
            String meta = in.readUTF();
            int pngLen = in.readInt();
            byte[] png = new byte[Math.max(0, pngLen)];
            in.readFully(png);
            int maskLen = in.readInt();
            byte[] mask = maskLen > 0 ? new byte[maskLen] : null;
            if (mask != null) in.readFully(mask);
            boolean ok = CharacterCatalog.registerRuntimeAsset(folder, meta, png, mask);
            if (ok) LOG.info("asset creator serveur enregistré : {}/{} ({} o png)", folder, id, png.length);
        } catch (Throwable t) {
            LOG.warn("décodage asset creator échoué ({})", t.toString());
        }
    }

    /** Purge (déconnexion) — le catalogue bundlé du jar reste. */
    public void clear() {
        pending.clear();
        CharacterCatalog.clearRuntime();
    }

    // La clé de réassemblage est « folder/id » côté serveur ; unused helper conservé
    // pour cohérence de nommage si besoin ultérieur.
    static String key(String folder, String id) {
        return folder + "/" + id;
    }

    @SuppressWarnings("unused")
    private static String utf(byte[] b) { return new String(b, StandardCharsets.UTF_8); }
}
