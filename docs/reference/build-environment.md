# 工程环境备忘

依赖坐标、maven 仓库与核对 API 签名的方式。网络 / 代理类报错的排查见 `../troubleshooting.md`。

## 依赖坐标与 maven 仓库

- AE2 依赖坐标：`org.appliedenergistics:appliedenergistics2:19.2.17`（Maven Central）；
  JEI 19.39.0.372 在 `maven.blamejared.com` / `modmaven.dev`。

## JEI 版本锁定

JEI 锁在 **19.39.0.372**（CurseForge file id `8512040`）：19.42.0 起 JEI 要求
NeoForge ≥ 21.1.238（它用到了 `TooltipFlagExtension#shouldDisplayAllInformation`），
而本附属要支持 NeoForge 21.1.219。19.40 / 19.41 没有 1.21.1-neoforge 发布，
所以 19.39.0.372 是最后一个可用版本；升级 JEI 前必须先抬 NeoForge 下界。

## 核对依赖 API 签名

- JEI 的 API jar 也可以直接从 blamejaven maven 下载后用 `javap` 核对签名，不必依赖 GitHub API
  （匿名额度 60/h 容易耗尽；`raw.githubusercontent.com` 在本机不可靠）。
