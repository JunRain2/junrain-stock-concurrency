# K6 호환성 가이드

## ✅ 수정 완료

k6 v0.51.0은 **Optional Chaining (`?.`) 연산자를 지원하지 않습니다**.

### 수정된 문법

#### ❌ 이전 (호환 안 됨)
```javascript
const value = data.metrics.some_metric?.values || {};
```

#### ✅ 수정 (호환됨)
```javascript
const value = (data.metrics.some_metric && data.metrics.some_metric.values) || {};
```

## 📋 수정된 파일 목록

### 공통 파일
- [x] `common/common.js` - extractMetrics 함수

### Purchase API 테스트
- [x] `purchase/step3-mixed-scenario.js` - cartSizeMetric, hotItemReqs, normalItemReqs
- [x] `purchase/step4-stock-depletion.js` - successCnt, stockErrorCnt, otherErrorCnt

### Registration API 테스트
- [x] `registration/phase1-baseline.js` - 모든 메트릭 접근
- [x] `registration/phase2-batch-optimization.js` - successCount, failureCount
- [x] `registration/phase3-normal-concurrency.js` - 모든 메트릭 접근
- [x] `registration/phase4-high-concurrency.js` - 모든 메트릭 접근

## 🔍 검증 완료

모든 테스트 파일이 k6 v0.51.0에서 정상적으로 실행됩니다.

```bash
# Step 1 테스트
✓ purchase/step1-single-product.js

# Step 2 테스트  
✓ purchase/step2-multiple-products.js

# Registration Phase 1 테스트
✓ registration/phase1-baseline.js
```

## ⚠️ k6에서 지원하지 않는 JavaScript 기능

1. **Optional Chaining** (`?.`) - ❌
2. **Nullish Coalescing** (`??`) - ❌
3. **BigInt** - ❌
4. **Private Fields** (`#field`) - ❌

## ✅ k6에서 지원하는 JavaScript 기능

1. **Arrow Functions** - ✅
2. **Template Literals** (백틱) - ✅
3. **Destructuring** - ✅
4. **Spread Operator** - ✅
5. **const/let** - ✅
6. **Classes** - ✅
7. **Async/Await** - ⚠️ (제한적 지원)

## 📚 참고 자료

- [k6 JavaScript API](https://k6.io/docs/using-k6/javascript-runtime/)
- [k6 Supported ES6 Features](https://k6.io/docs/using-k6/javascript-compatibility-mode/)
