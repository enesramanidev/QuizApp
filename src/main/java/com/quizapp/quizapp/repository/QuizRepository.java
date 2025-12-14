package com.quizapp.quizapp.repository;

import com.quizapp.quizapp.domain.Quiz;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    // Fetch quiz + its questions; options will be loaded lazily per question
    @EntityGraph(attributePaths = {"questions"})
    Quiz findWithQuestionsById(Long id);

    // All quizzes created by a specific teacher
    List<Quiz> findByTeacherId(Integer teacherId);
}
