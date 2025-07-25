# 엣지 케이스 및 장애 시나리오 처리 가이드

## 🚨 주요 엣지 케이스 분석

### 1. 모든 광고주 비활성화 시나리오

#### 상황 설명
모든 광고주가 연속 3회 실패하여 `batchEnabled = false` 상태가 된 경우

```sql
-- 현재 상태 확인
SELECT advertiser_id, failure_count, batch_enabled, last_batch_status 
FROM advertisers;

-- 결과 예시:
-- ADV001 | 3 | false | FAILED
-- ADV002 | 3 | false | FAILED  
-- ADV003 | 3 | false | FAILED
```

#### 시스템 동작
```java
// AdvertiserRotationService.getNextAdvertiserForBatch()
List<Advertiser> eligibleAdvertisers = advertiserRepository.findEligibleForBatch();
// 결과: 빈 리스트 []

if (eligibleAdvertisers.isEmpty()) {
    log.warn("배치 처리 가능한 광고주가 없습니다");
    return Optional.empty(); // 현재 사이클 건너뜀
}
```

#### 복구 방법
```bash
# 1. 전체 광고주 상태 확인
curl http://localhost:8080/api/admin/batch/status

# 2. 각 광고주별 수동 복구
curl -X POST http://localhost:8080/api/admin/batch/reset/ADV001
curl -X POST http://localhost:8080/api/admin/batch/reset/ADV002
curl -X POST http://localhost:8080/api/admin/batch/reset/ADV003

# 3. 복구 확인
curl http://localhost:8080/api/admin/batch/advertisers/eligible
```

### 2. 동시성 충돌 시나리오

#### 상황 설명
여러 스케줄러 인스턴스가 동시에 실행되어 같은 광고주를 선택하려는 경우

```
Thread A: getNextAdvertiserForBatch() → ADV001 선택 시도
Thread B: getNextAdvertiserForBatch() → ADV001 선택 시도 (동시)
```

#### 보호 메커니즘
```java
// 1. 원자적 인덱스 업데이트
int nextIndex = currentIndex.getAndUpdate(i -> (i + 1) % eligibleAdvertisers.size());
// Thread A: 0 → 1 반환
// Thread B: 1 → 2 반환 (다른 인덱스)

// 2. DB 트랜잭션 보호
@Transactional
public Optional<Advertiser> getNextAdvertiserForBatch() {
    // RUNNING 상태 체크로 중복 실행 방지
    selectedAdvertiser.recordRunning();
    advertiserRepository.save(selectedAdvertiser);
}
```

#### 실제 동작 시뮬레이션
```
T0: [ADV001:PENDING, ADV002:PENDING, ADV003:PENDING]
    currentIndex = 0

T1: Thread A: getAndUpdate(0) → 반환값 0, currentIndex = 1
    Thread B: getAndUpdate(1) → 반환값 1, currentIndex = 2

T2: Thread A: ADV001 선택 → RUNNING 상태로 변경
    Thread B: ADV002 선택 → RUNNING 상태로 변경

결과: 서로 다른 광고주가 안전하게 선택됨
```

### 3. 시스템 재시작 중 배치 실행 시나리오

#### 상황 설명
광고주의 배치 작업이 실행 중인 상태에서 시스템이 재시작되는 경우

```
T0: ADV001 배치 시작 → lastBatchStatus = 'RUNNING'
T1: 시스템 재시작 발생
T2: 시스템 재시작 완료, 하지만 ADV001은 여전히 'RUNNING' 상태
```

#### 스테일 작업 감지 및 정리
```java
// BatchJobOrchestrator.healthCheckAndCleanup()
@Scheduled(fixedDelay = 300000) // 5분마다 실행
public void healthCheckAndCleanup() {
    List<Advertiser> runningJobs = rotationService.getRunningBatchJobs();
    LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
    
    for (Advertiser advertiser : runningJobs) {
        if (advertiser.getLastBatchRun() != null && 
            advertiser.getLastBatchRun().isBefore(cutoffTime)) {
            
            log.warn("스테일 작업 감지: {} (30분 이상 RUNNING)", 
                     advertiser.getAdvertiserId());
            
            // 강제로 실패 처리
            rotationService.recordBatchFailure(
                advertiser.getAdvertiserId(), 
                new RuntimeException("스테일 작업 - 시스템 재시작으로 인한 정리")
            );
        }
    }
}
```

### 4. 데이터베이스 연결 실패 시나리오

#### 상황 설명
배치 처리 중 DB 연결이 끊어지는 경우

