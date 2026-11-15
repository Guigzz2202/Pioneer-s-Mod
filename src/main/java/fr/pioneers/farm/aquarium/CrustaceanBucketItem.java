package fr.pioneers.farm.aquarium;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.crustacean.Crustacean;
import fr.pioneers.farm.crustacean.GeneratedCrustaceans;
import fr.pioneers.farm.crustacean.PiCrustaceans;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 28) : SEAU DE CRUSTACE (seau d'eau sur un crustace vivant : {@code Crustacean.mobInteract}) : espece et taille en NBT
 * ({@code Species}, {@code Size}, comme le seau de poisson) ; se vide dans un aquarium ({@link Aquariums}) ou dans le monde (le crustace
 * revient : crustace aquatique ou crabe de rivage selon l'espece).
 */
public class CrustaceanBucketItem extends MobBucketItem {

    public CrustaceanBucketItem(Properties properties) {
        super(PiCrustaceans.AQUATIC, () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH, properties);
    }

    @Override
    public void checkExtraContent(@Nullable Player player, @NotNull Level level, @NotNull ItemStack stack, @NotNull BlockPos pos) {
        if (level instanceof ServerLevel server) {
            CompoundTag t = stack.getTag();
            int sp = Mth.clamp(t != null ? t.getInt("Species") : 0, 0, GeneratedCrustaceans.SPECIES.length - 1);
            var type = GeneratedCrustaceans.SPECIES[sp].aquatic() ? PiCrustaceans.AQUATIC.get() : PiCrustaceans.SHORE.get();
            Crustacean c = type.create(server);
            if (c != null) {
                c.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, server.random.nextFloat() * 360F, 0F);
                c.set(sp, t != null && t.contains("Size") ? t.getFloat("Size") : 1F);
                c.setPersistenceRequired();
                server.addFreshEntity(c);
                level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
            }
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        CompoundTag t = stack.getTag();
        int sp = Mth.clamp(t != null ? t.getInt("Species") : 0, 0, GeneratedCrustaceans.SPECIES.length - 1);
        tips.desc("item.pioneers.crustacean_bucket.desc");
    }

    /** Nom avec l'espece (« Seau de crustace : Homard ») : cle {@code item.pioneers.crustacean_bucket.named}, nom de {@code farm.pioneers.crustacean.<id>}. */
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        CompoundTag t = stack.getTag();
        int sp = Mth.clamp(t != null ? t.getInt("Species") : 0, 0, GeneratedCrustaceans.SPECIES.length - 1);
        return Component.translatable("item.pioneers.crustacean_bucket.named",
              Component.translatable("farm.pioneers.crustacean." + GeneratedCrustaceans.SPECIES[sp].id()));
    }
}
