import { GoogleGenerativeAI } from '@google/generative-ai';
import dotenv from 'dotenv';
dotenv.config();

const geminiKey = process.env.GEMINI_API_KEY;
console.log("Gemini key:", geminiKey ? `${geminiKey.substring(0, 8)}...` : 'NONE');

async function testGemini() {
  const models = ["gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro", "gemini-2.0-flash-exp", "gemini-flash"];
  for (const m of models) {
    try {
      const genAI = new GoogleGenerativeAI(geminiKey);
      const model = genAI.getGenerativeModel({ model: m });
      const res = await model.generateContent("Say hi");
      console.log(`✅ [${m}] SUCCESS:`, (await res.response).text());
      return;
    } catch (err) {
      console.log(`❌ [${m}] FAILED:`, err.message);
    }
  }
}

testGemini();
