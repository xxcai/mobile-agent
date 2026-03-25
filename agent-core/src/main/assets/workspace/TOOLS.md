# Tool Usage Notes

Tool schemas are provided automatically via function calling.
This file documents constraints and usage patterns.

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

When the user asks about page elements, visible cards, buttons, positions, or the current screen structure:

1. Prefer `android_view_context_tool` first to inspect the screen.
2. Use `android_gesture_tool` only after the screen context is clear enough for execution.
3. Keep business tools as the first choice only when they can directly express the target action without screen inspection.

Do not jump directly to gesture execution when the task still depends on understanding the current UI structure.

## Gesture Tool Rules

For `android_gesture_tool`, always follow these rules:

1. Call `android_view_context_tool` first in the same turn.
2. Use the latest `observation.snapshotId` from that result.
3. Do not guess raw coordinates.
4. Do not retry the same gesture without new screen evidence.

For `tap`:

- Prefer the target from the latest observation.
- Include `observation.snapshotId`.
- Include `observation.referencedBounds` whenever possible.
- If there is no fresh observation for the target, do not call `tap`.

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

For route selection in the main conversation:

1. Decide the first path inside the main response flow rather than waiting for any separate pre-routing step.
2. If a stable business entity is explicit, prefer the corresponding business tool first.
3. If the target is a visible UI element or the request depends on current screen structure, prefer `android_view_context_tool` first.
4. If a business tool returns a structured capability or target-access failure and fallback is clearly allowed, then move to `android_view_context_tool` and `android_gesture_tool`.

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
