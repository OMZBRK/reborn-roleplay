package fr.reborn.hud.menu.character.perso;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.character.CharacterCard;
import fr.reborn.hud.menu.character.CharacterData;
import fr.reborn.hud.menu.character.CharacterPayload;
import fr.reborn.hud.menu.character.CharacterRules;
import fr.reborn.hud.menu.character.scene.SceneSounds;
import fr.reborn.hud.mixin.HudAccessor;
import fr.reborn.hud.skin.CharacterCatalog;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import fr.reborn.hud.ui.EcranOpaque;
import fr.reborn.hud.ui.LotGui;
import fr.reborn.hud.ui.MenuScale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static fr.reborn.hud.menu.character.perso.PersoArt.*;

/**
 * Création de personnage « au bord de l'étang » (maquettes Maquettes/personnage/02 … 09) : le perso se tient sur sa
 * pierre de gué devant la silhouette de son village, un fil de huit sceaux suit l'avancée, le registre se remplit à
 * droite. Huit étapes, mêmes règles et même message serveur que l'ancienne création en scène :
 * {@code create\n<nom>\n<clan>\n<village>\n<sexe>\n<âge>\n<taille>\n<apparence…>}.
 *
 * <p>Ouverture : la scène, les trois murmures et leur voix off, « appuie sur une touche ». Chaque étape joue sa voix
 * ; la question s'allume au rythme de la parole.
 */
public final class PersoCreationScreen extends Screen implements MenuScale.Plancher, EcranOpaque {

    static final int S_VILLAGE = 0, S_CLAN = 1, S_SEXE = 2, S_NOM = 3, S_AGE = 4, S_TAILLE = 5, S_LOOK = 6,
        S_RECAP = 7, STEPS = 8;
    private static final String[] QUESTIONS = {"De quel village viens-tu ?", "Quel sang coule en toi ?", "Qui es-tu ?",
        "Quel est ton nom ?", "Quel âge as-tu ?", "Quelle est ta taille ?", "À quoi ressembles-tu ?",
        "Est-ce bien toi ?"};
    private static final String[] PAYS = {"Pays du Feu", "Pays du Vent", "Pays de l'Eau", "Pays de la Foudre",
        "Pays de la Terre", "Pays de la Pluie"};
    private static final int A_SUIVANT = -1, A_RETOUR = -2, A_VOIX = -4, A_HASARD = -5, A_MOINS = -6, A_PLUS = -7,
        A_LIBRE_CHEVEUX = -8, A_LIBRE_YEUX = -9;

    /* ------------------------------------------------------------- état */
    private int step;
    private int village = -1, clanIdx = -1, sexeIdx;
    private String name = "", customClan = "";
    private int age = 16;
    private double size = 172 / 180.0;
    private final SkinSpec look = new SkinSpec();
    private int skinIdx = 4, hairIdx, hairColorIdx = 1, eyeIdx, outfitIdx;
    private boolean maleSlim;
    private int hairScroll, outfitScroll, accScroll;
    /** Rangées de tenues affichées (dépend de la hauteur et de la rangée Accessoire), zone de la rangée Accessoire. */
    private int rangeesTenues = 3;
    private int[] zoneAcc = new int[4];
    private int vue;
    private float lacet = 26f, lacetCible = 26f;
    private long openedAt, introAt, stepAt, leaveAt = -1, questionsAt, errorAt;
    private int intro;
    private String error = "";
    private boolean prevHidden;
    private int drag;               // 1 réglette, 2 personnage
    private double dragX;

    private final PersoScene scene = new PersoScene();
    private final PersoSkins skins = new PersoSkins("c");
    /** Aperçus : texture du personnage (recomposée au plus toutes les 60 ms pendant un réglage continu) et
     *  variantes des vignettes (recomposées seulement quand l'apparence change, jamais à chaque image). */
    private Identifier figureTex;
    private String figureCle = "", lookCle = "";
    private long figureAt;
    private final java.util.Map<String, Identifier> variantes = new java.util.HashMap<>();
    /** Couleur libre (pipette) : sélecteur ouvert, couleurs libres choisies (-1 = aucune). */
    private PersoCouleur picker;
    private int hairLibreC = -1, eyeLibreC = -1;
    private final List<int[]> hits = new ArrayList<>();
    private int[] regle;            // {x, w, vmin, vmax} de la réglette visible
    private int[] zoneFigure = new int[4];
    private int oyHit;

    public PersoCreationScreen() {
        super(Component.literal("Nouveau shinobi"));
        String cv = CharacterData.candidatureVillage();
        String[] vs = CharacterRules.villages();
        for (int i = 0; cv != null && i < villageCount(); i++) if (vs[i].equalsIgnoreCase(cv)) village = i;
        if (village < 0) for (int i = 0; i < villageCount(); i++) if (!CharacterRules.villageLocked(vs[i])) { village = i; break; }
        if (village < 0) village = 0;
        presetClan();
        String cn = CharacterData.candidatureName();
        if (cn != null) name = cn;
        look.skinColor = SkinSpec.skinRamp(skinIdx / 11f);
        appliquerPalette();
        applyLists();
        scene.forcerVillage(village);
    }

    @Override public int plancherW() { return 840; }

    @Override public int plancherH() { return 470; }

    /* ------------------------------------------------------------- règles */

