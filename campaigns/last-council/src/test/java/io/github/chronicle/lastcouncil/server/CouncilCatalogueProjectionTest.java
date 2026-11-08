package io.github.chronicle.lastcouncil.server;

import io.github.chronicle.lastcouncil.content.CouncilContent;
import io.github.chronicle.lastcouncil.house.HouseDefinition;
import io.github.chronicle.lastcouncil.network.CataloguePayloads.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CouncilCatalogueProjectionTest {
    private static Request request(Category category,String query,int page,int selected,int part){return new Request(UUID.randomUUID(),category,query,page,selected,part);}
    @Test void projectsOnlyDefinitionsAndKeepsLongFactsReachable(){
        var house=new HouseDefinition("test:house","House","Known","Public text","Interests",true);
        String fact="x".repeat(4096);
        var secret=new CouncilContent.SecretTemplate("test:secret",7,"A secret ${fact.value}",Map.of("fact.value",fact),Set.of(house.id()));
        var content=new CouncilContent(Map.of(house.id(),house),List.of(secret),List.of(),1,0,37);
        View first=CouncilCatalogueRuntime.project(content,request(Category.SECRETS,"fact.value",0,0,0),false);
        assertEquals(List.of("test:secret"),first.labels());assertTrue(first.parts()>1);
        StringBuilder assembled=new StringBuilder();
        for(int i=0;i<first.parts();i++)assembled.append(CouncilCatalogueRuntime.project(content,request(Category.SECRETS,"fact.value",0,0,i),false).detail());
        assertTrue(assembled.toString().contains("Weight: 7"));assertTrue(assembled.toString().contains("fact.value: "+fact));
        assertTrue(CouncilCatalogueRuntime.project(content,request(Category.KNOWLEDGE,"",0,0,0),false).detail().contains("37%"));
        assertTrue(CouncilCatalogueRuntime.project(content,request(Category.HOUSES,"missing",0,0,0),false).labels().isEmpty());
    }
    @Test void paginationAndSelectionAreClamped(){
        var house=new HouseDefinition("test:house","House","Known","Public","Interests",false);
        List<CouncilContent.GoalTemplate> goals=new ArrayList<>();for(int i=0;i<8;i++)goals.add(new CouncilContent.GoalTemplate("test:goal"+i,1,"Text "+i,Set.of(house.id())));
        var content=new CouncilContent(Map.of(house.id(),house),List.of(),goals,0,1,0);
        View v=CouncilCatalogueRuntime.project(content,request(Category.GOALS,"",999,999,999),false);
        assertEquals(2,v.pages());assertEquals(1,v.page());assertEquals(1,v.selected());assertEquals(0,v.part());assertTrue(v.detail().contains("goal7"));
    }
}
