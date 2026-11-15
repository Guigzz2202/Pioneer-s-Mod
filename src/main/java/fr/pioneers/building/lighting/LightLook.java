package fr.pioneers.building.lighting;

import fr.pioneers.core.common.look.LookCards;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.FarmCard.Line;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.0.4-beta (lot 5) : FICHES AU REGARD de l'eclairage ({@link LookCards}) : l'interrupteur (marche / arret, lumieres liees, comment lier) et
 * la lumiere (libre ou liee, a quel interrupteur, redstone).
 */
public final class LightLook {

    static final int ACCENT = 0xE8B860;

    private LightLook() {
    }

    public static void init() {
        LookCards.register(s -> s.getBlock() instanceof LightSwitchBlock, (level, pos, state, player, sneaking) -> lightSwitch(level, pos, state));
        LookCards.register(s -> s.getBlock() instanceof LightFixtureBlock, (level, pos, state, player, sneaking) -> fixture(level, pos, state));
    }

    private static FarmCard lightSwitch(ServerLevel level, BlockPos pos, BlockState state) {
        LightLinks data = LightLinks.get(level);
        boolean on = state.getValue(LightSwitchBlock.POWERED);
        int n = data.lightsOf(pos).size();
        List<Line> lines = new ArrayList<>();
        lines.add(Line.pair(FarmCard.label("light.state"), Component.translatable(on ? "card.pioneers.light.on" : "card.pioneers.light.off"),
              on ? FarmCard.OK : FarmCard.DIM));
        lines.add(Line.pair(FarmCard.label("light.linked"), FarmCard.ratio(n, LightLinks.MAX_LIGHTS), n == 0 ? FarmCard.WARN : FarmCard.INFO));
        lines.add(Line.hint(Component.translatable(n == 0 ? "card.pioneers.light.switch_hint_empty" : "card.pioneers.light.switch_hint")));
        return new FarmCard(new ItemStack(state.getBlock()), state.getBlock().getName(), ACCENT, lines);
    }

    private static FarmCard fixture(ServerLevel level, BlockPos pos, BlockState state) {
        LightLinks data = LightLinks.get(level);
        BlockPos sw = data.switchOf(pos);
        boolean lit = state.getValue(LightFixtureBlock.LIT);
        List<Line> lines = new ArrayList<>();
        lines.add(Line.pair(FarmCard.label("light.state"), Component.translatable(lit ? "card.pioneers.light.lit" : "card.pioneers.light.dark"),
              lit ? FarmCard.OK : FarmCard.DIM));
        if (sw == null) {
            lines.add(Line.text(Component.translatable("card.pioneers.light.free"), FarmCard.INFO));
            lines.add(Line.hint(Component.translatable("card.pioneers.light.free_hint")));
        } else {
            lines.add(Line.pair(FarmCard.label("light.switch"), Component.translatable("card.pioneers.light.at", sw.getX(), sw.getY(), sw.getZ()),
                  FarmCard.INFO));
            if (level.hasNeighborSignal(pos)) {
                lines.add(Line.text(Component.translatable("card.pioneers.light.redstone"), FarmCard.WARN));
            }
            lines.add(Line.hint(Component.translatable("card.pioneers.light.linked_hint")));
        }
        return new FarmCard(new ItemStack(state.getBlock()), state.getBlock().getName(), ACCENT, lines);
    }
}
