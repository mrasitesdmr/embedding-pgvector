// ❌ KÖTÜ — Map döndürüyor, iç detayları açıyor, yanlış status kodları

@RestControllerAdvice
public class SourceErrorHandler {                          // ResponseEntityExceptionHandler'ı extend etmiyor

    @ExceptionHandler(NotFoundException.class)         // her kategori için ayrı metot; BaseException tek yerde map'lenmiyor
    public Map<String, Object> handleNotFound(NotFoundException ex) {
        return Map.of(                                     // ProblemDetail değil, Map
            "error", ex.getMessage(),
            "status", 404
        );                                                 // body 404 diyor ama HTTP status 200 kalıyor
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
        return ResponseEntity.ok(                          // hata body'siyle 200 dönüyor
            Map.of("error", "Kaynak sınırı doldu"));       // errorCode yok; istemci Türkçe metni parse etmek zorunda
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(500).body(Map.of(
            "error", ex.getMessage(),                      // ham exception mesajını açıyor (güvenlik riski)
            "stackTrace", Arrays.toString(ex.getStackTrace())  // stack trace ASLA dışarı verilmez
        ));                                                // ve hiç loglanmıyor
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
            .body("Validation failed: " + ex.getMessage());  // yapısız string, alan bazlı violations yok
    }
}
