package fr.pioneers.tech.nuclear;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.materials.MetalArmor;
import fr.pioneers.core.registries.PiNuclear;
import java.util.Locale;
import fr.pioneers.core.profile.PioneerApi;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Radiation des joueurs (0.17.0), toutes les secondes : debit = sources de la dimension ({@link RadiationData}) + objets radioactifs de
 * l'inventaire (tags {@code pioneers:radioactive/low|medium|high} : 0,05 / 0,5 / 3 Sv/h par pile pleine, au prorata) ; combinaison complete :
 * -95 %. Dose cumulee (donnees du joueur, perdue a la mort), -1 % par minute. Effets : > 1 Sv nausee, > 2 Sv faiblesse et faim,
 * > 4 Sv fletrissement, > 6 Sv fletrissement II. Dosimetre dans la barre rapide : debit et dose dans la barre d'action, crepitement.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class RadiationHandler {

    private RadiationHandler() {
    }

    public static final TagKey<Item> LOW = ItemTags.create(Pioneers.rl("radioactive/low"));
    public static final TagKey<Item> MEDIUM = ItemTags.create(Pioneers.rl("radioactive/medium"));
    public static final TagKey<Item> HIGH = ItemTags.create(Pioneers.rl("radioactive/high"));
    private static final String DOSE = "pioneers_rad_dose", RATE = "pioneers_rad_rate";
    /** Decroissance de la dose par seconde (1 % par minute). */
    private static final double DOSE_DECAY = Math.pow(0.99, 1 / 60.0);

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        CompoundTag data = player.getPersistentData();
        double rate = RadiationData.get(level).rate(player.getEyePosition(), level.getGameTime()) + inventoryRate(player.getInventory());
        if (HazmatArmorItem.fullSet(player)) {
            rate *= HazmatArmorItem.LEAK;
        }
        //1.0 : l'armure d'or reflechit une part des rayonnements
        rate *= MetalArmor.goldFactor(player);
        //1.9 (MAJ RP) : skill Nucleaire (exposition), Poumons fragiles du Recuperateur ; decontamination plus rapide
        rate *= PioneerApi.factor(player, "nuclear.exposure") * PioneerApi.factor(player, "salvager.exposure");
        boolean exempt = player.isCreative() || player.isSpectator();
        double before = data.getDouble(DOSE);
        double dose = before * Math.pow(DOSE_DECAY, PioneerApi.factor(player, "nuclear.decon_speed")) + (exempt ? 0 : rate / 3600.0);
        if (before >= 1 && dose < 1) {
            PioneerApi.gain(player, "nuclear.decon", 1, null);
        }
        if (dose < 1e-6) {
            dose = 0;
        }
        data.putDouble(DOSE, dose);
        data.putDouble(RATE, rate);
        if (!exempt) {
            effects(player, dose);
        }
        if (holdsDosimeter(player)) {
            player.displayClientMessage(Component.translatable("nuclear.pioneers.dosimeter.bar", format(rate), format(dose)).withStyle(color(rate)), true);
            int clicks = rate < 0.005 ? 0 : rate < 0.1 ? 1 : rate < 1 ? 2 : rate < 10 ? 4 : 7;
            for (int i = 0; i < clicks; i++) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.PLAYERS,
                      0.25F, 1.6F + player.getRandom().nextFloat() * 0.4F);
            }
        }
    }

    private static void effects(ServerPlayer player, double dose) {
        if (dose > 1) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, true, false, true));
        }
        if (dose > 2) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 0, true, false, true));
        }
        if (dose > 6) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1, true, false, true));
        } else if (dose > 4 && player.tickCount % 100 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, true, false, true));
        }
    }

    /** Debit des objets radioactifs portes (Sv/h). */
    public static double inventoryRate(Inventory inventory) {
        double rate = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            double per = stack.is(HIGH) ? 3 : stack.is(MEDIUM) ? 0.5 : stack.is(LOW) ? 0.05 : 0;
            if (per > 0) {
                rate += per * stack.getCount() / (double) Math.max(1, stack.getMaxStackSize());
            }
        }
        return rate;
    }

    private static boolean holdsDosimeter(ServerPlayer player) {
        Item dosimeter = PiNuclear.DOSIMETER.get();
        if (player.getOffhandItem().is(dosimeter)) {
            return true;
        }
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (player.getInventory().getItem(i).is(dosimeter)) {
                return true;
            }
        }
        return false;
    }

    public static double rate(ServerPlayer player) {
        return player.getPersistentData().getDouble(RATE);
    }

    public static double dose(ServerPlayer player) {
        return player.getPersistentData().getDouble(DOSE);
    }

    /** Valeur en Sv (ou mSv / uSv) lisible. */
    public static String format(double sievert) {
        if (sievert >= 1) {
            return String.format(Locale.ROOT, "%.2f Sv", sievert);
        }
        if (sievert >= 0.001) {
            return String.format(Locale.ROOT, "%.1f mSv", sievert * 1000);
        }
        return String.format(Locale.ROOT, "%.0f \u00b5Sv", sievert * 1_000_000);
    }

    /** Palier de la dose (cle de langue {@code nuclear.pioneers.dose.<n>}). */
    public static int stage(double dose) {
        return dose > 6 ? 4 : dose > 4 ? 3 : dose > 2 ? 2 : dose > 1 ? 1 : 0;
    }

    public static ChatFormatting color(double rate) {
        return rate < 0.01 ? ChatFormatting.GREEN : rate < 1 ? ChatFormatting.YELLOW : rate < 10 ? ChatFormatting.GOLD : ChatFormatting.RED;
    }
}
