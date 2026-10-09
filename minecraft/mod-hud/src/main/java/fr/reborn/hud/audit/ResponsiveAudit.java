package fr.reborn.hud.audit;

import fr.reborn.hud.chat.ChatDebug;
import fr.reborn.hud.combat.CombatState;
import fr.reborn.hud.ko.KoClient;
import fr.reborn.hud.ko.KoDebug;
import fr.reborn.hud.parchemin.BibliothequeScreen;
import fr.reborn.hud.parchemin.BibliothequeStaffScreen;
import fr.reborn.hud.parchemin.ParcheminClient;
import fr.reborn.hud.parchemin.ParcheminDebug;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.runtime.VitalsFeed;
import fr.reborn.hud.staff.InfirmerieDebug;
import fr.reborn.hud.staff.StaffDebug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Audit responsive, <b>inactif en production</b> : ne fait rien sauf si {@code REBORN_AUDIT=1} (client de dev).
 * Ouvre chaque écran et chaque état du HUD du mod aux échelles d'interface 2, 3 et 4 (ou celles de
 * {@code REBORN_AUDIT_SCALES}, ex. « 2,3 »), et enregistre une capture nommée {@code audit_<écran>__s<échelle>.png}
 * dans run/screenshots/. Filtre optionnel : {@code REBORN_AUDIT_ONLY=garde,parch} (préfixes).
 */
public final class ResponsiveAudit {

    private record Case(String name, int settle, Consumer<Minecraft> setup) {}

    private static final List<Case> CASES = new ArrayList<>();
    private static int[] scales = {2, 3, 4};
    private static int ticks = -1, index = 0, phase = 0, local = 0, applied = -1;

