package com.reborn.shinobicore.entreprise;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Registre des <b>entreprises</b> (docs/gameplay/ENTREPRISES.md) : données dans {@code plugins/ShinobiCore/entreprises.yml},
 * écrites à chaque changement (rares : commandes staff). Questions posées par les autres systèmes :
 * « ce personnage est-il membre d'une entreprise de tel type ? » (cuisinier = membre d'un RESTAURANT), « quel poste
 * est à cet endroit ? ».
 */
public final class EntrepriseService {

    private final ShinobiCore plugin;
    private final File fichier;
    private final Map<String, Entreprise> parId = new LinkedHashMap<>();

    public EntrepriseService(ShinobiCore plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "entreprises.yml");
    }

    /* ================================================================ requêtes */

    public List<Entreprise> toutes() { return new ArrayList<>(parId.values()); }

    /** Par identifiant ou par nom (sans accents ni casse) ; null sinon. */
    public Entreprise trouver(String s) {
        if (s == null || s.isBlank()) return null;
        String k = slug(s);
        Entreprise e = parId.get(k);
        if (e != null) return e;
        for (Entreprise x : parId.values()) if (slug(x.nom).equals(k)) return x;
        return null;
    }

    /** Entreprises dont ce personnage est membre. */
    public List<Entreprise> de(UUID perso) {
        List<Entreprise> l = new ArrayList<>();
        if (perso == null) return l;
        for (Entreprise e : parId.values()) if (e.membres.containsKey(perso)) l.add(e);
        return l;
    }

    /** Ce personnage est-il membre d'une entreprise de ce type ? (« cuisinier » = RESTAURANT) */
    public boolean membreDuType(UUID perso, Entreprise.Type type) {
        for (Entreprise e : de(perso)) if (e.type == type) return true;
        return false;
    }

    public boolean cuisinier(ShinobiCharacter c) {
        return c != null && membreDuType(c.id(), Entreprise.Type.RESTAURANT);
    }

    /** Le poste à cet endroit (tolérance en blocs), avec son entreprise ; null sinon. */
    public Map.Entry<Entreprise, Entreprise.Poste> posteA(Location l, int tolerance) {
        if (l == null) return null;
        Map.Entry<Entreprise, Entreprise.Poste> exact = null, proche = null;
        for (Entreprise e : parId.values()) {
            for (Entreprise.Poste p : e.postes) {
                if (p.proche(l, 0)) exact = Map.entry(e, p);
                else if (proche == null && p.proche(l, tolerance)) proche = Map.entry(e, p);
            }
        }
        return exact != null ? exact : proche;
    }

    /** « Ichiraku (employé) », plusieurs séparées par « · », ou "" : pour la fiche du Poste de garde. */
    public String resume(UUID perso) {
        StringBuilder b = new StringBuilder();
        for (Entreprise e : de(perso)) {
            if (!b.isEmpty()) b.append(" · ");
            b.append(e.nom).append(" (").append(e.membres.get(perso).role().libelle).append(')');
        }
        return b.toString();
    }

    /* ================================================================ écriture */

    public Entreprise creer(String nom, Entreprise.Type type, String creePar) {
        String id = slug(nom);
        if (id.isEmpty() || parId.containsKey(id)) return null;
        Entreprise e = new Entreprise(id, nom.trim(), type, null, System.currentTimeMillis(), creePar);
        parId.put(id, e);
        sauver();
        return e;
    }

    public boolean supprimer(Entreprise e) {
        boolean ok = e != null && parId.remove(e.id) != null;
        if (ok) sauver();
        return ok;
    }

    public boolean renommer(Entreprise e, String nom) {
        if (e == null || nom == null || nom.isBlank()) return false;
        Entreprise autre = trouver(nom);
        if (autre != null && autre != e) return false;
        e.nom = nom.trim();
        sauver();
        return true;
    }

    public void membre(Entreprise e, UUID compte, ShinobiCharacter c, Entreprise.Role role) {
        e.membres.put(c.id(), new Entreprise.Membre(c.id(), compte, c.name(), c.hasPlayerId() ? c.playerId() : -1, role));
        if (role == Entreprise.Role.GERANT && e.responsable == null) e.responsable = c.id();
        sauver();
    }

    public Entreprise.Membre retirerMembre(Entreprise e, UUID perso) {
        Entreprise.Membre m = e.membres.remove(perso);
        if (m != null && perso.equals(e.responsable)) {
            e.responsable = null;
            for (Entreprise.Membre x : e.membres.values()) {
                if (x.role() == Entreprise.Role.GERANT) { e.responsable = x.perso(); break; }
            }
        }
        if (m != null) sauver();
        return m;
    }

    public boolean role(Entreprise e, UUID perso, Entreprise.Role role) {
        Entreprise.Membre m = e.membres.get(perso);
        if (m == null) return false;
        e.membres.put(perso, new Entreprise.Membre(m.perso(), m.compte(), m.nom(), m.numero(), role));
        if (role == Entreprise.Role.GERANT && e.responsable == null) e.responsable = perso;
        if (role == Entreprise.Role.EMPLOYE && perso.equals(e.responsable)) e.responsable = null;
        sauver();
        return true;
    }

    public Entreprise.Poste ajouterPoste(Entreprise e, Location l, String libelle) {
        Entreprise.Poste p = new Entreprise.Poste(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                libelle == null || libelle.isBlank() ? e.type.poste : libelle.trim());
        e.postes.add(p);
        sauver();
        return p;
    }

    public Entreprise.Poste retirerPoste(Entreprise e, Location l) {
        for (Entreprise.Poste p : new ArrayList<>(e.postes)) {
            if (p.proche(l, 0)) {
                e.postes.remove(p);
                sauver();
                return p;
            }
        }
        return null;
    }

    /* ================================================================ disque */

    public void charger() {
        parId.clear();
        if (!fichier.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Entreprise.Type t = Entreprise.Type.parse(s.getString("type"));
            if (t == null) continue;
            Entreprise e = new Entreprise(id, s.getString("nom", id), t, uuid(s.getString("responsable")),
                    s.getLong("cree-le", 0), s.getString("cree-par", "?"));
            for (Map<?, ?> m : s.getMapList("membres")) {
                UUID perso = uuid(String.valueOf(m.get("perso")));
                Entreprise.Role r = Entreprise.Role.parse(String.valueOf(m.get("role")));
                if (perso == null || r == null) continue;
                Object n = m.get("numero");
                e.membres.put(perso, new Entreprise.Membre(perso, uuid(String.valueOf(m.get("compte"))),
                        String.valueOf(m.get("nom")), n instanceof Number nb ? nb.intValue() : -1, r));
            }
            for (Map<?, ?> m : s.getMapList("postes")) {
                try {
                    e.postes.add(new Entreprise.Poste(String.valueOf(m.get("monde")), ((Number) m.get("x")).intValue(),
                            ((Number) m.get("y")).intValue(), ((Number) m.get("z")).intValue(),
                            String.valueOf(m.get("libelle"))));
                } catch (RuntimeException ignored) {
                    // poste illisible
                }
            }
            parId.put(id, e);
        }
    }

    public void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        for (Entreprise e : parId.values()) {
            String k = e.id;
            y.set(k + ".nom", e.nom);
            y.set(k + ".type", e.type.name());
            y.set(k + ".responsable", e.responsable == null ? null : e.responsable.toString());
            y.set(k + ".cree-le", e.creeLe);
            y.set(k + ".cree-par", e.creePar);
            List<Map<String, Object>> ms = new ArrayList<>();
            for (Entreprise.Membre m : e.membres.values()) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("perso", m.perso().toString());
                o.put("compte", m.compte() == null ? "" : m.compte().toString());
                o.put("nom", m.nom());
                o.put("numero", m.numero());
                o.put("role", m.role().name());
                ms.add(o);
            }
            y.set(k + ".membres", ms);
            List<Map<String, Object>> ps = new ArrayList<>();
            for (Entreprise.Poste p : e.postes) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("monde", p.monde());
                o.put("x", p.x());
                o.put("y", p.y());
                o.put("z", p.z());
                o.put("libelle", p.libelle());
                ps.add(o);
            }
            y.set(k + ".postes", ps);
        }
        try {
            y.save(fichier);
        } catch (IOException ex) {
            plugin.getLogger().warning("[entreprises] sauvegarde impossible : " + ex.getMessage());
        }
    }

    private static UUID uuid(String s) {
        try { return s == null || s.isBlank() || "null".equals(s) ? null : UUID.fromString(s); }
        catch (IllegalArgumentException ex) { return null; }
    }

    /** « Ichiraku Ramen » → {@code ichiraku_ramen}. */
    public static String slug(String s) {
        String n = Normalizer.normalize(s == null ? "" : s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return n.replaceAll("^_+|_+$", "");
    }
}
