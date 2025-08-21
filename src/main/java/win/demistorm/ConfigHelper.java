package win.demistorm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads / saves a tiny json config and synchronises the server copy to
 * every connecting client.
 * Only one flag is stored for now – “boomerangEffect”.
 */
public final class ConfigHelper {
    /* ------------------------------------------------------------ */
    /*  DATA                                                         */
    /* ------------------------------------------------------------ */
    public static final class Data {
        public boolean boomerangEffect = true;

    }

    public static final Identifier CHANNEL =
            Identifier.of(VRThrowingExtensions.MOD_ID, "config_sync");

    private static final Gson  GSON      = new GsonBuilder().setPrettyPrinting().create();
    private static final Path  CONFIGDIR = Path.of("config");
    private static final Path  FILE      = CONFIGDIR.resolve("vr-throwing-extensions.json");

    /** client-side local copy (never overwritten by server) */
    public static final Data CLIENT      = new Data();
    /** the server copy currently in use on this logical side          */
    public static final Data ACTIVE      = new Data();        // replaced while on server

    /* ------------------------------------------------------------ */
    /*  SERIALISATION                                                */
    /* ------------------------------------------------------------ */
    private static String toJson(Data d)      { return GSON.toJson(d); }
    private static Data   fromJson(String js) { return GSON.fromJson(js, Data.class); }

    public static void loadOrCreateServerConfig() {
        Data d = read();
        write(d);                     // guarantees pretty file exists
        copyInto(d, ACTIVE);
        copyInto(d, SERVER_SHADOW);   // keeps an untouched reference
    }

    public static void loadOrCreateClientConfig() {
        copyInto(read(), CLIENT);
        copyInto(CLIENT, ACTIVE);     // single-player default
    }

    /* ------------------------------------------------------------ */
    /*  FILE IO                                                      */
    /* ------------------------------------------------------------ */
    private static Data read() {
        try {
            if (Files.exists(FILE))
                return fromJson(Files.readString(FILE));
        } catch (IOException ignored) { }
        return new Data();            // defaults
    }

    private static void write(Data d) {
        try {
            Files.createDirectories(CONFIGDIR);
            Files.writeString(FILE, toJson(d));
        } catch (IOException e) {
            VRThrowingExtensions.log.error("Unable to write config!", e);
        }
    }

    private static void copyInto(Data from, Data to) {
        to.boomerangEffect = from.boomerangEffect;
    }

    /* ------------------------------------------------------------ */
    /*  NETWORK  (server→client)                                     */
    /* ------------------------------------------------------------ */
    // payload is kept inside this helper to avoid the need for an extra class
    public record SyncPayload(String json) implements CustomPayload {
        public static final Id<SyncPayload> ID = new Id<>(CHANNEL);
        public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC =
                PacketCodec.of((p,b)->b.writeString(p.json), b->new SyncPayload(b.readString()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    static {
        /* Tell networking about the packet on BOTH sides */
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncPayload.ID, SyncPayload.CODEC); // (never sent C2S)
    }

    /* ------------------------------------------------------------ */
    /*  REGISTRATION HOOKS                                           */
    /* ------------------------------------------------------------ */
    public static void initServerSide() {
        // 1) load file
        loadOrCreateServerConfig();

        // 2) send to every joining player
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sender.sendPacket(new SyncPayload(toJson(ACTIVE)))
        );

        // 3) save on server stop in case somebody /reloaded etc.
        ServerLifecycleEvents.SERVER_STOPPING.register(s->write(ACTIVE));
    }

    /** called from the client helper once it has the packet in hand */
    static void clientReceivedRemote(String json) {
        copyInto(fromJson(json), ACTIVE);
        VRThrowingExtensions.log.debug("Received remote config: {}", json);
    }

    /** called on disconnect – again by the client helper */
    static void clientDisconnected() {
        copyInto(CLIENT, ACTIVE);
    }

    /* ------------------------------------------------------------ */
    /*  INTERNALS                                                    */
    /* ------------------------------------------------------------ */
    private static final Data SERVER_SHADOW = new Data(); // for future use/debug

}