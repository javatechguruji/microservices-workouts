# Rules for beginner-friendly technical guides

Use these rules when I ask you to create or update a topic guide in this project.
My main goal is to understand the concept through my project and explain it
clearly in an interview.

## 1. Audience and language

- Write for beginners in India who use English as a second language.
- Use simple, natural English, short sentences and small paragraphs.
- Explain the topic like a story: what the user does, what problem occurs, how
  the solution works and what happens next.
- Avoid difficult words, unexplained jargon and long textbook definitions.
- Keep necessary technical names, such as CORS, JWT and Kafka. Explain each new
  term in plain English when it first appears.
- Apply these rules everywhere: headings, diagrams, explanations, code comments,
  testing steps and the final summary.
- Keep the explanation technically correct. Simple wording must not change what
  the code actually does.
- Do not assume that a beginner understands a concept after reading a one-line
  definition. Start with a concrete situation, walk through what happens step by
  step, and explain why the result changes or stays the same.
- Use small numbers when useful. For example, explain a counter using failures
  that change its value from 0 to 1 to 2, and explain why a success does not reduce it.
- Use comparison tables as a recap after teaching the ideas, not as a substitute
  for explaining them. Explain essential basics without adding unrelated detail.

Use [the CORS guide](api-gateway/09-cors-preflight-and-browser-security.md) as the
style reference, especially its purpose, explanation below the diagram, testing
steps and final summary.

## 2. Keep the scope focused

- Cover only what is needed to understand, demonstrate and explain the requested
  topic in an interview.
- Do not add unrelated features, extra exercises, repeated explanations or long
  lists of advanced cases.
- Mention a limitation only when it matters to the explanation or test.
- Keep installation, credentials and environment setup in separate setup guides.
  Link to those guides instead of copying their content.
- When editing a guide, preserve my changes. Do not restore sections I removed
  or add new sections unless they are needed for my request.
- Do not change application behavior just to write a guide unless I ask for it.
- Prefer established tools that fit the project ecosystem. Explain one main
  learning path and briefly mention well-known alternatives when relevant. Say
  which tools work together and which replace one another. Do not claim a tool
  is the most widely used without reliable evidence, or require every alternative.

## 3. Read the implementation first

- Check the relevant code and configuration before describing the topic.
- Use the actual service names, API paths, methods and configuration values.
- Link to the files that implement the example.
- Clearly separate what our project implements from a general explanation or a
  possible future improvement. Do not describe planned work as completed work.
- Explain the problem in our scenario and how the implemented solution solves it.

## 4. Use a clear topic order

Use this order when it fits the topic. Do not add a section just to fill a template.
For an existing guide, keep the sections I chose and correct their numbering.

1. **Topic name and purpose:** explain the problem using a small, familiar example.
2. **Request or business flow:** show what happens from the first action to the
   result, with a diagram and a short explanation below it.
3. **Project implementation:** show where the solution is used and the relevant
   code or configuration, with simple comments.
4. **How to test:** give the shortest useful manual test and expected results.
5. **Summary and interview explanation:** finish with a connected explanation
   that I can say naturally in about 3–4 minutes. Avoid repeating a separate summary.

## 5. Diagrams and code examples

- Use a simple Mermaid diagram when it helps explain the flow. Keep labels short
  and explain the diagram below it in plain English.
- Show only the components needed for the topic. Identify the API and service
  when a call is important to understanding the flow.
- If the scenario includes background processing or Kafka, distinguish calls that
  wait for a response from work that happens later. Show the actual topic,
  consumer service, consumer group and listener method where relevant.
- For a guide covering several business scenarios, give each scenario its own
  diagram. Split a large flow into linked diagrams if that makes it easier to read.
- Show only relevant code. A full method is useful when needed, but unrelated
  methods should be represented by a comment such as `// ... other methods omitted ...`.
- Include the class name for context. If another method uses the shown settings
  or object, include only the part that demonstrates that connection.
- Explain important code lines with short, simple comments in the document.
  Keep teaching comments in the document, not in the actual Java class.
- Label shortened code as an excerpt. Do not imply that code with omitted parts
  is a complete replacement for the source file.
- If several services implement the topic, show the relevant snippet from each
  service and explain how they work together.

## 6. Keep testing simple

- Test one clear flow that demonstrates the topic. Add a failure case only if it
  is important to understanding that topic.
- State which applications must be running. Link to setup instructions or login
  credentials when needed; do not repeat the installation guide.
- Say exactly what to open, click or enter and what result to expect.
- Prefer the browser for browser-related topics. For example, a CORS test should
  focus on the browser's OPTIONS request and the actual API request that follows.
- Name the relevant Network tab fields, response headers or status codes and
  explain what the student should look for.
- Do not add Postman, curl or other tools unless they help test the requested topic.
- Include only short troubleshooting notes for likely problems in those steps.

## 7. Write an interview-ready ending

- Use a realistic business example, such as a customer opening My Orders in an
  e-commerce application.
- Explain the problem, the solution, the flow and why the design is useful.
- Use simple connected sentences that sound natural when spoken.
- Use production-style examples such as `shop.example.com` and `api.example.com`
  when addresses help the explanation. Avoid phrases such as “my laptop uses
  port 9100” in the interview answer. Local ports belong in project and test details.
- Do not claim production experience or features that our project does not have.
- Keep the answer focused enough to explain in about 3–4 minutes without filler.

## 8. Final check

Before finishing, check that the guide has simple language, correct numbering,
working file links, valid diagrams and examples that match the code. Remove
repeated or unnecessary content. Confirm that a beginner can follow the flow
without already knowing the terms being taught.

## How I will use this prompt

> Refer to `docs/prompt.md` and prepare a guide for **[topic]** under **[folder]**.
> Explain it using our project's implementation and focus on interview preparation.

For an existing document:

> Refer to `docs/prompt.md` and improve **[document path]**. Preserve my selected
> sections and changes. Simplify the language without adding unnecessary content.
