package net.phoenixvine.wiki.api.gate;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.wiki.PhoenixWiki;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * One place for "is this player allowed to use that" across the Phoenix mods.
 * <p>
 * A <b>gate</b> is a named question about a player ({@code conflux:node/phoenixcore:foo}, {@code solaris:...}). A mod that
 * knows how to answer registers it with {@link #registerGate}, or registers a {@link #registerResolver resolver} for a
 * whole namespace when the ids are too many to list ({@code conflux:flag/<anything>}). A mod that has something to
 * protect either asks directly ({@link #isOpen}) or lets a <b>binding</b> decide ({@link #bind} and {@link #allowed}).
 * <p>
 * A gate <b>expression</b> combines gates: {@code a | b} is "either", {@code a & b} is "both" ({@code &} binds tighter),
 * and {@code !a} is "not". A blank expression is always open; a gate that nobody has registered is always closed, so a
 * typo never opens a feature.
 * <p>
 * Nothing here knows what Conflux is: PhoenixCore registers the Conflux gates, so a gate means the same thing to every
 * mod, and a team that has researched something has it everywhere.
 */
public final class SuiteGates {

    /** Features of {@code feature} in {@code scope} (a dimension id, or {@code *}) are allowed when any expression is open. */
    public record Binding(String feature, String scope, List<String> anyOf) {}

    public static final String ANY_SCOPE = "*";

    private static final Map<String, Predicate<ServerPlayer>> GATES = new ConcurrentHashMap<>();
    private static final Map<String, BiPredicate<String, ServerPlayer>> RESOLVERS = new ConcurrentHashMap<>();
    private static final List<Binding> BINDINGS = new CopyOnWriteArrayList<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private SuiteGates() {}

    /** Registers (or replaces) the gate with this exact id. */
    public static void registerGate(String id, Predicate<ServerPlayer> gate) {
        GATES.put(id, gate);
    }

    public static void unregisterGate(String id) {
        GATES.remove(id);
    }

    /**
     * Answers every gate whose id starts with {@code namespace + ":"}, given the rest of the id: the resolver for
     * {@code conflux} gets {@code node/phoenixcore:foo} for the gate {@code conflux:node/phoenixcore:foo}. Exact gates win.
     */
    public static void registerResolver(String namespace, BiPredicate<String, ServerPlayer> resolver) {
        RESOLVERS.put(namespace, resolver);
    }

    /** Whether a gate (or a resolver for its namespace) exists for this id. */
    public static boolean isKnown(String id) {
        if (GATES.containsKey(id)) return true;
        int colon = id.indexOf(':');
        return colon > 0 && RESOLVERS.containsKey(id.substring(0, colon));
    }

    /** Evaluates a single gate id for the player; an unknown gate is closed. */
    public static boolean isOpenGate(ServerPlayer player, String id) {
        Predicate<ServerPlayer> exact = GATES.get(id);
        if (exact != null) return exact.test(player);
        int colon = id.indexOf(':');
        if (colon > 0) {
            BiPredicate<String, ServerPlayer> resolver = RESOLVERS.get(id.substring(0, colon));
            if (resolver != null) return resolver.test(id.substring(colon + 1), player);
        }
        if (WARNED.add(id)) PhoenixWiki.LOGGER.warn("[SuiteGates] no gate '{}' is registered; it stays closed", id);
        return false;
    }

    /** Evaluates a gate expression ({@code a | b & !c}) for the player. A blank expression is open. */
    public static boolean isOpen(ServerPlayer player, String expression) {
        if (expression == null || expression.isBlank()) return true;
        for (String either : expression.split("\\|")) {
            boolean all = true;
            boolean any = false;
            for (String term : either.split("&")) {
                String id = term.trim();
                if (id.isEmpty()) continue;
                any = true;
                boolean negate = id.startsWith("!");
                if (negate) id = id.substring(1).trim();
                if (isOpenGate(player, id) == negate) {
                    all = false;
                    break;
                }
            }
            if (any && all) return true;
        }
        return false;
    }

    /** Binds a feature (in one scope) to expressions: it is allowed when any of them is open. */
    public static void bind(String feature, String scope, String... anyOfExpressions) {
        if (anyOfExpressions.length == 0) throw new IllegalArgumentException("A binding needs at least one gate");
        BINDINGS.add(new Binding(feature, scope == null || scope.isBlank() ? ANY_SCOPE : scope,
                List.of(anyOfExpressions)));
    }

    public static void unbindAll(String feature) {
        BINDINGS.removeIf(b -> b.feature().equals(feature));
    }

    public static List<Binding> bindings() {
        return new ArrayList<>(BINDINGS);
    }

    /**
     * Whether the feature is allowed for the player in the scope. A feature with no binding for that scope is allowed;
     * with bindings, it is allowed when any of them has an open expression.
     */
    public static boolean allowed(ServerPlayer player, String feature, String scope) {
        boolean bound = false;
        for (Binding binding : BINDINGS) {
            if (!binding.feature().equals(feature)) continue;
            if (!binding.scope().equals(ANY_SCOPE) && !binding.scope().equals(scope)) continue;
            bound = true;
            for (String expression : binding.anyOf()) {
                if (isOpen(player, expression)) return true;
            }
        }
        return !bound;
    }
}
