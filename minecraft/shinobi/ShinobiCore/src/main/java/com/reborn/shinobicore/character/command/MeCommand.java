package com.reborn.shinobicore.character.command;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.CharacterEmote;
import com.reborn.shinobicore.character.Clan;
import com.reborn.shinobicore.character.Masques;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /me <action>} — broadcast a roleplay action narrated with the
 * sender's character nickname rather than their Minecraft username. Example:
 *
 * <pre>
 *   /me opens the door
 *   → * Hashirama Senju opens the door          (viewer knows the sender)
 *   → * ???? opens the door                     (viewer doesn't — stranger)
 *   → * Spikey opens the door                   (viewer only knows a nickname)
 * </pre>
 *
 * <h2>Unified colour</h2>
 * The entire /me line is rendered in a single colour so the narration reads
 * as one cohesive thought instead of a ransom-note of mixed tones. The only
 * visual emphasis is that the name+clan portion is <b>bold</b>. Colour is
 * picked from the sender's side (clan colour when known, gray when the
 * viewer only has a nickname, is still a stranger, or the sender wears a
 * mask — GAMEPLAN §14 : un masque porté cache aussi la couleur du clan).
 *
 * <h2>Per-viewer rendering</h2>
 * Because the server is an RP environment and characters default to
 * {@code ????} until they've been introduced via {@code /rencontrer}, the
 * narration is sent to each online player individually with the prefix
 * resolved through the viewer's active character's contact book. Console
 * receives the real name. La ligne de tchat reste pour tous (sans portée).
 *
 * <h2>Bulle au-dessus de la tête (31/10)</h2>
 * Canal {@value #CANAL} (S → C, JSON UTF-8 brut sans préfixe, <b>un message par destinataire</b> : le nom est résolu
 * pour chacun) : {@code {"v":1,"t":"me","id":<entityId>,"u":"<uuid>","nom":"Kazuki Uchiha","inconnu":false,
 * "ton":"C0392B","texte":"…","ms":6500,"r":16,"n":17}} — {@code nom} / {@code inconnu} / {@code ton} comme la ligne de
 * tchat de ce spectateur ({@code ????} et gris si inconnu ou masqué), {@code texte} ≤ {@value #TEXTE_MAX} caractères
 * sans codes §, {@code ms} = durée d'affichage, {@code r} = portée ({@code me.bulle.portee}), {@code n} = numéro
 * croissant (un nouveau /me remplace ou empile le précédent). Destinataires : l'auteur et ceux qui le suivent
 * ({@code getTrackedBy}), qui le voient, à moins de {@code r} blocs, et dont le mod écoute le canal. Le mod la dessine
 * calée sur la tête.
 *
 * <p>Les clients <b>sans</b> ce canal (pas de mod, ou mod trop ancien) gardent l'ancienne bulle flottante
 * {@link CharacterEmote} (un {@code TextDisplay} par libellé : deux inconnus partagent la même bulle {@code ????}) ; les
 * clients du mod n'en font jamais partie, sinon la bulle s'afficherait deux fois.
 */
public class MeCommand implements CommandExecutor {

    /** Bulle du /me au-dessus de la tête (S → C, mod reborn-hud). */
    public static final String CANAL = "reborn:me";
    /** Longueur maximale de l'action envoyée à la bulle. */
    private static final int TEXTE_MAX = 256;

    private final ShinobiCore plugin;
    /** Numéro croissant des /me, partagé par tous les auteurs (donc croissant pour chacun). */
    private int numero;

    public MeCommand(ShinobiCore plugin) {
        this.plugin = plugin;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CANAL)) m.registerOutgoingPluginChannel(plugin, CANAL);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Réservé aux joueurs.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(Component.text("Usage : /me <action>", NamedTextColor.RED));
            return true;
        }

        String action = String.join(" ", args).trim();
        if (action.isEmpty()) {
            p.sendMessage(Component.text("Usage : /me <action>", NamedTextColor.RED));
            return true;
        }

        ShinobiCharacter senderChar = plugin.characters().getActive(p.getUniqueId());

        // ---------- per-viewer chat broadcast ----------
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter viewerChar = plugin.characters().getActive(viewer.getUniqueId());
            Component line = meLineFor(senderChar, p, action, viewerChar, /*forceRealName=*/false);
            viewer.sendMessage(line);
        }
        // Console sees the real name so moderation / logs stay readable.
        Component consoleLine = meLineFor(senderChar, p, action, /*viewerChar=*/null, /*forceRealName=*/true);
        Bukkit.getConsoleSender().sendMessage(consoleLine);

        // ---------- bulle du mod (reborn:me) : proches qui voient l'auteur ----------
        bulles(p, senderChar, action);

        // ---------- per-viewer floating bubble (clients sans le canal reborn:me) ----------
        // Group viewers by the text they should see, so each unique label
        // becomes one TextDisplay visible only to that group (and invisible
        // to everyone else via Player.hideEntity).
        Map<String, CharacterEmote.ViewerGroup> groups = new HashMap<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (ecoute(viewer)) continue;   // mod à jour : sa bulle à lui (ou rien hors de portée), jamais les deux
            ShinobiCharacter viewerChar = plugin.characters().getActive(viewer.getUniqueId());
            Component prefix = (senderChar == null)
                    ? Component.text(p.getName(), NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
                    : CharacterDisplay.styledNameFor(senderChar, viewerChar);
            NamedTextColor tone = toneFor(senderChar, viewerChar);
            // Bubble body uses the unified tone, italics for narration flavour.
            Component bubble = Component.empty()
                    .append(prefix)
                    .append(Component.text(" " + action, tone)
                            .decoration(TextDecoration.ITALIC, true));

            // Key groups by the plain-text prefix label so two strangers
            // share a single ???? bubble. The action is identical for
            // everyone so it isn't part of the key.
            String labelKey = plainLabel(senderChar, viewerChar, p);
            groups.computeIfAbsent(labelKey, k -> new CharacterEmote.ViewerGroup(bubble))
                    .viewers.add(viewer);
        }
        CharacterEmote.showPerViewer(plugin, p, groups.values(), action.length());
        return true;
    }

    /* --------------------------------------------------------- bulle reborn:me */

    /** Ce client a-t-il le mod qui dessine la bulle du /me ? */
    private static boolean ecoute(Player p) {
        return p.getListeningPluginChannels().contains(CANAL);
    }

    /** Un message {@value #CANAL} par destinataire : l'auteur et ceux qui le voient à moins de {@code me.bulle.portee}. */
    private void bulles(Player auteur, ShinobiCharacter senderChar, String action) {
        int portee = Math.max(2, Math.min(128, plugin.getConfig().getInt("me.bulle.portee", 16)));
        String texte = nettoyer(action);
        if (texte.isEmpty()) return;
        int n = ++numero;
        long ms = Math.min(12_000L, 2_000L + 150L * texte.codePointCount(0, texte.length()));

        List<Player> dest = new ArrayList<>();
        if (ecoute(auteur)) dest.add(auteur);
        Location ici = auteur.getLocation();
        double max = (double) portee * portee;
        for (Player v : auteur.getTrackedBy()) {
            if (v.equals(auteur) || !ecoute(v) || !v.canSee(auteur)) continue;
            Location la = v.getLocation();
            if (la.getWorld() == null || !la.getWorld().equals(ici.getWorld()) || la.distanceSquared(ici) > max) continue;
            dest.add(v);
        }

        for (Player v : dest) {
            ShinobiCharacter viewerChar = plugin.characters().getActive(v.getUniqueId());
            JsonObject o = new JsonObject();
            o.addProperty("v", 1);
            o.addProperty("t", "me");
            o.addProperty("id", auteur.getEntityId());
            o.addProperty("u", auteur.getUniqueId().toString());
            o.addProperty("nom", nomPour(senderChar, viewerChar, auteur));
            o.addProperty("inconnu", inconnu(senderChar, viewerChar));
            o.addProperty("ton", String.format(Locale.ROOT, "%06X", toneFor(senderChar, viewerChar).value() & 0xFFFFFF));
            o.addProperty("texte", texte);
            o.addProperty("ms", ms);
            o.addProperty("r", portee);
            o.addProperty("n", n);
            v.sendPluginMessage(plugin, CANAL, o.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Action prête pour la bulle : codes § et caractères de contrôle retirés, {@value #TEXTE_MAX} caractères au plus. */
    private static String nettoyer(String action) {
        String t = action.replaceAll("(?i)§[0-9a-fk-orx]", "").replace("§", "")
                .replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (t.codePointCount(0, t.length()) > TEXTE_MAX) t = t.substring(0, t.offsetByCodePoints(0, TEXTE_MAX)).trim();
        return t;
    }

    /**
     * Le spectateur voit-il l'auteur comme un inconnu ({@code ????}) ? Mêmes règles que {@code styledNameFor} : masqué,
     * inconnu pour tous les autres, spectateur sans personnage actif compris (staff, sélection).
     */
    private static boolean inconnu(ShinobiCharacter senderChar, ShinobiCharacter viewerChar) {
        if (senderChar == null) return false;
        if (viewerChar != null && viewerChar.id().equals(senderChar.id())) return false;
        if (Masques.porte(senderChar.ownerId())) return true;
        return viewerChar != null && viewerChar.knownNameOf(senderChar.id()) == null;
    }

    /** Le nom tel que ce spectateur le lit dans la ligne de tchat, en texte brut. */
    private static String nomPour(ShinobiCharacter senderChar, ShinobiCharacter viewerChar, Player sender) {
        if (senderChar == null) return sender.getName();
        if (inconnu(senderChar, viewerChar)) return CharacterDisplay.UNKNOWN_LABEL;
        if (viewerChar == null || viewerChar.id().equals(senderChar.id())) return CharacterDisplay.realNameString(senderChar);
        return viewerChar.knownNameOf(senderChar.id()).display();
    }

    /* --------------------------------------------------------- tchat */

    /** Build the full {@code * Name action} line for one viewer. */
    private Component meLineFor(ShinobiCharacter senderChar, Player sender, String action,
                                ShinobiCharacter viewerChar, boolean forceRealName) {
        NamedTextColor tone = forceRealName
                ? Clan.colourFor(senderChar == null ? null : senderChar.clan())
                : toneFor(senderChar, viewerChar);

        Component prefix;
        if (senderChar == null) {
            // No active character (shouldn't happen under lockdown; be
            // defensive). Fall back to the Minecraft username.
            prefix = Component.text(sender.getName(), tone)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false);
        } else if (forceRealName) {
            prefix = CharacterDisplay.styledName(senderChar);
        } else {
            prefix = CharacterDisplay.styledNameFor(senderChar, viewerChar);
        }

        // Leading "* " in the unified tone as well so the whole line reads
        // as one colour — only the name+clan is bold, the rest is italic.
        return Component.empty()
                .append(Component.text("* ", tone)
                        .decoration(TextDecoration.BOLD, true))
                .append(prefix)
                .append(Component.text(" " + action, tone)
                        .decoration(TextDecoration.ITALIC, true));
    }

    /** Pick the single colour for the /me line as seen by this viewer.
     *  <ul>
     *    <li>Viewer knows the real name → clan colour of the sender.</li>
     *    <li>Viewer only knows a nickname → neutral gray (no clan leak).</li>
     *    <li>Viewer is the sender → clan colour.</li>
     *    <li>Stranger viewer → gray (???? matches gray).</li>
     *    <li>Masque porté → gris pour tous les autres, même ceux qui le connaissent, et même un spectateur sans
     *        personnage actif (staff, sélection) : pas de fuite du clan.</li>
     *    <li>Spectateur sans personnage actif, auteur non masqué → couleur du clan (il voit le vrai nom).</li>
     *  </ul>
     */
    private NamedTextColor toneFor(ShinobiCharacter senderChar, ShinobiCharacter viewerChar) {
        if (senderChar == null) return NamedTextColor.GRAY;
        if (viewerChar != null && viewerChar.id().equals(senderChar.id())) return Clan.colourFor(senderChar.clan());
        // masque porté : inconnu pour tous (§14) — avant la branche « sans personnage », qui lisait la couleur du clan
        if (Masques.porte(senderChar.ownerId())) return NamedTextColor.GRAY;
        if (viewerChar == null) return Clan.colourFor(senderChar.clan());
        ShinobiCharacter.KnownName known = viewerChar.knownNameOf(senderChar.id());
        if (known == null) return NamedTextColor.GRAY;
        return known.isNickname() ? NamedTextColor.GRAY : Clan.colourFor(senderChar.clan());
    }

    /** Plain-text label used to group viewers who see the same bubble. */
    private String plainLabel(ShinobiCharacter senderChar, ShinobiCharacter viewerChar, Player sender) {
        if (senderChar == null) return "mc:" + sender.getName();
        if (viewerChar != null && viewerChar.id().equals(senderChar.id())) {
            return "real:" + CharacterDisplay.realNameString(senderChar);
        }
        if (Masques.porte(senderChar.ownerId())) return "unknown";   // masque porté : la bulle ???? grise, pour tous
        if (viewerChar == null) return "real:" + CharacterDisplay.realNameString(senderChar);
        ShinobiCharacter.KnownName known = viewerChar.knownNameOf(senderChar.id());
        if (known == null) return "unknown";
        return (known.isNickname() ? "nick:" : "real:") + known.display();
    }
}
