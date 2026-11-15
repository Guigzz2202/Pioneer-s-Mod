package fr.pioneers.robotics;

import fr.pioneers.robotics.hub.RoboticHubData;
import fr.pioneers.robotics.logistics.PointConfig;
import fr.pioneers.robotics.works.WorksPlan;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Ordre robotique, cree depuis le menu des ordres (touche O) ou le planificateur de zone :
 * <ul>
 *     <li><b>transport</b> : d'une face d'un bloc (ou du reseau de balises) vers une face d'un autre ; ponctuel (une quantite, puis
 *     l'ordre est termine) ou recurrent (en continu, ou en gardant la cible a un niveau) ;</li>
 *     <li><b>zone</b> : une tache (recolte, abattage, minage...) dans une zone delimitee.</li>
 * </ul>
 * Affectation : n'importe quel hub (le meilleur candidat), un genre d'unites, ou un hub precis.
 */
public class RoboticOrder {

    public enum Type {TRANSFER, ZONE}

    public enum Status {
        WAITING, ACTIVE, PAUSED, NO_HUB, NO_SOURCE, TARGET_FULL, NO_SUPPLIES, DONE, INVALID, CONDITION,
        /** Rien a faire pour l'instant (zone : plus de travail trouve). */
        NOTHING_TO_DO,
        /** La cible est deja au niveau voulu. */
        TARGET_OK,
        /** Aucune unite dans les hubs qui peuvent prendre l'ordre. */
        NO_UNITS,
        /** Toutes les unites sont dehors, occupees. */
        UNITS_BUSY,
        /** Les unites rangees se rechargent. */
        UNITS_CHARGING,
        /** Le hub n'a pas assez d'energie pour lancer une unite. */
        HUB_NO_ENERGY,
        /** Aucun hub ne correspond a l'affectation choisie (genre, hub precis). */
        NO_HUB_MATCH,
        /** Le travail est hors d'atteinte des unites au sol (il faut des drones). */
        UNREACHABLE;

        /** Un probleme a regler (et non une simple attente). */
        public boolean problem() {
            return this != WAITING && this != ACTIVE && this != PAUSED && this != DONE && this != NOTHING_TO_DO && this != TARGET_OK
                   && this != UNITS_BUSY && this != UNITS_CHARGING && this != CONDITION;
        }

        public String getTranslationKey() {
            return "robotics.pioneers.order.status." + name().toLowerCase(Locale.ROOT);
        }

        public static Status byIndex(int index) {
            return values()[Math.max(0, Math.min(values().length - 1, index))];
        }
    }

    public static final int MAX_UNITS = 16, MAX_ZONE = 96, MAX_ZONE_HEIGHT = 64, MAX_HERD = 200;

    public int id;
    public Type type = Type.TRANSFER;
    public boolean paused;
    /** Genre d'unites impose (-1 : n'importe lequel). */
    public int kind = -1;
    /** Hub impose (son centre), ou null. */
    @Nullable
    public BlockPos hub;
    public int maxUnits = 2;

    /** -1 basse, 0 normale, 1 haute. */
    public int priority;
    /** Ne travailler que si le reseau a moins (ou plus) de N d'une ressource. */
    @Nullable
    public PointConfig.Condition condition;

    public boolean recurring;
    /** Ce qui est transporte (precis, ou toute une famille). */
    public LogisticResource res = LogisticResource.any(Resource.ITEMS);
    /** Ponctuel : quantite a livrer ; recurrent : niveau a maintenir dans la cible (0 : sans limite). */
    public long amount;
    public long delivered;
    /** Source : un bloc, ou null pour le reseau de balises (objets filtres seulement). */
    @Nullable
    public Endpoint source;
    @Nullable
    public Endpoint target;

