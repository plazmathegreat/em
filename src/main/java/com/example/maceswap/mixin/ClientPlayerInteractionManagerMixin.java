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

    @Unique
    private int maceswap$elytraHotbarSlot = -1;

    @Unique
    private int maceswap$originalWeaponSlot = -1;

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

        float effectiveFall = ((GlidingFallTracker) player).maceswap$getEffectiveFallDistance();
        if (effectiveFall > 1.5F) {
            player.fallDistance = effectiveFall;
        }

        // 1. Swap Elytra (Chest Armor Slot 6) -> Chestplate (Hotbar Slot)
        this.clickSlot(
                player.playerScreenHandler.syncId,
                CHEST_ARMOR_SLOT_INDEX,
                chestplateSlot,
                SlotActionType.SWAP,
                player
        );
        this.maceswap$elytraHotbarSlot = chestplateSlot;

        // 2. Cancel gliding state
        player.stopGliding();

        // 3. Attribute Swap: Switch from Sword/Axe -> Mace before the attack packet is sent
        if (!holdingMace && maceSlot != -1) {
            this.maceswap$originalWeaponSlot = inventory.getSelectedSlot();
            inventory.setSelectedSlot(maceSlot);
            this.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));
        }

        // 4. Monitor next 8 ticks for Wind Burst upward bounce velocity
        this
