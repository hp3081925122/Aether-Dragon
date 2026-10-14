package com.aetherdragon.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class NagaEntity extends RebornMobEntity {
    private static final EntityDataAccessor<Long> ANIMATION_START = SynchedEntityData.defineId(NagaEntity.class, EntityDataSerializers.LONG);
    private final NagaPart[] parts;
    private int hitboxSkill = -1;
    // 客户端：转身时尾部的拖拽弯曲角度（度），平滑跟随转身速度
    private float tailBend;
    private float tailBendO;

    public NagaEntity(EntityType<? extends RebornMobEntity> type, Level level) {
        super(type, level);
        this.parts = NagaHitboxes.createParts(this);
        setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
        updateParts();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ANIMATION_START, 0L);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (this.parts != null) {
            for (int i = 0; i < this.parts.length; i++) {
                this.parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public NagaPart[] getParts() {
        return this.parts;
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        if (this.parts != null) {
            updateParts();
        }
    }

    @Override
    public void tick() {
        super.tick();
        int skill = getNagaSkill();
        if (!level().isClientSide && skill != this.hitboxSkill) {
            this.hitboxSkill = skill;
            this.entityData.set(ANIMATION_START, level().getGameTime() - getNagaCombat().getSkillTicks());
        }
        updateParts();
        if (level().isClientSide) {
            // 尾部拖拽：转得越快甩得越开，停止转身后缓慢回正
            float turn = Mth.wrapDegrees(this.yBodyRot - this.yBodyRotO);
            float target = Mth.clamp(turn * 3.0F, -40.0F, 40.0F);
            this.tailBendO = this.tailBend;
            this.tailBend += (target - this.tailBend) * 0.12F;
        }
    }

    // 尾部拖拽弯曲角度（度），按帧插值
    public float getTailBend(float partialTicks) {
        return Mth.lerp(partialTicks, this.tailBendO, this.tailBend);
    }

    private void updateParts() {
        double time = getNagaSkill() == 0 ? this.tickCount / 20.0D
                : Math.max(0L, level().getGameTime() - this.entityData.get(ANIMATION_START)) / 20.0D;
        NagaHitboxes.update(this, this.parts, time);
    }
}
