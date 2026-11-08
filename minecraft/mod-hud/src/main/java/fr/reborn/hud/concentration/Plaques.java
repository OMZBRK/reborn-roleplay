package fr.reborn.hud.concentration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.recit.Recit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * <b>Plaques lues en concentration</b> (lot 4, étape B, côté mod ; décisions du 07/11). Le serveur envoie les
 * <b>ancres</b> des plaques à 32 blocs <b>sans le texte</b> ; le mod choisit celle que le joueur <b>regarde</b> et demande
 * son texte, que le serveur ne rend qu'après avoir revérifié concentration, portée (+ 1,5), cône et ligne de vue.
 *
 * <ul>
 *   <li><b>Affichage automatique</b> : être en face suffit, sans viser au curseur. Une plaque est candidate si elle est à
 *       sa portée (distance des yeux à sa boîte), en vue (rayon des yeux vers sa boîte sans bloc plein avant) et dans un
 *       <b>cône de {@value #CONE_DEG}°</b> autour de la direction de la caméra ; s'il y en a plusieurs, la plus centrée
 *       gagne, puis la plus proche. Relu à chaque tick, rien par image.</li>
 *   <li><b>Grâce</b> : quand plus rien n'est en face, le texte reste {@value #GRACE_MS} ms (anti-clignotement au bord du
 *       cône), puis s'efface ({@code Gabarit.STELE} : 350 ms). Une autre plaque en face : coupe de 150 ms.</li>
 *   <li><b>Cache</b> du texte par (id, rev) : revenir devant une plaque déjà lue la montre sans attendre ; une révision
 *       neuve (le staff l'a modifiée) la redemande. Refus du serveur : pas de nouvelle demande pendant 1,5 s.</li>
 *   <li><b>Rendu</b> : {@link Recit#lire} en {@code Gabarit.STELE}, <b>ancré devant l'objet</b> et tourné vers le joueur
 *       ({@code RecitHud} demande le point à {@link #pointTexte}) : centre de la face tournée vers la caméra pour un bloc
 *       ou une boîte, centre de la boîte pour une entité, avancé de {@value #DEVANT} bloc vers la caméra ; derrière le
 *       texte, le halo ({@link HaloPlaque}). Une narration du staff passe devant, la plaque revient ensuite
 *       ({@link Recit}).</li>
 *   <li><b>Pages</b> : un texte long se lit page par page, clic gauche hors d'une cible interactive <b>ou sur l'objet
 *       même</b> de la plaque (cadre, meuble à entité : la page tourne, pas le menu) ({@link #clicPage}) ; repère
 *       « 1 / 2 › » sous le bloc. Sous une narration du staff, le clic ne tourne rien.</li>
 *   <li><b>Révision</b> : une plaque corrigée par le staff pendant qu'on la lit est remplacée sur place.</li>
 *   <li><b>Face</b> : une plaque de bloc posée sur une face (sélecteur) ne se lit que de ce côté, comme au serveur.</li>
 *   <li>Cécité : plaques illisibles. Sortie de la concentration : texte effacé en 350 ms, ancres oubliées, {@code off}
 *       au serveur ; un {@code etat on:false} du serveur ferme le mode.</li>
 * </ul>
 * Coût en concentration : une boucle sur les ancres proches et au plus deux rayons par ancre candidate, par tick ;
 * hors concentration, rien.
 */
public final class Plaques {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/plaques");

    /** Cône de vue (degrés autour de la direction de la caméra). */
    public static final double CONE_DEG = 25.0;
    /** Grâce quand plus rien n'est en face (ms). */
    public static final long GRACE_MS = 150;
    /** Le texte flotte devant la face visée, vers la caméra (blocs). */
    public static final double DEVANT = 0.12;
    /** Demande de texte : une par plaque toutes les 600 ms au plus ; après un refus, 1,5 s. */
    private static final long DEMANDE_MS = 600, REFUS_MS = 1500;
    private static final int CACHE_MAX = 64;
    /** Deux plaques à égalité d'angle (à 3° près) : la plus proche gagne. */
    public static final double EGALITE_DEG = 3.0;
    /** Hystérésis : la plaque choisie reste tant qu'une autre n'est pas plus centrée d'au moins 5°. */
    public static final double HYSTERESIS_DEG = 5.0;

    /**
     * Une ancre reçue : boîte (minX, minY, minZ, maxX, maxY, maxZ), portée de lecture, bloc ou entité, et pour un bloc la
     * face visée au sélecteur (null = tous côtés) : la plaque ne se lit que de ce côté, comme au serveur.
     */
    static final class Ancre {
        final String id;
        final int rev, portee;
        final boolean bloc;
        final double[] b;
        final net.minecraft.core.Direction face;

        Ancre(String id, int rev, int portee, boolean bloc, double[] b, net.minecraft.core.Direction face) {
            this.id = id;
            this.rev = rev;
            this.portee = portee;
            this.bloc = bloc;
            this.b = b;
            this.face = face;
        }
    }

    /** Texte d'une plaque en cache : révision, segments, couleur du halo ({@link HaloPlaque#code}). */
    private record Texte(int rev, List<Recit.Segment> segs, int halo) { }

    /** Horloge de la grâce (le banc la fige). */
    public static volatile LongSupplier horloge = Util::getMillis;

    private static final List<Ancre> ANCRES = new ArrayList<>();
    private static final Map<String, Texte> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Texte> e) { return size() > CACHE_MAX; }
    };
    private static final Map<String, Long> DEMANDES = new HashMap<>(), REFUS = new HashMap<>();
    /** Le serveur a accepté la concentration ({@code etat on:true}). */
    private static boolean accepte;
    /** Plaque en face (après la grâce), plaque montrée par le récit, et l'instant où plus rien n'était en face. */
    private static String choisie, lue;
    /** Révision du texte montré pour {@link #lue} (une révision neuve le remplace sur place). */
    private static int lueRev;
    private static long perdueA = -1;
    /** Boîtes des deux dernières plaques montrées (la sortante garde sa place pendant son fondu). */
    private static final double[] BOITE_A = new double[6], BOITE_B = new double[6];
    private static String boiteAId, boiteBId;
    /** Couleur du halo de chaque place ({@link HaloPlaque#code}) : la sortante garde la sienne pendant son fondu. */
    private static int haloA = HaloPlaque.DEFAUT, haloB = HaloPlaque.DEFAUT;
    private static boolean boiteABloc, boiteBBloc;
    private static net.minecraft.core.Direction boiteAFace, boiteBFace;
    /** Dernier refus d'entrée du serveur (banc). */
    private static String refusServeur;

    private Plaques() {}

    /** Appelé par {@link ModeConcentration#init}. */
    static void init() {
        ConcentrationCanal.init();
        ClientTickEvents.END_CLIENT_TICK.register(Plaques::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(Plaques::oublier));
        fr.reborn.hud.recit.RecitHud.ancres = Plaques::pointTexte;
        fr.reborn.hud.recit.RecitHud.halos = Plaques::haloDe;
    }

    /* ================================================================ API du mode */

    /** Entrée en concentration : {@code on} au serveur s'il parle le canal. */
    static void entree() {
        accepte = false;
        ANCRES.clear();
        choisie = null;
        perdueA = -1;
        if (ConcentrationCanal.disponible()) ConcentrationCanal.envoyer("on", null);
    }

    /** Sortie (tous les chemins) : {@code off}, texte effacé en 350 ms, ancres oubliées (le cache reste). */
    static void sortie() {
        if (accepte || ConcentrationCanal.disponible()) ConcentrationCanal.envoyer("off", null);
        accepte = false;
        ANCRES.clear();
        choisie = null;
        perdueA = -1;
        cacherLue();
    }

    /** Déconnexion : tout oublié, cache compris. */
    static void oublier() {
        accepte = false;
        ANCRES.clear();
        CACHE.clear();
        DEMANDES.clear();
        REFUS.clear();
        choisie = lue = null;
        lueRev = 0;
        boiteAId = boiteBId = null;
        haloA = haloB = HaloPlaque.DEFAUT;
        perdueA = -1;
    }

    /**
     * Clic gauche en concentration (hors d'une cible interactive, ou sur l'objet de la plaque lue) : page suivante de la
     * plaque lue (true si fait). Rien sous une narration du staff (la plaque y est masquée).
     */
    public static boolean clicPage() {
        if (lue == null || !Recit.lireSuite("plaque:" + lue)) return false;
        fr.reborn.hud.menu.RebornSounds.uiClick();
        return true;
    }

    /**
     * Le point {@code (x, y, z)} (impact du rayon du curseur) tombe sur l'objet de la plaque lue : un clic gauche y tourne
     * la page au lieu d'ouvrir le menu de l'entité (cadre, meuble Nexo à entité {@code Interaction}). Aucune allocation.
     */
    public static boolean surPlaqueLue(double x, double y, double z) {
        if (lue == null) return false;
        double[] b = lue.equals(boiteAId) ? BOITE_A : lue.equals(boiteBId) ? BOITE_B : null;
        return b != null && dans(b, x, y, z, 0.1);
    }

    /** Plaque dont le texte est demandé ou montré (banc), null sinon. */
    public static String choisie() { return choisie; }

    /** Plaque montrée par le récit (banc), null sinon. */
    public static String lue() { return lue; }

    /** Ancres reçues (banc). */
    public static int nbAncres() { return ANCRES.size(); }

    public static boolean accepte() { return accepte; }

    public static String refusServeur() { return refusServeur; }

    /* ================================================================ messages du serveur */

    /** Un message du canal (fil client ; version déjà vérifiée par {@link ConcentrationCanal}). */
    static void recevoir(JsonObject o) {
        String t = str(o, "t");
        switch (t) {
            case "etat" -> {
                boolean on = o.has("on") && bool(o, "on");
                if (on) {
                    accepte = ModeConcentration.actif();
                    refusServeur = null;
                    return;
                }
                accepte = false;
                ANCRES.clear();
                refusServeur = str(o, "raison");
                // refus ou fin imposée (mort, à terre, cinématique, écran noir…) : le mode se ferme
                if (ModeConcentration.actif()) ModeConcentration.refusServeur(refusServeur);
            }
            case "ancres" -> {
                if (!ModeConcentration.actif()) return;
                accepte = true;
                ANCRES.clear();
                JsonArray l = o.has("l") && o.get("l").isJsonArray() ? o.getAsJsonArray("l") : new JsonArray();
                for (JsonElement e : l) {
                    try { ANCRES.add(lireAncre(e.getAsJsonObject())); }
                    catch (RuntimeException ignored) { }
                }
            }
            case "texte" -> {
                String id = str(o, "id");
                if (id.isEmpty() || !o.has("segs")) return;
                List<Recit.Segment> segs = new ArrayList<>();
                try {
                    for (JsonElement e : o.getAsJsonArray("segs")) {
                        JsonArray s = e.getAsJsonArray();
                        segs.add(new Recit.Segment(s.get(0).getAsString(), s.size() > 1 ? s.get(1).getAsString() : ""));
                    }
                } catch (RuntimeException e) { return; }
                // halo absent = ivoire-or (serveur d'avant la planche c6) ; « aucun » ; sinon la lueur RRGGBB
                CACHE.put(id, new Texte(num(o, "rev"), List.copyOf(segs), HaloPlaque.code(str(o, "halo"))));
                REFUS.remove(id);
                if (id.equals(choisie)) montrer(id);
            }
            case "texte_refus" -> {
                String id = str(o, "id");
                REFUS.put(id, horloge.getAsLong() + REFUS_MS);
                if (id.equals(lue)) cacherLue();
            }
            case "traces" -> Traces.recevoir(o);   // étape C : les pas des autres joueurs
            default -> { }
        }
    }

    private static Ancre lireAncre(JsonObject a) {
        String id = str(a, "id");
        int rev = num(a, "rev"), p = Math.max(2, Math.min(16, num(a, "p")));
        net.minecraft.core.Direction face = str(a, "f").isEmpty() ? null : net.minecraft.core.Direction.byName(str(a, "f"));
        if ("bloc".equals(str(a, "k"))) {
            int x = num(a, "x"), y = num(a, "y"), z = num(a, "z");
            int dx = 1, dy = 1, dz = 1;
            if (a.has("b") && a.get("b").isJsonArray()) {
                JsonArray b = a.getAsJsonArray("b");
                dx = Math.max(1, b.get(0).getAsInt());
                dy = Math.max(1, b.get(1).getAsInt());
                dz = Math.max(1, b.get(2).getAsInt());
            }
            return new Ancre(id, rev, p, true, new double[]{x, y, z, x + dx, y + dy, z + dz}, face);
        }
        // entité : boîte de lecture du serveur (centre x / z, bas, largeurs x et z, hauteur)
        double x = dbl(a, "x"), y = dbl(a, "y"), z = dbl(a, "z"), w = Math.max(0.2, dbl(a, "w")), h = Math.max(0.2, dbl(a, "h"));
        double d = a.has("d") ? Math.max(0.2, dbl(a, "d")) : w;
        return new Ancre(id, rev, p, false, new double[]{x - w / 2, y, z - d / 2, x + w / 2, y + h, z + d / 2}, null);
    }

    /* ================================================================ tick : la plaque en face */

    private static void tick(Minecraft mc) {
        if (!ModeConcentration.actif()) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        long now = horloge.getAsLong();
        Ancre best = (accepte || ConcentrationCanal.debugServeur != null) && !fr.reborn.hud.yeux.CeciteHud.active()
            ? enFace(mc, p) : null;
        if (best != null) {
            choisie = best.id;
            perdueA = -1;
            garderBoite(best);
        } else if (choisie != null) {
            if (perdueA < 0) perdueA = now;
            if (now - perdueA >= GRACE_MS) choisie = null;
        }
        if (choisie == null) {
            cacherLue();
            return;
        }
        Ancre a = ancre(choisie);
        Texte t = CACHE.get(choisie);
        if (t != null && a != null && t.rev() == a.rev) {
            montrer(choisie);
            return;
        }
        // texte inconnu ou révision neuve : la plaque montrée (une autre) s'efface, le texte est demandé
        if (lue != null && !lue.equals(choisie)) cacherLue();
        demander(choisie, now);
    }

    private static void demander(String id, long now) {
        Long r = REFUS.get(id);
        if (r != null && now < r) return;
        Long d = DEMANDES.get(id);
        if (d != null && now - d < DEMANDE_MS) return;
        DEMANDES.put(id, now);
        ConcentrationCanal.envoyer("lire", id);
    }

    /**
     * Montre le texte en cache de {@code id} ; la plaque déjà montrée n'est relue que si sa révision a changé (le staff
     * l'a corrigée pendant la lecture : le nouveau texte remplace l'ancien sur place, sans détourner le regard).
     */
    private static void montrer(String id) {
        Texte t = CACHE.get(id);
        if (t == null) return;
        boolean meme = id.equals(lue);
        if (meme && t.rev() == lueRev) return;
        Ancre a = ancre(id);
        if (a != null) garderBoite(a);
        if (id.equals(boiteAId)) haloA = t.halo();
        else if (id.equals(boiteBId)) haloB = t.halo();
        if (Recit.lire("plaque:" + id, t.segs(), meme)) {
            lue = id;
            lueRev = t.rev();
        }
    }

    private static void cacherLue() {
        if (lue == null) return;
        Recit.cacher("plaque:" + lue);
        lue = null;
    }

    private static Ancre ancre(String id) {
        for (Ancre a : ANCRES) if (a.id.equals(id)) return a;
        return null;
    }

    /** Garde la boîte de la plaque (deux places : la sortante finit son fondu à la sienne). */
    private static void garderBoite(Ancre a) {
        if (a.id.equals(boiteAId)) { System.arraycopy(a.b, 0, BOITE_A, 0, 6); boiteABloc = a.bloc; boiteAFace = a.face; return; }
        if (a.id.equals(boiteBId)) { System.arraycopy(a.b, 0, BOITE_B, 0, 6); boiteBBloc = a.bloc; boiteBFace = a.face; return; }
        // la plus ancienne place (B) est reprise ; A devient B
        System.arraycopy(BOITE_A, 0, BOITE_B, 0, 6);
        boiteBId = boiteAId;
        haloB = haloA;
        Texte t = CACHE.get(a.id);
        haloA = t != null ? t.halo() : HaloPlaque.DEFAUT;
        boiteBBloc = boiteABloc;
        boiteBFace = boiteAFace;
        System.arraycopy(a.b, 0, BOITE_A, 0, 6);
        boiteAId = a.id;
        boiteABloc = a.bloc;
        boiteAFace = a.face;
    }

    /**
     * La plaque que le joueur a en face : portée, cône de 25°, ligne de vue ; la plus centrée, puis la plus proche si
     * deux sont à {@value #EGALITE_DEG}° près ; la plaque déjà choisie garde la main tant qu'une autre n'est pas plus
     * centrée d'au moins {@value #HYSTERESIS_DEG}° (pas de bascule au milieu de deux stèles voisines).
     */
    private static Ancre enFace(Minecraft mc, LocalPlayer p) {
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || ANCRES.isEmpty()) return null;
        Vec3 c = cam.position();
        Vector3fc f = cam.forwardVector();
        Vec3 eye = p.getEyePosition();
        Ancre best = null, actuelle = null;
        double bestAngle = Double.MAX_VALUE, bestDist = Double.MAX_VALUE, angleActuelle = Double.MAX_VALUE;
        for (Ancre a : ANCRES) {
            double dist = distanceBoite(eye.x, eye.y, eye.z, a.b);
            if (dist > a.portee) continue;
            // face visée au sélecteur : seulement de ce côté (comme le serveur)
            if (!faceVisible(a, eye)) continue;
            double ang = angleBoite(c, f, a.b);
            if (ang > CONE_DEG) continue;
            boolean courante = a.id.equals(choisie);
            boolean mieux = best == null || ang < bestAngle - EGALITE_DEG
                || Math.abs(ang - bestAngle) <= EGALITE_DEG && dist < bestDist;
            if (!mieux && !courante) continue;
            if (!enVue(mc, p, eye, a)) continue;
            if (courante) { actuelle = a; angleActuelle = ang; }
            if (!mieux) continue;
            best = a;
            bestAngle = ang;
            bestDist = dist;
        }
        if (actuelle != null && best != actuelle && bestAngle > angleActuelle - HYSTERESIS_DEG) return actuelle;
        return best;
    }

    /** L'œil est du côté extérieur de la face visée de l'ancre (sans face : tous côtés). Même règle que le serveur. */
    static boolean faceVisible(Ancre a, Vec3 eye) {
        if (a.face == null) return true;
        double[] b = a.b;
        return switch (a.face) {
            case NORTH -> eye.z < b[2];
            case SOUTH -> eye.z > b[5];
            case WEST -> eye.x < b[0];
            case EAST -> eye.x > b[3];
            case DOWN -> eye.y < b[1];
            case UP -> eye.y > b[4];
        };
    }

    /** Angle (degrés) entre la caméra et la boîte : le plus petit du centre et du point le plus proche. */
    static double angleBoite(Vec3 c, Vector3fc f, double[] b) {
        double cx = (b[0] + b[3]) / 2, cy = (b[1] + b[4]) / 2, cz = (b[2] + b[5]) / 2;
        double a1 = angle(c, f, cx, cy, cz);
        double px = Math.max(b[0], Math.min(b[3], c.x)), py = Math.max(b[1], Math.min(b[4], c.y)), pz = Math.max(b[2], Math.min(b[5], c.z));
        return Math.min(a1, angle(c, f, px, py, pz));
    }

    private static double angle(Vec3 c, Vector3fc f, double x, double y, double z) {
        double vx = x - c.x, vy = y - c.y, vz = z - c.z;
        double n = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (n < 1e-6) return 0;
        double cos = (vx * f.x() + vy * f.y() + vz * f.z()) / n;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    static double distanceBoite(double px, double py, double pz, double[] b) {
        double dx = Math.max(Math.max(b[0] - px, 0), px - b[3]);
        double dy = Math.max(Math.max(b[1] - py, 0), py - b[4]);
        double dz = Math.max(Math.max(b[2] - pz, 0), pz - b[5]);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Ligne de vue des yeux vers la boîte (point le plus proche rentré de 5 cm, puis centre) : aucun bloc plein avant la
     * boîte ; pour une entité, les barrières collées au meuble (hitbox Nexo) ne cachent pas. Même règle que le serveur
     * ({@code PlaqueRegles.rayonLibre}) : 4 essais, et après une barrière le rayon repart <b>sorti de son bloc</b> (pas à
     * pas de 5 cm), jamais de l'intérieur — un rayon en biais dans une barrière la traverse jusqu'à 1,73 bloc.
     */
    private static boolean enVue(Minecraft mc, LocalPlayer p, Vec3 eye, Ancre a) {
        double[] b = a.b;
        double cx = (b[0] + b[3]) / 2, cy = (b[1] + b[4]) / 2, cz = (b[2] + b[5]) / 2;
        double px = Math.max(b[0], Math.min(b[3], eye.x)), py = Math.max(b[1], Math.min(b[4], eye.y)), pz = Math.max(b[2], Math.min(b[5], eye.z));
        px += Math.signum(cx - px) * Math.min(0.05, Math.abs(cx - px));
        py += Math.signum(cy - py) * Math.min(0.05, Math.abs(cy - py));
        pz += Math.signum(cz - pz) * Math.min(0.05, Math.abs(cz - pz));
        return rayon(mc, p, eye, px, py, pz, a) || rayon(mc, p, eye, cx, cy, cz, a);
    }

    private static boolean rayon(Minecraft mc, LocalPlayer p, Vec3 de, double x, double y, double z, Ancre a) {
        Vec3 vers = new Vec3(x, y, z);
        for (int essai = 0; essai < 4; essai++) {
            if (de.distanceToSqr(vers) < 0.0025) return true;
            BlockHitResult h = mc.level.clip(new ClipContext(de, vers, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (h == null || h.getType() == HitResult.Type.MISS) return true;
            Vec3 l = h.getLocation();
            if (dans(a.b, l.x, l.y, l.z, 0.02)) return true;
            BlockPos bp = h.getBlockPos();
            boolean barriere = !a.bloc && mc.level.getBlockState(bp).is(Blocks.BARRIER)
                && dans(a.b, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 1.0);
            if (!barriere) return false;
            // hitbox du meuble : on repart juste derrière la barrière (sortie de son bloc), vers la même cible
            double dx = vers.x - de.x, dy = vers.y - de.y, dz = vers.z - de.z, n = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (n < 1e-6) return true;
            dx /= n;
            dy /= n;
            dz /= n;
            double px = l.x + dx * 0.02, py = l.y + dy * 0.02, pz = l.z + dz * 0.02;
            for (int k = 0; k < 40 && px >= bp.getX() && px <= bp.getX() + 1 && py >= bp.getY() && py <= bp.getY() + 1
                && pz >= bp.getZ() && pz <= bp.getZ() + 1; k++) {
                px += dx * 0.05;
                py += dy * 0.05;
                pz += dz * 0.05;
            }
            de = new Vec3(px, py, pz);
        }
        return false;
    }

    private static boolean dans(double[] b, double x, double y, double z, double m) {
        return x >= b[0] - m && x <= b[3] + m && y >= b[1] - m && y <= b[4] + m && z >= b[2] - m && z <= b[5] + m;
    }

    /* ================================================================ point d'ancre du texte (rendu, chaque image) */

    /**
     * Point du monde où poser le texte de l'entrée {@code recitId} ({@code "plaque:<id>"}) : centre de la face de la
     * boîte tournée vers la caméra (bloc, boîte ; faces verticales seulement : le texte reste « de face ») ou centre de la
     * boîte (entité), avancé de {@value #DEVANT} bloc vers la caméra. Faux si ce n'est pas une plaque connue (banc, aperçu :
     * le texte reste au centre de l'écran). Aucune allocation.
     */
    public static boolean pointTexte(String recitId, double[] out) {
        if (recitId == null || !recitId.startsWith("plaque:")) return false;
        double[] b;
        boolean bloc;
        net.minecraft.core.Direction face;
        if (estId(recitId, boiteAId)) { b = BOITE_A; bloc = boiteABloc; face = boiteAFace; }
        else if (estId(recitId, boiteBId)) { b = BOITE_B; bloc = boiteBBloc; face = boiteBFace; }
        else return false;
        return pointDevant(b, bloc, face, out);
    }

    /**
     * Point devant la boîte {@code b} vu de la caméra (même règle que la lecture) : centre de la face verticale tournée vers
     * la caméra pour un bloc ou une boîte, centre de la boîte pour une entité, avancé de {@value #DEVANT} bloc vers la
     * caméra. Sert aussi à l'aperçu de l'outil « Plaques ». Aucune allocation.
     */
    public static boolean pointDevant(double[] b, boolean bloc, double[] out) {
        return pointDevant(b, bloc, null, out);
    }

    /** Comme {@link #pointDevant(double[], boolean, double[])} ; {@code face} verticale visée : le texte se pose sur elle. */
    public static boolean pointDevant(double[] b, boolean bloc, net.minecraft.core.Direction face, double[] out) {
        Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
        if (cam == null) return false;
        Vec3 c = cam.position();
        double cx = (b[0] + b[3]) / 2, cy = (b[1] + b[4]) / 2, cz = (b[2] + b[5]) / 2;
        double x = cx, y = cy, z = cz;
        if (bloc && face != null && face.getAxis().isHorizontal()) {
            // la face visée au sélecteur (la plaque ne se lit que de ce côté)
            switch (face) {
                case NORTH -> z = b[2];
                case SOUTH -> z = b[5];
                case WEST -> x = b[0];
                default -> x = b[3];
            }
        } else if (bloc) {
            // face verticale la plus tournée vers la caméra
            double dx = c.x - cx, dz = c.z - cz;
            if (Math.abs(dx) * (b[5] - b[2]) >= Math.abs(dz) * (b[3] - b[0])) x = dx > 0 ? b[3] : b[0];
            else z = dz > 0 ? b[5] : b[2];
        }
        double vx = c.x - x, vy = c.y - y, vz = c.z - z;
        double n = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (n > 1e-4) {
            x += vx / n * DEVANT;
            y += vy / n * DEVANT;
            z += vz / n * DEVANT;
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
        return true;
    }

    /**
     * Couleur du halo de l'entrée {@code recitId} ({@code "plaque:<id>"}) : {@link HaloPlaque#code} de son dernier texte,
     * ivoire-or ({@link HaloPlaque#DEFAUT}) sinon. Aucune allocation (lu par {@code RecitHud} à chaque image).
     */
    public static int haloDe(String recitId) {
        if (recitId == null) return HaloPlaque.DEFAUT;
        if (estId(recitId, boiteAId)) return haloA;
        if (estId(recitId, boiteBId)) return haloB;
        return HaloPlaque.DEFAUT;
    }

    /** {@code recitId} vaut « plaque:<id> » (sans allocation). */
    private static boolean estId(String recitId, String id) {
        return id != null && recitId.length() == 7 + id.length() && recitId.startsWith(id, 7);
    }

    /* ================================================================ outils */

    private static String str(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; } catch (RuntimeException e) { return ""; }
    }

    private static int num(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsInt() : 0; } catch (RuntimeException e) { return 0; }
    }

    private static double dbl(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsDouble() : 0; } catch (RuntimeException e) { return 0; }
    }

    private static boolean bool(JsonObject o, String k) {
        try { return o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }

    /** Banc : révision du texte montré. */
    public static int lueRev() { return lueRev; }

    /** Banc : l'état en une ligne. */
    public static String bilan() {
        return "ancres " + ANCRES.size() + ", acceptée " + accepte + ", en face " + choisie + ", lue " + lue + " (rév. " + lueRev + ")"
            + ", cache " + CACHE.size();
    }

    static void log(String m) { LOG.debug(m); }
}
