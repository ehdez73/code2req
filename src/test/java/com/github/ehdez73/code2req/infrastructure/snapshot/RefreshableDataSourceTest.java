package com.github.ehdez73.code2req.infrastructure.snapshot;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

class RefreshableDataSourceTest {

    @Test
    void initialDelegateIsAccessible() {
        var inner = new SQLiteDataSource();
        var ds = new RefreshableDataSource(inner);
        assertSame(inner, ds.delegate());
    }

    @Test
    void replaceDelegateSwapsUnderlyingDataSource() {
        var initial = new SQLiteDataSource();
        var replacement = new SQLiteDataSource();
        var ds = new RefreshableDataSource(initial);

        ds.replaceDelegate(replacement);

        assertSame(replacement, ds.delegate());
    }

    @Test
    void getConnectionDelegatesToInner() throws Exception {
        var inner = new SQLiteDataSource();
        inner.setUrl("jdbc:sqlite::memory:");
        var ds = new RefreshableDataSource(inner);

        try (Connection conn = ds.getConnection()) {
            assertFalse(conn.isClosed());
        }
    }

    @Test
    void isWrapperForReturnsFalse() throws Exception {
        var inner = new SQLiteDataSource();
        var ds = new RefreshableDataSource(inner);
        assertFalse(ds.isWrapperFor(RefreshableDataSource.class));
    }
}
