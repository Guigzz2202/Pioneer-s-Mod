package fr.pioneers.core.gas;

import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import java.util.function.Supplier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

/**
 * Gaz du socle Pioneer's (registre propre, sans Mekanism). Memes identifiants et couleurs que les gaz Mekanism du mod
 * ({@code compat.mekanism.MekGases}), qui restent le temps de la migration des machines : la passerelle les fait
 * correspondre un a un.
 */
public final class PiGasTypes {

    private PiGasTypes() {
    }

    /**
     * Teintes des gaz (source unique, realistes : charte design `claude/synthese-design-textures.md` §2.6 ; miroir : GAS_COLORS / GAS_OTHERS
     * de tools/gc/core/item_assets.py). Les gaz Mekanism du mod (MekGases, si Mekanism est present) reprennent ces valeurs.
     */
    public static final class Colors {

        private Colors() {
        }

        public static final int VOID_SINGULARITY = 0x2B0F4D;
        public static final int STELLAR_PLASMA = 0xFFB04A;
        public static final int NITROGEN = 0xC4D2DE;
        public static final int HELIUM_3 = 0xF5DDA0;
        public static final int METHANE = 0x9CC6D8;
        public static final int AMMONIA = 0xE6E0C8;
        public static final int SUPERHEATED_AMMONIA = 0xF0B8A0;
        public static final int HYDROGEN = 0xEEF3F7;
        public static final int OXYGEN = 0x8DBFE8;
        public static final int DEUTERIUM = 0xD2E0F2;
        public static final int TRITIUM = 0x9FE8B0;
        public static final int PLUTONIUM = 0xA8642E;
    }

    public static final DeferredRegister<Gas> GASES = DeferredRegister.create(PioneersAPI.GAS_REGISTRY_NAME, Pioneers.MODID);

    public static final Supplier<IForgeRegistry<Gas>> REGISTRY = GASES.makeRegistry(() -> new RegistryBuilder<Gas>()
          .setDefaultKey(Pioneers.rl("empty")));

    public static final RegistryObject<Gas> EMPTY = GASES.register("empty", () -> Gas.EMPTY);

    /** Gaz de singularite du vide : produit par le stabilisateur de trou noir. */
    public static final RegistryObject<Gas> VOID_SINGULARITY = GASES.register("void_singularity", () -> new Gas(new Gas.Properties(Colors.VOID_SINGULARITY)));
    /** Plasma stellaire : produit par la chambre stellaire. */
    public static final RegistryObject<Gas> STELLAR_PLASMA = GASES.register("stellar_plasma", () -> new Gas(new Gas.Properties(Colors.STELLAR_PLASMA)));
    /** Azote : capte dans l'atmosphere. */
    public static final RegistryObject<Gas> NITROGEN = GASES.register("nitrogen", () -> new Gas(new Gas.Properties(Colors.NITROGEN)));
    /** Helium 3 (Saturne). */
    public static final RegistryObject<Gas> HELIUM_3 = GASES.register("helium_3", () -> new Gas(new Gas.Properties(Colors.HELIUM_3)));
    /** Methane (Uranus) : combustible (100 FE par mB, comme 250 J). */
    public static final RegistryObject<Gas> METHANE = GASES.register("methane", () -> new Gas(new Gas.Properties(Colors.METHANE).fuel(100)));
    /** Ammoniac (Neptune) : caloporteur. */
    public static final RegistryObject<Gas> AMMONIA = GASES.register("ammonia", () -> new Gas(new Gas.Properties(Colors.AMMONIA)
          .heatsInto(() -> PiGasTypes.SUPERHEATED_AMMONIA.get())));
    public static final RegistryObject<Gas> SUPERHEATED_AMMONIA = GASES.register("superheated_ammonia", () -> new Gas(new Gas.Properties(Colors.SUPERHEATED_AMMONIA)
          .coolsInto(() -> PiGasTypes.AMMONIA.get())));

    // Gaz de base (standard items v2), propres au mod : hydrogene, oxygene, deuterium, tritium, plutonium (les trois derniers a confirmer).
    public static final RegistryObject<Gas> HYDROGEN = GASES.register("hydrogen", () -> new Gas(new Gas.Properties(Colors.HYDROGEN)));
    public static final RegistryObject<Gas> OXYGEN = GASES.register("oxygen", () -> new Gas(new Gas.Properties(Colors.OXYGEN)));
    public static final RegistryObject<Gas> DEUTERIUM = GASES.register("deuterium", () -> new Gas(new Gas.Properties(Colors.DEUTERIUM)));
    public static final RegistryObject<Gas> TRITIUM = GASES.register("tritium", () -> new Gas(new Gas.Properties(Colors.TRITIUM)));
    public static final RegistryObject<Gas> PLUTONIUM = GASES.register("plutonium", () -> new Gas(new Gas.Properties(Colors.PLUTONIUM)));
}
