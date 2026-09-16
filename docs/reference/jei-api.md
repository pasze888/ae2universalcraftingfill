# JEI API 事实

JEI（Just Enough Items）19.27.0.335 / 1.21.1 上**已验证**（编译通过 / 源码确认）的 API 签名与查找顺序事实。
数量对齐的踩坑见 `../ai/gotchas.md`。

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
