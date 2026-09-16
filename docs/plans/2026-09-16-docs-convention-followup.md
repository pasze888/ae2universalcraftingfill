# 文档结构迁移与后续整理

日期：2026-09-16

## 本次做了什么

按工作区 `AGENTS.md` §7.2 的文档落点表，把仓库里两个「什么都装」的文档拆到各自该在的位置，
正文逐字保留，只调整标题层级、相对链接与必要的过渡句。

| 旧 | 新 | 处理方式 |
|---|---|---|
| `docs/DESIGN.md` | `docs/design/universal-transfer-handler.md` | `git mv` 整体搬家（保留历史） |
| `docs/KNOWLEDGE.md` | `docs/reference/ae2-api.md` | `git mv` 后重写为「AE2 API 事实」 |
| 同上 | `docs/reference/jei-api.md` | 拆出 JEI API 事实 |
| 同上 | `docs/reference/neoforge-config.md` | 拆出 NeoForge 配置 / 配置界面 / 翻译键事实 |
| 同上 | `docs/reference/recipe-ids.md` | 拆出「配方三个 id」与「mods.toml 依赖 side」 |
| 同上 | `docs/reference/build-environment.md` | 拆出依赖坐标、maven 仓库、`javap` 核对签名 |
| 同上 | `docs/troubleshooting.md` | 拆出 Gradle 直连 maven TLS / 代理与离线核对签名两个构建坑 |
| 同上 | `docs/ai/gotchas.md` | 拆出跨会话知识积累坑与两处「对齐」踩坑 |

引用修正：`docs/design/universal-transfer-handler.md` 里两处 `KNOWLEDGE.md` 指针改为新的 reference / ai 路径。
README 未改正文（仅语言段落见待办）。提交前用脚本核对过：原 `KNOWLEDGE.md` 的每一行、每个行内代码片段
都能在新文件里找到，无内容丢失。

## 待办

- [x] **中文 README**（2026-09-16 完成）：`README.md` 拆成英文源与 `README.zh-CN.md` 中文同步，
      顶部语言切换改为 `[English](README.md) | [简体中文](README.zh-CN.md)`；两份按 §7.3 逐项核对
      标题层级、顺序、代码块数量、链接路径一致。
- [x] **README 瘦身**（2026-09-16 完成）：「背景」并入 `docs/design/universal-transfer-handler.md` §1，
      「行为」「兼容性」迁入 `docs/reference/behavior-and-compatibility.md`；README 只留简介、安装、
      配置、文档、协议，中英两份同步。
- [x] **英文 README 待补**（2026-09-16 完成）：英文节已补齐「配置界面图形化修改」与行为概述，
      与中文节逐项对齐。
- [ ] **项目内 `AGENTS.md`**：本仓库没有；若后续要在 §7 之外补项目特有约定，再新建。
