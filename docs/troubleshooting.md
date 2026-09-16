# 故障排查（环境 / 构建 / 工具链）

本文件收录环境、构建、网络与工具链层面的坑。依赖坐标与可用仓库见 `reference/build-environment.md`。

## Gradle 直连 maven.blamejared.com TLS 握手失败

- `maven.blamejared.com` 用 curl（走系统代理）能下，但 Gradle 直连 TLS 握手失败；
  Clash 未开 TUN 时需给 Gradle 配 `systemProp.https.proxyHost=127.0.0.1 / proxyPort=7897`（临时配置，勿提交）。

## 不依赖 GitHub API 核对依赖签名

- JEI 的 API jar 也可以直接从 blamejaven maven 下载后用 `javap` 核对签名，不必依赖 GitHub API
  （匿名额度 60/h 容易耗尽；`raw.githubusercontent.com` 在本机不可靠）。
