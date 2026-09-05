/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * 本文件的部分组织方式与调用参考了以下 LGPL-3.0 项目，特此标注出处：
 * - Applied Energistics 2 (https://github.com/AppliedEnergistics/Applied-Energistics-2)：
 *   appeng/integration/modules/itemlists/CraftingHelper.java（服务端填充的客户端入口）、
 *   appeng/core/network/serverbound/FillCraftingGridFromRecipePacket.java（实际的填充行为）。
 * - AE2 JEI Integration by Tamaized (https://github.com/Tamaized/AE2-JEI-Integration)：
 *   transfer/UseCraftingRecipeTransfer.java、transfer/EncodePatternTransferHandler.java
 *  （JEI 处理器的结构、ctrl+点击补货交互与 IRecipeTransferError 的用法）。
 */
package io.github.lzp.ae2universalcraftingfill.jei.transfer;

import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import appeng.integration.modules.itemlists.CraftingHelper;
import appeng.menu.me.items.CraftingTermMenu;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;

/**
 * 针对 AE2 合成终端的 JEI 通用配方转移处理器：点击 JEI 配方上的 + 号，把任意
 * 非工作台类配方（模组机器配方等）的输入填进 3x3 合成格。
 *
 * <p>填充逻辑不在这里实现：客户端调用 AE2 自带的
 * {@link CraftingHelper#performTransfer}，它发送 {@code FillCraftingGridFromRecipePacket}
 * 到服务端，由 AE2 完成「清格回插 -> 网络按存量排序提取 -> 背包兜底 -> ctrl+点击
 * 顺带安排 autocraft」，与原生工作台配方转移行为完全一致。
 *
 * <p>过滤规则（按 grilling 会话确定的共识）：
 * <ul>
 * <li>工作台配方由 AE2 / AE2-JEI-Integration 的专属处理器负责，本处理器不干预；</li>
 * <li>仅处理以真实 {@link RecipeHolder} 为基础的配方显示，纯合成显示静默忽略；</li>
 * <li>配方须有至少 1 个非空输入；</li>
 * <li>输入超过 9 个时拒绝（AE2 服务端 3x3 展开的硬约束）；</li>
 * <li>黑名单内的配方类型（熔炉系、切石、锻造等）拒绝转移。</li>
 * </ul>
 */
public class CraftingTermUniversalTransferHandler<T extends CraftingTermMenu>
        implements IUniversalRecipeTransferHandler<T> {

    private static final String KEY_BLACKLISTED = "ae2universalcraftingfill.transfer.blacklisted";
    private static final String KEY_NO_INPUTS = "ae2universalcraftingfill.transfer.no_inputs";
    private static final String KEY_TOO_LARGE = "ae2universalcraftingfill.transfer.too_large";
    private static final String KEY_COUNT_WARNING = "ae2universalcraftingfill.transfer.count_warning";

    private static final int CRAFTING_GRID_SIZE = 9;

    /** 对填入 3x3 合成格没有意义的配方类型（registry key 全名）。 */
    private static final Set<String> BLACKLISTED_RECIPE_TYPES = Set.of(
            "minecraft:smelting",
            "minecraft:blasting",
            "minecraft:smoking",
            "minecraft:campfire_cooking",
            "minecraft:stonecutting",
            "minecraft:smithing_trim",
            "minecraft:smithing_transform");

    private final MenuType<T> menuType;
    private final Class<T> menuClass;
    private final IRecipeTransferHandlerHelper helper;

    public CraftingTermUniversalTransferHandler(MenuType<T> menuType, Class<T> menuClass,
            IRecipeTransferHandlerHelper helper) {
        this.menuType = menuType;
        this.menuClass = menuClass;
        this.helper = helper;
    }

    @Override
    public Class<T> getContainerClass() {
        return menuClass;
    }

    @Override
    public Optional<MenuType<T>> getMenuType() {
        return Optional.of(menuType);
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(T menu, Object recipeBase, IRecipeSlotsView recipeSlotsView,
            Player player, boolean maxTransfer, boolean doTransfer) {

        // 只处理以真实 RecipeHolder 为基础的配方显示；
        // 村民交易、刷怪笼等纯合成显示不属于本附属的支持范围，静默忽略。
        if (!(recipeBase instanceof RecipeHolder<?> holder)) {
            return null;
        }

        Recipe<?> recipe = holder.value();

        // 工作台配方由专属处理器负责，不干预
        if (recipe.getType() == RecipeType.CRAFTING) {
            return null;
        }

        if (isBlacklisted(recipe.getType())) {
            return helper.createUserErrorWithTooltip(Component.translatable(KEY_BLACKLISTED));
        }

        var ingredients = recipe.getIngredients();
        if (ingredients.isEmpty() || ingredients.stream().allMatch(Ingredient::isEmpty)) {
            return helper.createUserErrorWithTooltip(Component.translatable(KEY_NO_INPUTS));
        }

        // AE2 服务端把输入平铺成 3x3 时硬性要求 <=9 个，超出只能拒绝
        if (ingredients.size() > CRAFTING_GRID_SIZE) {
            return helper.createUserErrorWithTooltip(Component.translatable(KEY_TOO_LARGE));
        }

        // 合成格每格只能放 1 个；配方显示里出现数量 >1 的输入时照填，但提示可能不足
        boolean oversizedCounts = hasOversizedCounts(recipeSlotsView);

        if (doTransfer) {
            // ctrl+点击时缺失的材料顺带安排 autocraft（与 AE2 原生行为一致）
            CraftingHelper.performTransfer(menu, holder.id(), recipe, AbstractContainerScreen.hasControlDown());
        }

        if (oversizedCounts) {
            return new CountWarningError();
        }
        return null;
    }

    private static boolean isBlacklisted(RecipeType<?> recipeType) {
        var key = BuiltInRegistries.RECIPE_TYPE.getKey(recipeType);
        return key != null && BLACKLISTED_RECIPE_TYPES.contains(key.toString());
    }

    private static boolean hasOversizedCounts(IRecipeSlotsView recipeSlotsView) {
        for (IRecipeSlotView slotView : recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT)) {
            if (slotView.getItemStacks().anyMatch(stack -> stack.getCount() > 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 数量超限的提示：转移照常执行，只附加一个非阻塞的悬浮提示。
     */
    private record CountWarningError() implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.add(Component.translatable(KEY_COUNT_WARNING));
        }
    }
}
