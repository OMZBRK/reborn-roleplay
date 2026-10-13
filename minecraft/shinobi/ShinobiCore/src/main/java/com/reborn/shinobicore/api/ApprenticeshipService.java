package com.reborn.shinobicore.api;

import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Apprentissage des techniques sur parchemin — fourni par ShinobiAbilities
 * ({@code SeanceService}), lu par ShinobiCore pour la fiche shinobi (section
 * « En apprentissage », sceau 師, origine des techniques).
 *
 * <p>Service <b>facultatif</b> : absent quand ShinobiAbilities n'est pas
 * installé (ou démarre après ShinobiCore) ; l'appelant le charge à chaque
 * usage via le ServicesManager et accepte {@code null}.
 */
@Stable
public interface ApprenticeshipService {

    /**
     * Une technique en cours d'apprentissage.
     *
     * @param techId              id de la technique
     * @param done                séances faites
     * @param required            séances requises
     * @param nextSessionAtMillis heure (ms epoch) à partir de laquelle la prochaine séance est permise ; 0 = maintenant
     * @param awaitingValidation  toutes les séances sont faites, la validation du staff est attendue (rang S)
     * @param scrollCarried       le joueur porte le rouleau de la technique
     */
    record Learning(String techId, int done, int required, long nextSessionAtMillis,
                    boolean awaitingValidation, boolean scrollCarried) {}

    /** Techniques en cours d'apprentissage du personnage (le joueur sert au rouleau porté). */
    List<Learning> inProgress(Player p, ShinobiCharacter c);

    /** Un maître de la technique est-il à portée du joueur ? */
    boolean masterNearby(Player p, String techId);

    /** Ce personnage peut-il enseigner la technique (maîtrise et ancienneté suffisantes) ? */
    boolean canTeach(ShinobiCharacter c, String techId);

    /** Date (ms epoch) où la technique a été apprise sur parchemin, ou {@code null}. */
    Long learnedAt(UUID charId, String techId);
}
