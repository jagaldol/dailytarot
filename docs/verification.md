# 2026-10-07 날짜별 기록·Lifebase 연동·디자인 개편 검증

## 범위

`.omx/plans/daily-tarot-local-history-lifebase.md` 계획의 B–E 단계를 구현했습니다.
서버·로그인·앱 내부 AI 호출·일지 쓰기는 없습니다. 새 라이브러리 의존성도 추가하지 않았습니다.

- 날짜별 기록: 플랫폼 SQLite(`readings.db`, 날짜 기본키). 로컬 추첨은 빈 날짜만 채우고(insert-if-absent),
  Lifebase 결과는 교체합니다(upsert). 모든 쓰기는 하나의 repository가 직렬화합니다.
- 기본 운세: Lifebase 카드 사전 문구 156개를 `assets/default_fortunes.ko.json`으로 번들했습니다.
  기록에는 저장 당시 문구를 복사합니다.
- Lifebase: SAF 읽기 권한, `Journal/YYYY/MM/YYYY-MM-DD.md` 이름 탐색, 현재·옛 형식 파서,
  운세 섹션 hash 비교, 연결 세대 ID, 월 단위 과거 기록 가져오기.
  확인 일정: 앱을 열 때·지금 확인은 8일치 전체 읽기, 오늘 운세 도착 전에만 15분마다 오늘·어제 확인
  (크기·수정 시각이 같으면 파일을 열지 않음), 도착 후에는 다음 자정 직후 한 번만 실행.
- UI: 종이·잉크·금색 테마(라이트/다크), 카드 뒤집기 공개, 오늘·기록·상세·설정·직접 고르기 화면, 새 앱 아이콘.
- 오늘의 카드: 하루 시작 시간(기본 자정) 전에는 어제 카드, 이후에는 오늘 카드 또는 카드 뒷면.
  자동 뽑기(기본 켜짐, 오전 8시, Lifebase 연결 시 자동으로 꺼짐). Lifebase 연결 중에는 뒷면을 눌러도 뽑히지 않고,
  확인 대화상자를 거치는 "카드 직접 뽑아보기"만 제공. 앱에서 뽑거나 고른 기록은 언제든, Lifebase에서 불러온 기록은
  연결 해제 상태에서만 한 건씩 또는 전부 지울 수 있음.
- 위젯: 기본 5×6(4열 런처에서는 4×6), 투명 배경에 카드를 최대한 크게. 날짜·이름·운세 한 줄 캡션(세로: 아래, 가로: 옆)은
  잘리지 않도록 13→10sp까지 줄여 맞추고, 그래도 안 되면 카드만 표시. 위젯 선택기 미리보기.
- 백업: 자동 백업은 기록 DB와 설정만 포함하고 폴더 권한은 제외합니다. JSON 내보내기/복원(빈 날짜만)을 제공합니다.

## 자동 검증

```sh
./gradlew :app:testDebugUnitTest :app:lint :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest   # DailyTarot_API37_Verification 에뮬레이터
python3 tools/check_assets.py
python3 tools/extract_lifebase_catalog.py ~/Documents/lifebase --check
```

- JVM 테스트 44개 통과(위젯 캡션, 예약 시각·파일 지문, 하루 시작·자동 뽑기 시간 규칙, 연결 상태별 삭제 허용 포함).
  - 파서: 현재 형식, 키워드만 있는 옛 형식, BOM/CRLF/따옴표 값, 카드 미기록, 작성 중(한 줄만·섹션 없음),
    중복 키·알 수 없는 카드·YAML 주석·방향/카드 불일치·중복 운세 제목·닫히지 않은 frontmatter 거부,
    코드 블록 안 가짜 제목 무시, 다음 H2에서 섹션 종료, 일기 수정은 hash 불변, 일기·일정·메모 미포함.
  - 저장소 규칙: 첫 추첨 재사용, 동시 100회 요청에도 한 행, 연말 자정 경계, 빠진 날짜 미보충,
    이전 버전 선택 이전과 1회 새로 뽑기, 연결 중 직접 선택 거부, 기본 A→Lifebase B 원자적 교체,
    늦은 로컬 추첨이 Lifebase를 덮지 않음, 같은 내용 반복 시 위젯 갱신 없음, 이전 연결 결과 폐기,
    문구 수정 반영과 완전한 기록의 키워드 전용 강등 거부, 카탈로그 교체 후에도 과거 문구 유지, 복원은 빈 날짜만.
  - 카탈로그 156개·덱 이름 일치, JSON 왕복, 백업 왕복·외부 파일 거부, 윤년·월말·요일·시간대.
