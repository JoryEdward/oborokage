# 0.3.0 验证与交付

2026-10-16（America/Los_Angeles）。目标实际实例为Minecraft26.3、Fabric Loader0.19.5、Fabric API0.162.0+26.3、Java25；真实客户端日志识别Apple M4。所有游戏测试使用项目内隔离目录及生成世界，未打开用户存档。

## 最终产物

- 发布：`release/oborokage-tachi-0.3.0.jar`
- 安装：`/Users/mac/Library/Application Support/minecraft/versions/26.3-Fabric/mods/oborokage-tachi-0.3.0.jar`
- SHA-256：`64e8c1430624ece2ed4dcb43a6520566df9cabba7c29cbffcd66651c8f3f507e`
- 构建、服务端、完整客户端、配方书、两个联网客户端、独立联网服务器、GameTest和安装文件逐一核对为同一SHA。
- 只有一个启用的oborokage JAR；发布包不含QA类、测试Mixin、旧dev.codex包。终结豁免标签发布默认空。
- 源码：`release/oborokage-0.3.0-source.tar.gz`，另附Gradle sources JAR。

机器可读详情：[summary.json](verification/release-0.3.0/summary.json)、[installation.json](verification/release-0.3.0/installation.json)。

## 本次最终检查

| 检查 | 记录结果 | 具体范围 |
|---|---:|---|
| Gradle格式及构建 | PASS | formatJavaCheck、Java25编译、独立JAR及sources |
| 受控服务器 | 105 PASS / 0 FAIL | 原刀1000、1024HP+吸收/护甲/抗性/图腾/无敌、Boss、碰撞、原冷却/耐久、双刀命中/成就、属性/附魔、同源格挡、保护、配置、豁免标签 |
| 完整实际客户端 | 30 PASS / 0 FAIL | 现有模组组合、Complementary光影、512顶点最低预算、真实使用/攻击包、镜头/创造栏、架刀/iframe/箭碰撞、斩物掉落/权限、三把新刀、四个新成就、原版/reload |
| 原版配方书 | 6 PASS / 0 FAIL | 正常拾取紫水晶，解锁原刀和脇差，客户端收到配方显示 |
| 两客户端与独立服务器 | 12 PASS / 0 FAIL | 双向各100ms有序延迟；本地立即预测、远端动作、1000终结、本人及观察者镜头、连接保持 |
| 原生Fabric GameTest | 2 PASS / 0 FAIL | 高生命值原刀终结、三把新增刀原版配方；不把minecraft:always_pass计入 |
| 真实回退保存 | 3阶段完成 | 0.3保存→0.1保存→0.3重开：原刀保留；脇差、新刀丢失并且不会因再升级恢复 |
| 文件保护 | 271文件哈希一致 | 其他mods、配置及用户saves；原版JAR、源码、配置、世界备份保存在mods外 |

这些数字是结果文件中的记录行/测试，不与0.2.0的117项直接比较。客户端的若干组合记录包含多个服务器原生断言，例如32原木掉落守恒、BEFORE事件拒绝、冒险模式、箱子和基岩保护。它们通过真实客户端与integrated server运行，仍属于脚本化验收。

证据位于`verification/release-0.3.0/`：最终日志、结果文件、GameTest XML、哈希表、截图。截图中低预算刀光比默认质量更有棱角，是预算降低细分的预期结果；主刀光保持可见。日志中离线测试账号会有401/Realms联网检查错误，未阻止世界加载和模组断言。

## 视觉与玩法结论

五刀显示、GUI与创造栏可见，弓模组未参与修改。新刀保留原创长刀轮廓，快刀较短、霜刀较宽、空弦较细，各自材质/图标/刀鞘与刀光配色区分。新世界加载成功，没有新增配方造成的数据包错误。

原居合强度和原参数通过回归。双刀增强范围与破势，按实际武器计算原版附魔击后效果。精准架刀反射真实箭矢，原版无敌帧拒绝的后续伤害不扣架势。下蹲斩木满蓄力在40格测试树干中斩32格，生存掉落32原木，权限拒绝保持整根树干，冒险模式不越权。

实际启用了ComplementaryReimagined_r5.9.3、Iris和Sodium；已查看刀光与新刀截图。空弦出刀初版的大白粒子改为远离视点的小型紫色尘粒，避免挡准星。正常质量开发截图及本次最终最低预算截图共同用于检查。

## 备份及回退

备份目录：
`/Users/mac/Documents/ChatGPT/Mods/Oborokage-0.3.0/backups/release-20261016-131851`

包括旧`oborokage-tachi-0.2.0.jar`、`source-0.2.0.tar.gz`、旧相关配置、`saves-before-upgrade.tar.gz`及保护文件哈希表。旧0.2.0工程也保留在同级目录。安装时只替换了oborokage JAR，并为其相关配置补齐缺失字段，已有设置值保留。

回退应先关闭游戏，移出0.3.0 JAR，恢复对应旧JAR和配置；如世界已含新版刀，应同时恢复对应升级前世界备份。仅换旧JAR会使旧版不认识的物品消失。不要把备份JAR放回mods后与新版同时启用。

## 尚未验证

- 真人连续游玩后的连招手感、镜头晕眩和最终审美接受度。
- 互联网不稳定网络、长期大型多人同时奥义的帧时间/TPS。
- 所有光影包、所有天气/水面透明排序组合。
- 任意第三方主动复活或重写死亡/生命值的实体。豁免标签及限频重试提供边界，无法通用保证。
- 新刀每种附魔组合、铁砧修理/经验修补全部生存情形；本次确认常用六项战斗附魔，配方和支持标签。

现有存档的实际游戏内容未经本次打开验证，这是按用户要求保留存档所采用的工作边界。
