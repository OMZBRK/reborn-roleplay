package fr.reborn.hud.concentration;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.allure.Allure;
import fr.reborn.hud.animation.NarutoRun;
import fr.reborn.hud.byakugan.ByakuganClient;
import fr.reborn.hud.combat.CombatInput;
import fr.reborn.hud.combat.CombatState;
import fr.reborn.hud.communication.CommClient;
import fr.reborn.hud.douane.DouaneClient;
import fr.reborn.hud.duel.DuelClient;
import fr.reborn.hud.etat.EtatClient;
import fr.reborn.hud.interaction.CursorRaycast;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.keybind.RappelsTouches;
import fr.reborn.hud.ko.KoClient;
import fr.reborn.hud.parchemin.ConcentrationHud;
import fr.reborn.hud.recit.Recit;
import fr.reborn.hud.staff.EffetsStaff;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.LongSupplier;

/**
 * <b>Mode Concentration</b> (lot 4 du Game Plan n° 17, étape A, client seul) : la seule classe qui tient le mode.
 * La concentration <b>remplace le menu d'interaction R</b> (décision du 01/11) : même curseur, même caméra figée, même
 * clic, mêmes menus ({@link InteractionMode}), plus l'effet d'écran, le contour lumineux de ce qu'on survole et les
 * rappels de concentration.
 *
 * <ul>
 *   <li><b>Bascule</b> : {@link fr.reborn.hud.keybind.HudKeybinds#CONCENTRATION} (Y), ou l'ancienne touche R tant
 *       qu'elle est liée (alias de transition) ; Échap sort aussi, sans ouvrir la pause. Quatre bascules par seconde au
 *       plus.</li>
 *   <li><b>Effet d'écran</b> : la recette la plus forte, précisée le 07/11 (désaturation 80 %, « presque noir et
 *       blanc mais un peu de couleur », nuance froide légère, centre −20 %, bords −45 %, coins −72 %), un post-effet ({@code shaders/post/concentration_ecran.fsh}) posé dans l'unique
 *       emplacement de {@link fr.reborn.hud.effets.FiltresEcran} : KO &gt; Byakugan &gt; concentration &gt; météo (le
 *       filtre de la météo est suspendu le temps de la concentration). Entrée 250 ms (courbe douce), sortie 150 ms, un souffle
 *       à chaque passage (V1).
 *       L'avancement passe au filtre par une texture 4×1 ({@link #PARAMS}), mise à jour seulement quand il change.</li>
 *   <li><b>Survol</b> : un rayon par tick (pas par image) au point du curseur ; l'entité vivante survolée à
 *       {@link #HALO_PORTEE} blocs prend le <b>contour lumineux vanilla</b> (« glowing », blanc), côté client seulement
 *       (aucun effet serveur) ; jamais sous le Byakugan (son filtre négatif l'inverserait, il garde la priorité), jamais
 *       menu ouvert.</li>
 *   <li><b>Caméra</b> : figée ; <b>clic droit maintenu</b> = la caméra tourne (le curseur se cache, puis revient).</li>
 *   <li><b>Blocages</b> relus à chaque tick ({@link #refusEntree}, {@link #blocage}) : à terre, inconscient, mort (pas l'ATA ni la douleur), duel, Malaxage du
 *       chakra, mode photo, écran noir du staff, cinématique, douane : refus ou sortie ; combat : refus 5 s après un coup
 *       donné ou reçu, sortie au coup, à la garde, au dash ; sprint et course de chakra : sortie ; narration, méditation,
 *       cécité : le mode reste ; porté ou entravé : le mode reste, le menu est refusé ; le tchat ne ferme pas le mode ;
 *       tout autre écran le ferme ; une demande d'un autre joueur (porter, fouiller) passe devant et le ferme.</li>
 *   <li><b>Sortie propre par tous les chemins</b> : touche, Échap, écran, blocage, action choisie dans le menu,
 *       déconnexion ou changement de joueur (remise à zéro sans fondu).</li>
 * </ul>
 * Hors concentration, le mode ne coûte rien : aucun rayon, aucun filtre, texture jamais réécrite.
 */
public final class ModeConcentration {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/concentration");

