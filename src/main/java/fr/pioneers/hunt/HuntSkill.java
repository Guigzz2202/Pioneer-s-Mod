package fr.pioneers.hunt;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.fauna.FaunaGroup;
import fr.pioneers.fauna.FaunaRegistry;
import fr.pioneers.fauna.FaunaSpecies;
import fr.pioneers.pet.PiPets;
import fr.pioneers.wild.Bear;
import fr.pioneers.wild.Bison;
import fr.pioneers.wild.Boar;
import fr.pioneers.wild.Deer;
import fr.pioneers.wild.Elephant;
import fr.pioneers.wild.PiWild8;
import fr.pioneers.wild.Rhino;
import fr.pioneers.wild.Shark;
import fr.pioneers.wild.Whale;
import fr.pioneers.wild.Wild9Rules;
import fr.pioneers.wild.WildAnimal;
import fr.pioneers.wild.WildRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 10, specification §6.2, decision 10) : la COMPETENCE CHASSE, 17e du Carnet (donnees : T:rp/pioneer_data.py, cle
 * {@code hunting}) :
 * <ul>
 *   <li>XP : gibier sauvage abattu ({@code hunting.kill}, x 2 de loin a {@link HuntRules#FAR_SHOT} blocs, x 2 pour une bete dangereuse),
 *   troupeau trouve ({@code hunting.herd}, {@link HerdEvents}), DEPECAGE = chaque peau ou cuir tire du butin d'une bete abattue
 *   ({@code hunting.skin}) ; la Regle de la Main, la fatigue et les plafonds s'appliquent comme a tout gain ({@link PioneerApi#gain}) ;</li>
 *   <li>effets (progressifs, valeurs au niveau 10) : {@code hunting.yield} +30 % de viande et de cuir sur le gibier, {@code hunting.trophy}
 *   +100 % de chance de trophee (5 % -> 10 %), {@code hunting.reload} -25 % de rechargement des armes a feu, {@code hunting.spread} -30 % de
 *   dispersion, {@code hunting.scent} +50 % de flair du chien ({@code HuntEvents.scentBonus}).</li>
 * </ul>
 * Gibier = betes sauvages du mod ({@link WildAnimal}), sauf les nuisibles (rats : {@code pioneers:cat_prey}), le singe
 * ({@code pioneers:not_game}), les petits, les betes apprivoisees ou nommees.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class HuntSkill {

    private HuntSkill() {
    }

    public static final TagKey<Item> RAW_MEAT = TagKey.create(Registries.ITEM, Pioneers.rl("food/raw_meat"));
    public static final TagKey<Item> LEATHER = TagKey.create(Registries.ITEM, new net.minecraft.resources.ResourceLocation("forge", "leather"));

    /** GameTests : chance de trophee forcee (null = la vraie). */
    public static volatile Double forcedTrophy;

    // ------------------------------------------------------------------ effets

    /** Bonus de flair du maitre (crochet du chien). */
    public static double scentBonus(Player master) {
        return Math.max(0, PioneerApi.modifier(master, "hunting.scent"));
    }

    public static double yieldBonus(Player p) {
        return Math.max(0, PioneerApi.modifier(p, "hunting.yield"));
    }

    public static double trophyBonus(Player p) {
        return Math.max(0, PioneerApi.modifier(p, "hunting.trophy"));
    }

    /** Bonus de rechargement (negatif : plus rapide). */
    public static double reloadBonus(Player p) {
        return Math.min(0, PioneerApi.modifier(p, "hunting.reload"));
    }

    /** Bonus de dispersion (negatif : plus precis). */
    public static double spreadBonus(Player p) {
        return Math.min(0, PioneerApi.modifier(p, "hunting.spread"));
    }

    // ------------------------------------------------------------------ gibier

    /** Gibier au sens de la competence : bete sauvage du mod adulte, ni nuisible, ni singe, ni apprivoisee, ni nommee. */
    public static boolean game(LivingEntity e) {
        return e instanceof WildAnimal w && !w.isBaby() && !w.hasCustomName() && !e.getType().is(PiPets.CAT_PREY)
               && !e.getType().is(PiWild8.NOT_GAME);
    }

    /** Bete dangereuse : predateur, ou bete qui charge ou defend (sanglier, bison, ours, rhinoceros, elephant, cachalot, orque). */
    public static boolean dangerous(LivingEntity e) {
        FaunaSpecies s = FaunaRegistry.of(e);
        if (s != null && s.group == FaunaGroup.PREDATOR) {
            return true;
        }
        return e instanceof Boar || e instanceof Bison || e instanceof Bear || e instanceof Rhino || e instanceof Elephant
               || e instanceof Whale w && (w.kind == Wild9Rules.SPERM_WHALE || w.kind == Wild9Rules.ORCA);
    }

    @Nullable
    static ServerPlayer hunter(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer p) {
            return p;
        }
        return victim.getLastHurtByMob() instanceof ServerPlayer p ? p : null;
    }

    @SubscribeEvent
    public static void killed(LivingDeathEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide || !game(v)) {
            return;
        }
        ServerPlayer p = hunter(v, event.getSource());
        if (p == null) {
            return;
        }
        double factor = HuntRules.killXp(1, p.distanceTo(v), dangerous(v));
        PioneerApi.gain(p, "hunting.kill", factor, null);
    }

    /** Butin du gibier : viande et cuir avec le bonus de la competence, XP de depecage, trophee. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void drops(LivingDropsEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide || !game(v)) {
            return;
        }
        ServerPlayer p = hunter(v, event.getSource());
        if (p == null) {
            return;
        }
        double bonus = yieldBonus(p);
        int hides = 0;
        for (ItemEntity ie : event.getDrops()) {
            ItemStack st = ie.getItem();
            boolean meat = st.is(RAW_MEAT), hide = st.is(Items.LEATHER) || st.is(Items.RABBIT_HIDE) || st.is(LEATHER);
            if (hide) {
                hides += st.getCount();
            }
            if ((meat || hide) && bonus > 0) {
                ItemStack n = st.copy();
                n.setCount(Math.min(n.getMaxStackSize(), HuntRules.yield(st.getCount(), bonus, v.getRandom().nextDouble())));
                ie.setItem(n);
            }
        }
        if (hides > 0) {
            PioneerApi.gain(p, "hunting.skin", hides, null);
        }
        Item trophy = trophyOf(v);
        double chance = forcedTrophy != null ? forcedTrophy : HuntRules.trophyChance(trophyBonus(p));
        if (trophy != null && v.getRandom().nextDouble() < chance) {
            event.getDrops().add(new ItemEntity(v.level(), v.getX(), v.getY() + 0.5, v.getZ(), new ItemStack(trophy)));
        }
    }

    /**
     * Trophee d'une bete : cerf MALE portant ses bois (pas la biche, ni un cerf qui les a perdus), ours adulte, requin (blanc, tigre, marteau,
     * gris ; le requin-baleine, trop grand, n'en donne pas). Null sinon.
     */
    @Nullable
    public static Item trophyOf(LivingEntity v) {
        if (v instanceof Deer d && d.sex() == WildRules.MALE && d.antlers() > 0.5F) {
            return PiHunt.DEER_TROPHY.get().asItem();
        }
        if (v instanceof Bear) {
            return PiHunt.BEAR_TROPHY.get().asItem();
        }
        if (v instanceof Shark s && s.kind != Wild9Rules.WHALE_SHARK) {
            return PiHunt.SHARK_TROPHY.get().asItem();
        }
        return null;
    }
}
