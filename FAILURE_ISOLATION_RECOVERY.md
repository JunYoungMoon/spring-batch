# 장애 격리 및 복구 시나리오 가이드

## 🛡️ 장애 격리 메커니즘

### 격리 원칙
본 시스템의 핵심 설계 원칙은 "한 광고주의 실패가 다른 광고주에게 절대 영향을 주지 않는다"입니다. 이를 위해 다층적 격리 메커니즘을 구현했습니다.

## 📊 장애 격리 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        격리 계층 구조                              │
├─────────────────────────────────────────────────────────────────┤
│ 1층: 프로세스 격리    │ 각 광고주별 독립적인 배치 작업 실행        │
│ 2층: 상태 격리        │ 개별 광고주의 성공/실패 상태 독립 관리     │
│ 3층: 데이터 격리      │ 광고주별 데이터 필터링 및 처리           │
│ 4층: 시간 격리        │ 타임아웃으로 무한 대기 방지              │
│ 5층: 자원 격리        │ 메모리/CPU 사용량 제한                  │
└─────────────────────────────────────────────────────────────────┘
```

## 🚨 장애 시나리오별 대응 전략

### 시나리오 1: 단일 광고주 배치 실패

#### 상황 설명
```
초기 상태:
- ADV001: PENDING (정상)
- ADV002: PENDING (정상)  
- ADV003: PENDING (정상)

T0: ADV001 배치 시작 → RUNNING
T1: ADV001 배치 실패 (데이터 오류)
```

#### 격리 및 복구 과정

```java
// 1단계: 즉시 실패 감지 및 기록
try {
    executeBatchJob(advertiser); // ADV001 실행
} catch (Exception e) {
    // 실패 즉시 격리 처리
    rotationService.recordBatchFailure("ADV001", e);
}

// 2단계: 상태 변경 (다른 광고주와 격리)
public void recordBatchFailure(String advertiserId, Exception exception) {
    advertiser.recordFailure(); // ADV001만 FAILED 상태로 변경
    advertiser.setFailureCount(failureCount + 1); // 실패 카운터 증가
    
    // 다른 광고주는 영향받지 않음
    // ADV002, ADV003은 여전히 PENDING 상태 유지
}

// 3단계: 다음 순환에서 자동 제외
List<Advertiser> eligible = findEligibleForBatch();
// 결과: [ADV002, ADV003] (ADV001 제외됨)
```

#### 시간별 상태 변화
```
T0: [ADV001:PENDING, ADV002:PENDING, ADV003:PENDING]
T1: [ADV001:RUNNING, ADV002:PENDING, ADV003:PENDING]
T2: [ADV001:FAILED,  ADV002:PENDING, ADV003:PENDING]  ← 격리 완료
T3: 다음 사이클에서 ADV002 정상 선택 및 실행
T4: [ADV001:FAILED,  ADV002:RUNNING, ADV003:PENDING] ← 다른 광고주 정상 처리
```

### 시나리오 2: 다중 광고주 동시 실패

#### 상황 설명
```
네트워크 장애로 인해 동시에 여러 광고주의 배치가 실패하는 경우

T0: ADV001, ADV002 동시 배치 시작
T1: 네트워크 장애 발생
T2: 두 배치 모두 실패
```

#### 독립적 격리 처리

```java
// 각 광고주별로 독립적인 실패 처리
CompletableFuture<Void> batch1 = CompletableFuture.runAsync(() -> {
    try {
        executeBatchJob(adv001);
    } catch (Exception e) {
        rotationService.recordBatchFailure("ADV001", e); // ADV001만 격리
    }
});

CompletableFuture<Void> batch2 = CompletableFuture.runAsync(() -> {
    try {
        executeBatchJob(adv002);
    } catch (Exception e) {
        rotationService.recordBatchFailure("ADV002", e); // ADV002만 격리
    }
});

