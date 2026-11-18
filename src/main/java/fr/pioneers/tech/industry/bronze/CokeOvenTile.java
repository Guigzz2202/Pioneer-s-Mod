package fr.pioneers.tech.industry.bronze;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.multiblock.FurnaceHeat;
import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.tech.bronze.BronzeTile;
import fr.pioneers.tech.bronze.HouseControllerBlock;
import fr.pioneers.tech.bronze.blast.BlastFurnaceTile;
import fr.pioneers.tech.bronze.blast.FireBricksBlock;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.tech.industry.IoTank;
import fr.pioneers.tech.industry.recipe.RecipeMatch;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Four a coke (age du bronze). API Machine (lot 7) : la tile ne fait plus que PORTER son unite de procede ({@link CokeOvenProcess}) ; elle
 * garde les capacites (goudron par toutes les faces) et l'etat du bloc. Type de tile {@code coke_oven}, cles et ordre des cases inchanges.
 * 1.0.4-beta (lot 24, Guillaume : « le controleur du coke oven primitif devient un block plein ») : MULTIBLOC MAISON ({@link HouseNbt},
 * {@code coke_oven}) : la porte du four (bloc plein) devant un massif de briques refractaires sur 7 foyers en briques refractaires et une
 * tuyere a l'arriere, la chambre vide au centre ; il ne cuit que forme (non forme : rien ne se perd) ; les foyers flambent en marche.
 */
public class CokeOvenTile extends BronzeTile implements RecipeMatch.Buffers, RecipeLockable, MultiblockCore.House.Holder, MultiblockPort {

    public static final int IDLE = CokeOvenProcess.IDLE, WORKING = CokeOvenProcess.WORKING, FULL = CokeOvenProcess.FULL,
          NO_RECIPE = CokeOvenProcess.NO_RECIPE;

    private final CokeOvenProcess unit = carry(new CokeOvenProcess(host()));
    /** 1.0.4-beta (lot 24) : structure de Guillaume ({@link HouseNbt}). */
    public static final String SHAPE = "coke_oven";
    private final MultiblockCore.House house = new MultiblockCore.House(HouseNbt.host(this, this::facing, SHAPE, "gui.pioneers.multiblock.house.coke_oven", this::markClient), 40);
    private int paintedFire = -1;
    /** 1.0.5-beta (lot 15) : derniere chaleur peinte sur les briques (-1 : a repeindre ; -2 : rien de peint). */
    private int paintedBricks = -1;
    /** Le four chauffe en vase clos : le feu est son propre travail. */
    private final PowerSource.Fuel power = new PowerSource.Fuel(() -> unit.working() ? 1_000 : 20, () -> unit.working() ? 1 : 0);
    public final IoTank tar = unit.tar;
    private final LazyOptional<IFluidHandler> fluid = LazyOptional.of(() -> tar);

    public CokeOvenTile(BlockPos pos, BlockState state) {
        super(PiIndustry.COKE_OVEN_TILE.get(), pos, state);
    }

    @Override
    protected PowerSource power() {
        return power;
    }

    public CokeOvenProcess process() {
        return unit;
    }

    @Override
    public List<ItemSlot> itemInputs() {
        return unit.itemInputs();
    }

    @Override
    public List<ItemSlot> itemOutputs() {
        return unit.itemOutputs();
    }

    @Override
    public List<FluidTank> fluidInputs() {
        return unit.fluidInputs();
    }

    @Override
    public List<FluidTank> fluidOutputs() {
        return unit.fluidOutputs();
    }

    @Override
    public List<BasicGasTank> gasInputs() {
        return unit.gasInputs();
    }

    @Override
    public List<BasicGasTank> gasOutputs() {
        return unit.gasOutputs();
    }

    @Override
    public void serverTick(ServerLevel level) {
        house.tick();
        if (house.formed()) {
            unit.tick();
        } else {
            //1.0.4-beta (lot 24) : structure non formee : le four ne cuit pas (rien n'est perdu)
            fr.pioneers.tech.bronze.LitBronzeBlock.setLit(level, worldPosition, getBlockState(), false);
        }
        //1.0.4-beta (lot 24) : les foyers en briques refractaires flambent quand le four cuit
        int fire = house.formed() && unit.working() ? 2 : 0;
        if (fire != paintedFire) {
            paintedFire = fire;
            HouseNbt.paintFire(level, worldPosition, facing(), SHAPE, fire);
            //1.0.5-beta (lot 23) : tuyere et controleur au meme palier que les briques (chauffe homogene)
            fr.pioneers.tech.bronze.HeatPalette.paintAll(level, HouseNbt.positions(worldPosition, facing(), SHAPE, "T"), fire);
            fr.pioneers.tech.bronze.HeatPalette.paint(level, worldPosition, fire);
        }
        //1.0.5-beta (lot 15) : controleur connecte a la paroi formee ; briques peintes (role et chaleur de la palette commune)
        HouseControllerBlock.sync(level, worldPosition, house.formed());
        int bricks = house.formed() ? fire : -2;
        if (bricks != paintedBricks) {
            paintedBricks = bricks;
            paintBricks(level, bricks);
        }
        //1.0.5-beta (lot 13) : ambiance commune des fours primitifs (crepitement discret du feu, BlastFurnaceTile.crackle)
        if (fire > 0) {
            BlastFurnaceTile.cokeCrackle(level, worldPosition);
        }
        //1.0.5-beta (lot 13) : qui entre dans la chambre d'un four allume se brule
        if (house.formed() && FurnaceHeat.due(level, worldPosition)) {
            FurnaceHeat.inside(level, HouseNbt.positions(worldPosition, facing(), SHAPE, "I"), fire > 0);
        }
        flushClient(level, 20);
    }

