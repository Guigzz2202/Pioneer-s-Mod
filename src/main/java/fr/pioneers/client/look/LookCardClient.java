package fr.pioneers.client.look;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.look.LookCards;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.core.network.PacketLookCard;
import fr.pioneers.core.network.PiNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.4 : cote client des fiches au regard calculees par le serveur ({@link LookCards}) : demande la fiche du bloc vise toutes les
 * {@link #PERIOD} ticks (tout de suite quand la cible ou l'accroupissement change), garde la derniere reponse ; {@code FarmHud} la dessine.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class LookCardClient {

    private static final int PERIOD = 10, STALE = 40;

    @Nullable
    private static BlockPos target;
    private static boolean targetSneak;
    private static int sinceAsk;
    @Nullable
    private static BlockPos cardPos;
    @Nullable
    private static FarmCard card;
    private static int cardAge;

    private LookCardClient() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        cardAge++;
        if (mc.level == null || mc.player == null || mc.screen != null) {
            target = null;
            return;
        }
        HitResult hit = mc.hitResult;
        BlockPos pos = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
        if (pos == null || !LookCards.has(mc.level.getBlockState(pos))) {
            target = null;
            return;
        }
        boolean sneak = mc.player.isShiftKeyDown();
        sinceAsk++;
        if (!pos.equals(target) || sneak != targetSneak || sinceAsk >= PERIOD) {
            target = pos.immutable();
            targetSneak = sneak;
            sinceAsk = 0;
            PiNetwork.sendToServer(new PacketLookCard.Request(target, sneak));
        }
    }

    public static void receive(BlockPos pos, @Nullable FarmCard reply) {
        cardPos = pos;
        card = reply;
        cardAge = 0;
    }

    /** Fiche a dessiner pour le bloc vise, ou null. */
    @Nullable
    public static FarmCard card(BlockPos pos) {
        return card != null && pos.equals(cardPos) && pos.equals(target) && cardAge < STALE ? card : null;
    }
}
