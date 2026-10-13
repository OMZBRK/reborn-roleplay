package fr.reborn.hud.menu.widget;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.esc.EscData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * Signalement en jeu, au niveau de la fiche shinobi (DA §4.1, §6). Signature : la <b>boîte à doléances</b>
 * (meyasubako) posée en tête de la planche laquée ({@code textures/gui/signalement/*},
 * {@code tools/gen_signalement_textures.py}) ; le jeu reste visible sous un voile.
 *
 * <p>Report in-game (menu ÉCHAP → REPORT) : un petit formulaire (catégorie +
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

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("reborn", "textures/gui/title/logo.png");
    private static final int LOGO_TEX_W = 2048;
    private static final int LOGO_TEX_H = 717;

    // {code API, libellé} — cyclable.
    private static final String[][] CATEGORIES = {
        {"REPORT_PLAYER", "Signaler un joueur"},
        {"BUG", "Bug / probleme technique"},
        {"OTHER", "Autre"},
    };

    private final Screen parent;
    private EditBox subjectField;
    private EditBox messageField;
    private int categoryIdx = 0;

    private enum State { EDIT, SENDING, DONE, ERROR }
    private volatile State state = State.EDIT;
    private volatile String feedback = "";

    private static final int CARD_W = 380;
    private static final int CARD_H = 230;

    public ReportScreen(Screen parent) {
        super(Component.literal("Report"));
        this.parent = parent;
    }

    private static final int W = 340, H = 220, FW = 300;

    private int px() { return (this.width - W) / 2; }

    private int py() { return Math.max(28, (this.height - H) / 2 + 8); }

    @Override
    protected void init() {
        int x = px() + 20, y = py();
        subjectField = new EditBox(this.font, x + 5, y + 85, FW - 10, 10, Component.literal("sujet"));
        subjectField.setMaxLength(120);
        subjectField.setBordered(false);
        this.addRenderableWidget(subjectField);

        messageField = new EditBox(this.font, x + 5, y + 125, FW - 10, 10, Component.literal("message"));
        messageField.setMaxLength(500);
        messageField.setBordered(false);
        this.addRenderableWidget(messageField);
        this.setInitialFocus(subjectField);
    }

    private void cycleCategory() {
        categoryIdx = (categoryIdx + 1) % CATEGORIES.length;
    }

    private void submit() {
        if (state == State.SENDING) return;
        String subject = subjectField.getValue().trim();
        String message = messageField.getValue().trim();
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

    private static Identifier tex(String n) { return fr.reborn.hud.ui.DaKit.tex("signalement", n); }

    private static final Identifier PLANCHE = tex("planche"), BOITE = tex("boite"), CHAMP = tex("champ");
    private static final String[] CAT_COURT = {"Un joueur", "Un bug", "Autre"};

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, Minecraft.getInstance().level == null ? 0xFF0A0608 : 0xC8080406);
        fr.reborn.hud.ui.DaKit.etire(ctx, fr.reborn.hud.menu.settings.ReglagesArt.VIGNETTE, 0, 0, this.width, this.height,
            160, 90, fr.reborn.hud.ui.DaKit.blanc(0.8f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.extractBackground(ctx, mouseX, mouseY, delta);
        Font f = this.font;
        int x = px(), y = py(), fx = x + 20;
        fr.reborn.hud.ui.DaKit.blit(ctx, PLANCHE, x - 2, y - 2, 0, 0, 348, 228, 348, 228);
        fr.reborn.hud.ui.DaKit.blit(ctx, BOITE, x + 14, y - 24, 0, 0, 44, 40, 44, 40);
        fr.reborn.hud.ui.DaKit.arcX1(ctx, f, fr.reborn.hud.ui.DaKit.arcade("Signaler"), x + 66, y + 10,
            fr.reborn.hud.ui.DaKit.OR_CLAIR, 0, true);
        fr.reborn.hud.ui.DaKit.inter7(ctx, f, fr.reborn.hud.ui.DaKit.inter(
            "Un ticket est créé ; la suite se fait dans le launcher (Tickets)."), x + 66, y + 25,
            fr.reborn.hud.ui.DaKit.CREME_VAL, 0);

        // catégorie : trois plaques segmentées
        fr.reborn.hud.ui.DaKit.arcFin(ctx, f, fr.reborn.hud.ui.DaKit.arcade("Catégorie"), fx, y + 42,
            fr.reborn.hud.ui.DaKit.OR_ETIQ, 0, false);
        int sw = (FW + 2) / CATEGORIES.length;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int sx = fx + i * sw;
            boolean actif = i == categoryIdx, hov = fr.reborn.hud.ui.DaKit.dans(mouseX, mouseY, sx, y + 49, sw - 2, 16);
            fr.reborn.hud.menu.settings.ReglagesArt.segment(ctx, sx, y + 49, sw - 2, actif ? 2 : hov ? 1 : 0);
            fr.reborn.hud.ui.DaKit.inter8(ctx, f, fr.reborn.hud.ui.DaKit.inter(CAT_COURT[i]), sx + (sw - 2) / 2f, y + 57,
                actif || hov ? fr.reborn.hud.ui.DaKit.CREME : fr.reborn.hud.ui.DaKit.NEUTRE_TXT, 1);
        }
        // champs
        champ(ctx, f, "Sujet", fx, y + 80, subjectField, "Pseudo du joueur, résumé…");
        champ(ctx, f, "Message", fx, y + 120, messageField, "Décris ce qu'il s'est passé…");

        if (!feedback.isEmpty()) {
            int color = switch (state) {
                case DONE -> 0xFF7CAA94;
                case ERROR -> fr.reborn.hud.ui.DaKit.ROUGE_CLAIR;
                default -> fr.reborn.hud.ui.DaKit.DOUX;
            };
            fr.reborn.hud.ui.DaKit.inter8(ctx, f, fr.reborn.hud.ui.DaKit.inter(feedback.replace("✓ ", "")), x + W / 2f, y + 158, color, 1);
        }
        // boutons
        int by = y + H - 34, bw = 130;
        boolean h1 = fr.reborn.hud.ui.DaKit.dans(mouseX, mouseY, x + W / 2 - bw - 6, by, bw, 20);
        boolean h2 = fr.reborn.hud.ui.DaKit.dans(mouseX, mouseY, x + W / 2 + 6, by, bw, 20);
        fr.reborn.hud.menu.settings.ReglagesArt.bouton(ctx, x + W / 2 - bw - 6, by, bw, h1 ? 1 : 0);
        fr.reborn.hud.menu.settings.ReglagesArt.bouton(ctx, x + W / 2 + 6, by, bw, state == State.SENDING ? 2 : 3);
        fr.reborn.hud.ui.DaKit.inter9(ctx, f, fr.reborn.hud.ui.DaKit.inter("Annuler"), x + W / 2f - bw / 2f - 6, by + 10,
            h1 ? fr.reborn.hud.ui.DaKit.CREME : fr.reborn.hud.ui.DaKit.CREME_VAL, 1);
        fr.reborn.hud.ui.DaKit.inter9(ctx, f, fr.reborn.hud.ui.DaKit.inter(state == State.SENDING ? "Envoi…" : "Envoyer"),
            x + W / 2f + bw / 2f + 6, by + 10, h2 ? fr.reborn.hud.ui.DaKit.CREME : fr.reborn.hud.ui.DaKit.CREME_VAL, 1);
    }

    private void champ(GuiGraphicsExtractor ctx, Font f, String label, int x, int y, EditBox box, String indice) {
        fr.reborn.hud.ui.DaKit.arcFin(ctx, f, fr.reborn.hud.ui.DaKit.arcade(label), x, y - 7, fr.reborn.hud.ui.DaKit.OR_ETIQ, 0, false);
        fr.reborn.hud.ui.DaKit.coupeH(ctx, CHAMP, x - 1, y - 1, FW + 2, 0, 0, 304, 20, 6, 304, 22);
        box.extractRenderState(ctx, 0, 0, 0);
        if (box.getValue().isEmpty() && !box.isFocused()) {
            fr.reborn.hud.ui.DaKit.inter8(ctx, f, fr.reborn.hud.ui.DaKit.inter(indice), x + 6, y + 9, fr.reborn.hud.ui.DaKit.INACTIF, 0);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x(), my = event.y();
            int x = px(), y = py(), fx = x + 20, sw = (FW + 2) / CATEGORIES.length;
            for (int i = 0; i < CATEGORIES.length; i++) {
                if (fr.reborn.hud.ui.DaKit.dans(mx, my, fx + i * sw, y + 49, sw - 2, 16)) {
                    categoryIdx = i;
                    fr.reborn.hud.menu.RebornSounds.uiClick();
                    return true;
                }
            }
            int by = y + H - 34, bw = 130;
            if (fr.reborn.hud.ui.DaKit.dans(mx, my, x + W / 2 - bw - 6, by, bw, 20)) { onClose(); return true; }
            if (fr.reborn.hud.ui.DaKit.dans(mx, my, x + W / 2 + 6, by, bw, 20)) {
                fr.reborn.hud.menu.RebornSounds.playReborn("sacoche.place", 0.8f, 0.5f);   // le pli tombe dans la boîte
                submit();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
