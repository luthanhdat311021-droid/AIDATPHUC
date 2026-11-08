import { GoogleGenerativeAI } from '@google/generative-ai';
import Groq from 'groq-sdk';
import dotenv from 'dotenv';
dotenv.config();

const geminiKey = process.env.GEMINI_API_KEY;
const groqKey = process.env.GROQ_API_KEY;

async function testModels() {
  console.log("--- Testing Gemini Models ---");
  const geminiModels = ["gemini-3.6-flash", "gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.0-flash"];
  for (const m of geminiModels) {
    try {
      const genAI = new GoogleGenerativeAI(geminiKey);
      const model = genAI.getGenerativeModel({ model: m });
      const res = await model.generateContent("Ping");
      console.log(`✅ Gemini [${m}]:`, (await res.response).text().substring(0, 30));
    } catch (err) {
      console.log(`❌ Gemini [${m}]:`, err.message.substring(0, 100));
    }
  }

  console.log("\n--- Testing Groq Models ---");
  const groq = new Groq({ apiKey: groqKey });
  const groqModels = ["llama-3.1-8b-instant", "llama3-70b-8192", "mixtral-8x7b-32768", "gemma2-9b-it", "groq/compound"];
  for (const m of groqModels) {
    try {
      const res = await groq.chat.completions.create({
        messages: [{ role: 'user', content: 'Ping' }],
        model: m,
        max_tokens: 10
      });
      console.log(`✅ Groq [${m}]:`, res.choices[0]?.message?.content);
    } catch (err) {
      console.log(`❌ Groq [${m}]:`, err.message.substring(0, 100));
    }
  }
}

testModels();
