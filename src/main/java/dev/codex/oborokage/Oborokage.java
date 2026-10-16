package dev.codex.oborokage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import java.nio.file.*;
import java.util.Properties;
public final class Oborokage implements ModInitializer {
 public static final String ID="oborokage";
 public static final ResourceKey<Item> KEY=ResourceKey.create(Registries.ITEM,id("tachi"));
 public static final Item TACHI=Registry.register(BuiltInRegistries.ITEM,KEY,new TachiItem(new Item.Properties().setId(KEY).sword(ToolMaterial.NETHERITE,8,-2.6f).durability(4096).rarity(Rarity.EPIC).fireResistant()));
 public static final ResourceKey<Item> WAKI_KEY=ResourceKey.create(Registries.ITEM,id("wakizashi"));
 public static final Item WAKIZASHI=Registry.register(BuiltInRegistries.ITEM,WAKI_KEY,new WakizashiItem(new Item.Properties().setId(WAKI_KEY).sword(ToolMaterial.IRON,4,-1.8f).durability(2048).rarity(Rarity.EPIC)));
 public static final CreativeModeTab EQUIPMENT=Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,id("equipment"),net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder().title(net.minecraft.network.chat.Component.translatable("itemGroup.oborokage.equipment")).icon(()->new ItemStack(TACHI)).displayItems((parameters,output)->{output.accept(TACHI);output.accept(WAKIZASHI);}).build());
 public static final net.minecraft.core.particles.SimpleParticleType CRESCENT=Registry.register(BuiltInRegistries.PARTICLE_TYPE,id("crescent"),net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));
 public static final net.minecraft.core.particles.SimpleParticleType STREAK=Registry.register(BuiltInRegistries.PARTICLE_TYPE,id("streak"),net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));
 public static boolean allowPlayerDamage=false;
 public static Identifier id(String s){return Identifier.fromNamespaceAndPath(ID,s);}
 @Override public void onInitialize(){
  PayloadTypeRegistry.clientboundPlay().register(SlashPayload.TYPE,SlashPayload.CODEC);
  PayloadTypeRegistry.serverboundPlay().register(ActionRequest.TYPE,ActionRequest.CODEC);
  PayloadTypeRegistry.clientboundPlay().register(ActionEvent.TYPE,ActionEvent.CODEC);
  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(ActionRequest.TYPE,(message,context)->context.server().execute(()->DualCombat.request(context.player(),message)));
  net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((p,l,h,e,hit)->DualCombat.equipped(p)?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS);
  net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((e,source,amount)->{if(e instanceof net.minecraft.server.level.ServerPlayer p)DualCombat.captureDamage(p);return DualCombat.allowDamage(e,source,amount);});
  net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((entity,viewer)->{if(entity instanceof net.minecraft.server.level.ServerPlayer p&&ServerPlayNetworking.canSend(viewer,ActionEvent.TYPE)){var snapshot=DualCombat.snapshot(p);if(snapshot.move()!=0)ServerPlayNetworking.send(viewer,snapshot);}});
  net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->DualCombat.remove(handler.player));
  loadConfig();
  net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,base,taken,blocked)->{if(taken>0&&!blocked){Combat.markDamaged(entity.getUUID());if(entity instanceof net.minecraft.server.level.ServerPlayer p)DualCombat.afterDamage(p);}});
  net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{if(entity instanceof net.minecraft.server.level.ServerPlayer p){DualCombat.damaged(p);DualCombat.remove(p);}});
  ServerTickEvents.END_SERVER_TICK.register(Combat::tick);
  ServerLifecycleEvents.SERVER_STOPPED.register(s->Combat.clear());
 }
 private static void loadConfig(){
  Path p=FabricLoader.getInstance().getConfigDir().resolve("oborokage-server.properties");
  try {if(!Files.exists(p)) Files.writeString(p,"# Server-authoritative: restart server after changing.\nallowPlayerDamage=false\n");
   Properties config=new Properties();try(var in=Files.newInputStream(p)){config.load(in);}allowPlayerDamage=Boolean.parseBoolean(config.getProperty("allowPlayerDamage","false"));
  }catch(Exception e){throw new IllegalStateException("Cannot read Oborokage server config",e);}
 }
}
