# 胧影 0.2.0：实现、验证与安装

交付日期：2026-10-16。环境：Minecraft 26.3、Fabric Loader 0.19.5、Fabric API 0.162.0+26.3、Java 25、Apple M4。
旧报告保存在 `backups/expansion-20261015/VERIFICATION-0.1.0.md`。

## 安装结果

正式安装文件：
`/Users/mac/Library/Application Support/minecraft/versions/26.3-Fabric/mods/oborokage-tachi-0.2.0.jar`

SHA-256：`1a44061633bd8febcc41a46b99c528a40e9698e181ca7c1529d076f758716960`

最终构建、隔离服务端、整合包客户端、本机联机服务器、两个独立客户端、配方书检查及正式安装文件的 SHA-256 全部相同。同一 Mod ID `oborokage` 只有一份启用 JAR。详见 [安装记录](verification/expansion-evidence/installation.json)。

安装前重新获取当前实例的保护清单，包含用户后来添加的模组。安装前后 **35 个其他模组 JAR、配置、两份用户世界中的所有现存文件、实例元数据、选项和光影文件，共 279 个受保护文件**哈希相同。仅替换太刀 JAR；未打开用户存档，未修改水桶弓。

旧安装 JAR 备份：`backups/release-20261016/oborokage-tachi-0.1.0.jar`。
旧 SHA-256：`89cedb5b992e0ec520e7cb72edbbb487fba0ae177b3bac083c8b5c1abf5b4ed4`。
当前配置备份和安装前保护清单同在该目录；最初源码备份为 `backups/expansion-20261015/source-0.1.0.tar.gz`。

## 已实现范围

- 保留原太刀和全部原居合参数；原执行器独立为 `LegacyIaido`。
- 新增一把脇差，主手太刀 + 副手脇差构成真实双刀，没有新增其他装备、方块或生物。
- 主副手交替三连、精准架刀、来源定向反切、持续防御、影步及副手派生、剑意、破势窗口。
- 双刀奥义「双月·归寂」：独立左右手动作、银紫/青白刀光、腰间双刀鞘、终斩、收势和施术者第三人称镜头。
- 服务端动作 ID、输入序列、限流、命中窗口去重、墙体遮挡、槽位变化取消、状态清理和旁观同步。
- 两张主世界工作台配方、配方书解锁及原版附魔支持标签。
- 独立创造分类「胧影」、创造搜索、独立「胧影剑录」的 10 项进度；旧进度 ID 和旧完成记录保留。
- 音效使用 Minecraft 原版声音；新增模型、贴图、刀光、背景和动作由项目自写，没有引入外部模型或动画库。

## 原刀没有削弱

备份中的原 `Combat.java` 与当前 `LegacyIaido.java` 除类名外内容完全一致；原 TACHI 注册属性保留。

| 参数 | 保留值 |
|---|---|
| 原物品 ID | `oborokage:tachi` |
| 最低有效释放 / 满蓄力 | 4 / 40 tick |
| 名义伤害 | `15 + 985 × q²`，满蓄力 1000 加原终结处理 |
| 满蓄力前摇 / 突进 | 12 / 6 tick，无障碍约 12 格 |
| 部分 / 满蓄力冷却 | 18 / 45 tick |
| 部分 / 满蓄力耐久消耗 | 2 / 8 |
| 总耐久、近战属性、稀有度、耐火 | 与原版一致 |

新剑意、架势、成就和双刀奥义冷却不限制旧右键居合；右键居合可打断新增动作。
高血量、防护组合及原版 Boss 已检查；第三方强制不死或改写死亡事件的生物不保证完全兼容。

## 最终包验证：117 项通过，0 项失败

下表为最终 JAR 的最新运行结果。规则检查使用测试用实体/玩家及受控事件；实机与联机另列，没有混称为玩家手动体验。

| 检查 | 通过 | 证据 |
|---|---:|---|
| 隔离专用服务器规则、注册与事件检查 | 76 | [服务端结果](verification/expansion-evidence/server-results.txt) |
| 复制当前整合包、临时世界实机客户端 | 24 | [实机结果](verification/expansion-evidence/client-results.txt) |
| 独立本机专用服务器 + 两个独立 JVM 客户端 | 11 | [服务器](verification/expansion-evidence/network-server-results.txt)、[施术者](verification/expansion-evidence/network-actor-results.txt)、[旁观者](verification/expansion-evidence/network-observer-results.txt) |
| 当前整合包、原生库存事件与配方书同步 | 6 | [配方书结果](verification/expansion-evidence/recipebook-results.txt) |

### 规则与旧能力

验证了注册仅两把武器、独立分类/搜索、10 项进度资源、两张配方组装、6 项武器附魔支持查询、原刀蓄力/耐久/冷却、1024 血量 + 护甲 + 吸收 + 抗性 + 永久无敌 + 图腾组合、凋灵/末影龙/监守者、使用者与 PvP 关闭保护、一次死亡回调、旧居合单目标去重和墙体阻挡。

