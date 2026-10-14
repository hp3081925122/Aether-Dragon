package com.aetherdragon.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.ArmorType;

public final class ShadayArmorModel extends HumanoidModel<HumanoidRenderState> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(Identifier.fromNamespaceAndPath("aether_dragon", "modelshaday_armor"), "main");
    public static final ModelLayerLocation BABY_LAYER_LOCATION = new ModelLayerLocation(Identifier.fromNamespaceAndPath("aether_dragon", "modelshaday_armor"), "baby");

    public ShadayArmorModel(ModelPart root, ArmorType type) {
        super(root);
        this.head.visible = type == ArmorType.HELMET;
        this.hat.visible = type == ArmorType.HELMET;
        this.body.visible = type == ArmorType.CHESTPLATE;
        this.rightArm.visible = type == ArmorType.CHESTPLATE;
        this.leftArm.visible = type == ArmorType.CHESTPLATE;
        this.rightLeg.visible = type == ArmorType.LEGGINGS;
        this.leftLeg.visible = type == ArmorType.LEGGINGS;
    }

    public static LayerDefinition createBodyLayer(boolean baby) {
        MeshDefinition original = createShadayMesh();
        PartDefinition oldRoot = original.getRoot().getChild("root");
        PartDefinition oldBody = oldRoot.getChild("waist").getChild("body");
        MeshDefinition standard = new MeshDefinition();
        PartDefinition root = standard.getRoot();
        root.addOrReplaceChild("head", oldBody.getChild("head"));
        root.addOrReplaceChild("body", oldBody.getChild("jacket"));
        root.addOrReplaceChild("left_arm", oldBody.getChild("leftArm"));
        root.addOrReplaceChild("right_arm", oldBody.getChild("rightArm"));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
        leftLeg.addOrReplaceChild("leftPants", oldRoot.getChild("leftLeg").getChild("leftPants"));
        leftLeg.addOrReplaceChild("leftBoots", oldRoot.getChild("leftLeg").getChild("leftBoots"));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        rightLeg.addOrReplaceChild("rightPants", oldRoot.getChild("rightLeg").getChild("rightPants"));
        rightLeg.addOrReplaceChild("rightBoots", oldRoot.getChild("rightLeg").getChild("rightBoots"));
        LayerDefinition layer = LayerDefinition.create(standard, 128, 128);
        return baby ? layer.apply(HumanoidModel.BABY_TRANSFORMER) : layer;
    }

    private static MeshDefinition createShadayMesh() {
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
        return meshdefinition;
    }
}
