# GPTxBot

## Running the bot

Pull the image from `ghcr.io/acrylic-style/gptx-bot:main`, then run it with the environment variables defined in
[BotConfig.kt](https://github.com/acrylic-style/gptx-bot/blob/main/src/main/kotlin/xyz/acrylicstyle/gptxbot/BotConfig.kt)

Example docker-compose.yml is as follows:

```yml
services:
  bot:
    image: ghcr.io/acrylic-style/gptx-bot:main
    environment:
      TOKEN: discord bot token
      OPENAI_TOKEN: sk-xxx
      ASSISTANT_ID: asst_0KeXvo5Lg9SrPQwt55hMFQcn
      CLOUDFLARE_API_KEY: cloudflare api key (workers kv read/write access)
      CLOUDFLARE_ACCOUNT_ID: cloudflare account id
      CLOUDFLARE_KV_USERS_ID: kv namespace id for users
      CLOUDFLARE_KV_DISCORD_ID: kv namespace id for discord
      CREATE_THREAD: "true"
      GITHUB_ACCESS_TOKEN: github access token
    volumes:
      - ./tool_calls.json:/app/tool_calls.json
      - ./reminds.json:/app/reminds.json
```
