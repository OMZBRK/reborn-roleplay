package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Poste de garde — panel staff en jeu, dans la DA Reborn (kit commun : voile sur le jeu, plaque laquée suspendue,
 * cadre de bois et papier washi). Motif propre à l'écran : chaque joueur est une plaquette de bois (fuda) et
 * sanctionner revient à apposer le sceau rouge 罰.
 *
 * <p><b>Structure (refonte du 14/10, « clarifier, bien espacer, rien ne dépasse »)</b> : une colonne de navigation en
 * groupes nommés (番 Garde : Alertes, Joueurs, Journal ; 令 Outils : Annonces, Chat staff, Commandes ; 医 Infirmerie
 * dans {@link GardeScreen}) ; chaque vue a la même ossature — un <b>en-tête fixe</b> (titre ou fil d'Ariane, compteur,
 * filet), une <b>zone d'outils fixe</b> (recherche, filtres, carte d'identité de la fiche, en-têtes de colonnes), un
 * <b>corps qui défile</b> (barre de défilement quand il dépasse) et un <b>pied fixe</b> (saisie du chat, pagination du
 * journal). Grille régulière : marges de 8, gouttières de 6, lignes espacées de 4 ; colonnes calculées depuis la
 * largeur disponible (une fois par taille d'écran), textes coupés par « … » avec info-bulle au survol.
 *
 * <p>Toutes les données viennent du serveur ({@link StaffClient}) ; chaque bouton envoie une demande que le serveur
 * revérifie (grade, cible, palier). Protocole : {@code docs/technique/PLUGINS_REFERENCE.md} §4 bis.
 */
public class StaffScreen extends Screen {

    /* --------------------------------------------------------------- palette (kit DA Reborn) */
    protected static final int LACQUER = 0xFFA0182B, LACQUER_HI = 0xFFC01E35, LACQUER_LO = 0xFF7A1322;
    protected static final int GOLD = 0xFFD9A95E, GOLD_SOFT = 0xFF3A2C14, IVORY = 0xFFF5E9D0, IVORY_2 = 0xFFC2B59A;
    protected static final int WASHI = 0xFFE3D1A8, WASHI_2 = 0xFFD4BF91, WOOD = 0xFF5A3A22, WOOD_DARK = 0xFF2E1C11;
    protected static final int INK = 0xFF2A1A10, INK_SOFT = 0xFF6B5434, FUDA = 0xFFF0E2C2, FUDA_2 = 0xFFE2CFA4;
    protected static final int OK = 0xFF2F7A3A, GLACE = 0xFF2F6A8A, ETEINT = 0xFF9A8A70;
    /** Player ID (N°) : or brun lisible sur le bois et le washi. */
    protected static final int PID = 0xFF8A5A0C;
    /** Indice des champs de saisie : gris doux, nettement plus clair que le texte saisi. */
    protected static final int HINT = 0xFFA8977A;
    /** Lignes de liste : fond clair, fond alterné, survol, filet. */
    protected static final int L_FOND = 0x48FFF8E8, L_ALT = 0x26FFF8E8, L_SURVOL = 0x80FFF8E8, L_FILET = 0x2A462810;

    /* --------------------------------------------------------------- grille */
    /** Gouttière entre colonnes et entre blocs. */
    protected static final int GAP = 6;
    /** Espace entre deux lignes de liste. */
    protected static final int RG = 4;
    /** Hauteurs : petit bouton (filtres, lignes denses), grand bouton (actions), champ de saisie. */
    protected static final int H_BTN = 12, H_BTN_G = 16, H_CHAMP = 14;

    protected static final String[][] G_GARDE = {{"番", "Garde"}, {"alerts", "Alertes"}, {"players", "Joueurs"}, {"journal", "Journal"}};
    protected static final String[][] G_OUTILS = {{"令", "Outils"}, {"annonces", "Annonces"}, {"chat", "Chat staff"}, {"cmds", "Commandes"}};

    protected record Hit(int x, int y, int w, int h, Runnable action, String refus) {
        Hit(int x, int y, int w, int h, Runnable action) { this(x, y, w, h, action, null); }

        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** Fenêtre posée par-dessus la vue (formulaire d'action, confirmation, détail d'une entrée du journal). */
    protected interface Fenetre { void dessiner(GuiGraphicsExtractor ctx); }

    protected final List<Hit> hits = new ArrayList<>();
    protected String tab = "alerts";
    protected String profileUuid;
    protected JsonObject confirm;
    protected Fenetre fenetre;
    protected final Set<Integer> flags = new HashSet<>();
    protected long sealAt;
    protected int scroll, contentH;
    protected long openedAt, viewAt;
    protected int cardIndex;
    protected int ticks;
    protected int mouseX, mouseY;
    protected boolean ouvert;
    /** Vrai pendant le dessin du corps qui défile (les clics n'y comptent que dans la partie visible). */
    protected boolean dansCorps;
    protected EditBox chatBox, noteBox, searchBox, playerSearch;
    protected final List<EditBox> champs = new ArrayList<>();
    protected String tipTexte;
    /** Raison donnée au clic sur le prochain bouton éteint (null = grade insuffisant). */
    protected String refusVerrou;

    protected final GardeFiche fiche = new GardeFiche(this);
    protected final GardeJournal journalVue = new GardeJournal(this);
    protected final GardeAnnonces annoncesVue = new GardeAnnonces(this);
    protected final GardeFormulaire formulaire = new GardeFormulaire(this);

    /* géométrie calculée une fois par taille d'écran (init) */
    protected int gx0, gx1, gy0, gy1;
    private final Map<String, String> fitCache = new HashMap<>();

    public StaffScreen() {
        super(Component.literal("Poste de garde"));
    }

    /* =================================================================== cycle de vie */

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        grille();
        champs.clear();
        chatBox = box(chatBox, "Écrire au staff…", 200);
        noteBox = box(noteBox, "Note pour le casier…", 160);
        searchBox = box(searchBox, "Chercher une commande : ata, zone, météo…", 40);
        searchBox.setResponder(v -> scroll = 0);
        playerSearch = box(playerSearch, "N° (#00042), pseudo ou nom RP", 32);
        playerSearch.setResponder(v -> scroll = 0);
        fiche.init();
        journalVue.init();
        annoncesVue.init();
        formulaire.init();
        if (!ouvert) {
            ouvert = true;
            openedAt = System.currentTimeMillis();
            viewAt = openedAt + 120;
            StaffClient.send("refresh");
            RebornSounds.playReborn("sacoche.open", 1.0f, 0.5f);
        }
    }

    /** Géométrie du cadre et du papier (une fois par taille d'écran ; règle DA §6.5). */
    protected void grille() {
        gx0 = Math.max(Math.round(width * 0.07f) + 6, width / 2 - 320);
        gx1 = width - gx0;
        gy0 = 48;
        gy1 = Math.min(height - 34, gy0 + GardeArt.PAPIER_H);
        fitCache.clear();
    }

    /** Champ de saisie sans bordure (le champ de washi est dessiné à part) ; garde la valeur après un redimensionnement. */
    protected EditBox box(EditBox ancien, String hint, int max) {
        EditBox b = new EditBox(this.font, 0, 0, 100, 12, Component.literal(hint));
        b.setMaxLength(max);
        indice(b, hint);
        b.setBordered(false);
        b.setTextShadow(false);   // saisie sans ombre portée (l'ombre bavait sur le washi)
        b.setTextColor(INK);
        b.setVisible(false);
        if (ancien != null) b.setValue(ancien.getValue());
        this.addRenderableWidget(b);
        champs.add(b);
        return b;
    }

    protected EditBox box(String hint, int max) { return box(null, hint, max); }

    /** Indices des champs, dessinés par {@link #indiceDe} : l'indice vanilla de l'EditBox a une ombre forcée. */
    protected final Map<EditBox, String> indices = new HashMap<>();

    protected void indice(EditBox b, String hint) {
        b.setHint(Component.empty());
        indices.put(b, hint == null ? "" : hint);
    }

    /** Indice gris doux, sans ombre, quand le champ est vide (x, y = coin du champ de washi, w = sa largeur). */
    protected void indiceDe(GuiGraphicsExtractor ctx, EditBox box, int x, int y, int w) {
        String h = indices.get(box);
        if (h == null || h.isEmpty() || !box.getValue().isEmpty() || !box.isVisible()) return;
        ctx.text(font, RebornFont.body(fit(h, w - 10)), x + 4, y + 3, HINT, false);
    }

    /** Police de l'écran (accès des vues du paquet). */
    net.minecraft.client.gui.Font police() { return font; }

    @Override
    public void onClose() {
        RebornSounds.playReborn("sacoche.close", 1.0f, 0.5f);
        super.onClose();
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        StaffClient.clearProfile();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 40 == 0) {
            StaffClient.send("refresh");
            if (profileUuid != null) requestProfile(profileUuid);
        }
        journalVue.tick();
    }

    /* =================================================================== géométrie */

    protected int sx0() { return gx0; }
    protected int sx1() { return gx1; }
    protected int sy0() { return gy0; }
    protected int sy1() { return gy1; }

    /** Haut de l'en-tête de vue (titre / fil d'Ariane). */
    protected int hy() { return sy0() + 10; }
    /** Haut de la zone d'outils fixe (sous l'en-tête). */
    protected int oy() { return hy() + 20; }
    /** Corps qui défile : sous les outils, au-dessus du pied. */
    protected int vy0() { int t = toolsH(); return oy() + t; }
    protected int vy1() { return sy1() - 6 - footerH(); }
    protected int vx0() { return sx0() + navW() + 12; }
    protected int vx1() { return sx1() - 9; }

    /** Hauteur de la zone d'outils fixe de la vue (0 = le corps commence sous l'en-tête). */
    protected int toolsH() {
        return switch (tab) {
            case "players" -> profileUuid == null ? 36 : fiche.hauteurOutils();
            case "journal" -> journalVue.hauteurOutils();
            case "cmds" -> 22;
            default -> 0;
        };
    }

    /** Hauteur du pied fixe (saisie du chat, pagination). */
    protected int footerH() {
        return switch (tab) {
            case "chat" -> 22;
            case "journal" -> 20;
            default -> 0;
        };
    }

    /** Liste des joueurs (pas la fiche d'un joueur). */
    protected boolean playerList() { return "players".equals(tab) && profileUuid == null; }

    /* =================================================================== rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        int center = (Math.round(0x50 * appear) << 24) | 0x08040C, edge = (Math.round(0xA8 * appear) << 24) | 0x08040C;
        ctx.fill(0, 0, width, height, center);
        ctx.fillGradient(0, 0, width, height / 4, edge, 0);
        ctx.fillGradient(0, height - height / 4, width, height, 0, edge);
        DrawHelpers.horizontalGradient(ctx, 0, 0, width / 6, height, edge, 0);
        DrawHelpers.horizontalGradient(ctx, width - width / 6, 0, width / 6, height, 0, edge);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        mouseX = mx;
        mouseY = my;
        hits.clear();
        cardIndex = 0;
        tipTexte = null;
        dansCorps = false;
        cacherChamps();
        drawScreen(ctx, mx, my, delta);
        if (confirm != null || fenetre != null) {
            // une fenêtre est ouverte : seuls ses champs restent visibles
            for (EditBox b : champs) if (!formulaire.possede(b) && b != noteBox) b.setVisible(false);
        }
        super.extractRenderState(ctx, mx, my, delta);   // champs de saisie
        toasts(ctx);
        bulle(ctx);
    }

    protected void cacherChamps() {
        for (EditBox b : champs) b.setVisible(false);
    }

    /** Tout le dessin propre à l'écran (surchargé par l'Infirmerie). */
    protected void drawScreen(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        JsonObject snap = StaffClient.snapshot();

        frame(ctx);
        tabs(ctx, snap);
        plaque(ctx, "");
        boolean pret = paperBottom() >= sy1() - 2;

        ctx.enableScissor(sx0(), sy0(), sx1(), paperBottom());
        entete(ctx, chemin(snap), meta(snap));
        if (snap != null) outils(ctx, snap);
        ctx.disableScissor();

        // corps qui défile
        int bas = Math.min(vy1(), paperBottom());
        ctx.enableScissor(vx0() - 10, vy0(), vx1() + 2, Math.max(vy0(), bas));
        dansCorps = true;
        int y0 = vy0() + 2 - scroll;
        int end;
        if (snap == null) end = vide(ctx, y0, "Connexion au poste de garde…", null);
        else end = switch (tab) {
            case "players" -> profileUuid == null ? players(ctx, snap, y0) : fiche.corps(ctx, snap, y0);
            case "chat" -> chat(ctx, snap, y0);
            case "journal" -> journalVue.corps(ctx, snap, y0);
            case "annonces" -> annoncesVue.corps(ctx, snap, y0);
            case "cmds" -> commands(ctx, snap, y0);
            default -> alerts(ctx, snap, y0);
        };
        dansCorps = false;
        ctx.disableScissor();
        contentH = end - y0 + 4;
        int vue = vy1() - vy0();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - vue)));
        if (pret) barreDefilement(ctx, vy0(), vy1(), contentH);

        if (pret && snap != null) {
            ctx.enableScissor(sx0(), sy0(), sx1(), paperBottom());
            pied(ctx, snap);
            ctx.disableScissor();
        }
        closeButton(ctx);
        if (confirm != null) confirmModal(ctx, snap);
        else if (fenetre != null) {
            hits.clear();
            ctx.nextStratum();
            ctx.fill(0, 0, width, height, 0x99060304);
            fenetre.dessiner(ctx);
        }
    }

    /** Fil d'Ariane de la vue : le premier élément est le titre de l'onglet (cliquable s'il y a une suite). */
    protected String[] chemin(JsonObject snap) {
        return switch (tab) {
            case "players" -> {
                JsonObject p = StaffClient.profile();
                if (profileUuid == null) yield new String[]{"Joueurs"};
                if (p == null || !profileUuid.equals(str(p, "uuid"))) yield new String[]{"Joueurs", "Fiche"};
                yield new String[]{"Joueurs", (str(p, "pid").isEmpty() ? "" : "N° " + str(p, "pid") + "  ·  ") + name(p)};
            }
            case "journal" -> journalVue.chemin();
            case "annonces" -> new String[]{"Annonces et effets"};
            case "chat" -> new String[]{"Chat staff"};
            case "cmds" -> new String[]{"Commandes"};
            default -> new String[]{"Alertes"};
        };
    }

    /** Compteur ou état affiché à droite de l'en-tête. */
    protected String meta(JsonObject snap) {
        if (snap == null) return "";
        return switch (tab) {
            case "players" -> {
                if (profileUuid != null) yield "";
                int n = arr(snap, "players").size(), shown = 0;
                String q = playerSearch.getValue().trim();
                for (JsonElement e : arr(snap, "players")) if (matchesPlayer(e.getAsJsonObject(), q)) shown++;
                yield q.isEmpty() ? n + " en ligne" : shown + " sur " + n + " en ligne";
            }
            case "journal" -> journalVue.meta();
            case "chat" -> num(snap, "staffOnline") + " staff en ligne";
            case "cmds" -> {
                int n = 0, ok = 0, g = grade(snap);
                for (JsonElement e : arr(snap, "commands")) { n++; if (num(e.getAsJsonObject(), "grade") <= g) ok++; }
                yield ok + " sur " + n + " accessibles";
            }
            case "annonces" -> "";
            default -> {
                int n = arr(snap, "alerts").size();
                yield n == 0 ? "aucune en attente" : n + " en attente";
            }
        };
    }

    /** Retour au début de l'onglet (clic sur le premier élément du fil d'Ariane, ou « Retour »). */
    protected void retourRacine() {
        if ("players".equals(tab) && profileUuid != null) {
            profileUuid = null;
            scroll = 0;
            StaffClient.clearProfile();
            RebornSounds.uiClick();
        } else if ("journal".equals(tab)) journalVue.retourRacine();
    }

    /* ------------------------------------------------------------------- en-tête de vue */

    /**
     * En-tête fixe : losange d'or, titre de l'onglet en ArcadePix à l'encre rouge (estompé et cliquable s'il y a une
     * suite), chevron, suite du fil d'Ariane en Inter gras ; compteur à droite ; bouton « Retour » dans une
     * sous-vue ; filet sous toute la largeur.
     */
    protected void entete(GuiGraphicsExtractor ctx, String[] chemin, String meta) {
        int x = vx0(), y = hy(), xr = vx1();
        boolean suite = chemin.length > 1;
        if (suite) {
            int rw = bw("< Retour");
            button(ctx, xr - rw, y, "< Retour", "plain", this::retourRacine, false);
            xr -= rw + GAP;
        } else if (meta != null && !meta.isEmpty()) {
            Component m = RebornFont.body(meta);
            float mw = font.width(m) * DaKit.S8;
            DaKit.texteEchelle(ctx, font, m, xr - mw, y + 3, INK_SOFT, DaKit.S8);
            xr -= Math.round(mw) + GAP;
        }
        GardeArt.losange(ctx, x, y + 3);
        Component racine = DaKit.arcade(chemin[0]);
        int tw = font.width(racine) - 1;
        boolean hovRacine = suite && hover(x + 9, y, tw, 10);
        DaKit.arcX1(ctx, font, racine, x + 9, y + 2, suite ? (hovRacine ? DaKit.ENCRE_ROUGE : INK_SOFT) : DaKit.ENCRE_ROUGE, 0, false);
        if (suite) {
            hits.add(new Hit(x + 9, y, tw, 10, this::retourRacine));
            int cx = x + 9 + tw + 6;
            LotGui l = LotGui.ouvrir(ctx);
            l.fill(cx, y + 2, cx + 1, y + 9, INK_SOFT);
            l.fill(cx + 1, y + 3, cx + 2, y + 8, INK_SOFT);
            l.fill(cx + 2, y + 4, cx + 3, y + 7, INK_SOFT);
            l.fill(cx + 3, y + 5, cx + 4, y + 6, INK_SOFT);
            l.fermer();
            int sx = cx + 10;
            String reste = String.join("  ›  ", java.util.Arrays.copyOfRange(chemin, 1, chemin.length));
            txtG(ctx, reste, sx, y + 1, xr - sx, INK);
        } else {
            int len = Math.max(0, Math.min(240, xr - (x + tw + 24)));
            if (len > 20) GardeArt.trait(ctx, x + tw + 16, y + 3, len);
        }
        ctx.fill(x, y + 15, vx1(), y + 16, 0x30462810);
    }

    /** Zone d'outils fixe de la vue (sous l'en-tête) : recherche, filtres, carte d'identité, en-têtes de colonnes. */
    protected void outils(GuiGraphicsExtractor ctx, JsonObject snap) {
        switch (tab) {
            case "players" -> {
                if (profileUuid == null) {
                    recherche(ctx, playerSearch, oy(), "Chercher");
                    enTetesJoueurs(ctx, oy() + 22);
                } else fiche.outils(ctx, snap, oy());
            }
            case "cmds" -> recherche(ctx, searchBox, oy(), "Chercher");
            case "journal" -> journalVue.outils(ctx, snap, oy());
            default -> { }
        }
    }

    /** Pied fixe de la vue. */
    protected void pied(GuiGraphicsExtractor ctx, JsonObject snap) {
        if ("chat".equals(tab)) {
            int y = sy1() - 20, x = vx0(), wEnv = bwG("Envoyer"), w = vx1() - vx0() - wEnv - GAP;
            ctx.fill(x, y - 4, vx1(), y - 3, 0x30462810);
            placer(ctx, chatBox, x, y + 1, w);
            bouton(ctx, vx1() - wEnv, y, wEnv, "Envoyer", "punish", this::sendChat, false);
        } else if ("journal".equals(tab)) journalVue.pied(ctx, snap, sy1() - 18);
    }

    /** Ligne de recherche : étiquette « CHERCHER » puis champ sur toute la largeur. */
    protected void recherche(GuiGraphicsExtractor ctx, EditBox box, int y, String label) {
        DaKit.Etiq lab = DaKit.etiq(font, label);
        DaKit.etiq(ctx, font, lab, vx0(), y + 7, DaKit.ENCRE_ROUGE);
        int x = vx0() + (int) Math.ceil(lab.w()) + 8;
        placer(ctx, box, x, y, vx1() - x);
    }

    /** Champ de washi (14 de haut) avec son EditBox, en (x, y), largeur w. */
    protected void placer(GuiGraphicsExtractor ctx, EditBox box, int x, int y, int w) {
        GardeArt.champ(ctx, x, y, w);
        // les champs sont dessinés hors du ciseau du corps : on cache ceux que le défilement masque
        box.setVisible(!dansCorps || y >= vy0() && y + H_CHAMP <= Math.min(vy1(), paperBottom()));
        indiceDe(ctx, box, x, y, w);
        box.setX(x + 4);
        box.setY(y + 3);
        box.setWidth(Math.max(10, w - 8));
    }

    /* ------------------------------------------------------------------- décor commun */

    protected static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /**
     * Cadre de bois veiné à ferrures d'or et papier washi qui se déroule (makimono) sous la poutre laquée ; le rouleau
     * (jiku) descend avec le papier. Décor : textures {@link GardeArt} (une coupe chacune).
     */
    protected void frame(GuiGraphicsExtractor ctx) {
        int x0 = sx0(), x1 = sx1(), y0 = sy0(), y1 = sy1();
        int yb = paperBottom();
        if (yb > y0) {
            ctx.enableScissor(x0, y0, x1, yb);
            GardeArt.papier(ctx, x0, y0, x1 - x0, y1 - y0);
            GardeArt.filigrane(ctx, x1 - 74, y1 - 70, infirmerie() ? 1 : 0);
            ctx.disableScissor();
        }
        GardeArt.cadre(ctx, x0, y0, x1 - x0, y1 - y0);
        if (yb < y1 - 1) GardeArt.jiku(ctx, x0, yb, x1 - x0);
    }

    /** Bas du papier pendant le déroulé d'ouverture. */
    protected int paperBottom() {
        float u = ease((System.currentTimeMillis() - openedAt) / 340f);
        return Math.round(sy0() + 5 + (sy1() - sy0() - 5) * u);
    }

    /** L'en-tête montre-t-il l'Infirmerie (医) plutôt que le Poste de garde (番) ? */
    protected boolean infirmerie() { return false; }

    /** En-tête suspendu (texture : plaque laquée, cordons, glands, sceau et titre cuits) ; reflet doré toutes les 7 s. */
    protected void plaque(GuiGraphicsExtractor ctx, String title) {
        int cx = width / 2;
        GardeArt.entete(ctx, cx, infirmerie() ? 1 : 0);
        float ph2 = (System.currentTimeMillis() % 7000L) / 1100f;
        if (ph2 < 1f) {
            int x = cx - 149, y = 9, pw = 298, ph = 26;
            int bx = x + Math.round(ph2 * (pw + 24)) - 12;
            ctx.enableScissor(x, y, x + pw, y + ph);
            LotGui l = LotGui.ouvrir(ctx);
            for (int k = 0; k < 8; k++) l.fill(bx + k, y + k * 3, bx + k + 5, y + 3 + k * 3, 0x22FFF2D0);
            l.fermer();
            ctx.disableScissor();
        }
    }

    /** Largeur de la colonne de navigation (dans le papier, à gauche ; sa bande est cuite dans le washi). */
    protected int navW() { return 74; }

    /**
     * Groupes de la navigation : la première ligne de chaque groupe est {sceau, titre}, les suivantes {id, libellé}.
     * Le Poste de garde seul a deux groupes ; l'écran unifié ajoute l'Infirmerie.
     */
    protected String[][][] navGroups() { return new String[][][]{G_GARDE, G_OUTILS}; }

    /**
     * Colonne de navigation : groupes nommés à sceau vermillon, entrée active en laque rouge (filet d'or), compteurs
     * en pastilles ; en bas, la bascule du mode staff.
     */
    protected void tabs(GuiGraphicsExtractor ctx, JsonObject snap) {
        int x0 = sx0() + 3, w = navW(), y = sy0() + 12;
        int yb = paperBottom();
        if (yb > sy0() + 12) {
            ctx.enableScissor(x0 - 3, sy0() + 5, x0 + w + 4, yb);
            long now = System.currentTimeMillis();
            for (String[][] group : navGroups()) {
                GardeArt.sceauVierge(ctx, x0, y - 1);
                ctx.text(font, Component.literal(group[0][0]), x0 + 2, y + 1, DaKit.CREME_KJ, false);
                DaKit.etiq(ctx, font, DaKit.etiq(font, group[0][1]), x0 + 15, y + 4.5f, DaKit.ENCRE_ROUGE);
                y += 14;
                for (int i = 1; i < group.length; i++) {
                    String id = group[i][0], label = group[i][1];
                    boolean sel = id.equals(tab), hov = !sel && hover(x0, y, w, 13);
                    if (sel || hov) GardeArt.navEntree(ctx, x0, y, w, sel);
                    int count = tabCount(id, snap);
                    int room = w - 10 - (count > 0 ? 14 : 0);
                    DaKit.inter9(ctx, font, RebornFont.body(fit(label, room)), x0 + 6, y + 6.5f, sel ? IVORY : INK, 0);
                    if (count > 0) {
                        Component n = DaKit.arcade(String.valueOf(count));
                        int bw = Math.max(11, (int) Math.ceil(DaKit.largeurFin(font, n)) + 6), bx = x0 + w - bw - 2;
                        boolean urgent = "blesses".equals(id) || "alerts".equals(id);
                        GardeArt.pastille(ctx, bx, y + 1, bw, urgent && (now / 400) % 2 == 0);
                        DaKit.arcFin(ctx, font, n, bx + bw / 2f, y + 4.75f, IVORY, 1, false);
                    }
                    hits.add(new Hit(x0, y, w, 13, () -> switchTab(id)));
                    y += 14;
                }
                y += 8;
            }
            if (snap != null && y + 26 < sy1() - 6) modeStaff(ctx, snap, x0, sy1() - 28, w);
            ctx.disableScissor();
        }
        if (snap != null) gradeBadge(ctx, num(snap, "grade"), str(snap, "gradeName"));
    }

    /** Bascule du mode staff (plaques au-dessus des têtes, invisibilité), en bas de la navigation. */
    protected void modeStaff(GuiGraphicsExtractor ctx, JsonObject snap, int x, int y, int w) {
        boolean on = bool(snap, "modeStaff");
        DaKit.etiq(ctx, font, DaKit.etiq(font, "Mode staff"), x + 2, y + 3, INK_SOFT);
        int bwd = w - 4;
        button(ctx, x + 2, y + 9, bwd, on ? "Actif" : "Inactif", on ? "watch" : "plain", () -> {
            StaffClient.send("modestaff");
            RebornSounds.uiClick();
        }, grade(snap) < 1);
        tip(x + 2, y + 9, bwd, H_BTN, on ? "Mode staff actif : tu vois les plaques au-dessus des têtes et tu es invisible aux joueurs. Clique pour le couper."
                : "Active le mode staff : plaques « N° · nom · état » au-dessus des têtes, invisible aux joueurs.");
    }

    /** Onglets de l'écran (ids de toutes les entrées de navigation). */
    protected String[][] tabList() {
        List<String[]> l = new ArrayList<>();
        for (String[][] g : navGroups()) for (int i = 1; i < g.length; i++) l.add(g[i]);
        return l.toArray(new String[0][]);
    }

    /** Compteur affiché en rouge sur une entrée de navigation (0 = aucun). */
    protected int tabCount(String id, JsonObject snap) {
        return "alerts".equals(id) && snap != null ? arr(snap, "alerts").size() : 0;
    }

    /** Badge du grade staff (laque noire, pastille à la couleur du grade, nom en ArcadePix or). */
    protected void gradeBadge(GuiGraphicsExtractor ctx, int grade, String name) {
        Component g = DaKit.arcade(name);
        int gw = font.width(g) + 22, gx = sx0() - 10, gy = height - 18;
        GardeArt.badgeGrade(ctx, gx, gy, gw);
        ctx.fill(gx + 6, gy + 5, gx + 11, gy + 10, gradeColor(grade) | 0xFF000000);
        ctx.fill(gx + 6, gy + 5, gx + 11, gy + 6, 0x40FFFFFF);
        DaKit.arcX1(ctx, font, g, gx + 15, gy + 4, DaKit.OR_CLAIR, 0, true);
    }

    /** Rectangle aux coins coupés (1 px). */
    protected static void chamfer(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    protected void switchTab(String id) {
        if (!id.equals(tab)) { RebornSounds.uiClick(); viewAt = System.currentTimeMillis(); }
        tab = id;
        scroll = 0;
        if (!"players".equals(id)) profileUuid = null;
        confirm = null;
        fenetre = null;
        if ("journal".equals(id)) journalVue.ouvrir();
    }

    /** Planche « Fermer » aux embouts dorés (texture), libellé ArcadePix. */
    protected void closeButton(GuiGraphicsExtractor ctx) {
        int w = 124, x = width / 2 - w / 2, y = height - 20;
        boolean hov = hover(x + 4, y, 116, 17);
        GardeArt.fermer(ctx, x, y, hov);
        DaKit.arcX1(ctx, font, DaKit.arcade("Fermer"), width / 2f, y + 5, hov ? DaKit.OR_CLAIR : IVORY, 1, true);
        hits.add(new Hit(x + 4, y + 1, 116, 15, this::onClose));
    }

    /** Bandes de washi scellées, au-dessus du pied. */
    protected void toasts(GuiGraphicsExtractor ctx) {
        int y = height - 44;
        long now = System.currentTimeMillis();
        StaffClient.Toast[] ts = StaffClient.toasts();
        if (ts.length == 0) return;
        ctx.nextStratum();
        for (StaffClient.Toast t : ts) {
            Component c = RebornFont.body(t.text());
            int w = Math.min(440, font.width(c) + 26), x = width / 2 - w / 2;
            int dy = Math.round((1f - ease((now - t.at()) / 180f)) * 8);
            GardeArt.toast(ctx, x, y + dy, w);
            DaKit.inter9(ctx, font, RebornFont.body(fit(t.text(), w - 26)), x + 17, y + dy + 8, INK, 0);
            y -= 18;
        }
    }

    /** Barre de défilement fine à droite du corps, quand le contenu dépasse. */
    protected void barreDefilement(GuiGraphicsExtractor ctx, int y0, int y1, int total) {
        int vue = y1 - y0;
        if (total <= vue + 2 || vue < 20) return;
        int x = vx1() + 4;
        int th = Math.max(14, vue * vue / total);
        int ty = y0 + Math.round((vue - th) * (scroll / (float) Math.max(1, total - vue)));
        LotGui l = LotGui.ouvrir(ctx);
        l.fill(x, y0, x + 2, y1, 0x22462810);
        l.fill(x, ty, x + 2, ty + th, 0xFFB08442);
        l.fill(x, ty, x + 1, ty + th, 0xFFD9A95E);
        l.fermer();
    }

    /** Info-bulle (texte complet d'une valeur coupée, aide d'un bouton) : bande d'encre bordée d'or près du curseur. */
    protected void bulle(GuiGraphicsExtractor ctx) {
        if (tipTexte == null || tipTexte.isBlank()) return;
        DaKit.Paragraphe p = DaKit.paragraphe(font, tipTexte, 220, DaKit.S8, 9, 6);
        float w = 0;
        for (var l : p.lignes()) w = Math.max(w, font.width(l) * DaKit.S8);
        int bw = Math.round(w) + 10, bh = p.hauteur() + 7;
        int x = Math.min(width - bw - 4, mouseX + 10), y = mouseY + 14;
        if (y + bh > height - 4) y = mouseY - bh - 6;
        ctx.nextStratum();
        LotGui l = LotGui.ouvrir(ctx);
        l.fill(x, y, x + bw, y + bh, 0xF0150C0A);
        l.fill(x, y, x + bw, y + 1, 0xFF8E6A30);
        l.fill(x, y + bh - 1, x + bw, y + bh, 0xFF8E6A30);
        l.fill(x, y, x + 1, y + bh, 0xFF8E6A30);
        l.fill(x + bw - 1, y, x + bw, y + bh, 0xFF8E6A30);
        l.fermer();
        p.dessiner(ctx, font, x + 5, y + 4, DaKit.CREME_VAL);
    }

    /** Info-bulle si la souris est sur (x, y, w, h) (hors fenêtre ouverte, et dans la partie visible du corps). */
    protected void tip(int x, int y, int w, int h, String texte) {
        if (texte == null || (fenetre != null || confirm != null) && !formulaire.dessine) return;
        if (dansCorps && (mouseY < vy0() || mouseY >= vy1())) return;
        if (hover(x, y, w, h)) tipTexte = texte;
    }

    /* =================================================================== onglets */

    protected int alerts(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        JsonArray al = arr(snap, "alerts");
        if (al.isEmpty()) return calm(ctx, y);
        int g = grade(snap);
        for (JsonElement e : al) {
            JsonObject a = e.getAsJsonObject();
            int dx = slide();
            ctx.pose().pushMatrix();
            ctx.pose().translate(dx, 0);
            int x = vx0() + 6, w = vx1() - vx0() - 6, h = 58;
            String kind = str(a, "kind");
            fuda(ctx, x, y, w, h, kindColor(kind));
            head(ctx, str(a, "uuid"), x + 16, y + 10, 24);
            int tx = x + 48, xr = x + w - 8;
            // à droite : type d'alerte (petites capitales) et sceau du score
            DaKit.Etiq et = DaKit.etiq(font, kindLabel(kind));
            DaKit.etiq(ctx, font, et, xr - et.w(), y + 9, kindColor(kind));
            int rx = xr - Math.round(et.w()) - GAP;
            int score = num(a, "score");
            if (score > 0) rx -= badge(ctx, rx, y + 4, "SCORE " + score, score >= 100) + GAP;
            identite(ctx, a, tx, y + 6, rx - tx, true);
            txt(ctx, str(a, "motif"), tx, y + 18, xr - tx, LACQUER_LO);
            txt7(ctx, join(arr(a, "ctx")), tx, y + 29, xr - tx, INK_SOFT);
            String uuid = str(a, "uuid");
            int bx = tx, by = y + h - 20;
            String id = str(a, "id");
            if ("mort".equals(kind)) {
                bx += bouton(ctx, bx, by, "Valider la RPK", "punish", () -> killVerdict(id, true), g < 2) + GAP;
                bx += bouton(ctx, bx, by, "Refuser", "plain", () -> killVerdict(id, false), g < 2) + GAP;
                bouton(ctx, bx, by, "Y aller", "plain", () -> act("tp", uuid), false);
            } else {
                bx += bouton(ctx, bx, by, "Observer", "watch", () -> act("watch", uuid), g < 2) + GAP;
                if ("aide".equals(kind) || "vol".equals(kind)) bouton(ctx, bx, by, "Y aller", "plain", () -> act("tp", uuid), false);
                else bouton(ctx, bx, by, "Ouvrir la fiche", "punish", () -> openProfile(uuid), false);
            }
            int dw = bwG("Ignorer");
            bouton(ctx, xr - dw, by, dw, "Ignorer", "plain", () -> dismiss(id), false);
            ctx.pose().popMatrix();
            y += h + RG + 2;
        }
        return y;
    }

    protected static String kindLabel(String kind) {
        return switch (kind) {
            case "agression" -> "Agression";
            case "aide" -> "Appel à l'aide";
            case "mort" -> "Demande de RPK";
            case "yeux" -> "Yeux";
            case "vol" -> "Vol";
            case "signalement" -> "Signalement";
            default -> kind.isEmpty() ? "Alerte" : kind;
        };
    }

    /* ------------------------------------------------------------------- joueurs */

    /** Colonnes de la liste des joueurs : {x N°, x identité, x état, largeur état, x bouton} (relatives à la ligne). */
    protected int[] colonnesJoueurs() {
        int w = vx1() - vx0() - 6;
        int pidW = font.width(gras("00000")) + 10;
        int btn = bw("Fiche");
        int etatW = Math.max(90, Math.min(150, w * 28 / 100));
        int xPid = 38, xId = xPid + pidW, xBtn = w - 8 - btn, xEtat = xBtn - GAP - etatW;
        return new int[]{xPid, xId, xEtat, etatW, xBtn};
    }

    protected void enTetesJoueurs(GuiGraphicsExtractor ctx, int y) {
        int[] c = colonnesJoueurs();
        int x = vx0() + 6;
        DaKit.etiq(ctx, font, DaKit.etiq(font, "N°"), x + c[0], y + 5, INK_SOFT);
        DaKit.etiq(ctx, font, DaKit.etiq(font, "Personnage · compte"), x + c[1], y + 5, INK_SOFT);
        DaKit.etiq(ctx, font, DaKit.etiq(font, "État"), x + c[2], y + 5, INK_SOFT);
    }

    protected int players(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        String q = playerSearch.getValue().trim();
        JsonArray all = arr(snap, "players");
        if (all.isEmpty()) return vide(ctx, y, "Personne en ligne pour l'instant.", null);
        int[] c = colonnesJoueurs();
        int shown = 0;
        for (JsonElement e : all) {
            JsonObject p = e.getAsJsonObject();
            if (!matchesPlayer(p, q)) continue;
            shown++;
            int dx = slide();
            ctx.pose().pushMatrix();
            ctx.pose().translate(dx, 0);
            String st = str(p, "state");
            int x = vx0() + 6, w = vx1() - vx0() - 6, h = 28;
            String uuid = str(p, "uuid");
            if (visible(y, h)) hits.add(new Hit(x, y, w, h, () -> openProfile(uuid)));
            fuda(ctx, x, y, w, h, bool(p, "gele") ? GLACE : st.isEmpty() ? OK : LACQUER);
            head(ctx, uuid, x + 16, y + 6, 16);
            String pid = str(p, "pid");
            txtG(ctx, pid.isEmpty() ? "—" : pid, x + c[0], y + 5, c[1] - c[0] - 4, PID);
            txt7(ctx, str(p, "rank"), x + c[0], y + 17, c[1] - c[0] - 4, LACQUER);
            int idW = c[2] - c[1] - GAP;
            txtG(ctx, str(p, "perso").isEmpty() ? "(sans personnage)" : str(p, "perso"), x + c[1], y + 5, idW, INK);
            String compte = str(p, "name") + (str(p, "village").isEmpty() ? "" : "  ·  " + str(p, "village"));
            txt7(ctx, compte, x + c[1], y + 17, idW, INK_SOFT);
            txt8(ctx, st.isEmpty() ? "En forme" : st, x + c[2], y + 6, c[3], st.isEmpty() ? OK : LACQUER_LO);
            txt7(ctx, marqueurs(p), x + c[2], y + 17, c[3], INK_SOFT);
            button(ctx, x + c[4], y + 8, "Fiche", "punish", () -> openProfile(uuid), false);
            ctx.pose().popMatrix();
            y += h + RG;
        }
        if (shown == 0) y = vide(ctx, y, "Aucun joueur ne correspond à « " + q + " ».", "Cherche un N° (#00042), un pseudo ou un nom RP.");
        return y;
    }

    /** Marqueurs courts d'un joueur : gelé, écran noir, mode staff, notes et étiquettes. */
    protected static String marqueurs(JsonObject p) {
        List<String> m = new ArrayList<>();
        if (bool(p, "gele")) m.add("gelé");
        if (bool(p, "noir")) m.add("écran noir");
        if (bool(p, "modeStaff")) m.add("mode staff");
        for (JsonElement e : arr(p, "etiquettes")) m.add(e.getAsString());
        int n = num(p, "notes");
        if (n > 0) m.add(n + " note" + (n > 1 ? "s" : ""));
        return String.join(" · ", m);
    }

    /** « N° 00042 » en or brun devant le nom (Player ID du personnage actif), ou rien. */
    protected static MutableComponent pidPart(JsonObject p) {
        String pid = str(p, "pid");
        return pid.isEmpty() ? Component.empty() : gras("N° " + pid + "  ").withColor(PID & 0xFFFFFF);
    }

    /**
     * Ligne d'identité « N° 00042  Genin  Kazuki Uchiha  Raiden_FR · konoha » tenant dans maxW : le compte puis le
     * grade cèdent la place, le nom RP est coupé en dernier ; texte complet en info-bulle si coupé.
     */
    protected void identite(GuiGraphicsExtractor ctx, JsonObject p, int x, int y, int maxW, boolean village) {
        String pid = str(p, "pid"), rank = str(p, "rank");
        String perso = str(p, "perso").isEmpty() ? str(p, "name") : str(p, "perso");
        String reste = str(p, "perso").isEmpty() ? "" : str(p, "name") + (village && !str(p, "village").isEmpty() ? " · " + str(p, "village") : "");
        String complet = (pid.isEmpty() ? "" : "N° " + pid + "  ") + (rank.isEmpty() ? "" : rank + "  ") + perso + (reste.isEmpty() ? "" : "  " + reste);
        int wPid = pid.isEmpty() ? 0 : font.width(gras("N° " + pid)) + 6;
        int wRank = rank.isEmpty() ? 0 : font.width(gras(rank)) + 6;
        int wPerso = font.width(gras(perso)), wReste = reste.isEmpty() ? 0 : font.width(RebornFont.body(reste)) + 6;
        boolean coupe = false;
        if (wPid + wRank + wPerso > maxW) { wRank = 0; rank = ""; }
        int cx = x;
        if (!pid.isEmpty()) { ctx.text(font, gras("N° " + pid), cx, y, PID, false); cx += wPid; }
        if (!rank.isEmpty()) { ctx.text(font, gras(rank), cx, y, LACQUER, false); cx += wRank; }
        int room = x + maxW - cx;
        if (wPerso > room) { coupe = true; ctx.text(font, gras(fitG(perso, room)), cx, y, INK, false); }
        else {
            ctx.text(font, gras(perso), cx, y, INK, false);
            cx += wPerso + 6;
            int room2 = x + maxW - cx;
            if (!reste.isEmpty()) {
                if (room2 >= 30) {
                    String r = fit(reste, room2);
                    coupe |= !r.equals(reste);
                    ctx.text(font, RebornFont.body(r), cx, y, INK_SOFT, false);
                } else coupe = true;
            }
        }
        if (coupe) tip(x, y - 1, maxW, 10, complet);
    }

    /**
     * Recherche du Poste de garde : {@code #00042} / {@code 42} → Player ID (zéros de tête ignorés) ; sinon pseudo
     * ou nom RP (contient, sans casse).
     */
    protected static boolean matchesPlayer(JsonObject p, String q) {
        if (q == null || q.isBlank()) return true;
        String s = q.trim().toLowerCase();
        String digits = s.startsWith("#") ? s.substring(1) : s;
        String pid = str(p, "pid");
        if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit) && !pid.isEmpty()) {
            String a = digits.replaceFirst("^0+(?=.)", ""), b = pid.replaceFirst("^0+(?=.)", "");
            if (s.startsWith("#") ? a.equals(b) : pid.contains(digits) || a.equals(b)) return true;
            if (s.startsWith("#")) return false;
        }
        return str(p, "name").toLowerCase().contains(s) || str(p, "perso").toLowerCase().contains(s);
    }

    /* ------------------------------------------------------------------- chat staff */

    protected int chat(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        JsonArray lines = arr(snap, "chat");
        if (lines.isEmpty()) return vide(ctx, y, "Aucun message pour l'instant.", "Écris au staff ci-dessous, ou en jeu avec # au début d'un message.");
        int x = vx0(), w = vx1() - vx0();
        int colT = 30, colA = Math.max(70, Math.min(110, w / 5));
        int start = Math.max(0, lines.size() - 60);
        for (int i = start; i < lines.size(); i++) {
            JsonObject m = lines.get(i).getAsJsonObject();
            boolean sys = bool(m, "sys");
            List<String> wrapped = wrap(str(m, "text"), w - colT - colA - 12);
            int h = 5 + wrapped.size() * 10;
            ctx.fill(x, y, x + w, y + h, sys ? 0x14A0182B : (i % 2 == 0 ? L_FOND : L_ALT));
            ctx.fill(x, y, x + 2, y + h, sys ? LACQUER : GOLD);
            txt8(ctx, str(m, "time"), x + 6, y + 3, colT, INK_SOFT);
            String auteur = sys ? "Système" : gradeName(num(m, "grade")) + " · " + str(m, "name");
            txtG(ctx, auteur, x + 6 + colT, y + 2, colA - 6, sys ? LACQUER : gradeColor(num(m, "grade")));
            for (int k = 0; k < wrapped.size(); k++) {
                ctx.text(font, RebornFont.body(wrapped.get(k)), x + 6 + colT + colA, y + 2 + k * 10, INK, false);
            }
            y += h + 2;
        }
        txt7(ctx, "En jeu, le même canal s'ouvre aussi avec # au début d'un message.", vx0(), y + 3, w, INK_SOFT);
        return y + 14;
    }

    /* ------------------------------------------------------------------- commandes */

    protected int commands(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        int g = grade(snap);
        String q = searchBox.getValue().trim().toLowerCase();
        String cat = null;
        int shown = 0;
        int x = vx0(), w = vx1() - vx0();
        int btnW = 66, tagW = 52;
        int cmdW = Math.max(110, Math.min(190, (w - btnW - tagW) * 40 / 100));
        int descX = x + 6 + cmdW + GAP, descW = x + w - btnW - tagW - 2 * GAP - descX;
        for (JsonElement e : arr(snap, "commands")) {
            JsonObject c = e.getAsJsonObject();
            String cmd = str(c, "cmd"), desc = str(c, "desc");
            if (!q.isEmpty() && !(cmd + " " + desc).toLowerCase().contains(q)) continue;
            if (!str(c, "cat").equals(cat)) {
                cat = str(c, "cat");
                y = section(ctx, cat, y + (shown > 0 ? 4 : 0));
            }
            shown++;
            int need = num(c, "grade");
            boolean ok = need <= g;
            ctx.fill(x, y, x + w, y + 18, ok ? L_FOND : 0x1CFFF8E8);
            ctx.fill(x, y + 17, x + w, y + 18, L_FILET);
            ctx.fill(x, y, x + 2, y + 17, ok ? GOLD : 0xFFB8A88A);
            txt(ctx, cmd, x + 6, y + 5, cmdW, ok ? WOOD_DARK : ETEINT);
            txt8(ctx, desc, descX, y + 6, descW, ok ? INK : ETEINT);
            String gl = gradeName(need) + (need < 4 ? "+" : "");
            int gw = Math.round(font.width(RebornFont.body(gl)) * DaKit.S7) + 6;
            int gx = x + w - btnW - GAP - gw;
            DrawHelpers.outlinedRect(ctx, gx, y + 4, gw, 10, 0, gradeColor(need));
            DaKit.texteEchelle(ctx, font, RebornFont.body(gl), gx + 3, y + 6, gradeColor(need), DaKit.S7);
            if (ok) button(ctx, x + w - btnW - 2, y + 3, btnW, "Préparer", "plain", () -> prepare(cmd), false);
            else button(ctx, x + w - btnW - 2, y + 3, btnW, "Verrouillé", "plain", null, true);
            y += 20;
        }
        if (shown == 0) y = vide(ctx, y, q.isEmpty() ? "Aucune commande." : "Aucune commande ne correspond à « " + q + " ».", null);
        return y + 4;
    }

    /* =================================================================== confirmation de sanction */

    protected void confirmModal(GuiGraphicsExtractor ctx, JsonObject snap) {
        JsonObject p = StaffClient.profile();
        if (p == null) { confirm = null; return; }
        hits.clear();   // la fenêtre capte tous les clics
        ctx.nextStratum();
        ctx.fill(0, 0, width, height, 0x99060304);
        int mw = Math.min(340, width - 40), x = width / 2 - mw / 2;
        JsonArray msgs = arr(p, "msgs");
        int shownMsgs = Math.min(msgs.size(), 4);
        int mh = 150 + shownMsgs * 22 + (shownMsgs == 0 ? 12 : 0);
        int y = Math.max(10, height / 2 - mh / 2);
        GardeArt.papierNu(ctx, x, y, mw, mh);
        GardeArt.cadre(ctx, x, y, mw, mh);
        int cx = x + 12, cw = mw - 24, cy = y + 12;
        GardeArt.losange(ctx, cx, cy + 1);
        DaKit.arcX1(ctx, font, DaKit.arcade(fitArc("Sanctionner " + name(p) + " ?", cw - 12)), cx + 9, cy, DaKit.ENCRE_ROUGE, 0, false);
        cy += 16;
        GardeArt.carte(ctx, cx, cy, cw, 36, false);
        String[][] rows = {{"Motif", str(confirm, "name")}, {"Sanction", str(confirm, "next")},
                {"Strike", String.valueOf(num(confirm, "strikes") + 1)}};
        for (int i = 0; i < rows.length; i++) {
            DaKit.texteEchelle(ctx, font, RebornFont.body(rows[i][0]), cx + 8, cy + 6 + i * 10, INK_SOFT, DaKit.S8);
            txtG(ctx, rows[i][1], cx + 64, cy + 4 + i * 10, cw - 72, LACQUER);
        }
        cy += 44;
        String lab = "Preuves marquées : " + flags.size();
        float lw = font.width(RebornFont.body(lab)) * DaKit.S8;
        txt8(ctx, "Messages récents — marque ceux qui servent de preuve", cx, cy, cw - Math.round(lw) - GAP, INK_SOFT);
        DaKit.texteEchelle(ctx, font, RebornFont.body(lab), cx + cw - lw, cy, INK_SOFT, DaKit.S8);
        cy += 11;
        if (shownMsgs == 0) { txt8(ctx, "Aucun message récent.", cx, cy + 2, cw, INK_SOFT); cy += 12; }
        for (int i = 0; i < shownMsgs; i++) {
            JsonObject m = msgs.get(i).getAsJsonObject();
            boolean on = flags.contains(i);
            ctx.fill(cx, cy, cx + cw, cy + 20, L_FOND);
            ctx.fill(cx, cy + 19, cx + cw, cy + 20, L_FILET);
            ctx.fill(cx, cy, cx + 2, cy + 19, on ? LACQUER : GOLD);
            txt7(ctx, str(m, "time"), cx + 6, cy + 2, cw - 30, INK_SOFT);
            txt(ctx, str(m, "text"), cx + 6, cy + 10, cw - 30, INK);
            int fx = cx + cw - 17;
            GardeArt.drapeau(ctx, fx, cy + 3, on);
            final int idx = i;
            hits.add(new Hit(cx, cy, cw, 20, () -> { if (!flags.remove(idx)) flags.add(idx); RebornSounds.uiClick(); }));
            cy += 22;
        }
        cy += 4;
        txt8(ctx, "Note pour le casier (facultatif)", cx, cy, cw, INK_SOFT);
        cy += 10;
        if (sealAt == 0) placer(ctx, noteBox, cx, cy, cw);
        else noteBox.setVisible(false);
        cy += 22;
        if (sealAt == 0) {
            bouton(ctx, cx, cy, "Annuler", "plain", () -> confirm = null, false);
            int ww = bwG("Avertir seulement"), sw = bwG("Apposer le sceau");
            bouton(ctx, cx + cw - sw - GAP - ww, cy, ww, "Avertir seulement", "plain", () -> sendSanction(true), false);
            bouton(ctx, cx + cw - sw, cy, sw, "Apposer le sceau", "punish", () -> sendSanction(false), false);
        } else {
            seal(ctx, x + mw - 74, y + 14);
            if (System.currentTimeMillis() - sealAt > 1100) { confirm = null; sealAt = 0; }
        }
    }

    protected void seal(GuiGraphicsExtractor ctx, int x, int y) {
        float t = Math.min(1f, (System.currentTimeMillis() - sealAt) / 220f);
        float scale = 1.6f - 0.6f * t;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + 28, y + 28);
        ctx.pose().scale(scale, scale);
        GardeArt.sceau(ctx, -32, -32, DaKit.blanc(0.92f * t));
        ctx.pose().popMatrix();
        // éclaboussures d'encre au moment de l'impact
        if (t >= 1f) {
            float fade = Math.max(0f, 1f - (System.currentTimeMillis() - sealAt - 220) / 900f);
            long s = 0x9E3779B97F4A7C15L;
            LotGui l = LotGui.ouvrir(ctx);
            for (int k = 0; k < 14; k++) {
                s = s * 6364136223846793005L + 1442695040888963407L;
                double ang = ((s >>> 40) % 360) * Math.PI / 180.0;
                int r = 30 + (int) ((s >>> 20) % 18), sz = 1 + (int) ((s >>> 50) % 3);
                int px = x + 28 + (int) (Math.cos(ang) * r), py = y + 28 + (int) (Math.sin(ang) * r);
                l.fill(px, py, px + sz, py + sz, (Math.round(200 * fade) << 24) | (LACQUER & 0xFFFFFF));
            }
            l.fermer();
        }
    }

    /** État vide des alertes : grand 静 (le calme) estompé. */
    protected int calm(GuiGraphicsExtractor ctx, int y) {
        int cx = (vx0() + vx1()) / 2;
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - 24, y + 14);
        ctx.pose().scale(6f, 6f);
        ctx.text(font, Component.literal("静"), 0, 0, 0x30462810, false);
        ctx.pose().popMatrix();
        Component c = RebornFont.body("Aucune alerte. Le village est calme.");
        ctx.text(font, c, cx - font.width(c) / 2, y + 72, INK_SOFT, false);
        Component d = RebornFont.body("Les combats hors zone, appels à l'aide et signalements arrivent ici.");
        float dw = font.width(d) * DaKit.S8;
        DaKit.texteEchelle(ctx, font, d, cx - dw / 2f, y + 86, 0xFF8C7A5C, DaKit.S8);
        return y + 100;
    }

    /** État vide explicite : une phrase, et une aide plus petite dessous. */
    protected int vide(GuiGraphicsExtractor ctx, int y, String phrase, String aide) {
        int cx = (vx0() + vx1()) / 2, w = vx1() - vx0() - 16;
        String p = fit(phrase, w);
        Component c = RebornFont.body(p);
        ctx.text(font, c, cx - font.width(c) / 2, y + 18, INK_SOFT, false);
        if (aide != null) {
            String a = fit(aide, Math.round(w / DaKit.S8));
            Component d = RebornFont.body(a);
            float dw = font.width(d) * DaKit.S8;
            DaKit.texteEchelle(ctx, font, d, cx - dw / 2f, y + 31, 0xFF8C7A5C, DaKit.S8);
        }
        return y + 46;
    }

    protected void sendSanction(boolean warnOnly) {
        JsonObject p = StaffClient.profile();
        if (p == null || confirm == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("a", "sanction");
        o.addProperty("t", str(p, "uuid"));
        o.addProperty("o", str(confirm, "id"));
        o.addProperty("warnOnly", warnOnly);
        o.addProperty("note", noteBox.getValue());
        JsonArray ev = new JsonArray();
        JsonArray msgs = arr(p, "msgs");
        for (int i : flags) if (i < msgs.size()) {
            JsonObject m = msgs.get(i).getAsJsonObject();
            ev.add(str(m, "time") + " — " + str(m, "text"));
        }
        o.add("ev", ev);
        StaffClient.send(o);
        if (warnOnly) { confirm = null; RebornSounds.uiClick(); }
        else { sealAt = System.currentTimeMillis(); RebornSounds.confirm(); }
    }

    /* =================================================================== actions */

    protected void act(String op, String uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "act");
        o.addProperty("op", op);
        o.addProperty("t", uuid);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    /** Verdict sur une demande de mort RP (alerte « mort »). */
    protected void killVerdict(String id, boolean ok) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "kill");
        o.addProperty("id", id);
        o.addProperty("v", ok ? "ok" : "no");
        StaffClient.send(o);
        if (ok) RebornSounds.confirm(); else RebornSounds.uiClick();
    }

    protected void dismiss(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "dismiss");
        o.addProperty("id", id);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    protected void openProfile(String uuid) {
        tab = "players";
        profileUuid = uuid;
        scroll = 0;
        fenetre = null;
        fiche.ouvrir();
        requestProfile(uuid);
        RebornSounds.uiClick();
    }

    protected void requestProfile(String uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "profile");
        o.addProperty("t", uuid);
        StaffClient.send(o);
    }

    protected void sendChat() {
        String m = chatBox.getValue().trim();
        if (m.isEmpty()) return;
        JsonObject o = new JsonObject();
        o.addProperty("a", "chat");
        o.addProperty("m", m);
        StaffClient.send(o);
        chatBox.setValue("");
        scroll = Integer.MAX_VALUE / 2;   // descend au dernier message
    }

    protected void prepare(String cmd) {
        Minecraft.getInstance().keyboardHandler.setClipboard(cmd.startsWith("#") ? "#" : cmd);
        StaffClient.toast(cmd + " · copiée, colle-la dans le chat (Ctrl+V)");
        RebornSounds.uiClick();
    }

    /* =================================================================== banc d'essai (StaffDebug) */

    void debugTab(String id) { switchTab(id); }

    void debugProfile(String uuid) { tab = "players"; profileUuid = uuid; scroll = 0; fiche.ouvrir(); requestProfile(uuid); }

    void debugSearch(String q) { playerSearch.setValue(q); scroll = 0; }

    void debugCmdSearch(String q) { searchBox.setValue(q); scroll = 0; }

    void debugScroll(int px) { scroll = px; }

    void debugSeal() { if (confirm != null) sealAt = System.currentTimeMillis() - 260; }

    void debugConfirm(int offenseIndex) {
        JsonObject p = StaffClient.profile();
        if (p == null) return;
        confirm = arr(p, "offenses").get(offenseIndex).getAsJsonObject();
        flags.clear();
        flags.add(0);
        flags.add(1);
    }

    /* =================================================================== entrées */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.in(e.x(), e.y())) {
                    if (h.action() != null) h.action().run();
                    else {
                        StaffClient.toast(h.refus() != null ? h.refus() : "Ton grade ne permet pas cette action.");
                        RebornSounds.deny();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (confirm == null && fenetre == null) scroll = Math.max(0, scroll - (int) Math.round(vAmount * 18));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (confirm != null) { confirm = null; return true; }
            if (fenetre != null) { fenetre = null; return true; }
            if ("players".equals(tab) && profileUuid != null) { retourRacine(); return true; }
            onClose();
            return true;
        }
        if ((k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER)) {
            if (chatBox.isVisible() && chatBox.isFocused()) { sendChat(); return true; }
            if (fenetre == formulaire && formulaire.entree()) return true;
            if (fiche.entree()) return true;
        }
        return super.keyPressed(e);
    }

    /* =================================================================== petits dessins */

    /** Titre de section : losange d'or, titre ArcadePix à l'encre rouge, trait de pinceau vermillon qui s'effile. */
    protected int section(GuiGraphicsExtractor ctx, String title, int y) {
        Component c = DaKit.arcade(fitArc(title, vx1() - vx0() - 20));
        int x = vx0();
        GardeArt.losange(ctx, x, y + 3);
        int tw = DaKit.arcX1(ctx, font, c, x + 9, y + 2, DaKit.ENCRE_ROUGE, 0, false);
        int len = Math.max(10, Math.min(240, (vx1() - x - tw - 18) * 2 / 3));
        GardeArt.trait(ctx, x + tw + 16, y + 3, len);
        return y + 16;
    }

    /** Glissement d'entrée d'une plaquette (décalé par carte), en px. */
    protected int slide() {
        float t = (System.currentTimeMillis() - viewAt - cardIndex++ * 45L) / 260f;
        return Math.round((1f - ease(t)) * 26f);
    }

    /**
     * Plaquette de hinoki (fuda, texture) : coins gauches biseautés, ficelle rouge passée dans le trou, liseré de
     * couleur selon le type. Légèrement soulevée et cerclée d'or au survol.
     */
    protected void fuda(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int accent) {
        boolean hov = confirm == null && fenetre == null && hover(x, y, w, h) && (!dansCorps || mouseY >= vy0() && mouseY < vy1());
        int lift = hov ? 1 : 0;
        y -= lift;
        GardeArt.fuda(ctx, x, y, w, h, hov);
        GardeArt.ficelle(ctx, x + 3, y + h / 2);
        ctx.fill(x + 9, y + 3, x + 11, y + h - 3, accent);
        ctx.fill(x + 9, y + 3, x + 10, y + h - 3, 0x30FFFFFF);
    }

    /** Score en sceau laqué (rouge si grave, ambre sinon), calé à droite en {@code right} ; renvoie sa largeur. */
    protected int badge(GuiGraphicsExtractor ctx, int right, int y, String label, boolean strong) {
        Component c = DaKit.arcade(label);
        int w = (int) Math.ceil(DaKit.largeurFin(font, c)) + 12;
        GardeArt.sceauScore(ctx, right - w, y, w, strong);
        DaKit.arcFin(ctx, font, c, right - w / 2f, y + 4.75f, IVORY, 1, true);
        return w;
    }

    /** Largeur d'un petit bouton pour ce libellé. */
    protected int bw(String label) {
        return (int) Math.ceil(DaKit.largeurFin(font, DaKit.arcade(label))) + 14;
    }

    /** Largeur d'un grand bouton pour ce libellé (Inter gras 8). */
    protected int bwG(String label) {
        return Math.max(52, (int) Math.ceil(font.width(gras(label)) * DaKit.S8) + 18);
    }

    /** Petit bouton-plaque laqué (12 de haut) : noir, rouge (« punish »), vert (« watch »), éteint ; renvoie sa largeur. */
    protected int button(GuiGraphicsExtractor ctx, int x, int y, String label, String style, Runnable action, boolean locked) {
        return button(ctx, x, y, bw(label), label, style, action, locked);
    }

    /** Petit bouton de largeur imposée (libellé centré). */
    protected int button(GuiGraphicsExtractor ctx, int x, int y, int w, String label, String style, Runnable action, boolean locked) {
        int h = H_BTN;
        boolean hov = !locked && hover(x, y, w, h) && cliquable(y, h);
        GardeArt.bouton(ctx, x, y, w, ligne(style, locked), hov);
        int color = locked ? ETEINT : hov ? DaKit.OR_CLAIR : IVORY;
        String lab = fitArcFin(label, w - (locked ? 12 : 6));
        DaKit.arcFin(ctx, font, DaKit.arcade(lab), x + w / 2f - (locked ? 2 : 0), y + 4.25f, color, 1, !locked);
        if (locked) ctx.fill(x + w - 6, y + 4, x + w - 4, y + 7, ETEINT);   // petit cadenas
        if (cliquable(y, h)) hits.add(new Hit(x, y, w, h, locked ? null : () -> { if (action != null) action.run(); }, refusVerrou));
        return w;
    }

    /** Grand bouton d'action (16 de haut, libellé Inter gras 8) ; renvoie sa largeur. */
    protected int bouton(GuiGraphicsExtractor ctx, int x, int y, String label, String style, Runnable action, boolean locked) {
        return bouton(ctx, x, y, bwG(label), label, style, action, locked);
    }

    protected int bouton(GuiGraphicsExtractor ctx, int x, int y, int w, String label, String style, Runnable action, boolean locked) {
        int h = H_BTN_G;
        boolean hov = !locked && hover(x, y, w, h) && cliquable(y, h);
        GardeArt.boutonGrand(ctx, x, y, w, ligne(style, locked), hov);
        int color = locked ? ETEINT : hov ? DaKit.OR_CLAIR : IVORY;
        String lab = fitG(label, Math.round((w - (locked ? 16 : 10)) / DaKit.S8));
        Component c = gras(lab);
        float tw = font.width(c) * DaKit.S8;
        DaKit.texteEchelle(ctx, font, c, x + w / 2f - tw / 2f - (locked ? 2 : 0), y + 4.5f, color, DaKit.S8);
        if (locked) ctx.fill(x + w - 7, y + 6, x + w - 5, y + 10, ETEINT);
        if (cliquable(y, h)) hits.add(new Hit(x, y, w, h, locked ? null : () -> { if (action != null) action.run(); }, refusVerrou));
        return w;
    }

    private static int ligne(String style, boolean locked) {
        return locked ? GardeArt.B_INACTIF : switch (style) {
            case "punish" -> GardeArt.B_ROUGE;
            case "watch" -> GardeArt.B_VERT;
            default -> GardeArt.B_NOIR;
        };
    }

    /** Un bouton est-il cliquable à cette hauteur (dans le corps : seulement dans sa partie visible) ? */
    protected boolean cliquable(int y, int h) {
        return !dansCorps || visible(y, h);
    }

    /** Nom ou valeur en Inter gras (comme la fiche). */
    protected static MutableComponent gras(String s) { return DaKit.interGras(s); }

    protected void head(GuiGraphicsExtractor ctx, String uuid, int x, int y, int size) {
        ctx.fill(x - 2, y - 2, x + size + 2, y + size + 2, WOOD_DARK);
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, GOLD);
        Identifier skin = null;
        try {
            var conn = Minecraft.getInstance().getConnection();
            PlayerInfo info = conn != null ? conn.getPlayerInfo(UUID.fromString(uuid)) : null;
            if (info != null) skin = info.getSkin().body().texturePath();
        } catch (RuntimeException ignored) { }
        if (skin == null) { ctx.fill(x, y, x + size, y + size, 0xFF3A2C14); return; }
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, size, size, 8, 8, 64, 64);
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, size, size, 8, 8, 64, 64);
    }

    protected int text(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
        ctx.text(font, RebornFont.body(s), x, y, color, false);
        return y + 11;
    }

    protected void small(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
        DaKit.texteEchelle(ctx, font, RebornFont.body(s), x, y, color, 0.75f);
    }

    /* ------------------------------------------------------------------- texte coupé + info-bulle */

    /** Inter 9 tenant dans maxW (coupé par « … », info-bulle du texte complet) ; renvoie la largeur dessinée. */
    protected int txt(GuiGraphicsExtractor ctx, String s, int x, int y, int maxW, int col) {
        if (s == null || s.isEmpty() || maxW <= 4) return 0;
        String f = fit(s, maxW);
        ctx.text(font, RebornFont.body(f), x, y, col, false);
        if (!f.equals(s)) tip(x, y - 1, maxW, 10, s);
        return font.width(RebornFont.body(f));
    }

    /** Inter gras 9 tenant dans maxW. */
    protected int txtG(GuiGraphicsExtractor ctx, String s, int x, int y, int maxW, int col) {
        if (s == null || s.isEmpty() || maxW <= 4) return 0;
        String f = fitG(s, maxW);
        ctx.text(font, gras(f), x, y, col, false);
        if (!f.equals(s)) tip(x, y - 1, maxW, 10, s);
        return font.width(gras(f));
    }

    /** Inter 8 tenant dans maxW (unités GUI). */
    protected int txt8(GuiGraphicsExtractor ctx, String s, int x, int y, int maxW, int col) {
        return txtEch(ctx, s, x, y, maxW, col, DaKit.S8);
    }

    /** Inter 7 tenant dans maxW (unités GUI). */
    protected int txt7(GuiGraphicsExtractor ctx, String s, int x, int y, int maxW, int col) {
        return txtEch(ctx, s, x, y, maxW, col, DaKit.S7);
    }

    private int txtEch(GuiGraphicsExtractor ctx, String s, int x, int y, int maxW, int col, float k) {
        if (s == null || s.isEmpty() || maxW <= 4) return 0;
        String f = fit(s, (int) Math.floor(maxW / k));
        DaKit.texteEchelle(ctx, font, RebornFont.body(f), x, y, col, k);
        if (!f.equals(s)) tip(x, y - 1, maxW, Math.round(10 * k), s);
        return Math.round(font.width(RebornFont.body(f)) * k);
    }

    /** Coupe s (Inter 9) par « … » pour tenir dans maxW (mémorisé). */
    protected String fit(String s, int maxW) { return coupe(s, maxW, 0); }

    /** Coupe s (Inter gras 9). */
    protected String fitG(String s, int maxW) { return coupe(s, maxW, 1); }

    /** Coupe s (ArcadePix ×1). */
    protected String fitArc(String s, int maxW) { return coupe(s, maxW, 2); }

    /** Coupe s (ArcadePix ×0,5 des petits boutons). */
    protected String fitArcFin(String s, int maxW) { return coupe(s, maxW, 3); }

    private String coupe(String s, int maxW, int police) {
        if (s == null) return "";
        String cle = police + ":" + maxW + ":" + s;
        String r = fitCache.get(cle);
        if (r != null) return r;
        if (fitCache.size() > 4000) fitCache.clear();
        r = s;
        if (largeur(r, police) > maxW) {
            String t = s;
            while (t.length() > 1 && largeur(t + "…", police) > maxW) t = t.substring(0, t.length() - 1);
            r = t.stripTrailing() + "…";
        }
        fitCache.put(cle, r);
        return r;
    }

    private float largeur(String s, int police) {
        return switch (police) {
            case 1 -> font.width(gras(s));
            case 2 -> font.width(DaKit.arcade(s)) - 1;
            case 3 -> DaKit.largeurFin(font, DaKit.arcade(s));
            default -> font.width(RebornFont.body(s));
        };
    }

    protected boolean hover(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Le bouton est-il dans la zone visible du corps (pas masqué par le défilement) ? */
    protected boolean visible(int y, int h) {
        return y >= vy0() - 1 && y + h <= vy1() + 1;
    }

    protected List<String> wrap(String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(RebornFont.body(next)) > maxW && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        if (out.isEmpty()) out.add("");
        for (int i = 0; i < out.size(); i++) out.set(i, fit(out.get(i), maxW));   // un mot plus long que la ligne
        return out;
    }

    /**
     * Disposition en flux (puces de filtres) : pour des largeurs données, renvoie {x relatif, rangée} de chaque
     * élément dans la largeur maxW.
     */
    protected static int[][] flux(int[] largeurs, int maxW, int gap) {
        int[][] out = new int[largeurs.length][2];
        int x = 0, r = 0;
        for (int i = 0; i < largeurs.length; i++) {
            if (x > 0 && x + largeurs[i] > maxW) { x = 0; r++; }
            out[i][0] = x;
            out[i][1] = r;
            x += largeurs[i] + gap;
        }
        return out;
    }

    /* =================================================================== JSON + grades */

    protected static String str(JsonObject o, String k) {
        try { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
        catch (RuntimeException e) { return ""; }
    }

    protected static int num(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsInt() : 0; } catch (RuntimeException e) { return 0; }
    }

    protected static long lng(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsLong() : 0L; } catch (RuntimeException e) { return 0L; }
    }

    protected static boolean bool(JsonObject o, String k) {
        try { return o != null && o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }

    protected static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    protected static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
    }

    protected static String join(JsonArray a) {
        List<String> parts = new ArrayList<>();
        for (JsonElement e : a) parts.add(e.getAsString());
        return String.join(" · ", parts);
    }

    protected static String name(JsonObject p) {
        return str(p, "perso").isEmpty() ? str(p, "name") : str(p, "perso");
    }

    protected static int grade(JsonObject snap) { return num(snap, "grade"); }

    protected static String gradeName(int g) {
        return switch (g) { case 1 -> "Helper"; case 2 -> "Modo"; case 3 -> "Admin"; case 4 -> "Owner"; default -> "Joueur"; };
    }

    protected static String gradeShort(int g) { return "[" + gradeName(g) + "]"; }

    protected static int gradeColor(int g) {
        return switch (g) { case 1 -> 0xFF2F6A7A; case 2 -> 0xFF8A5A0C; case 3 -> LACQUER; case 4 -> 0xFF3B2A12; default -> INK_SOFT; };
    }

    protected static int kindColor(String kind) {
        return switch (kind) {
            case "agression" -> LACQUER;
            case "aide" -> 0xFFD97706;
            case "mort" -> 0xFF5A0A12;
            case "yeux" -> 0xFF7A1322;
            case "vol" -> 0xFF8A5A0C;
            case "signalement" -> 0xFF6B4FA0;
            case "soin" -> OK;
            case "ko" -> 0xFF6B4FA0;
            case "sanction" -> LACQUER;
            case "tp" -> 0xFF2F6A7A;
            case "monde" -> 0xFF8A5A0C;
            default -> INK_SOFT;
        };
    }
}