    public static final Identifier FILTRE = Identifier.fromNamespaceAndPath("reborn-hud", "concentration_ecran");
    /** Lue par le filtre (entrée « texture » reborn-hud:concentration_params → textures/effect/concentration_params.png). */
    public static final Identifier PARAMS = Identifier.fromNamespaceAndPath("reborn-hud", "textures/effect/concentration_params.png");

    public static final long ENTREE_MS = 250, SORTIE_MS = 150, ANTI_REBOND_MS = 250, COMBAT_REFUS_MS = 5000;
    /** Portée du contour lumineux au survol (blocs). */
    public static final double HALO_PORTEE = 16.0;
    /**
     * Portée du rayon de survol, relancé à chaque tick (blocs) : courte, pour que la recherche d'entités reste bon marché
     * sur une place chargée ; le clic, lui, garde la longue portée de {@link CursorRaycast}.
     */
    public static final double SURVOL_PORTEE = 24.0;
    /** Couleur du contour : celle de l'effet « glowing » vanilla sans équipe. */
    public static final int HALO = 0xFFFFFF;
    /** Volume des souffles d'entrée et de sortie (variante V1 « souffle », retenue le 07/11 ; la sortie est déjà plus douce). */
    public static final float SON_VOLUME = 0.8f;

    /** Pourquoi le mode s'arrête (journal, banc). */
    public enum Raison { TOUCHE, ECHAP, ECRAN, BLOCAGE, FERME, DECONNEXION }

    /** Horloge du fondu (le banc la fige). */
    public static volatile LongSupplier horloge = Util::getMillis;

    private static boolean actif;
    private static long depuis;
    /** Fondu : avancement linéaire (0 à 1) au dernier changement, et son instant. */
    private static float p0;
    private static long changeA = Long.MIN_VALUE / 4;
    private static long derniereBascule = Long.MIN_VALUE / 4;
    /** Repères du combat à l'entrée : un coup, un dash ou une blessure plus récents ferment le mode. */
    private static long coupVu, dashVu;
    private static long blessureA, entreeReel;
    private static int blessureAvant;
    private static LocalPlayer joueur;
    /** Survol (relu une fois par tick) : id de l'entité au contour, cible interactive sous le curseur. */
    private static int survolId = -1;
    private static boolean interactif;
    /** Clic droit maintenu : la caméra tourne. */
    private static boolean rotation;
    /** Dernière raison d'un refus d'entrée ou d'une sortie (banc). */
    private static String dernierRefus, derniereSortie;

    private static DynamicTexture params;
    private static int octetEcrit = -1;

    private ModeConcentration() {}

    public static void init() {
        RappelsTouches.concentration = ModeConcentration::actif;
        RappelsTouches.concentrationBranchee = true;
        ClientTickEvents.END_CLIENT_TICK.register(ModeConcentration::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(ModeConcentration::reinitialiser));
        Plaques.init();   // étape B : canal reborn:concentration, plaques en face, halo
        Traces.init();    // étape C : traces de pas
    }

    /* ================================================================ API */

    public static boolean actif() { return actif; }

    /** Instant de l'entrée (horloge du mode). */
    public static long depuis() { return depuis; }

    /** Clic droit maintenu : la caméra tourne, le curseur est caché. */
    public static boolean rotation() { return actif && rotation; }

    /** Une cible interactive est sous le curseur (l'étiquette « Clic interagir » ne suit le curseur que sur elle). */
    public static boolean cibleInteractive() { return interactif; }

    /** Banc : dernière raison d'un refus d'entrée, et de la dernière sortie. */
    public static String dernierRefus() { return dernierRefus; }

    public static String derniereSortie() { return derniereSortie; }

    /** Id de l'entité survolée (contour), -1 sinon. */
    public static int survol() { return survolId; }

    /** Touche Concentration (ou l'ancienne touche R) : entre ou sort. */
    public static void basculer() {
        long now = horloge.getAsLong();
        if (now - derniereBascule < ANTI_REBOND_MS) return;
        if (actif) sortir(Raison.TOUCHE);
        else entrer();
    }