// 각각 독립적으로 타임아웃 및 실패 처리
```

#### 격리 결과
```
장애 전: [ADV001:RUNNING, ADV002:RUNNING, ADV003:PENDING, ADV004:PENDING]
장애 후: [ADV001:FAILED,  ADV002:FAILED,  ADV003:PENDING, ADV004:PENDING]

다음 순환 대상: [ADV003, ADV004] ← 정상 광고주만 계속 처리
```

### 시나리오 3: 타임아웃으로 인한 장애

#### 상황 설명
```
ADV001의 배치 작업이 5분 타임아웃을 초과하는 경우
```

#### 타임아웃 격리 메커니즘

```java
// 타임아웃 설정 및 강제 종료
try {
    batchExecution.get(5, TimeUnit.MINUTES);
} catch (TimeoutException e) {
    log.error("광고주 {} 배치 작업 타임아웃", advertiserId);
    
    // 1. 즉시 실패 처리 (다른 광고주 영향 없음)
    rotationService.recordBatchFailure(advertiserId, e);
    
    // 2. 강제 종료로 시스템 리소스 보호
    batchExecution.cancel(true);
    
    // 3. 다음 순환은 정상 진행
}
```

#### 타임아웃 격리 효과
```
T0: ADV001 배치 시작 (5분 타임아웃 설정)
T1~T4: 정상 처리 시간
T5: 5분 타임아웃 도달
    ├─ ADV001 강제 종료 및 FAILED 상태 변경
    ├─ 시스템 리소스 즉시 회수
    └─ 다른 광고주 처리에는 전혀 영향 없음

T6: 다음 사이클에서 ADV002 정상 선택
```

## 🔧 자동 복구 메커니즘

### 1. 점진적 복구 전략

```java
// 실패 횟수에 따른 점진적 복구
public class Advertiser {
    private Integer failureCount = 0;      // 현재 실패 횟수
    private Integer maxFailures = 3;       // 최대 허용 실패
    
    public boolean isEligibleForBatch() {
        return batchEnabled && 
               status == AdvertiserStatus.ACTIVE && 
               failureCount < maxFailures &&          // ← 핵심 조건
               lastBatchStatus != BatchStatus.RUNNING;
    }
    
    public void recordSuccess() {
        this.failureCount = 0;  // 성공 시 실패 카운터 완전 리셋
        this.lastBatchStatus = BatchStatus.SUCCESS;
    }
    
    public void recordFailure() {
        this.failureCount++;
        if (this.failureCount >= this.maxFailures) {
            this.batchEnabled = false;  // 3회 실패 시 완전 비활성화
        }
    }
}
```

### 2. 실패 패턴별 복구 시나리오

#### 패턴 A: 일시적 실패 (1-2회)
```
상황: 네트워크 지연, 일시적 DB 연결 문제
복구: 자동 (다음 순환에서 재시도)

T0: ADV001 실패 (failureCount: 0→1)
T1: 다음 순환에서 ADV001 여전히 eligible
T2: ADV001 재시도 → 성공 (failureCount: 1→0)
```

#### 패턴 B: 지속적 실패 (3회)
```
상황: 광고주별 데이터 문제, 설정 오류
복구: 수동 개입 필요

T0: ADV001 1차 실패 (failureCount: 0→1)
T1: ADV001 2차 실패 (failureCount: 1→2)
T2: ADV001 3차 실패 (failureCount: 2→3, batchEnabled: true→false)
T3: ADV001 완전 비활성화, 수동 복구 대기
```

### 3. 스테일 작업 자동 정리

```java
// 30분 이상 RUNNING 상태인 작업 자동 정리
@Scheduled(fixedDelay = 300000) // 5분마다 실행
public void healthCheckAndCleanup() {
    List<Advertiser> runningJobs = rotationService.getRunningBatchJobs();
    LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
    
    for (Advertiser advertiser : runningJobs) {
        if (advertiser.getLastBatchRun() != null && 
            advertiser.getLastBatchRun().isBefore(cutoffTime)) {
            
            // 스테일 작업 감지 및 자동 복구
            log.warn("스테일 작업 감지: {} (30분 이상 RUNNING)", 
                     advertiser.getAdvertiserId());
            
            rotationService.recordBatchFailure(
                advertiser.getAdvertiserId(), 
                new RuntimeException("스테일 작업 - 시스템 재시작으로 인한 정리")
            );
        }
    }
}
```

## 📋 복구 시나리오 매뉴얼

### 복구 시나리오 1: 단일 광고주 수동 복구

```bash
#!/bin/bash
# single_advertiser_recovery.sh

