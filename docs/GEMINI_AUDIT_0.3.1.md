# Gemini基线及0.3.1逐项审计

## 基线

任务提及最初0.3.0的`64e8c143…`和Gemini变体`0545c4ee…`。本机工程、release、安装目录实际同为：
`352bddc726d3ee58f836dadb0c6bd166eb535a8d806e16cb9aaf71d17dcda34c`。
未找到0545变体，因此以保留了所述六项改动的实际352变体继续。原工程保持不变；原件和补丁保存在本机backups/gemini-baseline。不能把原64变体的测试当作352或0.3.1的证据。

实际Gemini还放宽了依赖范围并添加条件Mixin插件；原件在26.3隔离客户端能加载，这不意味着跨版本已适配。0.3.1按指定26.3声明依赖，移除未经跨版本验证的插件；26.1.2另作只读兼容性检查。

## 六项Gemini修改

| 项目 | 0.3.1处理 | 实际证据 |
|---|---|---|
| 赤霆1/10/20音效 | 普通模式保留；下蹲委托父类1/20/40 | 客户端维护测试记录ServerLevel真实声音事件及使用tick |
| 四主刀HUD | 保留；以fullChargeTicks统一，赤霆通常20、下蹲40 | HUD绘制计数、条宽、20/40 tick截图；完整客户端仍验证原刀 |
| 赤霆5/10/15/20模型阈值 | 保留；UseDuration属性在赤霆下蹲时按40 tick归一到这四级 | 实际属性求值及蓄力截图；不会20 tick就显示满蓄力模型 |
| 按新刀名选择charged15/sheathed | 删除原居合分支内对新刀的扩展，移除3个未使用items包装；models几何保留 | 原Gemini三技能截图及sceneAge=-1断言；技能使用DualClient场景 |
| 方块目标附近双刀拦截 | 只认Feats.enemy；下蹲不拦截 | 僵尸/牛/村民×站立/下蹲六场原生挖掘测试，检查服务端方块实际消失 |
| 末影龙hostiles标签 | 删除重复条目，保留默认空标签供数据包扩展 | 本机26.3 EnderDragon实现Enemy；受控服务器仍验证满蓄力击杀末影龙 |

新刀普通出刀/收势不会触发SlashPayload的旧居合收刀分支，原先宣称的“材质回滚修复”没有相应运行路径。新刀配脇差的双刀奥义使用另一个真实可达分支，仍按每把刀选择自己的sheathed模型，予以保留。

普通新刀技能确认的视觉问题是：旧DualClient.pose无条件抬起副手，手里没有第二把刀时也像双刀架势。已为STORM/FROST/VOID/TERRAIN分别提供单手动作，副手放松并配合身体平衡。

截图：

- [Gemini霜轮旧动作](../verification/release-0.3.1/screenshots/baseline/baseline-1-single-technique.png)
- [霜轮新单手动作](../verification/release-0.3.1/screenshots/maintenance/maintenance-1-single-technique.png)
- [赤霆下蹲约20tick](../verification/release-0.3.1/screenshots/maintenance/maintenance-3-charge-20.png)
- [赤霆下蹲满40tick](../verification/release-0.3.1/screenshots/maintenance/maintenance-3-charge-40.png)

## 附件补丁

六个文件中的改动全部合入：fullChargeTicks供HUD、光环、HandsMixin使用；所有主刀蓄力均生成光环；赤霆下蹲走父类音效；空闲DualCombat.cancel不广播CANCEL；附近目标只认Feats.enemy；客户端默认配置注释说明重启客户端。配置示例的注释也已同步。

## 仍未处理问题的修正

- **按住H被拒闪烁**：记录本次真实按键产生的guard请求；收到对应REJECT后停止本次持续按住的GUARD预测。heartbeat仍向服务器发送，条件满足时接收真实START。新的一次按键重新启用预测。实际测试覆盖拒绝、20tick heartbeat无闪、冷却结束自动架刀和重新按键预测。
- **公告稳定注入**：WrapOperation挂到PlayerAdvancements.award内的Optional.ifPresent调用，未引用编译器lambda名。本机字节码确认该consumer仅执行公告；进度、奖励和可见性仍走原版。实际授予借你一刀成就：默认服务器/客户端公告均0；true时各1；再授予原版石器时代仍广播。客户端截图和日志留存。
- **别人宠物保护**：protectOthersPets默认false，独立于protectPets。实际创建另一个主人的狼，切换选项并用原居合终结路径检查保护。
- **刀碎后斩物**：每个方块破坏前检查原刀栈非空、仍为主刀且仍在同一手中。原生采掘测试1点耐久对32原木，只破坏1格，余31；真实满蓄力释放先扣原有8点技能耐久，破坏前刀碎，32格全部保留。这是两个不同边界，均已测试。
- **双刀取消与反切**：两客户端、独立服务器、每方向100ms有序延迟；检查空闲右键不向本人/旁观者发旧CANCEL、反切立即预测后得到确认、真实攻击伤害与旁观者动作。

## 强度及范围

LegacyIaido.java与TachiItem.java和实际Gemini源文件逐字节相同。原40tick、15+985q²、12tick前摇、6tick突进、18/45tick冷却、2/8消耗、4096耐久继续由受控服务器测试覆盖。新配置默认不改变对别人宠物的既有行为。

0.3.1的范围为26.3。另检查Gemini原件在本机26.1.2：服务端可启动但5个配方进度解析失败；客户端因InputConstants.Type.KEYBOARD不存在而退出，未进入世界。未发现可用的26.1.2专用构建，不把>=1.26声明当作全版本适配。

所有游戏检查使用build内生成的临时世界。人工手感、其他光影、第三方不死模组和长时多人性能不由这些脚本结果保证。完整结果及新JAR哈希见VERIFICATION.md。
