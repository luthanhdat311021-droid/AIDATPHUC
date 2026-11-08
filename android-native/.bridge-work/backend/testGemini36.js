import { GoogleGenerativeAI } from '@google/generative-ai';
import Groq from 'groq-sdk';
import dotenv from 'dotenv';
dotenv.config();

const geminiKey = process.env.GEMINI_API_KEY;
const groqKey = process.env.GROQ_API_KEY;

async function testGemini36() {
  console.log("Testing gemini-3.6-flash and groq/compound...");
  
  // 1. Groq with groq/compound
  try {
    const groq = new Groq({ apiKey: groqKey });
    const groqRes = await groq.chat.completions.create({
      messages: [{ role: 'user', content: 'Giải phương trình 2x + 6 = 18' }],
      model: 'groq/compound',
      max_tokens: 100
    });
    console.log("⚡ [GROQ groq/compound SUCCESS]:", groqRes.choices[0]?.message?.content);
  } catch (err) {
    console.error("❌ [GROQ ERROR]:", err.message);
  }

  // 2. Gemini with gemini-3.6-flash
  try {
    const genAI = new GoogleGenerativeAI(geminiKey);
    const model = genAI.getGenerativeModel({ model: "gemini-3.6-flash" });
    const geminiRes = await model.generateContent("Giải phương trình 2x + 6 = 18 và giải thích ngắn gọn.");
    const response = await geminiRes.response;
    console.log("🤖 [GEMINI gemini-3.6-flash SUCCESS]:", response.text());
  } catch (err) {
    console.error("❌ [GEMINI ERROR]:", err.message);
  }
}

testGemini36();
