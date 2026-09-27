package uhsuhjupjup.backend.oss.repo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.domain.Role;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubRepo;
import uhsuhjupjup.backend.oss.repo.application.OssRepoSaver;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
class AdminOssRepoIntegrationTest {

    private static final String REGISTER_URL = "/api/admin/oss/repos";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_TOKEN = "user-token";
    private static final long GITHUB_ID = 1296269L;
    private static final GitHubRepo HELLO_WORLD = new GitHubRepo(
            GITHUB_ID, "octocat/Hello-World", "My first repository on GitHub!", "Java", 80, false, true, false);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @MockitoBean
    private GitHubClient gitHubClient;

    @MockitoSpyBean
    private OssRepoSaver ossRepoSaver;

    @BeforeEach
    void setUp() {
        ossRepoRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        signUp(ADMIN_TOKEN, "admin-uid", "admin@example.com", Role.ADMIN);
        signUp(USER_TOKEN, "user-uid", "user@example.com", Role.USER);
    }

    @Test
    void register_asAdmin_storesGitHubCanonicalNameAndReturns201() throws Exception {
        AtomicBoolean lookedUpInsideTransaction = new AtomicBoolean(true);
        given(gitHubClient.findRepo("OCTOCAT", "hello-world")).willAnswer(invocation -> {
            lookedUpInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(HELLO_WORLD);
        });

        MockHttpServletResponse response = mockMvc.perform(register(ADMIN_TOKEN, "OCTOCAT/hello-world"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.githubId").value(GITHUB_ID))
                .andExpect(jsonPath("$.fullName").value("octocat/Hello-World"))
                .andExpect(jsonPath("$.description").value("My first repository on GitHub!"))
                .andExpect(jsonPath("$.primaryLanguage").value("Java"))
                .andExpect(jsonPath("$.stars").value(80))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse();

        assertThat(lookedUpInsideTransaction).isFalse();
        OssRepo stored = ossRepoRepository.findByGithubId(GITHUB_ID).orElseThrow();
        assertThat(JsonPath.<Number>read(response.getContentAsString(), "$.id").longValue()).isEqualTo(stored.getId());
        assertThat(stored.getFullName()).isEqualTo("octocat/Hello-World");
        assertThat(stored.getFullNameKey()).isEqualTo("octocat/hello-world");
        assertThat(stored.getStatus()).isEqualTo(OssRepoStatus.ACTIVE);
    }

    @Test
    void register_asNonAdmin_returns403WithoutCallingGitHub() throws Exception {
        mockMvc.perform(register(USER_TOKEN, "octocat/Hello-World"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        then(gitHubClient).shouldHaveNoInteractions();
        assertThat(ossRepoRepository.count()).isZero();
    }

    @Test
    void register_asNonAdminWithMalformedName_returns403BeforeValidation() throws Exception {
        mockMvc.perform(register(USER_TOKEN, "octocat/.."))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        then(gitHubClient).shouldHaveNoInteractions();
    }

    @Test
    void register_sameRepoAgain_returns409AndKeepsOneRow() throws Exception {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willReturn(Optional.of(HELLO_WORLD));
        given(gitHubClient.findRepo("OCTOCAT", "HELLO-WORLD")).willReturn(Optional.of(HELLO_WORLD));
        mockMvc.perform(register(ADMIN_TOKEN, "octocat/Hello-World"))
                .andExpect(status().isCreated());

        mockMvc.perform(register(ADMIN_TOKEN, "OCTOCAT/HELLO-WORLD"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OSS_REPO_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 등록된 레포입니다."));

        assertThat(ossRepoRepository.count()).isEqualTo(1);
    }

    @Test
    void register_nameDifferingOnlyInCaseFromStoredRepo_returns409() throws Exception {
        ossRepoRepository.save(OssRepo.create(42L, "OctoCat/HELLO-WORLD", null, null, 0));
        given(gitHubClient.findRepo("octocat", "Hello-World")).willReturn(Optional.of(HELLO_WORLD));

        mockMvc.perform(register(ADMIN_TOKEN, "octocat/Hello-World"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OSS_REPO_ALREADY_EXISTS"));

        assertThat(ossRepoRepository.findAll()).extracting(OssRepo::getGithubId).containsExactly(42L);
    }

    @Test
    void register_sameRepoTwiceAtOnce_keepsOneRowAndAnswers409ToTheOther() throws Exception {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willReturn(Optional.of(HELLO_WORLD));
        CyclicBarrier bothPassedDuplicateCheck = new CyclicBarrier(2);
        willAnswer(invocation -> {
            bothPassedDuplicateCheck.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).given(ossRepoSaver).save(any(OssRepo.class));

        List<MockHttpServletResponse> responses = sendTogether(2,
                () -> mockMvc.perform(register(ADMIN_TOKEN, "octocat/Hello-World")).andReturn().getResponse());

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 409);
        MockHttpServletResponse rejected = responses.stream()
                .filter(response -> response.getStatus() == 409)
                .findFirst()
                .orElseThrow();
        assertThat(JsonPath.<String>read(rejected.getContentAsString(), "$.code")).isEqualTo("OSS_REPO_ALREADY_EXISTS");
        assertThat(ossRepoRepository.count()).isEqualTo(1);
        then(ossRepoSaver).should(times(2)).save(any(OssRepo.class));
    }

    @Test
    void register_archivedRepo_returns400AndStoresNothing() throws Exception {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willReturn(Optional.of(new GitHubRepo(
                GITHUB_ID, "octocat/Hello-World", null, null, 80, false, true, true)));

        mockMvc.perform(register(ADMIN_TOKEN, "octocat/Hello-World"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OSS_REPO_ARCHIVED"))
                .andExpect(jsonPath("$.message").value("보관(archived)된 레포는 등록할 수 없습니다."));

        assertThat(ossRepoRepository.count()).isZero();
    }

    @Test
    void register_whenGitHubRateLimited_returns503AndStoresNothing() throws Exception {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willThrow(new GitHubClientException(
                Reason.RATE_LIMITED, 403, "GitHub 요청 실패(상태 403): /repos/octocat/Hello-World", null));

        mockMvc.perform(register(ADMIN_TOKEN, "octocat/Hello-World"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GITHUB_UNAVAILABLE"));

        assertThat(ossRepoRepository.count()).isZero();
    }

    private void signUp(String token, String uid, String email, Role role) {
        Member member = Member.create("google", uid, email);
        ReflectionTestUtils.setField(member, "role", role);
        memberRepository.save(member);
        given(firebaseTokenVerifier.verify(token)).willReturn(new AuthUser("google", uid, email, Instant.now()));
    }

    private MockHttpServletRequestBuilder register(String token, String fullName) {
        return post(REGISTER_URL)
                .header(AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"" + fullName + "\"}");
    }

    private List<MockHttpServletResponse> sendTogether(int count, Callable<MockHttpServletResponse> request)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<MockHttpServletResponse>> futures =
                    executor.invokeAll(Collections.nCopies(count, request), 30, TimeUnit.SECONDS);
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get());
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }
}
