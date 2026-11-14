package fr.reborn.hud.audit;

import com.google.gson.JsonObject;
import fr.reborn.hud.byakugan.ByakuganClient;
import fr.reborn.hud.byakugan.DojutsuCanal;
import fr.reborn.hud.byakugan.FeuillagePlein;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.concentration.ModeConcentration;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.ko.KoClient;
import fr.reborn.hud.ko.KoPayload;
import fr.reborn.hud.runtime.RebornSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Banc du <b>Byakugan</b> (lot 5, étape B), <b>inactif en production</b> : {@code REBORN_LOT5_DEBUG=byak} (monde solo de
 * dev, jamais de serveur). Serveur ShinobiCore simulé côté client au format du canal {@code reborn:dojutsu} (règles,
 * acceptation, fin) ; la touche O passe par le vrai {@link ByakuganClient#basculer()} ; le filtre, la profondeur, les
 * silhouettes, le contour derrière un mur et le rappel sont les vrais.
 *
 * <p>Décors bâtis à côté de l'origine (à l'est du couloir du saut) : champ dégagé, rue façon Konoha (pierre, bois, toits
 * sombres, lanternes), forêt (vrais arbres placés), mur avec deux personnes derrière, grotte fermée (aucune lumière du
 * ciel). Captures {@code run/screenshots/byak_*.png} (hors vision puis vision établie), fondu image par image à horloge
 * figée, rappel, concentration puis Byakugan, 1366 × 768, coupure au KO ; encre seule ({@code _traits}, puis après deux pas
 * d'un quart de bloc : part de pixels de traits et tenue dans le temps, mesurées hors du jeu), feuilles pleines pendant la
 * vision seulement (option du joueur inchangée) ; mesure des images par seconde sans et avec le filtre (même vue,
 * alternées). Bilans « [byak] OK / ECHEC », puis le jeu se ferme.
 */
public final class Lot5ByakDebug {

    private record Etape(int delai, Predicate<Minecraft> action, String nom) { }

    private static final List<Etape> ETAPES = new ArrayList<>();
    private static int t = -1, etape, prochaine, echecs, attente;
    private static long h;
    private static String mode = "creative", difficulte = "normal";
    private static long heure = -1;
    private static boolean pluie;
    private static int ox, oy, oz;
    private static RebornCamera.Mode cameraAvant;
    private static int limiteAvant;
    private static boolean vsyncAvant;
    private static double fonduChunksAvant;
    private static final ArrayDeque<String> FILE = new ArrayDeque<>();
    private static int demandesOn, demandesOff;
    private static final List<String> BILAN = new ArrayList<>();
    private static final List<Integer> FPS = new ArrayList<>();

    private Lot5ByakDebug() {}

    public static void init() {
        String env = String.valueOf(System.getenv("REBORN_LOT5_DEBUG"));
        if ("byak_vues".equalsIgnoreCase(env)) { initVues(String.valueOf(System.getenv("REBORN_BYAK_VUES"))); return; }
        if ("byak_fps".equalsIgnoreCase(env)) { initFps(); return; }
        if ("byak_entites".equalsIgnoreCase(env)) { initEntites(); return; }
        if (!"byak".equalsIgnoreCase(env) && !"byak_vite".equalsIgnoreCase(env)) return;
        // byak_vite : les décors seulement (réglage du rendu), sans fondu, mesure, priorité, KO ni 1366
        boolean complet = "byak".equalsIgnoreCase(env);

        pas(5, mc -> {
            var c = mc.player.connection;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            var srv = mc.getSingleplayerServer();
            if (srv != null) { heure = srv.overworld().getOverworldClockTime(); pluie = srv.overworld().isRaining(); }
            RebornSession.debugForceRp = true;
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            difficulte = mc.level.getDifficulty().getSerializedName();
            c.sendCommand("difficulty peaceful");
            c.sendCommand("gamerule doDaylightCycle false");
            for (int i = 0; i < 9; i++) c.sendCommand("item replace entity @s hotbar." + i + " with minecraft:air");
            ox = mc.player.getBlockX();
            oy = mc.player.getBlockY();
            oz = mc.player.getBlockZ();
            var ab = mc.player.getAbilities();
            ab.flying = false;
            mc.player.onUpdateAbilities();
            cameraAvant = RebornCamera.INSTANCE.mode();
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            limiteAvant = mc.options.framerateLimit().get();
            vsyncAvant = mc.options.enableVsync().get();
            System.out.println("[byak] origine " + ox + " " + oy + " " + oz + ", distance de rendu "
                + mc.options.getEffectiveRenderDistance() + ", feuilles découpées " + mc.options.cutoutLeaves().get()
                + ", transparence améliorée " + mc.options.improvedTransparency().get());
        }, "préparation");
        pas(4, Lot5ByakDebug::batirChamp, "champ");
        pas(4, Lot5ByakDebug::batirRue, "rue");
        pas(4, Lot5ByakDebug::batirForet, "forêt");
        pas(4, Lot5ByakDebug::batirGrotte, "grotte");
        pas(40, mc -> {
            DojutsuCanal.debugServeur = Lot5ByakDebug::serveur;
            DojutsuCanal.recevoir("{\"v\":1,\"t\":\"regles\",\"byakugan\":true,\"portee\":50.0}");
            verifie(ByakuganClient.serveurConnu() && ByakuganClient.droit(), "règles reçues : le personnage a le Byakugan");
            verifie("O".equals(HudKeybinds.nom(HudKeybinds.BYAKUGAN, "?")), "touche par défaut : " + HudKeybinds.nom(HudKeybinds.BYAKUGAN, "?"));
            ByakuganClient.ancienCanal(true, 40f);
            verifie(!ByakuganClient.actif(), "ancien canal reborn:byakugan ignoré quand ShinobiCore parle reborn:dojutsu");
        }, "serveur simulé");

        // ── 1. champ dégagé, de jour ───────────────────────────────────────────────────────────────────────────────
        vue(-44, true, 0, -25, -90f, 4f, "champ");
        paire("byak_jour_champ", true);
        // particules sur le sol (petits détails contrastés) : aucun écho, aucune copie autour (défaut du 3e tour)
        pas(1, mc -> basculer(), "O");
        attendre(mc -> ByakuganClient.niveau() >= 0.999f, 40, "vision établie");
        pas(1, mc -> {
            cmd(mc, "particle minecraft:composter %d %d.1 %d 1.2 0.02 1.2 0 300 force", ox - 34, P, oz - 25);
            cmd(mc, "particle minecraft:crit %d %d.2 %d 1.2 0.05 1.2 0 200 force", ox - 30, P, oz - 25);
        }, "particules");
        pas(2, mc -> shot(mc, "byak_particules"), "capture des particules");
        pas(1, mc -> basculer(), "O : fermeture");
        attendre(mc -> !ByakuganClient.filtreVoulu() && !ByakuganClient.filtreAffiche(), 40, "fermé");
        pas(1, mc -> cmd(mc, "particle minecraft:composter %d %d.1 %d 1.2 0.02 1.2 0 300 force", ox - 34, P, oz - 25),
            "particules, hors vision");
        pas(2, mc -> shot(mc, "byak_particules_sans"), "capture des particules hors vision");
        // rappel (bas à droite), profondeur vue par le filtre, anneaux d'écran (à juger), puis refus sans droit
        pas(1, mc -> basculer(), "O");
        attendre(mc -> ByakuganClient.niveau() >= 0.999f, 40, "vision établie");
        pas(2, mc -> {
            verifie(ByakuganClient.actif() && ByakuganClient.filtreAffiche(), "filtre posé");
            shot(mc, "byak_rappel");
            ByakuganClient.debugProfondeur = true;
        }, "capture du rappel");
        pas(3, mc -> { shot(mc, "byak_debug_profondeur"); ByakuganClient.debugProfondeur = false; ByakuganClient.anneaux = true; },
            "capture de la profondeur");
        pas(3, mc -> { shot(mc, "byak_essai_anneaux"); }, "capture des boucles d'encre");
        pas(1, mc -> basculer(), "O : fermeture");
        attendre(mc -> !ByakuganClient.filtreVoulu() && !ByakuganClient.filtreAffiche(), 40, "filtre retiré");
        pas(1, mc -> {
            DojutsuCanal.recevoir("{\"v\":1,\"t\":\"regles\",\"byakugan\":false,\"portee\":50.0}");
            int avant = demandesOn;
            ByakuganClient.basculer();
            verifie(demandesOn == avant && !ByakuganClient.actif(), "sans droit : la touche O ne fait rien, rien n'est envoyé");
            DojutsuCanal.recevoir("{\"v\":1,\"t\":\"regles\",\"byakugan\":true,\"portee\":50.0}");
        }, "personnage sans droit");

        // ── 2. deux personnes derrière un mur ──────────────────────────────────────────────────────────────────────
        pas(1, mc -> mur(mc, true), "mur");
        vue(-44, true, 0, -8, -90f, 4f, "devant le mur");
        pas(1, mc -> basculer(), "O");
        attendre(mc -> ByakuganClient.niveau() >= 0.999f, 40, "vision établie");
        pas(10, mc -> {
            int n = 0;
            // (les étiquettes ne viennent pas au client : les deux personnes du mur sont à l'est de lui, entre z − 15 et z − 1)
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.LivingEntity && e != mc.player && e.getX() > ox - 34
                    && e.getZ() > oz - 15 && e.getZ() < oz - 1 && e.getY() > P - 2 && ByakuganClient.cachee(e)) n++;
            }
            verifie(n == 2, "deux personnes vues derrière le mur (" + n + ")");
            shot(mc, "byak_mur_deux");
        }, "capture derrière le mur");
        pas(1, mc -> basculer(), "O : fermeture");
        attendre(mc -> !ByakuganClient.filtreVoulu(), 40, "fermé");
        pas(1, mc -> mur(mc, false), "mur retiré");

        // ── 3. rue façon Konoha, et le fondu image par image ───────────────────────────────────────────────────────
        vue(-46, true, 0, +25, -90f, 2f, "rue");
        paire("byak_jour_rue", true);
        if (complet) {
        pas(1, mc -> { h = Util.getMillis(); ByakuganClient.horloge = () -> h; basculer(); debutFondu = h; }, "O, horloge figée");
        attendre(mc -> ByakuganClient.actif(), 10, "acceptée");
        for (long ms : new long[] {50, 100, 200, 350}) {
            pas(2, mc -> { }, "image");
            final long cible = ms;
            pas(0, mc -> h = debutFondu + cible, "t = " + ms + " ms");
            pas(3, mc -> {
                verifie(true, String.format(Locale.ROOT, "entrée %d ms : niveau %.2f", cible, ByakuganClient.niveau()));
                shot(mc, "byak_fondu_entree_" + String.format(Locale.ROOT, "%03d", cible));
            }, "capture");
        }
        pas(1, mc -> {
            float n = niveauA(debutFondu + 350);
            verifie(n >= 0.88f && n <= 0.92f, String.format(Locale.ROOT, "90 %% du fondu à 0,35 s (%.3f)", n));
            h = debutFondu + 2000;
        }, "fondu d'entrée");
        pas(3, mc -> { basculer(); debutFondu = h; }, "O : fermeture");
        for (long ms : new long[] {90, 180, 260}) {
            final long cible = ms;
            pas(2, mc -> { }, "image");
            pas(0, mc -> h = debutFondu + cible, "t = " + ms + " ms");
            pas(4, mc -> {
                float attendu = Math.max(0f, 1f - cible / (float) ByakuganClient.SORTIE_MS);
                verifie(Math.abs(ByakuganClient.niveau() - attendu) < 0.03f,
                    String.format(Locale.ROOT, "sortie %d ms : niveau %.2f (attendu %.2f)", cible, ByakuganClient.niveau(), attendu));
                shot(mc, "byak_fondu_sortie_" + String.format(Locale.ROOT, "%03d", cible));
            }, "capture");
        }
        pas(1, mc -> {
            h = debutFondu + 280;
            verifie(ByakuganClient.niveau() <= 0.05f, "sortie finie à 0,27 s");
            ByakuganClient.horloge = Util::getMillis;
        }, "horloge rendue");
        attendre(mc -> !ByakuganClient.filtreAffiche(), 20, "filtre retiré");
        }
        // derrière une maison
        vue(-30, true, 0, +25, -116f, 3f, "rue, vers une maison");
        paire("byak_rue_maison");

        // ── 4. forêt ───────────────────────────────────────────────────────────────────────────────────────────────
        vue(-46, true, 0, +72, -90f, 2f, "forêt");
        paire("byak_jour_foret", true);
        // feuilles pleines et plantes cachées pendant la vision seulement, l'option du joueur jamais touchée
        pas(1, mc -> { feuillesAvant = mc.options.cutoutLeaves().get(); basculer(); }, "O");
        attendre(mc -> ByakuganClient.niveau() >= 0.999f, 40, "vision établie");
        pas(2, mc -> verifie(FeuillagePlein.actif() && mc.options.cutoutLeaves().get() == feuillesAvant,
            "vision : feuilles pleines, option « Feuilles découpées » du joueur inchangée"), "feuilles pleines");
        pas(1, mc -> basculer(), "O : fermeture");
        attendre(mc -> !ByakuganClient.filtreVoulu(), 40, "fermé");
        pas(2, mc -> verifie(!FeuillagePlein.actif() && mc.options.cutoutLeaves().get() == feuillesAvant,
            "fin du fondu : feuillage du joueur rendu"), "feuillage rendu");

        // ── 5. images par seconde : même vue, sans et avec, alternées ──────────────────────────────────────────────
        if (complet) {
        pas(1, mc -> {
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
        }, "images par seconde libres");
        mesure("sans", false);
        mesure("avec", true);
        mesure("sans", false);
        mesure("avec", true);
        pas(1, mc -> {
            mc.options.framerateLimit().set(limiteAvant);
            mc.options.enableVsync().set(vsyncAvant);
        }, "réglages rendus");
        }

        // ── 6. nuit, dans le champ ─────────────────────────────────────────────────────────────────────────────────
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        vue(-44, true, 0, -25, -90f, 4f, "champ, nuit");
        paire("byak_nuit_champ", true);
        vue(-46, true, 0, +25, -90f, 2f, "rue, nuit");
        paire("byak_nuit_rue");
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");

        // ── 7. grotte fermée ───────────────────────────────────────────────────────────────────────────────────────
        vue(-12, false, -30, -64, -90f, 0f, "grotte");
        pas(20, mc -> { }, "obscurité lissée");
        paire("byak_grotte", true);

        // ── 8. concentration puis Byakugan : un seul emplacement de filtre ─────────────────────────────────────────
        if (complet) {
        vue(-46, true, 0, +25, -90f, 2f, "rue");
        pas(2, mc -> ClavierBanc.tenir(true), "concentration tenue");
        attendre(mc -> ModeConcentration.actif() && ModeConcentration.filtreAffiche(), 40, "concentration");
        pas(8, mc -> shot(mc, "byak_concentration_seule"), "capture concentration");
        pas(1, mc -> basculer(), "O pendant la concentration");
        attendre(mc -> ByakuganClient.niveau() >= 0.999f, 40, "vision établie");
        pas(2, mc -> {
            verifie(ModeConcentration.actif() && !ModeConcentration.filtreAffiche() && ByakuganClient.filtreAffiche(),
                "priorité : le Byakugan prend le filtre, la concentration reste active");
            shot(mc, "byak_concentration_puis_byakugan");
        }, "capture priorité");
        pas(1, mc -> basculer(), "O : fermeture");
        attendre(mc -> ModeConcentration.filtreAffiche(), 40, "la concentration reprend le filtre");
        pas(1, mc -> { ClavierBanc.tenir(false); ClavierBanc.retirer(); }, "concentration relâchée");
        attendre(mc -> !ModeConcentration.filtreVoulu(), 40, "concentration finie");

        // ── 9. KO : coupure nette ──────────────────────────────────────────────────────────────────────────────────
        pas(1, mc -> basculer(), "O");
        attendre(mc -> ByakuganClient.niveau() >= 0.999f, 40, "vision établie");
        pas(1, mc -> {
            demandesOff = 0;
            KoClient.update(new KoPayload((byte) 2, (byte) 0, (short) 60, (short) 60, false, (byte) 0, 0f, (short) 0,
                (short) 0, false, false));
        }, "KO");
        attendre(mc -> KoClient.actif(), 40, "KO à l'écran");
        pas(2, mc -> {
            verifie(!ByakuganClient.actif() && ByakuganClient.niveau() == 0f && demandesOff == 1,
                "KO : Byakugan coupé net, fin envoyée au serveur (" + demandesOff + ")");
            verifie(!ByakuganClient.filtreAffiche(), "KO : le filtre du KO a l'emplacement");
            verifie(!FeuillagePlein.actif(), "KO : feuillage du joueur rendu");
            KoClient.clear();
        }, "coupure au KO");
        attendre(mc -> !KoClient.actif(), 60, "KO fini");

        // ── 10. 1366 × 768 ─────────────────────────────────────────────────────────────────────────────────────────
        pas(2, mc -> { mc.getWindow().setWindowed(1366, 768); mc.resizeGui(); }, "fenêtre 1366 × 768");
        pas(25, mc -> { }, "fenêtre posée");
        vue(-46, true, 0, +25, -90f, 2f, "rue");
        paire("byak_1366_rue");
        vue(-44, true, 0, -25, -90f, 4f, "champ");
        paire("byak_1366_champ");
        vue(-46, true, 0, +72, -90f, 2f, "forêt");
        paire("byak_1366_foret", true);
        pas(2, mc -> { mc.getWindow().setWindowed(1920, 1080); mc.resizeGui(); }, "fenêtre 1920 × 1080");
        pas(20, mc -> { }, "fenêtre posée");
        }

        // ── remise en état ─────────────────────────────────────────────────────────────────────────────────────────
        pas(5, mc -> {
            DojutsuCanal.debugServeur = null;
            ByakuganClient.horloge = Util::getMillis;
            ByakuganClient.clear();
            RebornCamera.INSTANCE.setMode(cameraAvant);
            var c = mc.player.connection;
            c.sendCommand("kill @e[tag=byak5]");
            c.sendCommand("gamerule doDaylightCycle true");
            if (heure >= 0) c.sendCommand("time set " + heure);
            c.sendCommand(pluie ? "weather rain" : "weather clear");
            c.sendCommand("difficulty " + difficulte);
            c.sendCommand(String.format("tp @s %d %d %d 0 8", ox, oy, oz));
            c.sendCommand("gamemode " + mode);
            RebornSession.debugForceRp = false;
        }, "remise en état");
        pas(10, mc -> {
            System.out.println("[byak] ===== mesures =====");
            for (String l : BILAN) System.out.println("[byak] " + l);
            System.out.println("[byak] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        demarrer();
    }

    /**
     * {@code REBORN_LOT5_DEBUG=byak_vues} : sans et avec le Byakugan sur des vues d'un vrai monde (une copie de Konoha, par
     * exemple), données par {@code REBORN_BYAK_VUES="nom:x,y,z,lacet,tangage[,heure];…"} — jamais écrites dans le dépôt.
     * Captures {@code byak_lieu_<nom>_sans.png} et {@code byak_lieu_<nom>.png}.
     */
    private static void initVues(String spec) {
        pas(5, mc -> {
            RebornSession.debugForceRp = true;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            mc.player.connection.sendCommand("gamemode creative");
            mc.player.connection.sendCommand("weather clear");
            cameraAvant = RebornCamera.INSTANCE.mode();
            // en vol : chaque vue garde la hauteur donnée
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            DojutsuCanal.debugServeur = Lot5ByakDebug::serveur;
            DojutsuCanal.recevoir("{\"v\":1,\"t\":\"regles\",\"byakugan\":true,\"portee\":50.0}");
            // sans fondu d'apparition des chunks le temps du banc : un grand monde arrive par morceaux, et un houppier encore
            // à demi transparent à 130 blocs sortait en fantôme pâle sur les captures (rendu à la fin)
            fonduChunksAvant = mc.options.chunkSectionFadeInTime().get();
            mc.options.chunkSectionFadeInTime().set(0.0);
        }, "préparation");
        for (String v : spec.split(";")) {
            String[] nv = v.split(":", 2);
            if (nv.length < 2) continue;
            String[] p = nv[1].split(",");
            if (p.length < 5) continue;
            String nom = nv[0].trim();
            // hauteur « sol » : posé sur le sol réel (feuillages exclus), lu une fois les chunks arrivés
            boolean sol = "sol".equalsIgnoreCase(p[1].trim());
            pas(1, mc -> {
                var c = mc.player.connection;
                if (p.length >= 6) c.sendCommand("time set " + p[5].trim());
                c.sendCommand(String.format(Locale.ROOT, "tp @s %s %s %s %s %s", p[0].trim(), sol ? "200" : p[1].trim(), p[2].trim(), p[3].trim(), p[4].trim()));
                // en vol à chaque vue (le changement de mode de jeu du serveur peut le couper) : la hauteur donnée tient
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
                ByakuganClient.sombreForce = null;
                RebornCamera.INSTANCE.initOrientation(Float.parseFloat(p[3].trim()), Float.parseFloat(p[4].trim()));
            }, "vue : " + nom);
            pas(6, mc -> {
                RebornCamera.INSTANCE.initOrientation(Float.parseFloat(p[3].trim()), Float.parseFloat(p[4].trim()));
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
            }, "caméra");
            // chunks arrivés et fondus (un chunk qui arrive apparaît en fondu : à 90 images, le bout d'un toit à ~160 blocs
            // en était encore voilé)
            pas(240, mc -> { }, "chunks");
            if (sol) {
                pas(1, mc -> {
                    int x = (int) Math.floor(Double.parseDouble(p[0].trim())), z = (int) Math.floor(Double.parseDouble(p[2].trim()));
                    // (carte des hauteurs « sans feuilles » : tenue par le serveur intégré seulement)
                    var srv = mc.getSingleplayerServer();
                    int y = srv != null
                        ? srv.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                        : mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                    System.out.println("[byak] " + nom + " : sol à y = " + y);
                    mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %s %d %s %s %s", p[0].trim(), y, p[2].trim(), p[3].trim(), p[4].trim()));
                    RebornCamera.INSTANCE.initOrientation(Float.parseFloat(p[3].trim()), Float.parseFloat(p[4].trim()));
                }, "au sol");
                pas(6, mc -> RebornCamera.INSTANCE.initOrientation(Float.parseFloat(p[3].trim()), Float.parseFloat(p[4].trim())), "caméra");
                pas(40, mc -> { }, "chunks");
            }
            paire("byak_lieu_" + nom, true);
        }
        pas(5, mc -> {
            mc.options.chunkSectionFadeInTime().set(fonduChunksAvant);
            DojutsuCanal.debugServeur = null;
            ByakuganClient.clear();
            RebornCamera.INSTANCE.setMode(cameraAvant);
            mc.player.connection.sendCommand("gamemode " + mode);
            RebornSession.debugForceRp = false;
            System.out.println("[byak] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        demarrer();
    }

    /**
     * {@code REBORN_LOT5_DEBUG=byak_entites} : de vraies bêtes (villageois, zombie du désert, cochon, vache, mouton ; sans IA)
     * derrière le mur du champ, vues au travers ({@code byak_entites_mur}), puis à découvert, de près et à ~20 blocs
     * ({@code byak_entites_proche}). Difficulté facile le temps du banc (le paisible retire les monstres), rendue ensuite.
     */
    private static void initEntites() {
        pas(5, mc -> {
            var c = mc.player.connection;
            RebornSession.debugForceRp = true;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            difficulte = mc.level.getDifficulty().getSerializedName();
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            c.sendCommand("difficulty easy");
            ox = mc.player.getBlockX();
            oy = mc.player.getBlockY();
            oz = mc.player.getBlockZ();
            cameraAvant = RebornCamera.INSTANCE.mode();
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
        }, "préparation");
        pas(4, Lot5ByakDebug::batirChamp, "plateau");
        pas(4, mc -> {
            cmd(mc, "kill @e[tag=byak5]");
            cmd(mc, "kill @e[type=minecraft:item,distance=..300]");
            mur(mc, true);
            bete(mc, "villager", ox - 30.5, oz - 9.5, 90f);
            bete(mc, "husk", ox - 26.5, oz - 5.5, 90f);
            bete(mc, "pig", ox - 22.5, oz - 11.5, 60f);
        }, "bêtes derrière le mur");
        pas(40, mc -> {
            DojutsuCanal.debugServeur = Lot5ByakDebug::serveur;
            DojutsuCanal.recevoir("{\"v\":1,\"t\":\"regles\",\"byakugan\":true,\"portee\":50.0}");
        }, "serveur simulé");
        vue(-44, true, 0, -8, -90f, 4f, "devant le mur");
        pas(2, mc -> shot(mc, "byak_entites_mur_sans"), "capture sans");
        pas(1, mc -> basculer(), "O");
        attendre(mc -> ByakuganClient.niveau() >= 0.999f, 40, "vision établie");
        pas(10, mc -> {
            int n = 0;
            for (var e : mc.level.entitiesForRendering()) if (ByakuganClient.cachee(e)) n++;
            verifie(n == 3, "trois bêtes vues derrière le mur (" + n + ")");
            shot(mc, "byak_entites_mur");
        }, "capture derrière le mur");
        pas(1, mc -> basculer(), "O : fermeture");
        attendre(mc -> !ByakuganClient.filtreVoulu(), 40, "fermé");
        pas(4, mc -> {
            mur(mc, false);
            cmd(mc, "kill @e[tag=byak5]");
            cmd(mc, "kill @e[type=minecraft:item,distance=..300]");
            bete(mc, "villager", ox - 40.5, oz - 9.5, 120f);
            bete(mc, "husk", ox - 39.5, oz - 5.5, 60f);
            bete(mc, "cow", ox - 24.5, oz - 18.5, 30f);
            bete(mc, "sheep", ox - 24.5, oz + 2.5, 150f);
        }, "bêtes à découvert");
        // le nuage de la mort des bêtes retirées (et ce qu'elles lâchent) se dissipe avant la capture
        pas(240, mc -> cmd(mc, "kill @e[type=minecraft:item,distance=..300]"), "bêtes posées");
        pas(2, mc -> shot(mc, "byak_entites_proche_sans"), "capture sans");
        pas(1, mc -> basculer(), "O");
        attendre(mc -> ByakuganClient.niveau() >= 0.999f, 40, "vision établie");
        pas(10, mc -> shot(mc, "byak_entites_proche"), "capture à découvert");
        pas(1, mc -> basculer(), "O : fermeture");
        attendre(mc -> !ByakuganClient.filtreVoulu(), 40, "fermé");
        pas(5, mc -> {
            DojutsuCanal.debugServeur = null;
            ByakuganClient.clear();
            RebornCamera.INSTANCE.setMode(cameraAvant);
            var c = mc.player.connection;
            c.sendCommand("kill @e[tag=byak5]");
            c.sendCommand("difficulty " + difficulte);
            c.sendCommand(String.format("tp @s %d %d %d 0 8", ox, oy, oz));
            c.sendCommand("gamemode " + mode);
            RebornSession.debugForceRp = false;
            System.out.println("[byak] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        demarrer();
    }

    /** Une vraie bête, sans IA ni bruit, posée sur le plateau. */
    private static void bete(Minecraft mc, String type, double x, double z, float lacet) {
        cmd(mc, "summon %s %.1f %d %.1f {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[%.1ff,0f],Tags:[\"byak5\"]}",
            type, x, P, z, lacet);
    }

    /**
     * {@code REBORN_LOT5_DEBUG=byak_fps} : coût du filtre seul, dans la forêt du banc sans personne (ni silhouette, ni
     * contour vanilla) : quatre alternances sans / avec, 6 s chacune.
     */
    private static void initFps() {
        pas(5, mc -> {
            var c = mc.player.connection;
            RebornSession.debugForceRp = true;
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            ox = mc.player.getBlockX();
            oy = mc.player.getBlockY();
            oz = mc.player.getBlockZ();
            cameraAvant = RebornCamera.INSTANCE.mode();
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            limiteAvant = mc.options.framerateLimit().get();
            vsyncAvant = mc.options.enableVsync().get();
        }, "préparation");
        pas(4, Lot5ByakDebug::batirChamp, "plateau");
        pas(4, Lot5ByakDebug::batirForet, "forêt");
        pas(4, mc -> mc.player.connection.sendCommand("kill @e[tag=byak5]"), "personne");
        pas(40, mc -> {
            DojutsuCanal.debugServeur = Lot5ByakDebug::serveur;
            DojutsuCanal.recevoir("{\"v\":1,\"t\":\"regles\",\"byakugan\":true,\"portee\":50.0}");
        }, "serveur simulé");
        vue(-46, true, 0, +72, -90f, 2f, "forêt");
        pas(1, mc -> { mc.options.framerateLimit().set(260); mc.options.enableVsync().set(false); }, "libres");
        for (int i = 0; i < 4; i++) { mesure("sans", false); mesure("avec", true); }
        pas(5, mc -> {
            mc.options.framerateLimit().set(limiteAvant);
            mc.options.enableVsync().set(vsyncAvant);
            DojutsuCanal.debugServeur = null;
            ByakuganClient.clear();
            RebornCamera.INSTANCE.setMode(cameraAvant);
            mc.player.connection.sendCommand(String.format("tp @s %d %d %d 0 8", ox, oy, oz));
            RebornSession.debugForceRp = false;
            for (String l : BILAN) System.out.println("[byak] " + l);
            System.out.println("[byak] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        demarrer();
    }

    /**
     * Tick du banc, et images comptées par une couche HUD du banc, dessinée en dernier : une étape ne part qu'après
     * {@value #IMAGES_ENTRE_ETAPES} images rendues depuis la précédente. Fenêtre en arrière-plan, le jeu ralentit ses images
     * et plusieurs ticks passent par image : au 10/11, une capture prise sur les ticks seuls reprenait l'image d'avant
     * (traits identiques à la distance brute).
     */
    private static void demarrer() {
        ClientTickEvents.END_CLIENT_TICK.register(Lot5ByakDebug::tick);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "lot5-byak-banc"), (g, dt) -> image());
    }

    private static int images, imagesEtape;
    private static final int IMAGES_ENTRE_ETAPES = 3;

    /* ================================================================ séries */

    private static long debutFondu;
    private static boolean feuillesAvant = true;

    /** Le joueur posé, regard fixé, chunks laissés arriver. */
    /** Décalages depuis l'origine, lus au moment du pas (l'origine n'est connue qu'à la préparation). */
    private static void vue(int dx, boolean plateau, int dy, int dz, float lacet, float tangage, String nom) {
        pas(1, mc -> {
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %d.5 %d %d.5 %.1f %.1f",
                ox + dx, plateau ? P : oy + dy, oz + dz, lacet, tangage));
            RebornCamera.INSTANCE.initOrientation(lacet, tangage);
        }, "vue : " + nom);
        pas(6, mc -> RebornCamera.INSTANCE.initOrientation(lacet, tangage), "caméra");
        pas(40, mc -> { }, "chunks");
    }

    /** Capture hors vision, puis O, vision établie, capture, O. */
    private static void paire(String nom) {
        paire(nom, false);
    }

    /**
     * Capture hors vision, puis O, vision établie, capture ; avec {@code traits}, les traits seuls (blanc sur noir), la
     * distance brute, puis les traits après deux pas d'un quart de bloc en avant ({@code _traits_p1}, {@code _traits_p2} :
     * tenue du trait quand on marche, mesurée hors du jeu contre le décalage attendu) ; O.
     */
    private static void paire(String nom, boolean traits) {
        pas(2, mc -> shot(mc, nom + "_sans"), "capture sans");
        pas(1, mc -> basculer(), "O");
        attendre(mc -> ByakuganClient.niveau() >= 0.999f, 40, "vision établie");
        pas(6, mc -> {
            verifie(ByakuganClient.filtreAffiche(), nom + " : filtre posé");
            shot(mc, nom);
            System.out.println(String.format(Locale.ROOT, "[byak] %s : obscurité %.3f, sous terre %.3f, fin du brouillard %.0f blocs",
                nom, ByakuganClient.sombre(), ByakuganClient.sousTerre(), ByakuganClient.finBrouillard()));
            // personnes et bêtes à moins de 16 blocs (une silhouette inattendue sur une capture se reconnaît au journal)
            for (var e : mc.level.entitiesForRendering()) {
                if (e != mc.player && e instanceof net.minecraft.world.entity.LivingEntity && e.distanceTo(mc.player) < 16f) {
                    System.out.println(String.format(Locale.ROOT, "[byak] %s : %s à %.1f blocs%s", nom, e.getType().toShortString(),
                        e.distanceTo(mc.player), ByakuganClient.cachee(e) ? ", caché" : ""));
                }
            }
            if (traits) ByakuganClient.debugTraits = true;
        }, "capture avec");
        if (traits) {
            pas(3, mc -> {
                shot(mc, nom + "_traits");
                ByakuganClient.debugTraits = false;
                ByakuganClient.debugBrut = true;
            }, "capture des traits");
            // distance brute (étude du filtre hors du jeu, avec la capture « sans » de la même vue)
            pas(3, mc -> {
                shot(mc, nom + "_prof");
                ByakuganClient.debugBrut = false;
                ByakuganClient.debugTraits = true;
                // à plat : un pas vers le bas enfoncerait le joueur dans la dalle d'un bloc et le ferait passer au travers
                mc.player.connection.sendCommand("execute rotated ~ 0 run tp @s ^ ^ ^0.25");
            }, "capture de la distance");
            pas(4, mc -> {
                shot(mc, nom + "_traits_p1");
                mc.player.connection.sendCommand("execute rotated ~ 0 run tp @s ^ ^ ^0.25");
            }, "traits après un quart de bloc");
            pas(4, mc -> { shot(mc, nom + "_traits_p2"); ByakuganClient.debugTraits = false; }, "traits après un demi-bloc");
        }
        pas(1, mc -> basculer(), "O : fermeture");
        attendre(mc -> !ByakuganClient.filtreVoulu() && !ByakuganClient.filtreAffiche(), 40, "fermé");
    }

    /**
     * Images par seconde sur 6 s (la première seconde est jetée) ; à chaque bascule, la saccade des 1,5 s qui suivent
     * (feuilles pleines : chunks recompilés une fois à l'ouverture, une fois à la fin du fondu de sortie).
     */
    private static void mesure(String nom, boolean avec) {
        pas(1, mc -> { if (avec != ByakuganClient.actif()) { enregistrer(); basculer(); } }, "mesure " + nom);
        pas(30, mc -> { saccade("ouverture"); FPS.clear(); }, "attente");
        for (int i = 0; i < 6; i++) pas(20, mc -> FPS.add(mc.getFps()), "seconde");
        pas(1, mc -> {
            double m = FPS.stream().mapToInt(Integer::intValue).average().orElse(0);
            String l = String.format(Locale.ROOT, "%s filtre : %.1f i/s, %.2f ms par image %s", nom, m, m > 0 ? 1000.0 / m : 0, FPS);
            BILAN.add(l);
            System.out.println("[byak] " + l);
        }, "bilan " + nom);
        if (avec) {
            pas(1, mc -> { enregistrer(); basculer(); }, "O : fermeture");
            pas(30, mc -> saccade("fermeture"), "saccade de la fermeture");
        }
    }

    /** Durées des images (couche HUD du banc), enregistrées après une bascule. */
    private static final long[] DUREES = new long[1024];
    private static int nDurees = -1;
    private static long imagePrec;

    private static void enregistrer() {
        nDurees = 0;
        imagePrec = System.nanoTime();
    }

    private static void image() {
        images++;
        if (nDurees < 0 || nDurees >= DUREES.length) return;
        long t0 = System.nanoTime();
        DUREES[nDurees++] = t0 - imagePrec;
        imagePrec = t0;
    }

    /** Saccade d'une bascule : image la plus longue, médiane, images au-delà de 1,5 × la médiane et leur excès cumulé. */
    private static void saccade(String quoi) {
        int n = Math.max(0, nDurees);
        nDurees = -1;
        if (n < 3) return;
        long[] tri = java.util.Arrays.copyOf(DUREES, n);
        java.util.Arrays.sort(tri);
        long med = tri[n / 2], max = tri[n - 1], exces = 0;
        int lentes = 0;
        for (int i = 0; i < n; i++) {
            if (DUREES[i] > med * 3 / 2) { lentes++; exces += DUREES[i] - med; }
        }
        String l = String.format(Locale.ROOT, "saccade %s : %d images en 1,5 s, médiane %.2f ms, la plus longue %.2f ms, "
            + "%d image(s) > 1,5 × médiane, excès cumulé %.1f ms", quoi, n, med / 1e6, max / 1e6, lentes, exces / 1e6);
        BILAN.add(l);
        System.out.println("[byak] " + l);
    }

    private static void basculer() {
        if (ByakuganClient.actif()) debutFondu = h;
        ByakuganClient.basculer();
    }

    private static float niveauA(long now) {
        long hh = h;
        h = now;
        ByakuganClient.frame();
        float n = ByakuganClient.niveau();
        h = hh;
        ByakuganClient.frame();
        return n;
    }

    /* ================================================================ décors */

    private static void cmd(Minecraft mc, String f, Object... a) {
        mc.player.connection.sendCommand(String.format(Locale.ROOT, f, a));
    }

    /** Hauteur du plateau des décors (au-dessus du relief du monde de dev, sous les nuages) ; la grotte est sous terre. */
    static final int P = 165;   // lu aussi par le banc du saut (Lot5Debug), qui retire ce plateau : il ombrait son couloir

    private static void personne(Minecraft mc, double x, double y, double z, float lacet, String tag) {
        cmd(mc, "summon mannequin %.1f %.1f %.1f {Rotation:[%.1ff,0f],Tags:[\"byak5\",\"%s\"]}", x, y, z, lacet, tag);
    }

    /**
     * Plateau de 97 × 145 blocs posé dans le ciel, près de l'origine (chunks chargés par le serveur) : le relief du monde de
     * dev (montagnes, grottes, surplombs) ne fait ni ombre ni trou. Champ et mur au nord, rue au milieu, forêt au sud.
     */
    private static void batirChamp(Minecraft mc) {
        cmd(mc, "kill @e[tag=byak5]");
        for (int x = ox - 48; x <= ox + 48; x += 16) {
            for (int z = oz - 48; z <= oz + 96; z += 16) {
                cmd(mc, "fill %d %d %d %d %d %d minecraft:air", x, P, z, Math.min(ox + 48, x + 15), P + 24, Math.min(oz + 96, z + 15));
            }
        }
        cmd(mc, "fill %d %d %d %d %d %d minecraft:grass_block", ox - 48, P - 1, oz - 48, ox + 48, P - 1, oz + 96);
        // les bêtes nées d'elles-mêmes sur l'herbe du plateau (moutons…) d'un banc précédent : hors du décor
        cmd(mc, "kill @e[type=!minecraft:player,tag=!byak5,x=%d,y=%d,z=%d,dx=97,dy=30,dz=145]", ox - 48, P - 2, oz - 48);
        personne(mc, ox - 39.5, P, oz - 23.5, 110f, "byak_champ");   // de près, tournée vers la caméra
        personne(mc, ox - 32.5, P, oz - 26.5, 120f, "byak_champ");
        personne(mc, ox - 22.5, P, oz - 29.5, 70f, "byak_champ");
        personne(mc, ox - 6.5, P, oz - 21.5, 100f, "byak_champ");
        cmd(mc, "summon cow %d.5 %d %d.5 {NoAI:1b,Rotation:[40f,0f],Tags:[\"byak5\"]}", ox - 30, P, oz - 18);
        // les deux personnes du mur (le mur n'est posé que pour sa capture) ; elles regardent le mur
        personne(mc, ox - 30.5, P, oz - 9.5, 90f, "byak_mur");
        personne(mc, ox - 17.5, P, oz - 6.5, 90f, "byak_mur");
        cmd(mc, "kill @e[type=minecraft:item,distance=..300]");
    }

    private static void mur(Minecraft mc, boolean on) {
        cmd(mc, "fill %d %d %d %d %d %d minecraft:%s", ox - 34, P, oz - 15, ox - 34, P + 4, oz - 1, on ? "stone_bricks" : "air");
    }

    /** Rue façon Konoha : chaussée de pierre, maisons de bois et d'enduit, toits sombres, lanternes, passants. */
    private static void batirRue(Minecraft mc) {
        cmd(mc, "fill %d %d %d %d %d %d minecraft:polished_andesite", ox - 48, P - 1, oz + 22, ox + 48, P - 1, oz + 28);
        cmd(mc, "fill %d %d %d %d %d %d minecraft:stone_bricks", ox - 48, P - 1, oz + 21, ox + 48, P - 1, oz + 21);
        cmd(mc, "fill %d %d %d %d %d %d minecraft:stone_bricks", ox - 48, P - 1, oz + 29, ox + 48, P - 1, oz + 29);
        for (int i = 0; i < 7; i++) {
            int x0 = ox - 40 + i * 12;
            maison(mc, x0, oz + 13, oz + 19, oz + 19, i % 2 == 0);
            maison(mc, x0 + 4, oz + 31, oz + 37, oz + 31, i % 2 == 1);
            cmd(mc, "setblock %d %d %d minecraft:spruce_fence", x0 + 9, P, oz + 20);
            cmd(mc, "setblock %d %d %d minecraft:lantern", x0 + 9, P + 1, oz + 20);
        }
        personne(mc, ox - 34.5, P, oz + 24.5, 80f, "byak_rue");
        personne(mc, ox - 20.5, P, oz + 26.5, 110f, "byak_rue");
        personne(mc, ox - 3.5, P, oz + 24.5, 90f, "byak_rue");
        // une personne dans une maison (vue à travers ses murs)
        personne(mc, ox - 12.5, P, oz + 16.5, 0f, "byak_maison");
    }

    private static void maison(Minecraft mc, int x0, int z1, int z2, int zPorte, boolean enduit) {
        int x1 = x0 + 6;
        cmd(mc, "fill %d %d %d %d %d %d minecraft:%s hollow", x0, P, z1, x1, P + 3, z2, enduit ? "white_terracotta" : "oak_planks");
        for (int[] c : new int[][] {{x0, z1}, {x1, z1}, {x0, z2}, {x1, z2}}) {
            cmd(mc, "fill %d %d %d %d %d %d minecraft:spruce_log", c[0], P, c[1], c[0], P + 3, c[1]);
        }
        cmd(mc, "fill %d %d %d %d %d %d minecraft:dark_oak_planks", x0 - 1, P + 4, z1 - 1, x1 + 1, P + 4, z2 + 1);
        cmd(mc, "fill %d %d %d %d %d %d minecraft:dark_oak_planks", x0, P + 5, z1, x1, P + 5, z2);
        cmd(mc, "fill %d %d %d %d %d %d minecraft:dark_oak_slab", x0 + 1, P + 6, z1 + 1, x1 - 1, P + 6, z2 - 1);
        cmd(mc, "fill %d %d %d %d %d %d minecraft:air", x0 + 3, P, zPorte, x0 + 3, P + 1, zPorte);
        cmd(mc, "setblock %d %d %d minecraft:glass_pane", x0 + 1, P + 2, zPorte);
        cmd(mc, "setblock %d %d %d minecraft:glass_pane", x0 + 5, P + 2, zPorte);
    }

    private static void batirForet(Minecraft mc) {
        String[] arbres = {"oak", "fancy_oak", "birch", "oak", "spruce", "fancy_oak"};
        int i = 0;
        for (int x = ox - 34; x <= ox + 44; x += 7) {
            for (int z = oz + 52; z <= oz + 92; z += 7) {
                if (Math.abs(z - (oz + 72)) <= 3 && x < ox - 8) continue;   // une allée devant la caméra
                int jx = (i * 37) % 5 - 2, jz = (i * 53) % 5 - 2;
                cmd(mc, "place feature minecraft:%s %d %d %d", arbres[i % arbres.length], x + jx, P, z + jz);
                i++;
            }
        }
        personne(mc, ox - 38.5, P, oz + 72.5, 100f, "byak_foret");
        personne(mc, ox - 26.5, P, oz + 77.5, 80f, "byak_foret");
        personne(mc, ox - 12.5, P, oz + 66.5, 90f, "byak_foret");
        cmd(mc, "kill @e[type=minecraft:item,distance=..300]");
    }

    /** Grotte fermée sous le sol : couloir de 5 × 4 sur 36 blocs, une salle, une torche au fond, deux personnes. */
    private static void batirGrotte(Minecraft mc) {
        int y = oy - 30;
        cmd(mc, "fill %d %d %d %d %d %d minecraft:stone", ox - 24, y - 3, oz - 72, ox + 18, y + 7, oz - 56);
        cmd(mc, "fill %d %d %d %d %d %d minecraft:air", ox - 21, y, oz - 66, ox + 14, y + 3, oz - 62);
        cmd(mc, "fill %d %d %d %d %d %d minecraft:air", ox - 6, y, oz - 68, ox + 2, y + 4, oz - 60);
        cmd(mc, "fill %d %d %d %d %d %d minecraft:cobblestone", ox - 21, y - 1, oz - 66, ox + 14, y - 1, oz - 62);
        cmd(mc, "setblock %d %d %d minecraft:wall_torch[facing=west]", ox + 14, y + 2, oz - 64);
        personne(mc, ox - 2.5, y, oz - 64.5, 90f, "byak_grotte");
        personne(mc, ox + 9.5, y, oz - 63.5, 90f, "byak_grotte");
    }

    /* ================================================================ serveur simulé (ShinobiCore ByakuganService) */

    private static void serveur(JsonObject o) {
        boolean on = o.has("on") && o.get("on").getAsBoolean();
        if (on) {
            demandesOn++;
            FILE.add("{\"v\":1,\"t\":\"byakugan\",\"on\":true,\"portee\":50.0}");
        } else {
            demandesOff++;
            FILE.add("{\"v\":1,\"t\":\"byakugan\",\"on\":false}");
        }
    }

    /* ================================================================ outils */

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[byak] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        ETAPES.add(new Etape(delai, mc -> { action.accept(mc); return true; }, nom));
    }

    private static void attendre(Predicate<Minecraft> cond, int max, String nom) {
        ETAPES.add(new Etape(1, mc -> {
            if (cond.test(mc)) { attente = 0; return true; }
            if (++attente > max) { attente = 0; verifie(false, "attente trop longue : " + nom); return true; }
            return false;
        }, "attente : " + nom));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        System.out.println("[byak] capture " + name);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        mc.getFramerateLimitTracker().onInputReceived();
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        String r = FILE.poll();
        if (r != null) DojutsuCanal.recevoir(r);
        t++;
        if (t == 0) prochaine = 60 + ETAPES.get(0).delai();
        if (etape >= ETAPES.size() || t < prochaine) return;
        if (attente == 0 && images - imagesEtape < IMAGES_ENTRE_ETAPES) return;
        Etape e = ETAPES.get(etape);
        if (attente == 0) System.out.println("[byak] " + e.nom());
        if (!e.action().test(mc)) return;
        imagesEtape = images;
        etape++;
        if (etape < ETAPES.size()) prochaine = t + ETAPES.get(etape).delai();
    }
}
