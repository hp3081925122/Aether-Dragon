package com.aetherdragon.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class ShadayArmorModel<T extends Entity>
extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("aether_dragon", "modelshaday_armor"), "main");
    public final ModelPart root;
    public final ModelPart waist;
    public final ModelPart body;
    public final ModelPart head;
    public final ModelPart hat;
    public final ModelPart leftArm;
    public final ModelPart leftSleeve;
    public final ModelPart rightArm;
    public final ModelPart rightSleeve;
    public final ModelPart jacket;
    public final ModelPart leftLeg;
    public final ModelPart leftPants;
    public final ModelPart leftBoots;
    public final ModelPart rightLeg;
    public final ModelPart rightBoots;
    public final ModelPart rightPants;

    public ShadayArmorModel(ModelPart root) {
        this.root = root.getChild("root");
        this.waist = this.root.getChild("waist");
        this.body = this.waist.getChild("body");
        this.head = this.body.getChild("head");
        this.hat = this.head.getChild("hat");
        this.leftArm = this.body.getChild("leftArm");
        this.leftSleeve = this.leftArm.getChild("leftSleeve");
        this.rightArm = this.body.getChild("rightArm");
        this.rightSleeve = this.rightArm.getChild("rightSleeve");
        this.jacket = this.body.getChild("jacket");
        this.leftLeg = this.root.getChild("leftLeg");
        this.leftPants = this.leftLeg.getChild("leftPants");
        this.leftBoots = this.leftLeg.getChild("leftBoots");
        this.rightLeg = this.root.getChild("rightLeg");
        this.rightBoots = this.rightLeg.getChild("rightBoots");
        this.rightPants = this.rightLeg.getChild("rightPants");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)24.0f, (float)0.0f));
        PartDefinition waist = root.addOrReplaceChild("waist", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)-12.0f, (float)0.0f));
        PartDefinition body = waist.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)-12.0f, (float)0.0f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(0, 15).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, new CubeDeformation(0.5f)).texOffs(52, 40).addBox(-1.5f, -9.5f, -4.5f, 3.0f, 1.0f, 9.0f, new CubeDeformation(0.0f)).texOffs(62, 50).addBox(-1.5f, -9.5f, 4.5f, 3.0f, 8.0f, 1.0f, new CubeDeformation(0.0f)).texOffs(0, 0).addBox(-1.5f, -9.5f, -5.5f, 3.0f, 4.0f, 1.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition hat_r1 = hat.addOrReplaceChild("hat_r1", CubeListBuilder.create().texOffs(0, 56).mirror().addBox(-5.0f, -9.0f, 0.0f, 7.0f, 11.0f, 0.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(0, 56).addBox(7.0f, -9.0f, 0.0f, 7.0f, 11.0f, 0.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)-4.5f, (float)-5.0f, (float)-1.0f, (float)-0.5672f, (float)0.0f, (float)0.0f));
        PartDefinition leftArm = body.addOrReplaceChild("leftArm", CubeListBuilder.create(), PartPose.offset((float)5.0f, (float)2.0f, (float)0.0f));
        PartDefinition leftSleeve = leftArm.addOrReplaceChild("leftSleeve", CubeListBuilder.create().texOffs(39, 38).addBox(-1.0f, -4.0f, -3.0f, 5.0f, 5.0f, 6.0f, new CubeDeformation(0.25f)).texOffs(30, 7).addBox(-1.0f, -2.5f, -4.0f, 6.0f, 2.0f, 8.0f, new CubeDeformation(0.25f)).texOffs(52, 17).addBox(-1.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f, new CubeDeformation(0.35f)).texOffs(50, 0).addBox(-2.0f, 3.2f, -3.0f, 6.0f, 2.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition leftSleeve_r1 = leftSleeve.addOrReplaceChild("leftSleeve_r1", CubeListBuilder.create().texOffs(32, 17).addBox(0.0f, 0.0f, -3.2f, 1.2f, 1.0f, 6.4f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)5.25f, (float)-2.75f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.7156f));
        PartDefinition rightArm = body.addOrReplaceChild("rightArm", CubeListBuilder.create(), PartPose.offset((float)-5.0f, (float)2.0f, (float)0.0f));
        PartDefinition rightSleeve = rightArm.addOrReplaceChild("rightSleeve", CubeListBuilder.create().texOffs(30, 7).mirror().addBox(-5.0f, -2.5f, -4.0f, 6.0f, 2.0f, 8.0f, new CubeDeformation(0.25f)).mirror(false).texOffs(39, 38).mirror().addBox(-4.0f, -4.0f, -3.0f, 5.0f, 5.0f, 6.0f, new CubeDeformation(0.25f)).mirror(false).texOffs(52, 17).mirror().addBox(-3.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f, new CubeDeformation(0.35f)).mirror(false).texOffs(50, 0).mirror().addBox(-4.0f, 3.2f, -3.0f, 6.0f, 2.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition rightSleeve_r1 = rightSleeve.addOrReplaceChild("rightSleeve_r1", CubeListBuilder.create().texOffs(32, 17).mirror().addBox(-1.2f, 0.0f, -3.2f, 1.2f, 1.0f, 6.4f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)-5.25f, (float)-2.75f, (float)0.0f, (float)0.0f, (float)0.0f, (float)-0.7156f));
        PartDefinition jacket = body.addOrReplaceChild("jacket", CubeListBuilder.create().texOffs(25, 24).addBox(-5.0f, 9.0f, -3.5f, 10.0f, 4.0f, 7.0f, new CubeDeformation(0.0f)).texOffs(24, 17).addBox(-1.5f, 8.0f, -4.0f, 3.0f, 4.0f, 2.0f, new CubeDeformation(0.15f)).texOffs(0, 0).addBox(-5.5f, 0.0f, -4.0f, 11.0f, 7.0f, 8.0f, new CubeDeformation(0.0f)).texOffs(0, 31).addBox(-4.5f, 7.0f, -3.0f, 9.0f, 2.0f, 6.0f, new CubeDeformation(0.1f)), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition jacket_r1 = jacket.addOrReplaceChild("jacket_r1", CubeListBuilder.create().texOffs(48, 52).mirror().addBox(0.0f, 0.0f, -3.0f, 1.0f, 6.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offsetAndRotation((float)-5.0f, (float)8.5f, (float)0.0f, (float)0.0f, (float)0.0f, (float)0.2182f));
        PartDefinition jacket_r2 = jacket.addOrReplaceChild("jacket_r2", CubeListBuilder.create().texOffs(48, 52).addBox(-1.0f, 0.0f, -3.0f, 1.0f, 6.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation((float)5.0f, (float)8.5f, (float)0.0f, (float)0.0f, (float)0.0f, (float)-0.2182f));
        PartDefinition leftLeg = root.addOrReplaceChild("leftLeg", CubeListBuilder.create(), PartPose.offset((float)1.9f, (float)-12.0f, (float)0.0f));
        PartDefinition leftPants = leftLeg.addOrReplaceChild("leftPants", CubeListBuilder.create().texOffs(16, 44).addBox(-2.0f, -1.0f, -2.0f, 4.0f, 8.0f, 4.0f, new CubeDeformation(0.35f)), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        PartDefinition leftBoots = leftLeg.addOrReplaceChild("leftBoots", CubeListBuilder.create().texOffs(16, 56).addBox(-0.1f, -6.3f, -2.0f, 4.0f, 5.0f, 4.0f, new CubeDeformation(0.4f)).texOffs(30, 0).addBox(-0.6f, -7.0f, -3.0f, 5.0f, 3.0f, 4.0f, new CubeDeformation(0.0f)).texOffs(32, 49).addBox(-0.6f, -2.75f, -3.5f, 5.0f, 3.0f, 6.0f, new CubeDeformation(0.0f)), PartPose.offset((float)-1.9f, (float)12.0f, (float)0.0f));
        PartDefinition rightLeg = root.addOrReplaceChild("rightLeg", CubeListBuilder.create(), PartPose.offset((float)-1.9f, (float)-12.0f, (float)0.0f));
        PartDefinition rightBoots = rightLeg.addOrReplaceChild("rightBoots", CubeListBuilder.create().texOffs(16, 56).mirror().addBox(-3.9f, -6.3f, -2.0f, 4.0f, 5.0f, 4.0f, new CubeDeformation(0.4f)).mirror(false).texOffs(32, 49).mirror().addBox(-4.4f, -2.75f, -3.5f, 5.0f, 3.0f, 6.0f, new CubeDeformation(0.0f)).mirror(false).texOffs(30, 0).mirror().addBox(-4.4f, -7.0f, -3.0f, 5.0f, 3.0f, 4.0f, new CubeDeformation(0.0f)).mirror(false), PartPose.offset((float)1.9f, (float)12.0f, (float)0.0f));
        PartDefinition rightPants = rightLeg.addOrReplaceChild("rightPants", CubeListBuilder.create().texOffs(16, 44).mirror().addBox(-2.0f, -1.0f, -2.0f, 4.0f, 8.0f, 4.0f, new CubeDeformation(0.35f)).mirror(false), PartPose.offset((float)0.0f, (float)0.0f, (float)0.0f));
        return LayerDefinition.create((MeshDefinition)meshdefinition, (int)128, (int)128);
    }

    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int packedColor) {
        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, packedColor);
    }

    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }
}


