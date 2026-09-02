package fr.reborn.hud.runtime;

import fr.reborn.hud.menu.character.CharacterPayload;
import fr.reborn.hud.menu.inventory.InventoryPayload;
import fr.reborn.hud.menu.tablist.TablistData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Détecte si le serveur courant est un serveur Reborn RP « complet » (dev/prod)
 * plutôt qu'un serveur de <b>build</b> créatif staff (ShinobiCore {@code mode=build})
 * ou un serveur non-Reborn.
 *
 * <p>Signal : un serveur RP complet déclare ses canaux RP <i>entrants</i>
 * ({@code reborn:character}, {@code reborn:inventory}) et pousse le feed tablist.
 * Un serveur build ne démarre pas ces managers (voir {@code ShinobiCore.onEnable}
 * gardé par {@code mode=build}) → aucun de ces signaux n'est présent.
 *
 * <p>Quand {@link #rpFeaturesEnabled()} est faux, on éteint toute l'UI RP
 * <b>always-on</b> (caméra épaule, viseur custom, masquage vie/faim vanilla,
 * panneau vitals) pour que le builder bâtisse à la vanilla. Les features RP
 * pilotées par le serveur (character selector, sacoche, HUD vitals live…)
 * s'éteignent déjà d'elles-mêmes faute de données / de canal.
 */
public final class RebornSession {

    private RebornSession() {}

    /**
     * Vrai si on est connecté à un serveur Reborn RP complet. Adresse-agnostique :
     * repose sur les canaux effectivement déclarés par le serveur, pas sur une
     * sélection Build/Dev pré-connexion (qui n'existe que pour le staff).
     */
    public static boolean rpFeaturesEnabled() {
        try {
            if (ClientPlayNetworking.canSend(CharacterPayload.ID)) return true;
            if (ClientPlayNetworking.canSend(InventoryPayload.ID)) return true;
        } catch (Throwable ignored) {
            // canSend peut lever hors-jeu (pas de connexion) → RP off.
        }
        return TablistData.hasData();
    }
}
