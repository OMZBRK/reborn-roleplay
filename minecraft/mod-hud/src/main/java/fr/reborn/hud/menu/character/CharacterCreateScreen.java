package fr.reborn.hud.menu.character;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Wizard de création de personnage <b>façon Zenkai</b>, ouvert depuis
 * {@link CharacterSelectScreen} sur « Créer ».
 *
 * <p>Layout : panneau opaque <b>à gauche</b> (logo Reborn + onglets d'étape +
 * contenu + boutons Retour/Suivant), <b>décor du jeu + vrai joueur visible</b>
 * (3e personne de face) à droite, et un <b>encadré de description</b> flottant
 * qui explique l'option survolée/sélectionnée.
 *
 * <p>Étapes : Village &amp; Clan (options hors candidature grisées + ✕),
 * Identité (sexe, clan pré-rempli, prénom, peau, taille, âge), Apparence
 * (Phase 2), Valider (récap). Sur « Valider » on envoie à ShinobiCore sur
 * {@code reborn:character} : {@code create\n<nom>\n<clan>\n<village>\n<sexe>\n<age>\n<size>}.
 *
 * <p>Le verrouillage village/clan est piloté par {@link CharacterData} : si le
 * serveur n'envoie pas encore la candidature, <b>rien n'est verrouillé</b>.
 */
public class CharacterCreateScreen extends Screen {

    // ── Données de référence (valeurs envoyées au serveur) ────────
    private static final String[] VILLAGES = {
        "Konohagakure", "Sunagakure", "Kirigakure", "Kumogakure",
        "Iwagakure", "Amegakure", "Déserteur"
    };
    private static final String[] V_SHORT = {
        "Konoha", "Suna", "Kiri", "Kumo", "Iwa", "Ame", "Déserteur"
    };
    private static final int[] V_COLOR = {
        0xFF3FA34D, 0xFFCB9A5A, 0xFF4F8C8C, 0xFF7FA8C9,
        0xFF9A7B54, 0xFF5C6B78, 0xFF7A2B2B
    };
    private static final String[] V_DESC = {
        "Le Village Caché de la Feuille, au cœur du Pays du Feu. Berceau des grands clans et de la Volonté du Feu.",
        "Le Village Caché du Sable, dans le désert du Pays du Vent. Rude, endurant, maître des marionnettes.",
        "Le Village Caché de la Brume, îles du Pays de l'Eau. Réputé pour ses bretteurs et son passé sanglant.",
        "Le Village Caché des Nuages, sommets du Pays de la Foudre. Puissance brute et jinchūriki d'exception.",
        "Le Village Caché de la Roche, montagnes du Pays de la Terre. Défense inébranlable et fierté tenace.",
        "Le Village Caché de la Pluie, terre neutre battue par les averses. Secret, méfiant, marqué par la guerre.",
        "Ninja renégat, sans allégeance. Traqué par tous les villages — libre, mais seul."
    };

    // Répertoire MAÎTRE des clans (couleur + lore), indexé par nom. L'affichage
    // est piloté par VILLAGE_CLANS (dissociation par village) ; ce tableau ne sert
    // plus qu'à retrouver couleur/desc d'un clan par son nom. Les noms Konoha sont
    // conservés À L'IDENTIQUE (gating ShinobiCore + tags clan de catalog.json).
    private static final String[] CLANS = {
        // Konoha
        "Senju", "Uchiha", "Hyuga", "Nara", "Sarutobi", "Uzumaki",
        "Aburame", "Akimichi", "Hatake", "Yamanaka", "Kurama",
        // Suna
        "Sabaku", "Hoki",
        // Kiri
        "Hozuki", "Hoshigaki", "Kaguya", "Yuki", "Terumi",
        // Kumo
        "Yotsuki",
        // Iwa
        "Kamizuru", "Onoki",
        // Clan custom (toujours en dernier)
        "Autre"
    };
    private static final int[] C_COLOR = {
        0xFF2E8B57, 0xFFB1302B, 0xFFCFC6E0, 0xFF5B6B3A, 0xFFC8722E, 0xFFD9532E,
        0xFF4A4F3A, 0xFFE0A33B, 0xFFAEB4BC, 0xFF8E5BB5, 0xFF8B1E2B,   // Konoha
        0xFFC97B3C, 0xFFB8A05A,                                       // Suna
        0xFF4FA3C7, 0xFF3E6E8C, 0xFFD8D2C4, 0xFFA9D5E8, 0xFFCF5A3A,   // Kiri
        0xFFE0C24B,                                                   // Kumo
        0xFFC89A3B, 0xFF8C8577,                                       // Iwa
        0xFF6A6A6A                                                    // Autre
    };
    private static final String[] C_DESC = {
        "Le Clan de la Forêt, descendants du Sage. Vitalité hors norme et affinités multiples.",
        "Le Clan à l'éventail, porteurs du Sharingan. Feu, fierté et amour dévastateur.",
        "Le Clan au Byakugan, vision à 360°. Doux-Poing et tradition rigide.",
        "Le Clan des ombres, stratèges nonchalants. Manipulation des ombres et esprit d'analyse.",
        "Lignée de volonté et de feu, proche du village. Polyvalence et sens du devoir.",
        "Le Clan aux tourbillons, réserves de chakra immenses. Fūinjutsu et longévité.",
        "Le Clan aux insectes, hôtes des kikaichū. Discrétion et logique implacable.",
        "Le Clan de l'expansion, force colossale. Manipulation de la taille et grand cœur.",
        "Lignée des Crocs Blancs, ninjas d'élite. Vitesse, ninken et talent rare.",
        "Le Clan de l'esprit, transfert mental. Renseignement et liens du cœur.",
        "Le Clan des illusions, genjutsu redoutable. Sensibilité et fardeau intérieur.",
        // Suna
        "Le Clan du Désert, lignée du Kazekage. Maîtrise du sable et fardeau du jinchūriki.",
        "Vieille lignée de Suna, marionnettistes aguerris. Patience et fils invisibles.",
        // Kiri
        "Le Clan de l'hydrification, corps de liquide. Sabreurs et Mizukage d'exception.",
        "Lignée aux traits de requin, chakra colossal. Force brute et lame Samehada.",
        "Le Clan aux ossements, Shikotsumyaku. Lignée maudite, fierté et sacrifice.",
        "Le Clan des neiges, Élément Glace. Doux de cœur, traqués pour leur sang.",
        "Double kekkei genkai, Lave et Vapeur. Volonté de fer, sang de Mizukage.",
        // Kumo
        "Lignée du Raikage, foudre et vitesse. Puissance brute, fierté de Kumo.",
        // Iwa
        "Le Clan des abeilles, insectes de miel. Rivaux ancestraux des Aburame.",
        "Lignée du Tsuchikage, Style Poussière. Fierté tenace, défense inébranlable.",
        // Autre
        "Clan ou famille personnalisé. Saisis un nom qui ne correspond à aucun clan connu."
    };

    /** Clans par village (parallèle à {@link #VILLAGES}). « Autre » (custom) partout. */
    private static final String[][] VILLAGE_CLANS = {
        {"Uchiha", "Senju", "Hyuga", "Nara", "Sarutobi", "Aburame",
         "Akimichi", "Yamanaka", "Hatake", "Uzumaki", "Kurama", "Autre"},   // Konoha
        {"Sabaku", "Hoki", "Autre"},                                        // Suna
        {"Hozuki", "Hoshigaki", "Kaguya", "Yuki", "Terumi", "Autre"},       // Kiri
        {"Yotsuki", "Autre"},                                               // Kumo
        {"Kamizuru", "Onoki", "Autre"},                                     // Iwa
        {"Autre"},                                                          // Ame
        {"Autre"},                                                          // Déserteur
    };
    private static final String[] NO_CLANS = {};

    private static final String[] SEXES = { "Homme", "Femme" };

    // ── Géométrie ─────────────────────────────────────────────────
    private static final int TILE = 42, CELL_W = 54, CELL_H = 60;
    private static final int NAV_W = 118, NAV_H = 26;

    // ── Draft ─────────────────────────────────────────────────────
    private String village = "";
    private String clan = "";
    private String sexe = "Homme";
    /** Carrure mémorisée pour l'Homme (la Femme est toujours Alex/slim). */
    private boolean maleSlim = false;
    private int age = 12;
    private double size = 1.0;      // 0.85–1.15 (≈ 1.53–2.07 m)

    /** Apparence (peau/coiffure/yeux/tenue) — pilote la composition + la synchro. */
    private final fr.reborn.hud.skin.SkinSpec appearance = new fr.reborn.hud.skin.SkinSpec();
    /** Vrai une fois le perso validé : on garde alors l'override (sinon on nettoie). */
    private boolean submitted = false;

    private int step = 0;           // 0=village/clan, 1=identité, 2=apparence, 3=valider
    private EditBox nameField;
    private EditBox customClanField;

    // Slider taille (rect mémorisé au render, réutilisé au click/drag).
    private int sizeTX, sizeTY, sizeTW;
    private boolean draggingSize = false;

    // Encadré de description (rempli pendant le render de chaque étape).
    private String descTitle = null;
    private String descBody = null;

    private CameraType prevPerspective;
    private boolean prevHudHidden;
    private boolean captured = false;

