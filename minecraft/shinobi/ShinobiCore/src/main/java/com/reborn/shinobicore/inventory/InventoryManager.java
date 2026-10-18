package com.reborn.shinobicore.inventory;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.InventoryService;
import com.reborn.shinobicore.api.StunService;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.character.LeafTestItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Sacoche RP</b> — inventaire custom <b>lié au personnage actif</b>, modèle « base = hotbar vanilla ». Les 9 cases
 * de base affichées dans la Sacoche sont la <b>vraie barre d'action</b> du joueur ; ce manager n'est autoritaire que
 * pour l'<b>espace supplémentaire</b> débloqué par un sac ({@link RpBag}), le sac porté, les cosmétiques et l'<b>étui</b>
 * (emplacement Arme) — persistés par perso ({@code plugins/ShinobiCore/rpbags/<charUuid>.yml}), en vrais
 * {@link ItemStack} sérialisés (base64).
 *
 * <p>Canal {@code reborn:inventory}. C2S : {@code open}, {@code swap:<A>:<B>} (A/B = {@code H0..H8} barre ou
 * {@code B0..Bn} sac), {@code bag:equip:<ref>} / {@code bag:unequip}, {@code cos:equip:<ref>:<SLOT>} /
 * {@code cos:unequip:<SLOT>}, {@code arme:equip:<ref>} / {@code arme:unequip} / {@code arme:basculer} (dégainer ou
 * rengainer), {@code drop:<ref>}, {@code del:<ref>}, {@code use:<ref>}, {@code pick:<uuid de l'objet au sol>}. S2C :
 * JSON (capacité, contenu du sac, équipements, {@code ouvrir}, {@code lecture}, {@code palier}).
 *
 * <p>Ajouts du 2026-10-13 (GAMEPLAN §2-3) : ramassage volontaire ({@link Ramassage}), surcharge par paliers
 * ({@link Surcharge}), objets au sol ({@link ObjetsAuSol}), notifications ({@link Notifs}), journal
 * ({@link JournalObjets}), lecture seule en KO / porté / étourdi / scène de mort, cases cachées 9-35 vidées.
 */
public class InventoryManager implements Listener, PluginMessageListener, CommandExecutor, InventoryService {

    public static final String CHANNEL = "reborn:inventory";
    /** Canal S2C de diffusion des cosmétiques équipés (uuid + json slot->{mat,model}). */
    public static final String COSMETICS_CHANNEL = "reborn:cosmetics";
    private static final int SAVE_FORMAT = 2;
    /** Marque l'arme sortie de l'étui (dans la barre). */
    private static final NamespacedKey DEGAINEE = new NamespacedKey("reborn", "degainee");

    private final ShinobiCore plugin;
    private final Map<UUID, RpBag> cache = new ConcurrentHashMap<>();
    private final Surcharge surcharge = new Surcharge();
    private final Map<UUID, String> dernierEtat = new ConcurrentHashMap<>();
    private Ramassage ramassage;
    private File dir;
    private StunService stun;
    private boolean stunCherche;

    public InventoryManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            m.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL)) {
            m.registerIncomingPluginChannel(plugin, CHANNEL, this);
        }
        // Diffusion des cosmétiques équipés (S2C uniquement) : autoritaire serveur,
        // pas de réception cliente.
        if (!m.isOutgoingChannelRegistered(plugin, COSMETICS_CHANNEL)) {
            m.registerOutgoingPluginChannel(plugin, COSMETICS_CHANNEL);
        }
        Notifs.start(plugin);
        dir = new File(plugin.getDataFolder(), "rpbags");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[sacoche] Impossible de créer le dossier rpbags.");
        }
        InventaireConfig.get(plugin);
        ramassage = new Ramassage(plugin, this);
        Bukkit.getPluginManager().registerEvents(ramassage, plugin);
        ObjetsAuSol sol = new ObjetsAuSol(plugin);
        Bukkit.getPluginManager().registerEvents(sol, plugin);
        sol.start();
        Bukkit.getServicesManager().register(InventoryService.class, this, plugin, ServicePriority.Normal);
        // Filet de sécurité : poids, palier, cases cachées, chaque seconde (et recalcul à chaque changement).
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
        plugin.getLogger().info("[sacoche] Inventaire RP prêt (canal " + CHANNEL + ", base = hotbar, ramassage F, "
            + "surcharge par paliers, canal " + Notifs.CHANNEL + ").");
    }

    // ─────────── Surcharge / état (chaque seconde + à chaque changement) ───────────
    private void tick() {
        InventaireConfig cfg = InventaireConfig.get(plugin);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (cfg.balayerCasesCachees && survie(p) && activeCharId(p) != null && cache.containsKey(activeCharId(p))) {
                rangerCasesCachees(p);
            }
            recalc(p);
        }
    }

    private static boolean survie(Player p) {
        GameMode gm = p.getGameMode();
        return gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE;
    }

    /** Recalcule poids → palier (vitesse / saut), annonce un changement de palier, pousse l'état au mod si changé. */
    public void recalc(Player p) {
        if (p == null || !p.isOnline()) return;
        UUID cid = activeCharId(p);
        RpBag b = cid != null ? cache.get(cid) : null;
        boolean actif = b != null && survie(p);
        if (!actif) {
            if (surcharge.palier(p.getUniqueId()) != NORMAL) surcharge.appliquer(p, 0, 1, InventaireConfig.get(plugin), false);
            else if (b == null) surcharge.retirer(p);
            // Créatif (15/10) : pas de surcharge, mais l'état est poussé quand même (palier 0) — le mod n'active le
            // ramassage volontaire (F) qu'après l'avoir reçu, et l'indicateur de surcharge doit retomber.
            if (b == null || p.getGameMode() == GameMode.SPECTATOR) return;
        }
        double cur = hotbarWeight(p) + b.storageWeight();
        double max = b.maxWeight();
        int avant = actif ? surcharge.appliquer(p, cur, max, InventaireConfig.get(plugin), true) : -1;
        if (avant >= 0) Surcharge.annoncer(p, avant, surcharge.palier(p.getUniqueId()));
        int libres = 0;
        for (int i = 0; i < RpBag.HOTBAR_SLOTS; i++) if (isEmpty(p.getInventory().getItem(i))) libres++;
        for (int i = 0; i < b.extraSlots(); i++) if (b.getStorage(i) == null) libres++;
        String verrou = lockReason(p);
        int pal = actif ? surcharge.palier(p.getUniqueId()) : NORMAL;
        String sig = round2(cur) + "|" + round2(max) + "|" + pal + "|" + libres + "|" + verrou;
        if (!sig.equals(dernierEtat.get(p.getUniqueId()))) {
            dernierEtat.put(p.getUniqueId(), sig);
            Notifs.poids(p, cur, max, pal, libres, verrou);
        }
    }

    /** Vitesse et saut calculés par la surcharge ({vitesse, saut}), ou null — l'applicateur des états les pose. */
    public double[] facteursSurcharge(UUID playerId) {
        return surcharge.facteurs(playerId);
    }

    @Override
    public int palier(UUID playerId) {
        return surcharge.palier(playerId);
    }

    // ─────────── Verrou (KO, porté, étourdi, scène de mort) ───────────
    @Override
    public String lockReason(Player p) {
        if (p == null) return null;
        UUID id = p.getUniqueId();
        try {
            if (plugin.deathScene() != null && plugin.deathScene().isDead(id)) return "mort";
            if (plugin.ko() != null && plugin.ko().isKo(id)) return "KO";
            if (plugin.porter() != null && plugin.porter().carriedBy(id) != null) return "porté";
            // Correctifs urgents (ETATS_JOUEUR §2.2 h) : le lâcher par la sacoche contournait le gel ; tenu à la gorge.
            if (com.reborn.shinobicore.etat.Verrous.gele(id)) return "gelé";
            if (com.reborn.shinobicore.etat.Verrous.tenu(id)) return "tenu";
        } catch (RuntimeException ignored) { }
        StunService s = stun();
        if (s != null && s.isStunned(id)) return "étourdi";
        // Étape 2 d'ETATS_JOUEUR : le reste vient de la matrice (MODIFIER_SACOCHE) — entravé, menotté, mode staff…
        var etats = plugin.etats();
        if (etats != null) {
            var v = etats.verifier(id, com.reborn.shinobicore.api.etat.Capacite.MODIFIER_SACOCHE, null);
            if (!v.ok()) return codeVerrou(v.ligne(), v.raison());
        }
        return null;
    }

    /** Ligne de la matrice → code court affiché par le mod (« KO », « porté », « gelé »…). */
    private static String codeVerrou(String ligne, String raison) {
        if (ligne == null) return raison;
        if (ligne.startsWith("vital.MORT")) return "mort";
        if (ligne.startsWith("vital.A_TERRE") || ligne.startsWith("vital.INCONSCIENT")) return "KO";
        if (ligne.equals("controle.PORTE")) return "porté";
        if (ligne.startsWith("controle.PORTE.") || ligne.startsWith("controle.ENTRAVE.EXECUTION")) return "tenu";
        if (ligne.startsWith("controle.ETOURDI")) return "étourdi";
        if (ligne.startsWith("mod.gel_staff")) return "gelé";
        if (ligne.startsWith("controle.ENTRAVE")) return "entravé";
        if (ligne.startsWith("controle.CONTROLE")) return "contrôlé";
        if (ligne.startsWith("controle.CORPS_VIDE")) return "esprit ailleurs";
        if (ligne.startsWith("mod.menottes")) return "menotté";
        if (ligne.startsWith("cadre.STAFF")) return "mode staff";
        if (ligne.startsWith("cadre.HORS_JEU")) return "hors jeu";
        return raison;
    }

    private StunService stun() {
        if (stun == null && !stunCherche) {
            stun = Bukkit.getServicesManager().load(StunService.class);
            if (stun == null) {
                // ShinobiCombat peut s'activer après nous : on réessaie plus tard, sans le chercher à chaque appel.
                stunCherche = true;
                Bukkit.getScheduler().runTaskLater(plugin, () -> stunCherche = false, 200L);
            }
        }
        return stun;
    }

    // ─────────── Résolution perso actif → sac ───────────
    private UUID activeCharId(Player p) {
        try {
            var c = plugin.characters().getActive(p.getUniqueId());
            return c != null ? c.id() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private RpBag bagFor(Player p) {
        UUID cid = activeCharId(p);
        if (cid == null) return null;
        return cache.computeIfAbsent(cid, this::loadOrSeed);
    }

    // ─────────── Persistance ───────────
    private RpBag loadOrSeed(UUID charId) {
        File f = new File(dir, charId + ".yml");
        if (f.exists()) {
            RpBag b = load(f);
            if (b != null) return b;
        }
        RpBag b = new RpBag(BagTier.NONE); // démarre SANS sac (9 cases = hotbar)
        saveBag(charId, b);
        return b;
    }

    private String enc(ItemStack s) {
        return (s == null || s.getType().isAir()) ? "" : Base64.getEncoder().encodeToString(s.serializeAsBytes());
    }

    private ItemStack dec(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(b64));
        } catch (Exception e) {
            return null;
        }
    }

    private RpBag load(File f) {
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            BagTier tier = BagTier.fromName(cfg.getString("bagTier"));
            RpBag b = new RpBag(tier);
            int fmt = cfg.getInt("format", 1);
            if (fmt >= 2) {
                b.setWornBag(dec(cfg.getString("wornBag")));
                List<String> st = cfg.getStringList("storage");
                for (int i = 0; i < st.size() && i < b.storage().length; i++) {
                    b.setStorage(i, dec(st.get(i)));
                }
                ConfigurationSection eq = cfg.getConfigurationSection("equipped");
                if (eq != null) {
                    for (String key : eq.getKeys(false)) {
                        CosmeticSlot slot = CosmeticSlot.fromName(key);
                        ItemStack it = dec(eq.getString(key));
                        if (slot != null && it != null) b.equipped().put(slot, it);
                    }
                }
                // Placements 3D par modèle : lignes "modelId=serialized" (le '=' ne
                // peut apparaître ni dans l'id ni dans le transform sérialisé).
                for (String line : cfg.getStringList("cosTransforms")) {
                    int i = line.indexOf('=');
                    if (i > 0) b.cosTransforms().put(line.substring(0, i), line.substring(i + 1));
                }
            }
            // fmt == 1 : ancien format data-driven → on garde juste le tier, contenu vide.
            return b;
        } catch (Exception e) {
            plugin.getLogger().warning("[sacoche] Lecture échouée (" + f.getName() + ") : " + e.getMessage());
            return null;
        }
    }

    private void saveBag(UUID charId, RpBag b) {
        File f = new File(dir, charId + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("format", SAVE_FORMAT);
        cfg.set("bagTier", b.bagTier().name());
        cfg.set("wornBag", enc(b.wornBag()));
        List<String> st = new ArrayList<>();
        for (ItemStack s : b.storage()) st.add(enc(s)); // garde l'alignement d'index (vides = "")
        cfg.set("storage", st);
        for (Map.Entry<CosmeticSlot, ItemStack> e : b.equipped().entrySet()) {
            cfg.set("equipped." + e.getKey().name(), enc(e.getValue()));
        }
        if (!b.cosTransforms().isEmpty()) {
            List<String> tfs = new ArrayList<>();
            for (Map.Entry<String, String> e : b.cosTransforms().entrySet()) {
                tfs.add(e.getKey() + "=" + e.getValue());
            }
            cfg.set("cosTransforms", tfs);
        }
        try {
            cfg.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("[sacoche] Sauvegarde échouée (" + f.getName() + ") : " + e.getMessage());
        }
    }

    private void persist(Player p, RpBag b) {
        UUID cid = activeCharId(p);
        if (cid != null) saveBag(cid, b);
    }

    // ─────────── Envoi au client (JSON) ───────────
    /** Pousse la sacoche et demande au mod de l'ouvrir (touche E, /sacoche). */
    public void push(Player p) {
        push(p, true);
    }

    /**
     * Pousse la sacoche. {@code ouvrir = false} : rafraîchit l'écran s'il est ouvert, sans l'ouvrir (dons, ramassage,
     * actions). Les anciens mods, qui ne lisent pas {@code ouvrir}, ouvrent comme avant.
     */
    public void push(Player p, boolean ouvrir) {
        RpBag b = bagFor(p);
        if (b == null) {
            if (ouvrir) Notifs.msg(p, "refus", "袋", "Aucun personnage actif", "impossible d'ouvrir le sac");
            return;
        }
        byte[] bytes = buildJson(p, b, ouvrir).getBytes(StandardCharsets.UTF_8);
        try {
            p.sendPluginMessage(plugin, CHANNEL, bytes);
        } catch (Exception ignored) {
            // canal non enregistré côté client (pas de mod-hud) → ignoré.
        }
    }

    private String buildJson(Player p, RpBag b, boolean ouvrir) {
        double cur = hotbarWeight(p) + b.storageWeight();
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"ouvrir\":").append(ouvrir).append(',');
        sb.append("\"bagTier\":\"").append(b.bagTier().name()).append("\",");
        sb.append("\"baseSlots\":").append(RpBag.HOTBAR_SLOTS).append(',');
        sb.append("\"extraSlots\":").append(b.extraSlots()).append(',');
        sb.append("\"maxWeight\":").append(round2(b.maxWeight())).append(',');
        sb.append("\"curWeight\":").append(round2(cur)).append(',');
        sb.append("\"palier\":").append(surcharge.palier(p.getUniqueId())).append(',');
        sb.append("\"poidsMax\":").append(InventaireConfig.get(plugin).poidsMaxRatio).append(',');
        String verrou = lockReason(p);
        if (verrou != null) sb.append("\"lecture\":\"").append(esc(verrou)).append("\",");
        sb.append("\"armeDegainee\":").append(weaponDrawn(p)).append(',');
        sb.append("\"hotbar\":[");
        for (int i = 0; i < RpBag.HOTBAR_SLOTS; i++) {
            if (i > 0) sb.append(',');
            sb.append(itemJson(p.getInventory().getItem(i)));
        }
        sb.append("],");
        sb.append("\"bagItem\":").append(itemJson(b.wornBag())).append(',');
        sb.append("\"bag\":[");
        for (int i = 0; i < b.storage().length; i++) {
            if (i > 0) sb.append(',');
            sb.append(itemJson(b.getStorage(i)));
        }
        sb.append("],\"equipped\":{");
        boolean first = true;
        for (Map.Entry<CosmeticSlot, ItemStack> e : b.equipped().entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append(',');
            sb.append('"').append(e.getKey().name()).append("\":").append(itemJson(e.getValue()));
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    /** Sérialise un item pour le rendu client : {mat, model?, count, name?, weight, rarity, arme?, unique?}. */
    private String itemJson(ItemStack s) {
        if (s == null || s.getType().isAir()) return "null";
        String mat = s.getType().getKey().toString();
        String model = null, name = null;
        if (s.hasItemMeta()) {
            ItemMeta m = s.getItemMeta();
            try {
                if (m.hasItemModel()) {
                    NamespacedKey k = m.getItemModel();
                    if (k != null) model = k.toString();
                }
            } catch (Throwable ignored) { }
            try {
                // Nexo nomme ses items via le composant item_name (pas custom_name/displayName) :
                // sans ce fallback, un item Nexo rangé dans le sac s'affichait « Paper ».
                if (m.hasDisplayName()) {
                    name = PlainTextComponentSerializer.plainText().serialize(m.displayName());
                } else if (m.hasItemName()) {
                    name = PlainTextComponentSerializer.plainText().serialize(m.itemName());
                }
            } catch (Throwable ignored) { }
        }
        // Description (lore) + éventuelle action contextuelle (ex. test de la feuille).
        String desc = null;
        if (s.hasItemMeta() && s.getItemMeta().hasLore()) {
            java.util.List<Component> lore = s.getItemMeta().lore();
            StringBuilder d = new StringBuilder();
            if (lore != null) {
                for (Component c : lore) {
                    String line = PlainTextComponentSerializer.plainText().serialize(c);
                    if (d.length() > 0) d.append('\n');
                    d.append(line);
                }
            }
            if (d.length() > 0) desc = d.toString();
        }
        boolean leaf = LeafTestItem.isLeafTest(plugin, s);

        StringBuilder b = new StringBuilder(96);
        b.append("{\"mat\":\"").append(esc(mat)).append("\",");
        b.append("\"count\":").append(s.getAmount()).append(',');
        b.append("\"weight\":").append(round2(RpWeights.of(s)));
        if (model != null) b.append(",\"model\":\"").append(esc(model)).append('"');
        if (name != null) b.append(",\"name\":\"").append(esc(name)).append('"');
        if (desc != null) b.append(",\"desc\":\"").append(esc(desc)).append('"');
        b.append(",\"rarity\":\"").append(leaf ? RpRarity.RARE.name() : RareteItems.of(s).name()).append('"');
        if (RareteItems.arme(s)) b.append(",\"arme\":true");
        if (RareteItems.unique(s)) b.append(",\"unique\":true");
        if (com.reborn.shinobicore.monnaie.Bourse.est(s)) b.append(",\"ryo\":").append(com.reborn.shinobicore.monnaie.Bourse.montant(s));
        if (leaf) b.append(",\"action\":{\"id\":\"tirage\",\"label\":\"Faire le test\"}");
        b.append('}');
        return b.toString();
    }

    // ─────────── Diffusion des cosmétiques (S2C, visibles entre joueurs) ───────────
    /**
     * Diffuse les cosmétiques équipés de {@code p} à TOUS les joueurs (pour qu'ils
     * voient ses modèles 3D portés) et envoie à {@code p} ceux de tous les autres
     * (pour qu'il voie les leurs) — même schéma que
     * {@code CharacterSelectManager.broadcastActive} pour les skins. Message S2C
     * {@code reborn:cosmetics} = {@code <uuid>\n<json>} (corps vide = aucun cosmétique).
     * L'arme à l'étui y figure comme emplacement {@code ARME} (portée à la ceinture).
     */
    public void broadcastCosmetics(Player p) {
        if (!Bukkit.getMessenger().isOutgoingChannelRegistered(plugin, COSMETICS_CHANNEL)) return;
        byte[] mine = cosmeticsPayload(p);
        for (Player other : Bukkit.getOnlinePlayers()) {
            sendCosmetics(other, mine);                       // p → tous
            if (other.equals(p)) continue;
            sendCosmetics(p, cosmeticsPayload(other));        // autres → p
        }
    }

    /** Diffuse un « clear » des cosmétiques de {@code id} à tous les autres (déconnexion). */
    public void broadcastCosmeticsClear(UUID id) {
        if (!Bukkit.getMessenger().isOutgoingChannelRegistered(plugin, COSMETICS_CHANNEL)) return;
        byte[] clear = (id + "\n").getBytes(StandardCharsets.UTF_8);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(id)) continue;
            sendCosmetics(other, clear);
        }
    }

    /** Applique un placement 3D reçu du client : {@code cos:tf\n<modelId>\n<serialized>}. */
    private void handleCosTransform(Player p, RpBag b, String msg) {
        String[] lines = msg.split("\n", 3);
        if (lines.length < 3) return;
        String modelId = lines[1].trim();
        String serialized = lines[2].trim();
        if (modelId.isEmpty()) return;
        if (serialized.isEmpty()) b.cosTransforms().remove(modelId);
        else b.cosTransforms().put(modelId, serialized);
        persist(p, b);
        broadcastCosmetics(p); // rediffuse le nouveau placement à tous → visible instantanément
    }

    /** {@code <uuid>\n{"SLOT":{"mat":..,"model":..,"t":..}, ...}} des cosmétiques équipés de {@code p}. */
    private byte[] cosmeticsPayload(Player p) {
        RpBag b = bagFor(p); // null si aucun perso actif (ex. en sélection) → corps vide
        StringBuilder sb = new StringBuilder(128);
        sb.append(p.getUniqueId()).append('\n').append('{');
        if (b != null) {
            boolean first = true;
            for (Map.Entry<CosmeticSlot, ItemStack> e : b.equipped().entrySet()) {
                ItemStack it = e.getValue();
                if (it == null || it.getType().isAir()) continue;
                if (!first) sb.append(',');
                sb.append('"').append(e.getKey().name()).append("\":").append(cosItemJson(it, b));
                first = false;
            }
        }
        sb.append('}');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Cosmétique pour le rendu distant : {@code {mat, model?, t?}} ({@code t} = placement appliqué). */
    private String cosItemJson(ItemStack s, RpBag bag) {
        String mat = s.getType().getKey().toString();
        String model = null;
        if (s.hasItemMeta()) {
            try {
                ItemMeta m = s.getItemMeta();
                if (m.hasItemModel()) {
                    NamespacedKey k = m.getItemModel();
                    if (k != null) model = k.toString();
                }
            } catch (Throwable ignored) { }
        }
        String tf = (model != null) ? bag.cosTransforms().get(model) : null;
        StringBuilder b = new StringBuilder(64);
        b.append("{\"mat\":\"").append(esc(mat)).append('"');
        if (model != null) b.append(",\"model\":\"").append(esc(model)).append('"');
        if (tf != null && !tf.isEmpty()) b.append(",\"t\":\"").append(esc(tf)).append('"');
        b.append('}');
        return b.toString();
    }

    private void sendCosmetics(Player to, byte[] bytes) {
        try {
            to.sendPluginMessage(plugin, COSMETICS_CHANNEL, bytes);
        } catch (Exception ignored) {
            // canal non enregistré côté client (pas de mod-hud) → ignoré.
        }
    }

    private double hotbarWeight(Player p) {
        double w = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < RpBag.HOTBAR_SLOTS; i++) w += RpWeights.of(inv.getItem(i));
        return w;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
    }

    // ─────────── Réception des actions client ───────────
    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        String msg = new String(message, StandardCharsets.UTF_8).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handle(p, msg));
    }

    private void handle(Player p, String msg) {
        RpBag b = bagFor(p);
        if (b == null) return;

        if (msg.equals("open")) {
            push(p, true);
            return;
        }
        // Placement 3D d'un cosmétique appliqué côté client : format \n-délimité
        // (l'id de modèle contient un ':') → routé AVANT le split ':'.
        if (msg.startsWith("cos:tf\n")) {
            handleCosTransform(p, b, msg);
            return;
        }
        String[] parts = msg.split(":");
        String cmd = parts[0];
        // Mode staff : ni ramasser, ni lâcher, ni utiliser (manger, pilule), ni détruire (aucune mécanique RP).
        if ((cmd.equals("pick") || cmd.equals("drop") || cmd.equals("use") || cmd.equals("del"))
                && com.reborn.shinobicore.staff.outils.ModeStaff.horsJeu(p)) {
            Notifs.msg(p, "refus", null, "Mode staff", " : aucune action RP sur les objets.");
            push(p, false);
            return;
        }
        if (cmd.equals("pick")) {
            if (parts.length >= 2 && ramassage.ramasser(p, b, parts[1])) {
                persist(p, b);
                p.updateInventory();
                push(p, false);
                recalc(p);
            }
            return;
        }
        // Lecture seule en KO / porté / étourdi / scène de mort : rien ne bouge (l'équipement reste en place).
        String verrou = lockReason(p);
        if (verrou != null) {
            Notifs.msg(p, "etat", "止", "Impossible " + Ramassage.verrou(verrou), null);
            push(p, false);
            return;
        }
        // Tenue imposée par le staff (/tenue, 14/10) : l'emplacement TENUE ne bouge plus.
        if (cmd.equals("cos") && com.reborn.shinobicore.staff.outils.TenuesForcees.verrouille(p, parts)) {
            Notifs.msg(p, "refus", "禁", "Ta tenue est imposée par le staff.", null);
            push(p, false);
            return;
        }
        // Journal universel : objet utilisé (nom lu avant l'action, qui peut le consommer).
        String utilise = cmd.equals("use") && parts.length >= 2 ? journalNom(refGet(p, b, parts[1])) : null;
        boolean changed = false;
        boolean touched = true; // repousse un snapshot frais après toute action (resync)
        switch (cmd) {
            case "swap" -> {
                if (parts.length >= 3) changed = doSwap(p, b, parts[1], parts[2]);
            }
            case "bag" -> {
                if (parts.length >= 3 && parts[1].equals("equip")) changed = equipBag(p, b, parts[2]);
                else if (parts.length >= 2 && parts[1].equals("unequip")) changed = unequipBag(p, b);
            }
            case "cos" -> {
                if (parts.length >= 4 && parts[1].equals("equip")) {
                    CosmeticSlot cs = CosmeticSlot.fromName(parts[3]);
                    if (cs == CosmeticSlot.ARME) changed = equipArme(p, b, parts[2]);
                    else if (cs != null) changed = equipCos(p, b, parts[2], cs);
                } else if (parts.length >= 3 && parts[1].equals("unequip")) {
                    CosmeticSlot cs = CosmeticSlot.fromName(parts[2]);
                    if (cs != null) changed = unequipCos(p, b, cs);
                }
                // Un cosmétique a changé → rediffuse à tous pour une MAJ live
                // (visible entre joueurs sans reconnexion).
                if (changed) broadcastCosmetics(p);
            }
            case "arme" -> {
                if (parts.length >= 3 && parts[1].equals("equip")) changed = equipArme(p, b, parts[2]);
                else if (parts.length >= 2 && parts[1].equals("unequip")) changed = unequipCos(p, b, CosmeticSlot.ARME);
                else if (parts.length >= 2 && parts[1].equals("basculer")) changed = basculerArme(p, b);
                if (changed) broadcastCosmetics(p);
            }
            case "drop" -> {
                if (parts.length >= 2) changed = doDrop(p, b, parts[1]);
            }
            case "del" -> {
                if (parts.length >= 2) changed = doDelete(p, b, parts[1]);
            }
            case "use" -> {
                if (parts.length >= 2) doUse(p, b, parts[1]);
                // L'action (ex. test de la feuille) ouvre son propre écran client :
                // ne rien repousser pour ne pas rouvrir la Sacoche par-dessus.
                touched = false;
            }
            default -> touched = false;
        }
        if ((changed && (cmd.equals("bag") || cmd.equals("cos") || cmd.equals("arme"))) || utilise != null) {
            com.reborn.shinobicore.journal.Journal.entree(com.reborn.shinobicore.api.JournalService.Categorie.INVENTAIRE,
                    utilise != null ? "UTILISE" : "EQUIPEMENT").acteur(p)
                    .detail(utilise != null ? utilise : msg).lieu(p.getLocation()).ecrire();
        }
        if (changed) persist(p, b);
        if (touched) {
            p.updateInventory();
            push(p, false);
        }
        if (changed) recalc(p);
    }

    private static String journalNom(ItemStack s) {
        return s == null || s.getType().isAir() ? null : s.getAmount() + "× " + RareteItems.nom(s);
    }

    // ─────────── Résolution des références de slot (H0..H8 / B0..Bn) ───────────
    private ItemStack refGet(Player p, RpBag b, String ref) {
        int i = refIndex(ref);
        if (i < 0) return null;
        char t = ref.charAt(0);
        if (t == 'H') return (i < RpBag.HOTBAR_SLOTS) ? p.getInventory().getItem(i) : null;
        if (t == 'B') return b.getStorage(i);
        return null;
    }

    private boolean refSet(Player p, RpBag b, String ref, ItemStack s) {
        int i = refIndex(ref);
        if (i < 0) return false;
        char t = ref.charAt(0);
        if (t == 'H') {
            if (i >= RpBag.HOTBAR_SLOTS) return false;
            p.getInventory().setItem(i, s);
            return true;
        }
        if (t == 'B') {
            if (i >= b.extraSlots()) return false;
            b.setStorage(i, s == null ? null : sansDegainee(s));
            return true;
        }
        return false;
    }

    private int refIndex(String ref) {
        if (ref == null || ref.length() < 2) return -1;
        try {
            return Integer.parseInt(ref.substring(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ─────────── Déplacement (swap / fusion) ───────────
    private boolean doSwap(Player p, RpBag b, String a, String c) {
        if (a.equals(c)) return false;
        ItemStack sa = refGet(p, b, a);
        ItemStack sc = refGet(p, b, c);
        if (isEmpty(sa) && isEmpty(sc)) return false;

        // Fusion si même type empilable.
        if (!isEmpty(sa) && !isEmpty(sc) && sa.isSimilar(sc) && sc.getAmount() < sc.getMaxStackSize()) {
            int room = sc.getMaxStackSize() - sc.getAmount();
            int move = Math.min(room, sa.getAmount());
            ItemStack nc = sc.clone(); nc.setAmount(sc.getAmount() + move);
            ItemStack na = sa.clone(); na.setAmount(sa.getAmount() - move);
            refSet(p, b, c, nc);
            refSet(p, b, a, na.getAmount() <= 0 ? null : na);
            return true;
        }
        // Échange simple.
        refSet(p, b, c, isEmpty(sa) ? null : sa);
        refSet(p, b, a, isEmpty(sc) ? null : sc);
        return true;
    }

    private static boolean isEmpty(ItemStack s) {
        return s == null || s.getType().isAir();
    }

    // ─────────── Place / rangement (ramassage, dons) ───────────
    private static boolean pareil(ItemStack existant, ItemStack unite) {
        if (isEmpty(existant)) return false;
        if (existant.isSimilar(unite)) return true;
        return RareteItems.normaliser(existant.clone()).isSimilar(unite);
    }

    /** Combien d'exemplaires de {@code unite} (au plus {@code voulu}) tiennent : fusion dans les piles, puis cases vides. */
    int placePour(Player p, RpBag b, ItemStack unite, int voulu) {
        int max = unite.getMaxStackSize();
        int n = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < RpBag.HOTBAR_SLOTS && n < voulu; i++) {
            ItemStack s = inv.getItem(i);
            if (isEmpty(s)) n += max;
            else if (pareil(s, unite)) n += Math.max(0, s.getMaxStackSize() - s.getAmount());
        }
        for (int i = 0; i < b.extraSlots() && n < voulu; i++) {
            ItemStack s = b.getStorage(i);
            if (s == null) n += max;
            else if (pareil(s, unite)) n += Math.max(0, s.getMaxStackSize() - s.getAmount());
        }
        return Math.min(n, voulu);
    }

    /** Range une pile : d'abord dans les piles identiques (barre, sac), puis cases vides (barre, sac). Renvoie le reste. */
    int ranger(Player p, RpBag b, ItemStack pile) {
        if (isEmpty(pile)) return 0;
        ItemStack unite = RareteItems.normaliser(pile.clone());
        unite.setAmount(1);
        int reste = pile.getAmount();
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < RpBag.HOTBAR_SLOTS && reste > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!pareil(s, unite)) continue;
            int room = s.getMaxStackSize() - s.getAmount();
            if (room <= 0) continue;
            int m = Math.min(room, reste);
            ItemStack ns = RareteItems.normaliser(s.clone());
            ns.setAmount(s.getAmount() + m);
            inv.setItem(i, ns);
            reste -= m;
        }
        for (int i = 0; i < b.extraSlots() && reste > 0; i++) {
            ItemStack s = b.getStorage(i);
            if (!pareil(s, unite)) continue;
            int room = s.getMaxStackSize() - s.getAmount();
            if (room <= 0) continue;
            int m = Math.min(room, reste);
            ItemStack ns = RareteItems.normaliser(s.clone());
            ns.setAmount(s.getAmount() + m);
            b.setStorage(i, ns);
            reste -= m;
        }
        int max = unite.getMaxStackSize();
        for (int i = 0; i < RpBag.HOTBAR_SLOTS && reste > 0; i++) {
            if (!isEmpty(inv.getItem(i))) continue;
            int m = Math.min(max, reste);
            ItemStack ns = unite.clone();
            ns.setAmount(m);
            inv.setItem(i, ns);
            reste -= m;
        }
        for (int i = 0; i < b.extraSlots() && reste > 0; i++) {
            if (b.getStorage(i) != null) continue;
            int m = Math.min(max, reste);
            ItemStack ns = unite.clone();
            ns.setAmount(m);
            b.setStorage(i, ns);
            reste -= m;
        }
        return reste;
    }

    // ─────────── Sac équipé (débloque l'espace) ───────────
    private BagTier tierOf(ItemStack s) {
        if (isEmpty(s) || !s.hasItemMeta()) return null;
        try {
            ItemMeta m = s.getItemMeta();
            if (m.hasItemModel()) {
                NamespacedKey k = m.getItemModel();
                if (k != null) return BagTier.fromItemId(k.getKey());
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private boolean equipBag(Player p, RpBag b, String ref) {
        ItemStack it = refGet(p, b, ref);
        BagTier tier = tierOf(it);
        if (tier == null || tier == BagTier.NONE) return false;
        // Retire un exemplaire de la source.
        ItemStack one = it.clone(); one.setAmount(1);
        ItemStack rem = it.clone(); rem.setAmount(it.getAmount() - 1);
        refSet(p, b, ref, rem.getAmount() <= 0 ? null : rem);
        // Grossit le stockage (aucun débordement en agrandissant), pose le nouveau sac.
        ItemStack oldWorn = b.wornBag();
        List<ItemStack> overflow = b.setTier(tier);
        b.setWornBag(one);
        if (oldWorn != null) giveOrDrop(p, b, oldWorn);
        for (ItemStack o : overflow) giveOrDrop(p, b, o);
        Notifs.msg(p, "info", tier.glyph, tier.displayName + " équipé", "+" + tier.extraSlots + " cases");
        return true;
    }

    private boolean unequipBag(Player p, RpBag b) {
        if (b.bagTier() == BagTier.NONE) return false;
        for (ItemStack s : b.storage()) {
            if (!isEmpty(s)) {
                Notifs.msg(p, "refus", "袋", "Vide ton sac", "avant de le retirer");
                return false;
            }
        }
        ItemStack worn = b.wornBag();
        b.setTier(BagTier.NONE);
        b.setWornBag(null);
        if (worn != null) giveOrDrop(p, b, worn);
        return true;
    }

    // ─────────── Cosmétiques ───────────
    private boolean equipCos(Player p, RpBag b, String ref, CosmeticSlot cs) {
        ItemStack it = refGet(p, b, ref);
        if (isEmpty(it)) return false;
        ItemStack one = it.clone(); one.setAmount(1);
        ItemStack rem = it.clone(); rem.setAmount(it.getAmount() - 1);
        refSet(p, b, ref, rem.getAmount() <= 0 ? null : rem);
        ItemStack prev = b.equipped().put(cs, one);
        if (prev != null) giveOrDrop(p, b, prev);
        return true;
    }

    /**
     * Staff ({@code /tenue}, 14/10) : pose {@code s} (copie, quantité 1) dans l'emplacement {@code TENUE} du personnage
     * actif, ou le vide si {@code s} est null ; renvoie la pièce qui s'y trouvait (le code appelant la garde pour la
     * rendre). Persiste, rafraîchit et rediffuse les cosmétiques.
     */
    public ItemStack staffPoserTenue(Player p, ItemStack s) {
        RpBag b = bagFor(p);
        if (b == null) return null;
        ItemStack prev;
        if (isEmpty(s)) prev = b.equipped().remove(CosmeticSlot.TENUE);
        else {
            ItemStack one = s.clone();
            one.setAmount(1);
            prev = b.equipped().put(CosmeticSlot.TENUE, one);
        }
        persist(p, b);
        p.updateInventory();
        push(p, false);
        broadcastCosmetics(p);
        return prev;
    }

    @Override
    public ItemStack cosmetique(Player p, String slot) {
        CosmeticSlot cs = CosmeticSlot.fromName(slot);
        RpBag b = cs == null ? null : bagFor(p);
        ItemStack s = b == null ? null : b.equipped().get(cs);
        return isEmpty(s) ? null : s.clone();
    }

    @Override
    public ItemStack poserCosmetique(Player p, String slot, ItemStack s) {
        CosmeticSlot cs = CosmeticSlot.fromName(slot);
        RpBag b = cs == null ? null : bagFor(p);
        if (b == null) return null;
        ItemStack prev;
        if (isEmpty(s)) prev = b.equipped().remove(cs);
        else {
            ItemStack one = s.clone();
            one.setAmount(1);
            prev = b.equipped().put(cs, one);
        }
        persist(p, b);
        p.updateInventory();
        push(p, false);
        broadcastCosmetics(p);
        recalc(p);
        return prev;
    }

    /**
     * Staff ({@code /persoreset}, 14/10) : vide la sacoche du personnage {@code charId} (fichier supprimé, le code
     * appelant en a fait une sauvegarde). Si {@code online} le joue, la sacoche vide est re-semée et poussée.
     */
    public void staffReinitialiserSac(UUID charId, Player online) {
        if (charId == null || dir == null) return;
        cache.remove(charId);
        File f = new File(dir, charId + ".yml");
        if (f.exists() && !f.delete()) plugin.getLogger().warning("[sacoche] Suppression échouée : " + f.getName());
        if (online != null && charId.equals(activeCharId(online))) {
            bagFor(online);
            push(online, false);
            recalc(online);
        }
    }

    /** Dossier des sacoches ({@code rpbags/}), pour les sauvegardes staff. */
    public File staffDossierSacs() { return dir; }

    /** Copies de la barre, du sac porté et de l'équipement du personnage actif (lecture staff, {@code /voirsac}). */
    public java.util.Map<String, ItemStack> staffApercu(Player p) {
        java.util.Map<String, ItemStack> out = new java.util.LinkedHashMap<>();
        RpBag b = bagFor(p);
        if (b == null) return out;
        if (b.wornBag() != null) out.put("SAC", b.wornBag().clone());
        b.equipped().forEach((k, v) -> { if (!isEmpty(v)) out.put("EQ:" + k.name(), v.clone()); });
        return out;
    }

    private boolean unequipCos(Player p, RpBag b, CosmeticSlot cs) {
        ItemStack it = b.equipped().remove(cs);
        if (it == null) return false;
        giveOrDrop(p, b, it);
        return true;
    }

    // ─────────── Arme (étui) ───────────
    /** Range une arme de la barre ou du sac dans l'étui (l'ancienne revient dans la barre / le sac). */
    private boolean equipArme(Player p, RpBag b, String ref) {
        ItemStack it = refGet(p, b, ref);
        if (isEmpty(it)) return false;
        if (!RareteItems.arme(it)) {
            Notifs.msg(p, "refus", "刀", "Pas une arme", "l'étui ne prend qu'une arme");
            return false;
        }
        ItemStack one = sansDegainee(it.clone()); one.setAmount(1);
        ItemStack rem = it.clone(); rem.setAmount(it.getAmount() - 1);
        refSet(p, b, ref, rem.getAmount() <= 0 ? null : rem);
        ItemStack prev = b.equipped().put(CosmeticSlot.ARME, one);
        if (prev != null) giveOrDrop(p, b, prev);
        JournalObjets.noter(plugin, p, "ARME_ETUI", one, 1, p.getLocation(), null);
        return true;
    }

    /**
     * Dégainer / rengainer (touche du mod, X par défaut). Arme à l'étui → elle passe dans la case tenue (ou la
     * première case libre de la barre, qui devient la case tenue). Sinon l'arme dégainée (ou l'arme tenue) retourne à
     * l'étui.
     */
    private boolean basculerArme(Player p, RpBag b) {
        PlayerInventory inv = p.getInventory();
        ItemStack etui = b.equipped().get(CosmeticSlot.ARME);
        if (etui != null) {
            int slot = inv.getHeldItemSlot();
            if (!isEmpty(inv.getItem(slot))) {
                slot = -1;
                for (int i = 0; i < RpBag.HOTBAR_SLOTS; i++) if (isEmpty(inv.getItem(i))) { slot = i; break; }
            }
            if (slot < 0) {
                Notifs.msg(p, "refus", "刀", "Mains pleines", "libère une case de la barre");
                return false;
            }
            b.equipped().remove(CosmeticSlot.ARME);
            inv.setItem(slot, avecDegainee(etui));
            inv.setHeldItemSlot(slot);
            Notifs.arme(p, true);
            return true;
        }
        int slot = -1;
        for (int i = 0; i < RpBag.HOTBAR_SLOTS; i++) if (degainee(inv.getItem(i))) { slot = i; break; }
        if (slot < 0 && RareteItems.arme(inv.getItem(inv.getHeldItemSlot()))) slot = inv.getHeldItemSlot();
        if (slot < 0) {
            Notifs.msg(p, "refus", "刀", "Aucune arme", "à dégainer ni à rengainer");
            return false;
        }
        ItemStack arme = inv.getItem(slot);
        ItemStack one = sansDegainee(arme.clone()); one.setAmount(1);
        ItemStack rem = arme.clone(); rem.setAmount(arme.getAmount() - 1);
        inv.setItem(slot, rem.getAmount() <= 0 ? null : rem);
        b.equipped().put(CosmeticSlot.ARME, one);
        Notifs.arme(p, false);
        return true;
    }

    private static boolean degainee(ItemStack s) {
        return !isEmpty(s) && s.hasItemMeta()
            && s.getItemMeta().getPersistentDataContainer().has(DEGAINEE, PersistentDataType.BYTE);
    }

    private static ItemStack avecDegainee(ItemStack s) {
        ItemStack c = s.clone();
        ItemMeta m = c.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(DEGAINEE, PersistentDataType.BYTE, (byte) 1);
            c.setItemMeta(m);
        }
        return c;
    }

    private static ItemStack sansDegainee(ItemStack s) {
        if (!degainee(s)) return s;
        ItemMeta m = s.getItemMeta();
        m.getPersistentDataContainer().remove(DEGAINEE);
        s.setItemMeta(m);
        return s;
    }

    @Override
    public ItemStack weapon(Player p) {
        RpBag b = bagFor(p);
        if (b == null) return null;
        ItemStack etui = b.equipped().get(CosmeticSlot.ARME);
        if (etui != null) return etui.clone();
        for (int i = 0; i < RpBag.HOTBAR_SLOTS; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (degainee(s)) return s.clone();
        }
        // Une arme simplement tenue en main compte aussi (objet jamais passé par l'étui).
        ItemStack main = p.getInventory().getItemInMainHand();
        return RareteItems.arme(main) ? main.clone() : null;
    }

    @Override
    public boolean weaponDrawn(Player p) {
        RpBag b = bagFor(p);
        if (b == null || b.equipped().get(CosmeticSlot.ARME) != null) return false;
        for (int i = 0; i < RpBag.HOTBAR_SLOTS; i++) if (degainee(p.getInventory().getItem(i))) return true;
        return false;
    }

    // ─────────── Action contextuelle (use) ───────────
    /** Déclenche l'action propre à l'item (ex. test de la feuille). */
    private void doUse(Player p, RpBag b, String ref) {
        ItemStack it = refGet(p, b, ref);
        if (isEmpty(it)) return;
        if (LeafTestItem.isLeafTest(plugin, it)) {
            plugin.leafTest().beginConfirm(p); // ouvre le popup de confirmation client
        }
    }

    // ─────────── Suppression ───────────
    private boolean doDelete(Player p, RpBag b, String ref) {
        ItemStack it = refGet(p, b, ref);
        if (isEmpty(it)) return false;
        JournalObjets.noter(plugin, p, "DETRUIT", it, it.getAmount(), p.getLocation(), null);
        refSet(p, b, ref, null);
        return true;
    }

    // ─────────── Drop / donner ───────────
    private boolean doDrop(Player p, RpBag b, String ref) {
        ItemStack it = refGet(p, b, ref);
        if (isEmpty(it)) return false;
        // Monnaie (15/10) : la bourse se pose avec un montant (« Combien veux-tu poser ? ») ; ici, toute la bourse.
        if (com.reborn.shinobicore.monnaie.Bourse.est(it) && plugin.monnaie() != null) {
            plugin.monnaie().bourses().poser(p, com.reborn.shinobicore.monnaie.Bourse.montant(it));
            return false;
        }
        ItemStack lache = sansDegainee(it.clone());
        Item e = p.getWorld().dropItemNaturally(p.getLocation(), lache);
        e.setThrower(p.getUniqueId());
        refSet(p, b, ref, null);
        Notifs.objet(p, false, lache, lache.getAmount(), "lâché" + (lache.getAmount() > 1 ? "s" : ""));
        JournalObjets.noter(plugin, p, "LACHE", lache, lache.getAmount(), p.getLocation(), null);
        return true;
    }

    // ─────────── Événements ───────────
    /** Touche « jeter » de Minecraft (Q) depuis la barre : interdite en lecture seule, journalisée sinon. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        // Créatif compris (15/10) : notification et journal comme en survie.
        if (p.getGameMode() == GameMode.SPECTATOR || activeCharId(p) == null) return;
        if (lockReason(p) != null) {
            e.setCancelled(true);
            return;
        }
        ItemStack s = e.getItemDrop().getItemStack();
        if (degainee(s)) e.getItemDrop().setItemStack(sansDegainee(s.clone()));
        Notifs.objet(p, false, s, s.getAmount(), "lâché" + (s.getAmount() > 1 ? "s" : ""));
        JournalObjets.noter(plugin, p, "LACHE", s, s.getAmount(), p.getLocation(), null);
        Bukkit.getScheduler().runTask(plugin, () -> recalc(p));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSprint(PlayerToggleSprintEvent e) {
        Player p = e.getPlayer();
        if (surcharge.palier(p.getUniqueId()) >= CHARGE) Bukkit.getScheduler().runTask(plugin, () -> recalc(p));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitch(CharacterSwitchEvent e) {
        Player p = e.player();
        // L'événement part AVANT l'échange d'inventaire : on range et on recalcule une fois le perso en place.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            dernierEtat.remove(p.getUniqueId());
            if (survie(p) && bagFor(p) != null) rangerCasesCachees(p);
            recalc(p);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (survie(p) && bagFor(p) != null) rangerCasesCachees(p);
            recalc(p);
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        surcharge.retirer(p);
        dernierEtat.remove(p.getUniqueId());
        if (ramassage != null) ramassage.oublier(p.getUniqueId());
    }

    // ─────────── Cases cachées 9-35 (faille) ───────────
    /**
     * Les cases 9-35 de l'inventaire Minecraft ne sont pas affichées par la sacoche (elle ne montre que la barre et le
     * sac) : un objet y tombait (give, plugin…) devenait invisible et sans poids. On le range : barre, sac, sinon au
     * sol. Créatif / spectateur exclus (mode construction du staff).
     */
    public void rangerCasesCachees(Player p) {
        RpBag b = bagFor(p);
        if (b == null) return;
        PlayerInventory inv = p.getInventory();
        int ranges = 0;
        for (int i = RpBag.HOTBAR_SLOTS; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (isEmpty(s)) continue;
            inv.setItem(i, null);
            JournalObjets.noter(plugin, p, "RANGE", s, s.getAmount(), p.getLocation(), "case cachée " + i);
            giveOrDrop(p, b, s);
            ranges++;
        }
        if (ranges > 0) {
            persist(p, b);
            Notifs.msg(p, "info", "袋", ranges + " objet" + (ranges > 1 ? "s rangés" : " rangé"), "dans ta sacoche");
        }
    }

    // ─────────── API pour les autres plugins ───────────

    /** Poids porté par le personnage actif (barre d'action + sac + cosmétiques + étui), en kg. */
    public double weight(Player p) {
        RpBag b = bagFor(p);
        return b == null ? hotbarWeight(p) : hotbarWeight(p) + b.storageWeight();
    }

    /** Poids maximum du personnage actif (base + sac porté), en kg. */
    public double maxWeight(Player p) {
        RpBag b = bagFor(p);
        return b == null ? RpBag.BASE_WEIGHT : b.maxWeight();
    }

    /**
     * Donne un objet : barre d'action (fusion des piles), puis sac, puis au sol ; jamais dans les cases cachées. La
     * sacoche ouverte est rafraîchie (sans l'ouvrir), une notification « +n Objet » s'affiche.
     */
    @Override
    public void give(Player p, ItemStack s) {
        if (p == null || isEmpty(s)) return;
        RpBag b = bagFor(p);
        if (b == null) {
            p.getInventory().addItem(s).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
            return;
        }
        int q = s.getAmount();
        int reste = ranger(p, b, s.clone());
        if (reste > 0) {
            ItemStack r = s.clone();
            r.setAmount(reste);
            p.getWorld().dropItemNaturally(p.getLocation(), r);
        }
        persist(p, b);
        p.updateInventory();
        push(p, false);
        Notifs.objet(p, true, s, q, reste > 0 ? reste + " au sol (plus de place)" : null);
        JournalObjets.noter(plugin, p, "DONNE", s, q, p.getLocation(), null);
        recalc(p);
    }

    /** Raccourci statique pour les appelants du Core : {@code give} sur la sacoche, sinon inventaire vanilla. */
    public static void donner(Player p, ItemStack s) {
        ShinobiCore core = ShinobiCore.get();
        InventoryManager inv = core != null ? core.rpInventory() : null;
        if (inv != null && inv.dir != null) inv.give(p, s);
        else if (p != null && s != null) {
            p.getInventory().addItem(s).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
        }
    }

    /**
     * Nourriture (14/10) : peut-il recevoir cette pile de la main à la main ? Mêmes règles que le ramassage (place,
     * poids jusqu'à {@code inventaire.ramassage.poids-max-pourcent}). {@code null} = oui, sinon la raison courte.
     */
    public String refusReception(Player p, ItemStack s) {
        if (p == null || isEmpty(s)) return "rien à prendre";
        RpBag b = bagFor(p);
        if (b == null) return p.getInventory().firstEmpty() < 0 ? "plus de place" : null;
        ItemStack unite = RareteItems.normaliser(s.clone());
        unite.setAmount(1);
        if (placePour(p, b, unite, s.getAmount()) < s.getAmount()) return "plus de place";
        double max = b.maxWeight() * InventaireConfig.get(plugin).poidsMaxRatio;
        if (max > 0 && weight(p) + RpWeights.of(s) > max + 1e-6) return "trop lourd";
        return null;
    }

    /** Nourriture (14/10) : range une pile reçue (barre puis sac), sans notification ni journal. Renvoie le reste. */
    public int recevoir(Player p, ItemStack s) {
        RpBag b = bagFor(p);
        if (b == null) return p.getInventory().addItem(s).values().stream().mapToInt(ItemStack::getAmount).sum();
        int reste = ranger(p, b, s.clone());
        persist(p, b);
        p.updateInventory();
        push(p, false);
        recalc(p);
        return reste;
    }

    /**
     * Stockage du sac porté (hors barre d'action) du personnage actif, case par case (null = vide). Copies : pour
     * retirer un objet, {@link #takeFromBag}. Sert aux bibliothèques (dépôt d'un rouleau rangé dans le sac).
     */
    public List<ItemStack> bagStorage(Player p) {
        List<ItemStack> out = new ArrayList<>();
        RpBag b = bagFor(p);
        if (b == null) return out;
        for (int i = 0; i < b.extraSlots(); i++) {
            ItemStack s = b.getStorage(i);
            out.add(s == null ? null : s.clone());
        }
        return out;
    }

    /** Retire et rend l'objet de la case {@code i} du stockage du sac (null si vide) ; persiste et rafraîchit. */
    public ItemStack takeFromBag(Player p, int i) {
        RpBag b = bagFor(p);
        if (b == null || i < 0 || i >= b.extraSlots()) return null;
        ItemStack s = b.getStorage(i);
        if (s == null) return null;
        b.setStorage(i, null);
        persist(p, b);
        push(p, false);
        recalc(p);
        return s;
    }

    /** Monnaie (15/10) : pose {@code s} (copie, ou null pour vider) dans la case {@code i} du sac ; persiste et pousse. */
    public void poserDansSac(Player p, int i, ItemStack s) {
        RpBag b = bagFor(p);
        if (b == null || i < 0 || i >= b.extraSlots()) return;
        b.setStorage(i, isEmpty(s) ? null : s.clone());
        persist(p, b);
    }

    /** Monnaie (15/10, fouille d'un corps dont le joueur est parti) : sac d'un personnage non joué, en copies. */
    public List<ItemStack> sacDuPersonnage(UUID charId) {
        List<ItemStack> out = new ArrayList<>();
        if (charId == null || dir == null) return out;
        RpBag b = cache.computeIfAbsent(charId, this::loadOrSeed);
        for (int i = 0; i < b.extraSlots(); i++) {
            ItemStack s = b.getStorage(i);
            out.add(s == null ? null : s.clone());
        }
        return out;
    }

    /** Monnaie (15/10) : écrit la case {@code i} du sac d'un personnage non joué et sauvegarde. */
    public void poserDansSacDe(UUID charId, int i, ItemStack s) {
        if (charId == null || dir == null) return;
        RpBag b = cache.computeIfAbsent(charId, this::loadOrSeed);
        if (i < 0 || i >= b.extraSlots()) return;
        b.setStorage(i, isEmpty(s) ? null : s.clone());
        saveBag(charId, b);
    }

    /** Monnaie (15/10) : le JSON d'objet de la sacoche (mêmes champs que le canal reborn:inventory). */
    public String jsonObjet(ItemStack s) { return itemJson(s); }

    /** Rend un item : barre (fusion des piles comprise), sinon sac, sinon au sol. */
    private void giveOrDrop(Player p, RpBag b, ItemStack s) {
        if (isEmpty(s)) return;
        int reste = ranger(p, b, s);
        if (reste > 0) {
            ItemStack r = s.clone();
            r.setAmount(reste);
            p.getWorld().dropItemNaturally(p.getLocation(), r);
        }
    }

    // ─────────── Commande /sacoche (confort + test) ───────────
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            push(p, true);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        RpBag b = bagFor(p);
        if (b == null) {
            p.sendMessage("§7[Sacoche] §fAucun personnage actif.");
            return true;
        }
        switch (sub) {
            case "bag" -> {
                if (args.length < 2) {
                    p.sendMessage("§7[Sacoche] §fUsage : /sacoche bag <sac_sacoche|sac_bandouliere|sac_dos|sac_lourd|none>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("none")) {
                    if (unequipBag(p, b)) p.sendMessage("§7[Sacoche] §fSac retiré (capacité de base).");
                } else {
                    BagTier t = BagTier.fromItemId(args[1].toLowerCase(Locale.ROOT));
                    if (t == null) t = BagTier.fromName(args[1]);
                    if (t == null || t == BagTier.NONE) {
                        p.sendMessage("§7[Sacoche] §cSac inconnu : " + args[1]);
                        return true;
                    }
                    List<ItemStack> overflow = b.setTier(t);
                    for (ItemStack o : overflow) giveOrDrop(p, b, o);
                    p.sendMessage("§7[Sacoche] §f" + t.displayName + " équipé — " + t.extraSlots + " cases de sac.");
                }
                persist(p, b);
                push(p, true);
                recalc(p);
            }
            case "give" -> {
                if (args.length < 2) {
                    p.sendMessage("§7[Sacoche] §fUsage : /sacoche give <material> [quantité] (ajoute dans le sac)");
                    return true;
                }
                Material mat = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
                if (mat == null || mat.isAir()) {
                    p.sendMessage("§7[Sacoche] §cMatériau inconnu : " + args[1]);
                    return true;
                }
                int count = 1;
                if (args.length >= 3) {
                    try { count = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException ignored) { }
                }
                ItemStack s = new ItemStack(mat, count);
                boolean placed = false;
                for (int i = 0; i < b.extraSlots(); i++) {
                    if (b.getStorage(i) == null) { b.setStorage(i, s); placed = true; break; }
                }
                if (!placed) {
                    p.sendMessage("§7[Sacoche] §c" + (b.extraSlots() == 0 ? "Aucun sac équipé." : "Sac plein."));
                    return true;
                }
                persist(p, b);
                push(p, true);
                recalc(p);
                p.sendMessage("§7[Sacoche] §f+" + count + " " + mat.name().toLowerCase(Locale.ROOT) + " dans le sac.");
            }
            case "reset" -> {
                UUID cid = activeCharId(p);
                if (cid != null) {
                    cache.remove(cid);
                    File f = new File(dir, cid + ".yml");
                    if (f.exists() && !f.delete()) {
                        plugin.getLogger().warning("[sacoche] Suppression échouée : " + f.getName());
                    }
                    bagFor(p); // re-seed (NONE, vide)
                    push(p, true);
                    recalc(p);
                    p.sendMessage("§7[Sacoche] §fSac réinitialisé (sans sac).");
                }
            }
            default -> p.sendMessage("§7[Sacoche] §fSous-commandes : open, bag <type|none>, give <material> [n], reset");
        }
        return true;
    }

    /** Sauvegarde tous les sacs en cache (appelé à l'extinction) et retire les modificateurs de surcharge. */
    public void flush() {
        for (Map.Entry<UUID, RpBag> e : cache.entrySet()) {
            saveBag(e.getKey(), e.getValue());
        }
        for (Player p : Bukkit.getOnlinePlayers()) surcharge.retirer(p);
    }
}
