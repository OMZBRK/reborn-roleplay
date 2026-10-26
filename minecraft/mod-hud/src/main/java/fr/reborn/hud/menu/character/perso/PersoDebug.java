package fr.reborn.hud.menu.character.perso;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.character.CharacterData;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Banc de captures de la sélection et de la création, <b>inactif en production</b> ({@code REBORN_PERSO_DEBUG=1},
 * monde solo de dev). Injecte le roster de la maquette (Kazuki Uchiha, Aya Hyūga, Renji mort + une place libre), puis
 * capture la sélection et les huit étapes de la création dans l'état des maquettes (Konoha, Hyūga, Homme, « Ren
 * Hyūga », 16 ans, 172 cm), la variante Suna, les échelles 3 et 4 et une fenêtre 1366×768. Captures dans
 * run/screenshots/perso_*.png, puis le jeu se ferme. {@code REBORN_PERSO_DEBUG=vite} : 1920×1080 seulement.
 */
public final class PersoDebug {

    private PersoDebug() {}

    private record Shot(String nom, int etape, int village, int echelle, int fw, int fh) {}

    private static final int STEP = 44, STEP_PERF = 160;
    private static final List<Shot> SHOTS = new ArrayList<>();
    /** Banc « clans » : clan choisi (index dans les clans du village) par capture. */
    private static final java.util.Map<String, Integer> CLAN = new java.util.HashMap<>();
    private static int t = -1;
    private static boolean perf;
    private static final StringBuilder RAPPORT = new StringBuilder();
    private static long somFps, nbFps;

