package fr.pioneers.core.profile;

import fr.pioneers.core.common.look.LookCards;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.FarmCard.Line;
import fr.pioneers.tech.industry.multi.ControllerTile;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.9 (MAJ RP) : « Diagnostic au premier regard » de l'Ingenieur. En regardant une machine du socle, il lit sa fiche sans l'ouvrir : energie,
 * marche ou arret, avancement ; pour un multibloc, la raison exacte s'il n'est pas forme. Les autres joueurs n'ont pas de fiche (le serveur
 * renvoie rien).
 */
public final class Diagnosis {

    private Diagnosis() {
    }

    static void init() {
        LookCards.register(s -> s.getBlock() instanceof MachineBlock, Diagnosis::card);
    }

    @Nullable
    private static FarmCard card(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, boolean sneaking) {
        if (PioneerApi.modifier(player, "engineer.diagnosis") <= 0 || !(level.getBlockEntity(pos) instanceof MachineTile tile)) {
            return null;
        }
        FarmCard card = FarmCard.of(new ItemStack(state.getBlock()), state.getBlock().getName(), 0x4A8AD0);
        long stored = tile.guiValue(0), capacity = tile.guiValue(1);
        if (capacity > 0) {
            card.add(Line.gauge(Component.translatable("pioneer.pioneers.diagnosis.energy"), stored / (float) capacity, FarmCard.ratio(stored, capacity),
                  stored > 0 ? FarmCard.OK : FarmCard.BAD));
        }
        if (tile instanceof ControllerTile controller) {
            if (!controller.formed()) {
                card.add(Line.text(ControllerTile.errorText((int) tile.guiValue(5)).copy(), FarmCard.BAD));
                BlockPos at = controller.errorAt();
                if (at != null) {
                    card.add(Line.pair(Component.translatable("pioneer.pioneers.diagnosis.at"),
                          Component.literal(at.getX() + " " + at.getY() + " " + at.getZ()), FarmCard.WARN));
                }
            } else {
                card.add(Line.pair(Component.translatable("pioneer.pioneers.diagnosis.status"),
                      Component.translatable("industry.pioneers.status." + tile.guiValue(4)), FarmCard.INFO));
                card.add(Line.bar(Component.translatable("pioneer.pioneers.diagnosis.progress"), tile.guiValue(6) / 1000F, 0x4A8AD0));
            }
        } else if (state.hasProperty(MachineBlock.ACTIVE)) {
            boolean on = state.getValue(MachineBlock.ACTIVE);
            card.add(Line.text(Component.translatable(on ? "pioneer.pioneers.diagnosis.on" : "pioneer.pioneers.diagnosis.off"),
                  on ? FarmCard.OK : FarmCard.DIM));
        }
        return card;
    }
}
