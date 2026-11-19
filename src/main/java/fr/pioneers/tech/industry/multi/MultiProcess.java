package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.Surplus;
import fr.pioneers.tech.compute.AiChips;
import fr.pioneers.tech.compute.ComputeCore;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.tech.fusion.FusionCore;
import fr.pioneers.world.geology.DepositData;
import fr.pioneers.world.geology.Deposits;
import fr.pioneers.tech.industry.Extraction;
import fr.pioneers.tech.industry.GeneratorOutput;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IoTank;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.industry.recipe.RecipeMatch;
import fr.pioneers.tech.nuclear.FissionCore;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 5) : UNITE DE PROCEDE d'un multibloc industriel (sortie de {@link ControllerTile}), sur la meme base que les unites des
 * machines compactes ({@link MachineUnit} : {@code Access}, tick, sauvegarde) et le pendant de {@code IndustryProcess} des ateliers : lots
 * de recettes en parallele (une recette, {@code k} operations a la fois, entrees prises au depart, 80 % du temps et 75 % de l'energie par
 * operation d'une machine d'atelier du meme tier, tous les sous-produits), generateurs (FE par {@code GeneratorOutput}), puits et
 * plate-forme, pompe, foreuse. Les cases, reservoirs, chaleur et etat restent ceux du controleur (lus par {@link #c}) ; les cles de
 * sauvegarde sont celles d'avant le lot 5 ({@code batch}, {@code batchImpurity}, {@code batchBonus}, {@code batchCount}, {@code progress},
 * {@code duration}, {@code batchPower}, {@code batchPressure}).
 * <p>
 * Reacteurs, fusion et calcul ne passent pas par cette unite : leur coeur propre ({@code FissionCore}, {@code FusionCore},
 * {@code ComputeCore}) reste branche par le controleur (lot 6).
 */
final class MultiProcess extends MachineUnit {

    final ControllerTile c;
    @Nullable
    IndustrialRecipe batch;
    @Nullable
    ResourceLocation savedBatch;
    int batchCount, duration = 1, batchImpurity;
    double progress, batchBonus = 1;
    long batchPower;
    /** Pression du lot en cours : 1 (pression de procede fournie), 0,5 (sans). */
    double batchPressure = 1;
    /** Tier demande par la recette bloquee (-1 : aucun). */
    int tierNeeded = -1;
    /** Contenu change : chercher une recette au prochain tick ; sinon toutes les secondes ({@link #retry}). */
    boolean dirty = true;
    int retry;
    /** 1.0.4-beta (lot 26) : plantes de la recette en cours (ferme hydroponique, plantation) : etat recu par le client ({@link GrowView}). */
    final GrowView.State grow = new GrowView.State();

    MultiProcess(ControllerTile c) {
        super(c.access());
        this.c = c;
    }

    /** Le controleur appelle {@link #work()} a sa place dans son tick (apres la chaleur et les ports) ; rien d'autre ici. */
    @Override
    public void tick() {
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putDouble("batchPressure", batchPressure);
        //1.0.6 (GameTest mb_procede) : un lot relu mais pas encore resolu (savedBatch : la recette se retrouve au premier tick) est resauve
        //tel quel ; avant, un chunk sauve sans avoir tourne (bord de zone chargee) perdait le lot en cours et ses entrees deja prises
        ResourceLocation id = batch != null ? batch.getId() : savedBatch;
        if (id != null) {
            tag.putString("batch", id.toString());
            tag.putInt("batchImpurity", batchImpurity);
            tag.putDouble("batchBonus", batchBonus);
        }
        if (id != null || batchCount > 0) {
            //Lot en cours (la foreuse n'a pas de recette : compte et avancement seulement)
            tag.putInt("batchCount", batchCount);
            tag.putDouble("progress", progress);
            tag.putInt("duration", duration);
            tag.putLong("batchPower", batchPower);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        batchPressure = tag.contains("batchPressure") ? tag.getDouble("batchPressure") : 1;
        if (tag.contains("batch")) {
            savedBatch = ResourceLocation.tryParse(tag.getString("batch"));
            batchImpurity = tag.getInt("batchImpurity");
            batchBonus = tag.contains("batchBonus") ? tag.getDouble("batchBonus") : 1;
        }
        if (tag.contains("batchCount")) {
            batchCount = tag.getInt("batchCount");
            progress = tag.getDouble("progress");
            duration = Math.max(1, tag.getInt("duration"));
            batchPower = tag.getLong("batchPower");
        }
    }

    /** Un tick de travail (structure formee, ni reacteur ni fusion ni calcul, pas en surchauffe) : vrai si le multibloc a travaille. */
    boolean work() {
        if (fr.pioneers.space.mining.SpaceDrill.is(c.machine.key)) {
            return tickSpaceDrill();
        }
        if (fr.pioneers.space.giant.GasCollector.is(c.machine.key)) {
            return tickGasCollector();
        }
        if (c.machine.kind == IndustryMachine.Kind.DRILL) {
            return tickDrill();
        }
        if (c.machine.kind == IndustryMachine.Kind.WELL) {
            return tickWell();
        }
        if (c.machine.kind == IndustryMachine.Kind.PUMP) {
            return tickPump();
        }
        if (c.machine.kind == IndustryMachine.Kind.TANK) {
            c.status = ControllerTile.IDLE;
            return false;
        }
        if (batch == null) {
            if (!machine.canWork()) {
                c.status = ControllerTile.IDLE;
            } else if (dirty || savedBatch != null || --retry <= 0) {
                //Recherche de recette seulement quand le contenu change (ou toutes les secondes)
                dirty = false;
                retry = 20;
                start();
            }
        }
        return batch != null && run();
    }

    /** Lance un lot : la premiere recette dont on peut faire au moins une operation. */
    void start() {
        if ("synthesizer".equals(c.machine.key) && !ResearchEffects.has(ResearchNode.PLANET_SYNTHESIS)) {
            //0.20.34 : le synthetiseur attend la recherche « Synthese planetaire » (comme l'ancien synthetiseur planetaire)
            c.status = ControllerTile.RESEARCH;
            return;
        }
        //1.0.7 (lot 4) : port d'une megastructure : sur son astre, megastructure debloquee, carcasses MKV (recettes uniques)
        int mega = fr.pioneers.space.mega.Megastructures.byPort(c.machine.key);
        if (mega >= 0) {
            int gate = fr.pioneers.space.mega.MegaGate.status(level(), pos(), mega, c.structure());
            if (gate != ControllerTile.IDLE) {
                c.status = gate;
                return;
            }
        }
        //1.0.4-beta (lot 5) : un generateur reserve pleine (ou une chaudiere vapeur pleine) ne s'arrete plus : le lot part, le surplus est perdu
        boolean producer = producer();
        int cap = c.parallel();
        boolean full = false;
        //MAJ Recettes (lot 7) : verrou pose : seule la recette verrouillee est essayee (pas de combustible solide hors d'elle)
        IndustrialRecipe locked = c.lock.isSet() ? IndustryRecipes.byId(level(), c.machine, c.lock.id()) : null;
        tierNeeded = -1;
        List<IndustrialRecipe> candidates;
        if (IndustryRecipes.burnsSolid(c.machine) && (locked == null || savedBatch != null)) {
            if (savedBatch != null && savedBatch.getPath().startsWith("fuel/")) {
                //Reprise d'une combustion : la recette est refaite d'apres l'objet brule
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(savedBatch.getNamespace(),
                      savedBatch.getPath().replaceFirst("^fuel/", "")));
                savedBatch = null;
                IndustrialRecipe fuel = item == null ? null : IndustryRecipes.fuel(new ItemStack(item));
                if (fuel != null) {
                    batch = fuel;
                    restoreDuration(fuel);
                    return;
                }
            }
            candidates = new ArrayList<>();
            for (ItemSlot slot : c.inputs) {
                IndustrialRecipe fuel = IndustryRecipes.fuel(slot.getStack());
                if (fuel != null && !candidates.contains(fuel)) {
                    candidates.add(fuel);
                }
            }
            //1.0 : le generateur a combustion brule aussi liquides et gaz (ses recettes)
            if (c.machine.source != IndustryMachine.Source.FUEL) {
                candidates.addAll(c.recipes());
                if (savedBatch != null) {
                    for (IndustrialRecipe r : candidates) {
                        if (r.getId().equals(savedBatch)) {
                            batch = r;
                            savedBatch = null;
                            return;
                        }
                    }
                }
            }
            savedBatch = null;
            if (locked != null) {
                //Lot repris ou perdu : le verrou ne laisse ensuite que sa recette
                candidates = List.of(locked);
            }
        } else {
            candidates = RecipeMatch.candidates(c.recipes(), locked);
            if (savedBatch != null) {
                //Reprise d'un lot apres chargement : sa recette d'abord (meme hors du verrou : ses entrees sont deja prises)
                for (IndustrialRecipe r : c.recipes()) {
                    if (r.getId().equals(savedBatch)) {
                        batch = r;
                        savedBatch = null;
                        return;
                    }
                }
                //1.0.5-beta (lots 38 a 42, un seul mecanisme : BatchRefund) : lot d'une recette qui n'est plus a cette machine (retiree,
                //partie ailleurs, renommee, dominee par une recette basse) : repris par sa remplacante si elle prend exactement les memes
                //entrees, sinon ses entrees, deja prises, sont RENDUES (rien ne se perd, rien ne se cree)
                BatchRefund.Plan plan = BatchRefund.plan(level(), c.machine, savedBatch);
                if (plan.next() != null) {
                    batch = plan.next();
                    savedBatch = null;
                    return;
                }
                if (plan.inputs() != null) {
                    BatchRefund.give(c, plan.inputs(), batchCount);
                } else {
                    fr.pioneers.Pioneers.LOGGER.warn("Lot en cours de {} perdu : recette {} inconnue ({})", c.machine.key, savedBatch, c.getBlockPos());
                }
                batchCount = 0;
                progress = 0;
                savedBatch = null;
                machine.changed();
            }
        }
        for (IndustrialRecipe r : candidates) {
            if (r.tier() > c.tier()) {
                //MAJ Recettes (lots 1 et 7) : « Tier insuffisant : MKIII requis » ; la recette verrouillee, meme sans ses entrees
                if ((r == locked || RecipeMatch.supply(r, c, 1) > 0) && (tierNeeded < 0 || r.tier() < tierNeeded)) {
                    tierNeeded = r.tier();
                }
                continue;
            }
            if (c.machine.isGenerator() && r.energy() <= 0) {
                continue;
            }
            int supply = RecipeMatch.supply(r, c, cap);
            if (supply <= 0) {
                continue;
            }
            //MAJ Recettes (lot 9a) : rendement du minerai du Mineur-Fondeur proprietaire (vocation de la metallurgie)
            double bonus = r.ore() ? Tiers.oreBonus(c.tier()) * c.ownerFactor("miner.ore_yield") : 1;
            int k = producer ? RecipeMatch.possibleVenting(r, c, cap, c.impurity(r), bonus) : RecipeMatch.possible(r, c, cap, c.impurity(r), bonus);
            if (k <= 0) {
                full = true;
                continue;
            }
            RecipeMatch.consume(r, c, k);
            batch = r;
            batchCount = k;
            //Pression de procede : l'air est pris au depart du lot ; sans elle, le lot tourne a 50 %
            batchPressure = 1;
            if (r.pressure() > 0) {
                double need = (double) r.air() * k;
                if (c.air.getPressure() >= r.pressure() && c.air.getAir() >= need) {
                    c.air.extractAir(need, false);
                } else {
                    batchPressure = 0.5;
                }
            }
            progress = 0;
            //Purete et bonus figes au depart du lot (un changement de carcasse en cours de lot ne les modifie pas)
            batchImpurity = c.impurity(r);
            batchBonus = bonus;
            if (c.machine.isGenerator()) {
                //1.11.2 : GeneratorOutput (source unique, aussi lue par JEI et les infobulles) ; energie totale conservee malgre l'arrondi
                duration = GeneratorOutput.duration(r.time(), c.tier());
                batchPower = Math.round(k * GeneratorOutput.exactPerTick(r, c.tier(), true));
            } else {
                duration = Tiers.ticks(r.time(), c.tier(), r.tier(), true, c.machine);
                if (r.draws() != null) {
                    duration = (int) Math.max(1, Math.round(duration / c.ownerFactor("salvage.sort_speed")));
                }
                batchPower = opPower(k);
                if (r.energy() > 0) {
                    //0.20.34 : recette a energie totale (compresseur gravitationnel, synthetiseur) : r.energy() FE par operation, sur la duree
                    batchPower = Math.max(batchPower, Math.round(k * (double) r.energy() / duration));
                }
            }
            c.setChanged();
            if (GrowView.grows(c)) {
                //1.0.4-beta (lot 26) : les plantes de la recette sont semees chez le client
                machine.markSync();
            }
            return;
        }
        c.status = tierNeeded >= 0 ? ControllerTile.TIER : full ? ControllerTile.FULL : c.allEmpty() ? ControllerTile.IDLE : ControllerTile.NO_RECIPE;
    }
    /** 1.0.4-beta (lot 5) : production perdue sortie pleine (generateurs : energie ; chaudiere : vapeur ; interface : « Surplus perdu »). */
    final Surplus surplus = new Surplus();

    /** 1.0.4-beta (lot 5) : valeur d'interface « surplus perdu » du controleur (1 : une perte recente). */
    long surplusValue() {
        return level() != null && surplus.active(level().getGameTime()) ? 1 : 0;
    }

    /** 1.0.4-beta (lot 5) : producteur (generateur ou chaudiere industrielle) : regle {@code api/machine/unit/Surplus}. */
    boolean producer() {
        return c.machine.isGenerator() || c.machine == IndustryMachine.BOILER;
    }

    /** Fluides et gaz en sortie (mB), pour mesurer ce qu'un lot de producteur a vraiment range. */
    private long outputFill() {
        long n = 0;
        for (var t : c.fluidOutputs()) {
            n += t.getFluidAmount();
        }
        for (var t : c.gasOutputs()) {
            n += t.getStack().getAmount();
        }
        return n;
    }

    /** Fluides et gaz que {@code k} operations de {@code r} produisent (mB). */
    private static long expected(IndustrialRecipe r, int k) {
        long n = 0;
        for (var f : r.fluidResults()) {
            n += (long) f.getAmount() * k;
        }
        for (var g : r.gasResults()) {
            n += g.stack().getAmount() * k;
        }
        return n;
    }

    /** FE/t de k operations : 75 % de l'energie par operation sur 80 % du temps. La chaudiere brule son combustible (pas d'electricite) ; la
     * cheminee industrielle tire seule (lot 22). */
    long opPower(int k) {
        if (c.machine == IndustryMachine.BOILER || c.machine == IndustryMachine.EXHAUST_STACK) {
            return 0;                                                   //1.0.4-beta (lot 22) : cheminee industrielle = tirage naturel
        }
        return Math.round(k * Tiers.power(c.tier()) * Tiers.INDUSTRIAL_ENERGY / Tiers.INDUSTRIAL_TIME);
    }
    /** Reprise d'une combustion sauvee : duree recalculee si elle manquait. */
    void restoreDuration(IndustrialRecipe fuel) {
        if (duration <= 1) {
            duration = GeneratorOutput.duration(fuel.time(), c.tier());
        }
    }
    boolean run() {
        //0.17.2 : puce d'IA (ou noyau d'usine) : vitesse et energie
        int ai = c.aiLevel();
        double factor = c.thermalFactor() * batchPressure * c.climateFactor() * AiChips.speedFactor(ai);
        if (c.machine.isGenerator()) {
            long made = Math.round(batchPower * factor);
            surplus.note(level().getGameTime(), made, machine.energy().insert(made, false));
            c.status = ControllerTile.WORKING;
        } else {
            //Chaleur recue : remplace une part de l'electricite des que le corps est a sa temperature de travail
            long saved = 0;
            if (c.machine.heat > 0 && batchPower > 0 && c.thermal.heatCredit > 0) {
                double surplus = (c.body.temperature - c.workTemperature(batch)) * c.body.getHeatCapacity();
                if (surplus > 0) {
                    saved = (long) Math.min(Math.min(c.thermal.heatCredit, surplus), batchPower * ControllerTile.HEAT_SHARE);
                }
            }
            //Energie par operation x energyFactor ; l'operation va plus vite : la puissance suit la vitesse
            if (machine.use(Math.round((batchPower - saved) * AiChips.energyFactor(ai) * AiChips.speedFactor(ai)))) {
                c.status = ControllerTile.WORKING;
                if (saved > 0) {
                    c.thermal.heatCredit -= saved;
                    c.body.addHeat(-saved);
                    c.thermal.savedRate += saved * 0.05;
                }
            } else {
                c.status = ControllerTile.NO_ENERGY;
                return false;
            }
        }
        if (c.machine.heat > 0) {
            c.body.addHeat(batchCount * c.heatPerOp());
        }
        progress += factor;
        if (progress >= duration) {
            finish();
        } else if (GrowView.grows(c) && level() != null && level().getGameTime() % GrowView.RESYNC == 0) {
            //1.0.4-beta (lot 26) : croissance recalee chez le client toutes les 10 s
            machine.markSync();
        }
        c.setChanged();
        return true;
    }
    void finish() {
        IndustrialRecipe r = batch;
        if (r != null) {
            if (producer()) {
                //1.0.4-beta (lot 5) : vapeur, gaz et fluides produits jusqu'au bord, le reste perdu
                long before = outputFill();
                RecipeMatch.produceVenting(r, c, batchCount, level().random, batchImpurity, batchBonus);
                surplus.note(level().getGameTime(), expected(r, batchCount), outputFill() - before);
            } else {
                RecipeMatch.produce(r, c, batchCount, level().random, batchImpurity, batchBonus);
            }
            IndustryEvents.award(level(), pos(), c.machine, r);
            //1.9 (MAJ RP) : XP automatique du proprietaire (20 %, s'il est la)
            if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
                //MAJ Recettes (lot 9a) : skill du procede ou de la machine (voies de metallurgie, machines nouvelles et refondues)
                fr.pioneers.core.profile.SkillHooks.autoRecipe(sl, pos(), c.owner(), c.machine, r, true);
            }
            if (batchCount >= Tiers.parallelCap(c.tier()) && batchCount >= 4) {
                IndustryEvents.award(level(), pos(), "industry_full_parallel");
            }
        }
        batch = null;
        batchCount = 0;
        progress = 0;
        batchPower = 0;
        batchImpurity = 0;
        batchBonus = 1;
        batchPressure = 1;
        dirty = true;
        if (GrowView.grows(c)) {
            //1.0.4-beta (lot 26) : recolte : le client voit le lot suivant (ou plus rien)
            machine.markSync();
        }
    }
    /** Puits : fraction de mB reportee d'un tick a l'autre, une par tete de puits (colonnes montantes de la plate-forme). */
    double[][] carry = new double[1][1];
    /**
     * Derrick, plate-forme offshore : le derrick puise sous son controleur avec tout son parallelisme ; la plate-forme puise sous chacune de ses
     * colonnes montantes (2 operations par colonne), chacune dans le gisement de son propre chunk.
     */
    boolean tickWell() {
        if (!machine.canWork()) {
            c.status = ControllerTile.IDLE;
            return false;
        }
        int k = c.parallel();
        long power = opPower(k);
        if (machine.energy().getStored() < power) {
            c.status = ControllerTile.NO_ENERGY;
            return false;
        }
        Structure.Result s = c.lifecycle.structure();
        List<BlockPos> heads = c.machine.freeform ? (c.offshore() != null ? c.offshore().complete() : s.risers()) : List.of(pos());
        if (heads.isEmpty()) {
            //0.20.37 : aucune colonne au fond : la plate-forme attend ses tubes de production
            c.status = ControllerTile.NO_TUBES;
            return false;
        }
        int max = c.machine.freeform ? Math.max(1, Tiers.parallelCap(c.tier()) / Structure.PER_MODULE) : 1;
        double mult = (1 << c.tier()) * Tiers.INDUSTRIAL_YIELD * (c.machine.freeform ? Structure.PER_MODULE : k) / 2.0;
        int ok = 0, last = Extraction.NO_DEPOSIT;
        int n = Math.min(heads.size(), max);
        if (carry.length < n) {
            carry = Arrays.copyOf(carry, n);
            for (int i = 0; i < n; i++) {
                if (carry[i] == null) {
                    carry[i] = new double[1];
                }
            }
        }
        for (int i = 0; i < n; i++) {
            int r = Extraction.well((ServerLevel) level(), heads.get(i), c.machine, mult, c.fluidIn.isEmpty() ? null : c.fluidIn.get(0),
                  c.fluidOut.isEmpty() ? null : c.fluidOut.get(0), c.gasOut.isEmpty() ? null : c.gasOut.get(0), c.gasOut.size() > 1 ? c.gasOut.get(1) : null, carry[i],
                  c.air);
            if (r == Extraction.OK) {
                ok++;
            } else {
                last = r;
            }
        }
        if (ok == 0) {
            c.status = switch (last) {
                case Extraction.EXHAUSTED -> ControllerTile.EXHAUSTED;
                case Extraction.FULL -> ControllerTile.FULL;
                default -> ControllerTile.NO_DEPOSIT;
            };
            return false;
        }
        machine.use(power);
        c.body.addHeat(k * c.heatPerOp());
        c.status = ControllerTile.WORKING;
        if (level().getGameTime() % 200 == 0) {
            IndustryEvents.award(level(), pos(), c.machine);
            awardAbsorbedWells(heads.subList(0, n), k);
        }
        if (level().getGameTime() % 40 == 0) {
            level().playSound(null, pos(), PiBronze.SOUND_DRILL.get(), SoundSource.BLOCKS, 0.4F, 0.7F);
        }
        c.setChanged();
        return true;
    }

    /**
     * 1.0.4-beta (lot 13, fusion des machines) : le chevalet de pompage a absorbe le puits de saumure, la tete de puits de gaz et le derrick
     * ({@code GeneratedFusions.WELLS}) : leurs progres ({@code industry_<puits>}) se gagnent en pompant un gisement de leur categorie (saumure,
     * gaz) ; le derrick (« quatre puits d'un coup ») : un gisement de petrole ou de gaz pompe sur quatre operations a la fois au moins.
     */
    private void awardAbsorbedWells(List<BlockPos> heads, int parallel) {
        for (BlockPos head : heads) {
            fr.pioneers.world.geology.fluid.FluidDeposits.Deposit deposit = fr.pioneers.world.geology.fluid.FluidDeposits.at((ServerLevel) level(), head);
            if (deposit == null) {
                continue;
            }
            String category = deposit.type().category();
            for (java.util.Map.Entry<String, List<String>> e : fr.pioneers.tech.industry.GeneratedFusions.WELLS.entrySet()) {
                if (!c.machine.key.equals(fr.pioneers.tech.industry.GeneratedFusions.base(e.getKey())) || !e.getValue().contains(category)) {
                    continue;
                }
                if ("derrick".equals(e.getKey()) && parallel < 4) {
                    continue;
                }
                IndustryEvents.award(level(), pos(), "industry_" + e.getKey());
            }
        }
    }
    /** Pompe industrielle : sous le centre du fond de la structure, autant de blocs par passe que d'operations en parallele. */
    boolean tickPump() {
        if (!machine.canWork()) {
            c.status = ControllerTile.IDLE;
            return false;
        }
        int k = c.parallel();
        long power = opPower(k);
        if (machine.energy().getStored() < power) {
            c.status = ControllerTile.NO_ENERGY;
            return false;
        }
        duration = Math.max(1, 20 >> c.tier());
        if (++progress < duration) {
            if (c.status == ControllerTile.WORKING) {
                machine.use(power);
            }
            return c.status == ControllerTile.WORKING;
        }
        progress = 0;
        Structure.Result s = c.lifecycle.structure();
        BlockPos bottom = new BlockPos((s.min().getX() + s.max().getX()) / 2, s.min().getY(), (s.min().getZ() + s.max().getZ()) / 2);
        int r = c.fluidOut.isEmpty() ? Extraction.FULL
              : Extraction.pump((ServerLevel) level(), bottom, c.tier(), k, c.fluidOut.get(0));
        c.status = r == Extraction.OK ? ControllerTile.WORKING : r == Extraction.FULL ? ControllerTile.FULL : ControllerTile.NO_FLUID;
        if (c.status != ControllerTile.WORKING) {
            return false;
        }
        machine.use(power);
        c.body.addHeat(k * c.heatPerOp());
        c.setChanged();
        return true;
    }
    /**
     * 1.0.7 (lot 6) : la foreuse spatiale ({@link fr.pioneers.space.mining.SpaceDrill}) : filon profond de l'astre sous elle, structure MKV,
     * minerai principal du filon (et son sous-produit selon sa chance), reserve de la region.
     */
    boolean tickSpaceDrill() {
        ServerLevel sl = (ServerLevel) level();
        fr.pioneers.space.mining.MiningVein vein = fr.pioneers.space.mining.SpaceDrill.vein(sl, pos());
        if (vein == null) {
            c.status = ControllerTile.NO_DEPOSIT;
            return false;
        }
        if (c.structure() == null || c.structure().tier() < fr.pioneers.logistics.transport.CableTier.STELLAR.ordinal()) {
            c.status = ControllerTile.MKV_CASINGS;
            return false;
        }
        int left = fr.pioneers.space.mining.SpaceDrill.left(sl, pos());
        if (left <= 0) {
            c.status = ControllerTile.EXHAUSTED;
            return false;
        }
        if (!machine.canWork()) {
            c.status = ControllerTile.IDLE;
            return false;
        }
        if (batchCount <= 0) {
            batchCount = Math.min(c.parallel(), left);
            duration = Tiers.ticks(fr.pioneers.space.mining.SpaceDrill.TIME, c.tier(), 0, true);
            batchPower = opPower(batchCount);
            progress = 0;
        }
        ItemStack ore = new ItemStack(vein.getPrimary(), batchCount * fr.pioneers.space.mining.SpaceDrill.YIELD);
        if (!ItemHandlerHelper.insertItemStacked(new ItemSlots(c.outputs, Automation.INTERNAL), ore.copy(), true).isEmpty()) {
            c.status = ControllerTile.FULL;
            return false;
        }
        if (!machine.use(batchPower)) {
            c.status = ControllerTile.NO_ENERGY;
            return false;
        }
        c.status = ControllerTile.WORKING;
        progress += c.thermalFactor();
        if (progress >= duration) {
            ItemHandlerHelper.insertItemStacked(new ItemSlots(c.outputs, Automation.INTERNAL), ore, false);
            int extra = 0;
            for (int i = 0; i < batchCount; i++) {
                extra += sl.random.nextFloat() < vein.byproductChance ? 1 : 0;
            }
            if (extra > 0) {
                ItemHandlerHelper.insertItemStacked(new ItemSlots(c.outputs, Automation.INTERNAL), new ItemStack(vein.getByproduct(), extra), false);
            }
            fr.pioneers.space.mining.SpaceDrill.mine(sl, pos(), batchCount);
            batchCount = 0;
            progress = 0;
        }
        c.setChanged();
        return true;
    }

    /**
     * 1.0.7 (lot 7) : collecteur de gaz (MKV) : dans une geante, une operation capte le melange de gaz de la geante (un gaz par reservoir
     * de sortie, mB x parallelisme) ; aucune reserve ; hors d'une geante, rien.
     */
    boolean tickGasCollector() {
        int row = fr.pioneers.space.giant.GasCollector.row(level());
        if (row < 0) {
            c.status = ControllerTile.NO_DEPOSIT;
            return false;
        }
        if (c.structure() == null || c.structure().tier() < fr.pioneers.logistics.transport.CableTier.STELLAR.ordinal()) {
            c.status = ControllerTile.MKV_CASINGS;
            return false;
        }
        if (!machine.canWork()) {
            c.status = ControllerTile.IDLE;
            return false;
        }
        if (batchCount <= 0) {
            batchCount = c.parallel();
            duration = Tiers.ticks(fr.pioneers.space.giant.GasCollector.TIME, c.tier(), 0, true);
            batchPower = opPower(batchCount);
            progress = 0;
        }
        fr.pioneers.api.gas.Gas[] gases = fr.pioneers.space.giant.GasCollector.gases(row);
        int[] amounts = fr.pioneers.space.giant.GasCollector.amounts(row);
        int n = Math.min(gases.length, c.gasOut.size());
        for (int k = 0; k < n; k++) {
            GasStack gas = GasStack.of(gases[k], (long) amounts[k] * batchCount);
            if (!c.gasOut.get(k).insert(gas, Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
                c.status = ControllerTile.FULL;
                return false;
            }
        }
        if (!machine.use(batchPower)) {
            c.status = ControllerTile.NO_ENERGY;
            return false;
        }
        c.status = ControllerTile.WORKING;
        progress += c.thermalFactor();
        if (progress >= duration) {
            for (int k = 0; k < n; k++) {
                c.gasOut.get(k).insert(GasStack.of(gases[k], (long) amounts[k] * batchCount), Action.EXECUTE, Automation.INTERNAL);
            }
            batchCount = 0;
            progress = 0;
        }
        c.setChanged();
        return true;
    }

    boolean tickDrill() {
        Deposits.Deposit deposit = Deposits.at((ServerLevel) level(), pos().getX() >> 4, pos().getZ() >> 4);
        if (deposit == null) {
            c.status = ControllerTile.NO_DEPOSIT;
            return false;
        }
        if (deposit.type().tier() > 2) {
            c.status = ControllerTile.TOO_DEEP;
            return false;
        }
        int left = deposit.left((ServerLevel) level());
        if (left <= 0) {
            c.status = ControllerTile.EXHAUSTED;
            return false;
        }
        if (!machine.canWork()) {
            c.status = ControllerTile.IDLE;
            return false;
        }
        if (batchCount <= 0) {
            batchCount = Math.min(c.parallel(), left);
            duration = Tiers.ticks(ControllerTile.DRILL_TIME, c.tier(), 0, true);
            batchPower = opPower(batchCount);
            progress = 0;
        }
        ItemStack ore = new ItemStack(deposit.type().result(), batchCount);
        if (!ItemHandlerHelper.insertItemStacked(new ItemSlots(c.outputs, Automation.INTERNAL), ore.copy(), true).isEmpty()) {
            c.status = ControllerTile.FULL;
            return false;
        }
        if (!machine.use(batchPower)) {
            c.status = ControllerTile.NO_ENERGY;
            return false;
        }
        c.status = ControllerTile.WORKING;
        c.body.addHeat(batchCount * c.heatPerOp());
        //Eau de forage : x1,5 plus vite avec de l'eau (100 mB par minerai)
        double factor = c.thermalFactor();
        if (!c.fluidIn.isEmpty() && c.fluidIn.get(0).getFluidAmount() >= 100 * batchCount) {
            factor *= 1.5;
            if (progress + factor >= duration) {
                ((IoTank) c.fluidIn.get(0)).drainInternal(100 * batchCount, FluidAction.EXECUTE);
            }
        }
        progress += factor;
        if (progress >= duration) {
            ItemHandlerHelper.insertItemStacked(new ItemSlots(c.outputs, Automation.INTERNAL), ore, false);
            DepositData.get((ServerLevel) level()).mine(deposit.region(), batchCount);
            IndustryEvents.award(level(), pos(), c.machine);
            batchCount = 0;
            progress = 0;
        }
        if (level().getGameTime() % 40 == 0) {
            level().playSound(null, pos(), PiBronze.SOUND_DRILL.get(), SoundSource.BLOCKS, 0.5F, 1.2F);
        }
        c.setChanged();
        return true;
    }
}
