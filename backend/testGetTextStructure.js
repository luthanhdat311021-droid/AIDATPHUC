import fs from 'fs';
import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const pdfParseModule = require('pdf-parse');

async function testGetTextStructure() {
  const filePath = 'c:/Users/lutha/OneDrive/Desktop/AI học tập/backend/uploads/1789548530312-379605224-project_8.pdf';
  const dataBuffer = fs.readFileSync(filePath);
  const uint8Array = new Uint8Array(dataBuffer);

  const { PDFParse } = pdfParseModule;
  const instance = new PDFParse(uint8Array);
  const parsed = await instance.getText();
  
  console.log("parsed type:", typeof parsed);
  console.log("parsed is null?", parsed === null);
  console.log("parsed keys:", parsed ? Object.keys(parsed) : 'null');
  console.log("parsed.text type:", typeof parsed.text);
  console.log("parsed.text length:", parsed.text ? parsed.text.length : 0);
}

testGetTextStructure();
