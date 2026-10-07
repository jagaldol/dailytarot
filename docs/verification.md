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
