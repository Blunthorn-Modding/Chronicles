package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.core.lore.TextTemplate;
import io.github.chronicle.core.lore.WeightedDraw;
import io.github.chronicle.lastcouncil.house.*;
import java.util.*;

public final class HouseCastGenerator {
    private HouseCastGenerator() {}
    /** Adds papers for one new owner without changing any committed papers. */
    public static HouseCast append(CouncilContent content, HouseRoster previousRoster, HouseCast committed,
                                   HouseRoster nextRoster, UUID newOwner, long drawSeed) {
        Objects.requireNonNull(content); Objects.requireNonNull(previousRoster); Objects.requireNonNull(committed);
        Objects.requireNonNull(nextRoster); Objects.requireNonNull(newOwner);
        if (committed.rosterRevision() != previousRoster.revision()
                || !committed.papers().keySet().equals(previousRoster.assignments().keySet()))
            throw new IllegalArgumentException("cast does not match previous roster");
        for (var entry : previousRoster.assignments().entrySet())
            if (!committed.papers().get(entry.getKey()).houseId().equals(entry.getValue().houseId()))
                throw new IllegalArgumentException("cast does not match previous roster");
        if (previousRoster.revision() == Long.MAX_VALUE || nextRoster.revision() != previousRoster.revision() + 1
                || previousRoster.assignments().containsKey(newOwner)
                || nextRoster.assignments().size() != previousRoster.assignments().size() + 1
                || !nextRoster.assignments().containsKey(newOwner))
            throw new IllegalArgumentException("invalid roster extension");
        for (var entry : previousRoster.assignments().entrySet())
            if (!entry.getValue().equals(nextRoster.assignments().get(entry.getKey())))
                throw new IllegalArgumentException("previous assignment changed");
        var owners = nextRoster.assignments().keySet().stream().sorted().toList();
        for (var assignment : nextRoster.assignments().values())
            if (!content.houses().containsKey(assignment.houseId())) throw new IllegalArgumentException("unknown assigned house");
        String house = nextRoster.assignments().get(newOwner).houseId();
        Random random = new Random(drawSeed);
        var secretChoices = content.secrets().stream().filter(x -> x.applicableHouseIds().contains(house))
                .map(x -> new WeightedDraw.Entry<>(x.id(), x.weight(), x)).toList();
        var selected = WeightedDraw.draw(secretChoices, content.secretsPerHouse(), random);
        Map<String,String> facts = new HashMap<>();
        for (var entry : selected) entry.value().facts().forEach((key, value) -> {
            String old = facts.putIfAbsent(key, value);
            if (old != null && !old.equals(value)) throw new IllegalArgumentException("conflicting facts");
        });
        List<String> secrets = new ArrayList<>(), goals = new ArrayList<>();
        for (var entry : selected) secrets.add(render(entry.value().text(), content, nextRoster, owners, newOwner, facts, random));
        var goalChoices = content.goals().stream().filter(x -> x.applicableHouseIds().contains(house))
                .map(x -> new WeightedDraw.Entry<>(x.id(), x.weight(), x)).toList();
        for (var entry : WeightedDraw.draw(goalChoices, content.goalsPerHouse(), random))
            goals.add(render(entry.value().text(), content, nextRoster, owners, newOwner, facts, random));
        List<HouseCast.KnownSecret> knowledge = new ArrayList<>();
        for (UUID target : owners) if (!target.equals(newOwner)) {
            var papers = committed.papers().get(target);
            for (String body : papers.secrets())
                if (random.nextInt(100) < content.knowledgeChancePercent())
                    knowledge.add(new HouseCast.KnownSecret(papers.houseId(), body));
        }
        Map<UUID, HouseCast.HousePapers> result = new LinkedHashMap<>(committed.papers());
        result.put(newOwner, new HouseCast.HousePapers(house, secrets, goals, knowledge));
        return new HouseCast(committed.castId(), nextRoster.revision(), committed.seed(), result);
    }
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
