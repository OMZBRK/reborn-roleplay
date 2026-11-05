package fr.reborn.hud.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conflits de touches pour les Paramètres › Commandes (planche t2-b2 : les deux plaques en vermillon, et l'aide dit
 * avec quelle action). Deux liaisons sur la même touche sont en conflit, raccourcis de Plasmo Voice non fusionnés
 * compris ({@link TouchesPlasmo#autres}), sauf :
 * <ul>
 *   <li>les raccourcis de débogage (F3 + touche, catégorie DEBUG) : ils ne partent qu'avec F3 ;</li>
 *   <li>Contextuel et « Main secondaire » de Minecraft sur F : partage voulu (Contextuel ne prend F que si un objet
 *       est visé, {@code sol/RamasserClient}) ;</li>
 *   <li>une touche Reborn fusionnée et son raccourci de Plasmo : c'est la même liaison.</li>
 * </ul>
 *
 * <p>Calculé seulement quand une liaison change : signature des touches liées (les {@link InputConstants.Key} étant
 * uniques par code), relue toutes les 50 ms, et signature des raccourcis de Plasmo non fusionnés, relue toutes les
 * 500 ms ; rien n'est recalculé ni alloué aux images où rien ne change.
 */
public final class TouchesConflits {

    private static final Map<KeyMapping, String> CONFLITS = new IdentityHashMap<>();
    private static long signature = Long.MIN_VALUE;
    private static long plasmoA, verifieA, signaturePlasmo;

    private TouchesConflits() {}

    /** La touche partage-t-elle sa touche avec une autre action ? */
    public static boolean enConflit(KeyMapping km) {
        if (km == null) return false;
        rafraichir();
        return CONFLITS.containsKey(km);
    }

    /** Les actions qui ont la même touche (« Contextuel », « Plasmo : Couper la voix »…), ou null sans conflit. */
    public static String avec(KeyMapping km) {
        if (km == null) return null;
        rafraichir();
        return CONFLITS.get(km);
    }

    /** Recalcule au prochain appel (une touche vient d'être changée). */
    public static void invalider() {
        signature = Long.MIN_VALUE;
    }

    /** Nom lisible d'une liaison (libellé de la touche de langue, comme dans les Commandes de Minecraft). */
    public static String libelle(KeyMapping km) {
        String nom = fr.reborn.hud.menu.settings.ControlsTab.libelleDe(km);
        return nom != null ? nom : Component.translatable(km.getName()).getString();
    }

    private static void rafraichir() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        long now = System.currentTimeMillis();
        if (signature != Long.MIN_VALUE && now - verifieA < 50) return;   // une case par ligne la demande à chaque image
        verifieA = now;
        long sig = 1;
        for (KeyMapping km : mc.options.keyMappings) sig = sig * 31 + System.identityHashCode(KeyMappingHelper.getBoundKeyOf(km));
        if (signature == Long.MIN_VALUE || now - plasmoA >= 500) {
            plasmoA = now;
            signaturePlasmo = signaturePlasmo();
        }
        sig = sig * 31 + signaturePlasmo;
        if (sig == signature) return;
        signature = sig;
        calculer(mc);
    }

    /** Empreinte des raccourcis de Plasmo non fusionnés (nom et touche) : un changement chez Plasmo relance le calcul. */
    private static long signaturePlasmo() {
        long[] h = {1};
        TouchesPlasmo.autres((nom, k) -> h[0] = h[0] * 31 + nom.hashCode() * 17L + System.identityHashCode(k));
        return h[0];
    }

    private static void calculer(Minecraft mc) {
        CONFLITS.clear();
        Map<InputConstants.Key, List<KeyMapping>> parTouche = new HashMap<>();
        for (KeyMapping km : mc.options.keyMappings) {
            if (KeyMapping.Category.DEBUG.equals(km.getCategory())) continue;
            InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(km);
            if (k.equals(InputConstants.UNKNOWN)) continue;
            parTouche.computeIfAbsent(k, x -> new ArrayList<>()).add(km);
        }
        Map<InputConstants.Key, List<String>> plasmo = new HashMap<>();
        TouchesPlasmo.autres((nom, k) -> {
            if (k != null && !k.equals(InputConstants.UNKNOWN)) plasmo.computeIfAbsent(k, x -> new ArrayList<>()).add("Plasmo : " + nom);
        });
        parTouche.forEach((k, l) -> {
            List<String> pv = plasmo.getOrDefault(k, List.of());
            for (KeyMapping km : l) {
                StringBuilder sb = new StringBuilder();
                for (KeyMapping autre : l) {
                    if (autre == km || toleres(km, autre)) continue;
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append("« ").append(libelle(autre)).append(" »");
                }
                for (String p : pv) {
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append("« ").append(p).append(" »");
                }
                if (!sb.isEmpty()) CONFLITS.put(km, sb.toString());
            }
        });
    }

    /** Partages voulus : Contextuel et Main secondaire (F). */
    private static boolean toleres(KeyMapping a, KeyMapping b) {
        KeyMapping offhand = Minecraft.getInstance().options.keySwapOffhand;
        KeyMapping ctx = HudKeybinds.PICKUP;
        return ctx != null && (a == ctx && b == offhand || a == offhand && b == ctx);
    }
}
