package fr.reborn.hud.menu.character.scene;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.character.CharacterData;
import fr.reborn.hud.menu.character.CharacterPayload;
import fr.reborn.hud.menu.character.CharacterRules;
import fr.reborn.hud.mixin.HudAccessor;
import fr.reborn.hud.nameplate.Nameplates;
import fr.reborn.hud.skin.CharacterCatalog;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Création de personnage <b>en scène</b> : le perso se tient dans un vide sombre, sur un ensō de lumière ; une
 * question par écran, la caméra change de plan à chaque étape. Ouverture : le perso sort du noir, seul sur son
 * ensō, pendant que trois fragments apparaissent mot à mot en haut (« Avant le nom… / avant le sang… / il n'y avait
 * que le chakra. ») ; en bas, « appuie sur une touche » clignote. Une touche ou un clic lance les questions.
 * <p>Étapes : village → clan → sexe → nom → âge → taille → apparence → récapitulatif. Mêmes règles que la création
 * classique (verrouillage par la candidature whitelist, clan « Autre » personnalisé) et même message serveur :
 * {@code create\n<nom>\n<clan>\n<village>\n<sexe>\n<age>\n<size>\n<apparence…>}.
 */
public final class SceneJourneyScreen extends Screen {

    private static final int GOLD = 0xFFD9A95E, CREAM = 0xFFF5E9D0, SUB = 0xFFC2B59A, MUTED = 0xFF7A6E5C,
            LACQUER = 0xFFA0182B, LOCKED = 0x50F5E9D0;
    private static final int S_VILLAGE = 0, S_CLAN = 1, S_SEXE = 2, S_NOM = 3, S_AGE = 4, S_TAILLE = 5,
            S_LOOK = 6, S_RECAP = 7, STEPS = 8;
    private static final String[] TITLES = {"DE QUEL VILLAGE VIENS-TU ?", "QUEL SANG COULE EN TOI ?",
            "QUI ES-TU ?", "QUEL EST TON NOM ?", "QUEL ÂGE AS-TU ?", "QUELLE EST TA TAILLE ?",
            "À QUOI RESSEMBLES-TU ?", "EST-CE BIEN TOI ?"};
    private static final int[] HAIR_COLORS = {0xFF1A1414, 0xFF3A2416, 0xFF6B4423, 0xFFB8862E, 0xFFE8D27A, 0xFFD9D9D9,
            0xFFC23B3B, 0xFFE58AB8, 0xFF3A4FA0, 0xFF2E6B4A};
    private static final int[] EYE_COLORS = {0xFF5A3A1E, 0xFF1E1A16, 0xFF3A6EA5, 0xFF4E8A4A, 0xFF9A9A9A, 0xFFB8A6D8,
            0xFFC01E35, 0xFFD9A95E};
    private static final String[] LOOK_ROWS = {"CARRURE", "PEAU", "CHEVEUX", "COULEUR", "YEUX", "TENUE"};

    private final Vec3 feet;
    private ClientMannequin actor;
    private int step;
    private int village = -1, clanIdx = -1, sexeIdx;
    private String name = "", customClan = "";
    private int age = 14;
    private double size = 1.0;
    private final SkinSpec look = new SkinSpec();
    private int lookRow, hairIdx, hairColorIdx = 1, eyeIdx, outfitIdx;
    private float skinT = 0.35f;
    private boolean maleSlim;
    private long openedAt, stepAt, leaveAt = -1, questionsAt;
    private int prevStep = -1, cursor;
    private float selY;
    private int intro;
    private long introAt, errorAt;
    private String error = "";
    private boolean prevHidden;
    /** Zones cliquables de l'étape courante : {x, y, w, h, action}. */
    private final List<int[]> hits = new ArrayList<>();

    public SceneJourneyScreen(Vec3 feet) {
        super(Component.literal("Nouveau shinobi"));
        this.feet = feet;
        look.skinColor = SkinSpec.skinRamp(skinT);
        look.hairColor = HAIR_COLORS[hairColorIdx];
        // pré-remplissage depuis la candidature whitelist
        String cv = CharacterData.candidatureVillage();
        String[] vs = CharacterRules.villages();
        for (int i = 0; cv != null && i < vs.length; i++) if (vs[i].equalsIgnoreCase(cv)) village = i;
        if (village < 0) for (int i = 0; i < vs.length; i++) if (!CharacterRules.villageLocked(vs[i])) { village = i; break; }
        presetClan();
        String cn = CharacterData.candidatureName();
        if (cn != null) name = cn;
        applyLists();
    }

    /** Ouvre la création au-dessus du joueur (vide sombre). */
    public static SceneJourneyScreen open(Minecraft mc) {
        return new SceneJourneyScreen(mc.player.position().add(0, 40, 0));
    }

    private void presetClan() {
        String[] cs = CharacterRules.clansOf(village);
        clanIdx = -1;
        String cc = CharacterData.candidatureClan();
        for (int i = 0; cc != null && i < cs.length; i++) if (cs[i].equalsIgnoreCase(cc)) clanIdx = i;
        if (clanIdx < 0) for (int i = 0; i < cs.length; i++) if (!CharacterRules.clanLocked(cs[i])) { clanIdx = i; break; }
    }

    private String clanName() {
        String[] cs = CharacterRules.clansOf(village);
        if (clanIdx < 0 || clanIdx >= cs.length) return "";
        return "Autre".equals(cs[clanIdx]) ? customClan.trim() : cs[clanIdx];
    }

    private String sexe() { return sexeIdx == 1 ? "Femme" : "Homme"; }

    /* ------------------------------------------------------------- cycle de vie */

