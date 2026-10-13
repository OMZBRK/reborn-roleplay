package fr.reborn.hud.parchemin;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bibliothèque à tirage, côté joueur. Motif de l'écran : un meuble à casiers (tansu) de bois sombre, linteau laqué et
 * plaque 書庫, chaque rouleau (modèle 3D SNR) couché dans son casier, son étiquette de rang (fuda) pendue au coin.
 * Survol : fiche du rouleau sur washi ; clic : on prend le rouleau (il disparaît pour les autres), dans la limite de
 * poids de la sacoche. Habillage : textures de {@code tools/gen_bibliotheque_textures.py} (cf. {@link BiblioArt}).
 */
public final class BibliothequeScreen extends Screen {

    private static final int COLS = 3, ROWS = 3, CARTE_W = 168, ECART = 14;

    /** Identifiant serveur ({@code null} = démo du banc d'essai, sans réseau). */
    private String libId;
    private String name;
    private final Technique[] cells = new Technique[COLS * ROWS];
    private int count;
    private String refresh;
    private double weight = 6.4;
    private double maxWeight = 12.0;
    private boolean staff;
    private String toast;
    private long toastAt;
    /** Banc d'essai : case survolée imposée (-1 = souris). */
    public int debugHover = -1;

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
    public static BibliothequeScreen fromJson(com.google.gson.JsonObject o) {
        BibliothequeScreen s = new BibliothequeScreen(o.get("name").getAsString(), List.of(), 0, "");
        s.update(o);
        return s;
    }

    public String libId() { return libId; }

    /** Nouvel état envoyé par le serveur (après une prise, un tirage…). */
    public void update(com.google.gson.JsonObject o) {
        libId = o.get("id").getAsString();
        name = o.get("name").getAsString();
        count = o.get("count").getAsInt();
        refresh = o.get("refreshIn").getAsString();
        weight = o.get("weight").getAsDouble();
        maxWeight = o.get("maxWeight").getAsDouble();
        staff = o.has("staff") && o.get("staff").getAsBoolean();
        var arr = o.getAsJsonArray("cells");
        for (int i = 0; i < cells.length; i++) {
            cells[i] = i < arr.size() && arr.get(i).isJsonObject() ? Technique.fromJson(arr.get(i).getAsJsonObject()) : null;
        }
    }

    public void toast(String msg) {
        toast = msg;
        toastAt = System.currentTimeMillis();
    }

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

    private int cabY() { return 62 + Math.max(0, (height - 62 - 64 - BiblioArt.MEUBLE_H) / 3); }

    private int cellX(int i) { return cabX() + 24 + (i % COLS) * 110; }

    private int cellY(int i) { return cabY() + 44 + (i / COLS) * 68; }

    private int[] staffButton() { return new int[]{width - 38, 12, 22, 22}; }

    private int hovered(int mx, int my) {
        if (debugHover >= 0) return debugHover;
        for (int i = 0; i < cells.length; i++) if (DaKit.dans(mx, my, cellX(i), cellY(i), BiblioArt.CW, BiblioArt.CH)) return i;
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        BiblioArt.entete(g, font, width, DaKit.arcade(name), DaKit.inter("Nouveau tirage dans " + refresh));

        int x = cabX(), y = cabY();
        DaKit.blit(g, BiblioArt.MEUBLE, x, y, 0, 0, BiblioArt.MEUBLE_W, BiblioArt.MEUBLE_H, BiblioArt.MEUBLE_W, BiblioArt.MEUBLE_H);

        int hov = hovered(mx, my);
        if (hov != lastHov) {
            if (hov >= 0 && cells[hov] != null) fr.reborn.hud.menu.RebornSounds.playReborn("esc.hover", 1.1f, 0.10f);
            lastHov = hov;
        }
        for (int i = 0; i < cells.length; i++) {
            int cx = cellX(i), cy = cellY(i);
            Technique t = cells[i];
            int v = i >= count ? 2 : t == null ? 1 : 0;
            DaKit.blit(g, BiblioArt.CASIERS, cx, cy, v * BiblioArt.CW, 0, BiblioArt.CW, BiblioArt.CH, 416, 62);
            if (t != null) {
                boolean hot = i == hov;
                if (hot) DaKit.blit(g, BiblioArt.CASIERS, cx, cy, 3 * BiblioArt.CW, 0, BiblioArt.CW, BiblioArt.CH, 416, 62);
                int sz = 50, lift = hot ? 2 : 0;
                ScrollArt.item(g, cx + (BiblioArt.CW - sz) / 2 - 4, cy + 7 - lift, sz, t.rank());
                BiblioArt.fuda(g, cx + BiblioArt.CW - 17, cy, t.rank());
            }
        }

        if (hov >= 0 && cells[hov] != null) card(g, cells[hov], hov);

        // sacoche
        int gx = x + 186 - 100, gy = y + BiblioArt.MEUBLE_H + 4;
        String wt = String.format(java.util.Locale.FRANCE, "%.1f / %.0f kg", weight, maxWeight);
        BiblioArt.jauge(g, font, gx, gy, (float) (weight / maxWeight), SACOCHE, DaKit.inter(wt));
        BiblioArt.aides(g, font, width, height, new String[][]{{"Clic", "prendre le rouleau"}, {"Echap", "fermer"}});

        if (staff) {
            int[] b = staffButton();
            boolean hb = DaKit.dans(mx, my, b[0], b[1], b[2], b[3]);
            DaKit.blit(g, BiblioArt.REGLAGES, width - 40, 0, hb ? 26 : 0, 0, 26, 38, 52, 38);
            DaKit.etiq(g, font, REGLAGES, width - 27 - REGLAGES.w() / 2f, 41, hb ? DaKit.OR_CLAIR : DaKit.OR_ETIQ);
        }
        if (toast != null && now - toastAt < 2200) {
            float a = Math.min(1f, (2200 - (now - toastAt)) / 400f);
            var c = DaKit.inter(toast);
            int tw = Math.round(DaKit.largeur8(font, c)) + 28, tx = width / 2 - tw / 2, ty = y + 34;
            DaKit.coupeH(g, BiblioArt.TOAST, tx, ty, tw + 4, 0, 0, 264, 20, 8, 264, 20);
            DaKit.inter8(g, font, c, width / 2f, ty + 8, DaKit.alpha(DaKit.CREME, a), 1);
        }
        super.extractRenderState(g, mx, my, delta);
    }

