---
name: react-composition-patterns
description: Ölçeklenebilen React composition pattern'leri. Boolean prop'ları çoğalmış component'leri refactor ederken, esnek ve yeniden kullanılabilir component'ler kurarken ya da component API'si tasarlarken kullan. Compound component, render prop, context provider veya component mimarisi içeren işlerde tetiklenir. React 19 API değişikliklerini (forwardRef yerine ref prop'u, useContext yerine use) içerir.
license: MIT
metadata:
  origin: vercel-labs/agent-skills
  source: https://github.com/vercel-labs/agent-skills/tree/main/skills/composition-patterns
  version: "1.0.0"
---

# React Composition Pattern'leri

Esnek ve bakımı kolay React component'leri kurmak için composition pattern'leri. Compound component kullanarak, state'i yukarı taşıyarak (lifting state) ve iç parçaları compose ederek boolean prop çoğalmasından kaçın. Bu pattern'ler, kod tabanı büyüdükçe hem insanların hem de AI agent'ların onunla çalışmasını kolaylaştırır.

## Ne zaman uygulanır

Bu kurallara şu durumlarda başvur:

- Çok sayıda boolean prop'u olan component'leri refactor ederken
- Yeniden kullanılabilir component'ler kurarken
- Esnek component API'leri tasarlarken
- Component mimarisini review ederken
- Compound component veya context provider ile çalışırken

## Önceliğe göre kural kategorileri

| Öncelik | Kategori                | Etki   | Prefix          |
| ------- | ----------------------- | ------ | --------------- |
| 1       | Component Mimarisi      | HIGH   | `architecture-` |
| 2       | State Management        | MEDIUM | `state-`        |
| 3       | Implementation Pattern'leri | MEDIUM | `patterns-` |
| 4       | React 19 API'leri       | MEDIUM | `react19-`      |

## Hızlı referans

### 1. Component Mimarisi (HIGH)

- [architecture-avoid-boolean-props](rules/architecture-avoid-boolean-props.md) - Davranışı özelleştirmek için boolean prop ekleme; composition kullan
- [architecture-compound-components](rules/architecture-compound-components.md) - Karmaşık component'leri paylaşılan context ile yapılandır

### 2. State Management (MEDIUM)

- [state-decouple-implementation](rules/state-decouple-implementation.md) - State'in nasıl yönetildiğini bilen tek yer provider'dır
- [state-context-interface](rules/state-context-interface.md) - Dependency injection için state, actions, meta içeren generic bir interface tanımla
- [state-lift-state](rules/state-lift-state.md) - Kardeş component'lerin erişebilmesi için state'i provider component'lerine taşı

### 3. Implementation Pattern'leri (MEDIUM)

- [patterns-explicit-variants](rules/patterns-explicit-variants.md) - Boolean modlar yerine açık (explicit) variant component'ler oluştur
- [patterns-children-over-render-props](rules/patterns-children-over-render-props.md) - Composition için renderX prop'ları yerine children kullan

### 4. React 19 API'leri (MEDIUM)

Bu proje React 19 kullanıyor, bu bölüm geçerlidir.

- [react19-no-forwardref](rules/react19-no-forwardref.md) - `forwardRef` kullanma; `useContext()` yerine `use()` kullan

## Nasıl kullanılır

Ayrıntılı açıklamalar ve kod örnekleri için ilgili kural dosyasını oku. Her kural dosyası şunları içerir:

- Neden önemli olduğuna dair kısa açıklama
- Açıklamalı yanlış kod örneği
- Açıklamalı doğru kod örneği
- Ek bağlam ve referanslar

Örneklerdeki `TextInput`, `onPress`, `onChangeText` gibi isimler React Native kökenlidir; web'de karşılıkları `input`, `onClick`, `onChange`'dir. Pattern aynıdır.

## Proje uyarlaması

`frontend-conventions` bu skill'deki örneklerden önce gelir:

- Kural dosyalarındaki `interface` örnekleri projede `type` ile yazılır.
- Compound component parçaları tek dosyada ayrı ayrı named export edilir (`ComposerFrame`, `ComposerInput`, `ComposerSubmit`; `frontend-conventions` bölüm 5'teki istisna). Kural dosyalarındaki `Composer.Input` gibi namespace kullanımı (`export const Composer = { Frame: ComposerFrame, ... }`) projede kullanılmaz: oxlint'in `react/only-export-components` kuralı (`.oxlintrc.json`, `allowConstantExport` sadece primitive sabitlere izin verir) bunu uyarı olarak verir ve projede lint uyarısı bırakılmaz. Default export yoktur.
- Context `createContext<ComposerContextValue | null>(null)` ile açılıyorsa `use(ComposerContext)` sonucu `null` olabilir; parçalar bunu doğrudan destructure etmez, null kontrolü yapan küçük bir hook kullanır:

```tsx
function useComposerContext(): ComposerContextValue {
  const context = use(ComposerContext)
  if (context === null) {
    throw new Error('Composer parts must be rendered inside ComposerProvider')
  }
  return context
}
```

- Kullanıcıya görünen metinler (`Send`, `Forward`, placeholder'lar) projede Türkçedir; stil Tailwind class'larıyla değil CSS Modules ile verilir.