    @Override
    protected void init() {
        if (actor != null) return;
        Minecraft mc = Minecraft.getInstance();
        prevHidden = mc.gui.hud.isHidden();
        ((HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        actor = SceneActors.spawn(mc, -424300, feet, 0, look, "scene_idle.json", 0);
        SceneActors.setBody(actor, age, size);
        SceneSounds.startHum();
        introAt = openedAt = System.currentTimeMillis();
        stepAt = questionsAt = Long.MAX_VALUE / 4;
        // plan d'ouverture : face au perso, centré, qui s'approche très lentement
        SceneCamera.start(SceneCamera.Shot.look(feet.add(0, 2.1, 5.6), feet.add(0, 1.0, 0), 50));
        SceneCamera.moveTo(SceneCamera.Shot.look(feet.add(0, 1.45, 3.7), feet.add(0, 0.95, 0), 50), 9000);
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (actor != null) SceneActors.remove(mc, actor);
        actor = null;
        thumbs.releaseAll();
        SceneCamera.stop();
        if (!(Minecraft.getInstance().gui.screen() instanceof SceneSelectScreen)) SceneSounds.stopHum();
        ((HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHidden);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    /* ----------------------------------------------------------------- caméra */

    private double stature() { return actor == null ? 1.0 : SceneActors.scaleOf(actor); }

    private SceneCamera.Shot framing(int s) {
        double k = stature();
        double eye = 1.62 * k;
        return switch (s) {
            case S_NOM -> SceneCamera.Shot.look(feet.add(0.32, eye + 0.04, 1.35), feet.add(0.62, eye - 0.04, 0), 44);
            case S_LOOK -> lookRow <= 1 || lookRow == 4
                    ? SceneCamera.Shot.look(feet.add(0.45, eye, 1.9), feet.add(0.75, eye - 0.12, 0), 46)
                    : SceneCamera.Shot.look(feet.add(0.62, 1.15 * k + 0.1, 2.75), feet.add(0.82, 1.0 * k, 0), 50);
            case S_AGE, S_TAILLE -> SceneCamera.Shot.look(feet.add(0.75, 1.0, 3.3), feet.add(0.98, 0.95, 0), 50);
            default -> SceneCamera.Shot.look(feet.add(0.62, 1.15 * k + 0.1, 2.75), feet.add(0.82, 1.0 * k, 0), 50);
        };
    }

    private SceneCamera.Shot orbit(double t) {
        double a = Math.toRadians(-28 + t * 7);
        Vec3 from = feet.add(Math.sin(a) * 2.9, 1.3, Math.cos(a) * 2.9);
        Vec3 right = new Vec3(Math.cos(a), 0, -Math.sin(a));
        return SceneCamera.Shot.look(from, feet.add(0, 1.0 * stature(), 0).add(right.scale(0.85)), 50);
    }

    /* ---------------------------------------------------------------- étapes */

    public void setStep(int s) {
        prevStep = step;
        step = Math.max(0, Math.min(STEPS - 1, s));
        stepAt = System.currentTimeMillis();
        cursor = 0;
        SceneSounds.whoosh();
        if (step == S_RECAP) {
            SceneActors.play(actor, "scene_crossed.json", 0);
            SceneCamera.moveTo(orbit(0), 1500);
        } else {
            SceneActors.play(actor, "scene_idle.json", 0);
            SceneCamera.moveTo(framing(step), 1400);
        }
    }

    private boolean validate(int s) {
        switch (s) {
            case S_VILLAGE -> { if (village < 0) return fail("Choisis un village."); }
            case S_CLAN -> {
                String[] cs = CharacterRules.clansOf(village);
                if (clanIdx < 0 || clanIdx >= cs.length) return fail("Choisis un clan.");
                if ("Autre".equals(cs[clanIdx])) {
                    if (customClan.isBlank()) return fail("Écris le nom de ton clan.");
                    if (CharacterRules.isPredefinedClan(customClan)) {
                        return fail("« " + customClan.trim() + " » est un clan existant : choisis-le dans la liste.");
                    }
                }
            }
            case S_NOM -> { if (name.isBlank()) return fail("Écris ton prénom."); }
            default -> { }
        }
        return true;
    }

    private boolean fail(String msg) {
        error = msg;
        errorAt = System.currentTimeMillis();
        RebornSounds.deny();
        return false;
    }

    private void next() {
        if (!validate(step)) return;
        if (step < S_RECAP) setStep(step + 1);
    }

    private void back() {
        if (step > 0) setStep(step - 1);
        else Minecraft.getInstance().setScreenAndShow(SceneSelectScreen.fromRoster(Minecraft.getInstance()));
    }

    public void setVillage(int v) {
        String[] vs = CharacterRules.villages();
        if (v < 0 || v >= vs.length) return;
        if (CharacterRules.villageLocked(vs[v])) { fail("Ta candidature t'attache à un autre village."); return; }
        if (v != village) { village = v; presetClan(); customClan = ""; SceneSounds.tick(); }
    }

    public void setClan(int c) {
        String[] cs = CharacterRules.clansOf(village);
        if (c < 0 || c >= cs.length) return;
        if (CharacterRules.clanLocked(cs[c])) { fail("Ta candidature t'attache à un autre clan."); return; }
        if (c != clanIdx) SceneSounds.tick();
        clanIdx = c;
        applyLists();
        if (actor != null) SceneActors.reskin(actor, look);
    }

    public void setSexe(int s) {
        if (s != sexeIdx) SceneSounds.tick();
        sexeIdx = s;
        look.female = s == 1;
        look.slim = look.female || maleSlim;
        hairIdx = outfitIdx = 0;
        applyLists();
        if (actor != null) SceneActors.reskin(actor, look);
    }

    public void setAge(int a) { age = Math.max(10, Math.min(60, a)); SceneActors.setBody(actor, age, size); }

    public void setSize(double s) { size = Math.max(0.85, Math.min(1.15, s)); SceneActors.setBody(actor, age, size); }

    public void tweak(int row, int dir) {
        lookRow = row;
        switch (row) {
            case 0 -> { if (!look.female) { maleSlim = !maleSlim; look.slim = maleSlim; } }
            case 1 -> { skinT = Math.max(0f, Math.min(1f, skinT + dir * 0.1f)); look.skinColor = SkinSpec.skinRamp(skinT); }
            case 2 -> hairIdx = SkinSpec.cycle(hairIdx, Math.max(1, hairs().size()), dir);
            case 3 -> { hairColorIdx = SkinSpec.cycle(hairColorIdx, HAIR_COLORS.length, dir); look.hairColor = HAIR_COLORS[hairColorIdx]; }
            case 4 -> { eyeIdx = SkinSpec.cycle(eyeIdx, EYE_COLORS.length, dir); look.eyeColor = look.eyeColorRight = EYE_COLORS[eyeIdx]; }
            default -> outfitIdx = SkinSpec.cycle(outfitIdx, Math.max(1, outfits().size()), dir);
        }
        applyLists();
        if (actor != null) SceneActors.reskin(actor, look);
        SceneCamera.moveTo(framing(S_LOOK), 900);
    }

    private void reskin() {
        if (actor != null) SceneActors.reskin(actor, look);
        SceneSounds.tick();
    }

    public void selectLookRow(int row) {
        int nr = Math.max(0, Math.min(LOOK_ROWS.length - 1, row));
        if (nr != lookRow) { SceneSounds.tick(); lookScroll = 0; }
        lookRow = nr;
        if (lookRow == 3 || lookRow == 4) syncHsv();
        SceneCamera.moveTo(framing(S_LOOK), 900);
    }

    private void applyLists() {
        List<CharacterCatalog.Asset> h = hairs(), o = outfits();
        look.hairId = h.isEmpty() ? "" : h.get(Math.min(hairIdx, h.size() - 1)).id;
        look.outfitId = o.isEmpty() ? "" : o.get(Math.min(outfitIdx, o.size() - 1)).id;
    }

    private List<CharacterCatalog.Asset> hairs() { return available("hair"); }

    private List<CharacterCatalog.Asset> outfits() { return available("outfit"); }

    private List<CharacterCatalog.Asset> available(String cat) {
        try { return CharacterCatalog.available(cat, look.female, clanName(), CharacterData.staffExempt()); }
        catch (RuntimeException e) { return new ArrayList<>(); }
    }

    /** Envoie la création au serveur (même format que la création classique). */
    private void submit() {
        for (int s = 0; s < S_RECAP; s++) if (!validate(s)) { setStep(s); return; }
        String cmd = "create\n" + name.trim() + "\n" + clanName() + "\n" + CharacterRules.villages()[village]
                + "\n" + sexe() + "\n" + age + "\n" + String.format(Locale.US, "%.2f", size) + "\n" + look.serialize();
        if (!ClientPlayNetworking.canSend(CharacterPayload.ID)) { fail("Création impossible hors du serveur."); return; }
        ClientPlayNetworking.send(new CharacterPayload(cmd));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) RebornSkins.applySpec(mc.player.getUUID(), look);
        leaveAt = System.currentTimeMillis();
        RebornSounds.confirm();
        SceneCamera.moveTo(SceneCamera.Shot.look(feet.add(0, 1.55 * stature(), 1.9), feet.add(0, 1.35 * stature(), 0), 46), 1600);
    }

    /* ------------------------------------------------------------------ intro */

    public void beginScene() {
        if (intro >= 2) return;
        intro = 2;
        stepAt = questionsAt = System.currentTimeMillis();
        RebornSounds.confirm();
        SceneCamera.moveTo(framing(0), 2600);
    }



    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (step == S_RECAP && leaveAt < 0 && now - stepAt > 1500) SceneCamera.start(orbit((now - stepAt - 1500) / 1000.0));
        if (leaveAt >= 0 && now - leaveAt > 2600) onClose();   // le serveur confirme et ferme aussi (« selected »)
    }

    /* ------------------------------------------------------------- saisie */

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (leaveAt >= 0) return true;
        if (intro < 2) { if (System.currentTimeMillis() - introAt > 700) beginScene(); return true; }
        boolean typing = step == S_NOM || (step == S_CLAN && isCustomClan());
        if (k == GLFW.GLFW_KEY_ESCAPE) { back(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            if (step == S_RECAP) { if (cursor == 0) submit(); else setStep(0); }
            else next();
            return true;
        }
        if (k == GLFW.GLFW_KEY_BACKSPACE) {
            if (step == S_NOM && !name.isEmpty()) { name = name.substring(0, name.length() - 1); SceneSounds.key(); return true; }
            if (step == S_CLAN && isCustomClan() && !customClan.isEmpty()) {
                customClan = customClan.substring(0, customClan.length() - 1);
                return true;
            }
            if (!typing) back();
            return true;
        }
        boolean up = k == GLFW.GLFW_KEY_UP, down = k == GLFW.GLFW_KEY_DOWN;
        boolean left = k == GLFW.GLFW_KEY_LEFT, right = k == GLFW.GLFW_KEY_RIGHT;
        switch (step) {
            case S_VILLAGE -> { if (up) setVillage(stepFree(village, -1, true)); if (down) setVillage(stepFree(village, 1, true)); }
            case S_CLAN -> { if (up) setClan(stepFree(clanIdx, -1, false)); if (down) setClan(stepFree(clanIdx, 1, false)); }
            case S_SEXE -> { if (up || left) setSexe(0); if (down || right) setSexe(1); }
            case S_AGE -> { if (left || down) setAge(age - 1); if (right || up) setAge(age + 1); }
            case S_TAILLE -> { if (left || down) setSize(size - 0.01); if (right || up) setSize(size + 0.01); }
            case S_LOOK -> {
                if (up) selectLookRow(lookRow - 1);
                if (down) selectLookRow(lookRow + 1);
                if (left) tweak(lookRow, -1);
                if (right) tweak(lookRow, 1);
            }
            case S_RECAP -> { if (up) cursor = 0; if (down) cursor = 1; }
            default -> { return super.keyPressed(e); }
        }
        return true;
    }

    /** Prochaine entrée non verrouillée dans le sens {@code dir}. */
    private int stepFree(int from, int dir, boolean villages) {
        String[] arr = villages ? CharacterRules.villages() : CharacterRules.clansOf(village);
        int n = villages ? villageCount() : arr.length;
        for (int i = from + dir; i >= 0 && i < n; i += dir) {
            boolean locked = villages ? CharacterRules.villageLocked(arr[i]) : CharacterRules.clanLocked(arr[i]);
            if (!locked) return i;
        }
        return from;
    }

    /** Villages proposés à la création (sans « Déserteur »). */
    private static int villageCount() {
        String[] vs = CharacterRules.villages();
        int n = vs.length;
        while (n > 0 && vs[n - 1].toLowerCase(Locale.ROOT).startsWith("d")) n--;
        return n;
    }

    private boolean isCustomClan() {
        String[] cs = CharacterRules.clansOf(village);
        return clanIdx >= 0 && clanIdx < cs.length && "Autre".equals(cs[clanIdx]);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (intro < 2 || leaveAt >= 0 || !e.isAllowedChatCharacter()) return false;
        String c = e.codepointAsString();
        if (step == S_NOM && name.length() < 24 && (!c.isBlank() || !name.isEmpty())) { name += c; SceneSounds.key(); return true; }
        if (step == S_CLAN && isCustomClan() && customClan.length() < 24) { customClan += c; SceneSounds.key(); return true; }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0) return super.mouseClicked(e, dbl);
        if (intro < 2) { if (System.currentTimeMillis() - introAt > 700) beginScene(); return true; }
        if (leaveAt >= 0) return true;
        int mx = (int) e.x(), my = (int) e.y();
        if (step == S_LOOK) {
            for (int[] b : bars) {
                if (mx >= b[0] && my >= b[1] && mx < b[0] + b[2] && my < b[1] + b[3]) {
                    dragBar = b[4];
                    dragTo(dragBar, e.x());
                    return true;
                }
            }
        }
        for (int[] r : hits) {
            if (mx < r[0] || my < r[1] || mx >= r[0] + r[2] || my >= r[1] + r[3]) continue;
            int act = r[4];
            if (act == -1) { if (step == S_RECAP) submit(); else next(); }
            else if (act == -3) setStep(0);
            else if (act >= 5000) { if (!look.female) { maleSlim = act == 5001; look.slim = maleSlim; reskin(); } }
            else if (act >= 4000) {
                int[] pr = lookRow == 3 ? HAIR_COLORS : EYE_COLORS;
                int ci = act - 4000;
                if (lookRow == 3) hairColorIdx = ci; else eyeIdx = ci;
                setLookColor(pr[ci]);
                syncHsv();
                SceneSounds.tick();
            }
            else if (act >= 3000) { outfitIdx = act - 3000; applyLists(); reskin(); }
            else if (act >= 2000) { hairIdx = act - 2000; applyLists(); reskin(); }
            else switch (step) {
                case S_VILLAGE -> setVillage(act);
                case S_CLAN -> setClan(act);
                case S_SEXE -> setSexe(act);
                case S_LOOK -> selectLookRow(act);
                default -> { }
            }
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    /* ------------------------------------------------------------------ rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
        long now = System.currentTimeMillis();
        int w = width, h = height;
        hits.clear();
        int bar = Math.round(16 * Math.min(1f, Math.max(0f, (now - openedAt) / 900f)));
        for (int i = 0; i < 60; i++) {
            int al = Math.round(110 * (1 - i / 60f));
            g.fill(i, 0, i + 1, h, al << 24);
            g.fill(w - 1 - i, 0, w - i, h, al << 24);
        }
        float scene = Math.min(1f, Math.max(0f, (now - openedAt) / 1500f));
        SceneFx.aura(g, feet, 0.55 * stature(), scene, CharacterRules.villageColor(village), 1, w, h);
        SceneFx.fireflies(g, w, h, scene, 22);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);

        float target = step == S_VILLAGE ? village : step == S_CLAN ? clanIdx : step == S_SEXE ? sexeIdx
                : step == S_LOOK ? lookRow : step == S_RECAP ? cursor : selY;
        selY += (target - selY) * Math.min(1f, d * 0.45f + 0.18f);
        float ui = Math.min(1f, Math.max(0f, (now - questionsAt - 700) / 600f));
        if (leaveAt >= 0) ui *= Math.max(0f, 1f - (now - leaveAt) / 500f);
        float in = Math.min(1f, (now - stepAt - (step == S_NOM ? 600 : 300)) / 500f);
        float a = ui * Math.max(0f, in);
        int qx = Math.round(w * 0.55f) + Math.round((1 - Math.max(0f, in)) * 14);

        float out = Math.min(1f, (now - stepAt) / 320f);
        if (prevStep >= 0 && prevStep != step && out < 1f) {
            drawStep(g, prevStep, ui * (1f - out), Math.round(w * 0.55f) - Math.round(out * 26), now, false);
        }
        drawStep(g, step, a, qx, now, a > 0.5f);
        float wipe = Math.min(1f, Math.max(0f, (now - stepAt - 200) / 450f));
        if (wipe > 0f && wipe < 1f && ui > 0f) {
            int wy = titleY(step) + 26;
            int wx0 = Math.round(w * 0.55f) - 10, len = Math.round(260 * wipe);
            for (int i = 0; i < len; i++) {
                float fade = 1f - i / (float) Math.max(1, len);
                g.fill(wx0 + i, wy, wx0 + i + 1, wy + 1, a(GOLD, ui * fade * (1f - wipe) * 1.6f));
            }
        }

        if (!error.isEmpty() && now - errorAt < 2600) {
            float ea = Math.min(1f, (2600 - (now - errorAt)) / 400f);
            text(g, RebornFont.body(error), Math.round(w * 0.55f), h - bar - 68, a(0xFFE0505A, ea * ui), 1f);
        }
        for (int i = 0; i < STEPS; i++) {
            int x = 24 + i * 14;
            g.fill(x, h - bar - 20, x + 8, h - bar - 17, a(i <= step ? (i == step ? GOLD : 0xFF8A6A2A) : 0x40F5E9D0, ui));
        }
        if (step != S_RECAP && ui > 0.5f) {
            Component nx = RebornFont.display("CONTINUER");
            int bw = font.width(nx) + 30, bx = w - 28 - bw, by = h - bar - 48;
            boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + 20;
            g.fill(bx, by, bx + bw, by + 20, a(hov ? 0xE03A1A1E : 0xD0150A0D, ui));
            g.fill(bx, by, bx + bw, by + 1, a(GOLD, ui));
            g.fill(bx, by + 19, bx + bw, by + 20, a(GOLD, ui));
            text(g, nx, bx + 15, by + 6, a(CREAM, ui), 1f);
            hits.add(new int[]{bx, by, bw, 20, -1});
        }
        String keys = switch (step) {
            case S_NOM -> "Écris ton prénom     Entrée : continuer     Échap : revenir";
            case S_AGE, S_TAILLE -> "← → : régler     Entrée : valider     Échap : revenir";
            case S_LOOK -> "↑ ↓ : ligne     ← → : changer     Entrée : valider     Échap : revenir";
            default -> "↑ ↓ : choisir     Entrée : valider     Échap : revenir";
        };
        Component kc = RebornFont.body(keys);
        text(g, kc, w - 24 - font.width(kc), h - bar - 22, a(SUB, ui), 1f);

        float black = Math.max(0f, 1f - (now - openedAt) / 2200f);
        if (leaveAt >= 0) black = Math.max(black, Math.min(1f, (now - leaveAt - 1100) / 1100f));
        if (black > 0) g.fill(0, 0, w, h, Math.round(255 * black) << 24);
        if (intro < 2 || now - questionsAt < 700) intro(g, now, w, h);
    }

    private int titleY(int s) { return s == S_NOM || s == S_AGE || s == S_TAILLE ? height / 2 - 60 : 60; }

    /** Contenu d'une étape. {@code live} : enregistre les zones cliquables. */
    private void drawStep(GuiGraphicsExtractor g, int s, float a, int qx, long now, boolean live) {
        int w = width, h = height;
        int ty = titleY(s);
        title(g, TITLES[s], qx, ty, a);
        int y0 = ty + 48;
        switch (s) {
            case S_VILLAGE -> {
                String[] vs = CharacterRules.villages();
                selected(g, qx, Math.round(y0 + selY * 24), 250, a);
                for (int i = 0; i < villageCount(); i++) {
                    int y = y0 + i * 24;
                    boolean on = i == village, locked = CharacterRules.villageLocked(vs[i]);
                    String k = CharacterRules.villageKey(i);
                    if (k != null) logo(g, k, qx + 8, y + 5, 14, a(on ? 0xFFF5E9D0 : 0x907A6E5C, a));
                    Component lbl = RebornFont.display(vs[i].toUpperCase(Locale.ROOT));
                    text(g, lbl, qx + 24, y, a(locked ? LOCKED : on ? CREAM : MUTED, a), 1.1f);
                    if (locked) g.fill(qx + 22, y + 4, qx + 26 + Math.round(font.width(lbl) * 1.1f), y + 5, a(LOCKED, a));
                    if (live) hits.add(new int[]{qx - 8, y - 6, 258, 22, i});
                }
            }
            case S_CLAN -> {
                String[] cs = CharacterRules.clansOf(village);
                int row = cs.length > 9 ? 20 : 24;
                selected(g, qx, Math.round(y0 + selY * row), 250, a);
                for (int i = 0; i < cs.length; i++) {
                    int y = y0 + i * row;
                    boolean on = i == clanIdx, locked = CharacterRules.clanLocked(cs[i]);
                    g.fill(qx + 4, y + 1, qx + 12, y + 9, a(CharacterRules.clanColor(cs[i]), a * (locked ? 0.3f : 1f)));
                    text(g, RebornFont.display(cs[i].toUpperCase(Locale.ROOT)), qx + 22, y,
                            a(locked ? LOCKED : on ? CREAM : MUTED, a), 1.05f);
                    if (live) hits.add(new int[]{qx - 8, y - 5, 258, row - 2, i});
                }
                int y = y0 + cs.length * row + 10;
                if (isCustomClan()) {
                    String v = customClan + ((now / 500) % 2 == 0 ? "_" : "");
                    text(g, RebornFont.display(v.isEmpty() ? "_" : v.toUpperCase(Locale.ROOT)), qx, y, a(CREAM, a), 1.3f);
                    g.fill(qx, y + 16, qx + 240, y + 17, a(GOLD, a));
                    para(g, "Écris le nom de ton clan ou de ta famille.", qx, y + 24, a);
                }
            }
            case S_SEXE -> {
                String[] opts = {"HOMME", "FEMME"};
                selected(g, qx, Math.round(y0 + selY * 28), 200, a);
                for (int i = 0; i < 2; i++) {
                    int y = y0 + i * 28;
                    text(g, RebornFont.display(opts[i]), qx + 8, y, a(i == sexeIdx ? CREAM : MUTED, a), 1.3f);
                    if (live) hits.add(new int[]{qx - 8, y - 6, 208, 24, i});
                }
            }
            case S_NOM -> {
                String n = name + (((now / 500) % 2 == 0) ? "_" : "");
                text(g, RebornFont.display(n.isEmpty() ? "_" : n.toUpperCase(Locale.ROOT)), qx, y0 - 2, a(CREAM, a), 1.8f);
                g.fill(qx, y0 + 24, qx + Math.min(300, w - qx - 30), y0 + 25, a(GOLD, a));
            }
            case S_AGE -> {
                text(g, RebornFont.display(age + " ANS"), qx, y0 - 2, a(CREAM, a), 2.2f);
                ruler(g, qx, y0 + 36, 260, (age - 10) / 50f, a);
                text(g, RebornFont.body(age < 13 ? "Encore à l'Académie : ton corps n'a pas fini de grandir."
                        : age < 17 ? "Jeune genin : tu grandis encore un peu." : "Ton corps a fini de grandir."),
                        qx, y0 + 50, a(SUB, a), 1f);
            }
            case S_TAILLE -> {
                int cm = (int) Math.round(180 * stature());
                text(g, RebornFont.display(cm + " CM"), qx, y0 - 2, a(CREAM, a), 2.2f);
                ruler(g, qx, y0 + 36, 260, (float) ((size - 0.85) / 0.30), a);
                text(g, RebornFont.body("Taille adulte " + Math.round(180 * size) + " cm"
                        + (age < 17 ? " — pour l'instant, tu fais " + cm + " cm." : ".")), qx, y0 + 50, a(SUB, a), 1f);
            }
            case S_LOOK -> {
                selected(g, qx, Math.round(y0 + selY * 24), 92, a);
                for (int i = 0; i < LOOK_ROWS.length; i++) {
                    int y = y0 + i * 24;
                    text(g, RebornFont.display(LOOK_ROWS[i]), qx + 4, y, a(i == lookRow ? CREAM : MUTED, a), 1f);
                    if (live) hits.add(new int[]{qx - 8, y - 6, 100, 22, i});
                }
                int cx2 = qx + 112, pw = Math.min(290, w - cx2 - 24);
                g.fill(cx2 - 12, y0 - 6, cx2 - 11, y0 + 6 * 24 + 30, a(0x40D9A95E, a));
                drawLookPanel(g, lookRow, cx2, y0, pw, a, live);
            }
            default -> {
                String clan = clanName();
                g.pose().pushMatrix();
                g.pose().translate(qx + 110, ty + 62);
                g.pose().scale(1.6f, 1.6f);
                Nameplates.drawVillagePlate(g, font, 0, 0, (name.trim() + " " + clan).trim(),
                        CharacterRules.clanColor(clan), village >= 0 ? CharacterRules.villages()[village] : "", a);
                g.pose().popMatrix();
                String[][] rows = {{"VILLAGE", village >= 0 ? CharacterRules.villages()[village] : "—"},
                        {"CLAN", clan.isEmpty() ? "—" : clan}, {"SEXE", sexe()}, {"ÂGE", age + " ans"},
                        {"TAILLE", Math.round(180 * stature()) + " cm"}};
                int ry = ty + 80;
                for (int i = 0; i < rows.length; i++) {
                    text(g, RebornFont.display(rows[i][0]), qx, ry + i * 20, a(GOLD, a), 1f);
                    text(g, RebornFont.body(rows[i][1]), qx + 84, ry + i * 20, a(CREAM, a), 1f);
                }
                int by = ry + rows.length * 20 + 16;
                g.fill(qx, by - 8, qx + 280, by - 7, a(0x60D9A95E, a));
                selected(g, qx, Math.round(by + selY * 28), 200, a);
                text(g, RebornFont.display("OUI, C'EST MOI"), qx + 4, by + 1, a(cursor == 0 ? CREAM : MUTED, a), 1.1f);
                text(g, RebornFont.display("RECOMMENCER"), qx + 4, by + 29, a(cursor == 1 ? CREAM : MUTED, a), 1.1f);
                if (live) {
                    hits.add(new int[]{qx - 8, by - 6, 208, 24, -1});
                    hits.add(new int[]{qx - 8, by + 22, 208, 24, -3});
                }
            }
        }
    }

    private static final String[][] WHISPERS = {{"Avant", "le", "nom…"}, {"avant", "le", "sang…"},
            {"il", "n'y", "avait", "que", "le", "chakra."}};
    private static final long[] WHISPER_AT = {1800, 3500, 5200};

    /**
     * Ouverture, sur la scène : trois fragments apparaissent mot à mot en haut de l'écran (chaque mot monte
     * légèrement en se révélant), « appuie sur une touche » clignote en bas. Tout s'efface quand on lance.
     */
    private void intro(GuiGraphicsExtractor g, long now, int w, int h) {
        long t = now - introAt;
        float leave = intro < 2 ? 1f : Math.max(0f, 1f - (now - questionsAt) / 700f);
        int cx = w / 2;
        for (int l = 0; l < WHISPERS.length; l++) {
            String[] words = WHISPERS[l];
            boolean last = l == WHISPERS.length - 1;
            float sc = last ? 1.3f : 1.05f;
            int col = last ? 0xFFF2C878 : CREAM;
            float total = 0;
            for (String wd : words) total += font.width(RebornFont.display(wd.toUpperCase(Locale.ROOT) + " ")) * sc;
            float x = cx - total / 2f;
            int ly = 48 + l * 20;
            for (int i = 0; i < words.length; i++) {
                Component c = RebornFont.display(words[i].toUpperCase(Locale.ROOT) + " ");
                long at = WHISPER_AT[l] + i * 260L;
                float wa = Math.min(1f, Math.max(0f, (t - at) / 650f));
                float rise = (1f - wa) * 6;
                if (wa > 0) text(g, c, x, ly + rise, a(col, wa * leave * (l == 0 ? 0.7f : l == 1 ? 0.82f : 1f)), sc);
                x += font.width(c) * sc;
            }
        }
        if (t > 1400) {
            float in = Math.min(1f, (t - 1400) / 800f);
            float blink = 0.3f + 0.7f * (float) (0.5 + 0.5 * Math.cos((t - 1400) / 1000.0 * 3.0));
            Component k = RebornFont.display("APPUIE SUR UNE TOUCHE");
            text(g, k, cx - font.width(k) / 2f, h - 46, a(CREAM, in * blink * leave), 1f);
        }
    }

    /* ----------------------------------------------- apparence : panneau de droite */

    private final SkinThumbs thumbs = new SkinThumbs();
    /** Barres glissables : {x, y, w, h, id} — 0 peau, 1 teinte, 2 saturation, 3 luminosité. */
    private final List<int[]> bars = new ArrayList<>();
    private int dragBar = -1, lookScroll;
    private float pkH, pkS, pkV;

    private void drawLookPanel(GuiGraphicsExtractor g, int cat, int x, int y, int pw, float a, boolean live) {
        if (live) bars.clear();
        switch (cat) {
            case 0 -> {
                String[] o = {"LARGE", "FINE"};
                int cur = look.slim ? 1 : 0;
                for (int i = 0; i < 2; i++) {
                    int yy = y + i * 26;
                    boolean dis = look.female && i == 0;
                    if (i == cur) selected(g, x, yy, 150, a);
                    text(g, RebornFont.display(o[i]), x + 6, yy, a(dis ? LOCKED : i == cur ? CREAM : MUTED, a), 1.1f);
                    if (live && !dis) hits.add(new int[]{x - 8, yy - 6, 158, 22, 5000 + i});
                }
            }
            case 1 -> {
                int by = y + 4;
                for (int i = 0; i < pw; i++) {
                    g.fill(x + i, by, x + i + 1, by + 12, a(SkinSpec.skinRamp(i / (float) (pw - 1)), a));
                }
                outline(g, x - 1, by - 1, pw + 2, 14, a(0x80D9A95E, a));
                knob(g, x + Math.round(skinT * (pw - 1)), by - 3, 18, a);
                if (live) bars.add(new int[]{x, by - 4, pw, 20, 0});
                g.fill(x, by + 26, x + 34, by + 44, a(look.skinColor, a));
                outline(g, x - 1, by + 25, 36, 20, a(0x80D9A95E, a));
            }
            case 2 -> grid(g, hairs(), true, x, y, pw, a, live);
            case 3, 4 -> {
                int[] presets = cat == 3 ? HAIR_COLORS : EYE_COLORS;
                int cur = cat == 3 ? look.hairColor : look.eyeColor;
                int sw = 22, gap = 5, cols = Math.max(1, (pw + gap) / (sw + gap));
                for (int i = 0; i < presets.length; i++) {
                    int sx = x + (i % cols) * (sw + gap), sy = y + (i / cols) * (sw + gap);
                    g.fill(sx, sy, sx + sw, sy + sw, a(presets[i], a));
                    if ((presets[i] & 0xFFFFFF) == (cur & 0xFFFFFF)) outline(g, sx - 2, sy - 2, sw + 4, sw + 4, a(GOLD, a));
                    if (live) hits.add(new int[]{sx, sy, sw, sw, 4000 + i});
                }
                int hy = y + ((presets.length + cols - 1) / cols) * (sw + gap) + 12;
                hsvBars(g, x, hy, pw, a, live);
            }
            default -> grid(g, outfits(), false, x, y, pw, a, live);
        }
    }

    /** Grille de vignettes : têtes (coiffures) ou corps entiers (tenues), composées sur le perso en cours. */
    private void grid(GuiGraphicsExtractor g, List<CharacterCatalog.Asset> list, boolean hair, int x, int y, int pw,
                      float a, boolean live) {
        int tw = hair ? 38 : 30, th = hair ? 38 : 54, gap = 6;
        int cols = Math.max(1, (pw + gap) / (tw + gap)), rows = hair ? 4 : 3;
        int maxScroll = Math.max(0, (list.size() + cols - 1) / cols - rows);
        lookScroll = Math.max(0, Math.min(maxScroll, lookScroll));
        String curId = hair ? look.hairId : look.outfitId;
        String curName = "";
        for (int k = 0; k < rows * cols; k++) {
            int i = lookScroll * cols + k;
            if (i >= list.size()) break;
            CharacterCatalog.Asset as = list.get(i);
            int tx = x + (k % cols) * (tw + gap), ty = y + (k / cols) * (th + gap);
            boolean on = as.id.equals(curId);
            if (on) curName = as.name;
            g.fill(tx, ty, tx + tw, ty + th, a(on ? 0x60D9A95E : 0x50000000, a));
            outline(g, tx, ty, tw, th, a(on ? GOLD : 0x40D9A95E, a));
            SkinSpec sp = SkinSpec.deserialize(look.serialize());
            if (hair) sp.hairId = as.id; else sp.outfitId = as.id;
            String key = (hair ? "h|" : "o|") + sp.serialize().hashCode();
            Identifier id = thumbs.get(key, sp);
            if (id != null && a > 0.05f) {
                if (hair) SkinThumbs.head(g, id, tx + 3, ty + 3, 4);
                else {
                    g.pose().pushMatrix();
                    g.pose().translate(tx + 3, ty + 3);
                    g.pose().scale(1.5f, 1.5f);
                    SkinThumbs.body(g, id, 0, 0, 1, sp.slim);
                    g.pose().popMatrix();
                }
            }
            if (live) hits.add(new int[]{tx, ty, tw, th, (hair ? 2000 : 3000) + i});
        }
        int gy = y + rows * (th + gap) + 2;
        text(g, RebornFont.display(curName.isEmpty() ? "—" : curName.toUpperCase(Locale.ROOT)), x, gy, a(CREAM, a), 1f);
        if (maxScroll > 0) {
            Component sc = RebornFont.body((lookScroll + 1) + " / " + (maxScroll + 1) + "  ·  molette");
            text(g, sc, x + pw - font.width(sc), gy, a(MUTED, a), 1f);
        }
    }

    private void hsvBars(GuiGraphicsExtractor g, int x, int y, int pw, float a, boolean live) {
        String[] lbl = {"TEINTE", "SATURATION", "LUMIÈRE"};
        for (int b = 0; b < 3; b++) {
            int by = y + b * 24;
            text(g, RebornFont.body(lbl[b]), x, by - 1, a(MUTED, a), 1f);
            int bx = x + 64, bw = pw - 64;
            for (int i = 0; i < bw; i++) {
                float t = i / (float) (bw - 1);
                int c = b == 0 ? SkinSpec.hsvToArgb(t, 1f, 1f) : b == 1 ? SkinSpec.hsvToArgb(pkH, t, pkV)
                        : SkinSpec.hsvToArgb(pkH, pkS, t);
                g.fill(bx + i, by, bx + i + 1, by + 8, a(c, a));
            }
            outline(g, bx - 1, by - 1, bw + 2, 10, a(0x80D9A95E, a));
            float v = b == 0 ? pkH : b == 1 ? pkS : pkV;
            knob(g, bx + Math.round(v * (bw - 1)), by - 3, 14, a);
            if (live) bars.add(new int[]{bx, by - 4, bw, 16, b + 1});
        }
    }

    private void knob(GuiGraphicsExtractor g, int x, int y, int h, float a) {
        g.fill(x - 2, y, x + 3, y + h, a(0xE0000000, a));
        g.fill(x - 1, y + 1, x + 2, y + h - 1, a(CREAM, a));
    }

    private void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void syncHsv() {
        int c = lookRow == 3 ? look.hairColor : look.eyeColor;
        float[] hsv = SkinSpec.argbToHsv(c);
        pkH = hsv[0]; pkS = hsv[1]; pkV = hsv[2];
    }

    private void setLookColor(int argb) {
        if (lookRow == 3) look.hairColor = argb;
        else look.eyeColor = look.eyeColorRight = argb;
        if (actor != null) SceneActors.reskin(actor, look);
    }

    /** Barre glissée : peau ou HSV. */
    private void dragTo(int id, double mx) {
        for (int[] b : bars) {
            if (b[4] != id) continue;
            float t = (float) Math.max(0, Math.min(1, (mx - b[0]) / Math.max(1.0, b[2] - 1)));
            switch (id) {
                case 0 -> { skinT = t; look.skinColor = SkinSpec.skinRamp(t); if (actor != null) SceneActors.reskin(actor, look); }
                case 1 -> { pkH = t; setLookColor(SkinSpec.hsvToArgb(pkH, pkS, pkV)); }
                case 2 -> { pkS = t; setLookColor(SkinSpec.hsvToArgb(pkH, pkS, pkV)); }
                default -> { pkV = t; setLookColor(SkinSpec.hsvToArgb(pkH, pkS, pkV)); }
            }
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragBar >= 0) { dragTo(dragBar, e.x()); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragBar >= 0) { dragBar = -1; SceneSounds.tick(); return true; }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (step == S_LOOK && (lookRow == 2 || lookRow == 5) && v != 0) {
            lookScroll += v > 0 ? -1 : 1;
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    private static String assetName(List<CharacterCatalog.Asset> list, String id) {
        if (id == null || id.isEmpty()) return "Aucun";
        for (CharacterCatalog.Asset x : list) if (x.id.equals(id)) return x.name;
        return id;
    }

    private void selected(GuiGraphicsExtractor g, int x, int y, int w, float a) {
        g.fill(x - 8, y - 6, x + w, y + 16, a(0x40D9A95E, a));
        g.fill(x - 8, y - 6, x - 6, y + 16, a(GOLD, a));
    }

    private void ruler(GuiGraphicsExtractor g, int x, int y, int w, float v, float a) {
        g.fill(x, y, x + w, y + 2, a(0x40F5E9D0, a));
        g.fill(x, y, x + Math.round(w * v), y + 2, a(GOLD, a));
        for (int i = 0; i <= 10; i++) g.fill(x + w * i / 10, y - 2, x + w * i / 10 + 1, y + 4, a(0x60D9A95E, a));
        int cx = x + Math.round(w * v);
        g.fill(cx - 2, y - 5, cx + 3, y + 7, a(CREAM, a));
    }

    private void para(GuiGraphicsExtractor g, String s, int x, int y, float a) {
        var lines = font.split(RebornFont.body(s), Math.min(300, width - x - 30));
        for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), x, y + i * 11, a(SUB, a), true);
    }

    private void title(GuiGraphicsExtractor g, String s, int x, int y, float a) {
        text(g, RebornFont.display(s), x, y, a(CREAM, a), 1.7f);
        g.fill(x, y + 22, x + 44, y + 24, a(LACQUER, a));
    }

    private void text(GuiGraphicsExtractor g, Component c, float x, float y, int col, float sc) {
        g.pose().pushMatrix();
        g.pose().translate(Math.round(x), Math.round(y));
        g.pose().scale(sc, sc);
        g.text(font, c, 0, 0, col, true);
        g.pose().popMatrix();
    }

    private void logo(GuiGraphicsExtractor g, String v, int cx, int cy, int size, int tint) {
        g.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("reborn", "textures/gui/esc/village_" + v + ".png"),
                cx - size / 2, cy - size / 2, 0f, 0f, size, size, 48, 48, 48, 48, tint);
    }

    private static int a(int argb, float f) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return al << 24 | (argb & 0xFFFFFF);
    }

    /* ------------------------------------------------------- banc d'essai */

    public void debugName(String n) { name = n; }
}
