package fr.reborn.hud.voice;

import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.radio.RadioClient;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.audio.source.ClientAudioSource;
import su.plo.voice.api.client.config.hotkey.Hotkey;
import su.plo.voice.api.client.event.audio.capture.ClientActivationRegisteredEvent;
import su.plo.voice.api.client.event.audio.source.AudioSourceWriteEvent;
import su.plo.voice.api.client.event.render.HudActivationRenderEvent;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.proto.data.audio.source.SourceInfo;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La radio côté Plasmo Voice client (docs/gameplay/RADIO.md §2 client, §5, §6) — chargée par {@link RebornVoiceAddon}
 * seulement si Plasmo Voice est là.
 * <ul>
 *   <li><b>Son radio</b> sur les échantillons reçus de la ligne « radio » ({@link AudioSourceWriteEvent}, thread
 *       audio) : passe-bande 300-3000 Hz, légère saturation, souffle léger ; <b>haché</b> (émetteur ou moi à terre) :
 *       trous de 60-140 ms, bitcrush, souffle plus fort. Les clics de début et de fin sont joués par
 *       {@link RadioClient} à la réception de {@code parle} / {@code tait}.</li>
 *   <li><b>Icône d'activation masquée</b> pour la radio ({@link HudActivationRenderEvent}) : l'élément « Escouade » du
 *       HUD suffit.</li>
 *   <li><b>Touche</b> : l'activation « radio » envoyée par le serveur est forcée en push-to-talk et reçoit la touche
 *       Reborn « Parler à la radio » (B par défaut, réglable dans les contrôles Reborn) ; resynchronisée si le joueur
 *       la change.</li>
 * </ul>
 */
public final class RadioVoix {

    static final String NOM = "radio";

    private final PlasmoVoiceClient client;
    private final Map<UUID, Filtre> filtres = new ConcurrentHashMap<>();
    private volatile UUID ligneRadio;
    private InputConstants.Key derniereTouche;

    RadioVoix(PlasmoVoiceClient client) {
        this.client = client;
    }

    /* ------------------------------------------------------------------ activation : touche + icône */

    @EventSubscribe
    public void onActivationRegistered(ClientActivationRegisteredEvent event) {
        if (NOM.equals(event.getActivation().getName())) {
            derniereTouche = null;
            regler(event.getActivation());
        }
    }

    @EventSubscribe
    public void onHudActivation(HudActivationRenderEvent event) {
        if (NOM.equals(event.getActivation().getName())) event.setRender(false);
    }

    /** Thread client, chaque tick : resynchronise la touche si le joueur l'a changée dans les contrôles Reborn. */
    void tick() {
        if (HudKeybinds.RADIO_PARLER == null) return;
        migrerMuet();
        InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(HudKeybinds.RADIO_PARLER);
        if (k.equals(derniereTouche)) return;
        client.getActivationManager().getActivationByName(NOM).ifPresent(this::regler);
    }

