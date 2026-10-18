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
 * Apprentissage d'un parchemin en séances (docs/gameplay/APPRENTISSAGE.md §4).
 *
 * <ul>
 *   <li>Clic droit rouleau en main : l'écran de lecture (technique, séances faites, délai).</li>
 *   <li>« S'entraîner » : une épreuve (mudras ou pompes). Une séance par technique toutes les
 *       {@code seance-heures}. Réussie, elle compte (double avec un maître à côté) ; ratée, elle est perdue.</li>
 *   <li>Il faut le rouleau en main du début à la fin. La progression est liée au personnage.</li>
 *   <li>Lecture sur place (bibliothèques v2) : devant une bibliothèque Clan, Académie ou Palais dont le fonds contient
 *       le rouleau, un membre peut s'entraîner sans l'emporter, si un maître de la technique est présent. Le rouleau
 *       reste sur l'étagère (il n'est ni consommé ni perdu) ; le rang S ne s'apprend pas sur place.</li>
 *   <li>Toutes les séances faites : la technique est apprise et le rouleau consommé. Rang S : le rouleau est remis au
 *       staff et une demande de validation s'ouvre ({@code /parchemin valider|refuser}).</li>
 *   <li>Très rarement, une séance fait perdre le rouleau (déchiré, emporté par le vent…).</li>
 * </ul>
 * Fichier {@code parchemins-progression.yml} : séances par personnage et par technique, date d'apprentissage
 * (ancienneté du maître), demandes de validation en attente.
 */
public final class SeanceService implements Listener {

