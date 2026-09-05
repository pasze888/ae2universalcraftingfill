/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.lzp.ae2universalcraftingfill.network;

import io.github.lzp.ae2universalcraftingfill.AE2UniversalCraftingFill;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class InitNetwork {

    private InitNetwork() {
    }

    /** 在 mod 构造器里通过 modEventBus.addListener 注册。 */
    public static void init(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(AE2UniversalCraftingFill.MODID);
        registrar.playToServer(FillCraftingGridWithCountsPacket.TYPE,
                FillCraftingGridWithCountsPacket.STREAM_CODEC,
                FillCraftingGridWithCountsPacket::handleOnServer);
    }
}
