package fr.reborn.hud.menu.tirage;

import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.settings.ReglagesArt;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.NatureIcones;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * <b>Test de la Feuille</b> v3 (16/10) — on canalise son chakra dans une feuille de papier à chakra pincée par le
 * haut par le bout du bras Minecraft du joueur (à son skin : {@link TirageBras}) ; sa réaction révèle la nature
 * (canon) : <b>Katon</b> elle s'enflamme et se consume, <b>Suiton</b> elle se gorge d'eau et goutte, <b>Doton</b>
 * elle se dessèche et s'effrite, <b>Raiton</b> elle se froisse en zigzag, <b>Fūton</b> elle se coupe net en deux. Double nature : la feuille se partage en bandes qui réagissent l'une après
 * l'autre (les natures déjà éveillées d'abord, la nouvelle en dernier).
 *
 * <p>Mise en scène : la caméra quitte l'épaule et glisse vers un plan fixe de ¾ face posé dans le monde
 * ({@link CameraTirage}) ; le personnage lève la feuille devant son visage, l'autre main en
 * demi-sceau ({@link TirageAnimations}) ; l'écran occupe la droite, sobre (voile de nuit, titre or, filet) : la feuille
 * en gros plan (pixel art, une case d'atlas par image, {@code tools/gen_tirage_textures.py}), « Maintenir [Espace] pour
 * canaliser » et sa jauge, la lueur de chakra qui grandit autour de la feuille, puis la réaction de la nature, le
 * symbole canonique ({@link NatureIcones}), son nom, une phrase ; à côté, les cinq natures et leur effet. Aucun kanji
 * décoratif (DA §3).
 *
 * <p>Déroulé (serveur autoritaire, tirage inchangé) : confirmation → entrée (glissement de caméra, la feuille monte) →
 * canalisation (maintenir) → quand la jauge est pleine, le client envoie {@code go} ; le serveur tire, attribue et
 * renvoie {@code <NATURE>|<clan>|<AUTRES>} (ou {@code deny}) → réaction → révélation → « Valider » → retour de la
 * caméra. Annuler avant la fin de la jauge (Échap) ne consomme rien.
 */
public class TirageScreen extends Screen {

    // Durées (ms).
    private static final long ENTREE_MS = CameraTirage.ENTREE_MS;
    private static final long CHARGE_MS = 2200;              // jauge pleine en 2,2 s de maintien
    static final long FRAME_MS = 70;
    static final int FRAMES = 32;
    private static final long REACTION_MS = FRAMES * FRAME_MS;
    private static final long BANDE_DECALAGE_MS = 1100;     // double nature : chaque bande réagit après la précédente
    private static final long TENUE_MS = 650;                // dernière image tenue avant la révélation
    private static final long ATTENTE_MAX_MS = 6000;         // attente du résultat serveur (sinon tirage local)

    enum Phase { CONFIRM, ENTREE, CANAL, ATTENTE, REACTION, RESULT }

    private Phase phase = Phase.ENTREE;
    private long phaseStart = TirageHorloge.now();
    private Nature nature;
    /** Natures déjà éveillées (double nature) : bandes de gauche, réagissent en premier. */
    private final List<Nature> autres = new ArrayList<>();
    private boolean reactionSoundPlayed;
    /** Prochain son de réaction à jouer (2 par bande : la réaction, puis son écho 0,6 s après). */
    private int bandeSon;
    private float charge;
    private long dernierTick = -1;
    private int prochainSonCharge;
    private boolean sourisTenue;

    private final Random rng = new Random();

    /** Clan du perso (pilote la pondération du tirage local de démo). */
    private final String clan;
    /** Nature imposée par le serveur (autoritaire). null = roll local (démo). */
    private final String forcedNatureName;
    private boolean confirmMode = false;
    /** En attente du résultat serveur (après la jauge pleine). */
    private boolean awaitingResult = false;
    /** Test piloté par le serveur → pas de reroll F. */
    private boolean serverDriven = false;
    /** Banc de captures : étape à afficher d'emblée (−1 = déroulé normal). */
    private int bancEtape = -1;
    /** Banc : maintien simulé de la touche de canalisation. */
    boolean debugTenir;
    private boolean initialise;
    /** Le bras qui tient la feuille au gros plan (skin du joueur relu toutes les 10 ticks). */
    private final TirageBras bras = new TirageBras();
    private int ticks;

    /** Le serveur a répondu : fixe la nature de l'écran en cours. {@code clanEtAutres} = « clan|AUTRES » (facultatif). */
    public void applyServerResult(String natureName) { applyServerResult(natureName, null); }

    public void applyServerResult(String natureName, String clanEtAutres) {
        nature = Nature.fromName(natureName);
        lireAutres(clanEtAutres);
        awaitingResult = false;
    }

    public boolean isAwaiting() { return awaitingResult; }

    /** Démo client : roll local, va direct au test. */
    public TirageScreen() { this(null, null); }

    /** Déclenché par le serveur : nature déjà tirée ; {@code clan} peut porter « clan|AUTRES ». */
    public TirageScreen(String forcedNatureName, String clan) {
        super(Component.literal("Test de la feuille"));
        String c = clan;
        if (c != null && c.contains("|")) {
            lireAutres(c);
            c = c.substring(0, c.indexOf('|'));
        }
        this.forcedNatureName = forcedNatureName;
        this.clan = c == null || c.isBlank() ? null : c;
        this.serverDriven = forcedNatureName != null;
    }

    /** Popup « Faire le test ? » (Confirmer / Refuser) avant le test — item / commande. */
    public static TirageScreen confirm(String clan) {
        TirageScreen s = new TirageScreen(null, clan);
        s.confirmMode = true;
        return s;
    }

    /**
     * Banc de captures : la nature (et les autres, « SUITON,RAITON »), à l'étape 0 entrée, 1 canalisation (jauge aux
     * deux tiers), 2 réaction (milieu), 3 résultat.
     */
    public static TirageScreen banc(String natureName, String autres, int etape) {
        TirageScreen s = new TirageScreen(natureName, autres == null ? null : "|" + autres);
        s.bancEtape = etape;
        return s;
    }

    private void lireAutres(String clanEtAutres) {
        if (clanEtAutres == null) return;
        int i = clanEtAutres.indexOf('|');
        if (i < 0) return;
        autres.clear();
        for (String p : clanEtAutres.substring(i + 1).split(",")) {
            Nature n = Nature.parse(p);
            if (n != null && !autres.contains(n) && autres.size() < 2) autres.add(n);
        }
        autres.remove(nature);
    }

    private static long now() { return TirageHorloge.now(); }
    private void setPhase(Phase p) { phase = p; phaseStart = now(); }
    private long ecoule() { return now() - phaseStart; }
    private float pt(long dur) { return Math.min(1f, ecoule() / (float) dur); }

    Phase phase() { return phase; }
    String phaseNom() { return phase.name(); }

    @Override
    protected void init() {
        if (initialise) return;                 // redimensionnement : on garde l'état
        initialise = true;
        bras.actualiser();
        if (confirmMode) { setPhase(Phase.CONFIRM); return; }
        startTest();
        if (bancEtape >= 0) {
            switch (bancEtape) {
                case 1 -> { setPhase(Phase.CANAL); charge = 0.66f; }
                case 2 -> {
                    commencerReaction();
                    phaseStart -= REACTION_MS / 2 + (long) autres.size() * BANDE_DECALAGE_MS;
                    reactionSoundPlayed = true;
                }
                case 3 -> { commencerReaction(); setPhase(Phase.RESULT); phaseStart -= 5000; }
                default -> { }
            }
            bancEtape = -1;
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && phase != Phase.CONFIRM) TirageAnimations.INSTANCE.stopDoux(mc.player);
        CameraTirage.INSTANCE.liberer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void startTest() {
        Minecraft mc = Minecraft.getInstance();
        CameraTirage.INSTANCE.prendre(mc);
        if (forcedNatureName != null) nature = Nature.fromName(forcedNatureName);
        else if (!awaitingResult) nature = rollNature(clan);
        autres.remove(nature);
        reactionSoundPlayed = false;
        charge = 0f;
        prochainSonCharge = 1;
        if (mc.player != null) TirageAnimations.INSTANCE.playFeuille(mc.player);
        setPhase(Phase.ENTREE);
        playSound(SoundEvents.BOOK_PAGE_TURN, 1.25f, 0.55f);
    }

    /** Jauge pleine : on demande le tirage au serveur (ou on tire en démo). */
    private void canalisationComplete() {
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.5f);
        if (serverDriven && forcedNatureName != null) { commencerReaction(); return; }
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(TiragePayload.ID)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new TiragePayload("go"));
            awaitingResult = true;
            serverDriven = true;
            setPhase(Phase.ATTENTE);
            return;
        }
        commencerReaction();
    }

    private void commencerReaction() {
        if (nature == null) nature = rollNature(clan);
        autres.remove(nature);
        reactionSoundPlayed = false;
        bandeSon = 0;
        setPhase(Phase.REACTION);
    }

    private long dureeReaction() { return autres.size() * BANDE_DECALAGE_MS + REACTION_MS; }

    // ─────────── Tirage local (démo hors serveur), pondéré par clan ───────────
    static Nature rollNature(String clan) {
        Nature[] ordre = {Nature.KATON, Nature.SUITON, Nature.RAITON, Nature.FUTON, Nature.DOTON};
        double[] w = {20, 20, 20, 20, 20};
        int fav = clanFavored(clan);
        if (fav >= 0) {
            for (int i = 0; i < w.length; i++) w[i] -= 7.5;
            w[fav] += 30 + 7.5;
        }
        double total = 0;
        for (double v : w) total += Math.max(0, v);
        double x = new Random().nextDouble() * total, acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += Math.max(0, w[i]);
            if (x < acc) return ordre[i];
        }
        return Nature.KATON;
    }

    private static int clanFavored(String clan) {
        if (clan == null) return -1;
        String c = clan.toLowerCase();
        if (c.contains("uchiha") || c.contains("sarutobi")) return 0;
        if (c.contains("hozuki") || c.contains("hōzuki") || c.contains("senju")) return 1;
        if (c.contains("yotsuki") || c.contains("kaminari")) return 2;
        if (c.contains("kaguya") || c.contains("kazekage") || c.contains("sabaku")) return 3;
        if (c.contains("kamizuru") || c.contains("iwa")) return 4;
        return -1;
    }

    // ─────────── Boucle temporelle ───────────
    /** Touche de canalisation maintenue : Espace (état physique) ou clic gauche maintenu. */
    private boolean tenu() {
        if (debugTenir) return true;
        Minecraft mc = Minecraft.getInstance();
        boolean espace = false;
        try {
            espace = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_SPACE);
        } catch (RuntimeException ignored) { }
        return espace || sourisTenue;
    }

    /** Avance la jauge (appelé à chaque image : la jauge suit la main sans à-coups). */
    private void avancerCharge() {
        long t = now();
        long dt = dernierTick < 0 ? 0 : Math.min(100, t - dernierTick);
        dernierTick = t;
        if (phase != Phase.CANAL) return;
        if (tenu()) charge = Math.min(1f, charge + dt / (float) CHARGE_MS);
        else charge = Math.max(0f, charge - dt / (float) (CHARGE_MS * 2));
        if (charge >= prochainSonCharge * 0.2f && prochainSonCharge <= 4) {
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f + 0.18f * prochainSonCharge, 0.45f);
            prochainSonCharge++;
        }
        if (charge >= 1f) canalisationComplete();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (++ticks % 10 == 0) bras.actualiser();
        Vec3 main = CameraTirage.INSTANCE.main();
        switch (phase) {
            case ENTREE -> { if (ecoule() >= ENTREE_MS) setPhase(Phase.CANAL); }
            case CANAL -> { }
            case ATTENTE -> {
                if (!awaitingResult || ecoule() >= ATTENTE_MAX_MS) { awaitingResult = false; commencerReaction(); }
            }
            case REACTION -> {
                // chaque bande sonne quand elle commence à réagir (double nature : l'ancienne d'abord), puis son écho
                int n = autres.size() + 1;
                if (!reactionSoundPlayed && bandeSon < 2 * n) {
                    int b = bandeSon / 2;
                    long eb = ecoule() - b * BANDE_DECALAGE_MS - (bandeSon % 2 == 1 ? 600 : 0);
                    if (eb >= 0) {
                        Nature nb = b < autres.size() ? autres.get(b) : nature;
                        if (bandeSon % 2 == 0) playSound(nb.son, nb.hauteur, b == n - 1 ? 0.85f : 0.6f);
                        else playSound(nb.son2, nb.hauteur2, b == n - 1 ? 0.6f : 0.4f);
                        bandeSon++;
                    }
                    if (bandeSon >= 2 * n) reactionSoundPlayed = true;
                }
                long e = ecoule() - autres.size() * BANDE_DECALAGE_MS;
                if (e >= 0 && e < REACTION_MS) spawnReaction(mc, main);
                if (ecoule() >= dureeReaction() + TENUE_MS) {
                    setPhase(Phase.RESULT);
                    TirageAnimations.INSTANCE.stopDoux(p);
                    playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.6f);
                }
            }
            default -> { }
        }
    }

    // ─────────── Particules in-world (vanilla, sobres) : seulement à la réaction (la canalisation est portée par
    // l'écran : lueur et étincelles autour de la feuille ; des particules si près de la caméra encombraient le visage) ───
    private void spawnReaction(Minecraft mc, Vec3 f) {
        ParticleOptions[] set = nature.particles;
        if (rng.nextInt(3) != 0) return;
        ParticleOptions po = set[rng.nextInt(set.length)];
        double sx = (rng.nextDouble() - 0.5) * 0.16, sy = -rng.nextDouble() * 0.2, sz = (rng.nextDouble() - 0.5) * 0.16;
        double vx = nature.vx * (rng.nextDouble() - 0.5) * 2;
        double vy = nature.vy + (rng.nextDouble() - 0.3) * 0.02;
        double vz = nature.vz * (rng.nextDouble() - 0.5) * 2;
        mc.level.addParticle(po, f.x + sx, f.y + sy, f.z + sz, vx, vy, vz);
    }

    private void playSound(SoundEvent ev, float pitch, float vol) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && ev != null) mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, vol));
    }

    // ─────────── Rendu ───────────
    private static Identifier tex(String n) { return DaKit.tex("tirage", n); }

    private static final Identifier FEUILLE = tex("feuille"), HALO = tex("halo"), BOUTON = tex("bouton"),
        AURA = tex("aura"), PIECES = tex("pieces");
    private static final int CW = 128, CH = 160, ATLAS_W = 1024, ATLAS_H = 640, COLS = 8;
    private static final int NUIT = 0xFF0A0608;
    private static final int BW = 96, BH = 16;
    private static final int CHAKRA = 0x7CC4FF;

    private static float lisse(float x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    /** Apparition de l'écran (entrée) : 0 → 1. */
    private float apparition() {
        if (phase != Phase.ENTREE) return 1f;
        return lisse((ecoule() - ENTREE_MS * 0.35f) / (ENTREE_MS * 0.65f));
    }

    // mise en page (recalculée à chaque image, quelques additions) : bloc de droite = la feuille en gros plan (texel ×2)
    // à gauche, la liste des natures et la jauge / la révélation à droite. Le personnage occupe le tiers gauche (plan
    // de CameraTirage).
    private static final int Z = 2;                       // gros plan : 1 texel de la feuille = 2 GUI
    private static final int LARG = CW * Z + 12 + 176;    // largeur du bloc
    private static final int HAUT = 16 + CH * Z - 12;     // hauteur du bloc
    private int blocX() {
        int x = Math.round(this.width * 0.685f) - LARG / 2;
        return Math.max(8, Math.min(this.width - LARG - 8, x));
    }
    private int blocTop() { return Math.max(2, (this.height - HAUT) / 2); }
    private int feuilleX() { return blocX(); }
    private int feuilleY() { return blocTop() + 4; }
    /** Bloc de droite (liste, jauge, révélation), aligné sur le haut du papier (texel 44 de la case). */
    private int droiteX() { return blocX() + CW * Z + 12; }
    private int droiteY() { return feuilleY() + 44 * Z - 6; }
    private int basY() { return droiteY() + 12 + 5 * 28 + 6; }
    private int titreCx() { return blocX() + LARG / 2 + 40; }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float a = phase == Phase.CONFIRM ? 1f : apparition();
        if (phase == Phase.CONFIRM) {
            ctx.fill(0, 0, this.width, this.height, DaKit.alpha(NUIT, 0.55f));
            return;
        }
        // voile léger partout (calme le monde), voile dense et doux derrière le bloc
        ctx.fill(0, 0, this.width, this.height, DaKit.alpha(NUIT, 0.14f * a));
        int cx = blocX() + LARG / 2, cy = blocTop() + HAUT / 2;
        int rw = LARG * 2 + 60, rh = Math.max(HAUT * 2, this.height * 3 / 2);
        DaKit.etire(ctx, HALO, cx - rw / 2, cy - rh / 2, rw, rh, 64, 64, DaKit.alpha(NUIT, 0.94f * a));
        ctx.fillGradient(0, this.height - 50, this.width, this.height, 0x00000000, DaKit.alpha(NUIT, 0.30f * a));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        avancerCharge();
        Font f = this.font;
        if (phase == Phase.CONFIRM) { drawConfirm(ctx, f, this.width / 2, mouseX, mouseY); return; }
        float a = apparition();
        drawEntete(ctx, f, a);
        drawFeuille(ctx, a);
        drawListe(ctx, f, a);
        switch (phase) {
            case ENTREE, CANAL -> drawJauge(ctx, f, a);
            case ATTENTE -> legende(ctx, f, "La feuille boit ton chakra", a);
            case REACTION -> legende(ctx, f, "La feuille réagit", 1f);
            case RESULT -> drawResult(ctx, f, mouseX, mouseY);
            default -> { }
        }
    }

    private void drawEntete(GuiGraphicsExtractor ctx, Font f, float a) {
        int cx = titreCx(), y = blocTop() + 22;
        DaKit.arcX1(ctx, f, DaKit.arcade("Test de la Feuille"), cx, y, DaKit.alpha(DaKit.OR_CLAIR, a), 1, true);
        if (a > 0.5f) ReglagesArt.filet(ctx, cx - 80, cx + 80, y + 12);
        DaKit.inter7o(ctx, f, DaKit.inter("Canalise ton chakra : la feuille réagit selon ta nature."), cx, y + 22,
            DaKit.alpha(DaKit.DOUX, 0.9f * a), 1);
    }

    /** Lueur douce teintée (halo.png étiré : une texture lisse, un blit). */
    private void lueur(GuiGraphicsExtractor ctx, int cx, int cy, int r, int rgb, float a) {
        if (a <= 0.003f) return;
        DaKit.etire(ctx, HALO, cx - r / 2, cy - r / 2, r, r, 64, 64, DaKit.argb(rgb, Math.min(1f, a)));
    }

    /** Blit d'une région de case au gros plan (×Z, au plus proche). */
    private static void blitZ(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int u, int v, int w, int h, int tw,
                              int th, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w * Z, h * Z, w, h, tw, th, argb);
    }

    /** La feuille en gros plan : lueur, aura de chakra, case d'atlas (en bandes si plusieurs natures). */
    private void drawFeuille(GuiGraphicsExtractor ctx, float a) {
        int x = feuilleX(), y = feuilleY();
        int ccx = x + CW * Z / 2, ccy = y + 84 * Z;
        float glow = switch (phase) {
            case ENTREE -> 0f;
            case CANAL -> charge;
            case ATTENTE -> 1f;
            case REACTION -> Math.max(0f, 1f - ecoule() / 350f);
            default -> 0f;
        };
        float pouls = 0.5f + 0.5f * (float) Math.sin(now() / 160.0);
        lueur(ctx, ccx, ccy, 220 + (int) (110 * glow), CHAKRA, (0.07f + 0.20f * glow + 0.05f * glow * pouls) * a);
        // la feuille monte à l'entrée et tremble quand la jauge est presque pleine
        int dy = Math.round((1 - a) * 14);
        int tremble = glow > 0.7f && phase != Phase.REACTION ? ((int) ((now() / 50) % 3) - 1) * Z / 2 : 0;
        int fx = x + tremble, fy = y + dy;
        if (phase == Phase.REACTION || phase == Phase.RESULT) {
            if (phase == Phase.REACTION) {
                for (int i = 0; i <= autres.size(); i++) {
                    Nature n = i < autres.size() ? autres.get(i) : nature;
                    long e = ecoule() - i * BANDE_DECALAGE_MS;
                    if (e >= 0) lueur(ctx, ccx, ccy, 260, n.couleur & 0xFFFFFF, 0.30f * Math.max(0f, 1f - e / 1000f));
                }
            }
            feuilleBandes(ctx, fx, fy);
        } else {
            blitZ(ctx, FEUILLE, fx, fy, 0, 0, CW, CH, CW, CH, DaKit.blanc(a));
        }
        if (glow > 0.01f) {
            int img = (int) ((now() / 110) % 4);
            blitZ(ctx, AURA, fx, fy, img * CW, 0, CW, CH, CW * 4, CH,
                DaKit.blanc(Math.min(1f, (float) Math.pow(glow, 0.8) * a)));
            drawEtincelles(ctx, ccx, ccy, glow);
        }
        // le bout du bras pince la feuille par le haut, par-dessus (la feuille réagit sous le poing, le bras reste)
        bras.dessiner(ctx, fx, fy, Z, a, phase == Phase.REACTION ? 0f : glow, pouls);
    }

    /** Image de la case pour la bande i (−1 = feuille intacte). */
    private int imageBande(int i) {
        if (phase == Phase.RESULT) return FRAMES - 1;
        long e = ecoule() - i * BANDE_DECALAGE_MS;
        if (e < 0) return -1;
        return (int) Math.min(FRAMES - 1, e / FRAME_MS);
    }

    private void feuilleBandes(GuiGraphicsExtractor ctx, int x, int y) {
        int n = 1 + autres.size();
        for (int i = 0; i < n; i++) {
            Nature nat = i < autres.size() ? autres.get(i) : nature;
            // bandes sur la largeur de la feuille (34..94 de la case) ; les marges vont aux bandes des bords
            int x0 = i == 0 ? 0 : 34 + 60 * i / n, x1 = i == n - 1 ? CW : 34 + 60 * (i + 1) / n;
            int img = imageBande(i);
            if (img < 0) {
                blitZ(ctx, FEUILLE, x + x0 * Z, y, x0, 0, x1 - x0, CH, CW, CH, 0xFFFFFFFF);
            } else {
                int u = (img % COLS) * CW, v = (img / COLS) * CH;
                blitZ(ctx, nat.atlas, x + x0 * Z, y, u + x0, v, x1 - x0, CH, ATLAS_W, ATLAS_H, 0xFFFFFFFF);
            }
        }
    }

    /** Petites étincelles de chakra qui montent autour de la feuille (positions calculées, aucune allocation). */
    private void drawEtincelles(GuiGraphicsExtractor ctx, int cx, int cy, float glow) {
        int n = (int) (5 + 9 * glow);
        long t = now();
        for (int i = 0; i < n; i++) {
            long per = 1400 + (i * 397L) % 900;
            float u = ((t + i * 811L) % per) / (float) per;
            float ox = (float) Math.sin(i * 2.399) * (66 + (i % 3) * 10) + (float) Math.sin(u * 6 + i) * 4;
            float px = cx + ox, py = cy + 80 - u * 190;
            float al = (float) Math.sin(Math.PI * u) * glow;
            int s = i % 4 == 0 ? Z : Z / 2 + 0;
            int sx = Math.round(px / Z) * Z, sy = Math.round(py / Z) * Z;   // sur la grille du gros plan
            ctx.fill(sx, sy, sx + Math.max(1, s), sy + Math.max(1, s), DaKit.argb(i % 3 == 0 ? 0xE6F6FF : CHAKRA, al));
        }
    }

    /** Les cinq natures et leur effet ; à la révélation, la nature obtenue s'allume, les autres s'effacent. */
    private void drawListe(GuiGraphicsExtractor ctx, Font f, float a) {
        int x = droiteX(), y = droiteY();
        DaKit.inter7o(ctx, f, DaKit.inter("Ce que montre la feuille"), x, y, DaKit.alpha(DaKit.OR, 0.9f * a), 0);
        y += 12;
        boolean revele = phase == Phase.RESULT;
        float rv = revele ? lisse(ecoule() / 400f) : 0f;
        for (Nature n : Nature.ORDRE) {
            boolean elue = n == nature && revele;
            boolean deja = autres.contains(n) && (phase == Phase.REACTION || phase == Phase.RESULT);
            float al = a * (elue || deja ? 1f : 1f - 0.6f * rv);
            if (elue) {
                ctx.fill(x - 5, y - 3, x + 172, y + 24, DaKit.argb(n.couleur & 0xFFFFFF, 0.12f * rv));
                ctx.fill(x - 5, y - 3, x - 4, y + 24, DaKit.argb(n.couleur & 0xFFFFFF, 0.9f * rv));
            }
            NatureIcones.dessiner(ctx, n.icone, x, y + 1, 16, DaKit.blanc(al), now());
            int col = elue || deja ? 0xFF000000 | n.icone.couleur : DaKit.CREME;
            float w = DaKit.inter9o(ctx, f, DaKit.inter(n.icone.nom), x + 21, y + 5, DaKit.alpha(col, al), 0);
            DaKit.inter7o(ctx, f, DaKit.inter("· " + n.icone.element + (deja ? " · déjà éveillée" : "")), x + 21 + w + 3,
                y + 5, DaKit.alpha(DaKit.DOUX, 0.85f * al), 0);
            DaKit.inter7o(ctx, f, DaKit.inter(n.effet), x + 21, y + 15, DaKit.alpha(DaKit.CREME_VAL, 0.8f * al), 0);
            y += 28;
        }
    }

    /** « Maintenir [Espace] / pour canaliser ton chakra » + jauge. */
    private void drawJauge(GuiGraphicsExtractor ctx, Font f, float a) {
        int x0 = droiteX(), y = basY();
        Component t1 = DaKit.inter("Maintenir"), t2 = DaKit.inter("pour canaliser ton chakra");
        Component k = DaKit.arcade("Espace");
        float w1 = DaKit.largeur8(f, t1);
        int kw = Math.round(DaKit.largeurFin(f, k)) + 10;
        boolean tient = tenu() && phase == Phase.CANAL;
        DaKit.inter8(ctx, f, t1, x0, y + 6, DaKit.alpha(DaKit.CREME, a), 0);
        int kx = Math.round(x0 + w1 + 4);
        cabochon(ctx, kx, y, kw, tient, a);
        DaKit.arcFin(ctx, f, k, kx + kw / 2f, y + 3.5f, DaKit.alpha(tient ? DaKit.OR_CLAIR : DaKit.CREME, a), 1, false);
        DaKit.inter8(ctx, f, t2, kx + kw + 4, y + 6, DaKit.alpha(DaKit.CREME, a), 0);
        // jauge 160×10
        int jx = x0, jy = y + 20;
        DaKit.blit(ctx, PIECES, jx, jy, 0, 16, 160, 10, 192, 48, DaKit.blanc(a));
        int fw = Math.round(158 * charge);
        if (fw > 0) DaKit.blit(ctx, PIECES, jx + 1, jy + 2, 0, 28, fw, 6, 192, 48, DaKit.blanc(a));
        if (fw > 1 && charge < 1f) ctx.fill(jx + fw, jy + 1, jx + fw + 1, jy + 9, DaKit.argb(0xFFFFFF, 0.85f * a));
        DaKit.inter7o(ctx, f, DaKit.inter("ou clic maintenu  ·  Échap : renoncer"), x0, jy + 18,
            DaKit.alpha(DaKit.DOUX, 0.6f * a), 0);
    }

    /** Cabochon de touche (laque sombre, liseré or) étiré à la largeur voulue, en 3 morceaux. */
    private void cabochon(GuiGraphicsExtractor ctx, int x, int y, int w, boolean actif, float a) {
        int col = DaKit.blanc(a);
        DaKit.blit(ctx, PIECES, x, y, 0, 0, 6, 14, 192, 48, col);
        ctx.blit(RenderPipelines.GUI_TEXTURED, PIECES, x + 6, y, 6f, 0f, w - 12, 14, 12, 14, 192, 48, col);
        DaKit.blit(ctx, PIECES, x + w - 6, y, 18, 0, 6, 14, 192, 48, col);
        if (actif) ctx.fill(x + 1, y + 1, x + w - 1, y + 12, DaKit.argb(CHAKRA, 0.18f * a));
    }

    private void legende(GuiGraphicsExtractor ctx, Font f, String t, float a) {
        int d = (int) ((now() / 350) % 4);
        DaKit.inter8(ctx, f, DaKit.inter(t + "...".substring(0, d)), droiteX(), basY() + 6, DaKit.alpha(DaKit.DOUX, a), 0);
    }

    // ── révélation (bloc de droite, sous la liste) ──
    private static final int ICONE = 32;

    private int btnY() { return basY() + ICONE + 6 + 26 + 8; }

    private void drawResult(GuiGraphicsExtractor ctx, Font f, int mouseX, int mouseY) {
        Nature n = nature;
        int x = droiteX(), y = basY() - 2;
        float a1 = lisse((ecoule() - 100) / 350f), a2 = lisse((ecoule() - 260) / 350f), a3 = lisse((ecoule() - 420) / 350f);
        lueur(ctx, x + ICONE / 2, y + ICONE / 2, 90, n.couleur & 0xFFFFFF, 0.30f * a1);
        if (a1 > 0.01f) NatureIcones.dessiner(ctx, n.icone, x, y + Math.round((1 - a1) * 4), ICONE, DaKit.blanc(a1), now());
        if (a2 > 0.01f) {
            Component nom = DaKit.arcade(n.icone.nom);
            float tx = x + ICONE + 8, ty = y + 3;
            DaKit.texteEchelle(ctx, f, nom, tx + 1, ty + 1, DaKit.alpha(0xFF0A0406, a2), 2f);
            DaKit.texteEchelle(ctx, f, nom, tx, ty, DaKit.alpha(DaKit.CREME, a2), 2f);
            DaKit.inter8(ctx, f, DaKit.inter(n.icone.element + " — nature éveillée"), tx, ty + 22,
                DaKit.alpha(0xFF000000 | n.icone.couleur, a2), 0);
        }
        y += ICONE + 6;
        if (a3 > 0.01f) {
            int c = n.phrase.indexOf(':');
            String l1 = c > 0 ? n.phrase.substring(0, c + 1) : n.phrase, l2 = c > 0 ? n.phrase.substring(c + 1).trim() : "";
            DaKit.inter8(ctx, f, DaKit.inter(l1), x, y + 4, DaKit.alpha(DaKit.CREME_VAL, a3), 0);
            DaKit.inter8(ctx, f, DaKit.inter(l2), x, y + 14, DaKit.alpha(DaKit.CREME_VAL, a3), 0);
            if (!autres.isEmpty()) {
                StringBuilder sb = new StringBuilder("Ton chakra porte aussi : ");
                for (int i = 0; i < autres.size(); i++) sb.append(i == 0 ? "" : ", ").append(autres.get(i).icone.nom);
                DaKit.inter7o(ctx, f, DaKit.inter(sb.toString()), x, y + 24, DaKit.alpha(DaKit.DOUX, a3), 0);
            }
            int by = btnY() + (autres.isEmpty() ? 0 : 6), bx = x;
            boolean hover = DaKit.dans(mouseX, mouseY, bx, by, BW, BH);
            plaque(ctx, f, bx, by, "Valider", hover);
            DaKit.inter7o(ctx, f, DaKit.inter(serverDriven ? "Entrée : valider" : "Entrée : valider  ·  F : refaire"),
                bx + BW + 10, by + BH / 2f, DaKit.alpha(DaKit.DOUX, 0.7f * a3), 0);
        }
    }

    // ── confirmation (popup centré, la caméra n'a pas encore bougé) ──
    private int confirmTop() { return Math.max(8, this.height / 2 - 136); }
    private int boutonY() { return confirmTop() + CH + 52; }
    private int confirmerX() { return this.width / 2 - BW - 6; }
    private int refuserX() { return this.width / 2 + 18; }
    private int refuserW() { return this.font.width(DaKit.inter("Refuser")) + 8; }

    private void drawConfirm(GuiGraphicsExtractor ctx, Font f, int cx, int mouseX, int mouseY) {
        int top = confirmTop();
        lueur(ctx, cx, top + 84, 170, CHAKRA, 0.14f + 0.05f * (float) Math.sin(now() / 420.0));
        int bob = (int) Math.round(Math.sin(now() / 600.0));
        DaKit.blit(ctx, FEUILLE, cx - CW / 2, top + bob, 0, 0, CW, CH, CW, CH);
        bras.dessiner(ctx, cx - CW / 2, top + bob, 1, 1f, 0f, 0f);
        int y = top + CH + 2;
        DaKit.arcX1(ctx, f, DaKit.arcade("Test de la Feuille"), cx, y, DaKit.OR_CLAIR, 1, true);
        ReglagesArt.filet(ctx, cx - 70, cx + 70, y + 12);
        DaKit.inter9o(ctx, f, DaKit.inter("Canalise ton chakra dans la feuille pour révéler ta nature."), cx, y + 26,
            DaKit.CREME_VAL, 1);
        int by = boutonY();
        boolean h1 = DaKit.dans(mouseX, mouseY, confirmerX(), by, BW, BH);
        boolean h2 = DaKit.dans(mouseX, mouseY, refuserX() - 4, by, refuserW(), BH);
        plaque(ctx, f, confirmerX(), by, "Confirmer", h1);
        int rw = DaKit.inter9o(ctx, f, DaKit.inter("Refuser"), refuserX(), by + BH / 2f, h2 ? DaKit.CREME : DaKit.DOUX, 0);
        if (h2) ctx.fill(refuserX(), by + BH / 2 + 6, refuserX() + rw, by + BH / 2 + 7, DaKit.OR);
        DaKit.inter7o(ctx, f, DaKit.inter("Entrée : confirmer  ·  Échap : refuser"), cx, by + BH + 12,
            DaKit.alpha(DaKit.DOUX, 0.75f), 1);
    }

    /** Plaque laquée fine (repos / survol) + libellé centré. */
    private void plaque(GuiGraphicsExtractor ctx, Font f, int x, int y, String txt, boolean survol) {
        DaKit.blit(ctx, BOUTON, x - 1, y - 1, 0, survol ? 20 : 0, BW + 4, 20, 100, 40);
        DaKit.inter9(ctx, f, DaKit.inter(txt), x + BW / 2f, y + BH / 2f, survol ? DaKit.OR_CLAIR : DaKit.CREME, 1);
    }

    // ─────────── Interactions ───────────
    private void confirmAccept() {
        RebornSounds.confirm();
        confirmMode = false;
        startTest();
    }

    private void annuler() {
        RebornSounds.uiClick();
        onClose();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (phase == Phase.CONFIRM) {
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { confirmAccept(); return true; }
            if (k == GLFW.GLFW_KEY_ESCAPE) { annuler(); return true; }
            return true;
        }
        if (phase == Phase.ENTREE || phase == Phase.CANAL) {
            // rien n'est encore envoyé au serveur : renoncer ne consomme pas la feuille
            if (k == GLFW.GLFW_KEY_ESCAPE) { annuler(); return true; }
            return true;
        }
        if (phase == Phase.RESULT) {
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER || k == GLFW.GLFW_KEY_ESCAPE) {
                RebornSounds.confirm();
                onClose();
                return true;
            }
            if (k == GLFW.GLFW_KEY_F && !serverDriven) { RebornSounds.uiClick(); nature = null; startTest(); return true; }
        }
        return true;   // la réaction ne s'interrompt pas
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            double mx = e.x(), my = e.y();
            if (phase == Phase.CONFIRM) {
                if (DaKit.dans(mx, my, confirmerX(), boutonY(), BW, BH)) { confirmAccept(); return true; }
                if (DaKit.dans(mx, my, refuserX() - 4, boutonY(), refuserW(), BH)) { annuler(); return true; }
                return true;
            }
            if (phase == Phase.CANAL || phase == Phase.ENTREE) { sourisTenue = true; return true; }
            if (phase == Phase.RESULT && ecoule() > 420) {
                if (DaKit.dans(mx, my, droiteX(), btnY() + (autres.isEmpty() ? 0 : 6), BW, BH)) {
                    RebornSounds.confirm();
                    onClose();
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        if (e.button() == 0) sourisTenue = false;
        return true;
    }

    // ─────────── Natures ───────────
    enum Nature {
        KATON(NatureIcones.Nature.KATON, "La feuille s'est consumée : ton chakra brûle comme une flamme.",
            "elle s'enflamme et se consume",
            SoundEvents.FIRECHARGE_USE, 1.0f, SoundEvents.FIRE_AMBIENT, 1.0f,
            new ParticleOptions[]{ParticleTypes.SMOKE, ParticleTypes.ASH},
            0.01, 0.03, 0.01),
        SUITON(NatureIcones.Nature.SUITON, "La feuille s'est détrempée : ton chakra coule comme l'eau.",
            "elle se gorge d'eau et goutte",
            SoundEvents.BUCKET_FILL, 1.3f, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 1.0f,
            new ParticleOptions[]{ParticleTypes.DRIPPING_WATER, ParticleTypes.FALLING_WATER},
            0.0, -0.02, 0.0),
        RAITON(NatureIcones.Nature.RAITON, "La feuille s'est froissée : ton chakra crépite comme la foudre.",
            "elle se froisse en zigzag",
            SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.4f, SoundEvents.BOOK_PAGE_TURN, 0.6f,
            new ParticleOptions[]{ParticleTypes.ELECTRIC_SPARK},
            0.06, 0.02, 0.06),
        FUTON(NatureIcones.Nature.FUTON, "La feuille s'est fendue en deux : ton chakra tranche comme le vent.",
            "elle se coupe net en deux",
            SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.3f,
            new ParticleOptions[]{ParticleTypes.WHITE_ASH},
            0.05, 0.0, 0.05),
        DOTON(NatureIcones.Nature.DOTON, "La feuille s'est effritée : ton chakra est solide comme la terre.",
            "elle se dessèche et s'effrite",
            SoundEvents.ROOTED_DIRT_BREAK, 0.9f, SoundEvents.SAND_FALL, 0.8f,
            new ParticleOptions[]{ParticleTypes.ASH, ParticleTypes.WHITE_ASH},
            0.02, -0.03, 0.02);

        /** Ordre d'affichage de la liste (comme la référence : feu, eau, terre, foudre, vent). */
        static final Nature[] ORDRE = {KATON, SUITON, DOTON, RAITON, FUTON};

        final NatureIcones.Nature icone;
        final String phrase, effet;
        final SoundEvent son, son2;
        final float hauteur, hauteur2;
        final ParticleOptions[] particles;
        final double vx, vy, vz;
        final Identifier atlas;
        final int couleur;

        Nature(NatureIcones.Nature icone, String phrase, String effet, SoundEvent son, float hauteur, SoundEvent son2,
               float hauteur2, ParticleOptions[] particles, double vx, double vy, double vz) {
            this.icone = icone;
            this.phrase = phrase;
            this.effet = effet;
            this.son = son;
            this.hauteur = hauteur;
            this.son2 = son2;
            this.hauteur2 = hauteur2;
            this.particles = particles;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.atlas = tex("feuille_" + icone.slug);
            this.couleur = icone.couleur;
        }

        static Nature parse(String s) {
            NatureIcones.Nature n = NatureIcones.parse(s);
            return n == null ? null : valueOf(n.name());
        }

        /** KATON / SUITON / RAITON / FUTON / DOTON (casse et accents indifférents), Katon par défaut. */
        static Nature fromName(String s) {
            Nature n = parse(s);
            return n == null ? KATON : n;
        }
    }
}
