package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.MainMenuFlow;
import fr.reborn.hud.menu.OSTPlayer;
import fr.reborn.hud.menu.RebornBranding;
import fr.reborn.hud.menu.ServerInfoState;
import fr.reborn.hud.menu.widget.DynamicPlayerBackground;
import fr.reborn.hud.menu.widget.IconButton;
import fr.reborn.hud.menu.widget.MainMenuRenderer;
import fr.reborn.hud.menu.widget.MenuEntryButton;
import fr.reborn.hud.menu.widget.OSTPlaylistOverlay;
import fr.reborn.hud.menu.widget.OSTPlayerV2;
import fr.reborn.hud.menu.widget.OSTVolumePopup;
import fr.reborn.hud.menu.widget.QuitConfirmScreen;
import fr.reborn.hud.menu.widget.ServerPickerWidget;
import fr.reborn.hud.menu.widget.SplashOverlay;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.world.SelectWorldScreen;
import net.minecraft.client.gui.components.ClickableWidget;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Refonte du title screen — layout v5 façon Paladium Reforged, piloté par
 * {@link MainMenuFlow} (SPLASH → MENU).
 *
 * <p>Stratégie :
 * <ol>
 *   <li>{@code init()} : on retire TOUS les widgets vanilla, on repose
 *       l'étage courant ({@link MainMenuFlow#onTitleInit()}), puis on
 *       ajoute nos widgets : les entrées du menu vertical gauche (JOUER /
 *       NEWS / BOUTIQUE / OPTIONS / QUITTER), les contrôles OST (révélés
 *       au hover de la marque top-left) et leurs overlays.</li>
 *   <li>{@code render()} : le panorama vanilla est masqué par le BG MCEF
 *       de {@link MainMenuRenderer}. On calcule l'étage + la révélation
 *       OST, on ajuste la visibilité des widgets en conséquence, on dessine
 *       le chrome passif puis on re-render les widgets par-dessus.</li>
 * </ol>
 *
 * <p>Le passage SPLASH → MENU (« appuie sur une touche ») est câblé dans
 * {@code RebornHudClient} via les événements clavier/souris du Screen API
 * Fabric (le mixin ne peut pas hooker {@code keyPressed}, hérité de
 * {@link Screen} et non redéclaré par {@link TitleScreen}).
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/title-mixin-v5");

    /** Entrées du menu vertical gauche (visibles seulement en MENU). */
    @Unique
    private final List<MenuEntryButton> reborn$menuEntries = new ArrayList<>();

    /** Contrôles OST (play / volume / playlist) — visibles au hover. */
    @Unique
    private final List<ClickableWidget> reborn$ostControls = new ArrayList<>();

    /** Widgets divers dont la visibilité suit l'étage MENU (dev solo). */
    @Unique
    private final List<ClickableWidget> reborn$menuOnly = new ArrayList<>();

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void reborn$rebuildMenu(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        MainMenuFlow.onTitleInit();

        // 1. Drop TOUS les widgets vanilla — on reconstruit from scratch.
        List<Element> toRemove = new ArrayList<>();
        for (Element child : this.children()) {
            if (child instanceof ClickableWidget) {
                toRemove.add(child);
            }
        }
        for (Element e : toRemove) {
            this.remove(e);
        }
        reborn$menuEntries.clear();
        reborn$ostControls.clear();
        reborn$menuOnly.clear();

        // 2. Menu vertical gauche (ordre Paladium).
        buildMenuEntries(client);

        // 3. Contrôles OST + overlays (ancrés sous la marque top-left).
        int ostX = MainMenuRenderer.ostPanelX();
        int ostY = MainMenuRenderer.ostPanelY();
        for (IconButton ctrl : OSTPlayerV2.buildControls(ostX, ostY)) {
            this.addRenderableWidget(ctrl);
            reborn$ostControls.add(ctrl);
        }
        int panelBottom = ostY + OSTPlayerV2.CARD_H;
        int[] volAnchor = OSTPlayerV2.volumeButtonAnchor(ostX, ostY);
        OSTVolumePopup volPopup = new OSTVolumePopup(
            volAnchor[0] - OSTVolumePopup.WIDTH / 2, panelBottom + 6);
        this.addRenderableWidget(volPopup);
        reborn$ostControls.add(volPopup);

        int playlistH = Math.min(360, this.height - panelBottom - 16);
        OSTPlaylistOverlay playlist = new OSTPlaylistOverlay(ostX, panelBottom + 6, playlistH);
        this.addRenderableWidget(playlist);
        reborn$ostControls.add(playlist);

        // 4. DEV ONLY — bouton "Solo (Dev)" top-right pour tester en solo.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()
                || Boolean.getBoolean("reborn.devMenu")) {
            int quitSize = 16;
            IconButton soloDev = new IconButton(
                this.width - quitSize - 18, 44, quitSize,
                IconPack::play, "Solo (Dev)", true,
                b -> client.setScreen(new SelectWorldScreen(this))
            )
                .ghost()
                .withIdleColor(Colors.WARNING)
                .withHoverColor(Colors.WHITE_PURE)
                .withTooltipPlacement(IconButton.TooltipPlacement.LEFT);
            this.addRenderableWidget(soloDev);
            reborn$menuOnly.add(soloDev);
        }

        LOG.info("title screen v5 : {} vanilla retirés, {} entrées menu, étage={}",
            toRemove.size(), reborn$menuEntries.size(), MainMenuFlow.stage());
    }

    /** Compteur Y courant pendant le build du menu (évite un état partagé). */
    @Unique
    private int reborn$nextEntryY = 0;

    @Unique
    private void buildMenuEntries(Minecraft client) {
        // 5 entrées empilées (ordre Paladium). Pas de record/inner-class ici
        // volontairement : Mixin gère mal les classes locales dans un mixin.
        reborn$nextEntryY = MainMenuRenderer.menuStartY(this.height, 5);

        // JOUER = entrée primaire (plus grosse que les autres).
        MenuEntryButton jouer = reborn$addEntry(client, "JOUER", false,
            MainMenuRenderer.MENU_ENTRY_SCALE_PRIMARY, MainMenuRenderer.MENU_ENTRY_H_PRIMARY,
            b -> RebornBranding.connectToReborn(client, this));

        // Staff + serveur dev → au survol de JOUER on affiche un popup de choix
        // Build/Dev (à droite) AU LIEU du compteur. Sinon (joueur normal) : le
        // compteur du serveur principal comme avant.
        boolean staffPicker = RebornBranding.serverToggleAvailable();
        if (!staffPicker) {
            jouer.withHoverInfo(() -> {
                ServerInfoState s = ServerInfoState.INSTANCE;
                return s.isOnline() ? s.getPlayers() + " EN LIGNE" : "HORS LIGNE";
            });
        }

        float sc = MainMenuRenderer.MENU_ENTRY_SCALE;
        int hh = MainMenuRenderer.MENU_ENTRY_H;
        reborn$addEntry(client, "NEWS", true, sc, hh, b -> {});
        reborn$addEntry(client, "BOUTIQUE", true, sc, hh, b -> {});
        reborn$addEntry(client, "OPTIONS", false, sc, hh,
            b -> client.setScreen(new ConfigShellScreen(this)));
        reborn$addEntry(client, "QUITTER", false, sc, hh,
            b -> client.setScreen(new QuitConfirmScreen(this)));

        // Popup sélecteur ancré sur JOUER — ajouté en dernier (rendu par-dessus).
        // Le menu garde sa disposition d'origine (aucune ligne fixe insérée).
        if (staffPicker) {
            ServerPickerWidget picker = new ServerPickerWidget(jouer);
            this.addRenderableWidget(picker);
            reborn$menuOnly.add(picker);
        }
    }

    @Unique
    private MenuEntryButton reborn$addEntry(Minecraft client, String label,
                                            boolean placeholder, float scale, int height,
                                            net.minecraft.client.gui.components.Button.PressAction action) {
        int w = Math.max(140,
            MenuEntryButton.labelWidth(client.textRenderer, label, scale) + 24);
        MenuEntryButton entry = new MenuEntryButton(
            MainMenuRenderer.MENU_X, reborn$nextEntryY, w, height, label, scale, action);
        if (placeholder) entry.placeholder();
        this.addRenderableWidget(entry);
        reborn$menuEntries.add(entry);
        reborn$nextEntryY += height + MainMenuRenderer.MENU_ENTRY_GAP;
        return entry;
    }

    /**
     * Rendu Reborn par-dessus le panorama vanilla. Push Z+=400 pour passer
     * au-dessus de tout ce que vanilla a buffered (logo MC, splash text,
     * version Fabric, copyright).
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void reborn$renderOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        boolean menu = !MainMenuFlow.isSplash();
        boolean ostRevealed = menu && (
            MainMenuRenderer.isOverOstReveal(mouseX, mouseY)
                || OSTPlayer.INSTANCE.isVolumePopupOpen()
                || OSTPlayer.INSTANCE.isPlaylistOpen());

        // Ping serveur (pour le compteur joueurs sur JOUER) tant qu'on est
        // sur le menu — le refresh est throttlé à 30s en interne.
        if (menu) {
            ServerInfoState.INSTANCE.maybeRefresh();
        }

        // Visibilité des widgets selon l'étage / la révélation OST.
        for (MenuEntryButton e : reborn$menuEntries) {
            e.visible = menu;
            e.active = menu;
        }
        for (ClickableWidget c : reborn$ostControls) {
            c.visible = ostRevealed;
            c.active = ostRevealed;
        }
        for (ClickableWidget c : reborn$menuOnly) {
            c.visible = menu;
            c.active = menu;
        }

        context.pose().pushMatrix();
        context.pose().translate(0, 0, 400);

        if (MainMenuFlow.isSplash()) {
            // SPLASH : fond MCEF → flou gaussien du framebuffer → contenu net.
            // `applyBlur` (hérité de Screen) applique le post-effect de flou du
            // menu (réglage « Menu background blurriness ») sur tout ce qui est
            // déjà dans le framebuffer — d'où le flou du décor derrière le logo.
            DynamicPlayerBackground.render(context, this.width, this.height);
            context.draw();
            this.applyBlur(delta);
            SplashOverlay.render(context, this.width, this.height);
        } else {
            // Chrome menu (BG MCEF + chrome + widgets par-dessus).
            MainMenuRenderer.render(context, this.width, this.height, mouseX, mouseY, ostRevealed);
            for (Element e : this.children()) {
                if (e instanceof ClickableWidget cw && cw.visible) {
                    cw.render(context, mouseX, mouseY, delta);
                }
            }
        }

        context.pose().popMatrix();
    }
}
