package fr.reborn.hud.menu.character;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
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

    private static final String[] CLANS = {
        "Senju", "Uchiha", "Hyuga", "Nara", "Sarutobi", "Uzumaki",
        "Aburame", "Akimichi", "Hatake", "Yamanaka", "Kurama", "Autre"
    };
    private static final int[] C_COLOR = {
        0xFF2E8B57, 0xFFB1302B, 0xFFCFC6E0, 0xFF5B6B3A, 0xFFC8722E, 0xFFD9532E,
        0xFF4A4F3A, 0xFFE0A33B, 0xFFAEB4BC, 0xFF8E5BB5, 0xFF8B1E2B, 0xFF6A6A6A
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
        "Clan ou famille personnalisé. Saisis un nom qui ne correspond à aucun clan connu."
    };

    private static final String[] SEXES = { "Homme", "Femme" };

    // Palette peau (aperçu — Phase 2, non envoyé au serveur pour l'instant).
    private static final int SKIN_DARK = 0xFF3B2A1E;
    private static final int SKIN_LIGHT = 0xFFFFE0BD;

    // ── Géométrie ─────────────────────────────────────────────────
    private static final int TILE = 42, CELL_W = 54, CELL_H = 60;
    private static final int NAV_W = 118, NAV_H = 26;

    // ── Draft ─────────────────────────────────────────────────────
    private String village = "";
    private String clan = "";
    private String sexe = "Homme";
    private int age = 12;
    private double size = 1.0;      // 0.85–1.15 (≈ 1.53–2.07 m)

    /** Apparence (peau/coiffure/yeux/tenue) — pilote la composition + la synchro. */
    private final fr.reborn.hud.skin.SkinSpec appearance = new fr.reborn.hud.skin.SkinSpec();
    /** Vrai une fois le perso validé : on garde alors l'override (sinon on nettoie). */
    private boolean submitted = false;

    private int step = 0;           // 0=village/clan, 1=identité, 2=apparence, 3=valider
    private EditBox nameField;
    private EditBox customClanField;

    // Sliders (rects mémorisés au render, réutilisés au click/drag).
    private int skinTX, skinTY, skinTW;
    private int sizeTX, sizeTY, sizeTW;
    private boolean draggingSkin = false, draggingSize = false;

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
            prevHudHidden = mc.options.hideGui;
            mc.options.hideGui = true;
            captured = true;
        }
        fr.reborn.hud.animation.MovementAnimations.INSTANCE.startPose();

        // Pré-remplissage depuis la candidature (verrou whitelist).
        preselectFromCandidature();

        nameField = new EditBox(this.font, panelX(), 0, panelW() - 8, 20,
            Component.literal("Prénom"));
        nameField.setMaxLength(24);
        nameField.setHint(Component.literal("Prénom du personnage…"));
        String cn = CharacterData.candidatureName();
        if (cn != null && !cn.isBlank()) nameField.setValue(cn);
        addWidget(nameField);

        customClanField = new EditBox(this.font, panelX(), 0, panelW() - 8, 20,
            Component.literal("Clan personnalisé"));
        customClanField.setMaxLength(24);
        customClanField.setHint(Component.literal("Nom de ton clan / famille…"));
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
            mc.options.hideGui = prevHudHidden;
            captured = false;
        }
        fr.reborn.hud.animation.MovementAnimations.INSTANCE.stopPose();
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

    // ── Layout ────────────────────────────────────────────────────
    private int panelX() { return 34; }
    private int panelW() { return Math.min(372, (int) (this.width * 0.46f)); }
    private int contentTop() { return 96; }
    private int perRow() { return Math.max(3, panelW() / CELL_W); }
    private int navY() { return this.height - 40; }

    // ── Rendu ─────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Voile gauche (panneau lisible) qui s'estompe vers la droite (monde visible).
        int panelRight = panelX() + panelW() + 24;
        DrawHelpers.horizontalGradient(ctx, 0, 0, panelRight, this.height,
            Colors.withAlpha(0xFF080405, 0.86f), 0x00000000);
        // Dégradés haut/bas pour asseoir le logo et les boutons.
        ctx.fillGradient(0, 0, this.width, 70, Colors.withAlpha(0xFF000000, 0.55f), 0x00000000);
        ctx.fillGradient(0, this.height - 70, this.width, this.height,
            0x00000000, Colors.withAlpha(0xFF000000, 0.65f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font tr = this.font;
        descTitle = null;
        descBody = null;

        drawLogo(ctx, tr);
        drawTabs(ctx, tr, mouseX, mouseY);
        drawStepSubtitle(ctx, tr);

        // Visibilité des champs de texte selon l'étape.
        nameField.setVisible(step == 1);
        customClanField.setVisible(step == 0 && "Autre".equals(clan));

        switch (step) {
            case 0 -> renderVillageClan(ctx, tr, mouseX, mouseY);
            case 1 -> renderIdentity(ctx, tr, mouseX, mouseY);
            case 2 -> renderApparence(ctx, tr, mouseX, mouseY);
            default -> renderValidate(ctx, tr);
        }

        drawDescBox(ctx, tr);
        drawNav(ctx, tr, mouseX, mouseY);
    }

    private void drawLogo(GuiGraphicsExtractor ctx, Font tr) {
        int x = panelX();
        // Léger espacement en dessinant lettre par lettre.
        int cx = x;
        for (char c : "REBORN".toCharArray()) {
            Component ch = Component.literal(String.valueOf(c));
            ctx.text(tr, ch, cx, 26, Colors.ACCENT, false);
            cx += tr.width(ch) + 3;
        }
        DrawHelpers.rect(ctx, x, 38, cx - x - 3, 1, Colors.withAlpha(Colors.GOLD, 0.8f));
        Component sub = Component.literal("Création de personnage");
        ctx.text(tr, sub, x, 44, Colors.FOREGROUND_MUTED, false);
    }

    private String[] tabLabels() {
        return new String[] { "Village & Clan", "Identité", "Apparence", "Valider" };
    }

    private void drawTabs(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        String[] labels = tabLabels();
        int x = panelX();
        int y = 62;
        for (int i = 0; i < labels.length; i++) {
            Component t = Component.literal(labels[i]);
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
        ctx.text(tr, Component.literal(s), panelX(), 80, Colors.FOREGROUND_SUBTLE, false);
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
            tile(ctx, tr, p[0], p[1], mono(V_SHORT[i]), V_SHORT[i], V_COLOR[i], sel, locked, hover);
            if (hover && !locked) { descTitle = V_SHORT[i]; descBody = V_DESC[i]; }
        }
        int vRows = rows(VILLAGES.length);
        int cLabelY = vBase + vRows * CELL_H + 6;
        label(ctx, tr, "CLAN", panelX(), cLabelY);
        int cBase = cLabelY + 14;
        for (int i = 0; i < CLANS.length; i++) {
            int[] p = tileXY(cBase, i);
            boolean sel = CLANS[i].equals(clan);
            boolean locked = clanLocked(CLANS[i]);
            boolean hover = hit(mx, my, p[0], p[1], TILE, TILE);
            tile(ctx, tr, p[0], p[1], mono(CLANS[i]), CLANS[i], C_COLOR[i], sel, locked, hover);
            if (hover && !locked) { descTitle = CLANS[i]; descBody = C_DESC[i]; }
        }

        // Champ « Autre » (nom de clan libre).
        if ("Autre".equals(clan)) {
            int cRows = rows(CLANS.length);
            int fy = cBase + cRows * CELL_H + 4;
            ctx.text(tr, Component.literal("NOM DE CLAN"), panelX(), fy, Colors.FOREGROUND_MUTED, false);
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

        // Couleur de peau (aperçu Phase 2).
        int sy = y + 136;
        label(ctx, tr, "COULEUR DE PEAU", x, sy);
        skinTX = x; skinTY = sy + 14; skinTW = w - 2;
        DrawHelpers.horizontalGradient(ctx, skinTX, skinTY, skinTW, 8, SKIN_DARK, SKIN_LIGHT);
        DrawHelpers.outlinedRect(ctx, skinTX, skinTY, skinTW, 8, 0, Colors.withAlpha(Colors.FOREGROUND, 0.3f));
        int skKnob = skinTX + Math.round(appearance.skinTone * (skinTW - 1));
        DrawHelpers.roundedOutlinedRect(ctx, skKnob - 3, skinTY - 2, 6, 12, 2,
            Colors.lerp(SKIN_DARK, SKIN_LIGHT, appearance.skinTone), Colors.WHITE_PURE);
        if (hit(mx, my, skinTX, skinTY - 3, skinTW, 14)) { descTitle = "Couleur de peau"; descBody = "Teinte de base de la peau (aperçu — personnalisation complète en Phase 2)."; }

        // Taille.
        int ty = y + 178;
        int cm = (int) Math.round(1.8 * size * 100);
        label(ctx, tr, "TAILLE", x, ty);
        Component cmT = Component.literal(cm + " cm");
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
        int ay = y + 214;
        label(ctx, tr, "ÂGE", x, ay);
        square(ctx, tr, x, ay + 12, "-", hit(mx, my, x, ay + 12, 22, 22));
        Component av = Component.literal(String.valueOf(age));
        ctx.text(tr, av, x + 40 - tr.width(av) / 2, ay + 12 + 7, Colors.WHITE_PURE, false);
        square(ctx, tr, x + 58, ay + 12, "+", hit(mx, my, x + 58, ay + 12, 22, 22));
        if (hit(mx, my, x, ay, w, 34)) { descTitle = "Âge"; descBody = "L'âge RP de départ de ton personnage."; }

        if (descTitle == null) { descTitle = "Identité"; descBody = "Renseigne le sexe, le prénom, la couleur de peau, la taille et l'âge."; }
    }

    // ── Étape 2 : Apparence (éditeur cosmétique façon KORVEX) ─────────
    private static final int ROW_H = 28, CTRL_W = 152, ARROW_W = 18, CTRL_H = 20;

    private void renderApparence(GuiGraphicsExtractor ctx, Font tr, int mx, int my) {
        int x = panelX();
        int w = panelW() - 8;
        int top = contentTop();

        boolean own = appearance.useOwnSkin;
        for (int i = 0; i < EDITOR_ROWS; i++) {
            int y = top + i * ROW_H;
            boolean enabled = rowEnabled(i);
            boolean hover = enabled && hit(mx, my, x, y, w, CTRL_H + 4);

            // Libellé.
            int lblCol = !enabled ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.5f)
                : Colors.FOREGROUND_MUTED;
            ctx.text(tr, Component.literal(rowLabel(i)), x, y + 6, lblCol, false);

            // Contrôle (cadre + flèches + valeur).
            int cx = x + w - CTRL_W, cy = y + 2;
            DrawHelpers.roundedOutlinedRect(ctx, cx, cy, CTRL_W, CTRL_H, 6,
                Colors.withAlpha(0xFF000000, enabled ? 0.42f : 0.25f),
                hover ? Colors.withAlpha(Colors.ACCENT, 0.7f) : Colors.BORDER);

            // Flèches ‹ ›.
            boolean lh = enabled && hit(mx, my, cx, cy, ARROW_W, CTRL_H);
            boolean rh = enabled && hit(mx, my, cx + CTRL_W - ARROW_W, cy, ARROW_W, CTRL_H);
            int arrCol = !enabled ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.4f) : Colors.ACCENT;
            drawGlyph(ctx, tr, "‹", cx, cy, ARROW_W, lh ? Colors.WHITE_PURE : arrCol);
            drawGlyph(ctx, tr, "›", cx + CTRL_W - ARROW_W, cy, ARROW_W,
                rh ? Colors.WHITE_PURE : arrCol);

            // Pastille de couleur (rows couleur) + valeur centrée.
            int swatch = rowSwatch(i);
            int textAreaX = cx + ARROW_W, textAreaW = CTRL_W - 2 * ARROW_W;
            String val = rowValue(i);
            int valW = tr.width(val);
            int vx = textAreaX + (textAreaW - valW) / 2;
            if (swatch != -1) {
                int sw = 8;
                int block = valW + sw + 4;
                vx = textAreaX + (textAreaW - block) / 2 + sw + 4;
                DrawHelpers.roundedOutlinedRect(ctx, vx - sw - 4, cy + (CTRL_H - sw) / 2, sw, sw, 2,
                    enabled ? swatch : Colors.withAlpha(swatch, 0.4f),
                    Colors.withAlpha(Colors.WHITE_PURE, 0.5f));
            }
            int valCol = !enabled ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.5f) : Colors.WHITE_PURE;
            ctx.text(tr, Component.literal(val), vx, cy + 6, valCol, false);

            if (hover) { descTitle = rowLabel(i); descBody = rowHint(i); }
        }

        // Note « mon skin ».
        int noteY = top + EDITOR_ROWS * ROW_H + 6;
        String note = own
            ? "Ton skin Minecraft personnel est conservé pour ce personnage."
            : "Aperçu procédural — les vrais visages / coiffures / tenues arrivent avec les assets.";
        for (FormattedCharSequence l : tr.split(Component.literal(note), w)) {
            ctx.text(tr, l, x, noteY, Colors.withAlpha(Colors.GOLD, 0.85f), false);
            noteY += 11;
        }

        if (descTitle == null) {
            descTitle = "Apparence";
            descBody = own
                ? "Tu as choisi de garder ton propre skin Minecraft."
                : "Compose l'apparence de ton personnage. Tout le monde (avec le mod) verra le même skin.";
        }
    }

    // Nombre de lignes de l'éditeur d'apparence.
    private static final int EDITOR_ROWS = 6;

    private String rowLabel(int i) {
        return switch (i) {
            case 0 -> "TYPE DE SKIN";
            case 1 -> "TEINTE DE PEAU";
            case 2 -> "COIFFURE";
            case 3 -> "COULEUR CHEVEUX";
            case 4 -> "YEUX";
            default -> "TENUE";
        };
    }

    private boolean rowEnabled(int i) {
        return i == 0 || !appearance.useOwnSkin; // « mon skin » désactive le reste
    }

    private String rowValue(int i) {
        return switch (i) {
            case 0 -> fr.reborn.hud.skin.SkinSpec.SKIN_TYPES[appearance.useOwnSkin ? 1 : 0];
            case 1 -> fr.reborn.hud.skin.SkinSpec.SKIN_STOP_NAMES[skinStopIndex()];
            case 2 -> fr.reborn.hud.skin.SkinSpec.HAIR_STYLES[appearance.hairStyle];
            case 3 -> fr.reborn.hud.skin.SkinSpec.HAIR_COLOR_NAMES[appearance.hairColor];
            case 4 -> fr.reborn.hud.skin.SkinSpec.EYE_COLOR_NAMES[appearance.eyeColor];
            default -> fr.reborn.hud.skin.SkinSpec.OUTFIT_NAMES[appearance.outfit];
        };
    }

    /** Couleur de pastille (ARGB) pour les lignes couleur, ou -1 si aucune. */
    private int rowSwatch(int i) {
        return switch (i) {
            case 3 -> fr.reborn.hud.skin.SkinSpec.HAIR_COLORS[appearance.hairColor];
            case 4 -> fr.reborn.hud.skin.SkinSpec.EYE_COLORS[appearance.eyeColor];
            case 5 -> fr.reborn.hud.skin.SkinSpec.OUTFIT_COLORS[appearance.outfit];
            default -> -1;
        };
    }

    private String rowHint(int i) {
        return switch (i) {
            case 0 -> "« RP composé » construit un skin depuis tes choix ; « Mon skin » garde ton skin Minecraft.";
            case 1 -> "Teinte de base de la peau (identique au curseur de l'étape Identité).";
            case 2 -> "Style de coiffure de ton personnage.";
            case 3 -> "Couleur des cheveux.";
            case 4 -> "Couleur des yeux.";
            default -> "Tenue portée par ton personnage.";
        };
    }

    /** Fait tourner l'option de la ligne i (dir = ±1) et rafraîchit l'aperçu. */
    private void cycleRow(int i, int dir) {
        switch (i) {
            case 0 -> appearance.useOwnSkin = !appearance.useOwnSkin;
            case 1 -> {
                int idx = fr.reborn.hud.skin.SkinSpec.cycle(
                    skinStopIndex(), fr.reborn.hud.skin.SkinSpec.SKIN_STOPS.length, dir);
                appearance.skinTone = fr.reborn.hud.skin.SkinSpec.SKIN_STOPS[idx];
            }
            case 2 -> appearance.hairStyle = fr.reborn.hud.skin.SkinSpec.cycle(
                appearance.hairStyle, fr.reborn.hud.skin.SkinSpec.HAIR_STYLES.length, dir);
            case 3 -> appearance.hairColor = fr.reborn.hud.skin.SkinSpec.cycle(
                appearance.hairColor, fr.reborn.hud.skin.SkinSpec.HAIR_COLORS.length, dir);
            case 4 -> appearance.eyeColor = fr.reborn.hud.skin.SkinSpec.cycle(
                appearance.eyeColor, fr.reborn.hud.skin.SkinSpec.EYE_COLORS.length, dir);
            default -> appearance.outfit = fr.reborn.hud.skin.SkinSpec.cycle(
                appearance.outfit, fr.reborn.hud.skin.SkinSpec.OUTFIT_COLORS.length, dir);
        }
        refreshPreview();
    }

    private int skinStopIndex() {
        float[] stops = fr.reborn.hud.skin.SkinSpec.SKIN_STOPS;
        int best = 0; float bd = Float.MAX_VALUE;
        for (int i = 0; i < stops.length; i++) {
            float d = Math.abs(stops[i] - appearance.skinTone);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    /** Clic dans l'éditeur d'apparence : renvoie true si une flèche a été activée. */
    private boolean apparenceClick(int mx, int my) {
        int x = panelX(), w = panelW() - 8, top = contentTop();
        for (int i = 0; i < EDITOR_ROWS; i++) {
            if (!rowEnabled(i)) continue;
            int y = top + i * ROW_H;
            int cx = x + w - CTRL_W, cy = y + 2;
            if (hit(mx, my, cx, cy, ARROW_W, CTRL_H)) { cycleRow(i, -1); return true; }
            if (hit(mx, my, cx + CTRL_W - ARROW_W, cy, ARROW_W, CTRL_H)) { cycleRow(i, +1); return true; }
            // Clic au centre = avance aussi (pratique pour le toggle Type).
            if (hit(mx, my, cx + ARROW_W, cy, CTRL_W - 2 * ARROW_W, CTRL_H)) { cycleRow(i, +1); return true; }
        }
        return false;
    }

    private void drawGlyph(GuiGraphicsExtractor ctx, Font tr, String g, int x, int y, int w, int col) {
        ctx.text(tr, Component.literal(g), x + (w - tr.width(g)) / 2, y + 6, col, false);
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
        ctx.text(tr, Component.literal(s), x, y, Colors.FOREGROUND_MUTED, false);
    }

    /** Tuile village/clan : monogramme + légende, avec états sélection/verrou/hover. */
    private void tile(GuiGraphicsExtractor ctx, Font tr, int x, int y, String monogram,
                      String caption, int color, boolean sel, boolean locked, boolean hover) {
        int fill = locked ? Colors.withAlpha(0xFF000000, 0.5f)
            : sel ? Colors.withAlpha(color, 0.55f)
            : hover ? Colors.withAlpha(color, 0.35f)
            : Colors.withAlpha(color, 0.18f);
        int border = locked ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.4f)
            : sel ? Colors.GOLD
            : hover ? Colors.withAlpha(color, 0.95f)
            : Colors.withAlpha(Colors.FOREGROUND, 0.22f);
        DrawHelpers.roundedOutlinedRect(ctx, x, y, TILE, TILE, 8, fill, border);

        Component m = Component.literal(monogram);
        int mCol = locked ? Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.6f) : Colors.WHITE_PURE;
        ctx.text(tr, m, x + (TILE - tr.width(m)) / 2, y + (TILE - 8) / 2, mCol, false);

        if (locked) {
            DrawHelpers.thickLine(ctx, x + 9, y + 9, x + TILE - 9, y + TILE - 9, 2,
                Colors.withAlpha(Colors.DANGER, 0.9f));
            DrawHelpers.thickLine(ctx, x + TILE - 9, y + 9, x + 9, y + TILE - 9, 2,
                Colors.withAlpha(Colors.DANGER, 0.9f));
        }

        Component cap = Component.literal(caption);
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
        Component t = Component.literal(s);
        ctx.text(tr, t, x + (w - tr.width(t)) / 2, y + (h - 8) / 2,
            sel ? Colors.WHITE_PURE : Colors.FOREGROUND, false);
    }

    private void readonlyField(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, String val) {
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, 20, 5,
            Colors.withAlpha(0xFF000000, 0.35f), Colors.withAlpha(Colors.FOREGROUND, 0.15f));
        ctx.text(tr, Component.literal(val), x + 6, y + 6, Colors.FOREGROUND_SUBTLE, false);
    }

    private void square(GuiGraphicsExtractor ctx, Font tr, int x, int y, String s, boolean hover) {
        DrawHelpers.roundedOutlinedRect(ctx, x, y, 22, 22, 5,
            hover ? Colors.SURFACE_OVERLAY : Colors.withAlpha(0xFF000000, 0.4f),
            hover ? Colors.GOLD : Colors.BORDER);
        Component t = Component.literal(s);
        ctx.text(tr, t, x + (22 - tr.width(t)) / 2, y + 7, Colors.WHITE_PURE, false);
    }

    private void recap(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, String key, String val) {
        ctx.text(tr, Component.literal(key), x, y, Colors.FOREGROUND_MUTED, false);
        Component v = Component.literal(val);
        ctx.text(tr, v, x + w - tr.width(v), y, Colors.WHITE_PURE, false);
    }

    private void drawDescBox(GuiGraphicsExtractor ctx, Font tr) {
        if (descTitle == null || descBody == null) return;
        int boxW = 208;
        int boxX = this.width - boxW - 44;
        if (boxX < panelX() + panelW() + 16) return; // écran trop étroit : on masque
        List<FormattedCharSequence> lines = tr.split(Component.literal(descBody), boxW - 20);
        int boxH = 26 + lines.size() * 11 + 8;
        int boxY = Math.max(contentTop(), (this.height - boxH) / 2);
        DrawHelpers.roundedOutlinedRect(ctx, boxX, boxY, boxW, boxH, 8,
            Colors.withAlpha(0xFF0A0608, 0.9f), Colors.BORDER_STRONG);
        DrawHelpers.rect(ctx, boxX, boxY, 3, boxH, Colors.GOLD);
        ctx.text(tr, Component.literal(descTitle), boxX + 12, boxY + 10, Colors.GOLD, false);
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
        Component back = Component.literal("Retour");
        ctx.text(tr, back, x + (NAV_W - tr.width(back)) / 2, y + (NAV_H - 8) / 2,
            Colors.FOREGROUND, false);

        // Suivant / Valider (clair, façon tan Zenkai).
        int nx = x + NAV_W + 12;
        boolean last = step == 3;
        boolean hn = hit(mx, my, nx, y, NAV_W, NAV_H);
        int fill = hn ? Colors.GOLD : Colors.withAlpha(Colors.GOLD, 0.82f);
        DrawHelpers.roundedRect(ctx, nx, y, NAV_W, NAV_H, 8, fill);
        Component nt = Component.literal(last ? "Valider" : "Suivant");
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
                        else village = VILLAGES[i];
                        return true;
                    }
                }
                int cBase = vBase + rows(VILLAGES.length) * CELL_H + 6 + 14;
                for (int i = 0; i < CLANS.length; i++) {
                    int[] p = tileXY(cBase, i);
                    if (hit(mx, my, p[0], p[1], TILE, TILE)) {
                        if (clanLocked(CLANS[i])) { toast("Ce clan ne correspond pas à ta candidature."); }
                        else clan = CLANS[i];
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
                    if (hit(mx, my, bx, yy + 12, pw, 26)) { sexe = SEXES[i]; return true; }
                }
                // Sliders.
                if (hit(mx, my, skinTX, skinTY - 4, skinTW, 16)) {
                    draggingSkin = true;
                    appearance.skinTone = valueFromTrack(mx, skinTX, skinTW, 0f, 1f);
                    refreshPreview();
                    return true;
                }
                if (hit(mx, my, sizeTX, sizeTY - 4, sizeTW, 16)) {
                    draggingSize = true; setSizeFromTrack(mx); return true;
                }
                // Âge.
                int ay = yy + 214;
                if (hit(mx, my, x, ay + 12, 22, 22)) { age = Math.max(0, age - 1); return true; }
                if (hit(mx, my, x + 58, ay + 12, 22, 22)) { age = Math.min(200, age + 1); return true; }
                // Champ prénom : focus + clic.
                if (nameField.mouseClicked(event, doubleClick)) {
                    setFocused(nameField);
                    return true;
                }
            }
            case 2 -> {
                if (apparenceClick(mx, my)) return true;
            }
            default -> { }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dX, double dY) {
        int mx = (int) event.x();
        if (draggingSkin) {
            appearance.skinTone = valueFromTrack(mx, skinTX, skinTW, 0f, 1f);
            refreshPreview();
            return true;
        }
        if (draggingSize) { setSizeFromTrack(mx); return true; }
        return super.mouseDragged(event, dX, dY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingSkin = false;
        draggingSize = false;
        return super.mouseReleased(event);
    }

    private float valueFromTrack(int mx, int tx, int tw, float min, float max) {
        float t = Math.max(0f, Math.min(1f, (mx - tx) / (float) Math.max(1, tw - 1)));
        return min + t * (max - min);
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
            Minecraft.getInstance().setScreen(new CharacterSelectScreen());
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
