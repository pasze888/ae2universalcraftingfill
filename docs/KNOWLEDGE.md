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

## AE2 数量感知填充（自建包）踩坑与 API 事实

- AE2 原生 `FillCraftingGridFromRecipePacket` 每格硬编码填 1 个（`poweredExtraction(...,1,...)`、
  `split(1)`），堆叠数量只存在于 JEI 显示里 → 需要数量感知时只能自建包。
- 服务端复刻填充所需的全套 API 都是公开的：`ICraftingGridMenu`（getEnergySource/getGridNode/
  getCraftingMatrix→`InternalInventory`/getViewCells/isPlayerInventorySlotLocked/startAutoCrafting）、
  `StorageHelper.poweredInsert/poweredExtraction`、`NullInventory.of()`、`ViewCellItem.createItemFilter`。
- `AutoCraftEntry(key, slots)` 的 autocraft 数量 = `slots.size()`（`CraftConfirmMenu.planJob(what,
  slots.size(), CRAFT_LESS)`），要表达「该槽要 n 个」就重复槽位下标 n 次。
- NeoForge 21.1：`@EventBusSubscriber(bus = Bus.MOD)` 已弃用 → 在 `@Mod` 构造器注入 `IEventBus`
  后 `modEventBus.addListener(...)`（同 AE2 `AppEngBase`）。
- `KeyCounter.findFuzzy` 返回 `Collection<Object2LongMap.Entry<AEKey>>`（数量在 entry 上，
  排序要在 map 成 `AEItemKey` 之前）；`AEItemKey.matches` 只有 `ItemStack` / `Ingredient`
  两个重载，没有 `matches(AEItemKey)`。
- Java record：自定义 canonical constructor 不能是 private（否则外部无法 new）；
  循环变量在 lambda 里用需先赋给 final 局部变量。
- 数据包注册：`RegisterPayloadHandlersEvent` + `registrar.playToServer(TYPE, CODEC, handler)`，
  codec 用 `RegistryFriendlyByteBuf` 的 `StreamCodec.ofMember`。
- `FillCraftingGridFromRecipePacket.handleOnServer` 逐槽语义（源自本地 `Applied-Energistics-2`
  仓库 `origin/1.21.1` 分支源码）：匹配 → `continue` 保留不补数；不匹配 → `poweredInsert`
  回插、余量 `player.getInventory().add()`、**放不下则留在格子**；提取/背包/autocraft 全部以
  `currentItem.isEmpty()` 为门。自建数量版若从空栈无条件提取会覆盖残留 → 丢物品；须以
  「槽位内容为空或匹配模板」作 fillable 门，且 autocraft 只在内容正确时安排。
- 工作区根有 `Applied-Energistics-2` / `AE2-JEI-Integration` 源码 checkout（AE2 main 在 26.x），
  读历史版本源码用 `git -C Applied-Energistics-2 show origin/1.21.1:<path>`，不必切分支；
  本地标签未必齐全，分支引用可用。
- `ItemStack.OPTIONAL_STREAM_CODEC`（1.21.1，api-sources 已核）即 AE2 原包的模板编解码，
  空栈编码为空，可复用。
- `Inventory.add(ItemStack)`（1.21.1，api-sources Inventory.java:252-288 已核）**会收缩传入栈**：
  L277 `stack.setCount(addResource(stack))` 循环吸收、受损物品 L263 `copyAndClear()` 清空源栈。
  故「add 后按 isEmpty 决定留格」不会复制物品（AE2 原包同款写法）。
- `ItemStack.split(int)`（api-sources ItemStack.java:315 已核）内部 `Math.min(amount, getCount())`
  收口，调用侧无需再钳位。
- 自建数量包与 AE2 `recipeId=null` 模板包在 count=1 时逐槽语义等价（匹配判定
  `isSameItemSameComponents` ≙ `Ingredient.of(template).test`，提取/背包/autocraft 同构），
  故客户端发送路径只需两条：`hasCounts || oversizedList` → 自建包，否则
  `CraftingHelper.performTransfer`。

## 范围：样板终端（PatternEncodingTermMenu）不纳入本附属

- `PatternEncodingTermMenu extends MEStorageMenu`（不是 `CraftingTermMenu`），自有 `TYPE`，
  与合成格是两块不同界面；`EncodingHelper.encodeCraftingRecipe(menu, @Nullable RecipeHolder<?>,
  List<List<GenericStack>>, Predicate<ItemStack>)` 与 `encodeProcessingRecipe(menu,
  List<List<GenericStack>>, List<GenericStack>)`、`isSupportedCraftingRecipe(Recipe)` 均为
  `public static`，技术上可参考实现一个样板终端 universal 处理器。
- 但 AE2-JEI-Integration 的 `JEIPlugin.registerRecipeTransferHandlers` 已注册
  `EncodePatternTransferHandler`（universal，全部配方类别）→ `PatternEncodingTermMenu`，
  样本终端缺口已被它覆盖。再在本附属加会与之重叠/冲突。
- 本附属**只做合成格填充**（CraftingTermMenu / WirelessCraftingTermMenu）的
  「非工作台配方 + 数量感知」缺口；AE2-JEI-Integration 的合成格专属处理器只处理
  `minecraft:crafting`。两侧互补、无重叠。
- 识别方式：AE2-JEI-Integration 用 `UseCraftingRecipeTransfer`（专属，RecipeTypes.CRAFTING）
  处理合成格原版配方；`EncodePatternTransferHandler`（universal）处理样板终端。
