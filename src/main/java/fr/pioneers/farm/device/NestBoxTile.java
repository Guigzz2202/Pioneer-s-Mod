package fr.pioneers.farm.device;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.ui.FarmLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Pondoir ("nichoir" jusqu'a la 1.0.5 ; id nest_box inchange) : les poules a 12 blocs viennent y pondre ({@code farm/animal/NestGoal}) ; jusqu'a {@link #CAPACITY} oeufs dans la paille (oeufs et
 * oeufs fecondes). Clic droit : on ramasse tout. Entonnoir dessous.
 */
public class NestBoxTile extends FarmDeviceTile {

    public static final int CAPACITY = 12;

    public NestBoxTile(BlockPos pos, BlockState state) {
        super(pos, state, 4, 0);
    }

    /** 1.0.5-beta (lot 41) : 4 cases d'oeufs (sortie : la main dans l'interface, un entonnoir par le dessous). */
    @Override
    protected FarmLayout layout() {
        return FarmLayout.builder().outputs(new int[]{0, 1, 2, 3}, 2, "gui.pioneers.farm.slot.eggs", "gui.pioneers.farm.slot.eggs",
              "gui.pioneers.farm.slot.eggs", "gui.pioneers.farm.slot.eggs").build();
    }

    /** Progres « pondoir » : a l'ouverture, s'il y a des oeufs (avant : en les prenant a la main). */
    @Override
    public void opened(Player player) {
        if (eggs() > 0) {
            PiFarmDevices.award(level, worldPosition, "nest");
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            DeviceSites.add(level, worldPosition, DeviceSites.Kind.NEST);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            DeviceSites.remove(level, worldPosition, DeviceSites.Kind.NEST);
        }
    }

    public int eggs() {
        int n = 0;
        for (ItemStack s : items) {
            n += s.getCount();
        }
        return n;
    }

    public boolean room() {
        return eggs() < CAPACITY;
    }

    /** Une poule pond dans le pondoir. */
    public boolean lay(ItemStack egg) {
        if (!room()) {
            return false;
        }
        ItemStack rest = store(egg, 0, items.size(), 16);
        if (!rest.isEmpty()) {
            return false;
        }
        sound(SoundEvents.CHICKEN_EGG, 1F, 1F);
        sync();
        return true;
    }


    @Override
    protected boolean canExtract(int slot, @Nullable Direction side) {
        return side == Direction.DOWN || side == null;
    }

    @Override
    protected int accent() {
        return 0xE0C890;
    }

    @Override
    protected void describe(FarmCard card, boolean sneaking) {
        int fertile = 0;
        for (ItemStack s : items) {
            if (s.is(PiFarmDevices.FERTILE_EGG.get())) {
                fertile += s.getCount();
            }
        }
        card.add(FarmCard.Line.gauge(FarmCard.label("eggs"), eggs() / (float) CAPACITY, FarmCard.ratio(eggs(), CAPACITY), 0xF0E6C8));
        if (fertile > 0) {
            card.add(FarmCard.Line.pair(FarmCard.label("fertile"), Component.literal(String.valueOf(fertile)), 0xF0B040));
        }
    }

    public static boolean isEgg(ItemStack stack) {
        return stack.is(Items.EGG) || stack.is(PiFarmDevices.FERTILE_EGG.get());
    }
}
