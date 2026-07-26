package uhsuhjupjup.backend.archive.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.archive.domain.Bookmark;

import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByMemberIdAndArticleId(Long memberId, Long articleId);

    long deleteByMemberIdAndArticleId(Long memberId, Long articleId);

    @Query("""
            select b from Bookmark b
            join fetch b.article a
            join fetch a.blog
            where b.member.id = :memberId
            order by b.createdAt desc
            """)
    List<Bookmark> findRecentWithArticleByMemberId(Long memberId, Pageable pageable);
}
