package fr.reborn.hud.menu.esc;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Banc d'essai du menu Échap e5, <b>inactif en production</b> : {@code REBORN_ESC_DEBUG=reprendre|echap|boutons|captures|tout}
 * (monde solo de dev, jamais de serveur). Ouvre l'écran pause, simule le clic sur CONTINUER (ou la touche Échap, ou
 * chaque entrée et la Boutique puis le retour du sous-écran), attend la fin de l'animation et écrit
 * « [EscDebug] OK … » dans le journal ; « [EscDebug] ECHEC … » sinon. {@code captures} : données fictives (solde
 * 1 250, Discord 1 234, stream en direct, deux charmes actifs), puis une capture par état dans
 * {@code run/screenshots/echap_e5_*.png} (repos, survol de chaque entrée et de chaque carte, un charme, Boutique
 * fermée et ouverte) ; retours du 05/11 ({@code echap_e5_r0197_*} : défilement de la carte Stream, hors ligne, identité
 * masquée, version du serveur) ; {@code REBORN_ESC_STAFF=1} ajoute POSTE DE GARDE. Ferme le jeu à la fin (« [EscDebug] FIN »).
 */
public final class EscDebug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static int t = -1, etape, prochaine;

    private EscDebug() {}

    public static void init() {
        String m = System.getenv("REBORN_ESC_DEBUG");
        if (m == null || m.isBlank()) return;
        boolean tout = "tout".equals(m);
        for (int n = 0; n < 3; n++) {
            if (tout || "reprendre".equals(m)) reprendre(n);
            if (tout || "echap".equals(m)) echap(n);
        }
        if (tout || "reprendre".equals(m)) {
            // clics répétés pendant la fermeture, Échap pendant la fermeture, réouverture pendant la fermeture
            ouvrir();
            pas(25, mc -> { clic(mc, 0); clic(mc, 0); }, "double clic CONTINUER");
            pas(3, mc -> clic(mc, 0), "3e clic pendant la fermeture");
            attendreFerme(20, "clics répétés");
            ouvrir();
            pas(25, mc -> clic(mc, 0), "clic CONTINUER");
            pas(3, EscDebug::echapTouche, "Échap pendant la fermeture");
            attendreFerme(20, "Échap pendant la fermeture");
            ouvrir();
            pas(25, mc -> clic(mc, 0), "clic CONTINUER");
            // Échap déjà à l'écran : pauseGame ne rouvre rien, la fermeture doit aller au bout
            pas(3, mc -> mc.pauseGame(false), "pauseGame pendant la fermeture");
            attendreFerme(20, "pauseGame pendant la fermeture");
            // réouverture dès la fin de la fermeture (≈ 180 ms), puis nouvelle fermeture
            ouvrir();
            pas(25, mc -> clic(mc, 0), "clic CONTINUER");
            pas(6, mc -> mc.pauseGame(false), "réouverture juste après la fermeture");
            pas(2, mc -> verifie(mc.gui.screen() instanceof PauseScreen, "réouverture juste après la fermeture"), "vérif");
            pas(25, mc -> clic(mc, 0), "clic CONTINUER");
            attendreFerme(20, "après réouverture");
            // RETOUR du pied
            ouvrir();
            pas(25, mc -> clicZone(mc, EscTokonoma.H_RETOUR), "clic RETOUR");
            attendreFerme(20, "RETOUR");
        }
        if (tout || "boutons".equals(m)) boutons();
        if (tout || "captures".equals(m)) captures();
        pas(20, mc -> {
            System.out.println("[EscDebug] FIN");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(EscDebug::tick);
    }

    private static void reprendre(int n) {
        ouvrir();
        pas(25, mc -> clic(mc, 0), "clic CONTINUER #" + n);
        attendreFerme(20, "CONTINUER #" + n);
    }

    private static void echap(int n) {
        ouvrir();
        pas(25, EscDebug::echapTouche, "touche Échap #" + n);
        attendreFerme(20, "Échap #" + n);
    }

    /** PARAMÈTRES, SIGNALER, QUITTER et la Boutique : chacun ouvre son écran, Échap y revient. */
    private static void boutons() {
        int[] ids = {EscTokonoma.E_PARAMETRES, EscTokonoma.E_SIGNALER, EscTokonoma.E_QUITTER, EscTokonoma.H_BOUTIQUE};
        String[] noms = {"PARAMETRES", "SIGNALER", "QUITTER", "BOUTIQUE"};
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            String nom = noms[i];
            ouvrir();
            pas(25, mc -> clicZone(mc, id), "clic " + nom);
            pas(15, mc -> verifie(!(mc.gui.screen() instanceof PauseScreen) && mc.gui.screen() != null,
                nom + " ouvre " + nom(mc.gui.screen())), "vérif");
            pas(5, EscDebug::echapTouche, "retour (Échap)");
            pas(15, mc -> verifie(mc.gui.screen() instanceof PauseScreen, "retour de " + nom + " → " + nom(mc.gui.screen())),
                "vérif retour");
            pas(5, mc -> clic(mc, 0), "clic CONTINUER");
            attendreFerme(20, "CONTINUER après " + nom);
        }
    }

    /** Captures pour l'intégrateur (données fictives, aucune API). */
    private static void captures() {
        pas(5, mc -> {
            fr.reborn.hud.monnaie.RbCoins.debug(1250);
            EscData.debug(new EscData.Snapshot(1234, 87,
                List.of(new EscData.PatchNote("1.2.0", "ECHAP ET TOUCHES", "5 NOV 2026", null,
                    "Échap et touches", "1.2.0", "5 nov.")),
                STREAMS_DIRECT, null, false, new EscData.Recompenses(5, 3_600_000L,
                    System.currentTimeMillis() + 2_537_000L, Set.of("boost", "tag"))));
            EscData.debugBoutique = false;
            // valeurs d'exemple : seulement ici (le jeu ne montre que les données du serveur)
            EscTokonoma.debugIdentite = new String[]{"Aki Uchiha", "Chūnin", "konoha"};
            EscData.debugVersionServeur = null;   // version de la dernière patch note (vraie source)
            EscTokonoma.tickerBanc = new int[]{0, 1000};
        }, "données fictives");
        ouvrir();
        pas(25, mc -> EscTokonoma.survolBanc = -1, "repos");
        capture("01_repos_boutique_fermee");
        survol(EscTokonoma.H_BOUTIQUE, "02_survol_boutique_fermee");
        pas(1, mc -> EscData.debugBoutique = true, "boutique ouverte");
        survol(-1, "03_repos_boutique_ouverte");
        survol(EscTokonoma.H_BOUTIQUE, "04_survol_boutique");
        survol(EscTokonoma.E_CONTINUER, "05_survol_continuer");
        survol(EscTokonoma.E_PARAMETRES, "06_survol_parametres");
        survol(EscTokonoma.E_QUITTER, "07_survol_quitter");
        survol(EscTokonoma.H_CHARME + 2, "08_survol_charme_tag");
        survol(EscTokonoma.H_STREAM, "09_survol_stream");
        survol(EscTokonoma.H_BLOG, "10_survol_devblog");
        survol(EscTokonoma.H_DISCORD, "11_survol_discord");
        survol(EscTokonoma.H_RETOUR, "12_survol_retour");
        pas(1, mc -> EscTokonoma.survolBanc = -1, "fin des survols");
        retours0197();
        pas(1, mc -> {
            EscTokonoma.survolBanc = -1;
            EscTokonoma.tickerBanc = null;
            EscData.debugBoutique = null;
        }, "fin des retours");
        pas(2, mc -> clic(mc, 0), "clic CONTINUER");
        pas(3, mc -> shot(mc, "echap_e5_13_fermeture"), "capture fermeture");
        attendreFerme(20, "fermeture après captures");
        // ouverture : deux images à mi-course
        pas(10, mc -> mc.pauseGame(false), "ouvre Échap");
        pas(3, mc -> shot(mc, "echap_e5_14_ouverture"), "capture ouverture");
    }

    /** Trois streamers en direct (dont un nom trop long pour la carte) et une chaîne hors ligne. */
    private static final List<EscData.Stream> STREAMS_DIRECT = List.of(
        new EscData.Stream("MEHDITDONC", "https://twitch.tv/mehditdonc", true, "EPISODE 67", "mehditdonc", "Épisode 67"),
        new EscData.Stream("KAMETO", "https://twitch.tv/kameto", false, null, "kameto", null),
        new EscData.Stream("JLTOMY", "https://twitch.tv/jltomy", true, "BONSOIR", "jltomy", "Bonsoir"),
        new EscData.Stream("REBORN_ROLEPLAY_OFFICIE", "https://twitch.tv/reborn", true, "PATROUILLE",
            "Reborn_Roleplay_Officiel", "Patrouille de nuit"));

    /**
     * Retours du 05/11 (0.4.197) : carte Stream qui fait défiler les streamers en direct (1, 2, 3, sortie, entrée,
     * survol), personne en direct, défilement réel (le streamer change seul après 4 s, pas au survol), identité
     * masquée sans personnage, pied sans version du serveur.
     */
    private static void retours0197() {
        String p = "r0197_";
        for (int i = 0; i < 3; i++) {
            int n = i;
            pas(1, mc -> EscTokonoma.tickerBanc = new int[]{n, 1000}, "stream " + (n + 1));
            capture(p + "stream_" + (n + 1));
        }
        pas(1, mc -> EscTokonoma.tickerBanc = new int[]{0, 3900}, "stream 1 qui sort");
        capture(p + "stream_sortie");
        pas(1, mc -> EscTokonoma.tickerBanc = new int[]{1, 110}, "stream 2 qui entre");
        capture(p + "stream_entree");
        pas(1, mc -> EscTokonoma.tickerBanc = new int[]{1, 1000}, "stream 2");
        survol(EscTokonoma.H_STREAM, p + "stream_survol");
        // défilement réel : avance seul, s'arrête au survol
        pas(1, mc -> { EscTokonoma.survolBanc = -1; EscTokonoma.tickerBanc = null; avant = rangTicker(); }, "défilement réel");
        pas(90, mc -> verifie(rangTicker() != avant, "le streamer change seul (" + avant + " → " + rangTicker() + ")"), "vérif");
        pas(1, mc -> { EscTokonoma.survolBanc = EscTokonoma.H_STREAM; avant = rangTicker(); }, "survol Stream");
        pas(100, mc -> verifie(rangTicker() == avant, "survolé, le streamer reste (" + avant + " → " + rangTicker() + ")"), "vérif");
        pas(1, mc -> {
            EscTokonoma.survolBanc = -1;
            EscData.Snapshot s = EscData.get();
            EscData.debug(new EscData.Snapshot(s.discordMembers(), s.discordOnline(), s.patchNotes(),
                List.of(new EscData.Stream("KAMETO", "https://twitch.tv/kameto", false, null, "kameto", null)),
                s.discordInviteUrl(), s.boutiqueOuverte(), s.recompenses()));
        }, "personne en direct");
        capture(p + "stream_hors_ligne");
        pas(1, mc -> {
            EscData.Snapshot s = EscData.get();
            EscData.debug(new EscData.Snapshot(s.discordMembers(), s.discordOnline(), s.patchNotes(), STREAMS_DIRECT,
                s.discordInviteUrl(), s.boutiqueOuverte(), s.recompenses()));
            EscTokonoma.debugIdentite = new String[0];
        }, "sans personnage");
        capture(p + "sans_identite");
        pas(1, mc -> EscData.debugVersionServeur = "", "sans version du serveur");
        capture(p + "sans_identite_sans_version");
        pas(1, mc -> {
            EscTokonoma.debugIdentite = null;
            verifie(true, "identité du jeu (solo, sans serveur) : " + (EscTokonoma.courant() != null
                ? "« " + EscTokonoma.courant().identiteBanc() + " »" : "?"));
        }, "identité réelle");
        capture(p + "identite_reelle_solo");
        pas(1, mc -> {
            String nom = EscTokonoma.courant() != null ? EscTokonoma.courant().identiteBanc() : "?";
            verifie(nom.isEmpty(), "aucun nom d'exemple hors banc (« " + nom + " »)");
            // perso A quitté : sa fiche reste en mémoire, le roster ne porte plus de grade sur la ligne SOI
            fr.reborn.hud.menu.stats.StatsData.update("{\"name\":\"Kenji\",\"clan\":\"Hyuga\",\"village\":\"konoha\","
                + "\"rank\":\"Chūnin\"}");
            fr.reborn.hud.menu.tablist.TablistData.update("{\"players\":[{\"r\":\"SOI\",\"n\":\""
                + mc.player.getName().getString() + "\",\"u\":\"" + mc.player.getUUID() + "\"}]}");
        }, "fiche d'un perso quitté + roster sans perso");
        capture(p + "perso_quitte");
        pas(2, mc -> {
            String nom = EscTokonoma.courant() != null ? EscTokonoma.courant().identiteBanc() : "?";
            verifie(nom.isEmpty(), "Échap : la fiche d'un personnage quitté n'est pas montrée (« " + nom + " »)");
            String sac = fr.reborn.hud.menu.inventory.InventoryScreen.identiteBanc();
            verifie(sac.equals(mc.player.getName().getString()), "sacoche sans personnage : pseudo seul (« " + sac + " »)");
        }, "vérif perso quitté");
        pas(1, mc -> {
            EscData.debugVersionServeur = null;   // pied : version de la dernière patch note (vraie source)
            fr.reborn.hud.menu.tablist.TablistData.update("{\"players\":[{\"r\":\"SOI\",\"n\":\"Aki Uchiha\","
            + "\"g\":\"CHUNIN\",\"v\":\"konoha\",\"u\":\"" + mc.player.getUUID() + "\"}]}");
        }, "roster avec perso actif");
        capture(p + "identite_roster_version");
        pas(2, mc -> {
            String nom = EscTokonoma.courant() != null ? EscTokonoma.courant().identiteBanc() : "?";
            verifie("Aki Uchiha".equals(nom), "Échap : identité du roster (« " + nom + " »)");
            String sac = fr.reborn.hud.menu.inventory.InventoryScreen.identiteBanc();
            verifie("Aki Uchiha · Chūnin · Konohagakure".equals(sac), "sacoche : identité du roster, sans fiche ouverte (« "
                + sac + " »)");
            verifie("1.2.0".equals(EscData.versionServeur()), "pied : version de la dernière patch note (« "
                + EscData.versionServeur() + " »)");
        }, "vérif roster");
        pas(1, mc -> {
            fr.reborn.hud.menu.tablist.TablistData.clear();
            fr.reborn.hud.menu.stats.StatsData.clear();
            EscTokonoma.debugIdentite = new String[]{"Aki Uchiha", "Chūnin", "konoha"};
            EscData.debugVersionServeur = null;   // version de la dernière patch note (vraie source)
        }, "vérif identité");
    }

    private static int avant;

    private static int rangTicker() {
        EscTokonoma e = EscTokonoma.courant();
        return e == null ? -1 : e.rangTickerBanc();
    }

    private static void survol(int id, String nom) {
        pas(2, mc -> EscTokonoma.survolBanc = id, "survol " + nom);
        capture(nom);
    }

    private static void capture(String nom) {
        pas(8, mc -> shot(mc, "echap_e5_" + nom), "capture " + nom);
    }

    // ── construction ──

    private static void ouvrir() {
        pas(20, mc -> mc.pauseGame(false), "ouvre Échap");
        pas(2, mc -> verifie(mc.gui.screen() instanceof PauseScreen, "écran pause ouvert"), "vérif");
    }

    private static void attendreFerme(int ticks, String quoi) {
        pas(ticks, mc -> verifie(mc.gui.screen() == null, quoi + " → écran " + nom(mc.gui.screen())), "vérif fermeture");
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            System.out.println("[EscDebug] " + nom);
            action.accept(mc);
        });
    }

    // ── actions ──

    private static String nom(Screen s) { return s == null ? "aucun" : s.getClass().getSimpleName(); }

    private static void verifie(boolean ok, String quoi) {
        System.out.println("[EscDebug] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static void echapTouche(Minecraft mc) {
        Screen s = mc.gui.screen();
        if (s != null) s.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
    }

    /**
     * Clic gauche sur la n-ième entrée affichée de la colonne (0 = CONTINUER ; sans staff : 1 PERSONNAGE,
     * 2 PARAMÈTRES, 3 SIGNALER, 4 QUITTER ; avec staff, POSTE DE GARDE en 4 et QUITTER en 5).
     */
    public static void clic(Minecraft mc, int i) {
        int[] ids = {EscTokonoma.E_CONTINUER, EscTokonoma.E_PERSONNAGE, EscTokonoma.E_PARAMETRES,
            EscTokonoma.E_SIGNALER, EscTokonoma.E_GARDE, EscTokonoma.E_QUITTER};
        double[] c = EscTokonoma.centreGui(ids[Math.max(0, Math.min(ids.length - 1, i))]);
        if (c == null && i == 4) c = EscTokonoma.centreGui(EscTokonoma.E_QUITTER);   // sans staff, la 5e est QUITTER
        if (c == null) { System.out.println("[EscDebug] (entrée " + i + " absente)"); return; }
        clicGui(mc, c[0], c[1]);
    }

    /** Clic gauche au centre d'une zone de l'Échap ({@code EscTokonoma.E_*}, {@code H_*}). */
    public static void clicZone(Minecraft mc, int id) {
        double[] c = EscTokonoma.centreGui(id);
        if (c == null) { System.out.println("[EscDebug] (zone " + id + " absente)"); return; }
        clicGui(mc, c[0], c[1]);
    }

    /**
     * Clic gauche en pixels de la maquette 1080p (x et y × hauteur / 1080 : la colonne suit la hauteur ; la grille de
     * droite, calée à droite, ne s'y prête pas — utiliser {@link #clicZone}). Gardé pour {@code keybind/TouchesDebug}.
     */
    public static void clicUnites(Minecraft mc, int ux, int uy) {
        var w = mc.getWindow();
        double k = w.getHeight() / 1080.0, gs = Math.max(1, w.getGuiScale());
        clicGui(mc, ux * k / gs, uy * k / gs);
    }

    private static void clicGui(Minecraft mc, double gx, double gy) {
        Screen s = mc.gui.screen();
        if (s == null) { System.out.println("[EscDebug] (pas d'écran pour cliquer)"); return; }
        s.mouseClicked(new MouseButtonEvent(gx, gy, new MouseButtonInfo(0, 0)), false);
        s.mouseReleased(new MouseButtonEvent(gx, gy, new MouseButtonInfo(0, 0)));
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 0) prochaine = 60 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine) return;
        ETAPES.get(etape).accept(mc);
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
