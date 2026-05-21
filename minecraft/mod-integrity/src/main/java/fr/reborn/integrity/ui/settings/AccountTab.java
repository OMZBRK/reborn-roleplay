package fr.reborn.integrity.ui.settings;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tab Compte — affichage readonly du pseudo MC, UUID, identifiant Reborn,
 * + danger zone "Se déconnecter du serveur".
 *
 * <p>Le rebornId est dérivé de l'UUID via un hash court formaté
 * "RBN-XXXXX". Placeholder en attendant l'API /v1/me qui retournera
 * le vrai ID alloué par le serveur.
 */
public class AccountTab implements SettingsTab {

    private final List<ClickableWidget> widgets = new ArrayList<>();
    private int contentHeight = 0;

    private static final int ROW_HEIGHT = 36;

    @Override
    public void layout(int x, int y, int width) {
        widgets.clear();

        // Pour MVP : aucun widget cliquable (compte = readonly). Le
        // bouton "Se déconnecter" sera câblé en PR ulterieure quand
        // on aura un endpoint /v1/auth/logout côté API.
        contentHeight = 3 * ROW_HEIGHT + 90;
    }

    @Override public List<ClickableWidget> widgets() { return widgets; }
    @Override public int height() { return contentHeight; }

    @Override
    public void renderPassive(DrawContext ctx, int x, int y, int width) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y - 28, 0);
        ctx.getMatrices().scale(1.2f, 1.2f, 1f);
        ctx.drawText(tr, RebornFont.bold("IDENTITÉ"), 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();

        // Récup données client.
        String pseudo = mc.getSession() != null ? mc.getSession().getUsername() : "Inconnu";
        UUID uuid = mc.getSession() != null ? mc.getSession().getUuidOrNull() : null;
        String uuidStr = uuid != null ? uuid.toString() : "(pas connecté)";
        String rebornId = uuid != null
            ? "RBN-" + String.format("%05d", Math.abs(uuid.hashCode() % 100000))
            : "RBN-?????";

        String[][] rows = {
            {"Pseudo Minecraft", pseudo},
            {"UUID Minecraft", uuidStr},
            {"Identifiant Reborn", rebornId},
        };

        int cursorY = y;
        for (String[] row : rows) {
            ctx.drawText(tr, RebornFont.body(row[0]), x, cursorY + 10, Colors.FOREGROUND_SUBTLE, false);

            // Field readonly à droite.
            Text val = RebornFont.bold(row[1]);
            int fieldW = Math.max(180, tr.getWidth(val) + 24);
            int fieldX = x + width - fieldW;
            DrawHelpers.roundedOutlinedRect(ctx, fieldX, cursorY + 6, fieldW, 22, 4,
                Colors.SURFACE, Colors.BORDER_STRONG);
            ctx.drawText(tr, val, fieldX + 10, cursorY + 12, Colors.FOREGROUND, false);

            cursorY += ROW_HEIGHT;
        }

        // Danger zone.
        cursorY += 24;
        int dangerH = 56;
        DrawHelpers.roundedOutlinedRect(ctx, x, cursorY, width, dangerH, 8,
            Colors.DANGER_SOFT, Colors.withAlpha(Colors.DANGER, 0.4f));
        ctx.drawText(tr, RebornFont.bold("Quitter le serveur"),
            x + 14, cursorY + 12, Colors.WHITE_PURE, false);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 14, cursorY + 26, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, RebornFont.body(
            "Tu seras renvoyé au menu principal. Ta progression est sauvegardée."),
            0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();
        // Bouton "Se déconnecter" — non câblé en MVP.
        Text btnText = RebornFont.bold("Se déconnecter");
        int btnW = tr.getWidth(btnText) + 28;
        int btnX = x + width - btnW - 14;
        int btnY = cursorY + (dangerH - 22) / 2;
        DrawHelpers.roundedOutlinedRect(ctx, btnX, btnY, btnW, 22, 4,
            Colors.withAlpha(Colors.DANGER, 0.15f), Colors.DANGER);
        ctx.drawText(tr, btnText, btnX + 14, btnY + 7, Colors.DANGER, false);
    }
}
