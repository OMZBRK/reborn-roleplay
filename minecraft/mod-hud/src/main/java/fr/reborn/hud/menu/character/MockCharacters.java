package fr.reborn.hud.menu.character;

import java.util.List;

/**
 * Données de démonstration pour tester {@link CharacterSelectScreen} en solo
 * (sans serveur). Convention identique à {@code MockTablist} : jamais appelée
 * directement par l'écran — c'est {@link CharacterData} qui bascule dessus quand
 * aucune donnée serveur n'est présente.
 *
 * <p>Un perso vivant + un perso mort (RPK) pour visualiser les deux états.
 */
public final class MockCharacters {

    private MockCharacters() {}

    public static List<CharacterCard> build() {
        return List.of(
            new CharacterCard("mock-1", "Hikami", "Uchiha", 0xFFC01E35,
                "Konohagakure", "Genin", 7, false, ""),
            new CharacterCard("mock-2", "Renji", "Nara", 0xFF7BA05B,
                "Konohagakure", "Chunin", 14, true, "")
        );
    }
}
