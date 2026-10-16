package com.reborn.shinobicore.communication;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.communication.CommData.Cloche;
import com.reborn.shinobicore.communication.CommData.Dest;
import com.reborn.shinobicore.communication.CommData.Etat;
import com.reborn.shinobicore.communication.CommData.Missive;
import com.reborn.shinobicore.communication.CommData.Point;
import com.reborn.shinobicore.communication.CommData.TypeVoliere;
import com.reborn.shinobicore.communication.CommData.Voliere;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistance de la cloche et des volières, dossier {@code plugins/ShinobiCore/communication/} :
 * {@code points.yml} (cloches et volières posées par le staff) et {@code missives.yml} (chaque copie : en vol, en
 * attente à une volière, prise — l'objet ne porte que son identifiant —, brûlée gardée 30 jours pour le staff).
 * Écrit au plus toutes les 2 s après un changement, et à l'arrêt.
 */
public final class CommStore {

    private final ShinobiCore plugin;
    private final File dir, fPoints, fMissives;
    final Map<String, Cloche> cloches = new LinkedHashMap<>();
    final Map<String, Voliere> volieres = new LinkedHashMap<>();
    final Map<String, Missive> missives = new LinkedHashMap<>();
    private volatile boolean salePoints, saleMissives;

    CommStore(ShinobiCore plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "communication");
        this.fPoints = new File(dir, "points.yml");
        this.fMissives = new File(dir, "missives.yml");
    }

    void start() {
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("[cloche] Dossier communication/ impossible à créer.");
        charger();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (salePoints || saleMissives) sauver(); }, 40L, 40L);
    }

    void marquerPoints() { salePoints = true; }

    void marquerMissives() { saleMissives = true; }

    private static UUID uuid(Object o) {
        try { return o == null || String.valueOf(o).isBlank() ? null : UUID.fromString(String.valueOf(o)); }
        catch (IllegalArgumentException e) { return null; }
    }

    /* ================================================================ lecture */

    private void point(ConfigurationSection s, Point p) {
        p.faction = s.getString("faction", "");
        p.monde = s.getString("monde", "world");
        p.x = s.getInt("x");
        p.y = s.getInt("y");
        p.z = s.getInt("z");
        p.entite = uuid(s.getString("entite"));
        p.pose = s.getLong("pose");
        p.posePar = uuid(s.getString("pose-par"));
    }

    private void charger() {
        if (fPoints.exists()) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(fPoints);
            ConfigurationSection cs = c.getConfigurationSection("cloches");
            if (cs != null) for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                Cloche k = new Cloche();
                k.id = id;
                point(s, k);
                k.lieu = s.getString("lieu", "");
                k.rayon = s.getInt("rayon", 250);
                k.derniere = s.getLong("derniere");
                k.taireJusqua = s.getLong("taire-jusqua");
                cloches.put(id, k);
            }
            ConfigurationSection vs = c.getConfigurationSection("volieres");
            if (vs != null) for (String id : vs.getKeys(false)) {
                ConfigurationSection s = vs.getConfigurationSection(id);
                if (s == null) continue;
                Voliere v = new Voliere();
                v.id = id;
                point(s, v);
                v.nom = s.getString("nom", id);
                TypeVoliere t = TypeVoliere.lire(s.getString("type"));
                v.type = t == null ? TypeVoliere.GRANDE : t;
                volieres.put(id, v);
            }
        }
        if (fMissives.exists()) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(fMissives);
            ConfigurationSection ms = c.getConfigurationSection("missives");
            if (ms != null) for (String id : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(id);
                if (s == null) continue;
                Missive m = new Missive();
                m.id = id;
                m.groupe = s.getString("groupe", id);
                m.auteur = uuid(s.getString("auteur"));
                m.auteurCompte = uuid(s.getString("auteur-compte"));
                m.auteurNom = s.getString("auteur-nom", "");
                m.signee = s.getBoolean("signee");
                for (Map<?, ?> d : s.getMapList("dests")) {
                    Object nom = d.get("nom");
                    m.dests.add(new Dest(nom == null ? "?" : String.valueOf(nom), uuid(d.get("perso"))));
                }
                m.index = s.getInt("index");
                m.texte = s.getString("texte", "");
                m.depart = s.getString("depart", "");
                m.arrivee = s.getString("arrivee", "");
                m.envoye = s.getLong("envoye");
                m.arrive = s.getLong("arrive");
                try { m.etat = Etat.valueOf(s.getString("etat", "VOL")); } catch (IllegalArgumentException e) { m.etat = Etat.VOL; }
                m.scellee = s.getBoolean("scellee", true);
                m.briseePar = uuid(s.getString("brisee-par"));
                m.briseeLe = s.getLong("brisee-le");
                m.preneur = uuid(s.getString("preneur"));
                m.priseLe = s.getLong("prise-le");
                m.prevenu = s.getBoolean("prevenu");
                m.transferee = s.getBoolean("transferee");
                missives.put(id, m);
            }
        }
        plugin.getLogger().info("[cloche] " + cloches.size() + " cloche(s), " + volieres.size() + " volière(s), "
                + missives.size() + " missive(s).");
    }

    /* ================================================================ écriture */

    private static void point(YamlConfiguration c, String b, Point p) {
        c.set(b + "faction", p.faction);
        c.set(b + "monde", p.monde);
        c.set(b + "x", p.x);
        c.set(b + "y", p.y);
        c.set(b + "z", p.z);
        if (p.entite != null) c.set(b + "entite", p.entite.toString());
        c.set(b + "pose", p.pose);
        if (p.posePar != null) c.set(b + "pose-par", p.posePar.toString());
    }

    synchronized void sauver() {
        if (salePoints) {
            salePoints = false;
            YamlConfiguration c = new YamlConfiguration();
            for (Cloche k : cloches.values()) {
                String b = "cloches." + k.id + ".";
                point(c, b, k);
                c.set(b + "lieu", k.lieu);
                c.set(b + "rayon", k.rayon);
                c.set(b + "derniere", k.derniere);
                if (k.taireJusqua > 0) c.set(b + "taire-jusqua", k.taireJusqua);
            }
            for (Voliere v : volieres.values()) {
                String b = "volieres." + v.id + ".";
                point(c, b, v);
                c.set(b + "nom", v.nom);
                c.set(b + "type", v.type.name());
            }
            ecrire(c, fPoints);
        }
        if (saleMissives) {
            saleMissives = false;
            YamlConfiguration c = new YamlConfiguration();
            for (Missive m : missives.values()) {
                String b = "missives." + m.id + ".";
                c.set(b + "groupe", m.groupe);
                if (m.auteur != null) c.set(b + "auteur", m.auteur.toString());
                if (m.auteurCompte != null) c.set(b + "auteur-compte", m.auteurCompte.toString());
                c.set(b + "auteur-nom", m.auteurNom);
                c.set(b + "signee", m.signee);
                List<Map<String, Object>> ds = new ArrayList<>();
                for (Dest d : m.dests) {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("nom", d.nom());
                    if (d.perso() != null) x.put("perso", d.perso().toString());
                    ds.add(x);
                }
                c.set(b + "dests", ds);
                c.set(b + "index", m.index);
                c.set(b + "texte", m.texte);
                c.set(b + "depart", m.depart);
                c.set(b + "arrivee", m.arrivee);
                c.set(b + "envoye", m.envoye);
                c.set(b + "arrive", m.arrive);
                c.set(b + "etat", m.etat.name());
                c.set(b + "scellee", m.scellee);
                if (m.briseePar != null) c.set(b + "brisee-par", m.briseePar.toString());
                if (m.briseeLe > 0) c.set(b + "brisee-le", m.briseeLe);
                if (m.preneur != null) c.set(b + "preneur", m.preneur.toString());
                if (m.priseLe > 0) c.set(b + "prise-le", m.priseLe);
                if (m.prevenu) c.set(b + "prevenu", true);
                if (m.transferee) c.set(b + "transferee", true);
            }
            ecrire(c, fMissives);
        }
    }

    private void ecrire(YamlConfiguration c, File f) {
        try {
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            c.save(tmp);
            java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("[cloche] Écriture de " + f.getName() + " impossible : " + e.getMessage());
        }
    }
}
