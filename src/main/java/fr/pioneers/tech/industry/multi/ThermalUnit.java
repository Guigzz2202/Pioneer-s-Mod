package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.heat.IHeatHandler;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.tech.bronze.steam.Steam;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.heat.HeatExchange;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 5) : UNITE DE CHALEUR d'un multibloc industriel (sortie de {@link ControllerTile}), sur la base des unites
 * ({@link MachineUnit}) : pertes vers l'air du lieu (par l'enveloppe, les events, les ventilateurs), caloporteur (eau -> vapeur,
 * caloporteur -> caloporteur chaud, azote liquide -> azote), ports de chaleur (sortie vers un voisin plus froid, entree d'un voisin plus
 * chaud = credit de chaleur), climat des fermes, surchauffe (au-dela de +35 % pendant 5 s une carcasse brule et la structure se defait).
 * Le corps thermique ({@link ControllerTile.Body}) reste porte par le controleur (les coeurs des reacteurs, de la fusion et du calcul le
 * lisent) ; cles de sauvegarde d'avant le lot 5 ({@code overheat}, {@code heatCredit}).
 */
final class ThermalUnit extends MachineUnit {

    final ControllerTile c;
    /**
     * Chaleur entree par les ports de chaleur en entree et pas encore consommee (J) ; chaque joule consomme par une operation d'un multibloc
     * chauffant remplace 1 FE (au plus 75 % de l'energie), des que le corps atteint sa temperature de travail. Debits lisses pour l'interface.
     */
    double heatCredit, heatInRate, savedRate, tickHeatIn;
    /** Temperature ambiante du lieu (fermes : selon le biome), recalculee de temps en temps. */
    double ambient = HeatExchange.AMBIENT;
    int ambientCheck, overheatTicks;

    /**
     * Vue des ports de chaleur pour les voisins : conductance nulle (les cables n'y echangent rien) ; c'est le controleur qui fait passer la
     * chaleur, dans le sens du port (voir {@link #heatPorts}).
     */
    final IHeatHandler valve = new IHeatHandler() {
        @Override
        public double getTemperature() {
            return c.body.getTemperature();
        }

        @Override
        public double getHeatCapacity() {
            return c.body.getHeatCapacity();
        }

        @Override
        public double getConductance() {
            return 0;
        }

        @Override
        public void addHeat(double joules) {
            c.body.addHeat(joules);
        }
    };

    ThermalUnit(ControllerTile c) {
        super(c.access());
        this.c = c;
    }

