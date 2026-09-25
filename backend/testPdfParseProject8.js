import fs from 'fs';
import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const pdfParseModule = require('pdf-parse');
const pdfParse = typeof pdfParseModule === 'function' ? pdfParseModule : (pdfParseModule.default || pdfParseModule);

async function inspectPdf() {
  const filePath = 'c:/Users/lutha/OneDrive/Desktop/AI học tập/backend/uploads/1789548530312-379605224-project_8.pdf';
  console.log("Reading:", filePath);
  const dataBuffer = fs.readFileSync(filePath);
  
  try {
    const pdfData = await pdfParse(dataBuffer);
    console.log("PDF Data info:", {
      numpages: pdfData.numpages,
      numrender: pdfData.numrender,
      info: pdfData.info,
      metadata: pdfData.metadata,
      textLength: pdfData.text ? pdfData.text.length : 0
    });
    console.log("--- RAW EXTRACTED TEXT ---");
    console.log(JSON.stringify(pdfData.text));
    console.log("--------------------------");
  } catch (err) {
    console.error("pdf-parse error:", err);
  }
}

inspectPdf();
