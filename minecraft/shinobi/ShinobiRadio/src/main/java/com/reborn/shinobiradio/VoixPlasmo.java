package com.reborn.shinobiradio;

import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.VoixService;
import com.reborn.shinobicore.inventory.Notifs;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.mute.PlayerVoiceMutedEvent;
import su.plo.voice.api.server.event.mute.PlayerVoiceUnmutedEvent;
import su.plo.voice.api.server.mute.MuteDurationUnit;
import su.plo.voice.api.server.mute.ServerMuteInfo;

import java.util.UUID;

/**
 * <b>Couper / rétablir la voix</b> (outil staff du 31/10) : implémentation de {@link VoixService} sur le mute de Plasmo
 * Voice ({@code MuteManager}), publiée dans le {@code ServicesManager} par {@link ShinobiRadio} ; le Poste de garde de
 * ShinobiCore la lit en différé.
 *
 * <ul>
 *   <li><b>Couper</b> : {@code mute(cible, staff, minutes, MINUTE, motif, silent = true)} — toute la voix est jetée
 *       (proximité et radio), la coupure est rangée par Plasmo ({@code voice_mutes.json}) et tombe seule à l'échéance
 *       (vérifiée toutes les 5 s). Plasmo exige la cible <b>en ligne</b> ({@code IllegalArgumentException} sinon :
 *       refus). Refus aussi si la voix est <b>déjà coupée</b> : Plasmo écraserait la coupure en cours sans rien dire
 *       (un {@code /vmute} sans fin raccourci à 15 min).</li>
 *   <li><b>Rétablir</b> : {@code unmute(cible, silent = true)}, hors ligne compris.</li>
 *   <li><b>Journal</b> : le Poste de garde écrit {@code STAFF VOIX_COUPEE / VOIX_RETABLIE} pour ce qu'il lance ; ici, on
 *       écrit les {@code /vmute} / {@code /vunmute} tapés dans Plasmo et les fins automatiques, <b>sans doublon</b> :
 *       Plasmo déclenche ses événements dans l'appel même ({@code fire} synchrone), on marque donc la cible du thread
 *       courant le temps de l'appel. Les coupures de la <b>douane</b> (commandes console sur un candidat) vont au journal
 *       SYSTEME, pas STAFF. Jamais de contenu vocal.</li>
 * </ul>
 * Les événements de Plasmo arrivent hors du thread principal (fin automatique : exécuteur de Plasmo) : journal et
 * notification repassent par le thread principal.
 */
final class VoixPlasmo implements VoixService, ShinobiRadio.Coupures {

    /** Cible d'une coupure / levée lancée par ce service, sur le thread de l'appel : ses événements sont déjà journalisés. */
    private static final ThreadLocal<UUID> EN_COURS = new ThreadLocal<>();

    private final ShinobiRadio plugin;
    private volatile PlasmoVoiceServer voix;
    private volatile RadioAddon addon;

    VoixPlasmo(ShinobiRadio plugin) {
        this.plugin = plugin;
    }

    /** Plasmo Voice prêt (addon initialisé, à chaque rechargement de Plasmo) : on écoute ses mutes. */
    @Override
    public void brancher(PlasmoVoiceServer v, RadioAddon a) {
        debrancher();
        voix = v;
        addon = a;
        v.getEventBus().register(a, this);
    }

    @Override
    public void debrancher() {
        PlasmoVoiceServer v = voix;
        RadioAddon a = addon;
        voix = null;
        if (v != null && a != null) {
            try {
                v.getEventBus().unregister(a, this);
            } catch (RuntimeException ignored) { }
        }
    }

    /* ================================================================== VoixService (thread principal) */

    @Override
    public boolean disponible() { return voix != null; }

    @Override
    public long couper(UUID cible, UUID staff, int minutes, String motif) {
        PlasmoVoiceServer v = voix;
        if (v == null || cible == null || minutes <= 0) return -1;
        // MuteManager.mute écrase une coupure en cours sans la lire (getMute n'est pas appelé, Map.put au stockage) : un
        // /vmute sans fin ou la coupure de la douane seraient raccourcis en silence. Jamais par ici : refus.
        if (etat(cible) != null) return -1;
        EN_COURS.set(cible);
        try {
            return v.getMuteManager().mute(cible, staff, minutes, MuteDurationUnit.MINUTE, motif, true)
                    .map(ServerMuteInfo::getMutedToTime).orElse(-1L);
        } catch (IllegalArgumentException e) {   // « Player not found » : Plasmo ne coupe qu'un joueur en ligne
            return -1;
        } finally {
            EN_COURS.remove();
        }
    }

    @Override
    public boolean retablir(UUID cible) {
        PlasmoVoiceServer v = voix;
        if (v == null || cible == null) return false;
        EN_COURS.set(cible);
        try {
            return v.getMuteManager().unmute(cible, true).isPresent();
        } catch (RuntimeException e) {
            return false;
        } finally {
            EN_COURS.remove();
        }
    }

