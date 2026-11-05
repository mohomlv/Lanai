import fs from "fs";
import path from "path";

const dir = "D:/Smart-Dashboard/9router/open-sse/providers/registry";
const files = fs.readdirSync(dir).filter((f) => f.endsWith(".js") && f !== "index.js");
const out = [];

for (const file of files) {
  const text = fs.readFileSync(path.join(dir, file), "utf8");
  if (/category:\s*["'](oauth|webCookie)["']/.test(text)) continue;
  const id = (text.match(/\bid:\s*["']([^"']+)["']/) || [])[1];
  const name = (text.match(/\bname:\s*["']([^"']+)["']/) || [])[1];
  if (!id || !name) continue;
  const urls = [...text.matchAll(/baseUrl:\s*["'](https?:[^"']+)["']/g)].map((m) => m[1]);
  const chat = urls.find((u) =>
    /chat\/completions|\/v1\/messages|\/api\/chat|generativelanguage\.googleapis\.com\/v1beta\/models/.test(u)
  );
  if (!chat) continue;
  let format = "openai";
  if (chat.includes("/messages")) format = "claude";
  else if (chat.includes("/api/chat")) format = "ollama";
  else if (chat.includes("generativelanguage.googleapis.com")) format = "gemini";
  const models = [];
  const modelRe = /\{\s*id:\s*["']([^"']+)["'],\s*name:\s*["']([^"']+)["'](?:,\s*kind:\s*["']([^"']+)["'])?/g;
  let match;
  while ((match = modelRe.exec(text)) && models.length < 12) {
    if (match[3] && match[3] !== "llm") continue;
    models.push({ id: match[1], name: match[2] });
  }
  const noAuth = /noAuth:\s*true/.test(text) || id === "ollama-local";
  out.push({ id, name, format, chatUrl: chat, noAuth, models });
}

const extras = [
  {
    id: "openai-compatible",
    name: "OpenAI-compatible endpoint",
    format: "openai",
    chatUrl: "http://localhost:1234/v1/chat/completions",
    noAuth: true,
    baseUrlEditable: true,
    models: [],
  },
  {
    id: "azure-openai",
    name: "Azure OpenAI",
    format: "azure",
    chatUrl: "https://YOUR_RESOURCE.openai.azure.com/openai/deployments/YOUR_DEPLOYMENT/chat/completions?api-version=2024-10-21",
    noAuth: false,
    baseUrlEditable: true,
    models: [],
  },
];

for (const extra of extras) {
  if (!out.some((p) => p.id === extra.id)) out.push(extra);
}
for (const provider of out) {
  if (provider.baseUrlEditable == null) {
    provider.baseUrlEditable = provider.id === "ollama-local" || provider.id === "azure";
  }
}
if (out.some((p) => p.id === "azure")) {
  const azure = out.find((p) => p.id === "azure");
  azure.format = "azure";
  azure.baseUrlEditable = true;
  azure.chatUrl = extras[1].chatUrl;
}

out.sort((a, b) => a.name.localeCompare(b.name));
const target = "D:/Smart-Dashboard/landata/src/main/resources/ai/providers.json";
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, JSON.stringify(out, null, 2));
console.log("providers", out.length);
console.log(out.map((p) => `${p.id} ${p.format}`).join("\n"));
