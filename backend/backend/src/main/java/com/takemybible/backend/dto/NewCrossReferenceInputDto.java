package com.takemybible.backend.dto;

import com.takemybible.backend.entity.Confidence;
import jakarta.persistence.Column;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

@Data
public final class NewCrossReferenceInputDto {
    private final @NotBlank String fromBook;
    private final @NotNull
    @Positive Integer fromChapter;
    private final @NotNull
    @Positive Integer fromVerse;
    private final String toBook;
    private final @NotNull
    @Positive Integer toChapter;
    private final @NotNull
    @Positive Integer toVerseStart;
    private final @NotNull
    @Positive Integer toVerseEnd;
    private final @NotNull
    @Positive Integer toVerse;
    private final @NotNull
    Integer votes;
    private final String connectionType;
    private final Confidence confidence;
    private final String[] themeTags;

    public NewCrossReferenceInputDto(
            @NotBlank String fromBook,
            @NotNull @Positive Integer fromChapter,
            @NotNull @Positive Integer fromVerse,
            String toBook,
            @NotNull @Positive Integer toChapter,
            @NotNull @Positive Integer toVerseStart,
            @NotNull @Positive Integer toVerseEnd,
            @NotNull @Positive Integer toVerse,
            @NotNull @Positive Integer votes,
            String connectionType,
            Confidence confidence,
            String[] themeTags


    ) {
        this.fromBook = fromBook;
        this.fromChapter = fromChapter;
        this.fromVerse = fromVerse;
        this.toBook = toBook;
        this.toChapter = toChapter;
        this.toVerseStart = toVerseStart;
        this.toVerseEnd = toVerseEnd;
        this.toVerse = toVerse;
        this.votes = votes;
        this.connectionType = connectionType;
        this.confidence = confidence;
        this.themeTags = themeTags;
    }

    @AssertTrue(message = "toVerseEnd must be greater than or equal to toVerseStart")
    public boolean isVerseRangeValid() {
        if (toVerseStart == null || toVerseEnd == null) {
            return true; // nothing to compare; @NotNull handles start, end is optional
        }
        return toVerseStart <= toVerseEnd;
    }

    public @NotBlank String fromBook() {
        return fromBook;
    }

    public @NotNull @Positive Integer fromChapter() {
        return fromChapter;
    }

    public @NotNull @Positive Integer fromVerse() {
        return fromVerse;
    }

    public String toBook() {
        return toBook;
    }

    public @NotNull @Positive Integer toChapter() {
        return toChapter;
    }

    public @NotNull @Positive Integer toVerseStart() {
        return toVerseStart;
    }

    public @NotNull @Positive Integer toVerseEnd() {
        return toVerseEnd;
    }

    public @NotNull @Positive Integer toVerse() {
        return toVerse;
    }

    public @NotNull @Positive Integer votes() {
        return votes;
    }

    public String connectionType() {
        return connectionType;
    }

    public Confidence confidence() {
        return confidence;
    }

    public String[] themeTags() {
        return themeTags;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (NewCrossReferenceInputDto) obj;
        return
                Objects.equals(this.fromBook, that.fromBook) &&
                Objects.equals(this.fromChapter, that.fromChapter) &&
                Objects.equals(this.fromVerse, that.fromVerse) &&
                Objects.equals(this.toBook, that.toBook) &&
                Objects.equals(this.toChapter, that.toChapter) &&
                Objects.equals(this.toVerseStart, that.toVerseStart) &&
                Objects.equals(this.toVerseEnd, that.toVerseEnd) &&
                Objects.equals(this.toVerse, that.toVerse) &&
                Objects.equals(this.votes, that.votes) &&
                Objects.equals(this.connectionType, that.connectionType) &&
                Objects.equals(this.confidence, that.confidence) &&
                Objects.equals(this.themeTags, that.themeTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash( fromBook, fromChapter, fromVerse, toBook, toChapter, toVerseStart, toVerseEnd, toVerse, votes, connectionType, confidence, themeTags);
    }

    @Override
    public String toString() {
        return "NewCrossReferenceInputDto[" +
                "fromBook=" + fromBook + ", " +
                "fromChapter=" + fromChapter + ", " +
                "fromVerse=" + fromVerse + ", " +
                "toBook=" + toBook + ", " +
                "toChapter=" + toChapter + ", " +
                "toVerseStart=" + toVerseStart + ", " +
                "toVerseEnd=" + toVerseEnd + ", " +
                "toVerse=" + toVerse + ", " +
                "votes=" + votes + ", " +
                "connectionType=" + connectionType + ", " +
                "confidence=" + confidence + ", " +
                "themeTags=" + themeTags + ']';
    }

}
