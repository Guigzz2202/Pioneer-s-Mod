package fr.pioneers.tech.bronze.foundry;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.Hearth;
import fr.pioneers.tech.bronze.LitBronzeBlock;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.materials.GeneratedMaterials;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 2, notes de Guillaume : « le fourneau a creuset embarque un selecteur de recette afin de ne pas se tromper lors des alliages ;
 * la notion d'impurete est supprimee : soit on obtient l'alliage soit on n'obtient rien ; par defaut, sans filtre, les minerais se melangent
 * s'ils le peuvent ») : SELECTEUR DE RECETTE (carre standard, source {@link #SOURCE} : chaque metal pur et chaque alliage du creuset, aux
 * proportions de {@link Metals#RECIPES}) : verrouille sur un alliage, le fourneau ne prend que ses metaux et les DOSE (une case ne fond que
 * si son metal n'avance pas devant les autres, {@link #dosed}) ; il ne coule que cet alliage. Sans verrou, tout se melange : metal pur ou
 * alliage dans ses fourchettes ({@link Metals#resolve}), sinon rien ne coule (« melange sans alliage », a corriger). Plus de lingot impur.
 * <p>
 * 1.0.3-beta (lot 7b, « plus aucun bain bloque ») : regle commune de {@link Metals} : la coulee prend la plus grande part de l'alliage choisi
 * (ou, sans verrou, de l'alliage qui prend le plus du bain) au rapport ideal et laisse le surplus dans le bain ({@link #plan}) ; un bain plein
 * que le verrou ne fait plus avancer coule son surplus ({@link #overflow}) ; entonnoirs, cables et robots : un metal par case
 * ({@link #automationFits}) ; bouton « Vider le bain » ({@link #dump}) ; le bain casse avec le fourneau est rendu ({@link #spill}).
 * <p>
 * API Machine (lot 8b) : UNITE du fourneau a creuset, code de l'ancienne {@code CrucibleFurnaceTile} qui ne fait plus que la porter (bloc,
 * air des soufflets, rendu). Energie motrice : le FEU du foyer ({@link PowerSource.Fuel} : temperature et part du feu, pretes par la tile) :
 * sans air il plafonne vers {@link CrucibleFurnaceTile#BASE_C} degres, un soufflet le pousse jusqu'a 1 600 ; chaque objet fond a la
 * temperature de son metal ; le creuset melange tout (9 lingots au plus). Coulee dans la table posee devant : bouton « Couler », ou toute
 * seule (interrupteur « Auto », actif par defaut) des que la table a un moule et est vide, que le metal est liquide et qu'aucune case n'attend
 * de fondre ({@link #waits} : une case qui ne peut plus fondre ne bloque pas) ; un front montant redstone force la coulee.
 * <p>
 * Sauvegarde au format d'avant : {@code slots} (tile), foyer ({@code burn}, {@code burnMax}, {@code bonus}, {@code woody}), {@code mix},
 * {@code t}, {@code auto}, {@code melting} ; client : {@code mix}, {@code t}, {@code auto}. Cases : creuset, combustible, 4 metaux (ordre d'avant).
 */
public class CrucibleUnit extends MachineUnit {

    private final CrucibleFurnaceTile tile;
    final ItemSlot crucible, fuel;
    final List<ItemSlot> inputs = new ArrayList<>();
    private final int[] melting = new int[CrucibleFurnaceTile.METAL_SLOTS];
    /**
     * 1.0.5-beta (lot 39, retour 51 : le creuset passe a 3 entrees) : contenu de la 4e case de metal d'un fourneau d'avant (relu dans le NBT
     * {@code slots}, que les 3 cases ne lisent plus) : range au premier tick serveur dans une case de metal libre (ou une pile du meme objet),
     * sinon pose au sol ; garde sous {@code legacy_metal} si la tile est sauvee avant.
     */
    private final List<ItemStack> legacy = new ArrayList<>();
    private final Map<String, Integer> mix = new LinkedHashMap<>();
    private final Hearth hearth = new Hearth();
    private double temperature = 20, air, airSeen;
    private boolean powered;
    private boolean pulsePending;
    /** Coulee automatique dans la table (interrupteur de l'interface ; vrai par defaut, aussi pour un fourneau pose avant). */
    private boolean autoPour = true;
    /** 1.0.2-beta : source du selecteur (metaux et alliages du creuset) ; identifiants {@code pioneers:alloy/<metal>}. */
    public static final String SOURCE = "alloy:crucible";
    final RecipeLock lock = new RecipeLock();

    public CrucibleUnit(UnitHost machine, CrucibleFurnaceTile tile) {
        super(machine);
        this.tile = tile;
        crucible = machine.slot(new ItemSlot(() -> 1, s -> s.getItem() instanceof MoldItem m && m.shape.equals("crucible"), (s, a) -> !a.external(),
              (s, a) -> !a.external() && mix.isEmpty(), machine::changed, CrucibleFurnaceTile.CRUCIBLE_X, CrucibleFurnaceTile.CRUCIBLE_Y));
        fuel = machine.slot(new ItemSlot(() -> 64, s -> ForgeHooks.getBurnTime(s, null) > 0, (s, a) -> true, (s, a) -> !a.external(),
              machine::changed, CrucibleFurnaceTile.FUEL_X, CrucibleFurnaceTile.FUEL_Y));
        for (int i = 0; i < CrucibleFurnaceTile.METAL_SLOTS; i++) {
            int[] p = CrucibleFurnaceTile.inPos(i);
            int index = i;
            inputs.add(machine.slot(new ItemSlot(() -> 64, s -> CrucibleFurnaceTile.meltable(s) && lockAllowsMetal(s),
                  (s, a) -> !a.external() || automationFits(inputs, index, s, lockedMetal()), (s, a) -> !a.external(), machine::changed, p[0], p[1])));
        }
    }

    /** Energie motrice du feu (temperature du creuset, part du foyer), pretee par la tile. */
    public PowerSource.Fuel power() {
        return new PowerSource.Fuel(() -> temperature, hearth::ratio);
    }

    // ------------------------------------------------------------------
    // Etat
    // ------------------------------------------------------------------

    public double temperature() {
        return temperature;
    }

    /** Temperature que le foyer peut atteindre maintenant (air et combustible compris). */
    public double reachable() {
        return hearth.lit() ? CrucibleFurnaceTile.BASE_C + CrucibleFurnaceTile.AIR_C * Math.min(1, air) + hearth.bonus() : 20;
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

    // ------------------------------------------------------------------
    // 1.0.2-beta : selecteur de recette (alliage choisi), dosage
    // ------------------------------------------------------------------

    /** Metal ou alliage choisi au selecteur (vide : aucun verrou). */
    public String lockedMetal() {
        ResourceLocation id = lock.id();
        return id != null && id.getPath().startsWith("alloy/") ? id.getPath().substring("alloy/".length()) : "";
    }

    /** Identifiant de choix du selecteur pour ce metal ou alliage. */
    public static ResourceLocation choiceId(String metal) {
        return Pioneers.rl("alloy/" + metal);
    }

    /** Verrouille : seuls les metaux de l'alliage choisi (ou l'alliage lui-meme) entrent. */
    boolean lockAllowsMetal(ItemStack stack) {
        String locked = lockedMetal();
        if (locked.isEmpty()) {
            return true;
        }
        GeneratedMaterials.Melt melt = stack.isEmpty() ? null : ForgeMetals.meltOf(stack);
        return melt != null && (melt.metal().equals(locked) || Metals.components(locked).containsKey(melt.metal()));
    }

    /**
     * Dosage (verrou sur un alliage de {@link Metals#RECIPES}) : ajouter {@code adds} garde-t-il le bain sur ses proportions ? Oui si le metal
     * ajoute est le plus en retard (part / proportion la plus basse), ou s'il ne passe pas devant le plus avance. Sans verrou, ou verrou sur
     * un metal pur : toujours.
     */
    boolean dosed(Map<String, Integer> adds) {
        //1.0.2-beta (lot 4) : regle commune avec la fonderie automatique (Metals.dosed), meme code qu'avant
        return Metals.dosed(mix, lockedMetal(), adds);
    }

    /** Ce que le fourneau coulerait maintenant dans sa table (1.0.3-beta : plan de la regle commune) ; vide : rien. */
    public String pourable() {
        Metals.Plan plan = plan();
        return plan.pours() ? plan.metal() : "";
    }

    /**
     * 1.0.3-beta (lot 7b) : plan de coulee (interface et coulee) : regle commune {@link Metals#plan}, verrou du selecteur, trop-plein, mB que le
     * moule de la table demande pour chaque metal (sans table ni moule : toute quantite, pour l'affichage).
     */
    public Metals.Plan plan() {
        CastingTableTile table = tile.table();
        boolean molds = table != null && !table.moldSlot().isEmpty();
        return Metals.plan(mix, lockedMetal(), overflow(), m -> {
            if (!molds) {
                return 0;
            }
            CastingRecipe r = table.recipeFor(m);
            return r == null ? -1 : r.amount();
        });
    }

    /**
     * 1.0.3-beta (lot 7b) : TROP-PLEIN : verrou pose et une case attend un metal admis qui ne tient plus dans le creuset (bain plein d'un autre
     * metal, d'un bain d'avant...) : la coulee prend alors aussi le surplus, le bain n'est jamais bloque.
     */
    public boolean overflow() {
        if (lockedMetal().isEmpty() || crucible.isEmpty()) {
            return false;
        }
        int total = total();
        for (ItemSlot slot : inputs) {
            Need need = slot.isEmpty() ? null : need(slot.getStack());
            if (need != null && total + need.add() > CrucibleFurnaceTile.CAPACITY) {
                return true;
            }
        }
        return false;
    }

    /**
     * 1.0.3-beta (lot 7b) : entonnoirs, cables et robots (sauf verrou sur un metal pur) : jamais dans une case un metal deja present dans une
     * autre case (4 cases de cuivre et l'etain ne passait plus : fourneau bloque). Commun a la fonderie automatique.
     */
    public static boolean automationFits(List<ItemSlot> inputs, int index, ItemStack stack, String locked) {
        if (!locked.isEmpty() && !Metals.isAlloy(locked)) {
            return true;
        }
        String metal = metalOf(stack);
        for (int j = 0; j < inputs.size(); j++) {
            if (j != index && !inputs.get(j).isEmpty() && metal.equals(metalOf(inputs.get(j).getStack()))) {
                return false;
            }
        }
        return true;
    }

    /** Objet de la case de metal n (0..3) (interface). */
    public ItemStack input(int i) {
        return inputs.get(i).getStack();
    }

    /** Metal d'un objet de la table de fusion (vide s'il ne fond pas). */
    public static String metalOf(ItemStack stack) {
        GeneratedMaterials.Melt melt = stack.isEmpty() ? null : ForgeMetals.meltOf(stack);
        return melt == null ? "" : melt.metal();
    }

    /**
     * 1.0.3-beta (lot 7b) : pourquoi la case ne fond pas encore (interface) : metal que le verrou attend avant celui-ci (« attend de l'etain »),
     * vide si elle fond ou attend la chaleur.
     */
    public String waitsFor(ItemStack stack) {
        Need need = stack.isEmpty() ? null : need(stack);
        if (need == null || dosed(need.adds())) {
            return "";
        }
        return Metals.waitingFor(mix, lockedMetal());
    }

    /** 1.0.3-beta (lot 7b) : « Vider le bain » : le bain en lingots, pepites et scorie ({@link Metals#spill}) ; le creuset reste en place. */
    public List<ItemStack> dump() {
        List<ItemStack> out = Metals.spill(mix);
        machine.markSync();
        machine.changed();
        return out;
    }

    /** Casse du fourneau : le bain rendu (1.0.3-beta : il etait perdu). */
    public List<ItemStack> spill() {
        return Metals.spill(mix);
    }

    public RecipeLock recipeLock() {
        return lock;
    }

    public void recipeLockChanged() {
        machine.markSync();
        machine.changed();
    }

    public int[] recipeSlot() {
        return new int[]{CrucibleFurnaceTile.RECIPE_X, CrucibleFurnaceTile.RECIPE_Y};
    }

    public List<ItemStack> presentItems() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemSlot slot : inputs) {
            if (!slot.isEmpty()) {
                out.add(slot.getStack());
            }
        }
        return out;
    }

    public boolean molten() {
        return !mix.isEmpty() && temperature >= Metals.pourC(mix);
    }

    void blast(double strength) {
        airSeen = Math.min(1.5, airSeen + strength);
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

    /** Pour les GameTests : met le creuset a cette temperature (il retombe ensuite vers ce que le feu permet). */
    public void setTemperature(double c) {
        temperature = c;
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
            placeLegacy(level, pos, inputs, legacy);
            machine.changed();
        }
        air = air * 0.9 + airSeen * 0.1;
        airSeen = 0;
        //Front montant du signal redstone lu a chaque tick (une impulsion de bouton ou de comparateur durait moins que les 10 ticks de la
        //coulee automatique) ; memorise jusqu'a la prochaine tentative
        boolean signal = level.hasNeighborSignal(pos);
        if (signal && !powered) {
            pulsePending = true;
        }
        powered = signal;
        boolean work = !crucible.isEmpty() && (!mix.isEmpty() || anyInput());          //1.0.5-beta (lot 29) : sans flux alloue a chaque tick
        hearth.tick(fuel, work, 1 + air * 0.6);
        double target = reachable();
        temperature += (target - temperature) * (target > temperature ? 0.006 : 0.003);
        if (!crucible.isEmpty()) {
            for (int i = 0; i < inputs.size(); i++) {
                meltSlot(level, i);
            }
        }
        if (level.getGameTime() % 10 == 0) {
            //1.0.5-beta (lot 29) : sauvegarde et envoi au client seulement si l'etat a change (avant : toutes les 10 ticks, meme four froid et vide)
            if (watch.changed(tile)) {
                machine.markSync();
            }
            autoPour(level);
        }
        if (hearth.lit() && air > 0.2 && level.getGameTime() % 4 == 0) {
            level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.15, 0.02, 0.15, 0.01);
        }
        LitBronzeBlock.setLit(level, pos, machine.state(), hearth.lit());
    }

    private boolean anyInput() {
        for (ItemSlot s : inputs) {
            if (!s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Ce que donne un objet d'une case en fondant : metaux ajoutes (mB) et temperature de fusion. */
    private record Need(Map<String, Integer> adds, int metalC, int add) {
    }

    @Nullable
    private static Need need(ItemStack stack) {
        Map<String, Integer> adds = new LinkedHashMap<>();
        GeneratedMaterials.Melt melt = stack.isEmpty() ? null : ForgeMetals.meltOf(stack);
        if (melt == null || !ForgeMetals.crucible(melt.metal())) {
            return null;
        }
        //Un alliage fondu compte comme ses composants (le dosage les voit)
        Metals.addTo(adds, melt.metal(), melt.mb());
        int metalC = Metals.meltingC(melt.metal());
        return new Need(adds, metalC, adds.values().stream().mapToInt(Integer::intValue).sum());
    }

    /**
     * Vrai si la case attend de fondre : un objet qui fond maintenant ou fondra au feu actuel (temperature atteinte ou atteignable) et qui tient
     * dans le creuset. Une case qui ne peut pas fondre (creuset plein, metal trop refractaire pour ce feu) n'attend rien : elle ne retient pas
     * la coulee automatique.
     */
    static boolean waits(double temperature, double reachable, int total, int metalC, int add) {
        return Math.max(temperature, reachable) >= metalC && total + add <= CrucibleFurnaceTile.CAPACITY;
    }

    /** Vrai si une des cases de metal attend de fondre (voir {@link #waits}). */
    public boolean inputsMelting() {
        if (crucible.isEmpty()) {
            return false;
        }
        double reach = reachable();
        int total = total();
        for (ItemSlot slot : inputs) {
            Need need = slot.isEmpty() ? null : need(slot.getStack());
            if (need != null && dosed(need.adds()) && waits(temperature, reach, total, need.metalC(), need.add())) {
                return true;
            }
        }
        return false;
    }

    private void meltSlot(ServerLevel level, int i) {
        ItemSlot slot = inputs.get(i);
        ItemStack stack = slot.getStack();
        Need need = need(stack);
        if (need == null) {
            melting[i] = 0;
            return;
        }
        Map<String, Integer> adds = need.adds();
        int metalC = need.metalC();
        int add = need.add();
        if (temperature < metalC || total() + add > CrucibleFurnaceTile.CAPACITY || !dosed(adds)) {
            melting[i] = Math.max(0, melting[i] - 1);
            return;
        }
        if (++melting[i] >= 20 + add / 6) {
            melting[i] = 0;
            adds.forEach((m, v) -> Metals.addTo(mix, m, v));
            ItemStack rest = stack.copy();
            rest.shrink(1);
            slot.setStack(rest);
            level.playSound(null, pos(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.4F, 1.2F);
            machine.markSync();
            machine.changed();
        }
    }

    // ------------------------------------------------------------------
    // Coulee
    // ------------------------------------------------------------------

    /**
     * Coulee automatique (forge primaire 0.21), essayee toutes les 10 ticks : table prete (moule, vide), metal liquide en quantite, recette ;
     * sans signal redstone il faut aussi que l'interrupteur soit allume et qu'aucune case n'attende de fondre (1.0.1-beta : une case qui ne
     * peut plus fondre ne bloque plus, voir {@link #waits}). Les objets des cases ne sont jamais sortis.
     */
    private void autoPour(ServerLevel level) {
        boolean pulse = pulsePending;
        pulsePending = false;
        if (crucible.isEmpty() || mix.isEmpty() || !molten() || !pulse && (!autoPour || inputsMelting())) {
            return;
        }
        CastingTableTile table = tile.table();
        if (table == null || table.moldSlot().isEmpty() || table.busy()) {
            return;
        }
        if (plan().pours()) {
            pour(level);
        }
    }

    Component pour(ServerLevel level) {
        CastingTableTile table = tile.table();
        if (table == null) {
            return Component.translatable("foundry.pioneers.no_table").withStyle(ChatFormatting.RED);
        }
        if (table.moldSlot().isEmpty()) {
            return Component.translatable("foundry.pioneers.no_mold").withStyle(ChatFormatting.RED);
        }
        if (table.busy()) {
            return Component.translatable("foundry.pioneers.table_busy").withStyle(ChatFormatting.YELLOW);
        }
        if (mix.isEmpty()) {
            return Component.translatable("foundry.pioneers.empty").withStyle(ChatFormatting.YELLOW);
        }
        if (!molten()) {
            return Component.translatable("foundry.pioneers.too_cold", Metals.pourC(mix)).withStyle(ChatFormatting.RED);
        }
        //1.0.3-beta (lot 7b) : regle commune : la plus grande part coulable qui remplit le moule, le surplus reste dans le bain
        Metals.Plan plan = plan();
        if (!plan.pours()) {
            return why(plan, table).withStyle(ChatFormatting.YELLOW);
        }
        String metal = plan.metal();
        CastingRecipe recipe = table.recipeFor(metal);
        if (recipe == null) {
            return Component.translatable("foundry.pioneers.no_recipe", Metals.name(metal)).withStyle(ChatFormatting.RED);
        }
        Map<String, Integer> poured = Metals.pour(mix, metal, recipe.amount());
        table.fill(recipe, poured);
        crucible.setStack(MoldItem.wear(crucible.getStack()));
        level.playSound(null, pos(), SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.8F, 1.1F);
        BronzeEvents.award(level, pos(), "pour_" + metal);
        machine.markSync();
        machine.changed();
        return Component.translatable("foundry.pioneers.poured", recipe.amount(), Metals.name(metal)).withStyle(ChatFormatting.GOLD);
    }

    /** Message quand rien ne coule : ce que le verrou attend, metal pris dans un alliage, moule sans recette, trop peu de metal. */
    static MutableComponent why(Metals.Plan plan, CastingTableTile table) {
        if (plan.reason() == Metals.WHY_WAITING && !plan.waiting().isEmpty()) {
            return Component.translatable("foundry.pioneers.waits", Metals.name(plan.waiting()));
        }
        if (plan.reason() == Metals.WHY_BOUND) {
            return Component.translatable("foundry.pioneers.bound");
        }
        if (!plan.metal().isEmpty()) {
            CastingRecipe recipe = table == null ? null : table.recipeFor(plan.metal());
            return recipe == null ? Component.translatable("foundry.pioneers.no_recipe", Metals.name(plan.metal()))
                  : Component.translatable("foundry.pioneers.not_enough", recipe.amount(), plan.mb());
        }
        return Component.translatable("foundry.pioneers.mixed");
    }

    // ------------------------------------------------------------------
    // Interface : 0 temperature x10, 1 accessible x10, 2 combustible x1000, 3 air x1000, 4 total mB, 5..8 fusion des cases x1000, 9 coulee
    // auto (0/1) : memes indices qu'avant
    // ------------------------------------------------------------------

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.HEAT, () -> Math.round(temperature * 10)).add(UiValues.REACH, () -> Math.round(reachable() * 10))
              .add(UiValues.BURN, () -> Math.round(hearth.ratio() * 1000)).add(UiValues.BLAST, () -> Math.round(Math.min(1, air) * 1000))
              .add(UiValues.MOLTEN, this::total);
        for (int i = 0; i < CrucibleFurnaceTile.METAL_SLOTS; i++) {
            int index = i;
            values.add(UiValues.melt(i), () -> melting[index] * 1000L / Math.max(1, 20 + Metals.INGOT / 6));
        }
        values.add(UiValues.AUTO, () -> autoPour ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // Client, sauvegarde
    // ------------------------------------------------------------------

    /** 1.0.5-beta (lot 29) : garde des envois periodiques (etat entier compare). */
    private final fr.pioneers.core.block.StateWatch watch = new fr.pioneers.core.block.StateWatch();

    @Override
    public void writeClient(CompoundTag tag) {
        CompoundTag m = new CompoundTag();
        mix.forEach(m::putInt);
        tag.put("mix", m);
        tag.putDouble("t", temperature);
        tag.putBoolean("auto", autoPour);
        lock.writeClient(tag);
    }

    @Override
    public void readClient(CompoundTag tag) {
        mix.clear();
        CompoundTag m = tag.getCompound("mix");
        for (String key : m.getAllKeys()) {
            //1.3.2 : un alliage garde d'une ancienne sauvegarde est decompose
            Metals.addTo(mix, key, m.getInt(key));
        }
        temperature = tag.getDouble("t");
        autoPour = !tag.contains("auto") || tag.getBoolean("auto");
        lock.readClient(tag);
    }

    @Override
    public void save(CompoundTag tag) {
        hearth.save(tag);
        writeClient(tag);
        //Le verrou se sauve a part (le texte vide du client n'est pas une sauvegarde)
        tag.remove(RecipeLock.NBT);
        lock.save(tag);
        tag.putIntArray("melting", melting);
        saveLegacy(tag, legacy);
    }

    @Override
    public void load(CompoundTag tag) {
        hearth.load(tag);
        readClient(tag);
        lock.load(tag);
        if (!tag.contains("t")) {
            temperature = 20;
        }
        int[] p = tag.getIntArray("melting");
        System.arraycopy(p, 0, melting, 0, Math.min(p.length, melting.length));
        //1.0.5-beta (lot 39) : cases au-dela des 5 d'aujourd'hui (creuset, combustible, 3 metaux) = la 4e case de metal d'avant
        loadLegacy(tag, 2 + CrucibleFurnaceTile.METAL_SLOTS, legacy);
    }

    // ------------------------------------------------------------------
    // 1.0.5-beta (lot 39, retour 51) : 4e case de metal d'avant (fourneau a creuset, fonderie automatique : 3 cases aujourd'hui)
    // ------------------------------------------------------------------

    /** Cle des objets d'une ancienne case pas encore ranges (tile sauvee avant son premier tick). */
    public static final String LEGACY = "legacy_metal";

    /** Objets des cases d'indice >= {@code first} du NBT {@code slots} (cases d'avant, plus lues), et ceux de la cle {@link #LEGACY}. */
    public static void loadLegacy(CompoundTag tag, int first, List<ItemStack> out) {
        out.clear();
        net.minecraft.nbt.ListTag slots = tag.getList("slots", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag e = slots.getCompound(i);
            if (e.getShort("Slot") >= first) {
                ItemStack st = ItemStack.of(e);
                if (!st.isEmpty()) {
                    out.add(st);
                }
            }
        }
        net.minecraft.nbt.ListTag kept = tag.getList(LEGACY, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < kept.size(); i++) {
            ItemStack st = ItemStack.of(kept.getCompound(i));
            if (!st.isEmpty()) {
                out.add(st);
            }
        }
    }

    public static void saveLegacy(CompoundTag tag, List<ItemStack> legacy) {
        if (legacy.isEmpty()) {
            return;
        }
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (ItemStack st : legacy) {
            list.add(st.save(new CompoundTag()));
        }
        tag.put(LEGACY, list);
    }

    /**
     * Range les objets d'une ancienne case : d'abord sur une pile du meme objet d'une case de metal, puis dans une case de metal vide, le reste
     * POSE AU SOL au-dessus de la machine ; rien ne se perd. Vide la liste.
     */
    public static void placeLegacy(ServerLevel level, BlockPos pos, List<ItemSlot> inputs, List<ItemStack> legacy) {
        for (ItemStack stack : legacy) {
            ItemStack rest = stack.copy();
            for (ItemSlot slot : inputs) {
                ItemStack in = slot.getStack();
                if (!rest.isEmpty() && !in.isEmpty() && ItemStack.isSameItemSameTags(in, rest) && in.getCount() < in.getMaxStackSize()) {
                    int n = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
                    slot.setStack(in.copyWithCount(in.getCount() + n));
                    rest.shrink(n);
                }
            }
            for (ItemSlot slot : inputs) {
                if (!rest.isEmpty() && slot.isEmpty()) {
                    slot.setStack(rest.copy());
                    rest = ItemStack.EMPTY;
                }
            }
            if (!rest.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, rest);
            }
        }
        legacy.clear();
    }

    /** Pour les GameTests : objets d'une ancienne case encore a ranger. */
    public List<ItemStack> legacy() {
        return legacy;
    }
}
