package uhsuhjupjup.backend.keyword.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uhsuhjupjup.backend.common.cache.RefreshOutcome;
import uhsuhjupjup.backend.common.cache.TieredCache;

import java.util.List;
import java.util.concurrent.Callable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class KeywordGraphRefresherTest {

    @Mock
    private TieredCache keywordGraphCache;

    @Mock
    private KeywordService keywordService;

    @InjectMocks
    private KeywordGraphRefresher refresher;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 중복된_키워드는_한_번씩만_갱신한다() {
        given(keywordGraphCache.refresh(any(), any())).willReturn(RefreshOutcome.STORED);

        refresher.refreshAfterCommit(List.of(3L, 1L, 3L, 2L, 1L));

        var order = inOrder(keywordGraphCache);
        order.verify(keywordGraphCache).refresh(eq(1L), any());
        order.verify(keywordGraphCache).refresh(eq(2L), any());
        order.verify(keywordGraphCache).refresh(eq(3L), any());
        verify(keywordGraphCache, times(3)).refresh(any(), any());
    }

    @Test
    void 트랜잭션_안에서_부르면_커밋된_뒤에_갱신한다() {
        given(keywordGraphCache.refresh(any(), any())).willReturn(RefreshOutcome.STORED);
        TransactionSynchronizationManager.initSynchronization();

        refresher.refreshAfterCommit(List.of(5L));
        verify(keywordGraphCache, never()).refresh(any(), any());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(keywordGraphCache).refresh(eq(5L), any());
    }

    @Test
    void 트랜잭션이_롤백되면_갱신하지_않는다() {
        TransactionSynchronizationManager.initSynchronization();

        refresher.refreshAfterCommit(List.of(5L));
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(keywordGraphCache);
    }

    @Test
    void 갱신은_기존_글과_새_글을_모두_다시_집계하는_조회를_쓴다() throws Exception {
        given(keywordGraphCache.refresh(eq(9L), any())).willAnswer(invocation -> {
            Callable<?> loader = invocation.getArgument(1);
            loader.call();
            return RefreshOutcome.STORED;
        });

        refresher.refreshAfterCommit(List.of(9L));

        verify(keywordService).getGraph(9L);
    }

    @Test
    void 대상이_없으면_아무것도_하지_않는다() {
        refresher.refreshAfterCommit(List.of());

        verifyNoInteractions(keywordGraphCache, keywordService);
    }
}