    @Override
    public Coupure etat(UUID cible) {
        PlasmoVoiceServer v = voix;
        if (v == null || cible == null) return null;
        ServerMuteInfo m;
        try {
            m = v.getMuteManager().getMute(cible).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
        if (m == null) return null;
        long fin = m.getMutedToTime();   // 0 = permanente chez Plasmo
        if (fin > 0 && fin <= System.currentTimeMillis()) return null;   // échue : Plasmo la lève dans les 5 s
        return new Coupure(fin > 0 ? fin : PERMANENTE, m.getReason() == null ? "" : m.getReason(), m.getMutedByPlayerUUID());
    }

    /* ================================================================== événements de Plasmo (tous threads) */

    @EventSubscribe
    public void onMuted(PlayerVoiceMutedEvent event) {
        ServerMuteInfo m = event.getMuteInfo();
        if (m == null || m.getPlayerUUID() == null || m.getPlayerUUID().equals(EN_COURS.get())) return;
        principal(() -> {
            JournalService j = journal();
            if (j == null) return;
            boolean douane = parLaDouane(m);
            JournalService.Entree e = j.entree(douane ? Categorie.SYSTEME : Categorie.STAFF, "VOIX_COUPEE");
            if (m.getMutedByPlayerUUID() != null) e.acteur(m.getMutedByPlayerUUID());
            else e.acteur(Bukkit.getConsoleSender());
            e.cible(m.getPlayerUUID())
                    .detail(duree(m) + " — " + (douane ? "douane (commande console)" : motif(m) + " · /vmute (Plasmo)")).ecrire();
        });
    }

    @EventSubscribe
    public void onUnmuted(PlayerVoiceUnmutedEvent event) {
        ServerMuteInfo m = event.getMuteInfo();
        if (m == null || m.getPlayerUUID() == null || m.getPlayerUUID().equals(EN_COURS.get())) return;
        boolean echue = m.getMutedToTime() > 0 && m.getMutedToTime() <= System.currentTimeMillis();
        boolean silencieuse = Boolean.TRUE.equals(m.getSilent());
        principal(() -> {
            JournalService j = journal();
            if (j != null) {
                boolean douane = !echue && parLaDouane(m);
                j.entree(douane ? Categorie.SYSTEME : Categorie.STAFF, "VOIX_RETABLIE").acteur(Bukkit.getConsoleSender())
                        .cible(m.getPlayerUUID())
                        .detail(echue ? "fin automatique (" + duree(m) + " — " + motif(m) + ")"
                                : douane ? "douane (commande console)" : "/vunmute (Plasmo)").ecrire();
            }
            // Coupure silencieuse (celles du Poste de garde) : Plasmo ne dit rien à l'échéance, on prévient le joueur.
            if (echue && silencieuse) {
                Player p = Bukkit.getPlayer(m.getPlayerUUID());
                if (p != null) dire(p, "info", "Voix rétablie", "fin de la coupure");
            }
        });
    }

    /* ================================================================== utilitaires */

    private void principal(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, r);
    }

    private static JournalService journal() {
        return Bukkit.getServicesManager().load(JournalService.class);
    }

    /**
     * Coupure posée (ou levée) par la <b>douane</b> : par la console ({@code douane.voix.couper / rendre}, le muteur de
     * Plasmo est alors {@code null}) sur un candidat ou un joueur qu'elle tient ({@code DouaneManager.restreint}, vrai
     * pendant ses propres appels, transitions comprises). Rangée au journal SYSTEME, pas STAFF : ce n'est pas une décision
     * du staff. Lu sur le thread principal (les commandes de la douane y déclenchent l'événement dans l'appel même).
     */
    private static boolean parLaDouane(ServerMuteInfo m) {
        if (m.getMutedByPlayerUUID() != null) return false;   // posée par un staff en jeu : sa décision
        try {
            com.reborn.shinobicore.ShinobiCore core = com.reborn.shinobicore.ShinobiCore.get();
            com.reborn.shinobicore.douane.DouaneManager d = core != null ? core.douane() : null;
            return d != null && d.restreint(m.getPlayerUUID());
        } catch (LinkageError | RuntimeException e) {   // ShinobiCore sans douane : rien à distinguer
            return false;
        }
    }

    /** « 15 min », « 2 h », « sans fin » (durée posée, pas le temps restant). */
    private static String duree(ServerMuteInfo m) {
        long to = m.getMutedToTime();
        if (to <= 0) return "sans fin";
        long min = Math.max(1, Math.round((to - m.getMutedAtTime()) / 60_000.0));
        return min < 120 ? min + " min" : (min / 60) + " h";
    }

    private static String motif(ServerMuteInfo m) {
        String r = m.getReason();
        return r == null || r.isBlank() ? "sans motif" : r.trim();
    }

    /** Une ligne dans la pile de notifications du mod (aucun kanji), sinon au tchat. */
    private static void dire(Player p, String ton, String gras, String texte) {
        if (Notifs.aLeMod(p)) {
            Notifs.msg(p, ton, null, gras, texte);
        } else {
            p.sendMessage((ton.equals("refus") ? ChatColor.RED : ChatColor.GOLD) + gras
                    + (texte == null || texte.isBlank() ? "" : ChatColor.GRAY + " · " + texte));
        }
    }
}
