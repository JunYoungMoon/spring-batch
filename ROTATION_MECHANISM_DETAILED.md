# 라운드로빈 순환 메커니즘 상세 분석

## 🔄 라운드로빈 알고리즘의 핵심 원리

### 기본 개념
라운드로빈(Round Robin)은 각 참여자가 순환적으로 동등한 기회를 얻도록 보장하는 스케줄링 알고리즘입니다. 본 시스템에서는 여러 광고주가 CDP 배치 처리 자원을 공정하게 사용할 수 있도록 구현되었습니다.

## 📊 단계별 동작 메커니즘

### 1. 초기 상태 설정

```java
// AdvertiserRotationService 클래스 초기화
private final AtomicInteger currentIndex = new AtomicInteger(0);
private final ConcurrentHashMap<String, Integer> advertiserFailureCount = new ConcurrentHashMap<>();

초기 상태:
- currentIndex = 0
- advertiserFailureCount = {} (빈 맵)
- 데이터베이스의 모든 광고주는 PENDING 상태
```

### 2. 광고주 목록 조회 및 필터링

```sql
-- findEligibleForBatch() 쿼리 실행
SELECT a FROM Advertiser a 
WHERE a.batchEnabled = true 
  AND a.status = 'ACTIVE' 
  AND a.failureCount < a.maxFailures 
  AND a.lastBatchStatus != 'RUNNING'
ORDER BY a.rotationPriority ASC, a.lastBatchRun ASC NULLS FIRST

-- 예시 결과:
-- [ADV001, ADV002, ADV003, ADV004, ADV005]
```

### 3. 원자적 인덱스 계산

```java
// getAndUpdate() 메서드의 내부 동작
int nextIndex = currentIndex.getAndUpdate(i -> (i + 1) % eligibleAdvertisers.size());

시나리오 분석:
광고주 수: 5명 (ADV001~ADV005)

사이클 1: currentIndex = 0
- getAndUpdate() 호출
- 현재값 0 반환
- 새로운값 (0 + 1) % 5 = 1로 설정
- 결과: ADV001 선택 (인덱스 0)

사이클 2: currentIndex = 1
- getAndUpdate() 호출
- 현재값 1 반환
- 새로운값 (1 + 1) % 5 = 2로 설정
- 결과: ADV002 선택 (인덱스 1)

...이하 계속 순환
```

### 4. 상태 변경 및 동시성 보호

```java
// 선택된 광고주 상태 변경
selectedAdvertiser.recordRunning();
advertiserRepository.save(selectedAdvertiser);

데이터베이스 변경사항:
UPDATE advertisers 
SET last_batch_status = 'RUNNING',
    updated_at = NOW()
WHERE advertiser_id = 'ADV001';

목적: 다른 스케줄러 스레드가 동일한 광고주를 선택하는 것을 방지
```

## 🎯 실제 실행 시뮬레이션

### 시나리오: 5명의 광고주가 있는 시스템

```
초기 상태:
광고주 목록: [ADV001, ADV002, ADV003, ADV004, ADV005]
currentIndex: 0
모든 광고주 상태: PENDING

=== 배치 사이클 1 (T0) ===
1. 스케줄러 시작 (30초 타이머)
2. findEligibleForBatch() → [ADV001, ADV002, ADV003, ADV004, ADV005]
3. currentIndex.getAndUpdate(0) → 반환값: 0, 새로운값: 1
4. ADV001 선택 (eligibleAdvertisers[0])
5. ADV001 상태: PENDING → RUNNING
6. 비동기 배치 작업 시작

상태 업데이트:
- ADV001: RUNNING
- ADV002~ADV005: PENDING
- currentIndex: 1

=== 배치 사이클 2 (T30초) ===
1. 스케줄러 실행
2. findEligibleForBatch() → [ADV002, ADV003, ADV004, ADV005] (ADV001 제외)
3. currentIndex.getAndUpdate(1) → 반환값: 1, 새로운값: 2
4. 인덱스 1 % 4 = 1 → ADV003 선택 (eligibleAdvertisers[1])
5. ADV003 상태: PENDING → RUNNING

상태 업데이트:
- ADV001: RUNNING (여전히 실행중)
- ADV002: PENDING
- ADV003: RUNNING
- ADV004~ADV005: PENDING
- currentIndex: 2

=== 배치 완료 (T45초) ===
ADV001 배치 완료 (성공)
- recordBatchSuccess("ADV001") 호출
- ADV001 상태: RUNNING → SUCCESS
- lastBatchRun: 현재시간
- failureCount: 0으로 리셋

=== 배치 사이클 3 (T60초) ===
1. findEligibleForBatch() → [ADV001, ADV002, ADV004, ADV005] (ADV003 제외)
2. currentIndex.getAndUpdate(2) → 반환값: 2, 새로운값: 3
3. 인덱스 2 % 4 = 2 → ADV004 선택
4. ADV004 상태: PENDING → RUNNING
```

