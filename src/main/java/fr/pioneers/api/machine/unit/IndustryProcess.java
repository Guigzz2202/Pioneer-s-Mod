package fr.pioneers.api.machine.unit;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.api.machine.resource.ResourceRows;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.gas.GasTankHandler;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.machine.Ejector;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.recipe.RecipeSources;
import fr.pioneers.core.side.SideType;
import fr.pioneers.tech.industry.Extraction;
import fr.pioneers.tech.industry.GeneratedLot46;
import fr.pioneers.tech.industry.Impurity;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IoMigration;
import fr.pioneers.tech.industry.IoTank;
import fr.pioneers.tech.industry.RockSamples;
import fr.pioneers.tech.industry.TankSync;
import fr.pioneers.tech.industry.TankView;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.industry.recipe.RecipeMatch;
import fr.pioneers.tech.industry.recipe.Remanents;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.Transfer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 2) : procede des MACHINES D'ATELIER de l'age industriel (une machine du catalogue {@link IndustryMachine} et un tier) :
 * ses cases et reservoirs viennent de l'io du catalogue. Une operation a la fois ; consommation {@link Tiers#power} FE/t, duree divisee par
 * 2 par tier au-dessus de celui de la recette. Les entrees sont prises a la fin de l'operation ; sans energie la progression s'arrete sans
 * se perdre. Generateur (combustion) : lot 3, la production est a l'unite {@link FuelGenerator} (le procede lui fournit la recette de
 * combustible). Puits et pompes : extraction continue.
 * <p>
 * Code repris tel quel de l'ancienne {@code AtelierTile} (memes cles de sauvegarde : {@code progress}, {@code burn}, {@code burnPower},
 * {@code recipe}, verrou, {@code ioVersion}).
 */
public class IndustryProcess extends ProcessUnit implements RecipeMatch.Buffers {

    public final IndustryMachine kind;
    private final ItemSlot battery;
    private boolean dirty = true;
    /**
     * 0.20.46 (audit) : etat d'attente sans recette (IDLE, TIER, NO_RECIPE) calcule seulement quand les entrees changent (ou toutes les
     * 20 ticks), et place des produits gardee jusqu'au prochain changement des sorties.
     */
    private int waitStatus = IDLE;
    private boolean fitsKnown, fitsOk;
    @Nullable
    private IndustrialRecipe fitsFor;
    /** 0.20.46 (audit) : envoi des reservoirs au client economise. */
    private final TankSync tankSync = new TankSync();
    @Nullable
    private IndustrialRecipe current;
    @Nullable
    private ResourceLocation savedRecipe;
    /** Lot 3 : generateur a combustion (null pour les autres machines). */
    @Nullable
    private final FuelGenerator generator;
    /** Puits : fraction de mB reportee d'un tick a l'autre. */
    private final double[] carry = new double[1];
    /** MAJ Recettes (lot 9a) : machine relue d'un monde d'avant la MAJ dont l'io a change : NBT a resauver, objets rendus au premier tick. */
    private boolean ioMigrated;
    private final List<ItemStack> ioOverflow = new ArrayList<>();
    /**
     * 1.0.5-beta (lot 44, retour 62) : CASE « OUTIL » (machines a remanents : presse, machine de coulee, extrudeuse, reacteur chimique...),
     * DERNIERE case de la machine (sauvegardes d'avant intactes) ; null sans remanent. Voir {@link Remanents}.
     */
    @Nullable
    private final ItemSlot tool;
    private final List<ItemSlot> tools;
    /** 1.0.5-beta (lot 46) : etats du generateur de roches (cles {@code industry.pioneers.status.<n>}) : sans echantillon, eau et lave. */
    public static final int NO_SAMPLE = 9, NEED_FLUIDS = 10;

