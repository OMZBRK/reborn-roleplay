package fr.reborn.hud.radio;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Élément HUD « Escouade » (docs/gameplay/RADIO.md §5, maquettes v3/02 et v3/02b ; DA §6 règle 9) : une ligne par
 * personne qui parle à la radio — <b>tête du skin puis le nom</b>, rien d'autre : ni fond, ni cadre, ni nom
 * d'escouade, aucun kanji. Celui qui parle est net, avec un filet d'or d'1 px ; la ligne s'efface 1,5 s après la fin.
 * J'émets : ma ligne avec un petit point rouge. À terre : « haché » après le nom. En silence : rien à l'écran (option
 * de l'éditeur « Afficher toute l'escouade » : ceux qui se taisent en retrait).
 *
 * <p>Au-dessus, quand il y en a : la ligne staff « écoute · Kagerō · 9:41 », l'invitation (« Aki U. t'ajoute à
 * l'escouade Kagerō » / « Entrée accepter · Retour refuser · 10 s ») et un rappel bref si B ne peut pas émettre.
 * Déplaçable et redimensionnable dans l'éditeur de HUD ({@link HudElement#ESCOUADE}, des têtes d'exemple y montrent
 * la taille). Coordonnées GUI, mises à l'échelle de l'élément.
 */
public final class RadioHud {

    /** Boîte de l'élément à l'échelle 1 (trois lignes d'exemple), en GUI. */
    public static final int LARGEUR = 96, PAS = 14, HAUTEUR = 3 * PAS;
    private static final int TETE = 11;

    private static final int CREME = 0xFFF5E9D0, DOUX = 0xFFC2B59A, OR = 0xFFD9A95E, VERM = 0xFFD2523E,
            HACHE = 0xFFC9A7A0, ECOUTE = 0xFF9FB0D0, GRIS = 0xFFB4A88E, GRIS_DOUX = 0xFF8C826E;

    private RadioHud() {}

