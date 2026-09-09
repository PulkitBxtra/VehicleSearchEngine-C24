package com.c24.vehiclesearch.search;

import com.c24.vehiclesearch.api.SearchRequest;
import com.c24.vehiclesearch.api.SearchResponse;
import com.c24.vehiclesearch.catalog.VehicleRepository;
import com.c24.vehiclesearch.search.llm.LlmParser;
import com.c24.vehiclesearch.search.parse.RuleParse;
import com.c24.vehiclesearch.search.parse.RulesParser;
import com.c24.vehiclesearch.search.query.CompiledQuery;
import com.c24.vehiclesearch.search.query.QueryCompiler;
import com.c24.vehiclesearch.search.spec.FilterSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SearchService {

    private final RulesParser rules;
    private final LlmParser llm;
    private final QueryCompiler compiler;
    private final VehicleRepository repository;
    private final Interpreter interpreter;

    public SearchService(RulesParser rules, LlmParser llm, QueryCompiler compiler,
                         VehicleRepository repository, Interpreter interpreter) {
        this.rules = rules;
        this.llm = llm;
        this.compiler = compiler;
        this.repository = repository;
        this.interpreter = interpreter;
    }

    public SearchResponse search(SearchRequest request) {
        long started = System.nanoTime();
        List<String> warnings = new ArrayList<>();

        Resolved resolved = resolve(request, warnings);
        FilterSpec spec = resolved.spec();

        // Contradictory bounds ("over 20L under 5L") would run fine and return an
        // empty page that looks like missing inventory. Short-circuit and say so.
        if (spec.constraints().isUnsatisfiable()) {
            warnings.add("The filters contradict each other, so no vehicle can match.");
            return empty(spec, request, resolved.parser(), warnings, started);
        }

        CompiledQuery query = compiler.compile(spec, request.pageOrDefault(), request.sizeOrDefault());
        var results = repository.search(query);
        long total = repository.count(query);
        var facets = repository.facets(query);

        if (total == 0) {
            // A zero-result search is a demand signal, not just a dead end: it is
            // the clearest evidence of inventory a buyer wanted and we did not have.
            if (spec.freeText() != null) {
                warnings.add("Nothing matched \"" + spec.freeText()
                        + "\". It may be a model we do not stock, or a term this parser "
                        + "does not know yet.");
            } else {
                warnings.add("No vehicles matched. Try relaxing the price or kilometre limit.");
            }
        }

        return new SearchResponse(
                interpreter.describe(spec), spec, results, total,
                request.pageOrDefault(), request.sizeOrDefault(), facets,
                resolved.parser(), millisSince(started), warnings, query.scoreSql());
    }

    /**
     * Resolution order: an explicit FilterSpec beats a sentence, and the
     * deterministic parser runs before any model is consulted.
     */
    private Resolved resolve(SearchRequest request, List<String> warnings) {
        if (request.filters() != null) {
            return new Resolved(request.filters(), "EXPLICIT");
        }
        if (request.query() == null || request.query().isBlank()) {
            return new Resolved(FilterSpec.empty(), "NONE");
        }

        RuleParse parsed = rules.parse(request.query());

        // The deterministic parser is the primary path. When it consumed the whole
        // query there is nothing left for a model to add, so the common formulaic
        // query never costs a call, a round trip, or a token.
        if (parsed.confident()) {
            return new Resolved(parsed.spec(), "RULES");
        }

        // Otherwise escalate the full query — not just the residual, since the
        // parts the rules already understood are the context that disambiguates
        // the rest. The model's answer replaces the rules answer wholesale rather
        // than merging: two specs disagreeing about the same bound has no sensible
        // resolution, and "whichever ran" is a far easier thing to reproduce from
        // a log than "whichever half of each".
        Optional<FilterSpec> fromModel = llm.parse(LlmParser.normalise(request.query()));
        if (fromModel.isPresent() && !fromModel.get().isBlank()) {
            FilterSpec spec = fromModel.get();
            if (!spec.unmapped().isEmpty()) {
                warnings.add("Ignored " + String.join(", ", spec.unmapped())
                        + " — not something this catalogue can filter on.");
            }
            return new Resolved(spec, "LLM");
        }

        // No model, no key, budget spent, or nothing usable came back. The rules
        // result is the floor: degraded, never absent.
        if (parsed.spec().isBlank()) {
            warnings.add("Could not extract any filter from that query; showing the whole catalogue.");
        }
        return new Resolved(parsed.spec(), "RULES");
    }

    private SearchResponse empty(FilterSpec spec, SearchRequest req, String parser,
                                 List<String> warnings, long started) {
        return new SearchResponse(
                interpreter.describe(spec), spec, List.of(), 0,
                req.pageOrDefault(), req.sizeOrDefault(), Map.of(),
                parser, millisSince(started), warnings, null);
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private record Resolved(FilterSpec spec, String parser) {}
}
