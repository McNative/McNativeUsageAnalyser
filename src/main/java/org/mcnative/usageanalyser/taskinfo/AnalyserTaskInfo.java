package org.mcnative.usageanalyser.taskinfo;

import org.mcnative.usageanalyser.McNativeUsageAnalyser;

import java.util.concurrent.TimeUnit;

public class AnalyserTaskInfo {

    private final int id;
    private AnalyserTaskStatus status;
    private final long time;
    private final int hour;
    private int collectorCurrentPage;
    private int calculatorCurrentIndex;

    private int currentBundle;
    private int totalBundles;

    private Runnable onFinishListener;

    public AnalyserTaskInfo(int id, long time, int hour) {
        this(id, AnalyserTaskStatus.WAITING, time, hour, 1, 0, 0, 0);
    }

    public AnalyserTaskInfo(int id, AnalyserTaskStatus status, long time, int hour, int collectorsCurrentPage, int calculatorCurrentIndex, int currentBundle, int totalBundles) {
        this.id = id;
        this.time = time;
        this.hour = hour;
        this.status = status;
        this.collectorCurrentPage = collectorsCurrentPage;
        this.calculatorCurrentIndex = calculatorCurrentIndex;
        this.currentBundle = currentBundle;
        this.totalBundles = totalBundles;
    }

    public int getId() {
        return id;
    }

    public AnalyserTaskStatus getStatus() {
        return status;
    }

    public long getTime() {
        return time;
    }

    public int getHour() {
        return hour;
    }

    public int getCollectorCurrentPage() {
        return collectorCurrentPage;
    }

    public void incrementCollectorCurrentPage() {
        this.collectorCurrentPage++;
        McNativeUsageAnalyser.getInstance().getDatabaseService().incrementAnalyserTaskCollectorPage(this.id, this.collectorCurrentPage);
    }

    public int getCalculatorCurrentIndex() {
        return calculatorCurrentIndex;
    }

    public void incrementCalculatorCurrentIndex() {
        this.calculatorCurrentIndex++;
        McNativeUsageAnalyser.getInstance().getDatabaseService().incrementAnalyserTaskCalculatorPage(this.id, this.calculatorCurrentIndex);
    }

    public void updateStatus(AnalyserTaskStatus status) {
        McNativeUsageAnalyser.getInstance().getDatabaseService().updateAnalyserTaskStatus(this.id, status);
        this.status = status;
        if(status == AnalyserTaskStatus.FINISHED) {
            if(this.onFinishListener != null) {
                this.onFinishListener.run();
            }
        }
    }

    public int getCurrentBundle() {
        return currentBundle;
    }

    public int getTotalBundles() {
        return totalBundles;
    }

    public void incrementCurrentBundle() {
        McNativeUsageAnalyser.getInstance().getDatabaseService().incrementAnalyserTaskCurrentBundle(this.id);
        this.currentBundle++;
    }

    public void incrementTotalBundles() {
        McNativeUsageAnalyser.getInstance().getDatabaseService().incrementAnalyserTaskTotalBundles(this.id);
        this.totalBundles++;
    }

    public void setOnFinishListener(Runnable onFinishListener) {
        this.onFinishListener = onFinishListener;
    }

    public long getTimeWithHour() {
        return getTime()+ TimeUnit.HOURS.toMillis(getHour());
    }
}
