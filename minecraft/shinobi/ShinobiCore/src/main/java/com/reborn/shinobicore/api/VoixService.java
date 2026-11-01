package com.reborn.shinobicore.api;

import java.util.UUID;

/**
 * <b>Couper / rétablir la voix</b> d'un joueur (outil staff, décision du 31/10). La voix passe par Plasmo Voice, que
 * ShinobiCore ne connaît pas : ShinobiRadio (le seul plugin qui en dépend) publie ce service dans le
 * {@code ServicesManager} (mute de Plasmo : proximité <b>et</b> radio coupées, persistant, fin automatique). Service
 * optionnel, lu en différé par le Poste de garde : sans ShinobiRadio ou sans Plasmo, aucun service.
 *
 * <p>Journalisé par l'appelant (Poste de garde : {@code STAFF VOIX_COUPEE / VOIX_RETABLIE}) ; ShinobiRadio journalise
 * lui-même les {@code /vmute} / {@code /vunmute} tapés dans Plasmo et les fins automatiques. <b>Jamais de contenu
 * vocal.</b> Thread principal.
 */
@Stable
public interface VoixService {

    /** Fin d'une coupure sans échéance ({@code /vmute} permanent de Plasmo) : une coupure permanente n'est pas « libre ». */
    long PERMANENTE = Long.MAX_VALUE;

    /** Une coupure en cours : fin (epoch ms, {@link #PERMANENTE} si sans fin), motif, compte du staff (null = console). */
    record Coupure(long finEpochMs, String motif, UUID par) {
        public boolean permanente() { return finEpochMs == PERMANENTE; }

        /** Millisecondes restantes ({@link Long#MAX_VALUE} si permanente). */
        public long resteMs() {
            return permanente() ? Long.MAX_VALUE : Math.max(0, finEpochMs - System.currentTimeMillis());
        }
    }

    /** Plasmo Voice prêt (addon initialisé) ? Faux : toute coupure est refusée. */
    boolean disponible();

    /**
     * Coupe la voix pendant {@code minutes}, sans message de Plasmo (l'appelant prévient le joueur). La cible doit être
     * <b>en ligne</b> (Plasmo l'exige).
     *
     * @return fin de la coupure (epoch ms), ou {@code -1} si refusée (indisponible, hors ligne, durée ≤ 0)
     */
    long couper(UUID cible, UUID staff, int minutes, String motif);

    /** Rétablit la voix, hors ligne compris. Faux si elle n'était pas coupée ou si le service est indisponible. */
    boolean retablir(UUID cible);

    /** La coupure en cours, ou {@code null} si la voix est libre (une coupure échue, pas encore levée, compte libre). */
    Coupure etat(UUID cible);

    default boolean coupee(UUID cible) { return etat(cible) != null; }
}
