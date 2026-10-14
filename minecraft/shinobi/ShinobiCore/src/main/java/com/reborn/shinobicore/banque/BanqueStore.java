package com.reborn.shinobicore.banque;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistance de la banque, dossier {@code plugins/ShinobiCore/banque/} :
 * <ul>
 *   <li>{@code comptes.yml} — un compte par personnage ({@link Compte}) ;</li>
 *   <li>{@code banque.yml} — trésors des villages, coffres des saisies, guichets, testaments, opérations numérotées
 *       (les 500 dernières, pour {@code /banque contrepasser}), semaine de solde déjà versée.</li>
 * </ul>
 * Écrit au plus toutes les 2 s après un changement, et à l'arrêt. Objets en base64 (comme les sacoches).
 */
public final class BanqueStore {

    public record Operation(long n, long le, String type, UUID de, UUID a, long montant, long frais, String acteur,
                            String motif, boolean annulee) {
        Operation annuler() { return new Operation(n, le, type, de, a, montant, frais, acteur, motif, true); }
    }

    public record Heritier(UUID perso, UUID owner, String nom, int part) {}

    public record Testament(UUID perso, String texte, List<Heritier> heritiers, String statut, long le, String par,
                            String validePar) {
        public boolean valide() { return "VALIDE".equals(statut); }
    }

    public record Guichet(String id, String type, String monde, int x, int y, int z, UUID entite, String village,
                          String libelle, String entreprise) {}

    private final ShinobiCore plugin;
    private final File dir, fComptes, fBanque;
    final Map<UUID, Compte> comptes = new ConcurrentHashMap<>();
    final Map<String, Long> tresors = new ConcurrentHashMap<>();
    final Map<String, List<ItemStack>> saisies = new ConcurrentHashMap<>();
    final Map<String, Guichet> guichets = new LinkedHashMap<>();
    final Map<UUID, Testament> testaments = new ConcurrentHashMap<>();
    final LinkedHashMap<Long, Operation> operations = new LinkedHashMap<>();
    long prochaineOp = 1;
    String semainePayee = "";
    private volatile boolean sale;