    public IndustryProcess(MachineTile.Access machine, IndustryMachine kind, CableTier tier) {
        super(machine, tier, counts(kind));
        this.kind = kind;
        int t = tier.ordinal();
        for (int i = 0; i < kind.itemsIn; i++) {
            int[] p = layout.itemIn.get(i);
            inputs.add(machine.slot(new ItemSlot(() -> 64, this::acceptsItem, (s, a) -> true, (s, a) -> !a.external(), this::inputChanged, p[0], p[1])));
        }
        for (int i = 0; i < kind.itemsOut; i++) {
            int[] p = layout.itemOut.get(i);
            outputs.add(machine.slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::outputChanged, p[0], p[1])));
        }
        battery = machine.batterySlot(layout.batteryX, layout.batteryY);
        //1.0.5-beta (lot 44) : case Outil en DERNIER (apres la batterie) : les index des cases d'avant ne bougent pas ; lot 46 (retour 72) : le
        //generateur de roches a, a la meme place, sa case reservee d'ECHANTILLON (une roche, jamais consommee)
        tool = kind.kind == IndustryMachine.Kind.ROCKS ? machine.slot(ToolBay.sample(t, this::inputChanged, layout.toolX, layout.toolY))
               : Remanents.hasToolSlot(kind.key) ? machine.slot(ToolBay.slot(kind.key, this, this::inputChanged, layout.toolX, layout.toolY)) : null;
        tools = tool == null ? List.of() : List.of(tool);
        int tank = 16_000 * (t + 1);
        //1.0.6 : liquides et gaz partages (une jauge = un liquide ou un gaz) quand les recettes prennent des deux (ResourceRows)
        pairedIn = ResourceRows.pairedIn(kind);
        pairedOut = ResourceRows.pairedOut(kind);
        for (int i = 0; i < kind.fluidsIn; i++) {
            //MAJ Recettes (lot 4b) : jamais deux reservoirs d'entree du meme fluide (machine de coulee : le metal fondu ne prend pas le
            //reservoir du fluide de refroidissement)
            int index = i;
            fluidIn.add(machine.fluidTank(new IoTank(tank, false, f -> acceptsFluid(f) && (pairedIn
                  ? ResourceRows.fluidFits(fluidIn, gasIn, index, f, lockedOrNull()) : !heldElsewhere(index, f)), this::tanksChanged)));
        }
        for (int i = 0; i < kind.fluidsOut; i++) {
            fluidOut.add(machine.fluidTank(new IoTank(tank, true, f -> true, this::outputTanksChanged)));
        }
        for (int i = 0; i < kind.gasesIn; i++) {
            int index = i;
            gasIn.add(machine.gasTank(BasicGasTank.input(tank * 2L, g -> level() == null || lockAllowsGas(g, () -> IndustryRecipes.acceptsGas(level(), kind, g))
                  && (!pairedIn || ResourceRows.gasFits(fluidIn, gasIn, index, g, lockedOrNull())), this::tanksChanged)));
        }
        for (int i = 0; i < kind.gasesOut; i++) {
            gasOut.add(machine.gasTank(BasicGasTank.output(tank * 2L, this::outputTanksChanged)));
        }
        if (!kind.isGenerator()) {
            machine.sides().addType(SideType.ENERGY);
        }
        generator = kind.isGenerator() ? new FuelGenerator(machine, kind, t, new FuelGenerator.Fuel() {
            @Nullable
            @Override
            public IndustrialRecipe take() {
                return takeFuel();
            }

            @Override
            public boolean inputsEmpty() {
                return IndustryProcess.this.inputsEmpty();
            }
        }) : null;
    }

    /** 1.0.6 : jauges de liquide et de gaz partagees (entrees, sorties) : {@link ResourceRows}. */
    private final boolean pairedIn, pairedOut;

    @Override
    public boolean pairedOutputs() {
        return pairedOut;
    }

    /** Recette verrouillee (choix de recette), ou null. */
    @Nullable
    private fr.pioneers.core.recipe.RecipeChoice lockedOrNull() {
        return level() != null && lock.isSet() ? lockedChoice() : null;
    }

    /** L'unite de generateur (generateur a combustion), ou null. */
    @Nullable
    public FuelGenerator generator() {
        return generator;
    }

    /** Cases et reservoirs d'une machine du catalogue (disposition de l'interface standard). */
    public static StandardLayout.Counts counts(IndustryMachine m) {
        return new StandardLayout.Counts(m.itemsIn, m.itemsOut, m.fluidsIn, m.gasesIn, m.fluidsOut, m.gasesOut, ResourceRows.pairedIn(m),
              ResourceRows.pairedOut(m));
    }

    /** Puissance (FE/t) d'une machine d'atelier a ce tier. */
    public static long power(CableTier tier) {
        return Tiers.power(tier);
    }

    @Override
    public boolean producesEnergy() {
        return kind.isGenerator();
    }

    @Override
    public SideType[] ejectTypes() {
        return kind.isGenerator() ? new SideType[]{SideType.ITEMS, SideType.FLUIDS, SideType.GASES, SideType.ENERGY}
              : new SideType[]{SideType.ITEMS, SideType.FLUIDS, SideType.GASES};
    }

    private boolean acceptsItem(ItemStack stack) {
        //1.0.5-beta (lot 44) : un remanent de la machine ne va jamais dans une case d'entree (sa place : la case Outil)
        if (tool != null && Remanents.isTool(kind.key, stack)) {
            return false;
        }
        //MAJ Recettes (lot 7) : verrouillee, la machine ne prend que les ingredients de sa recette (entonnoirs, tapis, robots, logistique)
        return level() == null || lockAllows(stack, () -> IndustryRecipes.accepts(level(), kind, stack));
    }


    private boolean acceptsFluid(FluidStack stack) {
        Level level = level();
        if (level == null) {
            return true;
        }
        if (kind.kind == IndustryMachine.Kind.WELL) {
            //Injection : eau ou boue de forage (1.0 : la vapeur est un gaz)
            return stack.getFluid().is(FluidTags.WATER) || stack.getFluid().isSame(PiIndustry.fluid("drilling_mud"));
        }
        if (kind.kind == IndustryMachine.Kind.ROCKS) {
            //1.0.5-beta (lot 46) : generateur de roches : eau et lave (un reservoir chacune : heldElsewhere)
            return RockSamples.fluid(stack);
        }
        if (lock.isSet() && lockedChoice() != null) {
            return lockedChoice().takesFluid(stack);
        }
        for (IndustrialRecipe r : IndustryRecipes.of(level, kind)) {
            for (IndustrialRecipe.FluidIn in : r.fluids()) {
                if (in.test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Un autre reservoir d'entree tient-il deja ce fluide ? */
    private boolean heldElsewhere(int index, FluidStack stack) {
        for (int i = 0; i < fluidIn.size(); i++) {
            if (i != index && !fluidIn.get(i).isEmpty() && fluidIn.get(i).getFluid().isFluidEqual(stack)) {
                return true;
            }
        }
        return false;
    }

    private void inputChanged() {
        dirty = true;
        machine.changed();
    }

    private void tanksChanged() {
        dirty = true;
        machine.changed();
        //0.20.46 (audit) : plus de markSync a chaque variation : voir tick()
        tankSync.changed();
    }

    /** 0.20.46 (audit) : une case de sortie a change : la place des produits sera revue. */
    private void outputChanged() {
        fitsKnown = false;
        machine.changed();
    }

    private void outputTanksChanged() {
        fitsKnown = false;
        tanksChanged();
    }

    /** Les produits d'une operation de la recette en cours tiennent-ils ? (garde jusqu'au prochain changement des sorties) */
    private boolean outputsFit(IndustrialRecipe recipe) {
        if (!fitsKnown || fitsFor != recipe) {
            fitsOk = RecipeMatch.fits(recipe, this, 1, impurity(recipe), bonus(recipe));
            fitsFor = recipe;
            fitsKnown = true;
        }
        return fitsOk;
    }

    // ------------------------------------------------------------------
    // Travail
    // ------------------------------------------------------------------

    private List<IndustrialRecipe> recipes() {
        return IndustryRecipes.of(level(), kind);
    }

    /** MAJ Recettes (lot 7) : la recette verrouillee (null : aucun verrou, ou recette disparue : la machine fait comme sans verrou). */
    @Nullable
    private IndustrialRecipe locked() {
        return lock.isSet() && level() != null ? IndustryRecipes.byId(level(), kind, lock.id()) : null;
    }

    @Nullable
    private IndustrialRecipe findRecipe() {
        IndustrialRecipe locked = locked();
        if (IndustryRecipes.burnsSolid(kind) && !inputs.isEmpty() && locked == null) {
            IndustrialRecipe fuel = IndustryRecipes.fuel(inputs.get(0).getStack());
            if (fuel != null || kind.source == IndustryMachine.Source.FUEL) {
                return fuel;
            }
        }
        if (savedRecipe != null) {
            //Reprise apres chargement : la recette en cours d'abord
            ResourceLocation id = savedRecipe;
            savedRecipe = null;
            for (IndustrialRecipe r : RecipeMatch.candidates(recipes(), locked)) {
                if (r.getId().equals(id) && RecipeMatch.supply(r, this, 1) > 0) {
                    return r;
                }
            }
        }
        //Verrou : seule la recette verrouillee est essayee (une recherche par changement des entrees, au plus toutes les 20 ticks)
        return RecipeMatch.find(recipes(), this, tier.ordinal(), false, locked);
    }

    private int impurity(IndustrialRecipe r) {
        return Impurity.result(r.impurity(), tier.ordinal(), r.tier(), false);
    }

    @Override
    public double drawFactor() {
        return machine.tile().ownerFactor("salvager.extra_draws");
    }

    @Override
    public int extraRolls() {
        return tier.ordinal();
    }

    private double bonus(IndustrialRecipe r) {
        //MAJ Recettes (lot 9a) : rendement du minerai du Mineur-Fondeur proprietaire (vocation de la metallurgie, connecte)
        return r.ore() ? Tiers.oreBonus(tier.ordinal()) * machine.tile().ownerFactor("miner.ore_yield") : 1;
    }

    @Override
    public void tick() {
        Level level = level();
        if (ioMigrated) {
            ioMigrated = false;
            IoMigration.release(level, pos(), ioOverflow);
            machine.changed();
        }
        if (tankSync.due(machine.tile(), machine.tile().getFluidTanks(), machine.tile().getGasTanks())) {
            machine.markSync();
        }
        if (level.getGameTime() % 100 == 17) {
            dropMissingLock();                                            // 1.11.1 : verrou d'une recette partie ailleurs
        }
        machine.discharge(battery, Tiers.power(tier) * 4);
        if (level.getGameTime() % 10 == 0) {
            eject();
        }
        if (generator != null) {
            generator.tick();
            status = generator.status();
            progress = generator.progress();
            duration = generator.duration();
            return;
        }
        if (kind.kind == IndustryMachine.Kind.WELL || kind.kind == IndustryMachine.Kind.PUMP) {
            tickExtraction();
            return;
        }
        if (kind.kind == IndustryMachine.Kind.WATER) {
            tickWater();
            return;
        }
        if (kind.kind == IndustryMachine.Kind.ROCKS) {
            tickRocks();
            return;
        }
        if (dirty || level.getGameTime() % 20 == 0) {
            dirty = false;
            //Place des produits revue au moins toutes les 20 ticks (sorties changees sans prevenir)
            fitsKnown = false;
            ResourceLocation resuming = savedRecipe;
            IndustrialRecipe found = findRecipe();
            if (found != current) {
                //Reprise apres chargement : l'avancement sauve est garde
                if (current != null || found == null || resuming == null || !found.getId().equals(resuming)) {
                    progress = 0;
                }
                current = found;
                if (found != null) {
                    duration = Tiers.ticks(found.time(), tier.ordinal(), found.tier(), false, found.machine());
                    if (found.draws() != null) {
                        //1.9 (MAJ RP) : tri plus rapide (skill Recuperation du proprietaire)
                        duration = (int) Math.max(1, Math.round(duration / machine.tile().ownerFactor("salvage.sort_speed")));
                    }
                }
            }
            if (current == null) {
                //MAJ Recettes (lots 1 et 7) : « Tier insuffisant : MKIII requis » (recette verrouillee, ou la seule qui convient, au-dessus)
                tierNeeded = RecipeMatch.tierNeeded(recipes(), this, tier.ordinal(), false, locked());
                waitStatus = tierNeeded >= 0 ? TIER : inputsEmpty() ? IDLE : NO_RECIPE;
            } else {
                tierNeeded = -1;
            }
        }
        boolean working = false;
        int was = status;
        if (!machine.canWork()) {
            status = IDLE;
        } else if (current == null) {
            status = waitStatus;
        } else if (!outputsFit(current)) {
            status = FULL;
        } else if (!machine.use(Tiers.power(tier))) {
            status = NO_ENERGY;
        } else {
            status = WORKING;
            working = true;
            if (++progress >= duration) {
                progress = 0;
                if (RecipeMatch.supply(current, this, 1) > 0) {
                    RecipeMatch.consume(current, this, 1);
                    RecipeMatch.produce(current, this, 1, level.random, impurity(current), bonus(current));
                    IndustryEvents.award(level, pos(), kind, current);
                    if (level instanceof ServerLevel sl) {
                        //1.9 (MAJ RP) : tri des scraps ; MAJ Recettes (lot 9a) : skill du procede ou de la machine
                        fr.pioneers.core.profile.SkillHooks.autoRecipe(sl, pos(), machine.tile().owner(), kind, current, false);
                    }
                }
                dirty = true;
            }
            machine.changed();
        }
        if (was != status) {
            machine.changed();
        }
        machine.markWorking(working);
    }

    /** Puits et pompes : extraction continue, {@link Tiers#power} FE/t tant qu'ils produisent. */
    private void tickExtraction() {
        Level level = level();
        int was = status;
        boolean working = false;
        long power = Tiers.power(tier);
        if (!machine.canWork()) {
            status = IDLE;
        } else if (machine.energy().getStored() < power) {
            status = NO_ENERGY;
        } else {
            int r;
            if (kind.kind == IndustryMachine.Kind.WELL) {
                r = Extraction.well((ServerLevel) level, pos(), kind, 1 << tier.ordinal(),
                      fluidIn.isEmpty() ? null : fluidIn.get(0), fluidOut.isEmpty() ? null : fluidOut.get(0), gasOut.isEmpty() ? null : gasOut.get(0),
                      gasOut.size() > 1 ? gasOut.get(1) : null, carry);
                progress = 0;
            } else {
                duration = Math.max(1, 20 >> tier.ordinal());
                if (++progress >= duration) {
                    progress = 0;
                    r = fluidOut.isEmpty() ? Extraction.FULL
                          : Extraction.pump((ServerLevel) level, pos(), tier.ordinal(), 1, fluidOut.get(0));
                } else {
                    r = status == WORKING ? Extraction.OK : -1;
                }
            }
            status = switch (r) {
                case Extraction.OK -> WORKING;
                case Extraction.NO_DEPOSIT -> NO_DEPOSIT;
                case Extraction.EXHAUSTED -> EXHAUSTED;
                case Extraction.FULL -> FULL;
                case Extraction.NO_FLUID -> NO_FLUID;
                default -> status;
            };
            if (status == WORKING) {
                machine.use(power);
                working = true;
                if (level.getGameTime() % 200 == 0) {
                    IndustryEvents.award(level, pos(), kind);
                }
            }
        }
        if (was != status) {
            machine.changed();
        }
        machine.markWorking(working);
    }

    /**
     * 1.0.5-beta (lot 46, retour 72 : « Un block qui genere de l'eau mkI a IV (Moins performant que la pompe multiblock) ») : BLOC A EAU :
     * sans entree, {@link GeneratedLot46#WATER_RATE} mB d'eau par tick dans son reservoir (au plus le quart de la pompe multibloc du meme
     * tier), {@link Tiers#power} FE/t tant qu'il produit ; reservoir plein : il attend (l'ejection par les faces le vide).
     */
    private void tickWater() {
        Level level = level();
        int was = status;
        boolean working = false;
        int rate = GeneratedLot46.WATER_RATE[tier.ordinal()];
        FluidTank out = fluidOut.isEmpty() ? null : fluidOut.get(0);
        duration = 20;
        if (!machine.canWork()) {
            status = IDLE;
        } else if (out == null || out.getSpace() <= 0 || (!out.isEmpty() && !out.getFluid().getFluid().isSame(Fluids.WATER))) {
            status = FULL;
        } else if (!machine.use(Tiers.power(tier))) {
            status = NO_ENERGY;
        } else {
            int put = Math.min(rate, out.getSpace());
            if (out instanceof IoTank io) {
                io.fillInternal(new FluidStack(Fluids.WATER, put), IFluidHandler.FluidAction.EXECUTE);
            } else {
                out.fill(new FluidStack(Fluids.WATER, put), IFluidHandler.FluidAction.EXECUTE);
            }
            status = WORKING;
            working = true;
            if (level.getGameTime() % 200 == 0) {
                IndustryEvents.award(level, pos(), kind);
            }
        }
        progress = working ? (int) (level.getGameTime() % duration) : 0;
        if (was != status) {
            machine.changed();
        }
        machine.markWorking(working);
    }

    /**
     * 1.0.5-beta (lot 46, retour 72 ; reponse de Guillaume : « echantillon + eau + lave jamais consommee ») : GENERATEUR DE ROCHES : un
     * echantillon dans la case reservee ({@link ToolBay#sample}, jamais consomme), au moins {@link GeneratedLot46#SAMPLE_FLUID} mB d'eau et de
     * lave dans les reservoirs (JAMAIS preleves) et l'energie : une roche de l'echantillon toutes les {@link GeneratedLot46#ROCK_TICKS} ticks.
     * Echantillon d'un tier plus haut : « Tier insuffisant : MKn requis » ; sans echantillon, sans eau ou sans lave : l'ecran le dit.
     */
    private void tickRocks() {
        Level level = level();
        int was = status;
        boolean working = false;
        ItemStack sample = tool == null ? ItemStack.EMPTY : tool.getStack();
        int need = RockSamples.minTier(sample);
        duration = GeneratedLot46.ROCK_TICKS[tier.ordinal()];
        tierNeeded = -1;
        if (!machine.canWork()) {
            status = IDLE;
        } else if (need < 0) {
            status = NO_SAMPLE;
            progress = 0;
        } else if (need > tier.ordinal()) {
            status = TIER;
            tierNeeded = need;
            progress = 0;
        } else if (!RockSamples.fluidsReady(fluidIn)) {
            status = NEED_FLUIDS;
        } else if (outputs.isEmpty() || !outputs.get(0).insert(RockSamples.product(sample), Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
            status = FULL;
        } else if (!machine.use(Tiers.power(tier))) {
            status = NO_ENERGY;
        } else {
            status = WORKING;
            working = true;
            if (++progress >= duration) {
                progress = 0;
                outputs.get(0).insert(RockSamples.product(sample), Action.EXECUTE, Automation.INTERNAL);
            }
            if (level.getGameTime() % 200 == 0) {
                IndustryEvents.award(level, pos(), kind);
            }
        }
        if (was != status) {
            machine.changed();
        }
        machine.markWorking(working);
    }

    /** Lot 3 : combustible pour {@link FuelGenerator} : la recette trouvee, ses entrees prises et ses sous-produits rendus (ou null). */
    @Nullable
    private IndustrialRecipe takeFuel() {
        Level level = level();
        IndustrialRecipe r = findRecipe();
        //1.0.4-beta (lot 5) : regle des producteurs (Surplus) : un sous-produit fluide ou gazeux plein ne bloque plus le combustible
        if (r != null && r.energy() > 0 && RecipeMatch.fitsVenting(r, this, 1, 0, 1) && RecipeMatch.supply(r, this, 1) > 0) {
            RecipeMatch.consume(r, this, 1);
            RecipeMatch.produceVenting(r, this, 1, level.random, 0, 1);
            IndustryEvents.award(level, pos(), kind);
            return r;
        }
        return null;
    }

    private void eject() {
        Level level = level();
        if (!outputs.isEmpty()) {
            Ejector.eject(level, pos(), machine.sides(), Transfer.ITEMS, new ItemSlots(outputs, Automation.INTERNAL), 64);
        }
        if (!fluidOut.isEmpty()) {
            Ejector.eject(level, pos(), machine.sides(), Transfer.FLUIDS, new TankView(fluidOut), 16_000);
        }
        if (!gasOut.isEmpty()) {
            Ejector.eject(level, pos(), machine.sides(), Transfer.GASES, new GasTankHandler(gasOut, Automation.INTERNAL), 32_000);
        }
    }

    /** 1.0.4-beta (lot 5) : generateur : en plus, « surplus perdu » (1 : la reserve pleine perd une partie de la production). */
    @Override
    public void declareValues(UiValues.Builder values) {
        super.declareValues(values);
        if (generator != null) {
            values.add(UiValues.SURPLUS, () -> generator.wasting() ? 1 : 0);
        }
    }

    @Override
    public long production() {
        return generator == null ? 0 : generator.production();
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    public CompoundTag migrate(CompoundTag tag) {
        //MAJ Recettes (lot 9a) : cases et reservoirs sauves avec l'ancienne io remis a leur place (une seule fois) ; 1.0.5-beta (lot 39) :
        //objets restes sans place gardes si la machine a ete sauvee avant son premier tick
        CompoundTag read = IoMigration.reload(kind, tag, ioOverflow, false);
        ioMigrated = read != tag || !ioOverflow.isEmpty();
        return read;
    }

    @Override
    public void save(CompoundTag tag) {
        IoMigration.stamp(tag, ioOverflow);   // 1.0.5-beta (lot 39) : objets sans place gardes si sauvee avant le premier tick
        tag.putInt("progress", progress);
        if (generator != null) {
            generator.save(tag);
        } else {
            //Cles d'avant le lot 3 (toutes les machines d'atelier les portaient)
            tag.putInt("burn", 0);
            tag.putLong("burnPower", 0);
        }
        if (current != null) {
            tag.putString("recipe", current.getId().toString());
        }
        lock.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        //1.0.5-beta (lot 44) : remanents d'avant ranges dans la case Outil (sinon gardes, puis au sol au premier tick)
        if (ToolBay.move(kind.key, inputs, tool, ioOverflow)) {
            ioMigrated = true;
        }
        progress = tag.getInt("progress");
        if (generator != null) {
            generator.load(tag);
        }
        savedRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        lock.load(tag);
        dirty = true;
        fitsKnown = false;
        tankSync.changed();
    }

    // ------------------------------------------------------------------
    // Selecteur de recette
    // ------------------------------------------------------------------

    @Override
    public String recipeSource() {
        return RecipeSources.industrial(kind, false);
    }

    /** 1.0.5-beta (lot 44) : la case Outil (null : machine sans remanent). */
    @Nullable
    @Override
    public ItemSlot toolSlot() {
        return tool;
    }

    @Override
    public List<ItemSlot> toolSlots() {
        return tools;
    }

    /** 1.0.5-beta (lot 44) : outil rendu sans place (jamais pour une machine compacte : il revient a la case d'ou il vient) : au sol. */
    @Override
    public void spill(ItemStack stack) {
        IoMigration.release(level(), pos(), new ArrayList<>(List.of(stack)));
    }

    /** Entrees presentes (selecteur : recettes faisables) : cases d'entree, puis la case Outil. */
    @Override
    public List<ItemStack> presentItems() {
        return ToolBay.present(super.presentItems(), tool);
    }

    @Override
    public void recipeLockChanged() {
        dirty = true;
        fitsKnown = false;
        machine.changed();
        machine.syncNow();
    }
}
