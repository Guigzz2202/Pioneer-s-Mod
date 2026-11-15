package fr.pioneers.world.pet;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.animal.breed.BreedConversion;
import java.util.ArrayDeque;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5, specification §5.1 et §8) : le loup vanilla est le CHIEN SAUVAGE ; APPRIVOISE, il devient un chien d'une race TIREE AU
 * HASARD (regle de Guillaume) au tick serveur suivant (NBT complet : proprietaire, collier, nom, assis, sante ; meme UUID :
 * {@link BreedConversion#convertTo}). Mondes existants : tout loup apprivoise qui entre dans un monde devient ainsi un chien de race (maitre,
 * collier et nom gardes), les loups sauvages restent des chiens sauvages. Les chats vanilla (errants des villages, cabanes de sorciere,
 * oeufs) deviennent le chat de la race de leur variante ({@link PiPets#catBreedOfVariant}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PetConversion {

    private PetConversion() {
    }

    private static final ArrayDeque<Entity> PENDING = new ArrayDeque<>();
    /** Conversions faites depuis le demarrage (GameTests). */
    public static int converted;

    public static boolean needs(Entity e) {
        return e.getType() == EntityType.WOLF && ((Wolf) e).isTame() || e.getType() == EntityType.CAT;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && needs(event.getEntity())) {
            PENDING.add(event.getEntity());
        }
    }

    /** Apprivoisement d'un chien sauvage : converti des que l'apprivoisement est fait (tick suivant). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tamed(AnimalTameEvent event) {
        if (!event.isCanceled() && event.getAnimal().getType() == EntityType.WOLF && !event.getAnimal().level().isClientSide) {
            PENDING.add(event.getAnimal());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        int n = PENDING.size();
        for (int i = 0; i < n && !PENDING.isEmpty(); i++) {
            Entity e = PENDING.poll();
            if (e != null && !e.isRemoved() && e.level() instanceof ServerLevel sl && needs(e)) {
                convert(sl, e);
            }
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        PENDING.clear();
    }

    @Nullable
    public static EntityType<?> target(ServerLevel level, Entity e) {
        if (e instanceof Wolf w && w.isTame() && e.getType() == EntityType.WOLF) {
            return PiPets.dogType(level.random.nextInt(GeneratedPets.DOG_BREEDS));
        }
        if (e instanceof Cat c && e.getType() == EntityType.CAT) {
            ResourceLocation v = BuiltInRegistries.CAT_VARIANT.getKey(c.getVariant());
            return PiPets.catType(PiPets.catBreedOfVariant(v == null ? null : v.toString()));
        }
        return null;
    }

    /** Convertit maintenant ; renvoie la nouvelle bete (null si rien n'a change). */
    @Nullable
    public static Entity convert(ServerLevel level, Entity old) {
        EntityType<?> type = target(level, old);
        if (type == null) {
            return null;
        }
        boolean wolf = old instanceof Wolf;
        Entity ne = BreedConversion.convertTo(level, old, type);
        if (ne instanceof Pet p) {
            p.brain().statsDue = true;
            converted++;
            if (wolf && p.self().getOwner() instanceof Player owner) {
                owner.displayClientMessage(Component.translatable("pet.pioneers.msg.breed_revealed",
                      Component.translatable("pet.pioneers.breed." + p.breedId())).withStyle(ChatFormatting.GREEN), true);
            }
        }
        return ne;
    }
}