    public static void init() {
        String v = System.getenv("REBORN_PERSO_DEBUG");
        if (v == null || v.isBlank()) return;
        String dirLive = System.getenv("REBORN_CREATOR_ASSETS");
        if (dirLive != null && !dirLive.isBlank()) liveDir = java.nio.file.Path.of(dirLive.trim());
        if ("coupes".equalsIgnoreCase(v.trim())) {
            // Coiffures (bundle + assets live du serveur) et sourcils, 18/10 : 1920×1080 sauf mention.
            SHOTS.add(new Shot("perso_10a_toutes_mod_seul", 116, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_10a2_live_arrive_ecran_ouvert", 126, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_10b_apparence_live", 6, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_10c_toutes_haut", 86, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_10d_toutes_bas", 96, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_10e_coupe_live_portee", 106, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_10f_toutes_1366", 86, 0, 2, 1366, 768));
            SHOTS.add(new Shot("perso_11a_sourcils_comme", 36, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_11b_sourcils_noirs", 46, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_11c_sourcils_bruns", 56, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_11d_sourcils_roux", 66, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_11e_sourcils_libre", 76, 0, 2, 1920, 1080));
            ClientTickEvents.END_CLIENT_TICK.register(PersoDebug::tick);
            return;
        }
        if ("clans".equalsIgnoreCase(v.trim())) {
            // Blasons dessinés et sceau du registre, 18/10 : grilles de clans, puis récapitulatif par village,
            // variante 認 (étape 207) et marque du clan (307). 1920×1080.
            String[][] grilles = {{"konoha", "0", "10"}, {"suna", "1", "1"}, {"kiri", "2", "1"}, {"iwa", "4", "1"}};
            for (String[] gr : grilles) {
                SHOTS.add(new Shot("perso_12_clans_" + gr[0], 1, Integer.parseInt(gr[1]), 2, 1920, 1080));
                CLAN.put("perso_12_clans_" + gr[0], Integer.parseInt(gr[2]));
            }
            String[][] recaps = {{"konoha", "0", "2"}, {"suna", "1", "0"}, {"kiri", "2", "1"}, {"kumo", "3", "0"},
                {"iwa", "4", "1"}, {"ame", "5", "0"}};
            for (String[] rc : recaps) {
                SHOTS.add(new Shot("perso_13_sceau_" + rc[0], 7, Integer.parseInt(rc[1]), 2, 1920, 1080));
                CLAN.put("perso_13_sceau_" + rc[0], Integer.parseInt(rc[2]));
            }
            SHOTS.add(new Shot("perso_13_sceau_variante_kanji", 207, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_13_sceau_variante_clan_suna", 307, 1, 2, 1920, 1080));
            CLAN.put("perso_13_sceau_variante_clan_suna", 0);
            SHOTS.add(new Shot("perso_13_sceau_1366_kiri", 7, 2, 2, 1366, 768));
            CLAN.put("perso_13_sceau_1366_kiri", 1);
            ClientTickEvents.END_CLIENT_TICK.register(PersoDebug::tick);
            return;
        }
        if ("candidature".equalsIgnoreCase(v.trim())) {
            // Lot J (maquette J9, 25/10) : premier personnage issu de la candidature (Kaito Arashi, Konoha, 16 ans) —
            // ouverture directe à l'étape Taille, village et âge sous cadenas, « Sans clan » + nom de famille, refus du
            // serveur ; puis un 2e personnage (clans de session sous cadenas). 1920×1080.
            String[] et = {"taille_ouverture", "village", "clan", "nom", "age", "refus_serveur", "recap"};
            for (int i = 0; i < et.length; i++) SHOTS.add(new Shot("perso_14" + (char) ('a' + i) + "_candidature_" + et[i],
                400 + i, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_14h_deuxieme_perso_clan", 407, 0, 2, 1920, 1080));
            ClientTickEvents.END_CLIENT_TICK.register(PersoDebug::tick);
            return;
        }
        boolean vite = "vite".equalsIgnoreCase(v.trim());
        perf = "perf".equalsIgnoreCase(v.trim());
        if (perf) {
            // Banc de performance : monde seul, sélection, puis chaque étape de la création, 8 s chacune.
            PersoPerf.actif = true;
            SHOTS.add(new Shot("monde", -9, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("ecran_vide_opaque", -8, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("selection", -1, 0, 2, 1920, 1080));
            String[] et = {"village", "clan", "sexe", "nom", "age", "taille", "apparence", "recap"};
            for (int i = 0; i < 8; i++) SHOTS.add(new Shot("creation_" + et[i], i, 0, 2, 1920, 1080));
            SHOTS.add(new Shot("monde_fin", -9, 0, 2, 1920, 1080));
            ClientTickEvents.END_CLIENT_TICK.register(PersoDebug::tick);
            return;
        }
        String[] noms = {"02_creation_village", "03_creation_clan", "04_creation_sexe", "05_creation_nom",
            "06_creation_age", "07_creation_taille", "08_creation_apparence", "09_creation_recapitulatif"};
        SHOTS.add(new Shot("perso_01_selection", -1, 0, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_01b_selection_aya", -2, 0, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_00_intro", -3, 0, 2, 1920, 1080));
        for (int i = 0; i < 8; i++) SHOTS.add(new Shot("perso_" + noms[i], i, 0, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_08b_creation_couleur_libre", 16, 0, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_08c_creation_accessoire", 26, 0, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_09b_recap_accessoire", 27, 0, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_01c_selection_glisse", -4, 0, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_02b_creation_village_suna", 0, 1, 2, 1920, 1080));
        SHOTS.add(new Shot("perso_03c_clan_suna", 1, 1, 2, 1920, 1080));
        for (String vv : new String[]{"kiri", "kumo", "iwa", "ame"}) {
            int vi = java.util.Arrays.asList(PersoArt.VILLAGES).indexOf(vv);
            SHOTS.add(new Shot("perso_02c_village_" + vv, 0, vi, 2, 1920, 1080));
            SHOTS.add(new Shot("perso_03c_clan_" + vv, 1, vi, 2, 1920, 1080));
        }
        if (!vite) {
            for (int s : new int[]{3, 4}) {
                SHOTS.add(new Shot("perso_s" + s + "_01_selection", -1, 0, s, 1920, 1080));
                SHOTS.add(new Shot("perso_s" + s + "_08_apparence", 6, 0, s, 1920, 1080));
            }
            SHOTS.add(new Shot("perso_1366_01_selection", -1, 0, 2, 1366, 768));
            SHOTS.add(new Shot("perso_1366_03_clan", 1, 0, 2, 1366, 768));
            SHOTS.add(new Shot("perso_1366_08_apparence", 26, 0, 2, 1366, 768));
            SHOTS.add(new Shot("perso_1366_09_recap", 7, 0, 2, 1366, 768));
            SHOTS.add(new Shot("perso_1706_03_clan", 1, 0, 2, 1706, 960));
            SHOTS.add(new Shot("perso_1706_08_apparence", 26, 0, 2, 1706, 960));
        }
        ClientTickEvents.END_CLIENT_TICK.register(PersoDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 20) {
            mc.player.connection.sendCommand("time set 13000");
            mc.player.connection.sendCommand("weather clear");
            mc.gui.hud.getChat().clearMessages(false);
            roster();
        }
        if (t < 50) return;
        int pas = perf ? STEP_PERF : STEP;
        int i = (t - 50) / pas, k = (t - 50) % pas;
        if (i >= SHOTS.size()) {
            if (k == 2) mc.setScreenAndShow(null);
            if (k == 4 && perf) ecrireRapport(mc);
            if (k == 6) mc.stop();
            return;
        }
        Shot s = SHOTS.get(i);
        if (liveDir != null && !liveInjecte && (s.etape() == 126 ? k == 20 : k == 1 && s.etape() != 116)) injecterLive(liveDir);
        if (perf) {
            mc.getFramerateLimitTracker().onInputReceived();
            if (k == 0) {
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(260);
                mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            }
            if (k == 50) { PersoPerf.reset(); somFps = nbFps = 0; }
            if (k > 50 && k % 10 == 0) { somFps += mc.getFps(); nbFps++; }
            if (k == pas - 2) {
                String l = String.format(java.util.Locale.ROOT, "%-20s getFps=%d %s", s.nom(),
                    nbFps == 0 ? 0 : somFps / nbFps, s.etape() == -9 ? "" : PersoPerf.bilan());
                RAPPORT.append(l).append("\n");
                org.slf4j.LoggerFactory.getLogger("reborn-perso-perf").info(l);
            }
            if (s.etape() == -9) {
                if (k == 0) mc.setScreenAndShow(null);
                return;
            }
        }
        if (k == 0) {
            mc.setScreenAndShow(null);
            if (mc.getWindow().getScreenWidth() != s.fw() || mc.getWindow().getScreenHeight() != s.fh()) {
                mc.getWindow().setWindowed(s.fw(), s.fh());
            }
            mc.options.guiScale().set(s.echelle());
            mc.resizeGui();
        }
        if (k == 3 && s.etape() == -8) {
            mc.setScreenAndShow(new Vide());
            return;
        }
        if (k == 3 && s.etape() >= 400) {
            int n = s.etape() - 400;
            rosterCandidature(n < 7);
            PersoSons.voixCoupee = true;
            PersoCreationScreen sc = new PersoCreationScreen();
            mc.setScreenAndShow(sc);
            sc.debugOuvrir();
            int[] etapes = {-1, 0, 1, 3, 4, 4, 7, 1};
            if (etapes[n] >= 0) sc.debugEtape(etapes[n]);
            if (n == 5) sc.refusServeur("age", "Ta candidature fixe l'âge de ton personnage à 16 ans.");
            PersoSons.voixCoupee = !PersoSons.VOIX;
        } else if (k == 3) {
            if (s.etape() < 0 && s.etape() > -3 || s.etape() == -4) {
                PersoSelectionScreen sc = PersoSelectionScreen.fromRoster(mc);
                mc.setScreenAndShow(sc);
                if (s.etape() == -2) sc.setFocus(1);
            } else {
                PersoCreationScreen sc = new PersoCreationScreen();
                mc.setScreenAndShow(sc);
                if (s.etape() >= 0) {
                    PersoSons.voixCoupee = true;
                    sc.debugEtat(s.village(), s.village() == 0 ? 2 : 0, 0, "Ren Hyūga", 16, 172, 4, 0, 1, 2, 0);
                    Integer ci = CLAN.get(s.nom());
                    if (ci != null) sc.debugEtat(-1, ci, 0, "Ren", 16, 172, 4, 0, 1, 2, 0);
                    PersoEmblemes.sceauMode = s.etape() / 100 == 2 ? "kanji" : s.etape() / 100 == 3 ? "clan" : "village";
                    sc.debugEtape(s.etape() % 10);
                    if (s.etape() == 16) sc.debugCouleurLibre(0x2E8A9A);
                    if (s.etape() / 10 == 2) sc.debugAccessoire();
                    int var = s.etape() / 10;
                    if (var >= 3 && var <= 7) {                 // sourcils : Konoha « Autre » (palette libre), blond
                        sc.debugEtat(0, 11, 0, "Ren", 16, 172, 3, 0, 4, 0, 13);
                        sc.debugEtape(6);
                        sc.debugCoiffure("Cheveux_Court");
                        sc.debugSourcils(var == 3 ? -1 : var == 4 ? 0 : var == 5 ? 2 : var == 6 ? 6 : -2, 0xD9D9D9);
                    }
                    if (var == 8 || var == 9 || var == 11) sc.debugToutes(var == 9);
                    if (var == 10) sc.debugCoiffureLive();
                    PersoSons.voixCoupee = !PersoSons.VOIX;
                }
            }
        }
        if (k == pas - 8 && s.etape() == -4 && mc.gui.screen() instanceof PersoSelectionScreen sel) sel.setFocus(1);
        if (k == pas - 3 && !perf) Screenshot.grab(mc.gameDirectory, s.nom() + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    /** Écran opaque vide (plancher de coût d'une image sans monde, banc de perf). */
    private static final class Vide extends net.minecraft.client.gui.screens.Screen implements fr.reborn.hud.ui.EcranOpaque {
        Vide() { super(net.minecraft.network.chat.Component.literal("vide")); }

        @Override
        public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float dt) { }

        @Override
        public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float dt) {
            PersoPerf.debut();
            g.fill(0, 0, width, height, 0xFF000000);
            PersoPerf.fin(g);
        }
    }

    private static void ecrireRapport(Minecraft mc) {
        try {
            java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("perso_perf.txt"), RAPPORT.toString());
        } catch (java.io.IOException ignored) { }
    }

    /* ------------------------------------------------------------- assets live du serveur (banc) */

    private static java.nio.file.Path liveDir;
    private static boolean liveInjecte;

    /**
     * Rejoue la diffusion {@code reborn:creatorpack} de ShinobiCore ({@code CreatorAssetManager.reload/sendPack}) à partir
     * d'une copie locale de {@code plugins/ShinobiCore/creator-assets/} : même corps (UTF dossier, UTF id, UTF méta, int
     * + PNG, int + masque), mêmes morceaux de 24 000 octets, même point d'entrée client ({@code CreatorAssets.registerChunk}).
     */
    private static void injecterLive(java.nio.file.Path dir) {
        liveInjecte = true;
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("reborn-perso-banc");
        Minecraft mc = Minecraft.getInstance();
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                java.nio.file.Files.readString(dir.resolve("catalog.json"))).getAsJsonObject();
            // skin d'un joueur composé AVANT l'arrivée du pack (cas du join) avec une coiffure live : doit être refait
            java.util.UUID u = mc.player.getUUID();
            String premier = root.getAsJsonArray("hair").get(0).getAsJsonObject().get("id").getAsString();
            SkinSpec sp = new SkinSpec();
            sp.hairId = premier;
            sp.hairColor = 0xFFC23B3B;
            fr.reborn.hud.skin.RebornSkins.applySpec(u, sp);
            log.info("[perso-banc] skin {} composé avant le pack : sommet du crâne = {}", premier, pixelCrane(u));
            int n = 0, sautes = 0;
            for (String cat : new String[]{"hair", "outfit", "eyes", "facial", "tattoo", "accessory", "underwear"}) {
                if (!root.has(cat) || !root.get(cat).isJsonArray()) continue;
                for (com.google.gson.JsonElement el : root.getAsJsonArray(cat)) {
                    if (!el.isJsonObject()) continue;
                    com.google.gson.JsonObject o = el.getAsJsonObject();
                    String id = o.has("id") ? o.get("id").getAsString() : "";
                    java.nio.file.Path png = dir.resolve(cat).resolve(id + ".png");
                    if (id.isBlank() || !java.nio.file.Files.isRegularFile(png)) { sautes++; continue; }
                    java.nio.file.Path mask = dir.resolve(cat).resolve(id + "_Mask.png");
                    byte[] pb = java.nio.file.Files.readAllBytes(png);
                    byte[] mb = java.nio.file.Files.isRegularFile(mask) ? java.nio.file.Files.readAllBytes(mask) : new byte[0];
                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                    try (java.io.DataOutputStream out = new java.io.DataOutputStream(b)) {
                        out.writeUTF(cat);
                        out.writeUTF(id);
                        out.writeUTF(o.toString());
                        out.writeInt(pb.length);
                        out.write(pb);
                        out.writeInt(mb.length);
                        if (mb.length > 0) out.write(mb);
                    }
                    byte[] corps = b.toByteArray();
                    int total = Math.max(1, (corps.length + 23999) / 24000);
                    for (int idx = 0; idx < total; idx++) {
                        int deb = idx * 24000, len = Math.min(24000, corps.length - deb);
                        fr.reborn.hud.skin.CreatorAssets.INSTANCE.registerChunk(cat + "/" + id, idx, total,
                            java.util.Arrays.copyOfRange(corps, deb, deb + len));
                    }
                    n++;
                }
            }
            log.info("[perso-banc] assets live rejoués : {} (sans PNG : {}) ; coiffures au catalogue : {} dont {} live ;"
                    + " proposées Homme {} / Femme {}",
                n, sautes, fr.reborn.hud.skin.CharacterCatalog.all("hair").size(),
                fr.reborn.hud.skin.CharacterCatalog.liveCount("hair"),
                fr.reborn.hud.skin.CharacterCatalog.available("hair", false, "", false).size(),
                fr.reborn.hud.skin.CharacterCatalog.available("hair", true, "", false).size());
            log.info("[perso-banc] après le pack : sommet du crâne = {}", pixelCrane(u));
            fr.reborn.hud.skin.RebornSkins.clear(u);
        } catch (Exception e) {
            log.warn("[perso-banc] assets live illisibles ({}) : {}", dir, e.toString());
        }
    }

    /** Pixel (12, 4) du skin composé du joueur (dessus du crâne : peau sans coiffure, cheveux sinon). */
    private static String pixelCrane(java.util.UUID u) {
        try {
            var t = Minecraft.getInstance().getTextureManager().getTexture(
                fr.reborn.hud.skin.RebornSkins.overrideFor(u));
            if (t instanceof net.minecraft.client.renderer.texture.DynamicTexture dt && dt.getPixels() != null) {
                return String.format("%08X", dt.getPixels().getPixel(12, 4));
            }
        } catch (RuntimeException ignored) { }
        return "?";
    }

    private static String look(boolean female, int skin, String hair, int hairColor, int eye, String outfit) {
        SkinSpec l = new SkinSpec();
        l.female = female;
        l.skinColor = SkinSpec.skinRamp(skin / 11f);
        l.hairId = hair;
        l.hairColor = hairColor;
        l.eyeColor = l.eyeColorRight = eye;
        l.outfitId = outfit;
        return l.serialize();
    }

    /** Roster de la maquette, au format exact du serveur (ShinobiCore CharacterSelectManager). */
    private static void roster() {
        JsonObject root = new JsonObject();
        root.addProperty("slotLimit", 4);
        JsonArray arr = new JsonArray();
        arr.add(card("c1", "Kazuki", "Uchiha", 0xFFB1302B, "Konohagakure", "Chūnin", false, 19, 176 / 180.0, "Homme",
            look(false, 3, "Cheveux_Sasuke", 0xFF1A1414, 0xFF1E1A16, "Complet_Uchiha")));
        arr.add(card("c2", "Aya", "Hyuga", 0xFFCFC6E0, "Konohagakure", "Genin", false, 13, 0.98, "Femme",
            look(true, 2, "Cheveux_Femme", 0xFF2A2440, 0xFFE4DDF0, "Complet_Femme1")));
        arr.add(card("c3", "Renji", "Sabaku", 0xFFC97B3C, "Sunagakure", "Tokubetsu Jōnin", true, 24, 1.10, "Homme",
            look(false, 6, "Cheveux_4", 0xFFC23B3B, 0xFF4E8A4A, "Complet_Taijutsuka")));
        root.add("characters", arr);
        CharacterData.update(root.toString());
    }

    /**
     * Roster du banc « candidature », au format de ShinobiCore (lot J) : {@code premier} = aucun personnage et bloc
     * candidature complet ; sinon un personnage déjà créé et le seul drapeau staff (2e personnage libre, sans clan).
     */
    private static void rosterCandidature(boolean premier) {
        JsonObject root = new JsonObject();
        root.addProperty("slotLimit", 2);
        JsonArray arr = new JsonArray();
        if (!premier) {
            arr.add(card("c1", "Kaito", "Arashi", 0xFFFFFFFF, "Konohagakure", "Genin", false, 16, 172 / 180.0, "Homme",
                look(false, 4, "Cheveux_Sasuke", 0xFF1A1414, 0xFF1E1A16, "Complet_Uchiha")));
        }
        root.add("characters", arr);
        JsonObject c = new JsonObject();
        c.addProperty("staff", false);
        c.addProperty("premier", premier);
        if (premier) {
            c.addProperty("village", "Konohagakure");
            c.addProperty("age", 16);
            c.addProperty("clan", "Autre");
            c.addProperty("name", "Kaito");
            c.addProperty("familyName", "Arashi");
            c.addProperty("sexe", "Homme");
            c.addProperty("type", "NINJA");
        }
        root.add("candidature", c);
        CharacterData.update(root.toString());
    }

    private static JsonObject card(String id, String name, String clan, int col, String village, String rank, boolean dead,
                                   int age, double size, String sexe, String appearance) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("clan", clan);
        o.addProperty("clanColor", col);
        o.addProperty("village", village);
        o.addProperty("rank", rank);
        o.addProperty("level", 5);
        o.addProperty("dead", dead);
        o.addProperty("age", age);
        o.addProperty("size", size);
        o.addProperty("sexe", sexe);
        o.addProperty("appearance", appearance);
        return o;
    }
}
