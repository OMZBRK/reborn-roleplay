package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.IconTextures;
import fr.reborn.hud.menu.RebornBranding;
import fr.reborn.hud.menu.widget.IconButton;
import fr.reborn.hud.menu.widget.MainMenuRenderer;
import fr.reborn.hud.menu.widget.OSTPlayerV2;
import fr.reborn.hud.menu.widget.OSTPlaylistOverlay;
import fr.reborn.hud.menu.widget.OSTVolumePopup;
import fr.reborn.hud.menu.widget.PressSpacePrompt;
import fr.reborn.hud.menu.widget.QuitConfirmScreen;
import fr.reborn.hud.menu.screens.RebornOptionsScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
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
 * Refonte complète du title screen — implémentation du design v2
 * (cf {@code reborn-design-prep/minecraft-main-menu/main-menu.jsx}).
 *
 * <p>Strategie :
 * <ol>
 *   <li>{@code init()} : on supprime TOUS les widgets vanilla (Singleplayer,
 *       Multiplayer, Realms, Mods, Options, Quit, accessibility, language)
 *       et tous les widgets v1 (RebornButton, OSTPlayerWidget,
 *       ServerInfoWidget). On ajoute les nouveaux widgets v2 :
 *       PressSpacePrompt, 4 contrôles OST (prev/play/next/playlist),
 *       3 IconButton bottom-right (settings/globe/discord), 1 IconButton
 *       top-right (X quit).</li>
 *   <li>{@code render()} : on garde le panorama vanilla en background
 *       (rendu par {@code super.render()}). Puis on dessine TOUT notre
 *       contenu par-dessus via {@link MainMenuRenderer} dans un push
 *       matrix Z+=400 pour passer au-dessus des éléments vanilla
 *       (logo MC, splash text, version Fabric, copyright Mojang).</li>
 *   <li>{@code keyPressed()} : intercept Espace → connecte au serveur.</li>
 * </ol>
 *
 * <p>Les widgets v1 ({@code RebornButton}, {@code OSTPlayerWidget},
 * {@code ServerInfoWidget}) ne sont plus instanciés. Leurs classes
 * restent en référence pour la PR #3+ qui peut s'en inspirer.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/title-mixin-v2");

    /**
     * Références aux 4 IconButton de contrôle OST. On les garde pour les
     * re-render dans {@code @Inject TAIL} après le background de la card
     * (qui les masquerait sinon, vu qu'ils sont dessinés en avance par
     * {@code super.render()}).
     */
    @Unique
    private final List<IconButton> reborn$ostControls = new ArrayList<>();

    /** Références aux 3 boutons centrés en bas (Settings/Globe/Discord) +
     *  bouton X quit top-right — re-rendered après MainMenuRenderer en TAIL. */
    @Unique
    private final List<IconButton> reborn$persistentIcons = new ArrayList<>();

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void reborn$rebuildMenu(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // 1. Drop TOUS les widgets vanilla et v1 — on reconstruit from scratch.
        List<Element> toRemove = new ArrayList<>();
        for (Element child : this.children()) {
            if (child instanceof ClickableWidget) {
                toRemove.add(child);
            }
        }
        for (Element e : toRemove) {
            this.remove(e);
        }
        LOG.info("title screen v2 : {} widgets vanilla/v1 retirés", toRemove.size());

        // 2. PressSpacePrompt — texte centré animé pulse.
        float responsive = MainMenuRenderer.responsiveScale(this.width);
        int promptW = PressSpacePrompt.computeWidth(this.textRenderer, responsive);
        int promptH = PressSpacePrompt.computeHeight(this.textRenderer, responsive);
        int promptX = (this.width - promptW) / 2;
        int promptY = MainMenuRenderer.promptY(this.height);
        this.addDrawableChild(new PressSpacePrompt(
            promptX, promptY, promptW, promptH,
            b -> RebornBranding.connectToReborn(client, this)
        ));

        // 3. OST controls (4 IconButton : prev / play|pause / next / playlist).
        int ostX = MainMenuRenderer.ostCardX(this.width);
        int ostY = MainMenuRenderer.ostCardY(this.height);
        reborn$ostControls.clear();
        for (IconButton ctrl : OSTPlayerV2.buildControls(ostX, ostY)) {
            this.addDrawableChild(ctrl);
            reborn$ostControls.add(ctrl);
        }

        // 4. Bottom-CENTER icons : 3 boutons épurés (Settings/Globe/Discord)
        //    ghost-style sans fond, centrés horizontalement sous ServerInfoMini.
        reborn$persistentIcons.clear();
        int iconSize = MainMenuRenderer.CENTER_ICON_SIZE;
        int iconGap = MainMenuRenderer.CENTER_ICON_SPACING;
        int totalW = 3 * iconSize + 2 * iconGap;
        int iconsY = MainMenuRenderer.centerIconsY(this.height);
        int startX = (this.width - totalW) / 2;

        IconButton btnSettings = new IconButton(
            startX, iconsY, iconSize,
            IconTextures.or(IconTextures.SETTINGS, IconPack::settings),
            "Paramètres", false,
            b -> client.setScreen(new RebornOptionsScreen(this))
        ).ghost();
        IconButton btnGlobe = new IconButton(
            startX + (iconSize + iconGap), iconsY, iconSize,
            IconTextures.or(IconTextures.GLOBE, IconPack::globe),
            "Site web", false,
            b -> RebornBranding.openSite()
        ).ghost();
        IconButton btnDiscord = new IconButton(
            startX + 2 * (iconSize + iconGap), iconsY, iconSize,
            IconTextures.or(IconTextures.DISCORD, IconPack::discord),
            "Discord", false,
            b -> RebornBranding.openDiscord()
        ).ghost();
        this.addDrawableChild(btnSettings);
        this.addDrawableChild(btnGlobe);
        this.addDrawableChild(btnDiscord);
        reborn$persistentIcons.add(btnSettings);
        reborn$persistentIcons.add(btnGlobe);
        reborn$persistentIcons.add(btnDiscord);

        // 5. Top-right quit (X ghost 16px, hover rouge danger, tooltip LEFT).
        // Y=22 (au lieu de 14) pour ne pas coller au bord supérieur de
        // la fenêtre — donne plus de respiration visuelle au coin.
        int quitSize = 16;
        IconButton quit = new IconButton(
            this.width - quitSize - 18, 22, quitSize,
            IconTextures.or(IconTextures.CLOSE, IconPack::close),
            "Quitter Reborn", true,
            b -> client.setScreen(new QuitConfirmScreen(this))
        )
            .ghost()
            .withIdleColor(Colors.WHITE_PURE)
            .withHoverColor(Colors.DANGER)
            .withTooltipPlacement(IconButton.TooltipPlacement.LEFT);
        this.addDrawableChild(quit);
        reborn$persistentIcons.add(quit);

        // 6. Overlays OST (volume popup + playlist). Toujours présents dans
        //    le screen mais conditionnellement visibles selon OSTPlayer state.
        int[] volAnchor = OSTPlayerV2.volumeButtonAnchor(ostX, ostY);
        OSTVolumePopup volPopup = new OSTVolumePopup(
            volAnchor[0] - OSTVolumePopup.WIDTH / 2,
            volAnchor[1] - OSTVolumePopup.HEIGHT - 8
        );
        this.addDrawableChild(volPopup);

        // Playlist : 260px de large vs card 220px → ancré au right-edge du
        // card (sinon avec la card en bottom-right, le surplus 40px sort
        // de l'écran).
        int playlistH = Math.min(360, this.height - 80);
        int playlistX = ostX + OSTPlayerV2.CARD_W - OSTPlaylistOverlay.WIDTH;
        OSTPlaylistOverlay playlist = new OSTPlaylistOverlay(
            playlistX,
            ostY - playlistH - 8,
            playlistH
        );
        this.addDrawableChild(playlist);

        // 7. DEV ONLY — bouton "Solo (Dev)" à coté du X close pour pouvoir
        //    tester l'ESC menu sans avoir besoin d'un compte MC vrai/serveur.
        //    Détecté uniquement en runClient via FabricLoader. Invisible
        //    sur le build de prod.
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            IconButton soloDev = new IconButton(
                this.width - quitSize - 18 - quitSize - 6, 22, quitSize,
                IconPack::play, "Solo (Dev)", true,
                b -> client.setScreen(new SelectWorldScreen(this))
            )
                .ghost()
                .withIdleColor(Colors.WARNING)
                .withHoverColor(Colors.WHITE_PURE)
                .withTooltipPlacement(IconButton.TooltipPlacement.LEFT);
            this.addDrawableChild(soloDev);
            reborn$persistentIcons.add(soloDev);
        }
    }

    /**
     * Rendu Reborn par-dessus le panorama vanilla. On utilise @Inject TAIL
     * + push Z+=400 pour s'assurer que notre UI passe par-dessus tout ce
     * que vanilla a buffered (logo MC, splash text, drawText du copyright /
     * version Fabric).
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void reborn$renderOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);

        // Rend l'UI Reborn par-dessus tout ce que vanilla a dessiné.
        // Le BackgroundRenderer couvre intégralement l'écran (gradient
        // bleu nuit), donc on doit ensuite re-render TOUS les widgets
        // cliquables (PressSpacePrompt, OST controls, OST overlays,
        // bottom icons, X close) par-dessus, sinon ils sont masqués.
        MainMenuRenderer.render(context, this.width, this.height);

        // Re-render tous les widgets cliquables par-dessus le background.
        // On itère sur children() pour ne rater aucun widget ajouté.
        for (Element e : this.children()) {
            if (e instanceof ClickableWidget cw) {
                cw.render(context, mouseX, mouseY, delta);
            }
        }

        context.getMatrices().pop();
    }

    // Note : pas de @Inject sur keyPressed — la méthode est déclarée dans
    // Screen parent, pas dans TitleScreen, donc Mixin ne peut pas la hooker
    // depuis ce mixin enfant. Le shortcut Espace = connect sera rebranché
    // via setInitialFocus(promptWidget) ou un Mixin séparé sur Screen
    // dans une PR ultérieure. Pour l'instant le PressSpacePrompt reste
    // cliquable à la souris ; vanilla MC active de toute façon les boutons
    // focused via Espace, et le PressSpacePrompt est ajouté en premier
    // donc focused par défaut.
}
