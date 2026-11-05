package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.resources.language.LanguageInfo;

import java.util.UUID;

/**
 * Onglet COMPTE (gabarit o2 du lot 3) : Compte, Discord, Langue, Accessibilité, Avancé. Reprend les onglets Compte,
 * Discord et Minecraft d'avant (CADRAGE §8.2, §8.4) : l'onglet Minecraft et ses six liens disparaissent ; seule reste,
 * dans Avancé, la porte de secours « Réglages avancés de Minecraft » (options de Minecraft et des autres mods, Sodium
 * compris, pour le staff et le build), qui ouvre le vrai écran sans redirection ({@link RedirectionOptions}).
 */
public final class AccountTab {

    private AccountTab() {}

    public static final int COMPTE = 0, DISCORD = 1, LANGUE = 2, ACCESSIBILITE = 3, AVANCE = 4;

    public static OngletPages onglet(Screen ecran) {
        return new OngletPages(
            new OngletPages.Page("Compte", new PageReglages(AccountTab::compte), null),
            new OngletPages.Page("Discord", new DiscordTab(), null),
            new OngletPages.Page("Langue", new PageReglages(p -> langue(p, ecran)), null),
            new OngletPages.Page("Accessibilité", new PageReglages(p -> accessibilite(p, ecran)),
                AccountTab::resetAccessibilite),
            new OngletPages.Page("Avancé", new PageReglages(p -> avance(p, ecran)), null));
    }

    // ─── Compte ──────────────────────────────────────────
    /**
     * Identité Minecraft en lecture seule (pseudo + UUID réels, lus depuis la session). L'ancien « Identifiant Reborn
     * RBN-xxxx » était un placeholder inventé localement : retiré en attendant l'API /v1/me.
     */
    private static void compte(PageReglages p) {
        Minecraft mc = Minecraft.getInstance();
        String username = "—";
        String uuid = "—";
        if (mc != null && mc.getUser() != null) {
            username = mc.getUser().getName();
            UUID id = mc.getUser().getProfileId();
            if (id != null) uuid = id.toString();
        }
        p.valueRow("Pseudo Minecraft", "Depuis ton compte Microsoft.", username, Colors.WHITE_PURE);
        p.valueRow("UUID", null, uuid, Colors.FOREGROUND_MUTED);
    }

    // ─── Langue ──────────────────────────────────────────
    private static void langue(PageReglages p, Screen ecran) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        String nom = mc.options.languageCode;
        try {
            LanguageInfo li = mc.getLanguageManager().getLanguage(mc.getLanguageManager().getSelected());
            if (li != null) nom = li.toComponent().getString();
        } catch (RuntimeException ignored) {
            // nom de code par défaut
        }
        p.valeur("Langue du jeu", "Textes de Minecraft et des autres mods ; les menus Reborn restent en français.", nom);
        p.action("Changer de langue", null, "Choisir", () -> RedirectionOptions.ouvrirVanilla(
            new LanguageSelectScreen(ecran, mc.options, mc.getLanguageManager())));
    }

    // ─── Accessibilité ───────────────────────────────────
    private static void accessibilite(PageReglages p, Screen ecran) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;
        p.bascule("Contraste élevé", "Interface de Minecraft plus contrastée.", o.highContrast().get(),
            v -> { o.highContrast().set(v); o.save(); });
        p.bascule("Contour des blocs contrasté", "Le bloc visé ressort mieux.", o.highContrastBlockOutline().get(),
            v -> { o.highContrastBlockOutline().set(v); o.save(); });
        p.curseur("Fond du texte", "Opacité du fond derrière le texte de Minecraft.",
            (int) Math.round(o.textBackgroundOpacity().get() * 100), 0, 100, "%",
            v -> { o.textBackgroundOpacity().set(v / 100.0); o.save(); });
        p.bascule("Écran de chargement sombre", null, o.darkMojangStudiosBackground().get(),
            v -> { o.darkMojangStudiosBackground().set(v); o.save(); });
        p.action("Toutes les options d'accessibilité", "L'écran de Minecraft (narrateur, sous-titres…).", "Ouvrir",
            () -> RedirectionOptions.ouvrirVanilla(new AccessibilityOptionsScreen(ecran, o)));
    }

    private static void resetAccessibilite() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;
        o.highContrast().set(false);
        o.highContrastBlockOutline().set(false);
        o.textBackgroundOpacity().set(0.5);
        o.darkMojangStudiosBackground().set(false);
        o.save();
    }

    // ─── Avancé ──────────────────────────────────────────
    private static void avance(PageReglages p, Screen ecran) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        p.action("Réglages avancés de Minecraft",
            "Options de Minecraft et des autres mods (Sodium…), pour le staff et le build.", "Ouvrir",
            () -> RedirectionOptions.ouvrirVanilla(new OptionsScreen(ecran, mc.options, mc.level != null)));
        p.action("Toutes les touches de Minecraft", "L'écran Commandes d'origine.", "Ouvrir",
            () -> RedirectionOptions.ouvrirVanilla(new KeyBindsScreen(ecran, mc.options)));
        p.valeur("Packs de ressources", "Gérés par le launcher Reborn.", "Automatiques");
    }
}