    @Override
    public void tick() {
        Structure.Result s = c.lifecycle.structure();
        if (--ambientCheck <= 0) {
            ambientCheck = 200;
            ambient = localAmbient();
        }
        //1.0.4-beta (lot 15) : plus d'events ni de ventilateurs : les pertes a l'air ne dependent que de l'enveloppe
        towardAmbient(s.shell() * ControllerTile.AMBIENT_PER_SHELL);
        if (c.machine.heat < 0) {
            //Tour de refroidissement : chaque garnissage evacue la chaleur recue par les ports
            //1.0.4-beta (lot 23) : plus de bloc de garnissage : la rangee de garnissage (volume interieur) compte
            HeatExchange.ambient(c.body, Structure.units(c.machine, s) * -c.machine.heat * (1 << c.tier()) * 0.05);
        }
        double nominal = c.nominal();
        double setpoint = HeatExchange.AMBIENT + 0.5 * (nominal - HeatExchange.AMBIENT);
        if (c.body.temperature > setpoint && !c.coolant.isEmpty()) {
            double want = (c.body.temperature - setpoint) * c.body.getHeatCapacity() * 0.05;
            //0.17.1 : reacteur rapide : pompes, echangeurs et sodium limitent la chaleur emportee ; sa vapeur est plus chaude
            double steamFactor = 1;
            fr.pioneers.tech.nuclear.FissionCore core = c.fission();
            if (core != null) {
                want *= core.coolingFactor();
                steamFactor = core.steamFactor();
            }
            FluidStack fluid = c.coolant.getFluid();
            double perMb;
            FluidStack product = FluidStack.EMPTY;
            long steam = 0;
            if (fluid.getFluid().isSame(Fluids.WATER)) {
                //1.0 (0.19.12) : l'eau bout en vapeur, un GAZ (reservoir de vapeur, ports de gaz)
                perMb = ControllerTile.WATER_J;
                steam = Math.round(Steam.STEAM_PER_WATER * steamFactor);
            } else if (fluid.getFluid().isSame(PiIndustry.fluid("coolant"))) {
                perMb = ControllerTile.COOLANT_J;
                product = new FluidStack(PiIndustry.fluid("hot_coolant"), 1);
            } else {
                perMb = ControllerTile.NITROGEN_J;
            }
            int mb = (int) Math.min(fluid.getAmount(), Math.ceil(want / perMb));
            if (!product.isEmpty()) {
                //Le produit doit trouver sa place (sinon il deborde : on le perd)
                if (!c.coolantOut.isEmpty() && !c.coolantOut.getFluid().isFluidEqual(product)) {
                    mb = 0;
                } else {
                    int room = c.coolantOut.getSpace() / product.getAmount();
                    mb = Math.min(mb, Math.max(0, room));
                }
            } else if (steam > 0) {
                GasStack held = c.steamOut.getStack();
                if (!held.isEmpty() && !held.isSameGas(PiIndustry.steam(1))) {
                    mb = 0;
                } else {
                    mb = (int) Math.min(mb, Math.max(0, (c.steamOut.getCapacity() - held.getAmount()) / steam));
                }
            }
            if (mb > 0) {
                c.coolant.drainInternal(mb, FluidAction.EXECUTE);
                c.body.addHeat(-mb * perMb);
                if (!product.isEmpty()) {
                    c.coolantOut.fillInternal(new FluidStack(product, product.getAmount() * mb), FluidAction.EXECUTE);
                } else if (steam > 0) {
                    c.steamOut.insert(PiIndustry.steam(steam * mb), Action.EXECUTE, Automation.INTERNAL);
                }
                //0.20.46 (audit) : reservoirs seulement (deja signales par leurs ecouteurs) : envoi regle par tankSync
                c.tankSync.changed();
            }
        }
        if (c.body.temperature > nominal * 1.35 && (c.unit == null || !c.unit.ownsHeat())) {
            if (++overheatTicks >= 100) {
                scorch();
            }
        } else {
            overheatTicks = Math.max(0, overheatTicks - 2);
        }
    }
    /** Fermes : vrai si le multibloc vit au rythme du climat (style serre). */
    boolean isFarm() {
        return c.machine.style == IndustryStyle.LIVING;
    }
    /**
     * Temperature ambiante : 300 K, sauf pour les fermes, ou elle suit le biome du controleur (300 K a la temperature de la plaine,
     * 25 K par unite de temperature vanilla, bornee entre 250 et 330 K : desert ~330, taiga ~286, neige ~280, banquise ~268).
     */
    double localAmbient() {
        if (!isFarm() || level() == null) {
            return HeatExchange.AMBIENT;
        }
        float t = level().getBiome(pos()).value().getBaseTemperature();
        return Math.max(250, Math.min(330, HeatExchange.AMBIENT + (t - 0.8) * 25));
    }
    /** Pertes vers l'air ambiant du lieu (meme loi que {@link HeatExchange#ambient}, mais vers la temperature du lieu). */
    void towardAmbient(double loss) {
        double dT = c.body.temperature - ambient;
        if (Math.abs(dT) < 1e-6 || loss <= 0) {
            return;
        }
        double flow = Math.signum(dT) * Math.min(Math.abs(dT) * loss, Math.abs(dT) * c.body.getHeatCapacity() * 0.5);
        c.body.addHeat(-flow);
    }
    /** Climat des fermes : 1 dans la plage de confort, 0,5 sinon (jamais d'arret). */
    public double climateFactor() {
        if (!isFarm()) {
            return 1;
        }
        return c.body.temperature < ControllerTile.FARM_MIN || c.body.temperature > ControllerTile.FARM_MAX ? 0.5 : 1;
    }
    /** Temperature de travail au-dela de laquelle la chaleur recue remplace l'electricite : celle de la recette, sinon mi-chemin du nominal. */
    double workTemperature(@Nullable IndustrialRecipe r) {
        if (r != null && r.temperature() > 0) {
            return r.temperature() + 273;
        }
        return HeatExchange.AMBIENT + 0.5 * (c.nominal() - HeatExchange.AMBIENT);
    }
    /**
     * Ports de chaleur : en sortie, la chaleur du multibloc part vers un voisin plus froid ; en entree, celle d'un voisin plus chaud entre et
     * s'ajoute au credit de chaleur. Deux ports colles : c'est le port en sortie qui mene l'echange.
     */
    void heatPorts(PortTile port, Structure.Result s) {
        port.migrateHeat(c.machine.heat >= 0);
        boolean output = port.isOutput();
        for (Direction d : Directions.ALL) {
            BlockPos n = port.getBlockPos().relative(d);
            //1.9 (MAJ RP, bloc 4) : la chaleur ne passe pas une frontiere de claim refusee
            if (s.isPart(n) || !level().isLoaded(n) || !ClanApi.canTransfer(level(), port.getBlockPos(), n)) {
                continue;
            }
            BlockEntity be = level().getBlockEntity(n);
            if (be == null) {
                continue;
            }
            if (be instanceof PortTile other) {
                if (other.type != PortBlock.Type.HEAT || !output) {
                    continue;
                }
                ControllerTile them = other.controller();
                if (them == null || them == c || c.body.temperature <= them.body.temperature) {
                    continue;
                }
                double flow = HeatExchange.exchange(c.body, them.body);
                if (!other.isOutput() && flow > 0) {
                    them.thermal.heatCredit += flow;
                    them.thermal.tickHeatIn += flow;
                }
                continue;
            }
            IHeatHandler h = be.getCapability(PioneersAPI.HEAT_HANDLER, d.getOpposite()).orElse(null);
            if (h == null || h.getConductance() <= 0) {
                continue;
            }
            if (output && c.body.temperature > h.getTemperature()) {
                HeatExchange.exchange(c.body, h);
            } else if (!output && h.getTemperature() > c.body.temperature) {
                double flow = HeatExchange.exchange(h, c.body);
                if (flow > 0) {
                    heatCredit += flow;
                    tickHeatIn += flow;
                }
            }
        }
    }
    /** Surchauffe prolongee : une carcasse brule, la structure se defait. */
    /** 0.17.2 : brule {@code n} carcasses de la structure (disruption d'un plasma de fusion). */
    public void scorchCasings(int n) {
        for (int i = 0; i < n; i++) {
            scorch();
        }
    }
    void scorch() {
        overheatTicks = 0;
        Structure.Result s = c.lifecycle.structure();
        if (s == null) {
            return;
        }
        List<BlockPos> casings = new ArrayList<>();
        //Forme libre : les pieces connues ; sinon les faces du pave
        Iterable<BlockPos> candidates = s.members() != null ? s.members() : BlockPos.betweenClosed(s.min(), s.max());
        for (BlockPos p : candidates) {
            boolean edge = s.members() != null || p.getX() == s.min().getX() || p.getX() == s.max().getX() || p.getY() == s.min().getY()
                           || p.getY() == s.max().getY() || p.getZ() == s.min().getZ() || p.getZ() == s.max().getZ();
            if (edge && level().isLoaded(p) && level().getBlockState(p).getBlock() instanceof CasingBlock casing && !casing.scorched()) {
                casings.add(p.immutable());
            }
        }
        if (casings.isEmpty()) {
            return;
        }
        BlockPos burnt = casings.get(level().random.nextInt(casings.size()));
        level().setBlock(burnt, PiIndustry.SCORCHED_CASING.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, burnt.getX() + 0.5, burnt.getY() + 0.5, burnt.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.05);
            server.sendParticles(ParticleTypes.FLAME, burnt.getX() + 0.5, burnt.getY() + 0.5, burnt.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.05);
        }
        level().playSound(null, burnt, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.2F, 0.7F);
        IndustryEvents.award(level(), pos(), "industry_scorched");
        c.requestCheck();
    }
}
