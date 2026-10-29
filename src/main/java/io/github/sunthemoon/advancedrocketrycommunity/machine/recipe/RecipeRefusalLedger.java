package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import com.google.gson.JsonParseException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * W193 (W179-F4, docs/17 section 5): the refusals of the mod's recipe serializers during one data-pack reload. A
 * refused recipe is not registered, as before, but vanilla's RecipeManager no longer logs one ERROR with a stack trace
 * per file: the serializer records the refusal here and returns {@code null}, and the reload logs one bounded summary.
 *
 * <p>Lifecycle (AGENTS.md section 3.3): open ledgers are keyed by the reload's {@link ICondition.IContext}, which
 * Forge's {@code ConditionContext} compares by identity, in a weak map. {@link RecipeRefusalReload} opens one when the
 * reload is assembled and removes it in a listener that applies after RecipeManager's. A reload that fails before that
 * listener leaves its ledger to be collected with its context. A ledger holds diagnostics only, never world state.
 */
public final class RecipeRefusalLedger {
    /** The summary, which is the only entry a reload writes for these refusals, never exceeds this. */
    public static final int MAX_SUMMARY_CHARS = 1_024;
    /** Distinct refusal reasons that are counted by name; further reasons count under {@link #OTHER}. */
    static final int MAX_REASONS = 16;
    /** Room for every refusal message of the mod's recipe codecs; the longest has 68 characters. */
    static final int MAX_REASON_CHARS = 72;
    /** The summary names the smallest recipe ids, each cut to {@link #MAX_ID_CHARS}. */
    public static final int MAX_IDS = 8;
    static final int MAX_ID_CHARS = 96;
    /** Refusals per reload whose detail and stack trace go to DEBUG. */
    static final int MAX_DETAILS = 64;
    static final String OTHER = "other";
    private static final int MAX_CAUSES = 8;
    private static final String ELLIPSIS = " ...";
    private static final Logger DETAIL = LoggerFactory.getLogger(RecipeRefusalLedger.class);
    private static final Map<ICondition.IContext, RecipeRefusalLedger> OPEN =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<String, Integer> reasons = new LinkedHashMap<>();
    private final TreeMap<String, String> smallestIds = new TreeMap<>();
    private int count;
    private int details;

    RecipeRefusalLedger() {
    }

    /** Opens the ledger of the reload that owns {@code context}. */
    static RecipeRefusalLedger open(ICondition.IContext context) {
        RecipeRefusalLedger ledger = new RecipeRefusalLedger();
        OPEN.put(context, ledger);
        return ledger;
    }

    /** Removes the ledger of the reload that owns {@code context}, if it is still open. */
    static Optional<RecipeRefusalLedger> close(ICondition.IContext context) {
        return Optional.ofNullable(OPEN.remove(context));
    }

    /** Logs the summary of the ledger for {@code context}, if it refused anything, and removes the ledger. */
    static void flush(ICondition.IContext context, Logger logger) {
        close(context).flatMap(RecipeRefusalLedger::summary).ifPresent(logger::error);
    }

    /**
     * Decodes one recipe for a serializer's {@code fromJson(id, json, context)}. With an open ledger for
     * {@code context}, a refusal ({@link IllegalArgumentException} or {@link JsonParseException}) is recorded and the
     * result is {@code null}, which Forge's RecipeManager skips. Without one (data generation, direct calls,
     * {@link ICondition.IContext#EMPTY}, a call after the reload) the refusal is thrown as before. Other exceptions
     * always propagate.
     */
    public static <T> T decode(ICondition.IContext context, ResourceLocation id, Supplier<T> decoder) {
        RecipeRefusalLedger ledger = context == null ? null : OPEN.get(context);
        if (ledger == null) {
            return decoder.get();
        }
        try {
            return decoder.get();
        } catch (IllegalArgumentException | JsonParseException refusal) {
            ledger.record(id, refusal);
            return null;
        }
    }

    synchronized void record(ResourceLocation id, RuntimeException refusal) {
        count++;
        String reason = reason(id, refusal);
        reasons.merge(reasons.containsKey(reason) || reasons.size() < MAX_REASONS ? reason : OTHER, 1, Integer::sum);
        String name = cut(String.valueOf(id), MAX_ID_CHARS);
        if (smallestIds.putIfAbsent(name, reason) == null && smallestIds.size() > MAX_IDS) {
            smallestIds.pollLastEntry();
        }
        if (details < MAX_DETAILS) {
            details++;
            DETAIL.debug("Refused data-pack recipe {}: {}", name, reason, refusal);
        }
    }

    synchronized int count() {
        return count;
    }

    /** The one ERROR entry for this reload's refusals, or nothing when no recipe was refused. */
    synchronized Optional<String> summary() {
        if (count == 0) {
            return Optional.empty();
        }
        Bounded text = new Bounded("Refused " + count + " data-pack recipe" + (count == 1 ? "" : "s")
                + " of this mod; none is registered. Reasons: ");
        String separator = "";
        for (Map.Entry<String, Integer> reason : reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList()) {
            if (!text.append(separator + reason.getKey() + "=" + reason.getValue())) {
                return Optional.of(text.toString());
            }
            separator = ", ";
        }
        separator = ". First " + smallestIds.size() + " by id: ";
        for (Map.Entry<String, String> id : smallestIds.entrySet()) {
            if (!text.append(separator + id.getKey() + " (" + id.getValue() + ")")) {
                return Optional.of(text.toString());
            }
            separator = ", ";
        }
        return Optional.of(text.toString());
    }

    /**
     * The reason of a refusal: the message of the innermost {@link IllegalArgumentException} or
     * {@link JsonParseException} in its cause chain, which is the codec's own short message rather than a
     * serializer's wrapper, with the recipe id replaced so that equal reasons count together.
     */
    static String reason(ResourceLocation id, Throwable refusal) {
        Throwable reason = refusal;
        Throwable cause = refusal;
        for (int depth = 0; cause != null && depth < MAX_CAUSES; depth++, cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException || cause instanceof JsonParseException) {
                reason = cause;
            }
        }
        String message = reason.getMessage();
        if (message == null || message.isBlank()) {
            message = reason.getClass().getSimpleName();
        }
        // Cut before replacing, so that a very long message costs a bounded replacement.
        String text = cut(message, MAX_ID_CHARS + MAX_REASON_CHARS * 4);
        if (id != null) {
            text = text.replace(id.toString(), "<id>");
        }
        return cut(text, MAX_REASON_CHARS);
    }

    private static String cut(String text, int maximum) {
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    /** Appends whole parts only, and ends with an ellipsis once the next part would pass the bound. */
    private static final class Bounded {
        private final StringBuilder text;
        private boolean full;

        Bounded(String start) {
            text = new StringBuilder(cut(start, MAX_SUMMARY_CHARS - ELLIPSIS.length()));
        }

        boolean append(String part) {
            if (full) {
                return false;
            }
            if (text.length() + part.length() > MAX_SUMMARY_CHARS - ELLIPSIS.length()) {
                text.append(ELLIPSIS);
                full = true;
                return false;
            }
            text.append(part);
            return true;
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
