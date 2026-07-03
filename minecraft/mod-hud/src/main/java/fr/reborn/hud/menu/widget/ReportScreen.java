package fr.reborn.hud.menu.widget;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.esc.EscData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Report in-game (menu ÉCHAP → REPORT) : un petit formulaire (catégorie +
 * sujet + message) qui crée un ticket rattaché au compte du joueur. L'identité
 * vient du play-token (lu depuis {@code reborn.playTokenPath}) — l'API en
 * vérifie la signature. La conversation continue ensuite côté launcher (onglet
 * Tickets) + thread Discord staff, comme un ticket créé depuis le launcher.
 */
public class ReportScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/report");
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    private static final Identifier LOGO = Identifier.of("reborn", "textures/gui/title/logo.png");
    private static final int LOGO_TEX_W = 2048;
    private static final int LOGO_TEX_H = 717;

    // {code API, libellé} — cyclable.
    private static final String[][] CATEGORIES = {
        {"REPORT_PLAYER", "Signaler un joueur"},
        {"BUG", "Bug / probleme technique"},
        {"OTHER", "Autre"},
    };

    private final Screen parent;
    private TextFieldWidget subjectField;
    private TextFieldWidget messageField;
    private RebornButton categoryButton;
    private int categoryIdx = 0;

    private enum State { EDIT, SENDING, DONE, ERROR }
    private volatile State state = State.EDIT;
    private volatile String feedback = "";

    private static final int CARD_W = 380;
    private static final int CARD_H = 230;

    public ReportScreen(Screen parent) {
        super(Text.literal("Report"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;
        int fieldW = CARD_W - 40;
        int fieldX = cardX + 20;

        subjectField = new TextFieldWidget(this.textRenderer, fieldX, cardY + 74, fieldW, 16,
            Text.literal("sujet"));
        subjectField.setMaxLength(120);
        subjectField.setPlaceholder(Text.literal("Sujet (ex: pseudo du joueur, résumé)…"));
        this.addDrawableChild(subjectField);

        messageField = new TextFieldWidget(this.textRenderer, fieldX, cardY + 116, fieldW, 16,
            Text.literal("message"));
        messageField.setMaxLength(500);
        messageField.setPlaceholder(Text.literal("Décris ce qu'il s'est passé…"));
        this.addDrawableChild(messageField);
        this.setInitialFocus(subjectField);

        int btnH = 26;
        int btnY = cardY + CARD_H - 20 - btnH;
        // Catégorie (cyclable) sur toute la largeur au-dessus des champs.
        categoryButton = RebornButton.ghost(fieldX, cardY + 40, fieldW, 18,
            "Catégorie : " + CATEGORIES[categoryIdx][1], b -> cycleCategory());
        this.addDrawableChild(categoryButton);

        int half = (fieldW - 10) / 2;
        this.addDrawableChild(RebornButton.ghost(fieldX, btnY, half, btnH,
            "Annuler", b -> close()));
        this.addDrawableChild(RebornButton.accent(fieldX + half + 10, btnY, half, btnH,
            "Envoyer", b -> submit()));
    }

    private void cycleCategory() {
        categoryIdx = (categoryIdx + 1) % CATEGORIES.length;
        categoryButton.setMessage(RebornFont.bold("Catégorie : " + CATEGORIES[categoryIdx][1]));
    }

    private void submit() {
        if (state == State.SENDING) return;
        String subject = subjectField.getText().trim();
        String message = messageField.getText().trim();
        if (subject.length() < 4) { setError("Sujet trop court (4 caractères min)."); return; }
        if (message.length() < 10) { setError("Message trop court (10 caractères min)."); return; }

        String token = readPlayToken();
        if (token == null) {
            setError("Report indisponible — lance le jeu via le launcher.");
            return;
        }

        state = State.SENDING;
        feedback = "Envoi…";
        final String cat = CATEGORIES[categoryIdx][0];
        Thread t = new Thread(() -> send(token, cat, subject, message), "reborn-report");
        t.setDaemon(true);
        t.start();
    }

    private void send(String token, String category, String subject, String message) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("playToken", token);
            body.addProperty("category", category);
            body.addProperty("subject", subject);
            body.addProperty("message", message);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(EscData.apiBase() + "/play/report"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 == 2) {
                state = State.DONE;
                feedback = "✓ Report envoyé — suivi côté launcher (Tickets).";
                LOG.info("report envoyé ({})", category);
            } else {
                state = State.ERROR;
                feedback = "Échec (HTTP " + res.statusCode() + "). Réessaie plus tard.";
                LOG.warn("report HTTP {} : {}", res.statusCode(), res.body());
            }
        } catch (Exception e) {
            state = State.ERROR;
            feedback = "Échec réseau. Réessaie plus tard.";
            LOG.warn("report échec : {}", e.toString());
        }
    }

    private void setError(String msg) {
        state = State.ERROR;
        feedback = msg;
    }

    private static String readPlayToken() {
        String path = System.getProperty("reborn.playTokenPath");
        if (path == null || path.isBlank()) return null;
        try {
            String tok = Files.readString(Path.of(path)).trim();
            return tok.isEmpty() ? null : tok;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int bottomTint = Colors.lerp(Colors.BACKGROUND, Colors.ACCENT, 0.10f);
        DrawHelpers.verticalGradient(ctx, 0, 0, this.width, this.height, Colors.BACKGROUND, bottomTint);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        TextRenderer tr = this.textRenderer;

        int logoW = Math.min(Math.round(this.width * 0.11f), 150);
        int logoH = Math.round(logoW * (float) LOGO_TEX_H / LOGO_TEX_W);
        ctx.drawTexture(LOGO, (this.width - logoW) / 2, 14, logoW, logoH,
            0f, 0f, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;
        DrawHelpers.dropShadow(ctx, cardX, cardY, CARD_W, CARD_H, 6);
        DrawHelpers.roundedOutlinedRect(ctx, cardX, cardY, CARD_W, CARD_H, 12,
            Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);
        ctx.fill(cardX + 12, cardY, cardX + CARD_W - 12, cardY + 2, Colors.ACCENT);

        Text title = RebornFont.arcade("SIGNALER UN PROBLEME");
        ctx.drawText(tr, title, cardX + (CARD_W - tr.getWidth(title)) / 2, cardY + 16,
            Colors.WHITE_PURE, false);

        // Labels champs.
        ctx.drawText(tr, RebornFont.body("Sujet"), subjectField.getX(), subjectField.getY() - 10,
            Colors.FOREGROUND_SUBTLE, false);
        ctx.drawText(tr, RebornFont.body("Message"), messageField.getX(), messageField.getY() - 10,
            Colors.FOREGROUND_SUBTLE, false);

        for (Element e : this.children()) {
            if (e instanceof Drawable d) d.render(ctx, mouseX, mouseY, delta);
        }

        if (!feedback.isEmpty()) {
            int color = switch (state) {
                case DONE -> Colors.SUCCESS;
                case ERROR -> Colors.DANGER;
                default -> Colors.FOREGROUND_MUTED;
            };
            ctx.drawCenteredTextWithShadow(tr, RebornFont.body(feedback),
                this.width / 2, cardY + CARD_H + 12, color);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
