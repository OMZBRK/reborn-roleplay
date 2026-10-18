package fr.reborn.hud.parchemin;

import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bibliothèque, côté joueur (bibliothèques v2, docs/gameplay/APPRENTISSAGE.md §3). Motif de l'écran : un meuble à
 * casiers (tansu) de bois sombre, linteau laqué au cordon d'or, chaque rouleau (modèle 3D SNR) couché dans son casier,
 * son étiquette de rang (fuda) pendue au coin. Deux étagères, deux onglets pendus à une perche de bambou :
 * <b>Fonds</b> (rouleaux déposés, permanents) et <b>Arrivages</b> (tirage au sort). En-tête : nom, type et
 * propriétaire ; à droite, le sceau au cadenas quand la bibliothèque est réservée à ses membres (survol : explication). Un
 * non-membre voit sans pouvoir prendre (petit cadenas sur chaque rouleau) ; si la bibliothèque le permet, il peut
 * <b>voler</b> : clic = fouille de quelques secondes (barre vermillon sur le casier, le reste de l'étagère s'éteint),
 * immobile, Échap pour renoncer. Dépôt : bouton « Déposer » sous le meuble sur toutes les bibliothèques (grisé, avec
 * la raison, quand on ne peut pas), ou clic sur une case vide du fonds, puis choix d'un rouleau de la sacoche.
 * Journal pour le propriétaire et le staff (aucun kanji depuis le 18/10, DA §3).
 * Habillage : textures de {@code tools/gen_bibliotheque_textures.py} (cf. {@link BiblioArt}).
 */
public final class BibliothequeScreen extends Screen {

    private static final int COLS = 3, ROWS = 3, CARTE_W = 168, ECART = 14;
    public static final int FONDS = 0, ARRIVAGES = 1;

    /** Identifiant serveur ({@code null} = démo du banc d'essai, sans réseau). */
    private String libId;
    private String name;
    private final Technique[] cells = new Technique[COLS * ROWS];
    private final Technique[] fonds = new Technique[COLS * ROWS];
    private final String[] fondsBy = new String[COLS * ROWS];
    private final String[] fondsStolen = new String[COLS * ROWS];
    private int count;
    private String refresh;
    private double weight = 6.4;
    private double maxWeight = 12.0;
    private boolean staff;

    // ─── v2 (champs optionnels : un serveur v1 n'envoie que les arrivages) ───
    private boolean v2;
    private String typeLabel = "", owner = "", openFor;
    private boolean membres, member = true, canTake = true, canDeposit, draws = true, onsite, journal;
    /** Vol (13/10, optionnel) : permis pour ce joueur, durée par rang D..S (s) ; raison quand on ne peut pas déposer. */
    private boolean canSteal;
    private double[] stealSecs;
    private String depositWhy = "";
    /** Fouille en cours : case, étagère, début et durée (ms) ; -1 = aucune. */
    private int stealCell = -1, stealTab;
    private long stealStart, stealMs;
    private boolean depositTip;
    /** Banc d'essai : bulle « pourquoi je ne peux pas déposer » forcée. */
    public boolean debugDepositTip;
    private record Sac(String ref, Technique t) {}
    private List<Sac> bag = List.of();
    private int tab = ARRIVAGES;
    private boolean tabChosen;

    // ─── calques : choix du rouleau à déposer, journal ───
    private boolean picking;
    private int pickCell = -1;
    private record Ligne(String when, String who, String act, String shelf, String tech, char rank) {}
    private List<Ligne> log;
    private int logScroll;

    private String toast;
    private long toastAt;
    /** Banc d'essai : case survolée imposée (-1 = souris), sceau survolé, ligne du choix survolée. */
    public int debugHover = -1;
    public boolean debugSeal;
    public int debugPick = -1;

    private record Hit(int x, int y, int w, int h, Runnable r) {}
    private final List<Hit> hits = new ArrayList<>();

