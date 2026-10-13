package fr.reborn.hud.parchemin;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Réglage d'une bibliothèque par le staff. Tout se règle à la souris : type (Village, Académie, Clan, ANBU, Palais,
 * Hôpital, Personnelle — il fixe l'accès, les arrivages et le modèle de pourcentages par défaut), accès
 * (libre / membres), arrivages (oui / non), nombre de rouleaux, délai entre deux tirages, pourcentage par rang, le
 * <b>vol</b> (permis, durée de base, alerte nominative), puis exceptions technique par technique (filtrées par branche
 * ou nature) et la <b>réserve de secrets</b> (onglet Secrets : recherche au clavier, filtres, ajouter / retirer une
 * technique, % propre, tirage commun oui / non, retour à la réserve par défaut du type). En bas, un tirage d'aperçu
 * et la simulation de 10 000 tirages (réserve comprise) montrent tout de suite l'effet des réglages.
 * Habillage : planche laquée, plaques-boutons, réglettes d'or ({@code tools/gen_bibliotheque_textures.py}).
 */
public final class BibliothequeStaffScreen extends Screen {

    private static final double[] STEPS = {0, 0.01, 0.02, 0.05, 0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 8, 12, 18, 25, 35, 50, 75, 100};
    private static final String[] FILTERS = {"Tous", "Taïjutsu", "Kenjutsu", "Ninjutsu", "Katon", "Suiton", "Doton", "Fūton", "Raiton"};
    private static final String[] SFILTERS = {"Toutes", "Reserve", "Secretes", "Clan", "ANBU", "Rang S"};
    /** Types de bibliothèque (ShinobiAbilities {@code Library.Type}) et leurs réglages par défaut. */
    static final String[] TYPES = {"VILLAGE", "ACADEMIE", "CLAN", "ANBU", "PALAIS", "HOPITAL", "PERSONNELLE"};
    static final String[] TYPE_LABELS = {"Village", "Académie", "Clan", "ANBU", "Palais", "Hôpital", "Personnelle"};
    static final String[] TYPE_NOMS = {"Village", "Académie", "Clan", "ANBU", "Palais du Kage", "Hôpital", "Personnelle"};
    private static final boolean[] TYPE_MEMBRES = {false, true, true, true, true, true, true};
    private static final boolean[] TYPE_DRAWS = {true, true, true, true, true, true, false};
    /** Tirage commun par défaut : tous sauf le Clan (sa réserve seulement). */
    private static final boolean[] TYPE_COMMONS = {true, true, false, true, true, true, true};
    private static final int[] TYPE_MODEL = {0, 0, 0, 2, 2, 0, 0};

    private String libId;
    private String name;
    private List<Technique> techs = Technique.DEMO;
    private final Map<String, String> slotInfo = new HashMap<>();
    private double[] byRank = Tirage.PUBLIQUE.clone();
    private final Map<String, Double> overrides = new HashMap<>();
    private int count = 6, delayH = 3, model = 0, filter = 0, scroll = 0;
    private int type = 0, fondsCount = 0, serverType = -1;
    private boolean membres = false, draws = true;
    private String owner = "";
    private List<Technique> preview = List.of();
    private Tirage.Stats stats;
    private long seed = 7;

    // ─── vol (13/10, optionnel) ───
    private boolean theft = true, theftNamed = false;
    private int theftSecs = 6;
    private double[] theftFactors = {1, 1.5, 2, 3, 4};

    // ─── secrets (13/10, optionnel : un ancien serveur n'en envoie pas) ───
    private boolean hasSecrets, commons = true;
    private record Secret(Technique t, boolean secret, String clan, boolean anbu, String tags, String norm) {}
    private List<Secret> secrets = List.of();
    private final Map<String, Secret> secretById = new HashMap<>();
    private final Map<String, Double> reserve = new LinkedHashMap<>();
    private final Map<String, Double> reserveDefault = new LinkedHashMap<>();
    private double[] secretPct = {30, 20, 10, 3, 0.5};
    private boolean reserveCustom, reserveDirty, reserveReset;
    private String query = "";
    private int sfilter = 0, sscroll = 0;
    private List<Secret> sFiltered = List.of();
    private final Map<String, String> nameCache = new HashMap<>();
    private int nameCacheW = -1;

    private record Hit(int x, int y, int w, int h, Runnable r) {}
    private final List<Hit> hits = new ArrayList<>();

    public BibliothequeStaffScreen(String name) {
        super(Component.literal(name));
        this.name = name;
        recompute();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) {
        BiblioArt.fond(g, width, height, 0.72f);
    }

    /** Réglages reçus du serveur ({@code {"t":"lib_cfg"}}). */
    public static BibliothequeStaffScreen fromJson(com.google.gson.JsonObject o) {
        BibliothequeStaffScreen s = new BibliothequeStaffScreen(o.get("name").getAsString());
        s.update(o);
        return s;
    }

    public String libId() { return libId; }

