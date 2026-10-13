package fr.reborn.hud.menu.inventory;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.stats.StatsData;
import fr.reborn.hud.ui.DaKit;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>Sacoche « inrō »</b> — l'inventaire RP, au niveau de la fiche shinobi (DA §4.1) : plaque laquée suspendue,
 * onglets pendus (装 équipement, 飾 cosmétique), plaque 閉, aides en cabochons.
 *
 * <p>Le monde reste visible derrière, sous un voile et une vignette.
 *
 * <p>Signature : un <b>inrō</b> (boîte laquée à étages portée à la ceinture). Chaque étage est un filtre
 * (全 Tout / 袋 Sac / 雑 Divers) ; l'étage actif glisse vers le <b>plateau</b> de laque sang-de-bœuf qui contient
 * le sac. Le cordon passe par les flancs, perle (ojime) en haut, netsuke en bas. La barre 1-9 est une <b>obi</b> de
 * soie indigo. Gauche : personnage sur un socle laqué, cosmétiques / sac sur des plaques à ferrures.
 *
 * <p>Tout le décor fixe est une texture ({@code textures/gui/sacoche/*}, générées par
 * {@code tools/gen_sacoche_textures.py}, contrat en tête du script), posée à 1 texel = 1 GUI ; le code ne dessine que
 * ce qui bouge (étage actif, cases survolées, jauge, objets, texte). Mise en page calculée à l'init (et au
 * redimensionnement), pas à chaque image.
 *
 * <p>Logique : clic gauche = prendre / poser, clic droit = panneau d'actions, clic hors cases avec un objet en main =
 * jeter, <b>glisser hors de la fenêtre = lâcher</b>. Sons {@code sounds/sacoche/*}.
 *
 * <p>Ajouts du 13/10 (maquette 01_inventaire) : onglet Équipement avec l'<b>étui</b> (emplacement Arme, l'arme se voit
 * à la ceinture ; dégainer / rengainer par la touche Z ou le panneau) et le rappel <b>Tête</b> (chapeau / masque /
 * bandeau de l'onglet Cosmétique) ; jauge de poids graduée <b>0 → 130 %</b> (repères 100 / 115 / 130 %) ; <b>lecture
 * seule</b> en KO, porté, étourdi ou scène de mort (le serveur refuse de toute façon).
 */
public class InventoryScreen extends Screen {

    private static final int F_ALL = 0, F_SAC = 1, F_DIVERS = 2;
    private static final String[] FILTER_NAME = {"TOUT", "SAC", "DIVERS"};
    private static final int TAB_EQUIP = 0, TAB_COSM = 1;

    // ─── textures (contrat : tools/gen_sacoche_textures.py) ───
    private static Identifier t(String n) { return DaKit.tex("sacoche", n); }
    private static final Identifier VIGNETTE = t("vignette"), ENTETE = t("entete"), ONGLETS = t("onglets"),
        FERMER = t("fermer"), INRO = t("inro"), CORDON = t("cordon"), PLATEAU3 = t("plateau3"), PLATEAU4 = t("plateau4"),
        CASES = t("cases"), PLAQUES = t("plaques"), KANJI = t("kanji"), SOCLE = t("socle"), OBI = t("obi"),
        CHAMP = t("champ"), PIECES = t("pieces"), DETAIL = t("detail"), BOUTONS = t("boutons");

    private static final int CELL = 22, PITCH = 25, COLS = 9;
    private static final int TRAY_W = 246, INRO_W = 52, TIER_H = 34, TIER_PITCH = 35, TIER_SLIDE = 18;
    private static final int PLATE = 22, LEFT_W = 176, MODEL_W = 116;
    private static final int DETAIL_W = 200, DETAIL_H = 150;
    /** Cases de l'atlas cases.png. */
    private static final int C_SAC = 0, C_SAC_SURVOL = 1, C_SAC_CHOISIE = 2, C_ABSENTE = 3, C_CEINT = 4,
        C_CEINT_SURVOL = 5, C_CEINT_CHOISIE = 6, C_DEPOT = 7;

    private static String lastSearch = "";
    private static int lastFilter = F_ALL;
    private static int lastTab = TAB_COSM;

    /** Banc d'essai (REBORN_INV_DEBUG) : ouvrir sur l'onglet Équipement / Cosmétique. */
    public static void debugOnglet(boolean equipement) {
        lastTab = equipement ? TAB_EQUIP : TAB_COSM;
    }

    private InventoryData.Snapshot snap;
    private String tierDisplay;
    private int extraSlots;
    private int bagRows;

    private EditBox search;
    private int filter = lastFilter;
    private int tab = lastTab;

    private String carriedRef = null;
    private ItemStack carriedStack = null;
    /** Glisser : objet pris à l'appui (pas un clic), position d'appui, déplacement suffisant. */
    private boolean pressPick, dragging;
    private double pressX, pressY;

    private String selRef = null;
    private ItemStack selStack = null;
    private SlotItem selMeta = null;
    private int anchorX, anchorY;
    private final List<Btn> panelButtons = new ArrayList<>();
    private DetailCache detail;

    private String hoverRef = null, lastHoverRef = null;
    private CosmeticSlot hoverCosm = null;
    private final long openedAt = System.currentTimeMillis();
    private long lastFrame = openedAt;
    private boolean closing, opened;
    private final float[] tierAnim = new float[FILTER_NAME.length];

    // ─── mise en page (coordonnées GUI), calculée par layout() ───
    private int rows, trayX, trayY, trayH, gridX, gridY, obiX, obiY, hotbarX0, hotbarY;
    private int inroX, tierY0;
    private int leftX, colY0, platePitch, cosmLeftX, cosmRightX;
    private int modelX1, modelY1, modelX2, modelY2, feetY, modelSize;
    private int contentTop, contentBottom;
    private int closeX, closeY = 12;
    private final int[] tabX = new int[2];
    private int tabY = 44;

    // ─── textes calculés une fois ───
    private static final Component TITRE = DaKit.arcade("Sacoche"), ECHAP = DaKit.arcade("Echap"),
        AUCUN_SAC = DaKit.arcade("Aucun sac equipe"), LIB_SAC = DaKit.arcade("Sac");
    private static final Component[] TAB_LIB = {DaKit.arcade("Equipement"), DaKit.arcade("Cosmetique")};
    private static final Component[] FILTER_LIB = {DaKit.arcade("Tout"), DaKit.arcade("Sac"), DaKit.arcade("Divers")};
    private static final Component[] NUM = new Component[9];
    static {
        for (int i = 0; i < 9; i++) NUM[i] = DaKit.arcade(String.valueOf(i + 1));
    }
    private Component identite, compte, poidsVal, tierLib;
    private DaKit.Etiq etiqPoids;
    private DaKit.Paragraphe sansSac;
    private float poidsRatio;
    private int jaugeX, jaugeW;
    private Component nomTete, nomArme, lecture;
    private static final Component LIB_TETE = DaKit.arcade("Tete"), LIB_ARME = DaKit.arcade("Arme"),
        DEGAINEE = DaKit.arcade("Degainee");
    private static final int KJ_ARME = 11, KJ_TETE = 12;

    public InventoryScreen() {
        super(Component.literal("Inventaire"));
        loadSnapshot();
    }

    /** (Re)lit l'état courant. Appelé au boot et à chaque push serveur (refresh en place). */
    private void loadSnapshot() {
        this.snap = InventoryData.get();
        this.tierDisplay = BagTiers.fromName(snap.bagTier()).displayName;
        this.extraSlots = Math.max(0, snap.extraSlots());
        this.bagRows = (extraSlots + COLS - 1) / COLS;
        this.tierLib = DaKit.arcade(tierDisplay);
        this.compte = DaKit.arcade(extraSlots > 0 ? itemCount() + " / " + extraSlots + "  -  " + tierDisplay : "Aucun sac");
        poidsTextes();
    }

    /** Valeur de la jauge : « 8,4 / 20,0 kg », et au-delà de la limite « 45,2 / 38,0 kg · 119 % ». */
    private void poidsTextes() {
        double cur = snap.curWeight(), max = snap.maxWeight();
        this.poidsRatio = (float) (max > 0 ? cur / max : 0);
        String v = String.format(Locale.FRANCE, "%.1f / %.1f kg", cur, max);
        if (poidsRatio > 1f) v += " · " + Math.round(poidsRatio * 100) + " %";
        this.poidsVal = DaKit.inter(v);
        Font fo = Minecraft.getInstance().font;
        SlotItem tete = tete();
        this.nomTete = DaKit.arcade(tete != null && tete.name != null ? DaKit.ellipse(fo, tete.name, 110) : "-");
        SlotItem arme = snap.equipped().get(CosmeticSlot.ARME);
        this.nomArme = arme != null && arme.name != null ? DaKit.arcade(DaKit.ellipse(fo, arme.name, 110))
            : snap.armeDegainee() ? DEGAINEE : DaKit.arcade("-");
        String l = verrou();
        this.lecture = l == null ? null : DaKit.inter("Lecture seule — " + switch (l) {
            case "mort" -> "scène de mort";
            default -> l;
        });
    }

    /** Objet porté à la tête (masque, puis chapeau, puis bandeau), pour le rappel « Tête » de l'onglet Équipement. */
    private SlotItem tete() {
        CosmeticSlot cs = teteSlot();
        return cs != null ? snap.equipped().get(cs) : null;
    }

    private CosmeticSlot teteSlot() {
        for (CosmeticSlot cs : new CosmeticSlot[] {CosmeticSlot.MASQUE, CosmeticSlot.CHAPEAU, CosmeticSlot.BANDEAU}) {
            if (snap.equipped().get(cs) != null) return cs;
        }
        return null;
    }

    /** Raison de la lecture seule (snapshot, ou état de charge plus récent poussé sur reborn:notif), sinon null. */
    private String verrou() {
        if (DEBUG_VERROU != null) return DEBUG_VERROU;
        var p = fr.reborn.hud.notif.NotifClient.poids();
        if (p != null && InventoryData.fromServer()) return p.verrou();
        return snap.lecture();
    }

    private boolean lectureSeule() {
        return verrou() != null;
    }

    /** Banc d'essai : verrou imposé (« KO »…). */
    public static String DEBUG_VERROU;

    /** Met à jour l'écran avec le nouvel état sans le recréer (filtre, onglet, recherche, objet en main conservés). */
    public void refresh() {
        loadSnapshot();
        if (selRef != null && stackAt(selRef) == null) closePanel();
        if (search != null) layout();
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        }
        search = new EditBox(this.font, 0, 0, 108, 10, Component.literal("search"));
        search.setMaxLength(48);
        search.setHint(DaKit.inter("Chercher…").withColor(0xFF8E6A5A));
        search.setBordered(false);
        search.setTextColor(DaKit.CREME);
        search.setValue(lastSearch);
        search.setResponder(v -> lastSearch = v);
        this.addRenderableWidget(search);
        for (int i = 0; i < tierAnim.length; i++) tierAnim[i] = i == filter ? 1f : 0f;
        etiqPoids = DaKit.etiq(this.font, "Poids");
        sansSac = DaKit.paragraphe(this.font, "Portez une sacoche, une bandoulière ou un sac pour débloquer de "
            + "l'espace de rangement.", 180, DaKit.S7, 9, 3);
        identite = DaKit.inter(DaKit.ellipse(this.font, identity(), Math.round(150 / DaKit.S7)));
        if (!opened && DEBUG != null) debugState();
        layout();
        if (!opened) {   // premier init seulement (pas au redimensionnement)
            opened = true;
            send("open");
            RebornSounds.playReborn("sacoche.open", 1.0f, 0.55f);
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        }
        lastFilter = filter;
        lastTab = tab;
        super.removed();
    }

    @Override
    public void onClose() {
        if (!closing) { closing = true; RebornSounds.playReborn("sacoche.close", 1.0f, 0.5f); }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Banc de captures, <b>inactif en production</b> : {@code REBORN_SACOCHE_DEBUG} (client de dev, avec
     * {@code REBORN_AUDIT=1 REBORN_AUDIT_ONLY=menu_sacoche}) impose un état : {@code detail} (panneau d'actions
     * ouvert), {@code porte} (objet en main), {@code equip} (onglet équipement), {@code vide} (aucun sac),
     * {@code lourd} (sac lourd plein, surcharge). Valeurs combinables séparées par des virgules.
     */
    private static final String DEBUG = System.getenv("REBORN_SACOCHE_DEBUG");

    private void debugState() {
        java.util.Set<String> o = java.util.Set.of(DEBUG.split(","));
        if (o.contains("vide")) {
            snap = new InventoryData.Snapshot("NONE", 9, 0, 12.0, 3.1, null, new SlotItem[0], snap.equipped());
        }
        if (o.contains("lourd")) {
            SlotItem[] bag = new SlotItem[36];
            String[] mats = {"minecraft:iron_sword", "minecraft:arrow", "minecraft:bread", "minecraft:paper", "minecraft:leather",
                "minecraft:string", "minecraft:bone", "minecraft:flint", "minecraft:feather"};
            for (int i = 0; i < 31; i++) bag[i] = new SlotItem(mats[i % mats.length], null, 1 + i % 5, "Objet " + i, 1.6);
            snap = new InventoryData.Snapshot("SAC_LOURD", 9, 36, 57.0, 59.6, snap.bagItem(), bag, snap.equipped());
        }
        if (o.contains("vide") || o.contains("lourd")) {
            tierDisplay = BagTiers.fromName(snap.bagTier()).displayName;
            extraSlots = snap.extraSlots();
            bagRows = (extraSlots + COLS - 1) / COLS;
            tierLib = DaKit.arcade(tierDisplay);
            compte = DaKit.arcade(extraSlots > 0 ? itemCount() + " / " + extraSlots + "  -  " + tierDisplay : "Aucun sac");
            poidsTextes();
        }
        if (o.contains("arme")) {   // étui garni + tête (masque) : onglet Équipement de la maquette 01
            java.util.Map<CosmeticSlot, SlotItem> eq = new java.util.EnumMap<>(CosmeticSlot.class);
            eq.putAll(snap.equipped());
            SlotItem arme = new SlotItem("minecraft:iron_sword", null, 1, "Ninjato", 2.0, null, "RARE", null, null);
            arme.arme = true;
            eq.put(CosmeticSlot.ARME, arme);
            eq.put(CosmeticSlot.MASQUE, new SlotItem("minecraft:carved_pumpkin", null, 1, "Masque ANBU", 0.4, null, "RARE",
                null, null));
            snap = new InventoryData.Snapshot(snap.bagTier(), snap.baseSlots(), snap.extraSlots(), snap.maxWeight(),
                snap.curWeight(), snap.bagItem(), snap.bag(), eq, snap.hotbar(), snap.lecture(), snap.palier(),
                snap.poidsMax(), false, true);
            tab = TAB_EQUIP;
        }
        if (o.contains("p119")) snap = snap.avec(38.0, 45.2, null, 2);
        if (o.contains("lecture")) DEBUG_VERROU = "KO";
        if (o.contains("arme") || o.contains("p119") || o.contains("lecture")) poidsTextes();
        if (o.contains("equip")) tab = TAB_EQUIP;
        if (o.contains("porte")) { carriedRef = "B0"; carriedStack = stackAt("B0"); }
        if (o.contains("detail")) {
            SlotItem m = new SlotItem("minecraft:paper", null, 1, "Parchemin — Katon : Gōkakyū", 0.3,
                "Rassembler le chakra dans la poitrine et souffler une grande boule de feu. Se lit à la bibliothèque.",
                "Rare", "lire", "Lire");
            selRefDebug = m;
        }
    }

    private SlotItem selRefDebug;
    private String dernierVerrou;

    // ─────────── Mise en page (une fois, et à chaque changement de taille ou de sac) ───────────
    private void layout() {
        int W = this.width, H = this.height;
        rows = Math.max(3, Math.min(4, bagRows));
        trayH = 30 + rows * PITCH - 3 + 30;
        int obiH = 30;
        contentTop = 84;
        contentBottom = H - 26;
        int availH = contentBottom - contentTop;

        // bloc : [plaques | personnage | plaques] [inrō] [plateau + obi]
        int rightH = trayH + 8 + obiH;
        platePitch = Math.max(26, Math.min(36, (Math.max(rightH, 176) - PLATE) / 4));
        int colH = 4 * platePitch + PLATE;
        int blockH = Math.max(rightH, Math.max(colH + 26, 176));
        int blockTop = contentTop + Math.max(0, (availH - blockH) * 2 / 5);
        int blockW = LEFT_W + 26 + INRO_W + 30 + TRAY_W + 12;
        leftX = Math.max(8, (W - blockW) / 2);

        inroX = leftX + LEFT_W + 26;
        trayX = inroX + INRO_W + 30;
        trayY = blockTop + Math.max(0, (blockH - rightH) / 2);
        gridX = trayX + 12;
        gridY = trayY + 30;
        obiX = trayX - 12;
        obiY = trayY + trayH + 8;
        hotbarX0 = obiX + 24;
        hotbarY = obiY + 4;
        tierY0 = trayY + 6;

        colY0 = blockTop + Math.max(0, (blockH - 26 - colH) / 2);
        cosmLeftX = leftX;
        cosmRightX = leftX + LEFT_W - PLATE;
        modelX1 = leftX + PLATE + 8;
        modelX2 = modelX1 + MODEL_W;
        feetY = colY0 + colH + 2;
        modelSize = Math.max(40, Math.min(80, Math.round(colH * 0.46f)));
        modelY1 = feetY - Math.round(1.8f * modelSize);
        modelY2 = feetY;

        // en-tête
        tabX[0] = W / 2 - 92;
        tabX[1] = W / 2 + 92;
        closeX = W - 34;

        // recherche (champ dans l'en-tête du plateau)
        search.setX(trayX + 17);
        search.setY(trayY + 10);
        search.setWidth(110);

        // jauge de poids : POIDS [rainure] valeur
        int vw = (int) Math.ceil(DaKit.largeur8(this.font, poidsVal));
        jaugeX = trayX + 14 + Math.round(etiqPoids.w()) + 8;
        jaugeW = Math.max(40, Math.min(204, trayX + TRAY_W - 14 - vw - 8 - jaugeX));
        detail = null;
        if (selRefDebug != null) {
            openPanel("B3", stackAt("B3"), selRefDebug, bagX(3), bagY(3));
            selRefDebug = null;
        }
    }

    private int hotbarX(int i) { return hotbarX0 + i * PITCH; }

    // ─────────── Accès items ───────────
    private ItemStack stackAt(String ref) {
        if (ref == null || ref.length() < 2) return null;
        if (ref.charAt(0) == 'C') {
            CosmeticSlot cs = CosmeticSlot.fromName(ref.substring(1));
            if (cs == null) return null;
            SlotItem si = snap.equipped().get(cs);
            return si != null ? si.toStack() : null;
        }
        int i = idx(ref);
        if (i < 0) return null;
        if (ref.charAt(0) == 'H') {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || i >= 9) return null;
            ItemStack s = mc.player.getInventory().getItem(i);
            return (s == null || s.isEmpty()) ? null : s;
        }
        if (ref.charAt(0) == 'B') {
            if (i >= extraSlots || snap.bag() == null || i >= snap.bag().length) return null;
            SlotItem si = snap.bag()[i];
            return si != null ? si.toStack() : null;
        }
        return null;
    }

    private SlotItem metaAt(String ref) {
        if (ref == null || ref.charAt(0) != 'B') return null;
        int i = idx(ref);
        return (i >= 0 && snap.bag() != null && i < snap.bag().length) ? snap.bag()[i] : null;
    }

    private static int idx(String ref) {
        try { return Integer.parseInt(ref.substring(1)); } catch (Exception e) { return -1; }
    }

    private boolean isBagStack(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        Identifier id = s.get(DataComponents.ITEM_MODEL);
        return id != null && BagTiers.fromModel(id.toString()) != null;
    }

    private String hoverName(ItemStack s) {
        try { return s.getHoverName().getString(); } catch (Exception e) { return null; }
    }

    private String actionLabel(ItemStack st, SlotItem meta) {
        if (meta != null && meta.hasAction()) return meta.actionLabel;
        String n = st != null ? hoverName(st) : null;
        if (n != null && n.toLowerCase(Locale.ROOT).contains("feuille")) return "Faire le test";
        return null;
    }

    private int groupOf(ItemStack s) {
        if (isBagStack(s)) return F_SAC;
        Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
        String p = id != null ? id.toString() : "";
        if (p.matches(".*(sword|_axe|bow|crossbow|trident|pickaxe|shovel|_hoe|mace|arrow|helmet|chestplate|leggings|boots|shield|elytra).*"))
            return F_SAC;
        return F_DIVERS;
    }

    private boolean dimmed(ItemStack st, SlotItem meta) {
        if (st == null) return false;
        if (filter != F_ALL && groupOf(st) != filter) return true;
        String q = search != null ? search.getValue().trim().toLowerCase(Locale.ROOT) : "";
        if (!q.isEmpty()) {
            String n = meta != null && meta.name != null ? meta.name : hoverName(st);
            if (n == null || !n.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    private int itemCount() {
        int n = 0;
        if (snap.bag() != null) for (int i = 0; i < Math.min(extraSlots, snap.bag().length); i++) if (snap.bag()[i] != null) n++;
        return n;
    }

    // ─────────── Rendu ───────────
    /** Fond : le monde reste visible, sous un voile et une vignette (texture lisse étirée). */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        ctx.fill(0, 0, this.width, this.height, DaKit.argb(0x08040A, 0.34f * appear));
        DaKit.etire(ctx, VIGNETTE, 0, 0, this.width, this.height, 160, 90, DaKit.blanc(appear));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (InventoryData.fromServer()) {
            InventoryData.Snapshot cur = InventoryData.get();
            if (cur != snap) refresh();
        }
        String v = verrou();
        if (!java.util.Objects.equals(v, dernierVerrou)) {   // KO / porté / étourdi arrivé pendant que la sacoche est ouverte
            dernierVerrou = v;
            poidsTextes();
            if (v != null) { clearCarried(); closePanel(); }
        }
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float time = (now - openedAt) / 1000f;
        float appear = ease(Math.min(1f, (now - openedAt) / 260f));
        for (int i = 0; i < tierAnim.length; i++) tierAnim[i] += ((i == filter ? 1f : 0f) - tierAnim[i]) * Math.min(1f, dt * 12f);

        Font f = this.font;
        hoverRef = slotAt(mouseX, mouseY);
        hoverCosm = null;
        if (hoverRef != null && !hoverRef.equals(lastHoverRef)) RebornSounds.playReborn("sacoche.hover", 1.0f, 0.18f);
        lastHoverRef = hoverRef;

        drawHeader(ctx, f, mouseX, mouseY);

        // gauche : socle + personnage + plaques
        DaKit.blit(ctx, SOCLE, (modelX1 + modelX2) / 2 - 60, feetY - 6, 0, 0, 124, 22, 124, 22);
        drawPreview(ctx, mouseX, mouseY);
        if (tab == TAB_COSM) drawCosmetics(ctx, mouseX, mouseY);
        else drawEquip(ctx, f, mouseX, mouseY);

        // inrō + plateau + obi : glissent légèrement à l'ouverture
        int slideX = Math.round((1f - appear) * 24);
        ctx.pose().pushMatrix();
        ctx.pose().translate(slideX, 0);
        drawInro(ctx, f, time, mouseX - slideX, mouseY);
        drawTray(ctx, f);
        drawObi(ctx, f);
        ctx.pose().popMatrix();
        search.extractRenderState(ctx, mouseX, mouseY, delta);

        if (selRef != null && selStack != null) drawDetailPanel(ctx, f, mouseX, mouseY);

        if (carriedStack != null) {
            ctx.item(carriedStack, mouseX - 8, mouseY - 8);
            ctx.itemDecorations(f, carriedStack, mouseX - 8, mouseY - 8);
        }
        if (carriedStack == null && selRef == null) {
            if (hoverCosm != null) {
                ctx.setTooltipForNextFrame(f, Component.literal(hoverCosm.label), mouseX, mouseY);
            } else if (hoverRef != null) {
                ItemStack tip = stackAt(hoverRef);
                if (tip != null) ctx.setTooltipForNextFrame(f, tip, mouseX, mouseY);
            }
        }
        drawHint(ctx, f);
    }

    // ── en-tête : plaque laquée suspendue, onglets pendus, plaque 閉 ──
    private void drawHeader(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        int cx = this.width / 2;
        DaKit.blit(ctx, ENTETE, cx - 160, 0, 0, 0, 320, 48, 320, 48);
        DaKit.arcX1(ctx, f, TITRE, cx, 19, DaKit.CREME, 1, true);
        DaKit.inter7o(ctx, f, identite, cx, 47, DaKit.argb(0xE8DCC0, 0.78f), 1);

        for (int i = 0; i < 2; i++) {
            boolean act = tab == (i == 0 ? TAB_EQUIP : TAB_COSM);
            boolean hov = !act && inTab(i, mx, my);
            int etat = act ? 2 : hov ? 1 : 0;
            DaKit.blit(ctx, ONGLETS, tabX[i] - 13, 36, (i * 3 + etat) * 26, 0, 26, 36, 156, 36);
            int ty = act ? 46 : 44;
            DaKit.arcFin(ctx, f, TAB_LIB[i], tabX[i], ty + 26, act ? DaKit.CREME : hov ? DaKit.CREME_VAL : 0xFFA88C5E, 1, act);
        }
        boolean h = inClose(mx, my);
        DaKit.blit(ctx, FERMER, closeX - 13, 0, h ? 26 : 0, 0, 26, 38, 52, 38);
        DaKit.arcFin(ctx, f, ECHAP, closeX, 41, h ? DaKit.CREME_VAL : 0xFFA88C5E, 1, false);
    }

    private boolean inTab(int i, double mx, double my) {
        return DaKit.dans(mx, my, tabX[i] - 12, 40, 24, 38);
    }

    private boolean inClose(double mx, double my) {
        return DaKit.dans(mx, my, closeX - 12, 10, 24, 36);
    }

    private static String identity() {
        StatsData.Snapshot s = StatsData.get();
        StringBuilder b = new StringBuilder();
        if (s != null && s.name() != null && !s.name().isBlank() && !"?".equals(s.name())) b.append(s.name());
        else {
            var p = Minecraft.getInstance().player;
            if (p != null) b.append(p.getName().getString());
        }
        if (s != null && s.rank() != null && !s.rank().isBlank() && !"?".equals(s.rank())) b.append(" · ").append(s.rank());
        if (s != null && s.village() != null && !s.village().isBlank()) b.append(" · ").append(s.village());
        return b.toString();
    }

    private void drawPreview(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || modelX2 <= modelX1 || modelY2 <= modelY1) return;
        // Le rendu d'entité est découpé à sa boîte, et le personnage y est centré (milieu de la hitbox au centre) :
        // une boîte de 1,8 × taille rognait la seconde couche de la tête et les coiffures hautes (ananas, chignons,
        // crête). On l'agrandit de la même marge en haut et en bas (et sur les côtés) : le personnage ne bouge pas.
        int my = modelSize * 3 / 4, mxg = modelSize / 2;   // ¾ de bloc au-dessus de la tête : coiffures 3D comprises
        net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(ctx,
            modelX1 - mxg, modelY1 - my, modelX2 + mxg, modelY2 + my, modelSize, 0f, mouseX, (modelY1 + modelY2) / 2f,
            mc.player);
    }

    /** Plaque cosmétique (plaques.png) : 0 vide, 1 survol, 2 garnie, 3 dépôt possible ; kanji si vide. */
    private void plaque(GuiGraphicsExtractor ctx, int x, int y, SlotItem it, int kanji, boolean droppable, int mx, int my) {
        boolean hover = DaKit.dans(mx, my, x, y, PLATE, PLATE);
        int etat = droppable ? 3 : hover ? 1 : it != null ? 2 : 0;
        DaKit.blit(ctx, PLAQUES, x, y, etat * 24, 0, 24, 25, 96, 25);
        if (it != null) ctx.item(it.toStack(), x + 3, y + 3);
        else if (kanji >= 0) DaKit.blit(ctx, KANJI, x + 3, y + 3, kanji * 16, 0, 16, 16, 208, 16,
            hover || droppable ? DaKit.blanc(1f) : DaKit.blanc(0.75f));
    }

    private int[] cosmPos(CosmeticSlot cs) {
        int row = 0;
        for (CosmeticSlot o : CosmeticSlot.values()) {
            if (o == cs) break;
            if (o.side == cs.side) row++;
        }
        int x = cs.side == 0 ? cosmLeftX : cosmRightX;
        return new int[] { x, colY0 + row * platePitch };
    }

    private void drawCosmetics(GuiGraphicsExtractor ctx, int mx, int my) {
        for (CosmeticSlot cs : CosmeticSlot.values()) {
            if (!cs.cosmetique()) continue;
            int[] p = cosmPos(cs);
            plaque(ctx, p[0], p[1], snap.equipped().get(cs), cs.ordinal(), carriedStack != null, mx, my);
            if (DaKit.dans(mx, my, p[0], p[1], PLATE, PLATE)) hoverCosm = cs;
        }
    }

    private void drawEquip(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        int x = cosmLeftX, y = colY0;
        plaque(ctx, x, y, snap.bagItem(), 10, carriedStack != null && isBagStack(carriedStack), mx, my);
        DaKit.arcFin(ctx, f, LIB_SAC, x + PLATE / 2f, y + PLATE + 5, DaKit.OR_ETIQ, 1, false);
        DaKit.arcFin(ctx, f, tierLib, x + PLATE / 2f, y + PLATE + 12, DaKit.CREME_VAL, 1, false);
        // Tête : rappel des emplacements Chapeau / Masque / Bandeau de l'onglet Cosmétique (pas un nouvel emplacement)
        int xt = cosmRightX, yt = teteY();
        SlotItem tete = tete();
        plaque(ctx, xt, yt, tete, KJ_TETE, false, mx, my);
        if (tete != null) pastille(ctx, xt, yt, tete.rarity);
        DaKit.arcFin(ctx, f, LIB_TETE, xt + PLATE / 2f, yt + PLATE + 5, DaKit.OR_ETIQ, 1, false);
        DaKit.arcFin(ctx, f, nomTete, xt + PLATE / 2f, yt + PLATE + 12, DaKit.CREME_VAL, 1, false);
        // Arme : l'étui (l'arme rangée se voit à la ceinture du personnage)
        int ya = armeY();
        SlotItem arme = snap.equipped().get(CosmeticSlot.ARME);
        boolean depot = carriedStack != null && carriedEstArme();
        plaque(ctx, xt, ya, arme, KJ_ARME, depot, mx, my);
        if (arme != null) pastille(ctx, xt, ya, arme.rarity);
        DaKit.arcFin(ctx, f, LIB_ARME, xt + PLATE / 2f, ya + PLATE + 5, DaKit.OR_ETIQ, 1, false);
        DaKit.arcFin(ctx, f, nomArme, xt + PLATE / 2f, ya + PLATE + 12, snap.armeDegainee() && arme == null
            ? DaKit.OR_CLAIR : DaKit.CREME_VAL, 1, false);
    }

    private int teteY() { return colY0; }

    private int armeY() { return colY0 + Math.max(2 * platePitch, PLATE + 26); }

    /** Petit carré de rareté au coin de la plaque (maquette 01). */
    private void pastille(GuiGraphicsExtractor ctx, int x, int y, String rarity) {
        fr.reborn.hud.sol.Rarete r = fr.reborn.hud.sol.Rarete.parse(rarity);
        if (r == null || r == fr.reborn.hud.sol.Rarete.COMMUN) return;
        ctx.fill(x + 2, y + 2, x + 4, y + 4, r.argb());
    }

    /** L'objet porté va-t-il à l'étui ? (métadonnée serveur, sinon épée / hache / trident / masse). */
    private boolean carriedEstArme() {
        SlotItem m = carriedRef != null ? metaAtAny(carriedRef) : null;
        if (m != null) return m.arme;
        if (carriedStack == null) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(carriedStack.getItem());
        String p = id != null ? id.getPath() : "";
        return p.endsWith("_sword") || p.endsWith("_axe") || p.equals("trident") || p.equals("mace");
    }

    private SlotItem metaAtAny(String ref) {
        if (ref == null || ref.length() < 2) return null;
        if (ref.charAt(0) == 'H') return snap.hotbarMeta(idx(ref));
        return metaAt(ref);
    }

    private boolean armeSlotAt(int mx, int my) {
        return tab == TAB_EQUIP && DaKit.dans(mx, my, cosmRightX, armeY(), PLATE, PLATE);
    }

    private boolean teteSlotAt(int mx, int my) {
        return tab == TAB_EQUIP && DaKit.dans(mx, my, cosmRightX, teteY(), PLATE, PLATE);
    }

    // ── inrō : étages = filtres ──
    private void drawInro(GuiGraphicsExtractor ctx, Font f, float time, int mx, int my) {
        DaKit.blit(ctx, CORDON, inroX - 4, tierY0 - 20, 0, 0, 60, 176, 60, 176);
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.4f);
        DaKit.blit(ctx, PIECES, inroX - 4 + 26, tierY0 - 20 + 4, 30, 44, 9, 9, 210, 74, DaKit.blanc(0.35f + 0.5f * pulse));
        for (int i = 0; i < FILTER_NAME.length; i++) {
            int y = tierY0 + i * TIER_PITCH;
            int dx = Math.round(tierAnim[i] * TIER_SLIDE);
            boolean act = filter == i;
            boolean hov = !act && DaKit.dans(mx, my, inroX, y, INRO_W + TIER_SLIDE, TIER_H);
            int etat = act ? 2 : hov ? 1 : 0;
            if (act) {   // glissière d'or vers le plateau
                ctx.fill(inroX + dx + INRO_W, y + TIER_H / 2, trayX, y + TIER_H / 2 + 1, DaKit.OR_ETEINT);
                ctx.fill(inroX + dx + INRO_W, y + TIER_H / 2 + 1, trayX, y + TIER_H / 2 + 2, 0xFF1A0608);
            }
            DaKit.blit(ctx, INRO, inroX - 1 + dx, y, etat * 56, i * 36, 56, 36, 168, 108);
            DaKit.arcFin(ctx, f, FILTER_LIB[i], inroX + dx + INRO_W / 2f, y + 24, act ? DaKit.CREME : hov ? DaKit.CREME_VAL
                : 0xFFA88C5E, 1, act);
        }
    }

    // ── plateau laqué : recherche, compteur, grille du sac, poids ──
    private void drawTray(GuiGraphicsExtractor ctx, Font f) {
        DaKit.blit(ctx, rows == 4 ? PLATEAU4 : PLATEAU3, trayX, trayY, 0, 0, TRAY_W + 4, trayH + 4, TRAY_W + 4, trayH + 4);
        DaKit.blit(ctx, CHAMP, trayX + 12, trayY + 6, 0, 0, 124, 14, 124, 14);
        DaKit.arcFin(ctx, f, compte, trayX + TRAY_W - 14, trayY + 11.25f, DaKit.OR, 2, true);

        if (extraSlots > 0) {
            int n = rows * COLS;
            for (int i = 0; i < n; i++) {
                int x = bagX(i), y = bagY(i);
                if (i >= extraSlots) { DaKit.blit(ctx, CASES, x, y, C_ABSENTE * 22, 0, 22, 22, 176, 22, DaKit.blanc(0.5f)); continue; }
                String ref = "B" + i;
                boolean hov = ref.equals(hoverRef), sel = ref.equals(selRef);
                cell(ctx, x, y, sel ? C_SAC_CHOISIE : hov && carriedStack != null ? C_DEPOT : hov ? C_SAC_SURVOL : C_SAC);
                ItemStack st = ref.equals(carriedRef) ? null : stackAt(ref);
                SlotItem meta = snap.bag() != null && i < snap.bag().length ? snap.bag()[i] : null;
                drawItem(ctx, f, x, y, st, meta);
            }
        } else {
            drawNoBagNotice(ctx, f);
        }
        drawWeight(ctx, f);
    }

    private static void cell(GuiGraphicsExtractor ctx, int x, int y, int etat) {
        DaKit.blit(ctx, CASES, x, y, etat * 22, 0, 22, 22, 176, 22);
    }

    private void drawItem(GuiGraphicsExtractor ctx, Font f, int x, int y, ItemStack st, SlotItem meta) {
        if (st == null || st.isEmpty()) return;
        ctx.item(st, x + 3, y + 3);
        ctx.itemDecorations(f, st, x + 3, y + 3);
        if (meta != null && meta.rarity != null
            && fr.reborn.hud.sol.Rarete.parse(meta.rarity) != fr.reborn.hud.sol.Rarete.COMMUN) {
            ctx.fill(x + 3, y + 19, x + 19, y + 20, rarityColor(meta.rarity));
        }
        if (dimmed(st, meta)) ctx.fill(x + 1, y + 1, x + 21, y + 21, 0xA8140306);
    }

    /**
     * Jauge graduée 0 → 130 % (maquette 01) : or jusqu'à la limite, laque rouge au-delà (on porte encore, on ralentit),
     * zone de surcharge assombrie, repères à 100 % (or), 115 % (ambre) et 130 % (vermillon, refus de ramasser).
     */
    private void drawWeight(GuiGraphicsExtractor ctx, Font f) {
        float ym = trayY + trayH - 14;
        boolean over = poidsRatio > 1f;
        DaKit.etiq(ctx, f, etiqPoids, trayX + 14, ym, DaKit.OR_ETIQ);
        int ry = Math.round(ym) - 5;
        DaKit.coupeH(ctx, PIECES, jaugeX, ry, jaugeW, 0, 14, 204, 12, 4, 210, 74);
        float plafond = (float) Math.max(1.05, snap.poidsMax());
        int inner = jaugeW - 4, x0 = jaugeX + 2;
        int p100 = x0 + Math.round(inner / plafond), pSur = x0 + Math.round(inner * 1.15f / plafond), pFin = x0 + inner;
        ctx.fill(p100, ry + 2, pFin, ry + 8, 0xFF2E0C10);                                   // zone de surcharge
        int fw = Math.round(inner * Math.max(0f, Math.min(1f, poidsRatio)) / plafond);
        DaKit.blit(ctx, PIECES, x0, ry + 2, 0, 28, Math.min(196, fw), 6, 210, 74);           // or jusqu'à 100 %
        if (over) {
            int fx = x0 + Math.round(inner * Math.min(plafond, poidsRatio) / plafond);
            DaKit.blit(ctx, PIECES, p100, ry + 2, 0, 36, Math.max(0, Math.min(196, fx - p100)), 6, 210, 74);
        }
        ctx.fill(p100, ry, p100 + 1, ry + 10, 0xFFF2D59A);                                  // repère 100 %
        ctx.fill(pSur, ry + 1, pSur + 1, ry + 9, 0xFFE0A050);                               // repère 115 %
        ctx.fill(pFin - 1, ry + 1, pFin, ry + 9, 0xFFD2523E);                               // repère 130 %
        DaKit.inter8(ctx, f, poidsVal, trayX + TRAY_W - 14, ym, over ? DaKit.ROUGE_CLAIR : DaKit.CREME_VAL, 2);
    }

    private void drawNoBagNotice(GuiGraphicsExtractor ctx, Font f) {
        int cx = trayX + TRAY_W / 2, top = gridY + 4;
        DaKit.blit(ctx, PIECES, cx - 13, top, 0, 44, 28, 28, 210, 74);
        DaKit.arcX1(ctx, f, AUCUN_SAC, cx, top + 34, DaKit.OR, 1, true);
        int lines = sansSac.lignes().size();
        float y = top + 48;
        for (int i = 0; i < lines; i++) {
            var l = sansSac.lignes().get(i);
            float w = f.width(l) * DaKit.S7;
            DaKit.texteEchelle(ctx, f, l, cx - w / 2f, y + i * 9, DaKit.argb(0xE8DCC0, 0.72f), DaKit.S7);
        }
    }

    // ── obi : ceinture de soie, barre 1-9 ──
    private void drawObi(GuiGraphicsExtractor ctx, Font f) {
        DaKit.blit(ctx, OBI, obiX, obiY, 0, 0, 274, 34, 274, 34);
        for (int i = 0; i < 9; i++) {
            String ref = "H" + i;
            int x = hotbarX(i);
            boolean hov = ref.equals(hoverRef), sel = ref.equals(selRef);
            cell(ctx, x, hotbarY, sel ? C_CEINT_CHOISIE : hov && carriedStack != null ? C_DEPOT : hov ? C_CEINT_SURVOL : C_CEINT);
            drawItem(ctx, f, x, hotbarY, ref.equals(carriedRef) ? null : stackAt(ref), snap.hotbarMeta(i));
            DaKit.arcFin(ctx, f, NUM[i], x + 3, hotbarY + 16, DaKit.argb(0xE8DCC0, 0.55f), 0, false);
        }
    }

    private void drawHint(GuiGraphicsExtractor ctx, Font f) {
        String[][] items = lectureSeule()
            ? new String[][] {{"Lecture seule", null}, {"Echap", "fermer"}}
            : carriedStack != null
            ? new String[][] {{"Clic", "poser"}, {"Glisser dehors", "lâcher"}, {"Echap", "annuler"}}
            : new String[][] {{"Clic", "prendre / poser"}, {"Clic droit", "actions"}, {"Glisser dehors", "lâcher"},
                {"Echap", "fermer"}};
        DaKit.aides(ctx, f, PIECES, 0, 0, 210, 74, items, this.width - 18, this.height - 14, 1f);
        if (lecture != null) {
            // sous l'identité : « Lecture seule — KO » ; le serveur refuse de toute façon
            DaKit.inter7o(ctx, f, lecture, this.width / 2f, 57, DaKit.argb(0xE8907E, 0.95f), 1);
        }
    }

    // ─────────── Panneau détail (ancré sur l'objet) ───────────
    private static final class Btn {
        final Component label;
        final boolean danger;
        final Runnable act;
        int x, y, w;
        Btn(String label, boolean danger, Runnable act) {
            this.label = DaKit.arcade(label);
            this.danger = danger; this.act = act;
        }
    }

    /** Textes du panneau détail, calculés à l'ouverture. */
    private record DetailCache(Component nom, boolean nomArcade, Component rarete, int rareteCol, Component poids, DaKit.Paragraphe desc,
                               DaKit.Etiq eRarete, DaKit.Etiq ePoids, int px, int py) {}

    private DetailCache buildDetail(Font f) {
        String name = selMeta != null && selMeta.name != null ? selMeta.name : hoverName(selStack);
        if (name == null) name = "Objet";
        // titre en ArcadePix ×1 s'il tient ; sinon Inter gras (minuscules, accents) coupé par « … »
        Component nom = DaKit.arcade(name);
        boolean arcade = f.width(nom) <= DETAIL_W - 48;
        if (!arcade) {
            String nm = name;
            while (nm.length() > 1 && f.width(DaKit.interGras(nm + "…")) > DETAIL_W - 48) nm = nm.substring(0, nm.length() - 1);
            nom = DaKit.interGras(nm.equals(name) ? name : nm.trim() + "…");
        }
        double weight = selMeta != null ? selMeta.weight : 0;
        String rarity = fr.reborn.hud.sol.Rarete.parseOr(selMeta != null ? selMeta.rarity : null,
            fr.reborn.hud.sol.Rarete.COMMUN).label;
        String desc = selMeta != null && selMeta.desc != null && !selMeta.desc.isBlank() ? selMeta.desc.replace('\n', ' ')
            : "Aucune description.";

        int px = anchorX + CELL + 6;
        if (px + DETAIL_W > this.width - 4) px = anchorX - DETAIL_W - 6;
        if (px < 4) px = 4;
        int py = Math.max(contentTop - 20, Math.min(anchorY - 8, contentBottom - DETAIL_H));

        buildPanelButtons();
        int bx = px + 12, by = py + DETAIL_H - 28;
        for (Btn b : panelButtons) {
            b.x = bx; b.y = by;
            b.w = (int) Math.ceil(DaKit.largeurFin(f, b.label)) + 16;
            bx += b.w + 5;
        }
        return new DetailCache(nom, arcade, DaKit.inter(rarity), rarityColor(rarity),
            DaKit.inter(weight > 0 ? String.format(Locale.FRANCE, "%.2f kg", weight) : "—"),
            DaKit.paragraphe(f, desc, DETAIL_W - 24, DaKit.S7, 9, 4),
            DaKit.etiq(f, "Rareté"), DaKit.etiq(f, "Poids"), px, py);
    }

    private void drawDetailPanel(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        if (detail == null) detail = buildDetail(f);
        DetailCache d = detail;
        int px = d.px(), py = d.py();
        DaKit.blit(ctx, DETAIL, px - 4, py - 4, 0, 0, DETAIL_W + 8, DETAIL_H + 8, DETAIL_W + 8, DETAIL_H + 8);
        if (d.nomArcade()) DaKit.arcX1(ctx, f, d.nom(), px + 12, py + 11, DaKit.CREME, 0, true);
        else DaKit.inter9o(ctx, f, d.nom(), px + 12, py + 14.5f, DaKit.CREME, 0);
        if (selStack != null) ctx.item(selStack, px + DETAIL_W - 26, py + 6);

        int lx = px + 12, vx = px + 70;
        DaKit.etiq(ctx, f, d.eRarete(), lx, py + 40, DaKit.OR_ETIQ);
        DaKit.inter8(ctx, f, d.rarete(), vx, py + 40, d.rareteCol(), 0);
        ctx.fill(lx, py + 48, px + DETAIL_W - 12, py + 49, DaKit.SEP);
        DaKit.etiq(ctx, f, d.ePoids(), lx, py + 56, DaKit.OR_ETIQ);
        DaKit.inter8(ctx, f, d.poids(), vx, py + 56, DaKit.CREME_VAL, 0);
        ctx.fill(lx, py + 64, px + DETAIL_W - 12, py + 65, DaKit.SEP);
        d.desc().dessiner(ctx, f, lx, py + 71, DaKit.argb(0xE8DCC0, 0.78f));

        for (Btn b : panelButtons) {
            boolean hov = DaKit.dans(mx, my, b.x, b.y, b.w, 16);
            int row = (b.danger ? 2 : 0) + (hov ? 1 : 0);
            DaKit.coupeH(ctx, BOUTONS, b.x - 1, b.y - 1, b.w + 2, 0, row * 20, 104, 20, 6, 104, 80);
            DaKit.arcFin(ctx, f, b.label, b.x + b.w / 2f, b.y + 6.25f, hov ? DaKit.CREME : 0xFFD8CCB0, 1, true);
        }
    }

    private void buildPanelButtons() {
        panelButtons.clear();
        if (selRef == null) return;
        if (selRef.equals("CARME")) {
            panelButtons.add(new Btn("Dégainer", false, () -> { send("arme:basculer"); closePanel(); }));
            panelButtons.add(new Btn("Retirer", true, () -> { send("arme:unequip"); closePanel(); }));
            return;
        }
        if (selRef.charAt(0) == 'C') {
            final CosmeticSlot cs = CosmeticSlot.fromName(selRef.substring(1));
            final SlotItem cosmItem = cs != null ? snap.equipped().get(cs) : null;
            panelButtons.add(new Btn("Repositionner", false, () -> {
                Minecraft mc = Minecraft.getInstance();
                closePanel();
                if (cs == null) return;
                String id = fr.reborn.hud.cosmetic.CosmeticFeatureRenderer.cosmeticId(cs, cosmItem);
                fr.reborn.hud.cosmetic.CosmeticTransform.Anchor anchor =
                    fr.reborn.hud.cosmetic.CosmeticFeatureRenderer.anchorForSlot(cs);
                mc.setScreenAndShow(new fr.reborn.hud.menu.cosmetic.RepositionScreen(id, cs.label, anchor));
            }));
            panelButtons.add(new Btn("Retirer", true, () -> {
                if (cs != null) send("cos:unequip:" + cs.name());
                closePanel();
            }));
            return;
        }
        final String ref = selRef;
        String al = actionLabel(selStack, selMeta);
        if (al != null) panelButtons.add(new Btn(al, false, () -> { send("use:" + ref); closePanel(); }));
        if (isBagStack(selStack)) panelButtons.add(new Btn("Équiper", false, () -> { send("bag:equip:" + ref); closePanel(); }));
        SlotItem meta = metaAtAny(ref);
        if (meta != null && meta.arme) panelButtons.add(new Btn("À l'étui", false, () -> { send("arme:equip:" + ref); closePanel(); }));
        panelButtons.add(new Btn("Jeter", false, () -> { send("drop:" + ref); closePanel(); }));
        panelButtons.add(new Btn("Détruire", true, () -> { send("del:" + ref); closePanel(); }));
    }

    private void openPanel(String ref, ItemStack st, SlotItem meta, int ax, int ay) {
        selRef = ref; selStack = st; selMeta = meta;
        anchorX = ax; anchorY = ay;
        detail = null;
    }

    private void closePanel() {
        selRef = null; selStack = null; selMeta = null;
        panelButtons.clear();
        detail = null;
    }

    private int rarityColor(String r) {
        fr.reborn.hud.sol.Rarete x = fr.reborn.hud.sol.Rarete.parse(r);
        return x != null ? x.argb() : 0xFFE6B4A0;
    }

    private static float ease(float t) { return 1 - (float) Math.pow(1 - t, 3); }

    // ─────────── Géométrie ───────────
    private int bagX(int i) { return gridX + (i % COLS) * PITCH; }
    private int bagY(int i) { return gridY + (i / COLS) * PITCH; }

    private String slotAt(int mx, int my) {
        for (int i = 0; i < extraSlots; i++) {
            int x = bagX(i), y = bagY(i);
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) return "B" + i;
        }
        for (int i = 0; i < 9; i++) {
            int x = hotbarX(i), y = hotbarY;
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) return "H" + i;
        }
        return null;
    }

    private int slotRefX(String ref) { int i = idx(ref); return ref.charAt(0) == 'H' ? hotbarX(i) : bagX(i); }
    private int slotRefY(String ref) { int i = idx(ref); return ref.charAt(0) == 'H' ? hotbarY : bagY(i); }

    private CosmeticSlot cosmeticAt(int mx, int my) {
        if (tab != TAB_COSM) return null;
        for (CosmeticSlot cs : CosmeticSlot.values()) {
            if (!cs.cosmetique()) continue;
            int[] p = cosmPos(cs);
            if (DaKit.dans(mx, my, p[0], p[1], PLATE, PLATE)) return cs;
        }
        return null;
    }

    private boolean equipBagSlotAt(int mx, int my) {
        return tab == TAB_EQUIP && DaKit.dans(mx, my, cosmLeftX, colY0, PLATE, PLATE);
    }

    private int tierAt(int mx, int my) {
        if (mx < inroX || mx >= inroX + INRO_W + TIER_SLIDE) return -1;
        for (int i = 0; i < FILTER_NAME.length; i++) {
            int y = tierY0 + i * TIER_PITCH;
            if (my >= y && my < y + TIER_H) return i;
        }
        return -1;
    }

    // ─────────── Interactions ───────────
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        int mx = (int) e.x(), my = (int) e.y();
        pressPick = false;
        dragging = false;
        pressX = e.x();
        pressY = e.y();
        // Lecture seule (KO, porté, étourdi, scène de mort) : on regarde, on ferme ; rien ne bouge.
        if (lectureSeule()) {
            if (e.button() == 0 && inClose(mx, my)) { onClose(); return true; }
            if (e.button() == 0) {
                for (int i = 0; i < 2; i++) if (inTab(i, mx, my)) { tab = i == 0 ? TAB_EQUIP : TAB_COSM; return true; }
                int t = tierAt(mx, my);
                if (t >= 0) { filter = t; return true; }
            }
            if (slotAt(mx, my) != null || cosmeticAt(mx, my) != null || armeSlotAt(mx, my) || equipBagSlotAt(mx, my)) {
                RebornSounds.playReborn("sacoche.deny", 1.0f, 0.4f);
            }
            return true;
        }

        if (selRef != null && e.button() == 0) {
            for (Btn b : panelButtons) {
                if (DaKit.dans(mx, my, b.x, b.y, b.w, 16)) {
                    RebornSounds.playReborn("sacoche.place", 1.0f, 0.5f);
                    b.act.run();
                    return true;
                }
            }
            closePanel();
        }

        if (e.button() == 0) {
            if (DaKit.dans(mx, my, trayX + 12, trayY + 6, 124, 14)) {
                search.mouseClicked(e, dbl);
                setFocused(search);
                search.setFocused(true);
                return true;
            }
            if (inClose(mx, my)) { onClose(); return true; }
            for (int i = 0; i < 2; i++) {
                if (inTab(i, mx, my)) {
                    int nt = i == 0 ? TAB_EQUIP : TAB_COSM;
                    if (nt != tab) { tab = nt; RebornSounds.playReborn("sacoche.pick", 0.9f, 0.4f); }
                    return true;
                }
            }
            int t = tierAt(mx, my);
            if (t >= 0) {
                if (t != filter) { filter = t; RebornSounds.playReborn("sacoche.tier", 1.0f, 0.55f); }
                return true;
            }
            if (armeSlotAt(mx, my)) {
                if (carriedStack != null) {
                    send("arme:equip:" + carriedRef); clearCarried(); RebornSounds.playReborn("sacoche.place", 1.0f, 0.6f);
                } else RebornSounds.playReborn("sacoche.pick", 1.0f, 0.4f);
                return true;
            }
            if (teteSlotAt(mx, my)) {   // la tête se règle dans l'onglet Cosmétique
                tab = TAB_COSM; RebornSounds.playReborn("sacoche.pick", 0.9f, 0.4f);
                return true;
            }
            if (equipBagSlotAt(mx, my)) {
                if (carriedStack != null && isBagStack(carriedStack)) {
                    send("bag:equip:" + carriedRef); clearCarried(); RebornSounds.playReborn("sacoche.place", 1.0f, 0.6f);
                } else RebornSounds.playReborn("sacoche.pick", 1.0f, 0.4f);
                return true;
            }
            CosmeticSlot cs = cosmeticAt(mx, my);
            if (cs != null) {
                if (carriedStack != null) {
                    send("cos:equip:" + carriedRef + ":" + cs.name()); clearCarried(); RebornSounds.playReborn("sacoche.place", 1.0f, 0.6f);
                } else if (snap.equipped().get(cs) != null) {
                    send("cos:unequip:" + cs.name()); RebornSounds.playReborn("sacoche.pick", 1.0f, 0.5f);
                }
                return true;
            }
            String ref = slotAt(mx, my);
            if (ref != null) {
                boolean vide = carriedStack == null;
                handleSlotClick(ref);
                pressPick = vide && carriedStack != null;   // pris à l'appui : un glisser peut suivre
                return true;
            }
            if (carriedStack != null) { send("drop:" + carriedRef); clearCarried(); RebornSounds.playReborn("sacoche.deny", 1.2f, 0.45f); return true; }
            if (search.isFocused()) { search.setFocused(false); setFocused(null); }
            return true;
        }

        if (e.button() == 1) {
            String ref = slotAt(mx, my);
            if (ref != null && carriedStack == null) {
                ItemStack st = stackAt(ref);
                if (st != null && !st.isEmpty()) {
                    openPanel(ref, st, metaAt(ref), slotRefX(ref), slotRefY(ref));
                    RebornSounds.playReborn("sacoche.pick", 1.1f, 0.45f);
                }
                return true;
            }
            CosmeticSlot cs = cosmeticAt(mx, my);
            if (cs != null && snap.equipped().get(cs) != null) {
                SlotItem it = snap.equipped().get(cs);
                int[] p = cosmPos(cs);
                openPanel("C" + cs.name(), it.toStack(), it, p[0], p[1]);
                RebornSounds.playReborn("sacoche.pick", 1.1f, 0.45f);
                return true;
            }
            if (equipBagSlotAt(mx, my) && snap.bagItem() != null) { send("bag:unequip"); RebornSounds.playReborn("sacoche.pick", 1.0f, 0.5f); return true; }
            if (armeSlotAt(mx, my) && snap.equipped().get(CosmeticSlot.ARME) != null) {
                SlotItem it = snap.equipped().get(CosmeticSlot.ARME);
                openPanel("CARME", it.toStack(), it, cosmRightX, armeY());
                RebornSounds.playReborn("sacoche.pick", 1.1f, 0.45f);
                return true;
            }
            if (teteSlotAt(mx, my) && teteSlot() != null) {
                CosmeticSlot ts = teteSlot();
                SlotItem it = snap.equipped().get(ts);
                openPanel("C" + ts.name(), it.toStack(), it, cosmRightX, teteY());
                RebornSounds.playReborn("sacoche.pick", 1.1f, 0.45f);
                return true;
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) {
        if (pressPick && carriedStack != null && !dragging
            && Math.abs(e.x() - pressX) + Math.abs(e.y() - pressY) > 4) dragging = true;
        return super.mouseDragged(e, dx, dy);
    }

    /** Glisser : relâcher sur une case = poser ; hors de la fenêtre = lâcher au sol (maquette 01, aide n° 5). */
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        boolean glisse = dragging && carriedStack != null && e.button() == 0;
        pressPick = false;
        dragging = false;
        if (glisse) {
            int mx = (int) e.x(), my = (int) e.y();
            String ref = slotAt(mx, my);
            if (ref != null) {
                if (!ref.equals(carriedRef)) handleSlotClick(ref);
                return true;
            }
            if (armeSlotAt(mx, my)) {
                send("arme:equip:" + carriedRef); clearCarried(); RebornSounds.playReborn("sacoche.place", 1.0f, 0.6f);
                return true;
            }
            CosmeticSlot cs = cosmeticAt(mx, my);
            if (cs != null) {
                send("cos:equip:" + carriedRef + ":" + cs.name()); clearCarried(); RebornSounds.playReborn("sacoche.place", 1.0f, 0.6f);
                return true;
            }
            if (equipBagSlotAt(mx, my) && isBagStack(carriedStack)) {
                send("bag:equip:" + carriedRef); clearCarried(); RebornSounds.playReborn("sacoche.place", 1.0f, 0.6f);
                return true;
            }
            if (!dansFenetre(mx, my)) {
                send("drop:" + carriedRef); clearCarried(); RebornSounds.playReborn("sacoche.deny", 1.2f, 0.45f);
                return true;
            }
            clearCarried(); RebornSounds.playReborn("sacoche.place", 1.1f, 0.4f);   // relâché dans la fenêtre, hors case
            return true;
        }
        return super.mouseReleased(e);
    }

    /** La « fenêtre » de la sacoche : personnage et plaques, inrō, plateau et obi. */
    private boolean dansFenetre(int mx, int my) {
        int x0 = leftX - 6, x1 = trayX + TRAY_W + 10;
        int y0 = Math.min(colY0, trayY) - 10, y1 = Math.max(obiY + 34, feetY + 30) + 6;
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    private void handleSlotClick(String ref) {
        closePanel();
        boolean belt = ref.charAt(0) == 'H';
        if (carriedStack == null) {
            ItemStack st = stackAt(ref);
            if (st != null && !st.isEmpty()) { carriedRef = ref; carriedStack = st.copy(); RebornSounds.playReborn("sacoche.pick", 1.0f, 0.5f); }
        } else if (ref.equals(carriedRef)) {
            clearCarried(); RebornSounds.playReborn("sacoche.place", 1.1f, 0.4f);
        } else {
            send("swap:" + carriedRef + ":" + ref); clearCarried();
            RebornSounds.playReborn(belt ? "sacoche.belt" : "sacoche.place", 1.0f, 0.6f);
        }
    }

    private void clearCarried() { carriedRef = null; carriedStack = null; }

    private void send(String cmd) {
        if (ClientPlayNetworking.canSend(InventoryPayload.ID)) ClientPlayNetworking.send(new InventoryPayload(cmd));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (search.isFocused()) { search.setFocused(false); return true; }
            if (selRef != null) { closePanel(); return true; }
            if (carriedStack != null) { clearCarried(); RebornSounds.playReborn("sacoche.place", 1.1f, 0.4f); return true; }
            onClose();
            return true;
        }
        if (search.isFocused()) {
            if (search.keyPressed(e)) return true;
            return super.keyPressed(e);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.keyInventory.matches(e)) {
            if (carriedStack != null) { clearCarried(); RebornSounds.playReborn("sacoche.place", 1.1f, 0.4f); return true; }
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (search != null && search.isFocused() && search.charTyped(event)) return true;
        return super.charTyped(event);
    }
}