    private ResponsiveAudit() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_AUDIT"))) return;
        String sc = System.getenv("REBORN_AUDIT_SCALES");
        if (sc != null && !sc.isBlank()) {
            String[] p = sc.split(",");
            scales = new int[p.length];
            for (int i = 0; i < p.length; i++) scales[i] = Integer.parseInt(p[i].trim());
        }
        build();
        String only = System.getenv("REBORN_AUDIT_ONLY");
        if (only != null && !only.isBlank()) {
            List<String> pre = List.of(only.split(","));
            CASES.removeIf(c -> pre.stream().noneMatch(p -> c.name.startsWith(p.trim())));
        }
        ClientTickEvents.END_CLIENT_TICK.register(ResponsiveAudit::tick);
    }

    private static void add(String name, int settle, Consumer<Minecraft> setup) { CASES.add(new Case(name, settle, setup)); }

    private static void screen(String name, java.util.function.Supplier<Screen> s) {
        add(name, 14, mc -> mc.setScreenAndShow(s.get()));
    }

    private static void build() {
        // ── HUD en jeu ──
        add("hud_chat_ferme", 8, mc -> { ChatDebug.fillDemo(mc); mc.setScreenAndShow(null); });
        add("hud_chat_ouvert", 10, mc -> { ChatDebug.fillDemo(mc); mc.setScreenAndShow(new ChatScreen("", false)); });
        add("hud_combat", 4, mc -> {
            mc.setScreenAndShow(null);
            long now = System.currentTimeMillis();
            CombatState st = CombatState.INSTANCE;
            st.onHit(mc.player.getId(), 9, now);
            st.onStamina(55, 100, now);
        });
        add("hud_ko_a_terre", 30, mc -> { mc.setScreenAndShow(null); KoClient.update(KoDebug.ko(1, 0, 38, 45, false)); });
        add("hud_ko_ata", 30, mc -> { KoClient.update(KoDebug.ata(2, 0.6f, 12, 20, true, true)); });
        add("hud_normal", 6, mc -> { KoClient.update(null); mc.setScreenAndShow(null); });

        // ── Poste de garde ──
        for (String tab : new String[]{"alerts", "players", "chat", "journal", "cmds"}) {
            add("garde_" + tab, 22, mc -> StaffDebug.openDemo(mc, tab, false));
        }
        add("garde_fiche", 22, mc -> StaffDebug.openDemo(mc, "players", true));
        // ── Infirmerie ──
        for (String tab : new String[]{"blesses", "zones", "hopitaux", "reglages"}) {
            add("infirmerie_" + tab, 22, mc -> InfirmerieDebug.openDemo(mc, tab));
        }
        // ── Parchemins ──
        add("parch_bibliotheque", 14, mc -> {
            ParcheminClient.debugReceive(ParcheminDebug.libJson().toString());
            if (mc.gui.screen() instanceof BibliothequeScreen b) b.debugHover = b.firstFilled();
        });
        add("parch_lecture", 22, mc -> {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("t", "read");
            o.add("tech", ParcheminDebug.tech(ParcheminDebug.find("katon_gokakyu")));
            o.addProperty("done", 1);
            o.addProperty("nextIn", "maintenant");
            o.addProperty("master", true);
            ParcheminClient.debugReceive(o.toString());
        });
        for (int p = 0; p < 3; p++) {
            final int page = p;
            add("parch_reglages_" + p, 12, mc -> {
                ParcheminClient.debugReceive(ParcheminDebug.cfgJson().toString());
                if (mc.gui.screen() instanceof BibliothequeStaffScreen s) s.setPage(page);
            });
        }

        // ── Menus du mod ──
        screen("menu_echap", () -> new PauseScreen(true));
        screen("menu_options", () -> new net.minecraft.client.gui.screens.options.OptionsScreen(null, Minecraft.getInstance().options, true));
        screen("menu_config", () -> new fr.reborn.hud.menu.screens.ConfigShellScreen(null));
        screen("menu_hud_edit", () -> new fr.reborn.hud.ui.HudEditScreen(null));
        screen("menu_hud_aide", () -> new fr.reborn.hud.ui.HudHelpScreen(null));
        screen("menu_chat_reglages", () -> new fr.reborn.hud.chat.ChatSettingsScreen(null));
        screen("menu_viseur", () -> new fr.reborn.hud.crosshair.CrosshairScreen(null));
        screen("menu_camera", () -> new fr.reborn.hud.camera.CameraScreen(null));
        screen("menu_animations", () -> new fr.reborn.hud.animation.AnimationMenuScreen(null));
        screen("menu_galerie", () -> new fr.reborn.hud.ui.GalleryScreen(null));
        screen("menu_sacoche", fr.reborn.hud.menu.inventory.InventoryScreen::new);
        screen("menu_fiche", fr.reborn.hud.menu.stats.StatsScreen::new);
        screen("menu_tablist", fr.reborn.hud.menu.tablist.TablistScreen::new);
        screen("menu_carte", fr.reborn.hud.map.WorldMapScreen::new);
        screen("menu_tirage", fr.reborn.hud.menu.tirage.TirageScreen::new);
        screen("menu_echoppe", () -> new fr.reborn.hud.menu.widget.ShopScreen(null));
        screen("menu_regles", () -> new fr.reborn.hud.menu.screens.RulesLoreScreen(null));
        screen("menu_signaler", () -> new fr.reborn.hud.menu.widget.ReportScreen(null));
        screen("menu_quitter", () -> new fr.reborn.hud.menu.widget.QuitConfirmScreen(null));
        screen("menu_deconnexion", () -> new fr.reborn.hud.menu.widget.DisconnectConfirmScreen(null));
        screen("perso_selection", fr.reborn.hud.menu.character.CharacterSelectScreen::new);
        screen("perso_creation", fr.reborn.hud.menu.character.CharacterCreateScreen::new);
        screen("perso_chargement", () -> new fr.reborn.hud.menu.character.CharacterLoadingScreen("Kazuki Uchiha", 0xFFC01E35));
        screen("menu_photo", fr.reborn.hud.ui.PhotoModeScreen::new);
        screen("titre", () -> new net.minecraft.client.gui.screens.TitleScreen());
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        if (ticks == 30) {
            RebornSession.debugForceRp = true;
            mc.player.connection.sendCommand("time set 6000");
            mc.player.connection.sendCommand("weather clear");
            VitalsFeed.update(82, 100, 64, 100);
            mc.gui.hud.getChat().clearMessages(false);
        }
        if (ticks < 50) return;
        if (phase >= scales.length) {
            if (local++ == 10) mc.stop();
            return;
        }
        if (applied != phase) {
            applied = phase;
            mc.options.guiScale().set(scales[phase]);
            mc.resizeGui();
            local = -15;   // laisse le rendu se stabiliser après le changement d'échelle
            return;
        }
        if (local < 0) { local++; return; }
        Case c = CASES.get(index);
        if (local == 2) {
            try {
                c.setup.accept(mc);
            } catch (RuntimeException e) {
                System.out.println("[audit] " + c.name + " : échec à l'ouverture — " + e);
            }
        }
        if (local == 2 + c.settle) {
            String file = "audit_" + c.name + "__s" + scales[phase] + ".png";
            Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
        }
        if (++local > 4 + c.settle) {
            local = 0;
            if (++index >= CASES.size()) { index = 0; phase++; }
        }
    }
}
