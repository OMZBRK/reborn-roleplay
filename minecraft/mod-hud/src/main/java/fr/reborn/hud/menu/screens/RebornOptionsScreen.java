package fr.reborn.hud.menu.screens;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.widget.IconButton;
import fr.reborn.hud.menu.settings.AccountTab;
import fr.reborn.hud.menu.settings.AudioTab;
import fr.reborn.hud.menu.settings.ControlsTab;
import fr.reborn.hud.menu.settings.DiscordTab;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.settings.SettingsTab;
import fr.reborn.hud.menu.settings.TabButton;
import fr.reborn.hud.menu.settings.VideoTab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Screen Paramètres Reborn — refonte v2 avec 5 tabs horizontaux.
 * Référence : {@code settings.jsx::SettingsScreen}.
 *
 * <p>Switch de tab via clearAndInit() — re-build complet de l'écran.
 * Simple et fiable, suffisant pour une UI statique sans animation
 * de transition.
 */
public class RebornOptionsScreen extends Screen {

    private final Screen parent;
    private final TabDef[] tabs;
    private int activeTabIdx;
    private SettingsTab activeTab;

    private static final int HEADER_H = 56;
    private static final int TABS_H = 36;
    private static final int CONTENT_PADDING_X = 80;
    private static final int CONTENT_TOP_PADDING = 36;

    private record TabDef(String id, String label, SettingsTab tab) {}

    public RebornOptionsScreen(Screen parent) {
        this(parent, 0);
    }

    public RebornOptionsScreen(Screen parent, int initialTabIdx) {
        super(Text.literal("Paramètres Reborn"));
        this.parent = parent;
        RebornPrefs.INSTANCE.ensureLoaded();
        this.tabs = new TabDef[] {
            new TabDef("video", "Vidéo", new VideoTab(this)),
            new TabDef("audio", "Audio", new AudioTab()),
            new TabDef("controls", "Contrôles", new ControlsTab(this)),
            new TabDef("discord", "Discord", new DiscordTab()),
            new TabDef("account", "Compte", new AccountTab()),
        };
        this.activeTabIdx = Math.max(0, Math.min(tabs.length - 1, initialTabIdx));
        this.activeTab = tabs[this.activeTabIdx].tab;
    }

    @Override
    protected void init() {
        // ─── Header : bouton retour gauche + X close droite ───
        this.addDrawableChild(new IconButton(
            18, 18, 18,
            IconPack::chevronLeft, "Retour", false,
            b -> close()
        ).ghost().withIdleColor(Colors.FOREGROUND_SUBTLE));

        this.addDrawableChild(new IconButton(
            this.width - 18 - 16, 18, 16,
            IconPack::close, "Fermer", true,
            b -> close()
        ).ghost()
            .withIdleColor(Colors.FOREGROUND_MUTED)
            .withHoverColor(Colors.DANGER)
            .withTooltipPlacement(IconButton.TooltipPlacement.LEFT));

        // ─── Tabs horizontaux ───
        int totalTabsW = Math.min(this.width - 80, 540);
        int tabW = totalTabsW / tabs.length;
        int startX = (this.width - totalTabsW) / 2;
        int tabsY = HEADER_H;

        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            this.addDrawableChild(new TabButton(
                startX + i * tabW, tabsY, tabW, TABS_H,
                tabs[i].label,
                () -> idx == activeTabIdx,
                b -> {
                    if (idx != activeTabIdx) {
                        activeTabIdx = idx;
                        activeTab = tabs[idx].tab;
                        this.clearAndInit();
                    }
                }
            ));
        }

        // ─── Layout du tab actif ───
        int contentX = CONTENT_PADDING_X;
        int contentY = HEADER_H + TABS_H + CONTENT_TOP_PADDING;
        int contentW = this.width - 2 * CONTENT_PADDING_X;
        activeTab.layout(contentX, contentY, contentW);
        for (ClickableWidget w : activeTab.widgets()) {
            this.addDrawableChild(w);
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, Colors.BACKGROUND);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        var tr = MinecraftClient.getInstance().textRenderer;

        // ─── Titre centré ───
        Text title = RebornFont.bold("PARAMÈTRES REBORN");
        float titleScale = 1.4f;
        int titleW = Math.round(tr.getWidth(title) * titleScale);
        int titleX = (this.width - titleW) / 2;
        int titleY = 18;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(titleX, titleY, 0);
        ctx.getMatrices().scale(titleScale, titleScale, 1f);
        ctx.drawText(tr, title, 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // ─── Séparations ───
        ctx.fill(40, HEADER_H - 1, this.width - 40, HEADER_H, Colors.BORDER);
        ctx.fill(40, HEADER_H + TABS_H, this.width - 40, HEADER_H + TABS_H + 1, Colors.BORDER);

        // ─── Contenu du tab actif (passif) ───
        int contentX = CONTENT_PADDING_X;
        int contentY = HEADER_H + TABS_H + CONTENT_TOP_PADDING;
        int contentW = this.width - 2 * CONTENT_PADDING_X;
        activeTab.renderPassive(ctx, contentX, contentY, contentW);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        RebornPrefs.INSTANCE.save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
