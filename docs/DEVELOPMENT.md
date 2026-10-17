# 开发与复验

主代码包`oborokage`，Mod ID仍是`oborokage`。原创模型/图标/材质由tools中的参数建模脚本生成，声音使用原版事件；不含视频资源、其他作者模型或代码。格式工具是第三方[Google Java Format](https://github.com/google/google-java-format)1.29.0，只用于开发，不打入发布JAR。

JDK25下：

```sh
./gradlew formatJavaCheck build --offline
```

首次机器没有依赖缓存时移除`--offline`。Loom固定1.18.4，Minecraft26.3，Loader0.19.5，Fabric API0.162.0+26.3。使用`./gradlew formatJava`统一格式。

## 验证环境参数

脚本优先读取`JAVA_HOME`、`MINECRAFT_HOME`、`MINECRAFT_INSTANCE`。macOS本机路径仅为默认发现位置；其他平台应显式指定。运行classpath来自目标实例元数据和本机库。当前QA脚本面向26.3及该Loader/API版本，非跨版本通用启动器。

```sh
python3 verification/run.py server
python3 verification/run.py client --fullpack
python3 verification/run.py client --maintenance --fullpack
python3 verification/run.py client --shader --lowfx
python3 verification/network.py --latency
python3 verification/recipebook.py
python3 verification/gametest.py
python3 verification/rollback.py
```

所有生成世界/测试JAR/配置都在项目`build/verification-*`内。测试JAR与Mixin不打进发布模组。

- server：原版强度、碰撞、双刀窗口、成就、属性/附魔、保护、配置和节流的受控服务器回归。
- client：真实integrated server、本机完整模组组合、真实use/release/attack包、原版iframe、箭实体碰撞、原木掉落、权限、/reload和截图。
- maintenance：0.3.1的HUD实际绘制、光环、原生音效、单手姿势、原版左键挖掘路径、刀碎边界、拒绝heartbeat与真实成就广播。左键场景直接调用Minecraft原生startAttack/continueAttack入口，避免自动化窗口失焦丢键；不以直接调用拦截函数代替挖掘结果。
- shader：隔离目录启用ComplementaryReimagined_r5.9.3，复制光影文件，保留正式实例光影选项。
- network：独立服务端与两个客户端；`--latency`对每个方向注入100ms有序延迟。含空闲取消、精准格挡和反切预测；不是互联网WAN质量或长时间多人性能结论。
- gametest：Fabric GameTest原生运行终结和配方测试，JUnit结果位于对应reports目录。API模块从本机Gradle缓存发现。
- rollback：仅生成的箱子世界，验证0.3.1→0.1→0.3.1新增物品丢失；可用`OBOROKAGE_LEGACY_JAR`指定旧版JAR。

测试中为排除持续格挡击退对箭矢瞄准的影响，会先将测试玩家稳定在平台上，再发射箭矢。测试的“输入立即预测”只验证呈现与确认，命中仍由服务端处理。不得把脚本操作称为真人手感验收。

源码交付使用`python3 tools/package_release.py`；COPYFILE_DISABLE和显式排除规则去除AppleDouble、Python缓存和内部WORK_LOG。截图另打evidence.zip，解压到源码根目录恢复验证文档图片。

## 真人手测清单

在临时世界检查：低/高FPS连续三连的节奏、方向键与影步组合、主副手快速切槽、超高ping镜头晕眩、不同FOV/左利手、不同水面/天气/光影选项下的透明排序。最后再由玩家确认视觉喜好。大规模多人长时压力、任意第三方不死模组、全部光影包仍须针对性测试。

## 性能约束

Move为静态缓存；进度根查询每20tick；状态包只在装备/数值/动作变化时发送。刀鞘栈缓存按刀型最多五份，渲染状态缓存最多64演员并在换世界清空。刀光与技能网格各预留总预算的一半，按剩余预算降低细分，远距离与场景数均有限制。服务器命中体积、每窗口目标数、斩物总量/每tick数均受限。正常战斗的经验和数值不能从这些上限推导出任意人数的实际TPS保证。
