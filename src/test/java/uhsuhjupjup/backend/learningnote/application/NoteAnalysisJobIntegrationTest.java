package uhsuhjupjup.backend.learningnote.application;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.domain.ArticleKeyword;
import uhsuhjupjup.backend.article.infra.ArticleKeywordRepository;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.blog.domain.Blog;
import uhsuhjupjup.backend.blog.infra.BlogRepository;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysisClaim;
import uhsuhjupjup.backend.learningnote.application.dto.NoteAnalysisJobOutcome;
import uhsuhjupjup.backend.learningnote.domain.LearningNote;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJob;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisJobStatus;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisReason;
import uhsuhjupjup.backend.learningnote.infra.LearningNoteRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteAnalysisJobRepository;
import uhsuhjupjup.backend.learningnote.infra.NoteKeywordRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
class NoteAnalysisJobIntegrationTest {

    private static final String BEARER = "Bearer test-token";
    private static final Duration LEASE = Duration.ofMinutes(3);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private LearningNoteRepository learningNoteRepository;
    @Autowired
    private NoteKeywordRepository noteKeywordRepository;
    @Autowired
    private NoteAnalysisJobRepository jobRepository;
    @Autowired
    private KeywordRepository keywordRepository;
    @Autowired
    private BlogRepository blogRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleKeywordRepository articleKeywordRepository;
    @Autowired
    private NoteAnalysisJobService jobService;
    @Autowired
    private NoteAnalysisJobRunner runner;
    @Autowired
    private NoteAnalysisJobClaimer claimer;
    @Autowired
    private NoteAnalysisJobRecorder recorder;
    @Autowired
    private BulkheadRegistry bulkheadRegistry;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NoteService noteService;
    @Autowired
    private NoteAnalyzer noteAnalyzer;
    @Autowired
    private NoteKeywordSaver noteKeywordSaver;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockitoBean
    private AsyncKeywordClassifier asyncKeywordClassifier;
    @MockitoBean
    private KeywordClassifier keywordClassifier;

