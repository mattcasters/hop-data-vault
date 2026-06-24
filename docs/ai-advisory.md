# AI advisory for Data Vault modeling

The Hop Data Vault plugin can call a large language model to help with source analysis, type mapping, modeling, Hop integration, and troubleshooting. Advice is read from your open `.hdv` model, catalog record definitions, and optional check results. **Proposed model edits require explicit confirmation before they are applied.**

## Prerequisites

1. Install the Hop **Language Model Chat** transform (`hop-transform-languagemodelchat`) in your Hop assembly (bundled via this plugin's `dependencies.xml`).
2. Configure AI under **Hop GUI → Configuration → Data Vault 2.0**.

## Grok (xAI) setup

xAI exposes an [OpenAI-compatible API](https://docs.x.ai/docs/guides/chat-completions). In plugin configuration:

| Setting | Value |
|---------|--------|
| Enable AI advisory | On |
| AI provider preset | **Grok (xAI)** |
| AI API key | Your key from [console.x.ai](https://console.x.ai) |
| AI base URL override | Leave empty (defaults to `https://api.x.ai/v1`) |
| AI model name override | e.g. `grok-4` (or leave empty for preset default) |

Other presets (OpenAI, Anthropic, Ollama, Mistral, Hugging Face) use the same Hop Language Model Chat adapters.

## Using AI Help

1. Open a Data Vault model (`.hdv`) in the visual modeler.
2. Click **AI Help** on the toolbar (near **Check model**).
3. Choose a **scenario**, enter your question, and select context options:
   - **Include model check results** — validation messages from **Check model**
   - **Include catalog sources** — pick record definitions to send (remembered for the conversation; use **Change catalog sources** to repick)
   - **Include full model XML** — complete model XML (larger payload; use for deep reviews; sent on the first message only)
4. Click **Send**. The request runs in the background and appears in the conversation transcript.
5. Continue the conversation with follow-up questions. Each turn sends compact model structure JSON and refreshed check results; the full catalog context is sent on the first turn only.
6. If a response includes proposals, click **Review N proposed change(s)** on that turn, select items, preview, then **Apply selected**.

Use **New conversation** to clear history and catalog selections. Changing the scenario mid-conversation also starts a new conversation.

Applied changes record an undo point. Use **Undo** on the model toolbar if needed. After you apply proposals, the next turn tells the AI what changed.

### Applyable structural proposals (milestone 2)

In addition to notes, configuration, and renames, the AI can propose:

| Type | Effect |
|------|--------|
| `ADD_HUB` | Create a hub with catalog record source and business keys |
| `ADD_LINK` | Create a link between two or more existing hubs |
| `ADD_SATELLITE` | Create a satellite on a hub or link with catalog attributes |
| `SET_BUSINESS_KEYS` | Replace business keys on an existing hub |
| `BIND_RECORD_SOURCE` | Add a record source to a hub or set a satellite record source |
| `SET_TABLE_LOCATION` | Move a table on the model canvas |

Structural proposals are validated against the open model and catalog before review. Catalog field names must exist in the record definitions you include in the conversation.

## Privacy

- API keys are stored in Hop configuration only; they are not sent inside LLM prompts.
- Database passwords are not included in context JSON.
- Catalog and model content you enable is sent to your chosen provider.

## Programmatic API

Headless or test use:

- `DvAiContextBuilder.build(...)` — assemble context (includes `modelStructureJson` every turn)
- `DvAiAdvisorService.advise(...)` — call the LLM (optional `conversationHistory` for multi-turn)
- `DvAiConversationSession` — session state for GUI conversations
- `DvAiProposalApplier.apply(...)` — apply confirmed proposals

## Related docs

- [datavault-plugin.adoc](datavault-plugin.adoc) — plugin overview
- [performance-tuning.md](performance-tuning.md) — load performance tuning