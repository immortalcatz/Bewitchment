package com.bewitchment.common.core.event;

import com.bewitchment.api.capability.EnumTransformationType;
import com.bewitchment.api.capability.IBloodReserve;
import com.bewitchment.api.capability.ITransformationData;
import com.bewitchment.api.event.HotbarActionCollectionEvent;
import com.bewitchment.api.event.HotbarActionTriggeredEvent;
import com.bewitchment.api.event.TransformationModifiedEvent;
import com.bewitchment.api.helper.RayTraceHelper;
import com.bewitchment.common.abilities.ModAbilities;
import com.bewitchment.common.core.capability.transformation.CapabilityTransformationData;
import com.bewitchment.common.core.capability.transformation.blood.CapabilityBloodReserve;
import com.bewitchment.common.core.helper.TransformationHelper;
import com.bewitchment.common.potion.ModPotions;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;

import java.util.UUID;

public class VampireAbilityHandler {

	public static final DamageSource SUN_DAMAGE = new DamageSource("sun_on_vampire").setDamageBypassesArmor().setDamageIsAbsolute().setFireDamage();

	public static final UUID ATTACK_SPEED_MODIFIER_UUID = UUID.fromString("c73f6d26-65ed-4ba5-ada8-9a96f8712424");

	/**
	 * Modifies damage depending on the type. Fire and explosion make it 150%of the original,
	 * all the other types make it 10% of the original provided there's blood in the pool
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public void onDamageReceived(LivingHurtEvent evt) {
		if (!evt.getEntity().world.isRemote && evt.getEntityLiving() instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) evt.getEntityLiving();
			ITransformationData data = player.getCapability(CapabilityTransformationData.CAPABILITY, null);
			if (data.getType() == EnumTransformationType.VAMPIRE) {
				if (evt.getSource() == SUN_DAMAGE)
					return;
				if (evt.getSource().isFireDamage() || evt.getSource().isExplosion()) {
					evt.setCanceled(false);
					evt.setAmount(evt.getAmount() * 1.5f);
				} else if (data.getBlood() > 0) { // Don't mitigate damage when there is no blood in the pool
					evt.setAmount(evt.getAmount() * 0.1f);
					if (evt.getAmount() > 5f) {
						evt.setAmount(5f);
					}
				}
			}
		}
	}

	@SubscribeEvent
	public void checkSun(PlayerTickEvent evt) {
		if (evt.side.isServer()) {
			ITransformationData data = evt.player.getCapability(CapabilityTransformationData.CAPABILITY, null);
			if (data.getType() == EnumTransformationType.VAMPIRE && evt.player.world.getTotalWorldTime() % 40 == 0) {
				if (evt.player.world.canBlockSeeSky(evt.player.getPosition()) && evt.player.world.isDaytime() && !evt.player.world.isRainingAt(evt.player.getPosition())) {
					if (data.getLevel() < 5 || !data.addVampireBlood(-11 + data.getLevel())) {
						evt.player.attackEntityFrom(SUN_DAMAGE, 11 - data.getLevel());
					}
				}
			}
		}
	}

	@SubscribeEvent
	public void attachAbilities(HotbarActionCollectionEvent evt) {
		ITransformationData data = evt.player.getCapability(CapabilityTransformationData.CAPABILITY, null);
		if (data.getType() == EnumTransformationType.VAMPIRE) {
			evt.getList().add(ModAbilities.DRAIN_BLOOD);
			if (data.getLevel() > 5) {
				evt.getList().add(ModAbilities.NIGHT_VISION);
			} else {
				data.setNightVision(false);
			}
		} else {
			data.setNightVision(false);
		}

	}

	@SubscribeEvent
	public void onAbilityToggled(HotbarActionTriggeredEvent evt) {
		ITransformationData data = evt.player.getCapability(CapabilityTransformationData.CAPABILITY, null);
		if (evt.action == ModAbilities.NIGHT_VISION) {
			data.setNightVision(!data.isNightVisionActive());
		} else if (evt.action == ModAbilities.DRAIN_BLOOD) {
			RayTraceResult rt = RayTraceHelper.rayTraceResult(evt.player, RayTraceHelper.fromLookVec(evt.player, evt.player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue()), true, true);
			if (rt != null && rt.typeOfHit == Type.ENTITY) {
				if (rt.entityHit instanceof EntityLivingBase) {
					EntityLivingBase entity = (EntityLivingBase) rt.entityHit;
					IBloodReserve br = entity.getCapability(CapabilityBloodReserve.CAPABILITY, null);
					if (br.getBlood() > 0 && br.getMaxBlood() > 0) {
						br.setBlood(br.getBlood() - 20);
						TransformationHelper.addVampireBlood(evt.player, 20);
						float amount = br.getPercentFilled();
						if (amount > 0 && amount < 0.4f) {
							entity.addPotionEffect(new PotionEffect(ModPotions.bloodDrained, 200, 0));
						}
					}
				}
			}
		}
	}

	@SubscribeEvent
	public void abilityHandler(PlayerTickEvent evt) {
		if (evt.phase == Phase.START) {
			PotionEffect nv = evt.player.getActivePotionEffect(MobEffects.NIGHT_VISION);
			if ((nv == null || nv.getDuration() <= 220) && evt.player.getCapability(CapabilityTransformationData.CAPABILITY, null).isNightVisionActive()) {
				evt.player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 300, 0, true, false));
			}
		}
	}

	@SubscribeEvent
	public void refreshModifiers(TransformationModifiedEvent evt) {
		ITransformationData data = evt.getEntityPlayer().getCapability(CapabilityTransformationData.CAPABILITY, null);
		IAttributeInstance attack_speed = evt.getEntityPlayer().getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
		AttributeModifier modifier = attack_speed.getModifier(ATTACK_SPEED_MODIFIER_UUID);
		if (modifier != null) {
			attack_speed.removeModifier(modifier);
		}
		if (data.getType() == EnumTransformationType.VAMPIRE) {
			attack_speed.applyModifier(new AttributeModifier(ATTACK_SPEED_MODIFIER_UUID, "Vampire Atk Speed", evt.level / 10, 0));
		}
	}

}
