package fr.pioneers.client.pet;

import fr.pioneers.Pioneers;
import fr.pioneers.client.farm.FarmHud;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.pet.Pet;
import fr.pioneers.pet.PiPets;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 1.0.5-beta (lot 5) : client des chiens et des chats : couches des silhouettes ({@link PetModel#layer}), rendus (loup vanilla = chien
 * sauvage, chat vanilla, races du mod), interface ({@link PetScreen}), fiche au regard (race, description, mode, rappel de Maj + clic droit).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PetClient {

    private PetClient() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (int i = 0; i < GeneratedPetShapes.DOGS.length; i++) {
            GeneratedPetShapes.Shape s = GeneratedPetShapes.DOGS[i];
            event.registerLayerDefinition(PetRenderer.DOG_LAYERS[i], () -> PetModel.layer(s));
        }
        for (int i = 0; i < GeneratedPetShapes.CATS.length; i++) {
            GeneratedPetShapes.Shape s = GeneratedPetShapes.CATS[i];
            event.registerLayerDefinition(PetRenderer.CAT_LAYERS[i], () -> PetModel.layer(s));
        }
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.WOLF, ctx -> new PetRenderer<Wolf>(ctx, false));
        event.registerEntityRenderer(EntityType.CAT, ctx -> new PetRenderer<Cat>(ctx, true));
        for (var t : PiPets.DOGS) {
            event.registerEntityRenderer(t.get(), ctx -> new PetRenderer<>(ctx, false));
        }
        for (var t : PiPets.CATS) {
            event.registerEntityRenderer(t.get(), ctx -> new PetRenderer<>(ctx, true));
        }
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(PiPets.MENU.get(), PetScreen::new);
            FarmHud.ENTITY_PROVIDERS.add(PetClient::card);
        });
    }

    /** Fiche au regard d'un chien ou d'un chat de race ; du chien sauvage. */
    public static FarmCard card(Entity e, boolean sneaking) {
        if (e instanceof Pet p) {
            SpawnEggItem egg = SpawnEggItem.byId(e.getType());
            FarmCard c = FarmCard.of(egg == null ? ItemStack.EMPTY : new ItemStack(egg), Component.translatable("pet.pioneers.breed." + p.breedId()),
                  p.isCat() ? 0xC8A070 : 0xB08050);
            c.add(FarmCard.Line.text(Component.translatable("pet.pioneers.breed." + p.breedId() + ".desc"), 0xD0D0D0));
            if (p.self().isTame()) {
                c.add(FarmCard.Line.pair(Component.translatable("pet.pioneers.gui.mode"), Component.translatable(p.effectiveMode().key()), FarmCard.INFO));
                c.add(FarmCard.Line.hint(Component.translatable("pet.pioneers.card.hint")));
            }
            return c;
        }
        if (e.getType() == EntityType.WOLF && e instanceof Wolf w && !w.isTame()) {
            SpawnEggItem egg = SpawnEggItem.byId(EntityType.WOLF);
            FarmCard c = FarmCard.of(egg == null ? ItemStack.EMPTY : new ItemStack(egg), Component.translatable("pet.pioneers.breed.wild"), 0x7A6A56);
            c.add(FarmCard.Line.text(Component.translatable("pet.pioneers.breed.wild.desc"), 0xD0D0D0));
            return c;
        }
        return null;
    }
}
