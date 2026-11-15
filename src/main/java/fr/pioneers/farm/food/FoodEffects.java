package fr.pioneers.farm.food;

import fr.pioneers.Pioneers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Effets de la cuisine cote serveur.
 * <ul>
 * <li>Repu : la fatigue alimentaire est remise a zero a chaque tick (la faim ne baisse plus).</li>
 * <li>Alcool : chaque boisson ajoute des unites (donnees du joueur {@code pioneers_alcohol}), une unite s'evapore toutes les 30 s. Ivresse I
 * des 2 unites (chance, legere lenteur), II des 5 (nausee par vagues), III des 9 (nausee forte, voile noir, titubements).</li>
 * <li>Cafe : boire un cafe sous Cafeine compte un exces ({@code pioneers_caffeine}) ; au troisieme : Nervosite et nausee breve. Cafeine :
 * vitesse, cadence de frappe et de minage (+20 %).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class FoodEffects {

    public static final String ALCOHOL = "pioneers_alcohol", CAFFEINE = "pioneers_caffeine";

    private FoodEffects() {
    }

    public static float alcohol(Player player) {
        return player.getPersistentData().getFloat(ALCOHOL);
    }

    public static int drunkLevel(float alcohol) {
        return alcohol >= 9 ? 3 : alcohol >= 5 ? 2 : alcohol >= 2 ? 1 : 0;
    }

    /** Une boisson alcoolisee bue. */
    public static void drink(Player player, int units) {
        if (units <= 0) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putFloat(ALCOHOL, Math.min(20F, data.getFloat(ALCOHOL) + units));
        refreshDrunk(player);
    }

    /** Un cafe bu : exces si deja sous cafeine. */
    public static void coffee(Player player) {
        CompoundTag data = player.getPersistentData();
        if (player.hasEffect(PiEffects.CAFFEINE.get())) {
            int n = data.getInt(CAFFEINE) + 1;
            data.putInt(CAFFEINE, n);
            if (n >= 2) {
                player.addEffect(new MobEffectInstance(PiEffects.JITTERS.get(), 1200, 0));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
            }
        }
    }

    private static void refreshDrunk(Player player) {
        int level = drunkLevel(alcohol(player));
        if (level > 0) {
            player.addEffect(new MobEffectInstance(PiEffects.TIPSY.get(), 60, level - 1, true, true, true));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide) {
            return;
        }
        if (player.hasEffect(PiEffects.WELL_FED.get()) && player.getFoodData().getExhaustionLevel() > 0) {
            player.getFoodData().setExhaustion(0);
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!player.hasEffect(PiEffects.CAFFEINE.get()) && data.getInt(CAFFEINE) > 0) {
            data.putInt(CAFFEINE, 0);
        }
        float alcohol = data.getFloat(ALCOHOL);
        if (alcohol <= 0) {
            return;
        }
        alcohol = Math.max(0, alcohol - 1F / 30F);
        data.putFloat(ALCOHOL, alcohol);
        int level = drunkLevel(alcohol);
        if (level == 0) {
            return;
        }
        refreshDrunk(player);
        RandomSource random = player.getRandom();
        if (level >= 2 && random.nextInt(level == 3 ? 6 : 14) == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, level == 3 ? 200 : 120, 0, true, false));
        }
        if (level == 3) {
            if (random.nextInt(12) == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false));
            }
            if (player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() > 0.001 && random.nextInt(3) == 0
                && player instanceof ServerPlayer server) {
                //Titubement : petite poussee de cote
                Vec3 side = Vec3.directionFromRotation(0, player.getYRot() + (random.nextBoolean() ? 90 : -90)).scale(0.25);
                server.push(side.x, 0.05, side.z);
                server.hurtMarked = true;
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity().hasEffect(PiEffects.CAFFEINE.get())) {
            event.setNewSpeed(event.getNewSpeed() * 1.2F);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        //La mort efface l'ivresse (les donnees persistantes ne suivent pas le joueur, sauf ce qu'on recopie)
        event.getEntity().getPersistentData().remove(ALCOHOL);
    }
}
