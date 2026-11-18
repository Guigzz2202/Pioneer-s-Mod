package fr.pioneers.equipment.cosmetic;

import fr.pioneers.Pioneers;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 1.12.0 (lot E4) : capacite {@link Wardrobe} des joueurs (attachee des deux cotes : le client garde la garde-robe des joueurs qu'il voit,
 * recue par {@link CosmeticNet}). Copiee ENTIERE a la mort comme au retour de l'End : les cosmetiques ne tombent jamais, quel que soit
 * {@code keepInventory}.
 */
public final class CosmeticCap {

    public static final Capability<Wardrobe> CAP = CapabilityManager.get(new CapabilityToken<>() {
    });
    private CosmeticCap() {
    }

    /**
     * Garde-robe du joueur. 1.0.4-beta (lot 8) : un joueur mort est retire du monde (capacites invalidees) avant sa reapparition : on relit
     * sa vraie garde-robe (comme {@code PlayerEvent.Clone}) au lieu d'une garde-robe vide PARTAGEE ; sans capacite du tout, une garde-robe
     * jetable (jamais partagee).
     */
    public static Wardrobe get(Player player) {
        Wardrobe w = player.getCapability(CAP).resolve().orElse(null);
        if (w == null && player.isRemoved()) {
            player.reviveCaps();
            try {
                w = player.getCapability(CAP).resolve().orElse(null);
            } finally {
                player.invalidateCaps();
            }
        }
        return w != null ? w : new Wardrobe();
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void register(RegisterCapabilitiesEvent event) {
            event.register(Wardrobe.class);
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID)
    public static final class ForgeBus {

        private ForgeBus() {
        }

        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(Pioneers.rl("wardrobe"), new Provider());
            }
        }

        @SubscribeEvent
        public static void clone(PlayerEvent.Clone event) {
            Player old = event.getOriginal();
            old.reviveCaps();
            Wardrobe from = old.getCapability(CAP).resolve().orElse(null);
            Wardrobe to = event.getEntity().getCapability(CAP).resolve().orElse(null);
            if (from != null && to != null) {
                to.copyFrom(from);
            }
            old.invalidateCaps();
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final Wardrobe data = new Wardrobe();
        private final LazyOptional<Wardrobe> opt = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? opt.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.load(tag);
        }
    }
}
