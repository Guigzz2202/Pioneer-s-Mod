package fr.pioneers.space.tile;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.BoundingTile;
import fr.pioneers.core.machine.MachineBounds;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.tech.industry.multi.IndustryStyle;
import fr.pioneers.tech.industry.multi.PortBlock;
import fr.pioneers.tech.industry.multi.PortHost;
import fr.pioneers.tech.industry.multi.PortTile;
import fr.pioneers.core.recipes.AssemblyStation;
import fr.pioneers.core.registries.PiWorksMachines;
import fr.pioneers.space.assembly.AssemblyHall;
import fr.pioneers.space.assembly.AssemblyHallStructure;
import fr.pioneers.space.tile.base.TileEntityAssemblyBase;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.9.2 (retours de Guillaume) : controleur d'un multibloc d'assemblage ({@link AssemblyHall}) : baie d'assemblage robotique (poste
 * {@link AssemblyStation#ROVER} : rovers, robots, drones, tortues) ou hall d'integration des fusees (poste {@link AssemblyStation#ROCKET} :
 * fusees et kit de station). Meme grille, memes recettes et meme interface que les anciennes tables ({@link TileEntityAssemblyBase}) ;
 * l'assemblage n'avance que si la structure est complete.
 * <p>
 * Branchements : ports industriels d'objets et d'energie poses dans l'enveloppe ({@link PortHost}) : energie en entree ; objets en entree
 * (chaque piece rejoint sa case) ou en sortie (le produit fini est pousse vers les voisins). Le controleur lui-meme n'est pas un port.
 * Structure revalidee toutes les 2 s et a chaque pose ou casse dans ses cases ({@link Structures}).
 * <p>
 * Anciennes tables posees (rocket_assembler, rover_assembly_table) : remappees vers ce controleur, leur contenu est garde ; leurs blocs
 * fantomes sont retires au premier tick.
 */
public class TileEntityAssemblyHall extends TileEntityAssemblyBase implements PortHost, MultiblockCore.House.Holder {

    private static final int CHECK_EVERY = 40;

    private final AssemblyHall.Kind kind;
    /** Lot 8a : cycle de vie commun des multiblocs maison (verification periodique ou a la demande, revalidee au chargement). */
    private final MultiblockCore.House house = new MultiblockCore.House(new MultiblockCore.House.Host() {
        @Override
        public net.minecraft.world.level.Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public net.minecraft.core.Direction front() {
            return facing();
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            if (level == null) {
                return null;
            }
            AssemblyHallStructure.Result r = AssemblyHallStructure.check(level, kind, worldPosition, facing());
            if (r.formed()) {
                return MultiblockCore.House.Survey.ok(r.cells(), r);
            }
            String what = r.expected() == null ? "assembler.pioneers.structure.check"
                  : "assembler.pioneers.structure.role." + r.expected().name().toLowerCase(Locale.ROOT);
            return MultiblockCore.House.Survey.fail(what, r.error(), r);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            AssemblyHallStructure.Result r = (AssemblyHallStructure.Result) survey.data();
            AssemblyHallStructure.Result old = structure;
            boolean changed = first || old == null || old.tier() != r.tier() || !old.ports().equals(r.ports()) || !old.members().equals(r.members());
            tier = r.tier();
            structure = r;
            errorPos = null;
            errorRole = -1;
            if (changed) {
                if (old != null && !old.ports().equals(r.ports())) {
                    AssemblyHallStructure.paint(level, kind, old, false);
                }
                AssemblyHallStructure.paint(level, kind, r, true);
                refreshPorts(r.ports());
                if (old != null) {
                    refreshPorts(old.ports());
                }
                paintController(tier + 1);
                if (first) {
                    IndustryEvents.award(level, worldPosition, "industry_formed");
                    IndustryEvents.award(level, worldPosition, "assembly_hall_" + kind.id());
                }
            }
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            if (survey.removal()) {
                return;
            }
            AssemblyHallStructure.Result r = (AssemblyHallStructure.Result) survey.data();
            AssemblyHallStructure.Result old = structure;
            if (broken && old != null) {
                AssemblyHallStructure.paint(level, kind, old, false);
                refreshPorts(old.ports());
            }
            structure = null;
            errorPos = r == null ? null : r.error();
            errorRole = r == null || r.expected() == null ? -1 : r.expected().ordinal();
            paintController(0);
        }

        @Override
        public void houseChanged() {
            setChanged();
            syncNow();
        }
    }, CHECK_EVERY);
    private int tier;
    @Nullable
    private AssemblyHallStructure.Result structure;
    /** Case fautive (cote client : message de l'ecran) et role attendu (ordinal, -1 = aucun). */
    @Nullable
    private BlockPos errorPos;
    private int errorRole = -1;
    private boolean ghostsCleared;

    public TileEntityAssemblyHall(BlockEntityType<?> type, BlockPos pos, BlockState state, AssemblyHall.Kind kind) {
        super(type, pos, state, kind.energy);
        this.kind = kind;
    }

    public static TileEntityAssemblyHall robotics(BlockPos pos, BlockState state) {
        return new TileEntityAssemblyHall(PiWorksMachines.HALL_TILES.get(AssemblyHall.Kind.ROBOTICS).get(), pos, state, AssemblyHall.Kind.ROBOTICS);
    }

    public static TileEntityAssemblyHall rocket(BlockPos pos, BlockState state) {
        return new TileEntityAssemblyHall(PiWorksMachines.HALL_TILES.get(AssemblyHall.Kind.ROCKET).get(), pos, state, AssemblyHall.Kind.ROCKET);
    }

