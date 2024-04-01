/*
 * All Rights Reserved (c) MoriyaShiine
 */

package moriyashiine.heartymeals.common.component.entity;

import com.nhoryzon.mc.farmersdelight.registry.EffectsRegistry;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.CommonTickingComponent;
import moriyashiine.heartymeals.common.HeartyMeals;
import moriyashiine.heartymeals.common.ModConfig;
import moriyashiine.heartymeals.common.init.ModEntityComponents;
import moriyashiine.heartymeals.common.init.ModStatusEffects;
import moriyashiine.heartymeals.common.init.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import vectorwing.farmersdelight.common.registry.ModEffects;

public class FoodHealingComponent implements AutoSyncedComponent, CommonTickingComponent {
	private final PlayerEntity obj;
	private boolean fromSaturation = false;
	private int healAmount = 0, ticksPerHeal = 0;
	private int healTicks = 0;
	private int amountHealed = 0;

	public FoodHealingComponent(PlayerEntity obj) {
		this.obj = obj;
	}

	@Override
	public void readFromNbt(NbtCompound tag) {
		fromSaturation = tag.getBoolean("FromSaturation");
		healAmount = tag.getInt("HealAmount");
		ticksPerHeal = tag.getInt("TicksPerHeal");
		healTicks = tag.getInt("HealTicks");
		amountHealed = tag.getInt("AmountHealed");
	}

	@Override
	public void writeToNbt(NbtCompound tag) {
		tag.putBoolean("FromSaturation", fromSaturation);
		tag.putInt("HealAmount", healAmount);
		tag.putInt("TicksPerHeal", ticksPerHeal);
		tag.putInt("HealTicks", healTicks);
		tag.putInt("AmountHealed", amountHealed);
	}

	@Override
	public void tick() {
		tickFoodHealing();
		tickCampfire();
		tickNourishing();
	}

	public void sync() {
		ModEntityComponents.FOOD_HEALING.sync(obj);
	}

	public void setFromSaturation(boolean fromSaturation) {
		this.fromSaturation = fromSaturation;
	}

	public int getHealAmount() {
		return healAmount;
	}

	public int getAmountHealed() {
		return amountHealed;
	}

	public int getMaximumHealTicks() {
		return healAmount * ticksPerHeal;
	}

	public boolean canEat() {
		return healAmount == 0;
	}

	public void startHealing(int food, float saturationModifier) {
		if (fromSaturation) {
			fromSaturation = false;
			int duration = obj.getStatusEffect(StatusEffects.SATURATION).getDuration();
			if (duration == StatusEffectInstance.INFINITE) {
				duration = obj.age;
			}
			if (duration % 2 == 0) {
				obj.heal(food);
			}
		} else if (food > 0) {
			healAmount = food;
			ticksPerHeal = getTicksPerHeal(saturationModifier);
			for (Item item : Registries.ITEM) {
				if (item.isFood()) {
					obj.getItemCooldownManager().set(item, getMaximumHealTicks());
				}
			}
		}
	}

	public static int getMaximumHealTicks(FoodComponent foodComponent) {
		return foodComponent.getHunger() * getTicksPerHeal(foodComponent.getSaturationModifier());
	}

	public static int getTicksPerHeal(float saturationModifier) {
		return (int) MathHelper.clamp(20 * (1F / saturationModifier), 0, 60);
	}

	private void tickFoodHealing() {
		if (healAmount > 0) {
			healTicks++;
			if (healTicks % ticksPerHeal == 0) {
				if (!obj.hasStatusEffect(StatusEffects.HUNGER) && obj.getWorld().getGameRules().getBoolean(GameRules.NATURAL_REGENERATION)) {
					obj.heal(1);
				}
				amountHealed++;
			}
			if (healTicks == getMaximumHealTicks()) {
				healAmount = ticksPerHeal = healTicks = amountHealed = 0;
			}
		}
	}

	private void tickCampfire() {
		if (obj.age % 20 == 0) {
			if (ModConfig.campfireHealing) {
				if (BlockPos.findClosest(obj.getBlockPos(), 5, 5, foundPos -> {
					BlockState state = obj.getWorld().getBlockState(foundPos);
					if (state.isIn(ModTags.BlockTags.COZY_SOURCES)) {
						return !state.contains(Properties.LIT) || state.get(Properties.LIT);
					}
					return false;
				}).isPresent()) {
					obj.addStatusEffect(new StatusEffectInstance(ModStatusEffects.COZY, StatusEffectInstance.INFINITE, 0, true, false, true));
				} else {
					obj.removeStatusEffect(ModStatusEffects.COZY);
				}
			} else {
				obj.removeStatusEffect(ModStatusEffects.COZY);
			}
		}
	}

	private void tickNourishing() {
		if (HeartyMeals.farmersDelightLoaded) {
			StatusEffect effect = HeartyMeals.farmersDelightRefabricatedLoaded ? ModEffects.NOURISHMENT.get() : EffectsRegistry.NOURISHMENT.get();
			if (obj.hasStatusEffect(effect)) {
				int duration = obj.getStatusEffect(effect).getDuration();
				if (duration == StatusEffectInstance.INFINITE) {
					duration = obj.age;
				}
				if (duration % 200 == 0) {
					obj.heal(1);
				}
			}
		}
	}
}
