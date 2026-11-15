package fr.pioneers.village;

import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerDefs;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.ProfileSync;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.ruins.LoreIndex;
import fr.pioneers.ruins.RuinEvents;
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
 * 1.9 (MAJ RP, bloc 3) : manuel d'un Corps de Metier (vendu par le Bibliothecaire, service Archives ; 200 jetons). A la premiere lecture, il
 * range l'entree du chapitre 12 dans le Codex et donne une fois de l'XP dans le premier skill de la vocation : jamais plus d'un niveau
 * (la seule facon d'acheter de l'XP). Le livre reste : on peut le relire.
 */
public class ManualItem extends Item {

    private final String vocation;

    public ManualItem(String vocation, Properties properties) {
        super(properties);
        this.vocation = vocation;
    }

    public String vocation() {
        return vocation;
    }

    public String entry() {
        return "trade_" + vocation;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            PioneerProfile p = PioneerCap.get(sp);
            if (p.loreRead.add(entry())) {
                p.touch();
                MinecraftForge.EVENT_BUS.post(new RuinEvents.LoreRead(sp, entry(), LoreIndex.tier(entry())));
                sp.displayClientMessage(Component.translatable("ruins.pioneers.lore.filed").withStyle(ChatFormatting.GOLD), true);
            }
            if (p.marks.add("manual:" + vocation)) {
                PioneerDefs.Vocation v = PioneerDefs.get().vocation(vocation);
                if (v != null && !v.affinities().isEmpty()) {
                    String skill = v.affinities().get(0);
                    int lvl = PioneerApi.level(sp, skill);
                    if (lvl < PioneerApi.MAX_LEVEL) {
                        //jamais plus d'un niveau, meme avec l'affinite de vocation (x1,5)
                        double need = PioneerApi.xpFor(lvl + 1) - p.xp(skill);
                        PioneerApi.gainXp(sp, skill, Math.max(1, need * 0.6), PioneerApi.XpSource.HAND, "manual_" + vocation, null);
                    }
                }
            }
            ProfileSync.full(sp);
            CompoundTag t = new CompoundTag();
            t.putString("id", entry());
            RpNet.send(sp, RpActions.LORE_PAGE, t);
            level.playSound(null, sp.blockPosition(), RpSounds.PAGE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("village.pioneers.manual.tip");
    }
}
