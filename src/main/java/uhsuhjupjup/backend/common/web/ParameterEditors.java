package uhsuhjupjup.backend.common.web;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.util.function.Function;

public final class ParameterEditors {

    private ParameterEditors() {
    }

    public static PropertyEditor parsedBy(Function<String, ?> parser) {
        return new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text.isEmpty() ? null : parser.apply(text));
            }
        };
    }
}
