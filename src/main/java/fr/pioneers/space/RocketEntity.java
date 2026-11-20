package fr.pioneers.space;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.common.PiLang;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiSounds;
import fr.pioneers.space.module.RocketCargo;
import fr.pioneers.space.module.RocketMenu;
import fr.pioneers.space.module.RocketModule;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.space.station.ItemStationKit;
import fr.pioneers.space.station.OrbitalStations;
import fr.pioneers.space.station.StationBuilder;
import fr.pioneers.space.tile.TileEntityLaunchPad;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Fusee (tiers 1 a 10, voir {@link RocketTier}).
 * <ol>
 *     <li>Accroupi + clic droit : l'interface de la fusee (modules et soutes debloquees par les modules).</li>
 *     <li>Posee sur le pas de tir, elle est remplie d'hydrogene liquide par le complexe.</li>
 *     <li>Avec un module habitacle, le joueur monte dedans (clic droit) puis appuie sur Espace : compte a rebours de 10 secondes.</li>
 *     <li>Decollage : la jauge d'altitude monte jusqu'a l'orbite.</li>
 *     <li>En orbite, le joueur choisit sa destination sur la carte du systeme solaire.</li>
 *     <li>Seule la capsule (avec le joueur et la fusee repliee dans sa soute) descend en parachute sur la destination.</li>
 * </ol>
 */
public class RocketEntity extends Entity {

    public static final int COUNTDOWN_TICKS = 200;
    /** Duree de la montee jusqu'a l'orbite (le ciel passe du bleu au noir etoile). */
    public static final int ASCENT_TICKS = 280;
    /** Separation du premier etage pendant la montee. */
    public static final int STAGING_TICK = 150;
    /** Cote client : effet de separation deja joue. */
    private boolean stagingShown;
    /** Tick (client) de la separation : l'etage qui retombe s'anime avec l'horloge locale, sans a-coups. */
    private int stagingClientTick;
    /** Hauteur (pixels de la maquette) de la base de l'etage superieur, par tier ; 0 : fusee a un seul etage (tier 1). */
    private static final int[] STAGE_HEIGHT_PX = {0, 100, 122, 134, 145, 160, 166, 194, 185, 205, 212, 226, 232};
    /** Altitude affichee a l'orbite (km). */
    public static final int ORBIT_ALTITUDE_KM = 400;

    public enum State {IDLE, COUNTDOWN, LAUNCHING, ORBIT, TRANSIT}

    /** Secondes restantes du compte a rebours (10 a 1). */
    public static int countdownSeconds(int timer) {
        return Math.max(1, (COUNTDOWN_TICKS - timer + 19) / 20);
    }

