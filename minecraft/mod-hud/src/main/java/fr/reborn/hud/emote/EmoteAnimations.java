package fr.reborn.hud.emote;

import com.zigythebird.playeranimcore.animation.Animation;
import io.github.kosmx.emotes.main.EmoteHolder;
import io.github.kosmx.emotes.main.mixinFunctions.IPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Pont client vers EmoteCraft : joue une emote <b>nommée</b> sur n'importe quel avatar,
 * en réutilisant les emotes qu'EmoteCraft a déjà chargées (built-in + dossier
 * {@code emotes/} du client + emotes distribuées par le serveur).
 *
 * <p>Contrairement à {@link fr.reborn.hud.combat.CombatAnimations} (assets embarqués +
 * layer PAL maison), on ne <b>bundle aucune animation</b> ni ne gère de layer : l'API
 * EmoteCraft ({@link EmoteHolder#playEmote}) fait le rendu. Le serveur (ShinobiCore) ne
 * transmet que le nom résolu via {@code reborn:emote} ; chaque client le résout ici.
 *
 * <p>Tout est gardé par {@code try/catch(Throwable)} : si EmoteCraft est absent du
 * modpack, la couche se désactive proprement (aucune emote, mais le mod tourne).
 */
public final class EmoteAnimations {

    public static final EmoteAnimations INSTANCE = new EmoteAnimations();
    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/emote");

    private EmoteAnimations() {}

    /** Renvoie {@code true} si l'API EmoteCraft est chargée et le registre accessible. */
    public boolean isAvailable() {
        try {
            return EmoteHolder.list != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Noms d'affichage des emotes chargées (triés, dédupliqués) — alimente la liste
     * d'émotes du menu Reborn (touche {@code .} → onglet ANIMATIONS).
     */
    public List<String> names() {
        List<String> out = new ArrayList<>();
        try {
            if (EmoteHolder.list == null) return out;
            TreeSet<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (EmoteHolder h : EmoteHolder.list) {
                String name = displayName(h);
                if (name != null && !name.isBlank() && seen.add(name)) {
                    out.add(name);
                }
            }
        } catch (Throwable t) {
            LOG.debug("liste emotes indisponible ({})", t.toString());
        }
        return out;
    }

    /** Résout une emote par nom d'affichage, id interne, ou UUID. {@code null} si absente. */
    public Animation resolve(String key) {
        if (key == null || key.isBlank()) return null;
        try {
            if (EmoteHolder.list == null) return null;
            String want = norm(key);
            // 1) correspondance exacte (nom d'affichage / getNameOrId / uuid).
            for (EmoteHolder h : EmoteHolder.list) {
                Animation a = h.getEmote();
                if (a == null) continue;
                if (want.equals(norm(displayName(h))) || want.equals(norm(a.getNameOrId()))
                        || key.equalsIgnoreCase(String.valueOf(a.uuid()))) {
                    return a;
                }
            }
            // 2) repli : préfixe (pratique pour la frappe rapide).
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
     * arrêt de l'emote en cours. Résout l'avatar dans le niveau client puis délègue à
     * EmoteCraft. No-op silencieux si l'avatar/l'emote est introuvable.
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
            EmoteHolder.playEmote(player, anim);
        } catch (Throwable t) {
            LOG.debug("lecture emote '{}' sur #{} échouée ({})", key, entityId, t.toString());
        }
    }

    /** Nom d'affichage lisible d'un holder (Component → String), ou son id interne. */
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

    /** Normalise pour comparaison lâche : minuscules, sans espaces/underscores/tirets. */
    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", "").trim();
    }
}
