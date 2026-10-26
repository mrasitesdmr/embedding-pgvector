#!/usr/bin/env bash
# Hangi testin istenmeyen dosya/state bıraktığını bulan bisection script'i.
# frontend/ dizininden çalıştırılır; her test dosyasını tek başına `npx vitest run` ile koşar.
# Kullanım: find-polluter.sh <kontrol-edilecek-dosya-veya-dizin> <test-pattern>
# Örnek:    find-polluter.sh '.git' 'src/**/*.test.ts'

set -e

if [ $# -ne 2 ]; then
  echo "Kullanım: $0 <kontrol-edilecek-dosya> <test-pattern>"
  echo "Örnek:    $0 '.git' 'src/**/*.test.ts'"
  exit 1
fi

POLLUTION_CHECK="$1"
TEST_PATTERN="$2"
TEST_COMMAND="${TEST_COMMAND:-npx vitest run}"

echo "🔍 Şunu oluşturan test aranıyor: $POLLUTION_CHECK"
echo "Test pattern: $TEST_PATTERN"
echo "Test komutu:  $TEST_COMMAND"
echo ""

# find ./ önekli path üretir; pattern ./ ile veya ./ olmadan yazılabilir.
TEST_PATTERN="${TEST_PATTERN#./}"
# find -path '**/' ifadesini sıfır dizin seviyesiyle eşleştiremez (src/**/*.test.ts,
# src/top.test.ts'i atlar); bu yüzden '**/' çıkarılmış pattern de denenir.
TEST_FILES=$(find . -path ./node_modules -prune -o \( -path "./$TEST_PATTERN" -o -path "./${TEST_PATTERN//\*\*\//}" \) -print | sort -u)
if [ -z "$TEST_FILES" ]; then
  TOTAL=0
else
  TOTAL=$(printf '%s\n' "$TEST_FILES" | wc -l | tr -d ' ')
fi

echo "$TOTAL test dosyası bulundu"
echo ""

COUNT=0
for TEST_FILE in $TEST_FILES; do
  COUNT=$((COUNT + 1))

  if [ -e "$POLLUTION_CHECK" ]; then
    echo "⚠️  Kirlilik $COUNT/$TOTAL numaralı testten önce zaten var"
    echo "   Atlanıyor: $TEST_FILE"
    continue
  fi

  echo "[$COUNT/$TOTAL] Test ediliyor: $TEST_FILE"

  $TEST_COMMAND "$TEST_FILE" > /dev/null 2>&1 || true

  if [ -e "$POLLUTION_CHECK" ]; then
    echo ""
    echo "🎯 KİRLETEN TEST BULUNDU!"
    echo "   Test:        $TEST_FILE"
    echo "   Oluşturulan: $POLLUTION_CHECK"
    echo ""
    echo "Kirlilik detayı:"
    ls -la "$POLLUTION_CHECK"
    echo ""
    echo "İncelemek için:"
    echo "  $TEST_COMMAND $TEST_FILE    # Sadece bu testi çalıştır"
    echo "  cat $TEST_FILE              # Test kodunu incele"
    exit 1
  fi
done

echo ""
echo "✅ Kirleten test bulunamadı - tüm testler temiz!"
exit 0
