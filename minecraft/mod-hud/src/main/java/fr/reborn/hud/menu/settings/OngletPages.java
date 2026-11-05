package fr.reborn.hud.menu.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.List;

/**
 * Onglet des Paramètres fait de pages (une par sous-catégorie, gabarit o2 du lot 3) : la page choisie fait la mise en
 * page et le rendu, les autres attendent. Chaque page est un {@link SettingsTab} (en général une {@link PageReglages}
 * ou un onglet d'avant devenu page, comme {@link CameraTab}) avec, au besoin, sa remise aux défauts.
 */
public final class OngletPages implements SettingsTab, SousListe {

    /** Une sous-catégorie : nom dans la sous-liste, contenu, remise aux défauts (null : pas de « Réinitialiser »). */
    public record Page(String nom, SettingsTab contenu, Runnable reinitialiser) {}

    private final Page[] pages;
    private int choisie;

    public OngletPages(Page... pages) {
        this.pages = pages;
    }

    private SettingsTab page() {
        return pages[choisie].contenu();
    }

    // ─── SettingsTab : la page choisie ───────────────────
    @Override
    public void layout(int x, int y, int width) { page().layout(x, y, width); }

    @Override
    public List<AbstractWidget> widgets() { return page().widgets(); }

    @Override
    public int height() { return page().height(); }

    @Override
    public void renderPassive(GuiGraphicsExtractor ctx, int x, int y, int width) { page().renderPassive(ctx, x, y, width); }

    // ─── SousListe ───────────────────────────────────────
    @Override
    public int nombreSous() { return pages.length; }

    @Override
    public String nomSous(int i) { return pages[i].nom(); }

    @Override
    public int sousChoisie() { return choisie; }

    @Override
    public void choisirSous(int i) { choisie = Math.max(0, Math.min(pages.length - 1, i)); }

    @Override
    public boolean reinitialisable() { return pages[choisie].reinitialiser() != null; }

    @Override
    public void reinitialiserSous() {
        Runnable r = pages[choisie].reinitialiser();
        if (r != null) r.run();
    }
}
