package io.github.chronicle.quietbelow.ecology;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Narrow bridge between version-pinned vanilla hooks and the campaign ecology service.
 *
 * <p>The bridge is inert until a handler is registered. Registration is deliberately
 * single-owner: silently replacing the campaign's world-write policy would make its
 * ownership journal unreliable.</p>
 */
public final class EcologyHooks {
    private static final ThreadLocal<Deque<OwnWriteScope>> OWN_WRITES = new ThreadLocal<>();
    private static final ThreadLocal<Deque<CatalystOrigin>> CATALYST_ORIGINS = new ThreadLocal<>();

    private static volatile Handler handler;

    private EcologyHooks() {
    }

    public static synchronized void register(Handler candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (handler != null && handler != candidate) {
            throw new IllegalStateException("An ecology hook handler is already registered");
        }
        handler = candidate;
    }

    /**
     * Suppresses callbacks for the next server-world write attempted in this scope, whose
     * durable intent was already recorded by the ecology service. Writes caused by that
     * block's neighbor-update cascade remain observable. Scopes are reentrant,
     * thread-confined and safe to close twice.
     */
    public static Scope ownWrite() {
        Deque<OwnWriteScope> writes = OWN_WRITES.get();
        if (writes == null) {
            writes = new ArrayDeque<>();
            OWN_WRITES.set(writes);
        }
        OwnWriteScope scope = new OwnWriteScope(Thread.currentThread());
        writes.push(scope);
        return scope;
    }

    /** Returns the catalyst whose native spread manager is currently running, if any. */
    public static CatalystOrigin catalystOrigin() {
        Deque<CatalystOrigin> origins = CATALYST_ORIGINS.get();
        return origins == null ? null : origins.peek();
    }

    /** Internal entry point used by the pinned mixins. */
    public static Handler handler() {
        return handler;
    }

    /** Internal entry point used by the pinned world-write mixin. */
    public static boolean claimOwnWrite() {
        Deque<OwnWriteScope> writes = OWN_WRITES.get();
        if (writes == null) {
            return false;
        }
        OwnWriteScope scope = writes.peek();
        if (scope == null || scope.claimed) {
            return false;
        }
        scope.claimed = true;
        return true;
    }

    /** Internal entry point used by the pinned catalyst tick mixin. */
    public static Scope catalystScope(ServerWorld world, BlockPos pos) {
        CatalystOrigin origin = new CatalystOrigin(world, pos);
        Deque<CatalystOrigin> origins = CATALYST_ORIGINS.get();
        if (origins == null) {
            origins = new ArrayDeque<>();
            CATALYST_ORIGINS.set(origins);
        }
        origins.push(origin);
        return new CatalystScope(Thread.currentThread(), origin);
    }

    public interface Handler {
        /** New/changed native crops are discovered without a farm binding or terrain read here. */
        default void cropWrite(ServerWorld world,BlockPos pos,BlockState state) { }
        /** New unowned sculk can reconnect or require cleanup; never reads/loads terrain here. */
        default void unownedNetworkWrite(ServerWorld world,BlockPos pos) { }
        /** Suppress vanilla XP spread throughout a configured campaign dimension, including pending ownership. */
        default boolean managesCatalyst(ServerWorld world,BlockPos pos) { return owns(world,pos); }
        /** Native sensors/shriekers in the campaign dimension cannot bypass ownership. */
        default boolean managesFeature(ServerWorld world,BlockPos pos) { return owns(world,pos); }
        /** Bounded native phase settling; does not change ownership or vitality. */
        default void settleInactive(ServerWorld world,BlockPos pos,BlockState state) { }
        boolean owns(ServerWorld world, BlockPos pos);

        boolean active(ServerWorld world, BlockPos pos);

        /** Returns the effective state, or {@code null} to deny the write. */
        BlockState beforeWrite(
                ServerWorld world,
                BlockPos pos,
                BlockState before,
                BlockState proposed,
                int flags
        );

        /**
         * Called once for every intercepted attempt. A denied write reports the original
         * proposed state with {@code success == false}; an allowed write reports the
         * effective state returned by {@link #beforeWrite}.
         */
        void afterWrite(
                ServerWorld world,
                BlockPos pos,
                BlockState before,
                BlockState proposed,
                boolean success
        );
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public record CatalystOrigin(ServerWorld world, BlockPos pos) {
        public CatalystOrigin {
            Objects.requireNonNull(world, "world");
            pos = Objects.requireNonNull(pos, "pos").toImmutable();
        }
    }

    private static final class OwnWriteScope implements Scope {
        private final Thread owner;
        private boolean claimed;
        private boolean closed;

        private OwnWriteScope(Thread owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            requireOwner(owner);
            if (closed) {
                return;
            }
            Deque<OwnWriteScope> writes = OWN_WRITES.get();
            if (writes == null || writes.peek() != this) {
                throw new IllegalStateException("Ecology write scopes must close in stack order");
            }
            closed = true;
            writes.pop();
            if (writes.isEmpty()) {
                OWN_WRITES.remove();
            }
        }
    }

    private static final class CatalystScope implements Scope {
        private final Thread owner;
        private final CatalystOrigin origin;
        private boolean closed;

        private CatalystScope(Thread owner, CatalystOrigin origin) {
            this.owner = owner;
            this.origin = origin;
        }

        @Override
        public void close() {
            requireOwner(owner);
            if (closed) {
                return;
            }
            Deque<CatalystOrigin> origins = CATALYST_ORIGINS.get();
            if (origins == null || origins.peek() != origin) {
                throw new IllegalStateException("Catalyst scopes must close in stack order");
            }
            closed = true;
            origins.pop();
            if (origins.isEmpty()) {
                CATALYST_ORIGINS.remove();
            }
        }
    }

    private static void requireOwner(Thread owner) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Ecology hook scopes are thread-confined");
        }
    }
}
