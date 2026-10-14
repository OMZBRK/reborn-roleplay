package fr.reborn.hud.allure;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.ByteBuffer;

/**
 * Les <b>trois allures</b> côté client (RedLine <i>Player movements</i>, décision du 2026-10-14) :
 * <ul>
 *   <li><b>Marche</b> : l'allure par défaut, lente et discrète (RP) ;</li>
 *   <li><b>Trot</b> : touche de sprint Minecraft <b>maintenue</b> (Ctrl gauche par défaut, rebindable dans
 *       Commandes) — c'est le sprint vanilla, gratuit ;</li>
 *   <li><b>Sprint</b> : <b>double appui</b> sur la même touche (le second maintenu) — plus rapide, payé en endurance
 *       (~5 s), puis récupération ; vidé = essoufflé jusqu'à 30 %.</li>
 * </ul>
 *
 * <p><b>Prédiction</b> : la vitesse est le modificateur d'attribut {@code reborn:allure} que ShinobiAbilities pose
 * côté serveur ; on pose ici le même, à chaque tick, avant le déplacement (START_CLIENT_TICK), avec un lissage court
 * (pas d'à-coup quand l'allure change, ni quand l'attribut serveur arrive avec la latence). Le serveur accorde ou
 * refuse le sprint ; sa réponse porte le numéro de la demande, pour ne jamais confondre un vieux paquet avec un refus.
 *
 * <p>Inerte hors serveur Reborn (canal absent) : marche et sprint redeviennent ceux de Minecraft.
 */
public final class Allure {

    public static final Allure INSTANCE = new Allure();

    public enum Etat { NEUTRE, MARCHE, TROT, SPRINT }

    /** Même clé que le modificateur serveur ({@code NamespacedKey("reborn", "allure")}). */
    public static final Identifier CLE = Identifier.fromNamespaceAndPath("reborn", "allure");
    private static final double SPRINT_MC = 1.3;
    /** Relâcher la touche ne coupe le trot qu'après ce délai (le temps d'un double appui). */
    private static final int GRACE_RELACHE = 4;
    /** Part du chemin vers la vitesse voulue parcourue à chaque tick (≈ 0,25 s pour changer d'allure). */
    private static final double LISSAGE = 0.3;

    // Réglages poussés par le serveur (défauts = ceux du plugin).
    private boolean serveurActif = true;
    private float fMarche = 0.75f, fTrot = 1.15f, fSprint = 1.55f;

    // Dernier état serveur.
    private boolean srvNeutre, srvEssouffle;
    private float endurance = 1f;

    // État local.
    private Etat etat = Etat.NEUTRE;
    private boolean amorce, sprintVoulu, latch, avaitSprint, sprintEnvoye, arretTrot, pose;
    private int t, dernierAppui = -1000, relache, horsSprint;
    private byte seq;
    private double total = 1.0;
    private long dernierSprintMs;

    // Banc d'essai (REBORN_ALLURE_DEBUG) : pas de plugin en solo, l'endurance est simulée ici.
    boolean debug, debugTenu, debugDoubleAppui;
    private int debugRepos;

    private Allure() {}

    /* ------------------------------------------------------------ lecture */

    public Etat etat() { return etat; }
    public boolean sprinte() { return etat == Etat.SPRINT; }
    public float endurance() { return endurance; }
    public boolean essouffle() { return srvEssouffle; }
    public long dernierSprintMs() { return dernierSprintMs; }

    /** Lu par {@code LocalPlayerAllureMixin} : le trot s'arrête quand la touche est relâchée. */
    public boolean arretTrot() { return arretTrot; }

    private boolean actif(Minecraft mc) {
        if (debug) return true;
        return serveurActif && mc.getConnection() != null && ClientPlayNetworking.canSend(AllurePayload.ID);
    }

    /* -------------------------------------------------------------- tick */

    /** START_CLIENT_TICK : avant les touches et le déplacement du joueur. */
    public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        KeyMapping k = mc.options == null ? null : mc.options.keySprint;
        int appuis = 0;
        if (k != null) while (k.consumeClick()) appuis++;     // personne d'autre ne lit ces clics
        if (p == null || mc.level == null || k == null) { reset(); return; }
        if (!amorce) { appuis = 0; amorce = true; }            // clics accumulés avant l'entrée en jeu
        if (!actif(mc)) {
            if (pose) retirer(p);
            etat = Etat.NEUTRE;
            sprintVoulu = false;
            arretTrot = false;
            total = 1.0;
            return;
        }
        t++;
        if (mc.gui.screen() != null) appuis = 0;
        boolean bascule = Boolean.TRUE.equals(mc.options.toggleSprint().get());
        boolean tenu = k.isDown() || debugTenu;
        int fenetre = Math.max(3, mc.options.sprintWindow().get());

        boolean doubleAppui = debugDoubleAppui;
        debugDoubleAppui = false;
        for (int i = 0; i < appuis; i++) {
            if (t - dernierAppui <= fenetre) doubleAppui = true;
            dernierAppui = t;
        }
        relache = tenu ? 0 : relache + 1;

        boolean mcSprint = p.isSprinting();
        // Sprint lancé sans la touche (double appui sur Avancer, vanilla) : on le garde jusqu'à l'arrêt.
        if (mcSprint && !avaitSprint) latch = !tenu && !sprintVoulu && !doubleAppui;
        if (!mcSprint) latch = false;
        avaitSprint = mcSprint;
        horsSprint = mcSprint ? 0 : horsSprint + 1;

