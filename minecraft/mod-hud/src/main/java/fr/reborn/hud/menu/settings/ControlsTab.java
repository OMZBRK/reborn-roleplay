package fr.reborn.hud.menu.settings;

import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.widget.KeybindButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

/**
 * Onglet Contrôles — réglages souris/déplacement câblés sur {@code mc.options}
 * <b>et</b> les commandes Reborn (re-bindables inline via {@link KeybindButton}) : la carte des touches du 15/10, avec
 * les touches Minecraft que Reborn utilise (sacoche, vue, allures) et celles des autres mods du modpack (OST, Plasmo
 * Voice) quand ils sont là.
 * Les binds Reborn vivent aussi dans l'écran vanilla « Commandes » sous la
 * catégorie « Reborn » (cf. {@code RebornKeyCategory}) ; on les remonte ici pour
 * qu'ils soient accessibles sans quitter les paramètres Reborn.
 */
public class ControlsTab extends SectionedTab {

    /** Largeur du bouton de touche, borné pour rester lisible sur colonne étroite. */
    private static final int KEY_BTN_W = 150;

    @SuppressWarnings("unused")
    private final Screen parent;

    public ControlsTab(Screen parent) {
        this.parent = parent;
    }

    /** Une ligne de touche (rien si le mod qui la porte est absent). */
    private void touche(KeyMapping km, String label, String aide) {
        if (km == null) return;
        String l = label != null ? label : Component.translatable(km.getName()).getString();
        int btnW = Math.min(KEY_BTN_W, controlW());
        row(l, aide, (cx, cy, cw) -> new KeybindButton(cx + cw - btnW, cy, btnW, 24, km));
    }

    private static String aide(KeyMapping km) {
        if (km == HudKeybinds.RADIO_PARLER) return "Maintenir : parler à l'escouade · appui bref : écran de l'escouade";
        if (km == HudKeybinds.ANIMATIONS) return "Démarches et émotes";
        if (km == HudKeybinds.PHOTO) return "La galerie s'ouvre depuis le mode photo";
        if (km == HudKeybinds.COURSE_CHAKRA) return "Un appui la lance, un autre l'arrête (aussi : 3 appuis sur le sprint)";
        if (km == HudKeybinds.PICKUP) return "Seulement s'il y a quelque chose à faire, sinon la touche garde son rôle";
        if (km == HudKeybinds.FACTION) return "Ta section, ton village, les annonces ; la même touche referme";
        return null;
    }

    @Override
    protected void build() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;

        section("Souris");

        // Sensibilité — vanilla stocke 0..1 où 1 = "HYPERSPEED" (×2).
        int sensPct = (int) Math.round(o.sensitivity().get() * 200);
        row("Sensibilité", null,
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                sensPct, 0, 200, "%",
                v -> { o.sensitivity().set(v / 200.0); o.save(); }));

        row("Inverser l'axe Y", "Haut/bas de la souris inversés",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.invertMouseY().get(),
                v -> { o.invertMouseY().set(v); o.save(); }));

        section("Déplacement");

        row("Saut automatique", "Grimpe les blocs sans appuyer sur Saut",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.autoJump().get(),
                v -> { o.autoJump().set(v); o.save(); }));

        row("Sprint (bascule)", "Maintenir plutôt que basculer si désactivé",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.toggleSprint().get(),
                v -> { o.toggleSprint().set(v); o.save(); }));

        row("Accroupi (bascule)", null,
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.toggleCrouch().get(),
                v -> { o.toggleCrouch().set(v); o.save(); }));

        section("Commandes Reborn");

        // Carte des touches du 15/10 (docs/technique/MOD_HUD.md §2), dans l'ordre de la carte : les touches Minecraft
        // que Reborn détourne (sacoche, vue, allures) sont listées à leur place. Clic → écoute la prochaine touche
        // (Échap = aucune).
        touche(o.keyInventory, "Sacoche", null);
        for (KeyMapping km : HudKeybinds.REBORN_KEYS) {
            if (km == HudKeybinds.FREELOOK) touche(o.keyTogglePerspective, "Vue", "Épaule / 1re personne ; côté et cadrage : onglet Caméra");
            if (km == HudKeybinds.DASH) touche(o.keySprint, "Allures", "Maintenir : trot · 2 appuis : sprint · 3 appuis : course de chakra");
            touche(km, null, aide(km));
        }

        section("Autres mods");
        touche(KeyMapping.get("key.reborn-ost.open_menu"), "Menu OST", "Musiques du serveur");
        touche(KeyMapping.get("key.plasmovoice.settings"), "Plasmo Voice", "Micro, casque et touches de la voix : dans ce menu");
        touche(KeyMapping.get("key.reborn-hud.staff_panel"), "Poste de garde", "Staff seulement");
        labelRow("Toutes les touches", "Minecraft et les autres mods : Options → Commandes");

        spacer(4);
    }
}
