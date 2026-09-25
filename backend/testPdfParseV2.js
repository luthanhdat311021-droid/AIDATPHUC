import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const pdfParseModule = require('pdf-parse');

console.log("pdfParseModule keys:", Object.keys(pdfParseModule));
console.log("pdfParseModule type:", typeof pdfParseModule);
console.log("pdfParseModule.default:", typeof pdfParseModule.default);
if (typeof pdfParseModule.PDFParser === 'function') console.log("PDFParser class found");
if (typeof pdfParseModule.PdfReader === 'function') console.log("PdfReader class found");
