package com.alexdamolidis.ai;

import com.alexdamolidis.model.EstimateConfidence;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * AI generated enrichment for a single assignment.
 *
 * The Anthropic SDK derives a JSON schema from this record and constrains the model's
 * response to match it, so the shape is guaranteed rather than requested. The field
 * descriptions below are sent to the model as part of that schema.
 *
 * Priority is not part of the response; it is recalculated on every run from the estimate
 * and the time left. The SDK sends schema properties in alphabetical order, so the order of
 * the components here does not control the order the model writes them in.
 */
public record Enrichment(

    @JsonPropertyDescription("Summary of what the assignment asks for, leading with any eligibility restriction.")
    String llmSummary,

    @JsonPropertyDescription("Short justification for the effort and difficulty estimate.")
    String reasoning,

    @JsonPropertyDescription("Total hours a typical student needs to complete the assignment, including reading, research or studying, the work itself, and preparing the submission.")
    double estimatedHours,

    @JsonPropertyDescription("How demanding the work is, from 1 (trivial) to 5 (very demanding), independent of length.")
    int difficulty,

    @JsonPropertyDescription("How much information the estimate is based on. HIGH: detailed instructions or a rubric. MEDIUM: partial instructions. LOW: little beyond the title and course.")
    EstimateConfidence confidence
){}
