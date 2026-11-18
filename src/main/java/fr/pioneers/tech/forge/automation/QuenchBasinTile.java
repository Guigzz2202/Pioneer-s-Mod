package fr.pioneers.tech.forge.automation;

import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.forge.ForgeTile;
import fr.pioneers.tech.forge.HeatedMetal;
import fr.pioneers.tech.forge.HeatedMetalItem;
import fr.pioneers.tech.forge.smithing.QuenchBasinBlock;
import fr.pioneers.tech.forge.PiForge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bac de trempe : reserve d'eau ({@link #CAPACITY} mB, NBT {@code water}), trempe tout metal chaud. API de fondation : {@link #quench},
 * {@link #water()}, {@link #fill}. PROPRIETAIRE : ingenieur « automation ».
 * <p>Automatisation (forge primaire 0.21, specification §3) : eau par seau (bloc, ingenieur « smithing ») ou par conduite (capacite fluide sur
 * toutes les faces, eau seulement) ; metal chaud par entonnoir (dessus et flancs : case d'entree) ou jete dedans (objets au sol dans le bac) ;
 * la piece trempee (et les pepites de chute) sort par le dessous (seules faces d'extraction). Chaque trempe boit {@link #WATER_PER_QUENCH} mB,
 * siffle et fume ({@link HeatedMetalItem#hiss}). Le metal refroidi a l'air (pas assez chaud pour une trempe) passe tel quel a la sortie, sous sa
 * forme froide ({@link HeatedMetal#coldForm}) : le bac ne se bouche jamais.
 */
public class QuenchBasinTile extends ForgeTile {

    public static final int CAPACITY = 4000;
    /** Eau consommee par trempe (mB). */
    public static final int WATER_PER_QUENCH = 25;
    /** Intervalle de recherche des objets jetes dans le bac (ticks). */
    private static final int SCAN = 5;
    /** 1.1.1 : duree d'une trempe (ticks) : la piece fume dans l'eau, visible, avant de sortir. */
    public static final int QUENCH_TICKS = 60;

    /** Avancement de la trempe en cours (ticks), synchronise pour le rendu et le message d'etat. */
    protected int progress;
    private int shownLevel = -1;

    protected int water;
    private final ItemSlot input, output, extra;
    private final LazyOptional<IItemHandler> topItems, bottomItems;
    private final LazyOptional<IFluidHandler> fluids = LazyOptional.of(WaterHandler::new);
    @Nullable
    private AABB inside;

    public QuenchBasinTile(BlockPos pos, BlockState state) {
        super(PiForge.QUENCH_BASIN_TILE.get(), pos, state);
        input = slot(new ItemSlot(() -> 1, HeatedMetal::is, (s, a) -> true, (s, a) -> !a.external(), this::changed, 62, 30));
        output = slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, 110, 30));
        extra = slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, 132, 30));
        topItems = LazyOptional.of(() -> new SideItems(List.of(input)));
        bottomItems = LazyOptional.of(() -> new SideItems(List.of(output, extra)));
    }

    public int water() {
        return water;
    }

    /** Ajoute de l'eau ; renvoie la quantite acceptee. */
    public int fill(int mb, boolean simulate) {
        int take = Math.max(0, Math.min(mb, CAPACITY - water));
        if (!simulate && take > 0) {
            water += take;
            changed();
            markClient();
        }
        return take;
    }

    /**
     * Trempe une piece chaude : renvoie le resultat ({@link HeatedMetal#quench}) et consomme l'eau ; vide si la piece n'est pas chaude ou s'il
     * n'y a pas assez d'eau. {@code hot} n'est pas modifie (au code appelant de le retirer). Siffle, fume et compte pour le progres « trempe ».
     */
    public ItemStack quench(ItemStack hot) {
        if (level == null || !HeatedMetal.is(hot)) {
            return ItemStack.EMPTY;
        }
        if (!HeatedMetal.isHot(hot, level)) {
            //Piece finie refroidie sans trempe : elle donne sa piece, sans eau (sinon elle resterait bloquee)
            return HeatedMetal.shape(hot);
        }
        if (water < WATER_PER_QUENCH) {
            return ItemStack.EMPTY;
        }
        water -= WATER_PER_QUENCH;
        changed();
        markClient();
        if (level instanceof ServerLevel server) {
            HeatedMetalItem.hiss(server, worldPosition.getX() + 0.5, worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5);
            BronzeEvents.award(server, worldPosition, "quench");
        }
        return HeatedMetal.quench(hot, level);
    }

    // ------------------------------------------------------------------
    // Tick : case d'entree, objets jetes dans le bac
    // ------------------------------------------------------------------

    @Override
    public void serverTick(ServerLevel level) {
        //1.1.1 : la piece reste QUENCH_TICKS dans l'eau (vapeur, sifflement), puis passe a la sortie
        if (!input.isEmpty()) {
            ItemStack in = input.getStack();
            boolean warm = HeatedMetal.isHot(in, level);
            if (!warm || water >= WATER_PER_QUENCH) {
                if (progress == 0 && warm) {
                    HeatedMetalItem.hiss(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5);
                }
                progress++;
                if (warm && progress % 6 == 0) {
                    level.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 0.65,
                          worldPosition.getZ() + 0.5, 2, 0.2, 0.05, 0.2, 0.01);
                }
                if (progress % 10 == 0) {
                    markClient();
                }
                if (progress >= (warm ? QUENCH_TICKS : 1) && process(in)) {
                    input.setStack(ItemStack.EMPTY);
                    progress = 0;
                    level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS,
                          0.15F, 1.8F);
                    markClient();
                } else if (progress > QUENCH_TICKS) {
                    progress = QUENCH_TICKS;                              //sortie pleine : la piece attend dans l'eau
                }
            }
        } else if (progress != 0) {
            progress = 0;
            markClient();
        }
        if (input.isEmpty() && (level.getGameTime() + worldPosition.asLong()) % SCAN == 0) {
            if (inside == null) {
                inside = new AABB(worldPosition.getX() + 0.0625, worldPosition.getY(), worldPosition.getZ() + 0.0625, worldPosition.getX() + 0.9375,
                      worldPosition.getY() + 0.9, worldPosition.getZ() + 0.9375);
            }
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, inside, e -> HeatedMetal.is(e.getItem()))) {
                ItemStack stack = entity.getItem();
                if (stack.getCount() == 1 && input.isEmpty()) {
                    input.setStack(stack.copy());
                    entity.discard();
                    changed();
                    markClient();
                }
            }
        }
        updateLevel(level);
        flushClient(level, 10);
    }

    /** Niveau d'eau visible (propriete d'etat {@code level} 0..4 du bloc) : vide = pas d'eau dessinee. */
    private void updateLevel(ServerLevel level) {
        int lvl = water <= 0 ? 0 : Math.max(1, Math.min(4, (water * 4 + CAPACITY - 1) / CAPACITY));
        if (lvl != shownLevel) {
            shownLevel = lvl;
            BlockState state = getBlockState();
            if (state.hasProperty(QuenchBasinBlock.LEVEL) && state.getValue(QuenchBasinBlock.LEVEL) != lvl) {
                level.setBlock(worldPosition, state.setValue(QuenchBasinBlock.LEVEL, lvl), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Clic droit avec une piece : posee dans le bac (trempe de {@link #QUENCH_TICKS} ticks). Vrai si acceptee. */
    public boolean insertManual(ItemStack piece) {
        if (!input.isEmpty() || !HeatedMetal.is(piece)) {
            return false;
        }
        input.setStack(piece.copyWithCount(1));
        progress = 0;
        changed();
        markClient();
        return true;
    }

    /** Pieces finies a reprendre a la main (sortie puis pepites) ; vide s'il n'y a rien. */
    public List<ItemStack> takeOutputs() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemSlot s : List.of(output, extra)) {
            if (!s.isEmpty()) {
                out.add(s.getStack().copy());
                s.setStack(ItemStack.EMPTY);
            }
        }
        if (!out.isEmpty()) {
            changed();
            markClient();
        }
        return out;
    }

    public ItemStack inputPiece() {
        return input.getStack();
    }

    public ItemStack outputPiece() {
        return output.getStack();
    }

    public int progress() {
        return progress;
    }

    /** Trempe (ou laisse passer froid) une piece vers les sorties ; faux si les sorties sont pleines ou s'il manque l'eau d'une trempe. */
    private boolean process(ItemStack hot) {
        boolean warm = HeatedMetal.isHot(hot, level);
        ItemStack shape = HeatedMetal.shape(hot);
        ItemStack result = warm ? HeatedMetal.quench(hot, level) : !shape.isEmpty() ? shape : HeatedMetal.coldForm(hot);
        List<ItemStack> extras = warm ? HeatedMetal.quenchExtras(hot) : List.of();
        if (warm && water < WATER_PER_QUENCH || !fitsOut(result, extras)) {
            return false;
        }
        if (warm) {
            result = quench(hot);
        }
        output.insert(result, Action.EXECUTE, Automation.INTERNAL);
        for (ItemStack e : extras) {
            extra.insert(e.copy(), Action.EXECUTE, Automation.INTERNAL);
        }
        changed();
        return true;
    }

    private boolean fitsOut(ItemStack result, List<ItemStack> extras) {
        if (!output.insert(result.copy(), Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
            return false;
        }
        //les extras sont des pepites d'un seul metal : une seule sorte
        for (ItemStack e : extras) {
            if (!extra.insert(e.copy(), Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Capacites : entree dessus et flancs, sortie dessous ; eau partout
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && side != null) {
            return (side == Direction.DOWN ? bottomItems : topItems).cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluids.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        topItems.invalidate();
        bottomItems.invalidate();
        fluids.invalidate();
    }

    /** Vue d'une face sur quelques cases (droits des cases, automatisation externe). */
    private static final class SideItems implements IItemHandler {

        private final List<ItemSlot> slots;

        SideItems(List<ItemSlot> slots) {
            this.slots = slots;
        }

        @Override
        public int getSlots() {
            return slots.size();
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return slots.get(slot).getStack();
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slots.get(slot).insert(stack, simulate ? Action.SIMULATE : Action.EXECUTE, Automation.EXTERNAL);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slots.get(slot).extract(amount, simulate ? Action.SIMULATE : Action.EXECUTE, Automation.EXTERNAL);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slots.get(slot).getLimit(slots.get(slot).getStack());
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slots.get(slot).isItemValid(stack);
        }
    }

    /** Reservoir d'eau vu des conduites : remplissage seulement (eau du tag {@code minecraft:water}). */
    private final class WaterHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return water <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, water);
        }

        @Override
        public int getTankCapacity(int tank) {
            return CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid().is(FluidTags.WATER);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !resource.getFluid().is(FluidTags.WATER)) {
                return 0;
            }
            return QuenchBasinTile.this.fill(resource.getAmount(), action.simulate());
        }

        @NotNull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @NotNull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------
    // Client et sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putInt("water", water);
        tag.putInt("progress", progress);
        tag.put("in", input.getStack().save(new CompoundTag()));
        tag.put("out", output.getStack().save(new CompoundTag()));
    }

    @Override
    protected void readClient(CompoundTag tag) {
        water = tag.getInt("water");
        progress = tag.getInt("progress");
        input.setStack(ItemStack.of(tag.getCompound("in")));
        output.setStack(ItemStack.of(tag.getCompound("out")));
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("water", water);
        tag.putInt("progress", progress);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        water = tag.getInt("water");
        progress = tag.getInt("progress");
    }
}
