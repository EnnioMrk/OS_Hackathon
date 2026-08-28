package com.hackathon.baritonesocket.proto;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public final class Protocol {
    public static final int MAX_LINE_LENGTH = 1024;

    private static final Gson GSON = new Gson();

    private Protocol() {
    }

    public static JsonObject parseLine(String line) throws JsonParseException {
        if (!GSON.fromJson(line, JsonObject.class).isJsonObject()) {
            throw new JsonParseException("expected a JSON object");
        }
        return GSON.fromJson(line, JsonObject.class);
    }

    public static Long getId(JsonObject obj) {
        try {
            return obj.has("id") && obj.get("id").isJsonPrimitive() ? obj.get("id").getAsLong() : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String getCmd(JsonObject obj) {
        return obj.has("cmd") && obj.get("cmd").isJsonPrimitive() ? obj.get("cmd").getAsString() : null;
    }

    public static String hello(int queueSize) {
        return json(null, "hello", "baritone socket bridge ready", queueSize);
    }

    public static String accepted(Long id, int queueSize) {
        return json(id, "accepted", null, queueSize);
    }

    public static String done(Long id, String message) {
        return json(id, "done", message, null);
    }

    public static String error(Long id, String message) {
        return json(id, "error", message, null);
    }

    private static String json(Long id, String status, String message, Integer queueSize) {
        JsonObject obj = new JsonObject();
        if (id != null) {
            obj.addProperty("id", id);
        }
        obj.addProperty("status", status);
        if (message != null) {
            obj.addProperty("message", message);
        }
        if (queueSize != null) {
            obj.addProperty("queue", queueSize);
        }
        return GSON.toJson(obj);
    }
}
