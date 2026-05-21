package fr.reborn.integrity.mixin;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.IconPack;
import fr.reborn.integrity.ui.RebornBranding;
import fr.reborn.integrity.ui.menu.IconButton;
import fr.reborn.integrity.ui.menu.MainMenuRenderer;
import fr.reborn.integrity.ui.menu.OSTPlayerV2;
import fr.reborn.integrity.ui.menu.PressSpacePrompt;
import fr.reborn.integrity.ui.menu.QuitConfirmScreen;
import fr.reborn.integrity.ui.screens.RebornOptionsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
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

    private static final Logger LOG = LoggerFactory.getLogger("reborn-integrity/title-mixin-v2");

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

        // 4. Bottom-right icons : 3 boutons épurés (Settings/Globe/Discord)
        //    ghost-style sans fond, alignés à DROITE sous les credits.
        reborn$persistentIcons.clear();
        int iconSize = MainMenuRenderer.RIGHT_ICON_SIZE;
        int iconGap = MainMenuRenderer.RIGHT_ICON_SPACING;
        int totalW = 3 * iconSize + 2 * iconGap;
        int rightEdge = MainMenuRenderer.rightIconsRightEdge(this.width);
        int iconsY = MainMenuRenderer.rightIconsY(this.height);
        int startX = rightEdge - totalW;

        IconButton btnSettings = new IconButton(
            startX, iconsY, iconSize,
            IconPack::settings, "Paramètres", false,
            b -> client.setScreen(new RebornOptionsScreen(this))
        ).ghost();
        IconButton btnGlobe = new IconButton(
            startX + (iconSize + iconGap), iconsY, iconSize,
            IconPack::globe, "Site web", false,
            b -> RebornBranding.openSite()
        ).ghost();
        IconButton btnDiscord = new IconButton(
            startX + 2 * (iconSize + iconGap), iconsY, iconSize,
            IconPack::discord, "Discord", false,
            b -> RebornBranding.openDiscord()
        ).ghost();
        this.addDrawableChild(btnSettings);
        this.addDrawableChild(btnGlobe);
        this.addDrawableChild(btnDiscord);
        reborn$persistentIcons.add(btnSettings);
        reborn$persistentIcons.add(btnGlobe);
        reborn$persistentIcons.add(btnDiscord);

        // 5. Top-right quit (X ghost 24px, hover rouge danger).
        int quitSize = 24;
        IconButton quit = new IconButton(
            this.width - quitSize - 18, 18, quitSize,
            IconPack::close, "Quitter Reborn", true,
            b -> client.setScreen(new QuitConfirmScreen(this))
        )
            .ghost()
            .withIdleColor(Colors.FOREGROUND_MUTED)
            .withHoverColor(Colors.DANGER);
        this.addDrawableChild(quit);
        reborn$persistentIcons.add(quit);
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

        // Rend l'UI Reborn par-dessus tout ce que vanilla a dessiné
        // (panorama + logo MC + splash + copyright). Le logo central Reborn
        // est positionné pour couvrir le logo MC vanilla. Les credits
        // couvrent la version Fabric + copyright Mojang en bas. Le splash
        // jaune dépasse à droite — accepté volontairement.
        MainMenuRenderer.render(context, this.width, this.height);

        // Re-render les 4 IconButton OST controls par-dessus le BG de la
        // card OST. Sans ça, le BG (dessiné par MainMenuRenderer) masque
        // les contrôles qui ont été rendus en avance par super.render().
        for (IconButton ctrl : reborn$ostControls) {
            ctrl.render(context, mouseX, mouseY, delta);
        }
        // Idem pour les icons persistants (bottom-center + top-right X) —
        // ils sont COUVERTS par les bandeaux gradient haut/bas de
        // MainMenuRenderer. On les re-render au-dessus.
        for (IconButton ic : reborn$persistentIcons) {
            ic.render(context, mouseX, mouseY, delta);
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
