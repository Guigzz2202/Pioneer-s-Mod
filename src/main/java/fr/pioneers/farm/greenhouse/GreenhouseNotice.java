package fr.pioneers.farm.greenhouse;

import fr.pioneers.farm.crop.Climate;
import fr.pioneers.space.SolarSystem;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 1.0.5-beta (lot 3, specification §2.2 et §8, « Mondes existants ») : MESSAGE UNIQUE de mise a jour des serres. La premiere fois qu'une
 * culture est vue sous l'ANCIENNE serre (verre au-dessus) la ou elle ne compte plus (Terre, Nether, End), le monde le retient
 * ({@code pioneers_greenhouse_notice} de l'Overworld) et chaque joueur recoit UNE FOIS le message (tout de suite s'il est connecte, sinon a
 * sa prochaine connexion). Ces cultures suivent le climat du dehors, avec le delai de grace d'un jour (lot 1) : rien ne meurt au
 * chargement. Le guide (« Construire une serre ») montre comment refaire la serre.
 */
public class GreenhouseNotice extends SavedData {

    public static final String NAME = "pioneers_greenhouse_notice";
    private boolean found;
    private final Set<UUID> told = new HashSet<>();

    public static GreenhouseNotice get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(GreenhouseNotice::load, GreenhouseNotice::new, NAME);
    }

    public boolean found() {
        return found;
    }

    /** Une culture hors serre (tick aleatoire) : est-elle sous l'ancienne serre de verre ? Si oui, la premiere fois : message. */
    public static void check(ServerLevel level, BlockPos pos) {
        if (Climate.legacyRule(SolarSystem.fromDimension(level.dimension()))) {
            return;
        }
        GreenhouseNotice n = get(level.getServer());
        if (n.found) {
            return;
        }
        //Filtre peu couteux : un bloc qui arrete la pluie au-dessus de la culture, puis la vraie regle d'avant
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY() + 1 || !Climate.legacyGlass(level, pos)) {
            return;
        }
        n.found = true;
        n.setDirty();
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            n.tell(p);
        }
    }

    /** Envoie le message une fois a ce joueur (connexion, ou decouverte) ; rien si aucune ancienne serre n'a ete vue. */
    public void tell(ServerPlayer player) {
        if (!found || !told.add(player.getUUID())) {
            return;
        }
        setDirty();
        player.sendSystemMessage(Component.translatable("message.pioneers.greenhouse.update.title").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.pioneers.greenhouse.update").withStyle(ChatFormatting.YELLOW));
    }

    private static GreenhouseNotice load(CompoundTag tag) {
        GreenhouseNotice n = new GreenhouseNotice();
        n.found = tag.getBoolean("found");
        for (Tag t : tag.getList("told", Tag.TAG_STRING)) {
            try {
                n.told.add(UUID.fromString(t.getAsString()));
            } catch (IllegalArgumentException ignored) {
                //uuid illisible : le joueur reverra le message
            }
        }
        return n;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("found", found);
        ListTag list = new ListTag();
        for (UUID u : told) {
            list.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
        }
        tag.put("told", list);
        return tag;
    }
}
