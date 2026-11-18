package fr.pioneers.tech.forge.smithing;

import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.forge.HeatedMetal;
import fr.pioneers.tech.forge.HeatedMetalItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Trempe a la main (chaudron, bac de trempe) : la piece tenue est remplacee par son resultat, pschitt, vapeur, progres {@code quench}. */
public final class Quench {

    private Quench() {
    }

    /**
     * {@code held} = la pile tenue (metal chauffe), {@code result} = {@link HeatedMetal#quench} deja calcule ; {@code effects} = pschitt et
     * progres ici (faux quand le bac de trempe s'en charge).
     */
    public static void give(Player player, InteractionHand hand, ItemStack held, ItemStack result, Level level, double x, double y, double z,
          boolean effects) {
        ItemStack hot = held.copy();
        held.shrink(1);
        if (held.isEmpty()) {
            player.setItemInHand(hand, result.copy());
        } else if (!player.getInventory().add(result.copy())) {
            player.drop(result.copy(), false);
        }
        for (ItemStack extra : HeatedMetal.quenchExtras(hot)) {
            if (!player.getInventory().add(extra.copy())) {
                player.drop(extra.copy(), false);
            }
        }
        if (effects && level instanceof ServerLevel server && HeatedMetal.isHot(hot, level)) {
            HeatedMetalItem.hiss(server, x, y, z);
            BronzeEvents.award(server, BlockPos.containing(x, y, z), "quench");
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                fr.pioneers.core.profile.PioneerApi.gain(sp, "smithing.quench"); //1.9 : trempe reussie a la main
            }
        }
    }
}
