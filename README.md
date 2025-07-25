# Spring Batch CDP Data Processing Application

A Spring Boot application that processes Customer Data Platform (CDP) data for advertisers and AI systems using Spring Batch.

## Features

- **Multi-Advertiser CDP Processing**: Fault-tolerant round-robin batch processing for multiple advertisers
- **Automatic Job Rotation**: Each advertiser gets dedicated batch processing in rotation
- **Failure Isolation**: If one advertiser's batch fails, others continue unaffected
- **REST API**: Endpoints for advertisers to access processed customer data
- **Admin Interface**: Monitor and control batch job execution
- **Modern Architecture**: Constructor injection, Java 17, Spring Boot 3.4.1, Spring Batch 5.x
- **Database**: H2 in-memory database for fast development
- **AI Integration**: Extracts advertiser-specific features for machine learning and AI systems

## Prerequisites

- Java 17 or higher
- Gradle 8.x (included with wrapper)

## Quick Start

### 1. Build the Application
```bash
./gradlew build
```

### 2. Run the Application
```bash
./gradlew bootRun
```

The application will start on port 8080 with H2 in-memory database.

### 3. Access H2 Console
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:cdpdb`
- Username: `sa`
- Password: `password`

## API Endpoints

### Customer Data Access
- `POST /api/advertiser/data` - Get filtered customer data
- `POST /api/advertiser/request` - Create data request
- `GET /api/advertiser/request/{id}` - Check request status
- `GET /api/advertiser/requests/{advertiserId}` - Get advertiser requests

### Metadata
- `GET /api/advertiser/segments` - Available customer segments
- `GET /api/advertiser/event-types` - Available event types

### Batch Administration
- `GET /api/admin/batch/status` - Get system status (eligible/running advertisers)
- `GET /api/admin/batch/advertisers/eligible` - List eligible advertisers
- `GET /api/admin/batch/advertisers/running` - List currently running batch jobs
- `POST /api/admin/batch/trigger/{advertiserId}` - Manually trigger batch job for advertiser
- `POST /api/admin/batch/reset/{advertiserId}` - Reset failure count for advertiser
- `GET /api/admin/batch/advertiser/{advertiserId}` - Get specific advertiser status

### Example API Request
```json
POST /api/advertiser/data
{
  "advertiserId": "ADV123",
  "segments": ["premium", "active"],
  "eventTypes": ["purchase", "view"],
  "startDate": "2025-01-01T00:00:00",
  "endDate": "2025-07-25T23:59:59",
  "aiEnhanced": true,
  "limit": 100
}
```

## Multi-Advertiser Batch Processing

### Automatic Rotation System
- **Scheduler**: Runs every 30 seconds, selecting next advertiser in round-robin fashion
- **Fault Tolerance**: Failed advertisers don't block others; automatic retry with backoff
- **Health Monitoring**: Cleanup jobs that run too long (30+ minutes)
- **Failure Limits**: Advertisers disabled after 3 consecutive failures

### Sample Advertisers
The system initializes with 5 sample advertisers:
- **ADV001**: TechCorp
- **ADV002**: FashionBrand  
- **ADV003**: FoodDelivery
- **ADV004**: TravelAgency
- **ADV005**: FinanceApp

### Job Configuration (Per Advertiser)
- **Reader**: Advertiser-specific data filtering from last 24 hours
- **Processor**: Extracts advertiser-specific AI features
- **Writer**: Saves processed data with advertiser context
- **Chunk Size**: 50 items per transaction
- **Fault Tolerance**: Skip up to 10 failed items per job

### Manual Job Control
```bash
# Check system status
curl http://localhost:8080/api/admin/batch/status

# List eligible advertisers
curl http://localhost:8080/api/admin/batch/advertisers/eligible

# Manually trigger job for specific advertiser
curl -X POST http://localhost:8080/api/admin/batch/trigger/ADV001

# Reset failed advertiser
curl -X POST http://localhost:8080/api/admin/batch/reset/ADV001
```

## Database Configuration

The application uses H2 in-memory database configured in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:cdpdb
    driver-class-name: org.h2.Driver
    username: sa
    password: password
  h2:
    console:
      enabled: true
```

- **Database**: H2 in-memory (data resets on restart)
- **Console Access**: http://localhost:8080/h2-console
- **JDBC URL**: `jdbc:h2:mem:cdpdb`
- **Credentials**: sa / password

### Persistent H2 Database
To make data persistent across restarts, change the URL in `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/cdpdb
```

## Architecture

### Entities
- **Customer**: Customer profile and metadata
- **CustomerData**: Event data with AI processing flags
- **AdvertiserRequest**: Tracks data requests from advertisers

### Components
- **Batch Configuration**: Job and step definitions
- **ItemReader**: Reads unprocessed customer data
- **ItemProcessor**: AI feature extraction and data transformation
- **ItemWriter**: Saves processed data
- **REST Controller**: API endpoints for data access
- **Services**: Business logic and data filtering

## AI Features Extracted

- Customer segmentation and behavior patterns
- Recency, frequency, and monetary analysis
- Cross-channel activity scoring
- Device fingerprinting for fraud detection
- Location-based scoring
- Temporal pattern analysis
- Campaign engagement metrics

## 📚 상세 문서

### 한국어 기술 문서
- **[내결함성 배치 시스템 개요](FAULT_TOLERANT_BATCH_SYSTEM.md)** - 시스템 아키텍처 및 운영 플로우
- **[라운드로빈 순환 메커니즘](ROTATION_MECHANISM_DETAILED.md)** - 순환 알고리즘 상세 분석
- **[엣지 케이스 처리 가이드](EDGE_CASES_AND_SCENARIOS.md)** - 모든 예외 상황 대응 방법
- **[장애 격리 및 복구](FAILURE_ISOLATION_RECOVERY.md)** - 장애 시나리오별 복구 전략

### 시스템 특징
- **완전한 장애 격리**: 한 광고주의 실패가 다른 광고주에게 영향 없음
- **자동 복구**: 실패 횟수 추적 및 점진적 복구 메커니즘
- **실시간 모니터링**: 상태 추적 및 관리 API 제공
- **공정성 보장**: 라운드로빈으로 모든 광고주에게 동등한 기회

## Version Information

- **Spring Boot**: 3.4.1
- **Spring Batch**: 5.x (included with Spring Boot)
- **Java**: 17 (compatible with 17+)
- **Database**: H2 in-memory

## Troubleshooting

### Java Version Issues
If you get Java toolchain errors:
1. Check your Java version: `java -version`
2. Update `build.gradle` java toolchain to match your version
3. Or install Java 17+

### Database Connection Issues
- Check H2 console URL: http://localhost:8080/h2-console
- Verify JDBC URL: `jdbc:h2:mem:cdpdb`
- Use credentials: sa / password

### Build Issues
```bash
# Clean and rebuild
./gradlew clean build

# Run with debug info
./gradlew build --debug
```

## Development

### Adding Sample Data
The application includes data initialization. Check `DataInitializer` component for sample customer and event data.

### Running Tests
```bash
./gradlew test
```

### Code Style
- Constructor injection (no field injection)
- Modern Spring Boot practices
- Proper error handling and logging