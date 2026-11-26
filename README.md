# HelpDesk

## Prerequisites
* JDK 25
* Docker and Docker Compose

This application uses [Spring AI](https://docs.spring.io/spring-ai/reference/) with the following chat model providers:
* **Ollama** (`ollama`) - runs locally. `compose.yml` includes an `ollama` service, and the `local` profile pulls the configured model (`OLLAMA_CHAT_MODEL`, default `llama3.2`) on first use.
* **OpenAI** (`openai`) - set the `OPENAI_API_KEY` environment variable.

The active chat model is selected by the `spring.ai.model.chat` property (default: `ollama`).
To switch providers, set the `SPRING_AI_MODEL_CHAT` environment variable to one of: `ollama`, `openai`.

Use `AiChatService` (or inject `ChatClient.Builder`) to talk to the model.

## Using mise
This project uses [mise](https://mise.jdx.dev/) to install the required SDKs and run common tasks defined in `mise.toml`.

Install mise by following the [installation guide](https://mise.jdx.dev/getting-started.html) (ex: `brew install mise`).

```shell
# Trust the project's mise.toml (one-time)
$ mise trust

# Install the SDKs (Java 25 Temurin, Maven)
$ mise install

# List available tasks
$ mise tasks

# Format code and run tests
$ mise run format
$ mise run test

# Run verification checks
$ mise run verify

# Build docker image
$ mise run build_image

# Run application in docker container
$ mise run start
$ mise run stop
$ mise run restart
```
