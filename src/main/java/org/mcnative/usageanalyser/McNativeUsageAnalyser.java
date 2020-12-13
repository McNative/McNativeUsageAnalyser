package org.mcnative.usageanalyser;

import io.sentry.Sentry;
import net.pretronic.libraries.concurrent.TaskScheduler;
import net.pretronic.libraries.concurrent.simple.SimpleTaskScheduler;
import org.mcnative.usageanalyser.organisation.OrganisationResourceBundle;
import org.mcnative.usageanalyser.taskinfo.AnalyserTaskInfo;
import org.mcnative.usageanalyser.tasks.EarningCalculatorTask;
import org.mcnative.usageanalyser.tasks.HourlyTask;
import org.mcnative.usageanalyser.tasks.ReportsCollectorTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimeZone;

public class McNativeUsageAnalyser {

    private static McNativeUsageAnalyser INSTANCE;
    private static final Logger LOGGER = LoggerFactory.getLogger(McNativeUsageAnalyser.class);

    private final TaskScheduler scheduler;
    private final Config config;
    private final DatabaseService databaseService;

    private final HourlyTask hourlyTask;
    private final ReportsCollectorTask reportsCollectorTask;
    private final EarningCalculatorTask earningCalculatorTask;

    public McNativeUsageAnalyser() {
        INSTANCE = this;
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        this.scheduler = new SimpleTaskScheduler();
        this.config = new Config();
        initSentry();
        this.databaseService = new DatabaseService(this).connect();
        this.hourlyTask = new HourlyTask(this);
        this.earningCalculatorTask = new EarningCalculatorTask(this);
        this.reportsCollectorTask = new ReportsCollectorTask(this);
        continuePausedAnalyserTasks();
        startTasks();
    }

    private void initSentry() {
        if(this.config.getEnvironment().equalsIgnoreCase("production")) {
            String dsn = Config.getenvOrNull("SENTRY_DSN");
            if(dsn != null) {
                Sentry.init(options -> options.setDsn(dsn));
            }
        }
    }

    protected void onShutdown() {
        LOGGER.info("Shutting down...");
        this.databaseService.shutdown();
        this.reportsCollectorTask.shutdown();
        this.earningCalculatorTask.shutdown();
        this.hourlyTask.shutdown();
        LOGGER.info("Shutdown");
    }

    public TaskScheduler getScheduler() {
        return scheduler;
    }

    public Config getConfig() {
        return config;
    }

    public DatabaseService getDatabaseService() {
        return databaseService;
    }

    public void addOrganisationResourceBundleToCalculator(OrganisationResourceBundle bundle) {
        this.earningCalculatorTask.addBundle(bundle);
    }

    public void addReportsCollectorRequest(AnalyserTaskInfo taskInfo) {
        this.reportsCollectorTask.addReportsCollectorRequest(taskInfo);
    }

    private void startTasks() {
        this.hourlyTask.start();
        this.reportsCollectorTask.start();
        this.earningCalculatorTask.start();
    }

    private void continuePausedAnalyserTasks() {
        this.reportsCollectorTask.addReportsCollectorRequests(this.databaseService.getWaitingAnalyserTasks());
    }

    public static McNativeUsageAnalyser getInstance() {
        return INSTANCE;
    }
}
