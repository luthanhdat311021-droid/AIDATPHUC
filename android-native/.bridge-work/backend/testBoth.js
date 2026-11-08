import { GoogleGenerativeAI } from '@google/generative-ai';
import Groq from 'groq-sdk';
import dotenv from 'dotenv';
dotenv.config();

const geminiKey = process.env.GEMINI_API_KEY;
const groqKey = process.env.GROQ_API_KEY;

async function testBoth() {
  console.log("=== TESTING LIVE BACKEND API KEYS ===");
  console.log("Gemini Key:", geminiKey ? `${geminiKey.substring(0, 10)}...` : "NOT FOUND");
  console.log("Groq Key:", groqKey ? `${groqKey.substring(0, 10)}...` : "NOT FOUND");
  
  // 1. Test Groq
  try {
    const groq = new Groq({ apiKey: groqKey });
    const groqRes = await groq.chat.completions.create({
      messages: [{ role: 'user', content: 'Xin chào, Groq API đã hoạt động chưa?' }],
      model: 'llama-3.3-70b-versatile',
      max_tokens: 50
    });
    console.log("⚡ [GROQ SUCCESS]:", groqRes.choices[0]?.message?.content);
  } catch (err) {
    console.error("❌ [GROQ ERROR]:", err.message);
  }

  // 2. Test Gemini with gemini-2.5-flash / gemini-1.5-flash
  const modelsToTest = ["gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.0-flash"];
  for (const modelName of modelsToTest) {
    try {
      console.log(`\nTesting Gemini Model: ${modelName}...`);
      const genAI = new GoogleGenerativeAI(geminiKey);
      const model = genAI.getGenerativeModel({ model: modelName });
      const geminiRes = await model.generateContent("Xin chào Gemini, hãy giải phương trình x + 5 = 12.");
      const response = await geminiRes.response;
      console.log(`🤖 [GEMINI ${modelName} SUCCESS]:`, response.text());
      break; // Success!
    } catch (err) {
      console.error(`❌ [GEMINI ${modelName} ERROR]:`, err.message);
    }
  }
}

testBoth();
