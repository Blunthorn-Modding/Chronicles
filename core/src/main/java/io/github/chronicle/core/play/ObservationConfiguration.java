package io.github.chronicle.core.play;

import com.google.gson.JsonObject;
import io.github.chronicle.core.authoring.AuthoringSite;

import java.util.Set;

/** Strict finite UI payload for one observation binding. */
public record ObservationConfiguration(PlayState.Mode mode, String regionId, String requiredFlag, long delaySeconds) {
    public static final int MAX_JSON_LENGTH = 512;
    public static final long MAX_DELAY_SECONDS = 604_800;
    private static final Set<String> FIELDS = Set.of("mode", "regionId", "requiredFlag", "delaySeconds");

    public ObservationConfiguration {
        if (mode == null) throw new IllegalArgumentException("observation mode is required");
        if (regionId == null) throw new IllegalArgumentException("observation region ID is required; use an empty string for no region");
        if (!regionId.isEmpty() && !AuthoringSite.ID.matcher(regionId).matches()) throw new IllegalArgumentException("observation region ID is invalid");
        if (requiredFlag == null) throw new IllegalArgumentException("required flag is required; use an empty string for no prerequisite");
        if (!requiredFlag.isEmpty()) ObservationCatalog.requireIdentifier(requiredFlag, "required flag");
        if (delaySeconds < 0 || delaySeconds > MAX_DELAY_SECONDS) throw new IllegalArgumentException("observation delay must be between 0 and " + MAX_DELAY_SECONDS + " seconds");
        if (mode == PlayState.Mode.ENTRY && regionId.isEmpty()) throw new IllegalArgumentException("entry observations require a region");
        if (mode == PlayState.Mode.TIMER && delaySeconds < 1) throw new IllegalArgumentException("timer observations require a positive delay");
        if (mode == PlayState.Mode.CONDITION && requiredFlag.isEmpty()) throw new IllegalArgumentException("condition observations require a flag");
        if (mode != PlayState.Mode.TIMER) delaySeconds = 0;
    }

    public String encode() {
        JsonObject object = new JsonObject();
        object.addProperty("mode", mode.name());
        object.addProperty("regionId", regionId);
        object.addProperty("requiredFlag", requiredFlag);
        object.addProperty("delaySeconds", delaySeconds);
        String encoded = object.toString();
        if (encoded.length() > MAX_JSON_LENGTH) throw new IllegalArgumentException("observation configuration exceeds " + MAX_JSON_LENGTH + " characters");
        return encoded;
    }

    public static ObservationConfiguration decode(String encoded) {
        if (encoded == null || encoded.length() > MAX_JSON_LENGTH) throw new IllegalArgumentException("observation configuration must be at most " + MAX_JSON_LENGTH + " characters");
        JsonObject object = PlayJson.object(encoded, "observation configuration");
        PlayJson.fields(object, FIELDS, "observation configuration");
        PlayState.Mode mode = PlayJson.enumeration(PlayJson.required(object, "mode"), PlayState.Mode.class, "observation mode");
        return new ObservationConfiguration(mode,
                PlayJson.string(PlayJson.required(object, "regionId"), "observation region ID"),
                PlayJson.string(PlayJson.required(object, "requiredFlag"), "required flag"),
                PlayJson.number(PlayJson.required(object, "delaySeconds"), "observation delay"));
    }
}