    /**
     * Migration des touches du 15/10 (une fois, drapeau {@code RebornPrefs.touchesPv}) : le raccourci « couper le
     * micro » de Plasmo Voice est sur M par défaut, comme la carte Reborn ; s'il y est encore (jamais changé) et que la
     * carte est bien sur M, on le laisse sans touche (le micro se coupe toujours depuis le menu Plasmo Voice, touche V).
     * Un raccourci personnalisé n'est pas touché.
     */
    private void migrerMuet() {
        var prefs = fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE;
        if (prefs.touchesPv || HudKeybinds.MAP == null) return;
        try {
            var hk = client.getHotkeys().getHotkey("key.plasmovoice.general.mute_microphone");
            if (hk.isEmpty()) return;                                   // Plasmo Voice pas encore prêt : on réessaie
            Set<Hotkey.Key> keys = hk.get().getKeys();
            InputConstants.Key carte = KeyMappingHelper.getBoundKeyOf(HudKeybinds.MAP);
            boolean surM = keys.size() == 1 && keys.contains(Hotkey.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_M));
            if (surM && carte.getType() == InputConstants.Type.KEYSYM && carte.getValue() == org.lwjgl.glfw.GLFW.GLFW_KEY_M) {
                hk.get().setKeys(Set.of());
                sauverConfig();
                org.slf4j.LoggerFactory.getLogger("reborn-hud/touches")
                    .info("Plasmo Voice : « couper le micro » M -> aucune (M = carte)");
            }
        } catch (RuntimeException e) {
            return;
        }
        prefs.touchesPv = true;
        prefs.save();
    }

    /** Écrit la config de Plasmo Voice ({@code VoiceClientConfig.save(boolean)}, hors API : par réflexion). */
    private void sauverConfig() {
        try {
            Object cfg = client.getConfig();
            cfg.getClass().getMethod("save", boolean.class).invoke(cfg, true);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    /** Activation radio active (j'émets) ? */
    boolean emet() {
        try {
            return client.getActivationManager().getActivationByName(NOM).map(a -> a.isActive() && !a.isDisabled()).orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void regler(ClientActivation a) {
        if (HudKeybinds.RADIO_PARLER == null) return;
        forcerPushToTalk(a);
        InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(HudKeybinds.RADIO_PARLER);
        derniereTouche = k;
        Hotkey.Type type = switch (k.getType()) {
            case MOUSE -> Hotkey.Type.MOUSE;
            case SCANCODE -> Hotkey.Type.SCANCODE;
            default -> Hotkey.Type.KEYSYM;
        };
        try {
            a.getPttKey().setKeys(k.getValue() < 0 ? Set.of() : Set.of(type.getOrCreate(k.getValue())));
        } catch (RuntimeException ignored) { }
    }

    /**
     * Le type d'activation (push-to-talk, voix, hérité) n'est pas exposé par l'API : une activation non principale est
     * « héritée » par défaut, ce qui ferait émettre la radio avec la voix de proximité. On le règle en push-to-talk par
     * réflexion sur le champ {@code configType} de l'implémentation (repli silencieux si la structure change).
     */
    private static void forcerPushToTalk(ClientActivation a) {
        try {
            var f = a.getClass().getDeclaredField("configType");
            f.setAccessible(true);
            Object entry = f.get(a);
            if (entry == null) return;
            for (var m : entry.getClass().getMethods()) {
                if (m.getName().equals("set") && m.getParameterCount() == 1) {
                    m.invoke(entry, ClientActivation.Type.PUSH_TO_TALK);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    /* ------------------------------------------------------------------ son radio (thread audio) */

    @EventSubscribe
    public void onWrite(AudioSourceWriteEvent event) {
        ClientAudioSource<?> source = event.getSource();
        SourceInfo info = source.getSourceInfo();
        if (info == null || !estRadio(info.getLineId())) return;
        UUID id = info.getId();
        Filtre f = filtres.computeIfAbsent(id, k -> new Filtre());
        f.traiter(event.getSamples(), event.getSequenceNumber(), RadioClient.hache(id));
        if (filtres.size() > 64) filtres.clear();
    }

    private boolean estRadio(UUID lineId) {
        if (lineId == null) return false;
        UUID l = ligneRadio;
        if (l == null) {
            try {
                Optional<? extends su.plo.voice.api.client.audio.line.ClientSourceLine> line =
                        client.getSourceLineManager().getLineByName(NOM);
                if (line.isEmpty()) return false;
                l = line.get().getId();
                ligneRadio = l;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return l.equals(lineId);
    }

    /** Oublie la ligne (reconnexion : les identifiants changent). */
    void reinitialiser() {
        ligneRadio = null;
        filtres.clear();
        derniereTouche = null;
    }

    /**
     * Passe-bande (deux biquads : passe-haut 300 Hz, passe-bas 3 kHz), saturation douce, souffle ; haché : trous en
     * rafales, bitcrush, échantillonnage bloqué. 48 kHz mono (Opus de Plasmo Voice). Coût : quelques opérations par
     * échantillon, 960 échantillons toutes les 20 ms et par source.
     */
    static final class Filtre {
        private final Biquad hp = Biquad.passeHaut(48_000, 300, 0.707);
        private final Biquad hp2 = Biquad.passeHaut(48_000, 300, 0.707);
        private final Biquad lp = Biquad.passeBas(48_000, 3000, 0.707);
        private final Biquad lp2 = Biquad.passeBas(48_000, 3000, 0.707);
        private long graine = System.nanoTime();
        private int tenu, tenuN;

        void traiter(short[] s, long seq, boolean hache) {
            boolean trou = hache && trou(seq);
            float souffle = hache ? 0.022f : 0.006f;
            for (int i = 0; i < s.length; i++) {
                if (trou) {
                    s[i] = (short) (bruit() * 0.03f * 32767f);
                    continue;
                }
                double x = s[i] / 32768.0;
                x = lp2.f(lp.f(hp2.f(hp.f(x))));
                x = Math.tanh(x * 1.8) * 0.85;
                if (hache) {
                    // bitcrush (6 bits) + échantillonnage tenu (÷3) : voix « hachée » de talkie au bord de la portée
                    x = Math.round(x * 32) / 32.0;
                    if (tenuN-- <= 0) { tenu = (int) (x * 32767); tenuN = 2; }
                    x = tenu / 32767.0;
                }
                x += bruit() * souffle;
                int v = (int) Math.round(x * 32767);
                s[i] = (short) Math.max(-32768, Math.min(32767, v));
            }
        }

        /** Trames de 20 ms coupées en rafales (≈ 35 %), stables dans une rafale de 3 trames. */
        private static boolean trou(long seq) {
            long r = seq / 3;
            long h = (r * 0xD6E8FEB86659FD93L) ^ (r >>> 11);
            h ^= h >>> 31;
            return Math.floorMod(h, 100) < 35;
        }

        private float bruit() {
            graine ^= graine << 13;
            graine ^= graine >>> 7;
            graine ^= graine << 17;
            return ((graine & 0xFFFF) / 32768f) - 1f;
        }
    }

    /** Filtre biquad (RBJ), forme directe I. */
    static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0; this.a1 = a1 / a0; this.a2 = a2 / a0;
        }

        static Biquad passeHaut(double fs, double fc, double q) {
            double w = 2 * Math.PI * fc / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al);
        }

        static Biquad passeBas(double fs, double fc, double q) {
            double w = 2 * Math.PI * fc / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al);
        }

        double f(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        }
    }
}
