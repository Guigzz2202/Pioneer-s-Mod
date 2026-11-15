package fr.pioneers.network;

import fr.pioneers.tile.base.TileEntityAssemblyBase;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Boutons des tables d'assemblage : lancer, arreter, repeter, changer de recette.
 */
public record PacketAssembler(BlockPos pos, Action action) {

    public enum Action {START, STOP, CYCLE_NEXT, REPEAT, CYCLE_BACK}

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeEnum(action);
    }

    public static PacketAssembler decode(FriendlyByteBuf buffer) {
        return new PacketAssembler(buffer.readBlockPos(), buffer.readEnum(Action.class));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        TileEntityAssemblyBase assembler = PacketGuard.tile(context.get().getSender(), pos, TileEntityAssemblyBase.class);
        if (assembler == null) {
            return;
        }
        switch (action) {
            case START -> assembler.startBuilding();
            case STOP -> assembler.cancelBuilding();
            case CYCLE_NEXT -> assembler.cycle(1);
            case REPEAT -> assembler.toggleRepeat();
            case CYCLE_BACK -> assembler.cycle(-1);
        }
    }
}
