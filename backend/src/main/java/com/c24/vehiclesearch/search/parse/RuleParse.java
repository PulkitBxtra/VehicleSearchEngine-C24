package com.c24.vehiclesearch.search.parse;

import com.c24.vehiclesearch.search.spec.FilterSpec;

/**
 * @param spec     what the rules understood
 * @param residual text left over after every matcher ran; becomes free-text
 *                 search, or evidence that we failed to understand something
 * @param confident true when the rules consumed enough of the query that
 *                  escalating to the LLM would not add anything
 */
public record RuleParse(FilterSpec spec, String residual, boolean confident) {}
