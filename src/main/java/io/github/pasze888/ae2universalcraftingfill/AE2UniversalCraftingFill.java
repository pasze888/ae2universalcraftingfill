/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.pasze888.ae2universalcraftingfill;

import io.github.pasze888.ae2universalcraftingfill.network.FillCraftingGridWithCountsPacket;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(AE2UniversalCraftingFill.MODID)
public class AE2UniversalCraftingFill {

    public static final String MODID = "ae2universalcraftingfill";

    public AE2UniversalCraftingFill(IEventBus modEventBus, ModContainer modContainer) {
        // 黑名单只影响客户端是否显示转移按钮，是纯客户端决策
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        if (FMLEnvironment.dist.isClient()) {
            // 不注册扩展点的话，模组列表里的 Config 按钮是灰的
            // （ModListScreen 里 configButton.active = IConfigScreenFactory.getForMod(...).isPresent()）
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }

        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar(MODID);
            registrar.playToServer(FillCraftingGridWithCountsPacket.TYPE,
                    FillCraftingGridWithCountsPacket.STREAM_CODEC,
                    FillCraftingGridWithCountsPacket::handleOnServer);
        });
    }
}
