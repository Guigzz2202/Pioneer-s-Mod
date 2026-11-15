package fr.pioneers.tech.nuclear;

import fr.pioneers.api.multiblock.MultiblockLogic;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.IndustryShape;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.core.registries.PiIndustry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Coeur d'un reacteur (0.17.0, generalise en 0.17.1 par {@link ReactorType}), tenu par le controleur du multibloc.
 * <ul>
 *     <li>Combustible charge par les cases d'entree (au plus un par module interieur), chacun avec son taux de combustion ;</li>
 *     <li>puissance visee P* = N . Pa . max(0, 1 - r . w) . f(T) . k, avec w = 1 si les barres de commande sont la (1.0 : le gabarit les
 *     impose ; MKIII : w = 1), f(T) = max(0,3 ; 1 - 0,0005 (T - 600)), k : coefficient de vide du sodium (MKII) ou champ de confinement (MKIII) ;
 *     P suit P* de 2 % par tick ; chaleur residuelle 6 % de la puissance, constante 1 200 ticks ;</li>
 *     <li>arret d'urgence automatique a 0,9 Tn (Tn : temperature nominale des carcasses), alarme au-dela de 1,05 Tn, fusion du coeur au-dela
 *     de 1,25 Tn pendant 200 ticks ({@link Meltdown}) ;</li>
 *     <li>cibles d'irradiation (MKI, MKII : lithium 6, neptunium 237), 2.10^8 J chacune ; couvertures surgeneratrices (MKII), rapport 1,2 ;</li>
 *     <li>MKII : sodium primaire (1 000 mB par sous-assemblage, jamais consomme) ; manque de sodium = +1 % de reactivite par % manquant
 *     (coefficient de vide positif) et moins d'extraction ; pompes et echangeurs intermediaires limitent l'extraction de chaleur ;
 *     vapeur x1,25 par joule ;</li>
 *     <li>MKIII : 80 % de la puissance en electricite (x collecteurs), le reste en chaleur ; champ de confinement allume par 2 000 000 FE en
 *     100 ticks puis entretenu par 10 % de la production ; sans energie il tombe en 40 ticks, et s'il tombe a plus de 50 % de puissance :
 *     dispersion du plasma.</li>
 * </ul>
 * 0.20.24 : <b>pilote automatique</b> (en marche par defaut) : il retire les barres doucement et tient le coeur a sa temperature de
 * fonctionnement ({@link #operatingTemperature}) ; la puissance se cale d'elle-meme sur ce que le refroidissement emporte (eau, sodium,
 * vapeur evacuee). Il rearme seul un arret d'urgence du a la temperature une fois le coeur refroidi. Toucher aux barres (interface,
 * ordinateur, IA) le coupe. Arret d'urgence : chute prompte de la puissance (15 %/t). Diagnostic {@link #blocker()} : ce qui empeche la
 * puissance de monter.
 */
public final class FissionCore implements ReactorControl, MultiblockLogic {

    public static final double T0 = 600, FEEDBACK = 0.0005, FEEDBACK_MIN = 0.3;
    public static final double DECAY_SHARE = 0.06, DECAY_TIME = 1_200;
    public static final double SCRAM_AT = 0.9, ALARM_AT = 1.05, MELT_AT = 1.25;
    public static final int MELT_TICKS = 200;
    /** Chaleur du coeur pour irradier une cible (J) ; rapport de surgeneration ; sodium par sous-assemblage (mB). */
    public static final double TARGET_ENERGY = 2.0e8, BREEDING_RATIO = 1.2;
    public static final int SODIUM_PER_CHANNEL = 1_000;
    /** MKIII : energie d'allumage du champ (FE/t pendant 100 ticks), part electrique, entretien du champ, entretien minimal (FE/t). */
    public static final long IGNITION_RATE = 20_000;
    public static final double ELECTRIC_SHARE = 0.8, FIELD_UPKEEP = 0.1, FIELD_IDLE = 1_000;
    /** 0.20.24 : masse thermique du coeur (eau, sodium, cuve) par case interieure, en multiples de celle d'un multibloc ordinaire. */
    public static final double CORE_MASS = 4;
    /** 0.20.24 : chute prompte de la puissance a l'arret d'urgence (part de l'ecart par tick). */
    public static final double PROMPT_DROP = 0.15;
    /** 0.20.24 : pilote : anticipation (ticks), rearmement (K sous la temperature de fonctionnement). */
    public static final double PILOT_LOOKAHEAD = 60, PILOT_REARM = 30;
    /** 0.20.24 : causes d'un arret d'urgence. */
    public static final int CAUSE_NONE = 0, CAUSE_MANUAL = 1, CAUSE_HEAT = 2;
    /** 0.20.24 : diagnostic ({@link #blocker()}). */
    public static final int OK = 0, NO_FUEL = 1, SCRAM = 2, RODS_IN = 3, STARTING = 4, NO_COOLANT = 5, STEAM_FULL = 6, NO_SODIUM = 7,
          NO_FIELD = 8, NO_COILS = 9, MELTED = 10, NO_DRIVES = 11;

    /** Cible ou couverture en cours d'irradiation. */
    private static final class Charge {

        final String item;
        double progress;