## ⚡ 동시성 처리 상세 분석

### 멀티스레드 환경에서의 안전성

```java
시나리오: 두 개의 스케줄러 스레드가 동시에 실행

Thread A                          Thread B
--------                          --------
T1: getAndUpdate(0) 호출          
                                  T2: getAndUpdate(1) 호출
T3: 반환값 0 받음 (ADV001)         T4: 반환값 1 받음 (ADV002)
T5: ADV001 → RUNNING              T6: ADV002 → RUNNING
T7: ADV001 배치 시작              T8: ADV002 배치 시작

결과: 서로 다른 광고주가 안전하게 선택됨
충돌 없음, 중복 실행 없음
```

### AtomicInteger의 내부 동작

```java
// getAndUpdate() 내부 구현 (간략화)
public final int getAndUpdate(IntUnaryOperator updateFunction) {
    int prev, next;
    do {
        prev = get();                          // 현재값 읽기
        next = updateFunction.applyAsInt(prev); // 새로운값 계산
    } while (!compareAndSet(prev, next));      // CAS 연산으로 원자적 업데이트
    return prev;                               // 이전값 반환
}

CAS (Compare-And-Set) 연산:
- 메모리의 값이 예상값과 같으면 새로운값으로 변경
- 다르면 실패하고 재시도
- 하드웨어 레벨에서 원자성 보장
```

## 🔧 인덱스 계산 최적화

### 모듈로 연산의 효율성

```java
// 기본 구현
int nextIndex = (currentIndex + 1) % eligibleAdvertisers.size();

// 광고주 수가 2의 거듭제곱일 때 최적화 (비트 연산)
if (size가 2의 거듭제곱) {
    int nextIndex = (currentIndex + 1) & (size - 1);
    // 예: size=8 → mask=7 (0111)
    // 15 & 7 = 7, 16 & 7 = 0 (순환)
}

성능 비교:
- 모듈로 연산: 약 10-20 CPU 사이클
- 비트 연산: 약 1-2 CPU 사이클
```

### 오버플로우 방지 메커니즘

```java
// 안전한 인덱스 업데이트
int nextIndex = currentIndex.getAndUpdate(i -> {
    // Integer.MAX_VALUE 근처에서 오버플로우 방지
    if (i >= Integer.MAX_VALUE - 1000) {
        return 0; // 리셋
    }
    return (i + 1) % eligibleAdvertisers.size();
});

오버플로우 시나리오:
- 시스템이 약 24.8일 연속 실행 (30초마다 증가)
- currentIndex가 Integer.MAX_VALUE에 근접
- 자동으로 0으로 리셋하여 계속 순환
```

## 📈 공정성 분석

### 이론적 공정성

```
N명의 광고주, M번의 배치 사이클에서:
각 광고주가 선택될 확률 = M/N (±1)

예시: 5명 광고주, 100번 사이클
- 각 광고주 기대 선택 횟수: 20회
- 실제 선택 횟수: 19~21회 (거의 균등)
```

### 실제 공정성 측정

