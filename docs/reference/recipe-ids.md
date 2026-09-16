# 配方 id 与 mods.toml 依赖事实

同一个配方在 1.21.1 上可能挂着多个注册名，以及 `mods.toml` 依赖 `side` 的校验行为——
两者都是**源码已核**的事实，直接影响"按 id 排除配方"和"依赖声明"两处实现。

## 同一个配方在 1.21.1 的三个 id（源码已核）

一个配方可能挂着三个互不相干的注册名，原版恰好三者同名（都是 `minecraft:smelting`），
模组各起各的名字就露馅（AE2CS）：

| 名字 | 注册表 / 出处 | 谁能看到 |
|---|---|---|
| `ae2cs:crystal_aggregator_recipe_serializer` | `Registries.RECIPE_SERIALIZER`（`AECSRecipeSerializers:32`） | 数据包 JSON 的 `"type"` 字段 |
| `ae2cs:crystal_aggregator_recipe` | `Registries.RECIPE_TYPE`（`AECSRecipeTypes`） | 只能看模组源码 |
| `ae2cs:crystal_aggregator` | JEI 的 `RecipeType.createRecipeHolderType(...)`（`CrystalAggregatorRecipeCategory:37`） | JEI 类别 / 模组源码 |

- JSON 的 `"type"` 是**序列化器**：`Recipe.CODEC = BuiltInRegistries.RECIPE_SERIALIZER.byNameCodec()
  .dispatch(Recipe::getSerializer, ...)`（`Recipe.java:17`）。
- **RecipeType 不进 JSON**：`Recipe#getType()`（`Recipe.java:68`）由 Java 类写死，
  `RecipeManager` 据此分组（`RecipeManager.java:171`）；`RecipeSerializer` 没有 getType。
- 因此一个配方类 = 一个类型 + 一个序列化器；"一个序列化器对多个类型"只能靠多个配方子类
  共用一个序列化器实例。"一个类型对多个序列化器"才是常态（`minecraft:crafting` 挂
  crafting_shaped / crafting_shapeless / crafting_special_* 一堆）。
- 实战结论：配置里按 id 排除时，**类型名和序列化器名两个都认**，否则用户从数据包 JSON
  复制来的名字（序列化器）会匹配不上；AE2CS 五个机器都是 JEI 名 + `_recipe` / `_recipe_serializer`。
- AE2CS 的熵变反应室用的是 **AE2 的** `ae2:entropy` 类型（`byType(AERecipeTypes.ENTROPY)`），
  JEI 类别名才是 `ae2cs:entropy_variation_reaction_chamber`；`ae2cs:crystal_growth` 是纯展示
  （`RecipeType.create("ae2cs","crystal_growth", CrystalSeedItem.class)`，元素非 RecipeHolder）。

## mods.toml 依赖的 side 是真会被校验的（字节码已核）

- `ModSorter.verifyDependencyVersions` 里对 `IModInfo.ModVersion` 的过滤同时用两个谓词：
  `getType() == DependencyType.REQUIRED` 与 `getSide().isCorrectSide()`；后者按
  `IModInfo.DependencySide`（内部就是一组 `Dist`，`isContained(Dist)`）判断。
  → `type="required"` + `side="CLIENT"` 只在客户端强制，专用服务器缺该模组照样启动。
- JEI 自己的 mods.toml 带 `displayTest="IGNORE_SERVER_VERSION"`（注释：让客户端能加入
  没装 JEI 的服务器）→ 专用服务器没有 JEI 是官方认可的正常情形，所以给 JEI 写
  `side="BOTH"` 的 required 会误伤服务器（AE2-JEI-Integration 就是这么写的）。