    /** HUD en jeu (enregistré dans {@code RebornHudClient}). */
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        HudElementState st;
        try { st = RebornHudClient.config().stateOf(HudElement.ESCOUADE); } catch (RuntimeException e) { return; }
        if (!st.visible()) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        HudElementBounds b = HudElementBounds.currentFor(HudElement.ESCOUADE, st, w, h);
        dessiner(g, mc.font, b.x(), b.y(), st.scale(), lignes(), false);
    }

    /** Éditeur de HUD : têtes d'exemple (Aki U. en retrait, Sakura H. qui parle, Ren H. en retrait). */
    public static void apercu(GuiGraphicsExtractor g, int x, int y, float echelle) {
        List<Ligne> l = new ArrayList<>();
        l.add(new Ligne(EXEMPLES[0], "Aki U.", false, false, false, false, 1f));
        l.add(new Ligne(EXEMPLES[1], "Sakura H.", true, false, false, false, 1f));
        l.add(new Ligne(EXEMPLES[2], "Ren H.", false, false, false, false, 1f));
        dessiner(g, Minecraft.getInstance().font, x, y, echelle, l, true);
    }

    /** Têtes d'exemple de l'éditeur (le banc de captures leur prête des skins RP). */
    public static final UUID[] EXEMPLES = {
        UUID.fromString("00000000-0000-4000-8000-00000000a001"),
        UUID.fromString("00000000-0000-4000-8000-00000000a002"),
        UUID.fromString("00000000-0000-4000-8000-00000000a003")};

    /** Une ligne : tête, nom, parle (net + filet d'or), émet (point rouge), haché, en retrait, opacité. */
    record Ligne(UUID tete, String nom, boolean parle, boolean emet, boolean hache, boolean retrait, float a) { }

    /** Les lignes à afficher maintenant. */
    static List<Ligne> lignes() {
        long now = System.currentTimeMillis();
        RadioClient.Etat e = RadioClient.etat();
        List<RadioClient.Parole> ps = RadioClient.paroles();
        List<Ligne> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        UUID moi = mc.player == null ? null : mc.player.getUUID();
        boolean emet = emetMaintenant(e);
        UUID maTete = e.volee() && e.proprioId() != null ? e.proprioId() : moi;
        String monNom = e.volee() && !e.proprio().isEmpty() ? "oreillette de " + e.proprio() : monNom(e, mc);

        if (RebornPrefs.INSTANCE.radioToutEscouade && e.esc() != null) {
            for (RadioClient.Membre m : e.esc().membres()) {
                if (!m.enLigne() || m.id() == null) continue;
                if (m.moi()) {
                    out.add(new Ligne(maTete, emet ? monNom : m.court(), false, emet, emet && e.hache(), !emet, 1f));
                    continue;
                }
                RadioClient.Parole p = parole(ps, m.id());
                float a = p == null ? 1f : fondu(p, now);
                out.add(new Ligne(m.id(), m.court(), p != null, false, p != null && p.hache, p == null, a));
            }
            // quelqu'un hors escouade (oreillette volée, écoute staff) : à la suite
            for (RadioClient.Parole p : ps) {
                boolean connu = e.esc().membres().stream().anyMatch(m -> p.tete != null && p.tete.equals(m.id()));
                if (!connu) out.add(new Ligne(p.tete, p.nom, true, false, p.hache, false, fondu(p, now)));
            }
            return out;
        }
        for (RadioClient.Parole p : ps) out.add(new Ligne(p.tete, p.nom, true, false, p.hache, false, fondu(p, now)));
        if (emet) out.add(new Ligne(maTete, monNom, false, true, e.hache(), false, 1f));
        return out;
    }

    private static boolean emetMaintenant(RadioClient.Etat e) {
        if (!RadioClient.serveur() || e.refus() != null) return false;
        if (debugEmet) return true;
        try {
            return fr.reborn.hud.voice.RebornVoiceAddon.radioEmet();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Banc d'essai : force « j'émets » (pas de Plasmo Voice connecté en solo). */
    public static boolean debugEmet;

    private static String monNom(RadioClient.Etat e, Minecraft mc) {
        if (e.esc() != null) for (RadioClient.Membre m : e.esc().membres()) if (m.moi()) return m.court();
        return mc.player == null ? "?" : mc.player.getGameProfile().name();
    }

    private static RadioClient.Parole parole(List<RadioClient.Parole> ps, UUID tete) {
        for (int i = ps.size() - 1; i >= 0; i--) if (tete.equals(ps.get(i).tete)) return ps.get(i);
        return null;
    }

    /** Pleine opacité tant qu'il parle, puis fondu sur les 400 dernières ms des 1,5 s. */
    private static float fondu(RadioClient.Parole p, long now) {
        if (p.fin < 0) return 1f;
        long reste = RadioClient.EFFACE_MS - (now - p.fin);
        return Math.max(0f, Math.min(1f, reste / 400f));
    }

    /* ------------------------------------------------------------------ dessin */

    private static void dessiner(GuiGraphicsExtractor g, Font f, int x, int y, float s, List<Ligne> lignes, boolean apercu) {
        long now = System.currentTimeMillis();
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        float yy = 0;
        if (!apercu) {
            RadioClient.Ecoute ec = RadioClient.ecoute();
            if (ec != null) {
                long r = Math.max(0, (ec.fin() - now) / 1000);
                DaKit.inter7o(g, f, DaKit.inter("écoute · " + ec.esc() + " · " + r / 60 + ":" + String.format("%02d", r % 60)),
                        0, yy + TETE / 2f, ECOUTE, 0);
                yy += PAS - 2;
            }
            if (RadioClient.refusB != null && now - RadioClient.refusBAt < 2200) {
                float a = Math.min(1f, (2200 - (now - RadioClient.refusBAt)) / 400f);
                DaKit.inter7o(g, f, DaKit.inter("radio · " + RadioClient.refusB), 0, yy + TETE / 2f, DaKit.alpha(GRIS, a), 0);
                yy += PAS - 2;
            }
            RadioClient.Invit inv = RadioClient.invit();
            if (inv != null) yy = invitation(g, f, inv, yy, now);
        }
        for (Ligne l : lignes) {
            ligne(g, f, l, yy);
            yy += PAS;
        }
        g.pose().popMatrix();
    }

    private static void ligne(GuiGraphicsExtractor g, Font f, Ligne l, float yy) {
        float a = l.a() * (l.retrait() ? 0.6f : 1f);
        if (a <= 0.01f) return;
        int y = Math.round(yy);
        tete(g, l.tete(), 0, y, TETE, a);
        if (l.parle()) g.fill(-3, y + 1, -2, y + TETE - 1, DaKit.alpha(OR, l.a()));
        Component nom = l.parle() || l.emet() ? DaKit.interGras(l.nom()) : DaKit.inter(l.nom());
        int col = DaKit.alpha(l.retrait() ? DOUX : CREME, a);
        float x = TETE + 4;
        x += DaKit.inter9o(g, f, nom, x, y + TETE / 2f, col, 0);
        if (l.emet()) {
            int px = Math.round(x + 4), py = y + TETE / 2 - 1;
            g.fill(px, py - 1, px + 3, py + 2, VERM);
            g.fill(px - 1, py, px + 4, py + 1, VERM);
            x += 8;
        }
        if (l.hache()) DaKit.inter7o(g, f, DaKit.inter("haché"), x + 4, y + TETE / 2f, DaKit.alpha(HACHE, a), 0);
    }

    private static float invitation(GuiGraphicsExtractor g, Font f, RadioClient.Invit inv, float yy, long now) {
        float a = Math.min(1f, (now - inv.at()) / 250f);
        float reste = inv.reste(now);
        String l1 = inv.creation() ? inv.de() + " crée une escouade avec toi" : inv.de() + " t'ajoute à l'escouade " + inv.esc();
        float w1 = DaKit.inter9o(g, f, DaKit.inter(l1), 0, yy + 5, DaKit.alpha(CREME, a), 0);
        String t = touche(HudKeybinds.DUEL_ACCEPT, "Entrée") + " accepter  ·  " + touche(HudKeybinds.DUEL_REFUSE, "Retour")
                + " refuser  ·  " + Math.max(0, (int) Math.ceil(reste)) + " s";
        float w2 = DaKit.inter7o(g, f, DaKit.inter(t), 0, yy + 15, DaKit.alpha(reste < 4 ? 0xFFE8A0A2 : GRIS, a), 0);
        int w = Math.round(Math.max(w1, w2));
        int fy = Math.round(yy + 21);
        g.fill(0, fy, w, fy + 1, DaKit.argb(0x8E6A30, 0.35f * a));
        int plein = Math.round(w * Math.min(1f, reste / Math.max(1, inv.secs())));
        g.fill(0, fy, plein, fy + 1, DaKit.alpha(OR, a));
        return yy + 27;
    }

    static String touche(net.minecraft.client.KeyMapping km, String def) {
        if (km == null) return def;
        String save = km.saveString();
        if ("key.keyboard.enter".equals(save)) return "Entrée";
        if ("key.keyboard.backspace".equals(save)) return "Retour";
        String s = km.getTranslatedKeyMessage().getString();
        return s.length() > 10 ? s.substring(0, 10) : s;
    }

    /** Tête 8×8 du skin (et son calque), {@code taille} GUI, opacité {@code a}. Skin RP, sinon celui du joueur, sinon défaut. */
    static void tete(GuiGraphicsExtractor g, UUID id, int x, int y, int taille, float a) {
        Identifier skin = skin(id);
        int col = DaKit.blanc(a);
        g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, taille, taille, 8, 8, 64, 64, col);
        g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, taille, taille, 8, 8, 64, 64, col);
    }

    static Identifier skin(UUID id) {
        if (id == null) return DefaultPlayerSkin.getDefaultTexture();
        Identifier rp = fr.reborn.hud.skin.RebornSkins.overrideFor(id);
        if (rp != null) return rp;
        try {
            var conn = Minecraft.getInstance().getConnection();
            PlayerInfo info = conn == null ? null : conn.getPlayerInfo(id);
            if (info != null) return info.getSkin().body().texturePath();
        } catch (RuntimeException ignored) { }
        return DefaultPlayerSkin.get(id).body().texturePath();
    }
}
