# 🛡️ 카톡광고차단 (KakaoTalk Ad Blocker)

[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-24-blue.svg)](https://developer.android.com/about/dashboards)
[![Android Target SDK](https://img.shields.io/badge/Target%20SDK-36-green.svg)](https://developer.android.com/about/dashboards)
[![Release Version](https://img.shields.io/github/v/release/chadolkr/KakaoAdBlocker?color=yellow)](https://github.com/chadolkr/KakaoAdBlocker/releases)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

**카톡광고차단**은 카카오톡 채널 등에서 발송되는 불편한 **광고성 알림 `(광고)`**을 실시간으로 감지하고 자동으로 숨겨주는 안전하고 가벼운 오픈소스 안드로이드 애플리케이션입니다. 

기기 외부로 어떠한 개인정보나 메시지 내용도 전송하지 않으며, 모든 동작이 100% 기기 내에서 로컬로 안전하게 수행됩니다.

---

## ✨ 주요 기능 (Key Features)

- 🚫 **실시간 광고 알림 필터링**: 수신된 카카오톡 알림의 제목 또는 내용이 `(광고)` 표기로 시작하면 즉시 숨김(Dismiss) 처리합니다. `[광고]`, `【광고】`, `( 광 고 )`, 전각 괄호, 앞쪽 이모지·공백, `[Web발신]` 접두어 등 다양한 변형 표기도 인식합니다.
- 🧹 **광고 채팅방 자동 정리 (선택)**: 접근성 권한을 허용하면 광고를 보낸 카카오톡 채널을 자동으로 차단하고, 채팅방을 나가 광고 메세지를 삭제합니다. 채널 채팅방이 아닌 경우(친구 채팅 등)에는 아무 조작도 하지 않습니다.
- 📝 **차단 히스토리 내역**: 차단된 알림의 발신 채널, 광고 내용, 차단 일시를 로컬에 기록하여 사용자가 언제든지 차단 이력을 확인할 수 있습니다.
- ⚡ **원터치 서비스 제어**: 메인 화면의 심플한 스위치를 토글하여 광고 차단 모니터링을 실시간으로 활성화/비활성화할 수 있습니다.
- 🔒 **철저한 프라이버시 보호**: 
  - 어떠한 서버 통신도 존재하지 않습니다.
  - 기본 기능은 **알림 접근 권한(NotificationListenerService)**만으로 작동합니다. 접근성 API(Accessibility API)는 자동 정리 기능을 직접 켠 경우에만 카카오톡 화면에 한해 사용되며, 화면 내용을 저장하거나 전송하지 않습니다.

---

## 🛠️ 권한 요구 사항 (Required Permissions)

애플리케이션이 정상 작동하기 위해 다음 권한의 설정이 필요합니다:
- **알림 접근 권한 (Notification Listener Permission)**: 수신되는 알림을 가로채고 필터링하여 `(광고)`를 차단하기 위한 필수 핵심 권한입니다.
- **접근성 권한 (Accessibility Service, 선택)**: 광고 채널 자동 차단 및 채팅방 나가기 기능에만 필요합니다. Android 13 이상에서 APK로 설치한 경우, 설정 > 애플리케이션 > 카톡광고차단 > ⋮ > **제한된 설정 허용**을 먼저 해야 접근성 권한을 켤 수 있습니다.

---

## 🚀 설치 방법 (Installation)

1. 최신 버전의 설치파일(APK)을 [GitHub Releases](https://github.com/chadolkr/KakaoAdBlocker/releases) 탭에서 다운로드합니다.
2. 기기에서 다운로드한 `KakaoAdBlocker_v<버전>-release.apk` 파일을 실행하여 설치합니다.
   - "출처를 알 수 없는 앱" 안내가 뜨면 설정에서 해당 브라우저/파일 앱의 **이 출처 허용**을 켭니다.
   - Play 프로텍트가 설치를 막으면 **세부정보 더보기 → 무시하고 설치**를 누릅니다.
3. 앱을 실행하고 화면의 안내에 따라 **"알림 접근 권한 설정하기"** 버튼을 클릭하여 권한을 허용해 줍니다.
4. **"실시간 광고 차단"** 스위치를 활성화하면 실시간 모니터링이 시작됩니다.
5. (선택) **"접근성 권한 설정하기"** 버튼으로 접근성 권한을 허용한 뒤, **자동 정리** 카드에서 원하는 기능(채널 차단 / 채팅방 나가기)을 켭니다. 광고가 오면 카카오톡이 잠시 열려 자동으로 정리되며, 화면이 잠겨 있으면 잠금 해제 후 진행됩니다. 앱이 접근성 권한을 켜는 방법(제한된 설정 허용)을 단계별로 안내합니다.
6. (선택) 기능을 켜기 전에 받은 광고는 **기존 광고 채팅방 일괄 정리** 버튼으로 정리할 수 있습니다. 카카오톡 채팅 목록에서 마지막 메세지가 광고인 채널 채팅방을 찾아 차례로 정리합니다.

---

## 💻 개발 및 빌드 환경 (Build Environment)

- **언어**: Kotlin, Jetpack Compose
- **최소 지원 SDK (Min SDK)**: API 24 (Android 7.0)
- **대상 SDK (Target SDK)**: API 36 (Android 16 / Android V)
- **디자인 패턴**: MVVM (Model-View-ViewModel) Architecture
- **빌드 도구**: Gradle (Kotlin DSL)

```powershell
# 프로젝트 릴리즈 빌드 명령어
.\gradlew.bat assembleRelease
```

---

## 📄 라이선스 (License)

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 📧 문의 및 지원 (Contact)

앱 사용 중 문의사항이나 피드백이 있으신 경우 아래 연락처로 연락해 주세요.
- **개발자**: David CHA
- **이메일**: [balance.david.cha@gmail.com](mailto:balance.david.cha@gmail.com)
