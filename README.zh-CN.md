# AE2 通用合成填充 (AE2 Universal Crafting Fill)

[English](README.md) | [简体中文](README.zh-CN.md)

在 **Minecraft 1.21.1（NeoForge）** 上，点击 JEI 配方上的 **+** 号，把任意配方的输入一键填入 AE2（Applied Energistics 2）**合成终端 / 无线合成终端**的 3×3 合成格。

安装：把本模组与 AE2、JEI 一起放进 `mods/`（JEI 只需装在客户端，专用服务器不装也能正常启动）。

物品来源完全复用 AE2 服务端填充逻辑——清格回插网络 → 按存量排序从 ME 网络提取 → 背包兜底，Ctrl+点击顺带为缺失材料安排 autocraft。JEI 显示槽与配方输入能一一对应时按显示堆叠数量填入，对不上则退化为每格 1 个并附提示。非配方表驱动的展示型类别（铁砧、酿造、燃料、堆肥、村民交易等）与没有物品输入的配方不提供转移按钮。不依赖 AE2-JEI-Integration，可共存。

## 配置

客户端配置 `config/ae2universalcraftingfill-client.toml`：

```toml
# 不提供转移按钮的配方类型 / 配方序列化器名单
blacklisted_recipe_types = []
```

列入其中的配方**不再显示转移按钮**；也可在模组列表里点 **Config** 图形化修改。两种 id 都认，任一命中即排除：

- 配方类型（`RecipeType`）注册名，如 `"minecraft:smelting"`、`"ae2cs:crystal_aggregator_recipe"`；
- 配方序列化器注册名，即数据包 JSON 里的 `"type"` 字段，如 `"ae2cs:crystal_aggregator_recipe_serializer"`。

模组未必给两者起同名（原版恰好同名，AE2CS 就不一样），所以写哪个都生效。只用来排除「有真实配方对象、却仍不该填进 3×3 合成格」的配方；展示型类别与没有物品输入的配方由代码判据自动排除，不需要配置。

## 文档

- [行为与兼容性](docs/reference/behavior-and-compatibility.md)
- [设计思路](docs/design/universal-transfer-handler.md)
- [AE2 / JEI / NeoForge API 事实](docs/reference/ae2-api.md)
- [环境与构建坑](docs/troubleshooting.md)

## 许可证

LGPL-3.0-or-later（与 AE2、AE2-JEI-Integration 一致；引用代码的出处标注见各源文件头）。
