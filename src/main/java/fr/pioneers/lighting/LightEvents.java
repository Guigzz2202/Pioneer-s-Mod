package fr.pioneers.lighting;

import fr.pioneers.Pioneers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.4-beta (lot 5) : lumieres des troncons decharges : quand un troncon charge (serveur), ses lumieres reliees sont remises a l'etat de
 * leur interrupteur au tick suivant ({@link LightLinks#chunkLoaded}, {@link LightLinks#tick}) ; memoire de liaison oubliee a la deconnexion ;
 * accroupi + clic main vide accepte meme l'autre main pleine ({@link #sneakClick}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class LightEvents {

    private LightEvents() {
    }

    @SubscribeEvent
    public static void chunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            LightLinks.get(level).chunkLoaded(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            LightLinks data = LightLinks.get(level);
            if (!data.pending.isEmpty()) {
                data.tick(level);
            }
        }
    }

    /**
     * Accroupi + clic droit MAIN VIDE sur un interrupteur ou une lumiere : le bloc est utilise meme si l'autre main tient un objet (vanilla
     * saute le bloc des qu'une des deux mains est pleine en etant accroupi : un bouclier ou une torche en main gauche empechait de lier).
     */
    @SubscribeEvent
    public static void sneakClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity().isShiftKeyDown() && event.getEntity().getMainHandItem().isEmpty()) {
            Block b = event.getLevel().getBlockState(event.getPos()).getBlock();
            if (b instanceof LightSwitchBlock || b instanceof LightFixtureBlock) {
                event.setUseBlock(Event.Result.ALLOW);
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LightLinks.forget(event.getEntity().getUUID());
    }
}
