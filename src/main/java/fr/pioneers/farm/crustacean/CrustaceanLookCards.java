package fr.pioneers.farm.crustacean;

import fr.pioneers.common.look.LookCards;
import fr.pioneers.farm.FarmCard.Line;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.fish.FishSpawns;
import fr.pioneers.farm.fish.GeneratedFish;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** 1.4 : fiches au regard (serveur, {@link LookCards}) du filet de peche et du casier a crabe. */
public final class CrustaceanLookCards {

    private CrustaceanLookCards() {
    }

    public static void init() {
        LookCards.register(s -> s.getBlock() instanceof TrapBlock, (level, pos, state, player, sneaking) ->
              level.getBlockEntity(pos) instanceof TrapTile t ? card(level, pos, state, t) : null);
    }

    /** 1.6 : fiche synthetique (eaux, eau ou appat, prochaine prise, prises). */
    private static FarmCard card(ServerLevel level, BlockPos pos, BlockState state, TrapTile t) {
        List<Line> lines = new ArrayList<>();
        boolean net = t.kind() == TrapBlock.Kind.NET;
        int habitat = FishSpawns.habitat(level, pos);
        if (!t.submerged()) {
            lines.add(Line.text(FarmCard.word("trap.dry"), FarmCard.BAD));
        } else {
            boolean empty = !net && CrustaceanSpawns.pickIn(habitat, level.random, true) < 0;
            lines.add(Line.pair(FarmCard.label("waters"), Component.translatable("farm.pioneers.fish.habitat." + GeneratedFish.HABITATS[Math.max(0, habitat)]),
                  empty ? FarmCard.WARN : 0x6AB0D0));
            if (net) {
                int w = t.water();
                lines.add(Line.gauge(FarmCard.label("water"), Math.min(1F, w / 60F), Component.translatable("card.pioneers.blocks", w),
                      w < TrapTile.NET_MIN_WATER ? FarmCard.BAD : FarmCard.WATER));
            } else if (empty) {
                lines.add(Line.pair(FarmCard.label("catch"), FarmCard.word("trap.nothing"), FarmCard.WARN));
            } else {
                ItemStack bait = t.bait();
                lines.add(bait.isEmpty() ? Line.pair(FarmCard.label("bait"), FarmCard.word("none"), FarmCard.WARN)
                                         : Line.pair(FarmCard.label("bait"), FarmCard.stack(bait), FarmCard.INFO));
            }
            lines.add(t.full() ? Line.pair(FarmCard.label("next"), FarmCard.word("full"), FarmCard.BAD)
                               : Line.gauge(FarmCard.label("next"), t.progress() / (float) t.goal(), FarmCard.percent(t.progress() / (float) t.goal()),
                                     FarmCard.OK));
        }
        lines.add(Line.pair(FarmCard.label("caught"), Component.literal(String.valueOf(t.caught())), t.caught() > 0 ? FarmCard.OK : FarmCard.DIM));
        return new FarmCard(new ItemStack(state.getBlock()), state.getBlock().getName(), net ? 0x4A8AF0 : 0xC07040, lines);
    }
}
