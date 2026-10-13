package fr.reborn.hud.menu.character.perso;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.character.CharacterCard;
import fr.reborn.hud.menu.character.CharacterData;
import fr.reborn.hud.menu.character.CharacterLoadingScreen;
import fr.reborn.hud.menu.character.CharacterPayload;
import fr.reborn.hud.menu.character.CharacterRules;
import fr.reborn.hud.menu.character.scene.SceneSounds;
import fr.reborn.hud.mixin.HudAccessor;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import fr.reborn.hud.ui.MenuScale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static fr.reborn.hud.menu.character.perso.PersoArt.*;

/**
 * Choix du shinobi « au bord de l'étang » (maquette Maquettes/personnage/01_selection) : le perso choisi, grand, sur
 * sa pierre de gué au centre ; les autres en retrait sur des pierres plus lointaines ; la place libre en silhouette
 * pointillée. Le fond est celui du village du perso choisi. Choisir envoie {@code select:<id>} puis l'écran de
 * chargement ; la place libre ouvre la création.
 */
public final class PersoSelectionScreen extends Screen implements MenuScale.Plancher {

    /** Une place : un perso ou la place « nouveau shinobi ». */
    public record Role(String id, String nom, String clan, int clanColor, String rang, String village, SkinSpec look,
                       int age, double size, String sexe, boolean mort, boolean creer) {}

    private static final float[] SX = {-390, -248, 0, 246, 388}, SY = {362, 394, 446, 394, 362}, SK = {3.2f, 4.5f, 8f, 4.5f, 3.2f};

    private final List<Role> roles;
    private int focus;
    private float glisse;
    private long openedAt, focusAt, leaveAt = -1;
    private boolean prevHidden;
    private final PersoScene scene = new PersoScene();
    private final PersoSkins skins = new PersoSkins("s");
    private final List<int[]> hits = new ArrayList<>();
    private int T, B, cx;

    public PersoSelectionScreen(List<Role> roles, int focus) {
        super(Component.literal("Choix du shinobi"));
        this.roles = roles;
        this.focus = Math.max(0, Math.min(roles.size() - 1, focus));
        this.glisse = this.focus;
        scene.forcerVillage(villageDe(this.focus));
    }

    @Override public int plancherW() { return 840; }

    @Override public int plancherH() { return 470; }

    /** Places construites depuis les persos envoyés par le serveur ; place libre en bout de rang. */
    public static PersoSelectionScreen fromRoster(Minecraft mc) {
        List<CharacterCard> cards = CharacterData.characters();
        List<Role> roles = new ArrayList<>();
        for (CharacterCard c : cards) {
            SkinSpec look = c.hasAppearance() ? SkinSpec.deserialize(c.appearance()) : null;
            roles.add(new Role(c.id(), c.firstName(), c.hasClan() ? c.clan() : "", c.clanColor() & 0xFFFFFF, c.rank(),
                c.village(), look, c.age(), c.size(), c.sexe(), c.dead(), false));
        }
        if (cards.size() < CharacterData.slotLimit()) {
            roles.add(new Role("", "Nouveau shinobi", "", 0xD9A95E, "", "", null, 18, 1.0, "", false, true));
        }
        if (roles.isEmpty()) roles.add(new Role("", "Nouveau shinobi", "", 0xD9A95E, "", "", null, 18, 1.0, "", false, true));
        return new PersoSelectionScreen(roles, 0);
    }

    private int villageDe(int i) {
        Role r = roles.get(i);
        int v = CharacterRules.villageIndex(r.village());
        return v < 0 || v > 5 ? scene.village() : v;
    }

    /* ------------------------------------------------------------- cycle de vie */