    private static String str(com.google.gson.JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    public void update(com.google.gson.JsonObject o) {
        libId = o.get("id").getAsString();
        name = o.get("name").getAsString();
        count = o.get("count").getAsInt();
        delayH = o.get("delayH").getAsInt();
        model = o.get("model").getAsInt();
        if (o.has("type")) {
            type = Math.max(0, java.util.Arrays.asList(TYPES).indexOf(o.get("type").getAsString()));
            membres = o.has("access") && o.get("access").getAsString().equals("membres");
            draws = !o.has("draws") || o.get("draws").getAsBoolean();
            owner = o.has("owner") ? o.get("owner").getAsString() : "";
            fondsCount = o.has("fondsCount") ? o.get("fondsCount").getAsInt() : 0;
        } else {
            type = model == 1 ? 1 : model == 2 ? 4 : 0;           // serveur v1 : modèle → type
        }
        serverType = type;
        var r = o.getAsJsonArray("byRank");
        for (int i = 0; i < 5 && i < r.size(); i++) byRank[i] = r.get(i).getAsDouble();
        overrides.clear();
        for (var e : o.getAsJsonObject("overrides").entrySet()) overrides.put(e.getKey(), e.getValue().getAsDouble());
        List<Technique> list = new ArrayList<>();
        slotInfo.clear();
        for (var e : o.getAsJsonArray("techs")) {
            var t = e.getAsJsonObject();
            Technique tech = Technique.fromJson(t);
            list.add(tech);
            if (t.has("slots")) slotInfo.put(tech.id(), t.get("slots").getAsString());
        }
        techs = list;
        // ─── vol ───
        theft = !o.has("theft") || o.get("theft").getAsBoolean();
        theftSecs = o.has("theftSecs") ? o.get("theftSecs").getAsInt() : 6;
        theftNamed = o.has("theftNamed") && o.get("theftNamed").getAsBoolean();
        if (o.has("theftFactors")) {
            var f = o.getAsJsonArray("theftFactors");
            for (int i = 0; i < 5 && i < f.size(); i++) theftFactors[i] = f.get(i).getAsDouble();
        }
        // ─── secrets ───
        hasSecrets = o.has("secretTechs");
        commons = !o.has("commons") || o.get("commons").getAsBoolean();
        reserveCustom = o.has("reserveCustom") && o.get("reserveCustom").getAsBoolean();
        reserveDirty = false;
        reserveReset = false;
        reserve.clear();
        if (o.has("reserve")) for (var e : o.getAsJsonObject("reserve").entrySet()) reserve.put(e.getKey(), e.getValue().getAsDouble());
        reserveDefault.clear();
        if (o.has("reserveDefault")) for (var e : o.getAsJsonObject("reserveDefault").entrySet()) reserveDefault.put(e.getKey(), e.getValue().getAsDouble());
        if (o.has("secretPct")) {
            var sp = o.getAsJsonArray("secretPct");
            for (int i = 0; i < 5 && i < sp.size(); i++) secretPct[i] = sp.get(i).getAsDouble();
        }
        List<Secret> sl = new ArrayList<>();
        secretById.clear();
        if (hasSecrets) for (var e : o.getAsJsonArray("secretTechs")) {
            var j = e.getAsJsonObject();
            Technique t = Technique.fromJson(j);
            String clan = str(j, "clan");
            boolean secret = j.has("secret") && j.get("secret").getAsBoolean(), anbu = j.has("anbu") && j.get("anbu").getAsBoolean();
            if (j.has("slots")) slotInfo.put(t.id(), j.get("slots").getAsString());
            StringBuilder tags = new StringBuilder(t.typeLine());
            if (!clan.isEmpty()) tags.append(" · clan ").append(clan);
            if (anbu) tags.append(" · ANBU");
            if (secret) tags.append(" · secrète");
            if (slotInfo.containsKey(t.id())) tags.append(" · slots ").append(slotInfo.get(t.id()));
            Secret s = new Secret(t, secret, clan.isEmpty() ? null : clan, anbu, tags.toString(),
                norm(t.name() + " " + t.id() + " " + tags));
            sl.add(s);
            secretById.put(t.id(), s);
        }
        sl.sort((a, b) -> a.t().rankIndex() != b.t().rankIndex() ? a.t().rankIndex() - b.t().rankIndex() : a.t().name().compareTo(b.t().name()));
        secrets = sl;
        refilter();
        recompute();
    }

    /** Envoie les réglages au serveur. La réserve n'est envoyée que si elle a été touchée (sinon : défaut du type). */
    void saveToServer() {
        if (libId == null) return;
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("name", name);
        o.addProperty("count", count);
        o.addProperty("delayH", delayH);
        o.addProperty("model", model);
        o.addProperty("type", TYPES[type]);
        o.addProperty("access", membres ? "membres" : "libre");
        o.addProperty("draws", draws);
        com.google.gson.JsonArray r = new com.google.gson.JsonArray();
        for (double d : byRank) r.add(d);
        o.add("byRank", r);
        com.google.gson.JsonObject ov = new com.google.gson.JsonObject();
        overrides.forEach(ov::addProperty);
        o.add("overrides", ov);
        o.addProperty("theft", theft);
        o.addProperty("theftSecs", theftSecs);
        o.addProperty("theftNamed", theftNamed);
        if (hasSecrets) {
            o.addProperty("commons", commons);
            if (reserveReset) o.addProperty("reserveReset", true);
            else if (reserveDirty) {
                com.google.gson.JsonObject res = new com.google.gson.JsonObject();
                reserve.forEach(res::addProperty);
                o.add("reserve", res);
            }
        }
        ParcheminClient.send("cfg_save", libId, o);
    }

    /** Techniques du tirage commun : l'Hôpital ne tire que les techniques médicales (s'il y en a). */
    private List<Technique> pool() {
        if (!TYPES[type].equals("HOPITAL")) return techs;
        List<Technique> med = new ArrayList<>();
        for (Technique t : techs) if (t.nature().equals("Iryō")) med.add(t);
        return med.isEmpty() ? techs : med;
    }

    /** Candidats du tirage : tirage commun (s'il est actif) + réserve (son % l'emporte). */
    private List<Technique> drawPool() {
        Map<String, Technique> m = new LinkedHashMap<>();
        if (commons) for (Technique t : pool()) m.put(t.id(), t);
        for (String id : reserve.keySet()) {
            Secret s = secretById.get(id);
            if (s != null) m.put(id, s.t());
        }
        return new ArrayList<>(m.values());
    }

    private Map<String, Double> drawOverrides() {
        Map<String, Double> m = new HashMap<>(overrides);
        m.putAll(reserve);
        return m;
    }

    /** Un rang S ne sort que s'il reste un slot (« 2/2 » = complet). */
    private boolean slotFree(Technique t) {
        String s = slotInfo.get(t.id());
        if (s == null) return true;
        String[] p = s.split("/");
        try { return p.length < 2 || Integer.parseInt(p[0].trim()) < Integer.parseInt(p[1].trim()); }
        catch (NumberFormatException e) { return true; }
    }

    private final java.util.Set<String> commonIds = new java.util.HashSet<>();

    void recompute() {
        commonIds.clear();
        for (Technique t : pool()) commonIds.add(t.id());
        List<Technique> pool = drawPool();
        Map<String, Double> ov = drawOverrides();
        stats = Tirage.simulate(pool, byRank, ov, count, 10_000, 42, t -> reserve.containsKey(t.id()));
        preview = Tirage.draw(pool, byRank, ov, count, this::slotFree, new Random(seed));
    }

    /** Nouveau type : accès, arrivages, tirage commun, modèle de pourcentages et réserve par défaut du type. */
    public void setType(int t) {
        type = t;
        membres = TYPE_MEMBRES[t];
        draws = TYPE_DRAWS[t];
        commons = TYPE_COMMONS[t];
        model = TYPE_MODEL[t];
        byRank = (model == 0 ? Tirage.PUBLIQUE : model == 1 ? Tirage.RESERVEE : Tirage.PRIVEE).clone();
        if (!reserveCustom) {
            reserve.clear();
            reserve.putAll(defaultReserve(t));
        }
        refilter();
        recompute();
    }

    /**
     * Réserve par défaut d'un type (même règle que le serveur, {@code ScrollCatalog.defaultReserve}) : celle envoyée
     * par le serveur pour son type, sinon recalculée ici (Clan → techniques du clan, ANBU → techniques ANBU, Palais →
     * rangs A et S des trois branches hors clan et ANBU).
     */
    private Map<String, Double> defaultReserve(int t) {
        if (t == serverType) return reserveDefault;
        Map<String, Double> m = new LinkedHashMap<>();
        String clan = norm(owner);
        for (Secret s : secrets) {
            char r = s.t().rank();
            String br = s.t().branch();
            boolean in = switch (TYPES[t]) {
                case "CLAN" -> !clan.isEmpty() && s.clan() != null && norm(s.clan()).equals(clan);
                case "ANBU" -> s.anbu();
                case "PALAIS" -> (r == 'A' || r == 'S') && s.clan() == null && !s.anbu()
                    && (br.equals("Taïjutsu") || br.equals("Kenjutsu") || br.equals("Ninjutsu"));
                default -> false;
            };
            if (in) m.put(s.t().id(), secretPct[s.t().rankIndex()]);
        }
        return m;
    }

    private void touchReserve() {
        reserveCustom = true;
        reserveDirty = true;
        reserveReset = false;
    }

    private void addReserve(Technique t) {
        touchReserve();
        reserve.put(t.id(), secretPct[t.rankIndex()]);
        refilter();
        recompute();
    }

    private void removeReserve(String id) {
        touchReserve();
        reserve.remove(id);
        refilter();
        recompute();
    }

    private void stepReserve(String id, int d) {
        touchReserve();
        double p = reserve.getOrDefault(id, 0.0);
        reserve.put(id, STEPS[Math.max(0, Math.min(STEPS.length - 1, stepOf(p) + d))]);
        recompute();
    }

    /** Retour à la réserve par défaut du type. */
    private void resetReserve() {
        reserveCustom = false;
        reserveDirty = false;
        reserveReset = true;
        reserve.clear();
        reserve.putAll(defaultReserve(type));
        refilter();
        recompute();
    }

    public void setFilter(int f) { filter = f; scroll = 0; }

    public void setSecretFilter(int f) { sfilter = f; sscroll = 0; refilter(); }

    public void setQuery(String q) { query = q; sscroll = 0; refilter(); }

    static String norm(String s) {
        return DaKit.sansAccents(s == null ? "" : s).toLowerCase(Locale.ROOT);
    }

    /** Liste filtrée de l'onglet Secrets : recalculée quand la recherche, le filtre ou la réserve changent. */
    private void refilter() {
        String q = norm(query.trim());
        List<Secret> out = new ArrayList<>();
        for (Secret s : secrets) {
            boolean ok = switch (sfilter) {
                case 1 -> reserve.containsKey(s.t().id());
                case 2 -> s.secret();
                case 3 -> s.clan() != null;
                case 4 -> s.anbu();
                case 5 -> s.t().rank() == 'S';
                default -> true;
            };
            if (ok && (q.isEmpty() || s.norm().contains(q))) out.add(s);
        }
        // la réserve d'abord, puis les secrètes, puis le reste (chaque groupe garde l'ordre rang puis nom)
        out.sort(java.util.Comparator.comparingInt((Secret s) -> reserve.containsKey(s.t().id()) ? 0 : s.secret() ? 1 : 2));
        sFiltered = out;
    }

    private static String pct(double p) {
        if (p >= 1 || p == 0) return String.format(Locale.FRANCE, "%.0f %%", p);
        if (p >= 0.1) return String.format(Locale.FRANCE, "%.1f %%", p);
        return String.format(Locale.FRANCE, "%.2f %%", p);
    }

    private static int stepOf(double p) {
        int best = 0;
        for (int i = 0; i < STEPS.length; i++) if (Math.abs(STEPS[i] - p) < Math.abs(STEPS[best] - p)) best = i;
        return best;
    }

    private static float slider(double p) { return stepOf(p) / (float) (STEPS.length - 1); }

    private List<Technique> filtered() {
        List<Technique> out = new ArrayList<>();
        String f = FILTERS[filter];
        for (Technique t : techs) {
            if (filter == 0 || t.branch().equals(f) || t.nature().equals(f)) out.add(t);
        }
        out.sort((a, b) -> a.rankIndex() != b.rankIndex() ? a.rankIndex() - b.rankIndex() : a.name().compareTo(b.name()));
        return out;
    }

    private static final String[] PAGES = {"Reglages", "Techniques", "Secrets", "Simulation"};
    public static final int PAGE_REGLAGES = 0, PAGE_TECHNIQUES = 1, PAGE_SECRETS = 2, PAGE_SIMULATION = 3;
    private int page = 0;
    /** Lots de la page en cours d'extraction : plaques, réglettes, textes. */
    private LotBlit pl, reg;
    private BiblioArt.Txt tx;

    public void setPage(int p) { page = p; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear();
        BiblioArt.entete(g, font, width, DaKit.arcade("Reglages de la bibliotheque"),
            DaKit.inter(name + " · " + TYPE_NOMS[type] + (owner.isEmpty() ? "" : " · " + owner)));
        int w = Math.min(width - 24, 568), h = Math.min(height - 88, 290);
        int x = (width - w) / 2, y = 60 + Math.max(0, (height - 88 - h) / 4);
        DaKit.coupe(g, BiblioArt.PANNEAU, x - 2, y - 2, w + 8, h + 8, 0, 0, 576, 348, 16, 16, 576, 348);

        // lots de la page (DA §6, règle 7) : plaques, réglettes, puis tous les textes, chacun en un seul élément GUI
        // (fermés dans cet ordre, après les pièces posées directement, qui ne chevauchent ni plaques ni textes)
        pl = BiblioArt.plaques(g);
        reg = BiblioArt.reglettes(g);
        tx = new BiblioArt.Txt(g, font);
        // onglets
        int saveW = w < 480 ? 70 : 84, rollW = w < 480 ? 84 : 104;
        int tw = Math.max(52, Math.min(80, (w - 28 - saveW - rollW - 16) / PAGES.length - 4));
        int tbx = x + 14, ty = y + 12;
        for (int i = 0; i < PAGES.length; i++) {
            final int p = i;
            BiblioArt.plaque(pl, tx, tbx, ty, tw, 14, PAGES[i], page == i, DaKit.dans(mx, my, tbx, ty, tw, 14), true);
            hits.add(new Hit(tbx, ty, tw, 14, () -> setPage(p)));
            tbx += tw + 5;
        }
        int sx = x + w - 14 - saveW, rx = sx - 6 - rollW;
        BiblioArt.plaque(pl, tx, sx, ty, saveW, 14, "Enregistrer", true, DaKit.dans(mx, my, sx, ty, saveW, 14), true);
        hits.add(new Hit(sx, ty, saveW, 14, this::saveToServer));
        BiblioArt.plaque(pl, tx, rx, ty, rollW, 14, w < 480 ? "Tirer" : "Tirer maintenant", false,
            DaKit.dans(mx, my, rx, ty, rollW, 14), libId != null && draws);
        if (libId != null && draws) hits.add(new Hit(rx, ty, rollW, 14, () -> ParcheminClient.send("reroll", libId, null)));
        LotGui filet = LotGui.ouvrir(g);
        filet.fill(x + 12, ty + 21, x + w - 12, ty + 22, 0xFF6A4E24);
        filet.fill(x + 12, ty + 22, x + w - 12, ty + 23, 0xFF120608);
        filet.fermer();
        int cx = x + 16, cy = ty + 34, cw = w - 32, ch = y + h - 14 - cy;
        switch (page) {
            case PAGE_REGLAGES -> settings(g, cx, cy, cw, ch, mx, my);
            case PAGE_TECHNIQUES -> techniques(g, cx, cy, cw, ch, mx, my);
            case PAGE_SECRETS -> secrets(g, cx, cy, cw, ch, mx, my);
            default -> simulation(g, cx, cy, cw, ch, mx, my);
        }
        pl.fermer();
        reg.fermer();
        tx.fermer();
        BiblioArt.aides(g, font, width, height, page == PAGE_TECHNIQUES
            ? new String[][]{{"- +", "régler une technique"}, {"Clic nom en or", "taux du rang"}, {"Molette", "défiler"}, {"Echap", "fermer"}}
            : page == PAGE_SECRETS
            ? new String[][]{{"Clavier", "rechercher"}, {"- +", "% d'une technique"}, {"Molette", "défiler"}, {"Echap", "fermer"}}
            : new String[][]{{"Clic", "réglette : à gauche baisse, à droite monte"}, {"Echap", "fermer"}});
        super.extractRenderState(g, mx, my, delta);
    }

    /** Titre de section : ArcadePix ×1 or, ombré (comme les intertitres des planches de la fiche). */
    private void titre(GuiGraphicsExtractor g, String s, int x, int y) {
        tx.arcX1(DaKit.arcade(s), x, y, DaKit.OR, 0, true);
    }

    private void settings(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        int colW = (w - 28) / 2, lx = x, rx = x + colW + 28;
        titre(g, "Type", lx, y);
        int by = y + 14, bw = (colW - 12) / 4;
        for (int i = 0; i < TYPES.length; i++) {
            final int t = i;
            int bx = lx + (i % 4) * (bw + 4), yy = by + (i / 4) * 18;
            BiblioArt.plaque(pl, tx, bx, yy, bw, 14, TYPE_LABELS[i], type == i, DaKit.dans(mx, my, bx, yy, bw, 14), true);
            hits.add(new Hit(bx, yy, bw, 14, () -> setType(t)));
        }
        int ay = by + 44, half = (colW - 12) / 2;
        titre(g, "Acces", lx, ay);
        titre(g, "Arrivages", lx + half + 12, ay);
        int pw = (half - 4) / 2;
        String[][] opts = {{"Libre", "Membres"}, {"Oui", "Non"}};
        for (int c = 0; c < 2; c++) {
            for (int k = 0; k < 2; k++) {
                int bx = lx + c * (half + 12) + k * (pw + 4), yy = ay + 14;
                boolean on = c == 0 ? membres == (k == 1) : draws == (k == 0);
                BiblioArt.plaque(pl, tx, bx, yy, pw, 14, opts[c][k], on, DaKit.dans(mx, my, bx, yy, pw, 14), true);
                final int cc = c, kk = k;
                hits.add(new Hit(bx, yy, pw, 14, () -> {
                    if (cc == 0) membres = kk == 1;
                    else draws = kk == 0;
                }));
            }
        }
        String info = (membres ? (theft ? "Les non-membres voient les rouleaux et peuvent tenter de les voler."
                : "Les non-membres voient les rouleaux sans pouvoir les prendre.") : "Tout le monde peut prendre.")
            + " Fonds : " + fondsCount + " / 9 rouleaux deposes.";
        DaKit.Paragraphe ip = DaKit.paragraphe(font, info, colW, DaKit.S7, 9, 2);
        tx.paragraphe(ip, lx, ay + 36, 0xFF9A8C78);
        int ly = ay + 36 + ip.hauteur() + 6;
        boolean hc = DaKit.dans(mx, my, lx, ly + 4, colW, 14);
        BiblioArt.reglette(reg, tx, lx, ly, colW, (count - 1) / 8f, "Rouleaux par tirage", String.valueOf(count), hc);
        hits.add(new Hit(lx, ly + 4, colW / 2, 14, () -> { count = Math.max(1, count - 1); recompute(); }));
        hits.add(new Hit(lx + colW / 2, ly + 4, colW / 2, 14, () -> { count = Math.min(9, count + 1); recompute(); }));
        ly += 30;
        boolean hd = DaKit.dans(mx, my, lx, ly + 4, colW, 14);
        BiblioArt.reglette(reg, tx, lx, ly, colW, (delayH - 1) / 23f, "Delai entre deux tirages", delayH + " h", hd);
        hits.add(new Hit(lx, ly + 4, colW / 2, 14, () -> delayH = Math.max(1, delayH - 1)));
        hits.add(new Hit(lx + colW / 2, ly + 4, colW / 2, 14, () -> delayH = Math.min(24, delayH + 1)));

        g.fill(x + colW + 13, y, x + colW + 14, y + h - 4, 0xFF2D181C);
        titre(g, "Apparition par rang", rx, y);
        int ry = y + 14, step = Math.max(18, Math.min(24, (h - 16 - 72) / 5));
        LotBlit fudas = BiblioArt.fudas(g);
        for (int r = 0; r < 5; r++) {
            char rank = "DCBAS".charAt(r);
            BiblioArt.fuda(fudas, rx - 1, ry - 1, rank);
            int rw = colW - 18;
            boolean hr = DaKit.dans(mx, my, rx + 18, ry + 4, rw, 14);
            BiblioArt.reglette(reg, tx, rx + 18, ry, rw, slider(byRank[r]), "Rang " + rank, pct(byRank[r]), hr);
            final int rr = r;
            hits.add(new Hit(rx + 18, ry + 4, rw / 2, 14, () -> { byRank[rr] = STEPS[Math.max(0, stepOf(byRank[rr]) - 1)]; recompute(); }));
            hits.add(new Hit(rx + 18 + rw / 2, ry + 4, rw / 2, 14, () -> { byRank[rr] = STEPS[Math.min(STEPS.length - 1, stepOf(byRank[rr]) + 1)]; recompute(); }));
            ry += step;
        }
        fudas.fermer();

        // vol par les non-membres : permis ou non, alerte nominative, durée de base (× facteur du rang)
        int vy = ry + 2;
        titre(g, "Vol", rx, vy);
        titre(g, "Alerte", rx + half + 12, vy);
        String[][] vopts = {{"Permis", "Interdit"}, {"Anonyme", "Nominative"}};
        for (int c = 0; c < 2; c++) {
            for (int k = 0; k < 2; k++) {
                int bx = rx + c * (half + 12) + k * (pw + 4), yy = vy + 14;
                boolean on = c == 0 ? theft == (k == 0) : theftNamed == (k == 1);
                boolean ok = c == 0 || theft;
                BiblioArt.plaque(pl, tx, bx, yy, pw, 14, vopts[c][k], on && ok, ok && DaKit.dans(mx, my, bx, yy, pw, 14), ok);
                final int cc = c, kk = k;
                if (ok) hits.add(new Hit(bx, yy, pw, 14, () -> {
                    if (cc == 0) theft = kk == 0;
                    else theftNamed = kk == 1;
                }));
            }
        }
        int dy = vy + 34;
        boolean hv = theft && DaKit.dans(mx, my, rx, dy + 4, colW, 14);
        String val = theft ? theftSecs + " s  ·  S " + secondes(theftSecs * theftFactors[4]) : "—";
        BiblioArt.reglette(reg, tx, rx, dy, colW, theft ? (theftSecs - 2) / 28f : 0f, "Duree de la fouille (rang D)", val, hv);
        if (theft) {
            hits.add(new Hit(rx, dy + 4, colW / 2, 14, () -> theftSecs = Math.max(2, theftSecs - 1)));
            hits.add(new Hit(rx + colW / 2, dy + 4, colW / 2, 14, () -> theftSecs = Math.min(30, theftSecs + 1)));
        }
    }

    private static String secondes(double s) {
        return s == Math.floor(s) ? String.format(Locale.FRANCE, "%.0f s", s) : String.format(Locale.FRANCE, "%.1f s", s);
    }

    private void techniques(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        DaKit.coupeH(g, BiblioArt.CHAMP, x, y - 3, 150, 0, 0, 152, 14, 4, 152, 14);
        tx.inter7(DaKit.inter("Tirage commun (hors secrets)"), x + 6, y + 4, 0xFF7A6E5C, 0);
        int fx = x + 158, fy = y - 2;
        for (int i = 0; i < FILTERS.length; i++) {
            int fw = Math.max(30, Math.round(DaKit.largeurFin(font, DaKit.arcade(FILTERS[i]))) + 14);
            if (fx + fw > x + w) { fx = x; fy += 17; }
            final int f = i;
            BiblioArt.plaque(pl, tx, fx, fy, fw, 12, FILTERS[i], filter == i, DaKit.dans(mx, my, fx, fy, fw, 12), true);
            hits.add(new Hit(fx, fy, fw, 12, () -> setFilter(f)));
            fx += fw + 3;
        }
        int ry = fy + 21, rowH = 14;
        int rows = Math.max(1, (y + h - 12 - ry) / rowH);
        List<Technique> list = filtered();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        // rangées : aplats, puis chevilles, puis textes (lot de la page) — les rangées ne se chevauchent pas
        LotGui aplats = LotGui.ouvrir(g);
        LotBlit chev = BiblioArt.chevilles(g);
        for (int i = 0; i < rows && i + scroll < list.size(); i++) {
            Technique t = list.get(i + scroll);
            int yy = ry + i * rowH;
            float ym = yy + 4;
            if (i % 2 == 0) aplats.fill(x, yy - 3, x + w, yy + rowH - 3, 0x0EFFFFFF);
            aplats.fill(x + 2, yy, x + 5, yy + 8, ScrollArt.rankColor(t.rank()));
            aplats.fill(x + 2, yy, x + 5, yy + 1, 0x60FFFFFF);
            boolean custom = overrides.containsKey(t.id());
            tx.inter9(DaKit.inter(t.name()), x + 11, ym, custom ? DaKit.OR_CLAIR : DaKit.CREME_VAL, 0);
            String type = t.typeLine() + (slotInfo.containsKey(t.id()) ? " · slots " + slotInfo.get(t.id()) : "");
            tx.inter8(DaKit.inter(type), x + w / 2f - 20, ym, 0xFF9A8C78, 0);
            double p = Tirage.chance(t, byRank, overrides);
            String ps = pct(p);
            int px = x + w - 62;
            tx.inter8(DaKit.inter(ps), px + 25, ym, custom ? DaKit.OR_CLAIR : 0xFFC8B4A0, 1);
            final Technique tt = t;
            BiblioArt.cheville(chev, px - 13, yy - 2, false, DaKit.dans(mx, my, px - 12, yy - 2, 12, 11));
            BiblioArt.cheville(chev, px + 50, yy - 2, true, DaKit.dans(mx, my, px + 50, yy - 2, 12, 11));
            hits.add(new Hit(px - 12, yy - 2, 12, 11, () -> { overrides.put(tt.id(), STEPS[Math.max(0, stepOf(Tirage.chance(tt, byRank, overrides)) - 1)]); recompute(); }));
            hits.add(new Hit(px + 50, yy - 2, 12, 11, () -> { overrides.put(tt.id(), STEPS[Math.min(STEPS.length - 1, stepOf(Tirage.chance(tt, byRank, overrides)) + 1)]); recompute(); }));
            if (custom) hits.add(new Hit(x + 11, yy - 1, font.width(DaKit.inter(t.name())), 10, () -> { overrides.remove(tt.id()); recompute(); }));
        }
        aplats.fermer();
        chev.fermer();
        if (list.size() > rows) {
            String more = (scroll + 1) + "-" + Math.min(list.size(), scroll + rows) + " sur " + list.size();
            tx.inter7(DaKit.inter(more), x + w, y + h - 6, 0xFF7A6E5C, 2);
        }
    }

    // ─────────────────────────────────────────────────────────────── secrets : la réserve

    /**
     * Onglet Secrets : recherche (clavier), filtres, tirage commun oui / non, état de la réserve (défaut du type ou
     * réglée, retour au défaut), puis la liste des techniques : celles de la réserve ont leur % propre (− +) et un
     * bouton « Retirer » ; les autres un bouton « Ajouter ».
     */
    private void secrets(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        if (!hasSecrets) {
            DaKit.Paragraphe p = DaKit.paragraphe(font, "Ce serveur n'envoie pas encore de réserve de secrets : "
                + "ShinobiAbilities est à mettre à jour.", w, DaKit.S8, 10, 3);
            tx.paragraphe(p, x, y + 6, 0xFF9A8C78);
            return;
        }
        // recherche
        DaKit.coupeH(g, BiblioArt.CHAMP, x, y - 3, 150, 0, 0, 152, 14, 4, 152, 14);
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        if (query.isEmpty()) {
            tx.inter7(DaKit.inter("Rechercher…"), x + 6, y + 4, 0xFF7A6E5C, 0);
            if (caret) g.fill(x + 5, y, x + 6, y + 8, 0xFFC8B4A0);
        } else {
            float qw = tx.inter7(DaKit.inter(query), x + 6, y + 4, DaKit.CREME_VAL, 0);
            if (caret) g.fill(Math.round(x + 7 + qw), y, Math.round(x + 8 + qw), y + 8, 0xFFC8B4A0);
        }
        // filtres et boutons du haut : leur lot de plaques est refermé avant le filet qui passe sur leur ombre
        LotBlit hautP = BiblioArt.plaques(g);
        int fx = x + 158, fy = y - 2;
        for (int i = 0; i < SFILTERS.length; i++) {
            int fw = Math.max(30, Math.round(DaKit.largeurFin(font, DaKit.arcade(SFILTERS[i]))) + 14);
            if (fx + fw > x + w) { fx = x; fy += 17; }
            final int f = i;
            BiblioArt.plaque(hautP, tx, fx, fy, fw, 12, SFILTERS[i], sfilter == i, DaKit.dans(mx, my, fx, fy, fw, 12), true);
            hits.add(new Hit(fx, fy, fw, 12, () -> setSecretFilter(f)));
            fx += fw + 3;
        }
        // tirage commun + état de la réserve
        int ly = fy + 20;
        tx.etiq(BiblioArt.etiq(font, "Tirage commun"), x, ly + 3, DaKit.OR_ETIQ);
        int bx = x + 86;
        for (int k = 0; k < 2; k++) {
            boolean on = commons == (k == 0);
            int bxx = bx + k * 40;
            BiblioArt.plaque(hautP, tx, bxx, ly - 2, 36, 12, k == 0 ? "Oui" : "Non", on, DaKit.dans(mx, my, bxx, ly - 2, 36, 12), true);
            final int kk = k;
            hits.add(new Hit(bxx, ly - 2, 36, 12, () -> { commons = kk == 0; recompute(); }));
        }
        String etat = (reserveCustom ? "Reserve reglee" : "Reserve par defaut du type") + " : " + reserve.size()
            + " technique" + (reserve.size() > 1 ? "s" : "");
        tx.etiq(BiblioArt.etiq(font, etat), bx + 92, ly + 3, reserveCustom ? DaKit.OR_CLAIR : DaKit.OR_ETIQ);
        if (reserveCustom) {
            int rw = 76, rxx = x + w - rw;
            BiblioArt.plaque(hautP, tx, rxx, ly - 2, rw, 12, "Par defaut", false, DaKit.dans(mx, my, rxx, ly - 2, rw, 12), true);
            hits.add(new Hit(rxx, ly - 2, rw, 12, this::resetReserve));
        }
        hautP.fermer();
        g.fill(x, ly + 12, x + w, ly + 13, 0xFF2D181C);

        // liste
        int ry = ly + 22, rowH = 14;
        int rows = Math.max(1, (y + h - 14 - ry) / rowH);
        List<Secret> list = sFiltered;
        sscroll = Math.max(0, Math.min(sscroll, Math.max(0, list.size() - rows)));
        int nameW = Math.round((w / 2f - 30) / DaKit.S8);
        if (nameW != nameCacheW) { nameCache.clear(); nameCacheW = nameW; }
        // rangées : la plaque d'une rangée déborde sur l'aplat de la suivante (posé par-dessus) : aplats et plaque par
        // rangée, dans cet ordre ; chevilles (sans chevauchement) et textes en un lot chacun pour toute la liste
        LotBlit chev = BiblioArt.chevilles(g);
        for (int i = 0; i < rows && i + sscroll < list.size(); i++) {
            Secret s = list.get(i + sscroll);
            Technique t = s.t();
            boolean in = reserve.containsKey(t.id());
            int yy = ry + i * rowH;
            float ym = yy + 4;
            LotGui aplats = LotGui.ouvrir(g);
            if (in) aplats.fill(x, yy - 3, x + w, yy + rowH - 3, 0x18E8C27A);
            else if (i % 2 == 0) aplats.fill(x, yy - 3, x + w, yy + rowH - 3, 0x0EFFFFFF);
            aplats.fill(x + 2, yy, x + 5, yy + 8, ScrollArt.rankColor(t.rank()));
            aplats.fill(x + 2, yy, x + 5, yy + 1, 0x60FFFFFF);
            if (s.secret()) aplats.fill(x + 7, yy + 3, x + 9, yy + 5, 0xFFC0392B);         // point vermillon : secrète
            aplats.fermer();
            String nm = nameCache.computeIfAbsent(t.id(), k -> DaKit.ellipse(font, t.name(), nameCacheW));
            tx.inter8(DaKit.inter(nm), x + 12, ym, in ? DaKit.OR_CLAIR : DaKit.CREME_VAL, 0);
            tx.inter7(DaKit.inter(s.tags()), x + w / 2f - 14, ym, 0xFF9A8C78, 0);
            int px = x + w - 62, ax = x + w - 134;
            final Technique tt = t;
            if (in) {
                double p = reserve.get(t.id());
                tx.inter8(DaKit.inter(pct(p)), px + 25, ym, DaKit.OR_CLAIR, 1);
                BiblioArt.cheville(chev, px - 13, yy - 2, false, DaKit.dans(mx, my, px - 12, yy - 2, 12, 11));
                BiblioArt.cheville(chev, px + 50, yy - 2, true, DaKit.dans(mx, my, px + 50, yy - 2, 12, 11));
                hits.add(new Hit(px - 12, yy - 2, 12, 11, () -> stepReserve(tt.id(), -1)));
                hits.add(new Hit(px + 50, yy - 2, 12, 11, () -> stepReserve(tt.id(), 1)));
                rangeePlaque(g, ax, yy - 2, "Retirer", DaKit.dans(mx, my, ax, yy - 2, 52, 11));
                hits.add(new Hit(ax, yy - 2, 52, 11, () -> removeReserve(tt.id())));
            } else {
                String c = commons && !s.secret() && techById(t.id()) ? pct(Tirage.chance(t, byRank, overrides)) : "—";
                tx.inter8(DaKit.inter(c), px + 25, ym, 0xFF7A6E5C, 1);
                rangeePlaque(g, ax, yy - 2, "Ajouter", DaKit.dans(mx, my, ax, yy - 2, 52, 11));
                hits.add(new Hit(ax, yy - 2, 52, 11, () -> addReserve(tt)));
            }
        }
        chev.fermer();
        if (list.isEmpty()) {
            tx.inter8(DaKit.inter("Aucune technique ne correspond."), x + w / 2f, ry + 6, 0xFF7A6E5C, 1);
        }
        String bas = String.format(Locale.FRANCE, "En moyenne %.2f rouleau(x) de la reserve par tirage", stats.marked());
        tx.inter7(DaKit.inter(bas), x, y + h - 6, 0xFF9A8C78, 0);
        if (list.size() > rows) {
            String more = (sscroll + 1) + "-" + Math.min(list.size(), sscroll + rows) + " sur " + list.size();
            tx.inter7(DaKit.inter(more), x + w, y + h - 6, 0xFF7A6E5C, 2);
        }
    }

    /** Plaque « Retirer / Ajouter » d'une rangée (posée tout de suite, sous l'aplat de la rangée suivante), libellé au lot. */
    private void rangeePlaque(GuiGraphicsExtractor g, int x, int y, String lib, boolean survol) {
        LotBlit l = BiblioArt.plaques(g);
        BiblioArt.plaque(l, tx, x, y, 52, 11, lib, false, survol, true);
        l.fermer();
    }

    /** La technique fait partie du tirage commun (liste « techs » du serveur, recalculée avec la simulation). */
    private boolean techById(String id) { return commonIds.contains(id); }

    private void simulation(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        titre(g, "Apercu d'un tirage", x, y);
        int sx = x, cw = 48;
        int step = Math.min(cw + 4, (w - 100) / Math.max(1, count));
        // cases (un lot), rouleaux, puis points de la réserve (un lot, par-dessus les rouleaux)
        LotBlit minis = LotBlit.ouvrir(g, BiblioArt.MINI, 48, 26);
        for (int i = 0; i < count; i++) minis.blit(sx + i * step, y + 13, 0, 0, Math.min(48, step - 2), 26);
        minis.fermer();
        LotGui points = LotGui.ouvrir(g);
        for (int i = 0; i < count; i++) {
            if (i < preview.size()) {
                ScrollArt.item(g, sx + (Math.min(48, step - 2) - 22) / 2, y + 14, 22, preview.get(i).rank());
                if (reserve.containsKey(preview.get(i).id())) points.fill(sx + 3, y + 16, sx + 6, y + 19, 0xFFC0392B);
            }
            sx += step;
        }
        points.fermer();
        BiblioArt.plaque(pl, tx, sx + 6, y + 18, 84, 14, "Nouvel apercu", false, DaKit.dans(mx, my, sx + 6, y + 18, 84, 14), true);
        hits.add(new Hit(sx + 6, y + 18, 84, 14, () -> { seed++; recompute(); }));

        int by = y + 52;
        titre(g, "Sur 10 000 tirages", x, by);
        tx.inter8(DaKit.inter("rouleaux par tirage en moyenne" + (reserve.isEmpty() ? "" : ", reserve comprise")),
            x + 4 + font.width(DaKit.arcade("Sur 10 000 tirages")), by + 3.5f, 0xFF9A8C78, 0);
        by += 14;
        double[] pr = stats.perDraw();
        double max = Math.max(0.01, java.util.Arrays.stream(pr).max().orElse(1));
        int barW = w - 120;
        int pas = h < 230 ? 13 : 17;
        // barres : étiquettes de rang, rainures, brocarts (un lot chacun ; les barres ne se chevauchent pas)
        LotBlit fudas = BiblioArt.fudas(g), brocarts = LotBlit.ouvrir(g, BiblioArt.BROCART, 440, 40);
        LotGui rainures = LotGui.ouvrir(g);
        for (int r = 0; r < 5; r++) {
            char rank = "DCBAS".charAt(r);
            BiblioArt.fuda(fudas, x - 1, by - 2, rank);
            int len = (int) Math.max(1, Math.round(barW * (pr[r] / max)));
            rainures.fill(x + 18, by + 4, x + 18 + barW, by + 11, 0x50000000);
            rainures.fill(x + 18, by + 11, x + 18 + barW, by + 12, 0xFF2D181C);
            BiblioArt.brocartH(brocarts, x + 18, by + 4, len, 7, rank);
            String v = pr[r] >= 0.1 ? String.format(Locale.FRANCE, "%.1f", pr[r])
                    : pr[r] >= 0.001 ? String.format(Locale.FRANCE, "%.3f", pr[r]) : String.format(Locale.FRANCE, "%.4f", pr[r]);
            tx.inter8(DaKit.inter(v), x + 24 + barW, by + 7.5f, DaKit.CREME_VAL, 0);
            by += pas;
        }
        fudas.fermer();
        rainures.fermer();
        brocarts.fermer();
        by += 8;
        ligneStat(g, x, by, "Au moins un rang A", String.format(Locale.FRANCE, "%.1f %% des tirages", stats.atLeastA()));
        ligneStat(g, x, by + 12, "Au moins un rang S", String.format(Locale.FRANCE, "%.2f %% des tirages", stats.atLeastS()));
        ligneStat(g, x, by + 24, "Cases vides", String.format(Locale.FRANCE, "%.1f sur %d en moyenne", stats.emptySlots(), count));
        ligneStat(g, x, by + 36, "Rouleaux de la reserve", String.format(Locale.FRANCE, "%.2f par tirage%s", stats.marked(),
            commons ? "" : " (tirage commun coupe)"));
    }

    private void ligneStat(GuiGraphicsExtractor g, int x, int y, String etiq, String val) {
        tx.etiq(BiblioArt.etiq(font, etiq), x, y, DaKit.OR_ETIQ);
        tx.inter8(DaKit.inter(val), x + 120, y, DaKit.CREME_VAL, 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (DaKit.dans(e.x(), e.y(), h.x, h.y, h.w, h.h)) {
                RebornSounds.uiClick();
                h.r.run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (page == PAGE_SECRETS) sscroll -= (int) Math.signum(sy);
        else scroll -= (int) Math.signum(sy);
        return true;
    }

    /** Onglet Secrets : la frappe va dans la recherche. */
    @Override
    public boolean charTyped(CharacterEvent e) {
        if (page == PAGE_SECRETS && hasSecrets) {
            String c = e.codepointAsString();
            if (query.length() < 24 && !c.isEmpty() && (!c.isBlank() || !query.isEmpty())) setQuery(query + c);
            return true;
        }
        return super.charTyped(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (page == PAGE_SECRETS && e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            setQuery(query.substring(0, query.length() - 1));
            return true;
        }
        return super.keyPressed(e);
    }

    /** Banc d'essai. */
    void debugCustom(String id, double p) { overrides.put(id, p); recompute(); }
}
