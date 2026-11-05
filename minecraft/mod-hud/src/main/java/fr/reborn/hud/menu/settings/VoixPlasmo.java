package fr.reborn.hud.menu.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Réglages de Plasmo Voice repris dans Paramètres › Audio › Voix (gabarit o2 du lot 3) : volume des voix, volume du
 * micro, activation (voix ou appuyer pour parler), seuil de détection, micro choisi, réduction du bruit, et l'ouverture
 * du menu de Plasmo. Les valeurs sont lues et écrites dans la configuration client de Plasmo Voice
 * ({@code VoiceClientConfig}, comme {@code voice/RebornVoiceAddon}) : Plasmo écoute ses entrées et applique tout seul,
 * puis la configuration est enregistrée (écriture asynchrone).
 *
 * <p><b>Plasmo Voice reste facultatif</b> (MOD_HUD §6) : aucune signature de cette classe ne nomme un type de Plasmo ;
 * tout passe par {@link Pv}, chargée au premier appel. Sans le jar, l'appel échoue une fois
 * ({@code NoClassDefFoundError}), {@link #present()} rend faux et la page montre le volume de Minecraft à la place.
 */
public final class VoixPlasmo {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/reglages");

    public static final int VOLUME = 0, MICRO = 1, SEUIL = 2;
    /** Activation de la voix de proximité : inconnue (pas encore connecté au serveur vocal), voix, appuyer. */
    public static final int ACTIVATION_INCONNUE = -1, ACTIVATION_VOIX = 0, ACTIVATION_APPUYER = 1;

    private static boolean absent;

    private VoixPlasmo() {}

    /** Plasmo Voice chargé et sa configuration lisible. */
    public static boolean present() {
        if (absent) return false;
        try {
            return Pv.pret();
        } catch (LinkageError e) {
            absent = true;
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Valeur brute d'un réglage numérique ({@link #VOLUME}, {@link #MICRO} : 1 = 100 % ; {@link #SEUIL} : dB). */
    public static double lire(int cle) {
        try {
            return Pv.lire(cle);
        } catch (Throwable t) {
            return cle == SEUIL ? -30 : 1;
        }
    }

    public static double min(int cle) {
        try {
            return Pv.borne(cle, false);
        } catch (Throwable t) {
            return cle == SEUIL ? -60 : 0;
        }
    }

    public static double max(int cle) {
        try {
            return Pv.borne(cle, true);
        } catch (Throwable t) {
            return cle == SEUIL ? 0 : 2;
        }
    }

    /** Applique tout de suite ; l'enregistrement attend la fin du glissé ({@link Enregistrement}). */
    public static void ecrire(int cle, double v) {
        try {
            Pv.ecrire(cle, v);
        } catch (Throwable t) {
            LOG.warn("Plasmo Voice : réglage {} non écrit ({})", cle, t.toString());
            return;
        }
        Enregistrement.plasmo();
    }

    /** Enregistre la configuration de Plasmo (écriture asynchrone) ; rien sans Plasmo. */
    static void sauver() {
        if (absent) return;
        try {
            Pv.sauver();
        } catch (Throwable t) {
            LOG.warn("Plasmo Voice : configuration non enregistrée ({})", t.toString());
        }
    }

    public static boolean reductionBruit() {
        try {
            return Pv.bruit();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void reductionBruit(boolean v) {
        try {
            Pv.bruit(v);
        } catch (Throwable t) {
            LOG.warn("Plasmo Voice : réduction du bruit non écrite ({})", t.toString());
        }
    }

    /** {@link #ACTIVATION_VOIX}, {@link #ACTIVATION_APPUYER}, ou {@link #ACTIVATION_INCONNUE} hors serveur vocal. */
    public static int activation() {
        try {
            return Pv.activation();
        } catch (Throwable t) {
            return ACTIVATION_INCONNUE;
        }
    }

    public static void activation(int a) {
        try {
            Pv.activation(a);
        } catch (Throwable t) {
            LOG.warn("Plasmo Voice : activation non écrite ({})", t.toString());
        }
    }

    /** Nom du micro choisi dans Plasmo Voice (« Par défaut » s'il suit celui du système). */
    public static String micro() {
        try {
            String m = Pv.micro();
            return m == null || m.isBlank() ? "Par défaut" : m;
        } catch (Throwable t) {
            return "Par défaut";
        }
    }

    /** Ouvre le menu de Plasmo Voice (celui de la touche V). */
    public static void ouvrirMenu() {
        try {
            Pv.ouvrirMenu();
        } catch (Throwable t) {
            LOG.warn("Plasmo Voice : menu non ouvert ({})", t.toString());
        }
    }

    /** Tout ce qui nomme une classe de Plasmo Voice (chargée au premier appel seulement). */
    private static final class Pv {

        private Pv() {}

        private static su.plo.voice.client.config.VoiceClientConfig cfg() {
            su.plo.voice.client.ModVoiceClient c = su.plo.voice.client.ModVoiceClient.INSTANCE;
            return c == null ? null : c.getConfig();
        }

        static boolean pret() {
            return cfg() != null;
        }

        private static su.plo.config.entry.DoubleConfigEntry entree(int cle) {
            su.plo.voice.client.config.VoiceClientConfig.Voice v = cfg().getVoice();
            return switch (cle) {
                case VOLUME -> v.getVolume();
                case MICRO -> v.getMicrophoneVolume();
                default -> v.getActivationThreshold();
            };
        }

        static double lire(int cle) {
            Double d = entree(cle).value();
            return d == null ? 0 : d;
        }

        static double borne(int cle, boolean haut) {
            su.plo.config.entry.DoubleConfigEntry e = entree(cle);
            return haut ? e.getMax() : e.getMin();
        }

        static void ecrire(int cle, double v) {
            su.plo.config.entry.DoubleConfigEntry e = entree(cle);
            e.set(Math.max(e.getMin(), Math.min(e.getMax(), v)));
        }

        static void sauver() {
            var c = cfg();
            if (c != null) c.save(true);
        }

        static boolean bruit() {
            return Boolean.TRUE.equals(cfg().getVoice().getNoiseSuppression().value());
        }

        static void bruit(boolean v) {
            cfg().getVoice().getNoiseSuppression().set(v);
            cfg().save(true);
        }

        private static java.util.Optional<su.plo.voice.client.config.capture.ConfigClientActivation> proximite() {
            return cfg().getActivations().getActivation(su.plo.voice.proto.data.audio.capture.VoiceActivation.PROXIMITY_ID);
        }

        static int activation() {
            var a = proximite();
            if (a.isEmpty()) return ACTIVATION_INCONNUE;
            var t = a.get().getConfigType().value();
            return t == su.plo.voice.api.client.audio.capture.ClientActivation.Type.PUSH_TO_TALK
                ? ACTIVATION_APPUYER : ACTIVATION_VOIX;
        }

        static void activation(int v) {
            var a = proximite();
            if (a.isEmpty()) return;
            a.get().getConfigType().set(v == ACTIVATION_APPUYER
                ? su.plo.voice.api.client.audio.capture.ClientActivation.Type.PUSH_TO_TALK
                : su.plo.voice.api.client.audio.capture.ClientActivation.Type.VOICE);
            cfg().save(true);
        }

        static String micro() {
            return cfg().getVoice().getInputDevice().value();
        }

        static void ouvrirMenu() {
            su.plo.voice.client.ModVoiceClient c = su.plo.voice.client.ModVoiceClient.INSTANCE;
            if (c != null) su.plo.voice.client.gui.settings.VoiceScreens.INSTANCE.openSettings(c);
        }
    }
}
