package com.johnnyb.openspec.editor;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.johnnyb.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScenarioAnnotator implements Annotator {

    private static final Pattern SCENARIO_KEYWORD_PATTERN = Pattern.compile(
            "^-\\s+\\*{0,2}(GIVEN|WHEN|THEN|AND)\\*{0,2}\\b", Pattern.MULTILINE);

    private static final TextAttributesKey SCENARIO_KEYWORD = TextAttributesKey.createTextAttributesKey(
            "OPENSPEC_SCENARIO_KEYWORD", DefaultLanguageHighlighterColors.METADATA);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiFile file)) return;
        if (!OpenSpecFileUtil.isSpecFile(file.getVirtualFile())) return;

        String text = file.getText();

        Matcher matcher = SCENARIO_KEYWORD_PATTERN.matcher(text);
        while (matcher.find()) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(new TextRange(matcher.start(1), matcher.end(1)))
                    .textAttributes(SCENARIO_KEYWORD)
                    .create();
        }
    }
}