        Charge(String item, double progress) {
            this.item = item;
            this.progress = progress;
        }
    }

    private final ControllerTile host;
    public final ReactorType type;
    /** Taux de combustion du combustible charge (0..1 ; 1 = use, en attente de sortie). */
    private final List<Double> fuel = new ArrayList<>();
    private final List<Charge> targets = new ArrayList<>(), blankets = new ArrayList<>();
    private int rodTarget = 100;
    private double rodActual = 100;
    private double power, decay, field, electric;
    private boolean scrammed, autoScram = true, critical, melted, bred;
    private int meltTicks, alarmTicks, fieldTicks;
    /** 0.20.24 : pilote automatique, cause du dernier arret d'urgence, temperature du dernier pas du pilote. */
    private boolean pilot = true;
    private int scramCause = CAUSE_NONE;
    private double pilotLast = Double.NaN;
    /** Structure : modules, pieces signature, positions des mecanismes, capacite thermique (gardee si la structure se defait). */
    private int channels, drives, pumps, exchangers, coils, collectors;
    private final List<BlockPos> drivePositions = new ArrayList<>();
    /** 1.0 (lot 3) : blocs qui s'allument en marche (canaux, pompes, echangeurs, bobines, collecteurs) ; dernier etat peint (-1 : inconnu). */
    private final List<BlockPos> glowPositions = new ArrayList<>();
    private int paintedGlow = -1;
    private double cachedCapacity = 10_000;
    private int cachedTier;
    private BlockPos cachedMin, cachedMax;
    private int paintedRods = -1;
    /** Synchronisation de la vue client : derniere signature envoyee, ticks depuis. */
    private long sentSignature = Long.MIN_VALUE;
    private int sinceSync;
    private final ReactorView view = new ServerView();

