package com.neueda.leap.db;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;

class FreshTradingSchemaTest extends AbstractPostgresIntegrationTest {
    List<String> shape() throws Exception {
        var rows = new ArrayList<String>();
        try (var c = newConnection();
                var s = c.createStatement()) {
            for (String query :
                    List.of(
                            "SELECT"
                                + " table_name,column_name,data_type,coalesce(column_default,''),is_nullable,coalesce(numeric_precision,0),coalesce(numeric_scale,0)"
                                + " FROM information_schema.columns WHERE table_schema='public'"
                                + " ORDER BY table_name,ordinal_position",
                            "SELECT c.relname,t.tgname,pg_get_triggerdef(t.oid) FROM pg_trigger t"
                                + " JOIN pg_class c ON c.oid=t.tgrelid WHERE NOT t.tgisinternal AND"
                                + " c.relnamespace='public'::regnamespace ORDER BY"
                                + " c.relname,t.tgname",
                            "SELECT c.relname,x.conname,pg_get_constraintdef(x.oid) FROM"
                                    + " pg_constraint x JOIN pg_class c ON c.oid=x.conrelid WHERE"
                                    + " c.relnamespace='public'::regnamespace ORDER BY"
                                    + " c.relname,x.conname",
                            "SELECT tablename,indexname,indexdef FROM pg_indexes"
                                    + " WHERE schemaname='public' ORDER BY tablename,indexname",
                            "SELECT p.proname,pg_get_function_identity_arguments(p.oid),"
                                + " p.prorettype::regtype,p.provolatile,p.prosecdef,p.proconfig,"
                                + " regexp_replace(p.prosrc,'[[:space:]]','','g') FROM pg_proc p"
                                + " JOIN pg_namespace n ON n.oid=p.pronamespace WHERE"
                                + " n.nspname='public' ORDER BY p.proname")) {
                var result = s.executeQuery(query);
                while (result.next()) {
                    StringBuilder row = new StringBuilder();
                    for (int i = 1; i <= result.getMetaData().getColumnCount(); i++)
                        row.append(result.getString(i)).append('|');
                    rows.add(row.toString());
                }
            }
        }
        return rows;
    }

    @Test
    void freshDdlAndUpgradeHaveEquivalentColumnsConstraintsAndGuards() throws Exception {
        for (String file :
                new String[] {
                    "003_workspace_roles.sql",
                    "004_client_simulation.sql",
                    "005_client_banking.sql",
                    "006_trading_cutover.sql",
                    "007_trading_openings.sql",
                    "008_trading_controls.sql",
                    "009_quote_cache_age.sql",
                    "010_quote_cache_twenty_minutes.sql",
                    "011_operations_workspace.sql",
                    "012_managed_support.sql",
                    "013_advisor_workspace.sql"
                }) executeSqlScript(Path.of("../database/migrations/" + file));
        var upgraded = shape();
        try (var c = newConnection();
                var s = c.createStatement()) {
            s.execute("DROP SCHEMA public CASCADE;CREATE SCHEMA public");
        }
        executeSqlScript(Path.of("../database/fresh/001_schema.sql"));
        executeSqlScript(Path.of("../database/fresh/002_reference.sql"));
        assertEquals(upgraded, shape());
        executeSqlScript(Path.of("../database/fresh/002_reference.sql"));
        try (var c = newConnection();
                var s = c.createStatement();
                var r = s.executeQuery("SELECT count(*) FROM schema_migrations")) {
            r.next();
            assertEquals(11, r.getInt(1));
        }
    }
}
