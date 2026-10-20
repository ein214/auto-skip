# AutoSkip — Android 개인용 광고 종료 도우미

> MVP 기획서 v0.2 | Claude Code 전달용 | 2026-10-13

## 1. 목표와 범위

- 사용자가 뮤니버스 등 K-POP Android 앱에서 광고를 **직접 시작**한 뒤, 광고 종료가 가능한 시점에 나타난 명확한 Skip / Close / X / Done UI를 찾아 닫는다.
- 유형 A: 전체 화면 광고 → 종료 버튼 등장 → 검증 후 클릭 → 원래 앱 복귀.
- 유형 B: 전체 화면 광고 → 설치 유도 엔드카드 → 사용자가 Google Play로 이동 및 복귀 → 3~5초 후 종료 버튼 등장 → 검증 후 클릭.
- Google Play CTA 자동 클릭, 시스템 뒤로가기 자동 실행, 광고 시작 자동 반복, 쿨타임 우회, 보상 자동 적립 루프는 MVP에서 제외한다.
- 대상은 개인 Android 기기에 직접 설치하는 로컬 APK. 접근성 권한은 사용자가 직접 활성화한다.

## 2. 상태 머신

```text
IDLE
  └─ (허용 앱에서 광고 화면 감지) → AD_DETECTED
AD_DETECTED → AD_PLAYING
AD_PLAYING
  ├─ (명확한 종료 버튼 활성화) → CLOSE_READY
  ├─ (설치 유도 화면) → END_CARD
  └─ (호스트 화면 복귀) → RETURNED_TO_HOST
END_CARD
  ├─ (종료 버튼 활성화) → CLOSE_READY
  └─ (사용자 조작으로 Play Store 이동) → EXTERNAL_STORE
EXTERNAL_STORE
  └─ (사용자가 광고 화면으로 복귀) → RETURNED_TO_AD
RETURNED_TO_AD
  ├─ (종료 버튼 활성화) → CLOSE_READY
  └─ (아직 없음) → WAITING_FOR_CLOSE → RETURNED_TO_AD
CLOSE_READY
  ├─ (버튼 재검증 성공) → AUTO_CLOSE → VERIFY_HOST
  └─ (검증 실패) → AD_PLAYING 또는 UNKNOWN
VERIFY_HOST
  ├─ (뮤니버스 충전소 등 호스트 화면 확인) → RETURNED_TO_HOST → IDLE
  └─ (실패) → UNKNOWN
모든 상태 → (사용자 중지, 화면 식별 실패, 제한시간 초과) → PAUSED/UNKNOWN
```

- 상태 변경은 이벤트 기반으로 처리한다. 3~5초는 참고 관찰 구간이며 시간 경과만으로 좌표 클릭하지 않는다.
- EXTERNAL_STORE 동안 자동 클릭을 중단한다. 사용자가 돌아오면 현재 화면을 다시 검사한다.
- 광고 SDK가 동일 패키지 내 Activity를 띄울 수 있으므로 패키지명만으로 광고 여부를 확정하지 않는다.
- 노드가 비활성화/비가시 상태라면 절대 클릭하지 않는다.

## 3. 우선순위 및 완료 조건

| 단계 | 작업 | 완료 기준 |
|---|---|---|
| P0-1 | Kotlin/Compose 프로젝트, 접근성 서비스, 권한 안내, ON/OFF | APK 빌드 및 사용자 수동 권한 승인 |
| P0-2 | 허용 앱 목록, UI 이벤트 수신, 노드 탐색 | 허용 앱에서만 탐지, 외부 앱에서는 클릭 금지 |
| P0-3 | Skip/Close/Done 등 명확한 종료 버튼 탐지 및 클릭 | 테스트 광고 화면에서 직접 종료 패턴 통과 |
| P0-4 | 클릭 전 광고 컨텍스트/가시성/활성화 재검증, 디바운스 | 일반 팝업/결제/인증 화면 오탐 방지 |
| P1-1 | 외부 스토어 진입/복귀 상태 추적 | 외부 화면에서 클릭하지 않고 복귀 감지 |
| P1-2 | 복귀 후 X UI 재탐색 및 호스트 복귀 확인 | 지연 노출되는 종료 버튼 처리 |
| P1-3 | 세션 로그, 오류/타임아웃 처리, 긴급 중지 | 미확인 화면에서 안전하게 정지 |
| P2 | OCR/이미지 기반 X 탐지 | UI 노드가 없는 광고에 한해 별도 검증 후 추가 |

