package fr.reborn.hud.mur;

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
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Marche sur les murs, essai technique C0</b> (lot 5, Game Plan n° 17, 23/11), côté client. Approche « B+ » de l'étude :
 * pieds sur la paroi, corps à 90°, et sur tous les clients une boîte de collision couchée qui suit ce corps, alors que le
 * serveur ne tient qu'un cube de 0,6 (pose de nage fixée, gravité nulle). <b>Inerte</b> tant que le serveur n'a pas envoyé
 * de permis ({@code /murtest on} d'un membre du staff, ShinobiAbilities {@code MurTest}) : sans permis, rien n'est lu au
 * clavier, rien n'est envoyé, et les mixins ne font qu'une lecture de booléen.
 *
 * <p><b>Géométrie.</b> {@code P} est la position de l'entité (bas-centre du cube du serveur), tenue à
 * {@value #DISTANCE} bloc de la paroi ; {@code n} la normale de la paroi ({@link MurFace}). Boîte du corps : de
 * {@code P − 0,85 n} à {@code P + 0,9 n}, ± 0,3 sur l'horizontale de la paroi, de {@code P.y} à {@code P.y + 0,6} : elle
 * contient toujours le cube du serveur.
 *
 * <p><b>Ordre</b> (canal {@code reborn:mur}, {@link MurCanal}). Permis en main, le joueur pousse contre un mur plein
 * {@value #TICKS_POUSSEE} ticks : le mod envoie {@code accroche} et <b>attend</b>. À la réception de son propre
 * {@code etat}, il joue la bascule ({@value #TICKS_ACCROCHE} ticks, {@code P} passe de 0,3 à 0,9 de la paroi et monte de
 * 0,6), puis marche. Sans réponse après une seconde : abandon, rien n'a changé. Espace ou la touche de course de chakra :
 * sortie en {@value #TICKS_SORTIE} ticks vers une place où un corps debout tient, puis {@code lache} avec le nombre de
 * corrections de position reçues. Le serveur peut lâcher de lui-même ({@code etat} « - », {@code permis} retiré).
 *
 * <p><b>Ce que la classe tient.</b> L'état {@link Etat} de chaque entité au mur, par identifiant (il peut arriver avant
 * l'entité), lu par les mixins : boîte ({@code EntityMurMixin}), pose de nage tenue et déplacement du joueur local
 * ({@code PlayerMurMixin}), rendu ({@link MurRendu}), cadrage ({@link MurCamera}), corrections
 * ({@code ClientPacketListenerMurMixin}). Tout est remis à zéro à la déconnexion.
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
    /** Attente de la réponse du serveur (1 s), contrôle de la paroi à l'arrêt, oubli d'un état sans entité (10 s). */
    private static final int TICKS_REPONSE = 20, TICKS_CONTROLE = 5, TICKS_ABSENCE = 200;
    /** 3,2 blocs/s en marche, 5,6 avec la touche de sprint. */
    private static final double PAS_MARCHE = 3.2 / 20.0, PAS_COURSE = 5.6 / 20.0;
    /** Écart à la paroi rattrapé sans rien dire ; au-delà (correction du serveur ailleurs), on lâche. */
    private static final double TOLERANCE = 0.05;
    private static final double EPS = 1.0e-7;
    private static final float DEMI_TOUR = (float) Math.PI, TOUR = (float) (Math.PI * 2);

    public enum Phase { REPOS, ATTENTE, BASCULE, MARCHE, SORTIE }

    /** État d'une entité au mur, vu de ce client. */
    public static final class Etat {
        final int id;
        MurFace face;
        /** La boîte couchée est posée (faux pendant le redressement qui suit un lâcher). */
        boolean couche;
        /** Vrai tant que le corps se couche ou reste couché, faux quand il se redresse. */
        boolean monte;
        /** Joueur local : {@code k} et le cap sont pilotés par sa propre marche. */
        boolean local;
        /** Joueur local : coordonnée de la paroi sur l'axe de la normale. */
        double plan;
        /** Avancement de la bascule (0 debout, 1 couché) et cap du corps sur la paroi (radians, 0 = vers le haut). */
        float k, kAvant, cap, capAvant;
        Entity entite;
        int absent;
        double x, y, z;

        Etat(int id) { this.id = id; }
    }

    private static final Int2ObjectOpenHashMap<Etat> ETATS = new Int2ObjectOpenHashMap<>();
    private static final MurFace[] FACES = MurFace.values();
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final double[] DESCENTES = {-ELAN, -0.45, -0.3, -0.15, 0.0};

    /** Au moins une entité a un état : lu par les chemins chauds (boîte, rendu) avant toute autre chose. */
    public static volatile boolean actif;
    /** Le corps du joueur local est au mur (bascule, marche ou sortie) : pose tenue, déplacement remplacé, entrées coupées. */
    public static volatile boolean corpsPris;

    /* ---- joueur local */
    private static boolean permis;
    private static Phase phase = Phase.REPOS;
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
    /** Pas de la bascule ou de la sortie, et leur arrivée. */
    private static double dx, dy, dz, cx, cy, cz;
    /** Base du déplacement, figée tant qu'une touche est tenue : « avant » sur la paroi (tangente, haut) et regard d'alors. */
    private static boolean baseFigee;
    private static double baseU, baseV, baseX, baseY, baseZ;

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
     * l'état est connu, sinon celle du jeu. Le serveur intégré du solo garde ses boîtes (filtre {@code isClientSide}).
     * Chez le joueur local, pendant la bascule, la boîte ne dépasse pas la paroi.
     */
    public static AABB boite(Entity e, Vec3 p, AABB origine) {
        if (!(e instanceof Avatar) || !e.level().isClientSide()) return origine;
        Etat s = ETATS.get(e.getId());
        if (s == null || !s.couche) return origine;
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
                if (id == p.getId()) etatLocal(mc, p, f);
                else etatVoisin(mc.level, id, f);
            }
            case "refus" -> {
                // le mod ne demande jamais de changement de face : un refus au mur dit que le serveur ne l'y tient pas
                if (corpsPris) liberer(p, "refusé par le serveur");
                else renoncer();
            }
            default -> { }
        }
    }

    /** Demande restée sans suite (refus, délai, permis retiré) : rien n'a changé, on attend que le joueur cesse de pousser. */
    private static void renoncer() {
        if (phase != Phase.ATTENTE) return;
        phase = Phase.REPOS;
        relacheExigee = true;
    }

    /** L'état du joueur lui-même : la confirmation de sa demande, ou le serveur qui le lâche. */
    private static void etatLocal(Minecraft mc, LocalPlayer p, MurFace f) {
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
                renoncer();
            }
            return;
        }
        if (corpsPris && moi.face == f) return;   // rappel du serveur
        if (corpsPris) liberer(p, "face imprévue");
        else {
            // état que le mod n'attend pas (réponse arrivée après l'abandon) : le serveur n'en envoie jamais de lui-même
            // au joueur, donc il le tient au mur ; le mod le rend, le serveur retire pose et gravité
            MurCanal.lache(0);
            renoncer();
        }
    }

    /** L'état d'un autre joueur, diffusé par le serveur à ceux qui le voient : sa boîte et son corps en dépendent. */
    private static void etatVoisin(ClientLevel level, int id, MurFace f) {
        Etat s = ETATS.get(id);
        if (f == null) {
            if (s == null) return;
            s.couche = false;
            s.monte = false;
            if (s.k <= 0f) ETATS.remove(id);
        } else {
            if (s == null) {
                s = new Etat(id);
                ETATS.put(id, s);
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
            case BASCULE, MARCHE, SORTIE -> {
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
        MurCanal.accroche(f);
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
        ETATS.put(s.id, s);
        actif = true;
        moi = s;
        joueur = p;
        phase = Phase.BASCULE;
        pas = 0;
        duree = 0;
        nbCorrections = 0;
        depuisControle = 0;
        baseFigee = false;
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
     * ni glisse. Les collisions restent celles du jeu ({@code move}), avec la boîte couchée.
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
            case SORTIE -> {
                // vers la paroi, la boîte couchée buterait contre le mur : la place debout a été lue libre, on y glisse
                if (++pas >= TICKS_SORTIE) p.setPos(cx, cy, cz);
                else p.setPos(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                s.k = 1f - pas / (float) TICKS_SORTIE;
                if (pas >= TICKS_SORTIE) finir(p);
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
        int avant = 0, droite = 0;
        if (mc.gui.screen() == null) {
            avant = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
            droite = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
        }
        if (avant == 0 && droite == 0) {
            baseFigee = false;
            return;
        }
        base(p, f);
        // sur la paroi : « avant » = (baseU, baseV), « droite » = avant × n = (baseV, −baseU)
        double mu = avant * baseU + droite * baseV, mv = avant * baseV - droite * baseU;
        double l = Math.sqrt(mu * mu + mv * mv);
        if (l < 1.0e-6) return;
        double allure = mc.options.keySprint.isDown() ? PAS_COURSE : PAS_MARCHE;
        mu = mu / l * allure;
        mv = mv / l * allure;
        // le devant du corps se tourne vers le sens de marche
        s.cap = tourner(s.cap, (float) Math.atan2(-mu, mv), 0.4f);

        Level level = p.level();
        // le sol : à moins d'un bloc en descendant, on lâche et on se relève
        if (mv < 0 && !level.noCollision(p, p.getBoundingBox().expandTowards(0, -1.0, 0))) {
            sortir(p);
            return;
        }
        // les bords : il faut du mur plein sous tout le corps (au plus quatre lectures de bloc par tick)
        double u = f.surParoi(p.getX(), p.getZ()), y = p.getY();
        double pu = borner(level, f, mu * f.sensTangente(), u - DEMI, u + DEMI, y, y + HAUTEUR, false);
        double pv = borner(level, f, mv, y, y + HAUTEUR, u + pu - DEMI, u + pu + DEMI, true);
        if (pu == 0 && pv == 0) return;
        p.move(MoverType.SELF, f.nx != 0 ? new Vec3(0, pv, pu) : new Vec3(pu, pv, 0));
    }

    /**
     * Base du déplacement sur la paroi (étude §4.3) : {@code w = regard + (regard · n)² × haut de caméra}, projeté sur la
     * paroi. Face au mur, « avant » monte ; le long du mur, « avant » suit le regard. Crans de ± 25° autour de la verticale et
     * de ± 15° autour de l'horizontale. La base reste figée tant qu'une touche est tenue, jusqu'à 60° de rotation du
     * regard : les touches ne s'inversent pas sous les doigts.
     */
    private static void base(LocalPlayer p, MurFace f) {
        fr.reborn.hud.camera.RebornCamera cam = fr.reborn.hud.camera.RebornCamera.INSTANCE;
        double lacet = Math.toRadians(cam.isEnabled() ? cam.camYaw() : p.getYRot());
        double tangage = Math.toRadians(cam.isEnabled() ? cam.camPitch() : p.getXRot());
        double vx = -Math.sin(lacet) * Math.cos(tangage), vy = -Math.sin(tangage), vz = Math.cos(lacet) * Math.cos(tangage);
        if (baseFigee && vx * baseX + vy * baseY + vz * baseZ >= 0.5) return;
        // droite = regard × vertical (horizontale), haut de caméra = droite × regard
        double rx = -Math.cos(lacet), rz = -Math.sin(lacet);
        double hx = -rz * vy, hy = rz * vx - rx * vz, hz = rx * vy;
        double vn = vx * f.nx + vz * f.nz, q = vn * vn;
        double wx = vx + q * hx, wy = vy + q * hy, wz = vz + q * hz;
        double angle = Math.toDegrees(Math.atan2(wx * f.tx + wz * f.tz, wy));   // 0 monte, ± 90 horizontal, 180 descend
        double ouverture = Math.abs(angle);
        if (ouverture <= 25) angle = 0;
        else if (ouverture >= 155) angle = 180;
        else if (Math.abs(ouverture - 90) <= 15) angle = Math.copySign(90, angle);
        baseU = Math.sin(Math.toRadians(angle));
        baseV = Math.cos(Math.toRadians(angle));
        baseX = vx;
        baseY = vy;
        baseZ = vz;
        baseFigee = true;
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

    /** Bloc plein de la couche du mur (forme de collision, pas forme visuelle). */
    private static boolean plein(Level level, MurFace f, int bloc, int cu, int cy) {
        POS.set(f.nx != 0 ? bloc : cu, cy, f.nx != 0 ? cu : bloc);
        return level.getBlockState(POS).isCollisionShapeFullBlock(level, POS);
    }

    /** Tourne un angle vers un autre par le plus court chemin (radians). */
    private static float tourner(float de, float vers, float part) {
        float d = (vers - de) % TOUR;
        if (d > DEMI_TOUR) d -= TOUR;
        else if (d < -DEMI_TOUR) d += TOUR;
        return de + d * part;
    }

    /* ================================================================ sorties */

    /**
     * Sortie voulue (Espace, touche de course de chakra, pied du mur, mur disparu) : trois ticks vers une place où un corps
     * debout tient, d'abord contre le mur 0,6 plus bas (l'inverse de l'accroche), puis sur place ; sans place, on lâche
     * là où l'on est et le jeu choisit la pose.
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
        phase = Phase.SORTIE;
        pas = 0;
    }

    /** Fin de la sortie voulue : le corps est debout, la physique du jeu reprend. */
    private static void finir(LocalPlayer p) {
        Etat s = moi;
        terminer("lâché");
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
        }
        actif = !ETATS.isEmpty();
        if (p != null) p.refreshDimensions();
    }

    /** Fin d'une marche, quelle qu'en soit la raison : le serveur reçoit le compte des corrections, le marcheur le lit. */
    private static void terminer(String raison) {
        MurCanal.lache(nbCorrections);
        String bilan = String.format(java.util.Locale.ROOT, "Mur : %.1f s, %d correction%s de position (%s)",
            duree / 20.0, nbCorrections, nbCorrections > 1 ? "s" : "", raison);
        LOG.info(bilan);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal(bilan));
        moi = null;
        joueur = null;
        phase = Phase.REPOS;
        corpsPris = false;
        relacheExigee = true;
        poussee = 0;
        MurCamera.lacher(mc);
    }

    /** Déconnexion : tout tombe, rien n'est envoyé. */
    private static void oublier() {
        ETATS.clear();
        actif = false;
        corpsPris = false;
        permis = false;
        phase = Phase.REPOS;
        moi = null;
        joueur = null;
        monde = null;
        face = null;
        poussee = 0;
        relacheExigee = false;
        nbCorrections = 0;
        MurCamera.lacher(Minecraft.getInstance());
    }

    /* ================================================================ entités */

    /**
     * Une fois par tick, seulement s'il existe un état : rattache chaque état à son entité (il a pu arriver avant elle, la
     * boîte est alors refaite), tient l'avancement de la bascule et le cap des autres joueurs, annule la nage du jeu (la
     * pose de nage ne doit ni coucher le corps ni sortir les bras), oublie l'état d'une entité disparue (tout de suite si
     * elle avait été vue, après dix secondes s'il était arrivé avant elle).
     */
    private static void entites(ClientLevel level) {
        ObjectIterator<Etat> it = ETATS.values().iterator();
        while (it.hasNext()) {
            Etat s = it.next();
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
                double du = (e.getX() - s.x) * s.face.tx + (e.getZ() - s.z) * s.face.tz, dv = e.getY() - s.y;
                if (s.couche && du * du + dv * dv > 4.0e-4) s.cap = tourner(s.cap, (float) Math.atan2(-du, dv), 0.4f);
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
