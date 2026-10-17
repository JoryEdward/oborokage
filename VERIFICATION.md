# 0.3.1 验证与交付

## 本次构建

- Minecraft26.3、Fabric Loader0.19.5、Fabric API0.162.0+26.3、Java25、Loom1.18.4。
- `formatJavaCheck`和独立JAR/sources构建通过。编译保留了原有弃用API提示；不声称零警告。
- JAR：`release/oborokage-tachi-0.3.1.jar`。
- SHA-256：`129e70e9b87c575a62aaa9e95c5114489e7745f77be72e1cc866fb8c67676998`。
- 构建产物、release、安装文件及8个0.3.1测试目录中的模组JAR逐一核对为同一SHA。
- sources：`release/oborokage-tachi-0.3.1-sources.jar`；完整源码包：`release/oborokage-0.3.1-source.tar.gz`；校验表：`release/SHA256SUMS.txt`。
- 源码包排除截图体积；70张截图另附`release/oborokage-0.3.1-evidence.zip`，解压到源码目录即可恢复文档中的截图链接。文本结果与日志仍包含在源码包内。

本机实际接手的Gemini同号0.3.0为`352bddc7…`，未找到任务所提`0545c4ee…`。旧版`64e8c143…`上的105/30/12/2记录没有用于下面的结论。Gemini基线的另一次实际截图检查仅用于前后对比，和0.3.1通过数分开。

## 实际重跑结果

| 检查 | 结果 | 范围 |
|---|---:|---|
| 受控服务器回归 | 110 PASS / 0 FAIL | 原105项及5项别人宠物保护；原刀强度/冷却/耐久、Boss、高生命值、碰撞、双刀、属性附魔、配置和保护 |
| 完整客户端回归 | 30 PASS / 0 FAIL | 实际模组组合，启用ComplementaryReimagined_r5.9.3，同时512顶点预算、64细分上限和单演员上限；创造栏、原刀与双刀演出、原生斩木/掉落/权限、箭、iframe、三把新刀、原版reload |
| 0.3.1客户端维护检查 | 66 PASS / 0 FAIL | 普通渲染下HUD实际绘制、光环、赤霆20/40时序、1/10/20及1/20/40原生音效、单手姿势、六种挖掘情况、刀碎、拒绝heartbeat、连招及真实成就广播 |
| 独立服务器+两客户端 | 19 PASS / 0 FAIL | 每方向100ms有序延迟；原12项及空闲CANCEL、精准格挡、反切预测/确认/真实伤害、旁观者动作 |
| 原版配方书 | 6 PASS / 0 FAIL | 正常获取紫水晶、原刀/脇差解锁及客户端配方显示 |
| 原生Fabric GameTest | 2 PASS / 0 FAIL | 高生命值原居合终结、新刀配方；XML中的minecraft:always_pass不计入 |

合计**233条PASS记录**，不是233次真人手动游玩。结果文件、最终运行日志、GameTest XML、70张前后对比/客户端/光影/联机截图及哈希详情均在`verification/release-0.3.1/`。入口：[summary.json](verification/release-0.3.1/summary.json)。

另用生成的箱子世界重跑0.3.1→0.1.0→0.3.1：原刀保留，旧版不认识的脇差和赤霆在旧版保存后消失，重新升级不能恢复。日志及三阶段箱子内容保留。未用用户世界做降级测试。

## 关键修正的证据

