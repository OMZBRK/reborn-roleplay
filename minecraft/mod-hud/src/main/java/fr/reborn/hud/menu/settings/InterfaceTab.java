package fr.reborn.hud.menu.settings;

import fr.reborn.hud.chat.ChatSettingsScreen;
import fr.reborn.hud.crosshair.CrosshairScreen;
import fr.reborn.hud.ui.HudEditScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * Onglet Interface — regroupe les trois éditeurs UI Reborn (HUD, Chat, Viseur)
 * sous une même catégorie logique. Chaque section ouvre son éditeur dédié.
 */
public class InterfaceTab extends SectionedTab {

    private final Screen parent;

    public InterfaceTab(Screen parent) {
        this.parent = parent;
    }

    @Override
    protected void build() {
        section("HUD");
        labelRow("Éditeur HUD", "Placez et redimensionnez vos éléments d'interface");
        actionButton("→ Ouvrir l'éditeur HUD",
            () -> open(new HudEditScreen(parent)));

        section("Chat");
        labelRow("Chat Reborn", "Onglets, filtres et apparence du chat");
        actionButton("→ Réglages du chat",
            () -> open(new ChatSettingsScreen(parent)));

        section("Viseur");
        labelRow("Éditeur de viseur", "Modèle, couleur, dynamique, hit-marker");
        actionButton("→ Ouvrir l'éditeur de viseur",
            () -> open(new CrosshairScreen(parent)));

        spacer(4);
    }

    private static void open(Screen screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.setScreen(screen);
    }
}