    public AssemblyHall.Kind kind() {
        return kind;
    }

    @Override
    public AssemblyStation getStation() {
        return kind.station;
    }

    /** Memes couleurs d'interface que les anciennes tables. */
    @Override
    protected String guiName() {
        return kind == AssemblyHall.Kind.ROBOTICS ? "rover_assembly_table" : "rocket_assembler";
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
        if (!ghostsCleared) {
            ghostsCleared = true;
            clearOldGhosts();
        }
        house.tick();
        super.tick();
        if (house.formed() && level != null && level.getGameTime() % 10 == 0) {
            pushProducts();
        }
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        house.load();
    }

    @Override
    public boolean structureReady() {
        return house.formed();
    }

    private void refreshPorts(List<BlockPos> ports) {
        for (BlockPos p : ports) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof PortTile port) {
                port.refresh();
            }
        }
    }

    private void paintController(int t) {
        BlockState state = getBlockState();
        if (state.hasProperty(IndustryStyle.TIER) && state.getValue(IndustryStyle.TIER) != t) {
            level.setBlock(worldPosition, state.setValue(IndustryStyle.TIER, t), Block.UPDATE_CLIENTS);
        }
    }

    /** Controleur casse : la structure redevient neutre et les ports ne pretent plus rien. */
    public void dismantle() {
        if (level == null) {
            return;
        }
        house.removed(true);
        if (structure != null) {
            AssemblyHallStructure.paint(level, kind, structure, false);
            refreshPorts(structure.ports());
        }
        structure = null;
        house.benchForm(false);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            house.removed(true);
        }
    }

    /** Anciennes tables d'un bloc a fantomes (avant 1.9.2) : leurs blocs fantomes orphelins sont retires (sans butin). */
    private void clearOldGhosts() {
        if (level == null) {
            return;
        }
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-1, 0, -1), worldPosition.offset(1, 4, 1))) {
            if (level.getBlockState(p).getBlock() == MachineBounds.BLOCK.get() && level.getBlockEntity(p) instanceof BoundingTile ghost
                && worldPosition.equals(ghost.getMain())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    // ------------------------------------------------------------------
    // Ports
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public Object portHandler(PortBlock.Type type, boolean output) {
        if (!house.formed()) {
            return null;
        }
        if (type == PortBlock.Type.ENERGY) {
            return output ? null : super.getCapability(ForgeCapabilities.ENERGY, null).orElse(null);
        }
        if (type == PortBlock.Type.ITEM) {
            return super.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        }
        return null;
    }

    /** Les ports d'objets en sortie poussent le produit fini vers leurs voisins hors de la structure. */
    private void pushProducts() {
        ItemSlot out = outputSlot();
        if (out.isEmpty() || structure == null) {
            return;
        }
        for (BlockPos p : structure.ports()) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof PortBlock port) || port.type != PortBlock.Type.ITEM || !state.getValue(PortBlock.OUTPUT)) {
                continue;
            }
            for (Direction d : Directions.ALL) {
                BlockPos n = p.relative(d);
                if (structure.cells().contains(n) || !level.isLoaded(n) || !ClanApi.canTransfer(level, p, n)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(n);
                IItemHandler target = be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, d.getOpposite()).orElse(null);
                if (target == null) {
                    continue;
                }
                ItemStack offered = out.extract(64, Action.SIMULATE, Automation.INTERNAL);
                if (offered.isEmpty()) {
                    return;
                }
                ItemStack left = ItemHandlerHelper.insertItem(target, offered, false);
                int moved = offered.getCount() - left.getCount();
                if (moved > 0) {
                    out.extract(moved, Action.EXECUTE, Automation.INTERNAL);
                }
            }
        }
    }

    /** Pas de branchement par le controleur : tout passe par les ports. */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (side != null && (cap == ForgeCapabilities.ENERGY || cap == ForgeCapabilities.ITEM_HANDLER)) {
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    @Nullable
    @Override
    public SideConfig getSideConfig() {
        return null;
    }

    // ------------------------------------------------------------------
    // Ecran et rendu
    // ------------------------------------------------------------------

    /** Lot 8a : interface standard v2 (inventaire dans le cadre, onglets a droite, Infos et Structure) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    public int tier() {
        return tier;
    }

    @Nullable
    @Override
    public Component structureProblem() {
        if (house.formed()) {
            return null;
        }
        if (errorPos == null || errorRole < 0) {
            return Component.translatable("assembler.pioneers.structure.check");
        }
        AssemblyHall.Role role = AssemblyHall.Role.values()[errorRole];
        return Component.translatable("assembler.pioneers.structure.missing",
              Component.translatable("assembler.pioneers.structure.role." + role.name().toLowerCase(Locale.ROOT)),
              errorPos.getX(), errorPos.getY(), errorPos.getZ());
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
        tag.putInt("tier", tier);
        if (errorPos != null) {
            tag.putLong("error", errorPos.asLong());
            tag.putInt("errorRole", errorRole);
        }
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
        tier = tag.getInt("tier");
        errorPos = tag.contains("error") ? BlockPos.of(tag.getLong("error")) : null;
        errorRole = tag.contains("errorRole") ? tag.getInt("errorRole") : -1;
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        int r = Math.max(kind.halfX, kind.halfZ) * 2 + 1;
        return new AABB(worldPosition.offset(-r, -1, -r), worldPosition.offset(r + 1, kind.roof + 2, r + 1));
    }
}
