# gateway-service: project revision questions

Use [the implementation guide](../docs/api-gateway/topic-list.md) to answer each question by tracing
the actual source and an observable outcome. These questions replace older
checklists that described already-implemented features as pending.

1. Why must a forged admin header be removed?
2. Why is client credentials different from token relay?
3. Which paths are public and which component authorizes an order?

Compare the implementation with its stated limitations. Future work is maintained
once in [the project roadmap](../Topics.md); a discussion topic is not proof
that the feature exists. For a runnable exercise use
[manual verification](../docs/project-docs/manual-verification.md).
