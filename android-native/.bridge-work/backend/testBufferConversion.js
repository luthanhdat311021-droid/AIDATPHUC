import fs from 'fs';
import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const pdfParseModule = require('pdf-parse');

async function testBuffer() {
  const filePath = 'c:/Users/lutha/OneDrive/Desktop/AI học tập/backend/uploads/1789548530312-379605224-project_8.pdf';
  const dataBuffer = fs.readFileSync(filePath);

  console.log("Buffer length:", dataBuffer.length, "byteOffset:", dataBuffer.byteOffset, "buffer byteLength:", dataBuffer.buffer.byteLength);

  const uint8Array = new Uint8Array(dataBuffer.buffer, dataBuffer.byteOffset, dataBuffer.byteLength);

  try {
    const parser = new pdfParseModule.PDFParser(uint8Array);
    const parsed = await parser.getText();
    console.log("SUCCESS! Extracted text length:", parsed.text.length);
    console.log("First 150 chars:\n", parsed.text.slice(0, 150));
  } catch (err) {
    console.error("FAIL:", err);
  }
}

testBuffer();
