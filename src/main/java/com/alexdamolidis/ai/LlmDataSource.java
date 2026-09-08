package com.alexdamolidis.ai;

public interface LlmDataSource {

    /**
     * Sends the formatted assignment prompt to the LLM and returns the parsed enrichment.
     *
     * Implementations are responsible for unwrapping their own provider response envelope,
     * so callers never depend on a particular vendor's JSON shape.
     *
     * @param prompt the formatted assignment data String
     * @return the enrichment, or null if the model returned nothing usable
     */
    public Enrichment enrich(String prompt);
}
