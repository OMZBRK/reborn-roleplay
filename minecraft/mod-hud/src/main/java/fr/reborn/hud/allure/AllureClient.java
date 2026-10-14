package fr.reborn.hud.allure;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;

/** Branchement des allures (canal, tick avant le déplacement, barre de sprint, banc). Un seul appel depuis le mod. */
public final class AllureClient {

    private AllureClient() {}

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(AllurePayload.ID, AllurePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AllurePayload.ID, AllurePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(AllurePayload.ID,
            (payload, context) -> context.client().execute(() -> Allure.INSTANCE.recevoir(payload.data())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Allure.INSTANCE.reset());
        ClientTickEvents.START_CLIENT_TICK.register(Allure.INSTANCE::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "allure"),
            (ctx, tickCounter) -> AllureHud.render(ctx));
        AllureDebug.init();
    }
}
