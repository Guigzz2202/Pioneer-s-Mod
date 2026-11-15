package fr.pioneers.farm.kitchen;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.Automation;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.core.recipe.RecipeSources;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.ui.FarmLayout;
import fr.pioneers.farm.ui.FarmMachine;
import fr.pioneers.farm.ui.FarmSlot;
import fr.pioneers.farm.ui.FluidPair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
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
 * Base des postes de cuisine : cases (les {@code inputs} premieres sont des entrees, les suivantes des sorties), reservoirs facultatifs
 * (entree {@link #tank}, sortie {@link #out}), avancement, synchro client (etat complet a chaque changement, et toutes les secondes pendant
 * un travail ; le client fait avancer l'aiguille entre deux), capacites (entonnoirs : entrees par le haut et les cotes, sorties par le bas ;
 * tuyaux : remplissage de l'entree, vidange de la sortie).
 * <p>
 * 1.0.5-beta (lot 41, retour 60 de Guillaume : « une vraie interface et fin de la bulle d'info, car l'interface est explicite ») : INTERFACE
 * STANDARD v2 de l'API Machine ({@link MachineGui} : {@code MachineMenu} + {@code GuiMachine}, vue {@code farm}) : cases d'entree et de
 * sortie ({@link FarmSlot}, vues sur {@link #items} : sauvegarde inchangee), jauges des cuves, fleche, ecran d'etat (le contenu de l'ancienne
 * fiche au regard : {@link #screenState}), bouton du geste des postes manuels, onglets Recettes (selecteur {@link RecipeLockable} des postes a
 * plusieurs recettes), Faces (objets et liquides : {@link #sides}, cle {@code farm_sides} ; absente = toutes les faces en entree-sortie, comme
 * avant) et Infos. Plus de seau ni de bouteille au clic : paires de cases « Vider » et « Remplir » ({@link FluidPair}), traitees au
 * changement du contenu seulement. Clic droit = l'interface ; Maj + clic droit main vide = un geste ({@link #manual}) sur un poste manuel.
 */
public abstract class StationTile extends PiBlockEntity implements MachineGui, MenuProvider, RecipeLockable, FarmMachine {

    protected final NonNullList<ItemStack> items;
    protected final int inputs;
    @Nullable
    protected final FluidTank tank, out;
    /** Avancement (ticks ou gestes) et objectif ; {@link #working} : le temps court (le client avance seul). */
    protected int progress, need;
    protected boolean working;
    private int syncClock;
    /** Cote client : instant du dernier geste (avancement qui augmente : coup de couteau, de fouet, tour de vis), pour les animations. */
    public long lastStroke = -100;
    /** Nombre de gestes (synchronise : le client anime chaque geste, meme celui qui acheve la recette). */
    protected int strokes;
    /**
     * 1.6 (lot A2) : grand poste ({@link BigStationBlock}) : 1 = toutes ses cases sont posees ; 0 = poste pose en un bloc avant la 1.6, qui
     * attend la place pour grandir (il marche quand meme ; essai toutes les 5 s : {@link #structureRetry}). Sauve, synchronise (fiche).
     */
    int structure;
    int structureRetry;
    /** 1.0.5-beta (lot 41) : faces reglables (objets ; liquides si le poste a une cuve), verrou du selecteur. */
    private final SideConfig sides;
    private final RecipeLock lock = new RecipeLock();
    @Nullable
    private List<ItemSlot> gui;
    @Nullable
    private UiValues named;
    /** Paires « Vider » / « Remplir » en cours de traitement (garde : les cuves previennent le poste quand elles changent). */
    private boolean pairing;
    /** Dispositions par sorte de poste (meme objet des deux cotes). */
    private static final Map<StationBlock.Kind, FarmLayout> LAYOUTS = new ConcurrentHashMap<>();
    public static final String SIDES_NBT = "farm_sides";

    protected StationTile(BlockPos pos, BlockState state, int slots, int inputs, int tankCapacity, int outCapacity) {
        super(PiKitchen.STATION_TILE.get(), pos, state);
        this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
        this.inputs = inputs;
        this.tank = tankCapacity > 0 ? new FluidTank(tankCapacity, f -> canFill(f)) {
            @Override
            protected void onContentsChanged() {
                contentsChanged();
            }
        } : null;
        this.out = outCapacity > 0 ? new FluidTank(outCapacity) {
            @Override
            protected void onContentsChanged() {
                contentsChanged();
            }
        } : null;
        this.sides = new SideConfig(EnumSet.allOf(SideMode.class), SideMode.IO, false, this::facing, this::sideChanged);
        if (slots > 0) {
            sides.addType(SideType.ITEMS);
        }
        if (tankCapacity > 0 || outCapacity > 0) {
            sides.addType(SideType.FLUIDS);
        }
    }

    public StationBlock.Kind kind() {
        return getBlockState().getBlock() instanceof StationBlock b ? b.kind() : StationBlock.Kind.CUTTING_BOARD;
    }

    /** 1.6 (lot A2) : un grand poste se dessine sur toutes ses cases (sinon son rendu disparait des que la case principale sort de la vue). */
    @Override
    public AABB getRenderBoundingBox() {
        BlockState s = getBlockState();
        if (s.getBlock() instanceof BigStationBlock big) {
            return big.renderBox(worldPosition, facing());
        }
        return super.getRenderBoundingBox();
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    @Nullable
    public FluidTank tank() {
        return tank;
    }

    @Nullable
    public FluidTank outTank() {
        return out;
    }

    /** Nombre de gestes faits (synchronise ; animations des postes a manivelle). */
    public int strokes() {
        return strokes;
    }

    public int progress() {
        return progress;
    }

    public int need() {
        return need;
    }

    public boolean working() {
        return working;
    }

    public float fraction() {
        return need <= 0 ? 0 : Math.min(1F, progress / (float) need);
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(StationBlock.FACING) ? s.getValue(StationBlock.FACING) : Direction.NORTH;
    }

    /** Reservoir d'entree : seulement les fluides d'une recette du poste (un seau de lave n'y entre pas). */
    protected boolean canFill(FluidStack stack) {
        Station st = kind().station();
        if (st == null || level == null) {
            return true;
        }
        for (KitchenRecipe r : recipes(st)) {
            if (r.fluid() != null && r.fluid().test(new FluidStack(stack.getFluid(), r.fluid().amount()))) {
                return true;
            }
        }
        return false;
    }

    /** Contenu change : paires de contenants (serveur), recette a revoir, synchro. */
    protected void contentsChanged() {
        if (!pairing && level != null && !level.isClientSide) {
            pairing = true;
            try {
                pairs();
            } finally {
                pairing = false;
            }
        }
        sync();
    }

    private void sideChanged() {
        setChanged();
        invalidateSided();
    }

    // ------------------------------------------------------------------
    // Ticks
    // ------------------------------------------------------------------

    public void serverTick() {
        if (working && ++syncClock >= 20) {
            syncClock = 0;
            sync();
        }
    }

    public void clientTick() {
        if (working && need > 0 && progress < need) {
            progress++;
        }
    }

    /** Particules cote client (appele par le bloc). */
    public void particles(RandomSource random) {
    }

    // ------------------------------------------------------------------
    // Gestes (1.0.5-beta lot 41 : il ne reste que le geste des postes manuels)
    // ------------------------------------------------------------------

    /** Poste a geste manuel (Maj + clic droit main vide, bouton de l'interface) ? */
    public boolean manualGesture() {
        return kind().stroke();
    }

    /** Un geste manuel (bouton {@code button} de l'interface, ou Maj + clic droit main vide pour le bouton 0) ; serveur. */
    public void manual(Player player, int button) {
    }

    protected List<KitchenRecipe> recipes(Station station) {
        return level == null ? List.of() : level.getRecipeManager().getAllRecipesFor(PiKitchen.TYPES.get(station).get());
    }

    /** 1.0.5-beta (lot 41) : recette verrouillee par le selecteur pour ce poste (null : aucun verrou, ou recette disparue). */
    @Nullable
    protected KitchenRecipe lockedKitchen(Station station) {
        ResourceLocation id = lock.id();
        if (id == null) {
            return null;
        }
        for (KitchenRecipe r : recipes(station)) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    protected KitchenRecipe findSingle(Station station, ItemStack stack) {
        return findSingle(station, stack, false);
    }

    /**
     * 1.4 : recette a un seul ingredient ; quand plusieurs recettes prennent le meme objet (pomme de terre : batonnets ou plants), elles sont
     * rangees par identifiant et {@code alt} prend la suivante. 1.0.5-beta (lot 41) : une recette verrouillee par le selecteur passe avant
     * (elle seule, si elle prend l'objet).
     */
    protected KitchenRecipe findSingle(Station station, ItemStack stack, boolean alt) {
        if (stack.isEmpty()) {
            return null;
        }
        KitchenRecipe locked = lockedKitchen(station);
        if (locked != null) {
            return locked.matchesSingle(stack) ? locked : null;
        }
        List<KitchenRecipe> found = new ArrayList<>();
        for (KitchenRecipe r : recipes(station)) {
            if (r.matchesSingle(stack)) {
                found.add(r);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        found.sort(Comparator.comparing(r -> r.getId().toString()));
        return found.get(alt && found.size() > 1 ? 1 : 0);
    }

    /** 1.4 : nombre de recettes a un ingredient qui prennent cet objet. */
    protected int countSingle(Station station, ItemStack stack) {
        int n = 0;
        for (KitchenRecipe r : recipes(station)) {
            if (!stack.isEmpty() && r.matchesSingle(stack)) {
                n++;
            }
        }
        return n;
    }

    /** Recette dont les ingredients sont exactement les cases d'entree (et le fluide d'entree suffit) ; lot 41 : la verrouillee seule. */
    @Nullable
    protected KitchenRecipe findExact(Station station) {
        List<ItemStack> contents = new ArrayList<>(items.subList(0, inputs));
        FluidStack fluid = tank == null ? FluidStack.EMPTY : tank.getFluid();
        KitchenRecipe locked = lockedKitchen(station);
        if (locked != null) {
            return locked.matchesExact(contents) && locked.fluidOk(fluid) ? locked : null;
        }
        for (KitchenRecipe r : recipes(station)) {
            if (r.matchesExact(contents) && r.fluidOk(fluid)) {
                return r;
            }
        }
        return null;
    }

    /** Une recette peut-elle encore naitre de ces entrees (objets en plus refuses) ? */
    protected boolean anyCould(Station station, List<ItemStack> contents) {
        KitchenRecipe locked = lockedKitchen(station);
        if (locked != null) {
            return locked.couldBecome(contents);
        }
        for (KitchenRecipe r : recipes(station)) {
            if (r.couldBecome(contents)) {
                return true;
            }
        }
        return false;
    }

    /** Ces entrees (cases [0, {@code to}) + un objet de plus) peuvent-elles encore faire une recette du poste ? */
    protected boolean couldTake(Station station, int to, ItemStack stack) {
        List<ItemStack> next = new ArrayList<>();
        for (int i = 0; i < to; i++) {
            if (!items.get(i).isEmpty()) {
                next.add(items.get(i));
            }
        }
        //Meme objet qu'une case deja prise : il s'y empile (le controle porte sur le nombre)
        boolean merged = false;
        for (int i = 0; i < next.size(); i++) {
            if (ItemStack.isSameItemSameTags(next.get(i), stack)) {
                next.set(i, next.get(i).copyWithCount(next.get(i).getCount() + 1));
                merged = true;
                break;
            }
        }
        if (!merged) {
            next.add(stack.copyWithCount(1));
        }
        return anyCould(station, next);
    }

    protected void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }

    /** Sort les restes de recette (bouteilles, seaux) a cote du poste. */
    protected void remainders(List<ItemStack> consumed) {
        for (ItemStack s : consumed) {
            if (s.hasCraftingRemainingItem()) {
                ItemStack r = s.getCraftingRemainingItem();
                for (int i = 0; i < s.getCount(); i++) {
                    Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, r.copy());
                }
            }
        }
    }

    /** Pose un produit sur la case {@code slot} (vide ou meme objet) ; vrai si tout y est entre. */
    protected boolean put(int slot, ItemStack stack) {
        ItemStack o = items.get(slot);
        if (o.isEmpty()) {
            items.set(slot, stack.copy());
            return true;
        }
        if (ItemStack.isSameItemSameTags(o, stack) && o.getCount() + stack.getCount() <= o.getMaxStackSize()) {
            o.grow(stack.getCount());
            return true;
        }
        return false;
    }

    /** La case {@code slot} peut-elle recevoir {@code stack} (vide ou meme objet avec la place) ? */
    protected boolean room(int slot, ItemStack stack) {
        ItemStack o = items.get(slot);
        return o.isEmpty() || ItemStack.isSameItemSameTags(o, stack) && o.getCount() + stack.getCount() <= o.getMaxStackSize();
    }

    public void dropContents() {
        if (level != null && !level.isClientSide) {
            Containers.dropContents(level, worldPosition, items);
        }
    }

    // ------------------------------------------------------------------
    // 1.0.5-beta (lot 41) : paires de contenants (« Vider » vers la cuve d'entree, « Remplir » depuis la cuve de sortie, « Servir »)
    // ------------------------------------------------------------------

    /** Cuve que la paire « Vider » remplit (l'entree, sinon la cuve unique). */
    @Nullable
    protected FluidTank drainTank() {
        return tank != null ? tank : out;
    }

    /** Cuve que la paire « Remplir » vide (la sortie, sinon la cuve unique). */
    @Nullable
    protected FluidTank fillTank() {
        return out != null ? out : tank;
    }

    /** Traite les paires de la disposition (au changement du contenu, serveur). */
    protected void pairs() {
        FarmLayout l = farmLayout();
        boolean any = false;
        FarmLayout.SlotSpec fi = l.slot(FarmLayout.Role.FILL_IN), fo = l.slot(FarmLayout.Role.FILL_OUT);
        if (fi != null && fo != null) {
            ItemStack[] pair = {items.get(fi.index()), items.get(fo.index())};
            if (FluidPair.fillAll(fillTank(), pair)) {
                items.set(fi.index(), pair[0]);
                items.set(fo.index(), pair[1]);
                any = true;
                StationTiles.sound(this, SoundEvents.BOTTLE_FILL, 0.8F, 1F);
            }
        }
        FarmLayout.SlotSpec di = l.slot(FarmLayout.Role.DRAIN_IN), dout = l.slot(FarmLayout.Role.DRAIN_OUT);
        if (di != null && dout != null) {
            ItemStack[] pair = {items.get(di.index()), items.get(dout.index())};
            if (FluidPair.drainAll(drainTank(), pair)) {
                items.set(di.index(), pair[0]);
                items.set(dout.index(), pair[1]);
                any = true;
                StationTiles.sound(this, SoundEvents.BUCKET_EMPTY, 0.6F, 1.1F);
            }
        }
        any |= serveAll();
        if (any) {
            setChanged();
        }
    }

    /** Paire « Servir » (marmite) : vrai si une portion a ete servie. */
    protected boolean serveAll() {
        return false;
    }

    // ------------------------------------------------------------------
    // Etat a l'ecran (client) : le contenu de l'ancienne fiche au regard
    // ------------------------------------------------------------------

    @Override
    public FarmCard screenState() {
        FarmCard c = FarmCard.of(ItemStack.EMPTY, getBlockState().getBlock().getName(), 0xC89050);
        describe(c, false);
        if (structure != 1 && getBlockState().getBlock() instanceof BigStationBlock) {
            c.add(FarmCard.Line.text(Component.translatable("farm.pioneers.station.needs_room"), FarmCard.WARN));
        }
        //Les icones du contenu et les indications de geste de l'ancienne fiche : l'interface les montre
        c.lines().removeIf(line -> line.kind() == FarmCard.Kind.ITEMS || line.kind() == FarmCard.Kind.HINT);
        return c;
    }

    /** 1.6 (fiches synthetiques) : avancement en jauge, puis {@link #contents} (liquides). */
    protected void describe(FarmCard card, boolean sneaking) {
        if (need > 0 && (working || progress > 0)) {
            card.add(FarmCard.Line.gauge(FarmCard.label("progress"), fraction(), FarmCard.percent(fraction()), 0x7CC860));
        }
        contents(card);
    }

    /** 1.6 : liquides (nom, jauge, quantite). Lot 41 : plus la rangee d'icones (les cases de l'interface la montrent). */
    protected void contents(FarmCard card) {
        fluidLine(card, tank);
        fluidLine(card, out);
    }

    protected static void fluidLine(FarmCard card, @Nullable FluidTank t) {
        if (t != null && !t.isEmpty()) {
            card.add(FarmCard.Line.gauge(t.getFluid().getDisplayName(), t.getFluidAmount() / (float) t.getCapacity(), FarmCard.millibuckets(t.getFluidAmount()),
                  0x5AA0E6));
        }
    }

    @Nullable
    @Override
    public FluidTank farmTank(int which) {
        if (which == 0) {
            return tank != null ? tank : out;
        }
        return out != null ? out : tank;
    }

    @Override
    public float arrowProgress() {
        return fraction();
    }

    @Override
    public boolean buttonActive(int i) {
        return true;
    }

    // ------------------------------------------------------------------
    // Interface standard v2 (API Machine)
    // ------------------------------------------------------------------

    /** Disposition propre de la sorte de poste (construite une fois par sorte). */
    protected abstract FarmLayout layout();

    @Override
    public FarmLayout farmLayout() {
        return LAYOUTS.computeIfAbsent(kind(), k -> layout());
    }

    /** Role de la case {@code slot} (null : case sans place dans l'interface). */
    @Nullable
    protected FarmLayout.Role role(int slot) {
        FarmLayout.SlotSpec s = farmLayout().at(slot);
        return s == null ? null : s.role();
    }

    /** Le joueur peut-il poser cet objet dans la case {@code slot} (role d'entree) ? Par defaut : la regle de l'automatisation. */
    protected boolean acceptsInput(int slot, ItemStack stack) {
        return acceptsAutomation(slot, stack);
    }

    /** Objet fantome d'une case vide (aide visuelle) ; vide : aucun. */
    protected ItemStack ghost(FarmLayout.SlotSpec spec) {
        return switch (spec.role()) {
            case FILL_IN -> new ItemStack(Items.GLASS_BOTTLE);
            case DRAIN_IN -> new ItemStack(Items.WATER_BUCKET);
            case TOOL -> knife();
            default -> ItemStack.EMPTY;
        };
    }

    /** Couteau montre en fantome dans la case d'outil (premier couteau du tag), vide si aucun. */
    static ItemStack knife() {
        for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(StationTiles.KNIVES)) {
            return new ItemStack(h.value());
        }
        return ItemStack.EMPTY;
    }

    private int manualLimit(FarmLayout.Role role, int slot) {
        return switch (role) {
            case TOOL, CELL -> 1;
            case FILL_IN, DRAIN_IN, SERVE_IN -> 16;
            case IN -> Math.max(1, automationLimit(slot));
            default -> 64;
        };
    }

    private static boolean playerPuts(FarmLayout.Role role) {
        return switch (role) {
            case OUT, FILL_OUT, DRAIN_OUT, SERVE_OUT -> false;
            default -> true;
        };
    }

    /** Filtre d'une case selon son role (le joueur et l'automatisation : chacun a ses droits en plus). */
    private boolean slotFilter(FarmLayout.SlotSpec spec, ItemStack stack) {
        return switch (spec.role()) {
            case FILL_IN -> FluidPair.fillable(stack);
            case DRAIN_IN -> FluidPair.drainable(stack);
            case SERVE_IN -> acceptsServe(stack);
            case TOOL -> stack.is(StationTiles.KNIVES);
            case OUT, FILL_OUT, DRAIN_OUT, SERVE_OUT -> false;
            case IN -> lockAllows(stack, () -> acceptsInput(spec.index(), stack));
            default -> acceptsInput(spec.index(), stack);
        };
    }

    /** Le joueur peut-il reprendre la case {@code slot} dans l'interface ? (bocal scelle, plat a servir : non) */
    protected boolean playerTakes(int slot) {
        return true;
    }

    /** Paire « Servir » : contenant accepte (marmite : celui que demande le plat). */
    protected boolean acceptsServe(ItemStack stack) {
        return false;
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
                FarmLayout.Role role = spec.role();
                FarmSlot slot = new FarmSlot(items, index, role, () -> manualLimit(role, index), s -> slotFilter(spec, s),
                      (s, a) -> a == Automation.MANUAL ? playerPuts(role) : automationInput(index) && acceptsAutomation(index, s),
                      (s, a) -> a == Automation.MANUAL ? playerTakes(index) : extractable(index), this::slotChanged, spec.x(), spec.y());
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

    /** Une case de l'interface a change (joueur) : le poste revoit sa recette. */
    private void slotChanged() {
        if (level != null && !level.isClientSide) {
            slotTaken();
            contentsChanged();
        }
    }

    /** Une case a change par le joueur (avant la synchro) : un poste a gestes remet son compte a zero si l'ingredient part. */
    protected void slotTaken() {
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
            b.add(UiValues.STATUS, () -> working ? 1 : 0);
            b.add(UiValues.FLUID, () -> tank == null ? 0 : tank.getFluidAmount());
            b.add(UiValues.FLUID_OUT, () -> out == null ? 0 : out.getFluidAmount());
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

    /** Valeurs propres de la sorte de poste (feu...). */
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

    @Override
    public SideConfig getSideConfig() {
        return sides;
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        int button = action - CUSTOM;
        if (button >= 0 && button < farmLayout().buttons.size()) {
            manual(player, button);
        }
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
        if (manualGesture()) {
            rows.add(new Object[]{"farm_gesture", Component.translatable("gui.pioneers.farm.info.gesture_value")});
        }
        if (tank != null) {
            rows.add(new Object[]{"farm_tank_in", FarmCard.millibuckets(tank.getCapacity())});
        }
        if (out != null) {
            rows.add(new Object[]{"farm_tank_out", FarmCard.millibuckets(out.getCapacity())});
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Selecteur de recette (postes a plusieurs recettes)
    // ------------------------------------------------------------------

    /** Poste a selecteur : la sorte de recettes qu'il choisit (null : pas de selecteur). */
    @Nullable
    protected Station selectorStation() {
        return null;
    }

    @Override
    public String recipeSource() {
        Station st = selectorStation();
        return st == null ? "none" : RecipeSources.station(st);
    }

    @Override
    public RecipeLock recipeLock() {
        return lock;
    }

    @Override
    public void recipeLockChanged() {
        progress = 0;
        contentsChanged();
    }

    @Nullable
    @Override
    public int[] recipeSlot() {
        return selectorStation() == null ? null : farmLayout().recipe;
    }

    @Override
    public int recipeTier() {
        return 0;
    }

    @Override
    public List<ItemStack> presentItems() {
        List<ItemStack> present = new ArrayList<>();
        for (int i = 0; i < inputs; i++) {
            if (!items.get(i).isEmpty()) {
                present.add(items.get(i));
            }
        }
        return present;
    }

    @Override
    public List<FluidStack> presentFluids() {
        return tank == null || tank.isEmpty() ? List.of() : List.of(tank.getFluid());
    }

    @Nullable
    @Override
    public Level recipeLevel() {
        return level;
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchro
    // ------------------------------------------------------------------

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items, true);
        if (tank != null) {
            tag.put("Tank", tank.writeToNBT(new CompoundTag()));
        }
        if (out != null) {
            tag.put("Out", out.writeToNBT(new CompoundTag()));
        }
        tag.putInt("progress", progress);
        tag.putInt("need", need);
        tag.putBoolean("working", working);
        tag.putInt("strokes", strokes);
        if (structure != 0) {
            tag.putByte("structure", (byte) structure);
        }
        tag.put(SIDES_NBT, sides.save());
        lock.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        int before = strokes;
        super.load(tag);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, items);
        if (tank != null) {
            tank.readFromNBT(tag.getCompound("Tank"));
        }
        if (out != null) {
            out.readFromNBT(tag.getCompound("Out"));
        }
        progress = tag.getInt("progress");
        need = tag.getInt("need");
        working = tag.getBoolean("working");
        strokes = tag.getInt("strokes");
        structure = tag.getByte("structure");
        if (tag.contains(SIDES_NBT)) {
            sides.load(tag.getCompound(SIDES_NBT));
        }
        lock.load(tag);
        if (level != null && level.isClientSide && strokes != before) {
            lastStroke = (long) RenderClock.now();             //1.0.5-beta (lot 29) : meme horloge que le rendu
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------
    // Capacites (lot 41 : selon les faces reglees ; regles des cases d'avant inchangees)
    // ------------------------------------------------------------------

    private final IItemHandler itemsIo = new IItemHandler() {
        @Override
        public int getSlots() {
            return items.size();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return items.get(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!automationInput(slot) || !acceptsAutomation(slot, stack) || role(slot) == FarmLayout.Role.IN && !lockAllows(stack, () -> true)) {
                return stack;
            }
            ItemStack cur = items.get(slot);
            int limit = Math.min(stack.getMaxStackSize(), automationLimit(slot));
            if (!cur.isEmpty() && !ItemStack.isSameItemSameTags(cur, stack)) {
                return stack;
            }
            int room = limit - cur.getCount();
            if (room <= 0) {
                return stack;
            }
            int n = Math.min(room, stack.getCount());
            if (!simulate) {
                if (cur.isEmpty()) {
                    items.set(slot, stack.copyWithCount(n));
                } else {
                    cur.grow(n);
                }
                contentsChanged();
            }
            return stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (automationInput(slot) && !extractInputs()) {
                return ItemStack.EMPTY;
            }
            ItemStack cur = items.get(slot);
            if (cur.isEmpty() || !extractable(slot)) {
                return ItemStack.EMPTY;
            }
            int n = Math.min(amount, cur.getCount());
            ItemStack o = cur.copyWithCount(n);
            if (!simulate) {
                cur.shrink(n);
                contentsChanged();
            }
            return o;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return automationInput(slot) && acceptsAutomation(slot, stack);
        }
    };

    /** Vue d'une face reglee : entree seule, sortie seule. */
    private IItemHandler sidedItems(boolean in) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return itemsIo.getSlots();
            }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                return itemsIo.getStackInSlot(slot);
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return in ? itemsIo.insertItem(slot, stack, simulate) : stack;
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                return in ? ItemStack.EMPTY : itemsIo.extractItem(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return itemsIo.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return in && itemsIo.isItemValid(slot, stack);
            }
        };
    }

    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> itemsIo), itemInCap = LazyOptional.of(() -> sidedItems(true)),
          itemOutCap = LazyOptional.of(() -> sidedItems(false));

    /**
     * 1.0.5-beta (lot 32) : case d'ENTREE pour l'automatisation (par defaut les {@link #inputs} premieres) ; lot 41 : plus les cases des
     * contenants a remplir, a vider et a servir.
     */
    protected boolean automationInput(int slot) {
        if (slot < inputs) {
            return true;
        }
        FarmLayout.Role r = role(slot);
        return r == FarmLayout.Role.FILL_IN || r == FarmLayout.Role.DRAIN_IN || r == FarmLayout.Role.SERVE_IN;
    }

    /** Automatisation : cet objet peut-il entrer dans cette case ? (lot 41 : regle des cases de contenants ici) */
    protected boolean acceptsAutomation(int slot, ItemStack stack) {
        if (slot >= inputs) {
            FarmLayout.Role r = role(slot);
            if (r == FarmLayout.Role.FILL_IN) {
                return FluidPair.fillable(stack);
            }
            if (r == FarmLayout.Role.DRAIN_IN) {
                return FluidPair.drainable(stack);
            }
            if (r == FarmLayout.Role.SERVE_IN) {
                return acceptsServe(stack);
            }
        }
        return true;
    }

    protected int automationLimit(int slot) {
        FarmLayout.Role r = slot >= inputs ? role(slot) : null;
        return r == FarmLayout.Role.FILL_IN || r == FarmLayout.Role.DRAIN_IN || r == FarmLayout.Role.SERVE_IN ? 16 : 1;
    }

    /** Automatisation : cette case de sortie est-elle prete (objet fini) ? Lot 41 : ni l'outil ni un contenant pas encore traite. */
    protected boolean extractable(int slot) {
        if (slot < inputs) {
            return false;
        }
        FarmLayout.Role r = role(slot);
        return r != FarmLayout.Role.TOOL && r != FarmLayout.Role.FILL_IN && r != FarmLayout.Role.DRAIN_IN && r != FarmLayout.Role.SERVE_IN;
    }

    protected boolean extractInputs() {
        return false;
    }

    @Nullable
    protected IFluidHandler fluidHandler(@Nullable Direction side) {
        if (tank == null && out == null) {
            return null;
        }
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return (tank != null ? 1 : 0) + (out != null ? 1 : 0);
            }

            private FluidTank at(int i) {
                return tank != null ? (i == 0 ? tank : out) : out;
            }

            @Override
            public @NotNull FluidStack getFluidInTank(int i) {
                FluidTank t = at(i);
                return t == null ? FluidStack.EMPTY : t.getFluid();
            }

            @Override
            public int getTankCapacity(int i) {
                FluidTank t = at(i);
                return t == null ? 0 : t.getCapacity();
            }

            @Override
            public boolean isFluidValid(int i, @NotNull FluidStack stack) {
                return tank != null && at(i) == tank && tank.isFluidValid(stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return tank == null ? 0 : tank.fill(resource, action);
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                FluidTank t = out != null ? out : tank;
                return t == null || !resource.isFluidEqual(t.getFluid()) ? FluidStack.EMPTY : t.drain(resource, action);
            }

            @Override
            public @NotNull FluidStack drain(int max, FluidAction action) {
                FluidTank t = out != null ? out : tank;
                return t == null ? FluidStack.EMPTY : t.drain(max, action);
            }
        };
    }

    /** Vue d'une face reglee pour les liquides : remplissage seul (entree) ou vidange seule (sortie). */
    private static IFluidHandler sidedFluids(IFluidHandler h, boolean in) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return h.getTanks();
            }

            @Override
            public @NotNull FluidStack getFluidInTank(int i) {
                return h.getFluidInTank(i);
            }

            @Override
            public int getTankCapacity(int i) {
                return h.getTankCapacity(i);
            }

            @Override
            public boolean isFluidValid(int i, @NotNull FluidStack stack) {
                return in && h.isFluidValid(i, stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return in ? h.fill(resource, action) : 0;
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                return in ? FluidStack.EMPTY : h.drain(resource, action);
            }

            @Override
            public @NotNull FluidStack drain(int max, FluidAction action) {
                return in ? FluidStack.EMPTY : h.drain(max, action);
            }
        };
    }

    @Nullable
    private LazyOptional<IFluidHandler> fluidCap, fluidInCap, fluidOutCap;

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !items.isEmpty()) {
            SideMode mode = side == null ? SideMode.IO : sides.mode(SideType.ITEMS, side);
            return switch (mode) {
                case NONE -> LazyOptional.empty();
                case INPUT -> itemInCap.cast();
                case OUTPUT -> itemOutCap.cast();
                case IO -> itemCap.cast();
            };
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER && (tank != null || out != null)) {
            if (fluidCap == null) {
                IFluidHandler h = fluidHandler(side);
                fluidCap = h == null ? LazyOptional.empty() : LazyOptional.of(() -> h);
                fluidInCap = h == null ? LazyOptional.empty() : LazyOptional.of(() -> sidedFluids(h, true));
                fluidOutCap = h == null ? LazyOptional.empty() : LazyOptional.of(() -> sidedFluids(h, false));
            }
            SideMode mode = side == null ? SideMode.IO : sides.mode(SideType.FLUIDS, side);
            return switch (mode) {
                case NONE -> LazyOptional.empty();
                case INPUT -> fluidInCap.cast();
                case OUTPUT -> fluidOutCap.cast();
                case IO -> fluidCap.cast();
            };
        }
        return super.getCapability(cap, side);
    }

    /** Faces changees : les entonnoirs et tuyaux voisins relisent les capacites. */
    private void invalidateSided() {
        itemCap.invalidate();
        itemInCap.invalidate();
        itemOutCap.invalidate();
        itemCap = LazyOptional.of(() -> itemsIo);
        itemInCap = LazyOptional.of(() -> sidedItems(true));
        itemOutCap = LazyOptional.of(() -> sidedItems(false));
        if (fluidCap != null) {
            fluidCap.invalidate();
            fluidInCap.invalidate();
            fluidOutCap.invalidate();
            fluidCap = null;
        }
        sync();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
        itemInCap.invalidate();
        itemOutCap.invalidate();
        if (fluidCap != null) {
            fluidCap.invalidate();
            fluidInCap.invalidate();
            fluidOutCap.invalidate();
        }
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    protected static Item item(String id) {
        return PiFarm.item(id);
    }

    @Nullable
    protected static Fluid fluid(ResourceLocation id) {
        return BuiltInRegistries.FLUID.get(id);
    }

}
