package com.c24.vehiclesearch.search;

import com.c24.vehiclesearch.api.SearchRequest;
import com.c24.vehiclesearch.api.SearchResponse;
import com.c24.vehiclesearch.catalog.VehicleRepository;
import com.c24.vehiclesearch.search.parse.RuleParse;
import com.c24.vehiclesearch.search.parse.RulesParser;
import com.c24.vehiclesearch.search.query.CompiledQuery;
import com.c24.vehiclesearch.search.query.QueryCompiler;
import com.c24.vehiclesearch.search.spec.FilterSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private final RulesParser rules;
    private final QueryCompiler compiler;
    private final VehicleRepository repository;
    private final Interpreter interpreter;

    public SearchService(RulesParser rules, QueryCompiler compiler,
                         VehicleRepository repository, Interpreter interpreter) {
        this.rules = rules;
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

        // Day 2 wires Gemini in here: when !parsed.confident(), escalate the
        // residual to the LLM and merge its spec over this one. The rules result
        // stays the floor, so an LLM outage degrades rather than fails.
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
