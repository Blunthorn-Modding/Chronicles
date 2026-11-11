package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.lastcouncil.house.HouseDefinition;
import io.github.chronicle.core.lore.WeightedDraw;
import io.github.chronicle.core.lore.NumberRange;
import java.util.*;
import java.util.regex.Pattern;

/** Reusable public definitions and private template pools, never committed cast state. */
public record CouncilContent(Map<String, HouseDefinition> houses, List<SecretTemplate> secrets,
                             List<GoalTemplate> goals, int secretsPerHouse, int goalsPerHouse, int knowledgeChancePercent) {
    public static final int MAX_TEXT = 4096;
    private static final Pattern FACT_KEY = Pattern.compile("fact\\.[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");
    public CouncilContent {
        Objects.requireNonNull(houses); Objects.requireNonNull(secrets); Objects.requireNonNull(goals);
        if (houses.size() > 32 || secrets.size() > 256 || goals.size() > 256 || secretsPerHouse < 0 || secretsPerHouse > 4 || goalsPerHouse < 0 || goalsPerHouse > 4 || knowledgeChancePercent < 0 || knowledgeChancePercent > 100) throw new IllegalArgumentException("content exceeds bounds");
        houses.forEach((id, house) -> { if (house == null || !house.id().equals(id)) throw new IllegalArgumentException("house key mismatch"); });
        houses = Map.copyOf(houses); secrets = List.copyOf(secrets); goals = List.copyOf(goals);
        var ids = new HashSet<String>();
        for (SecretTemplate item : secrets) { if (!ids.add(item.id())) throw new IllegalArgumentException("duplicate secret ID"); checkScope(item.applicableHouseIds(), houses); }
        ids.clear();
        for (GoalTemplate item : goals) { if (!ids.add(item.id())) throw new IllegalArgumentException("duplicate goal ID"); checkScope(item.applicableHouseIds(), houses); }
    }
    private static void checkScope(Set<String> ids, Map<String, HouseDefinition> houses) {
        if (ids.isEmpty() || !houses.keySet().containsAll(ids)) throw new IllegalArgumentException("invalid house scope");
    }
    static void text(String value) {
        Objects.requireNonNull(value);
        if (value.codePointCount(0, value.length()) > MAX_TEXT || value.codePoints().anyMatch(c -> c == 0xA7 || (Character.isISOControl(c) && c != '\n' && c != '\t'))) throw new IllegalArgumentException("invalid template text");
    }
    public record SecretTemplate(String id, int weight, String text, Map<String,String> facts, Map<String,NumberRange> numbers, Set<String> applicableHouseIds) {
        public SecretTemplate(String id, int weight, String text, Map<String,String> facts, Set<String> applicableHouseIds) {
            this(id, weight, text, facts, Map.of(), applicableHouseIds);
        }
        public SecretTemplate {
            new WeightedDraw.Entry<>(id, weight, text); CouncilContent.text(text);
            Objects.requireNonNull(facts); Objects.requireNonNull(numbers); Objects.requireNonNull(applicableHouseIds);
            if (facts.size() > 128 || (!numbers.isEmpty() && facts.size() + numbers.size() > 124) || applicableHouseIds.size() > 32) throw new IllegalArgumentException("template exceeds bounds");
            facts.forEach((key,value) -> { if (key == null || !FACT_KEY.matcher(key).matches()) throw new IllegalArgumentException("invalid fact key"); CouncilContent.text(value); });
            numbers.forEach((key,value) -> { if (key == null || (key.length() > 64 || !key.matches("[A-Za-z_][A-Za-z0-9_]*")) || value == null) throw new IllegalArgumentException("invalid number binding"); });
            facts = Map.copyOf(facts); numbers = Map.copyOf(numbers); applicableHouseIds = Set.copyOf(applicableHouseIds);
        }
    }
    public record GoalTemplate(String id, int weight, String text, Set<String> applicableHouseIds) {
        public GoalTemplate {
            new WeightedDraw.Entry<>(id, weight, text); CouncilContent.text(text);
            Objects.requireNonNull(applicableHouseIds);
            if (applicableHouseIds.size() > 32) throw new IllegalArgumentException("template exceeds bounds");
            applicableHouseIds = Set.copyOf(applicableHouseIds);
        }
    }
}
