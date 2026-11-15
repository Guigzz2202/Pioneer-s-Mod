package fr.pioneers.clan.faction;

import fr.pioneers.core.registry.PiCoreRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

/** 1.9 (MAJ RP, bloc 4) : objets des factions (le Pacte federal ; son etat « ouvert » est un predicat de modele, comme la Charte). */
public final class FactionItems {

    public static final RegistryObject<Item> PACT = PiCoreRegistries.ITEMS.register("federal_pact",
          () -> new FederalPactItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private FactionItems() {
    }

    /** Charge les registres (appele par {@code FactionBootstrap.init}). */
    public static void init() {
    }
}
