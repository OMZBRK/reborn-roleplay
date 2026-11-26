package fr.reborn.hud.mur;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.combat.ChakraJump;
import fr.reborn.hud.combat.CombatInput;
import fr.reborn.hud.etat.EtatClient;
import fr.reborn.hud.mixin.LivingEntityMurAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <b>Marche sur les murs</b> (lot 5, Game Plan n° 17 ; essai C0 du 23/11, étape C1a), côté client. Approche « B+ » de
 * l'étude : pieds sur la paroi, corps à 90°, et sur tous les clients une boîte de collision couchée qui suit ce corps,
 * alors que le serveur ne tient qu'un cube de 0,6 (pose de nage fixée, gravité nulle). <b>Inerte</b> tant que le serveur
 * n'a pas envoyé de permis ({@code /murtest on} d'un membre du staff, ShinobiAbilities {@code MurTest}) : sans permis,
 * rien n'est lu au clavier, rien n'est envoyé, et les mixins ne font qu'une lecture de booléen.
 *
 * <p><b>Géométrie.</b> {@code P} est la position de l'entité (bas-centre du cube du serveur), tenue à
 * {@value #DISTANCE} bloc de la paroi ; {@code n} la normale de la paroi ({@link MurFace}). Boîte du corps : de
 * {@code P − 0,85 n} à {@code P + 0,9 n}, ± 0,3 sur l'horizontale de la paroi, de {@code P.y} à {@code P.y + 0,6} : elle
 * contient toujours le cube du serveur. Un « mur plein » est un bloc dont la face tournée vers le joueur est pleine
 * (règle du serveur, {@link MurTransitions#facePleine}).
 *
 * <p><b>Ordre</b> (canal {@code reborn:mur}, {@link MurCanal}). Permis en main, le joueur pousse contre un mur plein
 * {@value #TICKS_POUSSEE} ticks : le mod envoie {@code accroche} et <b>attend</b>. À la réception de son propre
 * {@code etat}, il joue la bascule ({@value #TICKS_ACCROCHE} ticks, {@code P} passe de 0,3 à 0,9 de la paroi et monte de
 * 0,6), puis marche. Sans réponse après une seconde : abandon, rien n'a changé. Espace ou la touche de course de chakra :
 * sortie en {@value #TICKS_SORTIE} ticks vers une place où un corps debout tient, puis {@code lache} avec le nombre de
 * corrections de position reçues. Le serveur peut lâcher de lui-même ({@code etat} « - », {@code permis} retiré). Chaque
 * demande porte un numéro {@code n} croissant ; un {@code etat} de soi qui porte un autre numéro que la dernière demande
 * est un accusé en retard, ignoré.
 *
 * <p><b>Passages</b> (étape C1a, cahier §2 et §3). On passe d'une surface à l'autre en poussant contre une limite, dans la
 * direction voulue ({@link MurEntrees}) ; le type de limite choisit le passage. Les contrôles ne tournent que pendant
 * cette poussée, jamais à chaque tick de marche.
 * <ul>
 *   <li><b>coin rentrant</b> : l'avance le long de la paroi est arrêtée par la face pleine d'un mur perpendiculaire ;</li>
 *   <li><b>coin sortant</b> : le mur s'arrête sous le bord meneur et la face voisine du bloc d'angle est pleine
 *       ({@link MurTransitions#coinSortant}, la règle du serveur) ;</li>
 *   <li><b>sommet</b> : la montée est rognée par la fin du mur {@value #TICKS_LIMITE} ticks et un corps debout tient sur
 *       le toit (sinon, auvent : on reste au bord) ;</li>
 *   <li><b>pied</b> : le sol à moins d'un bloc sous la boîte en descendant.</li>
 * </ul>
 * Coins : au premier tick de poussée le mod envoie {@code face} (la face d'arrivée) et continue de pousser ; le virage
 * ({@link Phase#VIRAGE}) part quand la poussée a tenu {@value #TICKS_LIMITE} ticks <b>et</b> que l'accord est arrivé.
 * Sans accord après une seconde, ou touche relâchée avant le virage, le mod renvoie {@code face} avec l'ancienne face et
 * reste au bord ; un {@code refus} de face le laisse au bord, toujours au mur. Pendant le virage la boîte est le cube du
 * serveur, posé sur des points lus libres ; le cap est conservé et la caméra change de face au départ
 * ({@link MurCamera#changerFace}). Sommet et pied sont des sorties animées : le corps se redresse, {@code lache} part au
 * début de la sortie par le sommet (le serveur rend pose et gravité, son écho « - » est ignoré), à la fin au pied.
 *
 * <p><b>Ce que la classe tient.</b> L'état {@link Etat} de chaque entité au mur, par identifiant (il peut arriver avant
 * l'entité), lu par les mixins : boîte ({@code EntityMurMixin}), pose de nage tenue et déplacement du joueur local
 * ({@code PlayerMurMixin}), rendu ({@link MurRendu}), corrections ({@code ClientPacketListenerMurMixin}). Chez les autres
 * joueurs, un changement de face est joué comme un virage : le lacet rendu de la paroi rejoint la nouvelle face à
 * {@value #LACET_PAS}° par tick au plus. La direction voulue vient de {@link MurEntrees}, la caméra de paroi de
 * {@link MurCamera}, la géométrie des passages de {@link MurTransitions}. Tout est remis à zéro à la déconnexion.
 */
public final class MurClient {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/mur");

    /* ---- géométrie (cahier commun, §1) */
    /** Distance de {@code P} à la paroi, au mur. */
    public static final double DISTANCE = 0.9;
    /** Étendue de la boîte derrière {@code P} (vers la paroi) et devant (vers l'air). */
    public static final double ARRIERE = 0.85, AVANT = 0.9;
    /** Demi-largeur de la boîte dans le plan de la paroi, et sa hauteur. */
    public static final double DEMI = 0.3, HAUTEUR = 0.6;
    /** Distance de {@code P} à la paroi quand on pousse debout contre elle. */
    private static final double CONTACT = 0.3;
    /** La bascule monte {@code P} d'autant : le centre du corps reste à la même hauteur. */
    private static final double ELAN = 0.6;

    static final int TICKS_POUSSEE = 3, TICKS_ACCROCHE = 6, TICKS_SORTIE = 3;
    /** Poussée contre une limite (coin, sommet) avant le passage. */
    static final int TICKS_LIMITE = 2;
    /** Attente de la réponse du serveur (1 s), contrôle de la paroi à l'arrêt, oubli d'un état sans entité (10 s). */
    private static final int TICKS_REPONSE = 20, TICKS_CONTROLE = 5, TICKS_ABSENCE = 200;
    /** 3,2 blocs/s en marche, 5,6 avec la touche de sprint. */
    private static final double PAS_MARCHE = 3.2 / 20.0, PAS_COURSE = 5.6 / 20.0;
    /** Écart à la paroi rattrapé sans rien dire ; au-delà (correction du serveur ailleurs), on lâche. */
    private static final double TOLERANCE = 0.05;
    /** Une direction pousse contre une limite si sa part vers cette limite atteint cela (60° au plus d'écart). */
    private static final double POUSSEE_MIN = 0.5;
    /** Écart de {@code P} au point de départ prévu d'un coin, au-delà duquel le virage n'est pas tenté. */
    private static final double DEPART_ECART = 0.05;
    /** Pied du mur : le sol est cherché jusqu'à cette profondeur sous {@code P}. */
    private static final double PIED_PROFONDEUR = 1.2;
    /** Rotation rendue du corps pendant un virage : au plus tant de degrés par tick. */
    static final float LACET_PAS = 20f;
    private static final double EPS = 1.0e-7;
    private static final float DEMI_TOUR = (float) Math.PI, TOUR = (float) (Math.PI * 2);

    public enum Phase { REPOS, ATTENTE, BASCULE, MARCHE, VIRAGE, SORTIE }

    /** Genre de la sortie en cours : voulue (Espace, touche de course, mur disparu), par le sommet, par le pied. */
    private enum Sortie { VOULUE, SOMMET, PIED }

    /** Limite contre laquelle on pousse : fin du mur le long de la paroi, mur perpendiculaire, fin du mur en haut. */
    private enum Limite { BORD, OBSTACLE, HAUT }

    /** État d'une entité au mur, vu de ce client. */
    public static final class Etat {
        final int id;
        MurFace face;
        /** La boîte couchée est posée (faux pendant le redressement qui suit un lâcher). */
        boolean couche;
        /** La boîte est le cube du serveur (virage de coin, sortie animée), pas la boîte couchée. */
        boolean cube;
        /** Vrai tant que le corps se couche ou reste couché, faux quand il se redresse. */
        boolean monte;
        /** Joueur local : {@code k} et le cap sont pilotés par sa propre marche. */
        boolean local;
        /** Joueur local : coordonnée de la paroi sur l'axe de la normale. */
        double plan;
        /** Avancement de la bascule (0 debout, 1 couché) et cap du corps sur la paroi (radians, 0 = vers le haut). */
        float k, kAvant, cap, capAvant;
        /**
         * Lacet rendu de la paroi (degrés du jeu, non ramenés), au tick d'avant, et celui qu'il rejoint au plus de
         * {@value #LACET_PAS}° par tick : celui de la face, ou l'avancée d'un virage.
         */
        float lacet, lacetAvant, lacetCible;
        /**
         * Joueur local, sortie animée (sommet, pied) : lacet du corps debout visé (degrés), que le rendu rejoint à la
         * place du {@code yBodyRot} du jeu ; NaN sinon.
         */
        float debout = Float.NaN;
        Entity entite;
        int absent;
        double x, y, z;

        Etat(int id) { this.id = id; }
    }

    private static final Int2ObjectOpenHashMap<Etat> ETATS = new Int2ObjectOpenHashMap<>();
    private static final MurFace[] FACES = MurFace.values();
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final double[] DESCENTES = {-ELAN, -0.45, -0.3, -0.15, 0.0};
    /** Boîtes d'un bloc plein, pour la règle de la face pleine (le cas courant ne crée rien). */
    private static final List<double[]> BLOC_PLEIN = List.of(new double[] {0, 0, 0, 1, 1, 1});
    /** Lecteur de blocs de {@link MurTransitions} sur le monde du joueur local. */
    private static final MurTransitions.LecteurBlocs LECTEUR = (x, y, z) -> boites(MurClient.monde, x, y, z);

    /** Au moins une entité a un état : lu par les chemins chauds (boîte, rendu) avant toute autre chose. */
    public static volatile boolean actif;
    /** Le corps du joueur local est au mur (bascule, marche, virage ou sortie) : pose tenue, déplacement remplacé, entrées coupées. */
    public static volatile boolean corpsPris;

    /* ---- joueur local */
    private static boolean permis;
    private static Phase phase = Phase.REPOS;
    private static Sortie sortie = Sortie.VOULUE;
    private static LocalPlayer joueur;
    private static Etat moi;
    private static ClientLevel monde;
    /** Face demandée (attente) ou tenue. */
    private static MurFace face;
    /** Paroi sur l'axe de la normale, et indice de la couche de blocs du mur. */
    private static double plan;
    private static int couche;
    private static int poussee, attente, pas, depuisControle, duree, nbCorrections;
    /** Après un abandon, un refus ou une sortie : il faut cesser de pousser avant une nouvelle demande. */
    private static boolean relacheExigee;
    private static boolean espaceAvant, toucheLache;
    /** Pas de la bascule ou de la sortie voulue, et leur arrivée. */
    private static double dx, dy, dz, cx, cy, cz;

    /* ---- numéros des demandes et passages */
    /** Numéro de la dernière demande envoyée ; numéro de la dernière demande suivie d'un {@code lache}. */
    private static int numero, numeroLache;
    /** Limite contre laquelle on pousse (null : aucune), son sens sur l'axe horizontal de la paroi, et depuis combien de ticks. */
    private static Limite limite;
    private static int limiteSens, pousseeLimite;
    /** Le passage de cette limite a été jugé impossible (ou refusé) : on reste au bord tant qu'on la pousse. */
    private static boolean limiteEchec;
    /** Changement de face demandé et pas encore joué (null : aucun), son numéro, l'accord reçu, l'attente. */
    private static MurFace faceDemandee;
    private static int numeroFace, attenteFace;
    private static boolean accord;
    /** Le {@code lache} de la marche en cours est déjà parti (sortie par le sommet). */
    private static boolean lacheEnvoye;
    /** Chemin du virage ou de la sortie animée : un point par tick, et leur nombre. */
    private static final double[][] CHEMIN = new double[MurTransitions.SORTANT_MAX][3];
    private static int nbPas;
    /** Virage préparé : face d'arrivée, sa paroi, sa couche de blocs ; lacet du départ et quart de tour signé. */
    private static MurFace faceVirage;
    private static double planVirage;
    private static int coucheVirage;
    private static float lacetDepart, lacetTour;
    /** Sortie animée : lacet du corps debout à l'arrivée (face au toit, ou dos au mur). */
    private static float corpsVise;

    private MurClient() {}

    public static void init() {
        MurCanal.init();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> oublier());
        ClientTickEvents.END_CLIENT_TICK.register(MurClient::tick);
    }

    /* ================================================================ lecture (mixins, autres modules, banc) */

    public static boolean permis() { return permis; }
    public static Phase phase() { return phase; }
    public static int corrections() { return nbCorrections; }

    /** La face que tient le joueur local (celle d'arrivée pendant un virage) ; {@code null} hors du mur. */
    public static MurFace faceTenue() { return corpsPris && moi != null ? moi.face : null; }

    /** Numéro de la dernière demande envoyée au serveur. */
    public static int numero() { return numero; }

    /** Lacet rendu de la paroi d'une entité (degrés), {@code NaN} si elle n'est pas au mur. */
    public static float lacetRendu(int id) {
        Etat s = ETATS.get(id);
        return s == null ? Float.NaN : s.lacet;
    }

    /** État d'une entité ; {@code null} si elle n'est pas au mur. */
    public static Etat etat(int id) { return ETATS.get(id); }

    /** Cette entité a-t-elle le corps couché sur un mur (boîte posée) ? */
    public static boolean auMur(Entity e) {
        if (!actif) return false;
        Etat s = ETATS.get(e.getId());
        return s != null && s.couche;
    }

    /** {@code ClientPacketListenerMurMixin} : une correction de position du serveur pendant la marche. */
    public static void correction() { nbCorrections++; }

    /** La touche de course de chakra a été pressée au mur ({@code allure/Allure}, qui lit ses appuis d'abord) : on lâche. */
    public static void toucheLache() { toucheLache = true; }

    /**
     * Appuis de la touche de course de chakra restés sans lecteur ({@code Allure} ne les lit que si le serveur a activé
     * les allures) : vidés, vrai s'il y en avait.
     */
    private static boolean toucheCourse() {
        net.minecraft.client.KeyMapping k = fr.reborn.hud.keybind.HudKeybinds.COURSE_CHAKRA;
        boolean appui = false;
        if (k != null) while (k.consumeClick()) appui = true;
        return appui;
    }

    /* ================================================================ boîte */

    /**
     * Boîte d'une entité ({@code EntityMurMixin}, à chaque {@code setPos}) : couchée pour un avatar de ce client dont
     * l'état est connu, sinon celle du jeu (le cube de la pose de nage pendant un virage ou une sortie animée). Le serveur
     * intégré du solo garde ses boîtes (filtre {@code isClientSide}). Chez le joueur local, pendant la bascule, la boîte
     * ne dépasse pas la paroi.
     */
    public static AABB boite(Entity e, Vec3 p, AABB origine) {
        if (!(e instanceof Avatar) || !e.level().isClientSide()) return origine;
        Etat s = ETATS.get(e.getId());
        if (s == null || !s.couche || s.cube) return origine;
        MurFace f = s.face;
        double arriere = ARRIERE;
        if (s.local) arriere = Mth.clamp(Math.abs(f.surNormale(p.x, p.z) - s.plan), CONTACT, ARRIERE);
        return couchee(f, p.x, p.y, p.z, arriere);
    }

    private static AABB couchee(MurFace f, double x, double y, double z, double arriere) {
        double a = -f.sens() * arriere, b = f.sens() * AVANT;
        double min = Math.min(a, b), max = Math.max(a, b);
        return f.nx != 0
            ? new AABB(x + min, y, z - DEMI, x + max, y + HAUTEUR, z + DEMI)
            : new AABB(x - DEMI, y, z + min, x + DEMI, y + HAUTEUR, z + max);
    }

    /* ================================================================ messages du serveur */

    static void recevoir(JsonObject o) {
        String t = o.has("t") ? o.get("t").getAsString() : "";
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Integer n = entier(o, "n");
        switch (t) {
            case "permis" -> {
                permis = o.has("on") && o.get("on").getAsBoolean();
                if (permis) return;
                if (corpsPris) liberer(p, "permis retiré");
                else renoncer();
            }
            case "etat" -> {
                int id = o.get("e").getAsInt();
                MurFace f = MurFace.de(o.get("f").getAsString());
                if (p == null || mc.level == null) return;
                suivreMonde(mc, p);
                if (id == p.getId()) etatLocal(mc, p, f, n);
                else etatVoisin(mc.level, id, f);
            }
            case "refus" -> {
                String a = o.has("a") && o.get("a").isJsonPrimitive() ? o.get("a").getAsString() : null;
                if ("face".equals(a)) {
                    refusFace(n);
                    return;
                }
                if (n != null && n != numero) return;   // refus d'une demande plus ancienne que la dernière
                // un refus d'accroche au mur dit que le serveur ne l'y tient pas
                if (corpsPris) liberer(p, "refusé par le serveur");
                else renoncer();
            }
            default -> { }
        }
    }

    private static Integer entier(JsonObject o, String champ) {
        JsonElement e = o.get(champ);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        return e.getAsInt();
    }

    /** Demande restée sans suite (refus, délai, permis retiré) : rien n'a changé, on attend que le joueur cesse de pousser. */
    private static void renoncer() {
        if (phase != Phase.ATTENTE) return;
        phase = Phase.REPOS;
        relacheExigee = true;
    }

    /** Le serveur refuse le changement de face : le joueur reste sur sa face, arrêté au bord, toujours au mur. */
    private static void refusFace(Integer n) {
        if (faceDemandee == null || (n != null && n != numeroFace)) return;
        faceDemandee = null;
        accord = false;
        limiteEchec = true;
    }

    /** L'état du joueur lui-même : la confirmation de sa demande, l'accord d'un virage, ou le serveur qui le lâche. */
    private static void etatLocal(Minecraft mc, LocalPlayer p, MurFace f, Integer n) {
        // accusé d'une demande plus ancienne que la dernière (virage annulé, deux coins enchaînés) : en retard
        if (n != null && n != numero) return;
        // sortie par le sommet : le lache est déjà parti, l'écho du serveur ne doit pas couper l'animation
        if (corpsPris && lacheEnvoye) return;
        if (f == null) {
            // hors du mur, « - » n'est que l'écho d'un lache déjà envoyé (un refus arrive par « refus ») : la demande
            // en attente garde sa vraie réponse (etat, refus, ou le délai d'une seconde)
            if (corpsPris) liberer(p, "lâché par le serveur");
            return;
        }
        if (phase == Phase.ATTENTE && f == face) {
            // le joueur a pu bouger depuis la demande : la place est relue à l'instant de la réponse
            if (libre(p) && accrochePossible(mc.level, p, f)) commencer(mc, p);
            else {
                MurCanal.lache(0);
                numeroLache = numero;
                renoncer();
            }
            return;
        }
        if (corpsPris) {
            if (n != null && n == numeroFace) {
                // l'accord d'un virage : il partira quand la poussée aura tenu ; une demande déjà oubliée (sortie
                // commencée entre-temps, son lache suit) ne change plus rien
                if (faceDemandee != null && f == faceDemandee) accord = true;
                return;
            }
            if (moi.face == f) return;   // rappel du serveur, ou accusé d'une annulation
            liberer(p, "face imprévue");
            return;
        }
        // réponse à une demande suivie depuis d'un lache : le serveur a lâché le joueur après l'avoir écrite
        if (n != null && n <= numeroLache) return;
        // état que le mod n'attend pas (réponse arrivée après l'abandon) : le serveur n'en envoie jamais de lui-même au
        // joueur, donc il le tient au mur ; le mod le rend, le serveur retire pose et gravité
        MurCanal.lache(0);
        numeroLache = numero;
        renoncer();
    }

    /**
     * L'état d'un autre joueur, diffusé par le serveur à ceux qui le voient : sa boîte et son corps en dépendent. Une
     * nouvelle face alors qu'il est déjà couché est un virage de coin : le corps tourne vers elle au plus de
     * {@value #LACET_PAS}° par tick, avec le cube pour boîte le temps du virage.
     */
    private static void etatVoisin(ClientLevel level, int id, MurFace f) {
        Etat s = ETATS.get(id);
        if (f == null) {
            if (s == null) return;
            s.couche = false;
            s.monte = false;
            if (s.k <= 0f) ETATS.remove(id);
        } else {
            boolean nouveau = s == null || !s.couche || s.face == null;
            if (s == null) {
                s = new Etat(id);
                ETATS.put(id, s);
            }
            if (nouveau) {
                s.lacet = s.lacetAvant = s.lacetCible = f.lacet;
                s.cube = false;
            } else if (s.face != f) {
                s.lacetCible = s.lacet + court(f.lacet - s.lacet);
                s.cube = true;
            }
            s.face = f;
            s.couche = true;
            s.monte = true;
        }
        actif = !ETATS.isEmpty();
        // la boîte n'est refaite que par setPos : un joueur immobile garderait l'ancienne
        Entity e = level.getEntity(id);
        if (e == null) return;
        if (s != null) {
            s.entite = e;
            s.x = e.getX();
            s.y = e.getY();
            s.z = e.getZ();
        }
        e.refreshDimensions();
    }

    /* ================================================================ tick */

    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            if (actif || phase != Phase.REPOS) oublier();
            return;
        }
        suivreMonde(mc, p);
        local(mc, p);
        if (MurCamera.actif) MurCamera.tick(mc);
        if (actif) entites(mc.level);
    }

    /** Changement de monde : les identifiants d'entité de l'ancien ne valent plus rien. */
    private static void suivreMonde(Minecraft mc, LocalPlayer p) {
        if (mc.level == monde) return;
        monde = mc.level;
        if (corpsPris) liberer(p, "changement de monde");
        ETATS.clear();
        actif = false;
    }

    private static void local(Minecraft mc, LocalPlayer p) {
        switch (phase) {
            case REPOS -> pousser(mc, p);
            case ATTENTE -> {
                if (++attente > TICKS_REPONSE) renoncer();
            }
            case BASCULE, MARCHE, VIRAGE, SORTIE -> {
                duree++;
                // Espace (un nouvel appui) ou la touche de course de chakra lâchent ; le clavier n'est lu qu'au mur
                boolean espace = mc.options.keyJump.isDown() && mc.gui.screen() == null;
                boolean appui = espace && !espaceAvant;
                espaceAvant = espace;
                if (toucheCourse()) toucheLache = true;
                String rupture = rupture(p);
                if (rupture != null) liberer(p, rupture);
                else {
                    MurCamera.tenir(mc);
                    if (phase == Phase.MARCHE) {
                        if (appui || toucheLache) sortir(p);
                        else if (++depuisControle >= TICKS_CONTROLE) {
                            depuisControle = 0;
                            // le mur a disparu sous le corps (bloc cassé, porte ouverte) : on se relève
                            if (!paroiPleine(p.level(), moi.face, couche, moi.face.surParoi(p.getX(), p.getZ()), p.getY())) sortir(p);
                        }
                    }
                }
            }
        }
        toucheLache = false;
    }

    /** Ce qui retire le corps du mur sans attendre : le joueur n'est plus en état de marcher. */
    private static String rupture(LocalPlayer p) {
        if (p != joueur) return "joueur remplacé";
        if (!p.isAlive()) return "mort";
        if (p.isSpectator() || p.getAbilities().flying || p.isPassenger() || p.isFallFlying()) return "autre déplacement";
        if (p.isInWater() || p.isInLava()) return "eau";
        if (ChakraJump.INSTANCE.enVol()) return "saut chargé";
        var ko = fr.reborn.hud.ko.KoClient.state();
        if (ko != null && (ko.phase() == 1 || ko.phase() == 2)) return "à terre";
        if (EtatClient.enJeu() && EtatClient.refuse("mob")) return "mouvement refusé";
        return null;
    }

    /** Le joueur est-il en état de s'accrocher (le serveur revérifie tout) ? */
    private static boolean libre(LocalPlayer p) {
        if (!p.isAlive() || p.isSpectator() || p.getAbilities().flying || p.isPassenger() || p.isFallFlying()) return false;
        if (p.isInWater() || p.isInLava() || p.onClimbable()) return false;
        if (ChakraJump.INSTANCE.phase() != ChakraJump.Phase.REPOS || CombatInput.INSTANCE.isBlocking()) return false;
        var ko = fr.reborn.hud.ko.KoClient.state();
        if (ko != null && (ko.phase() == 1 || ko.phase() == 2)) return false;
        return !(EtatClient.enJeu() && EtatClient.refuse("mob"));
    }

    /* ================================================================ accroche */

    /** Au repos, permis en main : trois ticks de poussée contre un mur plein envoient la demande. */
    private static void pousser(Minecraft mc, LocalPlayer p) {
        if (!permis) {
            poussee = 0;
            return;
        }
        MurFace f = p.horizontalCollision && mc.gui.screen() == null && libre(p) ? faceVisee(p) : null;
        if (f == null) {
            poussee = 0;
            // réarmé seulement quand plus aucune touche de déplacement n'est tenue : après une sortie le joueur n'est
            // plus en collision (horizontalCollision n'est écrit que par move), la garde tombait au tick même
            Vec2 mv = p.input.getMoveVector();
            if (mv.x * mv.x + mv.y * mv.y < 0.01f) relacheExigee = false;
            return;
        }
        if (relacheExigee) return;
        if (f != face) {
            face = f;
            poussee = 0;
        }
        if (++poussee < TICKS_POUSSEE) return;
        poussee = 0;
        if (!MurCanal.disponible() || !accrochePossible(mc.level, p, f)) return;
        MurCanal.accroche(f, ++numero);
        phase = Phase.ATTENTE;
        attente = 0;
    }

    /** La face du mur vers lequel le joueur pousse : direction voulue à moins de 60° de {@code −n}. */
    private static MurFace faceVisee(LocalPlayer p) {
        Vec2 mv = p.input.getMoveVector();
        if (mv.x * mv.x + mv.y * mv.y < 0.01f) return null;
        double lacet = Math.toRadians(p.getYRot()), sin = Math.sin(lacet), cos = Math.cos(lacet);
        double wx = mv.x * cos - mv.y * sin, wz = mv.y * cos + mv.x * sin;
        double max = 0.5 * Math.sqrt(wx * wx + wz * wz);
        MurFace visee = null;
        for (MurFace f : FACES) {
            double d = -(wx * f.nx + wz * f.nz);
            if (d >= max) {
                max = d;
                visee = f;
            }
        }
        return visee;
    }

    /**
     * La place couchée existe-t-elle : joueur debout contre un mur plat et plein, mur plein sous tout le corps à
     * l'arrivée, 1,8 de libre devant la paroi ? Pose la paroi et l'arrivée de la bascule.
     */
    private static boolean accrochePossible(Level level, LocalPlayer p, MurFace f) {
        int sens = f.sens();
        double c = f.surNormale(p.getX(), p.getZ());
        int bloc = Mth.floor(c - sens * (CONTACT + TOLERANCE));
        double paroi = sens > 0 ? bloc + 1 : bloc;
        if (Math.abs((c - paroi) * sens - CONTACT) > 0.02) return false;
        double y = p.getY() + ELAN;
        if (!paroiPleine(level, f, bloc, f.surParoi(p.getX(), p.getZ()), y)) return false;
        double n = paroi + sens * DISTANCE;
        double x = f.nx != 0 ? n : p.getX(), z = f.nx != 0 ? p.getZ() : n;
        if (!level.noCollision(p, couchee(f, x, y, z, ARRIERE).deflate(1.0e-4))) return false;
        plan = paroi;
        couche = bloc;
        cx = x;
        cy = y;
        cz = z;
        return true;
    }

    /** Le serveur a confirmé : le cube du serveur est déjà petit, la bascule peut partir. */
    private static void commencer(Minecraft mc, LocalPlayer p) {
        Etat s = new Etat(p.getId());
        s.face = face;
        s.local = true;
        s.couche = true;
        s.monte = true;
        s.plan = plan;
        s.entite = p;
        s.lacet = s.lacetAvant = s.lacetCible = face.lacet;
        ETATS.put(s.id, s);
        actif = true;
        moi = s;
        joueur = p;
        phase = Phase.BASCULE;
        sortie = Sortie.VOULUE;
        pas = 0;
        duree = 0;
        nbCorrections = 0;
        depuisControle = 0;
        lacheEnvoye = false;
        oublierLimite();
        MurEntrees.oublier();
        toucheLache = false;
        espaceAvant = true;                  // Espace tenue à l'accroche (saut contre le mur) : il faudra un nouvel appui
        dx = (cx - p.getX()) / TICKS_ACCROCHE;
        dy = (cy - p.getY()) / TICKS_ACCROCHE;
        dz = (cz - p.getZ()) / TICKS_ACCROCHE;
        toucheCourse();                      // appuis d'avant le mur : oubliés
        MurCamera.accrocher(mc, p, face);   // avant la pose : le cadrage part de l'œil debout
        corpsPris = true;
        p.setPose(Pose.SWIMMING);
        p.refreshDimensions();
    }

    /* ================================================================ déplacement (PlayerMurMixin) */

    /** {@code PlayerMurMixin}, avant {@code Player.updatePlayerPose} : le jeu remettrait la pose debout à chaque tick. */
    public static void tenirPose(LocalPlayer p) {
        if (p.getPose() != Pose.SWIMMING) p.setPose(Pose.SWIMMING);
    }

    /**
     * Un tick de déplacement au mur, à la place de {@code Player.travel} pour le joueur local : ni gravité, ni frottement,
     * ni glisse. Les collisions restent celles du jeu ({@code move}), avec la boîte couchée ; virages et sorties animées
     * suivent des points lus libres.
     *
     * @return vrai si le tick a été joué ici (le jeu ne fait rien de plus)
     */
    public static boolean voyage(LocalPlayer p) {
        if (!corpsPris || p != joueur) return false;
        // un autre déplacement a pris le corps (saut chargé parti, vol, monture) : le tick de fin lâchera
        if (ChakraJump.INSTANCE.enVol() || p.getAbilities().flying || p.isPassenger()) return false;
        Etat s = moi;
        s.kAvant = s.k;
        s.capAvant = s.cap;
        switch (phase) {
            case BASCULE -> {
                p.move(MoverType.SELF, new Vec3(dx, dy, dz));
                s.k = ++pas / (float) TICKS_ACCROCHE;
                if (pas >= TICKS_ACCROCHE) {
                    if (p.distanceToSqr(cx, cy, cz) > 0.0625) {
                        liberer(p, "accroche gênée");
                        return true;
                    }
                    p.setPos(cx, cy, cz);
                    phase = Phase.MARCHE;
                }
            }
            case MARCHE -> marcher(p, s);
            case VIRAGE -> {
                double[] c = CHEMIN[pas++];
                p.setPos(c[0], c[1], c[2]);
                s.lacetCible = lacetDepart + lacetTour * pas / nbPas;
                if (pas >= nbPas) finirVirage(p, s);
            }
            case SORTIE -> {
                if (sortie == Sortie.VOULUE) {
                    // vers la paroi, la boîte couchée buterait contre le mur : la place debout a été lue libre, on y glisse
                    if (++pas >= TICKS_SORTIE) p.setPos(cx, cy, cz);
                    else p.setPos(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                    s.k = 1f - pas / (float) TICKS_SORTIE;
                    if (pas >= TICKS_SORTIE) finir(p);
                } else {
                    // sommet ou pied : le corps se redresse le long d'un chemin lu libre, et se tourne vers son arrivée
                    // (s.debout, lu par le rendu : yBodyRot est réécrit à la fin du tick par LocalPlayerBodyMixin)
                    double[] c = CHEMIN[pas++];
                    p.setPos(c[0], c[1], c[2]);
                    s.k = 1f - pas / (float) nbPas;
                    if (pas >= nbPas) finir(p);
                }
            }
            default -> { }
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.setOnGround(false);
        p.resetFallDistance();
        return true;
    }

    private static void marcher(LocalPlayer p, Etat s) {
        Minecraft mc = Minecraft.getInstance();
        MurFace f = s.face;
        int sens = f.sens();
        // P tenu à 0,9 de la paroi : un écart infime est rattrapé, un grand (correction du serveur ailleurs) fait lâcher
        double ecart = (f.surNormale(p.getX(), p.getZ()) - s.plan) * sens - DISTANCE;
        if (Math.abs(ecart) > EPS) {
            if (Math.abs(ecart) > TOLERANCE) {
                liberer(p, "écarté de la paroi");
                return;
            }
            double n = s.plan + sens * DISTANCE;
            p.setPos(f.nx != 0 ? n : p.getX(), p.getY(), f.nx != 0 ? p.getZ() : n);
        }
        // la direction voulue sur la paroi : touches continues, tirées de la caméra (MurEntrees)
        if (!MurEntrees.lire(mc, f)) {
            limite(p, s, null, 0);   // plus de poussée : un virage pas encore parti est annulé
            return;
        }
        double allure = MurEntrees.course ? PAS_COURSE : PAS_MARCHE;
        double mu = MurEntrees.u * allure, mv = MurEntrees.v * allure;
        // le devant du corps se tourne vers le sens de marche
        s.cap = tourner(s.cap, (float) Math.atan2(-mu, mv), 0.4f);

        Level level = p.level();
        // le pied du mur : à moins d'un bloc du sol en descendant, on se redresse au sol, dos au mur
        if (mv < 0 && !level.noCollision(p, p.getBoundingBox().expandTowards(0, -1.0, 0))) {
            limite(p, s, null, 0);
            sortirParLeBas(p);
            return;
        }
        // les bords : il faut du mur plein sous tout le corps (au plus quatre lectures de bloc par tick)
        double u = f.surParoi(p.getX(), p.getZ()), y = p.getY();
        double vouluU = mu * f.sensTangente();
        double pu = borner(level, f, vouluU, u - DEMI, u + DEMI, y, y + HAUTEUR, false);
        double pv = borner(level, f, mv, y, y + HAUTEUR, u + pu - DEMI, u + pu + DEMI, true);
        if (pu != 0 || pv != 0) p.move(MoverType.SELF, f.nx != 0 ? new Vec3(0, pv, pu) : new Vec3(pu, pv, 0));
        // la limite poussée : la fin du mur (rognée par borner) ou un obstacle (arrêt par les collisions du jeu)
        Limite lim = null;
        int sensLimite = 0;
        if (Math.abs(MurEntrees.u) >= POUSSEE_MIN) {
            double fait = f.surParoi(p.getX(), p.getZ()) - u;
            if (Math.abs(pu) < Math.abs(vouluU) - EPS) lim = Limite.BORD;
            else if (Math.abs(fait) < Math.abs(pu) - 1.0e-4) lim = Limite.OBSTACLE;
            sensLimite = vouluU > 0 ? 1 : -1;
        }
        if (lim == null && MurEntrees.v >= POUSSEE_MIN && pv < mv - EPS) {
            lim = Limite.HAUT;
            sensLimite = 1;
        }
        limite(p, s, lim, sensLimite);
    }

    /**
     * Borne un pas {@code a} le long d'un axe de la paroi : le bord meneur du corps ({@code min} à {@code max} sur cet axe,
     * {@code oMin} à {@code oMax} sur l'autre) n'entre dans une nouvelle rangée de blocs que si elle est pleine ; sinon il
     * s'arrête pile à sa limite.
     */
    private static double borner(Level level, MurFace f, double a, double min, double max, double oMin, double oMax, boolean vertical) {
        if (a == 0) return 0;
        boolean plus = a > 0;
        double bord = plus ? max : min;
        int ici = Mth.floor(plus ? bord - EPS : bord + EPS), la = Mth.floor(plus ? bord + a - EPS : bord + a + EPS);
        if (la == ici) return a;
        int o0 = Mth.floor(oMin + EPS), o1 = Mth.floor(oMax - EPS);
        for (int o = o0; o <= o1; o++) {
            if (!plein(level, f, couche, vertical ? o : la, vertical ? la : o)) return (plus ? la : ici) - bord;
        }
        return a;
    }

    /** Le mur est-il plein sous tout le corps ({@code u} : centre sur l'horizontale de la paroi, {@code y} : bas) ? */
    private static boolean paroiPleine(Level level, MurFace f, int bloc, double u, double y) {
        for (int cu = Mth.floor(u - DEMI + EPS), u1 = Mth.floor(u + DEMI - EPS); cu <= u1; cu++) {
            for (int cy = Mth.floor(y + EPS), y1 = Mth.floor(y + HAUTEUR - EPS); cy <= y1; cy++) {
                if (!plein(level, f, bloc, cu, cy)) return false;
            }
        }
        return true;
    }

    /** Bloc de la couche du mur dont la face tournée vers {@code n} est pleine (forme de collision, règle du serveur). */
    private static boolean plein(Level level, MurFace f, int bloc, int cu, int cy) {
        return MurTransitions.facePleine(boites(level, f.nx != 0 ? bloc : cu, cy, f.nx != 0 ? cu : bloc), f);
    }

    /** Boîtes de collision d'un bloc dans son repère : rien n'est créé pour un bloc plein ou vide. */
    private static Collection<double[]> boites(Level level, int x, int y, int z) {
        if (level == null) return List.of();
        POS.set(x, y, z);
        BlockState etat = level.getBlockState(POS);
        if (etat.isCollisionShapeFullBlock(level, POS)) return BLOC_PLEIN;
        VoxelShape forme = etat.getCollisionShape(level, POS);
        if (forme.isEmpty()) return List.of();
        List<double[]> l = new ArrayList<>(4);
        forme.forAllBoxes((x0, y0, z0, x1, y1, z1) -> l.add(new double[] {x0, y0, z0, x1, y1, z1}));
        return l;
    }

    /** Tourne un angle vers un autre par le plus court chemin (radians). */
    private static float tourner(float de, float vers, float part) {
        float d = (vers - de) % TOUR;
        if (d > DEMI_TOUR) d -= TOUR;
        else if (d < -DEMI_TOUR) d += TOUR;
        return de + d * part;
    }

    /** Écart d'angle ramené entre −180 et 180 (degrés). */
    private static float court(float d) {
        d %= 360f;
        if (d > 180f) d -= 360f;
        else if (d < -180f) d += 360f;
        return d;
    }

    /* ================================================================ passages : coins et sommet */

    /**
     * Un tick de marche contre une limite ({@code lim}, null : aucune poussée). Une autre limite, ou plus de poussée,
     * remet le compte à zéro et annule un changement de face pas encore joué. Contre le haut : au tick
     * {@value #TICKS_LIMITE}, la sortie par le sommet si elle est possible. Contre un bord ou un obstacle : au premier
     * tick, le coin est jugé et la face demandée ; le virage part quand la poussée a tenu et que l'accord est arrivé.
     */
    private static void limite(LocalPlayer p, Etat s, Limite lim, int sens) {
        if (lim != limite || sens != limiteSens) {
            annulerDemande();
            limite = lim;
            limiteSens = sens;
            pousseeLimite = 0;
            limiteEchec = false;
        }
        if (lim == null) return;
        pousseeLimite++;
        if (lim == Limite.HAUT) {
            if (pousseeLimite == TICKS_LIMITE && !limiteEchec && !sommet(p, s)) limiteEchec = true;
            return;
        }
        if (faceDemandee == null) {
            if (limiteEchec) return;
            if (!MurCanal.disponible() || !preparerCoin(p, s, lim, sens)) {
                limiteEchec = true;
                return;
            }
            faceDemandee = faceVirage;
            numeroFace = ++numero;
            accord = false;
            attenteFace = 0;
            MurCanal.face(faceDemandee, numeroFace);
            return;
        }
        if (accord && pousseeLimite >= TICKS_LIMITE) {
            // le joueur a pu glisser le long du bord depuis la demande : le virage est relu à l'instant du départ
            if (preparerCoin(p, s, lim, sens) && faceVirage == faceDemandee) virer(p, s);
            else {
                annulerDemande();
                limiteEchec = true;
            }
            return;
        }
        if (++attenteFace > TICKS_REPONSE) {
            // sans accord après une seconde : abandon, on reste au bord (le serveur revient à l'ancienne face)
            annulerDemande();
            limiteEchec = true;
        }
    }

    /** Annule un changement de face demandé et pas encore joué : l'ancienne face est redemandée. */
    private static void annulerDemande() {
        if (faceDemandee == null) return;
        faceDemandee = null;
        accord = false;
        if (moi != null) MurCanal.face(moi.face, ++numero);
    }

    /** Fin de la marche ou nouvelle accroche : plus de limite, plus de demande en cours (rien n'est envoyé). */
    private static void oublierLimite() {
        limite = null;
        limiteSens = 0;
        pousseeLimite = 0;
        limiteEchec = false;
        faceDemandee = null;
        accord = false;
    }

    /**
     * Prépare le coin de cette limite depuis la position actuelle : face d'arrivée ({@link #faceVirage}), paroi, couche
     * et chemin. Coin sortant contre un bord, rentrant contre un obstacle. Faux si le passage n'est pas possible : on
     * reste arrêté au bord, comme en C0.
     */
    private static boolean preparerCoin(LocalPlayer p, Etat s, Limite lim, int sens) {
        MurFace a = s.face;
        // d : le sens de marche, sur l'axe horizontal de la paroi
        MurFace d = a.nx != 0 ? MurFace.deNormale(0, sens) : MurFace.deNormale(sens, 0);
        if (d == null) return false;
        Level level = p.level();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        double[] q = new double[2];
        MurFace b;
        if (lim == Limite.BORD) {
            // coin sortant : la face B est celle du bloc d'angle tournée vers le sens de marche (règle du serveur)
            b = d;
            MurTransitions.Arete arete = MurTransitions.coinSortant(LECTEUR, x, y, z, a, b);
            if (arete == null) return false;
            double[] depart = MurTransitions.departSortant(arete, a, b);
            if (Math.abs(depart[0] - x) > DEPART_ECART || Math.abs(depart[1] - z) > DEPART_ECART) return false;
            nbPas = MurTransitions.ticksSortant(MurEntrees.course ? PAS_COURSE : PAS_MARCHE);
            for (int i = 0; i < nbPas; i++) {
                MurTransitions.pointSortant(arete, a, b, (i + 1) / (double) nbPas, q);
                CHEMIN[i][0] = q[0];
                CHEMIN[i][1] = y;
                CHEMIN[i][2] = q[1];
            }
            // le cube libre aux quarts de l'arc
            for (int i = 1; i <= 3; i++) {
                MurTransitions.pointSortant(arete, a, b, i / 4.0, q);
                if (!cubeLibre(level, p, q[0], y, q[1])) return false;
            }
        } else {
            // coin rentrant : la face B du mur qui barre la marche regarde vers nous
            b = MurFace.deNormale(-d.nx, -d.nz);
            double planB = MurTransitions.surfaceDerriere(LECTEUR, x, y + MurTransitions.HAUTEUR_CUBE, z, b, DEMI + TOLERANCE);
            if (Double.isNaN(planB)) return false;
            MurTransitions.departRentrant(s.plan, a, planB, b, q);
            if (Math.abs(q[0] - x) > DEPART_ECART || Math.abs(q[1] - z) > DEPART_ECART) return false;
            MurTransitions.arriveeRentrant(s.plan, a, planB, b, q);
            nbPas = MurTransitions.RENTRANT;
            for (int i = 0; i < nbPas; i++) {
                double t = (i + 1) / (double) nbPas;
                CHEMIN[i][0] = x + (q[0] - x) * t;
                CHEMIN[i][1] = y;
                CHEMIN[i][2] = z + (q[1] - z) * t;
            }
            double[] milieu = CHEMIN[nbPas / 2 - 1];
            if (!cubeLibre(level, p, milieu[0], y, milieu[2])) return false;
        }
        // l'arrivée : la boîte couchée libre sur B, et du mur plein de B sous tout le corps
        double[] fin = CHEMIN[nbPas - 1];
        double planB = b.surNormale(fin[0], fin[2]) - b.sens() * DISTANCE;
        int coucheB = (int) Math.round(b.sens() > 0 ? planB - 1 : planB);
        if (!cubeLibre(level, p, fin[0], y, fin[2])) return false;
        if (!level.noCollision(p, couchee(b, fin[0], y, fin[2], ARRIERE).deflate(1.0e-4))) return false;
        if (!paroiPleine(level, b, coucheB, b.surParoi(fin[0], fin[2]), y)) return false;
        faceVirage = b;
        planVirage = planB;
        coucheVirage = coucheB;
        return true;
    }

    /** Le cube du serveur (pose de nage, 0,6 de côté) tient-il en ce point ? */
    private static boolean cubeLibre(Level level, LocalPlayer p, double x, double y, double z) {
        return level.noCollision(p, p.getDimensions(Pose.SWIMMING).makeBoundingBox(x, y, z).deflate(1.0e-4));
    }

    /**
     * Le virage part : la face tenue devient celle d'arrivée, la boîte devient le cube, la caméra change de face (son
     * lissage fait tourner la vue avec le corps), le corps rendu tourne autour de l'arête. Le cap est conservé : la
     * direction voulue {@code (u, v)} se lit pareil sur la nouvelle face.
     */
    private static void virer(LocalPlayer p, Etat s) {
        MurFace b = faceVirage;
        lacetDepart = s.lacetCible;
        lacetTour = court(b.lacet - s.face.lacet);
        s.face = b;
        s.cube = true;
        faceDemandee = null;
        accord = false;
        limite = null;
        limiteSens = 0;
        pousseeLimite = 0;
        phase = Phase.VIRAGE;
        pas = 0;
        MurCamera.changerFace(b);
        p.setPos(p.getX(), p.getY(), p.getZ());   // la boîte est refaite : le cube
    }

    /** Fin du virage : la paroi d'arrivée est tenue, la boîte couchée posée sur elle, la marche reprend. */
    private static void finirVirage(LocalPlayer p, Etat s) {
        plan = planVirage;
        couche = coucheVirage;
        s.plan = planVirage;
        s.cube = false;
        s.lacetCible = lacetDepart + lacetTour;
        phase = Phase.MARCHE;
        depuisControle = 0;
        p.setPos(p.getX(), p.getY(), p.getZ());   // la boîte est refaite : couchée sur la nouvelle face
    }

    /**
     * Sortie par le sommet (du mur au toit) : montée rognée par la fin du mur, rien au-dessus, un corps debout tient sur
     * tout le chemin et le toit le porte à l'arrivée. On monte de 0,6 puis on avance de 1,2 jusqu'à 0,3 derrière
     * l'arête, debout face au toit. Le {@code lache} part tout de suite (le serveur rend pose et gravité, et sa boîte
     * debout passe sur le même chemin) ; la caméra défait son roulis pendant que le corps se redresse.
     *
     * @return faux si la sortie n'est pas possible (auvent, toit trop bas) : on reste au bord
     */
    private static boolean sommet(LocalPlayer p, Etat s) {
        MurFace f = s.face;
        Level level = p.level();
        EntityDimensions debout = p.getDimensions(Pose.STANDING);
        double haut = p.getY() + HAUTEUR;
        double[] q = new double[2];
        int n = MurTransitions.SOMMET_MONTEE + MurTransitions.SOMMET_AVANCE;
        for (int i = 1; i <= n; i++) {
            MurTransitions.pasSommet(i, DISTANCE, haut, q);
            double c = s.plan + f.sens() * q[0];
            double x = f.nx != 0 ? c : p.getX(), z = f.nx != 0 ? p.getZ() : c;
            CHEMIN[i - 1][0] = x;
            CHEMIN[i - 1][1] = q[1];
            CHEMIN[i - 1][2] = z;
            if (i >= MurTransitions.SOMMET_MONTEE && !level.noCollision(p, debout.makeBoundingBox(x, q[1], z).deflate(1.0e-4))) return false;
        }
        double[] fin = CHEMIN[n - 1];
        if (level.noCollision(p, debout.makeBoundingBox(fin[0], fin[1] - 0.1, fin[2]).deflate(1.0e-4))) return false;
        nbPas = n;
        pas = 0;
        phase = Phase.SORTIE;
        sortie = Sortie.SOMMET;
        corpsVise = f.lacet;
        s.debout = corpsVise;
        s.cube = true;
        oublierLimite();
        MurCanal.lache(nbCorrections);
        lacheEnvoye = true;
        numeroLache = numero;
        MurCamera.lacher(Minecraft.getInstance());
        p.setPos(p.getX(), p.getY(), p.getZ());
        return true;
    }

    /* ================================================================ sorties */

    /**
     * Sortie voulue (Espace, touche de course de chakra, mur disparu) : trois ticks vers une place où un corps debout
     * tient, d'abord contre le mur 0,6 plus bas (l'inverse de l'accroche), puis sur place ; sans place, on lâche là où
     * l'on est et le jeu choisit la pose.
     */
    private static void sortir(LocalPlayer p) {
        MurFace f = moi.face;
        Level level = p.level();
        EntityDimensions debout = p.getDimensions(Pose.STANDING);
        cx = p.getX();
        cy = p.getY();
        cz = p.getZ();
        boolean trouve = false;
        for (int essai = 0; essai < 2 && !trouve; essai++) {
            double x = p.getX() - (essai == 0 ? f.nx * (DISTANCE - CONTACT) : 0), z = p.getZ() - (essai == 0 ? f.nz * (DISTANCE - CONTACT) : 0);
            for (double d : DESCENTES) {
                if (!level.noCollision(p, debout.makeBoundingBox(x, p.getY() + d, z).deflate(1.0e-4))) continue;
                cx = x;
                cy = p.getY() + d;
                cz = z;
                trouve = true;
                break;
            }
        }
        dx = (cx - p.getX()) / TICKS_SORTIE;
        dy = (cy - p.getY()) / TICKS_SORTIE;
        dz = (cz - p.getZ()) / TICKS_SORTIE;
        oublierLimite();
        phase = Phase.SORTIE;
        sortie = Sortie.VOULUE;
        pas = 0;
    }

    /**
     * Pied du mur (du mur au sol) : {@code P} garde sa distance de 0,9 au mur et descend sur le sol trouvé sous lui, le
     * corps se redresse en 4 à 6 ticks et finit dos au mur ; {@code lache} à la fin, comme en C0 (le serveur voit encore
     * le mur derrière). Sans sol où un corps debout tient : la sortie voulue.
     */
    private static void sortirParLeBas(LocalPlayer p) {
        MurFace f = moi.face;
        Level level = p.level();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        double sol = Double.NEGATIVE_INFINITY;
        AABB dessous = new AABB(x - DEMI, y - PIED_PROFONDEUR, z - DEMI, x + DEMI, y, z + DEMI);
        for (VoxelShape forme : level.getBlockCollisions(p, dessous)) {
            double h = forme.max(Direction.Axis.Y);
            if (h <= y + EPS && h > sol) sol = h;
        }
        if (sol == Double.NEGATIVE_INFINITY
            || !level.noCollision(p, p.getDimensions(Pose.STANDING).makeBoundingBox(x, sol, z).deflate(1.0e-4))) {
            sortir(p);
            return;
        }
        nbPas = Mth.clamp(4 + (int) Math.round(2.0 * (y - sol)), 4, 6);
        for (int i = 0; i < nbPas; i++) {
            CHEMIN[i][0] = x;
            CHEMIN[i][1] = y + (sol - y) * (i + 1) / nbPas;
            CHEMIN[i][2] = z;
        }
        oublierLimite();
        pas = 0;
        phase = Phase.SORTIE;
        sortie = Sortie.PIED;
        corpsVise = f.lacet + 180f;
        moi.debout = corpsVise;
        moi.cube = true;
        p.setPos(x, y, z);
    }

    /** Fin d'une sortie (voulue, sommet ou pied) : le corps est debout, la physique du jeu reprend. */
    private static void finir(LocalPlayer p) {
        Etat s = moi;
        // sommet ou pied : le corps du jeu part de la pose de la dernière image animée, puis rejoint le regard comme au sol
        if (sortie != Sortie.VOULUE) {
            p.yBodyRot = corpsVise;
            p.yBodyRotO = corpsVise;
        }
        terminer(sortie == Sortie.SOMMET ? "sommet" : sortie == Sortie.PIED ? "pied du mur" : "lâché");
        ETATS.remove(s.id);
        actif = !ETATS.isEmpty();
        p.refreshDimensions();
    }

    /**
     * Lâcher immédiat (le serveur lâche, permis retiré, mort, eau, autre déplacement…) : {@code P} ne bouge pas, la boîte
     * redevient celle du jeu tout de suite, le corps rendu se redresse en trois ticks.
     */
    private static void liberer(LocalPlayer p, String raison) {
        Etat s = moi;
        terminer(raison);
        if (s == null) return;
        if (p == null || s.entite != p) ETATS.remove(s.id);
        else {
            s.local = false;
            s.couche = false;
            s.monte = false;
            s.cube = false;
            s.debout = Float.NaN;
            // le lacet rendu se cumule d'un coin à l'autre : la face est rejointe par le plus court chemin, sans tour parasite
            s.lacetCible = s.lacet + court(s.face.lacet - s.lacet);
        }
        actif = !ETATS.isEmpty();
        if (p != null) p.refreshDimensions();
    }

    /**
     * Fin d'une marche, quelle qu'en soit la raison : le serveur reçoit le compte des corrections (sauf si le
     * {@code lache} est déjà parti, au sommet), le marcheur le lit.
     */
    private static void terminer(String raison) {
        if (!lacheEnvoye) MurCanal.lache(nbCorrections);
        numeroLache = numero;
        lacheEnvoye = false;
        String bilan = String.format(java.util.Locale.ROOT, "Mur : %.1f s, %d correction%s de position (%s)",
            duree / 20.0, nbCorrections, nbCorrections > 1 ? "s" : "", raison);
        LOG.info(bilan);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal(bilan));
        moi = null;
        joueur = null;
        phase = Phase.REPOS;
        sortie = Sortie.VOULUE;
        corpsPris = false;
        relacheExigee = true;
        poussee = 0;
        oublierLimite();
        MurCamera.lacher(mc);
    }

    /** Déconnexion : tout tombe, rien n'est envoyé. */
    private static void oublier() {
        ETATS.clear();
        actif = false;
        corpsPris = false;
        permis = false;
        phase = Phase.REPOS;
        sortie = Sortie.VOULUE;
        moi = null;
        joueur = null;
        monde = null;
        face = null;
        poussee = 0;
        relacheExigee = false;
        nbCorrections = 0;
        numero = 0;
        numeroLache = 0;
        lacheEnvoye = false;
        oublierLimite();
        MurCamera.oublier(Minecraft.getInstance());
    }

    /* ================================================================ entités */

    /**
     * Une fois par tick, seulement s'il existe un état : rattache chaque état à son entité (il a pu arriver avant elle, la
     * boîte est alors refaite), tient l'avancement de la bascule et le cap des autres joueurs, fait tourner le lacet rendu
     * de la paroi vers sa cible (virage de coin, au plus {@value #LACET_PAS}° par tick), annule la nage du jeu (la pose de
     * nage ne doit ni coucher le corps ni sortir les bras), oublie l'état d'une entité disparue (tout de suite si elle
     * avait été vue, après dix secondes s'il était arrivé avant elle).
     */
    private static void entites(ClientLevel level) {
        ObjectIterator<Etat> it = ETATS.values().iterator();
        while (it.hasNext()) {
            Etat s = it.next();
            s.lacetAvant = s.lacet;
            s.lacet += Mth.clamp(s.lacetCible - s.lacet, -LACET_PAS, LACET_PAS);
            Entity e = level.getEntity(s.id);
            if (e == null) {
                // entité déjà vue puis sortie du suivi : son « - » ne nous parviendrait plus, l'état tombe tout de suite
                // (le serveur le renvoie à qui la revoit au mur) ; le délai ne sert qu'à l'état arrivé avant son entité
                boolean vue = s.entite != null;
                s.entite = null;
                if (!s.local && (vue || ++s.absent > TICKS_ABSENCE)) it.remove();
                continue;
            }
            s.absent = 0;
            if (e != s.entite) {
                s.entite = e;
                s.x = e.getX();
                s.y = e.getY();
                s.z = e.getZ();
                e.refreshDimensions();
            }
            if (e instanceof LivingEntity vivant) {
                LivingEntityMurAccessor nage = (LivingEntityMurAccessor) vivant;
                nage.reborn$nage(0f);
                nage.reborn$nageAvant(0f);
            }
            if (!s.local) {
                s.kAvant = s.k;
                s.k = s.monte ? Math.min(1f, s.k + 1f / TICKS_ACCROCHE) : Math.max(0f, s.k - 1f / TICKS_SORTIE);
                s.capAvant = s.cap;
                // virage d'un voisin fini : la boîte couchée se pose sur sa nouvelle face
                boolean vire = Math.abs(s.lacetCible - s.lacet) > 1.0e-3f;
                if (s.cube && !vire) {
                    s.cube = false;
                    e.refreshDimensions();
                }
                double du = (e.getX() - s.x) * s.face.tx + (e.getZ() - s.z) * s.face.tz, dv = e.getY() - s.y;
                if (s.couche && !vire && du * du + dv * dv > 4.0e-4) s.cap = tourner(s.cap, (float) Math.atan2(-du, dv), 0.4f);
                if (!s.monte && s.k <= 0f) {
                    it.remove();
                    continue;
                }
            }
            s.x = e.getX();
            s.y = e.getY();
            s.z = e.getZ();
        }
        actif = !ETATS.isEmpty();
    }
}
