package fr.pioneers.logistics.warehouse;

import fr.pioneers.tech.kinetics.rope.LinkType;
import fr.pioneers.tech.kinetics.rope.PulleyTile;
import fr.pioneers.tech.kinetics.rope.RopeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1, puis lot 27) : cordes de l'entrepot. Depuis le lot 27 (Guillaume : « Supprime la poulie d'entrepot. Les poulies de
 * bases sont suffisantes ») une ligne d'entrepot n'est faite que de poulies de base a axe vertical, reliees par l'objet corde comme toute
 * poulie ({@link RopeItem}, memes regles : axes paralleles, meme plan, portee, trajet libre, meme sens de rotation) ; ici : les messages
 * propres a l'entrepot (station desservie, colis genes) et la pose directe des GameTests.
 */
public final class WarehouseRopes {

    private WarehouseRopes() {
    }

    /** Message de pose : longueur, et la station desservie par un des bouts (ou ce qui l'en empeche), ou les colis genes. */
    public static Component done(Level level, BlockPos a, BlockPos b, Component base) {
        Component msg = base;
        for (BlockPos p : new BlockPos[]{b, a}) {
            LineNode node = LineNode.at(level, p);
            WarehouseLines.Station st = node != null && node.stationWheel() ? WarehouseLines.station(level, p) : null;
            if (st != null) {
                return msg.copy().append(" · ").append(Component.translatable(st.counter() ? "warehouse.pioneers.pulley.drop_counter"
                      : "warehouse.pioneers.pulley.drop_crate", st.gap()).withStyle(ChatFormatting.GOLD));
            }
            Component why = node == null ? null : WarehouseLines.stationProblem(level, p);
            if (why != null) {
                return msg.copy().append(" · ").append(why.copy().withStyle(ChatFormatting.YELLOW));
            }
        }
        return msg;
    }

    /** Raison du refus d'une corde entre {@code a} et {@code b} (regles de {@link RopeItem#check}), ou null. GameTests, apercu de pose. */
    @Nullable
    public static Component check(BlockGetter level, BlockPos a, BlockPos b) {
        return level instanceof Level l ? RopeItem.check(l, a, b, false) : null;
    }

    /** Pose directe (GameTests) : relie deux poulies si la corde est permise (droite, la premiere la dessine) ; vrai si posee. */
    public static boolean link(Level level, BlockPos a, BlockPos b, LinkType type) {
        if (check(level, a, b) != null || !(level.getBlockEntity(a) instanceof PulleyTile pa) || !(level.getBlockEntity(b) instanceof PulleyTile pb)) {
            return false;
        }
        pa.connect(b, type, false, true);
        pb.connect(a, type, false, false);
        return true;
    }
}
