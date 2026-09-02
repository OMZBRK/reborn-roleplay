package com.reborn.shinobicore.character.select;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.Clan;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sélection de personnage « façon Zenkai » pilotée par le mod client (mod-hud).
 *
 * <p>À la connexion (roster non vide, aucun perso actif) : téléporte au build de
 * sélection, fige le joueur (VISIBLE — son corps sert de perso en 3e personne
 * côté client), cache les autres joueurs, et envoie sa liste de personnages sur
 * le canal {@code reborn:character}. Le mod affiche l'overlay et renvoie
 * {@code select:<id>} / {@code create}.
 *
 * <p>L'ancre (build de sélection) est résolue <b>au moment du join</b> (config
 * relue à chaud) et <b>retombe sur la position actuelle du joueur</b> si le monde
 * configuré est absent — ainsi {@code enabled: true} suffit pour que ça marche,
 * même sans config parfaite (le build joli se règle après).
 */
public class CharacterSelectManager implements Listener, PluginMessageListener {

    public static final String CHANNEL = "reborn:character";
    /** Canal S2C de diffusion des apparences actives (uuid + queue serialize()). */
    public static final String SKINS_CHANNEL = "reborn:skins";

    private final ShinobiCore plugin;
    private boolean enabled;

    private final Map<UUID, SelectState> selecting = new HashMap<>();

    /** Candidature whitelist validée par joueur (village/clan lockés du wizard). */
    private final CandidatureClient candidatureClient;
    private final Map<UUID, CandidatureClient.Candidature> candidatures = new HashMap<>();

