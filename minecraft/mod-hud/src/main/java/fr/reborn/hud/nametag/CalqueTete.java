package fr.reborn.hud.nametag;

import fr.reborn.hud.anchor.TeteAncre;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * <b>Calque « pile au-dessus de la tête »</b> (31/10) : ce qui flotte au-dessus d'un joueur s'empile, de la tête
 * vers le haut, sans jamais se chevaucher :
 * <pre>
 *   sommet de tête → 3 px → nametag (si visible) → 2 px → bulle voix / frappe (si active)
 *                  → 2 px → /me (empilés) → 2 px → nametag staff → 2 px → invite « Tendre »
 * </pre>
 * Chaque élément <b>déclare</b> à chaque image la hauteur qu'il occupe ({@link #occuper}) et lit le bas de sa
 * couche ({@link #base}) : la somme des couches occupées en dessous, suivie avec un lissage exponentiel de
 * {@link #LISSAGE_MS} — quand le nametag apparaît, la bulle et le /me montent en douceur, sans saut. Pendant la
 * montée, l'élément du dessous n'est opaque qu'à hauteur de la place libérée ({@link #place}) : rien ne se recouvre,
 * même le temps d'une transition. Une couche absente cède sa place à l'image suivante (seule, la bulle prend la
 * place du nametag).
 *
 * <p>Les éléments sont dessinés dans l'ordre d'enregistrement du HUD (bulles, nametags, /me, staff…) : une
 * déclaration compte pendant l'image où elle est faite et la suivante, donc l'ordre n'importe pas (un élément dessiné
 * avant le nametag lit la déclaration de l'image précédente : une image de retard, invisible sous le lissage).
 *
 * <p>Entre entités : chaque pile ancrée à l'écran est retenue pour l'image ({@link #auDessusDesAutres} : le /me d'un
 * joueur du fond, dont la tête est visible à côté de celle de devant, monte au-dessus du nom et de la bulle de celui-ci ;
 * {@link #recouvre} : un nom en maintien cède la place à celui qu'on regarde). {@link #teteVisible} filtre la visibilité
 * de la tête à deux images près ; {@link #teteDegagee} y ajoute les corps plus proches (nom, bulle et /me : une tête
 * cachée derrière un joueur s'efface comme derrière un mur, elle ne se pose pas sur lui).
 *
 * <p>Ancre commune : {@link TeteAncre#ecran} au sommet de la tête rendue vue par la caméra (chapeau compris, tête
 * basculée ou vue en plongée comprise), calée au pixel physique.
 * Décalages en px GUI vers le haut. Thread de rendu, aucune allocation par image (une fiche par entité, créée à la
 * première rencontre, oubliée après 5 s). L'horloge est celle des nametags ({@link Nametags#horloge}) : un banc qui
 * l'impose rend tous les fondus et glissements déterministes.
 */
public final class CalqueTete {

    private CalqueTete() {}

    /** Couches, de la tête vers le haut. */
    public static final int NAMETAG = 0, BULLE = 1, ME = 2, STAFF = 3, INVITE = 4;
    private static final int COUCHES = 5;

    /** Sommet de tête → bas du premier élément (px GUI). */
    public static final float ECART_TETE = 3f;
    /** Entre deux éléments empilés (px GUI). */
    public static final float ECART = 2f;
    /** Constante de temps du lissage des décalages (ms) : 63 % du chemin en 90 ms, 95 % en 270 ms. */
    public static final float LISSAGE_MS = 90f;
    /** Une couche qui n'a pas été placée depuis 300 ms repart de sa cible (un /me qui arrive ne glisse pas depuis la tête). */
    private static final long REPRISE_MS = 300L;
    private static final long OUBLI_MS = 5000L;

    private static final class Pile {
        int id;
        final float[] hauteur = new float[COUCHES];
        /** Demi-largeur déclarée de chaque couche (px GUI), pour écarter le /me d'un autre auteur. */
        final float[] demi = new float[COUCHES];
        /** Image de la dernière déclaration de chaque couche. */
        final long[] declaree = new long[COUCHES];
        /** Bas lissé de chaque couche (px GUI au-dessus du sommet de tête), image et heure du dernier calcul. */
        final float[] base = new float[COUCHES];
        final long[] baseImage = new long[COUCHES];
        final long[] baseMs = new long[COUCHES];
        /** Ancre de l'image (sommet de tête à l'écran, px GUI ; z = distance caméra). */
        float ancreX, ancreY, ancreZ;
        long ancreImage = -10L;
        /** Images d'affilée sans tête visible (une tête jamais vue part cachée). */
        int invisibles = 3;
        long visImage = -10L;
        long vuMs;

        Pile() {
            Arrays.fill(declaree, -10L);
            Arrays.fill(baseImage, -10L);
        }
    }

    private static final Int2ObjectOpenHashMap<Pile> PILES = new Int2ObjectOpenHashMap<>();
    /** Piles ancrées à l'écran pendant l'image courante (vidée à chaque image, jamais réallouée). */
    private static final ArrayList<Pile> VUES = new ArrayList<>(64);
    /** Compteur d'images : avance à chaque fin d'extraction du monde, juste avant le HUD de la même image. */
    private static long image;

    /** Enregistré une fois par {@code RebornHudClient} : compteur d'images, oubli à la déconnexion, banc. */
    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(ctx -> {
            image++;
            VUES.clear();
            if ((image & 255) == 0) purger();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            vider();
            Nametags.vider();
            fr.reborn.hud.voice.SpeechBubbles.vider();
        });
        NametagsDebug.init();   // REBORN_NAMETAG_DEBUG (dev)
    }

    /** Heure des fondus et du lissage (ms) : {@link Nametags#horloge}, injectable par un banc. */
    public static long maintenant() { return Nametags.horloge.getAsLong(); }

    /**
     * Sommet de la tête rendue de {@code e} à l'écran (px GUI calés au pixel physique, {@code z} = distance caméra →
     * ancre). {@code false} : derrière la caméra ou hors écran.
     */
    public static boolean ancre(Entity e, Vector3f outGui) {
        if (!TeteAncre.ecran(e, 0f, outGui)) return false;
        Pile p = pile(e.getId());
        if (p.ancreImage != image) {
            p.ancreImage = image;
            if (VUES.size() < 128) VUES.add(p);
        }
        p.ancreX = outGui.x;
        p.ancreY = outGui.y;
        p.ancreZ = outGui.z;
        return true;
    }

    /** {@link #ancre} en pixels physiques de la fenêtre (éléments dessinés en « unités menu »). */
    public static boolean ancrePx(Entity e, Vector3f outPx) { return TeteAncre.ecranPx(e, 0f, outPx); }

    /**
     * {@link TeteAncre#visible} (rendue récemment, aucun bloc opaque entre la caméra et elle), à deux images près : une
     * image isolée sans capture fraîche (à-coup de plus de 300 ms, capture d'écran) ne fait rien clignoter ; une tête
     * jamais vue reste cachée jusqu'à sa première image visible. Le filtre ne relie que des images <b>consécutives</b> :
     * une tête évaluée après une pause (hors de portée puis revenue, cachée entre-temps) repart cachée tant que
     * {@link TeteAncre#visible} ne l'a pas confirmée — sans quoi une bulle s'allumait deux images à travers le mur.
     * Évalué une fois par image et par entité.
     */
    public static boolean teteVisible(Entity e) {
        Pile p = pile(e.getId());
        if (p.visImage != image) {
            if (p.visImage < image - 1) p.invisibles = 3;
            p.visImage = image;
            if (TeteAncre.visible(e)) p.invisibles = 0;
            else if (p.invisibles < 1000) p.invisibles++;
        }
        return p.invisibles < 3;
    }

    /**
     * {@link #teteVisible} <b>et</b> pas cachée par un autre corps plus proche de la caméra
     * ({@link TeteAncre#cacheeParUnCorps}) : le nom, la bulle et le /me d'un joueur caché derrière un autre s'effacent
     * comme derrière un mur, au lieu de se poser sur le visage de celui de devant (y compris un nom en maintien quand
     * quelqu'un passe devant, ou une tête cachée derrière son propre avatar en vue épaule).
     */
    public static boolean teteDegagee(Entity e) {
        return teteVisible(e) && !TeteAncre.cacheeParUnCorps(e);
    }

    /** Une couche au-dessus de {@code couche} est-elle occupée pour cette entité (image courante ou précédente) ? */
    public static boolean occupeeAuDessus(Entity e, int couche) {
        Pile p = PILES.get(e.getId());
        if (p == null) return false;
        for (int j = couche + 1; j < COUCHES; j++) if (occupee(p, j)) return true;
        return false;
    }

    /**
     * La couche {@code ca} de {@code a} et la couche {@code cb} de {@code b} se recouvrent-elles à l'écran (ancres et
     * déclarations de cette image ou de la précédente, {@link #ECART} compris) ?
     */
    public static boolean recouvre(Entity a, int ca, Entity b, int cb) {
        Pile p = PILES.get(a.getId()), q = PILES.get(b.getId());
        if (p == null || q == null || p == q || p.ancreImage < image - 1 || q.ancreImage < image - 1
            || !occupee(p, ca) || !occupee(q, cb)) return false;
        if (Math.abs(p.ancreX - q.ancreX) >= p.demi[ca] + q.demi[cb] + ECART) return false;
        float pb = p.ancreY - p.base[ca], qb = q.ancreY - q.base[cb];
        return pb - p.hauteur[ca] < qb + ECART && qb - q.hauteur[cb] < pb + ECART;
    }

    /** Déclare la hauteur visible (px GUI) de la couche pour cette image ; ne pas appeler = couche absente. */
    public static void occuper(Entity e, int couche, float hauteur) { occuper(e, couche, hauteur, 0f); }

    /** {@link #occuper} avec la demi-largeur du dessin (px GUI) : un /me voisin s'en écarte. */
    public static void occuper(Entity e, int couche, float hauteur, float demiLargeur) {
        Pile p = pile(e.getId());
        p.hauteur[couche] = Math.max(0f, hauteur);
        p.demi[couche] = Math.max(0f, demiLargeur);
        p.declaree[couche] = image;
        p.vuMs = maintenant();
    }

    /**
     * Bas d'un bloc posé dans la couche {@link #ME} d'une entité (px GUI : centre {@code x}, bas {@code yBas}, hauteur
     * {@code h}, demi-largeur {@code dl}, distance caméra {@code z}), remonté au-dessus du nom et de la bulle des
     * <b>autres</b> entités plus proches de la caméra qui le recouvriraient à l'écran (le /me d'un joueur du fond ne
     * se pose plus sur le nom de celui de devant).
     */
    public static float auDessusDesAutres(Entity e, float x, float yBas, float h, float dl, float z) {
        int id = e.getId();
        for (int passe = 0; passe < 8; passe++) {
            boolean bouge = false;
            for (int i = 0, n = VUES.size(); i < n; i++) {
                Pile p = VUES.get(i);
                if (p.id == id || p.ancreZ >= z) continue;
                float haut = ECART_TETE, demi = 0f;
                boolean occ = false;
                for (int j = 0; j < ME; j++) {
                    if (!occupee(p, j)) continue;
                    haut += p.hauteur[j] + ECART;
                    demi = Math.max(demi, p.demi[j]);
                    occ = true;
                }
                if (!occ || Math.abs(x - p.ancreX) >= dl + demi + ECART) continue;
                if (yBas - h >= p.ancreY || yBas <= p.ancreY - haut) continue;
                yBas = p.ancreY - haut;
                bouge = true;
            }
            if (!bouge) break;
        }
        return yBas;
    }

    /**
     * Bas de la couche (px GUI au-dessus du sommet de tête) pour cette image : {@link #ECART_TETE} + chaque couche
     * occupée en dessous ({@link #ECART} compris), lissé. Calculé une fois par image et par couche.
     */
    public static float base(Entity e, int couche) {
        Pile p = pile(e.getId());
        if (p.baseImage[couche] == image) return p.base[couche];
        float cible = ECART_TETE;
        for (int j = 0; j < couche; j++) if (occupee(p, j)) cible += p.hauteur[j] + ECART;
        long now = maintenant();
        long dt = now - p.baseMs[couche];
        if (p.baseImage[couche] < 0 || dt < 0 || dt > REPRISE_MS) {
            p.base[couche] = cible;
        } else if (dt > 0) {
            float b = p.base[couche] + (cible - p.base[couche]) * (1f - (float) Math.exp(-dt / LISSAGE_MS));
            p.base[couche] = Math.abs(cible - b) < 0.02f ? cible : b;
        }
        p.baseImage[couche] = image;
        p.baseMs[couche] = now;
        p.vuMs = now;
        return p.base[couche];
    }

    /**
     * Part de sa hauteur dont dispose la couche (0 à 1) : tant que l'élément du dessus n'a pas fini de monter, celui
     * du dessous reste estompé d'autant (aucun chevauchement pendant la transition). 1 sans couche au-dessus.
     */
    public static float place(Entity e, int couche) {
        Pile p = PILES.get(e.getId());
        if (p == null || !occupee(p, couche)) return 1f;
        float h = p.hauteur[couche];
        for (int j = couche + 1; j < COUCHES; j++) {
            if (!occupee(p, j)) continue;
            float libre = base(e, j) - base(e, couche) - ECART;
            return Math.max(0f, Math.min(1f, libre / h));
        }
        return 1f;
    }

    /** Décalage GUI calé au pixel physique (un nametag translaté de cette valeur ne scintille pas). */
    public static float calePx(float gui) {
        float gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        return Math.round(gui * gs) / gs;
    }

    /** Oublie tout (déconnexion). */
    public static void vider() {
        PILES.clear();
        VUES.clear();
    }

    // ── interne ─────────────────────────────────────────────────

    private static boolean occupee(Pile p, int j) { return p.declaree[j] >= image - 1 && p.hauteur[j] > 0f; }

    private static Pile pile(int id) {
        Pile p = PILES.get(id);
        if (p == null) {
            p = new Pile();
            p.id = id;
            p.vuMs = maintenant();
            PILES.put(id, p);
        }
        return p;
    }

    private static void purger() {
        long limite = maintenant() - OUBLI_MS;
        ObjectIterator<Int2ObjectMap.Entry<Pile>> it = Int2ObjectMaps.fastIterator(PILES);
        while (it.hasNext()) if (it.next().getValue().vuMs < limite) it.remove();
    }
}
