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
- 数量源在 JEI 槽的 `ItemStack` 上（本地 `JustEnoughItems` 仓库 `origin/1.21.1` 源码已核：
  `IRecipeSlotView.getIngredients(ITEM_STACK)` 直接取出 ItemStack，`RecipeSlotIngredients`
  只做可见性/循环切换，不改数量）→ `getItemStacks().mapToInt(ItemStack::getCount).max()`
  可拿到堆叠数（比 `getDisplayedItemStack()` 稳，不受循环切换影响）。
- **踩坑：对齐必须用「非空配料数量」，不能用 `getIngredients().size()`**。配方
  `getIngredients()` 常含空槽补齐（格子型/填充列表），而 JEI 只显示非空输入槽；按完整
  大小对齐会失败 → `viewCounts` 空 → `hasCounts=false` → 退回原生 `performTransfer`
  每格填 1 个（即「物品1x2/物品2x3 只各填 1 个」的 bug）。修复：`slotViews.size() ==
  nonEmptyCount` 才对齐，`buildEntries` 按非空下标取数量。
- **踩坑（已修）：对齐前必须先过滤空输入槽视图。** JEI `RecipeLayoutBuilder.addSlot`
  把空槽也无条件计入 `visibleSlots`（JEI 源码 `layout/builder/RecipeLayoutBuilder.java:65-74`），
  空槽照样出现在 `getSlotViews(INPUT)` 里；AE2CS（AE2 Crystal Science）晶能聚合器 JEI 分类
  固定 `for (i<3) addInputSlot` 留空占位（`CrystalAggregatorRecipeCategory.setRecipe:139-144`）
  → `slotViews.size()` 恒为 3、`nonEmptyCount` 为 1~2，对齐恒失败 → `hasCounts=false`
  退化到原生每格 1 个（配方数量 3/4 全部丢失，只填 1+1）。修复：先
  `filter(view -> view.getItemStacks().findAny().isPresent())` 再比数量；纯流体输入槽
  （itemStacks 空）同时被排除。
- `SizedIngredient.getItems()`（api-sources `net/neoforged/neoforge/common/crafting/
  SizedIngredient.java:138-145` 已核）返回 `copyWithCount(count)` 的栈 → 第三方 JEI 分类
  若直接 `addItemStacks(si.getItems())` 会把配方数量显示到槽位上，本附属 `extractCounts`
  即可读到堆叠数（AE2CS 晶能聚合器分类正是这么做的，数量源可靠）。
- AE2CS `CrystalAggregatorRecipe implements Recipe<ThreeItemStackRecipeInput>`，序列化器
  `ae2cs:crystal_aggregator_recipe_serializer`（input_a/b/c 为 SizedIngredient + result +
  energy_cost），`getIngredients()` 只含非空输入（拆包后无 count），JEI 用
  `RecipeType.createRecipeHolderType` 以真实 `RecipeHolder` 注册 → 本附属 universal handler
  可直接兜底该配方类别（≤9 输入、非 crafting、非黑名单）。

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

## NeoForge 21.1 配置（已核源码）

- `@Mod` 构造器可注入 `ModContainer`（NeoForge 自身的 `NeoForgeMod(IEventBus, Dist, ModContainer)`
  即同款用法；按类型注入，参数顺序无关）。
- `modContainer.registerConfig(ModConfig.Type.CLIENT, SPEC)`；
  `ModConfig.Type` 有 STARTUP / CLIENT / COMMON / SERVER 四种，文件落在
  `config/<modid>-client.toml`。
- `ModConfigSpec.Builder#defineList(...)` **要求列表非空**（内部用 `ListValueSpec.NON_EMPTY`），
  默认空列表必须用 `defineListAllowEmpty(String path, List<? extends T> defaultValue,
  Supplier<T> newElementSupplier, Predicate<Object> elementValidator)`；元素校验失败的条目
  会被丢弃并记日志（校验不等于启动失败）。
- `ResourceLocation.tryParse(String)` 返回 null 表示非法，可直接当元素校验谓词。
- `ConfigValue#get()` 带 `cachedValue` 缓存，但配置**未加载时会抛 `IllegalStateException`**
  （`getRaw` 里 `Preconditions.checkState(loadedConfig != null)`），不是返回默认值；
  客户端配置在 GUI 出现前已加载，正常路径读它没问题，真抛出来也会被 JEI 的
  `RecipeTransferService.transferRecipe` 捕获（记日志 + internal error → 按钮隐藏，不崩）。

## NeoForge 21.1 客户端配置界面（已核源码）

- 模组列表里的 Config 按钮**不会**自动可用：`ModListScreen:373` 是
  `configButton.active = IConfigScreenFactory.getForMod(selectedMod).isPresent()`，
  不注册扩展点就是灰的（`:283` 初始即 `active = false`）。注册默认界面：
  `container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)`，
  `net.neoforged.neoforge.client.gui.ConfigurationScreen` 的构造器为
  `(ModContainer mod, Screen parent)`（NeoForge 自身的 `ClientNeoForgeMod` 同款写法）。
- 主类不是 `@Mod(dist = Dist.CLIENT)` 时，注册要用 `FMLEnvironment.dist.isClient()` 包住：
  方法引用走 indy，`ConfigurationScreen` 的构造器要到该表达式首次执行时才解析，
  所以服务端不会加载到这个客户端类。

## NeoForge 21.1 配置界面的翻译键（源码 + 实测日志已核）

`ConfigurationScreen` 会请求这一族键，缺一个就露馅（标签直接显示原始键名）：
- 界面标题 `<modid>.configuration.title`（含 `%s` = 模组显示名）；
- 配置节按钮与节标题 `<modid>.configuration.section.<配置文件名：非字母数字→点、去首尾点、小写>`
  与同键 `+ ".title"`，如 `ae2universalcraftingfill.configuration.section.ae2universalcraftingfill.client.toml`；
- 值标签 `valueSpec.getTranslationKey()`，未调 `.translation(...)` 时回落 `<modid>.configuration.<path>`；
- 值提示 = 标签键 `+ ".tooltip"`：**该键不存在时用 `.comment(...)` 的文本兜底**
  （`getTooltipComponent` 里 `Component.translatableWithFallback(tooltipKey, comment)`），
  所以 `.comment()` 留着当 toml 文件注释、提示另写 `.tooltip` 键；
- 列表值的编辑按钮 = 标签键 `+ ".button"`（fallback 是 `uitext.sectiontext`，即 "Edit"）。

未翻译的键会在非生产环境触发 "Untranslated configuration keys" 开发警告，日志里会列出
**实际请求过的全部键**：`grep '"<modid>.configuration' run/logs/latest.log` 即可拿到待补键表，不必猜。

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