        boolean neutre = srvNeutre || neutreLocal(p);
        boolean interdit = neutre || srvEssouffle
            || fr.reborn.hud.notif.NotifClient.sprintBloque()
            || fr.reborn.hud.combat.CombatInput.INSTANCE.isBlocking()
            || endurance <= 0.02f;
        if (doubleAppui && !interdit) sprintVoulu = true;
        if (interdit || (!bascule && relache > GRACE_RELACHE) || (!mcSprint && !tenu && horsSprint > fenetre)) {
            sprintVoulu = false;
        }

        boolean sprint = sprintVoulu && mcSprint;
        if (sprint != sprintEnvoye) {
            sprintEnvoye = sprint;
            envoyer(sprint);
        }
        etat = neutre ? Etat.NEUTRE : !mcSprint ? Etat.MARCHE : sprint ? Etat.SPRINT : Etat.TROT;
        if (etat == Etat.SPRINT) dernierSprintMs = System.currentTimeMillis();
        arretTrot = !neutre && !latch && !sprintVoulu && relache > GRACE_RELACHE;

        if (etat == Etat.NEUTRE) {
            if (pose) retirer(p);
            total = 1.0;
        } else {
            double cible = switch (etat) {
                case MARCHE -> fMarche;
                case TROT -> fTrot;
                default -> fSprint;
            };
            total += (cible - total) * LISSAGE;
            if (Math.abs(cible - total) < 0.002) total = cible;
            poser(p, total / (mcSprint ? SPRINT_MC : 1.0) - 1.0);
        }
        if (debug) simuler();
    }

    /** Une autre mécanique possède la vitesse : course ninja, véhicule ou porté, nage, vol, planeur, créatif. */
    private static boolean neutreLocal(LocalPlayer p) {
        return fr.reborn.hud.animation.NarutoRun.INSTANCE.isActive()
            || p.isPassenger() || p.isSwimming() || p.isFallFlying()
            || p.getAbilities().flying || p.isCreative() || p.isSpectator();
    }

    /* ---------------------------------------------------------- attribut */

    private void poser(LocalPlayer p, double valeur) {
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        AttributeModifier m = a.getModifier(CLE);
        if (m != null && Math.abs(m.amount() - valeur) < 1e-5
            && m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) return;
        a.addOrUpdateTransientModifier(new AttributeModifier(CLE, valeur, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        pose = true;
    }

    private void retirer(LocalPlayer p) {
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null) a.removeModifier(CLE);
        pose = false;
    }

    /* ------------------------------------------------------------- réseau */

    private void envoyer(boolean sprint) {
        seq++;
        if (debug || !ClientPlayNetworking.canSend(AllurePayload.ID)) return;
        ClientPlayNetworking.send(new AllurePayload(new byte[] {(byte) (sprint ? 2 : 0), seq}));
    }

    /** Paquet S2C (thread client). */
    public void recevoir(byte[] d) {
        if (d == null || d.length == 0) return;
        try {
            ByteBuffer b = ByteBuffer.wrap(d);
            byte type = b.get();
            if (type == 0) {
                serveurActif = b.get() != 0;
                fMarche = clamp(b.getFloat());
                fTrot = clamp(b.getFloat());
                fSprint = clamp(b.getFloat());
            } else if (type == 1) {
                byte accorde = b.get();
                endurance = Math.max(0f, Math.min(1f, b.getFloat()));
                int flags = b.get();
                byte s = b.hasRemaining() ? b.get() : seq;
                srvNeutre = (flags & 1) != 0;
                srvEssouffle = (flags & 2) != 0;
                // Réponse à NOTRE dernière demande : sprint refusé ou coupé (vidé, étourdi, chargé…) → on lâche.
                if (s == seq && sprintEnvoye && accorde != 2) sprintVoulu = false;
            }
        } catch (RuntimeException ignored) {
            // paquet tronqué : on garde l'état courant
        }
    }

    private static float clamp(float f) {
        return Float.isFinite(f) ? Math.max(0.05f, Math.min(5f, f)) : 1f;
    }

    /** Déconnexion / changement de monde. */
    public void reset() {
        etat = Etat.NEUTRE;
        amorce = false;
        sprintVoulu = latch = avaitSprint = sprintEnvoye = arretTrot = pose = false;
        srvNeutre = srvEssouffle = false;
        serveurActif = true;
        endurance = 1f;
        total = 1.0;
        relache = horsSprint = 0;
    }

    /* -------------------------------------------------------------- banc */

    /** Endurance simulée en solo : 20/s en sprint sur 100, 16/s de récupération après 1,2 s, relance à 30 %. */
    private void simuler() {
        if (etat == Etat.SPRINT) {
            endurance = Math.max(0f, endurance - 20f / 100f / 20f);
            debugRepos = 0;
            if (endurance <= 0.02f) {
                srvEssouffle = true;
                sprintVoulu = false;
            }
        } else if (++debugRepos > 24) {
            endurance = Math.min(1f, endurance + 16f / 100f / 20f);
        }
        if (srvEssouffle && endurance >= 0.3f) srvEssouffle = false;
    }
}
