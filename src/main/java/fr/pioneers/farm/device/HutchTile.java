package fr.pioneers.farm.device;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.herd.GeneratedHerd;
import fr.pioneers.farm.herd.HerdData;
import fr.pioneers.farm.herd.HerdRules;
import fr.pioneers.farm.herd.PiHerd;
import fr.pioneers.farm.kitchen.StationTiles;
import fr.pioneers.farm.ui.FarmLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 9, Guillaume : « clapier a lapin ») : CLAPIER. Les lapins y vivent sans entite (une liste : donnees {@link HerdData} et age),
 * jusqu'a {@link GeneratedHerd#HUTCH_CAPACITY}. On les y met en laisse (clic droit sur le clapier : tous les lapins tenus en laisse par le
 * joueur entrent) ; accroupi main vide : le dernier ressort (avec sa race et ses stats). Nourriture (cases 0-1, dessus et cotes : carottes,
 * pissenlits, foin... {@link GeneratedHerd#RABBIT_FOOD}) en reserve ; nourris, deux adultes font une portee ({@link HerdRules#rabbitChild} :
 * race 50 / 50, stats heritees) ; les lapereaux grandissent ; les adultes donnent des peaux (x peau), la laine des angoras et du fumier
 * (compost) dans les cases 2-7 (dessous). Couteau + accroupi : abattre un adulte (on garde toujours deux adultes) : viande (x viande) et peau.
 * Fiche au regard. Casse : les lapins ressortent, rien n'est perdu.
 */
public class HutchTile extends FarmDeviceTile {

    public static final int FOOD_SLOTS = 2, OUT_FROM = 2, SLOTS = 8;
    /** 1.0.5-beta (lot 41) : case du couteau (abattage au bouton de l'interface ; avant : couteau en main + Maj). */
    public static final int KNIFE = 8;
    private static final TagKey<Item> KNIVES = ItemTags.create(ResourceLocation.parse("pioneers:tools/knives"));

    /** Un lapin du clapier : ses donnees, adulte ou lapereau (secondes de croissance). */
    public static final class Resident {
        public final HerdData data;
        public float grow;
        public boolean adult;

        public Resident(HerdData data, boolean adult, float grow) {
            this.data = data;
            this.adult = adult;
            this.grow = grow;
        }
    }

    private final List<Resident> rabbits = new ArrayList<>();
    private int food;
    private float eatAcc, breedAcc, peltAcc, woolAcc, manureAcc;
    private static Map<Item, Integer> foods;

    public HutchTile(BlockPos pos, BlockState state) {
        super(pos, state, SLOTS + 1, 0);
    }

    /**
     * 1.0.5-beta (lot 41, retour 60) : interface : nourriture (cases 0-1), produits (peaux, laine, fumier, viande : 2-7), couteau (8) ; boutons
     * « Relacher un lapin » (avant : Maj + clic droit main vide) et « Abattre » (avant : couteau en main + Maj). Seul geste garde en monde :
     * les lapins tenus en laisse entrent au clapier ({@link #worldGesture} : des entites, impossible dans une interface).
     */
    @Override
    protected FarmLayout layout() {
        return FarmLayout.builder().tool(KNIFE).store(new int[]{0, 1}, 1, "gui.pioneers.farm.slot.rabbit_food").arrow(false)
              .outputs(StationTiles.range(OUT_FROM, SLOTS), 2).button("gui.pioneers.farm.button.release")
              .button("gui.pioneers.farm.button.slaughter").build();
    }

    @Override
    protected ItemStack ghost(FarmLayout.SlotSpec spec) {
        if (spec.index() == KNIFE) {
            for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(KNIVES)) {
                return new ItemStack(h.value());
            }
        }
        return spec.index() < FOOD_SLOTS ? new ItemStack(Items.CARROT) : super.ghost(spec);
    }

    @Override
    protected boolean acceptsInput(int slot, ItemStack stack) {
        return slot < FOOD_SLOTS && foodValue(stack) > 0;
    }

    @Override
    public boolean worldGesture(Player player) {
        List<Rabbit> tied = leashed(player);
        if (tied.isEmpty()) {
            return false;
        }
        int in = 0;
        for (Rabbit r : tied) {
            HerdData d = PiHerd.get(r);
            if (d == null || !d.init || d.breed < 0) {
                continue;
            }
            float grow = r.isBaby() ? GeneratedHerd.HUTCH_GROW_SECONDS * Math.max(0F, 1F + r.getAge() / 24000F) : 0;
            if (!admit(d, !r.isBaby(), grow)) {
                say(player, "hutch.full");
                break;
            }
            r.dropLeash(true, false);
            r.discard();
            say(player, "hutch.added", Component.translatable("farm.pioneers.herd.rabbit." + d.rabbit().id()));
            in++;
        }
        if (in > 0) {
            sound(SoundEvents.RABBIT_JUMP, 1F, 1F);
            PiFarmDevices.award(level, worldPosition, "hutch");
        }
        return true;
    }

    @Override
    public boolean buttonActive(int i) {
        return i == 0 ? !rabbits.isEmpty() : adults() > 2 && items.get(KNIFE).is(KNIVES);
    }

    @Override
    public List<Component> buttonTip(int i) {
        if (i == 0) {
            return List.of(Component.translatable(rabbits.isEmpty() ? "farm.pioneers.device.hutch.empty" : "gui.pioneers.farm.tip.release"));
        }
        if (!items.get(KNIFE).is(KNIVES)) {
            return List.of(Component.translatable("gui.pioneers.farm.tip.need_knife"));
        }
        return List.of(Component.translatable(adults() <= 2 ? "farm.pioneers.device.hutch.keep_pair" : "gui.pioneers.farm.tip.slaughter"));
    }

    @Override
    protected void button(ServerPlayer player, int button) {
        if (button == 0) {
            if (rabbits.isEmpty()) {
                say(player, "hutch.empty");
                return;
            }
            Resident r = rabbits.remove(rabbits.size() - 1);
            release(r);
            say(player, "hutch.released", Component.translatable("farm.pioneers.herd.rabbit." + r.data.rabbit().id()));
            sync();
        } else if (button == 1) {
            if (!items.get(KNIFE).is(KNIVES)) {
                say(player, "hutch.need_knife");
                return;
            }
            slaughter(player, items.get(KNIFE));
        }
    }

    public List<Resident> rabbits() {
        return rabbits;
    }

    public int food() {
        return food;
    }

    public int adults() {
        int n = 0;
        for (Resident r : rabbits) {
            n += r.adult ? 1 : 0;
        }
        return n;
    }

    /** Valeur nourrissante d'un objet pour les lapins (0 : pas un aliment). */
    public static int foodValue(ItemStack stack) {
        if (foods == null) {
            Map<Item, Integer> m = new HashMap<>();
            for (String[] f : GeneratedHerd.RABBIT_FOOD) {
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(f[0]));
                if (it != Items.AIR) {
                    m.put(it, Integer.parseInt(f[1]));
                }
            }
            foods = m;
        }
        return stack.isEmpty() ? 0 : foods.getOrDefault(stack.getItem(), 0);
    }

    // ------------------------------------------------------------------ vie

    @Override
    public void serverTick() {
        if (level == null || (level.getGameTime() + worldPosition.asLong()) % 20 != 0) {
            return;
        }
        //1.0.4-beta (lot 28) : un clapier 1 x 1 d'avant devient double des que la case a sa droite est libre
        if ((level.getGameTime() + worldPosition.asLong()) % 100 == 0 && HutchBlock.grow(level, worldPosition, getBlockState())) {
            sync();
        }
        if (second(new Random(level.random.nextLong()))) {
            sync();
        }
    }

    /** Une seconde de vie du clapier (GameTests : appelable directement) ; vrai si quelque chose de visible a change. */
    public boolean second(Random random) {
        boolean changed = false;
        //Nourriture : les cases se vident dans la reserve
        for (int i = 0; i < FOOD_SLOTS; i++) {
            ItemStack s = items.get(i);
            int v = foodValue(s);
            while (!s.isEmpty() && v > 0 && food + v <= GeneratedHerd.HUTCH_FOOD_MAX) {
                food += v;
                s.shrink(1);
                changed = true;
            }
        }
        int n = rabbits.size();
        if (n == 0) {
            return changed;
        }
        eatAcc += n;
        while (eatAcc >= GeneratedHerd.HUTCH_EAT_SECONDS && food > 0) {
            eatAcc -= GeneratedHerd.HUTCH_EAT_SECONDS;
            food--;
            changed = true;
        }
        if (food <= 0) {
            eatAcc = Math.min(eatAcc, GeneratedHerd.HUTCH_EAT_SECONDS);
            return changed;
        }
        //Croissance des lapereaux
        for (Resident r : rabbits) {
            if (!r.adult) {
                r.grow += r.data.growth;
                if (r.grow >= GeneratedHerd.HUTCH_GROW_SECONDS) {
                    r.adult = true;
                    changed = true;
                }
            }
        }
        List<Resident> adults = new ArrayList<>();
        for (Resident r : rabbits) {
            if (r.adult) {
                adults.add(r);
            }
        }
        //Portee : les deux premiers adultes, s'il reste de la place
        if (adults.size() >= 2 && n < GeneratedHerd.HUTCH_CAPACITY) {
            HerdData a = adults.get(0).data, b = adults.get(1).data;
            breedAcc += (a.growth + b.growth) / 2F;
            if (breedAcc >= GeneratedHerd.HUTCH_BREED_SECONDS) {
                breedAcc = 0;
                int kits = Math.max(1, Math.round((a.litter + b.litter) / 2F * (0.6F + 0.4F * random.nextFloat())));
                kits = Math.min(kits, GeneratedHerd.HUTCH_CAPACITY - n);
                for (int k = 0; k < kits; k++) {
                    rabbits.add(new Resident(HerdRules.rabbitChild(a, b, random), false, 0));
                }
                sound(SoundEvents.RABBIT_AMBIENT, 0.8F, 1.4F);
                PiFarmDevices.award(level, worldPosition, "hutch_litter");
                changed = true;
            }
        }
        //Produits des adultes (peaux, laine angora), fumier de tous
        for (Resident r : adults) {
            peltAcc += r.data.pelt;
            woolAcc += r.data.wool;
        }
        manureAcc += n;
        changed |= produce(peltAcc, GeneratedHerd.HUTCH_PELT_SECONDS, new ItemStack(Items.RABBIT_HIDE), 0);
        changed |= produce(woolAcc, GeneratedHerd.HUTCH_WOOL_SECONDS, new ItemStack(PiHerd.ANGORA_WOOL.get()), 1);
        changed |= produce(manureAcc, GeneratedHerd.HUTCH_MANURE_SECONDS, new ItemStack(compost()), 2);
        return changed;
    }

    private static Item compost() {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse("pioneers:compost"));
        return it == Items.AIR ? Items.BONE_MEAL : it;
    }

    /** Un produit quand l'accumulateur depasse le seuil ; garde dans les cases de sortie (sinon l'accumulateur attend). */
    private boolean produce(float acc, int seconds, ItemStack out, int which) {
        if (acc < seconds) {
            return false;
        }
        ItemStack rest = store(out, OUT_FROM, SLOTS, 64);
        if (!rest.isEmpty()) {
            return false;
        }
        switch (which) {
            case 0 -> peltAcc -= seconds;
            case 1 -> woolAcc -= seconds;
            default -> manureAcc -= seconds;
        }
        return true;
    }

    /** Fait entrer un lapin (ses donnees, adulte ou lapereau). Faux si plein. */
    public boolean admit(HerdData data, boolean adult, float grow) {
        if (rabbits.size() >= GeneratedHerd.HUTCH_CAPACITY) {
            return false;
        }
        rabbits.add(new Resident(data.copy(), adult, grow));
        sync();
        return true;
    }

    // ------------------------------------------------------------------ gestes

    private List<Rabbit> leashed(Player player) {
        return level == null ? List.of() : level.getEntitiesOfClass(Rabbit.class, new AABB(worldPosition).inflate(12),
              r -> r.isLeashed() && r.getLeashHolder() == player && r.isAlive());
    }

    /** Abat un adulte (le plus ancien) si au moins trois adultes : viande x viande et une peau. */
    public boolean slaughter(@Nullable Player player, ItemStack knife) {
        if (adults() <= 2) {
            if (player != null) {
                say(player, "hutch.keep_pair");
            }
            return false;
        }
        Resident victim = null;
        for (Resident r : rabbits) {
            if (r.adult) {
                victim = r;
                break;
            }
        }
        rabbits.remove(victim);
        int meat = Math.max(1, Math.round(GeneratedHerd.HUTCH_MEAT * victim.data.meat));
        List<ItemStack> drops = List.of(new ItemStack(Items.RABBIT, meat), new ItemStack(Items.RABBIT_HIDE));
        for (ItemStack s : drops) {
            ItemStack rest = store(s, OUT_FROM, SLOTS, 64);
            if (!rest.isEmpty() && player != null) {
                give(player, rest);
            }
        }
        if (player != null) {
            //1.0.5-beta (lot 41) : le couteau de la case s'use ; la viande et la peau vont aux produits du clapier (sinon au joueur)
            knife.hurtAndBreak(1, player, p -> {
            });
            say(player, "hutch.slaughtered");
        }
        sound(SoundEvents.RABBIT_DEATH, 0.7F, 1F);
        sync();
        return true;
    }

    /** Fait ressortir un lapin du clapier (entite vanilla avec sa race et ses stats). */
    private void release(Resident r) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        //1.0.4-beta (lot 28) : le lapin sort directement dans l'entite de sa race
        Rabbit rabbit = r.data.breed >= 0 && fr.pioneers.farm.herd.HerdBreeds.create(server, fr.pioneers.farm.herd.HerdBreeds.RABBIT, r.data.breed)
              instanceof Rabbit rr ? rr : EntityType.RABBIT.create(server);
        if (rabbit == null) {
            return;
        }
        Direction f = facing();
        rabbit.moveTo(worldPosition.getX() + 0.5 + f.getStepX() * 0.9, worldPosition.getY() + 0.1, worldPosition.getZ() + 0.5 + f.getStepZ() * 0.9,
              f.toYRot(), 0);
        HerdData d = PiHerd.get(rabbit);
        if (d != null) {
            d.deserializeNBT(r.data.serializeNBT());
            d.init = true;
        }
        if (!r.adult) {
            rabbit.setAge(-Math.max(1, (int) ((GeneratedHerd.HUTCH_GROW_SECONDS - r.grow) / Math.max(0.2F, r.data.growth) * 20)));
        }
        rabbit.setPersistenceRequired();
        server.addFreshEntity(rabbit);
    }

    @Override
    public void dropContents() {
        super.dropContents();
        while (!rabbits.isEmpty()) {
            release(rabbits.remove(rabbits.size() - 1));
        }
    }

    // ------------------------------------------------------------------ automatisation

    @Override
    protected boolean canInsert(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < FOOD_SLOTS && side != Direction.DOWN && foodValue(stack) > 0;
    }

    @Override
    protected boolean canExtract(int slot, @Nullable Direction side) {
        return slot < SLOTS && slot >= OUT_FROM && (side == Direction.DOWN || side == null);   //lot 41 : jamais le couteau (case SLOTS)
    }

    /** 1.0.4-beta (lot 28) : le clapier double et ses lapins depassent de la case principale (rendu des lapins loges). */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }

    // ------------------------------------------------------------------ fiche

    @Override
    protected int accent() {
        return 0xC89A68;
    }

    @Override
    protected void describe(FarmCard card, boolean sneaking) {
        int n = rabbits.size(), adults = adults();
        card.add(FarmCard.Line.gauge(FarmCard.label("rabbits"), n / (float) GeneratedHerd.HUTCH_CAPACITY, FarmCard.ratio(n, GeneratedHerd.HUTCH_CAPACITY),
              0xC8A078));
        if (n > adults) {
            card.add(FarmCard.Line.pair(FarmCard.label("kits"), Component.literal(String.valueOf(n - adults)), 0xE8D0B0));
        }
        card.add(FarmCard.Line.gauge(FarmCard.label("food"), food / (float) GeneratedHerd.HUTCH_FOOD_MAX, FarmCard.ratio(food, GeneratedHerd.HUTCH_FOOD_MAX),
              food == 0 ? FarmCard.BAD : 0xE09040));
        Component why = adults < 2 ? FarmCard.word("no_pair") : food == 0 ? FarmCard.word("no_food") : n >= GeneratedHerd.HUTCH_CAPACITY
              ? FarmCard.word("no_room") : null;
        card.add(why != null ? FarmCard.Line.pair(FarmCard.label("litter"), why, FarmCard.WARN)
              : FarmCard.Line.gauge(FarmCard.label("litter"), breedAcc / GeneratedHerd.HUTCH_BREED_SECONDS,
              FarmCard.percent(breedAcc / GeneratedHerd.HUTCH_BREED_SECONDS), 0x7CDC6A));
        if (sneaking) {
            Map<String, Integer> count = new java.util.LinkedHashMap<>();
            for (Resident r : rabbits) {
                count.merge(r.data.rabbit().id(), 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> e : count.entrySet()) {
                card.add(FarmCard.Line.pair(Component.translatable("farm.pioneers.herd.rabbit." + e.getKey()), Component.literal("× " + e.getValue()),
                      0xD0D0D0));
            }
        }
        List<ItemStack> out = new ArrayList<>();
        for (int i = OUT_FROM; i < SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                out.add(items.get(i));
            }
        }
        if (!out.isEmpty()) {
            card.add(FarmCard.Line.items(out));
        }
    }

    // ------------------------------------------------------------------ sauvegarde

    @Override
    protected void saveExtra(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Resident r : rabbits) {
            CompoundTag t = r.data.serializeNBT();
            t.putBoolean("adult", r.adult);
            t.putFloat("grow", r.grow);
            list.add(t);
        }
        tag.put("rabbits", list);
        tag.putInt("food", food);
        tag.putFloat("eat", eatAcc);
        tag.putFloat("breed", breedAcc);
        tag.putFloat("pelt", peltAcc);
        tag.putFloat("wool", woolAcc);
        tag.putFloat("manure", manureAcc);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        rabbits.clear();
        for (Tag t : tag.getList("rabbits", 10)) {
            CompoundTag c = (CompoundTag) t;
            HerdData d = new HerdData();
            d.deserializeNBT(c);
            rabbits.add(new Resident(d, c.getBoolean("adult"), c.getFloat("grow")));
        }
        food = tag.getInt("food");
        eatAcc = tag.getFloat("eat");
        breedAcc = tag.getFloat("breed");
        peltAcc = tag.getFloat("pelt");
        woolAcc = tag.getFloat("wool");
        manureAcc = tag.getFloat("manure");
    }
}
