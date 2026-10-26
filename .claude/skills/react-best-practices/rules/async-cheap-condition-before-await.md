---
title: Async Flag'lerden Önce Ucuz Koşulları Kontrol Et
impact: HIGH
impactDescription: senkron bir guard zaten başarısızken gereksiz async işten kaçınır
tags: async, await, feature-flags, short-circuit, conditional
---

## Async Flag'lerden Önce Ucuz Koşulları Kontrol Et

Bir branch bir flag ya da remote değer için `await` kullanıyor ve aynı zamanda **ucuz, senkron** bir koşul da gerektiriyorsa (local prop'lar, request metadata'sı, zaten yüklenmiş state), önce ucuz koşulu değerlendir. Aksi halde birleşik koşul asla true olamayacakken bile async çağrının bedelini ödersin.

Bu, `flag && cheapCondition` tarzı kontroller için [Await'i Gerektiği Yere Ertele](./async-defer-await.md) kuralının özel bir halidir.

**Yanlış:**

```typescript
const someFlag = await getFlag()

if (someFlag && someCondition) {
  // ...
}
```

**Doğru:**

```typescript
if (someCondition) {
  const someFlag = await getFlag()
  if (someFlag) {
    // ...
  }
}
```

Bu, `getFlag` network'e, bir feature-flag servisine ya da pahalı bir işe gidiyorsa önemlidir: `someCondition` false olduğunda onu atlamak, soğuk yoldaki bu maliyeti ortadan kaldırır.

`someCondition` pahalıysa, flag'e bağlıysa ya da side effect'leri sabit bir sırada çalıştırman gerekiyorsa orijinal sırayı koru.