    private static int villageCount() {
        String[] vs = CharacterRules.villages();
        int n = vs.length;
        while (n > 0 && vs[n - 1].toLowerCase(Locale.ROOT).startsWith("d")) n--;
        return Math.min(6, n);
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

    private String clanBrut() {
        String[] cs = CharacterRules.clansOf(village);
        return clanIdx < 0 || clanIdx >= cs.length ? "" : cs[clanIdx];
    }

    private boolean isCustomClan() { return "Autre".equals(clanBrut()); }

    private String sexe() { return sexeIdx == 1 ? "Femme" : "Homme"; }

    static double croissance(int age) { return age >= 17 ? 1.0 : 0.78 + (Math.max(10, age) - 10) * (0.22 / 7.0); }

    private int cmAdulte() { return (int) Math.round(180 * size); }

    private int cmActuel() { return (int) Math.round(180 * size * croissance(age)); }

    private boolean nomValide() {
        String n = name.trim();
        if (n.length() < 3 || n.length() > 20) return false;
        for (char c : n.toCharArray()) if (!(Character.isLetter(c) || c == ' ' || c == '\'' || c == '’' || c == '-')) return false;
        return true;
    }

    private boolean nomPris() {
        for (CharacterCard c : CharacterData.characters()) if (c.firstName() != null && c.firstName().trim().equalsIgnoreCase(name.trim())) return true;
        return false;
    }

    private boolean validate(int s) {
        switch (s) {
            case S_VILLAGE -> { if (village < 0) return fail("Choisis un village."); }
            case S_CLAN -> {
                String[] cs = CharacterRules.clansOf(village);
                if (clanIdx < 0 || clanIdx >= cs.length) return fail("Choisis un clan.");
                if (isCustomClan()) {
                    if (customClan.isBlank()) return fail("Écris le nom de ton clan.");
                    if (CharacterRules.isPredefinedClan(customClan)) {
                        return fail("« " + customClan.trim() + " » est un clan existant : choisis-le dans la grille.");
                    }
                }
            }
            case S_NOM -> {
                if (name.isBlank()) return fail("Écris ton nom.");
                if (!nomValide()) return fail("Lettres, espaces et apostrophes, de 3 à 20 caractères.");
                if (nomPris()) return fail("Tu as déjà un personnage de ce nom.");
            }
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

    /* ------------------------------------------------------------- palettes de clan */

    record Palette(String nom, int[] cheveux, int[] yeux, String nomYeux) {}

    private static final Palette DEFAUT = new Palette("Palette libre",
        new int[]{0x1A1414, 0x3A2416, 0x6B4423, 0xB8862E, 0xE8D27A, 0xD9D9D9, 0xC23B3B, 0x3A4FA0},
        new int[]{0x5A3A1E, 0x1E1A16, 0x3A6EA5, 0x4E8A4A, 0x9A9A9A}, "Yeux");

    static Palette palette(String clan) {
        String c = clan == null ? "" : CharacterRules.slug(clan);
        return switch (c) {
            case "hyuga" -> new Palette("Palette Hyūga", new int[]{0x141012, 0x3A2E36, 0x4E3A30, 0x262A44, 0x4A4660, 0x6A5A54},
                new int[]{0xE4DDF0, 0xD2CCE8, 0xB8AEE0}, "Byakugan");
            case "uchiha" -> new Palette("Palette Uchiha", new int[]{0x0E0C10, 0x1A1414, 0x24202A, 0x2A2230, 0x3A2416, 0x1E1A26},
                new int[]{0x1E1A16, 0x2A2220, 0x3A2A24, 0x141012}, "Yeux sombres");
            case "uzumaki" -> new Palette("Palette Uzumaki", new int[]{0xA8302C, 0xC23B3B, 0xD9532E, 0x8E2420, 0xB84A30, 0x6A1E1A},
                new int[]{0x3A6EA5, 0x4E8A4A, 0x5A3A1E, 0x6A7A8A}, "Yeux");
            case "senju" -> new Palette("Palette Senju", new int[]{0x1A1414, 0x3A2416, 0x6B4423, 0x4A3A2A, 0x2A2018, 0x5A4630},
                new int[]{0x5A3A1E, 0x3A2A20, 0x4E8A4A, 0x6A5A3A}, "Yeux");
            case "nara" -> new Palette("Palette Nara", new int[]{0x1A1414, 0x24201C, 0x2E2A26, 0x3A2E24},
                new int[]{0x3A2A20, 0x1E1A16, 0x4A3A2A}, "Yeux");
            case "akimichi" -> new Palette("Palette Akimichi", new int[]{0x6B4423, 0x8E5A2E, 0xC2783A, 0x3A2416, 0xA04A2A},
                new int[]{0x3A2A20, 0x5A3A1E, 0x6A4A2A}, "Yeux");
            case "yamanaka" -> new Palette("Palette Yamanaka", new int[]{0xE8D27A, 0xF2E2A0, 0xD8C060, 0xC8B890, 0xB8862E, 0xE8E0C8},
                new int[]{0x3A6EA5, 0x6A9AC8, 0x4E7A9A, 0x8AB0D0}, "Yeux");
            case "aburame" -> new Palette("Palette Aburame", new int[]{0x1A1414, 0x24201C, 0x3A2E28, 0x2E3A2A},
                new int[]{0x141012, 0x2A2620, 0x3A3A30}, "Yeux");
            case "hatake" -> new Palette("Palette Hatake", new int[]{0xD9D9D9, 0xC8CCD0, 0xB8BCC4, 0xE8E8EC, 0x9A9EA6},
                new int[]{0x1E1A16, 0x3A3634, 0x5A5A5A}, "Yeux");
            case "sarutobi" -> new Palette("Palette Sarutobi", new int[]{0x1A1414, 0x3A2416, 0x4A3A2A, 0x2A2018},
                new int[]{0x3A2A20, 0x1E1A16, 0x5A3A1E}, "Yeux");
            case "kurama" -> new Palette("Palette Kurama", new int[]{0x3A1A2A, 0x5A2A3A, 0x2A1A22, 0x6A3A4A},
                new int[]{0x8B1E2B, 0x5A2A3A, 0x3A1A22}, "Yeux");
            case "sabaku" -> new Palette("Palette Sabaku", new int[]{0xA8302C, 0xC2603A, 0x8E3A24, 0x6B4423, 0xB87A3A},
                new int[]{0x4E8A6A, 0x3A6E5A, 0x5A7A4A, 0x7AA08A}, "Yeux");
            case "hozuki" -> new Palette("Palette Hōzuki", new int[]{0xD8E0EC, 0xB8C8E0, 0xE8ECF4, 0xA8B8D0},
                new int[]{0x6A4A9A, 0x8A6AB0, 0x5A5A8A}, "Yeux");
            case "kaguya" -> new Palette("Palette Kaguya", new int[]{0xE8E8EC, 0xD9D9D9, 0xC8C8CC},
                new int[]{0x3A6A4A, 0x4E8A6A, 0x6A2A3A}, "Yeux");
            case "yuki" -> new Palette("Palette Yuki", new int[]{0x1A1414, 0x2A2A34, 0x3A3A48},
                new int[]{0x3A2A20, 0x4A4A5A, 0x6A7A8A}, "Yeux");
            case "terumi" -> new Palette("Palette Terumi", new int[]{0xB8602E, 0xC8783A, 0xA04A2A, 0xD88A4A},
                new int[]{0x4E8A4A, 0x3A6A3A, 0x6A8A4A}, "Yeux");
            case "yotsuki" -> new Palette("Palette Yotsuki", new int[]{0xE8D27A, 0xF2E8C0, 0xD9D9D9, 0xC8A050},
                new int[]{0x3A2A20, 0x1E1A16, 0x5A3A1E}, "Yeux");
            default -> DEFAUT;
        };
    }

    private Palette pal() { return palette(clanName()); }

    private void appliquerPalette() {
        Palette p = pal();
        if (hairColorIdx < 0 && hairLibreC < 0) hairColorIdx = 0;
        if (eyeIdx < 0 && eyeLibreC < 0) eyeIdx = 0;
        hairColorIdx = Math.min(hairColorIdx, p.cheveux().length - 1);
        eyeIdx = Math.min(eyeIdx, p.yeux().length - 1);
        look.hairColor = 0xFF000000 | (hairColorIdx < 0 ? hairLibreC : p.cheveux()[hairColorIdx]);
        look.eyeColor = look.eyeColorRight = 0xFF000000 | (eyeIdx < 0 ? eyeLibreC : p.yeux()[eyeIdx]);
    }

    private boolean glisseCouleur() { return picker != null; }

    /** Ouvre le sélecteur de couleur libre (0 cheveux, 1 yeux) sur la couleur actuelle. */
    private void ouvrirLibre(int cible) {
        SceneSounds.tick();
        if (cible == 0) {
            picker = new PersoCouleur("COULEUR LIBRE - CHEVEUX", look.hairColor, rgb -> {
                hairLibreC = rgb & 0xFFFFFF;
                hairColorIdx = -1;
                appliquerPalette();
            });
        } else {
            picker = new PersoCouleur("COULEUR LIBRE - YEUX", look.eyeColor, rgb -> {
                eyeLibreC = rgb & 0xFFFFFF;
                eyeIdx = -1;
                appliquerPalette();
            });
        }
    }

    private void fermerLibre(boolean garder) {
        if (picker == null) return;
        if (!garder) picker.annuler();
        picker = null;
        SceneSounds.tick();
    }

    static String affiche(String clan) {
        if (clan == null) return "";
        return switch (clan) {
            case "Hyuga" -> "Hyūga";
            case "Hozuki" -> "Hōzuki";
            case "Onoki" -> "Ōnoki";
            default -> clan;
        };
    }

    /* ------------------------------------------------------------- listes d'apparence */

    private void applyLists() {
        List<CharacterCatalog.Asset> h = hairs(), o = outfits();
        look.hairId = h.isEmpty() ? "" : h.get(Math.min(hairIdx, h.size() - 1)).id;
        look.outfitId = o.isEmpty() ? "" : o.get(Math.min(outfitIdx, o.size() - 1)).id;
        // accessoire : retiré s'il n'est plus proposé (changement de sexe ou de clan)
        if (look.accessoryId != null && !look.accessoryId.isBlank()
            && accessoires().stream().noneMatch(a -> a.id.equals(look.accessoryId))) look.accessoryId = "";
    }

    /** Accessoires du catalogue proposés à ce perso (lus à chaque fois : un ajout serveur apparaît tout de suite). */
    private List<CharacterCatalog.Asset> accessoires() { return available("accessory", look.female); }

    /** Accessoire porté (null = aucun). */
    private CharacterCatalog.Asset accessoire() {
        if (look.accessoryId == null || look.accessoryId.isBlank()) return null;
        for (CharacterCatalog.Asset a : accessoires()) if (a.id.equals(look.accessoryId)) return a;
        return null;
    }

    private List<CharacterCatalog.Asset> hairs() { return available("hair", look.female); }

    private List<CharacterCatalog.Asset> outfits() { return available("outfit", look.female); }

    private List<CharacterCatalog.Asset> available(String cat, boolean female) {
        try { return CharacterCatalog.available(cat, female, clanName(), CharacterData.staffExempt()); }
        catch (RuntimeException e) { return new ArrayList<>(); }
    }

    /* ------------------------------------------------------------- cycle de vie */

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (openedAt == 0) {
            prevHidden = mc.gui.hud.isHidden();
            ((HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
            openedAt = introAt = System.currentTimeMillis();
            stepAt = questionsAt = Long.MAX_VALUE / 4;
            PersoSons.ambiance(village);
            PersoSons.voixIntro();
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        skins.liberer();
        PersoSons.couperVoix();
        if (!(mc.gui.screen() instanceof PersoSelectionScreen)) PersoSons.arreter();
        ((HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHidden);
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean shouldCloseOnEsc() { return false; }

    /* ------------------------------------------------------------- étapes */

    public void setStep(int s) {
        picker = null;
        step = Math.max(0, Math.min(STEPS - 1, s));
        stepAt = System.currentTimeMillis();
        error = "";
        SceneSounds.whoosh();
        PersoSons.voix(step);
        lacetCible = switch (step) {
            case S_AGE -> -24f;
            case S_TAILLE -> 20f;
            case S_LOOK -> 30f;
            case S_RECAP -> 28f;
            case S_NOM -> 18f;
            default -> 26f;
        };
        vue = 0;
    }

    public void beginScene() {
        if (intro >= 2) return;
        intro = 2;
        questionsAt = System.currentTimeMillis();
        RebornSounds.confirm();
        setStep(0);
    }

    private void next() {
        if (!validate(step)) return;
        if (step < S_RECAP) setStep(step + 1);
        else submit();
    }

    private void back() {
        if (step > 0) setStep(step - 1);
        else Minecraft.getInstance().setScreenAndShow(PersoSelectionScreen.fromRoster(Minecraft.getInstance()));
    }

    public void setVillage(int v) {
        String[] vs = CharacterRules.villages();
        if (v < 0 || v >= villageCount()) return;
        if (CharacterRules.villageLocked(vs[v])) { fail("Ta candidature t'attache à un autre village."); return; }
        if (v != village) {
            village = v;
            presetClan();
            customClan = "";
            appliquerPalette();
            applyLists();
            scene.setVillage(v);
            PersoSons.ambiance(v);
            SceneSounds.tick();
        }
    }

    public void setClan(int c) {
        String[] cs = CharacterRules.clansOf(village);
        if (c < 0 || c >= cs.length) return;
        if (CharacterRules.clanLocked(cs[c])) { fail("Ta candidature t'attache à un autre clan."); return; }
        if (c != clanIdx) SceneSounds.tick();
        clanIdx = c;
        hairColorIdx = 0;
        eyeIdx = 0;
        appliquerPalette();
        applyLists();
    }

    public void setSexe(int s) {
        if (s != sexeIdx) SceneSounds.tick();
        sexeIdx = s;
        look.female = s == 1;
        look.slim = look.female || maleSlim;
        hairIdx = outfitIdx = 0;
        hairScroll = outfitScroll = 0;
        applyLists();
    }

    public void setAge(int a) {
        int n = Math.max(10, Math.min(60, a));
        if (n != age) SceneSounds.key();
        age = n;
    }

    public void setCm(int cm) {
        int n = Math.max(153, Math.min(207, cm));
        if (n != cmAdulte()) SceneSounds.key();
        size = n / 180.0;
    }

    private void hasard() {
        Random r = new Random();
        skinIdx = r.nextInt(12);
        look.skinColor = SkinSpec.skinRamp(skinIdx / 11f);
        int nh = hairs().size(), no = outfits().size();
        hairIdx = nh > 0 ? r.nextInt(nh) : 0;
        outfitIdx = no > 0 ? r.nextInt(no) : 0;
        Palette p = pal();
        hairColorIdx = r.nextInt(p.cheveux().length);
        eyeIdx = r.nextInt(p.yeux().length);
        // accessoire : une chance sur deux de n'en porter aucun
        List<CharacterCatalog.Asset> al = accessoires();
        look.accessoryId = al.isEmpty() || r.nextInt(2) == 0 ? "" : al.get(r.nextInt(al.size())).id;
        picker = null;
        if (!look.female) { maleSlim = r.nextBoolean(); look.slim = maleSlim; }
        appliquerPalette();
        applyLists();
        SceneSounds.tick();
    }

    /** Envoie la création au serveur (même format que la création en scène). */
    private void submit() {
        for (int s = 0; s < S_RECAP; s++) if (!validate(s)) { setStep(s); return; }
        String cmd = "create\n" + name.trim() + "\n" + clanName() + "\n" + CharacterRules.villages()[village]
            + "\n" + sexe() + "\n" + age + "\n" + String.format(Locale.US, "%.4f", size) + "\n" + look.serialize();
        if (!ClientPlayNetworking.canSend(CharacterPayload.ID)) { fail("Création impossible hors du serveur."); return; }
        ClientPlayNetworking.send(new CharacterPayload(cmd));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) RebornSkins.applySpec(mc.player.getUUID(), look);
        leaveAt = System.currentTimeMillis();
        RebornSounds.confirm();
        SceneSounds.resonate();
    }

    @Override
    public void tick() {
        PersoSons.tick();
        if (leaveAt >= 0 && System.currentTimeMillis() - leaveAt > 2600) onClose();
    }

    /* ------------------------------------------------------------- saisie */

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (leaveAt >= 0) return true;
        if (intro < 2) { if (System.currentTimeMillis() - introAt > 700) beginScene(); return true; }
        if (picker != null) {
            if (k == GLFW.GLFW_KEY_ESCAPE) fermerLibre(false);
            else if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) fermerLibre(true);
            else if (k == GLFW.GLFW_KEY_BACKSPACE) picker.effacer();
            return true;
        }
        boolean typing = step == S_NOM || (step == S_CLAN && isCustomClan());
        if (k == GLFW.GLFW_KEY_ESCAPE) { back(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { next(); return true; }
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
            case S_CLAN -> {
                if (up) setClan(stepFree(clanIdx, -3, false));
                if (down) setClan(stepFree(clanIdx, 3, false));
                if (left && !typing) setClan(stepFree(clanIdx, -1, false));
                if (right && !typing) setClan(stepFree(clanIdx, 1, false));
            }
            case S_SEXE -> { if (up || left) setSexe(0); if (down || right) setSexe(1); }
            case S_AGE -> { if (left || down) setAge(age - 1); if (right || up) setAge(age + 1); }
            case S_TAILLE -> { if (left || down) setCm(cmAdulte() - 1); if (right || up) setCm(cmAdulte() + 1); }
            case S_LOOK, S_RECAP -> { if (left) lacetCible -= 30; if (right) lacetCible += 30; }
            default -> { return super.keyPressed(e); }
        }
        return true;
    }

    private int stepFree(int from, int dir, boolean villages) {
        String[] arr = villages ? CharacterRules.villages() : CharacterRules.clansOf(village);
        int n = villages ? villageCount() : arr.length;
        for (int i = from + dir; i >= 0 && i < n; i += dir) {
            boolean locked = villages ? CharacterRules.villageLocked(arr[i]) : CharacterRules.clanLocked(arr[i]);
            if (!locked) return i;
        }
        return from;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (intro < 2 || leaveAt >= 0 || !e.isAllowedChatCharacter()) return false;
        String c = e.codepointAsString();
        if (picker != null) { picker.car(c.charAt(0)); return true; }
        if (step == S_NOM) {
            char ch = c.charAt(0);
            boolean ok = Character.isLetter(ch) || ch == '\'' || ch == '’' || ch == '-' || (ch == ' ' && !name.isEmpty() && !name.endsWith(" "));
            if (ok && name.length() < 20) { name += c; SceneSounds.key(); }
            else RebornSounds.deny();
            return true;
        }
        if (step == S_CLAN && isCustomClan() && customClan.length() < 20) { customClan += c; SceneSounds.key(); return true; }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0) return super.mouseClicked(e, dbl);
        if (intro < 2) { if (System.currentTimeMillis() - introAt > 700) beginScene(); return true; }
        if (leaveAt >= 0) return true;
        double mx = e.x(), my = e.y();
        if (picker != null) {
            boolean[] ok = {false};
            if (!picker.clic(mx, my, ok) || ok[0]) fermerLibre(true);
            return true;
        }
        if (regle != null && mx >= regle[0] - 4 && mx < regle[0] + regle[1] + 4 && my >= regle[4] - 7 && my < regle[4] + 14) {
            drag = 1;
            glisserRegle(mx);
            return true;
        }
        for (int[] r : hits) {
            if (!dans(mx, my, r[0], r[1], r[2], r[3])) continue;
            action(r[4]);
            return true;
        }
        if (dans(mx, my, zoneFigure[0], zoneFigure[1], zoneFigure[2], zoneFigure[3])) {
            drag = 2;
            dragX = mx;
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    private void action(int a) {
        switch (a) {
            case A_SUIVANT -> next();
            case A_RETOUR -> back();
            case A_VOIX -> {
                PersoSons.voixCoupee = !PersoSons.voixCoupee;
                if (PersoSons.voixCoupee) PersoSons.couperVoix(); else PersoSons.voix(step);
                SceneSounds.tick();
            }
            case A_HASARD -> hasard();
            case A_LIBRE_CHEVEUX -> ouvrirLibre(0);
            case A_LIBRE_YEUX -> ouvrirLibre(1);
            case A_MOINS -> { if (step == S_AGE) setAge(age - 1); else setCm(cmAdulte() - 1); }
            case A_PLUS -> { if (step == S_AGE) setAge(age + 1); else setCm(cmAdulte() + 1); }
            default -> {
                if (a >= 1100) { choisirAccessoire(a - 1100); SceneSounds.tick(); }
                else if (a >= 1000) { vue = a - 1000; lacetCible = vue == 0 ? 0 : vue == 1 ? 90 : 180; SceneSounds.tick(); }
                else if (a >= 900) { outfitIdx = a - 900; applyLists(); SceneSounds.tick(); }
                else if (a >= 800) { eyeIdx = a - 800; appliquerPalette(); SceneSounds.tick(); }
                else if (a >= 700) { hairColorIdx = a - 700; appliquerPalette(); SceneSounds.tick(); }
                else if (a >= 600) { hairIdx = a - 600; applyLists(); SceneSounds.tick(); }
                else if (a >= 500) { skinIdx = a - 500; look.skinColor = SkinSpec.skinRamp(skinIdx / 11f); SceneSounds.tick(); }
                else if (a >= 400) {
                    if (!look.female) { maleSlim = a == 401; look.slim = maleSlim; SceneSounds.tick(); }
                }
                else if (a >= 300) {
                    int[][] tr = {{10, 12}, {13, 16}, {17, 60}};
                    int[] t = tr[a - 300];
                    setAge(age < t[0] || age > t[1] ? (a == 302 ? 17 : t[0]) : age);
                }
                else if (a >= 200) setSexe(a - 200);
                else if (a >= 100) setClan(a - 100);
                else setVillage(a);
            }
        }
    }

    /** 0 = aucun, i ≥ 1 = i-ème accessoire de la liste. */
    private void choisirAccessoire(int i) {
        List<CharacterCatalog.Asset> al = accessoires();
        look.accessoryId = i <= 0 || i > al.size() ? "" : al.get(i - 1).id;
    }

    private void glisserRegle(double mx) {
        if (regle == null) return;
        float t = (float) Math.max(0, Math.min(1, (mx - regle[0] - 2) / Math.max(1.0, regle[1] - 4)));
        int v = Math.round(regle[2] + t * (regle[3] - regle[2]));
        if (step == S_AGE) setAge(v); else setCm(v);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (picker != null && picker.glisser(e.x(), e.y())) return true;
        if (drag == 1) { glisserRegle(e.x()); return true; }
        if (drag == 2) { lacetCible += (float) (e.x() - dragX) * 0.9f; dragX = e.x(); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (picker != null && picker.relacher()) return true;
        if (drag != 0) { drag = 0; return true; }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (step == S_LOOK && v != 0) {
            if (mx < width / 2.0) {
                int max = Math.max(0, (hairs().size() + 3) / 4 - lignesCheveux());
                hairScroll = Math.max(0, Math.min(max, hairScroll + (v > 0 ? -1 : 1)));
            } else if (dans(mx, my, zoneAcc[0], zoneAcc[1], zoneAcc[2], zoneAcc[3])) {
                int max = Math.max(0, (accessoires().size() + 1 + 3) / 4 - 1);
                accScroll = Math.max(0, Math.min(max, accScroll + (v > 0 ? -1 : 1)));
            } else {
                int max = Math.max(0, (outfits().size() + 2) / 3 - rangeesTenues);
                outfitScroll = Math.max(0, Math.min(max, outfitScroll + (v > 0 ? -1 : 1)));
            }
            return true;
        }
        if ((step == S_AGE || step == S_TAILLE) && v != 0) {
            if (step == S_AGE) setAge(age + (v > 0 ? 1 : -1)); else setCm(cmAdulte() + (v > 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    /* ------------------------------------------------------------- mise en page */

    private int T, B, d, cx, bas;

    private void calculer() {
        d = height - 540;
        T = Math.max(0, d / 2);
        B = d > 0 ? d / 2 : d;
        cx = width / 2;
        bas = 478 + B - 14;            // bas maximal d'une planche (au-dessus des boutons)
    }

    private int lignesCheveux() { return bas - (108 + T) >= 352 ? 2 : 1; }

    private int lignesTenues() { return bas - (108 + T) >= 340 ? 3 : 2; }

    /** Hauteur de la rangée Accessoire dans la planche TENUE (filet, étiquette, vignettes têtes). */
    private static final int H_ACC = 98;

    private void hit(int x, int y, int w, int h, int a) { hits.add(new int[]{x, y + oyHit, w, h, a}); }

    /* ------------------------------------------------------------- aperçus */

    private void majApercus(long now) {
        String cle = look.serialize();
        if (figureTex == null || (!cle.equals(figureCle) && (now - figureAt >= 60 || !glisseCouleur()))) {
            figureCle = cle;
            figureTex = skins.get(cle, look, false);
            figureAt = now;
        }
        if (!glisseCouleur() && !cle.equals(lookCle)) {
            lookCle = cle;
            variantes.clear();
        }
    }

    /** Texture d'une variante de l'apparence courante (vignettes), calculée une fois par apparence. */
    private Identifier variante(String cle, java.util.function.Consumer<SkinSpec> mod) {
        Identifier id = variantes.get(cle);
        if (id != null) return id;
        SkinSpec sp = SkinSpec.deserialize(lookCle);
        mod.accept(sp);
        id = skins.get(sp, false);
        variantes.put(cle, id);
        return id;
    }

    /* ------------------------------------------------------------- rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        PersoPerf.debut();
        dessiner(g, mx, my, dt);
        PersoPerf.fin(g);
    }

    private void dessiner(GuiGraphicsExtractor g, int mx, int my, float dt) {
        calculer();
        long now = System.currentTimeMillis();
        majApercus(now);
        hits.clear();
        regle = null;
        Font f = font;
        float ui = intro < 2 ? 0f : Math.min(1f, Math.max(0f, (now - questionsAt - 300) / 500f));
        if (leaveAt >= 0) ui *= Math.max(0f, 1f - (now - leaveAt) / 500f);
        int K = scene.couleur();

        // ---------------- scène
        scene.fond(g, width, height);
        int sx = step == S_TAILLE ? cx - 10 : step == S_LOOK ? cx + 6 : cx;
        boolean gros = step == S_NOM && intro >= 2;
        int pieds = (gros ? 640 : 452) + B;
        boolean dais = (step == S_CLAN || step == S_SEXE) && intro >= 2;
        // décor fixe de la création : pierres de gué et lotus
        PersoScene.pierre(g, P_14, cx + 160, 470 + B);
        PersoScene.pierre(g, P_16, cx - 150, 476 + B);
        PersoScene.pierre(g, P_10, cx + 110, 380 + B);
        PersoScene.pierre(g, P_9, cx - 100, 384 + B);
        if (step == S_NOM && intro >= 2) {
            PersoScene.lotus(g, 1, cx - 230, 470 + B);
            PersoScene.lotus(g, 5, cx + 240, 496 + B);
        } else {
            PersoScene.lotus(g, 1, cx + 220, 486 + B);
            PersoScene.lotus(g, 5, cx - 212, 500 + B);
            PersoScene.lotus(g, 7, cx + 110, 418 + B);
        }
        scene.roseaux(g, width, height);
        scene.lueur(g, sx, pieds, K, 0.21f);
        if (!gros) {
            if (dais) {
                scene.pierreLit(g, LIT_DAIS, scene.village(), sx, pieds + 8);
                String slug = CharacterRules.slug(clanBrut());
                PersoScene.kamonSol(g, slug, CharacterRules.clanColor(clanBrut()) & 0xFFFFFF, sx, pieds, 1f);
            } else {
                scene.pierreLit(g, LIT_CREA, scene.village(), sx, pieds);
                PersoScene.ensoHalo(g, sx, pieds, 48, 9.2f, K);
            }
        }
        List<int[]> zones = zonesLucioles();
        scene.lucioles(width, height, zones, 21 + step * 0);
        PersoScene.braises(g, sx, pieds, K, 28, 44, 9, 110, false, 3, 1f);
        scene.luciolesFond(g, height, 1f);
        if (!gros) PersoScene.ombre(g, sx, pieds, 1f);

        // ---------------- personnage
        lacet += (lacetCible - lacet) * Math.min(1f, dt * 0.25f + 0.08f);
        float stature = step >= S_AGE ? (float) (size * croissance(age) / (172 / 180.0)) : 1f;
        stature = Math.min(1.12f, Math.max(0.7f, stature));
        float k = gros ? 13f : (step == S_RECAP ? 8f : 7.5f);
        Identifier tex = figureTex;
        PersoFigure.dessiner(g, tex, look.slim, sx, pieds, k * (gros ? 1f : stature), lacet, 6f,
            Math.round(13 * k), Math.round(19 * k));
        int hautFig = Math.round(pieds - 32 * k * (gros ? 1f : stature));
        zoneFigure = new int[]{sx - Math.round(8 * k), hautFig, Math.round(16 * k), pieds - hautFig};
        PersoScene.braises(g, sx, pieds, K, 28, 44, 9, 110, true, 3, 1f);
        if (gros) PersoScene.brumeAvant(g, cx, 360 + B);
        scene.luciolesDevant(g, 1f);

        if (intro < 2 || now - questionsAt < 700) intro(g, f, now);
        if (ui <= 0f) return;

        // ---------------- interface
        g.pose().pushMatrix();
        g.pose().translate(0, T);
        oyHit = T;
        PersoUi.progression(g, f, cx, step, ui);
        float dur = PersoSons.DUREE_VOIX[step];
        float lu = (!PersoSons.VOIX || PersoSons.voixCoupee) ? 1f : Math.min(1f, Math.max(0f, (now - stepAt - 200) / dur));
        String q = QUESTIONS[step];
        PersoUi.question(g, f, cx, 62, q, Math.round(lu * sansAccents(q).length()), PersoSons.voixEnCours(),
            PersoSons.VOIX, ui);
        if (PersoSons.VOIX) {
            int[] rv = PersoUi.chip(g, f, 24, 14, "", true, false, PersoSons.voixEnCours(), PersoSons.voixCoupee, ui);
            if (rv != null) hit(rv[0], rv[1], rv[2], rv[3], A_VOIX);
        }
        int y0 = 108;
        int xG = step == S_LOOK ? 20 : 24;
        int wG = step == S_LOOK ? 286 : 240;
        int xD = step == S_LOOK ? width - 20 - 276 : width - 264;
        boolean gauche = step != S_NOM;
        PersoUi.perches(g, gauche ? xG + wG + 10 : 0, xD - 10, width);
        int hMax = bas - T - y0;
        switch (step) {
            case S_VILLAGE -> etapeVillage(g, f, xG, y0, hMax, mx, my - T);
            case S_CLAN -> etapeClan(g, f, xG, y0, hMax, mx, my - T, now);
            case S_SEXE -> etapeSexe(g, f, xG, y0, hMax, mx, my - T);
            case S_NOM -> etapeNom(g, f, now, hautFig);
            case S_AGE -> etapeAge(g, f, xG, y0, mx, my - T);
            case S_TAILLE -> etapeTaille(g, f, xG, y0, mx, my - T, sx, pieds, k * stature);
            case S_LOOK -> {
                etapeLook(g, f, xG, xD, y0, hMax, mx, my - T);
                if (picker != null) {
                    picker.x = xG + 25;
                    picker.y = y0 + 150;
                    g.pose().pushMatrix();
                    g.pose().translate(0, -T);
                    picker.y += T;
                    picker.dessiner(g, f, mx, my, now);
                    g.pose().popMatrix();
                }
            }
            case S_RECAP -> etapeRecap(g, f, xG, y0, mx, my - T);
            default -> { }
        }
        if (step == S_RECAP) {
            PersoArt.panneau(g, P_REGISTRE_RECAP, xD, y0, 262);
            PersoUi.registre(g, f, xD, y0, valeurs(), -1, 1f);
            inter(g, f, inter("Le registre sera scellé."), xD + 16, y0 + 228, 7, DOUX, 0);
        } else if (step != S_LOOK) {
            PersoArt.panneau(g, P_REGISTRE, xD, y0, 214);
            PersoUi.registre(g, f, xD, y0, valeurs(), step, 1f);
        }
        g.pose().popMatrix();
        oyHit = 0;

        // ---------------- bas d'écran
        int by = 478 + B;
        boolean retour = step > S_VILLAGE;
        int bS = step == S_RECAP ? B_OUI : B_SUIVANT;
        int w1 = retour ? B_W[B_RETOUR] : 0, w2 = B_W[bS], gap = retour ? 10 : 0;
        int x = cx - (w1 + gap + w2) / 2;
        if (retour) {
            boolean h = dans(mx, my, x, by, w1, 20);
            bouton(g, f, B_RETOUR, h ? 1 : 0, x, by, null);
            hit(x, by, w1, 20, A_RETOUR);
            x += w1 + gap;
        }
        boolean hs = dans(mx, my, x, by, w2, 20);
        bouton(g, f, bS, hs ? 1 : 0, x, by, null);
        hit(x, by, w2, 20, A_SUIVANT);
        if (step == S_LOOK) {
            int wa = B_W[B_HASARD];
            boolean hh = dans(mx, my, 24, by, wa, 20);
            bouton(g, f, B_HASARD, hh ? 1 : 0, 24, by, null);
            hit(24, by, wa, 20, A_HASARD);
        }
        if (!error.isEmpty() && now - errorAt < 3000) {
            float ea = Math.min(1f, (3000 - (now - errorAt)) / 400f);
            inter(g, f, inter(error), cx, by - 12, 8, argb(0xE87070, ea * ui), 1);
        }
        String[][] aides = switch (step) {
            case S_VILLAGE -> new String[][]{{"CLIC", "choisir"}, {"ENTREE", "suivant"}};
            case S_CLAN -> new String[][]{{"CLIC", "choisir"}, {"ENTREE", "suivant"}, {"ECHAP", "retour"}};
            case S_SEXE -> new String[][]{{"< >", "changer"}, {"ENTREE", "suivant"}, {"ECHAP", "retour"}};
            case S_NOM -> new String[][]{{"CLAVIER", "écrire"}, {"ENTREE", "suivant"}};
            case S_AGE -> new String[][]{{"< >", "1 an"}, {"GLISSER", "régler"}, {"ENTREE", "suivant"}};
            case S_TAILLE -> new String[][]{{"< >", "1 cm"}, {"GLISSER", "régler"}, {"ENTREE", "suivant"}};
            case S_LOOK -> new String[][]{{"GLISSER", "tourner"}, {"CLIC", "choisir"}, {"ENTREE", "suivant"}};
            default -> new String[][]{{"ENTREE", "valider"}, {"ECHAP", "modifier"}};
        };
        aides(g, f, aides, width - 24, 509 + B);
        float noir = leaveAt >= 0 ? Math.min(1f, Math.max(0f, (now - leaveAt - 1100) / 1100f)) : 0f;
        if (noir > 0) g.fill(0, 0, width, height, argb(0, noir));
    }

    private List<int[]> zonesLucioles() {
        List<int[]> z = new ArrayList<>();
        int y0 = 108 + T;
        z.add(new int[]{0, 0, width, 80 + T});
        z.add(new int[]{10, y0 - 10, 320, 470 + B});
        z.add(new int[]{width - 310, y0 - 10, width - 10, 340 + T});
        z.add(new int[]{cx - 80, 190 + B, cx + 80, 470 + B});
        z.add(new int[]{cx - 150, 470 + B, width, height});
        return z;
    }

    private PersoUi.Ligne[] valeurs() {
        PersoUi.Ligne[] v = new PersoUi.Ligne[7];
        int lim = step == S_RECAP ? 7 : step;
        String clan = clanName();
        String slug = CharacterRules.slug(clanBrut());
        String[] vals = {village >= 0 ? CharacterRules.villages()[village] : null,
            clan.isEmpty() ? (isCustomClan() ? "Autre" : null) : affiche(clan), sexe(),
            name.isBlank() ? null : name.trim(), age + " ans", cmActuel() + " cm", ""};
        for (int i = 0; i <= Math.min(6, lim); i++) {
            if (i == lim && step == S_RECAP) break;
            if (i == 6) {
                CharacterCatalog.Asset acc = accessoire();
                v[i] = new PersoUi.Ligne(acc == null ? "" : nomCourt(acc.name), -1,
                    new int[]{look.skinColor, look.hairColor, look.eyeColor});
                continue;
            }
            if (vals[i] == null) continue;
            int em = i == 0 ? idxVillage(village) : i == 1 ? idxClan(slug) : -1;
            v[i] = new PersoUi.Ligne(vals[i], em, null);
        }
        return v;
    }

    /** Nom d'accessoire pour le registre : sans son complément (« Bandeau frontal de Konoha » → « Bandeau frontal »). */
    static String nomCourt(String nom) {
        if (nom == null) return "";
        for (String sep : new String[]{" de ", " du ", " des ", " d'", " d’"}) {
            int k = nom.indexOf(sep);
            if (k > 0) return nom.substring(0, k);
        }
        return nom;
    }

    /* ------------------------------------------------------------- étapes : panneaux */

    private void etapeVillage(GuiGraphicsExtractor g, Font f, int x, int y0, int hMax, int mx, int my) {
        int n = villageCount();
        int pas = Math.min(42, (hMax - 46) / n);
        int th = Math.min(38, pas - 4);
        int h = Math.min(300, 38 + n * pas + 8);
        PersoArt.panneau(g, P_VILLAGE, x, y0, h);
        String[] vs = CharacterRules.villages();
        for (int i = 0; i < n; i++) {
            int bx = x + 10, by = y0 + 38 + i * pas, bw = 220;
            boolean sel = i == village, lock = CharacterRules.villageLocked(vs[i]);
            boolean sur = !sel && !lock && dans(mx, my, bx, by, bw, th);
            tuile(g, T_VILLAGE, sel ? ACTIF : sur ? SURVOL : NORMAL, bx, by, th);
            int t = th >= 34 ? 24 : 16;
            int col = sel ? OR_CLAIR : sur ? CREME : lock ? 0xFF5E5248 : DOUX;
            embleme(g, idxVillage(i), bx + 8 + (24 - t) / 2f, by + th / 2f - t / 2f, t, col);
            float ym = by + th / 2f;
            Component nom = sel ? interGras(vs[i]) : inter(vs[i]);
            inter(g, f, nom, bx + 42, ym - 5, 9, sel || sur ? CREME : lock ? 0xFF6A5C52 : 0xFFD8CCB0, 0);
            inter(g, f, inter(PAYS[i]), bx + 42, ym + 7, 7, sel ? 0xFFE8C8A0 : lock ? 0xFF5E5248 : DOUX, 0);
            if (lock) cadenas(g, bx + bw - 14, by + 5);
            hit(bx, by, bw, th, i);
        }
    }

    private void etapeClan(GuiGraphicsExtractor g, Font f, int x, int y0, int hMax, int mx, int my, long now) {
        String[] cs = CharacterRules.clansOf(village);
        int rows = Math.max(1, (cs.length + 2) / 3);
        int ch = Math.min(64, (hMax - 34 - 30) / rows - 4);
        int h = 34 + rows * (ch + 4) + 26;
        PersoArt.panneau(g, P_CLAN, x, y0, h);
        int cw = 70;
        int gx0 = x + (240 - 3 * cw - 8) / 2;
        boolean unVerrou = false;
        for (int i = 0; i < cs.length; i++) {
            int cx0 = gx0 + (i % 3) * (cw + 4), cy0 = y0 + 34 + (i / 3) * (ch + 4);
            boolean sel = i == clanIdx, lock = CharacterRules.clanLocked(cs[i]);
            unVerrou |= lock;
            boolean sur = !sel && !lock && dans(mx, my, cx0, cy0, cw, ch);
            tuile(g, T_CLAN, sel ? ACTIF : sur ? SURVOL : lock ? VERROU : NORMAL, cx0, cy0, ch);
            int cc = CharacterRules.clanColor(cs[i]) & 0xFFFFFF;
            int t = ch >= 56 ? 32 : 24;
            float ey = cy0 + (ch >= 56 ? 25 : ch / 2f - 6);
            if (sel || sur) halo(g, cx0 + cw / 2f, ey, 20, 1f, mix(cc, 0xFFF0D2, 0.3f), 0.12f, 2);
            int col = sel ? OR_CLAIR : sur ? CREME : lock ? 0xFF5E5248 : DOUX;
            emblemeCentre(g, idxClan(CharacterRules.slug(cs[i])), cx0 + cw / 2f, ey, t, col);
            if (!lock) g.fill(cx0 + cw / 2 - 6, cy0 + ch - 21, cx0 + cw / 2 + 6, cy0 + ch - 20, opaque(mix(cc, 0xFFF0D2, 0.25f)));
            arcFin(g, f, arcade(affiche(cs[i])), cx0 + cw / 2f, cy0 + ch - 13, sel || sur ? CREME : lock ? 0xFF5E5248 : 0xFFC8B898, 1, sel);
            hit(cx0, cy0, cw, ch, 100 + i);
        }
        int ly = y0 + h - 15;
        if (isCustomClan()) {
            String v = customClan + ((now / 500) % 2 == 0 ? "|" : "");
            etiq(g, f, "NOM DU CLAN", x + 14, ly, OR_ETIQ);
            inter(g, f, inter(v.isEmpty() ? "|" : v), x + 96, ly, 9, CREME, 0);
            g.fill(x + 96, ly + 7, x + 226, ly + 8, R_OR[3]);
        } else if (unVerrou) {
            cadenas(g, x + 40, ly - 4);
            inter(g, f, inter("Clan ouvert sur candidature"), x + 52, ly, 7, DOUX, 0);
        } else {
            String desc = CharacterRules.clanDesc(clanBrut());
            if (desc != null && !desc.isEmpty()) {
                Component c = inter(desc);
                float s = 7f;
                while (largeur(f, c, s) > 212 && s > 5.5f) s -= 0.5f;
                inter(g, f, c, x + 14, ly, s, DOUX, 0);
            }
        }
    }

    private void etapeSexe(GuiGraphicsExtractor g, Font f, int x, int y0, int hMax, int mx, int my) {
        int ch = Math.min(168, hMax - 36 - 30);
        int h = 36 + ch + 28;
        PersoArt.panneau(g, P_SEXE, x, y0, h);
        int cw = 104;
        int gx = x + (240 - 2 * cw - 8) / 2;
        String[] lib = {"HOMME", "FEMME"}, kj = {"男", "女"};
        for (int i = 0; i < 2; i++) {
            int tx = gx + i * (cw + 8), ty = y0 + 36;
            boolean sel = i == sexeIdx;
            boolean sur = !sel && dans(mx, my, tx, ty, cw, ch);
            tuile(g, T_SEXE, sel ? ACTIF : sur ? SURVOL : NORMAL, tx, ty, ch);
            if (sel) halo(g, tx + cw / 2f, ty + ch * 0.48f, 50, 1.4f, desat(scene.couleur(), 0.8f), 0.08f, 2);
            boolean fem = i == 1, fin = fem || maleSlim;
            Identifier vt = variante("sexe" + i, sp -> {
                sp.female = fem;
                sp.slim = fin;
                if (sp.female != look.female) {
                    List<CharacterCatalog.Asset> hl = available("hair", fem), ol = available("outfit", fem);
                    sp.hairId = hl.isEmpty() ? "" : hl.get(0).id;
                    sp.outfitId = ol.isEmpty() ? "" : ol.get(0).id;
                }
            });
            float s = Math.min(3.1f, (ch - 50) / 32f);
            g.enableScissor(tx + 1, ty + 1, tx + cw - 1, ty + ch - 1);
            PersoPoupee.dessiner(g, skins, vt, fin, tx + cw / 2f, ty + ch - 30, s, i == 0 ? 16 : -16, 5, 0xFFFFFFFF);
            g.disableScissor();
            kanji16(g, f, kj[i], tx + 16, ty + 8, sel ? OR_CLAIR : 0xFF8A7458, 0xFF140608);
            arcFin(g, f, arcade(lib[i]), tx + cw / 2f, ty + ch - 18, sel ? CREME : 0xFFC8B898, 1, sel);
            hit(tx, ty, cw, ch, 200 + i);
        }
        inter(g, f, inter("Change la silhouette et les tenues proposées."), x + 14, y0 + h - 16, 7, DOUX, 0);
    }

    private void etapeNom(GuiGraphicsExtractor g, Font f, long now, int hautFig) {
        // plaque de nom au-dessus de la tête (aperçu de la plaque flottante)
        int ny = Math.max(150, hautFig - 18 - T);
        String n = name.trim().isEmpty() ? "…" : name.trim();
        float nw = largeur(f, inter(n), 9) + 14;
        g.fill(Math.round(cx - nw / 2), ny - 6, Math.round(cx + nw / 2), ny + 6, 0x960A0608);
        inter(g, f, inter(n), cx, ny, 9, CREME, 1);
        inter(g, f, inter("ainsi te verront les autres"), cx, ny - 14, 7, 0xC8C2B59A, 1);
        int x = cx - 180, y = 388 + B - T, w = 360, h = 64;
        blit(g, PLAQUE_NOM, x - 2, y - 2, 0, 0, 366, 71, 366, 71);
        float wn = inter(g, f, interGras(name), x + 22, y + 42, 13, CREME, 0);
        if ((now / 500) % 2 == 0) g.fill(Math.round(x + 22 + wn + 2), y + 34, Math.round(x + 22 + wn + 3), y + 49, OR_CLAIR);
        inter(g, f, inter(name.trim().length() + " / 20"), x + w - 20, y + 42, 7, DOUX, 2);
        losange(g, x + 6, y + h + 8);
        inter(g, f, inter("Lettres, espaces, apostrophe · 3 à 20 caractères"), x + 14, y + h + 10, 7, DOUX, 0);
        boolean ok = nomValide() && !nomPris();
        String etat = name.isBlank() ? "" : ok ? "Nom libre" : nomPris() ? "Déjà pris" : "Invalide";
        inter(g, f, inter(etat), x + w - 4, y + h + 10, 7, ok ? 0xFF9CC8A8 : 0xFFE08070, 2);
    }

    private void etapeAge(GuiGraphicsExtractor g, Font f, int x, int y0, int mx, int my) {
        PersoArt.panneau(g, P_AGE, x, y0, 184);
        int X = x + 14, WL = 212;
        float yy = y0 + 50;
        inter(g, f, interGras(age + " ans"), X, yy, 16, CREME, 0);
        plusMoins(g, X + WL - 44, Math.round(yy) - 7, mx, my);
        int ry = Math.round(yy) + 22;
        regleDyn(g, f, X + 2, ry, WL - 4, 10, 60, age, new int[]{10, 20, 30, 40, 50, 60});
        int by = ry + 28, tw = 68;
        String[] lib = {"ACADEMIE", "CROISSANCE", "ADULTE"};
        String[] sous = {"10 – 12 ans", "13 – 16 ans", "17 ans et +"};
        int cur = age < 13 ? 0 : age < 17 ? 1 : 2;
        for (int k = 0; k < 3; k++) {
            int tx = X + k * (tw + 4);
            boolean sur = k != cur && dans(mx, my, tx, by, tw, 34);
            tuile(g, T_AGE, k == cur ? ACTIF : sur ? SURVOL : NORMAL, tx, by, 34);
            arcFin(g, f, arcade(lib[k]), tx + tw / 2f, by + 8, k == cur ? CREME : 0xFFA8946E, 1, k == cur);
            inter(g, f, inter(sous[k]), tx + tw / 2f, by + 23, 7, k == cur ? 0xFFF2D8B0 : DOUX, 1);
            hit(tx, by, tw, 34, 300 + k);
        }
        String phrase = age < 13 ? "Encore à l'Académie : ton corps n'a pas fini de grandir."
            : age < 17 ? "Jeune genin : tu grandis encore un peu." : "Ton corps a fini de grandir.";
        inter(g, f, inter(phrase), X, by + 50, 8, CREME, 0);
        inter(g, f, inter("Ta taille d'aujourd'hui suit ton âge (étape suivante)."), X, by + 64, 7, DOUX, 0);
    }

    private void etapeTaille(GuiGraphicsExtractor g, Font f, int x, int y0, int mx, int my, int sx, int pieds, float k) {
        PersoArt.panneau(g, P_TAILLE, x, y0, 172);
        int X = x + 14, WL = 212;
        float yy = y0 + 50;
        float wv = inter(g, f, interGras(cmAdulte() + " cm"), X, yy, 16, CREME, 0);
        etiq(g, f, "ADULTE", X + wv + 8, yy + 2, OR_ETIQ);
        plusMoins(g, X + WL - 44, Math.round(yy) - 7, mx, my);
        int ry = Math.round(yy) + 22;
        regleDyn(g, f, X + 2, ry, WL - 4, 153, 207, cmAdulte(), new int[]{160, 170, 180, 190, 200});
        etiq(g, f, "AUJOURD'HUI", X, ry + 32, OR_ETIQ);
        inter(g, f, interGras(cmActuel() + " cm"), X + 84, ry + 32, 9, CREME, 0);
        int reste = cmAdulte() - cmActuel();
        inter(g, f, inter(reste > 0 ? "· encore " + reste + " cm à prendre" : "· taille d'adulte"), X + 128, ry + 32, 7, DOUX, 0);
        inter(g, f, inter(reste > 0 ? "Taille adulte " + cmAdulte() + " cm — pour l'instant, tu fais " + cmActuel() + " cm."
            : "Taille adulte atteinte : " + cmAdulte() + " cm."), X, ry + 56, 7, CREME, 0);
        inter(g, f, inter("Au centimètre près, de 153 à 207 cm."), X, ry + 70, 7, DOUX, 0);
        // toise plantée à côté du personnage (repère : le sol de la scène)
        g.pose().pushMatrix();
        g.pose().translate(0, -T);
        toise(g, f, sx + 90, pieds);
        g.pose().popMatrix();
    }

    /** Toise : mât et crans cuits ; valeurs, repère adulte et plaquette coulissante dessinés. */
    private void toise(GuiGraphicsExtractor g, Font f, int x, int ysol) {
        float pcm = 240f / 172f;
        blit(g, TOISE, x - 6, ysol - 310, 0, 0, 24, 314, 24, 314);
        int adulte = cmAdulte(), cm = cmActuel();
        LotGui.Textes crans = LotGui.textes(g);              // graduations : un seul élément GUI
        for (int c = 150; c <= 210; c += 10) {
            if (Math.abs(c - adulte) < 4 || Math.abs(c - cm) < 5) continue;
            arcFin(crans, g, f, arcade(String.valueOf(c)), x + 15, ysol - c * pcm - 1.75f, 0xFFA8946E, 0, false);
        }
        crans.fermer();
        int ya = Math.round(ysol - adulte * pcm);
        if (adulte != cm) {
            LotGui l = LotGui.ouvrir(g);
            for (int i = x - 3; i < x + 12; i += 2) l.fill(i, ya, i + 1, ya + 1, R_OR[3]);
            l.fermer();
            arcFin(g, f, arcade("ADULTE " + adulte), x - 5, ya - 1.75f, 0xFFC8A870, 2, false);
        }
        int Y = Math.round(ysol - cm * pcm);
        LotGui pointilles = LotGui.ouvrir(g);
        for (int i = x - 62; i < x - 2; i += 2) pointilles.fill(i, Y, i + 1, Y + 1, R_OR[4]);
        pointilles.fermer();
        int bx = x + 13;
        blit(g, CURSEURS, bx - 6, Y - 7, 14, 0, 50, 16, 64, 16);
        arcFin(g, f, arcade(cm + " CM"), bx + 20, Y - 1.75f, CREME, 1, true);
        halo(g, bx + 20, Y, 22, 1f, 0xD9A95E, 0.07f, 2);
    }

    private void plusMoins(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        for (int i = 0; i < 2; i++) {
            int bx = x + i * 22;
            if (dans(mx, my, bx, y, 18, 14)) g.fill(bx + 1, y + 1, bx + 17, y + 13, 0x30F2D08A);
            hit(bx, y, 18, 14, i == 0 ? A_MOINS : A_PLUS);
        }
    }

    /** Partie mobile d'une réglette (le bois et les crans sont cuits dans la planche). */
    private void regleDyn(GuiGraphicsExtractor g, Font f, int x, int y, int w, int vmin, int vmax, int v, int[] etiquettes) {
        int n = vmax - vmin;
        for (int val : etiquettes) {
            arcFin(g, f, arcade(String.valueOf(val)), x + 2 + (val - vmin) / (float) n * (w - 4), y + 12, 0xFFB8A27E, 1, false);
        }
        int X = Math.round(x + 2 + (v - vmin) / (float) n * (w - 4));
        g.fill(x + 2, y + 9, X, y + 10, R_OR[5]);
        blit(g, CURSEURS, X - 5, y - 6, 0, 0, 12, 14, 64, 16);
        regle = new int[]{x, w, vmin, vmax, y + oyHit};
    }

    private void etapeLook(GuiGraphicsExtractor g, Font f, int GX, int DX, int y0, int hMax, int mx, int my) {
        int lc = lignesCheveux(), lt = lignesTenues();
        int hG = lc == 2 ? 352 : 302, hD = lt == 3 ? 352 : 254;
        // planche TENUE : avec des accessoires au catalogue, la rangée Accessoire prend le bas de la planche et les
        // tenues gardent leurs vignettes pleines tant qu'il en tient deux rangées (sinon vignettes un peu plus basses)
        List<CharacterCatalog.Asset> al = accessoires();
        boolean avecAcc = !al.isEmpty();
        int ch = 92;
        if (avecAcc) {
            int place = Math.min(352, hMax) - 36 - H_ACC + 6;
            lt = 3;
            while (lt > 1 && place / lt - 6 < 70) lt--;
            ch = Math.min(92, place / lt - 6);
            // même bas que la planche APPARENCE ; la rangée Accessoire se pose au bas de la planche
            hD = Math.max(36 + lt * (ch + 6) - 6 + H_ACC, Math.min(hG, Math.min(352, hMax)));
        }
        rangeesTenues = lt;
        PersoArt.panneau(g, P_APPARENCE, GX, y0, hG);
        int X = GX + 14, WL = 258;
        float yy = y0 + 46;
        // carrure
        etiq(g, f, "CARRURE", X, yy, OR_ETIQ);
        for (int i = 0; i < 2; i++) {
            int bx = X + 64 + i * 76, by = Math.round(yy) - 10;
            boolean choisi = (i == 1) == look.slim;
            boolean dis = look.female && i == 0;
            boolean sur = !choisi && !dis && dans(mx, my, bx, by, 70, 20);
            bouton(g, f, i == 0 ? B_LARGE : B_FINE, choisi ? 2 : sur ? 1 : 0, bx, by, null);
            if (dis) g.fill(bx, by, bx + 70, by + 20, 0x700A0608);
            if (!dis) hit(bx, by, 70, 20, 400 + i);
        }
        // peau
        yy += 30;
        etiq(g, f, "PEAU", X, yy, OR_ETIQ);
        String[] tons = {"Très clair", "Très clair", "Clair", "Clair", "Clair", "Mat", "Mat", "Hâlé", "Hâlé", "Brun", "Foncé", "Très foncé"};
        inter(g, f, inter(tons[skinIdx]), X + WL, yy, 7, DOUX, 2);
        LotGui peaux = LotGui.ouvrir(g);                    // les douze pastilles : un seul élément GUI
        for (int i = 0; i < 12; i++) {
            int px = X + 2 + i * 21, py = Math.round(yy) + 10;
            pastille(peaux, px, py, SkinSpec.skinRamp(i / 11f), i == skinIdx, 15);
            hit(px - 2, py - 2, 19, 19, 500 + i);
        }
        peaux.fermer();
        if (skinIdx >= 0 && skinIdx < 12) pointe(g, X + 2 + skinIdx * 21 + 15 / 2, Math.round(yy) + 10 + 15 + 4);
        // cheveux : têtes rendues avec la coiffure
        yy += 46;
        List<CharacterCatalog.Asset> hl = hairs();
        etiq(g, f, "CHEVEUX", X, yy, OR_ETIQ);
        String hn = hl.isEmpty() ? "—" : hl.get(Math.min(hairIdx, hl.size() - 1)).name;
        inter(g, f, inter(hn), X + WL, yy, 7, DOUX, 2);
        int tw = 60, th = 46;
        for (int k = 0; k < 4 * lc; k++) {
            int i = hairScroll * 4 + k;
            if (i >= hl.size()) break;
            int tx = X + (k % 4) * (tw + 4), ty = Math.round(yy) + 9 + (k / 4) * (th + 4);
            boolean sel = i == hairIdx;
            boolean sur = !sel && dans(mx, my, tx, ty, tw, th);
            tuile(g, T_CHEVEUX, sel ? ACTIF : sur ? SURVOL : NORMAL, tx, ty, th);
            String hid = hl.get(i).id;
            Identifier vt = variante("hair:" + hid, sp -> sp.hairId = hid);
            g.enableScissor(tx + 2, ty + 2, tx + tw - 2, ty + th - 2);
            PersoPoupee.dessiner(g, skins, vt, look.slim, tx + tw / 2f, ty + 6 + 32 * 3.5f, 3.5f, 18, 4, 0xFFFFFFFF);
            g.disableScissor();
            hit(tx, ty, tw, th, 600 + i);
        }
        if ((hl.size() + 3) / 4 > lc) {
            inter(g, f, inter("Molette · " + (hairScroll + 1) + " / " + ((hl.size() + 3) / 4 - lc + 1)), X + WL, yy + 9 + lc * (th + 4) + 4, 7, DOUX, 2);
        }
        // couleur des cheveux : palette du clan
        yy += lc * (th + 4) + 26;
        Palette p = pal();
        etiq(g, f, "COULEUR", X, yy, OR_ETIQ);
        puceClan(g, f, X + WL, yy, hairColorIdx < 0 ? "Couleur libre" : p.nom());
        int nc = p.cheveux().length, pasC = Math.min(26, (WL - 4) / Math.max(1, nc + 1));
        LotGui teintes = LotGui.ouvrir(g);                  // pastilles et pipette : un seul élément GUI
        for (int i = 0; i < nc; i++) {
            int px = X + 2 + i * pasC, py = Math.round(yy) + 10;
            pastille(teintes, px, py, p.cheveux()[i], i == hairColorIdx, 17);
            hit(px - 2, py - 2, 21, 21, 700 + i);
        }
        int lx = X + 2 + nc * pasC, ly = Math.round(yy) + 10;
        pipette(teintes, lx, ly, hairLibreC, hairColorIdx < 0, 17, dans(mx, my, lx - 2, ly - 2, 21, 21));
        teintes.fermer();
        if (hairColorIdx >= 0 && hairColorIdx < nc) pointe(g, X + 2 + hairColorIdx * pasC + 17 / 2, ly + 17 + 4);
        else if (hairColorIdx < 0) pointe(g, lx + 17 / 2, ly + 17 + 4);
        hit(lx - 2, ly - 2, 21, 21, A_LIBRE_CHEVEUX);
        // yeux
        yy += 46;
        etiq(g, f, "YEUX", X, yy, OR_ETIQ);
        puceClan(g, f, X + WL, yy, eyeIdx < 0 ? "Couleur libre" : p.nomYeux());
        int ny = p.yeux().length, pasY = Math.min(44, (WL - 4 - 26) / Math.max(1, ny));
        LotGui yeux = LotGui.ouvrir(g);                     // vignettes d'yeux et pipette : un seul élément GUI
        for (int i = 0; i < ny; i++) {
            int px = X + 2 + i * pasY, py = Math.round(yy) + 10;
            oeil(yeux, px, py, p.yeux()[i], i == eyeIdx, pasY - 6);
            hit(px - 2, py - 2, pasY - 2, 20, 800 + i);
        }
        int ex = X + 2 + ny * pasY, ey = Math.round(yy) + 10;
        pipette(yeux, ex, ey - 1, eyeLibreC, eyeIdx < 0, 17, dans(mx, my, ex - 2, ey - 3, 21, 21));
        yeux.fermer();
        if (eyeIdx >= 0 && eyeIdx < ny) pointe(g, X + 2 + eyeIdx * pasY + Math.max(28, Math.min(38, pasY - 6)) / 2, ey + 16 + 4);
        else if (eyeIdx < 0) pointe(g, ex + 17 / 2, ey - 1 + 17 + 4);
        hit(ex - 2, ey - 3, 21, 21, A_LIBRE_YEUX);
        // planche TENUE : corps entiers
        PersoArt.panneau(g, P_TENUE, DX, y0, hD);
        List<CharacterCatalog.Asset> ol = outfits();
        int cw = 80;
        float sT = Math.min(2.5f, (ch - 12) / 32f);
        int gx = DX + (276 - 3 * cw - 2 * 6) / 2;
        for (int k = 0; k < 3 * lt; k++) {
            int i = outfitScroll * 3 + k;
            if (i >= ol.size()) break;
            int tx = gx + (k % 3) * (cw + 6), ty = y0 + 36 + (k / 3) * (ch + 6);
            boolean sel = i == outfitIdx;
            boolean sur = !sel && dans(mx, my, tx, ty, cw, ch);
            tuile(g, T_TENUE, sel ? ACTIF : sur ? SURVOL : NORMAL, tx, ty, ch);
            String oid = ol.get(i).id;
            Identifier vt = variante("outfit:" + oid, sp -> sp.outfitId = oid);
            PersoPoupee.dessiner(g, skins, vt, look.slim, tx + cw / 2f, ty + ch - 4, sT, 18, 5, 0xFFFFFFFF);
            hit(tx, ty, cw, ch, 900 + i);
        }
        int reste = ol.size() - (outfitScroll * 3 + 3 * lt);
        if (reste > 0 || outfitScroll > 0) {
            String t = reste > 0 ? "Molette : " + reste + " autre" + (reste > 1 ? "s" : "") + " tenue" + (reste > 1 ? "s" : "")
                : "Molette : remonter";
            int ym = avecAcc ? y0 + 36 + lt * (ch + 6) + 4 : y0 + hD - 11;
            inter(g, f, inter(t), DX + 138, ym, 7, DOUX, 1);
        }
        if (avecAcc) rangeeAccessoire(g, f, DX, y0 + hD - H_ACC, al, mx, my);
        else zoneAcc = new int[4];
        // anneau de rotation autour des pieds
        int pcx = cx + 6, pcy = 452 + B + 6 - T;
        LotGui anneau = LotGui.ouvrir(g);                   // l'anneau pointillé et ses flèches : un seul élément GUI
        for (int s = 0; s < 360; s += 6) {
            if (s > 200 && s < 340) continue;
            double a = Math.toRadians(s);
            int px = (int) Math.round(pcx + Math.cos(a) * 66), py = (int) Math.round(pcy + Math.sin(a) * 15);
            anneau.fill(px, py, px + 1, py + 1, 0x8CD9A95E);
        }
        for (int sg = -1; sg <= 1; sg += 2) {
            int ax = pcx + sg * 66;
            for (int j = 0; j < 3; j++) {
                anneau.fill(ax - sg * j, pcy - 3 + j, ax - sg * j + 1, pcy - 2 + j, R_OR[5]);
                anneau.fill(ax - sg * j, pcy + 3 - j, ax - sg * j + 1, pcy + 4 - j, R_OR[5]);
            }
        }
        anneau.fermer();
    }

    /**
     * Rangée ACCESSOIRE au bas de la planche TENUE, comme les rangées de la planche APPARENCE : étiquette, nom à droite,
     * vignettes têtes (« Aucun » puis le catalogue), quatre par rangée, molette au-delà. Pas de couleur : les
     * accessoires actuels sont en teinte fixe.
     */
    private void rangeeAccessoire(GuiGraphicsExtractor g, Font f, int DX, int yHaut, List<CharacterCatalog.Asset> al,
                                  int mx, int my) {
        int X = DX + 14, WD = DX + 262;
        // filet de séparation sous les tenues
        int yf = yHaut + 16;
        LotGui filet = LotGui.ouvrir(g);                    // filet pointillé : un seul élément GUI
        for (int i = X; i < WD; i += 2) filet.fill(i, yf, i + 1, yf + 1, R_OR[3]);
        filet.fermer();
        losange(g, DX + 136, yf - 2);
        float yy = yf + 14;
        etiq(g, f, "ACCESSOIRE", X, yy, OR_ETIQ);
        CharacterCatalog.Asset porte = accessoire();
        inter(g, f, inter(porte == null ? "Aucun" : porte.name), WD, yy, 7, DOUX, 2);
        int n = al.size() + 1, tw = 60, th = 46;
        accScroll = Math.max(0, Math.min(accScroll, (n + 3) / 4 - 1));
        int gx = DX + (276 - 4 * tw - 3 * 4) / 2, ty = Math.round(yy) + 9;
        zoneAcc = new int[]{gx, ty + oyHit, 4 * tw + 12, th};
        for (int k = 0; k < 4; k++) {
            int i = accScroll * 4 + k;
            if (i >= n) break;
            int tx = gx + k * (tw + 4);
            String id = i == 0 ? "" : al.get(i - 1).id;
            boolean sel = id.equals(look.accessoryId == null ? "" : look.accessoryId);
            boolean sur = !sel && dans(mx, my, tx, ty, tw, th);
            tuile(g, T_CHEVEUX, sel ? ACTIF : sur ? SURVOL : NORMAL, tx, ty, th);
            Identifier vt = variante("acc:" + id, sp -> sp.accessoryId = id);
            g.enableScissor(tx + 2, ty + 2, tx + tw - 2, ty + th - 2);
            PersoPoupee.dessiner(g, skins, vt, look.slim, tx + tw / 2f, ty + 6 + 32 * 3.5f, 3.5f, 18, 4, 0xFFFFFFFF);
            if (i == 0) {
                g.fill(tx + 2, ty + th - 13, tx + tw - 2, ty + th - 2, 0xB40A0608);
                arcFin(g, f, arcade("AUCUN"), tx + tw / 2f, ty + th - 10, sel ? CREME : 0xFFC8B898, 1, sel);
            }
            g.disableScissor();
            hit(tx, ty, tw, th, 1100 + i);
        }
        if (n > 4) {
            inter(g, f, inter("Molette · " + (accScroll + 1) + " / " + ((n + 3) / 4)), WD, ty + th + 7, 7, DOUX, 2);
        }
    }

    private void puceClan(GuiGraphicsExtractor g, Font f, int xd, float ymil, String texte) {
        float w = inter(g, f, inter(texte), xd, ymil, 7, 0xFFD8C8F0, 2);
        losange(g, Math.round(xd - w - 7), Math.round(ymil) - 1);
    }

    /** Pastille de couleur carrée (cadre or, reflet) ; choisie = double cadre or vif + pointe dessous. */
    private static void pastille(GuiGraphicsExtractor g, int x, int y, int c, boolean sel, int t) {
        LotGui l = LotGui.ouvrir(g);
        pastille(l, x, y, c, sel, t);
        l.fermer();
        if (sel) pointe(g, x + t / 2, y + t + 4);
    }

    /** Pastille dans un lot d'aplats ; la pointe de la pastille choisie est à poser après le lot. */
    private static void pastille(LotGui g, int x, int y, int c, boolean sel, int t) {
        if (sel) {
            g.fill(x - 3, y - 3, x + t + 3, y + t + 3, R_OR[6]);
            g.fill(x - 2, y - 2, x + t + 2, y + t + 2, 0xFF120A0C);
        }
        g.fill(x - 1, y - 1, x + t + 1, y + t + 1, sel ? R_OR[5] : R_OR[3]);
        g.fill(x, y, x + t, y + t, opaque(c));
        g.fill(x, y, x + t, y + 1, opaque(mix(c, 0xFFFFFF, 0.35f)));
        g.fill(x, y + t - 1, x + t, y + t, opaque(mix(c, 0x000000, 0.3f)));
    }

    /**
     * Pastille « pipette » (couleur libre) : la couleur libre choisie, sinon un petit nuancier en quartiers ; marquée
     * d'un « + » ; choisie = même cadre que les pastilles de la palette.
     */
    private static void pipette(GuiGraphicsExtractor g, int x, int y, int c, boolean sel, int t, boolean survol) {
        LotGui l = LotGui.ouvrir(g);
        pipette(l, x, y, c, sel, t, survol);
        l.fermer();
        if (sel) pointe(g, x + t / 2, y + t + 4);
    }

    /** Pipette dans un lot d'aplats ; la pointe (choisie) est à poser après le lot. */
    private static void pipette(LotGui g, int x, int y, int c, boolean sel, int t, boolean survol) {
        if (sel) {
            g.fill(x - 3, y - 3, x + t + 3, y + t + 3, R_OR[6]);
            g.fill(x - 2, y - 2, x + t + 2, y + t + 2, 0xFF120A0C);
        }
        g.fill(x - 1, y - 1, x + t + 1, y + t + 1, sel || survol ? R_OR[5] : R_OR[3]);
        if (c >= 0) {
            g.fill(x, y, x + t, y + t, opaque(c));
        } else {
            int m = t / 2;
            g.fill(x, y, x + m, y + m, 0xFFC23B3B);
            g.fill(x + m, y, x + t, y + m, 0xFFE8D27A);
            g.fill(x, y + m, x + m, y + t, 0xFF3A6EA5);
            g.fill(x + m, y + m, x + t, y + t, 0xFF4E8A4A);
        }
        int cx = x + t / 2, cy = y + t / 2;
        g.fill(cx - 3, cy - 1, cx + 4, cy + 2, 0xD0120A0C);
        g.fill(cx - 1, cy - 3, cx + 2, cy + 4, 0xD0120A0C);
        g.fill(cx - 2, cy, cx + 3, cy + 1, 0xFFF5E9D0);
        g.fill(cx, cy - 2, cx + 1, cy + 3, 0xFFF5E9D0);
    }

    /** Vignette d'œil 38×16 : peau, blanc de l'œil, iris à la couleur, reflet. */
    private void oeil(GuiGraphicsExtractor g, int x, int y, int iris, boolean sel, int w) {
        LotGui l = LotGui.ouvrir(g);
        oeil(l, x, y, iris, sel, w);
        l.fermer();
        if (sel) pointe(g, x + Math.max(28, Math.min(38, w)) / 2, y + 16 + 4);
    }

    /** Vignette d'œil dans un lot d'aplats ; la pointe (choisie) est à poser après le lot. */
    private void oeil(LotGui g, int x, int y, int iris, boolean sel, int w) {
        int h = 16;
        w = Math.max(28, Math.min(38, w));
        if (sel) {
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, R_OR[6]);
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF120A0C);
        }
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, sel ? R_OR[5] : R_OR[3]);
        g.fill(x, y, x + w, y + h, opaque(look.skinColor));
        g.fill(x, y, x + w, y + 2, opaque(look.hairColor));
        for (int ox : new int[]{w / 2 - 13, w / 2 + 3}) {
            g.fill(x + ox, y + 5, x + ox + 10, y + 11, 0xFFF4F0F4);
            g.fill(x + ox + 3, y + 5, x + ox + 7, y + 11, opaque(iris));
            g.fill(x + ox + 3, y + 5, x + ox + 7, y + 6, opaque(mix(iris, 0, 0.25f)));
            g.fill(x + ox + 4, y + 7, x + ox + 6, y + 9, opaque(mix(iris, 0xFFFFFF, 0.45f)));
            g.fill(x + ox, y + 4, x + ox + 10, y + 5, 0xFF2A2022);
        }
    }

    private void etapeRecap(GuiGraphicsExtractor g, Font f, int x, int y0, int mx, int my) {
        PersoArt.panneau(g, P_DOSSIER, x, y0, 254);
        int wx = x + 14, wy = y0 + 36, ww = 96, wh = 112;
        Identifier tex = figureTex;
        g.enableScissor(wx + 4, wy + 4, wx + ww - 4, wy + wh - 4);
        PersoPoupee.dessiner(g, skins, tex, look.slim, wx + ww / 2f, wy + 12 + 32 * 6f, 6f, 16, 4, 0xFFFFFFFF);
        g.disableScissor();
        int cx0 = wx + ww + 10;
        String clan = clanName();
        emblemeCentre(g, idxClan(CharacterRules.slug(clanBrut())), cx0 + 38, wy + 28, 32, OR_CLAIR);
        inter(g, f, interGras(clan.isEmpty() ? "Sans clan" : "Clan " + affiche(clan)), cx0 + 38, wy + 64, 9, CREME, 1);
        embleme(g, idxVillage(village), cx0 + 4, wy + 76, 12, DOUX);
        inter(g, f, inter(CharacterRules.villages()[village]), cx0 + 18, wy + 82, 8, DOUX, 0);
        inter(g, f, inter("Académicien"), cx0 + 38, wy + 100, 8, 0xFFC8A870, 1);
        int vy = wy + wh + 10;
        String[] lib = {"FACE", "PROFIL", "DOS"};
        float[] lac = {0, 90, 180};
        for (int i = 0; i < 3; i++) {
            int vx = x + 14 + i * 72;
            boolean sel = i == vue;
            boolean sur = !sel && dans(mx, my, vx, vy, 66, 84);
            tuile(g, T_VUE, sel ? ACTIF : sur ? SURVOL : NORMAL, vx, vy, 84);
            PersoPoupee.dessiner(g, skins, tex, look.slim, vx + 33, vy + 70, 1.7f, lac[i], 4, 0xFFFFFFFF);
            arcFin(g, f, arcade(lib[i]), vx + 33, vy + 74, sel ? CREME : DOUX, 1, false);
            hit(vx, vy, 66, 84, 1000 + i);
        }
    }

    /* ------------------------------------------------------------- ouverture */

    private static final String[][] MURMURES = {{"Avant", "le", "nom…"}, {"avant", "le", "sang…"},
        {"il", "n'y", "avait", "que", "le", "chakra."}};
    private static final long[] MURMURE_A = {300, 1600, 2900};

    private void intro(GuiGraphicsExtractor g, Font f, long now) {
        long t = now - introAt;
        float part = intro < 2 ? 1f : Math.max(0f, 1f - (now - questionsAt) / 700f);
        for (int l = 0; l < MURMURES.length; l++) {
            String[] mots = MURMURES[l];
            boolean dernier = l == MURMURES.length - 1;
            float s = dernier ? 1.5f : 1f;
            int col = dernier ? 0xFFF2C878 : CREME;
            float total = 0;
            for (String m : mots) total += f.width(arcade(m + " ")) * s;
            float x = cx - total / 2f;
            float ly = 70 + T + l * 22;
            for (int i = 0; i < mots.length; i++) {
                Component c = arcade(mots[i] + " ");
                long at = MURMURE_A[l] + i * 220L;
                float wa = Math.min(1f, Math.max(0f, (t - at) / 600f));
                if (wa > 0) texteEchelle(g, f, c, x, ly + (1 - wa) * 6, alpha(col, wa * part * (l == 0 ? 0.75f : l == 1 ? 0.88f : 1f)), s);
                x += f.width(c) * s;
            }
        }
        if (t > 1400) {
            float in = Math.min(1f, (t - 1400) / 800f);
            float blink = 0.3f + 0.7f * (float) (0.5 + 0.5 * Math.cos((t - 1400) / 1000.0 * 3.0));
            arcadeS(g, f, arcade("APPUIE SUR UNE TOUCHE"), cx, 500 + B, alpha(CREME, in * blink * part), 1f, 1, true);
        }
        float noir = Math.max(0f, 1f - t / 1600f);
        if (noir > 0) g.fill(0, 0, width, height, argb(0, noir));
    }

    /* ------------------------------------------------------------- banc d'essai */

    void debugEtat(int village, int clan, int sexe, String nom, int age, int cm, int skin, int hair, int hairColor, int eye,
                   int outfit) {
        if (intro < 2) { intro = 2; questionsAt = System.currentTimeMillis() - 5000; }
        if (village >= 0) { this.village = village; scene.forcerVillage(village); PersoSons.ambiance(village); }
        if (clan >= 0) this.clanIdx = clan;
        this.sexeIdx = sexe;
        look.female = sexe == 1;
        look.slim = look.female;
        if (nom != null) this.name = nom;
        this.age = age;
        this.size = cm / 180.0;
        this.skinIdx = skin;
        look.skinColor = SkinSpec.skinRamp(skin / 11f);
        this.hairIdx = hair;
        this.hairColorIdx = hairColor;
        this.eyeIdx = eye;
        this.outfitIdx = outfit;
        appliquerPalette();
        applyLists();
    }

    void debugEtape(int s) {
        setStep(s);
        stepAt = System.currentTimeMillis() - 5000;
        lacet = lacetCible;
    }

    void debugCouleurLibre(int rgb) {
        ouvrirLibre(0);
        for (char c : String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF).toCharArray()) picker.car(c);
    }

    /** Porte le premier accessoire du catalogue (capture « bandeau porté »). */
    void debugAccessoire() { choisirAccessoire(1); }

    void debugRegleVoix(boolean coupee) { PersoSons.voixCoupee = coupee; }
}
