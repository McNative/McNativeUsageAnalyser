package org.mcnative.usageanalyser.tasks;

import org.mcnative.usageanalyser.taskinfo.AnalyserTaskInfo;
import org.mcnative.usageanalyser.taskinfo.AnalyserTaskStatus;
import org.mcnative.usageanalyser.McNativeUsageAnalyser;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

public class HourlyTask extends Thread {

    private final McNativeUsageAnalyser usageAnalyser;
    private final AtomicBoolean running;

    public HourlyTask(McNativeUsageAnalyser usageAnalyser) {
        this.usageAnalyser = usageAnalyser;
        this.running = new AtomicBoolean(true);
    }

    public void shutdown() {
        this.running.set(false);
        this.interrupt();
    }

    private long getSleepTime() {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(1).truncatedTo(ChronoUnit.HOURS);
        Duration duration = Duration.between(start, end);
        return duration.toMillis();
    }

    @Override
    public void run() {
        while (!this.isInterrupted() && running.get()) {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.set(Calendar.HOUR, calendar.get(Calendar.HOUR)-1);
            AnalyserTaskInfo taskInfo = this.usageAnalyser.getDatabaseService().getOrCreateAnalyserTask(calendar.getTimeInMillis(), calendar.get(Calendar.HOUR_OF_DAY));
            if(taskInfo.getStatus() == AnalyserTaskStatus.WAITING) {
                this.usageAnalyser.addReportsCollectorRequest(taskInfo);
            }
            try {
                Thread.sleep(getSleepTime());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                this.running.set(false);
                return;
            }
        }
    }
}
