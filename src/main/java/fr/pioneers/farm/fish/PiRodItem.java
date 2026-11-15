package fr.pioneers.farm.fish;

import fr.pioneers.Pioneers;
import fr.pioneers.client.farm.fish.RodClient;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

/**
 * Canne du mod (bambou, bronze, acier) : lancer, attendre la touche, ferrer (clic droit dans la fenetre), puis combat a tension : clic droit
 * MAINTENU = mouliner. La canne fixe la resistance du fil, la vitesse du moulinet et le temps d'attente. Un appat en main gauche (vers,
 * appat) donne des touches plus rapides et des especes plus rares.
 * <p>
 * 1.6 : le lancer s'arme comme un arc. Clic droit maintenu sans bouchon a l'eau = armer (jauge de force, {@code FishingHud}) ; relacher =
 * lancer d'autant plus loin que l'on a arme longtemps ({@link PiBobber#power}), jusqu'a la longueur du fil de la canne
 * ({@link PiBobber#LINE}). Geste du bras (armer, fouetter) : {@link RodClient}.
 */
public class PiRodItem extends Item {

    public static final TagKey<Item> BAIT = ItemTags.create(Pioneers.rl("fishing_bait"));
    /** Joueurs qui arment un lancer (serveur : UUID ; client : joueur local, par id d'entite). */
    private static final Set<UUID> CHARGING_SERVER = new HashSet<>();
    private static final Set<Integer> CHARGING_CLIENT = new HashSet<>();
    public final int tier;

    public PiRodItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** Note qu'un joueur arme (ou n'arme plus) ; rend vrai s'il armait. */
    private static boolean setCharging(Player player, boolean on) {
        if (player.level().isClientSide) {
            return on ? !CHARGING_CLIENT.add(player.getId()) : CHARGING_CLIENT.remove(player.getId());
        }
        return on ? !CHARGING_SERVER.add(player.getUUID()) : CHARGING_SERVER.remove(player.getUUID());
    }

    /**
     * Le joueur arme-t-il un lancer ? Serveur et joueur local : d'apres le clic qui a commence l'armement ; autres joueurs vus par le client :
     * canne en cours d'utilisation sans bouchon a l'eau.
     */
    public static boolean charging(Player player) {
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof PiRodItem)) {
            return false;
        }
        if (!player.level().isClientSide) {
            return CHARGING_SERVER.contains(player.getUUID());
        }
        return player.isLocalPlayer() ? CHARGING_CLIENT.contains(player.getId()) : PiBobber.of(player) == null;
    }

    /** Ticks d'armement en cours (avec la fraction de tick pour l'affichage), 0 si le joueur n'arme pas. */
    public static float chargeTicks(Player player, float partial) {
        return charging(player) ? player.getTicksUsingItem() + partial : 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PiBobber b = PiBobber.of(player);
        if (b == null) {
            //1.6 : on arme ; le lancer part au relacher (releaseUsing)
            setCharging(player, true);
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        int st = b.state();
        if (st == PiBobber.BITE || st == PiBobber.FIGHT) {
            setCharging(player, false);
            if (!level.isClientSide && st == PiBobber.BITE) {
                b.strike(player);
            }
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (!level.isClientSide) {
            b.retrieve();
        }
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Relacher apres avoir arme : le lancer part, d'autant plus vite que l'armement a dure. */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player) || !setCharging(player, false)) {
            return;
        }
        int ticks = getUseDuration(stack) - timeLeft;
        float power = PiBobber.power(ticks);
        if (level.isClientSide) {
            PiBobber.markCast(player, PiBobber.windUp(ticks));
            return;
        }
        if (PiBobber.of(player) != null) {
            return;
        }
        InteractionHand hand = player.getUsedItemHand();
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        boolean bait = other.is(BAIT);
        if (bait && !player.getAbilities().instabuild) {
            other.shrink(1);
        }
        //1.0.5-beta (lot 31, retour 42) : panier de peche en main secondaire : il fournit l'appat (ver de terre, appat)
        if (!bait && other.getItem() instanceof fr.pioneers.home.bag.BagItem b && b.kind == fr.pioneers.home.bag.BagKind.FISH_BAG) {
            bait = !fr.pioneers.home.bag.BagStore.takeOne(other, b.kind, s -> s.is(BAIT), player.getAbilities().instabuild).isEmpty();
        }
        level.addFreshEntity(PiBobber.cast(player, tier, bait, power));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.35F + 0.35F * power,
              (0.55F - 0.15F * power) / (level.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity instanceof Player player) {
            setCharging(player, false);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    /** Geste dessine par {@link RodClient} (armer, fouetter, mouliner). */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CUSTOM;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(RodClient.INSTANCE);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return action == ToolActions.FISHING_ROD_CAST;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    /** Materiau de chaque canne (cle tooltip.pioneers.gear.material.*). */
    private static final String[] ROD_MATERIALS = {"bamboo", "bronze", "steel"};

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        //1.0.4-beta (lot 28) : format commun des equipements (J:core/tooltip/GearTips) : materiau, fil (longueur, resistance), durabilite
        //(infobulle commune), tier (bambou : age mecanique, bronze, acier : MKI, comme les outils de ces materiaux)
        Tooltips tips = Tooltips.of(tooltip);
        int t = Math.min(tier, 2);
        GearTips.material(tips, ROD_MATERIALS[t], null);
        tips.stat("tooltip.pioneers.gear.line", Component.translatable("tooltip.pioneers.gear.line_value", Math.round(PiBobber.LINE[t]),
              Component.translatable("tooltip.pioneers.gear.line_strength." + t)));
        GearTips.rank(tips, t);
        tips.hint("farm.pioneers.fishing.charge_hint");
        tips.hint("farm.pioneers.fishing.hold");
        tips.hint("farm.pioneers.fishing.bait");
    }
}