- Android 테스트 8개 통과: 실제 SQLite 동시 insert와 관찰, 카드 공개→직접 고르기 저장→Activity 재생성,
  기록 목록→상세, 여섯 가지 위젯 크기(카드만/빠듯한 세로/세로/가로/전체)의 카드·캡션 렌더링, 실제 AppWidgetHost 두 개의 연속 변경 반영,
  기존 이미지 회전·캐시·아랍어 로케일 테스트.
- 린트 오류 0, 경고 0.
- 카탈로그가 현재 vault 카드 사전과 156개 모두 문자 단위로 같음을 확인했습니다.

## 에뮬레이터 수동 확인 (API 37)

익명화한 가짜 vault(`Documents/lifebase/Journal`, 현재 형식 2개·옛 형식 1개)를 넣어 확인했습니다.
실제 개인 일지는 기기에 복사하지 않았습니다.

- 폴더 선택기에서 vault 루트를 고르면 `Journal`을 찾아 연결하고, 오늘 운세와 과거 3개를 가져왔습니다.
- 오늘의 기본 카드가 일지의 카드로 바뀌고 출처가 “Lifebase 일지”로 표시됐습니다.
- 옛 형식 기록은 “원문에 해석 없음”과 별도의 기본 해석으로 표시됐습니다.
- 홈 화면에 위젯을 실제로 추가하고 2×3과 화면 전체 크기에서 확인했습니다.
  (이전 디자인에서) 일지의 운세 문구를 고친 뒤 새로고침하면 위젯이 바뀌는 것을 확인했습니다.
- 라이트/다크 모드에서 앱과 위젯을 확인했습니다. 한국어 문장이 어절 단위로 줄바꿈됩니다(앱 화면).
- R8 릴리스 빌드를 디버그 키로 서명해 설치해 Lifebase 연결과 위젯 갱신을 확인했습니다.
  (당시 위젯 새로고침 버튼의 생성자가 R8에 제거되는 문제를 찾았고, 이후 위젯을 단순화하며 버튼 자체를 없앴습니다.)

- (오전 0시대 실측) 새로 설치·미연결·오전 8시 전: 카드 뒷면과 "카드를 눌러 오늘의 카드를 뽑아보세요".
  연결 직후 자동 뽑기가 꺼지고 오전 8시 전이라 어제(일지) 카드와 "어제의 카드" 안내를 표시.
  바뀌는 시간을 오전 0시로 바꾸자 앱·위젯 모두 카드 뒷면과 "Lifebase에 오늘의 카드가 아직 등록되지 않았어요",
  뒷면을 눌러도 뽑히지 않음. "카드 직접 뽑아보기"→확인으로 뽑은 카드는 오늘 일지를 넣은 뒤 앱을 열자 일지 카드로
  바뀌었고, 15분 확인 작업도 취소됨.
- 미연결·자동 뽑기 꺼짐에서 위젯의 "오늘의 카드를 뽑아주세요"를 누르면 앱이 오늘 화면으로 열리며 카드 뒷면이 뒤집혀 뽑힘.
  앱을 강제 종료 후 다시 열면 연속 캡처에서 뒷면 → 회전 중 → 앞면 순서로 진입 애니메이션이 재생됨.
  홈에 3초 다녀오면 다시 뒤집지 않음. 10분 이상 다녀오면 다시 뒤집힘.
- 연결 중 기록 상세에는 삭제 버튼이 없고, 해제 후에는 휴지통→확인으로 오늘 기록을 지우면 오늘 화면이 카드 뒷면으로 돌아감.

## 확인하지 못한 범위