ADVERTISER_ID="ADV001"

echo "=== 광고주 $ADVERTISER_ID 복구 시작 ==="

# 1. 현재 상태 확인
echo "1. 현재 상태 확인"
curl -s http://localhost:8080/api/admin/batch/advertiser/$ADVERTISER_ID | jq '.'

# 2. 실패 카운터 리셋
echo "2. 실패 카운터 리셋"
curl -s -X POST http://localhost:8080/api/admin/batch/reset/$ADVERTISER_ID

# 3. 테스트 배치 실행
echo "3. 테스트 배치 실행"
curl -s -X POST http://localhost:8080/api/admin/batch/trigger/$ADVERTISER_ID

# 4. 복구 결과 확인
echo "4. 복구 결과 확인 (10초 후)"
sleep 10
curl -s http://localhost:8080/api/admin/batch/advertiser/$ADVERTISER_ID | jq '.lastBatchStatus'

echo "=== 복구 완료 ==="
```

### 복구 시나리오 2: 전체 시스템 복구

```bash
#!/bin/bash
# full_system_recovery.sh

echo "=== 전체 시스템 복구 시작 ==="

# 1. 시스템 상태 진단
echo "1. 시스템 상태 진단"
STATUS=$(curl -s http://localhost:8080/api/admin/batch/status)
ELIGIBLE_COUNT=$(echo $STATUS | jq '.eligibleAdvertisers')
echo "처리 가능한 광고주 수: $ELIGIBLE_COUNT"

if [ "$ELIGIBLE_COUNT" -lt 2 ]; then
    echo "⚠️ 긴급 복구 필요: 처리 가능한 광고주가 부족"
    
    # 2. 모든 광고주 목록 조회 및 복구
    for adv in ADV001 ADV002 ADV003 ADV004 ADV005; do
        echo "광고주 $adv 상태 확인 중..."
        
        ADV_STATUS=$(curl -s http://localhost:8080/api/admin/batch/advertiser/$adv)
        BATCH_ENABLED=$(echo $ADV_STATUS | jq '.batchEnabled')
        FAILURE_COUNT=$(echo $ADV_STATUS | jq '.failureCount')
        
        if [ "$BATCH_ENABLED" = "false" ] || [ "$FAILURE_COUNT" -gt 0 ]; then
            echo "광고주 $adv 복구 중... (실패횟수: $FAILURE_COUNT)"
            curl -s -X POST http://localhost:8080/api/admin/batch/reset/$adv
            
            # 복구 후 즉시 테스트
            echo "복구 테스트 실행..."
            curl -s -X POST http://localhost:8080/api/admin/batch/trigger/$adv &
            
            sleep 2  # 다음 광고주와 간격 두기
        else
            echo "광고주 $adv 정상 상태"
        fi
    done
    
    # 3. 복구 결과 최종 확인
    echo "3. 복구 결과 확인 (30초 후)"
    sleep 30
    
    NEW_STATUS=$(curl -s http://localhost:8080/api/admin/batch/status)
    NEW_ELIGIBLE_COUNT=$(echo $NEW_STATUS | jq '.eligibleAdvertisers')
    echo "복구 후 처리 가능한 광고주 수: $NEW_ELIGIBLE_COUNT"
    
    if [ "$NEW_ELIGIBLE_COUNT" -ge 3 ]; then
        echo "✅ 시스템 복구 성공"
    else
        echo "❌ 추가 수동 조치 필요"
    fi
else
    echo "✅ 시스템 정상 상태"
fi

echo "=== 전체 시스템 복구 완료 ==="
```

### 복구 시나리오 3: 데이터 일관성 복구

```sql
-- 데이터베이스 일관성 체크 및 복구
-- 1. RUNNING 상태인데 30분 이상 된 작업 찾기
SELECT advertiser_id, last_batch_run, last_batch_status
FROM advertisers 
WHERE last_batch_status = 'RUNNING' 
  AND last_batch_run < NOW() - INTERVAL 30 MINUTE;

-- 2. 스테일 작업을 FAILED로 변경
UPDATE advertisers 
SET last_batch_status = 'FAILED',
    failure_count = failure_count + 1,
    batch_enabled = CASE 
        WHEN failure_count + 1 >= max_failures THEN false 
        ELSE batch_enabled 
    END,
    updated_at = NOW()
WHERE last_batch_status = 'RUNNING' 
  AND last_batch_run < NOW() - INTERVAL 30 MINUTE;

-- 3. 비정상적으로 높은 실패 카운트 리셋 (관리자 판단)
UPDATE advertisers 
SET failure_count = 0,
    batch_enabled = true,
    last_batch_status = 'PENDING'
WHERE failure_count >= max_failures 
  AND updated_at < NOW() - INTERVAL 1 DAY;  -- 1일 이상 된 실패
```

## 🔍 모니터링 및 알림

### 실시간 장애 감지

```java
@Component
public class FailureDetector {
    
    @EventListener
    public void onBatchFailure(BatchFailureEvent event) {
        String advertiserId = event.getAdvertiserId();
        int failureCount = event.getFailureCount();
        
        // 연속 실패 패턴 감지
        if (failureCount >= 2) {
            alertService.sendAlert(
                "광고주 " + advertiserId + " 연속 실패 (" + failureCount + "회)",
                AlertLevel.WARNING
            );
        }
        
        // 시스템 전체 건강도 체크
        checkSystemHealth();
    }
    
    private void checkSystemHealth() {
        long eligibleCount = advertiserRepository.countEligibleAdvertisers();
        long totalCount = advertiserRepository.count();
        
        double healthRatio = (double) eligibleCount / totalCount;
        
        if (healthRatio < 0.5) {  // 50% 미만이 처리 가능한 경우
            alertService.sendAlert(
                "시스템 건강도 저하: 처리 가능한 광고주 " + eligibleCount + "/" + totalCount,
                AlertLevel.CRITICAL
            );
        }
    }
}
```

### 복구 성공률 추적

```java
@Component
public class RecoveryMetrics {
    private final Map<String, RecoveryStats> recoveryStats = new ConcurrentHashMap<>();
    
    public void recordRecoveryAttempt(String advertiserId) {
        recoveryStats.computeIfAbsent(advertiserId, k -> new RecoveryStats())
                    .incrementAttempts();
    }
    
    public void recordRecoverySuccess(String advertiserId) {
        recoveryStats.computeIfAbsent(advertiserId, k -> new RecoveryStats())
                    .incrementSuccesses();
    }
    
    public double getRecoverySuccessRate(String advertiserId) {
        RecoveryStats stats = recoveryStats.get(advertiserId);
        if (stats == null || stats.attempts == 0) return 0.0;
        return (double) stats.successes / stats.attempts;
    }
    
    static class RecoveryStats {
        private long attempts = 0;
        private long successes = 0;
        
        void incrementAttempts() { attempts++; }
        void incrementSuccesses() { successes++; }
    }
}
```

이 포괄적인 장애 격리 및 복구 가이드를 통해, 시스템 운영자는 어떤 장애 상황에서도 신속하고 정확하게 대응할 수 있습니다. 각 시나리오별로 자동화된 복구 메커니즘과 수동 개입 절차가 모두 준비되어 있어, 최대한 빠른 시간 내에 정상 운영을 복구할 수 있습니다.