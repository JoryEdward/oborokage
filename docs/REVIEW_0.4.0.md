# 0.4.0 审查与修复记录

本文件记录 0.3.1 → 0.4.0 逐文件审查中确认的缺陷、修复方式，以及新增玩法的设计边界。每项修复都有对应回归检查（`verification/src/oborokage/check/ExpansionChecks.java`，或客户端冒烟 `ClientSmokeChecks.java`）。

## 已确认缺陷

| # | 位置 | 现象 | 修复 | 检查 |
|---|---|---|---|---|
| 1 | `DualCombat.begin` | 影步横向方向颠倒：`forward × up` 得到的是玩家右侧，按 A 会向右闪 | 改为 `up × forward` | “Shadow step follows strafe input” |
| 2 | `Oborokage` 攻击回调 + `DualClient.interceptAttack` | 双刀在手时左键任何实体都被拦截，末影水晶、展示框、船、矿车无法打碎（末影龙战会被卡住） | 只拦截 `LivingEntity`，其他实体走原版攻击 | “Drawn blades still break end crystals” |
| 3 | `HandsMixin` | 双刀奥义借用 `localAge/localFull`，第一人称同时叠加原居合拔刀位移，且副手也会跟着转 | 仅主手、仅非双刀奥义时应用居合动作 | 客户端冒烟奥义段 |
| 4 | `DualCombat.hit` | 连招挥空也扣耐久 | 有命中才扣；奥义保持原消耗 | “Missed chain swing spends no durability” |
| 5 | `DualCombat.snapshot` | 任何玩家被追踪时都会 `computeIfAbsent` 创建状态，且永不回收 | 只读已有状态；未装备、空闲且已恢复的状态在 tick 中回收 | “Idle unequipped dual state is released” |
| 6 | `DualCombat.guardable` | 持续格挡对一切投射物无效；精准窗口只对箭有反射 | 正面投射物可格挡：精准抵消，持续减至 35%（`guardProjectiles`） | 三项投射物检查 |
| 7 | `GuardDamageMixin` | `@ModifyArgs` 为世界中每个生物每次受伤分配 `Args` 数组 | 改为 `@ModifyArg(index = 2)` | 全部伤害相关回归 |
| 8 | `Feats.enemy` | 每次判定都 `TagKey.create` | 常量 | — |
| 9 | `DualClient` 键位冲突检查 | 26.3 上首次装备必定误报“影步与转储版本信息冲突”（F3+V 调试键） | 跳过 `DEBUG` 分类 | 客户端冒烟截图聊天栏 |
| 10 | 26.1.x 兼容 | 客户端 `InputConstants.Type.KEYBOARD` 不存在直接崩溃；5 个配方进度 `recipe`/`recipes` 字段解析失败 | 版本线编译 + `ClientCompat` + 分版本数据文件 | 26.1/26.1.1/26.1.2 实测 |

## 多版本结构

`versions/<线>.properties` 指定编译用的 Minecraft / Fabric API 版本与 `fabric.mod.json` 中的依赖范围。`versions/<线>/` 下只放有差异的类与资源，公共源码一律不含版本分支：

| 差异 | 26.1.x | 26.2 | 26.3 |
|---|---|---|---|
| 挥刀 | `swing(hand, true)` | 同左 | `swing(hand, SwingAnimation, true)` |
| 受击无敌 | `invulnerableTime` | 同左 | `damageCooldownTime` + `setInvulnerableTime` |
| 击退 | `knockback(s, x, z)` | 带伤害源五参 | 同 26.2 |
| 箭矢弹反 | `deflect(…, boolean)` | 同左 | 多一个速度参数 |
| 当前界面 | `Minecraft.screen` | `Gui.screen()` | 同 26.2 |
| 键位类型 | `KEYSYM` | 同左 | `KEYBOARD` |
| PoseStack 旋转 | `mulPose` | 同左 | `rotateDegrees` |
| 帧间插值 | `DeltaTracker` | 同左 | `worldPartialTicks` |
| 顶点 | 无 `setUv3` | 同左 | `setUv3` |
| 第一人称手部 | `ItemInHandRenderer.renderArmWithItem` | `ItemInHandRenderer.submitArmWithItem` | `FirstPersonHandsAndItemsRenderer.submitArmWithItem` |
| 配方解锁条件 | `recipe` | 同左 | `recipes` |

其余 Mixin 注入点（`Camera.alignWithEntity`、`ItemModelResolver.updateForLiving`、`UseDuration.get`、`HumanoidModel.setupAnim`、`Minecraft.startAttack`、`PlayerAdvancements.award` 中的 `Optional.ifPresent`、`LivingEntity.hurtServer → actuallyHurt`、`AbstractArrow.onHitEntity`、`ItemStack.canDestroyBlock`）已用 javap 在三条线逐一核对存在，并经实际启动确认。

## 新玩法的边界

- 见切：只对有来源实体的伤害生效（排除虚空、`/kill` 等 `BYPASSES_INVULNERABILITY`）；只在影步开始后 `perfectDodgeTicks` 内；剑意奖励每 20 tick 至多一次。影步冷却 16 tick，因此连续闪避时最多约 1/4 时间处于见切帧。
- 影返：只强化下一个**命中**的连招窗口，奥义不吃加成。
- 雷链：每个被斩敌人最多 2 道，每次交叉斩最多 4 道；只连敌对生物，遵守友方保护与视线。
- 霜蚀：叠层 5 秒过期；冻结使用原版冰冻机制（`canFreeze` 为假的生物只受减速）。
- 虚刃：7 格，受墙阻挡，不重复命中本窗口已斩目标。
- 虚印：仅标记者本人可引爆，引爆一次即消失；全服最多 512 条记录。
- 回霜：延迟脉冲最多同时 64 个；施放者离线、死亡或换维度即取消。
- 所有新增伤害都走 `WeaponDamage.apply`，计算力量/虚弱/攻击属性与刀的附魔，遵守 `TargetRules`。原居合 1000 点终结、40 tick 蓄力、冷却、耐久消耗不变。