    public BibliothequeScreen(String name, List<Technique> drawn, int count, String refresh) {
        super(Component.literal(name));
        this.name = name;
        this.count = count;
        this.refresh = refresh;
        // les rouleaux occupent des cases au hasard parmi les « count » cases ouvertes
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) slots.add(i);
        java.util.Collections.shuffle(slots, new java.util.Random(drawn.size() * 31L + count));
        for (int i = 0; i < drawn.size() && i < slots.size(); i++) cells[slots.get(i)] = drawn.get(i);
    }

    /** Bibliothèque reçue du serveur ({@code {"t":"lib"}}). */
    public static BibliothequeScreen fromJson(JsonObject o) {
        BibliothequeScreen s = new BibliothequeScreen(o.get("name").getAsString(), List.of(), 0, "");
        s.update(o);
        return s;
    }

    public String libId() { return libId; }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def;
    }

    /** Nouvel état envoyé par le serveur (après une prise, un dépôt, un tirage…). */
    public void update(JsonObject o) {
        libId = o.get("id").getAsString();
        name = o.get("name").getAsString();
        count = o.get("count").getAsInt();
        refresh = o.get("refreshIn").getAsString();
        weight = o.get("weight").getAsDouble();
        maxWeight = o.get("maxWeight").getAsDouble();
        staff = bool(o, "staff", false);
        var arr = o.getAsJsonArray("cells");
        for (int i = 0; i < cells.length; i++) {
            cells[i] = i < arr.size() && arr.get(i).isJsonObject() ? Technique.fromJson(arr.get(i).getAsJsonObject()) : null;
        }
        v2 = o.has("fonds");
        if (!v2) { tab = ARRIVAGES; return; }
        typeLabel = str(o, "typeLabel");
        owner = str(o, "owner");
        membres = "membres".equals(str(o, "access"));
        member = bool(o, "member", true);
        canTake = bool(o, "canTake", true);
        canDeposit = bool(o, "canDeposit", false);
        draws = bool(o, "draws", true);
        onsite = bool(o, "onsite", false);
        journal = bool(o, "journal", false);
        openFor = o.has("openFor") ? str(o, "openFor") : null;
        var fa = o.getAsJsonArray("fonds");
        for (int i = 0; i < fonds.length; i++) {
            boolean ok = i < fa.size() && fa.get(i).isJsonObject();
            fonds[i] = ok ? Technique.fromJson(fa.get(i).getAsJsonObject()) : null;
            fondsBy[i] = ok ? str(fa.get(i).getAsJsonObject(), "by") : null;
            fondsStolen[i] = ok ? str(fa.get(i).getAsJsonObject(), "stolen") : null;
        }
        canSteal = bool(o, "canSteal", false);
        stealSecs = null;
        if (o.has("stealSecs")) {
            var ss = o.getAsJsonArray("stealSecs");
            stealSecs = new double[5];
            for (int i = 0; i < 5 && i < ss.size(); i++) stealSecs[i] = ss.get(i).getAsDouble();
        }
        depositWhy = str(o, "depositWhy");
        List<Sac> b = new ArrayList<>();
        if (o.has("bag")) for (var e : o.getAsJsonArray("bag")) {
            JsonObject j = e.getAsJsonObject();
            b.add(new Sac(str(j, "ref"), Technique.fromJson(j)));
        }
        bag = b;
        if (!tabChosen) {
            tab = !draws || fondsCount() > 0 ? FONDS : ARRIVAGES;
            tabChosen = true;
        }
        if (!draws) tab = FONDS;
        if (picking && (!canDeposit || freeCell() < 0)) picking = false;
        if (pickCell >= 0 && fonds[pickCell] != null) pickCell = -1;
    }

    /** Journal reçu du serveur ({@code {"t":"lib_log"}}). */
    public void showLog(JsonObject o) {
        List<Ligne> l = new ArrayList<>();
        for (var e : o.getAsJsonArray("entries")) {
            JsonObject j = e.getAsJsonObject();
            String r = str(j, "rank");
            l.add(new Ligne(str(j, "when"), str(j, "who"), str(j, "act"), str(j, "shelf"), str(j, "tech"),
                r.isEmpty() ? 'D' : r.charAt(0)));
        }
        log = l;
        logScroll = 0;
        picking = false;
    }

    /** Fouille d'un voleur ({@code {"t":"steal"}}) : {@code start} (case, durée), {@code fail} ou {@code done}. */
    public void stealState(JsonObject o) {
        String st = str(o, "state");
        if (st.equals("start")) {
            stealCell = o.has("slot") ? o.get("slot").getAsInt() : -1;
            stealTab = "fonds".equals(str(o, "shelf")) ? FONDS : ARRIVAGES;
            tab = stealTab;
            stealStart = System.currentTimeMillis();
            stealMs = o.has("ms") ? o.get("ms").getAsLong() : 6000;
            picking = false;
            log = null;
        } else {
            stealCell = -1;
        }
    }

    /** Banc d'essai : fouille en cours sur la case {@code cell}, déjà avancée de {@code done} (0..1). */
    public void debugSteal(int cell, long ms, float done) {
        stealCell = cell;
        stealTab = tab;
        stealMs = ms;
        stealStart = System.currentTimeMillis() - Math.round(ms * done);
    }

    private boolean stealing() { return stealCell >= 0; }

    /** Durée de la fouille d'un rouleau de ce rang (s), ou -1 si inconnue. */
    private double stealSecsOf(char r) {
        if (stealSecs == null) return -1;
        int i = r == 'E' ? 0 : "DCBAS".indexOf(r);
        return i < 0 ? -1 : stealSecs[i];
    }

    private String whyNoDeposit() {
        return depositWhy.isEmpty() ? "Tu ne peux pas déposer dans cette bibliothèque." : depositWhy;
    }

    public void toast(String msg) {
        toast = msg;
        toastAt = System.currentTimeMillis();
    }

    public void setTab(int t) { tab = draws || !v2 ? t : FONDS; }

    public void openPicker(int cell) {
        if (!canDeposit) return;
        picking = true;
        pickCell = cell;
        log = null;
    }

    private int fondsCount() {
        int n = 0;
        for (Technique t : fonds) if (t != null) n++;
        return n;
    }

    private int freeCell() {
        for (int i = 0; i < fonds.length; i++) if (fonds[i] == null) return i;
        return -1;
    }

    private Technique[] shelf() { return tab == FONDS ? fonds : cells; }

    private int open() { return tab == FONDS ? fonds.length : count; }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) {
        BiblioArt.fond(g, width, height, 0.62f);
    }

    /**
     * Meuble centré ; si la fiche n'a pas la place à sa droite, c'est l'ensemble meuble + fiche qui est centré
     * (mise en page stable : elle ne bouge pas au survol).
     */
    private int cabX() {
        int mw = BiblioArt.MEUBLE_W - 4;
        int centered = (width - mw) / 2;
        if (centered + mw + ECART + CARTE_W <= width - 8) return centered;
        int group = mw + ECART + CARTE_W;
        return group <= width - 12 ? (width - group) / 2 : centered;
    }

    /** Haut du meuble : sous l'en-tête, et sous la perche des onglets en v2. */
    private int cabY() {
        int top = v2 ? 80 : 62;
        return top + Math.max(0, (height - top - 64 - BiblioArt.MEUBLE_H) / 3);
    }

    private int cellX(int i) { return cabX() + 24 + (i % COLS) * 110; }

    private int cellY(int i) { return cabY() + 44 + (i / COLS) * 68; }

    private int hovered(int mx, int my) {
        if (debugHover >= 0) return debugHover;
        if (picking || log != null || stealing()) return -1;
        for (int i = 0; i < cells.length; i++) if (DaKit.dans(mx, my, cellX(i), cellY(i), BiblioArt.CW, BiblioArt.CH)) return i;
        return -1;
    }

    private static String typeLine(Technique t) { return t.typeLine(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        hits.clear();
        String sous = !v2 ? "Nouveau tirage dans " + refresh
                : typeLabel + (owner.isEmpty() ? "" : " · " + owner);
        BiblioArt.entete(g, font, width, DaKit.arcade(name), DaKit.inter(sous));

        int x = cabX(), y = cabY();
        DaKit.blit(g, BiblioArt.MEUBLE, x, y, 0, 0, BiblioArt.MEUBLE_W, BiblioArt.MEUBLE_H, BiblioArt.MEUBLE_W, BiblioArt.MEUBLE_H);
        if (v2) bandeau(g, x, y, mx, my);

        Technique[] shelf = shelf();
        int open = open();
        int hov = hovered(mx, my);
        if (hov != lastHov) {
            if (hov >= 0 && (shelf[hov] != null || (tab == FONDS && canDeposit))) RebornSounds.playReborn("esc.hover", 1.1f, 0.10f);
            lastHov = hov;
        }
        // casiers (un lot), puis rouleaux, étiquettes, cadenas et « + Déposer » (un lot par texture) : DA §6, règle 7
        LotBlit casiers = LotBlit.ouvrir(g, BiblioArt.CASIERS, 416, 62);
        for (int i = 0; i < cells.length; i++) {
            int cx = cellX(i), cy = cellY(i);
            Technique t = shelf[i];
            int v = i >= open ? 2 : t == null ? 1 : 0;
            casiers.blit(cx, cy, v * BiblioArt.CW, 0, BiblioArt.CW, BiblioArt.CH);
            if (t != null ? i == hov : deposerIci(i, hov)) casiers.blit(cx, cy, 3 * BiblioArt.CW, 0, BiblioArt.CW, BiblioArt.CH);
        }
        casiers.fermer();
        LotBlit fudas = BiblioArt.fudas(g), cadenas = BiblioArt.acces(g);
        BiblioArt.Txt deposer = new BiblioArt.Txt(g, font);
        for (int i = 0; i < cells.length; i++) {
            int cx = cellX(i), cy = cellY(i);
            Technique t = shelf[i];
            if (t != null) {
                int sz = 50, lift = i == hov ? 2 : 0;
                ScrollArt.item(g, cx + (BiblioArt.CW - sz) / 2 - 4, cy + 7 - lift, sz, t.rank());
                BiblioArt.fuda(fudas, cx + BiblioArt.CW - 17, cy, t.rank());
                if (v2 && !canTake && !(stealing() && i == stealCell)) BiblioArt.cadenas(cadenas, cx + 6, cy + 5, false);
            } else if (deposerIci(i, hov)) {
                deposer.arcFin(DaKit.arcade("+ Deposer"), cx + BiblioArt.CW / 2f, cy + BiblioArt.CH / 2f - 6, DaKit.OR_CLAIR, 1, true);
            }
        }
        fudas.fermer();
        cadenas.fermer();
        deposer.fermer();

        if (stealing()) fouille(g, now);
        if (hov >= 0 && shelf[hov] != null) card(g, shelf[hov], hov);

        // sacoche, et le bouton « Déposer » sous le meuble (v2 : toujours visible, grisé avec la raison si interdit)
        int gx = v2 ? x + 24 : x + 186 - 100, gy = y + BiblioArt.MEUBLE_H + 4;
        String wt = String.format(Locale.FRANCE, "%.1f / %.0f kg", weight, maxWeight);
        if (stealing()) {
            DaKit.inter8(g, font, DaKit.inter("Ne bouge pas : un pas, un coup, et la fouille échoue."),
                x + BiblioArt.MEUBLE_W / 2f - 2, gy + 10, DaKit.argb(0xE8DCC0, 0.85f), 1);
        } else {
            BiblioArt.jauge(g, font, gx, gy, (float) (weight / maxWeight), SACOCHE, DaKit.inter(wt));
        }
        depositTip = false;
        if (v2 && !stealing()) {
            int bw = 112, bx = x + BiblioArt.MEUBLE_W - 30 - bw, by = gy + 5;
            boolean full = canDeposit && freeCell() < 0, ok = canDeposit && !full;
            boolean hv = DaKit.dans(mx, my, bx, by, bw, 14) && !picking && log == null;
            BiblioArt.plaque(g, font, bx, by, bw, 14, full ? "Fonds plein" : "Deposer", picking, ok && hv, ok);
            if (ok) hits.add(new Hit(bx, by, bw, 14, () -> { RebornSounds.uiClick(); tab = FONDS; openPicker(-1); }));
            else hits.add(new Hit(bx, by, bw, 14, () -> toast(full ? "Le fonds est plein : 9 rouleaux au plus." : whyNoDeposit())));
            depositTip = !canDeposit && (hv || debugDepositTip);
        }

        List<String[]> aide = new ArrayList<>();
        if (stealing()) {
            aide.add(new String[]{"Echap", "renoncer"});
        } else if (picking) {
            aide.add(new String[]{"Clic", "déposer ce rouleau"});
            aide.add(new String[]{"Echap", "revenir"});
        } else if (log != null) {
            aide.add(new String[]{"Molette", "défiler"});
            aide.add(new String[]{"Echap", "revenir"});
        } else {
            if (canTake || !v2) aide.add(new String[]{"Clic", "prendre le rouleau"});
            else if (canSteal) aide.add(new String[]{"Clic", "voler le rouleau"});
            if (v2 && tab == FONDS && onsite) aide.add(new String[]{"Clic droit", "lire sur place"});
            if (v2 && draws) aide.add(new String[]{"Tab", "étagère"});
            aide.add(new String[]{"Echap", "fermer"});
        }
        BiblioArt.aides(g, font, width, height, aide.toArray(new String[0][]));

        // boutons suspendus en haut à droite : réglages (staff, picto roue), journal (propriétaire, staff, picto registre)
        int bx = width - 40;
        if (staff) {
            boolean hb = DaKit.dans(mx, my, bx + 2, 12, 22, 22);
            BiblioArt.bouton26(g, bx, 0, hb);
            BiblioArt.etiq(g, font, REGLAGES, bx + 13 - REGLAGES.w() / 2f, 41, hb ? DaKit.OR_CLAIR : DaKit.OR_ETIQ);
            hits.add(new Hit(bx + 2, 12, 22, 22, () -> ParcheminClient.send("cfg_open", libId, null)));
            bx -= 46;
        }
        if (v2 && journal) {
            boolean hb = DaKit.dans(mx, my, bx + 2, 12, 22, 22);
            BiblioArt.bouton26(g, bx, 1, hb);
            BiblioArt.etiq(g, font, JOURNAL, bx + 13 - JOURNAL.w() / 2f, 41, hb ? DaKit.OR_CLAIR : DaKit.OR_ETIQ);
            hits.add(new Hit(bx + 2, 12, 22, 22, () -> {
                if (libId != null) ParcheminClient.send("log", libId, null);
                else log = List.of();
            }));
        }

        if (picking) picker(g, mx, my);
        else if (log != null) journal(g, mx, my);
        if (v2 && membres && (debugSeal || sealHover(mx, my)) && !picking && log == null && !stealing()) sealTip(g);
        if (depositTip) depositTip(g, gy);

        if (toast != null && now - toastAt < 2200 && log == null) {
            float a = Math.min(1f, (2200 - (now - toastAt)) / 400f);
            var c = DaKit.inter(toast);
            int tw = Math.round(DaKit.largeur8(font, c)) + 28, tx = width / 2 - tw / 2, ty = y + 34;
            DaKit.coupeH(g, BiblioArt.TOAST, tx, ty, tw + 4, 0, 0, 264, 20, 8, 264, 20);
            DaKit.inter8(g, font, c, width / 2f, ty + 8, DaKit.alpha(DaKit.CREME, a), 1);
        }
        super.extractRenderState(g, mx, my, delta);
    }

    /** Case vide du fonds qui propose le dépôt (survolée, ou visée par le dépôt en cours). */
    private boolean deposerIci(int i, int hov) {
        return tab == FONDS && v2 && canDeposit && (i == hov || (picking && i == targetCell()));
    }

    /** Case du fonds visée par le dépôt en cours. */
    private int targetCell() { return pickCell >= 0 && fonds[pickCell] == null ? pickCell : freeCell(); }

    // ─────────────────────────────────────────────────────────────── bandeau : onglets, état, sceau

    private int sealX() { return cabX() + BiblioArt.MEUBLE_W - 34; }

    private int sealY() { return cabY() - 17; }

    private boolean sealHover(int mx, int my) { return DaKit.dans(mx, my, sealX() - 12, sealY() - 12, 24, 24); }

    /** Perche et onglets pendus à gauche ; à droite, l'état de l'étagère et le sceau au cadenas (accès « membres »). */
    private void bandeau(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        int py = y - 32, ty = y - 21;
        int tx = x + 22;
        int[] w = new int[2];
        String[] lib = {"Fonds", "Arrivages"};
        int[] n = {fondsCount(), 0};
        for (Technique t : cells) if (t != null) n[1]++;
        int nb = draws ? 2 : 1;
        int total = 0;
        for (int i = 0; i < nb; i++) {
            float lw = DaKit.largeurFin(font, DaKit.arcade(lib[i])), nw = DaKit.largeurFin(font, DaKit.arcade(String.valueOf(n[i])));
            w[i] = Math.round(lw + nw) + 26;
            total += w[i] + (i > 0 ? 8 : 0);
        }
        // perche, fils, onglets et libellés : quatre éléments GUI en tout (DA §6, règle 7)
        BiblioArt.perche(g, x + 10, py, total + 26);
        LotGui fils = LotGui.ouvrir(g);
        int cx = tx;
        for (int i = 0; i < nb; i++) {
            BiblioArt.fil(fils, cx + w[i] / 2, py + 5, ty + (tab == i ? 2 : 0));
            cx += w[i] + 8;
        }
        fils.fermer();
        LotBlit onglets = BiblioArt.onglets(g);
        BiblioArt.Txt libs = new BiblioArt.Txt(g, font);
        cx = tx;
        for (int i = 0; i < nb; i++) {
            boolean act = tab == i, hv = !act && DaKit.dans(mx, my, cx, ty, w[i], 16) && !picking && log == null;
            int dy = act ? 2 : 0;
            BiblioArt.onglet(onglets, cx, ty + dy, w[i], act ? 2 : hv ? 1 : 0);
            Component c = DaKit.arcade(lib[i]), k = DaKit.arcade(String.valueOf(n[i]));
            float yy = ty + dy + 8 - 1.75f;
            libs.arcFin(c, cx + 9, yy, act || hv ? DaKit.CREME : 0xFFD8CCB0, 0, act);
            libs.arcFin(k, cx + 9 + DaKit.largeurFin(font, c) + 8, yy, act ? DaKit.OR_CLAIR : DaKit.OR, 0, false);
            final int t = i;
            hits.add(new Hit(cx, ty, w[i], 18, () -> { if (tab != t) { tab = t; RebornSounds.uiClick(); } }));
            cx += w[i] + 8;
        }
        onglets.fermer();
        // état de l'étagère, calé à droite (avant le sceau)
        int right = membres ? sealX() - 20 : x + BiblioArt.MEUBLE_W - 14;
        String etat = tab == FONDS
            ? fondsCount() + " / " + fonds.length + " rouleaux deposes"
            : "Nouveau tirage dans " + refresh;
        libs.inter7o(DaKit.inter(etat), right, ty + 8, DaKit.argb(0xE8DCC0, 0.78f), 2);
        if (membres) {
            boolean ouvert = member || openFor != null;
            BiblioArt.sceauAcces(g, sealX(), sealY(), ouvert);
            DaKit.Etiq et = openFor != null ? OUVERTE : MEMBRES;
            libs.etiq(et, right - et.w(), ty - 2, ouvert ? DaKit.OR_ETIQ : 0xFFE0705A);
        }
        libs.fermer();
    }

    /** Survol du sceau : qui peut prendre ici. */
    private void sealTip(GuiGraphicsExtractor g) {
        String titre = openFor != null ? "Ouverte" : "Reservee";
        String qui = switch (typeLabel) {
            case "Clan" -> "aux membres du clan" + (owner.isEmpty() ? "" : " " + owner);
            case "Académie" -> "aux élèves et shinobis du village" + (owner.isEmpty() ? "" : " de " + owner);
            case "ANBU" -> "aux membres de l'ANBU";
            case "Palais du Kage" -> "au Kage, à son conseil et à son bras droit";
            case "Hôpital" -> "aux médecins de l'hôpital";
            case "Personnelle" -> "à " + (owner.isEmpty() ? "son propriétaire" : owner) + " et à ceux qu'il autorise";
            default -> "aux shinobis du village" + (owner.isEmpty() ? "" : " de " + owner);
        };
        String txt = openFor != null
            ? "Le staff a ouvert l'accès pour encore " + openFor + " : chacun peut emporter un rouleau."
            : member ? "Ses rouleaux sont réservés " + qui + ". Tu en fais partie : tu peux les emporter."
            : "Ses rouleaux sont réservés " + qui + ". Tu peux les regarder, pas les emporter.";
        int w = CARTE_W, in = 12;
        DaKit.Paragraphe p = DaKit.paragraphe(font, txt, w - 2 * in, DaKit.S8, 10, 5);
        int h = 30 + p.hauteur() + 10;
        int x = Math.min(width - w - 8, sealX() - w + 20), y = sealY() + 16;
        DaKit.coupe(g, BiblioArt.CARTE, x - 2, y - 2, w + 8, h + 8, 0, 0, 176, 300, 8, 16, 176, 300);
        BiblioArt.cadenas(g, x + in, y + 11, member || openFor != null);
        DaKit.arcX1(g, font, DaKit.arcade(titre), x + in + 14, y + 13, member || openFor != null ? DaKit.ENCRE : DaKit.ENCRE_ROUGE, 0, false);
        BiblioArt.trait(g, x + in - 2, y + 24, w - 2 * in);
        p.dessiner(g, font, x + in, y + 32, DaKit.ENCRE);
    }

    private static final DaKit.Etiq SACOCHE = DaKit.etiq(net.minecraft.client.Minecraft.getInstance().font, "Sacoche"),
        REGLAGES = DaKit.etiq(net.minecraft.client.Minecraft.getInstance().font, "Reglages"),
        JOURNAL = DaKit.etiq(net.minecraft.client.Minecraft.getInstance().font, "Journal"),
        MEMBRES = DaKit.etiq(net.minecraft.client.Minecraft.getInstance().font, "Reserve aux membres"),
        OUVERTE = DaKit.etiq(net.minecraft.client.Minecraft.getInstance().font, "Ouverte a tous");
    private int lastHov = -1;

    // ─────────────────────────────────────────────────────────────── vol : fouille en cours

    private static String secondes(double s) {
        return s == Math.floor(s) ? String.format(Locale.FRANCE, "%.0f s", s) : String.format(Locale.FRANCE, "%.1f s", s);
    }

    /**
     * Fouille en cours : les autres casiers s'éteignent, une barre vermillon court sous le rouleau visé, le temps
     * restant au-dessus. Quelques {@code fill} par image (DA §6 : seul ce qui bouge est dessiné).
     */
    private void fouille(GuiGraphicsExtractor g, long now) {
        int open = open();
        for (int i = 0; i < cells.length; i++) {
            if (i == stealCell) continue;
            int cx = cellX(i), cy = cellY(i);
            g.fill(cx + 2, cy + 2, cx + BiblioArt.CW - 2, cy + BiblioArt.CH - 2, i < open ? 0xA0080406 : 0x60080406);
        }
        int cx = cellX(stealCell), cy = cellY(stealCell);
        float f = stealMs <= 0 ? 1f : Math.min(1f, (now - stealStart) / (float) stealMs);
        int bx = cx + 10, bw = BiblioArt.CW - 20, by = cy + BiblioArt.CH - 10;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 5, 0xFF120608);
        g.fill(bx, by, bx + bw, by + 4, 0xFF2D181C);
        int fw = Math.round(bw * f);
        g.fill(bx, by, bx + fw, by + 4, 0xFFB8322A);
        g.fill(bx, by, bx + fw, by + 1, 0xFFE0705A);
        if (fw > 0 && fw < bw) g.fill(bx + fw - 1, by - 1, bx + fw, by + 5, 0xFFF0C8A0);
        long left = Math.max(0, stealMs - (now - stealStart));
        String lib = left > 0 ? "Fouille… " + secondes(Math.ceil(left / 100.0) / 10.0) : "Fouille…";
        DaKit.inter7o(g, font, DaKit.inter(lib), cx + BiblioArt.CW / 2f, cy - 3, DaKit.argb(0xF0C8A0, 0.95f), 1);
    }

    /** Bulle au-dessus du bouton « Déposer » grisé : pourquoi on ne peut pas déposer ici. */
    private void depositTip(GuiGraphicsExtractor g, int gy) {
        int w = CARTE_W + 40, in = 12;
        DaKit.Paragraphe p = DaKit.paragraphe(font, whyNoDeposit(), w - 2 * in, DaKit.S8, 10, 4);
        int h = 26 + p.hauteur() + 8;
        int bx = cabX() + BiblioArt.MEUBLE_W - 30 - 112;
        int x = Math.max(6, Math.min(width - w - 8, bx + 112 - w)), y = gy - h - 2;
        DaKit.coupe(g, BiblioArt.CARTE, x - 2, y - 2, w + 8, h + 8, 0, 0, 176, 300, 8, 16, 176, 300);
        BiblioArt.cadenas(g, x + in, y + 9, false);
        DaKit.arcX1(g, font, DaKit.arcade("Depot reserve"), x + in + 14, y + 11, DaKit.ENCRE_ROUGE, 0, false);
        p.dessiner(g, font, x + in, y + 24, DaKit.ENCRE);
    }

    // ─────────────────────────────────────────────────────────────── fiche du rouleau

    /** Fiche du rouleau survolé, sur washi : à droite du meuble, sinon à gauche, sinon sur le casier. */
    private void card(GuiGraphicsExtractor g, Technique t, int cell) {
        int w = CARTE_W, in = 12, iw = w - 2 * in;
        boolean fondsShelf = v2 && tab == FONDS;
        String by = fondsShelf ? fondsBy[cell] : null;
        String vole = fondsShelf ? fondsStolen[cell] : null;
        boolean lire = fondsShelf && onsite && t.rank() != 'S';
        // nom (ArcadePix ×1, une ou deux lignes), description, puis les lignes de détail
        List<Component> nom = lignesArcade(t.name(), iw - 32);
        DaKit.Paragraphe desc = DaKit.paragraphe(font, t.desc(), iw, DaKit.S8, 10, 6);
        int yNom = 16, yType = yNom + nom.size() * 10 + 5, yTrait = yType + 9, yDesc = yTrait + 9;
        int metas = 2 + (by != null && !by.isEmpty() ? 1 : 0) + (vole != null && !vole.isEmpty() ? 1 : 0);
        int yMeta = yDesc + desc.hauteur() + 8, h = yMeta + metas * 13 + 20 + (lire ? 13 : 0);
        int mw = BiblioArt.MEUBLE_W - 4;
        int x;
        if (cabX() + mw + ECART + w <= width - 6) x = cabX() + mw + ECART;
        else if (cabX() - ECART - w >= 6) x = cabX() - ECART - w;
        else x = Math.max(6, Math.min(width - w - 6, cellX(cell) + BiblioArt.CW + 4));
        int y = Math.max(56, Math.min(cellY(cell) - 10, height - 30 - h));
        DaKit.coupe(g, BiblioArt.CARTE, x - 2, y - 2, w + 8, h + 8, 0, 0, 176, 300, 8, 16, 176, 300);
        BiblioArt.brocart(g, x, y, w, t.rank());
        BiblioArt.sceau26(g, x + w - in - 12, y + 26, t.rank());
        BiblioArt.trait(g, x + in - 2, y + yTrait, iw - 28);
        // tous les textes de la fiche en un seul élément GUI (aucun ne chevauche les pièces posées entre-temps)
        BiblioArt.Txt tx = new BiblioArt.Txt(g, font);
        for (int i = 0; i < nom.size(); i++) tx.arcX1(nom.get(i), x + in, y + yNom + i * 10, DaKit.ENCRE, 0, false);
        tx.inter7(DaKit.inter(typeLine(t)), x + in, y + yType, DaKit.ENCRE_DOUCE, 0);
        tx.paragraphe(desc, x + in, y + yDesc, DaKit.ENCRE);
        LotGui filets = LotGui.ouvrir(g);
        ligne(tx, filets, x + in, y + yMeta, "Rang", ScrollArt.rankName(t.rank()).replace("Rang ", "") + " · " + t.difficulty());
        ligne(tx, filets, x + in, y + yMeta + 13, "Seances", t.seances() + " · " + String.format(Locale.FRANCE, "%.1f kg", t.weight()));
        int ym = y + yMeta + 26;
        if (by != null && !by.isEmpty()) { ligne(tx, filets, x + in, ym, "Depose par", by); ym += 13; }
        if (vole != null && !vole.isEmpty()) ligne(tx, filets, x + in, ym, "Vole a", vole);
        float yb = y + h - 11 - (lire ? 13 : 0);
        if (!v2 || canTake) {
            int cw = BiblioArt.cabochon(g, font, "Clic", x + in, yb, 1f);
            tx.inter8(DaKit.inter("prendre le rouleau"), x + in + cw + 4, yb, DaKit.ENCRE_ROUGE, 0);
        } else if (canSteal) {
            int cw = BiblioArt.cabochon(g, font, "Clic", x + in, yb, 1f);
            double sec = stealSecsOf(t.rank());
            String lib = sec > 0 ? "voler · " + secondes(sec) + " immobile" : "voler le rouleau";
            tx.inter8(DaKit.inter(lib), x + in + cw + 4, yb, DaKit.ENCRE_ROUGE, 0);
        } else {
            BiblioArt.cadenas(g, x + in, Math.round(yb) - 6, false);
            tx.inter8(DaKit.inter("réservé aux membres"), x + in + 13, yb, DaKit.ENCRE_ROUGE, 0);
        }
        if (lire) {
            int cw = BiblioArt.cabochon(g, font, "Clic droit", x + in, yb + 13, 1f);
            tx.inter8(DaKit.inter("lire sur place"), x + in + cw + 4, yb + 13, DaKit.ENCRE, 0);
        }
        tx.fermer();
        filets.fermer();                                   // les filets passent sur les jambages, comme avant
    }

    /** Ligne de détail de la fiche : étiquette, valeur (lot de textes) et filet (lot d'aplats). */
    private void ligne(BiblioArt.Txt tx, LotGui filets, int x, int y, String etiq, String val) {
        DaKit.Etiq e = BiblioArt.etiq(font, etiq);
        tx.etiq(e, x, y, DaKit.ENCRE_DOUCE);
        tx.inter8(DaKit.inter(val), x + Math.max(52, Math.round(e.w()) + 8), y, DaKit.ENCRE, 0);
        filets.fill(x, y + 6, x + CARTE_W - 24, y + 7, 0x30462810);
    }

    /** Coupe un titre ArcadePix ×1 en lignes de largeur max (GUI), deux au plus. */
    private List<Component> lignesArcade(String s, int max) {
        List<Component> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String mot : DaKit.sansAccents(s).split(" +")) {
            String cand = cur.isEmpty() ? mot : cur + " " + mot;
            if (!cur.isEmpty() && font.width(DaKit.arcade(cand)) > max && out.size() < 1) {
                out.add(DaKit.arcade(cur.toString()));
                cur = new StringBuilder(mot);
            } else {
                cur = new StringBuilder(cand);
            }
        }
        if (!cur.isEmpty()) out.add(DaKit.arcade(cur.toString()));
        return out;
    }

    // ─────────────────────────────────────────────────────────────── dépôt : choix du rouleau

    private static final int PICK_W = 248, ROW = 18;

    private int[] pickerBox() {
        int rows = Math.max(1, Math.min(8, bag.size()));
        int h = 44 + rows * ROW + 10;
        int x = cabX() + (BiblioArt.MEUBLE_W - 4 - PICK_W) / 2, y = cabY() + 34;
        return new int[]{x, y, PICK_W, h};
    }

    private int pickRow(int mx, int my) {
        if (debugPick >= 0) return debugPick;
        int[] b = pickerBox();
        int ry = b[1] + 44;
        for (int i = 0; i < Math.min(8, bag.size()); i++) {
            if (DaKit.dans(mx, my, b[0] + 8, ry + i * ROW - 3, b[2] - 16, ROW)) return i;
        }
        return -1;
    }

    private void picker(GuiGraphicsExtractor g, int mx, int my) {
        int[] b = pickerBox();
        int x = b[0], y = b[1], w = b[2], h = b[3], in = 14;
        g.fill(0, 0, width, height, 0x58080406);
        BiblioArt.registre(g, x, y, w, h);
        DaKit.arcX1(g, font, DaKit.arcade("Deposer un rouleau"), x + in, y + 13, DaKit.ENCRE, 0, false);
        int cell = targetCell();
        String sous = "Il rejoint le fonds" + (cell >= 0 ? ", case " + (cell + 1) : "") + " : il y reste tant que personne ne le prend.";
        DaKit.inter7(g, font, DaKit.inter(sous), x + in, y + 27, DaKit.ENCRE_DOUCE, 0);
        BiblioArt.trait(g, x + in - 2, y + 33, Math.min(160, w - 2 * in));
        int ry = y + 44;
        if (bag.isEmpty()) {
            DaKit.inter8(g, font, DaKit.inter("Aucun rouleau dans ta sacoche."), x + w / 2f, ry + 4, DaKit.ENCRE_DOUCE, 1);
        }
        int hv = pickRow(mx, my);
        // aplats, étiquettes de rang, puis textes : un élément GUI chacun (les rangées ne se chevauchent pas)
        LotGui aplats = LotGui.ouvrir(g);
        for (int i = 0; i < Math.min(8, bag.size()); i++) {
            int yy = ry + i * ROW;
            if (i == hv) {
                aplats.fill(x + 8, yy - 3, x + w - 8, yy + ROW - 4, 0x24462810);
                aplats.fill(x + 8, yy - 3, x + 9, yy + ROW - 4, DaKit.ENCRE_ROUGE);
            }
            if (i < Math.min(8, bag.size()) - 1) aplats.fill(x + in, yy + ROW - 4, x + w - in, yy + ROW - 3, 0x20462810);
        }
        aplats.fermer();
        LotBlit fudas = BiblioArt.fudas(g);
        BiblioArt.Txt tx = new BiblioArt.Txt(g, font);
        for (int i = 0; i < Math.min(8, bag.size()); i++) {
            Sac s = bag.get(i);
            int yy = ry + i * ROW;
            BiblioArt.fuda(fudas, x + in - 2, yy - 8, s.t().rank());
            tx.inter9(DaKit.inter(s.t().name()), x + in + 16, yy + 4, i == hv ? DaKit.ENCRE_ROUGE : DaKit.ENCRE, 0);
            tx.inter7(DaKit.inter(s.t().typeLine()), x + w - in, yy + 4, DaKit.ENCRE_DOUCE, 2);
            final Sac sac = s;
            hits.add(new Hit(x + 8, yy - 3, w - 16, ROW, () -> deposit(sac)));
        }
        fudas.fermer();
        tx.fermer();
        if (bag.size() > 8) {
            DaKit.inter7(g, font, DaKit.inter("… et " + (bag.size() - 8) + " autres"), x + w - in, y + h - 12, DaKit.ENCRE_DOUCE, 2);
        }
    }

    private void deposit(Sac s) {
        RebornSounds.uiClick();
        int cell = targetCell();
        picking = false;
        if (libId != null) {
            JsonObject o = new JsonObject();
            o.addProperty("ref", s.ref());
            o.addProperty("cell", cell);
            ParcheminClient.send("deposit", libId, o);
            return;
        }
        if (cell < 0) return;                                  // banc d'essai : dépôt simulé
        fonds[cell] = s.t();
        fondsBy[cell] = "Toi";
        List<Sac> b = new ArrayList<>(bag);
        b.remove(s);
        bag = b;
        toast("Rouleau déposé : " + s.t().name());
    }

    // ─────────────────────────────────────────────────────────────── journal

    private static final int LOG_W = 320, LOG_ROW = 14;

    private void journal(GuiGraphicsExtractor g, int mx, int my) {
        int rows = Math.max(4, Math.min(12, log.size()));
        int w = Math.min(LOG_W, width - 24), h = 46 + rows * LOG_ROW + 16;
        int x = (width - w) / 2, y = Math.max(52, cabY() + 10);
        g.fill(0, 0, width, height, 0x58080406);
        BiblioArt.registre(g, x, y, w, h);
        int in = 14;
        DaKit.arcX1(g, font, DaKit.arcade("Journal"), x + in, y + 13, DaKit.ENCRE, 0, false);
        DaKit.inter7(g, font, DaKit.inter(name + " · dépôts, prises et vols, du plus récent au plus ancien"), x + in, y + 27,
            DaKit.ENCRE_DOUCE, 0);
        BiblioArt.trait(g, x + in - 2, y + 33, 160);
        int ry = y + 46;
        if (log.isEmpty()) DaKit.inter8(g, font, DaKit.inter("Aucun mouvement pour l'instant."), x + w / 2f, ry + 6, DaKit.ENCRE_DOUCE, 1);
        logScroll = Math.max(0, Math.min(logScroll, Math.max(0, log.size() - rows)));
        // aplats des rangées puis textes : deux éléments GUI (les rangées ne se chevauchent pas)
        LotGui aplats = LotGui.ouvrir(g);
        BiblioArt.Txt tx = new BiblioArt.Txt(g, font);
        for (int i = 0; i < rows && i + logScroll < log.size(); i++) {
            Ligne l = log.get(i + logScroll);
            int yy = ry + i * LOG_ROW;
            if (i % 2 == 0) aplats.fill(x + 8, yy - 4, x + w - 8, yy + LOG_ROW - 4, 0x10462810);
            tx.inter7(DaKit.inter(l.when()), x + in, yy + 3, DaKit.ENCRE_DOUCE, 0);
            tx.inter8(DaKit.inter(DaKit.ellipse(font, l.who(), 70)), x + in + 54, yy + 3, DaKit.ENCRE, 0);
            boolean depot = l.act().equals("depot"), vol = l.act().equals("vol");
            tx.inter8(DaKit.inter(depot ? "dépose" : vol ? "VOLE" : "prend"), x + in + 130, yy + 3,
                depot ? 0xFF2E6B3A : vol ? 0xFF7A1322 : DaKit.ENCRE_ROUGE, 0);
            aplats.fill(x + in + 166, yy, x + in + 169, yy + 7, ScrollArt.rankColor(l.rank()));
            tx.inter8(DaKit.inter(DaKit.ellipse(font, l.tech(), 92)), x + in + 173, yy + 3, DaKit.ENCRE, 0);
            tx.inter7(DaKit.inter(l.shelf().equals("fonds") ? "fonds" : "arrivages"), x + w - in, yy + 3,
                DaKit.ENCRE_DOUCE, 2);
        }
        aplats.fermer();
        tx.fermer();
        if (log.size() > rows) {
            String more = (logScroll + 1) + "-" + Math.min(log.size(), logScroll + rows) + " sur " + log.size();
            DaKit.inter7(g, font, DaKit.inter(more), x + w - in, y + h - 9, DaKit.ENCRE_DOUCE, 2);
        }
    }

    // ─────────────────────────────────────────────────────────────── entrées

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (stealing()) return true;                            // la fouille occupe les mains
        if (e.button() == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (DaKit.dans(e.x(), e.y(), h.x, h.y, h.w, h.h)) { h.r.run(); return true; }
            }
        }
        if (picking || log != null) {                           // clic hors du calque : on revient à l'étagère
            if (e.button() == 0) { picking = false; log = null; }
            return true;
        }
        int i = hovered((int) e.x(), (int) e.y());
        if (i < 0) return super.mouseClicked(e, dbl);
        Technique[] shelf = shelf();
        if (e.button() == 1) {
            if (v2 && tab == FONDS && onsite && fonds[i] != null && fonds[i].rank() != 'S') {
                RebornSounds.uiClick();
                JsonObject o = new JsonObject();
                o.addProperty("cell", i);
                ParcheminClient.send("read_onsite", libId, o);
            }
            return true;
        }
        if (e.button() != 0) return true;
        if (shelf[i] != null) take(i);
        else if (v2 && tab == FONDS && canDeposit) { RebornSounds.uiClick(); openPicker(i); }
        else if (v2 && tab == FONDS) toast(whyNoDeposit());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (log != null) { logScroll -= (int) Math.signum(sy); return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && stealing()) {   // renoncer, sans fermer l'écran
            if (libId != null) ParcheminClient.send("steal_cancel", libId, null);
            stealCell = -1;
            return true;
        }
        if (stealing()) return true;
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && (picking || log != null)) {
            picking = false;
            log = null;
            return true;
        }
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && v2 && draws && !picking && log == null) {
            tab = tab == FONDS ? ARRIVAGES : FONDS;
            RebornSounds.uiClick();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        if (stealing() && libId != null) ParcheminClient.send("steal_cancel", libId, null);
        stealCell = -1;
        super.onClose();
    }

    /** Banc d'essai : première case garnie, de préférence un rang C ou plus. */
    public int firstFilled() {
        Technique[] shelf = shelf();
        int any = -1;
        for (int i = 0; i < shelf.length; i++) {
            if (shelf[i] == null) continue;
            if (shelf[i].rankIndex() >= 1) return i;
            if (any < 0) any = i;
        }
        return Math.max(0, any);
    }

    void take(int i) {
        Technique t = shelf()[i];
        if (t == null) return;
        if (v2 && !canTake) {
            if (!canSteal) { toast("Réservé aux membres : tu peux regarder, pas emporter."); return; }
            RebornSounds.uiClick();
            if (libId != null) {                               // le serveur lance la fouille et renvoie sa durée
                JsonObject o = new JsonObject();
                o.addProperty("slot", i);
                o.addProperty("shelf", tab == FONDS ? "fonds" : "arrivages");
                ParcheminClient.send("steal", libId, o);
            } else {
                double sec = stealSecsOf(t.rank());
                debugSteal(i, Math.round((sec > 0 ? sec : 6) * 1000), 0f);
            }
            return;
        }
        if (libId != null) {                                   // le serveur décide et renvoie l'état
            JsonObject o = new JsonObject();
            o.addProperty("slot", i);
            if (v2) o.addProperty("shelf", tab == FONDS ? "fonds" : "arrivages");
            ParcheminClient.send("take", libId, o);
            RebornSounds.uiClick();
            return;
        }
        if (weight + t.weight() > maxWeight) {
            toast = "Ta sacoche est trop lourde.";
        } else {
            weight += t.weight();
            shelf()[i] = null;
            toast = "Rouleau pris : " + t.name();
            RebornSounds.uiClick();
        }
        toastAt = System.currentTimeMillis();
    }
}
