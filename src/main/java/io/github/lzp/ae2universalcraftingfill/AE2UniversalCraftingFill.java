/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.lzp.ae2universalcraftingfill;

import io.github.lzp.ae2universalcraftingfill.network.InitNetwork;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(AE2UniversalCraftingFill.MODID)
public class AE2UniversalCraftingFill {

    public static final String MODID = "ae2universalcraftingfill";

    public AE2UniversalCraftingFill(IEventBus modEventBus) {
        modEventBus.addListener(InitNetwork::init);
    }
}
