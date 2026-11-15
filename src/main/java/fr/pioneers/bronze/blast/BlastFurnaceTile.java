package fr.pioneers.bronze.blast;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.FurnaceHeat;
import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.bronze.BronzeBlock;
import fr.pioneers.bronze.BronzeEvents;
import fr.pioneers.bronze.BronzeTile;
import fr.pioneers.bronze.HouseControllerBlock;
import fr.pioneers.bronze.LitBronzeBlock;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.industry.multi.Structure;
import fr.pioneers.registries.PiBronze;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Haut fourneau primitif, le trou de coulee (controleur) au pied de la tour. 1.0.4-beta (lot 24) : forme de Guillaume ({@link BlastLayout} :
 * socle de foyers en briques refractaires, une tuyere a l'arriere, controleur plein sans rigole). 0.20.40 : tour qui s'affine (socle de 5 x 5,
 * cuve de 3 x 3 cerclee de fer, couronne de merlons ; 1 a 3 tuyeres) ; les briques prennent leur role a la formation et rougeoient avec le
 * four ; le rendu du controleur anime la charge, les flammes du gueulard et la coulee ({@code client/bronze/BlastFurnaceRenderer}). On jette la
 * charge par le haut (ou dans ses cases, ou par entonnoir). Charge : fer (minerai, concasse, fragments,
 * lingots), charbon de bois, chaux vive. 1.0.4-beta (lot 9, Guillaume : « Il faut que le haut fourneau primitif implemente 3 slot d'items ») :
 * TROIS cases d'entree, une par role ({@link #role}) : fer (0), carbone (1), fondant (2) ; chacune n'accepte que son role (joueur, entonnoirs,
 * cables : chaque objet va dans SA case) ; anciennes sauvegardes (une case {@code charge} + acier + laitier) relues sans perte ({@link #migrate}).
 * Temperature d'equilibre {@code 300 + 1350 x racine(air / tuyeres)} (avant le lot 24 : air / 3, trois tuyeres au plus) : chaque tuyere
 * soufflee a plein (un soufflet, ou une soufflerie a vapeur) porte le four vers 1 650 degres ; la structure de Guillaume n'a qu'UNE tuyere,
 * un soufflet suffit donc, comme trois soufflets avant ; au-dela de 1 500 le fer devient acier.
 */
public class BlastFurnaceTile extends BronzeTile implements MultiblockCore.House.Holder, MultiblockPort {

    public static final double STEEL_C = 1500, MAX_C = 1700;
    /**
     * 1.0.5-beta (lot 13, Guillaume : « le haut fourneau primitif doit cuire l'acier plus longtemps (40 s par acier) ; moins de bruit ; un
     * crepitement et non un souffle ») : 800 ticks par lingot (avant : 100 a 200 selon la chaleur) ; ambiance : crepitement du feu
     * (evenements vanilla {@code block.campfire.crackle} / {@code block.fire.ambient}, volume {@link #CRACKLE_VOLUME}, toutes les
     * {@link #CRACKLE_PERIOD} ticks au plus) au lieu du grondement de soufflerie toutes les 2,5 s.
     */
    public static final int STEEL_TICKS = 800, CRACKLE_PERIOD = 60;
    public static final float CRACKLE_VOLUME = 0.35F;
    public static final int IRON_PER_INGOT = 4, MAX_IRON = 256, MAX_CARBON = 64, MAX_FLUX = 32;
    /**
     * 1.0.4-beta (lot 9) : feu a la place de l'energie motrice ; trois colonnes (fer, carbone, fondant) = case d'entree en haut, nom court,
     * reserve en dessous ; fleche, acier, laitier (sorties inchangees).
     */
    public static final int[] INPUT_X = {28, 54, 80};
    public static final int INPUT_Y = StandardLayout.TOP + 1, LABEL_Y = StandardLayout.TOP + 20, STEEL_X = 136, STEEL_Y = StandardLayout.TOP + 22,
          SLAG_X = 154, SLAG_Y = StandardLayout.TOP + 22;
    /** Roles des cases d'entree (ordre des cases, des colonnes et des reserves). */
    public static final int ROLE_IRON = 0, ROLE_CARBON = 1, ROLE_FLUX = 2;
    /** Cles de langue des roles (nom court sous la case, infobulle de la case vide). */
    public static final String[] ROLE_KEYS = {"iron", "carbon", "flux"};
    /** Version des cases sauvees : 2 = trois entrees (1.0.4-beta lot 9) ; absente = une case de charge (avant). */
    public static final int SLOTS_VERSION = 2;
    public static final StandardLayout.Rect ARROW = new StandardLayout.Rect(102, StandardLayout.TOP + 26, 28, 8);
    /** Lot 8b : valeurs nommees aux indices d'avant. */
    public static final UiValues.Key IRON = UiValues.Key.of("iron"), CARBON = UiValues.Key.of("carbon"), FLUX = UiValues.Key.of("flux"),
          TUYERES = UiValues.Key.of("tuyeres");

    /** Lot 8b : jauge de la charge n (0 fer, 1 carbone, 2 fondant) ; 1.0.4-beta (lot 9) : sous la case du meme role. */
    public static StandardLayout.Rect chargeGauge(int n) {
        return new StandardLayout.Rect(INPUT_X[n] + 1, StandardLayout.TOP + 27, 14, StandardLayout.GAUGE_H - 27);
    }
    public static final int WORKING = 0, UNFORMED = 1, NO_TUYERE = 2, NO_FUEL = 3, TOO_COLD = 4, NO_IRON = 5, FULL = 6;
    private static final TagKey<Item> RAW_IRON = tag("forge:raw_materials/iron"), INGOT_IRON = tag("forge:ingots/iron"), DUST_IRON = tag("forge:dusts/iron");

    /** Cases d'entree par role (fer, carbone, fondant), puis les sorties. */
    private final ItemSlot[] inputs = new ItemSlot[3];
    private final ItemSlot steel, slag;
    /** Objets d'une ancienne case de charge qui n'ont pu aller nulle part (rendus devant le trou de coulee au tick suivant). */
    private final List<ItemStack> returned = new ArrayList<>();
    private final List<BlockPos> tuyeres = new ArrayList<>();
    /** Briques de la structure et leur role (peint a la formation). */
    private final Map<BlockPos, FireBricksBlock.Part> bricks = new LinkedHashMap<>();
    /** Lot 8a : cycle de vie commun des multiblocs maison (verification periodique ou a la demande, revalidee au chargement). */
    private final MultiblockCore.House house = new MultiblockCore.House(new MultiblockCore.House.Host() {
        @Override
        public net.minecraft.world.level.Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public net.minecraft.core.Direction front() {
            return facing();
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            if (level == null) {
                return null;
            }
            Direction f = facing();
            BlockPos fault = fault(Structure.View.of(level), worldPosition, f);
            if (fault != null) {
                return MultiblockCore.House.Survey.fail("gui.pioneers.multiblock.house.blast", fault, null);
            }
            List<BlockPos> cells = new ArrayList<>();
            for (BlastLayout.Cell c : BlastLayout.cells()) {
                if (c.kind() != BlastLayout.Kind.CONTROLLER && c.kind() != BlastLayout.Kind.AIR) {
                    cells.add(BlastLayout.at(worldPosition, f, c.x(), c.y(), c.z()));
                }
            }
            return MultiblockCore.House.Survey.ok(cells, null);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            ServerLevel server = (ServerLevel) level;
            Map<BlockPos, FireBricksBlock.Part> found = new LinkedHashMap<>();
            tuyeres.clear();
            Direction f = facing();
            Block brick = PiBronze.FIRE_BRICKS.getBlock(), tuyere = PiBronze.TUYERE.getBlock();
            for (BlastLayout.Cell c : BlastLayout.cells()) {
                if (c.kind() == BlastLayout.Kind.CONTROLLER) {
                    continue;
                }
                BlockPos p = BlastLayout.at(worldPosition, f, c.x(), c.y(), c.z());
                BlockState st = level.getBlockState(p);
                if (st.is(brick)) {
                    found.put(p, c.part());
                } else if (st.is(tuyere)) {
                    tuyeres.add(p);
                }
            }
            if (first) {
                heatLevel = -1;
                BronzeEvents.award(server, worldPosition, "blast_formed");
                markClient();
            }
            bricks.clear();
            bricks.putAll(found);
            paint(server);
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            tuyeres.clear();
            if (broken && !survey.removal()) {
                cool((ServerLevel) level);
                heatLevel = -1;
                markClient();
            }
        }

        @Override
        public void houseChanged() {
            markClient();
        }
    }, 40);
    /** Client : etat visible (rendu anime). */
    private boolean clientFormed, clientBurning;
    private int clientTemp = 20, clientFill, pour, sentTemp = -1000, sentFill = -1;
    private int iron, carbon, flux, fluxed, status = UNFORMED, check, heatLevel = -1, burnTimer;
    private double temperature = 20, air, airSmooth, progress;

    public BlastFurnaceTile(BlockPos pos, BlockState state) {
        super(PiBronze.BLAST_FURNACE_TILE.get(), pos, state);
        for (int r = 0; r < 3; r++) {
            int role = r;
            inputs[r] = slot(new ItemSlot(() -> 64, s -> role(s) == role, (s, a) -> true, (s, a) -> !a.external(), this::changed, INPUT_X[r], INPUT_Y)
                  .ghost(() -> ghost(role)));
        }
        steel = slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, STEEL_X, STEEL_Y));
        slag = slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, SLAG_X, SLAG_Y));
    }

    private static TagKey<Item> tag(String id) {
        return ItemTags.create(ResourceLocation.parse(id));
    }

    // ------------------------------------------------------------------
    // Charge
    // ------------------------------------------------------------------

    /** Ce qu'apporte un objet a la charge : {fer, carbone, fondant} par unite, ou null s'il n'a rien a faire ici. */
    @Nullable
    public static int[] kind(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(RAW_IRON) || stack.is(INGOT_IRON) || stack.is(DUST_IRON) || is(stack, "pioneers:crushed_iron")) {
            return new int[]{4, 0, 0};
        }
        if (is(stack, "pioneers:magnetite_fragment") || is(stack, "pioneers:hematite_fragment")) {
            return new int[]{1, 0, 0};
        }
        if (stack.is(PiVanillaMaterials.CHARCOAL.get()) || stack.is(PiVanillaMaterials.COAL.get())) {
            return new int[]{0, 1, 0};
        }
        if (stack.is(PiVanillaMaterials.COAL_BLOCK.asItem()) || stack.is(PiVanillaMaterials.CHARCOAL_BLOCK.asItem())) {
            return new int[]{0, 9, 0};
        }
        if (stack.is(PiBronze.QUICKLIME.get())) {
            return new int[]{0, 0, 1};
        }
        return null;
    }

    /** 1.0.4-beta (lot 9) : role d'un objet dans la charge (0 fer, 1 carbone, 2 fondant), -1 s'il n'a rien a faire ici. */
    public static int role(ItemStack stack) {
        int[] k = kind(stack);
        if (k == null) {
            return -1;
        }
        return k[0] > 0 ? ROLE_IRON : k[1] > 0 ? ROLE_CARBON : ROLE_FLUX;
    }

    /** Objet fantome de la case vide d'un role. */
    private static ItemStack ghost(int role) {
        return switch (role) {
            case ROLE_IRON -> new ItemStack(PiVanillaMaterials.item("raw_iron"));
            case ROLE_CARBON -> new ItemStack(PiVanillaMaterials.CHARCOAL.get());
            default -> new ItemStack(PiBronze.QUICKLIME.get());
        };
    }

    /** Case d'entree d'un role (GameTests). */
    public ItemSlot input(int role) {
        return inputs[role];
    }

    /** Reserves internes (fer, carbone, fondant) (GameTests). */
    public int[] reserves() {
        return new int[]{iron, carbon, flux};
    }

    public ItemSlot steelSlot() {
        return steel;
    }

    public ItemSlot slagSlot() {
        return slag;
    }

    /** GameTests (1.0.4-beta lot 9) : impose la temperature de la cuve (sans soufflets, elle redescend lentement vers l'equilibre). */
    public void testTemperature(double t) {
        temperature = t;
    }

    private static boolean is(ItemStack stack, String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id));
        return item != null && item != Items.AIR && stack.is(item);
    }

    /** Verse dans la charge ce qui y tient ; renvoie le reste. */
    private ItemStack absorb(ItemStack stack) {
        int[] k = kind(stack);
        if (k == null) {
            return stack;
        }
        int n = stack.getCount();
        int fit = n;
        if (k[0] > 0) {
            fit = Math.min(fit, (MAX_IRON - iron) / k[0]);
        }
        if (k[1] > 0) {
            fit = Math.min(fit, (MAX_CARBON - carbon) / k[1]);
        }
        if (k[2] > 0) {
            fit = Math.min(fit, (MAX_FLUX - flux) / k[2]);
        }
        if (fit <= 0) {
            return stack;
        }
        iron += k[0] * fit;
        carbon += k[1] * fit;
        flux += k[2] * fit;
        setChanged();
        ItemStack rest = stack.copy();
        rest.shrink(fit);
        return rest;
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    public Direction facing() {
        return getBlockState().getValue(BronzeBlock.FACING);
    }

    /** Lot 6 : structure formee (plan BlastLayout complet) ; GameTests de l'API Multibloc. */
    public boolean formed() {
        return house.formed();
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    public BlockPos center() {
        return BlastLayout.centre(worldPosition, facing());
    }

    /**
     * API Multibloc (lot 6) : premiere case fautive du plan {@link BlastLayout} (null : forme), trou de coulee en {@code controller} tourne
     * vers {@code f} ; monde reel ou factice (banc des multiblocs) : meme regle pour la tile et le banc.
     */
    @Nullable
    public static BlockPos fault(Structure.View level, BlockPos controller, Direction f) {
        Block brick = PiBronze.FIRE_BRICKS.getBlock(), tuyere = PiBronze.TUYERE.getBlock();
        for (BlastLayout.Cell c : BlastLayout.cells()) {
            if (c.kind() == BlastLayout.Kind.CONTROLLER) {
                continue;
            }
            BlockPos p = BlastLayout.at(controller, f, c.x(), c.y(), c.z());
            BlockState s = level.getBlockState(p);
            boolean good = switch (c.kind()) {
                case AIR -> s.isAir();
                case BRICK -> s.is(brick);
                case TUYERE -> s.is(tuyere);                                              //1.0.4-beta (lot 24) : une tuyere a sa place
                case FIREBOX -> s.is(PiBronze.FIRE_BRICK_FIREBOX.getBlock());             //1.0.4-beta (lot 24) : socle en foyers
                default -> true;
            };
            if (!good) {
                return p;
            }
        }
        return null;
    }

    /**
     * Palier de chauffe d'une brique de role {@code part} quand le four est au palier {@code h}. 1.0.5-beta (lot 23, chauffe homogene) : le
     * MEME pour toutes (avant : socle h - 2, couronne h - 1 : la structure n'avait pas une seule couleur). PUR (banc de la chauffe).
     */
    public static int heatOf(FireBricksBlock.Part part, int h) {
        return h;
    }

    /** Palier du four a {@code celsius} (0 froid sous 400, 1 tiede, 2 chaud des 900, 3 au rouge vif des 1 300). PUR. */
    public static int stepOf(double celsius) {
        return celsius < 400 ? 0 : celsius < 900 ? 1 : celsius < 1300 ? 2 : 3;
    }

    /**
     * Peint le role et la chaleur des briques de la structure (sans prevenir les voisins au-dela du necessaire). 1.0.5-beta (lot 23) : UN
     * palier pour toute la structure, au meme tick : briques, foyers en briques refractaires du socle, tuyere et controleur.
     */
    private void paint(ServerLevel level) {
        int h = heatLevel < 0 ? 0 : heatLevel;
        //1.0.4-beta (lot 24) : les foyers en briques refractaires du socle flambent avec le four ; 1.0.5-beta (lot 23) : au palier commun
        fr.pioneers.api.multiblock.HouseNbt.paintFire(level, worldPosition, facing(), BlastLayout.SHAPE, h);
        fr.pioneers.bronze.HeatPalette.paintAll(level, tuyeres, h);
        fr.pioneers.bronze.HeatPalette.paint(level, worldPosition, h);
        for (Map.Entry<BlockPos, FireBricksBlock.Part> e : bricks.entrySet()) {
            BlockState s = level.getBlockState(e.getKey());
            if (!s.hasProperty(FireBricksBlock.PART)) {
                continue;
            }
            BlockState want = s.setValue(FireBricksBlock.PART, e.getValue()).setValue(FireBricksBlock.HEAT, heatOf(e.getValue(), h));
            if (want != s) {
                level.setBlock(e.getKey(), want, Block.UPDATE_CLIENTS);
            }
        }
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    /** Temperature vers laquelle tend le four maintenant. */
    public double equilibrium() {
        if (!house.formed() || carbon <= 0) {
            return 20;
        }
        //1.0.4-beta (lot 24) : air rapporte au nombre de tuyeres de la structure (une seule) : un soufflet par tuyere = plein feu
        return 300 + 1350 * Math.sqrt(Math.min(1, airSmooth / BlastLayout.tuyeres()));
    }

    @Override
    public void serverTick(ServerLevel level) {
        house.tick();
        //1.0.5-beta (lot 15) : controleur connecte a la paroi formee (habillage des foyers voisins)
        HouseControllerBlock.sync(level, worldPosition, house.formed());
        //Air des tuyeres
        air = 0;
        for (BlockPos p : tuyeres) {
            if (level.getBlockEntity(p) instanceof TuyereTile t) {
                double a = t.drain();
                air += a;
                BlockState s = level.getBlockState(p);
                if (s.getValue(TuyereBlock.LIT) != (a > 0.05 && carbon > 0)) {
                    level.setBlock(p, s.setValue(TuyereBlock.LIT, a > 0.05 && carbon > 0), Block.UPDATE_CLIENTS);
                }
            }
        }
        airSmooth = airSmooth * 0.95 + air * 0.05;
        //1.0.4-beta (lot 9) : objets d'une ancienne case de charge sans place : rendus devant le trou de coulee
        if (!returned.isEmpty()) {
            Direction f = facing();
            for (ItemStack s : returned) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5 + f.getStepX() * 0.8, worldPosition.getY() + 0.3,
                      worldPosition.getZ() + 0.5 + f.getStepZ() * 0.8, s);
            }
            returned.clear();
            setChanged();
        }
        //Charge : les trois cases, puis ce qu'on jette dans la cuve
        for (ItemSlot in : inputs) {
            if (!in.isEmpty()) {
                in.setStack(absorb(in.getStack()));
            }
        }
        if (house.formed() && level.getGameTime() % 10 == 0) {
            BlockPos c = center();
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(c.above()).expandTowards(0, 3.5, 0))) {
                ItemStack rest = absorb(entity.getItem());
                if (rest.isEmpty()) {
                    entity.discard();
                } else {
                    entity.setItem(rest);
                }
            }
        }
        //Combustion et chaleur
        boolean burning = house.formed() && carbon > 0;
        if (burning && ++burnTimer >= 300) {
            burnTimer = 0;
            carbon--;
            setChanged();
        }
        temperature += (equilibrium() - temperature) * 0.003;
        //Acier
        int was = status;
        if (!house.formed()) {
            status = UNFORMED;
        } else if (tuyeres.isEmpty()) {
            status = NO_TUYERE;
        } else if (carbon <= 0) {
            status = NO_FUEL;
        } else if (iron < IRON_PER_INGOT) {
            status = NO_IRON;
        } else if (temperature < STEEL_C) {
            status = TOO_COLD;
        } else if (steel.getCount() >= 64 || slag.getCount() >= 64) {
            status = FULL;
        } else {
            status = WORKING;
            progress += 1.0 / STEEL_TICKS;
            if (progress >= 1) {
                progress = 0;
                tap(level);
            }
        }
        if (status != WORKING && status != TOO_COLD) {
            progress = Math.max(0, progress - 0.002);
        }
        //Spectacle : briques qui rougeoient, flammes au gueulard, grondement
        int h = stepOf(temperature);
        if (house.formed() && h != heatLevel) {
            heatLevel = h;
            paint(level);
        }
        LitBronzeBlock.setLit(level, worldPosition, getBlockState(), burning && temperature > 300);
        if (burning && temperature > 700 && level.getGameTime() % 5 == 0) {
            BlockPos top = center().above(BlastLayout.HEIGHT - 1);
            level.sendParticles(ParticleTypes.FLAME, top.getX() + 0.5, top.getY() + 0.05, top.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, top.getX() + 0.5, top.getY() + 0.4, top.getZ() + 0.5, 1, 0.15, 0.2, 0.15, 0.02);
        }
        if (burning && temperature > 700) {
            blastCrackle(level, center(), temperature);
        }
        //1.0.5-beta (lot 13) : qui entre dans la cuve d'un four allume se brule (FurnaceHeat)
        if (house.formed() && FurnaceHeat.due(level, worldPosition)) {
            FurnaceHeat.inside(level, HouseNbt.positions(worldPosition, facing(), BlastLayout.SHAPE, "I"), burning && temperature > 300);
        }
        if (was != status) {
            setChanged();
        }
        //Rendu anime : temperature, remplissage de la cuve, flamme (envoyes quand ils changent nettement)
        int fill = fill();
        if (Math.abs(temperature - sentTemp) > 15 || Math.abs(fill - sentFill) > 15 || (burning != clientBurning)) {
            clientBurning = burning;
            markClient();
        }
        if (pour > 0) {
            pour--;
        }
        flushClient(level, 10);
    }

    /**
     * 1.0.5-beta (lot 13) : AMBIANCE SONORE COMMUNE des fours primitifs (haut fourneau, four a coke, fonderie) : un crepitement de braises
     * de temps en temps (pas a chaque fois : 1 chance sur 2 toutes les {@link #CRACKLE_PERIOD} ticks), discret, un peu plus vif quand le four
     * est tres chaud. Evenements vanilla verifies : {@code block.campfire.crackle}, {@code block.fire.ambient}.
     */
    /**
     * 1.0.5-beta (lot 23, Guillaume : « le crepitement du haut fourneau primitif doit etre un peu plus fulgurant, celui du coke oven digne
     * d'un crepitement de charbon un peu intense ») : sons propres synthetises ({@code T:core/p105_lot23.py sounds}) : haut fourneau = salve
     * vive (souffle de flamme et claquements serres, ~1,4 s) toutes les {@link #BLAST_CRACKLE_PERIOD} ticks, 2 fois sur 3, volume
     * {@link #BLAST_CRACKLE_VOLUME} ; four a coke = crepitement dense de charbon (~2,2 s) toutes les {@link #COKE_CRACKLE_PERIOD} ticks, sans
     * trou, volume {@link #COKE_CRACKLE_VOLUME} (les salves se chevauchent : un crepitement continu). Fonderie : le crepitement commun d'avant.
     */
    public static final int BLAST_CRACKLE_PERIOD = 40, COKE_CRACKLE_PERIOD = 40;
    public static final float BLAST_CRACKLE_VOLUME = 0.55F, COKE_CRACKLE_VOLUME = 0.6F;

    public static void blastCrackle(ServerLevel level, BlockPos at, double temperature) {
        if (level.getGameTime() % BLAST_CRACKLE_PERIOD != Math.floorMod(at.asLong(), BLAST_CRACKLE_PERIOD) || level.random.nextInt(3) == 0) {
            return;
        }
        float pitch = 0.92F + level.random.nextFloat() * 0.16F + (float) Math.min(0.15, Math.max(0, temperature - 1000) / 4000);
        level.playSound(null, at.above(), fr.pioneers.registries.PiBronze.SOUND_BLAST_CRACKLE.get(), SoundSource.BLOCKS,
              BLAST_CRACKLE_VOLUME + level.random.nextFloat() * 0.1F, pitch);
    }

    public static void cokeCrackle(ServerLevel level, BlockPos at) {
        if (level.getGameTime() % COKE_CRACKLE_PERIOD != Math.floorMod(at.asLong(), COKE_CRACKLE_PERIOD)) {
            return;
        }
        level.playSound(null, at.above(), fr.pioneers.registries.PiBronze.SOUND_COKE_CRACKLE.get(), SoundSource.BLOCKS,
              COKE_CRACKLE_VOLUME + level.random.nextFloat() * 0.1F, 0.9F + level.random.nextFloat() * 0.2F);
    }

    public static void crackle(ServerLevel level, BlockPos at, double temperature) {
        if (level.getGameTime() % CRACKLE_PERIOD != Math.floorMod(at.asLong(), CRACKLE_PERIOD) || level.random.nextBoolean()) {
            return;
        }
        float pitch = 0.8F + level.random.nextFloat() * 0.3F + (float) Math.min(0.2, Math.max(0, temperature - 1000) / 3000);
        level.playSound(null, at.above(), level.random.nextInt(3) == 0 ? SoundEvents.FIRE_AMBIENT : SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
              CRACKLE_VOLUME + level.random.nextFloat() * 0.1F, pitch);
    }

    /** Remplissage de la cuve (0..1000) : fer et charbon par rapport a leur maximum. */
    private int fill() {
        return (int) Math.min(1000, 1000.0 * (iron / (double) MAX_IRON * 0.6 + carbon / (double) MAX_CARBON * 0.4));
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        sentTemp = (int) Math.round(temperature);
        sentFill = fill();
        house.write(tag);
        tag.putBoolean("formed", house.formed());
        tag.putBoolean("burning", house.formed() && carbon > 0);
        tag.putInt("t", sentTemp);
        tag.putInt("fill", sentFill);
        tag.putInt("pour", pour);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        house.read(tag);
        clientFormed = tag.getBoolean("formed");
        clientBurning = tag.getBoolean("burning");
        clientTemp = tag.getInt("t");
        clientFill = tag.getInt("fill");
        if (tag.getInt("pour") > 0) {
            pour = tag.getInt("pour");
        }
    }

    // Etat vu par le rendu (client)
    public boolean shownFormed() {
        return clientFormed;
    }

    public boolean shownBurning() {
        return clientBurning;
    }

    public int shownTemperature() {
        return clientTemp;
    }

    public float shownFill() {
        return clientFill / 1000F;
    }

    /** Coulee en cours (ticks restants, decompte cote client). */
    public int shownPour() {
        return pour;
    }

    @Override
    public void clientTick() {
        if (pour > 0) {
            pour--;
        }
        if (level == null || !clientFormed) {
            return;
        }
        //1.0.4-beta (lot 25) : flammes et fumee au gueulard (T:industry/nbt_models.py HOUSE_FX ; les flammes dessinees restent dans la cuve)
        houseFx("blast_furnace", clientBurning && clientTemp > 700);
        BlockPos c = center();
        if (clientBurning && clientTemp > 1100 && level.random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.LAVA, c.getX() + 0.5, c.getY() + BlastLayout.HEIGHT - 0.2, c.getZ() + 0.5, 0, 0, 0);
        }
        if (pour > 0 && level.random.nextInt(3) == 0) {
            Direction f = facing();
            level.addParticle(ParticleTypes.SMALL_FLAME, worldPosition.getX() + 0.5 + f.getStepX() * 0.75, worldPosition.getY() + 0.25,
                  worldPosition.getZ() + 0.5 + f.getStepZ() * 0.75, f.getStepX() * 0.02, 0.03, f.getStepZ() * 0.02);
        }
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos c = center();
        return new AABB(c).inflate(2.5, 0, 2.5).expandTowards(0, BlastLayout.HEIGHT + 2, 0);
    }

    private void tap(ServerLevel level) {
        iron -= IRON_PER_INGOT;
        carbon--;
        boolean withFlux = flux > 0;
        if (withFlux && ++fluxed >= 2) {
            fluxed = 0;
            flux--;
        }
        if (withFlux || level.random.nextFloat() < 0.75F) {
            steel.insert(new ItemStack(steelIngot()), Action.EXECUTE, Automation.INTERNAL);
            if (withFlux && fluxed == 0) {
                slag.insert(new ItemStack(PiBronze.SLAG.get()), Action.EXECUTE, Automation.INTERNAL);
            }
        } else {
            slag.insert(new ItemStack(PiBronze.SLAG.get()), Action.EXECUTE, Automation.INTERNAL);
        }
        level.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8F, 0.7F);
        pour = 40;
        markClient();
        Direction f = facing();
        level.sendParticles(ParticleTypes.LAVA, worldPosition.getX() + 0.5 + f.getStepX() * 0.55, worldPosition.getY() + 0.4,
              worldPosition.getZ() + 0.5 + f.getStepZ() * 0.55, 4, 0.1, 0.05, 0.1, 0.02);
        BronzeEvents.award(level, worldPosition, "steel");
        setChanged();
    }

    private static Item steelIngot() {
        Item item = ForgeRegistries.ITEMS.getValue(Pioneers.rl("steel_ingot"));
        return item == null ? PiVanillaMaterials.item("iron_ingot") : item;
    }

    private static void give(Consumer<ItemStack> drop, Item item, int count) {
        while (count > 0 && item != Items.AIR) {
            int n = Math.min(count, item.getMaxStackSize());
            drop.accept(new ItemStack(item, n));
            count -= n;
        }
    }

    /** Les briques reprennent leur couleur et redeviennent de simples briques (structure rompue ou controleur casse). */
    private void cool(ServerLevel level) {
        fr.pioneers.api.multiblock.HouseNbt.paintFire(level, worldPosition, facing(), BlastLayout.SHAPE, 0);
        //1.0.5-beta (lot 23) : tuyere et controleur reprennent aussi leur couleur froide
        fr.pioneers.bronze.HeatPalette.paintAll(level, fr.pioneers.api.multiblock.HouseNbt.positions(worldPosition, facing(), BlastLayout.SHAPE, "T"), 0);
        if (level.getBlockState(worldPosition).getBlock() instanceof fr.pioneers.bronze.HouseControllerBlock) {
            fr.pioneers.bronze.HeatPalette.paint(level, worldPosition, 0);
        }
        for (BlockPos p : bricks.keySet()) {
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(FireBricksBlock.HEAT) && (s.getValue(FireBricksBlock.HEAT) != 0 || s.getValue(FireBricksBlock.PART) != FireBricksBlock.Part.NONE)) {
                level.setBlock(p, s.setValue(FireBricksBlock.HEAT, 0).setValue(FireBricksBlock.PART, FireBricksBlock.Part.NONE), Block.UPDATE_CLIENTS);
            }
        }
        bricks.clear();
    }

    @Override
    public void dropContents(Consumer<ItemStack> drop) {
        super.dropContents(drop);
        //La charge encore dans la cuve retombe en objets
        give(drop, PiVanillaMaterials.item("raw_iron"), iron / IRON_PER_INGOT);
        Item fragment = ForgeRegistries.ITEMS.getValue(Pioneers.rl("magnetite_fragment"));
        give(drop, fragment == null ? Items.AIR : fragment, iron % IRON_PER_INGOT);
        give(drop, PiVanillaMaterials.CHARCOAL.get(), carbon);
        give(drop, PiBronze.QUICKLIME.get(), flux);
        iron = carbon = flux = 0;
        if (level instanceof ServerLevel server && house.formed()) {
            cool(server);
        }
    }

    // ------------------------------------------------------------------
    // Interface : 0 T x10, 1 T d'equilibre x10, 2 air x1000, 3 fer, 4 carbone, 5 fondant, 6 avancement x1000, 7 etat, 8 tuyeres
    // ------------------------------------------------------------------

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    /** Lot 8a : interface standard v2 (inventaire dans le cadre, onglets a droite, Infos et Structure). */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public java.util.List<StandardLayout.Part> guiParts() {
        return StandardLayout.ageService(false, StandardLayout.framed("gauge", chargeGauge(0)), StandardLayout.framed("gauge", chargeGauge(1)),
              StandardLayout.framed("gauge", chargeGauge(2)), new StandardLayout.Part("arrow", ARROW));   //les 3 cases : guiSlots
    }

    @Override
    public UiValues uiValues() {
        return namedIndices(UiValues.HEAT, UiValues.REACH, UiValues.BLAST, IRON, CARBON, FLUX, UiValues.PROGRESS_X1000, UiValues.STATUS, TUYERES);
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    @Override
    public int guiValueCount() {
        return 9;
    }

    @Override
    public long guiValue(int index) {
        return switch (index) {
            case 0 -> Math.round(temperature * 10);
            case 1 -> Math.round(equilibrium() * 10);
            case 2 -> Math.round(airSmooth * 1000);
            case 3 -> iron;
            case 4 -> carbon;
            case 5 -> flux;
            case 6 -> Math.round(progress * 1000);
            case 7 -> status;
            default -> tuyeres.size();
        };
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("iron", iron);
        tag.putInt("carbon", carbon);
        tag.putInt("flux", flux);
        tag.putInt("fluxed", fluxed);
        tag.putDouble("temperature", temperature);
        tag.putDouble("progress", progress);
        tag.putInt("slots_v", SLOTS_VERSION);
        if (!returned.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (ItemStack s : returned) {
                list.add(ItemSlot.saveStack(s));
            }
            tag.put("returned", list);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        iron = tag.getInt("iron");
        carbon = tag.getInt("carbon");
        flux = tag.getInt("flux");
        fluxed = tag.getInt("fluxed");
        temperature = tag.contains("temperature") ? tag.getDouble("temperature") : 20;
        progress = tag.getDouble("progress");
        returned.clear();
        for (net.minecraft.nbt.Tag t : tag.getList("returned", 10)) {
            ItemStack s = ItemSlot.loadStack((CompoundTag) t);
            if (!s.isEmpty()) {
                returned.add(s);
            }
        }
        if (!tag.contains("slots_v")) {
            migrate(tag.getList("slots", 10));
        }
    }

    /**
     * 1.0.4-beta (lot 9) : MIGRATION d'une sauvegarde d'avant (cases 0 = charge, 1 = acier, 2 = laitier) : la charge va dans la case de SON
     * role, l'acier et le laitier dans leurs cases ; ce qui ne trouve pas de place (aucun cas connu) est rendu devant le trou de coulee.
     */
    public void migrate(net.minecraft.nbt.ListTag old) {
        for (ItemSlot s : slots) {
            s.load(new CompoundTag());
        }
        for (net.minecraft.nbt.Tag t : old) {
            CompoundTag entry = (CompoundTag) t;
            ItemStack stack = ItemSlot.loadStack(entry);
            if (stack.isEmpty()) {
                continue;
            }
            int index = entry.getShort("Slot");
            ItemSlot to = switch (index) {
                case 0 -> role(stack) < 0 ? null : inputs[role(stack)];
                case 1 -> steel;
                case 2 -> slag;
                default -> null;
            };
            if (to != null && to.isEmpty()) {
                to.load(entry);
            } else {
                returned.add(stack);
            }
        }
    }

    /**
     * Lot 8c : CONTRAT DE PORT COMMUN ({@link MultiblockPort#portHandler}) : un port commun pose dans la structure formee prete la capacite
     * interne de la maison ({@link MultiblockPort#capabilityOf}) ; les ports propres de la maison gardent leurs gestionnaires.
     */
    @Nullable
    @Override
    public Object portHandler(fr.pioneers.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() ? MultiblockPort.capabilityOf(this, type) : null;
    }
}