    public RobotTask task = RobotTask.FARMER;
    /** Elevage et chasse : adultes gardes par espece (au-dela, le surplus est elimine ; 0 a la chasse : tous). */
    public int herd = 12;
    /** Chasse : cibles (0 : monstres et animaux, 1 : monstres seulement, 2 : animaux seulement). */
    public int hunt;
    /** Mode repos (tortues) : entre deux travaux, la tortue dort sur place au lieu de rentrer au hub, sans consommer. */
    public boolean rest = true;
    /** Agriculture, bucheronnage : replanter (et, pour les champs, rebecher la terre abimee). */
    public boolean replant = true;
    /** Graine ou pousse a planter (identifiant d'objet), vide : la meme que ce qui a ete recolte. */
    public String plant = "";
    /** Apiculture : part des recoltes en rayons de miel (en %), le reste en fioles (il faut des fioles vides dans le reseau). */
    public int honeycomb = 50;
    /** Nom donne par le joueur (vide : le nom de la tache). */
    public String label = "";

    /** La graine ou pousse choisie, ou vide. */
    public ItemStack plantItem() {
        if (plant.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(plant);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static final int HUNT_ALL = 0, HUNT_MONSTERS = 1, HUNT_ANIMALS = 2;
    @Nullable
    public BlockPos min, max;

    /** Etat calcule par l'ordonnanceur (synchronise vers les clients). */
    public Status status = Status.WAITING;
    /** Objet en cause dans l'etat (fourniture manquante...), identifiant de registre, ou vide. */
    public String statusItem = "";
    public int activeUnits;

    @Nullable
    public UnitKind getKind() {
        return kind < 0 || kind >= UnitKind.values().length ? null : UnitKind.values()[kind];
    }

    /** Ce hub peut-il prendre cet ordre ? */
    public boolean assignable(RoboticHubData data) {
        UnitKind wanted = getKind();
        return (wanted == null || data.getKind() == wanted) && (hub == null || hub.equals(data.getCenter()));
    }

    /** L'ordre est-il complet et coherent ? */
    public boolean isValid() {
        if (type == Type.ZONE && task == RobotTask.BUILDER) {
            //Travaux : le plan (cote serveur) fixe ses propres limites
            return min != null && max != null && max.getX() - min.getX() < WorksPlan.MAX_SPAN
                   && max.getZ() - min.getZ() < WorksPlan.MAX_SPAN && max.getY() - min.getY() < 128;
        }
        if (type == Type.ZONE) {
            return min != null && max != null && task != RobotTask.LOGISTICS && max.getX() - min.getX() < MAX_ZONE && max.getZ() - min.getZ() < MAX_ZONE
                   && max.getY() - min.getY() < MAX_ZONE_HEIGHT;
        }
        if (target == null) {
            return false;
        }
        if (source == null) {
            //Reseau : une ressource precise ou un tag
            return !res.isFamily();
        }
        return !source.pos().equals(target.pos()) || source.side() != target.side();
    }

    /** Zone de travail etendue selon la tache (minage jusqu'au fond, arbres en hauteur...). */
    public AABB zoneBox(int minBuildHeight) {
        if (min == null || max == null) {
            return new AABB(BlockPos.ZERO);
        }
        int y0 = min.getY(), y1 = max.getY();
        switch (task) {
            //Minage : seulement les minerais du volume de la zone (les filons profonds et infinis sont l'affaire des avant-postes miniers)
            case MINER -> {
            }
            case LUMBERJACK -> {
                y0 -= 1;
                y1 += 16;
            }
            case EXCAVATOR -> {
            }
            default -> {
                y0 -= 1;
                y1 += 3;
            }
        }
        return new AABB(min.getX(), y0, min.getZ(), max.getX() + 1, y1 + 1, max.getZ() + 1);
    }

    /** Point de reference (portee des hubs). */
    public BlockPos anchor() {
        if (type == Type.ZONE && min != null && max != null) {
            return new BlockPos((min.getX() + max.getX()) / 2, max.getY(), (min.getZ() + max.getZ()) / 2);
        }
        return target != null ? target.pos() : source != null ? source.pos() : BlockPos.ZERO;
    }

    public RoboticOrder copy() {
        return load(save(true));
    }

    public CompoundTag save(boolean withStatus) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        if (!label.isEmpty()) {
            tag.putString("label", label);
        }
        tag.putByte("type", (byte) type.ordinal());
        tag.putBoolean("paused", paused);
        tag.putByte("kind", (byte) kind);
        if (hub != null) {
            tag.put("hub", NbtUtils.writeBlockPos(hub));
        }
        tag.putByte("maxUnits", (byte) maxUnits);
        tag.putByte("priority", (byte) priority);
        if (condition != null) {
            CompoundTag c = condition.res().save();
            c.putBoolean("below", condition.below());
            c.putLong("threshold", condition.threshold());
            tag.put("condition", c);
        }
        if (type == Type.TRANSFER) {
            tag.putBoolean("recurring", recurring);
            tag.put("res", res.save());
            tag.putLong("amount", amount);
            tag.putLong("delivered", delivered);
            if (source != null) {
                tag.put("source", source.save());
            }
            if (target != null) {
                tag.put("target", target.save());
            }
        } else {
            tag.putByte("task", (byte) task.ordinal());
            tag.putShort("herd", (short) herd);
            tag.putByte("hunt", (byte) hunt);
            tag.putBoolean("rest", rest);
            tag.putBoolean("replant", replant);
            tag.putString("plant", plant);
            tag.putByte("comb", (byte) honeycomb);
            if (min != null && max != null) {
                tag.put("min", NbtUtils.writeBlockPos(min));
                tag.put("max", NbtUtils.writeBlockPos(max));
            }
        }
        if (withStatus) {
            tag.putByte("status", (byte) status.ordinal());
            tag.putByte("activeUnits", (byte) activeUnits);
            if (!statusItem.isEmpty()) {
                tag.putString("statusItem", statusItem);
            }
        }
        return tag;
    }

    public static RoboticOrder load(CompoundTag tag) {
        RoboticOrder order = new RoboticOrder();
        order.id = tag.getInt("id");
        order.type = tag.getByte("type") == 1 ? Type.ZONE : Type.TRANSFER;
        order.paused = tag.getBoolean("paused");
        order.kind = tag.getByte("kind");
        order.hub = tag.contains("hub") ? NbtUtils.readBlockPos(tag.getCompound("hub")) : null;
        order.maxUnits = Math.max(1, Math.min(MAX_UNITS, tag.getByte("maxUnits")));
        order.recurring = tag.getBoolean("recurring");
        order.priority = Math.max(-1, Math.min(1, tag.getByte("priority")));
        if (tag.contains("condition")) {
            CompoundTag c = tag.getCompound("condition");
            order.condition = new PointConfig.Condition(LogisticResource.load(c), c.getBoolean("below"), c.getLong("threshold"));
        }
        if (tag.contains("res")) {
            order.res = LogisticResource.load(tag.getCompound("res"));
        } else {
            //Ordres de la premiere version : famille + filtre d'objet
            Resource resource = Resource.byIndex(tag.getByte("resource"));
            order.res = resource == Resource.ITEMS && tag.contains("filter") ? LogisticResource.of(ItemStack.of(tag.getCompound("filter")))
                                                                          : LogisticResource.any(resource);
        }
        order.amount = Math.max(0, tag.getLong("amount"));
        order.delivered = tag.getLong("delivered");
        order.source = Endpoint.loadOptional(tag, "source");
        order.target = Endpoint.loadOptional(tag, "target");
        order.task = RobotTask.byIndex(tag.getByte("task"));
        order.herd = tag.contains("herd") ? Math.max(0, Math.min(MAX_HERD, tag.getShort("herd"))) : 12;
        order.hunt = Math.max(0, Math.min(2, tag.getByte("hunt")));
        order.rest = !tag.contains("rest") || tag.getBoolean("rest");
        order.replant = !tag.contains("replant") || tag.getBoolean("replant");
        order.plant = tag.getString("plant");
        order.honeycomb = tag.contains("comb") ? Math.max(0, Math.min(100, tag.getByte("comb"))) : 50;
        order.label = tag.getString("label").length() > 32 ? tag.getString("label").substring(0, 32) : tag.getString("label");
        if (tag.contains("min") && tag.contains("max")) {
            BlockPos a = NbtUtils.readBlockPos(tag.getCompound("min")), b = NbtUtils.readBlockPos(tag.getCompound("max"));
            order.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
            order.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        }
        order.status = Status.byIndex(tag.getByte("status"));
        order.activeUnits = tag.getByte("activeUnits");
        order.statusItem = tag.getString("statusItem");
        return order;
    }
}
