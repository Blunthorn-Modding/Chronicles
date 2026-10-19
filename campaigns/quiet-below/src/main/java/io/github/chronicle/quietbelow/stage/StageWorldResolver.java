package io.github.chronicle.quietbelow.stage;

@FunctionalInterface
public interface StageWorldResolver {
    StageWorld resolve(String dimension);
}
