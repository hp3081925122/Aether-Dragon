package com.aetherdragon.client;

import com.aetherdragon.entity.RebornMobEntity;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.constant.DataTickets;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.loading.math.MathParser;
import com.geckolib.loading.math.MolangQueries;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class DragonRenderer extends GeoEntityRenderer<RebornMobEntity, DragonRenderState> {
    private static final org.slf4j.Logger ANIMATION_LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final net.minecraft.resources.Identifier BEAM_TEXTURE = net.minecraft.resources.Identifier.fromNamespaceAndPath(
            "aether_dragon", "textures/entity/dragon_skill/dragon_beam.png");
    private static final com.mojang.blaze3d.pipeline.RenderPipeline BEAM_PIPELINE = net.minecraft.client.renderer.RenderPipelines.EYES.toBuilder()
            .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("aether_dragon", "pipeline/dragon_beam"))
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE))
            .build();
    private static final net.minecraft.client.renderer.rendertype.RenderType BEAM_RENDER_TYPE = net.minecraft.client.renderer.rendertype.RenderType.create(
            "aether_dragon_beam", net.minecraft.client.renderer.rendertype.RenderSetup.builder(BEAM_PIPELINE)
                    .withTexture("Sampler0", BEAM_TEXTURE).sortOnUpload().createRenderSetup());
    private static final String[] DEBUG_BONES = {"dragon", "hip", "body", "body2", "wing_left_master", "wing_left",
            "wing_left2", "wingtip_left", "wing_right_master", "wing_right3", "wing_right4", "wingtip_right"};
    public DragonRenderer(EntityRendererProvider.Context context) {
        super(context, new DragonModel());
        this.shadowRadius = 0.5F;
    }

    @Override
    public DragonRenderState createRenderState(RebornMobEntity entity, Void context) {
        return new DragonRenderState();
    }

    @Override
    public void extractRenderState(RebornMobEntity entity, DragonRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (state.hasGeckolibData(DataTickets.ANIMATION_CONTROLLER_STATES)) {
            var movement = state.getGeckolibData(DataTickets.ANIMATABLE_MANAGER).getAnimationControllers().get("movement");
            var sources = state.getGeckolibData(DataTickets.ANIMATION_CONTROLLER_STATES);
            for (int index = 0; index < sources.length; index++) {
                var source = sources[index];
                if (movement != null && source.animationPoint() == movement.getCurrentAnimationPoint()) {
                    sources[index] = DragonRenderState.alignMovementTransition(source, movement.getTransitionTicks());
                }
            }
        }
        state.beam = entity.isDragonBeamActive();
        state.beamEnd = entity.getDragonBeamEnd();
        boolean paused = Minecraft.getInstance().isPaused();
        state.debugSample = ANIMATION_LOGGER.isDebugEnabled() && entity.shouldCaptureDragonPoseDebug(paused);
        if (state.debugSample) {
            state.debugEntity = entity.getUUID();
            state.debugTick = entity.tickCount;
            state.debugSkill = entity.getDragonSkill();
            state.debugSkillTicks = entity.getClientDragonSkillTicks();
            state.debugPhaseTwo = entity.isDragonPhaseTwo();
            state.debugTransitioning = entity.isDragonTransitioning();
            state.debugAlive = entity.isAlive();
            state.debugPaused = paused;
            state.debugHealth = entity.getHealth();
            state.debugVelocity = entity.getDeltaMovement();
            StringBuilder controllers = new StringBuilder();
            if (state.hasGeckolibData(DataTickets.ANIMATABLE_MANAGER)) {
                var manager = state.getGeckolibData(DataTickets.ANIMATABLE_MANAGER);
                for (var controller : manager.getAnimationControllers().values()) {
                    var point = controller.getCurrentAnimationPoint();
                    controllers.append(controller.getName()).append("[play=").append(controller.getPlayState())
                            .append(",clip=").append(point == null ? null : point.animation().name())
                            .append(",time=").append(controller.getCurrentAnimationTime())
                            .append(",timeline=").append(controller.getCurrentTimelineTime())
                            .append(",speed=").append(controller.getAnimationSpeed())
                            .append(",finished=").append(controller.hasAnimationFinished())
                            .append(",triggered=").append(controller.isPlayingTriggeredAnimation()).append("] ");
                }
            }
            state.debugControllers = controllers.toString();
        }
    }

    @Override
    public void submitRenderTasks(com.geckolib.renderer.base.RenderPassInfo<DragonRenderState> pass,
            net.minecraft.client.renderer.OrderedSubmitNodeCollector collector,
            net.minecraft.client.renderer.rendertype.RenderType type) {
        DragonRenderState state = pass.renderState();
        if (state.debugSample) {
            logDragonPose(pass);
        }
        if (state.beam) {
            net.minecraft.world.phys.Vec3[] start = {null};
            pass.model().getBone("jaw").ifPresent(bone -> pass.renderPosed(() -> {
                var pose = pass.poseStack();
                pose.pushPose();
                com.geckolib.util.RenderUtil.transformToBone(pose, bone);
                var local = com.geckolib.util.RenderUtil.extractPoseFromRoot(pose.last().pose(), pass.getPreRenderMatrixState());
                start[0] = com.geckolib.util.RenderUtil.renderPoseToPosition(local, 1, 1, 1).add(state.x, state.y, state.z);
                pose.popPose();
            }));
            if (start[0] != null) {
                var pose = new com.mojang.blaze3d.vertex.PoseStack();
                pose.last().set(pass.getPreRenderMatrixPose());
                submitBeam(state, start[0], pose, collector, pass.cameraState());
            }
        }
        super.submitRenderTasks(pass, collector, type);
    }

    private static void logDragonPose(RenderPassInfo<DragonRenderState> pass) {
        DragonRenderState state = pass.renderState();
        Long instance = state.hasGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID)
                ? state.getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID) : null;
        ANIMATION_LOGGER.debug("Aether Dragon render snapshot: entity={}, instance={}, tick={}, renderAge={}, paused={}, alive={}, skill={}, skillTicks={}, phaseTwo={}, transitioning={}, beam={}, health={}, position={},{},{}, velocity={}, bodyYawDegrees={}, controllers={}",
                state.debugEntity, instance, state.debugTick, state.getAnimatableAge(), state.debugPaused, state.debugAlive,
                state.debugSkill, state.debugSkillTicks, state.debugPhaseTwo, state.debugTransitioning, state.beam, state.debugHealth,
                state.x, state.y, state.z, state.debugVelocity, state.bodyRot, state.debugControllers);
        if (state.hasGeckolibData(DataTickets.ANIMATION_CONTROLLER_STATES)) {
            var sources = state.getGeckolibData(DataTickets.ANIMATION_CONTROLLER_STATES);
            var animTime = MathParser.getVariableFor(MolangQueries.ANIM_TIME);
            for (int index = 0; index < sources.length; index++) {
                var source = sources[index];
                var point = source.animationPoint();
                var previous = source.prevAnimationPoint();
                if (source.transitionTime() >= 0) {
                    var previousState = previous == null ? null : new ControllerState(previous, null, -1, source.transitionTicks(),
                            source.additive(), source.easingOverride(), source.renderState(), source.queryValues());
                    ANIMATION_LOGGER.debug("Aether Dragon transition source: entity={}, tick={}, sourceIndex={}, transitionTicks={}, previousClipTimeSeconds={}, previousQueryAnimTimeSeconds={}, currentQueryAnimTimeSeconds={}",
                            state.debugEntity, state.debugTick, index, source.transitionTicks(), previous == null ? null : previous.animTime(),
                            previousState == null ? null : previousState.getQueryValue(animTime), source.getQueryValue(animTime));
                }
                ANIMATION_LOGGER.debug("Aether Dragon pose source: entity={}, tick={}, sourceIndex={}, animation={}, previousAnimation={}, clipTimeSeconds={}, queryAnimTimeSeconds={}, transitionTimeSeconds={}, transitionTicks={}, additive={}, wingTracks={}, previousWingTracks={}",
                        state.debugEntity, state.debugTick, index, point == null ? null : point.animation().name(),
                        previous == null ? null : previous.animation().name(), point == null ? null : point.animTime(),
                        source.queryValues().containsKey(animTime) ? source.getQueryValue(animTime) : null,
                        source.transitionTime(), source.transitionTicks(), source.additive(), wingTracks(point), wingTracks(previous));
            }
        }
        pass.renderPosed(() -> {
            StringBuilder pose = new StringBuilder();
            for (String name : DEBUG_BONES) {
                var bone = pass.model().getBone(name).orElse(null);
                var snapshot = bone == null ? null : bone.frameSnapshot;
                pose.append(name).append('=');
                if (snapshot == null) {
                    pose.append("missing");
                } else {
                    pose.append(snapshot.getRotX()).append(',').append(snapshot.getRotY()).append(',').append(snapshot.getRotZ());
                }
                pose.append(';');
            }
            ANIMATION_LOGGER.debug("Aether Dragon final bone pose: entity={}, tick={}, boneAnglesRadians={}", state.debugEntity, state.debugTick, pose);
        });
    }

    private static String wingTracks(AnimationPoint point) {
        if (point == null) {
            return "none";
        }
        StringBuilder tracks = new StringBuilder();
        for (var bone : point.animation().boneAnimations()) {
            if (bone.boneName().startsWith("wing")) {
                var rotation = bone.rotationKeyFrames();
                tracks.append(bone.boneName()).append('[').append(rotation.xKeyframes().length).append(',')
                        .append(rotation.yKeyframes().length).append(',').append(rotation.zKeyframes().length).append("] ");
            }
        }
        return tracks.toString();
    }

    private static void submitBeam(DragonRenderState state, net.minecraft.world.phys.Vec3 worldStart,
            com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.OrderedSubmitNodeCollector collector,
            net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        var origin = new net.minecraft.world.phys.Vec3(state.x, state.y, state.z);
        var start = worldStart.subtract(origin);
        var end = state.beamEnd.subtract(origin);
        var direction = end.subtract(start);
        double length = direction.length();
        if (length < 0.5D) return;
        var side = direction.cross(camera.pos.subtract(origin).subtract(start)).normalize();
        float scroll = -state.ageInTicks * 0.35F;
        float pulse = 1 + net.minecraft.util.Mth.sin(state.ageInTicks * 0.9F) * 0.12F;
        if (state.debugSample) {
            ANIMATION_LOGGER.debug("Aether Dragon beam render: entity={}, tick={}, pipeline={}, blend=ONE_PLUS_ONE, worldStart={}, worldEnd={}, length={}, scroll={}, pulse={}",
                    state.debugEntity, state.debugTick, BEAM_PIPELINE.getLocation(), worldStart, state.beamEnd, length, scroll, pulse);
        }
        collector.submitCustomGeometry(pose, BEAM_RENDER_TYPE, (entry, vertices) -> {
            for (float width : new float[]{2.4F * pulse, 1.0F * pulse}) {
                var half = side.scale(width * 0.5F);
                var corners = new net.minecraft.world.phys.Vec3[]{start.subtract(half), start.add(half), end.add(half), end.subtract(half)};
                for (int i = 0; i < 4; i++) {
                    var point = corners[i];
                    DragonSkillRenderer.vertex(vertices, entry.pose(), entry.normal(), (float) point.x, (float) point.y, (float) point.z,
                            i == 0 || i == 3 ? 0 : 1, scroll + (i >= 2 ? (float) (length / 4) : 0));
                }
            }
        });
    }
}
