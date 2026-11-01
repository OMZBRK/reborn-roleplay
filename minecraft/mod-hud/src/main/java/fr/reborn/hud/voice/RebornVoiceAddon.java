package fr.reborn.hud.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.event.audio.capture.ClientActivationRegisteredEvent;
import su.plo.voice.api.client.event.render.VoiceDistanceRenderEvent;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;

/**
 * Addon client PlasmoVoice de Reborn : déclare Reborn comme addon PV, sert de <b>détection de présence</b> — charger
 * cet addon échoue (NoClassDefFoundError, capturé) si PlasmoVoice n'est pas dans le modpack, ce qui gate
 * l'enregistrement du renderer — et branche la <b>radio</b> ({@link RadioVoix} : son radio sur la ligne « radio »,
 * icône d'activation masquée, touche B).
 *
 * <p><b>Réglages Reborn de Plasmo Voice</b> (31/10, {@link #appliquerReglages}) : icônes au-dessus des têtes masquées
 * (seule la bulle Reborn reste, calée sur la tête), touche « Action » déliée, et cercle de portée aux couleurs du micro
 * ({@link #onDistanceRender}).
 *
 * <p>L'accès au client se fait via l'instance statique {@code ModVoiceClient.INSTANCE} (l'injection
 * {@code @InjectPlasmoVoice} sur un champ Java ne s'est pas révélée fiable côté client).
 */
@Addon(id = "reborn-voice", name = "Reborn Voice", version = "1.2.0",
       authors = {"Reborn"}, scope = AddonLoaderScope.CLIENT)
