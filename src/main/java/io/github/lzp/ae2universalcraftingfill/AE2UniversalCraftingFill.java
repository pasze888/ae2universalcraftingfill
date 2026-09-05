/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.lzp.ae2universalcraftingfill;

import io.github.lzp.ae2universalcraftingfill.network.FillCraftingGridWithCountsPacket;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(AE2UniversalCraftingFill.MODID)
public class AE2UniversalCraftingFill {

    public static final String MODID = "ae2universalcraftingfill";

    public AE2UniversalCraftingFill(IEventBus modEventBus) {
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar(MODID);
            registrar.playToServer(FillCraftingGridWithCountsPacket.TYPE,
                    FillCraftingGridWithCountsPacket.STREAM_CODEC,
                    FillCraftingGridWithCountsPacket::handleOnServer);
        });
    }
}
