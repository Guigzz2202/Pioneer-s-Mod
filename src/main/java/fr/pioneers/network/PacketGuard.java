package fr.pioneers.network;

import fr.pioneers.compat.mekanism.MekanismCompat;
import fr.pioneers.compat.mekanism.MekanismMenus;
import fr.pioneers.core.menu.PiTileMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Verifications communes des paquets envoyes par les clients : on ne fait jamais confiance a une position recue.
 * <ul>
 *     <li>le bloc doit etre charge, dans la dimension du joueur ;</li>
 *     <li>le joueur doit avoir l'interface de ce bloc ouverte, ou etre a portee de main ;</li>
 *     <li>un spectateur n'a droit a rien.</li>
 * </ul>
 */
public final class PacketGuard {

    private PacketGuard() {
    }

    /** Portee d'interaction (blocs) quand l'interface n'est pas ouverte. */
    public static final double REACH = 8;

    /** Bloc cible d'un paquet d'interface, ou null si le joueur n'y a pas droit. */
    @Nullable
    public static <T> T tile(@Nullable ServerPlayer player, BlockPos pos, Class<T> type) {
        //0.20.46 (audit) : un spectateur n'agit sur rien (il passait a moins de 8 blocs, sans interface ouverte)
        if (player == null || player.isSpectator() || !player.level().isLoaded(pos)) {
            return null;
        }
        BlockEntity tile = player.level().getBlockEntity(pos);
        if (!type.isInstance(tile)) {
            return null;
        }
        boolean open = player.containerMenu instanceof PiTileMenu<?> menu && menu.getTile() == tile
                       || MekanismCompat.LOADED && MekanismMenus.isOpen(player.containerMenu, tile);
        if (!open && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH * REACH) {
            return null;
        }
        return type.cast(tile);
    }

    /** Texte libre recu d'un client (nom) : longueur bornee, sans caracteres de controle. */
    public static String sanitize(String text, int maxLength) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length() && builder.length() < maxLength; i++) {
            char c = text.charAt(i);
            if (c >= ' ' && c != '\u007f' && c != '§') {
                builder.append(c);
            }
        }
        return builder.toString().trim();
    }
}
