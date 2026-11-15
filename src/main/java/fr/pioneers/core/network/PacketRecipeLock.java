package fr.pioneers.core.network;

import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.logistics.robotics.logistics.LogisticTerminals;
import fr.pioneers.logistics.robotics.logistics.TileEntityLogisticTerminal;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * MAJ Recettes (lot 7) : client -> serveur : poser (identifiant de recette) ou retirer (texte vide) le verrou de recette d'une machine,
 * depuis son interface (case « Recette », glisser depuis JEI) ou depuis un terminal logistique ({@code via} = le terminal, la machine doit
 * etre servie par son reseau). Borne par {@link PacketRate} ; le serveur verifie la machine (distance, interface ouverte) et que la recette
 * existe et appartient bien a cette machine.
 */
public record PacketRecipeLock(BlockPos pos, @Nullable BlockPos via, String recipe) {

    /** Identifiant de recette le plus long accepte. */
    public static final int MAX = 256;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeBoolean(via != null);
        if (via != null) {
            buffer.writeBlockPos(via);
        }
        buffer.writeUtf(recipe, MAX);
    }

    public static PacketRecipeLock decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        BlockPos via = buffer.readBoolean() ? buffer.readBlockPos() : null;
        return new PacketRecipeLock(pos, via, buffer.readUtf(MAX));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || !PacketRate.allow(player, PacketRate.RECIPE_LOCK)) {
            return;
        }
        RecipeLockable target;
        if (via == null) {
            target = PacketGuard.tile(player, pos, RecipeLockable.class);
        } else {
            //Terminal logistique : le joueur est devant le terminal, la machine est servie par son reseau robotique
            TileEntityLogisticTerminal terminal = PacketGuard.tile(player, via, TileEntityLogisticTerminal.class);
            target = terminal == null ? null : LogisticTerminals.machine(terminal, pos);
        }
        if (target == null) {
            return;
        }
        ResourceLocation id = recipe.isEmpty() ? null : ResourceLocation.tryParse(recipe);
        if (!recipe.isEmpty() && id == null) {
            return;
        }
        if (!target.setLockedRecipe(id)) {
            player.displayClientMessage(Component.translatable("gui.pioneers.recipe_lock.refused"), true);
        }
    }
}
