package fr.reborn.hud.runtime;

import fr.reborn.hud.combat.CombatState;
import fr.reborn.hud.combat.CooldownState;
import fr.reborn.hud.menu.settings.RebornPrefs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Vitals compacts (Game Plan n° 18, validé le 05/11) : quand le panneau vitals montre le <b>compact</b> (tête et deux
 * filets) et quand il montre le <b>bloc complet</b> (le bloc d'avant, inchangé).
 *
 * <ul>
 *   <li><b>Combat</b> (relance 8 s de calme à chaque fois) : coup donné ou reçu d'un joueur ou d'une créature, parade,
 *       technique lancée (chakra dépensé hors déplacement : au moins 3 % ou 300 unités en moins d'1 s ; endurance
 *       dépensée hors sprint, pour le taïjutsu). <b>Reste ouvert</b> tant que l'arme est dégainée, qu'un
 *       duel est en cours ou qu'on est à terre (KO) : 8 s de calme ne commencent qu'après.</li>
 *   <li><b>Hors combat</b>, 2,5 s : un soin, du chakra dépensé en se déplaçant (course, saut de chakra), des dégâts
 *       d'environnement (chute, feu) ; en général un écart d'au moins 3 % en moins d'1 s. La régénération lente, la faim
 *       et le chakra qui remonte n'ouvrent rien, pas plus qu'un maximum qui change (point de stat, grade, personnage).</li>
 *   <li>Morph : 180 ms à l'ouverture (barres d'abord, puis la tête ; nom et chiffres sur les 20 % de fin), 300 ms au
 *       repli (textes d'abord, puis la tête, puis les barres).</li>
 *   <li>« Vitals toujours détaillés » ({@link RebornPrefs#vitalsToujoursDetailles}) et l'éditeur de HUD : bloc complet
 *       tout de suite, sans morph.</li>
 * </ul>
 *
 * <p>Les signaux sont relevés à chaque tick client (rien n'est alloué : anneaux d'entiers de 20 relevés = 1 s) ; le
 * rendu lit l'état et la progression ({@link #phases}). Thread client uniquement.
 */
public final class VitalsMode {

    public static final long ENTREE_MS = 180L, REPLI_MS = 300L, CALME_MS = 8000L, BREF_MS = 2500L;

    public enum Etat { COMPACT, OUVERTURE, COMPLET, REPLI }

    private static Etat etat = Etat.COMPACT;
    /** Début de la transition en cours (horloge {@link #maintenant()}). */
    private static long depuis;
    private static long combatJusqua, brefJusqua;
    /** Hystérésis : vie ≤ 25 % (s'arrête au-dessus de 30 %), satiété ≤ 25 % (idem). */
    private static boolean vieBasse, faimBasse;

    // Écarts : anneaux de 20 relevés (1 s) de la vie et du chakra, en pour mille.
    private static final int N = 20;
    private static final int[] VIE = new int[N], CK = new int[N];
    /** Chakra en unités (seuil absolu : les grandes réserves rendent 3 % trop haut pour une technique E). */
    private static final int[] CKA = new int[N];
    private static int iAnneau, nAnneau, sourcePrec = -1, vieMaxPrec = -1, ckMaxPrec = -1;
    private static int hurtPrec;
    private static long coupPrec, paradePrec, depensePrec;
    /** Chakra dépensé d'un coup (unités) qui vaut une technique, quelle que soit la réserve. */
    private static final int SEUIL_CK_ABSOLU = 300;

    /** Progression du morph (rendu) : barres, tête, textes, de 0 (compact) à 1 (bloc). */
    private static float tb, th, tt;
    /** Point de départ de l'ouverture : 0 depuis le compact, la position courante si elle interrompt un repli. */
    private static float ob, oh, ot;

    // ─── Banc d'essai ───
    /** Horloge figée du banc (0 = horloge réelle). */
    public static long debugMaintenant;
    /** Battement forcé (0 = creux, 1 = crête ; négatif = libre). */
    public static float debugPouls = -1f;
    /** Arme dégainée simulée (le banc n'a pas de serveur pour pousser l'inventaire RP). */
    public static boolean debugArme;
    /** Banc : signaux coupés (les valeurs posées d'un coup ne doivent pas ouvrir le bloc), seule l'hystérésis tourne. */
    public static boolean debugSansSignaux;

    private VitalsMode() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(VitalsMode::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> remettre());
    }

    public static long maintenant() {
        return debugMaintenant != 0L ? debugMaintenant : System.currentTimeMillis();
    }

    /** Retour au compact, sans minuteur (déconnexion, banc). */
    public static void remettre() {
        etat = Etat.COMPACT;
        combatJusqua = brefJusqua = 0L;
        vieBasse = faimBasse = false;
        nAnneau = 0;
        sourcePrec = vieMaxPrec = ckMaxPrec = -1;
        hurtPrec = 0;
        ob = oh = ot = 0f;
    }

    // ─── Signaux ───

    /** Entrée en combat (ou relance des 8 s). */
    public static void combat(long now) {
        combatJusqua = Math.max(combatJusqua, now + CALME_MS);
    }

    /** Changement de valeur hors combat : bloc ouvert 2,5 s. */
    public static void bref(long now) {
        brefJusqua = Math.max(brefJusqua, now + BREF_MS);
    }

    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            remettre();
            return;
        }
        long now = maintenant();
        if (debugSansSignaux) {
            VitalsHud.lire(p);
            hysteresis();
            avancer(mc, now);
            return;
        }

        // Restent ouverts : arme dégainée, duel en cours, à terre.
        // Sans snapshot serveur, get() fabrique un inventaire de démonstration à chaque appel (et jamais dégainé).
        boolean arme = debugArme;
        if (!arme && fr.reborn.hud.menu.inventory.InventoryData.fromServer()) {
            fr.reborn.hud.menu.inventory.InventoryData.Snapshot inv = fr.reborn.hud.menu.inventory.InventoryData.get();
            arme = inv != null && inv.armeDegainee();
        }
        fr.reborn.hud.ko.KoPayload ko = fr.reborn.hud.ko.KoClient.state();
        if (arme || fr.reborn.hud.duel.DuelClient.inDuel() || (ko != null && ko.phase() != 0)) combat(now);

        // Coup donné, parade (canal reborn:combat).
        long c = CombatState.INSTANCE.dernierCoupMs();
        if (c != coupPrec) {
            coupPrec = c;
            if (c != 0L) combat(now);
        }
        long pa = CombatState.INSTANCE.derniereParadeMs();
        if (pa != paradePrec) {
            paradePrec = pa;
            if (pa != 0L) combat(now);
        }
        // Endurance dépensée hors sprint (coup, technique de taïjutsu, qui ne touche pas au chakra).
        long de = CombatState.INSTANCE.derniereDepenseMs();
        if (de != depensePrec) {
            depensePrec = de;
            if (de != 0L) combat(now);
        }

        // Coup reçu (animation de dégâts vanilla) : d'une entité = combat ; sinon (chute, feu) = 2,5 s.
        int ht = p.hurtTime;
        if (ht > hurtPrec) {
            DamageSource s = p.getLastDamageSource();
            if (s != null && s.getEntity() != null) combat(now);
            else bref(now);
        }
        hurtPrec = ht;

        // Écarts d'au moins 3 % en moins d'1 s.
        VitalsHud.lire(p);
        int vie = VitalsHud.vieMille(), ck = VitalsHud.chakraMille(), src = VitalsHud.source();
        int cka = VitalsHud.chakraCur(), vieMax = VitalsHud.vieMax(), ckMax = VitalsHud.chakraMax();
        // Nouvelle source (arrivée sur le serveur) ou maximum changé (point de stat, grade, autre personnage) : le
        // ratio bouge sans rien de dépensé ni soigné, ce n'est pas un écart.
        if (src != sourcePrec || vieMax != vieMaxPrec || ckMax != ckMaxPrec || nAnneau == 0) {
            remplir(vie, ck, cka);
            sourcePrec = src;
            vieMaxPrec = vieMax;
            ckMaxPrec = ckMax;
        }
        int vMin = vie, vMax = vie, cMax = ck, caMax = cka;
        for (int i = 0; i < nAnneau; i++) {
            vMin = Math.min(vMin, VIE[i]);
            vMax = Math.max(vMax, VIE[i]);
            cMax = Math.max(cMax, CK[i]);
            caMax = Math.max(caMax, CKA[i]);
        }
        // Soin : en vanilla (hors serveur) la vie remonte d'un cœur d'un coup (5 %), d'où un seuil plus haut. Sur le
        // serveur, la régénération de satiété rend 0,25 %/s : 3 % en 1 s, c'est un soin.
        int seuilSoin = src == 0 ? 80 : 30;
        boolean ecart = false;
        if (vie - vMin >= seuilSoin) { bref(now); ecart = true; }
        if (vMax - vie >= 30) { bref(now); ecart = true; }
        // Chakra : 3 % de la réserve, ou 300 unités (une technique E en coûte 400) ; hors serveur, pas d'unités.
        if (cMax - ck >= 30 || (src != 0 && caMax - cka >= SEUIL_CK_ABSOLU)) {
            // Chakra dépensé en se déplaçant (course, saut de chakra) : 2,5 s ; sinon une technique : combat.
            boolean deplacement = fr.reborn.hud.animation.NarutoRun.INSTANCE.isActive()
                || CooldownState.INSTANCE.remainingMs(CooldownState.Ability.CHAKRA_JUMP, System.currentTimeMillis())
                    > CooldownState.CHAKRA_JUMP_CD_MS - 1500L;
            if (deplacement) bref(now);
            else combat(now);
            ecart = true;
        }
        if (ecart) remplir(vie, ck, cka);
        else {
            VIE[iAnneau] = vie;
            CK[iAnneau] = ck;
            CKA[iAnneau] = cka;
            iAnneau = (iAnneau + 1) % N;
            nAnneau = Math.min(N, nAnneau + 1);
        }

        hysteresis();
        avancer(mc, now);
    }

    /** Vie basse et faim : apparaissent à 25 %, s'en vont au-dessus de 30 %. */
    private static void hysteresis() {
        int vie = VitalsHud.vieMille();
        if (vie <= 250) vieBasse = true;
        else if (vie > 300) vieBasse = false;
        int st = Math.round(VitalsHud.satieteValeur() * 1000);
        if (st <= 250) faimBasse = true;
        else if (st > 300) faimBasse = false;
    }

    private static void remplir(int vie, int ck, int cka) {
        for (int i = 0; i < N; i++) {
            VIE[i] = vie;
            CK[i] = ck;
            CKA[i] = cka;
        }
        nAnneau = N;
        iAnneau = 0;
    }

    // ─── État ───

    /** Bloc complet tout de suite, sans morph : réglage « toujours détaillés », éditeur de HUD. */
    private static boolean immediat(Minecraft mc) {
        return RebornPrefs.INSTANCE.vitalsToujoursDetailles || mc.gui.screen() instanceof fr.reborn.hud.ui.HudEditScreen;
    }

    /** Fait avancer la machine (appelé au tick et au rendu, idempotent). */
    public static void avancer(Minecraft mc, long now) {
        boolean imm = immediat(mc);
        boolean ouvert = imm || now < combatJusqua || now < brefJusqua;
        switch (etat) {
            case COMPACT -> {
                if (ouvert) {
                    etat = imm ? Etat.COMPLET : Etat.OUVERTURE;
                    depuis = now;
                    ob = oh = ot = 0f;
                }
            }
            case OUVERTURE -> {
                if (imm || now - depuis >= ENTREE_MS) etat = Etat.COMPLET;
            }
            case COMPLET -> {
                if (!ouvert) {
                    etat = Etat.REPLI;
                    depuis = now;
                }
            }
            case REPLI -> {
                if (imm) etat = Etat.COMPLET;
                else if (ouvert) {
                    // Repli interrompu : on rouvre depuis là où en sont barres, tête et textes (rien ne saute).
                    phases(now);
                    ob = tb;
                    oh = th;
                    ot = tt;
                    etat = Etat.OUVERTURE;
                    depuis = now;
                } else if (now - depuis >= REPLI_MS) etat = Etat.COMPACT;
            }
        }
    }

    public static Etat etat() { return etat; }

    /** Vie ≤ 25 % (hystérésis jusqu'à 30 %). */
    public static boolean vieBasse() { return vieBasse; }

    /** Satiété ≤ 25 % (hystérésis jusqu'à 30 %) : le 3e filet du compact. */
    public static boolean faimBasse() { return faimBasse; }

    /** Battement de la vie basse (0 = creux, 1 = crête ; sinus de 1,2 s). */
    public static float pouls(long now) {
        if (debugPouls >= 0f) return debugPouls;
        return 0.5f - 0.5f * (float) Math.cos((now % 1200L) / 1200f * 2.0 * Math.PI);
    }

    /** Calcule la progression du morph pour {@code now} (lue ensuite par {@link #barres}, {@link #tete}, {@link #textes}). */
    public static void phases(long now) {
        switch (etat) {
            case COMPACT -> { tb = 0f; th = 0f; tt = 0f; }
            case COMPLET -> { tb = 1f; th = 1f; tt = 1f; }
            case OUVERTURE -> {
                float t = Math.min(1f, (now - depuis) / (float) ENTREE_MS);
                tb = ob + (1f - ob) * eo(t / 0.75f);
                th = oh + (1f - oh) * ei(t);
                tt = ot + (1f - ot) * clamp((t - 0.8f) / 0.2f);
            }
            case REPLI -> {
                float u = Math.min(1f, (now - depuis) / (float) REPLI_MS);
                tb = 1f - ei((u - 0.3f) / 0.7f);
                th = 1f - eo((u - 0.15f) / 0.5f);
                tt = clamp(1f - u / 0.2f);
            }
        }
    }

    public static float barres() { return tb; }
    public static float tete() { return th; }
    public static float textes() { return tt; }

    /** Banc : fige l'état et le temps écoulé dans la transition. */
    public static void debugFiger(Etat e, long ecoule) {
        etat = e;
        ob = oh = ot = 0f;
        depuis = maintenant() - ecoule;
    }

    private static float clamp(float x) { return Math.max(0f, Math.min(1f, x)); }

    /** Ease-out (1 − (1 − x)^2,2) et ease-in (x^1,6), ceux de la maquette. */
    static float eo(float x) { return 1f - (float) Math.pow(1f - clamp(x), 2.2); }

    static float ei(float x) { return (float) Math.pow(clamp(x), 1.6); }
}
