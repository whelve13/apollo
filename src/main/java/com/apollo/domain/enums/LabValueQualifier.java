package com.apollo.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Marks a censored lab value: the numeric value then holds the detection bound
 * ("&lt;5" is stored as LESS_THAN with numericValue 5). Serialized as "&lt;" / "&gt;" on the wire.
 */
public enum LabValueQualifier {
    LESS_THAN("<"),
    GREATER_THAN(">");

    private final String symbol;

    LabValueQualifier(String symbol) {
        this.symbol = symbol;
    }

    @JsonValue
    public String getSymbol() {
        return symbol;
    }
}
