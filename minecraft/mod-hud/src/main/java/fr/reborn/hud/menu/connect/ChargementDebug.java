package fr.reborn.hud.menu.connect;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;

import java.util.Optional;
import java.util.UUID;

/**
 * Banc de la scène de chargement, <b>inactif en production</b> : {@code REBORN_CHARGEMENT_DEBUG=scene|serveur}.
 *
 * <ul>
 *   <li>{@code scene} : la scène seule (écran de banc, horloge imposée) — une capture par phase
 *       ({@code chargement_01_connexion} … {@code _04_monde}) puis 180 images à 20 i/s pour le GIF
 *       ({@code chgif_000} …) dans {@code run/screenshots/}, puis ferme le jeu.</li>
 *   <li>{@code serveur} : vrai trajet vers un serveur de test local ({@code REBORN_CHARGEMENT_SERVEUR}, défaut
 *       {@code 127.0.0.1:25599}) avec un {@code ServerData} réglé sur « Demander » : le pack du serveur doit passer
 *       sans écran de confirmation. {@code REBORN_CHARGEMENT_PACK2="url sha1"} simule en plus un pack poussé à l'arrivée
 *       (comme Nexo) pendant l'écran du monde. Capture toutes les 8 ticks ({@code chsrv_<passage>_<n>.png}), entre en
 *       jeu, se déconnecte, refait le trajet (le pack doit venir du cache), puis ferme le jeu. Journal :
 *       « [ChargementDebug] … », « OK » / « ECHEC ».</li>
 * </ul>
 */
public final class ChargementDebug {

    private ChargementDebug() {}

    private static String mode;
    private static int t = -1, passage = 0, nCapture, enJeu, auMenu, surConnexion;
    private static boolean pack3Pousse;
    private static String dernier = "";
    private static boolean pack2Pousse;

    public static void init() {
        mode = System.getenv("REBORN_CHARGEMENT_DEBUG");
        if (mode == null || mode.isBlank()) return;
        ClientTickEvents.END_CLIENT_TICK.register(ChargementDebug::tick);
    }

    private static void log(String s) { System.out.println("[ChargementDebug] " + s); }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static boolean pret;

    private static void tick(Minecraft mc) {
        // le banc démarre au menu principal, une fois le chargement du jeu fini
        if (!pret) {
            pret = mc.gui != null && mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null;
            if (!pret) return;
            t = 20;
        }
        t++;
        if ("scene".equals(mode)) scene(mc);
        else if ("serveur".equals(mode)) serveur(mc);
    }

    /* ================================================================ scène seule */

    /** Écran de banc : la scène, rien d'autre. */
    static final class Banc extends Screen {
        Banc() { super(Component.literal("banc")); }

        @Override
        public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float d) { }

