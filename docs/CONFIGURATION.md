# 配置说明

旧配置可以继续使用，缺少字段会采用默认值。读取失败不会导致游戏因本模组配置而崩溃；非法布尔值、非有限数字使用默认值，超范围数字会夹取到边界并记日志。服务端原版`/reload`完成后替换整套配置快照。

## 服务端

完整可复制文件：`config-examples/oborokage-server.properties`。

| 字段 | 默认 | 范围/意义 |
|---|---:|---|
| allowPlayerDamage | false | 模组斩击是否伤玩家 |
| protectPets / protectAllies / protectVillagers / protectGolems | true | 分别保护自己拥有的实体、队友、村民、铁傀儡 |
| protectOthersPets | false | 保护其他主人拥有的宠物；不改变 protectPets 对自己的宠物的独立设置，缺省保持现有行为 |
| finisherRetry | true | 标准死亡未成立时仅重试一次 |
| announceAdvancements | false | 本模组成就在聊天广播；不影响个人toast |
| reflectArrows | true | 精准架刀反射正面箭矢；反射箭遵守本模组友方保护 |
| guardWindowTicks | 4 | 1–10，按收到请求的服务端tick起算 |
| guardLatencyCapTicks | 3 | 0–3；补偿ceil(RTT/100ms) tick，有界放宽，不倒放伤害 |
| guardRearmTicks / emptyGuardRecoveryTicks | 20 / 24 | 12–100，成功/空架后重启间隔 |
| guardEntryCost / perfectGuardCost | 8 / 8 | 1–50，起手与成功消耗 |
| staminaRegenDelayTicks | 20 | 0–200，停止架刀后延迟回复 |
| parryRewardCooldownTicks | 40 | 12–200，同来源剑意奖励节流 |
| parryIntent / hitIntent | 20 / 12 | 0–100，格挡/有效命中奖励 |
| intentGainMultiplier | 1 | 0–5，奖励倍率 |
| dodgeDistance / dodgeCooldownTicks | 2.8 / 16 | 1–6格；6–200tick，碰撞仍优先 |
| postureDurationTicks | 30 | 5–200，破势窗口 |
| ultimateCooldownTicks / ultimateReach | 100 / 6.5 | 48–1200tick；4.8–10格 |
| dualDamageMultiplier | 1 | 0.1–10，仅新招式基础伤害倍率，原居合1000保留 |
| maxTargetsPerWindow | 64 | 8–256，每次技能窗口最多处理目标数 |
| terrainCutEnabled | true | 显式下蹲斩物总开关 |
| terrainMaxBlocks / terrainBlocksPerTick | 32 / 4 | 1–128；1–8，每次总量和每tick处理量 |
| terrainReach / terrainMaxHardness | 5 / 5 | 2–6格起手选点；1–50硬度，不允许不可破坏方块 |
| bladeArts | true | 0.4.0 流派终式总开关：月蚀/雷链/霜蚀/虚刃、虚印、回霜、雷返 |
| guardProjectiles | true | 交叉架刀同样防御正面投射物：精准窗口内抵消，持续格挡减至35% |
| perfectDodgeTicks | 3 | 0–5；影步起手的见切帧，0为关闭 |
| perfectDodgeIntent | 10 | 0–100；见切奖励剑意，每20 tick最多一次 |
| riposteWindowTicks / riposteMultiplier | 20 / 1.5 | 0–60 tick；1–3倍。见切后影步结束起算的影返窗口及下一击倍率 |
| voidMarkTicks / voidMarkBonus / voidMarkIntent | 100 / 1.25 / 8 | 20–400 tick；1–3倍；0–100。空弦满蓄力虚印的时长、双刀引爆倍率与剑意 |
| ultimateGuard | true | 0.5.0：奥义进行中，生物与投射物的攻击无法打断 |
| zanshinTicks | 100 | 0–400；奥义结束后“残心”速度 I 的时长，0 为关闭 |
| techniqueIntent / iaidoIntent | 6 / 10 | 0–100；本刀技、居合每命中一个敌人获得的剑意 |

斩物数量=`floor(1+(terrainMaxBlocks-1)×charge²)`；起始硬度以2为基准，并随蓄力增长到配置上限。主刀普通战斗不触发斩物。方块原生事件、冒险规则、世界边界、正确工具掉落仍有效。默认5硬度不能斩黑曜石。

终结豁免数据包文件：`data/oborokage/tags/entity_type/finisher_exempt.json`。例如：

```json
{"replace":false,"values":["anothermod:immortal_boss"]}
```

这是实体类型标签；测试注入过盔甲架豁免，发布JAR内默认值为空。

## 客户端

完整可复制文件：`config-examples/oborokage-client.properties`。修改后重启客户端。

| 字段 | 默认 | 范围/意义 |
|---|---:|---|
| cinematic / screenShake / thirdPersonUltimate | true | 镜头演出、震动、本人奥义第三人称分别控制 |
| localPrediction | true | 自己的双刀起手先播放，服务端确认/拒绝后校正 |
| trailShaderCompat | true | 原生自发光透明实体层；false回退beacon层 |
| tutorial / keyConflictWarning | true | 首次装备提示、实际键位冲突检查 |
| hudScale | 1 | 0.5–2 |
| hudOffsetX / hudOffsetY | 0 | -2000–2000，最终位置按可见范围夹取 |
| trailMaxPlayers | 16 | 1–32，绘制场景/效果上限 |
| trailSegments | 32 | 8–64，弧线细分 |
| trailVertexBudget | 16384 | 512–65536，共用预算；低预算先降低细分再裁剪 |
| trailDistance | 48 | 8–96格，远处裁剪 |

推荐低档：8玩家、16细分、8192顶点、32格；中档采用默认；高档可用24玩家、48细分、32768顶点、64格。预算限制网格，原版粒子仍受游戏粒子设置影响。
