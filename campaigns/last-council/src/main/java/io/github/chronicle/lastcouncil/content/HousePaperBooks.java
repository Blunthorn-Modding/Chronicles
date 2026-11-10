package io.github.chronicle.lastcouncil.content;

import io.github.chronicle.core.books.BookDefinition;
import io.github.chronicle.core.books.BookPages;
import io.github.chronicle.lastcouncil.network.CouncilPayloads.Section;
import java.util.*;

/** One explicitly selected committed paper; never registered in the common book catalogue. */
public final class HousePaperBooks {
    private HousePaperBooks() { }
    public static List<BookDefinition> build(CouncilContent content,HouseCast cast,UUID owner,Section section,int index){
        if(cast==null)throw new IllegalStateException("House papers have not been issued yet.");
        var papers=cast.papers().get(owner);
        if(papers==null)throw new IllegalStateException("You do not have issued House papers.");
        var house=content.houses().get(papers.houseId());
        if(house==null)throw new IllegalStateException("Your House definition is unavailable.");
        String title,body;
        switch(section){
            case INFORMATION->{if(index!=0)throw new IllegalArgumentException("Choose the House information paper.");title="House information";body=String.join("\n\n",house.title(),house.reputation(),house.description(),house.interests());}
            case SECRETS->{title="Secret "+(index+1);body=house.title()+"\n\n"+entry(papers.secrets(),index);}
            case GOALS->{title="House goal "+(index+1);body=house.title()+"\n\n"+entry(papers.goals(),index);}
            case KNOWN_SECRETS->{if(index<0||index>=papers.knownSecrets().size())throw new IllegalArgumentException("Choose one of your known papers.");var known=papers.knownSecrets().get(index);var target=content.houses().get(known.targetHouseId());if(target==null)throw new IllegalStateException("The other House definition is unavailable.");title="Other house secret "+(index+1);body=house.title()+"\n\n"+target.title()+"\n"+known.body();}
            default->throw new IllegalArgumentException("Choose a paper category.");
        }
        var pages=BookPages.paginate(body);int volumes=(pages.size()+BookDefinition.MAX_PAGES-1)/BookDefinition.MAX_PAGES;
        if(volumes>36)throw new IllegalArgumentException("This paper exceeds the physical-copy limit.");
        List<BookDefinition> result=new ArrayList<>();
        for(int volume=0;volume<volumes;volume++){
            String name=title+(volumes>1?" ("+(volume+1)+"/"+volumes+")":"");
            String id="last_council:paper/"+cast.castId()+"/"+owner+"/"+section.name().toLowerCase(Locale.ROOT)+"/"+index+"/"+volume;
            result.add(new BookDefinition(id,name,house.title(),"",pages.subList(volume*BookDefinition.MAX_PAGES,Math.min(pages.size(),(volume+1)*BookDefinition.MAX_PAGES)),BookDefinition.Rule.OFF));
        }
        return List.copyOf(result);
    }
    private static String entry(List<String> entries,int index){if(index<0||index>=entries.size())throw new IllegalArgumentException("Choose one of your issued papers.");return entries.get(index);}
}
