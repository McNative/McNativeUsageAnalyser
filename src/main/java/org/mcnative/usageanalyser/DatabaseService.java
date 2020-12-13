package org.mcnative.usageanalyser;

import net.pretronic.databasequery.api.Database;
import net.pretronic.databasequery.api.collection.DatabaseCollection;
import net.pretronic.databasequery.api.driver.DatabaseDriver;
import net.pretronic.databasequery.api.driver.DatabaseDriverFactory;
import net.pretronic.databasequery.api.query.SearchOrder;
import net.pretronic.databasequery.api.query.result.QueryResult;
import net.pretronic.databasequery.api.query.result.QueryResultEntry;
import net.pretronic.databasequery.sql.SQLDatabase;
import org.mcnative.usageanalyser.organisation.Subscription;
import org.mcnative.usageanalyser.taskinfo.AnalyserTaskInfo;
import org.mcnative.usageanalyser.taskinfo.AnalyserTaskStatus;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DatabaseService {

    private final McNativeUsageAnalyser usageAnalyser;
    private final DatabaseDriver databaseDriver;
    private final Database database;

    private final DatabaseCollection resource;
    private final DatabaseCollection resourceReporting;
    private final DatabaseCollection subscriptionActive;
    private final DatabaseCollection resourceCustomers;

    private final DatabaseCollection analyserTasks;
    private final DatabaseCollection resourceEarnings;

    private final DatabaseCollection resourceStatisticsEarning;

    private final DatabaseCollection paymentTransactions;

    public DatabaseService(McNativeUsageAnalyser usageAnalyser) {
        this.usageAnalyser = usageAnalyser;

        this.databaseDriver = DatabaseDriverFactory.create("McNativeUsageAnalyser", this.usageAnalyser.getConfig().getStorageConfiguration());
        this.databaseDriver.connect();

        this.database = databaseDriver.getDatabase(usageAnalyser.getConfig().getDatabaseName());

        this.resource = database.getCollection("mcnative_resource");
        this.resourceReporting = database.getCollection("mcnative_resource_reporting");
        this.subscriptionActive = database.getCollection("mcnative_subscription_active");
        this.analyserTasks = database.getCollection("system_mcnative-usage-analyser-tasks");
        this.resourceCustomers = database.getCollection("mcnative_resource_customers");
        this.resourceEarnings = database.getCollection("system_mcnative-usage-analyser-resource-earnings");

        this.resourceStatisticsEarning = database.getCollection("mcnative_resource_statistics_earnings");

        this.paymentTransactions = database.getCollection("mcnative_payment_transactions");
    }

    protected DatabaseService connect() {
        return this;
    }

    protected void shutdown() {
        this.databaseDriver.disconnect();
    }

    public void addCustomerActivity(long time, String resourceId, String organisationId) {
        QueryResultEntry resultEntry = findCustomer(resourceId, organisationId);
        Timestamp now = new Timestamp(time);
        if(resultEntry != null) {
            this.resourceCustomers.update()
                    .add("ActivityCount", 1.0)
                    .set("LastUse", now)
                    .where("ResourceId", resourceId)
                    .where("OrganisationId", organisationId)
                    .execute();
        } else {
            this.resourceCustomers.insert()
                    .set("ResourceId", resourceId)
                    .set("OrganisationId", organisationId)
                    .set("FirstUse", now)
                    .set("LastUse", now)
                    .set("ActivityCount", 1.0)
                    .execute();
        }
    }

    public void removeCustomerActivity(long time) {
        this.resourceCustomers.update()
                .subtract("ActivityCount", 0.8)
                .whereLower("LastUse", new Timestamp(time-TimeUnit.HOURS.toMillis(1)))
                .execute();
    }

    public void deleteInactiveCustomers() {
        this.resourceCustomers.delete()
                .whereLower("ActivityCount", 0.0)
                .execute();
    }

    private QueryResultEntry findCustomer(String resourceId, String organisationId) {
        return this.resourceCustomers.find()
                .get("Id")
                .get("ActivityCount")
                .where("ResourceId", resourceId)
                .where("OrganisationId", organisationId)
                .execute().firstOrNull();
    }

    public AnalyserTaskInfo getOrCreateAnalyserTask(long time, int hour) {
        AnalyserTaskInfo taskInfo = getAnalyserTask(time, hour);
        if(taskInfo == null) {
            taskInfo = createAnalyserTask(time, hour);
        }
        return taskInfo;
    }

    public AnalyserTaskInfo createAnalyserTask(long time, int hour) {
        int id = this.analyserTasks.insert()
                .set("Status", AnalyserTaskStatus.WAITING)
                .set("Hour", hour)
                .executeAndGetGeneratedKeyAsInt("Id");
        return new AnalyserTaskInfo(id, time, hour);
    }

    public AnalyserTaskInfo getAnalyserTask(long time, int hour) {
        QueryResult result = this.analyserTasks.find()
                .whereBetween("Time", new Timestamp(getStartDayMillis(time)), new Timestamp(getEndDayMillis(time)))
                .where("Hour", hour)
                .execute();
        QueryResultEntry resultEntry = result.firstOrNull();
        if(resultEntry == null) return null;
        return fromResultEntry(resultEntry);
    }

    public Collection<AnalyserTaskInfo> getWaitingAnalyserTasks() {
        Collection<AnalyserTaskInfo> taskInfos = new ArrayList<>();
        this.analyserTasks.find()
                .whereIn("Status", AnalyserTaskStatus.WAITING, AnalyserTaskStatus.RUNNING)
                .execute()
                .loadIn(taskInfos, this::fromResultEntry);
        return taskInfos;
    }

    private AnalyserTaskInfo fromResultEntry(QueryResultEntry resultEntry) {
        return new AnalyserTaskInfo(resultEntry.getInt("Id"),
                AnalyserTaskStatus.valueOf(resultEntry.getString("Status")),
                ((Timestamp)resultEntry.getObject("Time")).getTime(),
                resultEntry.getInt("Hour"),
                resultEntry.getInt("CollectorCurrentPage"),
                resultEntry.getInt("CalculatorCurrentIndex"),
                resultEntry.getInt("CurrentBundle"),
                resultEntry.getInt("TotalBundles"));
    }

    public void updateAnalyserTaskStatus(int id, AnalyserTaskStatus status) {
        this.analyserTasks.update()
                .set("Status", status)
                .where("Id", id)
                .execute();
    }

    public void incrementAnalyserTaskCollectorPage(int taskId, int page) {
        this.analyserTasks.update()
                .set("CollectorCurrentPage", page)
                .where("Id", taskId)
                .execute();
    }

    public void incrementAnalyserTaskCalculatorPage(int id, int index) {
        this.analyserTasks.update()
                .set("CalculatorCurrentIndex", index)
                .where("Id", id)
                .execute();
    }

    public void incrementAnalyserTaskTotalBundles(int id) {
        this.analyserTasks.update()
                .add("TotalBundles", 1)
                .where("Id", id)
                .execute();
    }

    public void incrementAnalyserTaskCurrentBundle(int id) {
        this.analyserTasks.update()
                .add("CurrentBundle", 1)
                .where("Id", id)
                .execute();
    }

    public QueryResult getResourceReports(long time, int hour, AnalyserTaskInfo taskInfo) {
        return this.resourceReporting.find()
                .whereBetween("FirstContact", new Timestamp(getStartDayMillis(time)), new Timestamp(getEndDayMillis(time)))
                .where("Hour", hour)
                .orderBy("OrganisationId", SearchOrder.ASC)
                .page(taskInfo.getCollectorCurrentPage(), 1000).execute();
    }

    public Subscription getSubscription(String organisationId) {
        QueryResultEntry resultEntry = this.subscriptionActive.find()
                .where("OrganisationId", organisationId)
                .execute()
                .firstOrNull();
        if(resultEntry == null) return null;
        return new Subscription(organisationId, resultEntry.getBigDecimal("Revenue"));
    }

    public Resource getResource(String resourceId) {
        QueryResultEntry resultEntry = this.resource.find()
                .where("Id", resourceId)
                .execute()
                .firstOrNull();
        if(resultEntry == null) return null;
        return new Resource(resourceId, resultEntry.getString("OwnerId"), resultEntry.getBigDecimal("PriceRating"));
    }

    public void addResourceEarning(String resourceId, BigDecimal earning, long time, int hour) {
        QueryResultEntry resultEntry = this.resourceEarnings.find()
                .get("Id")
                .whereBetween("Time", new Timestamp(getStartDayMillis(time)), new Timestamp(getEndDayMillis(time)))
                .where("Hour", hour)
                .execute().firstOrNull();
        if(resultEntry == null) {
            this.resourceEarnings.insert()
                    .set("ResourceId", resourceId)
                    .set("Earning", earning)
                    .set("Hour", hour)
                    .execute();
        } else {
            this.resourceEarnings.update()
                    .add("Earning", earning)
                    .where("Id", resultEntry.getInt("Id"))
                    .execute();
        }
    }

    public long getStartDayMillis(long time) {
        Calendar startDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        startDay.setTimeInMillis(time);
        startDay.set(Calendar.HOUR, 0);
        startDay.set(Calendar.MILLISECOND, 0);
        startDay.set(Calendar.SECOND, 0);
        startDay.set(Calendar.MINUTE, 0);
        return startDay.getTimeInMillis();
    }

    public long getEndDayMillis(long time) {
        Calendar endDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        endDay.setTimeInMillis(time);
        endDay.set(Calendar.HOUR, 23);
        endDay.set(Calendar.MILLISECOND, 999);
        endDay.set(Calendar.SECOND, 59);
        endDay.set(Calendar.MINUTE, 59);
        return endDay.getTimeInMillis();
    }

    public void createAnalyserResourceStatistics(long time, int hour) {
        SQLDatabase sqlDatabase = (SQLDatabase) this.database;
        sqlDatabase.executeUpdateQuery(String.format("INSERT into `%s`.`%s` SELECT * from `%s`.`%s` where `Time` BETWEEN ? AND ? AND `Hour`=?",
                sqlDatabase.getName(),
                this.resourceStatisticsEarning.getName(),
                sqlDatabase.getName(),
                this.resourceEarnings.getName()),
                true, preparedStatement -> {
                    preparedStatement.setTimestamp(1, new Timestamp(getStartDayMillis(time)));
                    preparedStatement.setTimestamp(2, new Timestamp(getEndDayMillis(time)));
                    preparedStatement.setInt(3, hour);
                });
    }

    public void addPaymentTransactions(long time, int hour) {
        for (QueryResultEntry resultEntry : this.resourceEarnings.find().execute()) {
            createPaymentTransaction(resultEntry.getString("ResourceId"), resultEntry.getBigDecimal("Earning"));
        }
    }

    public void createPaymentTransaction(String resourceId, BigDecimal amount) {
        String status = amount.compareTo(new BigDecimal(100)) < 0 ? "APPROVED" : "WAITING_FOR_APPROVAL";
        this.paymentTransactions.insert()
                .set("Id", UUID.randomUUID().toString())
                .set("FromOrganisationId", this.usageAnalyser.getConfig().getPretronicOrganisationId())
                .set("ToOrganisationId", getResource(resourceId).getOwnerId())
                .set("Subject", resourceId)//@Todo maybe change
                .set("AmountOut", amount)
                .set("AmountIn", amount.multiply(new BigDecimal("0.85")))
                .set("Status", status)
                .set("Time", new Timestamp(System.currentTimeMillis()))
                .set("IssuerId", this.usageAnalyser.getConfig().getPaymentIssuerId())
                .execute();
    }

    public void clearTempResourceEarnings(long time, int hour) {
        this.resourceEarnings.delete()
                .whereBetween("Time", new Timestamp(getStartDayMillis(time)), new Timestamp(getEndDayMillis(time)))
                .where("Hour", hour)
                .execute();
    }

    public void deleteUsedResourceReports(long time, int hour) {
        this.resourceReporting.delete()
                .whereBetween("FirstContact", new Timestamp(getStartDayMillis(time)), new Timestamp(getEndDayMillis(time)))
                .where("Hour", hour)
                .execute();
    }
}