public final class RebornVoiceAddon implements AddonInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/voix");

    /** Couleur du cercle de portée de Plasmo Voice (serveur : {@code visualizeDistance(int)} ; client : à la connexion). */
    private static final int VERT_PLASMO = 0x00A000;
    /** Raccourci « Action » de Plasmo Voice : clic droit maintenu sur un joueur + molette = son volume, de 0 à 200 %. */
    private static final String TOUCHE_ACTION = "key.plasmovoice.general.action";

    private static RadioVoix radio;
    /** Anti-réentrance : {@code DistanceVisualizer.render} relance l'événement qu'on est en train de traiter. */
    private static boolean recolore;

    @Override
    public void onAddonInitialize() {
        PlasmoVoiceClient client;
        try {
            client = su.plo.voice.client.ModVoiceClient.INSTANCE;
        } catch (Throwable t) {
            return;
        }
        if (client == null || radio != null) return;
        radio = new RadioVoix(client);
        client.getEventBus().register(this, radio);
        client.getEventBus().register(this, this);
        appliquerReglages();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { radio.tick(); } catch (RuntimeException ignored) { }
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (h, mc) -> radio.reinitialiser());
    }

    /** Connexion à un serveur vocal (et chaque {@code /vreload}) : l'activation de proximité arrive, on réapplique. */
    @EventSubscribe
    public void onActivationRegistered(ClientActivationRegisteredEvent event) {
        if (VoiceActivation.PROXIMITY_ID.equals(event.getActivation().getId())) appliquerReglages();
    }

    /**
     * Cercle de portée (montré quand la portée change, et à la connexion) aux couleurs du micro : vert, or, rouge selon
     * le rang du rayon parmi les portées du serveur. {@code setColor} ne sert à rien (Plasmo Voice garde la couleur
     * d'origine, vérifié dans {@code DistanceVisualizeStateExtractor.render}) : on annule l'événement et on redessine le
     * même cercle centré sur soi, à notre couleur. Seul le vert de Plasmo Voice est repris (ses cercles centrés sur le
     * joueur) ; un cercle posé ailleurs ou d'une autre couleur (addon serveur) reste tel quel. C'est une sphère
     * translucide qui teinte toute la vue de l'intérieur : la couleur du micro, assombrie à 60 %, garde l'intensité du
     * vert d'origine (au banc, la couleur pleine délavait tout le ciel).
     */
    @EventSubscribe
    public void onDistanceRender(VoiceDistanceRenderEvent event) {
        if (recolore || event.getColor() != VERT_PLASMO) return;
        int c = fr.reborn.hud.runtime.VitalsHud.couleurPortee(VoiceState.rangDe(event.getRadius()));
        int couleur = Math.round((c >> 16 & 0xFF) * 0.6f) << 16 | Math.round((c >> 8 & 0xFF) * 0.6f) << 8
            | Math.round((c & 0xFF) * 0.6f);
        event.setCancelled(true);
        recolore = true;
        try {
            event.getVisualizer().render(event.getRadius(), couleur, null);
        } catch (RuntimeException ignored) {
        } finally {
            recolore = false;
        }
    }

    /**
     * Réglages Reborn de Plasmo Voice côté client, à l'init et à chaque connexion (le joueur peut les rechanger dans le
     * menu V) ; la config n'est écrite que si quelque chose a changé :
     * <ul>
     *   <li>icônes au-dessus des têtes masquées ({@code [overlay] show_source_icons = 2}, pourcentage de volume compris) :
     *       la seule bulle est celle de Reborn, calée sur la tête comme le nom ;</li>
     *   <li>touche « Action » déliée (clic droit maintenu sur un joueur + molette = volume de 0 à 200 %) : doublon du
     *       volume du menu R, borné à 25-100 %, et le clic droit sert aux armes.</li>
     * </ul>
     */
    static void appliquerReglages() {
        try {
            su.plo.voice.client.ModVoiceClient client = su.plo.voice.client.ModVoiceClient.INSTANCE;
            if (client == null) return;
            su.plo.voice.client.config.VoiceClientConfig cfg = client.getConfig();
            boolean change = false;
            su.plo.config.entry.IntConfigEntry icones = cfg.getOverlay().getShowSourceIcons();
            Integer v = icones.value();
            if (v == null || v != 2) {
                icones.set(Integer.valueOf(2));
                change = true;
                LOG.info("Plasmo Voice : icônes au-dessus des têtes masquées (seule la bulle Reborn reste)");
            }
            var action = client.getHotkeys().getHotkey(TOUCHE_ACTION);
            if (action.isPresent() && !action.get().getKeys().isEmpty()) {
                action.get().setKeys(java.util.Set.of());
                change = true;
                LOG.info("Plasmo Voice : touche « Action » (clic droit + molette, volume 0-200 %) -> aucune");
            }
            if (change) cfg.save(true);
        } catch (Throwable t) {
            LOG.warn("Plasmo Voice : réglages Reborn non appliqués ({})", t.toString());
        }
    }

    /** Banc de la voix : les réglages tels que Plasmo Voice les a (icônes, touche Action, portées, cercle). */
    public static String etatReglages() {
        su.plo.voice.client.ModVoiceClient client = su.plo.voice.client.ModVoiceClient.INSTANCE;
        if (client == null) return "Plasmo Voice pas prêt";
        su.plo.voice.client.config.VoiceClientConfig cfg = client.getConfig();
        String action = client.getHotkeys().getHotkey(TOUCHE_ACTION).map(h -> h.getKeys().toString()).orElse("?");
        String portees = client.getActivationManager().getParentActivation()
            .map(a -> a.getDistances() + " (choisie " + a.getDistance() + ")").orElse("aucune activation");
        return "icones=" + cfg.getOverlay().getShowSourceIcons().value() + " action=" + action + " portees=" + portees
            + " cercle=" + cfg.getAdvanced().getVisualizeVoiceDistance().value();
    }

    /** Banc des touches : raccourcis de Plasmo Voice (nom → touches), hors Commandes de Minecraft. */
    public static java.util.List<String> raccourcis() {
        java.util.List<String> l = new java.util.ArrayList<>();
        PlasmoVoiceClient c = su.plo.voice.client.ModVoiceClient.INSTANCE;
        if (c == null) return l;
        c.getHotkeys().getCategories().forEach((cat, hks) -> {
            for (var hk : hks) {
                StringBuilder k = new StringBuilder();
                for (var key : hk.getKeys()) k.append(key.getType()).append(':').append(key.getCode()).append(' ');
                l.add(String.format("%-34s %s", "pv " + hk.getName(), k.length() == 0 ? "aucune" : k.toString().trim()));
            }
        });
        return l;
    }

    /** J'émets à la radio en ce moment (touche B tenue et activation active) ? Faux sans Plasmo Voice. */
    public static boolean radioEmet() {
        try {
            return radio != null && radio.emet();
        } catch (Throwable t) {
            return false;
        }
    }
}
