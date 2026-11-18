package fr.pioneers.society.economy.contract;

import fr.pioneers.core.registry.PiCoreRegistries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/** 1.0.4-beta (lot 7) : objet des contrats : la facture imprimee ({@link InvoiceItem}). */
public final class ContractItems {

    public static final RegistryObject<Item> INVOICE = PiCoreRegistries.ITEMS.register("contract_invoice",
          () -> new InvoiceItem(new Item.Properties().stacksTo(1)));

    private ContractItems() {
    }

    /** Charge le registre (appele par {@link ContractService#init}). */
    static void init() {
    }
}
