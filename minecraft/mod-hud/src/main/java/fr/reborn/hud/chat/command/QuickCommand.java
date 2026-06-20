package fr.reborn.hud.chat.command;

/**
 * Commandes rapides accessibles via le pill {@code /me ▾} dans le footer
 * du chat custom. Click sur une entree → insère la commande dans l'input.
 */
public enum QuickCommand {
    ME      ("/me",            "Action RP"),
    SAY     ("/say",           "Parole vocale"),
    WHISPER ("/whisper <j>",   "Message privé"),
    SHOUT   ("/shout",         "Crier (porte loin)"),
    OOC     ("/ooc",           "Hors-RP entre [ ]"),
    ROLL    ("/roll XdY",      "Lancer de dés"),
    DICE    ("/dice",          "Pile ou face");

    private final String command;
    private final String description;

    QuickCommand(String command, String description) {
        this.command     = command;
        this.description = description;
    }

    public String command()     { return command; }
    public String description() { return description; }
}
