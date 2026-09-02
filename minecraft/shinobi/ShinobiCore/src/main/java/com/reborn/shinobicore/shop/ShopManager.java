package com.reborn.shinobicore.shop;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/**
 * Boutique de TENUES (skins). Autoritaire côté serveur : monnaie <b>ryo</b> +
 * possession de tenues stockées sur {@link ShinobiCharacter} (persistées YAML).
 * L'écran client (mod-hud {@code ShopScreen}) parle sur le canal {@code reborn:shop} :
 * <ul>
 *   <li>C2S {@code open} → S2C état {@code {ryo, price, owned[], appearance}}</li>
 *   <li>C2S {@code buy:<id>} → validation (solde, pas déjà possédée) → débit + ajout</li>
 *   <li>C2S {@code equip:<id>\n<blob>} → si possédée → applique l'apparence + rediffuse</li>
 * </ul>
 * L'apparence (blob SkinSpec sérialisé) reste opaque côté serveur : le client la
 * modifie (outfitId) et renvoie le blob complet ; le serveur ne fait que garder +
 * rediffuser (le gate d'achat est sur l'{@code id}). Prix plat configurable
 * ({@code shop.tenue-price}).
 *
 * <p>Commande staff {@code /ryo [give|take|set] <joueur> <montant>} (sinon affiche
 * le solde) — donne de la monnaie en attendant les gains RP.
 */
public final class ShopManager implements PluginMessageListener, CommandExecutor {

    public static final String CHANNEL = "reborn:shop";

    private final ShinobiCore plugin;
    private long tenuePrice;

    public ShopManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL)) m.registerIncomingPluginChannel(plugin, CHANNEL, this);
        reloadConfig();
    }

    public void reloadConfig() {
        this.tenuePrice = Math.max(0L, plugin.getConfig().getLong("shop.tenue-price", 500L));
    }

    // ─────────────────────────── réseau (C2S) ───────────────────────────
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player p, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) return;
        String msg = new String(message, StandardCharsets.UTF_8);
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return; // pas de perso actif → pas de boutique
        if (msg.equals("open")) {
            sendState(p, c, null);
        } else if (msg.startsWith("buy:")) {
            handleBuy(p, c, msg.substring("buy:".length()).trim());
        } else if (msg.startsWith("equip:")) {
            String body = msg.substring("equip:".length());
            int nl = body.indexOf('\n');
            String id = (nl < 0 ? body : body.substring(0, nl)).trim();
            String blob = nl < 0 ? "" : body.substring(nl + 1);
            handleEquip(p, c, id, blob);
        }
    }

    private void handleBuy(Player p, ShinobiCharacter c, String id) {
        if (id.isEmpty()) return;
        if (c.ownsOutfit(id)) { sendState(p, c, "§eTu possèdes déjà cette tenue."); return; }
        if (!c.trySpendRyo(tenuePrice)) { sendState(p, c, "§cPas assez de ryo (" + tenuePrice + ")."); return; }
        c.addOwnedOutfit(id);
        plugin.characters().save(c);
        sendState(p, c, "§aTenue achetée ! §7(-" + tenuePrice + " ryo)");
    }

    private void handleEquip(Player p, ShinobiCharacter c, String id, String blob) {
        // id vide = « aucune tenue » (torse nu) toujours autorisé ; sinon il faut la posséder.
        if (!id.isEmpty() && !c.ownsOutfit(id)) { sendState(p, c, "§cTu ne possèdes pas cette tenue."); return; }
        if (blob != null && !blob.isBlank()) {
            c.setAppearance(blob);
            plugin.characters().save(c);
            if (plugin.characterSelect() != null) plugin.characterSelect().broadcastActive(p);
        }
        sendState(p, c, "§aTenue équipée.");
    }

    // ─────────────────────────── réseau (S2C) ───────────────────────────
    private void sendState(Player p, ShinobiCharacter c, String toast) {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"ryo\":").append(c.ryo()).append(",\"price\":").append(tenuePrice).append(",\"owned\":[");
        boolean first = true;
        for (String o : c.ownedOutfits()) { if (!first) sb.append(','); sb.append('"').append(esc(o)).append('"'); first = false; }
        sb.append("],\"appearance\":\"").append(esc(c.appearance())).append('"');
        if (toast != null) sb.append(",\"toast\":\"").append(esc(toast)).append('"');
        sb.append('}');
        try {
            p.sendPluginMessage(plugin, CHANNEL, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    // ─────────────────────────── /ryo ───────────────────────────
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player self)) { sender.sendMessage(Component.text("/ryo give <joueur> <montant>")); return true; }
            ShinobiCharacter c = plugin.characters().getActive(self.getUniqueId());
            self.sendMessage(Component.text("Solde : " + (c == null ? 0 : c.ryo()) + " ryo",
                    c == null ? NamedTextColor.GRAY : NamedTextColor.GOLD));
            return true;
        }
        String op = args[0].toLowerCase();
        if (op.equals("give") || op.equals("take") || op.equals("set")) {
            if (!sender.hasPermission("shinobicore.ryo.admin")) {
                sender.sendMessage(Component.text("Permission refusée.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) { sender.sendMessage(Component.text("/ryo " + op + " <joueur> <montant>", NamedTextColor.YELLOW)); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(Component.text("Joueur introuvable : " + args[1], NamedTextColor.RED)); return true; }
            long amount;
            try { amount = Long.parseLong(args[2]); } catch (NumberFormatException e) { sender.sendMessage(Component.text("Montant invalide.", NamedTextColor.RED)); return true; }
            ShinobiCharacter c = plugin.characters().getActive(target.getUniqueId());
            if (c == null) { sender.sendMessage(Component.text(target.getName() + " n'a pas de personnage actif.", NamedTextColor.RED)); return true; }
            switch (op) {
                case "give" -> c.addRyo(amount);
                case "take" -> c.setRyo(c.ryo() - amount);
                case "set"  -> c.setRyo(amount);
            }
            plugin.characters().save(c);
            sendState(target, c, "§6Ryo : " + c.ryo());
            sender.sendMessage(Component.text(target.getName() + " → " + c.ryo() + " ryo.", NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("Usage : /ryo [give|take|set] <joueur> <montant>", NamedTextColor.YELLOW));
        return true;
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default   -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        return b.toString();
    }
}
