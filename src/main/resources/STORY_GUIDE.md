# Story Guide

## Overview

This plugin loads every `yml` or `yaml` file under `plugins/story/stories/`.

Each file should describe one story.

## File Layout

```yml
id: arcana-towny
name: Arcana Towny Guide
enable: true
trigger:
  id: town-create
  name: Town creation
  enable: true
  type: MANUAL
  event: towny_town_create
  description: Manually test the town creation flow for this story.
actions:
  message:
    id: message
    enable: true
    content: "&a[Story] Running town-create for arcana-towny"
    toSend: triggeredPlayer
  commandsEnable: true
  commands:
    - "say arcana-towny trigger fired by {player}"
```

## Top-level Keys

`id`
- Required.
- Must be unique across all loaded story files.
- Used in commands and placeholders.

`name`
- Optional.
- Display name shown in `/story list`.
- If omitted, `id` is used.

`enable`
- Optional.
- Default: `true`
- Enables or disables the whole story.

`trigger`
- Optional, but needed if you want the story to react to an event or be run manually.
- Current code supports one trigger per story file.

`actions`
- Optional.
- Contains the actions that run when the trigger fires.

## Trigger Keys

`trigger.id`
- Optional.
- Trigger identifier used in commands such as `/story trigger run <storyId> <triggerId>`.
- If omitted, `trigger.event` is used.

`trigger.name`
- Optional.
- Display name for trigger output.
- If omitted, trigger id is used.

`trigger.enable`
- Optional.
- Default: `true`
- Enables or disables the trigger.

`trigger.type`
- Optional.
- Current supported value:
  - `MANUAL`
- If omitted, defaults to `MANUAL`.

`trigger.event`
- Required for event-driven behavior.
- Event key matched by the runtime bridge.

`trigger.description`
- Optional.
- Sent to the player when the trigger runs.

`trigger.block`
- Optional.
- Only applies to `block_break` events.
- Filters the trigger to fire only when the specified block is broken.
- Format: `namespace:name` (e.g. `minecraft:stone`, `minecraft:oak_log`, `itemsadder:ruby_block`) or plain material name (e.g. `STONE`).
- If omitted, the trigger fires for any block break.

`trigger.item`
- Optional.
- Only applies to `itemsadder_item_pickup` and `itemsadder_item_equip` events.
- Filters the trigger to fire only when the specified item is involved.
- Format: `namespace:name` (e.g. `itemsadder:ruby`).
- If omitted, the trigger fires for any item.

## Actions Keys

### `actions.message`

Sends a message when the trigger fires.

```yml
actions:
  message:
    id: message
    enable: true
    content: "&aHello {player}"
    toSend: triggeredPlayer
```

`actions.message.id`
- Optional.
- Default: `message`

`actions.message.enable`
- Optional.
- Default: `true`

`actions.message.content`
- Optional.
- Message body.
- Supports `&` color codes.
- Supports:
  - `{player}`
  - `{sender}`

`actions.message.toSend`
- Optional.
- Supported values:
  - `triggeredPlayer`
  - `everyone`
- Any unknown value behaves like `triggeredPlayer`.

### `actions.commands`

Runs console commands when the trigger fires.

```yml
actions:
  commandsEnable: true
  commands:
    - "say hello {player}"
    - "tellraw {player} {\"text\":\"story fired\"}"
```

`actions.commandsEnable`
- Optional.
- Default: `true`

`actions.commands`
- Optional.
- A list of console commands.
- Supports:
  - `{player}`
  - `{sender}`

## Supported Action Ids

These ids are used by command toggles and PlaceholderAPI lookups.

`message`
- The `actions.message` block.

`commands`
- The `actions.commands` list.

## Commands

`/story list`
- Lists loaded stories.

`/story reload`
- Reloads all story files from the stories folder.

`/story enable <storyId>`
- Enables a story.

`/story disable <storyId>`
- Disables a story.

`/story trigger list <storyId>`
- Lists triggers for a story.

`/story trigger run <storyId> <triggerId>`
- Runs a trigger manually.

`/story trigger enable <storyId> <triggerId>`
- Enables a trigger.

`/story trigger disable <storyId> <triggerId>`
- Disables a trigger.

`/story action list <storyId> <triggerId>`
- Lists actions for a trigger.

`/story action enable <storyId> <triggerId> <actionId>`
- Enables an action.

`/story action disable <storyId> <triggerId> <actionId>`
- Disables an action.

## PlaceholderAPI

When PlaceholderAPI is installed, the plugin exposes:

`%story_last_update%`
- Last internal refresh timestamp in milliseconds.

`%story_story_enabled:<storyId>%`
- Returns `true` or `false`.

`%story_story_title:<storyId>%`
- Returns the story display name.

`%story_trigger_enabled:<storyId>:<triggerId>%`
- Returns `true` or `false`.

`%story_action_enabled:<storyId>:<triggerId>:<actionId>%`
- Returns `true` or `false`.

Story, trigger, and action enable/disable commands refresh PlaceholderAPI state.

## Towny Event Keys

If Towny is installed, these `trigger.event` values are currently connected:

`towny_town_create`
- `NewTownEvent`

`towny_town_claim`
- `TownClaimEvent`

`towny_town_unclaim`
- `TownUnclaimEvent`

`towny_town_add_resident`
- `TownAddResidentEvent`

`towny_town_remove_resident`
- `TownRemoveResidentEvent`

`towny_nation_create`
- `NewNationEvent`

`towny_town_transaction`
- `TownTransactionEvent`

`towny_nation_transaction`
- `NationTransactionEvent`

## Rules

- Every story file must have a unique `id`.
- Only one trigger is currently loaded from each story file.
- The plugin loads all files under the stories folder recursively.
- If Towny or PlaceholderAPI is not installed, the plugin skips those integrations safely.

## Recommended Pattern

Create one file per story:

`plugins/story/stories/arcana-towny.yml`

`plugins/story/stories/desert-kingdom.yml`

`plugins/story/stories/frozen-port.yml`

This keeps ids, trigger settings, and actions easy to manage.
