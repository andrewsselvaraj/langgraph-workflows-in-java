package com.andrewsselvaraj.langgraph.api;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Small helpers shared by the per-project controllers. */
final class Results {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Results() {
    }

    /** Maps a workflow's result map onto a typed response record. */
    static <T> T as(Map<String, Object> result, Class<T> type) {
        return MAPPER.convertValue(result, type);
    }

    /** Builds a workflow input map, skipping null values so the workflow's defaults apply. */
    static Map<String, Object> input(Object... keyValues) {
        Map<String, Object> input = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                input.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return input;
    }
}