    /**
     * 1.0.5-beta (lot 15, Guillaume : « texture finie pour le four a coke », « homogeneiser les couleurs ») : role de chaque brique de la
     * structure formee (repere du controleur, {@link HouseNbt.Cell}) ; meme regle que {@code T:core/p105_lot15.py coke_part}.
     */
    public static FireBricksBlock.Part partOf(int x, int y, int z, int top) {
        if (y >= top) {
            return x == 0 && z == 1 ? FireBricksBlock.Part.COKE_CHIMNEY : FireBricksBlock.Part.COKE_CAP;
        }
        if (Math.abs(x) == 1 && (z == 0 || z == 2)) {
            return FireBricksBlock.Part.COKE_CORNER;
        }
        return y == 1 ? FireBricksBlock.Part.COKE_DOOR : FireBricksBlock.Part.COKE_WALL;
    }

    /**
     * 1.0.5-beta (lot 23) : angle ORIENTE (faces exterieures dans le monde) d'une brique d'angle en (x, z) du repere du controleur dont la
     * facade regarde {@code front} (z 0 : cote facade, 2 : cote arriere ; x + : a droite en regardant la facade = {@link HouseNbt#at}). PUR.
     */
    public static FireBricksBlock.Part cornerOf(int x, int z, Direction front) {
        Direction forward = front.getOpposite(), right = forward.getClockWise();
        Direction a = z == 0 ? front : forward, b = x > 0 ? right : right.getOpposite();
        boolean north = a == Direction.NORTH || b == Direction.NORTH, west = a == Direction.WEST || b == Direction.WEST;
        return north ? west ? FireBricksBlock.Part.COKE_CORNER_NW : FireBricksBlock.Part.COKE_CORNER_NE
              : west ? FireBricksBlock.Part.COKE_CORNER_SW : FireBricksBlock.Part.COKE_CORNER_SE;
    }

    /**
     * Chaleur d'une brique du four a coke : 1.0.5-beta (lot 23, chauffe homogene) : le palier du feu pour TOUTES les briques (avant : voute et
     * cheminee un palier en dessous). PUR (banc de la chauffe).
     */
    public static int heatOf(FireBricksBlock.Part part, int fire) {
        return fire;
    }

    /** Peint les briques : {@code fire} >= 0 structure formee (role + chaleur), -2 briques remises a l'etat libre. */
    private void paintBricks(Level level, int fire) {
        int top = 0;
        for (HouseNbt.Cell c : HouseNbt.cells(SHAPE)) {
            if (c.letter() == 'B') {
                top = Math.max(top, c.y());
            }
        }
        Direction front = facing();
        for (HouseNbt.Cell c : HouseNbt.cells(SHAPE)) {
            if (c.letter() != 'B') {
                continue;
            }
            BlockPos p = HouseNbt.at(worldPosition, front, c);
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState st = level.getBlockState(p);
            if (!st.hasProperty(FireBricksBlock.PART)) {
                continue;
            }
            FireBricksBlock.Part part = fire < 0 ? FireBricksBlock.Part.NONE : partOf(c.x(), c.y(), c.z(), top);
            if (part == FireBricksBlock.Part.COKE_CORNER) {
                part = cornerOf(c.x(), c.z(), front);
            }
            BlockState want = st.setValue(FireBricksBlock.PART, part).setValue(FireBricksBlock.HEAT, fire < 0 ? 0 : heatOf(part, fire));
            if (want != st) {
                level.setBlock(p, want, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void dropContents(Consumer<ItemStack> drop) {
        super.dropContents(drop);
        //1.0.5-beta (lot 15) : controleur casse : les briques redeviennent de simples briques
        if (level != null && !level.isClientSide && paintedBricks >= 0) {
            paintBricks(level, -2);
            paintedBricks = -2;
        }
    }

    /** 1.0.4-beta (lot 24) : facade de la porte du four (la structure est derriere elle). */
    public Direction facing() {
        return HouseNbt.facing(getBlockState());
    }

    public boolean formed() {
        return house.formed();
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    /** Lot 8c : contrat de port commun : un port commun pose dans la structure formee prete la capacite interne du four. */
    @org.jetbrains.annotations.Nullable
    @Override
    public Object portHandler(fr.pioneers.tech.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() ? MultiblockPort.capabilityOf(this, type) : null;
    }

    /** 1.0.4-beta (lot 24) : premiere case fautive de la structure (null : formee) ; tile, banc et GameTests. */
    @org.jetbrains.annotations.Nullable
    public static BlockPos fault(fr.pioneers.tech.industry.multi.Structure.View view, BlockPos controller, Direction front) {
        return HouseNbt.fault(view, controller, front, SHAPE);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        house.load();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        house.removed(true);
    }

    @Override
    protected void writeClient(net.minecraft.nbt.CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
    }

    @Override
    protected void readClient(net.minecraft.nbt.CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluid.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluid.invalidate();
    }

    // ------------------------------------------------------------------
    // Interface standard v2
    // ------------------------------------------------------------------

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public String guiTier() {
        return "coke_oven";
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return unit.layout.ageParts(true, false);
    }

    // ------------------------------------------------------------------
    // Selecteur de recette (porte par l'unite)
    // ------------------------------------------------------------------

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
        return 0;
    }

    @Override
    public List<ItemStack> presentItems() {
        return unit.presentItems();
    }

    @Override
    public Level recipeLevel() {
        return level;
    }

    /** 1.0.4-beta (lot 25) : particules de la structure en marche (T:industry/nbt_models.py HOUSE_FX). */
    @Override
    public void clientTick() {
        houseFx("coke_oven", lit());
    }
}
