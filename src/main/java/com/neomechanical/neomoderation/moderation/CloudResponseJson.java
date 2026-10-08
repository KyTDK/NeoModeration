package com.neomechanical.neomoderation.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;

/** Strict object parsing shared by moderation and trial responses. */
final class CloudResponseJson {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    private CloudResponseJson() { }

    static JsonObject parseObject(String body) {
        return JSON.fromJson(body, JsonObject.class);
    }
}
