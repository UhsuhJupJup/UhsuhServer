package uhsuhjupjup.backend.subscription.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.subscription.application.SubscriptionService;
import uhsuhjupjup.backend.subscription.application.dto.SubscriptionsResult;
import uhsuhjupjup.backend.support.KeywordFixture;
import uhsuhjupjup.backend.support.LoginMemberStubResolver;
import uhsuhjupjup.backend.support.MemberFixture;
import uhsuhjupjup.backend.support.TopicFixture;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    private MockMvc mockMvc;
    private final Member loginMember = MemberFixture.member(1L, "octocat@github.com");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SubscriptionController(subscriptionService))
                .setCustomArgumentResolvers(new LoginMemberStubResolver(loginMember))
                .build();
    }

    @Test
    void mySubscriptions_returnsTopicsAndKeywords() throws Exception {
        given(subscriptionService.getMySubscriptions(1L)).willReturn(new SubscriptionsResult(
                List.of(TopicFixture.topic(1L, "Database")),
                List.of(KeywordFixture.keyword(11L, "Kafka"))));

        mockMvc.perform(get("/api/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0].name").value("Database"))
                .andExpect(jsonPath("$.keywords[0].name").value("Kafka"));
    }

    @Test
    void replace_returnsUpdatedSubscriptions() throws Exception {
        given(subscriptionService.replaceSubscriptions(any(Member.class), eq(List.of(1L)), eq(List.of(11L))))
                .willReturn(new SubscriptionsResult(
                        List.of(TopicFixture.topic(1L, "Database")),
                        List.of(KeywordFixture.keyword(11L, "Kafka"))));

        mockMvc.perform(put("/api/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicIds\":[1],\"keywordIds\":[11]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0].name").value("Database"))
                .andExpect(jsonPath("$.keywords[0].name").value("Kafka"));
    }

    @Test
    void unsubscribeAll_returns204() throws Exception {
        mockMvc.perform(delete("/api/subscriptions"))
                .andExpect(status().isNoContent());
    }
}
