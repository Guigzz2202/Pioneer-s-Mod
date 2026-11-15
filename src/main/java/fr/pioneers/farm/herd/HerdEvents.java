package fr.pioneers.farm.herd;

import fr.pioneers.Pioneers;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.4-beta (lot 9) : vie des lapins et des chevaux du mod. A l'arrivee dans le monde (serveur) : race au climat et stats de la race (lapin
 * tueur : aucune race, robe vanilla), le cheval prend vitesse / saut / sante dans les bornes de sa race ; le belier ne fuit plus (buts de
 * fuite retires a chaque chargement). Au croisement : race 50 / 50 et stats heritees ({@link HerdRules}). Cisailles sur un angora : laine
 * angora, puis repousse. Butin du lapin selon sa viande et sa peau. Donnees envoyees au joueur qui commence a voir l'animal.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class HerdEvents {

    private HerdEvents() {
    }

    /** Secondes de repousse de la laine d'un angora en liberte (divisees par sa laine). */
    public static final int WOOL_REGROW = 600;

    static Random random(Level level) {
        return new Random(level.random.nextLong());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void join(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (event.getLevel().isClientSide || !PiHerd.concerns(e)) {
            return;
        }
        HerdData d = PiHerd.get(e);
        if (d == null) {
            return;
        }
        if (!d.init) {
            init(e, d, event.getLevel());
        } else if (e instanceof HerdBreedEntity hb && d.breed != hb.herdBreed()) {
            //1.0.4-beta (lot 28) : la race est celle du type (entite de race)
            d.breed = hb.herdBreed();
        }
        if (e instanceof Rabbit rabbit && d.breed >= 0 && d.rabbit().calm()) {
            //Belier (calme) : plus aucune fuite (joueur, loups, monstres) ; les buts sont recrees a chaque chargement
            rabbit.goalSelector.removeAllGoals(g -> g instanceof AvoidEntityGoal<?>);
        }
    }

    /** Premiere arrivee : race et stats. */
    public static void init(Entity e, HerdData d, Level level) {
        Random r = random(level);
        float temp = fr.pioneers.farm.crop.Climate.temperature(level, e.blockPosition());   //1.0.5 (lot 1) : climat du lieu, saison comprise
        //1.0.4-beta (lot 28) : entite de race (oeuf de la race, /summon) : la race du type, stats tirees dans ses bornes
        if (e instanceof HerdBreedEntity hb) {
            int breed = hb.herdBreed();
            if (e instanceof Rabbit) {
                HerdRules.rollRabbit(d, breed, r);
            } else {
                HerdRules.rollHorse(d, breed, r);
                if (e instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h && !(e instanceof net.minecraft.world.entity.animal.horse.Llama)) {
                    applyStats(h, GeneratedHerd.HORSES[breed], r);
                }
            }
            return;
        }
        if (e instanceof Rabbit rabbit) {
            if (rabbit.getVariant() == Rabbit.Variant.EVIL) {
                d.kind = HerdData.RABBIT;
                d.breed = -1;
                d.init = true;
                return;
            }
            HerdRules.rollRabbit(d, HerdRules.climateBreed(temp, HerdRules.rabbitRanges(), r), r);
        } else if (e instanceof Horse horse) {
            int breed = HerdRules.climateBreed(temp, HerdRules.horseRanges(), r);
            HerdRules.rollHorse(d, breed, r);
            applyStats(horse, GeneratedHerd.HORSES[breed], r);
        } else if (e instanceof Donkey) {
            //1.0.4-beta (lot 28) : trois races d'anes, au climat
            HerdRules.rollHorse(d, HerdRules.climateBreed(temp, HerdRules.kindRanges(1), r), r);
        } else if (e instanceof Mule) {
            HerdRules.rollHorse(d, HerdRules.kindBreed(2), r);
        } else if (e instanceof Camel) {
            //1.0.4-beta (lot 10) : chameau de Bactriane (kind 3) ou dromadaire (kind 4) : charge de bat et force de trait
            HerdRules.rollHorse(d, HerdRules.kindBreed(e instanceof fr.pioneers.caravan.DromedaryEntity ? 4 : 3), r);
        } else if (e instanceof net.minecraft.world.entity.animal.horse.Llama) {
            //1.0.4-beta (lot 28) : lama vanilla : race au climat (il deviendra l'entite de sa race au tick suivant : HerdBreeds)
            HerdRules.rollHorse(d, HerdRules.climateBreed(temp, HerdRules.kindRanges(5), r), r);
        }
    }

    /** Vitesse, saut et sante tires dans les bornes de la race (cheval ne au monde). */
    public static void applyStats(AbstractHorse horse, GeneratedHerd.HorseBreed b, Random r) {
        set(horse, Attributes.MOVEMENT_SPEED, HerdRules.rollRange(b.speedMin(), b.speedMax(), r));
        set(horse, Attributes.JUMP_STRENGTH, HerdRules.rollRange(b.jumpMin(), b.jumpMax(), r));
        set(horse, Attributes.MAX_HEALTH, Math.round(HerdRules.rollRange(b.healthMin(), b.healthMax(), r)));
        horse.setHealth(horse.getMaxHealth());
    }

    static void set(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) {
            i.setBaseValue(v);
        }
    }

    static double base(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute a) {
        AttributeInstance i = e.getAttribute(a);
        return i == null ? 0 : i.getBaseValue();
    }

    @SubscribeEvent
    public static void baby(BabyEntitySpawnEvent event) {
        AgeableMob child = event.getChild();
        if (child == null || !PiHerd.concerns(child) || event.getParentA().level().isClientSide) {
            return;
        }
        HerdData a = PiHerd.get(event.getParentA()), b = PiHerd.get(event.getParentB()), c = PiHerd.get(child);
        if (a == null || b == null || c == null || !a.init || !b.init || a.breed < 0 || b.breed < 0) {
            return;
        }
        Random r = random(child.level());
        if (child instanceof Rabbit && a.kind == HerdData.RABBIT && b.kind == HerdData.RABBIT) {
            c.deserializeNBT(HerdRules.rabbitChild(a, b, r).serializeNBT());
            return;
        }
        if (a.kind != HerdData.HORSE || b.kind != HerdData.HORSE) {
            return;
        }
        int breed = child instanceof Horse ? HerdRules.childBreed(a.breed, b.breed, r) : child instanceof Mule ? HerdRules.kindBreed(2)
              : child instanceof fr.pioneers.caravan.DromedaryEntity ? HerdRules.kindBreed(4) : child instanceof Camel ? HerdRules.kindBreed(3)
              //1.0.4-beta (lot 28) : anes et lamas : la race d'un des parents, 50 / 50
              : child instanceof net.minecraft.world.entity.animal.horse.Llama ? HerdRules.kindChild(a.breed, b.breed, 5, r)
              : HerdRules.kindChild(a.breed, b.breed, 1, r);
        if (child instanceof Horse && GeneratedHerd.HORSES[breed].kind() != 0) {
            breed = GeneratedHerd.HORSES[a.breed].kind() == 0 ? a.breed : b.breed;
        }
        c.deserializeNBT(HerdRules.horseChild(a, b, breed, r).serializeNBT());
        if (child instanceof Horse horse) {
            LivingEntity pa = event.getParentA(), pb = event.getParentB();
            GeneratedHerd.HorseBreed h = GeneratedHerd.HORSES[breed];
            set(horse, Attributes.MOVEMENT_SPEED, HerdRules.inherit((float) base(pa, Attributes.MOVEMENT_SPEED), (float) base(pb, Attributes.MOVEMENT_SPEED),
                  h.speedMin(), h.speedMax(), r));
            set(horse, Attributes.JUMP_STRENGTH, HerdRules.inherit((float) base(pa, Attributes.JUMP_STRENGTH), (float) base(pb, Attributes.JUMP_STRENGTH),
                  h.jumpMin(), h.jumpMax(), r));
            set(horse, Attributes.MAX_HEALTH, Math.round(HerdRules.inherit((float) base(pa, Attributes.MAX_HEALTH), (float) base(pb, Attributes.MAX_HEALTH),
                  h.healthMin(), h.healthMax(), r)));
        }
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e instanceof Rabbit) || e.level().isClientSide || (e.tickCount + e.getId()) % 20 != 0) {
            return;
        }
        HerdData d = PiHerd.get(e);
        if (d != null && d.woolTimer > 0) {
            d.woolTimer = Math.max(0, d.woolTimer - 1);
            if (d.woolTimer == 0) {
                PiHerd.sync(e);
            }
        }
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Rabbit rabbit)) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());
        HerdData d = PiHerd.get(rabbit);
        if (!held.is(Items.SHEARS) || d == null || d.breed < 0 || d.wool <= 0 || rabbit.isBaby()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(rabbit.level().isClientSide));
        if (rabbit.level().isClientSide) {
            return;
        }
        if (d.woolTimer > 0) {
            player.displayClientMessage(Component.translatable("farm.pioneers.herd.shorn").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        int n = shear(d, rabbit.getRandom().nextFloat());
        ItemEntity it = rabbit.spawnAtLocation(new ItemStack(PiHerd.ANGORA_WOOL.get(), n), 0.3F);
        if (it != null) {
            it.setDeltaMovement(it.getDeltaMovement().add(0, 0.1, 0));
        }
        rabbit.level().playSound(null, rabbit, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1, 1.3F);
        held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(event.getHand()));
        PiHerd.sync(rabbit);
    }

    /** Tonte d'un angora : 1 ou 2 laines (x sa laine) ; la laine repousse en {@link #WOOL_REGROW} s / laine. */
    public static int shear(HerdData d, float roll) {
        int n = Math.max(1, Math.round(d.wool + roll));
        d.woolTimer = WOOL_REGROW / Math.max(0.2F, d.wool);
        return n;
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Rabbit rabbit) || rabbit.level().isClientSide || rabbit.isBaby()) {
            return;
        }
        HerdData d = PiHerd.get(rabbit);
        if (d == null || d.breed < 0) {
            return;
        }
        //Viande et peau selon l'individu : au-dela de 1, une chance d'un objet de plus par point (Geant : souvent 2 viandes)
        float rm = rabbit.getRandom().nextFloat(), rp = rabbit.getRandom().nextFloat();
        if (rm < d.meat - 1) {
            add(event, rabbit, new ItemStack(rabbit.isOnFire() ? Items.COOKED_RABBIT : Items.RABBIT));
        }
        if (rp < d.pelt - 1) {
            add(event, rabbit, new ItemStack(Items.RABBIT_HIDE));
        }
        if (d.wool > 0 && d.woolTimer <= 0) {
            add(event, rabbit, new ItemStack(PiHerd.ANGORA_WOOL.get()));
        }
    }

    private static void add(LivingDropsEvent event, Entity e, ItemStack s) {
        event.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY(), e.getZ(), s));
    }

    @SubscribeEvent
    public static void track(PlayerEvent.StartTracking event) {
        Entity e = event.getTarget();
        if (PiHerd.concerns(e) && event.getEntity() instanceof ServerPlayer sp) {
            HerdData d = PiHerd.get(e);
            if (d != null && d.init) {
                PiHerd.sendTo(sp, e, d);
            }
        }
    }
}
