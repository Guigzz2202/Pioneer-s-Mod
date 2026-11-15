package fr.pioneers.farm.aquarium;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 1.0.5-beta (lot 25) : POSES ET RETRAITS de la composition (serveur ; demandes de {@link PacketAquarium}).
 * <ul>
 *     <li>la case visee doit etre une case du FOND du bassin ouvert ({@link #floorCells}) ;</li>
 *     <li>poser une sorte prend UN objet de l'inventaire (rien en creatif) ; l'element d'avant est rendu (inventaire, sinon au sol) ;</li>
 *     <li>retirer rend l'objet ; retirer un sol rend aussi la plante qui y etait enracinee ;</li>
 *     <li>une plante enracinee demande un sol ({@link AquariumDecor#refusal}).</li>
 * </ul>
 */
public final class AquariumCompose {

    private AquariumCompose() {
    }

    /** Cases du fond d'un bassin (aquarium sans aquarium dessous), ordre du bassin. */
    public static List<BlockPos> floorCells(Level level, List<BlockPos> tank) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : tank) {
            if (!(level.getBlockState(p.below()).getBlock() instanceof AquariumBlock)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Nombre d'objets de cette sorte dans l'inventaire du joueur. */
    public static int count(Player player, AquariumDecor.Kind kind) {
        ItemStack want = AquariumTile.itemOf(kind);
        if (want.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (ItemStack st : player.getInventory().items) {
            if (st.is(want.getItem())) {
                n += st.getCount();
            }
        }
        return n;
    }

    /**
     * Applique une demande : {@code kind} = index de {@link AquariumDecor#KINDS} (-1 : retirer), {@code layer} 0 sol / 1 element. Renvoie la
     * cle du refus (message au joueur) ou null.
     */
    public static String edit(Player player, BlockPos opened, BlockPos cell, int slot, int layer, int kind) {
        Level level = player.level();
        if (slot < 0 || slot >= AquariumDecor.SLOTS || layer < 0 || layer > 1 || !level.isLoaded(cell)
            || !(level.getBlockState(opened).getBlock() instanceof AquariumBlock)) {
            return "aquarium.pioneers.decor.refuse.unknown";
        }
        List<BlockPos> tank = Aquariums.tank(level, opened);
        if (!tank.contains(cell) || level.getBlockState(cell.below()).getBlock() instanceof AquariumBlock
            || !(level.getBlockEntity(cell) instanceof AquariumTile tile)) {
            return "aquarium.pioneers.decor.refuse.unknown";
        }
        boolean creative = player.getAbilities().instabuild;
        if (kind < 0) {
            AquariumDecor.Kind old = layer == 0 ? tile.ground(slot) : tile.piece(slot);
            if (old == null) {
                return null;
            }
            tile.setDecor(slot, layer, null);
            give(player, old, creative);
            if (layer == 0 && tile.piece(slot) != null && tile.piece(slot).rooted()) {
                give(player, tile.setDecor(slot, 1, null), creative);
            }
            sound(level, cell, old);
            return null;
        }
        AquariumDecor.Kind k = AquariumDecor.byIndex(kind);
        if (k == null || k.ground() != (layer == 0)) {
            return "aquarium.pioneers.decor.refuse.unknown";
        }
        String why = AquariumDecor.refusal(k, tile.ground(slot));
        if (why != null) {
            return "aquarium.pioneers.decor.refuse." + why;
        }
        if ((layer == 0 ? tile.ground(slot) : tile.piece(slot)) == k) {
            return null;
        }
        if (!creative && !take(player, k)) {
            return "aquarium.pioneers.decor.refuse.need";
        }
        give(player, tile.setDecor(slot, layer, k), creative);
        sound(level, cell, k);
        return null;
    }

    /** Prend un objet de la sorte dans l'inventaire (faux s'il n'y en a pas). */
    static boolean take(Player player, AquariumDecor.Kind kind) {
        ItemStack want = AquariumTile.itemOf(kind);
        if (want.isEmpty()) {
            return false;
        }
        for (ItemStack st : player.getInventory().items) {
            if (st.is(want.getItem())) {
                st.shrink(1);
                player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    /** Rend l'objet d'une sorte retiree (inventaire, sinon au pied du joueur) ; rien en creatif. */
    static void give(Player player, AquariumDecor.Kind kind, boolean creative) {
        ItemStack st = AquariumTile.itemOf(kind);
        if (st.isEmpty() || creative) {
            return;
        }
        if (!player.getInventory().add(st)) {
            player.drop(st, false);
        }
    }

    static void sound(Level level, BlockPos pos, AquariumDecor.Kind k) {
        SoundEvent s = switch (k.group()) {
            case SOL -> SoundEvents.SAND_PLACE;
            case VEGETATION -> SoundEvents.WET_GRASS_PLACE;
            case DECORATION -> SoundEvents.STONE_PLACE;
        };
        level.playSound(null, pos, s, SoundSource.BLOCKS, 0.7F, 1.1F);
    }

    /** Message de refus au joueur (barre d'action). */
    public static void tell(ServerPlayer player, String key, AquariumDecor.Kind k) {
        ItemStack st = AquariumTile.itemOf(k);
        player.displayClientMessage(Component.translatable(key, st.isEmpty() ? Component.literal("?") : st.getHoverName()), true);
    }
}
