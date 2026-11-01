package fr.reborn.hud.nameplate;

import fr.reborn.hud.anchor.TeteAncre;
import fr.reborn.hud.douane.DouaneClient;
import fr.reborn.hud.ko.CorpsClient;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.tablist.TabEntry;
import fr.reborn.hud.menu.tablist.TablistData;
import fr.reborn.hud.ui.Da;
import fr.reborn.hud.ui.DaKit;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Plaques de nom RP au-dessus des têtes — remplacent le pseudo Minecraft (masqué
 * côté serveur par une équipe {@code NAME_TAG_VISIBILITY=NEVER} et côté client par
 * {@code EntityNameTagHideMixin}). Rendu <b>2D HUD</b> (projection de la tête à l'écran).
 *
 * <p>Quatre styles au choix (Options → Interface → Noms au-dessus des têtes,
 * {@link RebornPrefs#nameplateStyle}) :
 * <ol start="0">
 *   <li><b>Détouré</b> : le nom seul, contour sombre, petit carré à la couleur du clan.</li>
 *   <li><b>Filet doré</b> : nom, filet d'or à losange à la couleur du clan.</li>
 *   <li><b>Village</b> : emblème du village + nom sur une bande sombre estompée, filet du clan.</li>
 *   <li><b>Ruban</b> : nom sur un ruban vermillon à pointes.</li>
 * </ol>
 * Tout tient sur <b>une ligne</b> : le nom RP envoyé par le serveur est déjà « Prénom Clan ».
 * Joueur non présenté = « ??? » (« Inconnu » pour le style Village), en gris.
 *
 * <p><b>Au regard</b> (31/10) : le nom ne s'affiche plus en permanence. Il apparaît quand on regarde le joueur — rayon
 * lancé depuis la <b>caméra</b> (vue épaule par défaut : position + direction de la caméra, prolongé de la distance
 * caméra – œil), contre la boîte de collision gonflée unie à une boîte autour du centre de la tête (un joueur allongé
 * se vise à la tête), hystérésis entrée 0,30 / sortie 0,45, à 6 blocs au plus de l'œil, jamais à travers un bloc
 * opaque ({@link TeteAncre#visible}) — puis reste tant qu'on le regarde, se maintient 2,5 s et s'efface (fondu entrant
 * 160 ms, sortant 450 ms ; au-delà de 9 blocs pendant le maintien, il s'efface tout de suite ; accroupi = discret,
 * fondu de 200 ms). Si plusieurs joueurs sont dans l'axe, seul le plus proche s'allume ; un regard de moins de 90 ms
 * (viseur qui balaie un groupe) n'allume personne ; un nom en maintien qui recouvrirait celui qu'on regarde s'efface en
 * 110 ms, et le nom regardé attend qu'il soit parti (jamais deux noms l'un sur l'autre).
 *
 * <p><b>Ancre</b> : sommet de la tête réellement rendue ({@link TeteAncre} : émotes, ramper, à terre, allongé), dans
 * la pile commune de {@link CalqueTete} (3 px au-dessus du chapeau, la bulle et le /me au-dessus). L'ancre est calée
 * une fois au pixel physique puis la plaque est dessinée en coordonnées locales entières : le contour du style
 * « Détouré » ne scintille plus quand la plaque bouge. Les quatre dessins sont inchangés.
 */
public final class Nameplates {

    private Nameplates() {}

    // ── regard (réglages) ───────────────────────────────────────
    /** Le regard s'accroche à un joueur dont le centre de tête est à 6 blocs au plus de l'œil. */
    private static final double PORTEE_REGARD = 6.0;
    /** Au-delà, une plaque en maintien s'efface sans attendre. */
    private static final double PORTEE_MAINTIEN = 9.0;
    /** Marge autour de la boîte de collision : accroche à 0,30, décroche à 0,45 (hystérésis). */
    private static final double REGARD_ENTREE = 0.30, REGARD_SORTIE = 0.45;
    /** Demi-côté de la boîte autour du centre de la tête, × échelle (+ l'écart d'hystérésis en sortie). */
    private static final double BOITE_TETE = 0.35;

    // ── déroulé (réglages) ──────────────────────────────────────
    private static final long FONDU_ENTREE_MS = 160L, MAINTIEN_MS = 2500L, FONDU_SORTIE_MS = 450L,
        FONDU_DISCRET_MS = 200L;
    /**
     * Un nom en maintien qui recouvrirait celui qu'on regarde lui cède la place en 110 ms (avec le fondu de sortie
     * normal, il restait à 80 % au bout de 200 ms) ; tant qu'il s'efface, le nom regardé est estompé d'autant : jamais
     * deux noms l'un sur l'autre.
     */
    private static final long FONDU_CEDE_MS = 110L;
    /**
     * Le regard doit tenir 90 ms avant que le nom ne s'allume : un viseur qui balaie un groupe n'allume pas chaque
     * plaque pour 2,5 s (0 = immédiat).
     */
    private static final long DELAI_REGARD_MS = 90L;
    /** Atténuation avec la distance : pleine jusqu'à 3 blocs, puis −1/12 par bloc, 60 % au moins. */
    private static final float ATTENUATION_DEBUT = 3f, ATTENUATION_PENTE = 12f, ATTENUATION_MIN = 0.6f;
    /** Capitales d'ArcadePix (px de police) : hauteur visible des styles à contour. */
    private static final float CAP = 7f;

    private static final int OUTLINE = 0xFF0E080A, UNKNOWN = 0xFFA8A0A0, NEUTRAL_CLAN = 0xFFC8B4A0;
    private static final Set<String> VILLAGES = Set.of("konoha", "suna", "kiri", "kumo", "iwa", "ame");
    /** Emblèmes des villages (identifiants composés une fois). */
    private static final Map<String, Identifier> EMBLEMES = new HashMap<>();
    static {
        for (String v : VILLAGES) EMBLEMES.put(v, Identifier.fromNamespaceAndPath("reborn", "textures/gui/esc/village_" + v + ".png"));
    }
    /** Petit mot d'état KO accolé au nom (Inter, minuscules), composé une fois ; largeurs mesurées au premier usage. */
    private static final Component[] MOTS = {DaKit.inter("douleur"), DaKit.inter("à terre"), DaKit.inter("inconscient"),
        DaKit.inter("mort")};
    private static final int[] LARGEUR_MOT = {-1, -1, -1, -1};
    private static final int MOT_DOULEUR = 0, MOT_A_TERRE = 1, MOT_INCONSCIENT = 2, MOT_MORT = 3;

    /** Horloge des fondus (ms), commune à la pile ({@link CalqueTete#maintenant}) : un banc l'impose. */
    public static LongSupplier horloge = System::currentTimeMillis;

    /** Banc d'essai (KoDebug) : plaques aussi sur les mannequins. */
    public static boolean debugAvatars;
    /** Banc d'essai : le mannequin est vu comme un inconnu (masque porté, GAMEPLAN §14). */
    public static boolean debugInconnu;
    /** Banc d'essai : un nom par mannequin (prioritaire sur le tab ; sans entrée, « Ren Hyūga »). */
    public static final Map<UUID, TablistData.RpName> DEBUG_NOMS = new ConcurrentHashMap<>();
    private static final TablistData.RpName DEBUG_REN = new TablistData.RpName("Ren Hyūga",
        TabEntry.Relation.CONNAISSANCE, 0x5577DD, "Hyūga", "Konoha");
    /** Teinte du nom pendant le dessin d'une plaque (état KO ; 0 = crème). */
    private static int teinte;

    // ── état par joueur ─────────────────────────────────────────
    private static final byte CACHEE = 0, ENTREE = 1, MAINTIEN = 2, SORTIE = 3;

    /** Déroulé d'une plaque : entrée (et visible tant qu'on regarde), maintien, sortie. */
    private static final class Etat {
        boolean regarde;
        /** En sortie parce qu'il cède la place au nom regardé ({@link #FONDU_CEDE_MS}). */
        boolean cede;
        byte phase = CACHEE;
        long vuRegardMs, transDebut, sortieMs, vuMs, regardDepuis = -1L;
        /** Progression au début de la transition en cours (une entrée reprise à mi-sortie ne saute pas). */
        float transDepart;
    }

    private static final Int2ObjectOpenHashMap<Etat> ETATS = new Int2ObjectOpenHashMap<>();

    // Image courante (une seule image à la fois, thread de rendu) : rien n'est alloué par image.
    private static final Vector3d C = new Vector3d();
    private static final Vector3f A = new Vector3f();
    private static double camX, camY, camZ, dirX, dirY, dirZ, longueur, oeilX, oeilY, oeilZ, tCible;
    private static float pt;
    private static long now;
    private static Entity moi, vue, cibleEntite;
    private static int cible, images;
    /** Alpha du nom qui cède la place au nom regardé : relevé à cette image, appliqué au nom regardé à la suivante. */
    private static float cedeImage, cedePrec;

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options == null || mc.gui.hud.isHidden()) return;
        // Sans data serveur (hors serveur Shinobi / solo), on ne remplace rien — sauf dans la douane (plaques du canal
        // reborn:douane : « Examinateur », ou le pseudo à la place d'« Inconnu »).
        if (!TablistData.hasData() && !debugAvatars && !DouaneClient.plaquesActives()) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) return;

        now = horloge.getAsLong();
        pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        cedePrec = cedeImage;
        cedeImage = 0f;
        LocalPlayer j = mc.player;
        moi = j;
        vue = cam.entity();
        oeilX = Mth.lerp(pt, j.xo, j.getX());
        oeilY = Mth.lerp(pt, j.yo, j.getY()) + j.getEyeHeight();
        oeilZ = Mth.lerp(pt, j.zo, j.getZ());
        // Rayon du VISEUR : depuis la caméra (vue épaule : le viseur vise depuis là), prolongé de la distance
        // caméra – œil pour atteindre 6 blocs devant le joueur.
        Vec3 cp = cam.position();
        Vector3fc fw = cam.forwardVector();
        camX = cp.x;
        camY = cp.y;
        camZ = cp.z;
        dirX = fw.x();
        dirY = fw.y();
        dirZ = fw.z();
        double ex = oeilX - camX, ey = oeilY - camY, ez = oeilZ - camZ;
        longueur = PORTEE_REGARD + Math.sqrt(ex * ex + ey * ey + ez * ez);

        // 1) qui regarde-t-on ? (le plus proche dans l'axe)
        cible = -1;
        cibleEntite = null;
        tCible = Double.MAX_VALUE;
        if (debugAvatars) {
            for (Entity e : mc.level.entitiesForRendering()) viser(e);
        } else {
            List<AbstractClientPlayer> l = mc.level.players();
            for (int i = 0, n = l.size(); i < n; i++) viser(l.get(i));
        }
        // 2) déroulés, fondus, dessin
        Font font = mc.font;
        if (debugAvatars) {
            for (Entity e : mc.level.entitiesForRendering()) suivre(ctx, font, e);
        } else {
            List<AbstractClientPlayer> l = mc.level.players();
            for (int i = 0, n = l.size(); i < n; i++) suivre(ctx, font, l.get(i));
        }
        cibleEntite = moi = vue = null;   // aucune entité retenue d'une image à l'autre
        if (++images >= 256) {
            images = 0;
            purger();
        }
    }

    /** Oublie les déroulés (déconnexion). */
    public static void vider() {
        ETATS.clear();
        cibleEntite = moi = vue = null;
    }

    /** Banc : id de l'entité visée à la dernière image (−1 : personne). */
    static int debugCible() { return cible; }

    // ── regard ──────────────────────────────────────────────────

    /** Candidat au regard : touché par le rayon, à portée, tête dégagée (ni mur ni corps devant), ni accroupi ni invisible. */
    private static void viser(Entity e) {
        if (!(e instanceof Avatar) || e == moi || e == vue) return;
        if (e.isCrouching() || e.isInvisible() || e.isSpectator()) return;
        double dx = e.getX() - oeilX, dy = e.getY() - oeilY, dz = e.getZ() - oeilZ;
        double pre = PORTEE_REGARD + 2.5;   // pré-filtre : la tête d'un corps allongé est à 1,6 bloc de ses pieds
        if (dx * dx + dy * dy + dz * dz > pre * pre) return;
        TeteAncre.centre(e, C);
        if (distOeil2() > PORTEE_REGARD * PORTEE_REGARD || !CalqueTete.teteDegagee(e)) return;
        Etat s = ETATS.get(e.getId());
        double m = s != null && s.regarde ? REGARD_SORTIE : REGARD_ENTREE;
        // boîte de collision recalée sur la position interpolée (celle du rendu), gonflée…
        AABB b = e.getBoundingBox();
        double ix = Mth.lerp(pt, e.xOld, e.getX()) - e.getX();
        double iy = Mth.lerp(pt, e.yOld, e.getY()) - e.getY();
        double iz = Mth.lerp(pt, e.zOld, e.getZ()) - e.getZ();
        double t = rayonBoite(b.minX + ix - m, b.minY + iy - m, b.minZ + iz - m,
            b.maxX + ix + m, b.maxY + iy + m, b.maxZ + iz + m);
        // … unie à une boîte autour de la tête rendue
        double r = BOITE_TETE * TeteAncre.echelle(e) + (m - REGARD_ENTREE);
        double t2 = rayonBoite(C.x - r, C.y - r, C.z - r, C.x + r, C.y + r, C.z + r);
        if (t2 >= 0 && (t < 0 || t2 < t)) t = t2;
        if (t >= 0 && t < tCible) {
            tCible = t;
            cible = e.getId();
            cibleEntite = e;
        }
    }

    /** Entrée du rayon de regard dans la boîte (blocs le long du rayon), −1 s'il la manque (méthode des dalles). */
    private static double rayonBoite(double x0, double y0, double z0, double x1, double y1, double z1) {
        double tmin = 0.0, tmax = longueur;
        if (Math.abs(dirX) < 1e-9) {
            if (camX < x0 || camX > x1) return -1;
        } else {
            double a = (x0 - camX) / dirX, c = (x1 - camX) / dirX;
            tmin = Math.max(tmin, Math.min(a, c));
            tmax = Math.min(tmax, Math.max(a, c));
            if (tmin > tmax) return -1;
        }
        if (Math.abs(dirY) < 1e-9) {
            if (camY < y0 || camY > y1) return -1;
        } else {
            double a = (y0 - camY) / dirY, c = (y1 - camY) / dirY;
            tmin = Math.max(tmin, Math.min(a, c));
            tmax = Math.min(tmax, Math.max(a, c));
            if (tmin > tmax) return -1;
        }
        if (Math.abs(dirZ) < 1e-9) {
            if (camZ < z0 || camZ > z1) return -1;
        } else {
            double a = (z0 - camZ) / dirZ, c = (z1 - camZ) / dirZ;
            tmin = Math.max(tmin, Math.min(a, c));
            tmax = Math.min(tmax, Math.max(a, c));
            if (tmin > tmax) return -1;
        }
        return tmin;
    }

    private static double distOeil2() {
        double dx = C.x - oeilX, dy = C.y - oeilY, dz = C.z - oeilZ;
        return dx * dx + dy * dy + dz * dz;
    }

    // ── déroulé ─────────────────────────────────────────────────

    private static void suivre(GuiGraphicsExtractor ctx, Font font, Entity e) {
        if (!(e instanceof Avatar) || e == moi || e == vue) return;
        int id = e.getId();
        boolean regarde = id == cible;
        Etat s = ETATS.get(id);
        if (s == null) {
            if (!regarde) return;
            s = new Etat();
            ETATS.put(id, s);
        }
        s.regarde = regarde;
        s.vuMs = now;
        if (!regarde) s.regardDepuis = -1L;
        else if (s.regardDepuis < 0L || now < s.regardDepuis) s.regardDepuis = now;
        if (s.phase == CACHEE && !regarde) return;
        TeteAncre.centre(e, C);
        double d = Math.sqrt(distOeil2());
        // tête derrière un mur ou un corps plus proche : le nom (même en maintien) ne se peint pas sur celui de devant
        boolean discret = e.isCrouching() || e.isInvisible() || e.isSpectator() || !CalqueTete.teteDegagee(e);
        avancer(s, regarde && now - s.regardDepuis >= DELAI_REGARD_MS, discret, d > PORTEE_MAINTIEN);
        // Un autre nom s'allume là où celui-ci se maintient (deux têtes proches à l'écran) : il lui cède la place, vite.
        if (s.phase == MAINTIEN && cibleEntite != null && CalqueTete.recouvre(e, CalqueTete.PLAQUE, cibleEntite, CalqueTete.PLAQUE)) {
            sortir(s, FONDU_CEDE_MS);
            s.cede = true;
        }
        float a = alpha(s);
        if (a <= 0f && (s.phase == SORTIE || s.phase == CACHEE)) {
            s.phase = CACHEE;
            s.cede = false;
            return;
        }
        float att = Math.max(ATTENUATION_MIN, Math.min(1f, 1f - ((float) d - ATTENUATION_DEBUT) / ATTENUATION_PENTE));
        if (s.cede && s.phase == SORTIE && cibleEntite != null
            && CalqueTete.recouvre(e, CalqueTete.PLAQUE, cibleEntite, CalqueTete.PLAQUE)) {
            cedeImage = Math.max(cedeImage, a * att);   // il s'efface par-dessus le nom regardé
        } else if (regarde && cedePrec > 0f) {
            a *= 1f - cedePrec;                           // le nom regardé attend qu'il soit parti
        }
        dessiner(ctx, font, e, a * att);
    }

    /** Fait avancer le déroulé : le regard (seulement sur une cible ni discrète ni trop loin) relance l'entrée. */
    private static void avancer(Etat s, boolean regarde, boolean discret, boolean loin) {
        if (regarde) {
            s.vuRegardMs = now;
            if (s.phase == CACHEE || s.phase == SORTIE) entrer(s);
            else s.phase = ENTREE;   // maintien interrompu : la courbe d'entrée continue
            return;
        }
        if (s.phase == ENTREE) s.phase = MAINTIEN;
        if (s.phase == MAINTIEN) {
            if (discret) sortir(s, FONDU_DISCRET_MS);
            else if (loin || now - s.vuRegardMs >= MAINTIEN_MS) sortir(s, FONDU_SORTIE_MS);
        } else if (s.phase == SORTIE && discret && s.sortieMs > FONDU_DISCRET_MS) {
            sortir(s, FONDU_DISCRET_MS);   // il s'accroupit (ou passe derrière un mur) pendant le fondu : on presse
        }
    }

    /** Entrée depuis l'alpha courant : progression p telle que 1 − (1 − p)³ = alpha. */
    private static void entrer(Etat s) {
        float a = alpha(s);
        s.transDepart = 1f - (float) Math.cbrt(1f - a);
        s.transDebut = now;
        s.phase = ENTREE;
        s.cede = false;
    }

    /** Sortie depuis l'alpha courant : progression q telle que 1 − q² = alpha. */
    private static void sortir(Etat s, long ms) {
        float a = alpha(s);
        s.transDepart = (float) Math.sqrt(1f - a);
        s.transDebut = now;
        s.sortieMs = ms;
        s.phase = SORTIE;
    }

    /** Entrée en ease-out cubique (vive puis douce), sortie en ease-in (douce puis vive). */
    private static float alpha(Etat s) {
        float dt = Math.max(0L, now - s.transDebut);
        switch (s.phase) {
            case ENTREE, MAINTIEN -> {
                float q = 1f - Math.min(1f, s.transDepart + dt / FONDU_ENTREE_MS);
                return 1f - q * q * q;
            }
            case SORTIE -> {
                float q = Math.min(1f, s.transDepart + dt / s.sortieMs);
                return 1f - q * q;
            }
            default -> {
                return 0f;
            }
        }
    }

    private static void purger() {
        long limite = now - 10_000L;
        ObjectIterator<Int2ObjectMap.Entry<Etat>> it = Int2ObjectMaps.fastIterator(ETATS);
        while (it.hasNext()) if (it.next().getValue().vuMs < limite) it.remove();
    }

    // ── dessin ──────────────────────────────────────────────────

    private static void dessiner(GuiGraphicsExtractor ctx, Font font, Entity e, float alpha) {
        if (!CalqueTete.ancre(e, A)) return;
        UUID u = e.getUUID();
        TablistData.RpName rp = DEBUG_NOMS.get(u);
        if (rp == null) rp = TablistData.rpNameFor(u);
        if (rp == null && debugAvatars) rp = DEBUG_REN;
        boolean known = rp != null && rp.relation() != TabEntry.Relation.INCONNU && !debugInconnu
            && rp.name() != null && !rp.name().isBlank();
        String name = known ? rp.name() : null;
        int clanCol = known && rp.clanColor() != 0 ? (0xFF000000 | rp.clanColor()) : NEUTRAL_CLAN;
        String village = known ? villageKey(rp.village()) : null;
        // Douane (PLUGINS_REFERENCE §4 decies.4, plaques) : « Examinateur » (carré d'or), sinon le pseudo du profil.
        String douane = DouaneClient.nomPlaque(e);
        if (douane != null) {
            name = douane;
            clanCol = DouaneClient.examinateur(u) ? Da.GOLD : NEUTRAL_CLAN;
            village = null;
        }

        // État KO (14/10) intégré au nom : nom teinté et petit mot accolé à droite (douleur, à terre,
        // inconscient, mort) — plus de ligne séparée au-dessus ; plus de kanji depuis le 18/10 (DA §3).
        CorpsClient.Body etat = CorpsClient.body(u);
        int mot = -1;
        int motCol = 0xFFE26474;
        if (etat != null) {
            switch (etat.phase()) {
                case CorpsClient.PHASE_DOULEUR -> { mot = MOT_DOULEUR; teinte = 0xFFEBC2BC; }
                case 1 -> { mot = MOT_A_TERRE; teinte = 0xFFEBC2BC; }
                case 2 -> { mot = MOT_INCONSCIENT; teinte = 0xFFB4ACA2; motCol = 0xFFB4ACA2; }
                case 3 -> { mot = MOT_MORT; teinte = 0xFF8E867C; motCol = 0xFF8E867C; }
                default -> { }
            }
        }
        int style = Math.floorMod(RebornPrefs.INSTANCE.nameplateStyle, 4);
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float sc = Da.title(), px = 1f / gs;
        String s0 = name != null ? name : (style == 2 ? "Inconnu" : "???");
        float demi = largeur(font, s0, sc) / 2f + (style == 1 ? 7 : style == 2 ? 11 : style == 3 ? 12 : 9)
            + (mot >= 0 ? largeurMot(font, mot) * 0.75f + 10 : 0f);
        CalqueTete.occuper(e, CalqueTete.PLAQUE, hautStyle(style, sc, px), demi);
        float a = alpha * CalqueTete.place(e, CalqueTete.PLAQUE);
        float base = CalqueTete.base(e, CalqueTete.PLAQUE);
        if (a <= 0.004f) {
            teinte = 0;
            return;
        }
        // Pose sur l'ancre (déjà calée au pixel physique) et le bas de la couche ; dessin en coordonnées locales.
        float y = basStyle(style, sc, px);
        ctx.pose().pushMatrix();
        ctx.pose().translate(A.x, A.y - CalqueTete.calePx(base));
        try {
            switch (style) {
                case 1 -> drawFilet(ctx, font, 0f, y, name, clanCol, a);
                case 2 -> drawVillage(ctx, font, 0f, y, name, clanCol, village, a);
                case 3 -> drawRuban(ctx, font, 0f, y, name, a);
                default -> drawDetoure(ctx, font, 0f, y, name, clanCol, a);
            }
            if (mot >= 0) {
                // petit mot accolé à droite du nom (Inter de Reborn, minuscules), même hauteur
                float gx = largeur(font, s0, sc) / 2f + (style == 0 ? 10 : style == 2 ? 13 : 6);
                float gy = y - (style == 0 ? 8 * sc + 2 : style == 2 ? 13 : 14) + 1;   // mot Inter : 2 GUI plus bas que l'ancien glyphe
                ctx.pose().pushMatrix();
                ctx.pose().translate(CalqueTete.calePx(gx), CalqueTete.calePx(gy));
                ctx.pose().scale(0.75f, 0.75f);
                ctx.text(font, MOTS[mot], 0, 0, fade(motCol, a), true);
                ctx.pose().popMatrix();
            }
        } finally {
            teinte = 0;
            ctx.pose().popMatrix();
        }
    }

    /** Du point de référence {@code y} d'un style (son « bas ») au bas visible de son dessin, en px GUI. */
    private static float basStyle(int style, float sc, float px) {
        return switch (style) {
            case 1 -> 9f - 8f * sc;                                  // losange : ly + 3, ly = round(y − 12 + 8·sc)
            case 2 -> 2f;                                            // filet du clan sous la bande
            case 3 -> 4f;                                            // bas du ruban
            default -> 8f * sc + 2f - Math.max(CAP * sc + 2f * px, 6f);   // contour ou carré du clan
        };
    }

    /** Hauteur visible du dessin d'un style (px GUI), pour la pile. */
    private static float hautStyle(int style, float sc, float px) {
        return switch (style) {
            case 1 -> 5f + 8f * sc + 2f * px;
            case 2 -> 13f;
            case 3 -> 11f;
            default -> Math.max(CAP * sc + 2f * px, 6f) + 2f * px;
        };
    }

    private static int creme() { return teinte != 0 ? teinte : Da.CREAM; }

    /**
     * Plaque au style choisi par le joueur, centrée en {@code (x, y)} (bas de la plaque) — pour les écrans qui
     * montrent un perso hors du monde (sélection du personnage).
     */
    public static void drawPlate(GuiGraphicsExtractor g, Font f, float x, float y, String name, int clanCol,
                                 String village, float alpha) {
        int col = 0xFF000000 | clanCol;
        switch (Math.floorMod(RebornPrefs.INSTANCE.nameplateStyle, 4)) {
            case 1 -> drawFilet(g, f, x, y, name, col, alpha);
            case 2 -> drawVillage(g, f, x, y, name, col, villageKey(village), alpha);
            case 3 -> drawRuban(g, f, x, y, name, alpha);
            default -> drawDetoure(g, f, x, y, name, col, alpha);
        }
    }

    /** Style « Village » quel que soit le réglage (emblème + nom). */
    public static void drawVillagePlate(GuiGraphicsExtractor g, Font f, float x, float y, String name, int clanCol,
                                        String village, float alpha) {
        drawVillage(g, f, x, y, name, 0xFF000000 | clanCol, villageKey(village), alpha);
    }

    // ── styles ───────────────────────────────────────────────────

    /** Détouré : nom seul avec contour, carré du clan à droite. */
    private static void drawDetoure(GuiGraphicsExtractor g, Font f, float x, float y, String name, int clanCol, float a) {
        float sc = Da.title();
        String s = name != null ? name : "???";
        float w = largeur(f, s, sc);
        float top = y - 8 * sc - 2;
        outlined(g, f, s, x, top, sc, name != null ? creme() : UNKNOWN, OUTLINE, a);
        if (name != null) {
            int bx = Math.round(x + w / 2f + 3), by = Math.round(top + 1);
            g.fill(bx - 1, by - 1, bx + 5, by + 5, fade(OUTLINE, a));
            g.fill(bx, by, bx + 4, by + 4, fade(clanCol, a));
        }
    }

    /** Filet doré : nom, filet d'or à losange à la couleur du clan. */
    private static void drawFilet(GuiGraphicsExtractor g, Font f, float x, float y, String name, int clanCol, float a) {
        float sc = Da.title();
        String s = name != null ? name : "???";
        float top = y - 14;
        outlined(g, f, s, x, top, sc, name != null ? creme() : UNKNOWN, 0xFF000000, a);
        int ly = Math.round(top + 8 * sc + 2);
        int half = Math.max(16, Math.round(largeur(f, s, sc) / 2f) + 6);
        int cx = Math.round(x);
        g.fill(cx - half, ly, cx - 3, ly + 1, fade(Da.GOLD, a));
        g.fill(cx + 4, ly, cx + half + 1, ly + 1, fade(Da.GOLD, a));
        int dc = fade(name != null ? clanCol : UNKNOWN, a);
        g.fill(cx, ly - 2, cx + 1, ly + 3, dc);
        g.fill(cx - 1, ly - 1, cx + 2, ly + 2, dc);
    }

    /** Village : emblème du village + nom sur une bande estompée, filet de la couleur du clan. */
    private static void drawVillage(GuiGraphicsExtractor g, Font f, float x, float y, String name, int clanCol,
                                    String village, float a) {
        float sc = Da.title();
        String s = name != null ? name : "Inconnu";
        int icon = 8;
        boolean hasIcon = name != null;
        int tw = largeur(f, s, sc);
        int w = tw + (hasIcon ? icon + 14 : 12), h = 12;
        int x0 = Math.round(x - w / 2f), y0 = Math.round(y - h - 3);
        // bande sombre qui s'estompe sur les bords
        int fadeW = Math.max(1, Math.min(8, w / 3));
        for (int i = 0; i < w; i++) {
            float k = Math.min(1f, Math.min(i, w - 1 - i) / (float) fadeW);
            g.fill(x0 + i, y0, x0 + i + 1, y0 + h, fade(((int) (0xB4 * k) << 24) | 0x0A0608, a));
        }
        int tx = x0 + 6;
        if (hasIcon) {
            Identifier id = village != null ? EMBLEMES.get(village) : null;
            if (id != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, id, tx, y0 + 2, 0f, 0f, icon, icon, 48, 48, 48, 48, fade(Da.CREAM, a));
            } else {
                // village inconnu côté client : petit sceau à la couleur du clan
                g.fill(tx, y0 + 2, tx + icon, y0 + 2 + icon, fade(clanCol, a));
                g.fill(tx + 3, y0 + 4, tx + 5, y0 + icon, fade(Da.CREAM, a));
                g.fill(tx + 2, y0 + 5, tx + icon - 2, y0 + 6, fade(Da.CREAM, a));
            }
            tx += icon + 4;
        }
        texte(g, f, s, tx, y0 + (h - 8 * sc) / 2f + .5f, sc, fade(name != null ? creme() : UNKNOWN, a), 0);
        g.fill(x0 + 2, y0 + h, x0 + w - 2, y0 + h + 1, fade(name != null ? clanCol : 0xFF786E6E, a));
    }

    /** Ruban : nom sur un ruban vermillon à pointes. */
    private static void drawRuban(GuiGraphicsExtractor g, Font f, float x, float y, String name, float a) {
        float sc = Da.title();
        String s = name != null ? name : "???";
        int tw = largeur(f, s, sc);
        int w = tw + 14, h = 11;
        int x0 = Math.round(x - w / 2f), y0 = Math.round(y - 15);
        int body = fade(name != null ? 0xFF96202A : 0xFF463C3E, a);
        int light = fade(name != null ? 0xFFD65A4A : 0xFF645A5C, a);
        g.fill(x0, y0, x0 + w, y0 + h, body);
        g.fill(x0, y0 + 1, x0 + w, y0 + 2, light);
        // queues d'aronde : rangées plus courtes au milieu (encoche)
        for (int i = 0; i < h; i++) {
            int notch = Math.round(3 * (1f - Math.abs(i - (h - 1) / 2f) / ((h - 1) / 2f)));
            g.fill(x0 - 5 + notch, y0 + i, x0, y0 + i + 1, body);
            g.fill(x0 + w, y0 + i, x0 + w + 5 - notch, y0 + i + 1, body);
        }
        texte(g, f, s, x, y0 + (h - 8 * sc) / 2f + .5f, sc, fade(name != null ? creme() : UNKNOWN, a), 1);
    }

    // ── outils ───────────────────────────────────────────────────

    /** « Konohagakure », « Konoha no Sato »… → clé d'emblème (konoha, suna, kiri, kumo, iwa) ou null. */
    static String villageKey(String v) {
        if (v == null || v.isBlank()) return null;
        String k = v.trim().toLowerCase(Locale.ROOT);
        for (String id : VILLAGES) if (k.startsWith(id)) return id;
        return null;
    }

    /** Texte ArcadePix composé une fois par nom ({@link Da#arc} normalise et compile une regex à chaque appel) et sa largeur. */
    private record Arc(Component c, int w) {}

    private static final Map<String, Arc> ARC = new HashMap<>();

    private static Arc arc(Font f, String s) {
        Arc t = ARC.get(s);
        if (t == null) {
            if (ARC.size() > 256) ARC.clear();
            Component c = Da.arc(s);
            t = new Arc(c, f.width(c));
            ARC.put(s, t);
        }
        return t;
    }

    /** {@link Da#width} sans recomposer le texte. */
    private static int largeur(Font f, String s, float sc) { return Math.round(arc(f, s).w() * sc); }

    private static int largeurMot(Font f, int i) {
        if (LARGEUR_MOT[i] < 0) LARGEUR_MOT[i] = f.width(MOTS[i]);
        return LARGEUR_MOT[i];
    }

    /** {@link Da#text} sans recomposer le texte (même calage aux unités GUI). */
    private static void texte(GuiGraphicsExtractor g, Font f, String s, float x, float y, float sc, int col, int align) {
        Arc t = arc(f, s);
        float w = t.w() * sc;
        float x0 = align == 0 ? x : align == 1 ? x - w / 2f : x - w;
        posee(g, f, t.c(), Math.round(x0), Math.round(y), sc, col);
    }

    /**
     * Texte ArcadePix centré avec contour (lisible sur tout fond). Origine calée une seule fois au pixel physique,
     * contour à ±2 px physiques exacts : sur une plaque déjà posée sur une ancre calée, il reste symétrique et ne
     * change plus avec la position sous-pixel (il scintillait dès l'échelle 3).
     */
    private static void outlined(GuiGraphicsExtractor g, Font f, String s, float cx, float y, float sc,
                                 int col, int outline, float a) {
        Arc t = arc(f, s);
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float px = 1f / gs;
        float x0 = Math.round((cx - t.w() * sc / 2f) * gs) * px, y0 = Math.round(y * gs) * px;
        int oc = fade(outline, a);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) posee(g, f, t.c(), x0 + dx * 2 * px, y0 + dy * 2 * px, sc, oc);
        posee(g, f, t.c(), x0, y0, sc, fade(col, a));
    }

    private static void posee(GuiGraphicsExtractor g, Font f, Component c, float x, float y, float sc, int col) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        g.text(f, c, 0, 0, col, false);
        g.pose().popMatrix();
    }

    /** Atténue l'alpha (plaque plus discrète de loin, fondus). */
    private static int fade(int argb, float f) {
        int al = (int) (((argb >>> 24) & 0xFF) * f);
        return (al << 24) | (argb & 0xFFFFFF);
    }
}