    public FissionCore(ControllerTile host, ReactorType type) {
        this.host = host;
        this.type = type;
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    /** Appele par le controleur apres chaque validation reussie. */
    public void onStructure(Structure.Result s, Level level) {
        channels = s.modules();
        drivePositions.clear();
        drives = pumps = exchangers = coils = collectors = 0;
        //1.0.4-beta (lot 23, demande de Guillaume) : barres de commande, pompes primaires et echangeurs intermediaires RETIRES (blocs) :
        //integres a la structure formee, autant que l'ancien gabarit en exigeait (meme mecanique : barres pilotees, refroidissement du MKII)
        if (type.rodDrives()) {
            drives = IndustryShape.builtInRods(host.machine.shape, s.width());
        }
        if (type == ReactorType.MK2) {
            pumps = IndustryShape.SFR_PUMPS;
            exchangers = IndustryShape.SFR_EXCHANGERS;
        }
        glowPositions.clear();
        //1.0 (lot 3) : blocs fonctionnels du reacteur (canaux comptes par la structure : s.modules())
        if (s.members() != null) {
            for (BlockPos p : s.members()) {
                if (!level.isLoaded(p) || !(level.getBlockState(p).getBlock() instanceof ReactorBlock rb)) {
                    continue;
                }
                switch (rb.kind) {
                    case CONFINEMENT_COIL -> coils++;
                    case FRAGMENT_COLLECTOR -> collectors++;
                    default -> {
                    }
                }
                if (rb.kind != ReactorBlock.Kind.DIAMOND_REFLECTOR) {
                    glowPositions.add(p.immutable());
                }
            }
        }
        if (type == ReactorType.MK3) {
            //Gen III : pas de canaux (la sphere est vide, le plasma de fragments y est dessine) : les cartouches se placent dans
            //l'anneau de confinement, une pour deux bobines (avant : 0 place, le reacteur ne pouvait pas etre charge)
            channels = Math.max(4, coils / 2);
        }
        paintedGlow = -1;
        cachedCapacity = Math.max(10_000, s.volume() * ControllerTile.CAPACITY_PER_BLOCK * CORE_MASS);
        cachedTier = Math.max(0, Math.min(3, s.tier()));
        cachedMin = s.min();
        cachedMax = s.max();
        paintedRods = -1;
        sentSignature = Long.MIN_VALUE;
    }

    /** Capacite thermique a garder quand la structure se defait (le coeur continue de chauffer). */
    public double cachedCapacity() {
        return cachedCapacity;
    }

    /** Le coeur doit-il encore tourner sans structure (combustible charge et chaleur notable) ? */
    public boolean live() {
        return !fuel.isEmpty() && !melted && (power > 1 || decay > 1);
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    private int activeFuel() {
        int n = 0;
        for (double b : fuel) {
            if (b < 1) {
                n++;
            }
        }
        return n;
    }

    /** Puissance maximale du coeur charge (J/t). */
    public double maxPower() {
        return Math.max(type.fuelPower, activeFuel() * type.fuelPower);
    }

    private double nominal(boolean formed) {
        return Tiers.nominal(formed ? host.tier() : cachedTier);
    }

    /** MKII : remplissage du sodium primaire (0..1) ; 1 pour les autres. */
    public double sodiumFill() {
        if (type != ReactorType.MK2) {
            return 1;
        }
        List<FluidTank> tanks = host.fluidInTanks();
        if (tanks.isEmpty() || channels <= 0) {
            return 0;
        }
        return Math.min(1, tanks.get(0).getFluidAmount() / (double) (SODIUM_PER_CHANNEL * channels));
    }

    /** Part de la chaleur que le caloporteur peut emporter (MKII : pompes, echangeurs et sodium) ; 1 pour les autres. */
    public double coolingFactor() {
        if (type != ReactorType.MK2) {
            return 1;
        }
        //1.0 (lot 3) : la dalle porte toujours ses 2 pompes et ses 2 echangeurs (gabarit) ; il en manque un si le coeur tourne structure defaite
        double f = Math.min(1, pumps / 2.0);
        if (exchangers < pumps) {
            f *= 0.5;
        }
        return f * sodiumFill();
    }

    /** Vapeur produite par mB d'eau, relative a l'ordinaire (MKII : plus chaud, x1,25). */
    public double steamFactor() {
        return type == ReactorType.MK2 ? 1.25 : 1;
    }

    /** Le reacteur produit-il de l'electricite (ports d'energie en sortie) ? */
    public boolean producesEnergy() {
        return type == ReactorType.MK3;
    }

    /**
     * Un tick du coeur. {@code formed} : la structure est formee (chargement, sortie, peinture des barres) ; sinon le coeur continue de
     * chauffer avec ses derniers parametres. Renvoie vrai si le reacteur produit de la chaleur.
     */
    public boolean tick(boolean formed) {
        Level level = host.getLevel();
        if (level == null || melted) {
            return false;
        }
        double temperature = host.body.getTemperature();
        double tn = nominal(formed);
        if (formed && level.getGameTime() % 10 == 0) {
            exchange();
        }
        //Arret d'urgence : signal redstone sur le controleur, ou automatique a la temperature limite
        boolean redstone = level.hasNeighborSignal(host.getBlockPos());
        if (!scrammed && redstone) {
            scram();
        } else if (!scrammed && autoScram && temperature > tn * SCRAM_AT) {
            scram();
            scramCause = CAUSE_HEAT;
        }
        if (pilot && formed) {
            pilotStep(level, temperature, tn, redstone);
        }
        int target = scrammed ? 100 : rodTarget;
        double speed = scrammed ? 10 : 2;
        rodActual += Math.max(-speed, Math.min(speed, target - rodActual));
        int n = activeFuel();
        double w = 1;
        if (type.rodDrives()) {
            //1.0 (lot 3) : le gabarit impose une barre de commande au-dessus de chaque colonne de canaux (Gen I) ou du coeur (Gen II) :
            //la structure formee les a toutes ; sans barre (coeur qui tourne structure defaite), elles n'agissent plus
            w = n == 0 || drives > 0 ? 1 : 0;
        }
        double feedback = Math.max(FEEDBACK_MIN, 1 - FEEDBACK * (temperature - T0));
        double k = 1;
        if (type == ReactorType.MK2) {
            //Coefficient de vide positif : le sodium qui manque rend le coeur plus reactif
            k = 1 + (1 - sodiumFill());
        } else if (type == ReactorType.MK3) {
            tickField(formed, n);
            k = field;
        }
        double wanted = n * type.fuelPower * Math.max(0, 1 - rodActual / 100.0 * w) * feedback * k;
        //0.20.24 : a l'arret d'urgence, la puissance chute promptement (avant : 2 %/t, toute la chaleur des neutrons retardes ensuite)
        power += (wanted - power) * (scrammed && wanted < power ? PROMPT_DROP : 0.02);
        if (power < 0.5 && wanted <= 0) {
            power = 0;
        }
        decay += (DECAY_SHARE * power - decay) / DECAY_TIME;
        if (decay < 0.5 && power <= 0) {
            decay = 0;
        }
        double heat = power + decay;
        if (type == ReactorType.MK3) {
            heat = convert(heat, formed);
        }
        host.body.addHeat(heat);
        //Combustion : chaque combustible actif brule sa part ; couvertures et cibles s'irradient
        double burnt = 0;
        if (n > 0 && power > 0) {
            double burn = power / (n * type.fuelPower * type.fuelLife);
            for (int i = 0; i < fuel.size(); i++) {
                double b = fuel.get(i);
                if (b < 1) {
                    double next = Math.min(1, b + burn);
                    burnt += next - b;
                    fuel.set(i, next);
                }
            }
        }
        if (!blankets.isEmpty() && burnt > 0) {
            double each = BREEDING_RATIO * burnt / blankets.size();
            for (Charge c : blankets) {
                c.progress = Math.min(1, c.progress + each);
            }
        }
        if (!targets.isEmpty() && power > 0) {
            double each = power / targets.size() / TARGET_ENERGY;
            for (Charge c : targets) {
                c.progress = Math.min(1, c.progress + each);
            }
        }
        if (!critical && power > type.fuelPower * 0.01) {
            critical = true;
            IndustryEvents.award(level, host.getBlockPos(), type.criticalEvent);
        }
        //Alarme et fusion du coeur
        if (temperature > tn * ALARM_AT && ++alarmTicks % 20 == 1) {
            alarm(level);
        } else if (temperature <= tn * ALARM_AT) {
            alarmTicks = 0;
        }
        if (temperature > tn * MELT_AT) {
            if (++meltTicks >= MELT_TICKS && level instanceof ServerLevel server) {
                meltdown(server, formed, false);
                return false;
            }
        } else {
            meltTicks = Math.max(0, meltTicks - 2);
        }
        if (formed) {
            paintRods(level);
            paintGlow(level);
            sync();
        }
        return power > type.fuelPower * 0.01;
    }

    /** 0.20.24 : temperature de fonctionnement visee par le pilote : 60 % du chemin entre le debut du refroidissement et l'arret d'urgence. */
    public double operatingTemperature() {
        double tn = nominal(host.formed());
        double cooling = 300 + 0.5 * (tn - 300);
        return cooling + 0.6 * (tn * SCRAM_AT - cooling);
    }

    /**
     * 0.20.24 : pilote automatique, toutes les 10 ticks : temperature anticipee (pente sur {@link #PILOT_LOOKAHEAD} ticks) au-dessus de la
     * temperature de fonctionnement : barres enfoncees (1 a 10 %) ; en dessous : barres retirees doucement (1 a 2 %). Rearme seul un arret
     * d'urgence du a la chaleur quand le coeur est redescendu.
     */
    private void pilotStep(Level level, double temperature, double tn, boolean redstone) {
        double set = operatingTemperature();
        if (scrammed) {
            if (scramCause == CAUSE_HEAT && !redstone && temperature < set - PILOT_REARM) {
                scrammed = false;
                scramCause = CAUSE_NONE;
                host.setChanged();
            }
            pilotLast = Double.NaN;
            return;
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        double slope = Double.isNaN(pilotLast) ? 0 : (temperature - pilotLast) / 10;
        pilotLast = temperature;
        double err = temperature + slope * PILOT_LOOKAHEAD - set;
        int next = rodTarget;
        if (err > 0) {
            next = rodTarget + (int) Math.max(1, Math.min(10, Math.round(err / 4)));
        } else if (err < -3) {
            next = rodTarget - (int) Math.max(1, Math.min(2, Math.round(-err / 20)));
        }
        next = Math.max(0, Math.min(100, next));
        if (next != rodTarget) {
            rodTarget = next;
            host.setChanged();
        }
    }

    /**
     * 0.20.24 : ce qui empeche la puissance de monter (affiche par l'interface) : {@link #OK} si le reacteur est en puissance ou monte.
     */
    public int blocker() {
        if (melted) {
            return MELTED;
        }
        if (activeFuel() == 0) {
            return NO_FUEL;
        }
        if (scrammed) {
            return SCRAM;
        }
        if (type == ReactorType.MK3) {
            if (coils < 8) {
                return NO_COILS;
            }
            if (field < 1 && host.getEnergy().getStored() < IGNITION_RATE) {
                return NO_FIELD;
            }
        } else if (drives == 0) {
            return NO_DRIVES;
        }
        if (type == ReactorType.MK2 && sodiumFill() < 0.5) {
            return NO_SODIUM;
        }
        double t = host.body.getTemperature();
        if (t > operatingTemperature() - 15) {
            //Au chaud : ce qui limite, c'est l'evacuation de la chaleur
            if (host.coolantTank().isEmpty()) {
                return NO_COOLANT;
            }
            if (host.steamOutTank().getStack().getAmount() >= host.steamOutTank().getCapacity() * 0.98) {
                return STEAM_FULL;
            }
        }
        if (!pilot && rodTarget >= 100) {
            return RODS_IN;
        }
        if (power < type.fuelPower * 0.01 * activeFuel()) {
            return STARTING;
        }
        return OK;
    }

    public boolean pilot() {
        return pilot;
    }

    public void setPilot(boolean on) {
        pilot = on;
        pilotLast = Double.NaN;
        host.setChanged();
    }

    /**
     * MKIII : champ de confinement. Allumage (combustible charge, pas d'arret d'urgence, barres pas a fond) : 20 000 FE/t pris a la reserve
     * jusqu'a 1 ; entretien : 10 % de la production electrique (au moins 1 000 FE/t). Sans energie : -1/40 par tick ; s'il tombe sous 0,25
     * alors que la puissance depasse 50 % : dispersion.
     */
    private void tickField(boolean formed, int n) {
        EnergyBuffer energy = host.getEnergy();
        boolean wanted = formed && n > 0 && coils >= 8;
        boolean paid;
        if (!wanted) {
            paid = false;
        } else if (field < 1) {
            paid = energy.extract(IGNITION_RATE, true) >= IGNITION_RATE;
            if (paid) {
                energy.extract(IGNITION_RATE, false);
                field = Math.min(1, field + 0.01);
            }
        } else {
            long upkeep = Math.round(Math.max(FIELD_IDLE, electric * FIELD_UPKEEP));
            paid = energy.extract(upkeep, true) >= upkeep;
            if (paid) {
                energy.extract(upkeep, false);
            }
        }
        if (!paid && field > 0) {
            double before = power / maxPower();
            field = Math.max(0, field - 1 / 40.0);
            if (field < 0.25 && before > 0.5 && host.getLevel() instanceof ServerLevel server) {
                meltdown(server, formed, true);
            }
        }
        fieldTicks = paid ? fieldTicks + 1 : 0;
    }

    /** MKIII : la part electrique part dans la reserve (au prorata des collecteurs) ; renvoie la chaleur qui reste dans le coeur. */
    private double convert(double heat, boolean formed) {
        if (!formed || heat <= 0) {
            electric = 0;
            return heat;
        }
        double wanted = heat * ELECTRIC_SHARE * Math.min(1, collectors);
        long fe = host.getEnergy().insert(Math.round(wanted), false);
        electric = fe;
        return heat - fe;
    }

    /** Charge le combustible, les cibles et les couvertures ; sort les uses, les cibles et couvertures irradiees. */
    private void exchange() {
        for (int i = 0; i < fuel.size(); i++) {
            if (fuel.get(i) >= 1 && insert(host.itemOutputs(), new ItemStack(PiIndustry.item(type.spent)))) {
                fuel.remove(i--);
                host.setChanged();
                //1.9 (MAJ RP) : un assemblage use = un cycle de combustible (XP automatique du proprietaire)
                if (host.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                    fr.pioneers.core.profile.PioneerApi.gainAuto(sl, host.getBlockPos(), host.owner(), "nuclear.cycle", 1);
                }
            }
        }
        unload(targets, "irradiated_");
        if (unload(blankets, "irradiated_") && !bred && host.getLevel() != null) {
            bred = true;
            IndustryEvents.award(host.getLevel(), host.getBlockPos(), "breeding");
        }
        for (ItemSlot slot : host.itemInputs()) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            if (fuel.size() < channels && stack.is(PiIndustry.item(type.fuel))) {
                slot.extract(1, Action.EXECUTE, Automation.INTERNAL);
                fuel.add(0.0);
                host.setChanged();
            } else if (type.targets() && targets.size() < Math.max(1, channels / 2) && isTarget(stack)) {
                targets.add(new Charge(id(stack), 0));
                slot.extract(1, Action.EXECUTE, Automation.INTERNAL);
                host.setChanged();
            } else if (type.blankets() && blankets.size() < Math.max(1, channels / 2) && isBlanket(stack)) {
                blankets.add(new Charge(id(stack), 0));
                slot.extract(1, Action.EXECUTE, Automation.INTERNAL);
                host.setChanged();
            }
        }
    }

    /** Sort les charges irradiees ; vrai si au moins une est sortie. */
    private boolean unload(List<Charge> list, String prefix) {
        boolean any = false;
        for (int i = 0; i < list.size(); i++) {
            Charge c = list.get(i);
            if (c.progress >= 1 && insert(host.itemOutputs(), new ItemStack(PiIndustry.item(irradiated(c.item))))) {
                list.remove(i--);
                host.setChanged();
                any = true;
            }
        }
        return any;
    }

    /** Objet irradie d'une cible ou d'une couverture. */
    static String irradiated(String item) {
        return switch (item) {
            case "lithium_target" -> "irradiated_lithium_target";
            case "neptunium_target" -> "irradiated_neptunium_target";
            case "depleted_blanket_assembly" -> "irradiated_depleted_blanket";
            default -> "irradiated_thorium_blanket";
        };
    }

    private static String id(ItemStack stack) {
        return ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
    }

    private static boolean isTarget(ItemStack stack) {
        return stack.is(PiIndustry.item("lithium_target")) || stack.is(PiIndustry.item("neptunium_target"));
    }

    private static boolean isBlanket(ItemStack stack) {
        return stack.is(PiIndustry.item("depleted_blanket_assembly")) || stack.is(PiIndustry.item("thorium_blanket_assembly"));
    }

    /** Objet accepte par les cases d'entree de ce reacteur. */
    public boolean accepts(ItemStack stack) {
        return stack.is(PiIndustry.item(type.fuel)) || (type.targets() && isTarget(stack)) || (type.blankets() && isBlanket(stack));
    }

    private static boolean insert(List<ItemSlot> slots, ItemStack stack) {
        for (ItemSlot slot : slots) {
            if (slot.insert(stack, Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
                slot.insert(stack, Action.EXECUTE, Automation.INTERNAL);
                return true;
            }
        }
        return false;
    }

    /** Position des barres sur les mecanismes de grappe (0..4), peinte sans prevenir les voisins. */
    private void paintRods(Level level) {
        int r = (int) Math.round(rodActual / 25.0);
        if (r == paintedRods) {
            return;
        }
        paintedRods = r;
        for (BlockPos p : drivePositions) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            //1.0.4-beta (lot 23) : plus de bloc de barre de commande (positions vides : rien a peindre)
            if (state.getBlock() instanceof ReactorBlock rb && rb.kind.fuel()) {
                continue;
            }
        }
    }

    /** 1.0 (lot 3) : lueur des blocs du reacteur en marche (MKIII : bobines et collecteurs avec le champ), peinte au changement. */
    private void paintGlow(Level level) {
        boolean lit = type == ReactorType.MK3 ? field > 0.5 : power > type.fuelPower * 0.01;
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

    private void alarm(Level level) {
        BlockPos p = host.getBlockPos();
        level.playSound(null, p, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 2.0F, 0.6F);
        level.playSound(null, p, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 2.0F, 0.45F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.02);
        }
    }

    private void meltdown(ServerLevel level, boolean formed, boolean dispersal) {
        melted = true;
        int n = fuel.size();
        fuel.clear();
        targets.clear();
        blankets.clear();
        power = 0;
        decay = 0;
        field = 0;
        BlockPos min = formed && host.structure() != null ? host.structure().min() : cachedMin;
        BlockPos max = formed && host.structure() != null ? host.structure().max() : cachedMax;
        if (min == null || max == null) {
            min = host.getBlockPos();
            max = host.getBlockPos();
        }
        //Le MKIII compte comme plus de combustible (puissance bien plus grande)
        Meltdown.start(level, min, max, Math.max(1, n * type.mark), host.getBlockPos(), dispersal);
    }

    // ------------------------------------------------------------------
    // Vue client
    // ------------------------------------------------------------------

    /** Etat vu par les rendus du coeur (cote serveur ; le client lit sa copie synchronisee). */
    public ReactorView view() {
        return view;
    }

    private float heatFraction() {
        double tn = nominal(host.formed());
        return (float) Math.max(0, (host.body.getTemperature() - 300) / Math.max(1, tn - 300));
    }

    private float coolantFraction() {
        return switch (type) {
            case MK2 -> (float) sodiumFill();
            case MK3 -> (float) field;
            default -> {
                FluidTank t = host.coolantTank();
                yield t.getCapacity() <= 0 ? 0 : t.getFluidAmount() / (float) t.getCapacity();
            }
        };
    }

    /** Synchronise la vue au client : au plus 4 fois par seconde, et seulement si elle a change notablement. */
    private void sync() {
        sinceSync++;
        long sig = Math.round(powerFraction() * 50) | Math.round(heatFraction() * 50) << 8 | Math.round(rodActual) << 16
                   | (scrammed ? 1L : 0) << 24 | (long) fuel.size() << 25 | Math.round(coolantFraction() * 20) << 35;
        if (sig != sentSignature && sinceSync >= 5) {
            sentSignature = sig;
            sinceSync = 0;
            host.syncReactor();
        }
    }

    /** Ecrit la vue pour le client. */
    public CompoundTag writeView() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("mark", (byte) type.mark);
        tag.putFloat("power", (float) powerFraction());
        tag.putFloat("heat", heatFraction());
        tag.putFloat("rods", (float) (rodActual / 100.0));
        tag.putBoolean("scram", scrammed);
        tag.putInt("fuel", fuel.size());
        tag.putInt("cap", channels);
        tag.putFloat("coolant", coolantFraction());
        BlockPos min = host.structure() != null ? host.structure().min() : cachedMin;
        BlockPos max = host.structure() != null ? host.structure().max() : cachedMax;
        if (min != null && max != null) {
            tag.putLong("min", min.asLong());
            tag.putLong("max", max.asLong());
        }
        return tag;
    }

    /** Copie client de la vue (lue par {@code ControllerTile#readClient}). */
    public record ClientView(int mark, float power, float heat, float rods, boolean scrammed, int fuel, int capacity, float coolant, BlockPos min,
                             BlockPos max) implements ReactorView {

        public static ClientView read(CompoundTag tag, BlockPos fallback) {
            return new ClientView(tag.getByte("mark"), tag.getFloat("power"), tag.getFloat("heat"), tag.getFloat("rods"), tag.getBoolean("scram"),
                  tag.getInt("fuel"), tag.getInt("cap"), tag.getFloat("coolant"), tag.contains("min") ? BlockPos.of(tag.getLong("min")) : fallback,
                  tag.contains("max") ? BlockPos.of(tag.getLong("max")) : fallback);
        }
    }

    private final class ServerView implements ReactorView {

        @Override
        public int mark() {
            return type.mark;
        }

        @Override
        public float power() {
            return (float) powerFraction();
        }

        @Override
        public float heat() {
            return heatFraction();
        }

        @Override
        public float rods() {
            return (float) (rodActual / 100.0);
        }

        @Override
        public boolean scrammed() {
            return scrammed;
        }

        @Override
        public int fuel() {
            return fuel.size();
        }

        @Override
        public int capacity() {
            return channels;
        }

        @Override
        public float coolant() {
            return coolantFraction();
        }

        @Override
        public BlockPos min() {
            return host.structure() != null ? host.structure().min() : cachedMin != null ? cachedMin : host.getBlockPos();
        }

        @Override
        public BlockPos max() {
            return host.structure() != null ? host.structure().max() : cachedMax != null ? cachedMax : host.getBlockPos();
        }
    }

    // ------------------------------------------------------------------
    // Commande (ReactorControl)
    // ------------------------------------------------------------------

    @Override
    public double thermalPower() {
        return power + decay;
    }

    @Override
    public double powerFraction() {
        return Math.min(1, (power + decay) / maxPower());
    }

    @Override
    public double temperature() {
        return host.body.getTemperature();
    }

    @Override
    public double scramTemperature() {
        return nominal(host.formed()) * SCRAM_AT;
    }

    /** Temperature de fusion du coeur (K). */
    public double meltTemperature() {
        return nominal(host.formed()) * MELT_AT;
    }

    @Override
    public int rods() {
        return rodTarget;
    }

    /** Insertion reelle des barres (0..100). */
    public double rodsActual() {
        return rodActual;
    }

    @Override
    public void setRods(int percent) {
        rodTarget = Math.max(0, Math.min(100, percent));
        //0.20.24 : barres reglees a la main (interface, ordinateur, IA) : le pilote automatique se coupe
        pilot = false;
        host.setChanged();
    }

    @Override
    public boolean scrammed() {
        return scrammed;
    }

    @Override
    public void scram() {
        if (!scrammed) {
            scrammed = true;
            scramCause = CAUSE_MANUAL;
            host.setChanged();
            Level level = host.getLevel();
            if (level != null) {
                level.playSound(null, host.getBlockPos(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.5F, 0.6F);
            }
        }
    }

    @Override
    public boolean resetScram() {
        if (!scrammed || host.body.getTemperature() > scramTemperature()) {
            return false;
        }
        Level level = host.getLevel();
        if (level != null && level.hasNeighborSignal(host.getBlockPos())) {
            return false;
        }
        scrammed = false;
        scramCause = CAUSE_NONE;
        host.setChanged();
        return true;
    }

    @Override
    public boolean autoScram() {
        return autoScram;
    }

    @Override
    public void setAutoScram(boolean on) {
        autoScram = on;
        host.setChanged();
    }

    @Override
    public int fuelLoaded() {
        return fuel.size();
    }

    @Override
    public int fuelCapacity() {
        return channels;
    }

    @Override
    public double burnup() {
        if (fuel.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double b : fuel) {
            sum += b;
        }
        return sum / fuel.size();
    }

    public int drives() {
        return drives;
    }

    public int pumps() {
        return pumps;
    }

    public int exchangers() {
        return exchangers;
    }

    public int coils() {
        return coils;
    }

    public int collectors() {
        return collectors;
    }

    public int targetCount() {
        return targets.size();
    }

    public int blanketCount() {
        return blankets.size();
    }

    /** Avancement moyen des cibles (ou des couvertures si pas de cible), 0..1. */
    public double chargeProgress() {
        List<Charge> list = !blankets.isEmpty() ? blankets : targets;
        if (list.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Charge c : list) {
            sum += c.progress;
        }
        return sum / list.size();
    }

    /** MKIII : champ de confinement (0..1) ; electricite produite au dernier tick (FE/t). */
    public double field() {
        return field;
    }

    public double electric() {
        return electric;
    }

    public boolean alarming() {
        return alarmTicks > 0;
    }

    /** Avancement vers la fusion du coeur (0..1). */
    public double meltProgress() {
        return meltTicks / (double) MELT_TICKS;
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    private static ListTag saveCharges(List<Charge> list) {
        ListTag out = new ListTag();
        for (Charge c : list) {
            CompoundTag t = new CompoundTag();
            t.putString("item", c.item);
            t.putDouble("p", c.progress);
            out.add(t);
        }
        return out;
    }

    private static void loadCharges(ListTag in, List<Charge> list) {
        list.clear();
        for (Tag t : in) {
            CompoundTag c = (CompoundTag) t;
            list.add(new Charge(c.getString("item"), Math.max(0, Math.min(1, c.getDouble("p")))));
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (double b : fuel) {
            CompoundTag a = new CompoundTag();
            a.putDouble("b", b);
            list.add(a);
        }
        tag.put("fuel", list);
        tag.put("targets", saveCharges(targets));
        tag.put("blankets", saveCharges(blankets));
        tag.putInt("rods", rodTarget);
        tag.putDouble("rodActual", rodActual);
        tag.putDouble("power", power);
        tag.putDouble("decay", decay);
        tag.putDouble("field", field);
        tag.putBoolean("scrammed", scrammed);
        tag.putBoolean("auto", autoScram);
        tag.putBoolean("critical", critical);
        tag.putBoolean("bred", bred);
        tag.putBoolean("melted", melted);
        tag.putBoolean("pilot", pilot);
        tag.putByte("cause", (byte) scramCause);
        tag.putInt("meltTicks", meltTicks);
        tag.putDouble("capacity", cachedCapacity);
        tag.putInt("tier", cachedTier);
        if (cachedMin != null && cachedMax != null) {
            tag.putLong("min", cachedMin.asLong());
            tag.putLong("max", cachedMax.asLong());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        fuel.clear();
        for (Tag t : tag.getList("fuel", Tag.TAG_COMPOUND)) {
            fuel.add(Math.max(0, Math.min(1, ((CompoundTag) t).getDouble("b"))));
        }
        loadCharges(tag.getList("targets", Tag.TAG_COMPOUND), targets);
        loadCharges(tag.getList("blankets", Tag.TAG_COMPOUND), blankets);
        rodTarget = tag.contains("rods") ? tag.getInt("rods") : 100;
        rodActual = tag.contains("rodActual") ? tag.getDouble("rodActual") : 100;
        power = tag.getDouble("power");
        decay = tag.getDouble("decay");
        field = tag.getDouble("field");
        scrammed = tag.getBoolean("scrammed");
        autoScram = !tag.contains("auto") || tag.getBoolean("auto");
        critical = tag.getBoolean("critical");
        bred = tag.getBoolean("bred");
        melted = tag.getBoolean("melted");
        pilot = !tag.contains("pilot") || tag.getBoolean("pilot");
        scramCause = tag.getByte("cause");
        meltTicks = tag.getInt("meltTicks");
        cachedCapacity = tag.contains("capacity") ? tag.getDouble("capacity") : 10_000;
        cachedTier = tag.getInt("tier");
        if (tag.contains("min")) {
            cachedMin = BlockPos.of(tag.getLong("min"));
            cachedMax = BlockPos.of(tag.getLong("max"));
        }
        paintedRods = -1;
    }

    // ------------------------------------------------------------------
    // API Multibloc (lot 6) : coeur declare par la fiche (MultiblockLogic) ; memes cles, memes indices d'avant
    // ------------------------------------------------------------------

    @Override
    public String saveKey() {
        return "reactor";
    }

    @Override
    public boolean syncOnFormChange() {
        return true;
    }

    @Override
    public int work(LongPredicate use, boolean enabled) {
        //Sa propre logique (barres, arret d'urgence, fusion du coeur) ; jamais d'arret OVERHEAT
        return tick(true) ? WORKING : IDLE;
    }

    @Override
    public void unformedTick() {
        //Reacteur defait en marche : la reaction ne s'arrete pas d'elle-meme
        if (live()) {
            tick(false);
        }
    }

    @Override
    public boolean ownsHeat() {
        return true;
    }

    @Override
    public double heatCapacity(boolean formed) {
        //Forme ou defait : la masse du coeur (0.20.24 : eau, sodium et cuve, x CORE_MASS)
        return cachedCapacity;
    }

    @Override
    public long reserve() {
        //Reacteur a fragments : reserve pour l'allumage du champ et la production
        return producesEnergy() ? 64_000_000L : 0;
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
        //Son combustible, ses cibles et ses couvertures
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
    public boolean acceptsFluid(FluidStack stack) {
        //Reacteur a neutrons rapides : le sodium primaire (l'eau va au reservoir de caloporteur)
        return type == ReactorType.MK2 && stack.getFluid().isSame(PiIndustry.fluid("liquid_sodium"));
    }

    /** Valeurs d'interface (indices d'avant : 20..41, 58 diagnostic | pilote << 8, 59 consigne), nommees par {@code MultiblockValues}. */
    @Override
    public long value(int index) {
        return switch (index) {
            case 20 -> Math.round(thermalPower());
            case 21 -> rods();
            case 22 -> Math.round(rodsActual() * 10);
            case 23 -> (scrammed() ? 1 : 0) | (autoScram() ? 2 : 0) | (alarming() ? 4 : 0);
            case 24 -> fuelLoaded();
            case 25 -> fuelCapacity();
            case 26 -> Math.round(burnup() * 1000);
            case 27 -> Math.round(scramTemperature());
            case 28 -> Math.round(meltTemperature());
            case 29 -> Math.round(meltProgress() * 1000);
            case 30 -> Math.round(maxPower());
            case 31 -> drives();
            //0.17.1 : MKII et MKIII
            case 32 -> type.mark;
            case 33 -> Math.round(sodiumFill() * 1000);
            case 34 -> pumps() | (long) exchangers() << 8;
            case 35 -> coils() | (long) collectors() << 8;
            case 36 -> Math.round(field() * 1000);
            case 37 -> Math.round(electric());
            case 38 -> targetCount() | (long) blanketCount() << 8;
            case 39 -> Math.round(chargeProgress() * 1000);
            case 40 -> Math.round(coolingFactor() * 1000);
            case 41 -> Math.round(thermalPower() - electric());
            //0.20.24 : diagnostic (blocker) | pilote automatique << 8 ; consigne du pilote
            case 58 -> blocker() | (pilot() ? 1L << 8 : 0);
            case 59 -> Math.round(operatingTemperature());
            default -> 0;
        };
    }

    /** Commandes de l'interface : pilote, barres, arret d'urgence, rearmement, arret automatique ({@code ControllerTile.REACTOR_*}). */
    @Override
    public boolean action(ServerPlayer player, int action) {
        switch (action) {
            case ControllerTile.REACTOR_PILOT -> setPilot(!pilot());
            case ControllerTile.REACTOR_RODS_DOWN10 -> setRods(rods() - 10);
            case ControllerTile.REACTOR_RODS_DOWN1 -> setRods(rods() - 1);
            case ControllerTile.REACTOR_RODS_UP1 -> setRods(rods() + 1);
            case ControllerTile.REACTOR_RODS_UP10 -> setRods(rods() + 10);
            case ControllerTile.REACTOR_SCRAM -> scram();
            case ControllerTile.REACTOR_RESET -> {
                if (!resetScram()) {
                    player.displayClientMessage(Component.translatable("nuclear.pioneers.reset_refused"), true);
                }
            }
            case ControllerTile.REACTOR_AUTO -> setAutoScram(!autoScram());
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
        //0.17.1 : vue du reacteur pour les rendus du coeur (absente si la structure n'est pas formee)
        if (formed) {
            tag.put("reactorView", writeView());
        } else {
            tag.putBoolean("reactorOff", true);
        }
    }
}
