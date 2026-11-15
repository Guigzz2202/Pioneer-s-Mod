package fr.pioneers.gear.electric;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.core.Action;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.item.GasCapsuleItem;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 9) : energie et hydrogene des objets electriques ({@link PoweredItem}), dans le NBT de la pile :
 * <ul>
 *     <li>{@code Energy} (long, FE) : meme cle que l'equipement modulaire ;</li>
 *     <li>{@code Hydrogen} (long, mB) : reservoir du jetpack et du lance-flammes.</li>
 * </ul>
 * La pile expose sa reserve en {@link IEnergyStorage} (recharge seulement : buffer d'energie, etabli de modulation, chargeurs des autres
 * mods) et son reservoir en {@link IGasHandler} (remplissage seulement, hydrogene : case d'entree d'un buffer de gaz, qui vide d'abord un
 * recipient qui OFFRE du gaz ; le notre n'en offre jamais). Une capsule d'hydrogene posee sur l'objet dans l'inventaire y verse 1 B.
 */
public final class ElectricCharge {

    private ElectricCharge() {
    }

    public static final String ENERGY = "Energy", HYDROGEN = "Hydrogen";
    /** Barre de l'objet : energie (jaune) ; hydrogene (blanc bleute) pour le jetpack et le lance-flammes. */
    public static final int ENERGY_BAR = 0xF5D442, HYDROGEN_BAR = 0xBFE3FF;

    // ------------------------------------------------------------------ energie

    public static long capacity(ItemStack stack) {
        return stack.getItem() instanceof PoweredItem p ? p.energyCapacity() : 0;
    }

    public static long energy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getLong(ENERGY));
    }

    public static void setEnergy(ItemStack stack, long energy) {
        long value = Math.max(0, Math.min(capacity(stack), energy));
        if (value != energy(stack)) {
            stack.getOrCreateTag().putLong(ENERGY, value);
        }
    }

    /** Preleve exactement {@code amount} FE ; faux (et rien preleve) si la reserve ne suffit pas. */
    public static boolean use(ItemStack stack, long amount) {
        if (amount <= 0) {
            return true;
        }
        long energy = energy(stack);
        if (energy < amount) {
            return false;
        }
        setEnergy(stack, energy - amount);
        return true;
    }

    /** Preleve au plus {@code amount} FE ; renvoie ce qui a ete preleve. */
    public static long drain(ItemStack stack, long amount) {
        long taken = Math.min(energy(stack), Math.max(0, amount));
        if (taken > 0) {
            setEnergy(stack, energy(stack) - taken);
        }
        return taken;
    }

    /** Ajoute de l'energie ; renvoie ce qui est entre. */
    public static long charge(ItemStack stack, long amount) {
        long given = Math.max(0, Math.min(capacity(stack) - energy(stack), amount));
        if (given > 0) {
            setEnergy(stack, energy(stack) + given);
        }
        return given;
    }

    public static boolean powered(ItemStack stack, long atLeast) {
        return PoweredItem.is(stack) && energy(stack) >= Math.max(1, atLeast);
    }

    public static float ratio(ItemStack stack) {
        long cap = capacity(stack);
        return cap <= 0 ? 0 : (float) energy(stack) / cap;
    }

    // ------------------------------------------------------------------ hydrogene

    public static long hydrogenCapacity(ItemStack stack) {
        return stack.getItem() instanceof PoweredItem p ? p.hydrogenCapacity() : 0;
    }

    public static long hydrogen(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getLong(HYDROGEN));
    }

    public static void setHydrogen(ItemStack stack, long mb) {
        long value = Math.max(0, Math.min(hydrogenCapacity(stack), mb));
        if (value != hydrogen(stack)) {
            stack.getOrCreateTag().putLong(HYDROGEN, value);
        }
    }

    /** Ajoute de l'hydrogene ; renvoie ce qui est entre. */
    public static long fill(ItemStack stack, long mb) {
        long given = Math.max(0, Math.min(hydrogenCapacity(stack) - hydrogen(stack), mb));
        if (given > 0) {
            setHydrogen(stack, hydrogen(stack) + given);
        }
        return given;
    }

    public static float hydrogenRatio(ItemStack stack) {
        long cap = hydrogenCapacity(stack);
        return cap <= 0 ? 0 : (float) hydrogen(stack) / cap;
    }

    /** Un tick d'usage : preleve {@code mb} d'hydrogene ET {@code fe} d'energie, tout ou rien ; faux si l'un des deux manque (rien n'est preleve). */
    public static boolean burn(ItemStack stack, long mb, long fe) {
        if (hydrogen(stack) < mb || energy(stack) < fe) {
            return false;
        }
        setHydrogen(stack, hydrogen(stack) - mb);
        setEnergy(stack, energy(stack) - fe);
        return true;
    }

    public static boolean isHydrogen(@Nullable Gas gas) {
        return gas != null && gas.getRegistryName().equals(PiGasTypes.HYDROGEN.getId());
    }

    /**
     * Capsule d'hydrogene posee (clic) sur l'objet dans un inventaire : 1 B verse si le reservoir a la place ; la capsule vide revient au
     * curseur. A appeler depuis {@code Item.overrideOtherStackedOnMe}.
     */
    public static boolean capsuleOnto(ItemStack self, ItemStack other, ClickAction action, Player player, SlotAccess cursor) {
        if (action != ClickAction.PRIMARY || !(other.getItem() instanceof GasCapsuleItem capsule) || other.getCount() != 1
            || !isHydrogen(GasCapsuleItem.getGas(other)) || hydrogenCapacity(self) - hydrogen(self) < GasCapsuleItem.CAPACITY) {
            return false;
        }
        fill(self, GasCapsuleItem.CAPACITY);
        cursor.set(new ItemStack(capsule));
        player.playSound(SoundEvents.BOTTLE_EMPTY, 0.8F, 1.2F);
        if (!player.level().isClientSide) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        return true;
    }

    // ------------------------------------------------------------------ infobulle

    /** Lignes communes : description ({@code item.pioneers.<id>.desc}), energie (jaune, rouge a sec), hydrogene, recharge et remplissage. */
    public static void tooltip(ItemStack stack, List<Component> lines) {
        Tooltips tips = Tooltips.of(lines);
        tips.desc(stack.getDescriptionId() + ".desc");
        //1.0.4-beta (lot 28) : energie et reservoir au format commun des equipements (J:core/tooltip/GearTips)
        GearTips.energy(tips, energy(stack), capacity(stack));
        if (hydrogenCapacity(stack) > 0) {
            GearTips.tank(tips, "electric.pioneers.stat.hydrogen", hydrogen(stack), hydrogenCapacity(stack), "mb");
            tips.hint("electric.pioneers.hint.fill");
        }
        tips.hint("electric.pioneers.hint.charge");
    }

    // ------------------------------------------------------------------ capacites

    public static ICapabilityProvider provider(ItemStack stack) {
        return new Provider(stack);
    }

    private static final class Provider implements ICapabilityProvider, IEnergyStorage, IGasHandler {

        private final ItemStack stack;
        private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> this);
        private final LazyOptional<IGasHandler> gas = LazyOptional.of(() -> this);

        Provider(ItemStack stack) {
            this.stack = stack;
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ENERGY) {
                return energy.cast();
            }
            if (cap == PioneersAPI.GAS_HANDLER && hydrogenCapacity(stack) > 0) {
                return gas.cast();
            }
            return LazyOptional.empty();
        }

        private int rate() {
            return stack.getItem() instanceof PoweredItem p ? p.energyRate() : 0;
        }

        // -------------------------------------------------- energie (recharge seulement)

        @Override
        public int receiveEnergy(int max, boolean simulate) {
            long room = capacity(stack) - energy(stack);
            int given = (int) Math.max(0, Math.min(room, Math.min(max, rate())));
            if (!simulate && given > 0) {
                setEnergy(stack, energy(stack) + given);
            }
            return given;
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, energy(stack));
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, capacity(stack));
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        // -------------------------------------------------- hydrogene (remplissage seulement)

        @Override
        public int getGasTanks() {
            return 1;
        }

        @NotNull
        @Override
        public GasStack getGasInTank(int tank) {
            long h = hydrogen(stack);
            return h <= 0 ? GasStack.EMPTY : GasStack.of(PiGasTypes.HYDROGEN.get(), h);
        }

        @Override
        public long getGasTankCapacity(int tank) {
            return hydrogenCapacity(stack);
        }

        @Override
        public boolean isGasValid(int tank, @NotNull GasStack in) {
            return isHydrogen(in.getGas());
        }

        @NotNull
        @Override
        public GasStack insertGas(@NotNull GasStack in, @NotNull Action action) {
            if (in.isEmpty() || !isHydrogen(in.getGas())) {
                return in;
            }
            long room = hydrogenCapacity(stack) - hydrogen(stack);
            long given = Math.max(0, Math.min(room, in.getAmount()));
            if (given <= 0) {
                return in;
            }
            if (action == Action.EXECUTE) {
                setHydrogen(stack, hydrogen(stack) + given);
            }
            return given >= in.getAmount() ? GasStack.EMPTY : in.copyWithAmount(in.getAmount() - given);
        }

        @NotNull
        @Override
        public GasStack extractGas(long amount, @NotNull Action action) {
            return GasStack.EMPTY;
        }

        @NotNull
        @Override
        public GasStack extractGas(@NotNull GasStack wanted, @NotNull Action action) {
            return GasStack.EMPTY;
        }
    }
}
