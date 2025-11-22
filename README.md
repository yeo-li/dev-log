# dev-log

## 핵심 기능 정의

---

### ✅메모 저장 기능

- 메모를 저장할 수 있어야 한다.
- 메모의 저장 데이터는 다음과 같다.
    - `int id (not null)`: 메모의 고유한 아이디(PK)
    - `String content (not null)`: 사용자가 입력하는 메모의 내용
    - `LocalDateTime createdAt (not null)`: 메모가 생성된 시간
    - `LocalDateTime updatedAt (not null)`: 메모가 수정된 시간
    - `String commitHash (nullable)`: 현재 커밋의 해시값
    - `String filePath (nullable)`: 파일 경로
    - `String selectedCodeSnippet (nullable)`: 선택된 코드
    - `long selectionEnd (nullable)`: 문서에서 선택한 정확한 종료 위치
    - `long selectionStart (nullable)`: 문서에서 선택한 정확한 시작 위치
    - `int visibleEnd (nullable)`: 선택한 종료 줄
    - `int visibleStart (nullable)`: 선택한 시작 줄
- 저장은 시간 순서대로 저장이 되어야 한다.

### ⚠️ 메모 저장 기능 고려 사항

- 메모의 `content`가 blank라면 저장이 되어선 안된다.
- `selectionStart`가 `selectionEnd`보다 앞에 있어야 한다.
- `visibleStart`가 `visibleEnd`보다 앞에 있어야 한다.
- `createdAt`은 자동으로 생성 되어야 한다.

---

### ✅ 메모 조회 기능

- 전체 메모를 날짜순으로 조회할 수 있어야 한다.

### ⚠️ 메모 조회 기능 고려 사항

- 각 조건마다 조회할 데이터가 없다면 빈 리스트를 반환해야 한다.

---

### 🏁메모 삭제 기능

- 저장된 메모를 삭제할 수 있어야 한다.
- 메모를 한 번에 여러개 삭제할 수 있어야 한다.

### ⚠️ 메모 삭제 기능 예외 상황

- 삭제할 메모가 없으면 아무 수행도 해선 안된다.

---

### ☑️ 메모 수정 기능

- 저장된 메모의 content를 수정할 수 있어야 한다.

### ⚠️ 메모 수정 기능 예외 상황

---

### ☑️ 메모 추출 기능

- 저장된 메모를 한 개 이상 선택하여 txt 파일로 추출할 수 있어야 한다.
- 추출한 단위 메모의 구성 내용은 다음과 같다.
    - 메모 순서(시간순)
    - `LocalDateTime timestamp`: 메모가 생성된 시간
    - `String content`: 사용자가 입력하는 메모의 내용
    - `String filePath`: 파일 경로
    - `String commitHash`: 현재 커밋의 해시값
    - `int visibleStart`: 선택한 시작 줄
    - `int visibleEnd`: 선택한 종료 줄
- txt 파일 상단에 프로젝트명, 내보낸 시각, 메모의 개수가 있어야 한다.
- txt 파일의 이름은 `devlog-{프로젝트명}-{내보낸날짜}-{내보낸시각}.txt` 이다.
- 추출할 메모가 없으면 빈 txt 파일을 반환한다.

### ⚠️ 메모 추출 기능 예외 상황

---

### ☑️ 노트 수정/저장 기능

- 노트를 저장할 수 있어야 한다.
- 노트의 수정/저장 데이터는 다음과 같다.
    - `String content`: 노트의 내용
    - `LocalDateTime savedAt`: 저장된 시각

### ⚠️ 노트 수정/저장 기능 예외 상황

---

## 화면 요구 사항

---

### ☑️ 플러그인 기본 화면

- 화면의 최상단엔 기본 조작용 버튼이 있어야 한다.
- 기본 조작용 버튼은 아래와 같다.
    - 메모목록/노트 화면 전환 버튼
    - 메모 전체 선택/선택해제 버튼
    - 선택된 메모 추출 버튼
    - 선택된 메모 삭제 버튼
- 메인 컨텐츠를 표시하는 화면이 있어야 한다.

### ☑️ 메모 목록 출력 화면

- 저장된 메모 전체가 최근순 정렬되어 화면에 보여야 한다.
- 각 메모 좌측에는 메모를 선택할 수 있는 체크박스가 있어야 한다.
- 화면 하단에는 새 메모를 적을 수 있는 텍스트 입력창이 있어야 한다.
- 에디터 화면에 코드가 선택(드래그) 되어있으면, 코드가 선택되었음을 일목 요연하게 표시해야 한다.
- 저장된 메모 중 선택된 코드가 있는 메모들을 없는 메모와 다르게 표시해야 한다.
- 새로운 메모는 mac에선 커멘드+엔터, window에서는 컨트롤+엔터로 저장 가능해야 한다.

### ⚠️ 메모 목록 출력 화면 예외 상황

---

### ☑️ 노트 출력 화면

- 저장된 노트의 모든 내용이 출력 되어야 한다.
- 노트의 변경 내용은 자동 저장 되어야 한다.
- 화면의 크기를 벗어나지 않게 내용을 출력해야 한다.

### ⚠️ 노트 출력 화면 예외 상황

<!-- Plugin description -->
DevLog Plugin is a memo/note tracking plugin that automatically captures your code selection,
file path, commit hash, and editor state, storing it for later use.
<!-- Plugin description end -->