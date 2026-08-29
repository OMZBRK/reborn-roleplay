package com.reborn.shinobicore.inventory;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.LeafTestItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
 * <b>Sacoche RP</b> — inventaire custom <b>lié au personnage actif</b>, modèle
 * « base = hotbar vanilla ». Les 9 cases de base affichées dans la Sacoche sont
 * la <b>vraie barre d'action</b> du joueur ; ce manager n'est autoritaire que
 * pour l'<b>espace supplémentaire</b> débloqué par un sac ({@link RpBag}), le
 * sac porté et les cosmétiques — persistés par perso
 * ({@code plugins/ShinobiCore/rpbags/<charUuid>.yml}), en vrais {@link ItemStack}
 * sérialisés (base64).
 *
 * <p>Canal {@code reborn:inventory}. C2S : {@code open}, {@code swap:<A>:<B>}
 * (A/B = {@code H0..H8} hotbar ou {@code B0..Bn} sac), {@code bag:equip:<ref>} /
 * {@code bag:unequip}, {@code cos:equip:<ref>:<SLOT>} / {@code cos:unequip:<SLOT>},
 * {@code drop:<ref>}. S2C : JSON décrivant la capacité + le contenu du sac (les
 * items sont envoyés comme {mat, model, count, name, weight} — le client
 * reconstruit un ItemStack et le rend via le resource pack).
 */
public class InventoryManager implements Listener, PluginMessageListener, CommandExecutor {

    public static final String CHANNEL = "reborn:inventory";
    /** Canal S2C de diffusion des cosmétiques équipés (uuid + json slot->{mat,model}). */
    public static final String COSMETICS_CHANNEL = "reborn:cosmetics";
    private static final int SAVE_FORMAT = 2;