- 위젯 기본 크기는 4열 Pixel 런처(4×6)에서만 확인했습니다. 5열 런처의 5×6 배치는 확인하지 않았습니다.

- 실제 휴대폰, Syncthing 파일 교체, 재부팅·절전·강제 종료 후 주기 갱신 지연은 측정하지 않았습니다.
- API 24 에뮬레이터가 없어 API 24–25에서는 실행하지 않았습니다. `java.time`을 쓰지 않고 `GregorianCalendar`로 날짜를 계산합니다.
- 내보내기→새 설치 복원과 OS 자동 백업 복원은 단위 테스트(코덱·복원 규칙)로만 확인했습니다.

---

# 2026-10-07 검증 기록

## 범위

기준 버전은 `main`의 `3d37fe4`입니다. 보류된 `draft` 테마 실험은 병합하지 않았습니다.
기존 카드 선택 DataStore 키를 보존했습니다. GitHub 공개 전환, 배포, 커밋 이력 재작성은 수행하지 않았습니다.

## 변경

- 섹션별 `FlowRow`를 카드별 `LazyVerticalGrid`로 교체하고 카드 ID를 항목 키로 사용했습니다.
- 썸네일 디코딩을 IO 스레드로 옮기고 12MiB LRU 캐시와 `prepareToDraw()`를 적용했습니다.
- 회전 각도는 하나의 animation state를 공유하고 `graphicsLayer`에서 읽습니다.
  회전 이미지에 둥근 모서리 클리핑을 추가하지 않으며, 기존 카드 폭 96dp를 유지합니다.
- 원본 이미지 78장을 1086×1810에서 720×1200으로 줄였습니다. 썸네일은 360×600 그대로입니다.
- 위젯 비트맵은 최대 높이 1000px이며 작은 디스플레이에는 더 작게 맞춥니다.
  축소·회전은 백그라운드에서 수행하고 6MiB 캐시를 공유합니다.
- 앱과 위젯이 같은 DataStore를 읽습니다. 신규 위젯에 별도 상태 복사가 필요하지 않습니다.
- 저장 작업을 Application 수명으로 분리하고 단일 writer가 최신 대기 입력을 처리합니다.
  저장과 갱신 오류를 구분하며, 읽기 실패 시 기본값으로 기존 데이터를 덮어쓰지 않습니다.
- 설치된 위젯이 있을 때만 WorkManager 갱신을 예약합니다. 400ms 지연의 고유 작업으로
  연속 변경을 합치며, 앱 시작 시에도 필요한 갱신을 예약합니다.
- 문자열 리소스 조회, 미연결 위젯 action, 템플릿 테스트·색상·타이포그래피 코드를 정리했습니다.
- 썸네일의 재귀 생성, 이미지 다운로드 실패 처리와 임시 파일 정리를 고쳤습니다.
- R8 코드/리소스 축소, README, 이미지 출처 기록, GitHub Actions 검증 설정을 추가했습니다.

