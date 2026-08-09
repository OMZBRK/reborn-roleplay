package fr.reborn.hud.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * État « qui est en train d'écrire dans le chat », alimenté par le serveur
 * (ShinobiCore) sur le canal {@code reborn:typing}. Le client dessine 3 points
 * au-dessus de la tête des joueurs proches en train d'écrire.
 */
public final class TypingState {

    private TypingState() {}

    private static volatile Set<UUID> typing = Set.of();

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
        } catch (Exception ignored) {
            // JSON malformé → on garde l'état précédent.
        }
    }

    public static void clear() { typing = Set.of(); }

    /** Entity ids des joueurs proches en train d'écrire (résolus dans le monde). */
    public static Set<Integer> typingEntityIds() {
        Set<Integer> ids = new HashSet<>();
        Set<UUID> t = typing;
        if (t.isEmpty()) return ids;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return ids;
        for (UUID u : t) {
            Player p = mc.level.getPlayerByUUID(u);
            if (p != null) ids.add(p.getId());
        }
        return ids;
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