    public CharacterSelectManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.candidatureClient = new CandidatureClient(plugin);
    }

    /** État sauvegardé + ancre où figer le joueur. */
    private static final class SelectState {
        final boolean invulnerable;
        final Location anchor;
        SelectState(Player p, Location anchor) {
            this.invulnerable = p.isInvulnerable();
            this.anchor = anchor;
        }
    }

    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            m.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL)) {
            m.registerIncomingPluginChannel(plugin, CHANNEL, this);
        }
        if (!m.isOutgoingChannelRegistered(plugin, SKINS_CHANNEL)) {
            m.registerOutgoingPluginChannel(plugin, SKINS_CHANNEL);
        }
        reloadConfig();
        plugin.getLogger().info("[character-select] start — enabled=" + enabled);
    }

    public void reloadConfig() {
        this.enabled = plugin.getConfig().getBoolean("character-select.enabled", false);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isSelecting(UUID id) { return selecting.containsKey(id); }

    /**
     * Résout l'ancre au moment du join : monde config s'il existe, sinon la
     * position actuelle du joueur (fallback robuste). Config relue à chaud.
     */
    private Location resolveAnchor(Player p) {
        String worldName = plugin.getConfig().getString("character-select.world", "");
        World w = (worldName == null || worldName.isBlank()) ? null : Bukkit.getWorld(worldName);
        if (w == null) {
            plugin.getLogger().warning("[character-select] monde '" + worldName
                    + "' introuvable/vide → fallback sur la position actuelle de " + p.getName()
                    + " (mondes chargés : " + Bukkit.getWorlds().stream().map(World::getName).toList() + ")");
            return p.getLocation();
        }
        return new Location(w,
                plugin.getConfig().getDouble("character-select.x", 0.5),
                plugin.getConfig().getDouble("character-select.y", 100.0),
                plugin.getConfig().getDouble("character-select.z", 0.5),
                (float) plugin.getConfig().getDouble("character-select.yaw", 0.0),
                (float) plugin.getConfig().getDouble("character-select.pitch", 0.0));
    }

    /**
     * Démarre la sélection pour {@code p}. Retourne false si feature désactivée →
     * l'appelant retombe sur l'ancienne GUI. <b>Un roster vide est autorisé</b> :
     * le joueur ouvre l'écran avec la seule tuile « Créer » (sinon un nouveau
     * joueur sans perso n'aurait aucun accès au menu de création).
     */
    public boolean beginSelection(Player p) {
        if (!enabled) {
            plugin.getLogger().info("[character-select] " + p.getName()
                    + " : feature désactivée (enabled=false) → fallback GUI.");
            return false;
        }
        List<ShinobiCharacter> roster = plugin.characters().getAll(p.getUniqueId());

        // Déjà en sélection (réouverture via touche) : on renvoie juste le roster
        // sans re-figer/re-capturer l'état → évite le "stun" par double-capture.
        if (isSelecting(p.getUniqueId())) {
            sendRoster(p, roster);
            return true;
        }

        Location anchor = resolveAnchor(p);
        plugin.getLogger().info("[character-select] " + p.getName() + " : DÉMARRE sélection ("
                + roster.size() + " perso(s)), ancre=" + anchor.getWorld().getName() + " "
                + anchor.getBlockX() + "," + anchor.getBlockY() + "," + anchor.getBlockZ());

        selecting.put(p.getUniqueId(), new SelectState(p, anchor));
        p.setInvulnerable(true);
        // Pas de setWalkSpeed(0) : le gel se fait par l'épinglage onMove (setTo).
        // Toucher walkSpeed causait un "stun" si mal restauré après sélection.
        p.teleport(anchor);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            p.hideEntity(plugin, other);
            other.hideEntity(plugin, p);
        }
        // Candidature whitelist (village/clan lockés) — récupérée en async pour ne
        // pas bloquer le main thread ; un re-send suit dès qu'elle arrive.
        fetchCandidatureAsync(p);

        // Retries pour battre la course d'ouverture côté client.
        scheduleRosterSend(p, 5L);
        scheduleRosterSend(p, 20L);
        scheduleRosterSend(p, 40L);
        return true;
    }

    /**
     * Interroge l'API Reborn (async) pour la candidature validée du joueur, met en
     * cache, et re-pousse le roster (avec le bloc {@code candidature}) sur le main
     * thread. Silencieux en cas d'indisponibilité (grisage simplement absent).
     */
    private void fetchCandidatureAsync(Player p) {
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CandidatureClient.Candidature cand = candidatureClient.fetch(id);
            if (cand == null) return;
            candidatures.put(id, cand);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && isSelecting(id)) {
                    sendRoster(p, plugin.characters().getAll(id));
                }
            });
        });
    }

    private void scheduleRosterSend(Player p, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && isSelecting(p.getUniqueId())) {
                sendRoster(p, plugin.characters().getAll(p.getUniqueId()));
            }
        }, delayTicks);
    }

    private void endSelection(Player p) {
        SelectState st = selecting.remove(p.getUniqueId());
        if (st != null && st.invulnerable) {
            // NE PAS restaurer un invulnérable=true : le flag Invulnerable est
            // PERSISTÉ dans le NBT du joueur, donc une déco en plein select le
            // rend invulnérable en permanence (capturé true → restauré true) →
            // AUCUN EntityDamageEvent ne naît → PvP muet. Un joueur en jeu RP
            // n'est jamais invulnérable : on force donc OFF à la sortie de select.
            plugin.getLogger().warning("[character-select] " + p.getName()
                    + " avait Invulnerable=true en entrant en sélection (état persistant"
                    + " parasite) → forcé OFF pour ne pas bloquer les dégâts.");
        }
        p.setInvulnerable(false);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            p.showEntity(plugin, other);
            other.showEntity(plugin, p);
        }
    }

    // ── Envoi de la liste (S2C JSON) ─────────────────────────────
    private void sendRoster(Player p, List<ShinobiCharacter> roster) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"slotLimit\":").append(slotLimit(p)).append(",\"characters\":[");
        for (int i = 0; i < roster.size(); i++) {
            ShinobiCharacter c = roster.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"").append(c.id()).append('"');
            sb.append(",\"name\":\"").append(esc(c.name())).append('"');
            String clan = c.clan();
            if (clan != null && !clan.isBlank() && !"None".equalsIgnoreCase(clan)) {
                sb.append(",\"clan\":\"").append(esc(clan)).append('"');
                NamedTextColor col = Clan.colourFor(clan);
                if (col != null) sb.append(",\"clanColor\":").append(0xFF000000 | col.value());
            }
            if (!c.village().isBlank()) {
                sb.append(",\"village\":\"").append(esc(c.village())).append('"');
            }
            sb.append(",\"rank\":\"").append(esc(c.rank().displayName())).append('"');
            sb.append(",\"level\":").append(c.level());
            sb.append(",\"dead\":").append(c.dead());
            if (!c.appearance().isBlank()) {
                sb.append(",\"appearance\":\"").append(esc(c.appearance())).append('"');
            }
            sb.append('}');
        }
        sb.append(']');
        // Bloc candidature (village/clan validés à verrouiller côté wizard). Absent
        // si non récupéré → le mod ne verrouille rien (dégradation propre).
        CandidatureClient.Candidature cand = candidatures.get(p.getUniqueId());
        if (cand != null && (cand.found() || cand.staff())) {
            sb.append(",\"candidature\":{");
            boolean first = true;
            if (cand.village() != null && !cand.village().isBlank()) {
                sb.append("\"village\":\"").append(esc(cand.village())).append('"');
                first = false;
            }
            if (cand.clan() != null && !cand.clan().isBlank()) {
                if (!first) sb.append(',');
                sb.append("\"clan\":\"").append(esc(cand.clan())).append('"');
                first = false;
            }
            if (cand.name() != null && !cand.name().isBlank()) {
                if (!first) sb.append(',');
                sb.append("\"name\":\"").append(esc(cand.name())).append('"');
                first = false;
            }
            if (!first) sb.append(',');
            sb.append("\"staff\":").append(cand.staff());
            sb.append('}');
        }
        sb.append('}');
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        try {
            p.sendPluginMessage(plugin, CHANNEL, bytes);
            plugin.getLogger().info("[character-select] roster envoyé à " + p.getName()
                    + " (" + roster.size() + " perso(s), " + bytes.length + " o).");
        } catch (Exception e) {
            plugin.getLogger().warning("[character-select] échec envoi roster à "
                    + p.getName() + " : " + e.getMessage());
        }
    }

    /**
     * Diffuse l'apparence RP du perso actif de {@code p} à TOUS les joueurs (pour
     * qu'ils voient son skin composé) et envoie à {@code p} les apparences de tous
     * les autres joueurs actifs (pour qu'il voie les leurs). Message S2C
     * {@code reborn:skins} = {@code <uuid>\n<queue serialize()>} (apparence vide =
     * skin Minecraft normal / clear côté client).
     */
    public void broadcastActive(Player p) {
        if (!Bukkit.getMessenger().isOutgoingChannelRegistered(plugin, SKINS_CHANNEL)) return;
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        String app = c == null ? "" : c.appearance();
        byte[] mine = (p.getUniqueId() + "\n" + app).getBytes(StandardCharsets.UTF_8);
        for (Player other : Bukkit.getOnlinePlayers()) {
            sendSkin(other, mine);                       // p → tous
            if (other.equals(p) || isSelecting(other.getUniqueId())) continue;
            ShinobiCharacter oc = plugin.characters().getActive(other.getUniqueId());
            if (oc != null) {
                sendSkin(p, (other.getUniqueId() + "\n" + oc.appearance())
                        .getBytes(StandardCharsets.UTF_8)); // autres actifs → p
            }
        }
    }

    /** Diffuse un « clear » du skin de {@code id} à tous les autres (déconnexion). */
    private void broadcastClear(UUID id) {
        if (!Bukkit.getMessenger().isOutgoingChannelRegistered(plugin, SKINS_CHANNEL)) return;
        byte[] clear = (id + "\n").getBytes(StandardCharsets.UTF_8);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(id)) continue;
            sendSkin(other, clear);
        }
    }

    private void sendSkin(Player to, byte[] bytes) {
        try { to.sendPluginMessage(plugin, SKINS_CHANNEL, bytes); } catch (Exception ignored) { }
    }

    /** Confirme au client (canal {@code reborn:character}) que le perso est appliqué :
     *  le mod ferme alors l'écran de sélection/chargement (message « selected »). */
    private void sendSelected(Player p) {
        try {
            p.sendPluginMessage(plugin, CHANNEL, "selected".getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    /** Nombre de personnages autorisés selon le grade : lambda 2, premium 4, staff 7. */
    private int slotLimit(Player p) {
        if (p.hasPermission("shinobicore.staff")) return 7;
        if (p.hasPermission("reborn.premium")) return 4; // dormant tant que la boutique n'existe pas
        return 2;
    }

    // ── Réception (C2S : "select:<id>" | "create") ───────────────
    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        String msg = new String(message, StandardCharsets.UTF_8).trim();
        plugin.getLogger().info("[character-select] reçu de " + p.getName() + " : '" + msg + "'");
        // "open" = le client (touche) demande d'ouvrir/rouvrir la sélection.
        if (msg.equals("open")) {
            beginSelection(p);
            return;
        }
        if (!isSelecting(p.getUniqueId())) return;
        if (msg.startsWith("select:")) {
            handleSelect(p, msg.substring("select:".length()).trim());
        } else if (msg.startsWith("create")) {
            handleCreate(p, msg);
        }
    }

    private void handleSelect(Player p, String idStr) {
        UUID id;
        try { id = UUID.fromString(idStr); } catch (IllegalArgumentException e) { return; }
        ShinobiCharacter chosen = null;
        for (ShinobiCharacter c : plugin.characters().getAll(p.getUniqueId())) {
            if (c.id().equals(id)) { chosen = c; break; }
        }
        if (chosen == null) return;
        if (chosen.dead()) {
            p.sendMessage(Component.text(
                    "Ce personnage est mort (RPK). Demande à un staff de le ressusciter.",
                    NamedTextColor.RED));
            return;
        }
        endSelection(p);
        plugin.characters().setActive(p, chosen); // téléporte au dernier emplacement + stats/inventaire

        // Reset de sécurité : force un état de mouvement sain après la sélection
        // (évitait le "stun" résiduel walkSpeed/flySpeed/freezeTicks).
        p.setWalkSpeed(0.2f);
        p.setFlySpeed(0.1f);
        p.setFreezeTicks(0);

        // Confirme au client que le perso est appliqué → il ferme l'écran de
        // chargement/sélection (fermeture pilotée serveur, pas par un simple
        // minuteur, pour ne jamais lâcher un joueur non sélectionné en jeu).
        sendSelected(p);

        // Diffuse le skin RP à tous + reçoit ceux des autres (différé : client en jeu).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                broadcastActive(p);
                plugin.rpInventory().broadcastCosmetics(p); // cosmétiques 3D visibles entre joueurs
            }
        }, 10L);
    }

    /**
     * Crée un personnage depuis le wizard client. Format du message :
     * {@code create\n<name>\n<clan>\n<village>\n<sexe>\n<age>\n<size>}.
     * Un {@code create} nu (wizard pas encore soumis) = message d'attente.
     */
    private void handleCreate(Player p, String msg) {
        String[] parts = msg.split("\n", -1);
        if (parts.length < 7) {
            p.sendMessage(Component.text("Création de personnage — wizard en cours.",
                    NamedTextColor.YELLOW));
            return;
        }
        String name = parts[1].trim();
        String clan = parts[2].trim();
        String village = parts[3].trim();
        String sexe = parts[4].trim();
        int age;
        double size;
        try { age = Integer.parseInt(parts[5].trim()); } catch (NumberFormatException e) { age = 12; }
        try { size = Double.parseDouble(parts[6].trim()); } catch (NumberFormatException e) { size = 1.0; }

        if (name.isBlank()) {
            p.sendMessage(Component.text("Le prénom ne peut pas être vide.", NamedTextColor.RED));
            return;
        }
        List<ShinobiCharacter> roster = plugin.characters().getAll(p.getUniqueId());
        if (roster.size() >= slotLimit(p)) {
            p.sendMessage(Component.text("Limite de personnages atteinte (" + slotLimit(p) + ").",
                    NamedTextColor.RED));
            return;
        }
        if (plugin.characters().findByName(p.getUniqueId(), name).isPresent()) {
            p.sendMessage(Component.text("Tu as déjà un personnage nommé « " + name + " ».",
                    NamedTextColor.RED));
            return;
        }

        // Apparence RP composée = tout ce qui suit le size (queue SkinSpec.serialize()),
        // rejointe telle quelle (lignes séparées par \n) pour re-diffusion au client.
        String appearance = "";
        if (parts.length > 7) {
            StringBuilder ab = new StringBuilder();
            for (int i = 7; i < parts.length; i++) {
                if (i > 7) ab.append('\n');
                ab.append(parts[i]);
            }
            appearance = ab.toString();
        }

        ShinobiCharacter c = plugin.characters().create(p.getUniqueId(), name);
        c.setClan(clan.isBlank() ? "None" : clan);
        c.setVillage(village);
        c.setSexe(sexe);
        c.setAge(age);
        c.setSize(size);
        c.setAppearance(appearance);
        plugin.characters().save(c);

        plugin.getLogger().info("[character-select] " + p.getName() + " a créé le perso « "
                + name + " » (clan=" + clan + ", village=" + village + ", sexe=" + sexe
                + ", age=" + age + ", size=" + size + ")");

        endSelection(p);
        plugin.characters().setActive(p, c);
        sendSelected(p); // ferme l'écran client (perso appliqué)
        p.sendMessage(Component.text("Personnage « " + name + " » créé et activé !",
                NamedTextColor.GREEN));

        // Diffuse le nouveau skin RP à tous + reçoit ceux des autres.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                broadcastActive(p);
                plugin.rpInventory().broadcastCosmetics(p); // cosmétiques 3D visibles entre joueurs
            }
        }, 10L);
    }

    // Pas d'épinglage onMove : quand l'écran de sélection est ouvert côté client,
    // le joueur ne peut déjà pas bouger (input capturé par l'écran). Un
    // e.setTo(anchor) répété créait une désync teleport-confirm qui laissait le
    // joueur "stun" APRÈS la sélection — donc on ne fige plus côté serveur.

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        selecting.remove(e.getPlayer().getUniqueId());
        candidatures.remove(e.getPlayer().getUniqueId());
        broadcastClear(e.getPlayer().getUniqueId()); // les autres retirent son override skin
        plugin.rpInventory().broadcastCosmeticsClear(e.getPlayer().getUniqueId()); // + ses cosmétiques
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
                default   -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }
}
