package de.sub.goobi.persistence.managers;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.log4j.Logger;

public class DatabaseVersion {

    public static final int EXPECTED_VERSION = 2;
    private static final Logger logger = Logger.getLogger(DatabaseVersion.class);

    public static int getCurrentVersion() {

        Connection connection = null;
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM databaseversion");
        try {
            connection = MySQLHelper.getInstance().getConnection();
            logger.debug(sql.toString());
            int currentValue = new QueryRunner().query(connection, sql.toString(), MySQLHelper.resultSetToIntegerHandler);
            return currentValue;
        } catch (SQLException e) {
            logger.error(e);
        } finally {
            if (connection != null) {
                try {
                    MySQLHelper.closeConnection(connection);
                } catch (SQLException e) {
                    logger.error(e);
                }
            }
        }
        return 0;
    }

    public static void updateDatabase(int currentVersion) {
        switch (currentVersion) {
            case 0:
                logger.debug("Update database to version 1.");
                createTableDatabaseVersion();
                currentVersion = 1;
            case 1:
                logger.debug("Update database to version 2.");
                alterTableSteps();
            case 999:
                // this has to be the last case
                updateDatabaseVersion(currentVersion);
                logger.debug("Database is up to date.");

        }
    }

    private static void updateDatabaseVersion(int currentVersion) {
        Connection connection = null;
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE databaseversion set version = " + EXPECTED_VERSION + " where version = " + currentVersion);
        try {
            connection = MySQLHelper.getInstance().getConnection();
            QueryRunner runner = new QueryRunner();
            runner.update(connection, sql.toString());
        } catch (SQLException e) {
            logger.error(e);
        } finally {
            if (connection != null) {
                try {
                    MySQLHelper.closeConnection(connection);
                } catch (SQLException e) {
                    logger.error(e);
                }
            }
        }
        
    }

    private static void alterTableSteps() {
        Connection connection = null;
        try {
            connection = MySQLHelper.getInstance().getConnection();
            QueryRunner runner = new QueryRunner();
            runner.update(connection,"alter table benutzer change confVorgangsdatumAnzeigen displayProcessDateColumn boolean default false;"); 
            runner.update(connection,"alter table benutzer add column displayDeactivatedProjects boolean default false;");       
            runner.update(connection,"alter table benutzer add column displayFinishedProcesses boolean default false;"); 
            runner.update(connection,"alter table benutzer add column displaySelectBoxes boolean default false;");    
            runner.update(connection,"alter table benutzer add column displayIdColumn boolean default false;"); 
            runner.update(connection,"alter table benutzer add column displayBatchColumn boolean default false;");       
            runner.update(connection,"alter table benutzer add column displayLocksColumn boolean default false;"); 
            runner.update(connection,"alter table benutzer add column displaySwappingColumn boolean default false;");      
            runner.update(connection,"alter table benutzer add column displayAutomaticTasks boolean default false;"); 
            runner.update(connection,"alter table benutzer add column hideCorrectionTasks boolean default false;");       
            runner.update(connection,"alter table benutzer add column displayOnlySelectedTasks boolean default false;"); 
            runner.update(connection,"alter table benutzer add column displayOnlyOpenTasks boolean default false;");      
            runner.update(connection,"alter table benutzer add column displayModulesColumn boolean default false;"); 
        } catch (SQLException e) {
            logger.error(e);
        } finally {
            if (connection != null) {
                try {
                    MySQLHelper.closeConnection(connection);
                } catch (SQLException e) {
                    logger.error(e);
                }
            }
        } 
    }

    private static void createTableDatabaseVersion() {

        Connection connection = null;
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO databaseversion (version)  VALUES  (1)");
        try {
            connection = MySQLHelper.getInstance().getConnection();
            logger.debug(sql.toString());
            QueryRunner runner = new QueryRunner();
            runner.update(connection, "CREATE TABLE databaseversion (version int(11))");
            runner.insert(connection, sql.toString(), MySQLHelper.resultSetToIntegerHandler);
        } catch (SQLException e) {
            logger.error(e);
        } finally {
            if (connection != null) {
                try {
                    MySQLHelper.closeConnection(connection);
                } catch (SQLException e) {
                    logger.error(e);
                }
            }
        }
    }

}