## 자동 검증

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lint
./gradlew :app:connectedDebugAndroidTest
python3 tools/check_assets.py
bash -n tools/fetch_rws_images.sh tools/convert_pngs_to_webp.sh tools/make_thumbs.sh
git diff --check
```

- JVM 테스트 7개: 덱 참조/구성, 잘못된 선택 거부, 연속 입력, 읽기/저장/갱신 오류와 재시도,
  작은 화면의 위젯 비트맵 크기 제한.
- Android 테스트 7개: 이미지 회전·캐시·크기, 아랍어 로케일의 78장 이미지,
  선택/정역방향의 Activity 재생성 유지, 마지막 카드까지 스크롤·선택,
  신규 위젯의 기존 선택 로딩, 빈 위젯 안내, 실제 AppWidgetHost 두 개의 갱신.
- 카드 이미지 156개의 이름과 크기를 검사합니다.
- 썸네일 스크립트를 임시 디렉터리에서 두 번 실행해 원본 1개와 썸네일 1개만 남는 것을 확인했습니다.
- 린트: 오류 0, 경고 11. 남은 경고는 의존성과 빌드 도구 버전 안내입니다.
  기존의 기본 로케일·동적 리소스 조회·미사용 리소스 경고는 제거됐습니다.
- GitHub Actions YAML을 검사했습니다. 원격 CI 실행은 아직 수행하지 않았습니다.

기기 테스트 도구는 Android 16에서 발생한 `InputManager.getInstance` 오류 때문에
Espresso 3.7.0 / AndroidX JUnit 1.3.0으로 갱신했습니다.
[공식 변경 내역](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)을 확인했습니다.

## 최종 릴리스 확인

프로세스를 강제 종료하고 다시 실행한 뒤 `Selected: The Fool` 및 `Reversed` 상태를
UI hierarchy에서 확인했습니다. 앱에서 추가 조작 없이 새 홈 화면 위젯을 설치한 뒤
`The Fool, Reversed` 설명과 실제 회전 이미지를 확인했습니다.
정방향·역방향·홈 화면 스크린샷은 [README](../README.md)에 포함했습니다.

최종 unsigned release APK SHA-256:
`78a17dab18f5c1194c86d0b926d5da41257a07d4ceb9a3390acd491abf50e786`

## 크기

| 항목 | 이전 | 수정 후 |
| --- | ---: | ---: |
| 카드 원본 78장 | 33.01MiB | 15.75MiB |
| 썸네일 포함 이미지 | 37.32MiB | 20.06MiB |
| unsigned release APK | 45.35MiB | 21.82MiB |

R8로 축소한 release APK의 값입니다. debug APK와 비교한 값이 아닙니다.
APK 증분 패키징 과정에는 빈 공간이 남을 수 있으므로 debug APK의 파일 크기를 최적화 지표로 쓰지 않았습니다.

## 성능 표본

환경: macOS Apple Silicon, Android Emulator 36.2.12, Medium Phone API 36.1,
1080×2400, 홈 화면 위젯 1개. 기존/수정 release APK를 동일한 디버그 키로 서명해
같은 에뮬레이터에 번갈아 설치했습니다. 이 서명은 로컬 검증용입니다.

각 버전에서 프로세스를 다시 시작하고 카드 선택 및 회전을 예열한 뒤 측정했습니다.
회전은 스위치 12회(입력 사이 450ms 대기), 스크롤은 350ms swipe를 위로 12회,
아래로 12회 수행했습니다. 단계별로 `adb shell dumpsys gfxinfo com.jagaldol.dailytarot reset` 후
`dumpsys gfxinfo`를 수집했습니다. 앱이 전면에 표시된 것을 UI hierarchy로 확인했습니다.

| 동작 | 이전 | 수정 후 |
| --- | ---: | ---: |
| 회전 지연 프레임 | 1 / 354 (0.28%) | 0 / 350 (0.00%) |
| 회전 p99 | 18ms | 17ms |
| 스크롤 지연 프레임 | 51 / 490 (10.41%) | 2 / 552 (0.36%) |
| 스크롤 p99 | 53ms | 25ms |

원시 결과: [이전 회전](performance/before-rotation.txt), [수정 후 회전](performance/after-rotation.txt),
[이전 스크롤](performance/before-scroll.txt), [수정 후 스크롤](performance/after-scroll.txt).

이는 한 에뮬레이터의 표본이며 통계적 벤치마크나 실기기 전체의 성능 보장이 아닙니다.
호스트 부하에 따라 앞선 탐색 측정의 수치도 달라졌습니다. 알림창에 가려져 앱 프레임이
0개로 나온 실행은 무효 처리했습니다. 화면 밖 이미지의 일괄 디코딩과 위젯의 원본 크기
회전은 코드와 테스트로 제거를 확인했지만, 작년에 사용한 실제 기기의 체감 개선은 별도 확인이 필요합니다.
최종 작은 화면 크기 제한은 이 표본의 1080×2400 환경에서 같은 600×1000 비트맵을 사용합니다.

## 공개 전 남은 항목

코드 라이선스 선택, 원본 스캔의 재배포 조건 확인, 전체 Git 이력의 민감정보 검사는 별도입니다.
[이미지 출처와 확인 상태](../ASSETS.md)를 기록했습니다.

## Android Studio 업그레이드 후 경고 정리

같은 날 Upgrade Assistant 실행 뒤, 아래 빌드 환경으로 추가 검증했습니다.
위의 성능 표본·스크린샷·APK 해시는 업그레이드 전 기록입니다.

| 항목 | 최종 버전 |
| --- | --- |
| Gradle / AGP | 9.8.0 / 9.4.1 |
| Kotlin / Compose compiler | 2.4.20 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24 |
| 실행 JDK / 앱 JVM 컴파일 대상 | JBR 21 / 11 |
| Android SDK Platform / Build Tools | 37.0 / 36.0.0 |
| Core / Lifecycle / Activity | 1.19.1 / 2.11.0 / 1.13.0 |
| Compose BOM / Glance | 2026.09.00 / 1.2.0 |
| DataStore / WorkManager | 1.2.1 / 2.12.0 |

- AGP의 built-in Kotlin 및 새 DSL을 사용합니다. Upgrade Assistant가 추가한
  구버전 호환 옵션과 별도 Kotlin Android 플러그인을 제거했습니다.
- JVM 대상은 `android.compileOptions`의 11을 그대로 사용하며, 중복된
  `kotlinOptions.jvmTarget` 설정을 제거했습니다.
- `Card`의 drawable 어노테이션에 `@param:`을 명시해 기존 적용 대상을 유지했습니다.
- Compose의 리소스 조회를 `LocalResources.current`로 바꿨습니다.
- Compose UI 테스트는 v2 `createAndroidComposeRule`로 이전했습니다.
- Gradle wrapper 스크립트·JAR, CI SDK 설치 버전과 실행 문서를 맞췄습니다.

검증 명령:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint :app:assembleRelease :app:assembleDebugAndroidTest --warning-mode=all
./gradlew :app:connectedDebugAndroidTest
python3 tools/check_assets.py
git -c core.whitespace=trailing-space,space-before-tab,cr-at-eol diff --check
```

