package fr.pioneers.tech.industry.atelier;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.MachineRegistry;
import fr.pioneers.api.machine.MachineSpec;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.IndustryProcess;
import fr.pioneers.api.machine.unit.KitProcess;
import fr.pioneers.api.machine.unit.ProcessUnit;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.core.side.SideType;
import fr.pioneers.tech.industry.GeneratorOutput;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.recipe.RecipeMatch;
import fr.pioneers.tech.machine.KitMachineBlock;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.machine.PiMachineKit;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 2) : TILE GENERIQUE DES MACHINES COMPACTES A PROCEDE ({@link MachineTile} + une {@link ProcessUnit}) : machines d'atelier
 * de l'age industriel ({@link AtelierBlock}, type {@code industry_atelier}, procede {@link IndustryProcess}) et machines du kit (broyeur,
 * four electrique, compacteur : {@link KitMachineBlock}, type {@code kit_machine} garde comme alias, procede {@link KitProcess}). Plus aucune
 * logique ici : la tile porte l'unite, lui prete ses operations ({@link #access()}), et sert de facade (cases, reservoirs, verrou,
 * valeurs nommees) aux autres classes et aux GameTests. Interface : la standard v2 ({@link StandardLayout}, inventaire dans le cadre,
 * carre du selecteur).
 */
public class AtelierTile extends MachineTile implements RecipeMatch.Buffers, RecipeLockable {

    /** Etats de l'ecran ({@link ProcessUnit}). */
    public static final int IDLE = ProcessUnit.IDLE, WORKING = ProcessUnit.WORKING, NO_ENERGY = ProcessUnit.NO_ENERGY, FULL = ProcessUnit.FULL,
          NO_RECIPE = ProcessUnit.NO_RECIPE, TIER = ProcessUnit.TIER, NO_DEPOSIT = ProcessUnit.NO_DEPOSIT, EXHAUSTED = ProcessUnit.EXHAUSTED,
          NO_FLUID = ProcessUnit.NO_FLUID;

    /** Machine du catalogue industriel (pour le kit : la machine du catalogue de meme cle). */
    public final IndustryMachine machine;
    public final CableTier tier;
    public final StandardLayout layout;
    private final ProcessUnit unit;
    private final boolean kit;

    public AtelierTile(BlockPos pos, BlockState state) {
        super(type(state), pos, state, capacity(state), (int) Math.min(Integer.MAX_VALUE, Math.round(power(state) * 4 * outputYield(state))));
        if (state.getBlock() instanceof KitMachineBlock k) {
            kit = true;
            tier = k.tier;
            machine = IndustryMachine.byKey(k.kind.name);
            unit = new KitProcess(access(), k.kind, k.tier);
        } else {
            AtelierBlock block = (AtelierBlock) state.getBlock();
            kit = false;
            tier = block.tier;
            machine = block.machine;
            unit = new IndustryProcess(access(), block.machine, block.tier);
        }
        layout = unit.layout;
    }

    /**
     * 1.0.3-beta (lot 5) : fabrique des types de tile de l'atelier et du kit. Un NBT d'atelier relu sur un bloc qui n'est plus un atelier
     * (machine compacte retiree, bloc remappe vers le controleur de son multibloc : {@code PiRemaps.p103Block}) ne donne aucune tile (le jeu
     * passe ce NBT, puis le bloc pose cree la sienne) au lieu d'une exception de conversion.
     */
    @org.jetbrains.annotations.Nullable
    public static AtelierTile create(BlockPos pos, BlockState state) {
        return state.getBlock() instanceof AtelierBlock || state.getBlock() instanceof KitMachineBlock ? new AtelierTile(pos, state) : null;
    }

    /** Type de la tile : {@code kit_machine} pour le kit (alias garde : mondes existants), {@code industry_atelier} sinon. */
    private static BlockEntityType<?> type(BlockState state) {
        return state.getBlock() instanceof KitMachineBlock ? PiMachineKit.PROCESSING_TILE.get() : PiIndustry.ATELIER_TILE.get();
    }