    public CharacterCreateScreen() {
        super(Component.literal("Création de personnage"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!captured && mc.options != null) {
            prevPerspective = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            prevHudHidden = mc.gui.hud.isHidden();
            ((fr.reborn.hud.mixin.HudAccessor)(Object) mc.gui.hud).reborn$setHidden(true);
            captured = true;
        }
        // Perso figé debout : coupe toute pose idle (émote « assise ») héritée du
        // menu de sélection — l'éditeur montre le perso debout, pas assis.
        fr.reborn.hud.animation.MovementAnimations.INSTANCE.stopPose();

        // Pré-remplissage depuis la candidature (verrou whitelist).
        preselectFromCandidature();
        // Synchro genre → jeu de peau + carrure (Femme = Alex imposé).
        appearance.female = "Femme".equals(sexe);
        appearance.slim = appearance.female || maleSlim;
        // Couleur de peau initiale = position par défaut sur la rampe.
        appearance.skinColor = fr.reborn.hud.skin.SkinSpec.skinRamp(skinT);

        nameField = new EditBox(this.font, panelX(), 0, panelW() - 8, 20,
            Component.literal("Prénom"));
        nameField.setMaxLength(24);
        nameField.setHint(RebornFont.body("Prénom du personnage…"));
        String cn = CharacterData.candidatureName();
        if (cn != null && !cn.isBlank()) nameField.setValue(cn);
        addWidget(nameField);

        customClanField = new EditBox(this.font, panelX(), 0, panelW() - 8, 20,
            Component.literal("Clan personnalisé"));
        customClanField.setMaxLength(24);
        customClanField.setHint(RebornFont.body("Nom de ton clan / famille…"));
        addWidget(customClanField);

        refreshPreview(); // avatar composé visible dès l'ouverture
    }

    /** Recompose et applique l'aperçu du skin sur le modèle du joueur local. */
    private void refreshPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        fr.reborn.hud.skin.RebornSkins.applySpec(mc.player.getUUID(), appearance);
    }

