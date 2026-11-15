package fr.pioneers.farm.animal;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.device.FertileEggItem;
import fr.pioneers.farm.device.NestBoxTile;
import fr.pioneers.farm.animal.breed.BreedAnimal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Elevage : race et sexe a l'apparition (race selon la temperature du biome), besoins des animaux domestiques (faim, soif, paturage,
 * deperissement), production (traite a minuterie, ponte, toison, mohair de l'angora), reproduction (un male et une femelle en forme), butin
 * de viande selon la race, synchro vers les clients.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class AnimalEvents {

    private AnimalEvents() {
    }

    /** Secondes entre deux traites (race a 1.0) ; repousse d'une toison. */
    static final float MILK_INTERVAL = 300, WOOL_INTERVAL = 420;
    /** Ponte : secondes entre deux oeufs (race a 1.0, bete repue) ; secondes a chercher un pondoir avant de pondre par terre. */
    static final float EGG_INTERVAL = 450;
    static final int LAY_PATIENCE = 30;
    /** Faim et soif : 100 -> 0 en 50 et 35 minutes (race a rusticite 1). */
    static final float HUNGER_PER_S = 100F / 3000F, THIRST_PER_S = 100F / 2100F;

    // ------------------------------------------------------------------ apparition

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        AnimalSpecies sp = AnimalSpecies.of(e);
        if (sp == null || event.getLevel().isClientSide) {
            return;
        }
        AnimalData d = PiAnimals.get(e);
        if (d == null) {
            return;
        }
        if (!d.init) {
            init(e, d, sp, event.getLevel());
        } else if (e instanceof BreedAnimal b) {
            d.breed = b.breed();
        }
        if (e instanceof Animal a) {
            boolean has = false;
            for (WrappedGoal g : a.goalSelector.getAvailableGoals()) {
                has |= g.getGoal() instanceof NeedGoal;
            }
            if (!has) {
                a.goalSelector.addGoal(3, new NeedGoal(a, sp));
                if (a instanceof Chicken hen) {
                    a.goalSelector.addGoal(2, new NestGoal(hen));
                }
            }
        }
    }

    /**
     * Race tiree au climat du point (temperature du lieu, saison comprise depuis la 1.0.5 : Climate) : une race dont la plage contient la temperature nait 3 fois plus souvent.
     * 1.0.3-beta lot 8 : sert aussi a convertir une bete vanilla sans race ({@link fr.pioneers.farm.animal.breed.BreedConversion}).
     */
    public static int climateBreed(Level level, net.minecraft.core.BlockPos pos, AnimalSpecies sp) {
        RandomSource r = level.random;
        float temp = fr.pioneers.farm.crop.Climate.temperature(level, pos);   //1.0.5 (lot 1) : climat du lieu, saison comprise
        BreedDef[] breeds = sp.breeds();
        int total = 0;
        int[] w = new int[breeds.length];
        for (int i = 0; i < breeds.length; i++) {
            w[i] = temp >= breeds[i].climMin() && temp <= breeds[i].climMax() ? 3 : 1;
            total += w[i];
        }
        int pick = r.nextInt(total), i = 0;
        while (pick >= w[i]) {
            pick -= w[i];
            i++;
        }
        return i;
    }

    static void init(Entity e, AnimalData d, AnimalSpecies sp, Level level) {
        RandomSource r = level.random;
        BreedDef[] breeds = sp.breeds();
        int i = e instanceof BreedAnimal b ? b.breed() : climateBreed(level, e.blockPosition(), sp);
        d.breed = i;
        d.male = r.nextBoolean();
        d.init = true;
        d.milkTimer = r.nextFloat() * MILK_INTERVAL / Math.max(0.2F, breeds[i].milk());
        if (e instanceof Sheep sheep) {
            sheep.setColor("black".equals(breeds[i].dye()) ? DyeColor.BLACK : DyeColor.WHITE);
        }
    }

    // ------------------------------------------------------------------ vie

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || (e.tickCount + e.getId()) % 20 != 0) {
            return;
        }
        AnimalSpecies sp = AnimalSpecies.of(e);
        if (sp == null) {
            return;
        }
        AnimalData d = PiAnimals.get(e);
        if (d == null || !d.init) {
            return;
        }
        second(e, d, sp);
        PiAnimals.sync(e, d, false);
    }

    private static void second(LivingEntity e, AnimalData d, AnimalSpecies sp) {
        BreedDef b = sp.breed(d.breed);
        if (d.domestic) {
            float hardy = Math.max(0.3F, b.hardy());
            d.hunger = Math.max(0, d.hunger - HUNGER_PER_S / hardy);
            d.thirst = Math.max(0, d.thirst - THIRST_PER_S / hardy);
            //Paturage : herbivores (et le kunekune) sur l'herbe
            if ((sp.grazes || "kunekune".equals(b.id())) && d.hunger < 90 && e.level().getBlockState(e.blockPosition().below()).is(Blocks.GRASS_BLOCK)) {
                d.hunger = Math.min(100, d.hunger + 0.35F);
            }
            //1.0.2-beta : a jauge vide l'animal deperit (fumee, fiche « Affame », production au plancher) mais ne perd plus de vie (avant : 2
            //points toutes les 30 s apres 4 min, jusqu'a la mort)
            if (d.hunger <= 0 || d.thirst <= 0) {
                d.starving++;
                if (e.level() instanceof ServerLevel sl && d.starving % 5 == 0) {
                    sl.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
                }
            } else {
                d.starving = 0;
            }
        }
        float p = d.production();
        boolean female = !d.male;
        if (sp.milked && female && !e.isBaby()) {
            float interval = MILK_INTERVAL / Math.max(0.2F, b.milk());
            d.milkTimer = Math.max(0, d.milkTimer - p);
            d.produce = 1 - Math.min(1, d.milkTimer / interval);
        } else if (e instanceof Chicken chicken) {
            //1.3.1 : la ponte vanilla est remplacee (pondoirs, oeufs fecondes) : minuterie du mod, puis envie de pondre (NestGoal)
            chicken.eggTime = 6000;
            if (d.male || chicken.isBaby()) {
                d.produce = 0;
                d.laying = false;
            } else {
                float interval = EGG_INTERVAL / Math.max(0.2F, b.eggs());
                if (d.layTimer <= 0 && !d.laying) {
                    d.layTimer = interval * (0.5F + e.getRandom().nextFloat());
                }
                if (!d.laying) {
                    d.layTimer -= p;
                    if (d.layTimer <= 0) {
                        d.laying = true;
                        d.layWait = 0;
                    }
                } else if (++d.layWait >= LAY_PATIENCE) {
                    layEgg(chicken, d, null);
                }
                d.produce = d.laying ? 1 : 1 - Math.min(1, d.layTimer / interval);
            }
        } else if (e instanceof Sheep sheep) {
            float interval = WOOL_INTERVAL / Math.max(0.2F, b.wool());
            if (sheep.isSheared() && !sheep.isBaby()) {
                d.woolTimer = Math.max(0, d.woolTimer - p);
                if (d.woolTimer <= 0) {
                    sheep.setSheared(false);
                }
                d.produce = 1 - Math.min(1, d.woolTimer / interval);
            } else {
                d.woolTimer = interval;
                d.produce = 1;
            }
        } else if (sp == AnimalSpecies.GOAT && b.wool() > 1) {
            float interval = WOOL_INTERVAL / b.wool();
            d.woolTimer = Math.max(0, d.woolTimer - p);
            d.produce = 1 - Math.min(1, d.woolTimer / interval);
        } else {
            d.produce = 0;
        }
    }

    /**
     * La poule pond : oeuf feconde (race de la mere) si un coq adulte est a 8 blocs (70 %), sinon un oeuf ; dans le pondoir s'il y a de la
     * place, sinon par terre.
     */
    public static void layEgg(Chicken hen, AnimalData d, @Nullable NestBoxTile nest) {
        boolean rooster = false;
        for (Chicken c : hen.level().getEntitiesOfClass(Chicken.class, hen.getBoundingBox().inflate(8))) {
            AnimalData dc = c == hen ? null : PiAnimals.get(c);
            if (dc != null && dc.male && !c.isBaby()) {
                rooster = true;
                break;
            }
        }
        ItemStack egg = rooster && hen.getRandom().nextFloat() < 0.7F ? FertileEggItem.of(d.breed) : new ItemStack(Items.EGG);
        if (nest == null || !nest.lay(egg)) {
            hen.playSound(SoundEvents.CHICKEN_EGG, 1F, (hen.getRandom().nextFloat() - hen.getRandom().nextFloat()) * 0.2F + 1F);
            hen.spawnAtLocation(egg);
        }
        hen.gameEvent(GameEvent.ENTITY_PLACE);
        BreedDef b = AnimalSpecies.CHICKEN.breed(d.breed);
        d.laying = false;
        d.layWait = 0;
        d.layTimer = EGG_INTERVAL / Math.max(0.2F, b.eggs()) * (0.75F + hen.getRandom().nextFloat() * 0.5F);
        d.produce = 0;
    }

    // ------------------------------------------------------------------ reproduction

    @SubscribeEvent
    public static void baby(BabyEntitySpawnEvent event) {
        Mob a = event.getParentA(), b = event.getParentB();
        if (AnimalSpecies.of(a) == null) {
            return;
        }
        AnimalData da = PiAnimals.get(a), db = PiAnimals.get(b);
        if (da == null || db == null) {
            return;
        }
        Player player = event.getCausedByPlayer();
        if (da.male == db.male || da.weak() || db.weak()) {
            event.setCanceled(true);
            if (a.level() instanceof ServerLevel sl) {
                for (Mob m : new Mob[]{a, b}) {
                    sl.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 6, 0.3, 0.2, 0.3, 0.01);
                }
            }
            if (player != null) {
                player.displayClientMessage(Component.translatable(da.male == db.male ? "farm.pioneers.animal.same_sex" : "farm.pioneers.animal.too_weak")
                      .withStyle(ChatFormatting.GOLD), true);
            }
            return;
        }
        if (event.getChild() != null) {
            AnimalData dc = PiAnimals.get(event.getChild());
            if (dc != null) {
                dc.init = true;
                //1.0.3-beta lot 8 : la race du petit est celle de son type (tiree a 50 / 50 entre les parents par PiBreeds.offspring)
                dc.breed = event.getChild() instanceof BreedAnimal cb ? cb.breed() : a.getRandom().nextBoolean() ? da.breed : db.breed;
                dc.male = a.getRandom().nextBoolean();
                dc.domestic = true;
                dc.hunger = dc.thirst = 100;
                if (event.getChild() instanceof Sheep sheep) {
                    sheep.setColor("black".equals(AnimalSpecies.SHEEP.breed(dc.breed).dye()) ? DyeColor.BLACK : DyeColor.WHITE);
                }
            }
        }
        //Les parents ont depense : faim
        da.hunger = Math.max(0, da.hunger - 15);
        db.hunger = Math.max(0, db.hunger - 15);
    }

    // ------------------------------------------------------------------ interactions

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        AnimalSpecies sp = AnimalSpecies.of(target);
        if (sp == null || !(target instanceof Animal animal)) {
            return;
        }
        AnimalData d = PiAnimals.get(target);
        if (d == null) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        boolean server = !event.getLevel().isClientSide;
        BreedDef b = sp.breed(d.breed);
        //Traite : femelles adultes, lait pret
        if (held.is(Items.BUCKET) && sp.milked && !animal.isBaby()) {
            if (d.male) {
                deny(event, player, Component.translatable("farm.pioneers.animal.no_male_milk"));
                return;
            }
            if (d.produce < 0.999F) {
                int left = server ? Math.round(d.milkTimer) : Math.round((1 - d.produce) * MILK_INTERVAL / Math.max(0.2F, b.milk()));
                deny(event, player, Component.translatable("farm.pioneers.animal.not_ready", left));
                return;
            }
            if (server) {
                //1.9 (MAJ RP) : Elevage = la bete est prete plus tot (soins plus rapides), XP de soins
                d.milkTimer = MILK_INTERVAL / Math.max(0.2F, b.milk()) * (float) (1 - care(player));
                d.produce = 0;
                PiAnimals.sync(target, d, true);
                cared(player, animal);
            }
            return;
        }
        //Mohair de la chevre angora
        if (held.is(Items.SHEARS) && sp == AnimalSpecies.GOAT && b.wool() > 1 && !animal.isBaby()) {
            if (d.produce < 0.999F) {
                deny(event, player, Component.translatable("farm.pioneers.animal.shorn"));
                return;
            }
            if (server) {
                //1.0.2-beta : mohair selon la production (au moins une laine)
                float prod = d.production();
                int n = Math.max(1, Math.round((1 + animal.getRandom().nextInt(2) + (animal.getRandom().nextFloat() < b.wool() - 1 ? 1 : 0)) * prod));
                for (int i = 0; i < n; i++) {
                    ItemEntity it = animal.spawnAtLocation(new ItemStack(Blocks.WHITE_WOOL), 1);
                    if (it != null) {
                        it.setDeltaMovement(it.getDeltaMovement().add((animal.getRandom().nextFloat() - 0.5) * 0.1, animal.getRandom().nextFloat() * 0.05,
                              (animal.getRandom().nextFloat() - 0.5) * 0.1));
                    }
                }
                animal.level().playSound(null, animal, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1, 1);
                held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(event.getHand()));
                d.woolTimer = WOOL_INTERVAL / b.wool() * (float) (1 - care(player));
                d.produce = 0;
                PiAnimals.sync(target, d, true);
                cared(player, animal);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(!server));
            return;
        }
        //Tonte : toison plus fournie selon la race
        if (held.is(Items.SHEARS) && target instanceof Sheep sheep && sheep.readyForShearing() && server) {
            //1.0.2-beta : toison de la race selon la production (bete affamee : guere plus que la toison de base)
            float extra = (b.wool() - 1) * 2 * d.production();
            int n = (int) extra + (sheep.getRandom().nextFloat() < extra - (int) extra ? 1 : 0);
            for (int i = 0; i < n; i++) {
                sheep.spawnAtLocation(BuiltInRegistries.ITEM.get(new ResourceLocation(
                      sheep.getColor().getName() + "_wool")), 1);
            }
            d.woolTimer = WOOL_INTERVAL / Math.max(0.2F, b.wool()) * (float) (1 - care(player));
            cared(player, animal);
            return;
        }
        int v = sp.feedValue(held);
        //Aliments du mod que la bete vanilla ne reconnait pas (graines du mod pour les poules, orge, aliment compose...) : meme effet
        if (v > 0 && !animal.isFood(held)) {
            if (server) {
                d.domestic = true;
                d.hunger = Math.min(100, d.hunger + (int) Math.round(v * (1 + care(player))));
                cared(player, animal);
                if (animal.isBaby()) {
                    animal.ageUp(Animal.getSpeedUpSecondsWhenFeeding(-animal.getAge()), true);
                } else if (animal.getAge() == 0 && animal.canFallInLove()) {
                    animal.setInLove(player);
                }
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                animal.playAmbientSound();
                PiAnimals.sync(target, d, true);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(!server));
            return;
        }
        if (!server) {
            return;
        }
        //Nourri a la main ou mene en laisse : domestique
        if (v > 0 || animal.isFood(held)) {
            d.domestic = true;
            d.hunger = Math.min(100, d.hunger + (int) Math.round(Math.max(v, 10) * (1 + care(player))));
            PiAnimals.sync(target, d, true);
            cared(player, animal);
        } else if (held.is(Items.LEAD)) {
            d.domestic = true;
            PiAnimals.sync(target, d, true);
        }
    }

    /** 1.9 (MAJ RP) : soins plus rapides (skill Elevage, jusqu'a 25 %). */
    private static double care(Player player) {
        return Math.max(0, Math.min(0.5, fr.pioneers.core.profile.PioneerApi.modifier(player, "husbandry.care_speed")));
    }

    /** XP de soins (une fois par bete et par jour : dedoublonnage a la position). */
    private static void cared(Player player, Animal animal) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            fr.pioneers.core.profile.PioneerApi.gain(sp, "husbandry.care", 1, animal.blockPosition());
        }
    }

    private static void deny(PlayerInteractEvent.EntityInteract event, Player player, Component message) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (!event.getLevel().isClientSide) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.GOLD), true);
        }
    }

    // ------------------------------------------------------------------ butin, synchro

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        AnimalSpecies sp = AnimalSpecies.of(e);
        AnimalData d = sp == null ? null : PiAnimals.get(e);
        if (d == null || !d.init) {
            return;
        }
        BreedDef b = sp.breed(d.breed);
        //1.0.2-beta : viande proportionnelle a la production (un animal affame en donne tres peu, au moins un morceau)
        float f = b.meat() * (d.male ? 1.1F : 1F) * (d.domestic ? d.production() : 1F);
        for (ItemEntity it : event.getDrops()) {
            ItemStack s = it.getItem();
            if (s.is(Items.BEEF) || s.is(Items.PORKCHOP) || s.is(Items.MUTTON) || s.is(Items.CHICKEN) || s.is(Items.COOKED_BEEF)
                || s.is(Items.COOKED_PORKCHOP) || s.is(Items.COOKED_MUTTON) || s.is(Items.COOKED_CHICKEN)) {
                float n = s.getCount() * f;
                int c = (int) n + (e.getRandom().nextFloat() < n - (int) n ? 1 : 0);
                s.setCount(Math.max(1, c));
            }
        }
    }

    @SubscribeEvent
    public static void track(PlayerEvent.StartTracking event) {
        Entity e = event.getTarget();
        if (AnimalSpecies.of(e) != null && event.getEntity() instanceof ServerPlayer sp) {
            AnimalData d = PiAnimals.get(e);
            if (d != null && d.init) {
                PiAnimals.sendTo(sp, e, d);
            }
        }
    }
}
