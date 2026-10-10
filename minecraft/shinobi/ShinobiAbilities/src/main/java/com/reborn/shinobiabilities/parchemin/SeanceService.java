package com.reborn.shinobiabilities.parchemin;

import com.google.gson.JsonObject;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobiabilities.techniques.LearningMinigame;
import com.reborn.shinobiabilities.techniques.ParcheminItems;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.technique.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Apprentissage d'un parchemin en séances (docs/PROPOSITION_PARCHEMINS.md §3.3).
 *
 * <ul>
 *   <li>Clic droit rouleau en main : l'écran de lecture (technique, séances faites, délai).</li>
 *   <li>« S'entraîner » : une épreuve (mudras ou pompes). Une séance par technique toutes les
 *       {@code seance-heures}. Réussie, elle compte (double avec un maître à côté) ; ratée, elle est perdue.</li>
 *   <li>Il faut le rouleau en main du début à la fin. La progression est liée au personnage.</li>
 *   <li>Toutes les séances faites : la technique est apprise et le rouleau consommé. Rang S : le rouleau est remis au
 *       staff et une demande de validation s'ouvre ({@code /parchemin valider|refuser}).</li>
 *   <li>Très rarement, une séance fait perdre le rouleau (déchiré, emporté par le vent…).</li>
 * </ul>
 * Fichier {@code parchemins-progression.yml} : séances par personnage et par technique, date d'apprentissage
 * (ancienneté du maître), demandes de validation en attente.
 */
public final class SeanceService implements Listener {

    private record Progress(int done, long last) {}
    public record Pending(UUID charId, UUID playerId, String playerName, String techId, String scrollId) {}
    private record Training(String techId, String scrollId, boolean master) {}

    private static final String[] MISHAPS = {
        "Une bourrasque arrache le rouleau de tes mains et l'emporte au loin.",
        "Le vieux papier se déchire en deux sous tes doigts : le rouleau est perdu.",
        "Une goutte d'encre renversée a noyé les caractères : le rouleau est devenu illisible.",
        "Un rat surgi de nulle part s'enfuit avec le rouleau entre les dents.",
        "L'humidité a eu raison du papier : il s'effrite et part en poussière.",
    };

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final ScrollCatalog catalog;
    private final SlotRegistry slots;
    private final LearningMinigame minigame;
    private final LibraryService channel;
    private final File file;
    private final Random rnd = new Random();

    private final Map<UUID, Map<String, Progress>> progress = new HashMap<>();
    private final Map<UUID, Map<String, Long>> learnedAt = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private final Map<UUID, Training> training = new HashMap<>();

    private long cooldownMs = 20 * 3_600_000L;
    private double mishap = 0.002;
    private int masterMastery = 80;
    private long masterAgeMs = 14 * 86_400_000L;
    private double masterRange = 6.0;

    public SeanceService(JavaPlugin plugin, CoreServices core, ScrollCatalog catalog, SlotRegistry slots,
                         LearningMinigame minigame, LibraryService channel) {
        this.plugin = plugin;
        this.core = core;
        this.catalog = catalog;
        this.slots = slots;
        this.minigame = minigame;
        this.channel = channel;
        this.file = new File(plugin.getDataFolder(), "parchemins-progression.yml");
    }

    public void loadConfig(ConfigurationSection sec) {
        if (sec == null) return;
        cooldownMs = Math.round(Math.max(0.0, sec.getDouble("seance-heures", 20)) * 3_600_000L);
        mishap = Math.max(0, Math.min(1, sec.getDouble("perte-rare", 0.002)));
        masterMastery = sec.getInt("maitre.maitrise", 80);
        masterAgeMs = Math.round(Math.max(0.0, sec.getDouble("maitre.jours", 14)) * 86_400_000L);
        masterRange = sec.getDouble("maitre.distance", 6.0);
    }

    // ─────────────────────────────────────────────────────────────── persistance

