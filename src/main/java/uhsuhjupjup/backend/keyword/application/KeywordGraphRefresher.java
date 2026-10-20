package uhsuhjupjup.backend.keyword.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uhsuhjupjup.backend.common.cache.RefreshOutcome;
import uhsuhjupjup.backend.common.cache.TieredCache;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordGraphRefresher {

    private static final String REFRESH_ID = "cacheRefreshId";

    private final TieredCache keywordGraphCache;
    private final KeywordService keywordService;

    public void refreshAfterCommit(Collection<Long> keywordIds) {
        Set<Long> distinct = new TreeSet<>(keywordIds);
        if (distinct.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refreshNow(distinct);
                }
            });
            return;
        }
        refreshNow(distinct);
    }

    private void refreshNow(Set<Long> keywordIds) {
        String refreshId = UUID.randomUUID().toString().substring(0, 8);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(REFRESH_ID, refreshId)) {
            Map<RefreshOutcome, Integer> outcomes = new EnumMap<>(RefreshOutcome.class);
            for (Long keywordId : keywordIds) {
                RefreshOutcome outcome = keywordGraphCache.refresh(keywordId, () -> keywordService.getGraph(keywordId));
                outcomes.merge(outcome, 1, Integer::sum);
            }
            log.info("키워드 그래프 갱신 refreshId={} 대상={} 결과={}", refreshId, keywordIds.size(), outcomes);
        }
    }
}
