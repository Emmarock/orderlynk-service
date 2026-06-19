package com.myorderlynk.app.config;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * One-off data migration: rewrites any {@code service_provider_profiles.timezone} that the JVM
 * doesn't recognize as an IANA zone id (legacy free-text values like "EST", "GMT+1" or blanks left
 * over from before the timezone dropdown) to the platform default. Validity is checked with
 * {@link ZoneId#of(String)} against the running JVM's tz database — SQL can't enumerate that — so
 * genuinely valid ids (e.g. {@code America/Chicago}) are left untouched.
 */
public class NormalizeServiceTimezones implements CustomTaskChange {

    private static final String DEFAULT_ZONE = "America/Toronto";

    private String confirmationMessage = "Service timezones normalized";

    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection conn = (JdbcConnection) database.getConnection();
        int fixed = 0;
        try (Statement select = conn.createStatement();
             ResultSet rs = select.executeQuery("SELECT id, timezone FROM service_provider_profiles");
             PreparedStatement update = conn.prepareStatement(
                     "UPDATE service_provider_profiles SET timezone = ? WHERE id = ?")) {
            while (rs.next()) {
                if (!isValidZone(rs.getString("timezone"))) {
                    update.setString(1, DEFAULT_ZONE);
                    update.setString(2, rs.getString("id"));
                    update.executeUpdate();
                    fixed++;
                }
            }
        } catch (Exception e) {
            throw new CustomChangeException("Failed to normalize service timezones", e);
        }
        confirmationMessage = "Service timezones normalized (" + fixed + " row(s) reset to " + DEFAULT_ZONE + ")";
    }

    private static boolean isValidZone(String tz) {
        if (tz == null || tz.isBlank()) {
            return false;
        }
        try {
            ZoneId.of(tz.trim());
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    @Override
    public String getConfirmationMessage() {
        return confirmationMessage;
    }

    @Override
    public void setUp() throws SetupException {
        // no-op
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        // no-op
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}