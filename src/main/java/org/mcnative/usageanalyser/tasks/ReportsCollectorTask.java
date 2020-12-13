package org.mcnative.usageanalyser.tasks;

import net.pretronic.databasequery.api.query.result.QueryResult;
import net.pretronic.databasequery.api.query.result.QueryResultEntry;
import org.mcnative.usageanalyser.*;
import org.mcnative.usageanalyser.organisation.Organisation;
import org.mcnative.usageanalyser.organisation.OrganisationResourceBundle;
import org.mcnative.usageanalyser.organisation.Subscription;
import org.mcnative.usageanalyser.taskinfo.AnalyserTaskInfo;
import org.mcnative.usageanalyser.taskinfo.AnalyserTaskStatus;

import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReportsCollectorTask extends Thread {

    private final McNativeUsageAnalyser usageAnalyser;
    private final AtomicBoolean running;
    private final Deque<AnalyserTaskInfo> queue;
    private final AtomicBoolean collecting;

    public ReportsCollectorTask(McNativeUsageAnalyser usageAnalyser) {
        this.usageAnalyser = usageAnalyser;
        this.queue = new ConcurrentLinkedDeque<>();
        this.running = new AtomicBoolean(true);
        this.collecting = new AtomicBoolean(false);
    }

    public void shutdown() {
        this.running.set(false);
        this.interrupt();
    }

    @Override
    public void run() {
        while (!this.isInterrupted() && running.get()) {
            if(!collecting.get()) {
                AnalyserTaskInfo request = this.queue.poll();
                if(request != null) {
                    analyseHour(request);
                }
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                this.running.set(false);
                return;
            }
        }
    }

    public void addReportsCollectorRequest(AnalyserTaskInfo taskInfo) {
        this.queue.add(taskInfo);
    }

    public void addReportsCollectorRequests(Collection<AnalyserTaskInfo> taskInfos) {
        this.queue.addAll(taskInfos);
    }

    private void analyseHour(AnalyserTaskInfo taskInfo) {
        if(taskInfo.getStatus() == AnalyserTaskStatus.WAITING || taskInfo.getStatus() == AnalyserTaskStatus.RUNNING) {
            this.collecting.set(true);
            taskInfo.updateStatus(AnalyserTaskStatus.RUNNING);
            taskInfo.setOnFinishListener(()-> this.collecting.set(false));
            QueryResult result;
            OrganisationResourceBundle bundle = null;
            int currentIndex = 0;
            while (!(result = this.usageAnalyser.getDatabaseService().getResourceReports(taskInfo.getTime(), taskInfo.getHour(), taskInfo)).isEmpty()) {
                for (int i = 0; i < result.size(); i++) {
                    QueryResultEntry resultEntry = result.get(i);
                    String organisationId = resultEntry.getString("OrganisationId");

                    if(resultEntry.getInt("Count") < 3) {
                        continue;
                    }

                    Subscription subscription = this.usageAnalyser.getDatabaseService().getSubscription(organisationId);
                    if(subscription == null) {
                        continue;
                    }

                    if(bundle == null) {
                        bundle = new OrganisationResourceBundle(new Organisation(organisationId, subscription), taskInfo, currentIndex);
                    }

                    if(!bundle.getOrganisation().getId().equals(organisationId)) {
                        addBundleToCalculator(bundle);
                        bundle = new OrganisationResourceBundle(new Organisation(organisationId, subscription), taskInfo, currentIndex);
                    }

                    bundle.addResultEntry(resultEntry);
                    currentIndex++;
                }

                taskInfo.incrementCollectorCurrentPage();
            }

            if(bundle != null) {
                bundle.setLast(true);
                addBundleToCalculator(bundle);
            } else {
                taskInfo.updateStatus(AnalyserTaskStatus.FINISHED);
            }
        }
    }

    private void addBundleToCalculator(OrganisationResourceBundle bundle) {
        this.usageAnalyser.addOrganisationResourceBundleToCalculator(bundle);
        bundle.getTaskInfo().incrementTotalBundles();
    }
}