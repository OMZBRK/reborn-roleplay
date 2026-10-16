package com.reborn.shinobicore.faction;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.faction.FactionData.Annonce;
import com.reborn.shinobicore.faction.FactionData.Charge;
import com.reborn.shinobicore.faction.FactionData.Faction;
import com.reborn.shinobicore.faction.FactionData.GradeSection;
import com.reborn.shinobicore.faction.FactionData.Ligne;
import com.reborn.shinobicore.faction.FactionData.Membre;
import com.reborn.shinobicore.faction.FactionData.Section;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistance du système Faction, dossier {@code plugins/ShinobiCore/factions/} :
 * <ul>
 *   <li>{@code factions.yml} — factions (seuils, taxe, charges, annonces, journal court), sections (grades, places,
 *       tenue, caisse et mouvements, journal court) ;</li>
 *   <li>{@code membres.yml} — le registre : un personnage par clé (nom, grade du village, village, faction, section,
 *       grade de section), tenu à jour à chaque connexion, pour compter les hors-ligne.</li>
 * </ul>
 * Écrit au plus toutes les 2 s après un changement, et à l'arrêt (comme la banque). Le journal complet est le journal
 * universel (catégorie {@code FACTION}) ; les listes courtes d'ici ne servent qu'au volet.
 */
public final class FactionStore {

    static final int MAX_LIGNES = 120;

    private final ShinobiCore plugin;
    private final File dir, fFactions, fMembres;
    final Map<String, Faction> factions = new LinkedHashMap<>();
    final Map<String, Section> sections = new LinkedHashMap<>();
    final Map<UUID, Membre> membres = new ConcurrentHashMap<>();
    boolean migre;
    private volatile boolean sale, saleMembres;

