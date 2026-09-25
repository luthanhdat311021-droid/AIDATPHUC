import fs from 'fs';
import path from 'path';
import mammoth from 'mammoth';
import { aiRouter } from '../ai/AIRouter.js';

// Polyfill browser globals required by pdfjs-dist / pdf-parse v2 in Node.js serverless environment
if (typeof globalThis.DOMMatrix === 'undefined') {
  globalThis.DOMMatrix = class DOMMatrix {
    constructor() {
      this.a = 1; this.b = 0; this.c = 0; this.d = 1; this.e = 0; this.f = 0;
    }
  };
}
if (typeof globalThis.ImageData === 'undefined') {
  globalThis.ImageData = class ImageData {};
}
if (typeof globalThis.Path2D === 'undefined') {
  globalThis.Path2D = class Path2D {};
}

/**
 * Universal PDF Text Extractor supporting pdf-parse v2 API safely
 */
async function extractTextFromPdfBuffer(dataBuffer) {
  try {
    const pdfParseModule = await import('pdf-parse');
    const PDFParse = pdfParseModule.PDFParse || pdfParseModule.default;
    if (typeof PDFParse === 'function') {
      const uint8Array = new Uint8Array(dataBuffer);
      const parser = new PDFParse(uint8Array);
      const parsed = await parser.getText();
      const text = typeof parsed === 'string' ? parsed : (parsed?.text || '');
      if (text && text.trim().length > 5) {
        console.log(`✅ [Document Parser] Extracted ${text.trim().length} chars via PDFParse v2`);
        return text.trim();
      }
    }
  } catch (err1) {
    console.warn(`⚠️ [Document Parser] PDFParser v2 error: ${err1.message}`);
  }

  return '';
}


/**
 * Sanitize and clean extracted PDF text
 */
function sanitizePdfText(text) {
  if (!text) return '';

  return text
    // Remove null / non-printable control characters
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '')
    // Rejoin words broken across lines with hyphens (e.g. "khái -\n niệm" -> "khái niệm")
    .replace(/(\w+)\s*[\-\u2010\u2013\u2014]\s*\n\s*(\w+)/g, '$1$2')
    // Remove repeated page numbers / headers like "Trang 1 / 10" or "Page 3 of 12"
    .replace(/(?:trang|page)\s*\d+\s*(?:\/|of|-)\s*\d+/gi, '')
    // Remove standalone numbers acting as footer page counters
    .replace(/\n\s*\d+\s*\n/g, '\n')
    // Fix spaced out characters ("K h á i  n i ệ m" -> "Khái niệm")
    .replace(/(?:^|\n)([A-ZÀ-Ỹa-zà-ỹ]\s){4,}[A-ZÀ-Ỹa-zà-ỹ](?=\n|$)/g, (match) => match.replace(/\s+/g, ''))
    // Normalize multi blank lines to double newlines
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/**
 * Universal Multi-Format Document Parser
 * Uses mammoth for DOCX, pdf-parse for PDF, Tesseract.js for Image OCR, and native reader for TXT/MD
 */
