package fr.pioneers.pneumatic.tools;

import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.RegistryObject;

/**
 * Air comprime au travail : outils a reservoir (marteau-piqueur, cle a chocs, souffleur, sableuse, bouteille de plongee). MAJ simplification
 * (03/11/2026, demande de Guillaume) : les machines pneumatiques (presse, marteau, tarare) et le compresseur a piston sont retires
 * (anciens ids : {@code PiRemaps.simplifiedBlock}).
 */
public final class PiPneumaticTools {

    private PiPneumaticTools() {
    }

    /** Objets dans l'ordre de l'onglet (equipement). */
    public static final List<Supplier<? extends ItemLike>> TOOLS = new ArrayList<>();

    public static void init() {
    }

    private static RegistryObject<Item> tool(String name, Supplier<Item> item) {
        RegistryObject<Item> registered = PiCoreRegistries.ITEMS.register(name, item);
        TOOLS.add(registered);
        return registered;
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    public static final RegistryObject<Item> JACKHAMMER = tool("pneumatic_jackhammer", () -> new JackhammerItem(new Item.Properties()));
    public static final RegistryObject<Item> IMPACT_WRENCH = tool("impact_wrench", () -> new ImpactWrenchItem(new Item.Properties()));
    public static final RegistryObject<Item> AIR_BLOWER = tool("air_blower", () -> new BlowerItem(new Item.Properties()));
    public static final RegistryObject<Item> SANDBLASTER = tool("sandblaster", () -> new SandblasterItem(new Item.Properties()));
    public static final RegistryObject<Item> DIVING_TANK = tool("diving_tank", () -> new DivingTankItem(new Item.Properties()));
}