## 4. 아키텍처

```text
app/src/main/
  AndroidManifest.xml
  java/com/personal/autoskip/
    MainActivity.kt
    accessibility/AutoSkipAccessibilityService.kt
    accessibility/NodeScanner.kt
    accessibility/ClickExecutor.kt
    detection/AdContextDetector.kt
    detection/CloseButtonMatcher.kt
    detection/HostScreenDetector.kt
    automation/AdState.kt
    automation/AdStateMachine.kt
    automation/AdSessionController.kt
    settings/AppAllowlistRepository.kt
    settings/PreferencesRepository.kt
    ui/SettingsScreen.kt
  res/xml/accessibility_service_config.xml
```

- AccessibilityService는 이벤트 수신 및 상태 머신 전달만 수행.
- Detector는 화면/버튼 판정, StateMachine은 순수한 상태 전이, Executor는 검증된 클릭만 수행.
- DataStore에 ON/OFF, 허용 패키지, 민감 앱 제외, 간단한 통계 저장.
- 네트워크 권한은 필요하지 않음. 화면 텍스트나 캡처 이미지는 외부 전송하지 않음.

## 5. Android 구현 메모

- AccessibilityService: `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED`, `rootInActiveWindow`, `AccessibilityNodeInfo.ACTION_CLICK`.
- 노드의 text, contentDescription, viewIdResourceName, isClickable, isEnabled, isVisibleToUser를 활용.
- `X`만 있는 버튼은 일반 닫기 버튼과 혼동하기 쉬우므로 광고 화면 검증이 필수.
- 화면 전환 이벤트 폭주를 방지하는 스로틀/디바운스와 클릭 직전 재탐색 적용.
- `GLOBAL_ACTION_BACK`을 광고 자동 종료용으로 무조건 실행하지 않음.
- Android 버전별 접근성 서비스 및 백그라운드 제약 확인.
- 이미지 인식은 첫 버전에서 제외. 필요시 사용자 동의 기반 MediaProjection과 OCR 도입을 별도 검토.

## 6. 뮤니버스 스크린샷으로 확인된 사실

- 충전소에 `영상보고 ⭐ 5 받기` 행이 존재.
- 쿨타임 중에는 해당 행이 어둡게 표시되고 카운트다운이 나타남.
- 활성화되면 행이 밝아지고 카운트다운이 사라짐.
- 일일 소진 시 `오늘 0회 남음` 및 긴 초기화 타이머가 표시됨.
- 광고는 전체 화면으로 표시되고, 종료 패턴은 X 직접 노출 또는 Play Store 경유가 혼재함.
- **이 정보는 향후 앱 상태 식별 참고용**이며, 광고 보기 버튼 자동 클릭 및 반복 실행은 v0.2 범위 밖이다.
- 스크린샷만으로 접근성 노드가 실제 노출되는지는 알 수 없음. Android Studio Layout Inspector/UiAutomator로 확인할 것.

## 7. 테스트 케이스

1. 직접 종료: Skip 등장 → 클릭 → 원래 앱 복귀.
2. 지연 종료: 광고 종료 후 5초 이상 버튼 없음 → 클릭하지 않음 → 버튼 등장 후 클릭.
3. 스토어 경유: 사용자가 스토어 진입 → 자동 동작 없음 → 사용자 복귀 → 종료 버튼 재탐색.
4. 일반 팝업: X 또는 닫기 버튼이 있어도 광고 컨텍스트가 아니면 클릭하지 않음.
5. 버튼 비활성: 텍스트가 보여도 비활성/비가시 상태면 클릭하지 않음.
6. 빠른 이벤트 중복: 한 번의 종료에 클릭이 여러 번 발생하지 않음.
7. 예상치 못한 화면, 네트워크 오류, 앱 전환: PAUSED/UNKNOWN으로 안전하게 전환.
8. 사용자가 ON/OFF 변경 또는 접근성 서비스 종료: 즉시 중단.
9. 광고 SDK가 호스트와 같은 패키지를 사용하는 경우에도 화면 상태를 식별.