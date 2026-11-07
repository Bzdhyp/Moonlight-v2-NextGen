# Moonlight 双加载器模组开发文档

Moonlight 是一个 Minecraft 26.3 模组，同一份源码同时构建 **Fabric** 和 **NeoForge** 两个版本。工程结构参考 [stonecutter-template-multiloader](https://github.com/stonecutter-versioning/stonecutter-template-multiloader)，用 [Stonecutter](https://stonecutter.kikugie.dev/) 在一份 `src/` 里管理两个加载器的差异。

本文说明工程怎么组织、代码该写在哪、怎么构建和调试，以及怎样避免同一个功能写两份。

---

## 1. 版本一览

| 组件 | 版本 | 在哪里改 |
|---|---|---|
| Minecraft | 26.3 | `settings.gradle` |
| Java | 25 | `build.*.gradle`、`gradle/gradle-daemon-jvm.properties` |
| Gradle | 9.8.2（腾讯云镜像） | `gradle/wrapper/gradle-wrapper.properties` |
| Stonecutter | 0.9.8 | `settings.gradle` |
| Fabric Loader | 0.20.1 | `stonecutter.properties.toml` |
| Fabric API | 0.165.2+26.3 | `stonecutter.properties.toml` |
| Fabric Loom | 1.19.3 | `build.fabric.gradle` |
| NeoForge | 26.3.0.101 | `stonecutter.properties.toml` |
| ModDevGradle | 2.0.148 | `build.neoforge.gradle` |
| idea-ext | 1.2 | `stonecutter.gradle` |
| DevAuth（仅开发环境） | 1.2.2 | `stonecutter.properties.toml` |
| imgui-java | 1.92.7.1 | `stonecutter.properties.toml` |

26.3 是非混淆版本：Fabric 和 NeoForge 看到的都是 Mojang 原名的 Minecraft 类，不需要映射表，也不需要 refmap。

---

## 2. 目录结构

```
Moonlight-v2-NextGen/
├─ settings.gradle               定义 Stonecutter 节点（26.3-fabric / 26.3-neoforge）
├─ stonecutter.gradle            根项目（控制器）：当前激活节点、条件常量、swap
├─ stonecutter.properties.toml   模组信息和依赖版本，按加载器分表
├─ build.fabric.gradle           Fabric 节点的构建脚本（Loom）
├─ build.neoforge.gradle         NeoForge 节点的构建脚本（ModDevGradle）
├─ gradle.properties             Gradle 自身选项
├─ gradle/                       wrapper 和守护进程 JVM 要求
├─ src/main/java/wtf/moonlight/
│  ├─ Moonlight.java             通用入口：MOD_ID、LOGGER、init()、id()
│  ├─ MoonlightClient.java       客户端入口：init()，创建绑定和命令系统
│  ├─ platform/Platform.java     加载器差异的唯一出口
│  ├─ fabric/                    Fabric 入口类（只在 fabric 节点编译）
│  ├─ neoforge/                  NeoForge 入口类（只在 neoforge 节点编译）
│  ├─ event/                     事件系统（见 5.3）
│  ├─ command/                   聊天命令（见 5.4）
│  ├─ bind/                      按键绑定（见 5.5）
│  ├─ utils/                     工具类（见 5.6）
│  ├─ module/                    模块系统，模块在 impl.<分类> 下（见 5.7）
│  ├─ config/                    配置：手动 / 自动保存（见 5.8）
│  ├─ value/                     设置项（见 5.9）
│  ├─ rotation/                  旋转：发给服务器的视角、平滑、移动修复（见 5.11）
│  ├─ ui/                        界面：imgui（ImGui 接入）、clickgui（ClickGUI 绘制，见 5.10）
│  └─ mixin/                     Mixin，两个加载器共用
├─ src/main/resources/
│  ├─ fabric.mod.json            只打进 Fabric 包
│  ├─ META-INF/neoforge.mods.toml 只打进 NeoForge 包
│  ├─ moonlight.mixins.json      两边共用
│  └─ moonlight.ct               Fabric 的 class tweaker（访问扩展），只打进 Fabric 包
├─ src/preview/java/             不启动游戏的 UI 预览窗口，只在 Fabric 节点编译，不进 jar（见 8.6）
├─ versions/26.3-fabric/         Stonecutter 自动创建的子项目目录，产物在 build/ 下
├─ versions/26.3-neoforge/
├─ run/                          两个加载器共用的游戏运行目录（不进 git）
└─ doc/                          本文档
```

---

## 3. 环境准备

1. **JDK 25**。Loom 1.19 要求 Gradle 本身跑在 Java 25 上，不只是编译目标是 25。
2. **IDEA 设置**（Settings → Build, Execution, Deployment → Build Tools → Gradle）：
   - **Gradle JVM** 选 JDK 25，或者选 *Daemon JVM criteria*（项目已写 `toolchainVersion=25`）。
   - **Project Structure → SDK** 也设成 25。
3. **网络**：依赖来自 `maven.fabricmc.net`、`maven.neoforged.net`、`maven.kikugie.dev`，国内直连可能很慢。代理写在自己的 `GRADLE_USER_HOME/gradle.properties` 里（地址和端口按自己的代理改），**不要写进项目**：
   ```properties
   systemProp.https.proxyHost=127.0.0.1
   systemProp.https.proxyPort=7897
   systemProp.http.proxyHost=127.0.0.1
   systemProp.http.proxyPort=7897
   ```
4. **第一次同步比较重**：要下载 Gradle 和两套依赖，NeoForge 还会把 Minecraft 反编译再重新编译一遍（`createMinecraftArtifacts`）。期间电脑会比较卡，属于正常现象。

---

## 4. Stonecutter 是怎么工作的

### 4.1 节点和激活节点

`settings.gradle` 里定义了两个节点：

```groovy
version("26.3-fabric", "26.3").buildscript("build.fabric.gradle")
version("26.3-neoforge", "26.3").buildscript("build.neoforge.gradle")
```

- 每个节点是一个 Gradle 子项目（`:26.3-fabric`、`:26.3-neoforge`），目录在 `versions/` 下，用各自的构建脚本。
- 两个节点共用根目录的 `src/`。
- `src/` 里的代码永远是**激活节点**看到的样子。激活节点写在 `stonecutter.gradle`：
  ```groovy
  stonecutter.active "26.3-fabric"
  ```
  这一行由 Stonecutter 自动改写，**不要改它的格式**。
- 非激活节点构建时，Stonecutter 会把源码按条件注释处理成该节点的版本再编译，不用手动切换。所以 Fabric 激活时也能直接构建 NeoForge 版。

### 4.2 切换激活节点

切换只是为了**编辑**：要写 NeoForge 专用代码时，切过去之后 IDE 才能高亮和检查那部分代码。

| 操作 | Gradle 任务（根项目 → Tasks → stonecutter） |
|---|---|
| 切到 NeoForge | `Set active project to 26.3-neoforge` |
| 切回默认（Fabric） | `Reset active project` |
| 重新处理当前节点 | `Refresh active project` |

命令行写法：`./gradlew "Set active project to 26.3-neoforge"`。

> 提交前切回 Fabric（`Reset active project`）。仓库约定以 `26.3-fabric` 为准（`settings.gradle` 里的 `vcsVersion`），否则 diff 里会混进一大片注释切换。

### 4.3 条件注释语法

可用的条件常量是 `fabric` 和 `neoforge`（在 `stonecutter.gradle` 里用 `constants.match` 定义）。

**代码块**：激活分支是正常代码，非激活分支包在 `/* */` 里。

```java
//? if fabric {
import net.fabricmc.api.ModInitializer;
//?} else {
/*import net.neoforged.fml.common.Mod;
*///?}
```

**单行**：只作用于紧跟的下一条语句。

```java
//? if neoforge
//LOGGER.info("only on NeoForge");
```

**行内表达式**：见 `Platform.java`。

```java
Platform INSTANCE =
    /*? if fabric {*/new FabricPlatform();
    /*?} elif neoforge *///new NeoForgePlatform();
```

**嵌套**：在已注释掉的块里再写条件时，用 `/^ ^/` 代替 `/* */`，避免注释提前结束。

**版本条件**：以后加了其他 Minecraft 版本，可以写 `//? if >=26.4 {`。

**swap**：把一段代码替换成配置里的值。例如 `Moonlight.VERSION`：

```java
public static final String VERSION = /*$ mod_version*/ "0.1.0";
```

构建时会被替换成 `stonecutter.properties.toml` 里的 `mod.version`。swap 在 `stonecutter.gradle` 里定义。

> 非激活分支是注释，IDE 不检查。改 NeoForge 那部分代码时请先切换激活节点，改完能通过编译再切回来。

---

## 5. 代码分层（最重要的规则）

| 层 | 位置 | 能用什么 |
|---|---|---|
| 通用代码 | `wtf.moonlight` 下除 `fabric` / `neoforge` / `platform` 以外的所有包 | 只用原版 Minecraft 类、Mixin、自己的代码 |
| 平台层 | `wtf.moonlight.platform.Platform` | 加载器 API，用条件注释分开 |
| 入口层 | `wtf.moonlight.fabric`、`wtf.moonlight.neoforge` | 只负责把调用转给通用入口 |
| Mixin | `wtf.moonlight.mixin` | 原版类，两边共用 |

启动流程：

| | Fabric | NeoForge |
|---|---|---|
| 通用初始化 | `MoonlightFabric.onInitialize()` → `Moonlight.init()` | `@Mod("moonlight")` 构造器 → `Moonlight.init()` |
| 客户端初始化 | `MoonlightFabricClient.onInitializeClient()` → `MoonlightClient.init()` | `@Mod(value = "moonlight", dist = Dist.CLIENT)` 构造器 → `MoonlightClient.init()` |

两个加载器调用入口的时机都比较早，这时窗口、资源、字体都还没准备好。`init()` 里只做对象创建和注册；用到渲染资源的初始化，放到游戏启动完成之后，由自己的 Mixin 事件触发。

### 5.1 怎样避免同一个功能写两份

原则：**功能代码不直接调用任何加载器 API**。这样真正要写两份的只有入口类和 `Platform` 的两份实现。

**① 用自己的事件系统 + Mixin 触发，不用 Fabric API / NeoForge 的事件**

在原版方法里用 Mixin 发出自己的事件，所有功能只订阅这些事件。Mixin 两边通用，所以事件也是两边通用的。项目里已经有完整的事件系统，用法和事件列表见 5.3 节，例如 `MinecraftMixin`：

```java
@Inject(method = "tick", at = @At("HEAD"))
private void moonlight$preTick(CallbackInfo ci) {
    Moonlight.EVENT_BUS.post(TickEvent.PRE);
}
```

tick、渲染、收发包、按键、聊天这些都可以这样做。功能代码完全不依赖加载器时，`fabric.mod.json` 里对 `fabric-api` 的依赖也可以去掉，玩家装 Fabric 时就不用另外装 Fabric API。

**② 必须用加载器 API 的地方，统一加到 `Platform`**

例如配置目录：通用代码只写 `Platform.INSTANCE.configDir()`，两份实现在 `Platform.java` 里各占几行。新增能力时：

1. 在接口里加方法；
2. 在 `FabricPlatform` 里实现；
3. 切到 neoforge 节点，在 `NeoForgePlatform` 里实现，编译通过后切回。

**③ 很小的差异直接用条件注释**

一两行的差异，在原文件里用 `//? if fabric` / `//? if neoforge` 写就行，不用为它单独建类。

**④ 改访问权限用 `@Accessor` / `@Invoker`，不用 AW / AT**

Fabric 用 access widener（`moonlight.ct`），NeoForge 用 access transformer，格式不同，要维护两份。Mixin 的访问器两边通用，例如项目里的 `MinecraftAccessor`（旋转系统用它立刻刷新准星目标，见 5.11）：

```java
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("pick")
    void moonlight$pick(float partialTicks);
}
```

使用时：`((MinecraftAccessor) Minecraft.getInstance()).moonlight$pick(1.0F);`。字段用 `@Accessor`，写法相同。访问器接口也要登记到 `moonlight.mixins.json`。

### 5.2 必须用加载器 API 时放在哪

- 只在 `platform/` 或者 `//? if` 条件块里写。
- 不要在通用代码里 `import net.fabricmc.*` 或 `net.neoforged.*`：激活 Fabric 时能编译，NeoForge 节点构建时就会报错。

### 5.3 事件系统

支持优先级、可取消事件、Pre/Post 阶段和 `hasListeners` 快速判断，调用器参考 Meteor Orbit 按方法缓存 `LambdaMetafactory` 工厂。代码在 `wtf.moonlight.event`，事件类在 `wtf.moonlight.event.impl.*`，触发事件的 Mixin 在 `wtf.moonlight.mixin`。全部只用原版类，两个加载器共用。

按代码规范"不留零用法"，事件类里**只有 Mixin 和现有系统实际读写的成员**。以后写模块需要读某个值、改某个值（例如改 `MotionEvent` 的坐标）时，再给事件加对应的 getter / setter，并让 Mixin 用上它。

**核心类**

| 类 | 作用 |
|---|---|
| `Moonlight.EVENT_BUS` | 全局事件总线：`register(obj)`、`unregister(obj)`、`post(event)`、`hasListeners(type)` |
| `Event` / `CancellableEvent` | 事件基类 / 可取消事件（`cancel()`、`isCancelled()`） |
| `EventPhase` | `PRE` / `POST` |
| `Priority` | `HIGHEST=0`、`HIGH=5`、`MEDIUM=10`（默认）、`LOW=15`、`LOWEST=20`，数值小的先执行 |
| `@Subscribe(priority = ...)` | 标在监听方法上 |

**订阅**

```java
public final class ChatFilter {
    @Subscribe(priority = Priority.HIGH)
    private void onChat(ChatSendEvent event) {
        if (event.getMessage().isBlank()) event.cancel();
    }
}

Moonlight.EVENT_BUS.register(new ChatFilter());
```

- `register(obj)` 会扫描这个类和它所有父类里的 `@Subscribe` 方法，私有方法也可以。方法必须是非静态、返回 `void`、只有一个参数，参数类型必须是具体的事件类，否则直接抛 `IllegalArgumentException`。同一个对象重复注册会被忽略。
- `unregister(obj)` 移除这个对象的全部监听方法。派发过程中被注销的监听器，在当前这次派发里也会立即被跳过（例如按键关掉了一个模块，这个模块就收不到同一次按键事件）。模块开关就是靠 `register` / `unregister` 实现的，见 5.7。
- 目前没有 lambda 订阅，需要时再加。

**派发规则**

- 按事件的**确切类型**派发，不走继承：订阅 `Event` 或者父类收不到子类事件。
- 优先级数值小的先执行。同优先级下：不同对象按注册顺序；同一个类里按方法名排序（反射返回的方法顺序不固定，所以统一按名字排）。
- 事件被取消后**后面的监听器照样会收到**，需要时自己判断 `event.isCancelled()`。
- 某个监听器抛异常只会记一条错误日志（`moonlight/events`），不影响其他监听器和游戏。
- `post(event)` 返回传入的事件对象，可以链式写 `EVENT_BUS.post(e).isCancelled()`。
- 派发时不加锁（写时复制数组）。
- 调用器用 `LambdaMetafactory` 生成，每次调用约 8 ns（200 万次派发 × 5 个监听器约 80 ms）。

**在 Mixin 里触发事件**

- 频繁调用的位置先判断 `Moonlight.EVENT_BUS.hasListeners(XxxEvent.class)`，没人订阅时不创建事件对象。
- 只关心本地玩家的实体事件，先判断 `this == Minecraft.getInstance().player`：这些 Mixin 在单人游戏里也会作用到内置服务端的实体上。
- 不带数据、也不能取消的事件用静态单例（`INSTANCE`，`TickEvent` 是 `PRE` / `POST`），不每次新建。可取消的事件每次新建，因为取消状态属于这一次触发。

**事件列表**

| 事件 | 触发位置（26.3） | 可取消 | 现有成员 / 说明 |
|---|---|---|---|
| `client.GameLoadedEvent` | `Minecraft.onGameLoadFinished` 末尾 | — | 单例；资源加载完、进入标题画面时触发一次，适合初始化字体、贴图 |
| `client.TickEvent` | `Minecraft.tick` 开头 / 返回 | — | `PRE` / `POST`，`getPhase()` |
| `client.WorldChangeEvent` | `Minecraft.setLevel` / `clearClientLevel` 末尾 | — | 单例；进入或离开世界 |
| `client.ScreenOpenEvent` | 包住 `Gui.setScreen` | 是 | `getScreen()`；26.3 的界面切换在 `Gui` 里，`Minecraft` 已没有 `setScreen` |
| `client.ModuleToggleEvent` | `Module.switchTo` 改完状态后 | — | `getModule()`、`isEnabled()`；只在用户操作（热键、ClickGUI 开关、`.toggle`）时发，读配置不发（见 5.7） |
| `client.NotifyEvent` | `ConfigManager` 报告结果、热键菜单录完键 | — | `getKind()`（`CONFIG` / `BIND` / `ERROR`）、`getText()`；通知 HUD 显示它（见 5.15） |
| `input.KeyInputEvent` | `KeyboardHandler.keyPress` 开头 | 是 | `getKey()`（SDL scancode，对应 `InputConstants.KEY_*`）、`getEvent()`（原始 `KeyEvent`）、`isPress()`、`isRepeat()` |
| `input.CharInputEvent` | `KeyboardHandler.charTyped` 开头 | 是 | `getCodepoint()` |
| `input.MouseInputEvent` | `MouseHandler.onButton` 开头 | 是 | `getButton()`（SDL 编号：左 1、中 2、右 3）、`isPress()` |
| `input.MouseMoveEvent` | `MouseHandler.onMove` 末尾 | 否 | 不带数据，位置用 `mouseHandler.xpos()` / `ypos()` |
| `input.MouseScrollEvent` | `MouseHandler.onScroll` 开头 | 是 | 不带数据 |
| `network.PacketSendEvent` | `Connection.send` 里调用 `sendPacket` 处 | 是 | `getPacket()`；只处理客户端自己的连接；连接建立前排队的包不经过这里 |
| `network.PacketReceiveEvent` | `Connection.channelRead0` 里调用 `genericsFtw` 处 | 是 | `getPacket()`；**在网络线程触发**，不要在里面直接改游戏状态，需要时 `Minecraft.getInstance().execute(...)` |
| `network.ChatSendEvent` | 包住 `ClientPacketListener.sendChat` | 是 | `getMessage()`；只有普通聊天，`/` 开头的命令走 `sendCommand`，不经过这里 |
| `player.PlayerTickEvent` | `LocalPlayer.tick` 开头 | — | 单例 |
| `player.MotionEvent` | `LocalPlayer.sendPosition` 开头 | 是 | `getX/Y/Z()`、`getPosition()`、`getYaw/Pitch()` / `setYaw/Pitch()`、`isOnGround()`；取消 = 这一 tick 不发移动包。见下方说明 |
| `player.RespawnEvent` | `LocalPlayer.sendPosition` 开头，玩家 `tickCount <= 1` 时 | — | 单例；新的本地玩家对象刚出现（进世界、重生、换维度） |
| `player.MoveInputEvent` | `KeyboardInput.tick` 里新建 `Input` 处 | — | `getInput()` / `setInput()`：设置的 `Input` 就是这一 tick 的移动输入（`Sprint` 用它把冲刺置为按下，移动修复用它改方向键） |
| `player.JumpEvent` | `LivingEntity.jumpFromGround` 里的 `getYRot()` | — | `getYaw()` / `setYaw()`，结果作为疾跑跳的加速方向；只对本地玩家 |
| `player.StrafeEvent` | `Entity.moveRelative` 里 `getInputVector` 的偏航参数 | — | `getYaw()` / `setYaw()`，结果作为移动方向；只对本地玩家 |
| `player.RaycastEvent` | `Entity.getViewVector` 里的 `calculateViewVector` | — | `getYaw/Pitch()` / `setYaw/Pitch()`，结果作为视线方向，准星选中的方块和实体都由它算出；只对本地玩家 |
| `player.ItemRaycastEvent` | `Item.getPlayerPOVHitResult` 里的 `calculateViewVector` | — | 同上，用于物品自己的视线检测（水桶、船、刷子等）；只对本地玩家 |
| `player.UseItemEvent` | `MultiPlayerGameMode.useItem` 里新建 `ServerboundUseItemPacket` 处（lambda `lambda$useItem$0`） | — | `getYaw/Pitch()` / `setYaw/Pitch()`，结果写进使用物品包发给服务器 |
| `player.FallFlyingEvent` | `LivingEntity.updateFallFlyingMovement` 里的 `getXRot()` | — | `getPitch()` / `setPitch()`，结果作为鞘翅滑翔的俯仰角；只对本地玩家 |
| `player.AttackEvent` | `MultiPlayerGameMode.attack` 开头 | 是 | 不带数据；取消 = 不攻击 |
| `player.AttackYawEvent` | `Player.causeExtraKnockback` 里的 4 处 `getYRot()` | — | `getYaw()` / `setYaw()`，结果作为击退方向；只对本地玩家 |
| `player.SlowdownEvent` | `LocalPlayer.modifyInput` 里的 `isUsingItem()` | 是 | 取消 = 使用物品时不减速；只在正在使用物品时触发 |
| `render.Render2DEvent` | `Hud.extractRenderState` 末尾 | — | 单例 |
| `render.Render3DEvent` | `GameRenderer.render3dHud` 里 `popPush("hand")` 处 | — | 单例；世界画完、手部之前 |
| `render.GammaEvent` | `LightmapRenderStateExtractor.extract` 里读取 `options.gamma()` 的值处 | — | `getGamma()` / `setGamma()`：设置的值代替亮度选项参与光照贴图计算（`FullBright` 用它） |
| `render.RotationAnimationEvent` | `LivingEntity.tickHeadTurn` 里的 `getYRot()`；`LivingEntityRenderer.extractRenderState` 里头部偏航的 `Mth.rotLerp` 和俯仰的 `getXRot(float)` | — | `getYaw/LastYaw/Pitch/LastPitch()` 和对应 setter：本地玩家模型的身体转向、头部偏航、俯仰（第三人称和别人看到的效果一致）。每处只读用到的那一对值 |

**`MotionEvent` 的实现要点**：26.3 的 `sendPosition` 构造移动包时用的是 `position()` 返回的 `Vec3`，`getX/getY/getZ` 只用于计算位移差和记录上次位置。所以 Mixin 同时包住了 `position()`、`getX/Y/Z`、`getYRot/getXRot` 和 `onGround()`，发出去的值全部来自事件。旋转系统（5.11）就是用 `setYaw/setPitch` 改发出去的视角，`yRotLast/xRotLast` 记下的也是改过的值。以后给 `MotionEvent` 加坐标的 setter 时，改动同样会真正发到服务器，只改 `getX/Y/Z` 是不够的。骑乘时 26.3 在 `sendChanges` 里单独发 `Rot` 包、不进 `sendPosition`，所以骑乘期间不会触发 `MotionEvent`。

**新增一个事件**

1. 在 `wtf.moonlight.event.impl.<分组>` 下建事件类，继承 `Event` 或 `CancellableEvent`，声明为 `final`。
2. 写 Mixin 触发它，登记到 `moonlight.mixins.json`（客户端相关的放 `client` 数组）。
3. **两份 jar 都要核对注入点**：原版 `minecraft-merged.jar`（在 Gradle 缓存的 `fabric-loom/26.3/` 下）和 NeoForge 的 `versions/26.3-neoforge/build/moddev/artifacts/minecraft-patched-26.3.0.101.jar`。用 `javap -p` 看方法签名，用 `javap -c` 确认 `@At` 指向的调用确实在方法体里。注入点写错的话编译发现不了，要到游戏启动时才会崩溃（`defaultRequire = 1`）。NeoForge 的 `minecraft-patched-26.3.0.101-sources.jar` 里有反编译源码，看方法逻辑比读字节码方便。

### 5.4 聊天命令

代码在 `wtf.moonlight.command`。在聊天框输入以前缀（默认 `.`）开头的消息时，消息不会发到服务器，而是交给客户端命令执行。命令用 Brigadier 定义，**执行和 Tab 补全共用同一棵命令树**，一个命令只写一次。

| 内置命令 | 用法 |
|---|---|
| `.help` / `.h` | 列出所有命令；`.help <命令>` 显示该命令的全部用法 |
| `.prefix` | 显示当前前缀；`.prefix <新前缀>` 修改前缀（1–3 个字符，不能有空格，不能以 `/` 开头） |
| `.say <消息>` | 原样发送一条聊天，即使它以前缀开头 |
| `.bind` / `.b` | 按键绑定，见 5.5 |
| `.toggle` / `.t` | 按分类列出模块和开关状态；`.toggle <模块>` 开关模块，见 5.7 |
| `.config` / `.cfg` | 列出配置；`save [名字]` 保存，`load <名字>` 加载，见 5.8 |

**工作方式**

- `CommandManager` 以最高优先级订阅 `ChatSendEvent`：消息以前缀开头时取消事件（不发给服务器），把去掉前缀的部分交给 `dispatcher.execute(...)`。消息已经进了聊天历史，上箭头可以翻出来。
- 语法错误按原版样式显示：红色的错误原因，加上一行灰色的出错位置和 `<--[HERE]`，点击这行会把命令填回聊天框。命令名不存在时提示用 `.help`。命令里抛出的其他异常会记日志并在聊天里显示一行错误。
- `CommandSuggestionsMixin` 在 `CommandSuggestions.updateCommandInfo()` 第一次调用 `StringReader.canRead()` 之前注入：输入以前缀开头时改用客户端命令树解析和补全，然后跳过原版逻辑。所以补全列表、参数高亮和用法提示都是原版那一套。只在 `ChatScreen` 里生效，命令方块等界面不受影响。客户端命令不受服务器"禁止聊天 / 禁止命令"的限制。
- 前缀保存在 `config/moonlight/commands.properties`。
- 命令树的源类型是 `ClientSuggestionProvider`（和原版补全用的一样），所以原版的参数类型和补全提供者都可以直接用。

**新增一个命令**

```java
public final class EchoCommand extends Command {
    public EchoCommand() {
        super("echo", "Prints a message back to you", "e");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> root) {
        root.then(argument("text", StringArgumentType.greedyString())
                .executes(context -> {
                    ChatUtils.info(StringArgumentType.getString(context, "text"));
                    return SUCCESS;
                }));
    }
}

MoonlightClient.getCommands().register(new EchoCommand());
```

- `build(root)` 里的 `root` 已经是命令名本身，往下加 `literal(...)` / `argument(...)` 即可。别名会用同一份 `build` 再注册一次，所以别名的补全和执行和正名完全一样。
- 命令描述直接写在构造函数里（英文），`.help` 会显示。
- 需要补全时在参数上加 `.suggests((context, builder) -> SharedSuggestionProvider.suggest(列表, builder))`。

### 5.5 按键绑定

代码在 `wtf.moonlight.bind`。

| 类 | 作用 |
|---|---|
| `Bindable` | 接收按键的东西：`onKey(code, pressed)`、每 tick 的 `reconcile()` |
| `Bind` | 每个模块一个：有名字、开关状态，以及任意多个 `Hotkey` |
| `Hotkey` | 一个热键：按键（Key）、模式（Toggle / Hold）、Value、Visible |
| `BindManager` | 收到按键 / 鼠标事件后分发给所有 `Bind`；按名字查找 `Bind` |
| `BindCapture` | "按下一个键来绑定"：开始后下一次按键就写进目标 `Hotkey`；`getTarget()` 是正在等待的热键，`cancel()` 取消 |

**键码约定**（`KeyUtils`）：`0` = 未绑定；正数 = SDL scancode（就是 `InputConstants.KEY_*`）；负数 = 鼠标键，`-1` 左键、`-2` 中键、`-3` 右键、`-4`/`-5` 侧键（26.3 的 SDL 鼠标编号取负）。

**`Hotkey` 的四个属性**

| 属性 | 默认 | 作用 |
|---|---|---|
| Key | 构造时传入 | 按哪个键，`0` 表示还没录 |
| Mode | Toggle | Toggle：每按一次在开 / 关之间切换，Value 不起作用；Hold：按住时把模块设成 Value，松开恢复按下前的状态 |
| Value | 开 | Hold 模式按住期间模块的状态，关掉就是"按住时临时关闭" |
| Visible | 开 | 是否显示在热键列表 HUD（5.14） |

**`Bind` 的行为**

- 一个模块可以有任意多个热键，各自独立生效，例如 F 切换、R 按住临时开启。
- Hold 按下时记住模块原来的状态，松开时恢复。每 tick 的 `reconcile()` 还会用 `KeyUtils.isDown` 查一次物理按键：按着的 Hold 键已经松开（比如切出窗口时松开、松开事件被吞了）就按松开处理。删除一个正按着的 Hold 热键时也会先恢复。
- 按下时要满足触发条件，默认是没有打开任何界面；`setGate(...)` 可以换掉（ClickGUI 用它在自己的界面里也能关闭）。**松开不受这个限制**，保证 Hold 能正常恢复。
- 构造时传入状态回调 `new Bind(name, state -> ...)`，开关状态变化时调用。外部改了状态（比如用命令开关了模块）用 `sync(state)` 同步，不触发回调。
- `add` / `remove` / `clear` 管理热键，`primary()` 返回第一个热键（没有就新建一个），`isBound()` 表示至少有一个录了键的热键。
- 热键随模块一起保存在配置里（见 5.8），`Bind.save()` 写成数组 `[{"key", "hold", "value", "visible"}, ...]`。`load()` 也认旧格式的单个对象 `{"key", "hold"}`，读成一个热键（键为 0 时不建）。
- ClickGUI 模块构造时自带一个右 Shift 的 Toggle 热键，Visible 默认关，打开界面时不会出现在热键列表里。

每个模块自带一个同名的 `Bind`，回调就是 `switchTo`（会发 `ModuleToggleEvent`），由 `ModuleManager` 注册到 `BindManager`（见 5.7）。`BindManager` 和 `ModuleManager` 都在 `MoonlightClient.init()` 里创建。

**输入顺序**：`BindCapture`（最高优先级）→ `BindManager`（高优先级）。捕获中按下的键会取消事件，所以不会同时触发已有的绑定，也不会传给原版。松开事件不吞，避免原版按键卡住。

**`.bind` 命令**

| 写法 | 作用 |
|---|---|
| `.bind` | 列出所有模块和它们的热键 |
| `.bind <名字>` | 开始捕获，录进这个模块的第一个热键（没有就新建）：接下来按的键就是绑定，Esc 清除，鼠标左键取消；最后没录上键的热键会被删掉 |
| `.bind <名字> <按键>` | 直接设置第一个热键的按键，`none` 删除这个模块的全部热键 |
| `.bind <名字> <按键> <toggle\|hold>` | 同时设置第一个热键的模式 |

名字就是模块名，不区分大小写、忽略空格（`Kill Aura` 写成 `killaura` 即可），例如 `.bind Sprint R`。按键名见下面的 `KeyUtils`。

### 5.6 utils

工具类放在 `wtf.moonlight.utils`。

| 类 | 作用 |
|---|---|
| `ChatUtils` | 在聊天框显示客户端消息：`info(...)`、`error(...)`（带 `[Moonlight]` 标签），`send(...)`（原样，不带标签）。内部用 `Minecraft.execute` 切回主线程，任何线程都能调用 |
| `KeyUtils` | 键码和名字互转、按键名补全列表、`isDown(code)` 查询物理按键状态 |
| `RotationUtils` | 算朝向某点 / 实体 / 方块面的 `Rotation`（`calculate` 各重载，实体版可以自适应找能射到的点），GCD 修正（`applySensitivityPatch`），限速转动（`move`）和平滑（`smooth`），`relateToPlayerRotation`、`resetRotation`，眼睛到实体碰撞箱的最近距离（`getDistance`）。见 5.11 |
| `RaycastUtils` | 按给定 `Rotation` 做视线检测：`rayCast`（方块 + 实体，可以放大实体碰撞箱），`inView`（实体是否在视野里且看得见），`overBlock`（是否正对某个方块 / 方块面，距离 4.5） |
| `MoveUtils` | `fixMovement`：移动修复，见 5.11；`isMoving()`：这一 tick 有没有移动输入（`moveVector` 非零）；`impulse(正向键, 反向键)`：把一对按键换成 1 / 0 / -1 |

**聊天输出的写法和配色**

`info` / `error` 用模板写：`{}` 处依次填入参数，参数显示为白色，其余文字 `info` 是灰色、`error` 是红色。参数如果本身就是带颜色的 `Component`，保持它自己的颜色。

```java
ChatUtils.info("{} bound to {}.", bind.getName(), "R");
ChatUtils.info("{} {}", module.getName(), Component.literal("enabled").withStyle(ChatFormatting.GREEN));
ChatUtils.error("Unknown module: {}", name);
```

统一配色：

| 部分 | 颜色 |
|---|---|
| 标签 `[Moonlight]` | 灰色方括号 + 青色名字 |
| 普通说明文字 | 灰色 |
| 重点值（模块名、按键、前缀、数量、命令名） | 白色 |
| 开 / 关状态 | `enabled` 绿色，`disabled` 红色；列表里开着的模块绿色、关着的深灰色 |
| 错误 | 红色文字，出错的值白色 |
| 列表里的次要信息（别名、分隔符、未绑定的 `None`） | 深灰色 |

多行输出（命令列表、绑定列表、模块列表）：第一行用 `info` 带标签写标题，比如 `Commands (6):`；下面的条目用 `send`，缩进两格，不带标签。

**`KeyUtils` 的按键名**

- 键盘：由原版的 `key.keyboard.*` 名字转成驼峰，例如 `A`、`0`、`F5`、`LeftShift`、`RightControl`、`Keypad0`、`KeypadEnter`、`PageUp`、`GraveAccent`。没有登记名字的 scancode 显示为 `Key<数字>`。
- 鼠标：`LMB`、`MMB`、`RMB`、`MB4`、`MB5`……；未绑定显示 `None`。
- `parse(...)` 不分大小写，忽略空格、下划线、点等分隔符（`left_shift`、`left.shift`、`LeftShift` 都行），另外认这些简写：`lshift`、`rshift`、`lctrl`、`rctrl`、`lalt`、`ralt`、`esc`、`del`、`ins`、`bs`、`return`、`pgup`、`pgdn`、`caps`、`mouse1`…`mouseN`、`key<scancode>`、`none` / `unbind`。认不出返回 `null`。
- `isDown(code)`：键盘读 `InputConstants.isKeyDown`（SDL 键盘状态），鼠标读 `SDL_GetMouseState` 的按键掩码。

### 5.7 模块

代码在 `wtf.moonlight.module`。

```
module
├─ Module          模块基类：名字、分类（可选子分类）、Bind、开关、设置项
├─ Category        分类
├─ Subcategory     子分类（目前 Render 下的 Overlay、World）
├─ ModuleManager   创建模块、按名字查找、注册每个模块的 Bind
└─ impl
   ├─ client / combat / misc / movement / player / render / world   按分类放模块
   ├─ combat/KillAura
   ├─ movement/Sprint
   ├─ player/Scaffold
   └─ render/FullBright、ClickGui
```

- `impl` 下每个分类一个包，目前 `combat`（`KillAura`）、`movement`（`Sprint`）、`player`（`Scaffold`）和 `render`（`FullBright`、`PostProcess`、`Watermark`、`Hotkeys`、`Notifications`、`Esp`、`ClickGui`）里有模块，其余是空包（只放了 `package-info.java`，方便 git 保留目录；包里有了第一个类就把它删掉）。
- 分类下面可以再分子分类：模块构造时传 `Subcategory` 而不是 `Category`（`super("Watermark", Subcategory.OVERLAY)`），分类由子分类决定。有子分类的分类在侧栏里可以展开，它的模块要全部挂到子分类上。现在 Render 分成 Overlay（`PostProcess`、`Watermark`、`Hotkeys`、`Notifications`、`ClickGui`）、World（`FullBright`）和 Entities（`Esp`，页面布局特殊，见 5.16）。子分类同样按"不留零用法"，有模块了再加，图标在 `PageLayout.icon(Subcategory)`。
- 模块要用到另一个模块时，由 `ModuleManager` 在构造时传进去，不做全局查找。例如 `KillAura` 构造时拿到 `Scaffold`，`Scaffold` 开着时 `KillAura` 不工作，免得两个模块抢着转头。
- `Category` 枚举目前是 `MOVEMENT`、`RENDER`。在某个分类的包里写下第一个模块时，再给 `Category` 加对应的常量（按"不留零用法"，没有模块的分类不加，`.toggle` 列表里也不会出现空分类）。

**模块怎么工作**

- `setEnabled(true)` 时先调用 `onEnable()`，再把模块注册到事件总线；`setEnabled(false)` 时注销。所以模块里的 `@Subscribe` 方法只在模块开着时收到事件，不用自己判断开关。需要在开启时初始化状态的模块重写 `onEnable()`（例如 `FullBright` 记下当前亮度），关闭时要收尾的重写 `onDisable()`（在注销之后调用，例如 `ClickGui` 关掉界面）。
- 用户操作开关模块时调用 `switchTo(boolean)`：状态真的变了才调 `setEnabled`，然后发 `ModuleToggleEvent`（`isPersistent()` 为 false 的模块不发，ClickGUI 每次打开都提示没有意义）。热键、ClickGUI 的模块开关和 `.toggle` 命令都走 `switchTo`。`setEnabled` 保持静默，留给读配置和模块内部逻辑，所以读一份配置不会冒出一串开关通知。
- 每个模块自带一个同名的 `Bind`：按绑定键切换（或按住）时开关模块；用命令开关模块时，`Bind` 的状态跟着同步。默认打开任何界面时绑定键都不生效，模块可以用 `getBind().setGate(...)` 换成自己的条件（`ClickGui` 允许在自己的界面里按绑定键关闭，正在输入文字时除外）。
- 模块的开关状态和绑定都保存在配置里，重启后自动恢复（见 5.8）。`isPersistent()` 返回 `false` 的模块只保存绑定、不保存开关（`ClickGui`：不会一启动就弹出界面）。
- 设置项用 `add(...)` 声明成字段，按声明顺序登记到模块上：`private final BoolValue swing = add(new BoolValue("Swing", true));`。登记过的设置项会跟着模块存进配置，并显示在 ClickGUI 里（设置多的模块展开成卡片，其余在模块行的设置弹窗里，见 5.9、5.10）。

**`.toggle` 命令**

| 写法 | 作用 |
|---|---|
| `.toggle` / `.t` | 按分类列出所有模块，开着的绿色、关着的灰色 |
| `.toggle <模块>` | 开关这个模块 |

**新增一个模块**

```java
package wtf.moonlight.module.impl.movement;

public final class Sprint extends Module {
    public Sprint() {
        super("Sprint", Category.MOVEMENT);
    }

    @Subscribe
    private void onMoveInput(MoveInputEvent event) {
        Input input = event.getInput();
        event.setInput(new Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), input.shift(), true));
    }
}
```

1. 在 `module.impl.<分类>` 下建类，继承 `Module`，构造里传名字和分类；分类还没有就先给 `Category` 加上。
2. 在 `ModuleManager` 的模块列表里加上 `new Xxx()`。
3. 需要事件里还没有的数据或修改能力时，给事件类加对应的 getter / setter，并让 Mixin 用上。

**`Sprint` 的做法**：26.3 的 `KeyboardInput.tick` 把按键状态打包成 `Input`，`LocalPlayer.aiStep` 在可以冲刺且 `input.keyPresses.sprint()` 为真时开始冲刺，这个 `Input` 也会发给服务器。`Sprint` 在 `MoveInputEvent` 里把 `sprint` 置为 `true`，效果等于一直按着冲刺键；不改全局按键状态，关掉模块后下一 tick 就恢复，不需要额外清理。

**`FullBright` 的做法**：26.3 没有 `LightTexture` 了，亮度选项在 `LightmapRenderStateExtractor.extract` 里读取（`options.gamma().get()`），减去黑暗效果后写进 `LightmapRenderState.brightness`。Mixin 用 `@Slice` 从 `options.gamma()` 开始、改紧跟它的那次 `OptionInstance.get()` 的返回值（这个方法里还有 `hideLightningFlash`、`darknessEffectScale` 两次 `get()`，不能只按序号找），通过 `GammaEvent` 交给模块。`FullBright` 开启时记下当前亮度，之后每 tick 加 0.1、逐渐升到 15，光照贴图每 tick 重算一次，所以是渐亮效果；关掉后立刻恢复原亮度。不修改玩家的亮度设置，也不加药水效果。模式和目标亮度现在是固定的，以后可以接上设置项（见 5.9）做成可调。

**`Scaffold` 的做法**：普通平搭，脚下是空的就在脚下放方块。每个 `PlayerTickEvent` 做四件事：

1. 选方块：快捷栏里第一格能用的方块。不能用的有：非 `BlockItem`、火把这类 `StandingAndWallBlockItem`、`VegetationBlock`（26.3 的花草、作物、蘑菇、菌类都继承它）、台阶、黑名单里的方块（容器、矿石、沙子、蜘蛛网、栅栏等），以及物品名带 `Click` / `点击` 的菜单物品。关闭模块时切回开启前的格子。
2. 找放置点：脚下这格（玩家 Y 向下取整再减 1）能替换时，从这格开始往下、往四周按曼哈顿距离搜到 6 格深，找离眼睛 4.5 格以内最近的一个可依附面。依附的方块要有碰撞箱、不能是有界面的方块，而且这个面要朝着眼睛。正在移动且没按跳跃时不往方块顶面放。
3. 转头：偏航候选是 8 个固定方向（-135°～180°，按和移动方向反方向的接近程度排序），最后再加上直接对准放置面的偏航。每个偏航先试 75° / 82° / 87° 的俯仰（各加 ±0.3° 随机量），不行再从 -90° 到 89° 逐度扫，第一个能射中目标方块的就用。`45 Degrees` 打开时跳过正方向的偏航。都不行就直接对准放置面。第一次开启时的初始视角是"玩家偏航 -135°、俯仰 82°"。结果交给 `setRotations`，速度是 `Rotation Speed`。
4. 放方块：用当前服务端视角做一次视线检测，确认正对目标方块（`Raycast` 选 `Strict` 时还要求面也对上），然后 `MultiPlayerGameMode.useItemOn`。点击位置在面中心附近随机偏移，侧面再上移 0.08。

移动方向取自 `MoveInputEvent`（最高优先级，在移动修复改按键之前），按 45° 取整。`Swing` 打开时按原版的规则在本地挥手（`SwingSource.PREDICTED` 才挥）。26.3 原版放方块时不发挥手包（`ServerboundPunchPacket` 只在攻击和挖掘时发），服务器收到放置包后自己处理挥手，所以这里也不发。

| 设置项 | 默认 | 说明 |
|---|---|---|
| `45 Degrees` | 关 | 只用斜向偏航 |
| `Swing` | 开 | 放下方块时本地挥手 |
| `Raycast` | `Normal` | `Normal` 只要求射中目标方块，`Strict` 还要求射中的面就是放置面 |
| `Rotation Speed` | 127 | 每 tick 最多转多少度（1～180） |

**`KillAura` 的做法**：

1. 找目标（`TickEvent.PRE`）：遍历 `ClientLevel.entitiesForRendering()`，留下满足目标条件、眼睛到碰撞箱的距离不超过 `Range`、和玩家偏航相差不超过 `FOV` 一半的活体实体，再按 `Priority` 排序。目标条件：活着、不是自己、不是旁观者；隐身的只在 `Invisibles` 打开时算；玩家、村民（`AbstractVillager`）、怪物（`Monster`、`Slime`、`EnderDragon`）、动物（`Animal`、`AmbientCreature`、`WaterAnimal`）各由一个开关决定。
2. 选目标：`Single` 一直打排第一的；`Switch` 每过 `Switch Delay` 毫秒换下一个，到末尾回到第一个。
3. 转头：用 `RotationUtils.calculate(目标, true, Range)` 算朝向（直接对准射不中时，在碰撞箱上找一个能射中的点）。这个方向先撞上方块的话这一 tick 不转；否则交给 `setRotations`，速度是 `Rotation Speed`，检查条件是"3 格内的视线检测正好射中目标"。
4. 攻击：有目标时每帧（`RenderOverlayEvent`）按 CPS 攒攻击次数，间隔是 `1000 / 正态随机(CPS, 5)` 毫秒；到 `PlayerTickEvent` 再把攒下的次数打出去。准星（已经按服务端视角刷新过）指着实体就 `MultiPlayerGameMode.attack`，然后和原版 `startAttack` 一样本地挥手（`getAttackAnimation()`）并发送 `ServerboundPunchPacket`。正在使用物品时不攻击。

   发包顺序必须是"攻击包 → 挥手包"：1.9 以后的客户端每次攻击之间都要有一个挥手包，攻击包之后、挥手包之前也不能插进别的包，否则反作弊的发包顺序检查（`PacketOrderB`）会报 `pre-attack` / `post-attack`。

| 设置项 | 默认 | 说明 |
|---|---|---|
| `Mode` | `Single` | `Single` / `Switch` |
| `Switch Delay` | 100 ms | 只在 `Switch` 时显示 |
| `Priority` | `Range` | `Health`（血量少的先）、`FoV`（偏航差小的先）、`Range`（离得近的先）、`None`（不排序） |
| `CPS` | 12 | 每秒攻击次数的均值（1～20） |
| `Range` | 4.00 | 索敌距离（3～6），从眼睛算到目标碰撞箱 |
| `FOV` | 180 | 视野角度（0～360），目标要在玩家视线左右各一半以内 |
| `Rotation Speed` | 180 | 每 tick 最多转多少度 |
| `Targets` | Players | 打哪些实体，多选：Players、Mobs、Animals、Villagers、Invisibles（`ListValue`，ClickGUI 里是一行下拉框，点开勾选） |

### 5.8 配置

代码在 `wtf.moonlight.config.ConfigManager`，命令是 `.config`。

**存什么、存在哪**

- 每份配置是 `config/moonlight/configs/<名字>.json`，内容是每个模块的开关状态和绑定，以及 ClickGUI 的 Client 设置（见 5.10）：
  ```json
  {
    "modules": {
      "Sprint": { "enabled": true, "bind": [{ "key": 21, "hold": false, "value": true, "visible": true }] },
      "Scaffold": {
        "enabled": false,
        "bind": [],
        "values": { "45 Degrees": false, "Swing": true, "Raycast": "Normal", "Rotation Speed": 127.0 }
      },
      "ClickGUI": { "bind": [{ "key": 229, "hold": false, "value": true, "visible": false }] }
    },
    "client": { "Theme": "Dark", "Size": 100.0, "Custom Client Name": false, "Client Name": "" }
  }
  ```
- 模块的设置项（见 5.9）放在各自模块对象的 `values` 里，没有设置项的模块不写这一项。加载时文件里没有的设置项保持现状，先读设置项再恢复开关，所以 `onEnable()` 里看到的已经是加载后的值。
- 命令前缀是全局设置，不跟配置走，单独存在 `config/moonlight/commands.properties`。

**自动保存**

- 自动保存只写 `latest`：每 20 tick（约 1 秒）把当前状态序列化一次，和上次写入的内容不同才写盘。所以按键频繁开关模块不会反复写文件，状态最多晚 1 秒落盘。
- 退出游戏时 JVM 关闭钩子再保存一次（正常退出、Alt+F4、IDE 里点 Stop 都会触发）。
- 游戏加载完成（`GameLoadedEvent`）时自动加载 `latest`；在这之前不会自动保存，避免用默认状态覆盖上次的配置。不在启动更早的时候加载，是因为部分模块开启时要读游戏设置（例如 `FullBright` 读亮度）。

**手动保存 / 加载**

| 写法 | 作用 |
|---|---|
| `.config` / `.cfg` | 列出已保存的配置，`latest` 标 `(auto)` |
| `.config save` | 立即保存 `latest` |
| `.config save <名字>` | 把当前状态另存为一份命名配置 |
| `.config load <名字>` | 加载一份配置（加载后 `latest` 会在 1 秒内跟着更新） |

- 命名配置只在手动 `save` 时写入，不会被自动保存改动；想把改过的状态存回去就再 `save` 一次。
- 配置名只能用英文字母、数字、空格、`_`、`-`、`.`，最长 63 个字符，开头结尾不能是空格或 `.`，不能以 `.json` 结尾。名字是命令的最后一个参数，可以带空格，支持 Tab 补全。
- 加载时，文件里没有的模块保持现状；文件损坏或值的类型不对时加载失败并记一条警告（`moonlight/config`），游戏不受影响。
- 某个模块的 `onEnable()` / `onDisable()` 抛异常时，只记一条警告（`Module <名字> failed to switch on/off`），后面的模块和 Client 设置继续加载，不会因为一个模块让整份配置读一半。
- 写文件时先写 `<名字>.json.tmp` 再整体替换，中途崩溃也不会留下写了一半的配置。

**当前配置**

- `ConfigManager` 记着"当前配置"（默认 `latest`）：手动保存或加载哪份，当前配置就是哪份。ClickGUI 顶栏显示的就是它，左边的保存按钮保存的也是它。
- ClickGUI 的配置下拉框还能新建、覆盖、重命名、复制（`<名字> copy`、`<名字> copy 2`……）、删除、刷新列表、打开配置文件夹；删除当前配置时自动切到列表里的第一份并加载。每次操作的结果写进 `getStatus()`，下拉框底部显示 3 秒；同时发一条 `NotifyEvent`，成功（Saved / Loaded / Renamed to / Deleted / Duplicated）是 `CONFIG`，其余失败原因是 `ERROR`。
- 配置列表有缓存：游戏加载完成、每次保存 / 重命名 / 复制 / 删除、点"Refresh"时重新扫描目录，命令列出或补全前也会扫一次。

### 5.9 设置项

代码在 `wtf.moonlight.value`：基类 `Value<T>`（名字、当前值、可见条件、JSON 读写），实现在 `value.impl`：

| 类 | 值 | 说明 |
|---|---|---|
| `BoolValue` | `Boolean` | 开关；可以多传一个可见条件 |
| `NumberValue` | `Double` | `min` / `max` / `step`，`set` 时按步长四舍五入再夹到范围内；`format` 是 `String.format` 的格式，例如 `"%.0f%%"`；可以多传一个可见条件 |
| `ModeValue` | `Integer`（下标） | 选项列表；配置里存选项名字，不存下标。控件里点选项走 `select(i)`，会触发 `onSelect` 登记的回调（读配置走 `set`，不触发），重复点当前选项也会触发 |
| `TextValue` | `String` | 文本；构造时传可见条件 |
| `ListValue` | `Set<String>`（勾选了哪些） | 多选，两种构造。只传选项名列表 `ListValue(name, List<String>, 默认勾选)` 是普通勾选列表（`isSortable()` 为 false）：弹窗 160 宽，每行只有对勾和名字，不能拖动，读配置时也不改顺序，例如 KillAura 的 Targets。下面说的都是可排序版本：构造时传 `选项名 → 图标字形` 的有序表，`getOptions()` 返回当前顺序，`move(选项, 下标)` 改顺序。配置里存成 `{"order": [全部选项], "selected": [勾选的]}`；读到旧格式（只有勾选项的名字数组）时只改勾选、顺序不变。控件是一个下拉框，显示已选项摘要（按当前顺序）；点开是勾选列表（`ListElement`，188 宽），每行是对勾、图标（`hud-icons.ttf`）、名字、右侧拖动手柄（Font Awesome `grip-vertical`，被拿起的那一行渐变成高亮色，深色主题下是白色）。点一下切换勾选，弹窗不关；按住 0.25 秒或者按住移动超过 4 px 就拿起这一行，跟着鼠标上下走，其他行按 `1 - e^(-18·dt)` 滑开让位，顺序实时写回 `ListValue`，松手时那一行滑进空位，不切换勾选。某个选项可以挂子选项：`extra(选项, BoolValue...)`，这些开关不登记到模块上，跟着列表一起存进配置的 `"extras": {选项: {名字: 布尔}}`。有子选项的行在拖动手柄左边多一个小箭头；右键这一行，子选项在列表里原地向下展开（28 高的缩进行，对勾 + 13 号字，点一下切换），下面的行跟着往下滑、弹窗变高，箭头转成朝下，不另开弹窗；再右键收起。开始拖动排序时会先收起。弹窗带 `NoScrollbar | NoScrollWithMouse`（`Widgets.beginPopup(theme, id, flags)`）：它的高度每帧按内容设，本来不需要滚动，但 ImGui 会把窗口尺寸截成整数，在小数界面缩放下（例如 1.462、1.712、2.188）展开 / 收起动画的某些帧内容比窗口多出不到 1 像素，竖直滚动条时有时无，内容宽度跟着跳 12 像素，表现为左右快速抖动；整数缩放（1.25、1.5）不会出现。离线测试 `ListJitterTest` 按多种缩放检查这一点 |
| `ColorValue` | `Integer`（ImGui 颜色，含 alpha） | 颜色；配置里存 `#AARRGGBB`（旧的 `#RRGGBB` 按不透明读入），记住构造时的初始值，`reset()` 恢复。控件是色块，点开是调色板（见下） |

**调色板**（`element.Palette`，212 × 262，`ColorElement` 和账号菜单的 Theme Color 共用）：

- 上面是圆角的饱和度 / 亮度方块，选中点是白边圆圈。方块用两层圆角矩形叠出来：先白到色相的横向渐变，再透明到黑的纵向渐变。顶点颜色用 `Natives.gradient` 按位置重算，并保留抗锯齿边缘的 alpha。
- 下面是横向色相条（6 段）和透明度条（棋盘格底 + 颜色渐变）。
- 饱和度 / 明度方块的取色点、色相条和透明度条的滑块都是圆形（填当前颜色，透明度条的滑块填带当前透明度的颜色，2 px 白边），和细滑条同一套交互效果：外圈一层主题色光环，半径比滑块大 3 + 2 × 状态值，透明度 30 + 40 × 状态值；滑块本身随状态值放大 0.6。状态值悬停 0.7、拖动 1，按 `1 - e^(-18·dt)` 平滑。
- 最下面一行：
  - 格式下拉 HEX / RGB / HSV：带图标，选中项打勾。HEX 是 `#AARRGGBB`；RGB、HSV 不透明时只显示三个数，有透明度时多一个 alpha。
  - 当前色块。
  - 数值输入框：可以直接改，回车或失焦时按当前格式解析，解析不了就不改。
  - 恢复初始颜色：已是初始值时变暗。
  - 复制到剪贴板。
- 饱和度或亮度为 0 时色相保留上一次的值，拖到黑 / 白再拖回来不会跳色。

用在两个地方：ClickGUI 的 Client 设置（`ClientSettings`），以及模块用 `Module.add(...)` 登记的设置项。模块的设置项显示在模块的展开卡片或设置弹窗里（弹窗中在 `Bind` 那一行下面），顺序就是声明顺序，`Row.of` 按类型选控件。可见条件返回 `false` 的设置项不显示，但照样存进配置（`Watermark` 的 X / Y 就是这样存拖动位置的）。

### 5.10 ClickGUI

按 **右 Shift**（`ClickGUI` 模块的默认绑定）打开，再按一次、按 Esc 或点模块行里 ClickGUI 自己的开关关闭。界面用 ImGui 绘制，布局、尺寸、配色、字体和动画都照原样移植。

**包结构**

```
ui
├─ imgui            ImGui 接入，和界面内容无关
│  ├─ ImGuiManager  创建上下文、加载字体和样式，每帧 NewFrame → 画界面 → Render
│  ├─ ImGuiRenderer 用游戏的 RenderPearl 接口画 ImGui 的绘制数据，并管理 ImGui 的纹理
│  ├─ Input         把游戏的键盘、鼠标、文字输入转成 ImGui 的输入事件
│  ├─ Fonts         字体文件和 HUD 字号换算系数
│  ├─ Natives       直接读写 ImGui 结构体（偏移、校验）
│  ├─ Effects       给 ImGui 形状加模糊 / 阴影的接口（见 5.12）
│  ├─ Textures      把游戏纹理（如玩家皮肤）映射成 ImGui 纹理 ID（见 5.13）
│  └─ post          后处理的 GPU 部分：Blur、Bloom、Kawase、Area、Target
├─ hud              HUD 元素（见 5.13）
│  ├─ Hud           所有 HUD 元素的绘制入口，编辑模式下的拖拽、右键设置和聊天栏输入转发
│  ├─ HudElement / Rect 可拖拽元素的接口和它画出来的矩形
│  ├─ Style         每帧传给元素的全局样式：HUD Scale、深 / 浅色、主题色、HUD Alpha、是否在编辑
│  ├─ WatermarkRenderer 水印的排版、动画和绘制
│  ├─ Tracker / Snapshot 水印要用的游戏数据：服务器、延迟、TPS、坐标、速度、版本
│  ├─ Rolling       数字逐位滚动
│  ├─ FrameStats    帧时长统计：FPS、1% low、Low（最慢一帧）
│  └─ Skin          本地玩家皮肤纹理的查找
└─ clickgui         ClickGUI 本身
   ├─ ClickGuiScreen 打开 ClickGUI 时显示的 Screen：转发输入、切换光标和文字输入
   ├─ Panel         主窗口（748×576，侧栏 158，顶栏 56，圆角 14）、缩放和入场动画
   ├─ TopBar        字标、配置下拉框、搜索按钮
   ├─ Search        搜索浮层：模糊整个面板、搜索行、按模块分卡片的结果列表
   ├─ Scroller      平滑滚轮滚动（内容区和搜索结果共用）
   ├─ PageLayout    侧栏导航（含可展开的子分类）、两列模块卡片、Client 设置页、左下角账号菜单
   ├─ Row           一行设置（名字 + 键盘图标 + `...` + 控件 + 设置弹窗）
   ├─ element       控件：SwitchElement、SliderElement、SelectorElement、TextElement、BindElement、ColorElement、ListElement，以及调色板 Palette
   ├─ Controls      开关、选择器、滑条、卡片、弹窗底板等基础绘制和动画
   ├─ Widgets       字体、缩放、选项行、弹窗的进出动画
   ├─ Popups        弹窗关闭后的淡出快照
   ├─ Windows       记录这一帧画过的窗口，供入场 / 翻页 / 弹窗动画变换顶点
   ├─ Placement     主窗口居中和拖动后的位置
   ├─ Theme / Colors 深色、浅色配色（按主题色生成）和颜色运算
   └─ ClientSettings Theme、Theme Color、Background Alpha、Size、HUD Scale、HUD Alpha、HUD Menu Size、Custom Client Name、Client Name
```

**渲染**

- `GameRendererMixin` 在 `GameRenderer.render()` 里 `GuiRenderer.render()` 之后发 `RenderOverlayEvent`，`ImGuiManager` 在这里跑一帧 ImGui，画进主渲染目标。所以 ClickGUI 盖在游戏画面和 HUD 上面，截图（F2）里没有它。
- 不用 imgui-java 自带的 `ImGuiImplGl3`：`ImGuiRenderer` 用 RenderPearl 的 `RenderPass`（复用原版 `position_tex_color` 着色器，三角形、不剔除、ImGui 的混合方式），OpenGL 和 Vulkan 都能画。
- ImGui 1.92 的字体是按需光栅化的（任何字号都清晰），需要后端处理纹理的创建、增量更新和销毁。imgui-java 没有导出 `ImTextureData`，所以 `Natives` 按 1.92.7 的结构体布局直接读写，`ImGuiRenderer` 用 `GpuTexture` 上传。入场、翻页、弹窗动画也是直接改顶点缓冲。
- 这些偏移只对 imgui-java 1.92.7.1 成立。启动时 `Natives` 会把它们和绑定接口的结果比对，对不上就停用 ImGui、记一条错误，不会读写错内存。**升级 imgui-java 时要重新核对偏移。**

**输入**

- 打开 ClickGUI 就是打开 `ClickGuiScreen`：游戏松开鼠标，键盘和鼠标交给界面，其他模块的绑定键暂时不生效。
- 鼠标位置从 `MouseHandler` 的窗口坐标换算成帧缓冲像素；按键用 SDL 键码映射成 ImGui 键（`ImGuiImplSdl3.keyEventToImGuiKey`）；文字来自 `charTyped`。ImGui 要输入文字时 `ClickGuiScreen` 打开 SDL 的文字输入，不需要时关掉。
- 光标形状跟着 ImGui 变（输入框是 I 形）。
- 正在等按键绑定时按 Esc，按 5.5 的规则清除绑定，不关闭界面。
│  ├─ HotkeysRenderer 热键列表（见 5.14）
│  ├─ NotificationsRenderer 通知（见 5.15）
- ImGui 的断言在 imgui-java 里会直接结束进程，所以把可恢复的用户错误（Push / Pop 不成对之类）设成不断言，只显示 ImGui 自带的错误提示。

**字体**

├─ esp              ESP（见 5.16）
│  ├─ EspRenderer   游戏里：筛实体、投影成屏幕矩形、分三遍画
│  ├─ Painter / Target 一个目标的框、血条、护甲条、名字标签的画法（游戏和预览共用）
│  ├─ Kind          预览可选的实体类型（模型可见范围、原版名字标签高度、图标、名字）
│  ├─ View          预览的 3D 视角：yaw / pitch 和投影公式（框、游戏模型、演示模型共用）
│  ├─ VanillaTag    预览里原版名字标签的位置（点击范围、演示窗口画的位置）
│  ├─ Models        预览区"画模型"的接口
│  └─ Stage         游戏里的实现：离屏渲染真实实体模型
`src/main/resources/assets/moonlight/fonts/`：

| 文件 | 用途 |
|---|---|
| `sst-medium.ttf` | 正文，15 px 基准，3 倍横向过采样 |
| `medium.ttf` | 正文缺字时的回退（中日韩等），合并进正文字体 |
| `fa-solid-900.ttf` | 图标（Font Awesome 5 Solid），13 px |
| `extrabold.ttf` | 左上角字标，28 px，带 1.2 字距和两秒一轮的错位动画；也用作水印 logo |
| `inter-medium.ttf` | 水印文字（Inter Medium，wght 500 / opsz 14 的静态实例，只含 U+0020–007E、U+00A0–00FF，17 KB） |
| `inter-regular.ttf` | 热键列表和通知 HUD 的文字（Inter Regular，wght 400，其余同上，`Fonts.hudRegular()`）；和 Medium 同一套纵向度量，`HUD_EM` / `HUD_ASCENT` 通用 |
| `hud-icons.ttf` | HUD 图标，17 个 Material Symbols 字形。通知用的 5 个是 Outlined、wght 300：U+E9F6 `toggle_on`、U+E9F5 `toggle_off`、U+E161 `save`、U+E312 `keyboard`、U+E001 `error`。其余 12 个：U+E26B `bar_chart`（wght 300，已水平镜像）、U+E7FD `person`（实心）、U+F3E6 `history_2`（已顺时针转 90°）、U+E875 `dns`、U+E63E `wifi`、U+E9E4 `speed`、U+E0C8 `location_on`、U+EA0B `bolt`、U+F720 `deployed_code`、U+E51C `dark_mode`（实心）、U+E853 `account_circle`（实心）、U+E660 `auto_awesome_mosaic`（Outlined，wght 300，热键列表标题）。前 11 个是 Rounded，码位记在 `Watermark.Item` 里，HUD 和 Items 勾选列表共用。子集字体从 Google Fonts CSS2 接口（`icon_names=...`）下载，用 `test/watermark/build_fonts.py` 合成 |

ImGui 1.92 要求字体数据在整个图集生命周期里一直有效，imgui-java 直接传 `byte[]` 的接口做不到，所以 `Fonts` 先把字体解到临时目录，用 `addFontFromFileTTF` 让 ImGui 自己读进内存，读完马上删掉临时文件。两个大字体各约 8 MB，模组 jar 因此约 22 MB。

**页面**

- 侧栏 MODULES 下按 `Category` 列出分类（图标在 `PageLayout.icon`，加分类时要补一个）；CLIENT 下是 Settings。
  - 有子分类的分类（Render）点一下展开或收起，子分类缩进显示在下面，高度带动画。
  - 展开时如果当前页不在它下面，会跳到第一个子分类；收起时保持当前页，这时父项的文字高亮。
  - 内容超过 290 时这一栏可以滚动。
  - 页码：分类是 `ordinal`，子分类是 `100 + ordinal`，Client 页是 `-1`。
- 滚动：左右两列放在同一个子窗口 `module-list`（568 × 497）里一起滚动，每列自己记 y、手动排卡片。子窗口关掉了 ImGui 自带的滚轮（`NoScrollWithMouse`），由 `Scroller.update(generation)` 处理（搜索结果列表也用它）：鼠标在这个子窗口上时，滚轮每格让目标位置移动 74（两行），目标夹在 0 和 `getScrollMaxY()` 之间；实际位置按 `1 - e^(-16·dt)` 追目标，差不到 0.5 时对齐，再用 `setScrollY` 写回。有弹窗打开时 `isWindowHovered` 为 false，不会滚动。`generation` 变了（换页）就回到顶部。离线测试 `test/src/.../ScrollTest`。
- 模块页（`PageLayout.rebuild`）分两种卡片：
  - 可见设置项不少于 3 个（`EXPAND`）的模块单独展开成一张卡片，标题是大写的模块名。第一行是 `Enabled` 和开关（`Row.enabled`），下面每个设置项一行，控件和 Client 设置页一样（下拉框、带数值框的滑条、开关……）。
  - 其余模块合并到最前面的 `TOGGLES` 卡片，每行是模块名、开关，开关左边是 `...`（三个小圆点，悬停 / 打开时变亮）。点 `...` 打开这个模块的设置弹窗。
  - 在 TOGGLES 的行上或展开卡片的 `Enabled` 行上右键，也会打开设置弹窗；`Enabled` 行的弹窗里只有 `Bind`，因为设置项已经都在卡片上了。
  - 模块绑了键时，名字后面跟一个键盘图标（平时浅灰，热键菜单打开时变主题色），点它直接打开热键菜单。
  - 卡片按顺序放进当前更矮的那一列（高度 = 标题 20 + 间距 12 + 37 × 可见行数）。卡片标题统一大写，Client 页的卡片也是。
- 设置弹窗（`...`、右键模块行、右键 HUD 都是它）：宽 260（右键 HUD 时是 244），顶部是加粗的模块名，下面一张圆角内卡片，行高 30。右键 HUD 的弹窗按 HUD Menu Size 默认 125% 缩放，屏幕上约宽 305、行高 37.5，是按参考截图量出来的。第一行是 `Bind`，右边的键盘图标点开热键菜单（`BindElement`，见下）；下面是这个模块的设置项（见 5.9）。
- 所有数值滑条右边都有数值框（`SliderElement`），显示格式化后的值，点一下变成输入框，回车或点别处生效（解析不了的输入忽略，超出范围按 `NumberValue` 夹到范围内）。Client 设置页和展开卡片里的整体宽 132；设置弹窗和账号菜单里是"紧凑"布局：轨道固定 72，再加 10 的间距和数值框。数值框宽度按最小 / 最大 / 当前值里最长的文字 + 8，限制在 42～90，编辑时至少放宽到 58，轨道相应变短。没有悬停提示框。所有细滑条（`Controls.slider`）按下时跳到点击位置，之后按"按下时的值 + 鼠标横向位移 / 滑条宽度"计算，不看鼠标相对滑条的绝对位置，所以滑条本身在拖动中移动（比如弹窗跟着变大的 HUD 走）也不会影响数值。
- 细滑条的样子（设置弹窗、展开卡片、Client 设置页、账号菜单都共用 `Controls.slider`，颜色都走主题映射）：
  - 轨道高 3、圆角 1.5，底色是 `sliderBg`。悬停 / 拖动时按状态值（悬停 0.7、拖动 1，`1 - e^(-18·dt)` 平滑）混入 12% 主题色，轨道微微变亮。
  - 填充从左到右是主题色到"主题色混 15% 白色"的横向渐变，越靠近滑块越亮；进度按 `1 - e^(-12·dt)` 平滑。渐变是先画白色的抗锯齿圆角矩形、再用 `Natives.gradient` 改顶点色做的，边缘和轨道一样平滑。`addRectFilledMultiColor` 没有抗锯齿，3 px 高的条落在半像素上时边缘会出锯齿，所以不用。
  - 滑块外面先画一圈主题色光环，半径 8 + 2 × 状态值，透明度 30 + 40 × 状态值；里面是白色滑块，半径 5 + 0.6 × 状态值。静止时光环很淡，悬停和拖动时逐渐放大、变亮。
- 热键菜单（`BindElement`，168 宽，行高 30，在鼠标位置弹出）：
  - 每个热键一行：键盘图标 + `Toggle "F"` / `Hold "F"`（正在录键时显示 `Press a key...`）+ 右箭头，点开它的子菜单。
  - `All Hotkeys`：右侧列出所有模块的全部热键（模块名 + `Toggle "F"`），只看不改。
  - `New Hotkey`：新建一个 Toggle 热键并马上开始录键，同时打开它的子菜单；没录上键就取消（鼠标左键）时这个热键会被删掉。
  - 分隔线下面是红色的 `Reset All`：删除这个模块的全部热键。
  - 子菜单（184 宽，贴在菜单右边、和这一行对齐）：`Key`（右边是按键徽标，点了重新录键，录键中显示 `...`）、`Mode`（主题色胶囊，Toggle 是半圆图标、Hold 是手掌图标，点一下切换）、`Value` 和 `Visible`（开关）、分隔线、红色的 `Delete`。
  - Mode 胶囊切换时有过渡动画（速率 14）：胶囊宽度在两种文字宽度之间平滑过渡，旧的图标和文字向上滑出、新的从下方滑入，透明度分别按 `(1 - t)²` 和 `t²` 交叉淡化，和数字滚动的做法一致。每个热键单独记动画状态。
  - 图标都是 Font Awesome 5：键盘 F11C、列表 F03A、加号 F055、撤销 F0E2、钥匙 F084、链接 F0C1、眼睛 F06E、垃圾桶 F2ED、半圆 F042、手掌 F256。
- Settings 页（按这个顺序，和账号菜单里 HUD Alpha 下面紧跟 Background Alpha 一致）：Theme（Dark / Light）、Theme Color（色块 + 调色板）、Size（60%～150%，拖动滑条或点数值直接输入）、HUD Scale（所有 HUD 元素的缩放，50%～200%，默认 100%）、HUD Alpha（所有 HUD 背景色透明度的倍数，0%～100%，默认 100%，即设计稿原本的透明度：水印 70%、热键列表标题 70%、小胶囊 60%；只影响底色，文字、图标、模糊和阴影不变；经 `Style.alpha()` 传给每个 HUD 元素）、Background Alpha（整体 UI 底色的不透明度，0%～100%，默认 100%；面板和弹窗的描边、阴影也乘这个比例，否则底色调淡后一圈不透明的深色描边和阴影会很显眼）、HUD Menu Size（右键 HUD 弹出的设置窗口的缩放，60%～150%，默认 125%，和 Size 互不影响）、Custom Client Name、Client Name（打开 Custom Client Name 后出现；字标里的名字跟着变，默认 Moonlight）。
- 顶栏：配置下拉框（见 5.8）和右上角的搜索按钮（手画的放大镜，半径 5、线宽 2 的圆加斜柄；平时是 `secondary` 和 `main` 按 0.75 混合的浅灰，悬停或搜索打开时变成 `textHighlight`）。点它打开搜索浮层。
- 搜索浮层（`Search`）：
  - 是面板窗口里最后创建的子窗口 `search`，所以画在所有内容之上。先对整个面板（侧栏和内容区一起）画一层模糊，再画搜索面板。打开或淡出期间，面板其余部分包在 `beginDisabled` 里，并且被这个盖满面板的子窗口挡住，点不到。
  - 搜索面板 x 168～740、y 10 起（设计单位），圆角 10，底色叠两层 `windowBg` + `contentBg`（和内容区一样，所以深 / 浅色主题不用另外配色），四周 1 像素 `contentBorder`，带阴影。
  - 顶部是 43 高的搜索行：左边 Font Awesome 放大镜（中心距左边 22），x 42 起是输入框（15 号字，`textHighlight`，无底色无边框），右边 × 按钮（中心距右边 26，悬停变亮，点击关闭）。打开时输入框自动获得焦点。
  - 没输入时面板只有搜索行那么高；输入后高度按 `1 - e^(-14·dt)` 长到 558，搜索行下面一条 `split` 色分隔线（两端缩进 13），下面是结果列表。
  - 结果：名字包含输入文字（不分大小写）的模块，每个一张满宽卡片（第一行 Enabled，后面是全部设置项，用 `PageLayout.card`）。卡片上方是面包屑 `分类 › 子分类 > 模块名` 加链接图标（12 号大写字，`caption` 色）。点链接图标跳到这个模块所在的页面（有子分类时同时展开侧栏分类）并关闭搜索。没有结果时显示 `No matching modules`。
  - 结果列表用 `Scroller` 平滑滚动，输入变化时回到顶部。
  - 打开 / 关闭按 `1 - e^(-18·dt)` 淡入淡出：整个浮层画完后用 `Natives.transform` 乘透明度，模糊和阴影也跟着淡。
  - 关闭：× 按钮、Esc（游戏里 `ClickGuiScreen` 先拦下 Esc 关搜索，不关 ClickGUI；预览里 ImGui 自己收到 Esc）、点链接跳页、关掉 ClickGUI。重新打开时输入框是空的。
- 左下角：玩家皮肤头像（脸 + 帽子层，`Textures.face`）、账号名，下面一行是 Beta。点开是账号菜单（宽 280），头部是头像、账号名和 Beta，下面依次是：
  - Menu Scale、HUD Scale、HUD Alpha、Background Alpha：行内紧凑滑条加数值框（和设置弹窗里的一样，点数字可以直接输入），拖动时实时改 Size / HUD Scale / HUD Alpha / Background Alpha。Menu Scale 拖动中面板不缩放，松手后才生效，所以不会边拖边跳。
  - Theme：右侧弹出 Dark / Light，当前项高亮。
  - Theme Color：右侧是色块，点开调色板。
  - Client Settings：跳到 Settings 页。
- 二级弹窗放不下时往上移，不会超出屏幕底部。
- 圆角要同心：弹窗外框圆角 16、内边距 4，所以里面每一行的悬停 / 选中底色圆角是 12（账号菜单的行、Theme 选项、格式下拉、`Widgets.choice` 都是）。新写弹窗内的行也按"外圆角 − 内边距"取值。

**主题**

- `Theme.of(light, accent, opacity)` 按深 / 浅色、主题色和 Background Alpha 生成一套配色，`ClientSettings.colors()` 在三者变化时才重新生成。主题色就是 `iconHighlight`，悬停 / 选中底色（`selectorElementHover`、`selectorElementActive`）和滚动条拖动色由它算出。
- Background Alpha 用 `Theme.fade` 乘到这些底色的 alpha 上：主窗口（`windowBg`，也就是侧栏）、内容区（`contentBg`、`contentBorder`）、卡片和设置弹窗内卡片（`modulePanelBg`，`Controls` 的卡片色映射到它）、弹窗玻璃底板和描边（`Controls.c()` 里的 (20, 20, 29) 和 (57, 61, 76)）。后处理的模糊照常透出来，文字、控件和描边不受影响。HUD 水印有自己的 Background Alpha（模块设置），和这个分开。
- `Controls` 里写死的强调色 (75, 126, 255) 由 `c()` 换成当前主题色，所以开关、滑条、选择框悬停描边都跟着变。HUD 的图标和 logo 蓝色层也用主题色（logo 层 alpha 0.8），通过 `Style` 传给 HUD 元素。
- 两套主题的排版和配色都按参考图来，结构相同：
  - 整个窗口铺 `windowBg`，侧栏就是这一层，不再单独铺侧栏底色。
  - 内容区是一块单独的圆角面板：`contentBg` 叠在 `windowBg` 上；左侧两个圆角压在侧栏上，所以侧栏和内容区之间不画分隔线。左边的 1 像素描边（`contentBorder`）用路径单独描（左上圆弧、左边直线、左下圆弧，`pathArcTo` + `pathStroke`），不能用"先画一个描边色矩形再盖内容底色"，因为深色主题的内容区是半透明的，底下的描边色会透上来。
  - 顶栏不单独铺底色，分隔线（`barLine`）两端各缩进 13，侧栏标题下面不画线。配置框不填底色，描边和保存按钮与名字之间的竖线都用 `contentBorder`。
  - 导航选中项不铺底色，选中图标和文字一样用 `textHighlight`，不用主题色。账号按钮的底色用 `categoryHighlight`（alpha 写成 0，画的时候用 `reAlpha` 按悬停 / 弹窗打开程度补上）。
  - `Controls` 里控件写的是一套固定颜色，`c()` 按 RGB 换成主题字段：开关轨道 / 圆钮（`switchDisabledBg`、`switchKnob`）、选择框底色 / 悬停 / 描边 / 文字 / 箭头（`selectorBg`、`buttonHoveredBg`、`selectorBorder`、`main`、`secondary`）、滑条轨道（`sliderBg`）、卡片标题 / 底色 / 描边（`caption`、`modulePanelBg`、`cardBorder`）、弹窗底板 / 描边。
- 深色主题：颜色和透明度按参考图（蓝色背景上截的）反推。三层都是同一个深色加不同透明度，越往里越不透明：
  - 窗口 / 侧栏 (13, 15, 22) 80%（alpha 204）。在 (117, 159, 239) 的蓝底上合成 (34, 44, 65)，参考图 (36, 44, 62)。
  - 内容区 (13, 15, 22) 46%（alpha 117），叠在侧栏那层上，合成 (24, 31, 45)，参考图 (24～26, 31, 42～44)。两层合起来约 89% 不透明，所以侧栏比内容区透。
  - 卡片 (13, 14, 21) 64%（alpha 163），叠在内容区上，合成 (17, 20, 30)，参考图 (18, 20, 29)。
  - 不透明的部分：内容区左描边 (35, 37, 46)，卡片描边 (28, 31, 41)，顶栏分隔线 (37, 42, 53)；行分隔线、选择框、数值框、关闭的开关轨道 (25, 25, 33)，选择框描边 (34, 34, 42)，滑条轨道 (35, 37, 46)。
  - 文字：正文 (216, 219, 228)，标题、用户名和选中项 (226, 229, 237)，未选中导航、三个点、箭头、关闭的开关圆钮和次要文字 (150, 154, 166)，侧栏分组标题 (108, 112, 126)（`navLabel`），卡片标题 (105, 109, 123)（`caption`）。
  - 外框没有描边（`windowBorder` 的 alpha 为 0）。
  - 离线测试 `test/src/.../DarkTest` 把背景换成参考图的蓝色（`Raster.solid`）渲染，用来对照取色。
- 浅色主题：
  - 侧栏 (185, 188, 194)，外框描边和侧栏同色（看不出来）；内容区 (220, 221, 223)，左描边 (205, 205, 205)。
  - 顶栏分隔线 (210, 210, 210)。
  - 卡片 (229, 229, 229)，描边 (214, 214, 214)，行分隔线、选择框、数值框、关闭的开关轨道 (210, 210, 210)，选择框描边 (200, 200, 200)，滑条轨道 (205, 205, 205)，关闭的开关圆钮是白色。
  - 文字：正文 (55, 55, 55)，标题、用户名和选中项 (40, 40, 40)，未选中导航、三个点、箭头和次要文字 (100, 100, 100)，侧栏分组标题 (137, 137, 137)，卡片标题 (140, 140, 140)。
  - 弹窗底板 (236, 236, 236, 245)、描边 (200, 200, 200, 205)，也乘 Background Alpha。字标主色在浅色下用 `clientName`。
- 窗口居中显示，可以按住空白处拖动；拖动过的位置在改窗口大小后保留。窗口放不下时自动缩小。

### 5.11 旋转

代码在 `wtf.moonlight.rotation`：`Rotation`（record，`yaw` / `pitch`）和 `RotationManager`。计算用的工具方法在 `utils` 的 `RotationUtils`、`RaycastUtils`、`MoveUtils`（见 5.6）。`RotationManager` 全局只有一个，`MoonlightClient` 创建它并注册到事件总线，用 `MoonlightClient.getRotations()` 取。

作用是把**发给服务器的视角**（下面叫服务端视角）和玩家自己看到的视角分开：画面不跟着转，服务器、其他玩家和客户端里所有依赖视角的判定（准星目标、移动方向、使用物品、模型朝向）都按服务端视角来。

**用法**

```java
RotationManager rotations = MoonlightClient.getRotations();
rotations.setRotations(RotationUtils.calculate(target), 30);
rotations.setRotations(RotationUtils.calculate(target, true, 3.0), 30,
        rotation -> RaycastUtils.rayCast(rotation, 3.0) instanceof EntityHitResult hit && hit.getEntity() == target);
```

| 方法 | 作用 |
|---|---|
| `setRotations(目标, 速度)` | 服务端视角每 tick 最多转 `速度` 度（偏航和俯仰按比例分配）。需要保持时每 tick 都调用；不再调用后，服务端视角从下一 tick 开始转回玩家视角，偏航（取模后）和俯仰都差不到 1° 时结束，结束时把玩家偏航整圈平移到和最后发出的偏航同一圈（见下方"结束时的整圈平移"） |
| `setRotations(目标, 速度, 检查)` | 同上。目标和当前服务端视角相差超过 5° 时，在目标附近加随机偏移，用 `检查` 判断偏移后是否还满足条件（例如还能射中目标）；不满足就换个方向偏移，还不行就直接对准目标再加 0～2° 随机量 |
| `getRotation()` | 当前服务端视角；没在转时返回玩家视角 |
| `setRotation(r)` | 直接设定服务端视角，不经过平滑（配合 `setActive(true)` 使用） |
| `getLastRotation()` | 上一 tick 发出去的视角 |
| `isActive()` / `setActive(b)` | 是否正在使用服务端视角 |
| `isSmoothed()` / `setSmoothed(b)` / `smooth()` | 每 tick 只平滑一次；`smooth()` 在这一 tick 还没平滑过时算一次，然后调用 `Minecraft.pick` 刷新准星目标 |

**一个 tick 里的顺序**

| 时机 | 事件（优先级） | 做什么 |
|---|---|---|
| `LocalPlayer.tick` 开头 | `PlayerTickEvent`（LOW） | 没在转时，把服务端视角、上次视角、目标都对齐到玩家视角；在转时调用 `smooth()` |
| `KeyboardInput.tick` | `MoveInputEvent`（HIGH，在 `Sprint` 之前） | 移动修复 |
| 移动、跳跃 | `StrafeEvent`、`JumpEvent` | 移动方向和疾跑跳方向用服务端偏航 |
| 发移动包 | `MotionEvent`（LOWEST，最后执行） | 把服务端视角写进移动包（俯仰限制在 ±90°，NaN 不写）；记下这一 tick 发出的视角；和玩家视角相差不到 1° 时结束旋转并整圈平移玩家偏航；清掉平滑标记和 `检查` |
| 新的玩家对象 | `RespawnEvent` | 清空服务端视角和上次视角 |

其他钩子：

- 准星目标（`RaycastEvent`）、物品视线（`ItemRaycastEvent`）、使用物品包（`UseItemEvent`，HIGHEST）：在转时用服务端视角。`smooth()` 之后马上刷新了准星目标，所以同一 tick 里接着攻击、放方块就是对着服务端视角的。
- 鞘翅俯仰（`FallFlyingEvent`）、击退方向（`AttackYawEvent`）：只要有服务端视角就用它，不在转时它就等于玩家视角。
- 模型（`RotationAnimationEvent`）：在转时，本地玩家模型的身体、头部偏航和俯仰按发给服务器的视角显示，第三人称里看到的就是别人看到的样子。

**平滑**（`RotationUtils.smooth`）

- 先按速度限制这一 tick 的转动量（`move`），速度另加 0～1 的随机量。偏航差按 `wrapDegrees` 取最短方向，所以服务端视角的偏航是连续的（可以超出 ±180°），不会一下跳一整圈。目标和上次视角完全重合时转动量是 0（不能除以 0 得到 NaN，否则视角会跳回玩家上一 tick 的偏航）。

**结束时的整圈平移**

Minecraft 的偏航不取模，转几圈就会累积到 ±720° 之类的值。服务端视角沿最短方向转回玩家视角时，停下来的位置可能和玩家偏航差了整整 360°（例如玩家 170°、服务端视角 -190°）。如果直接交还，下一个移动包就从 -190 跳到 170，反作弊的 `AimModulo360` 检查（一次偏航变化超过 320° 而上一次小于 30°，当前偏航又在 ±360° 内）会马上报。

所以结束时以这一包实际发出的偏航为准，把玩家偏航加减整数个 360°，挪到同一圈里。画面完全不变，只是换了一种表示，下一包的偏航变化就不到 1°。`yRotO`（渲染插值）和 `yBob` / `yBobO`（手臂摆动，第一人称手部按"视角偏航 - 摆动偏航"算偏转）同时平移，否则这一帧会转一圈、手会甩一大下。不做鼠标步长修正：那样会改变玩家看到的画面。
- 再做若干次（约 FPS / 20 + 0～10 次）GCD 修正：转动量取整到当前鼠标灵敏度的最小步长（`applySensitivityPatch`，换算和 `MouseHandler` / `Entity.turn` 一致），在动的时候每次加一点随机抖动。

**移动修复**（`MoveUtils.fixMovement`）

服务端偏航和玩家偏航不同时，如果按原来的键走，服务器算出的移动方向和实际不一致。修复的做法：先算出玩家想走的方向（玩家偏航加上按键组合对应的 0 / ±45 / ±90 / ±135 / 180°），再相对服务端偏航选出最接近的按键组合（每个方向留 ±22.5° 容差，斜向就同时按两个键）。

26.3 的移动输入是 `Input` 里的布尔按键，`KeyboardInput.tick` 再由它算出 `moveVector`，所以修复直接改前后左右四个键，跳跃、潜行、冲刺不变。改过的 `Input` 也会作为 `ServerboundPlayerInputPacket` 发给服务器，和服务器看到的移动方向一致。没按方向键时不处理。

**26.3 上要注意的地方**

- 准星检测在 `Minecraft.pick`（私有），通过 `MinecraftAccessor` 调用。
- 使用物品时视角直接写在 `ServerboundUseItemPacket` 里，没有单独的位置包，所以改的是这个包。注入目标是 lambda `lambda$useItem$0`，原版和 NeoForge 的 jar 里名字相同，升级版本时要重新核对。
- 第三人称模型的头部偏航在 `LivingEntityRenderer.extractRenderState` 的 `Mth.rotLerp`，俯仰是 `entity.getXRot(partialTicks)`。
- 击退方向在 `Player.causeExtraKnockback`；客户端只有 `hurtClient` 返回 `true` 的实体会走到这里。
- 骑乘时不触发 `MotionEvent`（见 5.3），服务端视角不会写进骑乘时发的 `Rot` 包。

目前用到旋转系统的模块（见 5.7）：

- `Scaffold`：`setRotations(目标, 速度)`、`getRotation()`、`RotationUtils.calculate(BlockPos, Direction)`、`RaycastUtils.overBlock` 的两个重载。
- `KillAura`：`setRotations(目标, 速度, 检查)`、`RotationUtils.calculate(Entity, true, 距离)`（连带 `calculate(Entity)`）、`RotationUtils.getDistance(Entity)`、`RaycastUtils.rayCast(Rotation, 距离)`。

### 5.12 后处理（模糊和阴影）

给 ImGui 画的界面加背景模糊和外阴影。开关和参数在 Render 分类的 `PostProcess` 模块（`module.impl.render.PostProcess`），默认关闭：

| 设置项 | 范围 | 默认 | 说明 |
|---|---|---|---|
| Render Blur | 开 / 关 | 开 | 背景模糊 |
| Render Shadow | 开 / 关 | 开 | 外阴影 |
| Blur Amount | 1～8 | 4 | 模糊金字塔层数，每层分辨率减半 |
| Blur Offset | 1～10 | 10 | 模糊采样偏移 |
| Bloom Amount | 1～8 | 2 | 阴影金字塔层数 |
| Bloom Offset | 1～10 | 2 | 阴影扩散距离；超过 2 时自动拆成更多层，见下方"怎么实现的" |
| Shadow Opacity | 0～100% | 60% | 所有 UI 阴影的浓度，乘在阴影颜色的 alpha 上 |

**在界面里用**

```java
ImDrawList draw = ImGui.getWindowDrawList();
draw.pushClipRectFullScreen();
Effects.blur(draw, minX, minY, maxX, maxY, rounding, 0);
Effects.shadow(draw, minX, minY, maxX, maxY, rounding, 0, Colors.rgb(0, 0, 0));
draw.popClipRect();
// 然后画这个元素自己的半透明底色、边框和内容
```

- 两个方法都在元素背景**之前**调用，**先模糊后阴影**。模块关着、或者对应开关关着时什么都不画，没有开销。
- 顺序不能反：模糊会对当时的主画面取样，如果先合成阴影，这个形状自己的阴影会被模糊进玻璃里，边缘内侧出现一圈黑边（离线测试量到默认参数下最多暗 10/255）。阴影在形状内部是挖空的，所以先画模糊、再画阴影，外观不变，玻璃也干净。
- 参数和 `addRectFilled` 一样（屏幕坐标、圆角、`ImDrawFlags`）。阴影颜色就是遮罩颜色：RGB 决定阴影颜色，alpha 决定阴影浓度。一般用黑色，想要彩色阴影就传主题色。同一组连续的阴影形状只用一种颜色（取 alpha 最大的那个）。
- 阴影要画到元素外面，所以先 `pushClipRectFullScreen()`，否则会被窗口剪裁矩形切掉。
- 效果命令是普通的 ImGui 形状，入场、翻页、弹窗动画会一起缩放和淡入淡出。
- ClickGUI 的主窗口（`Panel`）和所有子窗口都已经接好：子窗口统一走 `Widgets.beginPopup`，它在弹窗玻璃底板之前加模糊和阴影，所以新加的弹窗自动带效果。阴影色统一用 `Controls.SHADOW`（不透明黑），实际浓度再乘 Shadow Opacity：默认 60% 时，边缘约 29%，往外渐隐。默认参数下阴影只扩散约 15 px，浓度太高就像一圈黑描边，所以默认值没有用 100%。
- 阴影只出现在形状外面：挖空按形状的覆盖率算，不管遮罩颜色多透明，形状内部都完全挖掉。

**怎么实现的**

- `Effects` 用两个保留的纹理 ID（`-2` 模糊、`-3` 阴影）画普通的 ImGui 圆角形状，`ImGuiRenderer` 按命令顺序执行：普通命令照常画；遇到阴影命令就暂停主绘制，把形状的"颜色 alpha × 覆盖率"写进遮罩，做 Kawase 金字塔，挖掉形状内部后合成回屏幕；遇到模糊命令就从主渲染目标做 Dual Kawase 金字塔，再用 `imgui_blur` 着色器按屏幕位置把结果画进这个形状。
- 挖空：渲染器取这一组阴影形状里最大的顶点 alpha 当作颜色 alpha，挖空时用 `1 - min(遮罩 / 颜色 alpha, 1)`，也就是 `1 - 覆盖率`。
- **阴影的中间结果全部存在 RGB 里，不用贴图的 alpha 通道。** 26.3 里这些离屏贴图的 alpha 读出来始终是 1（清空成 0 也一样），一开始按 alpha 存遮罩，结果遮罩背景变成 1，阴影要么朝内要么完全被挖掉。现在遮罩、金字塔、挖空结果都只用 R 通道；最后合成时由 `shadow` 着色器从 R 算出 alpha，阴影颜色用 uniform 传进去。以后写后处理也一样：要存的数据放 RGB，输出 alpha 只在合成到主画面那一步由着色器算出来。
- Bloom Offset 越大，Kawase 每一遍的采样点隔得越远，超过约 2 个像素就会跳格，出现一圈圈台阶状的重影。所以 Offset 超过 2 时，每翻一倍就多降一层金字塔、步长减半，让每一遍的步长始终不超过 2。扩散范围基本不变，重影消失；Offset ≤ 2 时和原来完全一样。
- 弹窗画在主窗口后面，所以弹窗的模糊会从已经画了主窗口的画面重新取样，阴影也会落在主窗口上。
- 所有离屏 pass 都用剪裁矩形限制在效果区域加外扩的范围里（外扩量按层数和偏移算，离线测试验证过和整屏计算结果一致）。
- 模糊只取 ImGui 之前的画面（游戏画面、原版 HUD，以及更早的元素合成的阴影；不含这个元素自己的阴影），最多每 1/60 秒重算一次，改参数或窗口大小时立即重算。如果模糊命令前面已经画过别的 ImGui 内容，就从当前画面重新算，不节流。
- 着色器在 `assets/moonlight/shaders/core/`：`blur_down`、`blur_up`（模糊和阴影金字塔共用）、`bloom_cut`（挖空输出）、`shadow`（阴影合成）、`imgui_mask`（把阴影形状写进遮罩）、`imgui_blur`（顶点和片元各一个）。全屏 pass 的顶点着色器复用原版的 `core/screenquad`。
- **着色器里不要用 `gl_FragCoord`。** 26.3 先按 Vulkan 规则用 shaderc 把 GLSL 编成 SPIR-V，再用 spvc 转成 OpenGL 的 GLSL；`gl_FragCoord` 的原点约定和 OpenGL 渲染目标的行序对不上，按它取样会上下颠倒，原版着色器也一处都没用。需要屏幕坐标时在顶点着色器里用 `gl_Position.xy / gl_Position.w * 0.5 + 0.5` 算出来再传下去（`imgui_blur.vsh` 就是这样），它和 `screenquad` 的 `texCoord` 约定一致。
- 离屏目标第一次用到时才创建，模块关掉后释放。后处理出错只记一次日志并在本次运行里停用，ImGui 界面照常显示。

### 5.13 HUD 水印

Render 分类的 `Watermark` 模块，在屏幕上画一条胶囊，显示哪些段由 Items 勾选。

| 设置项 | 范围 | 默认 | 说明 |
|---|---|---|---|
| Title | 文本 | `ML` | logo 的文字，留空就不画 logo |
| Items | 多选 | Logo、FPS、User、Time、Avatar | 要显示的段，见下表；没勾的段连同前面的间距一起去掉 |
| Position | 四个角 | Top Left | 锚定的角。在设置里选一个角时，X / Y 重置成 12，元素固定贴到这个角，和之前拖到哪里无关；拖动时自动改成离元素中心最近的角 |
| X / Y | 隐藏 | 12 / 12 | 离锚定角的距离（设计单位），拖动时写入，选 Position 时重置，随配置保存 |
| Scale | 0.5～3 | 1.5 | 1 设计单位 = Scale × HUD Scale（Client 设置，全局）× 系统显示缩放 个像素 |
| Animation | 100～1000 ms | 300 ms | 展开 / 收起的时长 |

Items 可选的段如下（`Watermark.Item`）。表里是默认顺序；在 Items 下拉框里拖动可以改，HUD 从左到右按改过的顺序画（`Watermark.items()` 返回勾选了的段，按用户顺序排好）。图标列是勾选列表里显示的图标，也是 HUD 上画的图标（Logo、Avatar 只在勾选列表里用）。

| 段 | 图标 | 内容 | 拿不到时 |
|---|---|---|---|
| Logo | `dark_mode` | Title 文字，蓝色错位两层加白色顶层 | — |
| FPS | `bar_chart` | `244 FPS 98  (Low) 127  (1%L)` | 在 Items 列表里右键 FPS 展开两个子选项：Low（默认关）、1% Low（默认开） |
| User | `person` | 账号名 | — |
| Server | `dns` | `ServerData.ip`；单人世界显示 `Singleplayer` | `--` |
| Ping | `wifi` | 自己在玩家列表里的 `getLatency()`，`42 ms` | `-- ms` |
| TPS | `speed` | 服务器 TPS 估算值，`19.9 TPS` | `-- TPS` |
| Coordinates | `location_on` | 脚下方块坐标，`x, y, z` | `--` |
| Speed | `bolt` | 水平速度，`4.3 b/s` | `-- b/s` |
| Version | `deployed_code` | `SharedConstants.getCurrentVersion().name()` | — |
| Time | `history_2` | `HH:mm` | — |
| Avatar | `account_circle` | 皮肤头像；在最左或最右时那一侧的边距是 4（其他段是 9 / 10），放在中间时和其他段一样隔 8 | — |

**画在哪里**

- `MoonlightClient` 给 `ImGuiManager` 的每帧回调里先调 `Hud.frame`（画所有 HUD 元素、处理编辑），再画 ClickGUI。HUD 画进 `ImGui.getBackgroundDrawList()`，按 F1 隐藏 HUD 时不画也不能编辑。
- 背景和 ClickGUI 主窗口同一套：`Effects.blur` → `Effects.shadow`（黑色，alpha 0.7）→ 半透明黑底，圆角为高度的一半。阴影和模糊的强度跟随 `PostProcess`。
- 跟随 Client 设置的主题：浅色时底色换成白色、文字和 logo 顶层换成深色 (28, 30, 38)，阴影 alpha 降到 0.3；图标和 logo 蓝色层用主题色。
- 水印是每帧第一批 ImGui 命令，所以它的模糊按 5.12 的规则节流到 60 Hz。打开 ClickGUI 时，主窗口的模糊排在水印之后，会每帧重算，也会模糊到后面的水印。

**排版**

- 所有尺寸用"设计单位"写在 `WatermarkRenderer` 的常量里，来自设计稿：高 28，文字基线 18，图标和头像中心 13.5，段间距 8，图标到文字 2，FPS 和 1% low 之间 3，左边距 9，右边距：头像 4、文字 10。
- 从左到右流式排列，宽度随内容变。
- 每一段的横坐标也单独按同样的速率平滑（`WatermarkRenderer.slots`，设计单位，按 `Item` 下标存）：在 Items 里拖动换顺序时，各段从旧位置滑到新位置，互换的两段会交错滑过对方；某段勾选了重新出现时直接出现在目标位置，其余段滑开让位。
- 换顺序时还有一次"呼吸"：只要显示的段集合没变、但先后顺序变了（真正的优先级交换，勾选 / 取消勾选不算），位置变了的段会变淡到约 55%（`BREATH` = 0.45），在最后一次交换后保持 0.18 秒（`BREATH_HOLD`），再按 `1 - e^(-10·dt)` 恢复，进出都是平滑的；拖着连续换好几格时一直保持淡的状态，松手后一起亮回来。位置没变的段不受影响。淡化作用在这一段的文字、图标、Logo 两层和头像上（`tone()`，头像通过 `Textures.face` 的 tint 参数）。
- 宽度和左上角位置都按 `1 - e^(-12·dt)` 平滑：内容变宽变窄，或者在设置里改 Position 时，背景连同内容滑到新的角，不会跳。两者速率相同，所以贴右边时宽度变化，右边缘仍然不动。拖动时位置直接跟着鼠标，不做平滑。
- 模块开关时从所在角那一侧按 easeOutCubic 展开 / 收起，内容按可见部分裁剪。
- 新加的段（Server、Ping、TPS、Coordinates、Speed、Version）用 13 的图标框，和时间一样；FPS、User 用 15。
- 数字（FPS、1% low、Ping、TPS、Speed、时间）每一位放在"最宽数字"那么宽的格子里居中，位数不变时宽度不会抖；某一位变化时旧字符向上滑出、新字符从下方滑入，同时交叉淡化：旧字符透明度是 `(1 - e)²`，新字符是 `e²`（`e` 是 easeOutCubic 后的进度），滚到一半时两个字符都只剩约 25%，不会叠成一团。` FPS`、`  (1%L)` 这些后缀不动。
- **ImGui 字号不是 em。** stb 后端用 `stbtt_ScaleForPixelHeight`，把 ascent − descent 映射到字号。设计稿里的 em 字号要乘 `Fonts.HUD_EM`（Inter 1.2100）、`HUD_ICON_EM`（1.2）、`WORDMARK_EM`（1.2）；文字顶部 = 基线 − em × `*_ASCENT`。换别的字体时要按它的 `hhea` 重新算这两个系数。
- ImGui 画文字时会把坐标截断到整像素，所以位置都先 `Math.round` 再传进去。

**数据**

- FPS 和 1% low：每个 ImGui 帧记一次 `io.deltaTime`，保留最近 5 秒（最多 4096 帧），每 0.5 秒重算：FPS = 最近 1 秒的帧数 / 这些帧的总时长；1% low = 最慢的 1% 帧（至少 1 帧）的平均时长的倒数；Low = 最慢那一帧时长的倒数（`FrameStats.minimum()`）。
- Server、Ping、TPS、Coordinates、Speed、Version 由 `Tracker` 提供（`MoonlightClient` 创建并注册到事件总线），渲染器每帧取一次 `Snapshot`：
  - TPS：收到 `ClientboundSetTimePacket` 时记下（到达时间, `gameTime`），保留最近 6 个，TPS = 游戏刻差 / 真实秒数，夹在 0～20。游戏刻倒退、两包间隔超过 10 秒或换世界时清空；最近一包超过 10 秒也算拿不到。收包在网络线程，所以加了锁。
  - Speed：每个 tick 结束时按 `(x - xo, z - zo)` 的长度 × 20 算 b/s。
  - 测试里直接传一个固定的 `Snapshot`，不依赖游戏。
- 头像：在世界里取 `player.getSkin()`，不在时用 `SkinManager.createLookup(mc.getGameProfile(), false)`，画皮肤的脸（UV 8–16）再叠帽子层（UV 40–48），圆形裁剪。

**编辑 HUD（拖动和右键设置）**

- 打开聊天栏或 ClickGUI 时进入编辑模式。
  - 鼠标移到 HUD 元素上会出现一圈白色描边，ClickGUI 里光标同时变成移动形状。
  - 按住左键拖动：元素跟着鼠标走，不会拖出屏幕。松开后按元素中心落在哪个象限，选最近的角作为 Position，并把离这个角的距离写进 X / Y。换分辨率后，元素仍然贴着同一个角。
  - 拖动时的辅助线和吸附（`Hud.drag`）：被拖元素的左边 / 中线 / 右边去对屏幕竖直中线以及其他 HUD 的左边 / 中线 / 右边，上边 / 中线 / 下边同理去对屏幕水平中线和其他 HUD 的上 / 中 / 下。距离在 6 px（乘系统显示缩放）以内就吸过去，横竖两个方向各自取最近的一条。吸上的那条画成贯穿屏幕的主题色细线（alpha 200，粗 1 × 显示缩放，画在背景层，所以在 HUD 上面、ClickGUI 窗口下面），按 `1 - e^(-18·dt)` 淡入淡出。
  - 右键打开这个模块的设置弹窗，内容和 ClickGUI 里点 `...` 打开的设置弹窗一样（Bind 加全部可见设置项）。离开编辑模式（关掉聊天栏或 ClickGUI）时弹窗自动关闭。
- **设置弹窗的位置**：锚在 HUD 元素的矩形上，`Hud` 每帧把矩形交给 `Panel.follow`。
  - 最佳位置：HUD 右侧、和顶部对齐；右边放不下放左侧；两侧都放不下放到下方（下方也放不下就上方）；最后夹在屏幕内。
  - 弹窗按 `1 - e^(-12·dt)` 平滑地滑向最佳位置，所以 HUD 换角或被拖走时弹窗跟着动画移动。
  - 按住弹窗顶部标题栏可以手动拖动，松手后停在那里。之后 HUD 只是变宽变窄（比如勾选 Items）不会动它；HUD 真正移动（左右两边同时变或上边变了）时取消手动位置，重新滑到最佳位置。
  - HUD 弹窗关掉了 ImGui 自己的窗口拖动（`NoMove`），只用标题栏拖，免得和每帧设置的位置抢。
- 鼠标在 ClickGUI 窗口或设置弹窗上时不算悬停 HUD，所以面板下面的 HUD 不会被误拖。
- **聊天栏里的输入**：原版只把输入交给 `ChatScreen`，`Hud` 监听输入事件，自己转发给 ImGui。
  - 鼠标移动（`MouseMoveEvent`）和按键都转发。按下时，如果鼠标在 HUD 元素或弹窗上，或者正在拖动，就取消原版处理，不点到聊天里的文字；对应的松开也一并取消。
  - 设置弹窗里的文本框获得焦点时，按键（`KeyInputEvent`）和字符（`CharInputEvent`）都交给 ImGui 并取消原版处理，打的字不会进聊天框；Esc 只退出文本框，不关聊天栏。
  - 打开聊天栏时给 ImGui 发获得焦点和当前鼠标位置；关掉时（不是切到 ClickGUI）发失去焦点并把鼠标移出窗口，清掉 ImGui 里残留的按键状态。
- ClickGUI 打开时输入由 `ClickGuiScreen` 照常转发，`Hud` 只做悬停和拖动判断。
- 弹窗由 `Panel.settings(module)` 打开，用 `Row.popup` 画，和 ClickGUI 里的设置弹窗是同一个。
  - 面板没打开、只有 HUD 弹窗时，`Panel.frame(open, editing)` 也会套上 ClickGUI 的主题、字体和弹窗淡出。面板和 HUD 弹窗都没有时，`Panel.frame` 仍会调用 `popups.drawClosing()` 把刚关掉的弹窗淡出播完。以前这时不渲染，淡出停在第一帧，下次打开别的 HUD 弹窗时旧弹窗会闪一下。
  - 弹窗跟着 HUD 走，包括拖弹窗里的 Scale 滑条时：HUD 实时变大，弹窗同时滑过去。这要求滑条按相对位移计算（见下面的 `Controls.slider`），否则滑条跟着弹窗移动会和鼠标位置互相拉扯。
  - 缩放和面板分开：面板用 Size，HUD 弹窗用 Client 页的 HUD Menu Size（默认 125%），各乘系统显示缩放。两者同时显示时，`Panel` 分两遍画，每遍各自缩放样式。
- **新加 HUD 元素**：实现 `HudElement`。
  - `bounds()`：这一帧实际画出来的矩形，没画时返回 null。
  - `place(x, y)`：拖动时传入的新左上角像素坐标，元素自己换算成锚点和偏移，并在下一次 `frame()` 直接跳到那里。
  - `frame(Style)`：绘制；`Style` 带全局 HUD Scale、深 / 浅色、主题色、HUD Alpha，以及现在是不是编辑模式（聊天栏或 ClickGUI 开着，通知 HUD 用它决定画不画示例）。
  - `module()`：右键打开哪个模块的设置。
  - 然后在 `MoonlightClient` 里加进 `Hud` 的列表。

**游戏纹理**

ImGui 的纹理 ID 本来只对应 ImGui 自己的图集。`Textures.of(Identifier)` 给游戏纹理分配一个 `2^40` 起的 ID，渲染时 `ImGuiRenderer` 按 ID 从 `TextureManager` 取 `GpuTextureView`，并用 `NEAREST` 采样（皮肤是像素画）；ImGui 自己的纹理仍用 `LINEAR`。`TextureManager.getTexture` 第一次取某个纹理时会当场加载并上传（`writeToTexture`），而 RenderPearl 不允许在渲染通道打开时执行别的命令，否则抛 `Close the existing render pass before performing additional commands`。所以每帧打开 ImGui 渲染通道之前，`ImGuiRenderer` 先把这一帧用到的游戏纹理都取一遍，画的时候拿到的都是已经加载好的。头像用 `Textures.face(draw, skin, x, y, r, tint)` 画，`tint` 带透明度，水印的"呼吸"淡化就靠它。

**位置（`module.Anchor`）**

水印、热键列表和通知共用：构造时 `new Anchor(this, corner, x, y)` 往模块里登记 Position（四个角，`corner` 是默认角：0 左上、1 右上、2 左下、3 右下）和隐藏的 X / Y（离锚定角的设计单位距离），所以它们在设置里的位置就是 `Anchor` 字段声明的位置。`left(screen, width, unit)` / `top(...)` 算出左上角像素坐标；`drop(...)` 是拖动松手时用的：夹在屏幕内，按中心落在哪个象限选角，再换算偏移。在设置里选 Position 会把 X / Y 重置成 12。

### 5.14 热键列表 HUD

Render → Overlay 的 `Hotkeys` 模块，按设计稿的 Hotkeys 组画（`ui.hud.HotkeysRenderer`）。设置只有 Position（默认左上，X 12、Y 52，正好在水印下面）和 Scale（0.5～3，默认 1.5）。

| 部分 | 设计稿（设计单位） |
|---|---|
| 标题胶囊 | 高 22、圆角 10，黑色 70% 底 + 背景模糊 + 阴影（黑色 70%）；`auto_awesome_mosaic` 图标 12 × 12 放在 (7, 5)，主题色；"Hotkeys" 用 Inter Regular 10.5，白色，x = 29，按大写字母高度上下居中（基线 14.82）；右边留 9，整体宽约 79 |
| 每一行 | 两个小胶囊：左边是按键名，右边是模块名。高 18、圆角 4，底色 (40, 32, 43) 60% + 背景模糊 + 阴影（设计稿里小胶囊没有阴影，这里补上，和标题一样）；文字 Inter Regular 10，白色，左右各留 4.5（设计稿里量出来 3.2～5.5，取平均）；上下按大写字母高度（`Fonts.HUD_CAP` = 1490 / 2048）居中，基线 12.64。设计稿的基线是 11.64，大写字母会偏上约 1，这里有意改成居中 |
| 间距 | 标题和第一行、行与行、左右两列之间都是 6；右列的 x 是左列最宽的胶囊 + 6 |

- 哪些热键会出现：Visible 打开、录了键、并且正在生效的热键。Toggle 热键在模块开着时生效，Hold 热键在按住时生效。一个模块有几个这样的热键就有几行。
- 新出现的行按 `1 - e^(-14·dt)` 淡入，消失的行淡出，同时下面的行平滑收上来（行距乘淡入进度）；左列宽度和整体位置按 `1 - e^(-12·dt)` 平滑。模块开关时整体 0.3 秒淡入 / 淡出。
- 分三遍画（`Pass`）：先给标题和所有胶囊画模糊，再画阴影，最后画底色、图标和文字。这样所有模糊连成一段只烘焙一次，所有阴影连成一段，`ImGuiRenderer` 只做一次 bloom，而不是每个胶囊各做一次；同时保证先模糊后阴影（5.12），阴影不会漏进玻璃里。
- 淡入淡出时背景模糊也跟着变淡：`Effects.blur` 多了一个带透明度的重载，模糊区域按顶点透明度混合，不会先冒出一块模糊。
- 浅色主题：标题和小胶囊换成白色 70% / 60% 底，文字用深色，阴影变浅，和水印一致。
- 底色透明度都再乘 Client 设置里的 HUD Alpha（见 5.10），水印的黑 / 白底（70%）也一样，模块里不再单独有 Background Alpha。
- 编辑模式下没有生效的热键时只有标题，可以照常拖动和右键打开设置。
- 列表和 ClickGUI 共用 `Hud`：`MoonlightClient` 里和水印一起加进 `Hud` 的列表。

---

## 6. Mixin 规范

- 配置文件是 `src/main/resources/moonlight.mixins.json`，Fabric 通过 `fabric.mod.json`、NeoForge 通过 `neoforge.mods.toml` 的 `[[mixins]]` 加载同一份。
- `mixins` 数组：客户端和服务端都会加载。`client` 数组：只在客户端加载。Moonlight 是客户端，**涉及 `net.minecraft.client.*` 的 Mixin 一律放进 `client`**，否则在专用服务端上会因为找不到类而崩溃。
- 命名：Mixin 类叫 `XxxMixin`，访问器叫 `XxxAccessor`；注入方法和访问器方法加 `moonlight$` 前缀，避免和其他模组冲突。
- `injectors.defaultRequire = 1`：注入点没找到就启动失败，问题能第一时间暴露。
- **NeoForge 改过部分原版方法**：同一个注入点在 Fabric 上正常、在 NeoForge 上可能找不到，渲染和事件相关的类最常见。遇到时切到 neoforge 节点看 NeoForge 打过补丁的源码，再用条件注释给 NeoForge 换一个注入点：
  ```java
  //? if fabric {
  @Inject(method = "xxx", at = @At("HEAD"))
  //?} else {
  /*@Inject(method = "xxx", at = @At(value = "INVOKE", target = "..."))
  *///?}
  ```
- 调试：Fabric 的运行配置带了 `-Dmixin.debug.export=true`，Mixin 处理后的类会导出到 `run/.mixin.out/`。
- 新增的 Mixin 必须在**两个加载器上都实际进游戏验证**，不能只测 Fabric。

---

## 7. 资源文件和元数据

| 文件 | Fabric 包 | NeoForge 包 |
|---|---|---|
| `fabric.mod.json` | ✅ | ❌（在 `build.neoforge.gradle` 里排除） |
| `META-INF/neoforge.mods.toml` | ❌（在 `build.fabric.gradle` 里排除） | ✅ |
| `moonlight.ct` | ✅ | ❌ |
| `moonlight.mixins.json` | ✅ | ✅ |
| `assets/moonlight/**` | ✅ | ✅ |

- 元数据里的 `${id}`、`${name}`、`${version}`、`${minecraft}`、`${loader}` / `${neoforge}`，以及 mixin 配置里的 `${java}`，都在 `processResources` 里从 `stonecutter.properties.toml` 填入。
- 这几个文件走 Groovy 模板展开，正文里如果要写字面量 `$`，需要写成 `\$`。
- 新增只给某个加载器用的资源时，记得在另一个加载器的构建脚本里加 `exclude`。

---

## 8. 构建和运行
### 5.15 通知 HUD

Render → Overlay 的 `Notifications` 模块，按设计稿的通知条画（`ui.hud.NotificationsRenderer`），一条消息一行，堆在屏幕一角。

| 设置 | 范围 | 默认 | 说明 |
|---|---|---|---|
| Position / X / Y | `Anchor` | 右下角，边距 12 | 不和左上角的水印、热键列表挤在一起 |
| Toggles | 开关 | 开 | 是否提示模块开关 |
| Duration | 1～10 s | 3 s | 每条显示多久（不含进出动画） |
| Max | 1～8 | 5 | 同时最多几条，多出来的最老一条提前离开 |
| Scale | 0.5～3 | 1.5 | 和水印、热键列表一致 |

| 部分 | 设计稿（设计单位） |
|---|---|
| 外框 | 高 22、宽 = 31 + 文字宽 + 6、圆角 5；黑色 70% 底（浅色主题白色 70%）+ 背景模糊 + 阴影，无描边 |
| 色条 | 2 × 21，贴左边；竖向渐变，10% 处透明、42%～60% 实色、90% 处透明。分三段矩形画（上下 10% 本来就透明，省掉），渐变段先画白色矩形，再用 `Natives.gradient` 改顶点色 |
| 图标 | Material Symbols Outlined（wght 300），14 × 14 放在 (10, 4)，颜色同色条 |
| 文字 | Inter Regular 10.5（设计稿是 Consolas，这里统一成其他 HUD 的字体），x = 31，按大写字母高度（`Fonts.HUD_CAP`）上下居中；白色，浅色主题用深色 |

**消息来源**

| 来源 | 事件 | 颜色 / 图标 | 文字 |
|---|---|---|---|
| 模块开关 | `ModuleToggleEvent`（`switchTo`） | 开：主题色 / `toggle_on`；关：(152, 164, 184) / `toggle_off` | `KillAura enabled`、`KillAura disabled` |
| 配置成功 | `NotifyEvent` `CONFIG` | (152, 164, 184) / `save` | `ConfigManager` 的状态文字，如 `Saved latest` |
| 配置失败 | `NotifyEvent` `ERROR` | (213, 34, 54) / `error` | 如 `latest is not a readable config` |
| 热键 | `NotifyEvent` `BIND`（`BindElement` 录完键） | (152, 164, 184) / `keyboard` | `Sprint bound to R`、`Sprint hotkey cleared` |

- 不提示的情况：读配置时恢复模块状态（`setEnabled`，只发一条 `Loaded xxx`）、`isPersistent()` 为 false 的模块（ClickGUI）、模块内部自己改状态。以后别的功能要弹通知，发 `NotifyEvent` 就行，不用依赖 HUD。
- 去重：列表里已有同一来源的消息时不新增，而是换成新文字、重置计时，并让色条闪一下（往白色混 40%，按 `e^(-10·dt)` 回落）。模块开关按模块去重，所以连按同一个模块的热键看到的是一条在 enabled / disabled 之间变化的消息；其他消息按"类型 + 文字"去重。
- 堆叠：间距 6，最新的一条离锚定的角最近，锚在右边时右对齐，左边时左对齐。整体包围盒是所有条的并集，用于拖动、右键和吸附；`place` 直接交给 `Anchor.drop`，每条的位置都从角算出来，拖动没有延迟。
- 动画：出现时从锚定的那一侧滑入（起点偏移自身宽度的 60%）并淡入，按 `1 - e^(-14·dt)` 趋近；每条的纵向位置和宽度按 `1 - e^(-12·dt)` 平滑，新消息插进来时其他条滑开。到时间后往同一侧滑出淡出，透明度接近 0 才移除，下面的条再滑上来。模块关闭时整体 0.3 秒淡出，期间不再收新消息。
- 和热键列表一样分三遍画（模糊、阴影、底色和内容），模糊和阴影的透明度跟着每条的透明度走。
- 编辑模式下列表为空时画一条示例 `Notifications`（主题色 `toggle_on`），可以拖动、右键打开设置；示例不计时，退出编辑或来了真消息就离开。
- 线程：事件可能不在渲染线程发出，渲染器把消息放进 `ConcurrentLinkedQueue`，每帧开头取出处理，列表只在渲染线程改。计时用 ImGui 每帧的 `deltaTime`（上限 0.1 s），和其他动画一致。
- 接入：`MoonlightClient` 创建渲染器、注册到 `Moonlight.EVENT_BUS`（它常驻订阅，自己判断模块开没开），并加进 `Hud` 的列表。

### 5.16 ESP 和 Entities 页面

Render → Entities 的 `Esp` 模块，画法、参数和默认值照参考项目的 2D ESP 和名字标签。

**设置**

- 卡片上只有 Entries（Players / Mobs / Animals / Items / TNT / Crystals / Fireballs，默认 Players）和 Filter（Teams：不画同队；Invisibles：画隐身的，默认关）。
- 各元素的设置是隐藏的设置项（`() -> false`，照常存配置），只在预览区点元素时显示：Elements（Name Tag / Health Bar / Armor Bar / Box，默认开名字、血条、框）；Box 的 Mode（Normal / Corners）、Color、Second Color、Glow；Health Bar 的 Mode（Health / Standard / Gradient）、Color、Second Color、Glow；Armor Bar 的 Color、Second Color、Glow；Name Tag 的 Style（Modern / Classic / Vanilla）、Color、Background、Health、Overlays。
- 颜色默认 A = (71, 148, 253)、B = (71, 253, 160)，名字底色 (0, 0, 0, 110)。

**游戏里（`EspRenderer`）**

- ImGui 帧里在 HUD 之前画到背景层（HUD 隐藏时不画）。遍历 `level.entitiesForRendering()`，按 Entries / Filter 筛选（死亡的不画；第一人称时不画自己）。
- 投影：实体插值位置减相机位置，包围盒半宽 `(bbWidth + 0.14) / 2`，高 `bbHeight + (潜行 ? -0.1 : 0.2) + 0.01`，8 个角乘主相机的 `getViewRotationProjectionMatrix`，只用 w > 0 的角，屏幕坐标取最小 / 最大值。单位换算：参考项目的坐标是 GUI 单位，这里乘游戏的 `Window.getGuiScale()` 换成像素。
- 分三遍画（模糊、阴影、填充），Glow 和名字标签的 Bloom 用 `Effects.shadow`，模糊用 `Effects.blur`。

**画法（`Painter`）**：坐标逐行照参考项目，`u` 是一个 GUI 单位的像素数。

- 主题色 `accent(y) = mix(B, A, sin(毫秒 / 600 + y × 0.06) × 0.5 + 0.5)`，框用 accent(0) → accent(500) 的水平渐变。
- Box：Normal 是四条黑边 + 四条 0.5u 宽的渐变线；Corners 每段 w/4、h/4。
- Health Bar：默认在左侧，0.5u 宽，底色黑 180；Health 模式按 HSB(血量 / 最大 / 3) 取色，Standard 用 accent(0)，Gradient 从填充起点的 B 渐变到末端的 mix(B, A, 血量比)，最少露出 2.5u。
- Armor Bar：默认在框下方，从起点 A 渐变到 mix(B, A, 护甲 / 20)，护甲为 0 不画。
- 两条都可以放在框的任意一边（隐藏设置 Health Side / Armor Side：Left / Right / Top / Bottom）。`Painter.track` 按边算出底条矩形，间距照参考项目的左边 / 下边对称换到另外两边：竖直的从下往上填，水平的从左往右填（底条内缩 0.5u 就是填充区）。两条在同一边时护甲条再往外 2u。条在上边时名字标签整体上移让出位置（`Painter.lift`，按条的实际矩形算，所以动画中也是连续的）。
- Name Tag：Modern 是圆角底 + A 色名字 + 白色血量数字（可加 Bloom / 模糊）；Classic 是直角底 + `名字 [♥血量]`（心形用图标字体，正文字体没有 ❤）；Vanilla 就是游戏自己的名字标签，ESP 不再画（`Esp.vanillaNames()` 时 `Painter` 跳过名字），哪些实体有名字标签也照原版。正文字体用 ClickGUI 的 `Fonts.regular()`，字号是参考项目字号的一半再乘 0.85。底的位置和大小照参考项目，但文字不照搬参考项目的固定偏移（那是按它自己的字体调的，换成我们的字体后没有血量行时文字会掉到底边），而是按行高在底里上下、左右居中（ImGui 的行高等于字号，大写字母和数字正好在行的中间）；有血量行时名字和血量两行作为一组居中。

**Entities 页面（`EspPane`）**

- 左边只排一列卡片（259 宽），ESP 卡片固定展开（不进 TOGGLES）；右边 x 440～735 是预览区，不跟着左边滚动。
- 工具栏：实体类型按钮（图标 + 复数名，点开下拉选 Player / Mob / Item / TNT / Crystal / Fireball）、隐藏模型、ESP Items 面板、全屏（预览区左边界按 `1 - e^(-14·dt)` 移到 167，左边卡片同时左移淡出并禁用）。
- 模型和框：不用实体的碰撞箱，而用 `Kind` 里记的"模型实际画出来的范围"（相对实体原点，单位格：半宽、前后、上下），是按原版渲染器代码算的，包含动画的活动范围：
  - 玩家：手臂 + 袖子 ±0.49，帽子层顶到 1.91，前后 ±0.27；僵尸：手臂 ±0.5，顶 2，双手前伸到前方 0.65。
  - 掉落物：贴图 0.5 格（地面显示缩放 0.5），底边在 0.0625 + 上下浮动（0～0.2），所以是 0.0625～0.7625。
  - TNT：±0.5、0～1。火球：物品贴图放大 3 倍、中心在 0.375，可见像素是 12 / 16，所以 ±0.5625、-0.1875～0.9375。
  - 末影水晶：底座在原点下方 -1～-0.5（原版就是这样画的），玻璃中心在 0.6～1.4 之间浮动、外层玻璃转起来最远 0.87，所以 ±0.87、-1～2.27。
- 框 = 这个范围左右前后各扩 0.07、上下各扩 0.105（总量和游戏里的扩边一样，但上下平分，模型在框里居中）。缩放取 `min(150, 170 / 框宽, 300 / 框高)`（设计单位每格），150 的上限是为了不让掉落物这种小东西被放得很大。框用 `Painter` 画（一个 GUI 单位 = 2 个设计单位），假数据：玩家 16 / 20 血、12 护甲，僵尸 20 / 20、2 护甲，其余不是活体。
- 3D 视角（`View`）：所有实体类型都可以在模型区按住拖动旋转，左右拖转 yaw，上下拖转 pitch（限制 ±90°），每设计单位 0.012 弧度，显示值按 `1 - e^(-18·dt)` 跟随；切换实体类型时归零。旋转中心是可见范围的中心。框和游戏里一样是 3D 盒子 8 个角投影后的范围，所以转起来框会跟着变宽变窄。`View` 的公式和 `Stage` 的 PoseStack 变换一一对应（先 yaw 后 pitch，再整体翻转到屏幕坐标），演示窗口也用它，三处一致。
- 缩放：鼠标在模型区时滚轮缩放，每格 ×1.15，范围 0.5～3 倍，同样按 `1 - e^(-18·dt)` 跟随，切换实体类型时归零。模型、框和名字标签都画在模型区的裁剪矩形里，放大后不会盖到工具栏。全屏时模型区盖在左边卡片列上面，所以全屏时不更新左边的 `Scroller`，免得滚轮同时滚动被隐藏的卡片。
- 移动超过 3 像素算拖动，松开时不弹元素设置；没拖动的松开才算点击（所以元素设置改成松开时弹出）。拖动中光标是四向箭头。
- Vanilla 名字标签：预览里也用原版的标签（见下面 `Stage`），点击范围由 `VanillaTag` 按原版位置算：锚点在碰撞箱顶 + 0.5 格（`Kind.tag`），文字向下 9 像素 × 0.025 = 0.225 格，四周 1 像素底。
- 点元素（名字、血条、护甲条、框，按这个优先级）弹出设置：上面是 Color 的调色板，下面是其余设置行（复用 `Row`，`renderCompact` 30 高；血条、护甲条多一行 Side）。
- 悬停 / 拖动 / 设置弹窗打开时的选中效果，没有动画、不铺彩色底，画在元素之后：
  - 框：没有选中效果（只有手形光标）。框只能点线条：点击范围是框线内外各 2u 的一圈，框里面（模型身上）的点击和拖动都交给旋转模型。
  - 血条、护甲条：外面一个 1 像素细框（`textHighlight`，alpha 170），紧贴底条外扩 1.5。
  - 名字标签：在标签背景上盖一层淡蓝色（`iconHighlight`，alpha 45），范围是 `Painter.tag` 的背景（Modern 带同样的圆角），原版标签是 `VanillaTag` 的范围。不画描边圈。
  - 点击范围另算，比选中效果大一圈。
- 拖动血条 / 护甲条：在条上按下并移动超过 3 像素就拿起，按鼠标相对框中心的方向（横向偏离大取左右，否则取上下）实时选最近的边并写回设置，不旋转模型；没移动的松开仍是点击。换边由 `Slide` 过渡（0.3 秒，smoothstep），粗细始终不变、只改长度：前半段旧条沿自身方向缩短到 0（相邻边时缩向新边那一端，对边时缩向中点），后半段新条从靠近旧边的那一端（对边时从中点）伸长到全长；同一边上的变化（护甲条因为叠放往外挪）直接平移。动画中途又换边时，从当前画出来的那一截接着缩。静止时直接用实时位置，所以旋转、缩放不会拖尾。切换实体类型时重置。
- 全屏时左边的卡片列子窗口还在（只是滑走、淡出），它盖在工具栏左半边和模型区上面，会把鼠标抢走（全屏下点不开实体类型下拉）。所以 `wide > 0` 时给它加 `NoInputs`。
- ESP Items 面板：放在单独的子窗口里，开关都用和弹窗一样的 `Popups.transform` 动画（淡入 + 从 0.96 放大 + 轻微平移，`1 - e^(-20·dt)`），关闭动画期间不响应点击。Main 一组芯片（12 号字），点击切换 Elements；选中样式和其他组件一致：左边白色对勾 + 高亮文字，未选中是普通文字，悬停是浅色底，描边始终是分隔线色，不用强调色。面板打开时不响应模型区的点击。

**预览里的模型（`Models`）**

- `Panel.models(...)` 设置画模型的实现；离线测试不设置，就只画 ESP 元素。
- 游戏里是 `Stage`：照原版 `PictureInPictureRenderer` 的流程离屏渲染，建 RGBA + 深度纹理，正交投影，`Lighting.Entry.ENTITY_IN_UI`，`EntityRenderDispatcher.submit` + `FeatureRenderDispatcher.prepareFrame` / `renderAllFeatures`；前后备份 / 恢复投影矩阵。纹理包成 `AbstractTexture` 注册为 `moonlight:esp/preview`，ImGui 用 `Textures.of` 画。原版画出来的纹理是上下颠倒的（原版 blit 时 V 从 1 到 0），所以 ImGui 画的时候 UV 也要用 (0, 1) → (1, 0)，否则模型倒立。
- 预览实体：本地玩家；僵尸、钻石剑掉落物、点燃的 TNT、带底座的末影水晶、火球按世界缓存，换世界重建。没有世界时不画模型。几个要注意的地方：
  - 生成时要用 `EntitySpawnRequest(LOAD, true)` 跳过检查，否则和平难度下 `EntityType.create` 对怪物返回 null，僵尸画不出来。
  - 这些实体不加进世界，ID 是 0，而 `ItemModelResolver` 要用实体 ID 当随机种子（掉落物、火球、手持物品都会走到），`getId()` 遇到 0 直接抛异常，所以生成后要 `setId` 成负数（-1 - 种类序号）。
  - 实体不 tick，动画不会走：`ageInTicks` 每帧改成本地玩家的 `tickCount` + partialTick（末影水晶的旋转和上下浮动、掉落物的旋转都靠它）。
  - `createRenderState` 要传当前帧的 partialTick（`getGameTimeDeltaPartialTick(false)`），不能像原版物品栏那样传 1：传 1 时所有插值（走路摆臂、挥手、`ageInTicks`）都停在 tick 值上，只有 20 FPS，疾跑时打开 ClickGUI 看玩家模型会明显卡顿。ClickGUI 不暂停游戏，partialTick 是有效的。
  - Vanilla 名字标签：状态里手动设 `nameTag = getDisplayName()`、`nameTagAttachment = NAME_TAG 挂点`（原版对自己和没名字的实体不显示，预览里要强制显示），不是 Vanilla 时清空。`CameraRenderState.orientation` 设成视角旋转的逆（`rotationY(-yaw).rotateX(-pitch)`），名字标签就和游戏里一样始终朝向观察者；火球例外保持单位旋转，因为 `ThrownItemRenderer` 也用这个朝向做广告牌，设了逆旋转火球就永远正对屏幕、转不出 3D。
  - TNT 默认引信 80 刚好落在原版"白闪"的区间（`(int)(引信 / 5)` 是偶数时变白），预览固定成 15（不闪也不膨胀）。
- `Stage` 的变换：平移到预览区中心 → `scale(s, s, -s)` → 绕 Z 转 180° 再依次 pitch、yaw → 平移 `-(0, 可见范围中心 y, 可见范围中心 z)`，模型的可见范围中心正好落在预览区中心。
- 预览窗口里是 `PreviewModels`：没有游戏的渲染管线，用原版贴图自己做一个小的 3D 管线：用 JOML `Matrix4f` 按原版 PoseStack 的顺序组合变换，顶点经 `View` 投影，方块面按原版 `ModelPart.Cube` 的顶点顺序和 UV 展开生成（外层按 `CubeDeformation` 外扩，镜像部件交换 minX / maxX 并反转顶点顺序），背面剔除后所有面按深度从远到近画（画家算法）。
  - 玩家和僵尸：实体变换照 `LivingEntityRenderer`（玩家缩放 15/16，XY 翻转、平移 1.501），部件按枢轴平移 + ZYX 旋转。僵尸双手前伸 80°、左右各偏 0.1 弧度，和原版不发怒时一样。
  - TNT：16 像素方块，顶 `tnt_top`、底 `tnt_bottom`、四面 `tnt_side`。
  - 末影水晶：照 `EndCrystalRenderer` + `EndCrystalModel`：`scale(2)`、下移 0.5，底座 12×4×12，外层玻璃（中心按原版公式浮动、绕斜轴 60° 并绕 Y 转）、内层玻璃（再转一次、缩放 0.875）、核心（再转一次、缩放 0.765625）。
  - 掉落物和火球：原版是 1 像素厚的拉伸物品模型，这里沿厚度方向叠多层贴图（层数 = 厚度像素数 + 1，最多 16），转起来有厚度；不做背面剔除。掉落物按原版公式浮动并绕 Y 转，火球中心 0.375、贴图 1.5 格、厚 3/32 格。
  - Vanilla 名字标签：没有原版渲染器，按 `VanillaTag` 的位置画半透明黑底（alpha 64）+ 白字，近似原版。


### 8.1 IDEA 的 Gradle 面板

```
Moonlight                  ← 根项目（控制器）
├─ Tasks → application     → 26.3-fabric-client、26.3-neoforge-client、buildAllJars、ui-preview
├─ Tasks → stonecutter     ← 切换激活节点
├─ 26.3-fabric
│   └─ Tasks → build       → build、buildAndCollect
│            → application → previewUi
│            → fabric      → runClient、runServer
└─ 26.3-neoforge
    └─ Tasks → build       → build、buildAndCollect
```

日常使用只看根项目的 `application` 分组：

| 任务 | 作用 |
|---|---|
| `26.3-fabric-client` | 启动 Fabric 客户端（执行 `:26.3-fabric:runClient`） |
| `26.3-neoforge-client` | 启动 NeoForge 客户端（执行 `:26.3-neoforge:runClient`） |
| `buildAllJars` | 同时构建 Fabric 和 NeoForge，jar 汇总到根目录的 `build/libs/<版本>/` |
| `ui-preview` | 不启动游戏，单独开一个窗口预览 ClickGUI 和 HUD（执行 `:26.3-fabric:previewUi`，见 8.6） |

- 这几个任务都定义在 `stonecutter.gradle`。`<节点>-client` 按子项目自动生成，`buildAllJars` 依赖所有节点的 `buildAndCollect`，以后新增 Minecraft 版本节点时都会自动包含，不用改。
- 右键这些任务选"调试"，IDEA 会把调试器挂到游戏进程上。
- 只构建 Fabric 版：双击 `26.3-fabric → Tasks → build → build`
- 只构建 NeoForge 版：双击 `26.3-neoforge → Tasks → build → build`

**根项目 Tasks 里为什么有 `fabric`、`mod development` 这些分组**

IDEA 在根项目下列出的是"可以从根项目运行的任务"，也就是根项目自己的任务加上所有子项目的任务（同名的合并成一项）。所以 Loom 的 `fabric` 分组、ModDevGradle 的 `mod development` 分组、两边的 `build` / `jar` 等都会出现在根项目下。这是 Gradle 多项目构建的正常行为，不是配置错误。各分组的来源：

| 分组 | 来源 |
|---|---|
| `fabric` | Loom（只有 26.3-fabric 有） |
| `mod development`、`mod development/internal` | ModDevGradle（只有 26.3-neoforge 有） |
| `stonecutter`、`stonecutter-impl` | Stonecutter（切换节点和它的内部任务） |
| `ide` | `idea` 插件（由 idea-ext 带进来，用于生成运行配置） |
| `build`、`documentation`、`verification`、`help`、`build setup`、`other` | Gradle 自带 |

在根项目下双击一个任务，Gradle 会在**每个有这个任务的子项目里都执行一次**。例如根项目的 `build` 会同时构建两个加载器。`runClient` / `runServer` 也一样，会把 Fabric 和 NeoForge 的游戏依次全部启动，所以 `stonecutter.gradle` 里拦掉了这两个：在根项目直接运行它们（比如双击根项目 `fabric` 分组下的 `runClient`）会报错，并提示改用 `application` 分组里的任务。

建议：根项目下只用 `application` 和 `stonecutter` 两个分组；其他单个加载器的任务去 `26.3-fabric` / `26.3-neoforge` 节点下找。

### 8.2 IDEA 的运行 / 调试配置

每次 Gradle 同步完成后，IDEA 会自动在右上角的运行 / 调试配置里创建这六个，全部是 Gradle 类型：

| 配置 | 执行的 Gradle 任务 | 作用 |
|---|---|---|
| `26.3-fabric-client` | `:26.3-fabric:runClient` | 启动 Fabric 客户端 |
| `26.3-fabric-server` | `:26.3-fabric:runServer` | 启动 Fabric 服务端 |
| `26.3-neoforge-client` | `:26.3-neoforge:runClient` | 启动 NeoForge 客户端 |
| `26.3-neoforge-server` | `:26.3-neoforge:runServer` | 启动 NeoForge 服务端 |
| `buildAllJars` | `buildAllJars` | 同时构建两个加载器的 jar |
| `ui-preview` | `:26.3-fabric:previewUi` | 不启动游戏预览 ClickGUI 和 HUD |

实现方式：

- 根项目应用了 JetBrains 的 `idea-ext` 插件（1.2，和 ModDevGradle 内部用的是同一个版本）。这些配置都在 `stonecutter.gradle` 里注册到 `idea.project.settings.runConfigurations`，IDEA 同步时读取并创建，不需要在仓库里放 `.run` / `.idea` 的 xml 文件。
- `<节点>-client` / `<节点>-server` 按子项目自动生成，以后新增版本节点会自动多出对应的配置。
- 配置直接执行 Loom / ModDevGradle 的 `runClient` / `runServer` 任务，所以构建脚本里给运行加的参数（DevAuth、`-Dmixin.debug.export` 等）、启动前的准备任务（生成 `launch.cfg`、下载资源、NeoForge 的 `prepareClientRun`）都由 Gradle 处理，和命令行运行完全一致。点"调试"时 IDEA 会把调试器挂到 Gradle 启动的游戏进程上。
- 加载器自己生成的 IDE 运行配置都已关闭：Loom 是 `ideConfigGenerated(false)`，ModDevGradle 是两个 `runs` 里的 `disableIdeRun()`。否则同步后会多出它们默认的"应用程序"类型配置。
- 同步不会删除旧的配置。以前留下的 `26.3-fabric`、`26.3-neoforge`、`26.3-neoforge - Client`、`26.3-neoforge - Server`、`Build All Jars`，以及上一版生成的"应用程序"类型的 `26.3-*-client` / `26.3-*-server`，需要在"编辑配置"里手动删一次，再重新同步。`Moonlight-v2-NextGen [xxx]` 这种是双击 Gradle 任务时 IDEA 自动生成的临时配置，可以不管。

### 8.3 命令行

| 目的 | 命令 |
|---|---|
| 只构建 Fabric | `./gradlew :26.3-fabric:build` |
| 只构建 NeoForge | `./gradlew :26.3-neoforge:build` |
| 两个都构建，并把 jar 汇总到 `build/libs/<版本>/` | `./gradlew buildAllJars` |
| 启动 Fabric / NeoForge 客户端（等同下面两行） | `./gradlew 26.3-fabric-client` / `./gradlew 26.3-neoforge-client` |
| 两个都构建（不汇总） | `./gradlew build` |
| 启动 Fabric 客户端 | `./gradlew :26.3-fabric:runClient` |
| 启动 NeoForge 客户端 | `./gradlew :26.3-neoforge:runClient` |
| 不启动游戏预览 ClickGUI 和 HUD | `./gradlew ui-preview`（等同 `./gradlew :26.3-fabric:previewUi`） |

产物位置：

- `versions/26.3-fabric/build/libs/moonlight-fabric-<版本>+26.3.jar`
- `versions/26.3-neoforge/build/libs/moonlight-neoforge-<版本>+26.3.jar`
- 运行 `buildAllJars` 后，两边的 jar 和源码 jar 都会复制到 `build/libs/<版本>/`：
  ```
  build/libs/0.1.0/
  ├─ moonlight-fabric-0.1.0+26.3.jar
  ├─ moonlight-fabric-0.1.0+26.3-sources.jar
  ├─ moonlight-neoforge-0.1.0+26.3.jar
  └─ moonlight-neoforge-0.1.0+26.3-sources.jar
  ```

两个加载器共用 `run/` 作为游戏目录（存档、配置、日志都在里面）。

### 8.4 开发环境登录正版账号（DevAuth）

两个加载器都以 `runtimeOnly` 引入了 [DevAuth](https://github.com/DJtheRedstoner/DevAuth)（版本在 `stonecutter.properties.toml` 的 `deps.devauth`）。它只在开发环境运行时生效，**不会打进发布的 jar**。

- 只有**客户端**运行带了 `-Ddevauth.enabled=true`：Fabric 在 `build.fabric.gradle` 的 `runConfigs`（client）里，NeoForge 在 `build.neoforge.gradle` 的 `runs.client` 里。IDEA 的 `26.3-fabric-client`、`26.3-neoforge-client` 配置都会带上，服务端配置不带。
- 第一次启动客户端时，控制台会打出一个微软登录链接，在浏览器里登录授权后就会用这个账号进游戏。令牌保存在 `C:\Users\<用户名>\.devauth\microsoft_accounts.json`，之后会自动续期。**这个文件不要分享给别人。**
- 多个账号：在 `~/.devauth/config.toml` 里加账号，用 `-Ddevauth.account=<名字>` 选择要登录哪个。
- 想临时用离线账号：把对应的启用参数改成 `false`，或者删掉那一行，然后重新同步 Gradle。
- DevAuth 的兼容表只写到 1.21.11。它的原理是在 `net.minecraft.client.main.Main.main` 开头改写启动参数，26.3 里这个方法没变，所以理论上能用。如果升级 Minecraft 后登录失效，先检查这一点。

### 8.5 gradle.properties 说明

- `org.gradle.parallel=false`：两个节点不并行构建。NeoForge 的 Minecraft 重编译很吃内存，并行时容易把电脑跑卡。
- `org.gradle.configuration-cache=true`：开启配置缓存。修改构建脚本时注意第 10 节的写法要求。
- `org.gradle.configuration-cache.problems=warn`：配置缓存遇到不兼容的写法时只警告、不中断构建。IDEA 设成"使用 Gradle 构建和运行"时，如果运行"应用程序"类型的配置（比如自己在 IDEA 里新建的），IDEA 会临时生成一个初始化脚本交给 Gradle 执行。这个脚本注册了构建监听器，还在执行阶段访问 `Task.project`，两者都不兼容配置缓存；不加这一行就会报 `Invocation of 'Task.project' ... is unsupported with the configuration cache`。平时构建仍然正常使用配置缓存，只是自己写的脚本出了兼容问题也只会显示成警告，要留意构建输出里的 `problems were found`。
- `dev.kikugie.stonecutter.hard_mode=true`：关闭 Stonecutter 每次构建都打印的"推荐使用 Kotlin DSL"提示。项目有意使用 Groovy。

### 8.6 不启动游戏预览 UI（ui-preview）

调 ClickGUI 和 HUD 的样式时不用每次都进游戏：运行根项目 `application` 分组的 `ui-preview`（或者 IDEA 里同名的运行配置、命令行 `./gradlew ui-preview`），会直接打开一个 "Moonlight UI Preview" 窗口，里面跑的是和游戏里同一套 `Panel`、`Hud`、`WatermarkRenderer`、主题和设置项。

| 文件 | 作用 |
|---|---|
| `src/preview/java/wtf/moonlight/ui/imgui/Preview.java` | 入口：SDL3 窗口 + OpenGL 3.3 上下文，`ImGuiImplSdl3` 处理输入，每帧调用 `hud.frame(true, true)` 和 `panel.frame(open, true)` |
| `src/preview/java/wtf/moonlight/ui/imgui/PreviewRenderer.java` | 直接用 OpenGL 画 ImGui 的绘制数据，按 1.92 的动态纹理流程创建 / 更新 / 销毁字体纹理；遍历命令的方式和游戏里的 `ImGuiRenderer` 一样（先模糊后阴影、连续的阴影命令合并成一次） |
| `src/preview/java/wtf/moonlight/ui/imgui/PreviewPost.java` | 后处理：把 `post/Blur`、`post/Bloom`、`post/Kawase` 的流程照搬成 OpenGL 调用 |
| `src/preview/java/wtf/moonlight/ui/imgui/Program.java`、`Target.java` | 着色器程序和离屏帧缓冲的小封装 |

- 源码集 `preview` 只在 `build.fabric.gradle` 里定义，编译时依赖 `main` 的输出和类路径，**不会打进模组 jar**。Stonecutter 会自动把它映射到根目录的 `src/preview/java`，和 `main` 一样。
- 这些类放在 `wtf.moonlight.ui.imgui` 包里，是为了直接用包内的 `Natives`、`Textures`、`Effects` 和 `ImGuiManager.setup(scale)`，不用反射。
- 操作：右 Shift 开关 ClickGUI；HUD 一直处于编辑状态，左键拖动、右键打开它的设置弹窗；关窗口就退出。
- 窗口大小是主显示器可用区域的 80%，UI 缩放取窗口所在显示器的缩放（和游戏里 `ImGuiManager` 的取法一样）。
- 背景是游戏的 `panorama_0.png`。jar 里的这张只是 1×1 占位图，真正的图片在 Loom 下载的资源目录里，`previewUi` 通过 `-Dmoonlight.assets` 把 `<Gradle 用户目录>/caches/fabric-loom/assets` 传进去，`Preview` 按当前版本号找资源索引再取文件。找不到就用纯色背景。
- 模糊和阴影：整帧先画到一张离屏纹理（`Target`），这样模糊才能采样到已经画好的背景和 UI，最后再整张拷到窗口。降采样、升采样、阴影的裁切和着色直接读游戏的 `blur_down.fsh`、`blur_up.fsh`、`bloom_cut.fsh`、`shadow.fsh`，参数（Blur Amount / Offset、Bloom Amount / Offset、Shadow Opacity）也读 PostProcess 模块，所以效果和游戏里一致。全屏三角形的顶点着色器是 `PreviewPost.SCREEN`，对应游戏的 `screenquad.vsh`（游戏那份用的是 `gl_VertexIndex`，原生 OpenGL 要换成 `gl_VertexID`）。
- 假数据：玩家名 `Steve`、默认 Steve 皮肤、服务器 `localhost`，延迟和速度会缓慢变化，方便看数字滚动动画；版本号是真实的 `SharedConstants` 版本。
- 预览启动时会调用 `Bootstrap.bootStrap()`（`Scaffold` 的方块列表需要注册表），并默认开启 PostProcess、Watermark、Hotkeys 和 Notifications。Entities 页面的预览用 `PreviewModels` 画模型（见 5.16）。通知渲染器注册在 `Moonlight.EVENT_BUS` 上，所以在预览里按热键开关模块、录热键、在配置下拉框里保存都会弹通知（`ConfigManager` 自己那条总线只管读写，通知仍发到全局总线）。
- 热键：26.3 的键码就是 SDL scancode，所以 `Preview` 把 SDL 的按键事件直接包成 `KeyInputEvent`（`KeyEvent(scancode, keycode, mod)`，动作用 `InputConstants.PRESS / REPEAT / RELEASE`）发到 `Moonlight.EVENT_BUS`，上面注册了 `BindManager` 和 `BindCapture`。正在往输入框打字时不转发。所有模块的触发条件换成"没在输入文字"（默认条件要查游戏界面，预览里没有）。这样在预览里也能录热键、按热键，热键列表 HUD 会跟着亮起；鼠标键绑定在预览里不转发。
- 保存设置：配置存在 `run/preview/configs/`，和游戏的配置分开。`Preview` 给 `ConfigManager` 单独建了一条 `EventBus`，启动时发一次 `GameLoadedEvent`（读 `latest.json`），之后每 50 ms 发一次 `TickEvent.PRE`，所以和游戏里一样，设置有变化时大约每秒自动写一次 `latest.json`，正常关窗口时也会再存一次。这条总线上只有 `ConfigManager`，模块的 Tick 监听不会被触发。
- 开关需要游戏实例的模块（比如 FullBright、Scaffold）会在 `onEnable` 里抛空指针。预览会捕获这一帧的异常、恢复 ImGui 样式并打印警告，窗口继续运行。读配置时 `ConfigManager` 会单独捕获每个模块开关的异常，所以就算 `latest.json` 里这些模块是开着的，其他设置也照常读进来。
- `previewUi` 会先编译 `main`。游戏客户端开着的时候运行它，会改写客户端正在用的 class 文件，最好先关掉游戏再预览。

---

## 9. 改版本号、加依赖

### 9.1 模组信息

`stonecutter.properties.toml` 顶部的 `mod.id`、`mod.name`、`mod.version`。

如果要**改模组 id**，下面这些地方要一起改：

- `stonecutter.properties.toml` 的 `mod.id`
- `Moonlight.MOD_ID`（`@Mod` 注解也引用它）
- `moonlight.mixins.json` 的文件名，以及 `fabric.mod.json`、`neoforge.mods.toml` 里对它的引用
- `moonlight.ct` 的文件名，以及 `fabric.mod.json` 和 `build.fabric.gradle` 里对它的引用
- `assets/<id>/` 目录名

### 9.2 升级加载器和工具

| 要升级的 | 改哪里 |
|---|---|
| Fabric Loader / Fabric API / NeoForge / DevAuth / imgui-java | `stonecutter.properties.toml` |
| Loom | `build.fabric.gradle` 的 `plugins` |
| ModDevGradle | `build.neoforge.gradle` 的 `plugins` |
| Stonecutter | `settings.gradle` 的 `plugins` |
| Gradle | `gradle/wrapper/gradle-wrapper.properties` |

最新版本可以查 maven 元数据，例如 `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`。

### 9.3 加依赖

- **只给一个加载器用的模组 / 库**：只加到对应的 `build.fabric.gradle` 或 `build.neoforge.gradle` 的 `dependencies` 里。
- **通用代码要用的第三方库**：两个构建脚本都要加。打包进 jar 时，Fabric 用 `include`，NeoForge 用 `jarJar`。
- 来自 Modrinth / CurseForge 的模组，先在对应构建脚本里加仓库，例如：
  ```groovy
  repositories {
      maven { url = "https://api.modrinth.com/maven" }
  }
  ```

### 9.4 以后支持更多 Minecraft 版本

1. `settings.gradle` 里为新版本加一对 `version("26.4-fabric", "26.4")` / `version("26.4-neoforge", "26.4")`；
2. `stonecutter.properties.toml` 里加 `[fabric."26.4"]`、`[neoforge."26.4"]` 两张表；
3. 代码里的版本差异用 `//? if >=26.4 {` 区分。

### 9.5 ImGui（imgui-java）

两个加载器都引入了 [imgui-java](https://github.com/SpaiR/imgui-java)，版本在 `stonecutter.properties.toml` 的 `deps.imgui`，来自 Maven Central。

| 构件 | 用途 | 依赖方式 |
|---|---|---|
| `imgui-java-binding` | ImGui 的 Java 绑定（`imgui.ImGui` 等） | `implementation` |
| `imgui-java-lwjgl3` | 后端：`imgui.sdl3.ImGuiImplSdl3`（SDL3 平台）、`imgui.gl3.ImGuiImplGl3`（OpenGL3 渲染） | `implementation`，排除 `org.lwjgl` |
| `imgui-java-natives-windows` / `-linux` / `-macos` | 三个平台的原生库 | `runtimeOnly` |

- 五个构件都打进了发布的模组 jar：Fabric 用 `include`（放在 `META-INF/jars/`），NeoForge 用 `jarJar`（放在 `META-INF/jarjar/`）。三个平台的原生库约 10 MB，加上 ClickGUI 的字体，模组 jar 约 22 MB。
- ClickGUI 实际怎么接入的见 5.10：渲染用自己的 `ImGuiRenderer`（RenderPearl），输入走 `ClickGuiScreen`，只借用了 `ImGuiImplSdl3.keyEventToImGuiKey` 做按键映射，下面关于两个后端的说明是给以后直接用它们的场合准备的。
- **不要带 imgui 自己的 LWJGL**。`imgui-java-lwjgl3` 默认依赖 LWJGL 3.4.1，还要 `lwjgl-glfw`。26.3 自带的是 LWJGL 3.4.3，并且已经用 SDL3 取代了 GLFW，没有 `lwjgl-glfw`。所以 `org.lwjgl` 整组都排除掉，用游戏自带的。我已经核对过：`ImGuiImplSdl3` 和 `ImGuiImplGl3` 用到的 105 个 LWJGL 方法和字段，在 3.4.3 里都存在，签名也一致。
- **平台后端用 `ImGuiImplSdl3`，不要用 `ImGuiImplGlfw`**：26.3 没有 GLFW 窗口。
- **渲染后端只能在 OpenGL 下用**：26.3 的图形 API 是一个选项（`PreferredGraphicsApi`：`DEFAULT` / `OPENGL` / `VULKAN`），默认由游戏自己选，可能跑在 Vulkan 上。`ImGuiImplGl3` 只在 OpenGL 下有效，接入时要先判断当前用的是哪个后端。
- 原生库由 `imgui.ImGui` 在第一次使用时从类路径的 `io/imgui/java/native-bin/` 解压到系统临时目录的 `imgui-java-natives/<版本>/` 再加载。也可以用系统属性 `imgui.library.path` 指定原生库所在目录。
- NeoForge 下的非模组库直接用 `implementation` / `runtimeOnly` 即可。ModDevGradle 在新版本上不允许往 `additionalRuntimeClasspath` 里加依赖。

---

## 10. Groovy 构建脚本的写法要求

构建脚本是 Groovy（`.gradle`）。有几个坑要注意：

1. **读配置用 `property("mod.id")`**。Stonecutter 会把 `stonecutter.properties.toml` 的值注入成项目属性。模板 Kotlin 版里的 `sc.properties.get(...)` 是 Kotlin 的 reified 内联函数，在 Groovy 里调用会直接抛异常。
2. **先在脚本顶部把值取成变量**，再在任务块里使用。在 `processResources { }`、`tasks.register(...) { }` 这些块里直接写 `property("...")`，会解析成任务对象自己的属性，读不到项目属性。
3. **给 `Property<String>` 赋值时用普通 String**（`.toString()` 或字符串拼接），不要直接塞 GString。
4. 开了配置缓存，任务执行阶段的闭包里不要访问 `project`。

---

## 11. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 同步时 Loom 报需要 Java 25 | Gradle JVM 没选 25，见第 3 节 |
| NeoForge 的代码在 IDE 里全是注释、没有高亮 | 当前激活的是 fabric 节点，切到 neoforge 再改 |
| 切换节点后 git 里出现大量改动 | 正常；提交前执行 `Reset active project` 切回 fabric |
| 通用代码在 NeoForge 构建时报找不到 `net.fabricmc...` | 通用代码引用了加载器 API，挪到 `Platform` 或包进 `//? if fabric` |
| Mixin 只在 NeoForge 上注入失败 | NeoForge 改过那个方法，见第 6 节 |
| 依赖下载超时 | 配置代理，见第 3 节 |
| 第一次同步 / 构建特别慢 | NeoForge 在重编译 Minecraft，只有第一次这样 |

---

## 12. 参考

- Stonecutter 文档：<https://stonecutter.kikugie.dev/>
- 参考模板：<https://github.com/stonecutter-versioning/stonecutter-template-multiloader>
- Fabric 文档：<https://docs.fabricmc.net/>
- NeoForge 文档：<https://docs.neoforged.net/>
- Fabric 官方示例（26.3 分支）：<https://github.com/FabricMC/fabric-example-mod/tree/26.3>
- NeoForge 26.3 MDK：<https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle>
