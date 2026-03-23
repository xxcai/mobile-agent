# Tool Usage Notes

Tool schemas are provided automatically via function calling.
This file documents constraints and usage patterns.

## General Constraints

- Tool results truncated at 10,000 characters
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
