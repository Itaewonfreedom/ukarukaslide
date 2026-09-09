# Ukaruka Slide

안 쓰는 Android 휴대폰이나 태블릿을 무료 개인 사진 전시 화면으로 만드는 네이티브 화면 보호기 앱입니다.

## 지금 되는 것

- 기기 MediaStore의 앨범·폴더를 여러 개 골라 전체 사진 랜덤 재생
- Google Photos Ambient API로 앨범을 통째로 연결하고 새 사진 자동 동기화
- 같은 사진 연속 재생을 피하는 셔플
- 자연스러운 크로스페이드와 슬라이드 내내 이어지는 느린 줌·이동 효과
- 10초 / 15초 / 30초 전환 간격
- 슬라이드 위 현재 시각·날짜 표시
- 시계 글꼴, 크기, 위치, 초 표시 설정
- 알림을 비누방울처럼 띄우고 탭해서 열거나 스와이프로 삭제
- 앱 안의 전체 화면 미리보기
- Android DreamService 등록 및 시스템 화면 보호기 설정 바로가기
- 휴대폰과 태블릿의 세로·가로 화면 대응

## 설치와 실행

1. Android Studio에서 이 폴더를 엽니다.
2. JDK 17과 Android SDK 35를 설치합니다.
3. 휴대폰 또는 태블릿에 app을 실행합니다.
4. 앱에서 사진 소스와 전환 간격을 정합니다.
5. **시계 · 알림 · 화면 스타일**에서 오버레이를 정합니다.
6. **시스템 화면 보호기 설정**을 누르고 Ukaruka Slide를 선택합니다.

삼성 기기에서는 보통 **설정 → 디스플레이 → 화면 보호기**에 있습니다. 화면 보호기의 자동 시작 조건은 제조사와 Android 버전에 따라 다를 수 있습니다.

명령행 빌드:

    ./gradlew :app:assembleDebug

GitHub Actions가 푸시마다 Debug APK를 빌드해 artifact로 남깁니다.

## Google Photos 앨범 연결

일반 Android Photo Picker는 개별 사진 권한만 전달하므로 앨범 자동 동기화에 사용할 수 없습니다. Ukaruka Slide는 사진 액자·TV용 공식 Google Photos Ambient API를 사용합니다.

최초 1회 다음 설정이 필요합니다.

1. Google Cloud 프로젝트에서 **Google Photos Ambient API**를 활성화합니다.
2. OAuth 동의 화면을 설정하고 본인 Google 계정을 테스트 사용자로 추가합니다.
3. 애플리케이션 유형이 **TV 및 입력 제한 기기**인 OAuth 클라이언트를 만듭니다.
4. 앱의 **Google Photos 앨범 연결** 화면에 Client ID와 Client secret을 입력합니다.
5. 기기 로그인 후 열리는 Google Photos 화면에서 표시할 앨범을 선택하고 앱에서 동기화합니다.

선택한 앨범별 목록을 페이지 단위로 동기화하며 최대 5,000장을 기기 전용 저장소에 캐시합니다. 화면 보호기나 미리보기를 시작할 때 마지막 동기화 후 한 시간이 지났다면 다시 확인합니다. 자격증명과 토큰은 저장소에 커밋하지 않고 해당 Android 기기에만 저장합니다.

기기 안의 앨범·폴더는 여러 개를 통째로 선택할 수 있고 새 사진도 자동 반영됩니다. Android 14 이상에서는 안정적인 앨범 재생을 위해 사진 권한을 모두 허용해야 합니다.

## 시계와 떠다니는 알림

**시계 · 알림 · 화면 스타일**에서 시계 표시 여부, 날짜와 초, 크기, 글꼴, 네 모서리 위치를 바꿀 수 있습니다.

알림 오버레이는 Android의 알림 접근 권한이 필요합니다. 설정 화면에서 Ukaruka Slide의 알림 접근을 허용하면 화면 보호기 위에 최대 5개의 알림이 떠다닙니다.

- 탭: 알림의 원래 앱 열기
- 좌우 스와이프: 화면 밖으로 던지고 시스템 알림에서도 삭제
- 진행 중 알림과 그룹 요약 알림은 표시하지 않음
- 비누방울 배경 효과는 별도로 끌 수 있음

## 구조

- MainActivity: 사진 소스·간격·스타일 설정 진입점
- PhotoRepository: 기기 앨범과 저장된 사진 조회
- PhotoSourceStore: 사진 소스 설정 영속화
- SlideshowPlayerView: 디코딩, 셔플, 전환 및 이동 효과
- AmbientDisplayView: 사진, 시계, 알림 레이어 합성
- AmbientApi: Google OAuth 기기 흐름, 앨범 목록, 사진 캐시
- FloatingNotificationService: 시스템 알림 수신과 삭제
- SlideshowDreamService: Android 네이티브 화면 보호기
- PreviewActivity: 전체 화면 미리보기

## 개인정보

로컬 사진은 기기 안에서 직접 표시합니다. Google Photos 사진은 Google에서 기기로 직접 받아 전용 저장소에 캐시하며 별도 서버로 업로드하지 않습니다. 떠다니는 알림의 내용도 외부 서버로 보내지 않습니다.

## 라이선스

MIT
