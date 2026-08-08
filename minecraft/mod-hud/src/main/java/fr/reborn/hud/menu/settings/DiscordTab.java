package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornBranding;
import fr.reborn.hud.menu.esc.EscData;
import net.minecraft.util.Util;

import java.net.URI;

/**
 * Onglet Discord — infos communauté réelles (compteur live via
 * {@link EscData}) + bouton « Rejoindre ». Les anciens toggles Rich Presence
 * étaient factices (le RPC est géré par le launcher, pas par le mod) : retirés.
 */
public class DiscordTab extends SectionedTab {

    @Override
    protected void build() {
        EscData.refreshIfStale();
        EscData.Snapshot snap = EscData.get();

        section("Communauté");

        if (snap != null && snap.discordMembers() >= 0) {
            valueRow("Membres", "Rejoignez la communauté Reborn",
                snap.discordMembers() + " membres", Colors.WHITE_PURE);
            valueRow("En ligne", null,
                snap.discordOnline() + " en ligne", Colors.SUCCESS);
        } else {
            labelRow("Membres", "Compteur indisponible (API injoignable)");
        }

        actionButton("→ Rejoindre le Discord", () -> {
            try {
                Util.getPlatform().openUri(URI.create(RebornBranding.DISCORD_URL));
            } catch (Exception ignored) {
                // Ouverture navigateur best-effort ; on n'échoue pas l'UI.
            }
        });

        spacer(4);
    }
}
