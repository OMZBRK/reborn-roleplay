package fr.reborn.hud.menu.character.scene;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.character.CharacterCard;
import fr.reborn.hud.menu.character.CharacterData;
import fr.reborn.hud.menu.character.CharacterLoadingScreen;
import fr.reborn.hud.menu.character.CharacterPayload;
import fr.reborn.hud.menu.character.CharacterRules;
import fr.reborn.hud.nameplate.Nameplates;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Sélection du personnage <b>en scène</b> : les persos du joueur se tiennent côte à côte dans un vide sombre, chacun
 * sur un ensō de lumière, avec leur skin Reborn, leur stature (âge + taille) et la plaque de nom du jeu. Caméra
 * cinématique (fondu depuis le noir, travelling vers le perso choisi, léger mouvement à l'épaule). La dernière place
 * libre est « + Nouveau shinobi » (création en scène). Entrer = travelling serré, fondu au noir, puis sélection
 * envoyée au serveur ({@code select:<id>}) et écran de chargement.
 */
public final class SceneSelectScreen extends Screen {

    /** Une place dans la scène : un perso (id, identité, apparence, stature) ou la place « nouveau shinobi ». */
    public record Role(String id, String name, String clan, int clanColor, String rank, String village,
                       SkinSpec look, int age, double size, boolean dead, boolean create, Vec3 feet, float yaw,
                       String anim) {}

    private static final int GOLD = 0xFFD9A95E, CREAM = 0xFFF5E9D0, SUB = 0xFFC2B59A, MUTED = 0xFF8A7E6C,
            DEAD = 0xFFA05048;
    private static final String[] POSES = {"scene_crossed.json", "scene_idle.json"};

    private final List<Role> roles;
    private final SceneCamera.Shot wide;
    private final List<ClientMannequin> actors = new ArrayList<>();
    private int focus;
    private long openedAt, focusAt, leaveAt = -1;
    private int prevFocus = -1;
    private boolean spawned, prevHideGui;
    private float[][] boxes = new float[0][];
    private final int[] enterBtn = new int[4];

    public SceneSelectScreen(List<Role> roles, SceneCamera.Shot wide, int focus) {
        super(Component.literal("Choix du shinobi"));
        this.roles = roles;
        this.wide = wide;
        this.focus = Math.max(0, Math.min(roles.size() - 1, focus));
    }

    /**
     * Scène construite depuis les persos envoyés par le serveur ({@link CharacterData}), 40 blocs au-dessus du
     * joueur : persos alignés, place « nouveau shinobi » en bout de rang s'il reste un emplacement.
     */
    public static SceneSelectScreen fromRoster(Minecraft mc) {
        List<CharacterCard> cards = CharacterData.characters();
        boolean canCreate = cards.size() < CharacterData.slotLimit();
        int n = cards.size() + (canCreate ? 1 : 0);
        Vec3 base = mc.player.position().add(0, 40, 0);
        double spacing = 1.65;
        double x0 = -(n - 1) * spacing / 2;
        List<Role> roles = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            CharacterCard c = cards.get(i);
            double x = x0 + i * spacing;
            float yaw = (float) (-x * 9);                      // tournés légèrement vers le centre
            SkinSpec look = c.hasAppearance() ? SkinSpec.deserialize(c.appearance()) : new SkinSpec();
            roles.add(new Role(c.id(), c.firstName(), c.hasClan() ? c.clan() : "", c.clanColor() & 0xFFFFFF,
                    c.rank(), c.village(), look, c.age(), c.size(), c.dead(), false,
                    base.add(x, 0, Math.abs(x) * 0.12), yaw, POSES[i % POSES.length]));
        }
        if (canCreate) {
            double x = x0 + cards.size() * spacing;
            roles.add(new Role("", "Nouveau shinobi", "", 0xD9A95E, "", "", null, 18, 1.0, false, true,
                    base.add(x, 0, Math.abs(x) * 0.12), 0, null));
        }
        SceneCamera.Shot wide = SceneCamera.Shot.look(base.add(0, 1.9, 5.4 + n * 0.6), base.add(0, 1.0, 0), 60);
        return new SceneSelectScreen(roles, wide, 0);
    }

    /* ------------------------------------------------------------- cycle de vie */

    @Override
    protected void init() {
        if (spawned) return;
        spawned = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        prevHideGui = mc.gui.hud.isHidden();
        ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        for (int i = 0; i < roles.size(); i++) {
            Role r = roles.get(i);
            if (r.create()) { actors.add(null); continue; }
            ClientMannequin m = SceneActors.spawn(mc, -424200 - i, r.feet(), r.yaw(), r.look(), r.anim(), i * 23f);
            SceneActors.setBody(m, r.age(), r.size());
            actors.add(m);
        }
        openedAt = System.currentTimeMillis();
        SceneCamera.Shot start = new SceneCamera.Shot(wide.pos().add(0, 0.6, 1.6), wide.yaw(), wide.pitch() + 6, wide.fov() + 6);
        SceneCamera.start(start);
        SceneCamera.moveTo(shotFor(focus), 2600);
        focusAt = openedAt;
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        for (ClientMannequin m : actors) if (m != null) SceneActors.remove(mc, m);
        actors.clear();
        SceneCamera.stop();
        ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHideGui);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Jouer exige d'avoir choisi un perso : Échap ne ferme pas l'écran. */
    @Override
    public boolean shouldCloseOnEsc() { return false; }

    /* ----------------------------------------------------------------- caméra */

    private double scaleOf(int i) {
        ClientMannequin m = i < actors.size() ? actors.get(i) : null;
        return m == null ? 1.0 : SceneActors.scaleOf(m);
    }

    private SceneCamera.Shot shotFor(int i) {
        Role r = roles.get(i);
        double k = scaleOf(i);
        Vec3 f = r.feet();
        Vec3 from = new Vec3(f.x + (wide.pos().x - f.x) * 0.3, f.y + 1.25 * k + 0.15, f.z + 4.2);
        return SceneCamera.Shot.look(from, f.add(0, 0.95 * k, 0), 52);
    }

    private SceneCamera.Shot closeFor(int i) {
        Vec3 f = roles.get(i).feet();
        double k = scaleOf(i);
        return SceneCamera.Shot.look(f.add(0, 1.5 * k, 1.9), f.add(0, 1.3 * k, 0), 46);
    }

    public void setFocus(int i) {
        if (i < 0 || i >= roles.size() || i == focus || leaveAt >= 0) return;
        prevFocus = focus;
        focus = i;
        focusAt = System.currentTimeMillis();
        RebornSounds.charNav();
        SceneCamera.moveTo(shotFor(i), 1300);
    }

    public void enter() {
        if (leaveAt >= 0) return;
        Role r = roles.get(focus);
        if (r.dead()) { RebornSounds.deny(); note("Ce personnage est mort (RPK). Demande à un staff de le ressusciter."); return; }
        leaveAt = System.currentTimeMillis();
        RebornSounds.confirm();
        SceneCamera.moveTo(closeFor(focus), 1600);
    }

    /** Fin du travelling d'entrée : création ou sélection envoyée au serveur. */
    private void finish() {
        Minecraft mc = Minecraft.getInstance();
        Role r = roles.get(focus);
        if (r.create()) { mc.setScreenAndShow(SceneJourneyScreen.open(mc)); return; }
        if (mc.player != null) {
            if (r.look() != null && CharacterData.characters().stream().anyMatch(c -> c.id().equals(r.id()) && c.hasAppearance())) {
                RebornSkins.applySpec(mc.player.getUUID(), r.look());
            } else {
                RebornSkins.clear(mc.player.getUUID());
            }
        }
        if (ClientPlayNetworking.canSend(CharacterPayload.ID)) {
            ClientPlayNetworking.send(new CharacterPayload("select:" + r.id()));
            mc.setScreenAndShow(new CharacterLoadingScreen(r.name(), 0xFF000000 | r.clanColor()));
        } else {
            note("Sélection (hors serveur)");
            leaveAt = -1;
            openedAt = System.currentTimeMillis() - 2000;
            SceneCamera.moveTo(shotFor(focus), 900);
        }
    }

    private void note(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Reborn] " + msg));
    }

    /* ------------------------------------------------------------- saisie */

    @Override
    public boolean keyPressed(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_Q -> setFocus(focus - 1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> setFocus(focus + 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> enter();
            case GLFW.GLFW_KEY_ESCAPE -> { }
            default -> { return super.keyPressed(e); }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v != 0) { setFocus(focus + (v > 0 ? -1 : 1)); return true; }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0 || leaveAt >= 0) return super.mouseClicked(e, dbl);
        double mx = e.x(), my = e.y();
        if (mx >= enterBtn[0] && my >= enterBtn[1] && mx < enterBtn[0] + enterBtn[2] && my < enterBtn[1] + enterBtn[3]) {
            enter();
            return true;
        }
        for (int i = 0; i < boxes.length; i++) {
            float[] b = boxes[i];
            if (b == null || mx < b[0] || mx > b[2] || my < b[1] || my > b[3]) continue;
            if (i == focus) enter(); else setFocus(i);
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public void tick() {
        SceneCamera.Shot cam = SceneCamera.current();
        for (int i = 0; i < actors.size(); i++) {
            ClientMannequin m = actors.get(i);
            if (m == null) continue;
            Role r = roles.get(i);
            float target = r.yaw();
            if (i == focus) {
                Vec3 d = cam.pos().subtract(m.position());
                float toCam = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                float diff = ((toCam - r.yaw()) % 360 + 540) % 360 - 180;
                target = r.yaw() + Math.max(-35, Math.min(35, diff));
            }
            float cur = m.getYHeadRot();
            float st = ((target - cur) % 360 + 540) % 360 - 180;
            m.setYHeadRot(cur + st * 0.12f);
        }
        if (leaveAt >= 0 && System.currentTimeMillis() - leaveAt > (roles.get(focus).create() ? 1500 : 2400)) finish();
    }

    /* ------------------------------------------------------------------ rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        int w = width, h = height;
        float ui = Math.min(1f, Math.max(0f, (now - openedAt - 900) / 700f));
        if (leaveAt >= 0) ui = Math.max(0f, 1f - (now - leaveAt) / 500f);

        float scene = Math.min(1f, (now - openedAt) / 1500f);
        float ig = Math.min(1f, (now - focusAt) / 650f);
        float igE = 1f - (float) Math.pow(1 - ig, 3);
        for (int i = 0; i < roles.size(); i++) {
            double k = scaleOf(i);
            float str = roles.get(i).create() ? 0.18f : 0.3f;
            double rad = 0.55 * k;
            if (i == focus) { str = 0.3f + 0.7f * igE + 0.5f * (float) Math.sin(Math.PI * ig); rad *= 0.55 + 0.45 * igE; }
            else if (i == prevFocus) str = 0.3f + 0.7f * (1f - igE);
            if (roles.get(i).dead()) str *= 0.35f;
            SceneFx.ring(g, roles.get(i).feet(), rad, scene * Math.min(1.4f, str), w, h);
        }
        SceneFx.fireflies(g, w, h, scene, 26);
        int bar = Math.round(16 * Math.min(1f, (now - openedAt) / 900f));
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);

        // plaques au-dessus des têtes + zones cliquables (projection monde → écran)
        Camera cam = mc.gameRenderer.mainCamera();
        boxes = new float[roles.size()][];
        if (cam != null && cam.isInitialized()) {
            Matrix4f vp = cam.getViewRotationProjectionMatrix(new Matrix4f());
            Vec3 cp = cam.position();
            for (int i = 0; i < roles.size(); i++) {
                Role r = roles.get(i);
                double k = scaleOf(i);
                float[] top = project(vp, cp, r.feet().add(0, 1.85 * k + 0.42, 0), w, h);
                float[] bot = project(vp, cp, r.feet(), w, h);
                if (top == null || bot == null) continue;
                float half = Math.max(18, (bot[1] - top[1]) * 0.28f);
                boxes[i] = new float[]{top[0] - half, top[1] - 10, top[0] + half, bot[1] + 6};
                if (ui <= 0) continue;
                boolean on = i == focus;
                float ig2 = Math.min(1f, (now - focusAt) / 400f);
                float emph = i == focus ? ig2 : i == prevFocus ? 1f - ig2 : 0f;
                float sc = 1f + 0.25f * emph;
                if (r.create()) {
                    Component c = RebornFont.display("+ NOUVEAU SHINOBI");
                    text(g, c, top[0] - font.width(c) * sc / 2f, bot[1] - 60 * sc, a(GOLD, ui * (0.55f + 0.45f * emph)), sc);
                    Component s = RebornFont.body(CharacterData.characters().size() + " / " + CharacterData.slotLimit() + " personnages");
                    text(g, s, top[0] - font.width(s) / 2f, bot[1] - 44 * sc, a(MUTED, ui), 1f);
                } else {
                    g.pose().pushMatrix();
                    g.pose().translate(top[0], top[1]);
                    g.pose().scale(sc, sc);
                    Nameplates.drawVillagePlate(g, font, 0, 0, (r.name() + " " + r.clan()).trim(), r.clanColor(), r.village(),
                            ui * (0.6f + 0.4f * emph) * (r.dead() ? 0.6f : 1f));
                    g.pose().popMatrix();
                    if (r.dead()) {
                        Component dc = RebornFont.display("MORT");
                        text(g, dc, top[0] - font.width(dc) / 2f, top[1] + 4, a(DEAD, ui), 1f);
                    }
                }
                if (on) {
                    int b = Math.round((float) Math.sin(now / 300.0) * 2);
                    int col = a(GOLD, ui);
                    int ax = Math.round(top[0]), ay = Math.round(top[1] - 26 * sc) + b;
                    if (r.create()) ay = Math.round(bot[1] - 76 * sc) + b;
                    g.fill(ax - 1, ay, ax + 1, ay + 6, col);
                    g.fill(ax - 3, ay + 2, ax + 3, ay + 4, col);
                }
            }
        }

        // fiche du perso choisi, en bas à gauche
        if (prevFocus >= 0 && now - focusAt < 260) {
            float o = (now - focusAt) / 260f;
            Role pf = roles.get(prevFocus);
            text(g, RebornFont.display((pf.name() + " " + pf.clan()).trim().toUpperCase()), 64 - o * 18, h - bar - 56,
                    a(CREAM, ui * (1 - o)), 1.5f);
        }
        Role f = roles.get(focus);
        float slide = Math.min(1f, Math.max(0f, (now - focusAt - (prevFocus >= 0 ? 180 : 0)) / 450f));
        float sweep = Math.min(1f, Math.max(0f, (now - focusAt - 200) / 500f));
        if (sweep > 0 && sweep < 1) {
            int sl = Math.round(220 * sweep);
            for (int q = 0; q < sl; q++) g.fill(64 + q, h - bar - 24, 65 + q, h - bar - 23,
                    a(GOLD, ui * (1 - q / (float) Math.max(1, sl)) * (1 - sweep) * 1.8f));
        }
        int fx = 28 - Math.round((1 - slide) * 12), fy = h - bar - 58;
        float fa = ui * slide;
        String key = villageKey(f.village());
        if (key != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("reborn", "textures/gui/esc/village_" + key + ".png"),
                    fx, fy + 4, 0f, 0f, 26, 26, 48, 48, 48, 48, a(CREAM, fa));
        }
        if (f.create()) {
            text(g, RebornFont.display("NOUVEAU SHINOBI"), fx + 36, fy + 2, a(GOLD, fa), 1.5f);
            text(g, RebornFont.display("UNE NOUVELLE HISTOIRE COMMENCE"), fx + 36, fy + 20, a(SUB, fa), 1f);
        } else {
            text(g, RebornFont.display((f.name() + " " + f.clan()).trim().toUpperCase()), fx + 36, fy + 2, a(f.dead() ? DEAD : CREAM, fa), 1.5f);
            String sub = (f.dead() ? "MORT  ·  " : "") + f.rank() + (f.village().isBlank() ? "" : "  ·  " + f.village());
            text(g, RebornFont.display(sub.toUpperCase()), fx + 36, fy + 20, a(SUB, fa), 1f);
            g.fill(fx + 36, fy + 32, fx + 76, fy + 34, a(0xFF000000 | f.clanColor(), fa));
        }

        text(g, RebornFont.display("CHOIX DU SHINOBI"), 28, bar + 14, a(SUB, ui), 1f);
        g.fill(28, bar + 26, 60, bar + 27, a(GOLD, ui));
        Component keys = RebornFont.display("ENTREE : JOUER    < > : CHANGER");
        text(g, keys, w - 28 - font.width(keys), h - bar - 22, a(MUTED, ui), 1f);
        Component enter = RebornFont.display(f.create() ? "CRÉER" : f.dead() ? "INDISPONIBLE" : "ENTRER EN JEU");
        int bw = font.width(enter) + 36, bx = w / 2 - bw / 2, by = h - bar - 34;
        boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + 20;
        g.fill(bx, by, bx + bw, by + 20, a(hov ? 0xE03A1A1E : 0xD0150A0D, ui));
        g.fill(bx, by, bx + bw, by + 1, a(GOLD, ui));
        g.fill(bx, by + 19, bx + bw, by + 20, a(GOLD, ui));
        text(g, enter, w / 2f - font.width(enter) / 2f, by + 6, a(f.dead() ? MUTED : CREAM, ui), 1f);
        enterBtn[0] = bx; enterBtn[1] = by; enterBtn[2] = bw; enterBtn[3] = 20;

        float black = Math.max(0f, 1f - (now - openedAt) / 1100f);
        if (leaveAt >= 0) black = Math.max(black, Math.min(1f, (now - leaveAt - (f.create() ? 300 : 900)) / 1100f));
        if (black > 0) g.fill(0, 0, w, h, Math.round(255 * black) << 24);
    }

    private static float[] project(Matrix4f vp, Vec3 cp, Vec3 p, int w, int h) {
        Vector4f c = vp.transform(new Vector4f((float) (p.x - cp.x), (float) (p.y - cp.y), (float) (p.z - cp.z), 1f));
        if (c.w() <= 0.05f) return null;
        return new float[]{(c.x() / c.w() * 0.5f + 0.5f) * w, (1f - (c.y() / c.w() * 0.5f + 0.5f)) * h};
    }

    /** « Konohagakure » → konoha (emblème) ; null pour Ame / Déserteur / vide. */
    private static String villageKey(String village) {
        String[] vs = CharacterRules.villages();
        for (int i = 0; i < vs.length; i++) if (vs[i].equalsIgnoreCase(village)) return CharacterRules.villageKey(i);
        return null;
    }

    private void text(GuiGraphicsExtractor g, Component c, float x, float y, int col, float sc) {
        g.pose().pushMatrix();
        g.pose().translate(Math.round(x), Math.round(y));
        g.pose().scale(sc, sc);
        g.text(font, c, 0, 0, col, true);
        g.pose().popMatrix();
    }

    private static int a(int argb, float f) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return al << 24 | (argb & 0xFFFFFF);
    }
}