- 赤霆普通蓄力：实际音效tick为1/10/20，20tick条满；下蹲为1/20/40，约20tick条半满，40tick条满。霜轮/空弦有实际HUD及光环；第一人称、模型与HUD共用同一进度。
- 新刀普通技能不创建旧SlashPayload场景；Gemini新增的按刀名收刀分支在这些技能中不执行。删除该分支及3个未使用items包装，保留真实模型几何和双刀奥义的独立刀鞘分支。
- 新单刀技能与斩物的副手已放松；生产渲染模型回调实际记录到新姿势，并保存截图。
- 左键：站立瞄准方块、附近僵尸时首次点击出双刀斩；后续原版持续挖掘仍可进行。牛/村民不触发这次拦截；三种情况下下蹲均正常挖掉测试泥土。测试从Minecraft原生startAttack/continueAttack入口驱动，固定射线来自当前站立/下蹲眼位，不直接调用拦截函数冒充挖掘。
- 1耐久的原生采掘任务面对32原木，仅破坏1格即停，余31；真实满蓄力斩物先支付原有8点技能消耗，因此刀在破坏前碎掉，32格均保留。
- 按住H被REJECT后，heartbeat没有新增本地预测；冷却结束服务端仍启动真实架刀；松开再按恢复预测。
- 实际授予“借你一刀，还你一月”：默认服务端/客户端公告均0；true时各1；关闭后授予原版石器时代仍正常公告，模组成就进度始终完成。
- LegacyIaido.java、TachiItem.java与接手基线逐字节一致；原40tick、15+985q²、12tick前摇、6tick突进、18/45冷却、2/8消耗、4096耐久保留。[原执行器哈希](verification/release-0.3.1/invariants.json)。

逐项代码审计及截图索引：[GEMINI_AUDIT_0.3.1.md](docs/GEMINI_AUDIT_0.3.1.md)。

## 安装与保护

已安装到：`/Users/mac/Library/Application Support/minecraft/versions/26.3-Fabric/mods/oborokage-tachi-0.3.1.jar`。
该实例只有一个启用的oborokage JAR。安装前后的**36个其他Mod文件、84个配置文件、153个存档文件**逐文件SHA一致，水桶弓包含在其他Mod保护范围；未修改其工程。没有打开用户世界。全部游戏测试使用项目build内生成的隔离世界。

保护文件清单的规范化SHA，安装前后同为：
`b40f84cd08efcd01ab812a87b86758c0fd30886bd413618603fd2ab45ba398c2`。
含不同采集元数据的JSON文件本身可以有不同SHA，保护判断比较的是每个被保护文件的路径和内容SHA。

[安装详情](verification/release-0.3.1/installation.json)。原JAR、胧影配置和完整逐文件保护清单备份：
`/Users/mac/Documents/ChatGPT/Mods/Oborokage-0.3.1/backups/upgrade-20261016-200459`。

回退到本次Gemini0.3.0：关闭游戏，移出0.3.1 JAR，恢复备份中的0.3.0 JAR；配置未被此次安装修改。更早版本的世界/新增物品风险见上面的降级实测及README。备份JAR不得与新版同时放在mods内启用。重启游戏后加载新安装文件。

## 独立的26.1.2查找结果

本机版本名为26.1.2-Fabric-DH，不是1.26.2。找到的仍是352bddc7…的Gemini通用声明原件；未发现可用的独立26.1.2构建。

使用Java25、Loader0.19.5及对应API0.155.3，在额外隔离目录启动该原件：服务端可以启动，但五个配方进度因recipe_unlocked的recipe/recipes字段差异解析失败；客户端退出255，根因InputConstants.Type.KEYBOARD不存在，未进入世界。因此它未通过26.1.2适配检查，不能把Minecraft>=1.26声明当作全版本适配。

该失败检查与本次26.3的233条通过记录分开，完整日志也已保留。原件及说明另存于：
`/Users/mac/Documents/ChatGPT/Mods/Oborokage-26.1.2-compatibility-audit/`。
没有改写或安装到26.1.2正式实例，也未打开其现有存档。

## 尚未验证

- 真人连续连招手感、晕眩舒适度、所有FOV/左利手与输入设备组合。
- 其他光影包及其全部水面/天气/透明排序设置；本次只测上述Complementary版本和普通渲染。
- 长时间大规模多人性能、互联网WAN抖动/丢包、所有保护插件组合。
- 任意第三方特殊不死模组或未实现OwnableEntity的自定义宠物系统。
- 26.3之外的0.3.1运行兼容；26.1.2的Gemini原件启动失败已有实际证据。

测试日志含离线账号401/Realms提示、若干优化模组refmap/覆盖提示及临时flat生成参数的“No key layers”提示；它们未阻止本次26.3世界加载与断言。没有把启动成功等同于上述未测试事项通过。
