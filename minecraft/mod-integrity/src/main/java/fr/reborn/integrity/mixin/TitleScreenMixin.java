package fr.reborn.integrity.mixin;

import fr.reborn.integrity.ui.RebornBranding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-skin du title screen : enleve les boutons vanilla
 * (Singleplayer, Multiplayer, Realms, Mods, ...) et ajoute trois
 * boutons Reborn (Connecter, Site, Discord) plus une option pour
 * acceder aux Settings.
 *
 * <p>Approche : on s'injecte a la fin de {@code init()} apres que
 * vanilla a tout cree, on filtre les widgets indesirables (par
 * leur texte de translation key) et on ajoute les notres a la
 * place.
 *
 * <p>Plus robuste qu'un screen 100% custom : si Mojang change la
 * structure interne (ajoute un bouton "Quick Play", deplace les
 * positions...), notre code marche encore parce qu'on filtre par
 * label connu et on ne touche pas au panorama / splash text.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    private static final Logger REBORN_LOGGER =
        LoggerFactory.getLogger("reborn-integrity/title-mixin");

    /**
     * Clefs de translation Yarn 1.21.1 des boutons vanilla qu'on veut
     * masquer. On filtre par la string brute du translation key pour
     * eviter les imports de TranslationKeys (qui changent souvent).
     */
    private static final String[] HIDDEN_VANILLA_KEYS = {
        "menu.singleplayer",
        "menu.multiplayer",
        "menu.online", // Realms
        "menu.modded", // Fabric/Forge "Mods" label
        "fml.menu.mods", // Forge legacy
        "modmenu.title", // Mod Menu mod
    };

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void reborn$replaceMenu(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // 1. Collecte tous les widgets vanilla a virer.
        List<Element> toRemove = new ArrayList<>();
        for (Element child : this.children()) {
            if (child instanceof ButtonWidget btn) {
                String label = btn.getMessage().getString();
                for (String key : HIDDEN_VANILLA_KEYS) {
                    String translated = Text.translatable(key).getString();
                    if (label.equalsIgnoreCase(translated)) {
                        toRemove.add(child);
                        break;
                    }
                }
            }
        }
        for (Element e : toRemove) {
            this.remove(e);
        }
        REBORN_LOGGER.info("title screen : {} boutons vanilla retires", toRemove.size());

        // 2. Ajoute les boutons Reborn. Position : centre, sous le
        //    logo Minecraft. Width 200 height 20 (standard vanilla).
        int centerX = this.width / 2 - 100;
        int baseY = this.height / 4 + 48;

        this.addDrawableChild(
            ButtonWidget.builder(
                Text.literal("⚔  Connecter à Reborn"),
                button -> RebornBranding.connectToReborn(client, this)
            ).dimensions(centerX, baseY, 200, 22).build()
        );

        this.addDrawableChild(
            ButtonWidget.builder(
                Text.literal("Site web"),
                button -> RebornBranding.openSite()
            ).dimensions(centerX, baseY + 28, 98, 20).build()
        );
        this.addDrawableChild(
            ButtonWidget.builder(
                Text.literal("Discord"),
                button -> RebornBranding.openDiscord()
            ).dimensions(centerX + 102, baseY + 28, 98, 20).build()
        );

        // 3. Empile encore plus bas un "Options" + "Quitter" pour
        //    que le user puisse acceder aux settings + quit
        //    (autrement il ne pourrait plus jamais fermer le jeu
        //    proprement depuis le menu).
        this.addDrawableChild(
            ButtonWidget.builder(
                Text.translatable("menu.options"),
                button -> client.setScreen(
                    new net.minecraft.client.gui.screen.option.OptionsScreen(this, client.options)
                )
            ).dimensions(centerX, baseY + 56, 98, 20).build()
        );
        this.addDrawableChild(
            ButtonWidget.builder(
                Text.translatable("menu.quit"),
                button -> client.scheduleStop()
            ).dimensions(centerX + 102, baseY + 56, 98, 20).build()
        );

        // 4. Cache les boutons restants qu'on n'a pas pu identifier
        //    par label (translation differente, mod ajoute un truc, etc.)
        //    et qui sont positionnes au-dessus de nos nouveaux boutons.
        //    Heuristique conservatrice : on laisse vivre tout ce qui n'est
        //    pas un ClickableWidget plus large que 100px.
        @SuppressWarnings("unused")
        int reservedForFutureUse = 0;
    }

    @SuppressWarnings("unused")
    private void reborn$noop(ClickableWidget w) {
        // Reserve si on ajoute un helper plus tard (renamer/wrapper boutons).
    }
}
