package com.aetherdragon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public final class GeometryCapture implements MultiBufferSource {
    private static final BlockDisplayContext BLOCK_CONTEXT = BlockDisplayContext.create();
    private final Map<RenderType, CapturedVertices> batches = new LinkedHashMap<>();
    private final List<BlockDraw> blocks = new ArrayList<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return batches.computeIfAbsent(type, ignored -> new CapturedVertices());
    }

    public void renderBlock(BlockState block, PoseStack pose, int light, int overlay) {
        BlockModelRenderState state = new BlockModelRenderState();
        Minecraft.getInstance().getBlockModelResolver().update(state, block, BLOCK_CONTEXT);
        blocks.add(new BlockDraw(state, new Matrix4f(pose.last().pose()), light, overlay));
    }

    public void submit(PoseStack pose, SubmitNodeCollector collector) {
        batches.forEach((type, vertices) -> collector.submitCustomGeometry(pose, type, vertices::draw));
        for (BlockDraw draw : blocks) {
            pose.pushPose();
            pose.mulPose(draw.transform);
            draw.state.submitMultiLayer(pose, collector, draw.light, draw.overlay, 0);
            pose.popPose();
        }
    }

    private record BlockDraw(BlockModelRenderState state, Matrix4f transform, int light, int overlay) {}

    private static final class Vertex {
        float x, y, z, u, v, nx, ny, nz;
        int color = -1, overlay = OverlayTexture.NO_OVERLAY, light;
        Vertex(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    }

    private static final class CapturedVertices implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private Vertex current;
        public VertexConsumer addVertex(float x, float y, float z) {
            current = new Vertex(x, y, z);
            vertices.add(current);
            return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a << 24) | (r << 16) | (g << 8) | b);
        }
        public VertexConsumer setColor(int color) { current.color = color; return this; }
        public VertexConsumer setUv(float u, float v) { current.u = u; current.v = v; return this; }
        public VertexConsumer setUv1(int u, int v) { current.overlay = u | (v << 16); return this; }
        public VertexConsumer setUv2(int u, int v) { current.light = u | (v << 16); return this; }
        public VertexConsumer setNormal(float x, float y, float z) { current.nx = x; current.ny = y; current.nz = z; return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
        void draw(PoseStack.Pose pose, VertexConsumer consumer) {
            for (Vertex vertex : vertices) {
                consumer.addVertex(pose, vertex.x, vertex.y, vertex.z).setColor(vertex.color)
                    .setUv(vertex.u, vertex.v).setOverlay(vertex.overlay).setLight(vertex.light)
                    .setNormal(pose, vertex.nx, vertex.ny, vertex.nz);
            }
        }
    }
}
