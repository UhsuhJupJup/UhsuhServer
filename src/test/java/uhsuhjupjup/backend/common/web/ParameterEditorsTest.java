package uhsuhjupjup.backend.common.web;

import org.junit.jupiter.api.Test;

import java.beans.PropertyEditor;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParameterEditorsTest {

    @Test
    void parsedBy_text_setsWhatTheParserReturns() {
        PropertyEditor editor = ParameterEditors.parsedBy(Integer::valueOf);

        editor.setAsText("42");

        assertThat(editor.getValue()).isEqualTo(42);
    }

    @Test
    void parsedBy_emptyText_setsNullWithoutCallingTheParser() {
        PropertyEditor editor = ParameterEditors.parsedBy(text -> {
            throw new IllegalStateException("빈 값은 파서에 넘기지 않아야 합니다.");
        });

        editor.setAsText("");

        assertThat(editor.getValue()).isNull();
    }

    @Test
    void parsedBy_blankText_isGivenToTheParserAsItIs() {
        PropertyEditor editor = ParameterEditors.parsedBy(text -> "[" + text + "]");

        editor.setAsText(" ");

        assertThat(editor.getValue()).isEqualTo("[ ]");
    }

    @Test
    void parsedBy_parserFailure_propagatesUnchanged() {
        PropertyEditor editor = ParameterEditors.parsedBy(Integer::valueOf);

        assertThatThrownBy(() -> editor.setAsText("forty-two")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void parsedBy_returnsANewEditorOnEveryCall() {
        Function<String, Integer> parser = Integer::valueOf;

        assertThat(ParameterEditors.parsedBy(parser)).isNotSameAs(ParameterEditors.parsedBy(parser));
    }
}
