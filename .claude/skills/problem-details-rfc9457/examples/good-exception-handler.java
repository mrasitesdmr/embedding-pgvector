// ✅ İYİ — ResponseEntityExceptionHandler'ı extend eder, BaseException hiyerarşisini tek yerde map'ler,
// her hata errorCode taşır, 500'de iç mesajı gizler ve sadece burada loglar.
// Projede kullanılacak tam sınıf: ../templates/GlobalExceptionHandler.java

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BaseException.class)
    public ProblemDetail handleBaseException(BaseException exception, HttpServletRequest request) {
        HttpStatus status = exception.getHttpStatus();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setType(typeUri(exception.getErrorCode()));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", exception.getErrorCode());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception path={}", request.getRequestURI(), exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setType(typeUri("INTERNAL_ERROR"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("errorCode", "INTERNAL_ERROR");
        return problem;
    }
}
