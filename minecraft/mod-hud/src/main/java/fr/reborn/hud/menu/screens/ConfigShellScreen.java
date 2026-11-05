package fr.reborn.hud.menu.screens;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.settings.AccountTab;
import fr.reborn.hud.menu.settings.AudioTab;
import fr.reborn.hud.menu.settings.ControlsTab;
import fr.reborn.hud.menu.settings.GraphismesTab;
import fr.reborn.hud.menu.settings.InterfaceTab;
import fr.reborn.hud.menu.settings.OngletPages;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.settings.ReglagesArt;
import fr.reborn.hud.menu.settings.SectionedTab;
import fr.reborn.hud.menu.settings.SettingsTab;
import fr.reborn.hud.menu.settings.SliderWidget;
import fr.reborn.hud.menu.settings.SousListe;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Écran Paramètres Reborn, <b>gabarit o2</b> du lot 3 « Échap et touches » (maquette validée {@code o2_parametres.png},
 * variante o2-a) : le format de Ketto (onglets en haut, sous-liste à gauche, lignes libellé + aide + réglage,
 * « ↺ Réinitialiser la section », pied « [Échap] RETOUR ») avec l'allure de nos Paramètres d'avant (DA §4.1, §6) :
 * pièce de laque et byōbu à feuilles d'or, en-tête suspendu « PARAMÈTRES », <b>cinq onglets pendus</b> à picto
 * (COMMANDES, INTERFACE, AUDIO, GRAPHISMES, COMPTE ; libellés ArcadePix ×1, pas de kanji), deux planches laquées : la
 * sous-liste à gauche (166 GUI ; choisie = plaque laquée rouge, survol = plaque sombre) et les lignes à droite
 * (604 GUI au plus ; titre ArcadePix or, filet à losanges, lignes de 34 GUI, {@link SectionedTab}). Plus de plaque à
 * croix : on sort par le pied, comme sur l'Échap (Échap ou un clic revient d'un cran). Une seule chose survolée à la
 * fois.
 *
 * <p>Contenu : chaque onglet est un {@link SettingsTab} qui implémente {@link SousListe} ({@link OngletPages} pour
 * Interface, Audio, Graphismes, Compte). COMMANDES instancie {@code ControlsTab(Screen)} : s'il implémente
 * {@link SousListe}, sa sous-liste s'affiche ; sinon il tient en une sous-catégorie « Touches ». Chaque catégorie ne
 * propose que des réglages <b>réellement câblés</b> ; les écrans d'options de Minecraft sont redirigés ici
 * ({@code settings/RedirectionOptions}), sauf par la porte « Compte › Avancé ». Textures :
 * {@code textures/gui/reglages/*} ({@code tools/gen_reglages_textures.py}, cf. {@link ReglagesArt}), inchangées.
 */
public class ConfigShellScreen extends Screen implements fr.reborn.hud.ui.EcranOpaque {

    /** Les cinq onglets, dans l'ordre de la barre (picto : rang dans {@code onglets_pictos.png}). */
    public enum Onglet {
        COMMANDES("COMMANDES", 2), INTERFACE("INTERFACE", 4), AUDIO("AUDIO", 1), GRAPHISMES("GRAPHISMES", 0),
        COMPTE("COMPTE", 6);

        final String label;
        final int picto;

        Onglet(String label, int picto) {
            this.label = label;
            this.picto = picto;
        }
    }

    private static final Onglet[] ONGLETS = Onglet.values();

    // ─── Cotes du gabarit o2 (unités GUI, écran logique 960 × 540 en 1080p) ───
    private static final int TAB_ATTACHE = 36;     // haut des cellules d'onglets (fil d'or)
    private static final int TAB_SPAN = 520;
    private static final int SUB_W = 166, GAP = 10, MAIN_W_MAX = 604;
    private static final int PY = 88, PIED_BAS = 18, PLANCHE_BAS = 36, PLANCHE_H_MAX = 648;
    private static final int SOUS_PAS = 26;
    private static final int SCROLLBAR_MIN_THUMB = 24;
    private static final int REPOS = 0xFFA88C5E, VERMILLON = 0xFFE5483C;
    private static final long CONFIRMATION_MS = 3000;

    /** Dernier onglet et sous-catégorie ouverts (session) : on rouvre les Paramètres là où on les a laissés. */
    private static Onglet dernier = Onglet.COMMANDES;
    private static final int[] DERNIERE_SOUS = new int[ONGLETS.length];

    private final Screen parent;
    private final SettingsTab[] tabs = new SettingsTab[ONGLETS.length];
    private final SousListe[] listes = new SousListe[ONGLETS.length];
    private int activeIdx;

    private int scrollY = 0;
    private List<AbstractWidget> contentWidgets = List.of();
    private int[] baseWidgetY = new int[0];

    private boolean draggingScrollbar = false;
    private int scrollbarGrabDy = 0;
    /** Fin de la fenêtre de confirmation de « Réinitialiser la section » (0 : pas de confirmation en cours). */
    private long confirmeJusqua;

    public ConfigShellScreen(Screen parent) {
        this(parent, (Onglet) null, -1);
    }

    /**
     * Compatibilité (banc, appels d'avant le lot 3) : rang des huit onglets d'avant (0 vidéo, 1 audio, 2 contrôles,
     * 3 caméra, 4 interface, 5 Discord, 6 compte, 7 Minecraft), ramené à l'onglet et à la sous-catégorie qui les
     * remplacent.
     */
    public ConfigShellScreen(Screen parent, int ancienIdx) {
        this(parent, ancien(ancienIdx), ancienSous(ancienIdx));
    }

    /** Ouvre sur un onglet (null : le dernier ouvert) et une sous-catégorie (-1 : la dernière ouverte). */
    public ConfigShellScreen(Screen parent, Onglet onglet, int sous) {
        super(Component.literal("Paramètres Reborn"));
        this.parent = parent;
        RebornPrefs.INSTANCE.ensureLoaded();
        for (Onglet o : ONGLETS) {
            SettingsTab t = switch (o) {
                case COMMANDES -> commandes();
                case INTERFACE -> InterfaceTab.onglet(this);
                case AUDIO -> AudioTab.onglet();
                case GRAPHISMES -> GraphismesTab.onglet();
                case COMPTE -> AccountTab.onglet(this);
            };
            tabs[o.ordinal()] = t;
            listes[o.ordinal()] = (SousListe) t;
            SousListe l = listes[o.ordinal()];
            if (l.nombreSous() > 0) l.choisirSous(Math.min(DERNIERE_SOUS[o.ordinal()], l.nombreSous() - 1));
        }
        Onglet o = onglet != null ? onglet : dernier;
        int[] banc = banc();
        if (banc != null) {
            o = ONGLETS[Math.max(0, Math.min(ONGLETS.length - 1, banc[0]))];
            sous = banc[1];
        }
        this.activeIdx = o.ordinal();
        if (sous >= 0 && sous < listes[activeIdx].nombreSous()) listes[activeIdx].choisirSous(sous);
        retenir();
    }

    /** COMMANDES : l'onglet des touches ({@code ControlsTab}), avec sa sous-liste s'il en a une. */
    private SettingsTab commandes() {
        SettingsTab t = new ControlsTab(this);
        if (t instanceof SousListe) return t;
        return new OngletPages(new OngletPages.Page("Touches", t, null));
    }

    private static Onglet ancien(int i) {
        return switch (i) {
            case 0 -> Onglet.GRAPHISMES;
            case 1 -> Onglet.AUDIO;
            case 2 -> Onglet.COMMANDES;
            case 3, 4 -> Onglet.INTERFACE;
            case 5, 6, 7 -> Onglet.COMPTE;
            default -> null;
        };
    }

    private static int ancienSous(int i) {
        return switch (i) {
            case 0, 1, 4, 6 -> 0;
            case 3 -> InterfaceTab.CAMERA;
            case 5 -> AccountTab.DISCORD;
            case 7 -> AccountTab.AVANCE;
            default -> -1;
        };
    }

    /**
     * Banc de captures : onglet imposé ({@code REBORN_REGLAGES_ONGLET=<onglet>[.<sous>]}, rangs du gabarit o2 :
     * 0 COMMANDES, 1 INTERFACE, 2 AUDIO, 3 GRAPHISMES, 4 COMPTE ; client de dev seulement).
     */
    private static int[] banc() {
        try {
            String v = System.getenv("REBORN_REGLAGES_ONGLET");
            if (v == null || v.isBlank()) return null;
            String[] p = v.trim().split("\\.");
            return new int[] {Integer.parseInt(p[0]), p.length > 1 ? Integer.parseInt(p[1]) : -1};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Écran d'où viennent les Paramètres (là où l'on revient). */
    public Screen parent() {
        return parent;
    }

    private SettingsTab activeTab() {
        return tabs[activeIdx];
    }

    private SousListe activeListe() {
        return listes[activeIdx];
    }

    private void retenir() {
        dernier = ONGLETS[activeIdx];
        DERNIERE_SOUS[activeIdx] = activeListe().sousChoisie();
    }

    // ─── Géométrie (maquette o2 : geo()) ─────────────────
    private int mainW() { return Math.max(200, Math.min(MAIN_W_MAX, this.width - 24 - SUB_W - GAP)); }
    private int subX() { return (this.width - (SUB_W + GAP + mainW())) / 2; }
    private int mainX() { return subX() + SUB_W + GAP; }
    private int plancheBas() { return PY + Math.min(PLANCHE_H_MAX, this.height - PLANCHE_BAS - PY); }
    private int piedY() { return this.height - PIED_BAS; }
    private int contentX() { return mainX() + 22; }
    /** Bord droit des réglages (8 GUI laissés à la barre de défilement). */
    private int reglagesDroite() { return mainX() + mainW() - 22 - 8; }
    private int contentW() { return reglagesDroite() - contentX(); }
    private int titreY() { return PY + 14; }
    private int viewportTop() { return PY + 32; }
    private int viewportBottom() { return plancheBas() - 12; }
    private int viewportH() { return Math.max(0, viewportBottom() - viewportTop()); }
    private int contentTopBase() { return PY + 36; }
    private int contentBottom() { return contentTopBase() + activeTab().height() + 8; }
    private int maxScroll() { return Math.max(0, contentBottom() - viewportBottom()); }
    private boolean hasScroll() { return maxScroll() > 0; }

    /** Centre de l'onglet i : cinq onglets répartis sous l'en-tête de 520. */
    private int tabX(int i) {
        int span = Math.min(TAB_SPAN, this.width - 40);
        float pitch = span / (float) ONGLETS.length;
        return Math.round(this.width / 2f - span / 2f + pitch * (i + 0.5f));
    }

    private int tabAt(double mx, double my) {
        if (my < TAB_ATTACHE + 4 || my > TAB_ATTACHE + 50) return -1;
        int span = Math.min(TAB_SPAN, this.width - 40);
        float pitch = span / (float) ONGLETS.length;
        for (int i = 0; i < ONGLETS.length; i++) {
            if (Math.abs(mx - tabX(i)) <= pitch / 2f) return i;
        }
        return -1;
    }

    /** Sous-catégorie sous la souris (rang dans la liste complète), -1 sinon. */
    private int sousAt(double mx, double my) {
        int sx = subX() + 10, sw = SUB_W - 20;
        if (mx < sx || mx >= sx + sw) return -1;
        SousListe l = activeListe();
        int rang = 0;
        for (int i = 0; i < l.nombreSous(); i++) {
            if (!l.sousVisible(i)) continue;
            int y = PY + 14 + SOUS_PAS * rang++;
            if (my >= y && my < y + 20) return i;
        }
        return -1;
    }

    /** Zone cliquable de « ↺ Réinitialiser la section » (largeur du libellé + icône). */
    private boolean overReinit(double mx, double my) {
        if (!activeListe().reinitialisable()) return false;
        int xr = reglagesDroite();
        float wl = DaKit.largeur7(this.font, DaKit.inter(libelleReinit()));
        int y = titreY();
        return mx >= xr - wl - 14 && mx < xr + 2 && my >= y && my < y + 14;
    }

    private String libelleReinit() {
        return System.currentTimeMillis() < confirmeJusqua ? "Confirmer : réinitialiser ?" : "Réinitialiser la section";
    }

    private int piedLargeur() {
        return this.font.width(DaKit.inter("Échap")) + 14;
    }

    private boolean overPied(double mx, double my) {
        int x = subX();
        int w = piedLargeur() + 7 + this.font.width(DaKit.arcade("RETOUR"));
        return mx >= x && mx < x + w && my >= piedY() - 9 && my < piedY() + 9;
    }

    // ─── Cycle de vie ────────────────────────────────────
    @Override
    protected void init() {
        rebuildContent();
    }

    @Override
    public void tick() {
        super.tick();
        if (SectionedTab.consommerRedimension()) {
            // « Taille des menus Reborn » ou échelle du HUD : resizeGui recalcule l'échelle (MenuScale) puis init()
            Minecraft.getInstance().resizeGui();
        }
        // pas de page refaite pendant un glissé : le curseur tenu serait remplacé sous la souris (attend le relâché)
        if (!SliderWidget.glisse() && SectionedTab.consommerRafraichir()) {
            int s = scrollY;
            rebuildContent();
            scrollY = s;
            clampScroll();
            applyScroll();
        }
    }

    private void rebuildContent() {
        for (AbstractWidget w : contentWidgets) {
            this.removeWidget(w);
        }
        activeTab().layout(contentX(), contentTopBase(), contentW());
        contentWidgets = activeTab().widgets();
        baseWidgetY = new int[contentWidgets.size()];
        for (int i = 0; i < contentWidgets.size(); i++) {
            AbstractWidget w = contentWidgets.get(i);
            baseWidgetY[i] = w.getY();
            this.addWidget(w);
        }
        clampScroll();
        applyScroll();
    }

    private void selectCategory(int idx) {
        if (idx == activeIdx) return;
        activeIdx = idx;
        scrollY = 0;
        confirmeJusqua = 0;
        retenir();
        RebornSounds.playReborn("stats.tab", 1.0f, 0.5f);
        rebuildContent();
    }

    private void selectSous(int i) {
        SousListe l = activeListe();
        if (i == l.sousChoisie()) return;
        l.choisirSous(i);
        scrollY = 0;
        confirmeJusqua = 0;
        retenir();
        RebornSounds.playReborn("stats.tab", 1.2f, 0.4f);
        rebuildContent();
    }

    private void reinitialiser() {
        long now = System.currentTimeMillis();
        if (now >= confirmeJusqua) {
            confirmeJusqua = now + CONFIRMATION_MS;
            RebornSounds.uiClick();
            return;
        }
        confirmeJusqua = 0;
        activeListe().reinitialiserSous();
        RebornSounds.confirm();
        rebuildContent();
    }

    private void applyScroll() {
        int top = viewportTop();
        int bottom = viewportBottom();
        for (int i = 0; i < contentWidgets.size(); i++) {
            AbstractWidget w = contentWidgets.get(i);
            int y = baseWidgetY[i] - scrollY;
            w.setY(y);
            boolean outside = (y + w.getHeight() < top) || (y > bottom);
            w.visible = !outside;
            w.active = !outside;
        }
    }

    private void clampScroll() {
        scrollY = Math.max(0, Math.min(scrollY, maxScroll()));
    }

    // ─── Rendu ───────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ReglagesArt.fond(ctx, this.width, this.height);
        Font f = this.font;
        int cx = this.width / 2;
        int bas = plancheBas();

        // Deux planches laquées : sous-liste à gauche, lignes à droite.
        ReglagesArt.planche(ctx, subX(), PY, SUB_W, bas - PY);
        ReglagesArt.planche(ctx, mainX(), PY, mainW(), bas - PY);

        // Survol unique : onglet, sous-catégorie, « Réinitialiser » ou pied (les lignes ont le leur, SectionedTab).
        int hovTab = tabAt(mouseX, mouseY);
        int hovSous = hovTab < 0 ? sousAt(mouseX, mouseY) : -1;
        boolean hovReinit = hovTab < 0 && hovSous < 0 && overReinit(mouseX, mouseY);
        boolean hovPied = hovTab < 0 && hovSous < 0 && !hovReinit && overPied(mouseX, mouseY);

        // Onglets pendus (sous l'en-tête) : plaquette à picto, libellé ArcadePix ×1 dessous.
        for (int i = 0; i < ONGLETS.length; i++) {
            int tx = tabX(i);
            boolean active = i == activeIdx, hot = i == hovTab && !active;
            int etat = active ? 2 : hot ? 1 : 0;
            ReglagesArt.onglet(ctx, tx, TAB_ATTACHE, etat);
            int ty = TAB_ATTACHE + 8 + (active ? 2 : 0);
            ReglagesArt.ongletPicto(ctx, ONGLETS[i].picto, tx, ty + 3, etat);
            ReglagesArt.titre(ctx, f, ONGLETS[i].label, tx, ty + 27, active ? DaKit.CREME : hot ? DaKit.CREME_VAL : REPOS,
                1, true);
        }
        ReglagesArt.entete(ctx, cx);
        ReglagesArt.titre(ctx, f, "Paramètres", cx, 19, DaKit.CREME, 1, true);

        // Sous-liste : plaque laquée rouge = choisie, plaque sombre = survol.
        SousListe l = activeListe();
        int sx = subX() + 10, sw = SUB_W - 20, rang = 0;
        for (int i = 0; i < l.nombreSous(); i++) {
            if (!l.sousVisible(i)) continue;
            int y = PY + 14 + SOUS_PAS * rang++;
            boolean sel = i == l.sousChoisie(), hot = i == hovSous && !sel;
            if (sel) ReglagesArt.bouton(ctx, sx, y, sw, 3);
            else if (hot) ReglagesArt.bouton(ctx, sx, y, sw, 1);
            String nom = DaKit.ellipse(f, l.nomSous(i), sw - 16);
            DaKit.inter9(ctx, f, DaKit.inter(nom), sx + 10, y + 10, sel || hot ? DaKit.CREME : DaKit.DOUX, 0);
        }

        // Titre de la sous-catégorie (ArcadePix or, filet à losanges) et « ↺ Réinitialiser la section » au bout.
        int x0 = contentX(), xr = reglagesDroite(), ty = titreY();
        String titre = l.titreSous();
        int w = ReglagesArt.titre(ctx, f, titre == null ? "" : titre, x0, ty + 4, DaKit.OR_CLAIR, 0, true);
        int finFilet = xr;
        if (l.reinitialisable()) {
            boolean conf = System.currentTimeMillis() < confirmeJusqua;
            int col = conf ? VERMILLON : hovReinit ? DaKit.CREME : DaKit.DOUX;
            float wl = DaKit.inter7(ctx, f, DaKit.inter(libelleReinit()), xr, ty + 7, col, 2);
            int xi = Math.round(xr - wl - 10);
            ReglagesArt.iconeReinit(ctx, xi, ty + 7, col);
            finFilet = xi - 8;
        }
        ReglagesArt.filet(ctx, x0 + w + 8, finFilet, ty + 7);

        // Pied « [Échap] RETOUR » : plaque de touche laquée, puis RETOUR en ArcadePix.
        int px = subX(), py = piedY(), pw = piedLargeur();
        ReglagesArt.segment(ctx, px + 1, py - 8, pw - 3, hovPied ? 1 : 0);
        DaKit.inter9(ctx, f, DaKit.inter("Échap"), px + pw / 2f - 1, py, hovPied ? DaKit.CREME : DaKit.OR_CLAIR, 1);
        DaKit.arcX1(ctx, f, DaKit.arcade("RETOUR"), px + pw + 7, py - 3, hovPied ? DaKit.CREME : REPOS, 0, true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        int contentX = contentX();
        int contentW = contentW();
        int contentYScrolled = contentTopBase() - scrollY;

        boolean dansLignes = mouseX >= mainX() && mouseX < mainX() + mainW() && !draggingScrollbar;
        SectionedTab.survol(dansLignes ? mouseX : Integer.MIN_VALUE, mouseY, viewportTop(), viewportBottom());
        ctx.enableScissor(mainX() + 4, viewportTop(), mainX() + mainW() - 4, viewportBottom());
        activeTab().renderPassive(ctx, contentX, contentYScrolled, contentW);
        for (AbstractWidget w : contentWidgets) {
            if (w.visible) {
                w.extractRenderState(ctx, mouseX, mouseY, delta);
            }
        }
        ctx.disableScissor();
        SectionedTab.survol(Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0);

        renderScrollbar(ctx);
    }

    private void renderScrollbar(GuiGraphicsExtractor ctx) {
        if (!hasScroll()) return;
        int top = viewportTop();
        int vh = viewportH();
        int x = scrollbarX();
        ReglagesArt.rail(ctx, x, top, vh);
        ReglagesArt.curseur(ctx, x - 1, thumbY(), thumbHeight());
    }

    private int scrollbarX() {
        return mainX() + mainW() - 14;
    }

    private int thumbHeight() {
        int vh = viewportH();
        int totalH = Math.max(1, contentBottom() - viewportTop());
        return Math.max(SCROLLBAR_MIN_THUMB, (int) ((long) vh * vh / totalH));
    }

    private int thumbY() {
        int vh = viewportH();
        int thumbH = thumbHeight();
        int max = maxScroll();
        if (max <= 0) return viewportTop();
        int travel = vh - thumbH;
        return viewportTop() + (int) ((long) travel * scrollY / max);
    }

    private boolean inContentViewport(double mouseX, double mouseY) {
        return mouseX >= mainX() && mouseX < mainX() + mainW() && mouseY >= viewportTop() && mouseY <= viewportBottom();
    }

    // ─── Entrées ─────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (hasScroll() && inContentViewport(mouseX, mouseY)) {
            scrollY = Math.max(0, Math.min(maxScroll(), scrollY - (int) (vAmount * 24)));
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (button == 0) {
            if (overPied(mouseX, mouseY)) {
                RebornSounds.uiClick();
                onClose();
                return true;
            }
            int t = tabAt(mouseX, mouseY);
            if (t >= 0) {
                selectCategory(t);
                return true;
            }
            int s = sousAt(mouseX, mouseY);
            if (s >= 0) {
                selectSous(s);
                return true;
            }
            if (overReinit(mouseX, mouseY)) {
                reinitialiser();
                return true;
            }
            if (hasScroll() && overScrollbar(mouseX, mouseY)) {
                draggingScrollbar = true;
                int thumbY = thumbY();
                int thumbH = thumbHeight();
                if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                    scrollbarGrabDy = (int) mouseY - thumbY;
                } else {
                    scrollbarGrabDy = thumbH / 2;
                    dragScrollbarTo(mouseY);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dX, double dY) {
        if (draggingScrollbar) {
            dragScrollbarTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dX, dY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        boolean r = super.mouseReleased(event);
        // relâché qui n'a pas atteint le curseur (page refaite pendant le glissé) : on écrit quand même
        if (event.button() == 0 && SliderWidget.glisse()) SliderWidget.finGlisse();
        return r;
    }

    /**
     * Le widget actif (une touche en écoute) reçoit la touche avant l'écran : Échap y annule l'écoute au lieu de
     * fermer les Paramètres.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        GuiEventListener focus = getFocused();
        if (focus != null && focus.keyPressed(event)) return true;
        return super.keyPressed(event);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        int x = scrollbarX();
        return mouseX >= x - 4 && mouseX <= x + 8
            && mouseY >= viewportTop() && mouseY <= viewportBottom();
    }

    private void dragScrollbarTo(double mouseY) {
        int vh = viewportH();
        int thumbH = thumbHeight();
        int travel = vh - thumbH;
        if (travel <= 0) return;
        int thumbTop = (int) mouseY - scrollbarGrabDy - viewportTop();
        thumbTop = Math.max(0, Math.min(travel, thumbTop));
        scrollY = (int) ((long) thumbTop * maxScroll() / travel);
        applyScroll();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        SliderWidget.finGlisse();          // écritures différées des curseurs (Enregistrement)
    }

    @Override
    public void onClose() {
        RebornPrefs.INSTANCE.save();
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
