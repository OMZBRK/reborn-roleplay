package fr.reborn.hud.element;

/**
 * Registre des éléments HUD repositionnables. Chaque entrée correspond
 * à une partie du HUD vanilla Minecraft qu'on intercepte via Mixin et
 * qu'on offset selon la config utilisateur.
 *
 * <p>Pour ajouter un nouvel élément :
 * <ol>
 *   <li>Ajouter une entrée ici avec un identifiant stable (string utilisé
 *       comme clé dans le JSON de config — ne PAS renommer après ship).</li>
 *   <li>Créer le Mixin correspondant dans {@code fr.reborn.hud.mixin}
 *       qui lit {@link HudElementOffset#x()} / {@link HudElementOffset#y()}
 *       et applique le décalage au render.</li>
 *   <li>L'ajouter au tableau {@code client} de {@code reborn-hud.mixins.json}.</li>
 *   <li>L'ajouter aux éléments draggables du {@code HudEditScreen}.</li>
 * </ol>
 */
public enum HudElement {
    CHAT("chat", "Chat"),
    SCOREBOARD("scoreboard", "Scoreboard"),
    BOSS_BAR("boss_bar", "Boss Bar"),
    ACTION_BAR("action_bar", "Action Bar"),
    HOTBAR("hotbar", "Hotbar"),
    EXPERIENCE_BAR("experience_bar", "XP Bar"),
    HEALTH("health", "Health"),
    HUNGER("hunger", "Hunger"),
    ARMOR("armor", "Armor"),
    AIR("air", "Air");

    private final String id;
    private final String displayName;

    HudElement(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** Identifiant stable utilisé dans le JSON de config — ne JAMAIS renommer. */
    public String id() { return id; }

    /** Nom affiché dans l'UI d'édition. */
    public String displayName() { return displayName; }
}
