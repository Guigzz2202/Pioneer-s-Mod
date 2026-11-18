package fr.pioneers.space.content.stabilizer;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.space.content.PiStabilizer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controleur du Stabilisateur spatial (paroi, en bas au centre d'une face laterale). Il verifie la structure chaque seconde et porte la case
 * du noyau : des que la chambre est valide et que son centre est libre (ou que le noyau en place est consomme et l'astre eteint), il pose
 * lui-meme le noyau au centre. Son interface montre l'etat et ouvre celle de la chambre.
 * <p>
 * Interface : 4 forme, 5 noyau en place, 6 noyau consomme, 7 regime du noyau (+1, 0 = aucun), 8 etat de l'astre.
 */
public class StabilizerControllerTile extends MachineTile implements MultiblockPort, MultiblockCore.House.Holder {

    public static final int ACTION_OPEN = CUSTOM;
    /**
     * Lot 8c : interface en VRAIE v2 (196 de large) : carte de la chambre a gauche, case du noyau en haut a droite, ecran d'etat raccourci
     * et bouton « Ouvrir la chambre » sur la rangee de reglage (geometrie partagee avec la vue {@code CosmicViews.CONTROLLER} et le banc).
     */
    public static final int CORE_X = StandardLayout.WIDTH - 8 - 17, CORE_Y = StandardLayout.TOP + 2;
    public static final StandardLayout.Rect CARD = new StandardLayout.Rect(8, StandardLayout.TOP, CORE_X - 1 - 6 - 8, StandardLayout.GAUGE_H),
          OPEN = StandardLayout.settingRow(StandardLayout.WIDTH);

    public final ItemSlot coreSlot;
    /**
     * Lot 8a : cycle de vie commun des multiblocs maison (verification chaque seconde ou a la demande, revalidee au chargement) : le
     * controleur suit la chambre (son voyant s'allume quand elle est prete a recevoir un noyau) ; la chambre formee est tenue par le noyau.
     */
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
        public Direction front() {
            BlockPos center = level == null ? worldPosition : StabilizerStructure.controllerCenter(level, worldPosition);
            Direction toward = Direction.getNearest(center.getX() - worldPosition.getX(), 0, center.getZ() - worldPosition.getZ());
            return toward.getAxis().isHorizontal() ? toward.getOpposite() : Direction.NORTH;
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            if (!(level instanceof ServerLevel server)) {
                return null;
            }
            BlockPos center = StabilizerStructure.controllerCenter(server, worldPosition);
            if (server.getBlockEntity(center) instanceof StabilizerCoreTile core) {
                core.house().checkNow();
            }
            StabilizerStructure.Result r = StabilizerStructure.validate(server, center);
            if (r == null) {
                return null;
            }
            //Les cases de la chambre formee sont inscrites par le noyau (hote des pieces) : le controleur n'en inscrit aucune
            return r.valid() ? MultiblockCore.House.Survey.ok(List.of(), r) : MultiblockCore.House.Survey.fail(r.error(), r.at(), r);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            light(true);
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            if (!survey.removal()) {
                light(false);
            }
        }

        @Override
        public void houseChanged() {
            syncNow();
        }
    }, 20);

    /** Voyant du controleur sans noyau (avec un noyau, la chambre peinte par le noyau l'allume). */
    private void light(boolean on) {
        if (level instanceof ServerLevel server && core() == null) {
            StabilizerStructure.lightController(server, worldPosition, on);
        }
    }

    public StabilizerControllerTile(BlockPos pos, BlockState state) {
        super(PiStabilizer.CONTROLLER_TILE.get(), pos, state, 1, 0);
        coreSlot = slot(new ItemSlot(() -> 16, StabilizerControllerTile::isCore, (stack, automation) -> true, (stack, automation) -> true, this::changed,
              CORE_X, CORE_Y).ghost(() -> new ItemStack(PiStabilizer.SINGULARITY_CORE.get())));
    }

    static boolean isCore(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof StabilizerCoreBlock;
    }

    private void changed() {
        setChanged();
        markSync();
    }

    @Nullable
    private StabilizerCoreTile core() {
        if (level == null) {
            return null;
        }
        return level.getBlockEntity(StabilizerStructure.controllerCenter(level, worldPosition)) instanceof StabilizerCoreTile core ? core : null;
    }

    @Override
    protected void tick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        house.tick();
        if ((level.getGameTime() + worldPosition.asLong()) % 20 != 0) {
            return;
        }
        boolean formed = house.formed();
        ItemStack stack = coreSlot.getStack();
        if (!formed || stack.isEmpty() || !(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof StabilizerCoreBlock fresh)) {
            return;
        }
        BlockPos center = StabilizerStructure.controllerCenter(server, worldPosition);
        StabilizerCoreTile core = core();
        boolean done;
        if (core == null) {
            done = server.getBlockState(center).isAir();
            if (done) {
                StabilizerStructure.placeCore(server, center, fresh);
            }
        } else {
            done = StabilizerStructure.swapCore(server, core, fresh);
        }
        if (done) {
            stack.shrink(1);
            coreSlot.setStack(stack);
            changed();
        }
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        StabilizerCoreTile core = core();
        if (action == ACTION_OPEN && core != null && core.isFormed()) {
            NetworkHooks.openScreen(player, core, core.getBlockPos());
        }
    }

    public boolean isFormed() {
        return house.formed();
    }

    /** Erreur au format d'avant ({@code cle|x y z}). */
    public String getError() {
        return house.legacyError();
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
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

    /** Rien ne passe par ses faces (ni energie, ni objets). */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap,
          @Nullable Direction side) {
        return side != null ? LazyOptional.empty() : super.getCapability(cap, side);
    }

    /** Lot 8a : valeurs d'interface NOMMEES (memes indices qu'avant : 4 et suivants, apres les 4 communes). */
    public static final UiValues.Key V_FORMED = UiValues.Key.of("formed"), V_HAS_CORE = UiValues.Key.of("has_core"),
          V_SPENT = UiValues.Key.of("spent"), V_REGIME = UiValues.Key.of("regime"), V_STATE = UiValues.Key.of("state");
    private static final UiValues.Key[] VALUES = {V_FORMED, V_HAS_CORE, V_SPENT, V_REGIME, V_STATE};

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        for (int i = 0; i < VALUES.length; i++) {
            int k = i;
            values.add(VALUES[i], () -> machineValue(k));
        }
    }

    @Override
    protected int machineValueCount() {
        return 5;
    }

    @Override
    protected long machineValue(int index) {
        StabilizerCoreTile core = core();
        return switch (index) {
            case 0 -> isFormed() ? 1 : 0;
            case 1 -> core != null ? 1 : 0;
            case 2 -> core != null && core.isSpent() ? 1 : 0;
            case 3 -> core == null ? 0 : core.getRegime().ordinal() + 1;
            default -> core == null ? 0 : core.getState().ordinal();
        };
    }

    @Nullable
    @Override
    public SideConfig getSideConfig() {
        return null;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    /** Lot 8c : interface en vraie v2 (196 de large, inventaire dans le cadre, onglets a droite, Infos et Structure). */
    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public java.util.List<StandardLayout.Part> guiParts() {
        return List.of(new StandardLayout.Part("card", CARD), new StandardLayout.Part("screen", StandardLayout.shortScreen(StandardLayout.WIDTH)),
              new StandardLayout.Part("setting", OPEN));
    }

    @Override
    public String guiTier() {
        StabilizerCoreTile core = core();
        return core == null ? "space_stabilizer_none" : "space_stabilizer_" + core.getRegime().key();
    }

    @Override
    public double guiReach() {
        return 16;
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.pioneers.space_stabilizer_controller");
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
    }

    /**
     * Lot 8c : CONTRAT DE PORT COMMUN ({@link MultiblockPort#portHandler}) : un port commun pose dans la structure formee prete la capacite
     * interne de la maison ({@link MultiblockPort#capabilityOf}) ; les ports propres de la maison gardent leurs gestionnaires.
     */
    @Nullable
    @Override
    public Object portHandler(fr.pioneers.tech.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() ? MultiblockPort.capabilityOf(this, type) : null;
    }
}
