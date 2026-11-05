package fr.reborn.hud.menu.esc;

import fr.reborn.hud.menu.RebornBranding;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import fr.reborn.hud.menu.stats.StatsData;
import fr.reborn.hud.menu.widget.DisconnectConfirmScreen;
import fr.reborn.hud.menu.widget.ReportScreen;
import fr.reborn.hud.monnaie.RbCoins;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import fr.reborn.hud.ui.TexteCache;
import fr.reborn.hud.ui.TexteRaster.Masque;
import fr.reborn.hud.ui.UiAmbience;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import static fr.reborn.hud.menu.esc.EchapArt.*;

/**
 * Menu Échap <b>e5</b> (Game Plan n° 17, lot 3, validé le 05/11 : {@code e5_echap_final.png}). Le jeu reste visible
 * derrière, flouté (le flou des menus de Minecraft, une passe) et voilé en dégradé (sombre à gauche, × 0,5 sous la
 * grille). À gauche, la colonne de la piste A : logo, identité du personnage, PAUSE, CONTINUER, PERSONNAGE,
 * PARAMÈTRES, SIGNALER, (POSTE DE GARDE pour le staff), QUITTER, pied « [Échap] RETOUR » et version. À droite, la
 * grille : la <b>Boutique</b> en vitrine (façade d'échoppe, enseigne BOUTIQUE, solde de RBCoins, rien d'autre ; le clic
 * ouvre la boutique ; fermée : volets tirés, « Bientôt »), les <b>Récompenses</b> en bande longue (les cinq charmes,
 * Actif / À faire, jauge du prochain versement) et trois petites cartes qui redirigent (Stream, Dev blog, Discord =
 * nombre de membres).
 *
 * <p>Une seule chose survolée à la fois. Ouverture 300 ms (la colonne glisse de 6 px, les cartes suivent en fondu,
 * 40 ms l'une après l'autre), fermeture 180 ms d'un bloc (CONTINUER, Échap ou RETOUR). Tout est dessiné en pixels
 * écran ({@link EchapArt}) ; textes au moteur {@code TexteRaster}, une poignée par libellé, rien n'est alloué à
 * chaque image hors des lots d'aplats.
 */
public final class EscTokonoma {

    private static final int OPEN_MS = 300, CLOSE_MS = 180, CARTE_MS = 140, DECALAGE_MS = 40;

    /* ---- entrées de la colonne (identifiants stables : survol, banc) ---- */
    static final int E_CONTINUER = 0, E_PERSONNAGE = 1, E_PARAMETRES = 2, E_SIGNALER = 3, E_GARDE = 4, E_QUITTER = 5;
    private static final String[] LIBELLES = {"CONTINUER", "PERSONNAGE", "PARAMÈTRES", "SIGNALER", "POSTE DE GARDE",
        "QUITTER"};
    private static final int[] LISTE = {E_CONTINUER, E_PERSONNAGE, E_PARAMETRES, E_SIGNALER, E_QUITTER};
    private static final int[] LISTE_STAFF = {E_CONTINUER, E_PERSONNAGE, E_PARAMETRES, E_SIGNALER, E_GARDE, E_QUITTER};

    /* ---- zones de la grille ---- */
    public static final int H_BOUTIQUE = 10, H_STREAM = 11, H_BLOG = 12, H_DISCORD = 13, H_CHARME = 20, H_RETOUR = 30;

    /** Banc de captures : zone survolée imposée ({@code REBORN_ESC_SURVOL=<id>}, client de dev seulement). */
    private static final int SURVOL_FORCE = entierEnv("REBORN_ESC_SURVOL");
    /** Banc : liste du staff imposée ({@code REBORN_ESC_STAFF=1}). */
    private static final boolean STAFF_FORCE = entierEnv("REBORN_ESC_STAFF") == 1;
    /** Bancs ({@code EscDebug}, {@code keybind/TouchesDebug}) : zone survolée imposée à chaud (−1 = aucune). */
    public static int survolBanc = -1;
    /**
     * Zones des bancs : PARAMÈTRES (l'éditeur de HUD et le viseur sont dans Paramètres › Interface depuis le lot 3) et
     * PERSONNAGE (l'entrée remplace la carte d'identité).
     */
    public static final int BANC_INTERFACE = E_PARAMETRES, BANC_PERSONNAGE = E_PERSONNAGE;