    private final ShinobiCore plugin;
    private final Map<UUID, RpBag> cache = new ConcurrentHashMap<>();
    private File dir;

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
        dir = new File(plugin.getDataFolder(), "rpbags");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[sacoche] Impossible de créer le dossier rpbags.");
        }
        // Surcharge : vérifie le poids porté chaque seconde et applique le malus.
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickWeights, 40L, 20L);
        plugin.getLogger().info("[sacoche] Inventaire RP prêt (canal " + CHANNEL + ", base = hotbar).");
    }

    // ─────────── Surcharge (poids > capacité → ralentissement/fatigue) ───────────
    /**
     * Applique une pénalité proportionnelle au dépassement de la capacité de poids
     * (ralentissement, puis fatigue de minage, puis faiblesse en cas de très forte
     * surcharge = quasi-immobilisation). Rafraîchi chaque seconde ; les effets ont une
     * durée courte et s'estompent d'eux-mêmes dès qu'on repasse sous la limite.
     */
    private void tickWeights() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            GameMode gm = p.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
            UUID cid = activeCharId(p);
            if (cid == null) continue;
            RpBag b = cache.get(cid); // pas de seed ici : uniquement les persos déjà chargés
            if (b == null) continue;
            double max = b.maxWeight();
            if (max <= 0) continue;
            double cur = hotbarWeight(p) + b.storageWeight();
            if (cur <= max) continue; // sous la limite : aucun malus
            double ratio = cur / max;

            int slow, fatigue = -1, weak = -1;
            if (ratio <= 1.25)      { slow = 0; }
            else if (ratio <= 1.5)  { slow = 1; }
            else if (ratio <= 2.0)  { slow = 2; fatigue = 0; }
            else if (ratio <= 3.0)  { slow = 3; fatigue = 1; }
            else                    { slow = 5; fatigue = 2; weak = 0; } // surcharge extrême = cloué sur place

            applyMalus(p, PotionEffectType.SLOWNESS, slow);
            if (fatigue >= 0) applyMalus(p, PotionEffectType.MINING_FATIGUE, fatigue);
            if (weak >= 0)    applyMalus(p, PotionEffectType.WEAKNESS, weak);

            p.sendActionBar(Component.text(
                "Surcharge — " + round2(cur) + " / " + round2(max) + " kg — tu es ralenti",
                NamedTextColor.RED));
        }
    }

    private void applyMalus(Player p, PotionEffectType type, int amp) {
        // Durée courte rafraîchie chaque seconde (ambient, sans particules ni icône HUD) :
        // s'estompe seul quand on n'est plus en surcharge. force=true = refresh garanti.
        p.addPotionEffect(new PotionEffect(type, 45, amp, true, false, false), true);
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
    public void push(Player p) {
        RpBag b = bagFor(p);
        if (b == null) {
            p.sendMessage("§7[Sacoche] §fAucun personnage actif — impossible d'ouvrir le sac.");
            return;
        }
        byte[] bytes = buildJson(p, b).getBytes(StandardCharsets.UTF_8);
        try {
            p.sendPluginMessage(plugin, CHANNEL, bytes);
        } catch (Exception ignored) {
            // canal non enregistré côté client (pas de mod-hud) → ignoré.
        }
    }

    private String buildJson(Player p, RpBag b) {
        double cur = hotbarWeight(p) + b.storageWeight();
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"bagTier\":\"").append(b.bagTier().name()).append("\",");
        sb.append("\"baseSlots\":").append(RpBag.HOTBAR_SLOTS).append(',');
        sb.append("\"extraSlots\":").append(b.extraSlots()).append(',');
        sb.append("\"maxWeight\":").append(round2(b.maxWeight())).append(',');
        sb.append("\"curWeight\":").append(round2(cur)).append(',');
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

    /** Sérialise un item pour le rendu client : {mat, model?, count, name?, weight}. */
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
        if (leaf) {
            b.append(",\"rarity\":\"Rare\"");
            b.append(",\"action\":{\"id\":\"tirage\",\"label\":\"Faire le test\"}");
        }
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
            push(p);
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
                    if (cs != null) changed = equipCos(p, b, parts[2], cs);
                } else if (parts.length >= 3 && parts[1].equals("unequip")) {
                    CosmeticSlot cs = CosmeticSlot.fromName(parts[2]);
                    if (cs != null) changed = unequipCos(p, b, cs);
                }
                // Un cosmétique a changé → rediffuse à tous pour une MAJ live
                // (visible entre joueurs sans reconnexion).
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
        if (changed) persist(p, b);
        if (touched) {
            p.updateInventory();
            push(p);
        }
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
            b.setStorage(i, s);
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
        return true;
    }

    private boolean unequipBag(Player p, RpBag b) {
        if (b.bagTier() == BagTier.NONE) return false;
        for (ItemStack s : b.storage()) {
            if (!isEmpty(s)) {
                p.sendMessage("§7[Sacoche] §cVide d'abord ton sac avant de le retirer.");
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

    private boolean unequipCos(Player p, RpBag b, CosmeticSlot cs) {
        ItemStack it = b.equipped().remove(cs);
        if (it == null) return false;
        giveOrDrop(p, b, it);
        return true;
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
        refSet(p, b, ref, null);
        return true;
    }

    // ─────────── Drop / donner ───────────
    private boolean doDrop(Player p, RpBag b, String ref) {
        ItemStack it = refGet(p, b, ref);
        if (isEmpty(it)) return false;
        p.getWorld().dropItemNaturally(p.getLocation(), it.clone());
        refSet(p, b, ref, null);
        return true;
    }

    /** Rend un item : 1re case libre du hotbar, sinon du stockage, sinon au sol. */
    private void giveOrDrop(Player p, RpBag b, ItemStack s) {
        if (isEmpty(s)) return;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < RpBag.HOTBAR_SLOTS; i++) {
            if (isEmpty(inv.getItem(i))) { inv.setItem(i, s); return; }
        }
        for (int i = 0; i < b.extraSlots(); i++) {
            if (b.getStorage(i) == null) { b.setStorage(i, s); return; }
        }
        p.getWorld().dropItemNaturally(p.getLocation(), s);
    }

    // ─────────── Commande /sacoche (confort + test) ───────────
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            push(p);
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
                push(p);
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
                push(p);
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
                    push(p);
                    p.sendMessage("§7[Sacoche] §fSac réinitialisé (sans sac).");
                }
            }
            default -> p.sendMessage("§7[Sacoche] §fSous-commandes : open, bag <type|none>, give <material> [n], reset");
        }
        return true;
    }

    /** Sauvegarde tous les sacs en cache (appelé à l'extinction). */
    public void flush() {
        for (Map.Entry<UUID, RpBag> e : cache.entrySet()) {
            saveBag(e.getKey(), e.getValue());
        }
    }
}
