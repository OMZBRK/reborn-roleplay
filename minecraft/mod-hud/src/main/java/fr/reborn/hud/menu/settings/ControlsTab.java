package fr.reborn.hud.menu.settings;

import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.keybind.TouchesConflits;
import fr.reborn.hud.keybind.TouchesMigration;
import fr.reborn.hud.keybind.TouchesPlasmo;
import fr.reborn.hud.menu.widget.KeybindButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Onglet COMMANDES des Paramètres (lot 3, gabarit o2 et planche t2 du 05/11, CADRAGE §8.3) : toutes les touches par
 * <b>sous-catégories</b> ({@link SousListe} : la sous-liste à gauche, les lignes de la sous-catégorie choisie à droite,
 * « Réinitialiser la section » dans l'en-tête). Une ligne = libellé explicite, aide d'une phrase, case de touche
 * ({@link KeybindButton} : clic → écoute la touche suivante, Échap = aucune).
 *
 * <ul>
 *   <li>Touches de Minecraft utiles à leur place (Avancer, Jeter, Tchat…), touches Reborn, et les touches de <b>Plasmo
 *       Voice fusionnées</b> avec les nôtres (Couper le micro, Couper le son, Appuyer pour parler, Parler à la radio :
 *       une seule liaison par action, {@link TouchesPlasmo}) ; sans Plasmo Voice, ses lignes sont masquées.</li>
 *   <li>Touches inutiles de Minecraft <b>cachées</b> ({@link TouchesMigration#CACHEES}, déliées une fois par la
 *       migration 4) ; le reste (Choisir le bloc, touches des autres mods) dans « Minecraft et mods ».</li>
 *   <li><b>Conflit</b> (planche t2-b2) : les deux cases passent en vermillon ({@link TouchesConflits}) et l'aide dit
 *       avec quelle action ; raccourcis de Plasmo non fusionnés compris.</li>
 * </ul>
 * Les binds Reborn vivent aussi dans l'écran vanilla « Commandes » sous la catégorie « Reborn »
 * ({@code RebornKeyCategory}).
 */
public class ControlsTab extends SectionedTab implements SousListe {

    /** Largeur de la case de touche (les noms longs, « Entrée », « Pg suiv », sont abrégés par la case). */
    private static final int KEY_BTN_W = 72;

    private static final int DEPLACEMENT = 0, COMBAT = 1, MONDE = 2, VOIX = 3, ANIMATIONS = 4, MENUS = 5, IMAGES = 6,
        SOURIS = 7, MINECRAFT = 8, STAFF = 9;
    private static final String[] SOUS = {"Déplacement", "Combat", "Monde", "Voix et escouade", "Animations", "Menus",
        "Images", "Souris", "Minecraft et mods", "Staff"};

    /** Sous-catégorie affichée, gardée d'une ouverture à l'autre. */
    private static int choisie = DEPLACEMENT;

    @SuppressWarnings("unused")
    private final Screen parent;

    public ControlsTab(Screen parent) {
        this.parent = parent;
    }

    /** Une ligne de touche de la sous-catégorie. */
    private record Ligne(KeyMapping km, String label, String aide) { }

    /**
     * Lignes de chaque sous-catégorie, en cache : {@code build()} est rejoué à chaque image (passe de rendu) et « Minecraft
     * et mods » parcourt toutes les autres. Les lignes ne dépendent que des objets {@link KeyMapping} (fixes après le
     * démarrage) et de la langue (libellés des touches des autres mods) : le cache est refait quand l'un d'eux change.
     */
    @SuppressWarnings("unchecked")
    private static final List<Ligne>[] CACHE = new List[SOUS.length];
    private static Options cacheOptions;
    private static String cacheLangue;
    private static int cacheNombre = -1;

    /**
     * Libellé d'une touche tel qu'affiché dans Commandes (« Contextuel » plutôt que le nom de langue), ou {@code null}
     * si elle n'y figure pas. Sert au texte des conflits ({@code TouchesConflits}, recalculé rarement, mis en cache).
     */
    public static String libelleDe(KeyMapping km) {
        Options o = net.minecraft.client.Minecraft.getInstance().options;
        if (km == null || o == null) return null;
        // « Minecraft et mods » est sauté : ses libellés viennent justement de TouchesConflits.libelle (la langue)
        for (int s = 0; s < SOUS.length; s++) {
            if (s == MINECRAFT) continue;
            for (Ligne l : lignes(s, o)) if (l.km() == km) return l.label();
        }
        return null;
    }

    /** Banc des touches ({@code REBORN_TOUCHES_DEBUG}) : sous-catégorie montrée à la prochaine ouverture. */
    public static void bancSous(int i) {
        if (i >= 0 && i < SOUS.length) choisie = i;
    }

    // ─── SousListe ──────────────────────────────────────
    @Override public int nombreSous() { return SOUS.length; }
    @Override public String nomSous(int i) { return SOUS[i]; }
    @Override public int sousChoisie() { return choisie; }
    @Override public void choisirSous(int i) { if (i >= 0 && i < SOUS.length) choisie = i; }
    @Override public boolean reinitialisable() { return true; }

    @Override
    public boolean sousVisible(int i) {
        if (i == STAFF) return staff() && touche("key.reborn-hud.staff_panel") != null;
        return true;
    }

    /** Staff : rôle connu du launcher, ou mode staff / animation actif en jeu. */
    private static boolean staff() {
        return fr.reborn.hud.menu.RebornBranding.isStaff() || fr.reborn.hud.staff.StaffClient.modeActif();
    }

    /** Remet la sous-catégorie affichée à ses défauts (touches, et réglages de la souris et du déplacement). */
    @Override
    public void reinitialiserSous() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;
        List<KeyMapping> cles = new ArrayList<>();
        for (Ligne l : lignes(choisie, o)) cles.add(l.km());
        if (!cles.isEmpty()) TouchesMigration.reinitialiser(cles);
        // la ligne « Interaction » dépend de sa liaison (migration 5 rejouée par la réinitialisation) : listes à refaire
        invaliderCache();
        if (choisie == DEPLACEMENT) {
            o.autoJump().set(false);
            o.toggleSprint().set(false);
            o.toggleCrouch().set(false);
            o.save();
        } else if (choisie == SOURIS) {
            o.sensitivity().set(0.5);
            o.invertMouseY().set(false);
            o.save();
        }
    }

    // ─── Contenu ────────────────────────────────────────
    @Override
    protected void build() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;
        int s = choisie >= 0 && choisie < SOUS.length && sousVisible(choisie) ? choisie : DEPLACEMENT;

        for (Ligne l : lignes(s, o)) ligne(l);

        if (s == DEPLACEMENT) {
            row("Saut automatique", "Grimpe les blocs sans appuyer sur Sauter.",
                (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy, o.autoJump().get(),
                    v -> { o.autoJump().set(v); o.save(); }));
            row("Sprint en bascule", "Un appui suffit au lieu de maintenir.",
                (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy, o.toggleSprint().get(),
                    v -> { o.toggleSprint().set(v); o.save(); }));
            row("Accroupi en bascule", "Un appui suffit au lieu de maintenir.",
                (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy, o.toggleCrouch().get(),
                    v -> { o.toggleCrouch().set(v); o.save(); }));
        } else if (s == COMBAT) {
            valueRow("Emplacements 1 à 9", "Une touche par case de la barre.", emplacements(o), fr.reborn.hud.menu.Colors.WHITE_PURE);
        } else if (s == SOURIS) {
            // Sensibilité : Minecraft stocke 0..1 où 1 = « HYPERSPEED » (×2).
            int sensPct = (int) Math.round(o.sensitivity().get() * 200);
            row("Sensibilité", "Vitesse de la caméra à la souris.",
                (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24, sensPct, 0, 200, "%",
                    v -> { o.sensitivity().set(v / 200.0); Enregistrement.options(); }));
            row("Inverser l'axe vertical", "Haut et bas de la souris inversés.",
                (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy, o.invertMouseY().get(),
                    v -> { o.invertMouseY().set(v); o.save(); }));
        }
        spacer(4);
    }

    /** Une ligne de touche : en conflit, l'aide dit avec quelle action (planche t2-b2). */
    private void ligne(Ligne l) {
        KeyMapping km = l.km();
        String conflit = TouchesConflits.avec(km);
        String aide = conflit != null ? "Déjà prise par " + conflit + " : choisis une autre touche." : l.aide();
        aideAlerte = conflit != null;
        row(l.label(), aide, (cx, cy, cw) -> {
            int w = Math.min(KEY_BTN_W, cw);
            return new KeybindButton(cx + cw - w, cy, w, 24, km);
        });
    }

    /**
     * Listes refaites au prochain {@code build()} (la ligne « Interaction » dépend de sa liaison et de celle de
     * Concentration) : appelée par « Réinitialiser la section » et par {@link KeybindButton} quand l'une des deux change.
     */
    public static void invaliderCache() {
        java.util.Arrays.fill(CACHE, null);
    }

    /** Touches de la sous-catégorie {@code s} (en cache, voir {@link #CACHE}). */
    private static List<Ligne> lignes(int s, Options o) {
        if (o == null) return List.of();
        String langue = o.languageCode;
        if (o != cacheOptions || o.keyMappings.length != cacheNombre || !java.util.Objects.equals(langue, cacheLangue)) {
            java.util.Arrays.fill(CACHE, null);
            cacheOptions = o;
            cacheNombre = o.keyMappings.length;
            cacheLangue = langue;
        }
        List<Ligne> l = CACHE[s];
        if (l == null) {
            l = construire(s, o);
            CACHE[s] = l;
        }
        return l;
    }

    /** Touches de la sous-catégorie {@code s}, dans l'ordre d'affichage (les touches absentes sont sautées). */
    private static List<Ligne> construire(int s, Options o) {
        List<Ligne> l = new ArrayList<>();
        boolean pv = TouchesPlasmo.present();
        switch (s) {
            case DEPLACEMENT -> {
                add(l, o.keyUp, "Avancer", null);
                add(l, o.keyDown, "Reculer", null);
                add(l, o.keyLeft, "Gauche", null);
                add(l, o.keyRight, "Droite", null);
                add(l, o.keyJump, "Sauter", "Maintenir avec S'accroupir : saut de chakra.");
                add(l, o.keyShift, "S'accroupir", null);
                add(l, o.keySprint, "Allures", "Maintenir : trot. Deux appuis : sprint.");
                add(l, HudKeybinds.COURSE_CHAKRA, "Course de chakra", "Un appui la lance, un autre l'arrête.");
                add(l, HudKeybinds.FREELOOK, "Regard libre", "Maintenir : la caméra tourne autour de toi.");
            }
            case COMBAT -> {
                add(l, o.keyAttack, "Attaquer", null);
                add(l, o.keyUse, "Utiliser", "Objet en main, porte, bloc.");
                add(l, HudKeybinds.PARRY, "Parer", "Maintenir : garde. Au bon moment : parade.");
                add(l, HudKeybinds.DASH, "Dash", "Un bond dans la direction de la marche.");
                add(l, HudKeybinds.DEGAINER, "Dégainer", "Sortir ou ranger l'arme de l'étui.");
                add(l, o.keySwapOffhand, "Main secondaire", "Partagée avec Contextuel, qui ne la prend que s'il y a quelque chose à faire.");
            }
            case MONDE -> {
                add(l, HudKeybinds.CONCENTRATION, "Concentration", "Maintenir : lire les plaques, viser un joueur ou un objet et cliquer pour agir. Relâcher pour revenir.");
                add(l, HudKeybinds.PICKUP, "Contextuel", "Ramasser, prendre ce qu'on te tend : seulement s'il y a quelque chose à faire.");
                // Ancienne touche du menu d'interaction : montrée seulement si elle a encore une touche (gardée par la
                // migration 5 : Concentration sans touche, ou touche personnalisée) ; elle ouvre la Concentration.
                if (HudKeybinds.INTERACTION != null && !HudKeybinds.INTERACTION.isUnbound()) {
                    add(l, HudKeybinds.INTERACTION, "Interaction", HudKeybinds.CONCENTRATION.isUnbound()
                        ? "Ancienne touche : à maintenir pour la Concentration, qui n'a pas de touche."
                        : "Ancienne touche : se maintient comme la Concentration. Échap pour la retirer.");
                }
                add(l, o.keyDrop, "Jeter", "Jeter l'objet en main ; avec Ctrl, toute la pile.");
                add(l, HudKeybinds.DUEL_ACCEPT, "Accepter", "Une invitation, un duel, une demande.");
                add(l, HudKeybinds.DUEL_REFUSE, "Refuser", null);
            }
            case VOIX -> {
                add(l, HudKeybinds.VOIX_PORTEE, "Portée de la voix", "Un cran plus bas à chaque appui : criée, normale, chuchotée.");
                add(l, HudKeybinds.RADIO_PARLER, "Parler à la radio", "Maintenir : parler à l'escouade. Appui bref : écran de l'escouade.");
                if (pv) {
                    add(l, HudKeybinds.MICRO, "Couper le micro", "Plus personne ne t'entend ; un autre appui le rétablit.");
                    add(l, HudKeybinds.SON, "Couper le son", "Tu n'entends plus les voix ; un autre appui les rétablit.");
                    add(l, HudKeybinds.PARLER, "Appuyer pour parler", "Seulement si la voix est réglée sur « Appuyer » dans Plasmo Voice.");
                    add(l, TouchesPlasmo.reglages(), "Réglages de la voix", "Le menu de Plasmo Voice.");
                }
                add(l, o.keyChat, "Tchat", "Parler en RP.");
                add(l, o.keyCommand, "Commande", "Ouvre le tchat sur « / ».");
                add(l, o.keyPlayerList, "Liste des joueurs", "Qui est en ligne.");
            }
            case ANIMATIONS -> add(l, HudKeybinds.ANIMATIONS, "Menu des animations",
                "Démarches et émotes ; la même touche arrête l'animation.");
            case MENUS -> {
                add(l, o.keyInventory, "Sacoche", "Ton sac ; l'inventaire de Minecraft hors du serveur RP.");
                add(l, HudKeybinds.STATS, "Fiche", "Ta fiche shinobi ; la même touche la referme.");
                add(l, HudKeybinds.MAP, "Carte", "Les lieux du monde.");
                add(l, HudKeybinds.FACTION, "Faction", "Ta section, ton village, les annonces ; la même touche referme.");
                add(l, touche("key.reborn-ost.open_menu"), "Lecteur de musique", "Les musiques du serveur.");
            }
            case IMAGES -> {
                add(l, o.keyTogglePerspective, "Vue", "Épaule ou 1re personne ; côté et cadrage : Interface › Caméra.");
                add(l, HudKeybinds.CINEMA, "Cinéma", "Bandes de cinéma, sans HUD.");
                add(l, HudKeybinds.PHOTO, "Mode photo", "La galerie s'ouvre depuis le mode photo.");
                add(l, o.keyScreenshot, "Capture d'écran", null);
                add(l, o.keyFullscreen, "Plein écran", null);
            }
            case MINECRAFT -> {
                add(l, o.keyPickItem, "Choisir le bloc", null);
                // touches des autres mods (Emotecraft, EntityCulling…) et de Minecraft que nulle autre ligne ne montre
                Set<KeyMapping> montrees = Collections.newSetFromMap(new IdentityHashMap<>());
                for (int i = 0; i < SOUS.length; i++) if (i != MINECRAFT) for (Ligne x : lignes(i, o)) montrees.add(x.km());
                montrees.add(o.keyPickItem);
                for (KeyMapping hb : o.keyHotbarSlots) montrees.add(hb);
                for (KeyMapping km : o.keyMappings) {
                    if (montrees.contains(km) || KeyMapping.Category.DEBUG.equals(km.getCategory())
                        || TouchesMigration.CACHEES.contains(km.getName()) || HudKeybinds.REBORN_KEYS.contains(km)
                        || km.getName().startsWith("key.plasmovoice") || km.getName().equals("key.reborn-hud.staff_panel")) continue;
                    // aucune autre ligne ne la montre : son libellé est celui de la langue (pas de détour par libelleDe)
                    add(l, km, net.minecraft.network.chat.Component.translatable(km.getName()).getString(), null);
                }
            }
            case STAFF -> add(l, touche("key.reborn-hud.staff_panel"), "Poste de garde", "Ouvre le poste de garde (staff seulement).");
            default -> { }
        }
        return l;
    }

    private static void add(List<Ligne> l, KeyMapping km, String label, String aide) {
        if (km != null) l.add(new Ligne(km, label, aide));
    }

    private static KeyMapping touche(String nom) {
        return KeyMapping.get(nom);
    }

    /** « 1 2 3 4 5 6 7 8 9 » tels que le clavier du joueur les nomme. */
    private static String emplacements(Options o) {
        StringBuilder sb = new StringBuilder();
        for (KeyMapping km : o.keyHotbarSlots) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(HudKeybinds.nom(km, "—"));
        }
        return sb.toString();
    }
}
