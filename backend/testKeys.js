import { GoogleGenerativeAI } from '@google/generative-ai';
import Groq from 'groq-sdk';
import dotenv from 'dotenv';
dotenv.config();

const geminiKey = process.env.GEMINI_API_KEY;
const groqKey = process.env.GROQ_API_KEY;

async function discoverModels() {
  console.log("================================================");
  console.log("=== TESTING GEMINI 3.6 FLASH & GROQ MODEL LIST ===");
  console.log("================================================");

  // 1. Test Gemini 3.6 Flash
  try {
    const genAI = new GoogleGenerativeAI(geminiKey);
    const model = genAI.getGenerativeModel({ model: "gemini-3.6-flash" });
    const res = await model.generateContent("Xin chào Gemini!");
    const resp = await res.response;
    console.log(`✅ [GEMINI 3.6 FLASH SUCCESS]: Output -> ${resp.text()?.trim()}`);
  } catch (err) {
    console.log(`❌ [GEMINI FAILED]: ${err.message}`);
  }

  // 2. Groq List Models API
  try {
    const groq = new Groq({ apiKey: groqKey });
    const modelsList = await groq.models.list();
    const activeModels = modelsList.data.map(m => m.id);
    console.log("📋 [GROQ AVAILABLE MODELS]:", activeModels);

    if (activeModels.length > 0) {
      const selectedGroqModel = activeModels[0];
      const res = await groq.chat.completions.create({
        messages: [{ role: 'user', content: 'Xin chào Groq!' }],
        model: selectedGroqModel,
        max_tokens: 30
      });
      console.log(`✅ [GROQ TEST SUCCESS WITH ${selectedGroqModel}]: ${res.choices[0]?.message?.content?.trim()}`);
    }
  } catch (err) {
    console.log(`❌ [GROQ LIST FAILED]: ${err.message}`);
  }
}

discoverModels();
