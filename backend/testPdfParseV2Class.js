import fs from 'fs';
import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const pdfParseModule = require('pdf-parse');

async function testPDFParseClass() {
  const filePath = 'c:/Users/lutha/OneDrive/Desktop/AI học tập/backend/uploads/1789548530312-379605224-project_8.pdf';
  const dataBuffer = fs.readFileSync(filePath);

  console.log("PDFParse type:", typeof pdfParseModule.PDFParse);

  // Method 1: PDFParse as function/class
  try {
    const parser = new pdfParseModule.PDFParse(dataBuffer);
    const result = await parser.getText();
    console.log("Method 1 (new PDFParse(dataBuffer).getText()):", typeof result, result ? result.slice(0, 200) : "empty");
  } catch (err1) {
    console.log("Method 1 failed:", err1.message);
  }

  // Method 2: PDFParse static or function
  try {
    const result = await pdfParseModule.PDFParse(dataBuffer);
    console.log("Method 2 (PDFParse(dataBuffer)):", typeof result, result ? result.text?.slice(0, 200) : "empty");
  } catch (err2) {
    console.log("Method 2 failed:", err2.message);
  }

  // Method 3: pdfParseModule directly if it has default export or pdf-parse legacy API
  try {
    const { PDFParse } = pdfParseModule;
    if (PDFParse) {
      const p = new PDFParse();
      const res = await p.load(dataBuffer);
      console.log("Method 3 res:", res);
    }
  } catch (err3) {
    console.log("Method 3 failed:", err3.message);
  }
}

testPDFParseClass();
