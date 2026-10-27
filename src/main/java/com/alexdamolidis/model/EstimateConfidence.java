package com.alexdamolidis.model;

/**
 * How much information an effort estimate was based on. Professors do not always post instructions,
 * so a LOW estimate is flagged on the calendar event as a rough guess.
 */
public enum EstimateConfidence {
    HIGH,
    MEDIUM,
    LOW
}
