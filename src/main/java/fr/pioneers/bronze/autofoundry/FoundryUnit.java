package fr.pioneers.bronze.autofoundry;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.bronze.BronzeEvents;
import fr.pioneers.bronze.blast.BlastFurnaceTile;
import fr.pioneers.bronze.foundry.CrucibleUnit;
import fr.pioneers.bronze.foundry.Metals;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.forge.ForgeMetals;
import fr.pioneers.materials.GeneratedMaterials;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 4) : UNITE de la fonderie automatique (API Machine, portee par {@link AutoFoundryTile}, qui tient le cycle de vie du
 * multibloc). Comme le fourneau a creuset, mais en grand et sans la main :
 * <ul>
 *     <li>FOYER a charbon ({@link #isFuel} : charbon, charbon de bois, coke et leurs blocs ; pas de vapeur) : sans air il plafonne vers
 *     {@link #BASE_C} degres, l'air des soufflets (sur le controleur ou un foyer de la structure) le pousse jusqu'a {@code BASE_C + AIR_C} ;
 *     le coke chauffe plus que le charbon, le charbon plus que le charbon de bois ;</li>
 *     <li>CUVE : 3 cases de metal (1.0.5-beta lot 39 ; avant : 4) (entonnoir, cable logistique, par n'importe quelle piece), memes metaux que le fourneau a creuset ; la
 *     contenance et la vitesse de fusion et de coulee viennent de la taille ({@link FoundryLayout.Size}) ;</li>
 *     <li>SELECTEUR d'alliage (meme source {@code alloy:crucible} que le fourneau a creuset) : verrouillee, la cuve ne prend que les metaux de
 *     l'alliage et les DOSE ({@link Metals#dosed}) ; elle ne coule que cet alliage. Sans verrou elle attend que tout soit fondu avant de couler
 *     (un alliage n'est pas coule a moitie fait) ;</li>
 *     <li>COULEE dans les moules automatiques par les conduits ({@link ChannelNetwork} : le premier moule libre, puis le suivant), a
 *     {@link #POUR_RATE} mB/t x vitesse ; interrupteur « Auto » ; un signal redstone sur le controleur suspend la coulee.</li>
 * </ul>
 * 1.0.3-beta (lot 7b, « plus aucun bain bloque ») : regle commune de {@link Metals} (comme le fourneau a creuset) : la coulee prend la plus
 * grande part de l'alliage choisi, ou sans verrou de l'alliage qui prend le plus du bain, et laisse le surplus (qui coule ensuite) ; chaque
 * part essaie les moules ({@link #tryPour}) ; trop-plein ({@link #overflow}) ; un metal par case pour les entonnoirs, cables et robots ;
 * bouton « Vider le bain » ({@link #dump}).
 * <p>
 * Sauvegarde : {@code slots} (tile), {@code mix}, {@code t}, {@code auto}, {@code melting}, {@code burn}, {@code burnMax}, {@code bonus}, verrou,
 * coulee en cours ({@code pourMetal}, {@code pourLeft}, {@code pourTarget}).
 */
public class FoundryUnit extends MachineUnit {

    public static final int BASE_C = 950, AIR_C = 650, POUR_RATE = 12;
    /** 1.0.5-beta (lot 39, retour 51) : cases de metal de la cuve (3, comme le fourneau a creuset ; avant : 4). */
    public static final int METAL_SLOTS = fr.pioneers.bronze.foundry.CrucibleFurnaceTile.METAL_SLOTS;
    /** Interface v2 : combustible sous la flamme, carte du bain, 4 cases de metal en 2 x 2, case « Recette » (memes places que le fourneau a creuset). */
    public static final int FUEL_X = 30, FUEL_Y = StandardLayout.TOP + 40, IN_X = 152, IN_Y = StandardLayout.TOP + 6, RECIPE_X = 161, RECIPE_Y = 73;
    public static final StandardLayout.Rect BATH = StandardLayout.ageCard(52, 94);
    /** Valeurs nommees propres : taille, contenance, moules atteints, moules libres. */
    public static final UiValues.Key SIZE = UiValues.Key.of("foundry_size"), CAPACITY = UiValues.Key.of("foundry_capacity"),
          MOLDS = UiValues.Key.of("foundry_molds"), FREE = UiValues.Key.of("foundry_free");
    /** Etats : 0 coule, 1 non formee, 2 sans feu, 3 chauffe, 4 attend que tout fonde, 5 aucun moule, 6 moules occupes, 7 melange sans alliage,
     * 8 suspendue (redstone ou interrupteur), 9 vide, 10 pret (rien a couler encore : dosage). */
    public static final int POURING = 0, UNFORMED = 1, NO_FIRE = 2, HEATING = 3, MELTING = 4, NO_MOLD = 5, MOLDS_BUSY = 6, MIXED = 7, PAUSED = 8,
          EMPTY = 9, DOSING = 10;
    /** Source du selecteur : la meme que le fourneau a creuset (metaux purs et alliages, {@code pioneers:alloy/<metal>}). */
    public static final String SOURCE = "alloy:crucible";

    private final AutoFoundryTile tile;
    final ItemSlot fuel;
    final List<ItemSlot> inputs = new ArrayList<>();
    private final int[] melting = new int[METAL_SLOTS];
    /** 1.0.5-beta (lot 39) : 4e case de metal d'une fonderie d'avant, rangee au premier tick ou posee au sol ({@link CrucibleUnit#placeLegacy}). */
    private final List<ItemStack> legacy = new ArrayList<>();
    private final Map<String, Integer> mix = new LinkedHashMap<>();
    final RecipeLock lock = new RecipeLock();
    private double temperature = 20, air, airSeen;
    private int burn, burnMax, bonus;
    private boolean autoPour = true;
    private int status = UNFORMED;
    // Coulee en cours (le metal est deja sorti du bain)
    private String pourMetal = "";
    private int pourLeft;
    @Nullable
    private BlockPos pourTarget;
    @Nullable
    private List<BlockPos> pourPath;
    // Interface (recalculee toutes les secondes)
    private int molds, free;

    /** 1.0.5-beta (lot 29) : garde des envois periodiques (etat entier compare). */
    private final fr.pioneers.core.block.StateWatch watch = new fr.pioneers.core.block.StateWatch();

    public FoundryUnit(UnitHost machine, AutoFoundryTile tile) {
        super(machine);
        this.tile = tile;
        fuel = machine.slot(new ItemSlot(() -> 64, FoundryUnit::isFuel, (s, a) -> true, (s, a) -> !a.external(), machine::changed, FUEL_X, FUEL_Y));
        for (int i = 0; i < METAL_SLOTS; i++) {
            int[] p = inPos(i);
            int index = i;
            inputs.add(machine.slot(new ItemSlot(() -> 64, s -> meltable(s) && Metals.allowedBy(lockedMetal(), metalOf(s)),
                  (s, a) -> !a.external() || CrucibleUnit.automationFits(inputs, index, s, lockedMetal()), (s, a) -> !a.external(), machine::changed,
                  p[0], p[1])));
        }
    }

    /** Case de metal n (0..2) : memes places que le fourneau a creuset (deux cases, la 3e centree dessous). */
    public static int[] inPos(int i) {
        return i < 2 ? new int[]{IN_X + i * StandardLayout.SLOT, IN_Y} : new int[]{IN_X + StandardLayout.SLOT / 2, IN_Y + 24};
    }

    /** Combustible du foyer : charbon, charbon de bois (tag {@code minecraft:coals}), coke, blocs de charbon et de charbon de bois. */
    public static boolean isFuel(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.COALS)) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && Pioneers.MODID.equals(id.getNamespace()) && (id.getPath().equals("coke") || id.getPath().equals("coal_block")
              || id.getPath().equals("charcoal_block"));
    }

    /** Bonus de temperature d'un combustible : coke +100, charbon +60, charbon de bois +30 (degres). */
    public static int bonusOf(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String path = id == null ? "" : id.getPath();
        if (path.equals("coke")) {
            return 100;
        }
        if (stack.is(PiVanillaMaterials.CHARCOAL.get()) || path.contains("charcoal")) {
            return 30;
        }
        return 60;
    }

    /** Objet qui fond dans la cuve : objet de la table de fusion dont le metal se fond au creuset (memes metaux que le fourneau a creuset). */
    public static boolean meltable(ItemStack stack) {
        GeneratedMaterials.Melt melt = stack.isEmpty() ? null : ForgeMetals.meltOf(stack);
        return melt != null && ForgeMetals.crucible(melt.metal());
    }

    private static String metalOf(ItemStack stack) {
        GeneratedMaterials.Melt melt = stack.isEmpty() ? null : ForgeMetals.meltOf(stack);
        return melt == null ? "" : melt.metal();
    }

    /** Energie motrice du feu (temperature de la cuve, part du foyer), pretee par la tile. */
    public PowerSource.Fuel power() {
        return new PowerSource.Fuel(() -> temperature, this::burnRatio);
    }

    // ------------------------------------------------------------------
    // Etat
    // ------------------------------------------------------------------

    private FoundryLayout.Size size() {
        return tile.size();
    }

    public int capacity() {
        return size().capacity();
    }

    public double temperature() {
        return temperature;
    }

    public boolean lit() {
        return burn > 0;
    }

    public double burnRatio() {
        return burnMax <= 0 ? 0 : burn / (double) burnMax;
    }

    public double air() {
        return Math.min(1, air);
    }

    /** Temperature que le foyer peut atteindre maintenant (air et combustible compris). */
    public double reachable() {
        return lit() && tile.formed() ? BASE_C + AIR_C * Math.min(1, air) + bonus : 20;
    }

    public Map<String, Integer> mix() {
        return mix;
    }

    public int total() {
        return mix.values().stream().mapToInt(Integer::intValue).sum();
    }

    public String result() {
        return Metals.resolve(mix);
    }

    public boolean molten() {
        return !mix.isEmpty() && temperature >= Metals.pourC(mix);
    }

    public int status() {
        return status;
    }

    public boolean pouring() {
        return pourLeft > 0;
    }

    public String pourMetal() {
        return pourMetal;
    }

    /** mB de la coulee en cours pas encore verses (sortis du bain). */
    public int pourLeft() {
        return pourLeft;
    }

    public boolean autoPour() {
        return autoPour;
    }

    public void setAutoPour(boolean on) {
        if (autoPour != on) {
            autoPour = on;
            machine.markSync();
            machine.changed();
        }
    }

    void blast(double strength) {
        airSeen = Math.min(3, airSeen + strength);
    }

    /** GameTests : met la cuve a cette temperature (elle retombe ensuite vers ce que le feu permet). */
    public void setTemperature(double c) {
        temperature = c;
    }

    // ------------------------------------------------------------------
    // Selecteur d'alliage (meme source que le fourneau a creuset)
    // ------------------------------------------------------------------

    public String lockedMetal() {
        ResourceLocation id = lock.id();
        return id != null && id.getPath().startsWith("alloy/") ? id.getPath().substring("alloy/".length()) : "";
    }

    /** Ce qui coulerait maintenant (1.0.3-beta : premiere part du plan de la regle commune) ; vide : rien. */
    public String pourable() {
        Metals.Plan plan = plan();
        return plan.pours() ? plan.metal() : "";
    }

    /** 1.0.3-beta (lot 7b) : plan de coulee (interface ; les moules sont essayes a la coulee). */
    public Metals.Plan plan() {
        return Metals.plan(mix, lockedMetal(), overflow(), m -> 0);
    }

    /** 1.0.3-beta (lot 7b) : trop-plein (verrou pose, une case attend un metal admis qui ne tient plus dans la cuve) : le surplus coule aussi. */
    public boolean overflow() {
        if (lockedMetal().isEmpty()) {
            return false;
        }
        int total = total();
        for (ItemSlot slot : inputs) {
            Need need = slot.isEmpty() ? null : need(slot.getStack());
            if (need != null && total + need.add() > capacity()) {
                return true;
            }
        }
        return false;
    }

    /** 1.0.3-beta (lot 7b) : metal que le verrou attend avant que cette case fonde (« attend de l'etain ») ; vide si elle fond. */
    public String waitsFor(ItemStack stack) {
        Need need = stack.isEmpty() ? null : need(stack);
        if (need == null || Metals.dosed(mix, lockedMetal(), need.adds())) {
            return "";
        }
        return Metals.waitingFor(mix, lockedMetal());
    }

    /** Objet de la case de metal n (0..3) (interface). */
    public ItemStack input(int i) {
        return inputs.get(i).getStack();
    }

    /** 1.0.3-beta (lot 7b) : « Vider le bain » (coulee en cours comprise) : lingots, pepites et scorie ; rien ne se perd. */
    public List<ItemStack> dump() {
        List<ItemStack> out = spill();
        machine.markSync();
        machine.changed();
        return out;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = pos();
        if (!legacy.isEmpty()) {
            CrucibleUnit.placeLegacy(level, pos, inputs, legacy);
            machine.changed();
        }
        boolean formed = tile.formed();
        int speed = size().speed;
        air = air * 0.9 + airSeen * 0.1;
        airSeen = 0;
        boolean work = formed && (!mix.isEmpty() || pourLeft > 0 || inputs.stream().anyMatch(s -> !s.isEmpty()));
        fire(work, speed);
        double target = reachable();
        temperature += (target - temperature) * (target > temperature ? 0.006 : 0.003);
        if (formed) {
            for (int i = 0; i < inputs.size(); i++) {
                meltSlot(level, i, speed);
            }
        }
        if (pourLeft > 0) {
            pourTick(level, speed);
        } else if (level.getGameTime() % 10 == 0) {
            tryPour(level);
        }
        if (level.getGameTime() % 20 == 0) {
            if (formed) {
                String metal = pourable();
                ChannelNetwork.Census c = ChannelNetwork.census(level, tile.vats(), metal);
                molds = c.molds();
                free = c.free();
            } else {
                molds = free = 0;
            }
            status = status(level, formed);
            //1.0.5-beta (lot 29) : sauvegarde et envoi au client seulement si l'etat a change (avant : chaque seconde, meme a l'arret)
            if (watch.changed(tile)) {
                machine.markSync();
            }
        }
        if (lit() && air > 0.2 && level.getGameTime() % 4 == 0) {
            level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 1, 0.15, 0.02, 0.15, 0.01);
        }
        //1.0.5-beta (lot 13) : ambiance commune des fours primitifs (crepitement discret du feu, BlastFurnaceTile.crackle)
        if (lit() && formed) {
            BlastFurnaceTile.crackle(level, pos, temperature);
        }
    }

    /** Foyer : le combustible brule plus vite dans une grande fonderie et sous le vent des soufflets. */
    private void fire(boolean wanted, int speed) {
        if (burn > 0) {
            burn -= Math.max(1, (int) Math.round(speed * (1 + Math.min(1, air) * 0.6)));
            if (burn <= 0) {
                burn = 0;
            }
        }
        if (burn <= 0 && wanted && !fuel.isEmpty()) {
            ItemStack stack = fuel.getStack();
            int time = ForgeHooks.getBurnTime(stack, null);
            if (time > 0) {
                burn = burnMax = time;
                bonus = bonusOf(stack);
                ItemStack rest = stack.getCraftingRemainingItem();
                stack = stack.copy();
                stack.shrink(1);
                fuel.setStack(stack.isEmpty() ? rest : stack);
                machine.changed();
            }
        }
    }

    private int status(ServerLevel level, boolean formed) {
        if (!formed) {
            return UNFORMED;
        }
        if (pourLeft > 0) {
            return POURING;
        }
        if (mix.isEmpty()) {
            return inputs.stream().anyMatch(s -> !s.isEmpty()) ? lit() ? HEATING : NO_FIRE : EMPTY;
        }
        if (!molten()) {
            return lit() ? HEATING : NO_FIRE;
        }
        Metals.Plan plan = plan();
        if (!plan.pours()) {
            return plan.reason() == Metals.WHY_WAITING ? DOSING : MIXED;
        }
        if (!autoPour || level.hasNeighborSignal(pos())) {
            return PAUSED;
        }
        if (lockedMetal().isEmpty() && inputsMelting()) {
            return MELTING;
        }
        return molds == 0 ? NO_MOLD : MOLDS_BUSY;
    }

    /** Ce que donne un objet d'une case en fondant : metaux ajoutes (mB) et temperature de fusion. */
    private record Need(Map<String, Integer> adds, int metalC, int add) {
    }

    @Nullable
    private static Need need(ItemStack stack) {
        GeneratedMaterials.Melt melt = stack.isEmpty() ? null : ForgeMetals.meltOf(stack);
        if (melt == null || !ForgeMetals.crucible(melt.metal())) {
            return null;
        }
        Map<String, Integer> adds = new LinkedHashMap<>();
        Metals.addTo(adds, melt.metal(), melt.mb());
        return new Need(adds, Metals.meltingC(melt.metal()), adds.values().stream().mapToInt(Integer::intValue).sum());
    }

    /** Vrai si une case de metal attend de fondre (fond ou fondra au feu actuel, et tient dans la cuve) : la coulee sans verrou l'attend. */
    public boolean inputsMelting() {
        double reach = reachable();
        int total = total();
        for (ItemSlot slot : inputs) {
            Need need = slot.isEmpty() ? null : need(slot.getStack());
            if (need != null && Metals.dosed(mix, lockedMetal(), need.adds()) && Math.max(temperature, reach) >= need.metalC()
                && total + need.add() <= capacity()) {
                return true;
            }
        }
        return false;
    }

    private void meltSlot(ServerLevel level, int i, int speed) {
        ItemSlot slot = inputs.get(i);
        Need need = need(slot.getStack());
        if (need == null) {
            melting[i] = 0;
            return;
        }
        if (temperature < need.metalC() || total() + need.add() > capacity() || !Metals.dosed(mix, lockedMetal(), need.adds())) {
            melting[i] = Math.max(0, melting[i] - 1);
            return;
        }
        melting[i] += speed;
        if (melting[i] >= meltTicks(need.add())) {
            melting[i] = 0;
            need.adds().forEach((m, v) -> Metals.addTo(mix, m, v));
            ItemStack rest = slot.getStack().copy();
            rest.shrink(1);
            slot.setStack(rest);
            level.playSound(null, pos(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.4F, 1.2F);
            machine.markSync();
            machine.changed();
        }
    }

    /** Duree de fusion d'un objet (ticks a la vitesse 1) : celle du fourneau a creuset. */
    public static int meltTicks(int mb) {
        return 20 + mb / 6;
    }

    // ------------------------------------------------------------------
    // Coulee
    // ------------------------------------------------------------------

    /** Essai de coulee (toutes les 10 ticks) : bain liquide et coulable, rien a attendre, un moule libre au bout des conduits. */
    private void tryPour(ServerLevel level) {
        if (!tile.formed() || !autoPour || level.hasNeighborSignal(pos()) || mix.isEmpty() || !molten()) {
            return;
        }
        if (lockedMetal().isEmpty() && inputsMelting()) {
            return;
        }
        //1.0.3-beta (lot 7b) : chaque part coulable (dans l'ordre de la regle commune) essaie les moules ; le surplus reste dans le bain
        ChannelNetwork.Target target = null;
        String metal = "";
        for (Metals.Portion p : Metals.pourable(mix, lockedMetal(), overflow())) {
            ChannelNetwork.Target t = ChannelNetwork.first(level, pos(), tile.vats(), p.metal());
            if (t != null && p.mb() >= t.wants()) {
                target = t;
                metal = p.metal();
                break;
            }
        }
        if (target == null) {
            return;
        }
        int took = Metals.pour(mix, metal, target.wants()).values().stream().mapToInt(Integer::intValue).sum();
        if (took <= 0) {
            return;
        }
        pourMetal = metal;
        pourLeft = took;
        pourTarget = target.mold();
        pourPath = target.path();
        level.playSound(null, pos().above(), SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.7F, 1.1F);
        machine.markSync();
        machine.changed();
    }

    private void pourTick(ServerLevel level, int speed) {
        if (pourTarget == null || !tile.formed()) {
            abortPour();
            return;
        }
        if (pourPath == null || level.getGameTime() % 10 == 0) {
            pourPath = ChannelNetwork.pathTo(level, tile.vats(), pourTarget);
        }
        if (pourPath == null || !(level.getBlockEntity(pourTarget) instanceof AutoMoldTile mold)) {
            abortPour();
            return;
        }
        int took = mold.mold().receive(pourMetal, Math.min(pourLeft, POUR_RATE * speed));
        if (took <= 0) {
            abortPour();
            return;
        }
        pourLeft -= took;
        for (int i = 0; i < pourPath.size(); i++) {
            if (level.getBlockEntity(pourPath.get(i)) instanceof CastingChannelTile channel) {
                channel.flow(pourMetal, 12, i == pourPath.size() - 1);
            }
        }
        if (pourLeft <= 0) {
            BronzeEvents.award(level, pos(), "auto_pour");
            BronzeEvents.award(level, pos(), "pour_" + pourMetal);
            pourLeft = 0;
            pourMetal = "";
            pourTarget = null;
            pourPath = null;
            machine.markSync();
        }
        machine.changed();
    }

    /** Coulee interrompue (moule ou conduit retire, structure rompue) : le metal pas encore verse retourne dans le bain. */
    void abortPour() {
        if (pourLeft > 0 && !pourMetal.isEmpty()) {
            Metals.addTo(mix, pourMetal, pourLeft);
        }
        pourLeft = 0;
        pourMetal = "";
        pourTarget = null;
        pourPath = null;
        machine.markSync();
        machine.changed();
    }

    /** Casse du controleur : le bain (et la coulee en cours) rend des lingots et des pepites (rien ne se perd). */
    List<ItemStack> spill() {
        abortPour();
        List<ItemStack> out = new ArrayList<>();
        mix.forEach((m, v) -> out.addAll(ForgeMetals.asIngotsAndNuggets(m, v)));
        mix.clear();
        return out;
    }

    // ------------------------------------------------------------------
    // Interface : feu, accessible, combustible, air, total, fusion des cases, coulee auto, taille, contenance, moules, libres, etat
    // ------------------------------------------------------------------

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.HEAT, () -> Math.round(temperature * 10)).add(UiValues.REACH, () -> Math.round(reachable() * 10))
              .add(UiValues.BURN, () -> Math.round(burnRatio() * 1000)).add(UiValues.BLAST, () -> Math.round(Math.min(1, air) * 1000))
              .add(UiValues.MOLTEN, this::total);
        for (int i = 0; i < METAL_SLOTS; i++) {
            int index = i;
            values.add(UiValues.melt(i), () -> {
                Need n = need(inputs.get(index).getStack());
                return melting[index] * 1000L / Math.max(1, meltTicks(n == null ? Metals.INGOT : n.add()));
            });
        }
        values.add(UiValues.AUTO, () -> autoPour ? 1 : 0).add(SIZE, () -> tile.formed() ? size().ordinal() : -1).add(CAPACITY, this::capacity)
              .add(MOLDS, () -> molds).add(FREE, () -> free).add(UiValues.STATUS, () -> status);
    }

    // ------------------------------------------------------------------
    // Client, sauvegarde
    // ------------------------------------------------------------------

    @Override
    public void writeClient(CompoundTag tag) {
        CompoundTag m = new CompoundTag();
        mix.forEach(m::putInt);
        tag.put("mix", m);
        tag.putDouble("t", temperature);
        tag.putBoolean("auto", autoPour);
        tag.putString("pourMetal", pourMetal);
        tag.putInt("pourLeft", pourLeft);
        lock.writeClient(tag);
    }

    @Override
    public void readClient(CompoundTag tag) {
        mix.clear();
        CompoundTag m = tag.getCompound("mix");
        for (String key : m.getAllKeys()) {
            Metals.addTo(mix, key, m.getInt(key));
        }
        temperature = tag.contains("t") ? tag.getDouble("t") : 20;
        autoPour = !tag.contains("auto") || tag.getBoolean("auto");
        pourMetal = tag.getString("pourMetal");
        pourLeft = tag.getInt("pourLeft");
        lock.readClient(tag);
    }

    @Override
    public void save(CompoundTag tag) {
        writeClient(tag);
        tag.remove(RecipeLock.NBT);
        lock.save(tag);
        tag.putIntArray("melting", melting);
        tag.putInt("burn", burn);
        tag.putInt("burnMax", burnMax);
        tag.putInt("bonus", bonus);
        if (pourTarget != null) {
            tag.putLong("pourTarget", pourTarget.asLong());
        }
        CrucibleUnit.saveLegacy(tag, legacy);
    }

    @Override
    public void load(CompoundTag tag) {
        readClient(tag);
        lock.load(tag);
        int[] p = tag.getIntArray("melting");
        System.arraycopy(p, 0, melting, 0, Math.min(p.length, melting.length));
        burn = tag.getInt("burn");
        burnMax = tag.getInt("burnMax");
        bonus = tag.getInt("bonus");
        pourTarget = tag.contains("pourTarget") ? BlockPos.of(tag.getLong("pourTarget")) : null;
        //1.0.5-beta (lot 39) : cases au-dela des 4 d'aujourd'hui (combustible, 3 metaux) = la 4e case de metal d'avant
        CrucibleUnit.loadLegacy(tag, 1 + METAL_SLOTS, legacy);
        pourPath = null;
        if (pourTarget == null && pourLeft > 0) {
            Metals.addTo(mix, pourMetal, pourLeft);
            pourLeft = 0;
            pourMetal = "";
        }
    }
}
