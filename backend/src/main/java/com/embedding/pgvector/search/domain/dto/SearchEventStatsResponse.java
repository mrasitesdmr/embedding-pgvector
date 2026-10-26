package com.embedding.pgvector.search.domain.dto;

import com.embedding.pgvector.search.repository.SearchEventStatsView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/**
 * Bir çalışma alanının arama kaydı istatistiklerini taşır; sonuçsuz ve az sonuçlu arama oranlarını son yedi günden
 * hesaplar.
 */
public record SearchEventStatsResponse(
        @Schema(description = "All search events of the workspace, any time", example = "4",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long totalCount,

        @Schema(description = "Events with createdAt >= todayStart", example = "4",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long todayCount,

        @Schema(description = "Events with createdAt >= last7DaysStart", example = "4",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long last7DaysCount,

        @Schema(description = "Events of the last 7 days with resultCount = 0", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long zeroResultCount,

        @Schema(description = "Events of the last 7 days with resultCount >= 1 and resultCount < limit. Events with "
                + "a null limit (stored before API 0.3.0) are never counted here.", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long lowResultCount,

        @Schema(types = {"number", "null"}, format = "double", minimum = "0", maximum = "1", example = "0.25",
                description = "zeroResultCount / last7DaysCount, not rounded; null when last7DaysCount is 0")
        @Nullable Double zeroResultRate,

        @Schema(types = {"number", "null"}, format = "double", minimum = "0", maximum = "1", example = "0.25",
                description = "lowResultCount / last7DaysCount, not rounded; null when last7DaysCount is 0")
        @Nullable Double lowResultRate,

        @Schema(description = "Time zone that defines the days", example = "Europe/Istanbul",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String timeZone,

        @Schema(description = "00:00 today in timeZone, as an instant", example = "2026-10-24T21:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant todayStart,

        @Schema(description = "00:00 six days before today in timeZone, as an instant",
                example = "2026-10-18T21:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant last7DaysStart) {

    public static SearchEventStatsResponse from(SearchEventStatsView view, ZoneId zone, Instant todayStart,
            Instant last7DaysStart) {
        long last7DaysCount = view.getLast7DaysCount();
        return new SearchEventStatsResponse(view.getTotalCount(), view.getTodayCount(), last7DaysCount,
                view.getZeroResultCount(), view.getLowResultCount(), rate(view.getZeroResultCount(), last7DaysCount),
                rate(view.getLowResultCount(), last7DaysCount), zone.getId(), todayStart, last7DaysStart);
    }

    private static @Nullable Double rate(long count, long total) {
        return total == 0 ? null : (double) count / total;
    }
}