    public void load() {
        progress.clear();
        learnedAt.clear();
        pending.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection pr = y.getConfigurationSection("progression");
        if (pr != null) for (String c : pr.getKeys(false)) {
            ConfigurationSection cs = pr.getConfigurationSection(c);
            if (cs == null) continue;
            Map<String, Progress> m = new HashMap<>();
            for (String t : cs.getKeys(false)) m.put(t, new Progress(cs.getInt(t + ".seances"), cs.getLong(t + ".derniere")));
            progress.put(UUID.fromString(c), m);
        }
        ConfigurationSection la = y.getConfigurationSection("appris");
        if (la != null) for (String c : la.getKeys(false)) {
            ConfigurationSection cs = la.getConfigurationSection(c);
            if (cs == null) continue;
            Map<String, Long> m = new HashMap<>();
            for (String t : cs.getKeys(false)) m.put(t, cs.getLong(t));
            learnedAt.put(UUID.fromString(c), m);
        }
        for (Map<?, ?> raw : y.getMapList("en-attente")) {
            try {
                pending.add(new Pending(UUID.fromString(String.valueOf(raw.get("perso"))),
                        UUID.fromString(String.valueOf(raw.get("joueur"))), String.valueOf(raw.get("nom")),
                        String.valueOf(raw.get("technique")), String.valueOf(raw.get("rouleau"))));
            } catch (RuntimeException ignored) {
                // entrée abîmée : ignorée
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        progress.forEach((c, m) -> m.forEach((t, p) -> {
            y.set("progression." + c + "." + t + ".seances", p.done());
            y.set("progression." + c + "." + t + ".derniere", p.last());
        }));
        learnedAt.forEach((c, m) -> m.forEach((t, at) -> y.set("appris." + c + "." + t, at)));
        List<Map<String, Object>> pl = new ArrayList<>();
        for (Pending p : pending) {
            Map<String, Object> m = new HashMap<>();
            m.put("perso", p.charId().toString());
            m.put("joueur", p.playerId().toString());
            m.put("nom", p.playerName());
            m.put("technique", p.techId());
            m.put("rouleau", p.scrollId());
            pl.add(m);
        }
        y.set("en-attente", pl);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[parchemins] Sauvegarde de la progression échouée : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────── lecture

    @EventHandler(priority = EventPriority.NORMAL)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !ParcheminItems.isParchemin(it)) return;
        // une bibliothèque cliquée rouleau en main s'ouvre normalement
        if (e.getClickedBlock() != null && channel.at(e.getClickedBlock().getLocation()) != null) return;
        e.setCancelled(true);
        read(e.getPlayer());
    }

    /** Envoie l'écran de lecture du rouleau tenu en main. */
    public void read(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        Ability a = catalog.byId(ParcheminItems.abilityIdOf(it));
        if (a == null) { p.sendMessage(Component.text("Ce parchemin est illisible…", NamedTextColor.RED)); return; }
        ShinobiCharacter c = core.characters().getActive(p.getUniqueId());
        if (c == null) return;
        channel.send(p, readJson(p, c, a));
    }

    private JsonObject readJson(Player p, ShinobiCharacter c, Ability a) {
        Progress pr = progress.getOrDefault(c.id(), Map.of()).getOrDefault(a.id(), new Progress(0, 0));
        long wait = pr.last() + cooldownMs - System.currentTimeMillis();
        JsonObject o = new JsonObject();
        o.addProperty("t", "read");
        o.add("tech", ScrollCatalog.json(a));
        o.addProperty("done", pr.done());
        o.addProperty("nextIn", pr.last() == 0 || wait <= 0 ? "maintenant" : "dans " + duration(wait));
        o.addProperty("master", masterNear(p, a.id()) != null);
        o.addProperty("known", c.knowsAbility(a.id()));
        o.addProperty("pending", isPending(c.id(), a.id()));
        return o;
    }

    private static String duration(long ms) {
        long min = (ms + 59_999) / 60_000;
        if (min < 60) return min + " min";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " h" : h + " h " + String.format("%02d", m);
    }

    private boolean isPending(UUID charId, String techId) {
        for (Pending p : pending) if (p.charId().equals(charId) && p.techId().equals(techId)) return true;
        return false;
    }

    /** Un maître de la technique à portée : Maître de la technique, et la connaissant depuis assez longtemps. */
    private Player masterNear(Player p, String techId) {
        for (Player o : p.getWorld().getPlayers()) {
            if (o.equals(p) || o.getLocation().distanceSquared(p.getLocation()) > masterRange * masterRange) continue;
            ShinobiCharacter oc = core.characters().getActive(o.getUniqueId());
            if (oc == null || !oc.knowsAbility(techId) || oc.abilityMastery(techId) < masterMastery) continue;
            Long at = learnedAt.getOrDefault(oc.id(), Map.of()).get(techId);
            if (at != null && System.currentTimeMillis() - at < masterAgeMs) continue;   // trop récent
            return o;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────── séance

    /** Demande du client (« S'entraîner » / « Terminer »). */
    public void train(Player p, String techId) {
        ItemStack it = p.getInventory().getItemInMainHand();
        String held = ParcheminItems.abilityIdOf(it);
        if (held == null || !held.equals(techId)) { channel.toast(p, "Garde le rouleau en main pour t'entraîner."); return; }
        Ability a = catalog.byId(techId);
        ShinobiCharacter c = core.characters().getActive(p.getUniqueId());
        if (a == null || c == null) return;
        if (c.knowsAbility(a.id())) { channel.toast(p, "Tu connais déjà cette technique."); return; }
        if (isPending(c.id(), a.id())) { channel.toast(p, "Ta demande de validation est entre les mains du staff."); return; }
        if (core.ko() != null && core.ko().isKo(p.getUniqueId())) return;
        if (minigame.isActive(p)) { channel.toast(p, "Termine d'abord ton entraînement en cours."); return; }
        Progress pr = progress.getOrDefault(c.id(), Map.of()).getOrDefault(a.id(), new Progress(0, 0));
        int total = ScrollCatalog.seances(ScrollCatalog.rank(a));
        if (pr.done() >= total) { complete(p, c, a, scrollIdOrNew(it)); return; }
        if (pr.last() != 0 && System.currentTimeMillis() - pr.last() < cooldownMs) {
            channel.toast(p, "Ton corps a besoin de repos : prochaine séance plus tard.");
            return;
        }
        Player master = masterNear(p, a.id());
        training.put(p.getUniqueId(), new Training(a.id(), scrollIdOrNew(it), master != null));
        if (master != null) {
            master.sendMessage(Component.text(c.name() + " s'entraîne à « " + a.name() + " » sous ton regard.", NamedTextColor.GOLD));
        }
        minigame.startSeance(p, a, !ScrollCatalog.branch(a).equals("Ninjutsu"), master != null);
    }

    private static String scrollIdOrNew(ItemStack it) {
        String id = ParcheminItems.scrollIdOf(it);
        return id != null ? id : UUID.randomUUID().toString();
    }

    /** Fin de l'épreuve (branché sur le mini-jeu). */
    public void onSeanceEnd(Player p, LearningMinigame.Result r) {
        Training tr = training.remove(p.getUniqueId());
        if (tr == null) return;
        ShinobiCharacter c = core.characters().getActive(p.getUniqueId());
        Ability a = r.ability();
        if (c == null || a == null) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!a.id().equals(ParcheminItems.abilityIdOf(it))) {
            p.sendMessage(Component.text("Tu as lâché le rouleau : la séance ne compte pas.", NamedTextColor.RED));
            return;
        }
        Map<String, Progress> m = progress.computeIfAbsent(c.id(), k -> new HashMap<>());
        Progress pr = m.getOrDefault(a.id(), new Progress(0, 0));
        int total = ScrollCatalog.seances(ScrollCatalog.rank(a));
        long now = System.currentTimeMillis();
        int gain = r.success() ? (tr.master() ? 2 : 1) : 0;
        int done = Math.min(total, pr.done() + gain);
        m.put(a.id(), new Progress(done, now));
        save();
        if (r.success()) {
            p.sendMessage(Component.text("Séance réussie" + (gain > 1 ? " (double, grâce à ton maître)" : "")
                    + " : " + done + " / " + total + ".", NamedTextColor.GREEN));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        } else {
            p.sendMessage(Component.text("Séance ratée : elle ne compte pas. Réessaie plus tard.", NamedTextColor.RED));
        }
        if (rnd.nextDouble() < mishap) {                                   // easter egg : le rouleau est perdu
            String msg = MISHAPS[rnd.nextInt(MISHAPS.length)];
            p.getInventory().setItemInMainHand(null);
            if (ScrollCatalog.rank(a) == 'S') slots.release(a.id(), "rouleau:" + tr.scrollId());
            p.sendMessage(Component.text(msg, NamedTextColor.GRAY, TextDecoration.ITALIC));
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.6f);
            log(p, "Parchemin perdu (aléa)", a.name());
            return;
        }
        if (done >= total) complete(p, c, a, tr.scrollId());
        else channel.send(p, readJson(p, c, a));
    }

    /** Toutes les séances faites : apprise, ou demande de validation staff pour le rang S. */
    private void complete(Player p, ShinobiCharacter c, Ability a, String scrollId) {
        p.getInventory().setItemInMainHand(null);
        if (ScrollCatalog.rank(a) == 'S') {
            pending.add(new Pending(c.id(), p.getUniqueId(), p.getName(), a.id(), scrollId));
            save();
            p.sendMessage(Component.text("Toutes les séances sont faites. Le rouleau est confié au staff, qui validera"
                    + " ton apprentissage de « " + a.name() + " ».", NamedTextColor.GOLD));
            for (Player s : Bukkit.getOnlinePlayers()) {
                if (s.hasPermission("shinobiabilities.staff")) {
                    s.sendMessage(Component.text("[Parchemins] " + c.name() + " (" + p.getName() + ") attend la validation de « "
                            + a.name() + " » : /parchemin valider " + p.getName() + " " + a.id(), NamedTextColor.GOLD));
                }
            }
            log(p, "Rang S : demande de validation", a.name());
            channel.send(p, readJson(p, c, a));
            return;
        }
        learn(p, c, a, scrollId);
    }

    private void learn(Player p, ShinobiCharacter c, Ability a, String scrollId) {
        c.learnAbility(a.id());
        core.characters().save(c);
        Map<String, Progress> m = progress.get(c.id());
        if (m != null) m.remove(a.id());
        learnedAt.computeIfAbsent(c.id(), k -> new HashMap<>()).put(a.id(), System.currentTimeMillis());
        if (ScrollCatalog.rank(a) == 'S') slots.transfer(a.id(), scrollId, c.id().toString());
        save();
        p.showTitle(Title.title(
                Component.text("Technique apprise !", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(a.name(), NamedTextColor.AQUA),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        log(p, "Technique apprise (parchemin)", a.name() + " · rang " + ScrollCatalog.rank(a));
    }

    // ─────────────────────────────────────────────────────────────── staff

    /** {@code /parchemin seance} : ajoute (ou retire) des séances au personnage actif d'un joueur. */
    public int grant(Player target, Ability a, int n) {
        ShinobiCharacter c = core.characters().getActive(target.getUniqueId());
        if (c == null) return -1;
        Map<String, Progress> m = progress.computeIfAbsent(c.id(), k -> new HashMap<>());
        Progress pr = m.getOrDefault(a.id(), new Progress(0, 0));
        int total = ScrollCatalog.seances(ScrollCatalog.rank(a));
        int done = Math.max(0, Math.min(total, pr.done() + n));
        m.put(a.id(), new Progress(done, 0L));                             // délai remis à zéro
        save();
        return done;
    }

    public List<Pending> pending() { return pending; }

    public Pending findPending(String playerName, String techId) {
        for (Pending p : pending) {
            if (p.playerName().equalsIgnoreCase(playerName) && p.techId().equals(techId)) return p;
        }
        return null;
    }

    /** Valide une demande : la technique est apprise (joueur connecté avec ce personnage actif). */
    public String validate(Player staff, Pending pd) {
        Player p = Bukkit.getPlayer(pd.playerId());
        ShinobiCharacter c = p == null ? null : core.characters().getActive(p.getUniqueId());
        if (c == null || !c.id().equals(pd.charId())) return "Le joueur doit être connecté avec ce personnage.";
        Ability a = catalog.byId(pd.techId());
        if (a == null) return "Technique inconnue.";
        pending.remove(pd);
        learn(p, c, a, pd.scrollId());
        log(staff, "Rang S validé", c.name() + " · " + a.name());
        return null;
    }

    /** Refuse une demande : le rouleau revient au joueur, la dernière séance est à refaire. */
    public String refuse(Player staff, Pending pd) {
        Player p = Bukkit.getPlayer(pd.playerId());
        if (p == null) return "Le joueur doit être connecté (son rouleau lui est rendu).";
        Ability a = catalog.byId(pd.techId());
        if (a == null) return "Technique inconnue.";
        pending.remove(pd);
        grant(p, a, -1);
        ItemStack it = ParcheminItems.create(a, pd.scrollId());
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        p.sendMessage(Component.text("Le staff n'a pas validé « " + a.name() + " » : ton rouleau t'est rendu, la dernière"
                + " séance est à refaire.", NamedTextColor.RED));
        save();
        log(staff, "Rang S refusé", pd.playerName() + " · " + a.name());
        return null;
    }

    private void log(Player p, String type, String detail) {
        if (Bukkit.getPluginManager().getPlugin("ShinobiCore") instanceof com.reborn.shinobicore.ShinobiCore sc
                && sc.staffPanel() != null) {
            sc.staffPanel().log(p, "parchemins", type, detail);
        }
    }
}
