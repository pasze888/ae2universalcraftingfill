/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * 服务端处理逻辑改编自 Applied Energistics 2 (LGPL-3.0) 的
 * appeng/core/network/serverbound/FillCraftingGridFromRecipePacket.java：
 * https://github.com/AppliedEnergistics/Applied-Energistics-2
 * 原 packet 每格固定填充 1 个物品（为原版合成格设计）；本包为每个输入槽
 * 额外携带目标数量，供需要堆叠输入的非工作台配方（如祭坛 2x木棍 3x金锭）使用。
 */
package io.github.pasze888.ae2universalcraftingfill.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import io.github.pasze888.ae2universalcraftingfill.AE2UniversalCraftingFill;
import io.github.pasze888.ae2universalcraftingfill.server.FillWithCountsHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 按「每格目标数量」填充 AE2 合成格的自定义服务端包。
 *
 * @param entries      每个合成格槽位的期望物品模板与数量（按槽位顺序，可为空）
 * @param craftMissing 缺失物品是否顺带安排 autocraft（ctrl+点击）
 */
public record FillCraftingGridWithCountsPacket(List<Entry> entries, boolean craftMissing)
        implements CustomPacketPayload {

    public static final Type<FillCraftingGridWithCountsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2UniversalCraftingFill.MODID, "fill_crafting_grid_with_counts"));

    public record Entry(ItemStack template, int count) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FillCraftingGridWithCountsPacket> STREAM_CODEC = StreamCodec
            .ofMember(FillCraftingGridWithCountsPacket::write,
                    FillCraftingGridWithCountsPacket::decode);

    private static FillCraftingGridWithCountsPacket decode(RegistryFriendlyByteBuf buf) {
        var size = buf.readInt();
        // 合成格固定 3x3，超界视为恶意/损坏的包（同 AE2 原包对模板数量的校验思路）
        if (size < 0 || size > 9) {
            throw new IllegalArgumentException("Invalid entry count: " + size);
        }
        var builder = new java.util.ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) {
            var template = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            var count = buf.readInt();
            builder.add(new Entry(template, count));
        }
        var craftMissing = buf.readBoolean();
        return new FillCraftingGridWithCountsPacket(builder, craftMissing);
    }

    private static void write(FillCraftingGridWithCountsPacket packet, RegistryFriendlyByteBuf buf) {
        buf.writeInt(packet.entries.size());
        for (var entry : packet.entries) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.template());
            buf.writeInt(entry.count());
        }
        buf.writeBoolean(packet.craftMissing);
    }

    public static void handleOnServer(FillCraftingGridWithCountsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                FillWithCountsHandler.handle(packet, serverPlayer);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