    /** Avancement d'un apprentissage : séances faites, heure de la dernière (ms epoch, 0 = jamais). */
    public record Progress(int done, long last) {}
    public record Pending(UUID charId, UUID playerId, String playerName, String techId, String scrollId) {}
    /** Séance en cours ; {@code libId} non nul = lecture sur place devant cette bibliothèque. */
    private record Training(String techId, String scrollId, boolean master, String libId) {}

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
        return readJson(p, c, a, null);
    }

    private JsonObject readJson(Player p, ShinobiCharacter c, Ability a, Library onsite) {
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
        if (onsite != null) {
            JsonObject l = new JsonObject();
            l.addProperty("id", onsite.id);
            l.addProperty("name", onsite.name);
            o.add("onsite", l);
        }
        return o;
    }

    /** Lecture sur place d'un rouleau du fonds (écran de lecture, « S'entraîner » sans emporter le rouleau). */
    public void readOnsite(Player p, Library l, String techId) {
        Ability a = catalog.byId(techId);
        ShinobiCharacter c = core.characters().getActive(p.getUniqueId());
        if (a == null || c == null) return;
        String err = onsiteError(p, l, a);
        if (err != null && !err.equals(NO_MASTER)) { channel.toast(p, err); return; }
        channel.send(p, readJson(p, c, a, l));
    }

    private static final String NO_MASTER = "La lecture sur place demande un maître de cette technique à tes côtés.";

    /** Ce qui empêche une séance sur place, ou null. */
    private String onsiteError(Player p, Library l, Ability a) {
        if (l == null || !l.type.onsiteReading()) return "Cette bibliothèque ne permet pas la lecture sur place.";
        if (l.distanceSq(p.getLocation()) > LibraryService.REACH_SQ) return "Reste devant la bibliothèque pour lire sur place.";
        if (!channel.isMember(p, l)) return "La lecture sur place est réservée aux membres.";
        if (channel.fondsCellOf(l, a.id()) < 0) return "Ce rouleau n'est plus sur l'étagère.";
        if (ScrollCatalog.rank(a) == 'S') return "Un rouleau de rang S ne s'étudie pas sur place : il faut l'emporter.";
        if (masterNear(p, a.id()) == null) return NO_MASTER;
        return null;
    }

    private static String duration(long ms) {
        long min = (ms + 59_999) / 60_000;
        if (min < 60) return min + " min";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " h" : h + " h " + String.format("%02d", m);
    }

    public boolean isPending(UUID charId, String techId) {
        for (Pending p : pending) if (p.charId().equals(charId) && p.techId().equals(techId)) return true;
        return false;
    }

    /** Un maître de la technique à portée : Maître de la technique, et la connaissant depuis assez longtemps. */
    public Player masterNear(Player p, String techId) {
        for (Player o : p.getWorld().getPlayers()) {
            if (o.equals(p) || o.getLocation().distanceSquared(p.getLocation()) > masterRange * masterRange) continue;
            ShinobiCharacter oc = core.characters().getActive(o.getUniqueId());
            if (isMaster(oc, techId)) return o;
        }
        return null;
    }

    /**
     * Ce personnage est-il maître de la technique (peut l'enseigner) : il la connaît,
     * maîtrise suffisante, et apprise depuis assez longtemps quand la date est connue
     * (date du parchemin, sinon date notée sur le personnage).
     */
    public boolean isMaster(ShinobiCharacter oc, String techId) {
        if (oc == null || techId == null || !oc.knowsAbility(techId) || oc.abilityMastery(techId) < masterMastery) return false;
        Long at = learnedAt(oc.id(), techId);
        if (at == null) at = oc.learnedAt(techId);
        return at == null || System.currentTimeMillis() - at >= masterAgeMs;      // trop récent sinon
    }

    // ─────────────────────────────────────────────────────────── lecture (pont)

    /** Copie de l'avancement des apprentissages en cours d'un personnage (technique → avancement). */
    public Map<String, Progress> progressOf(UUID charId) {
        Map<String, Progress> m = progress.get(charId);
        return m == null ? Map.of() : Map.copyOf(m);
    }

    /** Date d'apprentissage sur parchemin (ms epoch), ou null. */
    public Long learnedAt(UUID charId, String techId) {
        Map<String, Long> m = learnedAt.get(charId);
        return m == null ? null : m.get(techId);
    }

    /** Délai minimal entre deux séances (ms). */
    public long cooldownMs() { return cooldownMs; }

    /** Demandes de validation en attente d'un personnage (ids de technique). */
    public List<String> pendingOf(UUID charId) {
        List<String> out = new ArrayList<>();
        for (Pending p : pending) if (p.charId().equals(charId)) out.add(p.techId());
        return out;
    }

    // ─────────────────────────────────────────────────────────────── séance

    /** Demande du client (« S'entraîner » / « Terminer »), rouleau en main. */
    public void train(Player p, String techId) {
        train(p, techId, null);
    }

    /**
     * Demande du client (« S'entraîner » / « Terminer »). {@code libId} non nul : lecture sur place devant cette
     * bibliothèque (rouleau dans son fonds, maître présent) ; sinon il faut le rouleau en main.
     */
    public void train(Player p, String techId, String libId) {
        Library lib = libId == null ? null : channel.byId(libId);
        ItemStack it = p.getInventory().getItemInMainHand();
        Ability a = catalog.byId(techId);
        if (libId != null) {
            if (a == null) return;
            String err = onsiteError(p, lib, a);
            if (err != null) { channel.toast(p, err); return; }
        } else {
            String held = ParcheminItems.abilityIdOf(it);
            if (held == null || !held.equals(techId)) { channel.toast(p, "Garde le rouleau en main pour t'entraîner."); return; }
        }
        ShinobiCharacter c = core.characters().getActive(p.getUniqueId());
        if (a == null || c == null) return;
        if (c.knowsAbility(a.id())) { channel.toast(p, "Tu connais déjà cette technique."); return; }
        if (isPending(c.id(), a.id())) { channel.toast(p, "Ta demande de validation est entre les mains du staff."); return; }
        if (core.ko() != null && core.ko().isKo(p.getUniqueId())) return;
        // États (étape 2) : ni porté, ni gelé, ni étourdi, ni entravé, ni en mode staff (CANALISER).
        var etats = com.reborn.shinobicore.api.EtatService.get();
        String refusEtat = etats == null ? null : etats.refus(p.getUniqueId(), com.reborn.shinobicore.api.etat.Capacite.CANALISER);
        if (refusEtat != null) { channel.toast(p, refusEtat); return; }
        if (minigame.isActive(p)) { channel.toast(p, "Termine d'abord ton entraînement en cours."); return; }
        Progress pr = progress.getOrDefault(c.id(), Map.of()).getOrDefault(a.id(), new Progress(0, 0));
        int total = ScrollCatalog.seances(ScrollCatalog.rank(a));
        if (pr.done() >= total) { complete(p, c, a, lib != null ? null : scrollIdOrNew(it), lib != null); return; }
        if (pr.last() != 0 && System.currentTimeMillis() - pr.last() < cooldownMs) {
            channel.toast(p, "Ton corps a besoin de repos : prochaine séance plus tard.");
            return;
        }
        Player master = masterNear(p, a.id());
        training.put(p.getUniqueId(), new Training(a.id(), lib != null ? null : scrollIdOrNew(it), master != null,
                lib != null ? lib.id : null));
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
        Library lib = tr.libId() == null ? null : channel.byId(tr.libId());
        if (tr.libId() != null) {                                          // lecture sur place
            if (lib == null || channel.fondsCellOf(lib, a.id()) < 0) {
                p.sendMessage(Component.text("Le rouleau a quitté l'étagère : la séance ne compte pas.", NamedTextColor.RED));
                return;
            }
            if (lib.distanceSq(p.getLocation()) > LibraryService.REACH_SQ) {
                p.sendMessage(Component.text("Tu t'es éloigné de la bibliothèque : la séance ne compte pas.", NamedTextColor.RED));
                return;
            }
        } else {
            ItemStack it = p.getInventory().getItemInMainHand();
            if (!a.id().equals(ParcheminItems.abilityIdOf(it))) {
                p.sendMessage(Component.text("Tu as lâché le rouleau : la séance ne compte pas.", NamedTextColor.RED));
                return;
            }
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
            xp(p, a, "seance-parchemin", 40);
            p.sendMessage(Component.text("Séance réussie" + (gain > 1 ? " (double, grâce à ton maître)" : "")
                    + " : " + done + " / " + total + ".", NamedTextColor.GREEN));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        } else {
            p.sendMessage(Component.text("Séance ratée : elle ne compte pas. Réessaie plus tard.", NamedTextColor.RED));
        }
        if (lib == null && rnd.nextDouble() < mishap) {                    // easter egg : le rouleau est perdu
            String msg = MISHAPS[rnd.nextInt(MISHAPS.length)];
            p.getInventory().setItemInMainHand(null);
            if (ScrollCatalog.rank(a) == 'S') slots.release(a.id(), "rouleau:" + tr.scrollId());
            p.sendMessage(Component.text(msg, NamedTextColor.GRAY, TextDecoration.ITALIC));
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.6f);
            log(p, "Parchemin perdu (aléa)", a.name());
            return;
        }
        if (done >= total) complete(p, c, a, tr.scrollId(), lib != null);
        else channel.send(p, readJson(p, c, a, lib));
    }

    /** Toutes les séances faites : apprise, ou demande de validation staff pour le rang S. */
    private void complete(Player p, ShinobiCharacter c, Ability a, String scrollId, boolean onsite) {
        if (onsite) {                                                      // sur place : le rouleau reste sur l'étagère
            learn(p, c, a, null);
            return;
        }
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
        c.learnAbility(a.id(), com.reborn.shinobicore.character.LearnOrigin.PARCHEMIN);
        core.characters().save(c);
        Map<String, Progress> m = progress.get(c.id());
        if (m != null) m.remove(a.id());
        learnedAt.computeIfAbsent(c.id(), k -> new HashMap<>()).put(a.id(), System.currentTimeMillis());
        if (ScrollCatalog.rank(a) == 'S' && scrollId != null) slots.transfer(a.id(), scrollId, c.id().toString());
        save();
        p.showTitle(Title.title(
                Component.text("Technique apprise !", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(a.name(), NamedTextColor.AQUA),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        xp(p, a, "technique-apprise", 100);
        log(p, "Technique apprise (parchemin)", a.name() + " · rang " + ScrollCatalog.rank(a));
    }

    /** XP de progression (ShinobiCore), pondérée par le rang de la technique. */
    private void xp(Player p, Ability a, String key, double def) {
        var xp = org.bukkit.Bukkit.getServicesManager().load(com.reborn.shinobicore.api.XpService.class);
        if (xp == null) return;
        double mult = switch (ScrollCatalog.rank(a)) { case 'C' -> 1.5; case 'B' -> 2; case 'A' -> 3; case 'S' -> 5; default -> 1; };
        xp.reward(p.getUniqueId(), key, def, mult);
    }

    // ─────────────────────────────────────────────────────────────── mort RP

    /**
     * Mort RP validée : le personnage emporte ce qu'il savait. Ses slots de rang S sont libérés, sauf si le rouleau
     * correspondant est sur lui — le slot retourne alors au rouleau (héritage : celui qui le récupère pourra
     * l'apprendre). Ses séances en cours et ses demandes de validation disparaissent.
     */
    @org.bukkit.event.EventHandler
    public void onCharacterDeath(com.reborn.shinobicore.event.CharacterDeathEvent e) {
        UUID charId = e.characterId();
        java.util.List<String> freed = slots.releaseEverywhere("perso:" + charId);
        Player p = org.bukkit.Bukkit.getPlayer(e.playerId());
        int inherited = 0;
        for (String techId : freed) {
            String carried = p == null ? null : carriedScroll(p, techId);
            if (carried != null) { slots.reserve(techId, "rouleau:" + carried); inherited++; }
        }
        progress.remove(charId);
        learnedAt.remove(charId);
        pending.removeIf(pd -> pd.charId().equals(charId));
        save();
        if (!freed.isEmpty()) {
            plugin.getLogger().info("[parchemins] mort RP de " + e.characterName() + " : " + freed.size()
                    + " slot(s) de rang S libéré(s), " + inherited + " repassé(s) au rouleau porté.");
        }
    }

    /** Identifiant du rouleau de {@code techId} porté par le joueur (inventaire principal), ou null. */
    public static String carriedScroll(Player p, String techId) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null) continue;
            if (techId.equals(ParcheminItems.abilityIdOf(it))) return ParcheminItems.scrollIdOf(it);
        }
        return null;
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
        com.reborn.shinobiabilities.util.Donner.a(p, it);
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
