package fr.reborn.hud.ko;

import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.ui.DaKit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * <b>Exécuter à la touche</b> (14/10 v2, « comme pour ramasser ») : la touche <b>Ramasser</b> (F par défaut).
 * <ul>
 *   <li>En visant à 3,5 blocs au plus une personne <b>en douleur</b>, ou un corps <b>KO</b> sur lequel on a un
 *       <b>RPK validé</b>, une invite discrète sous le viseur : « [F] Exécuter » (« · mort RP » pour un KO).</li>
 *   <li><b>Maintenir</b> F lance l'exécution ({@code /corps executer <joueur> <animation>}) ; un fin anneau se remplit
 *       autour du viseur ; <b>relâcher</b> avant la fin l'annule ({@code /corps annuler}).</li>
 *   <li>Mort RP : <b>deux temps</b> — un premier appui arme la confirmation (« [F] Confirmer · mort RP définitive »,
 *       4 s), le second, maintenu, exécute.</li>
 *   <li>Priorité : objet au sol visé (Ramasser) &gt; personne exécutable &gt; rôle normal de F (techniques,
 *       tyrolienne, grappin, main gauche) — F n'est prise que lorsqu'une personne exécutable est visée.</li>
 *   <li>L'animation se choisit dans le menu R (« Animation d'exécution », seulement si l'on en possède) ; sinon
 *       « sans animation ».</li>
 * </ul>
 * Le serveur revérifie tout (distance, douleur, RPK, possession) — voir {@code CorpsService}.
 */
public final class ExecuterClient {

    private static final Identifier TOUCHE = DaKit.tex("inventaire", "touche"), VOILE = DaKit.tex("inventaire", "voile_centre");
    private static final double PORTEE = 3.5;
    private static final long CONFIRMATION_MS = 4000;
    private static final int GRIS = 0xFFC2B59A, VERMILLON = 0xFFE26474;

    private record Cible(Entity e, String nom, boolean lethal) {}

    private static String choix = "sobre";
    private static Cible cible;
    private static boolean wasDown, holding, vuEnCours, holdLethal;
    private static long holdStart, armeJusqua;
    private static UUID armePour;

    /* banc d'essai (KoDebug) */
    public static Entity debugCible;
    public static boolean debugLethal, debugArme;
    /** Avancement imposé de l'anneau (0..1), ou -1. */
    public static float debugAnneau = -1f;

