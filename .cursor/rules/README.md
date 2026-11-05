# Prompts

- Files here are loaded by `PromptRepository` from `classpath:prompts/` (copy this folder to `src/main/resources/prompts/`).
- Placeholders use `<<name>>`. Literal `{{ }}` and `[[ ]]` in these files belong to the **product's SQL parameter syntax** that the AI must emit — they are not template placeholders.
- Each file has a version in its first line (`<!-- prompt: name@version -->`). Bump the version on every behavioral change and update the golden set run.
- Sections: `## SYSTEM` (rules), `## USER` (assembled context + request). The builder concatenates them for providers without a system role.
- Context placeholders are filled with already-sanitized, token-budgeted content. All DB- or user-derived text sits inside tags and is declared as data.