    private static long power(BlockState state) {
        if (state.getBlock() instanceof AtelierBlock b) {
            return Tiers.power(b.tier);
        }
        return state.getBlock() instanceof KitMachineBlock k ? k.kind.power(k.tier) : Tiers.BASE_POWER;
    }

    private static long capacity(BlockState state) {
        return Math.round(power(state) * 400 * outputYield(state));
    }

    /** MAJ simplification : rendement de la machine (generateur a combustion x2) : reserve et debit a sa mesure. */
    private static double outputYield(BlockState state) {
        return state.getBlock() instanceof AtelierBlock b ? GeneratorOutput.machineYield(b.machine) : 1.0;
    }

    /** L'unite de procede (API Machine). */
    public ProcessUnit unit() {
        return unit;
    }

    /** Fiche de l'API de la machine (null hors du registre : banc, tests). */
    @Nullable
    public MachineSpec spec() {
        MachineRegistry.Entry e = MachineRegistry.of(getBlockState().getBlock());
        return e == null ? null : e.spec();
    }

    // ------------------------------------------------------------------
    // Regles de la machine (appelees par le constructeur de MachineTile : avant l'unite, d'apres le bloc)
    // ------------------------------------------------------------------

    @Override
    protected boolean producesEnergy() {
        return getBlockState().getBlock() instanceof AtelierBlock b && b.machine.isGenerator();
    }

    @Override
    protected boolean canEject() {
        return true;
    }

    @Override
    protected SideType[] ejectTypes() {
        if (getBlockState().getBlock() instanceof KitMachineBlock k) {
            //1.0.4-beta (lot 11) : fluides et gaz en plus quand l'io de la machine s'est elargie (machines absorbees)
            return KitProcess.ejectTypes(k.kind);
        }
        return producesEnergy() ? new SideType[]{SideType.ITEMS, SideType.FLUIDS, SideType.GASES, SideType.ENERGY}
              : new SideType[]{SideType.ITEMS, SideType.FLUIDS, SideType.GASES};
    }

    @Override
    protected List<ItemSlot> automationSlots() {
        return unit instanceof KitProcess k ? k.automationSlots() : super.automationSlots();
    }

    @Override
    protected boolean kitFormat() {
        return getBlockState().getBlock() instanceof KitMachineBlock;
    }

    /** 1.0.3-beta (lot 5) : objets d'une machine fondue dans celle-ci (relus d'un NBT d'atelier par le kit), rendus au premier tick. */
    private final List<ItemStack> merged = new java.util.ArrayList<>();

    @Override
    protected void tick() {
        if (!merged.isEmpty() && level != null) {
            fr.pioneers.tech.industry.IoMigration.release(level, worldPosition, merged);
        }
        unit.tick();
    }

    // ------------------------------------------------------------------
    // Interface (standard v2)
    // ------------------------------------------------------------------

    @Override
    public int guiHeight() {
        return layout.height;
    }

    @Override
    public int guiWidth() {
        return layout.width;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        //1.0.5-beta (lot 44) : + la case Outil sous la fleche (machines a remanents) ; lot 46 : case d'echantillon du generateur de roches
        boolean sample = machine != null && machine.kind == IndustryMachine.Kind.ROCKS;
        return sample ? layout.sampleParts() : layout.parts(unit.toolSlot() != null);
    }

    @Override
    public String guiTier() {
        return tier.id();
    }

    @Override
    protected boolean namedValues() {
        return true;
    }

    /** Valeurs : 4 avancement, 5 duree, 6 etat, 7 production FE/t, 8 tier demande (-1 : aucun) — les indices d'avant le lot 2. */
    @Override
    protected void declareValues(UiValues.Builder values) {
        unit.declareValues(values);
    }

    /** Production electrique en cours (FE/t), pour l'interface. */
    public long production() {
        return unit.production();
    }

    // ------------------------------------------------------------------
    // Cases et reservoirs (RecipeMatch.Buffers)
    // ------------------------------------------------------------------

    @Override
    public double drawFactor() {
        return unit instanceof IndustryProcess p ? p.drawFactor() : 1;
    }

    @Override
    public int extraRolls() {
        return tier.ordinal();
    }

