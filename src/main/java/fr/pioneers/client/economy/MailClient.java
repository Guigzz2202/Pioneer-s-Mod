package fr.pioneers.client.economy;

import fr.pioneers.Pioneers;
import fr.pioneers.economy.mail.PiMail;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.4-beta (lot 29) : courrier cote client : nombre de lettres non lues du joueur local (vue {@code MAIL_STATE} du serveur, a la connexion,
 * a chaque lettre recue, a l'ouverture d'une boite) et enregistrement du rendu des boites aux lettres ({@link MailboxRenderer} : le « ! »).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MailClient {

    private static int unread;

    private MailClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PiMail.MAILBOX_TILE.get(), MailboxRenderer::new);
    }

    /** Vue du serveur : lettres non lues. */
    public static void state(CompoundTag t) {
        unread = Math.max(0, t.getInt("unread"));
    }

    public static int unread() {
        return unread;
    }
}
