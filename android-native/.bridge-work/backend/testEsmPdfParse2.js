import fs from 'fs';
import { PDFParse } from 'pdf-parse';

async function testEsm2() {
  console.log("PDFParse type:", typeof PDFParse);

  const filePath = 'c:/Users/lutha/OneDrive/Desktop/AI học tập/backend/uploads/1789548530312-379605224-project_8.pdf';
  const dataBuffer = fs.readFileSync(filePath);
  const uint8Array = new Uint8Array(dataBuffer);

  const instance = new PDFParse(uint8Array);
  const parsed = await instance.getText();

  console.log("✅ PDFParse SUCCESS! Length:", parsed.text ? parsed.text.length : 0);
  console.log("Extracted text snippet:\n", parsed.text ? parsed.text.slice(0, 300) : "");
}

testEsm2();
