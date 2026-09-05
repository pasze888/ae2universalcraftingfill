/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * JEI 插件的注册方式参考了 AE2-JEI-Integration (Tamaized) 的
 * tamaized/ae2jeiintegration/integration/modules/jei/JEIPlugin.java（LGPL-3.0）：
 * https://github.com/Tamaized/AE2-JEI-Integration
 */
package io.github.pasze888.ae2universalcraftingfill.jei;

import net.minecraft.resources.ResourceLocation;

import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.me.items.WirelessCraftingTermMenu;
import io.github.pasze888.ae2universalcraftingfill.AE2UniversalCraftingFill;
import io.github.pasze888.ae2universalcraftingfill.jei.transfer.CraftingTermUniversalTransferHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeTransferRegistration;

@JeiPlugin
public class JEIPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AE2UniversalCraftingFill.MODID, "main");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // AE2 1.21.x 本体不自带 JEI 转移（由 AE2-JEI-Integration 等第三方补位）。
        // 专属处理器优先于 universal 处理器，因此这里注册的 universal handler 只会
        // 兜底接管「没有专属处理器」的配方类别，不影响工作台配方走原有逻辑。
        var handler = new CraftingTermUniversalTransferHandler<>(
                CraftingTermMenu.TYPE, CraftingTermMenu.class, registration.getTransferHelper());
        registration.addUniversalRecipeTransferHandler(handler);
        // 无线合成终端是 AE2 本体自带界面，菜单继承自 CraftingTermMenu，直接复用
        registration.addUniversalRecipeTransferHandler(new CraftingTermUniversalTransferHandler<>(
                WirelessCraftingTermMenu.TYPE, WirelessCraftingTermMenu.class, registration.getTransferHelper()));
    }
}
