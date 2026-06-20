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
 *       qui lit {@link HudElementState} et applique le décalage au render.</li>
 *   <li>L'ajouter au tableau {@code client} de {@code reborn-hud.mixins.json}.</li>
 *   <li>Vérifier que le {@link #defaultAnchor()} reflète bien la position
 *       vanilla logique (ex: HOTBAR → BOTTOM_CENTER, SCOREBOARD →
 *       CENTER_RIGHT).</li>
 * </ol>
 */
public enum HudElement {
    CHAT          ("chat",           "Chat",         "Boîte de discussion principale",          HudAnchor.BOTTOM_LEFT),
    SCOREBOARD    ("scoreboard",     "Scoreboard",   "Tableau des scores latéral",              HudAnchor.CENTER_RIGHT),
    BOSS_BAR      ("boss_bar",       "Boss Bar",     "Barre de PV de boss en haut d'écran",     HudAnchor.TOP_CENTER),
    ACTION_BAR    ("action_bar",     "Action Bar",   "Texte d'action au-dessus de la hotbar",   HudAnchor.BOTTOM_CENTER),
    HOTBAR        ("hotbar",         "Hotbar",       "Barre d'inventaire principale",           HudAnchor.BOTTOM_CENTER),
    EXPERIENCE_BAR("experience_bar", "XP Bar",       "Barre d'expérience juste au-dessus",      HudAnchor.BOTTOM_CENTER),
    HEALTH        ("health",         "Health",       "Cœurs de vie du joueur",                  HudAnchor.BOTTOM_CENTER),
    HUNGER        ("hunger",         "Hunger",       "Cuisses de poulet de faim",               HudAnchor.BOTTOM_CENTER),
    ARMOR         ("armor",          "Armor",        "Indicateur d'armure",                     HudAnchor.BOTTOM_CENTER),
    AIR           ("air",            "Air",          "Bulles d'air sous l'eau",                 HudAnchor.BOTTOM_CENTER);

    private final String id;
    private final String displayName;
    private final String description;
    private final HudAnchor defaultAnchor;

    HudElement(String id, String displayName, String description, HudAnchor defaultAnchor) {
        this.id            = id;
        this.displayName   = displayName;
        this.description   = description;
        this.defaultAnchor = defaultAnchor;
    }

    /** Identifiant stable utilisé dans le JSON de config — ne JAMAIS renommer. */
    public String id() { return id; }

    /** Nom affiché dans l'UI d'édition. */
    public String displayName() { return displayName; }

    /** Description courte pour le header du side panel. */
    public String description() { return description; }

    /** Anchor par défaut quand l'élément n'a pas encore d'override utilisateur. */
    public HudAnchor defaultAnchor() { return defaultAnchor; }
}
