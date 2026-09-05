/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * 服务端填充逻辑改编自 Applied Energistics 2 (LGPL-3.0) 的
 * appeng/core/network/serverbound/FillCraftingGridFromRecipePacket.java：
 * https://github.com/AppliedEnergistics/Applied-Energistics-2
 * 与原实现的差异：每个槽位支持目标数量（提取 count 个、背包取 count 个、
 * autocraft 按 (槽位, 缺口数量) 安排）；格子里已装有正确物品时以其为基底
 * 续补到目标数量（原版固定 1 个，等价于「已有则保留」），其余行为保持一致。
 */
package io.github.lzp.ae2universalcraftingfill.server;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.helpers.ICraftingGridMenu;
import appeng.helpers.ICraftingGridMenu.AutoCraftEntry;
import appeng.items.storage.ViewCellItem;
import appeng.me.storage.NullInventory;
import appeng.util.prioritylist.IPartitionList;
import io.github.lzp.ae2universalcraftingfill.network.FillCraftingGridWithCountsPacket;

public final class FillWithCountsHandler {

    private FillWithCountsHandler() {
    }

    public static void handle(FillCraftingGridWithCountsPacket packet, ServerPlayer player) {
        var menu = player.containerMenu;
        if (!(menu instanceof ICraftingGridMenu cct)) {
            // 服务端可能在包处理前就关闭了菜单，这不算错误
            return;
        }

        var energy = cct.getEnergySource();
        ICraftingService craftingService;
        IStorageService storageService;
        MEStorage networkStorage;
        KeyCounter cachedStorage;

        var node = cct.getGridNode();
        if (node != null && cct.getLinkStatus().connected()) {
            craftingService = node.getGrid().getCraftingService();
            storageService = node.getGrid().getStorageService();
            networkStorage = storageService.getInventory();
            cachedStorage = storageService.getCachedInventory();
        } else {
            craftingService = null;
            storageService = null;
            networkStorage = NullInventory.of();
            cachedStorage = new KeyCounter();
        }

        var craftMatrix = cct.getCraftingMatrix();
        var filter = ViewCellItem.createItemFilter(cct.getViewCells());
        var entries = packet.entries();

        var toAutoCraft = new LinkedHashMap<AEItemKey, IntList>();
        boolean touchedGridStorage = false;

        for (var x = 0; x < craftMatrix.size(); x++) {
            var entry = x < entries.size() ? entries.get(x) : null;
            var currentItem = craftMatrix.getStackInSlot(x);

            // 本槽位最终内容：正确的旧物品作为基底续补数量，移不走的遗留物品原样保留
            var filled = ItemStack.EMPTY;

            if (!currentItem.isEmpty()) {
                boolean matches = entry != null && !entry.template().isEmpty()
                        && ItemStack.isSameItemSameComponents(currentItem, entry.template());
                if (matches) {
                    // 格子里已是需要的物品，保留并以此为基底补足数量
                    filled = currentItem;
                } else {
                    // 先移走阻塞格子的旧物品（与 AE2 一致）
                    var in = AEItemKey.of(currentItem);
                    var inserted = StorageHelper.poweredInsert(energy, networkStorage, in, currentItem.getCount(),
                            cct.getActionSource());
                    if (inserted > 0) {
                        touchedGridStorage = true;
                    }
                    if (inserted < currentItem.getCount()) {
                        currentItem = currentItem.copy();
                        currentItem.shrink((int) inserted);
                    } else {
                        currentItem = ItemStack.EMPTY;
                    }

                    // 剩余的放回玩家背包；放不下则留在格子里（与 AE2 一致，不丢物品）
                    player.getInventory().add(currentItem);
                    craftMatrix.setItemDirect(x, currentItem);
                    filled = currentItem;
                }
            }

            if (entry == null || entry.template().isEmpty() || entry.count() <= 0) {
                continue;
            }

            var desired = Math.min(entry.count(), entry.template().getMaxStackSize());

            // 槽位为空或已装着正确物品时才补足；被遗留物品占用时不动（与 AE2 的 isEmpty 门一致）
            boolean fillable = filled.isEmpty() || ItemStack.isSameItemSameComponents(filled, entry.template());
            if (fillable && filled.getCount() < desired) {
                // 按可用数量从高到低尝试从网络提取（与 AE2 的排序语义一致）
                for (var what : findBestMatchingItemStack(entry.template(), filter, cachedStorage)) {
                    var remaining = desired - filled.getCount();
                    if (remaining <= 0) {
                        break;
                    }
                    if (!filled.isEmpty() && !ItemStack.isSameItemSameComponents(filled, what.toStack(1))) {
                        // 不同组件变体不混入同一格
                        continue;
                    }
                    var extracted = StorageHelper.poweredExtraction(energy, networkStorage, what, remaining,
                            cct.getActionSource());
                    if (extracted > 0) {
                        touchedGridStorage = true;
                        if (filled.isEmpty()) {
                            filled = what.toStack((int) extracted);
                        } else {
                            filled.grow((int) extracted);
                        }
                    }
                }

                // 网络不够时从玩家背包补足
                if (filled.getCount() < desired) {
                    var fromPlayer = takeFromPlayer(cct, player, entry.template(), desired - filled.getCount());
                    if (!fromPlayer.isEmpty()) {
                        if (filled.isEmpty()) {
                            filled = fromPlayer;
                        } else {
                            filled.grow(fromPlayer.getCount());
                        }
                    }
                }
            }

            craftMatrix.setItemDirect(x, filled);

            // 仍缺的部分按缺口数量安排 autocraft（槽位内容正确时才安排）
            var missing = desired - filled.getCount();
            if (missing > 0 && fillable && packet.craftMissing() && craftingService != null) {
                var craftable = (AEItemKey) craftingService.getFuzzyCraftable(AEItemKey.of(entry.template()),
                        key -> ((AEItemKey) key).matches(entry.template()));
                if (craftable != null) {
                    // AutoCraftEntry 的数量语义 = slots 列表长度，重复槽位下标 n 次 = 该槽要 n 个
                    var slots = toAutoCraft.computeIfAbsent(craftable, k -> new IntArrayList());
                    for (var i = 0; i < missing; i++) {
                        slots.add(x);
                    }
                }
            }
        }

        // 与 AE2 一致：填充完成后同步容器槽位状态
        menu.slotsChanged(craftMatrix.toContainer());

        if (!toAutoCraft.isEmpty() && storageService != null) {
            // 修改过网格存储时先失效缓存，否则合成计划会用到过期库存
            if (touchedGridStorage) {
                storageService.invalidateCache();
            }

            // 必须最后调用，因为它会切换菜单！
            var stacks = toAutoCraft.entrySet().stream()
                    .map(e -> new AutoCraftEntry(e.getKey(), e.getValue())).toList();
            cct.startAutoCrafting(stacks);
        }
    }

