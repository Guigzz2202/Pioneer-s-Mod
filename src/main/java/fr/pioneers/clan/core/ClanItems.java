package fr.pioneers.clan.core;

import fr.pioneers.core.registry.PiCoreRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

/** 1.9 (MAJ RP, bloc 4) : objets des fondations des clans (la Charte ; son etat « ouverte » est un predicat de modele, pas un autre objet). */
public final class ClanItems {

    public static final RegistryObject<Item> CHARTER = PiCoreRegistries.ITEMS.register("clan_charter",
          () -> new CharterItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private ClanItems() {
    }

    /** Charge les registres (appele par {@code ClanBootstrap.init}). */
    public static void init() {
    }
}
