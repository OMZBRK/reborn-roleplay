package fr.reborn.hud.staff;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.nametag.CalqueTete;
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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nametags staff au-dessus des têtes, en <b>mode staff</b> seulement (canal {@code reborn:plaques}, PLUGINS_REFERENCE
 * §4 bis.5) : une ligne petite et fine « 00042 · Nom RP · état » au-dessus du nametag RP, voile nuit léger,
 * mince jauge de PV d'1 px dessous ; petit losange d'or devant un staff, « gelé » en bleu glacier. Taille fixe à l'écran (unités
 * menu), fondu avec la distance (le serveur n'envoie que les joueurs à ≤ 64 blocs). Rien n'est envoyé aux joueurs.
 * Ancrés (31/10) sur la tête réellement rendue ({@code TeteAncre}), en haut de la pile de {@link CalqueTete} : jamais
 * sur le nom, la bulle ou les /me ; visible à travers les murs comme avant (outil staff).
 *
 * <p>Rien n'est alloué par image : la table des nametags est remplacée en bloc à chaque message (jamais modifiée
 * ensuite, lue telle quelle par le rendu) et les textes d'un nametag sont composés une fois, à sa réception.
 */
public final class NametagsStaff {

    record Nametag(String pid, String perso, String pseudo, String etat, int pv, int pvMax, boolean staff, boolean gele,
                  boolean modeStaff) { }

    /** Un nametag et ses textes, composés à la réception (N°, nom, état, et la largeur de la ligne en unités menu). */
    private record Dessin(Nametag p, Component cPid, Component cNom, Component cEtat, float largeur) { }

    /** Nametags reçus, par joueur : table remplacée en bloc (thread client), jamais modifiée une fois publiée. */
    private static volatile Map<UUID, Dessin> NAMETAGS = Map.of();
    private static volatile long recuAt;
    private static final int GLACE = 0xFF9CC8E0, GRIS = 0xFFB4A88E, ETAT = 0xFFE8A0A2;
    private static final Vector3f ANCRE = new Vector3f();
    /** Séparateur « · » des nametags (composé au premier message). */
    private static Component sep;

    /** Banc : affiche aussi le nametag du joueur local (vue de face, 3e personne). */
    static boolean debugSoi;

