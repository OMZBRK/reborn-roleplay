package com.reborn.shinobicore.modificateur;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.ModificateurService;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.journal.Journal;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Registre des <b>modificateurs</b> (implémentation de {@link ModificateurService}, docs/technique/ETATS_JOUEUR.md §4.4,
 * §7.3) : effets datés par <b>personnage</b>, types en configuration, empilement, persistance (GELE / CONTINUE /
 * PERDU), sort au KO (les bonus partent, les malus restent), visibilité, journal, contributeurs dérivés.
 *
 * <p><b>Synchro client</b> (minimale, en attendant {@code reborn:etat}) : canal {@code reborn:modifs}, S2C, JSON UTF-8
 * brut, envoyé à chaque changement et toutes les {@code modificateurs.sync-secondes} :
 * {@code {"mods":[{"id":"regeneration","nom":"Régénération","ic":"coeur","b":1,"val":"×2","reste":287,"duree":300,
 * "pause":0}]}} — seulement les types {@code visible: SOI}. Le mod décompte seul entre deux paquets.
 *
 * <p>Persistance : {@code plugins/ShinobiCore/modificateurs.yml} (une liste par personnage).
 */
public final class ModificateurRegistry implements ModificateurService, Listener {

    public static final String CHANNEL = "reborn:modifs";

    /** Un état dérivé (FAIM de la satiété, repas en cours…) : multiplicateurs et refus, jamais stocké. */
    public interface Contributeur {
        default double multiplicateur(UUID compte, String effet) { return 1.0; }

        default String raison(UUID compte, String capacite) { return null; }
    }

    /** Instance posée. {@code resteMs} pour GELE ; {@code finEpoch} pour CONTINUE. */
    static final class Modif {
        final String type;
        double intensite;
        long dureeMs, resteMs, finEpoch;
        String source;

        Modif(String type, double intensite, long dureeMs, String source) {
            this.type = type;
            this.intensite = intensite;
            this.dureeMs = dureeMs;
            this.resteMs = dureeMs;
            this.source = source;
        }

        long reste(TypeModificateur t, long now) {
            return t.persistance() == TypeModificateur.Persistance.CONTINUE ? Math.max(0, finEpoch - now) : resteMs;
        }
    }

    private final ShinobiCore plugin;
    private final File fichier;
    private final Map<String, TypeModificateur> types = new LinkedHashMap<>();
    /** personnage → (type → modificateur). */
    private final Map<UUID, Map<String, Modif>> parPerso = new ConcurrentHashMap<>();
    private final List<Contributeur> contributeurs = new ArrayList<>();
    private final Set<UUID> koVus = new HashSet<>();
    private final Set<UUID> sales = new HashSet<>();
    private final Map<UUID, Long> dernierEnvoi = new HashMap<>();
    /** « Ce modificateur est-il en pause ? » (régénération figée après un coup) : posé par la satiété. */
    private BiPredicate<UUID, TypeModificateur> enPause = (c, t) -> false;
    private BukkitTask tache;
    private long syncMs = 10_000L;
    private boolean disque;
    private int ticks;

