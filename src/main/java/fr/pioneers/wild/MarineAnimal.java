package fr.pioneers.wild;

import fr.pioneers.farm.fish.PiFish;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 9) : base des animaux de la MER ET DES COTES (cetaces, requins, raie manta, phoques et otaries, manchots et pingouins).
 * <ul>
 *     <li>NAGE : commande de mouvement propre ({@link SwimControl}) : en eau, la bete se tourne vers sa cible a la vitesse de virage de
 *     son espece ({@link Wild9Rules#turnRate}), se cabre ou pique (tangage = {@code xRot}, lu par le rendu) et accelere de
 *     {@link GeneratedWild9#SWIM} par tick (l'eau freine de 10 %) ; a terre (amphibies), la marche ordinaire ;</li>
 *     <li>RESPIRATION : les cetaces, phoques et manchots retiennent leur souffle ({@link GeneratedWild9#AIR}) et remontent quand il baisse
 *     ({@link Wild9Goals.Breathe}) ; en surface, l'air revient d'un coup et les cetaces SOUFFLENT un jet d'embruns ({@link #breathe}) ;
 *     requins et raie respirent sous l'eau ; hors de l'eau, les betes de pleine eau s'assechent ({@link Wild9Rules#DRY_GILLS}) ;</li>
 *     <li>GEANTS : la boite de collision reste d'un seul bloc de large ou plus, et des PARTIES ({@link Part}) prolongent la tete et la
 *     queue des baleines et du requin-baleine (on les frappe partout) ; la boite de rendu couvre tout le corps (aucune disparition a
 *     l'ecran) ;</li>
 *     <li>APPARITION : eau libre et profonde ({@link GeneratedWild9#DEPTH}), jamais en riviere, au plus {@link Wild9Rules#SEA_CAP} geants
 *     marins dans {@link Wild9Rules#SEA_ZONE} blocs, etendue d'eau mesuree comme les poissons du mod ({@link PiFish#waterBody}) ;
 *     amphibies sur la cote, pres de l'eau.</li>
 * </ul>
 * ATTENTION : le constructeur de {@code Mob} appelle {@link #createNavigation} et {@link #registerGoals} avant que {@link #kind} soit
 * rempli : les buts ne lisent la sorte qu'en jeu.
 */
public abstract class MarineAnimal extends WildAnimal {

    /** Respirations en surface (souffles), ticks hors de l'eau, plongee en cours apres un souffle. */
    public int breaths, dry, blowLeft, diveLeft;
    /** Coups portes, plongees de fuite, delai avant la prochaine chasse aux poissons (requins), repas digere (orque). */
    public int strikes, dives, fishHunt, fed;
    /** 1.0.5-beta (lot 13) : vitesse de virage courante (degres par tick, lissee : {@link Wild9Rules#steer}) ; dernier souffle (tick). */
    public float yawRate;
    long lastBlow = -1000;
    /** Coin de cote ou elle revient (phoques, manchots : la colonie) ; null : la cote la plus proche. */
    @Nullable
    public BlockPos home;
    /** Parties de la boite des geants (null : bete d'un seul tenant). */
    @Nullable
    final Part[] parts;

    protected MarineAnimal(EntityType<? extends Animal> type, Level level, int kind) {
        super(type, level, kind);
        this.moveControl = new SwimControl(this);
        this.lookControl = new SeaLook(this);
        setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.parts = buildParts();
        if (parts != null) {
            setId(getId());
        }
    }

    /** Index de l'espece dans les tables du lot 9 (pendant le constructeur de Entity, la sorte se lit dans le type). */
    final int j() {
        int k = species != null ? kind : kindOf(getType());
        return Mth.clamp(k - GeneratedWild9.FIRST, 0, GeneratedWild9.SPECIES.length - 1);
    }

    /** Vit seulement dans l'eau (cetaces, requins, raie). */
    public boolean aquatic() {
        return GeneratedWild9.AQUATIC[j()];
    }

    /** Longueur du corps (blocs, petit compris). */
    public float length() {
        return GeneratedWild9.LENGTH[j()] * (isBaby() ? 0.55F : 1F);
    }

    @NotNull
    @Override
    protected PathNavigation createNavigation(@NotNull Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    // ------------------------------------------------------------------ respiration

    @Override
    public boolean canBreatheUnderwater() {
        return GeneratedWild9.AIR[j()] <= 0;
    }

    @Override
    public int getMaxAirSupply() {
        int a = GeneratedWild9.AIR[j()];
        return a > 0 ? a : 300;
    }

    /**
     * L'air revient d'un coup. 1.0.5-beta (lot 13, retour de Guillaume : « souffle des baleines beaucoup trop long, voire infini ») : Forge
     * appelle cette methode A CHAQUE TICK, meme sous l'eau (ForgeHooks.onLivingBreathe calcule toujours la recharge) : le souffle qu'elle
     * declenchait se rejouait donc a chaque tick des que l'air etait entame de 10 % (jet, son, action BLOW tenue, baleine arretee par
     * Breathe). Elle ne fait plus que rendre la valeur ; la vraie respiration se lit dans {@link #baseTick} (air remonte).
     */
    @Override
    protected int increaseAirSupply(int air) {
        return getMaxAirSupply();
    }

    /**
     * 1.0.5-beta (lot 28, retour 38 : nage saccadee) : l'air d'une bete de la mer n'est PLUS une donnee synchronisee. Sous l'eau, l'air d'un
     * mammifere marin baisse a chaque tick ; une donnee synchronisee modifiee oblige {@code ServerEntity.sendChanges} a envoyer la position A
     * CHAQUE TICK (au lieu d'un tick sur 3) ; et un paquet de position sans changement de cap ({@code ClientboundMoveEntityPacket.Pos}) relance
     * l'interpolation du client avec son cap COURANT ({@code ClientPacketListener.handleMoveEntity}) : le virage interpole s'arretait a mi-course
     * un tick sur deux (cap qui hoquette, corps de 9 a 25 blocs qui balaie par a-coups). Le client n'a pas besoin de l'air d'une bete (seul le
     * joueur affiche des bulles) ; la sauvegarde ({@code Air}) passe par ces methodes.
     */
    private int seaAir = -1;

    @Override
    public int getAirSupply() {
        return seaAir < 0 ? super.getAirSupply() : seaAir;
    }

    @Override
    public void setAirSupply(int air) {
        seaAir = air;
    }

    /** Respiration en surface : le souffle revient ; les cetaces soufflent un jet d'embruns, puis replongent. */
    public void breathe() {
        breaths++;
        if (Wild9Rules.cetacean(kind)) {
            blowLeft = Wild9Rules.BLOW_TICKS;
            setAction(Wild9Rules.BLOW);
            playOwn("blow");
            if (level() instanceof ServerLevel sl) {
                Vec3 f = Vec3.directionFromRotation(0, getYRot());
                double fw = length() * 0.18;
                double x = getX() + f.x * fw, z = getZ() + f.z * fw, y = getY() + getBbHeight() * 0.9;
                float h = Math.min(4F, 1.2F + length() * 0.25F);
                for (int i = 0; i < 10; i++) {
                    sl.sendParticles(ParticleTypes.CLOUD, x, y + i * h / 10, z, 2, 0.12 + i * 0.04, 0.05, 0.12 + i * 0.04, 0.01);
                }
                sl.sendParticles(ParticleTypes.SPLASH, x, y + h, z, 30, 0.6, 0.3, 0.6, 0.1);
                sl.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 12, 0.4, 0.1, 0.4, 0.05);
            }
        }
    }

    /** Remonte-t-elle respirer ? */
    public boolean needsAir() {
        return !canBreatheUnderwater() && Wild9Rules.mustSurface(getAirSupply(), getMaxAirSupply());
    }

    @Override
    public void baseTick() {
        int airBefore = getAirSupply();
        super.baseTick();
        if (!isAlive() || level().isClientSide) {
            return;
        }
        //1.0.5-beta (lot 13) : une VRAIE respiration (l'air est remonte ce tick, depuis moins de 90 %) : un seul jet par remontee
        if (!canBreatheUnderwater() && getAirSupply() > airBefore && Wild9Rules.blows(airBefore, getMaxAirSupply())
            && tickCount - lastBlow > Wild9Rules.BLOW_TICKS + Wild9Rules.DIVE_TICKS) {
            lastBlow = tickCount;
            breathe();
        }
        if (aquatic() && !isInWaterOrBubble()) {
            dry++;
            int limit = canBreatheUnderwater() ? Wild9Rules.DRY_GILLS : Wild9Rules.DRY_MAMMAL;
            if (dry > limit && dry % Wild9Rules.DRY_PERIOD == 0) {
                hurt(damageSources().dryOut(), canBreatheUnderwater() ? 2F : 1F);
            }
        } else {
            dry = 0;
        }
    }

    // ------------------------------------------------------------------ nage

    @Override
    public boolean isPushedByFluid() {
        return !aquatic();
    }

    @NotNull
    @Override
    public MobType getMobType() {
        return aquatic() ? MobType.WATER : super.getMobType();
    }

    @Override
    public void travel(@NotNull Vec3 input) {
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
            if (!aquatic() && getMoveControl().hasWanted() == false && !isEyeInFluid(FluidTags.WATER)) {
                setDeltaMovement(getDeltaMovement().add(0, -0.004, 0));
            }
            calculateEntityAnimation(true);
            return;
        }
        super.travel(input);
    }

    /** Acceleration de nage par tick a pleine vitesse (vitesse de croisiere ~ 10 x). */
    public double swimAccel() {
        return GeneratedWild9.SWIM[j()];
    }

    /** Nage vers un point (commande de nage directe : aucun chemin calcule, cible verifiee par les buts). */
    public void swimTo(Vec3 to, double speed) {
        swimTo(to, speed, false);
    }

    /**
     * 1.0.5-beta (lot 28, retour 38) : {@code chase} = POURSUITE d'une proie (attaque) : ni arret ni freinage pres d'elle
     * ({@link Wild9Rules#chaseAim}) : la bete passe sur sa proie et revient, sans a-coups. Avant : a moins d'un rayon d'arrivee (2,8 blocs
     * pour le grand blanc) la commande s'arretait (WAIT), repartait au tick suivant (nouvelle cible), s'arretait... : poussee et virage
     * coupes un tick sur deux pendant toute la charge.
     */
    public void swimTo(Vec3 to, double speed, boolean chase) {
        this.chase = chase;
        getNavigation().stop();
        getMoveControl().setWantedPosition(to.x, to.y, to.z, speed);
    }

    /** Poursuite en cours (serveur, {@link #swimTo(Vec3, double, boolean)}). */
    boolean chase;
    /** Poussee du tick precedent (serveur ; -1 : aucune). */
    float thrust = -1F;

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            if (blowLeft > 0 && --blowLeft == 0 && action() == Wild9Rules.BLOW) {
                setAction(WildRules.IDLE);
                diveLeft = Wild9Rules.DIVE_TICKS;
            }
            if (diveLeft > 0) {
                diveLeft--;
            }
            if (fishHunt > 0) {
                fishHunt--;
            }
            if (fed > 0) {
                fed--;
            }
            if (home == null && !aquatic() && onGround() && !isInWater() && tickCount > 20) {
                home = blockPosition();
            }
            int a = action();
            if (a == WildRules.IDLE && isInWater() && !aquatic()) {
                setAction(Wild9Rules.SWIM);
            } else if (a == Wild9Rules.SWIM && !isInWater()) {
                setAction(WildRules.IDLE);
            }
        }
        movePartsAlong();
    }

    @Override
    public boolean killedEntity(@NotNull ServerLevel level, @NotNull net.minecraft.world.entity.LivingEntity victim) {
        if (kind == Wild9Rules.ORCA) {
            fed = Wild9Rules.ORCA_FED;
            setTarget(null);
        }
        return super.killedEntity(level, victim);
    }

    @Override
    public void addAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("sea_fed", fed);
        if (home != null) {
            tag.putLong("sea_home", home.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fed = tag.getInt("sea_fed");
        home = tag.contains("sea_home") ? BlockPos.of(tag.getLong("sea_home")) : null;
    }

    // ------------------------------------------------------------------ parties des geants

    @Nullable
    Part[] buildParts() {
        float len = GeneratedWild9.LENGTH[kind - GeneratedWild9.FIRST];
        if (len < 7F) {
            return null;
        }
        GeneratedWild.Species s = speciesOf(kind);
        float off = len * 0.5F - s.width() * 0.5F;
        return new Part[]{new Part(this, "head", s.width(), s.height(), off), new Part(this, "tail", s.width() * 0.75F, s.height() * 0.8F,
              -off)};
    }

    @Override
    public boolean isMultipartEntity() {
        return parts != null;
    }

    @Nullable
    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                parts[i].setId(id + i + 1);
            }
        }
    }

    void movePartsAlong() {
        if (parts == null) {
            return;
        }
        float k = isBaby() ? 0.55F : 1F;
        Vec3 dir = Vec3.directionFromRotation(Mth.clamp(getXRot(), -30F, 30F), yBodyRot);
        for (Part p : parts) {
            p.xo = p.getX();
            p.yo = p.getY();
            p.zo = p.getZ();
            p.xOld = p.getX();
            p.yOld = p.getY();
            p.zOld = p.getZ();
            p.scale = k;
            p.refreshDimensions();
            p.setPos(getX() + dir.x * p.offset * k, getY() + dir.y * p.offset * k, getZ() + dir.z * p.offset * k);
        }
    }

    @NotNull
    @Override
    public AABB getBoundingBoxForCulling() {
        //portee du modele (tangage compris : la bete pivote autour de son centre) : rien ne sort de la boite de rendu
        float r = GeneratedWild9.REACH[j()] * (isBaby() ? 0.55F : 1F) + 0.5F;
        return new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r).minmax(getBoundingBox());
    }

    /** Une partie de la boite d'un geant : frappee, elle blesse la bete. */
    public static final class Part extends PartEntity<MarineAnimal> {

        final String name;
        final float w, h, offset;
        float scale = 1F;

        Part(MarineAnimal parent, String name, float w, float h, float offset) {
            super(parent);
            this.name = name;
            this.w = w;
            this.h = h;
            this.offset = offset;
            refreshDimensions();
        }

        @Override
        protected void defineSynchedData() {
        }

        @Override
        protected void readAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        }

        @Override
        protected void addAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        }

        @Override
        public boolean isPickable() {
            return true;
        }

        @Override
        public boolean hurt(@NotNull DamageSource source, float amount) {
            return !isInvulnerableTo(source) && getParent().hurt(source, amount);
        }

        @Override
        public boolean is(@NotNull Entity e) {
            return this == e || getParent() == e;
        }

        @NotNull
        @Override
        public net.minecraft.world.entity.EntityDimensions getDimensions(@NotNull net.minecraft.world.entity.Pose pose) {
            return net.minecraft.world.entity.EntityDimensions.scalable(w * scale, h * scale);
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }

        @Nullable
        @Override
        public net.minecraft.world.item.ItemStack getPickResult() {
            return getParent().getPickResult();
        }
    }

    // ------------------------------------------------------------------ apparition

    /**
     * Pleine eau : eau ici et au-dessus, profondeur d'au moins {@link GeneratedWild9#DEPTH} blocs, pas en riviere, une grande etendue
     * d'eau ({@link PiFish#BODY_SCAN} blocs relies au moins, comme les poissons du mod), pas trop de geants marins autour.
     */
    public static boolean checkSeaSpawn(EntityType<? extends MarineAnimal> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
          RandomSource random) {
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND || reason == MobSpawnType.BUCKET) {
            return true;
        }
        int k = kindOf(type);
        if (k < GeneratedWild9.FIRST || !level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos.above()).is(FluidTags.WATER)) {
            return false;
        }
        if (level.getBiome(pos).is(BiomeTags.IS_RIVER)) {
            return false;
        }
        if (depth(level, pos) < GeneratedWild9.DEPTH[k - GeneratedWild9.FIRST]) {
            return false;
        }
        Level l = level.getLevel();
        if (PiFish.waterBody(l, pos, PiFish.BODY_SCAN).size() < PiFish.BODY_SCAN) {
            return false;
        }
        return crowd(l, pos) < Wild9Rules.SEA_CAP;
    }

    /** Hauteur de la colonne d'eau qui passe par {@code pos} (du fond a la surface, au plus 64). */
    public static int depth(LevelAccessor level, BlockPos pos) {
        int n = 1;
        BlockPos.MutableBlockPos m = pos.mutable();
        for (int i = 0; i < 32 && level.getFluidState(m.move(0, -1, 0)).is(FluidTags.WATER); i++) {
            n++;
        }
        m.set(pos);
        for (int i = 0; i < 32 && level.getFluidState(m.move(0, 1, 0)).is(FluidTags.WATER); i++) {
            n++;
        }
        return n;
    }

    /** Geants marins du lot (pleine eau) dans {@link Wild9Rules#SEA_ZONE} blocs. */
    public static int crowd(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(MarineAnimal.class, new AABB(pos).inflate(Wild9Rules.SEA_ZONE), MarineAnimal::aquatic).size();
    }

    /** Cote : sable, gravier, pierre, glace ou neige dessous, rien de liquide ici, de l'eau a moins de 6 blocs. */
    public static boolean checkCoastSpawn(EntityType<? extends WildAnimal> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
          RandomSource random) {
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        boolean ground = below.is(BlockTags.SAND) || below.is(Blocks.GRAVEL) || below.is(BlockTags.BASE_STONE_OVERWORLD) || below.is(BlockTags.ICE)
                         || below.is(Blocks.SNOW_BLOCK) || below.is(BlockTags.SNOW) || below.is(BlockTags.DIRT) || below.is(Blocks.STONE);
        return ground && level.getFluidState(pos).isEmpty() && nearWater(level, pos, 6);
    }

    /** De l'eau a moins de {@code r} blocs (anneaux autour, un bloc plus bas, au meme niveau). */
    public static boolean nearWater(LevelAccessor level, BlockPos pos, int r) {
        for (int d = 1; d <= r; d++) {
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4;
                int x = pos.getX() + (int) Math.round(Math.cos(ang) * d), z = pos.getZ() + (int) Math.round(Math.sin(ang) * d);
                if (level instanceof Level l && !l.isLoaded(new BlockPos(x, pos.getY(), z))) {
                    continue;                                                  //1.0.5-beta (lot 21) : troncon absent : ignore
                }
                for (int dy = -2; dy <= 0; dy++) {
                    if (level.getFluidState(new BlockPos(x, pos.getY() + dy, z)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ variantes

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        return switch (kind) {
            case Wild9Rules.SPERM_WHALE -> random.nextInt(50) == 0 ? 1 : 0;           //cachalot blanc : tres rare
            case Wild9Rules.MANTA -> random.nextInt(7) == 0 ? 1 : 0;                  //raie noire : rare
            case Wild9Rules.SEAL -> random.nextBoolean() ? 1 : 0;
            default -> 0;
        };
    }

    @Override
    protected boolean fleesWhenHurt() {
        return getAttributeValue(Attributes.ATTACK_DAMAGE) <= 1.0 || species().damage() <= 0;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, @NotNull DamageSource source) {
        return !aquatic() && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public boolean checkSpawnObstruction(@NotNull net.minecraft.world.level.LevelReader level) {
        return aquatic() ? level.isUnobstructed(this) : super.checkSpawnObstruction(level);
    }

    @Override
    public int getAmbientSoundInterval() {
        return Wild9Rules.cetacean(kind) ? 400 : 240;
    }

    @Override
    protected float getSoundVolume() {
        return Wild9Rules.cetacean(kind) || kind == Wild9Rules.WHALE_SHARK ? 2.0F : 0.8F;
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        if (!aquatic()) {
            playSound(net.minecraft.sounds.SoundEvents.TURTLE_SHAMBLE, 0.15F, 1.2F);
        }
    }

    @Override
    protected void playSwimSound(float volume) {
        playSound(net.minecraft.sounds.SoundEvents.DOLPHIN_SWIM, Math.min(0.4F, volume), aquatic() ? 0.6F : 1.1F);
    }

    // ------------------------------------------------------------------ commandes

    /** Nage : virage borne, tangage vers la cible, poussee ; a terre (amphibies) : la marche ordinaire. */
    public static final class SwimControl extends MoveControl {

        final MarineAnimal m;

        public SwimControl(MarineAnimal m) {
            super(m);
            this.m = m;
        }

        @Override
        public void tick() {
            if (!m.isInWater()) {
                if (m.aquatic()) {
                    m.setSpeed(0F);
                    m.setZza(0F);
                    operation = Operation.WAIT;
                    return;
                }
                super.tick();
                return;
            }
            if (operation != Operation.MOVE_TO) {
                m.setSpeed(0F);
                m.setZza(0F);
                m.setYya(0F);
                m.yawRate *= 0.8F;
                m.setXRot(rotlerp(m.getXRot(), 0F, 2F));
                return;
            }
            double dx = wantedX - m.getX(), dy = wantedY - m.getY(), dz = wantedZ - m.getZ();
            double arrive = Wild9Rules.arrive(m.length());
            //1.0.5-beta (lot 28) : poursuite : ni arret ni freinage pres de la proie (meme direction, visee repoussee hors de la zone de freinage)
            if (m.chase) {
                double[] c = Wild9Rules.chaseAim(dx, dy, dz, m.length());
                dx = c[0];
                dy = c[1];
                dz = c[2];
            }
            //arrivee : a une fraction du corps a l'horizontale, mais a la HAUTEUR pres (une baleine qui remonte respirer doit sortir l'event)
            if (!m.chase && dx * dx + dz * dz < arrive * arrive && Math.abs(dy) < 0.6) {
                operation = Operation.WAIT;
                m.setZza(0F);
                m.setYya(0F);
                m.yawRate *= 0.5F;
                return;
            }
            //1.0.5-beta (lot 13) : pilotage PUR et lisse (Wild9Rules.steer, meme code que le banc tools/wild/SwimBench.java)
            float len = m.length();
            float[] st = Wild9Rules.steer(m.getYRot(), m.getXRot(), m.yawRate, dx, dy, dz, len,
                  Wild9Rules.maxTurn(len, Wild9Rules.cruise(m.swimAccel() * speedModifier)));
            m.setYRot(st[0]);
            m.yawRate = st[2];
            m.yBodyRot = m.getYRot();
            m.yHeadRot = m.getYRot();
            m.setXRot(st[1]);
            //1.0.5-beta (lot 28) : poussee lissee (au plus 0,08 de variation par tick)
            m.thrust = Wild9Rules.easeThrust(m.thrust, st[3]);
            float speed = (float) (speedModifier * m.swimAccel() * (m.isBaby() ? 0.8 : 1.0) * m.thrust);
            m.setSpeed(speed);
            //poussee le long du corps : avant (zza) et verticale (yya) selon le tangage
            float c = Mth.cos(m.getXRot() * Mth.DEG_TO_RAD), s = Mth.sin(m.getXRot() * Mth.DEG_TO_RAD);
            m.setZza(c);
            m.setYya(-s);
        }
    }

    /** Regard : en eau, la tete suit le corps (le tangage appartient a la nage) ; a terre, le regard ordinaire. */
    static final class SeaLook extends LookControl {

        SeaLook(MarineAnimal m) {
            super(m);
        }

        @Override
        public void tick() {
            if (mob.isInWater()) {
                mob.yHeadRot = Mth.approachDegrees(mob.yHeadRot, mob.yBodyRot, 10F);
                lookAtCooldown = 0;
                return;
            }
            super.tick();
        }
    }

    // ------------------------------------------------------------------ eau libre

    /** Eau libre autour de {@code p} (rayon {@code r} : les geants ont besoin de place). */
    public static boolean openWater(Level level, Vec3 p, double r) {
        BlockPos c = BlockPos.containing(p);
        if (!level.isLoaded(c) || !level.getFluidState(c).is(FluidTags.WATER)) {
            return false;
        }
        int ri = (int) Math.ceil(r);
        for (int[] d : new int[][]{{ri, 0, 0}, {-ri, 0, 0}, {0, 0, ri}, {0, 0, -ri}, {0, -ri, 0}}) {
            if (!level.getFluidState(c.offset(d[0], d[1], d[2])).is(FluidTags.WATER)) {
                return false;
            }
        }
        return true;
    }

    /** Le trajet en ligne droite reste-t-il en eau libre ? (un point tous les 2 blocs) */
    public static boolean clearPath(Level level, Vec3 from, Vec3 to, double r) {
        double len = from.distanceTo(to);
        int n = Math.max(1, (int) (len / 2));
        for (int i = 1; i <= n; i++) {
            if (!openWater(level, from.lerp(to, (double) i / n), r)) {
                return false;
            }
        }
        return true;
    }

    /** Surface de l'eau au-dessus de {@code p} (y du premier bloc sans eau), au plus 40 blocs plus haut. */
    public static int surfaceY(Level level, BlockPos p) {
        BlockPos.MutableBlockPos m = p.mutable();
        for (int i = 0; i < 40 && level.getFluidState(m).is(FluidTags.WATER); i++) {
            m.move(0, 1, 0);
        }
        return m.getY();
    }

    /** Dans l'eau (etat du jeu, ou bloc d'eau a ses pieds : un joueur qui vient d'y entrer). */
    public static boolean wet(Entity e) {
        return e.isInWater() || e.level().getFluidState(e.blockPosition()).is(FluidTags.WATER);
    }

    /** Congeneres de l'espece (meme type) a {@code r} blocs. */
    public List<MarineAnimal> school(double r) {
        return level().getEntitiesOfClass(MarineAnimal.class, getBoundingBox().inflate(r), w -> w != this && w.getType() == getType() && w.isAlive());
    }
}