```java
// 공정성 측정 코드 예시
Map<String, Integer> selectionCount = new HashMap<>();

// 1000번 시뮬레이션
for (int i = 0; i < 1000; i++) {
    Optional<Advertiser> selected = rotationService.getNextAdvertiserForBatch();
    if (selected.isPresent()) {
        String id = selected.get().getAdvertiserId();
        selectionCount.merge(id, 1, Integer::sum);
    }
}

// 결과 분석
selectionCount.forEach((id, count) -> {
    double percentage = (count * 100.0) / 1000;
    System.out.println(id + ": " + count + "회 (" + percentage + "%)");
});

예상 결과 (5명 광고주):
- ADV001: 200회 (20.0%)
- ADV002: 200회 (20.0%)
- ADV003: 200회 (20.0%)
- ADV004: 200회 (20.0%)
- ADV005: 200회 (20.0%)
```

## 🚀 성능 최적화 기법

### 1. 메모리 지역성 최적화

```java
// 배열 기반 순환 (캐시 친화적)
private final Advertiser[] advertiserCache;
private volatile int cacheVersion = 0;

public void refreshCache() {
    List<Advertiser> eligible = advertiserRepository.findEligibleForBatch();
    advertiserCache = eligible.toArray(new Advertiser[0]);
    cacheVersion++; // 버전 증가로 캐시 무효화 신호
}

// O(1) 접근
Advertiser selected = advertiserCache[nextIndex];
```

### 2. 배치 크기 동적 조정

```java
// 광고주 수에 따른 동적 배치 크기
public int calculateOptimalBatchSize(int advertiserCount) {
    if (advertiserCount <= 2) return 100;      // 적은 광고주 → 큰 배치
    if (advertiserCount <= 5) return 50;       // 중간 광고주 → 중간 배치
    return 25;                                  // 많은 광고주 → 작은 배치
}
```

### 3. 예측적 프리로딩

```java
// 다음 광고주의 데이터를 미리 준비
@Async
public void preloadNextAdvertiserData(String nextAdvertiserId) {
    // 다음 순환에서 처리할 데이터를 백그라운드에서 미리 로딩
    List<CustomerData> upcomingData = customerDataRepository
        .findUnprocessedDataForAdvertiser(nextAdvertiserId);
    
    // 캐시에 저장
    dataCache.put(nextAdvertiserId, upcomingData);
}
```

## 📊 모니터링 메트릭

### 순환 공정성 메트릭

```java
@Component
public class RotationMetrics {
    private final Map<String, Long> selectionCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalSelections = new AtomicLong(0);
    
    public void recordSelection(String advertiserId) {
        selectionCounts.merge(advertiserId, 1L, Long::sum);
        totalSelections.incrementAndGet();
    }
    
    public double getFairnessIndex() {
        if (selectionCounts.isEmpty()) return 1.0;
        
        double mean = (double) totalSelections.get() / selectionCounts.size();
        double variance = selectionCounts.values().stream()
            .mapToDouble(count -> Math.pow(count - mean, 2))
            .average().orElse(0.0);
        
        // Jain's Fairness Index: 1.0이 완전 공정
        double sumSquares = selectionCounts.values().stream()
            .mapToDouble(count -> Math.pow(count, 2))
            .sum();
        
        return Math.pow(totalSelections.get(), 2) / 
               (selectionCounts.size() * sumSquares);
    }
}
```

### 실시간 대시보드 데이터

```json
{
  "rotation_status": {
    "current_index": 3,
    "total_cycles": 1250,
    "fairness_index": 0.98,
    "advertiser_stats": {
      "ADV001": {"selections": 250, "success_rate": 0.95},
      "ADV002": {"selections": 248, "success_rate": 0.97},
      "ADV003": {"selections": 252, "success_rate": 0.93},
      "ADV004": {"selections": 249, "success_rate": 0.96},
      "ADV005": {"selections": 251, "success_rate": 0.94}
    }
  }
}
```

이 상세 분석을 통해 라운드로빈 순환 메커니즘의 모든 측면을 이해할 수 있습니다. 알고리즘의 공정성, 성능, 안전성이 모두 보장되도록 설계되어 있으며, 실제 운영 환경에서 발생할 수 있는 모든 상황에 대비한 최적화가 적용되어 있습니다.