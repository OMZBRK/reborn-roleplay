package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient coordination state for the {@code /rencontrer} (meet) flow.
 *
 * <p>Three kinds of state live here:
 *
 * <h2>Pending name-requests</h2>
 * When player A runs {@code /rencontrer → "Ask for a name"} targeting
 * player B, we stash "A is asking B" here so that — if B accepts — we
 * know whose contact book to update. Keyed by the TARGET's owner UUID so
 * there's at most one in-flight request per target at a time; the target
 * sees the latest request if two people ask back-to-back.
 *
 * <h2>Pending save-nickname prompts</h2>
 * After a nickname is revealed (either side), the <em>giver</em> gets a
 * clickable chat prompt asking if they want to save the nickname for
 * later reuse. The prompt's Yes/No buttons fire hidden {@code /rencontrer
 * save|skip <token>} commands; the token is resolved here to the
 * character + nickname combination the click should apply to. Tokens
 * expire after {@link #SAVE_PROMPT_TTL_MS} so players don't hoard stale
 * ones.
 *
 * <h2>Pending "give-name" target selection</h2>
 * Between the GUI screens in the give-name flow we need to remember what
 * the giver chose (real name vs. nickname; if nickname, the actual text).
 * Stored per-giver by their Minecraft UUID.
 */
public class RencontrerManager {

    /** How long a save-nickname token is honoured after we mint it. */
    public static final long SAVE_PROMPT_TTL_MS = 2L * 60L * 1000L;  // 2 min

    private final ShinobiCore plugin;

    /** Whoever currently has a name-request GUI open on them, and who asked. */
    private final Map<UUID, NameRequest> nameRequests = new ConcurrentHashMap<>();

    /** In-progress give-name selection keyed by the giver's Minecraft UUID. */
    private final Map<UUID, GiveDraft> drafts = new ConcurrentHashMap<>();

    /** token → pending save-nickname entry. */
    private final Map<UUID, SavePrompt> savePrompts = new ConcurrentHashMap<>();

    public RencontrerManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* --------------------------------------------------- name requests */

    /** Record that {@code requester} is asking {@code target} for their
     *  name. Overwrites any previous pending request on the target. */
    public void openRequest(UUID requester, UUID target) {
        nameRequests.put(target, new NameRequest(requester, target));
    }

    /** Pop (remove + return) the pending name-request for {@code target}. */
    public NameRequest consumeRequest(UUID target) {
        return nameRequests.remove(target);
    }

    /** Peek at a pending request without removing it — used by the GUI
     *  renderer to show who's asking. */
    public NameRequest peekRequest(UUID target) {
        return nameRequests.get(target);
    }

    /** The one-shot coordinates of a name-request. */
    public record NameRequest(UUID requesterMc, UUID targetMc) {}

    /* ------------------------------------------------------ give drafts */

    /** Start (or replace) a give-name draft for {@code giver}. */
    public void startDraft(UUID giver) {
        drafts.put(giver, new GiveDraft());
    }

    /** Get the current give-name draft for {@code giver}, creating one
     *  on-demand so screens later in the flow can always stash state. */
    public GiveDraft draftFor(UUID giver) {
        return drafts.computeIfAbsent(giver, k -> new GiveDraft());
    }

    /** Drop the giver's draft — called after the reveal succeeds or the
     *  flow is cancelled. */
    public void clearDraft(UUID giver) {
        drafts.remove(giver);
    }

    /** Mutable carrier for the give-name flow. A {@code null} nickname
     *  means "real-name give"; non-null means the giver has typed a
     *  nickname and is now picking a target. */
    public static final class GiveDraft {
        public String nickname;
    }

    /* ----------------------------------------------- save-nickname prompts */

    /** Mint a new save-nickname token tied to {@code character} + {@code nickname}.
     *  Returns the token UUID so the clickable chat prompt can embed it. */
    public UUID mintSavePrompt(UUID characterId, String nickname) {
        UUID token = UUID.randomUUID();
        savePrompts.put(token, new SavePrompt(characterId, nickname,
                System.currentTimeMillis() + SAVE_PROMPT_TTL_MS));
        // Schedule async cleanup so stale tokens don't linger forever.
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
                () -> savePrompts.remove(token),
                SAVE_PROMPT_TTL_MS / 50L /* ms → ticks */);
        return token;
    }

    /** Pop a save-nickname prompt. Returns {@code null} if the token was
     *  never minted or has expired. */
    public SavePrompt consumeSavePrompt(UUID token) {
        SavePrompt p = savePrompts.remove(token);
        if (p == null) return null;
        if (System.currentTimeMillis() > p.expiresAtMs()) return null;
        return p;
    }

    /** Info carried on a save-nickname token. */
    public record SavePrompt(UUID characterId, String nickname, long expiresAtMs) {}

    /* --------------------------------------------------------- reveal API */

    /**
     * Commit a reveal: write {@code subject}'s display name into
     * {@code learner}'s contact book, persist, notify both sides, and —
     * if the display is a nickname that isn't already in the giver's
     * pool — fire a clickable save-prompt to the giver.
     *
     * <p>The giver and subject aren't always the same: in the "ask for a
     * name" flow, the requester is learning the target's name, so the
     * giver is the target. Pass whichever {@link Player} should receive
     * the save-prompt as {@code giverPlayer}.
     *
     * @param subject       the character being revealed (owner of the name)
     * @param learner       the character that will remember {@code subject}
     * @param giverPlayer   player that gave the name — receives the save prompt
     * @param learnerPlayer player that learned the name — receives a confirm line
     * @param display       string to store ("Name Clan" for real, arbitrary for nickname)
     * @param isNickname    true when {@code display} is a nickname
     */
    public void reveal(ShinobiCharacter subject, ShinobiCharacter learner,
                       Player giverPlayer, Player learnerPlayer,
                       String display, boolean isNickname) {
        if (subject == null || learner == null || display == null || display.isBlank()) return;
        learner.remember(subject.id(), display, isNickname);
        plugin.characters().save(learner);

        // Learner confirm line — uses their just-updated contact book
        // entry to preview how they'll see the subject from now on.
        if (learnerPlayer != null && learnerPlayer.isOnline()) {
            learnerPlayer.sendMessage(Component.text(
                    "Tu le connais désormais sous le nom de ", NamedTextColor.GREEN)
                    .append(Component.text(display, NamedTextColor.GOLD)
                            .decoration(TextDecoration.BOLD, true))
                    .append(Component.text(".", NamedTextColor.GREEN)));
        }
        // Giver confirm line — lets them verify the reveal landed.
        if (giverPlayer != null && giverPlayer.isOnline() && learnerPlayer != null) {
            giverPlayer.sendMessage(Component.text(
                    learnerPlayer.getName() + " te verra désormais sous le nom de ", NamedTextColor.GREEN)
                    .append(Component.text(display, NamedTextColor.GOLD)
                            .decoration(TextDecoration.BOLD, true))
                    .append(Component.text(".", NamedTextColor.GREEN)));
        }

        // Save-prompt only for nicknames — real names are derived from the
        // character and don't need a pool. Skip if the giver already has
        // this nickname saved.
        if (!isNickname) return;
        if (giverPlayer == null || !giverPlayer.isOnline()) return;
        ShinobiCharacter giverChar = plugin.characters().getActive(giverPlayer.getUniqueId());
        if (giverChar == null) return;
        String trimmed = display.trim();
        if (giverChar.savedNicknames().contains(trimmed)) return;

        UUID token = mintSavePrompt(giverChar.id(), trimmed);
        Component prompt = Component.text("Sauvegarder \"", NamedTextColor.GOLD)
                .append(Component.text(trimmed, NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, true))
                .append(Component.text("\" pour plus tard ? ", NamedTextColor.GOLD))
                .append(Component.text("[Oui]", NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true)
                        .clickEvent(ClickEvent.runCommand("/rencontrer save " + token))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Ajouter à ta liste de surnoms", NamedTextColor.GRAY))))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text("[Non]", NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true)
                        .clickEvent(ClickEvent.runCommand("/rencontrer skip " + token))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Ne pas sauvegarder", NamedTextColor.GRAY))));
        giverPlayer.sendMessage(prompt);
    }
}