    @Override
    public List<ItemSlot> itemInputs() {
        return unit.itemInputs();
    }

    @Override
    public List<ItemSlot> itemOutputs() {
        return unit.itemOutputs();
    }

    /** 1.0.5-beta (lot 44, retour 62) : case « Outil » des remanents (vide : la machine n'en a pas). */
    @Override
    public List<ItemSlot> toolSlots() {
        return unit.toolSlots();
    }

    /** 1.0.5-beta (lot 44) : la case Outil, ou null (GameTests, interface, JEI). */
    @Nullable
    public ItemSlot toolSlot() {
        return unit.toolSlot();
    }

    @Override
    public void spill(ItemStack stack) {
        if (level != null) {
            fr.pioneers.tech.industry.IoMigration.release(level, worldPosition, new java.util.ArrayList<>(List.of(stack)));
        }
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

    /** 1.0.6 : jauges de sortie partagees (liquide ou gaz) de l'unite. */
    @Override
    public boolean pairedOutputs() {
        return unit instanceof RecipeMatch.Buffers b && b.pairedOutputs();
    }

    public List<FluidTank> fluidInTanks() {
        return unit.fluidInputs();
    }

    public List<FluidTank> fluidOutTanks() {
        return unit.fluidOutputs();
    }

    public List<BasicGasTank> gasInTanks() {
        return unit.gasInputs();
    }

    public List<BasicGasTank> gasOutTanks() {
        return unit.gasOutputs();
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        unit.save(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        //1.0.3-beta (lot 5, decision de Guillaume) : la dechiqueteuse et la granuleuse compactes sont fondues dans le broyeur et le compacteur du
        //kit (bloc remappe : PiRemaps.p103Block) ; leur NBT d'atelier (drapeau « pioneers », que le kit n'ecrit jamais) n'a pas les cases du
        //kit : energie et faces gardees, objets rendus au premier tick
        CompoundTag fused = kit && tag.getBoolean("pioneers") && unit instanceof KitProcess k
              ? fr.pioneers.tech.industry.IoMigration.kit(machine, KitProcess.migrationLayout(k.kind), tag, merged) : null;
        if (fused != null) {
            //1.0.4-beta (lot 11, fusion des machines) : NBT d'atelier d'une machine ABSORBEE par ce kit (broyeur a boulets -> broyeur, four de
            //grillage, de reduction... -> four industriel ; bloc remappe : PiRemaps.p104Block) : contenu remis par noms dans les cases du
            //kit (IoMigration.kit) ; sans place : rendu au premier tick. Relu au format du kit : plus de drapeau « pioneers ».
            fused.remove("pioneers");
            tag = fused;
            readForeign(tag);
            unit.load(tag);
            return;
        } else if (kit && tag.getBoolean("pioneers")) {
            tag = tag.copy();
            net.minecraft.nbt.ListTag items = tag.getList("items", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = ItemStack.of(items.getCompound(i));
                if (!stack.isEmpty()) {
                    merged.add(stack);
                }
            }
            tag.remove("items");
            tag.remove("progress");
        }
        tag = unit.migrate(tag);
        super.load(tag);
        unit.load(tag);
    }

    /** 1.0.4-beta (lot 11) : lecture du NBT d'atelier remis au format du kit (energie, faces, cases, reservoirs). */
    private void readForeign(CompoundTag tag) {
        tag.putBoolean("pioneers", true);
        super.load(tag);
        tag.remove("pioneers");
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        unit.writeClient(tag);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        unit.readClient(tag);
    }

    // ------------------------------------------------------------------
    // Selecteur de recette (tenu par l'unite)
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
    public List<GasStack> presentGases() {
        return unit.presentGases();
    }

    @Nullable
    @Override
    public Level recipeLevel() {
        return level;
    }

    /** Machine du kit (broyeur, four electrique, compacteur) ? */
    public boolean isKit() {
        return kit;
    }

    /** Le gaz produit est-il present (rendu, infobulles) ? */
    public static boolean has(GasStack stack) {
        return stack != null && !stack.isEmpty();
    }
}
