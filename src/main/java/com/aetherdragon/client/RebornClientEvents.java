package com.aetherdragon.client;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.client.model.ShadayArmorModel;
import com.aetherdragon.client.particle.DragonDecalParticle;
import com.aetherdragon.client.particle.DragonQuakeBlockParticle;
import com.aetherdragon.client.particle.DragonShardParticle;
import com.aetherdragon.client.particle.DragonShockwaveParticle;
import com.aetherdragon.client.particle.JawParticle;
import com.aetherdragon.client.particle.PunchRingParticle;
import com.aetherdragon.entity.RebornMobEntity;
import com.aetherdragon.entity.RebornProjectileEntity;
import com.aetherdragon.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import com.aetherdragon.registry.ModParticles;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Mod.EventBusSubscriber(modid = AetherDragon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RebornClientEvents {
    private RebornClientEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (var entry : ModEntities.MOBS.entrySet()) {
            if (RebornGeoModel.supports(entry.getKey())) {
                event.registerEntityRenderer(entry.getValue().get(), RebornGeoRenderer::new);
            } else {
                event.registerEntityRenderer(entry.getValue().get(), RebornMobRenderer::new);
            }
        }
        for (var entry : ModEntities.PROJECTILES.entrySet()) {
            if (entry.getKey().equals("shulkercrysta")) {
                event.registerEntityRenderer(entry.getValue().get(), PyreSpearProjectileRenderer::new);
            } else {
                event.registerEntityRenderer(entry.getValue().get(), RebornProjectileRenderer::new);
            }
        }
        // 艾瑟龙技能实体
        event.registerEntityRenderer(ModEntities.DRAGON_SKILL.get(), DragonSkillRenderer::new);
        // 灵魂技能实体
        event.registerEntityRenderer(ModEntities.SPIRIT_SKILL.get(), SpiritSkillRenderer::new);
        // 天境娜迦技能实体
        event.registerEntityRenderer(ModEntities.NAGA_SKILL.get(), NagaSkillRenderer::new);
        // 水晶石傀儡技能实体
        event.registerEntityRenderer(ModEntities.GOLEM_SKILL.get(), GolemSkillRenderer::new);
        // 盐蟹王技能实体
        event.registerEntityRenderer(ModEntities.CRAB_SKILL.get(), CrabSkillRenderer::new);
        // 远古潜影贝技能实体
        event.registerEntityRenderer(ModEntities.SHULKER_SKILL.get(), ShulkerSkillRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ShadayArmorModel.LAYER_LOCATION, ShadayArmorModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FIRE.get(), FlameParticle.Provider::new);
        event.registerSpriteSet(ModParticles.RUNESEFFECT.get(), SmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.WHITESMOKE.get(), SmokeParticle.Provider::new);
        // 艾瑟龙转阶段特效
        event.registerSpriteSet(ModParticles.DRAGON_SHOCKWAVE.get(), DragonShockwaveParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DRAGON_SHARD.get(), DragonShardParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DRAGON_WARNING.get(), DragonShockwaveParticle.WarningProvider::new);
        event.registerSpriteSet(ModParticles.DRAGON_SLASH.get(), DragonDecalParticle.SlashProvider::new);
        event.registerSpecial(ModParticles.DRAGON_QUAKE_BLOCK.get(), new DragonQuakeBlockParticle.Provider());
        // 灵魂技能特效
        event.registerSpriteSet(ModParticles.SPIRIT_RING.get(), DragonShockwaveParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPIRIT_WARNING.get(), DragonShockwaveParticle.PlainWarningProvider::new);
        event.registerSpriteSet(ModParticles.SPIRIT_SLASH.get(), DragonDecalParticle.SlashProvider::new);
        event.registerSpriteSet(ModParticles.SPIRIT_SHARD.get(), DragonShardParticle.Provider::new);
        // 水晶石傀儡专属粒子
        event.registerSpriteSet(ModParticles.GOLEM_RING.get(), DragonShockwaveParticle.Provider::new);
        event.registerSpriteSet(ModParticles.GOLEM_WARNING.get(), DragonShockwaveParticle.PlainWarningProvider::new);
        event.registerSpriteSet(ModParticles.GOLEM_SHARD.get(), DragonShardParticle.Provider::new);
        event.registerSpriteSet(ModParticles.GOLEM_PUNCH.get(), PunchRingParticle.Provider::new);
        // 天境娜迦技能特效
        event.registerSpriteSet(ModParticles.NAGA_RING.get(), DragonShockwaveParticle.Provider::new);
        event.registerSpriteSet(ModParticles.NAGA_WARNING.get(), DragonShockwaveParticle.PlainWarningProvider::new);
        event.registerSpriteSet(ModParticles.NAGA_SLASH.get(), DragonDecalParticle.SlashProvider::new);
        event.registerSpriteSet(ModParticles.NAGA_BITE.get(), JawParticle.BiteProvider::new);
        // 盐蟹王技能特效
        event.registerSpriteSet(ModParticles.SALT_RING.get(), DragonShockwaveParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SALT_WARNING.get(), DragonShockwaveParticle.PlainWarningProvider::new);
        event.registerSpriteSet(ModParticles.SALT_SHARD.get(), DragonShardParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SALT_SPRAY.get(), DragonShardParticle.ShortProvider::new);
        event.registerSpriteSet(ModParticles.CRAB_PINCH.get(), JawParticle.PinchProvider::new);
        // 远古潜影贝技能特效
        event.registerSpriteSet(ModParticles.SHULKER_RING.get(), DragonShockwaveParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHULKER_WARNING.get(), DragonShockwaveParticle.PlainWarningProvider::new);
        event.registerSpriteSet(ModParticles.SHULKER_SPARK.get(), DragonShardParticle.Provider::new);
        // 燔祭长矛投射物：熔火冲击环、火星
        event.registerSpriteSet(ModParticles.PYRE_RING.get(), DragonShockwaveParticle.Provider::new);
        event.registerSpriteSet(ModParticles.PYRE_EMBER.get(), DragonShardParticle.Provider::new);
    }

    private static final class RebornGeoRenderer extends GeoEntityRenderer<RebornMobEntity> {
        private static final ResourceLocation DRAGON_BEAM = new ResourceLocation(AetherDragon.MOD_ID, "textures/entity/dragon_skill/dragon_beam.png");
        private static final ResourceLocation NAGA_BEAM = new ResourceLocation(AetherDragon.MOD_ID, "textures/entity/naga_skill/naga_beam.png");
        private static final ResourceLocation SHULKER_BEAM = new ResourceLocation(AetherDragon.MOD_ID, "textures/entity/shulker_skill/shulker_beam.png");

        private RebornGeoRenderer(EntityRendererProvider.Context context) {
            super(context, new RebornGeoModel());
            this.shadowRadius = 0.5F;
        }

        @Override
        public void render(RebornMobEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
            boolean nagaBeam = entity.isNagaBeamActive();
            boolean shulkerBeam = entity.isShulkerBeamActive();
            boolean beam = entity.isDragonBeamActive() || nagaBeam || shulkerBeam;
            // 光束起点骨骼：龙为下颚，娜迦为嘴部定位点
            String boneName = "jaw";
            ResourceLocation texture = DRAGON_BEAM;
            if (nagaBeam) {
                boneName = "mouth";
                texture = NAGA_BEAM;
            }
            if (shulkerBeam) {
                boneName = "mouth";
                texture = SHULKER_BEAM;
            }
            // 光束期间追踪起点骨骼的世界坐标
            if (beam) {
                getGeoModel().getBone(boneName).ifPresent(bone -> bone.setTrackingMatrices(true));
            }
            super.render(entity, yaw, partialTick, poseStack, buffers, light);
            if (beam) {
                ResourceLocation beamTexture = texture;
                getGeoModel().getBone(boneName).ifPresent(bone -> renderDragonBeam(entity, yaw, partialTick, poseStack, buffers, bone.getWorldPosition(), beamTexture));
            }
        }

        private void renderDragonBeam(RebornMobEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, Vector3d jaw, ResourceLocation texture) {
            // 以实体插值位置为原点，计算光束起止点
            double ox = Mth.lerp(partialTick, entity.xo, entity.getX());
            double oy = Mth.lerp(partialTick, entity.yo, entity.getY());
            double oz = Mth.lerp(partialTick, entity.zo, entity.getZ());
            // GeckoLib 的骨骼世界坐标基于未插值的实体位置
            Vec3 worldStart = new Vec3(jaw.x, jaw.y, jaw.z);
            Vec3 start = worldStart.subtract(ox, oy, oz);
            Vec3 worldEnd = entity.getDragonBeamEnd();
            Vec3 end = worldEnd.subtract(ox, oy, oz);
            if (entity.getIdPath().equals("eldershulker")) {
                Vec3 delta = worldEnd.subtract(worldStart);
                double horizontalLength = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                float renderYaw = entityYaw * Mth.DEG_TO_RAD;
                Vec3 direction = new Vec3(-Mth.sin(renderYaw), 0.0D, Mth.cos(renderYaw));
                end = start.add(direction.scale(horizontalLength)).add(0.0D, delta.y, 0.0D);
            }
            Vec3 dir = end.subtract(start);
            double length = dir.length();
            if (length < 0.5D) {
                return;
            }
            // 面向相机展开光束宽度
            Vec3 cam = this.entityRenderDispatcher.camera.getPosition().subtract(ox, oy, oz);
            Vec3 side = dir.cross(cam.subtract(start)).normalize();
            float time = entity.tickCount + partialTick;
            float scroll = -time * 0.35F;
            float pulse = 1.0F + Mth.sin(time * 0.9F) * 0.12F;
            float vLength = (float) (length / 4.0D);
            VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(texture));
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            // 外层光晕与内层核心
            for (float width : new float[]{2.4F * pulse, 1.0F * pulse}) {
                Vec3 s = side.scale(width * 0.5F);
                DragonSkillRenderer.vertex(consumer, pose, normal, (float) (start.x - s.x), (float) (start.y - s.y), (float) (start.z - s.z), 0.0F, scroll);
                DragonSkillRenderer.vertex(consumer, pose, normal, (float) (start.x + s.x), (float) (start.y + s.y), (float) (start.z + s.z), 1.0F, scroll);
                DragonSkillRenderer.vertex(consumer, pose, normal, (float) (end.x + s.x), (float) (end.y + s.y), (float) (end.z + s.z), 1.0F, scroll + vLength);
                DragonSkillRenderer.vertex(consumer, pose, normal, (float) (end.x - s.x), (float) (end.y - s.y), (float) (end.z - s.z), 0.0F, scroll + vLength);
            }
        }
    }

    private static final class RebornMobRenderer extends MobRenderer<RebornMobEntity, HumanoidModel<RebornMobEntity>> {
        private RebornMobRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(RebornMobEntity entity) {
            return new ResourceLocation(AetherDragon.MOD_ID, "textures/entities/" + entity.getTextureName() + ".png");
        }
    }

    private static final class RebornProjectileRenderer extends ArrowRenderer<RebornProjectileEntity> {
        private RebornProjectileRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(RebornProjectileEntity entity) {
            return new ResourceLocation("minecraft", "textures/entity/projectiles/arrow.png");
        }
    }
}

