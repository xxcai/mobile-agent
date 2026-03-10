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
