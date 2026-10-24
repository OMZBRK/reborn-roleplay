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
    AIR           ("air",            "Air",          "Bulles d'air sous l'eau",                 HudAnchor.BOTTOM_CENTER),
    VITALS        ("vitals",         "Vitals RP",    "Tête + vie + chakra + stamina (panneau RP)", HudAnchor.TOP_LEFT, 19, 17, 0.80f),
    COMBAT_ENDURANCE("combat_endurance", "Endurance", "Barre d'endurance de combat (garde M2)", HudAnchor.CENTER, -435, -201, 0.45f),
    // 14/10 : ancré en haut à gauche sous les vitals (l'ancien décalage depuis le bas-centre sortait de l'écran
    // en 1366×768) ; échelle 1,0 = icônes 32 px à 1:1 à l'échelle GUI 2
    COOLDOWNS     ("cooldowns",       "Cooldowns",    "Icônes de cooldown (dash, sauts…)",        HudAnchor.TOP_LEFT, 0, 0, 1.00f),
    // Radio en escouades (14/10, RADIO.md §5) : tête du skin → nom de ceux qui parlent ; défaut validé : sous les
    // vitals, à gauche (maquette v3/02). Option « Afficher toute l'escouade » dans l'inspecteur.
    ESCOUADE      ("escouade",        "Escouade radio", "Têtes et noms de ceux qui parlent à la radio", HudAnchor.TOP_LEFT, 0, 0, 1.00f),
    // GAMEPLAN §4 (19/10) : zone d'effets sous les vitals (icônes 10 px + minuteur), à la place qu'elle avait dans les
    // vitals (×0,80 comme eux) ; boussole horizontale en haut, facultative, MASQUÉE par défaut.
    EFFETS        ("effets",          "Effets",       "Icônes des effets et états, avec minuteur",  HudAnchor.TOP_LEFT, 0, 0, 0.80f),
    BOUSSOLE      ("boussole",        "Boussole",     "Boussole horizontale (cap, marqueur, lieux)", HudAnchor.TOP_CENTER, 0, 0, 1.00f, false);

    private final String id;
    private final String displayName;
    private final String description;
    private final HudAnchor defaultAnchor;
    /** Placement/taille par défaut (offset depuis l'anchor + échelle). */
    private final int defX, defY;
    private final float defScale;
    /** Visible par défaut (faux pour les éléments facultatifs : boussole). */
    private final boolean defVisible;

    HudElement(String id, String displayName, String description, HudAnchor defaultAnchor) {
        this(id, displayName, description, defaultAnchor, 0, 0, 1.0f);
    }

    HudElement(String id, String displayName, String description, HudAnchor defaultAnchor,
               int defX, int defY, float defScale) {
        this(id, displayName, description, defaultAnchor, defX, defY, defScale, true);
    }

    HudElement(String id, String displayName, String description, HudAnchor defaultAnchor,
               int defX, int defY, float defScale, boolean defVisible) {
        this.id            = id;
        this.displayName   = displayName;
        this.description   = description;
        this.defaultAnchor = defaultAnchor;
        this.defX          = defX;
        this.defY          = defY;
        this.defScale      = defScale;
        this.defVisible    = defVisible;
    }

    /** État par défaut (placement + taille) proposé pour cet élément aux nouveaux joueurs. */
    public HudElementState defaultState() {
        return new HudElementState(defX, defY, defScale, defVisible, null);
    }

    /** Identifiant stable utilisé dans le JSON de config — ne JAMAIS renommer. */
    public String id() { return id; }

    /** Nom affiché dans l'UI d'édition. */
    public String displayName() { return displayName; }

    /** Description courte pour le header du side panel. */
    public String description() { return description; }

    /** Anchor par défaut quand l'élément n'a pas encore d'override utilisateur. */
    public HudAnchor defaultAnchor() { return defaultAnchor; }

    /**
     * Éléments proposés dans l'éditeur HUD (liste + drag + snap). Exclut
     * {@link #HEALTH} et {@link #HUNGER} : masqués en permanence côté rendu
     * (remplacés par le VitalsHUD RP), donc pas repositionnables.
     */
    public static final HudElement[] EDITABLE = java.util.Arrays.stream(values())
        .filter(e -> e != HEALTH && e != HUNGER)
        .toArray(HudElement[]::new);
}
