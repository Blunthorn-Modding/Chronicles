package io.github.chronicle.lastcouncil.council;

import java.util.Collection;

/** Advisory count of remaining declared Regency meetings. */
public final class ElectionPlanning {
    private ElectionPlanning() { }

    public static String warning(SuccessionState election,int meeting,ProceedingsState.Phase phase,Collection<Integer> declared) {
        if(election.stage()!=SuccessionState.Stage.CONTESTED)return "";
        long opportunities=declared.stream().distinct().filter(number->number>meeting).count();
        if((phase==ProceedingsState.Phase.DISCUSSION||phase==ProceedingsState.Phase.RECESS)
                &&declared.contains(meeting)&&election.lastCompletedMeeting()<meeting)opportunities++;
        int ballots=Math.max(1,election.remaining().size()-1);
        if(opportunities>=ballots)return "";
        return "The election may need "+ballots+(ballots==1?" more Regency ballot, with ":" more Regency ballots, with ")
                +opportunities+(opportunities==1?" declared meeting remaining.":" declared meetings remaining.");
    }
}
