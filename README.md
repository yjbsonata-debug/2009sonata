# SONATA DRIVE 1.0.1 — Android 10 차량용

1280x720 가로 고정 차량 헤드유닛용 프로젝트입니다.

## 이번 빌드 설정 수정
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Java 17
- compileSdk 35 / targetSdk 29 / minSdk 23
- AndroidX AppCompat 1.7.0 / Material 1.12.0
- Gradle 버전 충돌을 피하도록 Gradle Wrapper 설정을 명시했습니다.

## Android Studio
1. 이 폴더(`SonataDrive`)를 Android Studio에서 Open 합니다.
2. Gradle JDK를 **17**로 선택합니다.
3. `Gradle Sync`를 실행합니다.
4. `Build > Build APK(s)`를 실행합니다.
5. Debug APK: `app/build/outputs/apk/debug/app-debug-debug.apk`
   (Android Studio가 variant 이름을 다르게 표시하면 `app-debug.apk`일 수 있습니다.)

## 차량 설치
Android 10 헤드유닛에 APK를 복사하여 설치합니다. 앱은 Landscape 방향으로 고정됩니다.

## 중요
이 프로젝트는 OBD 통신 프로토타입입니다. XTOOL의 정확한 Bluetooth 프로파일/ELM327 호환성은 실제 장치로 검증해야 합니다. DUDU7 내장 OBD 서비스가 같은 Bluetooth 어댑터를 점유하면 동시에 연결되지 않을 수 있습니다.

P2096 삭제는 원인 수리가 아닙니다. Mode 04로 코드를 삭제하면 readiness monitor가 초기화될 수 있으므로 반복 자동삭제는 하지 않습니다.
