# Main Story Runtime Contract

This directory is the source-managed location for deployed Story YAML. `story/actions` and the
repository-level `stroyFiles` directory are templates only; they are not extracted or loaded by the
plugin at runtime.

## Trigger types

- `EVENT` is the default. It can run only through a registered event bridge.
- `MANUAL` can run only through `/story trigger run` and still uses the same current-stage,
  completion, filter, and persistent-save checks as an automatic trigger.

`/story trigger run`, reloads, enable/disable actions, and direct progress changes require
`story.admin` (OP by default). There is intentionally no general-player force-run command.

## Persistent state

The plugin persists `currentStoryId`, objective values, completed story IDs, completion flags, and
one-time completed trigger IDs per player in `progress.yml`. A trigger's state is committed before
messages or console commands run; if saving fails, no action or completion reward is sent.

Use these YAML keys for a linear stage:

```yml
previousStory: arcana-chapter-1
requiredFlags: [chapter_1_completed]
completionFlags: [chapter_2_completed]
quest:
  enable: true
  objectives:
    - id: settlement
      target: 1
triggers:
  - id: settlement-complete
    type: EVENT
    event: towny_town_create
    objectiveId: settlement
```

Use `completeStory: true` only for a trigger that is itself the verified completion condition.
Use `repeatable: true` only for verified replayable dungeon or raid entry/content; it does not grant
the initial completion reward again.

## Filters

`block`, `item`, `mob`, `quest`, `cutscene`, `cutsceneKey`, `cutsceneReason`, `container`, and the
four `armor.*` fields can be set directly under a trigger. Arbitrary filters can also be set under
`filters`. For `itemsadder_item_equip`, all four armor fields are mandatory.

```yml
event: cutscene_end
cutscene: arcana-prologue-exile
cutsceneReason: COMPLETED
completeStory: true

# A designated chest transfer: world:x:y:z and exact item ID
event: block_interact_chest
container: world:100:64:-25
item: namespace:configured_item

# Full, actually-equipped armor set
event: itemsadder_item_equip
armor:
  helmet: namespace:helmet
  chestplate: namespace:chestplate
  leggings: namespace:leggings
  boots: namespace:boots
```

The pending Chapter 1 and 2 files remain disabled because the repository does not contain the
authoritative ItemsAdder, NPC, world, farming/fishing/sales, or Towny deployment settings. Fill and
test those values before enabling them; do not replace them with guessed IDs or coordinates.
