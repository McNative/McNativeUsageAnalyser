package org.mcnative.usageanalyser.tasks;

import net.pretronic.databasequery.api.query.result.QueryResultEntry;
import org.mcnative.usageanalyser.taskinfo.AnalyserTaskStatus;
import org.mcnative.usageanalyser.McNativeUsageAnalyser;
import org.mcnative.usageanalyser.organisation.OrganisationResourceBundle;
import org.mcnative.usageanalyser.Resource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class EarningCalculatorTask extends Thread {

    private final McNativeUsageAnalyser usageAnalyser;
    private final AtomicBoolean running;
    private final Deque<OrganisationResourceBundle> queue;

    public EarningCalculatorTask(McNativeUsageAnalyser usageAnalyser) {
        this.usageAnalyser = usageAnalyser;
        this.running = new AtomicBoolean(true);
        this.queue = new ConcurrentLinkedDeque<>();
    }

    public void shutdown() {
        this.running.set(false);
        this.interrupt();
    }

    @Override
    public void run() {
        while (!this.isInterrupted() && running.get()) {
            OrganisationResourceBundle bundle = this.queue.poll();
            if(bundle != null) {
                int calculatorCurrentIndex = bundle.getTaskInfo().getCalculatorCurrentIndex();
                int bundleStartIndex = bundle.getStartIndex();

                if(calculatorCurrentIndex > bundleStartIndex) return;

                int start = calculatorCurrentIndex-bundleStartIndex;
                if(start < 0) start = 0;

                BigDecimal totalCalculatedCount = new BigDecimal(0);
                Collection<Resource> resources = new ArrayList<>();

                for (int i = start; i < bundle.getResultEntries().size(); i++) {
                    QueryResultEntry resultEntry = bundle.getResultEntries().get(i);

                    String resourceId = resultEntry.getString("ResourceId");
                    int count = resultEntry.getInt("Count");
                    if(count < 3) continue;
                    if(count > 6) count = 6;

                    this.usageAnalyser.getDatabaseService().addCustomerActivity(System.currentTimeMillis(), resourceId, bundle.getOrganisation().getId());

                    Resource resource = this.usageAnalyser.getDatabaseService().getResource(resourceId);

                    BigDecimal resourcePriceRating = resource.getPriceRating().multiply(new BigDecimal(count));
                    resources.add(resource);

                    totalCalculatedCount = totalCalculatedCount.add(resourcePriceRating);
                    bundle.getTaskInfo().incrementCalculatorCurrentIndex();
                }
                BigDecimal perCountRevenue = bundle.getOrganisation().getSubscription().getHourlyRevenue().divide(totalCalculatedCount,4, RoundingMode.DOWN);//@Todo add rounding mode

                for (Resource resource : resources) {
                    BigDecimal revenue = resource.getPriceRating().multiply(perCountRevenue);

                    this.usageAnalyser.getDatabaseService().addResourceEarning(resource.getId(), revenue, bundle.getTaskInfo().getTime(), bundle.getTaskInfo().getHour());
                }

                bundle.getTaskInfo().incrementCurrentBundle();

                if(bundle.isLast()) {
                    this.usageAnalyser.getDatabaseService().removeCustomerActivity(bundle.getTaskInfo().getTimeWithHour());
                    this.usageAnalyser.getDatabaseService().deleteInactiveCustomers();

                    this.usageAnalyser.getDatabaseService().deleteUsedResourceReports(bundle.getTaskInfo().getTime(), bundle.getTaskInfo().getHour());

                    this.usageAnalyser.getDatabaseService().createAnalyserResourceStatistics(bundle.getTaskInfo().getTime(), bundle.getTaskInfo().getHour());
                    this.usageAnalyser.getDatabaseService().addPaymentTransactions(bundle.getTaskInfo().getTime(), bundle.getTaskInfo().getHour());

                    this.usageAnalyser.getDatabaseService().clearTempResourceEarnings(bundle.getTaskInfo().getTime(), bundle.getTaskInfo().getHour());


                    bundle.getTaskInfo().updateStatus(AnalyserTaskStatus.FINISHED);
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
    }

    public void addBundle(OrganisationResourceBundle bundle) {
        this.queue.add(bundle);
    }
}