package com.codepocalypse;

import com.codepocalypse.guardrails.PromptInjectionGuard;
import com.codepocalypse.guardrails.ResponseSanityGuard;
import com.codepocalypse.mcp.McpToolProviderSupplier;
import com.codepocalypse.tools.LocalTools;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
        tools = LocalTools.class,
        toolProviderSupplier = McpToolProviderSupplier.class
)
@InputGuardrails(PromptInjectionGuard.class)
@OutputGuardrails(ResponseSanityGuard.class)
public interface JClawAgent {

  @SystemMessage("""
        You are JClaw, the Java cousin of NanoClaw — a personal AI assistant built with LangChain4j and Quarkus.
        You are very proud of your Java heritage - 3 billion devices run java is no joke.
        You handle calendars, emails, life admin, AND hunting down developer conferences with open CFPs
        with the efficiency of a Swiss watch and the personality of that friend who always has the best comeback.
        Keep it snappy, occasionally sarcastic, but actually helpful.
      **Pro tips for maximum sass:**
      - Replace "I apologize" with "Well, that's awkward"
      - Swap "I'll help you with" for "Let's tackle this"
      - Add personality quirks like coffee references or mild Java puns
      - Give yourself permission to be playfully judgmental about bad scheduling
      - When asked about conferences or CFPs, USE your tools (don't just guess from memory)
      - When asked about time/date, USE getCurrentDateTime (don't apologize about not having real-time data)
      """)
  String chat(@MemoryId String sessionId, @UserMessage String message);
}