    BanqueStore(ShinobiCore plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "banque");
        this.fComptes = new File(dir, "comptes.yml");
        this.fBanque = new File(dir, "banque.yml");
    }

    void start() {
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("[banque] Dossier banque/ impossible à créer.");
        charger();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (sale) sauver(); }, 40L, 40L);
    }

    void marquer() { sale = true; }

    long nouvelleOp(String type, UUID de, UUID a, long montant, long frais, String acteur, String motif) {
        long n = prochaineOp++;
        operations.put(n, new Operation(n, System.currentTimeMillis(), type, de, a, montant, frais, acteur, motif, false));
        while (operations.size() > 500) operations.remove(operations.keySet().iterator().next());
        marquer();
        return n;
    }

    /* ================================================================ lecture */

    private static String enc(ItemStack s) {
        return s == null || s.getType().isAir() ? "" : Base64.getEncoder().encodeToString(s.serializeAsBytes());
    }

    private static ItemStack dec(String b) {
        if (b == null || b.isEmpty()) return null;
        try { return ItemStack.deserializeBytes(Base64.getDecoder().decode(b)); } catch (Exception e) { return null; }
    }

    private static UUID uuid(String s) {
        try { return s == null || s.isBlank() ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private void charger() {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(fComptes);
        ConfigurationSection cs = c.getConfigurationSection("comptes");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                UUID id = uuid(k);
                ConfigurationSection s = cs.getConfigurationSection(k);
                if (id == null || s == null) continue;
                Compte a = new Compte(id);
                a.owner = uuid(s.getString("owner"));
                a.nom = s.getString("nom", "");
                a.numero = s.getInt("numero", -1);
                a.village = s.getString("village", "");
                a.solde = s.getLong("solde", 0);
                a.palier = Math.max(1, s.getInt("palier", 1));
                List<String> co = s.getStringList("coffre");
                a.coffre = new ItemStack[co.size()];
                for (int i = 0; i < co.size(); i++) a.coffre[i] = dec(co.get(i));
                a.gelMotif = s.getString("gel.motif");
                a.gelPar = s.getString("gel.par");
                a.gelLe = s.getLong("gel.le", 0);
                a.mortLe = s.getLong("mort-le", 0);
                a.regle = s.getBoolean("regle", false);
                a.migre = s.getBoolean("migre", false);
                a.derniereBourse = s.getLong("bourse", 0);
                a.virementJour = s.getString("virements.jour", "");
                a.virementTotal = s.getLong("virements.total", 0);
                a.presenceSemaine = s.getString("presence.semaine", "");
                a.presenceMinutes = s.getLong("presence.minutes", 0);
                a.soldeSemaine = s.getString("solde-semaine.semaine", "");
                a.soldeMontant = s.getLong("solde-semaine.montant", 0);
                for (String l : s.getStringList("historique")) {
                    String[] p = l.split("\\|", 3);
                    if (p.length == 3) {
                        try { a.historique.addLast(new Compte.Ligne(Long.parseLong(p[0]), Long.parseLong(p[1]), p[2])); }
                        catch (NumberFormatException ignored) { }
                    }
                }
                comptes.put(id, a);
            }
        }
        YamlConfiguration b = YamlConfiguration.loadConfiguration(fBanque);
        ConfigurationSection t = b.getConfigurationSection("tresors");
        if (t != null) for (String v : t.getKeys(false)) tresors.put(v, t.getLong(v));
        ConfigurationSection sz = b.getConfigurationSection("saisies");
        if (sz != null) for (String v : sz.getKeys(false)) {
            List<ItemStack> l = new ArrayList<>();
            for (String e : sz.getStringList(v)) { ItemStack i = dec(e); if (i != null) l.add(i); }
            saisies.put(v, l);
        }
        for (Map<?, ?> g : b.getMapList("guichets")) {
            String id = str(g, "id");
            if (id.isEmpty()) continue;
            guichets.put(id, new Guichet(id, str(g, "type"), str(g, "monde"), (int) num(g, "x"), (int) num(g, "y"),
                    (int) num(g, "z"), uuid(str(g, "entite")), str(g, "village"), str(g, "libelle"), str(g, "entreprise")));
        }
        ConfigurationSection te = b.getConfigurationSection("testaments");
        if (te != null) for (String k : te.getKeys(false)) {
            UUID id = uuid(k);
            ConfigurationSection s = te.getConfigurationSection(k);
            if (id == null || s == null) continue;
            List<Heritier> hs = new ArrayList<>();
            for (String h : s.getStringList("heritiers")) {
                String[] p = h.split("\\|", 4);
                if (p.length == 4) {
                    try { hs.add(new Heritier(uuid(p[0]), uuid(p[1]), p[2], Integer.parseInt(p[3]))); }
                    catch (NumberFormatException ignored) { }
                }
            }
            testaments.put(id, new Testament(id, s.getString("texte", ""), hs, s.getString("statut", "DEPOSE"),
                    s.getLong("le", 0), s.getString("par", ""), s.getString("valide-par", "")));
        }
        ConfigurationSection ops = b.getConfigurationSection("operations");
        if (ops != null) for (String k : ops.getKeys(false)) {
            String[] p = ops.getString(k, "").split("\\|", 10);
            if (p.length < 10) continue;
            try {
                long n = Long.parseLong(k);
                operations.put(n, new Operation(n, Long.parseLong(p[0]), p[1], uuid(p[2]), uuid(p[3]), Long.parseLong(p[4]),
                        Long.parseLong(p[5]), p[6], p[7], Boolean.parseBoolean(p[8])));
            } catch (NumberFormatException ignored) { }
        }
        prochaineOp = Math.max(b.getLong("prochaine-operation", 1), operations.isEmpty() ? 1
                : operations.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1);
        semainePayee = b.getString("semaine-payee", "");
    }

    private static String str(Map<?, ?> m, String k) { Object o = m.get(k); return o == null ? "" : String.valueOf(o); }

    private static long num(Map<?, ?> m, String k) {
        Object o = m.get(k);
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (NumberFormatException e) { return 0; }
    }

    /* ================================================================ écriture */

    synchronized void sauver() {
        sale = false;
        YamlConfiguration c = new YamlConfiguration();
        for (Compte a : comptes.values()) {
            String k = "comptes." + a.perso;
            c.set(k + ".owner", a.owner == null ? null : a.owner.toString());
            c.set(k + ".nom", a.nom);
            c.set(k + ".numero", a.numero);
            c.set(k + ".village", a.village);
            c.set(k + ".solde", a.solde);
            c.set(k + ".palier", a.palier);
            List<String> co = new ArrayList<>();
            for (ItemStack s : a.coffre) co.add(enc(s));
            c.set(k + ".coffre", co);
            if (a.gelMotif != null) {
                c.set(k + ".gel.motif", a.gelMotif);
                c.set(k + ".gel.par", a.gelPar);
                c.set(k + ".gel.le", a.gelLe);
            }
            if (a.mortLe > 0) c.set(k + ".mort-le", a.mortLe);
            if (a.regle) c.set(k + ".regle", true);
            c.set(k + ".migre", a.migre);
            c.set(k + ".bourse", a.derniereBourse);
            if (!a.virementJour.isEmpty()) {
                c.set(k + ".virements.jour", a.virementJour);
                c.set(k + ".virements.total", a.virementTotal);
            }
            if (!a.presenceSemaine.isEmpty()) {
                c.set(k + ".presence.semaine", a.presenceSemaine);
                c.set(k + ".presence.minutes", a.presenceMinutes);
            }
            if (!a.soldeSemaine.isEmpty()) {
                c.set(k + ".solde-semaine.semaine", a.soldeSemaine);
                c.set(k + ".solde-semaine.montant", a.soldeMontant);
            }
            List<String> h = new ArrayList<>();
            for (Compte.Ligne l : a.historique) h.add(l.le() + "|" + l.delta() + "|" + l.texte());
            c.set(k + ".historique", h);
        }
        YamlConfiguration b = new YamlConfiguration();
        tresors.forEach((v, n) -> b.set("tresors." + v, n));
        saisies.forEach((v, l) -> {
            List<String> e = new ArrayList<>();
            for (ItemStack s : l) e.add(enc(s));
            b.set("saisies." + v, e);
        });
        List<Map<String, Object>> gs = new ArrayList<>();
        for (Guichet g : guichets.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.id());
            m.put("type", g.type());
            m.put("monde", g.monde());
            m.put("x", g.x());
            m.put("y", g.y());
            m.put("z", g.z());
            if (g.entite() != null) m.put("entite", g.entite().toString());
            m.put("village", g.village());
            m.put("libelle", g.libelle());
            if (g.entreprise() != null && !g.entreprise().isEmpty()) m.put("entreprise", g.entreprise());
            gs.add(m);
        }
        b.set("guichets", gs);
        for (Testament t : testaments.values()) {
            String k = "testaments." + t.perso();
            b.set(k + ".texte", t.texte());
            List<String> hs = new ArrayList<>();
            for (Heritier h : t.heritiers()) hs.add(h.perso() + "|" + h.owner() + "|" + h.nom().replace("|", "/") + "|" + h.part());
            b.set(k + ".heritiers", hs);
            b.set(k + ".statut", t.statut());
            b.set(k + ".le", t.le());
            b.set(k + ".par", t.par());
            b.set(k + ".valide-par", t.validePar());
        }
        for (Operation o : operations.values()) {
            b.set("operations." + o.n(), o.le() + "|" + o.type() + "|" + (o.de() == null ? "" : o.de()) + "|"
                    + (o.a() == null ? "" : o.a()) + "|" + o.montant() + "|" + o.frais() + "|" + o.acteur().replace("|", "/")
                    + "|" + (o.motif() == null ? "" : o.motif().replace("|", "/")) + "|" + o.annulee() + "|");
        }
        b.set("prochaine-operation", prochaineOp);
        b.set("semaine-payee", semainePayee);
        try {
            c.save(fComptes);
            b.save(fBanque);
        } catch (IOException e) {
            plugin.getLogger().warning("[banque] Sauvegarde échouée : " + e.getMessage());
            sale = true;
        }
    }
}
