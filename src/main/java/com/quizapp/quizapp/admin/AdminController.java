package com.quizapp.quizapp.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.quizapp.quizapp.admin.TeacherDashboardService.TestHistoryEntry;
import com.quizapp.quizapp.auth.AuthService;
import com.quizapp.quizapp.domain.ClassEntity;
import com.quizapp.quizapp.domain.Option;
import com.quizapp.quizapp.domain.Question;
import com.quizapp.quizapp.domain.Quiz;
import com.quizapp.quizapp.repository.QuizRepository;
import com.quizapp.quizapp.repository.QuestionRepository;
import com.quizapp.quizapp.user.User;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AuthService authService;
    private final TeacherDashboardService teacherDashboardService;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;

    public AdminController(AuthService authService,
                           TeacherDashboardService teacherDashboardService,
                           QuizRepository quizRepository,
                           QuestionRepository questionRepository) {

        this.authService = authService;
        this.teacherDashboardService = teacherDashboardService;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
    }

    // -------------------------------------------------------------------------
    // Teacher Dashboard
    // -------------------------------------------------------------------------
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!authService.isAuthorized(session, User.Role.ADMIN)) {
            return "redirect:/?error=unauthorized";
        }

        User currentUser = authService.getCurrentUser(session);
        List<ClassEntity> classes = teacherDashboardService.getClassesForTeacher(currentUser.getId());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("assignedClasses", classes);
        model.addAttribute("classCount", classes.size());

        return "admin-dashboard";
    }

    // -------------------------------------------------------------------------
    // My Classes
    // -------------------------------------------------------------------------
    @GetMapping("/classes")
    public String myClasses(HttpSession session, Model model) {
        if (!authService.isAuthorized(session, User.Role.ADMIN)) {
            return "redirect:/?error=unauthorized";
        }

        User currentUser = authService.getCurrentUser(session);
        List<ClassEntity> classes = teacherDashboardService.getClassesForTeacher(currentUser.getId());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("classes", classes);

        return "admin-classes";
    }

    // -------------------------------------------------------------------------
    // Single Class View
    // -------------------------------------------------------------------------
    @GetMapping("/classes/{classId}")
    public String viewClass(@PathVariable Integer classId,
                            HttpSession session,
                            Model model,
                            RedirectAttributes redirectAttributes) {

        if (!authService.isAuthorized(session, User.Role.ADMIN)) {
            return "redirect:/?error=unauthorized";
        }

        User currentUser = authService.getCurrentUser(session);

        try {
            ClassEntity classEntity = teacherDashboardService.getClassForTeacher(classId, currentUser.getId());

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("classEntity", classEntity);
            model.addAttribute("students", teacherDashboardService.sortStudents(classEntity));
            model.addAttribute("scheduleDays", teacherDashboardService.getScheduleDays(classEntity));
            model.addAttribute("timeRange", teacherDashboardService.formatTimeRange(classEntity));

            return "admin-class-view";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/classes";
        }
    }

    // -------------------------------------------------------------------------
    // Student Test History
    // -------------------------------------------------------------------------
    @GetMapping("/classes/{classId}/students/{studentId}/tests")
    public String viewStudentTests(@PathVariable Integer classId,
                                   @PathVariable Integer studentId,
                                   HttpSession session,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {

        if (!authService.isAuthorized(session, User.Role.ADMIN)) {
            return "redirect:/?error=unauthorized";
        }

        User currentUser = authService.getCurrentUser(session);

        try {
            ClassEntity classEntity = teacherDashboardService.getClassForTeacher(classId, currentUser.getId());

            User student = teacherDashboardService
                    .findStudentInClass(classEntity, studentId)
                    .orElseThrow(() -> new IllegalArgumentException("Student not found in this class."));

            List<TestHistoryEntry> history = teacherDashboardService.buildTestHistorySnapshot(currentUser);

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("classEntity", classEntity);
            model.addAttribute("student", student);
            model.addAttribute("history", history);

            return "admin-test-history";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/classes";
        }
    }

    // -------------------------------------------------------------------------
    // My Tests (List)
    // -------------------------------------------------------------------------
    @GetMapping("/tests")
    public String myTests(HttpSession session, Model model) {
        if (!authService.isAuthorized(session, User.Role.ADMIN)) {
            return "redirect:/?error=unauthorized";
        }

        User currentUser = authService.getCurrentUser(session);

        Integer teacherId = currentUser.getId();
        List<Quiz> quizzes = quizRepository.findByTeacherId(teacherId);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("quizzes", quizzes);
        model.addAttribute("quizCount", quizzes.size());

        return "my-tests";
    }

    // -------------------------------------------------------------------------
    // Create Test
    // -------------------------------------------------------------------------
    @PostMapping("/tests")
    public String createTest(@RequestParam String title,
                             @RequestParam(required = false) String description,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        if (!authService.isAuthorized(session, User.Role.ADMIN)) {
            return "redirect:/?error=unauthorized";
        }

        User currentUser = authService.getCurrentUser(session);
        if (currentUser == null) {
            return "redirect:/?error=unauthorized";
        }

        Quiz quiz = new Quiz();
        quiz.setTitle(title.trim());
        quiz.setDescription(
                (description == null || description.isBlank())
                        ? null
                        : description.trim()
        );
        quiz.setTeacherId(currentUser.getId());

        quizRepository.save(quiz);

        redirectAttributes.addFlashAttribute("success", "Test created successfully.");
        return "redirect:/admin/tests";
    }

    // -------------------------------------------------------------------------
    // Edit Test Page
    // -------------------------------------------------------------------------
    @GetMapping("/tests/{quizId}")
    public String editTest(@PathVariable Long quizId,
                           HttpSession session,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        if (!authService.isAuthorized(session, User.Role.ADMIN)) {
            return "redirect:/?error=unauthorized";
        }

        User currentUser = authService.getCurrentUser(session);
        if (currentUser == null) {
            return "redirect:/?error=unauthorized";
        }

        Quiz quiz = quizRepository.findWithQuestionsById(quizId);

        if (quiz == null || !quiz.getTeacherId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("error", "Test not found or you are not allowed to edit it.");
            return "redirect:/admin/tests";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("quiz", quiz);

        return "edit-test";
    }

    // -------------------------------------------------------------------------
    // Update Basic Test Info (title + description)
    // -------------------------------------------------------------------------
@PostMapping("/tests/{quizId}/basic")
public String updateTestBasic(@PathVariable Long quizId,
                              @RequestParam String title,
                              @RequestParam(required = false) String description,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {

    if (!authService.isAuthorized(session, User.Role.ADMIN)) {
        return "redirect:/?error=unauthorized";
    }

    User currentUser = authService.getCurrentUser(session);
    if (currentUser == null) {
        return "redirect:/?error=unauthorized";
    }

    Quiz quiz = quizRepository.findWithQuestionsById(quizId);
    if (quiz == null || !quiz.getTeacherId().equals(currentUser.getId())) {
        redirectAttributes.addFlashAttribute("error", "Test not found or you are not allowed to edit it.");
        return "redirect:/admin/tests";
    }

    quiz.setTitle(title.trim());
    quiz.setDescription(
            (description == null || description.isBlank())
                    ? null
                    : description.trim()
    );

    quizRepository.save(quiz);
    redirectAttributes.addFlashAttribute("success", "Test info updated.");

    // ❌ OLD:
    // return "redirect:/admin/tests/" + quizId;

    // ✅ NEW – keep editing enabled:
    return "redirect:/admin/tests/" + quizId + "?editing=true";
}



    // -------------------------------------------------------------------------
    // Save Question (Create or Update)
    // -------------------------------------------------------------------------
@PostMapping("/tests/{quizId}/questions")
public String saveQuestion(
        @PathVariable Long quizId,
        @RequestParam(name = "questionId", required = false) Long questionId,
        @RequestParam("text") String text,
        @RequestParam("option1") String option1,
        @RequestParam("option2") String option2,
        @RequestParam(name = "option3", required = false) String option3,
        @RequestParam(name = "option4", required = false) String option4,
        @RequestParam(name = "correctOptions", required = false) List<Integer> correctOptions,
        HttpSession session,
        RedirectAttributes redirectAttributes
) {
    User currentUser = authService.getCurrentUser(session);
    if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
        return "redirect:/?error=unauthorized";
    }

    Quiz quiz = quizRepository.findById(quizId).orElse(null);
    if (quiz == null) {
        redirectAttributes.addFlashAttribute("error", "Test not found.");
        return "redirect:/admin/tests";
    }

    if (!quiz.getTeacherId().equals(currentUser.getId())) {
        redirectAttributes.addFlashAttribute("error", "You are not allowed to edit this test.");
        return "redirect:/admin/tests";
    }

    Question question;
    if (questionId != null) {
        question = questionRepository.findById(questionId).orElse(null);
        if (question == null) {
            redirectAttributes.addFlashAttribute("error", "Question not found.");
            return "redirect:/admin/tests/" + quizId + "?editing=true";
        }
    } else {
        question = new Question();
        question.setQuiz(quiz);
        quiz.getQuestions().add(question);
    }

    question.setText(text);

    if (question.getOptions() == null) {
        question.setOptions(new ArrayList<>());
    } else {
        question.getOptions().clear();
    }

    List<String> optionTexts = List.of(option1, option2, option3, option4);

    for (int i = 0; i < optionTexts.size(); i++) {
        String optText = optionTexts.get(i);
        if (optText == null || optText.trim().isEmpty()) {
            continue;
        }
        Option opt = new Option();
        opt.setQuestion(question);
        opt.setText(optText.trim());
        boolean isCorrect = correctOptions != null && correctOptions.contains(i + 1);
        opt.setCorrect(isCorrect);
        question.getOptions().add(opt);
    }

    questionRepository.save(question);
    quizRepository.save(quiz);

    redirectAttributes.addFlashAttribute(
            "success",
            (questionId != null) ? "Question updated." : "Question added."
    );

    // ❌ OLD:
    // return "redirect:/admin/tests/" + quizId;

    // ✅ NEW – keep editing ON:
    return "redirect:/admin/tests/" + quizId + "?editing=true";
}


@PostMapping("/tests/{quizId}/questions/{questionId}/delete")
public String deleteQuestion(
        @PathVariable Long quizId,
        @PathVariable Long questionId,
        HttpSession session,
        RedirectAttributes redirectAttributes
) {
    // auth check
    User currentUser = authService.getCurrentUser(session);
    if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
        return "redirect:/?error=unauthorized";
    }

    // load quiz with its questions
    Quiz quiz = quizRepository.findWithQuestionsById(quizId);
    if (quiz == null || !quiz.getTeacherId().equals(currentUser.getId())) {
        redirectAttributes.addFlashAttribute("error", "Test not found or you are not allowed to edit it.");
        return "redirect:/admin/tests";
    }

    // find the question inside this quiz
    Question toDelete = null;
    if (quiz.getQuestions() != null) {
        for (Question q : quiz.getQuestions()) {
            if (q.getId().equals(questionId)) {
                toDelete = q;
                break;
            }
        }
    }

    if (toDelete == null) {
        redirectAttributes.addFlashAttribute("error", "Question not found.");
        // go back to this test and keep editing on
        return "redirect:/admin/tests/" + quizId + "?editing=true";
    }

    // remove from quiz and delete
    quiz.getQuestions().remove(toDelete);
    questionRepository.delete(toDelete);
    quizRepository.save(quiz);

    redirectAttributes.addFlashAttribute("success", "Question deleted.");
    // keep editing mode ON after delete
    return "redirect:/admin/tests/" + quizId + "?editing=true";
}

}
