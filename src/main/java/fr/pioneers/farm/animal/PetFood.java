package fr.pioneers.farm.animal;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.fish.FishItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.4-beta (lot 4, Guillaume : « Fixer l'attirance des poules pour les graines (n'importe laquelle) de meme pour les chats qui ne veulent pas
 * des poissons ») : la poule vanilla ne suit que six graines vanilla, le chat et l'ocelot que la morue et le saumon.
 * <ul>
 *   <li>Poules : un {@link TemptGoal} de plus (priorite 3 comme le vanilla) sur le tag genere {@code pioneers:seeds/all} (#forge:seeds, graines
 *   vanilla, graines des astres) ; nourrir et faire pondre : {@code PiChicken.isFood} et {@code AnimalSpecies.feedValue}.</li>
 *   <li>Chats et ocelots : un {@link TemptGoal} sur {@code pioneers:cat_fish} (tous les poissons crus du mod) avec les conditions du vanilla
 *   (chat non apprivoise, ocelot sans confiance) ; un clic droit avec un poisson du mod rejoue EXACTEMENT le geste vanilla avec une morue
 *   ({@link #feedAsCod} : apprivoiser 1 chance sur 3, soigner, faire se reproduire, gagner la confiance de l'ocelot), le poisson du mod
 *   etant consomme a la place de la morue.</li>
 * </ul>
 * GameTests : J:gametest/PatchP104Lot4Tests.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PetFood {

    public static final TagKey<Item> ALL_SEEDS = TagKey.create(Registries.ITEM, Pioneers.rl("seeds/all"));
    public static final TagKey<Item> CAT_FISH = TagKey.create(Registries.ITEM, Pioneers.rl("cat_fish"));

    private PetFood() {
    }

    /** Toute graine (vanilla, du mod, des astres). */
    public static boolean seed(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ALL_SEEDS);
    }

    /** Poisson cru du mod (pas la morue ni le saumon vanilla, que le vanilla gere). */
    public static boolean catFish(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof FishItem || stack.is(CAT_FISH));
    }

    /** Attirance par les graines (poules). */
    public static final class SeedTemptGoal extends TemptGoal {

        public SeedTemptGoal(Chicken chicken) {
            super(chicken, 1.0, Ingredient.of(ALL_SEEDS), false);
        }
    }

    /** Attirance par les poissons du mod (chat non apprivoise, ocelot sans confiance : memes conditions que le vanilla). */
    public static final class FishTemptGoal extends TemptGoal {

        private final Entity animal;

        public FishTemptGoal(Cat cat) {
            super(cat, 0.6, Ingredient.of(CAT_FISH), true);
            this.animal = cat;
        }

        public FishTemptGoal(Ocelot ocelot) {
            super(ocelot, 0.6, Ingredient.of(CAT_FISH), true);
            this.animal = ocelot;
        }

        @Override
        public boolean canUse() {
            if (animal instanceof Cat cat && cat.isTame()) {
                return false;
            }
            return super.canUse() && !(animal instanceof Ocelot o && trusting(o));
        }

        @Override
        protected boolean canScare() {
            return super.canScare() && !(animal instanceof Ocelot o && trusting(o));
        }
    }

    /** Confiance de l'ocelot (accesseur vanilla non public : lue dans sa sauvegarde, seulement quand un joueur le tente). */
    static boolean trusting(Ocelot ocelot) {
        CompoundTag t = new CompoundTag();
        ocelot.addAdditionalSaveData(t);
        return t.getBoolean("Trusting");
    }

    /** Les buts sont-ils deja poses (entite rechargee) ? */
    private static boolean has(Mob mob, Class<?> goal) {
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(w -> goal.isInstance(w.getGoal()));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        addGoals(event.getEntity());
    }

    /** Pose les buts d'attirance (aussi appele par les GameTests). */
    public static void addGoals(Entity entity) {
        if (entity instanceof Chicken chicken && !has(chicken, SeedTemptGoal.class)) {
            chicken.goalSelector.addGoal(3, new SeedTemptGoal(chicken));
        } else if (entity instanceof Cat cat && !has(cat, FishTemptGoal.class)) {
            cat.goalSelector.addGoal(3, new FishTemptGoal(cat));
        } else if (entity instanceof Ocelot ocelot && !has(ocelot, FishTemptGoal.class)) {
            ocelot.goalSelector.addGoal(3, new FishTemptGoal(ocelot));
        }
    }

    /** Un poisson du mod donne a un chat ou a un ocelot : le geste vanilla de la morue. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!(target instanceof Cat || target instanceof Ocelot) || !catFish(event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        if (event.getLevel().isClientSide) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        event.setCancellationResult(feedAsCod(target, event.getEntity(), event.getHand()));
    }

    /**
     * Rejoue {@code target.interact} avec une morue a la place du poisson tenu (meme nombre) ; le poisson du mod perd ce que la morue a perdu.
     * Serveur seulement.
     */
    public static InteractionResult feedAsCod(Entity target, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        int count = held.getCount();
        player.setItemInHand(hand, new ItemStack(Items.COD, count));
        InteractionResult result;
        try {
            result = target.interact(player, hand);
        } finally {
            ItemStack after = player.getItemInHand(hand);
            int left = after.is(Items.COD) ? after.getCount() : after.isEmpty() ? 0 : count;
            ItemStack back = held.copyWithCount(Math.min(count, left));
            player.setItemInHand(hand, back.isEmpty() ? ItemStack.EMPTY : back);
            if (!after.isEmpty() && !after.is(Items.COD)) {
                //Le geste a rendu autre chose (jamais pour un chat) : on le donne au joueur
                if (!player.getInventory().add(after)) {
                    player.drop(after, false);
                }
            }
        }
        return result;
    }
}