```java
// 예외 발생 지점들
try {
    List<Advertiser> eligibleAdvertisers = advertiserRepository.findEligibleForBatch();
} catch (DataAccessException e) {
    log.error("DB 연결 실패로 광고주 조회 불가", e);
    return Optional.empty(); // 현재 사이클 건너뜀
}
```

#### 복구 전략
```java
// Spring의 @Retryable 어노테이션 활용 (의존성 추가 필요)
@Retryable(value = {DataAccessException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
public Optional<Advertiser> getNextAdvertiserForBatch() {
    // DB 작업 수행
}

// 또는 수동 재시도 로직
public Optional<Advertiser> getNextAdvertiserForBatchWithRetry() {
    int maxRetries = 3;
    for (int i = 0; i < maxRetries; i++) {
        try {
            return getNextAdvertiserForBatch();
        } catch (DataAccessException e) {
            log.warn("DB 연결 실패 (시도 {}/{})", i + 1, maxRetries, e);
            if (i == maxRetries - 1) {
                log.error("DB 연결 재시도 한도 초과", e);
                return Optional.empty();
            }
            Thread.sleep(1000 * (i + 1)); // 지수 백오프
        }
    }
    return Optional.empty();
}
```

### 5. 메모리 부족 시나리오

#### 상황 설명
대용량 배치 처리 중 OutOfMemoryError 발생

```java
// CustomerDataProcessor에서 대용량 데이터 처리 시
@Override
public CustomerData process(CustomerData item) throws Exception {
    try {
        Map<String, Object> aiFeatures = extractAdvertiserSpecificFeatures(item);
        // 메모리 집약적 작업
    } catch (OutOfMemoryError e) {
        log.error("메모리 부족으로 처리 실패: customer {}", 
                 item.getCustomer().getCustomerId(), e);
        
        // 현재 아이템은 건너뛰고 계속 진행
        return null; // ItemProcessor에서 null 반환 시 해당 아이템 스킵
    }
}
```

#### 예방 및 대응
```yaml
# application.yml - JVM 메모리 설정 및 배치 크기 조정
spring:
  batch:
    chunk-size: 25  # 기본 50에서 25로 줄여서 메모리 사용량 감소

# JVM 옵션 설정 예시
# -Xmx2G -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/logs/
```

### 6. 네트워크 타임아웃 시나리오

#### 상황 설명
외부 API 호출이나 원격 데이터베이스 접근 시 네트워크 지연

```java
// AIFeatureExtractorService에서 외부 API 호출 시
public Map<String, Object> extractAdvancedFeatures(CustomerData data) {
    try {
        // 외부 AI 서비스 호출 (네트워크 지연 가능)
        String aiResponse = callExternalAIService(data);
        return parseAIResponse(aiResponse);
    } catch (SocketTimeoutException e) {
        log.warn("외부 AI 서비스 타임아웃, 기본값 사용: customer {}", 
                data.getCustomer().getCustomerId());
        
        // 타임아웃 시 기본 특성값 반환
        return getDefaultFeatures(data);
    }
}
```

### 7. 순환 인덱스 오버플로우 시나리오

#### 상황 설명
매우 오랜 시간 실행되어 AtomicInteger가 최대값에 도달하는 경우

```java
// 안전한 순환 인덱스 계산
int nextIndex = currentIndex.getAndUpdate(i -> {
    // Integer.MAX_VALUE에 가까워지면 0으로 리셋
    if (i >= Integer.MAX_VALUE - 1000) {
        return 0;
    }
    return (i + 1) % eligibleAdvertisers.size();
});
```

## 🔧 실시간 문제 진단 도구

### 1. 시스템 헬스 체크 스크립트

```bash
#!/bin/bash
# health_check.sh

echo "=== CDP 배치 시스템 헬스 체크 ==="

# 전체 상태 확인
STATUS=$(curl -s http://localhost:8080/api/admin/batch/status)
echo "시스템 상태: $STATUS"

# 처리 가능한 광고주 수 확인
ELIGIBLE_COUNT=$(echo $STATUS | jq '.eligibleAdvertisers')
if [ "$ELIGIBLE_COUNT" -eq 0 ]; then
    echo "⚠️  경고: 처리 가능한 광고주가 없습니다!"
    
    # 실패한 광고주 복구 시도
    for adv in ADV001 ADV002 ADV003 ADV004 ADV005; do
        echo "광고주 $adv 복구 시도..."
        curl -s -X POST http://localhost:8080/api/admin/batch/reset/$adv
    done
fi

# 실행 중인 작업이 5분 이상인지 확인
RUNNING_JOBS=$(curl -s http://localhost:8080/api/admin/batch/advertisers/running)
echo "실행 중인 작업: $RUNNING_JOBS"

echo "=== 헬스 체크 완료 ==="
```