    private NametagsStaff() {}

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(CanalJson.NAMETAGS, CanalJson.codec(CanalJson.NAMETAGS));
        ClientPlayNetworking.registerGlobalReceiver(CanalJson.NAMETAGS, (p, c) -> c.client().execute(() -> recevoir(p.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> NAMETAGS = Map.of());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "staff-nametags"), NametagsStaff::render);
    }

    static void recevoir(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        if (!"plaques".equals(StaffScreen.str(o, "t"))) return;   // type du message : nom de protocole gardé
        if (!StaffScreen.bool(o, "on")) {
            NAMETAGS = Map.of();
            return;
        }
        Font f = Minecraft.getInstance().font;
        Map<UUID, Dessin> m = new HashMap<>();
        for (JsonElement e : StaffScreen.arr(o, "joueurs")) {
            JsonObject j = e.getAsJsonObject();
            try {
                m.put(UUID.fromString(StaffScreen.str(j, "uuid")), dessin(f, new Nametag(StaffScreen.str(j, "pid"),
                        StaffScreen.str(j, "perso"), StaffScreen.str(j, "pseudo"), StaffScreen.str(j, "etat"),
                        StaffScreen.num(j, "pv"), StaffScreen.num(j, "pvMax"), StaffScreen.bool(j, "staff"),
                        StaffScreen.bool(j, "gele"), StaffScreen.bool(j, "modeStaff"))));
            } catch (IllegalArgumentException ignored) { }
        }
        NAMETAGS = m;
        recuAt = System.currentTimeMillis();
    }

    /** Textes d'un nametag (N°, nom, état) et largeur de sa ligne, composés une fois. */
    private static Dessin dessin(Font f, Nametag p) {
        if (sep == null) sep = DaKit.inter("  ·  ");
        String nom = p.perso().isBlank() ? p.pseudo() : p.perso();
        Component cPid = DaKit.interGras(p.pid().isBlank() ? "—" : p.pid());
        Component cNom = DaKit.inter(DaKit.ellipse(f, nom, 110));
        Component cEtat = p.gele() ? DaKit.inter("gelé" + (p.etat().isBlank() ? "" : " · " + p.etat()))
                : p.etat().isBlank() ? null : DaKit.inter(DaKit.ellipse(f, p.etat(), 90));
        float wS = DaKit.largeur7(f, sep);
        float w = (p.staff() ? 9 : 0) + DaKit.largeur7(f, cPid) + wS + DaKit.largeur7(f, cNom)
                + (cEtat != null ? wS + DaKit.largeur7(f, cEtat) : 0);
        return new Dessin(p, cPid, cNom, cEtat, w);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden()) return;
        Map<UUID, Dessin> nametags = NAMETAGS;
        if (nametags.isEmpty()) return;
        if (System.currentTimeMillis() - recuAt > 5000 && !debugSoi) return;   // plus de nouvelles : mode staff coupé
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        var win = mc.getWindow();
        int km = DaKit.echelleMenu();
        int gs = Math.max(1, win.getGuiScale());
        Font f = mc.font;
        // position interpolée du joueur local (celle de getPosition(pt)), une fois pour tous les nametags
        double ox = Mth.lerp(pt, mc.player.xo, mc.player.getX()), oy = Mth.lerp(pt, mc.player.yo, mc.player.getY()),
            oz = Mth.lerp(pt, mc.player.zo, mc.player.getZ());
        DaKit.unites(g, km);
        List<AbstractClientPlayer> joueurs = mc.level.players();
        for (int i = 0, n = joueurs.size(); i < n; i++) {
            AbstractClientPlayer e = joueurs.get(i);
            if (e == mc.player && !debugSoi) continue;
            Dessin d = nametags.get(e.getUUID());
            if (d == null) continue;
            Nametag p = d.p();
            // Tête rendue (à terre, allongé, émotes) projetée comme le monde, en pixels physiques ; le nametag se pose en
            // haut de la pile de CalqueTete (au-dessus du nom, de la bulle et des /me), bas visible sur le bas de sa couche.
            if (!CalqueTete.ancrePx(e, ANCRE)) continue;
            int bas = p.pvMax() > 0 ? 1 : -1;   // jauge de PV : 1 unité sous y ; sans jauge, le voile s'arrête 1 unité au-dessus
            CalqueTete.occuper(e, CalqueTete.STAFF, (p.pvMax() > 0 ? 12f : 10f) * km / gs);
            float yBas = ANCRE.y - Math.round(CalqueTete.base(e, CalqueTete.STAFF) * gs);
            float x = ANCRE.x / km, y = yBas / km - bas;
            double dx = ox - Mth.lerp(pt, e.xo, e.getX()), dy = oy - Mth.lerp(pt, e.yo, e.getY()),
                dz = oz - Mth.lerp(pt, e.zo, e.getZ());
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float a = (float) Math.max(0.45, 1.0 - dist / 96.0);
            nametag(g, f, d, x, y, a);
        }
        DaKit.finUnites(g);
    }

    /** Un nametag centré en x, bas en y (unités menu). */
    private static void nametag(GuiGraphicsExtractor g, Font f, Dessin d, float x, float y, float a) {
        Nametag p = d.p();
        Component cPid = d.cPid(), cNom = d.cNom(), cEtat = d.cEtat();
        float w = d.largeur();
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
        NAMETAGS = Map.of();
    }

    static void debugSoi(UUID moi) {
        debugSoi = true;
        Map<UUID, Dessin> m = new HashMap<>(NAMETAGS);
        m.put(moi, dessin(Minecraft.getInstance().font,
                new Nametag("00042", "Kazuki Uchiha", "Raiden_FR", "À terre · ATA", 7, 20, false, true, false)));
        NAMETAGS = m;
        recuAt = System.currentTimeMillis();
    }
}
