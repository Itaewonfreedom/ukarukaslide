# Ukaruka Slide

안 쓰는 Android 휴대폰이나 태블릿을 무료 사진 액자로 만드는 네이티브 화면 보호기 앱입니다.

## 지금 되는 것

- 기기 `MediaStore`의 앨범·폴더를 여러 개 골라 전체 사진 랜덤 재생
- Android 시스템 사진 선택기에서 로컬 또는 Google Photos 사진을 최대 100장 선택
- 같은 사진 연속 재생을 피하는 셔플
- 크로스페이드와 느린 줌/이동(Ken Burns) 효과
- 10초 / 15초 / 30초 전환 간격
- 앱 안의 전체 화면 미리보기
- Android `DreamService` 등록 및 시스템 화면 보호기 설정 바로가기
- 휴대폰과 태블릿의 세로·가로 화면 대응

## 설치와 실행

1. Android Studio에서 이 폴더를 엽니다.
2. JDK 17과 Android SDK 35를 설치합니다.
3. 휴대폰 또는 태블릿에 `app`을 실행합니다.
4. 앱에서 사진 소스와 전환 간격을 정합니다.
5. **시스템 화면 보호기 설정 열기**를 누르고 `Ukaruka Slide`를 선택합니다.

삼성 기기에서는 보통 **설정 → 디스플레이 → 화면 보호기**에 있습니다. 화면 보호기의 자동 시작 조건(충전 중/도킹 중)은 제조사와 One UI 버전에 따라 다를 수 있습니다.

명령행 빌드는 다음처럼 실행합니다.

```bash
./gradlew :app:assembleDebug
```

GitHub Actions가 푸시마다 Debug APK를 빌드해 artifact로 남깁니다.

## Google Photos 범위

현재 버전은 Android Photo Picker를 사용합니다. 기기에서 Google Photos를 클라우드 미디어 제공자로 설정했다면 사진 선택 화면 안에서 Google Photos의 앨범·검색·즐겨찾기를 열 수 있습니다. 앱은 사용자가 명시적으로 고른 사진만 읽습니다.

Android Photo Picker는 선택한 개별 사진의 읽기 권한만 앱에 전달하므로, Google Photos 앨범 자체를 저장하거나 앨범에 나중에 추가된 사진을 자동으로 가져올 수는 없습니다. 반면 기기 안의 앨범·폴더는 현재 버전부터 여러 개를 통째로 선택할 수 있고 새 사진도 자동 반영됩니다.

“Google Photos 앨범 하나를 연결하고, 나중에 추가되는 사진까지 자동 반영”은 Google Photos **Ambient API** 연동이 필요합니다. 이 API는 사진 액자/TV용 앨범 선택과 미디어 목록 조회를 공식 지원하지만 다음 설정이 선행되어야 합니다.

- Google Cloud 프로젝트
- Google Photos Ambient API 활성화
- TV 및 제한 입력 기기용 OAuth 2.0 클라이언트 ID
- 기기 등록, 사용자 승인, 토큰 갱신 구현

자격 증명은 저장소에 커밋하지 않습니다. Ambient API 연동은 다음 마일스톤으로 분리합니다.

## 구조

- `MainActivity`: 사진 소스·간격 설정
- `PhotoRepository`: 기기 앨범과 선택 URI 조회
- `PhotoSourceStore`: 설정 영속화
- `SlideshowPlayerView`: 디코딩, 셔플, 전환 및 이동 효과
- `SlideshowDreamService`: Android 네이티브 화면 보호기
- `PreviewActivity`: 전체 화면 미리보기

## 개인정보

사진은 기기 안에서 직접 표시되며 별도 서버로 업로드하지 않습니다. 네트워크 권한도 선언하지 않습니다.

## 라이선스

MIT
