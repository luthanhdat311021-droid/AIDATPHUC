import fs from 'fs';
import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const pdfParseModule = require('pdf-parse');

async function testPdfParseUint8() {
  const filePath = 'c:/Users/lutha/OneDrive/Desktop/AI học tập/backend/uploads/1789548530312-379605224-project_8.pdf';
  console.log("Reading:", filePath);
  const dataBuffer = fs.readFileSync(filePath);
  const uint8Array = new Uint8Array(dataBuffer);

  try {
    const { PDFParse } = pdfParseModule;
    const instance = new PDFParse(uint8Array);
    console.log("Instance created successfully!");
    
    // Check available methods on instance
    const prototypeKeys = Object.getOwnPropertyNames(Object.getPrototypeOf(instance));
    console.log("Instance prototype methods:", prototypeKeys);
    
    if (typeof instance.getText === 'function') {
      const textResult = await instance.getText();
      console.log("\n--- TEXT RESULT FROM pdf-parse v2 ---");
      console.log(textResult);
    } else if (typeof instance.asString === 'function') {
      const strResult = await instance.asString();
      console.log("\n--- STRING RESULT FROM pdf-parse v2 ---");
      console.log(strResult);
    } else if (typeof instance.extractText === 'function') {
      const extractResult = await instance.extractText();
      console.log("\n--- EXTRACT RESULT FROM pdf-parse v2 ---");
      console.log(extractResult);
    }
  } catch (err) {
    console.error("❌ Error with Uint8Array:", err);
  }
}

testPdfParseUint8();