- 디버그·릴리스·기기 테스트 APK 빌드 성공, JVM 테스트 7개 통과.
- 별도 `DailyTarot_API37_Verification` 에뮬레이터(Android 17, API 37.0,
  Google APIs ARM64, 1080×2400)에서 기기 테스트 7개 통과, 실패·오류·건너뜀 0개.
  카드 선택·회전의 Activity 재생성 유지, 마지막 카드 스크롤·선택, 이미지 크기·회전·캐시,
  아랍어 로케일, 빈 위젯 안내, 신규 위젯 로딩, 두 위젯 동시 갱신을 검증했습니다.
  이번 추가 검증에서는 API 24 실기기 테스트와 원격 GitHub Actions 실행은 하지 않았습니다.
- 린트 XML의 issue 수 0, 텍스트 보고서 `No issues found.` 확인.
- 카드 이미지 156개 검증 통과. wrapper가 생성하는 Windows 배치 파일의 CRLF를
  허용하면서 공백 오류를 검사했습니다.
- 최신 Gradle에서는 AGP 내부의 `Configuration.setVisible(boolean)` 호출에 대한
  폐기 예정 API 경고 1개가 남습니다. `-Dorg.gradle.deprecation.trace=true`로
  `com.android.build.gradle.internal.plugins.BasePlugin`의
  `createAndroidJdkImageConfiguration` 호출임을 확인했습니다.
  앱 코드나 린트 문제는 아니며, 전역 경고 숨김이나 외부 플러그인 패치는 하지 않았습니다.

업그레이드 후 unsigned release APK: 23,051,630 bytes,
SHA-256 `0bef179caf0b5ca1df1ce28ff1d1f076ae0cf65d046a652983cb0d0d4227ea2f`.

참고: [AGP 9.4 호환성](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[built-in Kotlin 이전](https://developer.android.com/build/migrate-to-built-in-kotlin),
[Compose 테스트 v2 이전](https://developer.android.com/develop/ui/compose/testing/migrate-v2),
[Android 17 동작 변경](https://developer.android.com/about/versions/17/behavior-changes-17).
