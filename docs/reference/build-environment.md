# 工程环境备忘

依赖坐标、maven 仓库与核对 API 签名的方式。网络 / 代理类报错的排查见 `../troubleshooting.md`。

## 依赖坐标与 maven 仓库

- AE2 依赖坐标：`org.appliedenergistics:appliedenergistics2:19.2.17`（Maven Central）；
  JEI 19.27.0.335 在 `maven.blamejared.com` / `modmaven.dev`。

## 核对依赖 API 签名

- JEI 的 API jar 也可以直接从 blamejaven maven 下载后用 `javap` 核对签名，不必依赖 GitHub API
  （匿名额度 60/h 容易耗尽；`raw.githubusercontent.com` 在本机不可靠）。
