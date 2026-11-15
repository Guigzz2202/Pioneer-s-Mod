package fr.pioneers.farm.animal;

import fr.pioneers.Pioneers;
import fr.pioneers.core.network.PiNetwork;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Capacite des animaux d'elevage ({@link AnimalData}) : enregistrement, attache aux 5 especes, lecture, synchro vers les clients. */
public final class PiAnimals {

    private PiAnimals() {
    }

    public static final Capability<AnimalData> CAP = CapabilityManager.get(new CapabilityToken<>() {
    });

    @Nullable
    public static AnimalData get(Entity entity) {
        return entity.getCapability(CAP).resolve().orElse(null);
    }

    /** Envoie l'etat aux joueurs qui voient l'animal s'il a change (ou toujours si {@code force}). */
    public static void sync(Entity e, AnimalData d, boolean force) {
        int h = Math.round(d.hunger), t = Math.round(d.thirst), p = Math.round(d.produce * 100), f = d.flags();
        d.sinceSync++;
        if (!force && Math.abs(h - d.sentHunger) < 2 && Math.abs(t - d.sentThirst) < 2 && Math.abs(p - d.sentProduce) < 3 && f == d.sentFlags
            && d.sinceSync < 30) {
            return;
        }
        d.sentHunger = h;
        d.sentThirst = t;
        d.sentProduce = p;
        d.sentFlags = f;
        d.sinceSync = 0;
        PiNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> e), PacketAnimal.of(e, d));
    }

    public static void sendTo(ServerPlayer player, Entity e, AnimalData d) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), PacketAnimal.of(e, d));
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void register(RegisterCapabilitiesEvent event) {
            event.register(AnimalData.class);
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID)
    public static final class ForgeBus {

        private ForgeBus() {
        }

        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (AnimalSpecies.of(event.getObject()) != null) {
                event.addCapability(Pioneers.rl("animal"), new Provider());
            }
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final AnimalData data = new AnimalData();
        private final LazyOptional<AnimalData> opt = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? opt.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.deserializeNBT(tag);
        }
    }
}
