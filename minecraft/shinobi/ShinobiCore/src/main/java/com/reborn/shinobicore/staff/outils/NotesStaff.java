package com.reborn.shinobicore.staff.outils;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Notes staff (« tags » RedLine) rattachées au <b>N° du personnage</b> ({@code notes-staff.yml}) : elles survivent à un
 * changement de nom RP ou de pseudo. Visibles de tout le staff ; suppression par l'auteur ou un Admin.
 */
public final class NotesStaff {

    public record Note(String id, long at, String auteur, String auteurUuid, String auteurPid, int auteurGrade,
                       String etiquette, String texte) { }

    public static final int MAX_TEXTE = 500, MAX_ETIQUETTE = 24;

    private final ShinobiCore plugin;
    private final File fichier;
    private final Map<Integer, List<Note>> notes = new ConcurrentHashMap<>();

    public NotesStaff(ShinobiCore plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "notes-staff.yml");
        charger();
    }

    public List<Note> de(int pid) {
        List<Note> l = notes.get(pid);
        if (l == null) return List.of();
        synchronized (l) {
            List<Note> out = new ArrayList<>(l);
            out.sort((a, b) -> Long.compare(b.at(), a.at()));
            return out;
        }
    }

    public int nombre(int pid) {
        List<Note> l = notes.get(pid);
        return l == null ? 0 : l.size();
    }

    public Set<String> etiquettes(int pid) {
        Set<String> out = new LinkedHashSet<>();
        for (Note n : de(pid)) if (!n.etiquette().isEmpty()) out.add(n.etiquette());
        return out;
    }

    public Note ajouter(CommandSender auteur, int pid, String etiquette, String texte) {
        String t = texte == null ? "" : texte.strip();
        if (t.isEmpty()) return null;
        if (t.length() > MAX_TEXTE) t = t.substring(0, MAX_TEXTE);
        String e = etiquette == null ? "" : etiquette.strip().toLowerCase(java.util.Locale.ROOT);
        if (e.length() > MAX_ETIQUETTE) e = e.substring(0, MAX_ETIQUETTE);
        long now = System.currentTimeMillis();
        Player p = auteur instanceof Player pl ? pl : null;
        Note n = new Note(Long.toString(now, 36), now, auteur.getName(), p != null ? p.getUniqueId().toString() : "",
                p != null ? com.reborn.shinobicore.character.PlayerIds.tag(p).replace("#", "") : "",
                p != null ? StaffGrades.of(p) : StaffGrades.OWNER, e, t);
        List<Note> l = notes.computeIfAbsent(pid, k -> new ArrayList<>());
        synchronized (l) { l.add(n); }
        sauver();
        Journal.entree(Categorie.STAFF, "NOTE_AJOUT").acteur(auteur).cibleTexte("N° " + PlayerIdRegistry.format(pid))
                .detail((e.isEmpty() ? "" : "[" + e + "] ") + t).ecrire();
        return n;
    }

    /** Peut-il supprimer cette note ? Auteur, ou Admin et plus (console comprise). */
    public static boolean peutSupprimer(CommandSender s, Note n) {
        if (!(s instanceof Player p)) return true;
        return StaffGrades.of(p) >= StaffGrades.ADMIN || p.getUniqueId().toString().equals(n.auteurUuid());
    }

    /** 1 supprimée, 0 introuvable, -1 refusé. */
    public int supprimer(CommandSender s, int pid, String id) {
        List<Note> l = notes.get(pid);
        if (l == null) return 0;
        Note cible = null;
        synchronized (l) {
            for (Note n : l) if (n.id().equals(id)) { cible = n; break; }
            if (cible == null) return 0;
            if (!peutSupprimer(s, cible)) return -1;
            l.remove(cible);
        }
        sauver();
        Journal.entree(Categorie.STAFF, "NOTE_SUPPRESSION").acteur(s).cibleTexte("N° " + PlayerIdRegistry.format(pid))
                .detail("note de " + cible.auteur() + " : " + cible.texte()).ecrire();
        return 1;
    }

    private void charger() {
        if (!fichier.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        ConfigurationSection root = y.getConfigurationSection("notes");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            int pid = PlayerIdRegistry.parse("#" + k);
            if (pid < 0) continue;
            List<Note> l = new ArrayList<>();
            for (Map<?, ?> m : root.getMapList(k)) {
                try {
                    l.add(new Note(String.valueOf(m.get("id")), ((Number) m.get("le")).longValue(),
                            String.valueOf(m.get("auteur")), str(m.get("auteur-uuid")), str(m.get("auteur-pid")),
                            m.get("auteur-grade") instanceof Number g ? g.intValue() : 0,
                            str(m.get("etiquette")), str(m.get("texte"))));
                } catch (RuntimeException ignored) { }
            }
            if (!l.isEmpty()) notes.put(pid, l);
        }
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private synchronized void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        notes.forEach((pid, l) -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            synchronized (l) {
                for (Note n : l) {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", n.id());
                    m.put("le", n.at());
                    m.put("auteur", n.auteur());
                    m.put("auteur-uuid", n.auteurUuid());
                    m.put("auteur-pid", n.auteurPid());
                    m.put("auteur-grade", n.auteurGrade());
                    m.put("etiquette", n.etiquette());
                    m.put("texte", n.texte());
                    rows.add(m);
                }
            }
            if (!rows.isEmpty()) y.set("notes." + PlayerIdRegistry.format(pid), rows);
        });
        try {
            plugin.getDataFolder().mkdirs();
            y.save(fichier);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Échec d'écriture de notes-staff.yml", e);
        }
    }
}
