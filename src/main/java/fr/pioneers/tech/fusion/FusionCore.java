package fr.pioneers.tech.fusion;

import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.multiblock.MultiblockLogic;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.IndustryShape;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.tech.nuclear.FissionCore;
import fr.pioneers.tech.nuclear.Meltdown;
import fr.pioneers.tech.nuclear.ReactorBlock;
import fr.pioneers.tech.nuclear.ReactorView;
import fr.pioneers.core.registries.PiIndustry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Coeur d'un reacteur a fusion (0.17.2), tenu par le controleur du multibloc (kind FUSION).
 * <ul>
 *     <li><b>Etats</b> : arret, chauffage (injecteurs de neutres et gyrotron, FE pris a la reserve d'energie du controleur, remplie par les
 *     ports d'energie d'entree), ignition, combustion entretenue (le chauffage peut alors etre coupe), arret commande ;</li>
 *     <li><b>plasma</b> : temperature T (millions de K) ; dT = chauffage + alpha . P/Pmax - pertes . T (chauffage regule : nul au-dela de
 *     1,05 x le pic) ; puissance P = Pmax . n^2 . s(T),
 *     n densite (gaz en reserve : pleine a 1 000 mB de chaque), s(T) = exp(-ln^2(T/Tpic)/0,5) ; facteur Q = P / chauffage ;</li>
 *     <li><b>bobines</b> supraconductrices : 1 mB/t d'helium liquide chacune quand le champ est tenu (l'helium evapore ressort en gaz par la
 *     sortie d'helium : 8 mB de gaz par mB, a reliquefier) et une energie de confinement (1 % de Pmax) ; sans helium ou sans energie le champ
 *     tombe ; champ sous 50 % avec un plasma chaud : <b>disruption</b> (tokamak ; stellarator : seulement par manque d'helium, sinon le
 *     plasma s'eteint sans casse) ;</li>
 *     <li><b>tokamak</b> : D + T, chaleur dans le corps du multibloc (vapeur par l'eau du reservoir de caloporteur), helium 4 en sortie ;
 *     couvertures : rapport de surgeneration 1,1 x min(1, couvertures / necessaires), 1 bille de lithium par 1 000 mB de tritium ; le tritium
 *     produit retourne d'abord au reservoir d'entree (boucle fermee), le surplus sort ; disruption aussi par perte de densite (plus de
 *     combustible en pleine combustion) et par surchauffe (1,25 x nominale) ;</li>
 *     <li><b>stellarator</b> : D + He-3, 70 % en electricite (+5 % par convertisseur direct, 90 % au plus) poussee par les ports d'energie en
 *     sortie, le reste en chaleur ; il ne disrupte pas par densite ni surchauffe (il s'eteint).</li>
 * </ul>
 */
public final class FusionCore implements MultiblockLogic {

    /** Pertes du plasma par tick (fraction de T) ; chauffage alpha ; chauffage par unite (fractions de pertes au pic). */
    public static final double LOSS = 0.004, ALPHA = 1.25, HEAT_RATE = 0.42;
    /** Energie de chauffage par unite (part de Pmax, FE/t) ; energie de confinement des bobines (part de Pmax, FE/t). */
    public static final double HEAT_FE = 0.015, COIL_FE = 0.01;
    /** Helium liquide par bobine et par tick (mB) ; gaz rendu par mB evapore. */
    public static final int HELIUM_PER_COIL = 1, BOILOFF_GAS = 8;
    /** Reserve de gaz pour une densite pleine (mB). */
    public static final double DENSITY_FULL = 1_000;
    /** Tokamak : rapport de surgeneration ; tritium par bille de lithium (mB). */
    public static final double BREEDING_RATIO = 1.1, TRITIUM_PER_PEBBLE = 1_000;
    /** Stellarator : part electrique, gain par convertisseur, plafond. */
    public static final double ELECTRIC_SHARE = 0.7, CONVERTER_GAIN = 0.05, ELECTRIC_MAX = 0.9;
    public static final int CONVERTERS_MAX = 4;
    /** Duree du flash de disruption (ticks). */
    public static final int FLASH = 60;
    /** 0.20.46 (audit) : gaz helium, cherche une fois (registre fige) au lieu de chaque tick. */
    private static final ResourceLocation HELIUM = Pioneers.rl("helium");
    @Nullable
    private static Gas helium;

    @Nullable
    private static Gas helium() {
        Gas gas = helium;
        if (gas == null) {
            gas = PioneersAPI.gas(HELIUM);
            helium = gas;
        }
        return gas;
    }

    private final ControllerTile host;
    public final FusionType type;
    /** 1.0 (lot 3 bis) : blocs qui s'allument quand le plasma tient ; dernier etat peint (-1 : inconnu). */
    private final List<BlockPos> glowPositions = new ArrayList<>();
    private int paintedGlow = -1;
    /** Pieces et modules comptes a la validation. */
    private int coils, injectors, solenoids, modules, plasmaCells;
    private double cachedCapacity = 10_000;
    private int cachedTier;
    @Nullable
    private BlockPos cachedMin, cachedMax;
    /** Etat du plasma. */
    private boolean heating, stopping, ignitedOnce, selfSufficient;
    private double plasma, density, field, power, heatingFe, heatingFrac, electric, coilFe;
    private boolean heliumShort, energyShort;
    /** Combustible et produits fractionnaires en attente (mB). */
    private double fuelDebt, ashCredit, bredCredit, lithium;
    /** Debits lisses (mB/t) du tritium brule et produit ; ticks d'autosuffisance d'affilee. */
    private double burnRate, bredRate;
    private int sufficientTicks, flash, overheatTicks;
    private long sentSignature = Long.MIN_VALUE;
    private int sinceSync;

    public FusionCore(ControllerTile host, FusionType type) {
        this.host = host;
        this.type = type;
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    /** Appele par le controleur apres chaque validation reussie. */
    public void onStructure(Structure.Result s, Level level) {
        coils = injectors = solenoids = 0;
        //1.0 (lot 3 bis) : blocs fonctionnels (ReactorBlock) : couverture / convertisseur comptes par la structure
        //(s.modules()), plasma = cases vides de la chambre (s.volume()) ; une bobine = environ 18 blocs (16 en D pour le tokamak), un injecteur = 3 blocs (1.0.4-beta, lot 23 : integres)
        modules = s.modules();
        plasmaCells = Math.max(1, s.volume());
        int coilBlocks = 0, solenoidBlocks = 0;
        glowPositions.clear();
        if (s.members() != null) {
            for (BlockPos p : s.members()) {
                if (!level.isLoaded(p) || !(level.getBlockState(p).getBlock() instanceof ReactorBlock rb)) {
                    continue;
                }
                switch (rb.kind) {
                    case TOROIDAL_COIL, HELICAL_COIL -> coilBlocks++;
                    case SOLENOID_COIL -> solenoidBlocks++;
                    default -> {
                    }
                }
                glowPositions.add(p.immutable());
            }
        }
        solenoids = solenoidBlocks > 0 ? 1 : 0;
        coils = (coilBlocks > 0 ? Math.max(1, coilBlocks / 18) : 0) + solenoids;
        //1.0.4-beta (lot 23, demande de Guillaume) : plus de bloc d'injecteur de neutres : l'injection est integree au tokamak forme, autant
        //d'injecteurs que l'ancien gabarit en portait (IndustryShape.builtInInjectors)
        injectors = type == FusionType.TOKAMAK ? IndustryShape.builtInInjectors((s.width() - 9) / 2) : 0;
        paintedGlow = -1;
        cachedCapacity = Math.max(10_000, s.volume() * ControllerTile.CAPACITY_PER_BLOCK);
        cachedTier = Math.max(0, Math.min(3, s.tier()));
        cachedMin = s.min();
        cachedMax = s.max();
        sentSignature = Long.MIN_VALUE;
    }

    public double cachedCapacity() {
        return cachedCapacity;
    }

    /** Puissance de fusion maximale de cette structure (J/t). */
    public double maxPower() {
        return Math.min(type.maxPower, Math.max(type.perCell, plasmaCells * type.perCell));
    }

    /** Unites de chauffage : gyrotron du controleur (1 ; 2 pour le stellarator, sans injecteurs) + injecteurs de neutres. */
    public int heatingUnits() {
        return type == FusionType.TOKAMAK ? 1 + injectors : 2;
    }

    /** Couvertures tritigenes (tokamak) ou convertisseurs directs (stellarator) : les modules interieurs. */
    public int modules() {
        return modules;
    }

    public int coils() {
        return coils;
    }

    public int injectors() {
        return injectors;
    }

    public int solenoids() {
        return solenoids;
    }

    // ------------------------------------------------------------------
    // Commandes (interface, automate, noyau d'usine)
    // ------------------------------------------------------------------

    public boolean heating() {
        return heating;
    }

    /** Allume ou coupe le chauffage (allumer annule un arret commande). */
    public void setHeating(boolean on) {
        heating = on;
        if (on) {
            stopping = false;
        }
        host.syncReactor();
    }

    /** Arret commande : chauffage coupe, injection de combustible arretee ; le plasma se refroidit sans disruption. */
    public void shutdown() {
        heating = false;
        if (plasma > 1) {
            stopping = true;
        }
        host.syncReactor();
    }

    public boolean stopping() {
        return stopping;
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    /** Temperature du plasma (millions de K). */
    public double plasmaTemperature() {
        return plasma;
    }

    /** Puissance de fusion instantanee (J/t). */
    public double power() {
        return power;
    }

    public double powerFraction() {
        return Math.min(1, power / maxPower());
    }

    /** Energie de chauffage prise ce tick (FE/t). */
    public double heatingPower() {
        return heatingFe;
    }

    /** Facteur Q : puissance de fusion / chauffage ; infini (positif) en combustion entretenue sans chauffage. */
    public double q() {
        if (heatingFe > 0) {
            return power / heatingFe;
        }
        return power > 0 ? Double.POSITIVE_INFINITY : 0;
    }

    /** Champ magnetique des bobines (0..1). */
    public double field() {
        return field;
    }

    /** Densite du plasma (0..1). */
    public double density() {
        return density;
    }

    /** Electricite directe produite ce tick (FE/t ; stellarator). */
    public double electric() {
        return electric;
    }

    /** Part de la puissance de fusion convertie en electricite (stellarator ; 0 pour le tokamak). */
    public double electricShare() {
        return type == FusionType.STELLARATOR ? Math.min(ELECTRIC_MAX, ELECTRIC_SHARE + CONVERTER_GAIN * modules) : 0;
    }

    /** Rapport de surgeneration effectif du tritium (tokamak ; 0 sans couvertures). */
    public double breedingRatio() {
        if (!type.tritium()) {
            return 0;
        }
        return BREEDING_RATIO * Math.min(1, modules / (double) FusionType.blanketsNeeded(plasmaCells + modules));
    }

    /** Le plasma brule par lui-meme (chauffage alpha >= pertes). */
    public boolean ignited() {
        return power > 0.05 * maxPower() && ALPHA * LOSS * type.peak * power / maxPower() >= LOSS * plasma;
    }

    /** Plasma present (chaud) ou chauffage en cours. */
    public boolean running() {
        return plasma > 1 || heating;
    }

    public boolean heliumShort() {
        return heliumShort;
    }

    public boolean energyShort() {
        return energyShort;
    }

    /** Flash de disruption en cours (rendus). */
    public boolean disrupted() {
        return flash > 0;
    }

    /**
     * Risque de disruption (0..1) : champ qui faiblit, helium ou energie qui manquent, densite qui s'effondre en combustion (tokamak),
     * surchauffe des carcasses. 0 si le plasma est froid. Le noyau d'usine coupe le chauffage (puis arrete) au-dela de 0,5.
     */
    public double disruptionRisk() {
        if (plasma < disruptMin()) {
            return 0;
        }
        double r = 0;
        if (heliumShort || energyShort) {
            r = Math.max(r, 0.6);
        }
        r = Math.max(r, Math.min(1, (1 - field) * 2));
        if (type == FusionType.TOKAMAK && !stopping && plasma > type.ignition()) {
            r = Math.max(r, Math.min(1, Math.max(0, (0.4 - density) / 0.25)));
            double nominal = Tiers.nominal(host.tier());
            r = Math.max(r, Math.min(1, Math.max(0, (host.body.getTemperature() / nominal - 1) * 4)));
        }
        return r;
    }

    private double disruptMin() {
        return type.peak * 0.15;
    }

    // ------------------------------------------------------------------
    // Gaz, fluides, objets acceptes
    // ------------------------------------------------------------------

    /** Reservoir de gaz d'entree {@code tank} : 0 = deuterium, 1 = tritium (tokamak) ou helium 3 (stellarator). */
    public boolean acceptsGas(int tank, Gas gas) {
        ResourceLocation id = gas.getRegistryName();
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return tank == 0 ? path.equals("deuterium") : path.equals(type.tritium() ? "tritium" : "helium_3");
    }

    /** Port de fluide d'entree : l'helium liquide des bobines (l'eau va au reservoir de caloporteur). */
    public boolean acceptsFluid(FluidStack stack) {
        return stack.getFluid().isSame(PiIndustry.fluid("liquid_helium"));
    }

    /** Cases d'objets d'entree : billes de lithium (tokamak). */
    public boolean accepts(ItemStack stack) {
        return type.tritium() && stack.is(PiIndustry.item("lithium_ceramic_pebbles"));
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    /**
     * Structure defaite : un plasma chaud se perd d'un coup (disruption sans carcasses brulees). Si une partie du tore est dans un troncon
     * non charge (chargement du monde, bord de zone chargee), rien ne bouge : l'etat est gele jusqu'a la prochaine validation.
     */
    public void unformed() {
        Level world = host.getLevel();
        if (world != null && cachedMin != null && cachedMax != null && (plasma > 0 || field > 0)) {
            for (BlockPos corner : new BlockPos[]{cachedMin, cachedMax, new BlockPos(cachedMin.getX(), cachedMin.getY(), cachedMax.getZ()),
                  new BlockPos(cachedMax.getX(), cachedMin.getY(), cachedMin.getZ())}) {
                if (!world.isLoaded(corner)) {
                    return;
                }
            }
        }
        if (plasma > disruptMin() && world instanceof ServerLevel level) {
            disrupt(level, false);
        } else if (plasma > 0 || heating) {
            plasma = 0;
            power = 0;
            heating = false;
            stopping = false;
        }
        field = Math.max(0, field - 0.05);
        power = 0;
        electric = heatingFe = coilFe = 0;
        if (flash > 0) {
            flash--;
        }
    }

    /** Un tick du reacteur forme (serveur). Retourne vrai si la fusion produit. */
    public boolean tick() {
        Level level = host.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        if (flash > 0) {
            flash--;
        }
        double pmax = maxPower();
        EnergyBuffer energy = host.getEnergy();
        // --- bobines : helium liquide et energie de confinement
        boolean wanted = heating || plasma > 1;
        heliumShort = energyShort = false;
        coilFe = 0;
        if (wanted && coils > 0) {
            int need = coils * HELIUM_PER_COIL;
            List<FluidTank> tanks = host.fluidInTanks();
            FluidTank he = tanks.isEmpty() ? null : tanks.get(0);
            int drained = 0;
            if (he != null && !he.isEmpty() && acceptsFluid(he.getFluid())) {
                drained = he.getFluidAmount() >= need ? need : 0;
                if (drained > 0) {
                    he.drain(drained, FluidAction.EXECUTE);
                    //Boucle cryogenique : l'helium evapore ressort en gaz (8 mB par mB) a reliquefier ; perdu si la sortie est pleine
                    Gas gas = helium();
                    if (gas != null && !host.gasOutTanks().isEmpty()) {
                        host.gasOutTanks().get(0).insert(GasStack.of(gas, (long) drained * BOILOFF_GAS), Action.EXECUTE, Automation.INTERNAL);
                    }
                }
            }
            heliumShort = drained < need;
            long want = Math.round(pmax * COIL_FE);
            long got = energy.extract(want, false);
            coilFe = got;
            energyShort = got < want;
            if (!heliumShort && !energyShort) {
                field = Math.min(1, field + 0.02);
            } else {
                field = Math.max(0, field - (heliumShort ? 0.1 : 0.05));
            }
        } else {
            field = Math.max(0, field - 0.02);
        }
        boolean confined = field >= 0.9;
        // --- densite : combustible en reserve (aucune injection pendant un arret commande)
        List<BasicGasTank> in = host.gasInTanks();
        long d = in.isEmpty() ? 0 : in.get(0).getStack().getAmount();
        long x = in.size() < 2 ? 0 : in.get(1).getStack().getAmount();
        double target = stopping || !confined ? 0 : Math.min(1, Math.min(d, x) / DENSITY_FULL);
        density += (target - density) * (stopping ? 0.05 : 0.1);
        if (density < 1e-4) {
            density = 0;
        }
        // --- puissance de fusion
        double thermal = thermalFactor();
        power = confined && plasma > 1 ? pmax * density * density * reactivity(plasma) * thermal : 0;
        // --- chauffage (seulement plasma confine et alimente)
        heatingFe = 0;
        heatingFrac = 0;
        if (heating && confined && density >= 0.3) {
            //Regulation du chauffage : pleine puissance sous le pic, rien au-dela de 1,05 x le pic (le plasma s'y entretient seul)
            double reg = Math.min(1, Math.max(0, (type.peak * 1.05 - plasma) / (type.peak * 0.1)));
            long want = Math.round(pmax * HEAT_FE * heatingUnits() * reg);
            long got = want <= 0 ? 0 : energy.extract(want, false);
            heatingFe = got;
            heatingFrac = want <= 0 ? 0 : reg * got / (double) want;
        }
        double heatRate = HEAT_RATE * LOSS * type.peak;
        double alpha = ALPHA * LOSS * type.peak;
        double dT = heatingFrac * heatingUnits() * heatRate + alpha * power / pmax - LOSS * plasma * (confined ? 1 : 10)
                    - (stopping ? 0.02 * plasma : 0);
        plasma = Math.max(0, plasma + dT);
        if (plasma < 0.5 && !heating) {
            plasma = 0;
            stopping = false;
        }
        // --- combustible brule, cendres d'helium, tritium des couvertures
        burn(power);
        // --- energie : chaleur (tokamak) ou electricite directe + chaleur (stellarator) ; le chauffage finit aussi en chaleur
        electric = 0;
        double heat = power + heatingFe;
        if (type == FusionType.STELLARATOR && power > 0) {
            long e = energy.insert(Math.round(power * electricShare()), false);
            electric = e;
            heat -= e;
        }
        host.body.addHeat(heat);
        // --- ignition et progres
        boolean ignited = ignited();
        if (ignited && !ignitedOnce) {
            ignitedOnce = true;
            IndustryEvents.award(server, host.getBlockPos(), type.ignitionEvent);
            server.playSound(null, host.getBlockPos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 0.6F);
        }
        if (plasma < 1) {
            ignitedOnce = false;
        }
        if (type.tritium() && power > 0.2 * pmax && bredRate >= burnRate && burnRate > 0) {
            if (++sufficientTicks >= 200 && !selfSufficient) {
                selfSufficient = true;
                IndustryEvents.award(server, host.getBlockPos(), "tritium_self_sufficient");
            }
        } else {
            sufficientTicks = 0;
        }
        // --- disruptions
        if (plasma > disruptMin()) {
            if (field < 0.5) {
                if (type == FusionType.TOKAMAK || heliumShort) {
                    disrupt(server, true);
                } else {
                    //Stellarator sans energie de confinement : le plasma s'eteint sans casse
                    quench();
                }
            } else if (type == FusionType.TOKAMAK && !stopping && plasma > type.ignition() && density < 0.15) {
                //Perte de densite en pleine combustion (plus de combustible)
                disrupt(server, true);
            }
        }
        double nominal = Tiers.nominal(host.tier());
        if (plasma > disruptMin() && host.body.getTemperature() > nominal * 1.25) {
            if (++overheatTicks >= 40) {
                if (type == FusionType.TOKAMAK) {
                    disrupt(server, true);
                } else {
                    quench();
                }
            }
        } else {
            overheatTicks = Math.max(0, overheatTicks - 1);
        }
        paintGlow();
        sync();
        return power > 0;
    }

    /** 1.0 (lot 3 bis) : lueur des bobines, de la chambre et des convertisseurs quand le plasma tient (peinte au changement). */
    private void paintGlow() {
        Level level = host.getLevel();
        if (level == null) {
            return;
        }
        boolean lit = plasma > 1 || heating;
        int g = lit ? 1 : 0;
        if (g == paintedGlow) {
            return;
        }
        paintedGlow = g;
        for (BlockPos p : glowPositions) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof ReactorBlock && state.getValue(ReactorBlock.ACTIVE) != lit) {
                level.setBlock(p, state.setValue(ReactorBlock.ACTIVE, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Reactivite relative s(T) (0..1), pic a la temperature du type. */
    public double reactivity(double t) {
        if (t <= 1) {
            return 0;
        }
        double l = Math.log(t / type.peak);
        return Math.exp(-l * l / 0.5);
    }

    /** Carcasses trop chaudes : la combustion est bridee au-dela de la nominale (nulle a 1,25 x). */
    private double thermalFactor() {
        double nominal = Tiers.nominal(host.tier());
        double t = host.body.getTemperature();
        return t <= nominal ? 1 : Math.max(0, 1 - (t / nominal - 1) * 4);
    }

    private void burn(double p) {
        List<BasicGasTank> in = host.gasInTanks(), out = host.gasOutTanks();
        double burnt = 0;
        if (p > 0 && in.size() >= 2) {
            fuelDebt += p / 1_000_000.0 * type.fuelPerMj;
            long n = (long) fuelDebt;
            if (n > 0) {
                long avail = Math.min(in.get(0).getStack().getAmount(), in.get(1).getStack().getAmount());
                long take = Math.min(n, avail);
                if (take > 0) {
                    in.get(0).extract(take, Action.EXECUTE, Automation.INTERNAL);
                    in.get(1).extract(take, Action.EXECUTE, Automation.INTERNAL);
                }
                fuelDebt -= n;
                burnt = take;
                ashCredit += take;
            }
        }
        //Cendres : un noyau d'helium 4 par reaction (D-T et D-He3)
        long ash = (long) ashCredit;
        if (ash > 0 && !out.isEmpty()) {
            Gas helium = helium();
            if (helium != null) {
                out.get(0).insert(GasStack.of(helium, ash), Action.EXECUTE, Automation.INTERNAL);
            }
            ashCredit -= ash;
        }
        double bred = 0;
        if (type.tritium()) {
            double ratio = breedingRatio();
            if (burnt > 0 && ratio > 0) {
                double want = burnt * ratio;
                if (lithium < want) {
                    //Une bille de lithium par 1 000 mB de tritium
                    for (ItemSlot slot : host.itemInputs()) {
                        if (lithium >= want) {
                            break;
                        }
                        if (accepts(slot.getStack())) {
                            ItemStack got = slot.extract(1, Action.EXECUTE, Automation.INTERNAL);
                            if (!got.isEmpty()) {
                                lithium += TRITIUM_PER_PEBBLE;
                            }
                        }
                    }
                }
                bred = Math.min(want, lithium);
                lithium -= bred;
                bredCredit += bred;
            }
            long t = (long) bredCredit;
            if (t > 0) {
                bredCredit -= t;
                //Boucle fermee : le tritium produit retourne d'abord au reservoir d'entree, le surplus sort
                GasStack stack = GasStack.of(PiGasTypes.TRITIUM.get(), t);
                GasStack rest = in.size() >= 2 ? in.get(1).insert(stack, Action.EXECUTE, Automation.INTERNAL) : stack;
                if (!rest.isEmpty() && out.size() >= 2) {
                    out.get(1).insert(rest, Action.EXECUTE, Automation.INTERNAL);
                }
            }
        }
        burnRate = burnRate * 0.98 + burnt * 0.02;
        bredRate = bredRate * 0.98 + bred * 0.02;
    }

    /** Extinction sans casse (stellarator) : le plasma se perd, rien n'explose. */
    private void quench() {
        plasma = 0;
        power = 0;
        density = 0;
        heating = false;
        stopping = false;
        overheatTicks = 0;
        host.syncReactor();
    }

    /** Disruption : plasma eteint d'un coup, bobines dechargees, explosion d'effet, carcasses brulees, fuite de tritium. */
    private void disrupt(ServerLevel level, boolean formed) {
        plasma = 0;
        power = 0;
        density = 0;
        field = 0;
        heating = false;
        stopping = false;
        overheatTicks = 0;
        flash = FLASH;
        BlockPos min = cachedMin != null ? cachedMin : host.getBlockPos(), max = cachedMax != null ? cachedMax : host.getBlockPos();
        Meltdown.disruption(level, min, max, host.getBlockPos(), true);
        if (formed) {
            host.scorchCasings(2);
        }
        sentSignature = Long.MIN_VALUE;
        host.syncReactor();
    }

    // ------------------------------------------------------------------
    // Vue du client
    // ------------------------------------------------------------------

    /** Temperature du plasma relative a l'ignition (vue : 1 = ignition). */
    public float heatFraction() {
        return (float) (plasma / type.ignition());
    }

    private void sync() {
        sinceSync++;
        long sig = Math.round(powerFraction() * 50) | Math.round(Math.min(10, heatFraction()) * 25) << 8 | (heating ? 1L : 0) << 16
                   | (flash > 0 ? 1L : 0) << 17 | Math.round(field * 20) << 18 | Math.round(density * 20) << 24;
        if (sig != sentSignature && sinceSync >= 5) {
            sentSignature = sig;
            sinceSync = 0;
            host.syncReactor();
        }
    }

    /**
     * Vue du reacteur (meme format que {@link FissionCore#writeView()}, lue par {@link FissionCore.ClientView#read}) : mark 4 ou 5,
     * power = P/Pmax, heat = T/T ignition, rods = chauffage (0..1), scram = flash de disruption, fuel = densite en %, cap = 100,
     * coolant = champ des bobines.
     */
    public CompoundTag writeView() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("mark", (byte) type.mark);
        tag.putFloat("power", (float) powerFraction());
        tag.putFloat("heat", heatFraction());
        tag.putFloat("rods", heating ? (float) Math.max(0.05, heatingFrac) : 0);
        tag.putBoolean("scram", flash > 0);
        tag.putInt("fuel", (int) Math.round(density * 100));
        tag.putInt("cap", 100);
        tag.putFloat("coolant", (float) field);
        BlockPos min = host.structure() != null ? host.structure().min() : cachedMin;
        BlockPos max = host.structure() != null ? host.structure().max() : cachedMax;
        if (min != null && max != null) {
            tag.putLong("min", min.asLong());
            tag.putLong("max", max.asLong());
        }
        return tag;
    }

    /** Vue cote serveur (memes valeurs que la vue synchronisee). */
    public ReactorView view() {
        return FissionCore.ClientView.read(writeView(), host.getBlockPos());
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("plasma", plasma);
        tag.putDouble("density", density);
        tag.putDouble("field", field);
        tag.putBoolean("heating", heating);
        tag.putBoolean("stopping", stopping);
        tag.putBoolean("ignited", ignitedOnce);
        tag.putBoolean("sufficient", selfSufficient);
        tag.putDouble("fuelDebt", fuelDebt);
        tag.putDouble("ash", ashCredit);
        tag.putDouble("bred", bredCredit);
        tag.putDouble("lithium", lithium);
        tag.putDouble("capacity", cachedCapacity);
        tag.putInt("tier", cachedTier);
        if (cachedMin != null && cachedMax != null) {
            tag.putLong("min", cachedMin.asLong());
            tag.putLong("max", cachedMax.asLong());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        plasma = Math.max(0, tag.getDouble("plasma"));
        density = Math.max(0, Math.min(1, tag.getDouble("density")));
        field = Math.max(0, Math.min(1, tag.getDouble("field")));
        heating = tag.getBoolean("heating");
        stopping = tag.getBoolean("stopping");
        ignitedOnce = tag.getBoolean("ignited");
        selfSufficient = tag.getBoolean("sufficient");
        fuelDebt = tag.getDouble("fuelDebt");
        ashCredit = tag.getDouble("ash");
        bredCredit = tag.getDouble("bred");
        lithium = tag.getDouble("lithium");
        cachedCapacity = tag.contains("capacity") ? tag.getDouble("capacity") : 10_000;
        cachedTier = tag.getInt("tier");
        if (tag.contains("min")) {
            cachedMin = BlockPos.of(tag.getLong("min"));
            cachedMax = BlockPos.of(tag.getLong("max"));
        }
    }

    // ------------------------------------------------------------------
    // API Multibloc (lot 6) : coeur declare par la fiche (MultiblockLogic) ; memes cles, memes indices d'avant
    // ------------------------------------------------------------------

    @Override
    public String saveKey() {
        return "fusion";
    }

    @Override
    public boolean syncOnFormChange() {
        return true;
    }

    @Override
    public int work(LongPredicate use, boolean enabled) {
        //Plasma, bobines, disruptions ; jamais d'arret OVERHEAT (le plasma est bride puis disrupte ou s'eteint)
        return tick() ? WORKING : IDLE;
    }

    @Override
    public void unformedTick() {
        //Tore defait : un plasma chaud se perd d'un coup (disruption)
        unformed();
    }

    @Override
    public boolean ownsHeat() {
        return true;
    }

    @Override
    public double heatCapacity(boolean formed) {
        //Defait : le coeur chauffe toujours la meme masse
        return formed ? -1 : cachedCapacity();
    }

    @Override
    public long reserve() {
        //Chauffage, bobines, electricite
        return 64_000_000L;
    }

    @Override
    public int comparator() {
        return (int) Math.round(powerFraction() * 15);
    }

    @Override
    public boolean ownsItems() {
        return true;
    }

    @Override
    public boolean acceptsItem(ItemStack stack) {
        //Billes de lithium des couvertures (tokamak)
        return accepts(stack);
    }

    @Override
    public int itemKinds() {
        return 2;
    }

    @Override
    public boolean ownsFluids() {
        return true;
    }

    @Override
    public boolean ownsGases() {
        return true;
    }

    @Override
    public boolean acceptsGas(int tank, GasStack stack, @Nullable Level level) {
        //Un gaz par reservoir (deuterium ; tritium ou helium 3)
        return acceptsGas(tank, stack.getGas());
    }

    /**
     * Valeurs d'interface (indices d'avant 42..57) : 42 puissance de fusion (J/t), 43 puissance max, 44 plasma (MK x 10), 45 drapeaux (1
     * chauffage, 2 disruption, 4 ignition, 8 arret commande, 16 helium manquant, 32 energie manquante), 46 Q x 100 (-1 = infini), 47 champ x
     * 1000, 48 chauffage (FE/t), 49 mark, 50 bobines | injecteurs << 8 | modules << 16, 51 electricite (FE/t), 52 part electrique x 1000, 53
     * surgeneration x 1000, 54 densite x 1000, 55 ignition (MK), 56 pic (MK), 57 risque de disruption x 1000 ; nommees par
     * {@code MultiblockValues}.
     */
    @Override
    public long value(int index) {
        return switch (index) {
            case 42 -> Math.round(power());
            case 43 -> Math.round(maxPower());
            case 44 -> Math.round(plasmaTemperature() * 10);
            case 45 -> (heating() ? 1 : 0) | (disrupted() ? 2 : 0) | (ignited() ? 4 : 0) | (stopping() ? 8 : 0) | (heliumShort() ? 16 : 0)
                       | (energyShort() ? 32 : 0);
            case 46 -> Double.isInfinite(q()) ? -1 : Math.round(q() * 100);
            case 47 -> Math.round(field() * 1000);
            case 48 -> Math.round(heatingPower());
            case 49 -> type.mark;
            case 50 -> coils() | (long) injectors() << 8 | (long) modules() << 16;
            case 51 -> Math.round(electric());
            case 52 -> Math.round(electricShare() * 1000);
            case 53 -> Math.round(breedingRatio() * 1000);
            case 54 -> Math.round(density() * 1000);
            case 55 -> Math.round(type.ignition());
            case 56 -> Math.round(type.peak);
            case 57 -> Math.round(disruptionRisk() * 1000);
            default -> 0;
        };
    }

    /** Commandes de l'interface : chauffer (bascule) ou couper (arret commande) ({@code ControllerTile.FUSION_*}). */
    @Override
    public boolean action(ServerPlayer player, int action) {
        switch (action) {
            case ControllerTile.FUSION_HEAT -> setHeating(!heating());
            case ControllerTile.FUSION_STOP -> shutdown();
            default -> {
            }
        }
        return true;
    }

    @Override
    public ReactorView reactorView() {
        return view();
    }

    @Override
    public void writeClient(CompoundTag tag, boolean formed) {
        //0.17.2 : meme vue (et meme lecture client) que le reacteur a fission
        if (formed) {
            tag.put("reactorView", writeView());
        } else {
            tag.putBoolean("reactorOff", true);
        }
    }
}
