package com.example.edithglasses.ai;

import java.util.List;

/** The AI services EDITH can use. Groq and Gemini have their own code paths, the rest speak the OpenAI chat format (Anthropic its own). */
public final class Ais {
    public record Ai(String id, String label, String base, String site, boolean anthropic) {
        public boolean compat() {
            return !id.equals("groq") && !id.equals("gemini");
        }
    }

    public static final List<Ai> ALL = List.of(
        new Ai("groq", "Groq", "https://api.groq.com/openai/v1", "console.groq.com", false),
        new Ai("gemini", "Google Gemini", "", "aistudio.google.com/apikey", false),
        new Ai("openai", "OpenAI", "https://api.openai.com/v1", "platform.openai.com/api-keys", false),
        new Ai("anthropic", "Anthropic Claude", "https://api.anthropic.com/v1", "console.anthropic.com", true),
        new Ai("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter.ai/keys", false),
        new Ai("mistral", "Mistral", "https://api.mistral.ai/v1", "console.mistral.ai", false),
        new Ai("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "platform.deepseek.com", false),
        new Ai("xai", "xAI Grok", "https://api.x.ai/v1", "console.x.ai", false),
        new Ai("cerebras", "Cerebras", "https://api.cerebras.ai/v1", "cloud.cerebras.ai", false),
        new Ai("together", "Together AI", "https://api.together.xyz/v1", "api.together.ai", false),
        new Ai("local", "Local AI (Ollama)", "http://localhost:11434/v1", "ollama.com", false));

    private Ais() {
    }

    /** Where to send requests: the address from the settings for the local AI, the fixed one for the others. */
    public static String base(Ai ai) {
        if (ai.id().equals("local")) {
            String u = com.example.edithglasses.EdithConfig.get().localUrl;
            u = u == null || u.isBlank() ? ai.base() : u.trim();
            return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
        }
        return ai.base();
    }

    public static Ai find(String id) {
        for (Ai a : ALL) if (a.id().equalsIgnoreCase(id)) return a;
        return null;
    }
}
