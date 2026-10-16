package com.example.maceswap.mixin;

import com.example.maceswap.util.GlidingFallTracker;
import com.example.maceswap.util.SwapHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
    @Final
    private ClientPlayNetworkHandler networkHandler;

    @Shadow
    public abstract void clickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player);

    // Tracks the hotbar slot holding the swapped Elytra while the Chestplate is equipped
    @Unique
    private int maceswap$elytraHotbarSlot = -1;

    // Tracks the original weapon slot (Sword/Axe) during a same-tick Attribute Swap
    @Unique
    private int maceswap$originalWeaponSlot = -1;

    // Number of ticks to monitor for a Wind Burst bounce after attacking
    @Unique
    private int maceswap$windBurstMonitorTicks = 0;

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void maceswap$clientBeforeAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        this.maceswap$originalWeaponSlot = -1;

        if (!SwapHelper.canPerformMaceSwap(player)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int chestplateSlot = SwapHelper.findChestplateHotbarSlot(inventory);
        if (chestplateSlot == -1) {
            return;
        }

        int maceSlot = SwapHelper.findMaceHotbarSlot(inventory);
        boolean holdingMace = player.getMainHandStack().isOf(Items.MACE);
        if (!holdingMace && maceSlot == -1) {
            return;
        }

        // 1. Preserve accumulated gliding fall distance
        float effectiveFall = ((GlidingFallTracker) player).maceswap$getEffectiveFallDistance();
        if (effectiveFall > 1.5F) {
            player.fallDistance = effectiveFall;
        }

        // 2. Swap Elytra (Chest Armor Slot 6) -> Chestplate (Hotbar Slot)
        this.clickSlot(
                player.playerScreenHandler.syncId,
                CHEST_ARMOR_SLOT_INDEX,
                chestplateSlot,
                SlotActionType.SWAP,
                player
        );
        this.maceswap$elytraHotbarSlot = chestplateSlot;

        // 3. Cancel gliding state so the server registers a falling smash attack
        player.stopGliding();

        // 4. Attribute Swap: If holding a Sword/Axe, switch active slot to the Mace BEFORE the attack packet is sent
        if (!holdingMace && maceSlot != -1) {
            this.maceswap$originalWeaponSlot = inventory.selectedSlot;
            inventory.selectedSlot = maceSlot;
            this.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));
        }

        // Monitor the next 8 ticks (0.4s) for Wind Burst upward bounce velocity
        this.maceswap$windBurstMonitorTicks = 8;

        player.sendMessage(
                Text.literal("⚡ Attribute Mace Smash! (Fall: " + String.format("%.1f", effectiveFall) + "m)")
                        .formatted(Formatting.GOLD),
                true
        );
    }

    @Inject(method = "attackEntity", at = @At("RETURN"))
    private void maceswap$clientAfterAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        // 5. Immediately swap back from Mace -> Original Weapon (Sword/Axe) after the attack packet is dispatched
        if (this.maceswap$originalWeaponSlot != -1) {
            PlayerInventory inventory = player.getInventory();
            inventory.selectedSlot = this.maceswap$originalWeaponSlot;
            this.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(this.maceswap$originalWeaponSlot));
            this.maceswap$originalWeaponSlot = -1;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void maceswap$onTick(CallbackInfo ci) {
        if (this.maceswap$elytraHotbarSlot == -1 || this.maceswap$windBurstMonitorTicks <= 0) {
            return;
        }

        PlayerEntity player = this.client.player;
        if (player == null) {
            this.maceswap$elytraHotbarSlot = -1;
            this.maceswap$windBurstMonitorTicks = 0;
            return;
        }

        this.maceswap$windBurstMonitorTicks--;

        // 6. Check if the player got Wind Bursted (upward velocity spike after Mace smash)
        // Wind Burst or smash bounce pushes player.getVelocity().y significantly above 0.15
        double verticalVelocity = player.getVelocity().y;
        boolean windBursted = verticalVelocity > 0.15D;

        if (windBursted && player.currentScreenHandler == player.playerScreenHandler) {
            // Re-equip the Elytra from the hotbar slot back to Chest Armor Slot 6
            if (SwapHelper.isGlider(player.getInventory().getStack(this.maceswap$elytraHotbarSlot))) {
                this.clickSlot(
                        player.playerScreenHandler.syncId,
                        CHEST_ARMOR_SLOT_INDEX,
                        this.maceswap$elytraHotbarSlot,
                        SlotActionType.SWAP,
                        player
                );
                player.sendMessage(
                        Text.literal("🪽 Wind Burst Detected — Elytra Re-Equipped!")
                                .formatted(Formatting.AQUA),
                        true
                );
            }
            ((GlidingFallTracker) player).maceswap$resetTrackedFallDistance();
            this.maceswap$elytraHotbarSlot = -1;
            this.maceswap$windBurstMonitorTicks = 0;
        } else if (this.maceswap$windBurstMonitorTicks == 0) {
            // Window expired without a Wind Burst bounce: keep the Chestplate equipped for ground combat!
            ((GlidingFallTracker) player).maceswap$resetTrackedFallDistance();
            this.maceswap$elytraHotbarSlot = -1;
        }
    }
}