export async function parseDocumentContent(filePath, originalName, mimeType) {
  console.log(`[Document Parser] Extracting content from: "${originalName}" (${mimeType})`);

  if (!filePath || !fs.existsSync(filePath)) {
    throw new Error(`Không nhận được tệp "${originalName}" trên máy chủ. Vui lòng tải lên lại.`);
  }

  const ext = path.extname(originalName).toLowerCase();
  
  // 1. DOCX / DOC Files using Mammoth
  if (['.docx', '.doc'].includes(ext) || mimeType?.includes('word')) {
    try {
      const result = await mammoth.extractRawText({ path: filePath });
      const extractedText = result.value ? result.value.trim() : '';
      if (extractedText.length > 5) {
        console.log(`✅ [Document Parser] Successfully extracted ${extractedText.length} chars from DOCX "${originalName}" via Mammoth`);
        return extractedText;
      }
    } catch (err) {
      console.warn(`⚠️ [Document Parser] Mammoth extract error: ${err.message}`);
    }
  }

  // 2. PDF Files using pdf-parse & Stream Fallback
  if (ext === '.pdf' || mimeType?.includes('pdf')) {
    try {
      const dataBuffer = fs.readFileSync(filePath);
      const rawText = await extractTextFromPdfBuffer(dataBuffer);
      const cleanPdfText = sanitizePdfText(rawText);

      if (cleanPdfText.length > 30) {
        console.log(`✅ [Document Parser] Successfully extracted & sanitized ${cleanPdfText.length} chars from PDF "${originalName}"`);
        return cleanPdfText;
      }

      console.warn(`⚠️ [Document Parser] No text layer in PDF (${cleanPdfText.length} chars) — reading it with Gemini vision...`);
    } catch (err) {
      console.warn(`⚠️ [Document Parser] PDF extract error: ${err.message}`);
    }

    // Scanned PDF: let Gemini read the pages (raw binary-stream scraping only produced garbage)
    const transcribed = await aiRouter.transcribeFile(filePath, 'application/pdf');
    if (transcribed.length > 30) {
      console.log(`✅ [Document Parser] Gemini read ${transcribed.length} chars from scanned PDF "${originalName}"`);
      return transcribed;
    }
    throw new Error(`Không đọc được nội dung PDF "${originalName}" (có thể là bản scan mờ hoặc tệp trống). Hãy thử tệp khác hoặc chụp ảnh rõ từng trang.`);
  }

  // PPTX: slide text lives in ppt/slides/slideN.xml as <a:t> runs
  if (ext === '.pptx') {
    try {
      const { default: JSZip } = await import('jszip');
      const zip = await JSZip.loadAsync(fs.readFileSync(filePath));
      const slidePaths = Object.keys(zip.files)
        .filter(p => /^ppt\/slides\/slide\d+\.xml$/.test(p))
        .sort((a, b) => Number(a.match(/\d+/g).pop()) - Number(b.match(/\d+/g).pop()));
      const slides = [];
      for (const [idx, p] of slidePaths.entries()) {
        const xml = await zip.file(p).async('string');
        const paragraphs = (xml.match(/<a:p>[\s\S]*?<\/a:p>/g) || [])
          .map(para => (para.match(/<a:t>([^<]*)<\/a:t>/g) || []).map(t => t.replace(/<\/?a:t>/g, '')).join('').trim())
          .filter(Boolean);
        if (paragraphs.length) slides.push(`Slide ${idx + 1}:\n${paragraphs.join('\n')}`);
      }
      const text = slides.join('\n\n');
      if (text.length > 30) return text;
    } catch (err) {
      console.warn(`⚠️ [Document Parser] PPTX extract error: ${err.message}`);
    }
    throw new Error(`Không đọc được nội dung PowerPoint "${originalName}".`);
  }

  // 3. Text & Markdown Files
  if (['.txt', '.md', '.json', '.csv'].includes(ext)) {
    try {
      return fs.readFileSync(filePath, 'utf-8');
    } catch (e) {
      console.warn(`⚠️ [Document Parser] Text read error: ${e.message}`);
    }
  }

  // 4. Images: Gemini vision reads Vietnamese, formulas and tables far better than OCR; Tesseract is the fallback
  if (['.png', '.jpg', '.jpeg', '.webp', '.bmp', '.gif'].includes(ext) || mimeType?.startsWith('image/')) {
    const transcribed = await aiRouter.transcribeFile(filePath, mimeType?.startsWith('image/') ? mimeType : 'image/jpeg');
    if (transcribed.length > 20) {
      console.log(`✅ [Document Parser] Gemini read ${transcribed.length} chars from image "${originalName}"`);
      return transcribed;
    }
    try {
      console.log(`📷 [Document Parser] Performing Tesseract OCR on image: ${originalName}...`);
      const tesseractModule = await import('tesseract.js');
      const Tesseract = tesseractModule.default || tesseractModule;
      const { data: { text } } = await Tesseract.recognize(filePath, 'eng+vie', {
        logger: m => console.log(`[OCR Progress] ${m.status}: ${(m.progress * 100).toFixed(0)}%`)
      });

      const cleanText = text ? text.trim() : '';
      if (cleanText.length > 5) {
        console.log(`✅ [Document Parser] Tesseract OCR extracted ${cleanText.length} chars from image "${originalName}"`);
        return cleanText;
      }
    } catch (ocrErr) {
      console.warn(`⚠️ [Document Parser] Tesseract OCR warning: ${ocrErr.message}`);
    }
  }

  // No readable text: fail loudly instead of handing the AI a generic placeholder it would build a fake lesson from
  if (['.png', '.jpg', '.jpeg', '.webp', '.bmp', '.gif'].includes(ext) || mimeType?.startsWith('image/')) {
    throw new Error(`Không đọc được chữ trong ảnh "${originalName}". Hãy chụp rõ nét, đủ sáng và thẳng trang rồi thử lại.`);
  }
  throw new Error(`Không đọc được nội dung tệp "${originalName}". Hãy dùng PDF, DOCX, PPTX, TXT hoặc ảnh JPG/PNG.`);
}

