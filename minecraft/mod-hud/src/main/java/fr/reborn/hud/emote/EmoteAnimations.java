package fr.reborn.hud.emote;

import com.zigythebird.playeranimcore.animation.Animation;
import io.github.kosmx.emotes.main.EmoteHolder;
import io.github.kosmx.emotes.main.mixinFunctions.IPlayerEntity;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pont client vers EmoteCraft : joue une emote <b>nommée</b> sur n'importe quel avatar.
 *
 * <p>Deux sources d'emotes, cumulées :
 * <ul>
 *   <li><b>EmoteCraft</b> : les emotes déjà chargées côté client (built-in + dossier
 *       {@code emotes/} du client), via {@link EmoteHolder#list}.</li>
 *   <li><b>Reborn (serveur)</b> : les emotes <b>déposées par les devs</b> dans le dossier
 *       serveur {@code plugins/ShinobiCore/emotes/} et distribuées au client à la
 *       connexion via le canal {@code reborn:emotepack} — voir {@link #registerCustom}.
 *       C'est ce qui permet « drop + link serveur → visible par tous » (ex. swings de
 *       kenjutsu) sans mise à jour du mod ni serveur Fabric.</li>
 * </ul>
 *
 * <p>Aucune animation n'est bundlée : l'API EmoteCraft fait le rendu. Tout est gardé
 * par {@code try/catch(Throwable)} → no-op propre si EmoteCraft est absent.
 */
public final class EmoteAnimations {

    public static final EmoteAnimations INSTANCE = new EmoteAnimations();
    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/emote");

    /** Emotes serveur (déposées par les devs), reçues sur {@code reborn:emotepack}. */
    private final Map<String, Animation> customEmotes = new ConcurrentHashMap<>();
    /** Réassemblage des chunks en cours : nom → morceaux reçus. */
    private final Map<String, byte[][]> pending = new ConcurrentHashMap<>();

    private EmoteAnimations() {}

    /** {@code true} si l'API EmoteCraft est chargée. */
    public boolean isAvailable() {
        try {
            return EmoteHolder.list != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // ─── Emotes serveur (distribution Reborn) ───

    /**
     * Reçoit un chunk du canal {@code reborn:emotepack} et, une fois tous les morceaux
     * reçus, réassemble puis enregistre l'emote. Les gros {@code .emotecraft} dépassent
     * la taille max d'un plugin-message → ils arrivent en plusieurs chunks.
     */
    public void registerChunk(String name, int idx, int total, byte[] data) {
        if (name == null || name.isBlank() || total <= 0 || idx < 0 || idx >= total) return;
        try {
            byte[][] parts = pending.computeIfAbsent(name, k -> new byte[total][]);
            if (parts.length != total) { // changement de taille (reload) → repart à neuf
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
            registerCustom(name, full);
        } catch (Throwable t) {
            LOG.debug("réassemblage emote '{}' échoué ({})", name, t.toString());
            pending.remove(name);
        }
    }

    /**
     * Décode les octets {@code .emotecraft} complets et enregistre l'emote sous {@code name}.
     * L'anim est aussi ajoutée au registre EmoteCraft ({@link EmoteHolder#list}) pour être
     * jouée exactement comme les emotes built-in (et visible dans la roue EmoteCraft).
     */
    public void registerCustom(String name, byte[] data) {
        if (name == null || name.isBlank() || data == null || data.length == 0) return;
        try {
            Map<String, Animation> parsed = UniversalEmoteSerializer.readData(
                    new ByteArrayInputStream(data), name + ".emotecraft");
            if (parsed == null || parsed.isEmpty()) {
                LOG.warn("emote serveur '{}' vide/illisible", name);
                return;
            }
            Animation anim = parsed.values().iterator().next();
            customEmotes.put(norm(name), anim);
            // Enregistre dans le registre EmoteCraft → rendu identique aux built-in.
            try {
                if (EmoteHolder.list != null) EmoteHolder.list.add(new EmoteHolder(anim));
            } catch (Throwable ignore) {
                // registre indisponible : on garde au moins customEmotes + IPlayerEntity.
            }
            LOG.info("emote serveur enregistrée : {} ({} o)", name, data.length);
        } catch (Throwable t) {
            LOG.warn("échec décodage emote serveur '{}' ({})", name, t.toString());
        }
    }

    /** Vide les emotes serveur (à la déconnexion). */
    public void clearCustom() {
        customEmotes.clear();
        pending.clear();
    }

    // ─── Lecture / résolution ───

    /**
     * Noms d'affichage des emotes disponibles (serveur + EmoteCraft), triés &amp;
     * dédupliqués — alimente la liste du menu Reborn (touche {@code .} → ANIMATIONS).
     */
    public List<String> names() {
        TreeSet<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<String> out = new ArrayList<>();
        // Emotes serveur d'abord (souvent les plus pertinentes : kenjutsu, RP…).
        for (String k : customEmotes.keySet()) {
            if (seen.add(k)) out.add(k);
        }
        try {
            if (EmoteHolder.list != null) {
                for (EmoteHolder h : EmoteHolder.list) {
                    String name = displayName(h);
                    if (name != null && !name.isBlank() && seen.add(name)) out.add(name);
                }
            }
        } catch (Throwable t) {
            LOG.debug("liste emotes EmoteCraft indisponible ({})", t.toString());
        }
        return out;
    }

    /** Résout une emote par nom : d'abord les emotes serveur, puis le registre EmoteCraft. */
    public Animation resolve(String key) {
        if (key == null || key.isBlank()) return null;
        String want = norm(key);
        // 1) emotes serveur (exact).
        Animation custom = customEmotes.get(want);
        if (custom != null) return custom;
        try {
            if (EmoteHolder.list == null) return null;
            // 2) EmoteCraft : exact (nom d'affichage / id / uuid).
            for (EmoteHolder h : EmoteHolder.list) {
                Animation a = h.getEmote();
                if (a == null) continue;
                if (want.equals(norm(displayName(h))) || want.equals(norm(a.getNameOrId()))
                        || key.equalsIgnoreCase(String.valueOf(a.uuid()))) {
                    return a;
                }
            }
            // 3) repli : préfixe (emotes serveur puis EmoteCraft).
            for (Map.Entry<String, Animation> e : customEmotes.entrySet()) {
                if (e.getKey().startsWith(want)) return e.getValue();
            }
            for (EmoteHolder h : EmoteHolder.list) {
                Animation a = h.getEmote();
                if (a == null) continue;
                String dn = norm(displayName(h));
                if (!dn.isEmpty() && dn.startsWith(want)) return a;
            }
        } catch (Throwable t) {
            LOG.debug("résolution emote '{}' échouée ({})", key, t.toString());
        }
        return null;
    }

    /**
     * Joue (ou arrête) l'emote {@code key} sur l'avatar {@code entityId}. Nom vide =
     * arrêt. No-op silencieux si l'avatar/l'emote est introuvable.
     */
    public void playByEntityId(int entityId, String key) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) return;
            if (!(level.getEntity(entityId) instanceof AbstractClientPlayer player)) return;

            if (key == null || key.isBlank()) {
                ((IPlayerEntity) player).stopEmote();
                return;
            }
            Animation anim = resolve(key);
            if (anim == null) {
                LOG.debug("emote introuvable côté client : {}", key);
                return;
            }
            // EmoteHolder.playEmote joue n'importe quelle Animation (registre non requis) ;
            // repli sur l'API bas niveau IPlayerEntity si elle refuse.
            boolean ok = EmoteHolder.playEmote(player, anim);
            if (!ok) ((IPlayerEntity) player).emotecraft$playEmote(anim, 3.0f, true);
        } catch (Throwable t) {
            LOG.debug("lecture emote '{}' sur #{} échouée ({})", key, entityId, t.toString());
        }
    }

    // ─── util ───

    private static String displayName(EmoteHolder h) {
        try {
            if (h.name != null) {
                String s = h.name.getString();
                if (s != null && !s.isBlank()) return s;
            }
            Animation a = h.getEmote();
            return a != null ? a.getNameOrId() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", "").trim();
    }
}
