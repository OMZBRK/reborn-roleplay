package com.reborn.shinobiabilities;

import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.HudService;
import com.reborn.shinobicore.api.ItemGiveService;
import com.reborn.shinobicore.api.KoService;
import com.reborn.shinobicore.api.MobilityService;
import com.reborn.shinobicore.api.ResourceService;

/**
 * The bundle of engine services this plugin consumes, resolved once
 * from the ShinobiCore api seam (ServicesManager) at boot and passed
 * around instead of the concrete plugin. Accessor names deliberately
 * mirror the old ShinobiCore facade accessors so existing call sites
 * read the same ({@code core.characters()}, {@code core.ko()}, …).
 */
public record CoreServices(CharacterService characters,
                           ResourceService chakra,
                           KoService ko,
                           MobilityService mobility,
                           HudService cooldownHud,
                           ItemGiveService itemGive,
                           com.reborn.shinobicore.api.InventoryService inventory) {

    /**
     * Surcharge (sacoche, palier « Surchargé » et au-delà) : la mobilité ninja est coupée — ni dash, ni double saut,
     * ni course aux murs, ni escalade, ni course de chakra (décision du 2026-10-13). Faux sans le service.
     */
    public boolean surcharge(org.bukkit.entity.Player p) {
        return inventory != null && p != null && inventory.mobiliteBloquee(p.getUniqueId());
    }
}
