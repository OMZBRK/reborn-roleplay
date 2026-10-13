package fr.reborn.hud.menu.fiche;

import fr.reborn.hud.menu.stats.StatDef;
import fr.reborn.hud.menu.stats.StatsData;
import fr.reborn.hud.menu.stats.StatsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Arrays;

/**
 * Répartition des points, sans rendu — copie à l'identique de la logique de {@code StatsScreen} (pending, optimistic,
 * seenVersion, resync, remaining, previewStats, step, validate, respecUsable, respec armé 3 s ; mêmes messages
 * {@code alloc:taijutsu=1,controle=2} / {@code respec} et même garde {@code canSend}). L'écran traduit le résultat en
 * sons et animations. {@code earned} n'est jamais exposé : il trahirait le niveau ; seul {@link #remaining()} s'affiche.
 *
 * <p>Les points en attente survivent à la fermeture pendant la session (rognés au resync).
 */
final class Repartition {

    enum Resultat { MONTE, BAISSE, REFUS, VALIDE, REINIT, ARME }

    private static final int N = StatDef.values().length;
    /** Partagés entre deux ouvertures de la fiche (session). */
    private static final int[] PENDING = new int[N];
    private static final int[] OPTIMISTIC = new int[N];
    private static int seenVersion = Integer.MIN_VALUE;
    private static long respecArmedAt = 0L;

    private StatsData.Snapshot snap;

    Repartition() {
        resync();
    }

    static void clearSession() {
        Arrays.fill(PENDING, 0);
        Arrays.fill(OPTIMISTIC, 0);
        seenVersion = Integer.MIN_VALUE;
    }

    StatsData.Snapshot snap() { return snap; }

    /** Rogne l'attente qui ne tient plus ; remet l'optimisme à zéro à chaque nouvelle version du serveur. */
    void resync() {
        int v = StatsData.fromServer() ? StatsData.version() : -1;
        boolean fresh = seenVersion != v;
        this.snap = FicheData.snapshot();
        seenVersion = v;
        if (fresh) Arrays.fill(OPTIMISTIC, 0);
        int budget = Math.max(0, snap.unspent());
        for (StatDef d : StatDef.values()) {
            int room = snap.max() - snap.get(d);
            PENDING[d.ordinal()] = Math.max(0, Math.min(PENDING[d.ordinal()], room));
        }
        while (pendingTotal() > budget) {
            for (int i = PENDING.length - 1; i >= 0; i--) {
                if (PENDING[i] > 0) { PENDING[i]--; break; }
            }
        }
    }

    int pending(int i) { return PENDING[i]; }

    int pendingTotal() {
        int t = 0;
        for (int p : PENDING) t += p;
        return t;
    }

    int remaining() { return Math.max(0, snap.unspent()) - pendingTotal(); }

    /** Valeur validée (serveur + validation en vol), sans l'attente. */
    int valide(int i) { return Math.min(snap.max(), snap.stats()[i] + OPTIMISTIC[i]); }

    /** Valeur visée (validée + attente). */
    int vise(int i) { return Math.min(snap.max(), valide(i) + PENDING[i]); }

    int[] previewStats() {
        int[] out = snap.stats().clone();
        for (int i = 0; i < out.length; i++) out[i] += PENDING[i];
        return out;
    }

    /** +1 / −1 (Maj : tout ce qui est possible). */
    Resultat step(int i, int dir, boolean all) {
        int before = PENDING[i];
        if (dir > 0) {
            int room = Math.min(remaining(), snap.max() - snap.stats()[i] - PENDING[i]);
            PENDING[i] += all ? Math.max(0, room) : Math.min(1, Math.max(0, room));
        } else {
            PENDING[i] = all ? 0 : Math.max(0, PENDING[i] - 1);
        }
        if (PENDING[i] > before) return Resultat.MONTE;
        if (PENDING[i] < before) return Resultat.BAISSE;
        return Resultat.REFUS;
    }

    Resultat annuler() {
        if (pendingTotal() <= 0) return Resultat.REFUS;
        Arrays.fill(PENDING, 0);
        return Resultat.BAISSE;
    }

    Resultat validate() {
        if (pendingTotal() <= 0) return Resultat.REFUS;
        StringBuilder sb = new StringBuilder("alloc:");
        boolean first = true;
        for (StatDef d : StatDef.values()) {
            int p = PENDING[d.ordinal()];
            if (p <= 0) continue;
            if (!first) sb.append(',');
            sb.append(d.key()).append('=').append(p);
            first = false;
            OPTIMISTIC[d.ordinal()] += p;   // la lanterne reste en haut sans attendre le serveur
        }
        if (ClientPlayNetworking.canSend(StatsPayload.ID)) {
            ClientPlayNetworking.send(new StatsPayload(sb.toString()));
        } else {
            Arrays.fill(OPTIMISTIC, 0);      // hors ligne : rien à valider pour de vrai
        }
        Arrays.fill(PENDING, 0);
        return Resultat.VALIDE;
    }

    boolean canRespec() { return snap.canRespec(); }

    /** Quelque chose à reprendre, et gratuit ou un jeton en poche. */
    boolean respecUsable() {
        return snap.spent() > 0 && (snap.respecFree() || snap.respecTokens() > 0);
    }

    boolean respecArme() {
        return respecUsable() && System.currentTimeMillis() - respecArmedAt < 3000L;
    }

    /** Premier clic : arme 3 s ; second clic : envoie « respec ». */
    Resultat respec() {
        if (!respecUsable()) return Resultat.REFUS;
        long now = System.currentTimeMillis();
        if (now - respecArmedAt > 3000L) {
            respecArmedAt = now;
            return Resultat.ARME;
        }
        respecArmedAt = 0L;
        Arrays.fill(PENDING, 0);
        if (ClientPlayNetworking.canSend(StatsPayload.ID)) ClientPlayNetworking.send(new StatsPayload("respec"));
        return Resultat.REINIT;
    }
}