    private ExecuterClient() {}

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(ExecuterClient::avantTouches);
        ClientTickEvents.END_CLIENT_TICK.register(ExecuterClient::apresTick);
    }

    /** Animation choisie au menu R (id d'une exécution possédée) ; « sobre » = sans animation. */
    public static void choisir(String id) { choix = id == null ? "sobre" : id; }

    public static String choix() {
        for (CorpsClient.Exec e : CorpsClient.executions()) if (e.id().equals(choix)) return choix;
        return "sobre";
    }

    public static void clear() {
        cible = null;
        holding = false;
        armeJusqua = 0;
    }

    /* ================================================================ visée */

    private static void apresTick(Minecraft mc) {
        cible = viser(mc);
    }

    private static Cible viser(Minecraft mc) {
        if (debugCible != null) return new Cible(debugCible, "debug", debugLethal);
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return null;
        KoPayload s = KoClient.state();
        if (s != null && s.phase() > 0 || CorpsClient.douleur() || CorpsClient.porte()) return null;
        if (fr.reborn.hud.sol.RamasserClient.cible() != null) return null;          // l'objet visé passe avant
        if (fr.reborn.hud.interaction.InteractionMode.INSTANCE.isActive()) return null;
        Entity e = mc.crosshairPickEntity;
        if (!(e instanceof Player p) || p == mc.player) return null;
        if (p.position().distanceTo(mc.player.position()) > PORTEE) return null;
        CorpsClient.Body b = CorpsClient.body(p.getUUID());
        if (b == null) return null;
        String nom = p.getGameProfile().name();
        if (b.phase() == CorpsClient.PHASE_DOULEUR) return new Cible(p, nom, false);
        if ((b.phase() == 1 || b.phase() == 2) && b.rpk() == 2) return new Cible(p, nom, true);
        return null;
    }

    /* ================================================================ touche */

    /** START : avant que Minecraft ne traite F (main gauche) — on ne la prend que si une personne exécutable est visée. */
    private static void avantTouches(Minecraft mc) {
        KeyMapping k = HudKeybinds.PICKUP;
        if (k == null || mc.player == null) return;
        boolean down = k.isDown() && mc.gui.screen() == null;
        boolean appui = down && !wasDown;
        wasDown = down;
        long now = System.currentTimeMillis();
        if (holding) {
            avaler(mc, k);
            CorpsClient.Exe x = CorpsClient.exe();
            boolean enCours = x != null && x.role() == 0;
            if (enCours) vuEnCours = true;
            if (!down) {
                if (enCours || !vuEnCours) envoyer(mc, "corps annuler");
                holding = false;
            } else if ((vuEnCours && !enCours) || (!vuEnCours && now - holdStart > 1500)) {
                holding = false;                       // terminée, ou refusée par le serveur
            }
            return;
        }
        Cible c = cible;
        if (!appui || c == null) return;
        avaler(mc, k);
        if (c.lethal() && !(c.e().getUUID().equals(armePour) && now < armeJusqua)) {
            armePour = c.e().getUUID();                // mort RP : premier appui = armer la confirmation
            armeJusqua = now + CONFIRMATION_MS;
            return;
        }
        armeJusqua = 0;
        envoyer(mc, "corps executer " + c.nom() + " " + choix());
        holding = true;
        vuEnCours = false;
        holdLethal = c.lethal();
        holdStart = now;
    }

    /** F servait à exécuter : pas d'échange de main (ni de technique liée) pour cet appui. */
    private static void avaler(Minecraft mc, KeyMapping k) {
        if (mc.options != null && k.same(mc.options.keySwapOffhand)) {
            while (mc.options.keySwapOffhand.consumeClick()) { /* pris */ }
        }
    }

    private static void envoyer(Minecraft mc, String cmd) {
        if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
    }

    /* ================================================================ rendu */

    /** Invite sous le viseur, ou anneau pendant le maintien. */
    public static void render(GuiGraphicsExtractor g, Font f, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return;
        float anneau = debugAnneau >= 0f ? debugAnneau : -1f;
        boolean lethal = debugAnneau >= 0f ? debugLethal : holdLethal;
        if (anneau < 0f && holding) {
            CorpsClient.Exe x = CorpsClient.exe();
            anneau = x != null && x.role() == 0 ? x.progress()
                : Math.min(0.08f, (System.currentTimeMillis() - holdStart) / 3000f);
        }
        if (anneau >= 0f) {
            KoHud.anneau(g, w / 2, h / 2, anneau, DaKit.OR);
            DaKit.inter7o(g, f, DaKit.inter(lethal ? "exécution · mort RP" : "exécution"), w / 2f, h / 2f + 17,
                0xA0C2B59A, 1);
            return;
        }
        Cible c = cible;
        if (c == null) return;
        boolean arme = debugCible != null ? debugArme
            : c.lethal() && c.e().getUUID().equals(armePour) && System.currentTimeMillis() < armeJusqua;
        invite(g, f, w, h, c.lethal(), arme);
    }

    /** « [F] Exécuter » dans le style de l'invite « [F] Ramasser » (voile, touche, Inter 8, filet d'1 px). */
    private static void invite(GuiGraphicsExtractor g, Font f, int w, int h, boolean lethal, boolean arme) {
        Minecraft mc = Minecraft.getInstance();
        int km = DaKit.echelleMenu();
        double gs = mc.getWindow().getGuiScale();
        float cx = (float) (w / 2.0 * gs / km), ym = (float) ((h / 2.0 + 24) * gs / km);
        Component verbe = DaKit.inter(arme ? "Confirmer" : "Exécuter");
        Component suite = lethal ? DaKit.inter(arme ? "· mort RP définitive" : "· mort RP") : null;
        float tw = 11 + 4 + DaKit.largeur8(f, verbe) + (suite != null ? 3 + DaKit.largeur8(f, suite) : 0);
        float x0 = cx - tw / 2f;
        DaKit.unites(g, km);
        DaKit.etire(g, VOILE, Math.round(x0) - 12, Math.round(ym) - 9, Math.round(tw) + 24, 18, 64, 16, DaKit.blanc(1f));
        DaKit.blit(g, TOUCHE, Math.round(x0), Math.round(ym) - 6, 0, 0, 11, 11, 11, 11, DaKit.blanc(1f));
        DaKit.arcFin(g, f, DaKit.arcade(touche()), x0 + 5.5f, ym - 2.5f, DaKit.CREME, 1, false);
        float x = x0 + 15;
        x += DaKit.inter8(g, f, verbe, x, ym, arme ? DaKit.CREME : GRIS, 0);
        if (suite != null) x += 3 + DaKit.inter8(g, f, suite, x + 3, ym, VERMILLON, 0);
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        g.fill(Math.round((x0 + 3) * k), Math.round((ym + 6) * k), Math.round((x - 4) * k), Math.round((ym + 6) * k) + 1,
            DaKit.argb(lethal ? 0xD2523E : 0xD9A95E, 0.6f));
        DaKit.finPx(g);
        DaKit.finUnites(g);
    }

    private static String touche() {
        KeyMapping km = HudKeybinds.PICKUP;
        if (km == null) return "F";
        String s = km.getTranslatedKeyMessage().getString();
        if (s.isEmpty() || "key.keyboard.unknown".equals(km.saveString())) return "—";
        return s.length() > 3 ? s.substring(0, 3) : s.toUpperCase(java.util.Locale.ROOT);
    }
}
