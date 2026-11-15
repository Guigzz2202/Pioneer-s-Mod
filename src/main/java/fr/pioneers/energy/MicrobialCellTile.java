package fr.pioneers.energy;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.StandardLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.45 : pile microbienne (energie bio). Des bacteries digerent la matiere organique entre deux electrodes et rendent du courant, sans
 * combustion. Elle mange tout ce qui se composte (graines, feuilles, recoltes, plats...) et les residus du vivant du tag
 * {@code pioneers:organic_matter} (copeaux, bagasse, tourteau, microalgues...). Un objet vaut sa chance de compostage en unites
 * (les residus du tag : 0,65), chaque unite rendant {@link EnergyKind#MICROBIAL_UNIT} FE ; la pile produit au plus sa puissance tant
 * qu'elle a a manger. L'objet entre par toutes les faces.
 * <p>
 * Interface : 8 matiere en cours (FE restants), 9 objets digeres, 10 energie de l'objet en cours (FE).
 * <p>
 * API Machine (lot 3) : la logique (case de matiere organique, combustible) est dans {@link MicrobialLogic} ; la tile la porte.
 */
public class MicrobialCellTile extends GeneratorTile {

    public static final TagKey<Item> ORGANIC = TagKey.create(Registries.ITEM, Pioneers.rl("organic_matter"));
    public static final int SLOT_X = StandardLayout.GEN_SLOT_X, SLOT_Y = StandardLayout.GEN_SLOT_Y;

    public MicrobialCellTile(BlockPos pos, BlockState state) {
        super(PiEnergy.MICROBIAL_TILE.get(), pos, state, EnergyKind.MICROBIAL,
              a -> new MicrobialLogic(a, fr.pioneers.core.machine.TieredMachineBlock.tierOf(state)));
    }

    /** Unites de matiere organique d'un objet (0 : refuse). */
    public static double units(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        float chance = ComposterBlock.COMPOSTABLES.getFloat(stack.getItem());
        if (chance > 0) {
            return chance;
        }
        return stack.is(ORGANIC) ? 0.65 : 0;
    }
}
