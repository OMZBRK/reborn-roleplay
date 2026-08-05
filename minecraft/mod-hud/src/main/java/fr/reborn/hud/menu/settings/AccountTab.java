package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * Onglet Compte — identité Minecraft en lecture seule (pseudo + UUID réels,
 * lus depuis la session). L'ancien « Identifiant Reborn RBN-xxxx » était un
 * placeholder inventé localement : retiré en attendant l'API /v1/me.
 */
public class AccountTab extends SectionedTab {

    @Override
    protected void build() {
        Minecraft mc = Minecraft.getInstance();
        String username = "—";
        String uuid = "—";
        if (mc != null && mc.getSession() != null) {
            username = mc.getSession().getUsername();
            UUID id = mc.getSession().getUuidOrNull();
            if (id != null) uuid = id.toString();
        }

        section("Identité");
        valueRow("Pseudo Minecraft", "Depuis votre compte Microsoft",
            username, Colors.WHITE_PURE);
        valueRow("UUID", null, uuid, Colors.FOREGROUND_MUTED);

        spacer(4);
    }
}
