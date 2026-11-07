package com.reborn.shinobicore.douane;

import com.reborn.shinobicore.douane.Aiguillage.Decision;
import com.reborn.shinobicore.douane.Aiguillage.Issue;
import com.reborn.shinobicore.douane.Aiguillage.Portee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aiguillage à la connexion (CANDIDATURE_ET_DOUANE.md §3.3) : staff avec réservation de douane (07/11). */
class AiguillageTest {

    private static final long DELAI = 10_000L;

    private static Decision staff(Portee api, Portee guardian) {
        return Aiguillage.decider(true, api, guardian, true, 0L, DELAI);
    }

    private static Decision joueur(Portee api, Portee guardian, long ecoule) {
        return Aiguillage.decider(false, api, guardian, true, ecoule, DELAI);
    }

    @Test
    void staffAvecReservation_vaAuHall() {
        Decision d = staff(Portee.DOUANE, null);
        assertEquals(Issue.DOUANE, d.issue());
        assertEquals("staff+api", d.source());
        assertTrue(d.staffAvecReservation());

        Decision g = staff(null, Portee.DOUANE);
        assertEquals(Issue.DOUANE, g.issue());
        assertEquals("staff+guardian", g.source());
        assertTrue(g.staffAvecReservation());

        assertEquals("staff+api+guardian", staff(Portee.DOUANE, Portee.DOUANE).source());
        // Refus de l'API (op au pré-login) mais marque douane de Guardian : la réservation l'emporte.
        assertEquals(Issue.DOUANE, staff(Portee.AUCUNE, Portee.DOUANE).issue());
    }

    @Test
    void staffSansReservation_gardeSonPasseDroit() {
        // Examinateur staff sans réservation : sélection, comme avant.
        Decision d = staff(Portee.PLAY, null);
        assertEquals(Issue.SELECTION, d.issue());
        assertEquals(Aiguillage.SOURCE_STAFF, d.source());
        assertFalse(d.staffAvecReservation());
        assertEquals(Issue.SELECTION, staff(Portee.PLAY, Portee.PLAY).issue());
        assertEquals(Issue.SELECTION, staff(null, Portee.PLAY).issue());
        // Refus explicite de l'API : l'op n'est pas expulsé.
        assertEquals(Issue.SELECTION, staff(Portee.AUCUNE, null).issue());
    }

    @Test
    void staffSansPreuve_attendGuardianPuisSelection() {
        assertEquals(Issue.ATTENDRE, Aiguillage.decider(true, null, null, true, 0L, DELAI).issue());
        assertEquals(Issue.SELECTION, Aiguillage.decider(true, null, null, true, DELAI, DELAI).issue());
        // Guardian absent : rien à attendre, passe-droit immédiat.
        assertEquals(Issue.SELECTION, Aiguillage.decider(true, null, null, false, 0L, DELAI).issue());
    }

    @Test
    void joueurAvecReservation_vaAuHall() {
        Decision d = joueur(Portee.DOUANE, null, 0L);
        assertEquals(Issue.DOUANE, d.issue());
        assertEquals("api", d.source());
        assertFalse(d.staffAvecReservation());
        assertEquals(Issue.DOUANE, joueur(null, Portee.DOUANE, 0L).issue());
        assertEquals(Issue.SELECTION, joueur(Portee.PLAY, null, 0L).issue());
    }

    @Test
    void joueurSansPreuve_attendPuisExpulse() {
        assertEquals(Issue.ATTENDRE, joueur(null, null, 0L).issue());
        assertEquals(Issue.ATTENDRE, joueur(null, null, DELAI - 1).issue());
        // Guardian absent : un joueur attend quand même une preuve (refus par défaut).
        assertEquals(Issue.ATTENDRE, Aiguillage.decider(false, null, null, false, 0L, DELAI).issue());
        Decision fin = joueur(null, null, DELAI);
        assertEquals(Issue.EXPULSER, fin.issue());
        assertEquals("aucune preuve", fin.source());
        // AUCUNE de Guardian est ignorée (il ne la pose jamais).
        assertEquals(Issue.ATTENDRE, joueur(null, Portee.AUCUNE, 0L).issue());
        assertEquals(Issue.EXPULSER, joueur(Portee.AUCUNE, null, 0L).issue());
    }

    @Test
    void divergence_laPlusRestrictiveGagne() {
        Decision d = joueur(Portee.PLAY, Portee.DOUANE, 0L);
        assertEquals(Issue.DOUANE, d.issue());
        assertEquals("api+guardian", d.source());
        assertEquals(Issue.DOUANE, joueur(Portee.DOUANE, Portee.PLAY, 0L).issue());
        assertEquals(Issue.DOUANE, staff(Portee.PLAY, Portee.DOUANE).issue());
        assertEquals(Issue.DOUANE, staff(Portee.DOUANE, Portee.PLAY).issue());
        // Refus explicite de l'API : il prime sur une marque play pour un joueur.
        assertEquals(Issue.EXPULSER, joueur(Portee.AUCUNE, Portee.PLAY, 0L).issue());
    }

    @Test
    void ancienneSignature_supposeGuardianPresent() {
        assertEquals(Issue.ATTENDRE, Aiguillage.decider(true, null, null, 0L, DELAI).issue());
        assertEquals(Issue.DOUANE, Aiguillage.decider(true, Portee.DOUANE, null, 0L, DELAI).issue());
    }

    @Test
    void douaneEnAttente_porteeOuPhase() {
        assertTrue(ReglesDouane.douaneEnAttente(Portee.DOUANE, null));
        assertTrue(ReglesDouane.douaneEnAttente(Portee.AUCUNE, "DOUANE_A_RESERVER"));
        assertTrue(ReglesDouane.douaneEnAttente(null, "DOUANE_RESERVEE"));
        assertTrue(ReglesDouane.douaneEnAttente(Portee.AUCUNE, "DOUANE_A_REVOIR"));
        assertFalse(ReglesDouane.douaneEnAttente(Portee.PLAY, "ACCEPTE"));
        assertFalse(ReglesDouane.douaneEnAttente(Portee.AUCUNE, "REFUSE"));
        assertFalse(ReglesDouane.douaneEnAttente(null, null));
    }
}
