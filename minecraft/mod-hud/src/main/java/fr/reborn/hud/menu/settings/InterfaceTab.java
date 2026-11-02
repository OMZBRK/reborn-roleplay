package fr.reborn.hud.menu.settings;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatSettingsScreen;
import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.crosshair.CrosshairScreen;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.ui.HudEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

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

        // Colonne bas-droite (lot 2) : mêmes interrupteurs que l'œil de l'éditeur de HUD (une seule source de vérité :
        // la visibilité de l'élément dans la configuration du HUD).
        section("Voix et touches");
        row("Qui parle", "Noms de ceux qui parlent autour de toi, en bas à droite",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy, visible(HudElement.QUI_PARLE),
                v -> montrer(HudElement.QUI_PARLE, v)));
        row("Rappels de touches", "Portée de la voix [H] et les touches utiles du moment",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy, visible(HudElement.RAPPELS),
                v -> montrer(HudElement.RAPPELS, v)));

        section("Tablist");
        row("Maintenir pour afficher", "Sinon : presser Tab ouvre le tablist interactif",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                RebornPrefs.INSTANCE.tablistHold,
                v -> { RebornPrefs.INSTANCE.tablistHold = v; RebornPrefs.INSTANCE.save(); }));

        section("Objets au sol");
        row("Lueur de rareté", "Petit halo sous les objets au sol, à la couleur de leur rareté",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                RebornPrefs.INSTANCE.aurasSol,
                v -> { RebornPrefs.INSTANCE.aurasSol = v; RebornPrefs.INSTANCE.save(); }));

        // Couleur de l'aura de méditation : plus de choix libre ici depuis le 19/10, elle deviendra un article de la
        // boutique de compte (RBCoins) ; en attendant, le staff l'attribue (/meditation aura <joueur> <couleur>).
        // L'écran de choix (perso/PersoAura) est gardé pour la boutique.

        section("Noms au-dessus des têtes");
        row("Style", "Apparence du nom RP des autres joueurs",
            (cx, cy, cw) -> new SegmentedControl(cx, cy, cw, 24,
                new SegmentedControl.Option[] {
                    new SegmentedControl.Option("0", "Détouré"),
                    new SegmentedControl.Option("1", "Filet doré"),
                    new SegmentedControl.Option("2", "Village"),
                    new SegmentedControl.Option("3", "Ruban"),
                },
                String.valueOf(RebornPrefs.INSTANCE.nametagStyle),
                v -> { RebornPrefs.INSTANCE.nametagStyle = Integer.parseInt(v); RebornPrefs.INSTANCE.save(); }));

        spacer(4);
    }

    private static void open(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreenAndShow(screen);
    }

    private static boolean visible(HudElement e) {
        try {
            return RebornHudClient.config().stateOf(e).visible();
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private static void montrer(HudElement e, boolean v) {
        HudConfig cfg = RebornHudClient.config();
        cfg.setState(e, cfg.stateOf(e).withVisible(v));
        cfg.save();
    }
}
