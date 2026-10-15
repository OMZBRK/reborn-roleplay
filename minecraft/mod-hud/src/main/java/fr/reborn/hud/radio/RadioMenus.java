package fr.reborn.hud.radio;

import fr.reborn.hud.cosmetic.RemoteCosmetics;
import fr.reborn.hud.interaction.InteractionItem;
import fr.reborn.hud.interaction.InteractionMenus;
import fr.reborn.hud.menu.inventory.CosmeticSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entrées « Radio » et « Oreillette » du menu R (maquettes v2/01 et v2/03, validées) :
 * <ul>
 *   <li>sur un joueur debout, à côté : « Radio › Créer une escouade avec lui » (sans escouade) ou « Radio › Ajouter à
 *       « Kagerō » » et « Voir l'escouade » ;</li>
 *   <li>sur un joueur à terre ou inconscient : « Oreillette › Couper son oreillette / Prendre son oreillette ».</li>
 * </ul>
 * Rien n'apparaît si le serveur n'a pas la radio. Le serveur revérifie tout (distance 3 blocs, oreillettes, états).
 */
public final class RadioMenus {

    private RadioMenus() {}

    /** Entrée « Radio » pour un joueur debout ({@code name} = pseudo Minecraft), ou null. */
    public static InteractionItem radio(String name) {
        if (!RadioClient.serveur()) return null;
        RadioClient.Etat e = RadioClient.etat();
        if (e.staff()) return null;
        RadioClient.Escouade esc = e.esc();
        Boolean porte = porteOreillette(name);
        if (esc == null) {
            if ("aucune".equals(e.ore())) return InteractionItem.disabled("Radio", "il te faut une oreillette");
            if ("coupee".equals(e.ore())) return InteractionItem.disabled("Radio", "oreillette coupée");
            List<InteractionItem> l = new ArrayList<>();
            l.add(InteractionItem.disabled("Oreillette", "portée · aucune escouade"));
            l.add(Boolean.FALSE.equals(porte) ? InteractionItem.disabled("Créer une escouade", "il n'a pas d'oreillette")
                    : InteractionItem.action("Créer une escouade avec lui", () -> InteractionMenus.sendCommand("radio creer " + name)));
            return InteractionItem.submenu("Radio", l);
        }
        List<InteractionItem> l = new ArrayList<>();
        l.add(InteractionItem.disabled("À côté", "3 blocs · il accepte"));
        UUID id = uuidDe(name);
        boolean membre = id != null && esc.membres().stream().anyMatch(m -> id.equals(m.id()) && "ok".equals(m.ore()));
        if (membre) l.add(InteractionItem.disabled("Ajouter", "déjà dans l'escouade"));
        else if (esc.n() >= esc.max()) l.add(InteractionItem.disabled("Ajouter", "escouade complète"));
        else if (Boolean.FALSE.equals(porte)) l.add(InteractionItem.disabled("Ajouter", "il n'a pas d'oreillette"));
        else l.add(InteractionItem.action("Ajouter à « " + esc.nom() + " »",
                () -> InteractionMenus.sendCommand("radio ajouter " + name)));
        l.add(InteractionItem.action("Voir l'escouade", () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreenAndShow(new RadioScreen()));
        }));
        return InteractionItem.submenu("Radio", l);
    }

    /** Entrée « Oreillette » sur un joueur à terre ou inconscient, ou null. */
    public static InteractionItem oreillette(String name, boolean inconscient) {
        if (!RadioClient.serveur()) return null;
        if (Boolean.FALSE.equals(porteOreillette(name))) return null;
        List<InteractionItem> l = new ArrayList<>();
        l.add(InteractionItem.disabled("Effet", inconscient ? "il est inconscient" : "il est à terre"));
        l.add(InteractionItem.action("Couper son oreillette", () -> InteractionMenus.sendCommand("radio oreillette couper " + name)));
        l.add(InteractionItem.action("Prendre son oreillette", () -> InteractionMenus.sendCommand("radio oreillette prendre " + name)));
        return InteractionItem.submenu("Oreillette", l);
    }

    /** Ce joueur porte-t-il une oreillette (emplacement Boucle d'oreille) ? null si on ne sait pas. */
    static Boolean porteOreillette(String name) {
        UUID id = uuidDe(name);
        if (id == null) return null;
        Map<CosmeticSlot, RemoteCosmetics.Remote> c = RemoteCosmetics.get(id);
        if (c == null) return Boolean.FALSE;
        RemoteCosmetics.Remote r = c.get(CosmeticSlot.BOUCLE);
        return r != null && r.item() != null && r.item().model != null && r.item().model.contains("oreillette");
    }

    private static UUID uuidDe(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || name == null) return null;
        for (Player p : mc.level.players()) if (p.getGameProfile().name().equalsIgnoreCase(name)) return p.getUUID();
        return null;
    }
}
