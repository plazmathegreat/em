package com.example.maceswap.mixin;

import com.example.maceswap.util.GlidingFallTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityGlideMixin extends Entity implements GlidingFallTracker {

    @Unique
    private float maceswap$trackedGlideFallDistance = 0.0F;

    public LivingEntityGlideMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void maceswap$trackGlideFallDistance(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self.isGliding()) {
            double dy = self.getY() - self.lastY;
            if (dy < 0.0D) {
                this.maceswap$trackedGlideFallDistance += (float) (-dy);
            }
            if (this.fallDistance > this.maceswap$trackedGlideFallDistance) {
                this.maceswap$trackedGlideFallDistance = (float) this.fallDistance;
            }
        } else if (this.isOnGround() || this.isTouchingWater() || self.isClimbing()) {
            this.maceswap$trackedGlideFallDistance = 0.0F;
        }
    }

    @Override
    public float maceswap$getEffectiveFallDistance() {
        return Math.max((float) this.fallDistance, this.maceswap$trackedGlideFallDistance);
    }

    @Override
    public void maceswap$resetTrackedFallDistance() {
        this.maceswap$trackedGlideFallDistance = 0.0F;
    }
}
