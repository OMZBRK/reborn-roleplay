package fr.reborn.hud.ko;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.runtime.VitalsHud;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.voice.VoiceState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Micro « à terre » (31/10) : à terre et inconscient, le panneau vitals est masqué ({@link KoMasqueHud}) alors que la
 * voix passe justement en chuchoté. Ce tout petit élément remet le <b>micro seul</b>, à sa place dans les vitals (même
 * position, même échelle : élément VITALS de l'éditeur de HUD), vert (chuchoter), avec les états des vitals
 * ({@link VitalsHud#micro} : pulsation quand on parle, éteint si coupé, gris sans liaison vocale). À son apparition, un mot minuscule
 * « chuchoté » à côté, 2 s ; la touche « Portée de la voix » y pose son refus (les notifications sont masquées à terre).
 *
 * <p>Pas dans {@code KoMasqueHud.REBORN} : il ne vit que lorsque les vitals sont masqués par le KO (et seulement si le
 * joueur les affiche, sur un serveur Reborn, avec Plasmo Voice). Rien n'est alloué par image : textes créés une fois,
 * position du panneau relue toutes les 500 ms.
 */
public final class MicroATerreHud {

    private static final long INFO_MS = 2000L, REFUS_MS = 2600L, ENTREE_MS = 150L, SORTIE_MS = 400L, RELIRE_MS = 500L;
    /** Texte (crème doux de la DA, celui des sous-textes du KO). */
    private static final int TEXTE = 0xFFC2B59A;

    private static boolean vu;
    private static long infoDepuis, infoJusqua;
    private static Component info, cChuchote, cRefus;

    /** Panneau vitals tel que le joueur l'a placé (relu toutes les {@link #RELIRE_MS}). */
    private static long relu;
    private static int lw, lh;
    private static boolean vitalsAffiches;
    private static float bx, by, sc = 1f;

    private MicroATerreHud() {}

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "micro-a-terre"),
            (ctx, tickCounter) -> render(ctx));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { vu = false; info = null; });
        Banc.init();
    }

    /** Touche « Portée de la voix » à terre : « À terre, tu ne peux que chuchoter » à côté du micro. */
    public static void refus() {
        if (cRefus == null) cRefus = DaKit.inter("À terre, tu ne peux que chuchoter");
        montrer(cRefus, REFUS_MS);
    }

    /** Touche « Portée de la voix » refusée pour une autre raison (voix coupée par l'état) : ce texte à côté du micro. */
    public static void refus(String texte) {
        montrer(DaKit.inter(texte), REFUS_MS);   // un appui de touche : composé à la demande, pas par image
    }

    private static void montrer(Component c, long duree) {
        long now = System.currentTimeMillis();
        boolean affiche = info != null && now < infoJusqua;
        if (!affiche || info != c) infoDepuis = now;   // même texte encore affiché : on le prolonge sans refaire l'entrée
        info = c;
        infoJusqua = now + duree;
    }

    private static void render(GuiGraphicsExtractor ctx) {
        Banc.images++;
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) {
            vu = false;
            return;
        }
        if (!vu) {   // apparition (à terre, inconscient) : « chuchoté », 2 s
            vu = true;
            if (cChuchote == null) cChuchote = DaKit.inter("chuchoté");
            montrer(cChuchote, INFO_MS);
        }
        ctx.pose().pushMatrix();
        ctx.pose().translate(bx, by);
        if (sc != 1f) ctx.pose().scale(sc, sc);
        VitalsHud.micro(ctx, VitalsHud.MIC_X, VitalsHud.MIC_Y, VitalsHud.MIC_TAILLE);
        long now = System.currentTimeMillis();
        if (info != null && now < infoJusqua) {
            float a = Math.min(1f, (now - infoDepuis) / (float) ENTREE_MS);
            long reste = infoJusqua - now;
            if (reste < SORTIE_MS) a *= reste / (float) SORTIE_MS;
            if (a > 0.02f) {
                DaKit.inter7o(ctx, mc.font, info, VitalsHud.MIC_X + VitalsHud.MIC_TAILLE + 3,
                    VitalsHud.MIC_Y + VitalsHud.MIC_TAILLE / 2f, DaKit.alpha(TEXTE, 0.9f * a), 0);
            }
        }
        ctx.pose().popMatrix();
    }

    /** Vitals masqués par le KO, alors que le joueur les affiche (serveur Reborn, Plasmo Voice présent). */
    private static boolean visible(Minecraft mc) {
        if (mc.player == null || mc.options == null || mc.gui.hud.isHidden()) return false;
        if (!KoMasqueHud.masque() || !VoiceState.available()) return false;
        if (!fr.reborn.hud.runtime.RebornSession.rpFeaturesEnabled()) return false;
        long now = System.currentTimeMillis();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        if (now - relu >= RELIRE_MS || now < relu || w != lw || h != lh) relire(w, h, now);
        return vitalsAffiches;
    }

    private static void relire(int w, int h, long now) {
        relu = now;
        lw = w;
        lh = h;
        try {
            HudElementState st = fr.reborn.hud.RebornHudClient.config().stateOf(HudElement.VITALS);
            vitalsAffiches = st.visible();
            HudElementBounds b = HudElementBounds.currentFor(HudElement.VITALS, st, w, h);
            bx = b.x();
            by = b.y();
            sc = st.scale();
        } catch (RuntimeException e) {
            vitalsAffiches = false;
        }
    }

    /**
     * Banc de la voix, <b>inactif en production</b> ({@code REBORN_VOIX_DEBUG=1}, monde solo de dev avec Plasmo Voice,
     * jamais de serveur ; ne touche pas au monde) : micro des vitals vert / or / rouge, « je parle » (deux phases de la
     * pulsation), coupé, assourdi ; touche « Portée de la voix » par le <b>vrai</b> chemin du clavier (le serveur vocal
     * intégré revalide la portée : la valeur relue est la vraie), notification et cercle de portée, un tour complet ;
     * à terre : micro seul, « chuchoté », refus de la touche. Écrit « [voix] OK … » / « ECHEC … » dans le journal et
     * {@code run/voix_banc.txt}, captures {@code run/screenshots/voix_*.png}, rend la portée de départ, ferme le jeu.
     */
    static final class Banc {

        private static final java.util.List<java.util.function.Consumer<Minecraft>> ETAPES = new java.util.ArrayList<>();
        private static final java.util.List<Integer> DELAIS = new java.util.ArrayList<>();
        private static final StringBuilder LOG = new StringBuilder();
        private static int t = -1, etape, prochaine, echelleAvant = -1, depart = -1, avant = -1;
        /** Images rendues (compteur posé par {@link #render}) : une étape attend 3 images après la précédente. */
        static int images, imagesMarque;
        private static java.lang.reflect.Method keyPress;

        private Banc() {}

        static void init() {
            if (!"1".equals(System.getenv("REBORN_VOIX_DEBUG"))) return;
            programme();
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(Banc::tick);
        }

        private static void programme() {
            pas(1, Banc::preparer, "préparation");
            pas(10, mc -> {
                String s = reglages();
                LOG.append("réglages Plasmo Voice : ").append(s).append('\n');
                verifie(VoiceState.available(), "Plasmo Voice présent");
                verifie(s.contains("icones=2"), "icônes au-dessus des têtes masquées (show_source_icons = 2)");
                verifie(s.contains("action=[]"), "touche « Action » de Plasmo Voice déliée");
                verifie(VoiceState.distance() > 0, "serveur vocal intégré : portée " + VoiceState.distance() + ", "
                    + VoiceState.nombrePortees() + " portées, rang " + VoiceState.portee());
            }, "réglages");
            // couleurs du micro (vitals)
            pas(1, mc -> VoiceState.DEBUG_PORTEE = VoiceState.CHUCHOTE, "portée 0");
            pas(6, mc -> shot(mc, "voix_01_micro_vert"), "capture");
            pas(1, mc -> VoiceState.DEBUG_PORTEE = VoiceState.NORMALE, "portée 1");
            pas(6, mc -> shot(mc, "voix_02_micro_or"), "capture");
            pas(1, mc -> VoiceState.DEBUG_PORTEE = VoiceState.CRIEE, "portée 2");
            pas(6, mc -> shot(mc, "voix_03_micro_rouge"), "capture");
            pas(1, mc -> { VoiceState.DEBUG_PORTEE = VoiceState.NORMALE; VoiceState.DEBUG_SOI_PARLE = true; }, "je parle");
            pas(10, mc -> shot(mc, "voix_04_parle_a"), "capture");
            pas(4, mc -> shot(mc, "voix_05_parle_b"), "capture");   // 200 ms plus tard : l'autre phase (2,5 Hz)
            pas(1, mc -> { VoiceState.DEBUG_SOI_PARLE = false; VoiceState.DEBUG_MUET = true; }, "micro coupé");
            pas(6, mc -> shot(mc, "voix_06_micro_coupe"), "capture");
            pas(1, mc -> { VoiceState.DEBUG_MUET = false; VoiceState.DEBUG_ASSOURDI = true; }, "assourdi");
            pas(6, mc -> shot(mc, "voix_07_assourdi"), "capture");
            pas(1, mc -> {
                VoiceState.DEBUG_ASSOURDI = false;
                VoiceState.DEBUG_PORTEE = null;
                depart = VoiceState.distance();
            }, "couleurs rendues à Plasmo Voice");
            // touche « Portée de la voix » : un tour complet
            for (int i = 1; i <= 3; i++) {
                final int n = i;
                pas(6, mc -> { avant = VoiceState.distance(); touche(mc, true); }, "touche n° " + n);
                pas(1, mc -> touche(mc, false), "");
                pas(8, mc -> verifie(VoiceState.distance() > 0 && VoiceState.distance() != avant, "touche n° " + n + " : portée "
                    + avant + " → " + VoiceState.distance() + " (rang " + VoiceState.portee() + ")"), "vérif");
                pas(1, mc -> shot(mc, String.format("voix_%02d_touche_%d", 7 + n, n)), "capture");
            }
            pas(1, mc -> verifie(VoiceState.nombrePortees() != 3 || VoiceState.distance() == depart,
                "un tour complet ramène à la portée de départ (" + depart + " → " + VoiceState.distance() + ")"), "vérif");
            // à terre
            pas(30, mc -> {
                avant = VoiceState.distance();
                KoClient.update(new KoPayload((byte) 1, (byte) 0, (short) 45, (short) 60, false, (byte) 0, 0f, (short) 0,
                    (short) 0, false, false));
            }, "à terre");
            pas(8, mc -> verifie(VoiceState.portee() == VoiceState.CHUCHOTE && VoiceState.porteeImposee(),
                "à terre : chuchoté imposé (rang " + VoiceState.portee() + ")"), "vérif");
            pas(1, mc -> shot(mc, "voix_11_a_terre_chuchote"), "capture");
            pas(50, mc -> shot(mc, "voix_12_a_terre"), "capture");
            pas(1, mc -> touche(mc, true), "touche à terre");
            pas(1, mc -> touche(mc, false), "");
            pas(6, mc -> verifie(VoiceState.distance() == avant, "à terre : touche refusée, portée inchangée ("
                + avant + " → " + VoiceState.distance() + ")"), "vérif");
            pas(1, mc -> shot(mc, "voix_13_a_terre_refus"), "capture");
            pas(1, mc -> KoClient.clear(), "relevé");
            pas(10, Banc::rendre, "remise en état");
            pas(10, mc -> {
                ecrire(mc);
                System.out.println("[voix] FIN");
                mc.stop();
            }, "fin");
        }

        private static void preparer(Minecraft mc) {
            echelleAvant = mc.options.guiScale().get();
            mc.options.guiScale().set(2);
            mc.resizeGui();
            fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
            fr.reborn.hud.runtime.VitalsFeed.update(82, 100, 64, 100);
            mc.gui.hud.getChat().clearMessages(false);
        }

        private static void rendre(Minecraft mc) {
            VoiceState.DEBUG_PORTEE = null;
            VoiceState.DEBUG_SOI_PARLE = VoiceState.DEBUG_MUET = VoiceState.DEBUG_ASSOURDI = false;
            // la portée de départ (le tour complet y ramène déjà avec trois portées)
            for (int i = 0; i < 8 && depart > 0 && VoiceState.distance() != depart; i++) {
                if (VoiceState.changerPortee() <= 0) break;
            }
            verifie(depart <= 0 || VoiceState.distance() == depart, "portée de départ rendue (" + VoiceState.distance() + ")");
            fr.reborn.hud.runtime.RebornSession.debugForceRp = false;
            fr.reborn.hud.runtime.VitalsFeed.clear();
            if (echelleAvant >= 0) { mc.options.guiScale().set(echelleAvant); mc.resizeGui(); }
        }

        private static String reglages() {
            try {
                return fr.reborn.hud.voice.RebornVoiceAddon.etatReglages();
            } catch (Throwable e) {
                return "Plasmo Voice absent (" + e + ")";
            }
        }

        /** La touche « Portée de la voix » par le vrai chemin du clavier (écrans, toutes les liaisons, mods à l'écoute). */
        private static void touche(Minecraft mc, boolean appui) {
            var k = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(
                fr.reborn.hud.keybind.HudKeybinds.VOIX_PORTEE);
            if (k.getType() != com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
                verifie(false, "touche « Portée de la voix » hors clavier");
                return;
            }
            try {
                if (keyPress == null) {
                    keyPress = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class,
                        net.minecraft.client.input.KeyEvent.class);
                    keyPress.setAccessible(true);
                }
                int glfw = k.getValue();
                keyPress.invoke(mc.keyboardHandler, mc.getWindow().handle(),
                    appui ? org.lwjgl.glfw.GLFW.GLFW_PRESS : org.lwjgl.glfw.GLFW.GLFW_RELEASE,
                    new net.minecraft.client.input.KeyEvent(glfw, org.lwjgl.glfw.GLFW.glfwGetKeyScancode(glfw), 0));
            } catch (ReflectiveOperationException e) {
                verifie(false, "keyPress indisponible : " + e);
            }
        }

        private static void pas(int delai, java.util.function.Consumer<Minecraft> action, String nom) {
            DELAIS.add(delai);
            ETAPES.add(mc -> {
                if (!nom.isEmpty()) System.out.println("[voix] " + nom);
                action.accept(mc);
            });
        }

        private static void verifie(boolean ok, String quoi) {
            String l = (ok ? "OK " : "ECHEC ") + quoi;
            System.out.println("[voix] " + l);
            LOG.append(l).append('\n');
        }

        private static void shot(Minecraft mc, String nom) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, nom + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        }

        private static void ecrire(Minecraft mc) {
            try {
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("voix_banc.txt"), LOG.toString(),
                    java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.out.println("[voix] journal non écrit : " + e);
            }
        }

        private static void tick(Minecraft mc) {
            if (mc.player == null || mc.level == null) { t = -1; return; }
            t++;
            // fenêtre du client de dev souvent à l'arrière-plan : sans « activité », Minecraft bride les images
            mc.getFramerateLimitTracker().onInputReceived();
            // on attend le serveur vocal intégré (au plus 20 s), puis 4 s que le monde se pose
            if (etape == 0 && VoiceState.distance() <= 0 && t < 400) return;
            if (prochaine == 0) prochaine = t + 80 + DELAIS.get(0);
            if (etape >= ETAPES.size() || t < prochaine) return;
            // juste après l'arrivée, le client rattrape plusieurs ticks par image : une capture pouvait montrer l'état
            // d'avant l'étape précédente ; on attend 3 images rendues depuis
            if (images - imagesMarque < 3) return;
            ETAPES.get(etape).accept(mc);
            etape++;
            imagesMarque = images;
            if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
        }
    }
}
