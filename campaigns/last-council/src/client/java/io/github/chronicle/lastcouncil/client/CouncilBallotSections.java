package io.github.chronicle.lastcouncil.client;

import io.github.chronicle.core.ballots.BallotView;
import io.github.chronicle.lastcouncil.network.ProceedingsPayloads.*;
import java.util.*;

/** Shared read-only motion and ballot presentation. */
final class CouncilBallotSections {
    private CouncilBallotSections() { }
    static String label(String value) {
        return switch(value) {
            case "SUBMITTED" -> "Proposed"; case "AGENDA" -> "On agenda"; case "VOTING" -> "Vote open";
            case "RECORDED" -> "Recorded"; case "WITHDRAWN" -> "Withdrawn"; case "DEFERRED" -> "Deferred";
            case "TWO_THIRDS" -> "Two thirds of the opening electorate"; case "MAJORITY" -> "Majority of the opening electorate";
            case "PUBLIC" -> "Public"; case "SECRET" -> "Secret"; case "OPEN" -> "Open"; case "CLOSED" -> "Closed"; case "VOID" -> "Void";
            default -> value;
        };
    }
    static String option(BallotView b,String id) { return b.options().stream().filter(o->o.id().equals(id)).map(o->o.label()).findFirst().orElse(id); }
    static void motion(List<CouncilScreen.SectionCard> cards,Detail d) {
        cards.add(new CouncilScreen.SectionCard("Question",d.title()));
        cards.add(new CouncilScreen.SectionCard("Proposal",d.text()));
        cards.add(new CouncilScreen.SectionCard("Proposed by",d.proposer()));
        cards.add(new CouncilScreen.SectionCard("Status",label(d.status())));
        cards.add(new CouncilScreen.SectionCard("Vote type",label(d.visibility())));
        cards.add(new CouncilScreen.SectionCard("Decision rule",label(d.threshold())));
        if(!d.sponsors().isEmpty())cards.add(new CouncilScreen.SectionCard("Sponsors",String.join(", ",d.sponsors())));
        if(!d.notes().isEmpty())cards.add(new CouncilScreen.SectionCard("Notes",String.join("; ",d.notes())));
        for(AttemptView a:d.attempts()) attempt(cards,a);
    }
    static void attempt(List<CouncilScreen.SectionCard> cards,AttemptView a) {
        BallotView b=a.ballot();
        String status=label(b.status().name());
        cards.add(new CouncilScreen.SectionCard("Ballot attempt","Meeting "+a.meeting()+"\nStatus: "+status+"\nVote type: "+label(b.visibility().name())+"\nDecision rule: "+b.threshold()+" of "+b.electorateLabels().size()+" required"));
        if(b.reason()!=null&&!b.reason().isBlank())cards.add(new CouncilScreen.SectionCard("Reason",b.reason()));
        if(b.status().name().equals("CLOSED")) {
            StringBuilder result=new StringBuilder();
            if(!b.passedOptions().isEmpty())result.append("Passed: ").append(String.join(", ",b.passedOptions().stream().map(id->option(b,id)).toList()));
            else result.append("No option passed");
            b.totals().forEach((id,count)->result.append("\n").append(option(b,id)).append(": ").append(count));
            cards.add(new CouncilScreen.SectionCard("Result",result.toString()));
        }
        if(b.visibility().name().equals("PUBLIC")) {
                for(var o:b.options()) {
                    List<String> names=a.voters().stream().filter(v->o.id().equals(b.namedVotes().get(v.house()))).map(Voter::name).toList();
                    if(!names.isEmpty())cards.add(new CouncilScreen.SectionCard(option(b,o.id()),String.join(", ",names)));
                }
                List<String> missing=a.voters().stream().filter(v->!b.namedVotes().containsKey(v.house())).map(Voter::name).toList();
                if(!missing.isEmpty())cards.add(new CouncilScreen.SectionCard("Not cast",String.join(", ",missing)));
        }
    }
}
