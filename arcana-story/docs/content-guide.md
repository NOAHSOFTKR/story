# ArcanaStory 콘텐츠 가이드

ArcanaStory 콘텐츠는 content root 아래의 YAML 파일로 작성합니다.

```text
<content root>/
├─ chapters/**/*.yml    # 파일 1개 = Chapter 1개
└─ cutscenes/**/*.yml   # 파일 1개 = Cutscene 1개
```

실제로 동작하는 예시는 [`samples/content`](../samples/content)에 있으며, 테스트에서 경고 없이 검증을 통과하는지 확인합니다.

## 원칙

- **게임플레이 판정은 Quest가 합니다.** 블록 파괴, 몹 처치, 지역 진입, 아이템 획득 같은 조건은 Quest objective(STORY scope)로 정의하고, Chapter는 `quest_complete` / `quest_objective_complete` trigger로 결과만 기다립니다.
- **Chapter id는 영구 History 키입니다.** `prologue.exile`처럼 의미 있는 id를 쓰고, 번호·표시 순서는 `arc`/`order`로만 표현합니다. id를 바꿔야 하면 이전 id를 `aliases`에 남깁니다.
- **추측한 id·좌표를 넣지 않습니다.** 검증을 통과하지 못한 콘텐츠는 활성화하지 않습니다.

## Chapter

| 키 | 필수 | 설명 |
|---|---|---|
| `id` | ✅ | `소문자_숫자.점구분` (예: `prologue.exile`) |
| `aliases` | | 이전 id 목록 |
| `title`, `arc`, `order` | | 표시용 메타데이터 |
| `prerequisites` | | `chapters: [id]`, `branches: ["<chapterId>:<branchId>"]`, `flags: [flag]` |
| `entry` | ✅ | `{ step, location }` — 시작 step과 시작 위치 |
| `locations` | | `id: { world, x, y, z, yaw?, pitch? }` |
| `branches` | | `id: { title }` |
| `choices` | | `id: { prompt, options: [{ id, text, branch? }] }` |
| `checkpoints` | | `id: { location, replay_cutscene? }` |
| `dialogues` | | `id: { start, nodes: { nodeId: { speaker, lines, actions, next \| choice } } }` |
| `steps` | ✅ | 순서 있는 step 목록 |
| `rewards` | | `first_clear: [...]`, `branches: { branchId: [...] }` |

내부 id(step, location, branch, choice, option, checkpoint, dialogue, node, flag)는 `소문자·숫자·밑줄`만 사용합니다.

### Step

```yaml
steps:
  - id: hide_luminite
    actor: leader          # leader(기본) | any | all — 파티 세션에서 누구의 이벤트가 진행시키는지
    on_enter: [ ...actions ]
    await:
      - type: quest_complete
        quest: story_prologue_hide_luminite
        actions: [ ...actions ]
        next: exile        # 생략하면 step의 next
    next: null
```

`await`가 없으면 `on_enter` 실행 후 바로 `next`로 넘어갑니다.

### Trigger

| type | 필수 값 | 선택 값 |
|---|---|---|
| `dialogue_end` | `dialogue` | |
| `cutscene_end` | `cutscene` | |
| `choice_made` | `choice` | `option` |
| `timer` | `ticks` | |
| `quest_start` / `quest_complete` / `quest_fail` | `quest` | |
| `quest_objective_complete` | `quest`, `objective` | |
| `custom` | `key` | |

### Action

| type | 필드 |
|---|---|
| `message` | `text`, `target: self \| session` |
| `title` | `title`, `subtitle`, `fade_in`, `stay`, `fade_out` |
| `actionbar` | `text` |
| `sound` | `sound`(예: `minecraft:block.amethyst_block.hit`), `volume`, `pitch` |
| `particle` | `particle`, `count`, `offset_x/y/z`, `speed` |
| `effect` | `effect`, `duration`(tick), `amplifier` |
| `teleport` | `location` |
| `wait` | `ticks` |
| `sequence` | `actions` |
| `dialogue` / `cutscene` / `checkpoint` / `choice` | 각 id |
| `set_flag` | `flag`, `value`(기본 `true`), `persist`(true면 Chapter 완료 시 영구 저장) |
| `clear_flag` | `flag` |
| `start_quest` / `fail_quest` / `reset_quest` | `quest` |
| `complete_objective` | `quest`, `objective` |
| `spawn_npc` | `npc`, `location` |
| `despawn_npc` | `npc` |
| `spawn_mob` | `mob`, `location`, `amount` |
| `clear_inventory` | — **같은 목록에서 `checkpoint` 바로 뒤에만 허용** |
| `fail` | `reason` — 직전 checkpoint(없으면 entry)로 되돌림 |
| `complete_chapter` | — |
| `command` | `command` — escape hatch(경고) |

### 보상

```yaml
rewards:
  first_clear:
    - { type: item, item: "arcana:tattered_material", amount: 3 }
    - { type: experience, points: 50 }
  branches:
    distrust:
      - { type: item, item: "arcana:corrupted_luminite" }
```

- 보상 키는 `<chapterId>.first_clear`, `<chapterId>.<branchId>.first_clear`이며 최초 1회만 지급됩니다(재플레이 시 미지급).
- 허용: `item`, `experience`
- **금지(검증 오류)**: `command`, `money` — 크래시 후 중복 지급을 막을 수 없음
- **금지(검증 오류)**: `title`, `cosmetic`, `unlock` — ArcanaCore 멱등 grant API 제공 전까지

## Chapter에서 쓰는 Quest 규칙

Quest 정보를 조회할 수 있는 환경에서 검증하면 다음을 강제합니다.

- scope가 `STORY`
- `rewards` 없음 (보상은 Chapter `rewards`로)
- `abandon_allowed: false`
- Quest `branch` 미사용 (분기는 Chapter `choices`로)

## Cutscene

```yaml
id: prologue.memory_wipe
name: "기억 소거"
freeze: true
vanilla:
  - { type: effect, effect: BLINDNESS, duration: 240, amplifier: 5 }
  - { type: wait, ticks: 20 }
mod:
  track: "arcana:prologue/memory_wipe"   # 선택
```

`vanilla` step은 CutThin step 형식을 따릅니다. **`clear_inventory`, `command` step은 사용할 수 없습니다.**

## 레거시 매핑

`legacyStoryId → chapterId` 매핑은 콘텐츠와 분리해 `src/main/resources/migration/legacy-story-mapping.yml`에서 관리합니다. Chapter 배치가 확정되기 전에는 `chapter: TBD`로 두며, 진행 데이터 이관 시 TBD 항목은 건너뜁니다.
