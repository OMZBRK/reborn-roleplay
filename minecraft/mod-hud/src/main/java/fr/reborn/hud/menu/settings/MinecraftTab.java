package fr.reborn.hud.menu.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

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
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        section("Réglages Minecraft");
        labelRow("Options de base", "Ouvre les écrans Mojang standards");
        actionButton("→ Options Minecraft (toutes)",
            () -> mc.setScreen(new OptionsScreen(parent, mc.options, false)));
        actionButton("→ Vidéo",
            () -> mc.setScreen(new VideoSettingsScreen(parent, mc, mc.options)));
        actionButton("→ Sons",
            () -> mc.setScreen(new SoundOptionsScreen(parent, mc.options)));
        actionButton("→ Commandes (touches)",
            () -> mc.setScreen(new KeyBindsScreen(parent, mc.options)));
        actionButton("→ Langue",
            () -> mc.setScreen(new LanguageSelectScreen(parent, mc.options, mc.getLanguageManager())));
        actionButton("→ Accessibilité",
            () -> mc.setScreen(new AccessibilityOptionsScreen(parent, mc.options)));

        labelRow("Packs de ressources", "Gérés automatiquement par le launcher Reborn");

        spacer(4);
    }
}
