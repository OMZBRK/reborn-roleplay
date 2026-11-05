package fr.reborn.hud.voice;

import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.keybind.TouchesMigration;
import fr.reborn.hud.keybind.TouchesPlasmo;
import fr.reborn.hud.menu.settings.RebornPrefs;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.config.hotkey.Hotkey;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Fusion des touches de la voix avec les raccourcis de Plasmo Voice (lot 3, CADRAGE §10, décision du 05/11 : « une
 * seule liaison par action, aucun doublon »). Chargée par {@link RebornVoiceAddon} seulement, donc seulement si Plasmo
 * Voice est là ; se branche dans {@link TouchesPlasmo} pour les Paramètres et le banc.
 *
 * <p>Quatre liens touche Reborn ↔ raccourci de Plasmo ({@link Lien}) :
 * <ul>
 *   <li>Couper le micro ({@link HudKeybinds#MICRO}, I) ↔ {@code key.plasmovoice.general.mute_microphone} ;</li>
 *   <li>Couper le son ({@link HudKeybinds#SON}, aucune) ↔ {@code key.plasmovoice.general.disable_voice} ;</li>
 *   <li>Appuyer pour parler ({@link HudKeybinds#PARLER}, aucune) ↔ {@code key.plasmovoice.proximity.ptt} (Alt gauche
 *       chez Plasmo, en double avec le Regard libre) ;</li>
 *   <li>Parler à la radio ({@link HudKeybinds#RADIO_PARLER}, B) ↔ l'appuyer-pour-parler de l'activation « radio » envoyée
 *       par le serveur (reprend la recopie de {@link RadioVoix}, désormais dans les deux sens).</li>
 * </ul>
 * <b>Nos touches font foi</b> : chaque tick, une touche Reborn changée est recopiée chez Plasmo ({@code setKeys}) ; un
 * raccourci changé dans le menu de Plasmo (V › Raccourcis) revient dans la nôtre ({@code addKeysChangeListener}, et la
 * relecture du tick en filet : Plasmo remplace l'objet du raccourci quand on le réinitialise). Nos touches n'ont aucun
 * code d'action : c'est Plasmo qui agit, une seule fois.
 *
 * <p><b>Reprise</b> (une fois, après la migration des touches, drapeau {@link RebornPrefs#touchesPvVersion}) : un
 * raccourci que le joueur avait personnalisé chez Plasmo (ni vide ni sur le défaut de Plasmo) est repris dans la touche
 * Reborn, si elle est encore sur son défaut ou non liée (migration 4 : touche déjà prise) ; sinon la touche Reborn est
 * recopiée chez Plasmo. Le « couper le micro » resté sur US M en AZERTY (il affichait « , » : migration du 15/10 ratée,
 * TOUCHES_LIBRES.md) est ainsi remplacé par I, et Alt gauche quitte l'appuyer-pour-parler de Plasmo — <b>sauf</b> si la
 * voix de proximité est réglée sur « Appuyer » : le raccourci est alors repris tel quel dans « Appuyer pour parler »
 * (même Alt gauche, le conflit avec le Regard libre s'affiche dans Commandes), pour ne jamais laisser le joueur sans
 * touche pour parler.
 *
 * <p>Coût : quatre comparaisons par tick, rien d'alloué tant que rien ne change.
 */
final class PlasmoTouches implements TouchesPlasmo.Pont {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/touches");

    static final String MUET = "key.plasmovoice.general.mute_microphone";
    static final String CASQUE = "key.plasmovoice.general.disable_voice";
    static final String PTT = "key.plasmovoice.proximity.ptt";
    /**
     * Raccourci de l'activation radio : Plasmo le range dans la catégorie « hidden » et le garde dans sa config d'une
     * session à l'autre, même hors serveur (où {@link #radio()} ne le rend plus) : il est toujours fusionné.
     */
    static final String RADIO_PTT = "key.plasmovoice." + RadioVoix.NOM + ".ptt";
    /** Version de la reprise ({@link RebornPrefs#touchesPvVersion}). */
    static final int VERSION = 1;

    private final PlasmoVoiceClient client;
    private final Lien[] liens;

    PlasmoTouches(PlasmoVoiceClient client) {
        this.client = client;
        this.liens = new Lien[]{
            new Lien("Couper le micro", () -> HudKeybinds.MICRO, () -> raccourci(MUET),
                Set.of(Hotkey.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_M))),
            new Lien("Couper le son", () -> HudKeybinds.SON, () -> raccourci(CASQUE), Set.of()),
            new Lien("Appuyer pour parler", () -> HudKeybinds.PARLER, () -> raccourci(PTT),
                Set.of(Hotkey.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_ALT))),
            new Lien("Parler à la radio", () -> HudKeybinds.RADIO_PARLER, this::radio, null),
        };
    }

    private Hotkey raccourci(String nom) {
        return client.getHotkeys().getHotkey(nom).orElse(null);
    }

    private Hotkey radio() {
        Optional<? extends ClientActivation> a = client.getActivationManager().getActivationByName(RadioVoix.NOM);
        return a.map(ClientActivation::getPttKey).orElse(null);
    }

    /** Thread client, chaque tick (après la migration des touches). */
    void tick() {
        if (!TouchesMigration.faite()) return;
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        if (prefs.touchesPvVersion < VERSION) {
            if (!reprendre()) return;                                   // Plasmo pas encore prêt : on réessaie
            prefs.touchesPvVersion = VERSION;
            prefs.touchesPv = true;
            prefs.save();
        }
        boolean sauver = false;
        for (Lien l : liens) sauver |= l.tick();
        if (sauver) sauverConfig();
    }

    /** Reprise une fois : raccourcis personnalisés chez Plasmo → touches Reborn, le reste → Plasmo. Faux si pas prêt. */
    private boolean reprendre() {
        if (raccourci(MUET) == null) return false;
        boolean sauver = false;
        for (Lien l : liens) {
            if (l.defautPlasmo == null) continue;                       // radio : arrive avec le serveur, poussée au tick
            KeyMapping km = l.reborn.get();
            Hotkey h = l.plasmo.get();
            if (km == null || h == null) continue;
            Set<Hotkey.Key> p = h.getKeys();
            boolean perso = !p.isEmpty() && !p.equals(l.defautPlasmo);
            // appuyer-pour-parler en service (voix réglée sur « Appuyer ») : le vider laisserait le joueur muet
            boolean enService = !p.isEmpty() && PTT.equals(h.getName()) && pttEnService();
            if ((perso || enService) && (km.isDefault() || km.isUnbound())) {
                LOG.info("Plasmo Voice : « {} » {} chez Plasmo ({}) -> repris dans la touche Reborn", l.libelle,
                    perso ? "personnalisé" : "en service", p);
                l.tirer(km, h);
            } else {
                if (!p.isEmpty()) LOG.info("Plasmo Voice : « {} » {} -> touche Reborn {}", l.libelle, p,
                    KeyMappingHelper.getBoundKeyOf(km).getName());
                sauver |= l.pousser(h, KeyMappingHelper.getBoundKeyOf(km));
            }
            l.vu = h;
            l.ecouter(h);
        }
        if (sauver) sauverConfig();
        return true;
    }

    /** La voix de proximité est-elle réglée sur « Appuyer » ? (faux si inconnu) */
    private static boolean pttEnService() {
        return fr.reborn.hud.menu.settings.VoixPlasmo.activation()
            == fr.reborn.hud.menu.settings.VoixPlasmo.ACTIVATION_APPUYER;
    }

    private void sauverConfig() {
        try {
            su.plo.voice.client.ModVoiceClient c = su.plo.voice.client.ModVoiceClient.INSTANCE;
            if (c != null) c.getConfig().save(true);
        } catch (Throwable t) {
            LOG.warn("Plasmo Voice : raccourcis non enregistrés ({})", t.toString());
        }
    }

    /* ------------------------------------------------------------------ Pont (Paramètres, banc) */

    @Override
    public void autres(BiConsumer<String, InputConstants.Key> sortie) {
        Set<String> fusionnes = new HashSet<>();
        for (Lien l : liens) if (l.vu != null) fusionnes.add(l.vu.getName());
        fusionnes.add(MUET);
        fusionnes.add(CASQUE);
        fusionnes.add(PTT);
        fusionnes.add(RADIO_PTT);
        client.getHotkeys().getCategories().forEach((cat, hks) -> {
            for (Hotkey hk : hks) {
                if (fusionnes.contains(hk.getName()) || hk.getKeys().isEmpty()) continue;
                sortie.accept(Component.translatable(hk.getName()).getString(), versReborn(hk.getKeys()));
            }
        });
    }

    @Override
    public String etat() {
        StringBuilder sb = new StringBuilder();
        for (Lien l : liens) {
            KeyMapping km = l.reborn.get();
            Hotkey h = l.plasmo.get();
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append(l.libelle).append(" : reborn=")
                .append(km == null ? "?" : KeyMappingHelper.getBoundKeyOf(km).getName())
                .append(" plasmo=").append(h == null ? "absent" : texte(h.getKeys()));
        }
        return sb.toString();
    }

    private static String texte(Set<Hotkey.Key> keys) {
        if (keys.isEmpty()) return "aucune";
        StringBuilder sb = new StringBuilder();
        for (Hotkey.Key k : keys) sb.append(k.getType()).append(':').append(k.getCode()).append(' ');
        return sb.toString().trim();
    }

    /* ------------------------------------------------------------------ conversions */

    /** Touche Minecraft → touche de Plasmo ; null = aucune. */
    static Hotkey.Key versPlasmo(InputConstants.Key k) {
        if (k == null || k.equals(InputConstants.UNKNOWN) || k.getValue() < 0) return null;
        Hotkey.Type t = switch (k.getType()) {
            case MOUSE -> Hotkey.Type.MOUSE;
            case SCANCODE -> Hotkey.Type.SCANCODE;
            default -> Hotkey.Type.KEYSYM;
        };
        return t.getOrCreate(k.getValue());
    }

    /**
     * Touches de Plasmo → une touche Minecraft (une {@link KeyMapping} n'en tient qu'une) : la touche principale d'une
     * combinaison (pas Ctrl, Maj, Alt, Win), sinon la dernière ; aucune si vide.
     */
    static InputConstants.Key versReborn(Set<Hotkey.Key> keys) {
        Hotkey.Key choisie = null;
        for (Hotkey.Key k : keys) {
            if (choisie == null || !modificateur(k)) choisie = k;
        }
        if (choisie == null) return InputConstants.UNKNOWN;
        InputConstants.Type t = switch (choisie.getType()) {
            case MOUSE -> InputConstants.Type.MOUSE;
            case SCANCODE -> InputConstants.Type.SCANCODE;
            default -> InputConstants.Type.KEYSYM;
        };
        return t.getOrCreate(choisie.getCode());
    }

    private static boolean modificateur(Hotkey.Key k) {
        if (k.getType() != Hotkey.Type.KEYSYM) return false;
        int c = k.getCode();
        return c == GLFW.GLFW_KEY_LEFT_SHIFT || c == GLFW.GLFW_KEY_RIGHT_SHIFT || c == GLFW.GLFW_KEY_LEFT_CONTROL
            || c == GLFW.GLFW_KEY_RIGHT_CONTROL || c == GLFW.GLFW_KEY_LEFT_ALT || c == GLFW.GLFW_KEY_RIGHT_ALT
            || c == GLFW.GLFW_KEY_LEFT_SUPER || c == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    /* ------------------------------------------------------------------ un lien */

    /** Une touche Reborn et son raccourci de Plasmo, avec le dernier état synchronisé des deux côtés. */
    private static final class Lien {
        final String libelle;
        final Supplier<KeyMapping> reborn;
        final Supplier<Hotkey> plasmo;
        /** Défaut du raccourci chez Plasmo (reprise) ; null = pas de reprise (radio). */
        final Set<Hotkey.Key> defautPlasmo;
        /** Objet du raccourci écouté (Plasmo le remplace à la réinitialisation, la radio à chaque connexion). */
        Hotkey vu;
        InputConstants.Key rVu;
        Set<Hotkey.Key> pVu = Set.of();
        /** Vrai pendant notre propre {@code setKeys} (l'écouteur de Plasmo est appelé dedans). */
        boolean pousse;
        final Hotkey.OnKeysChange ecoute;
        /**
         * Raccourcis déjà écoutés : Plasmo rend le même objet à chaque reconnexion (radio) ; sans ce registre, un
         * écouteur de plus s'y ajouterait à chaque session.
         */
        private final Set<Hotkey> ecoutes = Collections.newSetFromMap(new IdentityHashMap<>());

        Lien(String libelle, Supplier<KeyMapping> reborn, Supplier<Hotkey> plasmo, Set<Hotkey.Key> defautPlasmo) {
            this.libelle = libelle;
            this.reborn = reborn;
            this.plasmo = plasmo;
            this.defautPlasmo = defautPlasmo;
            // changé dans le menu de Plasmo : relu tout de suite sur le thread client (le tick le ferait aussi)
            this.ecoute = keys -> {
                if (pousse) return;
                Minecraft.getInstance().execute(() -> {
                    try {
                        KeyMapping km = reborn.get();
                        Hotkey h = vu;
                        if (km != null && h != null && !h.getKeys().equals(pVu)) tirer(km, h);
                    } catch (RuntimeException ignored) { }
                });
            };
        }

        /** Vrai si la config de Plasmo a changé (à enregistrer). */
        boolean tick() {
            KeyMapping km = reborn.get();
            if (km == null) return false;
            Hotkey h;
            try {
                h = plasmo.get();
            } catch (RuntimeException e) {
                h = null;
            }
            if (h == null) {
                vu = null;
                return false;
            }
            InputConstants.Key r = KeyMappingHelper.getBoundKeyOf(km);
            if (h != vu) {
                // nouveau raccourci (connexion, réinitialisation chez Plasmo) : nos touches font foi
                vu = h;
                ecouter(h);
                return pousser(h, r);
            }
            if (r != rVu) return pousser(h, r);                         // changée chez nous (Paramètres, Commandes)
            if (!h.getKeys().equals(pVu)) tirer(km, h);                 // changée chez Plasmo
            return false;
        }

        /** Branche l'écouteur une seule fois par objet de raccourci. */
        void ecouter(Hotkey h) {
            if (ecoutes.add(h)) h.addKeysChangeListener(ecoute);
        }

        /** Recopie la touche Reborn chez Plasmo ; une combinaison qui la contient déjà est gardée. */
        boolean pousser(Hotkey h, InputConstants.Key r) {
            Hotkey.Key cle = versPlasmo(r);
            Set<Hotkey.Key> p = h.getKeys();
            boolean deja = cle == null ? p.isEmpty() : p.contains(cle);
            rVu = r;
            if (!deja) {
                pousse = true;
                try {
                    h.setKeys(cle == null ? Set.of() : Set.of(cle));
                } finally {
                    pousse = false;
                }
                LOG.info("Plasmo Voice : « {} » -> {}", libelle, cle == null ? "aucune" : r.getName());
            }
            pVu = Set.copyOf(h.getKeys());
            return !deja;
        }

        /** Reprend chez nous le raccourci changé chez Plasmo. */
        void tirer(KeyMapping km, Hotkey h) {
            Set<Hotkey.Key> p = h.getKeys();
            pVu = Set.copyOf(p);
            InputConstants.Key k = versReborn(p);
            if (!k.equals(KeyMappingHelper.getBoundKeyOf(km))) {
                km.setKey(k);
                KeyMapping.resetMapping();
                Minecraft mc = Minecraft.getInstance();
                if (mc.options != null) mc.options.save();
                LOG.info("Plasmo Voice : « {} » changé dans le menu de Plasmo -> touche Reborn {}", libelle, k.getName());
            }
            rVu = KeyMappingHelper.getBoundKeyOf(km);
        }
    }
}