    /** Entre en concentration ; faux si un blocage la refuse (rien ne se passe, aucun message). */
    public static boolean entrer() {
        if (actif) return true;
        Minecraft mc = Minecraft.getInstance();
        long now = horloge.getAsLong();
        String r = refusEntree(mc, System.currentTimeMillis());
        if (r != null) {
            dernierRefus = r;
            LOG.debug("concentration refusée : {}", r);
            return false;
        }
        dernierRefus = null;
        InteractionMode.INSTANCE.ouvrirCurseur();
        if (!InteractionMode.INSTANCE.isActive()) return false;
        p0 = avancement(now);
        changeA = now;
        derniereBascule = now;
        actif = true;
        depuis = now;
        joueur = mc.player;
        coupVu = CombatState.INSTANCE.dernierCoupMs();
        dashVu = CombatInput.INSTANCE.dernierDashMs();
        entreeReel = System.currentTimeMillis();
        survolId = -1;
        interactif = false;
        rotation = false;
        // un bouton tenu avant l'entrée (manger, poser, miner) ne continue pas en boucle sur le viseur central
        mc.options.keyUse.setDown(false);
        mc.options.keyAttack.setDown(false);
        Recit.concentration(true);
        fr.reborn.hud.menu.RebornSounds.playReborn("concentration.entree", 1.0f, SON_VOLUME);
        preparerParams(mc);
        fr.reborn.hud.effets.FiltresEcran.tick();
        Traces.entree();    // étape C : plus rien de l'entrée précédente, le serveur renvoie la liste complète
        Plaques.entree();   // étape B : « on » au serveur (il revérifie et peut refuser)
        return true;
    }

    /** Sort de la concentration (fondu de 150 ms) ; sans effet si elle n'est pas active. */
    public static void sortir(Raison raison) {
        if (!actif) return;
        long now = horloge.getAsLong();
        p0 = avancement(now);
        changeA = now;
        derniereBascule = now;
        actif = false;
        derniereSortie = raison.name();
        survolId = -1;
        interactif = false;
        rotation = false;
        if (InteractionMode.INSTANCE.isActive()) InteractionMode.INSTANCE.deactivate();
        Recit.concentration(false);
        Plaques.sortie();   // étape B : « off », texte de la plaque effacé en 350 ms
        Traces.sortie();    // étape C : fondu avec l'effet, puis oubliées
        // son de sortie seulement quand le joueur sort lui-même (un coup, un écran : pas de souffle par-dessus)
        if (raison == Raison.TOUCHE || raison == Raison.ECHAP) {
            fr.reborn.hud.menu.RebornSounds.playReborn("concentration.sortie", 1.0f, SON_VOLUME);
        }
        LOG.debug("concentration finie : {}", raison);
    }

    /** Déconnexion, changement de joueur : tout s'arrête sans fondu. */
    public static void reinitialiser() {
        boolean etait = actif;
        actif = false;
        p0 = 0f;
        changeA = Long.MIN_VALUE / 4;
        survolId = -1;
        interactif = false;
        rotation = false;
        joueur = null;
        if (etait) {
            derniereSortie = Raison.DECONNEXION.name();
            if (InteractionMode.INSTANCE.isActive()) InteractionMode.INSTANCE.deactivate();
            Recit.concentration(false);
            Plaques.sortie();
        }
        Traces.oublier();
    }

    /** Étape B : le serveur refuse ou termine la concentration ({@code etat on:false}) : sortie, raison gardée (banc). */
    static void refusServeur(String raison) {
        if (!actif) return;
        sortir(Raison.BLOCAGE);
        derniereSortie = "SERVEUR " + (raison == null ? "" : raison);
        LOG.debug("concentration refusée par le serveur : {}", raison);
    }

    /** Clic droit (mixin de la souris), mode actif : maintenu = la caméra tourne. */
    public static void clicDroit(boolean appui) {
        // menu ouvert : pas de rotation (le panneau resterait figé à côté d'une cible qui s'éloigne)
        rotation = actif && appui && !InteractionMode.INSTANCE.menuOuvert();
    }

