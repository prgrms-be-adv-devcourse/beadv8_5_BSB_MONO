rootProject.name = "bukang"

// 공통 라이브러리(common, jwt), API 게이트웨이(gateway)와 서비스별 Spring Boot 앱
include("common", "jwt", "gateway", "member-service", "cash-service", "payout-service", "file-service", "order-service", "product-service", "queue-service", "notification-service", "inquiry-service", "board-service")