### 2. 로그 분석 스크립트

```bash
#!/bin/bash
# log_analysis.sh

LOG_FILE="application.log"

echo "=== 최근 1시간 배치 처리 상황 ==="

# 성공한 배치 수 계산
SUCCESS_COUNT=$(grep -c "배치 작업 성공 기록 완료" $LOG_FILE)
echo "성공한 배치: $SUCCESS_COUNT"

# 실패한 배치 수 계산
FAILURE_COUNT=$(grep -c "배치 작업 실패 기록" $LOG_FILE)
echo "실패한 배치: $FAILURE_COUNT"

# 타임아웃 발생 수
TIMEOUT_COUNT=$(grep -c "배치 작업 타임아웃" $LOG_FILE)
echo "타임아웃 발생: $TIMEOUT_COUNT"

# 비활성화된 광고주
DISABLED_ADVERTISERS=$(grep "최대 실패 횟수를 초과하여 자동 비활성화" $LOG_FILE | tail -5)
if [ ! -z "$DISABLED_ADVERTISERS" ]; then
    echo "⚠️  최근 비활성화된 광고주:"
    echo "$DISABLED_ADVERTISERS"
fi

# 성공률 계산
TOTAL=$((SUCCESS_COUNT + FAILURE_COUNT))
if [ $TOTAL -gt 0 ]; then
    SUCCESS_RATE=$((SUCCESS_COUNT * 100 / TOTAL))
    echo "전체 성공률: $SUCCESS_RATE%"
fi
```

### 3. 자동 복구 스크립트

```bash
#!/bin/bash
# auto_recovery.sh

echo "=== 자동 복구 시작 ==="

# 1. 시스템 상태 확인
STATUS=$(curl -s http://localhost:8080/api/admin/batch/status)
ELIGIBLE_COUNT=$(echo $STATUS | jq '.eligibleAdvertisers')

if [ "$ELIGIBLE_COUNT" -lt 2 ]; then
    echo "광고주 수가 부족합니다. 자동 복구를 시작합니다..."
    
    # 2. 모든 광고주 상태 확인 및 복구
    for adv in ADV001 ADV002 ADV003 ADV004 ADV005; do
        ADV_STATUS=$(curl -s http://localhost:8080/api/admin/batch/advertiser/$adv)
        BATCH_ENABLED=$(echo $ADV_STATUS | jq '.batchEnabled')
        
        if [ "$BATCH_ENABLED" = "false" ]; then
            echo "광고주 $adv 복구 중..."
            curl -s -X POST http://localhost:8080/api/admin/batch/reset/$adv
            
            # 복구 후 테스트 실행
            echo "복구된 광고주 $adv 테스트 실행..."
            curl -s -X POST http://localhost:8080/api/admin/batch/trigger/$adv
            
            sleep 10  # 테스트 완료 대기
        fi
    done
    
    # 3. 복구 결과 확인
    NEW_STATUS=$(curl -s http://localhost:8080/api/admin/batch/status)
    NEW_ELIGIBLE_COUNT=$(echo $NEW_STATUS | jq '.eligibleAdvertisers')
    echo "복구 완료. 처리 가능한 광고주: $NEW_ELIGIBLE_COUNT"
fi

echo "=== 자동 복구 완료 ==="
```

## 📋 운영 체크리스트

### 일일 점검 항목
- [ ] 전체 시스템 상태 확인 (`/api/admin/batch/status`)
- [ ] 비활성화된 광고주 없는지 확인
- [ ] 지난 24시간 배치 성공률 확인 (목표: 95% 이상)
- [ ] 타임아웃 발생 빈도 확인 (목표: 1% 미만)
- [ ] 로그에서 에러 패턴 분석

### 주간 점검 항목
- [ ] 데이터베이스 성능 메트릭 확인
- [ ] 배치 처리 시간 트렌드 분석
- [ ] 메모리 사용량 모니터링
- [ ] 자동 복구 스크립트 실행 테스트

### 월간 점검 항목
- [ ] 광고주별 처리량 공정성 분석
- [ ] 시스템 리소스 사용량 최적화 검토
- [ ] 장애 복구 시나리오 훈련
- [ ] 모니터링 임계값 조정

이 문서는 시스템 운영 중 발생할 수 있는 모든 주요 엣지 케이스에 대한 대응 방안을 제공합니다. 각 시나리오별로 명확한 진단 방법과 복구 절차가 정의되어 있어, 운영팀이 신속하고 정확하게 문제를 해결할 수 있습니다.