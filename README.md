# story / ArcanaStory

Arcana 서버의 스토리 플러그인 저장소입니다. 두 개의 Maven 모듈로 구성됩니다.

| 모듈 | 산출물 | 대상 서버 | 상태 |
|---|---|---|---|
| [`legacy-story/`](legacy-story) | `story-<version>.jar` | 기존 운영 서버 | 기존 `story` 플러그인. 코드는 수정하지 않으며, ArcanaStory 전환 완료 후 모듈째 삭제합니다. |
| [`arcana-story/`](arcana-story) | `ArcanaStory-<version>.jar` | Story Paper 전용 | Chapter / Session / Dialogue / Cutscene / Checkpoint 엔진 (개발 중) |

범용 Quest 처리(Objective, Requirement, Reward 등)는 [`NOAHSOFTKR/quest`](https://github.com/NOAHSOFTKR/quest)가 담당하며, ArcanaStory는 QuestAPI를 통해서만 연동합니다.

## 빌드

Java 21과 Maven이 필요합니다.

```bash
mvn -B verify
```

- `legacy-story`가 사용하는 `kr.kjh9211:cutthin`은 저장소의 `libs/` 디렉터리를 로컬 Maven 저장소로 사용해 해석합니다. 별도의 `install:install-file` 단계가 필요하지 않습니다.
- `arcana-story`는 Paper API `1.21.4`를 대상으로 컴파일하고, MockBukkit으로 테스트합니다.

## ArcanaStory 배포 조건

- `plugins/ArcanaStory/config.yml`의 `server.realm`은 `STORY`여야 합니다.
- Quest 플러그인이 설치되어 있다면 Quest의 `server.realm`도 `STORY`여야 합니다. 둘 중 하나라도 맞지 않으면 ArcanaStory는 스스로 비활성화됩니다(Survival 서버 오배포 방지).
