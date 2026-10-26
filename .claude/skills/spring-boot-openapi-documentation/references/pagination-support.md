# Pagination Dokümantasyonu

Proje standardı `backend-conventions` bölüm 6'dadır: query parametreleri `page` (0'dan) ve `size` (varsayılan 20, en fazla 100); `sort` yoktur, sıra repository sorgusunda sabittir; service `Page<XResponse>` döner, controller `PageResponse.from(...)` ile sarar. `Page<T>`, `Slice<T>` veya özel bir `PagedResponse` doğrudan döndürülmez.

## Standart sayfalı endpoint

```java
@Operation(operationId = "listSources", summary = "List sources of a workspace")
@GetMapping
public PageResponse<SourceResponse> listSources(
        @PathVariable long workspaceId,
        @Parameter(description = "Zero-based page index")
        @RequestParam(name = "page", defaultValue = "0")
        @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
        @Parameter(description = "Page size")
        @RequestParam(name = "size", defaultValue = "20")
        @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
        @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
    return PageResponse.from(sourceService.listSources(workspaceId, page, size));
}
```

- `Pageable` + `@PageableDefault` kullanılmaz: Spring'in çözümleyicisi sınırı aşan `size`'ı sessizce kırpar, geçersiz `page`'i düzeltir. Projede geçersiz değer `VALIDATION_FAILED` + `violations[].field` döner (TASK-002 D17). Parametreler `@RequestParam` olduğu için springdoc onları ayrı query parametreleri olarak, `defaultValue` ve `@Min`/`@Max` sınırlarıyla belgeler; `@ParameterObject` gerekmez.
- Service `PageRequest.of(page, size)` (sırasız) ile repository'nin sabit sıralı sorgusunu çağırır.

## PageResponse Şeması

```java
@Schema(description = "One page of results")
public record PageResponse<T>(
        @Schema(description = "Items on this page")
        List<T> content,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total number of items", example = "57")
        long totalElements,

        @Schema(description = "Total number of pages", example = "3")
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
```

Generic `PageResponse<SourceResponse>` için SpringDoc her içerik tipine ayrı bir şema (`PageResponseSourceResponse`) üretir; `docs/api/openapi.yaml`'daki şema adıyla karşılaştırırken bunu dikkate al.

## Sıralama Whitelist'i

İstemcinin rastgele kolona göre sıralamasına izin verilmez. Service, `Pageable`'ı izin verilen alanlarla sınırlar:

```java
private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");

public Page<SourceResponse> listSources(long workspaceId, Pageable pageable) {
    pageable.getSort().forEach(order -> {
        if (!SORTABLE_FIELDS.contains(order.getProperty())) {
            throw new UnprocessableContentException("INVALID_SORT_FIELD", "Sorting by " + order.getProperty() + " is not allowed");
        }
    });
    return sourceRepository.findByWorkspaceId(workspaceId, pageable).map(SourceResponse::from);
}
```

İzin verilen alanlar `@Operation(description = ...)` içinde listelenir. `sort` değeri entity alan adına gider; API'de adı farklı olan alanlar (entity `id` ↔ JSON `sourceId`) istemciye sıralama alanı olarak açılmaz ya da service'te açıkça eşlenir. Yeni `errorCode` (`INVALID_SORT_FIELD`) frontend'deki `errorMessages.ts`'e de eklenir.

## Filtre + Pagination

Projede henüz filtreli liste yok. Eklenirse filtre parametreleri sayfa parametrelerinin yanında `@RequestParam(required = false)` olur ve sözleşmeye planner ekler:

```java
@GetMapping("/api/workspaces/{workspaceId}/search-events")
public PageResponse<SearchEventResponse> listSearchEvents(
        @PathVariable long workspaceId,
        @Parameter(description = "Only events without results", example = "true")
        @RequestParam(name = "noResults", required = false) @Nullable Boolean noResults,
        @Parameter(description = "Zero-based page index")
        @RequestParam(name = "page", defaultValue = "0")
        @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
        @Parameter(description = "Page size")
        @RequestParam(name = "size", defaultValue = "20")
        @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
        @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
    return PageResponse.from(searchEventService.listSearchEvents(workspaceId, noResults, page, size));
}
```

## Toplam Sayı Gerekmeyen Listeler (Slice)

Sonsuz kaydırma gibi toplam sayının gerekmediği durumlarda repository `Slice<T>` döner (count sorgusu çalışmaz). Response şeması ayrı bir record'dur:

```java
public record SliceResponse<T>(List<T> content, int page, int size, boolean hasNext) {

    public static <T> SliceResponse<T> from(Slice<T> slice) {
        return new SliceResponse<>(slice.getContent(), slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
```

`SliceResponse` `backend-conventions` bölüm 6'da tanımlı proje tipidir; başka bir sayfalama tipi uydurulmaz.

## Best Practice'ler

1. Varsayılanlar: `page=0`, `size=20`; üst sınır 100
2. `sort` yok; sıra sabit ve endpoint `description`'ında yazılı. İleride sıralama eklenirse alanlar whitelist'lenir (sözleşme değişikliği)
3. Büyük tablolarda `OFFSET` yerine keyset pagination (`postgres-patterns`)
4. Pagination parametreleri request body'de (POST) değil query string'de taşınır
