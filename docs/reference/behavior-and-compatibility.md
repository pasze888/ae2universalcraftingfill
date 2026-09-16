# 行为与兼容性

本附属运行时的行为事实与依赖关系，从 `README.md` 按 §7 落点表迁入。设计取舍见
`../design/universal-transfer-handler.md`，API 事实见 `ae2-api.md`、`jei-api.md`、`neoforge-config.md`、`recipe-ids.md`。

## 行为

- **物品来源**：完全复用 AE2 服务端填充逻辑——清格回插网络 → 按存量排序从 ME 网络提取 → 背包兜底；
- **Ctrl+点击**：缺失的材料顺带安排 autocraft（与 AE2 原生行为一致）；
- **过滤**：
  - 工作台配方仍走 AE2 / AE2-JEI-Integration 的专属处理器，互不干扰；
  - 仅支持以真实 `RecipeHolder` 为基础的配方显示；
  - 非配方表驱动的展示型类别（铁砧、酿造、燃料、堆肥、村民交易等）与没有物品输入的配方（纯流体等）不提供转移按钮；
  - JEI 显示槽与配方输入一一对应时，按显示堆叠数量填入（如 2x木棍 3x金锭）；
    无法可靠对齐时退化为每格 1 个并附提示。

## 兼容性

- 不依赖 AE2-JEI-Integration，可共存（JEI 专属处理器优先于 universal 处理器，注册不冲突）；
- 依赖 AE2 与 **JEI**（JEI 只在客户端强制：专用服务器不装 JEI 也能正常启动）。
