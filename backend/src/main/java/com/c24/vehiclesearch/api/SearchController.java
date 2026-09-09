package com.c24.vehiclesearch.api;

import com.c24.vehiclesearch.catalog.BodyType;
import com.c24.vehiclesearch.catalog.FuelType;
import com.c24.vehiclesearch.catalog.Transmission;
import com.c24.vehiclesearch.catalog.Vehicle;
import com.c24.vehiclesearch.catalog.VehicleRepository;
import com.c24.vehiclesearch.search.SearchService;
import com.c24.vehiclesearch.search.concept.ConceptDictionary;
import com.c24.vehiclesearch.search.spec.SortOption;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private final SearchService search;
    private final VehicleRepository vehicles;
    private final ConceptDictionary concepts;

    public SearchController(SearchService search, VehicleRepository vehicles, ConceptDictionary concepts) {
        this.search = search;
        this.vehicles = vehicles;
        this.concepts = concepts;
    }

    @PostMapping("/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return search.search(request);
    }

    @GetMapping("/vehicles/{id}")
    public ResponseEntity<Vehicle> byId(@PathVariable long id) {
        return vehicles.findById(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/schema")
    public SchemaResponse schema() {
        var conceptInfos = concepts.all().entrySet().stream()
                .map(e -> new SchemaResponse.ConceptInfo(
                        e.getKey(),
                        e.getValue().label(),
                        kindOf(e.getValue()),
                        e.getValue().terms()))
                .toList();

        return new SchemaResponse(
                Map.of(
                        "bodyType",     names(BodyType.values()),
                        "fuelType",     names(FuelType.values()),
                        "transmission", names(Transmission.values())),
                conceptInfos,
                vehicles.ranges(),
                vehicles.distinct("city"),
                vehicles.distinct("make"),
                names(SortOption.values()));
    }

    private static String kindOf(com.c24.vehiclesearch.search.concept.ConceptDefinition d) {
        boolean hasConstraint = !d.constraints().equals(
                com.c24.vehiclesearch.search.spec.Constraints.empty());
        boolean hasPreference = !d.preferences().isEmpty();
        if (hasConstraint && hasPreference) return "BOTH";
        return hasConstraint ? "CONSTRAINT" : "PREFERENCE";
    }

    private static <E extends Enum<E>> List<String> names(E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
