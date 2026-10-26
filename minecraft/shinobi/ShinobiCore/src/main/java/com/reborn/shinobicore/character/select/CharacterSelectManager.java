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
 * Sélection de personnage « façon Reborn » pilotée par le mod client (mod-hud).
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
    /** Concurrente : écrite par la lecture asynchrone et par la douane (fil principal). */
    private final Map<UUID, CandidatureClient.Candidature> candidatures = new java.util.concurrent.ConcurrentHashMap<>();

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
        // Douane active (CANDIDATURE_ET_DOUANE.md §3.3) : refus par défaut. Sans preuve d'accès « play » (attente,
        // candidat au hall), la sélection ne s'ouvre pas, même demandée par le client (touche « open »). Vrai = traité :
        // l'appelant ne retombe pas sur l'ancienne GUI.
        if (plugin.douane() != null && !plugin.douane().selectionPermise(p)) {
            plugin.getLogger().info("[character-select] " + p.getName() + " : sélection refusée (accès douane non vérifié).");
            return true;
        }
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
        // Étape 5 d'ETATS_JOUEUR : hors jeu = non ciblable (porte des dégâts), plus de drapeau Invulnerable en NBT.
        if (!com.reborn.shinobicore.etat.Applicateur.actif()) p.setInvulnerable(true);
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
     * thread. Silencieux en cas d'indisponibilité (grisage simplement absent ; la
     * création du premier personnage attend alors une lecture réussie, voir
     * {@link #handleCreate}).
     */
    private void fetchCandidatureAsync(Player p) {
        UUID id = p.getUniqueId();
        if (!lecturesEnCours.add(id)) return;               // une lecture à la fois par joueur
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CandidatureClient.Candidature cand;
            try {
                cand = candidatureClient.fetch(id);
            } finally {
                lecturesEnCours.remove(id);
            }
            if (cand == null) return;
            candidatures.put(id, cand);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && isSelecting(id)) {
                    sendRoster(p, plugin.characters().getAll(id));
                }
            });
        });
    }

    /** Lectures de candidature en vol (une par joueur). */
    private final java.util.Set<UUID> lecturesEnCours = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Staff : grade du serveur ou compte staff côté API. Aucun verrou de création. */
    private static boolean estStaff(Player p, CandidatureClient.Candidature cand) {
        return p.hasPermission("shinobicore.staff") || (cand != null && cand.staff());
    }

    /**
     * Premier personnage issu de la candidature (CANDIDATURE_ET_DOUANE.md §4.2, Q8) : candidature acceptée, compte
     * hors staff, aucun personnage sur ce serveur (morts compris), candidature pas encore liée ailleurs.
     */
    private static boolean premierDeCandidature(Player p, CandidatureClient.Candidature cand,
                                                List<ShinobiCharacter> roster) {
        return cand != null && cand.found() && !cand.consumed() && !estStaff(p, cand) && roster.isEmpty();
    }

    /**
     * Candidature lue ailleurs (douane : bloc {@code candidature} de {@code GET /v1/game/acces}, signé par le nouveau
     * garde) : remplace le cache et renvoie le roster si la sélection est ouverte. Fil principal.
     */
    public void definirCandidature(UUID id, CandidatureClient.Candidature cand) {
        if (cand == null) return;
        candidatures.put(id, cand);
        Player p = Bukkit.getPlayer(id);
        if (p != null && isSelecting(id)) sendRoster(p, plugin.characters().getAll(id));
    }

    /** Oublie la candidature en cache (lue avant l'oral) : la prochaine sélection la relit. Fil principal. */
    public void oublierCandidature(UUID id) {
        candidatures.remove(id);
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
            sb.append(",\"age\":").append(c.age());
            sb.append(",\"size\":").append(String.format(java.util.Locale.ROOT, "%.2f", c.size()));
            if (c.sexe() != null && !c.sexe().isBlank()) sb.append(",\"sexe\":\"").append(esc(c.sexe())).append('"');
            if (!c.appearance().isBlank()) {
                sb.append(",\"appearance\":\"").append(esc(c.appearance())).append('"');
            }
            sb.append('}');
        }
        sb.append(']');
        // Bloc candidature (lot J, §4.1-4.2). « premier » = création du premier personnage issu de la candidature :
        // village et âge sous cadenas, prénom et nom de famille pré-remplis mais modifiables, sexe libre. Les valeurs
        // ne partent que dans ce cas (un mod d'avant verrouillait le village de tous les personnages). « staff » :
        // aucun verrou, clans de la liste ouverts. Absent → le mod ne pré-remplit rien.
        CandidatureClient.Candidature cand = candidatures.get(p.getUniqueId());
        boolean staff = estStaff(p, cand);
        boolean premier = premierDeCandidature(p, cand, roster);
        if (staff || premier) {
            sb.append(",\"candidature\":{\"staff\":").append(staff);
            sb.append(",\"premier\":").append(premier);
            if (premier) {
                ReglesCreation.Verrous v = ReglesCreation.Verrous.de(cand);
                champ(sb, "village", v.village());
                if (v.age() != null) sb.append(",\"age\":").append(v.age());
                champ(sb, "clan", ReglesCreation.AUTRE);
                champ(sb, "name", cand.name());
                champ(sb, "familyName", cand.familyName());
                champ(sb, "sexe", cand.sex());
                champ(sb, "type", cand.type());
            }
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

    /** Base selon le grade : lambda 2, premium 4, staff 7. */
    public static int slotBase(Player p) {
        if (p.hasPermission("shinobicore.staff")) return 7;
        if (p.hasPermission("reborn.premium")) return 4; // dormant tant que la boutique n'existe pas
        return 2;
    }

    /** Emplacements débloqués / retirés par le staff (Poste de garde, 15/10). */
    private EmplacementsBonus emplacements;

    public EmplacementsBonus emplacements() {
        if (emplacements == null) emplacements = new EmplacementsBonus(plugin);
        return emplacements;
    }

    /** Nombre de personnages autorisés : base du grade + bonus staff du compte (au moins 1). */
    public int slotLimit(Player p) {
        return Math.max(1, slotBase(p) + emplacements().of(p.getUniqueId()));
    }

    // ── Réception (C2S : "select:<id>" | "create") ───────────────
    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        String msg = new String(message, StandardCharsets.UTF_8).trim();
        plugin.getLogger().info("[character-select] reçu de " + p.getName() + " : '" + msg + "'");
        // "open" = le client (touche) demande d'ouvrir/rouvrir la sélection.
        if (msg.equals("open")) {
            // Correctif urgent (ETATS_JOUEUR §2.4 n° 4) : la sélection téléporte à l'ancre et rend invulnérable — elle
            // servait d'évasion (KO, gel, porté, duel, combat, scène de mort…). Refusée hors état « libre » quand on
            // incarne déjà quelqu'un ; la sélection de la connexion (sans personnage actif) reste ouverte.
            if (!isSelecting(p.getUniqueId()) && plugin.characters().getActive(p.getUniqueId()) != null) {
                String refus = com.reborn.shinobicore.etat.Verrous.changementPerso(p.getUniqueId());
                if (refus != null) {
                    p.sendActionBar(Component.text("Changement de personnage impossible : " + refus, NamedTextColor.RED));
                    return;
                }
            }
            beginSelection(p);
            return;
        }
        if (!isSelecting(p.getUniqueId())) return;
        // Douane (CANDIDATURE_ET_DOUANE.md §3.3) : pendant le contrôle tardif de l'accès (marque de Guardian pas encore
        // « play »), le choix est retenu puis rejoué à sa fin : un candidat n'entre jamais dans le monde RP avant.
        if ((msg.startsWith("select:") || msg.startsWith("create"))
                && plugin.douane() != null && !plugin.douane().choixPermis(p)) {
            plugin.douane().differerChoix(p, () -> onPluginMessageReceived(channel, p, message));
            return;
        }
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
        // Un KO suit le compte : s'il appartient à un autre personnage, il faut reprendre celui-là (sinon le KO
        // « périmé » s'appliquait au nouveau personnage, ou le joueur s'en échappait).
        String koDe = koSurAutrePerso(p, chosen);
        if (koDe != null) {
            p.sendMessage(Component.text(koDe + " est à terre : reprends ce personnage.", NamedTextColor.RED));
            return;
        }
        endSelection(p);
        if (!plugin.characters().setActive(p, chosen)) {   // refusé (prévenu) : on rouvre la sélection
            beginSelection(p);
            return;
        }
        // (setActive téléporte au dernier emplacement + stats/inventaire)

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

    /** Nom du personnage KO de ce compte s'il n'est pas {@code chosen}, sinon null. */
    private String koSurAutrePerso(Player p, ShinobiCharacter chosen) {
        var st = plugin.ko() != null ? plugin.ko().getKo(p.getUniqueId()) : null;
        if (st == null || st.characterId() == null || st.characterId().equals(chosen.id())) return null;
        return plugin.characters().findById(p.getUniqueId(), st.characterId()).map(ShinobiCharacter::name).orElse("Ton personnage");
    }

    /**
     * Refus d'une création : message au joueur (chat, pour tout client), roster renvoyé (candidature à jour), puis
     * {@code create-refus\n<étape>\n<message>} sur le canal : le mod annule la sortie de l'écran de création et rouvre
     * l'étape fautive. Un mod d'avant ignore ce message (JSON illisible).
     */
    private void refuserCreation(Player p, String etape, String message) {
        p.sendMessage(Component.text(message, NamedTextColor.RED));
        sendRoster(p, plugin.characters().getAll(p.getUniqueId()));
        try {
            p.sendPluginMessage(plugin, CHANNEL,
                    ("create-refus\n" + etape + "\n" + message).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    /**
     * Crée un personnage depuis le wizard client. Format du message :
     * {@code create\n<name>\n<clan>\n<village>\n<sexe>\n<age>\n<size>}.
     * Un {@code create} nu (wizard pas encore soumis) = message d'attente.
     *
     * <p>Contrôles <b>côté serveur</b> ({@link ReglesCreation}, CANDIDATURE_ET_DOUANE.md §4.2) : hors staff, aucun clan
     * de session (« Sans clan » + nom de famille) ; premier personnage issu de la candidature : village et âge imposés,
     * type et marque {@code application} repris. Un client modifié qui envoie autre chose est refusé.
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

        List<ShinobiCharacter> roster = plugin.characters().getAll(p.getUniqueId());
        if (roster.size() >= slotLimit(p)) {
            refuserCreation(p, "general", "Limite de personnages atteinte (" + slotLimit(p) + ").");
            return;
        }
        CandidatureClient.Candidature cand = candidatures.get(p.getUniqueId());
        boolean staff = estStaff(p, cand);
        // Candidature pas encore lue (elle arrive en asynchrone) : sans elle, le premier personnage échapperait à ses
        // verrous. On la relit et le joueur renvoie sa création. Sans aucun secret réglé (dev sans API), rien ne se lit :
        // création libre comme avant, toujours sans clan de session.
        if (!staff && roster.isEmpty() && cand == null && candidatureClient.configure()) {
            fetchCandidatureAsync(p);
            refuserCreation(p, "general", "Lecture de ta candidature en cours : valide à nouveau dans quelques secondes.");
            return;
        }
        boolean premier = premierDeCandidature(p, cand, roster);
        ReglesCreation.Verrous verrous = premier ? ReglesCreation.Verrous.de(cand) : null;
        ReglesCreation.Refus refus = ReglesCreation.verifier(
                new ReglesCreation.Demande(name, clan, village, sexe, age, size), staff, premier, verrous);
        if (refus != null) {
            plugin.getLogger().info("[character-select] création refusée pour " + p.getName() + " (" + refus.etape()
                    + ") : " + refus.message());
            refuserCreation(p, refus.etape(), refus.message());
            return;
        }
        if (plugin.characters().findByName(p.getUniqueId(), name).isPresent()) {
            refuserCreation(p, "nom", "Tu as déjà un personnage nommé « " + name + " ».");
            return;
        }
        if (!staff) {
            village = ReglesCreation.villageCanonique(village);
            sexe = ReglesCreation.sexeCanonique(sexe);
            size = ReglesCreation.tailleBornee(size);
        }
        clan = ReglesCreation.clanAEnregistrer(clan, staff);

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
        c.setClan(clan);
        c.setVillage(village);
        c.setSexe(sexe);
        c.setAge(age);
        c.setSize(size);
        c.setAppearance(appearance);
        if (premier) {
            // Lien candidature → personnage (§4.4) : la synchronisation renseigne la candidature côté API.
            c.setType(cand.type());
            c.setApplicationId(uuidOuNull(cand.applicationId()));
        }
        plugin.characters().save(c);
        // États de service (fiche v2) : « Enregistré à <village> », daté de la création.
        if (plugin.serviceRecords() != null) plugin.serviceRecords().enregistrement(c);
        if (plugin.synchroPersonnages() != null) plugin.synchroPersonnages().signaler(c, true);

        plugin.getLogger().info("[character-select] " + p.getName() + " a créé le perso « "
                + name + " » (clan=" + clan + ", village=" + village + ", sexe=" + sexe
                + ", age=" + age + ", size=" + size + (premier ? ", issu de la candidature" : "") + ")");

        if (plugin.ko() != null && plugin.ko().isKo(p.getUniqueId())) {
            p.sendMessage(Component.text("Personnage « " + name + " » créé. Ton personnage à terre doit être repris d'abord.",
                    NamedTextColor.GOLD));
            sendRoster(p, plugin.characters().getAll(p.getUniqueId()));
            return;
        }
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

    /** UUID valide sous forme canonique, ou {@code null}. */
    private static String uuidOuNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s.strip()).toString(); } catch (IllegalArgumentException e) { return null; }
    }

    /** {@code ,"k":"v"} si {@code v} n'est pas vide. */
    private static void champ(StringBuilder sb, String k, String v) {
        if (v == null || v.isBlank()) return;
        sb.append(",\"").append(k).append("\":\"").append(esc(v)).append('"');
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
