package fr.reborn.hud.audit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.TexteCache;
import fr.reborn.hud.ui.TexteCache.Style;
import fr.reborn.hud.ui.TexteCache.Texte;
import fr.reborn.hud.ui.TexteRaster;
import fr.reborn.hud.ui.TexteRaster.Ancre;
import fr.reborn.hud.ui.TexteRaster.Demande;
import fr.reborn.hud.ui.TexteRaster.Masque;
import fr.reborn.hud.ui.TexteRaster.Mise;
import fr.reborn.hud.ui.TexteRaster.Police;
import fr.reborn.hud.ui.TexteRaster.Preset;
import fr.reborn.hud.ui.TexteRaster.Rendu;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Banc du moteur de texte du lot 2, <b>inactif en production</b> ({@code REBORN_TEXTE_BANC=1}, au menu principal, sans
 * monde ni serveur ; {@code =couches} : l'étape 1 seule). Contrôle du brief §6 et des risques 1 à 3 du LISEZMOI v2 :
 * <ol>
 *   <li><b>Couches</b> : chaque cas (texte d'essai des maquettes en Alegreya Sans Bold Italic RÉCIT à f = 1 et 1,5,
 *       jeux de typographie communs, lignes du HUD en Inter Bold capitales 12 et 10, Inter Medium, noms de qui parle,
 *       formes du HUD) est rendu par {@link TexteRaster} et écrit en PNG RGBA dans {@code run/texte_banc/<cas>.png},
 *       avec {@code manifest.json} (entrées exactes, position de la couche, mise en page). {@code tools/texte_banc_compare.py}
 *       rend les mêmes entrées avec {@code _texte_raster.py} et mesure l'écart d'alpha (cible : moyen &lt; 1/255,
 *       max &lt; 16/255).</li>
 *   <li><b>Écran</b> : les mêmes couches passées par {@link TexteCache} (texture, blit à 1:1, lecture au plus proche)
 *       sur un fond uni sombre puis clair → {@code run/screenshots/texte_banc_{sombre,clair}.png}, que le script
 *       compare aux couches posées sur le même fond.</li>
 *   <li><b>Endurance</b> : 1000 rendus sur le fil du texte, puis par le cache 50 redimensionnements simulés (pas de
 *       200 ms), une rafale de 10 changements d'échelle en 50 ms (un seul recalcul attendu), 1000 changements de
 *       texte, LRU + 8 lignes de plus pour faire tourner la LRU, puis tout est libéré et FreeType fermé : les compteurs
 *       doivent montrer chaque glyphe FreeType rendu, chaque face et la bibliothèque fermées, chaque texture libérée.</li>
 * </ol>
 * Rapport : {@code run/texte_banc/rapport.txt} et le journal ({@code [texte-banc] OK} / {@code ECHEC}), puis le jeu se
 * ferme. Copie : {@code Maquettes/gameplan/17_lot2_ketto/code/}.
 */
public final class TexteBanc {

    private TexteBanc() {}

    public static void init() {
        String v = System.getenv("REBORN_TEXTE_BANC");
        if (v == null || v.isBlank() || "0".equals(v)) return;
        couchesSeules = "couches".equals(v.trim());
        ClientTickEvents.END_CLIENT_TICK.register(TexteBanc::tick);
    }

    /* ================================================================ entrées communes (maquettes v2) */

    /** Texte d'essai des maquettes ({@code _maquettes_lot2.TEXTE}), saisi comme un staff le tape. */
    static final String TEXTE = "Écoutez. Les <ombre>ombres</> s'étirent sur Konoha. Ce soir, devant le Hokage, vous "
        + "prêtez <or>serment</> : servir sans gloire, veiller jusqu'à la <rouge>mort</>. Vos noms s'effacent ; il ne "
        + "reste que la lame et la nuit, et nul ne verra vos visages. Que le <rouge>feu</> de la Feuille garde votre "
        + "cœur. Ren Hyūga, Aki Uchiha : à l'aube, l'escouade <or>Kagerō</> part vers l'Est. \"Aujourd'hui comme à "
        + "jamais\"... Ça commence ici.";
    static final String TEXTE_COURT = "Le vent tourne. Quelque chose approche de la <rouge>porte Est</>.";
    static final String TITRE_LONG = "Les ombres s'allongent sur la Feuille";

    static final int BLANC_HUD = 0xF3F0EA, VERT = 0x3FE05A, OR = 0xD9A95E, OR_CLAIR = 0xF2D08A;

    record Cas(String nom, Demande d, Masque masque, int rgb, int x, int y) {
        static Cas texte(String nom, Demande d) { return new Cas(nom, d, null, 0, 0, 0); }

        /** Forme (préréglage FORME, facteur f = d.f()) posée avec le coin du masque en (x, y). */
        static Cas forme(String nom, Masque m, int rgb, double f, int x, int y) {
            return new Cas(nom, new Demande("", Police.RECIT, Preset.FORME, 0, 0, 0, f, x, y, Ancre.GAUCHE, 0, "", false,
                true, false, TexteRaster.Equilibrage.JAMAIS, null), m, rgb, x, y);
        }
    }

    private static Demande bloc(String t, double f, double cx, double cy) {
        return Demande.bloc(t, Police.RECIT, Preset.RECIT, TexteRaster.capRecit(f), TexteRaster.largeurRecit(f),
            TexteRaster.pasRecit(f), f, cx, cy);
    }

    private static Demande ligne(String t, Style s, double cap, double f, double inter, double x, double yc) {
        return Demande.ligne(t, s.police(), s.preset(), cap, x, yc, s.ancre(), f, inter, s.couleur(), s.typo(),
            s.centrageEncre());
    }

    static final List<Cas> CAS = List.of(
        Cas.texte("recit_f1_essai", bloc(TEXTE, 1.0, 960.0, 0.54 * 1080)),
        Cas.texte("recit_f15_essai", bloc(TEXTE, 1.5, 1280.0, 0.54 * 1440)),
        Cas.texte("recit_f1_court", bloc(TEXTE_COURT, 1.0, 960.0, 0.54 * 1080)),
        Cas.texte("recit_f1_demi_pixel", bloc(TEXTE_COURT, 1.0, 682.5, 0.54 * 767)),
        Cas.texte("typo_guillemets", bloc("Il a dit \"bonjour\"... puis : quoi ? Rien ! L'ombre , la nuit ; le feu "
            + "(enfin) . Fin...", 1.0, 960.0, 300.0)),
        Cas.texte("typo_insecables", bloc("Ce soir à l'aube, Ren Hyūga et Aki Uchiha partent de la Feuille vers le "
            + "pays~du~Feu avec Kozue Nara.", 1.0, 960.0, 300.0)),
        Cas.texte("typo_espaces", bloc("« Bien » ; non : oui ! Vraiment ? Ah… d'accord. Rendez-vous à 12:30 , "
            + "\" sans faute \" !", 1.0, 960.0, 300.0)),
        Cas.texte("eq_plancher_94", bloc("La pluie bat les toits de Konoha depuis l'aube. Les patrouilles reviennent une "
            + "à une, trempées, silencieuses, et personne n'ose demander ce qu'elles ont vu au-delà du fleuve. Les "
            + "anciens disent que la brume cache toujours quelque chose. Ce soir, elle cache la <rouge>forêt</> "
            + "entière. Restez prêts : la nuit sera longue.", 1.0, 960.0, 583.2)),
        Cas.texte("eq_sans", bloc("Les lanternes s'allument une à une le long de la rue principale, et le marché se "
            + "tait enfin.", 1.0, 960.0, 300.0)),
        Cas.texte("couleurs_balises", bloc("<rouge>Rouge</> <or>or</> <ombre>ombre</> <gris>gris</> <blanc>blanc</> "
            + "<bleu>bleu</> <rouge>fin</rouge> normal.", 1.0, 960.0, 300.0)),
        Cas.texte("retours_forces", bloc("Première ligne.\nDeuxième ligne, un peu plus longue.\n\nAprès une ligne vide.",
            1.0, 960.0, 300.0)),
        Cas.texte("hud_titre_ost", ligne("Mystère", Style.TITRE_OST, 12.0, 1.0, 0.0, 1752.0, 26.0)),
        Cas.texte("hud_titre_long", ligne(TITRE_LONG, Style.TITRE_OST, 12.0, 1.0, 0.0, 1606.0, 60.0)),
        Cas.texte("hud_libelle_portee", ligne("Portée de la voix", Style.LIBELLE, 12.0, 1.0, 0.0, 1860.0, 1044.0)),
        Cas.texte("hud_libelle_animation", ligne("Arrêter l'animation", Style.LIBELLE, 12.0, 1.0, 0.0, 1860.0, 1017.0)),
        Cas.texte("hud_touche_h", ligne("H", Style.TOUCHE, 10.0, 1.0, 0.0, 1885.0, 1044.0)),
        Cas.texte("hud_touche_point", ligne(".", Style.TOUCHE_PONCT, 10.0, 1.0, 0.0, 1885.0, 1017.0)),
        Cas.texte("hud_touche_maj", ligne("Maj", Style.TOUCHE, 10.0, 1.0, 0.0, 1880.5, 990.0)),
        Cas.texte("notif_principal", ligne("+120 ryo", Style.NOTIF, 10.0, 1.0, 0.0, 1860.0, 952.0)),
        Cas.texte("notif_detail", ligne("— de Ren Hyūga", Style.NOTIF_DETAIL, 10.0, 1.0, 0.0, 1801.37, 952.0)),
        Cas.texte("qui_parle_nom", ligne("REN HYŪGA", Style.NOM, 12.0, 1.0, 1.0, 1731.37, 809.0)),
        Cas.texte("qui_parle_inconnu", ligne("INCONNU", Style.NOM_INCONNU, 12.0, 1.0, 1.0, 1752.81, 782.0)),
        Cas.texte("hud15_libelle", ligne("Portée de la voix", Style.LIBELLE, 18.0, 1.5, 0.0, 1503.0, 400.0)),
        Cas.texte("hud15_nom", ligne("AKI UCHIHA", Style.NOM, 18.0, 1.5, 1.5, 1290.64, 460.0)),
        Cas.forme("forme_puce", Masque.puce(4), VERT, 1.0, 1700, 830),
        Cas.forme("forme_barres_ost", Masque.barres(new int[] {6, 10, 8}, 2, 4), BLANC_HUD, 1.0, 1700, 16),
        Cas.forme("forme_vumetre", Masque.barres(new int[] {3, 5, 6, 4, 2}, 1, 4), BLANC_HUD, 1.0, 1820, 806),
        Cas.forme("forme_losange", Masque.losange(2), OR_CLAIR, 1.0, 1834, 44),
        Cas.forme("forme_filet", Masque.filet(113, 1, true), OR, 1.0, 1783, 46),
        Cas.forme("forme_pause", Masque.pause(1, 5), BLANC_HUD, 1.0, 1760, 44),
        Cas.forme("forme_puce_f15", Masque.puce(6), VERT, 1.5, 1200, 520),
        Cas.forme("forme_barres_f15", Masque.barres(new int[] {9, 15, 12}, 3, 6), BLANC_HUD, 1.5, 1240, 505));

    /* ================================================================ déroulé */

    private static final StringBuilder RAPPORT = new StringBuilder();
    /** {@code REBORN_TEXTE_BANC=couches} : les couches et le manifeste seulement, sans écran ni endurance. */
    private static boolean couchesSeules;
    private static int phase, t, etape, attente, echecs;
    private static long debut;
    private static Path dossier;
    private static CompletableFuture<?> travail;
    private static final List<Texte> AFFICHES = new ArrayList<>();
    private static int fond = 0xFF141012;

    private static void log(String s) {
        System.out.println("[texte-banc] " + s);
        RAPPORT.append(s).append('\n');
    }

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        log((ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void tick(Minecraft mc) {
        t++;
        if (phase > 0 && t > 20 * 60 * 10) {   // garde-fou : 10 min
            verifie(false, "banc interrompu (phase " + phase + ", étape " + etape + ")");
            finir(mc);
            return;
        }
        try {
            switch (phase) {
                case 0 -> {
                    if (mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null) {
                        phase = 1;
                        t = 0;
                    }
                }
                case 1 -> {
                    if (t == 20) demarrer(mc);
                }
                case 2 -> {
                    if (travail.isDone()) {
                        if (couchesSeules) {
                            travail.join();
                            finir(mc);
                        } else ecran(mc);
                    }
                }
                case 3 -> captures(mc);
                case 4 -> {
                    if (travail.isDone()) {
                        phase = 5;
                        etape = 0;
                        attente = 0;
                    }
                }
                case 5 -> endurance(mc);
                case 6 -> {
                    if (travail.isDone()) bilanFinal(mc);
                }
                case 7 -> {
                    if (--attente <= 0) mc.stop();
                }
                default -> { }
            }
        } catch (Throwable e) {
            verifie(false, "exception : " + e);
            e.printStackTrace();
            finir(mc);
        }
    }

    private static void finir(Minecraft mc) {
        ecrireRapport();
        phase = 7;
        attente = 40;
    }

    /* ---------------------------------------------------------------- 1. couches */

    private static void demarrer(Minecraft mc) throws IOException {
        dossier = mc.gameDirectory.toPath().resolve("texte_banc");
        Files.createDirectories(dossier);
        debut = System.nanoTime();
        log("Banc TexteRaster v1.1 · reborn-hud · " + mc.getWindow().getWidth() + "×" + mc.getWindow().getHeight()
            + " · échelle GUI " + mc.getWindow().getGuiScale());
        mc.setScreenAndShow(new Ecran());
        phase = 2;
        travail = TexteCache.executer(() -> {
            JsonArray cas = new JsonArray();
            for (Cas c : CAS) cas.add(ecrireCas(c));
            JsonObject m = new JsonObject();
            m.addProperty("moteur", "TexteRaster v1.1 (Java)");
            m.addProperty("freetype", TexteRaster.versionFreeType());
            m.addProperty("fenetre_l", Minecraft.getInstance().getWindow().getWidth());
            m.addProperty("fenetre_h", Minecraft.getInstance().getWindow().getHeight());
            m.add("cas", cas);
            try {
                Files.writeString(dossier.resolve("manifest.json"), new GsonBuilder().setPrettyPrinting()
                    .disableHtmlEscaping().create().toJson(m), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            log("couches : " + CAS.size() + " cas écrits dans " + dossier + " (FreeType " + TexteRaster.versionFreeType()
                + ")");
            return null;
        });
    }

    /** Fil du texte : rendu d'un cas, PNG et entrée du manifeste. */
    private static JsonObject ecrireCas(Cas c) {
        long t0 = System.nanoTime();
        Rendu r = c.masque() != null ? TexteRaster.forme(c.masque(), c.rgb(), c.d().f()) : TexteRaster.rendre(c.d());
        double ms = (System.nanoTime() - t0) / 1e6;
        int x = r.x + (c.masque() != null ? c.x() : 0), y = r.y + (c.masque() != null ? c.y() : 0);
        try (NativeImage img = TexteCache.image(r)) {
            img.writeToFile(dossier.resolve(c.nom() + ".png"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Demande d = c.d();
        JsonObject o = new JsonObject();
        o.addProperty("nom", c.nom());
        o.addProperty("fichier", c.nom() + ".png");
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("w", r.w);
        o.addProperty("h", r.h);
        o.addProperty("ms", ms);
        o.addProperty("ecran", surEcran(c));
        if (c.masque() != null) {
            Masque m = c.masque();
            o.addProperty("genre", "forme");
            o.addProperty("f", d.f());
            o.addProperty("r", d.f());
            o.addProperty("d", Math.max(1, TexteRaster.arrondi(d.f())));
            o.addProperty("contour", (double) Preset.FORME.contour);
            o.addProperty("ombre", (double) Preset.FORME.ombre);
            o.addProperty("alpha", 1.0);
            JsonArray rgb = new JsonArray();
            rgb.add((c.rgb() >> 16) & 0xFF);
            rgb.add((c.rgb() >> 8) & 0xFF);
            rgb.add(c.rgb() & 0xFF);
            o.add("rgb", rgb);
            o.addProperty("mx", c.x());
            o.addProperty("my", c.y());
            o.addProperty("mw", m.w());
            o.addProperty("mh", m.h());
            JsonArray v = new JsonArray();
            for (float f : m.v()) v.add((double) f);
            o.add("masque", v);
            return o;
        }
        o.addProperty("genre", d.ligne() ? "ligne" : "bloc");
        o.addProperty("police", switch (d.police()) {
            case RECIT -> "alegreya_sans_bi";
            case INTER_GRAS -> "inter_bold";
            case INTER_MEDIUM -> "inter_medium";
            case PHILOSOPHER -> "philosopher_b";
        });
        o.addProperty("texte", d.texte());
        o.addProperty("preset", d.preset().name());
        o.addProperty("cap", d.cap());
        o.addProperty("largeur_max", d.largeurMax());
        o.addProperty("pas", d.pas());
        o.addProperty("f", d.f());
        o.addProperty("cx", d.cx());
        o.addProperty("cy", d.cy());
        o.addProperty("ancre", d.ancre() == Ancre.GAUCHE ? "g" : d.ancre() == Ancre.CENTRE ? "c" : "d");
        o.addProperty("interlettre", d.interlettre());
        o.addProperty("couleur_defaut", d.couleurDefaut());
        o.addProperty("typo", d.typo());
        o.addProperty("centrage", d.centrageEncre() ? "encre" : "cap");
        o.addProperty("equilibrer", d.equilibrage().name().toLowerCase(Locale.ROOT));
        Mise m = r.mise;
        o.addProperty("em", m.em);
        o.addProperty("calage", m.calage);
        o.addProperty("capitales", m.capitales);
        o.addProperty("hauteur_x", m.hauteurX);
        o.addProperty("nb_lignes", m.nbLignes);
        o.addProperty("largeur_bloc", m.largeurBloc);
        o.addProperty("equilibrage", m.equilibrage);
        o.addProperty("espace_mot", m.espaceMot);
        o.addProperty("espace_fine", m.espaceFine);
        o.addProperty("insecables_auto", m.insecablesAuto);
        o.addProperty("paires_crenees", m.pairesCrenees);
        o.addProperty("haut", m.haut == Integer.MIN_VALUE ? null : m.haut);
        o.addProperty("manquants", m.manquants);
        JsonArray lignes = new JsonArray(), bases = new JsonArray(), larg = new JsonArray();
        for (int i = 0; i < m.nbLignes; i++) {
            lignes.add(m.lignes[i]);
            bases.add(m.bases[i]);
            larg.add(m.largeurs[i]);
        }
        o.add("lignes", lignes);
        o.add("bases", bases);
        o.add("largeurs", larg);
        return o;
    }

    /* ---------------------------------------------------------------- 2. écran (texture, blit 1:1) */

    /** Écran du banc : un fond uni, puis les poignées affichées. */
    static final class Ecran extends Screen {
        Ecran() { super(Component.literal("banc texte")); }

        @Override
        public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float d) { }

        @Override
        public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, fond);
            DaKit.debutPx(ctx);
            for (Texte x : AFFICHES) x.dessinerPx(ctx, 1f);
            DaKit.finPx(ctx);
        }

        @Override
        public boolean shouldCloseOnEsc() { return false; }

        @Override
        public boolean isPauseScreen() { return false; }
    }

    /** Cas posés à l'écran en 1920×1080 (sans chevauchement) : narration f = 1, HUD et formes à l'échelle 1. */
    static boolean surEcran(Cas c) {
        String n = c.nom();
        return !(n.contains("f15") || n.contains("demi") || n.startsWith("typo") || n.startsWith("eq_")
            || n.startsWith("couleurs") || n.startsWith("retours") || n.equals("recit_f1_court"));
    }

    private static void poser(Texte h, Cas c) {
        Demande d = c.d();
        if (c.masque() != null) h.forme(c.masque(), c.rgb(), d.f(), c.x(), c.y());
        else if (d.ligne()) {
            h.ligne(d.texte(), new Style(d.police(), d.preset(), d.couleurDefaut(), d.ancre(), d.typo(),
                d.centrageEncre()), d.cap(), d.f(), d.interlettre(), d.cx(), d.cy());
        } else h.bloc(d.texte(), d.police(), d.preset(), d.cap(), d.largeurMax(), d.pas(), d.f(), d.cx(), d.cy());
    }

    private static void ecran(Minecraft mc) {
        travail.join();
        AFFICHES.clear();
        for (Cas c : CAS) {
            if (!surEcran(c)) continue;
            Texte h = new Texte();
            poser(h, c);
            AFFICHES.add(h);
        }
        phase = 3;
        etape = 0;
        attente = 0;
    }

    private static boolean tousAJour() {
        for (Texte h : AFFICHES) if (!h.ajour()) return false;
        return true;
    }

    private static void shot(Minecraft mc, String nom) {
        Screenshot.grab(mc.gameDirectory, nom + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static void captures(Minecraft mc) {
        switch (etape) {
            case 0 -> {
                if (tousAJour()) {
                    etape = 1;
                    attente = 4;
                }
            }
            case 1 -> {
                if (--attente <= 0) {
                    shot(mc, "texte_banc_sombre");
                    log("écran : " + AFFICHES.size() + " couches passées par TexteCache, capture sur fond #141012");
                    fond = 0xFFE6E9EE;
                    etape = 2;
                    attente = 4;
                }
            }
            case 2 -> {
                if (--attente <= 0) {
                    shot(mc, "texte_banc_clair");
                    log("écran : capture sur fond #E6E9EE · " + TexteCache.bilan());
                    etape = 3;
                    attente = 10;
                }
            }
            case 3 -> {
                if (--attente <= 0) {
                    for (Texte h : AFFICHES) h.liberer();
                    AFFICHES.clear();
                    fond = 0xFF141012;
                    verifie(TexteCache.texturesVivantes() == 0, "écran : textures libérées (" + TexteCache.bilan() + ")");
                    phase = 4;
                    travail = TexteCache.executer(TexteBanc::milleRendus);
                }
            }
            default -> { }
        }
    }

    /* ---------------------------------------------------------------- 3. endurance */

    /** Fil du texte : 1000 rendus (tailles variées), compteurs FreeType avant / après. */
    private static Void milleRendus() {
        TexteRaster.Compteurs k = TexteRaster.COMPTEURS;
        long g0 = k.glyphes, l0 = k.glyphesLiberes, e0 = k.erreursFt;
        long t0 = System.nanoTime();
        int n = 0, pixels = 0;
        for (int i = 0; i < 1000; i++) {
            Cas c = CAS.get(i % CAS.size());
            double s = 1.0 + (i % 7) * 0.125;
            Rendu r;
            if (c.masque() != null) r = TexteRaster.forme(c.masque(), c.rgb(), c.d().f() * s);
            else {
                Demande d = c.d();
                r = TexteRaster.rendre(new Demande(d.texte(), d.police(), d.preset(), d.cap() * s, d.largeurMax() * s,
                    TexteRaster.arrondi(d.pas() * s), d.f() * s, d.cx(), d.cy(), d.ancre(), d.interlettre() * s,
                    d.couleurDefaut(), d.typo(), d.ligne(), d.centrageEncre(), d.equilibrage(), d.ombre()));
            }
            pixels += r.w * r.h;
            n++;
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        log(String.format(Locale.ROOT, "endurance : %d rendus en %.0f ms (%.2f ms par rendu, %d Mpx de couches)", n, ms,
            ms / n, pixels / 1_000_000));
        verifie(k.glyphes - g0 == k.glyphesLiberes - l0 && k.glyphes > g0, "endurance : glyphes FreeType obtenus "
            + (k.glyphes - g0) + " = rendus libérés " + (k.glyphesLiberes - l0));
        verifie(k.erreursFt == e0, "endurance : aucune erreur FreeType (" + (k.erreursFt - e0) + ")");
        return null;
    }

    private static final int NB_LIGNES = 10;
    private static final Texte BLOC = new Texte();
    private static final Texte[] LIGNES = new Texte[NB_LIGNES];
    private static final Texte[] FORMES = new Texte[4];
    private static final List<Texte> LRU = new ArrayList<>();
    private static final double[] ECHELLES = {1.0, 1.25, 1.5, 2.0};
    private static int redim, rafale, changements, demandesAvant, creeesAvant;
    private static double k = 1.0;
    private static boolean toujoursAffiche = true;

    private static final String[] MOTS = {"Portée de la voix", "Arrêter l'animation", "Descendre", "Se lever",
        "Poste de garde", "Parler à la radio", "Arrêter la course", "Mystère", "Combat", "Village", "Forêt", "Rivière"};

    /** Les poignées de l'endurance, à l'échelle k (comme un HUD qui les redemande à chaque image). */
    private static void demander(String suffixe) {
        BLOC.bloc(TEXTE, Police.RECIT, Preset.RECIT, TexteRaster.capRecit(k), TexteRaster.largeurRecit(k),
            TexteRaster.pasRecit(k), k, 960.0, 583.2);
        for (int i = 0; i < NB_LIGNES; i++) {
            String txt = MOTS[i % MOTS.length] + suffixe;
            Style s = i % 3 == 0 ? Style.LIBELLE : i % 3 == 1 ? Style.NOTIF : Style.TITRE_OST;
            LIGNES[i].ligne(txt, s, (s == Style.NOTIF ? 10 : 12) * k, k, 0, 1860.0, 700.0 - 27 * i * k);
        }
        for (int i = 0; i < 4; i++) FORMES[i].forme(MASQUES[i], i % 2 == 0 ? VERT : OR_CLAIR, k, 1700 + 20 * i, 100);
    }

    private static final Masque[] MASQUES = {Masque.puce(4), Masque.losange(2), Masque.pause(1, 5),
        Masque.filet(113, 1, true)};

    private static boolean enduranceAJour() {
        if (!BLOC.ajour()) return false;
        for (Texte h : LIGNES) if (!h.ajour()) return false;
        for (Texte h : FORMES) if (!h.ajour()) return false;
        return true;
    }

    private static void suivreAffichage() {
        if (!BLOC.pret()) toujoursAffiche = false;
        for (Texte h : LIGNES) if (!h.pret()) toujoursAffiche = false;
    }

    private static void endurance(Minecraft mc) {
        switch (etape) {
            case 0 -> {   // poignées à l'échelle 1
                AFFICHES.clear();
                AFFICHES.add(BLOC);
                for (int i = 0; i < NB_LIGNES; i++) AFFICHES.add(LIGNES[i] = new Texte());
                for (int i = 0; i < 4; i++) AFFICHES.add(FORMES[i] = new Texte());
                k = 1.0;
                demander("");
                etape = 1;
            }
            case 1 -> {
                demander("");
                if (enduranceAJour()) {
                    demandesAvant = TexteCache.demandes;
                    creeesAvant = TexteCache.texturesCreees;
                    redim = 0;
                    attente = 0;
                    etape = 2;
                }
            }
            case 2 -> {   // 50 redimensionnements simulés, un toutes les 200 ms
                if (attente == 0) {
                    k = ECHELLES[(redim + 1) % ECHELLES.length];
                    redim++;
                }
                demander("");
                suivreAffichage();
                if (++attente >= 4) {
                    attente = 0;
                    if (redim >= 50) etape = 3;
                }
            }
            case 3 -> {
                demander("");
                if (enduranceAJour()) {
                    int dem = TexteCache.demandes - demandesAvant;
                    verifie(dem == 50 * (1 + NB_LIGNES), "redimensionnements : 50 × " + (1 + NB_LIGNES)
                        + " poignées de texte → " + dem + " rendus demandés (un par changement d'échelle)");
                    verifie(toujoursAffiche, "redimensionnements : l'ancienne texture reste affichée pendant le calcul");
                    verifie(TexteCache.texturesVivantes() == 1 + NB_LIGNES + 4, "redimensionnements : "
                        + TexteCache.texturesVivantes() + " textures vivantes pour " + (1 + NB_LIGNES + 4)
                        + " poignées (" + TexteCache.bilan() + ")");
                    demandesAvant = TexteCache.demandes;
                    rafale = 0;
                    etape = 4;
                }
            }
            case 4 -> {   // rafale : 10 échelles en 10 ticks (50 ms), puis un seul recalcul
                if (rafale < 10) {
                    k = 1.0 + 0.03 * (rafale + 1);   // finit à 1,30 (l'échelle d'avant était 1,5)
                    rafale++;
                }
                demander("");
                if (rafale >= 10) {
                    etape = 5;
                    attente = 0;
                }
            }
            case 5 -> {
                demander("");
                if (++attente > 6 && enduranceAJour()) {
                    int dem = TexteCache.demandes - demandesAvant;
                    verifie(dem == 1 + NB_LIGNES, "rafale : 10 changements d'échelle en 500 ms → " + dem
                        + " rendus demandés (attendu " + (1 + NB_LIGNES) + ", attente de 150 ms)");
                    demandesAvant = TexteCache.demandes;
                    changements = 0;
                    etape = 6;
                }
            }
            case 6 -> {   // 1000 changements de texte (100 ticks × 10 lignes), envoyés tout de suite
                changements += NB_LIGNES;
                demander(" " + changements);
                if (changements >= 1000) etape = 7;
            }
            case 7 -> {
                demander(" " + changements);
                if (enduranceAJour()) {
                    int dem = TexteCache.demandes - demandesAvant;
                    verifie(dem == 1000, "changements de texte : 1000 demandes envoyées tout de suite (" + dem + ")");
                    log("changements de texte : " + TexteCache.bilan());
                    // demandées une fois, jamais dessinées : la LRU les évince d'abord (8 de plus que sa taille)
                    for (int i = 0; i < TexteCache.LRU_PETITES + 8; i++) {
                        Texte h = new Texte();
                        h.ligne("Rappel n° " + i, Style.LIBELLE, 12, 1, 0, 1860.0, 40.0 + 24 * i);
                        LRU.add(h);
                    }
                    etape = 8;
                    attente = 0;
                }
            }
            case 8 -> {   // LRU : 10 lignes + 4 formes dessinées + (LRU + 8) lignes demandées : la LRU tourne
                demander(" " + changements);
                if (++attente > 40) {
                    verifie(TexteCache.petitesVivantes() <= TexteCache.LRU_PETITES, "LRU : "
                        + TexteCache.petitesVivantes() + " petites textures vivantes (≤ " + TexteCache.LRU_PETITES
                        + "), " + TexteCache.evictions + " évictions");
                    BLOC.liberer();
                    for (Texte h : LIGNES) h.liberer();
                    for (Texte h : FORMES) h.liberer();
                    for (Texte h : LRU) h.liberer();
                    AFFICHES.clear();
                    etape = 9;
                    attente = 10;
                }
            }
            case 9 -> {
                if (--attente <= 0) {
                    verifie(TexteCache.texturesVivantes() == 0 && TexteCache.texturesCreees == TexteCache.texturesLiberees,
                        "cache : toutes les textures libérées (" + TexteCache.bilan() + ")");
                    phase = 6;
                    travail = TexteCache.executer(() -> {
                        TexteRaster.fermer();
                        return null;
                    });
                }
            }
            default -> { }
        }
    }

    private static void bilanFinal(Minecraft mc) {
        travail.join();
        TexteRaster.Compteurs c = TexteRaster.COMPTEURS;
        verifie(c.faces == c.facesFermees && c.faces > 0, "FreeType : faces ouvertes " + c.faces + " = fermées "
            + c.facesFermees);
        verifie(c.bibliotheques == c.bibliothequesFermees && c.traceurs == c.traceursFermes,
            "FreeType : bibliothèque " + c.bibliotheques + "/" + c.bibliothequesFermees + ", traceur " + c.traceurs + "/"
                + c.traceursFermes);
        verifie(c.glyphes == c.glyphesLiberes, "FreeType : glyphes " + c.glyphes + " = libérés " + c.glyphesLiberes);
        log("compteurs : " + c);
        log(String.format(Locale.ROOT, "durée totale %.1f s · %s", (System.nanoTime() - debut) / 1e9,
            echecs == 0 ? "BANC OK" : echecs + " ÉCHEC(S)"));
        finir(mc);
    }

    private static void ecrireRapport() {
        try {
            if (dossier != null) Files.writeString(dossier.resolve("rapport.txt"), RAPPORT.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }
}