    private void preselectFromCandidature() {
        String cv = CharacterData.candidatureVillage();
        if (cv != null && village.isBlank()) {
            for (String v : VILLAGES) if (v.equalsIgnoreCase(cv)) { village = v; break; }
        }
        String cc = CharacterData.candidatureClan();
        if (cc != null && clan.isBlank()) {
            for (String c : CLANS) if (c.equalsIgnoreCase(cc)) { clan = c; break; }
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (captured && mc.options != null) {
            mc.options.setCameraType(prevPerspective);
            ((fr.reborn.hud.mixin.HudAccessor)(Object) mc.gui.hud).reborn$setHidden(prevHudHidden);
            captured = false;
        }
        // Aperçu non validé : on retire l'override pour rendre son vrai skin au joueur.
        if (!submitted && mc.player != null) {
            fr.reborn.hud.skin.RebornSkins.clear(mc.player.getUUID());
        }
        super.removed();
    }

    // ── Verrouillage ──────────────────────────────────────────────
    private boolean villageLocked(String v) {
        String cand = CharacterData.candidatureVillage();
        if (cand == null || CharacterData.staffExempt()) return false;
        return !v.equalsIgnoreCase(cand);
    }

    private boolean clanLocked(String c) {
        if ("Autre".equals(c)) return false; // « Autre » jamais verrouillé
        String cand = CharacterData.candidatureClan();
        if (cand == null || CharacterData.staffExempt()) return false;
        return !c.equalsIgnoreCase(cand);
    }

    private boolean isPredefinedClanName(String s) {
        if (s == null) return false;
        for (String c : CLANS) if (!c.equals("Autre") && c.equalsIgnoreCase(s.trim())) return true;
        return false;
    }

    private String effectiveClan() {
        return "Autre".equals(clan) ? customClanField.getValue().trim() : clan;
    }

    /** Clans du village sélectionné (vide tant qu'aucun village n'est choisi). */
    private String[] villageClans() {
        if (village.isBlank()) return NO_CLANS;
        return VILLAGE_CLANS[indexOf(VILLAGES, village)];
    }

    /** Vrai si {@code c} appartient au village courant. */
    private boolean containsClan(String c) {
        if (c == null || c.isBlank()) return false;
        for (String x : villageClans()) if (x.equals(c)) return true;
        return false;
    }

    /** Slug ASCII pour un nom de clan/village (chemin de logo). Ex. « Hyūga » → « hyuga ». */
    private static String slug(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    // ── Layout ────────────────────────────────────────────────────
    private int panelX() { return 34; }
    private int panelW() { return Math.min(372, (int) (this.width * 0.46f)); }
    private int contentTop() { return 96; }
    private int perRow() { return Math.max(3, panelW() / CELL_W); }
    private int navY() { return this.height - 40; }

    // ── Rendu ─────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Étape Apparence = éditeur KORVEX plein écran sur fond « void » sombre.
        // Fond « void » KORVEX pour TOUTES les étapes (perso centré rendu par-dessus).
        ctx.fillGradient(0, 0, this.width, this.height, 0xFF16201C, 0xFF090C0B);
        // Voile gauche léger pour asseoir le panneau de contenu (étapes 0/1/3).
        if (step != 2) {
            int panelRight = panelX() + panelW() + 24;
            DrawHelpers.horizontalGradient(ctx, 0, 0, panelRight, this.height,
                Colors.withAlpha(0xFF05080A, 0.72f), 0x00000000);
        }
        ctx.fillGradient(0, 0, this.width, 80, Colors.withAlpha(0xFF000000, 0.55f), 0x00000000);
        ctx.fillGradient(0, this.height - 90, this.width, this.height,
            0x00000000, Colors.withAlpha(0xFF000000, 0.65f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font tr = this.font;
        descTitle = null;
        descBody = null;

        // Étape Apparence : éditeur KORVEX plein écran (pas de chrome wizard).
        if (step == 2) {
            nameField.setVisible(false);
            customClanField.setVisible(false);
            renderApparence(ctx, tr, mouseX, mouseY);
            return;
        }

        // Perso centré (rotatable/zoomable) commun à toutes les étapes.
        drawAvatar(ctx, false);

        drawLogo(ctx, tr);
        drawTabs(ctx, tr, mouseX, mouseY);
        drawStepSubtitle(ctx, tr);

        // Visibilité des champs de texte selon l'étape.
        nameField.setVisible(step == 1);
        customClanField.setVisible(step == 0 && "Autre".equals(clan));

        switch (step) {
            case 0 -> renderVillageClan(ctx, tr, mouseX, mouseY);
            case 1 -> renderIdentity(ctx, tr, mouseX, mouseY);
            default -> renderValidate(ctx, tr);
        }

        drawDescBox(ctx, tr);
        drawNav(ctx, tr, mouseX, mouseY);
    }

    private void drawLogo(GuiGraphicsExtractor ctx, Font tr) {
        int x = panelX();
        if (CreatorUi.logoExists()) {
            CreatorUi.blitLogo(ctx, x, 12, 54, 36);
        } else {
            int cx = x;
            for (char c : "REBORN".toCharArray()) {
                Component ch = RebornFont.arcade(String.valueOf(c));
                ctx.text(tr, ch, cx, 26, Colors.ACCENT, false);
                cx += tr.width(ch) + 3;
            }
            DrawHelpers.rect(ctx, x, 38, cx - x - 3, 1, Colors.withAlpha(Colors.GOLD, 0.8f));
        }
        Component sub = RebornFont.arcade("Création de personnage");
        ctx.text(tr, sub, x, 52, Colors.FOREGROUND_MUTED, false);
    }

    private String[] tabLabels() {
        return new String[] { "Village & Clan", "Identité", "Apparence", "Valider" };
    }

    private void drawTabs(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        String[] labels = tabLabels();
        int x = panelX();
        int y = 62;
        for (int i = 0; i < labels.length; i++) {
            Component t = RebornFont.arcade(labels[i]);
            int w = tr.width(t);
            boolean cur = i == step;
            boolean past = i < step;
            int col = cur ? Colors.WHITE_PURE : past ? Colors.FOREGROUND_SUBTLE : Colors.FOREGROUND_MUTED;
            ctx.text(tr, t, x, y, col, false);
            if (cur) DrawHelpers.rect(ctx, x, y + 10, w, 1, Colors.GOLD);
            x += w + 14;
        }
    }

    private void drawStepSubtitle(GuiGraphicsExtractor ctx, Font tr) {
        String s = switch (step) {
            case 0 -> "Choisis ton village et ton clan selon ta candidature.";
            case 1 -> "Renseigne l'identité de ton personnage.";
            case 2 -> "Compose l'apparence de ton personnage (ou garde ton skin).";
            default -> "Vérifie tes choix puis valide.";
        };
        ctx.text(tr, RebornFont.body(s), panelX(), 80, Colors.FOREGROUND_SUBTLE, false);
    }

    // ── Étape 0 : village + clan ──────────────────────────────────
    private void renderVillageClan(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        int top = contentTop();
        label(ctx, tr, "VILLAGE", panelX(), top);
        int vBase = top + 14;
        for (int i = 0; i < VILLAGES.length; i++) {
            int[] p = tileXY(vBase, i);
            boolean sel = VILLAGES[i].equals(village);
            boolean locked = villageLocked(VILLAGES[i]);
            boolean hover = hit(mx, my, p[0], p[1], TILE, TILE);
            tile(ctx, tr, p[0], p[1], mono(V_SHORT[i]), V_SHORT[i],
                "village/" + slug(V_SHORT[i]), V_COLOR[i], sel, locked, hover);
            if (hover && !locked) { descTitle = V_SHORT[i]; descBody = V_DESC[i]; }
        }
        int vRows = rows(VILLAGES.length);
        int cLabelY = vBase + vRows * CELL_H + 6;
        label(ctx, tr, "CLAN", panelX(), cLabelY);
        int cBase = cLabelY + 14;
        // Clans DISSOCIÉS par village : seuls ceux du village choisi s'affichent.
        String[] clans = villageClans();
        if (village.isBlank()) {
            ctx.text(tr, RebornFont.arcade("Choisis d'abord un village."),
                panelX(), cBase, Colors.FOREGROUND_MUTED, false);
        } else {
            for (int i = 0; i < clans.length; i++) {
                int ci = indexOf(CLANS, clans[i]);       // couleur/lore depuis le répertoire maître
                int[] p = tileXY(cBase, i);
                boolean sel = clans[i].equals(clan);
                boolean locked = clanLocked(clans[i]);
                boolean hover = hit(mx, my, p[0], p[1], TILE, TILE);
                tile(ctx, tr, p[0], p[1], mono(clans[i]), clans[i],
                    "clan/" + slug(clans[i]), C_COLOR[ci], sel, locked, hover);
                if (hover && !locked) { descTitle = clans[i]; descBody = C_DESC[ci]; }
            }
        }

        // Champ « Autre » (nom de clan libre).
        if ("Autre".equals(clan)) {
            int cRows = rows(clans.length);
            int fy = cBase + cRows * CELL_H + 4;
            ctx.text(tr, RebornFont.arcade("NOM DE CLAN"), panelX(), fy, Colors.FOREGROUND_MUTED, false);
            customClanField.setX(panelX());
            customClanField.setY(fy + 12);
            customClanField.extractRenderState(ctx, mx, my, 0f);
        }

        // Fallback description = sélection courante.
        if (descTitle == null) {
            if (!clan.isBlank()) { descTitle = clan; descBody = C_DESC[indexOf(CLANS, clan)]; }
            else if (!village.isBlank()) {
                int vi = indexOf(VILLAGES, village); descTitle = V_SHORT[vi]; descBody = V_DESC[vi];
            }
        }
    }

    // ── Étape 1 : identité ────────────────────────────────────────
    private void renderIdentity(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        int x = panelX();
        int w = panelW() - 8;
        int y = contentTop();

        // Sexe (2 tuiles avec glyphe).
        label(ctx, tr, "SEXE", x, y);
        int pw = (w - 8) / 2;
        for (int i = 0; i < SEXES.length; i++) {
            int bx = x + i * (pw + 8);
            boolean sel = SEXES[i].equals(sexe);
            boolean hover = hit(mx, my, bx, y + 12, pw, 26);
            pill(ctx, tr, bx, y + 12, pw, 26, SEXES[i], sel, hover);
            if (hover) { descTitle = "Sexe"; descBody = "Détermine le modèle de base de ton personnage (Steve / Alex)."; }
        }

        // Clan (pré-rempli, lecture seule).
        int cy = y + 48;
        label(ctx, tr, "CLAN", x, cy);
        readonlyField(ctx, tr, x, cy + 12, w, effectiveClan().isBlank() ? "—" : effectiveClan());

        // Prénom.
        int ny = y + 92;
        label(ctx, tr, "PRÉNOM", x, ny);
        nameField.setX(x);
        nameField.setY(ny + 12);
        nameField.extractRenderState(ctx, mx, my, 0f);
        if (hit(mx, my, x, ny + 12, w, 20)) { descTitle = "Prénom"; descBody = "Le nom RP de ton shinobi. Doit être unique sur le serveur."; }

        // (La couleur de peau se règle dans l'étape Apparence — éditeur KORVEX.)

        // Taille.
        int ty = y + 136;
        int cm = (int) Math.round(1.8 * size * 100);
        label(ctx, tr, "TAILLE", x, ty);
        Component cmT = RebornFont.arcade(cm + " cm");
        ctx.text(tr, cmT, x + w - tr.width(cmT), ty, Colors.FOREGROUND, false);
        sizeTX = x; sizeTY = ty + 14; sizeTW = w - 2;
        DrawHelpers.roundedRect(ctx, sizeTX, sizeTY, sizeTW, 8, 3, Colors.withAlpha(0xFF000000, 0.55f));
        float st = (float) ((size - 0.85) / (1.15 - 0.85));
        int fillW = Math.round(st * sizeTW);
        if (fillW > 0) DrawHelpers.roundedRect(ctx, sizeTX, sizeTY, Math.max(4, fillW), 8, 3, Colors.ACCENT);
        int szKnob = sizeTX + Math.round(st * (sizeTW - 1));
        DrawHelpers.roundedOutlinedRect(ctx, szKnob - 3, sizeTY - 2, 6, 12, 2, Colors.GOLD, Colors.WHITE_PURE);
        if (hit(mx, my, sizeTX, sizeTY - 3, sizeTW, 14)) { descTitle = "Taille"; descBody = "Ajuste la taille adulte de ton personnage. Ce sera sa taille maximale."; }

        // Âge (stepper compact).
        int ay = y + 172;
        label(ctx, tr, "ÂGE", x, ay);
        square(ctx, tr, x, ay + 12, "-", hit(mx, my, x, ay + 12, 22, 22));
        Component av = RebornFont.arcade(String.valueOf(age));
        ctx.text(tr, av, x + 40 - tr.width(av) / 2, ay + 12 + 7, Colors.WHITE_PURE, false);
        square(ctx, tr, x + 58, ay + 12, "+", hit(mx, my, x + 58, ay + 12, 22, 22));
        if (hit(mx, my, x, ay, w, 34)) { descTitle = "Âge"; descBody = "L'âge RP de départ de ton personnage."; }

        if (descTitle == null) { descTitle = "Identité"; descBody = "Renseigne le sexe, le prénom, la couleur de peau, la taille et l'âge."; }
    }

    // ══ Étape 2 : Apparence — éditeur KORVEX (perso centré + catégories) ══
    private int cat = -1;            // -1 = grille principale ; 0..3 = catégorie
    private int subCat = 0;          // sous-catégorie (Visage : 0 Yeux / 1 Cheveux / 2 Pilosité)
    private int eyeSel = 0;          // œil édité par le picker : 0=les deux, 1=gauche, 2=droite
    private boolean pickerOpen = false;
    private float avatarSpin = 0f;   // rotation horizontale (maintien clic droit)
    private float avatarZoom = 1f;   // facteur de zoom (molette)
    private int hsvDrag = -1;        // 0 teinte, 1 saturation, 2 valeur ; -1 = aucun
    private float pkH, pkS, pkV;     // HSV en cours d'édition
    private int pkX, pkY, pkW;       // rect du color-picker (mémorisé au render)
    private static final int PK_BAR_H = 9, PK_GAP = 14;

    // Barre « couleur de peau » (cat Corps) : position sur la rampe + drag + rect mémorisé.
    private float skinT = 0.35f;
    private boolean skinDrag = false;
    private int skX, skY, skW;
    /** Staff : prévisualiser les listes comme un JOUEUR (gating actif) — touche G. */
    private boolean previewAsPlayer = false;

    private static final String[] CAT_NAMES = { "Corps", "Visage", "Genre", "Tenue" };
    private static final String[] VISAGE_SUBS = { "Yeux", "Sourcils", "Cheveux", "Pilosité", "Accessoire" };
    // Icônes 32×32 livrées (assets/reborn/textures/character/ui/<slug>.png).
    private static final String[] CAT_SLUGS = { "corps", "visage", "genre", "tenue" };
    private static final String[] SUB_SLUGS = { "yeux", "sourcils", "cheveux", "pilosite", "accessoire" };
    private static final java.util.Map<String, Boolean> ICON_EXISTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static net.minecraft.resources.Identifier iconId(String slug) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(
            "reborn", "textures/character/ui/" + slug + ".png");
    }

    private static boolean iconExists(String slug) {
        return ICON_EXISTS.computeIfAbsent(slug, s -> {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.getResourceManager().getResource(iconId(s)).isPresent();
        });
    }

    /** Blitte la tuile-icône 32×32 centrée en (cx,cy) ; false si l'icône n'est pas livrée. */
    private boolean blitTile(GuiGraphicsExtractor ctx, String slug, int cx, int cy) {
        if (!iconExists(slug)) return false;
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, iconId(slug),
            cx - 16, cy - 16, 0f, 0f, 32, 32, 32, 32);
        return true;
    }

    private void renderApparence(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        drawAvatar(ctx, cat == 1);          // cat 1 = Visage → zoom sur la tête
        if (cat < 0) renderCatGrid(ctx, tr, mx, my);
        else renderCatEditor(ctx, tr, mx, my);
        // Logo serveur en haut à droite.
        if (CreatorUi.logoExists()) {
            CreatorUi.blitLogo(ctx, this.width - 24 - 45, 14, 45, 30);
        } else {
            Component logo = ax("REBORN");
            ctx.text(tr, logo, this.width - 24 - tr.width(logo), 22, Colors.GOLD, false);
        }
    }

    /** Rend le joueur local (skin composé via l'override) centré, zoomé ou non.
     *  Rotation horizontale = {@link #avatarSpin} (clic droit maintenu) ; zoom =
     *  {@link #avatarZoom} (molette). Perso figé debout (pas d'animation). */
    private void drawAvatar(GuiGraphicsExtractor ctx, boolean headZoom) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int cx = this.width / 2;
        float rotX = cx + avatarSpin; // rotation via le paramètre « souris » du render
        if (headZoom) {
            int size = (int) (this.height * 0.70f * avatarZoom);
            int feetY = (int) (this.height * 1.62f);
            net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(
                ctx, cx - size, feetY - size * 2, cx + size, feetY, size, 0f, rotX, feetY - size, mc.player);
        } else {
            int size = (int) (this.height * 0.30f * avatarZoom);
            int top = (int) (this.height * 0.15f);
            int bot = (int) (this.height * 0.93f);
            net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(
                ctx, cx - size, top, cx + size, bot, size, 0f, rotX, (top + bot) / 2f, mc.player);
        }
    }

