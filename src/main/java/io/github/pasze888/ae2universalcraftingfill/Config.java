/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.pasze888.ae2universalcraftingfill;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端配置。
 *
 * <p>展示型显示（非 {@code RecipeHolder}）与没有物品输入的配方由代码判据直接排除，不需要配置；
 * 这里只用来排除「有真实配方对象、却仍不该填进 3x3 合成格」的配方类型。
 * 排除只影响是否显示转移按钮，是纯客户端决策，故为 CLIENT 配置。
 */
public final class Config {

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_RECIPE_TYPES;
    public static final ModConfigSpec SPEC;

    static {
        var builder = new ModConfigSpec.Builder();
        BLACKLISTED_RECIPE_TYPES = builder
                .comment("不提供转移按钮的配方名单，可写配方类型（RecipeType）注册名，也可写配方序列化器注册名。",
                        "数据包 JSON 里的 \"type\" 就是序列化器，两者不同名时写哪个都行（如 AE2CS 的",
                        "ae2cs:crystal_aggregator_recipe 与 ae2cs:crystal_aggregator_recipe_serializer）。",
                        "写错的条目会被忽略并记日志。")
                .defineListAllowEmpty("blacklisted_recipe_types", List.of(),
                        () -> "minecraft:smelting",
                        o -> o instanceof String id && ResourceLocation.tryParse(id) != null);
        SPEC = builder.build();
    }

    private Config() {
    }
}
