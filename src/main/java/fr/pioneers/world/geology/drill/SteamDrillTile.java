package fr.pioneers.world.geology.drill;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.BronzeTile;
import fr.pioneers.tech.bronze.steam.SteamVolume;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.world.geology.DepositData;
import fr.pioneers.world.geology.Deposits;
import fr.pioneers.tech.industry.construction.StackedColumnBlock;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.core.registries.PiBronze;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Foreuse a vapeur (niveau 1), le controleur du multibloc. 1.0.4-beta (lot 24) : structure de Guillaume ({@link HouseNbt}) : le controleur
 * (bloc plein) au milieu du devant d'un socle de 3 x 3 (socles, dont au moins une prise : vapeur et sortie des minerais), le train de forage
 * en bronze au centre, la tour de derricks et la couronne. Posee sur un gisement de niveau 1, elle en tire le minerai brut tant que la
 * reserve dure ; 1.0.2-beta (lot 3, plus de pression) : un minerai toutes les 3 s pour 24 mB de vapeur par tick ({@link DrillTier}).
 */
public class SteamDrillTile extends BronzeTile implements MultiblockCore.House.Holder, MultiblockPort {

    public static final int TOWER = 3;
    /** 1.0.4-beta (lot 24) : cle de la structure de Guillaume ({@link HouseNbt}). */
    public static final String SHAPE = "steam_drill";
    public static final int OK = 0, UNFORMED = 1, NO_PORT = 2, NO_DEPOSIT = 3, TOO_DEEP = 4, EXHAUSTED = 5, NO_STEAM = 6, FULL = 7;
    /** Lot 8b (vraie v2) : manometre a la place de l'energie motrice, carte du gisement, fleche, 9 sorties contre le bord droit. */
    public static final int OUT_X = StandardLayout.POST_SLOTS_X, OUT_Y = StandardLayout.POST_SLOTS_Y;
    public static final StandardLayout.Rect CARD = StandardLayout.ageCard(26, 70), ARROW = new StandardLayout.Rect(100, StandardLayout.TOP + 26, 28, 8);
    /** Lot 8b : valeurs nommees aux indices d'avant. */
    public static final UiValues.Key LEFT = UiValues.Key.of("left"), RESERVE = UiValues.Key.of("reserve"), TICKS = UiValues.Key.of("ticks");

