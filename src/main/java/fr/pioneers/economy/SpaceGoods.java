package fr.pioneers.economy;

import fr.pioneers.Pioneers;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0.4-beta (lot 4, Guillaume : « Les villageois ne doivent pas vendre d'objets de l'espace (Le fermier vend des graines de l'espace) ») :
 * objets de l'espace, que ni les villageois ni le stock des Colporteurs ne proposent. Tag {@code pioneers:space_only} GENERE par
 * T:economy/gen_values.py (age MkIV ou cosmique, familles space / cosmic, plantes des astres et leurs graines) ; les catalogues generes
 * (T:economy/trade.py) les excluent deja, ce filtre couvre les evenements de village et les donnees modifiees. Controle :
 * tools/check_p104_4.py et J:gametest/PatchP104Lot4Tests.
 */
public final class SpaceGoods {

    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, Pioneers.rl("space_only"));

    private SpaceGoods() {
    }

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TAG);
    }

    public static boolean is(Item item) {
        return is(new ItemStack(item));
    }
}
