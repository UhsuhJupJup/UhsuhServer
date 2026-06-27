package uhsuhjupjup.backend.subscription.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.subscription.application.dto.SubscriptionsResult;
import uhsuhjupjup.backend.subscription.domain.KeywordSubscription;
import uhsuhjupjup.backend.subscription.domain.TopicSubscription;
import uhsuhjupjup.backend.subscription.infra.KeywordSubscriptionRepository;
import uhsuhjupjup.backend.subscription.infra.TopicSubscriptionRepository;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.infra.TopicRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final TopicSubscriptionRepository topicSubscriptionRepository;
    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final TopicRepository topicRepository;
    private final KeywordRepository keywordRepository;
    private final MemberRepository memberRepository;

    public SubscriptionsResult getMySubscriptions(Long memberId) {
        return new SubscriptionsResult(
                topicSubscriptionRepository.findSubscribedTopics(memberId),
                keywordSubscriptionRepository.findSubscribedKeywords(memberId));
    }

    @Transactional
    public SubscriptionsResult replaceSubscriptions(Member member, List<Long> topicIds, List<Long> keywordIds) {
        requireConsent(member);
        syncTopics(member, loadTopics(topicIds));
        syncKeywords(member, loadKeywords(keywordIds));
        return getMySubscriptions(member.getId());
    }

    private void requireConsent(Member member) {
        if (!member.hasConsented()) {
            throw new BusinessException(ErrorCode.CONSENT_REQUIRED);
        }
    }

    private void syncTopics(Member member, List<Topic> desired) {
        Set<Long> currentIds = topicSubscriptionRepository.findSubscribedTopics(member.getId()).stream()
                .map(Topic::getId).collect(Collectors.toSet());
        Set<Long> desiredIds = desired.stream().map(Topic::getId).collect(Collectors.toSet());
        List<Long> toRemove = currentIds.stream().filter(id -> !desiredIds.contains(id)).toList();
        if (!toRemove.isEmpty()) {
            topicSubscriptionRepository.deleteByMemberIdAndTopicIdIn(member.getId(), toRemove);
        }
        desired.stream()
                .filter(topic -> !currentIds.contains(topic.getId()))
                .forEach(topic -> topicSubscriptionRepository.save(TopicSubscription.of(member, topic)));
    }

    private void syncKeywords(Member member, List<Keyword> desired) {
        Set<Long> currentIds = keywordSubscriptionRepository.findSubscribedKeywords(member.getId()).stream()
                .map(Keyword::getId).collect(Collectors.toSet());
        Set<Long> desiredIds = desired.stream().map(Keyword::getId).collect(Collectors.toSet());
        List<Long> toRemove = currentIds.stream().filter(id -> !desiredIds.contains(id)).toList();
        if (!toRemove.isEmpty()) {
            keywordSubscriptionRepository.deleteByMemberIdAndKeywordIdIn(member.getId(), toRemove);
        }
        desired.stream()
                .filter(keyword -> !currentIds.contains(keyword.getId()))
                .forEach(keyword -> keywordSubscriptionRepository.save(KeywordSubscription.of(member, keyword)));
    }

    private List<Topic> loadTopics(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = topicIds.stream().distinct().toList();
        List<Topic> topics = topicRepository.findAllById(distinctIds);
        if (topics.size() != distinctIds.size()) {
            throw new BusinessException(ErrorCode.TOPIC_NOT_FOUND);
        }
        return topics;
    }

    private List<Keyword> loadKeywords(List<Long> keywordIds) {
        if (keywordIds == null || keywordIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = keywordIds.stream().distinct().toList();
        List<Keyword> keywords = keywordRepository.findAllById(distinctIds);
        if (keywords.size() != distinctIds.size()) {
            throw new BusinessException(ErrorCode.KEYWORD_NOT_FOUND);
        }
        return keywords;
    }

    @Transactional
    public void unsubscribeAll(Long memberId) {
        topicSubscriptionRepository.deleteByMemberId(memberId);
        keywordSubscriptionRepository.deleteByMemberId(memberId);
    }

    @Transactional
    public void unsubscribeByToken(String token) {
        Member member = memberRepository.findByUnsubscribeToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_UNSUBSCRIBE_TOKEN));
        topicSubscriptionRepository.deleteByMemberId(member.getId());
        keywordSubscriptionRepository.deleteByMemberId(member.getId());
    }
}
