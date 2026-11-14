package fr.reborn.hud.combat;

import fr.reborn.hud.combat.SautChargeLoi.Regles;
import fr.reborn.hud.etat.EtatClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * <b>Saut chargé</b> (lot 5, étape A, 09/11) : Maj + Espace tenues au sol chargent un bond, en 1,2 s. Le serveur décide
 * (ShinobiCombat {@code SautCharge}) : le mod annonce le début ({@code reborn:combatin} kind 8), la relâche avec la visée
 * (kind 9) ou l'annulation (kind 10) ; le serveur calcule la charge avec ses propres heures et répond {@code GO} (vitesse,
 * charge, recharge) ou {@code NON} (raison). <b>Rien ne part sans réponse</b> : plus de recharge fantôme.
 *
 * <p>À {@code GO}, le mod joue le vol (mixin {@code SautVolMixin} sur {@code LivingEntity.travel}) : frottement horizontal
 * × 0,98, gravité vanilla, <b>aucune poussée en l'air</b>, de sorte que l'arrivée est celle de l'aperçu. Un mur coupe la vitesse
 * sur son axe (on glisse, on tombe à son pied, comme l'aperçu). Une autre force (coup, technique, double saut du serveur),
 * l'eau ou une échelle rendent la main au jeu.
 *
 * <p>Gardes du mod (le serveur revérifie tout) : garde, état du serveur ({@code mob}, {@code sau}), eau, recharge ; quitter le
 * sol annule ; la charge ferme la concentration ({@code ModeConcentration.commun}). Pendant la charge, le corps suit la caméra
 * ({@code KeyboardInputMixin}) : le regard connu du serveur reste dans le cône de 25°.
 *
 * <p>Affichage : trajectoire en poussière violette jusqu'à l'arrivée prédite et anneau de poussière ({@link SautApercu},
 * chemin et arrivée calculés par {@link SautArc}), barre de charge près du réticule et textes ({@link SautHud}). Serveur
 * trop ancien (aucun réglage reçu après 5 s de jeu) : l'ancien saut
 * (kind 5, 35 s), sans aperçu. Un {@code GO} arrivé hors de sa fenêtre n'est pas joué : le mod le dit au serveur
 * ({@code KIND_SAUT_GO_IGNORE}), qui rembourse, et ne pose aucune recharge.
 */
public final class ChakraJump {

    public static final ChakraJump INSTANCE = new ChakraJump();

    public enum Phase { REPOS, CHARGE, ATTENTE, VOL }

    /** Horloge de la charge et des retours (banc : figeable). */
    public static LongSupplier horloge = Util::getMillis;
    /** Banc : Maj + Espace tenues (null = vraies touches). */
    public static BooleanSupplier debugTouches;
    /** Banc : serveur simulé (null = réseau). */
    public static Consumer<CombatInputPayload> debugServeur;

    private static final long ATTENTE_MAX_MS = 1500L, GO_TARDIF_MS = 3000L, VOL_MAX_MS = 15_000L;
    private static final long ANCIEN_CD_MS = 35_000L;
    /** Sans réglages du serveur, l'ancien saut n'est tenté qu'après 5 s de jeu (un serveur du lot 5 a parlé avant). */
    private static final int ATTENTE_REGLES_TICKS = 100;
    /** Écart toléré entre la vitesse laissée au tick d'avant et celle trouvée (frottement des entités, arrondis). */
    private static final double ECART_EXTERNE = 0.05;

    private Phase phase = Phase.REPOS;
    private Regles regles = Regles.DEFAUT;
    private boolean serveurConnu;
    private long debutMs, relacheMs, goMs = -1, refusMs = -1, finVolMs = -1, volDebutMs;
    private double cRelache, cGo;
    private byte drapeauxGo;
    private double porteeGo;
    private String refusTexte = "";
    private boolean touchesAvant;
    /** Après un refus, une nouvelle charge attend que Maj + Espace soient relâchées (pas de relance à chaque tick). */
    private boolean attendreRelache;
    private LocalPlayer joueurVol;
    private int volTicks;
    private double attX, attY, attZ;
    private boolean attenduPose;
    private String finRaison = "";

    /* aperçu d'arrivée : recalculé tous les 2 ticks au plus, seulement si charge, visée ou position bougent */
    private final SautArc arc = new SautArc();
    private final double[] vApercu = new double[3];
    private double cApercu = -1, lacetApercu, tangageApercu, xApercu, yApercu, zApercu;
    private long tickApercu = Long.MIN_VALUE / 4, tickCompte;
    private boolean bridePrevu;
    private double porteeBornePrevue;

    private ChakraJump() {}

    /* ================================================================ lecture (HUD, aperçu, autres modules) */

    public Phase phase() { return phase; }
    public boolean isCharging() { return phase == Phase.CHARGE; }
    public boolean enVol() { return phase == Phase.VOL; }
    public Regles regles() { return regles; }
    public boolean serveurConnu() { return serveurConnu; }
    public SautArc arc() { return arc; }
    public double charge() { return phase == Phase.CHARGE ? SautChargeLoi.charge(horloge.getAsLong() - debutMs, regles) : cRelache; }
    public double chargeRelachee() { return cRelache; }
    public double chargeGo() { return cGo; }
    public long goMs() { return goMs; }
    public long refusMs() { return refusMs; }
    public String refusTexte() { return refusTexte; }
    public long finVolMs() { return finVolMs; }
    public String finRaison() { return finRaison; }
    public int volTicks() { return volTicks; }
    public boolean bridePrevu() { return phase == Phase.CHARGE ? bridePrevu : (drapeauxGo & 1) != 0; }
    public boolean brideChakra() { return (drapeauxGo & 2) != 0; }
    public double porteeBorne() { return phase == Phase.CHARGE ? porteeBornePrevue : porteeGo; }

    /** Puissance affichée (0 à 1), pour l'ancien code. */
    public float power() { return phase == Phase.CHARGE ? (float) charge() : 0f; }

    /** Chakra dépensé il y a moins de 1,5 s par un saut (les vitals l'ouvrent comme un déplacement, pas un combat). */
    public boolean depenseRecente(long nowReel) {
        return phase == Phase.VOL || (goMs >= 0 && horloge.getAsLong() - goMs < 1500L);
    }

    /* ================================================================ touches et gardes */

    private static boolean touches(Minecraft mc) {
        if (debugTouches != null) return debugTouches.getAsBoolean();
        return mc.options != null && mc.options.keyShift.isDown() && mc.options.keyJump.isDown();
    }

    /**
     * Raison pour laquelle une charge ne peut pas commencer : null = possible ; chaîne vide = refus muet (en vol, monture,
     * spectateur) ; sinon le texte montré sous le filet.
     */
    private String refusLocal(Minecraft mc, LocalPlayer p) {
        if (p.isSpectator() || p.getAbilities().flying || p.isPassenger() || p.isFallFlying()) return "";
        if (CombatInput.INSTANCE.isBlocking()) return "En garde";
        var ko = fr.reborn.hud.ko.KoClient.state();
        if (ko != null && (ko.phase() == 1 || ko.phase() == 2)) return "À terre";
        if (EtatClient.enJeu() && (EtatClient.refuse("mob") || EtatClient.refuse("sau"))) {
            String r = EtatClient.raison(EtatClient.refuse("mob") ? "mob" : "sau");
            return r != null && !r.isBlank() ? r : "Mouvement impossible";
        }
        if (p.isInWater() || p.isInLava()
                || (mc.level != null && fr.reborn.hud.eau.MarcheEauClient.surEau(mc.level, p))) return "Pas sur l'eau";
        long reste = CooldownState.INSTANCE.remainingMs(CooldownState.Ability.CHAKRA_JUMP, System.currentTimeMillis());
        if (reste > 0) return "Saut chargé dans " + Math.max(1, (reste + 999) / 1000) + " s";
        return null;
    }

    /** Vrai si le saut vanilla doit être neutralisé : charge, attente, vol, ou geste de charge qui va démarrer. */
    public boolean suppressesJump() {
        if (phase != Phase.REPOS) return true;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return p != null && mc.options != null && mc.gui.screen() == null && touches(mc) && p.onGround()
            && refusLocal(mc, p) == null;
    }

    /* ================================================================ tick */

    /** À chaque tick client (piloté par {@code HudKeybinds}). */
    public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        tickCompte++;
        if (p == null || mc.level == null) { reset(); return; }
        long now = horloge.getAsLong();
        boolean t = touches(mc);
        if (!t) attendreRelache = false;
        switch (phase) {
            case VOL -> {
                if (p != joueurVol || now - volDebutMs > VOL_MAX_MS) finVol("délai");
            }
            case ATTENTE -> {
                if (now - relacheMs > ATTENTE_MAX_MS) {
                    phase = Phase.REPOS;
                    refus("Pas de réponse du serveur", now);
                }
            }
            case CHARGE -> {
                String r = refusLocal(mc, p);
                if (!t) relacher(p, now);
                else if (mc.gui.screen() != null || !p.onGround()) annuler();
                else if (r != null) {
                    annuler();
                    attendreRelache = true;
                    if (!r.isEmpty()) refus(r, now);
                } else apercu(mc, p, now, false);
            }
            case REPOS -> {
                if (t && mc.gui.screen() == null && p.onGround() && !attendreRelache) {
                    String r = refusLocal(mc, p);
                    if (r == null) demarrer(mc, p, now);
                    else if (!touchesAvant && !r.isEmpty()) refus(r, now);
                }
            }
        }
        touchesAvant = t;
    }

    private void demarrer(Minecraft mc, LocalPlayer p, long now) {
        phase = Phase.CHARGE;
        debutMs = now;
        cApercu = -1;
        refusMs = -1;
        goMs = -1;
        drapeauxGo = 0;
        if (serveurConnu) envoyer(CombatInputPayload.of(CombatInputPayload.KIND_SAUT_DEBUT));
        apercu(mc, p, now, true);
    }

    private void annuler() {
        if (phase == Phase.CHARGE && serveurConnu) envoyer(CombatInputPayload.of(CombatInputPayload.KIND_SAUT_ANNULE));
        phase = Phase.REPOS;
    }

    private void relacher(LocalPlayer p, long now) {
        double c = SautChargeLoi.charge(now - debutMs, regles);
        if (!SautChargeLoi.suffisante(c, regles)) { annuler(); return; }
        cRelache = c;
        relacheMs = now;
        double[] a = angles(p);
        if (serveurConnu) {
            envoyer(CombatInputPayload.sautRelache((float) a[0], (float) a[1]));
            phase = Phase.ATTENTE;
            return;
        }
        // Réglages pas encore reçus (le serveur du lot 5 les envoie 2 s après la connexion) : on attend, sans recharge.
        // Le serveur du lot 5 refuse l'ancien saut à ce mod (canal reborn:dojutsu écouté) : rien ne doit partir.
        if (p.tickCount < ATTENTE_REGLES_TICKS) {
            phase = Phase.REPOS;
            attendreRelache = true;
            refus("Un instant…", now);
            return;
        }
        // serveur d'avant le lot 5 : l'ancien saut (vitesse calculée ici, bornée là-bas), 35 s de recharge locale
        Vec3 aim = MobilityInput.aimDir(p);
        double ancienne = 2.4 + Math.min(1.0, (now - debutMs) / 900.0) * 2.2;
        envoyer(CombatInputPayload.chakraJump((float) (aim.x * ancienne), (float) (aim.y * ancienne + 0.5), (float) (aim.z * ancienne)));
        CooldownState.INSTANCE.trigger(CooldownState.Ability.CHAKRA_JUMP, ANCIEN_CD_MS);
        goMs = now;
        phase = Phase.REPOS;
    }

    private static void envoyer(CombatInputPayload pl) {
        if (debugServeur != null) { debugServeur.accept(pl); return; }
        if (ClientPlayNetworking.canSend(CombatInputPayload.ID)) ClientPlayNetworking.send(pl);
    }

    private void refus(String texte, long now) {
        refusTexte = texte;
        refusMs = now;
    }

    /** Lacet et tangage de la visée (caméra Reborn en épaule, sinon regard du joueur). */
    static double[] angles(LocalPlayer p) {
        Vec3 d = MobilityInput.aimDir(p);
        double tangage = Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, d.y))));
        double lacet = Math.toDegrees(Math.atan2(-d.x, d.z));
        return new double[] {lacet, tangage};
    }

    /* ================================================================ aperçu */

    private void apercu(Minecraft mc, LocalPlayer p, long now, boolean force) {
        double c = SautChargeLoi.charge(now - debutMs, regles);
        double[] a = angles(p);
        double x = p.getX(), y = p.getY(), z = p.getZ();
        boolean bouge = Math.abs(c - cApercu) > 0.004 || Math.abs(a[0] - lacetApercu) > 0.3 || Math.abs(a[1] - tangageApercu) > 0.3
            || Math.abs(x - xApercu) + Math.abs(y - yApercu) + Math.abs(z - zApercu) > 0.05;
        if (!force && (!bouge || tickCompte - tickApercu < 2)) return;
        cApercu = c;
        lacetApercu = a[0];
        tangageApercu = a[1];
        xApercu = x;
        yApercu = y;
        zApercu = z;
        tickApercu = tickCompte;
        double[] v = SautChargeLoi.vitesseDepart(Math.max(c, regles.seuil()), a[0], a[1], regles);
        porteeBornePrevue = SautChargeLoi.porteeVue(mc.options.getEffectiveRenderDistance(), regles);
        bridePrevu = SautChargeLoi.bornerArrivee(v, porteeBornePrevue, regles, SautArc.relief(mc.level, x, y, z));
        System.arraycopy(v, 0, vApercu, 0, 3);
        arc.calculer(mc.level, p, x, y, z, v, regles);
    }

    /** Banc : force le recalcul de l'aperçu maintenant. */
    public void debugApercu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && phase == Phase.CHARGE) apercu(mc, mc.player, horloge.getAsLong(), true);
    }

    /** Vitesse de l'aperçu (charge en cours) ou du départ. */
    public double[] vitesseApercu() { return vApercu; }

    /* ================================================================ réponses du serveur */

    /** {@code reborn:combat} type 7 (GO / NON) ou 8 (réglages). Corps brut, octets du {@code DataOutputStream} du plugin. */
    public void recevoir(byte type, byte[] corps) {
        if (corps == null) return;
        ByteBuffer b = ByteBuffer.wrap(corps);
        try {
            if (type == CombatPayload.TYPE_SAUT_REGLES) lireRegles(b);
            else if (type == CombatPayload.TYPE_SAUT) lireReponse(b);
        } catch (RuntimeException e) {
            // corps tronqué : ignoré
        }
    }

    private void lireRegles(ByteBuffer b) {
        int version = b.get();
        if (version < 1) return;
        regles = new Regles(b.getInt(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(),
            b.getFloat(), b.getFloat(), b.getFloat(), b.getInt(), b.getInt(), b.getFloat(), b.getFloat());
        serveurConnu = true;
    }

    private void lireReponse(ByteBuffer b) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long now = horloge.getAsLong();
        byte statut = b.get();
        if (statut == 1) {
            double vx = b.getFloat(), vy = b.getFloat(), vz = b.getFloat();
            double c = b.getFloat();
            int recharge = b.getInt();
            byte dr = b.get();
            double borne = b.remaining() >= 4 ? b.getFloat() : regles.porteeMax();
            boolean attendu = phase == Phase.ATTENTE || (phase == Phase.REPOS && now - relacheMs < GO_TARDIF_MS);
            if (p == null || !attendu) {
                // GO trop tardif (ou pendant une nouvelle charge) : non joué, le serveur rembourse et lève la recharge
                envoyer(CombatInputPayload.of(CombatInputPayload.KIND_SAUT_GO_IGNORE));
                return;
            }
            CooldownState.INSTANCE.trigger(CooldownState.Ability.CHAKRA_JUMP, recharge);
            cGo = c;
            drapeauxGo = dr;
            porteeGo = borne;
            goMs = now;
            refusMs = -1;
            // l'arrivée tenue jusqu'à la réception : la vraie vitesse, depuis la vraie position
            vApercu[0] = vx;
            vApercu[1] = vy;
            vApercu[2] = vz;
            arc.calculer(mc.level, p, p.getX(), p.getY(), p.getZ(), vApercu, regles);
            p.setDeltaMovement(vx, vy, vz);
            attX = vx;
            attY = vy;
            attZ = vz;
            attenduPose = true;
            joueurVol = p;
            volTicks = 0;
            volDebutMs = now;
            finRaison = "";
            phase = Phase.VOL;
        } else {
            byte raison = b.get();
            int reste = b.remaining() >= 4 ? b.getInt() : 0;
            if (raison == 2 && reste > 0) CooldownState.INSTANCE.trigger(CooldownState.Ability.CHAKRA_JUMP, reste);
            if (phase == Phase.CHARGE || phase == Phase.ATTENTE) phase = Phase.REPOS;
            attendreRelache = true;
            refus(texteRaison(raison, reste), now);
        }
    }

    /** Raisons du serveur ({@code CombatChannel.SAUT_R_*}). */
    public static String texteRaison(byte raison, int resteMs) {
        return switch (raison) {
            case 1 -> "Mouvement impossible";
            case 2 -> "Saut chargé dans " + Math.max(1, (resteMs + 999) / 1000) + " s";
            case 3 -> "Pas assez de chakra";
            case 4 -> "Il faut être au sol";
            case 5 -> "Charge trop courte";
            case 6 -> "Pas sur l'eau";
            case 7 -> "Trop chargé";
            case 8 -> "À terre";
            case 9 -> "Aucun personnage";
            case 11 -> "En garde";
            default -> "Saut refusé";
        };
    }

    /* ================================================================ vol */

    /**
     * Un tick de vol, appelé par {@code SautVolMixin} à la place de {@code LivingEntity.travel} pour le joueur local.
     *
     * @return vrai si le tick a été joué ici (le jeu ne fait rien de plus)
     */
    public boolean volEtape(LocalPlayer p) {
        if (phase != Phase.VOL || p != joueurVol) return false;
        if (p.isInWater() || p.isInLava() || p.isFallFlying() || p.getAbilities().flying || p.isPassenger() || p.onClimbable()) {
            finVol("eau ou échelle");
            return false;
        }
        if (volTicks > 0 && p.hurtTime > 0) { finVol("coup reçu"); return false; }
        Vec3 v = p.getDeltaMovement();
        if (attenduPose && volTicks > 0) {
            double dx = v.x - attX, dy = v.y - attY, dz = v.z - attZ;
            if (dx * dx + dy * dy + dz * dz > ECART_EXTERNE * ECART_EXTERNE) { finVol("autre force"); return false; }
            v = new Vec3(attX, attY, attZ);   // les arrondis du jeu (vitesse < 0,003 mise à zéro) n'entrent pas dans le vol
        }
        p.move(MoverType.SELF, v);
        Vec3 apres = p.getDeltaMovement();   // composantes heurtées déjà mises à zéro par le jeu
        volTicks++;
        if (p.onGround() && v.y <= 0) {
            p.setDeltaMovement(0, apres.y, 0);   // réception nette : on ne glisse pas hors de l'arrivée prévue
            finVol("réception");
            return true;
        }
        double[] w = {apres.x, apres.y, apres.z};
        SautChargeLoi.avancerVitesse(w, regles);
        p.setDeltaMovement(w[0], w[1], w[2]);
        attX = w[0];
        attY = w[1];
        attZ = w[2];
        return true;
    }

    private void finVol(String raison) {
        phase = Phase.REPOS;
        attenduPose = false;
        joueurVol = null;
        finVolMs = horloge.getAsLong();
        finRaison = raison;
    }

    /** Déconnexion, mort : tout tombe (aucun envoi). */
    public void reset() {
        phase = Phase.REPOS;
        attenduPose = false;
        joueurVol = null;
        refusMs = -1;
        goMs = -1;
    }

    /** Déconnexion : les réglages du serveur seront renvoyés à la prochaine connexion. */
    public void oublierServeur() {
        reset();
        serveurConnu = false;
        regles = Regles.DEFAUT;
    }

    /** Banc : réglages imposés et serveur connu (serveur simulé). */
    public void debugServeurConnu(Regles r) {
        regles = r;
        serveurConnu = true;
    }
}
