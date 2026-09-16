# NeoForge 21.1 配置 API 事实

客户端配置注册、图形配置界面与翻译键上**已验证**（源码 + 实测日志已核）的 API 事实。

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
