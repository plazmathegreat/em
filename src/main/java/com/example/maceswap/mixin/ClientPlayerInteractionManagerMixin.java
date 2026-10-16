package com.example.maceswap.mixin;

import com.example.maceswap.util.GlidingFallTracker;
import com.example.maceswap.util.SwapHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Unique
    private static final int CHEST_ARMOR_SLOT_INDEX = 6;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    public abstract void clickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player);

    @Unique
    private int maceswap$clientSwappedSlot = -1;

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void maceswap$clientBeforeAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        this.maceswap$clientSwappedSlot = -1;

        if (this.client.isInSingleplayer()) {
            return;
        }

        if (!SwapHelper.canPerformMaceSwap(player)) {
            return;
        }

        int chestplateSlot = SwapHelper.findChestplateHotbarSlot(player.getInventory());
        if (chestplateSlot == -1) {
            return;
        }

        float effectiveFall = ((GlidingFallTracker) player).maceswap$getEffectiveFallDistance();
        if (effectiveFall > 1.5F) {
            player.fallDistance = effectiveFall;
        }

        this.clickSlot(
                player.playerScreenHandler.syncId,
                CHEST_ARMOR_SLOT_INDEX,
                chestplateSlot,
                SlotActionType.SWAP,
                player
        );

        player.stopFallFlying();
        this.maceswap$clientSwappedSlot = chestplateSlot;
    }

    @Inject(method = "attackEntity", at = @At("RETURN"))
    private void maceswap$clientAfterAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (this.maceswap$clientSwappedSlot != -1) {
            this.clickSlot(
                    player.playerScreenHandler.syncId,
                    CHEST_ARMOR_SLOT_INDEX,
                    this.maceswap$clientSwappedSlot,
                    SlotActionType.SWAP,
                    player
            );
            ((GlidingFallTracker) player).maceswap$resetTrackedFallDistance();
            this.maceswap$clientSwappedSlot = -1;
        }
    }
}