    private final DrillTier tier = DrillTier.STEAM;
    private final SteamVolume chest = new SteamVolume(fr.pioneers.tech.bronze.steam.SteamRules.DRILL_RESERVE);
    private final List<ItemSlot> out = new ArrayList<>();
    private final List<BlockPos> ports = new ArrayList<>();
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
            BlockPos fault = fault(Structure.View.of(level), worldPosition, facing());
            if (fault != null) {
                return MultiblockCore.House.Survey.fail("gui.pioneers.multiblock.house.drill", fault, null);
            }
            return MultiblockCore.House.Survey.ok(HouseNbt.members(worldPosition, facing(), SHAPE), null);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            ports.clear();
            for (BlockPos p : HouseNbt.positions(worldPosition, facing(), SHAPE, "b")) {
                if (level.getBlockState(p).getBlock() == PiBronze.DRILL_PORT.getBlock()) {
                    ports.add(p);
                }
            }
            if (first) {
                BronzeEvents.award((ServerLevel) level, worldPosition, "drill_formed");
            }
            hideString(true);
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            ports.clear();
            hideString(false);
        }

        @Override
        public void houseChanged() {
            markClient();
        }
    }, 40);
    private int status = UNFORMED, left, reserve, ticks = 120, check;
    private double progress;
    @Nullable
    private String depositKey;
    /** 1.0.4-beta (lot 25) : train de forage deja repeint depuis le chargement. */
    private boolean stringChecked;
    //Client
    private boolean clientWorking;

    public SteamDrillTile(BlockPos pos, BlockState state) {
        super(PiBronze.STEAM_DRILL_TILE.get(), pos, state);
        for (int i = 0; i < 9; i++) {
            out.add(slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, OUT_X + (i % 3) * 18, OUT_Y + (i / 3) * 18)));
        }
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

    public SteamVolume chest() {
        return chest;
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    /**
     * API Multibloc (lot 6) : premiere case fautive du plan de la foreuse, null si la structure est complete ; monde reel ou factice (banc des
     * multiblocs) : meme regle pour la tile et le banc. 1.0.4-beta (lot 24) : structure de Guillaume ({@link HouseNbt}, {@code steam_drill}) :
     * le controleur (bloc plein) au milieu du devant du socle, 7 socles (ou prises) autour du train de forage EN BRONZE (4 blocs, au centre),
     * 4 derricks a chaque niveau autour de lui, le mat de 2 derricks et la couronne.
     */
    @Nullable
    public static BlockPos fault(Structure.View level, BlockPos pos, Direction front) {
        return HouseNbt.fault(level, pos, front, SHAPE);
    }

    /** 1.0.4-beta (lot 24) : facade du controleur (le plan est derriere lui). */
    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(fr.pioneers.tech.bronze.BronzeBlock.FACING) ? s.getValue(fr.pioneers.tech.bronze.BronzeBlock.FACING) : Direction.NORTH;
    }

    /** 1.0.4-beta (lot 24) : pied du train de forage (le centre de la foreuse : gisement, poussiere). */
    public BlockPos center() {
        HouseNbt.Cell c = HouseNbt.first(SHAPE, 'D');
        return c == null ? worldPosition : HouseNbt.at(worldPosition, facing(), c);
    }

    // ------------------------------------------------------------------
    // Travail
    // ------------------------------------------------------------------

    /**
     * 1.0.4-beta (lot 25) : train de forage en bronze de la structure formee : masque (le controleur le dessine, tournant quand la foreuse
     * travaille : {@code client/bronze/DrillRenderer}), de nouveau dessine par le monde a la rupture.
     */
    private void hideString(boolean hidden) {
        stringChecked = true;
        if (level == null) {
            return;
        }
        for (BlockPos p : HouseNbt.positions(worldPosition, facing(), SHAPE, "D")) {
            BlockState st = level.isLoaded(p) ? level.getBlockState(p) : null;
            if (st != null && st.getBlock() instanceof StackedColumnBlock && st.getValue(StackedColumnBlock.HIDDEN) != hidden) {
                level.setBlock(p, st.setValue(StackedColumnBlock.HIDDEN, hidden), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void serverTick(ServerLevel level) {
        house.tick();
        if (!stringChecked && !house.formed()) {
            //1.0.4-beta (lot 25) : premiere verification apres le chargement, structure perdue : train de nouveau dessine par le monde
            hideString(false);
        }
        int was = status;
        boolean working = false;
        BlockPos centre = center();
        Deposits.Deposit deposit = Deposits.at(level, centre.getX() >> 4, centre.getZ() >> 4);
        String key = deposit == null ? null : deposit.type().translationKey();
        if (!Objects.equals(key, depositKey)) {
            depositKey = key;
            markClient();
        }
        left = deposit == null ? 0 : deposit.left(level);
        reserve = deposit == null ? 0 : deposit.reserve();
        if (!house.formed()) {
            status = UNFORMED;
        } else if (ports.isEmpty()) {
            status = NO_PORT;
        } else if (deposit == null) {
            status = NO_DEPOSIT;
        } else if (deposit.type().tier() > tier.level) {
            status = TOO_DEEP;
        } else if (left <= 0) {
            status = EXHAUSTED;
        } else if (!hasRoom(new ItemStack(deposit.type().result()))) {
            status = FULL;
        } else if (chest.steam() < tier.steamPerTick) {
            status = NO_STEAM;
        } else {
            status = OK;
            ticks = tier.ticksPerOre;
            chest.take(tier.steamPerTick);
            progress += 1.0 / ticks;
            working = true;
            if (progress >= 1) {
                progress = 0;
                ItemStack ore = new ItemStack(deposit.type().result());
                for (ItemSlot slot : out) {
                    ore = slot.insert(ore, Action.EXECUTE, Automation.INTERNAL);
                    if (ore.isEmpty()) {
                        break;
                    }
                }
                DepositData.get(level).mine(deposit.region(), 1);
                BronzeEvents.award(level, worldPosition, "drill_ore");
                setChanged();
            }
            if (level.getGameTime() % 32 == 0) {
                level.playSound(null, worldPosition, PiBronze.SOUND_DRILL.get(), SoundSource.BLOCKS, 0.6F, 0.8F + (float) (chest.fill() * 0.3));
            }
            if (level.getGameTime() % 6 == 0) {
                //1.0.4-beta (lot 24) : la poussiere sort au pied du train de forage (centre de la foreuse)
                BlockState dust = level.getBlockState(centre.below());
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust.isAir() ? Blocks.STONE.defaultBlockState() : dust),
                      centre.getX() + 0.5, centre.getY() + 0.1, centre.getZ() + 0.5, 3, 0.45, 0.05, 0.45, 0.05);
            }
        }
        if (working != clientWorking || was != status) {
            clientWorking = working;
            markClient();
        }
        flushClient(level, 20);
    }

    private boolean hasRoom(ItemStack stack) {
        for (ItemSlot slot : out) {
            if (slot.isEmpty() || ItemStack.isSameItemSameTags(slot.getStack(), stack) && slot.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Client : rotation de la tige
    // ------------------------------------------------------------------

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putBoolean("working", clientWorking);
        house.write(tag);
        if (depositKey != null) {
            tag.putString("deposit", depositKey);
        }
    }

    @Override
    protected void readClient(CompoundTag tag) {
        clientWorking = tag.getBoolean("working");
        house.read(tag);
        depositKey = tag.contains("deposit") ? tag.getString("deposit") : null;
    }

    public boolean clientWorking() {
        return clientWorking;
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos c = center();
        return new AABB(c).inflate(1, 0, 1).expandTowards(0, 7, 0).minmax(new AABB(worldPosition));
    }

    // ------------------------------------------------------------------
    // Interface : 0 etat, 1 reserve restante, 2 reserve totale, 3 pression x1000, 4 avancement x1000, 5 ticks par minerai
    // ------------------------------------------------------------------

    @Override
    protected boolean exposesItems(@Nullable Direction side) {
        return side == Direction.DOWN;
    }

    @Nullable
    public String depositKey() {
        return depositKey;
    }

    public Component depositName() {
        return depositKey == null ? Component.translatable("geology.pioneers.no_deposit") : Component.translatable(depositKey);
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    /** Lot 8a : interface standard v2 (inventaire dans le cadre, onglets a droite, Infos et Structure). */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public java.util.List<StandardLayout.Part> guiParts() {
        return StandardLayout.ageService(false, new StandardLayout.Part("card", CARD), new StandardLayout.Part("arrow", ARROW));
    }

    @Override
    public UiValues uiValues() {
        return namedIndices(UiValues.STATUS, LEFT, RESERVE, UiValues.STEAM, UiValues.PROGRESS_X1000, TICKS);
    }

    @Override
    public String guiTier() {
        return "bronze";
    }

    @Override
    public int guiValueCount() {
        return 6;
    }

    @Override
    public long guiValue(int index) {
        return switch (index) {
            case 0 -> status;
            case 1 -> left;
            case 2 -> reserve;
            case 3 -> Math.round(chest.steam());
            case 4 -> Math.round(progress * 1000);
            default -> ticks;
        };
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        chest.save(tag);
        tag.putDouble("progress", progress);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        chest.load(tag);
        progress = tag.getDouble("progress");
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
