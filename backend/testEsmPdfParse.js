import fs from 'fs';
import pdfParse, { PDFParse } from 'pdf-parse';

async function testEsm() {
  console.log("pdfParse default type:", typeof pdfParse);
  console.log("PDFParse named export type:", typeof PDFParse);

  const filePath = 'c:/Users/lutha/OneDrive/Desktop/AI học tập/backend/uploads/1789548530312-379605224-project_8.pdf';
  const dataBuffer = fs.readFileSync(filePath);

  // Try named export PDFParse
  if (typeof PDFParse === 'function') {
    try {
      const instance = new PDFParse(new Uint8Array(dataBuffer));
      const res = await instance.getText();
      console.log("✅ PDFParse named export SUCCESS! Length:", res.text ? res.text.length : 0);
      console.log("Snippet:", res.text ? res.text.slice(0, 100) : "");
      return;
    } catch (e1) {
      console.log("PDFParse named export failed:", e1.message);
    }
  }

  // Try default export function
  if (typeof pdfParse === 'function') {
    try {
      const res = await pdfParse(dataBuffer);
      console.log("✅ pdfParse default export SUCCESS! Length:", res.text ? res.text.length : 0);
      console.log("Snippet:", res.text ? res.text.slice(0, 100) : "");
      return;
    } catch (e2) {
      console.log("pdfParse default export failed:", e2.message);
    }
  }
}

testEsm();
