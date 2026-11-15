package fr.pioneers.world.wild;

import fr.pioneers.core.common.PiCommonConfig;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 7, specification §4, §11 n° 14) : le RAT, partout et surtout PRES DES CONSTRUCTIONS (exception du socle). De temps en temps
 * ({@link Wild7Rules#THEFT_MIN}..{@link Wild7Rules#THEFT_MAX} ticks), il VOLE UN OBJET (de la nourriture d'abord) dans un coffre ou un
 * tonneau vanilla NON FERME : sans verrou vanilla et hors claim de clan (le mod n'a pas de verrou de coffre) ; jamais dans une caisse
 * d'entrepot, une machine ni aucun autre contenant ({@link #robbable}). Il emporte l'objet, qu'il lache a sa mort. Reglage serveur
 * {@code fauna.ratTheft} (actif par defaut). Il PULLULE LENTEMENT pres des reserves de nourriture (coffre ou tonneau qui contient a manger a
 * 8 blocs : un raton une fois sur 12 par minute, au plus 6 rats autour : {@link Wild7Rules#ratBirth}). Il fuit le joueur, les chats et les
 * serpents ; proie du chat (tag {@code pioneers:cat_prey}) et du serpent ; contre lui : le piege a rat. Robes : surmulot, rat noir.
 */
public class Rat extends WildAnimal {

    /** Objet vole (lache a la mort ; synchronise : la fiche au regard le montre). */
    static final net.minecraft.network.syncher.EntityDataAccessor<ItemStack> LOOT = net.minecraft.network.syncher.SynchedEntityData.defineId(Rat.class,
          net.minecraft.network.syncher.EntityDataSerializers.ITEM_STACK);
    int nextTheft;
    @Nullable
    BlockPos lure;
    int lureTicks;

    public Rat(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild7Rules.RAT);
        nextTheft = Wild7Rules.THEFT_MIN / 2 + random.nextInt(Wild7Rules.THEFT_MAX - Wild7Rules.THEFT_MIN);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Cat.class, 8.0F, 1.3, 1.6));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Snake.class, 6.0F, 1.3, 1.6));
        goalSelector.addGoal(2, new WildGoals.Flee(this));
        goalSelector.addGoal(3, new Lure(this));
        goalSelector.addGoal(4, new Steal(this));
        goalSelector.addGoal(7, new Wild7Goals.Idle(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        return random.nextInt(3) == 0 ? 1 : 0;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LOOT, ItemStack.EMPTY);
    }

    public ItemStack loot() {
        return entityData.get(LOOT);
    }

    void setLoot(ItemStack s) {
        entityData.set(LOOT, s.copy());
    }

    /** Le piege a rat appelle ce rat vers son appat ({@code ticks} au plus). */
    public void lureTo(BlockPos trap, int ticks) {
        lure = trap.immutable();
        lureTicks = ticks;
    }

    // ------------------------------------------------------------------ vol

    /** Contenant ou le rat peut voler : coffre (piege compris) ou tonneau vanilla, sans verrou, hors claim, vol permis. */
    public static boolean robbable(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        boolean kind = be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
        boolean locked = kind && be.saveWithoutMetadata().contains("Lock");
        return Wild7Rules.ratMayRob(theftEnabled(), kind, locked, PiWild7.claimed(level, pos));
    }

    public static boolean theftEnabled() {
        return PiCommonConfig.get(PiCommonConfig.FAUNA_RAT_THEFT, true);
    }

    /** Vole UN objet du contenant en {@code pos} (nourriture d'abord) ; rend l'objet pris (vide : rien). */
    public ItemStack steal(BlockPos pos) {
        if (!loot().isEmpty() || !robbable(level(), pos) || !(level().getBlockEntity(pos) instanceof Container c)) {
            return ItemStack.EMPTY;
        }
        int pick = -1;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && (pick < 0 || s.isEdible() && !c.getItem(pick).isEdible())) {
                pick = i;
            }
        }
        if (pick < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack loot = c.removeItem(pick, 1);
        setLoot(loot);
        c.setChanged();
        playOwn("steal");
        if (level() instanceof ServerLevel sl) {
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(16))) {
                p.displayClientMessage(Component.translatable("wild.pioneers.rat.stole", loot.getHoverName()), true);
            }
        }
        alarm(position().add(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5), WildRules.ALARM_TICKS, false);
        return loot;
    }

    /** Reserve de nourriture (coffre ou tonneau qui contient a manger) a 8 blocs ? */
    boolean foodNear() {
        BlockPos c = blockPosition();
        int r = Wild7Rules.FOOD_RANGE;
        //1.0.5-beta (lot 29) : tiles des troncons d'abord (exact) : ni coffre ni tonneau dans la boite, rien a lire
        if (!fr.pioneers.core.block.ScanFilter.mayHaveTile(level(), c.offset(-r, -3, -r), c.offset(r, 3, r),
              t -> t instanceof ChestBlockEntity || t instanceof BarrelBlockEntity)) {
            return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 3, r))) {
            BlockEntity be = level().getBlockEntity(p);
            if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && be instanceof Container ct) {
                for (int i = 0; i < ct.getContainerSize(); i++) {
                    if (ct.getItem(i).isEdible()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (nextTheft > 0) {
            nextTheft--;
        }
        if (lureTicks > 0 && --lureTicks == 0) {
            lure = null;
        }
        //pullule lentement pres des reserves de nourriture (une fois par minute, propre a chaque rat)
        if (!isBaby() && (tickCount + getId()) % Wild7Rules.RAT_BREED_PERIOD == 0 && level() instanceof ServerLevel sl) {
            int around = kin(16).size();
            if (Wild7Rules.ratBirth(foodNear(), around, random.nextDouble())) {
                Rat baby = PiWild7.RAT.get().create(sl);
                if (baby != null) {
                    baby.moveTo(getX(), getY(), getZ(), random.nextFloat() * 360F, 0);
                    baby.finalizeSpawn(sl, sl.getCurrentDifficultyAt(blockPosition()), MobSpawnType.BREEDING, null, null);
                    baby.setAge(-24000);
                    baby.setVariant(variant());
                    sl.addFreshEntityWithPassengers(baby);
                }
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(@NotNull DamageSource source, int looting, boolean hit) {
        super.dropCustomDeathLoot(source, looting, hit);
        if (!loot().isEmpty()) {
            spawnAtLocation(loot());
            setLoot(ItemStack.EMPTY);
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!loot().isEmpty()) {
            tag.put("rat_loot", loot().save(new CompoundTag()));
        }
        tag.putInt("rat_theft", nextTheft);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setLoot(tag.contains("rat_loot") ? ItemStack.of(tag.getCompound("rat_loot")) : ItemStack.EMPTY);
        if (tag.contains("rat_theft")) {
            nextTheft = tag.getInt("rat_theft");
        }
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.SILVERFISH_STEP, 0.1F, 1.3F);
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    /** Va voler : coffre ou tonneau ouvert a 12 blocs, le plus proche ; une fois a cote, prend un objet. */
    static final class Steal extends Goal {

        final Rat r;
        @Nullable
        BlockPos box;
        int left;
        /** 1.0.5-beta (lot 28) : heure du prochain essai (avant : « tickCount % 20 », jamais vrai a un choix de buts pour un rat sur deux). */
        long nextTry;

        Steal(Rat r) {
            this.r = r;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (r.nextTheft > 0 || r.isBaby() || !r.loot().isEmpty() || r.alarmed() || !theftEnabled() || r.level().getGameTime() < nextTry) {
                return false;
            }
            nextTry = r.level().getGameTime() + 20;
            box = null;
            double best = Double.MAX_VALUE;
            BlockPos c = r.blockPosition();
            int R = Wild7Rules.THEFT_RANGE;
            //1.0.5-beta (lot 29) : tiles des troncons d'abord (exact) : sans contenant garni dans la boite, pas de lecture des 6 727 cases
            if (fr.pioneers.core.block.ScanFilter.mayHaveTile(r.level(), c.offset(-R, -3, -R), c.offset(R, 3, R),
                  t -> t instanceof Container ct && !ct.isEmpty())) {
                for (BlockPos p : BlockPos.betweenClosed(c.offset(-R, -3, -R), c.offset(R, 3, R))) {
                    if (r.level().isLoaded(p) && r.level().getBlockEntity(p) instanceof Container ct && !ct.isEmpty() && robbable(r.level(), p)) {
                        double d = p.distSqr(c);
                        if (d < best) {
                            best = d;
                            box = p.immutable();
                        }
                    }
                }
            }
            if (box == null) {
                r.nextTheft = 600;                                 //rien a voler ici : il retentera dans 30 s
            }
            return box != null;
        }

        @Override
        public void start() {
            left = 400;
            r.getNavigation().moveTo(box.getX() + 0.5, box.getY(), box.getZ() + 0.5, 1.1);
        }

        @Override
        public boolean canContinueToUse() {
            return box != null && left > 0 && r.loot().isEmpty() && !r.alarmed();
        }

        @Override
        public void tick() {
            left--;
            if (box == null || left < 0) {
                return;                                                    //1.0.5-beta (lot 21)
            }
            if (r.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(box)) <= Wild7Rules.THEFT_REACH) {
                r.steal(box);
                left = 0;
            } else if (r.getNavigation().isDone()) {
                r.getNavigation().moveTo(box.getX() + 0.5, box.getY(), box.getZ() + 0.5, 1.1);
            }
        }

        @Override
        public void stop() {
            box = null;
            r.nextTheft = Wild7Rules.THEFT_MIN + r.random.nextInt(Wild7Rules.THEFT_MAX - Wild7Rules.THEFT_MIN);
        }
    }

    /** Attire par l'appat d'un piege a rat ({@link RatTrapBlock}). */
    static final class Lure extends Goal {

        final Rat r;

        Lure(Rat r) {
            this.r = r;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return r.lure != null && !r.alarmed();
        }

        @Override
        public void start() {
            r.getNavigation().moveTo(r.lure.getX() + 0.5, r.lure.getY(), r.lure.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return r.lure != null && !r.alarmed() && !r.getNavigation().isDone();
        }
    }
}