    private static ItemStack takeFromPlayer(ICraftingGridMenu cct, ServerPlayer player, ItemStack template,
            int amount) {
        var playerInv = player.getInventory();
        var taken = ItemStack.EMPTY;
        for (var i = 0; i < playerInv.items.size() && taken.getCount() < amount; i++) {
            // 不要从锁定的槽位取材料（如无线终端本身）
            if (cct.isPlayerInventorySlotLocked(i)) {
                continue;
            }
            var item = playerInv.getItem(i);
            if (!item.isEmpty() && ItemStack.isSameItemSameComponents(item, template)) {
                // split 内部已按 min(amount, getCount()) 收口（ItemStack.java:315）
                var split = item.split(amount - taken.getCount());
                if (!split.isEmpty()) {
                    if (taken.isEmpty()) {
                        taken = split;
                    } else {
                        taken.grow(split.getCount());
                    }
                }
            }
        }
        return taken;
    }

    /**
     * 从网络条目中挑选与模板匹配、可用数量最多的物品（同 AE2 原实现的排序语义）。
     */
    private static List<AEItemKey> findBestMatchingItemStack(ItemStack template, IPartitionList filter,
            KeyCounter storage) {
        return storage.findFuzzy(AEItemKey.of(template), FuzzyMode.IGNORE_ALL).stream()
                .filter(e -> e.getKey() instanceof AEItemKey key && key.matches(template)
                        && (filter == null || filter.isListed(key)))
                .sorted(Comparator.comparingLong((Object2LongMap.Entry<AEKey> e) -> e.getLongValue()).reversed())
                .map(e -> (AEItemKey) e.getKey())
                .toList();
    }
}
