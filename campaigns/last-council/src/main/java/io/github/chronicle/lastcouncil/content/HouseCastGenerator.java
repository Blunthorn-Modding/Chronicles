package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.core.lore.TextTemplate;
import io.github.chronicle.core.lore.WeightedDraw;
import io.github.chronicle.lastcouncil.house.*;
import java.util.*;

public final class HouseCastGenerator {
    private HouseCastGenerator() {}
    public static HouseCast generate(CouncilContent content, HouseRoster roster, long seed) {
        Objects.requireNonNull(content); Objects.requireNonNull(roster);
        Random random = new Random(seed);
        UUID castId = new UUID(random.nextLong(), random.nextLong());
        var owners = roster.assignments().keySet().stream().sorted().toList();
        Map<UUID, List<String>> secrets = new LinkedHashMap<>(), goals = new LinkedHashMap<>();
        Map<UUID, List<HouseCast.KnownSecret>> knowledge = new LinkedHashMap<>();
        Map<UUID, Map<String,String>> assignedFacts = new HashMap<>();
        for (UUID owner : owners) {
            var assignment = roster.assignments().get(owner);
            if (!content.houses().containsKey(assignment.houseId())) throw new IllegalArgumentException("unknown assigned house");
            knowledge.put(owner, new ArrayList<>());
            var choices = content.secrets().stream().filter(x -> x.applicableHouseIds().contains(assignment.houseId())).map(x -> new WeightedDraw.Entry<>(x.id(), x.weight(), x)).toList();
            var selected = WeightedDraw.draw(choices, content.secretsPerHouse(), random);
            Map<String,String> facts = new HashMap<>();
            for (var entry : selected) entry.value().facts().forEach((key,value) -> { String old = facts.putIfAbsent(key,value); if (old != null && !old.equals(value)) throw new IllegalArgumentException("conflicting facts"); });
            assignedFacts.put(owner, Map.copyOf(facts));
            List<String> rendered = new ArrayList<>();
            for (var entry : selected) rendered.add(render(entry.value().text(), content, roster, owners, owner, facts, random));
            secrets.put(owner, rendered);
        }
        for (UUID owner : owners) {
            String house = roster.assignments().get(owner).houseId();
            var choices = content.goals().stream().filter(x -> x.applicableHouseIds().contains(house)).map(x -> new WeightedDraw.Entry<>(x.id(), x.weight(), x)).toList();
            List<String> rendered = new ArrayList<>();
            for (var entry : WeightedDraw.draw(choices, content.goalsPerHouse(), random)) rendered.add(render(entry.value().text(), content, roster, owners, owner, assignedFacts.get(owner), random));
            goals.put(owner, rendered);
        }
        for (UUID target : owners) for (String body : secrets.get(target)) for (UUID observer : owners)
            if (!target.equals(observer) && random.nextInt(100) < content.knowledgeChancePercent()) knowledge.get(observer).add(new HouseCast.KnownSecret(roster.assignments().get(target).houseId(), body));
        Map<UUID, HouseCast.HousePapers> result = new LinkedHashMap<>();
        for (UUID owner : owners) result.put(owner, new HouseCast.HousePapers(roster.assignments().get(owner).houseId(), secrets.get(owner), goals.get(owner), knowledge.get(owner)));
        return new HouseCast(castId, roster.revision(), seed, result);
    }
    private static String render(String text, CouncilContent content, HouseRoster roster, List<UUID> owners, UUID owner, Map<String,String> facts, Random random) {
        Map<String,String> bindings = new HashMap<>(facts);
        bindings.put("house.title", content.houses().get(roster.assignments().get(owner).houseId()).title());
        if (text.contains("${other.title}")) {
            var others = owners.stream().filter(id -> !id.equals(owner)).toList();
            if (others.isEmpty()) throw new IllegalArgumentException("other house required");
            bindings.put("other.title", content.houses().get(roster.assignments().get(others.get(random.nextInt(others.size()))).houseId()).title());
        }
        String rendered = TextTemplate.render(text, bindings);
        CouncilContent.text(rendered);
        return rendered;
    }
}
