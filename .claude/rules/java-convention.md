[캠퍼스 핵데이 Java 코딩 컨벤션](https://naver.github.io/hackday-conventions-java/)을 따릅니다. 글로벌 설정의 "들여쓰기 2칸"보다 이 규칙이 우선합니다.

- 들여쓰기는 **탭**(크기 4), 한 줄 최대 120자
- import 와일드카드(`*`) 금지, 한 줄짜리 `if`/`for`에도 중괄호 필수
- Java 코드를 수정한 뒤에는 `./gradlew checkstyleMain checkstyleTest`로 검사하고, 경고가 나오면 수정합니다. 위반이 있어도 빌드는 실패하지 않고 경고만 출력되므로(`isIgnoreFailures = true`), 출력을 직접 확인해야 합니다.
- 규칙 파일은 `build.gradle.kts`에서 네이버 GitHub 원본을 URL로 직접 참조하므로, 검사하려면 네트워크 연결이 필요합니다.