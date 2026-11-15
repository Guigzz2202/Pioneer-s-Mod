package fr.pioneers.wild;

import fr.pioneers.season.BiomeClimates;
import fr.pioneers.season.SeasonClimate;
import fr.pioneers.season.Seasons;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 6) : base des cinq especes du gibier. Le SEXE (male / femelle, tire a la naissance, sauvegarde) et l'ACTION en cours
 * ({@link WildRules#IDLE} ... {@link WildRules#FLEE} : pose du modele) sont synchronises. Commun : fuite devant le joueur ({@link WildGoals.Flee}
 * : distance de l'espece, moitie si le joueur est accroupi ; alerte des congeneres a 16 blocs), cohesion de la harde, repos couche, broutage
 * et animation propre ({@link WildGoals.Act}), elevage du joueur (nourriture du tag {@code pioneers:wild_food/<espece>}, deux sexes,
 * naissances en toute saison : specification §7), sons vanilla assembles, rien a manger sur un petit. Persistant et compte par le socle de
 * faune (groupe GIBIER).
 */
public abstract class WildAnimal extends Animal {

    static final EntityDataAccessor<Byte> SEX = SynchedEntityData.defineId(WildAnimal.class, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(WildAnimal.class, EntityDataSerializers.BYTE);
    /** 1.0.5-beta (lot 7) : variante (robe) de la bete, tiree a l'apparition selon le biome ({@link #pickVariant}), sauvegardee. */
    static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(WildAnimal.class, EntityDataSerializers.BYTE);

    public final int kind;
    final GeneratedWild.Species species;
    /**
     * 1.0.5-beta (lot 21) : CLIENT : phase de nage integree (radians, modulo 4 pi) et age ou elle a ete calculee ({@code Wild9Anim.phase} ;
     * -1 : jamais dessinee).
     */
    public float animPhase, animAge = -1F;
    /**
     * 1.0.5-beta (lot 28, retour 38) : CLIENT : transitions LISSEES des animations de la mer ({@code Wild9Anim.ease} : charge, morsure,
     * souffle, plongee, vitesse de nage), age de la derniere image (-1 : jamais dessinee). Avant : amplitude, machoire et poses changees d'un
     * coup au changement d'action.
     */
    public final float[] animEase = new float[6];
    public float animEaseAge = -1F;
    /** Alerte (serveur) : d'ou vient la menace, et pour combien de ticks encore. */
    @Nullable
    Vec3 threat;
    int alarm;

    protected WildAnimal(EntityType<? extends Animal> type, Level level, int kind) {
        super(type, level);
        this.kind = kind;
        this.species = speciesOf(kind);
    }

    /**
     * Fiche d'une sorte : gibier du lot 6 (0..4), puis predateurs et petite faune du lot 7 ({@link GeneratedWild7}, 5..10), puis savane et
     * jungle du lot 8 ({@link GeneratedWild8}, 11..16).
     */
    public static GeneratedWild.Species speciesOf(int kind) {
        if (kind >= GeneratedWild9.FIRST) {
            return GeneratedWild9.SPECIES[kind - GeneratedWild9.FIRST];         //1.0.5-beta (lot 9) : mer et cotes
        }
        if (kind >= GeneratedWild8.FIRST) {
            return GeneratedWild8.SPECIES[kind - GeneratedWild8.FIRST];
        }
        return kind < GeneratedWild.SPECIES.length ? GeneratedWild.SPECIES[kind] : GeneratedWild7.SPECIES[kind - GeneratedWild7.FIRST];
    }

    /** Nombre de sortes (lots 6, 7, 8 et 9). */
    public static int kinds() {
        return GeneratedWild.SPECIES.length + GeneratedWild7.SPECIES.length + GeneratedWild8.SPECIES.length + GeneratedWild9.SPECIES.length;
    }

    /**
     * 1.0.5-beta (lot 9) : sorte d'un type d'entite (id {@code pioneers:<espece>}), 0 si inconnu. Sert pendant le constructeur de
     * {@code Mob}, qui appelle {@code registerGoals} et {@code createNavigation} AVANT que {@link #kind} soit rempli.
     */
    public static int kindOf(EntityType<?> type) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id != null && id.getNamespace().equals(fr.pioneers.Pioneers.MODID)) {
            for (int k = 0; k < kinds(); k++) {
                if (speciesOf(k).id().equals(id.getPath())) {
                    return k;
                }
            }
        }
        return 0;
    }

    /**
     * Nourriture de l'elevage de cette bete. 1.0.5-beta (lot 9) : lue par le TYPE pendant la construction (avant le lot 9, les buts poses
     * par {@code registerGoals} lisaient {@link #kind} encore a 0 : toutes les especes etaient attirees par la nourriture du sanglier).
     */
    protected net.minecraft.tags.TagKey<net.minecraft.world.item.Item> food() {
        return PiWild.FOOD.get(species != null ? kind : kindOf(getType()));
    }

    public static AttributeSupplier.Builder createAttributes(int kind) {
        GeneratedWild.Species s = speciesOf(kind);
        AttributeSupplier.Builder b = Animal.createMobAttributes().add(Attributes.MAX_HEALTH, s.health()).add(Attributes.MOVEMENT_SPEED, s.speed())
              .add(Attributes.ATTACK_DAMAGE, Math.max(1, s.damage())).add(Attributes.ATTACK_KNOCKBACK, s.knockback())
              .add(Attributes.FOLLOW_RANGE, 24);
        if (kind == Wild7Rules.WOODPECKER || kind == Wild7Rules.PIGEON) {
            b.add(Attributes.FLYING_SPEED, 0.4);
        }
        if (kind >= GeneratedWild9.FIRST) {
            //1.0.5-beta (lot 9) : peau epaisse des geants marins ; ils ne reculent pas sous les coups ; vitesse de nage
            int j = kind - GeneratedWild9.FIRST;
            b.add(Attributes.ARMOR, GeneratedWild9.ARMOR[j]).add(Attributes.KNOCKBACK_RESISTANCE, Wild9Rules.knockbackResistance(kind))
             .add(Attributes.FOLLOW_RANGE, 32);
            if (kind == Wild9Rules.SEAGULL) {
                b.add(Attributes.FLYING_SPEED, 0.45);
            }
            return b;
        }
        if (kind >= GeneratedWild8.FIRST) {
            //1.0.5-beta (lot 8) : cuirasse du rhinoceros, peau de l'elephant ; les grands animaux encaissent sans reculer
            b.add(Attributes.ARMOR, GeneratedWild8.ARMOR[kind - GeneratedWild8.FIRST]).add(Attributes.ARMOR_TOUGHNESS, Wild8Rules.toughness(kind))
             .add(Attributes.KNOCKBACK_RESISTANCE, Wild8Rules.knockbackResistance(kind));
        }
        return b;
    }

    /** Sol du gibier : herbe, terre, podzol, neige ; assez de lumiere (comme les animaux du jeu). */
    public static boolean checkWildSpawnRules(EntityType<? extends WildAnimal> type, LevelAccessor level, MobSpawnType reason, BlockPos pos,
          RandomSource random) {
        BlockState below = level.getBlockState(pos.below());
        boolean ground = below.is(BlockTags.ANIMALS_SPAWNABLE_ON) || below.is(BlockTags.DIRT) || below.is(Blocks.SNOW_BLOCK)
                         || below.is(Blocks.POWDER_SNOW) || below.is(BlockTags.SAND);
        return ground && level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SEX, (byte) 0);
        entityData.define(ACTION, (byte) 0);
        entityData.define(VARIANT, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new WildGoals.Flee(this));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.1, Ingredient.of(food()), false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new WildGoals.Herd(this));
        goalSelector.addGoal(7, new WildGoals.Act(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ sexe, action

    public int sex() {
        return entityData.get(SEX);
    }

    public void setSex(int sex) {
        entityData.set(SEX, (byte) (sex == WildRules.FEMALE ? 1 : 0));
    }

    public boolean isMale() {
        return sex() == WildRules.MALE;
    }

    public int action() {
        return entityData.get(ACTION);
    }

    public void setAction(int a) {
        if (action() != a) {
            entityData.set(ACTION, (byte) a);
        }
    }

    public GeneratedWild.Species species() {
        return species;
    }

    // ------------------------------------------------------------------ variante, robe (lot 7)

    public int variant() {
        return entityData.get(VARIANT);
    }

    public void setVariant(int v) {
        entityData.set(VARIANT, (byte) v);
    }

    /** Variante tiree a l'apparition (lot 7 : selon le biome) ; 0 pour le gibier du lot 6. */
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        return 0;
    }

    /**
     * Robe a dessiner (nom de texture {@code textures/entity/wild/<robe>.png}) ; null : robe du gibier du lot 6 (sexe, petit, saison,
     * choisie par le rendu). Lot 7 : robe de la variante, ou robe propre du petit.
     */
    @Nullable
    public String coat() {
        if (kind < GeneratedWild7.FIRST) {
            return null;
        }
        if (kind >= GeneratedWild9.FIRST) {
            //lot 9 : robe de la variante (ou du petit : blanchon, poussin du manchot)
            int j = kind - GeneratedWild9.FIRST;
            String[] c9 = GeneratedWild9.COATS[j];
            return isBaby() && !GeneratedWild9.BABY[j].isEmpty() ? GeneratedWild9.BABY[j] : c9[Math.floorMod(variant(), c9.length)];
        }
        if (kind >= GeneratedWild8.FIRST) {
            //lot 8 : robe de la variante (ou du petit) ; le lion n'en a pas : robes par sexe, comme le gibier du lot 6
            int j = kind - GeneratedWild8.FIRST;
            String[] c8 = GeneratedWild8.COATS[j];
            if (c8.length == 0) {
                return null;
            }
            return isBaby() && !GeneratedWild8.BABY[j].isEmpty() ? GeneratedWild8.BABY[j] : c8[Math.floorMod(variant(), c8.length)];
        }
        int i = kind - GeneratedWild7.FIRST;
        if (isBaby() && !GeneratedWild7.BABY[i].isEmpty()) {
            return GeneratedWild7.BABY[i];
        }
        String[] coats = GeneratedWild7.COATS[i];
        return coats[Math.floorMod(variant(), coats.length)];
    }

    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty, @NotNull MobSpawnType reason,
          @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        setSex(random.nextBoolean() ? WildRules.MALE : WildRules.FEMALE);
        setVariant(pickVariant(level, random));
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("wild_sex", (byte) sex());
        tag.putByte("wild_variant", (byte) variant());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSex(tag.getByte("wild_sex"));
        setVariant(tag.getByte("wild_variant"));
    }

    // ------------------------------------------------------------------ elevage

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return stack.is(food());
    }

    @Override
    public boolean canMate(@NotNull Animal other) {
        return super.canMate(other) && other instanceof WildAnimal w && w.sex() != sex();
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob partner) {
        Entity e = getType().create(level);
        if (e instanceof WildAnimal w) {
            w.setSex(level.random.nextBoolean() ? WildRules.MALE : WildRules.FEMALE);
            w.setVariant(level.random.nextBoolean() || !(partner instanceof WildAnimal o) ? variant() : o.variant());
            return w;
        }
        return null;
    }

    @Override
    protected boolean shouldDropLoot() {
        return !isBaby();
    }

    // ------------------------------------------------------------------ alerte, fuite

    /** Menace venue de {@code from} : fuite pendant {@code ticks} ; la harde (meme espece a 16 blocs) est prevenue si {@code herd}. */
    public void alarm(Vec3 from, int ticks, boolean herd) {
        threat = from;
        alarm = Math.max(alarm, ticks);
        if (herd && level() instanceof ServerLevel) {
            for (WildAnimal w : kin(WildRules.HERD_RADIUS)) {
                if (w != this && w.alarm < ticks / 2) {
                    w.threat = from;
                    w.alarm = ticks;
                }
            }
        }
    }

    public boolean alarmed() {
        return alarm > 0 && threat != null;
    }

    /** Congeneres (meme type) a {@code r} blocs, vivants. */
    public List<WildAnimal> kin(double r) {
        return level().getEntitiesOfClass(WildAnimal.class, getBoundingBox().inflate(r, 6, r), w -> w.getType() == getType() && w.isAlive());
    }

    /** Une proie fuit quand on la blesse et previent sa harde ; le sanglier et le bison font face (voir leurs classes). */
    protected boolean fleesWhenHurt() {
        return true;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && fleesWhenHurt()) {
            alarm(attacker.position(), WildRules.ALARM_TICKS * 2, true);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && alarm > 0) {
            alarm--;
        }
    }

    /** Joueur que l'espece craint (pas en creatif ni spectateur). */
    public static boolean scary(Entity e) {
        return e instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive();
    }

    // ------------------------------------------------------------------ saisons (lues aussi par le rendu)

    /** Date du calendrier ici (null : pas de saisons) ; le banc du rendu la remplace. */
    @Nullable
    public Seasons.Date date() {
        return Seasons.now(level());
    }

    /** Biome enneige a l'endroit de la bete (famille des saisons). */
    public boolean snowyHere() {
        return BiomeClimates.of(level().getBiome(blockPosition())).family() == SeasonClimate.Family.SNOWY;
    }

    /** Graine propre a la bete (0 a 1). */
    public double seed() {
        return WildRules.seed(getUUID().getMostSignificantBits(), getUUID().getLeastSignificantBits());
    }

    // ------------------------------------------------------------------ sons

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return PiWild.sound(kind, "ambient");
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) {
        return PiWild.sound(kind, "hurt");
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return PiWild.sound(kind, "death");
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        SoundEvent s = switch (kind) {
            case WildRules.BOAR -> SoundEvents.PIG_STEP;
            case WildRules.TURKEY -> SoundEvents.CHICKEN_STEP;
            case WildRules.HARE -> SoundEvents.RABBIT_JUMP;
            case WildRules.BISON -> SoundEvents.COW_STEP;
            default -> SoundEvents.GOAT_STEP;
        };
        playSound(s, kind == WildRules.BISON ? 0.3F : 0.15F, 1.0F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return kind == WildRules.TURKEY ? 160 : 240;
    }

    @Override
    protected float getSoundVolume() {
        return kind == WildRules.BISON ? 1.2F : kind == WildRules.HARE ? 0.5F : 0.8F;
    }

    /** Joue le son propre de l'espece (charge, brame, roue, saut, colere). */
    public void playOwn(String name) {
        SoundEvent s = PiWild.sound(kind, name);
        if (s != null) {
            playSound(s, getSoundVolume(), getVoicePitch());
        }
    }
}
