package fr.pioneers.home.line;

import fr.pioneers.Pioneers;
import fr.pioneers.network.PacketClothesline;
import fr.pioneers.network.PiNetwork;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Patch 1.0.3-beta, lot 7 : clic droit SUR LA CORDE elle-meme (en l'air, ou devant un bloc plus loin) : avec un objet qui s'accroche, il va a
 * la place visee ; main vide, on reprend la piece la plus proche du point vise. Les deux cotes font la meme visee (le client annule son geste
 * normal, ex. manger le poisson ; le serveur agit) ; main vide dans le vide : {@link PacketClothesline}.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class LineEvents {

    /** Portee de la main (blocs) et rayon de visee autour de la corde. */
    public static final double REACH = 4.5, RADIUS = 0.3;

    private LineEvents() {
    }

    /** Corde visee : maitre, position (0..1) et distance, ou null. */
    public record Aim(HookTile line, double t, double distance) {}

    @Nullable
    public static Aim aim(Player player, double maxDistance) {
        Level level = player.level();
        Vec3 from = player.getEyePosition(), dir = player.getViewVector(1F);
        double reach = Math.min(REACH, maxDistance);
        Aim best = null;
        for (HookTile line : HookTile.lines(level)) {
            if (line.isRemoved() || !line.isMaster() || line.getBlockPos().distToCenterSqr(from) > (Clothesline.MAX_LENGTH + REACH) * (Clothesline.MAX_LENGTH + REACH)) {
                continue;
            }
            double[] hit = Clothesline.pick(line.eye(), line.otherEye(), line.sag(), from, dir, reach, RADIUS);
            if (hit != null && (best == null || hit[1] < best.distance)) {
                best = new Aim(line, hit[0], hit[1]);
            }
        }
        return best;
    }

    /** Geste du joueur sur la corde visee (serveur et client) ; vrai si la corde a pris le clic. */
    public static boolean interact(Player player, InteractionHand hand, double maxDistance) {
        Aim a = aim(player, maxDistance);
        if (a == null) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            if (hand != InteractionHand.MAIN_HAND || a.line.hung().isEmpty()) {
                return false;
            }
            if (!player.level().isClientSide) {
                a.line.take(player, a.t);
            }
            return true;
        }
        if (!Clothesline.hangable(player.level(), held)) {
            return false;
        }
        if (!player.level().isClientSide) {
            a.line.hang(player, held, a.t);
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (interact(event.getEntity(), event.getHand(), Double.MAX_VALUE)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        //La corde passe devant le bloc vise : elle prend le clic (jamais quand on vise un crochet : le bloc s'en charge)
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof HookBlock) {
            return;
        }
        double block = event.getEntity().getEyePosition().distanceTo(event.getHitVec().getLocation());
        if (interact(event.getEntity(), event.getHand(), block)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    @SubscribeEvent
    public static void rightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        //Client seulement : rien n'arrive au serveur pour un clic dans le vide main vide
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getItemStack().isEmpty() && aim(event.getEntity(), Double.MAX_VALUE) != null) {
            PiNetwork.sendToServer(new PacketClothesline());
        }
    }
}
