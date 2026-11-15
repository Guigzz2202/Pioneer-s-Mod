package fr.pioneers.building.home.bag;

import fr.pioneers.Pioneers;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Patch 1.0.3-beta, lot 7 : RAMASSAGE AUTOMATIQUE des sacs de rangement (graines, pousses, poissons) : un objet ramasse va d'abord dans le
 * premier sac de l'inventaire (barre d'action, sac a dos du joueur, main secondaire) qui accepte sa sorte et a de la place ; le reste suit le
 * chemin normal.
 * <p>
 * 1.0.5-beta (lot 31, retour 42) : le sac est un registre a grosses cases ({@link BagLedger}) ecrit DIRECTEMENT dans l'objet (plus de copie
 * de travail, meme ecran ouvert : rien ne se duplique ni ne se perd) ; interrupteur de ramassage et filtre du sac respectes ; les objets
 * DONNES au joueur par le code du mod (prise de peche, casier, panier du chat, appareils de la ferme) passent par {@link #give} : sac d'abord,
 * puis l'inventaire, puis au sol ; petit son discret (au plus un toutes les {@link #SOUND_GAP} ticks par joueur).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class BagEvents {

    /** Son du rangement direct : au plus un toutes les 6 ticks par joueur. */
    public static final int SOUND_GAP = 6;
    private static final Map<Player, long[]> LAST_SOUND = new WeakHashMap<>();

    private BagEvents() {
    }

    @SubscribeEvent
    public static void pickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        ItemEntity entity = event.getItem();
        //1.0.5-beta (lot 35) : une pile deja ramassee (retiree du monde) n'est jamais rangee une 2e fois (aimant d'un autre mod, 2e joueur)
        if (player.level().isClientSide || entity.hasPickUpDelay() || entity.isRemoved()) {
            return;
        }
        ItemStack item = entity.getItem();
        int before = item.getCount();
        ItemStack rest = absorb(player, item);
        if (rest.getCount() == before) {
            return;
        }
        if (rest.isEmpty()) {
            event.setCanceled(true);
            player.take(entity, before);
            entity.discard();
        } else {
            //1.0.5-beta (lot 35) : la pile au sol est RETAILLEE EN PLACE (meme objet). ItemEntity.playerTouch lit getItem() AVANT cet evenement
            //et range ensuite CET objet dans l'inventaire (verifie au bytecode de Forge 47.4.20) : une pile neuve posee par setItem laissait
            //l'ancienne, entiere, aller a l'inventaire = DUPLICATION de la part rangee dans le sac (case pleine a 1 024 en pleine recolte).
            item.setCount(rest.getCount());
        }
    }

    /** Range {@code item} dans les sacs de rangement du joueur (ramassage : interrupteur et filtre respectes) ; rend le reste. */
    public static ItemStack absorb(Player player, ItemStack item) {
        //ne modifie jamais item : rend item lui-meme (rien n'est entre), une copie (le reste) ou EMPTY
        ItemStack rest = item;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && !rest.isEmpty(); i++) {
            ItemStack bag = inv.getItem(i);
            if (!(bag.getItem() instanceof BagItem b) || !b.kind.storage() || !b.kind.accepts(rest)) {
                continue;
            }
            rest = BagStore.insert(bag, b.kind, rest, false, true);
        }
        return rest;
    }

    /**
     * Donne {@code stack} au joueur comme une prise : dans un sac adapte d'abord (son discret), puis l'inventaire, puis au sol a ses pieds.
     * Serveur seulement.
     */
    public static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack rest = deliver(player, stack.copy());
        if (!rest.isEmpty() && !player.getInventory().add(rest)) {
            player.drop(rest, false);
        }
    }

    /** Range dans les sacs (avec le son discret si quelque chose y est entre) ; rend le reste, que l'appelant donne a sa facon. */
    public static ItemStack deliver(Player player, ItemStack stack) {
        int before = stack.getCount();
        ItemStack rest = absorb(player, stack);
        if (rest.getCount() < before) {
            chime(player);
        }
        return rest;
    }

    static void chime(Player player) {
        long now = player.level().getGameTime();
        long[] last = LAST_SOUND.computeIfAbsent(player, p -> new long[]{Long.MIN_VALUE / 2});
        if (now - last[0] >= SOUND_GAP) {
            last[0] = now;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.3F,
                  0.9F + player.level().random.nextFloat() * 0.3F);
        }
    }
}