    private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIMER = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    /** Transit : astre de destination (ordinal, -1 aucun), duree du voyage et arrivee a la station orbitale. */
    private static final EntityDataAccessor<Integer> DESTINATION = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRANSIT_TICKS = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TO_STATION = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ORIGIN = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    /** 1.0.7 (lot 11) : propulseur a matiere noire a bord (portee des systemes lointains), pour la carte du client. */
    private static final EntityDataAccessor<Boolean> DARK_DRIVE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);

    private double verticalSpeed;
    /** Vrai pendant le transfert entre dimensions : autorise le passager a descendre. */
    private boolean transferring;

    private final RocketTier tier;
    /** Emplacements de modules (seuls les {@link RocketModule#slots} premiers servent). */
    private final NonNullList<ItemStack> moduleSlots = NonNullList.withSize(RocketModule.MAX_SLOTS, ItemStack.EMPTY);
    /** Soutes (capacite selon les modules). */
    private final RocketCargo cargo = new RocketCargo();

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level, RocketTier tier) {
        super(type, level);
        this.tier = tier;
        this.blocksBuilding = true;
    }

    public RocketTier getTier() {
        return tier;
    }

    /** Modules embarques (copie, emplacements utilisables uniquement). */
    public List<ItemStack> getModules() {
        List<ItemStack> list = new ArrayList<>();
        int slots = RocketModule.slots(tier);
        for (int i = 0; i < slots; i++) {
            if (!moduleSlots.get(i).isEmpty()) {
                list.add(moduleSlots.get(i).copy());
            }
        }
        return list;
    }

    public NonNullList<ItemStack> getModuleSlots() {
        return moduleSlots;
    }

    public void setModules(List<ItemStack> list) {
        moduleSlots.replaceAll(stack -> ItemStack.EMPTY);
        int index = 0;
        for (ItemStack stack : list) {
            for (int i = 0; i < stack.getCount() && index < moduleSlots.size(); i++) {
                moduleSlots.set(index++, stack.copyWithCount(1));
            }
        }
    }

    /** Retire et renvoie les modules embarques. */
    public List<ItemStack> takeModules() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < moduleSlots.size(); i++) {
            if (!moduleSlots.get(i).isEmpty()) {
                list.add(moduleSlots.get(i));
                moduleSlots.set(i, ItemStack.EMPTY);
            }
        }
        return list;
    }

    public RocketCargo getCargo() {
        return cargo;
    }

    public boolean hasModule(RocketModule module) {
        return RocketModule.count(getModules(), module) > 0;
    }

    /** La fusee repliee (objet), avec ses modules et sa cargaison. */
    public ItemStack toItem() {
        return ItemRocket.withContents(tier, getModules(), cargo);
    }

    public int getFuelCapacity() {
        return tier.fuelCapacity;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FUEL, 0);
        entityData.define(STATE, State.IDLE.ordinal());
        entityData.define(TIMER, 0);
        entityData.define(DESTINATION, -1);
        entityData.define(TRANSIT_TICKS, 1);
        entityData.define(TO_STATION, false);
        entityData.define(ORIGIN, -1);
        entityData.define(DARK_DRIVE, false);
    }

    /**
     * 1.0.7 (lot 11) : portee de la fusee : son tier, ou {@link SolarSystem#DARK_DRIVE_LEVEL} pour une fusee de tier 13 qui porte un
     * propulseur a matiere noire (les trois systemes lointains).
     */
    public int reachLevel() {
        boolean drive = level().isClientSide ? entityData.get(DARK_DRIVE) : hasDarkDrive();
        return drive && tier.level >= SolarSystem.DARK_DRIVE_LEVEL - 1 ? SolarSystem.DARK_DRIVE_LEVEL : tier.level;
    }

    /** Un propulseur a matiere noire parmi les modules utilisables (sans allocation). */
    private boolean hasDarkDrive() {
        int slots = Math.min(RocketModule.slots(tier), moduleSlots.size());
        for (int i = 0; i < slots; i++) {
            if (RocketModule.of(moduleSlots.get(i)) == RocketModule.DARK_MATTER_DRIVE) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Etat
    // ------------------------------------------------------------------

    public int getFuel() {
        return entityData.get(FUEL);
    }

    public void setFuel(int fuel) {
        entityData.set(FUEL, Mth.clamp(fuel, 0, getFuelCapacity()));
    }

    /** Remplissage par le pas de tir. Retourne la quantite acceptee. */
    public int fill(int amount) {
        if (getState() != State.IDLE) {
            return 0;
        }
        int accepted = Math.min(amount, getFuelCapacity() - getFuel());
        if (accepted > 0) {
            setFuel(getFuel() + accepted);
        }
        return accepted;
    }

    public State getState() {
        return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
    }

    private void setState(State state) {
        entityData.set(STATE, state.ordinal());
        entityData.set(TIMER, 0);
    }

    public int getTimer() {
        return entityData.get(TIMER);
    }

    public boolean isFlying() {
        return getState() != State.IDLE;
    }

    public boolean isTransferring() {
        return transferring;
    }

    /** Altitude affichee (km) : 0 au sol, {@link #ORBIT_ALTITUDE_KM} en orbite. */
    public float getAltitudeFraction() {
        return switch (getState()) {
            case IDLE, COUNTDOWN -> 0;
            case LAUNCHING -> Mth.clamp(getTimer() / (float) ASCENT_TICKS, 0, 1);
            case ORBIT, TRANSIT -> 1;
        };
    }

    /** Destination du transit en cours (null hors transit). */
    @Nullable
    public SolarSystem.Body getDestination() {
        int index = entityData.get(DESTINATION);
        return SolarSystem.Body.byIndex(index);
    }

    /** Astre de depart du transit (null si inconnu). */
    @Nullable
    public SolarSystem.Body getTransitOrigin() {
        int index = entityData.get(ORIGIN);
        return SolarSystem.Body.byIndex(index);
    }

    public boolean isTransitToStation() {
        return entityData.get(TO_STATION);
    }

    public int getTransitTicks() {
        return Math.max(1, entityData.get(TRANSIT_TICKS));
    }

    /** Avancement du transit (0 a 1). */
    public float getTransitProgress(float partialTick) {
        return getState() != State.TRANSIT ? 0 : Mth.clamp((getTimer() + partialTick) / getTransitTicks(), 0, 1);
    }

    /**
     * Duree du voyage (ticks) : quelques secondes vers une lune voisine, plus longtemps vers les planetes lointaines, et un saut
     * interstellaire vers un autre systeme.
     */
    public static int transitTicks(@Nullable SolarSystem.Body from, SolarSystem.Body to) {
        if (from == null) {
            return 160;
        }
        if (from.sector() != to.sector()) {
            return 560;
        }
        SolarSystem.Body a = from.getPrimary(), b = to.getPrimary();
        double distance = Math.abs(a.orbit - b.orbit) + (a == b ? 0.25 : 0);
        return (int) Mth.clamp(120 + distance * 30, 120, 420);
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (isVehicle() || isFlying()) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            //Accroupi : modules et soutes
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider((id, inventory, p) -> new RocketMenu(id, inventory, this),
                      Component.translatable(tier.getItem().getDescriptionId())), buffer -> {
                    buffer.writeVarInt(getId());
                    buffer.writeVarInt(tier.ordinal());
                });
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!hasModule(RocketModule.PASSENGER)) {
            //Pas d'habitacle : personne ne monte a bord
            if (!level().isClientSide) {
                player.displayClientMessage(Component.translatable("rocket.pioneers.no_cabin").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Le menu de la fusee reste ouvert tant qu'elle est au sol et a portee. */
    public boolean canPlayerConfigure(Player player) {
        return isAlive() && !isFlying() && !isVehicle() && player.distanceToSqr(this) < 64;
    }

    /** Demande de decollage (touche Espace du passager). */
    public void tryLaunch(ServerPlayer player) {
        if (getState() != State.IDLE || player.getVehicle() != this) {
            return;
        }
        List<ItemStack> loadout = getModules();
        //1.9 (MAJ RP) : skill Spatial, un vol pilote a la main demande moins d'ergols
        int needed = (int) Math.ceil(TileEntityLaunchPad.mannedFuel(tier, loadout) * fr.pioneers.core.profile.PioneerApi.factor(player, "space.fuel"));
        if (SolarSystem.reachableFrom(level().dimension(), getX(), reachLevel()).isEmpty()) {
            player.displayClientMessage(PiLang.ROCKET_NO_DESTINATION.translateColored(ChatFormatting.RED), true);
        } else if (RocketModule.count(loadout, RocketModule.PASSENGER) == 0) {
            player.displayClientMessage(Component.translatable("rocket.pioneers.no_cabin").withStyle(ChatFormatting.RED), true);
        } else if (getFuel() < needed) {
            player.displayClientMessage(PiLang.ROCKET_NOT_FUELED.translateColored(ChatFormatting.RED, getFuel(), needed), true);
        } else {
            setState(State.COUNTDOWN);
        }
    }

    /** Choix sur la carte du systeme solaire (en orbite). */
    public void selectDestination(ServerPlayer player, SolarSystem.Body body) {
        if (getState() != State.ORBIT || player.getVehicle() != this || !body.isReachableFrom(level().dimension(), getX(), reachLevel())
            || !ResearchEffects.canLand(body) && !body.isHere(level().dimension())) {
            return;
        }
        if (player.server.getLevel(body.dimension()) != null) {
            //Les coordonnees du point de depart sont retenues des maintenant (le transit se fait depuis l'orbite)
            groundPosition(player);
            beginTransit(body, false);
        }
    }

    /**
     * Abandon de la mission (bouton de la carte) : la fusee repliee redescend en capsule a la verticale du pas de tir, sur l'astre de
     * depart (descente pilotee, comme a l'arrivee).
     */
    public void abortMission(ServerPlayer player) {
        if (getState() != State.ORBIT || player.getVehicle() != this || !(level() instanceof ServerLevel here)) {
            return;
        }
        SolarSystem.Body origin = SolarSystem.origin(here.dimension(), getX());
        player.displayClientMessage(Component.translatable("rocket.pioneers.aborted").withStyle(ChatFormatting.GOLD), true);
        travel(here, getX(), CapsuleEntity.arrivalAltitude(here), getZ(), origin);
    }

    /** Debut du voyage : la fusee quitte l'orbite et file vers sa destination (cinematique cote client). */
    private void beginTransit(SolarSystem.Body body, boolean station) {
        SolarSystem.Body from = SolarSystem.origin(level().dimension(), getX());
        setState(State.TRANSIT);
        entityData.set(DESTINATION, body.ordinal());
        entityData.set(TO_STATION, station);
        entityData.set(ORIGIN, from == null ? -1 : from.ordinal());
        entityData.set(TRANSIT_TICKS, Math.max(40, (int) (transitTicks(from, body) * PiCommonConfig.get(PiCommonConfig.TRANSIT_TIME, 1.0))));
        level().playSound(null, blockPosition(), PiSounds.TRANSIT_BURN.get(), SoundSource.NEUTRAL, 2.0F, 1.0F);
    }

    /** Fin du transit : descente en capsule vers la surface, ou amarrage a la station orbitale. */
    private void arrive(ServerPlayer player) {
        SolarSystem.Body body = getDestination();
        if (body == null) {
            setState(State.ORBIT);
            return;
        }
        if (isTransitToStation()) {
            ServerLevel orbit = player.server.getLevel(PiDimensions.ORBIT);
            if (orbit != null) {
                BlockPos deck = OrbitalStations.origin(body);
                travel(orbit, deck.getX() + 0.5, deck.getY() + OrbitalStations.ARRIVAL_HEIGHT, deck.getZ() + 0.5, body);
            }
            return;
        }
        ServerLevel destination = player.server.getLevel(body.dimension());
        if (destination == null) {
            setState(State.ORBIT);
            return;
        }
        //Memes coordonnees qu'au decollage ; depuis une station orbitale, celles du dernier decollage depuis le sol
        CompoundTag ground = player.getPersistentData().getCompound(NBT_GROUND);
        double x = ground.contains("x") ? ground.getDouble("x") : getX(), z = ground.contains("z") ? ground.getDouble("z") : getZ();
        travel(destination, x, CapsuleEntity.arrivalAltitude(destination), z, body);
    }

    private static final String NBT_GROUND = "pioneers_ground";

    /**
     * Coordonnees d'arrivee : x et z du decollage (on retombe a la verticale du point de depart, quel que soit l'astre). En orbite
     * (stations), ce sont celles du dernier decollage depuis le sol, retenues sur le joueur.
     */
    private double[] groundPosition(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (level().dimension() != PiDimensions.ORBIT) {
            CompoundTag ground = new CompoundTag();
            ground.putDouble("x", getX());
            ground.putDouble("z", getZ());
            data.put(NBT_GROUND, ground);
            return new double[]{getX(), getZ()};
        }
        CompoundTag ground = data.getCompound(NBT_GROUND);
        return ground.contains("x") ? new double[]{ground.getDouble("x"), ground.getDouble("z")} : new double[]{0.5, 0.5};
    }

    /**
     * Station orbitale de l'astre choisi : on s'y amarre si elle existe ; sinon un kit de station (inventaire du passager ou soute)
     * la deploie. Recherche « Stations orbitales » requise.
     */
    public void selectStation(ServerPlayer player, SolarSystem.Body body) {
        if (getState() != State.ORBIT || player.getVehicle() != this || tier == null || !OrbitalStations.canHost(body)) {
            return;
        }
        if (body.isStar() && !ResearchEffects.has(ResearchNode.SOLAR_ABSORPTION)) {
            player.displayClientMessage(Component.translatable("station.pioneers.star_research_needed").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!ResearchEffects.has(ResearchNode.ORBITAL_STATIONS)) {
            player.displayClientMessage(Component.translatable("station.pioneers.research_needed").withStyle(ChatFormatting.RED), true);
            return;
        }
        SolarSystem.Body origin = SolarSystem.origin(level().dimension(), getX());
        int originTier = origin == null ? 1 : origin.requiredTier();
        if (Math.max(body.requiredTier(), originTier) > reachLevel()) {
            player.displayClientMessage(PiLang.ROCKET_NO_DESTINATION.translateColored(ChatFormatting.RED), true);
            return;
        }
        ServerLevel orbit = player.server.getLevel(PiDimensions.ORBIT);
        if (orbit == null) {
            return;
        }
        OrbitalStations stations = OrbitalStations.get(player.server);
        BlockPos deck = OrbitalStations.origin(body);
        if (!stations.has(body)) {
            if (!consumeStationKit(player)) {
                player.displayClientMessage(Component.translatable("station.pioneers.kit_needed",
                      Component.translatable(body.getTranslationKey())).withStyle(ChatFormatting.RED), true);
                return;
            }
            StationBuilder.build(orbit, deck);
            stations.markDeployed(body);
            player.sendSystemMessage(Component.translatable("station.pioneers.deployed", Component.translatable(body.getTranslationKey()))
                  .withStyle(ChatFormatting.AQUA));
        }
        //Le point de depart au sol est retenu pour le retour (on rejoint la station, puis on redescend au meme endroit)
        groundPosition(player);
        beginTransit(body, true);
    }

    /**
     * 1.0.7 : « Demarrer le chantier de la megastructure » sur la carte : seulement le passager de la fusee en orbite, vers un astre que
     * la fusee peut atteindre. Le chantier devient une destination des fusees cargo (complexes de lancement en mode logistique), qui y livrent
     * sa liste d'ingredients ; la fusee du joueur ne bouge pas.
     */
    public void startMegaSite(ServerPlayer player, int mega) {
        if (getState() != State.ORBIT || player.getVehicle() != this || tier == null || mega < 0 || mega >= fr.pioneers.space.mega.Megastructures.COUNT) {
            return;
        }
        fr.pioneers.space.mega.MegaWorld world = fr.pioneers.space.mega.MegaWorld.get(player.server);
        if (world.unlocked(mega)) {
            return;
        }
        SolarSystem.Body body = fr.pioneers.space.mega.Megastructures.body(mega);
        SolarSystem.Body origin = SolarSystem.origin(level().dimension(), getX());
        int originTier = origin == null ? 1 : origin.requiredTier();
        if (Math.max(body.requiredTier(), originTier) > reachLevel()) {
            player.displayClientMessage(PiLang.ROCKET_NO_DESTINATION.translateColored(ChatFormatting.RED), true);
            return;
        }
        boolean fresh = world.opener(mega) == null;
        world.open(mega, player);
        fr.pioneers.space.cargo.CargoNetwork.get(player.server).openMegaSite(mega);
        if (fresh) {
            player.server.getPlayerList().broadcastSystemMessage(Component.translatable("mega.pioneers.site_opened",
                  player.getDisplayName(), fr.pioneers.space.mega.Megastructures.name(mega)).withStyle(ChatFormatting.AQUA), false);
        }
    }

    /** Un kit de station : d'abord dans l'inventaire du passager, sinon dans la soute de la fusee. */
    private boolean consumeStationKit(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof ItemStationKit) {
                inventory.removeItem(i, 1);
                return true;
            }
        }
        return cargo.removeItem(stack -> stack.getItem() instanceof ItemStationKit);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isFlying() || isVehicle()) {
            return false;
        }
        //Un joueur qui frappe la fusee la recupere (le carburant est perdu)
        if (source.getEntity() instanceof Player player) {
            if (!player.getAbilities().instabuild) {
                spawnAtLocation(toItem(), 1);
            }
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        //Sans module habitacle, personne ne monte a bord
        return getPassengers().isEmpty() && !isFlying() && (level().isClientSide || hasModule(RocketModule.PASSENGER));
    }

    @Override
    public double getPassengersRidingOffset() {
        //Assis dans la capsule, sous le cone
        return tier.riderOffset;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 side = Vec3.directionFromRotation(0, passenger.getYRot()).scale(1.6);
        return new Vec3(getX() + side.x, getY(), getZ() + side.z);
    }

    // ------------------------------------------------------------------
    // Vol
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0) {
            //envoye seulement au changement (SynchedEntityData compare la valeur)
            entityData.set(DARK_DRIVE, hasDarkDrive());
        }
        if (getState() != State.LAUNCHING) {
            stepLerp();
        }
        int timer = getTimer() + 1;
        switch (getState()) {
            case IDLE -> {
                verticalSpeed = 0;
                if (!onGround()) {
                    setDeltaMovement(0, Math.max(getDeltaMovement().y - 0.04, -0.6), 0);
                } else {
                    setDeltaMovement(Vec3.ZERO);
                }
                move(MoverType.SELF, getDeltaMovement());
            }
            case COUNTDOWN -> {
                if (!level().isClientSide) {
                    entityData.set(TIMER, timer);
                    //Une annonce par seconde restante : 10 au premier tick, puis a chaque seconde pleine (9, 8... 1)
                    int remaining = COUNTDOWN_TICKS - timer;
                    if (remaining > 0 && (timer == 1 || timer > 1 && remaining % 20 == 0)) {
                        announce(Component.literal(String.valueOf(countdownSeconds(timer))));
                    }
                    if (timer >= COUNTDOWN_TICKS) {
                        setFuel(0);
                        setState(State.LAUNCHING);
                        announce(PiLang.ROCKET_LIFTOFF.translateColored(ChatFormatting.GOLD));
                        //0.18.0 : progres « premier vol habite »
                        IndustryEvents.award(level(), blockPosition(), "space_manned_launch");
                        for (net.minecraft.world.entity.Entity p : getPassengers()) {
                            if (p instanceof ServerPlayer sp) {
                                fr.pioneers.core.profile.PioneerApi.gain(sp, "spaceflight.launch", 1, null);
                            }
                        }
                        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 2.0F, 0.5F);
                    }
                } else if (timer > COUNTDOWN_TICKS - 60) {
                    //Allumage des moteurs les 3 dernieres secondes
                    exhaust(0.5F, false, 0);
                }
            }
            case LAUNCHING -> {
                verticalSpeed = Math.min(verticalSpeed + 0.02, 2.0);
                setPos(getX(), getY() + verticalSpeed, getZ());
                if (level().isClientSide) {
                    boolean staged = isStaged();
                    if (staged && !stagingShown) {
                        stagingShown = true;
                        stagingClientTick = tickCount;
                        staging();
                    }
                    exhaust(1.0F, true, staged ? stageHeight() : 0);
                } else {
                    entityData.set(TIMER, timer);
                    if (timer % 10 == 0) {
                        level().playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 3.0F, 0.4F);
                    }
                    if (timer == STAGING_TICK && stageHeight() > 0) {
                        announce(Component.translatable("rocket.pioneers.staging").withStyle(ChatFormatting.GOLD));
                        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.5F, 1.4F);
                    }
                    if (timer >= ASCENT_TICKS) {
                        //Orbite atteinte : on attend le choix de la destination
                        setState(State.ORBIT);
                        announce(PiLang.ROCKET_ORBIT.translateColored(ChatFormatting.AQUA));
                    }
                }
            }
            case ORBIT -> {
                //Immobile en orbite
                verticalSpeed = 0;
                setDeltaMovement(Vec3.ZERO);
                if (!level().isClientSide && !isVehicle()) {
                    //Plus personne a bord (deconnexion...) : la fusee redescend
                    setState(State.IDLE);
                }
            }
            case TRANSIT -> {
                verticalSpeed = 0;
                setDeltaMovement(Vec3.ZERO);
                if (!level().isClientSide) {
                    entityData.set(TIMER, timer);
                    if (!(getFirstPassenger() instanceof ServerPlayer player)) {
                        setState(State.IDLE);
                    } else if (timer >= getTransitTicks()) {
                        arrive(player);
                    }
                }
            }
        }
    }

    /** Message au centre de l'ecran du passager. */
    private void announce(Component text) {
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetTitleTextPacket(text));
            }
        }
    }

    /**
     * Arrivee : seule la capsule descend en parachute au-dessus de la destination, avec le passager,
     * et la fusee repliee dans sa soute (a reposer sur un pas de tir pour repartir).
     */
    private void travel(ServerLevel destination, double x, double y, double z, SolarSystem.Body body) {
        List<Entity> passengers = List.copyOf(getPassengers());
        transferring = true;
        ejectPassengers();
        transferring = false;
        for (Entity passenger : passengers) {
            if (passenger instanceof ServerPlayer player) {
                player.teleportTo(destination, x, y, z, player.getYRot(), player.getXRot());
            }
        }
        CapsuleEntity capsule = PiEntityTypes.CAPSULE.get().create(destination);
        if (capsule != null) {
            capsule.moveTo(x, y, z, getYRot(), 0);
            capsule.setCargo(toItem());
            capsule.prepareDescent(destination, tier, body);
            destination.addFreshEntity(capsule);
            for (Entity passenger : passengers) {
                if (passenger instanceof ServerPlayer player && player.level() == destination) {
                    player.startRiding(capsule, true);
                }
            }
        }
        discard();
    }

    /** Hauteur (blocs) de la base de l'etage superieur ; 0 si la fusee n'a qu'un etage. */
    public double stageHeight() {
        int px = STAGE_HEIGHT_PX[Math.min(getTier().ordinal(), STAGE_HEIGHT_PX.length - 1)];
        return px / 16.0;
    }

    /** Le premier etage est-il deja largue ? (ascension apres la separation, orbite, transit) */
    /** Age (ticks, client) de l'etage largue, pour son animation de chute. */
    public float stagingAge(float partialTick) {
        return stagingShown ? tickCount - stagingClientTick + partialTick : 0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        //Flammes et etage largue debordent de la boite de collision
        return getBoundingBox().inflate(3, 12, 3);
    }

    public boolean isStaged() {
        if (stageHeight() <= 0) {
            return false;
        }
        return switch (getState()) {
            case LAUNCHING -> getTimer() >= STAGING_TICK;
            case ORBIT, TRANSIT -> true;
            default -> false;
        };
    }

    /** Separation du premier etage : anneau de fumee et eclair a la jonction des etages (cote client). */
    private void staging() {
        double y = getY() + stageHeight();
        for (int i = 0; i < 80; i++) {
            double angle = random.nextDouble() * Math.PI * 2, r = 0.4 + random.nextDouble() * 0.8;
            level().addParticle(i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.LARGE_SMOKE, getX() + Math.cos(angle) * r, y, getZ() + Math.sin(angle) * r,
                  Math.cos(angle) * 0.35, -0.1 - random.nextDouble() * 0.3, Math.sin(angle) * 0.35);
        }
        for (int i = 0; i < 20; i++) {
            level().addParticle(ParticleTypes.LAVA, getX(), y, getZ(), 0, 0, 0);
        }
        level().addParticle(ParticleTypes.FLASH, getX(), y, getZ(), 0, 0, 0);
    }

    /** Flammes et fumee sous la tuyere (cote client) ; {@code base} : hauteur de la tuyere active (etage superieur apres separation). */
    private void exhaust(float intensity, boolean plume, double base) {
        for (int i = 0; i < 6 * intensity + 1; i++) {
            double ox = (random.nextDouble() - 0.5) * 0.5, oz = (random.nextDouble() - 0.5) * 0.5;
            level().addParticle(ParticleTypes.FLAME, getX() + ox, getY() + base - 0.1, getZ() + oz, ox * 0.2, -0.4 - random.nextDouble() * 0.4, oz * 0.2);
            if (random.nextFloat() < intensity) {
                level().addParticle(plume ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.LARGE_SMOKE,
                      getX() + ox * 2, getY() + base - 0.5, getZ() + oz * 2, ox * 0.15, -0.05, oz * 0.15);
            }
        }
        //Nuage de vapeur au sol pendant l'allumage et le decollage
        if (onGround() || plume && getTimer() < 40) {
            for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                level().addParticle(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), Math.cos(angle) * 0.35, 0.02, Math.sin(angle) * 0.35);
            }
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setFuel(tag.getInt("fuel"));
        List<ItemStack> list = new ArrayList<>();
        ItemRocket.readModules(tag.getList(ItemRocket.NBT_MODULES, Tag.TAG_COMPOUND), list);
        setModules(list);
        cargo.load(tag.getCompound(ItemRocket.NBT_CARGO));
        //Une fusee sauvegardee en plein vol redescend simplement
        setState(State.IDLE);

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("fuel", getFuel());
        tag.put(ItemRocket.NBT_MODULES, ItemRocket.writeModules(getModules()));
        tag.put(ItemRocket.NBT_CARGO, cargo.save());

    }

    // ------------------------------------------------------------------
    // Interpolation cote client : les positions du serveur arrivent par paquets irreguliers ; au lieu de sauter d'une position a
    // l'autre (saccades, fusee qui « disparait » un instant), on glisse vers la derniere recue en quelques ticks.
    // ------------------------------------------------------------------

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot;

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        //Au decollage, le client calcule lui-meme l'ascension (meme acceleration que le serveur) : on ne le recale que s'il s'est
        //vraiment ecarte, sinon chaque paquet (en retard de quelques ticks) le ferait redescendre puis remonter.
        if (getState() == State.LAUNCHING && Math.abs(y - getY()) < 24 && Math.abs(x - getX()) < 1 && Math.abs(z - getZ()) < 1) {
            return;
        }
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = Math.max(1, steps) + 1;
    }

    /** Avance d'un pas vers la position recue du serveur (client). */
    private void stepLerp() {
        if (!level().isClientSide || lerpSteps <= 0) {
            return;
        }
        double nx = getX() + (lerpX - getX()) / lerpSteps;
        double ny = getY() + (lerpY - getY()) / lerpSteps;
        double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
        setYRot(getYRot() + Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
        lerpSteps--;
        setPos(nx, ny, nz);
    }
}
