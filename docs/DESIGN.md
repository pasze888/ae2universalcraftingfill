# 设计思路

记录本项目为什么长成现在这样。行为说明见 `README.md`，API 事实与踩坑见 `KNOWLEDGE.md`，本文只讲决策与取舍。

## 1. 缺口在哪

AE2 1.21.x 不带 JEI 转移；AE2-JEI-Integration 补位，但只注册了 `minecraft:crafting` 的**专属**处理器。
JEI 的查找顺序是「专属 handler 优先，universal handler 兜底」，所以「没有专属处理器的配方类别」是空的——
模组机器配方（祭坛、晶能聚合器…）、甚至是需要堆叠输入的原版工作台配方，点 + 号要么没反应，要么只填 1 个。

**这就是本项目唯一要填的缺口**：注册一个 universal 处理器接管合成格，且只接管没人管的类别。
不修改 AE2、不抢占既有处理器，是选择「附属」而非「补丁」的全部理由——缺口本身是注册缺失，不是行为错误。

## 2. 分层：判定在客户端，改格在服务端

填充要动 ME 网络、要扣能量、要触发 autocraft，必须在服务端做；而配方与 JEI 显示只存在于客户端。
所以自然的切分是：

```
客户端  CraftingTermUniversalTransferHandler   过滤 + 提取数量 + 对齐 → 组包
  ↓    FillCraftingGridWithCountsPacket         每格「模板 + 目标数量」
服务端  FillWithCountsHandler                   清格回插 → 提取 → 背包兜底 → 安排 autocraft
```

客户端只做**判定**与**翻译**，不落任何物品状态；服务端不信任包里的数量（见 §5）。
过滤器放在客户端也是被迫的：AE2 的 `ensure3by3CraftingMatrix` 遇到 >9 输入直接抛 `IllegalArgumentException`，
必须在发包前拦住，否则崩服务端。

## 3. 为什么必须自建网络包

AE2 自带 `FillCraftingGridFromRecipePacket` 把数量硬编码成 1：`poweredExtraction(..., 1, ...)`、`split(1)`。
堆叠数量只活在 JEI 的显示里，原包没有任何表达途径。

所以自建包沿用原包的字段（`ingredientTemplates` + `craftMissing`），只在每个槽位后追加一个 `int count`。
这不是「重新发明」，而是**给原包补一个缺失字段**：服务端处理逻辑逐槽照搬 AE2 的语义（清格 → 回插网络 →
余量回背包 → 放不下就留在格子），只在「提取几个、背包取几个、autocraft 几个」三处把常量 1 换成目标数量。

代价是服务端代码与 AE2 内部实现耦合（`ICraftingGridMenu`、`StorageHelper`、`ViewCellItem`…）。
这个代价是可接受的：这些 API 全是公开的，无需 AT/Mixin；而另一种做法——改 AE2 原包——会让附属变成
「必须给 AE2 打补丁」，与 §1 的选择矛盾。

## 4. 两条发送路径，而不是一条

数量为 1、且输入 ≤9 时，自建包与 AE2 的 `recipeId=null` 模板包在逐槽语义上是等价的
（匹配判定 `isSameItemSameComponents` ≙ `Ingredient.of(template).test`，提取/背包/autocraft 同构）。
既然等价，就没必要单独保留一条「模板路径」——那只是多一份要维护的平行实现。

于是客户端只有两条分支：

```java
if (hasCounts || oversizedList) 自建包   // 需要数量，或需要绕过 >9 异常
else CraftingHelper.performTransfer(...) // 都能表达，走 AE2 原生
```

**能交给 AE2 的就交给 AE2**：减少自建包的使用面，也就是减少与 AE2 内部实现耦合的面。

## 5. 对齐：本项目最脆弱的一环

数量来自 JEI 槽显示的 `ItemStack.getCount()`，而服务端需要知道「第 i 个非空配料要几个」。
两端没有共享的标识，只能靠**顺序对齐**。整个设计的复杂度几乎都堆在这里。

对齐规则是 `槽视图数 == 非空配料数`。两个约束都是踩坑换来的（详见 `KNOWLEDGE.md`）：

- 要用**非空**配料数，不能用 `getIngredients().size()`：配方常带空槽补齐，JEI 只显示非空槽；
- 比之前要**先滤掉空槽视图**：JEI 的 `addSlot` 把空占位槽也算进 `visibleSlots`，
  AE2CS 晶能聚合器固定 add 3 个输入槽留空，不过滤则恒不对齐。

对不上时的策略是**降级而非拒绝**：退化为每格 1 个并附非阻塞提示（`Type.COSMETIC`）。
理由是「填 1 个」在多数配方下仍是有用的起点，而拒绝转移等于把功能拿掉。
同理，>9 输入取前 9 个 + 提示——尽力而为优于什么都不做。

## 6. 服务端的三条硬约束

包来自客户端，处理逻辑必须自己兜住：

1. **不丢物品**。移格时旧物品先回插网络，余量进玩家背包，都放不下就留在格子里（AE2 原实现同款）。
   只有「槽位为空 或 已是目标模板」时才补足，避免从空栈无条件提取覆盖残留物——这一步做错就是复制/丢失 bug。
2. **数量收口**。每格目标量 `min(count, maxStackSize)`；包内条目数 >9 直接视为损坏包抛错；不取玩家锁定的槽位（如无线终端本身）。
3. **autocraft 只在内容正确时安排**，且缓存失效要在 `startAutoCrafting` 之前——它会导致切换菜单。

## 7. 明确不做的事

- **工作台配方**：AE2-JEI-Integration 的专属处理器已覆盖，本附属不干预（否则是重复实现 + 冲突）；
- **样板终端（PatternEncodingTermMenu）**：它不继承 `CraftingTermMenu`，是另一块界面；
  其缺口已被 AE2-JEI-Integration 的 universal `EncodePatternTransferHandler` 覆盖，再加一份会重叠；
- **铁砧、酿造、燃料、堆肥、村民交易等展示型类别**：JEI 在场自造对象，没有配方表数据也就没东西可填；
  不列黑名单（负列表永远不完备），改用正判据 `recipeBase instanceof RecipeHolder`；
- **没有物品输入的配方**（纯流体等）：有真实配方对象，但同样没有可搬进合成格的东西，
  与上一条同归为“无可填内容”；

上面两条在实现里是同一条规则：**摆不出“填”这个动作就不摆按钮**——返回 internal error
让 JEI 隐藏转移按钮（`Type.INTERNAL` 在 `RecipeTransferButtonController.updateStateForTransferError`
里就是 `setVisible(false)`），界面与未安装本附属时一致。反例是“填得到但不完整”的
过度输入/数量未知，那两种仍然给按钮，只附非阻塞提示。

判断标准统一是：**别人已经做了的不做，做了没意义的不做，没有数据的不摆假按钮**。