    public ModificateurRegistry(ShinobiCore plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "modificateurs.yml");
    }

    /* ================================================================ cycle */

    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        reload();
        charger();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (tache != null) { tache.cancel(); tache = null; }
        sauver();
    }

    public void reload() {
        types.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("modificateurs.types");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection t = s.getConfigurationSection(id);
                if (t != null) types.put(id.toLowerCase(Locale.ROOT), TypeModificateur.lire(id.toLowerCase(Locale.ROOT), t));
            }
        }
        syncMs = Math.max(2, plugin.getConfig().getInt("modificateurs.sync-secondes", 10)) * 1000L;
    }

    public void ajouterContributeur(Contributeur c) { contributeurs.add(c); }

    public void pauseSi(BiPredicate<UUID, TypeModificateur> p) { enPause = p == null ? (c, t) -> false : p; }

    public TypeModificateur type(String id) { return id == null ? null : types.get(id.toLowerCase(Locale.ROOT)); }

    public java.util.Collection<TypeModificateur> types() { return types.values(); }

    /* ================================================================ lecture (API) */

    private UUID perso(UUID compte) {
        ShinobiCharacter c = plugin.characters() == null ? null : plugin.characters().getActive(compte);
        return c == null ? null : c.id();
    }

    @Override
    public double multiplicateur(UUID compte, String effet) {
        double m = 1.0;
        UUID pid = compte == null ? null : perso(compte);
        if (pid != null) {
            Map<String, Modif> l = parPerso.get(pid);
            if (l != null) {
                for (Modif x : l.values()) {
                    TypeModificateur t = types.get(x.type);
                    if (t != null && t.effets().containsKey(effet)) m *= t.effet(effet, x.intensite);
                }
            }
        }
        for (Contributeur c : contributeurs) m *= c.multiplicateur(compte, effet);
        return m;
    }

    @Override
    public String raison(UUID compte, String capacite) {
        if (compte == null || capacite == null) return null;
        String cap = capacite.toUpperCase(Locale.ROOT);
        UUID pid = perso(compte);
        if (pid != null) {
            Map<String, Modif> l = parPerso.get(pid);
            if (l != null) {
                for (Modif x : l.values()) {
                    TypeModificateur t = types.get(x.type);
                    if (t != null && t.refuse().contains(cap)) return t.nom();
                }
            }
        }
        for (Contributeur c : contributeurs) {
            String r = c.raison(compte, cap);
            if (r != null) return r;
        }
        return null;
    }

    @Override
    public List<Vue> actifs(UUID compte) {
        List<Vue> out = new ArrayList<>();
        UUID pid = compte == null ? null : perso(compte);
        Map<String, Modif> l = pid == null ? null : parPerso.get(pid);
        if (l == null) return out;
        long now = System.currentTimeMillis();
        for (Modif x : l.values()) {
            TypeModificateur t = types.get(x.type);
            if (t == null) continue;
            out.add(new Vue(t.id(), t.nom(), t.famille(), t.bonus(), x.intensite, x.reste(t, now), x.dureeMs, x.source));
        }
        return out;
    }

    /** Ce type est-il actif sur le personnage actif ? */
    public boolean actif(UUID compte, String type) {
        UUID pid = perso(compte);
        Map<String, Modif> l = pid == null ? null : parPerso.get(pid);
        return l != null && type != null && l.containsKey(type.toLowerCase(Locale.ROOT));
    }

    /** Temps restant d'un type actif (ms), 0 sinon. */
    public long reste(UUID compte, String type) {
        UUID pid = perso(compte);
        Map<String, Modif> l = pid == null ? null : parPerso.get(pid);
        Modif x = l == null || type == null ? null : l.get(type.toLowerCase(Locale.ROOT));
        TypeModificateur t = x == null ? null : types.get(x.type);
        return t == null ? 0 : x.reste(t, System.currentTimeMillis());
    }

    /* ================================================================ écriture (API) */

    @Override
    public boolean poser(Player p, String typeId, double intensite, long dureeMs, String source) {
        if (p == null) return false;
        UUID pid = perso(p.getUniqueId());
        TypeModificateur t = type(typeId);
        if (pid == null || t == null) return false;
        long d = dureeMs > 0 ? dureeMs : t.dureeMs();
        Map<String, Modif> l = parPerso.computeIfAbsent(pid, k -> new ConcurrentHashMap<>());
        Modif ex = l.get(t.id());
        long now = System.currentTimeMillis();
        if (ex != null) {
            switch (t.empilement()) {
                case IGNORE -> { return false; }
                case PROLONGE -> {
                    ex.dureeMs += d;
                    ex.resteMs += d;
                    ex.finEpoch += d;
                    ex.intensite = Math.max(ex.intensite, intensite);
                }
                case CUMULE -> {
                    double n = ex.intensite + intensite;
                    ex.intensite = t.plafond() > 0 ? Math.min(t.plafond(), n) : n;
                    ex.dureeMs = d;
                    ex.resteMs = d;
                    ex.finEpoch = now + d;
                }
                default -> {   // le plus fort remplace ; à force égale, la durée repart
                    if (intensite + 1e-9 < ex.intensite) return false;
                    ex.intensite = intensite;
                    ex.dureeMs = d;
                    ex.resteMs = d;
                    ex.finEpoch = now + d;
                }
            }
            ex.source = source;
        } else {
            Modif m = new Modif(t.id(), intensite, d, source);
            m.finEpoch = now + d;
            l.put(t.id(), m);
        }
        if (t.journal()) {
            Journal.entree(Categorie.JOUEUR, "MODIF_POSE").cible(p)
                    .detail(t.nom() + " " + t.valeurAffichee(intensite) + " · " + d / 1000 + " s · " + source).ecrire();
        }
        disque = true;
        envoyer(p);
        return true;
    }

    @Override
    public void retirer(Player p, String typeId, String source) {
        if (p == null || typeId == null) return;
        UUID pid = perso(p.getUniqueId());
        Map<String, Modif> l = pid == null ? null : parPerso.get(pid);
        if (l == null) return;
        Modif m = l.remove(typeId.toLowerCase(Locale.ROOT));
        if (m == null) return;
        TypeModificateur t = types.get(m.type);
        if (t != null && t.journal()) {
            Journal.entree(Categorie.JOUEUR, "MODIF_RETRAIT").cible(p).detail(t.nom() + " · " + source).ecrire();
        }
        disque = true;
        envoyer(p);
    }

    /** Retire tous les bonus « perdus au KO » (appelé à l'entrée en KO). */
    public void auKo(Player p) {
        UUID pid = perso(p.getUniqueId());
        Map<String, Modif> l = pid == null ? null : parPerso.get(pid);
        if (l == null) return;
        boolean ch = l.values().removeIf(m -> {
            TypeModificateur t = types.get(m.type);
            return t == null || t.perduAuKo();
        });
        if (ch) { disque = true; envoyer(p); }
    }

    /* ================================================================ tick */

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            UUID pid = perso(id);
            boolean ko = plugin.ko() != null && plugin.ko().isKo(id);
            if (ko && koVus.add(id)) auKo(p);
            else if (!ko) koVus.remove(id);
            if (pid == null) continue;
            Map<String, Modif> l = parPerso.get(pid);
            if (l == null || l.isEmpty()) continue;
            boolean change = false;
            List<String> finis = new ArrayList<>();
            for (Modif m : l.values()) {
                TypeModificateur t = types.get(m.type);
                if (t == null) { finis.add(m.type); continue; }
                if (t.persistance() != TypeModificateur.Persistance.CONTINUE) m.resteMs -= 1000L;
                if (m.reste(t, now) <= 0) finis.add(m.type);
            }
            for (String f : finis) {
                Modif m = l.remove(f);
                change = true;
                TypeModificateur t = types.get(f);
                if (t != null && t.journal()) {
                    Journal.entree(Categorie.JOUEUR, "MODIF_FIN").cible(p).detail(t.nom()).ecrire();
                }
                if (t != null && t.ensuite() != null && m != null && type(t.ensuite()) != null) {
                    poser(p, t.ensuite(), 1.0, 0, "contrecoup · " + t.nom());
                }
            }
            if (change) disque = true;
            if (change || now - dernierEnvoi.getOrDefault(id, 0L) >= syncMs) envoyer(p);
        }
        if (disque && ++ticks % 30 == 0) sauver();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> envoyer(e.getPlayer()), 40L);
    }

    /** Pousse la liste visible au joueur (canal {@code reborn:modifs}). */
    public void envoyer(Player p) {
        if (p == null || !p.isOnline()) return;
        dernierEnvoi.put(p.getUniqueId(), System.currentTimeMillis());
        if (!p.getListeningPluginChannels().contains(CHANNEL)) return;
        UUID pid = perso(p.getUniqueId());
        Map<String, Modif> l = pid == null ? null : parPerso.get(pid);
        StringBuilder b = new StringBuilder(160).append("{\"mods\":[");
        boolean premier = true;
        long now = System.currentTimeMillis();
        if (l != null) {
            for (Modif m : l.values()) {
                TypeModificateur t = types.get(m.type);
                if (t == null || t.visible() != TypeModificateur.Visibilite.SOI) continue;
                if (!premier) b.append(',');
                premier = false;
                b.append("{\"id\":\"").append(esc(t.id())).append("\",\"nom\":\"").append(esc(t.nom()))
                        .append("\",\"ic\":\"").append(esc(t.icone())).append("\",\"b\":").append(t.bonus() ? 1 : 0)
                        .append(",\"val\":\"").append(esc(t.valeurAffichee(m.intensite))).append('"')
                        .append(",\"reste\":").append(Math.max(0, m.reste(t, now) / 1000))
                        .append(",\"duree\":").append(Math.max(1, m.dureeMs / 1000))
                        .append(",\"pause\":").append(enPause.test(p.getUniqueId(), t) ? 1 : 0).append('}');
            }
        }
        b.append("]}");
        try {
            p.sendPluginMessage(plugin, CHANNEL, b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // client sans le mod
        }
    }

    /* ================================================================ disque */

    private void charger() {
        parPerso.clear();
        if (!fichier.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        long now = System.currentTimeMillis();
        for (String k : y.getKeys(false)) {
            UUID pid;
            try { pid = UUID.fromString(k); } catch (IllegalArgumentException ex) { continue; }
            Map<String, Modif> l = new ConcurrentHashMap<>();
            for (Map<?, ?> e : y.getMapList(k)) {
                String type = String.valueOf(e.get("type"));
                TypeModificateur t = types.get(type);
                if (t == null) continue;
                Modif m = new Modif(type, num(e.get("n"), 1), (long) num(e.get("duree"), 0), String.valueOf(e.get("source")));
                m.resteMs = (long) num(e.get("reste"), 0);
                m.finEpoch = (long) num(e.get("fin"), 0);
                if (m.reste(t, now) > 0) l.put(type, m);
            }
            if (!l.isEmpty()) parPerso.put(pid, l);
        }
    }

    public void sauver() {
        disque = false;
        YamlConfiguration y = new YamlConfiguration();
        for (var e : parPerso.entrySet()) {
            List<Map<String, Object>> l = new ArrayList<>();
            for (Modif m : e.getValue().values()) {
                TypeModificateur t = types.get(m.type);
                if (t == null || t.persistance() == TypeModificateur.Persistance.PERDU) continue;
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("type", m.type);
                o.put("n", m.intensite);
                o.put("duree", m.dureeMs);
                o.put("reste", m.resteMs);
                o.put("fin", m.finEpoch);
                o.put("source", m.source == null ? "" : m.source);
                l.add(o);
            }
            if (!l.isEmpty()) y.set(e.getKey().toString(), l);
        }
        try {
            y.save(fichier);
        } catch (IOException ex) {
            plugin.getLogger().warning("[modificateurs] sauvegarde impossible : " + ex.getMessage());
        }
    }

    /** Oublie les modificateurs PERDU d'un personnage (déconnexion, changement de personnage). */
    public void quitter(UUID persoId) {
        if (persoId == null) return;
        Map<String, Modif> l = parPerso.get(persoId);
        if (l == null) return;
        l.values().removeIf(m -> {
            TypeModificateur t = types.get(m.type);
            return t == null || t.persistance() == TypeModificateur.Persistance.PERDU;
        });
        disque = true;
    }

    private static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try { return o == null ? def : Double.parseDouble(String.valueOf(o)); } catch (NumberFormatException ex) { return def; }
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
    }
}
