package com.alexdamolidis.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * AI generated enrichment for a single assignment.
 *
 * The Anthropic SDK derives a JSON schema from this record and constrains the model's
 * response to match it, so the shape is guaranteed rather than requested. The field
 * descriptions below are sent to the model as part of that schema.
 */
public record Enrichment(

    @JsonPropertyDescription("Urgency from 0 to 4. 0 means the assignment can no longer be submitted.")
    int priority,

    @JsonPropertyDescription("Short justification for the chosen priority.")
    String reasoning,

    @JsonPropertyDescription("Summary of what the assignment asks for, leading with any eligibility restriction.")
    String llmSummary
){}
