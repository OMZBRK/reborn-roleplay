package com.reborn.shinobicore.boutique;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Le catalogue des boutiques-lieux (BOUTIQUES.md §4) : {@code plugins/ShinobiCore/boutique-catalogue.yml}, copié depuis
 * le jar au premier démarrage, relu par {@code /boutique recharger}. Prix et conditions vivent ici, jamais dans le mod.
 * Les vivres et les cartes du bazar ne sont pas ici : ils viennent de la nourriture et de {@code places.yml}.
 */
public final class Catalogue {

    /** Les catégories d'un comptoir, dans l'ordre d'affichage. */
    public enum Categorie {
        TENUES("tenues", "Tenues"),
        HAUTS("hauts", "Hauts"),
        BAS("bas", "Bas"),
        MANTEAUX("manteaux", "Manteaux"),
        COIFFES("coiffes", "Coiffes"),
        MASQUES("masques", "Masques"),
        ACCESSOIRES("accessoires", "Accessoires"),
        VIVRES("vivres", "Vivres"),
        CARTES("cartes", "Cartes");

        public final String id, libelle;

        Categorie(String id, String libelle) {
            this.id = id;
            this.libelle = libelle;
        }

        /** Lecture tolérante (« Hauts », « haut », « masque »…) ; null si inconnue. */
        public static Categorie parse(String s) {
            if (s == null || s.isBlank()) return null;
            String n = java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
            for (Categorie c : values()) {
                if (c.id.equals(n) || c.id.equals(n + "s") || c.id.equals(n + "x") || c.name().equalsIgnoreCase(n)) return c;
            }
            return switch (n) {
                case "tenue", "complet", "complets" -> TENUES;
                case "coiffe", "chapeau", "chapeaux", "bandeau", "bandeaux" -> COIFFES;
                case "manteau" -> MANTEAUX;
                case "vivre", "nourriture" -> VIVRES;
                case "carte" -> CARTES;
                default -> null;
            };
        }

        public boolean bazar() { return this == VIVRES || this == CARTES; }
    }

    /** {@code tenue} = garde-robe du personnage ; {@code objet} = cosmétique 3D Nexo remis dans la sacoche. */
    public record Article(String id, String nom, Categorie categorie, boolean tenue, String nexo, String emplacement,
                          long prix, List<String> conditions) {}

    private final ShinobiCore plugin;
    private final File fichier;
    private final Map<String, Article> articles = new LinkedHashMap<>();

    Catalogue(ShinobiCore plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "boutique-catalogue.yml");
    }

    void charger() {
        if (!fichier.exists()) {
            try {
                plugin.saveResource("boutique-catalogue.yml", false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[boutique] catalogue par défaut absent du jar.");
            }
        }
        articles.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        ConfigurationSection s = y.getConfigurationSection("articles");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection a = s.getConfigurationSection(id);
            if (a == null) continue;
            Categorie cat = Categorie.parse(a.getString("categorie", "tenues"));
            if (cat == null || cat.bazar()) {
                plugin.getLogger().warning("[boutique] article " + id + " : catégorie inconnue « " + a.getString("categorie") + " ».");
                continue;
            }
            boolean tenue = !"objet".equalsIgnoreCase(a.getString("genre", "tenue"));
            articles.put(id, new Article(id, a.getString("nom", id), cat, tenue, a.getString("nexo", tenue ? null : id),
                    a.getString("emplacement", null), Math.max(0, a.getLong("prix", 300)), a.getStringList("conditions")));
        }
        plugin.getLogger().info("[boutique] catalogue : " + articles.size() + " article(s).");
    }

    public Collection<Article> tous() { return articles.values(); }

    /** Par identifiant exact, puis sans casse. */
    public Article article(String id) {
        if (id == null) return null;
        Article a = articles.get(id);
        if (a != null) return a;
        for (Article x : articles.values()) if (x.id().equalsIgnoreCase(id)) return x;
        return null;
    }

    public List<Article> de(Categorie c) {
        List<Article> l = new ArrayList<>();
        for (Article a : articles.values()) if (a.categorie() == c) l.add(a);
        return l;
    }
}
