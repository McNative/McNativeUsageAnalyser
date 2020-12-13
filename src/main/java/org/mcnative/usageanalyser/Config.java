package org.mcnative.usageanalyser;

import io.github.cdimascio.dotenv.Dotenv;
import net.pretronic.databasequery.api.driver.config.DatabaseDriverConfig;
import net.pretronic.databasequery.sql.dialect.Dialect;
import net.pretronic.databasequery.sql.driver.config.SQLDatabaseDriverConfigBuilder;

import java.net.InetSocketAddress;

public class Config {

    public static final Dotenv DOTENV = Dotenv.configure().ignoreIfMissing().load();

    public static String getenv(String name) {
        String value = getenvOrNull(name);
        if(value == null) throw new IllegalArgumentException("Can't load environment variable " + name);
        return value;
    }

    public static String getenvOrNull(String name) {
        if (System.getenv(name) != null) {
            return System.getenv(name);
        } else if (DOTENV.get(name) != null) {
            return DOTENV.get(name);
        }
        return null;
    }

    private final String environment = getenv("ENVIRONMENT");

    private final String databaseName = getenv("DATABASE_NAME");
    private final DatabaseDriverConfig<?> storageConfiguration = new SQLDatabaseDriverConfigBuilder()
            .setAddress(InetSocketAddress.createUnresolved(getenv("DATABASE_HOST"), Integer.parseInt(getenv("DATABASE_PORT"))))
            .setDialect(Dialect.byName(getenv("DATABASE_DIALECT")))
            .setUsername(getenv("DATABASE_USERNAME"))
            .setPassword(getenv("DATABASE_PASSWORD"))
            .build();

    private final String paymentIssuerId = getenv("PAYMENT_ISSUER_ID");
    private final String pretronicOrganisationId = getenv("PRETRONIC_ORGANISATION_ID");

    public static Dotenv getDOTENV() {
        return DOTENV;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public DatabaseDriverConfig<?> getStorageConfiguration() {
        return storageConfiguration;
    }

    public String getPaymentIssuerId() {
        return paymentIssuerId;
    }

    public String getPretronicOrganisationId() {
        return pretronicOrganisationId;
    }
}
