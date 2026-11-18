package fr.pioneers.tech.forge.smithing;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.forge.HeatedMetal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Trempe au chaudron : clic droit sur un chaudron d'eau avec une piece chaude = trempe ; une fois sur trois le niveau d'eau baisse
 * (evaporation). 1.0.5-beta (lot 23) : allumage du foyer de forge a Maj + clic droit ({@link #hearthIgnite}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class SmithingEvents {

    private SmithingEvents() {
    }

    /**
     * 1.0.5-beta (lot 23) : Maj + clic droit sur le foyer de forge avec de quoi allumer ({@link ForgeHearthTile#isIgniter}) = allumage, et
     * rien d'autre (ni feu pose a cote, ni torche posee dessus). Le jeu n'appelle pas le bloc quand le joueur accroupi tient un objet : c'est
     * ici que l'allumage se fait. Sans Maj, le clic droit ouvre l'interface (le bloc). L'allume-feu a archet garde son geste (pas dans la liste).
     */
    @SubscribeEvent
    public static void hearthIgnite(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        Level level = event.getLevel();
        if (!event.getEntity().isSecondaryUseActive() || !ForgeHearthTile.isIgniter(held)
            || !(level.getBlockEntity(event.getPos()) instanceof ForgeHearthTile hearth)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!level.isClientSide) {
            hearth.igniteBy(event.getEntity(), event.getHand());
        }
    }

    @SubscribeEvent
    public static void cauldron(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        Level level = event.getLevel();
        if (!HeatedMetal.is(held)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        //Chaude, ou piece finie refroidie sans trempe (elle donne simplement sa piece)
        if (!state.is(Blocks.WATER_CAULDRON) || !HeatedMetal.isHot(held, level) && HeatedMetal.shape(held).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) {
            return;
        }
        ItemStack result = HeatedMetal.quench(held, level);
        Quench.give(event.getEntity(), event.getHand(), held, result, level, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, true);
        if (level.random.nextInt(3) == 0) {
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        }
    }
}
