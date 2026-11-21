package com.takemybible.backend.repository;
import com.takemybible.backend.dto.CrossReferenceDto;
import org.springframework.data.jpa.repository.JpaRepository;

import com.takemybible.backend.entity.CrossReference;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CrossReferenceRepository extends JpaRepository<CrossReference, Long> {

    List<CrossReference> findByFromBookAndFromChapterAndFromVerse(
        String fromBook, Integer fromChapter, Integer fromVerse
    );

    @Modifying
    @Query("update CrossReference c set c.votes = c.votes + :delta where c.id = :id")
    int addVotes(@Param("id") Long id, @Param("delta") int delta);

    @Query("""
        select new com.takemybible.backend.dto.CrossReferenceDto(
            cr.id, cr.fromBook, cr.fromChapter, cr.fromVerse,
            cr.toBook, cr.toChapter, cr.toVerseStart, cr.toVerseEnd,
            cr.votes, coalesce(v.value, 0))
        from CrossReference cr
        left join CrossReferenceVotes v
            on v.crossReferenceId = cr.id and v.userId = :userId
        where cr.fromBook = :book and cr.fromChapter = :chapter and cr.fromVerse = :verse
        order by cr.votes desc
        """)
    List<CrossReferenceDto> findWithUserVote(
            @Param("book") String book,
            @Param("chapter") Integer chapter,
            @Param("verse") Integer verse,
            @Param("userId") Long userId);   // null for anonymous → no join match → userVote 0



    @Query("""
select c from CrossReference c
where c.createdBy = :userId
  and c.fromBook = :fromBook and c.fromChapter = :fromChapter and c.fromVerse = :fromVerse
  and c.toBook = :toBook and c.toChapter = :toChapter
  and c.toVerseStart = :start and c.toVerseEnd = :end
""")
    Optional<CrossReference> findDuplicate(@Param("userId") Long userId,
                            @Param("fromBook") String fromBook,
                            @Param("fromChapter") Integer fromChapter,
                            @Param("fromVerse") Integer fromVerse,
                            @Param("toBook") String toBook,
                            @Param("toChapter") Integer toChapter,
                            @Param("start") Integer start,
                            @Param("end") Integer end);
}
