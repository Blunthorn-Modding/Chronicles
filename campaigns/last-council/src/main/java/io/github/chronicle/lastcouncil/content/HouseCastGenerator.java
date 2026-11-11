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
        Set<String> used = reservedSecrets(content, previousRoster, committed);
        var secretChoices = content.secrets().stream().filter(x -> !used.contains(x.id()) && x.applicableHouseIds().contains(house) && usable(x.text(),owners.size()))
                .map(x -> new WeightedDraw.Entry<>(x.id(), x.weight(), x)).toList();
        if(secretChoices.size()<content.secretsPerHouse())throw new IllegalArgumentException("Not enough unused eligible secrets for this House. Add secrets or adjust their applicable Houses; existing papers were preserved.");
        var ordered=WeightedDraw.draw(secretChoices,secretChoices.size(),random).stream().map(WeightedDraw.Entry::value).toList();
        var selected=ordered.subList(0,content.secretsPerHouse());
        if(!complete(content,nextRoster,newOwner,selected)){var search=new CompatibleSearch(content,nextRoster,ordered,List.of(newOwner));if(!search.run())throw new IllegalArgumentException(search.exhausted?"Variable dependency search reached its limit. Simplify legacy cross-secret references; existing papers were preserved.":"No unused secret combination can resolve this House's variables. Check legacy providers; existing papers were preserved.");selected=search.result.get(newOwner);}
        Map<String,String> facts = legacyFacts(selected);
        List<String> secrets = new ArrayList<>(), goals = new ArrayList<>();
        for (var secret : selected) secrets.add(render(secret.text(), content, nextRoster, owners, newOwner, localFacts(facts,secret), secret.numbers(), random));
        var goalChoices = content.goals().stream().filter(x -> x.applicableHouseIds().contains(house) && usable(x.text(),owners.size()) && legacyGoalBound(x.text(),facts))
                .map(x -> new WeightedDraw.Entry<>(x.id(), x.weight(), x)).toList();
        for (var entry : WeightedDraw.draw(goalChoices, content.goalsPerHouse(), random))
            goals.add(render(entry.value().text(), content, nextRoster, owners, newOwner, facts, Map.of(), random));
        List<HouseCast.KnownSecret> knowledge = new ArrayList<>();
        for (UUID target : owners) if (!target.equals(newOwner)) {
            var papers = committed.papers().get(target);
            for (String body : papers.secrets())
                if (random.nextInt(100) < content.knowledgeChancePercent())
                    knowledge.add(new HouseCast.KnownSecret(papers.houseId(), body));
        }
        Map<UUID, HouseCast.HousePapers> result = new LinkedHashMap<>(committed.papers());
        result.put(newOwner, new HouseCast.HousePapers(house, secrets, goals, knowledge));
        selected.forEach(secret->used.add(secret.id()));
        return new HouseCast(committed.castId(), nextRoster.revision(), committed.seed(), result,used);
    }
    public static HouseCast generate(CouncilContent content, HouseRoster roster, long seed) {
        Objects.requireNonNull(content); Objects.requireNonNull(roster);
        Random random = new Random(seed);
        UUID castId = new UUID(random.nextLong(), random.nextLong());
        var owners = roster.assignments().keySet().stream().sorted().toList();
        var assigned=allocate(content,roster,random);
        Map<UUID, List<String>> secrets = new LinkedHashMap<>(), goals = new LinkedHashMap<>();
        Map<UUID, List<HouseCast.KnownSecret>> knowledge = new LinkedHashMap<>();
        Map<UUID, Map<String,String>> assignedFacts = new HashMap<>();Set<String> used=new HashSet<>();
        for (UUID owner : owners) {
            knowledge.put(owner,new ArrayList<>());Map<String,String> facts=legacyFacts(assigned.get(owner));
            assignedFacts.put(owner,Map.copyOf(facts));List<String> rendered=new ArrayList<>();
            for(var secret:assigned.get(owner)){used.add(secret.id());rendered.add(render(secret.text(),content,roster,owners,owner,localFacts(facts,secret),secret.numbers(),random));}
            secrets.put(owner,rendered);
        }
        for(UUID owner:owners){String house=roster.assignments().get(owner).houseId();
            var choices=content.goals().stream().filter(x->x.applicableHouseIds().contains(house)&&usable(x.text(),owners.size())&&legacyGoalBound(x.text(),assignedFacts.get(owner))).map(x->new WeightedDraw.Entry<>(x.id(),x.weight(),x)).toList();
            List<String> rendered=new ArrayList<>();for(var entry:WeightedDraw.draw(choices,content.goalsPerHouse(),random))rendered.add(render(entry.value().text(),content,roster,owners,owner,assignedFacts.get(owner),Map.of(),random));goals.put(owner,rendered);
        }
        for(UUID target:owners)for(String body:secrets.get(target))for(UUID observer:owners)
            if(!target.equals(observer)&&random.nextInt(100)<content.knowledgeChancePercent())knowledge.get(observer).add(new HouseCast.KnownSecret(roster.assignments().get(target).houseId(),body));
        Map<UUID,HouseCast.HousePapers> result=new LinkedHashMap<>();for(UUID owner:owners)result.put(owner,new HouseCast.HousePapers(roster.assignments().get(owner).houseId(),secrets.get(owner),goals.get(owner),knowledge.get(owner)));
        return new HouseCast(castId,roster.revision(),seed,result,used);
    }

    /** Capacity-only check; never renders papers, consumes a live seed or reveals an allocation. */
    public static void validateAssignments(CouncilContent content,HouseRoster roster){allocate(content,roster,new Random(0));}

    private static Map<UUID,List<CouncilContent.SecretTemplate>> allocate(CouncilContent content,HouseRoster roster,Random random){
        var owners=roster.assignments().keySet().stream().sorted().toList();List<UUID> slots=new ArrayList<>();
        for(UUID owner:owners){String house=roster.assignments().get(owner).houseId();if(!content.houses().containsKey(house))throw new IllegalArgumentException("unknown assigned house");
            long goals=content.goals().stream().filter(g->g.applicableHouseIds().contains(house)&&usable(g.text(),owners.size())).count();
            if(goals<content.goalsPerHouse())throw new IllegalArgumentException(content.houses().get(house).title()+" has too few eligible House goals. Add goals or adjust their applicable Houses; Other House needs another assigned House.");
            for(int i=0;i<content.secretsPerHouse();i++)slots.add(owner);
        }
        Collections.shuffle(slots,random);Map<Integer,CouncilContent.SecretTemplate> matched=new HashMap<>();
        var choices=content.secrets().stream().filter(s->usable(s.text(),owners.size())&&owners.stream().anyMatch(o->s.applicableHouseIds().contains(roster.assignments().get(o).houseId()))).map(s->new WeightedDraw.Entry<>(s.id(),s.weight(),s)).toList();
        var ordered=WeightedDraw.draw(choices,choices.size(),random).stream().map(WeightedDraw.Entry::value).toList();
        for(var secret:ordered){if(matched.size()==slots.size())break;place(secret,new HashSet<>(),slots,matched,roster);}
        if(matched.size()!=slots.size())throw new IllegalArgumentException("Not enough unique eligible secrets for the assigned Houses. Each secret can be assigned once; add secrets, adjust applicable Houses or reduce secrets per House. Other House needs another assigned House.");
        Map<UUID,List<CouncilContent.SecretTemplate>> result=new LinkedHashMap<>();for(UUID owner:owners)result.put(owner,new ArrayList<>());
        for(int i=0;i<slots.size();i++)result.get(slots.get(i)).add(matched.get(i));result.values().forEach(list->list.sort(Comparator.comparing(CouncilContent.SecretTemplate::id)));
        if(result.entrySet().stream().allMatch(e->complete(content,roster,e.getKey(),e.getValue())))return result;
        var search=new CompatibleSearch(content,roster,ordered);if(search.run())return search.result;
        throw new IllegalArgumentException(search.exhausted?"Variable dependency search reached its limit. Simplify legacy cross-secret references; no papers were committed.":"No unique assignment can resolve all secret and House-goal variables. Check number ranges and legacy variable providers.");
    }
    private static boolean complete(CouncilContent content,HouseRoster roster,UUID owner,List<CouncilContent.SecretTemplate> selected){
        Map<String,String> facts=legacyFacts(selected);
        for(var secret:selected){if(!legacyGoalBound(secret.text(),localFacts(facts,secret)))return false;var m=java.util.regex.Pattern.compile("\\$\\{number\\.([^}]+)}").matcher(secret.text());while(m.find())if(!secret.numbers().containsKey(m.group(1)))return false;}
        String house=roster.assignments().get(owner).houseId();return content.goals().stream().filter(g->g.applicableHouseIds().contains(house)&&usable(g.text(),roster.assignments().size())&&legacyGoalBound(g.text(),facts)).count()>=content.goalsPerHouse();
    }
    /** Old cross-secret fixed-value references may need a compatible combination, not a retry of live draws. */
    private static final class CompatibleSearch {
        final CouncilContent content;final HouseRoster roster;final List<UUID> owners;final Map<UUID,List<CouncilContent.SecretTemplate>> candidates=new HashMap<>();
        final Map<UUID,List<CouncilContent.SecretTemplate>> result=new LinkedHashMap<>();final Set<String> used=new HashSet<>();int remaining=20000;boolean exhausted;
        CompatibleSearch(CouncilContent content,HouseRoster roster,List<CouncilContent.SecretTemplate> ordered){this(content,roster,ordered,roster.assignments().keySet());}
        CompatibleSearch(CouncilContent content,HouseRoster roster,List<CouncilContent.SecretTemplate> ordered,Collection<UUID> targets){this.content=content;this.roster=roster;
            for(UUID owner:targets){String house=roster.assignments().get(owner).houseId();candidates.put(owner,ordered.stream().filter(s->s.applicableHouseIds().contains(house)).toList());}
            owners=targets.stream().sorted(Comparator.<UUID>comparingInt(o->candidates.get(o).size()).thenComparing(Comparator.naturalOrder())).toList();}
        boolean run(){return house(0);}
        boolean house(int index){if(index==owners.size())return true;return choose(index,0,new ArrayList<>());}
        boolean choose(int index,int start,List<CouncilContent.SecretTemplate> selected){
            UUID owner=owners.get(index);if(selected.size()==content.secretsPerHouse()){
                if(!complete(content,roster,owner,selected))return false;result.put(owner,List.copyOf(selected));if(house(index+1))return true;result.remove(owner);return false;}
            var options=candidates.get(owner);if(options.size()-start<content.secretsPerHouse()-selected.size())return false;
            var available=options.subList(start,options.size()).stream().filter(s->!used.contains(s.id())).toList();if(!potential(content,roster,owner,selected,available,content.secretsPerHouse()-selected.size()))return false;
            for(int i=start;i<options.size();i++){var secret=options.get(i);if(used.contains(secret.id()))continue;if(--remaining<0){exhausted=true;return false;}
                used.add(secret.id());selected.add(secret);if(choose(index,i+1,selected))return true;selected.removeLast();used.remove(secret.id());if(exhausted)return false;}
            return false;
        }
    }
    private static Set<String> factKeys(String text){Set<String> keys=new HashSet<>();var m=java.util.regex.Pattern.compile("\\$\\{(fact\\.[^}]+)}").matcher(text);while(m.find())keys.add(m.group(1));return keys;}
    private static boolean potential(CouncilContent content,HouseRoster roster,UUID owner,List<CouncilContent.SecretTemplate> selected,List<CouncilContent.SecretTemplate> available,int slots){
        Map<String,String> known=legacyFacts(selected);Set<String> conflicts=new HashSet<>();selected.forEach(s->conflicts.addAll(s.facts().keySet()));conflicts.removeAll(known.keySet());
        for(var secret:selected){var needed=factKeys(secret.text());needed.removeAll(secret.facts().keySet());if(!canSupply(needed,known,conflicts,available,slots))return false;}
        String house=roster.assignments().get(owner).houseId();return content.goals().stream().filter(g->g.applicableHouseIds().contains(house)&&usable(g.text(),roster.assignments().size())&&canSupply(factKeys(g.text()),known,conflicts,available,slots)).count()>=content.goalsPerHouse();
    }
    private static boolean canSupply(Set<String> needed,Map<String,String> known,Set<String> conflicts,List<CouncilContent.SecretTemplate> available,int slots){
        needed.removeAll(known.keySet());if(needed.isEmpty())return true;if(slots==0||needed.stream().anyMatch(conflicts::contains))return false;
        Set<String> union=new HashSet<>();int most=0;for(var secret:available){int count=0;for(String key:needed)if(secret.facts().containsKey(key)){count++;union.add(key);}most=Math.max(most,count);}
        return union.containsAll(needed)&&most>0&&(needed.size()+most-1)/most<=slots;
    }
    /** Augmenting paths preserve earlier selections while finding another eligible slot. */
    private static boolean place(CouncilContent.SecretTemplate secret,Set<Integer> seen,List<UUID> slots,Map<Integer,CouncilContent.SecretTemplate> matched,HouseRoster roster){
        for(int i=0;i<slots.size();i++){if(!secret.applicableHouseIds().contains(roster.assignments().get(slots.get(i)).houseId())||!seen.add(i))continue;var prior=matched.get(i);if(prior==null||place(prior,seen,slots,matched,roster)){matched.put(i,secret);return true;}}return false;
    }
    private static Set<String> reservedSecrets(CouncilContent content,HouseRoster previous,HouseCast cast){
        if(cast.usedSecretIds()!=null)return new HashSet<>(cast.usedSecretIds());
        Set<String> houses=new HashSet<>();previous.assignments().values().forEach(a->houses.add(a.houseId()));Set<String> reserved=new HashSet<>();
        for(var secret:content.secrets())if(secret.applicableHouseIds().stream().anyMatch(houses::contains))reserved.add(secret.id());return reserved;
    }
    private static Map<String,String> legacyFacts(Collection<CouncilContent.SecretTemplate> selected){
        Map<String,String> values=new HashMap<>();Set<String> ambiguous=new HashSet<>();
        for(var secret:selected)secret.facts().forEach((key,value)->{String old=values.putIfAbsent(key,value);if(old!=null&&!old.equals(value))ambiguous.add(key);});
        ambiguous.forEach(values::remove);return values;
    }
    private static Map<String,String> localFacts(Map<String,String> shared,CouncilContent.SecretTemplate secret){Map<String,String> result=new HashMap<>(shared);result.putAll(secret.facts());return result;}
    private static boolean legacyGoalBound(String text,Map<String,String> facts){var matcher=java.util.regex.Pattern.compile("\\$\\{(fact\\.[^}]+)}").matcher(text);while(matcher.find())if(!facts.containsKey(matcher.group(1)))return false;return true;}
    private static boolean requiresOther(String text){return text.contains("${house.other}")||text.contains("${other.title}");}
    private static boolean usable(String text,int houseCount){return houseCount>1||!requiresOther(text);}
    private static String render(String text,CouncilContent content,HouseRoster roster,List<UUID> owners,UUID owner,Map<String,String> facts,Map<String,io.github.chronicle.core.lore.NumberRange> numbers,Random random){
        Map<String,String> bindings=new HashMap<>(facts);String own=content.houses().get(roster.assignments().get(owner).houseId()).title();bindings.put("house.own",own);bindings.put("house.title",own);
        if(requiresOther(text)){var others=owners.stream().filter(id->!id.equals(owner)).toList();if(others.isEmpty())throw new IllegalArgumentException("Other House requires another assigned House.");String other=content.houses().get(roster.assignments().get(others.get(random.nextInt(others.size()))).houseId()).title();bindings.put("house.other",other);bindings.put("other.title",other);}
        new TreeMap<>(numbers).forEach((name,range)->bindings.put("number."+name,Integer.toString(range.draw(random))));
        bindings.keySet().removeIf(key->!text.contains("${"+key+"}"));String rendered=TextTemplate.render(text,bindings);CouncilContent.text(rendered);return rendered;
    }
}