    /** Texte en police du main menu (ArcadePix), majuscules ASCII (accents retirés). */
    private static Component ax(String s) {
        return fr.reborn.hud.menu.RebornFont.arcade(
            java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.FRENCH));
    }

    // ── Grille principale (4 catégories autour du perso) ──────────────
    private int[][] catPositions() {
        int cx = this.width / 2, cy = this.height / 2;
        int dx = (int) (this.width * 0.15f), dy = (int) (this.height * 0.20f);
        return new int[][] {
            { cx - dx, cy - dy }, { cx + dx, cy - dy },
            { cx - dx, cy + dy }, { cx + dx, cy + dy }
        };
    }

    private void renderCatGrid(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        boolean own = appearance.useOwnSkin;
        int[][] pos = catPositions();
        for (int i = 0; i < 4; i++) {
            boolean hover = hit(mx, my, pos[i][0] - 20, pos[i][1] - 20, 40, 40);
            drawCatIcon(ctx, tr, pos[i][0], pos[i][1], i, hover, own);
        }
        // Toggle « Mon skin » (haut-gauche).
        boolean th = hit(mx, my, 24, 22, 150, 24);
        DrawHelpers.roundedOutlinedRect(ctx, 24, 22, 150, 24, 6,
            Colors.withAlpha(0xFF000000, 0.45f), th ? Colors.ACCENT : Colors.BORDER);
        String ts = own ? "Skin : le mien" : "Skin : RP composé";
        ctx.text(tr, ax(ts), 34, 30, own ? Colors.GOLD : Colors.ACCENT, false);

        // CONFIRM (bas-centre) + RETOUR (bas-gauche).
        int by = this.height - 58, cx = this.width / 2;
        drawKorvexButton(ctx, tr, cx - 95, by, 190, 30, "CONFIRMER", "F",
            hit(mx, my, cx - 95, by, 190, 30), true);
        drawKorvexButton(ctx, tr, 24, by, 130, 30, "RETOUR", "↑",
            hit(mx, my, 24, by, 130, 30), false);
    }

    /** Icône de catégorie : carré teal + petit pictogramme + badge n° + légende. */
    private void drawCatIcon(GuiGraphicsExtractor ctx, Font tr, int cx, int cy, int idx,
                             boolean hover, boolean dimmed) {
        int s = 40, x = cx - s / 2, y = cy - s / 2;
        if (blitTile(ctx, CAT_SLUGS[idx], cx, cy)) {
            if (dimmed) DrawHelpers.rect(ctx, cx - 16, cy - 16, 32, 32, Colors.withAlpha(0xFF000000, 0.55f));
            else if (hover) DrawHelpers.outlinedRect(ctx, cx - 18, cy - 18, 36, 36, 0, Colors.WHITE_PURE);
        } else {
            int border = dimmed ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.4f)
                : hover ? Colors.WHITE_PURE : Colors.ACCENT;
            DrawHelpers.roundedOutlinedRect(ctx, x, y, s, s, 7,
                Colors.withAlpha(0xFF0A1A16, hover ? 0.9f : 0.7f), border);
            int gc = dimmed ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.5f) : Colors.ACCENT;
            drawCatGlyph(ctx, cx, cy, idx, gc);
        }
        // Badge numéro (coin bas-droit).
        ctx.text(tr, RebornFont.arcade(String.valueOf(idx + 1)), x + s - 6, y + s - 9,
            dimmed ? Colors.FOREGROUND_MUTED : Colors.WHITE_PURE, false);
        // Légende sous l'icône.
        Component cap = ax(CAT_NAMES[idx]);
        ctx.text(tr, cap, cx - tr.width(cap) / 2, y + s + 4,
            dimmed ? Colors.FOREGROUND_MUTED : Colors.FOREGROUND_SUBTLE, false);
    }

    /** Pictogramme procédural par catégorie (sans asset). */
    private void drawCatGlyph(GuiGraphicsExtractor ctx, int cx, int cy, int idx, int c) {
        switch (idx) {
            case 0 -> { // Corps
                DrawHelpers.rect(ctx, cx - 2, cy - 9, 4, 4, c);
                DrawHelpers.rect(ctx, cx - 5, cy - 3, 10, 8, c);
            }
            case 1 -> { // Visage
                DrawHelpers.outlinedRect(ctx, cx - 6, cy - 6, 12, 12, 0, c);
                DrawHelpers.rect(ctx, cx - 3, cy - 1, 2, 2, c);
                DrawHelpers.rect(ctx, cx + 2, cy - 1, 2, 2, c);
            }
            case 2 -> { // Genre (cercle + flèche)
                DrawHelpers.outlinedRect(ctx, cx - 5, cy - 2, 8, 8, 0, c);
                DrawHelpers.thickLine(ctx, cx + 2, cy + 1, cx + 7, cy - 4, 2, c);
                DrawHelpers.rect(ctx, cx + 3, cy - 5, 4, 2, c);
                DrawHelpers.rect(ctx, cx + 5, cy - 5, 2, 4, c);
            }
            default -> { // Tenue (chemise)
                DrawHelpers.rect(ctx, cx - 6, cy - 5, 12, 4, c); // épaules
                DrawHelpers.rect(ctx, cx - 4, cy - 1, 8, 8, c);  // torse
            }
        }
    }

    // ── Éditeur d'une catégorie (screens 2/3/4) ───────────────────────
    private void renderCatEditor(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        // Icônes du haut : sous-catégories (Visage) ou l'icône de la catégorie.
        int topY = 60, cx = this.width / 2;
        if (cat == 1) {
            int n = VISAGE_SUBS.length, gap = 64, startX = cx - (n - 1) * gap / 2;
            for (int i = 0; i < n; i++) {
                int ix = startX + i * gap;
                boolean sel = i == subCat, hover = hit(mx, my, ix - 18, topY - 18, 36, 36);
                if (blitTile(ctx, SUB_SLUGS[i], ix, topY)) {
                    if (sel) DrawHelpers.outlinedRect(ctx, ix - 17, topY - 17, 34, 34, 0, Colors.WHITE_PURE);
                    else if (hover) DrawHelpers.outlinedRect(ctx, ix - 17, topY - 17, 34, 34, 0, Colors.ACCENT);
                } else {
                    DrawHelpers.roundedOutlinedRect(ctx, ix - 18, topY - 18, 36, 36, 6,
                        Colors.withAlpha(0xFF0A1A16, 0.75f),
                        sel ? Colors.WHITE_PURE : hover ? Colors.ACCENT : Colors.BORDER);
                    drawSubGlyph(ctx, ix, topY, i, sel ? Colors.WHITE_PURE : Colors.ACCENT);
                }
                // Libellé seulement pour la tuile sélectionnée/survolée (évite le chevauchement à 5).
                if (sel || hover) {
                    Component cp = ax(VISAGE_SUBS[i]);
                    ctx.text(tr, cp, ix - tr.width(cp) / 2, topY + 22,
                        sel ? Colors.WHITE_PURE : Colors.FOREGROUND, false);
                }
            }
        } else {
            if (!blitTile(ctx, CAT_SLUGS[cat], cx, topY)) {
                DrawHelpers.roundedOutlinedRect(ctx, cx - 18, topY - 18, 36, 36, 6,
                    Colors.withAlpha(0xFF0A1A16, 0.75f), Colors.ACCENT);
                drawCatGlyph(ctx, cx, topY, cat, Colors.ACCENT);
            }
        }

        // Cycleur de style [A] ‹ n/m › [D] (si la facette a un style).
        int barY = this.height - 58;
        if (facetHasStyle()) {
            int cyy = barY - 34, cw = 150, sx = cx - cw / 2;
            DrawHelpers.roundedOutlinedRect(ctx, sx, cyy, cw, 22, 6,
                Colors.withAlpha(0xFF000000, 0.5f), Colors.BORDER);
            boolean lh = hit(mx, my, sx, cyy, 22, 22), rh = hit(mx, my, sx + cw - 22, cyy, 22, 22);
            ctx.text(tr, RebornFont.arcade("A ‹"), sx + 6, cyy + 7, lh ? Colors.WHITE_PURE : Colors.ACCENT, false);
            ctx.text(tr, RebornFont.arcade("› D"), sx + cw - 22, cyy + 7, rh ? Colors.WHITE_PURE : Colors.ACCENT, false);
            Component frac = ax((facetStyleIdx() + 1) + " / " + facetStyleCount());
            ctx.text(tr, frac, cx - tr.width(frac) / 2, cyy + 7, Colors.WHITE_PURE, false);
        }

        // Corps : toggle Carrure (Classique / Alex) + barre « couleur de peau ».
        if (cat == 0) { drawBuildToggle(ctx, tr, mx, my); drawSkinBar(ctx, tr, mx, my); }
        // Yeux : sélecteur d'œil édité (les deux / gauche / droite) pour le picker.
        if (cat == 1 && subCat == 0) drawEyeSelToggle(ctx, tr, mx, my);
        // Staff : indicateur d'aperçu gating (coin haut-gauche, discret).
        if (CharacterData.staffExempt()) {
            Component g = ax(previewAsPlayer ? "[G] APERCU JOUEUR" : "[G] STAFF : TOUT DEBLOQUE");
            ctx.text(tr, g, 24, this.height - 92,
                previewAsPlayer ? Colors.GOLD : Colors.FOREGROUND_MUTED, false);
        }

        // Barre du bas : RETURN | 🎨 | NOM DU STYLE | 🎨 | CONFIRM.
        drawKorvexButton(ctx, tr, 24, barY, 130, 30, "RETOUR", "↑",
            hit(mx, my, 24, barY, 130, 30), false);
        drawKorvexButton(ctx, tr, this.width - 154, barY, 130, 30, "CONFIRMER", "F",
            hit(mx, my, this.width - 154, barY, 130, 30), true);

        int nameW = 300, nameX = cx - nameW / 2;
        DrawHelpers.roundedOutlinedRect(ctx, nameX, barY, nameW, 30, 7,
            Colors.withAlpha(0xFF0A1A16, 0.8f), Colors.BORDER_STRONG);
        Component name = ax(facetStyleName());
        ctx.text(tr, name, cx - tr.width(name) / 2, barY + 11, Colors.WHITE_PURE, false);

        if (facetHasColor()) {
            int col = facetColorGet();
            // 🎨 gauche + droite (ouvre/ferme le picker).
            boolean cLh = hit(mx, my, nameX - 40, barY, 30, 30);
            boolean cRh = hit(mx, my, nameX + nameW + 10, barY, 30, 30);
            drawColorButton(ctx, nameX - 40, barY, col, cLh || pickerOpen);
            drawColorButton(ctx, nameX + nameW + 10, barY, col, cRh || pickerOpen);
            if (pickerOpen) drawPicker(ctx, tr, mx, my);
        }
    }

    private void drawSubGlyph(GuiGraphicsExtractor ctx, int cx, int cy, int sub, int c) {
        switch (sub) {
            case 0 -> { // Yeux
                DrawHelpers.rect(ctx, cx - 7, cy - 1, 5, 3, c);
                DrawHelpers.rect(ctx, cx + 2, cy - 1, 5, 3, c);
            }
            case 1 -> { // Sourcils (deux traits inclinés)
                DrawHelpers.rect(ctx, cx - 7, cy - 2, 5, 2, c);
                DrawHelpers.rect(ctx, cx + 2, cy - 2, 5, 2, c);
            }
            case 2 -> { // Cheveux
                DrawHelpers.rect(ctx, cx - 7, cy - 7, 14, 4, c);
                DrawHelpers.rect(ctx, cx - 7, cy - 7, 3, 8, c);
                DrawHelpers.rect(ctx, cx + 4, cy - 7, 3, 8, c);
            }
            case 3 -> // Pilosité (moustache)
                DrawHelpers.rect(ctx, cx - 5, cy + 2, 10, 3, c);
            default -> { // Accessoire (bandeau)
                DrawHelpers.rect(ctx, cx - 7, cy - 2, 14, 3, c);
                DrawHelpers.rect(ctx, cx + 3, cy - 1, 4, 5, c);
            }
        }
    }

    /** Toggle Carrure (cat Corps) : Classique | Alex. Femme = Alex imposé (gauche grisée). */
    private void drawBuildToggle(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        int cx = this.width / 2, barY = this.height - 58;
        int tw = 220, tx = cx - tw / 2, ty = barY - 66, hw = tw / 2;
        Component lab = ax("CARRURE");
        ctx.text(tr, lab, cx - tr.width(lab) / 2, ty - 12, Colors.FOREGROUND_MUTED, false);
        boolean female = appearance.female;
        segButton(ctx, tr, tx, ty, hw, 22, "CLASSIQUE",
            !appearance.slim, !female && hit(mx, my, tx, ty, hw, 22), female);
        segButton(ctx, tr, tx + hw, ty, hw, 22, "ALEX",
            appearance.slim, hit(mx, my, tx + hw, ty, hw, 22), false);
    }

    private static final String[] EYE_SEL_NAMES = { "LES DEUX", "OEIL GAUCHE", "OEIL DROIT" };

    /** Sélecteur d'œil édité par le picker (cycle : les deux → gauche → droite). */
    private void drawEyeSelToggle(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        int cx = this.width / 2, barY = this.height - 58;
        int w = 190, x = cx - w / 2, y = barY - 66;
        Component lab = ax("OEIL EDITE");
        ctx.text(tr, lab, cx - tr.width(lab) / 2, y - 12, Colors.FOREGROUND_MUTED, false);
        boolean hover = hit(mx, my, x, y, w, 22);
        segButton(ctx, tr, x, y, w, 22, "< " + EYE_SEL_NAMES[eyeSel] + " >", true, hover, false);
    }

    /** Cycle l'œil édité + réaligne le picker sur la couleur de ce nouvel œil. */
    private void cycleEyeSel() {
        eyeSel = (eyeSel + 1) % 3;
        if (pickerOpen) openPicker(); // resynchronise les curseurs HSV
    }

    private void segButton(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h,
                           String label, boolean sel, boolean hover, boolean disabled) {
        int fill = disabled ? Colors.withAlpha(0xFF000000, 0.5f)
            : sel ? Colors.withAlpha(Colors.ACCENT, 0.4f)
            : hover ? Colors.SURFACE_OVERLAY : Colors.withAlpha(0xFF000000, 0.4f);
        int border = disabled ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.4f)
            : sel ? Colors.GOLD : hover ? Colors.withAlpha(Colors.FOREGROUND, 0.5f) : Colors.BORDER;
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 6, fill, border);
        Component t = ax(label);
        int col = disabled ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.6f)
            : sel ? Colors.WHITE_PURE : Colors.FOREGROUND;
        ctx.text(tr, t, x + (w - tr.width(t)) / 2, y + (h - 8) / 2, col, false);
    }

    private void drawColorButton(GuiGraphicsExtractor ctx, int x, int y, int color, boolean active) {
        DrawHelpers.roundedOutlinedRect(ctx, x, y, 30, 30, 6,
            Colors.withAlpha(0xFF000000, 0.5f), active ? Colors.ACCENT : Colors.BORDER);
        DrawHelpers.roundedRect(ctx, x + 8, y + 8, 14, 14, 3, color);
    }

    /** Barre « couleur de peau » (cat Corps) : rampe de tons chair + curseur draggable. */
    private void drawSkinBar(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        int cx = this.width / 2, barY = this.height - 58;
        int w = 200, x = cx - w / 2, y = barY - 28;
        skX = x; skY = y; skW = w;
        Component lab = ax("COULEUR DE PEAU");
        ctx.text(tr, lab, cx - tr.width(lab) / 2, y - 12, Colors.FOREGROUND_MUTED, false);
        int segs = 32;
        for (int i = 0; i < segs; i++) {
            float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
            int x0 = x + Math.round(t0 * w), x1 = x + Math.round(t1 * w);
            DrawHelpers.horizontalGradient(ctx, x0, y, Math.max(1, x1 - x0), 10,
                fr.reborn.hud.skin.SkinSpec.skinRamp(t0), fr.reborn.hud.skin.SkinSpec.skinRamp(t1));
        }
        DrawHelpers.outlinedRect(ctx, x - 1, y - 1, w + 2, 12, 0, Colors.BORDER_STRONG);
        int kx = x + Math.round(skinT * (w - 1));
        DrawHelpers.roundedOutlinedRect(ctx, kx - 2, y - 2, 4, 14, 1,
            Colors.WHITE_PURE, Colors.withAlpha(0xFF000000, 0.8f));
    }

    /** Positionne le curseur de peau depuis la souris → recolore la peau. */
    private void setSkinFromTrack(int mx) {
        skinT = Math.max(0f, Math.min(1f, (mx - skX) / (float) Math.max(1, skW - 1)));
        appearance.skinColor = fr.reborn.hud.skin.SkinSpec.skinRamp(skinT);
        refreshPreview();
    }

    // ── Color picker HSV libre ────────────────────────────────────────
    private void openPicker() {
        float[] hsv = fr.reborn.hud.skin.SkinSpec.argbToHsv(facetColorGet());
        pkH = hsv[0]; pkS = hsv[1]; pkV = hsv[2];
        pickerOpen = true;
    }

    private void drawPicker(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        int w = 280, h = 3 * PK_BAR_H + 2 * PK_GAP + 40;
        int x = this.width / 2 - w / 2, y = this.height - 58 - h - 10;
        pkX = x + 14; pkY = y + 26; pkW = w - 28;
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 8,
            Colors.withAlpha(0xFF0A0F0E, 0.95f), Colors.BORDER_STRONG);
        ctx.text(tr, ax("COULEUR"), x + 14, y + 10, Colors.ACCENT, false);
        int cur = fr.reborn.hud.skin.SkinSpec.hsvToArgb(pkH, pkS, pkV);
        DrawHelpers.roundedOutlinedRect(ctx, x + w - 38, y + 8, 24, 14, 3, cur, Colors.WHITE_PURE);

        // Barre Teinte (spectre en 6 segments).
        int hueY = pkY;
        for (int i = 0; i < 6; i++) {
            int segW = pkW / 6;
            int cA = fr.reborn.hud.skin.SkinSpec.hsvToArgb(i / 6f, 1f, 1f);
            int cB = fr.reborn.hud.skin.SkinSpec.hsvToArgb((i + 1) / 6f, 1f, 1f);
            DrawHelpers.horizontalGradient(ctx, pkX + i * segW, hueY, segW, PK_BAR_H, cA, cB);
        }
        drawPkKnob(ctx, hueY, pkH);
        // Barre Saturation.
        int satY = pkY + PK_BAR_H + PK_GAP;
        DrawHelpers.horizontalGradient(ctx, pkX, satY, pkW, PK_BAR_H,
            fr.reborn.hud.skin.SkinSpec.hsvToArgb(pkH, 0f, pkV),
            fr.reborn.hud.skin.SkinSpec.hsvToArgb(pkH, 1f, pkV));
        drawPkKnob(ctx, satY, pkS);
        // Barre Valeur.
        int valY = pkY + 2 * (PK_BAR_H + PK_GAP);
        DrawHelpers.horizontalGradient(ctx, pkX, valY, pkW, PK_BAR_H,
            fr.reborn.hud.skin.SkinSpec.hsvToArgb(pkH, pkS, 0f),
            fr.reborn.hud.skin.SkinSpec.hsvToArgb(pkH, pkS, 1f));
        drawPkKnob(ctx, valY, pkV);
    }

    private void drawPkKnob(GuiGraphicsExtractor ctx, int barY, float t) {
        int kx = pkX + Math.round(t * (pkW - 1));
        DrawHelpers.roundedOutlinedRect(ctx, kx - 2, barY - 2, 4, PK_BAR_H + 4, 1,
            Colors.WHITE_PURE, Colors.withAlpha(0xFF000000, 0.8f));
    }

    /** Clic dans le picker : renvoie true si consommé (démarre un drag). */
    private boolean pickerClick(int mx, int my) {
        if (!pickerOpen) return false;
        int[] ys = { pkY, pkY + PK_BAR_H + PK_GAP, pkY + 2 * (PK_BAR_H + PK_GAP) };
        for (int i = 0; i < 3; i++) {
            if (hit(mx, my, pkX - 4, ys[i] - 4, pkW + 8, PK_BAR_H + 8)) {
                hsvDrag = i; updateHsv(mx); return true;
            }
        }
        return false;
    }

    private void updateHsv(int mx) {
        float t = Math.max(0f, Math.min(1f, (mx - pkX) / (float) Math.max(1, pkW - 1)));
        switch (hsvDrag) {
            case 0 -> pkH = t;
            case 1 -> pkS = t;
            case 2 -> pkV = t;
            default -> { return; }
        }
        facetColorSet(fr.reborn.hud.skin.SkinSpec.hsvToArgb(pkH, pkS, pkV));
    }

    // ── Accès générique à la facette courante (cat/subCat) ────────────
    // Peau (cat 0) = barre « couleur de peau » (rampe), sans cycleur.
    // Visage (cat 1) sous-cats : 0 Yeux · 1 Sourcils · 2 Cheveux · 3 Pilosité · 4 Accessoire.
    private boolean facetHasStyle() {
        if (cat == 0) return false;             // peau : barre de couleur (pas de cycleur)
        if (cat == 1) return subCat != 1;       // Visage : Sourcils = couleur seule
        return cat == 2 || cat == 3;
    }

    /** Catégorie catalogue de la facette courante ({@code null} = peau/genre, {@code "brows"} = sourcils). */
    private String facetCategory() {
        if (cat == 1) return switch (subCat) {
            case 0 -> "eyes"; case 1 -> "brows"; case 2 -> "hair"; case 3 -> "facial"; default -> "accessory";
        };
        if (cat == 3) return "outfit";
        return null;
    }

    /** La facette propose-t-elle une option « Aucun » (chauve/imberbe/torse nu/sans accessoire) ? */
    private boolean facetAllowsNone() {
        String c = facetCategory();
        return "hair".equals(c) || "facial".equals(c) || "outfit".equals(c) || "accessory".equals(c);
    }

    /** Assets éligibles (genre/clan ; staff exempté sauf aperçu joueur) pour la facette. */
    private java.util.List<fr.reborn.hud.skin.CharacterCatalog.Asset> facetList() {
        String c = facetCategory();
        if (c == null || "brows".equals(c)) return java.util.List.of();
        boolean exempt = CharacterData.staffExempt() && !previewAsPlayer;
        return fr.reborn.hud.skin.CharacterCatalog.available(c, appearance.female, effectiveClan(), exempt);
    }

    /** Id d'asset actuellement sélectionné pour la facette catalogue courante. */
    private String facetId() {
        return switch (facetCategory() == null ? "" : facetCategory()) {
            case "eyes" -> appearance.eyeId;
            case "hair" -> appearance.hairId;
            case "facial" -> appearance.facialId;
            case "outfit" -> appearance.outfitId;
            case "accessory" -> appearance.accessoryId;
            default -> "";
        };
    }

    private void facetSetId(String id) {
        switch (facetCategory() == null ? "" : facetCategory()) {
            case "eyes" -> appearance.eyeId = id;
            case "hair" -> appearance.hairId = id;
            case "facial" -> appearance.facialId = id;
            case "outfit" -> appearance.outfitId = id;
            case "accessory" -> appearance.accessoryId = id;
            default -> { }
        }
    }

    private static int indexOfId(java.util.List<fr.reborn.hud.skin.CharacterCatalog.Asset> l, String id) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(id)) return i;
        return -1;
    }

    private boolean facetHasColor() {
        String c = facetCategory();
        if (c == null) return false;                       // peau/genre : pas de picker ici
        if ("brows".equals(c)) return true;                // sourcils : couleur seule
        if (facetAllowsNone() && facetId().isBlank()) return false; // « Aucun » sélectionné
        var a = fr.reborn.hud.skin.CharacterCatalog.byId(c, facetId());
        return a != null && a.tintable();
    }

    private int facetColorGet() {
        return switch (facetCategory() == null ? "" : facetCategory()) {
            case "eyes" -> eyeSel == 2 ? appearance.eyeColorRight : appearance.eyeColor;
            case "brows" -> appearance.browColor;
            case "hair" -> appearance.hairColor;
            case "facial" -> appearance.facialColor;
            case "outfit" -> appearance.outfitZone[0]; // zone R (primaire) ; multi-zone à venir
            case "accessory" -> appearance.accessoryColor;
            default -> 0xFFFFFFFF;
        };
    }

    private void facetColorSet(int argb) {
        switch (facetCategory() == null ? "" : facetCategory()) {
            case "eyes" -> { // yeux : applique selon l'œil sélectionné
                if (eyeSel != 2) appearance.eyeColor = argb;      // les deux / gauche
                if (eyeSel != 1) appearance.eyeColorRight = argb; // les deux / droite
            }
            case "brows" -> appearance.browColor = argb;
            case "hair" -> appearance.hairColor = argb;
            case "facial" -> appearance.facialColor = argb;
            case "outfit" -> appearance.outfitZone[0] = argb;
            case "accessory" -> appearance.accessoryColor = argb;
            default -> { }
        }
        refreshPreview();
    }

    private int facetStyleCount() {
        if (cat == 0) return 1;
        if (cat == 2) return 2;
        return facetList().size() + (facetAllowsNone() ? 1 : 0);
    }

    private int facetStyleIdx() {
        if (cat == 0) return 0;
        if (cat == 2) return "Femme".equals(sexe) ? 1 : 0;
        boolean none = facetAllowsNone();
        if (none && facetId().isBlank()) return 0;
        int p = indexOfId(facetList(), facetId());
        return p < 0 ? 0 : p + (none ? 1 : 0);
    }

    private String facetStyleName() {
        if (cat == 0) return "PEAU";
        if (cat == 2) return sexe.toUpperCase(Locale.FRENCH);
        String c = facetCategory();
        if ("brows".equals(c)) return "SOURCILS";
        if (facetAllowsNone() && facetId().isBlank()) return "AUCUN";
        var a = fr.reborn.hud.skin.CharacterCatalog.byId(c, facetId());
        if (a != null) return a.name;
        return facetAllowsNone() ? "AUCUN" : "—";
    }

    private void cycleFacet(int dir) {
        switch (cat) {
            case 0 -> { /* peau : barre de couleur, pas de cycle */ }
            case 2 -> setSexe("Homme".equals(sexe) ? "Femme" : "Homme");
            default -> { // facettes catalogue (yeux/cheveux/pilosité/tenue/accessoire)
                var list = facetList();
                boolean none = facetAllowsNone();
                int count = list.size() + (none ? 1 : 0);
                if (count == 0) break;                      // catalogue vide → rien à cycler
                int nidx = fr.reborn.hud.skin.SkinSpec.cycle(facetStyleIdx(), count, dir);
                facetSetId(none && nidx == 0 ? "" : list.get(nidx - (none ? 1 : 0)).id);
            }
        }
        refreshPreview();
    }

    /**
     * Change le sexe et synchronise le jeu de PNG de peau (genre) + la carrure :
     * Femme = Alex (slim) imposé ; Homme = carrure mémorisée ({@link #maleSlim}).
     */
    private void setSexe(String s) {
        sexe = s;
        appearance.female = "Femme".equals(s);
        appearance.slim = appearance.female || maleSlim;
        refreshPreview();
    }

    /** Change la carrure de l'Homme (Classique/Alex) et rafraîchit l'aperçu. */
    private void setBuild(boolean slim) {
        maleSlim = slim;
        appearance.slim = slim;
        refreshPreview();
    }

    /** Clic dans l'éditeur KORVEX (grille ou catégorie). true si consommé. */
    private boolean apparenceClick(int mx, int my) {
        int cx = this.width / 2;
        if (cat < 0) {
            // Toggle « Mon skin ».
            if (hit(mx, my, 24, 22, 150, 24)) {
                appearance.useOwnSkin = !appearance.useOwnSkin; refreshPreview(); return true;
            }
            // Icônes de catégorie.
            int[][] pos = catPositions();
            for (int i = 0; i < 4; i++) {
                if (hit(mx, my, pos[i][0] - 20, pos[i][1] - 20, 40, 40)) {
                    if (appearance.useOwnSkin && i != 2) { // « mon skin » : seul le genre reste
                        toast("Désactive « Mon skin » pour composer l'apparence.");
                    } else { cat = i; subCat = 0; pickerOpen = false; }
                    return true;
                }
            }
            // CONFIRMER → étape Valider ; RETOUR → Identité.
            int by = this.height - 58;
            if (hit(mx, my, cx - 95, by, 190, 30)) { goToStep(3); return true; }
            if (hit(mx, my, 24, by, 130, 30)) { goToStep(1); return true; }
            return false;
        }
        // Dans une catégorie.
        if (pickerClick(mx, my)) return true;
        int topY = 60;
        if (cat == 1) { // sous-catégories Visage
            int n = VISAGE_SUBS.length, gap = 64, startX = cx - (n - 1) * gap / 2;
            for (int i = 0; i < n; i++) {
                int ix = startX + i * gap;
                if (hit(mx, my, ix - 18, topY - 18, 36, 36)) { subCat = i; eyeSel = 0; pickerOpen = false; return true; }
            }
        }
        int barY = this.height - 58;
        // Corps : toggle Carrure (Classique | Alex) + barre couleur de peau.
        if (cat == 0) {
            int tw = 220, tx = cx - tw / 2, ty = barY - 66, hw = tw / 2;
            if (hit(mx, my, tx, ty, hw, 22)) {
                if (appearance.female) toast("La Femme est toujours en carrure Alex (slim).");
                else setBuild(false);
                return true;
            }
            if (hit(mx, my, tx + hw, ty, hw, 22)) {
                if (!appearance.female) setBuild(true);
                return true;
            }
            if (hit(mx, my, skX - 3, skY - 3, skW + 6, 16)) { skinDrag = true; setSkinFromTrack(mx); return true; }
        }
        // Yeux : toggle œil édité (les deux / gauche / droite).
        if (cat == 1 && subCat == 0) {
            int w = 190, x = cx - w / 2, y = barY - 66;
            if (hit(mx, my, x, y, w, 22)) { cycleEyeSel(); return true; }
        }
        if (facetHasStyle()) {
            int cyy = barY - 34, cw = 150, sx = cx - cw / 2;
            if (hit(mx, my, sx, cyy, 26, 22)) { cycleFacet(-1); return true; }
            if (hit(mx, my, sx + cw - 26, cyy, 26, 22)) { cycleFacet(+1); return true; }
        }
        // RETOUR / CONFIRMER (les deux reviennent à la grille).
        if (hit(mx, my, 24, barY, 130, 30)) { cat = -1; pickerOpen = false; return true; }
        if (hit(mx, my, this.width - 154, barY, 130, 30)) { cat = -1; pickerOpen = false; return true; }
        // 🎨 (ouvre/ferme le picker).
        if (facetHasColor()) {
            int nameW = 300, nameX = cx - nameW / 2;
            if (hit(mx, my, nameX - 40, barY, 30, 30) || hit(mx, my, nameX + nameW + 10, barY, 30, 30)) {
                if (pickerOpen) pickerOpen = false; else openPicker();
                return true;
            }
        }
        return true; // clic dans l'éditeur = consommé (évite de retomber sur le monde)
    }

    /** Bouton style KORVEX (crochets latéraux + libellé + hint touche). */
    private void drawKorvexButton(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h,
                                  String label, String key, boolean hover, boolean primary) {
        int fill = primary
            ? Colors.withAlpha(Colors.ACCENT, hover ? 0.35f : 0.2f)
            : Colors.withAlpha(0xFF000000, hover ? 0.6f : 0.45f);
        int border = hover ? Colors.WHITE_PURE : primary ? Colors.ACCENT : Colors.BORDER;
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 7, fill, border);
        // Crochets latéraux.
        DrawHelpers.rect(ctx, x + 6, y + 6, 2, h - 12, border);
        DrawHelpers.rect(ctx, x + w - 8, y + 6, 2, h - 12, border);
        Component t = ax(label);
        ctx.text(tr, t, x + (w - tr.width(t)) / 2, y + (h - 8) / 2,
            primary ? Colors.WHITE_PURE : Colors.FOREGROUND, false);
        if (key != null) ctx.text(tr, RebornFont.arcade(key), x + w - 14, y + h - 10,
            Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.8f), false);
    }

    private void renderValidate(GuiGraphicsExtractor ctx, Font tr) {
        int x = panelX();
        int w = panelW() - 8;
        int y = contentTop() + 4;
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, 132, 8,
            Colors.withAlpha(0xFF000000, 0.4f), Colors.BORDER);
        int ry = y + 12;
        recap(ctx, tr, x + 12, ry,       w - 24, "Prénom", blankDash(currentName().trim()));
        recap(ctx, tr, x + 12, ry + 18,  w - 24, "Clan", blankDash(effectiveClan()));
        recap(ctx, tr, x + 12, ry + 36,  w - 24, "Village", blankDash(village));
        recap(ctx, tr, x + 12, ry + 54,  w - 24, "Sexe", sexe);
        recap(ctx, tr, x + 12, ry + 72,  w - 24, "Âge", String.valueOf(age));
        recap(ctx, tr, x + 12, ry + 90,  w - 24, "Taille", (int) Math.round(1.8 * size * 100) + " cm");
        descTitle = "Validation";
        descBody = "Clique sur « Valider » pour créer ton personnage et entrer en jeu.";
    }

    // ── Widgets ───────────────────────────────────────────────────
    private void label(GuiGraphicsExtractor ctx, Font tr, String s, int x, int y) {
        ctx.text(tr, RebornFont.arcade(s), x, y, Colors.FOREGROUND_MUTED, false);
    }

    /** Tuile village/clan : monogramme + légende, avec états sélection/verrou/hover. */
    private void tile(GuiGraphicsExtractor ctx, Font tr, int x, int y, String monogram,
                      String caption, String iconSlug, int color, boolean sel, boolean locked, boolean hover) {
        int fill = locked ? Colors.withAlpha(0xFF000000, 0.5f)
            : sel ? Colors.withAlpha(color, 0.55f)
            : hover ? Colors.withAlpha(color, 0.35f)
            : Colors.withAlpha(color, 0.18f);
        int border = locked ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.4f)
            : sel ? Colors.GOLD
            : hover ? Colors.withAlpha(color, 0.95f)
            : Colors.withAlpha(Colors.FOREGROUND, 0.22f);
        DrawHelpers.roundedOutlinedRect(ctx, x, y, TILE, TILE, 8, fill, border);

        // Logo si livré (assets/reborn/textures/character/ui/<iconSlug>.png), sinon
        // monogramme stylé. Tuile verrouillée = on garde le monogramme (croix rouge).
        boolean hasLogo = !locked && iconSlug != null
            && blitTile(ctx, iconSlug, x + TILE / 2, y + TILE / 2);
        if (!hasLogo) {
            Component m = RebornFont.arcade(monogram);
            int mCol = locked ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.6f) : Colors.WHITE_PURE;
            ctx.text(tr, m, x + (TILE - tr.width(m)) / 2, y + (TILE - 8) / 2, mCol, false);
        }

        if (locked) {
            DrawHelpers.thickLine(ctx, x + 9, y + 9, x + TILE - 9, y + TILE - 9, 2,
                Colors.withAlpha(Colors.DANGER, 0.9f));
            DrawHelpers.thickLine(ctx, x + TILE - 9, y + 9, x + 9, y + TILE - 9, 2,
                Colors.withAlpha(Colors.DANGER, 0.9f));
        }

        Component cap = RebornFont.arcade(caption);
        int capCol = locked ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.55f)
            : sel ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE;
        int cw = tr.width(cap);
        int cellCx = x + TILE / 2;
        ctx.text(tr, cap, cellCx - cw / 2, y + TILE + 3, capCol, false);
    }

    private void pill(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h, String s,
                      boolean sel, boolean hover) {
        int fill = sel ? Colors.withAlpha(Colors.ACCENT, 0.45f)
            : hover ? Colors.SURFACE_OVERLAY : Colors.withAlpha(0xFF000000, 0.4f);
        int border = sel ? Colors.GOLD : hover ? Colors.withAlpha(Colors.FOREGROUND, 0.4f) : Colors.BORDER;
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 6, fill, border);
        Component t = RebornFont.arcade(s);
        ctx.text(tr, t, x + (w - tr.width(t)) / 2, y + (h - 8) / 2,
            sel ? Colors.WHITE_PURE : Colors.FOREGROUND, false);
    }

    private void readonlyField(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, String val) {
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, 20, 5,
            Colors.withAlpha(0xFF000000, 0.35f), Colors.withAlpha(Colors.FOREGROUND, 0.15f));
        ctx.text(tr, RebornFont.arcade(val), x + 6, y + 6, Colors.FOREGROUND_SUBTLE, false);
    }

    private void square(GuiGraphicsExtractor ctx, Font tr, int x, int y, String s, boolean hover) {
        DrawHelpers.roundedOutlinedRect(ctx, x, y, 22, 22, 5,
            hover ? Colors.SURFACE_OVERLAY : Colors.withAlpha(0xFF000000, 0.4f),
            hover ? Colors.GOLD : Colors.BORDER);
        Component t = RebornFont.arcade(s);
        ctx.text(tr, t, x + (22 - tr.width(t)) / 2, y + 7, Colors.WHITE_PURE, false);
    }

    private void recap(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, String key, String val) {
        ctx.text(tr, RebornFont.arcade(key), x, y, Colors.FOREGROUND_MUTED, false);
        Component v = RebornFont.arcade(val);
        ctx.text(tr, v, x + w - tr.width(v), y, Colors.WHITE_PURE, false);
    }

    private void drawDescBox(GuiGraphicsExtractor ctx, Font tr) {
        if (descTitle == null || descBody == null) return;
        int boxW = 208;
        int boxX = this.width - boxW - 44;
        if (boxX < panelX() + panelW() + 16) return; // écran trop étroit : on masque
        List<FormattedCharSequence> lines = tr.split(RebornFont.body(descBody), boxW - 20);
        int boxH = 26 + lines.size() * 11 + 8;
        int boxY = Math.max(contentTop(), (this.height - boxH) / 2);
        DrawHelpers.roundedOutlinedRect(ctx, boxX, boxY, boxW, boxH, 8,
            Colors.withAlpha(0xFF0A0608, 0.9f), Colors.BORDER_STRONG);
        DrawHelpers.rect(ctx, boxX, boxY, 3, boxH, Colors.GOLD);
        ctx.text(tr, RebornFont.arcade(descTitle), boxX + 12, boxY + 10, Colors.GOLD, false);
        int ly = boxY + 26;
        for (FormattedCharSequence l : lines) {
            ctx.text(tr, l, boxX + 12, ly, Colors.FOREGROUND_SUBTLE, false);
            ly += 11;
        }
    }

    private void drawNav(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        int y = navY();
        int x = panelX();
        // Retour (sombre).
        boolean hb = hit(mx, my, x, y, NAV_W, NAV_H);
        DrawHelpers.roundedOutlinedRect(ctx, x, y, NAV_W, NAV_H, 8,
            hb ? Colors.SURFACE_OVERLAY : Colors.withAlpha(0xFF000000, 0.5f),
            hb ? Colors.withAlpha(Colors.FOREGROUND, 0.5f) : Colors.BORDER);
        Component back = RebornFont.arcade("Retour");
        ctx.text(tr, back, x + (NAV_W - tr.width(back)) / 2, y + (NAV_H - 8) / 2,
            Colors.FOREGROUND, false);

        // Suivant / Valider (clair, façon tan Zenkai).
        int nx = x + NAV_W + 12;
        boolean last = step == 3;
        boolean hn = hit(mx, my, nx, y, NAV_W, NAV_H);
        int fill = hn ? Colors.GOLD : Colors.withAlpha(Colors.GOLD, 0.82f);
        DrawHelpers.roundedRect(ctx, nx, y, NAV_W, NAV_H, 8, fill);
        Component nt = RebornFont.arcade(last ? "Valider" : "Suivant");
        ctx.text(tr, nt, nx + (NAV_W - tr.width(nt)) / 2, y + (NAV_H - 8) / 2, 0xFF1A1008, false);
    }

    // ── Helpers géométrie / texte ─────────────────────────────────
    private int[] tileXY(int baseY, int index) {
        int pr = perRow();
        int col = index % pr, row = index / pr;
        int x = panelX() + col * CELL_W + (CELL_W - TILE) / 2;
        int y = baseY + row * CELL_H;
        return new int[] { x, y };
    }

    private int rows(int count) {
        int pr = perRow();
        return (count + pr - 1) / pr;
    }

    private String mono(String s) {
        if (s == null || s.isBlank()) return "?";
        if ("Autre".equalsIgnoreCase(s)) return "+";
        return s.substring(0, 1).toUpperCase(Locale.FRENCH);
    }

    private int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private String blankDash(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String currentName() { return nameField != null ? nameField.getValue() : ""; }

    // ── Interactions ──────────────────────────────────────────────
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y(); int button = event.button();
        if (button != 0) return super.mouseClicked(event, doubleClick);
        int mx = (int) mouseX, my = (int) mouseY;
        // Feedback sonore de clic dans la création de perso.
        fr.reborn.hud.menu.RebornSounds.uiClick();

        // Étape Apparence : éditeur KORVEX plein écran (aucun chrome wizard actif).
        if (step == 2) { apparenceClick(mx, my); return true; }

        // Onglets : navigation libre. En avant, on valide chaque étape traversée
        // (impossible de sauter un champ requis) ; en arrière, direct.
        int ti = tabHit(mx, my);
        if (ti >= 0 && ti != step) {
            if (ti > step) {
                for (int s = step; s < ti; s++) {
                    if (!stepValid(s)) { goToStep(s); return true; }
                }
            }
            goToStep(ti);
            return true;
        }

        // Nav.
        int y = navY();
        if (hit(mx, my, panelX(), y, NAV_W, NAV_H)) { onBack(); return true; }
        if (hit(mx, my, panelX() + NAV_W + 12, y, NAV_W, NAV_H)) { onNext(); return true; }

        switch (step) {
            case 0 -> {
                int top = contentTop();
                int vBase = top + 14;
                for (int i = 0; i < VILLAGES.length; i++) {
                    int[] p = tileXY(vBase, i);
                    if (hit(mx, my, p[0], p[1], TILE, TILE)) {
                        if (villageLocked(VILLAGES[i])) { toast("Ce village ne correspond pas à ta candidature."); }
                        else {
                            village = VILLAGES[i];
                            if (!containsClan(clan)) clan = "";   // le clan doit appartenir au village
                        }
                        return true;
                    }
                }
                int cBase = vBase + rows(VILLAGES.length) * CELL_H + 6 + 14;
                String[] clans = villageClans();
                for (int i = 0; i < clans.length; i++) {
                    int[] p = tileXY(cBase, i);
                    if (hit(mx, my, p[0], p[1], TILE, TILE)) {
                        if (clanLocked(clans[i])) { toast("Ce clan ne correspond pas à ta candidature."); }
                        else clan = clans[i];
                        return true;
                    }
                }
                // Champ « Autre » : laisser l'EditBox gérer le clic (focus).
                if ("Autre".equals(clan) && customClanField.isVisible()) {
                    if (customClanField.mouseClicked(event, doubleClick)) {
                        setFocused(customClanField);
                        return true;
                    }
                }
            }
            case 1 -> {
                int x = panelX(), w = panelW() - 8, yy = contentTop();
                int pw = (w - 8) / 2;
                for (int i = 0; i < SEXES.length; i++) {
                    int bx = x + i * (pw + 8);
                    if (hit(mx, my, bx, yy + 12, pw, 26)) { setSexe(SEXES[i]); return true; }
                }
                // Slider taille.
                if (hit(mx, my, sizeTX, sizeTY - 4, sizeTW, 16)) {
                    draggingSize = true; setSizeFromTrack(mx); return true;
                }
                // Âge.
                int ay = yy + 172;
                if (hit(mx, my, x, ay + 12, 22, 22)) { age = Math.max(0, age - 1); return true; }
                if (hit(mx, my, x + 58, ay + 12, 22, 22)) { age = Math.min(200, age + 1); return true; }
                // Champ prénom : focus + clic.
                if (nameField.mouseClicked(event, doubleClick)) {
                    setFocused(nameField);
                    return true;
                }
            }
            default -> { }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dX, double dY) {
        int mx = (int) event.x();
        // Clic droit maintenu = rotation horizontale du perso.
        if (event.button() == 1) { avatarSpin += (float) dX; return true; }
        if (hsvDrag >= 0) { updateHsv(mx); return true; }
        if (skinDrag) { setSkinFromTrack(mx); return true; }
        if (draggingSize) { setSizeFromTrack(mx); return true; }
        return super.mouseDragged(event, dX, dY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Molette = zoom sur le perso.
        avatarZoom = Math.max(0.5f, Math.min(2.5f, avatarZoom + (float) verticalAmount * 0.12f));
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingSize = false;
        hsvDrag = -1;
        skinDrag = false;
        return super.mouseReleased(event);
    }

    private void setSizeFromTrack(int mx) {
        float t = Math.max(0f, Math.min(1f, (mx - sizeTX) / (float) Math.max(1, sizeTW - 1)));
        double v = 0.85 + t * (1.15 - 0.85);
        size = Math.round(v * 100.0) / 100.0;
    }

    private int tabHit(int mx, int my) {
        String[] labels = tabLabels();
        int x = panelX(), y = 62;
        for (int i = 0; i < labels.length; i++) {
            int w = this.font.width(labels[i]);
            if (hit(mx, my, x, y - 2, w, 12)) return i;
            x += w + 14;
        }
        return -1;
    }

    private void goToStep(int s) {
        step = Math.max(0, Math.min(3, s));
        setFocused(null);
        if (nameField != null) nameField.setFocused(false);
        if (customClanField != null) customClanField.setFocused(false);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Laisse les champs de texte capter les frappes quand ils ont le focus.
        if (nameField != null && nameField.isFocused() && nameField.keyPressed(event)) return true;
        if (customClanField != null && customClanField.isFocused()
                && customClanField.keyPressed(event)) return true;

        // Étape Apparence — raccourcis clavier KORVEX.
        if (step == 2) {
            int k = event.key();
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                if (cat >= 0) { cat = -1; pickerOpen = false; } else goToStep(1);
                return true;
            }
            if (cat < 0) {
                if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_4) {
                    int i = k - GLFW.GLFW_KEY_1;
                    if (!(appearance.useOwnSkin && i != 2)) { cat = i; subCat = 0; pickerOpen = false; }
                    return true;
                }
                if (k == GLFW.GLFW_KEY_F) { goToStep(3); return true; }
            } else {
                if (k == GLFW.GLFW_KEY_A && facetHasStyle()) { cycleFacet(-1); return true; }
                if (k == GLFW.GLFW_KEY_D && facetHasStyle()) { cycleFacet(+1); return true; }
                if (k == GLFW.GLFW_KEY_S && cat == 0) { // toggle Carrure (Corps)
                    if (appearance.female) toast("La Femme est toujours en carrure Alex (slim).");
                    else setBuild(!appearance.slim);
                    return true;
                }
                if (k == GLFW.GLFW_KEY_E && cat == 1 && subCat == 0) { cycleEyeSel(); return true; } // œil édité
                if (k == GLFW.GLFW_KEY_G && CharacterData.staffExempt()) { // staff : aperçu gating joueur
                    previewAsPlayer = !previewAsPlayer; return true;
                }
                if (k == GLFW.GLFW_KEY_F) { cat = -1; pickerOpen = false; return true; }
            }
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onBack(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (nameField != null && nameField.isFocused() && nameField.charTyped(event)) return true;
        if (customClanField != null && customClanField.isFocused()
                && customClanField.charTyped(event)) return true;
        return super.charTyped(event);
    }

    private void onBack() {
        if (step == 0) {
            Minecraft.getInstance().setScreenAndShow(new CharacterSelectScreen());
        } else {
            goToStep(step - 1);
        }
    }

    /** Valide une étape (avec toast d'erreur). true = complète. */
    private boolean stepValid(int s) {
        switch (s) {
            case 0 -> {
                if (village.isBlank()) { toast("Choisis un village."); return false; }
                if (clan.isBlank()) { toast("Choisis un clan (ou « Autre »)."); return false; }
                if ("Autre".equals(clan)) {
                    String custom = customClanField.getValue().trim();
                    if (custom.isBlank()) { toast("Entre le nom de ton clan."); return false; }
                    if (isPredefinedClanName(custom)) {
                        toast("« " + custom + " » est un clan existant — choisis-le dans la liste ou un autre nom.");
                        return false;
                    }
                }
                return true;
            }
            case 1 -> {
                if (currentName().trim().isBlank()) { toast("Entre un prénom."); return false; }
                return true;
            }
            default -> { return true; }
        }
    }

    private void onNext() {
        if (step < 3) {
            if (!stepValid(step)) return;
            goToStep(step + 1);
            return;
        }
        // Étape Valider : re-vérifie les étapes requises (l'onglet a pu être sauté).
        if (!stepValid(0)) { goToStep(0); return; }
        if (!stepValid(1)) { goToStep(1); return; }
        submit();
    }

    private void submit() {
        // create\n<nom>\n<clan>\n<village>\n<sexe>\n<age>\n<size>\n<apparence…>
        String cmd = "create\n" + currentName().trim() + "\n" + effectiveClan() + "\n" + village
            + "\n" + sexe + "\n" + age + "\n" + String.format(Locale.US, "%.2f", size)
            + "\n" + appearance.serialize();
        if (ClientPlayNetworking.canSend(CharacterPayload.ID)) {
            ClientPlayNetworking.send(new CharacterPayload(cmd));
            submitted = true;      // on garde le skin composé appliqué
            refreshPreview();      // s'assure que l'override final est en place
            this.onClose();        // le serveur crée + active le perso
        } else {
            toast("Création (hors serveur) — impossible ici.");
        }
    }

    private void toast(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Reborn] " + msg));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