新增动作验证了四个独立命中窗、两手分别扣耐久、连招与反切/影步/破势/奥义等成就事件、序列重放无效、切副手取消后续命中、新动作墙体遮挡。新增边界检查确认：超过当前 60 秒窗口的手别命中不能通过早期击杀记录继续计入；新的左右手命中仍能完成挑战。

### 实机客户端

真实 use/release 包触发原居合并击败 1024 血量目标，玩家骨架动作、电影镜头和恢复通过。将旧版**测试世界**中已完成的 `moon_still_hangs` 进度复制到新测试世界，确认迁移父节点后仍完成，随后仅在测试世界撤销并再次达成。

真实左键入口及 C2S 请求产生四次双刀命中；H/V/J 键位可触发架刀、影步和奥义；剑意同步 HUD、终斩击杀和镜头恢复通过。正常难度下 6 点普通近战持续架刀减至 2.1 点。

实际工作台界面取出太刀、Shift 合成脇差后，结果正确、材料消耗、输出完整耐久。单独配方书检查中，新玩家先没有配方，向真实库存加入紫水晶碎片并广播原生库存变化后，两张解锁进度完成，服务端配方书与客户端显示均收到两张配方，没有手动奖励解锁。

创造页用原生 Fabric 分页 API 选到**实际可见**的胧影分类，确认两把武器显示。此整合包中胧影在创造栏第二页：使用创造面板内部右上角的小箭头翻页，右侧 JEI 箭头属于另一套界面。

### 两客户端联机

两个独立客户端实际连接同一个只绑定 `127.0.0.1` 的专用服务器。服务端确认命中/剑意与 1024 血量目标终结；施术者播放并恢复自己的镜头；旁观者收到有序动作并渲染远端角色两手动画，自己的第一人称镜头不被接管。该结果仅证明本机低延迟联机。

## 视觉证据

以下均为无光影实机截图。截图用于检查造型、握持、手别、刀光、界面和演出；伤害、材料消耗等结论来自运行检查。

- [第一人称双刀](verification/expansion-evidence/screenshots/07-dual-first-person.png)
- [交叉斩与第三人称握持](verification/expansion-evidence/screenshots/09-dual-cross-cut.png)
- [双刀奥义双弧](verification/expansion-evidence/screenshots/12-dual-ultimate-twin-arcs.png)
- [终斩后的收势](verification/expansion-evidence/screenshots/13b-dual-ultimate-after-cut.png)
- [真实独立创造分类](verification/expansion-evidence/screenshots/06-creative-tab.png)
- [太刀工作台配方](verification/expansion-evidence/screenshots/14-tachi-overworld-recipe.png)、[脇差配方](verification/expansion-evidence/screenshots/15-wakizashi-overworld-recipe.png)
- [胧影剑录](verification/expansion-evidence/screenshots/16-chronicle-advancements.png)
- [独立旁观客户端看到的奥义](verification/expansion-evidence/screenshots/network-observer-dual-ultimate.png)

模型区分长短刀，刀鞘是独立鞘体，左右手有不同动作时间线。刀光为世界空间有限寿命带状网格，包含彩色外沿和白色内芯，奥义有交叉双弧。视觉是否符合个人审美仍需用户体验确认。

## 日志、限制与未完成内容

Sodium/Iris 已加载，截图与本轮渲染使用**关闭光影**的配置。整合包日志仍有现有模组的 refmap、资源元数据回退、JER 配置/村民交易注册表提示，以及离线测试身份造成的认证/Realms 提示；这些未阻止世界加载或最终检查，未顺手修改其他模组。

尚未验证：开启用户光影包后的效果、远程高延迟/丢包服务器、PvP 开启后的真人对战、所有第三方战斗/相机/强制不死模组、全部左右利手/移动姿态/取消组合、每一种附魔的额外击后效果与长期性能。原版附魔标签支持不等同于所有附魔组合均完成实战测试。

尚未实现：专精树、空中派生、任意剑混搭、两把长太刀组合、成就称号/刀鞘装饰奖励。这些是后续方向，未在本版声称完成。

没有下载参考音视频或派生参考图片，交付截图都是本项目自己的测试画面。

## 重现与回退

Java 25 下：`./gradlew build --offline`。
验证入口：`python3 verification/run.py server`、`python3 verification/run.py client --fullpack`、`python3 verification/network.py`、`python3 verification/recipebook.py`。它们只使用 `build/verification-*` 下的隔离目录，QA 模组未打入发布 JAR。

退出游戏后移除正式 mods 的 0.2.0 JAR，将备份的 0.1.0 JAR 复制回去，保证同 ID 只有一份。配置备份可单独恢复。旧版无法识别脇差，已经在世界中使用新版物品时，应先另存该世界再回退。不要将整个备份包放进 mods。
