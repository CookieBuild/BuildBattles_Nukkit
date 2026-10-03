package com.cookiebuild.buildbattles.gallery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.cookiebuild.cookiedough.persistence.SqlTransactionExecutor;

/**
 * Plugin side of the gallery tables created by the website migration
 * {@code 0024_buildbattle_gallery.sql}. This class only reads and writes the
 * rows; it never creates or alters a table. Every method blocks on JDBC and
 * must run off the server thread.
 */
public final class PostgresBuildGalleryRepository {
    static final int SHORT_CODE_ATTEMPTS = 6;

    /** One captured plot, ready for insertion. */
    public record BuildRow(UUID id, UUID playerId, UUID matchId, String themeKey, String themeName,
            GalleryCapturePolicy.Outcome outcome, Integer placement, int builders, int blockCount,
            int sizeX, int sizeY, int sizeZ, byte[] data) {
    }

    public record Inserted(UUID id, String shortCode, String status) {
        public boolean published() {
            return GalleryCapturePolicy.STATUS_PUBLISHED.equals(status);
        }
    }

    public Inserted insert(BuildRow row, Supplier<String> shortCodes) {
        return SqlTransactionExecutor.inTransaction(connection -> {
            String status = GalleryCapturePolicy.status(optOut(connection, row.playerId()));
            for (int attempt = 0; attempt < SHORT_CODE_ATTEMPTS; attempt++) {
                String code = shortCodes.get();
                try (PreparedStatement insert = connection.prepareStatement("""
                        insert into buildbattle_builds
                          (id, short_code, player_id, match_id, theme_key, theme_name, outcome, placement,
                           builders, block_count, size_x, size_y, size_z, data, status)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (short_code) do nothing
                        """)) {
                    insert.setObject(1, row.id());
                    insert.setString(2, code);
                    insert.setObject(3, row.playerId());
                    if (row.matchId() == null) insert.setNull(4, Types.OTHER);
                    else insert.setObject(4, row.matchId());
                    insert.setString(5, truncate(row.themeKey(), 64));
                    insert.setString(6, truncate(row.themeName(), 80));
                    insert.setString(7, row.outcome().column());
                    if (row.placement() == null) insert.setNull(8, Types.SMALLINT);
                    else insert.setShort(8, row.placement().shortValue());
                    insert.setShort(9, (short) row.builders());
                    insert.setInt(10, row.blockCount());
                    insert.setShort(11, (short) row.sizeX());
                    insert.setShort(12, (short) row.sizeY());
                    insert.setShort(13, (short) row.sizeZ());
                    insert.setBytes(14, row.data());
                    insert.setString(15, status);
                    if (insert.executeUpdate() == 1) return new Inserted(row.id(), code, status);
                }
            }
            throw new SQLException("No free short_code after " + SHORT_CODE_ATTEMPTS + " attempts");
        });
    }

    public Optional<Boolean> optOut(UUID playerId) {
        return SqlTransactionExecutor.inTransaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "select gallery_opt_out from buildbattle_gallery_settings where player_id = ?")) {
                select.setObject(1, playerId);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next() ? Optional.of(rows.getBoolean(1)) : Optional.<Boolean>empty();
                }
            }
        });
    }

    /**
     * Stores the owner's choice. Turning the gallery off also makes every
     * published build of that player private; turning it on never republishes.
     *
     * @return number of builds made private by this call
     */
    public int setOptOut(UUID playerId, boolean optOut) {
        return SqlTransactionExecutor.inTransaction(connection -> {
            try (PreparedStatement upsert = connection.prepareStatement("""
                    insert into buildbattle_gallery_settings (player_id, gallery_opt_out, updated_at)
                    values (?, ?, now())
                    on conflict (player_id) do update
                      set gallery_opt_out = excluded.gallery_opt_out, updated_at = now()
                    """)) {
                upsert.setObject(1, playerId);
                upsert.setBoolean(2, optOut);
                upsert.executeUpdate();
            }
            if (!optOut) return 0;
            try (PreparedStatement hide = connection.prepareStatement(
                    "update buildbattle_builds set status = 'private' where player_id = ? and status = 'published'")) {
                hide.setObject(1, playerId);
                return hide.executeUpdate();
            }
        });
    }

    /** Likes received by every build of the player, whatever its status. */
    public long totalLikes(UUID playerId) {
        return SqlTransactionExecutor.inTransaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "select coalesce(sum(like_count), 0) from buildbattle_builds where player_id = ?")) {
                select.setObject(1, playerId);
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next() ? rows.getLong(1) : 0L;
                }
            }
        });
    }

    private static boolean optOut(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "select gallery_opt_out from buildbattle_gallery_settings where player_id = ?")) {
            select.setObject(1, playerId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        }
    }

    private static String truncate(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    /** True when the failure is "relation does not exist" (migration 0024 not applied yet). */
    public static boolean isMissingTable(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && "42P01".equals(sql.getSQLState())) return true;
            String message = current.getMessage();
            if (message != null && message.contains("buildbattle_") && message.contains("does not exist")) {
                return true;
            }
            if (current.getCause() == current) break;
        }
        return false;
    }

    public static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
