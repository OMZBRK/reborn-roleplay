package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestration du <b>test de la feuille</b> (serveur autoritaire).
 *
 * <p>Flux : item/commande → {@link #beginConfirm} envoie {@code confirm|<clan>}
 * au client (popup Confirmer/Refuser) → le client répond {@code go} → {@link
 * #onPluginMessageReceived} lance {@link #runTest} : tirage <b>pondéré par le
 * clan</b> (Uchiha→Katon…), attribution ({@code addChakraAffinity}+save, cadenas
 * niveau 6/12, max 3), effet in-world (particules + son, vu par tous), puis envoi
 * du résultat {@code <NATURE>|<clan>} qui ouvre le popup animé du mod.
 */
public final class LeafTestManager implements Listener, PluginMessageListener {

    public static final String CHANNEL = "reborn:tirage";
    private static final int LEVEL_GATE_SECOND = 6;
    private static final int LEVEL_GATE_THIRD = 12;

    private final ShinobiCore plugin;

    public LeafTestManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var m = plugin.getServer().getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            m.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL)) {
            m.registerIncomingPluginChannel(plugin, CHANNEL, this);
        }
    }

    // ── Item : clic droit → confirmation ──────────────────────────────
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (!LeafTestItem.isLeafTest(plugin, e.getItem())) return;
        e.setCancelled(true);
        beginConfirm(e.getPlayer());
    }

    // ── Client → serveur : "go" (Confirmer) ───────────────────────────
    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        String msg = new String(message, StandardCharsets.UTF_8).trim();
        if (msg.equals("go")) {
            if (runTest(p)) consumeOne(p);
            else send(p, "deny");   // devenu inéligible → le client ferme l'écran
        }
    }

    // ── API ──────────────────────────────────────────────────────────
    public void giveItem(Player p) {
        p.getInventory().addItem(LeafTestItem.create(plugin));
        p.sendMessage(Component.text("Tu reçois une Feuille de test.", NamedTextColor.GREEN));
    }

    /** Ouvre le popup de confirmation client si le joueur est éligible. */
    public boolean beginConfirm(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) {
            p.sendMessage(Component.text("Aucun personnage actif.", NamedTextColor.RED));
            return false;
        }
        if (!eligible(p, c)) return false;
        String clan = c.clan() == null ? "" : c.clan();
        send(p, "confirm|" + clan);
        return true;
    }

    /** Exécute réellement le test : tire, attribue, effet, envoie le résultat. */
    public boolean runTest(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) {
            p.sendMessage(Component.text("Aucun personnage actif.", NamedTextColor.RED));
            return false;
        }
        if (!eligible(p, c)) return false;

        ChakraAffinity picked = rollWeighted(c.clan(), pool(c));
        if (picked == null || !c.addChakraAffinity(picked)) {
            p.sendMessage(Component.text("La feuille reste inerte...", NamedTextColor.RED));
            return false;
        }
        plugin.characters().save(c);

        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.3f);
        elementBurst(p, picked);

        send(p, picked.name() + "|" + (c.clan() == null ? "" : c.clan()));
        p.sendMessage(Component.text("La feuille réagit — affinité " + picked.displayName() + " éveillée.",
                NamedTextColor.GREEN));
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private boolean eligible(Player p, ShinobiCharacter c) {
        int owned = c.chakraAffinities().size();
        if (owned >= ShinobiCharacter.MAX_CHAKRA_AFFINITIES || pool(c).isEmpty()) {
            p.sendMessage(Component.text("Tes affinités sont déjà éveillées.", NamedTextColor.YELLOW));
            return false;
        }
        if (owned == 1 && c.level() < LEVEL_GATE_SECOND) {
            p.sendMessage(Component.text("La deuxième affinité s'éveille au niveau " + LEVEL_GATE_SECOND + ".",
                    NamedTextColor.RED));
            return false;
        }
        if (owned == 2 && c.level() < LEVEL_GATE_THIRD) {
            p.sendMessage(Component.text("La troisième affinité s'éveille au niveau " + LEVEL_GATE_THIRD + ".",
                    NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private List<ChakraAffinity> pool(ShinobiCharacter c) {
        List<ChakraAffinity> pool = new ArrayList<>();
        for (ChakraAffinity a : ChakraAffinity.ROLLABLE) {
            if (!c.hasChakraAffinity(a)) pool.add(a);
        }
        return pool;
    }

    /** 1/5 pondéré : base 20, +40 sur l'affinité du clan (fidèle à l'animé). */
    private ChakraAffinity rollWeighted(String clan, List<ChakraAffinity> pool) {
        if (pool.isEmpty()) return null;
        ChakraAffinity fav = clanFavored(clan);
        double[] w = new double[pool.size()];
        double total = 0;
        for (int i = 0; i < pool.size(); i++) {
            w[i] = 20.0 + (fav != null && pool.get(i) == fav ? 40.0 : 0.0);
            total += w[i];
        }
        double x = ThreadLocalRandom.current().nextDouble() * total, acc = 0;
        for (int i = 0; i < pool.size(); i++) {
            acc += w[i];
            if (x < acc) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    private ChakraAffinity clanFavored(String clan) {
        if (clan == null) return null;
        String c = clan.toLowerCase();
        if (c.contains("uchiha") || c.contains("sarutobi")) return ChakraAffinity.KATON;
        if (c.contains("hozuki") || c.contains("hōzuki") || c.contains("senju")) return ChakraAffinity.SUITON;
        if (c.contains("yotsuki") || c.contains("kaminari") || c.contains("raikage")) return ChakraAffinity.RAITON;
        if (c.contains("kaguya") || c.contains("sabaku") || c.contains("kazekage")) return ChakraAffinity.FUTON;
        if (c.contains("kamizuru") || c.contains("iwa") || c.contains("onoki")) return ChakraAffinity.DOTON;
        return null;
    }

    private void elementBurst(Player p, ChakraAffinity a) {
        Location l = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.8));
        Particle particle = switch (a) {
            case KATON  -> Particle.FLAME;
            case SUITON -> Particle.SPLASH;
            case RAITON -> Particle.ELECTRIC_SPARK;
            case FUTON  -> Particle.CLOUD;
            case DOTON  -> Particle.LARGE_SMOKE;
            default     -> Particle.ENCHANT;
        };
        p.getWorld().spawnParticle(particle, l, 24, 0.25, 0.35, 0.25, 0.02);
    }

    /** Retire une Feuille de test de la main (si présente). */
    private void consumeOne(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (LeafTestItem.isLeafTest(plugin, hand)) {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    private void send(Player p, String content) {
        try {
            p.sendPluginMessage(plugin, CHANNEL, content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Client sans le mod → pas de canal ; on ignore.
        }
    }
}
