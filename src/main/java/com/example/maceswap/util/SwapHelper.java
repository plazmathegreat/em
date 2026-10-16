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

    /**
     * Checks if the player can perform the Elytra -> Chestplate + Attribute Mace swap:
     * 1. Currently gliding with an Elytra in the Chest slot
     * 2. Has a Chestplate in the hotbar (slots 0-8)
     * 3. Either holding a Mace OR has a Mace in the hotbar (slots 0-8) to attribute-swap from a Sword/Axe
     */
    public static boolean canPerformMaceSwap(PlayerEntity player) {
        if (player == null || player.isSpectator()) {
            return false;
        }

        if (!player.isGliding()) {
            return false;
        }

        ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!isGlider(chestStack)) {
            return false;
        }

        // Must either hold a Mace OR have a Mace in hotbar for attribute swapping
        ItemStack mainHand = player.getMainHandStack();
        return mainHand.isOf(Items.MACE) || findMaceHotbarSlot(player.getInventory()) != -1;
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

    /**
     * Finds the first hotbar slot (0-8) containing a Chestplate.
     */
    public static int findChestplateHotbarSlot(PlayerInventory inventory) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (isChestplate(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the first hotbar slot (0-8) containing a Mace.
     */
    public static int findMaceHotbarSlot(PlayerInventory inventory) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(Items.MACE)) {
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