    FactionStore(ShinobiCore plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "factions");
        this.fFactions = new File(dir, "factions.yml");
        this.fMembres = new File(dir, "membres.yml");
    }

    void start() {
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("[faction] Dossier factions/ impossible à créer.");
        charger();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (sale || saleMembres) sauver(); }, 40L, 40L);
    }

    void marquer() { sale = true; }

    void marquerMembres() { saleMembres = true; }

    static void ajouter(List<Ligne> l, Ligne x) {
        l.add(0, x);
        while (l.size() > MAX_LIGNES) l.remove(l.size() - 1);
    }

    /* ================================================================ lecture */

    private static UUID uuid(String s) {
        try { return s == null || s.isBlank() ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static List<Ligne> lignes(ConfigurationSection s, String k) {
        List<Ligne> out = new ArrayList<>();
        if (s == null) return out;
        for (Map<?, ?> m : s.getMapList(k)) {
            Object le = m.get("le"), t = m.get("t"), n = m.get("n");
            out.add(new Ligne(le instanceof Number x ? x.longValue() : 0L, t == null ? "" : String.valueOf(t),
                    n instanceof Number x ? x.longValue() : 0L));
        }
        return out;
    }

    private static List<Map<String, Object>> ecrire(List<Ligne> l) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Ligne x : l) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("le", x.le());
            m.put("t", x.texte());
            if (x.montant() != 0) m.put("n", x.montant());
            out.add(m);
        }
        return out;
    }

    private void charger() {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(fFactions);
        migre = c.getBoolean("migre", false);
        ConfigurationSection fs = c.getConfigurationSection("factions");
        if (fs != null) for (String tag : fs.getKeys(false)) {
            ConfigurationSection s = fs.getConfigurationSection(tag);
            if (s == null) continue;
            Faction f = new Faction();
            f.tag = tag.toUpperCase(java.util.Locale.ROOT);
            f.nom = s.getString("nom", f.tag);
            f.type = "ORGANISATION".equalsIgnoreCase(s.getString("type")) ? FactionData.Type.ORGANISATION : FactionData.Type.VILLAGE;
            f.embleme = s.getString("embleme", "");
            f.village = s.getString("village", "");
            f.chef = uuid(s.getString("chef"));
            f.serveur = s.getString("serveur", "shinobi");
            f.taux = s.getInt("taux", -1);
            f.cree = s.getLong("cree", 0);
            ConfigurationSection se = s.getConfigurationSection("seuils");
            if (se != null) for (String k : se.getKeys(false)) {
                Rank r = Droits.rang(se.getString(k));
                if (r != null && Droits.seuilConnu(k)) f.seuils.put(k, r);
            }
            ConfigurationSection ch = s.getConfigurationSection("charges");
            if (ch != null) for (String id : ch.getKeys(false)) {
                ConfigurationSection x = ch.getConfigurationSection(id);
                if (x == null) continue;
                Charge k = new Charge();
                k.id = id;
                k.nom = x.getString("nom", id);
                k.publique = x.getBoolean("publique", false);
                for (String d : x.getStringList("droits")) if (Droits.droitCharge(d)) k.droits.add(d);
                for (String t : x.getStringList("titulaires")) { UUID u = uuid(t); if (u != null) k.titulaires.add(u); }
                f.charges.put(id, k);
            }
            for (Map<?, ?> m : s.getMapList("annonces")) {
                Annonce a = new Annonce();
                a.le = m.get("le") instanceof Number n ? n.longValue() : 0L;
                a.auteur = uuid(String.valueOf(m.get("auteur")));
                a.titre = m.get("titre") == null ? "" : String.valueOf(m.get("titre"));
                a.section = m.get("section") == null ? "" : String.valueOf(m.get("section"));
                a.texte = m.get("texte") == null ? "" : String.valueOf(m.get("texte"));
                f.annonces.add(a);
            }
            f.journal.addAll(lignes(s, "journal"));
            ConfigurationSection ts = s.getConfigurationSection("taxe-semaine");
            if (ts != null) for (String k : ts.getKeys(false)) f.taxeSemaine.put(k, ts.getLong(k));
            factions.put(f.tag, f);
        }
        ConfigurationSection ss = c.getConfigurationSection("sections");
        if (ss != null) for (String id : ss.getKeys(false)) {
            ConfigurationSection s = ss.getConfigurationSection(id);
            if (s == null) continue;
            Section x = new Section();
            x.id = id;
            x.faction = s.getString("faction", "");
            x.nom = s.getString("nom", id);
            x.insigne = Droits.insigne(s.getString("insigne"));
            x.chef = uuid(s.getString("chef"));
            x.places = Math.max(1, s.getInt("places", 8));
            x.cachee = s.getBoolean("cachee", false);
            x.tenue = s.getString("tenue", "");
            x.tenueNom = s.getString("tenue-nom", "");
            x.accessoire = s.getString("accessoire", "");
            x.caisse = s.getLong("caisse", 0);
            x.cree = s.getLong("cree", 0);
            x.creePar = uuid(s.getString("cree-par"));
            x.lieux.addAll(s.getStringList("lieux"));
            for (Map<?, ?> m : s.getMapList("grades")) {
                GradeSection g = new GradeSection();
                g.id = String.valueOf(m.get("id"));
                g.nom = m.get("nom") == null ? g.id : String.valueOf(m.get("nom"));
                if (m.get("droits") instanceof List<?> l) for (Object d : l) if (Droits.droitSection(String.valueOf(d))) g.droits.add(String.valueOf(d));
                x.grades.add(g);
            }
            x.mouvements.addAll(lignes(s, "mouvements"));
            x.journal.addAll(lignes(s, "journal"));
            if (x.grades.isEmpty()) {
                GradeSection g = new GradeSection();
                g.id = "chef"; g.nom = "Chef"; g.droits.addAll(Droits.tousSection());
                x.grades.add(g);
            }
            sections.put(id, x);
        }
        YamlConfiguration m = YamlConfiguration.loadConfiguration(fMembres);
        ConfigurationSection ms = m.getConfigurationSection("membres");
        if (ms != null) for (String k : ms.getKeys(false)) {
            UUID id = uuid(k);
            ConfigurationSection s = ms.getConfigurationSection(k);
            if (id == null || s == null) continue;
            Membre x = new Membre();
            x.perso = id;
            x.owner = uuid(s.getString("owner"));
            x.nom = s.getString("nom", "");
            x.sexe = s.getString("sexe", "Homme");
            x.rang = Rank.from(s.getString("rang", "GENIN"));
            x.village = s.getString("village", "");
            x.faction = s.getString("faction", "");
            x.section = s.getString("section", "");
            x.grade = s.getString("grade", "");
            x.depuis = s.getLong("depuis", 0);
            x.vu = s.getLong("vu", 0);
            x.pid = s.getInt("pid", -1);
            x.mort = s.getBoolean("mort", false);
            membres.put(id, x);
        }
        plugin.getLogger().info("[faction] " + factions.size() + " faction(s), " + sections.size() + " section(s), "
                + membres.size() + " personnage(s) au registre.");
    }

    /* ================================================================ écriture */

    synchronized void sauver() {
        if (sale) {
            sale = false;
            YamlConfiguration c = new YamlConfiguration();
            c.set("migre", migre);
            for (Faction f : factions.values()) {
                String b = "factions." + f.tag + ".";
                c.set(b + "nom", f.nom);
                c.set(b + "type", f.type.name());
                c.set(b + "embleme", f.embleme);
                if (!f.village.isEmpty()) c.set(b + "village", f.village);
                if (f.chef != null) c.set(b + "chef", f.chef.toString());
                c.set(b + "serveur", f.serveur);
                if (f.taux >= 0) c.set(b + "taux", f.taux);
                c.set(b + "cree", f.cree);
                for (Map.Entry<String, Rank> e : f.seuils.entrySet()) c.set(b + "seuils." + e.getKey(), e.getValue().name());
                for (Charge k : f.charges.values()) {
                    String kb = b + "charges." + k.id + ".";
                    c.set(kb + "nom", k.nom);
                    c.set(kb + "publique", k.publique);
                    c.set(kb + "droits", new ArrayList<>(k.droits));
                    List<String> t = new ArrayList<>();
                    for (UUID u : k.titulaires) t.add(u.toString());
                    c.set(kb + "titulaires", t);
                }
                List<Map<String, Object>> an = new ArrayList<>();
                for (Annonce a : f.annonces) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("le", a.le);
                    if (a.auteur != null) m.put("auteur", a.auteur.toString());
                    m.put("titre", a.titre);
                    if (!a.section.isEmpty()) m.put("section", a.section);
                    m.put("texte", a.texte);
                    an.add(m);
                }
                c.set(b + "annonces", an);
                c.set(b + "journal", ecrire(f.journal));
                for (Map.Entry<String, Long> e : f.taxeSemaine.entrySet()) c.set(b + "taxe-semaine." + e.getKey(), e.getValue());
            }
            for (Section x : sections.values()) {
                String b = "sections." + x.id + ".";
                c.set(b + "faction", x.faction);
                c.set(b + "nom", x.nom);
                c.set(b + "insigne", x.insigne);
                if (x.chef != null) c.set(b + "chef", x.chef.toString());
                c.set(b + "places", x.places);
                c.set(b + "cachee", x.cachee);
                c.set(b + "tenue", x.tenue);
                c.set(b + "tenue-nom", x.tenueNom);
                c.set(b + "accessoire", x.accessoire);
                c.set(b + "caisse", x.caisse);
                c.set(b + "cree", x.cree);
                if (x.creePar != null) c.set(b + "cree-par", x.creePar.toString());
                c.set(b + "lieux", new ArrayList<>(x.lieux));
                List<Map<String, Object>> gl = new ArrayList<>();
                for (GradeSection g : x.grades) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", g.id);
                    m.put("nom", g.nom);
                    m.put("droits", new ArrayList<>(g.droits));
                    gl.add(m);
                }
                c.set(b + "grades", gl);
                c.set(b + "mouvements", ecrire(x.mouvements));
                c.set(b + "journal", ecrire(x.journal));
            }
            enregistrer(c, fFactions);
        }
        if (saleMembres) {
            saleMembres = false;
            YamlConfiguration m = new YamlConfiguration();
            for (Membre x : membres.values()) {
                String b = "membres." + x.perso + ".";
                if (x.owner != null) m.set(b + "owner", x.owner.toString());
                m.set(b + "nom", x.nom);
                m.set(b + "sexe", x.sexe);
                m.set(b + "rang", x.rang.name());
                m.set(b + "village", x.village);
                m.set(b + "faction", x.faction);
                if (!x.section.isEmpty()) {
                    m.set(b + "section", x.section);
                    m.set(b + "grade", x.grade);
                }
                m.set(b + "depuis", x.depuis);
                m.set(b + "vu", x.vu);
                if (x.pid >= 0) m.set(b + "pid", x.pid);
                if (x.mort) m.set(b + "mort", true);
            }
            enregistrer(m, fMembres);
        }
    }

    private void enregistrer(YamlConfiguration c, File f) {
        try {
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            c.save(tmp);
            java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            try { c.save(f); } catch (IOException e2) { plugin.getLogger().warning("[faction] Écriture " + f.getName() + " : " + e2.getMessage()); }
        }
    }
}
