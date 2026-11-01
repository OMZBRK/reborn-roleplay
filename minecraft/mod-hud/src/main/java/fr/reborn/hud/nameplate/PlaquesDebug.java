package fr.reborn.hud.nameplate;

import com.google.gson.JsonObject;
import fr.reborn.hud.anchor.TeteAncre;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.chat.TypingState;
import fr.reborn.hud.combat.CombatAnimations;
import fr.reborn.hud.ko.CorpsClient;
import fr.reborn.hud.ko.KoClient;
import fr.reborn.hud.ko.KoPayload;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.tablist.TabEntry;
import fr.reborn.hud.menu.tablist.TablistData;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.runtime.VitalsFeed;
import fr.reborn.hud.voice.MeBulles;
import fr.reborn.hud.voice.VoiceState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Banc des plaques, bulles et /me (31/10), <b>inactif en production</b> : seulement avec {@code REBORN_PLAQUES_DEBUG=1}
 * (client de dev, monde solo « New World »). Vue épaule Reborn (RP forcé), 1920×1080, échelle GUI 2, horloge des fondus
 * imposée ({@link Nameplates#horloge}) : les films sont image par image. Cinq mannequins « autres joueurs » à ~3,5 blocs
 * (debout, debout derrière, à terre, allongé sans lit, accroupi), chacun avec son nom ({@link Nameplates#DEBUG_NOMS}).
 *
 * <p>Cas, dans l'ordre des captures {@code run/screenshots/plaques_<nn>_<cas>.png} : aucun regard ; fondu entrant à
 * 0, 80, 160 et 240 ms ; plaque pleine ; maintien puis fondu sortant ; 1re personne ; accroupi (aucune) ; à terre et
 * inconscient avec la croix de l'ancre ({@link TeteAncre#debugCroix} : sur la tête, pas au-dessus des pieds) ; émote
 * ({@link CombatAnimations} sur le mannequin : garde tenue, coup) ; parle (3 barres) et écrit (3 points), <b>sans
 * nom</b> (le regard quitte la cible avant que l'horloge n'avance) ; /me court à plusieurs instants, /me long ; pile
 * complète ; masqué (« ??? ») ; /me à terre ; fond clair (neige, en plongée) et nuit ; auteur d'un /me qui parle,
 * caché derrière un mannequin plus proche (3e puis 1re personne, sans puis avec le nom de celui de devant : ni bulle ni
 * /me sur celui de devant, masqués comme derrière un mur — journal « tête de derrière cachée par un corps : oui ») ; nom
 * en maintien quand un autre passe devant (21a : montre un défaut de {@link Nameplates}, le nom se pose sur son visage) ;
 * orateur à 12 blocs (la voix de proximité garde sa bulle, atténuée ; la frappe non) ; auteur devant le joueur lui-même en
 * vue épaule, tête qui dépasse (bulle et /me au-dessus) puis cachée (rien) ; soi en 3e personne ; micro des vitals vert,
 * jaune, rouge, qui parle ; micro à terre (vitals masqués) ; marche avec balancement de vue ; petit écran 1366×768 ;
 * échelle GUI 3 ; mur ; les trois autres styles de plaque.
 *
 * <p>À la fin, tout est rendu : mannequins tués, drapeaux, style, échelle, fenêtre, balancement, caméra, mode de jeu,
 * heure, météo, et le joueur à sa position d'origine <b>en absolu</b> (vérifiée hors du sol), puis le jeu se ferme.
 * Le mur provisoire est en béton gris posé seulement sur de l'air, puis retiré : le monde n'est pas modifié. Journal
 * {@code [plaques-banc]} (origine, chaque photo avec la cible retenue, ancres des corps au sol, cas cachés avec la valeur
 * attendue, orateur lointain, position finale).
 */
final class PlaquesDebug {

    private record Pas(int attente, Runnable action) {}

    /** Visées : poitrine (boîte de collision), centre de la tête, à côté (au-dessus à gauche : le mannequin reste à l'écran). */
    private static final int POITRINE = 0, TETE = 1, A_COTE = 2;
    /** Délai de regard des plaques ({@code Nameplates.DELAI_REGARD_MS}) : avancé à la main dans les films gelés. */
    private static final int DELAI = 90;
    private static final String COURT = "serre le poing et fixe l'horizon.";
    private static final String LONG = "se tourne lentement vers la porte, la main posée sur la garde de son sabre, prêt à "
        + "dégainer au moindre bruit venu du couloir sombre où quelqu'un semble l'attendre depuis longtemps.";
    private static final String MOYEN = "se tourne lentement vers la porte, la main posée sur la garde de son sabre.";

    private static final List<Pas> PAS = new ArrayList<>();
    private static int t = -1, prochain, idx;
    /**
     * Images rendues (élément HUD du banc) : chaque pas attend aussi 2 images après le précédent — après une capture
     * lente, le jeu enchaîne plusieurs ticks dans la même image et la capture suivante reprendrait l'ancienne image.
     */
    private static int images, imagesMin;
    /** Horloge imposée (ms) : +50 par tick, sauf gelée (les films avancent à la main). */
    private static long horloge;
    private static boolean gelee;
    private static Entity a, b, c, d, e;
    /** Visée tenue à chaque tick : une entité, sinon un lacet et un tangage fixes. */
    private static Entity vise;
    private static int viseMode;
    private static float lacet = Float.NaN, tangage;
    /** Numéro des /me du banc (MeBulles écarte un second message du même auteur portant le même numéro). */
    private static int nMe;

    // ── état d'origine, rendu à la fin ──────────────────────────
    private static String modeAvant = "survival";
    private static double oX, oY, oZ;
    private static float oLacet, oTangage;
    private static long heureAvant = -1;
    private static boolean pluieAvant, orageAvant, croixAvant, balancementAvant = true;
    private static int styleAvant, echelleAvant = -1, largeurAvant, hauteurAvant;
    private static RebornCamera.Mode camAvant;
    private static CameraType typeAvant;

    /** Position du banc (après la montée en surface) et places de « a » et « e » (rendues après les cas qui les déplacent). */
    private static double bX, bY, bZ, aX, aY, aZ, eX, eY, eZ;
    private static int[] mur;
    /**
     * Cas « fond clair » : hauteur du joueur avant de monter (rendue en absolu, jamais « ~-3 » : en créatif, le vol se
     * coupe au contact du sol et une descente relative l'enfoncerait dans le terrain), vol tenu tant qu'il est en l'air.
     */
    private static double yAvant = Double.NaN;
    private static boolean enHaut;
    /** Cas « marche » : la touche Avancer est tenue à chaque tick (le vrai chemin du clavier, mouvement relatif caméra). */
    private static boolean marche;
    private static final Vector3d V = new Vector3d();

    private PlaquesDebug() {}

    static void init() {
        if (!"1".equals(System.getenv("REBORN_PLAQUES_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(PlaquesDebug::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "plaques-banc"), (g, dt) -> images++);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        // fenêtre du client de dev souvent à l'arrière-plan : sans « activité », Minecraft bride les images
        mc.getFramerateLimitTracker().onInputReceived();
        if (!gelee) horloge += 50;
        if (t == 40) preparer(mc);
        // retours des commandes : le tchat est vidé à chaque tick (une capture reprend l'image d'avant le pas)
        if (t > 40) mc.gui.hud.getChat().clearMessages(false);
        if (enHaut && !mc.player.onGround() && !mc.player.getAbilities().flying) vol(mc, true);
        if (marche) mc.options.keyUp.setDown(true);
        orienter(mc);
        if (t > 40 && t >= prochain && images >= imagesMin && idx < PAS.size()) {
            Pas p = PAS.get(idx++);
            p.action().run();
            prochain = t + p.attente();
            imagesMin = images + 2;
        }
    }

    /** Un pas : l'action tout de suite, puis {@code attente} ticks (et 2 images rendues) avant le suivant. */
    private static void pas(int attente, Runnable r) { PAS.add(new Pas(attente, r)); }

    private static void preparer(Minecraft mc) {
        var cmd = mc.player.connection;
        var j = mc.player;
        // état d'origine (rendu à la fin ; la position en absolu)
        if (mc.gameMode != null) modeAvant = mc.gameMode.getPlayerMode().getName();
        oX = j.getX();
        oY = j.getY();
        oZ = j.getZ();
        oLacet = j.getYRot();
        oTangage = j.getXRot();
        heureAvant = mc.level.getDefaultClockTime();
        pluieAvant = mc.level.isRaining();
        orageAvant = mc.level.isThundering();
        croixAvant = TeteAncre.debugCroix;
        balancementAvant = mc.options.bobView().get();
        largeurAvant = mc.getWindow().getScreenWidth();
        hauteurAvant = mc.getWindow().getScreenHeight();
        typeAvant = mc.options.getCameraType();
        camAvant = RebornCamera.INSTANCE.mode();
        styleAvant = RebornPrefs.INSTANCE.nameplateStyle;
        echelleAvant = mc.options.guiScale().get();
        System.out.println(String.format(Locale.ROOT, "[plaques-banc] origine %.4f %.4f %.4f (%.1f / %.1f) mode=%s heure=%d "
            + "pluie=%b orage=%b fenêtre=%dx%d échelle=%d caméra=%s/%s", oX, oY, oZ, oLacet, oTangage, modeAvant, heureAvant,
            pluieAvant, orageAvant, largeurAvant, hauteurAvant, echelleAvant, camAvant, typeAvant));

        cmd.sendCommand("time set 6000");
        cmd.sendCommand("weather clear");
        cmd.sendCommand("gamemode creative");
        cmd.sendCommand("execute positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ -90 8");
        // restes d'un banc interrompu : sous le monde puis tués (constaté le 31/10 : un mannequin survit plusieurs secondes
        // sous le monde ; « kill » le retire tout de suite, hors de la vue)
        cmd.sendCommand("execute as @e[tag=plaque_test] run tp @s ~ -300 ~");
        cmd.sendCommand("kill @e[tag=plaque_test]");
        // reste du banc du combat (husk sans IA, jamais retiré) : il entrerait dans le champ ; sous le monde, sans butin
        cmd.sendCommand("execute as @e[type=minecraft:husk,distance=..96,nbt={NoAI:1b}] run tp @s ~ -300 ~");
        horloge = System.currentTimeMillis();
        Nameplates.horloge = () -> horloge;
        RebornSession.debugForceRp = true;
        VitalsFeed.update(82, 100, 64, 100);
        RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        // l'orbite de la caméra épaule tire le lacet du joueur : on la tient sur −90 pour que les mannequins se placent
        // devant lui et le regardent
        regarderVers(-90f, 8f);
        RebornPrefs.INSTANCE.nameplateStyle = 0;
        mc.options.guiScale().set(2);
        mc.resizeGui();
        programme(mc);
    }

    private static void programme(Minecraft mc) {
        var cmd = mc.player.connection;
        pas(4, () -> vol(mc, true));
        pas(30, () -> {
            // base du banc hors de la neige (les mannequins, sans gravité, se posent à la hauteur du joueur)
            double y = hauteurLibre(mc);
            if (y != mc.player.getY()) {
                cmd.sendCommand(String.format(Locale.ROOT, "tp @s %.4f %.4f %.4f -90 8", mc.player.getX(), y, mc.player.getZ()));
            }
            String f = "execute rotated -90 0 run summon mannequin %s {Rotation:[90f,0f],NoGravity:1b,Tags:[\"plaque_test\"],hide_description:1b%s}";
            cmd.sendCommand(String.format(Locale.ROOT, f, "^-0.6 ^ ^3.6", ""));
            cmd.sendCommand(String.format(Locale.ROOT, f, "^-1.5 ^ ^7.5", ""));
            cmd.sendCommand(String.format(Locale.ROOT, f, "^1.8 ^ ^4.6", ",pose:\"swimming\""));
            cmd.sendCommand(String.format(Locale.ROOT, f, "^4.4 ^0.45 ^2.6", ",pose:\"sleeping\""));
            cmd.sendCommand(String.format(Locale.ROOT, f, "^-3.2 ^ ^4.4", ",pose:\"crouching\""));
        });
        pas(25, () -> {
            identifier(mc);
            bX = mc.player.getX();
            bY = mc.player.getY();
            bZ = mc.player.getZ();
            if (a != null) {
                aX = a.getX();
                aY = a.getY();
                aZ = a.getZ();
            }
            if (e != null) {
                eX = e.getX();
                eY = e.getY();
                eZ = e.getZ();
            }
            nom(a, "Ren Hyūga", 0x5577DD, "Konoha");
            nom(e, "Mei Terumī", 0x3F8FBF, "Kiri");
            nom(b, "Kazuki Uchiha", 0xC0392B, "Konoha");
            nom(c, "Aiko Nara", 0x6B8E23, "Konoha");
            nom(d, "Sora Kaze", 0x8E7CC3, "Suna");
            if (b != null && c != null) CorpsClient.debugReceive("{\"t\":\"bodies\",\"b\":[{\"u\":\"" + b.getUUID()
                + "\",\"ph\":1,\"rpk\":0},{\"u\":\"" + c.getUUID() + "\",\"ph\":2,\"rpk\":0}],\"dl\":[],\"me\":{}}");
            Nameplates.debugAvatars = true;
            ailleurs();
            System.out.println(String.format(Locale.ROOT, "[plaques-banc] base %.4f %.4f %.4f ; mannequins a=%d e=%d b=%d c=%d d=%d",
                bX, bY, bZ, id(a), id(e), id(b), id(c), id(d)));
        });

        // ── 01 : personne dans le viseur → aucun nom
        pas(3, () -> photo(mc, "01_aucun_regard"));
        // ── 02 : fondu entrant à horloge gelée (le regard doit tenir 90 ms : avancé à la main), 0 / 80 / 160 / 240 ms
        pas(14, () -> { gelee = true; viser(a, POITRINE); });
        pas(3, () -> horloge += DELAI);
        pas(3, () -> photo(mc, "02_apparition_t000"));
        film(mc, "02_apparition_t", new int[] {80, 80, 80});
        // ── 03 : plaque pleine (regard tenu, horloge rendue)
        pas(20, () -> gelee = false);
        pas(3, () -> photo(mc, "03_plaque_pleine"));
        // ── 04–05 : le regard part : maintien (1 s), puis fondu sortant à horloge gelée (0 / 150 / 300 / 450 ms)
        pas(20, PlaquesDebug::ailleurs);
        pas(3, () -> photo(mc, "04_maintien_1s"));
        pas(3, () -> { gelee = true; horloge += 1600; });
        pas(3, () -> photo(mc, "05_effacement_t000"));
        film(mc, "05_effacement_t", new int[] {150, 150, 150});
        // ── 06 : 1re personne, même déclenchement au regard
        pas(18, () -> {
            gelee = false;
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.FIRST);
            viser(a, POITRINE);
        });
        pas(3, () -> photo(mc, "06_premiere_personne"));
        pas(12, () -> { RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER); ailleurs(); });
        pas(12, PlaquesDebug::oublier);
        // ── 07 : accroupi → aucun nom (règle)
        pas(18, () -> viser(d, POITRINE));
        pas(3, () -> photo(mc, "07_accroupi"));
        // ── 08–09 : à terre (ramper + phase 1) et inconscient (allongé sans lit + phase 2), croix de l'ancre : sur la
        // tête, pas au-dessus des pieds (en gris, l'ancien ancrage « œil + 0,62 »)
        pas(18, () -> { TeteAncre.debugCroix = true; viser(b, TETE); });
        pas(3, () -> { photo(mc, "08_a_terre"); journalAncre(b); });
        pas(12, PlaquesDebug::ailleurs);
        pas(12, PlaquesDebug::oublier);
        pas(18, () -> viser(c, TETE));
        pas(3, () -> { photo(mc, "09_inconscient"); journalAncre(c); });
        pas(12, () -> { TeteAncre.debugCroix = croixAvant; ailleurs(); });
        pas(12, PlaquesDebug::oublier);
        // ── 10 : émote (CombatAnimations sur le mannequin) : garde tenue, puis coup (la croix suit la tête)
        pas(14, () -> { viser(a, POITRINE); CombatAnimations.INSTANCE.playByEntityId(id(a), CombatAnimations.ANIM_BLOCK_ON); });
        pas(3, () -> photo(mc, "10_emote_garde"));
        pas(5, () -> { TeteAncre.debugCroix = true; CombatAnimations.INSTANCE.playByEntityId(id(a), CombatAnimations.ANIM_PYRAMID); });
        pas(3, () -> photo(mc, "10_emote_coup"));
        pas(20, () -> TeteAncre.debugCroix = croixAvant);
        // ── 11–12 : parle (3 barres), puis écrit (3 points), SANS nom : le regard quitte « a » d'abord (l'orbite converge
        // en quelques ticks), PUIS l'horloge avance de 3 s — sinon le maintien de 2,5 s repart après coup
        pas(12, () -> { viser(a, A_COTE); parle(a, true); });
        pas(22, PlaquesDebug::oublier);
        pas(3, () -> photo(mc, "11_parle"));
        pas(12, () -> { parle(a, false); ecrit(a); });
        pas(3, () -> photo(mc, "12_ecrit"));
        // ── 13 : /me court, apparition à horloge gelée (0 / 60 / 150 / 300 / 600 ms : fondu, montée, lettres)
        pas(12, TypingState::clear);
        pas(3, () -> { gelee = true; me(a, COURT); });
        pas(3, () -> photo(mc, "13_me_court_t000"));
        film(mc, "13_me_court_t", new int[] {60, 90, 150, 300});
        // ── 14 : /me long, seul (3 lignes au plus, puis « … »)
        pas(16, () -> { gelee = false; MeBulles.vider(); me(a, LONG); });
        pas(3, () -> photo(mc, "14_me_long"));
        // ── 15 : pile complète (nom, bulle de voix, deux /me) sans chevauchement, sur le ciel de jour
        pas(18, () -> { viser(a, POITRINE); parle(a, true); me(a, COURT); });
        pas(3, () -> photo(mc, "15_pile_complete"));
        // ── 16 : masqué : « ??? » sur la plaque, /me d'un inconnu
        pas(16, () -> { Nameplates.debugInconnu = true; MeBulles.vider(); me(a, "ajuste son masque.", true); });
        pas(3, () -> photo(mc, "16_masque"));
        pas(12, () -> { Nameplates.debugInconnu = false; parle(a, false); MeBulles.vider(); ailleurs(); });
        pas(12, PlaquesDebug::oublier);
        // ── 17 : /me sur un mannequin à terre (ancré sur la tête qui rampe)
        pas(18, () -> { viser(b, TETE); me(b, "rampe vers la porte en serrant les dents."); });
        pas(3, () -> photo(mc, "17_me_a_terre"));
        pas(12, () -> { MeBulles.vider(); ailleurs(); });
        pas(12, PlaquesDebug::oublier);
        // ── 18 : fond clair (la bulle et le /me n'ont pas de fond) : 1re personne, 3 blocs plus haut, plongée sur « a » —
        // la neige derrière toute la pile. Les corps au sol et l'accroupi ne servent plus : sous le monde ; « e » écarté.
        pas(14, () -> {
            for (Entity m : new Entity[] {b, c, d}) {
                if (m != null) cmd.sendCommand("execute as " + m.getUUID() + " run tp @s ~ -300 ~");
            }
            placer(mc, e, eX, eY, eZ + 8.0);
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.FIRST);
            yAvant = mc.player.getY();
            enHaut = true;
            cmd.sendCommand(String.format(Locale.ROOT, "tp @s ~ %.4f ~", yAvant + 3.0));
            viser(a, TETE);
            parle(a, true);
            me(a, COURT);
        });
        pas(16, () -> me(a, MOYEN));
        pas(3, () -> photo(mc, "18_neige_pile"));
        pas(12, () -> { parle(a, false); ecrit(a); });
        pas(3, () -> photo(mc, "18_neige_frappe"));
        pas(4, () -> {
            TypingState.clear();
            MeBulles.vider();
            descendre(mc);
            placer(mc, e, eX, eY, eZ);
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
        });
        // ── 19 : nuit (minuit) : même pile sur le ciel de nuit et la neige sombre
        pas(20, () -> { cmd.sendCommand("time set 18000"); viser(a, POITRINE); parle(a, true); me(a, COURT); });
        pas(3, () -> photo(mc, "19_nuit"));
        pas(12, () -> { cmd.sendCommand("time set 6000"); parle(a, false); MeBulles.vider(); viser(a, A_COTE); });
        pas(14, PlaquesDebug::oublier);
        // ── 20–21 : l'auteur d'un /me, qui parle aussi, est caché derrière un mannequin plus proche (« e » posé dans
        // l'axe caméra → « a », 3 blocs derrière) : rien ne doit se poser sur le visage ou le corps de « a » — la tête de
        // « e » est cachée par un corps (TeteAncre.cacheeParUnCorps), sa bulle et son /me s'effacent comme derrière un mur.
        // 3e puis 1re personne, sans puis avec le nom de « a » (regard)
        pas(10, () -> aligner(mc, e, a, 3.0));
        pas(18, () -> { parle(e, true); me(e, "lève la main pour saluer."); });
        pas(3, () -> { photo(mc, "20_cache_3e"); journalCache(mc, e, a, true); });
        pas(12, () -> viser(a, POITRINE));
        pas(18, () -> aligner(mc, e, a, 3.0));
        pas(3, () -> { photo(mc, "20_cache_3e_nom"); journalCache(mc, e, a, true); });
        pas(12, () -> { RebornCamera.INSTANCE.setMode(RebornCamera.Mode.FIRST); viser(a, A_COTE); });
        pas(14, PlaquesDebug::oublier);
        pas(18, () -> aligner(mc, e, a, 3.0));
        pas(3, () -> { photo(mc, "21_cache_1re"); journalCache(mc, e, a, true); });
        pas(12, () -> viser(a, POITRINE));
        pas(18, () -> aligner(mc, e, a, 3.0));
        pas(3, () -> { photo(mc, "21_cache_1re_nom"); journalCache(mc, e, a, true); });
        // ── 21a : le nom de « a » se maintient 2,5 s après le regard (parti 20° à gauche : ni « a » ni « e » dans l'axe) ;
        // « e », silencieux, passe alors devant « a » (1,5 bloc plus près, dans l'axe œil → « a ») : rien ne devrait se
        // poser sur son visage (le nom suit CalqueTete.teteDegagee, corps compris, depuis le 01/11 : avant, il restait
        // peint sur « e » jusqu'à la fin du maintien)
        pas(10, () -> {
            parle(e, false);
            MeBulles.vider();
            if (a == null) return;
            Vec3 cp = mc.gameRenderer.mainCamera().position();
            regarderVers((float) Math.toDegrees(Math.atan2(-(a.getX() - cp.x), a.getZ() - cp.z)) - 20f, 4f);
        });
        pas(16, () -> aligner(mc, e, a, -1.5));
        pas(3, () -> { photo(mc, "21a_nom_maintenu_devant"); journalCache(mc, a, e, true); });
        // ── 21b–21c : orateur à ~12 blocs (au-delà des 8 blocs de la frappe) : la voix de proximité garde sa bulle
        // (atténuée vers 80 %), la frappe n'en a pas. Posé à droite, loin de l'axe caméra → « a » : à (+12, +4), le rayon
        // du regard frôlait « a », caché derrière la tête du joueur, et son nom s'allumait sur cette tête (1er banc du 01/11) ;
        // le maintien du nom de « a » (21a) est écoulé avant la photo
        pas(12, () -> {
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            MeBulles.vider();
            placer(mc, e, bX + 10.0, eY, bZ + 7.0);
            viser(e, A_COTE);
        });
        pas(12, PlaquesDebug::oublier);
        pas(18, () -> parle(e, true));
        pas(3, () -> { photo(mc, "21b_voix_12_blocs"); journalLoin(mc); });
        pas(12, () -> { parle(e, false); ecrit(e); });
        pas(3, () -> { photo(mc, "21c_frappe_12_blocs"); journalLoin(mc); });
        // ── 21d–21e : l'auteur d'un /me qui parle (« a ») est devant le joueur, dans l'axe caméra → joueur, en vue épaule
        // (son propre corps compte en 3e personne, TeteAncre.cacheeParUnCorps). À 3 blocs, la caméra plus haute que les
        // têtes laisse dépasser le haut de la sienne : bulle et /me posés au-dessus d'elle, pas sur la tête du joueur ; à
        // 1 bloc, le centre de sa tête est caché : ni bulle ni /me. Noms coupés le temps des deux cas (le rayon du regard,
        // juste à droite de la tête du joueur, peut frôler « a ») : ils ne jugent que la bulle et le /me
        pas(12, () -> {
            TypingState.clear();
            MeBulles.vider();
            placer(mc, e, eX, eY, eZ);
            Nameplates.debugAvatars = false;
            regarderVers(-90f, 8f);
        });
        pas(16, () -> aligner(mc, a, mc.player, 3.0));
        pas(18, () -> { parle(a, true); me(a, "attend, les bras croisés."); });
        pas(3, () -> { photo(mc, "21d_derriere_soi_depasse"); journalCache(mc, a, mc.player, false); });
        pas(16, () -> aligner(mc, a, mc.player, 1.0));
        pas(3, () -> { photo(mc, "21e_derriere_soi_cache"); journalCache(mc, a, mc.player, true); });
        pas(12, () -> {
            parle(a, false);
            MeBulles.vider();
            placer(mc, a, aX, aY, aZ);
            Nameplates.debugAvatars = true;
            regarderVers(-140f, 14f);   // les mannequins sortent du cadre : rien derrière sa propre tête
        });
        pas(12, PlaquesDebug::oublier);
        // ── 22 : soi en 3e personne : bulle de voix et /me sur soi
        pas(18, () -> { VoiceState.DEBUG_SOI_PARLE = true; me(mc.player, "s'étire longuement en bâillant."); });
        pas(3, () -> photo(mc, "22_soi_3e_personne"));
        // ── 23–26 : micro des vitals (RP forcé) : vert (chuchoter), jaune (normale), rouge (crier), qui parle
        pas(10, () -> { VoiceState.DEBUG_SOI_PARLE = false; MeBulles.vider(); VoiceState.DEBUG_PORTEE = VoiceState.CHUCHOTE; });
        pas(3, () -> photo(mc, "23_micro_vert"));
        pas(8, () -> VoiceState.DEBUG_PORTEE = VoiceState.NORMALE);
        pas(3, () -> photo(mc, "24_micro_jaune"));
        pas(8, () -> VoiceState.DEBUG_PORTEE = VoiceState.CRIEE);
        pas(3, () -> photo(mc, "25_micro_rouge"));
        pas(10, () -> { VoiceState.DEBUG_PORTEE = VoiceState.NORMALE; VoiceState.DEBUG_SOI_PARLE = true; });
        pas(3, () -> photo(mc, "26_micro_parle"));
        // ── 27 : à terre (phase KO 1 du joueur local) : vitals masqués, micro seul, vert, « chuchoté »
        pas(14, () -> {
            VoiceState.DEBUG_SOI_PARLE = false;
            VoiceState.DEBUG_PORTEE = null;
            KoClient.update(new KoPayload((byte) 1, (byte) 0, (short) 45, (short) 60, false, (byte) 0, 0f, (short) 0,
                (short) 0, false, false));
        });
        pas(3, () -> photo(mc, "27_micro_a_terre"));
        pas(40, KoClient::clear);
        // ── 28 : marche avec balancement de vue, au sol, vers « a » (la touche Avancer tenue) : le nom reste collé à la
        // tête, il ne « pompe » pas ; quatre captures à 2 ticks d'écart (phases différentes du balancement)
        pas(14, () -> {
            regarderVers(-90f, 8f);
            mc.options.bobView().set(true);
            cmd.sendCommand(String.format(Locale.ROOT,
                "execute positioned %.4f %.4f %.4f positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ -90 8", bX - 6.4, bY, bZ));
            vol(mc, false);
        });
        pas(26, () -> { viser(a, POITRINE); marche = true; });
        pas(2, () -> photo(mc, "28_marche_1"));
        pas(2, () -> photo(mc, "28_marche_2"));
        pas(2, () -> photo(mc, "28_marche_3"));
        pas(2, () -> photo(mc, "28_marche_4"));
        pas(14, () -> {
            marche = false;
            mc.options.keyUp.setDown(false);
            mc.options.bobView().set(balancementAvant);
            cmd.sendCommand(String.format(Locale.ROOT, "tp @s %.4f %.4f %.4f -90 8", bX, bY, bZ));
            vol(mc, true);
            viser(a, A_COTE);
        });
        pas(10, PlaquesDebug::oublier);
        // ── 29 : petit écran (1366×768, échelle 2) : pile complète
        pas(20, () -> {
            mc.getWindow().setWindowed(1366, 768);
            viser(a, POITRINE);
            parle(a, true);
            me(a, LONG);
            me(a, COURT);
        });
        pas(3, () -> photo(mc, "29_petit_1366"));
        pas(14, () -> mc.getWindow().setWindowed(largeurAvant, hauteurAvant));
        // ── 30 : échelle GUI 3
        pas(14, () -> { mc.options.guiScale().set(3); mc.resizeGui(); });
        pas(3, () -> photo(mc, "30_echelle3"));
        pas(10, () -> { mc.options.guiScale().set(2); mc.resizeGui(); });
        // ── 31 : un mur entre la caméra et « a » : nom, bulle et /me masqués
        pas(14, () -> murer(mc, true));
        pas(3, () -> photo(mc, "31_mur"));
        pas(12, () -> murer(mc, false));
        // ── 32–34 : les trois autres styles de plaque (inchangés), sous la même pile
        pas(12, () -> RebornPrefs.INSTANCE.nameplateStyle = 1);
        pas(3, () -> photo(mc, "32_style_filet"));
        pas(12, () -> RebornPrefs.INSTANCE.nameplateStyle = 2);
        pas(3, () -> photo(mc, "33_style_village"));
        pas(12, () -> RebornPrefs.INSTANCE.nameplateStyle = 3);
        pas(3, () -> photo(mc, "34_style_ruban"));

        // ── remise en état
        pas(10, () -> rendreClient(mc));
        pas(12, () -> {
            cmd.sendCommand("kill @e[tag=plaque_test]");
            cmd.sendCommand("gamemode " + modeAvant);
            if (heureAvant >= 0) cmd.sendCommand("time set " + Math.min(Integer.MAX_VALUE, heureAvant));
            cmd.sendCommand(orageAvant ? "weather thunder" : pluieAvant ? "weather rain" : "weather clear");
            cmd.sendCommand(String.format(Locale.ROOT, "tp @s %.4f %.4f %.4f %.2f %.2f", oX, oY, oZ, oLacet, oTangage));
        });
        pas(10, () -> {
            // jamais dans le sol : si les pieds touchent un bloc à l'origine, au premier seizième de bloc libre (absolu)
            var j = mc.player;
            double y = hauteurLibre(mc);
            System.out.println(String.format(Locale.ROOT, "[plaques-banc] fin : %.4f %.4f %.4f (origine %.4f %.4f %.4f), bloc aux "
                + "pieds %s, %s", j.getX(), j.getY(), j.getZ(), oX, oY, oZ, mc.level.getBlockState(j.blockPosition()),
                y == j.getY() ? "hors du sol" : String.format(Locale.ROOT, "pieds dans un bloc → remonté à %.4f", y)));
            if (y != j.getY()) {
                cmd.sendCommand(String.format(Locale.ROOT, "tp @s %.4f %.4f %.4f %.2f %.2f", j.getX(), y, j.getZ(), oLacet, oTangage));
            }
        });
        pas(10, mc::stop);
    }

    /** Drapeaux, mannequins (sous le monde), affichage et caméra rendus ; le serveur suit au pas d'après. */
    private static void rendreClient(Minecraft mc) {
        vise = null;
        lacet = Float.NaN;
        gelee = false;
        marche = false;
        mc.options.keyUp.setDown(false);
        murer(mc, false);
        descendre(mc);
        Nameplates.debugAvatars = false;
        Nameplates.debugInconnu = false;
        Nameplates.DEBUG_NOMS.clear();
        VoiceState.DEBUG_PARLE.clear();
        VoiceState.DEBUG_SOI_PARLE = false;
        VoiceState.DEBUG_PORTEE = null;
        TypingState.clear();
        MeBulles.vider();
        KoClient.clear();
        CorpsClient.debugReceive("{\"t\":\"bodies\",\"b\":[],\"dl\":[],\"me\":{}}");
        TeteAncre.debugCroix = croixAvant;
        mc.player.connection.sendCommand("execute as @e[tag=plaque_test] run tp @s ~ -300 ~");
        RebornPrefs.INSTANCE.nameplateStyle = styleAvant;
        if (largeurAvant > 0 && (mc.getWindow().getScreenWidth() != largeurAvant || mc.getWindow().getScreenHeight() != hauteurAvant)) {
            mc.getWindow().setWindowed(largeurAvant, hauteurAvant);
        }
        if (echelleAvant >= 0) {
            mc.options.guiScale().set(echelleAvant);
            mc.resizeGui();
        }
        mc.options.bobView().set(balancementAvant);
        if (camAvant != null) RebornCamera.INSTANCE.setMode(camAvant);
        if (typeAvant != null) mc.options.setCameraType(typeAvant);
        RebornSession.debugForceRp = false;
        VitalsFeed.clear();
        Nameplates.horloge = System::currentTimeMillis;
        vol(mc, false);
    }

    /** Film à horloge gelée : avance de chaque pas (ms), une capture après chacun (nom + temps cumulé sur 3 chiffres). */
    private static void film(Minecraft mc, String nom, int[] pasMs) {
        int cumul = 0;
        for (int dt : pasMs) {
            cumul += dt;
            int ms = cumul;
            pas(3, () -> horloge += dt);
            pas(3, () -> photo(mc, nom + String.format(Locale.ROOT, "%03d", ms)));
        }
    }

    /** Plus personne dans le viseur : à gauche des mannequins. */
    private static void ailleurs() { regarderVers(-135f, 6f); }

    private static void regarderVers(float y, float p) {
        vise = null;
        lacet = y;
        tangage = p;
    }

    /** Laisse passer le maintien des plaques (horloge avancée de 3 s) — seulement une fois le regard parti. */
    private static void oublier() { horloge += 3000; }

    private static void viser(Entity cible, int mode) {
        vise = cible;
        viseMode = mode;
        lacet = Float.NaN;
    }

    private static double viseX, viseY, viseZ;

    private static boolean pointVise() {
        if (vise == null || vise.isRemoved()) return false;
        if (viseMode == TETE) {
            TeteAncre.centre(vise, V);
            viseX = V.x;
            viseY = V.y;
            viseZ = V.z;
        } else if (viseMode == A_COTE) {
            // au-dessus et à gauche de la tête (le joueur regarde vers +X : la gauche est −Z) : le rayon passe au-dessus
            // du mannequin à terre, celui qu'on vise reste au milieu de l'image sans être regardé
            viseX = vise.getX();
            viseY = vise.getY() + 2.0;
            viseZ = vise.getZ() - 1.0;
        } else {
            viseX = vise.getX();
            viseY = vise.getY() + 1.1;
            viseZ = vise.getZ();
        }
        return true;
    }

    /**
     * Tient la visée : la caméra épaule orbite autour de l'œil, donc on vise depuis sa position de l'image précédente
     * (l'orbite converge en quelques ticks) ; le joueur suit (même lacet, même tangage).
     */
    private static void orienter(Minecraft mc) {
        float y, p;
        if (pointVise()) {
            Vec3 cp = mc.gameRenderer.mainCamera().position();
            double dx = viseX - cp.x, dy = viseY - cp.y, dz = viseZ - cp.z;
            y = (float) Math.toDegrees(Math.atan2(-dx, dz));
            p = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        } else if (!Float.isNaN(lacet)) {
            y = lacet;
            p = tangage;
        } else {
            return;
        }
        RebornCamera.INSTANCE.initOrientation(y, p);
        mc.player.setYRot(y);
        mc.player.setXRot(p);
        mc.player.yRotO = y;
        mc.player.xRotO = p;
        mc.player.setYHeadRot(y);
    }

    /** Mannequins du banc, par pose (et le plus proche des deux debout = « a »). */
    private static void identifier(Minecraft mc) {
        a = b = c = d = e = null;
        for (Entity m : mc.level.entitiesForRendering()) {
            if (!(m instanceof Mannequin) || m.distanceTo(mc.player) > 12f) continue;
            Pose p = m.getPose();
            if (p == Pose.SWIMMING) b = m;
            else if (p == Pose.SLEEPING) c = m;
            else if (p == Pose.CROUCHING) d = m;
            else if (a == null) a = m;
            else if (m.distanceTo(mc.player) < a.distanceTo(mc.player)) { e = a; a = m; }
            else e = m;
        }
    }

    private static int id(Entity m) { return m == null ? -1 : m.getId(); }

    private static void nom(Entity m, String nom, int couleur, String village) {
        if (m == null) return;
        String clan = nom.substring(nom.indexOf(' ') + 1);
        Nameplates.DEBUG_NOMS.put(m.getUUID(), new TablistData.RpName(nom, TabEntry.Relation.CONNAISSANCE, couleur, clan, village));
    }

    /** Le mannequin « parle » (VoiceState, relu au tick suivant : chaque pas attend plusieurs ticks avant sa capture). */
    private static void parle(Entity m, boolean oui) {
        if (m == null) return;
        if (oui) VoiceState.DEBUG_PARLE.add(m.getId());
        else VoiceState.DEBUG_PARLE.remove(m.getId());
    }

    /** Le mannequin écrit (même message que le serveur, {@code reborn:typing}). */
    private static void ecrit(Entity m) {
        if (m != null) TypingState.update("{\"typing\":[\"" + m.getUUID() + "\"]}");
    }

    private static void me(Entity qui, String texte) { me(qui, texte, false); }

    /** Un /me par le même chemin qu'un message {@code reborn:me} du serveur (20 s, portée 16, numéro croissant). */
    private static void me(Entity qui, String texte, boolean inconnu) {
        if (qui == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("t", "me");
        o.addProperty("id", qui.getId());
        o.addProperty("u", qui.getUUID().toString());
        o.addProperty("nom", inconnu ? "???" : "");
        o.addProperty("inconnu", inconnu);
        o.addProperty("ton", "AAAAAA");
        o.addProperty("texte", texte);
        o.addProperty("ms", 20_000);
        o.addProperty("r", 16);
        o.addProperty("n", ++nMe);
        MeBulles.debugRecevoir(o.toString());
    }

    /** Téléporte un mannequin en absolu (rien si absent). */
    private static void placer(Minecraft mc, Entity m, double x, double y, double z) {
        if (m == null) return;
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp %s %.4f %.4f %.4f", m.getUUID(), x, y, z));
    }

    /**
     * Pose {@code qui} à {@code recul} blocs derrière {@code devant}, dans l'axe horizontal caméra → {@code devant}, sur
     * le même sol : sa tête est cachée par celle de {@code devant} (en 3e personne, la caméra plus haute en laisse
     * dépasser le sommet).
     */
    private static void aligner(Minecraft mc, Entity qui, Entity devant, double recul) {
        if (qui == null || devant == null) return;
        Vec3 cp = mc.gameRenderer.mainCamera().position();
        double dx = devant.getX() - cp.x, dz = devant.getZ() - cp.z, l = Math.hypot(dx, dz);
        if (l < 1e-3) return;
        placer(mc, qui, devant.getX() + dx / l * recul, devant.getY(), devant.getZ() + dz / l * recul);
    }

    /** Ancre d'un corps au sol : centre de tête rendu, pieds, écart horizontal (un allongé : ~1,6 bloc). */
    private static void journalAncre(Entity m) {
        if (m == null) return;
        TeteAncre.Qualite q = TeteAncre.centre(m, V);
        System.out.println(String.format(Locale.ROOT, "[plaques-banc] ancre %d (%s) : tête %.3f %.3f %.3f, pieds %.3f %.3f %.3f, "
            + "écart horizontal %.3f, hauteur %.3f, %s", m.getId(), m.getPose(), V.x, V.y, V.z, m.getX(), m.getY(), m.getZ(),
            Math.hypot(V.x - m.getX(), V.z - m.getZ()), V.y - m.getY(), q));
    }

    /**
     * Cas caché : où sont la caméra, le corps de devant et celui de derrière (alignement dans l'axe), et ce que l'ancre dit
     * de la tête de celui de derrière ({@code attendu} : cachée ou non).
     */
    private static void journalCache(Minecraft mc, Entity cache, Entity devant, boolean attendu) {
        if (cache == null || devant == null) return;
        Vec3 cp = mc.gameRenderer.mainCamera().position();
        double dx = devant.getX() - cp.x, dz = devant.getZ() - cp.z, cx = cache.getX() - cp.x, cz = cache.getZ() - cp.z;
        double ecart = Math.toDegrees(Math.atan2(dx * cz - dz * cx, dx * cx + dz * cz));
        System.out.println(String.format(Locale.ROOT, "[plaques-banc] caché : caméra %.2f %.2f %.2f, devant (%d) à %.2f, derrière (%d) "
            + "à %.2f, écart angulaire %.2f°, tête de derrière cachée par un corps : %s (attendu : %s)", cp.x, cp.y, cp.z,
            devant.getId(), Math.hypot(dx, dz), cache.getId(), Math.hypot(cx, cz), ecart,
            TeteAncre.cacheeParUnCorps(cache) ? "oui" : "non", attendu ? "oui" : "non"));
    }

    /** Orateur lointain : distance de l'œil du joueur au centre de la tête de « e » (la bulle de voix porte à 64 blocs). */
    private static void journalLoin(Minecraft mc) {
        if (e == null) return;
        TeteAncre.centre(e, V);
        var j = mc.player;
        double d = Math.sqrt(V.distanceSquared(j.getX(), j.getEyeY(), j.getZ()));
        // tête visible = rendue et sans bloc devant (le relief à 12 blocs n'est pas connu d'avance), puis les corps
        System.out.println(String.format(Locale.ROOT, "[plaques-banc] loin : e à %.2f blocs de l'œil, voix de proximité %s, "
            + "tête visible %s, cachée par un corps %s", d, VoiceState.voixDeProximite(e.getId()) ? "oui" : "non",
            TeteAncre.visible(e) ? "oui" : "non", TeteAncre.cacheeParUnCorps(e) ? "oui" : "non"));
    }

    /**
     * Mur provisoire (béton gris, 3 × 2 × 3, à mi-chemin entre le joueur et le mannequin « a », de 1 à 3 blocs au-dessus
     * de ses pieds) posé seulement sur de l'air, puis retiré (seul ce béton est enlevé).
     */
    private static void murer(Minecraft mc, boolean poser) {
        if (poser && a != null) {
            int mx = (int) Math.floor((mc.player.getX() + a.getX()) / 2), mz = (int) Math.floor((mc.player.getZ() + a.getZ()) / 2);
            int y0 = (int) Math.floor(a.getY()) + 1;
            mur = new int[] {mx - 1, y0, mz - 1, mx + 1, y0 + 1, mz + 1};
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:gray_concrete replace air",
                mur[0], mur[1], mur[2], mur[3], mur[4], mur[5]));
        } else if (!poser && mur != null) {
            mc.player.connection.sendCommand(String.format(Locale.ROOT,
                "fill %d %d %d %d %d %d minecraft:air replace minecraft:gray_concrete", mur[0], mur[1], mur[2], mur[3], mur[4], mur[5]));
            mur = null;
        }
    }

    /**
     * Première hauteur, au seizième de bloc, où la boîte du joueur ne touche aucun bloc (3 blocs au plus au-dessus de
     * lui) : le heightmap {@code motion_blocking} ignore les couches de neige — à la position du joueur de dev dans
     * « New World », il posait les pieds 4/16 dans {@code snow[layers=3]}.
     */
    private static double hauteurLibre(Minecraft mc) {
        var j = mc.player;
        var boite = j.getBoundingBox().deflate(1e-3);
        double y = j.getY();
        for (int k = 0; k < 48 && !mc.level.noBlockCollision(j, boite.move(0, y - j.getY(), 0)); k++) y += 1.0 / 16;
        return y;
    }

    /** Rend au joueur sa hauteur d'avant le cas « fond clair » (en absolu ; rien si déjà fait). */
    private static void descendre(Minecraft mc) {
        enHaut = false;
        if (Double.isNaN(yAvant)) return;
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s ~ %.4f ~", yAvant));
        yAvant = Double.NaN;
    }

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void photo(Minecraft mc, String nom) {
        Screenshot.grab(mc.gameDirectory, "plaques_" + nom + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        System.out.println(String.format(Locale.ROOT, "[plaques-banc] photo %s : cible=%d visée=%d fps=%d fenêtre=%dx%d échelle=%d",
            nom, Nameplates.debugCible(), id(vise), mc.getFps(), mc.getWindow().getWidth(), mc.getWindow().getHeight(),
            mc.getWindow().getGuiScale()));
    }
}
