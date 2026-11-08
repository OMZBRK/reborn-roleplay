package fr.reborn.hud.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.menu.settings.RebornPrefs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Migration ponctuelle des touches (carte réduite du 15/10, docs/technique/MOD_HUD.md §2). Les joueurs existants ont
 * un {@code options.txt} avec les anciens défauts : une seule fois (drapeau {@link RebornPrefs#touchesVersion}), chaque
 * touche dont la valeur sauvegardée est <b>encore l'ancien défaut</b> reçoit le nouveau ; une touche personnalisée par
 * le joueur n'est jamais touchée. Les touches Reborn supprimées disparaissent simplement (Minecraft ignore les lignes
 * inconnues d'{@code options.txt}).
 *
 * <ul>
 *   <li>Menu OST (mod-ost) : M → N (N est libérée par la radio, M = carte) ;</li>
 *   <li>Dash : V → G (V = réglages de Plasmo Voice, G est libérée par la galerie) ;</li>
 *   <li>Emotecraft « fastchoose » : B → aucune (B = radio ; les émotes sont dans le menu « . ») ;</li>
 *   <li>Minecraft « Actions rapides » : G → aucune, « Interactions sociales » : P → aucune (conflits avec le dash et le
 *       mode photo ; Reborn n'utilise ni l'un ni l'autre) ;</li>
 *   <li>Vue : la touche vanilla « Changer de vue » (F5) pilote la caméra Reborn (cf. {@link HudKeybinds#vueReborn}),
 *       rien à déplacer.</li>
 * </ul>
 * Les raccourcis de Plasmo Voice ne sont pas des touches Minecraft : ils sont repris à part, quand Plasmo est prêt
 * ({@code voice/PlasmoTouches}, drapeau {@link RebornPrefs#touchesPvVersion}).
 *
 * <p><b>Version 2 — disposition du clavier</b> (15/10) : les défauts de Minecraft sont des <i>positions</i> de touche
 * (noms US) ; en AZERTY la touche « M » de la carte s'affichait « , », « . » s'affichait « : ». Pour chaque touche Reborn
 * (et le menu OST) restée sur son défaut, {@link #cible} cherche la touche qui <b>affiche</b> la même lettre sur le
 * clavier du joueur ({@code glfwGetKeyName}) et l'y place si elle est libre. AZERTY : Carte → « m » (US « ; »),
 * Animations → la touche gravée « ; . » (US « , », affichée « ; », « . » n'existant qu'avec Maj), Dégainer reste sur la
 * même position (affichée « W ») car « z » est Avancer (ZQSD). QWERTZ : Dégainer → « z » (US « Y »). QWERTY : rien. Les
 * touches non imprimables (Ctrl, Maj, Alt, F1…, Entrée) ne dépendent pas de la disposition.
 *
 * <p><b>Version 3 — « Portée de la voix » sur H</b> (31/10) : la règle de la version 2 s'applique à cette seule touche
 * chez les joueurs déjà migrés (en AZERTY, QWERTZ et QWERTY, H s'affiche « H » : rien ne bouge). H n'est <b>jamais
 * prise</b> à une autre liaison : si le joueur l'a déjà donnée à autre chose, le conflit reste (Minecraft le montre dans
 * les Commandes) et il est noté au journal ({@link #verifierPortee}). Aucune autre touche Reborn n'a H par défaut, et
 * la version 2 ne pose une touche que sur une touche libre (la portée passe avant la parade dans la carte) ; seul un
 * joueur en BÉPO migré avant le 31/10 peut avoir la parade sur la touche US H (elle y affiche « c ») : la portée lui
 * cède alors la place (aucune touche, à lier dans Réglages → Contrôles).
 *
 * <p><b>Version 4 — lot 3 « Échap et touches »</b> (05/11, TOUCHES_LIBRES.md) :
 * <ul>
 *   <li><b>Concentration</b> (gravée Y) et <b>Couper le micro</b> (I) : la règle de la version 2 leur est appliquée chez
 *       les joueurs déjà migrés. En QWERTZ neuf, Dégainer (US Z → US Y) et Concentration (US Y → US Z) <b>s'échangent</b> :
 *       {@link #planifier} compte comme libre une cible tenue par une touche Reborn qui part dans la même passe.</li>
 *   <li>Y et I ne sont <b>jamais prises</b> (règle de H) : si le joueur y a déjà mis autre chose, la nôtre reste sans
 *       touche ({@link #jamaisPrises}).</li>
 *   <li>Touches de Minecraft inutiles à Reborn <b>déliées</b> (si elles sont encore sur leur défaut) et <b>cachées</b>
 *       des Paramètres ({@link #CACHEES}) : Progrès (L), Amis (O, nouveau en 26.2), Effet de spectateur (F4), Barre du
 *       spectateur (clic molette), Contours en spectateur, Sauver / charger la barre du créatif (C / X), Caméra fluide ;
 *       Actions rapides et Interactions sociales (déjà déliées en version 1).</li>
 *   <li>Le « couper le micro » de Plasmo resté sur US M en AZERTY (il affichait « , ») est remplacé par la touche
 *       Reborn, à la première synchronisation ({@code voice/PlasmoTouches}).</li>
 * </ul>
 * <p><b>Version 5 — retrait de R</b> (lot 4 « Concentration », validé en jeu le 07/11, CADRAGE §5.4) : la
 * Concentration remplace le menu d'interaction R. {@link #retirerInteraction} délie l'ancienne touche « Interaction »
 * ({@link HudKeybinds#INTERACTION}) <b>seulement</b> si elle est encore sur son défaut <b>et</b> si Concentration a
 * une touche ; le défaut est R ou la touche qui affiche « r » où la version 2 l'a posée (BÉPO US L, Dvorak US O,
 * Colemak US S, {@link #surDefaut}) ; règle pure {@link #retraitR}, testée par le banc {@code REBORN_LOT4_DEBUG=v5}.
 * Un joueur dont Y était déjà prise (Concentration sans touche) garde R, qui ouvre la Concentration à sa place ; une
 * touche Interaction personnalisée (le joueur l'a mise sur une autre touche) n'est jamais touchée et reste un alias de
 * la Concentration. « Réinitialiser la section » applique la même règle après avoir remis les défauts, avant la règle
 * du clavier ({@link #reinitialiser}).
 *
 * Idempotente : chaque partie ne s'applique qu'aux versions antérieures, puis le drapeau passe à {@link #VERSION}.
 */
public final class TouchesMigration {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/touches");

    /** Version de la carte des touches ; on la monte si une nouvelle migration s'ajoute. */
    public static final int VERSION = 5;

    /**
     * Touches de Minecraft cachées des Paramètres › Commandes (et déliées une fois par la version 4 si elles étaient sur
     * leur défaut) : inutiles en RP, et en conflit avec des touches Reborn pour plusieurs (C parer, X course, clic
     * molette choisir le bloc, G et P).
     */
    public static final Set<String> CACHEES = Set.of(
        "key.advancements", "key.friends", "key.toggleSpectatorShaderEffects", "key.spectatorHotbar",
        "key.spectatorOutlines", "key.saveToolbarActivator", "key.loadToolbarActivator", "key.smoothCamera",
        "key.quickActions", "key.socialInteractions");

    private TouchesMigration() {}

    static void init() {
        ClientLifecycleEvents.CLIENT_STARTED.register(TouchesMigration::migrer);
    }

    /** Banc ({@code audit/Lot4v5Debug}) : rejoue la migration telle qu'au démarrage (drapeau {@link RebornPrefs#touchesVersion} lu). */
    public static void debugMigrer(Minecraft mc) {
        migrer(mc, k -> GLFW.glfwGetKeyName(k, 0));
    }

    /** Banc : migration rejouée sur un clavier simulé ({@code nom} : ce qu'affiche chaque touche, BÉPO, Dvorak…). */
    public static void debugMigrer(Minecraft mc, IntFunction<String> nom) {
        migrer(mc, nom);
    }

    /** Banc : « Réinitialiser la section » sur un clavier simulé. */
    public static void debugReinitialiser(List<KeyMapping> cles, IntFunction<String> nom) {
        reinitialiser(cles, nom);
    }

    /** Migration faite (les liens avec Plasmo attendent qu'elle soit passée). */
    public static boolean faite() {
        return RebornPrefs.INSTANCE.touchesVersion >= VERSION;
    }

    /** Une touche à migrer : nom de la {@link KeyMapping}, ancien défaut, nouveau ({@code -1} = aucune). */
    private record Regle(String nom, int ancien, int nouveau) { }

    /** Conflits vanilla d'abord (libèrent G et P), puis les déplacements. */
    private static final Regle[] REGLES = {
        new Regle("key.quickActions", GLFW.GLFW_KEY_G, -1),
        new Regle("key.socialInteractions", GLFW.GLFW_KEY_P, -1),
        new Regle("key.emotecraft.fastchoose", GLFW.GLFW_KEY_B, -1),
        new Regle("key.reborn-ost.open_menu", GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_N),
        new Regle("key.reborn-hud.dash", GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_G),
    };

    static void migrer(Minecraft mc) {
        migrer(mc, k -> GLFW.glfwGetKeyName(k, 0));
    }

    private static void migrer(Minecraft mc, IntFunction<String> nom) {
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        prefs.ensureLoaded();
        int v = prefs.touchesVersion;
        if (v >= VERSION || mc.options == null) return;
        int changees = 0;
        if (v < 1) changees += anciensDefauts();
        if (v < 2) {
            // version 2 : toutes les touches Reborn (Concentration et micro compris, échange QWERTZ)
            changees += clavier(mc, nom);
        } else {
            // déjà faite : seulement les touches arrivées depuis (version 3 : portée ; version 4 : concentration, micro)
            List<KeyMapping> nouvelles = new ArrayList<>();
            if (v < 3) nouvelles.add(HudKeybinds.VOIX_PORTEE);
            if (v < 4) {
                nouvelles.add(HudKeybinds.CONCENTRATION);
                nouvelles.add(HudKeybinds.MICRO);
            }
            if (!nouvelles.isEmpty()) changees += clavier(nom, nouvelles);
        }
        if (v < 3) changees += verifierPortee(mc);
        // parties de la version 4 : jamais rejouées chez un joueur déjà en version 4 (ses choix restent)
        if (v < 4) {
            changees += cacher(mc);
            changees += jamaisPrises(mc);
        }
        // version 5 (lot 4, validé en jeu le 07/11) : R déliée si elle est sur son défaut et que Concentration a une
        // touche ; après la version 4, qui a pu laisser Concentration sans touche (Y déjà prise)
        if (v < 5) changees += retirerInteraction(nom);
        if (changees > 0) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
        prefs.touchesVersion = VERSION;
        prefs.save();
        LOG.info("touches : carte migrée de la version {} à la version {} ({} changement(s))", v, VERSION, changees);
    }

    /**
     * Version 3 : la touche « Portée de la voix » ne prend jamais sa touche à une autre liaison. Une touche d'un autre mod
     * ou de Minecraft déjà sur H (le joueur l'y a mise) y reste : Minecraft affiche le conflit dans les Commandes, le
     * joueur choisit ; c'est noté au journal. Aucune autre touche Reborn n'a H par défaut ; la seule qui puisse s'y
     * trouver est une touche déplacée par la version 2 avant que la portée existe (en BÉPO, la touche US H affiche « c » :
     * la parade y est passée le 15/10). Dans ce cas la portée, restée sur son défaut, cède : elle passe sur « aucune »
     * (à lier dans Réglages → Contrôles) plutôt que de déclencher deux touches Reborn à la fois. Renvoie 1 si elle a cédé.
     */
    private static int verifierPortee(Minecraft mc) {
        KeyMapping voix = HudKeybinds.VOIX_PORTEE;
        if (voix == null) return 0;
        InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(voix);
        if (k.equals(InputConstants.UNKNOWN)) return 0;
        boolean reborn = false;
        for (KeyMapping km : mc.options.keyMappings) {
            if (km == voix || KeyMapping.Category.DEBUG.equals(km.getCategory())) continue;
            if (!KeyMappingHelper.getBoundKeyOf(km).equals(k)) continue;
            if (HudKeybinds.REBORN_KEYS.contains(km)) {
                reborn = true;
                LOG.info("portée de la voix : {} déjà prise par la touche Reborn {}", k.getName(), km.getName());
            } else {
                LOG.info("portée de la voix : {} déjà prise par {}, laissée au joueur (Minecraft affiche le conflit)",
                    k.getName(), km.getName());
            }
        }
        if (!reborn || !voix.isDefault()) return 0;
        voix.setKey(InputConstants.UNKNOWN);
        LOG.info("portée de la voix : {} -> aucune (à lier dans Réglages → Contrôles)", k.getName());
        return 1;
    }

    /** Version 4 : touches de Minecraft inutiles, déliées si elles sont encore sur leur défaut (cachées dans tous les cas). */
    private static int cacher(Minecraft mc) {
        int changees = 0;
        for (KeyMapping km : mc.options.keyMappings) {
            if (!CACHEES.contains(km.getName()) || km.isUnbound() || !km.isDefault()) continue;
            InputConstants.Key avant = KeyMappingHelper.getBoundKeyOf(km);
            km.setKey(InputConstants.UNKNOWN);
            changees++;
            LOG.info("touche {} : {} -> aucune (cachée, inutile en RP)", km.getName(), avant.getName());
        }
        return changees;
    }

    /**
     * Version 4 : Concentration et Couper le micro ne prennent jamais une touche déjà donnée à autre chose (règle de H) :
     * la nôtre reste alors sans touche, à lier dans les Paramètres. Les raccourcis F3 + touche ne comptent pas.
     */
    private static int jamaisPrises(Minecraft mc) {
        int changees = 0;
        for (KeyMapping km : new KeyMapping[]{HudKeybinds.CONCENTRATION, HudKeybinds.MICRO}) {
            if (km == null || km.isUnbound()) continue;
            InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(km);
            KeyMapping tenue = tenuePar(mc, k, km);
            if (tenue == null) continue;
            km.setKey(InputConstants.UNKNOWN);
            changees++;
            LOG.info("touche {} : {} déjà prise par {}, laissée au joueur -> aucune (à lier dans les Paramètres)",
                km.getName(), k.getName(), tenue.getName());
        }
        return changees;
    }

    /**
     * Règle de la version 5, pure (le banc la teste sans toucher aux liaisons) : l'ancienne touche « Interaction » est à
     * délier si elle est liée ({@code rActuelle != -1}), encore sur son défaut ({@code rActuelle == rDefaut}) et si
     * Concentration a une touche ({@code concentration != -1}). Codes de {@link #code}.
     */
    public static boolean retraitR(int rActuelle, int rDefaut, int concentration) {
        return rActuelle != -1 && rActuelle == rDefaut && concentration != -1;
    }

    /**
     * Une touche est-elle « encore sur son défaut » au sens de la carte ? Soit la position US du défaut, soit la touche où
     * la version 2 l'a posée : celle qui <b>affiche</b> la lettre du défaut sur ce clavier ({@link #cible}). En BÉPO, la
     * touche US R affiche « o » et le « r » est sur US L ; en Dvorak sur US O ; en Colemak sur US S. Pure (banc).
     */
    public static boolean surDefaut(int actuelle, int defaut, IntFunction<String> nom) {
        if (actuelle == -1) return false;
        if (actuelle == defaut) return true;
        Integer c = cible(defaut, nom);
        return c != null && c == actuelle;
    }

    /** {@link #retraitR} selon le clavier : le défaut de R compte aussi sur la touche qui affiche « r » ({@link #surDefaut}). */
    public static boolean retraitR(int rActuelle, int rDefaut, int concentration, IntFunction<String> nom) {
        return surDefaut(rActuelle, rDefaut, nom) && concentration != -1;
    }

    /**
     * Version 5 : l'ancienne touche « Interaction » (R) est déliée si elle est encore sur son défaut et si Concentration a
     * une touche ({@link #retraitR}) ; sinon elle reste (alias de la Concentration). Renvoie 1 si elle a été déliée.
     * N'enregistre rien : l'appelant fait {@code KeyMapping.resetMapping()} et {@code options.save()}.
     */
    public static int retirerInteraction() {
        return retirerInteraction(k -> GLFW.glfwGetKeyName(k, 0));
    }

    private static int retirerInteraction(IntFunction<String> nom) {
        KeyMapping r = HudKeybinds.INTERACTION, c = HudKeybinds.CONCENTRATION;
        if (r == null || c == null) return 0;
        int ra = code(KeyMappingHelper.getBoundKeyOf(r)), rd = code(r.getDefaultKey()),
            cc = code(KeyMappingHelper.getBoundKeyOf(c));
        if (!retraitR(ra, rd, cc, nom)) {
            if (surDefaut(ra, rd, nom)) LOG.info("touche {} : gardée sur {} (Concentration n'a pas de touche : elle l'ouvre à sa place)",
                r.getName(), KeyMappingHelper.getBoundKeyOf(r).getName());
            else if (ra != -1) LOG.info("touche {} : personnalisée ({}), gardée (alias de la Concentration)",
                r.getName(), KeyMappingHelper.getBoundKeyOf(r).getName());
            return 0;
        }
        InputConstants.Key avant = KeyMappingHelper.getBoundKeyOf(r);
        r.setKey(InputConstants.UNKNOWN);
        LOG.info("touche {} : {} -> aucune (la Concentration, sur {}, ouvre le menu d'interaction)", r.getName(),
            avant.getName(), KeyMappingHelper.getBoundKeyOf(c).getName());
        return 1;
    }

    private static KeyMapping tenuePar(Minecraft mc, InputConstants.Key k, KeyMapping sauf) {
        for (KeyMapping km : mc.options.keyMappings) {
            if (km == sauf || KeyMapping.Category.DEBUG.equals(km.getCategory())) continue;
            if (KeyMappingHelper.getBoundKeyOf(km).equals(k)) return km;
        }
        return null;
    }

    /** Version 1 : anciens défauts → nouvelle carte. */
    private static int anciensDefauts() {
        int changees = 0;
        for (Regle r : REGLES) {
            KeyMapping km = KeyMapping.get(r.nom());
            if (km == null) continue;                                  // mod absent : rien à faire
            InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(km);
            if (k.getType() != InputConstants.Type.KEYSYM || k.getValue() != r.ancien()) continue;   // personnalisée
            InputConstants.Key cible = r.nouveau() < 0 ? InputConstants.UNKNOWN
                : InputConstants.Type.KEYSYM.getOrCreate(r.nouveau());
            if (r.nouveau() >= 0 && occupee(cible, km)) {
                LOG.info("touche {} : {} déjà prise ailleurs, laissée sur {}", r.nom(), cible.getName(), k.getName());
                continue;
            }
            km.setKey(cible);
            changees++;
            LOG.info("touche {} : {} -> {}", r.nom(), k.getName(), r.nouveau() < 0 ? "aucune" : cible.getName());
        }
        return changees;
    }

    /**
     * « Réinitialiser la section » des Paramètres : chaque touche revient à son défaut, puis les touches Reborn (et le
     * lecteur de musique) passent par la règle du clavier comme à la migration (en AZERTY, la Carte revient sur la touche
     * qui affiche « M », pas sur la position US M ; échanges compris). Les touches de Minecraft gardent leur défaut tel
     * quel : ce sont des positions (ZQSD en AZERTY). Enregistre {@code options.txt}.
     */
    public static void reinitialiser(List<KeyMapping> cles) {
        reinitialiser(cles, k -> GLFW.glfwGetKeyName(k, 0));
    }

    private static void reinitialiser(List<KeyMapping> cles, IntFunction<String> nom) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        List<KeyMapping> reborn = new ArrayList<>();
        KeyMapping ost = KeyMapping.get("key.reborn-ost.open_menu");
        for (KeyMapping km : cles) {
            if (km == null) continue;
            km.setKey(km.getDefaultKey());
            if (HudKeybinds.REBORN_KEYS.contains(km) || km == ost) reborn.add(km);
        }
        // version 5 : le défaut de l'ancienne touche Interaction (R) ne revient pas si Concentration a une touche.
        // Avant la règle du clavier : déliée ici, elle n'est plus « sur son défaut » et la version 2 ne la déplace pas
        // sur la touche qui affiche « r » (BÉPO, Dvorak, Colemak) ; sinon (Concentration sans touche) elle y passe.
        if (cles.contains(HudKeybinds.INTERACTION)) retirerInteraction(nom);
        if (!reborn.isEmpty()) clavier(nom, reborn);
        KeyMapping.resetMapping();
        mc.options.save();
        TouchesConflits.invalider();
    }

    /** Version 2 : les touches Reborn restées sur leur défaut passent sur la touche qui affiche la même lettre. */
    static int clavier(Minecraft mc, IntFunction<String> nom) {
        List<KeyMapping> cles = new ArrayList<>(HudKeybinds.REBORN_KEYS);
        cles.add(KeyMapping.get("key.reborn-ost.open_menu"));
        return clavier(nom, cles);
    }

    /**
     * Règle de la version 2 sur les touches {@code cles} (une touche personnalisée n'est jamais touchée), planifiée en
     * une passe ({@link #planifier}) : les échanges entre touches Reborn passent.
     */
    private static int clavier(IntFunction<String> nom, List<KeyMapping> cles) {
        LOG.info("clavier : Q affiche « {} », « ; » affiche « {} », Y affiche « {} »",
            nom.apply(GLFW.GLFW_KEY_Q), nom.apply(GLFW.GLFW_KEY_SEMICOLON), nom.apply(GLFW.GLFW_KEY_Y));
        Minecraft mc = Minecraft.getInstance();
        Map<KeyMapping, Liaison> vues = new IdentityHashMap<>();
        List<Liaison> toutes = new ArrayList<>();
        for (KeyMapping km : mc.options.keyMappings) {
            if (KeyMapping.Category.DEBUG.equals(km.getCategory())) continue;
            InputConstants.Key def = km.getDefaultKey();
            boolean mobile = cles.contains(km) && km.isDefault() && def.getType() == InputConstants.Type.KEYSYM;
            Liaison l = new Liaison(km.getName(), code(KeyMappingHelper.getBoundKeyOf(km)), mobile ? def.getValue() : -1, mobile);
            toutes.add(l);
            vues.put(km, l);
        }
        Map<String, Integer> plan = planifier(toutes, nom);
        int changees = 0;
        for (KeyMapping km : cles) {
            if (km == null || !vues.containsKey(km)) continue;
            Integer c = plan.get(km.getName());
            if (c == null) continue;
            InputConstants.Key t = InputConstants.Type.KEYSYM.getOrCreate(c);
            InputConstants.Key avant = KeyMappingHelper.getBoundKeyOf(km);
            km.setKey(t);
            changees++;
            LOG.info("clavier : {} {} -> {} (affiche « {} »)", km.getName(), avant.getName(), t.getName(), nom.apply(c));
        }
        return changees;
    }

    /**
     * Une liaison vue par {@link #planifier} : nom, touche actuelle ({@link #code}), défaut (code GLFW, nom US) et
     * {@code mobile} (touche à migrer : dans la liste et encore sur son défaut).
     */
    public record Liaison(String nom, int actuelle, int defaut, boolean mobile) { }

    /** Code d'une touche pour {@link Liaison} : valeur GLFW au clavier, souris et scancode à part, -1 = aucune. */
    public static int code(InputConstants.Key k) {
        if (k == null || k.equals(InputConstants.UNKNOWN)) return -1;
        return switch (k.getType()) {
            case KEYSYM -> k.getValue();
            case MOUSE -> -1000 - k.getValue();
            case SCANCODE -> -100000 - k.getValue();
        };
    }

    /**
     * Plan de la règle de la version 2 (pure : le banc la teste sur des claviers simulés) : pour chaque liaison mobile,
     * la touche qui affiche la lettre de son défaut ({@link #cible}), si elle est libre. Une cible tenue par une liaison
     * qui part dans la même passe compte comme libre (QWERTZ neuf : Dégainer US Z → US Y et Concentration US Y → US Z
     * s'échangent) ; deux liaisons qui visent la même touche, ou une cible tenue par une liaison qui reste, et la liaison
     * reste sur place — retiré du plan jusqu'à ce qu'il soit stable. Renvoie nom → code GLFW de la nouvelle touche.
     */
    public static Map<String, Integer> planifier(List<Liaison> toutes, IntFunction<String> nom) {
        Map<String, Integer> plan = new LinkedHashMap<>();
        for (Liaison l : toutes) {
            if (!l.mobile() || l.actuelle() != l.defaut()) continue;
            Integer c = cible(l.defaut(), nom);
            if (c != null && c != l.actuelle()) plan.put(l.nom(), c);
        }
        boolean retire = true;
        while (retire) {
            retire = false;
            for (Map.Entry<String, Integer> e : new ArrayList<>(plan.entrySet())) {
                int t = e.getValue();
                String pris = null;
                for (Liaison l : toutes) {
                    if (l.nom().equals(e.getKey())) continue;
                    Integer va = plan.get(l.nom());
                    if (va != null ? va == t : l.actuelle() == t) { pris = l.nom(); break; }
                }
                if (pris != null) {
                    LOG.info("clavier : {} reste en place (sa cible {} est tenue par {})", e.getKey(), t, pris);
                    plan.remove(e.getKey());
                    retire = true;
                }
            }
        }
        return plan;
    }

    /** Touches imprimables de GLFW (noms US) : espace, ponctuation, chiffres, lettres, « world 1/2 ». */
    private static final int[] IMPRIMABLES;
    static {
        List<Integer> l = new ArrayList<>();
        for (int k : new int[]{32, 39, 59, 61, 91, 92, 93, 96, 161, 162}) l.add(k);
        for (int k = 44; k <= 57; k++) l.add(k);
        for (int k = 65; k <= 90; k++) l.add(k);
        IMPRIMABLES = l.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Ce qu'affiche une touche imprimable sur un clavier QWERTY US (la lettre « voulue » d'un défaut). */
    static String nomUs(int k) {
        if (k >= 65 && k <= 90) return String.valueOf((char) ('a' + k - 65));
        if (k >= 48 && k <= 57) return String.valueOf((char) k);
        return switch (k) {
            case 39 -> "'"; case 44 -> ","; case 45 -> "-"; case 46 -> "."; case 47 -> "/"; case 59 -> ";";
            case 61 -> "="; case 91 -> "["; case 92 -> "\\"; case 93 -> "]"; case 96 -> "`";
            default -> null;
        };
    }

    /**
     * Touche à utiliser pour un défaut {@code defaut} (nom US) sur le clavier décrit par {@code nom} (ce qu'affiche
     * chaque touche), ou {@code null} s'il n'y a rien à changer. Pure : testée par le banc sur des dispositions simulées.
     */
    public static Integer cible(int defaut, IntFunction<String> nom) {
        String voulu = nomUs(defaut);
        if (voulu == null) return null;                                  // non imprimable : indépendant du clavier
        if (voulu.equalsIgnoreCase(nom.apply(defaut))) return null;      // affiche déjà la bonne lettre
        for (int k : IMPRIMABLES) if (voulu.equalsIgnoreCase(nom.apply(k))) return k;
        // AZERTY : « . » n'existe qu'avec Maj, sur la touche gravée « ; . » (US « , ») → on prend celle-là
        if (".".equals(voulu) && "a".equalsIgnoreCase(nom.apply(GLFW.GLFW_KEY_Q))) {
            for (int k : IMPRIMABLES) if (";".equals(nom.apply(k))) return k;
        }
        return null;
    }

    /**
     * Une autre touche (hors {@code sauf}) est-elle déjà liée à {@code k} ? Les raccourcis de débogage (F3 + touche,
     * catégorie DEBUG : F3+G, F3+N…) ne comptent pas, ils ne se déclenchent qu'avec F3.
     */
    private static boolean occupee(InputConstants.Key k, KeyMapping sauf) {
        return tenuePar(Minecraft.getInstance(), k, sauf) != null;
    }
}
