package fr.pioneers.farm.greenhouse;

import fr.pioneers.common.look.LookCards;
import fr.pioneers.farm.FarmCard;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0.5-beta (lot 3) : FICHE AU REGARD du regulateur de serre (calculee par le serveur) : formee ou non, volume, dimensions, cultures,
 * climat interieur ; non formee : le probleme, la case (x y z) et le bloc fautif, et le rappel du plan (Maj + clic droit).
 */
public final class GreenhouseLook {

    private GreenhouseLook() {
    }

    public static void init() {
        LookCards.register(s -> s.getBlock() instanceof GreenhouseRegulatorBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof GreenhouseRegulatorTile tile ? card(tile) : null);
    }

    static FarmCard card(GreenhouseRegulatorTile tile) {
        tile.refresh();
        FarmCard c = FarmCard.of(new ItemStack(PiGreenhouse.REGULATOR), Component.translatable("block.pioneers.greenhouse_regulator"), 0x7CC860);
        if (tile.formed()) {
            int[] s = tile.size();
            c.add(FarmCard.Line.pair(FarmCard.label("greenhouse.state"), FarmCard.word("greenhouse.formed"), FarmCard.OK));
            c.add(FarmCard.Line.pair(FarmCard.label("greenhouse.volume"), FarmCard.word("greenhouse.volume_value", tile.volume()), FarmCard.INFO));
            c.add(FarmCard.Line.pair(FarmCard.label("greenhouse.size"), FarmCard.word("greenhouse.size_value", s[0], s[2], s[1]), FarmCard.INFO));
            c.add(FarmCard.Line.pair(FarmCard.label("greenhouse.crops"), Component.literal(String.valueOf(tile.crops())), FarmCard.INFO));
            c.add(FarmCard.Line.pair(FarmCard.label("greenhouse.climate"), FarmCard.word("greenhouse.ideal"), FarmCard.OK));
            return c;
        }
        c.add(FarmCard.Line.pair(FarmCard.label("greenhouse.state"), FarmCard.word("greenhouse.unformed"), FarmCard.BAD));
        c.add(FarmCard.Line.text(Component.translatable(tile.status().key()), FarmCard.WARN));
        BlockPos at = tile.at();
        if (at != null) {
            c.add(FarmCard.Line.pair(FarmCard.label("greenhouse.where"), Component.literal(at.getX() + " " + at.getY() + " " + at.getZ()), FarmCard.BAD));
            String block = tile.faultBlockKey();
            if (block != null && tile.status() != GreenhouseScan.Status.SHARED) {
                c.add(FarmCard.Line.pair(FarmCard.label("greenhouse.block"), Component.translatable(block), FarmCard.WARN));
            }
        }
        c.add(FarmCard.Line.hint(FarmCard.word("greenhouse.hint_plan")));
        return c;
    }
}