    @Override
    protected void init() {
        if (openedAt != 0) return;
        Minecraft mc = Minecraft.getInstance();
        prevHidden = mc.gui.hud.isHidden();
        ((HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        openedAt = focusAt = System.currentTimeMillis();
        PersoSons.musique(scene.village());
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        skins.liberer();
        if (!(mc.gui.screen() instanceof PersoCreationScreen)) PersoSons.arreterMusique();
        ((HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHidden);
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean shouldCloseOnEsc() { return false; }

    public void setFocus(int i) {
        if (i < 0 || i >= roles.size() || i == focus || leaveAt >= 0) return;
        focus = i;
        focusAt = System.currentTimeMillis();
        SceneSounds.focus();
        int v = villageDe(i);
        scene.setVillage(v);
        PersoSons.musique(v);
    }

    public void enter() {
        if (leaveAt >= 0) return;
        Role r = roles.get(focus);
        if (r.mort()) { RebornSounds.deny(); note("Ce personnage est mort (RPK). Demande à un staff de le ressusciter."); return; }
        leaveAt = System.currentTimeMillis();
        SceneSounds.resonate();
    }

    private void finish() {
        Minecraft mc = Minecraft.getInstance();
        Role r = roles.get(focus);
        if (r.creer()) { mc.setScreenAndShow(new PersoCreationScreen()); return; }
        if (mc.player != null) {
            if (r.look() != null) RebornSkins.applySpec(mc.player.getUUID(), r.look());
            else RebornSkins.clear(mc.player.getUUID());
        }
        if (ClientPlayNetworking.canSend(CharacterPayload.ID)) {
            ClientPlayNetworking.send(new CharacterPayload("select:" + r.id()));
            mc.setScreenAndShow(new CharacterLoadingScreen(r.nom(), 0xFF000000 | r.clanColor()));
        } else {
            note("Sélection (hors serveur)");
            leaveAt = -1;
        }
    }

    private void note(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Reborn] " + msg));
    }

    @Override
    public void tick() {
        if (leaveAt >= 0 && System.currentTimeMillis() - leaveAt > (roles.get(focus).creer() ? 700 : 1300)) finish();
    }

    /* ------------------------------------------------------------- saisie */

    @Override
    public boolean keyPressed(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_Q -> setFocus(Math.floorMod(focus - 1, roles.size()));
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> setFocus(Math.floorMod(focus + 1, roles.size()));
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> enter();
            case GLFW.GLFW_KEY_ESCAPE -> { }
            default -> { return super.keyPressed(e); }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v != 0) { setFocus(Math.floorMod(focus + (v > 0 ? -1 : 1), roles.size())); return true; }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0 || leaveAt >= 0) return super.mouseClicked(e, dbl);
        for (int[] r : hits) {
            if (!dans(e.x(), e.y(), r[0], r[1], r[2], r[3])) continue;
            int a = r[4];
            if (a == -1) enter();
            else if (a == -2) setFocus(Math.floorMod(focus - 1, roles.size()));
            else if (a == -3) setFocus(Math.floorMod(focus + 1, roles.size()));
            else if (a == focus) enter();
            else setFocus(a);
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    /* ------------------------------------------------------------- rendu */

    /** Rang relatif de i par rapport à la position glissée (circulaire, dans [−n/2, n/2]). */
    private float rel(int i) {
        int n = roles.size();
        float r = i - glisse;
        while (r > n / 2f) r -= n;
        while (r < -n / 2f) r += n;
        return r;
    }

    private static float interp(float[] t, float r) {
        float u = Math.max(-2f, Math.min(2f, r)) + 2f;
        int a = (int) Math.floor(u);
        if (a >= 4) return t[4];
        float f = u - a;
        return t[a] + (t[a + 1] - t[a]) * f;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        int d = height - 540;
        T = Math.max(0, d / 2);
        B = d > 0 ? d / 2 : d;
        cx = width / 2;
        Font f = font;
        long now = System.currentTimeMillis();
        hits.clear();
        // glissement circulaire vers le perso choisi
        int n = roles.size();
        float cible = focus;
        float delta = cible - glisse;
        while (delta > n / 2f) delta -= n;
        while (delta < -n / 2f) delta += n;
        glisse += delta * Math.min(1f, dt * 0.18f + 0.06f);
        if (Math.abs(delta) < 0.004f) glisse = cible;
        glisse = ((glisse % n) + n) % n;
        boolean pose = Math.abs(delta) < 0.02f;
        float ui = Math.min(1f, Math.max(0f, (now - openedAt - 400) / 600f));
        if (leaveAt >= 0) ui *= Math.max(0f, 1f - (now - leaveAt) / 400f);
        int K = scene.couleur();

        // ---------------- décor
        scene.fond(g, width, height);
        PersoScene.lotus(g, 0, cx + 188, 474 + B);
        PersoScene.lotus(g, 5, cx - 162, 466 + B);
        PersoScene.lotus(g, 7, cx + 302, 432 + B);
        PersoScene.lotus(g, 5, cx - 352, 446 + B);
        PersoScene.lotus(g, 3, cx + 450, 404 + B);
        PersoScene.lotus(g, 4, cx - 78, 512 + B);
        scene.roseaux(g, width, height);
        scene.lueur(g, cx, 446 + B, K, 0.21f);
        PersoScene.pierre(g, P_15, cx + 132, 422 + B);
        PersoScene.pierre(g, P_11, cx + 320, 377 + B);
        PersoScene.pierre(g, P_15B, cx - 128, 422 + B);
        PersoScene.pierre(g, P_10, cx - 330, 372 + B);
        PersoScene.pierre(g, P_24, cx + 110, 506 + B);
        // reflets dans l'eau (poupées renversées, très atténuées), sous les pierres
        for (int i = 0; i < n; i++) {
            float r = rel(i);
            Role ro = roles.get(i);
            if (Math.abs(r) > 2.6f || ro.creer()) continue;
            float fade = Math.max(0f, Math.min(1f, 2.6f - Math.abs(r)) / 0.6f);
            double stat = Math.max(0.75, Math.min(1.15, ro.size() * PersoCreationScreen.croissance(ro.age())));
            float kk = (float) (interp(SK, r) * stat);
            boolean slim = ro.look() != null ? ro.look().slim : RebornSkins.isSlim(Minecraft.getInstance().player.getUUID());
            float lac = r > 0.3f ? -28f : r < -0.3f ? 28f : 24f;
            PersoPoupee.dessiner(g, skins.get(ro.look(), ro.mort()), slim, cx + interp(SX, r),
                interp(SY, r) + B + (Math.abs(r) < 0.5f ? 7 : 4), kk, lac, 5, argb(0x6A6474, 0.12f * fade), true);
        }
        // pierres des places (fixes) : la grande au centre, deux de chaque côté
        for (int s = -2; s <= 2; s++) {
            if (s != 0 && occupant(s) < 0) continue;
            int px = Math.round(cx + SX[s + 2]), py = Math.round(SY[s + 2] + B);
            if (s == 0) {
                scene.pierreLit(g, LIT_SEL, scene.village(), px, py);
                PersoScene.ensoHalo(g, px, py, 50, 9.5f, K);
            } else if (Math.abs(s) == 1) {
                Role r = roles.get(occupant(s));
                if (r.creer() || r.mort()) PersoScene.pierre(g, P_VIDE, px, py);
                else scene.pierreLit(g, LIT_COTE, villageDe(occupant(s)), px, py);
            } else {
                PersoScene.pierre(g, P_REN, px, py);
            }
        }
        scene.lucioles(width, height, zones(), 12);
        PersoScene.braises(g, cx, 446 + B, K, 30, 46, 9, 110, false, 3, 1f);
        scene.luciolesFond(g, height, 1f);

        // ---------------- personnages : du plus loin au plus proche
        Integer[] ordre = new Integer[n];
        for (int i = 0; i < n; i++) ordre[i] = i;
        java.util.Arrays.sort(ordre, (a, b) -> Float.compare(Math.abs(rel(b)), Math.abs(rel(a))));
        float[][] tete = new float[n][];
        for (int i : ordre) {
            float r = rel(i);
            if (Math.abs(r) > 2.6f) continue;
            float fade = Math.max(0f, Math.min(1f, 2.6f - Math.abs(r)) / 0.6f);
            float x = cx + interp(SX, r), y = interp(SY, r) + B, k = interp(SK, r);
            Role ro = roles.get(i);
            double stat = ro.creer() ? 1.0 : Math.max(0.75, Math.min(1.15, ro.size() * PersoCreationScreen.croissance(ro.age())));
            float kk = (float) (k * stat);
            int col = ro.creer() ? 0xD9A95E : ro.mort() ? 0x8A8690 : PersoScene.couleur(villageDe(i));
            if (Math.abs(r) > 0.5f) PersoScene.braises(g, Math.round(x), Math.round(y), col, ro.creer() || ro.mort() ? 8 : 12,
                24 * k / 4.5f, 5, 60, false, 4 + i, fade);
            tete[i] = new float[]{x, y - 32 * kk};
            boolean principal = i == focus && pose;
            if (ro.creer()) {
                float s = kk / 4.5f;
                int w = Math.round(80 * s), h = Math.round(150 * s);
                etire(g, SILHOUETTE, Math.round(x - 40 * s), Math.round(y - 146 * s), w, h, 0, 0, 80, 150, 80, 150,
                    argb(0xFFFFFF, fade));
            } else {
                Identifier tex = skins.get(ro.look(), ro.mort());
                boolean slim = ro.look() != null ? ro.look().slim : RebornSkins.isSlim(Minecraft.getInstance().player.getUUID());
                float lac = r > 0.3f ? -28f : r < -0.3f ? 28f : 24f;
                PersoScene.ombre(g, Math.round(x), Math.round(y), 0.8f * fade);
                if (principal) {
                    PersoFigure.dessiner(g, tex, slim, Math.round(x), Math.round(y), kk, lac, 6, Math.round(13 * 8.4f),
                        Math.round(19 * 8.4f));
                } else {
                    int teinte = Math.abs(r) > 0.5f ? (ro.mort() ? 0xC0C0C0 : 0xD8D4DC) : 0xFFFFFF;
                    PersoPoupee.dessiner(g, tex, slim, x, y, kk, lac, 5, argb(teinte, fade), false);
                }
            }
            hits.add(new int[]{Math.round(x - 9 * kk), Math.round(y - 33 * kk), Math.round(18 * kk), Math.round(34 * kk), i});
        }
        PersoScene.braises(g, cx, 446 + B, K, 30, 46, 9, 110, true, 3, 1f);
        scene.luciolesDevant(g, 1f);

        // ---------------- étiquettes et repère
        for (int i = 0; i < n; i++) {
            if (tete[i] == null || ui <= 0) continue;
            float r = rel(i);
            Role ro = roles.get(i);
            if (Math.abs(r) < 0.5f) {
                int bob = Math.round((float) Math.sin(now / 300.0) * 2);
                chevron(g, Math.round(tete[i][0]), Math.round(tete[i][1]) - 16 + bob);
                continue;
            }
            float a = ui * Math.max(0f, Math.min(1f, (2.4f - Math.abs(r)) / 0.6f)) * Math.min(1f, Math.abs(r) * 2 - 1);
            if (a <= 0) continue;
            float y = tete[i][1] - 24;
            String nom, sous;
            int cn, cs;
            if (ro.creer()) { nom = "+ NOUVEAU SHINOBI"; sous = "Une nouvelle histoire"; cn = OR; cs = DOUX; }
            else {
                nom = nomComplet(ro);
                sous = ro.mort() ? "Mort · indisponible" : (ro.rang() + (ro.village().isBlank() ? "" : " · " + ro.village()));
                cn = ro.mort() ? 0xFF8A8078 : 0xFFD8CCB0;
                cs = ro.mort() ? 0xFFB0605A : DOUX;
            }
            arcFin(g, f, arcade(nom), tete[i][0], y, alpha(cn, a), 1, true);
            inter(g, f, inter(sous), tete[i][0], y + 13, 7, alpha(cs, a), 1);
        }
        if (ui <= 0) { voile(g, now); return; }

        // ---------------- interface
        g.pose().pushMatrix();
        g.pose().translate(0, T);
        blit(g, PLAQUE_TITRE, cx - 170, 0, 0, 0, 340, 50, 340, 50, argb(0xFFFFFF, ui));
        int nb = (int) roles.stream().filter(r -> !r.creer()).count();
        int libres = Math.max(0, CharacterData.slotLimit() - nb);
        String sous = nb + " personnage" + (nb > 1 ? "s" : "") + (libres > 0 ? " · " + libres + " emplacement" + (libres > 1 ? "s" : "") + " libre" + (libres > 1 ? "s" : "") : "");
        inter(g, f, inter(sous), cx, 46, 8, alpha(0xD2C2B59A, ui), 1);
        PersoUi.chip(g, f, 24, 14, PersoSons.TITRES[scene.village()], false, PersoSons.joue(), false, false, ui);
        g.pose().popMatrix();
        cartouche(g, f, 24, 440 + B, roles.get(focus), ui);
        Role fr = roles.get(focus);
        int by = 478 + B, wb = B_W[B_ENTRER], xb = cx - wb / 2;
        boolean sur = dans(mx, my, xb, by, wb, 20);
        if (fr.mort()) bouton(g, f, B_ENTRER, 2, xb, by, "INDISPONIBLE");
        else bouton(g, f, fr.creer() ? B_CREER : B_ENTRER, sur ? 1 : 0, xb, by, fr.creer() ? "CREER" : null);
        hits.add(0, new int[]{xb, by, wb, 20, -1});
        if (n > 1) {
            for (int s = 0; s < 2; s++) {
                int fx = s == 0 ? xb - 28 : xb + wb + 8;
                boolean h = dans(mx, my, fx, by, 20, 20);
                blit(g, FLECHES, fx - 1, by - 1, (h ? 44 : 0) + s * 22, 0, 22, 23, 88, 23);
                hits.add(0, new int[]{fx, by, 20, 20, s == 0 ? -2 : -3});
            }
        }
        aides(g, f, new String[][]{{"< >", "changer"}, {"ENTREE", "jouer"}, {"CLIC", "choisir"}}, width - 24, 509 + B);
        voile(g, now);
    }

    private void voile(GuiGraphicsExtractor g, long now) {
        float noir = Math.max(0f, 1f - (now - openedAt) / 900f);
        if (leaveAt >= 0) noir = Math.max(noir, Math.min(1f, (now - leaveAt - 300) / 800f));
        if (noir > 0) g.fill(0, 0, width, height, argb(0, noir));
    }

    /** Index du perso dans la place s (−2 … 2) autour du choisi, ou −1. */
    private int occupant(int s) {
        int n = roles.size();
        for (int i = 0; i < n; i++) if (Math.round(rel(i)) == s && Math.abs(rel(i) - s) < 0.5f) return i;
        // pendant le glissement, la pierre reste visible si une place est occupée à l'arrivée
        for (int i = 0; i < n; i++) {
            int r = i - focus;
            while (r > n / 2) r -= n;
            while (r < -n / 2) r += n;
            if (r == s) return i;
        }
        return -1;
    }

    private List<int[]> zones() {
        List<int[]> z = new ArrayList<>();
        z.add(new int[]{cx - 170, 0, cx + 170, 60 + T});
        z.add(new int[]{0, 0, 200, 34 + T});
        z.add(new int[]{cx - 180, 186 + B, cx + 180, 470 + B});
        z.add(new int[]{cx + 180, 214 + B, cx + 310, 404 + B});
        z.add(new int[]{cx + 336, 226 + B, cx + 436, 370 + B});
        z.add(new int[]{cx - 308, 214 + B, cx - 188, 404 + B});
        z.add(new int[]{0, 436 + B, 330, 532 + B});
        z.add(new int[]{cx - 100, 470 + B, width, height});
        return z;
    }

    static String nomComplet(Role r) {
        String n = r.nom() == null ? "" : r.nom().trim();
        String c = r.clan() == null ? "" : r.clan().trim();
        if (c.isEmpty() || "None".equalsIgnoreCase(c) || sansAccents(n).contains(sansAccents(c))) return n;
        return n + " " + PersoCreationScreen.affiche(c);
    }

    /** Fiche courte du perso choisi : emblème du clan, nom, grade · village, clan · sexe · âge · taille. */
    private void cartouche(GuiGraphicsExtractor g, Font f, int x, int y, Role r, float a) {
        blit(g, CARTOUCHE, x - 2, y - 2, 0, 0, 296, 70, 296, 70, argb(0xFFFFFF, a));
        if (r.creer()) {
            emblemeCentre(g, idxClan("autre"), x + 30, y + 32, 28, alpha(OR_CLAIR, a));
            arcadeS(g, f, arcade("NOUVEAU SHINOBI"), x + 60, y + 13, alpha(CREME, a), 1f, 0, true);
            inter(g, f, inter("Une nouvelle histoire commence"), x + 60, y + 33, 9, alpha(CREME, a), 0);
            inter(g, f, inter(CharacterData.characters().size() + " / " + CharacterData.slotLimit() + " personnages"),
                x + 60, y + 50, 8, alpha(DOUX, a), 0);
            return;
        }
        String slug = CharacterRules.slug(r.clan());
        emblemeCentre(g, idxClan(r.clan().isBlank() ? "autre" : slug), x + 30, y + 32, 28, alpha(OR_CLAIR, a));
        arcadeS(g, f, arcade(nomComplet(r)), x + 60, y + 13, alpha(r.mort() ? 0xFFB0605A : CREME, a), 1f, 0, true);
        int vi = CharacterRules.villageIndex(r.village());
        float xx = x + 60;
        if (vi >= 0 && vi <= 5) {
            embleme(g, idxVillage(vi), x + 59, y + 26, 12, alpha(OR_CLAIR, a));
            xx = x + 74;
        }
        String rang = r.mort() ? "Mort" : (r.rang() == null || r.rang().isBlank() ? "—" : r.rang());
        float w1 = inter(g, f, inter(rang), xx, y + 33, 9, alpha(r.mort() ? 0xFFB0605A : CREME, a), 0);
        if (!r.village().isBlank()) {
            inter(g, f, inter("·"), xx + w1 + 4, y + 33, 9, alpha(OR_ETEINT, a), 0);
            inter(g, f, inter(r.village()), xx + w1 + 10, y + 33, 9, alpha(CREME, a), 0);
        }
        g.fill(Math.round(xx), y + 40, Math.round(xx + w1), y + 41, alpha(ROUGE, a));
        int cm = (int) Math.round(180 * r.size() * PersoCreationScreen.croissance(r.age()));
        String clan = r.clan().isBlank() || "None".equalsIgnoreCase(r.clan()) ? "Sans clan" : "Clan " + PersoCreationScreen.affiche(r.clan());
        String l3 = clan + " · " + (r.sexe() == null || r.sexe().isBlank() ? "Homme" : r.sexe()) + " · " + r.age() + " ans · " + cm + " cm";
        inter(g, f, inter(l3), x + 60, y + 50, 8, alpha(DOUX, a), 0);
    }
}
