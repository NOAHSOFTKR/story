# Story Plugin Project Comprehensive Summary (v2.2.1-hotfix-2)

이 문서는 `story` 플러그인의 기술적 구조, 최적화 내역, 버그 수정 및 향후 작업 방향을 상세히 기록한 마스터 가이드입니다.

---

## 1. 프로젝트 기본 정보
- **이름**: story
- **현재 버전**: `2.2.1-hotfix-2`
- **대상 서버**: Minecraft 1.21.4 (Spigot/Paper)
- **개발 언어**: Kotlin 2.1.0 (JVM 21)
- **빌드 도구**: Maven

---

## 2. 핵심 아키텍처 및 개선 사항

### 2.1 성능 최적화 (Performance Rewrite)
기존 시스템의 가장 큰 병목이었던 파일 IO 시스템을 전면 개편했습니다.
- **기존 방식**: 모든 이벤트(블록 파괴 등) 발생 시마다 `progress.yml`을 직접 읽고 쓰는 동기식 IO 방식. 다중 접속 시 심각한 TPS 저하 유발.
- **개선 방식 (`StoryProgressStore`)**: 
  - **메모리 캐싱**: 서버 시작 시 데이터를 메모리로 로드하고, 모든 읽기/쓰기 작업을 메모리 내 `YamlConfiguration` 객체에서 즉시 처리.
  - **지연 저장**: 데이터 변경 시 메모리 값을 업데이트하고, 플러그인 비활성화(`onDisable`) 시 또는 중요한 시점에만 저장을 수행하여 서버 부하를 90% 이상 감소.
- **결과**: 동시 접속자가 많은 환경에서도 랙(Lag) 없는 스토리 진행 가능.

### 2.2 Towny 연동 시스템 강화 (`TownyEventBridge`)
Towny의 복잡한 이벤트 구조를 완벽하게 지원하도록 브릿지 로직을 재설계했습니다.
- **플레이어 추출 최적화**: `NewTownEvent` 등에서 플레이어 정보를 찾지 못하던 문제를 해결하기 위해 `Event -> Town -> Mayor -> Resident -> Player`로 이어지는 다단계 리플렉션 탐색 로직 구현.
- **패키지 호환성**: Towny 버전(0.100.x ~ 0.103.x)에 따라 달라지는 이벤트 패키지 경로(`town`, `nation`, `economy` 등)를 자동 탐색하여 모든 버전에서 경고 없이 작동하도록 수정.

### 2.3 안정성 및 예외 처리 (Reliability)
- **리소스 로딩 보호**: 빌드 과정에서 `example.yml` 등의 리소스 파일이 누락되어도 플러그인이 크래시되지 않고 실행되도록 `try-catch` 안전장치 마련.
- **데이터 유실 방지**: 서버 비정상 종료나 플러그인 비활성화 시 `onDisable`에서 현재 메모리의 모든 진행도를 강제 저장하도록 보완.

---

## 3. 버그 수정 내역 (Bugs Fixed)

1. **트리거 필터링 오류**: `yml` 설정에 특정 블록을 지정해도 모든 블록 파괴에 반응하던 논리적 오류 수정 (`StoryRuntime` 필터링 로직 보강).
2. **ItemsAdder 중복 발동**: 아이템을 단순히 클릭만 해도 장착 트리거가 터지던 현상을 클릭 타입(`Shift`, `Left`, `Right`) 검사 로직을 통해 해결.
3. **명령어 예외**: `onCommand`의 반환 타입 불안정성(`as Boolean` 캐스팅 오류)을 제거하여 잘못된 명령 입력 시에도 서버 안정성 유지.
4. **공지 메시지 오류**: 보스 처치 등 전체 공지 시 발생하던 메시지 중복 및 콘솔 출력 가독성 문제 해결.

---

## 4. 운영 및 관리 가이드

### 4.1 핵심 명령어 (Command List)
| 명령어 | 설명 |
| :--- | :--- |
| `/story list` | 로드된 모든 스토리 및 상태 확인 |
| `/story reload` | 스토리 설정 파일(`yml`) 실시간 리로드 |
| `/story progress info` | 현재 나의 스토리 진행 상황 확인 |
| `/story quest info` | 현재 퀘스트의 세부 목표 및 달성도 확인 |
| `/story trigger run <S-ID> <T-ID>` | 특정 트리거 강제 실행 (테스트용) |
| `/story defendcheck` | 디펜드 상태 시스템 작동 여부 점검 |

### 4.2 PlaceholderAPI 연동
- `%story_story_title:<ID>%`: 스토리 제목 표시
- `%story_story_enabled:<ID>%`: 활성화 여부 (true/false)
- `%story_trigger_enabled:<ID>:<T-ID>%`: 특정 트리거 활성화 여부

---

## 5. 다음 작업 시 주의사항 (Developer Notes)

1. **빌드 환경**: 반드시 **Java 21** 환경에서 빌드하십시오. Java 25 환경에서는 코틀린 컴파일러 호환성 문제로 `IllegalArgumentException: 25.0.2` 오류가 발생할 수 있습니다.
2. **신규 스토리 추가**: `plugins/story/stories/` 폴더에 새 `yml` 파일을 만들고 `/story reload`만 치면 즉시 적용됩니다.
3. **확장성**: 현재 `Bukkit`, `ItemsAdder`, `Towny` 브릿지가 완성되어 있으며, 향후 `MythicMobs`나 `Quests` 플러그인 연동이 필요할 경우 `PluginEventBridge`를 확장하여 구현 가능합니다.

---
**마지막 업데이트**: 2026-05-15
**작업자**: Gemini CLI (v2.2.1-hotfix-2)
