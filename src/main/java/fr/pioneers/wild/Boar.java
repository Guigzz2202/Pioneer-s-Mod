package fr.pioneers.wild;

import fr.pioneers.Pioneers;
import fr.pioneers.common.PiCommonConfig;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 6, specification §4) : le SANGLIER. Il fuit le plus souvent (8 blocs), mais CHARGE si un joueur arrive a moins de 4 blocs
 * ({@link WildRules#charges}) ou s'il est blesse ; la charge dure 5 s au plus ; apres un coup porte, il s'enfuit et ne charge plus pendant
 * 4 s. Les marcassins ne chargent pas. Male (defenses, criniere) et laie ; marcassins rayes.
 * <p>
 * 1.0.5-beta (lot 13, retour de Guillaume) : les CULTURES. Pas attire par les champs, mais de temps en temps (reglage
 * {@code fauna.boarCropChance}, par minute) un adulte a FAIM : il cherche une culture a {@link WildRules#BOAR_CROP_RANGE} blocs qu'il a
 * le droit de detruire ({@link #rootable} : reglage {@code fauna.boarCropDamage}, mobGriefing, hors claim, champ non clos par le tag
 * {@code pioneers:boar_barriers}), y va, la FOUILLE ({@link WildRules#GRAZE}, groin dans la terre, particules de terre et de culture, son
 * de racines vanilla) puis la detruit (sans butin) ; trois au plus par repas.
 */
public class Boar extends WildAnimal {

    int chargeLeft, cooldown;
    /** 1.0.5-beta (lot 13) : faim de cultures (ticks restants), prochain tirage, cultures mangees ce repas. */
    int hungry, hungerCheck = WildRules.BOAR_HUNGER_CHECK, eaten;
    /** Clotures, murets, portillons : un champ qu'ils entourent est a l'abri des sangliers. */
    public static final TagKey<Block> BARRIERS = BlockTags.create(Pioneers.rl("boar_barriers"));

    public Boar(EntityType<? extends Animal> type, Level level) {
        super(type, level, WildRules.BOAR);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.7, true));
        goalSelector.addGoal(6, new RootCrops(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected boolean fleesWhenHurt() {
        return isBaby();
    }

    /** Charge un joueur trop proche (le cas echeant) ; rend la cible. */
    public LivingEntity provoke() {
        if (getTarget() == null && cooldown == 0 && !isBaby()) {
            Player p = level().getNearestPlayer(getX(), getY(), getZ(), species().charge(), WildAnimal::scary);
            if (p != null && WildRules.charges(distanceTo(p), species().charge(), false)) {
                setTarget(p);
            }
        }
        return getTarget();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (hungry > 0) {
            hungry--;
        }
        if (--hungerCheck <= 0) {
            hungerCheck = WildRules.BOAR_HUNGER_CHECK;
            if (hungry <= 0 && !isBaby() && cropDamage() && getRandom().nextDouble() < WildRules.boarHungerRoll(cropChance())) {
                hungry = WildRules.BOAR_HUNGER_TICKS;
                eaten = 0;
            }
        }
        if (isBaby() && getTarget() != null) {
            setTarget(null);
        }
        LivingEntity t = provoke();
        if (t != null) {
            setAction(WildRules.CHARGE);
            if (chargeLeft == 0) {
                chargeLeft = WildRules.CHARGE_TICKS;
                playOwn("charge");
            }
            if (--chargeLeft <= 0 || !t.isAlive() || distanceTo(t) > 16 || t instanceof Player p && (p.isCreative() || p.isSpectator())) {
                calm(t);
            }
        }
    }

    void calm(Entity from) {
        setTarget(null);
        chargeLeft = 0;
        cooldown = WildRules.CHARGE_COOLDOWN;
        setAction(WildRules.IDLE);
        alarm(from.position(), WildRules.ALARM_TICKS, false);
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity e) {
        boolean hit = super.doHurtTarget(e);
        if (hit) {
            calm(e);
        }
        return hit;
    }

    public boolean charging() {
        return getTarget() != null;
    }

    // ------------------------------------------------------------------ cultures (1.0.5-beta lot 13)

    /** Faim de cultures immediate (GameTests du lot 13 : le hasard du tirage n'entre pas dans le test). */
    public void makeHungry() {
        hungry = WildRules.BOAR_HUNGER_TICKS;
        eaten = 0;
    }

    /** 1.0.5-beta (lot 21) : le but de fouille, tel que le selecteur le tient (GameTest du plantage : appel hors de son tour). */
    @Nullable
    public net.minecraft.world.entity.ai.goal.WrappedGoal rootGoal() {
        for (net.minecraft.world.entity.ai.goal.WrappedGoal g : goalSelector.getAvailableGoals()) {
            if (g.getGoal() instanceof RootCrops) {
                return g;
            }
        }
        return null;
    }

    public static boolean cropDamage() {
        return PiCommonConfig.get(PiCommonConfig.FAUNA_BOAR_CROP_DAMAGE, true);
    }

    public static double cropChance() {
        return PiCommonConfig.get(PiCommonConfig.FAUNA_BOAR_CROP_CHANCE, WildRules.BOAR_CROP_CHANCE);
    }

    /** Une culture (ble, carottes, pommes de terre, betteraves, tiges, cultures du mod...). */
    public static boolean crop(BlockState s) {
        return s.is(BlockTags.CROPS) || s.getBlock() instanceof CropBlock || s.getBlock() instanceof StemBlock;
    }

    /** Case infranchissable pour un sanglier : barriere (au sol ou a hauteur de tete), ou mur plein de deux blocs. */
    public static boolean blocks(Level level, BlockPos p) {
        //1.0.5-beta (lot 19, relecture) : la recherche d'enclos va jusqu'a 36 blocs du sanglier ; un troncon non charge ne se lit pas (le
        //lire le chargerait) : il compte comme une cloture (champ juge clos, donc epargne)
        if (!level.isLoaded(p)) {
            return true;
        }
        BlockState a = level.getBlockState(p), b = level.getBlockState(p.above());
        if (a.is(BARRIERS) || b.is(BARRIERS)) {
            return true;
        }
        return !a.getCollisionShape(level, p).isEmpty() && !b.getCollisionShape(level, p.above()).isEmpty();
    }

    /** La culture en {@code crop} est-elle dans un enclos (vu depuis le sanglier en {@code from}) ? */
    public static boolean enclosed(Level level, BlockPos crop, BlockPos from) {
        return WildRules.enclosed((dx, dz) -> !blocks(level, crop.offset(dx, 0, dz)), from.getX() - crop.getX(), from.getZ() - crop.getZ(),
              WildRules.BOAR_ENCLOSURE_RADIUS);
    }

    /** Ce sanglier (en {@code from}) peut-il detruire la culture en {@code pos} ? */
    public boolean rootable(BlockPos pos, BlockPos from) {
        Level l = level();
        boolean griefing = l instanceof ServerLevel sl && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(sl, this);
        return l.isLoaded(pos) && crop(l.getBlockState(pos)) && WildRules.boarMayRoot(cropDamage(), griefing, isBaby(), PiWild7.claimed(l, pos), enclosed(l, pos, from));
    }

    /** Fouille un tick (particules de terre et de culture, son des racines) ; detruit la culture au dernier (sans butin). */
    public void rootTick(BlockPos pos, int left) {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        BlockState st = sl.getBlockState(pos);
        if (left % 5 == 0) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 4, 0.25, 0.1, 0.25, 0.05);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sl.getBlockState(pos.below())), pos.getX() + 0.5, pos.getY() + 0.1,
                  pos.getZ() + 0.5, 3, 0.25, 0.05, 0.25, 0.05);
        }
        if (left % 20 == 0) {
            sl.playSound(null, pos, SoundEvents.ROOTED_DIRT_HIT, SoundSource.NEUTRAL, 0.6F, 0.8F + getRandom().nextFloat() * 0.2F);
        }
        if (left <= 0) {
            sl.destroyBlock(pos, false, this);
            sl.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.NEUTRAL, 0.8F, 0.9F);
            eaten++;
            if (eaten >= WildRules.BOAR_CROPS_PER_MEAL) {
                hungry = 0;
            }
        }
    }

    /** But : aller fouiller une culture permise (seulement affame, jamais en charge ni en fuite). */
    static final class RootCrops extends Goal {

        final Boar b;
        @Nullable
        BlockPos crop;
        int left, nextScan, stuck;

        RootCrops(Boar b) {
            this.b = b;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (b.hungry <= 0 || b.isBaby() || b.charging() || b.alarmed() || b.isInLove() || b.tickCount < nextScan) {
                return false;
            }
            nextScan = b.tickCount + 40;
            crop = find();
            return crop != null;
        }

        /**
         * Culture permise la plus proche : les cultures sont essayees de la plus proche a la plus lointaine ; les cases parcourues par la
         * recherche d'un enclos gardent leur verdict (un champ entier ne se reparcourt pas pour chaque plant).
         */
        @Nullable
        BlockPos find() {
            BlockPos at = b.blockPosition();
            int r = WildRules.BOAR_CROP_RANGE;
            //1.0.5-beta (lot 29) : palettes des sections d'abord (exact) : aucune culture possible, rien a lire
            if (!fr.pioneers.core.block.ScanFilter.mayContain(b.level(), at.offset(-r, -3, -r), at.offset(r, 3, r), Boar::crop)) {
                return null;
            }
            java.util.List<BlockPos> crops = new java.util.ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -3, -r), at.offset(r, 3, r))) {
                if (b.level().isLoaded(p) && crop(b.level().getBlockState(p))) {
                    crops.add(p.immutable());
                }
            }
            crops.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(at)));
            Map<Long, Boolean> known = new HashMap<>();
            Level l = b.level();
            boolean griefing = l instanceof ServerLevel sl && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(sl, b);
            for (BlockPos p : crops) {
                if (!WildRules.boarMayRoot(cropDamage(), griefing, b.isBaby(), PiWild7.claimed(l, p), false)) {
                    continue;
                }
                long key = BlockPos.asLong(p.getX(), p.getY(), p.getZ());
                Boolean closed = known.get(key);
                if (closed == null) {
                    java.util.Set<Long> seen = new java.util.HashSet<>();
                    closed = WildRules.enclosed((dx, dz) -> !blocks(l, p.offset(dx, 0, dz)), at.getX() - p.getX(), at.getZ() - p.getZ(),
                          WildRules.BOAR_ENCLOSURE_RADIUS, seen);
                    for (long c : seen) {
                        int dx = (int) (c >> 32), dz = (int) c;
                        known.put(BlockPos.asLong(p.getX() + dx, p.getY(), p.getZ() + dz), closed);
                    }
                }
                if (!closed) {
                    return p;
                }
            }
            return null;
        }

        @Override
        public void start() {
            left = WildRules.BOAR_ROOT_TICKS;
            stuck = 0;
            b.getNavigation().moveTo(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return crop != null && b.hungry > 0 && !b.charging() && !b.alarmed() && b.level().isLoaded(crop) && crop(b.level().getBlockState(crop))
                   && stuck < 200;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            // Le selecteur ne reverifie canContinueToUse qu'un tick sur deux : apres la fin du fouissage (crop = null), tick() peut
            // encore etre appele une fois (crash du 11/11/2026).
            if (crop == null) {
                return;
            }
            b.getLookControl().setLookAt(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5);
            if (b.position().distanceToSqr(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5) > 2.6) {
                stuck++;
                if (b.getNavigation().isDone()) {
                    b.getNavigation().moveTo(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5, 1.0);
                }
                return;
            }
            b.getNavigation().stop();
            b.setAction(WildRules.GRAZE);
            b.rootTick(crop, --left);
            if (left <= 0) {
                crop = null;
            }
        }

        @Override
        public void stop() {
            if (b.action() == WildRules.GRAZE) {
                b.setAction(WildRules.IDLE);
            }
            crop = null;
        }
    }
}
