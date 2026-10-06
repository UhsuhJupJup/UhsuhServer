package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class HtmlCommentsTest {

    @Test
    void removeFrom_textWithoutComments_isUnchanged() {
        String markdown = "Steps:\n1. Run `queue start`\n2. See 3 < 5 retries\n";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo(markdown);
    }

    @Test
    void removeFrom_commentInsideALine_isRemoved() {
        assertThat(HtmlComments.removeFrom("Before <!-- hidden --> after")).isEqualTo("Before  after");
    }

    @Test
    void removeFrom_templateCommentOverManyLines_isRemoved() {
        String markdown = "### Describe the bug\n<!--\nA clear description.\n```\nexample\n```\n-->\nThe worker hangs.\n";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo("### Describe the bug\n\nThe worker hangs.\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<!-->", "<!--->", "<!---->"})
    void removeFrom_emptyComments_areRemoved(String comment) {
        assertThat(HtmlComments.removeFrom("a" + comment + "b")).isEqualTo("ab");
    }

    @ParameterizedTest
    @ValueSource(strings = {"```", "~~~", "````", "  ```"})
    void removeFrom_commentInsideAFencedCodeBlock_isKept(String fence) {
        String markdown = fence + "html\n<!-- shown in code -->\n" + fence + "\n<!-- hidden -->after";

        assertThat(HtmlComments.removeFrom(markdown))
                .isEqualTo(fence + "html\n<!-- shown in code -->\n" + fence + "\nafter");
    }

    @Test
    void removeFrom_fenceClosesOnlyWithARunOfTheSameMarkerAtLeastAsLong() {
        String markdown = "````\n```\n~~~~\n<!-- still code -->\n`````\n<!-- hidden -->";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo("````\n```\n~~~~\n<!-- still code -->\n`````\n");
    }

    @Test
    void removeFrom_unclosedFence_keepsTheRestAsCode() {
        String markdown = "```\n<!-- shown in code -->\nmore code";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo(markdown);
    }

    @ParameterizedTest
    @ValueSource(strings = {"``` a`b", "    ```", "\t```", "``"})
    void removeFrom_lineThatIsNotAFence_doesNotProtectComments(String notAFence) {
        String markdown = notAFence + "\n<!-- hidden -->\n```";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo(notAFence + "\n\n```");
    }

    @Test
    void removeFrom_unclosedCommentStartingALine_hidesTheRestOfTheBody() {
        String markdown = "Visible\n  <!-- unclosed\nhidden\n```\nhidden code\n```\n";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo("Visible\n  ");
    }

    @Test
    void removeFrom_unclosedCommentAfterAClosedOneOnTheSameLine_hidesTheRestOfTheBody() {
        assertThat(HtmlComments.removeFrom("<!-- a --> <!-- b\nhidden")).isEqualTo(" ");
    }

    @Test
    void removeFrom_unclosedCommentAfterText_staysAsText() {
        String markdown = "Typing <!-- breaks the preview.\nNext line";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo(markdown);
    }

    @Test
    void removeFrom_commentStartingMidLineAndEndingLater_isRemovedAcrossLines() {
        assertThat(HtmlComments.removeFrom("Shown <!-- one\ntwo --> shown\nnext")).isEqualTo("Shown  shown\nnext");
    }

    @Test
    void removeFrom_inlineOpeningClosedOnlyInALaterParagraph_staysAsText() {
        String markdown = "The `<!--` token breaks parsing.\n\nSteps:\n1. Run A --> B\n2. See the error\n";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo(markdown);
    }

    @Test
    void removeFrom_inlineOpeningRightBeforeAFence_keepsTheFenceAndItsArrows() {
        String markdown = "Typing <!-- breaks the preview.\n```mermaid\ngraph LR\nA --> B\n```\nSteps follow.\n";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo(markdown);
    }

    @Test
    void removeFrom_inlineOpeningBeforeAMermaidBlockInTheNextParagraph_keepsEverything() {
        String markdown = "Typing <!-- breaks the preview.\n\n```mermaid\nA --> B\n```\n\nRepro: run it twice.\n";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo(markdown);
    }

    @Test
    void removeFrom_inlineCommentOverLinesOfOneParagraph_isRemoved() {
        assertThat(HtmlComments.removeFrom("Text <!-- hidden\nstill hidden --> shown\n\nNext"))
                .isEqualTo("Text  shown\n\nNext");
    }

    @Test
    void removeFrom_commentStartingALine_reachesPastBlankLinesAndFences() {
        String markdown = "<!--\n\nTemplate help\n\n```\nexample\n```\n-->\nBody";

        assertThat(HtmlComments.removeFrom(markdown)).isEqualTo("\nBody");
    }

    @Test
    void removeFrom_manyUnclosedInlineOpeningsOnOneLine_finishesInLinearTime() {
        String markdown = "a<!--".repeat(200_000);

        String visible = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> HtmlComments.removeFrom(markdown));

        assertThat(visible).isEqualTo(markdown);
    }

    @Test
    void removeFrom_manyUnclosedInlineOpeningsOnManyLines_finishesInLinearTime() {
        String markdown = "x <!--\n".repeat(100_000) + "\n-->";

        String visible = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> HtmlComments.removeFrom(markdown));

        assertThat(visible).isEqualTo(markdown);
    }

    @Test
    void removeFrom_windowsAndOldMacLineBreaks_areKept() {
        assertThat(HtmlComments.removeFrom("a\r\n<!-- x -->\r\n```\r<!-- code -->\r```\rb"))
                .isEqualTo("a\r\n\r\n```\r<!-- code -->\r```\rb");
    }
}
