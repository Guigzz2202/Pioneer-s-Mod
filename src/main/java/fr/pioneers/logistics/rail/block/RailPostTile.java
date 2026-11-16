package fr.pioneers.logistics.rail.block;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.Action;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.pressure.PressureVolume;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.logistics.rail.RailWorld;
import fr.pioneers.logistics.rail.logistics.Cargo;
import fr.pioneers.logistics.rail.logistics.PostPoint;
import fr.pioneers.logistics.rail.logistics.StationPost;
import fr.pioneers.logistics.rail.train.Car;
import fr.pioneers.logistics.rail.PiRail;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.2 : postes de quai sans cases d'objets : bras de remplissage (citernes fluide et gaz), pompe a carburant (diesel, gaz), borne de
 * vapeur (sans foyer), prise de quai (wagon-batterie, batteries des electriques), borne de recharge, fibre de quai (wagon-serveur),
 * raccord pneumatique (wagon a air) et quai voyageurs. Chacun sert le vehicule arrete devant lui ; ses reserves se raccordent aux cables
 * du mod par ses faces (interface standard).
 * <p>
 * Interface : 4 mode (0 charger, 1 decharger), 5 vehicule devant (0/1), 6 quantite passee depuis la pose, 7 sorte du poste, 8 air (L x10),
 * 9 volume d'air (L), 10 donnees stockees, 11 capacite des donnees. Action {@link #MODE}.
 * <p>
 * 0.20.5 : dans une gare logistique, le role de la gare (ou la livraison en cours) decide du sens, de la ressource et de la quantite ; les
 * reserves du poste comptent dans le stock de la gare et les robots les remplissent ou les vident ({@link PostPoint}).
 * <p>
 * API Machine (lot 8c) : logique dans {@link RailPostUnit} (fiches {@code migrated}) ; cette tile la porte ({@link #carry}) et garde le
 * point de releve, le stock de la gare, les faces d'energie et les capacites.
 */
public class RailPostTile extends DataMachineTile implements StationPost {

    public static final int MODE = CUSTOM;

    private final PostKind kind;
    private final int tier;
    private LazyOptional<PressureVolume> airCap = LazyOptional.empty();
    /** Point de releve du poste (gare logistique) ; lu par l'unite. */
    final PostPoint point;
    /** API Machine (lot 8c) : la logique (reserves, transferts, vehicule servi, plan de la gare) est dans l'unite portee. */
    private final RailPostUnit unit;

    public RailPostTile(BlockPos pos, BlockState state) {
        super(PiRail.POST_TILE.get(), pos, state, energyCapacity(state), energyRate(state));
        kind = RailPostBlock.kindOf(state);
        tier = TieredMachineBlock.tierOf(state);
        unit = carry(new RailPostUnit(this, access(), kind, tier));
        if (unit.air != null) {
            PressureVolume a = unit.air;
            airCap = LazyOptional.of(() -> a);
        }
        if (kind == PostKind.DATA) {
            dataDirections(true, true);
        }
        point = new PostPoint(this, Direction.UP);
    }

    @Override
    protected void tick() {
    }

    /** Lot 8c : reservoir de donnees declare pour l'unite (fibre de quai). */
    BasicDataTank declareData(BasicDataTank tank) {
        return dataTank(tank);
    }

    private static long energyCapacity(BlockState state) {
        PostKind k = RailPostBlock.kindOf(state);
        return k == PostKind.ENERGY || k == PostKind.CHARGER ? k.rate(TieredMachineBlock.tierOf(state)) * 40L : 0;
    }

    private static int energyRate(BlockState state) {
        PostKind k = RailPostBlock.kindOf(state);
        return k == PostKind.ENERGY || k == PostKind.CHARGER ? k.rate(TieredMachineBlock.tierOf(state)) * 2 : 0;
    }

    //Appeles PENDANT le constructeur parent : la sorte se lit dans l'etat (le champ kind n'est pas encore pose)

    @Override
    protected boolean producesEnergy() {
        return false;
    }

    @Override
    protected SideMode initialMode() {
        return RailPostBlock.kindOf(getBlockState()).reversible() ? SideMode.IO : SideMode.INPUT;
    }

    /** Prise de quai : l'energie entre par les faces en chargement, sort en dechargement. */
    void energySides() {
        if (kind == PostKind.ENERGY) {
            sides.independent(SideType.ENERGY, unit != null && unit.unload ? SideMode.OUTPUT : SideMode.INPUT);
            sidesChanged();
        }
    }

    public PostKind kind() {
        return kind;
    }

    /** Lot 4 : jauges hautes de l'interface (fluide et gaz, vapeur, energie) selon la sorte du poste. */
    public static int gauges(PostKind kind) {
        return switch (kind) {
            case FLUID, FUEL -> 2;
            case STEAM, ENERGY, CHARGER -> 1;
            default -> 0;
        };
    }

    @Nullable
    public PressureVolume air() {
        return unit.air;
    }

    static String id(Fluid f) {
        ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(f);
        return rl == null ? "" : rl.toString();
    }

    static String id(Gas g) {
        ResourceLocation rl = g.getRegistryName();
        return rl == null ? "" : rl.toString();
    }

    /** Vehicule servi par un poste : arrete devant lui (ou au-dessus d'une fosse), et de la bonne sorte. */
    @Nullable
    /** 1.9 (MAJ RP, bloc 4) : case du vehicule servi par le poste (controle des frontieres de claim). */
    public static BlockPos carPos(RailWorld w, BlockPos pos, Car car) {
        if (w.carAt(pos.above()) == car) {
            return pos.above();
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (w.carAt(pos.relative(d)) == car) {
                return pos.relative(d);
            }
            if (w.carAt(pos.relative(d).below()) == car) {
                return pos.relative(d).below();
            }
        }
        return pos;
    }

    public static Car carFor(RailWorld w, BlockPos pos, PostKind kind) {
        if (kind == PostKind.PIT) {
            Car c = w.carAt(pos.above());
            return c != null && kind.serves(c) ? c : null;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Car c = w.carAt(pos.relative(d));
            if (c == null) {
                c = w.carAt(pos.relative(d).below());
            }
            if (c != null && kind.serves(c)) {
                return c;
            }
        }
        return null;
    }

    @Nullable
    static ResourceLocation rl(String id) {
        return ResourceLocation.tryParse(id);
    }

    @Override
    public void addStock(Map<Cargo, Long> out) {
        if (unit.fluid != null && !unit.fluid.getFluid().isEmpty() && rl(id(unit.fluid.getFluid().getFluid())) != null) {
            out.merge(Cargo.fluid(rl(id(unit.fluid.getFluid().getFluid()))), (long) unit.fluid.getFluidAmount(), Long::sum);
        }
        if (unit.gas != null && !unit.gas.getStack().isEmpty() && rl(id(unit.gas.getStack().getGas())) != null) {
            out.merge(Cargo.gas(rl(id(unit.gas.getStack().getGas()))), unit.gas.getStack().getAmount(), Long::sum);
        }
        if (kind == PostKind.ENERGY && energy.getStored() > 0) {
            out.merge(Cargo.ENERGY, energy.getStored(), Long::sum);
        }
        if (unit.data != null && !unit.data.getStack().isEmpty()) {
            out.merge(new Cargo(Cargo.Kind.DATA, unit.data.getStack().getType().getRegistryName()), unit.data.getStored(), Long::sum);
        }
        if (unit.air != null && unit.air.getAir() >= 1) {
            out.merge(Cargo.AIR, (long) unit.air.getAir(), Long::sum);
        }
    }

    @Override
    public Set<Cargo.Kind> holds() {
        return switch (kind) {
            case FLUID -> EnumSet.of(Cargo.Kind.FLUID, Cargo.Kind.GAS);
            case ENERGY -> EnumSet.of(Cargo.Kind.ENERGY);
            case DATA -> EnumSet.of(Cargo.Kind.DATA);
            case AIR -> EnumSet.of(Cargo.Kind.AIR);
            default -> EnumSet.noneOf(Cargo.Kind.class);
        };
    }

    /** 0.20.46 (audit) : poste connu du releve des quais des son chargement. */
    @Override
    public void onLoad() {
        super.onLoad();
        point.loaded(level);
    }

    @Override
    public void setRemoved() {
        point.remove(level);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        point.remove(level);
        super.onChunkUnloaded();
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == MODE && kind.reversible()) {
            unit.setUnload(!unit.unload);
        }
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.postParts(gauges(kind), false, kind.reversible());
    }

    /** API Machine (lot 4) : valeurs NOMMEES (lot 8c : declarees par l'unite, memes cles et indices). */
    public static final UiValues.Key UNLOAD = RailPostUnit.UNLOAD, SERVING = RailPostUnit.SERVING, MOVED = RailPostUnit.MOVED, KIND = RailPostUnit.KIND,
          AIR_X10 = RailPostUnit.AIR_X10, VOLUME = RailPostUnit.VOLUME, DATA_STORED = RailPostUnit.DATA_STORED, DATA_CAPACITY = RailPostUnit.DATA_CAPACITY;

    @Override
    public String guiTier() {
        return kind.tiered ? TieredMachineBlock.TIERS[tier] : "steel";
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PioneersAPI.PRESSURE_HANDLER && unit.air != null) {
            return airCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        airCap.invalidate();
    }

    /** Sens du poste (vider le vehicule). */
    public boolean unloads() {
        return unit.unload;
    }

    public void setUnloads(boolean value) {
        unit.setUnload(value);
    }
}
