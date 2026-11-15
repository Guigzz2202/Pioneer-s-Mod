package fr.pioneers.bronze.machines;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.bronze.BronzeBlock;
import fr.pioneers.bronze.BronzeTile;
import fr.pioneers.bronze.steam.Steam;
import fr.pioneers.bronze.steam.SteamNode;
import fr.pioneers.bronze.steam.SteamPorts;
import fr.pioneers.bronze.steam.SteamVolume;
import fr.pioneers.core.Automation;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.machine.Ejector;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.side.Sided;
import fr.pioneers.registries.PiBronze;
import fr.pioneers.transport.Transfer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Machine de traitement a la vapeur (compacteur, extracteur, broyeur, four, trefileuse, laminoir, scie, melangeur ; lot 38 : crible, tour,
 * presse). API Machine (lot 7) : la tile PORTE son unite de procede ({@link SteamProcess} : recettes de la famille, vapeur prise a debit fixe)
 * et reste le noeud de vapeur (reserve, echanges {@link Steam#exchangeAll}) ; type de tile {@code steam_process} (presse : {@code steam_press},
 * garde pour les mondes existants).
 * <p>
 * 1.0.5-beta (lot 38, specification age du bronze §3.2) : FACES REGLABLES (panneau standard des faces) : objets (entree / sortie par face,
 * ejection), vapeur (gaz : entree), eau du melangeur (fluides : entree). Reglages par defaut = comportement d'avant : objets par les 6 faces
 * (les entrees n'acceptent que l'insertion, les sorties que l'extraction), vapeur par toutes les faces sauf la facade et le dessus, eau du
 * melangeur par toutes les faces ; pas d'ejection. Les brides de vapeur ({@link SteamPorts}) suivent les faces de vapeur. Selecteur de recette
 * standard (verrou de l'unite). Cles nouvelles et facultatives : {@code config} (faces), {@code lockedRecipe}, {@code overflow},
 * {@code pfmt} (format du lot 38).
 * <p>
 * Lecture des mondes d'avant le lot 38 ({@link #load}) : marteau-pilon a vapeur (bloc remappe vers la scie a vapeur, type de tile
 * {@code steam_hammer} relu ici) et presse d'etabli (format {@code SteamWorkshopTile} : 6 materiaux + sortie, verrou {@code recipe}) : leurs
 * objets sont POSES AU SOL au premier tick serveur (gardes en trop-plein si le tronçon est sauve avant), le verrou est efface, la vapeur
 * gardee ; compacteur d'avant (1 entree) : les cases de sortie retrouvent leur place.
 */
public class SteamProcessTile extends BronzeTile implements SteamNode, RecipeLockable {

    public static final double STEAM_PER_TICK = SteamProcess.STEAM_PER_TICK;
    public static final int IDLE = SteamProcess.IDLE, WORKING = SteamProcess.WORKING, NO_STEAM = SteamProcess.NO_STEAM, FULL = SteamProcess.FULL,
          NO_RECIPE = SteamProcess.NO_RECIPE;
    public static final int FURNACE_TIME = SteamProcess.FURNACE_TIME;
    /** 1.0.5-beta (lot 38) : cle du format de sauvegarde (absente : machine d'avant le lot 38). */
    public static final String FORMAT = "pfmt";
    public static final int FORMAT_VERSION = 38;

    private final SteamVolume chest = new SteamVolume(fr.pioneers.bronze.steam.SteamRules.PROCESS_RESERVE);
    private final PowerSource.Steam power = new PowerSource.Steam(chest);
    private final SteamProcess unit;
    private final SideConfig sides;
    private final Map<Direction, LazyOptional<IItemHandler>> sidedItems = new EnumMap<>(Direction.class);
    private final Map<Direction, LazyOptional<IFluidHandler>> sidedWater = new EnumMap<>(Direction.class);
    private LazyOptional<IItemHandler> internalItems = LazyOptional.empty();
    private LazyOptional<IFluidHandler> internalWater = LazyOptional.empty();

    /**
     * 1.0.4-beta (lot 11) : fabrique du type de tile ; un NBT de machine a vapeur relu sur un bloc qui n'en est plus une ne donne aucune tile
     * (le bloc pose cree la sienne), comme {@code AtelierTile.create}. 1.0.5-beta (lot 38) : aussi pour l'ancien type {@code steam_hammer}
     * (remappe : le bloc pose est la scie a vapeur) et le type {@code steam_press} garde.
     */
    @Nullable
    public static SteamProcessTile create(BlockPos pos, BlockState state) {
        return state.getBlock() instanceof SteamProcessBlock ? new SteamProcessTile(pos, state) : null;
    }

    private static SteamMachine machineOf(BlockState state) {
        return state.getBlock() instanceof SteamProcessBlock b ? b.machine : SteamMachine.COMPRESSOR;
    }

    /** Type de tile du bloc : la presse garde le sien ({@code steam_press}), les autres partagent {@code steam_process}. */
    private static BlockEntityType<?> typeOf(BlockState state) {
        return machineOf(state) == SteamMachine.PRESS ? PiBronze.STEAM_PRESS_TILE.get() : PiBronze.STEAM_PROCESS_TILE.get();
    }

    public SteamProcessTile(BlockPos pos, BlockState state) {
        super(typeOf(state), pos, state);
        SteamMachine machine = machineOf(state);
        sides = new SideConfig(EnumSet.allOf(SideMode.class), SideMode.IO, true, this::facing, this::sidesChanged).ejects(SideType.ITEMS);
        sides.addType(SideType.ITEMS);
        if (machine == SteamMachine.MIXER) {
            sides.addType(SideType.FLUIDS);
        }
        sides.addType(SideType.GASES);
        defaults(sides, machine);
        unit = carry(new SteamProcess(host(), this, machine));
    }

    /** Reglages par defaut des faces = comportement d'avant le lot 38 (voir l'en-tete). */
    static void defaults(SideConfig sides, SteamMachine machine) {
        for (RelativeSide side : RelativeSide.values()) {
            sides.set(SideType.ITEMS, side, SideMode.IO);
            if (machine == SteamMachine.MIXER) {
                sides.set(SideType.FLUIDS, side, SideMode.INPUT);
            }
            sides.set(SideType.GASES, side, side == RelativeSide.FRONT || side == RelativeSide.TOP ? SideMode.NONE : SideMode.INPUT);
        }
    }

    @Override
    protected PowerSource power() {
        return power;
    }

    public SteamProcess process() {
        return unit;
    }

    public SteamMachine machine() {
        return machineOf(getBlockState());
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(BronzeBlock.FACING) ? state.getValue(BronzeBlock.FACING) : Direction.NORTH;
    }

    public List<ItemSlot> inputs() {
        return unit.itemInputs();
    }

    @Nullable
    public FluidTank waterTank() {
        return unit.water();
    }

    // ------------------------------------------------------------------
    // Faces (lot 38)
    // ------------------------------------------------------------------

    @Override
    public SideConfig getSideConfig() {
        return sides;
    }

    private void sidesChanged() {
        if (sidedItems == null) {
            return;                                                       // appele pendant la construction (reglages par defaut)
        }
        invalidateSided();
        setChanged();
        if (level != null && !level.isClientSide) {
            //Brides de vapeur : d'apres les faces de vapeur reglees
            BlockState state = getBlockState();
            BlockState next = SteamPorts.refresh(state, level, worldPosition, (s, d) -> steamFace(d));
            if (next != state) {
                level.setBlock(worldPosition, next, Block.UPDATE_ALL);
            }
            level.sendBlockUpdated(worldPosition, state, getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** La face du monde {@code side} prend-elle la vapeur (faces reglees) ? */
    public boolean steamFace(Direction side) {
        return sides.mode(SideType.GASES, side).canInsert();
    }

    private void invalidateSided() {
        sidedItems.values().forEach(LazyOptional::invalidate);
        sidedItems.clear();
        sidedWater.values().forEach(LazyOptional::invalidate);
        sidedWater.clear();
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && unit != null) {
            if (side == null) {
                if (!internalItems.isPresent()) {
                    internalItems = LazyOptional.of(() -> new ItemSlots(slots, Automation.EXTERNAL));
                }
                return internalItems.cast();
            }
            SideMode mode = sides.mode(SideType.ITEMS, side);
            if (mode == SideMode.NONE) {
                return LazyOptional.empty();
            }
            return sidedItems.computeIfAbsent(side, d -> LazyOptional.of(() -> Sided.items(new ItemSlots(slots, Automation.EXTERNAL), mode))).cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER && unit != null && unit.water() != null) {
            if (side == null) {
                if (!internalWater.isPresent()) {
                    internalWater = LazyOptional.of(() -> new FillOnly(this::waterTank));
                }
                return internalWater.cast();
            }
            if (!sides.mode(SideType.FLUIDS, side).canInsert()) {
                return LazyOptional.empty();
            }
            return sidedWater.computeIfAbsent(side, d -> LazyOptional.of(() -> new FillOnly(this::waterTank))).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        invalidateSided();
        internalItems.invalidate();
        internalWater.invalidate();
    }

    /** Vue « remplissage seulement » du reservoir d'eau. */
    private record FillOnly(java.util.function.Supplier<FluidTank> tank) implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int i) {
            return tank.get().getFluid();
        }

        @Override
        public int getTankCapacity(int i) {
            return tank.get().getCapacity();
        }

        @Override
        public boolean isFluidValid(int i, @NotNull FluidStack stack) {
            return tank.get().isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tank.get().fill(resource, action);
        }

        @NotNull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @NotNull
        @Override
        public FluidStack drain(int max, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------
    // Vapeur
    // ------------------------------------------------------------------

    @Override
    public SteamVolume steam(Direction side) {
        return steamFace(side) ? chest : null;
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    /** Recettes de cette machine dans sa famille (tier minimal <= 1), dans l'ordre d'essai. */
    public static List<fr.pioneers.api.machine.tier.TierRecipes.TierRecipe> recipes(Level level, SteamMachine machine) {
        return fr.pioneers.api.machine.tier.TierRecipes.of(level, machine.family());
    }

    /** Recettes {@code pioneers:steam_processing} propres a cette machine (pages JEI des machines a vapeur), triees par identifiant. */
    public static List<SteamRecipe> ownRecipes(Level level, SteamMachine machine) {
        List<SteamRecipe> out = new ArrayList<>();
        for (SteamRecipe r : fr.pioneers.recipe.PiRecipes.sorted(level, PiBronze.STEAM_PROCESSING.get())) {
            if (r.machine() == machine) {
                out.add(r);
            }
        }
        return out;
    }

    @Override
    public void dropContents(Consumer<ItemStack> drop) {
        super.dropContents(drop);
        unit.takePending().forEach(drop);
    }

    @Override
    public void serverTick(ServerLevel level) {
        if (unit.hasPending()) {
            //Lot 38 : objets d'un ancien atelier a vapeur (marteau-pilon, presse d'etabli) : poses au sol, rien ne se perd
            for (ItemStack s : unit.takePending()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5, s);
            }
            setChanged();
        }
        Steam.exchangeAll(level, this);
        unit.tick();
        if (level.getGameTime() % 10 == 3 && sides.isEject(SideType.ITEMS)) {
            Ejector.eject(level, worldPosition, sides, Transfer.ITEMS, unit.ejectable(), 16);
        }
        flushClient(level, 10);
    }

    // ------------------------------------------------------------------
    // Interface standard v2, selecteur (porte par l'unite)
    // ------------------------------------------------------------------

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public String guiTier() {
        return "bronze";
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        List<StandardLayout.Part> out = new ArrayList<>(unit.layout.ageParts(true, false));
        if (unit.toolSlot() != null) {
            //1.0.5-beta (lot 44) : case Outil sous la fleche (presse a vapeur : matrice a plaque)
            out.add(new StandardLayout.Part("slot_tool", StandardLayout.slotFrame(unit.layout.toolX, unit.layout.toolY)));
        }
        return out;
    }

    @Override
    public String recipeSource() {
        return unit.recipeSource();
    }

    @Override
    public RecipeLock recipeLock() {
        return unit.recipeLock();
    }

    @Override
    public void recipeLockChanged() {
        unit.recipeLockChanged();
    }

    @Override
    public int[] recipeSlot() {
        return unit.recipeSlot();
    }

    @Override
    public int recipeTier() {
        return unit.recipeTier();
    }

    @Override
    public List<ItemStack> presentItems() {
        return unit.presentItems();
    }

    @Override
    public List<FluidStack> presentFluids() {
        return unit.presentFluids();
    }

    @Override
    public Level recipeLevel() {
        return level;
    }

    // ------------------------------------------------------------------
    // Sauvegarde (lot 38 : faces, format ; lecture des formats d'avant)
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        chest.save(tag);
        tag.put("config", sides.save());
        tag.putInt(FORMAT, FORMAT_VERSION);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        boolean legacy = !tag.contains(FORMAT);
        String id = tag.getString("id");
        if (legacy && (id.equals("pioneers:steam_hammer") || id.equals("pioneers:steam_press"))) {
            //Ancien atelier a vapeur (SteamWorkshopTile : cle slots = 6 materiaux + sortie, verrou recipe) : tout au sol, verrou efface
            CompoundTag t = tag.copy();
            ListTag old = t.getList("slots", Tag.TAG_COMPOUND);
            t.remove("slots");
            t.remove("recipe");
            t.remove(RecipeLock.NBT);
            t.remove("progress");
            super.load(t);
            for (Tag e : old) {
                unit.addPending(ItemSlot.loadStack((CompoundTag) e));
            }
        } else if (legacy && unit.machineKind.legacyInputs() != unit.machineKind.inputs) {
            //Compacteur d'avant le lot 38 (1 entree) : sortie et sous-produit glissent apres la 2e entree
            CompoundTag t = tag.copy();
            ListTag old = t.getList("slots", Tag.TAG_COMPOUND), moved = new ListTag();
            int was = unit.machineKind.legacyInputs(), shift = unit.machineKind.inputs - was;
            for (Tag e : old) {
                CompoundTag c = ((CompoundTag) e).copy();
                int i = c.getShort("Slot");
                c.putShort("Slot", (short) (i >= was ? i + shift : i));
                moved.add(c);
            }
            t.put("slots", moved);
            super.load(t);
        } else {
            super.load(tag);
        }
        chest.load(tag);
        if (tag.contains("config", Tag.TAG_COMPOUND)) {
            sides.load(tag.getCompound("config"));
        }
        unit.moveLegacyTools();                                           // 1.0.5-beta (lot 44) : remanents d'avant -> case Outil
    }
}
