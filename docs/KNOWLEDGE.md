# KNOWLEDGE.md

本文件记录开发过程中**已验证**（编译通过/源码确认）的 API 事实与踩坑，供后续会话减少重复查证。

## AE2 1.21.1（19.2.x）相关

- AE2 1.21.x 不自带 JEI 支持；`appeng.integration.modules.itemlists` 是保留的内部模块，
  发布 jar 里可直接 import（`CraftingHelper` / `TransferHelper` / `EncodingHelper`），无需 AT/Mixin。
- `appeng.integration.modules.itemlists.CraftingHelper.performTransfer(CraftingTermMenu, @Nullable ResourceLocation, Recipe<?>, boolean)`
  **吃任意 `Recipe<?>`**，内部发送 `FillCraftingGridFromRecipePacket`；末位参数为「缺失材料安排 autocraft」（ctrl+点击）。
- `FillCraftingGridFromRecipePacket.handleOnServer` 是配方类型无关的：
  按 recipeId 解析任意配方 → `CraftingRecipeUtil.ensure3by3CraftingMatrix`（**>9 输入抛 IllegalArgumentException**，
  客户端必须先拦）→ 清格回插网络/背包 → `findBestMatchingItemStack` 按网络存量降序提取 → 背包兜底。
  服务端手动发包时 `ingredientTemplates` 尺寸必须为 9（有 `Preconditions.checkArgument`）。
- `appeng.menu.me.items.WirelessCraftingTermMenu extends CraftingTermMenu`（1.21.1），
  泛型上限取 `CraftingTermMenu` 可同时覆盖两者。
- `BuiltInRegistries.RECIPE_TYPE.getKey(RecipeType<?>)` 可取配方类型 registry key（1.21.1）。

## JEI 19.27.0.335（1.21.1）相关

- `IUniversalRecipeTransferHandler<C>` 三个方法：`getContainerClass()` / `getMenuType()`（返回 `Optional`）/
  `transferRecipe(C, Object, IRecipeSlotsView, Player, boolean, boolean)`，返回 `@Nullable IRecipeTransferError`。
- JEI 查找顺序：**专属 handler（按类别）优先于 universal handler** → universal 只兜底，
  与 AE2-JEI-Integration 注册的 `RecipeTypes.CRAFTING` 专属 handler 共存无冲突。
- `IRecipeTransferRegistration.getTransferHelper()` 返回 `IRecipeTransferHandlerHelper`；
  `addUniversalRecipeTransferHandler(IUniversalRecipeTransferHandler<C>)` 泛型只要求 `AbstractContainerMenu`。
- `IRecipeTransferError`：`Type.COSMETIC` 可在转移成功后附加非阻塞提示；
  默认 `getTooltip(ITooltipBuilder)` 委托给旧版 `getTooltip(): List<Component>`，
  旧版 `getTooltip()` 已标记 for removal——应覆写 `getTooltip(ITooltipBuilder)`（`add(FormattedText)`）。
- `IRecipeSlotView.getItemStacks()`（default）返回 `Stream<ItemStack>`，可用来检测显示数量 >1。
- JEI 插件发现方式：`@JeiPlugin` 注解 + `IModPlugin`，无需额外 json；`getPluginUid()` 为 abstract 必须实现。
- AE2-JEI-Integration（Tamaized）里 `UseCraftingRecipeTransfer` 直接引用 `AbstractContainerScreen.hasControlDown()`
  也能在专用服务器上正常工作——JEI 对非 basic 的自定义 handler 只在客户端调用 `transferRecipe`。

## 工程环境备忘

- AE2 依赖坐标：`org.appliedenergistics:appliedenergistics2:19.2.17`（Maven Central）；
  JEI 19.27.0.335 在 `maven.blamejared.com` / `modmaven.dev`。
- `maven.blamejared.com` 用 curl（走系统代理）能下，但 Gradle 直连 TLS 握手失败；
  Clash 未开 TUN 时需给 Gradle 配 `systemProp.https.proxyHost=127.0.0.1 / proxyPort=7897`（临时配置，勿提交）。
- JEI 的 API jar 也可以直接从 blamejaven maven 下载后用 `javap` 核对签名，不必依赖 GitHub API
  （匿名额度 60/h 容易耗尽；`raw.githubusercontent.com` 在本机不可靠）。
