package fr.pioneers.bronze.autofoundry;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.multiblock.FurnaceHeat;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.bronze.AirBlastReceiver;
import fr.pioneers.bronze.BronzeBlock;
import fr.pioneers.bronze.BronzeEvents;
import fr.pioneers.bronze.BronzeTile;
import fr.pioneers.bronze.HouseControllerBlock;
import fr.pioneers.bronze.LitBronzeBlock;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.industry.multi.Structure;
import fr.pioneers.registries.PiBronze;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 4, notes de Guillaume « Developper un nouveau systeme de forge entierement automatique » ; reponses du 04/11) : CONTROLEUR
 * de la FONDERIE AUTOMATIQUE, la porte du foyer au pied de la cuve. Multibloc maison sur l'API Multibloc : cycle de vie commun
 * ({@link MultiblockCore.House} : verification periodique et a la demande, revalidation au chargement, registre des hotes pour les pieces,
 * erreur commune quoi / ou / attendu), plan tire de {@link FoundryLayout} (trois tailles 1 x 1 x 2, 2 x 2 x 3, 3 x 3 x 5 : la plus grande
 * complete se forme), onglet Structure. La tile PORTE son unite ({@link FoundryUnit} : foyer, fusion, bain, selecteur d'alliage, coulee par
 * les conduits) et garde le cycle, la peinture des pieces (cuve qui rougeoie, bain sous la grille du dessus, foyers qui flambent), l'air
 * des soufflets et le contrat de port commun.
 * <p>Vient APRES le fourneau a creuset et la table de coulee (qui restent : la coulee a la main d'abord, la fonderie automatise ensuite).
 */
public class AutoFoundryTile extends BronzeTile implements MultiblockCore.House.Holder, MultiblockPort, AirBlastReceiver, RecipeLockable {

    /** Actions : interrupteur « Auto », 1.0.3-beta (lot 7b) « Vider le bain ». */
    public static final int AUTO = CUSTOM, DUMP = CUSTOM + 1;
    private final FoundryUnit unit;
    private final PowerSource.Fuel fire;
    /** Structure formee (null : non formee). */
    @Nullable
    private FoundryLayout.Found found;
    /** Cases peintes (pour depeindre celles qui sortent de la structure). */
    private final Set<BlockPos> painted = new HashSet<>();
    private int heatLevel = -1, fireLevel = -1;
    /** Client : taille de la structure formee (-1 : non formee). */
    private int clientSize = -1;
    private final MultiblockCore.House house = new MultiblockCore.House(new MultiblockCore.House.Host() {
        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public Direction front() {
            return facing();
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            if (level == null) {
                return null;
            }
            FoundryLayout.Survey s = FoundryLayout.survey(Structure.View.of(level), worldPosition, facing());
            if (s.unreadable()) {
                return null;
            }
            if (s.found() == null) {
                return MultiblockCore.House.Survey.fail(ERROR, s.fault(), s);
            }
            return MultiblockCore.House.Survey.ok(s.found().cells(), s);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            FoundryLayout.Found f = survey.data() instanceof FoundryLayout.Survey s ? s.found() : null;
            boolean resized = found != null && f != null && found.size() != f.size();
            found = f;
            if (first && level instanceof ServerLevel server) {
                BronzeEvents.award(server, worldPosition, "auto_foundry_formed");
            }
            if (first || resized) {
                heatLevel = fireLevel = -1;
                markClient();
            }
            repaint();
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            found = null;
            if (broken) {
                unit.abortPour();
                if (!survey.removal()) {
                    repaint();
                }
                markClient();
            }
        }

        @Override
        public void houseChanged() {
            markClient();
        }
    }, 20);

    /** Cle de l'erreur commune de la fonderie (quoi). */
    public static final String ERROR = "gui.pioneers.multiblock.house.auto_foundry";

    public AutoFoundryTile(BlockPos pos, BlockState state) {
        super(PiBronze.AUTO_FOUNDRY_TILE.get(), pos, state);
        unit = carry(new FoundryUnit(host(), this));
        fire = unit.power();
    }

    @Override
    protected PowerSource power() {
        return fire;
    }

    public FoundryUnit foundry() {
        return unit;
    }

    public Direction facing() {
        return getBlockState().getValue(BronzeBlock.FACING);
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    public boolean formed() {
        return level != null && level.isClientSide ? clientSize >= 0 : house.formed() && found != null;
    }

    /** Taille de la structure formee (petite si non formee). */
    public FoundryLayout.Size size() {
        if (level != null && level.isClientSide) {
            return FoundryLayout.Size.of(Math.max(0, clientSize));
        }
        return found == null ? FoundryLayout.Size.SMALL : found.size();
    }

    /** Cases de cuve de la structure formee (sources des conduits). */
    public List<BlockPos> vats() {
        return found == null ? List.of() : found.vats();
    }

    /** Capacite d'objets de la fonderie (pretee par les pieces aux entonnoirs et au cable). */
    public LazyOptional<?> inputCapability() {
        return getCapability(ForgeCapabilities.ITEM_HANDLER, null);
    }

    @Override
    public void blast(double strength) {
        unit.blast(strength);
    }

    /** Peint les pieces : cuve (chaleur, dessus), foyers (feu) ; depeint celles qui ne sont plus dans la structure. */
    private void repaint() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Set<BlockPos> now = new HashSet<>();
        if (found != null) {
            int heat = heatOf(unit.temperature());
            int flame = unit.lit() ? unit.air() > 0.2 ? 2 : 1 : 0;
            Set<BlockPos> top = new HashSet<>(found.top());
            for (BlockPos p : found.vats()) {
                FoundryPartBlock.paint(server, p, heat, top.contains(p));
                now.add(p);
            }
            //1.0.5-beta (lot 23, chauffe homogene) : les foyers prennent le palier de la cuve (avant : celui du feu, au soufflet) ; tuyeres et
            //controleur aussi
            for (BlockPos p : found.fireboxes()) {
                FoundryPartBlock.paint(server, p, heat, false);
                now.add(p);
            }
            fr.pioneers.bronze.HeatPalette.paintAll(server, found.tuyeres(), heat);
            fr.pioneers.bronze.HeatPalette.paint(server, worldPosition, heat);
            heatLevel = heat;
            fireLevel = flame;
        }
        for (BlockPos p : painted) {
            if (!now.contains(p) && server.isLoaded(p)) {
                FoundryPartBlock.paint(server, p, 0, false);
                fr.pioneers.bronze.HeatPalette.paint(server, p, 0);
            }
        }
        if (found == null) {
            fr.pioneers.bronze.HeatPalette.paint(server, worldPosition, 0);
        } else {
            now.addAll(found.tuyeres());
        }
        painted.clear();
        painted.addAll(now);
    }

    /** Chaleur peinte de la cuve : 0 froide (moins de 300 degres), 1 tiede, 2 au rouge (des 900). */
    public static int heatOf(double celsius) {
        return celsius < 300 ? 0 : celsius < 900 ? 1 : 2;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void serverTick(ServerLevel level) {
        house.tick();
        //1.0.5-beta (lot 15) : controleur connecte a la paroi formee (habillage des foyers voisins)
        HouseControllerBlock.sync(level, worldPosition, house.formed());
        //1.0.4-beta (lot 24) : la tuyere de la grande fonderie (forme de Guillaume) passe l'air de son soufflet au foyer
        if (found != null) {
            for (BlockPos p : found.tuyeres()) {
                if (level.isLoaded(p) && level.getBlockEntity(p) instanceof fr.pioneers.bronze.blast.TuyereTile tuyere) {
                    double air = tuyere.drain();
                    if (air > 0) {
                        unit.blast(air);
                    }
                }
            }
        }
        unit.tick();
        if (found != null && level.getGameTime() % 20 == 0) {
            int flame = unit.lit() ? unit.air() > 0.2 ? 2 : 1 : 0;
            if (heatOf(unit.temperature()) != heatLevel || flame != fireLevel) {
                repaint();
            }
        }
        LitBronzeBlock.setLit(level, worldPosition, getBlockState(), unit.lit() && formed());
        //1.0.5-beta (lot 13) : qui marche sur le bain d'une fonderie allumee se brule (comme sur un bloc de magma)
        if (found != null && FurnaceHeat.due(level, worldPosition)) {
            FurnaceHeat.onTop(level, found.top(), unit.lit() && formed());
        }
        flushClient(level, 10);
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
    public void handleCustom(ServerPlayer player, int action) {
        if (action == AUTO) {
            unit.setAutoPour(!unit.autoPour());
        } else if (action == DUMP && (unit.total() > 0 || unit.pouring())) {
            //1.0.3-beta (lot 7b) : sortie de secours : le bain (et la coulee en cours) en lingots, pepites et scorie, au joueur
            int mb = unit.total();
            for (ItemStack s : unit.dump()) {
                if (!player.getInventory().add(s)) {
                    player.drop(s, false);
                }
            }
            player.displayClientMessage(Component.translatable("foundry.pioneers.dumped", mb).withStyle(ChatFormatting.GOLD), true);
        }
    }

    @Override
    public void dropContents(Consumer<ItemStack> drop) {
        super.dropContents(drop);
        unit.spill().forEach(drop);
        house.removed(false);
        if (level instanceof ServerLevel) {
            found = null;
            repaint();
        }
    }

    // ------------------------------------------------------------------
    // Client
    // ------------------------------------------------------------------

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
        tag.putInt("size", formed() ? size().ordinal() : -1);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
        clientSize = tag.contains("size") ? tag.getInt("size") : -1;
    }

    public Map<String, Integer> mix() {
        return unit.mix();
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3, 0, 3).expandTowards(0, 5, 0);
    }

    // ------------------------------------------------------------------
    // Interface standard v2 (corps dans la vue maison : voile et erreur commune non formee, onglet Structure)
    // ------------------------------------------------------------------

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        List<StandardLayout.Part> parts = new ArrayList<>();
        parts.add(new StandardLayout.Part("card", FoundryUnit.BATH));
        parts.add(new StandardLayout.Part("recipe", StandardLayout.slotFrame(FoundryUnit.RECIPE_X, FoundryUnit.RECIPE_Y)));
        for (int i = 0; i < FoundryUnit.METAL_SLOTS; i++) {
            int[] p = FoundryUnit.inPos(i);
            parts.add(new StandardLayout.Part("bar", new StandardLayout.Rect(p[0], p[1] + 17, 16, 2)));
        }
        return StandardLayout.ageService(true, parts.toArray(new StandardLayout.Part[0]));
    }

    // ------------------------------------------------------------------
    // Selecteur d'alliage
    // ------------------------------------------------------------------

    @Override
    public String recipeSource() {
        return FoundryUnit.SOURCE;
    }

    @Override
    public RecipeLock recipeLock() {
        return unit.lock;
    }

    @Override
    public void recipeLockChanged() {
        host().markSync();
        host().changed();
    }

    @Override
    public int[] recipeSlot() {
        return new int[]{FoundryUnit.RECIPE_X, FoundryUnit.RECIPE_Y};
    }

    @Override
    public int recipeTier() {
        return 0;
    }

    @Override
    public List<ItemStack> presentItems() {
        List<ItemStack> out = new ArrayList<>();
        unit.inputs.forEach(s -> {
            if (!s.isEmpty()) {
                out.add(s.getStack());
            }
        });
        return out;
    }

    @Override
    public Level recipeLevel() {
        return level;
    }

    /**
     * Contrat de port commun ({@link MultiblockPort#portHandler}) : un port commun pose dans la structure formee prete la capacite interne de
     * la fonderie ({@link MultiblockPort#capabilityOf}) ; les pieces (foyers, cuves) pretent deja ses cases aux entonnoirs.
     */
    @Nullable
    @Override
    public Object portHandler(fr.pioneers.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() ? MultiblockPort.capabilityOf(this, type) : null;
    }

    /** 1.0.4-beta (lot 25) : particules de la structure en marche (T:industry/nbt_models.py HOUSE_FX). */
    @Override
    public void clientTick() {
        houseFx("auto_foundry", lit());
    }
}