    private Keyword redis;
    private Article redisArticle;
    private Long noteId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.create("github", "uid-job", "job@example.com"));
        memberId = member.getId();
        signedInAs("uid-job", "job@example.com");
        redis = keywordRepository.save(Keyword.create("Redis"));
        Blog blog = blogRepository.save(Blog.create("우아한형제들", "woowahan.com", "https://woowahan.com/rss"));
        redisArticle = articleRepository.save(
                Article.create(blog, "Redis 캐시 전략", "https://woowahan.com/redis-cache", LocalDateTime.now()));
        articleKeywordRepository.save(ArticleKeyword.of(redisArticle, redis, "ai"));
        noteId = learningNoteRepository.save(LearningNote.create(member, "학습 노트", "Redis 정리")).getId();
    }

    @AfterEach
    void tearDown() {
        jobRepository.deleteAllInBatch();
        noteKeywordRepository.deleteAllInBatch();
        learningNoteRepository.deleteAllInBatch();
        articleKeywordRepository.deleteAllInBatch();
        articleRepository.deleteAllInBatch();
        blogRepository.deleteAllInBatch();
        keywordRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 폴러가_대기_작업을_처리해_실제_AI_결과를_저장하고_확인하기_전까지_목록에_완료를_표시한다() throws Exception {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        aiReturns(redis);

        assertThat(runner.dispatch(LocalDateTime.now())).isEqualTo(1);
        awaitStatus(NoteAnalysisJobStatus.DONE);

        assertThat(learningNoteRepository.findById(noteId).orElseThrow().getAnalyzedAt()).isNotNull();
        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId)).containsExactly(redis.getId());
        mockMvc.perform(get("/api/notes").header(AUTHORIZATION, BEARER))
                .andExpect(jsonPath("$[0].analysisStatus").value("ANALYZED"))
                .andExpect(jsonPath("$[0].recommendationReady").value(true));

        recommendations()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.keywords[0]").value("Redis"))
                .andExpect(jsonPath("$.articles[0].articleId").value(redisArticle.getId()));

        mockMvc.perform(get("/api/notes/" + noteId).header(AUTHORIZATION, BEARER))
                .andExpect(jsonPath("$.recommendationReady").value(false));
    }

    @Test
    void 대기_중인_노트는_목록과_상세에_분석_중으로_보인다() throws Exception {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());

        mockMvc.perform(get("/api/notes").header(AUTHORIZATION, BEARER))
                .andExpect(jsonPath("$[0].analysisStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].recommendationReady").value(false));
        mockMvc.perform(get("/api/notes/" + noteId).header(AUTHORIZATION, BEARER))
                .andExpect(jsonPath("$.analysisStatus").value("PENDING"));
    }

    @Test
    void 허가가_모두_쓰이고_있으면_폴러는_작업을_선점하지_않는다() {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(NoteAnalysisJobRunner.RETRY_BULKHEAD);
        int permits = bulkhead.getBulkheadConfig().getMaxConcurrentCalls();
        for (int i = 0; i < permits; i++) {
            assertThat(bulkhead.tryAcquirePermission()).isTrue();
        }
        try {
            assertThat(runner.dispatch(LocalDateTime.now())).isZero();
        } finally {
            for (int i = 0; i < permits; i++) {
                bulkhead.releasePermission();
            }
        }

        assertThat(job().getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
        verifyNoInteractions(asyncKeywordClassifier);
    }

    @Test
    void 작업이_끝나면_폴러가_받은_허가를_돌려준다() {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        aiReturns(redis);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(NoteAnalysisJobRunner.RETRY_BULKHEAD);
        int permits = bulkhead.getBulkheadConfig().getMaxConcurrentCalls();

        runner.dispatch(LocalDateTime.now());
        awaitStatus(NoteAnalysisJobStatus.DONE);

        await(() -> bulkhead.getMetrics().getAvailableConcurrentCalls() == permits);
    }

    @Test
    void 여러_작업자가_같은_작업을_동시에_선점해도_한_작업자만_선점한다() throws Exception {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();

        List<Optional<NoteAnalysisClaim>> claims = runConcurrently(8, () -> claimer.claim(noteId, LEASE, now));

        assertThat(claims.stream().filter(Optional::isPresent)).hasSize(1);
        assertThat(job().getStatus()).isEqualTo(NoteAnalysisJobStatus.RUNNING);
    }

    @Test
    void 임대가_만료되면_다른_작업자가_다시_선점하고_이전_작업자는_결과를_저장하지_못한다() {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        NoteAnalysisClaim first = claimer.claim(noteId, LEASE, now).orElseThrow();

        assertThat(claimer.claim(noteId, LEASE, now.plusSeconds(1))).isEmpty();
        NoteAnalysisClaim second = claimer.claim(noteId, LEASE, now.plus(LEASE).plusSeconds(1)).orElseThrow();

        assertThat(recorder.complete(first, List.of(new KeywordMatch(redis.getId(), "ai")), LocalDateTime.now()))
                .isEqualTo(NoteAnalysisJobOutcome.LEASE_LOST);
        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId)).isEmpty();
        assertThat(recorder.complete(second, List.of(new KeywordMatch(redis.getId(), "ai")), LocalDateTime.now()))
                .isEqualTo(NoteAnalysisJobOutcome.COMPLETED);
        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId)).containsExactly(redis.getId());
    }

    @Test
    void 분석_중에_노트를_수정하면_옛_결과를_저장하지_않고_다음_조회에서_새_내용으로_분석한다() throws Exception {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        NoteAnalysisClaim claim = claimer.claim(noteId, LEASE, LocalDateTime.now()).orElseThrow();
        mockMvc.perform(put("/api/notes/" + noteId).header(AUTHORIZATION, BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"새 제목\",\"content\":\"새 본문\"}"))
                .andExpect(status().isOk());

        assertThat(recorder.complete(claim, List.of(new KeywordMatch(redis.getId(), "ai")), LocalDateTime.now()))
                .isEqualTo(NoteAnalysisJobOutcome.SUPERSEDED);
        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId)).isEmpty();
        assertThat(learningNoteRepository.findById(noteId).orElseThrow().getAnalyzedAt()).isNull();
        assertThat(job().getStatus()).isEqualTo(NoteAnalysisJobStatus.SUPERSEDED);

        aiReturns(redis);
        recommendations()
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.keywords[0]").value("Redis"));
    }

    @Test
    void 서킷이_열려_실패하면_시도_횟수를_늘리지_않고_뒤로_미룬다() {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        CallNotPermittedException open = CallNotPermittedException.createCallNotPermittedException(
                circuitBreakerRegistry.circuitBreaker("keywordClassifierGpt"));
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.error(new KeywordClassificationException("Claude, GPT 키워드 분류 모두 실패", open)));
        LocalDateTime before = LocalDateTime.now();

        runner.dispatch(LocalDateTime.now());
        await(() -> job().getLastReason() == NoteAnalysisReason.CIRCUIT_OPEN);

        NoteAnalysisJob job = job();
        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getNextAttemptAt()).isAfter(before.plusSeconds(20));
    }

    @Test
    void AI가_실패하면_시도_횟수를_늘리고_백오프_뒤에_다시_시도한다() {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        aiFails();
        LocalDateTime before = LocalDateTime.now();

        runner.dispatch(LocalDateTime.now());
        await(() -> job().getAttempts() == 1);

        assertThat(job().getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
        assertThat(job().getLastReason()).isEqualTo(NoteAnalysisReason.AI_FAILED);
        assertThat(job().getNextAttemptAt()).isAfter(before.plusSeconds(20));
        assertThat(runner.dispatch(LocalDateTime.now())).isZero();
    }

    @Test
    void 시간_지평을_넘겨_실패하면_FAILED로_끝내고_다시_시도_API로_대기로_돌린다() throws Exception {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        jdbcTemplate.update("UPDATE note_analysis_job SET queued_at = ? WHERE note_id = ?",
                LocalDateTime.now().minusHours(7), noteId);
        aiFails();

        runner.dispatch(LocalDateTime.now());
        awaitStatus(NoteAnalysisJobStatus.FAILED);

        recommendations().andExpect(jsonPath("$.status").value("FAILED"));
        mockMvc.perform(get("/api/notes").header(AUTHORIZATION, BEARER))
                .andExpect(jsonPath("$[0].analysisStatus").value("FAILED"));

        mockMvc.perform(post("/api/notes/" + noteId + "/recommendations/retry").header(AUTHORIZATION, BEARER))
                .andExpect(status().isNoContent());

        NoteAnalysisJob job = job();
        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
        recommendations().andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 다른_회원은_내_노트의_분석을_다시_맡길_수_없다() throws Exception {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        jdbcTemplate.update("UPDATE note_analysis_job SET status = 'FAILED' WHERE note_id = ?", noteId);
        memberRepository.save(Member.create("github", "uid-other", "other@example.com"));
        signedInAs("uid-other", "other@example.com");

        mockMvc.perform(post("/api/notes/" + noteId + "/recommendations/retry").header(AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notes").header(AUTHORIZATION, BEARER))
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(job().getStatus()).isEqualTo(NoteAnalysisJobStatus.FAILED);
    }

    @Test
    void 인증_없이_다시_맡기기는_401() throws Exception {
        mockMvc.perform(post("/api/notes/" + noteId + "/recommendations/retry"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 노트를_삭제하면_분석_작업도_함께_지워진다() throws Exception {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());

        mockMvc.perform(delete("/api/notes/" + noteId).header(AUTHORIZATION, BEARER))
                .andExpect(status().isNoContent());

        assertThat(jobRepository.findById(noteId)).isEmpty();
    }

    @Test
    void 같은_노트를_동시에_보존해도_작업은_하나만_남고_예외가_나지_않는다() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        List<Boolean> results = runConcurrently(8,
                () -> jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, now));

        assertThat(results).contains(true);
        assertThat(jobRepository.count()).isEqualTo(1);
        assertThat(job().getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
    }

    @Test
    void 이미_분석된_노트의_작업은_AI를_부르지_않고_알림_없이_끝낸다() {
        jobService.enqueue(noteId, NoteAnalysisReason.REQUEST_TIMEOUT, LocalDateTime.now());
        LearningNote note = learningNoteRepository.findById(noteId).orElseThrow();
        note.markAnalyzed(LocalDateTime.now());
        learningNoteRepository.saveAndFlush(note);

        assertThat(runner.dispatch(LocalDateTime.now())).isZero();

        NoteAnalysisJob job = job();
        assertThat(job.getStatus()).isEqualTo(NoteAnalysisJobStatus.DONE);
        assertThat(job.getAcknowledgedAt()).isNotNull();
        verifyNoInteractions(asyncKeywordClassifier);
    }

    @Test
    void 이미_분석된_노트는_보존하지_않는다() {
        LearningNote note = learningNoteRepository.findById(noteId).orElseThrow();
        note.markAnalyzed(LocalDateTime.now());
        learningNoteRepository.saveAndFlush(note);

        assertThat(jobService.enqueue(noteId, NoteAnalysisReason.REQUEST_TIMEOUT, LocalDateTime.now())).isFalse();
        assertThat(jobRepository.findById(noteId)).isEmpty();
    }

    @Test
    void 같은_노트를_동시에_수정해도_내용_버전이_수정_횟수만큼_오른다() throws Exception {
        runConcurrently(8, () -> noteService.update(noteId, memberId, "제목", "본문"));

        assertThat(learningNoteRepository.findById(noteId).orElseThrow().getContentRevision()).isEqualTo(8);
    }

    @Test
    void 분석_대기_중인_노트의_그래프는_AI를_다시_부르지_않는다() throws Exception {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());

        mockMvc.perform(get("/api/notes/" + noteId + "/graph").header(AUTHORIZATION, BEARER))
                .andExpect(status().isOk());

        verifyNoInteractions(keywordClassifier, asyncKeywordClassifier);
        assertThat(job().getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
    }

    @Test
    void AI가_빈_응답으로_끝나면_실패로_세고_다시_시도한다() {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.empty());

        runner.dispatch(LocalDateTime.now());
        await(() -> job().getAttempts() == 1);

        assertThat(job().getStatus()).isEqualTo(NoteAnalysisJobStatus.PENDING);
        assertThat(job().getLastReason()).isEqualTo(NoteAnalysisReason.AI_FAILED);
    }

    @Test
    void 시간_지평을_넘긴_작업은_선점할_때_AI를_부르지_않고_실패로_끝낸다() {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        jdbcTemplate.update("UPDATE note_analysis_job SET queued_at = ? WHERE note_id = ?",
                LocalDateTime.now().minusHours(7), noteId);

        assertThat(runner.dispatch(LocalDateTime.now())).isZero();

        assertThat(job().getStatus()).isEqualTo(NoteAnalysisJobStatus.FAILED);
        verifyNoInteractions(asyncKeywordClassifier);
    }

    @Test
    void 작업이_끝나기_전에_즉시_요청이_먼저_저장했으면_완료_표시를_띄우지_않는다() {
        jobService.enqueue(noteId, NoteAnalysisReason.REQUEST_TIMEOUT, LocalDateTime.now());
        NoteAnalysisClaim claim = claimer.claim(noteId, LEASE, LocalDateTime.now()).orElseThrow();
        LearningNote note = learningNoteRepository.findById(noteId).orElseThrow();
        note.markAnalyzed(LocalDateTime.now());
        learningNoteRepository.saveAndFlush(note);

        assertThat(recorder.complete(claim, List.of(new KeywordMatch(redis.getId(), "ai")), LocalDateTime.now()))
                .isEqualTo(NoteAnalysisJobOutcome.COMPLETED);

        assertThat(job().getAcknowledgedAt()).isNotNull();
        assertThat(noteKeywordRepository.findKeywordIdsByNoteId(noteId)).isEmpty();
    }

    @Test
    void 서버_한_대의_AI_동시_호출_자리는_즉시_요청_15개와_재처리_5개로_나뉜다() {
        int request = bulkheadRegistry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD).getBulkheadConfig().getMaxConcurrentCalls();
        int retry = bulkheadRegistry.bulkhead(NoteAnalysisJobRunner.RETRY_BULKHEAD).getBulkheadConfig().getMaxConcurrentCalls();

        assertThat(request).isEqualTo(15);
        assertThat(retry).isEqualTo(5);
        assertThat(request + retry).isEqualTo(20);
    }

    @Test
    void 즉시_요청_허가가_모두_쓰여도_폴러는_재처리_허가로_대기_작업을_처리한다() {
        jobService.enqueue(noteId, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
        aiReturns(redis);
        Bulkhead request = bulkheadRegistry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD);
        int requestPermits = request.getBulkheadConfig().getMaxConcurrentCalls();
        for (int i = 0; i < requestPermits; i++) {
            assertThat(request.tryAcquirePermission()).isTrue();
        }
        try {
            assertThat(runner.dispatch(LocalDateTime.now())).isEqualTo(1);
            awaitStatus(NoteAnalysisJobStatus.DONE);
            assertThat(request.getMetrics().getAvailableConcurrentCalls()).isZero();
        } finally {
            for (int i = 0; i < requestPermits; i++) {
                request.releasePermission();
            }
        }
    }

    @Test
    void 대기_작업이_재처리_한도보다_많으면_한도만큼만_AI를_부르고_끝난_뒤_나머지를_처리한다() {
        Bulkhead retry = bulkheadRegistry.bulkhead(NoteAnalysisJobRunner.RETRY_BULKHEAD);
        Bulkhead request = bulkheadRegistry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD);
        int retryPermits = retry.getBulkheadConfig().getMaxConcurrentCalls();
        List<Long> noteIds = queuedNotes(retryPermits + 3);
        Sinks.One<List<KeywordMatch>> ai = Sinks.one();
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(ai.asMono());

        assertThat(runner.dispatch(LocalDateTime.now())).isEqualTo(retryPermits);
        assertThat(runner.dispatch(LocalDateTime.now())).isZero();

        verify(asyncKeywordClassifier, times(retryPermits)).classify(anyString(), anyString(), any(MatchCatalog.class));
        assertThat(retry.getMetrics().getAvailableConcurrentCalls()).isZero();
        assertThat(request.getMetrics().getAvailableConcurrentCalls())
                .isEqualTo(request.getBulkheadConfig().getMaxConcurrentCalls());

        ai.tryEmitValue(List.of(new KeywordMatch(redis.getId(), "ai")));
        await(() -> retry.getMetrics().getAvailableConcurrentCalls() == retryPermits);
        assertThat(runner.dispatch(LocalDateTime.now())).isEqualTo(3);
        await(() -> noteIds.stream().allMatch(id -> statusOf(id) == NoteAnalysisJobStatus.DONE));
        await(() -> retry.getMetrics().getAvailableConcurrentCalls() == retryPermits);
    }

    @Test
    void 즉시_요청이_계속_몰려도_대기_작업이_처리되고_각_경로는_자기_한도를_넘지_않는다() throws Exception {
        Bulkhead request = bulkheadRegistry.bulkhead(NoteAnalyzer.REQUEST_BULKHEAD);
        Bulkhead retry = bulkheadRegistry.bulkhead(NoteAnalysisJobRunner.RETRY_BULKHEAD);
        int requestPermits = request.getBulkheadConfig().getMaxConcurrentCalls();
        int retryPermits = retry.getBulkheadConfig().getMaxConcurrentCalls();
        List<Long> jobNoteIds = queuedNotes(12);
        Member member = memberRepository.findById(memberId).orElseThrow();
        List<LearningNote> liveNotes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            liveNotes.add(learningNoteRepository.save(LearningNote.create(member, "즉시-" + i, "Redis 정리")));
        }
        InFlight requestCalls = new InFlight();
        InFlight retryCalls = new InFlight();
        InFlight allCalls = new InFlight();
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class))).willAnswer(invocation -> {
            InFlight path = invocation.<String>getArgument(0).startsWith("즉시-") ? requestCalls : retryCalls;
            return Mono.defer(() -> {
                        path.enter();
                        allCalls.enter();
                        return Mono.delay(Duration.ofMillis(80));
                    })
                    .map(tick -> List.of(new KeywordMatch(redis.getId(), "ai")))
                    .doOnTerminate(() -> {
                        path.leave();
                        allCalls.leave();
                    })
                    .doOnCancel(() -> {
                        path.leave();
                        allCalls.leave();
                    });
        });
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService load = Executors.newFixedThreadPool(4);
        try {
            for (int worker = 0; worker < 4; worker++) {
                LearningNote note = liveNotes.get(worker);
                load.submit(() -> {
                    while (running.get()) {
                        try {
                            noteAnalyzer.analyzeAsync(note).subscribe(ignored -> {
                            }, error -> {
                                if (error instanceof BulkheadFullException) {
                                    rejected.incrementAndGet();
                                }
                            });
                        } catch (RuntimeException ignored) {
                        }
                    }
                });
            }
            await(() -> request.getMetrics().getAvailableConcurrentCalls() == 0);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!jobNoteIds.stream().allMatch(id -> statusOf(id) == NoteAnalysisJobStatus.DONE)) {
                assertThat(System.nanoTime()).as("대기 작업이 20초 안에 모두 처리되어야 한다").isLessThan(deadline);
                runner.dispatch(LocalDateTime.now());
                Thread.sleep(20);
            }
        } finally {
            running.set(false);
            load.shutdown();
            assertThat(load.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(rejected.get()).isPositive();
        assertThat(requestCalls.max()).isPositive().isLessThanOrEqualTo(requestPermits);
        assertThat(retryCalls.max()).isPositive().isLessThanOrEqualTo(retryPermits);
        assertThat(allCalls.max()).isLessThanOrEqualTo(requestPermits + retryPermits);
        await(() -> request.getMetrics().getAvailableConcurrentCalls() == requestPermits);
        await(() -> retry.getMetrics().getAvailableConcurrentCalls() == retryPermits);
    }

    @Test
    void 처음_분석하는_노트_여러_개의_결과를_동시에_저장해도_교착_없이_모두_저장된다() throws Exception {
        Member member = memberRepository.findById(memberId).orElseThrow();
        for (int round = 0; round < 5; round++) {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                ids.add(learningNoteRepository.save(LearningNote.create(member, "동시-" + i, "Redis 정리")).getId());
            }
            AtomicInteger cursor = new AtomicInteger();

            List<Boolean> saved = runConcurrently(16, () -> noteKeywordSaver.replaceKeywords(
                    ids.get(cursor.getAndIncrement()), 0L,
                    List.of(new KeywordMatch(redis.getId(), "ai")), LocalDateTime.now()));

            assertThat(saved).containsOnly(true);
            assertThat(ids).allSatisfy(id ->
                    assertThat(noteKeywordRepository.findKeywordIdsByNoteId(id)).containsExactly(redis.getId()));
        }
    }

    private List<Long> queuedNotes(int count) {
        Member member = memberRepository.findById(memberId).orElseThrow();
        List<Long> noteIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Long id = learningNoteRepository.save(LearningNote.create(member, "작업-" + i, "Redis 정리")).getId();
            jobService.enqueue(id, NoteAnalysisReason.CAPACITY_EXCEEDED, LocalDateTime.now());
            noteIds.add(id);
        }
        return noteIds;
    }

    private NoteAnalysisJobStatus statusOf(Long id) {
        return jobRepository.findById(id).orElseThrow().getStatus();
    }

    private static final class InFlight {

        private final AtomicInteger current = new AtomicInteger();
        private final AtomicInteger max = new AtomicInteger();

        void enter() {
            max.accumulateAndGet(current.incrementAndGet(), Math::max);
        }

        void leave() {
            current.decrementAndGet();
        }

        int max() {
            return max.get();
        }
    }

    private ResultActions recommendations() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/notes/" + noteId + "/recommendations").header(AUTHORIZATION, BEARER))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started));
    }

    private void signedInAs(String uid, String email) {
        given(firebaseTokenVerifier.verify(anyString())).willReturn(new AuthUser("github", uid, email));
    }

    private void aiReturns(Keyword... keywords) {
        List<KeywordMatch> matches = java.util.Arrays.stream(keywords)
                .map(keyword -> new KeywordMatch(keyword.getId(), "ai"))
                .toList();
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.just(matches));
    }

    private void aiFails() {
        given(asyncKeywordClassifier.classify(anyString(), anyString(), any(MatchCatalog.class)))
                .willReturn(Mono.error(new KeywordClassificationException("Claude, GPT 키워드 분류 모두 실패",
                        new RuntimeException("503"))));
    }

    private NoteAnalysisJob job() {
        return jobRepository.findById(noteId).orElseThrow();
    }

    private void awaitStatus(NoteAnalysisJobStatus expected) {
        await(() -> job().getStatus() == expected);
    }

    private static void await(Supplier<Boolean> condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("10초 안에 조건을 만족하지 않았습니다");
    }

    private static <T> List<T> runConcurrently(int workers, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