        @Override
        public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float d) {
            ChargementScene.dessinerBanc(ctx);
        }

        @Override
        public boolean shouldCloseOnEsc() { return false; }
    }

    private static final ChargementScene.Etat[] PHASES = {
        new ChargementScene.Etat("Connexion au serveur", true, -1),
        new ChargementScene.Etat("Téléchargement des ressources", false, 0.42f),
        new ChargementScene.Etat("Application des ressources", true, 0.68f),
        new ChargementScene.Etat("Chargement du monde", true, 0.55f),
    };
    private static final String[] NOMS = {"01_connexion", "02_telechargement", "03_application", "04_monde"};
    private static final double[] INSTANTS = {2.0, 4.4, 6.3, 8.9};
    private static final int GIF = 180, GIF_DEBUT = 160;

    private static void scene(Minecraft mc) {
        if (t == 60) {
            ChargementScene.tempsBanc = 0;
            ChargementScene.etatBanc = PHASES[0];
            mc.setScreenAndShow(new Banc());
        }
        for (int i = 0; i < PHASES.length; i++) {
            int base = 80 + i * 16;
            if (t == base) {
                ChargementScene.etatBanc = PHASES[i];
                ChargementScene.tempsBanc = INSTANTS[i];
            }
            if (t == base + 6) shot(mc, "chargement_" + NOMS[i]);
        }
        // GIF : 9 s à 20 i/s, les quatre phases se suivent ; image i rendue au tick GIF_DEBUT + i, capturée au suivant
        int i = t - GIF_DEBUT;
        if (i >= 1 && i <= GIF) shot(mc, String.format("chgif_%03d", i - 1));
        if (i >= 0 && i < GIF) {
            ChargementScene.tempsBanc = 20.0 + i * 0.05;
            int ph = Math.min(3, i / 45);
            if (ph == 1) {
                int pct = Math.min(100, (i - 45) * 100 / 44);
                ChargementScene.etatBanc = new ChargementScene.Etat("Téléchargement des ressources", false, pct / 100f);
            } else if (ph == 2) {
                ChargementScene.etatBanc = new ChargementScene.Etat("Application des ressources", true, (i - 90) / 44f);
            } else if (ph == 3) {
                ChargementScene.etatBanc = new ChargementScene.Etat("Chargement du monde", true, (i - 135) / 44f);
            } else {
                ChargementScene.etatBanc = PHASES[0];
            }
        }
        if (i == 0) { ChargementScene.coutNs = 0; ChargementScene.images = 0; }
        if (i == GIF + 10) {
            log(String.format("coût moyen de la scène (extraction CPU) : %.1f µs par image sur %d images, k = %d",
                ChargementScene.coutNs / 1000.0 / Math.max(1, ChargementScene.images), ChargementScene.images,
                ChargementScene.k));
            log("FIN");
            mc.stop();
        }
    }

    /* ================================================================ vrai serveur local */

    private static void serveur(Minecraft mc) {
        Screen s = mc.gui == null ? null : mc.gui.screen();
        var o = mc.gui == null ? null : mc.gui.overlay();
        String etat = (s == null ? "aucun" : s.getClass().getSimpleName()) + (o != null ? "+overlay" : "")
            + (ChargementScene.actif() ? " [scène]" : "") + (ChargementScene.packEnAttente() ? " [pack]" : "");
        if (!etat.equals(dernier)) {
            log("t=" + t + " passage " + passage + " : " + etat);
            dernier = etat;
        }
        if (s instanceof ConfirmScreen) log("ECHEC écran de confirmation affiché : " + s.getTitle().getString());
        if (s instanceof DisconnectedScreen) {
            log("ECHEC déconnecté");
            shot(mc, "chsrv_" + passage + "_deconnecte");
            mc.stop();
            return;
        }
        // passage 0 : Échap sur l'écran de connexion doit annuler et ramener au menu
        if (passage == 0 && s instanceof ConnectScreen) {
            if (++surConnexion == 8) {
                log("touche Échap sur l'écran de connexion");
                s.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0));
            }
        }
        if (passage == 0 && surConnexion >= 8 && s instanceof TitleScreen) {
            log((ChargementScene.actif() ? "ECHEC" : "OK") + " Échap a annulé la connexion (retour au menu)");
            passage = 1;
        }
        if (s instanceof net.minecraft.client.gui.screens.DeathScreen && mc.player != null && t % 10 == 0) {
            mc.player.respawn();                       // monde de test : on ne s'arrête pas sur une mort
            mc.setScreenAndShow(null);
        }
        boolean trajet = ChargementScene.actif() || o != null;
        if (passage == 3 && o != null && t % 3 == 0 && nCapture < 150) shot(mc, String.format("chsrv_3_%03d", nCapture++));
        else if (trajet && t % 8 == 0 && nCapture < 150) shot(mc, String.format("chsrv_%d_%03d", passage, nCapture++));

        if (s instanceof LevelLoadingScreen && !pack2Pousse && passage == 1) {
            pack2Pousse = true;
            String p2 = System.getenv("REBORN_CHARGEMENT_PACK2");
            if (p2 != null && !p2.isBlank() && mc.getConnection() != null) {
                String[] a = p2.trim().split("\\s+");
                log("pack poussé à l'arrivée (simulation Nexo) : " + a[0]);
                mc.getConnection().handleResourcePackPush(new ClientboundResourcePackPushPacket(
                    UUID.nameUUIDFromBytes("reborn-banc-pack2".getBytes()), a[0], a.length > 1 ? a[1] : "", true,
                    Optional.empty()));
            }
        }

        if (s instanceof TitleScreen && o == null && !ChargementScene.actif()) {
            auMenu++;
            if (auMenu == 40) {
                String adr = System.getenv().getOrDefault("REBORN_CHARGEMENT_SERVEUR", "127.0.0.1:25599");
                ServerData d = new ServerData("Banc chargement", adr, ServerData.Type.OTHER);
                d.setResourcePackStatus(ServerData.ServerPackStatus.PROMPT);
                log("connexion " + passage + " à " + adr + " (packs serveur : Demander)");
                nCapture = 0;
                ConnectScreen.startConnecting(s, mc, ServerAddress.parseString(adr), d, false, null);
            }
        } else {
            auMenu = 0;
        }

        if (s == null && mc.level != null && o == null) {
            enJeu++;
            if (enJeu == 2) log("OK en jeu, passage " + passage);
            if (enJeu == 6 || enJeu == 12) shot(mc, "chsrv_" + passage + "_en_jeu_" + enJeu);
            if (enJeu == 40 && passage == 2 && !pack3Pousse && mc.getConnection() != null) {
                // pack renvoyé en plein jeu (comme un /nexo reload) : fondu jeu → scène → jeu, rien de Mojang
                pack3Pousse = true;
                String p2 = System.getenv("REBORN_CHARGEMENT_PACK2");
                if (p2 != null && !p2.isBlank()) {
                    String[] a = p2.trim().split("\s+");
                    log("pack renvoyé en jeu (simulation /nexo reload)");
                    nCapture = 0;
                    passage = 3;
                    enJeu = 0;
                    mc.getConnection().handleResourcePackPush(new ClientboundResourcePackPushPacket(
                        UUID.nameUUIDFromBytes("reborn-banc-pack3".getBytes()), a[0], a.length > 1 ? a[1] : "", true,
                        Optional.empty()));
                }
            }
            if (enJeu == 80) {
                if (passage == 1) {
                    passage = 2;
                    enJeu = 0;
                    log("déconnexion, puis second trajet (cache)");
                    mc.disconnect(new TitleScreen(), false);
                } else {
                    log("FIN");
                    mc.stop();
                }
            }
        } else {
            enJeu = 0;
        }
        if (t > 20 * 60 * 4) {
            log("ECHEC délai dépassé");
            mc.stop();
        }
    }
}
