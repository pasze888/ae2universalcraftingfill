# AI 协作坑

本文件收录 AI 协作 / 会话 / 跨会话知识积累层面的坑：模型容易误判、只看代码无法发现、
必须写下来才能省掉重复查证的点。已验证的 API 事实在 `../reference/`，构建与网络坑在 `../troubleshooting.md`。

## 对齐依赖顺序，不看代码就会误判

- 方法名与直觉不一致：`getIngredients()` 的 size 不等于"配方有几个输入"，
  `getSlotViews(INPUT)` 的 size 也不等于"界面显示几个可填槽"——两处都按直觉写必然错，
  且错法是**静默降级**（不报错，只是每格填 1 个），靠日志与测试都很难发现，只能靠对齐判据本身写清。

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
