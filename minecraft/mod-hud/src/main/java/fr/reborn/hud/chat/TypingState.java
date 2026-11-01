package fr.reborn.hud.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * État « qui est en train d'écrire dans le chat », alimenté par le serveur
 * (ShinobiCore) sur le canal {@code reborn:typing}. Le client dessine 3 points
 * au-dessus de la tête des joueurs proches en train d'écrire.
 *
 * <p>Les UUID sont résolus par {@code Level#getEntity(UUID)} (joueurs, et mannequins du banc) dans un ensemble
 * <b>réutilisé</b>, rafraîchi au plus toutes les 50 ms ou à chaque nouveau message : {@link #typingEntityIds} n'alloue
 * rien quand on l'appelle à chaque image.
 */
public final class TypingState {

    private TypingState() {}

    private static volatile Set<UUID> typing = Set.of();
    /** Change à chaque message : force une nouvelle résolution. */
    private static volatile int version;

    private static final long RAFRAICHIR_MS = 50L;
    private static final Set<Integer> IDS = new HashSet<>();
    private static final Set<Integer> VUE = Collections.unmodifiableSet(IDS);
    private static long resoluMs;
    private static int resoluVersion = -1;

    /** Parse le JSON S2C {@code {"typing":["uuid",...]}}. */
    public static void update(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Set<UUID> set = new HashSet<>();
            JsonArray arr = root.has("typing") ? root.getAsJsonArray("typing") : null;
            if (arr != null) {
                for (var el : arr) {
                    try { set.add(UUID.fromString(el.getAsString())); } catch (Exception ignored) {}
                }
            }
            typing = set;
            version++;
        } catch (Exception ignored) {
            // JSON malformé → on garde l'état précédent.
        }
    }

    public static void clear() {
        typing = Set.of();
        version++;
    }

    /**
     * Entity ids des joueurs proches en train d'écrire (résolus dans le monde). Ensemble réutilisé, en lecture seule,
     * mis à jour au plus toutes les 50 ms (thread client).
     */
    public static Set<Integer> typingEntityIds() {
        long now = System.currentTimeMillis();
        if (resoluVersion != version || now - resoluMs >= RAFRAICHIR_MS || now < resoluMs) resoudre(now);
        return VUE;
    }

    private static void resoudre(long now) {
        resoluMs = now;
        resoluVersion = version;
        IDS.clear();
        Set<UUID> t = typing;
        if (t.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (UUID u : t) {
            Entity e = mc.level.getEntity(u);
            if (e != null) IDS.add(e.getId());
        }
    }

    /** Notifie le serveur que le joueur local (dé)commence à écrire (C2S). */
    public static void sendTyping(boolean typingNow) {
        try {
            if (ClientPlayNetworking.canSend(TypingPayload.ID)) {
                ClientPlayNetworking.send(new TypingPayload(typingNow ? "1" : "0"));
            }
        } catch (Throwable ignored) {}
    }
}
