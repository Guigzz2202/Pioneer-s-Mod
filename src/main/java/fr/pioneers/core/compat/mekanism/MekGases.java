package fr.pioneers.core.compat.mekanism;

import fr.pioneers.Pioneers;
import fr.pioneers.core.gas.PiGasTypes;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasBuilder;
import mekanism.api.chemical.gas.attribute.GasAttributes;
import mekanism.api.math.FloatingLong;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Copie des gaz du socle dans le registre de gaz de Mekanism (memes ids), pour que ses tuyaux et reservoirs les transportent : enregistre
 * seulement si Mekanism est present ({@link MekanismCompat#LOADED}).
 */
public final class MekGases {

    private MekGases() {
    }

    public static final DeferredRegister<Gas> GASES = DeferredRegister.create(MekanismAPI.GAS_REGISTRY_NAME, Pioneers.MODID);

    /** Couleur du gaz : violet tres sombre, presque noir. */
    public static final int VOID_SINGULARITY_COLOR = PiGasTypes.Colors.VOID_SINGULARITY;

    /** Void Singularity Gas : produit par le stabilisateur de trou noir (500 mB d'antimatiere -> 1 mB). */
    public static final RegistryObject<Gas> VOID_SINGULARITY = GASES.register("void_singularity",
          () -> new Gas(GasBuilder.builder().tint(VOID_SINGULARITY_COLOR)));

    /** Couleur du plasma stellaire : orange incandescent. */
    public static final int STELLAR_PLASMA_COLOR = PiGasTypes.Colors.STELLAR_PLASMA;

    /** Plasma stellaire : produit par la Chambre stellaire a partir de lave. */
    public static final RegistryObject<Gas> STELLAR_PLASMA = GASES.register("stellar_plasma",
          () -> new Gas(GasBuilder.builder().tint(STELLAR_PLASMA_COLOR)));

    /** Azote : capte dans l'atmosphere par le collecteur de gaz ; se liquefie au condensateur rotatif. */
    public static final int NITROGEN_COLOR = PiGasTypes.Colors.NITROGEN;
    public static final RegistryObject<Gas> NITROGEN = GASES.register("nitrogen", () -> new Gas(GasBuilder.builder().tint(NITROGEN_COLOR)));

    // ------------------------------------------------------------------
    // 0.10 : gaz des geantes gazeuses (poches recoltees par les drones)
    // ------------------------------------------------------------------

    /** Helium 3 (Saturne) : combustible de fusion (avec le deuterium) et propergol des moteurs interstellaires. */
    public static final int HELIUM_3_COLOR = PiGasTypes.Colors.HELIUM_3;
    public static final RegistryObject<Gas> HELIUM_3 = GASES.register("helium_3", () -> new Gas(GasBuilder.builder().tint(HELIUM_3_COLOR)));
    /** Methane (Uranus) : brule dans le generateur a gaz de Mekanism Generators. */
    public static final int METHANE_COLOR = PiGasTypes.Colors.METHANE;
    public static final RegistryObject<Gas> METHANE = GASES.register("methane", () -> new Gas(GasBuilder.builder().tint(METHANE_COLOR)
          .with(new GasAttributes.Fuel(40, FloatingLong.createConst(250)))));
    /** Ammoniac chaud : ammoniac rechauffe par un reacteur ou l'absorbeur solaire (alimente la chaudiere thermoelectrique). */
    public static final int SUPERHEATED_AMMONIA_COLOR = PiGasTypes.Colors.SUPERHEATED_AMMONIA;
    /** Ammoniac (Neptune) : caloporteur (reacteur a fission, absorbeur d'energie solaire). */
    public static final int AMMONIA_COLOR = PiGasTypes.Colors.AMMONIA;
    public static final RegistryObject<Gas> AMMONIA = GASES.register("ammonia", () -> new Gas(GasBuilder.builder().tint(AMMONIA_COLOR)
          .with(new GasAttributes.CooledCoolant(() -> MekGases.SUPERHEATED_AMMONIA.get(), 10, 1.0))));
    public static final RegistryObject<Gas> SUPERHEATED_AMMONIA = GASES.register("superheated_ammonia", () -> new Gas(GasBuilder.builder()
          .tint(SUPERHEATED_AMMONIA_COLOR).with(new GasAttributes.HeatedCoolant(() -> MekGases.AMMONIA.get(), 10, 1.0))));

    public static void register(IEventBus bus) {
        GASES.register(bus);
    }
}
