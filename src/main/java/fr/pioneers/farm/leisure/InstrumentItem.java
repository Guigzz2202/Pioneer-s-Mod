package fr.pioneers.farm.leisure;

import fr.pioneers.client.farm.leisure.InstrumentScreen;
import fr.pioneers.client.farm.leisure.LeisureClient;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.5 : instrument de musique : clic droit ouvre le clavier ({@link InstrumentScreen}) ; les notes passent par {@link PacketNote}. 1.6 : tenu a
 * deux mains, anime quand on joue ({@link LeisureClient}).
 */
public class InstrumentItem extends Item {

    public final Instrument instrument;

    public InstrumentItem(Instrument instrument, Properties properties) {
        super(properties);
        this.instrument = instrument;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> InstrumentScreen.open(instrument));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** 1.6 : poses des bras en 3e personne (LeisureClient). */
    @Override
    public void initializeClient(@NotNull Consumer<IClientItemExtensions> consumer) {
        consumer.accept(LeisureClient.EXTENSIONS);
    }

    /** Instrument tenu (main droite, puis gauche), ou null. */
    @Nullable
    public static Instrument held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof InstrumentItem item) {
                return item.instrument;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc(getDescriptionId() + ".desc");
    }
}
