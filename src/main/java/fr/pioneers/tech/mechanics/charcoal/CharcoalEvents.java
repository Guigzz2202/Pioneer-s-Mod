package fr.pioneers.tech.mechanics.charcoal;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registries.PiFluids;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Allumage des fosses (briquet, charge de feu ou allume-feu sur une buche couverte) et combustion a chaque tick du monde. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CharcoalEvents {

    private CharcoalEvents() {
    }

    /** Reponses qui designent une vraie meule (couverte) : le briquet est alors pris par la fosse ; sinon il garde son usage normal. */
    private static final Set<String> CLAIMED = Set.of("charcoal.pioneers.lit", "charcoal.pioneers.already", "charcoal.pioneers.no_vent",
          "charcoal.pioneers.openings", "charcoal.pioneers.vent_low");

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Boolean lit = ignitePit(level, event.getPos(), event.getEntity());
        if (lit == null) {
            return;                                                  //Tronc d'arbre, mur de rondins... : comportement normal
        }
        if (lit) {
            if (stack.is(Items.FLINT_AND_STEEL)) {
                stack.hurtAndBreak(1, event.getEntity(), p -> p.broadcastBreakEvent(event.getHand()));
            } else if (!event.getEntity().getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * Allume la fosse dont fait partie la buche {@code pos} (briquet, charge de feu, allume-feu de la forge primaire) et previent le joueur.
     * Renvoie null si ce n'est pas une vraie meule couverte (l'outil garde alors son usage normal), vrai si la fosse s'est allumee, faux si
     * la meule a refuse (deja allumee, pas d'event...).
     */
    @Nullable
    public static Boolean ignitePit(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (!CharcoalPits.looksLikePit(level, pos)) {
            return null;
        }
        Component message = CharcoalPits.get(level).ignite(level, pos);
        if (!(message.getContents() instanceof TranslatableContents contents) || !CLAIMED.contains(contents.getKey())) {
            return null;
        }
        if (player != null) {
            player.displayClientMessage(message, true);
        }
        return contents.getKey().equals("charcoal.pioneers.lit");
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            CharcoalPits.get(level).tick(level);
        }
    }

    /** Le seau de creosote brule comme 2/3 d'un seau de lave (le seau vide reste). */
    @SubscribeEvent
    public static void onBurnTime(FurnaceFuelBurnTimeEvent event) {
        if (event.getItemStack().is(PiFluids.CREOSOTE.bucket.get())) {
            event.setBurnTime(12_800);
        }
    }
}