    private static final DaKit.Etiq SACOCHE = DaKit.etiq(net.minecraft.client.Minecraft.getInstance().font, "Sacoche"),
        REGLAGES = DaKit.etiq(net.minecraft.client.Minecraft.getInstance().font, "Reglages");
    private int lastHov = -1;

    /** Fiche du rouleau survolé, sur washi : à droite du meuble, sinon à gauche, sinon sur le casier. */
    private void card(GuiGraphicsExtractor g, Technique t, int cell) {
        int w = CARTE_W, in = 12, iw = w - 2 * in;
        // nom (ArcadePix ×1, une ou deux lignes), description, puis les lignes de détail
        java.util.List<Component> nom = lignesArcade(t.name(), iw - 32);
        DaKit.Paragraphe desc = DaKit.paragraphe(font, t.desc(), iw, DaKit.S8, 10, 6);
        int yNom = 16, yType = yNom + nom.size() * 10 + 5, yTrait = yType + 9, yDesc = yTrait + 9;
        int yMeta = yDesc + desc.hauteur() + 8, h = yMeta + 2 * 13 + 20;
        int mw = BiblioArt.MEUBLE_W - 4;
        int x;
        if (cabX() + mw + ECART + w <= width - 6) x = cabX() + mw + ECART;
        else if (cabX() - ECART - w >= 6) x = cabX() - ECART - w;
        else x = Math.max(6, Math.min(width - w - 6, cellX(cell) + BiblioArt.CW + 4));
        int y = Math.max(56, Math.min(cellY(cell) - 10, height - 30 - h));
        DaKit.coupe(g, BiblioArt.CARTE, x - 2, y - 2, w + 8, h + 8, 0, 0, 176, 300, 8, 16, 176, 300);
        BiblioArt.brocart(g, x, y, w, t.rank());
        BiblioArt.sceau26(g, x + w - in - 12, y + 26, t.rank());
        for (int i = 0; i < nom.size(); i++) DaKit.arcX1(g, font, nom.get(i), x + in, y + yNom + i * 10, DaKit.ENCRE, 0, false);
        DaKit.inter7(g, font, DaKit.inter(t.typeLine()), x + in, y + yType, DaKit.ENCRE_DOUCE, 0);
        BiblioArt.trait(g, x + in - 2, y + yTrait, iw - 28);
        desc.dessiner(g, font, x + in, y + yDesc, DaKit.ENCRE);
        ligne(g, x + in, y + yMeta, "Rang", ScrollArt.rankName(t.rank()).replace("Rang ", "") + " · " + t.difficulty());
        ligne(g, x + in, y + yMeta + 13, "Seances", t.seances() + " · " + String.format(java.util.Locale.FRANCE, "%.1f kg", t.weight()));
        float yb = y + h - 11;
        int cw = BiblioArt.cabochon(g, font, "Clic", x + in, yb, 1f);
        DaKit.inter8(g, font, DaKit.inter("prendre le rouleau"), x + in + cw + 4, yb, DaKit.ENCRE_ROUGE, 0);
    }

    private void ligne(GuiGraphicsExtractor g, int x, int y, String etiq, String val) {
        DaKit.etiq(g, font, DaKit.etiq(font, etiq), x, y, DaKit.ENCRE_DOUCE);
        DaKit.inter8(g, font, DaKit.inter(val), x + 52, y, DaKit.ENCRE, 0);
        g.fill(x, y + 6, x + CARTE_W - 24, y + 7, 0x30462810);
    }

    /** Coupe un titre ArcadePix ×1 en lignes de largeur max (GUI), deux au plus. */
    private java.util.List<Component> lignesArcade(String s, int max) {
        java.util.List<Component> out = new ArrayList<>();
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

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0) return super.mouseClicked(e, dbl);
        int[] sb = staffButton();
        if (staff && DaKit.dans(e.x(), e.y(), sb[0], sb[1], sb[2], sb[3])) {
            ParcheminClient.send("cfg_open", libId, null);
            return true;
        }
        int i = hovered((int) e.x(), (int) e.y());
        if (i >= 0 && cells[i] != null) take(i);
        return true;
    }

    /** Banc d'essai : première case garnie, de préférence un rang C ou plus. */
    public int firstFilled() {
        int any = -1;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == null) continue;
            if (cells[i].rankIndex() >= 1) return i;
            if (any < 0) any = i;
        }
        return Math.max(0, any);
    }

    void take(int i) {
        Technique t = cells[i];
        if (t == null) return;
        if (libId != null) {                                   // le serveur décide et renvoie l'état
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("slot", i);
            ParcheminClient.send("take", libId, o);
            RebornSounds.uiClick();
            return;
        }
        if (weight + t.weight() > maxWeight) {
            toast = "Ta sacoche est trop lourde.";
        } else {
            weight += t.weight();
            cells[i] = null;
            toast = "Rouleau pris : " + t.name();
            RebornSounds.uiClick();
        }
        toastAt = System.currentTimeMillis();
    }
}
