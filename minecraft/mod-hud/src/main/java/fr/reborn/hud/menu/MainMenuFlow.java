package fr.reborn.hud.menu;

/**
 * État du main menu Reborn en deux étages, façon Paladium Reforged :
 * <ol>
 *   <li>{@link Stage#SPLASH} — écran d'accroche : logo centré + « Appuie
 *       sur une touche » clignotant. Aucune interaction menu.</li>
 *   <li>{@link Stage#MENU} — le menu réel : liste verticale à gauche
 *       (JOUER / NEWS / BOUTIQUE / OPTIONS / QUITTER) avec la flèche
 *       curseur, logo top-left (hover → OST), version top-right,
 *       crédits bottom-right.</li>
 * </ol>
 *
 * <p>Le splash n'apparaît qu'une fois par session : dès qu'on l'a
 * consommé (touche/clic), les retours ultérieurs au TitleScreen (retour
 * depuis Options par ex.) atterrissent directement sur {@link Stage#MENU}.
 * Singleton static — l'état doit survivre aux ré-inits de screen.
 */
public final class MainMenuFlow {

    public enum Stage { SPLASH, MENU }

    private static Stage stage = Stage.SPLASH;
    private static boolean splashConsumed = false;

    private MainMenuFlow() {}

    public static Stage stage() {
        return stage;
    }

    public static boolean isSplash() {
        return stage == Stage.SPLASH;
    }

    /**
     * À appeler au {@code init()} du TitleScreen. Repart sur SPLASH tant
     * qu'on ne l'a pas encore consommé cette session, sinon MENU direct.
     */
    public static void onTitleInit() {
        stage = splashConsumed ? Stage.MENU : Stage.SPLASH;
    }

    /** Passe du splash au menu (touche ou clic). No-op si déjà en MENU. */
    public static void advanceFromSplash() {
        if (stage == Stage.SPLASH) {
            stage = Stage.MENU;
            splashConsumed = true;
        }
    }
}
