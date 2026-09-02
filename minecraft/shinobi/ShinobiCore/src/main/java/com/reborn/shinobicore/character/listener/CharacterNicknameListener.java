package com.reborn.shinobicore.character.listener;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.ShinobiCharacter;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Per-viewer chat rendering: every recipient sees the sender under the
 * name their active character has stored for them in the contact book
 * ("????" by default, real "Name Clan" or a nickname once introduced).
 *
 * <p>Chat format:
 *
 * <pre>
 *   Hashirama Senju » &lt;message&gt;        // if viewer has been introduced
 *   ???? » &lt;message&gt;                     // otherwise
 * </pre>
 *
 * <p>The nickname is bold (clan colour for real names, neutral gray for
 * nicknames / stranger marks). The separator is a soft gray chevron and
 * the message body inherits the Minecraft default. Console / staff
 * without an active character always see the real name so moderation
 * logs stay readable.
 *
 * <p><b>Why cancel+broadcast instead of {@code e.renderer(...)}:</b> the
 * renderer receives a single {@code sourceDisplayName} component, which
 * can't diverge per-viewer. Cancelling + iterating {@code e.viewers()}
 * lets us build a different prefix for every recipient, which is what
 * the "unknown stranger" RP feature requires.
 */
public class CharacterNicknameListener implements Listener {

    private final ShinobiCore plugin;

    public CharacterNicknameListener(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* --------------------------------------------------------------- chat */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player sender = e.getPlayer();
        // getActiveOrLastSeen — the chat event is async, so a chat
        // message that fires during the brief window of setActive
        // (between the previous-character save and the active-map
        // publish) used to read null and render the Mojang username.
        // Falling back to the most recently seen active character keeps
        // the rendered identity stable across that race.
        ShinobiCharacter senderChar = plugin.characters().getActiveOrLastSeen(sender.getUniqueId());

        // Cancel first so the vanilla chat path doesn't also deliver the
        // message — we're re-broadcasting with per-viewer formatting.
        e.setCancelled(true);

        Component separator = Component.text(" » ", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, false);
        Component message = e.message();

        for (Audience viewer : e.viewers()) {
            Component prefix = prefixFor(senderChar, sender, viewer);
            viewer.sendMessage(Component.empty()
                    .append(prefix)
                    .append(separator)
                    .append(message));
        }
    }

    /** Build the name prefix {@code viewer} should see for the chat
     *  line from {@code sender}. Console / non-player viewers always
     *  see the real name. */
    private Component prefixFor(ShinobiCharacter senderChar, Player sender, Audience viewer) {
        if (senderChar == null) {
            // Sender genuinely has no active character (never selected
            // one OR the cache was deliberately cleared) — fall back to
            // a neutral "Inconnu" label rather than leaking the Mojang
            // username, which would defeat the per-viewer ???? RP system.
            return Component.text("Inconnu", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true);
        }
        if (!(viewer instanceof Player p)) {
            return CharacterDisplay.styledName(senderChar);
        }
        ShinobiCharacter viewerChar = plugin.characters().getActiveOrLastSeen(p.getUniqueId());
        return CharacterDisplay.styledNameFor(senderChar, viewerChar);
    }

    /* --------------------------------------------- lifecycle refresh hooks */

    /** After join, push the subject's own real name onto tab / nameplate.
     *  (Viewer-specific masking is only applied to chat / /me, not the
     *  global display-name component; see {@link CharacterDisplay#apply}.) */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            if (c == null) CharacterDisplay.reset(p);
            else CharacterDisplay.apply(p, c);
        });
    }

    /** Some clients drop the display-name override on world change; push
     *  it again to stay consistent. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) CharacterDisplay.reset(p);
        else CharacterDisplay.apply(p, c);
    }
}
