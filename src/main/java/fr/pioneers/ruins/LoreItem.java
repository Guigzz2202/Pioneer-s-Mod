package fr.pioneers.ruins;

import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.ProfileSync;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : objets de lore. Note de parchemin (NBT {@code note}), page de lore et module de memoire (NBT {@code entry}) ; la Note
 * de service d'une console est une page speciale ({@code entry = service_code}, NBT {@code code}). Un clic droit ouvre la lecture (fond de
 * parchemin) et range l'ecrit dans le Codex du Carnet ; l'objet n'est pas consomme. Seule une page (ou un module) lue pour la premiere fois
 * declenche {@link RuinEvents.LoreRead}.
 */
public class LoreItem extends Item {

    public enum Kind {
        NOTE, PAGE, MEMORY
    }

    public static final String SERVICE_CODE = "service_code";
    public final Kind kind;

    public LoreItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Nullable
    public static String id(ItemStack stack) {
        CompoundTag t = stack.getTag();
        if (t == null) {
            return null;
        }
        String id = t.getString(stack.getItem() instanceof LoreItem l && l.kind == Kind.NOTE ? "note" : "entry");
        return id.isEmpty() ? null : id;
    }

    public static ItemStack note(String id) {
        ItemStack s = new ItemStack(RuinItems.PARCHMENT_NOTE.get());
        s.getOrCreateTag().putString("note", id);
        return s;
    }

    public static ItemStack page(String id, boolean memory) {
        ItemStack s = new ItemStack(memory ? RuinItems.MEMORY_MODULE.get() : RuinItems.LORE_PAGE.get());
        s.getOrCreateTag().putString("entry", id);
        return s;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String id = id(stack);
        if (id == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer sp) {
            read(sp, stack, id);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 1.0.5-beta (lot 16) : lecture d'un ecrit range dans le bac d'archives (meme effet qu'un clic droit : Codex, evenement, ecran). */
    public static void readStored(ServerPlayer player, ItemStack stack) {
        String id = id(stack);
        if (id != null && stack.getItem() instanceof LoreItem l) {
            l.read(player, stack, id);
        }
    }

    private void read(ServerPlayer player, ItemStack stack, String id) {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        if (SERVICE_CODE.equals(id)) {
            t.putString("code", String.format("%04d", stack.getOrCreateTag().getInt("code")));
        } else {
            PioneerProfile p = PioneerCap.get(player);
            boolean fresh = kind == Kind.NOTE ? p.notesRead.add(id) : p.loreRead.add(id);
            if (fresh) {
                p.touch();
                ProfileSync.full(player);
                if (kind != Kind.NOTE) {
                    MinecraftForge.EVENT_BUS.post(new RuinEvents.LoreRead(player, id, LoreIndex.tier(id)));
                    player.displayClientMessage(Component.translatable("ruins.pioneers.lore.filed").withStyle(ChatFormatting.GOLD), true);
                }
            }
        }
        player.level().playSound(null, player.blockPosition(), RpSounds.PAGE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        RpNet.send(player, RpActions.LORE_PAGE, t);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        String id = id(stack);
        if (id != null && kind != Kind.NOTE && !SERVICE_CODE.equals(id)) {
            return Component.translatable(getDescriptionId()).append(" : ").append(Component.translatable("codex.pioneers." + id + ".title"));
        }
        if (SERVICE_CODE.equals(id)) {
            return Component.translatable("ruins.pioneers.service_code.title");
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("ruins.pioneers.lore.read");
    }
}
