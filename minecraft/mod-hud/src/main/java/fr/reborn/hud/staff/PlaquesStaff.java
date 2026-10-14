package fr.reborn.hud.staff;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plaques staff au-dessus des têtes, en <b>mode staff</b> seulement ({@code reborn:plaques}, PLUGINS_REFERENCE
 * §4 bis.5) : une ligne petite et fine « 00042 · Nom RP · état » au-dessus de la plaque de nom RP, voile nuit léger,
 * mince jauge de PV d'1 px dessous ; petit losange d'or devant un staff, « gelé » en bleu glacier. Taille fixe à l'écran (unités
 * menu), fondu avec la distance (le serveur n'envoie que les joueurs à ≤ 64 blocs). Rien n'est envoyé aux joueurs.
 */
public final class PlaquesStaff {

    record Plaque(String pid, String perso, String pseudo, String etat, int pv, int pvMax, boolean staff, boolean gele,
                  boolean modeStaff) { }

    private static final Map<UUID, Plaque> PLAQUES = new HashMap<>();
    private static volatile long recuAt;
    private static final int GLACE = 0xFF9CC8E0, GRIS = 0xFFB4A88E, ETAT = 0xFFE8A0A2;
    private static final Matrix4f VP = new Matrix4f();
    private static final Vector4f CLIP = new Vector4f();

    /** Banc : affiche aussi la plaque du joueur local (vue de face, 3e personne). */
    static boolean debugSoi;

    private PlaquesStaff() {}

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(CanalJson.PLAQUES, CanalJson.codec(CanalJson.PLAQUES));
        ClientPlayNetworking.registerGlobalReceiver(CanalJson.PLAQUES, (p, c) -> c.client().execute(() -> recevoir(p.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { synchronized (PLAQUES) { PLAQUES.clear(); } });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "staff-plaques"), PlaquesStaff::render);
    }

    static void recevoir(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        if (!"plaques".equals(StaffScreen.str(o, "t"))) return;
        synchronized (PLAQUES) {
            PLAQUES.clear();
            if (!StaffScreen.bool(o, "on")) return;
            for (JsonElement e : StaffScreen.arr(o, "joueurs")) {
                JsonObject j = e.getAsJsonObject();
                try {
                    PLAQUES.put(UUID.fromString(StaffScreen.str(j, "uuid")), new Plaque(StaffScreen.str(j, "pid"),
                            StaffScreen.str(j, "perso"), StaffScreen.str(j, "pseudo"), StaffScreen.str(j, "etat"),
                            StaffScreen.num(j, "pv"), StaffScreen.num(j, "pvMax"), StaffScreen.bool(j, "staff"),
                            StaffScreen.bool(j, "gele"), StaffScreen.bool(j, "modeStaff")));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        recuAt = System.currentTimeMillis();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden()) return;
        Map<UUID, Plaque> copie;
        synchronized (PLAQUES) {
            if (PLAQUES.isEmpty()) return;
            copie = new HashMap<>(PLAQUES);
        }
        if (System.currentTimeMillis() - recuAt > 5000 && !debugSoi) return;   // plus de nouvelles : mode staff coupé
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = cam.position();
        Matrix4f vp = cam.getViewRotationProjectionMatrix(VP);
        var win = mc.getWindow();
        int km = DaKit.echelleMenu();
        int sw = win.getWidth() / km, sh = win.getHeight() / km;
        Font f = mc.font;
        DaKit.unites(g, km);
        for (Player e : mc.level.players()) {
            if (e == mc.player && !debugSoi) continue;
            Plaque p = copie.get(e.getUUID());
            if (p == null) continue;
            Vec3 tete = e.getEyePosition(pt).add(0.0, 1.02, 0.0);
            Vector4f c = vp.transform(CLIP.set((float) (tete.x - camPos.x), (float) (tete.y - camPos.y),
                    (float) (tete.z - camPos.z), 1f));
            if (c.w() <= 0.05f) continue;
            float nx = c.x() / c.w(), ny = c.y() / c.w();
            if (nx < -1.1f || nx > 1.1f || ny < -1.1f || ny > 1.1f) continue;
            float x = (nx * 0.5f + 0.5f) * sw, y = (1f - (ny * 0.5f + 0.5f)) * sh;
            double dist = mc.player.getPosition(pt).distanceTo(e.getPosition(pt));
            float a = (float) Math.max(0.45, 1.0 - dist / 96.0);
            plaque(g, f, p, x, y, a);
        }
        DaKit.finUnites(g);
    }

    /** Une plaque centrée en x, bas en y (unités menu). */
    static void plaque(GuiGraphicsExtractor g, Font f, Plaque p, float x, float y, float a) {
        String nom = p.perso().isBlank() ? p.pseudo() : p.perso();
        Component cPid = DaKit.interGras(p.pid().isBlank() ? "—" : p.pid());
        Component cNom = DaKit.inter(DaKit.ellipse(f, nom, 110));
        Component cEtat = p.gele() ? DaKit.inter("gelé" + (p.etat().isBlank() ? "" : " · " + p.etat()))
                : p.etat().isBlank() ? null : DaKit.inter(DaKit.ellipse(f, p.etat(), 90));
        Component sep = DaKit.inter("  ·  ");
        float wS = DaKit.largeur7(f, sep);
        float w = (p.staff() ? 9 : 0) + DaKit.largeur7(f, cPid) + wS + DaKit.largeur7(f, cNom)
                + (cEtat != null ? wS + DaKit.largeur7(f, cEtat) : 0);
        float x0 = x - w / 2f;
        int yb = Math.round(y), ym = yb - 6;
        LotGui l = LotGui.ouvrir(g);
        int L = Math.round(w) + 10, X = Math.round(x0) - 5;
        // voile nuit léger, bords adoucis (deux aplats)
        l.fill(X, ym - 5, X + L, ym + 5, Math.round(0x30 * a) << 24 | 0x0A0608);
        l.fill(X + 3, ym - 5, X + L - 3, ym + 5, Math.round(0x40 * a) << 24 | 0x0A0608);
        if (p.pvMax() > 0) {   // jauge de PV d'1 px
            int bw = Math.max(16, Math.min(40, Math.round(w * 0.4f))), bx = Math.round(x - bw / 2f);
            float r = Math.max(0f, Math.min(1f, p.pv() / (float) p.pvMax()));
            l.fill(bx, ym + 6, bx + bw, ym + 7, DaKit.alpha(0xFF2A1A12, a));
            l.fill(bx, ym + 6, bx + Math.round(bw * r), ym + 7, DaKit.alpha(r < 0.3f ? 0xFFC8404A : DaKit.OR, a));
        }
        l.fermer();
        float tx = x0;
        if (p.staff()) {
            // petit losange d'or devant un staff (aucun kanji, DA §3)
            int dx = Math.round(tx) + 2, dy = Math.round(ym);
            g.fill(dx, dy - 1, dx + 1, dy + 2, DaKit.alpha(DaKit.OR, a));
            g.fill(dx - 1, dy, dx + 2, dy + 1, DaKit.alpha(DaKit.OR, a));
            tx += 9;
        }
        tx += DaKit.inter7o(g, f, cPid, tx, ym, DaKit.alpha(DaKit.OR_CLAIR, a), 0);
        tx += DaKit.inter7o(g, f, sep, tx, ym, DaKit.alpha(GRIS, a), 0);
        tx += DaKit.inter7o(g, f, cNom, tx, ym, DaKit.alpha(DaKit.CREME, a), 0);
        if (cEtat != null) {
            tx += DaKit.inter7o(g, f, sep, tx, ym, DaKit.alpha(GRIS, a), 0);
            DaKit.inter7o(g, f, cEtat, tx, ym, DaKit.alpha(p.gele() ? GLACE : ETAT, a), 0);
        }
    }

    /* ================================================================ banc */

    static void debugEffacer() {
        debugSoi = false;
        synchronized (PLAQUES) { PLAQUES.clear(); }
    }

    static void debugSoi(UUID moi) {
        debugSoi = true;
        synchronized (PLAQUES) {
            PLAQUES.put(moi, new Plaque("00042", "Kazuki Uchiha", "Raiden_FR", "À terre · ATA", 7, 20, false, true, false));
        }
        recuAt = System.currentTimeMillis();
    }
}
