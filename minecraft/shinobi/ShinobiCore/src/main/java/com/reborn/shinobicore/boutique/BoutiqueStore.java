package com.reborn.shinobicore.boutique;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.entreprise.EntrepriseService;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les boutiques-lieux sur disque : {@code plugins/ShinobiCore/boutiques.yml}, écrit à chaque changement (rares :
 * commandes staff). Questions posées par le reste : « quel comptoir est ici ? », « quelle boutique s'appelle ainsi ? ».
 */
public final class BoutiqueStore {

    private final ShinobiCore plugin;
    private final File fichier;
    private final Map<String, Boutique> parId = new LinkedHashMap<>();

    BoutiqueStore(ShinobiCore plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "boutiques.yml");
    }

    public List<Boutique> toutes() { return new ArrayList<>(parId.values()); }

    /** Par identifiant ou par nom (sans accents ni casse). */
    public Boutique trouver(String s) {
        if (s == null || s.isBlank()) return null;
        String k = EntrepriseService.slug(s);
        Boutique b = parId.get(k);
        if (b != null) return b;
        for (Boutique x : parId.values()) if (EntrepriseService.slug(x.nom).equals(k)) return x;
        return null;
    }

    public Boutique creer(String nom, Boutique.Genre genre, String village, String entreprise, String par) {
        String id = EntrepriseService.slug(nom);
        if (id.isEmpty() || parId.containsKey(id)) return null;
        Boutique b = new Boutique(id, nom.trim(), genre, village, entreprise, System.currentTimeMillis(), par);
        parId.put(id, b);
        sauver();
        return b;
    }

    public boolean supprimer(Boutique b) {
        boolean ok = b != null && parId.remove(b.id) != null;
        if (ok) sauver();
        return ok;
    }

    /** Le comptoir à cet endroit (bloc exact, puis à {@code tolerance} blocs), avec sa boutique. */
    public Map.Entry<Boutique, Boutique.Comptoir> comptoirA(Location l, int tolerance) {
        Map.Entry<Boutique, Boutique.Comptoir> proche = null;
        for (Boutique b : parId.values()) {
            for (Boutique.Comptoir c : b.comptoirs) {
                if (c.entite != null) continue;
                if (c.a(l, 0)) return Map.entry(b, c);
                if (proche == null && tolerance > 0 && c.a(l, tolerance)) proche = Map.entry(b, c);
            }
        }
        return proche;
    }

    /** Le comptoir porté par cette entité : PNJ précis, sinon meuble Nexo à un bloc près. */
    public Map.Entry<Boutique, Boutique.Comptoir> comptoirDe(Entity e) {
        if (e == null) return null;
        for (Boutique b : parId.values()) {
            for (Boutique.Comptoir c : b.comptoirs) {
                if (e.getUniqueId().equals(c.entite)) return Map.entry(b, c);
            }
        }
        for (Boutique b : parId.values()) {
            for (Boutique.Comptoir c : b.comptoirs) {
                if (c.entite == null && "entite".equals(c.type) && c.a(e.getLocation(), 1)) return Map.entry(b, c);
            }
        }
        return null;
    }

    /* ================================================================ disque */

    void charger() {
        parId.clear();
        if (!fichier.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Boutique.Genre g = Boutique.Genre.parse(s.getString("genre"));
            if (g == null) continue;
            Boutique b = new Boutique(id, s.getString("nom", id), g, s.getString("village", ""),
                    s.getString("entreprise", ""), s.getLong("cree-le", 0), s.getString("cree-par", "?"));
            b.ouverte = s.getBoolean("ouverte", true);
            b.camDistance = s.getDouble("camera.distance", 2.7);
            b.camHauteur = s.getDouble("camera.hauteur", 1.0);
            for (Map<?, ?> m : s.getMapList("comptoirs")) {
                try {
                    Boutique.Comptoir c = new Boutique.Comptoir(String.valueOf(m.get("type")), String.valueOf(m.get("monde")),
                            ((Number) m.get("x")).intValue(), ((Number) m.get("y")).intValue(), ((Number) m.get("z")).intValue(),
                            uuid(m.get("entite")), String.valueOf(m.get("libelle")));
                    if (m.get("categories") instanceof List<?> l) {
                        for (Object o : l) {
                            Catalogue.Categorie cat = Catalogue.Categorie.parse(String.valueOf(o));
                            if (cat != null && !c.categories.contains(cat)) c.categories.add(cat);
                        }
                    }
                    if (m.get("articles") instanceof List<?> l) for (Object o : l) c.articles.add(String.valueOf(o));
                    if (c.categories.isEmpty()) c.categories.addAll(g.parDefaut());
                    b.comptoirs.add(c);
                } catch (RuntimeException ignored) {
                    // comptoir illisible
                }
            }
            for (Map<?, ?> m : s.getMapList("estrades")) {
                try {
                    b.estrades.add(new Boutique.Estrade(String.valueOf(m.get("monde")), ((Number) m.get("x")).doubleValue(),
                            ((Number) m.get("y")).doubleValue(), ((Number) m.get("z")).doubleValue(),
                            ((Number) m.get("yaw")).floatValue()));
                } catch (RuntimeException ignored) {
                    // estrade illisible
                }
            }
            ConfigurationSection px = s.getConfigurationSection("prix");
            if (px != null) for (String k : px.getKeys(false)) b.prix.put(k, Math.max(0, px.getLong(k)));
            b.exclus.addAll(s.getStringList("exclus"));
            parId.put(id, b);
        }
    }

    void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        for (Boutique b : parId.values()) {
            String k = b.id;
            y.set(k + ".nom", b.nom);
            y.set(k + ".genre", b.genre.name().toLowerCase(java.util.Locale.ROOT));
            y.set(k + ".village", b.village);
            y.set(k + ".entreprise", b.entreprise);
            y.set(k + ".ouverte", b.ouverte);
            y.set(k + ".camera.distance", b.camDistance);
            y.set(k + ".camera.hauteur", b.camHauteur);
            y.set(k + ".cree-le", b.creeLe);
            y.set(k + ".cree-par", b.creePar);
            List<Map<String, Object>> cs = new ArrayList<>();
            for (Boutique.Comptoir c : b.comptoirs) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("type", c.type);
                o.put("monde", c.monde);
                o.put("x", c.x);
                o.put("y", c.y);
                o.put("z", c.z);
                if (c.entite != null) o.put("entite", c.entite.toString());
                o.put("libelle", c.libelle);
                List<String> cats = new ArrayList<>();
                for (Catalogue.Categorie cat : c.categories) cats.add(cat.id);
                o.put("categories", cats);
                o.put("articles", new ArrayList<>(c.articles));
                cs.add(o);
            }
            y.set(k + ".comptoirs", cs);
            List<Map<String, Object>> es = new ArrayList<>();
            for (Boutique.Estrade e : b.estrades) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("monde", e.monde());
                o.put("x", e.x());
                o.put("y", e.y());
                o.put("z", e.z());
                o.put("yaw", (double) e.yaw());
                es.add(o);
            }
            y.set(k + ".estrades", es);
            for (Map.Entry<String, Long> p : b.prix.entrySet()) y.set(k + ".prix." + p.getKey(), p.getValue());
            y.set(k + ".exclus", new ArrayList<>(b.exclus));
        }
        try {
            y.save(fichier);
        } catch (IOException ex) {
            plugin.getLogger().warning("[boutique] sauvegarde impossible : " + ex.getMessage());
        }
    }

    private static UUID uuid(Object o) {
        try { return o == null ? null : UUID.fromString(String.valueOf(o)); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
