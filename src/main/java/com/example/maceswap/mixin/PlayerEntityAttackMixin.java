package com.example.maceswap.mixin;

import com.example.maceswap.util.GlidingFallTracker;
import com.example.maceswap.util.SwapHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAttackMixin extends LivingEntity {

    @Unique
    private int maceswap$swappedHotbarSlot = -1;

    protected PlayerEntityAttackMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void maceswap$beforeAttack(Entity target, CallbackInfo ci) {
        this.maceswap$swappedHotbarSlot = -1;
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (!target.isAttackable() || target.handleAttack(player)) {
            return;
        }

        if (!SwapHelper.canPerformMaceSwap(player)) {
            return;
        }

        int chestplateSlot = SwapHelper.findChestplateHotbarSlot(player.getInventory());
        if (chestplateSlot == -1) {
            return;
        }

        float effectiveFall = ((GlidingFallTracker) this).maceswap$getEffectiveFallDistance();
        if (effectiveFall > 1.5F) {
            this.fallDistance = effectiveFall;
        }

        SwapHelper.swapChestWithHotbar(player, chestplateSlot);
        this.maceswap$swappedHotbarSlot = chestplateSlot;

        player.stopFallFlying();
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void maceswap$afterAttack(Entity target, CallbackInfo ci) {
        if (this.maceswap$swappedHotbarSlot != -1) {
            PlayerEntity player = (PlayerEntity) (Object) this;
            SwapHelper.swapChestWithHotbar(player, this.maceswap$swappedHotbarSlot);
            ((GlidingFallTracker) this).maceswap$resetTrackedFallDistance();
            this.maceswap$swappedHotbarSlot = -1;
        }
    }
}