    /** Le contour lumineux va-t-il sur cette entité ? (rendu, à chaque image : aucune allocation) */
    public static boolean halo(LivingEntity e) {
        return actif && survolId >= 0 && e.getId() == survolId && !ByakuganClient.active()
            && !InteractionMode.INSTANCE.menuOuvert();
    }

    /* ================================================================ effet d'écran */

    /** Avancement linéaire du fondu (0 à 1) en {@code now}. */
    private static float avancement(long now) {
        long d = Math.max(0L, now - changeA);
        if (actif) return Math.min(1f, p0 + (1f - p0) * d / (float) ENTREE_MS);
        return Math.max(0f, p0 - p0 * d / (float) SORTIE_MS);
    }

    /** Niveau affiché de l'effet (0 à 1, courbe douce). */
    public static float niveau() {
        float p = avancement(horloge.getAsLong());
        return p * p * (3f - 2f * p);
    }

    /** Le filtre doit-il être posé (mode actif, ou fondu de sortie pas fini) ? Lu par {@code FiltresEcran}. */
    public static boolean filtreVoulu() {
        return actif || avancement(horloge.getAsLong()) > 0.001f;
    }

    /** Le filtre de la concentration est-il réellement à l'écran (sinon le KO ou le Byakugan a pris l'emplacement) ? */
    public static boolean filtreAffiche() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameRenderer != null && FILTRE.equals(mc.gameRenderer.currentPostEffect());
    }

    /** À chaque image (avant le rendu) : avancement écrit dans la texture du filtre, seulement s'il a changé. */
    public static void frame() {
        if (params == null && !actif) return;
        int o = Math.max(0, Math.min(255, Math.round(niveau() * 255f)));
        if (o == octetEcrit) return;
        Minecraft mc = Minecraft.getInstance();
        preparerParams(mc);
        if (params == null) return;
        NativeImage px = params.getPixels();
        if (px == null) return;
        px.setPixel(0, 0, 0xFF000000 | o << 16);
        params.upload();
        octetEcrit = o;
    }

    private static void preparerParams(Minecraft mc) {
        if (params != null || mc.getTextureManager() == null) return;
        params = new DynamicTexture(() -> "reborn-hud concentration params", 4, 1, false);
        mc.getTextureManager().register(PARAMS, params);
        octetEcrit = -1;
    }

    /* ================================================================ tick */

    private static void tick(Minecraft mc) {
        long reel = System.currentTimeMillis();
        LocalPlayer p = mc.player;
        // blessure reçue (même hors du mode : l'entrée est refusée 5 s après)
        if (p != null) {
            if (p.hurtTime > blessureAvant) blessureA = reel;
            blessureAvant = p.hurtTime;
        }
        if (!actif) return;
        if (p == null || mc.level == null || p != joueur) {
            reinitialiser();
            return;
        }
        if (!InteractionMode.INSTANCE.isActive()) {
            // fermé ailleurs : action choisie dans le menu, refus serveur, écran
            sortir(Raison.FERME);
            return;
        }
        Screen ecran = mc.gui.screen();
        if (ecran != null && !(ecran instanceof ChatScreen)) {
            sortir(Raison.ECRAN);
            return;
        }
        String b = blocage(mc, reel);
        if (b != null) {
            sortir(Raison.BLOCAGE);
            derniereSortie = "BLOCAGE " + b;
            return;
        }
        // clic droit relâché hors de la fenêtre ou pendant le tchat : la rotation s'arrête
        if (rotation && (ecran != null
            || GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS)) {
            rotation = false;
        }
        survoler(mc);
    }

    /** Un rayon au point du curseur (24 blocs) : entité au contour (16 blocs), cible interactive. */
    private static void survoler(Minecraft mc) {
        InteractionMode im = InteractionMode.INSTANCE;
        if (im.menuOuvert() || rotation) {
            survolId = -1;
            interactif = false;
            Traces.sortie();
            return;
        }
        HitResult hit = CursorRaycast.raycast(mc, im.cursorGuiX(), im.cursorGuiY(), SURVOL_PORTEE);
        Traces.survoler(mc, im.cursorGuiX(), im.cursorGuiY());
        int id = -1;
        boolean inter = false;
        if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            // une entité invisible pour nous ne se trahit pas : ni contour (sa silhouette apparaîtrait), ni étiquette
            inter = !e.isInvisibleTo(mc.player);
            if (inter && e instanceof LivingEntity && mc.gameRenderer.mainCamera() != null
                && e.distanceToSqr(mc.gameRenderer.mainCamera().position()) <= HALO_PORTEE * HALO_PORTEE) {
                id = e.getId();
            }
        } else if (hit instanceof BlockHitResult bhr) {
            var bp = bhr.getBlockPos();
            inter = CommClient.titreR(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5) != null;
        }
        survolId = id;
        interactif = inter;
    }

    /* ================================================================ blocages */

    /** Combat récent : coup donné ou reçu depuis moins de 5 s. */
    private static boolean combatRecent(long reel) {
        long dernier = Math.max(CombatState.INSTANCE.dernierCoupMs(), blessureA);
        return dernier != 0L && reel - dernier < COMBAT_REFUS_MS;
    }

    /** Blocages communs à l'entrée et au maintien ; null si rien ne s'y oppose. */
    private static String commun(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return "hors jeu";
        // à terre, inconscient, mort seulement (CADRAGE §7) : en ATA ou en douleur, le mode reste permis (le filtre du
        // KO garde alors l'emplacement, la vignette du récit sert de repli)
        if (p.isDeadOrDying() || koAterre() || EtatClient.voileImpose()) return "à terre";
        // le serveur refuse le spectateur : bloqué d'avance (pas de souffle d'entrée suivi d'une fermeture muette)
        if (p.isSpectator()) return "spectateur";
        if (DuelClient.inDuel()) return "duel";
        if (ConcentrationHud.actif()) return "malaxage du chakra";
        if (fr.reborn.hud.immersion.PhotoMode.INSTANCE.isActive()) return "mode photo";
        if (EffetsStaff.noirActif()) return "écran noir";
        if (cinematique()) return "cinématique";
        if (DouaneClient.masqueHud()) return "douane";
        // demande d'un autre joueur (porter, fouiller) : elle passe devant et ferme la concentration (comme chez Ketto)
        if (fr.reborn.hud.ko.CorpsClient.demande() != null) return "demande";
        if (CombatInput.INSTANCE.isBlocking()) return "garde";
        if (Allure.INSTANCE.sprinte()) return "sprint";
        if (NarutoRun.INSTANCE.isActive()) return "course de chakra";
        return null;
    }

    /** Phase 1 (à terre) ou 2 (inconscient) du KO reçue du serveur. */
    private static boolean koAterre() {
        var s = KoClient.state();
        return s != null && (s.phase() == 1 || s.phase() == 2);
    }

    /** Cinématique, sélection ou scène du personnage, chargement : hors du jeu. */
    private static boolean cinematique() {
        EtatClient.Etat e = EtatClient.etat();
        if (e != null && "HORS_JEU".equals(e.cadre())) return true;
        return fr.reborn.hud.menu.character.scene.SceneCamera.active()
            || fr.reborn.hud.menu.connect.ChargementScene.actif();
    }

    /** Raison de refuser l'entrée (null = permise). */
    public static String refusEntree(Minecraft mc, long reel) {
        if (mc.gui.screen() != null) return "écran ouvert";
        String c = commun(mc);
        if (c != null) return c;
        if (combatRecent(reel)) return "combat";
        return null;
    }

    /** Raison de sortir, relue à chaque tick (null = le mode reste). */
    private static String blocage(Minecraft mc, long reel) {
        String c = commun(mc);
        if (c != null) return c;
        if (CombatState.INSTANCE.dernierCoupMs() != coupVu) return "coup donné";
        if (CombatInput.INSTANCE.dernierDashMs() != dashVu) return "dash";
        if (blessureA > entreeReel) return "coup reçu";
        return null;
    }

    /** Banc : impose le survol (contour) d'une entité, ou -1. */
    public static void debugSurvol(int id) {
        survolId = id;
        interactif = id >= 0;
    }

    /** Banc : l'avancement linéaire de l'effet en {@code now} (vérifications). */
    public static float debugAvancement(long now) { return avancement(now); }
}
