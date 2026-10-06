package uhsuhjupjup.backend.oss.pipeline.sync.domain;

import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OssRepoSyncStateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 19, 30, 0);
    private static final LocalDateTime LATEST_UPDATE = LocalDateTime.of(2026, 10, 3, 18, 42, 7);
    private static final LocalDateTime EARLIER_SYNC = LocalDateTime.of(2026, 10, 3, 19, 0, 0);
    private static final String ETAG = "W/\"a1b2c3\"";
    private static final String NEW_ETAG = "W/\"d4e5f6\"";

    private final OssRepoSyncState state =
            OssRepoSyncState.create(OssRepo.create(1296269L, "octocat/Hello-World", null, "Java", 80));

    @Test
    void issuesUpdatedSince_neverSawAnIssue_isSevenDaysBeforeNow() {
        assertThat(state.issuesUpdatedSince(NOW)).isEqualTo(LocalDateTime.of(2026, 9, 26, 19, 30, 0));
    }

    @Test
    void issuesUpdatedSince_readKeepingEtagWithoutAnyIssue_isStillSevenDaysBeforeNow() {
        state.recordRead(null, ETAG, EARLIER_SYNC);

        assertThat(state.issuesUpdatedSince(NOW)).isEqualTo(NOW.minusDays(7));
    }

    @Test
    void issuesUpdatedSince_afterReadKeepingEtag_startsFiveMinutesBeforeLatestUpdate() {
        state.recordRead(LATEST_UPDATE, ETAG, EARLIER_SYNC);

        assertThat(state.issuesUpdatedSince(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 3, 18, 37, 7));
    }

    @Test
    void issuesUpdatedSince_afterNotModified_staysWhereItWas() {
        state.recordRead(LATEST_UPDATE, ETAG, EARLIER_SYNC);

        state.recordNotModified(NOW);

        assertThat(state.issuesUpdatedSince(NOW.plusMinutes(30))).isEqualTo(LATEST_UPDATE.minusMinutes(5));
    }

    @Test
    void issuesUpdatedSince_afterSeveralPagesOrPageLimitReadWithoutEtag_resumesAtLatestUpdateWithoutOverlap() {
        state.recordRead(LATEST_UPDATE, ETAG, EARLIER_SYNC);

        state.recordRead(LATEST_UPDATE, null, NOW);

        assertThat(state.issuesUpdatedSince(NOW)).isEqualTo(LATEST_UPDATE);
    }

    @Test
    void issuesUpdatedSince_afterReadCutByFailure_resumesAtLatestUpdateWithoutOverlap() {
        state.recordRead(LATEST_UPDATE.minusHours(1), ETAG, EARLIER_SYNC);

        state.recordReadCutByFailure(LATEST_UPDATE, NOW);

        assertThat(state.issuesUpdatedSince(NOW)).isEqualTo(LATEST_UPDATE);
    }

    @Test
    void recordRead_newerLatestUpdate_movesForward() {
        state.recordRead(LATEST_UPDATE, ETAG, EARLIER_SYNC);

        state.recordRead(LATEST_UPDATE.plusSeconds(1), NEW_ETAG, NOW);

        assertThat(state.getLastIssueUpdatedAt()).isEqualTo(LATEST_UPDATE.plusSeconds(1));
    }

    @Test
    void recordRead_olderOrNoLatestUpdate_neverMovesBack() {
        state.recordRead(LATEST_UPDATE, ETAG, EARLIER_SYNC);

        state.recordRead(LATEST_UPDATE.minusDays(1), NEW_ETAG, NOW);
        state.recordRead(null, NEW_ETAG, NOW);

        assertThat(state.getLastIssueUpdatedAt()).isEqualTo(LATEST_UPDATE);
    }

    @Test
    void recordRead_takesEtagOfThisReadAndSyncTimeAndClearsFailures() {
        state.recordFailure();
        state.recordFailure();

        state.recordRead(LATEST_UPDATE, NEW_ETAG, NOW);

        assertThat(state.getEtag()).isEqualTo(NEW_ETAG);
        assertThat(state.getLastSyncedAt()).isEqualTo(NOW);
        assertThat(state.getConsecutiveFailures()).isZero();
    }

    @Test
    void recordRead_withoutEtag_dropsPreviousEtag() {
        state.recordRead(LATEST_UPDATE, ETAG, EARLIER_SYNC);

        state.recordRead(LATEST_UPDATE, null, NOW);

        assertThat(state.getEtag()).isNull();
    }

    @Test
    void recordReadCutByFailure_movesLatestUpdateDropsEtagAndCountsFailure() {
        state.recordRead(LATEST_UPDATE.minusHours(1), ETAG, EARLIER_SYNC);
        state.recordFailure();

        state.recordReadCutByFailure(LATEST_UPDATE, NOW);

        assertThat(state.getLastIssueUpdatedAt()).isEqualTo(LATEST_UPDATE);
        assertThat(state.getEtag()).isNull();
        assertThat(state.getLastSyncedAt()).isEqualTo(NOW);
        assertThat(state.getConsecutiveFailures()).isEqualTo(2);
    }

    @Test
    void recordReadCutByFailure_olderLatestUpdate_neverMovesBack() {
        state.recordRead(LATEST_UPDATE, ETAG, EARLIER_SYNC);

        state.recordReadCutByFailure(LATEST_UPDATE.minusHours(1), NOW);

        assertThat(state.getLastIssueUpdatedAt()).isEqualTo(LATEST_UPDATE);
    }

    @Test
    void recordNotModified_keepsEtagAndLatestUpdateButTakesSyncTimeAndClearsFailures() {
        state.recordRead(LATEST_UPDATE, ETAG, EARLIER_SYNC);
        state.recordFailure();

        state.recordNotModified(NOW);

        assertThat(state.getEtag()).isEqualTo(ETAG);
        assertThat(state.getLastIssueUpdatedAt()).isEqualTo(LATEST_UPDATE);
        assertThat(state.getLastSyncedAt()).isEqualTo(NOW);
        assertThat(state.getConsecutiveFailures()).isZero();
    }

    @Test
    void recordFailure_countsUpKeepingEverythingElse() {
        state.recordRead(LATEST_UPDATE, ETAG, EARLIER_SYNC);

        state.recordFailure();
        state.recordFailure();

        assertThat(state.getConsecutiveFailures()).isEqualTo(2);
        assertThat(state.getEtag()).isEqualTo(ETAG);
        assertThat(state.getLastIssueUpdatedAt()).isEqualTo(LATEST_UPDATE);
        assertThat(state.getLastSyncedAt()).isEqualTo(EARLIER_SYNC);
    }
}
