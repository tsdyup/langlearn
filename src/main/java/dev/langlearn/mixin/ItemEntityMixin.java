package dev.langlearn.mixin;

import dev.langlearn.LangLearn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects ground item pickups. We remember the item stack before the player touches it,
 * and afterwards compare to see how many items went into the player's inventory.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Unique private ItemStack langlearn$before = ItemStack.EMPTY;
	@Unique private int langlearn$beforeCount;
	@Unique private boolean langlearn$wasRemoved;

	@Inject(method = "playerTouch", at = @At("HEAD"))
	private void langlearn$beforeTouch(Player player, CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		ItemStack stack = self.getItem();
		this.langlearn$before = stack.copy();
		this.langlearn$beforeCount = stack.getCount();
		this.langlearn$wasRemoved = self.isRemoved();
	}

	@Inject(method = "playerTouch", at = @At("RETURN"))
	private void langlearn$afterTouch(Player player, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer) || this.langlearn$wasRemoved) return;
		ItemEntity self = (ItemEntity) (Object) this;
		// when the whole stack is picked up, vanilla discards the entity and restores the count,
		// so "removed" means everything was taken
		int picked = self.isRemoved()
				? this.langlearn$beforeCount
				: this.langlearn$beforeCount - self.getItem().getCount();
		if (picked > 0) {
			LangLearn.onPickup(serverPlayer, this.langlearn$before, picked);
		}
		this.langlearn$before = ItemStack.EMPTY;
	}
}
