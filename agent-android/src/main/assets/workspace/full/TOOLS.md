# Tool Usage Notes

Tool schemas are provided automatically via function calling.
This file documents tool-side constraints, failure semantics, and execution rules.

## General Constraints

- Tool results truncated at 40,000 characters
- No shell access (Android sandbox)
- All tools run locally on device

## Error Handling

Tools may return errors. Common patterns:

```json
{"success": false, "error": "description of error"}
```

Always check the `success` field before processing results.

When a tool returns a structured failure result, interpret it in this order:

1. Check `success` first.
2. If `success` is `false`, prefer `suggestedNextAction` to decide the next step.
3. Use `failureType` to determine whether the failure is a capability boundary or only an execution problem.
4. Treat `fallback=vision` only as an additional confirmation that vision fallback is allowed.

Use vision or UI-based tools only when the failure clearly indicates that:

- the business capability does not support this target action, or
- the current target object cannot be handled through business parameters and the tool explicitly allows vision fallback.

Do not switch to vision or UI-based tools directly when the failure is caused by:

- temporary execution failure
- permission denied
- missing or invalid arguments

If the tool result does not include clear structured fallback fields, do not assume that vision fallback is allowed by yourself.

## Gesture Tool Rules

When execution depends on the current UI structure, obtain fresh screen evidence before any gesture attempt.

For `android_gesture_tool`, always follow these rules:

1. Call `android_view_context_tool` first in the same turn.
2. Use the latest `observation.snapshotId` from that result.
3. Do not guess raw coordinates.
4. Do not retry the same gesture without new screen evidence.
5. Stop exploring when repeated tool results no longer produce new evidence.

For `tap`:

- Prefer the target from the latest observation.
- `tap` must include integer `x` and `y`.
- Derive `x` and `y` from the target bounds in the latest observation.
- Include `observation.snapshotId`.
- Include `observation.referencedBounds` whenever possible.
- If there is no fresh observation for the target, do not call `tap`.
- If you cannot identify fresh bounds for the target from the current-turn observation, do not call `tap`.
- If `tap` fails with `missing_view_context_observation` or `missing_view_context_snapshot_id`, the next step must be `android_view_context_tool` before any new gesture attempt.
- If `tap` fails with `invalid_args`, do not retry until the missing required fields are corrected.

For `swipe`:

- Use `swipe` only for scrolling a specific visible container.
- Always include:
  - `direction`: `up` or `down`
  - `amount`: `small`, `medium`, `large`, or `one_screen`
  - `observation.snapshotId`
  - `observation.referencedBounds`
- `observation.referencedBounds` must point to the container you want to scroll.
- Do not pass raw swipe coordinates such as `startX/startY/endX/endY`.
- Do not rely on the runtime to guess which list or feed to scroll when multiple scroll containers are visible.
- If a swipe result shows no movement or no change in the same container, treat it as no new progress in that direction.
- Do not repeat the same swipe on the same container unless fresh evidence suggests the content changed.
- When repeated observation produces no new relevant items, stop exploring and answer with the best result already supported by the evidence.

Example `swipe`:

```json
{
  "action": "swipe",
  "direction": "down",
  "amount": "small",
  "observation": {
    "snapshotId": "obs_xxx",
    "referencedBounds": "[0,287][1216,2381]",
    "targetDescriptor": "朋友圈列表"
  }
}
```

For feed, list, and time-range tasks:

1. The goal is to gather enough evidence to answer, not to exhaust every possible item on the screen.
2. Once relevant items are found and further attempts do not change the result, summarize and stop.
3. Do not keep repeating the same observation or scroll action only to confirm a possibility when the evidence is no longer changing.

## Tool Categories

### File Operations
- Read, write, list files within workspace
- Search for text patterns
- No access outside workspace directory

### Memory Tools
- Save and retrieve long-term memories
- Search conversation history

## Best Practices

1. Validate tool arguments before calling
2. Handle large results by processing incrementally
3. Provide clear error messages to users
