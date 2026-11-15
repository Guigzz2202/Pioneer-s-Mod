package fr.pioneers.farm.device;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.kitchen.StationTiles;
import fr.pioneers.farm.ui.FarmLayout;
import fr.pioneers.farm.ui.FarmMachine;
import fr.pioneers.farm.ui.FarmSlot;
import fr.pioneers.farm.ui.FluidPair;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 41, retour 60 de Guillaume : « Ajout d'interfaces sur les machines de fermes (plus d'interactions directes, une vraie
 * interface et fin de la bulle d'info, car l'interface est explicite) ») : appareil de la ferme animee A INTERFACE (arroseurs, composteur,
 * station meteo, ruche, pondoir, couveuse, distributeur automatique, clapier) : interface standard v2 de l'API Machine ({@link MachineGui},
 * vue {@code farm}) : cases vues sur {@link #items} (sauvegarde inchangee, cases nouvelles en fin de liste), cuve d'eau et sa paire
 * « Vider » (seau d'eau ; avant : au clic), ecran d'etat = contenu de l'ancienne fiche au regard (retiree), faces reglables (objets, eau ;
 * cle {@code farm_sides} ; absente : comme avant), onglet Infos. Clic droit = l'interface ({@code DeviceBlock}) ; geste propre en monde
 * seulement s'il est impossible dans une interface ({@link #worldGesture}).
 */
public abstract class FarmDeviceTile extends DeviceTile implements MachineGui, MenuProvider, FarmMachine {

    public static final String SIDES_NBT = "farm_sides";
    private static final Map<DeviceBlock.Kind, FarmLayout> LAYOUTS = new ConcurrentHashMap<>();
    private final SideConfig sides;
    @Nullable
    private List<ItemSlot> gui;
    @Nullable
    private UiValues named;
    private boolean pairing;

    protected FarmDeviceTile(BlockPos pos, BlockState state, int slots, int tankCapacity) {
        super(pos, state, slots, tankCapacity);
        sides = new SideConfig(EnumSet.allOf(SideMode.class), SideMode.IO, false, this::facing, this::sidesChanged);
        if (slots > 0) {
            sides.addType(SideType.ITEMS);
        }
        if (tankCapacity > 0) {
            sides.addType(SideType.FLUIDS);
        }
    }

    private void sidesChanged() {
        setChanged();
        invalidateSided();
        sync();
    }

    @Override
    protected SideMode sideMode(SideType type, @Nullable Direction side) {
        return sides.mode(type, side);
    }

    // ------------------------------------------------------------------
    // Disposition et cases
    // ------------------------------------------------------------------

    /** Disposition propre de la sorte d'appareil (construite une fois par sorte). */
    protected abstract FarmLayout layout();

    @Override
    public FarmLayout farmLayout() {
        return LAYOUTS.computeIfAbsent(kind(), k -> layout());
    }

    /** Le joueur peut-il poser cet objet dans la case {@code slot} ? Par defaut : la regle des entonnoirs (sans face). */
    protected boolean acceptsInput(int slot, ItemStack stack) {
        return canInsert(slot, stack, null);
    }

    /** Le joueur peut-il reprendre la case {@code slot} dans l'interface ? */
    protected boolean playerTakes(int slot) {
        return true;
    }

    /** Objet fantome d'une case vide. */
    protected ItemStack ghost(FarmLayout.SlotSpec spec) {
        return spec.role() == FarmLayout.Role.DRAIN_IN ? new ItemStack(Items.WATER_BUCKET) : ItemStack.EMPTY;
    }

    private boolean filter(FarmLayout.SlotSpec spec, ItemStack stack) {
        return switch (spec.role()) {
            case DRAIN_IN -> FluidPair.drainable(stack) && tank != null && tank.isFluidValid(FluidPair.contained(stack));
            case TOOL -> stack.is(StationTiles.KNIVES);
            case OUT, FILL_OUT, DRAIN_OUT, SERVE_OUT -> false;
            default -> acceptsInput(spec.index(), stack);
        };
    }

    private int manualLimit(FarmLayout.SlotSpec spec) {
        return switch (spec.role()) {
            case TOOL -> 1;
            case DRAIN_IN -> 16;
            case CELL -> Math.max(1, slotLimit(spec.index()));
            default -> guiLimit(spec.index());
        };
    }

    /** Limite d'une case dans l'interface (par defaut : celle des entonnoirs). */
    protected int guiLimit(int slot) {
        return slotLimit(slot);
    }

    private static boolean playerPuts(FarmLayout.Role role) {
        return switch (role) {
            case OUT, FILL_OUT, DRAIN_OUT, SERVE_OUT -> false;
            default -> true;
        };
    }

    @Override
    public List<ItemSlot> guiSlots() {
        if (gui == null) {
            List<ItemSlot> out = new ArrayList<>();
            for (FarmLayout.SlotSpec spec : farmLayout().slots) {
                if (spec.index() >= items.size()) {
                    continue;
                }
                int index = spec.index();
                FarmSlot slot = new FarmSlot(items, index, spec.role(), () -> manualLimit(spec), s -> filter(spec, s),
                      (s, a) -> a == Automation.MANUAL ? playerPuts(spec.role()) : canInsert(index, s, null),
                      (s, a) -> a == Automation.MANUAL ? playerTakes(index) : canExtract(index, null), this::slotChanged, spec.x(), spec.y());
                ItemStack g = ghost(spec);
                if (!g.isEmpty()) {
                    slot.ghost(() -> g);
                }
                out.add(slot);
            }
            gui = List.copyOf(out);
        }
        return gui;
    }

    /** Une case de l'interface a change (joueur) : meme suite qu'un entonnoir. */
    private void slotChanged() {
        if (level != null && !level.isClientSide) {
            itemsChanged();
        } else {
            setChanged();
        }
    }

    @Override
    protected void itemsChanged() {
        if (!pairing && level != null && !level.isClientSide) {
            pairing = true;
            try {
                pairs();
            } finally {
                pairing = false;
            }
        }
        super.itemsChanged();
    }

    /** Paire « Vider » : seaux d'eau vers la cuve, seaux vides rendus (au changement du contenu seulement). */
    protected void pairs() {
        FarmLayout l = farmLayout();
        FarmLayout.SlotSpec di = l.slot(FarmLayout.Role.DRAIN_IN), dout = l.slot(FarmLayout.Role.DRAIN_OUT);
        if (di != null && dout != null && tank != null) {
            ItemStack[] pair = {items.get(di.index()), items.get(dout.index())};
            if (FluidPair.drainAll(tank, pair)) {
                items.set(di.index(), pair[0]);
                items.set(dout.index(), pair[1]);
                sound(SoundEvents.BUCKET_EMPTY, 0.6F, 1.1F);
            }
        }
    }

    // ------------------------------------------------------------------
    // Interface standard v2
    // ------------------------------------------------------------------

    @Override
    public FarmCard screenState() {
        FarmCard c = card(false);
        if (c == null) {
            c = FarmCard.of(ItemStack.EMPTY, getBlockState().getBlock().getName(), accent());
        }
        c.lines().removeIf(line -> line.kind() == FarmCard.Kind.ITEMS || line.kind() == FarmCard.Kind.HINT);
        return c;
    }

    @Nullable
    @Override
    public FluidTank farmTank(int which) {
        return tank;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return farmLayout().width;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return farmLayout().parts();
    }

    @Override
    public UiValues uiValues() {
        if (named == null) {
            UiValues.Builder b = UiValues.builder();
            b.add(UiValues.PROGRESS_X1000, () -> Math.round(arrowProgress() * 1000));
            b.add(UiValues.WATER, () -> tank == null ? 0 : tank.getFluidAmount());
            List<FarmLayout.SlotSpec> cells = farmLayout().slots(FarmLayout.Role.CELL);
            for (int i = 0; i < cells.size(); i++) {
                int index = cells.get(i).index();
                b.add(UiValues.item("farm_cell", i), () -> Math.round(cellProgress(index) * 1000));
            }
            declareValues(b);
            named = b.build();
        }
        return named;
    }

    /** Valeurs propres de la sorte d'appareil. */
    protected void declareValues(UiValues.Builder b) {
    }

    @Override
    public int guiValueCount() {
        return uiValues().size();
    }

    @Override
    public long guiValue(int index) {
        return uiValues().get(index);
    }

    @Nullable
    @Override
    public SideConfig getSideConfig() {
        //Station meteo : ni case ni cuve, pas d'onglet des faces
        return sides.types().isEmpty() ? null : sides;
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        int button = action - CUSTOM;
        if (button >= 0 && button < farmLayout().buttons.size()) {
            button(player, button);
        }
    }

    /** Bouton {@code button} de l'interface (serveur). */
    protected void button(ServerPlayer player, int button) {
    }

    /**
     * Geste en monde garde (avant l'interface) : vrai s'il a ete fait. Seul cas : les lapins tenus en laisse entrent au clapier (des
     * entites : impossible dans une interface).
     */
    public boolean worldGesture(Player player) {
        return false;
    }

    /** L'interface vient de s'ouvrir (serveur) : ruche ouverte sans fumee = piqure. */
    public void opened(Player player) {
    }

    @Override
    public @NotNull Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return standardMenu(id, inventory);
    }

    @Override
    public List<Object[]> infoRows() {
        List<Object[]> rows = new ArrayList<>();
        if (tank != null) {
            rows.add(new Object[]{"farm_tank_in", FarmCard.millibuckets(tank.getCapacity())});
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(SIDES_NBT, sides.save());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(SIDES_NBT)) {
            sides.load(tag.getCompound(SIDES_NBT));
        }
    }
}
