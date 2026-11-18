package fr.pioneers.core.registries;

import fr.pioneers.Pioneers;
import fr.pioneers.space.CapsuleMenu;
import fr.pioneers.space.module.RocketMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Menus "vanilla" (hors Mekanism) du mod.
 */
public final class PiMenus {

    private PiMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Pioneers.MODID);

    public static final RegistryObject<MenuType<CapsuleMenu>> CAPSULE = MENUS.register("capsule", () -> IForgeMenuType.create(CapsuleMenu::new));
    /** Fusee : modules et soutes (accroupi + clic droit). */
    public static final RegistryObject<MenuType<RocketMenu>> ROCKET = MENUS.register("rocket",
          () -> IForgeMenuType.create(RocketMenu::new));
}
