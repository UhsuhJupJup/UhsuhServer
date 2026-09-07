package uhsuhjupjup.backend.archive.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.archive.application.BookmarkService;
import uhsuhjupjup.backend.archive.ui.dto.BookmarkedArticlesResponse;
import uhsuhjupjup.backend.common.auth.LoginMember;
import uhsuhjupjup.backend.member.domain.Member;

@RestController
@RequestMapping("/api/me/bookmarks")
@RequiredArgsConstructor
public class BookmarkController implements BookmarkControllerApi {

    private final BookmarkService bookmarkService;

    @Override
    @GetMapping
    public BookmarkedArticlesResponse myBookmarks(@LoginMember Member member) {
        return BookmarkedArticlesResponse.from(bookmarkService.getBookmarks(member.getId()));
    }

    @Override
    @PostMapping("/{articleId}")
    @ResponseStatus(HttpStatus.CREATED)
    public void addBookmark(@LoginMember Member member, @PathVariable Long articleId) {
        bookmarkService.addBookmark(member, articleId);
    }

    @Override
    @DeleteMapping("/{articleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBookmark(@LoginMember Member member, @PathVariable Long articleId) {
        bookmarkService.removeBookmark(member.getId(), articleId);
    }
}
