package com.example.maceswap.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

public final class SwapHelper {

    private SwapHelper() {}

    public static boolean canPerformMaceSwap(PlayerEntity player) {
        if (player == null || player.isSpectator()) {
            return false;
        }

        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isOf(Items.MACE)) {
            return false;
        }

        if (!player.isGliding()) {
            return false;
        }

        ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
        return isGlider(chestStack);
    }

    public static boolean isGlider(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.isOf(Items.ELYTRA) || stack.contains(DataComponentTypes.GLIDER);
    }

    public static boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || isGlider(stack)) {
            return false;
        }
        if (stack.isIn(ItemTags.CHEST_ARMOR)) {
            return true;
        }
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    public static int findChestplateHotbarSlot(PlayerInventory inventory) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (isChestplate(stack)) {
                return i;
            }
        }
        return -1;
    }

    public static void swapChestWithHotbar(PlayerEntity player, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack hotbarStack = inventory.getStack(hotbarSlot);

        player.equipStack(EquipmentSlot.CHEST, hotbarStack);
        inventory.setStack(hotbarSlot, chestStack);

        inventory.markDirty();
        if (!player.getEntityWorld().isClient()) {
            player.playerScreenHandler.sendContentUpdates();
        }
    }
}
