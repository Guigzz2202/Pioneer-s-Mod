package fr.pioneers.farm.device;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.animal.AnimalData;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.PiAnimals;
import fr.pioneers.farm.kitchen.StationTiles;
import fr.pioneers.farm.ui.FarmLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Couveuse : 6 oeufs fecondes ({@link FertileEggItem}) au chaud sous la lampe ; ils basculent de plus en plus et eclosent en {@link #HATCH}
 * ticks : poussin domestique de la race de l'oeuf, sexe au hasard. Clic droit avec un oeuf feconde : on le pose ; main vide accroupi : on
 * reprend le dernier. Entonnoirs : oeufs par le haut et les cotes.
 */
public class IncubatorTile extends FarmDeviceTile {

    public static final int SLOTS = 6, HATCH = 6000;
    private final int[] time = new int[SLOTS];

    public IncubatorTile(BlockPos pos, BlockState state) {
        super(pos, state, SLOTS, 0);
    }

    /** 1.0.5-beta (lot 41) : 6 places d'oeufs (barre de l'eclosion sous chacune), chaleur en jauge. */
    @Override
    protected FarmLayout layout() {
        return FarmLayout.builder().gauge(FarmLayout.Gauge.HEAT)
              .cells(StationTiles.range(0, SLOTS), 3).build();
    }

    @Override
    public float cellProgress(int index) {
        return index < 0 || index >= SLOTS || items.get(index).isEmpty() ? -1 : progress(index);
    }

    @Override
    public float gaugeLevel() {
        return warm() ? 1 : 0;
    }

    @Override
    public List<Component> gaugeTip() {
        return List.of(Component.translatable(warm() ? "gui.pioneers.farm.gauge.warm" : "gui.pioneers.farm.gauge.cold"));
    }

    /** Avancement de l'oeuf (0..1) ; -1 si la case est vide. */
    public float progress(int slot) {
        return items.get(slot).isEmpty() ? -1 : Math.min(1F, time[slot] / (float) HATCH);
    }

    public boolean warm() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (items.get(i).isEmpty()) {
                continue;
            }
            time[i]++;
            if (time[i] >= HATCH) {
                hatch((ServerLevel) level, i);
            } else if (time[i] % 400 == 0) {
                sync();
            }
        }
    }

    @Override
    public void clientTick() {
        for (int i = 0; i < SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                time[i]++;
            }
        }
    }

    private void hatch(ServerLevel level, int slot) {
        ItemStack egg = items.get(slot);
        int breed = FertileEggItem.breed(egg);
        items.set(slot, ItemStack.EMPTY);
        time[slot] = 0;
        //1.0.3-beta lot 8 : le poussin est l'entite de la race de l'oeuf
        Chicken chick = (Chicken) fr.pioneers.farm.animal.breed.PiBreeds.type(AnimalSpecies.CHICKEN,
              Math.max(0, Math.min(breed, AnimalSpecies.CHICKEN.breeds().length - 1))).create(level);
        if (chick != null) {
            chick.setAge(-24000);
            chick.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 0.7, worldPosition.getZ() + 0.5, level.random.nextFloat() * 360F, 0);
            AnimalData d = PiAnimals.get(chick);
            if (d != null) {
                d.init = true;
                d.breed = Math.max(0, Math.min(breed, AnimalSpecies.CHICKEN.breeds().length - 1));
                d.male = level.random.nextBoolean();
                d.domestic = true;
                d.hunger = d.thirst = 100;
            }
            level.addFreshEntity(chick);
        }
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, egg), worldPosition.getX() + 0.5, worldPosition.getY() + 0.6,
              worldPosition.getZ() + 0.5, 8, 0.15, 0.1, 0.15, 0.05);
        level.playSound(null, worldPosition, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 0.8F, 1.4F);
        level.playSound(null, worldPosition, SoundEvents.CHICKEN_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.6F);
        PiFarmDevices.award(level, worldPosition, "hatch");
        sync();
    }


    @Override
    protected boolean canInsert(int slot, ItemStack stack, @Nullable Direction side) {
        return side != Direction.DOWN && items.get(slot).isEmpty() && stack.is(PiFarmDevices.FERTILE_EGG.get());
    }

    @Override
    protected int slotLimit(int slot) {
        return 1;
    }

    @Override
    protected void itemsChanged() {
        for (int i = 0; i < SLOTS; i++) {
            if (items.get(i).isEmpty()) {
                time[i] = 0;
            }
        }
        sync();
    }

    @Override
    public void particles(RandomSource random) {
        if (level != null && warm() && random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, worldPosition.getX() + 0.5, worldPosition.getY() + 0.75, worldPosition.getZ() + 0.5, 0, 0.005, 0);
        }
    }

    @Override
    protected int accent() {
        return 0xF0A040;
    }

    @Override
    protected void describe(FarmCard card, boolean sneaking) {
        //1.6 : les oeufs en icones, l'eclosion en petite jauge sous chacun ; le plus proche de l'eclosion en clair
        List<ItemStack> eggs = new ArrayList<>();
        List<Float> done = new ArrayList<>();
        int next = Integer.MAX_VALUE;
        for (int i = 0; i < SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                eggs.add(items.get(i));
                done.add(progress(i));
                next = Math.min(next, Math.max(0, (HATCH - time[i]) / 20));
            }
        }
        if (eggs.isEmpty()) {
            card.add(FarmCard.Line.hint(Component.translatable("farm.pioneers.device.incubator.empty")));
            return;
        }
        float[] f = new float[done.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = done.get(i);
        }
        card.add(FarmCard.Line.items(eggs, f));
        card.add(FarmCard.Line.pair(FarmCard.label("hatch"), FarmCard.duration(next), 0xF0C060));
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putIntArray("time", time);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        int[] t = tag.getIntArray("time");
        for (int i = 0; i < SLOTS; i++) {
            time[i] = i < t.length ? t[i] : 0;
        }
    }
}
