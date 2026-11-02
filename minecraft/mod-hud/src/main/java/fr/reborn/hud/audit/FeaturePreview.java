package fr.reborn.hud.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.chat.TypingState;
import fr.reborn.hud.immersion.CinemaBars;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.keybind.NomTouche;
import fr.reborn.hud.keybind.RappelsTouches;
import fr.reborn.hud.keybind.RappelsTouches.Rappel;
import fr.reborn.hud.ko.CorpsClient;
import fr.reborn.hud.ko.KoClient;
import fr.reborn.hud.ko.KoPayload;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.tablist.TabEntry;
import fr.reborn.hud.menu.tablist.TablistData;
import fr.reborn.hud.nametag.Nametags;
import fr.reborn.hud.notif.NotifHud;
import fr.reborn.hud.ost.OstHud;
import fr.reborn.hud.ost.OstLien;
import fr.reborn.hud.recit.Gabarit;
import fr.reborn.hud.recit.NarrationClient;
import fr.reborn.hud.recit.Recit;
import fr.reborn.hud.recit.RecitHud;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.runtime.VitalsFeed;
import fr.reborn.hud.runtime.VitalsHud;
import fr.reborn.hud.screenshot.CapturePreview;
import fr.reborn.hud.staff.CanalJson;
import fr.reborn.hud.ui.HudEditScreen;
import fr.reborn.hud.ui.TexteCache;
import fr.reborn.hud.ui.TexteRaster;
import fr.reborn.hud.voice.MeBulles;
import fr.reborn.hud.voice.QuiParleHud;
import fr.reborn.hud.voice.VoiceState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Aperçu avant publication, <b>inactif en production</b> ({@code REBORN_FEATURE_PREVIEW}, monde solo de dev
 * « New World », à lancer par Git Bash, cf. MOD_HUD.md §5) → {@code run/screenshots/ost_*.png},
 * {@code basdroite_*.png}, {@code centre_*.png} et {@code narration_*.png}, puis le jeu se ferme. Lot actuel : lot 2
 * « Reprises Ketto » — étape 2a, <b>lecteur OST en haut à droite</b> (variante B, maquettes b1 / b2) ; étape 2b,
 * <b>colonne bas-droite</b> (qui parle, notifications, rappels de touches ; maquettes c1 à c4) ; étape 2c, <b>texte au
 * centre</b> ({@code recit/Recit}, maquettes a1 à a8) ; étape 2d, <b>narration du staff</b> (canal
 * {@code reborn:narration}, {@code recit/NarrationClient}).
 *
 * <ul>
 *   <li>{@code =1} : les quatre séries, l'OST, le bas-droite, le centre puis la narration ; {@code =ost},
 *       {@code =basdroite}, {@code =centre} ou {@code =narration} : l'une seule.</li>
 *   <li><b>Narration</b>, dans son allure plein écran ({@code recit/Gabarit.NARRATION}, décision du 02/11 ; jamais
 *       celle des stèles) : messages du serveur simulés (JSON écrit comme {@code Narrations.json}), passés au vrai
 *       receveur ({@code NarrationClient.recevoir}) comme s'ils arrivaient du canal, horloge figée : une narration (file,
 *       auto, texte d'essai de la planche d1) en salle sombre avec le HUD, de jour, sur la neige, deux en file (la 1re,
 *       son fondu de sortie, l'écart de 400 ms, la 2e), « remplace » qui coupe l'écran (0,50 à 75 ms) et vide la file,
 *       une tenue (2 min plus tard, puis {@code narration_fin} : 150 ms, effacée), le garde-fou de 10 min,
 *       {@code narration_fin} à id vide, les quatre couleurs et une clé inconnue, près de 1 000 caractères (pages),
 *       {@code pos} 30 puis hors bornes, un mot plus large que la ligne, le rattrapage ({@code ecoule}), les refus du
 *       protocole ; les règles de place et de visibilité de l'étape 2c en grand (écran noir, KO, à terre, tchat, F1, une
 *       stèle couverte par une narration puis revenue, 1366×768, 2560×1440) ; la variante V1 de la planche d1 ; enfin un
 *       message envoyé par le serveur intégré sur le vrai réseau. Vérifications « OK · » / « ÉCART · » au journal
 *       {@code run/narration_banc.txt} (avec la demande posée de chaque capture : {@code python tools/centre_compare.py
 *       --serie narration} y repose le moteur des maquettes, à l'allure du journal).</li>
 *   <li><b>Centre</b> : horloge du texte figée ({@link Recit#horloge}, chaque état réglé à la milliseconde), salle
 *       sombre bâtie 100 blocs au-dessus du joueur (polished deepslate, lumière invisible) et plateau de neige 130 blocs
 *       au-dessus (posés sur de l'air, retirés à la fin), forêt de jour : salle sombre façon Ketto avec le HUD (a1),
 *       jour (a2), texte court sur la neige (a3), fondu d'entrée à 0 / 60 / 120 / 180 ms, plein, sortie à 150 ms sur
 *       300 (a4), 1366×768, 1920×1080 et 2560×1440 à l'échelle d'interface 3 (a5), écran noir du staff sans puis avec
 *       texte, inconscient (texte long et court), à terre (a6), tchat ouvert à l'échelle 4 puis 2 (a8), noms, bulle et
 *       /me d'un mannequin à 25 % quand ils touchent le bloc, deux pages, F1 « BARS » et « CLEAN », lecture d'une plaque
 *       (lot 4 : narration qui passe devant, retour de la plaque, autre plaque, fin). Chaque état fixe est pris deux
 *       fois, la couche masquée ({@link RecitHud#debugMasque}) puis posée, et journalisé dans {@code run/centre_banc.txt}
 *       (demande posée : ancre, échelle, texte) : {@code python tools/centre_compare.py} y repose le moteur des
 *       maquettes.</li>
 *   <li><b>OST</b> : sans mod-ost, l'état de la lecture est <b>simulé</b> dans l'ObjectShare ({@value OstLien#CLE},
 *       mêmes tableaux que reborn-ost 0.2.6). Jour (ciel) puis nuit : 100 % (barres de la maquette b2, puis qui
 *       ondulent), 70 % après 10 s, pause, titre long (« … »), sous l'indicateur « Mode animation » ; écran noir du staff
 *       (le bloc dessous), bande de la cloche en 1080p, 1366×768 avec bande et mode staff (b2), 1280×720 avec une bande
 *       pleine largeur (le bloc passe dessous), option « Masquer après 10 s » (à 5 s puis à 11,5 s), éditeur de HUD
 *       (élément « Musique » sélectionné avec son option, puis panneau replié : l'aperçu). Chaque état fixe est pris
 *       deux fois de suite, l'élément masqué puis visible ({@code <cas>_sans.png} puis {@code <cas>.png}, monde gelé
 *       par {@code /tick freeze}) : le fond nu sert à reposer la maquette ({@code tools/ost_compare.py}).</li>
 *   <li><b>Bas-droite</b> : orateurs simulés ({@code VoiceState.DEBUG_ORATEURS}, {@code DEBUG_SOI_PARLE},
 *       {@code DEBUG_PORTEE}), noms des maquettes ({@code Nametags.DEBUG_NOMS}), vumètres figés
 *       ({@code QuiParleHud.DEBUG_VU}), notifications réelles ({@code NotifHud.message}), rappels imposés
 *       ({@code RappelsTouches.DEBUG}) ou réels (porté de plein gré, mode animation du staff, à terre) : personne,
 *       un orateur, « INCONNU », soi dans les trois portées, orateur loin (60 %), la colonne complète (c1), vumètre
 *       qui ondule, contextes des rappels (c3 : animation, course, descendre, se lever, Poste de garde, AZERTY « ; »,
 *       clic molette, 4 lignes, 3 avec des notifications, « Voix : … » dans les trois portées), collisions (c4 :
 *       sous-titres vanilla aux échelles 2, 3 et 4, carte d'aperçu de capture, à terre), 1366×768, échelle
 *       d'interface 4, éditeur de HUD, Réglages › Interface. Fond nu puis colonne pour les cas reproduits par
 *       {@code tools/basdroite_compare.py}.</li>
 *   <li>{@code =compat} : avec un vrai reborn-ost dans {@code run/mods} (0.2.6 ou 0.2.5) et une piste dans
 *       {@code run/reborn/ost/<catégorie>/} : la première piste de la bibliothèque joue (par réflexion, volume général
 *       coupé le temps du banc), captures lecture / pause / arrêt et journal {@code [ost-banc]} (version de mod-ost,
 *       lien ObjectShare, qui dessine le lecteur).</li>
 * </ul>
 * Rendus à la fin : réglages des éléments « Musique », « Qui parle » et « Rappels de touches », option, touches
 * « Portée de la voix » et « Animations », sous-titres, échelle d'interface, fenêtre, mode de jeu, heure, gel du monde,
 * volume général, mode staff, écran noir, bandes, mode cinéma, KO, crochets de banc (orateurs, noms, vumètres,
 * rappels, horloge et masque du texte au centre), blocs posés (salle, plateau), mannequin, position du joueur (en
 * absolu).
 */
public final class FeaturePreview {

    private static final String TITRE_LONG = "Les ombres s'allongent sur la Feuille";

    private static int t = -1;
    private static String mode;
    private static boolean compat, ost, basDroite, centre, narration, garde;

    /* état simulé (même format que reborn-ost 0.2.6) */
    private static final String[] TEXTES = {"", ""};
    private static final long[] ETAT = {0L, 0L, 0L};

    /* séquence : attente (ticks) puis action ; un pas conditionnel attend en plus sa condition (10 s au plus) */
    private record Pas(int attente, Runnable action, java.util.function.BooleanSupplier condition) { }
    private static final List<Pas> PAS = new ArrayList<>();
    private static int idx, prochain;
    /**
     * Images rendues (élément HUD du banc) : chaque pas attend aussi 2 images après le précédent, la dernière dessinée
     * au moins « attente » après lui — une capture fige le rendu un instant, le jeu rattrape ensuite plusieurs ticks
     * d'affilée et une capture reprendrait une image d'avant (barres qui ondulent prises deux fois à la même hauteur).
     */
    private static int images, imagesMin;
    private static long imageA, pasA;

    /* réglages rendus à la fin */
    private static HudElementState ostAvant, quiAvant, rappelsAvant;
    private static boolean optionAvant, sousTitresAvant;
    private static int echelleAvant = -1, largeurAvant, hauteurAvant;
    private static double volumeAvant = -1;
    private static String jeuAvant = "survival";
    private static long heureAvant = -1;
    private static InputConstants.Key porteeAvant, animationsAvant;
    private static final StringBuilder JOURNAL = new StringBuilder();

    /* bas-droite : orateurs des maquettes c1 / c2 */
    private static final UUID REN = UUID.nameUUIDFromBytes("banc:ren".getBytes(StandardCharsets.UTF_8));
    private static final UUID KOZUE = UUID.nameUUIDFromBytes("banc:kozue".getBytes(StandardCharsets.UTF_8));
    private static final UUID MASQUE = UUID.nameUUIDFromBytes("banc:masque".getBytes(StandardCharsets.UTF_8));
    private static UUID soi;

    private FeaturePreview() {}

    public static void init() {
        mode = System.getenv("REBORN_FEATURE_PREVIEW");
        if (mode == null || mode.isBlank()) return;
        compat = "compat".equals(mode);
        boolean tout = !compat && !"ost".equals(mode) && !"basdroite".equals(mode) && !"centre".equals(mode)
            && !"narration".equals(mode) && !"garde".equals(mode);
        ost = compat || tout || "ost".equals(mode);
        basDroite = tout || "basdroite".equals(mode);
        centre = tout || "centre".equals(mode);
        narration = tout || "narration".equals(mode);
        garde = tout || "garde".equals(mode);
        ClientTickEvents.END_CLIENT_TICK.register(FeaturePreview::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "preview-banc"), (g, dt) -> {
            images++;
            imageA = Util.getMillis();
        });
        // F1 « CLEAN » / « BARS » : le HUD est coupé, la couche du banc n'est plus appelée — les images se comptent à la
        // fin du rendu du monde (sinon une capture reprend l'image d'avant le pas : la lecture d'une capture fige le rendu)
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN.register(ctx -> {
            if (!CinemaBars.INSTANCE.hidesHud()) return;
            images++;
            imageA = Util.getMillis();
        });
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        // fenêtre du client de dev souvent à l'arrière-plan : sans « activité », Minecraft bride les images
        mc.getFramerateLimitTracker().onInputReceived();
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        // Échap venu du dehors (la fenêtre du banc passe au premier plan pendant qu'on tape ailleurs) : le menu de pause
        // gèlerait la suite (plus aucune image du HUD, captures du menu) — aucun pas du banc n'ouvre ce menu
        if (t > 20 && idx < PAS.size() && mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            System.out.println("[preview-banc] Échap reçu pendant le banc (pas " + idx + ") : menu de pause refermé");
            mc.setScreenAndShow(null);
        }
        if (vise != null) viser(mc);
        if (t == 20) {
            preparer(mc);
            if (compat) programmeCompat(mc);
            else programme(mc);
            idx = 0;
            prochain = t + PAS.get(0).attente();
            imagesMin = images + 2;
            pasA = Util.getMillis();
        } else if (t > 20 && idx < PAS.size() && t >= prochain && pretPourPas(PAS.get(idx))
            && (mc.gui.screen() instanceof fr.reborn.hud.ui.EcranOpaque
            || images >= imagesMin && imageA - pasA >= PAS.get(idx).attente() * 50L - 30)) {
            // écran opaque (Réglages) : ni monde ni HUD dessinés, le compteur d'images ne bouge plus — on attend les ticks
            PAS.get(idx++).action().run();
            imagesMin = images + 2;
            pasA = Util.getMillis();
            if (idx < PAS.size()) prochain = t + PAS.get(idx).attente();
        }
    }

    private static void pas(int attente, Runnable action) { PAS.add(new Pas(attente, action, null)); }

    /** Un pas qui attend aussi {@code condition} (10 s au plus : au-delà, il part quand même et le journal le dit). */
    private static void pasQuand(java.util.function.BooleanSupplier condition, int attente, Runnable action) {
        PAS.add(new Pas(attente, action, condition));
    }

    private static boolean pretPourPas(Pas p) {
        if (p.condition() == null || p.condition().getAsBoolean()) return true;
        if (t - prochain < 200) return false;
        System.out.println("[preview-banc] condition du pas " + idx + " jamais remplie (10 s) : il part quand même");
        return true;
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); }

    /* ================================================================ mise en place et retour */

    private static void preparer(Minecraft mc) {
        var cfg = RebornHudClient.config();
        ostAvant = cfg.stateOf(HudElement.OST);
        quiAvant = cfg.stateOf(HudElement.QUI_PARLE);
        rappelsAvant = cfg.stateOf(HudElement.RAPPELS);
        cfg.setState(HudElement.OST, HudElement.OST.defaultState());
        cfg.setState(HudElement.QUI_PARLE, HudElement.QUI_PARLE.defaultState());
        cfg.setState(HudElement.RAPPELS, HudElement.RAPPELS.defaultState());
        optionAvant = RebornPrefs.INSTANCE.ostMasquerApres10s;
        RebornPrefs.INSTANCE.ostMasquerApres10s = false;
        sousTitresAvant = Boolean.TRUE.equals(mc.options.showSubtitles().get());
        mc.options.showSubtitles().set(false);
        porteeAvant = KeyMappingHelper.getBoundKeyOf(HudKeybinds.VOIX_PORTEE);
        animationsAvant = KeyMappingHelper.getBoundKeyOf(HudKeybinds.ANIMATIONS);
        echelleAvant = mc.options.guiScale().get();
        mc.options.guiScale().set(2);
        mc.resizeGui();
        largeurAvant = mc.getWindow().getScreenWidth();
        hauteurAvant = mc.getWindow().getScreenHeight();
        if (mc.gameMode != null) jeuAvant = mc.gameMode.getPlayerMode().getName();
        var srv = mc.getSingleplayerServer();
        if (srv != null) heureAvant = srv.overworld().getOverworldClockTime();
        RebornSession.debugForceRp = true;
        VitalsFeed.update(82, 100, 64, 100);
        soi = mc.player.getUUID();
        cmd(mc, "gamemode creative");
        vol(mc, true);
        cmd(mc, "time set 6000");
        cmd(mc, "weather clear");
        cmd(mc, "tp @s ~ ~ ~ 20 -6");
        RebornCamera.INSTANCE.initOrientation(20f, -6f);
        if (!compat) {
            // état de la lecture simulé : pas de mod-ost dans run/mods
            if (FabricLoader.getInstance().isModLoaded("reborn-ost"))
                System.out.println("[ost-banc] reborn-ost chargé : relancer avec REBORN_FEATURE_PREVIEW=compat pour l'état réel");
            else FabricLoader.getInstance().getObjectShare().put(OstLien.CLE, new Object[]{TEXTES, ETAT});
        } else {
            volumeAvant = mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).get();
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        }
        System.out.println("[ost-banc] fenêtre " + mc.getWindow().getWidth() + "×" + mc.getWindow().getHeight()
            + ", lien mod-ost " + OstLien.present());
    }

    private static void restaurer(Minecraft mc) {
        if (mc.gui.screen() != null) mc.setScreenAndShow(null);
        if (garde) fr.reborn.hud.staff.StaffDebug.narrationFermer();
        if (centre || narration) restaurerCentre(mc);
        arret();
        OstHud.debugBarres = null;
        staff(false);
        noir(false);
        viderBasDroite();
        var cfg = RebornHudClient.config();
        if (ostAvant != null) cfg.setState(HudElement.OST, ostAvant);
        if (quiAvant != null) cfg.setState(HudElement.QUI_PARLE, quiAvant);
        if (rappelsAvant != null) cfg.setState(HudElement.RAPPELS, rappelsAvant);
        RebornPrefs.INSTANCE.ostMasquerApres10s = optionAvant;
        mc.options.showSubtitles().set(sousTitresAvant);
        lier(HudKeybinds.VOIX_PORTEE, porteeAvant);
        lier(HudKeybinds.ANIMATIONS, animationsAvant);
        if (largeurAvant > 0 && (mc.getWindow().getScreenWidth() != largeurAvant || mc.getWindow().getScreenHeight() != hauteurAvant))
            mc.getWindow().setWindowed(largeurAvant, hauteurAvant);
        if (echelleAvant >= 0) {
            mc.options.guiScale().set(echelleAvant);
            mc.resizeGui();
        }
        if (volumeAvant >= 0) mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(volumeAvant);
        VitalsFeed.clear();
        RebornSession.debugForceRp = false;
        cmd(mc, "tick unfreeze");
        if (heureAvant >= 0) cmd(mc, "time set " + heureAvant);
        vol(mc, false);
        cmd(mc, "gamemode " + jeuAvant);
        ecrireJournal(mc);
    }

    /* ================================================================ programme */

    private static void programme(Minecraft mc) {
        // le ciel se pose (fin de la pluie, heure) avant de geler le monde : le fond nu et la colonne doivent être la même image
        pas(120, () -> cmd(mc, "tick freeze"));
        if (ost) programmeOst(mc);
        if (basDroite) programmeBasDroite(mc);
        if (centre) programmeCentre(mc);
        if (narration) programmeNarration(mc);
        if (garde) programmeGarde(mc);
        pas(2, () -> restaurer(mc));
        pas(20, mc::stop);
    }

    /* ================================================================ lot 2 (2a) : scènes du lecteur */

    private static void programmeOst(Minecraft mc) {
        // ------------------------------------------------------------------------------------------- jour (ciel)
        serie(mc, "jour");
        // ------------------------------------------------------------------------------------------- nuit
        pas(4, () -> { arret(); cmd(mc, "time set 18000"); });
        serie(mc, "nuit");
        // ------------------------------------------------------------------------------------------- écran noir du staff
        pas(10, () -> { OstHud.debugBarres = new int[]{6, 10, 8}; jouer("mystere/aube", "Mystère"); });
        pas(16, () -> shot(mc, "ost_21_nuit_avant_ecran_noir"));
        pas(2, () -> noir(true));
        pas(8, () -> shot(mc, "ost_22_sous_ecran_noir"));
        pas(2, () -> noir(false));
        // ------------------------------------------------------------------------------------------- bande de la cloche, 1080p
        pas(10, () -> { cmd(mc, "time set 6000"); annonce("Les patrouilles de l'Est rentrent ce soir. Rassemblement devant la "
            + "grande porte au coucher du soleil, en tenue."); });
        pas(16, () -> shot(mc, "ost_23_bande_1080p"));
        // ------------------------------------------------------------------------------------------- 1366×768 : bande + mode staff (b2)
        pas(2, () -> { arret(); mc.getWindow().setWindowed(1366, 768); });
        pas(16, () -> { modeStaff(); jouer("triste/ombres", TITRE_LONG); });
        pas(16, () -> shot(mc, "ost_24_1366_bande_staff_titre_long"));
        // ------------------------------------------------------------------------------------------- 1280×720 : bande pleine largeur
        pas(2, () -> { staff(false); arret(); mc.getWindow().setWindowed(1280, 720); });
        pas(12, () -> annonce("Les patrouilles de l'Est rentrent ce soir. Rassemblement devant la grande porte au coucher du "
            + "soleil, en tenue, armes rangées : le conseil parlera aux familles, puis l'escouade partira avant l'aube."));
        pas(6, () -> jouer("triste/ombres", TITRE_LONG));
        pas(16, () -> shot(mc, "ost_25_1280_sous_la_bande"));
        // ------------------------------------------------------------------------------------------- option « Masquer après 10 s »
        pas(2, () -> { arret(); finAnnonce(); mc.getWindow().setWindowed(largeurAvant, hauteurAvant); });
        pas(16, () -> RebornPrefs.INSTANCE.ostMasquerApres10s = true);
        pas(2, () -> jouer("combat/duel", "Combat"));
        pas(100, () -> shot(mc, "ost_26_option_a_5s"));
        pas(130, () -> shot(mc, "ost_27_option_a_11s5"));
        // ------------------------------------------------------------------------------------------- éditeur de HUD
        pas(2, () -> { arret(); mc.setScreenAndShow(new HudEditScreen(null)); });
        pas(6, () -> { if (mc.gui.screen() instanceof HudEditScreen h) h.debugSelectionner(HudElement.OST); });
        pas(12, () -> shot(mc, "ost_28_editeur_option"));
        // panneau replié (languette) : l'aperçu et la boîte de l'élément, au bord droit
        pas(2, () -> { if (mc.gui.screen() instanceof HudEditScreen h) h.debugReplier(true); });
        pas(8, () -> shot(mc, "ost_29_editeur_apercu"));
        pas(2, () -> {
            if (mc.gui.screen() instanceof HudEditScreen h) h.debugReplier(false);
            mc.setScreenAndShow(null);
            RebornPrefs.INSTANCE.ostMasquerApres10s = false;
            OstHud.debugBarres = null;
            arret();
        });
    }

    /** Une série jour ou nuit : 100 %, barres qui ondulent, 70 %, pause, reprise, titre long, sous l'indicateur. */
    private static void serie(Minecraft mc, String nom) {
        String n = nom.equals("jour") ? "0" : "1";
        pas(16, () -> { OstHud.debugBarres = new int[]{6, 10, 8}; jouer("mystere/aube", "Mystère"); });
        paire(mc, 14, "ost_" + n + "1_" + nom + "_100");
        pas(2, () -> OstHud.debugBarres = null);
        pas(2, () -> shot(mc, "ost_" + n + "2_" + nom + "_100_ondule_a"));
        pas(3, () -> shot(mc, "ost_" + n + "2_" + nom + "_100_ondule_b"));
        pas(3, () -> shot(mc, "ost_" + n + "2_" + nom + "_100_ondule_c"));
        paire(mc, 205, "ost_" + n + "3_" + nom + "_70");
        pas(2, FeaturePreview::pause);
        paire(mc, 14, "ost_" + n + "4_" + nom + "_pause");
        pas(2, () -> { OstHud.debugBarres = new int[]{6, 10, 8}; reprise(); });
        pas(14, () -> shot(mc, "ost_" + n + "5_" + nom + "_reprise_100"));
        pas(2, FeaturePreview::arret);
        pas(10, () -> jouer("triste/ombres", TITRE_LONG));
        paire(mc, 16, "ost_" + n + "6_" + nom + "_titre_long");
        pas(2, FeaturePreview::arret);
        pas(10, () -> { staff(true); jouer("mystere/aube", "Mystère"); });
        paire(mc, 16, "ost_" + n + "7_" + nom + "_sous_indicateur");
        pas(2, () -> { staff(false); arret(); });
    }

    /** Fond nu puis le bloc, à quelques images d'écart (élément masqué le temps du fond : son minuteur est gelé). */
    private static void paire(Minecraft mc, int attente, String nom) {
        pas(attente, () -> visible(false));
        pas(3, () -> shot(mc, nom + "_sans"));
        pas(1, () -> visible(true));
        pas(3, () -> shot(mc, nom));
    }

    private static void visible(boolean v) {
        var cfg = RebornHudClient.config();
        cfg.setState(HudElement.OST, cfg.stateOf(HudElement.OST).withVisible(v));
    }

    /* ---------------------------------------------------------------- lecture simulée */

    private static void jouer(String id, String titre) {
        TEXTES[0] = id;
        TEXTES[1] = titre;
        ETAT[0] = OstLien.LECTURE;
        ETAT[1] = 1L;
        ETAT[2] = Util.getMillis();
    }

    private static void pause() {
        if (compat) { reel("togglePause"); return; }
        ETAT[0] = OstLien.PAUSE;
        ETAT[2] = Util.getMillis();
    }

    private static void reprise() {
        ETAT[0] = OstLien.LECTURE;
        ETAT[2] = Util.getMillis();
    }

    private static void arret() {
        if (compat) return;
        TEXTES[0] = "";
        TEXTES[1] = "";
        ETAT[0] = OstLien.RIEN;
        ETAT[1] = 0L;
        ETAT[2] = Util.getMillis();
    }

    /* ================================================================ lot 2 (2b) : colonne bas-droite */

    private static void programmeBasDroite(Minecraft mc) {
        pas(4, () -> { cmd(mc, "time set 6000"); viderBasDroite(); });
        // ------------------------------------------------------------------------------------------- qui parle (c2)
        cas(mc, 30, "basdroite_01_personne", () -> base());
        cas(mc, 12, "basdroite_02_un_orateur", () -> { base(); orateur(REN, 3f, 3, 5, 6, 4, 3); });
        cas3(mc, "basdroite_03_inconnu");
        cas(mc, 12, "basdroite_04_soi_chuchote", () -> { base(); moi(VoiceState.CHUCHOTE, 4, 6, 5, 3, 2); });
        cas(mc, 12, "basdroite_05_soi_normale", () -> { base(); moi(VoiceState.NORMALE, 4, 6, 5, 3, 2); });
        cas(mc, 12, "basdroite_06_soi_criee", () -> { base(); moi(VoiceState.CRIEE, 5, 6, 6, 4, 3); });
        cas(mc, 12, "basdroite_07_loin", () -> {
            base();
            moi(VoiceState.CHUCHOTE, 4, 6, 5, 3, 2);
            orateur(REN, -1f, 3, 5, 6, 4, 3);    // entité non chargée : 60 %
        });
        // ------------------------------------------------------------------------------------------- la colonne complète (c1)
        complet(mc, "basdroite_08_complet");
        // vumètre réel (égaliseur factice du banc), trois images
        pas(4, FeaturePreview::viderBasDroite);
        pas(4, () -> { base(); orateur(REN, 3f); });
        pas(14, () -> shot(mc, "basdroite_09_vumetre_a"));
        pas(3, () -> shot(mc, "basdroite_09_vumetre_b"));
        pas(3, () -> shot(mc, "basdroite_09_vumetre_c"));
        // ------------------------------------------------------------------------------------------- rappels selon le contexte (c3)
        cas(mc, 12, "basdroite_10_animation", () -> { base(); RappelsTouches.DEBUG.add(Rappel.ANIMATION); });
        cas(mc, 12, "basdroite_11_course", () -> { base(); RappelsTouches.DEBUG.add(Rappel.COURSE); });
        // porté de plein gré, par le vrai état (CorpsClient) : « Descendre [Espace] », plus de ligne au centre (KoHud)
        cas(mc, 12, "basdroite_12_porte_descendre", () -> {
            base();
            CorpsClient.debugReceive("{\"t\":\"bodies\",\"b\":[],\"dl\":[],\"me\":{\"c\":1,\"v\":1}}");
        });
        pas(2, () -> CorpsClient.debugReceive("{\"t\":\"bodies\",\"b\":[],\"dl\":[],\"me\":{}}"));
        cas(mc, 12, "basdroite_13_meditation", () -> { base(); RappelsTouches.DEBUG.add(Rappel.SE_LEVER); });
        // mode animation du staff, par le vrai message du serveur : « Poste de garde [F7] »
        cas(mc, 12, "basdroite_14_staff", () -> { base(); staff(true); });
        pas(2, () -> staff(false));
        cas(mc, 12, "basdroite_15_azerty", () -> {
            base();
            NomTouche.DEBUG_NOMS.put("key.keyboard.comma", ";");   // la touche gravée « ; . » d'un clavier AZERTY
            NomTouche.invalider();
            lier(HudKeybinds.ANIMATIONS, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_COMMA));
            RappelsTouches.DEBUG.add(Rappel.ANIMATION);
        });
        pas(2, () -> { lier(HudKeybinds.ANIMATIONS, animationsAvant); NomTouche.DEBUG_NOMS.clear(); NomTouche.invalider(); });
        cas(mc, 12, "basdroite_16_clic_m", () -> {
            base();
            lier(HudKeybinds.VOIX_PORTEE, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
        });
        pas(2, () -> lier(HudKeybinds.VOIX_PORTEE, porteeAvant));
        cas(mc, 12, "basdroite_17_cumul_4", () -> {
            base();
            RappelsTouches.DEBUG.add(Rappel.ANIMATION);
            RappelsTouches.DEBUG.add(Rappel.COURSE);
            RappelsTouches.DEBUG.add(Rappel.GARDE);
        });
        cas(mc, 12, "basdroite_18_cumul_notifs", () -> {
            base();
            RappelsTouches.DEBUG.add(Rappel.ANIMATION);
            RappelsTouches.DEBUG.add(Rappel.COURSE);
            RappelsTouches.DEBUG.add(Rappel.GARDE);
            notifsC1();
        });
        // touche H : « Voix : … » sur la ligne de base, 2,5 s
        voix(mc, "basdroite_19_voix_chuchotee", VitalsHud.MIC_VERT, "Voix : chuchotée");
        voix(mc, "basdroite_19_voix_normale", VitalsHud.MIC_JAUNE, "Voix : normale");
        voix(mc, "basdroite_19_voix_criee", VitalsHud.MIC_ROUGE, "Voix : criée");
        // ------------------------------------------------------------------------------------------- collisions (c4)
        // sous-titres vanilla (option d'accessibilité) aux échelles d'interface 2, 3 et 4 : rappels masqués, qui parle et
        // notifications remontés 15 px au-dessus de la case la plus haute
        for (int e = 2; e <= 4; e++) {
            final int echelle = e;
            pas(4, () -> {
                viderBasDroite();
                mc.options.guiScale().set(echelle);
                mc.resizeGui();
                mc.options.showSubtitles().set(true);
            });
            pas(4, () -> {
                base();
                moi(VoiceState.CHUCHOTE, 4, 6, 5, 3, 2);
                orateur(REN, 3f, 3, 5, 6, 4, 3);
                notifsC1();
                cmd(mc, "playsound minecraft:entity.chicken.ambient master @s ~ ~ ~ 1 1");
                cmd(mc, "playsound minecraft:block.wooden_door.close master @s ~ ~ ~ 1 1");
                cmd(mc, "playsound minecraft:block.chest.open master @s ~ ~ ~ 1 1");
            });
            pas(14, () -> shot(mc, "basdroite_20_sous_titres_e" + echelle));
        }
        pas(2, () -> {
            mc.options.showSubtitles().set(false);
            mc.options.guiScale().set(2);
            mc.resizeGui();
            viderBasDroite();
        });
        // carte d'aperçu de capture (6 s après F2) : notifications et rappels masqués, qui parle reste au-dessus
        pas(4, () -> {
            base();
            moi(VoiceState.CHUCHOTE, 4, 6, 5, 3, 2);
            orateur(REN, 3f, 3, 5, 6, 4, 3);
            notifsC1();
            CapturePreview.INSTANCE.markPending();
        });
        pas(26, () -> shot(mc, "basdroite_21_carte_capture"));
        pas(2, FeaturePreview::viderBasDroite);
        pas(130, () -> { });   // la carte finit ses 6 s
        // à terre (phase KO 1) : rappels et notifications masqués (KoMasqueHud), qui parle reste
        pas(4, () -> {
            base();
            orateur(REN, 3f, 3, 5, 6, 4, 3);
            notifsC1();
            KoClient.update(new KoPayload((byte) 1, (byte) 0, (short) 45, (short) 60, false, (byte) 0, 0f, (short) 0,
                (short) 0, false, false));
        });
        pas(16, () -> shot(mc, "basdroite_22_a_terre"));
        pas(2, () -> { KoClient.clear(); viderBasDroite(); });
        // ------------------------------------------------------------------------------------------- 1366×768, échelle 4
        pas(4, () -> mc.getWindow().setWindowed(1366, 768));
        complet(mc, "basdroite_23_1366");
        pas(4, () -> { viderBasDroite(); mc.getWindow().setWindowed(largeurAvant, hauteurAvant); });
        pas(12, () -> { mc.options.guiScale().set(4); mc.resizeGui(); });
        complet(mc, "basdroite_24_echelle4");
        pas(4, () -> { viderBasDroite(); mc.options.guiScale().set(2); mc.resizeGui(); });
        // fond clair : 60 blocs plus haut (en vol), la caméra levée vers le ciel (pire cas de lisibilité avec la neige),
        // colonne complète et 4 rappels ; le joueur redescend d'autant ensuite
        pas(2, () -> { cmd(mc, "tp @s ~ ~60 ~ 20 -50"); RebornCamera.INSTANCE.initOrientation(20f, -50f); });
        complet(mc, "basdroite_28_ciel_complet");
        cas(mc, 12, "basdroite_29_ciel_cumul_4", () -> {
            base();
            RappelsTouches.DEBUG.add(Rappel.ANIMATION);
            RappelsTouches.DEBUG.add(Rappel.COURSE);
            RappelsTouches.DEBUG.add(Rappel.GARDE);
            moi(VoiceState.CRIEE, 5, 6, 6, 4, 3);
            orateur(REN, -1f, 3, 5, 6, 4, 3);
        });
        pas(2, () -> { viderBasDroite(); cmd(mc, "tp @s ~ ~-60 ~ 20 -6"); RebornCamera.INSTANCE.initOrientation(20f, -6f); });
        // ------------------------------------------------------------------------------------------- éditeur, réglages
        pas(6, () -> mc.setScreenAndShow(new HudEditScreen(null)));
        pas(6, () -> { if (mc.gui.screen() instanceof HudEditScreen h) { h.debugReplier(true); h.debugSelectionner(HudElement.QUI_PARLE); } });
        pas(10, () -> shot(mc, "basdroite_25_editeur_qui_parle"));
        pas(2, () -> { if (mc.gui.screen() instanceof HudEditScreen h) h.debugSelectionner(HudElement.RAPPELS); });
        pas(10, () -> shot(mc, "basdroite_26_editeur_rappels"));
        pas(2, () -> {
            if (mc.gui.screen() instanceof HudEditScreen h) h.debugReplier(false);
            mc.setScreenAndShow(new ConfigShellScreen(null, 4));
        });
        pas(12, () -> shot(mc, "basdroite_27_reglages_interface"));
        pas(2, () -> mc.setScreenAndShow(null));
    }

    /**
     * Un état de la colonne, fond nu puis colonne : tout vidé et les deux éléments masqués (capture {@code _sans}),
     * puis l'état posé et les éléments montrés (fondus d'entrée finis, noms rendus sur le fil du texte).
     */
    private static void cas(Minecraft mc, int attente, String nom, Runnable etat) {
        pas(attente, () -> { viderBasDroite(); colonne(false); });
        pas(3, () -> shot(mc, nom + "_sans"));
        pas(1, () -> { colonne(true); etat.run(); });
        pas(14, () -> shot(mc, nom));
    }

    /** Trois orateurs dont un masqué (relation INCONNU) : arrivés dans l'ordre Ren, inconnu, Kozue (du bas vers le haut). */
    private static void cas3(Minecraft mc, String nom) {
        pas(12, () -> { viderBasDroite(); colonne(false); });
        pas(3, () -> shot(mc, nom + "_sans"));
        pas(1, () -> { colonne(true); base(); orateur(REN, 3f, 3, 5, 6, 4, 3); });
        pas(2, () -> orateur(MASQUE, 3f, 2, 4, 5, 3, 2));
        pas(2, () -> orateur(KOZUE, 3f, 4, 6, 3, 5, 2));
        pas(14, () -> shot(mc, nom));
    }

    /** La colonne de la maquette c1 : soi (chuchoté), Ren Hyūga, Kozue Nara ; deux notifications ; deux rappels. */
    private static void complet(Minecraft mc, String nom) {
        pas(12, () -> { viderBasDroite(); colonne(false); });
        pas(3, () -> shot(mc, nom + "_sans"));
        pas(1, () -> {
            colonne(true);
            base();
            RappelsTouches.DEBUG.add(Rappel.ANIMATION);
            moi(VoiceState.CHUCHOTE, 4, 6, 5, 3, 2);
            orateur(REN, 3f, 3, 5, 6, 4, 3);
            notifsC1();
        });
        pas(2, () -> orateur(KOZUE, 3f, 2, 4, 3, 2, 2));
        pas(14, () -> {
            shot(mc, nom);
            journal(nom + " : qui parle « " + QuiParleHud.bilan() + " », rappels « " + RappelsTouches.bilan() + " », "
                + TexteCache.bilan());
        });
    }

    /** « Voix : … » (touche H) sur la ligne de base, puce à la couleur de la portée (2,5 s). */
    private static void voix(Minecraft mc, String nom, int teinte, String texte) {
        pas(12, () -> { viderBasDroite(); colonne(false); });
        pas(3, () -> shot(mc, nom + "_sans"));
        pas(1, () -> { colonne(true); base(); });
        // la ligne de base est affichée : la touche la remplace (comme HudKeybinds.annonceVoix)
        pas(10, () -> journal(nom + " : ligne de rappel " + (RappelsTouches.voix(teinte, texte) ? "remplacée" : "ABSENTE")));
        pas(8, () -> shot(mc, nom));
    }

    /** Ligne de base « Portée de la voix [H] » imposée (sans attendre le serveur vocal intégré de Plasmo Voice). */
    private static void base() { RappelsTouches.DEBUG.add(Rappel.PORTEE); }

    private static void orateur(UUID u, float distance, int... vu) {
        if (vu.length == 5) QuiParleHud.DEBUG_VU.put(u, vu);
        String nom = u.equals(REN) ? "Ren Hyūga" : u.equals(KOZUE) ? "Kozue Nara" : "Sasori";
        Nametags.DEBUG_NOMS.put(u, new TablistData.RpName(nom,
            u.equals(MASQUE) ? TabEntry.Relation.INCONNU : TabEntry.Relation.CONNAISSANCE, 0, null, null));
        VoiceState.DEBUG_ORATEURS.put(u, distance);
    }

    private static void moi(int portee, int... vu) {
        Nametags.DEBUG_NOMS.put(soi, new TablistData.RpName("Aki Uchiha", TabEntry.Relation.SOI, 0, null, null));
        QuiParleHud.DEBUG_VU.put(soi, vu);
        VoiceState.DEBUG_PORTEE = portee;
        VoiceState.DEBUG_SOI_PARLE = true;
    }

    /** Les deux notifications de la maquette c1, la plus ancienne d'abord. */
    private static void notifsC1() {
        NotifHud.message("info", null, "Rassasié", "100 %", "satiete_prolongee");
        NotifHud.message("info", null, "+120 ryo", "de Ren Hyūga", "ryo");
    }

    private static void colonne(boolean v) {
        var cfg = RebornHudClient.config();
        cfg.setState(HudElement.QUI_PARLE, cfg.stateOf(HudElement.QUI_PARLE).withVisible(v));
        cfg.setState(HudElement.RAPPELS, cfg.stateOf(HudElement.RAPPELS).withVisible(v));
    }

    /** Plus personne ne parle, plus de notification, plus de rappel imposé (et les noms du banc oubliés). */
    private static void viderBasDroite() {
        VoiceState.DEBUG_ORATEURS.clear();
        VoiceState.DEBUG_SOI_PARLE = false;
        VoiceState.DEBUG_PORTEE = null;
        QuiParleHud.DEBUG_VU.clear();
        if (soi != null) Nametags.DEBUG_NOMS.remove(soi);
        Nametags.DEBUG_NOMS.remove(REN);
        Nametags.DEBUG_NOMS.remove(KOZUE);
        Nametags.DEBUG_NOMS.remove(MASQUE);
        RappelsTouches.DEBUG.clear();
        NotifHud.clear();
        VoiceState.debugViderOrateurs();
        QuiParleHud.debugOublier();   // pas de garde de 1,5 s entre deux états capturés
    }

    /** Lie une touche (réglage du banc, pas enregistré dans options.txt) et remet la table des touches à jour. */
    private static void lier(KeyMapping km, InputConstants.Key k) {
        if (km == null || k == null) return;
        km.setKey(k);
        KeyMapping.resetMapping();
    }

    private static void journal(String s) {
        System.out.println("[basdroite-banc] " + s);
        JOURNAL.append(s).append('\n');
    }

    private static void ecrireJournal(Minecraft mc) {
        if (JOURNAL.isEmpty()) return;
        try {
            java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("basdroite_banc.txt"), JOURNAL.toString(),
                StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[basdroite-banc] journal non écrit : " + e);
        }
    }

    /* ================================================================ lot 2 (2c) : texte au centre */

    /** Texte d'essai des maquettes (≈ 385 caractères, 9 lignes en 1080p), saisi comme un staff le tape. */
    private static final String RECIT_TEXTE = "Écoutez. Les <ombre>ombres</> s'étirent sur Konoha. Ce soir, devant le "
        + "Hokage, vous prêtez <or>serment</> : servir sans gloire, veiller jusqu'à la <rouge>mort</>. Vos noms s'effacent ; "
        + "il ne reste que la lame et la nuit, et nul ne verra vos visages. Que le <rouge>feu</> de la Feuille garde votre "
        + "cœur. Ren Hyūga, Aki Uchiha : à l'aube, l'escouade <or>Kagerō</> part vers l'Est. \"Aujourd'hui comme à "
        + "jamais\"... Ça commence ici.";
    private static final String RECIT_COURT = "Le vent tourne. Quelque chose approche de la <rouge>porte Est</>.";
    /** Plus de 12 lignes en 1080p, avec un paragraphe : deux pages. */
    private static final String RECIT_LONG = "Il y a cent ans, quand la Feuille n'était qu'un camp de toile au bord du "
        + "fleuve, les clans se battaient pour chaque arpent de forêt. On raconte que le premier Hokage planta ici un arbre, "
        + "et jura que tant qu'il resterait debout, aucun enfant du village ne mourrait de faim.\n\nL'arbre a brûlé deux "
        + "fois. Deux fois, on l'a replanté. Ceux qui gardent la <or>porte Est</> savent pourquoi : une promesse ne vaut "
        + "que par ceux qui la tiennent, et la nuit, quand les lanternes s'éteignent, ce sont eux qui veillent. Ce soir, "
        + "c'est votre tour. Prenez la lame, prenez la lanterne, et souvenez-vous des noms gravés sur la pierre. Les "
        + "<ombre>ombres</> ne pardonnent pas l'oubli, et le <rouge>sang</> versé ici n'a jamais séché tout à fait.";
    /** Plaques du lot 4 (lecture en concentration). */
    private static final String PLAQUE_A = "Ici reposent ceux qui ont gardé la <or>porte Est</>. Que leur nom reste quand "
        + "la pierre s'effacera.";
    private static final String PLAQUE_B = "Salle du serment. Nul ne franchit ce seuil sans avoir juré devant le "
        + "<rouge>feu</> de la Feuille.";

    /** Horloge figée du texte au centre (ms), avancée à la main ; début de l'état en cours. */
    private static long hb, t0;
    /** Position d'origine (absolue), salle sombre et plateau de neige posés (centre du sol), mannequin visé. */
    private static double oX = Double.NaN, oY, oZ;
    private static float oLacet, oTangage;
    private static int sX, sY, sZ, nX, nY, nZ;
    private static boolean sallePosee, neigePosee;
    private static Entity vise;
    private static int nMe;
    private static final StringBuilder JOURNAL_CENTRE = new StringBuilder();

    private static void programmeCentre(Minecraft mc) {
        pas(4, () -> journalCentreTexte(preparerScene(mc)));
        // ------------------------------------------------------------------------------- a1 : salle sombre, avec le HUD
        pas(4, () -> aller(mc, sX + 0.5, sY, sZ + 0.5, 20f, 4f));
        pas(12, () -> {
            OstHud.debugBarres = new int[]{6, 10, 8};
            jouer("mystere/aube", "Mystère");
            base();
            moi(VoiceState.CHUCHOTE, 4, 6, 5, 3, 2);
            orateur(REN, 3f, 3, 5, 6, 4, 3);
        });
        scene(mc, "centre_01_salle_sombre", RECIT_TEXTE);
        pas(2, () -> { arret(); viderBasDroite(); OstHud.debugBarres = null; });
        // ------------------------------------------------------------------------------- a2 : jour (forêt, ciel derrière le haut du bloc)
        pas(4, () -> { cmd(mc, "time set 6000"); aller(mc, oX, oY, oZ, 20f, -20f); });
        pas(16, () -> { });
        scene(mc, "centre_02_jour", RECIT_TEXTE);
        // ------------------------------------------------------------------------------- a3 : texte court sur la neige
        pas(4, () -> aller(mc, nX + 0.5, nY, nZ + 0.5, 20f, 12f));
        pas(12, () -> { });
        scene(mc, "centre_03_neige_court", RECIT_COURT);
        // ------------------------------------------------------------------------------- a4 : fondus (salle sombre)
        pas(4, () -> aller(mc, sX + 0.5, sY, sZ + 0.5, 20f, 4f));
        pas(12, Recit::vider);
        pas(2, () -> {
            t0 = hb;
            Recit.montrer("fondu", Recit.segments(RECIT_TEXTE), Recit.AUTO, Recit.Mode.REMPLACE, Gabarit.STELE);
        });
        // texture prête (fil du texte) à t = 0 : opacité 0, l'image nue sert de fond aux comparaisons
        pasQuand(RecitHud::pret, 4, () -> {
            shot(mc, "centre_04_fondu_t000");
            journalCentre("centre_04_fondu_t000", "centre_04_fondu_t000");
        });
        for (int ms : new int[]{60, 120, 180}) {
            String nom = String.format(Locale.ROOT, "centre_04_fondu_t%03d", ms);
            pas(2, () -> hb = t0 + ms);
            pas(3, () -> { shot(mc, nom); journalCentre(nom, "centre_04_fondu_t000"); });
        }
        pas(2, () -> hb = t0 + 2000);
        pas(3, () -> { shot(mc, "centre_04_fondu_plein"); journalCentre("centre_04_fondu_plein", "centre_04_fondu_t000"); });
        pas(2, () -> { hb = t0 + 3000; Recit.cacher("fondu"); });
        pas(2, () -> hb = t0 + 3150);
        pas(3, () -> {
            shot(mc, "centre_04_fondu_sortie150");
            journalCentre("centre_04_fondu_sortie150", "centre_04_fondu_t000");
        });
        pas(2, () -> hb = t0 + 3400);
        // coupe par « remplace » : l'ancien texte s'efface en 150 ms depuis son opacité du moment (0,50 à 75 ms)
        pas(2, () -> {
            t0 = hb;
            Recit.montrer("coupe-a", Recit.segments(RECIT_TEXTE), Recit.AUTO, Recit.Mode.REMPLACE, Gabarit.STELE);
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        pas(2, () -> Recit.montrer("coupe-b", Recit.segments(RECIT_COURT), Recit.AUTO, Recit.Mode.REMPLACE, Gabarit.STELE));
        pas(2, () -> hb = t0 + 1075);
        pas(3, () -> {
            shot(mc, "centre_04_coupe075");
            journalCentre("centre_04_coupe075", "centre_04_fondu_t000");
        });
        pas(2, Recit::vider);
        // ------------------------------------------------------------------------------- a5 : tailles, échelle 3 (forêt)
        pas(4, () -> {
            cmd(mc, "time set 6000");
            aller(mc, oX, oY, oZ, 20f, -6f);
            mc.options.guiScale().set(3);
            mc.resizeGui();
            mc.getWindow().setWindowed(1366, 768);
        });
        pas(16, () -> { });
        scene(mc, "centre_05_1366_e3", RECIT_TEXTE);
        pas(4, () -> mc.getWindow().setWindowed(1920, 1080));
        pas(16, () -> { });
        scene(mc, "centre_05_1080_e3", RECIT_TEXTE);
        pas(4, () -> mc.getWindow().setWindowed(2560, 1440));
        pas(16, () -> journalCentreTexte("fenêtre demandée 2560×1440, obtenue " + mc.getWindow().getWidth() + "×"
            + mc.getWindow().getHeight() + " (échelle " + mc.getWindow().getGuiScale() + ")"));
        scene(mc, "centre_05_1440_e3", RECIT_TEXTE);
        pas(4, () -> {
            mc.getWindow().setWindowed(largeurAvant, hauteurAvant);
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
        pas(16, () -> { });
        // ------------------------------------------------------------------------------- a6 : écran noir, KO
        pas(2, () -> noir(true));
        pas(6, () -> { });
        scene(mc, "centre_06_noir", RECIT_TEXTE);
        pas(2, () -> noirTexte("Vous perdez connaissance…"));
        pas(6, () -> { });
        scene(mc, "centre_06_noir_texte", RECIT_TEXTE);
        pas(2, () -> noir(false));
        pas(4, () -> KoClient.update(new KoPayload((byte) 2, (byte) 0, (short) 167, (short) 300, false, (byte) 0, 0f,
            (short) 0, (short) 0, false, false)));
        pas(30, () -> { });   // le filtre du KO s'installe
        scene(mc, "centre_06_inconscient", RECIT_TEXTE);
        scene(mc, "centre_06_inconscient_court", RECIT_COURT);
        pas(4, () -> KoClient.update(new KoPayload((byte) 1, (byte) 0, (short) 45, (short) 60, false, (byte) 0, 0f,
            (short) 0, (short) 0, false, false)));
        pas(24, () -> { });
        scene(mc, "centre_06_a_terre", RECIT_TEXTE);
        pas(4, KoClient::clear);
        pas(20, () -> { });
        // ------------------------------------------------------------------------------- a8 : tchat ouvert (neige)
        pas(4, () -> {
            aller(mc, nX + 0.5, nY, nZ + 0.5, 20f, 0f);
            mc.options.guiScale().set(4);
            mc.resizeGui();
        });
        pas(12, () -> mc.setScreenAndShow(new ChatScreen("", false)));
        pas(8, () -> { });
        scene(mc, "centre_07_tchat_e4", RECIT_TEXTE);
        pas(2, () -> {
            mc.setScreenAndShow(null);
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
        pas(8, () -> mc.setScreenAndShow(new ChatScreen("", false)));
        pas(8, () -> { });
        scene(mc, "centre_07_tchat_e2", RECIT_TEXTE);
        pas(2, () -> mc.setScreenAndShow(null));
        // ------------------------------------------------------------------------------- centre dégagé : nom, bulle, /me à 25 %
        pas(6, () -> aller(mc, nX + 0.5, nY, nZ + 0.5, 20f, 6f));
        pas(10, () -> cmd(mc, "execute rotated 20 0 run summon mannequin ^0.8 ^ ^3.6 {Rotation:[200f,0f],NoGravity:1b,"
            + "Tags:[\"recit_test\"],hide_description:1b}"));
        pas(10, () -> {
            Recit.vider();
            vise = mannequin(mc);
            if (vise == null) {
                journalCentreTexte("mannequin introuvable : cas des 25 % sauté");
                return;
            }
            Nametags.DEBUG_NOMS.put(vise.getUUID(), new TablistData.RpName("Ren Hyūga", TabEntry.Relation.CONNAISSANCE,
                0x5577DD, "Hyūga", "Konoha"));
            Nametags.debugAvatars = true;
            TypingState.update("{\"typing\":[\"" + vise.getUUID() + "\"]}");
            me(vise, "serre le poing et fixe l'horizon.");
        });
        pas(24, () -> { shot(mc, "centre_08_nametags_avant"); journalCentreTexte("centre_08_nametags_avant : sans texte"); });
        scene(mc, "centre_08_nametags_25", RECIT_TEXTE);
        pas(2, () -> {
            Recit.vider();
            oublierMannequin(mc);
        });
        // ------------------------------------------------------------------------------- deux pages (salle sombre)
        pas(4, () -> aller(mc, sX + 0.5, sY, sZ + 0.5, 20f, 4f));
        pas(12, () -> {
            t0 = hb;
            Recit.montrer("pages", Recit.segments(RECIT_LONG), Recit.AUTO, Recit.Mode.REMPLACE, Gabarit.STELE);
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 2000);
        paireCentre(mc, 3, "centre_09_pages_1");
        pas(2, () -> { long[] tr = Recit.debugTranches("pages"); hb = t0 + (tr == null ? 20_000 : tr[0] + 10); });
        pasQuand(RecitHud::pret, 2, () -> {   // page 2 prête : fondu de page fini
            long[] tr = Recit.debugTranches("pages");
            hb = t0 + (tr == null ? 20_400 : tr[0] + 400);
        });
        paireCentre(mc, 3, "centre_09_pages_2");
        // ------------------------------------------------------------------------------- F1 : « CLEAN » puis « BARS »
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            Recit.montrer("cinema", Recit.segments(RECIT_TEXTE), Recit.AUTO, Recit.Mode.REMPLACE, Gabarit.STELE);
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        pas(2, () -> CinemaBars.INSTANCE.toggle());   // HUD → CLEAN
        pas(16, () -> {
            shot(mc, "centre_10_cinema_clean");
            journalCentreTexte("centre_10_cinema_clean : texte posé à l'écran " + (RecitHud.visible() ? "OUI (erreur)" : "non")
                + ", minuteur " + (Recit.actif() ? "qui continue" : "fini"));
        });
        pas(2, () -> CinemaBars.INSTANCE.toggle());   // CLEAN → BARS
        paireCentre(mc, 20, "centre_10_cinema_bars");
        pas(2, () -> CinemaBars.INSTANCE.toggle());   // BARS → HUD
        pas(12, Recit::vider);
        // ------------------------------------------------------------------------------- lot 4 : lecture d'une plaque
        pas(2, () -> {
            t0 = hb;
            Recit.lire("plaque:serment", Recit.segments(PLAQUE_A));
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 500);
        paireCentre(mc, 3, "centre_11_lecture_plaque");
        // une narration (4 s) passe devant : la plaque s'efface en 150 ms, la narration entre
        pas(2, () -> {
            hb = t0 + 1000;
            Recit.montrer("devant", Recit.segments(RECIT_COURT), 4, Recit.Mode.FILE, Gabarit.NARRATION);
        });
        pas(3, () -> hb = t0 + 1200);
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1500);
        paireCentre(mc, 3, "centre_11_lecture_narration_devant");
        // la narration finit : la plaque revient (première page)
        pas(2, () -> hb = t0 + 5000);
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 5400);
        paireCentre(mc, 3, "centre_11_lecture_retour");
        // une autre plaque visée : remplacement rapide
        pas(2, () -> {
            hb = t0 + 6000;
            Recit.lire("plaque:seuil", Recit.segments(PLAQUE_B));
        });
        pas(3, () -> hb = t0 + 6200);
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 6500);
        paireCentre(mc, 3, "centre_11_lecture_autre_plaque");
        // fin de la visée : fondu de 300 ms
        pas(2, () -> {
            hb = t0 + 7000;
            Recit.cacher("plaque:seuil");
        });
        pas(3, () -> hb = t0 + 7150);
        pas(3, () -> {
            shot(mc, "centre_11_lecture_fin_150");
            journalCentre("centre_11_lecture_fin_150", "centre_11_lecture_autre_plaque_sans");
        });
        pas(2, () -> hb = t0 + 7400);
        pas(3, () -> { shot(mc, "centre_11_lecture_fin"); journalCentreTexte("centre_11_lecture_fin : " + RecitHud.bilan()); });
        pas(2, () -> restaurerCentre(mc));
    }

    /**
     * Mise en place des séries du texte au centre et de la narration : bas-droite et OST vidés, texte oublié, horloge du
     * texte figée, position d'origine notée (en absolu), salle sombre (21 × 10 × 21, creuse, 100 blocs au-dessus) et
     * plateau de neige (61 × 61, 60 blocs au-dessus et 60 à l'est) posés sur de l'air, retirés par
     * {@link #restaurerCentre}. Rend la ligne du journal.
     */
    private static String preparerScene(Minecraft mc) {
        viderBasDroite();
        arret();
        OstHud.debugBarres = null;
        Recit.vider();
        vol(mc, false);   // debout sur la salle et le plateau (en vol, l'avatar prend la pose du vol)
        hb = Util.getMillis();
        Recit.horloge = () -> hb;
        var j = mc.player;
        oX = j.getX();
        oY = j.getY();
        oZ = j.getZ();
        oLacet = j.getYRot();
        oTangage = j.getXRot();
        int x = Mth.floor(oX), y = Mth.floor(oY), z = Mth.floor(oZ);
        sX = x;
        sY = Math.min(y + 100, 270);
        sZ = z;
        // plateau 60 blocs plus haut, sous la couche des nuages (vers 192 : à 130 blocs, la caméra y entrait), et 60 blocs
        // à l'est : ni dans l'ombre de la salle, ni dans le champ (la vue regarde vers le sud)
        nX = x + 60;
        nY = Math.min(y + 60, 170);
        nZ = z;
        cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:polished_deepslate hollow",
            sX - 10, sY - 1, sZ - 10, sX + 10, sY + 8, sZ + 10));
        for (int dx = -5; dx <= 5; dx += 10) {
            for (int dz = -5; dz <= 5; dz += 10) {
                cmd(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:light[level=10]", sX + dx, sY + 6, sZ + dz));
            }
        }
        sallePosee = true;
        cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:snow_block",
            nX - 30, nY - 1, nZ - 30, nX + 30, nY - 1, nZ + 30));
        neigePosee = true;
        return "origine " + String.format(Locale.ROOT, "%.3f %.3f %.3f (%.1f / %.1f)", oX, oY, oZ, oLacet, oTangage)
            + ", salle y " + sY + ", neige y " + nY;
    }

    /** Un texte montré (remplace, durée automatique), horloge figée : la texture se rend, puis plein 1 s plus tard. */
    private static void scene(Minecraft mc, String nom, String balise) {
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            Recit.montrer("banc", Recit.segments(balise), Recit.AUTO, Recit.Mode.REMPLACE, Gabarit.STELE);
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);   // fondu d'entrée parti à t0 (texture prête), plein 1 s après
        paireCentre(mc, 4, nom);
    }

    /** Fond nu puis le texte, même image (couche non posée, tout le reste identique : viseur masqué, noms à 25 %). */
    private static void paireCentre(Minecraft mc, int attente, String nom) {
        pas(attente, () -> RecitHud.debugMasque = true);
        pas(3, () -> shot(mc, nom + "_sans"));
        pas(1, () -> RecitHud.debugMasque = false);
        pas(3, () -> { shot(mc, nom); journalCentre(nom, nom + "_sans"); });
    }

    /** Téléporte le joueur (en absolu) et oriente la caméra Reborn. */
    private static void aller(Minecraft mc, double x, double y, double z, float lacet, float tangage) {
        cmd(mc, String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", x, y, z, lacet, tangage));
        RebornCamera.INSTANCE.initOrientation(lacet, tangage);
    }

    /** Le mannequin du banc le plus proche (8 blocs). */
    private static Entity mannequin(Minecraft mc) {
        Entity m = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Mannequin) || e.distanceTo(mc.player) > 8f) continue;
            if (m == null || e.distanceTo(mc.player) < m.distanceTo(mc.player)) m = e;
        }
        return m;
    }

    /** Vise la tête du mannequin à chaque tick (caméra Reborn et joueur), comme le banc des nametags. */
    private static void viser(Minecraft mc) {
        if (vise.isRemoved() || mc.gameRenderer.mainCamera() == null) {
            vise = null;
            return;
        }
        Vec3 cp = mc.gameRenderer.mainCamera().position();
        double dx = vise.getX() - cp.x, dy = vise.getY() + 1.62 - cp.y, dz = vise.getZ() - cp.z;
        float y = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float p = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        RebornCamera.INSTANCE.initOrientation(y, p);
        mc.player.setYRot(y);
        mc.player.setXRot(p);
        mc.player.yRotO = y;
        mc.player.xRotO = p;
        mc.player.setYHeadRot(y);
    }

    /** Un /me par le même chemin qu'un message {@code reborn:me} du serveur (20 s, portée 16). */
    private static void me(Entity qui, String texte) {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("t", "me");
        o.addProperty("id", qui.getId());
        o.addProperty("u", qui.getUUID().toString());
        o.addProperty("nom", "");
        o.addProperty("inconnu", false);
        o.addProperty("ton", "AAAAAA");
        o.addProperty("texte", texte);
        o.addProperty("ms", 20_000);
        o.addProperty("r", 16);
        o.addProperty("n", ++nMe);
        MeBulles.debugRecevoir(o.toString());
    }

    private static void oublierMannequin(Minecraft mc) {
        if (vise != null) Nametags.DEBUG_NOMS.remove(vise.getUUID());
        vise = null;
        Nametags.debugAvatars = false;
        TypingState.clear();
        MeBulles.vider();
        cmd(mc, "kill @e[tag=recit_test]");
    }

    /** Fin de la série (ou du banc) : texte oublié, horloge rendue, F1, KO, mannequin, blocs posés, position d'origine. */
    private static void restaurerCentre(Minecraft mc) {
        Recit.vider();
        Recit.horloge = Util::getMillis;
        RecitHud.debugMasque = false;
        NarrationClient.debugGabarit = null;
        while (CinemaBars.INSTANCE.mode() != CinemaBars.Mode.HUD) CinemaBars.INSTANCE.toggle();
        KoClient.clear();
        if (mc.player == null) return;
        oublierMannequin(mc);
        if (sallePosee) {
            cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", sX - 10, sY - 1, sZ - 10, sX + 10,
                sY + 8, sZ + 10));
            sallePosee = false;
        }
        if (neigePosee) {
            cmd(mc, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", nX - 30, nY - 1, nZ - 30, nX + 30,
                nY - 1, nZ + 30));
            neigePosee = false;
        }
        if (!Double.isNaN(oX)) {
            aller(mc, oX, oY, oZ, oLacet, oTangage);
            oX = Double.NaN;
        }
        if (!JOURNAL_CENTRE.isEmpty()) {
            try {
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("centre_banc.txt"), JOURNAL_CENTRE.toString(),
                    StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.out.println("[centre-banc] journal non écrit : " + e);
            }
        }
        if (!JOURNAL_NARRATION.isEmpty()) {
            try {
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("narration_banc.txt"),
                    JOURNAL_NARRATION.toString(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.out.println("[narration-banc] journal non écrit : " + e);
            }
        }
    }

    /** Demande posée (une ligne JSON de {@code run/centre_banc.txt}, lue par {@code tools/centre_compare.py}). */
    private static void journalCentre(String cas, String sans) {
        JsonObject o = RecitHud.etatBanc();
        o.addProperty("cas", cas);
        o.addProperty("sans", sans);
        o.addProperty("viseur_masque", RecitHud.masqueVanilla());
        JOURNAL_CENTRE.append(o).append('\n');
    }

    private static void journalCentreTexte(String s) {
        System.out.println("[centre-banc] " + s);
        JsonObject o = new JsonObject();
        o.addProperty("note", s);
        JOURNAL_CENTRE.append(o).append('\n');
    }

    /* ================================================================ lot 2 (2d) : narration du staff, comme un joueur la voit */

    /**
     * Texte d'essai de la planche d1 (narration plein écran, 02/11 ; texte original, 141 caractères) : la même phrase
     * dans le jeu et sur les maquettes V1 à V3.
     */
    private static final String NARR_GONG = "Trois coups de gong résonnent depuis la tour du Hokage. Les portes du village "
        + "se ferment jusqu'à l'aube : que chaque ninja rejoigne son poste.";
    /** File : la narration qui suit {@link #RECIT_COURT} (durée automatique). */
    private static final String NARR_SUITE = "Les lanternes s'éteignent une à une. Restez groupés et gardez la <or>porte "
        + "Est</> en vue.";
    /** Remplace : n4 à l'écran (20 s), n5 et n6 en file, puis n7 coupe tout. */
    private static final String NARR_FOULE = "La foule se presse sur la place du marché. Les marchands ferment leurs volets "
        + "un à un.";
    private static final String NARR_GARDE = "Un garde court vers la tour de guet.";
    /** Voyelles à macron (Philosopher Bold n'en a aucune précomposée : lettre + U+00AF, comme le moteur des maquettes). */
    private static final String NARR_MACRONS = "Ren Hyūga et Ōtsutsuki Kōta franchissent la porte. Āsa, Ēri et Īzumi les "
        + "attendent au pied de la tour : « Ūn seul mot, et tout s'arrête. »";
    private static final String NARR_CLOCHES = "Les cloches sonnent trois coups.";
    private static final String NARR_SILENCE = "Silence. Le <rouge>Hokage</> prend la parole.";
    private static final String NARR_TENUE = "Le conseil délibère. Personne ne quitte la salle avant la fin du <or>serment</>.";
    private static final String NARR_POS = "Au loin, un corbeau se pose sur la <ombre>tour de guet</>.";
    /** Un mot plus large que la ligne (cri sans espace) : coupé entre deux caractères. */
    private static final String NARR_MOT_LONG = "Un cri déchire la nuit : « AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAH ! » "
        + "Puis plus rien.";
    /** 998 caractères (points de code, \n compris), trois paragraphes : la limite du serveur, en plusieurs pages. */
    private static final String NARR_1000 = "Il y a cent ans, quand la Feuille n'était qu'un camp de toile au bord du fleuve, "
        + "les clans se battaient pour chaque arpent de forêt. On raconte que le premier Hokage planta ici un arbre, et jura "
        + "que tant qu'il resterait debout, aucun enfant du village ne mourrait de faim.\n\nL'arbre a brûlé deux fois. Deux "
        + "fois, on l'a replanté. Ceux qui gardent la <or>porte Est</> savent pourquoi : une promesse ne vaut que par ceux "
        + "qui la tiennent, et la nuit, quand les lanternes s'éteignent, ce sont eux qui veillent. Ce soir, c'est votre tour. "
        + "Prenez la lame, prenez la lanterne, et souvenez-vous des noms gravés sur la pierre. Les <ombre>ombres</> ne "
        + "pardonnent pas l'oubli, et le <rouge>sang</> versé ici n'a jamais séché tout à fait.\n\nÀ l'aube, vous rendrez "
        + "compte au conseil. Ce que vous aurez vu restera entre ces murs, et ce que vous aurez tu vous suivra longtemps, bien "
        + "après la relève. Que la <gris>cendre</> des anciens feux vous rappelle ce que coûte une porte laissée ouverte, et "
        + "que votre nom, un jour, mérite sa place sur la pierre.";

    private static final StringBuilder JOURNAL_NARRATION = new StringBuilder();
    /** Vérifications du banc : faites, hors cible. */
    private static int verifs, ecarts;
    /** Chemin réseau : le client a déclaré {@code reborn:narration} au serveur intégré ({@code ServerPlayNetworking.canSend}). */
    private static volatile Boolean reseauDeclare;
    /** Réception du dernier message (temps réel) : délai jusqu'à la texture prête (fil du texte). */
    private static long recuA;
    /** Fin de chaque page de la narration longue (ms, depuis son début), première page en dernier élément. */
    private static long[] tranches1000;
    /** Centre vertical du bloc plein (narration n1) : les fondus ne déplacent rien. */
    private static double cyPlein;

    /**
     * La narration du staff ({@code /narration}, canal {@code reborn:narration}) telle qu'un joueur la voit : messages du
     * serveur simulés, passés au vrai receveur ({@link fr.reborn.hud.recit.NarrationClient#recevoir}) comme s'ils
     * arrivaient du canal, horloge du texte figée. Allure {@link Gabarit#NARRATION} (plein écran, blanc pur, plus grande,
     * décision du 02/11), jamais celle des stèles ; les règles de place et de visibilité de l'étape 2c rejouées avec elle
     * ; la variante V1 de la planche d1 en fin de série. Enfin un message envoyé par le serveur intégré, par le vrai
     * réseau (type S2C enregistré, codec, fil client).
     */
    private static void programmeNarration(Minecraft mc) {
        pas(4, () -> journalNarrationTexte(preparerScene(mc)));
        pas(4, () -> aller(mc, sX + 0.5, sY, sZ + 0.5, 20f, 4f));
        pas(14, () -> { verifs = 0; ecarts = 0; NarrationClient.debugGabarit = null; });
        // ------------------------------------------------------------------------------- 1. une narration (file, auto)
        pas(12, () -> {   // le HUD de la salle sombre : OST « Mystère », qui parle (Ren Hyūga, soi), rappel de portée
            OstHud.debugBarres = new int[]{6, 10, 8};
            jouer("mystere/aube", "Mystère");
            base();
            moi(VoiceState.CHUCHOTE, 4, 6, 5, 3, 2);
            orateur(REN, 3f, 3, 5, 6, 4, 3);
        });
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n1", NARR_GONG, 0, "file", null, 0));
            verifier("n1 à l'écran tout de suite (écran libre)", "n1".equals(Recit.debugEcran()));
            verifier("n1 : allure de la narration (gabarit « narration »), pas celle des stèles",
                Recit.debugGabarit("n1") == Gabarit.NARRATION);
            verifier("n1 : durée auto = clamp(1 300 + n × 1 000 / 30 ; 4 000 ; 60 000) = " + dureeAuto(NARR_GONG) + " ms",
                Recit.debugDuree("n1") == dureeAuto(NARR_GONG));
        });
        // fondu d'entrée de 600 ms, opacité seule (vidéos de Ketto) : 0, 150, 300, 450 ms, puis pleine
        pasQuand(RecitHud::pret, 2, () -> { notePret("n1"); cyPlein = RecitHud.etatBanc().get("cy").getAsDouble(); });
        pas(3, () -> { shot(mc, "narration_01_entree_t000"); journalNarration("narration_01_entree_t000", "narration_01_file_auto_sans"); });
        for (int ms : new int[]{150, 300, 450}) {
            String nom = String.format(Locale.ROOT, "narration_01_entree_t%03d", ms);
            float attendu = 1f - (1f - ms / 600f) * (1f - ms / 600f);
            pas(2, () -> hb = t0 + ms);
            pas(3, () -> {
                shot(mc, nom);
                journalNarration(nom, "narration_01_file_auto_sans");
                verifier(String.format(Locale.ROOT, "entrée de la narration : opacité %.3f à %d ms (600 ms, ease-out, ni "
                    + "glissement ni changement de taille)", attendu, ms), Math.abs(RecitHud.etatBanc().get("a").getAsDouble() - attendu) < 0.01
                    && Math.abs(RecitHud.etatBanc().get("cy").getAsDouble() - cyPlein) < 0.01);
            });
        }
        pas(2, () -> hb = t0 + 1000);
        paireNarration(mc, 4, "narration_01_file_auto");
        pas(2, () -> { verifierAllure("n1", Gabarit.NARRATION); cyPlein = RecitHud.etatBanc().get("cy").getAsDouble(); });
        // tenue : 3 lignes au plus, puis fondu de sortie de 700 ms (350 ms avant la fin : 0,50)
        pas(2, () -> {
            JsonObject e = RecitHud.etatBanc();
            verifier("narration courte : " + e.get("lignes").getAsInt() + " ligne(s) ≤ 3 par page, tenue "
                + (dureeAuto(NARR_GONG) - 1300) + " ms entre les fondus (≈ 7 s pour 3 lignes pleines)", e.get("lignes").getAsInt() <= 3);
            hb = t0 + dureeAuto(NARR_GONG) - 350;
        });
        pas(3, () -> {
            shot(mc, "narration_01_sortie_350");
            journalNarration("narration_01_sortie_350", "narration_01_file_auto_sans");
            verifier("sortie de la narration : opacité 0,50 à 350 ms de la fin (700 ms, linéaire, sur place)",
                Math.abs(RecitHud.etatBanc().get("a").getAsDouble() - 0.5) < 0.01
                && Math.abs(RecitHud.etatBanc().get("cy").getAsDouble() - cyPlein) < 0.01);
        });
        pas(2, () -> hb = t0 + dureeAuto(NARR_GONG) - 120);
        pas(3, () -> { shot(mc, "narration_01_sortie_120"); journalNarration("narration_01_sortie_120", "narration_01_file_auto_sans"); });
        pas(2, () -> { arret(); viderBasDroite(); OstHud.debugBarres = null; });
        // de jour (forêt, ciel derrière le haut du bloc) et sur la neige : le blanc pur sur un fond clair
        pas(4, () -> { Recit.vider(); cmd(mc, "time set 6000"); aller(mc, oX, oY, oZ, 20f, -20f); });
        pas(16, () -> { });
        sceneNarration(mc, "narration_01_jour", "n1b", NARR_GONG);
        pas(4, () -> { Recit.vider(); aller(mc, nX + 0.5, nY, nZ + 0.5, 20f, 12f); });
        pas(12, () -> { });
        sceneNarration(mc, "narration_01_neige", "n1c", NARR_GONG);
        pas(4, () -> { Recit.vider(); aller(mc, sX + 0.5, sY, sZ + 0.5, 20f, 4f); });
        pas(12, () -> { });
        // ------------------------------------------------------------------------------- 2. deux narrations en file
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n2", RECIT_COURT, 4, "file", null, 0));
            recevoir(narration("n3", NARR_SUITE, 0, "file", null, 0));
            verifier("n2 à l'écran, n3 en file", "n2".equals(Recit.debugEcran()) && "n3".equals(Recit.debugFile()));
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        paireNarration(mc, 4, "narration_02_file_1");
        pas(2, () -> hb = t0 + 3650);   // n2 (4 s) : fondu de sortie (700 ms) à mi-course
        pas(3, () -> { shot(mc, "narration_02_file_sortie350"); journalNarration("narration_02_file_sortie350", "narration_02_file_1_sans"); });
        pas(2, () -> hb = t0 + 4200);   // n2 finie à 4 000 ms ; n3 attend 400 ms
        pas(3, () -> {
            shot(mc, "narration_02_file_ecart");
            verifier("écart de 400 ms : écran libre, n3 en file", Recit.debugEcran().isEmpty() && "n3".equals(Recit.debugFile()));
        });
        pas(2, () -> hb = t0 + 4400);
        pasQuand(RecitHud::pret, 2, () -> {
            verifier("n3 à l'écran à 4 400 ms (fin de n2 + 400 ms)", "n3".equals(Recit.debugEcran()) && Recit.debugFile().isEmpty());
            hb = t0 + 5400;
        });
        paireNarration(mc, 4, "narration_02_file_2");
        // ------------------------------------------------------------------------------- 3. « remplace » coupe l'écran et la file
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n4", NARR_FOULE, 20, "file", null, 0));
            recevoir(narration("n5", NARR_GARDE, 0, "file", null, 0));
            recevoir(narration("n6", NARR_CLOCHES, 0, "file", null, 0));
            verifier("n4 à l'écran, n5 et n6 en file", "n4".equals(Recit.debugEcran()) && "n5,n6".equals(Recit.debugFile()));
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        paireNarration(mc, 4, "narration_03_avant_remplace");
        pas(2, () -> {
            hb = t0 + 2000;
            recevoir(narration("n7", NARR_SILENCE, 0, "remplace", null, 0));
            verifier("remplace : file vidée, n7 prend la place", "n7".equals(Recit.debugEcran()) && Recit.debugFile().isEmpty());
            verifier("remplace : la durée de n7 part de son fondu d'entrée (après la coupe de 150 ms)",
                Recit.debugReste() == dureeAuto(NARR_SILENCE) + 150);
        });
        pas(2, () -> hb = t0 + 2075);   // n4 s'efface en 150 ms depuis son opacité du moment : 0,50 à 75 ms
        pas(3, () -> {
            shot(mc, "narration_03_coupe075");
            journalNarration("narration_03_coupe075", "narration_03_avant_remplace_sans");
            verifier("remplace : n4 coupée, opacité 0,50 à 75 ms", Math.abs(RecitHud.etatBanc().get("a").getAsDouble() - 0.5) < 0.01);
        });
        pas(2, () -> hb = t0 + 2150);
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 3150);
        paireNarration(mc, 4, "narration_03_remplace");
        pas(2, () -> hb = t0 + 2150 + dureeAuto(NARR_SILENCE) + 1000);
        pas(3, () -> {
            shot(mc, "narration_03_apres");
            verifier("après n7 : n5 et n6 ne passent jamais", Recit.debugEcran().isEmpty() && Recit.debugFile().isEmpty());
        });
        // ------------------------------------------------------------------------------- 4. tenue, puis narration_fin
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n8", NARR_TENUE, -1, "file", null, 0));
            verifier("n8 tenue", Recit.debugDuree("n8") == -1);
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 120_000);   // deux minutes plus tard : toujours là
        paireNarration(mc, 4, "narration_04_tenue");
        pas(2, () -> {
            verifier("tenue : garde-fou à 10 min, reste 480 s", Recit.debugReste() == 480_000);
            recevoir(fin("n8"));
        });
        pas(2, () -> hb = t0 + 120_350);
        pas(3, () -> {
            shot(mc, "narration_04_fin350");
            journalNarration("narration_04_fin350", "narration_04_tenue_sans");
            verifier("narration_fin n8 : fondu de sortie de 700 ms, 0,50 à 350 ms", Math.abs(RecitHud.etatBanc().get("a").getAsDouble() - 0.5) < 0.01);
        });
        pas(2, () -> hb = t0 + 120_700);
        pas(3, () -> {
            shot(mc, "narration_04_fin");
            verifier("narration_fin n8 : effacée en 700 ms", Recit.debugEcran().isEmpty());
        });
        // garde-fou de la tenue : effacée d'elle-même 10 min après le début de son fondu d'entrée
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n9", NARR_TENUE, -1, "file", null, 0));
        });
        pas(2, () -> hb = t0 + 599_999);
        pas(2, () -> verifier("tenue : encore là à 599 999 ms", "n9".equals(Recit.debugEcran())));
        pas(2, () -> hb = t0 + 600_000);
        pas(2, () -> verifier("tenue : effacée d'elle-même à 600 000 ms", Recit.debugEcran().isEmpty()));
        // id vide : tout effacer, écran (fondu de 300 ms) et file
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n10", NARR_FOULE, 30, "file", null, 0));
            recevoir(narration("n11", NARR_GARDE, 30, "file", null, 0));
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        pas(2, () -> {
            recevoir(fin(""));
            verifier("narration_fin vide : file vidée, n10 en fondu de sortie (700 ms)", "n10".equals(Recit.debugEcran())
                && Recit.debugFile().isEmpty() && Recit.debugReste() == 700);
        });
        pas(2, () -> hb = t0 + 1700);
        pas(3, () -> {
            shot(mc, "narration_05_tout_efface");
            verifier("narration_fin vide : écran libre après 700 ms", Recit.debugEcran().isEmpty());
        });
        pas(2, () -> hb = t0 + 10_000);
        pas(2, () -> verifier("narration_fin vide : n11 ne passe jamais", Recit.debugEcran().isEmpty() && !Recit.actif()));
        // ------------------------------------------------------------------------------- 5. les quatre couleurs (+ clé inconnue = blanc)
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(couleurs("n12"));
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        paireNarration(mc, 4, "narration_06_couleurs");
        pas(4, () -> { Recit.vider(); aller(mc, nX + 0.5, nY, nZ + 0.5, 20f, 12f); });   // la neige
        pas(12, () -> { });
        pas(2, () -> {
            t0 = hb;
            recevoir(couleurs("n13"));
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        paireNarration(mc, 4, "narration_06_couleurs_neige");
        pas(4, () -> { Recit.vider(); aller(mc, sX + 0.5, sY, sZ + 0.5, 20f, 4f); });
        pas(12, () -> { });
        // ------------------------------------------------------------------------------- 6. près de 1 000 caractères : pages
        pas(2, () -> {
            t0 = hb;
            recevoir(narration("n14", NARR_1000, 0, "file", null, 0));
            tranches1000 = Recit.debugTranches("n14");
            int n = Recit.debugPages("n14");
            long d1000 = dureeAutoPages(NARR_1000, "n14");
            verifier("998 caractères : durée auto " + dureeAuto(NARR_1000) + " ms + " + (n - 1) + " × 1 200 ms (fondus de "
                + "page) = " + d1000 + " ms", Recit.debugDuree("n14") == d1000);
            int parPage = Gabarit.NARRATION.lignesParPage(mc.getWindow().getHeight(),
                Gabarit.NARRATION.pas(Gabarit.NARRATION.facteur(mc.getWindow().getWidth(), mc.getWindow().getHeight())));
            verifier("998 caractères : " + n + " pages de " + parPage + " lignes au plus, fins de page "
                + java.util.Arrays.toString(java.util.Arrays.copyOf(tranches1000, n)) + " ms (fondus + prorata)",
                n >= 2 && tranches1000[n - 1] == d1000);
            StringBuilder decoupe = new StringBuilder();
            int courtes = 0;
            for (int k = 0; k < n; k++) {
                String pg = Recit.debugPage("n14", k);
                int l = 1;
                for (int i = 0; i < pg.length(); i++) if (pg.charAt(i) == '\n') l++;
                boolean finPar = Recit.debugFinParagraphe("n14", k);
                String nu = pg.replaceAll("<[^>]*>", "").strip();
                int z = nu.length() - 1;
                while (z >= 0 && "»)   ".indexOf(nu.charAt(z)) >= 0) z--;
                boolean finPhrase = z >= 0 && ".!?…:;".indexOf(nu.charAt(z)) >= 0;
                if (!finPar && !finPhrase || l > parPage) courtes++;
                decoupe.append(k > 0 ? " | " : "").append("p").append(k + 1).append(' ').append(l).append(" l.")
                    .append(finPar ? " ¶" : "");
            }
            journalNarrationTexte("998 caractères, pages : " + decoupe);
            verifier("998 caractères : " + parPage + " lignes au plus par page, chaque page finit une phrase ou un "
                + "paragraphe (" + decoupe + ")", courtes == 0);
            // tenue d'une page pleine : sa tranche moins ses fondus, au rythme de la règle (≈ 30 caractères par seconde)
            long tenue1 = tranches1000[0] - Gabarit.NARRATION.entreeMs() - Gabarit.NARRATION.pageMs();
            verifier("998 caractères : page 1 tenue " + tenue1 + " ms à pleine opacité", tenue1 >= 3_000);
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 2000);
        paireNarration(mc, 3, "narration_07_longue_p1");
        for (int k = 1; k < 9; k++) {   // pages suivantes, s'il y en a (le nombre dépend de l'allure)
            final int page = k;
            if (page == 1) {   // fondu de page (600 ms de chaque côté) : la page 1 qui s'efface, à mi-course
                pas(2, () -> { if (page < pagesLongue()) hb = t0 + tranches1000[0] - 300; });
                pas(3, () -> {
                    if (page >= pagesLongue()) return;
                    shot(mc, "narration_07_longue_p1_sortie300");
                    journalNarration("narration_07_longue_p1_sortie300", "narration_07_longue_p1_sans");
                    verifier("fin de page : opacité 0,50 à 300 ms de la tranche (fondu de page de 600 ms)",
                        Math.abs(RecitHud.etatBanc().get("a").getAsDouble() - 0.5) < 0.01);
                });
            }
            pas(2, () -> { if (page < pagesLongue()) hb = t0 + tranches1000[page - 1] + 1; });
            pasQuand(() -> page >= pagesLongue() || RecitHud.pret(), 2, () -> {
                if (page < pagesLongue()) hb = t0 + tranches1000[page - 1] + 800;   // fondu de page (600 ms) fini
            });
            pas(2, () -> {
                if (page < pagesLongue()) verifier("page " + (page + 1) + " à " + (tranches1000[page - 1] + 800) + " ms, "
                    + RecitHud.etatBanc().get("lignes").getAsInt() + " ligne(s) ≤ 3", Recit.debugPage() == page
                    && RecitHud.etatBanc().get("lignes").getAsInt() <= 3);
            });
            paireNarrationSi(mc, 3, String.format(Locale.ROOT, "narration_07_longue_p%d", page + 1), () -> page < pagesLongue());
        }
        // ------------------------------------------------------------------------------- 7. pos : 30 %, puis hors bornes (place par défaut)
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n15", NARR_POS, 0, "file", 30, 0));
            verifier("pos 30 pris", Recit.debugPos("n15") == 30);
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        paireNarration(mc, 4, "narration_08_pos30");
        pas(2, () -> verifier("pos 30 : centre du bloc à 30 % de la hauteur", Math.abs(RecitHud.etatBanc().get("cy").getAsDouble()
            - 0.30 * mc.getWindow().getHeight()) < 0.01));
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n16", NARR_POS, 0, "file", 150, 0));
            verifier("pos 150 hors bornes : place par défaut", Recit.debugPos("n16") == Recit.POS_DEFAUT);
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        paireNarration(mc, 4, "narration_08_pos_hors_bornes");
        pas(2, () -> {   // place par défaut de l'allure : bas du bloc ancré (V2 : 47 %), sinon centre du gabarit
            JsonObject e = RecitHud.etatBanc();
            int H = mc.getWindow().getHeight();
            Gabarit g = Gabarit.NARRATION;
            double attendu = g.bas() > 0 ? g.bas() * H - e.get("lignes").getAsInt() * e.get("P").getAsInt() / 2.0 : g.centre() * H;
            verifier("pos hors bornes : place par défaut de la narration (" + (g.bas() > 0 ? "bas du bloc à " + Math.round(100 * g.bas())
                : "centre à " + Math.round(100 * g.centre())) + " %)", Math.abs(e.get("cy").getAsDouble() - attendu) < 0.01);
        });
        // ------------------------------------------------------------------------------- 8. un mot plus large que la ligne
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n17", NARR_MOT_LONG, 0, "file", null, 0));
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        paireNarration(mc, 4, "narration_09_mot_long", "mot plus large que la ligne coupé par le moteur Java (contrat de "
            + "la narration) ; le moteur Python des maquettes ne coupe pas");
        pas(2, () -> {
            JsonObject e = RecitHud.etatBanc();
            int W = e.get("W").getAsInt();
            Gabarit g = Gabarit.NARRATION;
            double f = e.get("f").getAsDouble(), L = g.largeur(f, W), bloc = 0;
            boolean dansL = true;
            int n = 0;
            StringBuilder lettres = new StringBuilder(), lignes = new StringBuilder();
            // toutes les pages (narration courte : 3 lignes par page)
            for (int k = 0; k < Recit.debugPages("n17"); k++) {
                // la demande que RecitHud pose (largeurPose / equilibragePose du gabarit), pas une nouvelle mise en page
                TexteRaster.Mise m = TexteRaster.mettreEnPage(TexteRaster.Demande.bloc(Recit.debugPage("n17", k), g.police(),
                    g.preset(), g.cap(f), g.largeurPose(f, W), g.pas(f), g.contour(f), 0.0, 0.0, g.approche(f),
                    g.equilibragePose(), null));
                for (int i = 0; i < m.nbLignes; i++) {
                    dansL &= m.largeurs[i] <= L + 1e-6;   // avance ; l'encre déborde au plus du contour (2 px)
                    m.lignes[i].codePoints().filter(Character::isLetter).forEach(lettres::appendCodePoint);
                    lignes.append(n++ > 0 ? " | " : "").append(m.lignes[i]).append(String.format(Locale.ROOT, " (%.1f px)", m.largeurs[i]));
                }
                bloc = Math.max(bloc, m.largeurBloc);
            }
            journalNarrationTexte("mot long, lignes : " + lignes);
            verifier("mot plus large que la ligne : coupé, chaque ligne ≤ L = " + String.format(Locale.ROOT, "%.1f", L)
                + " px (avance, l'encre déborde au plus du contour), bloc de " + String.format(Locale.ROOT, "%.1f", bloc) + " px, aucune lettre "
                + "perdue (" + n + " lignes posées)", dansL && n >= 2 && lettres.toString().equals(lettres(NARR_MOT_LONG)));
        });
        // ------------------------------------------------------------------------------- 9. rattrapage (ecoule)
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n18", "Le portail s'ouvre.", 90, "file", null, 23_417));
            verifier("rattrapage : reste D − ecoule = 66 583 ms", Recit.debugReste() == 66_583);
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        paireNarration(mc, 4, "narration_10_rattrapage");
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("n19", NARR_1000, 0, "file", null, 17_000));
            long[] tr = Recit.debugTranches("n19");
            int attendue = 0;
            while (tr != null && attendue < tr.length - 2 && 17_000 >= tr[attendue]) attendue++;
            long d = dureeAutoPages(NARR_1000, "n19");
            verifier("rattrapage à 17 s d'une narration de " + d + " ms : page " + (attendue + 1)
                + " d'emblée (sa tranche contient 17 s), reste " + (d - 17_000) + " ms",
                Recit.debugPage() == attendue && Recit.debugReste() == d - 17_000);
        });
        pasQuand(RecitHud::pret, 3, () -> hb = t0 + 300);   // fondu d'entrée normal (600 ms) sur la page de départ : 0,75 à 300 ms
        pas(3, () -> {
            shot(mc, "narration_10_rattrapage_t300");
            journalNarration("narration_10_rattrapage_t300", "narration_10_rattrapage_sans");
            verifier("rattrapage : fondu d'entrée normal sur la page de départ (0,75 à 300 ms)",
                Math.abs(RecitHud.etatBanc().get("a").getAsDouble() - 0.75) < 0.01);
        });
        pas(2, () -> hb = t0 + 1000);
        paireNarration(mc, 4, "narration_10_rattrapage_page2");
        pas(2, () -> {
            Recit.vider();
            verifier("rattrapage, ecoule ≥ D : ignorée", !NarrationClient.recevoir(narration("n20", RECIT_COURT, 10, "file", null, 10_000)));
            verifier("tenue rattrapée, ecoule ≥ 10 min : ignorée",
                !NarrationClient.recevoir(narration("n21", RECIT_COURT, -1, "file", null, 600_000)));
            verifier("tenue rattrapée : garde-fou 600 000 − ecoule",
                NarrationClient.recevoir(narration("n22", RECIT_COURT, -1, "file", null, 590_000)) && Recit.debugReste() == 10_000);
        });
        // ------------------------------------------------------------------------------- 10. protocole : ce qui est ignoré, durées, file pleine
        pas(2, () -> {
            Recit.vider();
            String ok = narration("x1", RECIT_COURT, 0, "file", null, 0);
            verifier("JSON illisible : ignoré", !NarrationClient.recevoir("{pas du json"));
            verifier("v = 2 : ignoré", !NarrationClient.recevoir(ok.replace("\"v\":1", "\"v\":2")));
            verifier("v absent : ignoré", !NarrationClient.recevoir(ok.replace("\"v\":1,", "")));
            verifier("t inconnu : ignoré", !NarrationClient.recevoir(ok.replace("\"t\":\"narration\"", "\"t\":\"annonce\"")));
            verifier("id absent : ignoré", !NarrationClient.recevoir(ok.replace("\"id\":\"x1\",", "")));
            verifier("id vide : ignoré", !NarrationClient.recevoir(ok.replace("\"id\":\"x1\"", "\"id\":\"\"")));
            verifier("sans texte lisible : ignorée", !NarrationClient.recevoir(narration("x2", "   ", 0, "file", null, 0)));
            verifier("segs absent : ignorée", !NarrationClient.recevoir("{\"v\":1,\"t\":\"narration\",\"id\":\"x9\",\"s\":0,\"mode\":\"file\"}"));
            verifier("aucun texte posé après les refus", !Recit.actif());
            verifier("champ inconnu : ignoré, narration prise",
                NarrationClient.recevoir(ok.replace("\"mode\"", "\"futur\":{\"a\":[1,2]},\"mode\"")));
            verifier("doublon (id à l'écran) : ignoré", !NarrationClient.recevoir(ok) && Recit.debugFile().isEmpty());
            verifier("mode inconnu : file", NarrationClient.recevoir(narration("x3", RECIT_COURT, 0, "bidon", null, 0))
                && "x3".equals(Recit.debugFile()));
            verifier("doublon (id en file) : ignoré", !NarrationClient.recevoir(narration("x3", NARR_GARDE, 0, "file", null, 0))
                && "x3".equals(Recit.debugFile()));
            verifier("narration_fin d'un id en file : retiré", NarrationClient.recevoir(fin("x3")) && Recit.debugFile().isEmpty());
            verifier("narration_fin d'un id inconnu : rien", NarrationClient.recevoir(fin("n999")) && "x1".equals(Recit.debugEcran()));
            verifier("narration_fin sans champ id : ignoré", !NarrationClient.recevoir("{\"v\":1,\"t\":\"narration_fin\"}")
                && "x1".equals(Recit.debugEcran()));
            // durées : 0, -1 et 2 à 180 ; toute autre valeur = automatique
            long auto = dureeAuto(RECIT_COURT);
            String[] s = {"0", "-1", "2", "180", "1", "181", "-2", "2.5", "\"30\"", "true", "null"};
            long[] attendu = {auto, -1, 2_000, 180_000, auto, auto, auto, auto, auto, auto, auto};
            for (int i = 0; i < s.length; i++) {
                Recit.vider();
                String id = "d" + i;
                NarrationClient.recevoir(narration(id, RECIT_COURT, 0, "file", null, 0).replace("\"s\":0", "\"s\":" + s[i]));
                verifier("s = " + s[i] + " → " + (attendu[i] < 0 ? "tenue" : attendu[i] + " ms"), Recit.debugDuree(id) == attendu[i]);
            }
            // pos : entier 0 à 100, sinon la place par défaut
            String[] p = {"0", "100", "-1", "101", "40.5", "\"40\""};
            int[] pAttendu = {0, 100, Recit.POS_DEFAUT, Recit.POS_DEFAUT, Recit.POS_DEFAUT, Recit.POS_DEFAUT};
            for (int i = 0; i < p.length; i++) {
                Recit.vider();
                String id = "p" + i;
                NarrationClient.recevoir(narration(id, RECIT_COURT, 0, "file", 50, 0).replace("\"pos\":50", "\"pos\":" + p[i]));
                verifier("pos = " + p[i] + " → " + (pAttendu[i] < 0 ? "défaut" : pAttendu[i]), Recit.debugPos(id) == pAttendu[i]);
            }
            // file pleine : 8 en attente au plus, une 9e fait sortir la plus ancienne en attente
            Recit.vider();
            NarrationClient.recevoir(narration("q0", NARR_FOULE, 30, "file", null, 0));
            for (int i = 1; i <= 10; i++) NarrationClient.recevoir(narration("q" + i, NARR_GARDE, 0, "file", null, 0));
            verifier("file pleine : q1 et q2 sortis, 8 en attente", "q0".equals(Recit.debugEcran())
                && "q3,q4,q5,q6,q7,q8,q9,q10".equals(Recit.debugFile()));
            verifier("remplace lu comme le serveur (casse, espaces) : file vidée",
                NarrationClient.recevoir(narration("q11", NARR_SILENCE, 0, " REMPLACE ", null, 0)) && Recit.debugFile().isEmpty());
            Recit.vider();
        });
        // ------------------------------------------------------------------------------- 11. place et visibilité (étape 2c) en grand
        pas(2, () -> noir(true));
        pas(6, () -> { });
        sceneNarration(mc, "narration_12_noir", "nv1", NARR_GONG);
        pas(2, () -> noirTexte("Vous perdez connaissance…"));
        pas(6, () -> { });
        sceneNarration(mc, "narration_12_noir_texte", "nv2", NARR_GONG);
        pas(2, () -> verifier("écran noir avec texte : haut du bloc ≥ bas du texte noir + 2 interlignes", sousLeNoir()));
        pas(2, () -> noir(false));
        pas(4, () -> KoClient.update(new KoPayload((byte) 2, (byte) 0, (short) 167, (short) 300, false, (byte) 0, 0f,
            (short) 0, (short) 0, false, false)));
        pas(30, () -> { });   // le filtre du KO s'installe
        sceneNarration(mc, "narration_12_inconscient", "nv3", NARR_GONG);
        pas(2, () -> verifier("inconscient : haut du bloc ≥ bas du bloc KO + 2 interlignes", sousLeKo()));
        pas(4, () -> KoClient.update(new KoPayload((byte) 1, (byte) 0, (short) 45, (short) 60, false, (byte) 0, 0f,
            (short) 0, (short) 0, false, false)));
        pas(24, () -> { });
        sceneNarration(mc, "narration_12_a_terre", "nv4", NARR_GONG);
        pas(2, () -> verifier("à terre : bloc au-dessus du groupe « À terre » (une interligne au moins)", auDessusATerre()));
        pas(4, KoClient::clear);
        pas(20, () -> { });
        pas(8, () -> mc.setScreenAndShow(new ChatScreen("", false)));
        pas(8, () -> { });
        sceneNarration(mc, "narration_12_tchat", "nv5", NARR_GONG);
        pas(2, () -> mc.setScreenAndShow(null));
        // F1 « CLEAN » (masquée, le minuteur continue) puis « BARS » (visible, après les bandes)
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration("nv6", NARR_GONG, 0, "file", null, 0));
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1000);
        pas(2, () -> CinemaBars.INSTANCE.toggle());   // HUD → CLEAN
        pas(16, () -> {
            shot(mc, "narration_12_cinema_clean");
            verifier("F1 « CLEAN » : narration masquée, minuteur qui continue", !RecitHud.visible() && Recit.actif());
        });
        pas(2, () -> CinemaBars.INSTANCE.toggle());   // CLEAN → BARS
        paireNarration(mc, 20, "narration_12_cinema_bars");
        pas(2, () -> CinemaBars.INSTANCE.toggle());   // BARS → HUD
        // une stèle lue (lot 4, allure des stèles), une narration passe devant (allure de la narration), la stèle revient
        pas(12, Recit::vider);
        // la concentration commence (bords assombris, lot 4 : Recit.concentration) et la plaque est lue
        pas(2, () -> {
            t0 = hb;
            Recit.concentration(true);
            Recit.lire("plaque:serment", Recit.segments(PLAQUE_A));
        });
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 125);   // entrée de 250 ms : texte 0,75 (ease-out), bords 0,50
        pas(3, () -> {
            shot(mc, "narration_12_stele_t125");
            journalNarration("narration_12_stele_t125", "narration_12_stele_sans");
            JsonObject e = RecitHud.etatBanc();
            verifier("stèle : entrée de 250 ms (0,75 à 125 ms), bords de la concentration à mi-course (0,09 = 0,18 × 0,5)",
                Math.abs(e.get("a").getAsDouble() - 0.75) < 0.01 && Math.abs(e.get("bords").getAsDouble() - 0.09) < 0.002);
        });
        pas(2, () -> hb = t0 + 500);
        paireNarration(mc, 3, "narration_12_stele");
        pas(2, () -> {
            verifierAllure("plaque:serment", Gabarit.STELE);
            verifier("stèle : tenue sans minuterie, bords assombris à 0,18 pendant la concentration",
                Math.abs(RecitHud.etatBanc().get("bords").getAsDouble() - Gabarit.BORDS_CONCENTRATION) < 0.001);
        });
        pas(2, () -> {
            hb = t0 + 1000;
            recevoir(narration("nv7", RECIT_COURT, 4, "file", null, 0));
        });
        pas(3, () -> hb = t0 + 1075);   // la stèle s'efface (coupe de 150 ms) : la narration passe devant
        pas(3, () -> { shot(mc, "narration_12_stele_coupe075"); journalNarration("narration_12_stele_coupe075", "narration_12_stele_sans"); });
        pas(3, () -> hb = t0 + 1200);
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 1900);
        paireNarration(mc, 3, "narration_12_devant_stele");
        pas(2, () -> {
            verifierAllure("nv7", Gabarit.NARRATION);
            verifier("narration devant : la stèle est cachée (une seule place au centre), vignette = la plus forte des deux",
                "nv7".equals(RecitHud.etatBanc().get("id").getAsString()));
        });
        pas(2, () -> hb = t0 + 5000);
        pasQuand(RecitHud::pret, 2, () -> hb = t0 + 5400);
        paireNarration(mc, 3, "narration_12_stele_retour");
        pas(2, () -> verifierAllure("plaque:serment", Gabarit.STELE));
        // fin de la concentration : texte et bords s'en vont en 350 ms (0,50 à 175 ms)
        pas(2, () -> {
            hb = t0 + 6000;
            Recit.cacher("plaque:serment");
            Recit.concentration(false);
        });
        pas(2, () -> hb = t0 + 6175);
        pas(3, () -> {
            shot(mc, "narration_12_stele_fin175");
            journalNarration("narration_12_stele_fin175", "narration_12_stele_retour_sans");
            JsonObject e = RecitHud.etatBanc();
            verifier("stèle : sortie de 350 ms (0,50 à 175 ms), bords 0,09", Math.abs(e.get("a").getAsDouble() - 0.5) < 0.01
                && Math.abs(e.get("bords").getAsDouble() - 0.09) < 0.002);
        });
        pas(2, () -> hb = t0 + 6400);
        pas(3, () -> {
            shot(mc, "narration_12_stele_fin");
            verifier("stèle : effacée, bords revenus", !Recit.actif() && RecitHud.etatBanc().get("bords").getAsDouble() == 0.0);
        });
        // tailles : 1366×768 (échelle 2), 2560×1440 (échelle 3), forêt de jour
        pas(4, () -> {
            Recit.vider();
            cmd(mc, "time set 6000");
            aller(mc, oX, oY, oZ, 20f, -6f);
            mc.getWindow().setWindowed(1366, 768);
        });
        pas(16, () -> { });
        sceneNarration(mc, "narration_13_1366", "nt1", NARR_GONG);
        pas(2, () -> verifierAllure("nt1", Gabarit.NARRATION));
        pas(4, () -> {
            mc.options.guiScale().set(3);
            mc.resizeGui();
            mc.getWindow().setWindowed(2560, 1440);
        });
        pas(16, () -> journalNarrationTexte("fenêtre demandée 2560×1440, obtenue " + mc.getWindow().getWidth() + "×"
            + mc.getWindow().getHeight() + " (échelle " + mc.getWindow().getGuiScale() + ")"));
        sceneNarration(mc, "narration_13_1440_e3", "nt2", NARR_GONG);
        pas(2, () -> verifierAllure("nt2", Gabarit.NARRATION));
        pas(4, () -> {
            mc.getWindow().setWindowed(largeurAvant, hauteurAvant);
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
        pas(16, () -> { });
        // ------------------------------------------------------------------------------- 12. planche d1 : la variante V1, en jeu
        pas(2, () -> NarrationClient.debugGabarit = Gabarit.NARRATION_V1);
        sceneNarration(mc, "narration_14_v1_jour", "w1", NARR_GONG);
        pas(2, () -> verifierAllure("w1", Gabarit.NARRATION_V1));
        pas(4, () -> { Recit.vider(); aller(mc, sX + 0.5, sY, sZ + 0.5, 20f, 4f); });
        pas(12, () -> { });
        sceneNarration(mc, "narration_14_v1_salle", "w2", NARR_GONG);
        pas(4, () -> { Recit.vider(); aller(mc, nX + 0.5, nY, nZ + 0.5, 20f, 12f); });
        pas(12, () -> { });
        sceneNarration(mc, "narration_14_v1_neige", "w3", NARR_GONG);
        pas(4, () -> {
            Recit.vider();
            aller(mc, oX, oY, oZ, 20f, -6f);
            mc.getWindow().setWindowed(1366, 768);
        });
        pas(16, () -> { });
        sceneNarration(mc, "narration_14_v1_1366", "w4", NARR_GONG);
        pas(2, () -> verifierAllure("w4", Gabarit.NARRATION_V1));
        pas(4, () -> mc.getWindow().setWindowed(2560, 1440));
        pas(16, () -> { });
        sceneNarration(mc, "narration_14_v1_1440", "w5", NARR_GONG);
        pas(2, () -> verifierAllure("w5", Gabarit.NARRATION_V1));
        pas(4, () -> {
            NarrationClient.debugGabarit = null;
            Recit.vider();
            mc.getWindow().setWindowed(largeurAvant, hauteurAvant);
        });
        pas(16, () -> aller(mc, sX + 0.5, sY, sZ + 0.5, 20f, 4f));
        pas(12, () -> { });
        // ------------------------------------------------------------------------------- 12 bis. voyelles à macron (Philosopher Bold : composées)
        sceneNarration(mc, "narration_16_macrons", "nm1", NARR_MACRONS);
        pas(2, () -> verifier("voyelles à macron : composées (lettre + U+00AF), aucun glyphe manquant",
            TexteRaster.mettreEnPage(Gabarit.NARRATION.bloc(NARR_MACRONS, 1.0, 1920, 0, 0)).manquants.isEmpty()));
        // ------------------------------------------------------------------------------- 13. le vrai réseau (serveur intégré)
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            reseauDeclare = null;
            var srv = mc.getSingleplayerServer();
            if (srv == null) {
                journalNarrationTexte("pas de serveur intégré : chemin réseau sauté");
                return;
            }
            UUID u = mc.player.getUUID();
            String json = narration("n30", NARR_SUITE, 0, "file", null, 0);
            srv.execute(() -> {
                var sp = srv.getPlayerList().getPlayer(u);
                if (sp == null) return;
                reseauDeclare = net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(sp, CanalJson.NARRATION);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, new CanalJson(CanalJson.NARRATION, json));
            });
        });
        pasQuand(RecitHud::pret, 4, () -> {
            verifier("réseau : le client déclare reborn:narration au serveur (canSend, comme getListeningPluginChannels)",
                Boolean.TRUE.equals(reseauDeclare));
            verifier("réseau : n30 reçue par le canal et montrée", "n30".equals(Recit.debugEcran()));
            hb = t0 + 1000;
        });
        paireNarration(mc, 4, "narration_15_reseau");
        pas(2, () -> {
            journalNarrationTexte("vérifications : " + verifs + ", hors cible : " + ecarts + " ; " + RecitHud.bilan());
            restaurerCentre(mc);
        });
    }

    /** Une narration reçue (file, durée auto), horloge figée : la texture se rend, puis pleine 1 s plus tard ; paire de captures. */
    private static void sceneNarration(Minecraft mc, String nom, String id, String balise) {
        pas(2, () -> {
            Recit.vider();
            t0 = hb;
            recevoir(narration(id, balise, 0, "file", null, 0));
        });
        pasQuand(RecitHud::pret, 2, () -> { notePret(id); hb = t0 + 1000; });
        paireNarration(mc, 4, nom);
    }

    /** Pages de la narration longue (0 tant qu'elle n'est pas reçue). */
    private static int pagesLongue() { return tranches1000 == null ? 0 : tranches1000.length - 1; }

    /**
     * L'allure posée à la dernière image est celle du gabarit : nom, capitales, interligne, largeur, centre (sans règle
     * de place active), préréglage, rayon du contour ; journalisée en clair.
     */
    private static void verifierAllure(String id, Gabarit g) {
        JsonObject e = RecitHud.etatBanc();
        int W = e.get("W").getAsInt(), H = e.get("H").getAsInt();
        double f = g.facteur(W, H);
        boolean figee = e.get("figee").getAsBoolean();
        double L = figee ? TexteRaster.SANS_LIMITE : g.largeur(f, W);
        JsonObject pr = e.getAsJsonObject("preset");
        double cyVoulu = g.bas() > 0 && figee ? g.bas() * H - e.get("lignes").getAsInt() * g.pas(f) / 2.0 : g.centre() * H;
        boolean ok = id.equals(e.get("id").getAsString()) && g.nom().equals(e.get("gabarit").getAsString())
            && figee == g.coupesFigees()
            && Math.abs(e.get("cap").getAsDouble() - g.cap(f)) < 1e-9 && e.get("P").getAsInt() == g.pas(f)
            && Math.abs(e.get("L").getAsDouble() - L) < 1e-6 && Math.abs(e.get("rayon").getAsDouble() - g.contour(f)) < 1e-9
            && Math.abs(e.get("cy").getAsDouble() - cyVoulu) < 0.75
            && g.preset().nom.equals(pr.get("nom").getAsString());
        verifier(String.format(Locale.ROOT, "%s : allure « %s » en %d×%d — f %.3f, capitales %.2f px, interligne %d px, "
                + "largeur max %.1f px%s, centre %.1f px (%s), contour %.4f px à %.0f %%, ombre %s à %.0f %%, bloc %.1f px "
                + "sur %d lignes", id, g.nom(), W, H, f, g.cap(f), g.pas(f), g.largeur(f, W), figee ? " (coupes figées à 1080p)" : "",
            e.get("cy").getAsDouble(), g.bas() > 0 && figee ? String.format(Locale.ROOT, "bas du bloc à %.0f %%", 100 * g.bas())
                : String.format(Locale.ROOT, "%.0f %%", 100 * g.centre()), g.contour(f), 100 * g.preset().contour,
            g.ombre() == null ? "dure" : "douce " + g.ombrePx(f), 100 * g.preset().ombre, e.get("largeur_bloc").getAsDouble(),
            e.get("lignes").getAsInt()), ok);
    }

    /** Écran noir avec texte : haut de la couche du bloc sous le bas du texte noir + 2 interlignes (à l'arrondi près). */
    private static boolean sousLeNoir() {
        JsonObject e = RecitHud.etatBanc();
        int bas = fr.reborn.hud.staff.EffetsStaff.basTexteNoirPx();
        double haut = e.get("cy").getAsDouble() - e.get("lignes").getAsInt() * e.get("P").getAsInt() / 2.0;
        journalNarrationTexte("noir : bas du texte " + bas + " px, haut du bloc " + haut + " px, interligne " + e.get("P"));
        return bas >= 0 && haut >= bas + 2 * e.get("P").getAsInt() - 1;
    }

    /** Inconscient : haut du bloc sous le bas du bloc KO + 2 interlignes (ou bloc tenu dans l'écran). */
    private static boolean sousLeKo() {
        JsonObject e = RecitHud.etatBanc();
        int bas = fr.reborn.hud.ko.KoHud.basInconscientPx(), P = e.get("P").getAsInt(), n = e.get("lignes").getAsInt();
        double haut = e.get("cy").getAsDouble() - n * P / 2.0, basBloc = haut + n * P;
        journalNarrationTexte("inconscient : bas du bloc KO " + bas + " px, bloc de " + haut + " à " + basBloc + " px");
        return bas >= 0 && (haut >= bas + 2 * P - 1 || basBloc >= e.get("H").getAsInt() - P - 1);
    }

    /** À terre : bas du bloc au-dessus du groupe « À terre » d'une interligne au moins. */
    private static boolean auDessusATerre() {
        JsonObject e = RecitHud.etatBanc();
        int haut = fr.reborn.hud.ko.KoHud.hautATerrePx(), P = e.get("P").getAsInt(), n = e.get("lignes").getAsInt();
        double basBloc = e.get("cy").getAsDouble() + n * P / 2.0;
        journalNarrationTexte("à terre : haut du groupe « À terre » " + haut + " px, bas du bloc " + basBloc + " px, centre "
            + e.get("cy") + " px");
        return haut >= 0 && basBloc <= haut - P + 1;
    }

    /** Délai réel entre la réception et la texture prête (fil du texte), au journal. */
    private static void notePret(String id) {
        journalNarrationTexte("« " + id + " » : texture prête " + (Util.getMillis() - recuA) + " ms après la réception");
    }

    /** Message {@code narration} tel que le serveur l'écrit ({@code Narrations.json}) : segments depuis un texte balisé. */
    private static String narration(String id, String balise, int s, String mode, Integer pos, long ecoule) {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("t", "narration");
        o.addProperty("id", id);
        JsonArray a = new JsonArray();
        for (Recit.Segment g : Recit.segments(balise)) {
            JsonArray x = new JsonArray();
            x.add(g.texte());
            x.add(g.couleur());
            a.add(x);
        }
        o.add("segs", a);
        o.addProperty("s", s);
        o.addProperty("mode", mode);
        if (pos != null) o.addProperty("pos", pos);
        if (ecoule > 0) o.addProperty("ecoule", ecoule);
        return o.toString();
    }

    /** Les quatre couleurs du contrat, et une clé inconnue (« bleu » : blanc). */
    private static String couleurs(String id) {
        return "{\"v\":1,\"t\":\"narration\",\"id\":\"" + id + "\",\"segs\":[[\"Le \",\"\"],[\"sang\",\"rouge\"],"
            + "[\" sur la pierre, l'\",\"\"],[\"or\",\"or\"],[\" du serment, les \",\"\"],[\"ombres\",\"ombre\"],"
            + "[\" du soir, la \",\"\"],[\"cendre\",\"gris\"],[\" des lanternes. Et la \",\"\"],[\"neige\",\"bleu\"],"
            + "[\" reste blanche.\",\"\"]],\"s\":0,\"mode\":\"file\"}";
    }

    private static String fin(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("t", "narration_fin");
        o.addProperty("id", id);
        return o.toString();
    }

    /** Durée automatique du contrat sur le texte tel que le serveur l'envoie (points de code des segments). */
    private static long dureeAuto(String balise) {
        int n = 0;
        for (Recit.Segment g : Recit.segments(balise)) n += g.texte().codePointCount(0, g.texte().length());
        return Math.max(4_000L, Math.min(60_000L, 1_300L + n * 1_000L / 30));
    }

    /** Durée automatique d'une narration en plusieurs pages : la base, plus deux fondus de page par page en plus. */
    private static long dureeAutoPages(String balise, String id) {
        return dureeAuto(balise) + Math.max(0, Recit.debugPages(id) - 1) * 2L * Gabarit.NARRATION.pageMs();
    }

    /** Un message passé au vrai receveur, comme s'il arrivait du canal ; le journal dit s'il est pris. */
    private static void recevoir(String json) {
        recuA = Util.getMillis();
        boolean pris = NarrationClient.recevoir(json);
        journalNarrationTexte((pris ? "pris" : "IGNORÉ") + " : " + (json.length() > 140 ? json.substring(0, 140) + "…" : json)
            + " → " + Recit.bilan());
    }

    private static void verifier(String quoi, boolean ok) {
        verifs++;
        if (!ok) ecarts++;
        journalNarrationTexte((ok ? "OK · " : "ÉCART · ") + quoi + (ok ? "" : " — " + Recit.bilan() + " ; " + RecitHud.bilan()));
    }

    /** Fond nu puis la narration, même image ; journal de la demande posée (comparaison au moteur des maquettes). */
    private static void paireNarration(Minecraft mc, int attente, String nom) {
        paireNarrationSi(mc, attente, nom, () -> true, null);
    }

    /** {@link #paireNarration} ; {@code sansReference} : pourquoi le moteur des maquettes ne s'y compare pas. */
    private static void paireNarration(Minecraft mc, int attente, String nom, String sansReference) {
        paireNarrationSi(mc, attente, nom, () -> true, sansReference);
    }

    /** {@link #paireNarration} si {@code si} est vrai au moment de la capture (pages dont le nombre dépend de l'allure). */
    private static void paireNarrationSi(Minecraft mc, int attente, String nom, java.util.function.BooleanSupplier si) {
        paireNarrationSi(mc, attente, nom, si, null);
    }

    private static void paireNarrationSi(Minecraft mc, int attente, String nom, java.util.function.BooleanSupplier si,
                                         String sansReference) {
        pas(attente, () -> { if (si.getAsBoolean()) RecitHud.debugMasque = true; });
        pas(3, () -> { if (si.getAsBoolean()) shot(mc, nom + "_sans"); });
        pas(1, () -> RecitHud.debugMasque = false);
        pas(3, () -> {
            if (!si.getAsBoolean()) return;
            shot(mc, nom);
            journalNarration(nom, nom + "_sans", sansReference);
        });
    }

    private static void journalNarration(String cas, String sans) { journalNarration(cas, sans, null); }

    /** Demande posée (une ligne JSON de {@code run/narration_banc.txt}, lue par {@code tools/centre_compare.py --serie narration}). */
    private static void journalNarration(String cas, String sans, String sansReference) {
        JsonObject o = RecitHud.etatBanc();
        o.addProperty("cas", cas);
        o.addProperty("sans", sans);
        o.addProperty("viseur_masque", RecitHud.masqueVanilla());
        o.addProperty("recit", Recit.bilan());
        if (sansReference != null) o.addProperty("sans_reference", sansReference);
        JOURNAL_NARRATION.append(o).append('\n');
    }

    /** Lettres d'un texte balisé (sans balises) : un mot coupé ne doit en perdre aucune. */
    private static String lettres(String balise) {
        StringBuilder s = new StringBuilder();
        for (Recit.Segment g : Recit.segments(balise)) g.texte().codePoints().filter(Character::isLetter).forEach(s::appendCodePoint);
        return s.toString();
    }

    private static void journalNarrationTexte(String s) {
        System.out.println("[narration-banc] " + s);
        JsonObject o = new JsonObject();
        o.addProperty("note", s);
        JOURNAL_NARRATION.append(o).append('\n');
    }

    /* ---------------------------------------------------------------- serveur simulé (réflexion, comme les autres bancs) */

    /** Mode animation (indicateur « Mode animation · journalisé ») ; false : aucun mode. */
    private static void staff(boolean anim) {
        appeler("fr.reborn.hud.staff.StaffClient", "receive", "{\"t\":\"mode\",\"staff\":false,\"anim\":" + anim + "}");
    }

    /** Mode staff (indicateur « Mode staff · hors RP », maquette b2). */
    private static void modeStaff() {
        appeler("fr.reborn.hud.staff.StaffClient", "receive", "{\"t\":\"mode\",\"staff\":true,\"anim\":false}");
    }

    private static void noir(boolean on) {
        appeler("fr.reborn.hud.staff.EffetsStaff", "recevoir", "{\"t\":\"noir\",\"on\":" + on + ",\"fondu\":0}");
    }

    /** Écran noir du staff qui porte un texte (centré, filet d'or dessous). */
    private static void noirTexte(String texte) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "noir");
        o.addProperty("on", true);
        o.addProperty("fondu", 0);
        o.addProperty("texte", texte);
        appeler("fr.reborn.hud.staff.EffetsStaff", "recevoir", o.toString());
    }

    private static void annonce(String texte) {
        appeler("fr.reborn.hud.communication.CommClient", "recevoir", "{\"t\":\"annonce\",\"type\":\"normale\",\"titre\":\"\","
            + "\"texte\":[[\"" + texte + "\",\"\"]],\"signe\":\"Hokage\",\"s\":20}");
    }

    private static void finAnnonce() {
        appeler("fr.reborn.hud.communication.CommClient", "recevoir", "{\"t\":\"annonce_fin\"}");
    }

    private static void appeler(String classe, String methode, String json) {
        try {
            var m = Class.forName(classe).getDeclaredMethod(methode, String.class);
            m.setAccessible(true);
            m.invoke(null, json);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /* ================================================================ compat : vrai reborn-ost dans run/mods */

    private static void programmeCompat(Minecraft mc) {
        pas(50, () -> {
            String v = FabricLoader.getInstance().getModContainer("reborn-ost")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("absent");
            System.out.println("[ost-banc] compat : reborn-ost " + v + " ; état publié (" + OstLien.CLE + ") : "
                + OstLien.present() + " ; " + OstLien.CLE_RENDU + " déposée : "
                + (FabricLoader.getInstance().getObjectShare().get(OstLien.CLE_RENDU) != null));
            reel("lire");
        });
        pas(24, () -> { journalOst("lecture"); shot(mc, "ost_compat_01_lecture"); });
        pas(2, () -> reel("togglePause"));
        pas(16, () -> { journalOst("pause"); shot(mc, "ost_compat_02_pause"); });
        pas(2, () -> reel("togglePause"));
        pas(16, () -> { journalOst("reprise"); shot(mc, "ost_compat_03_reprise"); });
        pas(2, () -> reel("stop"));
        pas(16, () -> { journalOst("arrêt"); shot(mc, "ost_compat_04_arret"); });
        pas(2, () -> restaurer(mc));
        pas(20, mc::stop);
    }

    private static void journalOst(String etape) {
        System.out.println("[ost-banc] compat " + etape + " : piste « " + OstLien.piste() + " », titre « " + OstLien.titre()
            + " », état " + OstLien.etat() + ", audible " + OstLien.audible() + " ; lecteur reborn-hud : "
            + (OstHud.dessine() ? "DESSINÉ" : "rien") + " (" + OstHud.bilan() + ")");
    }

    /** Pilote le vrai reborn-ost (0.2.5 ou 0.2.6, mêmes classes publiques) : lire la première piste, pause, arrêt. */
    private static void reel(String quoi) {
        try {
            Class<?> client = Class.forName("fr.reborn.ost.RebornOstClient");
            Object moteur = client.getMethod("audioEngine").invoke(null);
            Class<?> lecture = Class.forName("fr.reborn.ost.audio.OstPlayback");
            Object instance = lecture.getField("INSTANCE").get(null);
            switch (quoi) {
                case "lire" -> {
                    Object biblio = client.getMethod("library").invoke(null);
                    Object config = client.getMethod("config").invoke(null);
                    List<?> pistes = (List<?>) biblio.getClass().getMethod("allTracks").invoke(biblio);
                    System.out.println("[ost-banc] compat : " + pistes.size() + " piste(s) dans run/reborn/ost");
                    if (pistes.isEmpty()) return;
                    for (var m : lecture.getMethods()) {
                        if (m.getName().equals("play") && m.getParameterCount() == 4) m.invoke(instance, moteur, config, pistes, 0);
                    }
                }
                case "togglePause" -> moteur.getClass().getMethod("togglePause").invoke(moteur);
                case "stop" -> {
                    for (var m : lecture.getMethods()) {
                        if (m.getName().equals("stop") && m.getParameterCount() == 1) m.invoke(instance, moteur);
                    }
                }
                default -> { }
            }
        } catch (ReflectiveOperationException e) {
            System.out.println("[ost-banc] compat : reborn-ost injoignable (" + e + ")");
        }
    }

    /* ================================================================ outils */

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    /* ================================================================ lot 2 (2e) : outil « Narration » du Poste de garde */

    /** Cas de l'outil montrés aux deux tailles, puis ceux des grades (1080p). */
    private static final String[] GARDE_CAS = {"narration", "narration_couleurs", "narration_tous", "narration_actives",
        "narration_plein_ecran"};
    private static final String[] GARDE_GRADES = {"narration_helper", "narration_anim"};

    /**
     * L'outil « Narration » du volet, serveur simulé ({@code staff/StaffDemo}), de jour dans la forêt : vide, rempli avec
     * des mots de couleur et l'aperçu à taille réelle, la confirmation de « tout le serveur », la liste « En cours » avec
     * deux narrations, l'aperçu « Plein écran » (volet caché) ; en 1920×1080 puis 1366×768 ; enfin un Helper (un joueur seulement) et un Helper en mode animation
     * (un joueur ou ici). Captures {@code garde_narration_<taille>_<cas>.png}.
     */
    private static void programmeGarde(Minecraft mc) {
        pas(4, () -> {
            Recit.vider();
            viderBasDroite();
            arret();
            OstHud.debugBarres = null;
            cmd(mc, "time set 6000");
            if (!Double.isNaN(oX)) aller(mc, oX, oY, oZ, 20f, -6f);
        });
        for (int[] f : new int[][]{{1920, 1080}, {1366, 768}}) {
            String tag = f[0] + "x" + f[1];
            pas(2, () -> {
                if (mc.getWindow().getScreenWidth() != f[0] || mc.getWindow().getScreenHeight() != f[1]) mc.getWindow().setWindowed(f[0], f[1]);
            });
            pas(16, () -> { });
            for (String cas : GARDE_CAS) sceneGarde(mc, cas, "garde_narration_" + tag + "_" + cas);
            if (f[0] == 1920) for (String cas : GARDE_GRADES) sceneGarde(mc, cas, "garde_narration_" + tag + "_" + cas);
        }
        pas(2, fr.reborn.hud.staff.StaffDebug::narrationFermer);
    }

    private static void sceneGarde(Minecraft mc, String cas, String nom) {
        pas(2, () -> fr.reborn.hud.staff.StaffDebug.narrationOuvrir(cas));
        pas(10, () -> fr.reborn.hud.staff.StaffDebug.narrationSuite(cas));
        // 2 s : aperçu 250 ms après la frappe, puis, s'il ne tient pas à gauche du volet, son rendu réduit
        pas(40, () -> shot(mc, nom));
    }

    private static void shot(Minecraft mc, String name) {
        boolean bd = name.startsWith("basdroite"), ce = name.startsWith("centre"), na = name.startsWith("narration");
        String etat = bd ? "qui parle « " + QuiParleHud.bilan() + " » · rappels « " + RappelsTouches.bilan() + " »"
            : ce || na ? RecitHud.bilan() + (RecitHud.masqueVanilla() ? " · viseur masqué" : "") + (na ? " · " + Recit.bilan() : "")
            : OstHud.bilan();
        System.out.println((bd ? "[basdroite-banc] " : ce ? "[centre-banc] " : na ? "[narration-banc] " : "[ost-banc] ") + name
            + " (" + Util.getMillis() + " ms, image " + images + ") : " + etat);
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
