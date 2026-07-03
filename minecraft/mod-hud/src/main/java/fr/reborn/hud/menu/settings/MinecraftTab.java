package fr.reborn.hud.menu.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.option.SoundOptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;

/**
 * Onglet « Minecraft » — <b>seule</b> porte vers les réglages vanilla de base.
 * Toutes les redirections vers les écrans Mojang sont centralisées ici
 * (l'{@code OptionsScreenMixin} masque déjà Resource Packs / Skin / Online,
 * non pertinents sur un serveur RP).
 */
public class MinecraftTab extends SectionedTab {

    private final Screen parent;

    public MinecraftTab(Screen parent) {
        this.parent = parent;
    }

    @Override
    protected void build() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        section("Réglages Minecraft");
        labelRow("Options de base", "Ouvre les écrans Mojang standards");
        actionButton("→ Options Minecraft (toutes)",
            () -> mc.setScreen(new OptionsScreen(parent, mc.options)));
        actionButton("→ Vidéo",
            () -> mc.setScreen(new VideoOptionsScreen(parent, mc, mc.options)));
        actionButton("→ Sons",
            () -> mc.setScreen(new SoundOptionsScreen(parent, mc.options)));
        actionButton("→ Commandes (touches)",
            () -> mc.setScreen(new ControlsOptionsScreen(parent, mc.options)));
        actionButton("→ Langue",
            () -> mc.setScreen(new LanguageOptionsScreen(parent, mc.options, mc.getLanguageManager())));
        actionButton("→ Accessibilité",
            () -> mc.setScreen(new AccessibilityOptionsScreen(parent, mc.options)));

        labelRow("Packs de ressources", "Gérés automatiquement par le launcher Reborn");

        spacer(4);
    }
}