    private static int entierEnv(String nom) {
        try {
            String v = System.getenv(nom);
            return v == null || v.isBlank() ? -1 : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Les cinq charmes (ordre et omamori de la maquette e5) : id API, colonne de omamori.png, libellé, aide. */
    private record Charme(String id, int omamori, String libelle, String aide) {}
    private static final Charme[] CHARMES = {
        new Charme("premium", 3, "Premium", "Soutiens le serveur."),
        new Charme("boost", 1, "Boost Discord", "Booste le Discord Reborn."),
        new Charme("tag", 2, "Tag dans le pseudo", "Ajoute le tag Reborn à ton pseudo."),
        new Charme("bio", 0, "Lien dans la bio", "Mets le lien Reborn dans ta bio."),
        new Charme("stream", 4, "Stream Reborn", "Streame sur le serveur."),
    };

    private static final String[] VILLAGE_IDS = {"konoha", "suna", "kiri", "kumo", "iwa", "ame"};
    private static final String[] VILLAGE_TITRES = {"Konohagakure", "Sunagakure", "Kirigakure", "Kumogakure",
        "Iwagakure", "Amegakure"};

    private static final String VERSION = "reborn-hud " + net.fabricmc.loader.api.FabricLoader.getInstance()
        .getModContainer("reborn-hud").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");

    // ── état ──
    private static boolean active;            // un menu Échap Reborn est à l'écran
    private static boolean closeSoundPlayed;  // la fermeture a déjà joué son son
    private static boolean silentStatsRequest;
    /** L'Échap à l'écran (bancs). */
    private static EscTokonoma courant;

    private final Screen screen;
    private final long openedAt = System.currentTimeMillis();
    private long closingAt = -1L;
    private boolean fermetureDemandee;
    private boolean openSoundPlayed;
    private int hovered = -1;
    private final Mise m = new Mise();
    /** Bords des colonnes des charmes (px), mesurés à la dernière image. */
    private final int[] bords = new int[CHARMES.length + 1];
    private int bordsY0, bordsY1 = -1;

    // ── libellés (une poignée de texte chacun) ──
    private final Lib lPause = new Lib(), lNom = new Lib(), lSous = new Lib(), lRetour = new Lib(), lTouche = new Lib(),
        lVersion = new Lib(), lBoutique = new Lib(), lChevron = new Lib(), lSolde = new Lib(), lRb = new Lib(),
        lBientot = new Lib(), lRecTitre = new Lib(), lTaux = new Lib(), lProchain = new Lib(), lMinuteur = new Lib(),
        lAide = new Lib();
    private final Lib[] lEntrees = libs(LIBELLES.length), lCharmes = libs(CHARMES.length),
        lEtats = libs(CHARMES.length), lTitres = libs(3), lLignes = libs(3), lChevrons = libs(3);
    private final TexteCache.Texte filetPause = new TexteCache.Texte();
    private Masque filetMasque;
    private int filetL = -1, filetEp = -1;

    // ── textes calculés quand leur source change (pas de format à chaque image) ──
    private long soldeVu = Long.MIN_VALUE;
    private String soldeTxt = "—";
    private EscData.Snapshot snapVu;
    private boolean snapLu;
    private String blogLigne = "Aucune note", discordTitre = "Discord", discordLigne = "Rejoindre";
    private long minuteurSec = -1;
    private String minuteurTxt = "";
    private int tauxVu = -1;
    private String tauxTxt = "";
    /** Identité de la colonne (nom, rang · village, emblème) : relue quand la fiche ({@link StatsData}) change. */
    private StatsData.Snapshot statsVu;
    private boolean statsLu;
    private String nomTxt = "", sousTxt = "";
    private int villageVu = -1;

    private static Lib[] libs(int n) {
        Lib[] l = new Lib[n];
        for (int i = 0; i < n; i++) l[i] = new Lib();
        return l;
    }

    public EscTokonoma(Screen screen) {
        this.screen = screen;
    }

    /** Appelé à chaque {@code init} de l'écran pause (ouverture, retour d'un sous-écran, redimensionnement). */
    public void init() {
        boolean wasActive = active;
        active = true;
        courant = this;
        closeSoundPlayed = false;
        EscData.refreshIfStale();
        if (StatsData.get() == null
            && net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(fr.reborn.hud.menu.stats.StatsPayload.ID)) {
            silentStatsRequest = true; // identité (nom, village) sans ouvrir la fiche
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new fr.reborn.hud.menu.stats.StatsPayload("open"));
        }
        if (!wasActive) UiAmbience.start("esc.ambience", 0.32f);
    }

    /** La réponse « stats » demandée en silence ne doit pas ouvrir la fiche. */
    public static boolean consumeSilentStatsRequest() {
        boolean s = silentStatsRequest;
        silentStatsRequest = false;
        return s;
    }

    /** Tick client : détecte la sortie du menu (Échap, sous-écran) pour couper l'ambiance. */
    public static void tick(Minecraft mc) {
        if (!active) return;
        Screen cur = mc.gui.screen();
        if (cur instanceof PauseScreen) return;
        active = false;
        UiAmbience.stop();
        if (cur == null && !closeSoundPlayed) RebornSounds.playReborn("esc.close", 1.0f, 0.55f);
    }

    // ═════════════════════════════════════════════════════════════ géométrie

    /** Cotes de l'Échap pour une fenêtre (pixels écran). */
    static final class Mise {
        int W, H, P, X, gx, gy, pied, tx, tw, th, ry, nEntrees;
        double k, kk;
        int[] liste = LISTE;

        void calculer(int W, int H, boolean staff) {
            this.W = W;
            this.H = H;
            k = H / 1080.0;
            int p = Math.max(1, (int) Math.floor(3 * k + 0.25));
            while (p > 1 && (GRILLE_W * p + r(120 * k) + r(470 * k) > W
                || r(150 * k) + (BOUTIQUE_H + GOUTTIERE + RANG_H) * p > H - r(90 * k))) p--;
            P = p;
            kk = P / 3.0;
            X = r(120 * k);
            gx = W - r(120 * k) - GRILLE_W * P;
            gy = r(150 * k);
            ry = gy + (BOUTIQUE_H + GOUTTIERE) * P;
            tx = gx + (RECOMP_W + GOUTTIERE) * P;
            tw = (GRILLE_W - RECOMP_W - GOUTTIERE) * P;
            th = TUILE_H * P;
            pied = H - r(56 * k);
            liste = staff ? LISTE_STAFF : LISTE;
            nEntrees = liste.length;
        }

        /** Centre vertical de la n-ième entrée de la colonne. */
        int yEntree(int n) { return r((428 + 57 * n) * k); }

        int tuileY(int i) { return ry + i * TUILE_PAS * P; }
    }

    private static boolean staff() {
        return STAFF_FORCE || RebornBranding.isStaff() || fr.reborn.hud.staff.StaffClient.modeActif()
            || fr.reborn.hud.staff.StaffClient.snapshot() != null;
    }

    /** Zone sous le point (px écran), ou −1. */
    private int zone(double mx, double my) {
        Mise g = m;
        double k = g.k;
        if (mx >= r(56 * k) && mx < r(436 * k)) {
            for (int n = 0; n < g.nEntrees; n++) {
                int yc = g.yEntree(n);
                if (my >= yc - r(28.5 * k) && my < yc + r(28.5 * k)) return g.liste[n];
            }
        }
        int ys = g.pied;
        if (mx >= g.X - 4 && mx < g.X + r(140 * k) && my >= ys - r(16 * k) && my < ys + r(16 * k)) return H_RETOUR;
        int P = g.P;
        if (dans(mx, my, g.gx, g.gy, GRILLE_W * P, BOUTIQUE_H * P)) return H_BOUTIQUE;
        if (bordsY1 > 0 && my >= bordsY0 && my < bordsY1) {
            for (int i = 0; i < CHARMES.length; i++) if (mx >= bords[i] && mx < bords[i + 1]) return H_CHARME + i;
        }
        for (int i = 0; i < 3; i++) if (dans(mx, my, g.tx, g.tuileY(i), g.tw, g.th)) return H_STREAM + i;
        return -1;
    }

    private static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ═════════════════════════════════════════════════════════════ animation

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float sortie(float t) { float u = 1f - t; return 1f - u * u * u; }

    private float fermeture(long now) {
        return closingAt > 0 ? 1f - clamp01((now - closingAt) / (float) CLOSE_MS) : 1f;
    }

    private float ouverture(long now) { return sortie(clamp01((now - openedAt) / (float) OPEN_MS)); }

    private float aCarte(long now, int i) {
        return sortie(clamp01((now - openedAt - DECALAGE_MS * i) / (float) CARTE_MS)) * fermeture(now);
    }

    // ═════════════════════════════════════════════════════════════ fond : flou de Minecraft + voile

    private static final float[] VX = {0f, .15f, .23f, .30f, .38f, .60f, 1f};
    private static final float[] AX = {.94f, .87f, .80f, .70f, .56f, .48f, .50f};
    private static final float[] VY = {0f, .08f, .30f, .90f, .94f, 1f};
    private static final float[] AY = {.20f, 0f, 0f, 0f, .38f, .66f};

    /**
     * Voile de la piste A (cotes de Ketto à gauche, × 0,5 sous la grille, bande sombre en bas), posé après le flou des
     * menus de Minecraft : deux dégradés d'encre (horizontal puis vertical), dont le produit donne exactement
     * flou × fx × fy + encre × (1 − fx·fy). Il monte avec l'ouverture.
     */
    public void fond(GuiGraphicsExtractor ctx) {
        long now = System.currentTimeMillis();
        float a = ouverture(now) * fermeture(now);
        var win = Minecraft.getInstance().getWindow();
        int W = win.getWidth(), H = win.getHeight();
        DaKit.debutPx(ctx);
        for (int i = 0; i < VX.length - 1; i++) {
            degradeH(ctx, r(VX[i] * W), 0, r(VX[i + 1] * W), H, argb(ENCRE, AX[i] * a), argb(ENCRE, AX[i + 1] * a));
        }
        for (int i = 0; i < VY.length - 1; i++) {
            if (AY[i] <= 0f && AY[i + 1] <= 0f) continue;
            ctx.fillGradient(0, r(VY[i] * H), W, r(VY[i + 1] * H), argb(ENCRE, AY[i] * a), argb(ENCRE, AY[i + 1] * a));
        }
        DaKit.finPx(ctx);
    }

    // ═════════════════════════════════════════════════════════════ rendu

    public void render(GuiGraphicsExtractor ctx, int width, int height, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        if (!openSoundPlayed) { openSoundPlayed = true; RebornSounds.playReborn("esc.open", 1.0f, 0.6f); }
        // Fin de la fermeture : jamais de changement d'écran ici (on est au milieu de l'extraction de l'image) —
        // la fermeture est postée sur la boucle client et faite avant l'image suivante.
        if (closingAt > 0 && now - closingAt >= CLOSE_MS) demanderFermeture();

        var win = Minecraft.getInstance().getWindow();
        m.calculer(win.getWidth(), win.getHeight(), staff());
        double gs = Math.max(1, win.getGuiScale());
        int h = zone(mouseX * gs, mouseY * gs);
        if (SURVOL_FORCE >= 0) h = SURVOL_FORCE;
        if (survolBanc >= 0) h = survolBanc;
        if (closingAt > 0) h = -1;
        if (h != hovered) {
            hovered = h;
            boolean charme = h >= H_CHARME && h < H_CHARME + CHARMES.length;
            if (h >= 0 && now - openedAt >= OPEN_MS) RebornSounds.playReborn(charme ? "esc.charm" : "esc.hover",
                charme ? 1.3f : 1.0f, charme ? 0.18f : 0.3f);
        }
        majTextes(now);

        DaKit.debutPx(ctx);
        float ferme = fermeture(now), ouv = ouverture(now);
        colonne(ctx, ouv * ferme, -r(6 * m.k * (1f - ouv)));
        boutique(ctx, aCarte(now, 0));
        recompenses(ctx, aCarte(now, 1), now);
        for (int i = 0; i < 3; i++) tuile(ctx, i, aCarte(now, 2 + i));
        aide(ctx, aCarte(now, 1));
        DaKit.finPx(ctx);
    }

    /** Textes qui dépendent des données : recalculés seulement quand la donnée change. */
    private void majTextes(long now) {
        StatsData.Snapshot st = StatsData.get();
        if (st != statsVu || !statsLu || nomTxt.isEmpty()) {
            statsVu = st;
            statsLu = true;
            villageVu = village();
            nomTxt = nom();
            sousTxt = sousTitre(villageVu);
        }
        long rb = RbCoins.solde();
        if (rb != soldeVu) { soldeVu = rb; soldeTxt = rb < 0 ? "—" : fmt(rb); }
        EscData.Snapshot s = EscData.get();
        if (s != snapVu || !snapLu) {
            snapVu = s;
            snapLu = true;
            EscData.PatchNote n = s != null && !s.patchNotes().isEmpty() ? s.patchNotes().get(0) : null;
            if (n == null) blogLigne = "Aucune note";
            else {
                String v = n.versionTexte() != null && !n.versionTexte().isBlank() ? n.versionTexte() : null;
                String d = n.dateCourte();
                blogLigne = v != null && d != null ? v + " · " + d : v != null ? v : d != null ? d : "Nouvelle note";
            }
            boolean membres = s != null && s.discordMembers() >= 0;
            discordTitre = membres ? fmt(s.discordMembers()) : "Discord";
            discordLigne = membres ? "membres" : "Rejoindre";
        }
        int taux = EscData.recompenses().taux();
        if (taux != tauxVu) { tauxVu = taux; tauxTxt = "+" + taux + " / heure de jeu"; }
        long sec = (EscData.resteAvantVersement(now) + 999) / 1000;
        if (sec != minuteurSec) {
            minuteurSec = sec;
            minuteurTxt = String.format(Locale.ROOT, "%02d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60);
        }
    }

    // ── colonne de gauche (piste A) ──
    private void colonne(GuiGraphicsExtractor ctx, float a, int dx) {
        if (a <= 0.003f) return;
        double k = m.k, f = Math.max(1, k);
        int X = m.X + dx;
        // logo (réduit d'avance : la rangée de l'atlas la plus proche de 224·k)
        int lw = r(224 * k), row = rangLogo(lw);
        int lh = r(lw * (double) LOGO_H[row] / LOGO_W[row]);
        blit(ctx, LOGO, X - r(4 * k), r(150 * k), lw, lh, 0, LOGO_V[row], LOGO_W[row], LOGO_H[row], LOGO_TW, LOGO_TH, a);
        // identité : emblème du village (or clair), nom, rang · village
        int yc = r(268 * k), vi = villageVu;
        int lx = X;
        if (vi >= 0) {
            int s = Math.max(16, r(26 * k));
            blitTeint(ctx, EMBLEMES, X, yc - s / 2, s, s, vi * 26, 0, 26, 26, 156, 26, argb(OR_CLAIR, a));
            lx = X + s + r(12 * k);
        }
        lNom.ligne(ctx, nomTxt, P_CREME, 18 * k, f, 0, lx, yc - r(8 * k), a);
        String sous = sousTxt;
        if (!sous.isEmpty()) lSous.ligne(ctx, sous, I_DOUX, capInter(11, k), f, 0, lx, yc + r(13 * k), a);
        // PAUSE et son filet d'or
        lPause.ligne(ctx, "PAUSE", P_CREME, 32 * k, f, 2 * k, X, r(342 * k), a);
        int L = r(227 * k), ep = Math.max(1, r(k));
        if (L != filetL || ep != filetEp || filetMasque == null) { filetL = L; filetEp = ep; filetMasque = Masque.filet(L, ep, true); }
        filetPause.forme(filetMasque, OR, k, 0.55f, X - r(5 * k), r(373 * k)).dessinerPx(ctx, a);
        // entrées : bande laquée au survol (coup de pinceau + filet d'or sous l'entrée)
        for (int n = 0; n < m.nEntrees; n++) {
            int id = m.liste[n], y = m.yEntree(n);
            String lib = LIBELLES[id];
            boolean hot = hovered == id, garde = id == E_GARDE;
            double cap = 22 * k, inter = 1.2 * k;
            if (hot) {
                int bw = r(380 * k), bh = r(55 * k);
                blit(ctx, BANDE, r(56 * k) + dx, y - bh / 2, bw, bh, 0, 0, 380, 55, 380, 55, a);
            }
            lEntrees[id].ligne(ctx, lib, hot ? P_CREME : garde ? P_OR : P_REPOS, cap, f, inter, X, y, a);
            if (hot) {
                double w = lEntrees[id].largeur(lib, P_CREME, cap, inter);
                ctx.fill(X, y + r(15 * k), X + (int) Math.round(w), y + r(15 * k) + Math.max(1, r(k)), argb(OR_CLAIR, 0.95f * a));
            }
            if (garde) DaKit.losange(ctx, X - r(20 * k), y, Math.max(1, r(3 * k)), argb(OR, 0.9f * a), argb(ENCRE, 0.4f * a));
        }
        // pied : [Échap] RETOUR, version à droite
        int yp = m.pied, ch = r(22 * k);
        double capT = 10 * k;
        double tw = lTouche.largeur("Échap", TexteCache.Style.TOUCHE, capT, 0);
        int cw = Math.max(ch, (int) Math.ceil(tw + r(ch * 0.45)));
        LotGui l = LotGui.ouvrir(ctx);
        caseTouche(l, X, yp - ch / 2, cw, ch, a);
        l.fermer();
        lTouche.ligne(ctx, "Échap", TexteCache.Style.TOUCHE, capT, f, 0, X + cw / 2.0, yp, a);
        boolean hr = hovered == H_RETOUR;
        lRetour.ligne(ctx, "RETOUR", hr ? B_CREME_G : B_DOUX, capInter(12, k), f, 0.6 * k, X + cw + r(10 * k), yp, a);
        lVersion.ligne(ctx, VERSION, I_GRIS_D, capInter(10, k), f, 0, m.W - r(120 * k), yp, a);
    }

    private static final TexteCache.Style B_CREME_G = new TexteCache.Style(IB, MENU,
        fr.reborn.hud.ui.TexteRaster.cleRgb(CREME), G, true, false);

    private static String nom() {
        StatsData.Snapshot s = StatsData.get();
        String name = s != null ? s.name() : null;
        if (name == null || name.isBlank() || "?".equals(name)) {
            var p = Minecraft.getInstance().player;
            name = p != null ? p.getName().getString() : "";
        }
        return name;
    }

    private static String sousTitre(int vi) {
        StatsData.Snapshot s = StatsData.get();
        String rank = s != null && s.rank() != null && !s.rank().isBlank() && !"?".equals(s.rank()) ? s.rank() : null;
        String vil = vi >= 0 ? VILLAGE_TITRES[vi] : null;
        if (rank == null) return vil == null ? "" : vil;
        return vil == null ? rank : rank + " · " + vil;
    }

    private static int village() {
        StatsData.Snapshot s = StatsData.get();
        if (s == null || s.village() == null) return -1;
        String v = s.village().toLowerCase(Locale.ROOT);
        for (int i = 0; i < VILLAGE_IDS.length; i++) if (v.startsWith(VILLAGE_IDS[i])) return i;
        return -1;
    }

    /** Ombre, halo (survol) et fond nuit d'une carte ; renvoie le y (monté d'un pixel d'art au survol). */
    private int carte(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean survol, boolean fondNuit, float a) {
        int P = m.P;
        if (survol) {
            y -= P;
            doux(ctx, HALO, x, y, w, h, m.kk, argb(OR, 0.62f * a));
        }
        doux(ctx, OMBRE, x, y, w, h, m.kk, argb(ENCRE, (survol ? 0.62f : 0.5f) * a));
        if (fondNuit) ctx.fillGradient(x + P, y + P, x + w - P, y + h - P, argb(survol ? NUIT_H_HOV : NUIT_H, 0.9f * a),
            argb(NUIT_B, 0.9f * a));
        return y;
    }

    private void cadreCarte(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean survol, float a) {
        LotGui l = LotGui.ouvrir(ctx);
        cadre(l, x, y, w, h, m.P, survol, a);
        l.fermer();
    }

    // ── Boutique : la vitrine ──
    private void boutique(GuiGraphicsExtractor ctx, float a) {
        if (a <= 0.003f) return;
        int P = m.P, x = m.gx, w = GRILLE_W * P, h = BOUTIQUE_H * P;
        double kk = m.kk, f = Math.max(1, kk);
        boolean ouverte = EscData.boutiqueOuverte(), ferme = !ouverte;
        boolean allume = hovered == H_BOUTIQUE && ouverte;
        int y = m.gy;
        if (allume) doux(ctx, HALO, x, y - P, w, h, kk, argb(OR, 0.55f * a));   // de jour, un second halo plus large
        y = carte(ctx, x, y, w, h, allume, false, a);
        int X0 = x + P, Y0 = y + P;
        int etat = allume ? 1 : ferme ? 2 : 0;
        blit(ctx, VITRINE, X0, Y0, VIT_W * P, VIT_H * P, 0, etat * VIT_H, VIT_W, VIT_H, VIT_W, VIT_H * 3, a);
        // chōchin (allumés au survol : cœur ambré + lueur)
        for (int c = 0; c < 2; c++) {
            int lx = X0 + (c == 0 ? VIT_OX0 - 26 : VIT_OX1 + 8) * P, ly = Y0 + 15 * P;
            if (allume) lueur(ctx, lx + 10 * P, ly + 16 * P, r(120 * kk), argb(0xFF9A50, 0.40f * a));
            blit(ctx, CHOCHIN, lx, ly, 20 * P, 32 * P, etat * 20, 0, 20, 32, 60, 32, a);
        }
        // titre peint sur l'enseigne, chevron dedans
        int zc = X0 + (ENS_X + ENS_W / 2) * P, yt = Y0 + (ENS_Y + ENS_H / 2) * P;
        double cap = 36 * kk, inter = 3 * kk;
        var stT = allume ? P_BLANC : ferme ? P_REPOS : P_CREME;
        double wt = lBoutique.largeur("BOUTIQUE", stT, cap, inter);
        int wch = ferme ? 0 : r(24 * kk);
        double xt = zc - Math.floor((wt + wch) / 2);
        lBoutique.ligne(ctx, "BOUTIQUE", stT, cap, f, inter, xt, yt + r(kk), a);
        if (!ferme) lChevron.ligne(ctx, "›", allume ? P_BLANC_D : P_OR_D, 32 * kk, f, 0,
            xt + Math.floor(wt) + wch + (allume ? r(4 * kk) : 0), yt - r(kk), allume ? a : 0.75f * a);
        // solde de RBCoins sous l'enseigne
        int yb = Y0 + (ENS_Y + ENS_H) * P + r(40 * kk);
        double capS = capInter(24, kk), capR = capInter(13, kk);
        double ws = lSolde.largeur(soldeTxt, B_OR, capS, 0), wl = lRb.largeur("RBCoins", I_DOUX, capR, 0);
        int coin = 12 * P;
        double tot = coin + r(12 * kk) + ws + r(8 * kk) + wl;
        int bx = (int) (zc - Math.floor(tot / 2));
        blit(ctx, RBCOIN, bx, yb - coin / 2, coin, coin, 0, 0, 12, 12, 12, 12, ferme ? 0.85f * a : a);
        int xs = bx + coin + r(12 * kk);
        lSolde.ligne(ctx, soldeTxt, B_OR, capS, f, 0, xs, yb, a);
        lRb.ligne(ctx, "RBCoins", I_DOUX, capR, f, 0, xs + Math.round(ws) + r(8 * kk), yb + r(2 * kk), a);
        if (ferme) {
            double capB = capInter(10, kk), interB = 1.2 * kk;
            int bw = (int) Math.round(lBientot.largeur("BIENTÔT", B_CREME_C, capB, interB)) + r(28 * kk), bh = r(24 * kk);
            int yp = yb + r(40 * kk);
            LotGui l = LotGui.ouvrir(ctx);
            pilule(l, zc - bw / 2, yp - bh / 2, bw, bh, 0x3A2E2C, 0.95f * a);
            l.fermer();
            lBientot.ligne(ctx, "BIENTÔT", B_CREME_C, capB, f, interB, zc, yp, a);
        }
        cadreCarte(ctx, x, y, w, h, allume, a);
    }

    // ── Récompenses : bande longue ──
    private void recompenses(GuiGraphicsExtractor ctx, float a, long now) {
        if (a <= 0.003f) { bordsY1 = -1; return; }
        int P = m.P, x = m.gx, y = m.ry, w = RECOMP_W * P, h = RANG_H * P;
        double kk = m.kk, f = Math.max(1, kk);
        carte(ctx, x, y, w, h, false, true, a);
        int pad = r(24 * kk);
        // en-tête : losange, titre, taux à droite, filet laqué
        int yc = y + r(30 * kk);
        DaKit.losange(ctx, x + pad + r(3 * kk), yc, Math.max(2, r(3 * kk)), argb(OR, a), argb(ENCRE, 0.4f * a));
        lRecTitre.ligne(ctx, "RÉCOMPENSES", P_OR, 17 * kk, f, 1.6 * kk, x + pad + r(16 * kk), yc, a);
        double capT = capInter(12, kk);
        double wtx = lTaux.largeur(tauxTxt, B_OR_D, capT, 0);
        lTaux.ligne(ctx, tauxTxt, B_OR_D, capT, f, 0, x + w - pad, yc, a);
        int cs = 12 * Math.max(1, P - 1);
        blit(ctx, RBCOIN, (int) (x + w - pad - wtx - r(8 * kk) - cs), yc - cs / 2, cs, cs, 0, 0, 12, 12, 12, 12, a);
        filetFondu(ctx, x + pad, y + r(54 * kk), w - 2 * pad, Math.max(1, r(kk)), OR_ETEINT, 0.45f * a);
        // colonnes : largeur proportionnelle au libellé, écarts égaux
        int n = CHARMES.length, c0 = y + r(60 * kk), c1 = y + h - r(54 * kk), pc = r(12 * kk);
        double capL = capInter(11.5, kk), somme = 0;
        double[] lws = LWS;
        for (int i = 0; i < n; i++) {
            lws[i] = Math.max(lCharmes[i].largeur(CHARMES[i].libelle(), B_HUD_C, capL, 0), r(60 * kk));
            somme += lws[i];
        }
        double libre = (w - 2 * pc) - somme, bx = x + pc;
        bords[0] = (int) bx;
        for (int i = 0; i < n; i++) { bx += lws[i] + libre / n; bords[i + 1] = (int) bx; }
        bordsY0 = c0;
        bordsY1 = c1;
        int s = Math.max(1, P - 1);
        LotGui l = LotGui.ouvrir(ctx);
        for (int i = 0; i < n; i++) {
            if (hovered == H_CHARME + i) {
                rect(l, bords[i] + 1, c0, bords[i + 1], c1, 0x2A1014, 0.85f * a);
                int bh = Math.max(1, r(3 * kk));
                rect(l, bords[i] + 1, c1 - bh, bords[i + 1], c1, ROUGE_HOV, a);
            }
            if (i > 0) rect(l, bords[i], c0 + r(10 * kk), bords[i] + 1, c1 - r(10 * kk), 0x3A2E2C, 0.7f * a);
        }
        l.fermer();
        for (int i = 0; i < n; i++) {
            Charme ch = CHARMES[i];
            boolean actif = EscData.charmeActif(ch.id()), hot = hovered == H_CHARME + i;
            int cx = (bords[i] + bords[i + 1]) / 2, yi = c0 + r(6 * kk);
            blit(ctx, OMAMORI, cx - 12 * s, yi, 24 * s, 40 * s, ch.omamori() * 24, 0, 24, 40, 120, 40, (actif ? 1f : 0.55f) * a);
            int yl = yi + 40 * s + r(16 * kk);
            lCharmes[i].ligne(ctx, ch.libelle(), actif || hot ? B_HUD_C : B_DOUX_C, capL, f, 0, cx, yl, a);
            interrupteur(ctx, i, cx, yl + r(22 * kk), actif, a);
        }
        // pied : prochain versement, jauge, minuteur
        int yp = y + h - r(28 * kk);
        double capP = capInter(11, kk);
        double wp = lProchain.largeur("Prochain versement", I_DOUX, capP, 0);
        lProchain.ligne(ctx, "Prochain versement", I_DOUX, capP, f, 0, x + pad, yp, a);
        int gx = (int) (x + pad + wp + r(14 * kk)), gw = x + w - pad - r(80 * kk) - gx, gh = Math.max(4, r(6 * kk));
        long iv = Math.max(60_000L, EscData.recompenses().intervalleMs());
        float prog = 1f - clamp01(EscData.resteAvantVersement(now) / (float) iv);
        int gp = Math.round(gw * prog);
        l = LotGui.ouvrir(ctx);
        rect(l, gx, yp - gh / 2, gx + gw, yp + gh - gh / 2, 0x2A2222, 0.95f * a);
        rect(l, gx, yp - gh / 2, gx + gp, yp + gh - gh / 2, OR, 0.95f * a);
        rect(l, gx, yp - gh / 2, gx + gp, yp - gh / 2 + Math.max(1, r(kk)), OR_CLAIR, 0.95f * a);
        l.fermer();
        lMinuteur.ligne(ctx, minuteurTxt, B_CREME_D, capInter(12, kk), f, 0, x + w - pad, yp, a);
        cadreCarte(ctx, x, y, w, h, false, a);
    }

    private static final double[] LWS = new double[CHARMES.length];

    /** Interrupteur Actif / À faire sous un charme. */
    private void interrupteur(GuiGraphicsExtractor ctx, int i, int cx, int cy, boolean actif, float a) {
        double kk = m.kk;
        String t = actif ? "Actif" : "À faire";
        var st = actif ? B_VERT : B_GRIS;
        double cap = capInter(10.5, kk);
        double tw = lEtats[i].largeur(t, st, cap, 0);
        int sw = r(24 * kk), sh = r(12 * kk), g = r(6 * kk);
        int x0 = (int) (cx - Math.floor((sw + g + tw) / 2));
        int rr = Math.max(2, r(3 * kk));
        LotGui l = LotGui.ouvrir(ctx);
        pilule(l, x0, cy - sh / 2, sw, sh, actif ? 0x3A2E22 : 0x2A2426, a);
        disque(l, x0 + (actif ? sw - rr - r(3 * kk) : rr + r(3 * kk)), cy, rr, actif ? VERT : 0x6A605C, a);
        l.fermer();
        lEtats[i].ligne(ctx, t, st, cap, Math.max(1, kk), 0, x0 + sw + g, cy, a);
    }

    /** Aide du charme survolé, en infobulle sous la bande (masquée sous kk 0,8 : plancher de lisibilité). */
    private void aide(GuiGraphicsExtractor ctx, float a) {
        if (hovered < H_CHARME || hovered >= H_CHARME + CHARMES.length || m.kk < 0.8 || a <= 0.003f) return;
        int i = hovered - H_CHARME;
        double kk = m.kk;
        int x = m.gx, y = m.ry, w = RECOMP_W * m.P, h = RANG_H * m.P;
        String t = CHARMES[i].aide();
        double cap = capInter(11, kk);
        int tw = (int) Math.round(lAide.largeur(t, I_HUD_C, cap, 0));
        int bw = tw + r(24 * kk), bh = r(28 * kk);
        int cx = (bords[i] + bords[i + 1]) / 2;
        int bx = Math.min(Math.max(cx - bw / 2, x), x + w - bw), by = y + h + r(8 * kk);
        doux(ctx, OMBRE, bx, by, bw, bh, kk, argb(ENCRE, 0.5f * a));
        LotGui l = LotGui.ouvrir(ctx);
        rect(l, bx, by, bx + bw, by + bh, OR_ETEINT, a);
        rect(l, bx + 1, by + 1, bx + bw - 1, by + bh - 1, 0x140E11, 0.97f * a);
        l.fermer();
        lAide.ligne(ctx, t, I_HUD_C, cap, Math.max(1, kk), 0, bx + bw / 2.0, by + bh / 2.0, a);
    }

    // ── petites cartes : Stream, Dev blog, Discord ──
    private void tuile(GuiGraphicsExtractor ctx, int i, float a) {
        if (a <= 0.003f) return;
        int P = m.P, x = m.tx, w = m.tw, h = m.th;
        double kk = m.kk, f = Math.max(1, kk);
        boolean survol = hovered == H_STREAM + i;
        int y = carte(ctx, x, m.tuileY(i), w, h, survol, true, a);
        // case du pictogramme (carré sombre à filet d'or)
        int cs = (h - 2 * P - 2 * r(8 * kk)) / P * P;
        int cx0 = x + P + r(8 * kk), cy0 = y + (h - cs) / 2;
        LotGui l = LotGui.ouvrir(ctx);
        rect(l, cx0, cy0, cx0 + cs, cy0 + cs, 0x0C080A, 0.85f * a);
        int bc = survol ? OR : OR_ETEINT;
        float ba = (survol ? 0.9f : 0.55f) * a;
        rect(l, cx0, cy0, cx0 + cs, cy0 + 1, bc, ba);
        rect(l, cx0, cy0 + cs - 1, cx0 + cs, cy0 + cs, bc, ba);
        rect(l, cx0, cy0 + 1, cx0 + 1, cy0 + cs - 1, bc, ba);
        rect(l, cx0 + cs - 1, cy0 + 1, cx0 + cs, cy0 + cs - 1, bc, ba);
        l.fermer();
        EscData.Snapshot s = EscData.get();
        boolean live = s != null && s.liveStreamCount() > 0;
        String titre, ligne;
        TexteCache.Style stTitre, stLigne;
        double capT = 15 * kk, interT = 1.4 * kk;
        int point = -1;
        switch (i) {
            case 0 -> {
                int p = 16 * P;
                blit(ctx, PICTOS, cx0 + (cs - p) / 2, cy0 + (cs - p) / 2, p, p, live ? 0 : 16, 0, 16, 16, 32, 16, a);
                titre = "STREAM";
                ligne = live ? "En direct" : "Hors ligne";
                stLigne = live ? I_CREME : I_GRIS;
                if (live) point = DIRECT;
                stTitre = survol ? P_BLANC : P_OR;
            }
            case 1 -> {
                int p = 14 * P;
                blit(ctx, PARCHEMIN, cx0 + (cs - p) / 2, cy0 + (cs - p) / 2, p, p, 0, 0, 14, 14, 14, 14, a);
                titre = "DEV BLOG";
                ligne = blogLigne;
                stLigne = I_DOUX;
                stTitre = survol ? P_BLANC : P_OR;
            }
            default -> {
                int p = 16 * P;
                blit(ctx, RESEAUX, cx0 + (cs - p) / 2, cy0 + (cs - p) / 2, p, p, 0, survol ? 16 : 0, 16, 16, 64, 32, a);
                titre = discordTitre;
                ligne = discordLigne;
                stLigne = I_DOUX;
                stTitre = survol ? P_BLANC : P_CREME;
                capT = 18 * kk;
                interT = 0.6 * kk;
            }
        }
        int lx = cx0 + cs + r(14 * kk), yc = y + h / 2;
        lTitres[i].ligne(ctx, titre, stTitre, capT, f, interT, lx, yc - r(9 * kk), a);
        int xl = lx;
        if (point >= 0) {
            int rr = Math.max(2, r(3 * kk));
            LotGui lp = LotGui.ouvrir(ctx);
            disque(lp, lx + rr, yc + r(13 * kk), rr, point, a);
            lp.fermer();
            xl = lx + 2 * rr + r(6 * kk);
        }
        lLignes[i].ligne(ctx, ligne, stLigne, capInter(11, kk), f, 0, xl, yc + r(13 * kk), a);
        lChevrons[i].ligne(ctx, "›", survol ? P_BLANC_D : P_OR_D, 22 * kk, f, 0,
            x + w - r(16 * kk) + (survol ? r(3 * kk) : 0), yc - r(kk), survol ? a : 0.55f * a);
        cadreCarte(ctx, x, y, w, h, survol, a);
    }

    // ═════════════════════════════════════════════════════════════ fermeture

    /**
     * Ferme l'écran pause au début de l'image suivante, hors extraction. Changer d'écran pendant le rendu faisait
     * rendre le monde à une image où il n'avait pas été extrait : crash Sodium « Global terrain uniforms have not been
     * updated » (15/10). {@code gui.setScreen} et non {@code setScreenAndShow} : ce dernier forcerait une image sans le
     * monde (flash).
     */
    private void demanderFermeture() {
        if (fermetureDemandee) return;
        fermetureDemandee = true;
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> { if (mc.gui.screen() == screen) mc.gui.setScreen(null); });
    }

    /** CONTINUER, Échap ou RETOUR : fermeture animée (180 ms). Vrai si l'Échap s'en charge. */
    public boolean continuer() {
        if (closingAt > 0) return true;
        RebornSounds.playReborn("esc.close", 1.0f, 0.55f);
        closeSoundPlayed = true;
        closingAt = System.currentTimeMillis();
        return true;
    }

    // ═════════════════════════════════════════════════════════════ clics

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || closingAt > 0) return false;
        var win = Minecraft.getInstance().getWindow();
        m.calculer(win.getWidth(), win.getHeight(), staff());
        double gs = Math.max(1, win.getGuiScale());
        int id = zone(mouseX * gs, mouseY * gs);
        return id >= 0 && agir(id);
    }

    /** Action d'une zone (clic, bancs). */
    boolean agir(int id) {
        Minecraft mc = Minecraft.getInstance();
        EscData.Snapshot snap = EscData.get();
        switch (id) {
            case E_CONTINUER, H_RETOUR -> continuer();
            case E_PERSONNAGE -> { select(); fr.reborn.hud.keybind.HudKeybinds.ouvrirPersonnage(mc); }
            case E_PARAMETRES -> { select(); mc.setScreenAndShow(new ConfigShellScreen(screen)); }
            case E_SIGNALER -> { select(); mc.setScreenAndShow(new ReportScreen(screen)); }
            case E_GARDE -> { select(); fr.reborn.hud.staff.StaffClient.send("open"); }
            case E_QUITTER -> { select(); mc.setScreenAndShow(new DisconnectConfirmScreen(screen)); }
            case H_BOUTIQUE -> { select(); mc.setScreenAndShow(new fr.reborn.hud.boutique.BoutiqueVideScreen(screen)); }
            case H_STREAM -> {
                if (snap == null || snap.streams().isEmpty()) return true;
                EscData.Stream choix = snap.streams().get(0);
                for (EscData.Stream s : snap.streams()) if (s.live()) { choix = s; break; }
                open(choix.url());
            }
            case H_BLOG -> {
                List<EscData.PatchNote> n = snap != null ? snap.patchNotes() : List.of();
                open(!n.isEmpty() && n.get(0).url() != null ? n.get(0).url() : RebornBranding.SITE_URL);
            }
            case H_DISCORD -> open(RebornBranding.discordUrl());
            default -> {
                if (id >= H_CHARME && id < H_CHARME + CHARMES.length) RebornSounds.playReborn("esc.charm", 1.0f, 0.45f);
                else return false;
            }
        }
        return true;
    }

    private static void select() { RebornSounds.playReborn("esc.select", 1.0f, 0.55f); }

    private static void open(String url) {
        if (url == null || url.isBlank()) return;
        select();
        try { Util.getPlatform().openUri(URI.create(url)); } catch (Exception ignored) {}
    }

    // ═════════════════════════════════════════════════════════════ bancs

    /**
     * Centre d'une zone en coordonnées GUI pour la fenêtre courante (banc {@code EscDebug}) : entrées par identifiant
     * ({@link #E_CONTINUER}…), cartes ({@link #H_BOUTIQUE}…), charmes ({@link #H_CHARME} + i, colonnes égales),
     * pied ({@link #H_RETOUR}). {@code null} si la zone n'est pas affichée.
     */
    public static double[] centreGui(int id) {
        Minecraft mc = Minecraft.getInstance();
        var win = mc.getWindow();
        Mise g = new Mise();
        g.calculer(win.getWidth(), win.getHeight(), staff());
        double gs = Math.max(1, win.getGuiScale());
        double px, py;
        int P = g.P;
        if (id >= E_CONTINUER && id <= E_QUITTER) {
            int n = -1;
            for (int i = 0; i < g.nEntrees; i++) if (g.liste[i] == id) n = i;
            if (n < 0) return null;
            px = g.X + 60;
            py = g.yEntree(n);
        } else if (id == H_BOUTIQUE) {
            px = g.gx + GRILLE_W * P / 2.0;
            py = g.gy + BOUTIQUE_H * P / 2.0;
        } else if (id >= H_STREAM && id <= H_DISCORD) {
            px = g.tx + g.tw / 2.0;
            py = g.tuileY(id - H_STREAM) + g.th / 2.0;
        } else if (id >= H_CHARME && id < H_CHARME + CHARMES.length) {
            int w = RECOMP_W * P;
            px = g.gx + (id - H_CHARME + 0.5) * w / CHARMES.length;
            py = g.ry + RANG_H * P / 2.0;
        } else if (id == H_RETOUR) {
            px = g.X + 20;
            py = g.pied;
        } else return null;
        return new double[]{px / gs, py / gs};
    }

    /** L'Échap à l'écran, ou null. */
    static EscTokonoma courant() {
        Screen s = Minecraft.getInstance().gui.screen();
        return s instanceof PauseScreen && courant != null && courant.screen == s ? courant : null;
    }

    private static String fmt(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder(s.length() + 4);
        if (v < 0) b.append('-');
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}
