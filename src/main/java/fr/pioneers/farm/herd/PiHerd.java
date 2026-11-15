package fr.pioneers.farm.herd;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.item.ItemWithDescription;
import fr.pioneers.network.PiNetwork;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.item.Item;
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
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 9, Guillaume : « ameliorer l'elevage de lapin (clapier, races...) et de chevaux (races de chevaux) ») : registre de
 * l'elevage des lapins et des chevaux. Les entites restent VANILLA (lapin, cheval, ane, mulet) ; leur race et leurs stats propres vivent
 * dans la capacite {@link HerdData} (attachee ici, synchronisee par {@link PacketHerd}) ; regles {@link HerdRules}, evenements
 * {@link HerdEvents}, clapier {@code farm/device/HutchTile}, rendus {@code client/farm/herd/*}. Donnees : {@code tools/gc/farm2/herd.py}.
 */
public final class PiHerd {

    private PiHerd() {
    }

    public static void init() {
    }

    public static final RegistryObject<Item> ANGORA_WOOL = PiCoreRegistries.ITEMS.register("angora_wool", () -> new ItemWithDescription(new Item.Properties()));

    public static final Capability<HerdData> CAP = CapabilityManager.get(new CapabilityToken<>() {
    });

    /**
     * Entites concernees : lapin, cheval, ane, mulet (pas le lama, ni les chevaux squelette et zombie) ; 1.0.4-beta (lot 10) : chameau (de
     * Bactriane) et dromadaire, pour leur charge de bat.
     */
    public static boolean concerns(Entity e) {
        //1.0.4-beta (lot 28) : le lama aussi (races), pas celui du marchand ambulant
        return e instanceof Rabbit || e instanceof Horse || e instanceof Donkey || e instanceof Mule || e instanceof Camel
               || e instanceof net.minecraft.world.entity.animal.horse.Llama && !(e instanceof net.minecraft.world.entity.animal.horse.TraderLlama);
    }

    @Nullable
    public static HerdData get(Entity entity) {
        return entity.getCapability(CAP).resolve().orElse(null);
    }

    /** Race connue d'un lapin (null : pas de donnees ou pas encore de race, ex. le lapin tueur). */
    @Nullable
    public static GeneratedHerd.RabbitBreed rabbitBreed(Entity e) {
        HerdData d = get(e);
        return d == null || d.kind != HerdData.RABBIT || d.breed < 0 ? null : d.rabbit();
    }

    @Nullable
    public static GeneratedHerd.HorseBreed horseBreed(Entity e) {
        HerdData d = get(e);
        return d == null || d.kind != HerdData.HORSE || d.breed < 0 ? null : d.horse();
    }

    /** Force de trait (kg) d'un cheval, d'un ane ou d'un mulet (0 : inconnu) : pour les caravanes (lot 10). */
    public static int draftKg(Entity e) {
        HerdData d = get(e);
        return d == null || d.kind != HerdData.HORSE ? 0 : d.draft;
    }

    /** Charge de bat (kg) : pour les caravanes (lot 10). */
    public static int packKg(Entity e) {
        HerdData d = get(e);
        return d == null || d.kind != HerdData.HORSE ? 0 : d.pack;
    }

    public static void sync(Entity e) {
        HerdData d = get(e);
        if (d != null && !e.level().isClientSide) {
            PiNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> e), PacketHerd.of(e, d));
        }
    }

    public static void sendTo(ServerPlayer player, Entity e, HerdData d) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), PacketHerd.of(e, d));
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void register(RegisterCapabilitiesEvent event) {
            event.register(HerdData.class);
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID)
    public static final class ForgeBus {

        private ForgeBus() {
        }

        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (concerns(event.getObject())) {
                event.addCapability(Pioneers.rl("herd"), new Provider(event.getObject() instanceof Rabbit ? HerdData.RABBIT : HerdData.HORSE));
            }
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final HerdData data = new HerdData();
        private final LazyOptional<HerdData> opt = LazyOptional.of(() -> data);

        Provider(int kind) {
            data.kind = kind;
        }

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
