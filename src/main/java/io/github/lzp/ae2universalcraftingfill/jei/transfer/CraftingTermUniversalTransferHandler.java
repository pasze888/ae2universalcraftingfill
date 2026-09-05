/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * 本文件的部分组织方式与调用参考了以下 LGPL-3.0 项目，特此标注出处：
 * - Applied Energistics 2 (https://github.com/AppliedEnergistics/Applied-Energistics-2)：
 *   appeng/integration/modules/itemlists/CraftingHelper.java（服务端填充的客户端入口）、
 *   appeng/core/network/serverbound/FillCraftingGridFromRecipePacket.java（实际的填充行为）、
 *   appeng/integration/modules/itemlists/EncodingHelper.java（物品模板挑选的优先级逻辑）。
 * - AE2 JEI Integration by Tamaized (https://github.com/Tamaized/AE2-JEI-Integration)：
 *   transfer/UseCraftingRecipeTransfer.java、transfer/EncodePatternTransferHandler.java
 *  （JEI 处理器的结构、ctrl+点击补货交互与 IRecipeTransferError 的用法）。
 */
package io.github.lzp.ae2universalcraftingfill.jei.transfer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.integration.modules.itemlists.CraftingHelper;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.items.CraftingTermMenu;
import io.github.lzp.ae2universalcraftingfill.network.FillCraftingGridWithCountsPacket;
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
 * {@link CraftingHelper#performTransfer}（或直接发送
 * {@code FillCraftingGridFromRecipePacket}），由 AE2 服务端完成
 * 「清格回插 -> 网络按存量排序提取 -> 背包兜底 -> ctrl+点击顺带安排 autocraft」，
 * 与原生工作台配方转移行为完全一致。
 *
 * <p>过滤规则（按 grilling 会话确定的共识）：
 * <ul>
 * <li>工作台配方由 AE2 / AE2-JEI-Integration 的专属处理器负责，本处理器不干预；</li>
 * <li>仅处理以真实 {@link RecipeHolder} 为基础的配方显示，纯合成显示静默忽略；</li>
 * <li>配方须有至少 1 个非空输入；</li>
 * <li>输入超过 9 个时尽力而为：取前 9 个非空输入填入，并附提示；</li>
 * <li>JEI 显示槽与配方输入一一对应时，按显示堆叠数填充（如 2x木棍 3x金锭），
 * 无法可靠对齐时退化为每格 1 个并附提示；</li>
 * <li>铁砧类配方拒绝转移（黑名单）。</li>
 * </ul>
 */
public class CraftingTermUniversalTransferHandler<T extends CraftingTermMenu>
        implements IUniversalRecipeTransferHandler<T> {

    private static final String KEY_BLACKLISTED = "ae2universalcraftingfill.transfer.blacklisted";
    private static final String KEY_NO_INPUTS = "ae2universalcraftingfill.transfer.no_inputs";
    private static final String KEY_COUNT_WARNING = "ae2universalcraftingfill.transfer.count_warning";
    private static final String KEY_TRUNCATED = "ae2universalcraftingfill.transfer.truncated";

    private static final int CRAFTING_GRID_SIZE = 9;

    /** 对填入 3x3 合成格没有意义的配方类型（registry key 全名）。 */
    private static final Set<String> BLACKLISTED_RECIPE_TYPES = Set.of(
            "minecraft:anvil");

    /**
     * 与 AE2 原生 {@code EncodingHelper.ENTRY_COMPARATOR} 同语义：可合成 > 未损耗 > 存量多。
     * （原常量是包私有，这里自建一份。）
     */
    private static final Comparator<GridInventoryEntry> ENTRY_COMPARATOR = Comparator
            .comparing(GridInventoryEntry::isCraftable)
            .thenComparing(entry -> !(entry.getWhat() instanceof AEItemKey itemKey) || !itemKey.isDamaged())
            .thenComparing(GridInventoryEntry::getStoredAmount);

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
        var nonEmptyIngredients = ingredients.stream().filter(i -> !i.isEmpty()).toList();
        if (nonEmptyIngredients.isEmpty()) {
            return helper.createUserErrorWithTooltip(Component.translatable(KEY_NO_INPUTS));
        }

        // AE2 服务端按 recipeId 展开输入时要求完整列表 <=9（ensure3by3CraftingMatrix 会抛异常）；
        // 超出时降级为模板路径（recipeId=null），尽力而为：取前 9 个非空输入填入，并提示可能不完整
        boolean oversizedList = ingredients.size() > CRAFTING_GRID_SIZE;
        boolean truncated = nonEmptyIngredients.size() > CRAFTING_GRID_SIZE;

        // 数量感知：JEI 输入槽视图与配方输入一一对应时，取显示堆叠数作为每格目标数量；
        // 对应不上（无法可靠对齐）时退化为每格 1 个，仅在显示数量 >1 时提示
        var viewCounts = extractCounts(recipeSlotsView, ingredients.size());
        boolean hasCounts = viewCounts != null && viewCounts.stream().anyMatch(c -> c > 1);
        boolean countsUnknown = viewCounts == null && hasOversizedCounts(recipeSlotsView);

        if (doTransfer) {
            // ctrl+点击时缺失的材料顺带安排 autocraft（与 AE2 原生行为一致）
            boolean craftMissing = AbstractContainerScreen.hasControlDown();
            if (hasCounts) {
                // 自定义数量感知填充（同样覆盖 >9 截断）
                PacketDistributor.sendToServer(new FillCraftingGridWithCountsPacket(
                        buildEntries(menu, ingredients, viewCounts), craftMissing));
            } else if (!oversizedList) {
                CraftingHelper.performTransfer(menu, holder.id(), recipe, craftMissing);
            } else {
                PacketDistributor.sendToServer(new FillCraftingGridFromRecipePacket(null,
                        buildTemplates(menu, nonEmptyIngredients), craftMissing));
            }
        }

        if (truncated) {
            return new CosmeticWarningError(Component.translatable(KEY_TRUNCATED));
        }
        if (countsUnknown) {
            return new CosmeticWarningError(Component.translatable(KEY_COUNT_WARNING));
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
     * JEI 输入槽视图与配方输入一一对应时，取每个输入的显示堆叠数（上限 64）。
     * 对应不上时返回 null（无法可靠对齐，退化为每格 1 个）。
     */
    @Nullable
    private static List<Integer> extractCounts(IRecipeSlotsView recipeSlotsView, int ingredientCount) {
        var slotViews = recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT);
        if (slotViews.size() != ingredientCount) {
            return null;
        }
        var counts = new ArrayList<Integer>(ingredientCount);
        for (var slotView : slotViews) {
            var count = slotView.getDisplayedItemStack().map(ItemStack::getCount).orElse(1);
            counts.add(Math.max(1, Math.min(64, count)));
        }
        return counts;
    }

    /**
     * 为输入挑选合适的物品模板（优先网络存量，逻辑同 AE2
     * {@code CraftingHelper.findGoodTemplateItems}）。
     */
    private static ItemStack pickBestStack(Map<AEKey, Integer> ingredientPriorities, Ingredient ingredient) {
        return ingredientPriorities.entrySet().stream()
                .filter(e -> e.getKey() instanceof AEItemKey itemKey && itemKey.matches(ingredient))
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(e -> ((AEItemKey) e.getKey()).toStack())
                .orElse(ingredient.getItems()[0]);
    }

    /**
     * 数量感知的降级路径：为每个非空输入（最多 9 个）挑选物品模板并携带目标数量。
     */
    private static List<FillCraftingGridWithCountsPacket.Entry> buildEntries(CraftingTermMenu menu,
            List<Ingredient> ingredients, List<Integer> viewCounts) {
        var ingredientPriorities = EncodingHelper.getIngredientPriorities(menu, ENTRY_COMPARATOR);
        var entries = new ArrayList<FillCraftingGridWithCountsPacket.Entry>();
        for (int i = 0; i < ingredients.size() && entries.size() < CRAFTING_GRID_SIZE; i++) {
            var ingredient = ingredients.get(i);
            if (ingredient.isEmpty()) {
                continue;
            }
            entries.add(new FillCraftingGridWithCountsPacket.Entry(
                    pickBestStack(ingredientPriorities, ingredient), viewCounts.get(i)));
        }
        return entries;
    }

    /**
     * 输入超过 9 个时的降级路径（无数量信息时）：为前 9 个非空输入挑选物品模板，
     * 以 recipeId=null 的模板路径发送，绕开服务端 3x3 展开对完整列表 <=9 的硬约束。
     */
    private static NonNullList<ItemStack> buildTemplates(CraftingTermMenu menu, List<Ingredient> ingredients) {
        var ingredientPriorities = EncodingHelper.getIngredientPriorities(menu, ENTRY_COMPARATOR);
        var templates = NonNullList.withSize(CRAFTING_GRID_SIZE, ItemStack.EMPTY);
        int slot = 0;
        for (var ingredient : ingredients) {
            if (slot >= CRAFTING_GRID_SIZE) {
                break;
            }
            templates.set(slot++, pickBestStack(ingredientPriorities, ingredient));
        }
        return templates;
    }

    /**
     * 非阻塞的悬浮提示：转移照常执行，只附加提示文本。
     */
    private record CosmeticWarningError(Component message) implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.add(message);
        }
    }
}
