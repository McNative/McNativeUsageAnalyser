package org.mcnative.usageanalyser.organisation;

import net.pretronic.databasequery.api.query.result.QueryResultEntry;
import org.mcnative.usageanalyser.taskinfo.AnalyserTaskInfo;

import java.util.ArrayList;
import java.util.List;

public class OrganisationResourceBundle {

    private final Organisation organisation;
    private final AnalyserTaskInfo taskInfo;
    private final int startIndex;
    private final List<QueryResultEntry> resultEntries;
    private boolean last;

    public OrganisationResourceBundle(Organisation organisation, AnalyserTaskInfo taskInfo, int startIndex) {
        this.organisation = organisation;
        this.taskInfo = taskInfo;
        this.startIndex = startIndex;
        this.resultEntries = new ArrayList<>();
        this.last = false;
    }

    public Organisation getOrganisation() {
        return organisation;
    }

    public AnalyserTaskInfo getTaskInfo() {
        return taskInfo;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public List<QueryResultEntry> getResultEntries() {
        return resultEntries;
    }

    public void addResultEntry(QueryResultEntry resultEntry) {
        this.resultEntries.add(resultEntry);
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }
}
