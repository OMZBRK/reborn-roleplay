package fr.reborn.hud.communication;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * La missive ouverte (maquette v3c 05, CLOCHE_ET_MISSIVES.md §3.5) : la lettre <b>seule au centre</b>, dans le même
 * écran laqué — « À », volière et heure d'arrivée, le texte crème en grand, la signature (ou « sans signature ») et le
 * cachet brisé en pied. Sous la lettre : « Cachet déjà brisé quand tu l'as prise : quelqu'un l'a lue avant toi. » si
 * c'est le cas, puis Garder (Échap) · Brûler · Tendre (menu R).
 */
public final class LettreScreen extends Screen {

    private final JsonObject o;
    private final CommArt.Plume pz = new CommArt.Plume();
    private final VoliereScreen retour;
    private final long ouvert = System.currentTimeMillis();
    private int x0, y0, w, h;
    private float bx, by, bw;

    LettreScreen(JsonObject o, VoliereScreen retour) {
        super(Component.literal("Missive"));
        this.o = o;
        this.retour = retour;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private List<List<CommArt.Seg>> lignes;

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        w = 300;
        lignes = CommArt.lignes(this.font, CommArt.segs(o.get("texte")), 270 - 24, 17, 0);
        h = Math.max(202, 15 + 45 + Math.max(4, lignes.size()) * 16 + 37 + 15);
        h = Math.min(h, this.height - 50);
        x0 = (this.width - w) / 2;
        y0 = Math.max(4, (this.height - h) / 2 - 15);
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        super.removed();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        float a = Math.min(1f, (System.currentTimeMillis() - ouvert) / 180f);
        ctx.fill(0, 0, this.width, this.height, DaKit.argb(0x0A0608, 0.62f * a));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        pz.police(this.font);
        CommArt.panneau(ctx, x0, y0, w, h);
        float x = x0 + 15, y = y0 + 15, zw = 270, zh = h - 30;
        CommArt.zone(pz, x, y, zw, zh, false);
        List<String> a = new ArrayList<>();
        for (JsonElement e : CommClient.arr(o, "a")) a.add(e.getAsString());
        pz.texte("À", x + 11, y + 14, 14, CommArt.GRIS, false);
        String arrivee = CommClient.str(o, "lieu") + " · " + CommClient.str(o, "heure");
        float aw = pz.largeur(arrivee, 12, false);
        pz.texte(pz.ellipse(String.join(", ", a), zw - 40 - aw, 16, true), x + 22, y + 14, 16, CommArt.CREME, true);
        pz.texteD(arrivee, x + zw - 11, y + 14, 12, CommArt.GRIS, false);
        pz.h(x + 9, x + zw - 9, y + 27, CommArt.FILET_ZONE);
        int max = (int) Math.max(1, (zh - 45 - 37) / 16 + 1);
        for (int i = 0; i < Math.min(max, lignes.size()); i++) pz.riche(lignes.get(i), x + 12, y + 45 + i * 16, 17, CommArt.CREME);
        float pied = y + zh - 29, yb = y + zh - 14.5f;
        pz.h(x + 9, x + zw - 9, pied, CommArt.FILET_ZONE);
        float cw = 27;
        String sg = o.has("signe") ? "— " + CommClient.str(o, "signe") : "— sans signature";
        pz.italique(sg, x + zw - 17 - cw - pz.largeurItalique(sg, 16), yb, 16, CommArt.CREME_DOUX);
        float cx = x0 + w / 2f;
        float ys = y0 + h + 11;
        if (CommClient.bool(o, "dejaBrise"))
            pz.texteC("Cachet déjà brisé quand tu l'as prise : quelqu'un l'a lue avant toi.", cx, ys, 14, CommArt.AMBRE, false);
        else if (CommClient.bool(o, "vientDeBriser"))
            pz.texteC("Tu viens de briser le cachet : il le restera.", cx, ys, 14, CommArt.CREME_DOUX, false);
        // Garder  Échap · Brûler · Tendre  menu R
        String g = "Garder  Échap", b = "Brûler", t = "Tendre  menu R", sep = "    ·    ";
        float tot = pz.largeur(g + sep + b + sep + t, 13, false);
        float xx = cx - tot / 2f, yy = ys + 14;
        xx += pz.texte(g + sep, xx, yy, 13, CommArt.CREME_DOUX, false);
        bw = pz.largeur(b, 13, false);
        bx = xx;
        by = yy;
        boolean hov = DaKit.dans(mx, my, Math.round(bx - 2), Math.round(by - 6), Math.round(bw + 4), 12);
        xx += pz.texte(b, xx, yy, 13, hov ? CommArt.OR_CLAIR : CommArt.CREME_DOUX, hov);
        pz.texte(sep + t, xx, yy, 13, CommArt.CREME_DOUX, false);
        String toast = CommClient.toastActif();
        if (toast != null) pz.texteC(toast, cx, yy + 14, 13, CommClient.toastRefus ? CommArt.ROUGE : CommArt.AMBRE, false);
        pz.vider(ctx);
        CommArt.sprite(ctx, CommArt.CACHET_BRISE, 18, 16, x + zw - 10 - cw, yb - 12, cw, -1);
    }

    private void bruler() {
        JsonObject q = CommClient.a("bruler");
        q.addProperty("id", CommClient.str(o, "id"));
        CommClient.send(q);
        RebornSounds.uiClick();
    }

    @Override
    public void onClose() {
        if (retour != null) Minecraft.getInstance().setScreenAndShow(retour);
        else super.onClose();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (DaKit.dans(e.x(), e.y(), Math.round(bx - 2), Math.round(by - 6), Math.round(bw + 4), 12)) { bruler(); return true; }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(e);
    }
}
