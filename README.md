# AE2 Universal Crafting Fill

[English below](#english)

## 中文

在 **Minecraft 1.21.1（NeoForge）** 上，点击 JEI 配方上的 **+** 号，把任意配方的输入一键填入 AE2（Applied Energistics 2）**合成终端 / 无线合成终端**的 3×3 合成格。

### 背景

AE2 在 1.20.x 及 26.x 自带「工作台配方 → 合成终端」的 JEI 转移，但 1.21.x 一代砍掉了自带的 JEI 支持，社区由 [AE2-JEI-Integration](https://github.com/Tamaized/AE2-JEI-Integration) 补位——它也只处理原版工作台配方（`minecraft:crafting`）。模组机器配方（如神秘农业祭坛）点 + 号毫无反应。本附属补上这块缺口：注册 JEI universal 配方转移处理器，兜底接管所有没有专属处理器的配方类别。

### 行为

- **物品来源**：完全复用 AE2 服务端填充逻辑——清格回插网络 → 按存量排序从 ME 网络提取 → 背包兜底；
- **Ctrl+点击**：缺失的材料顺带安排 autocraft（与 AE2 原生行为一致）；
- **过滤**：
  - 工作台配方仍走 AE2 / AE2-JEI-Integration 的专属处理器，互不干扰；
  - 仅支持以真实 `RecipeHolder` 为基础的配方显示；
  - 非配方表驱动的展示型类别（铁砧、酿造、燃料、堆肥、村民交易等）与没有物品输入的配方（纯流体等）不提供转移按钮；
  - JEI 显示槽与配方输入一一对应时，按显示堆叠数量填入（如 2x木棍 3x金锭）；
    无法可靠对齐时退化为每格 1 个并附提示。

### 兼容性

- 不依赖 AE2-JEI-Integration，可共存（JEI 专属处理器优先于 universal 处理器，注册不冲突）；
- 依赖 AE2 与 **JEI**（JEI 只在客户端强制：专用服务器不装 JEI 也能正常启动）。

### 配置

客户端配置 `config/ae2universalcraftingfill-client.toml`：

```toml
# 不提供转移按钮的配方类型 / 配方序列化器名单
blacklisted_recipe_types = []
```

列入其中的配方**不再显示转移按钮**；也可在模组列表里点 **Config** 图形化修改。两种 id 都认，任一命中即排除：

- 配方类型（`RecipeType`）注册名，如 `"minecraft:smelting"`、`"ae2cs:crystal_aggregator_recipe"`；
- 配方序列化器注册名，即数据包 JSON 里的 `"type"` 字段，如 `"ae2cs:crystal_aggregator_recipe_serializer"`。

模组未必给两者起同名（原版恰好同名，AE2CS 就不一样），所以写哪个都生效。只用来排除「有真实配方对象、却仍不该填进 3×3 合成格」的配方；展示型类别与没有物品输入的配方由代码判据自动排除，不需要配置。

### 协议

LGPL-3.0-or-later（与 AE2、AE2-JEI-Integration 一致；引用代码的出处标注见各源文件头）。

## English

Click the **+** button on any JEI recipe to fill the AE2 (Applied Energistics 2) **Crafting Terminal / Wireless Crafting Terminal** 3×3 grid with its ingredients, on **Minecraft 1.21.1 (NeoForge)**.

AE2 ships JEI recipe transfer for vanilla crafting recipes on 1.20.x and 26.x, but not on 1.21.x — the community-maintained [AE2-JEI-Integration](https://github.com/Tamaized/AE2-JEI-Integration) fills that gap, yet it only handles `minecraft:crafting` recipes. This addon registers a JEI universal recipe transfer handler for AE2's crafting terminals, covering every recipe category that has no dedicated handler (modded machine recipes, etc.).

Item sourcing reuses AE2's own server-side fill logic: return grid contents to the network, extract from ME storage sorted by availability, fall back to the player inventory, and Ctrl+click additionally schedules autocrafting for missing items. Ingredient amounts shown by JEI are respected when the recipe slots can be matched up; otherwise one item per slot is filled with a notice. Display-only categories that are not backed by a recipe (anvil, brewing, fuel, composting, villager trades) get no transfer button, and neither do recipes without item inputs (fluid-only recipes, etc.). Requires AE2 and JEI (JEI on the client only, so dedicated servers can run without it).

License: LGPL-3.0-or-later.

### Config

Client config `config/ae2universalcraftingfill-client.toml`:

```toml
# Recipe types / recipe serializers that get no transfer button
blacklisted_recipe_types = []
```

Anything listed there gets **no transfer button**; it can also be edited graphically through the **Config** button in the mod list. Both kinds of id are accepted, and matching either one is enough:

- the recipe type (`RecipeType`) id, e.g. `"minecraft:smelting"`, `"ae2cs:crystal_aggregator_recipe"`;
- the recipe serializer id, i.e. the `"type"` field in a datapack JSON, e.g. `"ae2cs:crystal_aggregator_recipe_serializer"`.

Mods do not always give both the same name (vanilla happens to, AE2CS does not), so either spelling works. Meant only for recipes that have a real recipe object but still should not be filled into the 3×3 grid; display-only categories and recipes without item inputs are excluded automatically and need no config.
