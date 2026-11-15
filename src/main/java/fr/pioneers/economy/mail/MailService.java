package fr.pioneers.economy.mail;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 29) : le COURRIER cote serveur. {@link #deliver} depose une lettre dans la boite d'un joueur, connecte ou non (le virement
 * de {@code EconomyApi.transfer}, les evenements des contrats de {@code ContractService}) et previent son client ({@link RpActions#MAIL_STATE} :
 * nombre de lettres non lues, pour le « ! » au-dessus de ses boites) ; {@link #open} ouvre la boite du proprietaire (coffre de 3 rangees,
 * le meme contenu dans toutes ses boites) et la marque lue. A la connexion, le client recoit son compte de non lus.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class MailService {

    private MailService() {
    }

    static void init() {
    }

    /** Depose une lettre dans la boite de {@code to} (connecte ou non) ; son client est prevenu s'il est en ligne. */
    public static void deliver(MinecraftServer server, UUID to, ItemStack letter) {
        if (server == null || to == null || letter.isEmpty()) {
            return;
        }
        MailData.Box b = MailData.get(server).deliver(to, letter);
        ServerPlayer p = server.getPlayerList().getPlayer(to);
        if (p != null) {
            b.name = p.getGameProfile().getName();
            if (p.containerMenu instanceof ChestMenu cm && cm.getContainer() instanceof View v && v.box == b) {
                b.flush();
                b.unread = 0;                                 //boite ouverte sous ses yeux : la lettre est deja vue
            }
            state(p, b.unread);
            p.displayClientMessage(Component.translatable("mail.pioneers.msg.new_letter",
                  Component.translatable("mail.pioneers.kind." + letterKind(letter))).withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static String letterKind(ItemStack letter) {
        CompoundTag t = Letters.data(letter);
        return t == null ? "transfer" : t.getString("k");
    }

    /** Compte de non lus envoye au client du joueur. */
    public static void state(ServerPlayer p, int unread) {
        CompoundTag t = new CompoundTag();
        t.putInt("unread", unread);
        RpNet.send(p, RpActions.MAIL_STATE, t);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getServer() != null) {
            MailData.Box b = MailData.get(p.getServer()).find(p.getUUID());
            state(p, b == null ? 0 : b.unread);
            if (b != null && b.unread > 0) {
                p.sendSystemMessage(Component.translatable("mail.pioneers.msg.waiting", Integer.toString(b.unread)).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** Le proprietaire ouvre sa boite posee en {@code pos} : coffre de 3 rangees sur sa boite ; plus rien de non lu. */
    public static void open(ServerPlayer p, BlockPos pos) {
        MinecraftServer server = p.getServer();
        if (server == null) {
            return;
        }
        MailData data = MailData.get(server);
        MailData.Box b = data.box(p.getUUID());
        b.name = p.getGameProfile().getName();
        data.opened(p.getUUID());
        state(p, 0);
        View view = new View(b, p.level(), pos);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, view), Component.translatable("block.pioneers.mailbox")));
    }

    /**
     * La boite d'un joueur vue depuis un bloc : memes cases, fermee si le joueur s'eloigne ou si le bloc disparait ; a la fermeture, le
     * courrier en attente entre dans les cases liberees.
     */
    public static final class View implements Container {

        private final MailData.Box box;
        private final Level level;
        private final BlockPos pos;

        View(MailData.Box box, Level level, BlockPos pos) {
            this.box = box;
            this.level = level;
            this.pos = pos.immutable();
        }

        @Override
        public int getContainerSize() {
            return box.items.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return box.items.isEmpty();
        }

        @Override
        public @NotNull ItemStack getItem(int slot) {
            return box.items.getItem(slot);
        }

        @Override
        public @NotNull ItemStack removeItem(int slot, int amount) {
            return box.items.removeItem(slot, amount);
        }

        @Override
        public @NotNull ItemStack removeItemNoUpdate(int slot) {
            return box.items.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, @NotNull ItemStack stack) {
            box.items.setItem(slot, stack);
        }

        @Override
        public void setChanged() {
            box.items.setChanged();
        }

        @Override
        public boolean stillValid(@NotNull Player player) {
            return level.getBlockState(pos).is(PiMail.MAILBOX.getBlock())
                   && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MailRules.REACH * MailRules.REACH;
        }

        @Override
        public void stopOpen(@NotNull Player player) {
            box.flush();
        }

        @Override
        public void clearContent() {
            box.items.clearContent();
        }
    }
}
