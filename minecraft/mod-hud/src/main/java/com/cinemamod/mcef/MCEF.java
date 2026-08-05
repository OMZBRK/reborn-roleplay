package com.cinemamod.mcef;

import java.util.function.Consumer;

/**
 * ⚠️ STUB 26.1 (phase 2) — l'ancien MCEF (com.cinemamod) est bloqué en 1.21.4.
 * Le fond menu Chromium est neutralisé le temps du port du cœur UI de mod-hud.
 * scheduleForInit ne signale jamais l'init → aucun browser n'est créé → le
 * fond dynamique ne s'affiche pas (menu vanilla). À remplacer par le fork
 * net.dimaskama:mcef-modern (API net.dimaskama.mcef.api.*) en phase 2.
 */
public final class MCEF {
    private MCEF() {}

    /** No-op : ne rappelle jamais le callback (browser jamais initialisé). */
    public static void scheduleForInit(Consumer<Boolean> onInit) {}

    /** No-op : renvoie un browser inerte. */
    public static MCEFBrowser createBrowser(String url, boolean transparent) {
        return new MCEFBrowser();
    }
}
