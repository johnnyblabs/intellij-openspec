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

public class SpecAnnotator implements Annotator {

    private static final Pattern RFC_2119_PATTERN = Pattern.compile(
            "\\b(SHALL NOT|SHOULD NOT|SHALL|SHOULD|MAY)\\b");

    private static final TextAttributesKey RFC_KEYWORD = TextAttributesKey.createTextAttributesKey(
            "OPENSPEC_RFC_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiFile file)) return;
        if (!OpenSpecFileUtil.isSpecFile(file.getVirtualFile())) return;

        String text = file.getText();
        int baseOffset = 0;

        Matcher matcher = RFC_2119_PATTERN.matcher(text);
        while (matcher.find()) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(new TextRange(baseOffset + matcher.start(), baseOffset + matcher.end()))
                    .textAttributes(RFC_KEYWORD)
                    .create();
        }
    }
}
